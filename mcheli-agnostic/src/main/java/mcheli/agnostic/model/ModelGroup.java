package mcheli.agnostic.model;

import java.util.ArrayList;

/**
 * A named group of {@link ModelFace}s (a model "part") — faithful port of the client-only
 * {@code W_GroupObject}, minus rendering. {@link #glDrawingMode} preserves the reference's GL primitive
 * code (4 = triangles, 7 = quads) so the dependent renderer can reproduce the original topology.
 */
public class ModelGroup {
    public String name;
    public ArrayList<ModelFace> faces = new ArrayList<>();
    public int glDrawingMode;

    public ModelGroup() {
        this("");
    }

    public ModelGroup(String name) {
        this(name, -1);
    }

    public ModelGroup(String name, int glDrawingMode) {
        this.name = name;
        this.glDrawingMode = glDrawingMode;
    }
}
