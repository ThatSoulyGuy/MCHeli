/*
 * Decompiled with CFR 0.152.
 */
package mcheli.block;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.Random;
import mcheli.MCH_Lib;
import mcheli.MCH_MOD;
import mcheli.block.MCH_DraftingTableTileEntity;
import mcheli.wrapper.IconRegister;
import mcheli.wrapper.W_Block;
import mcheli.wrapper.W_BlockContainer;
import mcheli.wrapper.W_Item;
import mcheli.wrapper.W_WorldFunc;
import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public class MCH_DraftingTableBlock
extends W_BlockContainer
implements ITileEntityProvider {
    private final boolean isLighting;

    public MCH_DraftingTableBlock(int blockId, boolean p_i45421_1_) {
        super(blockId, Material.field_151573_f);
        this.func_149672_a(W_Block.field_149777_j);
        this.func_149711_c(0.2f);
        this.isLighting = p_i45421_1_;
        if (p_i45421_1_) {
            this.func_149715_a(1.0f);
        }
    }

    public boolean func_149727_a(World world, int x, int y, int z, EntityPlayer player, int par6, float par7, float par8, float par9) {
        if (!world.field_72995_K) {
            if (!player.func_70093_af()) {
                MCH_Lib.DbgLog((World)player.field_70170_p, (String)"MCH_DraftingTableGui.MCH_DraftingTableGui OPEN GUI (%d, %d, %d)", (Object[])new Object[]{x, y, z});
                player.openGui((Object)MCH_MOD.instance, 4, world, x, y, z);
            } else {
                int yaw = world.func_72805_g(x, y, z);
                MCH_Lib.DbgLog((World)world, (String)"MCH_DraftingTableBlock.onBlockActivated:yaw=%d Light %s", (Object[])new Object[]{yaw, this.isLighting ? "OFF->ON" : "ON->OFF"});
                if (this.isLighting) {
                    W_WorldFunc.setBlock((World)world, (int)x, (int)y, (int)z, (Block)MCH_MOD.blockDraftingTable, (int)(yaw + 180), (int)2);
                } else {
                    W_WorldFunc.setBlock((World)world, (int)x, (int)y, (int)z, (Block)MCH_MOD.blockDraftingTableLit, (int)(yaw + 180), (int)2);
                }
                world.func_72921_c(x, y, z, yaw, 2);
                world.func_72908_a((double)x + 0.5, (double)y + 0.5, (double)z + 0.5, "random.click", 0.3f, 0.5f);
            }
        }
        return true;
    }

    public TileEntity func_149915_a(World world, int a) {
        return new MCH_DraftingTableTileEntity();
    }

    public TileEntity createNewTileEntity(World world) {
        return new MCH_DraftingTableTileEntity();
    }

    public boolean func_149646_a(IBlockAccess p_149646_1_, int p_149646_2_, int p_149646_3_, int p_149646_4_, int p_149646_5_) {
        return true;
    }

    public boolean func_149686_d() {
        return false;
    }

    public boolean func_149662_c() {
        return false;
    }

    public boolean canHarvestBlock(EntityPlayer player, int meta) {
        return true;
    }

    public boolean canRenderInPass(int pass) {
        return false;
    }

    public int func_149656_h() {
        return 1;
    }

    public void func_149689_a(World world, int par2, int par3, int par4, EntityLivingBase entity, ItemStack itemStack) {
        float pyaw = (float)MCH_Lib.getRotate360((double)entity.field_70177_z);
        int yaw = (int)((pyaw += 22.5f) / 45.0f);
        if (yaw < 0) {
            yaw = yaw % 8 + 8;
        }
        world.func_72921_c(par2, par3, par4, yaw, 2);
        MCH_Lib.DbgLog((World)world, (String)"MCH_DraftingTableBlock.onBlockPlacedBy:yaw=%d", (Object[])new Object[]{yaw});
    }

    public boolean func_149710_n() {
        return true;
    }

    @SideOnly(value=Side.CLIENT)
    public void func_149651_a(IIconRegister par1IconRegister) {
        this.field_149761_L = par1IconRegister.func_94245_a("mcheli:drafting_table");
    }

    public void registerIcons(IconRegister par1IconRegister) {
        this.field_149761_L = par1IconRegister.registerIcon("mcheli:drafting_table");
    }

    public Item func_149650_a(int p_149650_1_, Random p_149650_2_, int p_149650_3_) {
        return W_Item.getItemFromBlock((Block)MCH_MOD.blockDraftingTable);
    }

    @SideOnly(value=Side.CLIENT)
    public Item func_149694_d(World world, int p_149694_2_, int p_149694_3_, int p_149694_4_) {
        return W_Item.getItemFromBlock((Block)MCH_MOD.blockDraftingTable);
    }

    protected ItemStack func_149644_j(int p_149644_1_) {
        return new ItemStack((Block)MCH_MOD.blockDraftingTable);
    }
}

