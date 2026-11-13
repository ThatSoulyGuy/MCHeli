package mcheli.dependent.entity;

import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.aircraft.MCH_SeatInfo;
import mcheli.agnostic.sim.AimLatch;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Where the crew sit and look: the pilot-aim latch and the ±15°/tick turret slew (the agnostic {@link AimLatch}), the
 * seat attachment (rotSeat orbit, body rotation, the {@code -minY} lift), the first-person and config camera eyes,
 * dismount placement, the seat-pick offsets, the model minY, the HideGM groups and the selected view camera.
 *
 * <p>Common code (the attachment runs every server tick through {@code positionRider}); it holds only the
 * back-reference to its vehicle and writes no synced accessor. The JOML float maths moved verbatim from the entity.
 */
final class MchRiderPose {
    private final AbstractMchVehicle v;

    // Aim latch — the reference lastRiderYaw/lastRiderPitch (MCH_EntityAircraft:1486-1497, NBT "AcLastRYaw"): the
    // PILOT's look, refreshed only while a live pilot is aboard and the craft is intact, and HELD after dismount so
    // the turret/guns/crew-seats keep their last pose instead of snapping to hull-forward.
    // Turret slew (reference updateWeaponsRotation ±15°/tick): the shared turret ring EASES toward the operator's aim at
    // a capped rate instead of snapping. Tracked in WORLD space (so a hull turn does not drag it — only a change in the
    // operator's own look slews it), stepped once per tick on BOTH sides; turretOrbitYaw() derives the rel-hull angle.
    // render, the rotSeat crew orbit, and the ring-mounted muzzle + shot all read that one angle, so they stay locked.
    private final AimLatch aim = new AimLatch();

    // CLIENT: the pilot's selected config CameraPosition index (reference MCH_EntityAircraft.cameraId). Purely a local
    // view state — the reference only ever reads it through the client player (getCameraPosInfo), so it is never synced.
    // 0 = the default eye (cameraPosition[0]); >0 = an alternate configured viewpoint, cycled by the gunner/view key.
    private int viewCameraId;

    /** Cached lowest model vertex Y (&le;0) for the current config; the renderer lifts the MODEL by {@code -minY} (the
     *  {@code #5} ground-offset fix), so the rider seat + cockpit camera must lift by the same amount to stay ON the
     *  model. Resolved lazily from the parsed model (both sides, classpath-cached), invalidated on config change and
     *  on a model-cache reload (F3+T, {@link mcheli.dependent.port.MchContentPacks#modelGeneration}). */
    private float modelMinY;
    private boolean minYResolved;
    private int minYGeneration;

    /** Cached lower-cased {@code $weaponN} groups the config hides in the first-person gunner view (HideGM). */
    private java.util.Set<String> hideGmGroups;

    MchRiderPose(AbstractMchVehicle v) {
        this.v = v;
    }

    /** A config change: the model lift and the HideGM groups re-resolve, and the view camera falls back to the
     *  default eye. The aim latch is kept. */
    void onConfigChanged() {
        this.minYResolved = false;
        this.hideGmGroups = null;
        this.viewCameraId = 0; // a different config may have fewer camera positions — fall back to the default eye
    }

    // ---- aim latch + turret slew ----

    /** Refresh the pilot-aim latch once per tick (call from the entity tick, both sides). */
    void updateAimLatch() {
        Entity p = v.pilot();
        boolean live = p != null && p.isAlive() && !v.isDestroyed();
        this.aim.update(live, live ? p.getYRot() : 0.0F, live ? p.getXRot() : 0.0F, v.getYRot());
    }

    /**
     * The TURRET yaw, relative to the hull — the ONE source shared by the rotSeat seat orbit, the weapon-ring in
     * {@code renderWeaponParts}, and the emplacement's VPart turret. All three must read this (textually equivalent)
     * or a crewed tank shears: seat, turret mesh and camera would each follow a different angle.
     */
    float turretOrbitYaw(float partialTick) {
        if (!this.aim.slewInit()) {
            return 0.0F; // never ticked -> hull-forward
        }
        // The rate-limited world-space turret yaw (updateTurretSlew), render-interpolated, expressed relative to the
        // interpolated hull. Deriving rel-hull here (rather than slewing a rel-hull value) keeps a hull turn from
        // dragging the turret: the world slew target is the operator's look, unaffected by the hull spinning under it.
        float worldYaw = MchVehicleMath.shortLerpDeg(this.aim.slew(), this.aim.prevSlew(), partialTick);
        float hullYaw = Mth.rotLerp(partialTick, v.yRotO, v.getYRot());
        return Mth.wrapDegrees(worldYaw - hullYaw);
    }

