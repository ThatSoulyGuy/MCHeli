/*
 * Decompiled with CFR 0.152.
 */
package mcheli.weapon;

import mcheli.MCH_Config;
import mcheli.weapon.MCH_BulletModel;
import mcheli.weapon.MCH_DefaultBulletModels;
import mcheli.weapon.MCH_DummyEntityPlayer;
import mcheli.weapon.MCH_EntityBaseBullet;
import mcheli.wrapper.W_Blocks;
import mcheli.wrapper.W_Item;
import mcheli.wrapper.W_WorldFunc;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;

public class MCH_EntityDispensedItem
extends MCH_EntityBaseBullet {
    public MCH_EntityDispensedItem(World par1World) {
        super(par1World);
    }

    public MCH_EntityDispensedItem(World par1World, double posX, double posY, double posZ, double targetX, double targetY, double targetZ, float yaw, float pitch, double acceleration) {
        super(par1World, posX, posY, posZ, targetX, targetY, targetZ, yaw, pitch, acceleration);
    }

    public void func_70071_h_() {
        super.func_70071_h_();
        if (this.getInfo() != null && !this.getInfo().disableSmoke) {
            this.spawnParticle(this.getInfo().trajectoryParticleName, 3, 7.0f * this.getInfo().smokeSize);
        }
        if (!this.field_70170_p.field_72995_K && this.getInfo() != null) {
            if (this.acceleration < 1.0E-4) {
                this.field_70159_w *= 0.999;
                this.field_70179_y *= 0.999;
            }
            if (this.func_70090_H()) {
                this.field_70159_w *= (double)this.getInfo().velocityInWater;
                this.field_70181_x *= (double)this.getInfo().velocityInWater;
                this.field_70179_y *= (double)this.getInfo().velocityInWater;
            }
        }
        this.onUpdateBomblet();
    }

    protected void onImpact(MovingObjectPosition m, float damageFactor) {
        if (!this.field_70170_p.field_72995_K) {
            this.field_70121_D.field_72337_e += 2000.0;
            this.field_70121_D.field_72338_b += 2000.0;
            EntityPlayer player = null;
            Item item = null;
            int itemDamage = 0;
            if (m != null && this.getInfo() != null) {
                if (this.shootingAircraft instanceof EntityPlayer) {
                    player = (EntityPlayer)this.shootingAircraft;
                }
                if (this.shootingEntity instanceof EntityPlayer) {
                    player = (EntityPlayer)this.shootingEntity;
                }
                item = this.getInfo().dispenseItem;
                itemDamage = this.getInfo().dispenseDamege;
            }
            if (player != null && !player.field_70128_L && item != null) {
                MCH_DummyEntityPlayer dummyPlayer = new MCH_DummyEntityPlayer(this.field_70170_p, player);
                dummyPlayer.field_70125_A = 90.0f;
                int RNG = this.getInfo().dispenseRange - 1;
                for (int x = -RNG; x <= RNG; ++x) {
                    for (int y = -RNG; y <= RNG; ++y) {
                        if (y < 0 || y >= 256) continue;
                        for (int z = -RNG; z <= RNG; ++z) {
                            int dist = x * x + y * y + z * z;
                            if (dist > RNG * RNG) continue;
                            if ((double)dist <= 0.5 * (double)RNG * (double)RNG) {
                                this.useItemToBlock(m.field_72311_b + x, m.field_72312_c + y, m.field_72309_d + z, item, itemDamage, (EntityPlayer)dummyPlayer);
                                continue;
                            }
                            if (this.field_70146_Z.nextInt(2) != 0) continue;
                            this.useItemToBlock(m.field_72311_b + x, m.field_72312_c + y, m.field_72309_d + z, item, itemDamage, (EntityPlayer)dummyPlayer);
                        }
                    }
                }
            }
            this.func_70106_y();
        }
    }

    private void useItemToBlock(int x, int y, int z, Item item, int itemDamage, EntityPlayer dummyPlayer) {
        dummyPlayer.field_70165_t = (double)x + 0.5;
        dummyPlayer.field_70163_u = (double)y + 2.5;
        dummyPlayer.field_70161_v = (double)z + 0.5;
        dummyPlayer.field_70177_z = this.field_70146_Z.nextInt(360);
        Block block = W_WorldFunc.getBlock((World)this.field_70170_p, (int)x, (int)y, (int)z);
        Material blockMat = W_WorldFunc.getBlockMaterial((World)this.field_70170_p, (int)x, (int)y, (int)z);
        if (block != W_Blocks.field_150350_a && blockMat != Material.field_151579_a) {
            if (item == W_Item.getItemByName((String)"water_bucket")) {
                if (MCH_Config.Collision_DestroyBlock.prmBool) {
                    if (blockMat == Material.field_151581_o) {
                        this.field_70170_p.func_147468_f(x, y, z);
                    } else if (blockMat == Material.field_151587_i) {
                        int metadata = this.field_70170_p.func_72805_g(x, y, z);
                        if (metadata == 0) {
                            W_WorldFunc.setBlock((World)this.field_70170_p, (int)x, (int)y, (int)z, (Block)W_Blocks.field_150343_Z);
                        } else if (metadata <= 4) {
                            W_WorldFunc.setBlock((World)this.field_70170_p, (int)x, (int)y, (int)z, (Block)W_Blocks.field_150347_e);
                        }
                    }
                }
            } else if (!item.onItemUseFirst(new ItemStack(item, 1, itemDamage), dummyPlayer, this.field_70170_p, x, y, z, 1, (float)x, (float)y, (float)z) && !item.func_77648_a(new ItemStack(item, 1, itemDamage), dummyPlayer, this.field_70170_p, x, y, z, 1, (float)x, (float)y, (float)z)) {
                item.func_77659_a(new ItemStack(item, 1, itemDamage), this.field_70170_p, dummyPlayer);
            }
        }
    }

    public void sprinkleBomblet() {
        if (!this.field_70170_p.field_72995_K) {
            MCH_EntityDispensedItem e = new MCH_EntityDispensedItem(this.field_70170_p, this.field_70165_t, this.field_70163_u, this.field_70161_v, this.field_70159_w, this.field_70181_x, this.field_70179_y, (float)this.field_70146_Z.nextInt(360), 0.0f, this.acceleration);
            e.setParameterFromWeapon((MCH_EntityBaseBullet)this, this.shootingAircraft, this.shootingEntity);
            e.setName(this.getName());
            float MOTION = 1.0f;
            float RANDOM = this.getInfo().bombletDiff;
            e.field_70159_w = this.field_70159_w * 1.0 + (double)((this.field_70146_Z.nextFloat() - 0.5f) * RANDOM);
            e.field_70181_x = this.field_70181_x * 1.0 / 2.0 + (double)((this.field_70146_Z.nextFloat() - 0.5f) * RANDOM / 2.0f);
            e.field_70179_y = this.field_70179_y * 1.0 + (double)((this.field_70146_Z.nextFloat() - 0.5f) * RANDOM);
            e.setBomblet();
            this.field_70170_p.func_72838_d((Entity)e);
        }
    }

    public MCH_BulletModel getDefaultBulletModel() {
        return MCH_DefaultBulletModels.Bomb;
    }
}

