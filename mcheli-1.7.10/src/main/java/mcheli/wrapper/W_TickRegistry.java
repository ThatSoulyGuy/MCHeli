package mcheli.wrapper;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;

public class W_TickRegistry {
   public static void registerTickHandler(W_TickHandler handler, Side side) {
      FMLCommonHandler.instance().bus().register(handler);
   }
}