    /** The pilot's look PITCH (absolute) — live while piloted, latched after dismount. The emplacement/turret elevation. */
    float turretAimPitch(float partialTick) {
        Entity p = v.pilot();
        if (p != null) {
            return Mth.lerp(partialTick, p.xRotO, p.getXRot());
        }
        return this.aim.latched() ? Mth.lerp(partialTick, this.aim.prevPitch(), this.aim.pitch()) : 0.0F;
    }

    /** NBT {@code AcLastRYaw}/{@code AcLastRPitch} (reference {@code readEntityFromNBT} :748-753): a saved latch is
     *  restored latched, with no interpolation step. */
    void loadAimLatch(CompoundTag tag) {
        if (tag.contains("AcLastRYaw")) {
            this.aim.restore(tag.getFloat("AcLastRYaw"), tag.getFloat("AcLastRPitch"));
        }
    }

    /** NBT {@code AcLastRYaw}/{@code AcLastRPitch}, written only while latched. */
    void saveAimLatch(CompoundTag tag) {
        if (this.aim.latched()) {
            // Only once a pilot has set it: an unlatched vehicle keeps aiming hull-forward after a reload.
            tag.putFloat("AcLastRYaw", this.aim.yaw());
            tag.putFloat("AcLastRPitch", this.aim.pitch());
        }
    }

    // ---- the selected view camera (client) ----

    /** CLIENT: how many config {@code CameraPosition} entries this vehicle has (reference {@code getCameraPosNum}). */
    int cameraPosCount() {
        MCH_AircraftInfo info = v.weaponHostInfo();
        return info != null && !info.cameraPosition.isEmpty() ? info.cameraPosition.size() : 1;
    }

    /** CLIENT: at least two configured viewpoints, so the view key can cycle them (reference {@code canSwitchCameraPos}). */
    boolean canSwitchCameraPos() {
        return v.cameraPosCount() >= 2;
    }

    /** CLIENT: the pilot's currently-selected {@code CameraPosition} index, clamped into range. */
    int getViewCameraId() {
        int n = v.cameraPosCount();
        return this.viewCameraId > 0 && this.viewCameraId < n ? this.viewCameraId : 0;
    }

    /** CLIENT: select a config {@code CameraPosition} index (out-of-range collapses to 0, the default eye). */
    void setViewCameraId(int id) {
        this.viewCameraId = id > 0 && id < v.cameraPosCount() ? id : 0;
    }

    // ---- seat attachment, body rotation, eyes, dismount ----

