/*
 * Decompiled with CFR 0.152.
 */
package mcheli;

import com.google.common.io.ByteArrayDataInput;
import mcheli.MCH_CommonPacketHandler;
import mcheli.MCH_Lib;
import mcheli.aircraft.MCH_AircraftPacketHandler;
import mcheli.block.MCH_DraftingTablePacketHandler;
import mcheli.command.MCH_CommandPacketHandler;
import mcheli.gltd.MCH_GLTDPacketHandler;
import mcheli.helicopter.MCH_HeliPacketHandler;
import mcheli.lweapon.MCH_LightWeaponPacketHandler;
import mcheli.multiplay.MCH_MultiplayPacketHandler;
import mcheli.plane.MCP_PlanePacketHandler;
import mcheli.tank.MCH_TankPacketHandler;
import mcheli.tool.MCH_ToolPacketHandler;
import mcheli.uav.MCH_UavPacketHandler;
import mcheli.vehicle.MCH_VehiclePacketHandler;
import mcheli.wrapper.W_PacketHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

public class MCH_PacketHandler
extends W_PacketHandler {
    public void onPacket(ByteArrayDataInput data, EntityPlayer entityPlayer) {
        int msgid = this.getMessageId(data);
        switch (msgid) {
            default: {
                MCH_Lib.DbgLog((World)entityPlayer.field_70170_p, (String)"MCH_PacketHandler.onPacket invalid MSGID=0x%X(%d)", (Object[])new Object[]{msgid, msgid});
                break;
            }
            case 0x10000810: {
                MCH_CommonPacketHandler.onPacketEffectExplosion((EntityPlayer)entityPlayer, (ByteArrayDataInput)data);
                break;
            }
            case 0x20000820: {
                MCH_CommonPacketHandler.onPacketIndOpenScreen((EntityPlayer)entityPlayer, (ByteArrayDataInput)data);
                break;
            }
            case 268437568: {
                MCH_CommonPacketHandler.onPacketNotifyServerSettings((EntityPlayer)entityPlayer, (ByteArrayDataInput)data);
                break;
            }
            case 0x20000C00: {
                MCH_CommonPacketHandler.onPacketNotifyLock((EntityPlayer)entityPlayer, (ByteArrayDataInput)data);
                break;
            }
            case 0x20000880: {
                MCH_MultiplayPacketHandler.onPacket_Command((EntityPlayer)entityPlayer, (ByteArrayDataInput)data);
                break;
            }
            case 0x10000901: {
                MCH_MultiplayPacketHandler.onPacket_NotifySpotedEntity((EntityPlayer)entityPlayer, (ByteArrayDataInput)data);
                break;
            }
            case 268437762: {
                MCH_MultiplayPacketHandler.onPacket_NotifyMarkPoint((EntityPlayer)entityPlayer, (ByteArrayDataInput)data);
                break;
            }
            case 0x20000A00: {
                MCH_MultiplayPacketHandler.onPacket_LargeData((EntityPlayer)entityPlayer, (ByteArrayDataInput)data);
                break;
            }
            case 536873473: {
                MCH_MultiplayPacketHandler.onPacket_ModList((EntityPlayer)entityPlayer, (ByteArrayDataInput)data);
                break;
            }
            case 0x10000A10: {
                MCH_MultiplayPacketHandler.onPacket_IndClient((EntityPlayer)entityPlayer, (ByteArrayDataInput)data);
                break;
            }
            case 0x10000B00: {
                MCH_CommandPacketHandler.onPacketTitle((EntityPlayer)entityPlayer, (ByteArrayDataInput)data);
                break;
            }
            case 536873729: {
                MCH_CommandPacketHandler.onPacketSave((EntityPlayer)entityPlayer, (ByteArrayDataInput)data);
                break;
            }
            case 0x20000900: {
                MCH_ToolPacketHandler.onPacket_IndSpotEntity((EntityPlayer)entityPlayer, (ByteArrayDataInput)data);
                break;
            }
            case 0x20002010: {
                MCH_HeliPacketHandler.onPacket_PlayerControl((EntityPlayer)entityPlayer, (ByteArrayDataInput)data);
                break;
            }
            case 536875104: {
                MCH_AircraftPacketHandler.onPacketStatusRequest((EntityPlayer)entityPlayer, (ByteArrayDataInput)data);
                break;
            }
            case 0x10001061: {
                MCH_AircraftPacketHandler.onPacketStatusResponse((EntityPlayer)entityPlayer, (ByteArrayDataInput)data);
                break;
            }
            case 0x20001010: {
                MCH_AircraftPacketHandler.onPacketSeatListRequest((EntityPlayer)entityPlayer, (ByteArrayDataInput)data);
                break;
            }
            case 0x10001011: {
                MCH_AircraftPacketHandler.onPacketSeatListResponse((EntityPlayer)entityPlayer, (ByteArrayDataInput)data);
                break;
            }
            case 0x20001020: {
                MCH_AircraftPacketHandler.onPacket_PlayerControl((EntityPlayer)entityPlayer, (ByteArrayDataInput)data);
                break;
            }
            case 0x10001030: {
                MCH_AircraftPacketHandler.onPacketNotifyTVMissileEntity((EntityPlayer)entityPlayer, (ByteArrayDataInput)data);
                break;
            }
            case 536875072: {
                MCH_AircraftPacketHandler.onPacket_ClientSetting((EntityPlayer)entityPlayer, (ByteArrayDataInput)data);
                break;
            }
            case 0x10001050: {
                MCH_AircraftPacketHandler.onPacketOnMountEntity((EntityPlayer)entityPlayer, (ByteArrayDataInput)data);
                break;
            }
            case 0x10001031: {
                MCH_AircraftPacketHandler.onPacketNotifyWeaponID((EntityPlayer)entityPlayer, (ByteArrayDataInput)data);
                break;
            }
            case 268439602: {
                MCH_AircraftPacketHandler.onPacketNotifyHitBullet((EntityPlayer)entityPlayer, (ByteArrayDataInput)data);
                break;
            }
            case 536875059: {
                MCH_AircraftPacketHandler.onPacketIndReload((EntityPlayer)entityPlayer, (ByteArrayDataInput)data);
                break;
            }
            case 536875062: {
                MCH_AircraftPacketHandler.onPacketIndRotation((EntityPlayer)entityPlayer, (ByteArrayDataInput)data);
                break;
            }
            case 536875063: {
                MCH_AircraftPacketHandler.onPacketNotifyInfoReloaded((EntityPlayer)entityPlayer, (ByteArrayDataInput)data);
                break;
            }
            case 268439604: {
                MCH_AircraftPacketHandler.onPacketNotifyAmmoNum((EntityPlayer)entityPlayer, (ByteArrayDataInput)data);
                break;
            }
            case 536875061: {
                MCH_AircraftPacketHandler.onPacketIndNotifyAmmoNum((EntityPlayer)entityPlayer, (ByteArrayDataInput)data);
                break;
            }
            case 536887312: {
                MCH_GLTDPacketHandler.onPacket_GLTDPlayerControl((EntityPlayer)entityPlayer, (ByteArrayDataInput)data);
                break;
            }
            case 536903696: {
                MCP_PlanePacketHandler.onPacket_PlayerControl((EntityPlayer)entityPlayer, (ByteArrayDataInput)data);
                break;
            }
            case 0x20100010: {
                MCH_TankPacketHandler.onPacket_PlayerControl((EntityPlayer)entityPlayer, (ByteArrayDataInput)data);
                break;
            }
            case 0x20010010: {
                MCH_LightWeaponPacketHandler.onPacket_PlayerControl((EntityPlayer)entityPlayer, (ByteArrayDataInput)data);
                break;
            }
            case 0x20020010: {
                MCH_VehiclePacketHandler.onPacket_PlayerControl((EntityPlayer)entityPlayer, (ByteArrayDataInput)data);
                break;
            }
            case 537133072: {
                MCH_UavPacketHandler.onPacketUavStatus((EntityPlayer)entityPlayer, (ByteArrayDataInput)data);
                break;
            }
            case 537395216: {
                MCH_DraftingTablePacketHandler.onPacketCreate((EntityPlayer)entityPlayer, (ByteArrayDataInput)data);
            }
        }
    }

    protected int getMessageId(ByteArrayDataInput data) {
        try {
            return data.readInt();
        }
        catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }
}

