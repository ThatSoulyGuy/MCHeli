/*
 * Decompiled with CFR 0.152.
 */
package mcheli;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.io.File;
import java.util.HashMap;
import java.util.Random;
import mcheli.MCH_MOD;
import mcheli.wrapper.W_ModelBase;
import mcheli.wrapper.W_ResourcePath;
import mcheli.wrapper.modelloader.W_ModelCustom;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraftforge.client.model.IModelCustom;

/*
 * Exception performing whole class analysis ignored.
 */
@SideOnly(value=Side.CLIENT)
public class MCH_ModelManager
extends W_ModelBase {
    private static MCH_ModelManager instance = new MCH_ModelManager();
    private static HashMap<String, IModelCustom> map;
    private static ModelRenderer defaultModel;
    private static boolean forceReloadMode;
    private static Random rand;

    private MCH_ModelManager() {
        map = new HashMap();
        defaultModel = null;
        defaultModel = new ModelRenderer((ModelBase)this, 0, 0);
        defaultModel.func_78790_a(-5.0f, -5.0f, -5.0f, 10, 10, 10, 0.0f);
    }

    public static void setForceReloadMode(boolean b) {
        forceReloadMode = b;
    }

    public static IModelCustom load(String path, String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        return MCH_ModelManager.load((String)(path + "/" + name));
    }

    public static IModelCustom load(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        IModelCustom obj = (IModelCustom)map.get(name);
        if (obj != null) {
            if (forceReloadMode) {
                map.remove(name);
            } else {
                return obj;
            }
        }
        IModelCustom model = null;
        try {
            String filePathMqo = "/assets/mcheli/models/" + name + ".mqo";
            String filePathObj = "/assets/mcheli/models/" + name + ".obj";
            String filePathTcn = "/assets/mcheli/models/" + name + ".tcn";
            if (new File(MCH_MOD.sourcePath + filePathMqo).exists()) {
                filePathMqo = W_ResourcePath.getModelPath() + "models/" + name + ".mqo";
                model = W_ModelBase.loadModel((String)filePathMqo);
            } else if (new File(MCH_MOD.sourcePath + filePathObj).exists()) {
                filePathObj = W_ResourcePath.getModelPath() + "models/" + name + ".obj";
                model = W_ModelBase.loadModel((String)filePathObj);
            } else if (new File(MCH_MOD.sourcePath + filePathTcn).exists()) {
                filePathTcn = W_ResourcePath.getModelPath() + "models/" + name + ".tcn";
                model = W_ModelBase.loadModel((String)filePathTcn);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            model = null;
        }
        if (model != null) {
            map.put(name, model);
            return model;
        }
        return null;
    }

    public static void render(String path, String name) {
        MCH_ModelManager.render((String)(path + "/" + name));
    }

    public static void render(String name) {
        IModelCustom model = (IModelCustom)map.get(name);
        if (model != null) {
            model.renderAll();
        } else if (defaultModel != null) {
            // empty if block
        }
    }

    public static void renderPart(String name, String partName) {
        IModelCustom model = (IModelCustom)map.get(name);
        if (model != null) {
            model.renderPart(partName);
        }
    }

    public static void renderLine(String path, String name, int startLine, int maxLine) {
        IModelCustom model = (IModelCustom)map.get(path + "/" + name);
        if (model instanceof W_ModelCustom) {
            ((W_ModelCustom)model).renderAllLine(startLine, maxLine);
        }
    }

    public static void render(String path, String name, int startFace, int maxFace) {
        IModelCustom model = (IModelCustom)map.get(path + "/" + name);
        if (model instanceof W_ModelCustom) {
            ((W_ModelCustom)model).renderAll(startFace, maxFace);
        }
    }

    public static int getVertexNum(String path, String name) {
        IModelCustom model = (IModelCustom)map.get(path + "/" + name);
        if (model instanceof W_ModelCustom) {
            return ((W_ModelCustom)model).getVertexNum();
        }
        return 0;
    }

    public static W_ModelCustom get(String path, String name) {
        IModelCustom model = (IModelCustom)map.get(path + "/" + name);
        if (model instanceof W_ModelCustom) {
            return (W_ModelCustom)model;
        }
        return null;
    }

    public static W_ModelCustom getRandome() {
        int size = map.size();
        for (int i = 0; i < 10; ++i) {
            int idx = 0;
            int index = rand.nextInt(size);
            for (IModelCustom model : map.values()) {
                if (idx >= index && model instanceof W_ModelCustom) {
                    return (W_ModelCustom)model;
                }
                ++idx;
            }
        }
        return null;
    }

    public static boolean containsModel(String path, String name) {
        return MCH_ModelManager.containsModel((String)(path + "/" + name));
    }

    public static boolean containsModel(String name) {
        return map.containsKey(name);
    }

    static {
        forceReloadMode = false;
        rand = new Random();
    }
}

