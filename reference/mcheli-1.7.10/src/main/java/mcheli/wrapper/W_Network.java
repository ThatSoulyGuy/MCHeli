package mcheli.wrapper;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.NetworkRegistry.TargetPoint;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

public class W_Network {
   public static final SimpleNetworkWrapper INSTANCE = NetworkRegistry.INSTANCE.newSimpleChannel("MCHeli_CH");

   public static void sendToServer(W_PacketBase pkt) {
      INSTANCE.sendToServer(pkt);
   }

   public static void sendToPlayer(W_PacketBase pkt, EntityPlayer player) {
      if (player instanceof EntityPlayerMP) {
         INSTANCE.sendTo(pkt, (EntityPlayerMP)player);
      }
   }

   public static void sendToAllAround(W_PacketBase pkt, Entity sender, double renge) {
      TargetPoint t = new TargetPoint(sender.dimension, sender.posX, sender.posY, sender.posZ, renge);
      INSTANCE.sendToAllAround(pkt, t);
   }

   public static void sendToAllPlayers(W_PacketBase pkt) {
      INSTANCE.sendToAll(pkt);
   }
}
