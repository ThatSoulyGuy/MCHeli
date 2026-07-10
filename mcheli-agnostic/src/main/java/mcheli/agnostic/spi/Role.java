package mcheli.agnostic.spi;

/**
 * Entity classification, replacing the reference's {@code instanceof MCH_EntityHeli/Plane/...} and
 * {@code EntityLivingBase/EntityMob/EntityPlayer} checks. The dependent adapter tags each entity;
 * the agnostic layer branches on {@code role()} and NEVER references a {@code Class}.
 */
public enum Role {
    HELICOPTER, PLANE, TANK, VEHICLE, UAV,   // mcheli rideable vehicles
    SEAT, HITBOX, PROJECTILE,                 // mcheli support entities
    PLAYER, LIVING, MOB, OTHER;               // vanilla categories

    public boolean isMchVehicle() { return this == HELICOPTER || this == PLANE || this == TANK || this == VEHICLE || this == UAV; }
    public boolean isAircraft()   { return this == HELICOPTER || this == PLANE; }
    public boolean isLivingLike() { return this == PLAYER || this == LIVING || this == MOB; }
}
