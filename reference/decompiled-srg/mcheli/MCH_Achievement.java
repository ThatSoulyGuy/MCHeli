/*
 * Decompiled with CFR 0.152.
 */
package mcheli;

import mcheli.MCH_MOD;
import mcheli.helicopter.MCH_HeliInfo;
import mcheli.helicopter.MCH_HeliInfoManager;
import mcheli.plane.MCP_PlaneInfoManager;
import mcheli.vehicle.MCH_VehicleInfoManager;
import mcheli.wrapper.W_Achievement;
import mcheli.wrapper.W_Item;
import mcheli.wrapper.W_LanguageRegistry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.stats.Achievement;
import net.minecraft.stats.StatBase;
import net.minecraftforge.common.AchievementPage;

/*
 * Exception performing whole class analysis ignored.
 */
public class MCH_Achievement {
    public static Achievement welcome = null;
    public static Achievement supplyFuel = null;
    public static Achievement supplyAmmo = null;
    public static Achievement aintWarHell = null;
    public static Achievement reliefSupplies = null;
    public static Achievement rideValkyries = null;

    public static void PreInit() {
        Item item = MCH_Achievement.getAnyAircraftIcon((String)"ah-64");
        boolean BC = true;
        boolean BR = true;
        String name = "McHeliWelcome";
        welcome = W_Achievement.registerAchievement((String)("mcheli" + name), (String)name, (int)1, (int)1, (Item)item, null);
        W_LanguageRegistry.addNameForObject((Object)welcome, (String)"en_US", (String)"Welcome to MC Helicopter MOD", (String)name, (String)"Put the helicopter");
        W_LanguageRegistry.addNameForObject((Object)welcome, (String)"ja_JP", (String)"MC Helicopter MOD \u3078\u3088\u3046\u3053\u305d", (String)name, (String)"\u30d8\u30ea\u30b3\u30d7\u30bf\u30fc\u3092\u8a2d\u7f6e");
        name = "McHeliSupplyFuel";
        supplyFuel = W_Achievement.registerAchievement((String)("mcheli" + name), (String)name, (int)-1, (int)1, (Item)MCH_MOD.itemFuel, null);
        W_LanguageRegistry.addNameForObject((Object)supplyFuel, (String)"en_US", (String)"Refueling", (String)name, (String)"Refuel aircraft");
        W_LanguageRegistry.addNameForObject((Object)supplyFuel, (String)"ja_JP", (String)"\u71c3\u6599\u88dc\u7d66", (String)name, (String)"\u71c3\u6599\u3092\u88dc\u7d66");
        item = MCH_Achievement.getAircraftIcon((String)"ammo_box");
        name = "McHeliSupplyAmmo";
        supplyAmmo = W_Achievement.registerAchievement((String)("mcheli" + name), (String)name, (int)3, (int)1, (Item)item, null);
        W_LanguageRegistry.addNameForObject((Object)supplyAmmo, (String)"en_US", (String)"Supply ammo", (String)name, (String)"Supply ammo to the aircraft");
        W_LanguageRegistry.addNameForObject((Object)supplyAmmo, (String)"ja_JP", (String)"\u5f3e\u85ac\u88dc\u7d66", (String)name, (String)"\u5f3e\u85ac\u3092\u88dc\u7d66");
        item = MCH_Achievement.getAircraftIcon((String)"uh-1c");
        name = "McHeliRideValkyries";
        rideValkyries = W_Achievement.registerAchievement((String)("mcheli" + name), (String)name, (int)-1, (int)3, (Item)item, null);
        W_LanguageRegistry.addNameForObject((Object)rideValkyries, (String)"en_US", (String)"Ride Of The Valkyries", (String)name, (String)"?");
        W_LanguageRegistry.addNameForObject((Object)rideValkyries, (String)"ja_JP", (String)"\u30ef\u30eb\u30ad\u30e5\u30fc\u30ec\u306e\u9a0e\u884c", (String)name, (String)"?");
        item = MCH_Achievement.getAircraftIcon((String)"mh-60l_dap");
        name = "McHeliAintWarHell";
        aintWarHell = W_Achievement.registerAchievement((String)("mcheli" + name), (String)name, (int)3, (int)3, (Item)item, null);
        W_LanguageRegistry.addNameForObject((Object)aintWarHell, (String)"en_US", (String)"Ain't war hell?", (String)name, (String)"?");
        W_LanguageRegistry.addNameForObject((Object)aintWarHell, (String)"ja_JP", (String)"\u30db\u30f3\u30c8\u6226\u4e89\u306f\u5730\u7344\u3060\u305c", (String)name, (String)"?");
        item = MCH_MOD.itemContainer;
        name = "McHeliReliefSupplies";
        reliefSupplies = W_Achievement.registerAchievement((String)("mcheli" + name), (String)name, (int)-1, (int)-1, (Item)item, null);
        W_LanguageRegistry.addNameForObject((Object)reliefSupplies, (String)"en_US", (String)"Relief supplies", (String)name, (String)"Drop a container");
        W_LanguageRegistry.addNameForObject((Object)reliefSupplies, (String)"ja_JP", (String)"\u652f\u63f4\u7269\u8cc7", (String)name, (String)"\u30b3\u30f3\u30c6\u30ca\u3092\u6295\u4e0b");
        Achievement[] achievements = new Achievement[]{welcome, supplyFuel, supplyAmmo, aintWarHell, rideValkyries, reliefSupplies};
        AchievementPage.registerAchievementPage((AchievementPage)new AchievementPage("MC Helicopter", achievements));
    }

    public static Item getAircraftIcon(String defaultIconAircraft) {
        Item item = W_Item.getItemByName((String)"stone");
        MCH_HeliInfo info = MCH_HeliInfoManager.get((String)defaultIconAircraft);
        if (info != null && info.getItem() != null) {
            return info.getItem();
        }
        info = MCP_PlaneInfoManager.get((String)defaultIconAircraft);
        if (info != null && info.getItem() != null) {
            return info.getItem();
        }
        info = MCH_VehicleInfoManager.get((String)defaultIconAircraft);
        if (info != null && info.getItem() != null) {
            return info.getItem();
        }
        return item;
    }

    public static Item getAnyAircraftIcon(String defaultIconAircraft) {
        Item item = W_Item.getItemByName((String)"stone");
        if (MCH_HeliInfoManager.map.size() > 0) {
            MCH_HeliInfo info = MCH_HeliInfoManager.get((String)defaultIconAircraft);
            if (info != null && info.item != null) {
                item = info.item;
            } else {
                for (MCH_HeliInfo i : MCH_HeliInfoManager.map.values()) {
                    if (i.item == null) continue;
                    item = i.item;
                    break;
                }
            }
        }
        return item;
    }

    public static void addStat(Entity player, Achievement a, int i) {
        if (a != null && player instanceof EntityPlayer && !player.field_70170_p.field_72995_K) {
            ((EntityPlayer)player).func_71064_a((StatBase)a, i);
        }
    }
}

