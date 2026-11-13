package mcheli.agnostic.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
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
        assertEquals(6, model.getFaceNum()); // the real face count (the reference's vertices/3 would say 2)

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
        assertEquals(2, model.getFaceNum());
    }

    /**
     * A {@code .obj} with no faces (vertices only, or a non-{@code $} group line) used to leave a single {@code null}
     * group behind (the reference added the trailing group unconditionally), so {@code containsPart} and the
     * renderer's group walk threw. It now parses to an empty part list.
     */
    @Test
    void obj_withoutFaces_hasNoNullGroup() {
        String src = "v 0.000000 0.000000 0.000000\n"
            + "v 1.000000 0.000000 0.000000\n"
            + "g plain\n";
        ObjModel model = new ObjModel("faceless.obj", new ByteArrayInputStream(src.getBytes(StandardCharsets.UTF_8)));

        assertTrue(model.groups().isEmpty());
        assertFalse(model.containsPart("Default"));
        assertEquals(0, model.getFaceNum());
        assertEquals(2, model.getVertexNum());
    }

    /** An empty {@code $} group followed by faces in the next one keeps both, and none of them is null. */
    @Test
    void obj_emptyDollarGroup_isKeptButNeverNull() {
        String src = "v 0.000000 0.000000 0.000000\n"
            + "v 1.000000 0.000000 0.000000\n"
            + "v 0.000000 1.000000 0.000000\n"
            + "g $empty\n"
            + "g $full\n"
            + "f 1 2 3\n";
        ObjModel model = new ObjModel("empty-group.obj", new ByteArrayInputStream(src.getBytes(StandardCharsets.UTF_8)));

        assertEquals(2, model.groups().size());
        assertFalse(model.groups().contains(null));
        assertEquals(0, model.groups().get(0).faces.size());
        assertEquals(1, model.groups().get(1).faces.size());
    }

    // ---- character decoding ----
    //
    // Both parsers pin UTF-8 instead of the reference's platform default. The in-process tests below are round-trip
    // regression guards only: on JDK 18+ the default charset is already UTF-8 (JEP 400) and this build sets no
    // file.encoding, so a parser still using the default would pass them too. The forked test is the proof.

    /** A UTF-8 object name, whose bytes read as ISO-8859-1 would be different text, survives the MQO parser. */
    @Test
    void mqo_utf8ObjectName_roundTrips() {
        assertNotEquals(Utf8Probe.MQO_NAME, Utf8Probe.asIso88591(Utf8Probe.MQO_NAME), "the name must be charset-sensitive");
        MqoModel model = Utf8Probe.parseMqo();

        assertEquals(1, model.groups().size());
        assertEquals(Utf8Probe.MQO_NAME, model.groups().get(0).name);
        assertEquals(1, model.getFaceNum());
        assertEquals(3, model.getVertexNum());
    }

    /**
     * The OBJ group grammar ({@code [-$\w\d]+}, ASCII {@code \w}) accepts only ASCII names, so the decoding cannot
     * change what a valid OBJ parses to. It shows in the error for a non-ASCII group line, which quotes the line as
     * decoded: here as the UTF-8 text.
     */
    @Test
    void obj_nonAsciiGroupLine_isRejectedAndQuotedAsUtf8() {
        assertNotEquals(Utf8Probe.OBJ_GROUP, Utf8Probe.asIso88591(Utf8Probe.OBJ_GROUP));
        assertEquals("g " + Utf8Probe.OBJ_GROUP, Utf8Probe.objErrorLine());
    }

    /**
     * Both parsers decode UTF-8 even when the JVM's default charset is not UTF-8. The checks run in a child JVM started
     * with {@code -Dfile.encoding=ISO-8859-1}, where a parser using the platform default would read the MQO object
     * name and the quoted OBJ line as Latin-1 text and fail. Skipped if that JVM ignores the property.
     */
    @Test
    void bothParsers_decodeUtf8_whenTheDefaultCharsetIsNotUtf8() throws Exception {
        Path javaHome = Path.of(System.getProperty("java.home"), "bin");
        Path java = Files.exists(javaHome.resolve("java.exe")) ? javaHome.resolve("java.exe") : javaHome.resolve("java");
        String cp = classpathEntry(MqoModel.class) + File.pathSeparator + classpathEntry(Utf8Probe.class);
        Process p = new ProcessBuilder(java.toString(), "-Dfile.encoding=ISO-8859-1", "-cp", cp, Utf8Probe.class.getName())
            .redirectErrorStream(true).start();
        byte[] out = p.getInputStream().readAllBytes();
        assertTrue(p.waitFor(60, TimeUnit.SECONDS), "probe JVM did not exit");
        List<String> lines = new String(out, StandardCharsets.US_ASCII).lines().toList();
        assertEquals(0, p.exitValue(), "probe JVM failed: " + lines);
        assumeTrue(lines.get(0).equals("ISO-8859-1"), "the probe JVM ignored -Dfile.encoding: " + lines.get(0));

        assertEquals(Utf8Probe.escape(Utf8Probe.MQO_NAME), lines.get(1), "MQO object name");
        assertEquals(Utf8Probe.escape("g " + Utf8Probe.OBJ_GROUP), lines.get(2), "OBJ error line");
    }

    private static String classpathEntry(Class<?> c) throws Exception {
        return Path.of(c.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
    }

    /**
     * The parse half of the decoding tests, runnable as a child JVM's main class. It references only the model
     * parsers and the JDK, so the child needs no test framework on its classpath. {@link #main} prints the default
     * charset, then the MQO object name and the OBJ error line with every non-ASCII char as {@code \}{@code uXXXX}.
     */
    static final class Utf8Probe {
        /** "airframe" in Japanese: UTF-8 E6 A9 9F E4 BD 93, which ISO-8859-1 reads as six other characters. */
        static final String MQO_NAME = "機体";
        /** A {@code $} group with a non-ASCII name ("wing" in Japanese, and an umlaut). */
        static final String OBJ_GROUP = "$翼_flügel";

        public static void main(String[] args) {
            System.out.println(Charset.defaultCharset().name());
            System.out.println(escape(parseMqo().groups().get(0).name));
            System.out.println(escape(objErrorLine()));
        }

        static MqoModel parseMqo() {
            String src = "Metasequoia Document\n"
                + "Format Text Ver 1.0\n"
                + "Object \"" + MQO_NAME + "\" {\n"
                + "\tvertex 3 {\n"
                + "\t\t0 0 0\n"
                + "\t\t100 0 0\n"
                + "\t\t0 100 0\n"
                + "\t}\n"
                + "\tface 1 {\n"
                + "\t\t3 V(0 1 2)\n"
                + "\t}\n"
                + "}\n"
                + "Eof\n";
            return new MqoModel("utf8.mqo", new ByteArrayInputStream(src.getBytes(StandardCharsets.UTF_8)));
        }

        /** The line the OBJ parser quotes when it rejects the non-ASCII group line, as it decoded it. */
        static String objErrorLine() {
            String src = "v 0.000000 0.000000 0.000000\n"
                + "v 1.000000 0.000000 0.000000\n"
                + "v 0.000000 1.000000 0.000000\n"
                + "g " + OBJ_GROUP + "\n"
                + "f 1 2 3\n";
            try {
                new ObjModel("utf8.obj", new ByteArrayInputStream(src.getBytes(StandardCharsets.UTF_8)));
            } catch (ModelFormatException e) {
                String m = e.getMessage();
                int from = m.indexOf("('");
                int to = m.indexOf("', line");
                if (from >= 0 && to > from) {
                    return m.substring(from + 2, to);
                }
                return "unexpected message: " + m;
            }
            return "no error";
        }

        static String asIso88591(String s) {
            return new String(s.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
        }

        static String escape(String s) {
            StringBuilder b = new StringBuilder();
            for (char c : s.toCharArray()) {
                if (c >= 0x20 && c < 0x7F) {
                    b.append(c);
                } else {
                    b.append(String.format("\\u%04X", (int) c));
                }
            }
            return b.toString();
        }
    }
}
