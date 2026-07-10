/*
 * Decompiled with CFR 0.152.
 */
package mcheli.aircraft;

import net.minecraft.command.ICommandManager;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;

public class MCH_DummyCommandSender
implements ICommandSender {
    public static MCH_DummyCommandSender instance = new MCH_DummyCommandSender();

    public static void execCommand(String s) {
        ICommandManager icommandmanager = MinecraftServer.func_71276_C().func_71187_D();
        icommandmanager.func_71556_a((ICommandSender)instance, s);
    }

    public String func_70005_c_() {
        return "";
    }

    public IChatComponent func_145748_c_() {
        return null;
    }

    public void func_145747_a(IChatComponent p_145747_1_) {
    }

    public boolean func_70003_b(int p_70003_1_, String p_70003_2_) {
        return true;
    }

    public ChunkCoordinates func_82114_b() {
        return null;
    }

    public World func_130014_f_() {
        return null;
    }
}

