package mcheli.agnostic.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Metasequoia ({@code .mqo}) model parser — faithful port of the parse half of the client-only
 * {@code W_MetasequoiaObject} (all {@code Tessellator} rendering removed). Behaviour preserved bit-for-bit:
 *
 * <ul>
 *   <li>vertices scaled by {@code 1/100};</li>
 *   <li>faces read as triangles ({@code 3 V(...)}) or quads ({@code 4 V(...)}, split into two triangles),
 *       both with the reference's reversed winding ({@code s[3],s[2],s[1]});</li>
 *   <li>per-object smooth vertex normals via the {@code shading}/{@code facet} angle threshold, else the
 *       flat face normal;</li>
 *   <li>{@code getVertexNum()}/{@code getFaceNum()} accumulate per-object vertex counts and post-split
 *       triangle counts respectively.</li>
 * </ul>
 *
 * <p>The {@code mirror} directive is parsed and threaded to {@code parseFace} exactly as upstream — where it
 * is (and always was) unused.
 */
public class MqoModel extends MchModel {
    public ArrayList<ModelVertex> vertices = new ArrayList<>();
    public ArrayList<ModelGroup> groupObjects = new ArrayList<>();
    private final String fileName;
    private int vertexNum = 0;
    private int faceNum = 0;

    public MqoModel(String fileName, InputStream inputStream) throws ModelFormatException {
        this.fileName = fileName;
        this.loadModel(inputStream);
    }

    @Override
    public List<ModelGroup> groups() {
        return this.groupObjects;
    }

    @Override
    public int getVertexNum() {
        return this.vertexNum;
    }

    @Override
    public int getFaceNum() {
        return this.faceNum;
    }

