package mcheli.dependent.entity;

import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.plane.MCP_PlaneInfo;
import mcheli.agnostic.plane.MCP_PlaneInfoManager;
import mcheli.agnostic.sim.AircraftFlightController;
import mcheli.agnostic.sim.AircraftSimState;
import mcheli.agnostic.sim.ControlInput;
import mcheli.agnostic.sim.FlightModel;
import mcheli.agnostic.sim.PlaneControlMapping;
import mcheli.agnostic.sim.PlaneFlightModel;
import mcheli.agnostic.sim.PlaneState;
import mcheli.agnostic.sim.RotationSolver;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * The fixed-wing plane entity — ONE class serving EVERY plane config (a-10, f-15, su-27, …), selected by the synced
 * config name; the per-category flight model is {@code PlaneFlightModel} (vectored-thrust aerodynamics). Holding
 * throttle-up spools the engine and it flies; a pilotless plane makes no thrust and sinks. The concrete
 * {@link MCP_PlaneInfo} is resolved from the config name via {@link MCP_PlaneInfoManager}.
 */
public class MchPlane extends AbstractMchVehicle {

    private static final FlightModel MODEL = new PlaneFlightModel();
    private static final MCP_PlaneInfo FALLBACK = buildFallback();

    private final PlaneState planeState = new DemoPlaneState(this);
    private final AircraftSimState simState = new AircraftSimState(0.07);
    private final AircraftSimState rotState = new AircraftSimState();
    private RotationSolver.ControlMapping mapping; // lazy — needs the resolved concrete MCP_PlaneInfo

    // VTOL thrust vectoring (#28). The pilot toggles the nozzle STATUS (target); the angle ramps deterministically on
    // BOTH sides (1.5°/tick toward 0=forward / 90=hover), so only the boolean needs syncing. The already-ported
    // PlaneFlightModel + PlaneControlMapping consume it via the PlaneState seam (DemoPlaneState delegates to these).
    private static final EntityDataAccessor<Boolean> DATA_VTOL =
        SynchedEntityData.defineId(MchPlane.class, EntityDataSerializers.BOOLEAN);
    private float nozzleRotation;      // 0 (forward thrust) .. 90 (vertical/hover) — reference MCH_Parts rotation
    private float prevNozzleRotation;  // render interpolation
    private int modeSwitchCooldown;    // reference getModeSwitchCooldown (20 ticks after a toggle)

