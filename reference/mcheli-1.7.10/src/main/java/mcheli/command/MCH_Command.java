package mcheli.command;

import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import mcheli.MCH_Config;
import mcheli.MCH_MOD;
import mcheli.MCH_PacketNotifyServerSettings;
import mcheli.multiplay.MCH_MultiplayPacketHandler;
import mcheli.multiplay.MCH_PacketIndClient;
import net.minecraft.block.Block;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.CommandGameMode;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.SyntaxErrorException;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MathHelper;
import net.minecraft.util.IChatComponent.Serializer;
import net.minecraft.world.World;
import net.minecraftforge.event.CommandEvent;
import org.apache.commons.lang3.exception.ExceptionUtils;

public class MCH_Command extends CommandBase {
   public static final String CMD_GET_SS = "sendss";
   public static final String CMD_MOD_LIST = "modlist";
   public static final String CMD_RECONFIG = "reconfig";
   public static final String CMD_TITLE = "title";
   public static final String CMD_FILL = "fill";
   public static final String CMD_STATUS = "status";
   public static final String CMD_KILL_ENTITY = "killentity";
   public static final String CMD_REMOVE_ENTITY = "removeentity";
   public static final String CMD_ATTACK_ENTITY = "attackentity";
   public static final String CMD_SHOW_BB = "showboundingbox";
   public static final String CMD_LIST = "list";
   public static String[] ALL_COMMAND = new String[]{
      "sendss", "modlist", "reconfig", "title", "fill", "status", "killentity", "removeentity", "attackentity", "showboundingbox", "list"
   };
   public static MCH_Command instance = new MCH_Command();

   public static boolean canUseCommand(Entity player) {
      return player instanceof EntityPlayer ? instance.canCommandSenderUseCommand((EntityPlayer)player) : false;
   }

   public String getCommandName() {
      return "mcheli";
   }

   public static boolean checkCommandPermission(ICommandSender sender, String cmd) {
      if (new CommandGameMode().canCommandSenderUseCommand(sender)) {
         return true;
      }

      if (sender instanceof EntityPlayer && cmd.length() > 0) {
         String playerName = ((EntityPlayer)sender).getGameProfile().getName();

         for (MCH_Config.CommandPermission c : MCH_Config.CommandPermissionList) {
            if (c.name.equals(cmd)) {
               for (String s : c.players) {
                  if (s.equalsIgnoreCase(playerName)) {
                     return true;
                  }
               }
            }
         }
      }

      return false;
   }

   public static void onCommandEvent(CommandEvent event) {
      if (event.command instanceof MCH_Command) {
         if (event.parameters.length > 0 && event.parameters[0].length() > 0) {
            if (!checkCommandPermission(event.sender, event.parameters[0])) {
               event.setCanceled(true);
               ChatComponentTranslation c = new ChatComponentTranslation("commands.generic.permission", new Object[0]);
               c.getChatStyle().setColor(EnumChatFormatting.RED);
               event.sender.addChatMessage(c);
            }
         } else {
            event.setCanceled(true);
         }
      }
   }

   public boolean canCommandSenderUseCommand(ICommandSender player) {
      return true;
   }

   public String getCommandUsage(ICommandSender p_71518_1_) {
      return "commands.mcheli.usage";
   }

