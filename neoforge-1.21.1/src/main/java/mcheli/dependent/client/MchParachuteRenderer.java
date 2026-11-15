package mcheli.dependent.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import mcheli.MCHeli;
import mcheli.agnostic.model.MchModel;
import mcheli.agnostic.parachute.ParachuteFlight;
import mcheli.agnostic.spi.ModelHandle;
import mcheli.dependent.config.MchConfig;
import mcheli.dependent.entity.MchParachute;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Draws a {@link MchParachute}, the port of {@code parachute/MCH_RenderParachute.doRender} (:21-71): type 1 the whole
 * {@code parachute1} model, type 2 the {@code parachute2} span {@code $parachute} once open and {@code $seat} before,
 * type 3 the {@code $parachute} span; type 0 (and any other value) nothing. The origin is the 1.7.10 {@code posY}
 * (the box bottom + 0.3), the yaw is interpolated, and the model is tinted 0.75 grey in one blended pass with back-face
 * culling ({@link MchRenderTypes#vehicle}, the reference {@code GL_CULL_FACE} and {@code SRC_ALPHA,
 * ONE_MINUS_SRC_ALPHA}), smooth-shaded when the client {@code smoothShading} option is on. The rider renders itself.
 */
public class MchParachuteRenderer extends EntityRenderer<MchParachute> {

    private static final ResourceLocation TEXTURE_1 =
        ResourceLocation.fromNamespaceAndPath(MCHeli.MODID, "textures/parachute1.png");
    private static final ResourceLocation TEXTURE_2 =
        ResourceLocation.fromNamespaceAndPath(MCHeli.MODID, "textures/parachute2.png");
    /** Reference {@code glColor4f(0.75, 0.75, 0.75, 1)}. */
    private static final int TINT = 191;

    public MchParachuteRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.5F; // reference shadowSize
    }

    @Override
    public ResourceLocation getTextureLocation(MchParachute entity) {
        return entity.parachuteType() == ParachuteFlight.TYPE_HAND ? TEXTURE_1 : TEXTURE_2;
    }

    @Override
    public void render(MchParachute entity, float entityYaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight) {
        int type = entity.parachuteType();
        if (type == ParachuteFlight.TYPE_HAND || type == ParachuteFlight.TYPE_SEAT
                || type == ParachuteFlight.TYPE_DROP) {
            ModelHandle h = mcheli.dependent.port.MchContentPacks.resources()
                .loadModel(type == ParachuteFlight.TYPE_HAND ? "parachute1" : "parachute2");
            if (h instanceof MchModel model) {
                pose.pushPose();
                pose.translate(0.0, MchParachute.Y_OFFSET, 0.0);
                float yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot()); // reference 360 unwrap :66-73
                pose.mulPose(Axis.YP.rotationDegrees(-yaw));                         // glRotatef(yaw, 0, -1, 0)
                VertexConsumer vc = buffers.getBuffer(MchRenderTypes.vehicle(getTextureLocation(entity)));
                boolean prevShade = MchModelRenderer.shadeModel(MchConfig.CLIENT.smoothShading());
                try {
                    if (type == ParachuteFlight.TYPE_HAND) {
                        MchModelRenderer.render(model, pose, vc, packedLight, OverlayTexture.NO_OVERLAY,
                            TINT, TINT, TINT, 255);
                    } else {
                        String part = type == ParachuteFlight.TYPE_SEAT && !entity.isOpenParachute()
                            ? "$seat" : "$parachute";
                        MchModelRenderer.renderPartSpan(model, pose, vc, packedLight, OverlayTexture.NO_OVERLAY,
                            TINT, TINT, TINT, 255, part);
                    }
                } finally {
                    MchModelRenderer.shadeModel(prevShade);
                }
                pose.popPose();
            }
        }
        super.render(entity, entityYaw, partialTick, pose, buffers, packedLight);
    }
}
