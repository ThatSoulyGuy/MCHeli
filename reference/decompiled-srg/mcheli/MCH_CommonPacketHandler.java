/*
 * Decompiled with CFR 0.152.
 */
package mcheli;

import com.google.common.io.ByteArrayDataInput;
import mcheli.MCH_Config;
import mcheli.MCH_Explosion;
import mcheli.MCH_Lib;
import mcheli.MCH_MOD;
import mcheli.MCH_PacketEffectExplosion;
import mcheli.MCH_PacketIndOpenScreen;
import mcheli.MCH_PacketNotifyLock;
import mcheli.MCH_PacketNotifyServerSettings;
import mcheli.MCH_ServerSettings;
import mcheli.aircraft.MCH_EntityAircraft;
import mcheli.aircraft.MCH_EntitySeat;
import mcheli.lweapon.MCH_ClientLightWeaponTickHandler;
import mcheli.wrapper.W_Reflection;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

public class MCH_CommonPacketHandler {
    public static void onPacketEffectExplosion(EntityPlayer player, ByteArrayDataInput data) {
        if (!player.field_70170_p.field_72995_K) {
            return;
        }
        MCH_PacketEffectExplosion pkt = new MCH_PacketEffectExplosion();
        pkt.readData(data);
        Entity exploder = null;
        if (player.func_70092_e(pkt.prm.posX, pkt.prm.posY, pkt.prm.posZ) <= 40000.0) {
            if (!pkt.prm.inWater) {
                if (!MCH_Config.DefaultExplosionParticle.prmBool) {
                    MCH_Explosion.effectExplosion((World)player.field_70170_p, exploder, (double)pkt.prm.posX, (double)pkt.prm.posY, (double)pkt.prm.posZ, (float)pkt.prm.size, (boolean)true);
                } else {
                    MCH_Explosion.DEF_effectExplosion((World)player.field_70170_p, exploder, (double)pkt.prm.posX, (double)pkt.prm.posY, (double)pkt.prm.posZ, (float)pkt.prm.size, (boolean)true);
                }
            } else {
                MCH_Explosion.effectExplosionInWater((World)player.field_70170_p, exploder, (double)pkt.prm.posX, (double)pkt.prm.posY, (double)pkt.prm.posZ, (float)pkt.prm.size, (boolean)true);
            }
        }
    }

    public static void onPacketIndOpenScreen(EntityPlayer player, ByteArrayDataInput data) {
        if (player.field_70170_p.field_72995_K) {
            return;
        }
        MCH_PacketIndOpenScreen pkt = new MCH_PacketIndOpenScreen();
        pkt.readData(data);
        if (pkt.guiID == 3) {
            MCH_EntityAircraft ac = MCH_EntityAircraft.getAircraft_RiddenOrControl((Entity)player);
            if (ac != null) {
                ac.openInventory(player);
            }
        } else {
            player.openGui((Object)MCH_MOD.instance, pkt.guiID, player.field_70170_p, (int)player.field_70165_t, (int)player.field_70163_u, (int)player.field_70161_v);
        }
    }

    public static void onPacketNotifyServerSettings(EntityPlayer player, ByteArrayDataInput data) {
        if (!player.field_70170_p.field_72995_K) {
            return;
        }
        MCH_Lib.DbgLog((boolean)false, (String)("onPacketNotifyServerSettings:" + player), (Object[])new Object[0]);
        MCH_PacketNotifyServerSettings pkt = new MCH_PacketNotifyServerSettings();
        pkt.readData(data);
        if (!pkt.enableCamDistChange) {
            W_Reflection.setThirdPersonDistance((float)4.0f);
        }
        MCH_ServerSettings.enableCamDistChange = pkt.enableCamDistChange;
        MCH_ServerSettings.enableEntityMarker = pkt.enableEntityMarker;
        MCH_ServerSettings.enablePVP = pkt.enablePVP;
        MCH_ServerSettings.stingerLockRange = pkt.stingerLockRange;
        MCH_ServerSettings.enableDebugBoundingBox = pkt.enableDebugBoundingBox;
        MCH_ClientLightWeaponTickHandler.lockRange = MCH_ServerSettings.stingerLockRange;
    }

    public static void onPacketNotifyLock(EntityPlayer player, ByteArrayDataInput data) {
        MCH_PacketNotifyLock pkt = new MCH_PacketNotifyLock();
        pkt.readData(data);
        if (!player.field_70170_p.field_72995_K) {
            Entity target;
            if (pkt.entityID >= 0 && (target = player.field_70170_p.func_73045_a(pkt.entityID)) != null) {
                MCH_EntityAircraft ac = null;
                ac = target instanceof MCH_EntityAircraft ? (MCH_EntityAircraft)target : (target instanceof MCH_EntitySeat ? ((MCH_EntitySeat)target).getParent() : MCH_EntityAircraft.getAircraft_RiddenOrControl((Entity)target));
                if (ac != null && ac.haveFlare() && !ac.isDestroyed()) {
                    for (int i = 0; i < 2; ++i) {
                        Entity entity = ac.getEntityBySeatId(i);
                        if (!(entity instanceof EntityPlayerMP)) continue;
                        MCH_PacketNotifyLock.sendToPlayer((EntityPlayer)((EntityPlayerMP)entity));
                    }
                }
            }
        } else {
            MCH_MOD.proxy.clientLocked();
        }
    }
}

