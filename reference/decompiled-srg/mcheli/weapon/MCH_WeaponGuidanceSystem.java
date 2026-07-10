/*
 * Decompiled with CFR 0.152.
 */
package mcheli.weapon;

import java.util.List;
import mcheli.MCH_Lib;
import mcheli.MCH_PacketNotifyLock;
import mcheli.aircraft.MCH_EntityAircraft;
import mcheli.uav.MCH_EntityUavStation;
import mcheli.weapon.MCH_IEntityLockChecker;
import mcheli.wrapper.W_Entity;
import mcheli.wrapper.W_Lib;
import mcheli.wrapper.W_MovingObjectPosition;
import mcheli.wrapper.W_WorldFunc;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/*
 * Exception performing whole class analysis ignored.
 */
public class MCH_WeaponGuidanceSystem {
    protected World worldObj;
    public Entity lastLockEntity;
    private Entity targetEntity;
    private int lockCount;
    private int lockSoundCount;
    private int continueLockCount;
    private int lockCountMax;
    private int prevLockCount;
    public boolean canLockInWater;
    public boolean canLockOnGround;
    public boolean canLockInAir;
    public boolean ridableOnly;
    public double lockRange;
    public int lockAngle;
    public MCH_IEntityLockChecker checker;

    public MCH_WeaponGuidanceSystem() {
        this(null);
    }

    public MCH_WeaponGuidanceSystem(World w) {
        this.worldObj = w;
        this.targetEntity = null;
        this.lastLockEntity = null;
        this.lockCount = 0;
        this.continueLockCount = 0;
        this.lockCountMax = 1;
        this.prevLockCount = 0;
        this.canLockInWater = false;
        this.canLockOnGround = false;
        this.canLockInAir = false;
        this.ridableOnly = false;
        this.lockRange = 50.0;
        this.lockAngle = 10;
        this.checker = null;
    }

    public void setWorld(World w) {
        this.worldObj = w;
    }

    public void setLockCountMax(int i) {
        this.lockCountMax = i > 0 ? i : 1;
    }

    public int getLockCountMax() {
        float stealth = MCH_WeaponGuidanceSystem.getEntityStealth((Entity)this.targetEntity);
        return (int)((float)this.lockCountMax + (float)this.lockCountMax * stealth);
    }

    public int getLockCount() {
        return this.lockCount;
    }

    public boolean isLockingEntity(Entity entity) {
        return this.getLockCount() > 0 && this.targetEntity != null && !this.targetEntity.field_70128_L && W_Entity.isEqual((Entity)entity, (Entity)this.targetEntity);
    }

    public Entity getLockingEntity() {
        return this.getLockCount() > 0 && this.targetEntity != null && !this.targetEntity.field_70128_L ? this.targetEntity : null;
    }

    public Entity getTargetEntity() {
        return this.targetEntity;
    }

    public boolean isLockComplete() {
        return this.getLockCount() == this.getLockCountMax() && this.lastLockEntity != null;
    }

    public void update() {
        if (this.worldObj != null && this.worldObj.field_72995_K) {
            if (this.lockCount != this.prevLockCount) {
                this.prevLockCount = this.lockCount;
            } else {
                this.prevLockCount = 0;
                this.lockCount = 0;
            }
        }
    }

    public static boolean isEntityOnGround(Entity entity) {
        if (entity != null && !entity.field_70128_L) {
            if (entity.field_70122_E) {
                return true;
            }
            for (int i = 0; i < 12; ++i) {
                int x = (int)(entity.field_70165_t + 0.5);
                int y = (int)(entity.field_70163_u + 0.5) - i;
                int z = (int)(entity.field_70161_v + 0.5);
                int blockId = W_WorldFunc.getBlockId((World)entity.field_70170_p, (int)x, (int)y, (int)z);
                if (blockId == 0 || W_WorldFunc.isBlockWater((World)entity.field_70170_p, (int)x, (int)y, (int)z)) continue;
                return true;
            }
        }
        return false;
    }

