/*
 * Decompiled with CFR 0.152.
 */
package mcheli.particles;

import cpw.mods.fml.client.FMLClientHandler;
import mcheli.particles.MCH_EntityBlockDustFX;
import mcheli.particles.MCH_EntityParticleExplode;
import mcheli.particles.MCH_EntityParticleMarkPoint;
import mcheli.particles.MCH_EntityParticleSmoke;
import mcheli.particles.MCH_EntityParticleSplash;
import mcheli.particles.MCH_ParticleParam;
import mcheli.wrapper.W_Particle;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityAuraFX;
import net.minecraft.client.particle.EntityBreakingFX;
import net.minecraft.client.particle.EntityBubbleFX;
import net.minecraft.client.particle.EntityCloudFX;
import net.minecraft.client.particle.EntityCritFX;
import net.minecraft.client.particle.EntityDiggingFX;
import net.minecraft.client.particle.EntityDropParticleFX;
import net.minecraft.client.particle.EntityEnchantmentTableParticleFX;
import net.minecraft.client.particle.EntityExplodeFX;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.particle.EntityFireworkSparkFX;
import net.minecraft.client.particle.EntityFishWakeFX;
import net.minecraft.client.particle.EntityFlameFX;
import net.minecraft.client.particle.EntityFootStepFX;
import net.minecraft.client.particle.EntityHeartFX;
import net.minecraft.client.particle.EntityHugeExplodeFX;
import net.minecraft.client.particle.EntityLargeExplodeFX;
import net.minecraft.client.particle.EntityLavaFX;
import net.minecraft.client.particle.EntityNoteFX;
import net.minecraft.client.particle.EntityPortalFX;
import net.minecraft.client.particle.EntityReddustFX;
import net.minecraft.client.particle.EntitySmokeFX;
import net.minecraft.client.particle.EntitySnowShovelFX;
import net.minecraft.client.particle.EntitySpellParticleFX;
import net.minecraft.client.particle.EntitySplashFX;
import net.minecraft.client.particle.EntitySuspendFX;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.world.World;

/*
 * Exception performing whole class analysis ignored.
 */
public class MCH_ParticlesUtil {
    public static MCH_EntityParticleMarkPoint markPoint = null;

    public static void spawnParticleExplode(World w, double x, double y, double z, float size, float r, float g, float b, float a, int age) {
        MCH_EntityParticleExplode epe = new MCH_EntityParticleExplode(w, x, y, z, (double)size, (double)age, 0.0);
        epe.setParticleMaxAge(age);
        epe.func_70538_b(r, g, b);
        epe.func_82338_g(a);
        FMLClientHandler.instance().getClient().field_71452_i.func_78873_a((EntityFX)epe);
    }

    public static void spawnParticleTileCrack(World w, int blockX, int blockY, int blockZ, double x, double y, double z, double mx, double my, double mz) {
        String name = W_Particle.getParticleTileCrackName((World)w, (int)blockX, (int)blockY, (int)blockZ);
        if (!name.isEmpty()) {
            MCH_ParticlesUtil.DEF_spawnParticle((String)name, (double)x, (double)y, (double)z, (double)mx, (double)my, (double)mz, (float)20.0f);
        }
    }

    public static boolean spawnParticleTileDust(World w, int blockX, int blockY, int blockZ, double x, double y, double z, double mx, double my, double mz, float scale) {
        boolean ret = false;
        int[][] offset = new int[][]{{0, 0, 0}, {0, 0, -1}, {0, 0, 1}, {1, 0, 0}, {-1, 0, 0}};
        int len = offset.length;
        for (int i = 0; i < len; ++i) {
            EntityFX e;
            String name = W_Particle.getParticleTileDustName((World)w, (int)(blockX + offset[i][0]), (int)(blockY + offset[i][1]), (int)(blockZ + offset[i][2]));
            if (name.isEmpty() || !((e = MCH_ParticlesUtil.DEF_spawnParticle((String)name, (double)x, (double)y, (double)z, (double)mx, (double)my, (double)mz, (float)20.0f)) instanceof MCH_EntityBlockDustFX)) continue;
            ((MCH_EntityBlockDustFX)e).setScale(scale * 2.0f);
            ret = true;
            break;
        }
        return ret;
    }