    /**
     * Seats the pilot faithfully — the 1.21.1 port of {@code MCH_EntityAircraft.updateRiderPosition} +
     * {@code getTransformedPosition(...,rotSeat)}. Three composed stages on the config seat's FEET offset
     * ({@code seatY − 0.5}):
     * <ol>
     *   <li><b>rotSeat orbit</b> — a turret-mounted gunner seat (config {@code rotSeat}, e.g. the m1a2's
     *       {@code AddGunnerSeat}) orbits {@code turretPosition} in the X-Z plane by the turret yaw
     *       ({@code riderHeadYaw − bodyYaw}), the SAME {@code Ry(-turretYaw)} the turret renderer applies — so the
     *       rider stays welded to the aiming turret instead of appearing detached;</li>
     *   <li><b>body orientation</b> — {@code Ry(-yaw)·Rx(pitch)·Rz(roll)}, interpolated to match the model renderer
     *       and cockpit camera so a banking craft's seat never drifts;</li>
     *   <li><b>{@code -minY} lift</b> — world-vertical, matching the renderer's model lift, so the rider sits ON the
     *       lifted model rather than {@code |minY|} below it.</li>
     * </ol>
     * Vanilla passenger attachments rotate by yaw only, which is why this override is required. JOML math is common to
     * both sides, so this is dist-safe. {@code getPassengerRidingPosition} adds {@code this.position()} on top.
     * <p>The body of {@link AbstractMchVehicle#getPassengerAttachmentPoint}: null where that override falls back to
     * vanilla's {@code super} attachment (no config seat, or the passenger's seat not resolved yet).
     */
    Vec3 handlePassengerAttachmentPoint(Entity passenger, float partialTick) {
        MCH_AircraftInfo info = v.weaponHostInfo();
        if (info == null || info.seatList == null || info.seatList.isEmpty()) {
            return null;
        }
        int sid = v.seatIndexOf(passenger);
        if (sid < 0) {
            sid = v.getPassengers().isEmpty() || v.getPassengers().get(0) == passenger ? 0 : -1;
        }
        if (sid < 0 || sid >= info.seatList.size()) {
            return null; // seat not resolved yet
        }
        MCH_SeatInfo seat = info.seatList.get(sid);
        // The reference genuinely uses DIFFERENT y conventions per path — do not unify. Pilot (updateRiderPosition
        // :3201): feet at seat.y − 0.5. Crew seats (updateSeatsPosition :3142-3148): the client player is offset +1.0
        // (eye convention → feet at seat.y − 0.62); a tall mob sits at seat.y + (1 − height); a small mob at seat.y.
        double sy;
        if (sid == 0) {
            sy = seat.pos.y() - 0.5;
        } else if (passenger instanceof Player) {
            sy = seat.pos.y() - 0.62;
        } else if (passenger.getBbHeight() >= 1.0F) {
            sy = seat.pos.y() + (1.0 - passenger.getBbHeight());
        } else {
            sy = seat.pos.y();
        }
        org.joml.Vector3f p = new org.joml.Vector3f(
            (float) seat.pos.x(), (float) sy, (float) seat.pos.z());

        // (1) rotSeat: orbit the seat about turretPosition by the TURRET yaw, keeping Y (Ry never touches Y). The yaw is
        // turretOrbitYaw() — the latched PILOT's look (reference lastRiderYaw), NOT this passenger's own head: a tank
        // crew seat rides the turret the PILOT is slewing. Same expression as the renderers' ring => no shear.
        if (seat.rotSeat) {
            float turretYaw = v.turretOrbitYaw(partialTick);
            org.joml.Vector3f tp = new org.joml.Vector3f(
                (float) info.turretPosition.x(), (float) info.turretPosition.y(), (float) info.turretPosition.z());
            // DELIBERATE divergence: the reference (getTransformedPosition:3335-3337) re-adds turretPosition.xCoord to
            // ALL THREE components (a copy-paste bug — a no-op only when turretPosition is (0,0,0)). The intent is the
            // pure X-Z orbit below; only the merkava_mk4 (TurretPosition z=0.25, rider hidden) differs, by 0.25.
            p.sub(tp).rotateY((float) Math.toRadians(-turretYaw)).add(tp);
        }

        // (2) body orientation, interpolated to match the model renderer + cockpit camera exactly.
        p.rotate(v.bodyRotation(partialTick));

        // (3) -minY consistency lift (world-vertical, AFTER the body rotation) so the rider matches the lifted model.
        return new Vec3(p.x, (double) p.y - v.modelMinY(), p.z);
    }

    /** Stage-2 body rotation {@code Ry(-yaw)·Rx(pitch)·Rz(roll)}, interpolated — the single transform shared by the seat
     *  attachment, the config camera eye, and dismount placement (reference {@code MCH_Lib.RotVec3(-yaw,-pitch,-roll)}:
     *  MC {@code rotateAroundX(a) ≡ JOML rotateX(-a)}, so the reference triple maps EXACTLY to this quaternion). The
     *  search light ({@link MchVehicleVision#searchLightValue}) reads it through the entity bridge. */
    org.joml.Quaternionf bodyRotation(float partialTick) {
        float yaw = Mth.rotLerp(partialTick, v.yRotO, v.getYRot());
        float pitch = Mth.lerp(partialTick, v.xRotO, v.getXRot());
        float roll = MchVehicleMath.shortLerpDeg(v.getRollAngle(), v.getPrevRollAngle(), partialTick);
        return new org.joml.Quaternionf()
            .rotateY((float) Math.toRadians(-yaw))
            .rotateX((float) Math.toRadians(pitch))
            .rotateZ((float) Math.toRadians(roll));
    }

