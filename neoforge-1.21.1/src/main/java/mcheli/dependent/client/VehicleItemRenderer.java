package mcheli.dependent.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Map;
import mcheli.agnostic.model.MchModel;
import mcheli.agnostic.spi.ModelHandle;
import mcheli.dependent.item.VehicleSpawnItem;
import mcheli.dependent.port.NeoResourceSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Renders each {@link VehicleSpawnItem}'s inventory/creative icon as the vehicle's actual 3D model — the config-driven
 * "actual icon" (any future vehicle that ships a config + model gets a correct icon with no hand-drawn sprite). Wired
 * via {@code IClientItemExtensions.getCustomRenderer} + a {@code builtin/entity} item model. The model is fit into the
 * item cell (scaled by its largest dimension, centered); the item model JSON's {@code display} block supplies the
 * viewing angle.
 */
public class VehicleItemRenderer extends BlockEntityWithoutLevelRenderer {

    private final Map<String, MchModel> modelCache = new HashMap<>();
    private final Map<String, ResourceLocation> texCache = new HashMap<>();

    public VehicleItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack pose, MultiBufferSource buffers,
                             int light, int overlay) {
        if (!(stack.getItem() instanceof VehicleSpawnItem item)) {
            return;
        }
        String path = item.category.dir + "/" + item.configName;
        MchModel model;
        if (this.modelCache.containsKey(path)) {
            model = this.modelCache.get(path); // may be null (negative cache) — don't re-parse a failed icon every frame
        } else {
            ModelHandle h = new NeoResourceSource().loadModel(path);
            model = h instanceof MchModel m ? m : null;
            this.modelCache.put(path, model);
        }
        if (model == null) {
            return;
        }
        float sx = model.maxX - model.minX;
        float sy = model.maxY - model.minY;
        float sz = model.maxZ - model.minZ;
        float max = Math.max(sx, Math.max(sy, sz));
        if (max < 0.01F) {
            max = 1.0F;
        }
        ResourceLocation tex = this.texCache.computeIfAbsent(path,
            p -> ResourceLocation.fromNamespaceAndPath("mcheli", "textures/" + p + ".png"));

        pose.pushPose();
        pose.translate(0.5F, 0.5F, 0.5F);         // centre of the item cell
        float s = 0.95F / max;                     // fit the largest dimension into the cell
        pose.scale(s, s, s);
        pose.translate(-(model.minX + model.maxX) / 2.0F,   // centre the model geometry on the origin
                       -(model.minY + model.maxY) / 2.0F,
                       -(model.minZ + model.maxZ) / 2.0F);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(tex));
        MchModelRenderer.render(model, pose, consumer, light, OverlayTexture.NO_OVERLAY, 255, 255, 255, 255);
        pose.popPose();
    }
}
