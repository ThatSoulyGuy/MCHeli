package mcheli.multiplay;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import mcheli.MCH_Lib;
import mcheli.aircraft.MCH_EntityAircraft;
import mcheli.helicopter.MCH_EntityHeli;
import mcheli.plane.MCP_EntityPlane;
import mcheli.tank.MCH_EntityTank;
import mcheli.vehicle.MCH_EntityVehicle;
import net.minecraft.command.server.CommandScoreboard;
import net.minecraft.command.server.CommandTeleport;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

public class MCH_Multiplay {
   public static final MCH_TargetType[][] ENTITY_SPOT_TABLE = new MCH_TargetType[][]{
      {MCH_TargetType.NONE, MCH_TargetType.NONE},
      {MCH_TargetType.OTHER_MOB, MCH_TargetType.OTHER_MOB},
      {MCH_TargetType.MONSTER, MCH_TargetType.MONSTER},
      {MCH_TargetType.NONE, MCH_TargetType.NO_TEAM_PLAYER},
      {MCH_TargetType.NONE, MCH_TargetType.SAME_TEAM_PLAYER},
      {MCH_TargetType.NONE, MCH_TargetType.OTHER_TEAM_PLAYER},
      {MCH_TargetType.NONE, MCH_TargetType.NONE},
      {MCH_TargetType.NONE, MCH_TargetType.NO_TEAM_PLAYER},
      {MCH_TargetType.NONE, MCH_TargetType.SAME_TEAM_PLAYER},
      {MCH_TargetType.NONE, MCH_TargetType.OTHER_TEAM_PLAYER}
   };

   public static boolean canSpotEntityWithFilter(int filter, Entity entity) {
      if (entity instanceof MCP_EntityPlane) {
         return (filter & 32) != 0;
      } else if (entity instanceof MCH_EntityHeli) {
         return (filter & 16) != 0;
      } else if (entity instanceof MCH_EntityVehicle || entity instanceof MCH_EntityTank) {
         return (filter & 8) != 0;
      } else if (entity instanceof EntityPlayer) {
         return (filter & 4) != 0;
      } else if (!(entity instanceof EntityLivingBase)) {
         return false;
      } else {
         return isMonster(entity) ? (filter & 2) != 0 : (filter & 1) != 0;
      }
   }

   public static boolean isMonster(Entity entity) {
      return entity.getClass().toString().toLowerCase().indexOf("monster") >= 0;
   }

   public static MCH_TargetType canSpotEntity(Entity user, double posX, double posY, double posZ, Entity target, boolean checkSee) {
      if (!(user instanceof EntityLivingBase)) {
         return MCH_TargetType.NONE;
      }

      EntityLivingBase spotter = (EntityLivingBase)user;
      int col = spotter.getTeam() == null ? 0 : 1;
      int row = 0;
      if (target instanceof EntityLivingBase) {
         if (!isMonster(target)) {
            row = 1;
         } else {
            row = 2;
         }
      }

      if (spotter.getTeam() != null) {
         if (target instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer)target;
            if (player.getTeam() == null) {
               row = 3;
            } else if (spotter.isOnSameTeam(player)) {
               row = 4;
            } else {
               row = 5;
            }
         } else if (target instanceof MCH_EntityAircraft) {
            MCH_EntityAircraft ac = (MCH_EntityAircraft)target;
            EntityPlayer rideEntity = ac.getFirstMountPlayer();
            if (rideEntity == null) {
               row = 6;
            } else if (rideEntity.getTeam() == null) {
               row = 7;
            } else if (spotter.isOnSameTeam(rideEntity)) {
               row = 8;
            } else {
               row = 9;
            }
         }
      } else if (target instanceof EntityPlayer || target instanceof MCH_EntityAircraft) {
         row = 0;
      }

      MCH_TargetType ret = ENTITY_SPOT_TABLE[row][col];
      if (checkSee && ret != MCH_TargetType.NONE) {
         Vec3 vs = Vec3.createVectorHelper(posX, posY, posZ);
         Vec3 ve = Vec3.createVectorHelper(target.posX, target.posY + target.getEyeHeight(), target.posZ);
         MovingObjectPosition mop = target.worldObj.rayTraceBlocks(vs, ve);
         if (mop != null && mop.typeOfHit == MovingObjectType.BLOCK) {
            ret = MCH_TargetType.NONE;
         }
      }