   public void processCommand(ICommandSender sender, String[] prm) {
      if (MCH_Config.EnableCommand.prmBool) {
         if (!checkCommandPermission(sender, prm[0])) {
            ChatComponentTranslation c = new ChatComponentTranslation("commands.generic.permission", new Object[0]);
            c.getChatStyle().setColor(EnumChatFormatting.RED);
            sender.addChatMessage(c);
         } else {
            if (prm[0].equalsIgnoreCase("sendss")) {
               if (prm.length != 2) {
                  throw new CommandException("Parameter error! : /mcheli sendss playerName", new Object[0]);
               }

               EntityPlayerMP player = getPlayer(sender, prm[1]);
               if (player != null) {
                  MCH_PacketIndClient.send(player, 1, prm[1]);
               }
            } else if (prm[0].equalsIgnoreCase("modlist")) {
               if (prm.length != 2) {
                  throw new CommandException("Parameter error! : /mcheli modlist playerName", new Object[0]);
               }

               EntityPlayerMP reqPlayer = sender instanceof EntityPlayerMP ? (EntityPlayerMP)sender : null;
               EntityPlayerMP player = getPlayer(sender, prm[1]);
               if (player != null) {
                  MCH_PacketIndClient.send(player, 2, "" + MCH_MultiplayPacketHandler.getPlayerInfoId(reqPlayer));
               }
            } else if (prm[0].equalsIgnoreCase("reconfig")) {
               if (prm.length != 1) {
                  throw new CommandException("Parameter error! : /mcheli reconfig", new Object[0]);
               }

               MCH_MOD.proxy.reconfig();
               if (sender.getEntityWorld() != null && !sender.getEntityWorld().isRemote) {
                  MCH_PacketNotifyServerSettings.sendAll();
               }

               if (MCH_MOD.proxy.isSinglePlayer()) {
                  sender.addChatMessage(new ChatComponentText("Reload mcheli.cfg"));
               } else {
                  sender.addChatMessage(new ChatComponentText("Reload server side mcheli.cfg"));
               }
            } else if (prm[0].equalsIgnoreCase("title")) {
               if (prm.length < 4) {
                  throw new WrongUsageException("Parameter error! : /mcheli title time[1~180] position[0~4] messege[JSON format]", new Object[0]);
               }

               String s = func_82360_a(sender, prm, 3);
               int showTime = Integer.valueOf(prm[1]);
               if (showTime < 1) {
                  showTime = 1;
               }

               if (showTime > 180) {
                  showTime = 180;
               }

               int pos = Integer.valueOf(prm[2]);
               if (pos < 0) {
                  pos = 0;
               }

               if (pos > 5) {
                  pos = 5;
               }

               try {
                  IChatComponent ichatcomponent = Serializer.func_150699_a(s);
                  MCH_PacketTitle.send(ichatcomponent, 20 * showTime, pos);
               } catch (JsonParseException jsonparseexception) {
                  Throwable throwable = ExceptionUtils.getRootCause(jsonparseexception);
                  throw new SyntaxErrorException("mcheli.title.jsonException", new Object[]{throwable == null ? "" : throwable.getMessage()});
               }
            } else if (prm[0].equalsIgnoreCase("fill")) {
               this.executeFill(sender, prm);
            } else if (prm[0].equalsIgnoreCase("status")) {
               this.executeStatus(sender, prm);
            } else if (prm[0].equalsIgnoreCase("killentity")) {
               this.executeKillEntity(sender, prm);
            } else if (prm[0].equalsIgnoreCase("removeentity")) {
               this.executeRemoveEntity(sender, prm);
            } else if (prm[0].equalsIgnoreCase("attackentity")) {
               this.executeAttackEntity(sender, prm);
            } else if (prm[0].equalsIgnoreCase("showboundingbox")) {
               if (prm.length != 2) {
                  throw new CommandException("Parameter error! : /mcheli showboundingbox true or false", new Object[0]);
               }

               if (!parseBoolean(sender, prm[1])) {
                  MCH_Config.EnableDebugBoundingBox.prmBool = false;
                  MCH_PacketNotifyServerSettings.sendAll();
                  sender.addChatMessage(new ChatComponentText("Disabled bounding box"));
               } else {
                  MCH_Config.EnableDebugBoundingBox.prmBool = true;
                  MCH_PacketNotifyServerSettings.sendAll();
                  sender.addChatMessage(new ChatComponentText("Enabled bounding box [F3 + b]"));
               }
            } else {
               if (!prm[0].equalsIgnoreCase("list")) {
                  throw new CommandException("Unknown mcheli command. please type /mcheli list", new Object[0]);
               }

               String msg = "";

               for (String s : ALL_COMMAND) {
                  msg = msg + s + ", ";
               }

               sender.addChatMessage(new ChatComponentText("/mcheli command list : " + msg));
            }
         }
      }
   }

