package mcheli.agnostic.model;

/**
 * A texture coordinate (u, v, optional w) — faithful port of the client-only {@code W_TextureCoordinate}.
 * Pure data; the {@code .obj} parser stores {@code 1 - v} here (V flipped), while {@code .mqo} stores V as-is.
 */
public class ModelTexCoord {
    public float u;
    public float v;
    public float w;

    public ModelTexCoord(float u, float v) {
        this(u, v, 0.0F);
    }

    public ModelTexCoord(float u, float v, float w) {
        this.u = u;
        this.v = v;
        this.w = w;
    }
}
