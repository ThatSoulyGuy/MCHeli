package mcheli.agnostic.spi;

/**
 * High-level 2D HUD canvas. It NEVER exposes raw GL — the agnostic HUD DSL emits text/rect/line/
 * textured-rect + matrix ops; GL-heavy items (graduation, raw textures, camera effects) are rewritten
 * natively in the dependent layer, not coerced through here.
 */
public interface HudRenderer {
    int screenWidth();
    int screenHeight();

    void drawString(String text, int x, int y, int argb);
    void fill(double x1, double y1, double x2, double y2, int argb);
    void drawLine(double x1, double y1, double x2, double y2, float width, int argb);

    void bindTexture(String textureId);
    void drawTexturedRect(double x, double y, double w, double h, double u, double v, double uWidth, double vHeight);

    void pushPose();
    void popPose();
    void translate(double x, double y, double z);
    void rotate(float degrees, float ax, float ay, float az);
    void scale(double sx, double sy, double sz);
    void setColor(float r, float g, float b, float a);
}
