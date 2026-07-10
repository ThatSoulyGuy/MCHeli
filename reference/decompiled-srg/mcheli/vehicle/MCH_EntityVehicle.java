/*
 * Decompiled with CFR 0.152.
 */
package mcheli.vehicle;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import mcheli.MCH_Config;
import mcheli.MCH_Lib;
import mcheli.aircraft.MCH_AircraftInfo;
import mcheli.aircraft.MCH_EntityAircraft;
import mcheli.aircraft.MCH_PacketStatusRequest;
import mcheli.vehicle.MCH_VehicleInfo;
import mcheli.vehicle.MCH_VehicleInfoManager;
import mcheli.weapon.MCH_WeaponParam;
import mcheli.weapon.MCH_WeaponSet;
import mcheli.wrapper.W_Entity;
import mcheli.wrapper.W_Lib;
import mcheli.wrapper.W_WorldFunc;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

public class MCH_EntityVehicle
extends MCH_EntityAircraft {
    private MCH_VehicleInfo vehicleInfo = null;
    public boolean isUsedPlayer;
    public float lastRiderYaw;
    public float lastRiderPitch;

    public MCH_EntityVehicle(World world) {
        super(world);
        this.currentSpeed = 0.07;
        this.field_70156_m = true;
        this.func_70105_a(2.0f, 0.7f);
        this.field_70129_M = this.field_70131_O / 2.0f;
        this.field_70159_w = 0.0;
        this.field_70181_x = 0.0;
        this.field_70179_y = 0.0;
        this.isUsedPlayer = false;
        this.lastRiderYaw = 0.0f;
        this.lastRiderPitch = 0.0f;
        this.weapons = this.createWeapon(0);
    }

    public String getKindName() {
        return "vehicles";
    }

    public String getEntityType() {
        return "Vehicle";
    }

    public MCH_VehicleInfo getVehicleInfo() {
        return this.vehicleInfo;
    }

    public void changeType(String type) {
        if (!type.isEmpty()) {
            this.vehicleInfo = MCH_VehicleInfoManager.get((String)type);
        }
        if (this.vehicleInfo == null) {
            MCH_Lib.Log((Entity)this, (String)"##### MCH_EntityVehicle changeVehicleType() Vehicle info null %d, %s, %s", (Object[])new Object[]{W_Entity.getEntityId((Entity)this), type, this.getEntityName()});
            this.func_70106_y();
        } else {
            this.setAcInfo((MCH_AircraftInfo)this.vehicleInfo);
            this.newSeats(this.getAcInfo().getNumSeatAndRack());
            this.weapons = this.createWeapon(1 + this.getSeatNum());
            this.initPartRotation(this.field_70177_z, this.field_70125_A);
        }
    }

    public boolean canMountWithNearEmptyMinecart() {
        return MCH_Config.MountMinecartVehicle.prmBool;
    }

    protected void func_70088_a() {
        super.func_70088_a();
    }

    protected void func_70014_b(NBTTagCompound par1NBTTagCompound) {
        super.func_70014_b(par1NBTTagCompound);
    }

    protected void func_70037_a(NBTTagCompound par1NBTTagCompound) {
        super.func_70037_a(par1NBTTagCompound);
        if (this.vehicleInfo == null) {
            this.vehicleInfo = MCH_VehicleInfoManager.get((String)this.getTypeName());
            if (this.vehicleInfo == null) {
                MCH_Lib.Log((Entity)this, (String)"##### MCH_EntityVehicle readEntityFromNBT() Vehicle info null %d, %s", (Object[])new Object[]{W_Entity.getEntityId((Entity)this), this.getEntityName()});
                this.func_70106_y();
            } else {
                this.setAcInfo((MCH_AircraftInfo)this.vehicleInfo);
            }
        }
    }

    public Item getItem() {
        return this.getVehicleInfo() != null ? this.getVehicleInfo().item : null;
    }

    public void func_70106_y() {
        super.func_70106_y();
    }

    public float getSoundVolume() {
        return (float)this.getCurrentThrottle() * 2.0f;
    }

    public float getSoundPitch() {
        return (float)(this.getCurrentThrottle() * 0.5);
    }

    public String getDefaultSoundName() {
        return "";
    }

    @SideOnly(value=Side.CLIENT)
    public void zoomCamera() {
        if (this.canZoom()) {
            float z = this.camera.getCameraZoom();
            this.camera.setCameraZoom((double)(z += 1.0f) <= (double)this.getZoomMax() + 0.01 ? z : 1.0f);
        }
    }

    public void _updateCameraRotate(float yaw, float pitch) {
        this.camera.prevRotationYaw = this.camera.rotationYaw;
        this.camera.prevRotationPitch = this.camera.rotationPitch;
        if (pitch > 89.0f) {
            pitch = 89.0f;
        }
        if (pitch < -89.0f) {
            pitch = -89.0f;
        }
        this.camera.rotationYaw = yaw;
        this.camera.rotationPitch = pitch;
    }

    public boolean isCameraView(Entity entity) {
        return true;
    }

    public boolean useCurrentWeapon(MCH_WeaponParam prm) {
        MCH_AircraftInfo.Weapon w;
        MCH_WeaponSet currentWs;
        if (prm.user != null && (currentWs = this.getCurrentWeapon(prm.user)) != null && (w = this.getAcInfo().getWeaponByName(currentWs.getInfo().name)) != null && w.maxYaw != 0.0f && w.minYaw != 0.0f) {
            return super.useCurrentWeapon(prm);
        }
        float breforeUseWeaponPitch = this.field_70125_A;
        float breforeUseWeaponYaw = this.field_70177_z;
        this.field_70125_A = prm.user.field_70125_A;
        this.field_70177_z = prm.user.field_70177_z;
        boolean result = super.useCurrentWeapon(prm);
        this.field_70125_A = breforeUseWeaponPitch;
        this.field_70177_z = breforeUseWeaponYaw;
        return result;
    }

    public void onUpdateAircraft() {
        if (this.vehicleInfo == null) {
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
            this.getRiddenByEntity().field_70125_A = 0.0f;
            this.getRiddenByEntity().field_70127_C = 0.0f;
            this.initCurrentWeapon(this.getRiddenByEntity());
        }
        this.updateWeapons();
        this.onUpdate_Seats();
        this.onUpdate_Control();
        this.field_70169_q = this.field_70165_t;
        this.field_70167_r = this.field_70163_u;
        this.field_70166_s = this.field_70161_v;
        if (this.func_70090_H()) {
            this.field_70125_A *= 0.9f;
        }
        if (this.field_70170_p.field_72995_K) {
            this.onUpdate_Client();
        } else {
            this.onUpdate_Server();
        }
    }

    protected void onUpdate_Control() {
        double max_y = 1.0;
        if (this.field_70153_n != null && !this.field_70153_n.field_70128_L) {
            if (this.getVehicleInfo().isEnableMove || this.getVehicleInfo().isEnableRot) {
                this.onUpdate_ControlOnGround();
            }
        } else if (this.getCurrentThrottle() > 0.0) {
            this.addCurrentThrottle(-0.00125);
        } else {
            this.setCurrentThrottle(0.0);
        }
        if (this.getCurrentThrottle() < 0.0) {
            this.setCurrentThrottle(0.0);
        }
        if (this.field_70170_p.field_72995_K) {
            if (!W_Lib.isClientPlayer((Entity)this.getRiddenByEntity())) {
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

    protected void onUpdate_ControlOnGround() {
        if (!this.field_70170_p.field_72995_K) {
            boolean move = false;
            float yaw = this.field_70177_z;
            double x = 0.0;
            double z = 0.0;
            if (this.getVehicleInfo().isEnableMove) {
                if (this.throttleUp) {
                    yaw = this.field_70177_z;
                    x += Math.sin((double)yaw * Math.PI / 180.0);
                    z += Math.cos((double)yaw * Math.PI / 180.0);
                    move = true;
                }
                if (this.throttleDown) {
                    yaw = this.field_70177_z - 180.0f;
                    x += Math.sin((double)yaw * Math.PI / 180.0);
                    z += Math.cos((double)yaw * Math.PI / 180.0);
                    move = true;
                }
            }
            if (this.getVehicleInfo().isEnableMove) {
                if (this.moveLeft && !this.moveRight) {
                    this.field_70177_z = (float)((double)this.field_70177_z - 0.5);
                }
                if (this.moveRight && !this.moveLeft) {
                    this.field_70177_z = (float)((double)this.field_70177_z + 0.5);
                }
            }
            if (move) {
                double d = Math.sqrt(x * x + z * z);
                this.field_70159_w -= x / d * (double)0.03f;
                this.field_70179_y += z / d * (double)0.03f;
            }
        }
    }

    protected void onUpdate_Particle() {
        int y;
        double particlePosY = this.field_70163_u;
        boolean b = false;
        for (y = 0; y < 5 && !b; ++y) {
            int z;
            int x;
            for (x = -1; x <= 1; ++x) {
                for (z = -1; z <= 1; ++z) {
                    int block = W_WorldFunc.getBlockId((World)this.field_70170_p, (int)((int)(this.field_70165_t + 0.5) + x), (int)((int)(this.field_70163_u + 0.5) - y), (int)((int)(this.field_70161_v + 0.5) + z));
                    if (block == 0 || b) continue;
                    particlePosY = (int)(this.field_70163_u + 1.0) - y;
                    b = true;
                }
            }
            for (x = -3; b && x <= 3; ++x) {
                for (z = -3; z <= 3; ++z) {
                    if (!W_WorldFunc.isBlockWater((World)this.field_70170_p, (int)((int)(this.field_70165_t + 0.5) + x), (int)((int)(this.field_70163_u + 0.5) - y), (int)((int)(this.field_70161_v + 0.5) + z))) continue;
                    int i = 0;
                    while ((double)i < 7.0 * this.getCurrentThrottle()) {
                        this.field_70170_p.func_72869_a("splash", this.field_70165_t + 0.5 + (double)x + (this.field_70146_Z.nextDouble() - 0.5) * 2.0, particlePosY + this.field_70146_Z.nextDouble(), this.field_70161_v + 0.5 + (double)z + (this.field_70146_Z.nextDouble() - 0.5) * 2.0, (double)x + (this.field_70146_Z.nextDouble() - 0.5) * 2.0, -0.3, (double)z + (this.field_70146_Z.nextDouble() - 0.5) * 2.0);
                        ++i;
                    }
                }
            }
        }
        double pn = (double)(5 - y + 1) / 5.0;
        if (b) {
            for (int k = 0; k < (int)(this.getCurrentThrottle() * 6.0 * pn); ++k) {
                float f3 = 0.25f;
                this.field_70170_p.func_72869_a("explode", this.field_70165_t + (this.field_70146_Z.nextDouble() - 0.5), particlePosY + (this.field_70146_Z.nextDouble() - 0.5), this.field_70161_v + (this.field_70146_Z.nextDouble() - 0.5), (this.field_70146_Z.nextDouble() - 0.5) * 2.0, -0.4, (this.field_70146_Z.nextDouble() - 0.5) * 2.0);
            }
        }
    }

    protected void onUpdate_Client() {
        this.updateCameraViewers();
        if (this.field_70153_n != null && W_Lib.isClientPlayer((Entity)this.getRiddenByEntity())) {
            this.getRiddenByEntity().field_70125_A = this.getRiddenByEntity().field_70127_C;
        }
        if (this.aircraftPosRotInc > 0) {
            double rpinc = this.aircraftPosRotInc;
            double yaw = MathHelper.func_76138_g((double)(this.aircraftYaw - (double)this.field_70177_z));
            this.field_70177_z = (float)((double)this.field_70177_z + yaw / rpinc);
            this.field_70125_A = (float)((double)this.field_70125_A + (this.aircraftPitch - (double)this.field_70125_A) / rpinc);
            this.func_70107_b(this.field_70165_t + (this.aircraftX - this.field_70165_t) / rpinc, this.field_70163_u + (this.aircraftY - this.field_70163_u) / rpinc, this.field_70161_v + (this.aircraftZ - this.field_70161_v) / rpinc);
            this.func_70101_b(this.field_70177_z, this.field_70125_A);
            --this.aircraftPosRotInc;
        } else {
            this.func_70107_b(this.field_70165_t + this.field_70159_w, this.field_70163_u + this.field_70181_x, this.field_70161_v + this.field_70179_y);
            if (this.field_70122_E) {
                this.field_70159_w *= 0.95;
                this.field_70179_y *= 0.95;
            }
            if (this.func_70090_H()) {
                this.field_70159_w *= 0.99;
                this.field_70179_y *= 0.99;
            }
        }
        if (this.field_70153_n != null) {
            // empty if block
        }
        this.updateCamera(this.field_70165_t, this.field_70163_u, this.field_70161_v);
    }

    private void onUpdate_Server() {
        double prevMotion = Math.sqrt(this.field_70159_w * this.field_70159_w + this.field_70179_y * this.field_70179_y);
        this.updateCameraViewers();
        double dp = 0.0;
        if (this.canFloatWater()) {
            dp = this.getWaterDepth();
        }
        if (dp == 0.0) {
            this.field_70181_x += (double)(!this.func_70090_H() ? this.getAcInfo().gravity : this.getAcInfo().gravityInWater);
        } else if (dp < 1.0) {
            this.field_70181_x -= 1.0E-4;
            this.field_70181_x += 0.007 * this.getCurrentThrottle();
        } else {
            if (this.field_70181_x < 0.0) {
                this.field_70181_x /= 2.0;
            }
            this.field_70181_x += 0.007;
        }
        double motion = Math.sqrt(this.field_70159_w * this.field_70159_w + this.field_70179_y * this.field_70179_y);
        float speedLimit = this.getAcInfo().speed;
        if (motion > (double)speedLimit) {
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
        if (this.field_70122_E) {
            this.field_70159_w *= 0.5;
            this.field_70179_y *= 0.5;
        }
        this.func_70091_d(this.field_70159_w, this.field_70181_x, this.field_70179_y);
        this.field_70181_x *= 0.95;
        this.field_70159_w *= 0.99;
        this.field_70179_y *= 0.99;
        this.onUpdate_updateBlock();
        if (this.field_70153_n != null && this.field_70153_n.field_70128_L) {
            this.unmountEntity();
            this.field_70153_n = null;
        }
    }

    public void onUpdateAngles(float partialTicks) {
    }

    public void _updateRiderPosition() {
        float yaw = this.field_70177_z;
        if (this.field_70153_n != null) {
            this.field_70177_z = this.field_70153_n.field_70177_z;
        }
        super.func_70043_V();
        this.field_70177_z = yaw;
    }

    public boolean canSwitchFreeLook() {
        return false;
    }
}

