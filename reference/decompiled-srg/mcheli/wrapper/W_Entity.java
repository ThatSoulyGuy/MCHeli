/*
 * Decompiled with CFR 0.152.
 */
package mcheli.wrapper;

import mcheli.wrapper.W_Block;
import net.minecraft.block.Block;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;

/*
 * Exception performing whole class analysis ignored.
 */
public abstract class W_Entity
extends Entity {
    public W_Entity(World par1World) {
        super(par1World);
    }

    protected void func_70088_a() {
    }

    public static boolean isEntityFallingBlock(Entity entity) {
        return entity instanceof EntityFallingBlock;
    }

    public static int getEntityId(Entity entity) {
        return entity != null ? entity.func_145782_y() : -1;
    }

    public static boolean isEqual(Entity e1, Entity e2) {
        int i2;
        int i1 = W_Entity.getEntityId((Entity)e1);
        return i1 == (i2 = W_Entity.getEntityId((Entity)e2));
    }

    public EntityItem dropItemWithOffset(Item item, int par2, float par3) {
        return this.func_70099_a(new ItemStack(item, par2, 0), par3);
    }

    public String getEntityName() {
        return super.func_70022_Q();
    }

    public boolean func_130002_c(EntityPlayer par1EntityPlayer) {
        return this.interact(par1EntityPlayer);
    }

    public boolean interact(EntityPlayer par1EntityPlayer) {
        return false;
    }

    public boolean attackEntityFrom(DamageSource par1DamageSource, int par2) {
        return this.func_70097_a(par1DamageSource, (float)par2);
    }

    public boolean func_70097_a(DamageSource par1DamageSource, float par2) {
        return false;
    }

    public static boolean attackEntityFrom(Entity entity, DamageSource ds, float par2) {
        return entity.func_70097_a(ds, par2);
    }

    public void func_85029_a(CrashReportCategory par1CrashReportCategory) {
        super.func_85029_a(par1CrashReportCategory);
    }

    public static float getBlockExplosionResistance(Entity entity, Explosion par1Explosion, World par2World, int par3, int par4, int par5, Block par6Block) {
        if (par6Block != null) {
            try {
                return entity.func_145772_a(par1Explosion, par2World, par3, par4, par5, par6Block);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        return 0.0f;
    }

    public static boolean shouldExplodeBlock(Entity entity, Explosion par1Explosion, World par2World, int par3, int par4, int par5, int par6, float par7) {
        return entity.func_145774_a(par1Explosion, par2World, par3, par4, par5, W_Block.getBlockById((int)par6), par7);
    }

    public static PotionEffect getActivePotionEffect(Entity entity, Potion par1Potion) {
        return entity instanceof EntityLivingBase ? ((EntityLivingBase)entity).func_70660_b(par1Potion) : null;
    }

    public static void removePotionEffectClient(Entity entity, int id) {
        if (entity instanceof EntityLivingBase) {
            ((EntityLivingBase)entity).func_70618_n(id);
        }
    }

    public static void removePotionEffect(Entity entity, int id) {
        if (entity instanceof EntityLivingBase) {
            ((EntityLivingBase)entity).func_82170_o(id);
        }
    }

    public static void addPotionEffect(Entity entity, PotionEffect pe) {
        if (entity instanceof EntityLivingBase) {
            ((EntityLivingBase)entity).func_70690_d(pe);
        }
    }

    protected void doBlockCollisions() {
        super.func_145775_I();
    }
}