    /**
     * World-space first-person eye for the LOCAL rider, or null when the reference renders from the player itself and
     * the vanilla camera already answers (a free-look vehicle without {@code alwaysCameraView}). Reference:
     * {@code setupAllRiderRenderPosition:3266-3276} (config camera), {@code calcOnTurretPos:3218-3232} (rotSeat camera
     * orbit — pivot {@code (turretX, turretY+camY, turretZ)}, offset rotated by the rider's ABSOLUTE look yaw only,
     * pivot body-rotated: deliberately different math from the seat orbit), {@code updateRiderPosition} (seat eye).
     * {@code CameraPosition} IS the eye — never add the player eye height on top. Both paths carry the port's
     * {@code -minY} lift ({@code CameraPosition} is authored in the same model space as the seats).
     */
    Vec3 firstPersonEye(Entity rider, float partialTick) {
        MCH_AircraftInfo info = v.weaponHostInfo();
        if (info == null) {
            return null;
        }
        double vx = Mth.lerp((double) partialTick, v.xo, v.getX());
        double vy = Mth.lerp((double) partialTick, v.yo, v.getY());
        double vz = Mth.lerp((double) partialTick, v.zo, v.getZ());
        org.joml.Quaternionf body = v.bodyRotation(partialTick);

        // GUNNER CAMERA (getCameraPosInfo:685-699, the sid>0 branch): a gunner seat with its OWN camera position
        // (AddGunnerSeat sets invCamPos + a CameraPosition) renders from that camera while the seat is in gunner mode,
        // orbiting the turret if the seat is rotSeat. This is what detaches the view to the gun sight.
        int sid = v.seatIndexOf(rider);
        MCH_SeatInfo seat = info.seatList != null && sid > 0 && sid < info.seatList.size()
            ? info.seatList.get(sid) : null;
        if (seat != null && seat.invCamPos && seat.getCamPos() != null && v.isSeatGunnerMode(sid)) {
            return configCameraEye(seat.getCamPos().pos, seat.rotSeat, info, vx, vy, vz, body, partialTick);
        }

        // The pilot uses CameraPosition[camIdx] when the config forces the camera view (alwaysCameraView), when the pilot
        // has entered gunner mode, OR when they have cycled to an alternate viewpoint (cameraId>0). The reference renders
        // through the camera dummy for (isPilot && alwaysCameraView) || getIsGunnerMode || cameraId>0
        // (MCH_Client*TickHandler:~110), and getCameraPosInfo returns cameraPosition[cameraId] for seat 0. Gunner mode
        // resets cameraId to 0 (see the view-key handler), so it always shows cameraPosition[0].
        int camIdx = sid == 0 ? v.getViewCameraId() : 0;
        boolean pilotGunnerCam = sid == 0 && v.isSeatGunnerMode(0);
        if ((v.usesConfigCameraEye() || pilotGunnerCam || camIdx > 0) && !info.cameraPosition.isEmpty()) {
            MCH_SeatInfo seat0 = info.seatList == null || info.seatList.isEmpty() ? null : info.seatList.get(0);
            return configCameraEye(info.cameraPosition.get(pilotGunnerCam ? 0 : camIdx).pos,
                seat0 != null && seat0.rotSeat, info, vx, vy, vz, body, partialTick);
        }

        // Seat eye — for BOTH locked view and free-look-without-config-camera: eye = attachment (rotSeat-orbited,
        // body-rotated, -minY-lifted) + Rbody·(0,eyeHeight,0). The eye height rotates WITH the hull (the reference
        // 1.7.10 client-player posY IS the eye and rides getTransformedPosition, so the full 1.62 is body-rotated —
        // a vanilla world-Y eye height hangs off the hull axis on a pitched/rolled tank). The camera mixin overrides
        // POSITION only for a free-look vehicle; the rotation stays the player's own look.
        Vec3 att = v.getPassengerAttachmentPoint(rider, rider.getDimensions(rider.getPose()), partialTick);
        org.joml.Vector3f up = new org.joml.Vector3f(0.0F, rider.getEyeHeight(), 0.0F).rotate(body);
        return new Vec3(vx + att.x + up.x, vy + att.y + up.y, vz + att.z + up.z);
    }

