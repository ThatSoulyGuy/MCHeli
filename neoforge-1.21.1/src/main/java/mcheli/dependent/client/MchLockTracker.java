package mcheli.dependent.client;

import mcheli.agnostic.weapon.MCH_LockGeometry;
import mcheli.agnostic.weapon.MCH_WeaponInfo;
import mcheli.dependent.entity.AbstractMchVehicle;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * CLIENT-ONLY missile lock-on tracker — the port of {@code MCH_WeaponGuidanceSystem}'s per-weapon lock state machine,
 * scoped to the LOCAL player's single active lock-on weapon (the port has exactly one local operator, so one shared
 * state suffices instead of the reference's per-weapon-instance object).
 *
 * <p>Faithful to the reference: the lock resolves entirely on the client (reference {@code lock()} no-ops when
 * {@code !isRemote}), which is also the only choice consistent with the port's client-authoritative pilot rotation.
 * It ramps while the fire trigger is held and the operator keeps a valid target in the FOV cone (with a line-of-sight
 * gate and the per-type air/ground/water filters); once {@link #lockCount} reaches the config lock time the lock is
 * COMPLETE and {@link #completeTargetId()} exposes the target entity id for {@link MchClientInput} to ship to the
 * server, which then fires a guided round at it. Releasing fire (or switching weapon/seat/vehicle) clears the lock.
 *
 * <p>Pure lock GEOMETRY (the FOV cone + stealth scalers) lives in the agnostic {@link MCH_LockGeometry}; the entity
 * scan, LOS raytrace, and on-ground/in-water tests are here because they need {@code net.minecraft} types.
 */
public final class MchLockTracker {

    private MchLockTracker() {}

    // --- the single local-player lock state (reference MCH_WeaponGuidanceSystem fields) ---
    private static int targetId = -1;          // the entity currently being locked (-1 == none)
    private static int lockCount;
    private static int prevLockCount;
    private static int continueLockCount;
    private static int completeTargetId = -1;   // the target id once lock is COMPLETE (shipped to the server), else -1

    // what we are locking WITH, so a change resets the state (reference: a new weapon instance = a fresh system)
    private static int trackedVehicleId = -1;
    private static int trackedSeat = -1;
    private static int trackedWeaponIndex = -1;

    /** HUD lock-reticle progress 0..1 (reference {@code getLockCount()/getLockCountMax()}); 0 when not locking. */
    public static float progress() {
        MCH_WeaponInfo wi = lastWi;
        if (wi == null || lockCount <= 0) {
            return 0.0F;
        }
        int max = MCH_LockGeometry.effectiveCountMax(Math.max(1, wi.lockTime), 0.0F);
        return max > 0 ? Mth.clamp((float) lockCount / max, 0.0F, 1.0F) : 0.0F;
    }

    /** The locked target's entity id once the lock is COMPLETE, else -1 (what the fire packet ships). */
    public static int completeTargetId() { return completeTargetId; }

    /** True if a lock-on weapon type is one this tracker drives (AA/AT; AS/TV are deferred). */
    public static boolean isLockType(String type) {
        if (type == null) {
            return false;
        }
        String t = type.toLowerCase();
        return t.equals("aamissile") || t.equals("atmissile");
    }

    private static MCH_WeaponInfo lastWi; // remembered for progress() between ticks

    /** Reset all lock state (dismount / weapon change / fire released). */
    public static void clear() {
        targetId = -1;
        lockCount = 0;
        prevLockCount = 0;
        continueLockCount = 0;
        completeTargetId = -1;
    }

    /**
     * Drive the lock this client tick. Called from {@link MchClientInput} with the local operator's vehicle, seat,
     * selected weapon info, and whether the fire trigger is held. Runs the reference {@code lock()} acquire/hold
     * machine then the {@code update()} decay; clears when not actively locking.
     */
    public static void tick(AbstractMchVehicle vehicle, int seat, int weaponIndex, MCH_WeaponInfo wi, boolean fireHeld) {
        Minecraft mc = Minecraft.getInstance();
        Player user = mc.player;
        lastWi = wi;

        // Not actively locking → the lock evaporates (reference: lock() isn't called, update() zeroes it next tick).
        if (user == null || vehicle == null || wi == null || !fireHeld || !isLockType(wi.type)
            || user.getVehicle() != vehicle) {
            clear();
            trackedVehicleId = vehicle != null ? vehicle.getId() : -1;
            trackedSeat = seat;
            trackedWeaponIndex = weaponIndex;
            return;
        }

        // A change of vehicle / seat / selected weapon starts a fresh lock (reference: a new weapon instance).
        if (vehicle.getId() != trackedVehicleId || seat != trackedSeat || weaponIndex != trackedWeaponIndex) {
            clear();
            trackedVehicleId = vehicle.getId();
            trackedSeat = seat;
            trackedWeaponIndex = weaponIndex;
        }

        // Per-type lock envelope (reference weapon subclasses): AA locks airborne targets, AT locks ground targets.
        boolean isAA = wi.type.equalsIgnoreCase("aamissile");
        boolean canLockInAir = isAA;
        boolean canLockOnGround = !isAA;
        boolean canLockInWater = false;

        lock(user, vehicle, wi, canLockInAir, canLockOnGround, canLockInWater);
        update(); // decay (reference MCH_WeaponGuidanceSystem.update, isRemote branch)

        // Expose the completed lock for the fire packet. FIDELITY: the reference AIRCRAFT lock path is SILENT for the
        // shooter (the HUD reticle is the only feedback — `lockon` is played only by the hand-launcher/tool paths); the
        // `locked` tone is the RWR warning played on the TARGET's client via MCH_PacketNotifyLock, which is deferred to
        // the missile-detector / countermeasures effort. So no sound is played here.
        completeTargetId = isComplete(wi) ? targetId : -1;
    }

    private static boolean isComplete(MCH_WeaponInfo wi) {
        int max = MCH_LockGeometry.effectiveCountMax(Math.max(1, wi.lockTime), 0.0F);
        return lockCount >= max && targetId >= 0;
    }

    // ---- port of MCH_WeaponGuidanceSystem.lock() (isRemote branch) ----
    private static void lock(Player user, AbstractMchVehicle vehicle, MCH_WeaponInfo wi,
                             boolean canLockInAir, boolean canLockOnGround, boolean canLockInWater) {
        Level level = user.level();
        double lockRange = wi.lockRange;
        float lockAngle = wi.lockAngle;

        if (lockCount == 0) {
            // ACQUIRE: nearest valid entity inside range + cone + clear LOS.
            AABB box = user.getBoundingBox().inflate(lockRange, lockRange, lockRange);
            List<Entity> list = level.getEntitiesOfClass(Entity.class, box,
                e -> canLockEntity(e, user, vehicle, wi, canLockInAir, canLockOnGround, canLockInWater));
            // DELIBERATE DEVIATION: acquire the NEAREST valid target. The reference initialises the same accumulator
            // but never assigns `dist = d`, so it actually locks the LAST qualifying entity in world-query order
            // (arbitrary) — an accidental bug; nearest-target is the intended, more useful behaviour.
            Entity best = null;
            double bestSq = lockRange * lockRange * 2.0;
            for (Entity e : list) {
                double dx = e.getX() - user.getX();
                double dy = e.getY() - user.getY();
                double dz = e.getZ() - user.getZ();
                double d = dx * dx + dy * dy + dz * dz;
                float stealth = 0.0F; // aircraft stealth not yet ported (defer): 0 = full range/cone
                double range = MCH_LockGeometry.effectiveRange(lockRange, stealth);
                float angle = MCH_LockGeometry.effectiveAngle(lockAngle, stealth);
                if (d < range * range && d < bestSq
                    && MCH_LockGeometry.inLockCone(user.getX(), user.getY(), user.getZ(),
                        user.getYRot(), user.getXRot(), e.getX(), e.getY() + e.getBbHeight() / 2.0, e.getZ(), angle)
                    && hasLineOfSight(level, user, e)) {
                    best = e;
                    bestSq = d;
                }
            }
            targetId = best != null ? best.getId() : -1;
            if (best != null) {
                lockCount++;
            }
        } else {
            // HOLD: re-validate the current target's range + air/ground/water gates + cone.
            Entity target = targetId >= 0 ? level.getEntity(targetId) : null;
            if (target == null || !target.isAlive()) {
                clearLock();
                return;
            }
            boolean canLock = true;
            if (!canLockInWater && target.isInWater()) {
                canLock = false;
            }
            boolean ong = isEntityOnGround(level, target);
            if (!canLockOnGround && ong) {
                canLock = false;
            }
            if (!canLockInAir && !ong) {
                canLock = false;
            }
            if (!canLock) {
                clearLock();
                return;
            }
            double dx = target.getX() - user.getX();
            double dy = target.getY() - user.getY();
            double dz = target.getZ() - user.getZ();
            float stealth = 0.0F;
            double range = MCH_LockGeometry.effectiveRange(wi.lockRange, stealth);
            if (dx * dx + dy * dy + dz * dz >= range * range) {
                clearLock();
                return;
            }
            // NOTE: the reference advances a lockSoundCount here to pace the RWR "you are being locked" packet to the
            // target every 15 ticks — that warning (MCH_PacketNotifyLock) is deferred to the countermeasures effort.
            int max = MCH_LockGeometry.effectiveCountMax(Math.max(1, wi.lockTime), stealth);
            if (MCH_LockGeometry.inLockCone(user.getX(), user.getY(), user.getZ(), user.getYRot(), user.getXRot(),
                    target.getX(), target.getY() + target.getBbHeight() / 2.0, target.getZ(), lockAngle)) {
                if (lockCount < max) {
                    lockCount++;
                }
            } else if (continueLockCount > 0) {
                continueLockCount--;
                if (continueLockCount <= 0 && lockCount > 0) {
                    lockCount--;
                }
            } else {
                continueLockCount = 0;
                lockCount--;
            }
            if (lockCount >= max) {
                if (continueLockCount <= 0) {
                    continueLockCount = Math.min(max / 3, 20);
                }
                // reference keeps the lock alive against update()'s decay (prevLockCount = lockCount - 1)
                prevLockCount = lockCount - 1;
            }
        }
    }

    // ---- port of MCH_WeaponGuidanceSystem.update() decay (isRemote branch) ----
    private static void update() {
        if (lockCount != prevLockCount) {
            prevLockCount = lockCount;
        } else {
            lockCount = prevLockCount = 0;
        }
    }

    private static void clearLock() {
        targetId = -1;
        lockCount = 0;
        continueLockCount = 0;
    }

    // ---- classification (reference canLockEntity: entity KIND + air/ground/water, NOT the wi.target bitmask) ----
    private static boolean canLockEntity(Entity e, Player user, AbstractMchVehicle vehicle, MCH_WeaponInfo wi,
                                         boolean canLockInAir, boolean canLockOnGround, boolean canLockInWater) {
        if (e == user || e == vehicle || e.isSpectator() || !e.isAlive() || !e.isPickable()) {
            return false;
        }
        if (vehicle.getPassengers().contains(e) || e.getVehicle() == vehicle) {
            return false; // don't lock your own aircraft's passengers (reference checker !isMountedEntity)
        }
        // ridableOnly: a player ON FOOT cannot be locked (reference), only a mounted one.
        if (wi.ridableOnly && e instanceof Player && e.getVehicle() == null) {
            return false;
        }
        // Kind: a living entity (player/mob) OR an MCHeli vehicle (the reference's className/isEntityLivingBase test).
        if (!(e instanceof LivingEntity) && !(e instanceof AbstractMchVehicle)) {
            return false;
        }
        if (!canLockInWater && e.isInWater()) {
            return false;
        }
        boolean ong = isEntityOnGround(e.level(), e);
        if (!canLockOnGround && ong) {
            return false;
        }
        return canLockInAir || ong;
    }

    /** Reference {@code isEntityOnGround}: on the ground, or ANY non-air, non-WATER block within 12 blocks straight
     *  below (lava counts as ground). Faithful to the reference's round-to-nearest column sampling. */
    private static boolean isEntityOnGround(Level level, Entity entity) {
        if (entity.onGround()) {
            return true;
        }
        int x = (int) (entity.getX() + 0.5);
        int z = (int) (entity.getZ() + 0.5);
        int y0 = (int) (entity.getY() + 0.5);
        for (int i = 0; i < 12; i++) {
            var bs = level.getBlockState(new net.minecraft.core.BlockPos(x, y0 - i, z));
            if (!bs.isAir() && !bs.getFluidState().is(net.minecraft.tags.FluidTags.WATER)) {
                return true;
            }
        }
        return false;
    }

    /** Reference LOS gate: a clear block raytrace from the locker to the target centre (an entity in between is fine).
     *  Traced from the operator's EYE, not the feet — a GROUND emplacement (S-75 SAM) seats the operator at ground
     *  level, so a feet-origin ray starts inside the block the launcher rests on and always self-blocks. */
    private static boolean hasLineOfSight(Level level, Player user, Entity target) {
        Vec3 from = user.getEyePosition();
        Vec3 to = target.position().add(0.0, target.getBbHeight() / 2.0, 0.0);
        HitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user));
        return hit.getType() == HitResult.Type.MISS;
    }
}
