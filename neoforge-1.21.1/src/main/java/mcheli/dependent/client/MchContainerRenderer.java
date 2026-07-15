package mcheli.dependent.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import mcheli.MCHeli;
import mcheli.agnostic.model.MchModel;
import mcheli.agnostic.spi.ModelHandle;
import mcheli.dependent.entity.MchContainer;
import mcheli.dependent.port.NeoResourceSource;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Renders a {@link MchContainer} as the bundled {@code models/container} model textured from
 * {@code mcheli:textures/container.png} — the port of {@code MCH_RenderContainer}. Opaque, normally lit, yaw-oriented.
 */
public class MchContainerRenderer extends EntityRenderer<MchContainer> {

    private static final ResourceLocation TEXTURE =
        ResourceLocation.fromNamespaceAndPath(MCHeli.MODID, "textures/container.png");

    public MchContainerRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.9F;
    }

    @Override public ResourceLocation getTextureLocation(MchContainer entity) { return TEXTURE; }

    @Override
    public void render(MchContainer entity, float entityYaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight) {
        ModelHandle h = new NeoResourceSource().loadModel("container");
        if (h instanceof MchModel model) {
            pose.pushPose();
            float yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
            pose.mulPose(Axis.YP.rotationDegrees(-yaw));
            MchModelRenderer.render(model, pose,
                buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)),
                packedLight, OverlayTexture.NO_OVERLAY, 255, 255, 255, 255);
            pose.popPose();
        }
        super.render(entity, entityYaw, partialTick, pose, buffers, packedLight);
    }
}
