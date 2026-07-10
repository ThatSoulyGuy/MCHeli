package mcheli.dependent.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import mcheli.dependent.entity.MchDemoHeli;
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
import net.minecraft.util.Mth;

/**
 * Minimal visible renderer for the demo helicopter: a stubby fuselage plus a rotor bar that spins over the top.
 * Like {@link MchDemoVehicleRenderer} the geometry is baked inline and reuses a vanilla block texture, so the
 * slice ships no binary assets. It also applies the entity's pitch and MCHeli custom roll so the flight attitude
 * is visible. Cosmetic only — the movement pipeline is validated headlessly on the server.
 */
public class MchDemoHeliRenderer extends EntityRenderer<MchDemoHeli> {

    private static final ResourceLocation TEXTURE =
        ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/gold_block.png");

    private final ModelPart body;
    private final ModelPart rotor;

    public MchDemoHeliRenderer(EntityRendererProvider.Context context) {
        super(context);
        MeshDefinition bodyMesh = new MeshDefinition();
        bodyMesh.getRoot().addOrReplaceChild(
            "body",
            CubeListBuilder.create().texOffs(0, 0).addBox(-6.0F, 0.0F, -16.0F, 12.0F, 8.0F, 32.0F),
            PartPose.ZERO);
        this.body = LayerDefinition.create(bodyMesh, 128, 128).bakeRoot();

        MeshDefinition rotorMesh = new MeshDefinition();
        rotorMesh.getRoot().addOrReplaceChild(
            "rotor",
            CubeListBuilder.create().texOffs(0, 0).addBox(-20.0F, 0.0F, -1.0F, 40.0F, 1.0F, 2.0F),
            PartPose.ZERO);
        this.rotor = LayerDefinition.create(rotorMesh, 128, 128).bakeRoot();
    }

    @Override
    public ResourceLocation getTextureLocation(MchDemoHeli entity) {
        return TEXTURE;
    }

    @Override
    public void render(MchDemoHeli entity, float entityYaw, float partialTick,
                       PoseStack pose, MultiBufferSource buffers, int packedLight) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-entityYaw));
        // Flight attitude: pitch about X, MCHeli custom roll about Z (interpolated for smoothness).
        float pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
        float roll = Mth.lerp(partialTick, entity.getPrevRollAngle(), entity.getRollAngle());
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.mulPose(Axis.ZP.rotationDegrees(roll));
        pose.scale(1.0F / 16.0F, 1.0F / 16.0F, 1.0F / 16.0F); // model is in pixel units

        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        this.body.render(pose, consumer, packedLight, OverlayTexture.NO_OVERLAY);

        // Spinning rotor above the fuselage — a lively cue that the sim is running (purely time-driven).
        pose.pushPose();
        pose.translate(0.0F, 9.0F, 0.0F);
        pose.mulPose(Axis.YP.rotationDegrees((entity.tickCount + partialTick) * 36.0F));
        this.rotor.render(pose, consumer, packedLight, OverlayTexture.NO_OVERLAY);
        pose.popPose();

        pose.popPose();
        super.render(entity, entityYaw, partialTick, pose, buffers, packedLight);
    }
}
