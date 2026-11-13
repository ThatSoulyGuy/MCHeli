package mcheli.dependent.entity;

import mcheli.agnostic.weapon.MCH_WeaponInfo;
import mcheli.agnostic.weapon.WeaponSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The server lock audit of one vehicle (see {@link mcheli.agnostic.weapon.MCH_LockAudit}): what the server watched of
 * each seat's client-built lock. It records the lock notices ({@code ServerboundNotifyLockPayload}), samples every
 * locking seat's candidate each tick against its operator's look, range and sight, and backs (or refuses) a guided
 * weapon's completed lock at the moment of fire. Owned by {@link MchFireControl}, which drops a seat's audit on a
 * weapon switch; the entity keeps {@code noteLockNotice} as a public one-line delegate.
 */
final class MchLockAudits {
    private final AbstractMchVehicle v;

    MchLockAudits(AbstractMchVehicle v) {
        this.v = v;
    }

    private final java.util.Map<Integer, mcheli.agnostic.weapon.MCH_LockAudit> lockAudits = new java.util.HashMap<>();

    /**
     * SERVER: a lock notice from {@code sender} in {@code seat} naming {@code target} ({@code ServerboundNotifyLockPayload}).
     * Records the candidate for the lock audit, and returns whether the RWR warning may be relayed: the target must be
     * within the seat weapon's lock range for its stealth (the reference sends the notice only inside it, :182-185) and
     * the seat's relay interval must have passed.
     */
    boolean noteLockNotice(int seat, Entity sender, Entity target) {
        if (v.level().isClientSide || v.weaponLoadout() == null) {
            return false;
        }
        WeaponSlot s = v.weaponLoadout().selected(seat);
        if (s == null || !AbstractMchVehicle.isLockOnType(s.info.type)) {
            return false;
        }
        long now = v.level().getGameTime();
        mcheli.agnostic.weapon.MCH_LockAudit audit =
            this.lockAudits.computeIfAbsent(seat, k -> new mcheli.agnostic.weapon.MCH_LockAudit());
        audit.observeCandidate(target.getId(), now);
        double range = mcheli.agnostic.weapon.MCH_LockAudit.serverLockRange(s.info.lockRange,
            AbstractMchVehicle.lockStealth(target));
        return sender.distanceToSqr(target) <= range * range && audit.allowRelay(now);
    }

    /** SERVER: forget {@code seat}'s audit — a new weapon builds a new lock (the client tracker restarts too). */
    void drop(int seat) {
        this.lockAudits.remove(seat);
    }

    /** SERVER, each tick: sample every locking seat's candidate against its operator's look, range and sight, scaled
     *  for the candidate's stealth as the client tracker's are. A seat that no longer holds a lock-on weapon (or a
     *  player) drops its audit. */
    void tickLockAudits() {
        if (this.lockAudits.isEmpty()) {
            return;
        }
        this.lockAudits.keySet().removeIf(seat -> !(v.seatPassenger(seat) instanceof Player)
            || !v.selectedWeaponIsLockOn(seat));
        long now = v.level().getGameTime();
        for (java.util.Map.Entry<Integer, mcheli.agnostic.weapon.MCH_LockAudit> e : this.lockAudits.entrySet()) {
            Entity op = v.seatPassenger(e.getKey());
            WeaponSlot s = v.weaponLoadout().selected(e.getKey());
            mcheli.agnostic.weapon.MCH_LockAudit audit = e.getValue();
            Entity t = audit.candidate() >= 0 ? v.level().getEntity(audit.candidate()) : null;
            if (op == null || s == null || t == null || !t.isAlive()) {
                continue;
            }
            double dx = t.getX() - op.getX();
            double dy = t.getY() - op.getY();
            double dz = t.getZ() - op.getZ();
            double dSq = dx * dx + dy * dy + dz * dz;
            float stealth = AbstractMchVehicle.lockStealth(t);
            double range = mcheli.agnostic.weapon.MCH_LockAudit.serverLockRange(s.info.lockRange, stealth);
            double speed = t.getDeltaMovement().length();
            float cone = mcheli.agnostic.weapon.MCH_LockAudit.serverConeAngle(s.info.lockAngle, speed, Math.sqrt(dSq));
            boolean inCone = dSq < range * range && inSeekerCone(op, t, cone);
            // The sight counts from inside the acquisition cone, which stealth narrows (reference lock() :144).
            boolean acquired = inCone && audit.needsSightCheck() && inSeekerCone(op, t,
                mcheli.agnostic.weapon.MCH_LockAudit.serverAcquireConeAngle(s.info.lockAngle, stealth, speed,
                    Math.sqrt(dSq)));
            audit.sample(now, inCone, acquired && hasClearSight(op, t));
        }
    }

    /** Is {@code t}'s centre inside {@code op}'s look cone of half-width {@code cone} degrees? */
    private static boolean inSeekerCone(Entity op, Entity t, float cone) {
        return mcheli.agnostic.weapon.MCH_LockGeometry.inLockCone(op.getX(), op.getY(), op.getZ(), op.getYRot(),
            op.getXRot(), t.getX(), t.getY() + t.getBbHeight() / 2.0, t.getZ(), cone);
    }

    /** Reference lock LOS gate: a clear block ray from the operator's eye to the target's centre. */
    private boolean hasClearSight(Entity op, Entity t) {
        Vec3 to = t.position().add(0.0, t.getBbHeight() / 2.0, 0.0);
        return v.level().clip(new net.minecraft.world.level.ClipContext(op.getEyePosition(), to,
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, op))
            .getType() == net.minecraft.world.phys.HitResult.Type.MISS;
    }

    /**
     * SERVER: the entity {@code seat}'s completed lock names ({@code controlState(seat).lockTargetId}), if the server's
     * audit backs it: a live, lockable entity (a living one or a vehicle, reference {@code canLockEntity}) that is not
     * this vehicle or aboard it, that the seat's lock notices named, for long enough (the lock time lengthened for the
     * target's stealth, reference {@code getLockCountMax}), inside the seeker cone with a clear sight at least once.
     * Otherwise null and the guided weapon holds fire.
     */
    Entity auditedLockTarget(int seat, MCH_WeaponInfo wi) {
        int tid = v.controlState(seat).lockTargetId;
        Entity t = tid >= 0 ? v.level().getEntity(tid) : null;
        if (t == null || !t.isAlive() || t == v || v.getPassengers().contains(t)
            || !(t instanceof LivingEntity || t instanceof AbstractMchVehicle)) {
            return null;
        }
        mcheli.agnostic.weapon.MCH_LockAudit audit = this.lockAudits.get(seat);
        int max = mcheli.agnostic.weapon.MCH_LockGeometry.effectiveCountMax(Math.max(1, wi.lockTime),
            AbstractMchVehicle.lockStealth(t));
        return audit != null && audit.plausible(tid, v.level().getGameTime(), max) ? t : null;
    }
}
