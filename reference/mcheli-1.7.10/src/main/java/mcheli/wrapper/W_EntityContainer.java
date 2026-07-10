package mcheli.wrapper;

import mcheli.MCH_Lib;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

public abstract class W_EntityContainer extends W_Entity implements IInventory {
   public static final int MAX_INVENTORY_SIZE = 54;
   private ItemStack[] containerItems;
   public boolean dropContentsWhenDead = true;

   public W_EntityContainer(World par1World) {
      super(par1World);
      this.containerItems = new ItemStack[54];
   }

   @Override
   protected void entityInit() {
   }

   public ItemStack getStackInSlot(int par1) {
      return this.containerItems[par1];
   }

   public int getUsingSlotNum() {
      int numUsingSlot = 0;
      if (this.containerItems == null) {
         numUsingSlot = 0;
      } else {
         int n = this.getSizeInventory();
         numUsingSlot = 0;

         for (int i = 0; i < n && i < this.containerItems.length; i++) {
            if (this.getStackInSlot(i) != null) {
               numUsingSlot++;
            }
         }
      }

      return numUsingSlot;
   }

   public ItemStack decrStackSize(int par1, int par2) {
      if (this.containerItems[par1] != null) {
         if (this.containerItems[par1].stackSize <= par2) {
            ItemStack itemstack = this.containerItems[par1];
            this.containerItems[par1] = null;
            return itemstack;
         }

         ItemStack itemstack = this.containerItems[par1].splitStack(par2);
         if (this.containerItems[par1].stackSize == 0) {
            this.containerItems[par1] = null;
         }

         return itemstack;
      } else {
         return null;
      }
   }

   public ItemStack getStackInSlotOnClosing(int par1) {
      if (this.containerItems[par1] != null) {
         ItemStack itemstack = this.containerItems[par1];
         this.containerItems[par1] = null;
         return itemstack;
      } else {
         return null;
      }
   }

   public void setInventorySlotContents(int par1, ItemStack par2ItemStack) {
      this.containerItems[par1] = par2ItemStack;
      if (par2ItemStack != null && par2ItemStack.stackSize > this.getInventoryStackLimit()) {
         par2ItemStack.stackSize = this.getInventoryStackLimit();
      }

      this.markDirty();
   }

   public void onInventoryChanged() {
   }

   public boolean isUseableByPlayer(EntityPlayer par1EntityPlayer) {
      return this.isDead ? false : par1EntityPlayer.getDistanceSqToEntity(this) <= 64.0;
   }

   public void openChest() {
   }

   public void closeChest() {
   }

   public boolean isItemValidForSlot(int par1, ItemStack par2ItemStack) {
      return true;
   }

   public boolean isStackValidForSlot(int par1, ItemStack par2ItemStack) {
      return true;
   }

   public String getInvName() {
      return "Inventory";
   }

   public String getInventoryName() {
      return this.getInvName();
   }

   public boolean isInvNameLocalized() {
      return false;
   }

   public boolean hasCustomInventoryName() {
      return this.isInvNameLocalized();
   }

   public int getInventoryStackLimit() {
      return 64;
   }

   public void setDead() {
      if (this.dropContentsWhenDead && !this.worldObj.isRemote) {
         for (int i = 0; i < this.getSizeInventory(); i++) {
            ItemStack itemstack = this.getStackInSlot(i);
            if (itemstack != null) {
               float x = this.rand.nextFloat() * 0.8F + 0.1F;
               float y = this.rand.nextFloat() * 0.8F + 0.1F;
               float z = this.rand.nextFloat() * 0.8F + 0.1F;

               while (itemstack.stackSize > 0) {
                  int j = this.rand.nextInt(21) + 10;
                  if (j > itemstack.stackSize) {
                     j = itemstack.stackSize;
                  }

                  itemstack.stackSize -= j;
                  EntityItem entityitem = new EntityItem(
                     this.worldObj,
                     this.posX + x,
                     this.posY + y,
                     this.posZ + z,
                     new ItemStack(itemstack.getItem(), j, itemstack.getItemDamage())
                  );
                  if (itemstack.hasTagCompound()) {
                     entityitem.getEntityItem().setTagCompound((NBTTagCompound)itemstack.getTagCompound().copy());
                  }

                  float f3 = 0.05F;
                  entityitem.motionX = (float)this.rand.nextGaussian() * f3;
                  entityitem.motionY = (float)this.rand.nextGaussian() * f3 + 0.2F;
                  entityitem.motionZ = (float)this.rand.nextGaussian() * f3;
                  this.worldObj.spawnEntityInWorld(entityitem);
               }
            }
         }
      }

      super.setDead();
   }

   protected void writeEntityToNBT(NBTTagCompound par1NBTTagCompound) {
      NBTTagList nbttaglist = new NBTTagList();

      for (int i = 0; i < this.containerItems.length; i++) {
         if (this.containerItems[i] != null) {
            NBTTagCompound nbttagcompound1 = new NBTTagCompound();
            nbttagcompound1.setByte("Slot", (byte)i);
            this.containerItems[i].writeToNBT(nbttagcompound1);
            nbttaglist.appendTag(nbttagcompound1);
         }
      }

      par1NBTTagCompound.setTag("Items", nbttaglist);
   }

   protected void readEntityFromNBT(NBTTagCompound par1NBTTagCompound) {
      NBTTagList nbttaglist = W_NBTTag.getTagList(par1NBTTagCompound, "Items", 10);
      this.containerItems = new ItemStack[this.getSizeInventory()];
      MCH_Lib.DbgLog(this.worldObj, "W_EntityContainer.readEntityFromNBT.InventorySize = %d", this.getSizeInventory());

      for (int i = 0; i < nbttaglist.tagCount(); i++) {
         NBTTagCompound nbttagcompound1 = W_NBTTag.tagAt(nbttaglist, i);
         int j = nbttagcompound1.getByte("Slot") & 255;
         if (j >= 0 && j < this.containerItems.length) {
            this.containerItems[j] = ItemStack.loadItemStackFromNBT(nbttagcompound1);
         }
      }
   }

   public void travelToDimension(int par1) {
      this.dropContentsWhenDead = false;
      super.travelToDimension(par1);
   }

   public boolean openInventory(EntityPlayer player) {
      if (!this.worldObj.isRemote && this.getSizeInventory() > 0) {
         player.displayGUIChest(this);
         return true;
      } else {
         return false;
      }
   }

   public void openInventory() {
   }

   public void closeInventory() {
   }

   public void markDirty() {
   }

   public int getSizeInventory() {
      return 0;
   }
}
