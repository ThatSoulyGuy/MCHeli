/*
 * Decompiled with CFR 0.152.
 */
package mcheli.wrapper;

import mcheli.wrapper.W_Blocks;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;

public abstract class W_Block
extends Block {
    protected W_Block(Material p_i45394_1_) {
        super(p_i45394_1_);
    }

    public static Block getBlockFromName(String name) {
        return Block.func_149684_b((String)name);
    }

    public static Block getSnowLayer() {
        return W_Blocks.field_150431_aC;
    }

    public static boolean isNull(Block block) {
        return block == null || block == W_Blocks.field_150350_a;
    }

    public static boolean isEqual(int blockId, Block block) {
        return Block.func_149680_a((Block)Block.func_149729_e((int)blockId), (Block)block);
    }

    public static boolean isEqual(Block block1, Block block2) {
        return Block.func_149680_a((Block)block1, (Block)block2);
    }

    public static Block getWater() {
        return W_Blocks.field_150355_j;
    }

    public static Block getBlockById(int i) {
        return Block.func_149729_e((int)i);
    }
}

