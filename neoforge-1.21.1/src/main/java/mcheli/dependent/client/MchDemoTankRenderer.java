package mcheli.dependent.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import mcheli.dependent.entity.MchDemoTank;
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
 * Minimal visible renderer for the demo tank: a hull with a turret and gun barrel, baked inline and reusing a
 * vanilla block texture (no shipped assets). Applies the entity's pitch and MCHeli custom roll. Cosmetic only —
 * the movement pipeline is validated headlessly on the server.
 */
public class MchDemoTankRenderer extends EntityRenderer<MchDemoTank> {

    private static final ResourceLocation TEXTURE =
        ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/deepslate_tiles.png");

    private final ModelPart body;

    public MchDemoTankRenderer(EntityRendererProvider.Context context) {
        super(context);
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild(
            "body",
            CubeListBuilder.create()
                .texOffs(0, 0).addBox(-8.0F, 0.0F, -12.0F, 16.0F, 6.0F, 24.0F)  // hull
                .texOffs(0, 0).addBox(-5.0F, 6.0F, -5.0F, 10.0F, 5.0F, 10.0F)   // turret
                .texOffs(0, 0).addBox(-1.0F, 8.0F, 5.0F, 2.0F, 2.0F, 14.0F),    // barrel
            PartPose.ZERO);
        this.body = LayerDefinition.create(mesh, 128, 128).bakeRoot();
    }

    @Override
    public ResourceLocation getTextureLocation(MchDemoTank entity) {
        return TEXTURE;
    }

    @Override
    public void render(MchDemoTank entity, float entityYaw, float partialTick,
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
