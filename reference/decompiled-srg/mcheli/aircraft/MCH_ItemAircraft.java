/*
 * Decompiled with CFR 0.152.
 */
package mcheli.aircraft;

import java.util.List;
import mcheli.MCH_Achievement;
import mcheli.MCH_Config;
import mcheli.aircraft.MCH_AircraftInfo;
import mcheli.aircraft.MCH_EntityAircraft;
import mcheli.aircraft.MCH_ItemAircraftDispenseBehavior;
import mcheli.wrapper.W_EntityPlayer;
import mcheli.wrapper.W_Item;
import mcheli.wrapper.W_MovingObjectPosition;
import mcheli.wrapper.W_WorldFunc;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDispenser;
import net.minecraft.block.BlockSponge;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityMinecartEmpty;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.stats.Achievement;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public abstract class MCH_ItemAircraft
extends W_Item {
    private static boolean isRegistedDispenseBehavior = false;

    public MCH_ItemAircraft(int i) {
        super(i);
    }

    public static void registerDispenseBehavior(Item item) {
        if (isRegistedDispenseBehavior) {
            return;
        }
        BlockDispenser.field_149943_a.func_82595_a((Object)item, (Object)new MCH_ItemAircraftDispenseBehavior());
    }

    public abstract MCH_AircraftInfo getAircraftInfo();

    public abstract MCH_EntityAircraft createAircraft(World var1, double var2, double var4, double var6, ItemStack var8);

    public MCH_EntityAircraft onTileClick(ItemStack itemStack, World world, float rotationYaw, int x, int y, int z) {
        MCH_EntityAircraft ac = this.createAircraft(world, (double)((float)x + 0.5f), (double)((float)y + 1.0f), (double)((float)z + 0.5f), itemStack);
        if (ac == null) {
            return null;
        }
        ac.initRotationYaw((float)(((MathHelper.func_76128_c((double)((double)(rotationYaw * 4.0f / 360.0f) + 0.5)) & 3) - 1) * 90));
        if (!world.func_72945_a((Entity)ac, ac.field_70121_D.func_72314_b(-0.1, -0.1, -0.1)).isEmpty()) {
            return null;
        }
        return ac;
    }

    public String toString() {
        MCH_AircraftInfo info = this.getAircraftInfo();
        if (info != null) {
            return super.toString() + "(" + info.getDirectoryName() + ":" + info.name + ")";
        }
        return super.toString() + "(null)";
    }

    public ItemStack func_77659_a(ItemStack par1ItemStack, World world, EntityPlayer player) {
        float f8;
        float f6;
        double d3;
        float f5;
        float f = 1.0f;
        float f1 = player.field_70127_C + (player.field_70125_A - player.field_70127_C) * f;
        float f2 = player.field_70126_B + (player.field_70177_z - player.field_70126_B) * f;
        double d0 = player.field_70169_q + (player.field_70165_t - player.field_70169_q) * (double)f;
        double d1 = player.field_70167_r + (player.field_70163_u - player.field_70167_r) * (double)f + 1.62 - (double)player.field_70129_M;
        double d2 = player.field_70166_s + (player.field_70161_v - player.field_70166_s) * (double)f;
        Vec3 vec3 = W_WorldFunc.getWorldVec3((World)world, (double)d0, (double)d1, (double)d2);
        float f3 = MathHelper.func_76134_b((float)(-f2 * ((float)Math.PI / 180) - (float)Math.PI));
        float f4 = MathHelper.func_76126_a((float)(-f2 * ((float)Math.PI / 180) - (float)Math.PI));
        float f7 = f4 * (f5 = -MathHelper.func_76134_b((float)(-f1 * ((float)Math.PI / 180))));
        Vec3 vec31 = vec3.func_72441_c((double)f7 * (d3 = 5.0), (double)(f6 = MathHelper.func_76126_a((float)(-f1 * ((float)Math.PI / 180)))) * d3, (double)(f8 = f3 * f5) * d3);
        MovingObjectPosition mop = W_WorldFunc.clip((World)world, (Vec3)vec3, (Vec3)vec31, (boolean)true);
        if (mop == null) {
            return par1ItemStack;
        }
        Vec3 vec32 = player.func_70676_i(f);
        boolean flag = false;
        float f9 = 1.0f;
        List list = world.func_72839_b((Entity)player, player.field_70121_D.func_72321_a(vec32.field_72450_a * d3, vec32.field_72448_b * d3, vec32.field_72449_c * d3).func_72314_b((double)f9, (double)f9, (double)f9));
        for (int i = 0; i < list.size(); ++i) {
            float f10;
            AxisAlignedBB axisalignedbb;
            Entity entity = (Entity)list.get(i);
            if (!entity.func_70067_L() || !(axisalignedbb = entity.field_70121_D.func_72314_b((double)(f10 = entity.func_70111_Y()), (double)f10, (double)f10)).func_72318_a(vec3)) continue;
            flag = true;
        }
        if (flag) {
            return par1ItemStack;
        }
        if (W_MovingObjectPosition.isHitTypeTile((MovingObjectPosition)mop)) {
            Block block;
            if (MCH_Config.PlaceableOnSpongeOnly.prmBool && !((block = world.func_147439_a(mop.field_72311_b, mop.field_72312_c, mop.field_72309_d)) instanceof BlockSponge)) {
                return par1ItemStack;
            }
            this.spawnAircraft(par1ItemStack, world, player, mop.field_72311_b, mop.field_72312_c, mop.field_72309_d);
        }
        return par1ItemStack;
    }

    public MCH_EntityAircraft spawnAircraft(ItemStack itemStack, World world, EntityPlayer player, int x, int y, int z) {
        MCH_EntityAircraft ac = this.onTileClick(itemStack, world, player.field_70177_z, x, y, z);
        if (ac != null) {
            if (ac.isUAV()) {
                if (world.field_72995_K) {
                    if (ac.isSmallUAV()) {
                        W_EntityPlayer.addChatMessage((EntityPlayer)player, (String)"Please use the UAV station OR Portable Controller");
                    } else {
                        W_EntityPlayer.addChatMessage((EntityPlayer)player, (String)"Please use the UAV station");
                    }
                }
                ac = null;
            } else {
                if (!world.field_72995_K) {
                    ac.getAcDataFromItem(itemStack);
                    world.func_72838_d((Entity)ac);
                    MCH_Achievement.addStat((Entity)player, (Achievement)MCH_Achievement.welcome, (int)1);
                }
                if (!player.field_71075_bZ.field_75098_d) {
                    --itemStack.field_77994_a;
                }
            }
        }
        return ac;
    }

    public void rideEntity(ItemStack item, Entity target, EntityPlayer player) {
        if (!MCH_Config.PlaceableOnSpongeOnly.prmBool && target instanceof EntityMinecartEmpty && target.field_70153_n == null) {
            MCH_EntityAircraft ac = this.spawnAircraft(item, player.field_70170_p, player, (int)target.field_70165_t, (int)target.field_70163_u + 2, (int)target.field_70161_v);
            if (!player.field_70170_p.field_72995_K && ac != null) {
                ac.func_70078_a(target);
            }
        }
    }
}

