/*
 * Decompiled with CFR 0.152.
 */
package mcheli;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import mcheli.MCH_ClientEventHook;
import mcheli.MCH_ClientTickHandlerBase;
import mcheli.MCH_Config;
import mcheli.MCH_GuiCommon;
import mcheli.MCH_Key;
import mcheli.MCH_PacketIndOpenScreen;
import mcheli.MCH_ServerSettings;
import mcheli.MCH_ViewEntityDummy;
import mcheli.aircraft.MCH_AircraftInfo;
import mcheli.aircraft.MCH_ClientSeatTickHandler;
import mcheli.aircraft.MCH_EntityAircraft;
import mcheli.aircraft.MCH_EntitySeat;
import mcheli.aircraft.MCH_SeatInfo;
import mcheli.command.MCH_GuiTitle;
import mcheli.gltd.MCH_ClientGLTDTickHandler;
import mcheli.gltd.MCH_EntityGLTD;
import mcheli.gltd.MCH_GuiGLTD;
import mcheli.gui.MCH_Gui;
import mcheli.helicopter.MCH_ClientHeliTickHandler;
import mcheli.helicopter.MCH_EntityHeli;
import mcheli.helicopter.MCH_GuiHeli;
import mcheli.lweapon.MCH_ClientLightWeaponTickHandler;
import mcheli.lweapon.MCH_GuiLightWeapon;
import mcheli.multiplay.MCH_GuiScoreboard;
import mcheli.multiplay.MCH_GuiTargetMarker;
import mcheli.multiplay.MCH_MultiplayClient;
import mcheli.plane.MCP_ClientPlaneTickHandler;
import mcheli.plane.MCP_EntityPlane;
import mcheli.plane.MCP_GuiPlane;
import mcheli.tank.MCH_ClientTankTickHandler;
import mcheli.tank.MCH_EntityTank;
import mcheli.tank.MCH_GuiTank;
import mcheli.tool.MCH_ClientToolTickHandler;
import mcheli.tool.MCH_GuiWrench;
import mcheli.tool.MCH_ItemWrench;
import mcheli.tool.rangefinder.MCH_GuiRangeFinder;
import mcheli.uav.MCH_EntityUavStation;
import mcheli.vehicle.MCH_ClientVehicleTickHandler;
import mcheli.vehicle.MCH_EntityVehicle;
import mcheli.vehicle.MCH_GuiVehicle;
import mcheli.weapon.MCH_WeaponSet;
import mcheli.wrapper.W_Lib;
import mcheli.wrapper.W_McClient;
import mcheli.wrapper.W_Reflection;
import mcheli.wrapper.W_TickHandler;
import mcheli.wrapper.W_Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import org.lwjgl.opengl.Display;

/*
 * Exception performing whole class analysis ignored.
 */
