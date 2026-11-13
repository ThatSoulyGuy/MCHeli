package mcheli.dependent.entity;

import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Flares / countermeasures (#26) — the server-authoritative dispenser and missile decoy of one vehicle: the flare type
 * cycle, the dispense schedule and its volleys (reference {@code MCH_Flare}), the decoy, the RWR alert spacing (the
 * agnostic {@link mcheli.agnostic.aircraft.MCH_MissileDetector}) and the crew tones. The only writer of
 * {@link AbstractMchVehicle#DATA_FLARE_STATE}; the synced read {@code flareDispenserIdle} stays on the entity.
 */
final class MchCountermeasures {

    private final AbstractMchVehicle v;

    private final mcheli.agnostic.aircraft.MCH_FlareInfo flareInfo = new mcheli.agnostic.aircraft.MCH_FlareInfo();
    private int currentFlareIndex; // which of the config's flare.types[] the next press dispenses
    // reference MCH_MissileDetector.alertCount — 20-tick spacing between inbound-missile tones
    private final mcheli.agnostic.aircraft.MCH_MissileDetector missileDetector =
        new mcheli.agnostic.aircraft.MCH_MissileDetector();

    MchCountermeasures(AbstractMchVehicle v) {
        this.v = v;
    }

    /** Drop the config-derived dispenser state (from the entity's config-cache invalidation). The RWR alert cooldown
     *  is not reset. */
    void onConfigChanged() {
        this.flareInfo.reset();       // a different config may declare different flare types
        this.currentFlareIndex = 0;
    }

    /** This vehicle's config declares flares (reference {@code haveFlare}: FlareType set). */
    boolean haveFlare() {
        MCH_AircraftInfo info = v.weaponHostInfo();
        return info != null && info.haveFlare();
    }

    /** The decoy-active burn window is open (reference {@code isFlareUsing}). */
    boolean isFlareUsing() { return this.flareInfo.isUsing(); }

    /** Ready to dispense (has flares + the dispenser is idle). */
    boolean canDeployFlare() { return v.haveFlare() && this.flareInfo.canUseFlare(); }

    /** The flare type the next dispense will use (cycles the config's {@code FlareType} list). */
    int currentFlareType() {
        MCH_AircraftInfo info = v.weaponHostInfo();
        if (info == null || info.flare.types.length == 0) {
            return 1;
        }
        return info.flare.types[Math.floorMod(this.currentFlareIndex, info.flare.types.length)];
    }

    /** Advance to the next configured flare type (reference {@code nextFlareType}). */
    void nextFlareType() { this.currentFlareIndex++; }

    /** Begin a dispense of {@code type} (server-side; reference {@code useFlare}). */
    void deployFlare(int type) { this.flareInfo.use(type); }

    /** Server tick: step the dispense schedule, eject each due volley, then run the missile decoy while flaring. */
    void tickServer() {
        if (v.level().isClientSide || !v.haveFlare()) {
            return;
        }
        this.flareInfo.tickDown();
        // ONE volley per qualifying tick (reference MCH_Flare.update uses an `if`, not a loop): tick is only stepped
        // by tickDown() above, so a `while` here would dump the WHOLE flare complement in a single tick instead of
        // streaming it across the window.
        if (this.flareInfo.shouldSpawnVolley()) {
            spawnFlareVolley();
            this.flareInfo.onVolleySpawned();
        }
        v.getEntityData().set(AbstractMchVehicle.DATA_FLARE_STATE, (byte) (this.flareInfo.isPreparing() ? 1 : 0)); // sync the HUD cue
        // Decoy (reference MCH_MissileDetector.destroyMissile): while the burn window is active, null the target of
        // every guided round homing on us within SEARCH_RANGE and discard it (tickGuidance would self-discard on the
        // nulled target next tick; the explicit discard matches the reference for immediacy).
        // The detector counts its alert cooldown down first, then picks the decoy or the alert.
        // Reference MCH_MissileDetector gates BOTH the decoy and the RWR alert on a rider being present.
        boolean crew = v.seatPassenger(0) != null || v.seatPassenger(1) != null;
        mcheli.agnostic.aircraft.MCH_MissileDetector.Action action =
            this.missileDetector.tick(this.flareInfo.isUsing(), v.isDestroyed(), crew, this::isLockedByMissile);
        if (action == mcheli.agnostic.aircraft.MCH_MissileDetector.Action.DECOY) {
            double r = mcheli.agnostic.aircraft.MCH_FlareInfo.SEARCH_RANGE;
            AABB box = v.getBoundingBox().inflate(r, r, r);
            for (MchBullet b : v.level().getEntitiesOfClass(MchBullet.class, box)) {
                if (b.isGuided() && b.isHomingOn(v)) {
                    b.decoyClear();
                    b.discard();
                }
            }
        } else if (action == mcheli.agnostic.aircraft.MCH_MissileDetector.Action.ALERT) {
            // RWR inbound-missile 'alert' (reference MCH_MissileDetector: not flaring + a live missile homing on us).
            // The detector has already set its cooldown to 20.
            v.sendRwrToCrew(mcheli.dependent.control.ClientboundRwrPayload.ALERT);
        }
    }

    /** True if a guided round is currently homing on this vehicle or a passenger within the flare search radius
     *  (reference {@code MCH_MissileDetector.isLockedByMissile}). Server-side. */
    private boolean isLockedByMissile() {
        double r = mcheli.agnostic.aircraft.MCH_FlareInfo.SEARCH_RANGE;
        AABB box = v.getBoundingBox().inflate(r, r, r);
        for (MchBullet b : v.level().getEntitiesOfClass(MchBullet.class, box)) {
            if (b.isGuided() && b.isHomingOn(v)) {
                return true;
            }
        }
        return false;
    }

    /** Send an RWR tone to the seat-0/1 crew (reference {@code MCH_MissileDetector} warns {@code getEntityBySeatId(0..1)}).
     *  Server-side; used by both the inbound-missile alert and the being-locked relay. */
    void sendRwrToCrew(byte kind) {
        if (v.level().isClientSide) {
            return;
        }
        for (int s = 0; s <= 1; s++) {
            if (v.seatPassenger(s) instanceof net.minecraft.server.level.ServerPlayer sp) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp,
                    new mcheli.dependent.control.ClientboundRwrPayload(kind));
            }
        }
    }

    /** Eject one volley of {@code num} flares from the config {@code FlareOption} point, behind + below the hull, with
     *  the per-type spread velocity (reference {@code MCH_Flare.spawnFlare}). */
    private void spawnFlareVolley() {
        MCH_AircraftInfo info = v.weaponHostInfo();
        if (info == null) {
            return;
        }
        int type = this.flareInfo.flareType();
        int num = this.flareInfo.volleyNum();
        int fuse = this.flareInfo.fuseCount();
        boolean floaty = mcheli.agnostic.aircraft.MCH_FlareInfo.isFloatyType(type);

        // Emit point = FlareOption offset (model space) rotated by the hull, at the vehicle position.
        float hullYaw = Mth.rotLerp(1.0F, v.yRotO, v.getYRot());
        float roll = v.getRollAngle();
        org.joml.Quaternionf qHull = new org.joml.Quaternionf()
            .rotateY((float) Math.toRadians(-hullYaw))
            .rotateX((float) Math.toRadians(v.getXRot()))
            .rotateZ((float) Math.toRadians(roll));
        org.joml.Vector3f fpos = new org.joml.Vector3f(
            (float) info.flare.pos.x(), (float) info.flare.pos.y(), (float) info.flare.pos.z());
        qHull.transform(fpos);
        Vec3 mot = v.getDeltaMovement();
        double baseX = v.getX() + fpos.x - mot.x * 2.0;
        double baseY = v.getY() + fpos.y - mot.y * 2.0 - 1.0 + (type == 10 ? 2.0 : 0.0);
        double baseZ = v.getZ() + fpos.z - mot.z * 2.0;

        for (int i = 0; i < num; i++) {
            // The local `v` (the motion) shadows the vehicle field inside this loop, hence `this.v`.
            double[] v = mcheli.agnostic.aircraft.MCH_FlareInfo.volleyMotion(type, i, num, mot.x, mot.y, mot.z,
                hullYaw, this.v.getRandom().nextFloat(), this.v.getRandom().nextFloat(), this.v.getRandom().nextFloat());
            MchFlare.spawn(this.v.level(), new Vec3(baseX, baseY, baseZ), new Vec3(v[0], v[1], v[2]), fuse, floaty);
        }
        // Dispense report (reference plays random.click on use + an aux pop per volley).
        v.level().playSound(null, baseX, baseY, baseZ, net.minecraft.sounds.SoundEvents.FIRECHARGE_USE,
            SoundSource.NEUTRAL, 0.6F, 1.4F);
    }
}
