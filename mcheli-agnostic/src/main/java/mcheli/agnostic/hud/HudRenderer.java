package mcheli.agnostic.hud;

/**
 * The draw target the agnostic HUD emits into — implemented dependent-side over {@code GuiGraphics}. All coordinates
 * are already resolved to GUI-scaled screen pixels (centre-origin offsets have been applied), and colours are
 * 0xAARRGGBB. This keeps every layout/expression decision in the agnostic layer and leaves only the raw draw here.
 */
public interface HudRenderer {

    /** Draw text (with shadow) at (x,y); {@code centered} centres it horizontally on x. */
    void string(String text, int x, int y, int argb, boolean centered);

    /** Fill an axis-aligned rectangle [x1,y1)-(x2,y2) in 0xAARRGGBB. */
    void fill(int x1, int y1, int x2, int y2, int argb);

    /** Draw a polyline through {@code points} (x0,y0,x1,y1,…); {@code mode} 1=disjoint pairs, 2=loop, 3=strip;
     *  {@code width} in pixels (the GUI scale). */
    void line(double[] points, int argb, int mode, float width);

    /** Blit a region of {@code mcheli:textures/gui/<name>.png} — a {@code w×h} quad at (x,y) sampling the (u,v)+(uw,vh)
     *  texel region, rotated {@code rot} degrees about its centre. Drawn untinted (white). */
    void texture(String name, double x, double y, double w, double h, double u, double v, double uw, double vh,
                 double rot);

    /** Push a rotation of {@code degrees} about the pivot ({@code pivotX},{@code pivotY}) onto the draw matrix — the
     *  reference's {@code glTranslate(x,y)/glRotate(roll)/glTranslate(-x,-y)} for the graduation ladders that bank with
     *  roll. Must be balanced by {@link #popMatrix()}. Default: no-op (headless capture ignores the transform). */
    default void pushMatrix(double pivotX, double pivotY, double degrees) { }

    /** Pop the matrix pushed by {@link #pushMatrix}. Default: no-op. */
    default void popMatrix() { }

    /** Draw a stippled (dashed) line of disjoint point pairs (GL_LINES) using a 16-bit LSB-first {@code pattern} with
     *  {@code factor} pixels per bit (the reference's {@code glLineStipple}). Default: a solid line, so a headless
     *  capture still tallies it. */
    default void lineStipple(double[] points, int argb, int factor, int pattern, float width) {
        line(points, argb, 1, width);
    }

    /** Draw radar blips: each (x,y) pair in {@code centers} as a filled {@code size}-pixel square centred on the point,
     *  in 0xAARRGGBB (the reference's {@code drawPoints}/{@code GL_POINTS}). Default: no-op (headless capture ignores). */
    default void points(double[] centers, int argb, double size) { }
}