   private void executeAttackEntity(ICommandSender sender, String[] args) {
      if (args.length < 3) {
         throw new WrongUsageException(
            "/mcheli attackentity <entity class name : example1 EntityBat , example2 minecraft.entity.passive> <damage> [damage source]", new Object[0]
         );
      }

      String className = args[1].toLowerCase();
      float damage = Float.valueOf(args[2]);
      String damageName = args.length >= 4 ? args[3].toLowerCase() : "";
      DamageSource ds = DamageSource.generic;
      if (!damageName.isEmpty()) {
         if (damageName.equals("player")) {
            if (sender instanceof EntityPlayer) {
               ds = DamageSource.causePlayerDamage((EntityPlayer)sender);
            }
         } else if (damageName.equals("anvil")) {
            ds = DamageSource.anvil;
         } else if (damageName.equals("cactus")) {
            ds = DamageSource.cactus;
         } else if (damageName.equals("drown")) {
            ds = DamageSource.drown;
         } else if (damageName.equals("fall")) {
            ds = DamageSource.fall;
         } else if (damageName.equals("fallingblock")) {
            ds = DamageSource.fallingBlock;
         } else if (damageName.equals("generic")) {
            ds = DamageSource.generic;
         } else if (damageName.equals("infire")) {
            ds = DamageSource.inFire;
         } else if (damageName.equals("inwall")) {
            ds = DamageSource.inWall;
         } else if (damageName.equals("lava")) {
            ds = DamageSource.lava;
         } else if (damageName.equals("magic")) {
            ds = DamageSource.magic;
         } else if (damageName.equals("onfire")) {
            ds = DamageSource.onFire;
         } else if (damageName.equals("starve")) {
            ds = DamageSource.starve;
         } else if (damageName.equals("wither")) {
            ds = DamageSource.wither;
         }
      }

      int attacked = 0;
      List list = sender.getEntityWorld().loadedEntityList;

      for (int i = 0; i < list.size(); i++) {
         if (list.get(i) != null && !(list.get(i) instanceof EntityPlayer) && list.get(i).getClass().getName().toLowerCase().indexOf(className) >= 0) {
            ((Entity)list.get(i)).attackEntityFrom(ds, damage);
            attacked++;
         }
      }

      sender.addChatMessage(new ChatComponentText(attacked + " entity attacked(" + args[1] + ", damage=" + damage + ")."));
   }

   private void executeKillEntity(ICommandSender sender, String[] args) {
      if (args.length < 2) {
         throw new WrongUsageException("/mcheli killentity <entity class name : example1 EntityBat , example2 minecraft.entity.passive>", new Object[0]);
      }

      String className = args[1].toLowerCase();
      int killed = 0;
      List list = sender.getEntityWorld().loadedEntityList;

      for (int i = 0; i < list.size(); i++) {
         if (list.get(i) != null && !(list.get(i) instanceof EntityPlayer) && list.get(i).getClass().getName().toLowerCase().indexOf(className) >= 0) {
            ((Entity)list.get(i)).setDead();
            killed++;
         }
      }

      sender.addChatMessage(new ChatComponentText(killed + " entity killed(" + args[1] + ")."));
   }

   private void executeRemoveEntity(ICommandSender sender, String[] args) {
      if (args.length < 2) {
         throw new WrongUsageException("/mcheli removeentity <entity class name : example1 EntityBat , example2 minecraft.entity.passive>", new Object[0]);
      }

      String className = args[1].toLowerCase();
      List list = sender.getEntityWorld().loadedEntityList;
      int removed = 0;

      for (int i = 0; i < list.size(); i++) {
         if (list.get(i) != null && !(list.get(i) instanceof EntityPlayer) && list.get(i).getClass().getName().toLowerCase().indexOf(className) >= 0) {
            ((Entity)list.get(i)).isDead = true;
            removed++;
         }
      }

      sender.addChatMessage(new ChatComponentText(removed + " entity removed(" + args[1] + ")."));
   }

   private void executeStatus(ICommandSender sender, String[] args) {
      if (args.length < 2) {
         throw new WrongUsageException("/mcheli status <entity or tile> [min num]", new Object[0]);
      }

      if (args[1].equalsIgnoreCase("entity")) {
         this.executeStatusSub(sender, args, "Server loaded Entity List", sender.getEntityWorld().loadedEntityList);
      } else if (args[1].equalsIgnoreCase("tile")) {
         this.executeStatusSub(sender, args, "Server loaded Tile Entity List", sender.getEntityWorld().loadedTileEntityList);
      }
   }

