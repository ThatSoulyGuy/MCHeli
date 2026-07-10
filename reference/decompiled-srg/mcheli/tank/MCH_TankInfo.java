/*
 * Decompiled with CFR 0.152.
 */
package mcheli.tank;

import java.util.ArrayList;
import java.util.List;
import mcheli.MCH_Config;
import mcheli.MCH_MOD;
import mcheli.aircraft.MCH_AircraftInfo;
import mcheli.tank.MCH_ItemTank;
import net.minecraft.item.Item;
import net.minecraft.util.Vec3;

public class MCH_TankInfo
extends MCH_AircraftInfo {
    public MCH_ItemTank item = null;
    public int weightType = 0;
    public float weightedCenterZ = 0.0f;

    public Item getItem() {
        return this.item;
    }

    public MCH_TankInfo(String name) {
        super(name);
    }

    public List<MCH_AircraftInfo.Wheel> getDefaultWheelList() {
        ArrayList<MCH_AircraftInfo.Wheel> list = new ArrayList<MCH_AircraftInfo.Wheel>();
        list.add(new MCH_AircraftInfo.Wheel((MCH_AircraftInfo)this, Vec3.func_72443_a((double)1.5, (double)-0.24, (double)2.0)));
        list.add(new MCH_AircraftInfo.Wheel((MCH_AircraftInfo)this, Vec3.func_72443_a((double)1.5, (double)-0.24, (double)-2.0)));
        return list;
    }

    public float getDefaultSoundRange() {
        return 50.0f;
    }

    public float getDefaultRotorSpeed() {
        return 47.94f;
    }

    private float getDefaultStepHeight() {
        return 0.6f;
    }

    public float getMaxSpeed() {
        return 1.8f;
    }

    public int getDefaultMaxZoom() {
        return 8;
    }

    public String getDefaultHudName(int seatId) {
        if (seatId <= 0) {
            return "tank";
        }
        if (seatId == 1) {
            return "tank";
        }
        return "gunner";
    }

    public boolean isValidData() throws Exception {
        this.speed = (float)((double)this.speed * MCH_Config.AllTankSpeed.prmDouble);
        return super.isValidData();
    }

    public void loadItemData(String item, String data) {
        super.loadItemData(item, data);
        if (item.equalsIgnoreCase("WeightType")) {
            this.weightType = (data = data.toLowerCase()).equals("tank") ? 2 : (data.equals("car") ? 1 : 0);
        } else if (item.equalsIgnoreCase("WeightedCenterZ")) {
            this.weightedCenterZ = this.toFloat(data, -1000.0f, 1000.0f);
        }
    }

    public String getDirectoryName() {
        return "tanks";
    }

    public String getKindName() {
        return "tank";
    }

    public void preReload() {
        super.preReload();
    }

    public void postReload() {
        MCH_MOD.proxy.registerModelsTank(this.name, true);
    }
}

