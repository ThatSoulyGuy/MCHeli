/*
 * Decompiled with CFR 0.152.
 */
package mcheli.tool;

import com.google.common.collect.Multimap;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.List;
import java.util.Random;
import mcheli.MCH_MOD;
import mcheli.aircraft.MCH_EntityAircraft;
import mcheli.aircraft.MCH_EntitySeat;
import mcheli.wrapper.W_Item;
import mcheli.wrapper.W_WorldFunc;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.material.MaterialLogic;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/*
 * Exception performing whole class analysis ignored.
 */
public class MCH_ItemWrench
extends W_Item {
    private float damageVsEntity;
    private final Item.ToolMaterial toolMaterial;
    private static Random rand = new Random();

    public MCH_ItemWrench(int itemId, Item.ToolMaterial material) {
        super(itemId);
        this.toolMaterial = material;
        this.field_77777_bU = 1;
        this.func_77656_e(material.func_77997_a());
        this.damageVsEntity = 4.0f + material.func_78000_c();
    }

    public boolean func_150897_b(Block b) {
        Material material = b.func_149688_o();
        if (material == Material.field_151573_f) {
            return true;
        }
        return material instanceof MaterialLogic;
    }

    public float func_150893_a(ItemStack itemStack, Block block) {
        Material material = block.func_149688_o();
        if (material == Material.field_151573_f) {
            return 20.5f;
        }
        if (material instanceof MaterialLogic) {
            return 5.5f;
        }
        return 2.0f;
    }

    public static int getUseAnimCount(ItemStack stack) {
        return MCH_ItemWrench.getAnimCount((ItemStack)stack, (String)"MCH_WrenchAnim");
    }

    public static void setUseAnimCount(ItemStack stack, int n) {
        MCH_ItemWrench.setAnimCount((ItemStack)stack, (String)"MCH_WrenchAnim", (int)n);
    }

    public static int getAnimCount(ItemStack stack, String name) {
        if (!stack.func_77942_o()) {
            stack.field_77990_d = new NBTTagCompound();
        }
        if (stack.field_77990_d.func_74764_b(name)) {
            return stack.field_77990_d.func_74762_e(name);
        }
        stack.field_77990_d.func_74768_a(name, 0);
        return 0;
    }

    public static void setAnimCount(ItemStack stack, String name, int n) {
        if (!stack.func_77942_o()) {
            stack.field_77990_d = new NBTTagCompound();
        }
        stack.field_77990_d.func_74768_a(name, n);
    }

    public boolean func_77644_a(ItemStack itemStack, EntityLivingBase entity, EntityLivingBase player) {
        if (!player.field_70170_p.field_72995_K) {
            if (rand.nextInt(40) == 0) {
                entity.func_70099_a(new ItemStack(W_Item.getItemByName((String)"iron_ingot"), 1, 0), 0.0f);
            } else if (rand.nextInt(20) == 0) {
                entity.func_70099_a(new ItemStack(W_Item.getItemByName((String)"gunpowder"), 1, 0), 0.0f);
            }
        }
        itemStack.func_77972_a(2, player);
        return true;
    }

    public void func_77615_a(ItemStack stack, World world, EntityPlayer player, int count) {
        MCH_ItemWrench.setUseAnimCount((ItemStack)stack, (int)0);
    }

    public void onUsingTick(ItemStack stack, EntityPlayer player, int count) {
        MCH_EntityAircraft ac;
        if (player.field_70170_p.field_72995_K && (ac = this.getMouseOverAircraft(player)) != null) {
            int cnt = MCH_ItemWrench.getUseAnimCount((ItemStack)stack);
            cnt = cnt <= 0 ? 16 : --cnt;
            MCH_ItemWrench.setUseAnimCount((ItemStack)stack, (int)cnt);
        }
        if (!player.field_70170_p.field_72995_K && count < this.func_77626_a(stack) && count % 20 == 0 && (ac = this.getMouseOverAircraft(player)) != null && ac.getHP() > 0 && ac.repair(10)) {
            stack.func_77972_a(1, (EntityLivingBase)player);
            W_WorldFunc.MOD_playSoundEffect((World)player.field_70170_p, (double)((int)ac.field_70165_t), (double)((int)ac.field_70163_u), (double)((int)ac.field_70161_v), (String)"wrench", (float)1.0f, (float)(0.9f + rand.nextFloat() * 0.2f));
        }
    }

    public void func_77663_a(ItemStack item, World world, Entity entity, int n, boolean b) {
        EntityPlayer player;
        ItemStack itemStack;
        if (entity instanceof EntityPlayer && (itemStack = (player = (EntityPlayer)entity).func_71045_bC()) == item) {
            MCH_MOD.proxy.setCreativeDigDelay(0);
        }
    }

    public MCH_EntityAircraft getMouseOverAircraft(EntityPlayer player) {
        MovingObjectPosition m = this.getMouseOver((EntityLivingBase)player, 1.0f);
        MCH_EntityAircraft ac = null;
        if (m != null) {
            MCH_EntitySeat seat;
            if (m.field_72308_g instanceof MCH_EntityAircraft) {
                ac = (MCH_EntityAircraft)m.field_72308_g;
            } else if (m.field_72308_g instanceof MCH_EntitySeat && (seat = (MCH_EntitySeat)m.field_72308_g).getParent() != null) {
                ac = seat.getParent();
            }
        }
        return ac;
    }

    private static MovingObjectPosition rayTrace(EntityLivingBase entity, double dist, float tick) {
        Vec3 vec3 = Vec3.func_72443_a((double)entity.field_70165_t, (double)(entity.field_70163_u + (double)entity.func_70047_e()), (double)entity.field_70161_v);
        Vec3 vec31 = entity.func_70676_i(tick);
        Vec3 vec32 = vec3.func_72441_c(vec31.field_72450_a * dist, vec31.field_72448_b * dist, vec31.field_72449_c * dist);
        return entity.field_70170_p.func_147447_a(vec3, vec32, false, false, true);
    }

    private MovingObjectPosition getMouseOver(EntityLivingBase user, float tick) {
        Entity pointedEntity = null;
        double d0 = 4.0;
        MovingObjectPosition objectMouseOver = MCH_ItemWrench.rayTrace((EntityLivingBase)user, (double)d0, (float)tick);
        double d1 = d0;
        Vec3 vec3 = Vec3.func_72443_a((double)user.field_70165_t, (double)(user.field_70163_u + (double)user.func_70047_e()), (double)user.field_70161_v);
        if (objectMouseOver != null) {
            d1 = objectMouseOver.field_72307_f.func_72438_d(vec3);
        }
        Vec3 vec31 = user.func_70676_i(tick);
        Vec3 vec32 = vec3.func_72441_c(vec31.field_72450_a * d0, vec31.field_72448_b * d0, vec31.field_72449_c * d0);
        pointedEntity = null;
        Vec3 vec33 = null;
        float f1 = 1.0f;
        List list = user.field_70170_p.func_72839_b((Entity)user, user.field_70121_D.func_72321_a(vec31.field_72450_a * d0, vec31.field_72448_b * d0, vec31.field_72449_c * d0).func_72314_b((double)f1, (double)f1, (double)f1));
        double d2 = d1;
        for (int i = 0; i < list.size(); ++i) {
            double d3;
            Entity entity = (Entity)list.get(i);
            if (!entity.func_70067_L()) continue;
            float f2 = entity.func_70111_Y();
            AxisAlignedBB axisalignedbb = entity.field_70121_D.func_72314_b((double)f2, (double)f2, (double)f2);
            MovingObjectPosition movingobjectposition = axisalignedbb.func_72327_a(vec3, vec32);
            if (axisalignedbb.func_72318_a(vec3)) {
                if (!(0.0 < d2) && d2 != 0.0) continue;
                pointedEntity = entity;
                vec33 = movingobjectposition == null ? vec3 : movingobjectposition.field_72307_f;
                d2 = 0.0;
                continue;
            }
            if (movingobjectposition == null || !((d3 = vec3.func_72438_d(movingobjectposition.field_72307_f)) < d2) && d2 != 0.0) continue;
            if (entity == user.field_70154_o && !entity.canRiderInteract()) {
                if (d2 != 0.0) continue;
                pointedEntity = entity;
                vec33 = movingobjectposition.field_72307_f;
                continue;
            }
            pointedEntity = entity;
            vec33 = movingobjectposition.field_72307_f;
            d2 = d3;
        }
        if (pointedEntity != null && (d2 < d1 || objectMouseOver == null)) {
            objectMouseOver = new MovingObjectPosition(pointedEntity, vec33);
        }
        return objectMouseOver;
    }

    public boolean func_150894_a(ItemStack itemStack, World world, Block block, int x, int y, int z, EntityLivingBase entity) {
        if ((double)block.func_149712_f(world, x, y, z) != 0.0) {
            itemStack.func_77972_a(2, entity);
        }
        return true;
    }

    @SideOnly(value=Side.CLIENT)
    public boolean func_77662_d() {
        return true;
    }

    public EnumAction func_77661_b(ItemStack itemStack) {
        return EnumAction.block;
    }

    public int func_77626_a(ItemStack itemStack) {
        return 72000;
    }

    public ItemStack func_77659_a(ItemStack itemStack, World world, EntityPlayer player) {
        player.func_71008_a(itemStack, this.func_77626_a(itemStack));
        return itemStack;
    }

    public int func_77619_b() {
        return this.toolMaterial.func_77995_e();
    }

    public String getToolMaterialName() {
        return this.toolMaterial.toString();
    }

    public boolean func_82789_a(ItemStack item1, ItemStack item2) {
        return this.toolMaterial.func_150995_f() == item2.func_77973_b() ? true : super.func_82789_a(item1, item2);
    }

    public Multimap func_111205_h() {
        Multimap multimap = super.func_111205_h();
        multimap.put((Object)SharedMonsterAttributes.field_111264_e.func_111108_a(), (Object)new AttributeModifier(field_111210_e, "Weapon modifier", (double)this.damageVsEntity, 0));
        return multimap;
    }
}

