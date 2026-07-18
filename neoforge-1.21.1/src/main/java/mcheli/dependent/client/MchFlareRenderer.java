package mcheli.dependent.client;

import mcheli.dependent.entity.MchFlare;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * Renderer for {@link MchFlare}. The flare's entire visual is its client-tick particle stream (a bright flame + smoke
 * trail, and a smoke burst on an airburst death), so this renderer draws no geometry — it exists only to satisfy the
 * EntityType→renderer binding. The base {@link EntityRenderer#render} draws nothing for a nameless entity.
 */
public class MchFlareRenderer extends EntityRenderer<MchFlare> {

    public MchFlareRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public ResourceLocation getTextureLocation(MchFlare entity) {
        return ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    }
}
