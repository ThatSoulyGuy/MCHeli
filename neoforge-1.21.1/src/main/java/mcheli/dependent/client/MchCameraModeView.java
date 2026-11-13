package mcheli.dependent.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mcheli.MCHeli;
import mcheli.agnostic.aircraft.MCH_CameraMode;
import mcheli.dependent.entity.AbstractMchVehicle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

/**
 * The local viewer's camera mode on screen — the client half of the reference {@code MCH_Camera} night / thermal
 * vision. The mode itself is per seat on the vehicle (synced; switched by the camera-mode key, see
 * {@link AbstractMchVehicle#cameraModeKey}), and the server keeps the viewer under a night-vision effect in both vision
 * modes, which brightens the world exactly as the reference potion did. On top of that:
 * <ul>
 *   <li><b>Thermal vision</b> (mode 2) runs a full-screen post shader, as the reference's {@code activateShader} did.
 *       1.21 has no "pencil" program, so {@code mcheli:shaders/post/thermal.json} draws the scene's luminance as a
 *       white-hot greyscale with Sobel outlines.</li>
 *   <li><b>Night vision</b> (mode 1) adds the reference green sensor noise ({@code drawNightVisionNoise}: the
 *       {@code gui/alpha.png} sheet at a random offset, additive, tinted (0, 1, 0)), drawn by {@link #drawNoise}.</li>
 *   <li>In either vision mode, teammates blink an IR strobe ({@code MCH_ClientEventHook.renderIRStrobe}).</li>
 * </ul>
 */
@EventBusSubscriber(modid = MCHeli.MODID, value = Dist.CLIENT)
public final class MchCameraModeView {
    private MchCameraModeView() {}

    private static final ResourceLocation THERMAL = ResourceLocation.fromNamespaceAndPath(MCHeli.MODID, "shaders/post/thermal.json");
    private static final ResourceLocation NOISE = ResourceLocation.fromNamespaceAndPath(MCHeli.MODID, "textures/gui/alpha.png");
    private static final ResourceLocation IR_STROBE = ResourceLocation.fromNamespaceAndPath(MCHeli.MODID, "textures/ir_strobe.png");
    private static final RandomSource NOISE_RANDOM = RandomSource.create();

    /** Set while the thermal chain failed to load, so a broken shader warns once instead of every tick. */
    private static boolean thermalFailed;

    /** The camera mode of the seat the local player occupies (0 when not riding, or in a seat without a camera) —
     *  the reference {@code MCH_ClientCommonTickHandler.cameraMode}. */
    public static int localMode() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getVehicle() instanceof AbstractMchVehicle v) {
            return v.cameraMode(v.seatIndexOf(mc.player));
        }
        return MCH_CameraMode.NORMAL;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        GameRenderer gr = mc.gameRenderer;
        PostChain current = gr.currentEffect();
        boolean ours = current != null && THERMAL.toString().equals(current.getName());
        if (localMode() == MCH_CameraMode.THERMAL_VISION) {
            if (!ours && !thermalFailed) {
                gr.loadEffect(THERMAL); // reference MCH_Camera.setMode(2): W_EntityRenderer.activateShader("pencil")
                PostChain loaded = gr.currentEffect();
                thermalFailed = loaded == null || !THERMAL.toString().equals(loaded.getName());
            }
        } else {
            thermalFailed = false;
            if (ours) {
                // Reference deactivateShader, then give back any vanilla spectator effect (creeper/spider/enderman).
                gr.shutdownEffect();
                gr.checkEntityPostEffect(mc.getCameraEntity());
            }
        }
    }

    /** Reference {@code MCH_AircraftCommonGui.drawNightVisionNoise}: in night vision the whole screen gets the
     *  {@code alpha.png} noise sheet, offset by a random texel each frame, blended additively in green at 0.3. */
    public static void drawNoise(GuiGraphics g) {
        if (localMode() != MCH_CameraMode.NIGHT_VISION) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ONE,
            com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        g.setColor(0.0F, 1.0F, 0.0F, 0.3F);
        // One 256×256 sheet stretched over the screen, starting at a random texel (the texture repeats).
        g.blit(NOISE, 0, 0, g.guiWidth(), g.guiHeight(), NOISE_RANDOM.nextInt(256), NOISE_RANDOM.nextInt(256),
            256, 256, 256, 256);
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /**
     * Reference {@code MCH_ClientEventHook.renderIRStrobe}: while the local viewer is in a vision mode, every living
     * entity on the viewer's team flashes an IR beacon at three quarters of its height for 4 ticks in 20, full-bright,
     * brighter in night vision than in thermal.
     */
    @SubscribeEvent
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
        int mode = localMode();
        if (mode == MCH_CameraMode.NORMAL) {
            return;
        }
        LivingEntity entity = event.getEntity();
        int ticks = entity.tickCount % 20;
        Minecraft mc = Minecraft.getInstance();
        if (ticks >= 4 || mc.player == null || !mc.player.isAlliedTo(entity)) {
            return;
        }
        float alpha = (ticks == 1 || ticks == 2 ? 1.0F : 0.5F) * (mode == MCH_CameraMode.NIGHT_VISION ? 0.9F : 0.5F);
        int a = (int) (alpha * 255.0F);
        float half = (int) Math.max(entity.getBbWidth(), entity.getBbHeight()) * 20 * 0.08F;
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(0.0F, entity.getBbHeight() * 0.75F, 0.0F);
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        PoseStack.Pose last = pose.last();
        VertexConsumer vc = event.getMultiBufferSource().getBuffer(RenderType.entityTranslucent(IR_STROBE));
        int light = LightTexture.FULL_BRIGHT;
        vertex(vc, last, -half, -half, 0.0F, 1.0F, a, light);
        vertex(vc, last, -half, half, 0.0F, 0.0F, a, light);
        vertex(vc, last, half, half, 1.0F, 0.0F, a, light);
        vertex(vc, last, half, -half, 1.0F, 1.0F, a, light);
        pose.popPose();
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose last, float x, float y, float u, float v, int a, int light) {
        vc.addVertex(last, x, y, 0.0F).setColor(255, 255, 255, a).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light).setNormal(last, 0.0F, 0.0F, 1.0F);
    }
}
