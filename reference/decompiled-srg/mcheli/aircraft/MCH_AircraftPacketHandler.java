/*
 * Decompiled with CFR 0.152.
 */
package mcheli.aircraft;

import com.google.common.io.ByteArrayDataInput;
import java.util.List;
import java.util.UUID;
import mcheli.MCH_Lib;
import mcheli.MCH_MOD;
import mcheli.aircraft.MCH_EntityAircraft;
import mcheli.aircraft.MCH_EntitySeat;
import mcheli.aircraft.MCH_PacketIndNotifyAmmoNum;
import mcheli.aircraft.MCH_PacketIndReload;
import mcheli.aircraft.MCH_PacketIndRotation;
import mcheli.aircraft.MCH_PacketNotifyAmmoNum;
import mcheli.aircraft.MCH_PacketNotifyClientSetting;
import mcheli.aircraft.MCH_PacketNotifyHitBullet;
import mcheli.aircraft.MCH_PacketNotifyInfoReloaded;
import mcheli.aircraft.MCH_PacketNotifyOnMountEntity;
import mcheli.aircraft.MCH_PacketNotifyTVMissileEntity;
import mcheli.aircraft.MCH_PacketNotifyWeaponID;
import mcheli.aircraft.MCH_PacketSeatListRequest;
import mcheli.aircraft.MCH_PacketSeatListResponse;
import mcheli.aircraft.MCH_PacketSeatPlayerControl;
import mcheli.aircraft.MCH_PacketStatusRequest;
import mcheli.aircraft.MCH_PacketStatusResponse;
import mcheli.weapon.MCH_EntityTvMissile;
import mcheli.weapon.MCH_WeaponInfoManager;
import mcheli.wrapper.W_Entity;
import mcheli.wrapper.W_Lib;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

public class MCH_AircraftPacketHandler {
    public static void onPacketIndRotation(EntityPlayer player, ByteArrayDataInput data) {
        if (player == null || player.field_70170_p.field_72995_K) {
            return;
        }
        MCH_PacketIndRotation req = new MCH_PacketIndRotation();
        req.readData(data);
        if (req.entityID_Ac <= 0) {
            return;
        }
        Entity e = player.field_70170_p.func_73045_a(req.entityID_Ac);
        if (e instanceof MCH_EntityAircraft) {
            MCH_EntityAircraft ac = (MCH_EntityAircraft)e;
            ac.setRotRoll(req.roll);
            if (req.rollRev) {
                MCH_Lib.DbgLog((World)ac.field_70170_p, (String)"onPacketIndRotation Error:req.rollRev y=%.2f, p=%.2f, r=%.2f", (Object[])new Object[]{Float.valueOf(req.yaw), Float.valueOf(req.pitch), Float.valueOf(req.roll)});
                if (ac.getRiddenByEntity() != null) {
                    ac.getRiddenByEntity().field_70177_z = req.yaw;
                    ac.getRiddenByEntity().field_70126_B = req.yaw;
                }
                for (int sid = 0; sid < ac.getSeatNum(); ++sid) {
                    Entity entity = ac.getEntityBySeatId(1 + sid);
                    if (entity == null) continue;
                    entity.field_70177_z = entity.field_70177_z + (entity.field_70177_z <= 0.0f ? 180.0f : -180.0f);
                }
            }
            ac.setRotYaw(req.yaw);
            ac.setRotPitch(req.pitch);
        }
    }

