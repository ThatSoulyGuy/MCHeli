/*
 * Decompiled with CFR 0.152.
 */
package mcheli;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import mcheli.MCH_Config;
import mcheli.MCH_DamageFactor;
import mcheli.MCH_Explosion;
import mcheli.MCH_Lib;
import mcheli.MCH_PacketEffectExplosion;
import mcheli.flare.MCH_EntityFlare;
import mcheli.particles.MCH_ParticleParam;
import mcheli.particles.MCH_ParticlesUtil;
import mcheli.weapon.MCH_EntityBaseBullet;
import mcheli.wrapper.W_AxisAlignedBB;
import mcheli.wrapper.W_Block;
import mcheli.wrapper.W_Blocks;
import mcheli.wrapper.W_ChunkPosition;
import mcheli.wrapper.W_Entity;
import mcheli.wrapper.W_WorldFunc;
import net.minecraft.block.Block;
import net.minecraft.enchantment.EnchantmentProtection;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityExpBottle;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;

/*
 * Exception performing whole class analysis ignored.
 */
public class MCH_Explosion
extends Explosion {
    public final int field_77289_h = 16;
    public World world;
    private static Random explosionRNG = new Random();
    public Map field_77288_k = new HashMap();
    public boolean isDestroyBlock;
    public int countSetFireEntity;
    public boolean isPlaySound;
    public boolean isInWater;
    ExplosionResult result;
    public EntityPlayer explodedPlayer;
    public float explosionSizeBlock;
    public MCH_DamageFactor damageFactor = null;

    public MCH_Explosion(World par1World, Entity exploder, Entity player, double x, double y, double z, float size) {
        super(par1World, exploder, x, y, z, size);
        this.world = par1World;
        this.isDestroyBlock = false;
        this.explosionSizeBlock = size;
        this.countSetFireEntity = 0;
        this.isPlaySound = true;
        this.isInWater = false;
        this.result = null;
        this.explodedPlayer = player instanceof EntityPlayer ? (EntityPlayer)player : null;
    }

    public boolean isRemote() {
        return this.world.field_72995_K;
    }

    public void func_77278_a() {
        double d2;
        double d1;
        double d0;
        int k;
        int j;
        HashSet<ChunkPosition> hashset = new HashSet<ChunkPosition>();
        int i = 0;
        while (true) {
            this.getClass();
            if (i >= 16) break;
            j = 0;
            while (true) {
                this.getClass();
                if (j >= 16) break;
                k = 0;
                while (true) {
                    block19: {
                        block18: {
                            this.getClass();
                            if (k >= 16) break;
                            if (i == 0) break block18;
                            this.getClass();
                            if (i == 16 - 1 || j == 0) break block18;
                            this.getClass();
                            if (j == 16 - 1 || k == 0) break block18;
                            this.getClass();
                            if (k != 16 - 1) break block19;
                        }
                        float f = i;
                        this.getClass();
                        double d3 = f / (16.0f - 1.0f) * 2.0f - 1.0f;
                        float f2 = j;
                        this.getClass();
                        double d4 = f2 / (16.0f - 1.0f) * 2.0f - 1.0f;
                        float f3 = k;
                        this.getClass();
                        double d5 = f3 / (16.0f - 1.0f) * 2.0f - 1.0f;
                        double d6 = Math.sqrt(d3 * d3 + d4 * d4 + d5 * d5);
                        d3 /= d6;
                        d4 /= d6;
                        d5 /= d6;
                        d0 = this.field_77284_b;
                        d1 = this.field_77285_c;
                        d2 = this.field_77282_d;
                        float f22 = 0.3f;
                        for (float f1 = this.explosionSizeBlock * (0.7f + this.world.field_73012_v.nextFloat() * 0.6f); f1 > 0.0f; f1 -= 0.22500001f) {
                            int j1;
                            int i1;
                            int l = MathHelper.func_76128_c((double)d0);
                            int k1 = W_WorldFunc.getBlockId((World)this.world, (int)l, (int)(i1 = MathHelper.func_76128_c((double)d1)), (int)(j1 = MathHelper.func_76128_c((double)d2)));
                            if (k1 > 0) {
                                Block block = W_WorldFunc.getBlock((World)this.world, (int)l, (int)i1, (int)j1);
                                float f32 = this.field_77283_e != null ? W_Entity.getBlockExplosionResistance((Entity)this.field_77283_e, (Explosion)this, (World)this.world, (int)l, (int)i1, (int)j1, (Block)block) : block.getExplosionResistance(this.field_77283_e, this.world, l, i1, j1, this.field_77284_b, this.field_77285_c, this.field_77282_d);
                                if (this.isInWater) {
                                    f32 *= this.world.field_73012_v.nextFloat() * 0.2f + 0.2f;
                                }
                                f1 -= (f32 + 0.3f) * 0.3f;
                            }
                            if (f1 > 0.0f && (this.field_77283_e == null || W_Entity.shouldExplodeBlock((Entity)this.field_77283_e, (Explosion)this, (World)this.world, (int)l, (int)i1, (int)j1, (int)k1, (float)f1))) {
                                hashset.add(new ChunkPosition(l, i1, j1));
                            }
                            d0 += d3 * (double)0.3f;
                            d1 += d4 * (double)0.3f;
                            d2 += d5 * (double)0.3f;
                        }
                    }
                    ++k;
                }
                ++j;
            }
            ++i;
        }
        float f = this.field_77280_f;
        this.field_77281_g.addAll(hashset);
        this.field_77280_f *= 2.0f;
        i = MathHelper.func_76128_c((double)(this.field_77284_b - (double)this.field_77280_f - 1.0));
        j = MathHelper.func_76128_c((double)(this.field_77284_b + (double)this.field_77280_f + 1.0));
        k = MathHelper.func_76128_c((double)(this.field_77285_c - (double)this.field_77280_f - 1.0));
        int l1 = MathHelper.func_76128_c((double)(this.field_77285_c + (double)this.field_77280_f + 1.0));
        int i2 = MathHelper.func_76128_c((double)(this.field_77282_d - (double)this.field_77280_f - 1.0));
        int j2 = MathHelper.func_76128_c((double)(this.field_77282_d + (double)this.field_77280_f + 1.0));
        List list = this.world.func_72839_b(this.field_77283_e, W_AxisAlignedBB.getAABB((double)i, (double)k, (double)i2, (double)j, (double)l1, (double)j2));
        Vec3 vec3 = W_WorldFunc.getWorldVec3((World)this.world, (double)this.field_77284_b, (double)this.field_77285_c, (double)this.field_77282_d);
        this.field_77283_e = this.explodedPlayer;
        for (int k2 = 0; k2 < list.size(); ++k2) {
            double fireFactor;
            double d8;
            Entity entity = (Entity)list.get(k2);
            double d7 = entity.func_70011_f(this.field_77284_b, this.field_77285_c, this.field_77282_d) / (double)this.field_77280_f;
            if (!(d7 <= 1.0) || (d8 = (double)MathHelper.func_76133_a((double)((d0 = entity.field_70165_t - this.field_77284_b) * d0 + (d1 = entity.field_70163_u + (double)entity.func_70047_e() - this.field_77285_c) * d1 + (d2 = entity.field_70161_v - this.field_77282_d) * d2))) == 0.0) continue;
            d0 /= d8;
            d1 /= d8;
            d2 /= d8;
            double d9 = this.getBlockDensity(vec3, entity.field_70121_D);
            double d10 = (1.0 - d7) * d9;
            float damage = (int)((d10 * d10 + d10) / 2.0 * 8.0 * (double)this.field_77280_f + 1.0);
            if (!(!(damage > 0.0f) || this.result == null || entity instanceof EntityItem || entity instanceof EntityExpBottle || entity instanceof EntityXPOrb || W_Entity.isEntityFallingBlock((Entity)entity))) {
                if (entity instanceof MCH_EntityBaseBullet && this.field_77283_e instanceof EntityPlayer) {
                    if (!W_Entity.isEqual((Entity)((MCH_EntityBaseBullet)entity).shootingEntity, (Entity)this.field_77283_e)) {
                        this.result.hitEntity = true;
                        MCH_Lib.DbgLog((World)this.world, (String)("MCH_Explosion.doExplosionA:Damage=%.1f:HitEntityBullet=" + entity.getClass()), (Object[])new Object[]{Float.valueOf(damage)});
                    }
                } else {
                    MCH_Lib.DbgLog((World)this.world, (String)("MCH_Explosion.doExplosionA:Damage=%.1f:HitEntity=" + entity.getClass()), (Object[])new Object[]{Float.valueOf(damage)});
                    this.result.hitEntity = true;
                }
            }
            MCH_Lib.applyEntityHurtResistantTimeConfig((Entity)entity);
            DamageSource ds = DamageSource.func_94539_a((Explosion)this);
            damage = MCH_Config.applyDamageVsEntity((Entity)entity, (DamageSource)ds, (float)damage);
            W_Entity.attackEntityFrom((Entity)entity, (DamageSource)ds, (float)(damage *= this.damageFactor != null ? this.damageFactor.getDamageFactor(entity) : 1.0f));
            double d11 = EnchantmentProtection.func_92092_a((Entity)entity, (double)d10);
            if (!(entity instanceof MCH_EntityBaseBullet)) {
                entity.field_70159_w += d0 * d11 * 0.4;
                entity.field_70181_x += d1 * d11 * 0.1;
                entity.field_70179_y += d2 * d11 * 0.4;
            }
            if (entity instanceof EntityPlayer) {
                this.field_77288_k.put((EntityPlayer)entity, W_WorldFunc.getWorldVec3((World)this.world, (double)(d0 * d10), (double)(d1 * d10), (double)(d2 * d10)));
            }
            if (!(damage > 0.0f) || this.countSetFireEntity <= 0 || !((fireFactor = 1.0 - d8 / (double)this.field_77280_f) > 0.0)) continue;
            entity.func_70015_d((int)(fireFactor * (double)this.countSetFireEntity));
        }
        this.field_77280_f = f;
    }

    private double getBlockDensity(Vec3 vec3, AxisAlignedBB p_72842_2_) {
        double d0 = 1.0 / ((p_72842_2_.field_72336_d - p_72842_2_.field_72340_a) * 2.0 + 1.0);
        double d1 = 1.0 / ((p_72842_2_.field_72337_e - p_72842_2_.field_72338_b) * 2.0 + 1.0);
        double d2 = 1.0 / ((p_72842_2_.field_72334_f - p_72842_2_.field_72339_c) * 2.0 + 1.0);
        if (d0 >= 0.0 && d1 >= 0.0 && d2 >= 0.0) {
            int i = 0;
            int j = 0;
            float f = 0.0f;
            while (f <= 1.0f) {
                float f1 = 0.0f;
                while (f1 <= 1.0f) {
                    float f2 = 0.0f;
                    while (f2 <= 1.0f) {
                        double d3 = p_72842_2_.field_72340_a + (p_72842_2_.field_72336_d - p_72842_2_.field_72340_a) * (double)f;
                        double d4 = p_72842_2_.field_72338_b + (p_72842_2_.field_72337_e - p_72842_2_.field_72338_b) * (double)f1;
                        double d5 = p_72842_2_.field_72339_c + (p_72842_2_.field_72334_f - p_72842_2_.field_72339_c) * (double)f2;
                        if (this.world.func_147447_a(Vec3.func_72443_a((double)d3, (double)d4, (double)d5), vec3, false, true, false) == null) {
                            ++i;
                        }
                        ++j;
                        f2 = (float)((double)f2 + d2);
                    }
                    f1 = (float)((double)f1 + d1);
                }
                f = (float)((double)f + d0);
            }
            return (float)i / (float)j;
        }
        return 0.0;
    }

    public void func_77279_a(boolean par1) {
        int l;
        int k;
        int j;
        int i;
        if (this.isPlaySound) {
            W_WorldFunc.DEF_playSoundEffect((World)this.world, (double)this.field_77284_b, (double)this.field_77285_c, (double)this.field_77282_d, (String)"random.explode", (float)4.0f, (float)((1.0f + (this.world.field_73012_v.nextFloat() - this.world.field_73012_v.nextFloat()) * 0.2f) * 0.7f));
        }
        if (this.field_82755_b) {
            for (ChunkPosition chunkposition : this.field_77281_g) {
                i = W_ChunkPosition.getChunkPosX((ChunkPosition)chunkposition);
                l = W_WorldFunc.getBlockId((World)this.world, (int)i, (int)(j = W_ChunkPosition.getChunkPosY((ChunkPosition)chunkposition)), (int)(k = W_ChunkPosition.getChunkPosZ((ChunkPosition)chunkposition)));
                if (l <= 0 || !this.isDestroyBlock || !(this.explosionSizeBlock > 0.0f)) continue;
                if (!MCH_Config.Explosion_DestroyBlock.prmBool) continue;
                Block block = W_Block.getBlockById((int)l);
                if (block.func_149659_a((Explosion)this)) {
                    block.func_149690_a(this.world, i, j, k, this.world.func_72805_g(i, j, k), 1.0f / this.explosionSizeBlock, 0);
                }
                block.onBlockExploded(this.world, i, j, k, (Explosion)this);
            }
        }
        if (this.field_77286_a) {
            if (MCH_Config.Explosion_FlamingBlock.prmBool) {
                for (ChunkPosition chunkposition : this.field_77281_g) {
                    i = W_ChunkPosition.getChunkPosX((ChunkPosition)chunkposition);
                    j = W_ChunkPosition.getChunkPosY((ChunkPosition)chunkposition);
                    k = W_ChunkPosition.getChunkPosZ((ChunkPosition)chunkposition);
                    l = W_WorldFunc.getBlockId((World)this.world, (int)i, (int)j, (int)k);
                    Block b = W_WorldFunc.getBlock((World)this.world, (int)i, (int)(j - 1), (int)k);
                    if (l != 0 || b == null || !b.func_149662_c()) continue;
                    if (explosionRNG.nextInt(3) != 0) continue;
                    W_WorldFunc.setBlock((World)this.world, (int)i, (int)j, (int)k, (Block)W_Blocks.field_150480_ab);
                }
            }
        }
    }

    public ExplosionResult newExplosionResult() {
        return new ExplosionResult(this);
    }

    public static ExplosionResult newExplosion(World w, Entity entityExploded, Entity player, double x, double y, double z, float size, float sizeBlock, boolean playSound, boolean isSmoking, boolean isFlaming, boolean isDestroyBlock, int countSetFireEntity) {
        return MCH_Explosion.newExplosion((World)w, (Entity)entityExploded, (Entity)player, (double)x, (double)y, (double)z, (float)size, (float)sizeBlock, (boolean)playSound, (boolean)isSmoking, (boolean)isFlaming, (boolean)isDestroyBlock, (int)countSetFireEntity, null);
    }

    public static ExplosionResult newExplosion(World w, Entity entityExploded, Entity player, double x, double y, double z, float size, float sizeBlock, boolean playSound, boolean isSmoking, boolean isFlaming, boolean isDestroyBlock, int countSetFireEntity, MCH_DamageFactor df) {
        if (w.field_72995_K) {
            return null;
        }
        MCH_Explosion exp = new MCH_Explosion(w, entityExploded, player, x, y, z, size);
        exp.field_82755_b = w.func_82736_K().func_82766_b("mobGriefing");
        exp.field_77286_a = isFlaming;
        exp.isDestroyBlock = isDestroyBlock;
        exp.explosionSizeBlock = sizeBlock;
        exp.countSetFireEntity = countSetFireEntity;
        exp.isPlaySound = playSound;
        exp.isInWater = false;
        exp.result = exp.newExplosionResult();
        exp.damageFactor = df;
        exp.func_77278_a();
        exp.func_77279_a(true);
        MCH_PacketEffectExplosion.ExplosionParam param = MCH_PacketEffectExplosion.create();
        param.exploderID = W_Entity.getEntityId((Entity)entityExploded);
        param.posX = x;
        param.posY = y;
        param.posZ = z;
        param.size = size;
        param.inWater = false;
        MCH_PacketEffectExplosion.send((MCH_PacketEffectExplosion.ExplosionParam)param);
        return exp.result;
    }

    public static ExplosionResult newExplosionInWater(World w, Entity entityExploded, Entity player, double x, double y, double z, float size, float sizeBlock, boolean playSound, boolean isSmoking, boolean isFlaming, boolean isDestroyBlock, int countSetFireEntity, MCH_DamageFactor df) {
        if (w.field_72995_K) {
            return null;
        }
        MCH_Explosion exp = new MCH_Explosion(w, entityExploded, player, x, y, z, size);
        exp.field_82755_b = w.func_82736_K().func_82766_b("mobGriefing");
        exp.field_77286_a = isFlaming;
        exp.isDestroyBlock = isDestroyBlock;
        exp.explosionSizeBlock = sizeBlock;
        exp.countSetFireEntity = countSetFireEntity;
        exp.isPlaySound = playSound;
        exp.isInWater = true;
        exp.result = exp.newExplosionResult();
        exp.damageFactor = df;
        exp.func_77278_a();
        exp.func_77279_a(true);
        MCH_PacketEffectExplosion.ExplosionParam param = MCH_PacketEffectExplosion.create();
        param.exploderID = W_Entity.getEntityId((Entity)entityExploded);
        param.posX = x;
        param.posY = y;
        param.posZ = z;
        param.size = size;
        param.inWater = true;
        MCH_PacketEffectExplosion.send((MCH_PacketEffectExplosion.ExplosionParam)param);
        return exp.result;
    }

    public static void playExplosionSound(World w, double x, double y, double z) {
        Random rand = new Random();
        W_WorldFunc.DEF_playSoundEffect((World)w, (double)x, (double)y, (double)z, (String)"random.explode", (float)4.0f, (float)((1.0f + (rand.nextFloat() - rand.nextFloat()) * 0.2f) * 0.7f));
    }

    public static void effectExplosion(World world, Entity exploder, double explosionX, double explosionY, double explosionZ, float explosionSize, boolean isSmoking) {
        double d2;
        double d1;
        double d0;
        int k;
        int j;
        int i;
        ArrayList affectedBlockPositions = new ArrayList();
        int field_77289_h = 16;
        float f = explosionSize;
        HashSet<ChunkPosition> hashset = new HashSet<ChunkPosition>();
        for (i = 0; i < 16; ++i) {
            for (j = 0; j < 16; ++j) {
                for (k = 0; k < 16; ++k) {
                    if (i != 0 && i != 15 && j != 0 && j != 15 && k != 0 && k != 15) continue;
                    double d3 = (float)i / 15.0f * 2.0f - 1.0f;
                    double d4 = (float)j / 15.0f * 2.0f - 1.0f;
                    double d5 = (float)k / 15.0f * 2.0f - 1.0f;
                    double d6 = Math.sqrt(d3 * d3 + d4 * d4 + d5 * d5);
                    d3 /= d6;
                    d4 /= d6;
                    d5 /= d6;
                    d0 = explosionX;
                    d1 = explosionY;
                    d2 = explosionZ;
                    float f2 = 0.3f;
                    for (float f1 = explosionSize * (0.7f + world.field_73012_v.nextFloat() * 0.6f); f1 > 0.0f; f1 -= f2 * 0.75f) {
                        int j1;
                        int i1;
                        int l = MathHelper.func_76128_c((double)d0);
                        int k1 = W_WorldFunc.getBlockId((World)world, (int)l, (int)(i1 = MathHelper.func_76128_c((double)d1)), (int)(j1 = MathHelper.func_76128_c((double)d2)));
                        if (k1 > 0) {
                            Block block = W_Block.getBlockById((int)k1);
                            float f3 = block.getExplosionResistance(exploder, world, l, i1, j1, explosionX, explosionY, explosionZ);
                            f1 -= (f3 + 0.3f) * f2;
                        }
                        if (f1 > 0.0f) {
                            hashset.add(new ChunkPosition(l, i1, j1));
                        }
                        d0 += d3 * (double)f2;
                        d1 += d4 * (double)f2;
                        d2 += d5 * (double)f2;
                    }
                }
            }
        }
        affectedBlockPositions.addAll(hashset);
        if (explosionSize >= 2.0f && isSmoking) {
            MCH_ParticlesUtil.DEF_spawnParticle((String)"hugeexplosion", (double)explosionX, (double)explosionY, (double)explosionZ, (double)1.0, (double)0.0, (double)0.0, (float)10.0f);
        } else {
            MCH_ParticlesUtil.DEF_spawnParticle((String)"largeexplode", (double)explosionX, (double)explosionY, (double)explosionZ, (double)1.0, (double)0.0, (double)0.0, (float)10.0f);
        }
        if (isSmoking) {
            Iterator iterator = affectedBlockPositions.iterator();
            int cnt = 0;
            int flareCnt = (int)explosionSize;
            while (iterator.hasNext()) {
                ChunkPosition chunkposition = (ChunkPosition)iterator.next();
                i = W_ChunkPosition.getChunkPosX((ChunkPosition)chunkposition);
                j = W_ChunkPosition.getChunkPosY((ChunkPosition)chunkposition);
                k = W_ChunkPosition.getChunkPosZ((ChunkPosition)chunkposition);
                int l = W_WorldFunc.getBlockId((World)world, (int)i, (int)j, (int)k);
                ++cnt;
                d0 = (float)i + world.field_73012_v.nextFloat();
                d1 = (float)j + world.field_73012_v.nextFloat();
                d2 = (float)k + world.field_73012_v.nextFloat();
                double mx = d0 - explosionX;
                double my = d1 - explosionY;
                double mz = d2 - explosionZ;
                double d6 = MathHelper.func_76133_a((double)(mx * mx + my * my + mz * mz));
                mx /= d6;
                my /= d6;
                mz /= d6;
                double d7 = 0.5 / (d6 / (double)explosionSize + 0.1);
                mx *= (d7 *= (double)(world.field_73012_v.nextFloat() * world.field_73012_v.nextFloat() + 0.3f)) * 0.5;
                my *= d7 * 0.5;
                mz *= d7 * 0.5;
                double px = (d0 + explosionX * 1.0) / 2.0;
                double py = (d1 + explosionY * 1.0) / 2.0;
                double pz = (d2 + explosionZ * 1.0) / 2.0;
                double r = Math.PI * (double)world.field_73012_v.nextInt(360) / 180.0;
                if (explosionSize >= 4.0f && flareCnt > 0) {
                    double a = Math.min((double)(explosionSize / 12.0f), 0.6) * (double)(0.5f + world.field_73012_v.nextFloat() * 0.5f);
                    world.func_72838_d((Entity)new MCH_EntityFlare(world, px, py + 2.0, pz, Math.sin(r) * a, (1.0 + my / 5.0) * a, Math.cos(r) * a, 2.0f, 0));
                    --flareCnt;
                }
                if (cnt % 4 == 0) {
                    float bdf = Math.min(explosionSize / 3.0f, 2.0f) * (0.5f + world.field_73012_v.nextFloat() * 0.5f);
                    boolean ret = MCH_ParticlesUtil.spawnParticleTileDust((World)world, (int)((int)(px + 0.5)), (int)((int)(py - 0.5)), (int)((int)(pz + 0.5)), (double)px, (double)(py + 1.0), (double)pz, (double)(Math.sin(r) * (double)bdf), (double)(0.5 + my / 5.0 * (double)bdf), (double)(Math.cos(r) * (double)bdf), (float)(Math.min(explosionSize / 2.0f, 3.0f) * (0.5f + world.field_73012_v.nextFloat() * 0.5f)));
                }
                int es = (int)(explosionSize >= 4.0f ? explosionSize : 4.0f);
                if (!(explosionSize <= 1.0f) && cnt % es != 0) continue;
                if (world.field_73012_v.nextBoolean()) {
                    my *= 3.0;
                    mx *= 0.1;
                    mz *= 0.1;
                } else {
                    my *= 0.2;
                    mx *= 3.0;
                    mz *= 3.0;
                }
                MCH_ParticleParam prm = new MCH_ParticleParam(world, "explode", px, py, pz, mx, my, mz, explosionSize < 8.0f ? (explosionSize < 2.0f ? 2.0f : explosionSize * 2.0f) : 16.0f);
                prm.g = prm.b = 0.3f + world.field_73012_v.nextFloat() * 0.4f;
                prm.r = prm.b;
                prm.r += 0.1f;
                prm.g += 0.05f;
                prm.b += 0.0f;
                prm.age = 10 + world.field_73012_v.nextInt(30);
                prm.age = (int)((float)prm.age * (explosionSize < 6.0f ? explosionSize : 6.0f));
                prm.age = prm.age * 2 / 3;
                prm.diffusible = true;
                MCH_ParticlesUtil.spawnParticle((MCH_ParticleParam)prm);
            }
        }
    }

    public static void DEF_effectExplosion(World world, Entity exploder, double explosionX, double explosionY, double explosionZ, float explosionSize, boolean isSmoking) {
        double d2;
        double d1;
        double d0;
        int k;
        int j;
        int i;
        ArrayList affectedBlockPositions = new ArrayList();
        int field_77289_h = 16;
        float f = explosionSize;
        HashSet<ChunkPosition> hashset = new HashSet<ChunkPosition>();
        for (i = 0; i < 16; ++i) {
            for (j = 0; j < 16; ++j) {
                for (k = 0; k < 16; ++k) {
                    if (i != 0 && i != 15 && j != 0 && j != 15 && k != 0 && k != 15) continue;
                    double d3 = (float)i / 15.0f * 2.0f - 1.0f;
                    double d4 = (float)j / 15.0f * 2.0f - 1.0f;
                    double d5 = (float)k / 15.0f * 2.0f - 1.0f;
                    double d6 = Math.sqrt(d3 * d3 + d4 * d4 + d5 * d5);
                    d3 /= d6;
                    d4 /= d6;
                    d5 /= d6;
                    d0 = explosionX;
                    d1 = explosionY;
                    d2 = explosionZ;
                    float f2 = 0.3f;
                    for (float f1 = explosionSize * (0.7f + world.field_73012_v.nextFloat() * 0.6f); f1 > 0.0f; f1 -= f2 * 0.75f) {
                        int j1;
                        int i1;
                        int l = MathHelper.func_76128_c((double)d0);
                        int k1 = W_WorldFunc.getBlockId((World)world, (int)l, (int)(i1 = MathHelper.func_76128_c((double)d1)), (int)(j1 = MathHelper.func_76128_c((double)d2)));
                        if (k1 > 0) {
                            Block block = W_Block.getBlockById((int)k1);
                            float f3 = block.getExplosionResistance(exploder, world, l, i1, j1, explosionX, explosionY, explosionZ);
                            f1 -= (f3 + 0.3f) * f2;
                        }
                        if (f1 > 0.0f) {
                            hashset.add(new ChunkPosition(l, i1, j1));
                        }
                        d0 += d3 * (double)f2;
                        d1 += d4 * (double)f2;
                        d2 += d5 * (double)f2;
                    }
                }
            }
        }
        affectedBlockPositions.addAll(hashset);
        if (explosionSize >= 2.0f && isSmoking) {
            MCH_ParticlesUtil.DEF_spawnParticle((String)"hugeexplosion", (double)explosionX, (double)explosionY, (double)explosionZ, (double)1.0, (double)0.0, (double)0.0, (float)10.0f);
        } else {
            MCH_ParticlesUtil.DEF_spawnParticle((String)"largeexplode", (double)explosionX, (double)explosionY, (double)explosionZ, (double)1.0, (double)0.0, (double)0.0, (float)10.0f);
        }
        if (isSmoking) {
            for (ChunkPosition chunkposition : affectedBlockPositions) {
                i = W_ChunkPosition.getChunkPosX((ChunkPosition)chunkposition);
                j = W_ChunkPosition.getChunkPosY((ChunkPosition)chunkposition);
                k = W_ChunkPosition.getChunkPosZ((ChunkPosition)chunkposition);
                int l = W_WorldFunc.getBlockId((World)world, (int)i, (int)j, (int)k);
                d0 = (float)i + world.field_73012_v.nextFloat();
                d1 = (float)j + world.field_73012_v.nextFloat();
                d2 = (float)k + world.field_73012_v.nextFloat();
                double d3 = d0 - explosionX;
                double d4 = d1 - explosionY;
                double d5 = d2 - explosionZ;
                double d6 = MathHelper.func_76133_a((double)(d3 * d3 + d4 * d4 + d5 * d5));
                d3 /= d6;
                d4 /= d6;
                d5 /= d6;
                double d7 = 0.5 / (d6 / (double)explosionSize + 0.1);
                MCH_ParticlesUtil.DEF_spawnParticle((String)"explode", (double)((d0 + explosionX * 1.0) / 2.0), (double)((d1 + explosionY * 1.0) / 2.0), (double)((d2 + explosionZ * 1.0) / 2.0), (double)(d3 *= (d7 *= (double)(world.field_73012_v.nextFloat() * world.field_73012_v.nextFloat() + 0.3f))), (double)(d4 *= d7), (double)(d5 *= d7), (float)10.0f);
                MCH_ParticlesUtil.DEF_spawnParticle((String)"smoke", (double)d0, (double)d1, (double)d2, (double)d3, (double)d4, (double)d5, (float)10.0f);
            }
        }
    }

    public static void effectExplosionInWater(World world, Entity exploder, double explosionX, double explosionY, double explosionZ, float explosionSize, boolean isSmoking) {
        if (explosionSize <= 0.0f) {
            return;
        }
        int range = (int)((double)explosionSize + 0.5) / 1;
        int ex = (int)(explosionX + 0.5);
        int ey = (int)(explosionY + 0.5);
        int ez = (int)(explosionZ + 0.5);
        for (int y = -range; y <= range; ++y) {
            if (ey + y < 1) continue;
            for (int x = -range; x <= range; ++x) {
                for (int z = -range; z <= range; ++z) {
                    int d = x * x + y * y + z * z;
                    if (d >= range * range || !W_Block.func_149680_a((Block)W_WorldFunc.getBlock((World)world, (int)(ex + x), (int)(ey + y), (int)(ez + z)), (Block)W_Block.getWater())) continue;
                    int n = explosionRNG.nextInt(2);
                    for (int i = 0; i < n; ++i) {
                        MCH_ParticleParam prm = new MCH_ParticleParam(world, "splash", (double)(ex + x), (double)(ey + y), (double)(ez + z), (double)x / (double)range * ((double)explosionRNG.nextFloat() - 0.2), 1.0 - Math.sqrt(x * x + z * z) / (double)range + (double)explosionRNG.nextFloat() * 0.4 * (double)range * 0.4, (double)z / (double)range * ((double)explosionRNG.nextFloat() - 0.2), (float)(explosionRNG.nextInt(range) * 3 + range));
                        MCH_ParticlesUtil.spawnParticle((MCH_ParticleParam)prm);
                    }
                }
            }
        }
    }
}

