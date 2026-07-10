/*
 * Decompiled with CFR 0.152.
 */
package mcheli.wrapper;

import mcheli.MCH_Lib;
import mcheli.wrapper.W_Entity;
import mcheli.wrapper.W_NBTTag;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

public abstract class W_EntityContainer
extends W_Entity
implements IInventory {
    public static final int MAX_INVENTORY_SIZE = 54;
    private ItemStack[] containerItems = new ItemStack[54];
    public boolean dropContentsWhenDead = true;

    public W_EntityContainer(World par1World) {
        super(par1World);
    }

    protected void func_70088_a() {
    }

    public ItemStack func_70301_a(int par1) {
        return this.containerItems[par1];
    }

    public int getUsingSlotNum() {
        int numUsingSlot = 0;
        if (this.containerItems == null) {
            numUsingSlot = 0;
        } else {
            int n = this.func_70302_i_();
            numUsingSlot = 0;
            for (int i = 0; i < n && i < this.containerItems.length; ++i) {
                if (this.func_70301_a(i) == null) continue;
                ++numUsingSlot;
            }
        }
        return numUsingSlot;
    }

    public ItemStack func_70298_a(int par1, int par2) {
        if (this.containerItems[par1] != null) {
            if (this.containerItems[par1].field_77994_a <= par2) {
                ItemStack itemstack = this.containerItems[par1];
                this.containerItems[par1] = null;
                return itemstack;
            }
            ItemStack itemstack = this.containerItems[par1].func_77979_a(par2);
            if (this.containerItems[par1].field_77994_a == 0) {
                this.containerItems[par1] = null;
            }
            return itemstack;
        }
        return null;
    }

    public ItemStack func_70304_b(int par1) {
        if (this.containerItems[par1] != null) {
            ItemStack itemstack = this.containerItems[par1];
            this.containerItems[par1] = null;
            return itemstack;
        }
        return null;
    }

    public void func_70299_a(int par1, ItemStack par2ItemStack) {
        this.containerItems[par1] = par2ItemStack;
        if (par2ItemStack != null && par2ItemStack.field_77994_a > this.func_70297_j_()) {
            par2ItemStack.field_77994_a = this.func_70297_j_();
        }
        this.func_70296_d();
    }

    public void onInventoryChanged() {
    }

    public boolean func_70300_a(EntityPlayer par1EntityPlayer) {
        return this.field_70128_L ? false : par1EntityPlayer.func_70068_e((Entity)this) <= 64.0;
    }

    public void openChest() {
    }

    public void closeChest() {
    }

    public boolean func_94041_b(int par1, ItemStack par2ItemStack) {
        return true;
    }

    public boolean isStackValidForSlot(int par1, ItemStack par2ItemStack) {
        return true;
    }

    public String getInvName() {
        return "Inventory";
    }

    public String func_145825_b() {
        return this.getInvName();
    }

    public boolean isInvNameLocalized() {
        return false;
    }

    public boolean func_145818_k_() {
        return this.isInvNameLocalized();
    }

    public int func_70297_j_() {
        return 64;
    }

    public void func_70106_y() {
        if (this.dropContentsWhenDead && !this.field_70170_p.field_72995_K) {
            for (int i = 0; i < this.func_70302_i_(); ++i) {
                ItemStack itemstack = this.func_70301_a(i);
                if (itemstack == null) continue;
                float x = this.field_70146_Z.nextFloat() * 0.8f + 0.1f;
                float y = this.field_70146_Z.nextFloat() * 0.8f + 0.1f;
                float z = this.field_70146_Z.nextFloat() * 0.8f + 0.1f;
                while (itemstack.field_77994_a > 0) {
                    int j = this.field_70146_Z.nextInt(21) + 10;
                    if (j > itemstack.field_77994_a) {
                        j = itemstack.field_77994_a;
                    }
                    itemstack.field_77994_a -= j;
                    EntityItem entityitem = new EntityItem(this.field_70170_p, this.field_70165_t + (double)x, this.field_70163_u + (double)y, this.field_70161_v + (double)z, new ItemStack(itemstack.func_77973_b(), j, itemstack.func_77960_j()));
                    if (itemstack.func_77942_o()) {
                        entityitem.func_92059_d().func_77982_d((NBTTagCompound)itemstack.func_77978_p().func_74737_b());
                    }
                    float f3 = 0.05f;
                    entityitem.field_70159_w = (float)this.field_70146_Z.nextGaussian() * f3;
                    entityitem.field_70181_x = (float)this.field_70146_Z.nextGaussian() * f3 + 0.2f;
                    entityitem.field_70179_y = (float)this.field_70146_Z.nextGaussian() * f3;
                    this.field_70170_p.func_72838_d((Entity)entityitem);
                }
            }
        }
        super.func_70106_y();
    }

    protected void func_70014_b(NBTTagCompound par1NBTTagCompound) {
        NBTTagList nbttaglist = new NBTTagList();
        for (int i = 0; i < this.containerItems.length; ++i) {
            if (this.containerItems[i] == null) continue;
            NBTTagCompound nbttagcompound1 = new NBTTagCompound();
            nbttagcompound1.func_74774_a("Slot", (byte)i);
            this.containerItems[i].func_77955_b(nbttagcompound1);
            nbttaglist.func_74742_a((NBTBase)nbttagcompound1);
        }
        par1NBTTagCompound.func_74782_a("Items", (NBTBase)nbttaglist);
    }

    protected void func_70037_a(NBTTagCompound par1NBTTagCompound) {
        NBTTagList nbttaglist = W_NBTTag.getTagList((NBTTagCompound)par1NBTTagCompound, (String)"Items", (int)10);
        this.containerItems = new ItemStack[this.func_70302_i_()];
        MCH_Lib.DbgLog((World)this.field_70170_p, (String)"W_EntityContainer.readEntityFromNBT.InventorySize = %d", (Object[])new Object[]{this.func_70302_i_()});
        for (int i = 0; i < nbttaglist.func_74745_c(); ++i) {
            NBTTagCompound nbttagcompound1 = W_NBTTag.tagAt((NBTTagList)nbttaglist, (int)i);
            int j = nbttagcompound1.func_74771_c("Slot") & 0xFF;
            if (j < 0 || j >= this.containerItems.length) continue;
            this.containerItems[j] = ItemStack.func_77949_a((NBTTagCompound)nbttagcompound1);
        }
    }

    public void func_71027_c(int par1) {
        this.dropContentsWhenDead = false;
        super.func_71027_c(par1);
    }

    public boolean openInventory(EntityPlayer player) {
        if (!this.field_70170_p.field_72995_K && this.func_70302_i_() > 0) {
            player.func_71007_a((IInventory)this);
            return true;
        }
        return false;
    }

    public void func_70295_k_() {
    }

    public void func_70305_f() {
    }

    public void func_70296_d() {
    }

    public int func_70302_i_() {
        return 0;
    }
}