    public boolean lock(Entity user) {
        return this.lock(user, true);
    }

    public boolean lock(Entity user, boolean isLockContinue) {
        if (!this.worldObj.field_72995_K) {
            return false;
        }
        boolean result = false;
        if (this.lockCount == 0) {
            List list = this.worldObj.func_72839_b(user, user.field_70121_D.func_72314_b(this.lockRange, this.lockRange, this.lockRange));
            Entity tgtEnt = null;
            double dist = this.lockRange * this.lockRange * 2.0;
            for (int i = 0; i < list.size(); ++i) {
                Vec3 v2;
                Vec3 v1;
                MovingObjectPosition m;
                Entity entity = (Entity)list.get(i);
                if (!this.canLockEntity(entity)) continue;
                double dx = entity.field_70165_t - user.field_70165_t;
                double dy = entity.field_70163_u - user.field_70163_u;
                double dz = entity.field_70161_v - user.field_70161_v;
                double d = dx * dx + dy * dy + dz * dz;
                Entity entityLocker = this.getLockEntity(user);
                float stealth = 1.0f - MCH_WeaponGuidanceSystem.getEntityStealth((Entity)entity);
                double range = this.lockRange * (double)stealth;
                float angle = (float)this.lockAngle * (stealth / 2.0f + 0.5f);
                if (!(d < range * range) || !(d < dist) || !MCH_WeaponGuidanceSystem.inLockRange((Entity)entityLocker, (float)user.field_70177_z, (float)user.field_70125_A, (Entity)entity, (float)angle) || (m = W_WorldFunc.clip((World)this.worldObj, (Vec3)(v1 = W_WorldFunc.getWorldVec3((World)this.worldObj, (double)entityLocker.field_70165_t, (double)entityLocker.field_70163_u, (double)entityLocker.field_70161_v)), (Vec3)(v2 = W_WorldFunc.getWorldVec3((World)this.worldObj, (double)entity.field_70165_t, (double)(entity.field_70163_u + (double)(entity.field_70131_O / 2.0f)), (double)entity.field_70161_v)), (boolean)false, (boolean)true, (boolean)false)) != null && !W_MovingObjectPosition.isHitTypeEntity((MovingObjectPosition)m)) continue;
                d = dist;
                tgtEnt = entity;
            }
            this.targetEntity = tgtEnt;
            if (tgtEnt != null) {
                ++this.lockCount;
            }
        } else if (this.targetEntity != null && !this.targetEntity.field_70128_L) {
            boolean canLock = true;
            if (!this.canLockInWater && this.targetEntity.func_70090_H()) {
                canLock = false;
            }
            boolean ong = MCH_WeaponGuidanceSystem.isEntityOnGround((Entity)this.targetEntity);
            if (!this.canLockOnGround && ong) {
                canLock = false;
            }
            if (!this.canLockInAir && !ong) {
                canLock = false;
            }
            if (canLock) {
                double dx = this.targetEntity.field_70165_t - user.field_70165_t;
                double dy = this.targetEntity.field_70163_u - user.field_70163_u;
                double dz = this.targetEntity.field_70161_v - user.field_70161_v;
                float stealth = 1.0f - MCH_WeaponGuidanceSystem.getEntityStealth((Entity)this.targetEntity);
                double range = this.lockRange * (double)stealth;
                if (dx * dx + dy * dy + dz * dz < range * range) {
                    if (this.worldObj.field_72995_K && this.lockSoundCount == 1) {
                        MCH_PacketNotifyLock.send((Entity)this.getTargetEntity());
                    }
                    this.lockSoundCount = (this.lockSoundCount + 1) % 15;
                    Entity entityLocker = this.getLockEntity(user);
                    if (MCH_WeaponGuidanceSystem.inLockRange((Entity)entityLocker, (float)user.field_70177_z, (float)user.field_70125_A, (Entity)this.targetEntity, (float)this.lockAngle)) {
                        if (this.lockCount < this.getLockCountMax()) {
                            ++this.lockCount;
                        }
                    } else if (this.continueLockCount > 0) {
                        --this.continueLockCount;
                        if (this.continueLockCount <= 0 && this.lockCount > 0) {
                            --this.lockCount;
                        }
                    } else {
                        this.continueLockCount = 0;
                        --this.lockCount;
                    }
                    if (this.lockCount >= this.getLockCountMax()) {
                        if (this.continueLockCount <= 0) {
                            this.continueLockCount = this.getLockCountMax() / 3;
                            if (this.continueLockCount > 20) {
                                this.continueLockCount = 20;
                            }
                        }
                        result = true;
                        this.lastLockEntity = this.targetEntity;
                        if (isLockContinue) {
                            this.prevLockCount = this.lockCount - 1;
                        } else {
                            this.clearLock();
                        }
                    }
                } else {
                    this.clearLock();
                }
            } else {
                this.clearLock();
            }
        } else {
            this.clearLock();
        }
        return result;
    }

