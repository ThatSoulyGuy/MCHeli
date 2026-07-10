package mcheli.wrapper;

import cpw.mods.fml.common.FMLLog;
import mcheli.wrapper.modelloader.W_MqoModelLoader;
import mcheli.wrapper.modelloader.W_ObjModelLoader;
import net.minecraft.client.model.ModelBase;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.AdvancedModelLoader;
import net.minecraftforge.client.model.IModelCustom;
import net.minecraftforge.client.model.IModelCustomLoader;
import net.minecraftforge.client.model.ModelFormatException;

public abstract class W_ModelBase extends ModelBase {
   private static IModelCustomLoader objLoader = new W_ObjModelLoader();
   private static IModelCustomLoader mqoLoader = new W_MqoModelLoader();

   public static IModelCustom loadModel(String name) throws IllegalArgumentException, ModelFormatException {
      ResourceLocation resource = new ResourceLocation("mcheli", name);
      String path = resource.getResourcePath();
      int i = path.lastIndexOf(46);
      if (i == -1) {
         FMLLog.severe("The resource name %s is not valid", new Object[]{resource});
         throw new IllegalArgumentException("The resource name is not valid");
      } else {
         String test = path.substring(i);
         if (path.substring(i).equalsIgnoreCase(".mqo")) {
            return mqoLoader.loadInstance(resource);
         } else {
            return path.substring(i).equalsIgnoreCase(".obj") ? objLoader.loadInstance(resource) : AdvancedModelLoader.loadModel(resource);
         }
      }
   }
}
