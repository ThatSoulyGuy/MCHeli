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
import mcheli.agnostic.physics.MCH_Rotor;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
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

    // ---- Rotor-blade fold (reference MCH_EntityHeli.foldBladeStat + the MCH_Rotor blade animator) ----
    // Synced fold state: 0 = folded, 1 = folding, 2 = unfolded, 3 = unfolding. Server-authoritative; defaults to
    // unfolded so bundled helis fly unchanged and folding is an opt-in stow the pilot triggers while parked.
    private static final EntityDataAccessor<Byte> DATA_FOLD =
        SynchedEntityData.defineId(MchHelicopter.class, EntityDataSerializers.BYTE);

    private MCH_Rotor[] foldRotors;   // lazy per-config blade animators, on BOTH sides; null until the first fold tick
    private byte lastFoldStat = -1;   // edge-detects the synced state so startFold/startUnfold fire exactly once
    private int foldCooldown;         // reference foldBladesCooldown — blocks re-toggling right after a switch
    private float foldRotorSpin;      // reference rotationRotor: throttle-driven, so it FREEZES when parked and the
    private float prevFoldRotorSpin;  // fold sweep runs deterministically + identically on client and server

    public MchHelicopter(EntityType<? extends MchHelicopter> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FOLD, (byte) 2); // unfolded
    }

    @Override
    protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("FoldBlade", foldStat() == 0); // reference persists folded vs not; transient states collapse
    }

    @Override
    protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("FoldBlade")) {
            // Reload folded (0) or unfolded (2); ensureFoldRotors force-folds the blades on the first tick when 0.
            this.entityData.set(DATA_FOLD, tag.getBoolean("FoldBlade") ? (byte) 0 : (byte) 2);
        }
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
            // Light dust kicked up by the rotor — the MCHeli smoke.png billboard (port of MCH_EntityParticleSmoke),
            // a pale, semi-transparent puff, replacing the vanilla CLOUD stand-in.
            this.level().addParticle(
                new mcheli.dependent.particle.MchSmokeOptions(scale * 0.15F, 0xB0CFCFC7, 12 + this.random.nextInt(8)),
                x, y, z, scale * dxr * 0.3, scale * -0.02, scale * dzr * 0.3); // blows radially outward + slightly down
        }
    }

    /** Reference {@code MCH_EntityHeli.canSwitchGunnerMode}:293 — additionally requires usable (unfolded) blades AND a
     *  near-level attitude (|roll| &lt; 40°, |pitch| &lt; 40°). Since #19 wired {@link #canUseBlades()} to the fold
     *  state, a folded / mid-fold heli must NOT enter gunner mode. */
    @Override public boolean canSwitchGunnerMode() {
        if (!super.canSwitchGunnerMode() || !canUseBlades()) {
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

    @Override
    public void tick() {
        super.tick();
        tickFoldBlades(); // runs on BOTH sides after the base client/server split (rotor animation + state settle)
    }

    /** Whether this heli's config enables blade folding and actually has rotors — the port of the
     *  {@code isEnableFoldBlade && rotors.length > 0} guard the reference repeats before every fold operation. */
    public boolean isFoldEnabled() {
        MCH_AircraftInfo info = weaponHostInfo();
        return info instanceof MCH_HeliInfo hi && hi.isEnableFoldBlade && !hi.rotorList.isEmpty();
    }

    /** The synced fold state: 0 folded, 1 folding, 2 unfolded, 3 unfolding. */
    public byte foldStat() { return this.entityData.get(DATA_FOLD); }

    private void setFoldStat(byte b) {
        if (!this.level().isClientSide && b >= 0 && b <= 3) {
            this.entityData.set(DATA_FOLD, b);
        }
    }

    /** Reference {@code isFoldBlades}: the blades are stowed (state 0). */
    public boolean isFolded() { return isFoldEnabled() && foldStat() == 0; }

    /** Reference {@code MCH_EntityHeli.canUseBlades}: the rotor can lift only when fully unfolded and settled — so the
     *  flight sim (via {@link mcheli.agnostic.sim.HeliState#canUseBlades}) makes no lift while folded or mid-fold. */
    @Override
    public boolean canUseBlades() {
        if (!isFoldEnabled()) {
            return true;
        }
        if (foldStat() != 2) {
            return false;
        }
        if (this.foldRotors != null) {
            for (MCH_Rotor r : this.foldRotors) {
                if (r.isFoldingOrUnfolding()) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Reference {@code canSwitchFoldBlades}: only from a settled state (folded/unfolded), with the throttle idle and
     *  the post-switch cooldown expired. */
    public boolean canSwitchFoldBlades() {
        return isFoldEnabled()
            && getThrottleInput() <= 0.01F
            && this.foldCooldown == 0
            && (foldStat() == 2 || foldStat() == 0);
    }

    /** Reference {@code onUpdate_ControlFoldBladeAndOnGround} gate: folded, grounded, and intact. */
    @Override
    public boolean isFoldedOnGround() {
        return isFolded() && this.onGround() && !isDestroyed();
    }

    /** Server-authoritative fold toggle (reference {@code KeyExtra} → {@code foldBlades}/{@code unfoldBlades}). Pilot
     *  only, gated by {@link #canSwitchFoldBlades}; sets the transient folding/unfolding state and {@link
     *  #tickFoldBlades} runs the animation and settles it. */
    public void toggleFold(Player pilot) {
        if (this.level().isClientSide || seatIndexOf(pilot) != 0 || !canSwitchFoldBlades()) {
            return;
        }
        if (foldStat() == 0) {
            setFoldStat((byte) 3); // folded -> begin unfolding
        } else if (foldStat() == 2) {
            setFoldStat((byte) 1); // unfolded -> begin folding
        }
    }

    /** Port of {@code MCH_EntityHeli.onUpdate_Rotor}: advances the throttle-driven rotor angle, drives each rotor's
     *  fold/unfold animation off the synced state, and (server-side) settles the transient state once every blade
     *  reaches its target. No-op unless the config enables folding. */
    private void tickFoldBlades() {
        if (!isFoldEnabled()) {
            return;
        }
        ensureFoldRotors();
        // Rotor angle fed to the animator (reference rotationRotor): rises with the sim throttle, freezes at idle. The
        // throttle is synced, so client and server compute the same angle -> the fold sweep completes on the same tick.
        this.prevFoldRotorSpin = this.foldRotorSpin;
        float rp = 1.0F - getThrottleInput();
        float speed = weaponHostInfo().rotorSpeed;
        this.foldRotorSpin = (this.foldRotorSpin + (1.0F - rp * rp * rp) * speed) % 360.0F;

        byte stat = foldStat();
        if (stat != this.lastFoldStat) {
            if (stat == 1) {
                for (MCH_Rotor r : this.foldRotors) r.startFold();
            } else if (stat == 3) {
                for (MCH_Rotor r : this.foldRotors) r.startUnfold();
            }
            this.foldCooldown = 40; // reference resets the re-toggle guard on every state change (both sides here)
            this.lastFoldStat = stat;
        } else if (this.foldCooldown > 0) {
            this.foldCooldown--;
        }

        boolean settled = true;
        for (MCH_Rotor r : this.foldRotors) {
            r.update(this.foldRotorSpin);
            if (r.isFoldingOrUnfolding()) {
                settled = false;
            }
        }
        if (settled && !this.level().isClientSide) { // the server advances the transient states once the sweep ends
            if (stat == 1) {
                setFoldStat((byte) 0); // folding -> folded
            } else if (stat == 3) {
                setFoldStat((byte) 2); // unfolding -> unfolded
            }
        }
    }

    private void ensureFoldRotors() {
        if (this.foldRotors != null) {
            return;
        }
        MCH_HeliInfo hi = (MCH_HeliInfo) weaponHostInfo();
        java.util.List<MCH_HeliInfo.Rotor> list = hi.rotorList;
        MCH_Rotor[] arr = new MCH_Rotor[list.size()];
        for (int i = 0; i < arr.length; i++) {
            MCH_HeliInfo.Rotor r = list.get(i);
            // Reference createRotors: foldSpeed = 2 on both sides; invRot = the config bladeRot so an unfolded blade i
            // sits at (spin + i*bladeRot), identical to the non-fold render path.
            arr[i] = new MCH_Rotor(r.bladeNum, r.bladeRot, 2,
                (float) r.pos.x(), (float) r.pos.y(), (float) r.pos.z(),
                (float) r.rot.x(), (float) r.rot.y(), (float) r.rot.z(), r.haveFoldFunc);
        }
        this.foldRotors = arr;
        if (foldStat() == 0) { // built into an already-folded world state -> collapse the blades immediately
            for (MCH_Rotor r : arr) r.forceFold();
            this.lastFoldStat = 0;
        }
    }

    /** CLIENT: true once the fold animator is live, so the renderer draws each blade at its own (possibly folded)
     *  angle instead of the shared spin. */
    public boolean foldActive() { return isFoldEnabled() && this.foldRotors != null; }

    /** CLIENT: current animated angle (deg) of blade {@code bi} on rotor {@code ri}. */
    public float bladeAngle(int ri, int bi) {
        return this.foldRotors != null && ri < this.foldRotors.length ? this.foldRotors[ri].getBladeRotaion(bi) : 0.0F;
    }

    /** CLIENT: previous-tick angle of blade {@code bi} on rotor {@code ri}, for render interpolation. */
    public float prevBladeAngle(int ri, int bi) {
        return this.foldRotors != null && ri < this.foldRotors.length && bi < this.foldRotors[ri].blades.length
            ? this.foldRotors[ri].blades[bi].getPrevRotation() : 0.0F;
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
