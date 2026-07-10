/*
 * Decompiled with CFR 0.152.
 */
package mcheli;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.internal.FMLProxyPacket;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import mcheli.wrapper.W_Reflection;
import net.minecraft.network.NetworkManager;

public class MCH_ServerTickHandler {
    HashMap<String, Integer> rcvMap = new HashMap();
    HashMap<String, Integer> sndMap = new HashMap();
    int sndPacketNum = 0;
    int rcvPacketNum = 0;
    int tick;

    @SubscribeEvent
    public void onServerTickEvent(TickEvent.ServerTickEvent event) {
        TickEvent.Phase cfr_ignored_0 = event.phase;
        if (event.phase == TickEvent.Phase.START) {
            // empty if block
        }
        TickEvent.Phase cfr_ignored_1 = event.phase;
        if (event.phase == TickEvent.Phase.END) {
            // empty if block
        }
    }

    private void onServerTickPre() {
        ++this.tick;
        List list = W_Reflection.getNetworkManagers();
        if (list != null) {
            for (int i = 0; i < list.size(); ++i) {
                Queue queue = W_Reflection.getReceivedPacketsQueue((NetworkManager)((NetworkManager)list.get(i)));
                if (queue != null) {
                    this.putMap(this.rcvMap, queue.iterator());
                    this.rcvPacketNum += queue.size();
                }
                if ((queue = W_Reflection.getSendPacketsQueue((NetworkManager)((NetworkManager)list.get(i)))) == null) continue;
                this.putMap(this.sndMap, queue.iterator());
                this.sndPacketNum += queue.size();
            }
        }
        if (this.tick >= 20) {
            this.tick = 0;
            this.sndPacketNum = 0;
            this.rcvPacketNum = 0;
            this.rcvMap.clear();
            this.sndMap.clear();
        }
    }

    public void putMap(HashMap<String, Integer> map, Iterator iterator) {
        while (iterator.hasNext()) {
            Object o = iterator.next();
            String key = o.getClass().getName().toString();
            if (key.startsWith("net.minecraft.")) {
                key = "Minecraft";
            } else if (o instanceof FMLProxyPacket) {
                FMLProxyPacket p = (FMLProxyPacket)o;
                key = p.channel();
            } else {
                key = "Unknown!";
            }
            if (map.containsKey(key)) {
                map.put(key, 1 + map.get(key));
                continue;
            }
            map.put(key, 1);
        }
    }

    private void onServerTickPost() {
    }
}

