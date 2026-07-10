/*
 * Decompiled with CFR 0.152.
 */
package mcheli.wrapper;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import mcheli.wrapper.ITickHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

public abstract class W_TickHandler
implements ITickHandler {
    protected Minecraft mc;

    public W_TickHandler(Minecraft m) {
        this.mc = m;
    }

    public void onPlayerTickPre(EntityPlayer player) {
    }

    public void onPlayerTickPost(EntityPlayer player) {
    }

    public void onRenderTickPre(float partialTicks) {
    }

    public void onRenderTickPost(float partialTicks) {
    }

    public void onTickPre() {
    }

    public void onTickPost() {
    }

    @SubscribeEvent
    public void onPlayerTickEvent(TickEvent.PlayerTickEvent event) {
        TickEvent.Phase cfr_ignored_0 = event.phase;
        if (event.phase == TickEvent.Phase.START) {
            this.onPlayerTickPre(event.player);
        }
        TickEvent.Phase cfr_ignored_1 = event.phase;
        if (event.phase == TickEvent.Phase.END) {
            this.onPlayerTickPost(event.player);
        }
    }

    @SubscribeEvent
    public void onClientTickEvent(TickEvent.ClientTickEvent event) {
        TickEvent.Phase cfr_ignored_0 = event.phase;
        if (event.phase == TickEvent.Phase.START) {
            this.onTickPre();
        }
        TickEvent.Phase cfr_ignored_1 = event.phase;
        if (event.phase == TickEvent.Phase.END) {
            this.onTickPost();
        }
    }

    @SubscribeEvent
    public void onRenderTickEvent(TickEvent.RenderTickEvent event) {
        TickEvent.Phase cfr_ignored_0 = event.phase;
        if (event.phase == TickEvent.Phase.START) {
            this.onRenderTickPre(event.renderTickTime);
        }
        TickEvent.Phase cfr_ignored_1 = event.phase;
        if (event.phase == TickEvent.Phase.END) {
            this.onRenderTickPost(event.renderTickTime);
        }
    }
}

