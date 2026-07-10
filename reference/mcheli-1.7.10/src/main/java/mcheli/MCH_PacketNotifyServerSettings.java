package mcheli;

import com.google.common.io.ByteArrayDataInput;
import java.io.DataOutputStream;
import java.io.IOException;
import mcheli.wrapper.W_Network;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

public class MCH_PacketNotifyServerSettings extends MCH_Packet {
   public boolean enableCamDistChange = true;
   public boolean enableEntityMarker = true;
   public boolean enablePVP = true;
   public double stingerLockRange = 120.0;
   public boolean enableDebugBoundingBox = true;

   @Override
   public int getMessageID() {
      return 268437568;
   }

   @Override
   public void readData(ByteArrayDataInput data) {
      try {
         byte b = data.readByte();
         this.enableCamDistChange = this.getBit(b, 0);
         this.enableEntityMarker = this.getBit(b, 1);
         this.enablePVP = this.getBit(b, 2);
         this.stingerLockRange = data.readFloat();
         this.enableDebugBoundingBox = this.getBit(b, 3);
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

   @Override
   public void writeData(DataOutputStream dos) {
      try {
         byte b = 0;
         b = this.setBit(b, 0, this.enableCamDistChange);
         b = this.setBit(b, 1, this.enableEntityMarker);
         b = this.setBit(b, 2, this.enablePVP);
         b = this.setBit(b, 3, this.enableDebugBoundingBox);
         dos.writeByte(b);
         dos.writeFloat((float)this.stingerLockRange);
      } catch (IOException e) {
         e.printStackTrace();
      }
   }

   public static void send(EntityPlayerMP player) {
      MCH_PacketNotifyServerSettings s = new MCH_PacketNotifyServerSettings();
      s.enableCamDistChange = !MCH_Config.DisableCameraDistChange.prmBool;
      s.enableEntityMarker = MCH_Config.DisplayEntityMarker.prmBool;
      s.enablePVP = MinecraftServer.getServer().isPVPEnabled();
      s.stingerLockRange = MCH_Config.StingerLockRange.prmDouble;
      s.enableDebugBoundingBox = MCH_Config.EnableDebugBoundingBox.prmBool;
      if (player != null) {
         W_Network.sendToPlayer(s, player);
      } else {
         W_Network.sendToAllPlayers(s);
      }
   }

   public static void sendAll() {
      send(null);
   }
}
