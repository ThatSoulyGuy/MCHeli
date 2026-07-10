/*
 * Decompiled with CFR 0.152.
 */
package mcheli.lweapon;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import mcheli.MCH_ClientTickHandlerBase;
import mcheli.MCH_Config;
import mcheli.MCH_Key;
import mcheli.MCH_Lib;
import mcheli.aircraft.MCH_AircraftInfo;
import mcheli.aircraft.MCH_EntityAircraft;
import mcheli.gltd.MCH_EntityGLTD;
import mcheli.lweapon.MCH_ItemLightWeaponBase;
import mcheli.lweapon.MCH_PacketLightWeaponPlayerControl;
import mcheli.weapon.MCH_WeaponBase;
import mcheli.weapon.MCH_WeaponCreator;
import mcheli.weapon.MCH_WeaponGuidanceSystem;
import mcheli.wrapper.W_Entity;
import mcheli.wrapper.W_EntityPlayer;
import mcheli.wrapper.W_McClient;
import mcheli.wrapper.W_Network;
import mcheli.wrapper.W_PacketBase;
import mcheli.wrapper.W_Reflection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

/*
 * Exception performing whole class analysis ignored.
 */
public class MCH_ClientLightWeaponTickHandler
extends MCH_ClientTickHandlerBase {
    private static FloatBuffer screenPos = BufferUtils.createFloatBuffer((int)3);
    private static FloatBuffer screenPosBB = BufferUtils.createFloatBuffer((int)3);
    private static FloatBuffer matModel = BufferUtils.createFloatBuffer((int)16);
    private static FloatBuffer matProjection = BufferUtils.createFloatBuffer((int)16);
    private static IntBuffer matViewport = BufferUtils.createIntBuffer((int)16);
    protected boolean isHeldItem = false;
    protected boolean isBeforeHeldItem = false;
    protected EntityPlayer prevThePlayer = null;
    protected ItemStack prevItemStack = null;
    public MCH_Key KeyAttack;
    public MCH_Key KeyUseWeapon;
    public MCH_Key KeySwWeaponMode;
    public MCH_Key KeyZoom;
    public MCH_Key KeyCameraMode;
    public MCH_Key[] Keys;
    protected static MCH_WeaponBase weapon;
    public static int reloadCount;
    public static int lockonSoundCount;
    public static int weaponMode;
    public static int selectedZoom;
    public static Entity markEntity;
    public static Vec3 markPos;
    public static MCH_WeaponGuidanceSystem gs;
    public static double lockRange;

    public MCH_ClientLightWeaponTickHandler(Minecraft minecraft, MCH_Config config) {
        super(minecraft);
        this.updateKeybind(config);
        MCH_ClientLightWeaponTickHandler.gs.canLockInAir = false;
        MCH_ClientLightWeaponTickHandler.gs.canLockOnGround = false;
        MCH_ClientLightWeaponTickHandler.gs.canLockInWater = false;
        gs.setLockCountMax(40);
        MCH_ClientLightWeaponTickHandler.gs.lockRange = 120.0;
        lockonSoundCount = 0;
        this.initWeaponParam(null);
    }

    public static void markEntity(Entity entity, double x, double y, double z) {
        if (gs.getLockingEntity() == entity) {
            MCH_AircraftInfo i;
            GL11.glGetFloat((int)2982, (FloatBuffer)matModel);
            GL11.glGetFloat((int)2983, (FloatBuffer)matProjection);
            GL11.glGetInteger((int)2978, (IntBuffer)matViewport);
            GLU.gluProject((float)((float)x), (float)((float)y), (float)((float)z), (FloatBuffer)matModel, (FloatBuffer)matProjection, (IntBuffer)matViewport, (FloatBuffer)screenPos);
            MCH_AircraftInfo mCH_AircraftInfo = i = entity instanceof MCH_EntityAircraft ? ((MCH_EntityAircraft)entity).getAcInfo() : null;
            float w = i != null ? i.markerWidth : (entity.field_70130_N > entity.field_70131_O ? entity.field_70130_N : entity.field_70131_O);
            float h = i != null ? i.markerHeight : entity.field_70131_O;
            GLU.gluProject((float)((float)x + w), (float)((float)y + h), (float)((float)z + w), (FloatBuffer)matModel, (FloatBuffer)matProjection, (IntBuffer)matViewport, (FloatBuffer)screenPosBB);
            markEntity = entity;
        }
    }

    public static Vec3 getMartEntityPos() {
        if (gs.getLockingEntity() == markEntity && markEntity != null) {
            return Vec3.func_72443_a((double)screenPos.get(0), (double)screenPos.get(1), (double)screenPos.get(2));
        }
        return null;
    }

    public static Vec3 getMartEntityBBPos() {
        if (gs.getLockingEntity() == markEntity && markEntity != null) {
            return Vec3.func_72443_a((double)screenPosBB.get(0), (double)screenPosBB.get(1), (double)screenPosBB.get(2));
        }
        return null;
    }

    public void initWeaponParam(EntityPlayer player) {
        reloadCount = 0;
        weaponMode = 0;
        selectedZoom = 0;
    }

    public void updateKeybind(MCH_Config config) {
        this.KeyAttack = new MCH_Key(MCH_Config.KeyAttack.prmInt);
        this.KeyUseWeapon = new MCH_Key(MCH_Config.KeyUseWeapon.prmInt);
        this.KeySwWeaponMode = new MCH_Key(MCH_Config.KeySwWeaponMode.prmInt);
        this.KeyZoom = new MCH_Key(MCH_Config.KeyZoom.prmInt);
        this.KeyCameraMode = new MCH_Key(MCH_Config.KeyCameraMode.prmInt);
        this.Keys = new MCH_Key[]{this.KeyAttack, this.KeyUseWeapon, this.KeySwWeaponMode, this.KeyZoom, this.KeyCameraMode};
    }

    protected void onTick(boolean inGUI) {
        ItemStack is;
        EntityClientPlayerMP player;
        block34: {
            block31: {
                MCH_ItemLightWeaponBase lweapon;
                block33: {
                    block32: {
                        for (MCH_Key k : this.Keys) {
                            k.update();
                        }
                        this.isBeforeHeldItem = this.isHeldItem;
                        player = this.mc.field_71439_g;
                        if (this.prevThePlayer == null || this.prevThePlayer != player) {
                            this.initWeaponParam((EntityPlayer)player);
                            this.prevThePlayer = player;
                        }
                        ItemStack itemStack = is = player != null ? player.func_70694_bm() : null;
                        if (player == null || player.field_70154_o instanceof MCH_EntityGLTD || player.field_70154_o instanceof MCH_EntityAircraft) {
                            is = null;
                        }
                        if (gs.getLockingEntity() == null) {
                            markEntity = null;
                        }
                        if (is == null || !(is.func_77973_b() instanceof MCH_ItemLightWeaponBase)) break block31;
                        lweapon = (MCH_ItemLightWeaponBase)is.func_77973_b();
                        if (this.prevItemStack == null || !this.prevItemStack.func_77969_a(is) && !this.prevItemStack.func_77977_a().equals(is.func_77977_a())) {
                            this.initWeaponParam((EntityPlayer)player);
                            weapon = MCH_WeaponCreator.createWeapon((World)player.field_70170_p, (String)MCH_ItemLightWeaponBase.getName((ItemStack)is), (Vec3)Vec3.func_72443_a((double)0.0, (double)0.0, (double)0.0), (float)0.0f, (float)0.0f, null, (boolean)false);
                            if (weapon != null) {
                                if (weapon.getInfo() != null) {
                                    if (weapon.getGuidanceSystem() != null) {
                                        gs = weapon.getGuidanceSystem();
                                    }
                                }
                            }
                        }
                        if (weapon == null) break block32;
                        if (gs != null) break block33;
                    }
                    return;
                }
                gs.setWorld(player.field_70170_p);
                MCH_ClientLightWeaponTickHandler.gs.lockRange = lockRange;
                if (player.func_71057_bx() > 10) {
                    W_Reflection.setCameraZoom((float)MCH_ClientLightWeaponTickHandler.weapon.getInfo().zoom[selectedZoom %= MCH_ClientLightWeaponTickHandler.weapon.getInfo().zoom.length]);
                } else {
                    W_Reflection.restoreCameraZoom();
                }
                if (is.func_77960_j() < is.func_77958_k()) {
                    if (player.func_71057_bx() > 10) {
                        gs.lock((Entity)player);
                        if (gs.getLockCount() > 0) {
                            if (lockonSoundCount > 0) {
                                --lockonSoundCount;
                            } else {
                                lockonSoundCount = 7;
                                lockonSoundCount = (int)((double)lockonSoundCount * (1.0 - (double)gs.getLockCount() / (double)gs.getLockCountMax()));
                                if (lockonSoundCount < 3) {
                                    lockonSoundCount = 2;
                                }
                                W_McClient.MOD_playSoundFX((String)"lockon", (float)1.0f, (float)1.0f);
                            }
                        }
                    } else {
                        W_Reflection.restoreCameraZoom();
                        gs.clearLock();
                    }
                    reloadCount = 0;
                } else {
                    lockonSoundCount = 0;
                    if (W_EntityPlayer.hasItem((EntityPlayer)player, (Item)lweapon.bullet) && player.func_71052_bv() <= 0) {
                        if (reloadCount == 10) {
                            W_McClient.MOD_playSoundFX((String)"fim92_reload", (float)1.0f, (float)1.0f);
                        }
                        int RELOAD_CNT = 40;
                        if (reloadCount < 40) {
                            if (++reloadCount == 40) {
                                this.onCompleteReload();
                            }
                        }
                    } else {
                        reloadCount = 0;
                    }
                    gs.clearLock();
                }
                if (!inGUI) {
                    this.playerControl((EntityPlayer)player, is, (MCH_ItemLightWeaponBase)is.func_77973_b());
                }
                this.isHeldItem = MCH_ItemLightWeaponBase.isHeld((EntityPlayer)player);
                break block34;
            }
            lockonSoundCount = 0;
            reloadCount = 0;
            this.isHeldItem = false;
        }
        if (this.isBeforeHeldItem != this.isHeldItem) {
            MCH_Lib.DbgLog((boolean)true, (String)"LWeapon cancel", (Object[])new Object[0]);
            if (!this.isHeldItem) {
                if (MCH_ClientLightWeaponTickHandler.getPotionNightVisionDuration((EntityPlayer)player) < 250) {
                    MCH_PacketLightWeaponPlayerControl pc = new MCH_PacketLightWeaponPlayerControl();
                    pc.camMode = 1;
                    W_Network.sendToServer((W_PacketBase)pc);
                    player.func_70618_n(Potion.field_76439_r.func_76396_c());
                }
                W_Reflection.restoreCameraZoom();
            }
        }
        this.prevItemStack = is;
        gs.update();
    }

    protected void onCompleteReload() {
        MCH_PacketLightWeaponPlayerControl pc = new MCH_PacketLightWeaponPlayerControl();
        pc.cmpReload = 1;
        W_Network.sendToServer((W_PacketBase)pc);
    }

    protected void playerControl(EntityPlayer player, ItemStack is, MCH_ItemLightWeaponBase item) {
        MCH_PacketLightWeaponPlayerControl pc = new MCH_PacketLightWeaponPlayerControl();
        boolean send = false;
        boolean autoShot = false;
        if (MCH_Config.LWeaponAutoFire.prmBool && is.func_77960_j() < is.func_77958_k() && gs.isLockComplete()) {
            autoShot = true;
        }
        if (this.KeySwWeaponMode.isKeyDown()) {
            if (MCH_ClientLightWeaponTickHandler.weapon.numMode > 1) {
                weaponMode = (weaponMode + 1) % MCH_ClientLightWeaponTickHandler.weapon.numMode;
                W_McClient.MOD_playSoundFX((String)"pi", (float)0.5f, (float)0.9f);
            }
        }
        if (this.KeyAttack.isKeyPress() || autoShot) {
            boolean result = false;
            if (is.func_77960_j() < is.func_77958_k() && gs.isLockComplete()) {
                boolean canFire = true;
                if (weaponMode > 0 && gs.getTargetEntity() != null) {
                    double dx = MCH_ClientLightWeaponTickHandler.gs.getTargetEntity().field_70165_t - player.field_70165_t;
                    double dz = MCH_ClientLightWeaponTickHandler.gs.getTargetEntity().field_70161_v - player.field_70161_v;
                    boolean bl = canFire = Math.sqrt(dx * dx + dz * dz) >= 40.0;
                }
                if (canFire) {
                    pc.useWeapon = true;
                    pc.useWeaponOption1 = W_Entity.getEntityId((Entity)MCH_ClientLightWeaponTickHandler.gs.lastLockEntity);
                    pc.useWeaponOption2 = weaponMode;
                    pc.useWeaponPosX = player.field_70165_t;
                    pc.useWeaponPosY = player.field_70163_u;
                    pc.useWeaponPosZ = player.field_70161_v;
                    gs.clearLock();
                    send = true;
                    result = true;
                }
            }
            if (this.KeyAttack.isKeyDown() && !result && player.func_71057_bx() > 5) {
                MCH_ClientLightWeaponTickHandler.playSoundNG();
            }
        }
        if (this.KeyZoom.isKeyDown()) {
            int prevZoom = selectedZoom;
            selectedZoom = (selectedZoom + 1) % MCH_ClientLightWeaponTickHandler.weapon.getInfo().zoom.length;
            if (prevZoom != selectedZoom) {
                MCH_ClientLightWeaponTickHandler.playSound((String)"zoom", (float)0.5f, (float)1.0f);
            }
        }
        if (this.KeyCameraMode.isKeyDown()) {
            PotionEffect pe = player.func_70660_b(Potion.field_76439_r);
            MCH_Lib.DbgLog((boolean)true, (String)"LWeapon NV %s", (Object[])new Object[]{pe != null ? "ON->OFF" : "OFF->ON"});
            if (pe != null) {
                player.func_70618_n(Potion.field_76439_r.func_76396_c());
                pc.camMode = 1;
                send = true;
                W_McClient.MOD_playSoundFX((String)"pi", (float)0.5f, (float)0.9f);
            } else if (player.func_71057_bx() > 60) {
                pc.camMode = 2;
                send = true;
                W_McClient.MOD_playSoundFX((String)"pi", (float)0.5f, (float)0.9f);
            } else {
                MCH_ClientLightWeaponTickHandler.playSoundNG();
            }
        }
        if (send) {
            W_Network.sendToServer((W_PacketBase)pc);
        }
    }

    public static int getPotionNightVisionDuration(EntityPlayer player) {
        PotionEffect cpe = player.func_70660_b(Potion.field_76439_r);
        return player == null || cpe == null ? 0 : cpe.func_76459_b();
    }

    static {
        markEntity = null;
        markPos = Vec3.func_72443_a((double)0.0, (double)0.0, (double)0.0);
        gs = new MCH_WeaponGuidanceSystem();
        lockRange = 120.0;
    }
}

