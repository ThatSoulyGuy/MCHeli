package mcheli.agnostic.model;

/**
 * A single polygon (triangle or quad) — faithful port of the client-only {@code W_Face}, minus the
 * {@code Tessellator} emit (which is rewritten dependent-side onto a {@code VertexConsumer}).
 *
 * <p>{@link #calculateFaceNormal()} reproduces the reference's {@code Vec3} math exactly: the edge
 * cross-product {@code (v1-v0) x (v2-v0)} is evaluated in {@code double}, normalized with MC's
 * {@code Vec3.normalize} zero-length guard ({@code len < 1.0E-4 -> (0,0,0)}), then truncated to {@code float}.
 * Crucially the length divisor is itself truncated to {@code float} first: {@code Vec3.normalize} computes it via
 * {@code MathHelper.sqrt_double}, which is {@code (float) Math.sqrt(...)}. Using a full-{@code double} sqrt here
 * would shift ~1 ULP on most faces and, through the {@code >=} facet comparison in
 * {@code MqoModel.getVerticesNormalFromFace}, can flip smooth-normal contributions on boundary faces.
 */
public class ModelFace {
    /** Indices into the owning group/model vertex list (used for smooth-normal accumulation in .mqo). */
    public int[] verticesID;
    public ModelVertex[] vertices;
    public ModelVertex[] vertexNormals;
    public ModelVertex faceNormal;
    public ModelTexCoord[] textureCoordinates;

    public ModelVertex calculateFaceNormal() {
        // v1 = vertices[1] - vertices[0], v2 = vertices[2] - vertices[0]; both widened float->double as upstream.
        double v1x = this.vertices[1].x - this.vertices[0].x;
        double v1y = this.vertices[1].y - this.vertices[0].y;
        double v1z = this.vertices[1].z - this.vertices[0].z;
        double v2x = this.vertices[2].x - this.vertices[0].x;
        double v2y = this.vertices[2].y - this.vertices[0].y;
        double v2z = this.vertices[2].z - this.vertices[0].z;
        // cross = v1 x v2 (matches Vec3.crossProduct component order)
        double cx = v1y * v2z - v1z * v2y;
        double cy = v1z * v2x - v1x * v2z;
        double cz = v1x * v2y - v1y * v2x;
        // Vec3.normalize: len via MathHelper.sqrt_double = (float) Math.sqrt(...), truncated to float BEFORE the
        // guard and the divisions; len < 1.0E-4 -> zero vector, else divide.
        double len = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
        if (len < 1.0E-4D) {
            return new ModelVertex(0.0F, 0.0F, 0.0F);
        }
        return new ModelVertex((float) (cx / len), (float) (cy / len), (float) (cz / len));
    }
}
