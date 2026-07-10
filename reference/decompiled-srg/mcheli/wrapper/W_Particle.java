/*
 * Decompiled with CFR 0.152.
 */
package mcheli.wrapper;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.world.World;

public class W_Particle {
    public static String getParticleTileCrackName(World w, int blockX, int blockY, int blockZ) {
        Block block = w.func_147439_a(blockX, blockY, blockZ);
        if (block.func_149688_o() != Material.field_151579_a) {
            return "blockcrack_" + Block.func_149682_b((Block)block) + "_" + w.func_72805_g(blockX, blockY, blockZ);
        }
        return "";
    }

    public static String getParticleTileDustName(World w, int blockX, int blockY, int blockZ) {
        Block block = w.func_147439_a(blockX, blockY, blockZ);
        if (block.func_149688_o() != Material.field_151579_a) {
            return "blockdust_" + Block.func_149682_b((Block)block) + "_" + w.func_72805_g(blockX, blockY, blockZ);
        }
        return "";
    }
}