    public static void onPacketOnMountEntity(EntityPlayer player, ByteArrayDataInput data) {
        if (player == null || !player.field_70170_p.field_72995_K) {
            return;
        }
        MCH_PacketNotifyOnMountEntity req = new MCH_PacketNotifyOnMountEntity();
        req.readData(data);
        MCH_Lib.DbgLog((World)player.field_70170_p, (String)"onPacketOnMountEntity.rcv:%d, %d, %d, %d", (Object[])new Object[]{W_Entity.getEntityId((Entity)player), req.entityID_Ac, req.entityID_rider, req.seatID});
        if (req.entityID_Ac <= 0) {
            return;
        }
        if (req.entityID_rider <= 0) {
            return;
        }
        if (req.seatID < 0) {
            return;
        }
        Entity e = player.field_70170_p.func_73045_a(req.entityID_Ac);
        if (e instanceof MCH_EntityAircraft) {
            MCH_Lib.DbgLog((World)player.field_70170_p, (String)("onPacketOnMountEntity:" + W_Entity.getEntityId((Entity)player)), (Object[])new Object[0]);
            Entity rider = player.field_70170_p.func_73045_a(req.entityID_rider);
            MCH_EntityAircraft ac = (MCH_EntityAircraft)e;
        }
    }

    public static void onPacketNotifyAmmoNum(EntityPlayer player, ByteArrayDataInput data) {
        if (player == null || !player.field_70170_p.field_72995_K) {
            return;
        }
        MCH_PacketNotifyAmmoNum status = new MCH_PacketNotifyAmmoNum();
        status.readData(data);
        if (status.entityID_Ac <= 0) {
            return;
        }
        Entity e = player.field_70170_p.func_73045_a(status.entityID_Ac);
        if (e instanceof MCH_EntityAircraft) {
            MCH_EntityAircraft ac = (MCH_EntityAircraft)e;
            String msg = "onPacketNotifyAmmoNum:";
            msg = msg + (ac.getAcInfo() != null ? ac.getAcInfo().displayName : "null") + ":";
            if (status.all) {
                msg = msg + "All=true, Num=" + status.num;
                for (int i = 0; i < ac.getWeaponNum() && i < status.num; ++i) {
                    ac.getWeapon(i).setAmmoNum((int)status.ammo[i]);
                    ac.getWeapon(i).setRestAllAmmoNum((int)status.restAmmo[i]);
                    msg = msg + ", [" + status.ammo[i] + "/" + status.restAmmo[i] + "]";
                }
                MCH_Lib.DbgLog((World)e.field_70170_p, (String)msg, (Object[])new Object[0]);
            } else if (status.weaponID < ac.getWeaponNum()) {
                msg = msg + "All=false, WeaponID=" + status.weaponID + ", " + status.ammo[0] + ", " + status.restAmmo[0];
                ac.getWeapon((int)status.weaponID).setAmmoNum((int)status.ammo[0]);
                ac.getWeapon((int)status.weaponID).setRestAllAmmoNum((int)status.restAmmo[0]);
                MCH_Lib.DbgLog((World)e.field_70170_p, (String)msg, (Object[])new Object[0]);
            } else {
                MCH_Lib.DbgLog((World)e.field_70170_p, (String)("Error:" + status.weaponID), (Object[])new Object[0]);
            }
        }
    }

    public static void onPacketStatusRequest(EntityPlayer player, ByteArrayDataInput data) {
        if (player.field_70170_p.field_72995_K) {
            return;
        }
        MCH_PacketStatusRequest req = new MCH_PacketStatusRequest();
        req.readData(data);
        if (req.entityID_AC <= 0) {
            return;
        }
        Entity e = player.field_70170_p.func_73045_a(req.entityID_AC);
        if (e instanceof MCH_EntityAircraft) {
            MCH_PacketStatusResponse.sendStatus((MCH_EntityAircraft)((MCH_EntityAircraft)e), (EntityPlayer)player);
        }
    }

    public static void onPacketIndNotifyAmmoNum(EntityPlayer player, ByteArrayDataInput data) {
        if (player.field_70170_p.field_72995_K) {
            return;
        }
        MCH_PacketIndNotifyAmmoNum req = new MCH_PacketIndNotifyAmmoNum();
        req.readData(data);
        if (req.entityID_Ac <= 0) {
            return;
        }
        Entity e = player.field_70170_p.func_73045_a(req.entityID_Ac);
        if (e instanceof MCH_EntityAircraft) {
            if (req.weaponID >= 0) {
                MCH_PacketNotifyAmmoNum.sendAmmoNum((MCH_EntityAircraft)((MCH_EntityAircraft)e), (EntityPlayer)player, (int)req.weaponID);
            } else {
                MCH_PacketNotifyAmmoNum.sendAllAmmoNum((MCH_EntityAircraft)((MCH_EntityAircraft)e), (EntityPlayer)player);
            }
        }
    }

