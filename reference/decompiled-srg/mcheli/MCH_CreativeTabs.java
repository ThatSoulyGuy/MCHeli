/*
 * Decompiled with CFR 0.152.
 */
package mcheli;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import mcheli.MCH_CreativeTabs;
import mcheli.wrapper.W_Item;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public class MCH_CreativeTabs
extends CreativeTabs {
    private List<Item> iconItems = new ArrayList();
    private Item lastItem;
    private int currentIconIndex = 0;
    private int switchItemWait = 0;
    private Item fixedItem = null;

    public MCH_CreativeTabs(String label) {
        super(label);
    }

    public void setFixedIconItem(String itemName) {
        if (itemName.indexOf(58) >= 0) {
            this.fixedItem = W_Item.getItemByName((String)itemName);
            if (this.fixedItem != null) {
                this.fixedItem.func_111206_d(itemName);
            }
        } else {
            this.fixedItem = W_Item.getItemByName((String)("mcheli:" + itemName));
            if (this.fixedItem != null) {
                this.fixedItem.func_111206_d("mcheli:" + itemName);
            }
        }
    }

    public Item func_78016_d() {
        if (this.iconItems.size() <= 0) {
            return null;
        }
        this.currentIconIndex = (this.currentIconIndex + 1) % this.iconItems.size();
        return (Item)this.iconItems.get(this.currentIconIndex);
    }

    public ItemStack func_151244_d() {
        if (this.fixedItem != null) {
            return new ItemStack(this.fixedItem, 1, 0);
        }
        if (this.switchItemWait > 0) {
            --this.switchItemWait;
        } else {
            this.lastItem = this.func_78016_d();
            this.switchItemWait = 60;
        }
        if (this.lastItem == null) {
            this.lastItem = W_Item.getItemByName((String)"iron_block");
        }
        return new ItemStack(this.lastItem, 1, 0);
    }

    @SideOnly(value=Side.CLIENT)
    public void func_78018_a(List list) {
        super.func_78018_a(list);
        1 cmp = new /* Unavailable Anonymous Inner Class!! */;
        Collections.sort(list, cmp);
    }

    public void addIconItem(Item i) {
        if (i != null) {
            this.iconItems.add(i);
        }
    }

    public String func_78024_c() {
        return "MC Heli";
    }
}

