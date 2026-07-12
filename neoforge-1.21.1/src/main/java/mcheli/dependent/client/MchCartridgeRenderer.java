package mcheli.dependent.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import mcheli.MCHeli;
import mcheli.agnostic.model.MchModel;
import mcheli.agnostic.spi.ModelHandle;
import mcheli.dependent.entity.MchCartridge;
import mcheli.dependent.port.NeoResourceSource;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Renders an {@link MchCartridge} as its config model: {@code models/bullets/<name>} textured from
 * {@code mcheli:textures/bullets/<name>.png}, uniformly scaled by the config {@code scale}, tumbling (yaw negated per
 * the reference {@code MCH_RenderCartridge}). Unlike the bullet tracer this is an opaque, <b>normally-lit</b>,
 * <b>untinted</b> 3D model — the brass colour comes from the texture, and cartridges carry no colour config.
 */
public class MchCartridgeRenderer extends EntityRenderer<MchCartridge> {

    public MchCartridgeRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(MchCartridge entity) {
        return ResourceLocation.fromNamespaceAndPath(MCHeli.MODID, "textures/bullets/" + entity.cartridgeName() + ".png");
    }

    @Override
    public void render(MchCartridge entity, float entityYaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight) {
        String name = entity.cartridgeName();
        ModelHandle h = name.isEmpty() ? null : new NeoResourceSource().loadModel("bullets/" + name);
        if (h instanceof MchModel model) {
            pose.pushPose();
            float scale = entity.cartridgeScale();
            pose.scale(scale, scale, scale);
            float yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
            float pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
            pose.mulPose(Axis.YP.rotationDegrees(-yaw)); // reference negates the render yaw
            pose.mulPose(Axis.XP.rotationDegrees(pitch));
            MchModelRenderer.render(model, pose, buffers.getBuffer(RenderType.entityCutoutNoCull(getTextureLocation(entity))),
                packedLight, OverlayTexture.NO_OVERLAY, 255, 255, 255, 255);
            pose.popPose();
        }
        super.render(entity, entityYaw, partialTick, pose, buffers, packedLight);
    }
}