@SideOnly(value=Side.CLIENT)
public class MCH_ClientCommonTickHandler
extends W_TickHandler {
    public static MCH_ClientCommonTickHandler instance;
    public MCH_GuiCommon gui_Common;
    public MCH_Gui gui_Heli;
    public MCH_Gui gui_Plane;
    public MCH_Gui gui_Tank;
    public MCH_Gui gui_GLTD;
    public MCH_Gui gui_Vehicle;
    public MCH_Gui gui_LWeapon;
    public MCH_Gui gui_Wrench;
    public MCH_Gui gui_EMarker;
    public MCH_Gui gui_RngFndr;
    public MCH_Gui gui_Title;
    public MCH_Gui[] guis;
    public MCH_Gui[] guiTicks;
    public MCH_ClientTickHandlerBase[] ticks;
    public MCH_Key[] Keys;
    public MCH_Key KeyCamDistUp;
    public MCH_Key KeyCamDistDown;
    public MCH_Key KeyScoreboard;
    public MCH_Key KeyMultiplayManager;
    public static int cameraMode;
    public static MCH_EntityAircraft ridingAircraft;
    public static boolean isDrawScoreboard;
    public static int sendLDCount;
    public static boolean isLocked;
    public static int lockedSoundCount;
    int debugcnt;
    private static double prevMouseDeltaX;
    private static double prevMouseDeltaY;
    private static double mouseDeltaX;
    private static double mouseDeltaY;
    private static double mouseRollDeltaX;
    private static double mouseRollDeltaY;
    private static boolean isRideAircraft;
    private static float prevTick;

    public MCH_ClientCommonTickHandler(Minecraft minecraft, MCH_Config config) {
        super(minecraft);
        this.gui_Common = new MCH_GuiCommon(minecraft);
        this.gui_Heli = new MCH_GuiHeli(minecraft);
        this.gui_Plane = new MCP_GuiPlane(minecraft);
        this.gui_Tank = new MCH_GuiTank(minecraft);
        this.gui_GLTD = new MCH_GuiGLTD(minecraft);
        this.gui_Vehicle = new MCH_GuiVehicle(minecraft);
        this.gui_LWeapon = new MCH_GuiLightWeapon(minecraft);
        this.gui_Wrench = new MCH_GuiWrench(minecraft);
        this.gui_RngFndr = new MCH_GuiRangeFinder(minecraft);
        this.gui_EMarker = new MCH_GuiTargetMarker(minecraft);
        this.gui_Title = new MCH_GuiTitle(minecraft);
        this.guis = new MCH_Gui[]{this.gui_RngFndr, this.gui_LWeapon, this.gui_Heli, this.gui_Plane, this.gui_Tank, this.gui_GLTD, this.gui_Vehicle};
        this.guiTicks = new MCH_Gui[]{this.gui_Common, this.gui_Heli, this.gui_Plane, this.gui_Tank, this.gui_GLTD, this.gui_Vehicle, this.gui_LWeapon, this.gui_Wrench, this.gui_RngFndr, this.gui_EMarker, this.gui_Title};
        this.ticks = new MCH_ClientTickHandlerBase[]{new MCH_ClientHeliTickHandler(minecraft, config), new MCP_ClientPlaneTickHandler(minecraft, config), new MCH_ClientTankTickHandler(minecraft, config), new MCH_ClientGLTDTickHandler(minecraft, config), new MCH_ClientVehicleTickHandler(minecraft, config), new MCH_ClientLightWeaponTickHandler(minecraft, config), new MCH_ClientSeatTickHandler(minecraft, config), new MCH_ClientToolTickHandler(minecraft, config)};
        this.updatekeybind(config);
    }

    public void updatekeybind(MCH_Config config) {
        this.KeyCamDistUp = new MCH_Key(MCH_Config.KeyCameraDistUp.prmInt);
        this.KeyCamDistDown = new MCH_Key(MCH_Config.KeyCameraDistDown.prmInt);
        this.KeyScoreboard = new MCH_Key(MCH_Config.KeyScoreboard.prmInt);
        this.KeyMultiplayManager = new MCH_Key(MCH_Config.KeyMultiplayManager.prmInt);
        this.Keys = new MCH_Key[]{this.KeyCamDistUp, this.KeyCamDistDown, this.KeyScoreboard, this.KeyMultiplayManager};
        for (MCH_ClientTickHandlerBase t : this.ticks) {
            t.updateKeybind(config);
        }
    }

    public String getLabel() {
        return null;
    }

    public void onTick() {
        EntityClientPlayerMP player;
        block20: {
            block21: {
                MCH_ClientTickHandlerBase.initRotLimit();
                for (MCH_Key k : this.Keys) {
                    k.update();
                }
                player = this.mc.field_71439_g;
                if (player == null || this.mc.field_71462_r != null) break block20;
                if (MCH_ServerSettings.enableCamDistChange && (this.KeyCamDistUp.isKeyDown() || this.KeyCamDistDown.isKeyDown())) {
                    int camdist = (int)W_Reflection.getThirdPersonDistance();
                    if (this.KeyCamDistUp.isKeyDown() && camdist < 60) {
                        if ((camdist += 4) > 60) {
                            camdist = 60;
                        }
                        W_Reflection.setThirdPersonDistance((float)camdist);
                    } else if (this.KeyCamDistDown.isKeyDown()) {
                        if ((camdist -= 4) < 4) {
                            camdist = 4;
                        }
                        W_Reflection.setThirdPersonDistance((float)camdist);
                    }
                }
                if (this.mc.field_71462_r != null) break block20;
                if (!this.mc.func_71356_B()) break block21;
                if (!MCH_Config.DebugLog) break block20;
            }
            if (!(isDrawScoreboard = this.KeyScoreboard.isKeyPress()) && this.KeyMultiplayManager.isKeyDown()) {
                MCH_PacketIndOpenScreen.send((int)5);
            }
        }
        if (sendLDCount < 10) {
            ++sendLDCount;
        } else {
            MCH_MultiplayClient.sendImageData();
            sendLDCount = 0;
        }
        boolean inOtherGui = this.mc.field_71462_r != null;
        for (MCH_ClientTickHandlerBase mCH_ClientTickHandlerBase : this.ticks) {
            mCH_ClientTickHandlerBase.onTick(inOtherGui);
        }
        for (MCH_ClientTickHandlerBase mCH_ClientTickHandlerBase : this.guiTicks) {
            mCH_ClientTickHandlerBase.onTick();
        }
        MCH_EntityAircraft ac = MCH_EntityAircraft.getAircraft_RiddenOrControl((Entity)player);
        if (player != null && ac != null && !ac.isDestroyed()) {
            if (isLocked && lockedSoundCount == 0) {
                isLocked = false;
                lockedSoundCount = 20;
                MCH_ClientTickHandlerBase.playSound((String)"locked");
            }
        } else {
            lockedSoundCount = 0;
            isLocked = false;
        }
        if (lockedSoundCount > 0) {
            --lockedSoundCount;
        }
    }

    public void onTickPre() {
        if (this.mc.field_71439_g != null && this.mc.field_71441_e != null) {
            this.onTick();
        }
    }

    public void onTickPost() {
        if (this.mc.field_71439_g != null && this.mc.field_71441_e != null) {
            MCH_GuiTargetMarker.onClientTick();
        }
    }

    public static double getCurrentStickX() {
        return mouseRollDeltaX;
    }

    public static double getCurrentStickY() {
        double inv = 1.0;
        if (Minecraft.func_71410_x().field_71474_y.field_74338_d) {
            inv = -inv;
        }
        if (MCH_Config.InvertMouse.prmBool) {
            inv = -inv;
        }
        return mouseRollDeltaY * inv;
    }

    public static double getMaxStickLength() {
        return 40.0;
    }

    public void updateMouseDelta(boolean stickMode, float partialTicks) {
        prevMouseDeltaX = mouseDeltaX;
        prevMouseDeltaY = mouseDeltaY;
        mouseDeltaX = 0.0;
        mouseDeltaY = 0.0;
        if (this.mc.field_71415_G && Display.isActive() && this.mc.field_71462_r == null) {
            if (stickMode) {
                if (Math.abs(mouseRollDeltaX) < MCH_ClientCommonTickHandler.getMaxStickLength() * 0.2) {
                    mouseRollDeltaX *= (double)(1.0f - 0.15f * partialTicks);
                }
                if (Math.abs(mouseRollDeltaY) < MCH_ClientCommonTickHandler.getMaxStickLength() * 0.2) {
                    mouseRollDeltaY *= (double)(1.0f - 0.15f * partialTicks);
                }
            }
            this.mc.field_71417_B.func_74374_c();
            float f1 = this.mc.field_71474_y.field_74341_c * 0.6f + 0.2f;
            float f2 = f1 * f1 * f1 * 8.0f;
            double ms = MCH_Config.MouseSensitivity.prmDouble * 0.1;
            mouseDeltaX = ms * (double)this.mc.field_71417_B.field_74377_a * (double)f2;
            mouseDeltaY = ms * (double)this.mc.field_71417_B.field_74375_b * (double)f2;
            int inv = 1;
            if (this.mc.field_71474_y.field_74338_d) {
                inv = -1;
            }
            if (MCH_Config.InvertMouse.prmBool) {
                inv = (byte)(inv * -1);
            }
            mouseRollDeltaX += mouseDeltaX;
            mouseRollDeltaY += mouseDeltaY * (double)inv;
            double dist = mouseRollDeltaX * mouseRollDeltaX + mouseRollDeltaY * mouseRollDeltaY;
            if (dist > 1.0) {
                double d = dist = (double)MathHelper.func_76133_a((double)dist);
                if (d > MCH_ClientCommonTickHandler.getMaxStickLength()) {
                    d = MCH_ClientCommonTickHandler.getMaxStickLength();
                }
                mouseRollDeltaX /= dist;
                mouseRollDeltaY /= dist;
                mouseRollDeltaX *= d;
                mouseRollDeltaY *= d;
            }
        }
    }

    public void onRenderTickPre(float partialTicks) {
        MCH_ViewEntityDummy de;
        MCH_GuiTargetMarker.clearMarkEntityPos();
        if (!MCH_ServerSettings.enableDebugBoundingBox) {
            RenderManager.field_85095_o = false;
        }
        MCH_ClientEventHook.haveSearchLightAircraft.clear();
        if (this.mc != null && this.mc.field_71441_e != null) {
            for (Object o : Minecraft.func_71410_x().field_71441_e.field_72996_f) {
                if (!(o instanceof MCH_EntityAircraft) || !((MCH_EntityAircraft)o).haveSearchLight()) continue;
                MCH_ClientEventHook.haveSearchLightAircraft.add((MCH_EntityAircraft)o);
            }
        }
        if (W_McClient.isGamePaused()) {
            return;
        }
        EntityClientPlayerMP player = this.mc.field_71439_g;
        if (player == null) {
            return;
        }
        ItemStack currentItemstack = player.func_71045_bC();
        if (currentItemstack != null && currentItemstack.func_77973_b() instanceof MCH_ItemWrench && player.func_71052_bv() > 0) {
            W_Reflection.setItemRendererProgress((float)1.0f);
        }
        if ((ridingAircraft = MCH_EntityAircraft.getAircraft_RiddenOrControl((Entity)player)) != null) {
            cameraMode = ridingAircraft.getCameraMode((EntityPlayer)player);
        } else if (player.field_70154_o instanceof MCH_EntityGLTD) {
            MCH_EntityGLTD gltd = (MCH_EntityGLTD)player.field_70154_o;
            cameraMode = gltd.camera.getMode(0);
        } else {
            cameraMode = 0;
        }
        MCH_EntityAircraft ac = null;
        if (player.field_70154_o instanceof MCH_EntityHeli || player.field_70154_o instanceof MCP_EntityPlane || player.field_70154_o instanceof MCH_EntityTank) {
            ac = (MCH_EntityAircraft)player.field_70154_o;
        } else if (player.field_70154_o instanceof MCH_EntityUavStation) {
            ac = ((MCH_EntityUavStation)player.field_70154_o).getControlAircract();
        } else if (player.field_70154_o instanceof MCH_EntityVehicle) {
            MCH_EntityAircraft vehicle = (MCH_EntityAircraft)player.field_70154_o;
            vehicle.setupAllRiderRenderPosition(partialTicks, (EntityPlayer)player);
        }
        boolean stickMode = false;
        if (ac instanceof MCH_EntityHeli) {
            stickMode = MCH_Config.MouseControlStickModeHeli.prmBool;
        }
        if (ac instanceof MCP_EntityPlane) {
            stickMode = MCH_Config.MouseControlStickModePlane.prmBool;
        }
        for (int i = 0; i < 10; ++i) {
            if (!(prevTick > partialTicks)) break;
            prevTick -= 1.0f;
        }
        if (ac != null && ac.canMouseRot()) {
            MCH_AircraftInfo.CameraPosition cp;
            if (!isRideAircraft) {
                ac.onInteractFirst((EntityPlayer)player);
            }
            isRideAircraft = true;
            this.updateMouseDelta(stickMode, partialTicks);
            boolean fixRot = false;
            float fixYaw = 0.0f;
            float fixPitch = 0.0f;
            MCH_SeatInfo seatInfo = ac.getSeatInfo((Entity)player);
            if (seatInfo != null && seatInfo.fixRot && ac.getIsGunnerMode((Entity)player) && !ac.isGunnerLookMode((EntityPlayer)player)) {
                fixRot = true;
                fixYaw = seatInfo.fixYaw;
                fixPitch = seatInfo.fixPitch;
                mouseRollDeltaX *= 0.0;
                mouseRollDeltaY *= 0.0;
                mouseDeltaX *= 0.0;
                mouseDeltaY *= 0.0;
            } else if (ac.isPilot((Entity)player) && (cp = ac.getCameraPosInfo()) != null) {
                fixYaw = cp.yaw;
                fixPitch = cp.pitch;
            }
            if (ac.getAcInfo() == null) {
                player.func_70082_c((float)mouseDeltaX, (float)mouseDeltaY);
            } else {
                ac.setAngles((Entity)player, fixRot, fixYaw, fixPitch, (float)(mouseDeltaX + prevMouseDeltaX) / 2.0f, (float)(mouseDeltaY + prevMouseDeltaY) / 2.0f, (float)mouseRollDeltaX, (float)mouseRollDeltaY, partialTicks - prevTick);
            }
            ac.setupAllRiderRenderPosition(partialTicks, (EntityPlayer)player);
            double dist = MathHelper.func_76133_a((double)(mouseRollDeltaX * mouseRollDeltaX + mouseRollDeltaY * mouseRollDeltaY));
            if (!stickMode || dist < MCH_ClientCommonTickHandler.getMaxStickLength() * 0.1) {
                mouseRollDeltaX *= 0.95;
                mouseRollDeltaY *= 0.95;
            }
            float roll = MathHelper.func_76142_g((float)ac.getRotRoll());
            float yaw = MathHelper.func_76142_g((float)(ac.getRotYaw() - player.field_70177_z));
            roll *= MathHelper.func_76134_b((float)((float)((double)yaw * Math.PI / 180.0)));
            if (ac.getTVMissile() != null && W_Lib.isClientPlayer((Entity)ac.getTVMissile().shootingEntity) && ac.getIsGunnerMode((Entity)player)) {
                roll = 0.0f;
            }
            W_Reflection.setCameraRoll((float)roll);
            this.correctViewEntityDummy((Entity)player);
        } else {
            MCH_EntitySeat seat;
            MCH_EntitySeat mCH_EntitySeat = seat = player.field_70154_o instanceof MCH_EntitySeat ? (MCH_EntitySeat)player.field_70154_o : null;
            if (seat != null && seat.getParent() != null) {
                this.updateMouseDelta(stickMode, partialTicks);
                ac = seat.getParent();
                boolean fixRot = false;
                MCH_SeatInfo seatInfo = ac.getSeatInfo((Entity)player);
                if (seatInfo != null && seatInfo.fixRot && ac.getIsGunnerMode((Entity)player) && !ac.isGunnerLookMode((EntityPlayer)player)) {
                    fixRot = true;
                    mouseRollDeltaX *= 0.0;
                    mouseRollDeltaY *= 0.0;
                    mouseDeltaX *= 0.0;
                    mouseDeltaY *= 0.0;
                }
                Vec3 v = Vec3.func_72443_a((double)mouseDeltaX, (double)mouseRollDeltaY, (double)0.0);
                W_Vec3.rotateAroundZ((float)((float)((double)(ac.calcRotRoll(partialTicks) / 180.0f) * Math.PI)), (Vec3)v);
                MCH_WeaponSet ws = ac.getCurrentWeapon((Entity)player);
                double d = ws != null && ws.getInfo() != null ? (double)ws.getInfo().cameraRotationSpeedPitch : 1.0;
                player.func_70082_c((float)mouseDeltaX, (float)(mouseDeltaY *= d));
                float y = ac.getRotYaw();
                float p = ac.getRotPitch();
                float r = ac.getRotRoll();
                ac.setRotYaw(ac.calcRotYaw(partialTicks));
                ac.setRotPitch(ac.calcRotPitch(partialTicks));
                ac.setRotRoll(ac.calcRotRoll(partialTicks));
                float revRoll = 0.0f;
                if (fixRot) {
                    player.field_70177_z = ac.getRotYaw() + seatInfo.fixYaw;
                    player.field_70125_A = ac.getRotPitch() + seatInfo.fixPitch;
                    if (player.field_70125_A > 90.0f) {
                        player.field_70127_C -= (player.field_70125_A - 90.0f) * 2.0f;
                        player.field_70125_A -= (player.field_70125_A - 90.0f) * 2.0f;
                        player.field_70126_B += 180.0f;
                        player.field_70177_z += 180.0f;
                        revRoll = 180.0f;
                    } else if (player.field_70125_A < -90.0f) {
                        player.field_70127_C -= (player.field_70125_A - 90.0f) * 2.0f;
                        player.field_70125_A -= (player.field_70125_A - 90.0f) * 2.0f;
                        player.field_70126_B += 180.0f;
                        player.field_70177_z += 180.0f;
                        revRoll = 180.0f;
                    }
                }
                ac.setupAllRiderRenderPosition(partialTicks, (EntityPlayer)player);
                ac.setRotYaw(y);
                ac.setRotPitch(p);
                ac.setRotRoll(r);
                mouseRollDeltaX *= 0.9;
                mouseRollDeltaY *= 0.9;
                float roll = MathHelper.func_76142_g((float)ac.getRotRoll());
                float yaw = MathHelper.func_76142_g((float)(ac.getRotYaw() - player.field_70177_z));
                roll *= MathHelper.func_76134_b((float)((float)((double)yaw * Math.PI / 180.0)));
                if (ac.getTVMissile() != null && W_Lib.isClientPlayer((Entity)ac.getTVMissile().shootingEntity) && ac.getIsGunnerMode((Entity)player)) {
                    roll = 0.0f;
                }
                W_Reflection.setCameraRoll((float)(roll + revRoll));
                this.correctViewEntityDummy((Entity)player);
            } else {
                if (isRideAircraft) {
                    W_Reflection.setCameraRoll((float)0.0f);
                    isRideAircraft = false;
                }
                mouseRollDeltaX = 0.0;
                mouseRollDeltaY = 0.0;
            }
        }
        if (ac != null) {
            if (ac.getSeatIdByEntity((Entity)player) == 0 && !ac.isDestroyed()) {
                ac.lastRiderYaw = player.field_70177_z;
                ac.prevLastRiderYaw = player.field_70126_B;
                ac.lastRiderPitch = player.field_70125_A;
                ac.prevLastRiderPitch = player.field_70127_C;
            }
            ac.updateWeaponsRotation();
        }
        if ((de = MCH_ViewEntityDummy.getInstance((World)player.field_70170_p)) != null) {
            MCH_WeaponSet wi;
            de.field_70177_z = player.field_70177_z;
            de.field_70126_B = player.field_70126_B;
            if (ac != null && (wi = ac.getCurrentWeapon((Entity)player)) != null && wi.getInfo() != null && wi.getInfo().fixCameraPitch) {
                de.field_70127_C = 0.0f;
                de.field_70125_A = 0.0f;
            }
        }
        prevTick = partialTicks;
    }

    public void correctViewEntityDummy(Entity entity) {
        MCH_ViewEntityDummy de = MCH_ViewEntityDummy.getInstance((World)entity.field_70170_p);
        if (de != null) {
            if (de.field_70177_z - de.field_70126_B > 180.0f) {
                de.field_70126_B += 360.0f;
            } else if (de.field_70177_z - de.field_70126_B < -180.0f) {
                de.field_70126_B -= 360.0f;
            }
        }
    }

    public void onPlayerTickPre(EntityPlayer player) {
        ItemStack currentItemstack;
        if (player.field_70170_p.field_72995_K && (currentItemstack = player.func_71045_bC()) != null && currentItemstack.func_77973_b() instanceof MCH_ItemWrench && player.func_71052_bv() > 0 && player.func_71011_bu() != currentItemstack) {
            int maxdm = currentItemstack.func_77958_k();
            int dm = currentItemstack.func_77960_j();
            if (dm <= maxdm && dm > 0) {
                player.func_71008_a(currentItemstack, player.func_71052_bv());
            }
        }
    }

    public void onPlayerTickPost(EntityPlayer player) {
    }

    public void onRenderTickPost(float partialTicks) {
        if (this.mc.field_71439_g != null) {
            MCH_ClientTickHandlerBase.applyRotLimit((Entity)this.mc.field_71439_g);
            MCH_ViewEntityDummy e = MCH_ViewEntityDummy.getInstance((World)this.mc.field_71439_g.field_70170_p);
            if (e != null) {
                e.field_70125_A = this.mc.field_71439_g.field_70125_A;
                e.field_70177_z = this.mc.field_71439_g.field_70177_z;
                e.field_70127_C = this.mc.field_71439_g.field_70127_C;
                e.field_70126_B = this.mc.field_71439_g.field_70126_B;
            }
        }
        if (this.mc.field_71462_r == null || this.mc.field_71462_r instanceof GuiChat || this.mc.field_71462_r.getClass().toString().indexOf("GuiDriveableController") >= 0) {
            for (MCH_Gui gui : this.guis) {
                if (this.drawGui(gui, partialTicks)) break;
            }
            this.drawGui((MCH_Gui)this.gui_Common, partialTicks);
            this.drawGui(this.gui_Wrench, partialTicks);
            this.drawGui(this.gui_EMarker, partialTicks);
            if (isDrawScoreboard) {
                MCH_GuiScoreboard.drawList((Minecraft)this.mc, (FontRenderer)this.mc.field_71466_p, (boolean)false);
            }
            this.drawGui(this.gui_Title, partialTicks);
        }
    }

    public boolean drawGui(MCH_Gui gui, float partialTicks) {
        if (gui.isDrawGui((EntityPlayer)this.mc.field_71439_g)) {
            gui.func_73863_a(0, 0, partialTicks);
            return true;
        }
        return false;
    }

    static {
        cameraMode = 0;
        ridingAircraft = null;
        isDrawScoreboard = false;
        sendLDCount = 0;
        isLocked = false;
        lockedSoundCount = 0;
        mouseDeltaX = 0.0;
        mouseDeltaY = 0.0;
        mouseRollDeltaX = 0.0;
        mouseRollDeltaY = 0.0;
        isRideAircraft = false;
        prevTick = 0.0f;
    }
}

