package mcheli.dependent.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import mcheli.dependent.entity.MchDemoPlane;
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
 * Minimal visible renderer for the demo plane: a fuselage with wings and a tail fin, baked inline and reusing a
 * vanilla block texture (no shipped assets). Applies the entity's pitch and MCHeli custom roll so the flight
 * attitude is visible. Cosmetic only — the movement pipeline is validated headlessly on the server.
 */
public class MchDemoPlaneRenderer extends EntityRenderer<MchDemoPlane> {

    private static final ResourceLocation TEXTURE =
        ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/diamond_block.png");

    private final ModelPart body;

    public MchDemoPlaneRenderer(EntityRendererProvider.Context context) {
        super(context);
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild(
            "body",
            CubeListBuilder.create()
                .texOffs(0, 0).addBox(-2.0F, 0.0F, -16.0F, 4.0F, 4.0F, 32.0F)   // fuselage
                .texOffs(0, 0).addBox(-18.0F, 1.0F, -4.0F, 36.0F, 1.0F, 8.0F)   // wings
                .texOffs(0, 0).addBox(-1.0F, 4.0F, 10.0F, 2.0F, 6.0F, 4.0F),    // tail fin
            PartPose.ZERO);
        this.body = LayerDefinition.create(mesh, 128, 128).bakeRoot();
    }

    @Override
    public ResourceLocation getTextureLocation(MchDemoPlane entity) {
        return TEXTURE;
    }

    @Override
    public void render(MchDemoPlane entity, float entityYaw, float partialTick,
                       PoseStack pose, MultiBufferSource buffers, int packedLight) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-entityYaw));
        float pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
        float roll = Mth.lerp(partialTick, entity.getPrevRollAngle(), entity.getRollAngle());
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.mulPose(Axis.ZP.rotationDegrees(roll));
        pose.scale(1.0F / 16.0F, 1.0F / 16.0F, 1.0F / 16.0F);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        this.body.render(pose, consumer, packedLight, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        super.render(entity, entityYaw, partialTick, pose, buffers, packedLight);
    }
}
