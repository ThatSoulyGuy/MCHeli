/*
 * Decompiled with CFR 0.152.
 */
package mcheli.wrapper;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;
import mcheli.wrapper.W_TickHandler;

public class W_TickRegistry {
    public static void registerTickHandler(W_TickHandler handler, Side side) {
        FMLCommonHandler.instance().bus().register((Object)handler);
    }
}

