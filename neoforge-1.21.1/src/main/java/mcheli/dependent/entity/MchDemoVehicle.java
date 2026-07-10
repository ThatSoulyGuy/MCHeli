package mcheli.dependent.entity;

import mcheli.agnostic.sim.AircraftFlightController;
import mcheli.agnostic.sim.AircraftSimState;
import mcheli.agnostic.sim.AircraftState;
import mcheli.agnostic.sim.ControlInput;
import mcheli.agnostic.sim.FlightModel;
import mcheli.agnostic.sim.VehicleFlightModel;
import mcheli.agnostic.spi.EntityRef;
import mcheli.agnostic.vehicle.MCH_VehicleInfo;
import mcheli.dependent.port.NeoEntityRef;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The vertical-slice demo vehicle: a minimal rideable {@link Entity} whose per-tick movement is now driven by
 * the REAL ported MCHeli physics — the agnostic {@code VehicleFlightModel} run via {@link AircraftFlightController},
 * reached through the {@link NeoEntityRef} port. Right-click to mount; it drives forward under the ported ground
 * physics (gravity, directional thrust, speed clamp, on-ground friction, post-move damping).
 *
 * <p>This proves the whole stack end-to-end: agnostic flight sim -> EntityRef/MCH_VehicleInfo -> dependent
 * entity -> in-game. Control input is hard-coded "drive forward" for now (a proper player-input provider is the
 * remaining wiring); {@code VehicleFlightModel} with {@code isFloat=false} never touches {@code WorldView}, so no
 * {@code NeoWorldView} is required yet.
 */
public class MchDemoVehicle extends Entity {

    /** The agnostic view of this entity. Reused every tick so the physics never sees a Minecraft type. */
    private final EntityRef ref = new NeoEntityRef(this);

    // The real agnostic flight sim, wired in: the ground-vehicle FlightModel + its per-vehicle definition + its
    // mutable sim state. This replaces the SimpleVehicleLogic placeholder.
    private static final FlightModel MODEL = new VehicleFlightModel();
    private static final AircraftState ALIVE = () -> false; // never destroyed
    private static final ControlInput DRIVE_FORWARD = ControlInput.ground(true, false, false, false);
    private final MCH_VehicleInfo info = buildInfo();
    private final AircraftSimState simState = new AircraftSimState(0.07);

    private static MCH_VehicleInfo buildInfo() {
        MCH_VehicleInfo vi = new MCH_VehicleInfo("demo_vehicle");
        vi.speed = 0.3F;
        vi.gravity = -0.04F;
        vi.gravityInWater = -0.04F;
        vi.motionFactor = 0.96F;
        vi.throttleUpDown = 1.0F;
        vi.isFloat = false;
        vi.isEnableMove = true;
        return vi;
    }

    public MchDemoVehicle(EntityType<? extends MchDemoVehicle> type, Level level) {
        super(type, level);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { /* no synced slots yet */ }
    @Override protected void readAdditionalSaveData(CompoundTag tag) { /* nothing to persist yet */ }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { /* nothing to persist yet */ }

    @Override public boolean isPickable() { return !this.isRemoved(); }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canBeCollidedWith() { return true; }

    // NOTE: deliberately DO NOT override getControllingPassenger() to return the rider.
    // On Minecraft 1.21.1 the vehicle-movement protocol is CLIENT-authoritative: when the controlling
    // passenger is the local Player, the client sends a ServerboundMoveVehiclePacket every tick and the
    // server snaps the vehicle to the client's reported position (Entity#absMoveTo) — which would clobber
    // our server-side physics each tick and make a human rider stall/rubber-band. Keeping the default (null)
    // leaves the vehicle SERVER-authoritative. The real MCH vehicle hierarchy must honor this same constraint
    // (server-authoritative physics + no naive getControllingPassenger()->Player), or add explicit client-input handling.

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (this.level().isClientSide) {
            return InteractionResult.SUCCESS; // let the client predict the mount
        }
        return player.startRiding(this) ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    @Override
    public void tick() {
        super.tick();

        // Server-authoritative movement: the physics runs only on the server, and the entity tracker syncs the
        // result to clients (which interpolate it smoothly). Running it on both sides would make the client copy
        // fight the authoritative server position and jitter.
        if (!this.level().isClientSide) {
            // Snapshot previous position/rotation before moving so any server-side prevPosition() read (and the
            // spawn/track deltas) reflect the start of this tick.
            this.xo = this.getX();
            this.yo = this.getY();
            this.zo = this.getZ();
            this.yRotO = this.getYRot();
            this.xRotO = this.getXRot();

            // Real ported physics: AircraftFlightController sequences snapshot -> throttle -> force integration
            // -> collision move -> post-move damp, delegating to the ground-vehicle FlightModel. Applied back to
            // this entity through the NeoEntityRef port.
            AircraftFlightController.tickServer(this.ref, this.info, this.simState, DRIVE_FORWARD, ALIVE, MODEL);
        }
    }
}
