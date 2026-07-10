/*
 * Decompiled with CFR 0.152.
 */
package mcheli;

import mcheli.MCH_Queue;

public class MCH_LowPassFilterFloat {
    private MCH_Queue<Float> filter;

    public MCH_LowPassFilterFloat(int filterLength) {
        this.filter = new MCH_Queue(filterLength, (Object)Float.valueOf(0.0f));
    }

    public void clear() {
        this.filter.clear((Object)Float.valueOf(0.0f));
    }

    public void put(float t) {
        this.filter.put((Object)Float.valueOf(t));
    }

    public float getAvg() {
        float f = 0.0f;
        for (int i = 0; i < this.filter.size(); ++i) {
            f += ((Float)this.filter.get(i)).floatValue();
        }
        return f / (float)this.filter.size();
    }
}

