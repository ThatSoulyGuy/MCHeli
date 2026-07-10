/*
 * Decompiled with CFR 0.152.
 */
package mcheli.wrapper;

import cpw.mods.fml.common.network.IGuiHandler;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.relauncher.Side;
import mcheli.wrapper.W_Network;
import mcheli.wrapper.W_PacketBase;
import mcheli.wrapper.W_PacketHandler;
import net.minecraft.entity.player.EntityPlayer;

public class W_NetworkRegistry {
    public static W_PacketHandler packetHandler;

    public static void registerChannel(W_PacketHandler handler, String name) {
        packetHandler = handler;
        W_Network.INSTANCE.registerMessage(W_PacketHandler.class, W_PacketBase.class, 0, Side.SERVER);
        W_Network.INSTANCE.registerMessage(W_PacketHandler.class, W_PacketBase.class, 0, Side.CLIENT);
    }

    public static void handlePacket(EntityPlayer player, byte[] data) {
    }

    public static void registerGuiHandler(Object mod, IGuiHandler handler) {
        NetworkRegistry.INSTANCE.registerGuiHandler(mod, handler);
    }
}

