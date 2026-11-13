package mcheli.dependent.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import mcheli.agnostic.model.MchModel;
import mcheli.agnostic.model.ModelFace;
import mcheli.agnostic.model.ModelGroup;
import mcheli.agnostic.model.ModelPartIndex;
import mcheli.agnostic.model.ModelVertex;

/**
 * Emits a parsed {@link MchModel} to a {@link VertexConsumer} — the 1.21.1 rewrite of the reference's
 * {@code W_Face.addFaceForRender} / {@code W_GroupObject.render} Tessellator path.
 *
 * <p>Faithful to the default render path where {@code textureOffset == 0}: the average-UV + per-vertex UV-offset
 * texel-bleed trick collapses to a no-op at offset 0, so it is omitted. Per-vertex smooth normals are used when the
 * parser produced them (always for {@code .mqo}; for {@code .obj} only the {@code V//VN}/{@code V/VT/VN} forms) and
 * the shade model is smooth (see {@link #shadeModel}); otherwise the flat face normal. Every face is emitted as a
 * QUAD because entity {@link net.minecraft.client.renderer.RenderType}s use {@code QUADS} mode — a triangle becomes a
 * degenerate quad by repeating its last vertex ({@code v0,v1,v2,v2}).
 *
 * <p>Part lookups go through a per-model {@link ModelPartIndex}, built on first use: a part name is a map lookup, not
 * a scan of every group (render thread only; the weak keys let a reloaded model's index go with it).
 */
public final class MchModelRenderer {
    private MchModelRenderer() {}

    private static final Map<MchModel, ModelPartIndex> INDEX = new WeakHashMap<>();
    /** The current shade model (the reference's {@code glShadeModel} state): smooth uses the parsed vertex normals. */
    private static boolean smooth = true;

    /** Set the shade model for the following draws and return the previous one, so a caller restores it — the port of
     *  {@code W_Render.setCommonRenderParam} / {@code restoreCommonRenderParam} switching {@code GL_SMOOTH}/{@code GL_FLAT}.
     *  Render thread only. Smooth is the default, so renderers that never call this are unchanged. */
    public static boolean shadeModel(boolean smoothShading) {
        boolean prev = smooth;
        smooth = smoothShading;
        return prev;
    }

    /** The cached group-name index of {@code model}. */
    public static ModelPartIndex index(MchModel model) {
        return INDEX.computeIfAbsent(model, ModelPartIndex::of);
    }

    public static void render(MchModel model, PoseStack pose, VertexConsumer consumer, int packedLight, int overlay,
                              int r, int g, int b, int a) {
        PoseStack.Pose last = pose.last();
        for (ModelGroup group : model.groups()) {
            emitGroup(last, consumer, group, packedLight, overlay, r, g, b, a);
        }
    }

    /** Render every group EXCEPT those whose (lower-cased) name is in {@code excludeLower} — used to draw the static
     *  hull while animated parts (rotor blades, etc.) are drawn separately with their own transforms. */
    public static void renderExcept(MchModel model, PoseStack pose, VertexConsumer consumer, int packedLight, int overlay,
                                    int r, int g, int b, int a, Set<String> excludeLower) {
        PoseStack.Pose last = pose.last();
        ModelPartIndex idx = index(model);
        List<ModelGroup> groups = model.groups();
        for (int i = 0; i < groups.size(); i++) {
            if (!excludeLower.contains(idx.lowerName(i))) {
                emitGroup(last, consumer, groups.get(i), packedLight, overlay, r, g, b, a);
            }
        }
    }

    /** Render a single named group (case-insensitive) under the CURRENT pose — the caller applies the part's animation
     *  transform (translate to hub, rotate about the axis) before calling. */
    public static void renderGroup(MchModel model, PoseStack pose, VertexConsumer consumer, int packedLight, int overlay,
                                   int r, int g, int b, int a, String groupName) {
        emitAll(model, index(model).exact(groupName), pose.last(), consumer, packedLight, overlay, r, g, b, a, Set.of());
    }

    /** True when the model contains a group with this (case-insensitive) name. */
    public static boolean hasGroup(MchModel model, String groupName) {
        return index(model).contains(groupName);
    }

