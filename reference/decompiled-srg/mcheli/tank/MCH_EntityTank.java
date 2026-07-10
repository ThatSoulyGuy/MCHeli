/*
 * Decompiled with CFR 0.152.
 */
package mcheli.tank;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.List;
import mcheli.MCH_Config;
import mcheli.MCH_Lib;
import mcheli.MCH_MOD;
import mcheli.MCH_Math;
import mcheli.aircraft.MCH_AircraftInfo;
import mcheli.aircraft.MCH_BoundingBox;
import mcheli.aircraft.MCH_EntityAircraft;
import mcheli.aircraft.MCH_EntityHitBox;
import mcheli.aircraft.MCH_EntitySeat;
import mcheli.aircraft.MCH_PacketStatusRequest;
import mcheli.aircraft.MCH_Parts;
import mcheli.particles.MCH_ParticleParam;
import mcheli.particles.MCH_ParticlesUtil;
import mcheli.tank.MCH_TankInfo;
import mcheli.tank.MCH_TankInfoManager;
import mcheli.tank.MCH_WheelManager;
import mcheli.weapon.MCH_WeaponSet;
import mcheli.wrapper.W_Block;
import mcheli.wrapper.W_Blocks;
import mcheli.wrapper.W_Entity;
import mcheli.wrapper.W_Lib;
import mcheli.wrapper.W_WorldFunc;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.command.IEntitySelector;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/*
 * Exception performing whole class analysis ignored.
 */