      return ret;
   }

   public static boolean canAttackEntity(DamageSource ds, Entity target) {
      return canAttackEntity(ds.getEntity(), target);
   }

   public static boolean canAttackEntity(Entity attacker, Entity target) {
      if (attacker != null && target != null) {
         EntityPlayer attackPlayer = null;
         EntityPlayer targetPlayer = null;
         if (attacker instanceof EntityPlayer) {
            attackPlayer = (EntityPlayer)attacker;
         }

         if (target instanceof EntityPlayer) {
            targetPlayer = (EntityPlayer)target;
         } else if (target.riddenByEntity instanceof EntityPlayer) {
            targetPlayer = (EntityPlayer)target.riddenByEntity;
         }

         if (target instanceof MCH_EntityAircraft) {
            MCH_EntityAircraft ac = (MCH_EntityAircraft)target;
            if (ac.getRiddenByEntity() instanceof EntityPlayer) {
               targetPlayer = (EntityPlayer)ac.getRiddenByEntity();
            }
         }

         if (attackPlayer != null && targetPlayer != null && !attackPlayer.canAttackPlayer(targetPlayer)) {
            return false;
         }
      }

      return true;
   }

   public static void jumpSpawnPoint(EntityPlayer player) {
      MCH_Lib.DbgLog(false, "JumpSpawnPoint");
      CommandTeleport cmd = new CommandTeleport();
      if (cmd.canCommandSenderUseCommand(player)) {
         MinecraftServer minecraftServer = MinecraftServer.getServer();

         for (String playerName : minecraftServer.getConfigurationManager().getAllUsernames()) {
            EntityPlayerMP jumpPlayer = CommandTeleport.getPlayer(player, playerName);
            ChunkCoordinates cc = null;
            if (jumpPlayer != null && jumpPlayer.dimension == player.dimension) {
               cc = jumpPlayer.getBedLocation(jumpPlayer.dimension);
               if (cc != null) {
                  cc = EntityPlayer.verifyRespawnCoordinates(minecraftServer.worldServerForDimension(jumpPlayer.dimension), cc, true);
               }

               if (cc == null) {
                  cc = jumpPlayer.worldObj.provider.getRandomizedSpawnPoint();
               }
            }

            if (cc != null) {
               String[] cmdStr = new String[]{
                  playerName,
                  String.format("%.1f", cc.posX + 0.5),
                  String.format("%.1f", cc.posY + 0.1),
                  String.format("%.1f", cc.posZ + 0.5)
               };
               cmd.processCommand(player, cmdStr);
            }
         }
      }
   }

   public static void shuffleTeam(EntityPlayer player) {
      Collection teams = player.worldObj.getScoreboard().getTeams();
      int teamNum = teams.size();
      MCH_Lib.DbgLog(false, "ShuffleTeam:%d teams ----------", teamNum);
      if (teamNum > 0) {
         CommandScoreboard cmd = new CommandScoreboard();
         if (cmd.canCommandSenderUseCommand(player)) {
            List<String> list = Arrays.asList(MinecraftServer.getServer().getConfigurationManager().getAllUsernames());
            Collections.shuffle(list);
            ArrayList<String> listTeam = new ArrayList<>();

            for (Object o : teams) {
               ScorePlayerTeam team = (ScorePlayerTeam)o;
               listTeam.add(team.getRegisteredName());
            }

            Collections.shuffle(listTeam);
            int i = 0;
            int j = 0;

            while (i < list.size()) {
               listTeam.set(j, listTeam.get(j) + " " + list.get(i));
               if (++j >= teamNum) {
                  j = 0;
               }

               i++;
            }

            for (int jx = 0; jx < listTeam.size(); jx++) {
               String exe_cmd = "teams join " + listTeam.get(jx);
               String[] process_cmd = exe_cmd.split(" ");
               if (process_cmd.length > 3) {
                  MCH_Lib.DbgLog(false, "ShuffleTeam:" + exe_cmd);
                  cmd.processCommand(player, process_cmd);
               }
            }
         }
      }
   }

   public static boolean spotEntity(
      EntityPlayer player, MCH_EntityAircraft ac, double posX, double posY, double posZ, int targetFilter, float spotLength, int markTime, float angle
   ) {
      boolean ret = false;
      if (!player.worldObj.isRemote) {
         float acYaw = 0.0F;
         float acPitch = 0.0F;
         float acRoll = 0.0F;
         if (ac != null) {
            acYaw = ac.getRotYaw();
            acPitch = ac.getRotPitch();
            acRoll = ac.getRotRoll();
         }

         Vec3 vv = MCH_Lib.RotVec3(0.0, 0.0, 1.0, -player.rotationYaw, -player.rotationPitch, -acRoll);
         double tx = vv.xCoord;
         double tz = vv.zCoord;
         List list = player.worldObj.getEntitiesWithinAABBExcludingEntity(player, player.boundingBox.expand(spotLength, spotLength, spotLength));
         List<Integer> entityList = new ArrayList<>();
         Vec3 pos = Vec3.createVectorHelper(posX, posY, posZ);

         for (int i = 0; i < list.size(); i++) {
            Entity entity = (Entity)list.get(i);
            if (canSpotEntityWithFilter(targetFilter, entity)) {
               MCH_TargetType stopType = canSpotEntity(player, posX, posY, posZ, entity, true);
               if (stopType != MCH_TargetType.NONE && stopType != MCH_TargetType.SAME_TEAM_PLAYER) {
                  double dist = entity.getDistanceSq(pos.xCoord, pos.yCoord, pos.zCoord);
                  if (dist > 1.0 && dist < spotLength * spotLength) {
                     double cx = entity.posX - pos.xCoord;
                     double cy = entity.posY - pos.yCoord;
                     double cz = entity.posZ - pos.zCoord;
                     double h = MCH_Lib.getPosAngle(tx, tz, cx, cz);
                     double v = Math.atan2(cy, Math.sqrt(cx * cx + cz * cz)) * 180.0 / Math.PI;
                     v = Math.abs(v + player.rotationPitch);
                     if (h < angle * 2.0F && v < angle * 2.0F) {
                        entityList.add(entity.getEntityId());
                     }
                  }
               }
            }
         }

         if (entityList.size() > 0) {
            int[] entityId = new int[entityList.size()];

            for (int i = 0; i < entityId.length; i++) {
               entityId[i] = entityList.get(i);
            }

            sendSpotedEntityListToSameTeam(player, markTime, entityId);
            ret = true;
         } else {
            ret = false;
         }
      }

      return ret;
   }

   public static void sendSpotedEntityListToSameTeam(EntityPlayer player, int count, int[] entityId) {
      ServerConfigurationManager svCnf = MinecraftServer.getServer().getConfigurationManager();

      for (EntityPlayer notifyPlayer : (java.util.List<EntityPlayer>)svCnf.playerEntityList) {
         if (player == notifyPlayer || player.isOnSameTeam(notifyPlayer)) {
            MCH_PacketNotifySpotedEntity.send(notifyPlayer, count, entityId);
         }
      }
   }

   public static boolean markPoint(EntityPlayer player, double posX, double posY, double posZ) {
      Vec3 vs = Vec3.createVectorHelper(posX, posY, posZ);
      Vec3 ve = MCH_Lib.Rot2Vec3(player.rotationYaw, player.rotationPitch);
      ve = vs.addVector(ve.xCoord * 300.0, ve.yCoord * 300.0, ve.zCoord * 300.0);
      MovingObjectPosition mop = player.worldObj.rayTraceBlocks(vs, ve, true);
      if (mop != null && mop.typeOfHit == MovingObjectType.BLOCK) {
         sendMarkPointToSameTeam(player, mop.blockX, mop.blockY + 2, mop.blockZ);
         return true;
      } else {
         sendMarkPointToSameTeam(player, 0, 1000, 0);
         return false;
      }
   }

   public static void sendMarkPointToSameTeam(EntityPlayer player, int x, int y, int z) {
      ServerConfigurationManager svCnf = MinecraftServer.getServer().getConfigurationManager();

      for (EntityPlayer notifyPlayer : (java.util.List<EntityPlayer>)svCnf.playerEntityList) {
         if (player == notifyPlayer || player.isOnSameTeam(notifyPlayer)) {
            MCH_PacketNotifyMarkPoint.send(notifyPlayer, x, y, z);
         }
      }
   }
}
