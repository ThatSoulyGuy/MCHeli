/*
 * Decompiled with CFR 0.152.
 */
package mcheli.tool;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import mcheli.aircraft.MCH_EntityAircraft;
import mcheli.gui.MCH_Gui;
import mcheli.tool.MCH_ItemWrench;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.opengl.GL11;

/*
 * Exception performing whole class analysis ignored.
 */
@SideOnly(value=Side.CLIENT)
public class MCH_GuiWrench
extends MCH_Gui {
    public MCH_GuiWrench(Minecraft minecraft) {
        super(minecraft);
    }

    public void func_73866_w_() {
        super.func_73866_w_();
    }

    public boolean func_73868_f() {
        return false;
    }

    public boolean isDrawGui(EntityPlayer player) {
        return player != null && player.field_70170_p != null && player.func_71045_bC() != null && player.func_71045_bC().func_77973_b() instanceof MCH_ItemWrench;
    }

    public void drawGui(EntityPlayer player, boolean isThirdPersonView) {
        if (isThirdPersonView) {
            return;
        }
        GL11.glLineWidth((float)scaleFactor);
        if (!this.isDrawGui(player)) {
            return;
        }
        GL11.glDisable((int)3042);
        MCH_EntityAircraft ac = ((MCH_ItemWrench)player.func_71045_bC().func_77973_b()).getMouseOverAircraft(player);
        if (ac != null && ac.getMaxHP() > 0) {
            int color = (double)ac.getHP() / (double)ac.getMaxHP() > 0.3 ? -14101432 : -2161656;
            this.drawHP(color, -15433180, ac.getHP(), ac.getMaxHP());
        }
    }

    void drawHP(int color, int colorBG, int hp, int hpmax) {
        int posX = this.centerX;
        int posY = this.centerY + 20;
        int WID = 20;
        int INV = 10;
        MCH_GuiWrench.func_73734_a((int)(posX - 20), (int)(posY + 20 + 1), (int)(posX - 20 + 40), (int)(posY + 20 + 1 + 1 + 3 + 1), (int)colorBG);
        if (hp > hpmax) {
            hp = hpmax;
        }
        float hpp = (float)hp / (float)hpmax;
        MCH_GuiWrench.func_73734_a((int)(posX - 20 + 1), (int)(posY + 20 + 1 + 1), (int)(posX - 20 + 1 + (int)(38.0 * (double)hpp)), (int)(posY + 20 + 1 + 1 + 3), (int)color);
        int hppn = (int)(hpp * 100.0f);
        if (hp < hpmax && hppn >= 100) {
            hppn = 99;
        }
        this.drawCenteredString(String.format("%d %%", hppn), posX, posY + 30, color);
    }
}

