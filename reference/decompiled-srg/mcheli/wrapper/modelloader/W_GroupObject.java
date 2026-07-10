/*
 * Decompiled with CFR 0.152.
 */
package mcheli.wrapper.modelloader;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.ArrayList;
import mcheli.wrapper.modelloader.W_Face;
import net.minecraft.client.renderer.Tessellator;

@SideOnly(value=Side.CLIENT)
public class W_GroupObject {
    public String name;
    public ArrayList<W_Face> faces = new ArrayList();
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
            Tessellator tessellator = Tessellator.field_78398_a;
            tessellator.func_78371_b(this.glDrawingMode);
            this.render(tessellator);
            tessellator.func_78381_a();
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

