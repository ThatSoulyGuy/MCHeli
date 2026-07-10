/*
 * Decompiled with CFR 0.152.
 */
package mcheli.helicopter;

import java.util.ArrayList;
import java.util.List;
import mcheli.MCH_Config;
import mcheli.MCH_MOD;
import mcheli.aircraft.MCH_AircraftInfo;
import mcheli.helicopter.MCH_HeliInfo;
import mcheli.helicopter.MCH_ItemHeli;
import net.minecraft.item.Item;

public class MCH_HeliInfo
extends MCH_AircraftInfo {
    public MCH_ItemHeli item = null;
    public boolean isEnableFoldBlade;
    public List<Rotor> rotorList;

    public MCH_HeliInfo(String name) {
        super(name);
        this.isEnableGunnerMode = false;
        this.isEnableFoldBlade = false;
        this.rotorList = new ArrayList();
        this.minRotationPitch = -20.0f;
        this.maxRotationPitch = 20.0f;
    }

    public boolean isValidData() throws Exception {
        this.speed = (float)((double)this.speed * MCH_Config.AllHeliSpeed.prmDouble);
        return super.isValidData();
    }

    public float getDefaultSoundRange() {
        return 80.0f;
    }

    public float getDefaultRotorSpeed() {
        return 79.99f;
    }

    public int getDefaultMaxZoom() {
        return 8;
    }

    public Item getItem() {
        return this.item;
    }

    public String getDefaultHudName(int seatId) {
        if (seatId <= 0) {
            return "heli";
        }
        if (seatId == 1) {
            return "heli_gnr";
        }
        return "gunner";
    }

    public void loadItemData(String item, String data) {
        String[] s;
        super.loadItemData(item, data);
        if (item.compareTo("enablefoldblade") == 0) {
            this.isEnableFoldBlade = this.toBool(data);
        } else if (!(item.compareTo("addrotor") != 0 && item.compareTo("addrotorold") != 0 || (s = data.split("\\s*,\\s*")).length != 8 && s.length != 9)) {
            boolean cfb = s.length == 9 && this.toBool(s[8]);
            Rotor e = new Rotor(this, this.toInt(s[0]), this.toInt(s[1]), this.toFloat(s[2]), this.toFloat(s[3]), this.toFloat(s[4]), this.toFloat(s[5]), this.toFloat(s[6]), this.toFloat(s[7]), "blade" + this.rotorList.size(), cfb, item.compareTo("addrotorold") == 0);
            this.rotorList.add(e);
        }
    }

    public String getDirectoryName() {
        return "helicopters";
    }

    public String getKindName() {
        return "helicopter";
    }

    public void preReload() {
        super.preReload();
        this.rotorList.clear();
    }

    public void postReload() {
        MCH_MOD.proxy.registerModelsHeli(this.name, true);
    }
}

