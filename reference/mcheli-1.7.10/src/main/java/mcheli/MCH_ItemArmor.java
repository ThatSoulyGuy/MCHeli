package mcheli;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import mcheli.wrapper.W_ItemArmor;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;

public class MCH_ItemArmor extends W_ItemArmor {
   public static final String HELMET_TEXTURE = "mcheli:textures/helicopters/ah-64.png";
   public static final String CHESTPLATE_TEXTURE = "mcheli:textures/armor/plate.png";
   public static final String LEGGINGS_TEXTURE = "mcheli:textures/armor/leg.png";
   public static final String BOOTS_TEXTURE = "mcheli:textures/armor/boots.png";
   public static MCH_TEST_ModelBiped model = null;

   public MCH_ItemArmor(int par1, int par3, int par4) {
      super(par1, par3, par4);
   }

   public String getArmorTexture(ItemStack stack, Entity entity, int slot, String layer) {
      if (slot == 0) {
         return "mcheli:textures/helicopters/ah-64.png";
      } else if (slot == 1) {
         return "mcheli:textures/armor/plate.png";
      } else if (slot == 2) {
         return "mcheli:textures/armor/leg.png";
      } else {
         return slot == 3 ? "mcheli:textures/armor/boots.png" : "none";
      }
   }

   @SideOnly(Side.CLIENT)
   public ModelBiped getArmorModel(EntityLivingBase entityLiving, ItemStack itemStack, int armorSlot) {
      if (model == null) {
         model = new MCH_TEST_ModelBiped();
      }

      return armorSlot == 0 ? model : null;
   }
}
