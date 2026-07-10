/*
 * Decompiled with CFR 0.152.
 */
package mcheli.vehicle;

import java.util.ArrayList;
import java.util.List;
import mcheli.MCH_MOD;
import mcheli.aircraft.MCH_AircraftInfo;
import mcheli.vehicle.MCH_ItemVehicle;
import mcheli.vehicle.MCH_VehicleInfo;
import net.minecraft.item.Item;

public class MCH_VehicleInfo
extends MCH_AircraftInfo {
    public MCH_ItemVehicle item = null;
    public boolean isEnableMove = false;
    public boolean isEnableRot = false;
    public List<VPart> partList = new ArrayList();

    public float getMinRotationPitch() {
        return -90.0f;
    }

    public float getMaxRotationPitch() {
        return 90.0f;
    }

    public Item getItem() {
        return this.item;
    }

    public MCH_VehicleInfo(String name) {
        super(name);
    }

    public boolean isValidData() throws Exception {
        return super.isValidData();
    }

    public String getDefaultHudName(int seatId) {
        return "vehicle";
    }

    public void loadItemData(String item, String data) {
        String[] s;
        super.loadItemData(item, data);
        if (item.compareTo("canmove") == 0) {
            this.isEnableMove = this.toBool(data);
        } else if (item.compareTo("canrotation") == 0) {
            this.isEnableRot = this.toBool(data);
        } else if (item.compareTo("rotationpitchmin") == 0) {
            super.loadItemData("minrotationpitch", data);
        } else if (item.compareTo("rotationpitchmax") == 0) {
            super.loadItemData("maxrotationpitch", data);
        } else if (item.compareTo("addpart") == 0) {
            String[] s2 = data.split("\\s*,\\s*");
            if (s2.length >= 7) {
                float rb = s2.length >= 8 ? this.toFloat(s2[7]) : 0.0f;
                VPart n = new VPart(this, this.toFloat(s2[4]), this.toFloat(s2[5]), this.toFloat(s2[6]), "part" + this.partList.size(), this.toBool(s2[0]), this.toBool(s2[1]), this.toBool(s2[2]), this.toInt(s2[3]), rb);
                this.partList.add(n);
            }
        } else if (item.compareTo("addchildpart") == 0 && this.partList.size() > 0 && (s = data.split("\\s*,\\s*")).length >= 7) {
            float rb = s.length >= 8 ? this.toFloat(s[7]) : 0.0f;
            VPart p = (VPart)this.partList.get(this.partList.size() - 1);
            if (p.child == null) {
                p.child = new ArrayList();
            }
            VPart n = new VPart(this, this.toFloat(s[4]), this.toFloat(s[5]), this.toFloat(s[6]), p.modelName + "_" + p.child.size(), this.toBool(s[0]), this.toBool(s[1]), this.toBool(s[2]), this.toInt(s[3]), rb);
            p.child.add(n);
        }
    }

    public String getDirectoryName() {
        return "vehicles";
    }

    public String getKindName() {
        return "vehicle";
    }

    public void preReload() {
        super.preReload();
        this.partList.clear();
    }

    public void postReload() {
        MCH_MOD.proxy.registerModelsVehicle(this.name, true);
    }
}

