package mcheli.wrapper;

import net.minecraft.item.Item;
import net.minecraft.stats.Achievement;

public class W_Achievement {
   public static Achievement registerAchievement(String par1, String par2Str, int par3, int par4, Item par5Item, Achievement par6Achievement) {
      return new Achievement(par1, par2Str, par3, par4, par5Item, par6Achievement).initIndependentStat().registerStat();
   }
}
