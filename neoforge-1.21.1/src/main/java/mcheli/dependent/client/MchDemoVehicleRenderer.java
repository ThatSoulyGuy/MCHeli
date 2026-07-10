package mcheli.dependent.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import mcheli.dependent.entity.MchDemoVehicle;
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
 * Minimal visible renderer for the demo vehicle: a single flat metallic slab. The model is baked inline
 * (no separate layer-definition registration needed) and reuses a vanilla block texture so the slice
 * ships no binary assets. Cosmetic only — the movement pipeline is validated headlessly on the server.
 */
public class MchDemoVehicleRenderer extends EntityRenderer<MchDemoVehicle> {

    private static final ResourceLocation TEXTURE =
        ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/iron_block.png");

    private final ModelPart body;

    public MchDemoVehicleRenderer(EntityRendererProvider.Context context) {
        super(context);
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild(
            "body",
            CubeListBuilder.create().texOffs(0, 0).addBox(-12.0F, 0.0F, -12.0F, 24.0F, 12.0F, 24.0F),
            PartPose.ZERO);
        this.body = LayerDefinition.create(mesh, 64, 64).bakeRoot();
    }

    @Override
    public ResourceLocation getTextureLocation(MchDemoVehicle entity) {
        return TEXTURE;
    }

    @Override
    public void render(MchDemoVehicle entity, float entityYaw, float partialTick,
                       PoseStack pose, MultiBufferSource buffers, int packedLight) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-entityYaw));
        pose.scale(1.0F / 16.0F, 1.0F / 16.0F, 1.0F / 16.0F); // model is in pixel units
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        this.body.render(pose, consumer, packedLight, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        super.render(entity, entityYaw, partialTick, pose, buffers, packedLight);
    }
}
