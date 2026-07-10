/*
 * Decompiled with CFR 0.152.
 */
package mcheli;

import cpw.mods.fml.common.registry.GameRegistry;
import java.util.ArrayList;
import java.util.List;
import mcheli.MCH_Config;
import mcheli.MCH_IRecipeList;
import mcheli.MCH_MOD;
import mcheli.MCH_RecipeFuel;
import mcheli.MCH_RecipeReloadRangeFinder;
import mcheli.aircraft.MCH_AircraftInfo;
import mcheli.aircraft.MCH_AircraftInfoManager;
import mcheli.helicopter.MCH_HeliInfo;
import mcheli.helicopter.MCH_HeliInfoManager;
import mcheli.plane.MCP_PlaneInfo;
import mcheli.plane.MCP_PlaneInfoManager;
import mcheli.tank.MCH_TankInfo;
import mcheli.tank.MCH_TankInfoManager;
import mcheli.throwable.MCH_ThrowableInfoManager;
import mcheli.vehicle.MCH_VehicleInfo;
import mcheli.vehicle.MCH_VehicleInfoManager;
import mcheli.wrapper.W_Block;
import mcheli.wrapper.W_Item;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.item.crafting.ShapelessRecipes;

/*
 * Exception performing whole class analysis ignored.
 */
