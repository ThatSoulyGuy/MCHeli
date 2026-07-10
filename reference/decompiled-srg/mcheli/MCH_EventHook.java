/*
 * Decompiled with CFR 0.152.
 */
package mcheli;

import java.util.List;
import java.util.UUID;
import mcheli.MCH_Config;
import mcheli.MCH_Lib;
import mcheli.MCH_PacketNotifyServerSettings;
import mcheli.aircraft.MCH_EntityAircraft;
import mcheli.aircraft.MCH_EntitySeat;
import mcheli.aircraft.MCH_ItemAircraft;
import mcheli.chain.MCH_ItemChain;
import mcheli.command.MCH_Command;
import mcheli.weapon.MCH_EntityBaseBullet;
import mcheli.wrapper.W_Entity;
import mcheli.wrapper.W_EntityPlayer;
import mcheli.wrapper.W_EventHook;
import mcheli.wrapper.W_Lib;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.EntityInteractEvent;

public class MCH_EventHook
extends W_EventHook {
    public void commandEvent(CommandEvent event) {
        MCH_Command.onCommandEvent((CommandEvent)event);
    }

    public void entitySpawn(EntityJoinWorldEvent event) {
        if (W_Lib.isEntityLivingBase((Entity)event.entity) && !W_EntityPlayer.isPlayer((Entity)event.entity)) {
            event.entity.field_70155_l *= MCH_Config.MobRenderDistanceWeight.prmDouble;
        } else if (event.entity instanceof MCH_EntityAircraft) {
            MCH_EntityAircraft aircraft = (MCH_EntityAircraft)event.entity;
            if (!aircraft.field_70170_p.field_72995_K && !aircraft.isCreatedSeats()) {
                aircraft.createSeats(UUID.randomUUID().toString());
            }
        } else if (W_EntityPlayer.isPlayer((Entity)event.entity)) {
            Entity e = event.entity;
            boolean b = Float.isNaN(e.field_70125_A);
            b |= Float.isNaN(e.field_70127_C);
            b |= Float.isInfinite(e.field_70125_A);
            if (b |= Float.isInfinite(e.field_70127_C)) {
                MCH_Lib.Log((Entity)event.entity, (String)("### EntityJoinWorldEvent Error:Player invalid rotation pitch(" + e.field_70125_A + ")"), (Object[])new Object[0]);
                e.field_70125_A = 0.0f;
                e.field_70127_C = 0.0f;
            }
            b = Float.isInfinite(e.field_70177_z);
            b |= Float.isInfinite(e.field_70126_B);
            b |= Float.isNaN(e.field_70177_z);
            if (b |= Float.isNaN(e.field_70126_B)) {
                MCH_Lib.Log((Entity)event.entity, (String)("### EntityJoinWorldEvent Error:Player invalid rotation yaw(" + e.field_70177_z + ")"), (Object[])new Object[0]);
                e.field_70177_z = 0.0f;
                e.field_70126_B = 0.0f;
            }
            if (!e.field_70170_p.field_72995_K && event.entity instanceof EntityPlayerMP) {
                MCH_Lib.DbgLog((boolean)false, (String)("EntityJoinWorldEvent:" + event.entity), (Object[])new Object[0]);
                MCH_PacketNotifyServerSettings.send((EntityPlayerMP)((EntityPlayerMP)event.entity));
            }
        }
    }

    public void livingAttackEvent(LivingAttackEvent event) {
        MCH_EntityAircraft ac = this.getRiddenAircraft(event.entity);
        if (ac == null) {
            return;
        }
        if (ac.getAcInfo() == null) {
            return;
        }
        if (ac.isDestroyed()) {
            return;
        }
        if (ac.getAcInfo().damageFactor > 0.0f) {
            return;
        }
        Entity attackEntity = event.source.func_76346_g();
        if (attackEntity == null) {
            event.setCanceled(true);
        } else if (W_Entity.isEqual((Entity)attackEntity, (Entity)event.entity)) {
            event.setCanceled(true);
        } else if (ac.isMountedEntity(attackEntity)) {
            event.setCanceled(true);
        } else {
            MCH_EntityAircraft atkac = this.getRiddenAircraft(attackEntity);
            if (W_Entity.isEqual((Entity)atkac, (Entity)ac)) {
                event.setCanceled(true);
            }
        }
    }

    public void livingHurtEvent(LivingHurtEvent event) {
        MCH_EntityAircraft ac = this.getRiddenAircraft(event.entity);
        if (ac == null) {
            return;
        }
        if (ac.getAcInfo() == null) {
            return;
        }
        if (ac.isDestroyed()) {
            return;
        }
        Entity attackEntity = event.source.func_76346_g();
        if (attackEntity == null) {
            ac.func_70097_a(event.source, event.ammount * 2.0f);
            event.ammount *= ac.getAcInfo().damageFactor;
        } else if (W_Entity.isEqual((Entity)attackEntity, (Entity)event.entity)) {
            ac.func_70097_a(event.source, event.ammount * 2.0f);
            event.ammount *= ac.getAcInfo().damageFactor;
        } else if (ac.isMountedEntity(attackEntity)) {
            event.ammount = 0.0f;
            event.setCanceled(true);
        } else {
            MCH_EntityAircraft atkac = this.getRiddenAircraft(attackEntity);
            if (W_Entity.isEqual((Entity)atkac, (Entity)ac)) {
                event.ammount = 0.0f;
                event.setCanceled(true);
            } else {
                ac.func_70097_a(event.source, event.ammount * 2.0f);
                event.ammount *= ac.getAcInfo().damageFactor;
            }
        }
    }

    public MCH_EntityAircraft getRiddenAircraft(Entity entity) {
        List list;
        MCH_EntityAircraft ac = null;
        Entity ridden = entity.field_70154_o;
        if (ridden instanceof MCH_EntityAircraft) {
            ac = (MCH_EntityAircraft)ridden;
        } else if (ridden instanceof MCH_EntitySeat) {
            ac = ((MCH_EntitySeat)ridden).getParent();
        }
        if (ac == null && (list = entity.field_70170_p.func_72872_a(MCH_EntityAircraft.class, entity.field_70121_D.func_72314_b(50.0, 50.0, 50.0))) != null) {
            for (int i = 0; i < list.size(); ++i) {
                MCH_EntityAircraft tmp = (MCH_EntityAircraft)list.get(i);
                if (!tmp.isMountedEntity(entity)) continue;
                return tmp;
            }
        }
        return ac;
    }

    public void entityInteractEvent(EntityInteractEvent event) {
        ItemStack item = event.entityPlayer.func_70694_bm();
        if (item == null) {
            return;
        }
        if (item.func_77973_b() instanceof MCH_ItemChain) {
            MCH_ItemChain.interactEntity((ItemStack)item, (Entity)event.target, (EntityPlayer)event.entityPlayer, (World)event.entityPlayer.field_70170_p);
            event.setCanceled(true);
        } else if (item.func_77973_b() instanceof MCH_ItemAircraft) {
            ((MCH_ItemAircraft)item.func_77973_b()).rideEntity(item, event.target, event.entityPlayer);
        }
    }

    public void entityCanUpdate(EntityEvent.CanUpdate event) {
        if (event.entity instanceof MCH_EntityBaseBullet) {
            MCH_EntityBaseBullet bullet = (MCH_EntityBaseBullet)event.entity;
            bullet.func_70106_y();
        }
    }
}

