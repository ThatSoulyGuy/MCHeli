/*
 * Decompiled with CFR 0.152.
 */
package mcheli.command;

import com.google.common.collect.Lists;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.ArrayList;
import java.util.List;
import mcheli.gui.MCH_Gui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.GL11;

/*
 * Exception performing whole class analysis ignored.
 */
@SideOnly(value=Side.CLIENT)
public class MCH_GuiTitle
extends MCH_Gui {
    private final List chatLines = new ArrayList();
    private int prevPlayerTick = 0;
    private int restShowTick = 0;
    private int showTick = 0;
    private float colorAlpha = 0.0f;
    private int position = 0;
    private static Minecraft s_minecraft;

    public MCH_GuiTitle(Minecraft minecraft) {
        super(minecraft);
        s_minecraft = minecraft;
    }

    public void func_73866_w_() {
        super.func_73866_w_();
    }

    public boolean func_73868_f() {
        return false;
    }

    public boolean isDrawGui(EntityPlayer player) {
        if (this.restShowTick > 0 && this.chatLines.size() > 0 && player != null && player.field_70170_p != null) {
            if (this.prevPlayerTick != player.field_70173_aa) {
                ++this.showTick;
                --this.restShowTick;
            }
            this.prevPlayerTick = player.field_70173_aa;
        }
        return this.restShowTick > 0;
    }

    public void drawGui(EntityPlayer player, boolean isThirdPersonView) {
        GL11.glLineWidth((float)(scaleFactor * 2));
        GL11.glDisable((int)3042);
        if (scaleFactor <= 0) {
            scaleFactor = 1;
        }
        this.colorAlpha = 1.0f;
        if (this.restShowTick > 20 && this.showTick < 5) {
            this.colorAlpha = 0.2f * (float)this.showTick;
        }
        if (this.showTick > 0 && this.restShowTick < 5) {
            this.colorAlpha = 0.2f * (float)this.restShowTick;
        }
        this.drawChat();
    }

    private String func_146235_b(String s) {
        return Minecraft.func_71410_x().field_71474_y.field_74344_o ? s : EnumChatFormatting.func_110646_a((String)s);
    }

    private int func_146233_a() {
        int short1 = 320;
        int b0 = 40;
        return MathHelper.func_76141_d((float)(this.field_146297_k.field_71474_y.field_96692_F * (float)(short1 - b0) + (float)b0));
    }

    public void setupTitle(IChatComponent chatComponent, int showTime, int pos) {
        int displayTime = 20;
        int line = 0;
        this.chatLines.clear();
        this.position = pos;
        this.showTick = 0;
        this.restShowTick = showTime;
        int k = MathHelper.func_76141_d((float)((float)this.func_146233_a() / this.field_146297_k.field_71474_y.field_96691_E));
        int l = 0;
        ChatComponentText chatcomponenttext = new ChatComponentText("");
        ArrayList arraylist = Lists.newArrayList();
        ArrayList arraylist1 = Lists.newArrayList((Iterable)chatComponent);
        for (int i1 = 0; i1 < arraylist1.size(); ++i1) {
            IChatComponent ichatcomponent1 = (IChatComponent)arraylist1.get(i1);
            String[] splitLine = (ichatcomponent1.func_150261_e() + "").split("\n");
            int lineCnt = 0;
            for (String sLine : splitLine) {
                String s = this.func_146235_b(ichatcomponent1.func_150256_b().func_150218_j() + sLine);
                int j1 = this.field_146297_k.field_71466_p.func_78256_a(s);
                ChatComponentText chatcomponenttext1 = new ChatComponentText(s);
                chatcomponenttext1.func_150255_a(ichatcomponent1.func_150256_b().func_150232_l());
                boolean flag1 = false;
                if (l + j1 > k) {
                    String s2;
                    String s1 = this.field_146297_k.field_71466_p.func_78262_a(s, k - l, false);
                    String string = s2 = s1.length() < s.length() ? s.substring(s1.length()) : null;
                    if (s2 != null && s2.length() > 0) {
                        int k1 = s1.lastIndexOf(" ");
                        if (k1 >= 0 && this.field_146297_k.field_71466_p.func_78256_a(s.substring(0, k1)) > 0) {
                            s1 = s.substring(0, k1);
                            s2 = s.substring(k1);
                        }
                        ChatComponentText chatcomponenttext2 = new ChatComponentText(s2);
                        chatcomponenttext2.func_150255_a(ichatcomponent1.func_150256_b().func_150232_l());
                        arraylist1.add(i1 + 1, chatcomponenttext2);
                    }
                    j1 = this.field_146297_k.field_71466_p.func_78256_a(s1);
                    chatcomponenttext1 = new ChatComponentText(s1);
                    chatcomponenttext1.func_150255_a(ichatcomponent1.func_150256_b().func_150232_l());
                    flag1 = true;
                }
                if (l + j1 <= k) {
                    l += j1;
                    chatcomponenttext.func_150257_a((IChatComponent)chatcomponenttext1);
                } else {
                    flag1 = true;
                }
                if (flag1) {
                    arraylist.add(chatcomponenttext);
                    l = 0;
                    chatcomponenttext = new ChatComponentText("");
                }
                if (++lineCnt >= splitLine.length) continue;
                arraylist.add(chatcomponenttext);
                l = 0;
                chatcomponenttext = new ChatComponentText("");
            }
        }
        arraylist.add(chatcomponenttext);
        for (IChatComponent ichatcomponent2 : arraylist) {
            this.chatLines.add(new ChatLine(displayTime, ichatcomponent2, line));
        }
        while (this.chatLines.size() > 100) {
            this.chatLines.remove(this.chatLines.size() - 1);
        }
    }

    private int func_146243_b() {
        int short1 = 180;
        int b0 = 20;
        return MathHelper.func_76141_d((float)(this.field_146297_k.field_71474_y.field_96694_H * (float)(short1 - b0) + (float)b0));
    }

    private void drawChat() {
        float charAlpha = this.field_146297_k.field_71474_y.field_74357_r * 0.9f + 0.1f;
        float scale = this.field_146297_k.field_71474_y.field_96691_E * 2.0f;
        GL11.glPushMatrix();
        float posY = 0.0f;
        switch (this.position) {
            default: {
                posY = (float)(this.field_146297_k.field_71440_d / 2 / scaleFactor) - (float)this.chatLines.size() / 2.0f * 9.0f * scale;
                break;
            }
            case 1: {
                posY = 0.0f;
                break;
            }
            case 2: {
                posY = (float)(this.field_146297_k.field_71440_d / scaleFactor) - (float)this.chatLines.size() * 9.0f * scale;
                break;
            }
            case 3: {
                posY = (float)(this.field_146297_k.field_71440_d / 3 / scaleFactor) - (float)this.chatLines.size() / 2.0f * 9.0f * scale;
                break;
            }
            case 4: {
                posY = (float)(this.field_146297_k.field_71440_d * 2 / 3 / scaleFactor) - (float)this.chatLines.size() / 2.0f * 9.0f * scale;
            }
        }
        GL11.glTranslatef((float)0.0f, (float)posY, (float)0.0f);
        GL11.glScalef((float)scale, (float)scale, (float)1.0f);
        for (int i = 0; i < this.chatLines.size(); ++i) {
            ChatLine chatline = (ChatLine)this.chatLines.get(i);
            if (chatline == null) continue;
            int alpha = (int)(255.0f * charAlpha * this.colorAlpha);
            int y = i * 9;
            MCH_GuiTitle.func_73734_a((int)0, (int)(y + 9), (int)this.field_146297_k.field_71443_c, (int)y, (int)(alpha / 2 << 24));
            GL11.glEnable((int)3042);
            String s = chatline.func_151461_a().func_150254_d();
            int sw = this.field_146297_k.field_71443_c / 2 / scaleFactor - this.field_146297_k.field_71466_p.func_78256_a(s);
            sw = (int)((float)sw / scale);
            this.field_146297_k.field_71466_p.func_78261_a(s, sw, y + 1, 0xFFFFFF + (alpha << 24));
            GL11.glDisable((int)3008);
        }
        GL11.glTranslatef((float)-3.0f, (float)0.0f, (float)0.0f);
        GL11.glPopMatrix();
    }
}

