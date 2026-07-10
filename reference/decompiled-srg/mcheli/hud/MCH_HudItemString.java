/*
 * Decompiled with CFR 0.152.
 */
package mcheli.hud;

import java.util.Date;
import mcheli.MCH_Config;
import mcheli.MCH_KeyName;
import mcheli.MCH_Lib;
import mcheli.MCH_MOD;
import mcheli.hud.MCH_HudItem;
import mcheli.hud.MCH_HudItemString;
import mcheli.hud.MCH_HudItemStringArgs;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MathHelper;

/*
 * Exception performing whole class analysis ignored.
 */
public class MCH_HudItemString
extends MCH_HudItem {
    private final String posX;
    private final String posY;
    private final String format;
    private final MCH_HudItemStringArgs[] args;
    private final boolean isCenteredString;

    public MCH_HudItemString(int fileLine, String posx, String posy, String fmt, String[] arg, boolean centered) {
        super(fileLine);
        this.posX = posx.toLowerCase();
        this.posY = posy.toLowerCase();
        this.format = fmt;
        int len = arg.length < 3 ? 0 : arg.length - 3;
        this.args = new MCH_HudItemStringArgs[len];
        for (int i = 0; i < len; ++i) {
            this.args[i] = MCH_HudItemStringArgs.toArgs((String)arg[3 + i]);
        }
        this.isCenteredString = centered;
    }

    public void execute() {
        int x = (int)(centerX + MCH_HudItemString.calc((String)this.posX));
        int y = (int)(centerY + MCH_HudItemString.calc((String)this.posY));
        long dateCount = Minecraft.func_71410_x().field_71439_g.field_70170_p.func_82737_E();
        int worldTime = (int)((MCH_HudItemString.ac.field_70170_p.func_72820_D() + 6000L) % 24000L);
        Date date = new Date();
        Object[] prm = new Object[this.args.length];
        double hp_per = ac.getMaxHP() > 0 ? (double)ac.getHP() / (double)ac.getMaxHP() : 0.0;
        block40: for (int i = 0; i < prm.length; ++i) {
            switch (1.$SwitchMap$mcheli$hud$MCH_HudItemStringArgs[this.args[i].ordinal()]) {
                case 1: {
                    prm[i] = MCH_HudItemString.ac.getAcInfo().displayName;
                    continue block40;
                }
                case 2: {
                    prm[i] = Altitude;
                    continue block40;
                }
                case 3: {
                    prm[i] = date;
                    continue block40;
                }
                case 4: {
                    prm[i] = worldTime / 1000;
                    continue block40;
                }
                case 5: {
                    prm[i] = worldTime % 1000 * 36 / 10 / 60;
                    continue block40;
                }
                case 6: {
                    prm[i] = worldTime % 1000 * 36 / 10 % 60;
                    continue block40;
                }
                case 7: {
                    prm[i] = ac.getMaxHP();
                    continue block40;
                }
                case 8: {
                    prm[i] = ac.getHP();
                    continue block40;
                }
                case 9: {
                    prm[i] = hp_per * 100.0;
                    continue block40;
                }
                case 10: {
                    prm[i] = MCH_HudItemString.ac.field_70165_t;
                    continue block40;
                }
                case 11: {
                    prm[i] = MCH_HudItemString.ac.field_70163_u;
                    continue block40;
                }
                case 12: {
                    prm[i] = MCH_HudItemString.ac.field_70161_v;
                    continue block40;
                }
                case 13: {
                    prm[i] = MCH_HudItemString.ac.field_70159_w;
                    continue block40;
                }
                case 14: {
                    prm[i] = MCH_HudItemString.ac.field_70181_x;
                    continue block40;
                }
                case 15: {
                    prm[i] = MCH_HudItemString.ac.field_70179_y;
                    continue block40;
                }
                case 16: {
                    prm[i] = ac.func_70302_i_();
                    continue block40;
                }
                case 17: {
                    prm[i] = WeaponName;
                    if (CurrentWeapon != null) continue block40;
                    return;
                }
                case 18: {
                    prm[i] = WeaponAmmo;
                    if (CurrentWeapon == null) {
                        return;
                    }
                    if (CurrentWeapon.getAmmoNumMax() > 0) continue block40;
                    return;
                }
                case 19: {
                    prm[i] = WeaponAllAmmo;
                    if (CurrentWeapon == null) {
                        return;
                    }
                    if (CurrentWeapon.getAmmoNumMax() > 0) continue block40;
                    return;
                }
                case 20: {
                    prm[i] = Float.valueOf(ReloadPer);
                    if (CurrentWeapon != null) continue block40;
                    return;
                }
                case 21: {
                    prm[i] = Float.valueOf(ReloadSec);
                    if (CurrentWeapon != null) continue block40;
                    return;
                }
                case 22: {
                    prm[i] = Float.valueOf(MortarDist);
                    if (CurrentWeapon != null) continue block40;
                    return;
                }
                case 23: {
                    prm[i] = "1.7.10";
                    continue block40;
                }
                case 24: {
                    prm[i] = MCH_MOD.VER;
                    continue block40;
                }
                case 25: {
                    prm[i] = "MC Helicopter MOD";
                    continue block40;
                }
                case 26: {
                    prm[i] = MCH_Lib.getRotate360((double)(ac.getRotYaw() + 180.0f));
                    continue block40;
                }
                case 27: {
                    prm[i] = Float.valueOf(-ac.getRotPitch());
                    continue block40;
                }
                case 28: {
                    prm[i] = Float.valueOf(MathHelper.func_76142_g((float)ac.getRotRoll()));
                    continue block40;
                }
                case 29: {
                    prm[i] = MCH_Lib.getRotate360((double)(MCH_HudItemString.player.field_70177_z + 180.0f));
                    continue block40;
                }
                case 30: {
                    prm[i] = Float.valueOf(-MCH_HudItemString.player.field_70125_A);
                    continue block40;
                }
                case 31: {
                    prm[i] = TVM_PosX;
                    continue block40;
                }
                case 32: {
                    prm[i] = TVM_PosY;
                    continue block40;
                }
                case 33: {
                    prm[i] = TVM_PosZ;
                    continue block40;
                }
                case 34: {
                    prm[i] = TVM_Diff;
                    continue block40;
                }
                case 35: {
                    prm[i] = Float.valueOf(MCH_HudItemString.ac.camera.getCameraZoom());
                    continue block40;
                }
                case 36: {
                    prm[i] = UAV_Dist;
                    continue block40;
                }
                case 37: {
                    prm[i] = MCH_KeyName.getDescOrName((int)MCH_Config.KeyGUI.prmInt);
                    continue block40;
                }
                case 38: {
                    prm[i] = ac.getCurrentThrottle() * 100.0;
                    continue block40;
                }
            }
        }
        if (this.isCenteredString) {
            this.drawCenteredString(String.format(this.format, prm), x, y, colorSetting);
        } else {
            this.drawString(String.format(this.format, prm), x, y, colorSetting);
        }
    }
}

