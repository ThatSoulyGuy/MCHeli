package mcheli.wrapper;

import com.google.common.io.ByteArrayDataInput;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import mcheli.MCH_Lib;
import net.minecraft.entity.player.EntityPlayer;

public class W_PacketHandler implements IPacketHandler, IMessageHandler<W_PacketBase, W_PacketDummy> {
   public void onPacket(ByteArrayDataInput data, EntityPlayer player) {
   }

   public W_PacketDummy onMessage(W_PacketBase message, MessageContext ctx) {
      try {
         if (message.data != null) {
            if (ctx.side.isClient()) {
               if (MCH_Lib.getClientPlayer() != null) {
                  W_NetworkRegistry.packetHandler.onPacket(message.data, (EntityPlayer)MCH_Lib.getClientPlayer());
               }
            } else {
               W_NetworkRegistry.packetHandler.onPacket(message.data, ctx.getServerHandler().playerEntity);
            }
         }
      } catch (Exception e) {
         e.printStackTrace();
      }

      return null;
   }
}
