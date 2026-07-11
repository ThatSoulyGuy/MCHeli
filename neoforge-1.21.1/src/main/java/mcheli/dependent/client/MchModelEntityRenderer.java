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

    protected final MchModel model;
    private final ResourceLocation defaultTexture;
    private final String textureDir; // e.g. "textures/helicopters/" — a selected skin becomes <dir><skin>.png
    private final java.util.Map<String, ResourceLocation> skinCache = new java.util.HashMap<>();

    protected MchModelEntityRenderer(EntityRendererProvider.Context context, String modelName, String texturePath) {
        super(context);
        this.model = load(modelName);
        this.defaultTexture = ResourceLocation.fromNamespaceAndPath("mcheli", texturePath);
        this.textureDir = texturePath.substring(0, texturePath.lastIndexOf('/') + 1);
        if (this.model == null) {
            LOGGER.warn("MCHeli renderer: model '{}' did not load; entity will be invisible", modelName);
        }
    }

    /** Resolve the entity's selected paint scheme to a texture ({@code <dir><skin>.png}), else the default. Cached.
     *  Skin 0 always uses the baked default texture — it is the guaranteed-present file, and the correct fallback if
     *  the config failed to load (the entity's info name is then a {@code demo_*} placeholder, not a real texture). */
    private ResourceLocation textureFor(T entity) {
        String skin = entity.getSkinIndex() == 0 ? null : entity.skinTextureName();
        if (skin == null || skin.isEmpty()) {
            return this.defaultTexture;
        }
        return this.skinCache.computeIfAbsent(skin,
            s -> ResourceLocation.fromNamespaceAndPath("mcheli", this.textureDir + s + ".png"));
    }

    private static MchModel load(String name) {
        ModelHandle h = new NeoResourceSource().loadModel(name);
        return h instanceof MchModel m ? m : null;
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return textureFor(entity);
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

            VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(textureFor(entity)));
            java.util.Set<String> dynamic = dynamicGroupsLower();
            if (dynamic.isEmpty()) {
                MchModelRenderer.render(this.model, pose, consumer, packedLight, OverlayTexture.NO_OVERLAY, 255, 255, 255, 255);
            } else {
                // Static hull (everything but the animated groups), then the moving parts with their own transforms.
                MchModelRenderer.renderExcept(this.model, pose, consumer, packedLight, OverlayTexture.NO_OVERLAY,
                    255, 255, 255, 255, dynamic);
                renderDynamicParts(entity, pose, consumer, packedLight, OverlayTexture.NO_OVERLAY, partialTick);
            }
            pose.popPose();
        }
        super.render(entity, entityYaw, partialTick, pose, buffers, packedLight);
    }

    /** Lower-cased names of the model groups a subclass animates in {@link #renderDynamicParts} (so they are excluded
     *  from the static hull pass). Empty by default → the whole model renders statically. */
    protected java.util.Set<String> dynamicGroupsLower() {
        return java.util.Collections.emptySet();
    }

    /** Draw the animated parts (rotor blades, wheels, turret, …). The {@code pose} is already at the entity and
     *  oriented (yaw/pitch/roll); apply each part's local transform, then {@link MchModelRenderer#renderGroup}. */
    protected void renderDynamicParts(T entity, PoseStack pose, VertexConsumer consumer, int packedLight, int overlay,
                                      float partialTick) {
    }

    /**
     * Short-path angle interpolation — the 1.21.1 port of {@code MCH_RenderAircraft.calcRot}. Both endpoints are
     * wrapped to [-180,180] ({@link Mth#wrapDegrees} == the reference's {@code wrapAngleTo180_float}); when they
     * straddle the seam, {@code prev} is shifted by ±360 so the interpolation always takes the short way round.
     */
    protected static float calcRot(float rot, float prevRot, float tickTime) {
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

