/*
 * Decompiled with CFR 0.152.
 */
package mcheli.wrapper;

import mcheli.wrapper.W_MOD;
import net.minecraft.block.Block;
import net.minecraft.item.Item;

public class W_Item
extends Item {
    public W_Item(int par1) {
    }

    public W_Item() {
    }

    public static int getIdFromItem(Item i) {
        return i == null ? 0 : field_150901_e.func_148757_b((Object)i);
    }

    public Item setTexture(String par1Str) {
        this.func_111206_d(W_MOD.DOMAIN + ":" + par1Str);
        return this;
    }

    public static Item getItemById(int i) {
        return Item.func_150899_d((int)i);
    }

    public static Item getItemByName(String nm) {
        if (nm.indexOf(58) < 0) {
            nm = "minecraft:" + nm;
        }
        return (Item)Item.field_150901_e.func_82594_a(nm);
    }

    public static String getNameForItem(Item item) {
        return Item.field_150901_e.func_148750_c((Object)item);
    }

    public static Item getItemFromBlock(Block block) {
        return Item.func_150898_a((Block)block);
    }
}

