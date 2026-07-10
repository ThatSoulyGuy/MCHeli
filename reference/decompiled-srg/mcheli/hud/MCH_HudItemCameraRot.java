/*
 * Decompiled with CFR 0.152.
 */
package mcheli.hud;

import mcheli.MCH_Camera;
import mcheli.MCH_Lib;
import mcheli.hud.MCH_HudItem;
import net.minecraft.entity.Entity;

/*
 * Exception performing whole class analysis ignored.
 */
public class MCH_HudItemCameraRot
extends MCH_HudItem {
    private final String drawPosX;
    private final String drawPosY;

    public MCH_HudItemCameraRot(int fileLine, String posx, String posy) {
        super(fileLine);
        this.drawPosX = MCH_HudItemCameraRot.toFormula((String)posx);
        this.drawPosY = MCH_HudItemCameraRot.toFormula((String)posy);
    }

    public void execute() {
        this.drawCommonGunnerCamera((Entity)ac, MCH_HudItemCameraRot.ac.camera, colorSetting, centerX + MCH_HudItemCameraRot.calc((String)this.drawPosX), centerY + MCH_HudItemCameraRot.calc((String)this.drawPosY));
    }

    private void drawCommonGunnerCamera(Entity ac, MCH_Camera camera, int color, double posX, double posY) {
        if (camera == null) {
            return;
        }
        double centerX = posX;
        double centerY = posY;
        int WW = 20;
        int WH = 10;
        boolean LW = true;
        double[] line = new double[]{centerX - 21.0, centerY - 11.0, centerX + 21.0, centerY - 11.0, centerX + 21.0, centerY + 11.0, centerX - 21.0, centerY + 11.0};
        this.drawLine(line, color, 2);
        line = new double[]{centerX - 21.0, centerY, centerX, centerY, centerX + 21.0, centerY, centerX, centerY, centerX, centerY - 11.0, centerX, centerY, centerX, centerY + 11.0, centerX, centerY};
        this.drawLineStipple(line, color, 1, 52428);
        float pitch = camera.rotationPitch;
        if (pitch < -30.0f) {
            pitch = -30.0f;
        }
        if (pitch > 70.0f) {
            pitch = 70.0f;
        }
        pitch -= 20.0f;
        pitch = (float)((double)pitch * 0.16);
        float heliYaw = ac.field_70126_B + (ac.field_70177_z - ac.field_70126_B) / 2.0f;
        float cameraYaw = camera.prevRotationYaw + (camera.rotationYaw - camera.prevRotationYaw) / 2.0f;
        float yaw = (float)MCH_Lib.getRotateDiff((double)ac.field_70177_z, (double)camera.rotationYaw);
        if ((yaw *= 2.0f) < -50.0f) {
            yaw = -50.0f;
        }
        if (yaw > 50.0f) {
            yaw = 50.0f;
        }
        yaw = (float)((double)yaw * 0.34);
        line = new double[]{centerX + (double)yaw - 3.0, centerY + (double)pitch - 2.0, centerX + (double)yaw + 3.0, centerY + (double)pitch - 2.0, centerX + (double)yaw + 3.0, centerY + (double)pitch + 2.0, centerX + (double)yaw - 3.0, centerY + (double)pitch + 2.0};
        this.drawLine(line, color, 2);
    }
}

