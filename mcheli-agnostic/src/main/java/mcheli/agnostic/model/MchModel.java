package mcheli.agnostic.model;

import java.util.List;
import mcheli.agnostic.spi.ModelHandle;

/**
 * Agnostic base for a parsed vehicle model — faithful port of the client-only {@code W_ModelCustom}
 * (the bounding-box bookkeeping) with the {@code Tessellator} render entry points removed. Implements
 * {@link ModelHandle} so a parsed model can be carried by the {@code *Info} definitions and downcast by
 * the dependent renderer.
 *
 * <p>Concrete geometry lives in the format subclasses {@link MqoModel} / {@link ObjModel}; this base only
 * owns the min/max bounds and the part-name query.
 */
public abstract class MchModel implements ModelHandle {
    public float min = 100000.0F;
    public float minX = 100000.0F;
    public float minY = 100000.0F;
    public float minZ = 100000.0F;
    public float max = -100000.0F;
    public float maxX = -100000.0F;
    public float maxY = -100000.0F;
    public float maxZ = -100000.0F;
    public float size = 0.0F;
    public float sizeX = 0.0F;
    public float sizeY = 0.0F;
    public float sizeZ = 0.0F;

    public void checkMinMax(ModelVertex v) {
        if (v.x < this.minX) {
            this.minX = v.x;
        }

        if (v.y < this.minY) {
            this.minY = v.y;
        }

        if (v.z < this.minZ) {
            this.minZ = v.z;
        }

        if (v.x > this.maxX) {
            this.maxX = v.x;
        }

        if (v.y > this.maxY) {
            this.maxY = v.y;
        }

        if (v.z > this.maxZ) {
            this.maxZ = v.z;
        }
    }

    public void checkMinMaxFinal() {
        if (this.minX < this.min) {
            this.min = this.minX;
        }

        if (this.minY < this.min) {
            this.min = this.minY;
        }

        if (this.minZ < this.min) {
            this.min = this.minZ;
        }

        if (this.maxX > this.max) {
            this.max = this.maxX;
        }

        if (this.maxY > this.max) {
            this.max = this.maxY;
        }

        if (this.maxZ > this.max) {
            this.max = this.maxZ;
        }

        this.sizeX = this.maxX - this.minX;
        this.sizeY = this.maxY - this.minY;
        this.sizeZ = this.maxZ - this.minZ;
        this.size = this.max - this.min;
    }

    /** All parsed groups, in file order — the dependent renderer walks these. */
    public abstract List<ModelGroup> groups();

    public abstract int getVertexNum();

    public abstract int getFaceNum();

    public boolean containsPart(String partName) {
        for (ModelGroup groupObject : groups()) {
            if (partName.equalsIgnoreCase(groupObject.name)) {
                return true;
            }
        }

        return false;
    }
}
