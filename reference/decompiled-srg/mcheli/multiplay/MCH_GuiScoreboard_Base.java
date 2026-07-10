/*
 * Decompiled with CFR 0.152.
 */
package mcheli.multiplay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import mcheli.multiplay.MCH_ContainerScoreboard;
import mcheli.multiplay.MCH_GuiScoreboard_Base;
import mcheli.multiplay.MCH_IGuiScoreboard;
import mcheli.wrapper.W_GuiContainer;
import mcheli.wrapper.W_ScaledResolution;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiPlayerInfo;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.opengl.GL11;

/*
 * Exception performing whole class analysis ignored.
 */
public abstract class MCH_GuiScoreboard_Base
extends W_GuiContainer {
    public List<Gui> listGui;
    public static final int BUTTON_ID_SHUFFLE = 256;
    public static final int BUTTON_ID_CREATE_TEAM = 512;
    public static final int BUTTON_ID_CREATE_TEAM_OK = 528;
    public static final int BUTTON_ID_CREATE_TEAM_CANCEL = 544;
    public static final int BUTTON_ID_CREATE_TEAM_FF = 560;
    public static final int BUTTON_ID_CREATE_TEAM_NEXT_C = 576;
    public static final int BUTTON_ID_CREATE_TEAM_PREV_C = 577;
    public static final int BUTTON_ID_JUMP_SPAWN_POINT = 768;
    public static final int BUTTON_ID_SWITCH_PVP = 1024;
    public static final int BUTTON_ID_DESTORY_ALL = 1280;
    private MCH_IGuiScoreboard screen_switcher;

    public MCH_GuiScoreboard_Base(MCH_IGuiScoreboard switcher, EntityPlayer player) {
        super((Container)new MCH_ContainerScoreboard(player));
        this.screen_switcher = switcher;
        this.field_146297_k = Minecraft.func_71410_x();
    }

    public void func_73866_w_() {
    }

    public void initGui(List buttonList, GuiScreen parents) {
        this.listGui = new ArrayList();
        this.field_146297_k = Minecraft.func_71410_x();
        this.field_146289_q = this.field_146297_k.field_71466_p;
        this.field_146294_l = parents.field_146294_l;
        this.field_146295_m = parents.field_146295_m;
        this.func_73866_w_();
        for (Gui b : this.listGui) {
            if (!(b instanceof GuiButton)) continue;
            buttonList.add(b);
        }
        this.field_146292_n.clear();
    }

    public static void setVisible(Object g, boolean v) {
        if (g instanceof GuiButton) {
            ((GuiButton)g).field_146125_m = v;
        }
        if (g instanceof GuiTextField) {
            ((GuiTextField)g).func_146189_e(v);
        }
    }

    public void updateScreenButtons(List list) {
    }

    protected void func_146976_a(float p_146976_1_, int p_146976_2_, int p_146976_3_) {
    }

    public int getTeamNum() {
        return this.field_146297_k.field_71441_e.func_96441_U().func_96525_g().size();
    }

    protected void acviveScreen() {
    }

    public void onSwitchScreen() {
        for (Gui b : this.listGui) {
            MCH_GuiScoreboard_Base.setVisible((Object)b, (boolean)true);
        }
        this.acviveScreen();
    }

    public void leaveScreen() {
        for (Gui b : this.listGui) {
            MCH_GuiScoreboard_Base.setVisible((Object)b, (boolean)false);
        }
    }

    public void keyTypedScreen(char c, int code) {
        this.func_73869_a(c, code);
    }

    public void mouseClickedScreen(int p_73864_1_, int p_73864_2_, int p_73864_3_) {
        block3: {
            try {
                this.func_73864_a(p_73864_1_, p_73864_2_, p_73864_3_);
            }
            catch (Exception e) {
                if (p_73864_3_ != 0) break block3;
                for (int l = 0; l < this.field_146292_n.size(); ++l) {
                    GuiButton guibutton = (GuiButton)this.field_146292_n.get(l);
                    if (!guibutton.func_146116_c(this.field_146297_k, p_73864_1_, p_73864_2_)) continue;
                    guibutton.func_146113_a(this.field_146297_k.func_147118_V());
                    this.func_146284_a(guibutton);
                }
            }
        }
    }

    public void drawGuiContainerForegroundLayerScreen(int param1, int param2) {
        this.func_146979_b(param1, param2);
    }

    protected void actionPerformedScreen(GuiButton btn) {
        this.func_146284_a(btn);
    }

    public void switchScreen(SCREEN_ID id) {
        this.screen_switcher.switchScreen(id);
    }

    public static int getScoreboradWidth(Minecraft mc) {
        W_ScaledResolution scaledresolution = new W_ScaledResolution(mc, mc.field_71443_c, mc.field_71440_d);
        int ScaledWidth = scaledresolution.func_78326_a() - 40;
        int width = ScaledWidth * 3 / 4 / (mc.field_71441_e.func_96441_U().func_96525_g().size() + 1);
        if (width > 150) {
            width = 150;
        }
        return width;
    }

    public static int getScoreBoardLeft(Minecraft mc, int teamNum, int teamIndex) {
        W_ScaledResolution scaledresolution = new W_ScaledResolution(mc, mc.field_71443_c, mc.field_71440_d);
        int ScaledWidth = scaledresolution.func_78326_a();
        return (int)((double)(ScaledWidth / 2) + (double)(MCH_GuiScoreboard_Base.getScoreboradWidth((Minecraft)mc) + 10) * (-((double)teamNum) / 2.0 + (double)teamIndex));
    }

    public static void drawList(Minecraft mc, FontRenderer fontRendererObj, boolean mng) {
        ArrayList<ScorePlayerTeam> teamList = new ArrayList<ScorePlayerTeam>();
        teamList.add(null);
        for (Object team : mc.field_71441_e.func_96441_U().func_96525_g()) {
            teamList.add((ScorePlayerTeam)team);
        }
        Collections.sort(teamList, new /* Unavailable Anonymous Inner Class!! */);
        for (int i = 0; i < teamList.size(); ++i) {
            if (mng) {
                MCH_GuiScoreboard_Base.drawPlayersList((Minecraft)mc, (FontRenderer)fontRendererObj, (ScorePlayerTeam)((ScorePlayerTeam)teamList.get(i)), (int)(1 + i), (int)(1 + teamList.size()));
                continue;
            }
            MCH_GuiScoreboard_Base.drawPlayersList((Minecraft)mc, (FontRenderer)fontRendererObj, (ScorePlayerTeam)((ScorePlayerTeam)teamList.get(i)), (int)i, (int)teamList.size());
        }
    }

    public static void drawPlayersList(Minecraft mc, FontRenderer fontRendererObj, ScorePlayerTeam team, int teamIndex, int teamNum) {
        W_ScaledResolution scaledresolution = new W_ScaledResolution(mc, mc.field_71443_c, mc.field_71440_d);
        int ScaledWidth = scaledresolution.func_78326_a();
        int ScaledHeight = scaledresolution.func_78328_b();
        ScoreObjective scoreobjective = mc.field_71441_e.func_96441_U().func_96539_a(0);
        NetHandlerPlayClient nethandlerplayclient = mc.field_71439_g.field_71174_a;
        List list = nethandlerplayclient.field_147303_b;
        int MaxPlayers = (list.size() / 5 + 1) * 5;
        int n = MaxPlayers = MaxPlayers < 10 ? 10 : MaxPlayers;
        if (MaxPlayers > nethandlerplayclient.field_147304_c) {
            MaxPlayers = nethandlerplayclient.field_147304_c;
        }
        int width = MCH_GuiScoreboard_Base.getScoreboradWidth((Minecraft)mc);
        int listLeft = MCH_GuiScoreboard_Base.getScoreBoardLeft((Minecraft)mc, (int)teamNum, (int)teamIndex);
        int listTop = ScaledHeight / 2 - (MaxPlayers * 9 + 10) / 2;
        MCH_GuiScoreboard_Base.func_73734_a((int)(listLeft - 1), (int)(listTop - 1 - 18), (int)(listLeft + width), (int)(listTop + 9 * MaxPlayers), (int)Integer.MIN_VALUE);
        String teamName = ScorePlayerTeam.func_96667_a((Team)team, (String)(team == null ? "No team" : team.func_96661_b()));
        int teamNameX = listLeft + width / 2 - fontRendererObj.func_78256_a(teamName) / 2;
        fontRendererObj.func_78261_a(teamName, teamNameX, listTop - 18, -1);
        String ff_onoff = "FriendlyFire : " + (team == null ? "ON" : (team.func_96665_g() ? "ON" : "OFF"));
        int ff_onoffX = listLeft + width / 2 - fontRendererObj.func_78256_a(ff_onoff) / 2;
        fontRendererObj.func_78261_a(ff_onoff, ff_onoffX, listTop - 9, -1);
        int drawY = 0;
        for (int i = 0; i < MaxPlayers; ++i) {
            int j4;
            int k4;
            int x = listLeft;
            int y = listTop + drawY * 9;
            int rectY = listTop + i * 9;
            MCH_GuiScoreboard_Base.func_73734_a((int)x, (int)rectY, (int)(x + width - 1), (int)(rectY + 8), (int)0x20FFFFFF);
            GL11.glColor4f((float)1.0f, (float)1.0f, (float)1.0f, (float)1.0f);
            GL11.glEnable((int)3008);
            if (i >= list.size()) continue;
            GuiPlayerInfo guiplayerinfo = (GuiPlayerInfo)list.get(i);
            String playerName = guiplayerinfo.field_78831_a;
            ScorePlayerTeam steam = mc.field_71441_e.func_96441_U().func_96509_i(playerName);
            if (!(steam == null && team == null || steam != null && team != null && steam.func_142054_a((Team)team))) continue;
            ++drawY;
            fontRendererObj.func_78261_a(playerName, x, y, -1);
            if (scoreobjective != null && (k4 = x + width - 12 - 5) - (j4 = x + fontRendererObj.func_78256_a(playerName) + 5) > 5) {
                Score score = scoreobjective.func_96682_a().func_96529_a(guiplayerinfo.field_78831_a, scoreobjective);
                String s1 = EnumChatFormatting.YELLOW + "" + score.func_96652_c();
                fontRendererObj.func_78261_a(s1, k4 - fontRendererObj.func_78256_a(s1), y, 0xFFFFFF);
            }
            MCH_GuiScoreboard_Base.drawResponseTime((int)(x + width - 12), (int)y, (int)guiplayerinfo.field_78829_b);
        }
    }

    public static void drawResponseTime(int x, int y, int responseTime) {
        GL11.glColor4f((float)1.0f, (float)1.0f, (float)1.0f, (float)1.0f);
        Minecraft.func_71410_x().func_110434_K().func_110577_a(field_110324_m);
        int b2 = responseTime < 0 ? 5 : (responseTime < 150 ? 0 : (responseTime < 300 ? 1 : (responseTime < 600 ? 2 : (responseTime < 1000 ? 3 : 4))));
        MCH_GuiScoreboard_Base.static_drawTexturedModalRect((int)x, (int)y, (int)0, (int)(176 + b2 * 8), (int)10, (int)8, (double)0.0);
    }

    public static void static_drawTexturedModalRect(int x, int y, int x2, int y2, int x3, int y3, double zLevel) {
        float f = 0.00390625f;
        Tessellator tessellator = Tessellator.field_78398_a;
        tessellator.func_78382_b();
        tessellator.func_78374_a((double)(x + 0), (double)(y + y3), zLevel, (double)((float)(x2 + 0) * 0.00390625f), (double)((float)(y2 + y3) * 0.00390625f));
        tessellator.func_78374_a((double)(x + x3), (double)(y + y3), zLevel, (double)((float)(x2 + x3) * 0.00390625f), (double)((float)(y2 + y3) * 0.00390625f));
        tessellator.func_78374_a((double)(x + x3), (double)(y + 0), zLevel, (double)((float)(x2 + x3) * 0.00390625f), (double)((float)(y2 + 0) * 0.00390625f));
        tessellator.func_78374_a((double)(x + 0), (double)(y + 0), zLevel, (double)((float)(x2 + 0) * 0.00390625f), (double)((float)(y2 + 0) * 0.00390625f));
        tessellator.func_78381_a();
    }
}

