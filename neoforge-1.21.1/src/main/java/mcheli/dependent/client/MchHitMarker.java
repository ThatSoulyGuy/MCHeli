package mcheli.dependent.client;

import mcheli.MCHeli;
import mcheli.dependent.config.MchConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * CLIENT-ONLY weapon hit marker — the port of {@code MCH_GuiCommon.hitBullet}/{@code drawHitBullet}
 * ({@code MCH_AircraftCommonGui:62-93}). A {@code ClientboundHitMarkPayload} sets a 15-tick counter; while it runs, four
 * short diagonal strokes frame the screen centre (an "X" with an open middle, from 10 to 5 GUI pixels out), fading as
 * the counter falls: alpha = {@code count * (256 / 15) * HitMarkColor alpha}, colour = the {@code HitMarkColor} RGB. As
 * in the reference (whose common GUI drew it for every player) it shows whether or not the player is seated. Visual
 * only: the reference plays no sound for a hit.
 */
@EventBusSubscriber(modid = MCHeli.MODID, value = Dist.CLIENT)
public final class MchHitMarker {
    private MchHitMarker() {}

    /** Reference {@code getMaxHitStatus}: the marker lasts 15 ticks. */
    private static final int MAX_COUNT = 15;
    private static int count;

    /** A hit was reported: restart the marker (reference {@code hitBullet}). */
    public static void hit() {
        count = MAX_COUNT;
    }

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR,
            ResourceLocation.fromNamespaceAndPath(MCHeli.MODID, "hit_marker"), MchHitMarker::render);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (count > 0) {
            count--;
        }
    }

    private static void render(GuiGraphics g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (count <= 0 || mc.options.hideGui) {
            return;
        }
        int cfg = MchConfig.CLIENT.hitMarkColor();
        float cfgAlpha = (cfg >>> 24) / 255.0F;
        int alpha = (int) (cfgAlpha * (count * (256 / MAX_COUNT)));
        if (alpha <= 0) {
            return;
        }
        int color = Math.min(alpha, 255) << 24 | (cfg & 0xFFFFFF);
        int cx = g.guiWidth() / 2;
        int cy = g.guiHeight() / 2;
        // Reference strokes: (cx-10,cy-10)->(cx-5,cy-5), (cx-10,cy+10)->(cx-5,cy+5), and their mirrors on the right.
        for (int i = 5; i <= 10; i++) {
            g.fill(cx - i, cy - i, cx - i + 1, cy - i + 1, color);
            g.fill(cx - i, cy + i, cx - i + 1, cy + i + 1, color);
            g.fill(cx + i, cy - i, cx + i + 1, cy - i + 1, color);
            g.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, color);
        }
    }
}
