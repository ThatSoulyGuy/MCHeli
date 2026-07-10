/*
 * Decompiled with CFR 0.152.
 */
package mcheli.block;

import java.util.ArrayList;
import java.util.List;
import mcheli.MCH_IRecipeList;
import mcheli.MCH_ItemRecipe;
import mcheli.MCH_Lib;
import mcheli.aircraft.MCH_AircraftInfo;
import mcheli.aircraft.MCH_RenderAircraft;
import mcheli.block.MCH_CurrentRecipe;
import mcheli.block.MCH_DraftingTableCreatePacket;
import mcheli.block.MCH_DraftingTableGuiContainer;
import mcheli.gui.MCH_GuiSliderVertical;
import mcheli.helicopter.MCH_HeliInfoManager;
import mcheli.plane.MCP_PlaneInfoManager;
import mcheli.tank.MCH_TankInfoManager;
import mcheli.vehicle.MCH_VehicleInfoManager;
import mcheli.wrapper.W_GuiButton;
import mcheli.wrapper.W_GuiContainer;
import mcheli.wrapper.W_KeyBinding;
import mcheli.wrapper.W_McClient;
import mcheli.wrapper.W_ScaledResolution;
import mcheli.wrapper.modelloader.W_ModelCustom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/*
 * Exception performing whole class analysis ignored.
 */
