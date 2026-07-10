/*
 * Decompiled with CFR 0.152.
 */
package mcheli.wrapper;

import mcheli.wrapper.W_Block;
import mcheli.wrapper.W_MOD;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/*
 * Exception performing whole class analysis ignored.
 */
public class W_WorldFunc {
    public static void DEF_playSoundEffect(World w, double x, double y, double z, String name, float volume, float pitch) {
        w.func_72908_a(x, y, z, name, volume, pitch);
    }

    public static void MOD_playSoundEffect(World w, double x, double y, double z, String name, float volume, float pitch) {
        W_WorldFunc.DEF_playSoundEffect((World)w, (double)x, (double)y, (double)z, (String)(W_MOD.DOMAIN + ":" + name), (float)volume, (float)pitch);
    }

    private static void playSoundAtEntity(Entity e, String name, float volume, float pitch) {
        e.field_70170_p.func_72956_a(e, name, volume, pitch);
    }

    public static void MOD_playSoundAtEntity(Entity e, String name, float volume, float pitch) {
        W_WorldFunc.playSoundAtEntity((Entity)e, (String)(W_MOD.DOMAIN + ":" + name), (float)volume, (float)pitch);
    }

    public static int getBlockId(World w, int x, int y, int z) {
        return Block.func_149682_b((Block)w.func_147439_a(x, y, z));
    }

    public static Block getBlock(World w, int x, int y, int z) {
        return w.func_147439_a(x, y, z);
    }

    public static Material getBlockMaterial(World w, int x, int y, int z) {
        return w.func_147439_a(x, y, z).func_149688_o();
    }

    public static boolean isBlockWater(World w, int x, int y, int z) {
        return W_WorldFunc.isEqualBlock((World)w, (int)x, (int)y, (int)z, (Block)W_Block.getWater());
    }

    public static boolean isEqualBlock(World w, int x, int y, int z, Block block) {
        return Block.func_149680_a((Block)w.func_147439_a(x, y, z), (Block)block);
    }

    public static MovingObjectPosition clip(World w, Vec3 par1Vec3, Vec3 par2Vec3) {
        return w.func_72933_a(par1Vec3, par2Vec3);
    }

    public static MovingObjectPosition clip(World w, Vec3 par1Vec3, Vec3 par2Vec3, boolean b) {
        return w.func_72901_a(par1Vec3, par2Vec3, b);
    }

    public static MovingObjectPosition clip(World w, Vec3 par1Vec3, Vec3 par2Vec3, boolean b1, boolean b2, boolean b3) {
        return w.func_147447_a(par1Vec3, par2Vec3, b1, b2, b3);
    }

    public static boolean setBlock(World w, int a, int b, int c, Block d) {
        return w.func_147449_b(a, b, c, d);
    }

    public static void setBlock(World w, int x, int y, int z, Block b, int i, int j) {
        w.func_147465_d(x, y, z, b, i, j);
    }

    public static boolean destroyBlock(World w, int x, int y, int z, boolean par4) {
        return w.func_147480_a(x, y, z, par4);
    }

    public static Vec3 getWorldVec3(World w, double x, double y, double z) {
        return Vec3.func_72443_a((double)x, (double)y, (double)z);
    }

    public static Vec3 getWorldVec3EntityPos(Entity e) {
        return W_WorldFunc.getWorldVec3((World)e.field_70170_p, (double)e.field_70165_t, (double)e.field_70163_u, (double)e.field_70161_v);
    }
}