    /** The eye at a config {@code CameraPosition} {@code cp} — the shared body of {@link #firstPersonEye}'s pilot and
     *  gunner camera branches ({@code setupAllRiderRenderPosition:3266-3276} + {@code calcOnTurretPos:3218-3232}). A
     *  {@code rotSeat} camera orbits {@code turretPosition} by the rider's absolute look yaw; otherwise it is fixed to
     *  the hull. {@code cp} IS the eye (no player eye height added); both carry the port's {@code -minY} lift. */
    private Vec3 configCameraEye(mcheli.agnostic.value.Vec3d cp, boolean rotSeat, MCH_AircraftInfo info,
                                 double vx, double vy, double vz, org.joml.Quaternionf body, float partialTick) {
        org.joml.Vector3f off;
        if (rotSeat) {
            org.joml.Vector3f tp = new org.joml.Vector3f(
                (float) info.turretPosition.x(),
                (float) (info.turretPosition.y() + cp.y()),
                (float) info.turretPosition.z());
            // The orbit is the PILOT's turret yaw (reference calcOnTurretPos ry = getRiddenByEntity().rotationYaw), NOT
            // the camera-owner's — a gunner's cupola camera stays fixed to the turret and only its VIEW rotates, exactly
            // like the gunner's rotSeat SEAT (which also orbits by the pilot's look). For the pilot's own camera the two
            // are the same rider.
            float orbitYaw = cameraOrbitYaw(partialTick);
            off = new org.joml.Vector3f((float) cp.x(), (float) cp.y(), (float) cp.z())
                .sub(tp)
                .rotateY((float) Math.toRadians(-orbitYaw))
                .add(tp.rotate(body));
        } else {
            off = new org.joml.Vector3f((float) cp.x(), (float) cp.y(), (float) cp.z()).rotate(body);
        }
        return new Vec3(vx + off.x, vy + (double) off.y - v.modelMinY(), vz + off.z);
    }

    /** The camera turret-orbit yaw (reference {@code calcOnTurretPos} {@code ry}): the PILOT's interpolated body yaw, or
     *  the aim latch if no pilot rides. Both the pilot's own turret camera and a gunner's cupola camera orbit by this. */
    private float cameraOrbitYaw(float partialTick) {
        Entity p = v.pilot();
        if (p != null) {
            return Mth.rotLerp(partialTick, p.yRotO, p.getYRot());
        }
        return this.aim.latched() ? Mth.rotLerp(partialTick, this.aim.prevYaw(), this.aim.yaw()) : v.getYRot();
    }

    /** Config-driven dismount — reference {@code setUnmountPosition:3787-3802}: the config {@code UnmountPosition}
     *  body-transformed, else {@code (seat.x ± 3 outward, 2.0, seat.z)}. The body of
     *  {@link AbstractMchVehicle#getDismountLocationForPassenger}: null where that override falls back to vanilla's
     *  {@code super} placement (no config seat). */
    Vec3 handleDismountLocationForPassenger(net.minecraft.world.entity.LivingEntity rider) {
        MCH_AircraftInfo info = v.weaponHostInfo();
        if (info == null || info.seatList == null || info.seatList.isEmpty()) {
            return null;
        }
        org.joml.Vector3f local;
        if (info.unmountPosition != null) {
            local = new org.joml.Vector3f((float) info.unmountPosition.x(),
                (float) info.unmountPosition.y(), (float) info.unmountPosition.z());
        } else {
            mcheli.agnostic.value.Vec3d s = info.seatList.get(0).pos;
            float x = s.x() >= 0 ? (float) s.x() + 3.0F : (float) s.x() - 3.0F;
            local = new org.joml.Vector3f(x, 2.0F, (float) s.z());
        }
        local.rotate(v.bodyRotation(1.0F));
        return v.position().add(local.x, local.y, local.z);
    }

