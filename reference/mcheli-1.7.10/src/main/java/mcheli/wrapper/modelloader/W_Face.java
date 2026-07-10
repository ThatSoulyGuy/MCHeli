package mcheli.wrapper.modelloader;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.Vec3;

@SideOnly(Side.CLIENT)
public class W_Face {
   public int[] verticesID;
   public W_Vertex[] vertices;
   public W_Vertex[] vertexNormals;
   public W_Vertex faceNormal;
   public W_TextureCoordinate[] textureCoordinates;

   public W_Face copy() {
      return new W_Face();
   }

   public void addFaceForRender(Tessellator tessellator) {
      this.addFaceForRender(tessellator, 0.0F);
   }

   public void addFaceForRender(Tessellator tessellator, float textureOffset) {
      if (this.faceNormal == null) {
         this.faceNormal = this.calculateFaceNormal();
      }

      tessellator.setNormal(this.faceNormal.x, this.faceNormal.y, this.faceNormal.z);
      float averageU = 0.0F;
      float averageV = 0.0F;
      if (this.textureCoordinates != null && this.textureCoordinates.length > 0) {
         for (int i = 0; i < this.textureCoordinates.length; i++) {
            averageU += this.textureCoordinates[i].u;
            averageV += this.textureCoordinates[i].v;
         }

         averageU /= this.textureCoordinates.length;
         averageV /= this.textureCoordinates.length;
      }

      for (int i = 0; i < this.vertices.length; i++) {
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
               tessellator.setNormal(this.vertexNormals[i].x, this.vertexNormals[i].y, this.vertexNormals[i].z);
            }

            tessellator.addVertexWithUV(
               this.vertices[i].x, this.vertices[i].y, this.vertices[i].z, this.textureCoordinates[i].u + offsetU, this.textureCoordinates[i].v + offsetV
            );
         } else {
            tessellator.addVertex(this.vertices[i].x, this.vertices[i].y, this.vertices[i].z);
         }
      }
   }

   public W_Vertex calculateFaceNormal() {
      Vec3 v1 = Vec3.createVectorHelper(this.vertices[1].x - this.vertices[0].x, this.vertices[1].y - this.vertices[0].y, this.vertices[1].z - this.vertices[0].z);
      Vec3 v2 = Vec3.createVectorHelper(this.vertices[2].x - this.vertices[0].x, this.vertices[2].y - this.vertices[0].y, this.vertices[2].z - this.vertices[0].z);
      Vec3 normalVector = null;
      normalVector = v1.crossProduct(v2).normalize();
      return new W_Vertex((float)normalVector.xCoord, (float)normalVector.yCoord, (float)normalVector.zCoord);
   }
}
