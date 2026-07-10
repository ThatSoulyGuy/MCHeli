/*
 * Decompiled with CFR 0.152.
 */
package mcheli.wrapper;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import mcheli.wrapper.W_PacketBase;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

public class W_Network {
    public static final SimpleNetworkWrapper INSTANCE = NetworkRegistry.INSTANCE.newSimpleChannel("MCHeli_CH");

    public static void sendToServer(W_PacketBase pkt) {
        INSTANCE.sendToServer((IMessage)pkt);
    }

    public static void sendToPlayer(W_PacketBase pkt, EntityPlayer player) {
        if (player instanceof EntityPlayerMP) {
            INSTANCE.sendTo((IMessage)pkt, (EntityPlayerMP)player);
        }
    }

    public static void sendToAllAround(W_PacketBase pkt, Entity sender, double renge) {
        NetworkRegistry.TargetPoint t = new NetworkRegistry.TargetPoint(sender.field_71093_bK, sender.field_70165_t, sender.field_70163_u, sender.field_70161_v, renge);
        INSTANCE.sendToAllAround((IMessage)pkt, t);
    }

    public static void sendToAllPlayers(W_PacketBase pkt) {
        INSTANCE.sendToAll((IMessage)pkt);
    }
}