    public static EntityFX DEF_spawnParticle(String s, double x, double y, double z, double mx, double my, double mz, float dist) {
        EntityFX e = MCH_ParticlesUtil.doSpawnParticle((String)s, (double)x, (double)y, (double)z, (double)mx, (double)my, (double)mz);
        if (e != null) {
            e.field_70155_l *= (double)dist;
        }
        return e;
    }

    public static EntityFX doSpawnParticle(String p_72726_1_, double p_72726_2_, double p_72726_4_, double p_72726_6_, double p_72726_8_, double p_72726_10_, double p_72726_12_) {
        Minecraft mc = Minecraft.func_71410_x();
        RenderGlobal renderGlobal = mc.field_71438_f;
        if (mc != null && mc.field_71451_h != null && mc.field_71452_i != null) {
            int i = mc.field_71474_y.field_74362_aa;
            if (i == 1 && mc.field_71441_e.field_73012_v.nextInt(3) == 0) {
                i = 2;
            }
            double d6 = mc.field_71451_h.field_70165_t - p_72726_2_;
            double d7 = mc.field_71451_h.field_70163_u - p_72726_4_;
            double d8 = mc.field_71451_h.field_70161_v - p_72726_6_;
            EntityHugeExplodeFX entityfx = null;
            if (p_72726_1_.equalsIgnoreCase("hugeexplosion")) {
                entityfx = new EntityHugeExplodeFX((World)mc.field_71441_e, p_72726_2_, p_72726_4_, p_72726_6_, p_72726_8_, p_72726_10_, p_72726_12_);
                mc.field_71452_i.func_78873_a((EntityFX)entityfx);
            } else if (p_72726_1_.equalsIgnoreCase("largeexplode")) {
                entityfx = new EntityLargeExplodeFX(mc.field_71446_o, (World)mc.field_71441_e, p_72726_2_, p_72726_4_, p_72726_6_, p_72726_8_, p_72726_10_, p_72726_12_);
                mc.field_71452_i.func_78873_a((EntityFX)entityfx);
            } else if (p_72726_1_.equalsIgnoreCase("fireworksSpark")) {
                entityfx = new EntityFireworkSparkFX((World)mc.field_71441_e, p_72726_2_, p_72726_4_, p_72726_6_, p_72726_8_, p_72726_10_, p_72726_12_, mc.field_71452_i);
                mc.field_71452_i.func_78873_a((EntityFX)entityfx);
            }
            if (entityfx != null) {
                return entityfx;
            }
            double d9 = 300.0;
            if (d6 * d6 + d7 * d7 + d8 * d8 > d9 * d9) {
                return null;
            }
            if (i > 1) {
                return null;
            }
            if (p_72726_1_.equalsIgnoreCase("bubble")) {
                entityfx = new EntityBubbleFX((World)mc.field_71441_e, p_72726_2_, p_72726_4_, p_72726_6_, p_72726_8_, p_72726_10_, p_72726_12_);
            } else if (p_72726_1_.equalsIgnoreCase("suspended")) {
                entityfx = new EntitySuspendFX((World)mc.field_71441_e, p_72726_2_, p_72726_4_, p_72726_6_, p_72726_8_, p_72726_10_, p_72726_12_);
            } else if (p_72726_1_.equalsIgnoreCase("depthsuspend")) {
                entityfx = new EntityAuraFX((World)mc.field_71441_e, p_72726_2_, p_72726_4_, p_72726_6_, p_72726_8_, p_72726_10_, p_72726_12_);
            } else if (p_72726_1_.equalsIgnoreCase("townaura")) {
                entityfx = new EntityAuraFX((World)mc.field_71441_e, p_72726_2_, p_72726_4_, p_72726_6_, p_72726_8_, p_72726_10_, p_72726_12_);
            } else if (p_72726_1_.equalsIgnoreCase("crit")) {
                entityfx = new EntityCritFX((World)mc.field_71441_e, p_72726_2_, p_72726_4_, p_72726_6_, p_72726_8_, p_72726_10_, p_72726_12_);
            } else if (p_72726_1_.equalsIgnoreCase("magicCrit")) {
                entityfx = new EntityCritFX((World)mc.field_71441_e, p_72726_2_, p_72726_4_, p_72726_6_, p_72726_8_, p_72726_10_, p_72726_12_);
                entityfx.func_70538_b(entityfx.func_70534_d() * 0.3f, entityfx.func_70542_f() * 0.8f, entityfx.func_70535_g());
                entityfx.func_94053_h();
            } else if (p_72726_1_.equalsIgnoreCase("smoke")) {
                entityfx = new EntitySmokeFX((World)mc.field_71441_e, p_72726_2_, p_72726_4_, p_72726_6_, p_72726_8_, p_72726_10_, p_72726_12_);
            } else if (p_72726_1_.equalsIgnoreCase("mobSpell")) {
                entityfx = new EntitySpellParticleFX((World)mc.field_71441_e, p_72726_2_, p_72726_4_, p_72726_6_, 0.0, 0.0, 0.0);
                entityfx.func_70538_b((float)p_72726_8_, (float)p_72726_10_, (float)p_72726_12_);
            } else if (p_72726_1_.equalsIgnoreCase("mobSpellAmbient")) {
                entityfx = new EntitySpellParticleFX((World)mc.field_71441_e, p_72726_2_, p_72726_4_, p_72726_6_, 0.0, 0.0, 0.0);
                entityfx.func_82338_g(0.15f);
                entityfx.func_70538_b((float)p_72726_8_, (float)p_72726_10_, (float)p_72726_12_);
            } else if (p_72726_1_.equalsIgnoreCase("spell")) {
                entityfx = new EntitySpellParticleFX((World)mc.field_71441_e, p_72726_2_, p_72726_4_, p_72726_6_, p_72726_8_, p_72726_10_, p_72726_12_);
            } else if (p_72726_1_.equalsIgnoreCase("instantSpell")) {
                entityfx = new EntitySpellParticleFX((World)mc.field_71441_e, p_72726_2_, p_72726_4_, p_72726_6_, p_72726_8_, p_72726_10_, p_72726_12_);
                ((EntitySpellParticleFX)entityfx).func_70589_b(144);
            } else if (p_72726_1_.equalsIgnoreCase("witchMagic")) {
                entityfx = new EntitySpellParticleFX((World)mc.field_71441_e, p_72726_2_, p_72726_4_, p_72726_6_, p_72726_8_, p_72726_10_, p_72726_12_);
                ((EntitySpellParticleFX)entityfx).func_70589_b(144);
                float f = mc.field_71441_e.field_73012_v.nextFloat() * 0.5f + 0.35f;
                entityfx.func_70538_b(1.0f * f, 0.0f * f, 1.0f * f);
            } else if (p_72726_1_.equalsIgnoreCase("note")) {
                entityfx = new EntityNoteFX((World)mc.field_71441_e, p_72726_2_, p_72726_4_, p_72726_6_, p_72726_8_, p_72726_10_, p_72726_12_);
            } else if (p_72726_1_.equalsIgnoreCase("portal")) {
                entityfx = new EntityPortalFX((World)mc.field_71441_e, p_72726_2_, p_72726_4_, p_72726_6_, p_72726_8_, p_72726_10_, p_72726_12_);
            } else if (p_72726_1_.equalsIgnoreCase("enchantmenttable")) {
                entityfx = new EntityEnchantmentTableParticleFX((World)mc.field_71441_e, p_72726_2_, p_72726_4_, p_72726_6_, p_72726_8_, p_72726_10_, p_72726_12_);
            } else if (p_72726_1_.equalsIgnoreCase("explode")) {
                entityfx = new EntityExplodeFX((World)mc.field_71441_e, p_72726_2_, p_72726_4_, p_72726_6_, p_72726_8_, p_72726_10_, p_72726_12_);
            } else if (p_72726_1_.equalsIgnoreCase("flame")) {
                entityfx = new EntityFlameFX((World)mc.field_71441_e, p_72726_2_, p_72726_4_, p_72726_6_, p_72726_8_, p_72726_10_, p_72726_12_);
            } else if (p_72726_1_.equalsIgnoreCase("lava")) {
                entityfx = new EntityLavaFX((World)mc.field_71441_e, p_72726_2_, p_72726_4_, p_72726_6_);
            } else if (p_72726_1_.equalsIgnoreCase("footstep")) {
                entityfx = new EntityFootStepFX(mc.field_71446_o, (World)mc.field_71441_e, p_72726_2_, p_72726_4_, p_72726_6_);
            } else if (p_72726_1_.equalsIgnoreCase("splash")) {
                entityfx = new EntitySplashFX((World)mc.field_71441_e, p_72726_2_, p_72726_4_, p_72726_6_, p_72726_8_, p_72726_10_, p_72726_12_);
            } else if (p_72726_1_.equalsIgnoreCase("wake")) {
                entityfx = new EntityFishWakeFX((World)mc.field_71441_e, p_72726_2_, p_72726_4_, p_72726_6_, p_72726_8_, p_72726_10_, p_72726_12_);
            } else if (p_72726_1_.equalsIgnoreCase("largesmoke")) {
                entityfx = new EntitySmokeFX((World)mc.field_71441_e, p_72726_2_, p_72726_4_, p_72726_6_, p_72726_8_, p_72726_10_, p_72726_12_, 2.5f);
            } else if (p_72726_1_.equalsIgnoreCase("cloud")) {
                entityfx = new EntityCloudFX((World)mc.field_71441_e, p_72726_2_, p_72726_4_, p_72726_6_, p_72726_8_, p_72726_10_, p_72726_12_);
            } else if (p_72726_1_.equalsIgnoreCase("reddust")) {
                entityfx = new EntityReddustFX((World)mc.field_71441_e, p_72726_2_, p_72726_4_, p_72726_6_, (float)p_72726_8_, (float)p_72726_10_, (float)p_72726_12_);
            } else if (p_72726_1_.equalsIgnoreCase("snowballpoof")) {
                entityfx = new EntityBreakingFX((World)mc.field_71441_e, p_72726_2_, p_72726_4_, p_72726_6_, Items.field_151126_ay);
            } else if (p_72726_1_.equalsIgnoreCase("dripWater")) {
                entityfx = new EntityDropParticleFX((World)mc.field_71441_e, p_72726_2_, p_72726_4_, p_72726_6_, Material.field_151586_h);
            } else if (p_72726_1_.equalsIgnoreCase("dripLava")) {
                entityfx = new EntityDropParticleFX((World)mc.field_71441_e, p_72726_2_, p_72726_4_, p_72726_6_, Material.field_151587_i);
            } else if (p_72726_1_.equalsIgnoreCase("snowshovel")) {
                entityfx = new EntitySnowShovelFX((World)mc.field_71441_e, p_72726_2_, p_72726_4_, p_72726_6_, p_72726_8_, p_72726_10_, p_72726_12_);
            } else if (p_72726_1_.equalsIgnoreCase("slime")) {
                entityfx = new EntityBreakingFX((World)mc.field_71441_e, p_72726_2_, p_72726_4_, p_72726_6_, Items.field_151123_aH);
            } else if (p_72726_1_.equalsIgnoreCase("heart")) {
                entityfx = new EntityHeartFX((World)mc.field_71441_e, p_72726_2_, p_72726_4_, p_72726_6_, p_72726_8_, p_72726_10_, p_72726_12_);
            } else if (p_72726_1_.equalsIgnoreCase("angryVillager")) {
                entityfx = new EntityHeartFX((World)mc.field_71441_e, p_72726_2_, p_72726_4_ + 0.5, p_72726_6_, p_72726_8_, p_72726_10_, p_72726_12_);
                entityfx.func_70536_a(81);
                entityfx.func_70538_b(1.0f, 1.0f, 1.0f);
            } else if (p_72726_1_.equalsIgnoreCase("happyVillager")) {
                entityfx = new EntityAuraFX((World)mc.field_71441_e, p_72726_2_, p_72726_4_, p_72726_6_, p_72726_8_, p_72726_10_, p_72726_12_);
                entityfx.func_70536_a(82);
                entityfx.func_70538_b(1.0f, 1.0f, 1.0f);
            } else if (p_72726_1_.startsWith("iconcrack_")) {
                String[] astring = p_72726_1_.split("_", 3);
                int j = Integer.parseInt(astring[1]);
                if (astring.length > 2) {
                    int k = Integer.parseInt(astring[2]);
                    entityfx = new EntityBreakingFX((World)mc.field_71441_e, p_72726_2_, p_72726_4_, p_72726_6_, p_72726_8_, p_72726_10_, p_72726_12_, Item.func_150899_d((int)j), k);
                } else {
                    entityfx = new EntityBreakingFX((World)mc.field_71441_e, p_72726_2_, p_72726_4_, p_72726_6_, p_72726_8_, p_72726_10_, p_72726_12_, Item.func_150899_d((int)j), 0);
                }
            } else if (p_72726_1_.startsWith("blockcrack_")) {
                String[] astring = p_72726_1_.split("_", 3);
                Block block = Block.func_149729_e((int)Integer.parseInt(astring[1]));
                int k = Integer.parseInt(astring[2]);
                entityfx = new EntityDiggingFX((World)mc.field_71441_e, p_72726_2_, p_72726_4_, p_72726_6_, p_72726_8_, p_72726_10_, p_72726_12_, block, k).func_90019_g(k);
            } else if (p_72726_1_.startsWith("blockdust_")) {
                String[] astring = p_72726_1_.split("_", 3);
                Block block = Block.func_149729_e((int)Integer.parseInt(astring[1]));
                int k = Integer.parseInt(astring[2]);
                entityfx = new MCH_EntityBlockDustFX((World)mc.field_71441_e, p_72726_2_, p_72726_4_, p_72726_6_, p_72726_8_, p_72726_10_, p_72726_12_, block, k).func_90019_g(k);
            }
            if (entityfx != null) {
                mc.field_71452_i.func_78873_a((EntityFX)entityfx);
            }
            return entityfx;
        }
        return null;
    }

