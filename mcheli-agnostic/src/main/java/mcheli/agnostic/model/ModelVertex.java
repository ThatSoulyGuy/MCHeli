package mcheli.agnostic.model;

/**
 * A model-space vertex (or a normal direction) — faithful port of the client-only {@code W_Vertex}.
 * Pure data + the three vector helpers the parsers rely on; carries no rendering.
 *
 * <p>{@link #normalize()} preserves the reference exactly: the length is computed in {@code double}
 * ({@link Math#sqrt}) with no zero-length guard (a degenerate normal divides by zero → NaN, as upstream),
 * then each component is truncated back to {@code float}.
 */
public class ModelVertex {
    public float x;
    public float y;
    public float z;

    public ModelVertex(float x, float y) {
        this(x, y, 0.0F);
    }

    public ModelVertex(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public void normalize() {
        double d = Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z);
        this.x = (float) (this.x / d);
        this.y = (float) (this.y / d);
        this.z = (float) (this.z / d);
    }

    public void add(ModelVertex v) {
        this.x = this.x + v.x;
        this.y = this.y + v.y;
        this.z = this.z + v.z;
    }

    public boolean equal(ModelVertex v) {
        return this.x == v.x && this.y == v.y && this.z == v.z;
    }
}