    /** World-space offset of seat {@code i} (the same transform the attachment uses) — for seat picking. */
    Vec3 seatOffsetWorld(int seat, Entity rider, float partialTick) {
        MCH_AircraftInfo info = v.weaponHostInfo();
        MCH_SeatInfo si = info.seatList.get(seat);
        org.joml.Vector3f p = new org.joml.Vector3f((float) si.pos.x(), (float) si.pos.y(), (float) si.pos.z());
        if (si.rotSeat) {
            org.joml.Vector3f tp = new org.joml.Vector3f(
                (float) info.turretPosition.x(), (float) info.turretPosition.y(), (float) info.turretPosition.z());
            p.sub(tp).rotateY((float) Math.toRadians(-v.turretOrbitYaw(partialTick))).add(tp);
        }
        p.rotate(v.bodyRotation(partialTick));
        return new Vec3(p.x, (double) p.y - v.modelMinY(), p.z);
    }

    // ---- model lift and rider visibility ----

    /** Lowest model vertex Y (&le;0) for the current config — the value the renderer lifts the model by ({@code -minY}).
     *  Sourced once from the parsed model via the shared bundled+content-pack resource set (works both sides, so a pack
     *  vehicle lifts correctly on the server too), then cached until the config changes or the model caches reload
     *  (an edited model's lift is picked up by live vehicles); 0 when there is no model.
     *  Public on the entity so the cockpit {@code CameraMixin} can apply the same lift to the first-person eye. */
    float modelMinY() {
        int generation = mcheli.dependent.port.MchContentPacks.modelGeneration();
        if (!this.minYResolved || this.minYGeneration != generation) {
            this.minYGeneration = generation;
            this.modelMinY = 0.0F;
            String name = v.configName();
            if (name != null && !name.isEmpty()) {
                mcheli.agnostic.spi.ModelHandle h =
                    mcheli.dependent.port.MchContentPacks.resources().loadModel(v.modelDir() + "/" + name);
                if (h instanceof mcheli.agnostic.model.MchModel m) {
                    this.modelMinY = m.minY;
                }
            }
            this.minYResolved = true;
        }
        return this.modelMinY;
    }

    /** True when the config hides the seated rider ({@code HideEntity=true}: the Phalanx dome / closed tank turret). The
     *  reference makes the pilot fully invisible; the port's client render hook suppresses the rider accordingly. */
    boolean hidesRider() {
        MCH_AircraftInfo info = v.weaponHostInfo();
        return info != null && info.hideEntity;
    }

    /** Lower-cased {@code $weaponN} model groups the config hides in the FIRST-PERSON gunner view ({@code HideGM} weapon
     *  parts — e.g. the ec665 chin gun that would fill the cockpit). Cached; empty when no weapon is marked HideGM. The
     *  base renderer excludes these only for the LOCAL pilot in first person. */
    java.util.Set<String> firstPersonHiddenGroups() {
        if (this.hideGmGroups == null) {
            java.util.Set<String> s = new java.util.HashSet<>();
            MCH_AircraftInfo info = v.weaponHostInfo();
            if (info != null && info.partWeapon != null) {
                for (MCH_AircraftInfo.PartWeapon pw : info.partWeapon) {
                    // Reference hides a HideGM part ONLY for the weapon's OPERATOR (getWeaponUserByWeaponName): the lone
                    // pilot operates a weapon iff it is canUsePilot. A gunner-seat gun (canUsePilot=false) stays visible.
                    if (pw != null && pw.hideGM) {
                        MCH_AircraftInfo.Weapon wn = info.getWeaponByName(pw.name[0]);
                        if (wn != null && wn.canUsePilot) {
                            s.add(("$" + pw.modelName).toLowerCase(java.util.Locale.ROOT));
                        }
                    }
                }
            }
            this.hideGmGroups = s;
        }
        return this.hideGmGroups;
    }
}
