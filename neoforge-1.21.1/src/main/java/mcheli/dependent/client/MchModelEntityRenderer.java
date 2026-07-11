package mcheli.dependent.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import mcheli.agnostic.model.MchModel;
import mcheli.agnostic.spi.ModelHandle;
import mcheli.dependent.entity.AbstractMchVehicle;
import mcheli.dependent.port.NeoResourceSource;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.slf4j.Logger;

/**
 * Base renderer that draws a real MCHeli {@code .mqo}/{@code .obj} model for a demo vehicle — the 1.21.1 rewrite of
 * {@code MCH_RenderAircraft}/{@code MCH_RenderHeli}'s core body render. The model is resolved once (by the reference
 * {@code <category>/<name>} convention) through the agnostic parsers and emitted via {@link MchModelRenderer}.
 *
 * <p>Transform mirrors the reference exactly: the {@link PoseStack} is already at the entity, so it applies only
 * {@code yaw} about {@code -Y}, {@code pitch} about {@code X}, {@code roll} about {@code Z} — and NO scale, because
 * the {@code .mqo} parser already divides vertices by 100 into world units. Per-part animation (rotor/wheels/turret)
 * is a later increment; for now every group renders in its rest pose, which still shows the complete vehicle.
 */
public abstract class MchModelEntityRenderer<T extends AbstractMchVehicle> extends EntityRenderer<T> {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final MchModel model;
    private final ResourceLocation texture;

    protected MchModelEntityRenderer(EntityRendererProvider.Context context, String modelName, String texturePath) {
        super(context);
        this.model = load(modelName);
        this.texture = ResourceLocation.fromNamespaceAndPath("mcheli", texturePath);
        if (this.model == null) {
            LOGGER.warn("MCHeli renderer: model '{}' did not load; entity will be invisible", modelName);
        }
    }

    private static MchModel load(String name) {
        ModelHandle h = new NeoResourceSource().loadModel(name);
        return h instanceof MchModel m ? m : null;
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return this.texture;
    }

    @Override
    public void render(T entity, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int packedLight) {
        if (this.model != null) {
            pose.pushPose();
            // Reference order: yaw about -Y, pitch about X, roll about Z (MCH_RenderHeli.renderAircraft). No scale.
            pose.mulPose(Axis.YP.rotationDegrees(-entityYaw));
            // Pitch is a plain lerp (matches MCH_RenderAircraft.calcRotPitch; pitch is clamped, never crosses the
            // seam). Roll uses the short-path calcRot: roll IS wrapped to [-180,180] (RotationSolver), so a barrel
            // roll can jump +179 -> -179 between ticks; a plain lerp would spin ~358 deg for one frame.
            float pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
            float roll = calcRot(entity.getRollAngle(), entity.getPrevRollAngle(), partialTick);
            pose.mulPose(Axis.XP.rotationDegrees(pitch));
            pose.mulPose(Axis.ZP.rotationDegrees(roll));

            VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(this.texture));
            MchModelRenderer.render(this.model, pose, consumer, packedLight, OverlayTexture.NO_OVERLAY, 255, 255, 255, 255);
            pose.popPose();
        }
        super.render(entity, entityYaw, partialTick, pose, buffers, packedLight);
    }

    /**
     * Short-path angle interpolation — the 1.21.1 port of {@code MCH_RenderAircraft.calcRot}. Both endpoints are
     * wrapped to [-180,180] ({@link Mth#wrapDegrees} == the reference's {@code wrapAngleTo180_float}); when they
     * straddle the seam, {@code prev} is shifted by ±360 so the interpolation always takes the short way round.
     */
    private static float calcRot(float rot, float prevRot, float tickTime) {
        rot = Mth.wrapDegrees(rot);
        prevRot = Mth.wrapDegrees(prevRot);
        if (rot - prevRot < -180.0F) {
            prevRot -= 360.0F;
        } else if (prevRot - rot < -180.0F) {
            prevRot += 360.0F;
        }
        return prevRot + (rot - prevRot) * tickTime;
    }
}