   private void executeStatusSub(ICommandSender sender, String[] args, String title, List list) {
      int minNum = args.length >= 3 ? Integer.valueOf(args[2]) : 0;
      HashMap<String, Integer> map = new HashMap<>();

      for (int i = 0; i < list.size(); i++) {
         String key = list.get(i).getClass().getName();
         if (map.containsKey(key)) {
            map.put(key, map.get(key) + 1);
         } else {
            map.put(key, 1);
         }
      }

      List<Entry<String, Integer>> entries = new ArrayList<>(map.entrySet());
      Collections.sort(entries, new Comparator<Entry<String, Integer>>() {
         public int compare(Entry<String, Integer> entry1, Entry<String, Integer> entry2) {
            return entry1.getKey().compareTo(entry2.getKey());
         }
      });
      boolean send = false;
      sender.addChatMessage(new ChatComponentText("--- " + title + " ---"));

      for (Entry<String, Integer> s : entries) {
         if (s.getValue() >= minNum) {
            String msg = " " + s.getKey() + " : " + s.getValue();
            System.out.println(msg);
            sender.addChatMessage(new ChatComponentText(msg));
            send = true;
         }
      }

      if (!send) {
         System.out.println("none");
         sender.addChatMessage(new ChatComponentText("none"));
      }
   }

   public void executeFill(ICommandSender sender, String[] args) {
      if (args.length < 8) {
         throw new WrongUsageException("/mcheli fill <x1> <y1> <z1> <x2> <y2> <z2> <block name> [meta data] [oldBlockHandling] [data tag]", new Object[0]);
      }

      int x1 = sender.getPlayerCoordinates().posX;
      int y1 = sender.getPlayerCoordinates().posY;
      int z1 = sender.getPlayerCoordinates().posZ;
      int x2 = sender.getPlayerCoordinates().posX;
      int y2 = sender.getPlayerCoordinates().posY;
      int z2 = sender.getPlayerCoordinates().posZ;
      x1 = MathHelper.floor_double(func_110666_a(sender, x1, args[1]));
      y1 = MathHelper.floor_double(func_110666_a(sender, y1, args[2]));
      z1 = MathHelper.floor_double(func_110666_a(sender, z1, args[3]));
      x2 = MathHelper.floor_double(func_110666_a(sender, x2, args[4]));
      y2 = MathHelper.floor_double(func_110666_a(sender, y2, args[5]));
      z2 = MathHelper.floor_double(func_110666_a(sender, z2, args[6]));
      Block block = CommandBase.getBlockByText(sender, args[7]);
      int metadata = 0;
      if (args.length >= 9) {
         metadata = parseIntBounded(sender, args[8], 0, 15);
      }

      World world = sender.getEntityWorld();
      if (x1 > x2) {
         int t = x1;
         x1 = x2;
         x2 = t;
      }

      if (y1 > y2) {
         int t = y1;
         y1 = y2;
         y2 = t;
      }

      if (z1 > z2) {
         int t = z1;
         z1 = z2;
         z2 = t;
      }

      if (y1 >= 0 && y2 < 256) {
         int blockNum = (x2 - x1 + 1) * (y2 - y1 + 1) * (z2 - z1 + 1);
         if (blockNum > 3000000) {
            throw new CommandException("commands.setblock.tooManyBlocks " + blockNum + " limit=327680", new Object[]{blockNum, 3276800});
         }

         boolean result = false;
         boolean keep = args.length >= 10 && args[9].equals("keep");
         boolean destroy = args.length >= 10 && args[9].equals("destroy");
         boolean override = args.length >= 10 && args[9].equals("override");
         NBTTagCompound nbttagcompound = new NBTTagCompound();
         boolean flag = false;
         if (args.length >= 11 && block.hasTileEntity()) {
            String s = func_147178_a(sender, args, 10).getUnformattedText();

            try {
               NBTBase nbtbase = JsonToNBT.func_150315_a(s);
               if (!(nbtbase instanceof NBTTagCompound)) {
                  throw new CommandException("commands.setblock.tagError", new Object[]{"Not a valid tag"});
               }

               nbttagcompound = (NBTTagCompound)nbtbase;
               flag = true;
            } catch (NBTException nbtexception) {
               throw new CommandException("commands.setblock.tagError", new Object[]{nbtexception.getMessage()});
            }
         }

         for (int x = x1; x <= x2; x++) {
            for (int y = y1; y <= y2; y++) {
               for (int z = z1; z <= z2; z++) {
                  if (world.blockExists(x, y, z) && (world.isAirBlock(x, y, z) ? !override : !keep)) {
                     if (destroy) {
                        world.func_147480_a(x, y, z, false);
                     }

                     TileEntity block2 = world.getTileEntity(x, y, z);
                     if (block2 instanceof IInventory) {
                        IInventory ii = (IInventory)block2;

                        for (int i = 0; i < ii.getSizeInventory(); i++) {
                           ItemStack is = ii.getStackInSlotOnClosing(i);
                           if (is != null) {
                              is.stackSize = 0;
                           }
                        }
                     }

                     if (world.setBlock(x, y, z, block, metadata, 3)) {
                        if (flag) {
                           TileEntity tileentity = world.getTileEntity(x, y, z);
                           if (tileentity != null) {
                              nbttagcompound.setInteger("x", x);
                              nbttagcompound.setInteger("y", y);
                              nbttagcompound.setInteger("z", z);
                              tileentity.readFromNBT(nbttagcompound);
                           }
                        }

                        result = true;
                     }
                  }
               }
            }
         }

         if (result) {
            func_152373_a(sender, this, "commands.setblock.success", new Object[0]);
         } else {
            throw new CommandException("commands.setblock.noChange", new Object[0]);
         }
      } else {
         throw new CommandException("commands.setblock.outOfWorld", new Object[0]);
      }
   }

