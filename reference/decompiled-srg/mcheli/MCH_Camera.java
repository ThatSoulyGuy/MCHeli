/*
 * Decompiled with CFR 0.152.
 */
package mcheli;

import mcheli.wrapper.W_Entity;
import mcheli.wrapper.W_EntityRenderer;
import mcheli.wrapper.W_Lib;
import net.minecraft.entity.Entity;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.world.World;

public class MCH_Camera {
    private final World worldObj;
    private float zoom;
    private int[] mode;
    private boolean[] canUseShader;
    private int[] lastMode;
    public double posX;
    public double posY;
    public double posZ;
    public float rotationYaw;
    public float rotationPitch;
    public float prevRotationYaw;
    public float prevRotationPitch;
    private int lastZoomDir;
    public float partRotationYaw;
    public float partRotationPitch;
    public float prevPartRotationYaw;
    public float prevPartRotationPitch;
    public static final int MODE_NORMAL = 0;
    public static final int MODE_NIGHTVISION = 1;
    public static final int MODE_THERMALVISION = 2;

    public MCH_Camera(World w, Entity p) {
        this.worldObj = w;
        this.mode = new int[]{0, 0};
        this.zoom = 1.0f;
        this.lastMode = new int[this.getUserMax()];
        this.lastZoomDir = 0;
        this.canUseShader = new boolean[this.getUserMax()];
    }

    public MCH_Camera(World w, Entity p, double x, double y, double z) {
        this(w, p);
        this.setPosition(x, y, z);
        this.setCameraZoom(1.0f);
    }

    public int getUserMax() {
        return this.mode.length;
    }

    public void initCamera(int uid, Entity viewer) {
        this.setCameraZoom(1.0f);
        this.setMode(uid, 0);
        this.updateViewer(uid, viewer);
    }

    public void setMode(int uid, int m) {
        if (!this.isValidUid(uid)) {
            return;
        }
        this.mode[uid] = m < 0 ? 0 : m % this.getModeNum(uid);
        switch (this.mode[uid]) {
            case 2: {
                if (!this.worldObj.field_72995_K) break;
                W_EntityRenderer.activateShader((String)"pencil");
                break;
            }
            case 0: 
            case 1: {
                if (!this.worldObj.field_72995_K) break;
                W_EntityRenderer.deactivateShader();
                break;
            }
        }
    }

    public void setShaderSupport(int uid, Boolean b) {
        if (this.isValidUid(uid)) {
            this.setMode(uid, 0);
            this.canUseShader[uid] = b;
        }
    }

    public boolean isValidUid(int uid) {
        return uid >= 0 && uid < this.getUserMax();
    }

    public int getModeNum(int uid) {
        if (!this.isValidUid(uid)) {
            return 2;
        }
        return this.canUseShader[uid] ? 3 : 2;
    }

    public int getMode(int uid) {
        return this.isValidUid(uid) ? this.mode[uid] : 0;
    }

    public String getModeName(int uid) {
        if (this.getMode(uid) == 1) {
            return "NIGHT VISION";
        }
        if (this.getMode(uid) == 2) {
            return "THERMAL VISION";
        }
        return "";
    }

    public void updateViewer(int uid, Entity viewer) {
        if (!this.isValidUid(uid) || viewer == null) {
            return;
        }
        if (W_Lib.isEntityLivingBase((Entity)viewer) && !viewer.field_70128_L) {
            PotionEffect pe;
            if (this.getMode(uid) == 0 && this.lastMode[uid] != 0 && (pe = W_Entity.getActivePotionEffect((Entity)viewer, (Potion)Potion.field_76439_r)) != null && pe.func_76459_b() > 0 && pe.func_76459_b() < 500) {
                if (viewer.field_70170_p.field_72995_K) {
                    W_Entity.removePotionEffectClient((Entity)viewer, (int)Potion.field_76439_r.field_76415_H);
                } else {
                    W_Entity.removePotionEffect((Entity)viewer, (int)Potion.field_76439_r.field_76415_H);
                }
            }
            if (!(this.getMode(uid) != 1 && this.getMode(uid) != 2 || (pe = W_Entity.getActivePotionEffect((Entity)viewer, (Potion)Potion.field_76439_r)) != null && (pe == null || pe.func_76459_b() >= 500) || viewer.field_70170_p.field_72995_K)) {
                W_Entity.addPotionEffect((Entity)viewer, (PotionEffect)new PotionEffect(Potion.field_76439_r.field_76415_H, 250, 0, true));
            }
        }
        this.lastMode[uid] = this.getMode(uid);
    }

    public void setPosition(double x, double y, double z) {
        this.posX = x;
        this.posY = y;
        this.posZ = z;
    }

    public void setCameraZoom(float z) {
        float prevZoom = this.zoom;
        float f = this.zoom = z < 1.0f ? 1.0f : z;
        this.lastZoomDir = this.zoom > prevZoom ? 1 : (this.zoom < prevZoom ? -1 : 0);
    }

    public float getCameraZoom() {
        return this.zoom;
    }
}