    public static void spawnParticle(MCH_ParticleParam p) {
        if (p.world.field_72995_K) {
            Object entityFX = null;
            entityFX = p.name.equalsIgnoreCase("Splash") ? new MCH_EntityParticleSplash(p.world, p.posX, p.posY, p.posZ, p.motionX, p.motionY, p.motionZ) : new MCH_EntityParticleSmoke(p.world, p.posX, p.posY, p.posZ, p.motionX, p.motionY, p.motionZ);
            entityFX.func_70538_b(p.r, p.g, p.b);
            entityFX.func_82338_g(p.a);
            if (p.age > 0) {
                entityFX.setParticleMaxAge(p.age);
            }
            entityFX.moutionYUpAge = p.motionYUpAge;
            entityFX.gravity = p.gravity;
            entityFX.isEffectedWind = p.isEffectWind;
            entityFX.diffusible = p.diffusible;
            entityFX.toWhite = p.toWhite;
            if (p.diffusible) {
                entityFX.setParticleScale(p.size * 0.2f);
                entityFX.particleMaxScale = p.size * 2.0f;
            } else {
                entityFX.setParticleScale(p.size);
            }
            FMLClientHandler.instance().getClient().field_71452_i.func_78873_a((EntityFX)entityFX);
        }
    }

    public static void spawnMarkPoint(EntityPlayer player, double x, double y, double z) {
        MCH_ParticlesUtil.clearMarkPoint();
        markPoint = new MCH_EntityParticleMarkPoint(player.field_70170_p, x, y, z, player.func_96124_cp());
        FMLClientHandler.instance().getClient().field_71452_i.func_78873_a((EntityFX)markPoint);
    }

    public static void clearMarkPoint() {
        if (markPoint != null) {
            markPoint.func_70106_y();
            markPoint = null;
        }
    }
}