   public List addTabCompletionOptions(ICommandSender sender, String[] prm) {
      if (!MCH_Config.EnableCommand.prmBool) {
         return null;
      }

      if (prm.length <= 1) {
         return getListOfStringsMatchingLastWord(prm, ALL_COMMAND);
      }

      if (prm[0].equalsIgnoreCase("sendss")) {
         if (prm.length == 2) {
            return getListOfStringsMatchingLastWord(prm, MinecraftServer.getServer().getAllUsernames());
         }
      } else if (prm[0].equalsIgnoreCase("modlist")) {
         if (prm.length == 3) {
            return getListOfStringsMatchingLastWord(prm, MinecraftServer.getServer().getAllUsernames());
         }
      } else {
         if (prm[0].equalsIgnoreCase("fill")) {
            if ((prm.length == 2 || prm.length == 5) && sender instanceof Entity) {
               Entity entity = (Entity)sender;
               List a = new ArrayList();
               int x = entity.posX < 0.0 ? (int)(entity.posX - 1.0) : (int)entity.posX;
               int z = entity.posZ < 0.0 ? (int)(entity.posZ - 1.0) : (int)entity.posZ;
               a.add("" + x + " " + (int)(entity.posY + 0.5) + " " + z);
               return a;
            }

            return prm.length == 8
               ? getListOfStringsFromIterableMatchingLastWord(prm, Block.blockRegistry.getKeys())
               : (prm.length == 10 ? getListOfStringsMatchingLastWord(prm, new String[]{"replace", "destroy", "keep", "override"}) : null);
         }

         if (prm[0].equalsIgnoreCase("status")) {
            if (prm.length == 2) {
               return getListOfStringsMatchingLastWord(prm, new String[]{"entity", "tile"});
            }
         } else if (prm[0].equalsIgnoreCase("attackentity")) {
            if (prm.length == 4) {
               return getListOfStringsMatchingLastWord(
                  prm,
                  new String[]{
                     "player",
                     "inFire",
                     "onFire",
                     "lava",
                     "inWall",
                     "drown",
                     "starve",
                     "cactus",
                     "fall",
                     "outOfWorld",
                     "generic",
                     "magic",
                     "wither",
                     "anvil",
                     "fallingBlock"
                  }
               );
            }
         } else if (prm[0].equalsIgnoreCase("showboundingbox") && prm.length == 2) {
            return getListOfStringsMatchingLastWord(prm, new String[]{"true", "false"});
         }
      }

      return null;
   }
}