    public static void onPacketIndReload(EntityPlayer player, ByteArrayDataInput data) {
        if (player.field_70170_p.field_72995_K) {
            return;
        }
        MCH_PacketIndReload ind = new MCH_PacketIndReload();
        ind.readData(data);
        if (ind.entityID_Ac <= 0) {
            return;
        }
        Entity e = player.field_70170_p.func_73045_a(ind.entityID_Ac);
        if (e instanceof MCH_EntityAircraft) {
            MCH_EntityAircraft ac = (MCH_EntityAircraft)e;
            MCH_Lib.DbgLog((World)e.field_70170_p, (String)"onPacketIndReload :%s", (Object[])new Object[]{ac.getAcInfo().displayName});
            ac.supplyAmmo(ind.weaponID);
        }
    }

    public static void onPacketStatusResponse(EntityPlayer player, ByteArrayDataInput data) {
        if (!player.field_70170_p.field_72995_K) {
            return;
        }
        MCH_PacketStatusResponse status = new MCH_PacketStatusResponse();
        status.readData(data);
        String msg = "onPacketStatusResponse:";
        if (status.entityID_AC <= 0) {
            return;
        }
        msg = msg + "EID=" + status.entityID_AC + ":";
        Entity e = player.field_70170_p.func_73045_a(status.entityID_AC);
        if (e instanceof MCH_EntityAircraft) {
            MCH_EntityAircraft ac = (MCH_EntityAircraft)e;
            if (status.seatNum > 0 && status.weaponIDs != null && status.weaponIDs.length == status.seatNum) {
                msg = msg + "seatNum=" + status.seatNum + ":";
                for (int i = 0; i < status.seatNum; ++i) {
                    ac.updateWeaponID(i, (int)status.weaponIDs[i]);
                    msg = msg + "[" + i + "," + status.weaponIDs[i] + "]";
                }
            } else {
                msg = msg + "Error seatNum=" + status.seatNum;
            }
        }
        MCH_Lib.DbgLog((boolean)true, (String)msg, (Object[])new Object[0]);
    }

    public static void onPacketNotifyWeaponID(EntityPlayer player, ByteArrayDataInput data) {
        MCH_EntityAircraft ac;
        if (!player.field_70170_p.field_72995_K) {
            return;
        }
        MCH_PacketNotifyWeaponID status = new MCH_PacketNotifyWeaponID();
        status.readData(data);
        if (status.entityID_Ac <= 0) {
            return;
        }
        Entity e = player.field_70170_p.func_73045_a(status.entityID_Ac);
        if (e instanceof MCH_EntityAircraft && (ac = (MCH_EntityAircraft)e).isValidSeatID(status.seatID)) {
            ac.getWeapon(status.weaponID).setAmmoNum((int)status.ammo);
            ac.getWeapon(status.weaponID).setRestAllAmmoNum((int)status.restAmmo);
            MCH_Lib.DbgLog((boolean)true, (String)"onPacketNotifyWeaponID:WeaponID=%d (%d / %d)", (Object[])new Object[]{status.weaponID, status.ammo, status.restAmmo});
            if (W_Lib.isClientPlayer((Entity)ac.getEntityBySeatId(status.seatID))) {
                MCH_Lib.DbgLog((boolean)true, (String)"onPacketNotifyWeaponID:#discard:SeatID=%d, WeaponID=%d", (Object[])new Object[]{status.seatID, status.weaponID});
            } else {
                MCH_Lib.DbgLog((boolean)true, (String)"onPacketNotifyWeaponID:SeatID=%d, WeaponID=%d", (Object[])new Object[]{status.seatID, status.weaponID});
                ac.updateWeaponID(status.seatID, status.weaponID);
            }
        }
    }