    /**
     * Render a named part's full SPAN — the faithful port of the reference part primitive, which is what
     * "{@code renderPart($name)}" actually meant in 1.7.10 ({@link ModelPartIndex#span}): on an MQO, the matched
     * {@code $name} group plus its trailing non-{@code $} sub-meshes up to the next {@code $}-group, for EVERY match
     * (a tank turret is {@code $weapon0} + dozens of plain-named armour meshes after it); on an OBJ, the exact-named
     * groups only. The terminating {@code $}-group is not re-tested, exactly as the reference's loop skips it.
     */
    public static void renderPartSpan(MchModel model, PoseStack pose, VertexConsumer consumer, int packedLight, int overlay,
                                      int r, int g, int b, int a, String groupName) {
        emitAll(model, index(model).span(groupName), pose.last(), consumer, packedLight, overlay, r, g, b, a, Set.of());
    }

    /** {@link #renderPartSpan} that also leaves out the groups whose lower-cased name is in {@code skipLower}. No
     *  vehicle renderer skips a group any more (cockpit glass draws in the blended pass), so callers pass an empty set. */
    public static void renderPartSpan(MchModel model, PoseStack pose, VertexConsumer consumer, int packedLight, int overlay,
                                      int r, int g, int b, int a, String groupName, Set<String> skipLower) {
        emitAll(model, index(model).span(groupName), pose.last(), consumer, packedLight, overlay, r, g, b, a, skipLower);
    }

    private static void emitAll(MchModel model, int[] ids, PoseStack.Pose last, VertexConsumer consumer, int light,
                                int overlay, int r, int g, int b, int a, Set<String> skipLower) {
        if (ids.length == 0) {
            return;
        }
        List<ModelGroup> groups = model.groups();
        ModelPartIndex idx = skipLower.isEmpty() ? null : index(model);
        for (int id : ids) {
            if (idx == null || !skipLower.contains(idx.lowerName(id))) {
                emitGroup(last, consumer, groups.get(id), light, overlay, r, g, b, a);
            }
        }
    }

    private static void emitGroup(PoseStack.Pose last, VertexConsumer consumer, ModelGroup group, int light, int overlay,
                                  int r, int g, int b, int a) {
        for (ModelFace face : group.faces) {
            emitFace(last, consumer, face, light, overlay, r, g, b, a);
        }
    }

    private static void emitFace(PoseStack.Pose last, VertexConsumer c, ModelFace f, int light, int overlay,
                                 int r, int g, int b, int a) {
        int n = f.vertices.length;
        if (n < 3) {
            return;
        }
        for (int i = 0; i < 4; i++) {
            int idx = i < n ? i : n - 1; // triangle -> degenerate quad (repeat last vertex)
            emitVertex(last, c, f, idx, light, overlay, r, g, b, a);
        }
    }

    private static void emitVertex(PoseStack.Pose last, VertexConsumer c, ModelFace f, int idx, int light, int overlay,
                                   int r, int g, int b, int a) {
        ModelVertex v = f.vertices[idx];
        // Flat shading (GL_FLAT in the reference) gives the whole face one normal; smooth uses the parsed vertex normal.
        ModelVertex nrm = (smooth && f.vertexNormals != null && idx < f.vertexNormals.length && f.vertexNormals[idx] != null)
            ? f.vertexNormals[idx]
            : f.faceNormal;
        float u = 0.0F;
        float w = 0.0F;
        if (f.textureCoordinates != null && idx < f.textureCoordinates.length && f.textureCoordinates[idx] != null) {
            u = f.textureCoordinates[idx].u;
            w = f.textureCoordinates[idx].v;
        }
        float nx = nrm != null ? nrm.x : 0.0F;
        float ny = nrm != null ? nrm.y : 1.0F;
        float nz = nrm != null ? nrm.z : 0.0F;
        c.addVertex(last, v.x, v.y, v.z)
            .setColor(r, g, b, a)
            .setUv(u, w)
            .setOverlay(overlay)
            .setLight(light)
            .setNormal(last, nx, ny, nz);
    }
}