public class MCH_DraftingTableGui
extends W_GuiContainer {
    private final EntityPlayer thePlayer;
    private int scaleFactor;
    private MCH_GuiSliderVertical listSlider;
    private GuiButton buttonCreate;
    private GuiButton buttonNext;
    private GuiButton buttonPrev;
    private GuiButton buttonNextPage;
    private GuiButton buttonPrevPage;
    private int drawFace;
    private int buttonClickWait;
    public static final int RECIPE_HELI = 0;
    public static final int RECIPE_PLANE = 1;
    public static final int RECIPE_VEHICLE = 2;
    public static final int RECIPE_TANK = 3;
    public static final int RECIPE_ITEM = 4;
    public MCH_IRecipeList currentList;
    public MCH_CurrentRecipe current;
    public static final int BUTTON_HELI = 10;
    public static final int BUTTON_PLANE = 11;
    public static final int BUTTON_VEHICLE = 12;
    public static final int BUTTON_TANK = 13;
    public static final int BUTTON_ITEM = 14;
    public static final int BUTTON_NEXT = 20;
    public static final int BUTTON_PREV = 21;
    public static final int BUTTON_CREATE = 30;
    public static final int BUTTON_SELECT = 40;
    public static final int BUTTON_NEXT_PAGE = 50;
    public static final int BUTTON_PREV_PAGE = 51;
    public List<List<GuiButton>> screenButtonList;
    public int screenId = 0;
    public static final int SCREEN_MAIN = 0;
    public static final int SCREEN_LIST = 1;
    public static float modelZoom = 1.0f;
    public static float modelRotX = 0.0f;
    public static float modelRotY = 0.0f;
    public static float modelPosX = 0.0f;
    public static float modelPosY = 0.0f;

    public MCH_DraftingTableGui(EntityPlayer player, int posX, int posY, int posZ) {
        super((Container)new MCH_DraftingTableGuiContainer(player, posX, posY, posZ));
        this.thePlayer = player;
        this.field_146999_f = 400;
        this.field_147000_g = 240;
        this.screenButtonList = new ArrayList();
        this.drawFace = 0;
        this.buttonClickWait = 0;
        MCH_Lib.DbgLog((World)player.field_70170_p, (String)"MCH_DraftingTableGui.MCH_DraftingTableGui", (Object[])new Object[0]);
    }

    public void func_73866_w_() {
        super.func_73866_w_();
        this.field_146292_n.clear();
        this.screenButtonList.clear();
        this.screenButtonList.add(new ArrayList());
        this.screenButtonList.add(new ArrayList());
        List list = null;
        list = (List)this.screenButtonList.get(0);
        GuiButton btnHeli = new GuiButton(10, this.field_147003_i + 20, this.field_147009_r + 20, 90, 20, "Helicopter List");
        GuiButton btnPlane = new GuiButton(11, this.field_147003_i + 20, this.field_147009_r + 40, 90, 20, "Plane List");
        GuiButton btnVehicle = new GuiButton(12, this.field_147003_i + 20, this.field_147009_r + 60, 90, 20, "Vehicle List");
        GuiButton btnTank = new GuiButton(13, this.field_147003_i + 20, this.field_147009_r + 80, 90, 20, "Tank List");
        GuiButton btnItem = new GuiButton(14, this.field_147003_i + 20, this.field_147009_r + 100, 90, 20, "Item List");
        btnHeli.field_146124_l = MCH_HeliInfoManager.getInstance().getRecipeListSize() > 0;
        btnPlane.field_146124_l = MCP_PlaneInfoManager.getInstance().getRecipeListSize() > 0;
        btnVehicle.field_146124_l = MCH_VehicleInfoManager.getInstance().getRecipeListSize() > 0;
        btnTank.field_146124_l = MCH_TankInfoManager.getInstance().getRecipeListSize() > 0;
        btnItem.field_146124_l = MCH_ItemRecipe.getInstance().getRecipeListSize() > 0;
        list.add(btnHeli);
        list.add(btnPlane);
        list.add(btnVehicle);
        list.add(btnTank);
        list.add(btnItem);
        this.buttonCreate = new GuiButton(30, this.field_147003_i + 120, this.field_147009_r + 89, 50, 20, "Create");
        this.buttonPrev = new GuiButton(21, this.field_147003_i + 120, this.field_147009_r + 111, 36, 20, "<<");
        this.buttonNext = new GuiButton(20, this.field_147003_i + 155, this.field_147009_r + 111, 35, 20, ">>");
        list.add(this.buttonCreate);
        list.add(this.buttonPrev);
        list.add(this.buttonNext);
        this.buttonPrevPage = new GuiButton(51, this.field_147003_i + 210, this.field_147009_r + 210, 60, 20, "Prev Page");
        this.buttonNextPage = new GuiButton(50, this.field_147003_i + 270, this.field_147009_r + 210, 60, 20, "Next Page");
        list.add(this.buttonPrevPage);
        list.add(this.buttonNextPage);
        list = (List)this.screenButtonList.get(1);
        int i = 0;
        for (int y = 0; y < 3; ++y) {
            int x = 0;
            while (x < 2) {
                int px = this.field_147003_i + 30 + x * 140;
                int py = this.field_147009_r + 40 + y * 70;
                list.add(new GuiButton(40 + i, px, py, 45, 20, "Select"));
                ++x;
                ++i;
            }
        }
        this.listSlider = new MCH_GuiSliderVertical(0, this.field_147003_i + 360, this.field_147009_r + 20, 20, 200, "", 0.0f, 0.0f, 0.0f, 1.0f);
        list.add(this.listSlider);
        for (int i2 = 0; i2 < this.screenButtonList.size(); ++i2) {
            list = (List)this.screenButtonList.get(i2);
            for (int j = 0; j < list.size(); ++j) {
                this.field_146292_n.add(list.get(j));
            }
        }
        this.switchScreen(0);
        MCH_DraftingTableGui.initModelTransform();
        modelRotX = 180.0f;
        modelRotY = 90.0f;
        if (MCH_ItemRecipe.getInstance().getRecipeListSize() > 0) {
            this.switchRecipeList((MCH_IRecipeList)MCH_ItemRecipe.getInstance());
        } else if (MCH_HeliInfoManager.getInstance().getRecipeListSize() > 0) {
            this.switchRecipeList((MCH_IRecipeList)MCH_HeliInfoManager.getInstance());
        } else if (MCP_PlaneInfoManager.getInstance().getRecipeListSize() > 0) {
            this.switchRecipeList((MCH_IRecipeList)MCP_PlaneInfoManager.getInstance());
        } else if (MCH_VehicleInfoManager.getInstance().getRecipeListSize() > 0) {
            this.switchRecipeList((MCH_IRecipeList)MCH_VehicleInfoManager.getInstance());
        } else if (MCH_TankInfoManager.getInstance().getRecipeListSize() > 0) {
            this.switchRecipeList((MCH_IRecipeList)MCH_TankInfoManager.getInstance());
        } else {
            this.switchRecipeList((MCH_IRecipeList)MCH_ItemRecipe.getInstance());
        }
    }

    public static void initModelTransform() {
        modelRotX = 0.0f;
        modelRotY = 0.0f;
        modelPosX = 0.0f;
        modelPosY = 0.0f;
        modelZoom = 1.0f;
    }

    public void updateListSliderSize(int listSize) {
        int s = listSize / 2;
        if (listSize % 2 != 0) {
            ++s;
        }
        this.listSlider.valueMax = s > 3 ? (float)(s - 3) : 0.0f;
        this.listSlider.setSliderValue(0.0f);
    }

    public void switchScreen(int id) {
        this.screenId = id;
        for (int i = 0; i < this.field_146292_n.size(); ++i) {
            W_GuiButton.setVisible((GuiButton)((GuiButton)this.field_146292_n.get(i)), (boolean)false);
        }
        if (id < this.screenButtonList.size()) {
            List list = (List)this.screenButtonList.get(id);
            for (GuiButton b : list) {
                W_GuiButton.setVisible((GuiButton)b, (boolean)true);
            }
        }
        if (this.getScreenId() == 0 && this.current != null && this.current.getDescMaxPage() > 1) {
            W_GuiButton.setVisible((GuiButton)this.buttonNextPage, (boolean)true);
            W_GuiButton.setVisible((GuiButton)this.buttonPrevPage, (boolean)true);
        } else {
            W_GuiButton.setVisible((GuiButton)this.buttonNextPage, (boolean)false);
            W_GuiButton.setVisible((GuiButton)this.buttonPrevPage, (boolean)false);
        }
    }

    public void setCurrentRecipe(MCH_CurrentRecipe currentRecipe) {
        modelPosX = 0.0f;
        modelPosY = 0.0f;
        if (this.current == null || currentRecipe == null || !this.current.recipe.func_77571_b().func_77969_a(currentRecipe.recipe.func_77571_b())) {
            this.drawFace = 0;
        }
        this.current = currentRecipe;
        if (this.getScreenId() == 0 && this.current != null && this.current.getDescMaxPage() > 1) {
            W_GuiButton.setVisible((GuiButton)this.buttonNextPage, (boolean)true);
            W_GuiButton.setVisible((GuiButton)this.buttonPrevPage, (boolean)true);
        } else {
            W_GuiButton.setVisible((GuiButton)this.buttonNextPage, (boolean)false);
            W_GuiButton.setVisible((GuiButton)this.buttonPrevPage, (boolean)false);
        }
    }

    public MCH_IRecipeList getCurrentList() {
        return this.currentList;
    }

    public void switchRecipeList(MCH_IRecipeList list) {
        if (this.getCurrentList() != list) {
            this.setCurrentRecipe(new MCH_CurrentRecipe(list, 0));
            this.currentList = list;
            this.updateListSliderSize(list.getRecipeListSize());
        } else {
            this.listSlider.setSliderValue((float)(this.current.index / 2));
        }
    }

    public void func_73876_c() {
        super.func_73876_c();
        MCH_DraftingTableGuiContainer container = (MCH_DraftingTableGuiContainer)this.field_147002_h;
        this.buttonCreate.field_146124_l = false;
        if (!container.func_75139_a(container.outputSlotIndex).func_75216_d() && MCH_Lib.canPlayerCreateItem((IRecipe)this.current.recipe, (InventoryPlayer)this.thePlayer.field_71071_by)) {
            this.buttonCreate.field_146124_l = true;
        }
        if (this.thePlayer.field_71075_bZ.field_75098_d) {
            this.buttonCreate.field_146124_l = true;
        }
        if (this.buttonClickWait > 0) {
            --this.buttonClickWait;
        }
    }

    public void func_146281_b() {
        super.func_146281_b();
        MCH_Lib.DbgLog((World)this.thePlayer.field_70170_p, (String)"MCH_DraftingTableGui.onGuiClosed", (Object[])new Object[0]);
    }

    protected void func_146284_a(GuiButton button) {
        super.func_146284_a(button);
        if (this.buttonClickWait > 0) {
            return;
        }
        if (!button.field_146124_l) {
            return;
        }
        this.buttonClickWait = 3;
        int index = 0;
        int page = this.current.getDescCurrentPage();
        switch (button.field_146127_k) {
            case 30: {
                MCH_DraftingTableCreatePacket.send((IRecipe)this.current.recipe);
                break;
            }
            case 21: {
                if (this.current.isCurrentPageTexture()) {
                    page = 0;
                }
                if ((index = this.current.index - 1) < 0) {
                    index = this.getCurrentList().getRecipeListSize() - 1;
                }
                this.setCurrentRecipe(new MCH_CurrentRecipe(this.getCurrentList(), index));
                this.current.setDescCurrentPage(page);
                break;
            }
            case 20: {
                if (this.current.isCurrentPageTexture()) {
                    page = 0;
                }
                index = (this.current.index + 1) % this.getCurrentList().getRecipeListSize();
                this.setCurrentRecipe(new MCH_CurrentRecipe(this.getCurrentList(), index));
                this.current.setDescCurrentPage(page);
                break;
            }
            case 10: {
                MCH_DraftingTableGui.initModelTransform();
                modelRotX = 180.0f;
                modelRotY = 90.0f;
                this.switchRecipeList((MCH_IRecipeList)MCH_HeliInfoManager.getInstance());
                this.switchScreen(1);
                break;
            }
            case 11: {
                MCH_DraftingTableGui.initModelTransform();
                modelRotX = 90.0f;
                modelRotY = 180.0f;
                this.switchRecipeList((MCH_IRecipeList)MCP_PlaneInfoManager.getInstance());
                this.switchScreen(1);
                break;
            }
            case 13: {
                MCH_DraftingTableGui.initModelTransform();
                modelRotX = 180.0f;
                modelRotY = 90.0f;
                this.switchRecipeList((MCH_IRecipeList)MCH_TankInfoManager.getInstance());
                this.switchScreen(1);
                break;
            }
            case 12: {
                MCH_DraftingTableGui.initModelTransform();
                modelRotX = 180.0f;
                modelRotY = 90.0f;
                this.switchRecipeList((MCH_IRecipeList)MCH_VehicleInfoManager.getInstance());
                this.switchScreen(1);
                break;
            }
            case 14: {
                this.switchRecipeList((MCH_IRecipeList)MCH_ItemRecipe.getInstance());
                this.switchScreen(1);
                break;
            }
            case 50: {
                if (this.current == null) break;
                this.current.switchNextPage();
                break;
            }
            case 51: {
                if (this.current == null) break;
                this.current.switchPrevPage();
                break;
            }
            case 40: 
            case 41: 
            case 42: 
            case 43: 
            case 44: 
            case 45: {
                index = (int)this.listSlider.getSliderValue() * 2 + (button.field_146127_k - 40);
                if (index >= this.getCurrentList().getRecipeListSize()) break;
                this.setCurrentRecipe(new MCH_CurrentRecipe(this.getCurrentList(), index));
                this.switchScreen(0);
            }
        }
    }

    protected void func_73869_a(char par1, int keycode) {
        if (keycode == 1 || keycode == W_KeyBinding.getKeyCode((KeyBinding)Minecraft.func_71410_x().field_71474_y.field_151445_Q)) {
            if (this.getScreenId() == 0) {
                this.field_146297_k.field_71439_g.func_71053_j();
            } else {
                this.switchScreen(0);
            }
        }
        if (this.getScreenId() == 0) {
            if (keycode == 205) {
                this.func_146284_a(this.buttonNext);
            }
            if (keycode == 203) {
                this.func_146284_a(this.buttonPrev);
            }
        } else if (this.getScreenId() == 1) {
            if (keycode == 200) {
                this.listSlider.scrollDown(1.0f);
            }
            if (keycode == 208) {
                this.listSlider.scrollUp(1.0f);
            }
        }
    }

    protected void func_146979_b(int mx, int my) {
        super.func_146979_b(mx, my);
        float z = this.field_73735_i;
        this.field_73735_i = 0.0f;
        GL11.glEnable((int)3042);
        if (this.getScreenId() == 0) {
            ArrayList<String> list = new ArrayList<String>();
            if (this.current != null) {
                if (this.current.isCurrentPageTexture()) {
                    GL11.glColor4d((double)1.0, (double)1.0, (double)1.0, (double)1.0);
                    this.field_146297_k.func_110434_K().func_110577_a(this.current.getCurrentPageTexture());
                    this.drawTexturedModalRect(210, 20, 170, 190, 0, 0, 340, 380);
                } else if (this.current.isCurrentPageAcInfo()) {
                    int COLOR = -9491968;
                    for (int i = 0; i < this.current.infoItem.size(); ++i) {
                        this.field_146289_q.func_78276_b((String)this.current.infoItem.get(i), 210, 40 + 10 * i, -9491968);
                        String data = (String)this.current.infoData.get(i);
                        if (data.isEmpty()) continue;
                        this.field_146289_q.func_78276_b(data, 280, 40 + 10 * i, -9491968);
                    }
                } else {
                    W_McClient.MOD_bindTexture((String)"textures/gui/drafting_table.png");
                    this.drawTexturedModalRect(340, 215, 45, 15, 400, 60, 90, 30);
                    if (mx >= 350 && mx <= 400 && my >= 214 && my <= 230) {
                        boolean lb = Mouse.isButtonDown((int)0);
                        boolean rb = Mouse.isButtonDown((int)1);
                        boolean mb = Mouse.isButtonDown((int)2);
                        list.add((lb ? EnumChatFormatting.AQUA : "") + "Mouse left button drag : Rotation model");
                        list.add((rb ? EnumChatFormatting.AQUA : "") + "Mouse right button drag : Zoom model");
                        list.add((mb ? EnumChatFormatting.AQUA : "") + "Mouse middle button drag : Move model");
                    }
                }
            }
            this.drawString(this.current.displayName, 120, 20, -1);
            this.drawItemRecipe(this.current.recipe, 121, 34);
            if (list.size() > 0) {
                this.drawHoveringText(list, mx - 30, my - 0, this.field_146289_q);
            }
        }
        if (this.getScreenId() == 1) {
            int ry;
            int rx;
            int r;
            int index = 2 * (int)this.listSlider.getSliderValue();
            int i = 0;
            for (r = 0; r < 3; ++r) {
                for (int c = 0; c < 2; ++c) {
                    if (index + i < this.getCurrentList().getRecipeListSize()) {
                        rx = 110 + 140 * c;
                        ry = 20 + 70 * r;
                        String s = this.getCurrentList().getRecipe(index + i).func_77571_b().func_82833_r();
                        this.drawCenteredString(s, rx, ry, -1);
                    }
                    ++i;
                }
            }
            W_McClient.MOD_bindTexture((String)"textures/gui/drafting_table.png");
            i = 0;
            for (r = 0; r < 3; ++r) {
                for (int c = 0; c < 2; ++c) {
                    if (index + i < this.getCurrentList().getRecipeListSize()) {
                        rx = 80 + 140 * c - 1;
                        ry = 30 + 70 * r - 1;
                        this.func_73729_b(rx, ry, 400, 0, 75, 54);
                    }
                    ++i;
                }
            }
            i = 0;
            for (r = 0; r < 3; ++r) {
                for (int c = 0; c < 2; ++c) {
                    if (index + i < this.getCurrentList().getRecipeListSize()) {
                        rx = 80 + 140 * c;
                        ry = 30 + 70 * r;
                        this.drawItemRecipe(this.getCurrentList().getRecipe(index + i), rx, ry);
                    }
                    ++i;
                }
            }
        }
    }

    protected void func_146984_a(Slot p_146984_1_, int p_146984_2_, int p_146984_3_, int p_146984_4_) {
        if (this.getScreenId() != 1) {
            super.func_146984_a(p_146984_1_, p_146984_2_, p_146984_3_, p_146984_4_);
        }
    }

    private int getScreenId() {
        return this.screenId;
    }

    public void drawItemRecipe(IRecipe recipe, int x, int y) {
        if (recipe == null) {
            return;
        }
        if (recipe.func_77571_b() == null) {
            return;
        }
        if (recipe.func_77571_b().func_77973_b() == null) {
            return;
        }
        if (recipe instanceof ShapedRecipes) {
            ShapedRecipes rcp = (ShapedRecipes)recipe;
            int RH = rcp.field_77577_c;
            for (int h = 0; h < RH; ++h) {
                for (int w = 0; w < rcp.field_77576_b; ++w) {
                    int IDX = h * RH + w;
                    if (IDX >= rcp.field_77574_d.length) continue;
                    this.drawItemStack(rcp.field_77574_d[IDX], x + w * 18, y + h * 18);
                }
            }
        } else if (recipe instanceof ShapelessRecipes) {
            ShapelessRecipes rcp = (ShapelessRecipes)recipe;
            for (int i = 0; i < rcp.field_77579_b.size(); ++i) {
                this.drawItemStack((ItemStack)rcp.field_77579_b.get(i), x + i % 3 * 18, y + i / 3 * 18);
            }
        }
        this.drawItemStack(recipe.func_77571_b(), x + 54 + 3, y + 18);
    }

    public void func_146274_d() {
        int wheel;
        super.func_146274_d();
        int dx = Mouse.getEventDX();
        int dy = Mouse.getEventDY();
        if (this.getScreenId() == 0 && Mouse.getX() > this.field_146297_k.field_71443_c / 2) {
            if (Mouse.isButtonDown((int)0) && (dx != 0 || dy != 0)) {
                modelRotX = (float)((double)modelRotX - (double)dy / 2.0);
                modelRotY = (float)((double)modelRotY - (double)dx / 2.0);
                if (modelRotX > 360.0f) {
                    modelRotX -= 360.0f;
                }
                if (modelRotX < -360.0f) {
                    modelRotX += 360.0f;
                }
                if (modelRotY > 360.0f) {
                    modelRotY -= 360.0f;
                }
                if (modelRotY < -360.0f) {
                    modelRotY += 360.0f;
                }
            }
            if (Mouse.isButtonDown((int)2) && (dx != 0 || dy != 0)) {
                modelPosX = (float)((double)modelPosX + (double)dx / 2.0);
                modelPosY = (float)((double)modelPosY - (double)dy / 2.0);
                if (modelRotX > 1000.0f) {
                    modelRotX = 1000.0f;
                }
                if (modelRotX < -1000.0f) {
                    modelRotX = -1000.0f;
                }
                if (modelRotY > 1000.0f) {
                    modelRotY = 1000.0f;
                }
                if (modelRotY < -1000.0f) {
                    modelRotY = -1000.0f;
                }
            }
            if (Mouse.isButtonDown((int)1) && dy != 0) {
                if ((double)(modelZoom = (float)((double)modelZoom + (double)dy / 100.0)) < 0.1) {
                    modelZoom = 0.1f;
                }
                if (modelZoom > 10.0f) {
                    modelZoom = 10.0f;
                }
            }
        }
        if ((wheel = Mouse.getEventDWheel()) != 0) {
            if (this.getScreenId() == 1) {
                if (wheel > 0) {
                    this.listSlider.scrollDown(1.0f);
                } else if (wheel < 0) {
                    this.listSlider.scrollUp(1.0f);
                }
            } else if (this.getScreenId() == 0) {
                if (wheel > 0) {
                    this.func_146284_a(this.buttonPrev);
                } else if (wheel < 0) {
                    this.func_146284_a(this.buttonNext);
                }
            }
        }
    }

    public void func_73863_a(int mouseX, int mouseY, float partialTicks) {
        GL11.glEnable((int)3042);
        GL11.glBlendFunc((int)770, (int)771);
        GL11.glColor4f((float)1.0f, (float)1.0f, (float)1.0f, (float)1.0f);
        if (this.getScreenId() == 0) {
            super.func_73863_a(mouseX, mouseY, partialTicks);
        } else {
            List inventory = this.field_147002_h.field_75151_b;
            this.field_147002_h.field_75151_b = new ArrayList();
            super.func_73863_a(mouseX, mouseY, partialTicks);
            this.field_147002_h.field_75151_b = inventory;
        }
        if (this.getScreenId() == 0 && this.current.isCurrentPageModel()) {
            RenderHelper.func_74520_c();
            this.drawModel(partialTicks);
        }
    }

    public void drawModel(float partialTicks) {
        W_ModelCustom model = this.current.getModel();
        double scl = 162.0 / ((double)MathHelper.func_76135_e((float)model.size) < 0.01 ? 0.01 : (double)model.size);
        this.field_146297_k.func_110434_K().func_110577_a(this.current.getModelTexture());
        GL11.glPushMatrix();
        double cx = (double)(model.maxX - model.minX) * 0.5 + (double)model.minX;
        double cy = (double)(model.maxY - model.minY) * 0.5 + (double)model.minY;
        double cz = (double)(model.maxZ - model.minZ) * 0.5 + (double)model.minZ;
        if (this.current.modelRot == 0) {
            GL11.glTranslated((double)(cx * scl), (double)(cz * scl), (double)0.0);
        } else {
            GL11.glTranslated((double)(cz * scl), (double)(cy * scl), (double)0.0);
        }
        GL11.glTranslated((double)((float)(this.field_147003_i + 300) + modelPosX), (double)((float)(this.field_147009_r + 110) + modelPosY), (double)550.0);
        GL11.glRotated((double)modelRotX, (double)1.0, (double)0.0, (double)0.0);
        GL11.glRotated((double)modelRotY, (double)0.0, (double)1.0, (double)0.0);
        GL11.glScaled((double)(scl * (double)modelZoom), (double)(scl * (double)modelZoom), (double)(-scl * (double)modelZoom));
        GL11.glDisable((int)32826);
        GL11.glDisable((int)2896);
        GL11.glEnable((int)3008);
        GL11.glEnable((int)3042);
        int faceNum = model.getFaceNum();
        if (this.drawFace < faceNum * 2) {
            GL11.glColor4d((double)0.1f, (double)0.1f, (double)0.1f, (double)1.0);
            GL11.glDisable((int)3553);
            GL11.glPolygonMode((int)1032, (int)6913);
            float lw = GL11.glGetFloat((int)2849);
            GL11.glLineWidth((float)1.0f);
            model.renderAll(this.drawFace - faceNum, this.drawFace);
            MCH_RenderAircraft.renderCrawlerTrack(null, (MCH_AircraftInfo)this.current.getAcInfo(), (float)partialTicks);
            GL11.glLineWidth((float)lw);
            GL11.glPolygonMode((int)1032, (int)6914);
            GL11.glEnable((int)3553);
        }
        if (this.drawFace >= faceNum) {
            GL11.glColor4d((double)1.0, (double)1.0, (double)1.0, (double)1.0);
            model.renderAll(0, this.drawFace - faceNum);
            MCH_RenderAircraft.renderCrawlerTrack(null, (MCH_AircraftInfo)this.current.getAcInfo(), (float)partialTicks);
        }
        GL11.glEnable((int)32826);
        GL11.glEnable((int)2896);
        GL11.glPopMatrix();
        if (this.drawFace < 10000000) {
            this.drawFace = (int)((float)this.drawFace + 20.0f);
        }
    }

    protected void func_146976_a(float var1, int var2, int var3) {
        W_ScaledResolution scaledresolution = new W_ScaledResolution(this.field_146297_k, this.field_146297_k.field_71443_c, this.field_146297_k.field_71440_d);
        this.scaleFactor = scaledresolution.func_78325_e();
        GL11.glColor4f((float)1.0f, (float)1.0f, (float)1.0f, (float)1.0f);
        float z = this.field_73735_i;
        this.field_73735_i = 0.0f;
        W_McClient.MOD_bindTexture((String)"textures/gui/drafting_table.png");
        if (this.getScreenId() == 0) {
            this.func_73729_b(this.field_147003_i, this.field_147009_r, 0, 0, this.field_146999_f, this.field_147000_g);
        }
        if (this.getScreenId() == 1) {
            this.func_73729_b(this.field_147003_i, this.field_147009_r, 0, this.field_147000_g, this.field_146999_f, this.field_147000_g);
            List list = (List)this.screenButtonList.get(1);
            int index = (int)this.listSlider.getSliderValue() * 2;
            for (int i = 0; i < 6; ++i) {
                W_GuiButton.setVisible((GuiButton)((GuiButton)list.get(i)), (index + i < this.getCurrentList().getRecipeListSize() ? 1 : 0) != 0);
            }
        }
        this.field_73735_i = z;
    }

    public void func_73729_b(int par1, int par2, int par3, int par4, int par5, int par6) {
        float w = 0.001953125f;
        float h = 0.001953125f;
        Tessellator tessellator = Tessellator.field_78398_a;
        tessellator.func_78382_b();
        tessellator.func_78374_a((double)(par1 + 0), (double)(par2 + par6), (double)this.field_73735_i, (double)((float)(par3 + 0) * w), (double)((float)(par4 + par6) * h));
        tessellator.func_78374_a((double)(par1 + par5), (double)(par2 + par6), (double)this.field_73735_i, (double)((float)(par3 + par5) * w), (double)((float)(par4 + par6) * h));
        tessellator.func_78374_a((double)(par1 + par5), (double)(par2 + 0), (double)this.field_73735_i, (double)((float)(par3 + par5) * w), (double)((float)(par4 + 0) * h));
        tessellator.func_78374_a((double)(par1 + 0), (double)(par2 + 0), (double)this.field_73735_i, (double)((float)(par3 + 0) * w), (double)((float)(par4 + 0) * h));
        tessellator.func_78381_a();
    }

    public void drawTexturedModalRect(int dx, int dy, int dw, int dh, int u, int v, int tw, int th) {
        float w = 0.001953125f;
        float h = 0.001953125f;
        Tessellator tessellator = Tessellator.field_78398_a;
        tessellator.func_78382_b();
        tessellator.func_78374_a((double)(dx + 0), (double)(dy + dh), (double)this.field_73735_i, (double)((float)(u + 0) * w), (double)((float)(v + th) * h));
        tessellator.func_78374_a((double)(dx + dw), (double)(dy + dh), (double)this.field_73735_i, (double)((float)(u + tw) * w), (double)((float)(v + th) * h));
        tessellator.func_78374_a((double)(dx + dw), (double)(dy + 0), (double)this.field_73735_i, (double)((float)(u + tw) * w), (double)((float)(v + 0) * h));
        tessellator.func_78374_a((double)(dx + 0), (double)(dy + 0), (double)this.field_73735_i, (double)((float)(u + 0) * w), (double)((float)(v + 0) * h));
        tessellator.func_78381_a();
    }
}