    public static void onPacketNotifyHitBullet(EntityPlayer player, ByteArrayDataInput data) {
        if (!player.field_70170_p.field_72995_K) {
            return;
        }
        MCH_PacketNotifyHitBullet status = new MCH_PacketNotifyHitBullet();
        status.readData(data);
        if (status.entityID_Ac <= 0) {
            MCH_MOD.proxy.hitBullet();
        } else {
            Entity e = player.field_70170_p.func_73045_a(status.entityID_Ac);
            if (e instanceof MCH_EntityAircraft) {
                ((MCH_EntityAircraft)e).hitBullet();
            }
        }
    }

    public static void onPacketSeatListRequest(EntityPlayer player, ByteArrayDataInput data) {
        if (player.field_70170_p.field_72995_K) {
            return;
        }
        MCH_PacketSeatListRequest req = new MCH_PacketSeatListRequest();
        req.readData(data);
        if (req.entityID_AC <= 0) {
            return;
        }
        Entity e = player.field_70170_p.func_73045_a(req.entityID_AC);
        if (e instanceof MCH_EntityAircraft) {
            MCH_PacketSeatListResponse.sendSeatList((MCH_EntityAircraft)((MCH_EntityAircraft)e), (EntityPlayer)player);
        }
    }

    public static void onPacketNotifyTVMissileEntity(EntityPlayer player, ByteArrayDataInput data) {
        if (player.field_70170_p.field_72995_K) {
            MCH_PacketNotifyTVMissileEntity packet = new MCH_PacketNotifyTVMissileEntity();
            packet.readData(data);
            if (packet.entityID_Ac <= 0) {
                return;
            }
            if (packet.entityID_TVMissile <= 0) {
                return;
            }
            Entity e = player.field_70170_p.func_73045_a(packet.entityID_Ac);
            if (e == null || !(e instanceof MCH_EntityAircraft)) {
                return;
            }
            MCH_EntityAircraft ac = (MCH_EntityAircraft)e;
            e = player.field_70170_p.func_73045_a(packet.entityID_TVMissile);
            if (e == null || !(e instanceof MCH_EntityTvMissile)) {
                return;
            }
            ((MCH_EntityTvMissile)e).shootingEntity = player;
            ac.setTVMissile((MCH_EntityTvMissile)e);
        }
    }

    public static void onPacketSeatListResponse(EntityPlayer player, ByteArrayDataInput data) {
        if (!player.field_70170_p.field_72995_K) {
            return;
        }
        MCH_PacketSeatListResponse seatList = new MCH_PacketSeatListResponse();
        seatList.readData(data);
        if (seatList.entityID_AC <= 0) {
            return;
        }
        Entity e = player.field_70170_p.func_73045_a(seatList.entityID_AC);
        if (e instanceof MCH_EntityAircraft) {
            MCH_EntityAircraft ac = (MCH_EntityAircraft)e;
            if (seatList.seatNum > 0 && seatList.seatNum == ac.getSeats().length && seatList.seatEntityID != null && seatList.seatEntityID.length == seatList.seatNum) {
                for (int i = 0; i < seatList.seatNum; ++i) {
                    Entity entity = player.field_70170_p.func_73045_a(seatList.seatEntityID[i]);
                    if (!(entity instanceof MCH_EntitySeat)) continue;
                    MCH_EntitySeat seat = (MCH_EntitySeat)entity;
                    seat.seatID = i;
                    seat.setParent(ac);
                    seat.parentUniqueID = ac.getCommonUniqueId();
                    ac.setSeat(i, seat);
                }
            }
        }
    }

