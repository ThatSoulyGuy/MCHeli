/*
 * Decompiled with CFR 0.152.
 */
package mcheli.debug;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import mcheli.wrapper.W_ModelBase;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;

@SideOnly(value=Side.CLIENT)
public class MCH_ModelTest
extends W_ModelBase {
    public ModelRenderer test;

    public MCH_ModelTest() {
        int SIZE = 10;
        this.test = new ModelRenderer((ModelBase)this, 0, 0);
        this.test.func_78790_a(-5.0f, -5.0f, -5.0f, 10, 10, 10, 0.0f);
    }

    public void renderModel(double yaw, double pitch, float par7) {
        this.test.func_78785_a(par7);
    }
}

