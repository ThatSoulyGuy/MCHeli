package mcheli.wrapper.modelloader;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.ArrayList;
import net.minecraft.client.renderer.Tessellator;

@SideOnly(Side.CLIENT)
public class W_GroupObject {
   public String name;
   public ArrayList<W_Face> faces = new ArrayList<>();
   public int glDrawingMode;

   public W_GroupObject() {
      this("");
   }

   public W_GroupObject(String name) {
      this(name, -1);
   }

   public W_GroupObject(String name, int glDrawingMode) {
      this.name = name;
      this.glDrawingMode = glDrawingMode;
   }

   public void render() {
      if (this.faces.size() > 0) {
         Tessellator tessellator = Tessellator.instance;
         tessellator.startDrawing(this.glDrawingMode);
         this.render(tessellator);
         tessellator.draw();
      }
   }

   public void render(Tessellator tessellator) {
      if (this.faces.size() > 0) {
         for (W_Face face : this.faces) {
            face.addFaceForRender(tessellator);
         }
      }
   }
}
