/*
 * Decompiled with CFR 0.152.
 */
package mcheli.aircraft;

import mcheli.MCH_Lib;
import mcheli.aircraft.MCH_Parts;
import mcheli.wrapper.W_WorldFunc;
import net.minecraft.entity.DataWatcher;
import net.minecraft.entity.Entity;

public class MCH_Parts {
    public final Entity parent;
    public final DataWatcher dataWatcher;
    public final int shift;
    public final int dataIndex;
    public final String partName;
    public float prevRotation = 0.0f;
    public float rotation = 0.0f;
    public float rotationMax = 90.0f;
    public float rotationInv = 1.0f;
    public Sound soundStartSwichOn = new Sound(this);
    public Sound soundEndSwichOn = new Sound(this);
    public Sound soundSwitching = new Sound(this);
    public Sound soundStartSwichOff = new Sound(this);
    public Sound soundEndSwichOff = new Sound(this);
    private boolean status = false;

    public MCH_Parts(Entity parent, int shiftBit, int dataWatcherIndex, String name) {
        this.parent = parent;
        this.dataWatcher = parent.func_70096_w();
        this.shift = shiftBit;
        this.dataIndex = dataWatcherIndex;
        this.status = (this.getDataWatcherValue() & 1 << this.shift) != 0;
        this.partName = name;
    }

    public int getDataWatcherValue() {
        return this.dataWatcher.func_75679_c(this.dataIndex);
    }

    public void setStatusServer(boolean stat) {
        this.setStatusServer(stat, true);
    }

    public void setStatusServer(boolean stat, boolean playSound) {
        if (!this.parent.field_70170_p.field_72995_K && this.getStatus() != stat) {
            MCH_Lib.DbgLog((boolean)false, (String)"setStatusServer(ID=%d %s :%s -> %s)", (Object[])new Object[]{this.shift, this.partName, this.getStatus() ? "ON" : "OFF", stat ? "ON" : "OFF"});
            this.updateDataWatcher(stat);
            this.playSound(this.soundSwitching);
            if (!stat) {
                this.playSound(this.soundStartSwichOff);
            } else {
                this.playSound(this.soundStartSwichOn);
            }
            this.update();
        }
    }

    protected void updateDataWatcher(boolean stat) {
        int currentStatus = this.dataWatcher.func_75679_c(this.dataIndex);
        int mask = 1 << this.shift;
        if (!stat) {
            this.dataWatcher.func_75692_b(this.dataIndex, (Object)(currentStatus & ~mask));
        } else {
            this.dataWatcher.func_75692_b(this.dataIndex, (Object)(currentStatus | mask));
        }
        this.status = stat;
    }

    public boolean getStatus() {
        return this.status;
    }

    public boolean isOFF() {
        return !this.status && this.rotation <= 0.02f;
    }

    public boolean isON() {
        return this.status && this.rotation >= this.rotationMax - 0.02f;
    }

    public void updateStatusClient(int statFromDataWatcher) {
        if (this.parent.field_70170_p.field_72995_K) {
            this.status = (statFromDataWatcher & 1 << this.shift) != 0;
        }
    }

    public void update() {
        this.prevRotation = this.rotation;
        if (this.getStatus()) {
            if (this.rotation < this.rotationMax) {
                this.rotation += this.rotationInv;
                if (this.rotation >= this.rotationMax) {
                    this.playSound(this.soundEndSwichOn);
                }
            }
        } else if (this.rotation > 0.0f) {
            this.rotation -= this.rotationInv;
            if (this.rotation <= 0.0f) {
                this.playSound(this.soundEndSwichOff);
            }
        }
    }

    public void forceSwitch(boolean onoff) {
        this.updateDataWatcher(onoff);
        this.rotation = this.prevRotation = this.rotationMax;
    }

    public float getFactor() {
        if (this.rotationMax > 0.0f) {
            return this.rotation / this.rotationMax;
        }
        return 0.0f;
    }

    public void playSound(Sound snd) {
        if (!snd.name.isEmpty() && !this.parent.field_70170_p.field_72995_K) {
            W_WorldFunc.MOD_playSoundAtEntity((Entity)this.parent, (String)snd.name, (float)snd.volume, (float)snd.pitch);
        }
    }
}

