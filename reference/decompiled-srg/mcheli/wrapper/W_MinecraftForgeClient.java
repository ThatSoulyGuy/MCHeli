/*
 * Decompiled with CFR 0.152.
 */
package mcheli.wrapper;

import net.minecraft.item.Item;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.MinecraftForgeClient;

public class W_MinecraftForgeClient {
    public static void registerItemRenderer(Item item, IItemRenderer renderer) {
        if (item != null) {
            MinecraftForgeClient.registerItemRenderer((Item)item, (IItemRenderer)renderer);
        }
    }
}

