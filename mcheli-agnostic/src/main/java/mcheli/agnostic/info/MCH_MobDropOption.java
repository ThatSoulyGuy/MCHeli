package mcheli.agnostic.info;

import mcheli.agnostic.value.Vec3d;

/** Mob-drop spawn option on a vehicle definition (position + interval). */
public class MCH_MobDropOption {
    public Vec3d pos = Vec3d.ZERO;
    public int interval = 1;
}
