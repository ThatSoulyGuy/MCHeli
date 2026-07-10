/*
 * Decompiled with CFR 0.152.
 */
package mcheli;

import java.util.HashMap;
import net.minecraft.entity.Entity;

public class MCH_DamageFactor {
    private HashMap<Class, Float> map = new HashMap();

    public void clear() {
        this.map.clear();
    }

    public void add(Class c, float value) {
        this.map.put(c, Float.valueOf(value));
    }

    public float getDamageFactor(Class c) {
        if (this.map.containsKey(c)) {
            return ((Float)this.map.get(c)).floatValue();
        }
        return 1.0f;
    }

    public float getDamageFactor(Entity e) {
        return e != null ? this.getDamageFactor(e.getClass()) : 1.0f;
    }
}