    public static void onPacket_PlayerControl(EntityPlayer player, ByteArrayDataInput data) {
        if (player.field_70170_p.field_72995_K) {
            return;
        }
        MCH_EntityAircraft ac = null;
        if (player.field_70154_o instanceof MCH_EntitySeat) {
            MCH_EntitySeat seat = (MCH_EntitySeat)player.field_70154_o;
            ac = seat.getParent();
        } else {
            ac = MCH_EntityAircraft.getAircraft_RiddenOrControl((Entity)player);
        }
        if (ac == null) {
            return;
        }
        MCH_PacketSeatPlayerControl pc = new MCH_PacketSeatPlayerControl();
        pc.readData(data);
        if (pc.isUnmount) {
            ac.unmountEntityFromSeat((Entity)player);
        } else if (pc.switchSeat > 0) {
            if (pc.switchSeat == 3) {
                player.func_70078_a(null);
                ac.keepOnRideRotation = true;
                ac.interactFirst(player, true);
            }
            if (pc.switchSeat == 1) {
                ac.switchNextSeat((Entity)player);
            }
            if (pc.switchSeat == 2) {
                ac.switchPrevSeat((Entity)player);
            }
        } else if (pc.parachuting) {
            ac.unmount((Entity)player);
        }
    }

    public static void onPacket_ClientSetting(EntityPlayer player, ByteArrayDataInput data) {
        if (player.field_70170_p.field_72995_K) {
            return;
        }
        MCH_PacketNotifyClientSetting pc = new MCH_PacketNotifyClientSetting();
        pc.readData(data);
        MCH_EntityAircraft ac = MCH_EntityAircraft.getAircraft_RiddenOrControl((Entity)player);
        if (ac != null) {
            int sid = ac.getSeatIdByEntity((Entity)player);
            if (sid == 0) {
                ac.cs_dismountAll = pc.dismountAll;
                ac.cs_heliAutoThrottleDown = pc.heliAutoThrottleDown;
                ac.cs_planeAutoThrottleDown = pc.planeAutoThrottleDown;
                ac.cs_tankAutoThrottleDown = pc.tankAutoThrottleDown;
            }
            ac.camera.setShaderSupport(sid, Boolean.valueOf(pc.shaderSupport));
        }
    }

    public static void onPacketNotifyInfoReloaded(EntityPlayer player, ByteArrayDataInput data) {
        if (player.field_70170_p.field_72995_K) {
            return;
        }
        MCH_PacketNotifyInfoReloaded pc = new MCH_PacketNotifyInfoReloaded();
        pc.readData(data);
        switch (pc.type) {
            case 0: {
                MCH_EntityAircraft ac = MCH_EntityAircraft.getAircraft_RiddenOrControl((Entity)player);
                if (ac == null || ac.getAcInfo() == null) break;
                String name = ac.getAcInfo().name;
                for (WorldServer world : MinecraftServer.func_71276_C().field_71305_c) {
                    List list = world.field_72996_f;
                    for (int i = 0; i < list.size(); ++i) {
                        if (!(list.get(i) instanceof MCH_EntityAircraft) || (ac = (MCH_EntityAircraft)list.get(i)).getAcInfo() == null || !ac.getAcInfo().name.equals(name)) continue;
                        ac.changeType(name);
                        ac.createSeats(UUID.randomUUID().toString());
                        ac.onAcInfoReloaded();
                    }
                }
                break;
            }
            case 1: {
                MCH_WeaponInfoManager.reload();
                for (WorldServer world : MinecraftServer.func_71276_C().field_71305_c) {
                    List list = world.field_72996_f;
                    for (int i = 0; i < list.size(); ++i) {
                        MCH_EntityAircraft ac;
                        if (!(list.get(i) instanceof MCH_EntityAircraft) || (ac = (MCH_EntityAircraft)list.get(i)).getAcInfo() == null) continue;
                        ac.changeType(ac.getAcInfo().name);
                        ac.createSeats(UUID.randomUUID().toString());
                    }
                }
                break;
            }
        }
    }
}

