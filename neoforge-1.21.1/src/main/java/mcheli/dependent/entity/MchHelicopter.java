package mcheli.dependent.entity;

import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.helicopter.MCH_HeliInfo;
import mcheli.agnostic.helicopter.MCH_HeliInfoManager;
import mcheli.agnostic.sim.AircraftFlightController;
import mcheli.agnostic.sim.AircraftSimState;
import mcheli.agnostic.sim.ControlInput;
import mcheli.agnostic.sim.FlightModel;
import mcheli.agnostic.sim.HeliControlMapping;
import mcheli.agnostic.sim.HeliFlightModel;
import mcheli.agnostic.sim.HeliState;
import mcheli.agnostic.sim.RotationSolver;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * The helicopter entity — ONE class serving EVERY helicopter config (ah-64, mi-28, ka-52, …). Which vehicle it is
 * comes from the synced config name ({@link AbstractMchVehicle#configName()}); the per-category flight model is
 * {@code HeliFlightModel}. Collective/cyclic flight: holding throttle-up spools the rotor and it climbs; a pilotless
 * heli develops no lift and settles under gravity. The concrete {@link MCH_HeliInfo} (model, HP, weapons, seats,
 * rotor list) is resolved from the config name via {@link MCH_HeliInfoManager}.
 */
public class MchHelicopter extends AbstractMchVehicle {

    private static final FlightModel MODEL = new HeliFlightModel();
    /** Fallback for an unknown/blank config name so the physics never sees a null info. */
    private static final MCH_HeliInfo FALLBACK = buildFallback();

    private final HeliState heliState = new DemoHeliState(this);
    private final AircraftSimState simState = new AircraftSimState(0.07); // heli idles currentSpeed at 0.07
    private final AircraftSimState rotState = new AircraftSimState();
    private RotationSolver.ControlMapping mapping; // lazy — needs the resolved concrete MCH_HeliInfo

    public MchHelicopter(EntityType<? extends MchHelicopter> type, Level level) {
        super(type, level);
    }

    @Override protected MCH_AircraftInfo resolveInfo(String name) {
        MCH_HeliInfo i = MCH_HeliInfoManager.get(name);
        return i != null ? i : FALLBACK;
    }

    @Override protected String modelDir() { return "helicopters"; }


    /** Fuel burns with the FLIGHT-SIM throttle (reference getThrottle), not the display enginePower —
     *  which idles at 0.5 while merely ridden and would drain the tank at rest. */
    @Override protected double simThrottle() { return this.simState.getCurrentThrottle(); }

    // Reference MCH_EntityHeli.getSoundVolume/getSoundPitch: volume = SoundVolume × throttle × 2; pitch spans [0.2,0.4].
    @Override protected String defaultEngineSound() { return "heli"; }
    @Override public float engineSoundVolume() { return configSoundVolume() * getThrottleInput() * 2.0F; }
    @Override public float engineSoundPitch() { return configSoundPitch() * (0.2F + getThrottleInput() * 0.2F); }

    @Override
    protected void clientAmbientEffects() {
        spawnRotorDownwash();
        super.clientAmbientEffects(); // shared damage smoke
    }

    /**
     * The rotor down-wash dust/vapor ring — the port of {@code MCH_EntityAircraft.onUpdate_ParticleSandCloud(false)}
     * ({@code MCH_EntityAircraft}:2855, called for the heli at {@code MCH_EntityHeli}:787). A throttle-gated ring of
     * radius {@code particlesScale × 9} at the ground/water surface below the rotor, denser the closer the heli hovers
     * to the ground. Client-only.
     */
    private void spawnRotorDownwash() {
        float throttle = Math.min(getThrottleInput() * 2.0F, 1.0F); // rotor RPM gate (reference min(throttle*2,1))
        mcheli.agnostic.aircraft.MCH_AircraftInfo info = weaponHostInfo();
        float particlesScale = info != null ? info.particlesScale : 1.0F;
        if (throttle <= 0.0F || particlesScale <= 0.01F) {
            return;
        }
        float scale = particlesScale * 3.0F;
        int rangeY = (int) (scale * 10.0F) + 1;
        double px = getX(), py = getY(), pz = getZ();

        // Scan straight down (over a 3×3 column) for the first solid/water block — the surface the wash hits.
        int foundY = -1;
        double surfaceY = 0.0;
        for (int y = 0; y < rangeY && foundY < 0; y++) {   // reference scans y in [0, rangeY)
            for (int dx = -1; dx <= 1 && foundY < 0; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    net.minecraft.core.BlockPos bp =
                        net.minecraft.core.BlockPos.containing(px + 0.5 + dx, py + 0.5 - y, pz + 0.5 + dz);
                    if (!this.level().getBlockState(bp).getCollisionShape(this.level(), bp).isEmpty()
                        || !this.level().getFluidState(bp).isEmpty()) {
                        foundY = y;
                        surfaceY = py + 1.0 + scale / 5.0 - y; // reference particlePosY
                        break;
                    }
                }
            }
        }
        if (foundY < 0) {
            return; // too high above any surface — no wash
        }
        float pn = (rangeY - foundY) / (5.0F * scale) / 2.0F; // altitude fade: bigger near the ground (reference rangeY-y+1 after the loop post-increment)
        int count = (int) (throttle * 6.0F * pn);
        for (int i = 0; i < count; i++) {
            float r = this.random.nextFloat() * (float) (Math.PI * 2.0);
            float dxr = net.minecraft.util.Mth.cos(r);
            float dzr = net.minecraft.util.Mth.sin(r);
            double x = px + dxr * scale * 3.0;                                // ring radius = particlesScale*9
            double y = surfaceY + (this.random.nextFloat() - 0.5) * scale;
            double z = pz + dzr * scale * 3.0;
            this.level().addParticle(net.minecraft.core.particles.ParticleTypes.CLOUD, x, y, z,
                scale * dxr * 0.3, scale * -0.02, scale * dzr * 0.3);        // blows radially outward + slightly down
        }
    }

    /** Reference {@code MCH_EntityHeli.canSwitchGunnerMode}:293 — additionally requires a near-level attitude
     *  (|roll| &lt; 40°, |pitch| &lt; 40°). {@code canUseBlades()} collapses to true for the port's rotor model. */
    @Override public boolean canSwitchGunnerMode() {
        if (!super.canSwitchGunnerMode()) {
            return false;
        }
        float roll = Math.abs(net.minecraft.util.Mth.wrapDegrees(getRollAngle()));
        float pitch = Math.abs(net.minecraft.util.Mth.wrapDegrees(getXRot()));
        return roll < 40.0F && pitch < 40.0F;
    }
    @Override protected RotationSolver.ControlMapping controlMapping() {
        if (this.mapping == null) {
            this.mapping = new HeliControlMapping(this.ref, (MCH_HeliInfo) weaponHostInfo(), this.rotState, this.heliState);
        }
        return this.mapping;
    }
    @Override protected void onConfigChanged() { this.mapping = null; }

    @Override
    protected void tickPhysics(ControlInput in) {
        AircraftFlightController.tickServer(this.ref, weaponHostInfo(), this.simState, in, this.heliState, MODEL);
    }

    private static MCH_HeliInfo buildFallback() {
        MCH_HeliInfo hi = new MCH_HeliInfo("");
        hi.speed = 0.35F;
        hi.gravity = -0.04F;
        hi.gravityInWater = -0.04F;
        // Keep the constructor's default rotorSpeed (getDefaultRotorSpeed = 79.99) so an unknown-config heli's blades
        // still spin — the old 0.5 placeholder was dead when the spin rate was hardcoded, but now it drives the spin.
        hi.throttleUpDown = 1.0F;
        hi.isFloat = false;
        hi.maxFuel = 0;
        return hi;
    }
}
