package mcheli.block;

import mcheli.MCH_Lib;
import net.minecraft.tileentity.TileEntity;

public class MCH_DraftingTableTileEntity extends TileEntity {
   public int getBlockMetadata() {
      if (this.blockMetadata == -1) {
         this.blockMetadata = this.worldObj.getBlockMetadata(this.xCoord, this.yCoord, this.zCoord);
         MCH_Lib.DbgLog(this.worldObj, "MCH_DraftingTableTileEntity.getBlockMetadata : %d(0x%08X)", this.blockMetadata, this.blockMetadata);
      }

      return this.blockMetadata;
   }
}
