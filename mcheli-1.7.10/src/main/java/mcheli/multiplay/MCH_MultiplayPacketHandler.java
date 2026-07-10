package mcheli.multiplay;

import com.google.common.io.ByteArrayDataInput;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import mcheli.MCH_Lib;
import mcheli.MCH_PacketNotifyServerSettings;
import mcheli.aircraft.MCH_EntityAircraft;
import net.minecraft.command.ICommandManager;
import net.minecraft.command.server.CommandScoreboard;
import net.minecraft.command.server.CommandSummon;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MCH_MultiplayPacketHandler {
   private static final Logger logger = LogManager.getLogger();
   private static DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss");
   private static byte[] imageData = null;
   private static String lastPlayerName = "";
   private static double lastDataPercent = 0.0;
   public static EntityPlayer modListRequestPlayer = null;
   private static int playerInfoId = 0;

   public static void onPacket_Command(EntityPlayer player, ByteArrayDataInput data) {
      if (!player.worldObj.isRemote) {
         MinecraftServer minecraftServer = MinecraftServer.getServer();
         if (minecraftServer != null) {
            MCH_PacketIndMultiplayCommand pc = new MCH_PacketIndMultiplayCommand();
            pc.readData(data);
            MCH_Lib.DbgLog(false, "MCH_MultiplayPacketHandler.onPacket_Command cmd:%d:%s", pc.CmdID, pc.CmdStr);
            switch (pc.CmdID) {
               case 256:
                  MCH_Multiplay.shuffleTeam(player);
                  break;
               case 512:
                  MCH_Multiplay.jumpSpawnPoint(player);
                  break;
               case 768:
                  ICommandManager icommandmanager = minecraftServer.getCommandManager();
                  icommandmanager.executeCommand(player, pc.CmdStr);
                  break;
               case 1024:
                  if (new CommandScoreboard().canCommandSenderUseCommand(player)) {
                     minecraftServer.setAllowPvp(!minecraftServer.isPVPEnabled());
                     MCH_PacketNotifyServerSettings.send(null);
                  }
                  break;
               case 1280:
                  destoryAllAircraft(player);
                  break;
               default:
                  MCH_Lib.DbgLog(false, "MCH_MultiplayPacketHandler.onPacket_Command unknown cmd:%d:%s", pc.CmdID, pc.CmdStr);
            }
         }
      }
   }

   private static void destoryAllAircraft(EntityPlayer player) {
      CommandSummon cmd = new CommandSummon();
      if (cmd.canCommandSenderUseCommand(player)) {
         for (Object e : player.worldObj.loadedEntityList) {
            if (e instanceof MCH_EntityAircraft) {
               ((MCH_EntityAircraft)e).setDead();
            }
         }
      }
   }

   public static void onPacket_NotifySpotedEntity(EntityPlayer player, ByteArrayDataInput data) {
      if (player.worldObj.isRemote) {
         MCH_PacketNotifySpotedEntity pc = new MCH_PacketNotifySpotedEntity();
         pc.readData(data);
         if (pc.count > 0) {
            for (int i = 0; i < pc.num; i++) {
               MCH_GuiTargetMarker.addSpotedEntity(pc.entityId[i], pc.count);
            }
         }
      }
   }

   public static void onPacket_NotifyMarkPoint(EntityPlayer player, ByteArrayDataInput data) {
      if (player.worldObj.isRemote) {
         MCH_PacketNotifyMarkPoint pc = new MCH_PacketNotifyMarkPoint();
         pc.readData(data);
         MCH_GuiTargetMarker.markPoint(pc.px, pc.py, pc.pz);
      }
   }

   public static void onPacket_LargeData(EntityPlayer player, ByteArrayDataInput data) {
      if (!player.worldObj.isRemote) {
         try {
            MinecraftServer minecraftServer = MinecraftServer.getServer();
            if (minecraftServer == null) {
               return;
            }

            MCH_PacketLargeData pc = new MCH_PacketLargeData();
            pc.readData(data);
            if (pc.imageDataIndex < 0 || pc.imageDataTotalSize <= 0) {
               return;
            }

            if (pc.imageDataIndex == 0) {
               if (imageData != null && !lastPlayerName.isEmpty()) {
                  LogError("[mcheli]Err1:Saving the %s screen shot to server FAILED!!!", lastPlayerName);
               }

               imageData = new byte[pc.imageDataTotalSize];
               lastPlayerName = player.getDisplayName();
               lastDataPercent = 0.0;
            }

            double dataPercent = (double)(pc.imageDataIndex + pc.imageDataSize) / pc.imageDataTotalSize * 100.0;
            if (dataPercent - lastDataPercent >= 10.0 || lastDataPercent == 0.0) {
               LogInfo(
                  "[mcheli]Saving the %s screen shot to server. %.0f%% : %dbyte / %dbyte",
                  player.getDisplayName(),
                  dataPercent,
                  pc.imageDataIndex,
                  pc.imageDataTotalSize
               );
               lastDataPercent = dataPercent;
            }

            if (imageData == null) {
               if (imageData != null && !lastPlayerName.isEmpty()) {
                  LogError("[mcheli]Err2:Saving the %s screen shot to server FAILED!!!", player.getDisplayName());
               }

               imageData = null;
               lastPlayerName = "";
               lastDataPercent = 0.0;
               return;
            }

            for (int i = 0; i < pc.imageDataSize; i++) {
               imageData[pc.imageDataIndex + i] = pc.buf[i];
            }

            if (pc.imageDataIndex + pc.imageDataSize >= pc.imageDataTotalSize) {
               DataOutputStream dos = null;
               String dt = dateFormat.format(new Date()).toString();
               File file = new File("screenshots_op");
               file.mkdir();
               file = new File(file, player.getDisplayName() + "_" + dt + ".png");
               String s = file.getAbsolutePath();
               LogInfo("[mcheli]Save Screenshot has been completed: %s", s);
               FileOutputStream fos = new FileOutputStream(s);
               dos = new DataOutputStream(fos);
               dos.write(imageData);
               dos.flush();
               dos.close();
               imageData = null;
               lastPlayerName = "";
               lastDataPercent = 0.0;
            }
         } catch (Exception e) {
            e.printStackTrace();
         }
      }
   }

   public static void LogInfo(String format, Object... args) {
      logger.info(String.format(format, args));
   }

   public static void LogError(String format, Object... args) {
      logger.error(String.format(format, args));
   }

   public static void onPacket_IndClient(EntityPlayer player, ByteArrayDataInput data) {
      if (player.worldObj.isRemote) {
         MCH_PacketIndClient pc = new MCH_PacketIndClient();
         pc.readData(data);
         if (pc.CmdID == 1) {
            MCH_MultiplayClient.startSendImageData();
         } else if (pc.CmdID == 2) {
            MCH_MultiplayClient.sendModsInfo(player.getDisplayName(), Integer.parseInt(pc.CmdStr));
         }
      }
   }

   public static int getPlayerInfoId(EntityPlayer player) {
      modListRequestPlayer = player;
      playerInfoId++;
      if (playerInfoId > 1000000) {
         playerInfoId = 1;
      }

      return playerInfoId;
   }

   public static void onPacket_ModList(EntityPlayer player, ByteArrayDataInput data) {
      MCH_PacketModList pc = new MCH_PacketModList();
      pc.readData(data);
      MCH_Lib.DbgLog(player.worldObj, "MCH_MultiplayPacketHandler.onPacket_ModList : ID=%d, Num=%d", pc.id, pc.num);
      if (player.worldObj.isRemote) {
         if (pc.firstData) {
            MCH_Lib.Log(EnumChatFormatting.RED + "###### " + player.getDisplayName() + " ######");
            player.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "###### " + player.getDisplayName() + " ######"));
         }

         for (String s : pc.list) {
            MCH_Lib.Log(s);
            player.addChatMessage(new ChatComponentText(s));
         }
      } else if (pc.id == playerInfoId) {
         if (modListRequestPlayer != null) {
            MCH_PacketModList.send(modListRequestPlayer, pc);
         } else {
            if (pc.firstData) {
               LogInfo("###### " + player.getDisplayName() + " ######");
            }

            for (String s : pc.list) {
               LogInfo(s);
            }
         }
      }
   }
}