    public static float getEntityStealth(Entity entity) {
        if (entity instanceof MCH_EntityAircraft) {
            return ((MCH_EntityAircraft)entity).getStealth();
        }
        if (entity != null && entity.field_70154_o instanceof MCH_EntityAircraft) {
            return ((MCH_EntityAircraft)entity.field_70154_o).getStealth();
        }
        return 0.0f;
    }

    public void clearLock() {
        this.targetEntity = null;
        this.lockCount = 0;
        this.continueLockCount = 0;
        this.lockSoundCount = 0;
    }

    public Entity getLockEntity(Entity entity) {
        MCH_EntityUavStation us;
        if (entity.field_70154_o instanceof MCH_EntityUavStation && (us = (MCH_EntityUavStation)entity.field_70154_o).getControlAircract() != null) {
            return us.getControlAircract();
        }
        return entity;
    }

    public boolean canLockEntity(Entity entity) {
        if (this.ridableOnly && entity instanceof EntityPlayer && entity.field_70154_o == null) {
            return false;
        }
        String className = entity.getClass().getName();
        if (className.indexOf("EntityCamera") >= 0) {
            return false;
        }
        if (!W_Lib.isEntityLivingBase((Entity)entity) && !(entity instanceof MCH_EntityAircraft) && className.indexOf("EntityVehicle") < 0 && className.indexOf("EntityPlane") < 0 && className.indexOf("EntityMecha") < 0 && className.indexOf("EntityAAGun") < 0) {
            return false;
        }
        if (!this.canLockInWater && entity.func_70090_H()) {
            return false;
        }
        if (this.checker != null && !this.checker.canLockEntity(entity)) {
            return false;
        }
        boolean ong = MCH_WeaponGuidanceSystem.isEntityOnGround((Entity)entity);
        if (!this.canLockOnGround && ong) {
            return false;
        }
        return this.canLockInAir || ong;
    }

    public static boolean inLockRange(Entity entity, float rotationYaw, float rotationPitch, Entity target, float lockAng) {
        double dx = target.field_70165_t - entity.field_70165_t;
        double dy = target.field_70163_u + (double)(target.field_70131_O / 2.0f) - entity.field_70163_u;
        double dz = target.field_70161_v - entity.field_70161_v;
        float entityYaw = (float)MCH_Lib.getRotate360((double)rotationYaw);
        float targetYaw = (float)MCH_Lib.getRotate360((double)(Math.atan2(dz, dx) * 180.0 / Math.PI));
        float diffYaw = (float)MCH_Lib.getRotate360((double)(targetYaw - entityYaw - 90.0f));
        double dxz = Math.sqrt(dx * dx + dz * dz);
        float targetPitch = -((float)(Math.atan2(dy, dxz) * 180.0 / Math.PI));
        float diffPitch = targetPitch - rotationPitch;
        return (diffYaw < lockAng || diffYaw > 360.0f - lockAng) && Math.abs(diffPitch) < lockAng;
    }
}

