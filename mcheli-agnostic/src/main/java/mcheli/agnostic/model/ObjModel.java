package mcheli.agnostic.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Wavefront ({@code .obj}) model parser — faithful port of the parse half of the client-only
 * {@code W_WavefrontObject} (all {@code Tessellator} rendering removed). Behaviour preserved:
 *
 * <ul>
 *   <li>global 1-indexed {@code v}/{@code vt}/{@code vn} lists; faces reference them as
 *       {@code V}, {@code V/VT}, {@code V//VN}, or {@code V/VT/VN};</li>
 *   <li>texture V flipped ({@code 1 - v}); vertices are NOT scaled (unlike {@code .mqo});</li>
 *   <li>triangles set {@code glDrawingMode=4}, quads {@code =7} (kept as faces of 4 vertices, not split);</li>
 *   <li>the upstream group predicate {@code startsWith("g ") | startsWith("o ") && charAt(2)=='$'} is
 *       reproduced verbatim (Java {@code &&} binds tighter than {@code |}): every {@code g} line, but only
 *       {@code o} lines whose name starts with {@code $}.</li>
 * </ul>
 */
public class ObjModel extends MchModel {
    private static final Pattern vertexPattern =
        Pattern.compile("(v( (\\-){0,1}\\d+\\.\\d+){3,4} *\\n)|(v( (\\-){0,1}\\d+\\.\\d+){3,4} *$)");
    private static final Pattern vertexNormalPattern =
        Pattern.compile("(vn( (\\-){0,1}\\d+\\.\\d+){3,4} *\\n)|(vn( (\\-){0,1}\\d+\\.\\d+){3,4} *$)");
    private static final Pattern textureCoordinatePattern =
        Pattern.compile("(vt( (\\-){0,1}\\d+\\.\\d+){2,3} *\\n)|(vt( (\\-){0,1}\\d+\\.\\d+){2,3} *$)");
    private static final Pattern face_V_VT_VN_Pattern =
        Pattern.compile("(f( \\d+/\\d+/\\d+){3,4} *\\n)|(f( \\d+/\\d+/\\d+){3,4} *$)");
    private static final Pattern face_V_VT_Pattern =
        Pattern.compile("(f( \\d+/\\d+){3,4} *\\n)|(f( \\d+/\\d+){3,4} *$)");
    private static final Pattern face_V_VN_Pattern =
        Pattern.compile("(f( \\d+//\\d+){3,4} *\\n)|(f( \\d+//\\d+){3,4} *$)");
    private static final Pattern face_V_Pattern =
        Pattern.compile("(f( \\d+){3,4} *\\n)|(f( \\d+){3,4} *$)");
    private static final Pattern groupObjectPattern =
        Pattern.compile("([go]( [-\\$\\w\\d]+) *\\n)|([go]( [-\\$\\w\\d]+) *$)");

    public ArrayList<ModelVertex> vertices = new ArrayList<>();
    public ArrayList<ModelVertex> vertexNormals = new ArrayList<>();
    public ArrayList<ModelTexCoord> textureCoordinates = new ArrayList<>();
    public ArrayList<ModelGroup> groupObjects = new ArrayList<>();
    private ModelGroup currentGroupObject;
    private final String fileName;

    public ObjModel(String fileName, InputStream inputStream) throws ModelFormatException {
        this.fileName = fileName;
        this.loadModel(inputStream);
    }

    @Override
    public List<ModelGroup> groups() {
        return this.groupObjects;
    }

    @Override
    public int getVertexNum() {
        return this.vertices.size();
    }

    @Override
    public int getFaceNum() {
        return this.getVertexNum() / 3;
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
                if (!currentLine.startsWith("#") && currentLine.length() != 0) {
                    if (currentLine.startsWith("v ")) {
                        ModelVertex vertex = this.parseVertex(currentLine, lineCount);
                        if (vertex != null) {
                            this.checkMinMax(vertex);
                            this.vertices.add(vertex);
                        }
                    } else if (currentLine.startsWith("vn ")) {
                        ModelVertex vertex = this.parseVertexNormal(currentLine, lineCount);
                        if (vertex != null) {
                            this.vertexNormals.add(vertex);
                        }
                    } else if (currentLine.startsWith("vt ")) {
                        ModelTexCoord textureCoordinate = this.parseTextureCoordinate(currentLine, lineCount);
                        if (textureCoordinate != null) {
                            this.textureCoordinates.add(textureCoordinate);
                        }
                    } else if (currentLine.startsWith("f ")) {
                        if (this.currentGroupObject == null) {
                            this.currentGroupObject = new ModelGroup("Default");
                        }

                        ModelFace face = this.parseFace(currentLine, lineCount);
                        if (face != null) {
                            this.currentGroupObject.faces.add(face);
                        }
                    } else if (currentLine.startsWith("g ") | currentLine.startsWith("o ") && currentLine.charAt(2) == '$') {
                        ModelGroup group = this.parseGroupObject(currentLine, lineCount);
                        if (group != null && this.currentGroupObject != null) {
                            this.groupObjects.add(this.currentGroupObject);
                        }

                        this.currentGroupObject = group;
                    }
                }
            }

            this.groupObjects.add(this.currentGroupObject);
        } catch (IOException e) {
            throw new ModelFormatException("IO Exception reading model format", e);
        } finally {
            this.checkMinMaxFinal();

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

    private ModelVertex parseVertex(String line, int lineCount) throws ModelFormatException {
        ModelVertex vertex = null;
        if (isValidVertexLine(line)) {
            line = line.substring(line.indexOf(" ") + 1);
            String[] tokens = line.split(" ");

            try {
                if (tokens.length == 2) {
                    return new ModelVertex(Float.parseFloat(tokens[0]), Float.parseFloat(tokens[1]));
                } else {
                    return tokens.length == 3
                        ? new ModelVertex(Float.parseFloat(tokens[0]), Float.parseFloat(tokens[1]), Float.parseFloat(tokens[2]))
                        : vertex;
                }
            } catch (NumberFormatException e) {
                throw new ModelFormatException(String.format("Number formatting error at line %d", lineCount), e);
            }
        } else {
            throw new ModelFormatException(
                "Error parsing entry ('" + line + "'" + ", line " + lineCount + ") in file '" + this.fileName + "' - Incorrect format");
        }
    }

    private ModelVertex parseVertexNormal(String line, int lineCount) throws ModelFormatException {
        ModelVertex vertexNormal = null;
        if (isValidVertexNormalLine(line)) {
            line = line.substring(line.indexOf(" ") + 1);
            String[] tokens = line.split(" ");

            try {
                return tokens.length == 3
                    ? new ModelVertex(Float.parseFloat(tokens[0]), Float.parseFloat(tokens[1]), Float.parseFloat(tokens[2]))
                    : vertexNormal;
            } catch (NumberFormatException e) {
                throw new ModelFormatException(String.format("Number formatting error at line %d", lineCount), e);
            }
        } else {
            throw new ModelFormatException(
                "Error parsing entry ('" + line + "'" + ", line " + lineCount + ") in file '" + this.fileName + "' - Incorrect format");
        }
    }

    private ModelTexCoord parseTextureCoordinate(String line, int lineCount) throws ModelFormatException {
        ModelTexCoord textureCoordinate = null;
        if (isValidTextureCoordinateLine(line)) {
            line = line.substring(line.indexOf(" ") + 1);
            String[] tokens = line.split(" ");

            try {
                if (tokens.length == 2) {
                    return new ModelTexCoord(Float.parseFloat(tokens[0]), 1.0F - Float.parseFloat(tokens[1]));
                } else {
                    return tokens.length == 3
                        ? new ModelTexCoord(Float.parseFloat(tokens[0]), 1.0F - Float.parseFloat(tokens[1]), Float.parseFloat(tokens[2]))
                        : textureCoordinate;
                }
            } catch (NumberFormatException e) {
                throw new ModelFormatException(String.format("Number formatting error at line %d", lineCount), e);
            }
        } else {
            throw new ModelFormatException(
                "Error parsing entry ('" + line + "'" + ", line " + lineCount + ") in file '" + this.fileName + "' - Incorrect format");
        }
    }

    private ModelFace parseFace(String line, int lineCount) throws ModelFormatException {
        ModelFace face = null;
        if (isValidFaceLine(line)) {
            face = new ModelFace();
            String trimmedLine = line.substring(line.indexOf(" ") + 1);
            String[] tokens = trimmedLine.split(" ");
            String[] subTokens = null;
            if (tokens.length == 3) {
                if (this.currentGroupObject.glDrawingMode == -1) {
                    this.currentGroupObject.glDrawingMode = 4;
                } else if (this.currentGroupObject.glDrawingMode != 4) {
                    throw new ModelFormatException(
                        "Error parsing entry ('" + line + "'" + ", line " + lineCount + ") in file '" + this.fileName
                            + "' - Invalid number of points for face (expected 4, found " + tokens.length + ")");
                }
            } else if (tokens.length == 4) {
                if (this.currentGroupObject.glDrawingMode == -1) {
                    this.currentGroupObject.glDrawingMode = 7;
                } else if (this.currentGroupObject.glDrawingMode != 7) {
                    throw new ModelFormatException(
                        "Error parsing entry ('" + line + "'" + ", line " + lineCount + ") in file '" + this.fileName
                            + "' - Invalid number of points for face (expected 3, found " + tokens.length + ")");
                }
            }

            if (isValidFace_V_VT_VN_Line(line)) {
                face.vertices = new ModelVertex[tokens.length];
                face.textureCoordinates = new ModelTexCoord[tokens.length];
                face.vertexNormals = new ModelVertex[tokens.length];

                for (int i = 0; i < tokens.length; i++) {
                    subTokens = tokens[i].split("/");
                    face.vertices[i] = this.vertices.get(Integer.parseInt(subTokens[0]) - 1);
                    face.textureCoordinates[i] = this.textureCoordinates.get(Integer.parseInt(subTokens[1]) - 1);
                    face.vertexNormals[i] = this.vertexNormals.get(Integer.parseInt(subTokens[2]) - 1);
                }

                face.faceNormal = face.calculateFaceNormal();
            } else if (isValidFace_V_VT_Line(line)) {
                face.vertices = new ModelVertex[tokens.length];
                face.textureCoordinates = new ModelTexCoord[tokens.length];

                for (int i = 0; i < tokens.length; i++) {
                    subTokens = tokens[i].split("/");
                    face.vertices[i] = this.vertices.get(Integer.parseInt(subTokens[0]) - 1);
                    face.textureCoordinates[i] = this.textureCoordinates.get(Integer.parseInt(subTokens[1]) - 1);
                }

                face.faceNormal = face.calculateFaceNormal();
            } else if (isValidFace_V_VN_Line(line)) {
                face.vertices = new ModelVertex[tokens.length];
                face.vertexNormals = new ModelVertex[tokens.length];

                for (int i = 0; i < tokens.length; i++) {
                    subTokens = tokens[i].split("//");
                    face.vertices[i] = this.vertices.get(Integer.parseInt(subTokens[0]) - 1);
                    face.vertexNormals[i] = this.vertexNormals.get(Integer.parseInt(subTokens[1]) - 1);
                }

                face.faceNormal = face.calculateFaceNormal();
            } else {
                if (!isValidFace_V_Line(line)) {
                    throw new ModelFormatException(
                        "Error parsing entry ('" + line + "'" + ", line " + lineCount + ") in file '" + this.fileName + "' - Incorrect format");
                }

                face.vertices = new ModelVertex[tokens.length];

                for (int i = 0; i < tokens.length; i++) {
                    face.vertices[i] = this.vertices.get(Integer.parseInt(tokens[i]) - 1);
                }

                face.faceNormal = face.calculateFaceNormal();
            }

            return face;
        } else {
            throw new ModelFormatException(
                "Error parsing entry ('" + line + "'" + ", line " + lineCount + ") in file '" + this.fileName + "' - Incorrect format");
        }
    }

    private ModelGroup parseGroupObject(String line, int lineCount) throws ModelFormatException {
        ModelGroup group = null;
        if (isValidGroupObjectLine(line)) {
            String trimmedLine = line.substring(line.indexOf(" ") + 1);
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
        return vertexPattern.matcher(line).matches();
    }

    private static boolean isValidVertexNormalLine(String line) {
        return vertexNormalPattern.matcher(line).matches();
    }

    private static boolean isValidTextureCoordinateLine(String line) {
        return textureCoordinatePattern.matcher(line).matches();
    }

    private static boolean isValidFace_V_VT_VN_Line(String line) {
        return face_V_VT_VN_Pattern.matcher(line).matches();
    }

    private static boolean isValidFace_V_VT_Line(String line) {
        return face_V_VT_Pattern.matcher(line).matches();
    }

    private static boolean isValidFace_V_VN_Line(String line) {
        return face_V_VN_Pattern.matcher(line).matches();
    }

    private static boolean isValidFace_V_Line(String line) {
        return face_V_Pattern.matcher(line).matches();
    }

    private static boolean isValidFaceLine(String line) {
        return isValidFace_V_VT_VN_Line(line) || isValidFace_V_VT_Line(line) || isValidFace_V_VN_Line(line) || isValidFace_V_Line(line);
    }

    private static boolean isValidGroupObjectLine(String line) {
        return groupObjectPattern.matcher(line).matches();
    }
}
