package mcheli.dependent.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.function.Function;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * MCHeli's own render types, built from vanilla state shards (no custom GL state or shaders).
 */
public final class MchRenderTypes {
    private MchRenderTypes() {}

    /**
     * The vehicle model pass: vanilla {@code entity_translucent_cull} (alpha blend, back-face cull, depth write, entity
     * lighting and overlay) WITHOUT {@code sortOnUpload}. That is the reference technique exactly
     * ({@code W_Render.setCommonRenderParam}: {@code GL_BLEND} with {@code SRC_ALPHA, ONE_MINUS_SRC_ALPHA}, cull on,
     * depth write on, faces in model order), so cockpit glass blends from its texture alpha as it did in 1.7.10. The
     * vanilla type re-sorted every quad of every vehicle sharing a texture by distance on the CPU each frame; the
     * reference never sorted, and its models are authored for model order (interior meshes before the glass, the
     * canopy part drawn last).
     */
    private static final Function<ResourceLocation, RenderType> VEHICLE = Util.memoize(tex -> RenderType.create(
        "mcheli_vehicle", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, true, false,
        RenderType.CompositeState.builder()
            .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_CULL_SHADER)
            .setTextureState(new RenderStateShard.TextureStateShard(tex, false, false))
            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
            .setLightmapState(RenderStateShard.LIGHTMAP)
            .setOverlayState(RenderStateShard.OVERLAY)
            .createCompositeState(true)));

    /**
     * The search-light beam (reference {@code MCH_RenderAircraft.renderLight}): untextured vertex colour, additive
     * {@code SRC_ALPHA, ONE} blending, no culling and no depth write — the reference's
     * {@code glDisable(GL_TEXTURE_2D/GL_CULL_FACE)}, {@code glBlendFunc(770, 1)}, {@code glDepthMask(false)}. The fan
     * is emitted as triangles so batches of several beams can share one buffer. Additive blending is
     * order-independent, so it needs no sorting.
     */
    public static final RenderType SEARCH_LIGHT = RenderType.create(
        "mcheli_search_light", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES, 1536, false, false,
        RenderType.CompositeState.builder()
            .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
            .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
            .setCullState(RenderStateShard.NO_CULL)
            .setWriteMaskState(RenderStateShard.COLOR_WRITE)
            .createCompositeState(false));

    public static RenderType vehicle(ResourceLocation texture) {
        return VEHICLE.apply(texture);
    }
}