    private void loadModel(InputStream inputStream) throws ModelFormatException {
        BufferedReader reader = null;
        String currentLine = null;
        int lineCount = 0;

        try {
            reader = new BufferedReader(new InputStreamReader(inputStream));

            while ((currentLine = reader.readLine()) != null) {
                lineCount++;
                currentLine = currentLine.replaceAll("\\s+", " ").trim();
                if (isValidGroupObjectLine(currentLine)) {
                    ModelGroup group = this.parseGroupObject(currentLine, lineCount);
                    if (group != null) {
                        group.glDrawingMode = 4;
                        this.vertices.clear();
                        int vertexNum = 0;
                        boolean mirror = false;
                        double facet = Math.cos(0.785398163375);
                        boolean shading = false;

                        while ((currentLine = reader.readLine()) != null) {
                            lineCount++;
                            currentLine = currentLine.replaceAll("\\s+", " ").trim();
                            if (currentLine.equalsIgnoreCase("mirror 1")) {
                                mirror = true;
                            }

                            if (currentLine.equalsIgnoreCase("shading 1")) {
                                shading = true;
                            }

                            String[] s = currentLine.split(" ");
                            if (s.length == 2 && s[0].equalsIgnoreCase("depth")) {
                                group.depth = Integer.parseInt(s[1]); // MQO part nesting depth (for child grouping)
                            }
                            if (s.length == 2 && s[0].equalsIgnoreCase("facet")) {
                                facet = Math.cos(Double.parseDouble(s[1]) * 3.1415926535 / 180.0);
                            }

                            if (isValidVertexLine(currentLine)) {
                                vertexNum = Integer.parseInt(currentLine.split(" ")[1]);
                                break;
                            }
                        }

                        if (vertexNum > 0) {
                            while ((currentLine = reader.readLine()) != null) {
                                lineCount++;
                                currentLine = currentLine.replaceAll("\\s+", " ").trim();
                                String[] s = currentLine.split(" ");
                                if (s.length == 3) {
                                    ModelVertex v = new ModelVertex(
                                        Float.parseFloat(s[0]) / 100.0F, Float.parseFloat(s[1]) / 100.0F, Float.parseFloat(s[2]) / 100.0F);
                                    this.checkMinMax(v);
                                    this.vertices.add(v);
                                    if (--vertexNum <= 0) {
                                        break;
                                    }
                                } else if (s.length > 0) {
                                    throw new ModelFormatException("format error : " + this.fileName + " : line=" + lineCount);
                                }
                            }

                            int faceNum = 0;

                            while ((currentLine = reader.readLine()) != null) {
                                lineCount++;
                                currentLine = currentLine.replaceAll("\\s+", " ").trim();
                                if (isValidFaceLine(currentLine)) {
                                    faceNum = Integer.parseInt(currentLine.split(" ")[1]);
                                    break;
                                }
                            }

                            if (faceNum > 0) {
                                while ((currentLine = reader.readLine()) != null) {
                                    lineCount++;
                                    currentLine = currentLine.replaceAll("\\s+", " ").trim();
                                    String[] s = currentLine.split(" ");
                                    if (s.length <= 2) {
                                        if (s.length > 2 && Integer.parseInt(s[0]) != 3) {
                                            throw new ModelFormatException(
                                                "found face is not triangle : " + this.fileName + " : line=" + lineCount);
                                        }
                                    } else {
                                        if (Integer.parseInt(s[0]) >= 3) {
                                            ModelFace[] faces = this.parseFace(currentLine, lineCount, mirror);

                                            for (ModelFace face : faces) {
                                                group.faces.add(face);
                                            }
                                        }

                                        if (--faceNum <= 0) {
                                            break;
                                        }
                                    }
                                }

                                this.calcVerticesNormal(group, shading, facet);
                            }
                        }

                        this.vertexNum = this.vertexNum + this.vertices.size();
                        this.faceNum = this.faceNum + group.faces.size();
                        this.vertices.clear();
                        this.groupObjects.add(group);
                    }
                }
            }
        } catch (IOException e) {
            throw new ModelFormatException("IO Exception reading model format : " + this.fileName, e);
        } finally {
            this.checkMinMaxFinal();
            this.vertices = null;

            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
            }

            try {
                inputStream.close();
            } catch (IOException e) {
            }
        }
    }

    public void calcVerticesNormal(ModelGroup group, boolean shading, double facet) {
        for (ModelFace f : group.faces) {
            f.vertexNormals = new ModelVertex[f.verticesID.length];

            for (int i = 0; i < f.verticesID.length; i++) {
                ModelVertex vn = this.getVerticesNormalFromFace(f.faceNormal, f.verticesID[i], group, (float) facet);
                vn.normalize();
                if (shading) {
                    if (f.faceNormal.x * vn.x + f.faceNormal.y * vn.y + f.faceNormal.z * vn.z >= facet) {
                        f.vertexNormals[i] = vn;
                    } else {
                        f.vertexNormals[i] = f.faceNormal;
                    }
                } else {
                    f.vertexNormals[i] = f.faceNormal;
                }
            }
        }
    }

    public ModelVertex getVerticesNormalFromFace(ModelVertex faceNormal, int verticesID, ModelGroup group, float facet) {
        ModelVertex v = new ModelVertex(0.0F, 0.0F, 0.0F);

        for (ModelFace f : group.faces) {
            for (int id : f.verticesID) {
                if (id == verticesID) {
                    if (f.faceNormal.x * faceNormal.x + f.faceNormal.y * faceNormal.y + f.faceNormal.z * faceNormal.z >= facet) {
                        v.add(f.faceNormal);
                    }
                    break;
                }
            }
        }

        v.normalize();
        return v;
    }

    private ModelFace[] parseFace(String line, int lineCount, boolean mirror) {
        String[] s = line.split("[ VU)(M]+");
        int vnum = Integer.parseInt(s[0]);
        if (vnum != 3 && vnum != 4) {
            return new ModelFace[0];
        }

        if (vnum == 3) {
            ModelFace face = new ModelFace();
            face.verticesID = new int[]{Integer.parseInt(s[3]), Integer.parseInt(s[2]), Integer.parseInt(s[1])};
            face.vertices = new ModelVertex[]{
                this.vertices.get(face.verticesID[0]), this.vertices.get(face.verticesID[1]), this.vertices.get(face.verticesID[2])};
            if (s.length >= 11) {
                face.textureCoordinates = new ModelTexCoord[]{
                    new ModelTexCoord(Float.parseFloat(s[9]), Float.parseFloat(s[10])),
                    new ModelTexCoord(Float.parseFloat(s[7]), Float.parseFloat(s[8])),
                    new ModelTexCoord(Float.parseFloat(s[5]), Float.parseFloat(s[6]))
                };
            } else {
                face.textureCoordinates = new ModelTexCoord[]{
                    new ModelTexCoord(0.0F, 0.0F), new ModelTexCoord(0.0F, 0.0F), new ModelTexCoord(0.0F, 0.0F)
                };
            }

            face.faceNormal = face.calculateFaceNormal();
            return new ModelFace[]{face};
        } else {
            ModelFace face1 = new ModelFace();
            face1.verticesID = new int[]{Integer.parseInt(s[3]), Integer.parseInt(s[2]), Integer.parseInt(s[1])};
            face1.vertices = new ModelVertex[]{
                this.vertices.get(face1.verticesID[0]), this.vertices.get(face1.verticesID[1]), this.vertices.get(face1.verticesID[2])
            };
            if (s.length >= 12) {
                face1.textureCoordinates = new ModelTexCoord[]{
                    new ModelTexCoord(Float.parseFloat(s[10]), Float.parseFloat(s[11])),
                    new ModelTexCoord(Float.parseFloat(s[8]), Float.parseFloat(s[9])),
                    new ModelTexCoord(Float.parseFloat(s[6]), Float.parseFloat(s[7]))
                };
            } else {
                face1.textureCoordinates = new ModelTexCoord[]{
                    new ModelTexCoord(0.0F, 0.0F), new ModelTexCoord(0.0F, 0.0F), new ModelTexCoord(0.0F, 0.0F)
                };
            }

            face1.faceNormal = face1.calculateFaceNormal();
            ModelFace face2 = new ModelFace();
            face2.verticesID = new int[]{Integer.parseInt(s[4]), Integer.parseInt(s[3]), Integer.parseInt(s[1])};
            face2.vertices = new ModelVertex[]{
                this.vertices.get(face2.verticesID[0]), this.vertices.get(face2.verticesID[1]), this.vertices.get(face2.verticesID[2])
            };
            if (s.length >= 14) {
                face2.textureCoordinates = new ModelTexCoord[]{
                    new ModelTexCoord(Float.parseFloat(s[12]), Float.parseFloat(s[13])),
                    new ModelTexCoord(Float.parseFloat(s[10]), Float.parseFloat(s[11])),
                    new ModelTexCoord(Float.parseFloat(s[6]), Float.parseFloat(s[7]))
                };
            } else {
                face2.textureCoordinates = new ModelTexCoord[]{
                    new ModelTexCoord(0.0F, 0.0F), new ModelTexCoord(0.0F, 0.0F), new ModelTexCoord(0.0F, 0.0F)
                };
            }

            face2.faceNormal = face2.calculateFaceNormal();
            return new ModelFace[]{face1, face2};
        }
    }

    private static boolean isValidGroupObjectLine(String line) {
        String[] s = line.split(" ");
        return s.length < 2 || !s[0].equals("Object") ? false : s[1].length() >= 4 && s[1].charAt(0) == '"';
    }

    private ModelGroup parseGroupObject(String line, int lineCount) throws ModelFormatException {
        ModelGroup group = null;
        if (isValidGroupObjectLine(line)) {
            String[] s = line.split(" ");
            String trimmedLine = s[1].substring(1, s[1].length() - 1);
            if (trimmedLine.length() > 0) {
                group = new ModelGroup(trimmedLine);
            }

            return group;
        } else {
            throw new ModelFormatException(
                "Error parsing entry ('" + line + "'" + ", line " + lineCount + ") in file '" + this.fileName + "' - Incorrect format");
        }
    }

    private static boolean isValidVertexLine(String line) {
        String[] s = line.split(" ");
        return s[0].equals("vertex");
    }

    private static boolean isValidFaceLine(String line) {
        String[] s = line.split(" ");
        return s[0].equals("face");
    }
}