    public MchPlane(EntityType<? extends MchPlane> type, Level level) {
        super(type, level);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_VTOL, false); // fixed-wing by default — bundled non-VTOL planes are unchanged
    }

    @Override
    public void tick() {
        super.tick();
        tickNozzle(); // BOTH sides: ramp the nozzle toward its target + tick down the toggle cooldown
    }

    // ---- VTOL (#28) — a near-clone of the rotor-fold (#19) discrete-toggle state machine ----

    /** This plane's config enables VTOL (reference {@code isEnableVtol}) — the harrier + f-35b + the MV-22; f-35a/c
     *  carry draw-only nozzle parts and never toggle. */
    public boolean isVtolEnabled() {
        return weaponHostInfo() instanceof MCP_PlaneInfo pi && pi.isEnableVtol;
    }

    /** This plane is a DEFAULT-VTOL aircraft (reference {@code isDefaultVtol}) — the MV-22 Osprey: it rests in VTOL,
     *  spawns with nozzles already vertical, auto-engages VTOL whenever grounded, and cannot toggle to fixed-wing while
     *  on the ground. */
    public boolean isDefaultVtol() {
        return weaponHostInfo() instanceof MCP_PlaneInfo pi && pi.isDefaultVtol;
    }

    /** Nozzle angle 0 (forward thrust) .. 90 (vertical/hover) — feeds the agnostic thrust vectoring in PlaneFlightModel. */
    public float getNozzleRotation() { return this.nozzleRotation; }

    /** Interpolated nozzle angle for the render (the nozzle-part model swivel is deferred to #28b). */
    public float nozzleRotationLerp(float pt) {
        return this.prevNozzleRotation + (this.nozzleRotation - this.prevNozzleRotation) * pt;
    }

    /** Reference {@code MCP_EntityPlane.getVtolMode}:942 — 0 = forward, 1 = in transition, 2 = full hover. The synced
     *  boolean is the nozzle TARGET; the mode derives from the target + the current angle (exact reference thresholds). */
    public int getVtolMode() {
        boolean statusOn = this.entityData.get(DATA_VTOL);
        if (!statusOn) {
            return this.nozzleRotation <= 0.005F ? 0 : 1;
        }
        return this.nozzleRotation >= 89.995F ? 2 : 1;
    }

    /** Reference {@code canSwitchVtol}:912 — VTOL-enabled, off cooldown, not mid-transition, near-level roll (≤30°),
     *  and NOT a grounded default-VTOL plane (the MV-22 is locked in VTOL while parked, reference:929). */
    public boolean canSwitchVtol() {
        return isVtolEnabled()
            && this.modeSwitchCooldown == 0
            && getVtolMode() != 1
            && !(onGround() && isDefaultVtol())
            && Math.abs(net.minecraft.util.Mth.wrapDegrees(getRollAngle())) <= 30.0F;
    }

    /** Server-authoritative VTOL toggle (reference {@code swithVtolMode} via KeyExtra). Pilot only, gated by
     *  {@link #canSwitchVtol}; flips the nozzle target and starts the 20-tick cooldown. */
    public void toggleVtol(Entity pilot) {
        if (this.level().isClientSide || seatIndexOf(pilot) != 0 || !canSwitchVtol()) {
            return;
        }
        this.entityData.set(DATA_VTOL, !this.entityData.get(DATA_VTOL));
        this.modeSwitchCooldown = 20;
    }

    /** Ramp the nozzle toward its target at the reference 1.5°/tick ({@code MCH_Parts.rotationInv}) on BOTH sides —
     *  deterministic + throttle-independent, so client and server reach the endpoint on the same tick — and tick the
     *  toggle cooldown down. No-op for a non-VTOL plane already at rest. */
    private void tickNozzle() {
        if (!isVtolEnabled() && this.nozzleRotation == 0.0F) {
            return;
        }
        // DefaultVtol (MV-22): re-engage VTOL whenever grounded (reference onUpdateAircraft:184) so it always lands +
        // rests in hover, and cannot be left in fixed-wing on the ground.
        if (!this.level().isClientSide && isDefaultVtol() && onGround() && getVtolMode() == 0) {
            this.entityData.set(DATA_VTOL, true);
        }
        this.prevNozzleRotation = this.nozzleRotation;
        float target = this.entityData.get(DATA_VTOL) ? 90.0F : 0.0F;
        if (this.nozzleRotation < target) {
            this.nozzleRotation = Math.min(target, this.nozzleRotation + 1.5F);
        } else if (this.nozzleRotation > target) {
            this.nozzleRotation = Math.max(target, this.nozzleRotation - 1.5F);
        }
        if (this.modeSwitchCooldown > 0) {
            this.modeSwitchCooldown--;
        }
    }

    @Override protected MCH_AircraftInfo resolveInfo(String name) {
        MCP_PlaneInfo i = MCP_PlaneInfoManager.get(name);
        return i != null ? i : FALLBACK;
    }

    @Override protected String modelDir() { return "planes"; }

    /** Reference {@code MCP_ClientPlaneTickHandler} sweeps the plane radar at 10°/tick (twice the heli/vehicle rate). */
    @Override protected int radarSweepSpeed() { return 10; }


    /** Fuel burns with the FLIGHT-SIM throttle (reference getThrottle), not the display enginePower —
     *  which idles at 0.5 while merely ridden and would drain the tank at rest. */
    @Override protected double simThrottle() { return this.simState.getCurrentThrottle(); }

    // Reference MCP_EntityPlane.getSoundVolume/getSoundPitch: volume = SoundVolume × slewedThrottle × 0.7; pitch [0.6,1.0].
    // (The port's engine power is already spooled, so it stands in for the reference's separately-slewed soundVolume.)
    @Override protected String defaultEngineSound() { return "plane"; }
    @Override public float engineSoundVolume() { return configSoundVolume() * getThrottleInput() * 0.7F; }
    @Override public float engineSoundPitch() { return configSoundPitch() * (0.6F + getThrottleInput() * 0.4F); }

    /** Reference {@code MCP_EntityPlane.canSwitchGunnerMode}:140 — additionally requires a near-level attitude
     *  (|roll|,|pitch| ≤ 40°), fast throttle ({@code > 0.6}), and being airborne, so you cannot gun a parked plane. */
    @Override public boolean canSwitchGunnerMode() {
        if (!super.canSwitchGunnerMode()) {
            return false;
        }
        float roll = Math.abs(net.minecraft.util.Mth.wrapDegrees(getRollAngle()));
        float pitch = Math.abs(net.minecraft.util.Mth.wrapDegrees(getXRot()));
        if (roll > 40.0F || pitch > 40.0F) {
            return false;
        }
        return simThrottle() > 0.6 && !onGround(); // airborne proxy for the reference getBlockIdY(this,3,-5)==0
    }
    @Override protected RotationSolver.ControlMapping controlMapping() {
        if (this.mapping == null) {
            this.mapping = new PlaneControlMapping(this.ref, (MCP_PlaneInfo) weaponHostInfo(), this.rotState, this.planeState);
        }
        return this.mapping;
    }
    @Override protected void onConfigChanged() {
        this.mapping = null;
        // Reference createNozzle forceSwitch(true): a default-VTOL plane (MV-22) resolves its config already in hover —
        // nozzles snapped vertical (no ramp). The server owns the synced status; both sides snap the local angle.
        if (isDefaultVtol()) {
            if (!this.level().isClientSide) {
                this.entityData.set(DATA_VTOL, true);
            }
            this.nozzleRotation = 90.0F;
            this.prevNozzleRotation = 90.0F;
        }
    }

    /** A plane wreck rolls FURTHER into its existing bank (reference {@code MCP_EntityPlane}): the roll seed's SIGN
     *  follows the lean (positive-roll banks seed positive, negative-roll banks seed negative), inverting only past
     *  ±90° so a plane already rolled past vertical keeps going over — so a banked plane spirals in, either way. */
    @Override protected void seedWreckTumble() {
        float r = net.minecraft.util.Mth.wrapDegrees(getRollAngle());
        float inv = r >= 0.0F ? (r > 90.0F ? -1.0F : 1.0F) : (r > -90.0F ? -1.0F : 1.0F);
        setRotDestroyedPitch(this.random.nextFloat() - 0.5F);
        setRotDestroyedRoll((0.5F + this.random.nextFloat()) * inv);
        setRotDestroyedYaw(0.0F);
    }
    /** Plane tumble: while airborne (no block within 3 below), pitch nose-over (guarded) and keep rolling unless near
     *  inverted-level (reference gate {@code roll<45 || roll>135}); once near the ground, gradually level a steep nose. */
    @Override protected void applyWreckTumble() {
        if (!solidBlockBelow()) {
            if (Math.abs(net.minecraft.util.Mth.wrapDegrees(getXRot())) < 10.0F) {
                setXRot(getXRot() + rotDestroyedPitch());
            }
            float roll = Math.abs(net.minecraft.util.Mth.wrapDegrees(getRollAngle()));
            if (roll < 45.0F || roll > 135.0F) {
                setRollAngle(getRollAngle() + rotDestroyedRoll());
            }
        } else if (Math.abs(net.minecraft.util.Mth.wrapDegrees(getXRot())) > 20.0F) {
            setXRot(getXRot() * 0.99F); // near-ground: settle a steep nose toward level (reference MCP_EntityPlane:688)
        }
    }

    @Override
    protected void tickPhysics(ControlInput in) {
        AircraftFlightController.tickServer(this.ref, weaponHostInfo(), this.simState, in, this.planeState, MODEL);
    }

    private static MCP_PlaneInfo buildFallback() {
        MCP_PlaneInfo pi = new MCP_PlaneInfo("");
        pi.speed = 0.4F;
        pi.gravity = -0.04F;
        pi.gravityInWater = -0.04F;
        pi.motionFactor = 0.96F;
        pi.throttleUpDown = 1.0F;
        pi.isFloat = false;
        pi.maxFuel = 0;
        pi.isVariableSweepWing = false;
        return pi;
    }
}
