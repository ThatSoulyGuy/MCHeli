package mcheli.dependent.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import mcheli.MCHeli;
import mcheli.agnostic.model.MchModel;
import mcheli.agnostic.spi.ModelHandle;
import mcheli.dependent.entity.MchBullet;
import mcheli.dependent.port.NeoResourceSource;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders a {@link MchBullet} as its <b>configured bullet model</b>: the {@code .mqo}/{@code .obj} named by the firing
 * weapon's {@code ModelBullet=} (or the per-type default — bullet / rocket / aamissile / …), loaded through the shared
 * {@link NeoResourceSource} cache and textured from {@code mcheli:textures/bullets/<name>.png}. The model is oriented
 * along the bullet's flight ({@code -yaw} then {@code +pitch}, no scale — matching the reference {@code MCH_RenderBullet}
 * and the mqo parser's built-in /100 scale) and tinted by the weapon's colour (opaque white = untinted).
 *
 * <p>If the model can't be resolved (empty/unknown name, or a parse miss) it falls back to a small full-bright tracer
 * cube so the projectile is never invisible.
 */
public class MchBulletRenderer extends EntityRenderer<MchBullet> {

    private static final ResourceLocation TRACER_TEXTURE =
        ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/glowstone.png");

    private final ModelPart tracer;

    public MchBulletRenderer(EntityRendererProvider.Context context) {
        super(context);
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild(
            "tracer", CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -3.0F, -3.0F, 6.0F, 6.0F, 6.0F), PartPose.ZERO);
        this.tracer = LayerDefinition.create(mesh, 16, 16).bakeRoot();
    }

    @Override
    public ResourceLocation getTextureLocation(MchBullet entity) {
        String name = entity.bulletModelName();
        return name.isEmpty()
            ? TRACER_TEXTURE
            : ResourceLocation.fromNamespaceAndPath(MCHeli.MODID, "textures/bullets/" + name + ".png");
    }

    @Override
    public void render(MchBullet entity, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int packedLight) {
        String name = entity.bulletModelName();
        MchModel model = resolve(name);

        pose.pushPose();
        // Orient the model +Z along the flight path (yaw/pitch are kept current from the motion each tick).
        float yaw = entity.getYRot();
        float pitch = entity.getXRot();
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        pose.mulPose(Axis.XP.rotationDegrees(pitch));

        if (model != null) {
            int color = entity.bulletColor();
            int a = (color >>> 24) & 0xFF;
            int r = (color >>> 16) & 0xFF;
            int g = (color >>> 8) & 0xFF;
            int b = color & 0xFF;
            ResourceLocation tex = ResourceLocation.fromNamespaceAndPath(MCHeli.MODID, "textures/bullets/" + name + ".png");
            MchModelRenderer.render(model, pose, buffers.getBuffer(RenderType.entityCutoutNoCull(tex)),
                0xF000F0 /* full-bright */, OverlayTexture.NO_OVERLAY, r, g, b, a);
        } else {
            // Fallback tracer: a tiny full-bright cube so an unresolved model still reads as a streak.
            pose.scale(1.0F / 16.0F, 1.0F / 16.0F, 1.0F / 16.0F);
            this.tracer.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TRACER_TEXTURE)),
                0xF000F0, OverlayTexture.NO_OVERLAY);
        }
        pose.popPose();
        super.render(entity, entityYaw, partialTick, pose, buffers, packedLight);
    }

    /** Resolve the bullet model by name via the shared cache, or null if the name is empty / unparseable. */
    private static MchModel resolve(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        ModelHandle h = new NeoResourceSource().loadModel("bullets/" + name);
        return h instanceof MchModel m ? m : null;
    }
}