public class MCH_ItemRecipe
implements MCH_IRecipeList {
    private static final MCH_ItemRecipe instance = new MCH_ItemRecipe();
    private static List<IRecipe> commonItemRecipe = new ArrayList();

    private MCH_ItemRecipe() {
    }

    public static MCH_ItemRecipe getInstance() {
        return instance;
    }

    public int getRecipeListSize() {
        return commonItemRecipe.size();
    }

    public IRecipe getRecipe(int index) {
        return (IRecipe)commonItemRecipe.get(index);
    }

    private static void addRecipeList(IRecipe recipe) {
        if (recipe != null) {
            commonItemRecipe.add(recipe);
        }
    }

    private static void registerCommonItemRecipe() {
        commonItemRecipe.clear();
        GameRegistry.addRecipe((IRecipe)new MCH_RecipeFuel());
        MCH_ItemRecipe.addRecipeList((IRecipe)MCH_ItemRecipe.addRecipe((Item)MCH_MOD.itemFuel, (String)MCH_Config.ItemRecipe_Fuel.prmString));
        MCH_ItemRecipe.addRecipeList((IRecipe)MCH_ItemRecipe.addRecipe((Item)MCH_MOD.itemGLTD, (String)MCH_Config.ItemRecipe_GLTD.prmString));
        MCH_ItemRecipe.addRecipeList((IRecipe)MCH_ItemRecipe.addRecipe((Item)MCH_MOD.itemChain, (String)MCH_Config.ItemRecipe_Chain.prmString));
        MCH_ItemRecipe.addRecipeList((IRecipe)MCH_ItemRecipe.addRecipe((Item)MCH_MOD.itemParachute, (String)MCH_Config.ItemRecipe_Parachute.prmString));
        MCH_ItemRecipe.addRecipeList((IRecipe)MCH_ItemRecipe.addRecipe((Item)MCH_MOD.itemContainer, (String)MCH_Config.ItemRecipe_Container.prmString));
        for (int i = 0; i < MCH_MOD.itemUavStation.length; ++i) {
            MCH_ItemRecipe.addRecipeList((IRecipe)MCH_ItemRecipe.addRecipe((Item)MCH_MOD.itemUavStation[i], (String)MCH_Config.ItemRecipe_UavStation[i].prmString));
        }
        MCH_ItemRecipe.addRecipeList((IRecipe)MCH_ItemRecipe.addRecipe((Item)MCH_MOD.itemWrench, (String)MCH_Config.ItemRecipe_Wrench.prmString));
        MCH_ItemRecipe.addRecipeList((IRecipe)MCH_ItemRecipe.addRecipe((Item)MCH_MOD.itemRangeFinder, (String)MCH_Config.ItemRecipe_RangeFinder.prmString));
        GameRegistry.addRecipe((IRecipe)new MCH_RecipeReloadRangeFinder());
        MCH_ItemRecipe.addRecipeList((IRecipe)MCH_ItemRecipe.addRecipe((Item)MCH_MOD.itemStinger, (String)MCH_Config.ItemRecipe_Stinger.prmString));
        MCH_ItemRecipe.addRecipeList((IRecipe)MCH_ItemRecipe.addRecipe((Item)MCH_MOD.itemStingerBullet, (String)("2," + MCH_Config.ItemRecipe_StingerMissile.prmString)));
        MCH_ItemRecipe.addRecipeList((IRecipe)MCH_ItemRecipe.addRecipe((Item)MCH_MOD.itemJavelin, (String)MCH_Config.ItemRecipe_Javelin.prmString));
        MCH_ItemRecipe.addRecipeList((IRecipe)MCH_ItemRecipe.addRecipe((Item)MCH_MOD.itemJavelinBullet, (String)("2," + MCH_Config.ItemRecipe_JavelinMissile.prmString)));
        MCH_ItemRecipe.addRecipeList((IRecipe)MCH_ItemRecipe.addRecipe((Item)W_Item.getItemFromBlock((Block)MCH_MOD.blockDraftingTable), (String)MCH_Config.ItemRecipe_DraftingTable.prmString));
    }

    public static void registerItemRecipe() {
        MCH_HeliInfo info;
        MCH_ItemRecipe.registerCommonItemRecipe();
        for (String name : MCH_HeliInfoManager.map.keySet()) {
            info = (MCH_HeliInfo)MCH_HeliInfoManager.map.get(name);
            MCH_ItemRecipe.addRecipeAndRegisterList((MCH_AircraftInfo)info, (Item)info.item, (MCH_AircraftInfoManager)MCH_HeliInfoManager.getInstance());
        }
        for (String name : MCP_PlaneInfoManager.map.keySet()) {
            info = (MCP_PlaneInfo)MCP_PlaneInfoManager.map.get(name);
            MCH_ItemRecipe.addRecipeAndRegisterList((MCH_AircraftInfo)info, (Item)info.item, (MCH_AircraftInfoManager)MCP_PlaneInfoManager.getInstance());
        }
        for (String name : MCH_TankInfoManager.map.keySet()) {
            info = (MCH_TankInfo)MCH_TankInfoManager.map.get(name);
            MCH_ItemRecipe.addRecipeAndRegisterList((MCH_AircraftInfo)info, (Item)info.item, (MCH_AircraftInfoManager)MCH_TankInfoManager.getInstance());
        }
        for (String name : MCH_VehicleInfoManager.map.keySet()) {
            info = (MCH_VehicleInfo)MCH_VehicleInfoManager.map.get(name);
            MCH_ItemRecipe.addRecipeAndRegisterList((MCH_AircraftInfo)info, (Item)info.item, (MCH_AircraftInfoManager)MCH_VehicleInfoManager.getInstance());
        }
        for (String name : MCH_ThrowableInfoManager.getKeySet()) {
            info = MCH_ThrowableInfoManager.get((String)name);
            for (String s : info.recipeString) {
                if (s.length() < 3) continue;
                IRecipe recipe = MCH_ItemRecipe.addRecipe((Item)info.item, (String)s, (boolean)info.isShapedRecipe);
                info.recipe.add(recipe);
                MCH_ItemRecipe.addRecipeList((IRecipe)recipe);
            }
            info.recipeString = null;
        }
    }

    private static void addRecipeAndRegisterList(MCH_AircraftInfo info, Item item, MCH_AircraftInfoManager im) {
        int count = 0;
        for (String s : info.recipeString) {
            ++count;
            if (s.length() < 3) continue;
            IRecipe recipe = MCH_ItemRecipe.addRecipe((Item)item, (String)s, (boolean)info.isShapedRecipe);
            info.recipe.add(recipe);
            im.addRecipe(recipe, count, info.name, s);
        }
        info.recipeString = null;
    }

    public static IRecipe addRecipe(Item item, String data) {
        return MCH_ItemRecipe.addShapedRecipe((Item)item, (String)data);
    }

    public static IRecipe addRecipe(Item item, String data, boolean isShaped) {
        if (isShaped) {
            return MCH_ItemRecipe.addShapedRecipe((Item)item, (String)data);
        }
        return MCH_ItemRecipe.addShapelessRecipe((Item)item, (String)data);
    }

    public static IRecipe addShapedRecipe(Item item, String data) {
        ArrayList<Object> rcp = new ArrayList<Object>();
        String[] s = data.split("\\s*,\\s*");
        if (s.length < 3) {
            return null;
        }
        int start = 0;
        int createNum = 1;
        if (MCH_ItemRecipe.isNumber((String)s[0])) {
            start = 1;
            createNum = Integer.valueOf(s[0]);
            if (createNum <= 0) {
                createNum = 1;
            }
        }
        int idx = start;
        for (int i = start; i < 3 + start; ++i) {
            if (s[idx].length() <= 0 || s[idx].charAt(0) != '\"' || s[idx].charAt(s[idx].length() - 1) != '\"') continue;
            rcp.add(s[idx].subSequence(1, s[idx].length() - 1));
            ++idx;
        }
        if (idx == 0) {
            return null;
        }
        boolean isChar = true;
        while (idx < s.length) {
            if (s[idx].length() <= 0) {
                return null;
            }
            if (isChar) {
                if (s[idx].length() != 1) {
                    return null;
                }
                char c = s[idx].toUpperCase().charAt(0);
                if (c < 'A' || c > 'Z') {
                    return null;
                }
                rcp.add(Character.valueOf(c));
            } else {
                String nm = s[idx].trim().toLowerCase();
                int dmg = 0;
                if (idx + 1 < s.length && MCH_ItemRecipe.isNumber((String)s[idx + 1])) {
                    dmg = Integer.parseInt(s[++idx]);
                }
                if (MCH_ItemRecipe.isNumber((String)nm)) {
                    return null;
                }
                rcp.add(new ItemStack(W_Item.getItemByName((String)nm), 1, dmg));
            }
            isChar = !isChar;
            ++idx;
        }
        Object[] recipe = new Object[rcp.size()];
        for (int i = 0; i < recipe.length; ++i) {
            recipe[i] = rcp.get(i);
        }
        ShapedRecipes r = (ShapedRecipes)GameRegistry.addShapedRecipe((ItemStack)new ItemStack(item, createNum), (Object[])recipe);
        for (int i = 0; i < r.field_77574_d.length; ++i) {
            if (r.field_77574_d[i] == null || r.field_77574_d[i].func_77973_b() != null) continue;
            throw new RuntimeException("Error: Invalid ShapedRecipes! " + item + " : " + data);
        }
        return r;
    }

    public static IRecipe addShapelessRecipe(Item item, String data) {
        ArrayList<ItemStack> rcp = new ArrayList<ItemStack>();
        String[] s = data.split("\\s*,\\s*");
        if (s.length < 1) {
            return null;
        }
        int start = 0;
        int createNum = 1;
        if (MCH_ItemRecipe.isNumber((String)s[0]) && createNum <= 0) {
            createNum = 1;
        }
        for (int idx = start; idx < s.length; ++idx) {
            if (s[idx].length() <= 0) {
                return null;
            }
            String nm = s[idx].trim().toLowerCase();
            int dmg = 0;
            if (idx + 1 < s.length && MCH_ItemRecipe.isNumber((String)s[idx + 1])) {
                dmg = Integer.parseInt(s[++idx]);
            }
            if (MCH_ItemRecipe.isNumber((String)nm)) {
                int n = Integer.parseInt(nm);
                if (n <= 255) {
                    rcp.add(new ItemStack(W_Block.getBlockById((int)n), 1, dmg));
                    continue;
                }
                if (n <= 511) {
                    rcp.add(new ItemStack(W_Item.getItemById((int)n), 1, dmg));
                    continue;
                }
                if (n <= 2255) {
                    rcp.add(new ItemStack(W_Block.getBlockById((int)n), 1, dmg));
                    continue;
                }
                if (n <= 2267) {
                    rcp.add(new ItemStack(W_Item.getItemById((int)n), 1, dmg));
                    continue;
                }
                if (n <= 4095) {
                    rcp.add(new ItemStack(W_Block.getBlockById((int)n), 1, dmg));
                    continue;
                }
                if (n > 31999) continue;
                rcp.add(new ItemStack(W_Item.getItemById((int)(n + 256)), 1, dmg));
                continue;
            }
            rcp.add(new ItemStack(W_Item.getItemByName((String)nm), 1, dmg));
        }
        Object[] recipe = new Object[rcp.size()];
        for (int i = 0; i < recipe.length; ++i) {
            recipe[i] = rcp.get(i);
        }
        ShapelessRecipes r = MCH_ItemRecipe.getShapelessRecipe((ItemStack)new ItemStack(item, createNum), (Object[])recipe);
        for (int i = 0; i < r.field_77579_b.size(); ++i) {
            ItemStack is = (ItemStack)r.field_77579_b.get(i);
            if (is.func_77973_b() != null) continue;
            throw new RuntimeException("Error: Invalid ShapelessRecipes! " + item + " : " + data);
        }
        GameRegistry.addRecipe((IRecipe)r);
        return r;
    }

    public static ShapelessRecipes getShapelessRecipe(ItemStack par1ItemStack, Object ... par2ArrayOfObj) {
        ArrayList<ItemStack> arraylist = new ArrayList<ItemStack>();
        Object[] aobject = par2ArrayOfObj;
        int i = par2ArrayOfObj.length;
        for (int j = 0; j < i; ++j) {
            Object object1 = aobject[j];
            if (object1 instanceof ItemStack) {
                arraylist.add(((ItemStack)object1).func_77946_l());
                continue;
            }
            if (object1 instanceof Item) {
                arraylist.add(new ItemStack((Item)object1));
                continue;
            }
            if (!(object1 instanceof Block)) {
                throw new RuntimeException("Invalid shapeless recipy!");
            }
            arraylist.add(new ItemStack((Block)object1));
        }
        return new ShapelessRecipes(par1ItemStack, arraylist);
    }

    public static boolean isNumber(String s) {
        byte[] buf;
        if (s == null || s.isEmpty()) {
            return false;
        }
        for (byte b : buf = s.getBytes()) {
            if (b >= 48 && b <= 57) continue;
            return false;
        }
        return true;
    }
}

