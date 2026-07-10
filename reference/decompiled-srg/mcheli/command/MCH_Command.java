/*
 * Decompiled with CFR 0.152.
 */
package mcheli.command;

import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mcheli.MCH_Config;
import mcheli.MCH_MOD;
import mcheli.MCH_PacketNotifyServerSettings;
import mcheli.command.MCH_PacketTitle;
import mcheli.multiplay.MCH_MultiplayPacketHandler;
import mcheli.multiplay.MCH_PacketIndClient;
import net.minecraft.block.Block;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.CommandGameMode;
import net.minecraft.command.ICommand;
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
import net.minecraft.world.World;
import net.minecraftforge.event.CommandEvent;
import org.apache.commons.lang3.exception.ExceptionUtils;

/*
 * Exception performing whole class analysis ignored.
 */
public class MCH_Command
extends CommandBase {
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
    public static String[] ALL_COMMAND = new String[]{"sendss", "modlist", "reconfig", "title", "fill", "status", "killentity", "removeentity", "attackentity", "showboundingbox", "list"};
    public static MCH_Command instance = new MCH_Command();

    public static boolean canUseCommand(Entity player) {
        return player instanceof EntityPlayer ? instance.func_71519_b((ICommandSender)((EntityPlayer)player)) : false;
    }

    public String func_71517_b() {
        return "mcheli";
    }

    public static boolean checkCommandPermission(ICommandSender sender, String cmd) {
        if (new CommandGameMode().func_71519_b(sender)) {
            return true;
        }
        if (sender instanceof EntityPlayer && cmd.length() > 0) {
            String playerName = ((EntityPlayer)sender).func_146103_bH().getName();
            for (MCH_Config.CommandPermission c : MCH_Config.CommandPermissionList) {
                if (!c.name.equals(cmd)) continue;
                for (String s : c.players) {
                    if (!s.equalsIgnoreCase(playerName)) continue;
                    return true;
                }
            }
        }
        return false;
    }

    public static void onCommandEvent(CommandEvent event) {
        if (!(event.command instanceof MCH_Command)) {
            return;
        }
        if (event.parameters.length <= 0 || event.parameters[0].length() <= 0) {
            event.setCanceled(true);
            return;
        }
        if (!MCH_Command.checkCommandPermission((ICommandSender)event.sender, (String)event.parameters[0])) {
            event.setCanceled(true);
            ChatComponentTranslation c = new ChatComponentTranslation("commands.generic.permission", new Object[0]);
            c.func_150256_b().func_150238_a(EnumChatFormatting.RED);
            event.sender.func_145747_a((IChatComponent)c);
        }
    }

    public boolean func_71519_b(ICommandSender player) {
        return true;
    }

    public String func_71518_a(ICommandSender p_71518_1_) {
        return "commands.mcheli.usage";
    }

    /*
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    public void func_71515_b(ICommandSender sender, String[] prm) {
        if (!MCH_Config.EnableCommand.prmBool) {
            return;
        }
        if (!MCH_Command.checkCommandPermission((ICommandSender)sender, (String)prm[0])) {
            ChatComponentTranslation c = new ChatComponentTranslation("commands.generic.permission", new Object[0]);
            c.func_150256_b().func_150238_a(EnumChatFormatting.RED);
            sender.func_145747_a((IChatComponent)c);
            return;
        }
        if (prm[0].equalsIgnoreCase("sendss")) {
            if (prm.length != 2) throw new CommandException("Parameter error! : /mcheli sendss playerName", new Object[0]);
            EntityPlayerMP player = MCH_Command.func_82359_c((ICommandSender)sender, (String)prm[1]);
            if (player == null) return;
            MCH_PacketIndClient.send((EntityPlayer)player, (int)1, (String)prm[1]);
            return;
        } else if (prm[0].equalsIgnoreCase("modlist")) {
            if (prm.length != 2) throw new CommandException("Parameter error! : /mcheli modlist playerName", new Object[0]);
            EntityPlayerMP reqPlayer = sender instanceof EntityPlayerMP ? (EntityPlayerMP)sender : null;
            EntityPlayerMP player = MCH_Command.func_82359_c((ICommandSender)sender, (String)prm[1]);
            if (player == null) return;
            MCH_PacketIndClient.send((EntityPlayer)player, (int)2, (String)("" + MCH_MultiplayPacketHandler.getPlayerInfoId((EntityPlayer)reqPlayer)));
            return;
        } else if (prm[0].equalsIgnoreCase("reconfig")) {
            if (prm.length != 1) throw new CommandException("Parameter error! : /mcheli reconfig", new Object[0]);
            MCH_MOD.proxy.reconfig();
            if (sender.func_130014_f_() != null && !sender.func_130014_f_().field_72995_K) {
                MCH_PacketNotifyServerSettings.sendAll();
            }
            if (MCH_MOD.proxy.isSinglePlayer()) {
                sender.func_145747_a((IChatComponent)new ChatComponentText("Reload mcheli.cfg"));
                return;
            } else {
                sender.func_145747_a((IChatComponent)new ChatComponentText("Reload server side mcheli.cfg"));
            }
            return;
        } else if (prm[0].equalsIgnoreCase("title")) {
            int pos;
            if (prm.length < 4) {
                throw new WrongUsageException("Parameter error! : /mcheli title time[1~180] position[0~4] messege[JSON format]", new Object[0]);
            }
            String s = MCH_Command.func_82360_a((ICommandSender)sender, (String[])prm, (int)3);
            int showTime = Integer.valueOf(prm[1]);
            if (showTime < 1) {
                showTime = 1;
            }
            if (showTime > 180) {
                showTime = 180;
            }
            if ((pos = Integer.valueOf(prm[2]).intValue()) < 0) {
                pos = 0;
            }
            if (pos > 5) {
                pos = 5;
            }
            try {
                IChatComponent ichatcomponent = IChatComponent.Serializer.func_150699_a((String)s);
                MCH_PacketTitle.send((IChatComponent)ichatcomponent, (int)(20 * showTime), (int)pos);
                return;
            }
            catch (JsonParseException jsonparseexception) {
                Throwable throwable = ExceptionUtils.getRootCause((Throwable)jsonparseexception);
                throw new SyntaxErrorException("mcheli.title.jsonException", new Object[]{throwable == null ? "" : throwable.getMessage()});
            }
        } else if (prm[0].equalsIgnoreCase("fill")) {
            this.executeFill(sender, prm);
            return;
        } else if (prm[0].equalsIgnoreCase("status")) {
            this.executeStatus(sender, prm);
            return;
        } else if (prm[0].equalsIgnoreCase("killentity")) {
            this.executeKillEntity(sender, prm);
            return;
        } else if (prm[0].equalsIgnoreCase("removeentity")) {
            this.executeRemoveEntity(sender, prm);
            return;
        } else if (prm[0].equalsIgnoreCase("attackentity")) {
            this.executeAttackEntity(sender, prm);
            return;
        } else if (prm[0].equalsIgnoreCase("showboundingbox")) {
            if (prm.length != 2) {
                throw new CommandException("Parameter error! : /mcheli showboundingbox true or false", new Object[0]);
            }
            if (!MCH_Command.func_110662_c((ICommandSender)sender, (String)prm[1])) {
                MCH_Config.EnableDebugBoundingBox.prmBool = false;
                MCH_PacketNotifyServerSettings.sendAll();
                sender.func_145747_a((IChatComponent)new ChatComponentText("Disabled bounding box"));
                return;
            } else {
                MCH_Config.EnableDebugBoundingBox.prmBool = true;
                MCH_PacketNotifyServerSettings.sendAll();
                sender.func_145747_a((IChatComponent)new ChatComponentText("Enabled bounding box [F3 + b]"));
            }
            return;
        } else {
            if (!prm[0].equalsIgnoreCase("list")) throw new CommandException("Unknown mcheli command. please type /mcheli list", new Object[0]);
            String msg = "";
            for (String s : ALL_COMMAND) {
                msg = msg + s + ", ";
            }
            sender.func_145747_a((IChatComponent)new ChatComponentText("/mcheli command list : " + msg));
        }
    }

    private void executeAttackEntity(ICommandSender sender, String[] args) {
        if (args.length < 3) {
            throw new WrongUsageException("/mcheli attackentity <entity class name : example1 EntityBat , example2 minecraft.entity.passive> <damage> [damage source]", new Object[0]);
        }
        String className = args[1].toLowerCase();
        float damage = Float.valueOf(args[2]).floatValue();
        String damageName = args.length >= 4 ? args[3].toLowerCase() : "";
        DamageSource ds = DamageSource.field_76377_j;
        if (!damageName.isEmpty()) {
            if (damageName.equals("player")) {
                if (sender instanceof EntityPlayer) {
                    ds = DamageSource.func_76365_a((EntityPlayer)((EntityPlayer)sender));
                }
            } else if (damageName.equals("anvil")) {
                ds = DamageSource.field_82728_o;
            } else if (damageName.equals("cactus")) {
                ds = DamageSource.field_76367_g;
            } else if (damageName.equals("drown")) {
                ds = DamageSource.field_76369_e;
            } else if (damageName.equals("fall")) {
                ds = DamageSource.field_76379_h;
            } else if (damageName.equals("fallingblock")) {
                ds = DamageSource.field_82729_p;
            } else if (damageName.equals("generic")) {
                ds = DamageSource.field_76377_j;
            } else if (damageName.equals("infire")) {
                ds = DamageSource.field_76372_a;
            } else if (damageName.equals("inwall")) {
                ds = DamageSource.field_76368_d;
            } else if (damageName.equals("lava")) {
                ds = DamageSource.field_76371_c;
            } else if (damageName.equals("magic")) {
                ds = DamageSource.field_76376_m;
            } else if (damageName.equals("onfire")) {
                ds = DamageSource.field_76370_b;
            } else if (damageName.equals("starve")) {
                ds = DamageSource.field_76366_f;
            } else if (damageName.equals("wither")) {
                ds = DamageSource.field_82727_n;
            }
        }
        int attacked = 0;
        List list = sender.func_130014_f_().field_72996_f;
        for (int i = 0; i < list.size(); ++i) {
            if (list.get(i) == null || list.get(i) instanceof EntityPlayer || list.get(i).getClass().getName().toLowerCase().indexOf(className) < 0) continue;
            ((Entity)list.get(i)).func_70097_a(ds, damage);
            ++attacked;
        }
        sender.func_145747_a((IChatComponent)new ChatComponentText(attacked + " entity attacked(" + args[1] + ", damage=" + damage + ")."));
    }

    private void executeKillEntity(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            throw new WrongUsageException("/mcheli killentity <entity class name : example1 EntityBat , example2 minecraft.entity.passive>", new Object[0]);
        }
        String className = args[1].toLowerCase();
        int killed = 0;
        List list = sender.func_130014_f_().field_72996_f;
        for (int i = 0; i < list.size(); ++i) {
            if (list.get(i) == null || list.get(i) instanceof EntityPlayer || list.get(i).getClass().getName().toLowerCase().indexOf(className) < 0) continue;
            ((Entity)list.get(i)).func_70106_y();
            ++killed;
        }
        sender.func_145747_a((IChatComponent)new ChatComponentText(killed + " entity killed(" + args[1] + ")."));
    }

    private void executeRemoveEntity(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            throw new WrongUsageException("/mcheli removeentity <entity class name : example1 EntityBat , example2 minecraft.entity.passive>", new Object[0]);
        }
        String className = args[1].toLowerCase();
        List list = sender.func_130014_f_().field_72996_f;
        int removed = 0;
        for (int i = 0; i < list.size(); ++i) {
            if (list.get(i) == null || list.get(i) instanceof EntityPlayer || list.get(i).getClass().getName().toLowerCase().indexOf(className) < 0) continue;
            ((Entity)list.get((int)i)).field_70128_L = true;
            ++removed;
        }
        sender.func_145747_a((IChatComponent)new ChatComponentText(removed + " entity removed(" + args[1] + ")."));
    }

    private void executeStatus(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            throw new WrongUsageException("/mcheli status <entity or tile> [min num]", new Object[0]);
        }
        if (args[1].equalsIgnoreCase("entity")) {
            this.executeStatusSub(sender, args, "Server loaded Entity List", sender.func_130014_f_().field_72996_f);
        } else if (args[1].equalsIgnoreCase("tile")) {
            this.executeStatusSub(sender, args, "Server loaded Tile Entity List", sender.func_130014_f_().field_147482_g);
        }
    }

    private void executeStatusSub(ICommandSender sender, String[] args, String title, List list) {
        int minNum = args.length >= 3 ? Integer.valueOf(args[2]) : 0;
        HashMap<String, Integer> map = new HashMap<String, Integer>();
        for (int i = 0; i < list.size(); ++i) {
            String key = list.get(i).getClass().getName();
            if (map.containsKey(key)) {
                map.put(key, (Integer)map.get(key) + 1);
                continue;
            }
            map.put(key, 1);
        }
        ArrayList entries = new ArrayList(map.entrySet());
        Collections.sort(entries, new /* Unavailable Anonymous Inner Class!! */);
        boolean send = false;
        sender.func_145747_a((IChatComponent)new ChatComponentText("--- " + title + " ---"));
        for (Map.Entry entry : entries) {
            if ((Integer)entry.getValue() < minNum) continue;
            String msg = " " + (String)entry.getKey() + " : " + entry.getValue();
            System.out.println(msg);
            sender.func_145747_a((IChatComponent)new ChatComponentText(msg));
            send = true;
        }
        if (!send) {
            System.out.println("none");
            sender.func_145747_a((IChatComponent)new ChatComponentText("none"));
        }
    }

    public void executeFill(ICommandSender sender, String[] args) {
        int t;
        if (args.length < 8) {
            throw new WrongUsageException("/mcheli fill <x1> <y1> <z1> <x2> <y2> <z2> <block name> [meta data] [oldBlockHandling] [data tag]", new Object[0]);
        }
        int x1 = sender.func_82114_b().field_71574_a;
        int y1 = sender.func_82114_b().field_71572_b;
        int z1 = sender.func_82114_b().field_71573_c;
        int x2 = sender.func_82114_b().field_71574_a;
        int y2 = sender.func_82114_b().field_71572_b;
        int z2 = sender.func_82114_b().field_71573_c;
        x1 = MathHelper.func_76128_c((double)MCH_Command.func_110666_a((ICommandSender)sender, (double)x1, (String)args[1]));
        y1 = MathHelper.func_76128_c((double)MCH_Command.func_110666_a((ICommandSender)sender, (double)y1, (String)args[2]));
        z1 = MathHelper.func_76128_c((double)MCH_Command.func_110666_a((ICommandSender)sender, (double)z1, (String)args[3]));
        x2 = MathHelper.func_76128_c((double)MCH_Command.func_110666_a((ICommandSender)sender, (double)x2, (String)args[4]));
        y2 = MathHelper.func_76128_c((double)MCH_Command.func_110666_a((ICommandSender)sender, (double)y2, (String)args[5]));
        z2 = MathHelper.func_76128_c((double)MCH_Command.func_110666_a((ICommandSender)sender, (double)z2, (String)args[6]));
        Block block = CommandBase.func_147180_g((ICommandSender)sender, (String)args[7]);
        int metadata = 0;
        if (args.length >= 9) {
            metadata = MCH_Command.func_71532_a((ICommandSender)sender, (String)args[8], (int)0, (int)15);
        }
        World world = sender.func_130014_f_();
        if (x1 > x2) {
            t = x1;
            x1 = x2;
            x2 = t;
        }
        if (y1 > y2) {
            t = y1;
            y1 = y2;
            y2 = t;
        }
        if (z1 > z2) {
            t = z1;
            z1 = z2;
            z2 = t;
        }
        if (y1 < 0 || y2 >= 256) {
            throw new CommandException("commands.setblock.outOfWorld", new Object[0]);
        }
        int blockNum = (x2 - x1 + 1) * (y2 - y1 + 1) * (z2 - z1 + 1);
        if (blockNum > 3000000) {
            throw new CommandException("commands.setblock.tooManyBlocks " + blockNum + " limit=327680", new Object[]{blockNum, 0x320000});
        }
        boolean result = false;
        boolean keep = args.length >= 10 && args[9].equals("keep");
        boolean destroy = args.length >= 10 && args[9].equals("destroy");
        boolean override = args.length >= 10 && args[9].equals("override");
        NBTTagCompound nbttagcompound = new NBTTagCompound();
        boolean flag = false;
        if (args.length >= 11 && block.func_149716_u()) {
            String s = MCH_Command.func_147178_a((ICommandSender)sender, (String[])args, (int)10).func_150260_c();
            try {
                NBTBase nbtbase = JsonToNBT.func_150315_a((String)s);
                if (!(nbtbase instanceof NBTTagCompound)) {
                    throw new CommandException("commands.setblock.tagError", new Object[]{"Not a valid tag"});
                }
                nbttagcompound = (NBTTagCompound)nbtbase;
                flag = true;
            }
            catch (NBTException nbtexception) {
                throw new CommandException("commands.setblock.tagError", new Object[]{nbtexception.getMessage()});
            }
        }
        for (int x = x1; x <= x2; ++x) {
            for (int y = y1; y <= y2; ++y) {
                for (int z = z1; z <= z2; ++z) {
                    TileEntity tileentity;
                    TileEntity block2;
                    if (!world.func_72899_e(x, y, z) || (!world.func_147437_c(x, y, z) ? keep : override)) continue;
                    if (destroy) {
                        world.func_147480_a(x, y, z, false);
                    }
                    if ((block2 = world.func_147438_o(x, y, z)) instanceof IInventory) {
                        IInventory ii = (IInventory)block2;
                        for (int i = 0; i < ii.func_70302_i_(); ++i) {
                            ItemStack is = ii.func_70304_b(i);
                            if (is == null) continue;
                            is.field_77994_a = 0;
                        }
                    }
                    if (!world.func_147465_d(x, y, z, block, metadata, 3)) continue;
                    if (flag && (tileentity = world.func_147438_o(x, y, z)) != null) {
                        nbttagcompound.func_74768_a("x", x);
                        nbttagcompound.func_74768_a("y", y);
                        nbttagcompound.func_74768_a("z", z);
                        tileentity.func_145839_a(nbttagcompound);
                    }
                    result = true;
                }
            }
        }
        if (!result) {
            throw new CommandException("commands.setblock.noChange", new Object[0]);
        }
        MCH_Command.func_152373_a((ICommandSender)sender, (ICommand)this, (String)"commands.setblock.success", (Object[])new Object[0]);
    }

    public List func_71516_a(ICommandSender sender, String[] prm) {
        if (!MCH_Config.EnableCommand.prmBool) {
            return null;
        }
        if (prm.length <= 1) {
            return MCH_Command.func_71530_a((String[])prm, (String[])ALL_COMMAND);
        }
        if (prm[0].equalsIgnoreCase("sendss")) {
            if (prm.length == 2) {
                return MCH_Command.func_71530_a((String[])prm, (String[])MinecraftServer.func_71276_C().func_71213_z());
            }
        } else if (prm[0].equalsIgnoreCase("modlist")) {
            if (prm.length == 3) {
                return MCH_Command.func_71530_a((String[])prm, (String[])MinecraftServer.func_71276_C().func_71213_z());
            }
        } else {
            if (prm[0].equalsIgnoreCase("fill")) {
                if ((prm.length == 2 || prm.length == 5) && sender instanceof Entity) {
                    Entity entity = (Entity)sender;
                    ArrayList<String> a = new ArrayList<String>();
                    int x = entity.field_70165_t < 0.0 ? (int)(entity.field_70165_t - 1.0) : (int)entity.field_70165_t;
                    int z = entity.field_70161_v < 0.0 ? (int)(entity.field_70161_v - 1.0) : (int)entity.field_70161_v;
                    a.add("" + x + " " + (int)(entity.field_70163_u + 0.5) + " " + z);
                    return a;
                }
                return prm.length == 8 ? MCH_Command.func_71531_a((String[])prm, (Iterable)Block.field_149771_c.func_148742_b()) : (prm.length == 10 ? MCH_Command.func_71530_a((String[])prm, (String[])new String[]{"replace", "destroy", "keep", "override"}) : null);
            }
            if (prm[0].equalsIgnoreCase("status")) {
                if (prm.length == 2) {
                    return MCH_Command.func_71530_a((String[])prm, (String[])new String[]{"entity", "tile"});
                }
            } else if (prm[0].equalsIgnoreCase("attackentity")) {
                if (prm.length == 4) {
                    return MCH_Command.func_71530_a((String[])prm, (String[])new String[]{"player", "inFire", "onFire", "lava", "inWall", "drown", "starve", "cactus", "fall", "outOfWorld", "generic", "magic", "wither", "anvil", "fallingBlock"});
                }
            } else if (prm[0].equalsIgnoreCase("showboundingbox") && prm.length == 2) {
                return MCH_Command.func_71530_a((String[])prm, (String[])new String[]{"true", "false"});
            }
        }
        return null;
    }
}

