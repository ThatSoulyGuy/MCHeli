package mcheli.agnostic.spi;

/**
 * Entity classification, replacing the reference's {@code instanceof MCH_EntityHeli/Plane/...} and
 * {@code EntityLivingBase/EntityMob/EntityPlayer} checks. The dependent adapter tags each entity;
 * the agnostic layer branches on {@code role()} and NEVER references a {@code Class}.
 *
 * <p>Every constant has a live counterpart in the port's {@code NeoEntityRef.role()} mapping. Two reference
 * categories are deliberately absent:
 * <ul>
 *   <li><b>seat</b> ({@code MCH_EntitySeat}) — the port seats riders as vanilla passengers, so no seat entity exists;</li>
 *   <li><b>UAV</b> — a UAV aircraft is an {@code MCH_EntityHeli}/{@code MCP_EntityPlane} with {@code isUAV} set in its
 *       config (so it is {@link #HELICOPTER}/{@link #PLANE} here, and a weapon's {@code DamageFactor = plane} still
 *       applies to it, as in the reference); the UAV <em>station</em> entity is not ported. Add a station role
 *       together with that entity.</li>
 * </ul>
 */
public enum Role {
    HELICOPTER, PLANE, TANK, VEHICLE,   // mcheli rideable vehicles
    HITBOX,                             // a multipart sub-hitbox entity (reference MCH_EntityHitBox; 1.21 PartEntity)
    PROJECTILE,                         // an mcheli round (MchBullet) or any vanilla Projectile
    PLAYER, LIVING, MOB, OTHER;         // vanilla categories (MOB = reference EntityMob / 1.21 Monster)

    public boolean isMchVehicle() { return this == HELICOPTER || this == PLANE || this == TANK || this == VEHICLE; }
    public boolean isAircraft()   { return this == HELICOPTER || this == PLANE; }
    public boolean isLivingLike() { return this == PLAYER || this == LIVING || this == MOB; }
}
