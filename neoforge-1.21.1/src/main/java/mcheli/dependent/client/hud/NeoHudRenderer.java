package mcheli.dependent.client.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import mcheli.MCHeli;
import mcheli.agnostic.hud.HudRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * {@link HudRenderer} over {@link GuiGraphics}: text with shadow, filled rects, textured quads from
 * {@code mcheli:textures/gui/<name>.png}, and — since GuiGraphics has no line primitive — lines drawn as thin rotated
 * {@code fill} quads via the pose stack (so diagonal reticle/ladder lines work too).
 */
public final class NeoHudRenderer implements HudRenderer {

    /** Real {@code [width, height]} of each gui sheet, read once from its PNG header (IHDR) and cached — the reference
     *  scaled UVs by the texture's actual size, so this must NOT be hardcoded (a 512-wide or non-256-tall custom/content
     *  sheet would otherwise map its UVs wrong and render distorted). Falls back to 256×256 if the file can't be read. */
    private static final Map<String, int[]> DIMS = new HashMap<>();

    private static int[] dims(String name) {
        return DIMS.computeIfAbsent(name, n -> {
            ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(MCHeli.MODID, "textures/gui/" + n + ".png");
            try (InputStream in = Minecraft.getInstance().getResourceManager().open(rl)) {
                byte[] head = in.readNBytes(24); // PNG: 8-byte sig + IHDR length/type, then width@16, height@20 (BE)
                if (head.length >= 24) {
                    int w = beInt(head, 16);
                    int h = beInt(head, 20);
                    if (w > 0 && h > 0) {
                        return new int[]{w, h};
                    }
                }
            } catch (Exception ignored) {
                // missing/unreadable texture — fall through to the 256×256 default (blit will just show missing texture)
            }
            return new int[]{256, 256};
        });
    }

    private static int beInt(byte[] b, int off) {
        return ((b[off] & 0xFF) << 24) | ((b[off + 1] & 0xFF) << 16) | ((b[off + 2] & 0xFF) << 8) | (b[off + 3] & 0xFF);
    }

    private final GuiGraphics g;
    private final Font font;

    public NeoHudRenderer(GuiGraphics g, Font font) {
        this.g = g;
        this.font = font;
    }

    @Override
    public void string(String text, int x, int y, int argb, boolean centered) {
        if (centered) {
            g.drawCenteredString(this.font, text, x, y, argb);
        } else {
            g.drawString(this.font, text, x, y, argb, true);
        }
    }

    @Override
    public void fill(int x1, int y1, int x2, int y2, int argb) {
        g.fill(x1, y1, x2, y2, argb);
    }

    @Override
    public void line(double[] points, int argb, int mode, float width) {
        int n = points.length / 2;
        if (n < 2) {
            return;
        }
        for (int i = 0; i + 1 < n; i++) {
            if (mode == 1 && (i & 1) == 1) {
                continue; // GL_LINES: disjoint pairs
            }
            segment(points[2 * i], points[2 * i + 1], points[2 * i + 2], points[2 * i + 3], argb, width);
        }
        if (mode == 2) { // GL_LINE_LOOP: close last -> first
            segment(points[2 * (n - 1)], points[2 * (n - 1) + 1], points[0], points[1], argb, width);
        }
    }

    private void segment(double x1, double y1, double x2, double y2, int argb, float width) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 1.0e-6) {
            return;
        }
        // A filled parallelogram between the endpoints, offset ±half-thickness along the perpendicular, in FLOAT
        // coords. This replaces the old "rotated 1px g.fill" — integer rounding + a thin rotated axis-aligned fill
        // rasterized diagonal and connected lines as broken/dotted; a real quad in float space renders them solid.
        double half = Math.max(1.0F, width) / 2.0;
        double px = -dy / len * half;
        double py = dx / len * half;
        Matrix4f m = g.pose().last().pose(); // includes any active graduation rotation
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        // Same vertex winding as GuiGraphics.fill (so it is not culled and batches with the other GUI quads).
        vertex(vc, m, x1 + px, y1 + py, argb);
        vertex(vc, m, x2 + px, y2 + py, argb);
        vertex(vc, m, x2 - px, y2 - py, argb);
        vertex(vc, m, x1 - px, y1 - py, argb);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, double x, double y, int argb) {
        vc.addVertex(m, (float) x, (float) y, 0.0F).setColor(argb);
    }

    @Override
    public void points(double[] centers, int argb, double size) {
        double half = Math.max(1.0, size) / 2.0;
        for (int i = 0; i + 1 < centers.length; i += 2) {
            int x1 = (int) Math.round(centers[i] - half);
            int y1 = (int) Math.round(centers[i + 1] - half);
            int x2 = (int) Math.round(centers[i] + half);
            int y2 = (int) Math.round(centers[i + 1] + half);
            g.fill(x1, y1, Math.max(x2, x1 + 1), Math.max(y2, y1 + 1), argb); // a >=1px square blip
        }
    }

    @Override
    public void pushMatrix(double pivotX, double pivotY, double degrees) {
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(pivotX, pivotY, 0.0);
        pose.mulPose(Axis.ZP.rotationDegrees((float) degrees));
        pose.translate(-pivotX, -pivotY, 0.0);
    }

    @Override
    public void popMatrix() {
        g.pose().popPose();
    }

    @Override
    public void lineStipple(double[] points, int argb, int factor, int pattern, float width) {
        int n = points.length / 2;
        int f = Math.max(1, factor);
        int pat = pattern & 0xFFFF;
        for (int p = 0; p + 1 < n; p += 2) { // GL_LINES: disjoint point pairs
            stippleSegment(points[2 * p], points[2 * p + 1], points[2 * p + 2], points[2 * p + 3], argb, f, pat, width);
        }
    }

    /** One dashed segment: walk its length pixel-by-pixel, emitting a solid sub-segment for each run of set pattern
     *  bits (bit index {@code (pos/factor) % 16}, LSB first — the GL_LINE_STIPPLE convention). */
    private void stippleSegment(double x1, double y1, double x2, double y2, int argb, int factor, int pattern,
                                float width) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 1.0e-6) {
            return;
        }
        double ux = dx / len;
        double uy = dy / len;
        int steps = (int) Math.ceil(len);
        double dashStart = -1.0;
        for (int d = 0; d <= steps; d++) {
            boolean on = d < len && ((pattern >> ((d / factor) % 16)) & 1) != 0;
            if (on && dashStart < 0.0) {
                dashStart = d;
            } else if (!on && dashStart >= 0.0) {
                double end = Math.min(d, len);
                segment(x1 + ux * dashStart, y1 + uy * dashStart, x1 + ux * end, y1 + uy * end, argb, width);
                dashStart = -1.0;
            }
        }
    }

    @Override
    public void texture(String name, double x, double y, double w, double h, double u, double v, double uw, double vh,
                        double rot) {
        ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(MCHeli.MODID, "textures/gui/" + name + ".png");
        PoseStack pose = g.pose();
        pose.pushPose();
        double cx = x + w / 2.0;
        double cy = y + h / 2.0;
        pose.translate(cx, cy, 0.0);
        if (rot != 0.0) {
            pose.mulPose(Axis.ZP.rotationDegrees((float) rot));
        }
        pose.translate(-w / 2.0, -h / 2.0, 0.0);
        int[] d = dims(name); // real sheet size for correct UV normalization (not a hardcoded width/height)
        g.blit(rl, 0, 0, (int) Math.round(w), (int) Math.round(h),
            (float) u, (float) v, (int) Math.round(uw), (int) Math.round(vh), d[0], d[1]);
        pose.popPose();
    }
}
