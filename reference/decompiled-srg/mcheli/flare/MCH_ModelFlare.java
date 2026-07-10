/*
 * Decompiled with CFR 0.152.
 */
package mcheli.flare;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import mcheli.wrapper.W_ModelBase;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;

@SideOnly(value=Side.CLIENT)
public class MCH_ModelFlare
extends W_ModelBase {
    public ModelRenderer model;

    public MCH_ModelFlare() {
        int SIZE = 4;
        this.model = new ModelRenderer((ModelBase)this, 0, 0).func_78787_b(4, 4);
        this.model.func_78790_a(-2.0f, -2.0f, -2.0f, 4, 4, 4, 0.0f);
    }

    public void renderModel(double yaw, double pitch, float par7) {
        this.model.func_78785_a(par7);
    }
}

