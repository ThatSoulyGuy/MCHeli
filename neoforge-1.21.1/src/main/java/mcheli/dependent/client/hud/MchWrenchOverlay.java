package mcheli.dependent.client.hud;

import mcheli.MCHeli;
import mcheli.dependent.entity.AbstractMchVehicle;
import mcheli.dependent.item.MchWrench;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Draws the targeted vehicle's HP bar below the crosshair while the player holds the wrench and looks at an MCHeli
 * vehicle — the 1.21.1 port of {@code MCH_GuiWrench.drawHP}. First-person only, using the same 4-block ray-pick the
 * repair tick uses ({@link MchWrench#mouseOverVehicle}); the bar goes green above 30% HP, red below, with an {@code N %}
 * readout underneath. The colour constants are the reference's exact ARGB ints.
 */
@EventBusSubscriber(modid = MCHeli.MODID, value = Dist.CLIENT)
public final class MchWrenchOverlay {
    private MchWrenchOverlay() {}

    private static final int COLOR_HIGH = -14101432; // teal-green (HP fraction > 0.3)
    private static final int COLOR_LOW = -2161656;   // red
    private static final int COLOR_BG = -15433180;   // dark backing

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR,
            ResourceLocation.fromNamespaceAndPath(MCHeli.MODID, "wrench_hp"),
            MchWrenchOverlay::render);
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.options.hideGui || mc.options.getCameraType() != CameraType.FIRST_PERSON
            || !(player.getMainHandItem().getItem() instanceof MchWrench)) {
            return;
        }
        AbstractMchVehicle ac = MchWrench.mouseOverVehicle(player);
        if (ac == null || ac.getMaxHp() <= 0) {
            return;
        }
        int hp = ac.getHp();
        int maxHp = ac.getMaxHp();
        float hpp = Math.min(1.0F, (float) hp / maxHp);
        int color = (double) hp / maxHp > 0.3 ? COLOR_HIGH : COLOR_LOW;

        int cx = g.guiWidth() / 2;
        int posY = g.guiHeight() / 2 + 20; // reference posY = centerY + 20, bar sits below it
        g.fill(cx - 20, posY + 21, cx + 20, posY + 26, COLOR_BG);
        g.fill(cx - 19, posY + 22, cx - 19 + (int) (38.0F * hpp), posY + 25, color);

        int pct = (int) (hpp * 100.0F);
        if (hp < maxHp && pct >= 100) {
            pct = 99; // never show a full 100% while any damage remains (reference)
        }
        g.drawCenteredString(mc.font, String.format("%d %%", pct), cx, posY + 30, color);
    }
}
