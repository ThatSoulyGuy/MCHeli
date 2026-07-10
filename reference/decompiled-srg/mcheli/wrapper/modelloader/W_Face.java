/*
 * Decompiled with CFR 0.152.
 */
package mcheli.wrapper.modelloader;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import mcheli.wrapper.modelloader.W_TextureCoordinate;
import mcheli.wrapper.modelloader.W_Vertex;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.Vec3;

@SideOnly(value=Side.CLIENT)
public class W_Face {
    public int[] verticesID;
    public W_Vertex[] vertices;
    public W_Vertex[] vertexNormals;
    public W_Vertex faceNormal;
    public W_TextureCoordinate[] textureCoordinates;

    public W_Face copy() {
        W_Face f = new W_Face();
        return f;
    }

    public void addFaceForRender(Tessellator tessellator) {
        this.addFaceForRender(tessellator, 0.0f);
    }

    public void addFaceForRender(Tessellator tessellator, float textureOffset) {
        if (this.faceNormal == null) {
            this.faceNormal = this.calculateFaceNormal();
        }
        tessellator.func_78375_b(this.faceNormal.x, this.faceNormal.y, this.faceNormal.z);
        float averageU = 0.0f;
        float averageV = 0.0f;
        if (this.textureCoordinates != null && this.textureCoordinates.length > 0) {
            for (int i = 0; i < this.textureCoordinates.length; ++i) {
                averageU += this.textureCoordinates[i].u;
                averageV += this.textureCoordinates[i].v;
            }
            averageU /= (float)this.textureCoordinates.length;
            averageV /= (float)this.textureCoordinates.length;
        }
        for (int i = 0; i < this.vertices.length; ++i) {
            if (this.textureCoordinates != null && this.textureCoordinates.length > 0) {
                float offsetU = textureOffset;
                float offsetV = textureOffset;
                if (this.textureCoordinates[i].u > averageU) {
                    offsetU = -offsetU;
                }
                if (this.textureCoordinates[i].v > averageV) {
                    offsetV = -offsetV;
                }
                if (this.vertexNormals != null && i < this.vertexNormals.length) {
                    tessellator.func_78375_b(this.vertexNormals[i].x, this.vertexNormals[i].y, this.vertexNormals[i].z);
                }
                tessellator.func_78374_a((double)this.vertices[i].x, (double)this.vertices[i].y, (double)this.vertices[i].z, (double)(this.textureCoordinates[i].u + offsetU), (double)(this.textureCoordinates[i].v + offsetV));
                continue;
            }
            tessellator.func_78377_a((double)this.vertices[i].x, (double)this.vertices[i].y, (double)this.vertices[i].z);
        }
    }

    public W_Vertex calculateFaceNormal() {
        Vec3 v1 = Vec3.func_72443_a((double)(this.vertices[1].x - this.vertices[0].x), (double)(this.vertices[1].y - this.vertices[0].y), (double)(this.vertices[1].z - this.vertices[0].z));
        Vec3 v2 = Vec3.func_72443_a((double)(this.vertices[2].x - this.vertices[0].x), (double)(this.vertices[2].y - this.vertices[0].y), (double)(this.vertices[2].z - this.vertices[0].z));
        Vec3 normalVector = null;
        normalVector = v1.func_72431_c(v2).func_72432_b();
        return new W_Vertex((float)normalVector.field_72450_a, (float)normalVector.field_72448_b, (float)normalVector.field_72449_c);
    }
}

