/*
 * Decompiled with CFR 0.152.
 */
package mcheli.aircraft;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import mcheli.MCH_Lib;
import mcheli.aircraft.MCH_EntityAircraft;
import mcheli.aircraft.MCH_SeatRackInfo;
import mcheli.tool.MCH_ItemWrench;
import mcheli.wrapper.W_Entity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

public class MCH_EntitySeat
extends W_Entity {
    public String parentUniqueID;
    private MCH_EntityAircraft parent;
    public int seatID;
    public int parentSearchCount;
    protected Entity lastRiddenByEntity;
    public static final float BB_SIZE = 1.0f;

    public MCH_EntitySeat(World world) {
        super(world);
        this.func_70105_a(1.0f, 1.0f);
        this.field_70129_M = 0.0f;
        this.field_70159_w = 0.0;
        this.field_70181_x = 0.0;
        this.field_70179_y = 0.0;
        this.seatID = -1;
        this.setParent(null);
        this.parentSearchCount = 0;
        this.lastRiddenByEntity = null;
        this.field_70158_ak = true;
        this.field_70178_ae = true;
    }

    public MCH_EntitySeat(World world, double x, double y, double z) {
        this(world);
        this.func_70107_b(x, y + 1.0, z);
        this.field_70169_q = x;
        this.field_70167_r = y + 1.0;
        this.field_70166_s = z;
    }

    protected boolean func_70041_e_() {
        return false;
    }

    public AxisAlignedBB func_70114_g(Entity par1Entity) {
        return par1Entity.field_70121_D;
    }

    public AxisAlignedBB func_70046_E() {
        return this.field_70121_D;
    }

    public boolean func_70104_M() {
        return false;
    }

    public double func_70042_X() {
        return -0.3;
    }

    public boolean func_70097_a(DamageSource par1DamageSource, float par2) {
        if (this.getParent() != null) {
            return this.getParent().func_70097_a(par1DamageSource, par2);
        }
        return false;
    }

    public boolean func_70067_L() {
        return !this.field_70128_L;
    }

    @SideOnly(value=Side.CLIENT)
    public void func_70056_a(double par1, double par3, double par5, float par7, float par8, int par9) {
    }

    public void func_70106_y() {
        super.func_70106_y();
    }

    public void func_70071_h_() {
        super.func_70071_h_();
        this.field_70143_R = 0.0f;
        if (this.field_70153_n != null) {
            this.field_70153_n.field_70143_R = 0.0f;
        }
        if (this.lastRiddenByEntity == null && this.field_70153_n != null) {
            if (this.getParent() != null) {
                MCH_Lib.DbgLog((World)this.field_70170_p, (String)"MCH_EntitySeat.onUpdate:SeatID=%d", (Object[])new Object[]{this.seatID, this.field_70153_n.toString()});
                this.getParent().onMountPlayerSeat(this, this.field_70153_n);
            }
        } else if (this.lastRiddenByEntity != null && this.field_70153_n == null && this.getParent() != null) {
            MCH_Lib.DbgLog((World)this.field_70170_p, (String)"MCH_EntitySeat.onUpdate:SeatID=%d", (Object[])new Object[]{this.seatID, this.lastRiddenByEntity.toString()});
            this.getParent().onUnmountPlayerSeat(this, this.lastRiddenByEntity);
        }
        if (this.field_70170_p.field_72995_K) {
            this.onUpdate_Client();
        } else {
            this.onUpdate_Server();
        }
        this.lastRiddenByEntity = this.field_70153_n;
    }

    private void onUpdate_Client() {
        this.checkDetachmentAndDelete();
    }

    private void onUpdate_Server() {
        this.checkDetachmentAndDelete();
        if (this.field_70153_n != null && this.field_70153_n.field_70128_L) {
            this.field_70153_n = null;
        }
    }

    public void func_70043_V() {
        this.updatePosition();
    }

    public void updatePosition() {
        Entity ridEnt = this.field_70153_n;
        if (ridEnt != null) {
            ridEnt.func_70107_b(this.field_70165_t, this.field_70163_u, this.field_70161_v);
            ridEnt.field_70179_y = 0.0;
            ridEnt.field_70181_x = 0.0;
            ridEnt.field_70159_w = 0.0;
        }
    }

    public void updateRotation(float yaw, float pitch) {
        Entity ridEnt = this.field_70153_n;
        if (ridEnt != null) {
            ridEnt.field_70177_z = yaw;
            ridEnt.field_70125_A = pitch;
        }
    }

    protected void checkDetachmentAndDelete() {
        if (!this.field_70128_L && (this.seatID < 0 || this.getParent() == null || this.getParent().field_70128_L)) {
            if (this.getParent() != null && this.getParent().field_70128_L) {
                this.parentSearchCount = 100000000;
            }
            if (this.parentSearchCount >= 1200) {
                this.func_70106_y();
                if (!this.field_70170_p.field_72995_K && this.field_70153_n != null) {
                    this.field_70153_n.func_70078_a(null);
                }
                this.setParent(null);
                MCH_Lib.DbgLog((World)this.field_70170_p, (String)"[Error]\u5ea7\u5e2d\u30a8\u30f3\u30c6\u30a3\u30c6\u30a3\u306f\u672c\u4f53\u304c\u898b\u3064\u304b\u3089\u306a\u3044\u305f\u3081\u524a\u9664 seat=%d, parentUniqueID=%s", (Object[])new Object[]{this.seatID, this.parentUniqueID});
            } else {
                ++this.parentSearchCount;
            }
        } else {
            this.parentSearchCount = 0;
        }
    }

    protected void func_70014_b(NBTTagCompound par1NBTTagCompound) {
        par1NBTTagCompound.func_74768_a("SeatID", this.seatID);
        par1NBTTagCompound.func_74778_a("ParentUniqueID", this.parentUniqueID);
    }

    protected void func_70037_a(NBTTagCompound par1NBTTagCompound) {
        this.seatID = par1NBTTagCompound.func_74762_e("SeatID");
        this.parentUniqueID = par1NBTTagCompound.func_74779_i("ParentUniqueID");
    }

    @SideOnly(value=Side.CLIENT)
    public float func_70053_R() {
        return 0.0f;
    }

    public boolean canRideMob(Entity entity) {
        if (this.getParent() == null || this.seatID < 0) {
            return false;
        }
        return !(this.getParent().getSeatInfo(this.seatID + 1) instanceof MCH_SeatRackInfo);
    }

    public boolean isGunnerMode() {
        if (this.field_70153_n != null && this.getParent() != null) {
            return this.getParent().getIsGunnerMode(this.field_70153_n);
        }
        return false;
    }

    public boolean func_130002_c(EntityPlayer player) {
        if (this.getParent() == null || this.getParent().isDestroyed()) {
            return false;
        }
        if (!this.getParent().checkTeam(player)) {
            return false;
        }
        ItemStack itemStack = player.func_71045_bC();
        if (itemStack != null && itemStack.func_77973_b() instanceof MCH_ItemWrench) {
            return this.getParent().func_130002_c(player);
        }
        if (this.field_70153_n != null) {
            return false;
        }
        if (player.field_70154_o != null) {
            return false;
        }
        if (!this.canRideMob((Entity)player)) {
            return false;
        }
        player.func_70078_a((Entity)this);
        return true;
    }

    public MCH_EntityAircraft getParent() {
        return this.parent;
    }

    public void setParent(MCH_EntityAircraft parent) {
        this.parent = parent;
    }
}