public class MCH_EntityTank
extends MCH_EntityAircraft {
    private MCH_TankInfo tankInfo = null;
    public float soundVolume;
    public float soundVolumeTarget;
    public float rotationRotor;
    public float prevRotationRotor;
    public float addkeyRotValue;
    public final MCH_WheelManager WheelMng;

    public MCH_EntityTank(World world) {
        super(world);
        this.currentSpeed = 0.07;
        this.field_70156_m = true;
        this.func_70105_a(2.0f, 0.7f);
        this.field_70129_M = this.field_70131_O / 2.0f;
        this.field_70159_w = 0.0;
        this.field_70181_x = 0.0;
        this.field_70179_y = 0.0;
        this.weapons = this.createWeapon(0);
        this.soundVolume = 0.0f;
        this.field_70138_W = 0.6f;
        this.rotationRotor = 0.0f;
        this.prevRotationRotor = 0.0f;
        this.WheelMng = new MCH_WheelManager((MCH_EntityAircraft)this);
    }

    public String getKindName() {
        return "tanks";
    }

    public String getEntityType() {
        return "Vehicle";
    }

    public MCH_TankInfo getTankInfo() {
        return this.tankInfo;
    }

    public void changeType(String type) {
        if (!type.isEmpty()) {
            this.tankInfo = MCH_TankInfoManager.get((String)type);
        }
        if (this.tankInfo == null) {
            MCH_Lib.Log((Entity)this, (String)"##### MCH_EntityTank changeTankType() Tank info null %d, %s, %s", (Object[])new Object[]{W_Entity.getEntityId((Entity)this), type, this.getEntityName()});
            this.func_70106_y();
        } else {
            this.setAcInfo((MCH_AircraftInfo)this.tankInfo);
            this.newSeats(this.getAcInfo().getNumSeatAndRack());
            this.switchFreeLookModeClient(this.getAcInfo().defaultFreelook);
            this.weapons = this.createWeapon(1 + this.getSeatNum());
            this.initPartRotation(this.getRotYaw(), this.getRotPitch());
            this.WheelMng.createWheels(this.field_70170_p, this.getAcInfo().wheels, Vec3.func_72443_a((double)0.0, (double)-0.35, (double)this.getTankInfo().weightedCenterZ));
        }
    }

    public Item getItem() {
        return this.getTankInfo() != null ? this.getTankInfo().item : null;
    }

    public boolean canMountWithNearEmptyMinecart() {
        return MCH_Config.MountMinecartTank.prmBool;
    }

    protected void func_70088_a() {
        super.func_70088_a();
    }

    public float getGiveDamageRot() {
        return 91.0f;
    }

    protected void func_70014_b(NBTTagCompound par1NBTTagCompound) {
        super.func_70014_b(par1NBTTagCompound);
    }

    protected void func_70037_a(NBTTagCompound par1NBTTagCompound) {
        super.func_70037_a(par1NBTTagCompound);
        if (this.tankInfo == null) {
            this.tankInfo = MCH_TankInfoManager.get((String)this.getTypeName());
            if (this.tankInfo == null) {
                MCH_Lib.Log((Entity)this, (String)"##### MCH_EntityTank readEntityFromNBT() Tank info null %d, %s", (Object[])new Object[]{W_Entity.getEntityId((Entity)this), this.getEntityName()});
                this.func_70106_y();
            } else {
                this.setAcInfo((MCH_AircraftInfo)this.tankInfo);
            }
        }
    }

    public void func_70106_y() {
        super.func_70106_y();
    }

    public void onInteractFirst(EntityPlayer player) {
        this.addkeyRotValue = 0.0f;
        player.field_70759_as = player.field_70758_at = this.getLastRiderYaw();
        player.field_70126_B = player.field_70177_z = this.getLastRiderYaw();
        player.field_70125_A = this.getLastRiderPitch();
    }

    public boolean canSwitchGunnerMode() {
        if (!super.canSwitchGunnerMode()) {
            return false;
        }
        return false;
    }

    public void onUpdateAircraft() {
        if (this.tankInfo == null) {
            this.changeType(this.getTypeName());
            this.field_70169_q = this.field_70165_t;
            this.field_70167_r = this.field_70163_u;
            this.field_70166_s = this.field_70161_v;
            return;
        }
        if (!this.isRequestedSyncStatus) {
            this.isRequestedSyncStatus = true;
            if (this.field_70170_p.field_72995_K) {
                MCH_PacketStatusRequest.requestStatus((MCH_EntityAircraft)this);
            }
        }
        if (this.lastRiddenByEntity == null && this.getRiddenByEntity() != null) {
            this.initCurrentWeapon(this.getRiddenByEntity());
        }
        this.updateWeapons();
        this.onUpdate_Seats();
        this.onUpdate_Control();
        this.prevRotationRotor = this.rotationRotor;
        this.rotationRotor = (float)((double)this.rotationRotor + this.getCurrentThrottle() * (double)this.getAcInfo().rotorSpeed);
        if (this.rotationRotor > 360.0f) {
            this.rotationRotor -= 360.0f;
            this.prevRotationRotor -= 360.0f;
        }
        if (this.rotationRotor < 0.0f) {
            this.rotationRotor += 360.0f;
            this.prevRotationRotor += 360.0f;
        }
        this.field_70169_q = this.field_70165_t;
        this.field_70167_r = this.field_70163_u;
        this.field_70166_s = this.field_70161_v;
        if (this.isDestroyed() && this.getCurrentThrottle() > 0.0) {
            if (MCH_Lib.getBlockIdY((Entity)this, (int)3, (int)-2) > 0) {
                this.setCurrentThrottle(this.getCurrentThrottle() * 0.8);
            }
            if (this.isExploded()) {
                this.setCurrentThrottle(this.getCurrentThrottle() * 0.98);
            }
        }
        this.updateCameraViewers();
        if (this.field_70170_p.field_72995_K) {
            this.onUpdate_Client();
        } else {
            this.onUpdate_Server();
        }
    }

    @SideOnly(value=Side.CLIENT)
    public boolean func_90999_ad() {
        return this.isDestroyed() || super.func_90999_ad();
    }

    public void updateExtraBoundingBox() {
        if (this.field_70170_p.field_72995_K) {
            super.updateExtraBoundingBox();
        } else if (this.getCountOnUpdate() <= 1) {
            super.updateExtraBoundingBox();
            super.updateExtraBoundingBox();
        }
    }

    public double calculateXOffset(List list, AxisAlignedBB bb, double parX) {
        for (int i = 0; i < list.size(); ++i) {
            parX = ((AxisAlignedBB)list.get(i)).func_72316_a(bb, parX);
        }
        bb.func_72317_d(parX, 0.0, 0.0);
        return parX;
    }

    public double calculateYOffset(List list, AxisAlignedBB bb, double parY) {
        for (int i = 0; i < list.size(); ++i) {
            parY = ((AxisAlignedBB)list.get(i)).func_72323_b(bb, parY);
        }
        bb.func_72317_d(0.0, parY, 0.0);
        return parY;
    }

    public double calculateZOffset(List list, AxisAlignedBB bb, double parZ) {
        for (int i = 0; i < list.size(); ++i) {
            parZ = ((AxisAlignedBB)list.get(i)).func_72322_c(bb, parZ);
        }
        bb.func_72317_d(0.0, 0.0, parZ);
        return parZ;
    }

    public void func_70091_d(double parX, double parY, double parZ) {
        this.field_70170_p.field_72984_F.func_76320_a("move");
        this.field_70139_V *= 0.4f;
        double nowPosX = this.field_70165_t;
        double nowPosY = this.field_70163_u;
        double nowPosZ = this.field_70161_v;
        double mx = parX;
        double my = parY;
        double mz = parZ;
        AxisAlignedBB backUpAxisalignedBB = this.field_70121_D.func_72329_c();
        List list = MCH_EntityTank.getCollidingBoundingBoxes((Entity)this, (AxisAlignedBB)this.field_70121_D.func_72321_a(parX, parY, parZ));
        parY = this.calculateYOffset(list, this.field_70121_D, parY);
        boolean flag1 = this.field_70122_E || my != parY && my < 0.0;
        for (MCH_BoundingBox ebb : this.extraBoundingBox) {
            ebb.updatePosition(this.field_70165_t, this.field_70163_u, this.field_70161_v, this.getRotYaw(), this.getRotPitch(), this.getRotRoll());
        }
        parX = this.calculateXOffset(list, this.field_70121_D, parX);
        parZ = this.calculateZOffset(list, this.field_70121_D, parZ);
        if (this.field_70138_W > 0.0f && flag1 && this.field_70139_V < 0.05f && (mx != parX || mz != parZ)) {
            double bkParX = parX;
            double bkParY = parY;
            double bkParZ = parZ;
            parX = mx;
            parY = this.field_70138_W;
            parZ = mz;
            AxisAlignedBB axisalignedbb1 = this.field_70121_D.func_72329_c();
            this.field_70121_D.func_72328_c(backUpAxisalignedBB);
            list = MCH_EntityTank.getCollidingBoundingBoxes((Entity)this, (AxisAlignedBB)this.field_70121_D.func_72321_a(mx, parY, mz));
            parY = this.calculateYOffset(list, this.field_70121_D, parY);
            parX = this.calculateXOffset(list, this.field_70121_D, parX);
            parZ = this.calculateZOffset(list, this.field_70121_D, parZ);
            parY = this.calculateYOffset(list, this.field_70121_D, (double)(-this.field_70138_W));
            if (bkParX * bkParX + bkParZ * bkParZ >= parX * parX + parZ * parZ) {
                parX = bkParX;
                parY = bkParY;
                parZ = bkParZ;
                this.field_70121_D.func_72328_c(axisalignedbb1);
            }
        }
        double prevPX = this.field_70165_t;
        double prevPZ = this.field_70161_v;
        this.field_70170_p.field_72984_F.func_76319_b();
        this.field_70170_p.field_72984_F.func_76320_a("rest");
        double minX = this.field_70121_D.field_72340_a;
        double minZ = this.field_70121_D.field_72339_c;
        double maxX = this.field_70121_D.field_72336_d;
        double maxZ = this.field_70121_D.field_72334_f;
        this.field_70165_t = (minX + maxX) / 2.0;
        this.field_70163_u = this.field_70121_D.field_72338_b + (double)this.field_70129_M - (double)this.field_70139_V;
        this.field_70161_v = (minZ + maxZ) / 2.0;
        this.field_70123_F = mx != parX || mz != parZ;
        this.field_70124_G = my != parY;
        this.field_70122_E = my != parY && my < 0.0;
        this.field_70132_H = this.field_70123_F || this.field_70124_G;
        this.func_70064_a(parY, this.field_70122_E);
        if (mx != parX) {
            this.field_70159_w = 0.0;
        }
        if (my != parY) {
            this.field_70181_x = 0.0;
        }
        if (mz != parZ) {
            this.field_70179_y = 0.0;
        }
        try {
            this.doBlockCollisions();
        }
        catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.func_85055_a((Throwable)throwable, (String)"Checking entity tile collision");
            CrashReportCategory crashreportcategory = crashreport.func_85058_a("Entity being checked for collision");
            this.func_85029_a(crashreportcategory);
        }
        this.field_70170_p.field_72984_F.func_76319_b();
    }

    private void rotationByKey(float partialTicks) {
        float rot = 0.2f;
        if (this.moveLeft && !this.moveRight) {
            this.addkeyRotValue -= rot * partialTicks;
        }
        if (this.moveRight && !this.moveLeft) {
            this.addkeyRotValue += rot * partialTicks;
        }
    }

    public void onUpdateAngles(float partialTicks) {
        boolean isFly;
        if (this.isDestroyed()) {
            return;
        }
        if (this.isGunnerMode) {
            this.setRotPitch(this.getRotPitch() * 0.95f);
            this.setRotYaw(this.getRotYaw() + this.getAcInfo().autoPilotRot * 0.2f);
            if (MathHelper.func_76135_e((float)this.getRotRoll()) > 20.0f) {
                this.setRotRoll(this.getRotRoll() * 0.95f);
            }
        }
        this.updateRecoil(partialTicks);
        this.setRotPitch(this.getRotPitch() + (this.WheelMng.targetPitch - this.getRotPitch()) * partialTicks);
        this.setRotRoll(this.getRotRoll() + (this.WheelMng.targetRoll - this.getRotRoll()) * partialTicks);
        boolean bl = isFly = MCH_Lib.getBlockIdY((Entity)this, (int)3, (int)-3) == 0;
        if (!isFly || this.getAcInfo().isFloat && this.getWaterDepth() > 0.0) {
            float gmy = 1.0f;
            if (!isFly) {
                Block block;
                gmy = this.getAcInfo().mobilityYawOnGround;
                if (!(this.getAcInfo().canRotOnGround || W_Block.isEqual((Block)(block = MCH_Lib.getBlockY((Entity)this, (int)3, (int)-2, (boolean)false)), (Block)W_Block.getWater()) || W_Block.isEqual((Block)block, (Block)W_Blocks.field_150350_a))) {
                    gmy = 0.0f;
                }
            }
            float pivotTurnThrottle = this.getAcInfo().pivotTurnThrottle;
            double dx = this.field_70165_t - this.field_70169_q;
            double dz = this.field_70161_v - this.field_70166_s;
            double dist = dx * dx + dz * dz;
            if (pivotTurnThrottle <= 0.0f || this.getCurrentThrottle() >= (double)pivotTurnThrottle || this.throttleBack >= pivotTurnThrottle / 10.0f || dist > (double)this.throttleBack * 0.01) {
                float flag;
                float sf = (float)Math.sqrt(dist <= 1.0 ? dist : 1.0);
                if (pivotTurnThrottle <= 0.0f) {
                    sf = 1.0f;
                }
                float f = flag = !this.throttleUp && this.throttleDown && this.getCurrentThrottle() < (double)pivotTurnThrottle + 0.05 ? -1.0f : 1.0f;
                if (this.moveLeft && !this.moveRight) {
                    this.setRotYaw(this.getRotYaw() - 0.6f * gmy * partialTicks * flag * sf);
                }
                if (this.moveRight && !this.moveLeft) {
                    this.setRotYaw(this.getRotYaw() + 0.6f * gmy * partialTicks * flag * sf);
                }
            }
        }
        this.addkeyRotValue = (float)((double)this.addkeyRotValue * (1.0 - (double)(0.1f * partialTicks)));
    }

    protected void onUpdate_Control() {
        if (this.isGunnerMode && !this.canUseFuel()) {
            this.switchGunnerMode(false);
        }
        this.throttleBack = (float)((double)this.throttleBack * 0.8);
        if (this.getBrake()) {
            this.throttleBack = (float)((double)this.throttleBack * 0.5);
            if (this.getCurrentThrottle() > 0.0) {
                this.addCurrentThrottle(-0.02 * (double)this.getAcInfo().throttleUpDown);
            } else {
                this.setCurrentThrottle(0.0);
            }
        }
        if (this.getRiddenByEntity() != null && !this.getRiddenByEntity().field_70128_L && this.isCanopyClose() && this.canUseFuel() && !this.isDestroyed()) {
            this.onUpdate_ControlSub();
        } else if (this.isTargetDrone() && this.canUseFuel() && !this.isDestroyed()) {
            this.throttleUp = true;
            this.onUpdate_ControlSub();
        } else if (this.getCurrentThrottle() > 0.0) {
            this.addCurrentThrottle(-0.0025 * (double)this.getAcInfo().throttleUpDown);
        } else {
            this.setCurrentThrottle(0.0);
        }
        if (this.getCurrentThrottle() < 0.0) {
            this.setCurrentThrottle(0.0);
        }
        if (this.field_70170_p.field_72995_K) {
            if (!W_Lib.isClientPlayer((Entity)this.getRiddenByEntity()) || this.getCountOnUpdate() % 200 == 0) {
                double ct = this.getThrottle();
                if (this.getCurrentThrottle() > ct) {
                    this.addCurrentThrottle(-0.005);
                }
                if (this.getCurrentThrottle() < ct) {
                    this.addCurrentThrottle(0.005);
                }
            }
        } else {
            this.setThrottle(this.getCurrentThrottle());
        }
    }

    protected void onUpdate_ControlSub() {
        if (!this.isGunnerMode) {
            float throttleUpDown = this.getAcInfo().throttleUpDown;
            if (this.throttleUp) {
                float f = throttleUpDown;
                if (this.getRidingEntity() != null) {
                    double mx = this.getRidingEntity().field_70159_w;
                    double mz = this.getRidingEntity().field_70179_y;
                    f *= MathHelper.func_76133_a((double)(mx * mx + mz * mz)) * this.getAcInfo().throttleUpDownOnEntity;
                }
                if (this.getAcInfo().enableBack && this.throttleBack > 0.0f) {
                    this.throttleBack = (float)((double)this.throttleBack - 0.01 * (double)f);
                } else {
                    this.throttleBack = 0.0f;
                    if (this.getCurrentThrottle() < 1.0) {
                        this.addCurrentThrottle(0.01 * (double)f);
                    } else {
                        this.setCurrentThrottle(1.0);
                    }
                }
            } else if (this.throttleDown) {
                if (this.getCurrentThrottle() > 0.0) {
                    this.addCurrentThrottle(-0.01 * (double)throttleUpDown);
                } else {
                    this.setCurrentThrottle(0.0);
                    if (this.getAcInfo().enableBack) {
                        this.throttleBack = (float)((double)this.throttleBack + 0.0025 * (double)throttleUpDown);
                        if (this.throttleBack > 0.6f) {
                            this.throttleBack = 0.6f;
                        }
                    }
                }
            } else if (this.cs_tankAutoThrottleDown && this.getCurrentThrottle() > 0.0) {
                this.addCurrentThrottle(-0.005 * (double)throttleUpDown);
                if (this.getCurrentThrottle() <= 0.0) {
                    this.setCurrentThrottle(0.0);
                }
            }
        }
    }

    protected void onUpdate_Particle2() {
        int d;
        if (!this.field_70170_p.field_72995_K) {
            return;
        }
        if ((double)this.getHP() >= (double)this.getMaxHP() * 0.5) {
            return;
        }
        if (this.getTankInfo() == null) {
            return;
        }
        int bbNum = this.getTankInfo().extraBoundingBox.size();
        if (bbNum < 0) {
            bbNum = 0;
        }
        if (this.isFirstDamageSmoke || this.prevDamageSmokePos.length != bbNum + 1) {
            this.prevDamageSmokePos = new Vec3[bbNum + 1];
        }
        float yaw = this.getRotYaw();
        float pitch = this.getRotPitch();
        float roll = this.getRotRoll();
        for (int ri = 0; ri < bbNum; ++ri) {
            if ((double)this.getHP() >= (double)this.getMaxHP() * 0.2 && this.getMaxHP() > 0 && (d = (int)(((double)this.getHP() / (double)this.getMaxHP() - 0.2) / 0.3 * 15.0)) > 0 && this.field_70146_Z.nextInt(d) > 0) continue;
            MCH_BoundingBox bb = (MCH_BoundingBox)this.getTankInfo().extraBoundingBox.get(ri);
            Vec3 pos = this.getTransformedPosition(bb.offsetX, bb.offsetY, bb.offsetZ);
            double x = pos.field_72450_a;
            double y = pos.field_72448_b;
            double z = pos.field_72449_c;
            this.onUpdate_Particle2SpawnSmoke(ri, x, y, z, 1.0f);
        }
        boolean b = true;
        if ((double)this.getHP() >= (double)this.getMaxHP() * 0.2 && this.getMaxHP() > 0 && (d = (int)(((double)this.getHP() / (double)this.getMaxHP() - 0.2) / 0.3 * 15.0)) > 0 && this.field_70146_Z.nextInt(d) > 0) {
            b = false;
        }
        if (b) {
            double px = this.field_70165_t;
            double py = this.field_70163_u;
            double pz = this.field_70161_v;
            if (this.getSeatInfo(0) != null && this.getSeatInfo((int)0).pos != null) {
                Vec3 pos = MCH_Lib.RotVec3((double)0.0, (double)this.getSeatInfo((int)0).pos.field_72448_b, (double)-2.0, (float)(-yaw), (float)(-pitch), (float)(-roll));
                px += pos.field_72450_a;
                py += pos.field_72448_b;
                pz += pos.field_72449_c;
            }
            this.onUpdate_Particle2SpawnSmoke(bbNum, px, py, pz, bbNum == 0 ? 2.0f : 1.0f);
        }
        this.isFirstDamageSmoke = false;
    }

    public void onUpdate_Particle2SpawnSmoke(int ri, double x, double y, double z, float size) {
        if (this.isFirstDamageSmoke || this.prevDamageSmokePos[ri] == null) {
            this.prevDamageSmokePos[ri] = Vec3.func_72443_a((double)x, (double)y, (double)z);
        }
        Vec3 prev = this.prevDamageSmokePos[ri];
        double dx = x - prev.field_72450_a;
        double dy = y - prev.field_72448_b;
        double dz = z - prev.field_72449_c;
        int num = 1;
        for (int i = 0; i < num; ++i) {
            float c = 0.2f + this.field_70146_Z.nextFloat() * 0.3f;
            MCH_ParticleParam prm = new MCH_ParticleParam(this.field_70170_p, "smoke", x, y, z);
            prm.motionX = (double)size * (this.field_70146_Z.nextDouble() - 0.5) * 0.3;
            prm.motionY = (double)size * this.field_70146_Z.nextDouble() * 0.1;
            prm.motionZ = (double)size * (this.field_70146_Z.nextDouble() - 0.5) * 0.3;
            prm.size = size * ((float)this.field_70146_Z.nextInt(5) + 5.0f) * 1.0f;
            prm.setColor(0.7f + this.field_70146_Z.nextFloat() * 0.1f, c, c, c);
            MCH_ParticlesUtil.spawnParticle((MCH_ParticleParam)prm);
        }
        this.prevDamageSmokePos[ri].field_72450_a = x;
        this.prevDamageSmokePos[ri].field_72448_b = y;
        this.prevDamageSmokePos[ri].field_72449_c = z;
    }

    public void onUpdate_Particle2SpawnSmode(int ri, double x, double y, double z, float size) {
        if (this.isFirstDamageSmoke) {
            this.prevDamageSmokePos[ri] = Vec3.func_72443_a((double)x, (double)y, (double)z);
        }
        Vec3 prev = this.prevDamageSmokePos[ri];
        double dx = x - prev.field_72450_a;
        double dy = y - prev.field_72448_b;
        double dz = z - prev.field_72449_c;
        int num = (int)((double)MathHelper.func_76133_a((double)(dx * dx + dy * dy + dz * dz)) / 0.3) + 1;
        for (int i = 0; i < num; ++i) {
            float c = 0.2f + this.field_70146_Z.nextFloat() * 0.3f;
            MCH_ParticleParam prm = new MCH_ParticleParam(this.field_70170_p, "smoke", x, y, z);
            prm.motionX = (double)size * (this.field_70146_Z.nextDouble() - 0.5) * 0.3;
            prm.motionY = (double)size * this.field_70146_Z.nextDouble() * 0.1;
            prm.motionZ = (double)size * (this.field_70146_Z.nextDouble() - 0.5) * 0.3;
            prm.size = size * ((float)this.field_70146_Z.nextInt(5) + 5.0f) * 1.0f;
            prm.setColor(0.7f + this.field_70146_Z.nextFloat() * 0.1f, c, c, c);
            MCH_ParticlesUtil.spawnParticle((MCH_ParticleParam)prm);
        }
        this.prevDamageSmokePos[ri].field_72450_a = x;
        this.prevDamageSmokePos[ri].field_72448_b = y;
        this.prevDamageSmokePos[ri].field_72449_c = z;
    }

    public void onUpdate_ParticleLandingGear() {
        this.WheelMng.particleLandingGear();
    }

    private void onUpdate_ParticleSplash() {
        if (this.getAcInfo() == null) {
            return;
        }
        if (!this.field_70170_p.field_72995_K) {
            return;
        }
        double mx = this.field_70165_t - this.field_70169_q;
        double mz = this.field_70161_v - this.field_70166_s;
        double dist = mx * mx + mz * mz;
        if (dist > 1.0) {
            dist = 1.0;
        }
        for (MCH_AircraftInfo.ParticleSplash p : this.getAcInfo().particleSplashs) {
            for (int i = 0; i < p.num; ++i) {
                if (!(dist > 0.03 + (double)this.field_70146_Z.nextFloat() * 0.1)) continue;
                this.setParticleSplash(p.pos, -mx * (double)p.acceleration, (double)p.motionY, -mz * (double)p.acceleration, p.gravity, (double)p.size * (0.5 + dist * 0.5), p.age);
            }
        }
    }

    private void setParticleSplash(Vec3 pos, double mx, double my, double mz, float gravity, double size, int age) {
        Vec3 v = this.getTransformedPosition(pos);
        v = v.func_72441_c(this.field_70146_Z.nextDouble() - 0.5, (this.field_70146_Z.nextDouble() - 0.5) * 0.5, this.field_70146_Z.nextDouble() - 0.5);
        int x = (int)(v.field_72450_a + 0.5);
        int y = (int)(v.field_72448_b + 0.0);
        int z = (int)(v.field_72449_c + 0.5);
        if (W_WorldFunc.isBlockWater((World)this.field_70170_p, (int)x, (int)y, (int)z)) {
            float c = this.field_70146_Z.nextFloat() * 0.3f + 0.7f;
            MCH_ParticleParam prm = new MCH_ParticleParam(this.field_70170_p, "smoke", v.field_72450_a, v.field_72448_b, v.field_72449_c);
            prm.motionX = mx + ((double)this.field_70146_Z.nextFloat() - 0.5) * 0.7;
            prm.motionY = my;
            prm.motionZ = mz + ((double)this.field_70146_Z.nextFloat() - 0.5) * 0.7;
            prm.size = (float)size * (this.field_70146_Z.nextFloat() * 0.2f + 0.8f);
            prm.setColor(0.9f, c, c, c);
            prm.age = age + (int)((double)this.field_70146_Z.nextFloat() * 0.5 * (double)age);
            prm.gravity = gravity;
            MCH_ParticlesUtil.spawnParticle((MCH_ParticleParam)prm);
        }
    }

    public void destroyAircraft() {
        super.destroyAircraft();
        this.rotDestroyedPitch = 0.0f;
        this.rotDestroyedRoll = 0.0f;
        this.rotDestroyedYaw = 0.0f;
    }

    public int getClientPositionDelayCorrection() {
        return this.getTankInfo() == null ? 7 : (this.getTankInfo().weightType == 1 ? 2 : 7);
    }

    protected void onUpdate_Client() {
        if (this.getRiddenByEntity() != null && W_Lib.isClientPlayer((Entity)this.getRiddenByEntity())) {
            this.getRiddenByEntity().field_70125_A = this.getRiddenByEntity().field_70127_C;
        }
        if (this.aircraftPosRotInc > 0) {
            this.applyServerPositionAndRotation();
        } else {
            this.func_70107_b(this.field_70165_t + this.field_70159_w, this.field_70163_u + this.field_70181_x, this.field_70161_v + this.field_70179_y);
            if (!this.isDestroyed() && (this.field_70122_E || MCH_Lib.getBlockIdY((Entity)this, (int)1, (int)-2) > 0)) {
                this.field_70159_w *= 0.95;
                this.field_70179_y *= 0.95;
                this.applyOnGroundPitch(0.95f);
            }
            if (this.func_70090_H()) {
                this.field_70159_w *= 0.99;
                this.field_70179_y *= 0.99;
            }
        }
        this.updateWheels();
        this.onUpdate_Particle2();
        this.updateSound();
        if (this.field_70170_p.field_72995_K) {
            this.onUpdate_ParticleLandingGear();
            this.onUpdate_ParticleSplash();
            this.onUpdate_ParticleSandCloud(true);
        }
        this.updateCamera(this.field_70165_t, this.field_70163_u, this.field_70161_v);
    }

    public void applyOnGroundPitch(float factor) {
    }

    private void onUpdate_Server() {
        float speedLimit;
        double motion;
        Block block;
        Entity rdnEnt = this.getRiddenByEntity();
        double prevMotion = Math.sqrt(this.field_70159_w * this.field_70159_w + this.field_70179_y * this.field_70179_y);
        double dp = 0.0;
        if (this.canFloatWater()) {
            dp = this.getWaterDepth();
        }
        boolean levelOff = this.isGunnerMode;
        if (dp == 0.0) {
            if (!levelOff) {
                this.field_70181_x += 0.04 + (double)(!this.func_70090_H() ? this.getAcInfo().gravity : this.getAcInfo().gravityInWater);
                this.field_70181_x += -0.047 * (1.0 - this.getCurrentThrottle());
            } else {
                this.field_70181_x *= 0.8;
            }
        } else {
            if (MathHelper.func_76135_e((float)this.getRotRoll()) < 40.0f) {
                // empty if block
            }
            if (dp < 1.0) {
                this.field_70181_x -= 1.0E-4;
                this.field_70181_x += 0.007 * this.getCurrentThrottle();
            } else {
                if (this.field_70181_x < 0.0) {
                    this.field_70181_x /= 2.0;
                }
                this.field_70181_x += 0.007;
            }
        }
        float throttle = (float)(this.getCurrentThrottle() / 10.0);
        Vec3 v = MCH_Lib.Rot2Vec3((float)this.getRotYaw(), (float)(this.getRotPitch() - 10.0f));
        if (!levelOff) {
            this.field_70181_x += v.field_72448_b * (double)throttle / 8.0;
        }
        boolean canMove = true;
        if (!(this.getAcInfo().canMoveOnGround || W_Block.isEqual((Block)(block = MCH_Lib.getBlockY((Entity)this, (int)3, (int)-2, (boolean)false)), (Block)W_Block.getWater()) || W_Block.isEqual((Block)block, (Block)W_Blocks.field_150350_a))) {
            canMove = false;
        }
        if (canMove) {
            if (this.getAcInfo().enableBack && this.throttleBack > 0.0f) {
                this.field_70159_w -= v.field_72450_a * (double)this.throttleBack;
                this.field_70179_y -= v.field_72449_c * (double)this.throttleBack;
            } else {
                this.field_70159_w += v.field_72450_a * (double)throttle;
                this.field_70179_y += v.field_72449_c * (double)throttle;
            }
        }
        if ((motion = Math.sqrt(this.field_70159_w * this.field_70159_w + this.field_70179_y * this.field_70179_y)) > (double)(speedLimit = this.getMaxSpeed())) {
            this.field_70159_w *= (double)speedLimit / motion;
            this.field_70179_y *= (double)speedLimit / motion;
            motion = speedLimit;
        }
        if (motion > prevMotion && this.currentSpeed < (double)speedLimit) {
            this.currentSpeed += ((double)speedLimit - this.currentSpeed) / 35.0;
            if (this.currentSpeed > (double)speedLimit) {
                this.currentSpeed = speedLimit;
            }
        } else {
            this.currentSpeed -= (this.currentSpeed - 0.07) / 35.0;
            if (this.currentSpeed < 0.07) {
                this.currentSpeed = 0.07;
            }
        }
        if (this.field_70122_E || MCH_Lib.getBlockIdY((Entity)this, (int)1, (int)-2) > 0) {
            this.field_70159_w *= (double)this.getAcInfo().motionFactor;
            this.field_70179_y *= (double)this.getAcInfo().motionFactor;
            if (MathHelper.func_76135_e((float)this.getRotPitch()) < 40.0f) {
                this.applyOnGroundPitch(0.8f);
            }
        }
        this.updateWheels();
        this.func_70091_d(this.field_70159_w, this.field_70181_x, this.field_70179_y);
        this.field_70181_x *= 0.95;
        this.field_70159_w *= (double)this.getAcInfo().motionFactor;
        this.field_70179_y *= (double)this.getAcInfo().motionFactor;
        this.func_70101_b(this.getRotYaw(), this.getRotPitch());
        this.onUpdate_updateBlock();
        this.updateCollisionBox();
        if (this.getRiddenByEntity() != null && this.getRiddenByEntity().field_70128_L) {
            this.unmountEntity();
            this.field_70153_n = null;
        }
    }

    private void collisionEntity(AxisAlignedBB bb) {
        if (bb == null) {
            return;
        }
        double speed = Math.sqrt(this.field_70159_w * this.field_70159_w + this.field_70181_x * this.field_70181_x + this.field_70179_y * this.field_70179_y);
        if (speed <= 0.05) {
            return;
        }
        Entity rider = this.getRiddenByEntity();
        float damage = (float)(speed * 15.0);
        MCH_EntityAircraft rideAc = this.field_70154_o instanceof MCH_EntityAircraft ? (MCH_EntityAircraft)this.field_70154_o : (this.field_70154_o instanceof MCH_EntitySeat ? ((MCH_EntitySeat)this.field_70154_o).getParent() : null);
        List list = this.field_70170_p.func_94576_a((Entity)this, bb.func_72314_b(0.3, 0.3, 0.3), (IEntitySelector)new /* Unavailable Anonymous Inner Class!! */);
        for (int i = 0; i < list.size(); ++i) {
            Entity e = (Entity)list.get(i);
            if (!this.shouldCollisionDamage(e)) continue;
            double dx = e.field_70165_t - this.field_70165_t;
            double dz = e.field_70161_v - this.field_70161_v;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > 5.0) {
                dist = 5.0;
            }
            damage = (float)((double)damage + (5.0 - dist));
            DamageSource ds = rider instanceof EntityLivingBase ? DamageSource.func_76358_a((EntityLivingBase)((EntityLivingBase)rider)) : DamageSource.field_76377_j;
            MCH_Lib.applyEntityHurtResistantTimeConfig((Entity)e);
            e.func_70097_a(ds, damage);
            if (e instanceof MCH_EntityAircraft) {
                e.field_70159_w += this.field_70159_w * 0.05;
                e.field_70179_y += this.field_70179_y * 0.05;
            } else if (e instanceof EntityArrow) {
                e.func_70106_y();
            } else {
                e.field_70159_w += this.field_70159_w * 1.5;
                e.field_70179_y += this.field_70179_y * 1.5;
            }
            if (this.getTankInfo().weightType != 2 && (e.field_70130_N >= 1.0f || (double)e.field_70131_O >= 1.5)) {
                ds = e instanceof EntityLivingBase ? DamageSource.func_76358_a((EntityLivingBase)((EntityLivingBase)e)) : DamageSource.field_76377_j;
                this.func_70097_a(ds, damage / 3.0f);
            }
            MCH_Lib.DbgLog((World)this.field_70170_p, (String)"MCH_EntityTank.collisionEntity damage=%.1f %s", (Object[])new Object[]{Float.valueOf(damage), e.toString()});
        }
    }

    private boolean shouldCollisionDamage(Entity e) {
        MCH_EntityAircraft ac;
        if (this.getSeatIdByEntity(e) >= 0) {
            return false;
        }
        if (this.noCollisionEntities.containsKey(e)) {
            return false;
        }
        if (e instanceof MCH_EntityHitBox && ((MCH_EntityHitBox)e).parent != null && this.noCollisionEntities.containsKey(ac = ((MCH_EntityHitBox)e).parent)) {
            return false;
        }
        if (e.field_70154_o instanceof MCH_EntityAircraft && this.noCollisionEntities.containsKey(e.field_70154_o)) {
            return false;
        }
        return !(e.field_70154_o instanceof MCH_EntitySeat) || ((MCH_EntitySeat)e.field_70154_o).getParent() == null || !this.noCollisionEntities.containsKey(((MCH_EntitySeat)e.field_70154_o).getParent());
    }

    public void updateCollisionBox() {
        if (this.getAcInfo() == null) {
            return;
        }
        this.WheelMng.updateBlock();
        for (MCH_BoundingBox bb : this.extraBoundingBox) {
            if (this.field_70146_Z.nextInt(3) != 0) continue;
            if (MCH_Config.Collision_DestroyBlock.prmBool) {
                Vec3 v = this.getTransformedPosition(bb.offsetX, bb.offsetY, bb.offsetZ);
                this.destoryBlockRange(v, (double)bb.width, (double)bb.height);
            }
            this.collisionEntity(bb.boundingBox);
        }
        if (MCH_Config.Collision_DestroyBlock.prmBool) {
            this.destoryBlockRange(this.getTransformedPosition(0.0, 0.0, 0.0), (double)this.field_70130_N * 1.5, (double)(this.field_70131_O * 2.0f));
        }
        this.collisionEntity(this.func_70046_E());
    }

    public void destoryBlockRange(Vec3 v, double w, double h) {
        if (this.getAcInfo() == null) {
            return;
        }
        List destroyBlocks = MCH_Config.getBreakableBlockListFromType((int)this.getTankInfo().weightType);
        List noDestroyBlocks = MCH_Config.getNoBreakableBlockListFromType((int)this.getTankInfo().weightType);
        List destroyMaterials = MCH_Config.getBreakableMaterialListFromType((int)this.getTankInfo().weightType);
        int ws = (int)(w + 2.0) / 2;
        int hs = (int)(h + 2.0) / 2;
        for (int x = -ws; x <= ws; ++x) {
            block1: for (int z = -ws; z <= ws; ++z) {
                block2: for (int y = -hs; y <= hs + 1; ++y) {
                    int bx = (int)(v.field_72450_a + (double)x - 0.5);
                    int by = (int)(v.field_72448_b + (double)y - 1.0);
                    int bz = (int)(v.field_72449_c + (double)z - 0.5);
                    Block block = by >= 0 && by < 256 ? this.field_70170_p.func_147439_a(bx, by, bz) : Blocks.field_150350_a;
                    Material mat = block.func_149688_o();
                    if (Block.func_149680_a((Block)block, (Block)Blocks.field_150350_a)) continue;
                    for (Block c : noDestroyBlocks) {
                        if (!Block.func_149680_a((Block)block, (Block)c)) continue;
                        block = null;
                        break;
                    }
                    if (block == null) continue block1;
                    for (Block c : destroyBlocks) {
                        if (!Block.func_149680_a((Block)block, (Block)c)) continue;
                        this.destroyBlock(bx, by, bz);
                        mat = null;
                        break;
                    }
                    if (mat == null) continue block1;
                    for (Material m : destroyMaterials) {
                        if (block.func_149688_o() != m) continue;
                        this.destroyBlock(bx, by, bz);
                        continue block2;
                    }
                }
            }
        }
    }

    public void destroyBlock(int bx, int by, int bz) {
        if (this.field_70146_Z.nextInt(8) == 0) {
            W_WorldFunc.destroyBlock((World)this.field_70170_p, (int)bx, (int)by, (int)bz, (boolean)true);
        } else {
            this.field_70170_p.func_147468_f(bx, by, bz);
        }
    }

    private void updateWheels() {
        this.WheelMng.move(this.field_70159_w, this.field_70181_x, this.field_70179_y);
    }

    public float getMaxSpeed() {
        return this.getTankInfo().speed + 0.0f;
    }

    public void setAngles(Entity player, boolean fixRot, float fixYaw, float fixPitch, float deltaX, float deltaY, float x, float y, float partialTicks) {
        if (partialTicks < 0.03f) {
            partialTicks = 0.4f;
        }
        if (partialTicks > 0.9f) {
            partialTicks = 0.6f;
        }
        this.lowPassPartialTicks.put(partialTicks);
        partialTicks = this.lowPassPartialTicks.getAvg();
        float ac_pitch = this.getRotPitch();
        float ac_yaw = this.getRotYaw();
        float ac_roll = this.getRotRoll();
        if (this.isFreeLookMode()) {
            y = 0.0f;
            x = 0.0f;
        }
        float yaw = 0.0f;
        float pitch = 0.0f;
        float roll = 0.0f;
        MCH_Math.FMatrix m_add = MCH_Math.newMatrix();
        MCH_Math.MatTurnZ((MCH_Math.FMatrix)m_add, (float)(roll / 180.0f * (float)Math.PI));
        MCH_Math.MatTurnX((MCH_Math.FMatrix)m_add, (float)(pitch / 180.0f * (float)Math.PI));
        MCH_Math.MatTurnY((MCH_Math.FMatrix)m_add, (float)(yaw / 180.0f * (float)Math.PI));
        MCH_Math.MatTurnZ((MCH_Math.FMatrix)m_add, (float)((float)((double)(this.getRotRoll() / 180.0f) * Math.PI)));
        MCH_Math.MatTurnX((MCH_Math.FMatrix)m_add, (float)((float)((double)(this.getRotPitch() / 180.0f) * Math.PI)));
        MCH_Math.MatTurnY((MCH_Math.FMatrix)m_add, (float)((float)((double)(this.getRotYaw() / 180.0f) * Math.PI)));
        MCH_Math.FVector3D v = MCH_Math.MatrixToEuler((MCH_Math.FMatrix)m_add);
        v.x = MCH_Lib.RNG((float)v.x, (float)-90.0f, (float)90.0f);
        v.z = MCH_Lib.RNG((float)v.z, (float)-90.0f, (float)90.0f);
        if (v.z > 180.0f) {
            v.z -= 360.0f;
        }
        if (v.z < -180.0f) {
            v.z += 360.0f;
        }
        this.setRotYaw(v.y);
        this.setRotPitch(v.x);
        this.setRotRoll(v.z);
        this.onUpdateAngles(partialTicks);
        if (this.getAcInfo().limitRotation) {
            v.x = MCH_Lib.RNG((float)this.getRotPitch(), (float)-90.0f, (float)90.0f);
            v.z = MCH_Lib.RNG((float)this.getRotRoll(), (float)-90.0f, (float)90.0f);
            this.setRotPitch(v.x);
            this.setRotRoll(v.z);
        }
        float RV = 180.0f;
        if (MathHelper.func_76135_e((float)this.getRotPitch()) > 90.0f) {
            MCH_Lib.DbgLog((boolean)true, (String)"MCH_EntityAircraft.setAngles Error:Pitch=%.1f", (Object[])new Object[]{Float.valueOf(this.getRotPitch())});
            this.setRotPitch(0.0f);
        }
        if (this.getRotRoll() > 180.0f) {
            this.setRotRoll(this.getRotRoll() - 360.0f);
        }
        if (this.getRotRoll() < -180.0f) {
            this.setRotRoll(this.getRotRoll() + 360.0f);
        }
        this.prevRotationRoll = this.getRotRoll();
        this.field_70127_C = this.getRotPitch();
        if (this.getRidingEntity() == null) {
            this.field_70126_B = this.getRotYaw();
        }
        float deltaLimit = this.getAcInfo().cameraRotationSpeed * partialTicks;
        MCH_WeaponSet ws = this.getCurrentWeapon(player);
        if (deltaX > (deltaLimit *= ws != null && ws.getInfo() != null ? ws.getInfo().cameraRotationSpeedPitch : 1.0f)) {
            deltaX = deltaLimit;
        }
        if (deltaX < -deltaLimit) {
            deltaX = -deltaLimit;
        }
        if (deltaY > deltaLimit) {
            deltaY = deltaLimit;
        }
        if (deltaY < -deltaLimit) {
            deltaY = -deltaLimit;
        }
        if (this.isOverridePlayerYaw() || fixRot) {
            if (this.getRidingEntity() == null) {
                player.field_70126_B = this.getRotYaw() + fixYaw;
            } else {
                if (this.getRotYaw() - player.field_70177_z > 180.0f) {
                    player.field_70126_B += 360.0f;
                }
                if (this.getRotYaw() - player.field_70177_z < -180.0f) {
                    player.field_70126_B -= 360.0f;
                }
            }
            player.field_70177_z = this.getRotYaw() + fixYaw;
        } else {
            player.func_70082_c(deltaX, 0.0f);
        }
        if (this.isOverridePlayerPitch() || fixRot) {
            player.field_70127_C = this.getRotPitch() + fixPitch;
            player.field_70125_A = this.getRotPitch() + fixPitch;
        } else {
            player.func_70082_c(0.0f, deltaY);
        }
        float playerYaw = MathHelper.func_76142_g((float)(this.getRotYaw() - player.field_70177_z));
        float playerPitch = this.getRotPitch() * MathHelper.func_76134_b((float)((float)((double)playerYaw * Math.PI / 180.0))) + -this.getRotRoll() * MathHelper.func_76126_a((float)((float)((double)playerYaw * Math.PI / 180.0)));
        if (MCH_MOD.proxy.isFirstPerson()) {
            player.field_70125_A = MCH_Lib.RNG((float)player.field_70125_A, (float)(playerPitch + this.getAcInfo().minRotationPitch), (float)(playerPitch + this.getAcInfo().maxRotationPitch));
            player.field_70125_A = MCH_Lib.RNG((float)player.field_70125_A, (float)-90.0f, (float)90.0f);
        }
        player.field_70127_C = player.field_70125_A;
        if (this.getRidingEntity() == null && ac_yaw != this.getRotYaw() || ac_pitch != this.getRotPitch() || ac_roll != this.getRotRoll()) {
            this.aircraftRotChanged = true;
        }
    }

    public float getSoundVolume() {
        if (this.getAcInfo() != null && this.getAcInfo().throttleUpDown <= 0.0f) {
            return 0.0f;
        }
        return this.soundVolume * 0.7f;
    }

    public void updateSound() {
        float target = (float)this.getCurrentThrottle();
        if (this.getRiddenByEntity() != null && (this.partCanopy == null || this.getCanopyRotation() < 1.0f)) {
            target += 0.1f;
        }
        if (this.moveLeft || this.moveRight || this.throttleDown) {
            this.soundVolumeTarget += 0.1f;
            if (this.soundVolumeTarget > 0.75f) {
                this.soundVolumeTarget = 0.75f;
            }
        } else {
            this.soundVolumeTarget *= 0.8f;
        }
        if (target < this.soundVolumeTarget) {
            target = this.soundVolumeTarget;
        }
        if (this.soundVolume < target) {
            this.soundVolume += 0.02f;
            if (this.soundVolume >= target) {
                this.soundVolume = target;
            }
        } else if (this.soundVolume > target) {
            this.soundVolume -= 0.02f;
            if (this.soundVolume <= target) {
                this.soundVolume = target;
            }
        }
    }

    public float getSoundPitch() {
        float target2;
        float target1 = (float)(0.5 + this.getCurrentThrottle() * 0.5);
        return target1 > (target2 = (float)(0.5 + (double)this.soundVolumeTarget * 0.5)) ? target1 : target2;
    }

    public String getDefaultSoundName() {
        return "prop";
    }

    public boolean hasBrake() {
        return true;
    }

    public void updateParts(int stat) {
        MCH_Parts[] parts;
        super.updateParts(stat);
        if (this.isDestroyed()) {
            return;
        }
        for (MCH_Parts p : parts = new MCH_Parts[0]) {
            if (p == null) continue;
            p.updateStatusClient(stat);
            p.update();
        }
    }

    public float getUnfoldLandingGearThrottle() {
        return 0.7f;
    }
}

