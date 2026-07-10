/*
 * Decompiled with CFR 0.152.
 */
package mcheli.wrapper;

import net.minecraft.client.particle.EntityFX;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

public abstract class W_EntityFX
extends EntityFX {
    public W_EntityFX(World par1World, double par2, double par4, double par6) {
        super(par1World, par2, par4, par6);
    }

    public W_EntityFX(World par1World, double par2, double par4, double par6, double par8, double par10, double par12) {
        super(par1World, par2, par4, par6, par8, par10, par12);
    }

    public AxisAlignedBB func_70046_E() {
        return this.field_70121_D;
    }

    public void setIcon(IIcon icon) {
        this.func_110125_a(icon);
    }

    protected void doBlockCollisions() {
        super.func_145775_I();
    }
}

