package mcheli.agnostic.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Parses two real bundled MCHeli models (a {@code .mqo} and a {@code .obj}) through the agnostic parsers and
 * asserts the faithful-port invariants: {@code .mqo} scales by 1/100 and splits quads into triangles;
 * {@code .obj} is 1-indexed, keeps quads, and flips the texture V. These lock the geometry the dependent
 * {@code VertexConsumer} renderer will consume.
 */
class ModelParsingTest {

    private static InputStream open(String name) {
        InputStream in = ModelParsingTest.class.getResourceAsStream("/models/" + name);
        assertNotNull(in, "test resource /models/" + name + " must be on the classpath");
        return in;
    }

    @Test
    void mqo_blu10b_parsesScaledTrianglesWithNormalsAndUv() {
        MqoModel model = new MqoModel("blu10b.mqo", open("blu10b.mqo"));

        // One object, "BLU-10B", 12 vertices; 5 quads + 10 tris -> 20 triangles after the quad split.
        assertEquals(1, model.groups().size());
        assertEquals("BLU-10B", model.groups().get(0).name);
        assertEquals(12, model.getVertexNum());
        assertEquals(20, model.getFaceNum());

        ModelGroup g = model.groups().get(0);
        assertEquals(20, g.faces.size());
        assertEquals(4, g.glDrawingMode); // .mqo groups are always triangles post-split

        for (ModelFace f : g.faces) {
            assertEquals(3, f.vertices.length, "every .mqo face is a triangle");
            assertEquals(3, f.verticesID.length);
            assertNotNull(f.textureCoordinates);
            assertEquals(3, f.textureCoordinates.length);
            assertNotNull(f.faceNormal);
            assertNotNull(f.vertexNormals, "calcVerticesNormal must have populated smooth normals");
            assertEquals(3, f.vertexNormals.length);
        }

        // Face normals are unit length (calculateFaceNormal normalizes; these faces are non-degenerate).
        ModelVertex n = g.faces.get(0).faceNormal;
        double len = Math.sqrt(n.x * n.x + n.y * n.y + n.z * n.z);
        assertTrue(Math.abs(len - 1.0) < 1.0E-3, "face normal should be unit length, was " + len);

        // Vertices were scaled by 1/100: raw Z spans -134.7404..133.6866 -> ~ -1.347..1.337.
        assertTrue(model.maxZ > 1.30F && model.maxZ < 1.40F, "maxZ=" + model.maxZ);
        assertTrue(model.minZ < -1.30F && model.minZ > -1.40F, "minZ=" + model.minZ);
        assertTrue(model.sizeZ > 2.68F && model.sizeZ < 2.69F, "sizeZ=" + model.sizeZ);
    }

    @Test
    void obj_cbc_parsesQuadsOneIndexedWithFlippedV() {
        ObjModel model = new ObjModel("cbc.obj", open("cbc.obj"));

        // 8 vertices, 6 quad faces (V/VT/VN form). NOTE: the "g CBC" line does NOT start a group — MCHeli only
        // treats $-prefixed groups as part boundaries. This is the faithful upstream quirk: the predicate is
        // (startsWith("g ") | startsWith("o ")) && charAt(2)=='$' because Java's bitwise | binds tighter than &&.
        // 'C' != '$', so the faces fold into the auto-created "Default" group. (See dollarGroups_splitParts.)
        assertEquals(1, model.groups().size());
        assertEquals("Default", model.groups().get(0).name);
        assertEquals(8, model.getVertexNum());

        ModelGroup g = model.groups().get(0);
        assertEquals(6, g.faces.size());
        assertEquals(7, g.glDrawingMode); // quads

        for (ModelFace f : g.faces) {
            assertEquals(4, f.vertices.length, "these .obj faces are quads (kept, not split)");
            assertNotNull(f.textureCoordinates);
            assertEquals(4, f.textureCoordinates.length);
            assertNotNull(f.vertexNormals);
            assertEquals(4, f.vertexNormals.length);
            assertNotNull(f.faceNormal);
            for (ModelTexCoord tc : f.textureCoordinates) {
                assertTrue(tc.u >= 0.0F && tc.u <= 1.0F, "u in [0,1], was " + tc.u);
                assertTrue(tc.v >= 0.0F && tc.v <= 1.0F, "v (flipped) in [0,1], was " + tc.v);
            }
        }

        ModelVertex n = g.faces.get(0).faceNormal;
        double len = Math.sqrt(n.x * n.x + n.y * n.y + n.z * n.z);
        assertTrue(Math.abs(len - 1.0) < 1.0E-3, "face normal should be unit length, was " + len);

        // .obj vertices are NOT scaled: raw coords are ~ +/-0.1425.
        assertTrue(model.maxX <= 0.15F && model.minX >= -0.15F, "obj verts unscaled: X in [-0.15,0.15]");
    }

    @Test
    void loader_dispatchesByExtension() {
        MchModel mqo = MchModelLoader.load("some/path/blu10b.mqo", open("blu10b.mqo"));
        MchModel obj = MchModelLoader.load("some/path/cbc.obj", open("cbc.obj"));
        assertTrue(mqo instanceof MqoModel);
        assertTrue(obj instanceof ObjModel);
        assertTrue(mqo.containsPart("BLU-10B"));
        assertTrue(obj.containsPart("Default")); // "g CBC" folds to Default (no $ prefix); see obj_cbc test
    }

    /**
     * Proves the {@code $}-prefixed group convention actually splits parts (cbc.obj has none). A {@code g $name}
     * line's third char is {@code $}, so {@code (startsWith("g ")|...) && charAt(2)=='$'} is true and it starts a
     * new group; MCHeli's real multi-part {@code .obj} models (e.g. {@code $body}/{@code $blade0}) rely on this.
     */
    @Test
    void obj_dollarGroups_splitParts() {
        String src = "v 0.000000 0.000000 0.000000\n"
            + "v 1.000000 0.000000 0.000000\n"
            + "v 0.000000 1.000000 0.000000\n"
            + "g $partA\n"
            + "f 1 2 3\n"
            + "g $partB\n"
            + "f 3 2 1\n";
        ObjModel model = new ObjModel("synthetic.obj", new ByteArrayInputStream(src.getBytes(StandardCharsets.UTF_8)));

        assertEquals(2, model.groups().size());
        assertEquals("$partA", model.groups().get(0).name);
        assertEquals("$partB", model.groups().get(1).name);
        assertEquals(1, model.groups().get(0).faces.size());
        assertEquals(1, model.groups().get(1).faces.size());
        assertEquals(3, model.groups().get(0).faces.get(0).vertices.length);
        assertTrue(model.containsPart("$partA") && model.containsPart("$partB"));
    }
}
