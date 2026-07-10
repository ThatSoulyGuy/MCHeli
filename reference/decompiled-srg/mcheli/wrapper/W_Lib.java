/*
 * Decompiled with CFR 0.152.
 */
package mcheli.wrapper;

import mcheli.MCH_MOD;
import mcheli.wrapper.W_Entity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

public class W_Lib {
    public static boolean isEntityLivingBase(Entity entity) {
        return entity instanceof EntityLivingBase;
    }

    public static EntityLivingBase castEntityLivingBase(Object entity) {
        return (EntityLivingBase)entity;
    }

    public static Class getEntityLivingBaseClass() {
        return EntityLivingBase.class;
    }

    public static double getEntityMoveDist(Entity entity) {
        if (entity == null) {
            return 0.0;
        }
        return entity instanceof EntityLivingBase ? (double)((EntityLivingBase)entity).field_70701_bs : 0.0;
    }

    public static boolean isClientPlayer(Entity entity) {
        if (entity instanceof EntityPlayer && entity.field_70170_p.field_72995_K) {
            return W_Entity.isEqual((Entity)MCH_MOD.proxy.getClientPlayer(), (Entity)entity);
        }
        return false;
    }

    public static boolean isFirstPerson() {
        return MCH_MOD.proxy.isFirstPerson();
    }
}

