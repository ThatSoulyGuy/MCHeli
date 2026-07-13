package mcheli.dependent.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import mcheli.agnostic.model.MchModel;
import mcheli.agnostic.model.ModelFace;
import mcheli.agnostic.model.ModelGroup;
import mcheli.agnostic.model.ModelVertex;
import mcheli.agnostic.model.MqoModel;

/**
 * Emits a parsed {@link MchModel} to a {@link VertexConsumer} — the 1.21.1 rewrite of the reference's
 * {@code W_Face.addFaceForRender} / {@code W_GroupObject.render} Tessellator path.
 *
 * <p>Faithful to the default render path where {@code textureOffset == 0}: the average-UV + per-vertex UV-offset
 * texel-bleed trick collapses to a no-op at offset 0, so it is omitted. Per-vertex smooth normals are used when the
 * parser produced them (always for {@code .mqo}; for {@code .obj} only the {@code V//VN}/{@code V/VT/VN} forms),
 * otherwise the flat face normal. Every face is emitted as a QUAD because entity {@link
 * net.minecraft.client.renderer.RenderType}s use {@code QUADS} mode — a triangle becomes a degenerate quad by
 * repeating its last vertex ({@code v0,v1,v2,v2}).
 */
public final class MchModelRenderer {
    private MchModelRenderer() {}

    public static void render(MchModel model, PoseStack pose, VertexConsumer consumer, int packedLight, int overlay,
                              int r, int g, int b, int a) {
        PoseStack.Pose last = pose.last();
        for (ModelGroup group : model.groups()) {
            if (group == null) {
                continue; // .obj can append a null trailing group for an empty file (faithful upstream edge)
            }
            for (ModelFace face : group.faces) {
                emitFace(last, consumer, face, packedLight, overlay, r, g, b, a);
            }
        }
    }

    /** Render every group EXCEPT those whose (lower-cased) name is in {@code excludeLower} — used to draw the static
     *  hull while animated parts (rotor blades, etc.) are drawn separately with their own transforms. */
    public static void renderExcept(MchModel model, PoseStack pose, VertexConsumer consumer, int packedLight, int overlay,
                                    int r, int g, int b, int a, Set<String> excludeLower) {
        PoseStack.Pose last = pose.last();
        for (ModelGroup group : model.groups()) {
            if (group == null || excludeLower.contains(group.name.toLowerCase(Locale.ROOT))) {
                continue;
            }
            for (ModelFace face : group.faces) {
                emitFace(last, consumer, face, packedLight, overlay, r, g, b, a);
            }
        }
    }

    /** Render a single named group (case-insensitive) under the CURRENT pose — the caller applies the part's animation
     *  transform (translate to hub, rotate about the axis) before calling. */
    public static void renderGroup(MchModel model, PoseStack pose, VertexConsumer consumer, int packedLight, int overlay,
                                   int r, int g, int b, int a, String groupName) {
        PoseStack.Pose last = pose.last();
        for (ModelGroup group : model.groups()) {
            if (group != null && group.name.equalsIgnoreCase(groupName)) {
                for (ModelFace face : group.faces) {
                    emitFace(last, consumer, face, packedLight, overlay, r, g, b, a);
                }
            }
        }
    }

    /** True when the model contains a group with this (case-insensitive) name. */
    public static boolean hasGroup(MchModel model, String groupName) {
        for (ModelGroup group : model.groups()) {
            if (group != null && group.name.equalsIgnoreCase(groupName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Render a named part's full SPAN — the faithful port of the reference part primitive, which is what
     * "{@code renderPart($name)}" actually meant in 1.7.10:
     * <ul>
     *   <li><b>MQO</b> ({@code W_MetasequoiaObject.renderPart}): the matched {@code $name} group PLUS every FOLLOWING
     *       group whose name does NOT start with {@code $}, stopping at the next {@code $}-group. An MQO "part" is the
     *       {@code $name} header + its trailing non-{@code $} sub-meshes (a tank turret is {@code $weapon0} + dozens of
     *       plain-named armour meshes after it).</li>
     *   <li><b>OBJ</b> ({@code W_WavefrontObject.renderPart}): the exact-named group only — no trailing span.</li>
     * </ul>
     * First match wins (as in the reference). {@code skipLower} groups are never drawn (the port's glass/canopy
     * view fix); pass an empty set when no skip applies.
     */
    public static void renderPartSpan(MchModel model, PoseStack pose, VertexConsumer consumer, int packedLight, int overlay,
                                      int r, int g, int b, int a, String groupName, Set<String> skipLower) {
        PoseStack.Pose last = pose.last();
        List<ModelGroup> groups = model.groups();
        // Span semantics apply on an MQO part whose name is $-prefixed (W_MetasequoiaObject.renderPart gates on
        // charAt(0)=='$'); an OBJ, or a non-$ name, is exact-name only. EVERY match renders (the reference's outer
        // loop keeps scanning after a span ends, so duplicate part names each draw their own span).
        boolean span = model instanceof MqoModel && groupName.startsWith("$");
        for (int i = 0; i < groups.size(); i++) {
            ModelGroup group = groups.get(i);
            if (group == null || !group.name.equalsIgnoreCase(groupName)) {
                continue;
            }
            emitGroup(last, consumer, group, packedLight, overlay, r, g, b, a, skipLower);
            if (span) {
                int j = i + 1;
                for (; j < groups.size(); j++) {
                    ModelGroup next = groups.get(j);
                    if (next == null) {
                        continue;
                    }
                    if (next.name.startsWith("$")) {
                        break; // the next $-part begins -> this part's span ends
                    }
                    emitGroup(last, consumer, next, packedLight, overlay, r, g, b, a, skipLower);
                }
                i = j - 1; // resume the scan AT the terminating $-group (it may itself match again)
            }
        }
    }

    private static void emitGroup(PoseStack.Pose last, VertexConsumer consumer, ModelGroup group, int light, int overlay,
                                  int r, int g, int b, int a, Set<String> skipLower) {
        if (!skipLower.isEmpty() && skipLower.contains(group.name.toLowerCase(Locale.ROOT))) {
            return;
        }
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
        ModelVertex nrm = (f.vertexNormals != null && idx < f.vertexNormals.length && f.vertexNormals[idx] != null)
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
