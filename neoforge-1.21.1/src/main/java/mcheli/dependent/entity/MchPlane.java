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

    private final PlaneState planeState = new DemoPlaneState(this::isDestroyed);
    private final AircraftSimState simState = new AircraftSimState(0.07);
    private final AircraftSimState rotState = new AircraftSimState();
    private RotationSolver.ControlMapping mapping; // lazy — needs the resolved concrete MCP_PlaneInfo

    public MchPlane(EntityType<? extends MchPlane> type, Level level) {
        super(type, level);
    }

    @Override protected MCH_AircraftInfo resolveInfo(String name) {
        MCP_PlaneInfo i = MCP_PlaneInfoManager.get(name);
        return i != null ? i : FALLBACK;
    }

    @Override protected String modelDir() { return "planes"; }

    @Override protected RotationSolver.ControlMapping controlMapping() {
        if (this.mapping == null) {
            this.mapping = new PlaneControlMapping(this.ref, (MCP_PlaneInfo) weaponHostInfo(), this.rotState, this.planeState);
        }
        return this.mapping;
    }
    @Override protected void onConfigChanged() { this.mapping = null; }

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
