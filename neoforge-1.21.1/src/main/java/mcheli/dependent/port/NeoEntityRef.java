package mcheli.dependent.port;

import java.util.ArrayList;
import java.util.List;
import mcheli.agnostic.spi.EntityRef;
import mcheli.agnostic.spi.Role;
import mcheli.agnostic.spi.WorldView;
import mcheli.agnostic.value.AABB;
import mcheli.agnostic.value.MoveResult;
import mcheli.agnostic.value.Vec3d;
import mcheli.dependent.entity.MchBullet;
import mcheli.dependent.entity.MchHelicopter;
import mcheli.dependent.entity.MchPlane;
import mcheli.dependent.entity.MchTank;
import mcheli.dependent.entity.MchGroundVehicle;
import mcheli.dependent.entity.RollHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;

/**
 * The concrete {@link EntityRef} port: wraps a real Minecraft {@link Entity} and translates the agnostic
 * layer's kinematic reads/writes to it. This is the ONLY place the agnostic vehicle logic touches a
 * platform type; classification is mapped to {@link Role} here (the {@code instanceof} lives at the port
 * boundary, never in agnostic code).
 *
 * <p>Every {@link EntityRef} method is implemented. {@link #world()} hands out a {@link NeoWorldView} over the
 * entity's level, which the agnostic flight sims ({@code AircraftFlightController}, {@code GroundProbe},
 * {@code Buoyancy}, ...) use for their block/water probes. Synced state stays in each entity's own
 * {@code EntityDataAccessor}s; the agnostic layer never reads a synced slot.
 */
public final class NeoEntityRef implements EntityRef {
    private final Entity e;

    public NeoEntityRef(Entity e) { this.e = e; }

    /** The wrapped Minecraft entity (dependent-side only). */
    public Entity handle() { return e; }

    @Override public int id() { return e.getId(); }
    @Override public boolean isSameAs(EntityRef other) { return other instanceof NeoEntityRef n && n.e == this.e; }
    @Override public boolean isDead() { return e.isRemoved(); }
    @Override public boolean isRemote() { return e.level().isClientSide; }

    /**
     * Classification by entity class, like the reference's {@code getClass()}-keyed lookups: a UAV config is still
     * a helicopter/plane entity (see {@link Role}). {@link Monster} is the 1.21 counterpart of the reference's
     * {@code EntityMob} (the {@code MCH_Radar} hostile test: zombies, skeletons, creepers, the wither, ...; slimes and
     * ghasts were not {@code EntityMob} there either). A {@link PartEntity} fills the reference
     * {@code MCH_EntityHitBox}'s place: a sub-hitbox whose damage belongs to a parent entity.
     */
    @Override public Role role() {
        if (e instanceof Player) return Role.PLAYER;
        if (e instanceof MchHelicopter) return Role.HELICOPTER;
        if (e instanceof MchPlane) return Role.PLANE;
        if (e instanceof MchTank) return Role.TANK;
        if (e instanceof MchGroundVehicle) return Role.VEHICLE;
        if (e instanceof MchBullet || e instanceof Projectile) return Role.PROJECTILE;
        if (e instanceof PartEntity<?>) return Role.HITBOX;
        if (e instanceof Monster) return Role.MOB;
        if (e instanceof LivingEntity) return Role.LIVING;
        return Role.OTHER;
    }

    // kinematics — reads
    @Override public Vec3d position()     { return new Vec3d(e.getX(), e.getY(), e.getZ()); }
    @Override public Vec3d prevPosition() { return new Vec3d(e.xo, e.yo, e.zo); }
    @Override public Vec3d motion()       { Vec3 m = e.getDeltaMovement(); return new Vec3d(m.x, m.y, m.z); }
    @Override public float yaw()          { return e.getYRot(); }
    @Override public float pitch()        { return e.getXRot(); }
    @Override public float roll()         { return e instanceof RollHolder r ? r.getRollAngle() : 0.0F; }
    @Override public float prevYaw()      { return e.yRotO; }
    @Override public float prevPitch()    { return e.xRotO; }
    @Override public float prevRoll()     { return e instanceof RollHolder r ? r.getPrevRollAngle() : 0.0F; }
    @Override public float width()        { return e.getBbWidth(); }
    @Override public float height()       { return e.getBbHeight(); }
    @Override public AABB boundingBox() {
        net.minecraft.world.phys.AABB b = e.getBoundingBox();
        return new AABB(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ);
    }
    @Override public boolean onGround()   { return e.onGround(); }
    @Override public boolean isInWater()  { return e.isInWater(); }
    @Override public int ageTicks()       { return e.tickCount; }

    // kinematics — writes
    @Override public void setPosition(Vec3d p) { e.setPos(p.x(), p.y(), p.z()); }
    @Override public void setMotion(Vec3d m)   { e.setDeltaMovement(new Vec3(m.x(), m.y(), m.z())); }
    @Override public void addMotion(Vec3d d)   { e.setDeltaMovement(e.getDeltaMovement().add(d.x(), d.y(), d.z())); }
    @Override public void setRotation(float yaw, float pitch) { e.setYRot(yaw); e.setXRot(pitch); }
    @Override public void setRoll(float roll)  { if (e instanceof RollHolder r) r.setRollAngle(roll); }
    @Override public MoveResult move(Vec3d d) {
        double ox = e.getX(), oy = e.getY(), oz = e.getZ();
        e.move(MoverType.SELF, new Vec3(d.x(), d.y(), d.z()));
        Vec3d actual = new Vec3d(e.getX() - ox, e.getY() - oy, e.getZ() - oz);
        // An axis is "blocked" when the move did not cover the requested displacement — the reference moveEntity rule
        // (`if (requested != moved) motion = 0`, compared against its own pre-collision value). The non-collision
        // shortenings vanilla 1.21 applies match it: the stuck multiplier of cobweb/berry bush/powder snow also zeroes
        // the entity's delta movement inside move() (as 1.7.10 isInWeb zeroed motion), and a step-up's changed Y zeroed
        // motionY in the reference too. maybeBackOffFromEdge only affects sneaking players.
        boolean blockedX = Math.abs(actual.x() - d.x()) > 1.0e-9;
        boolean blockedY = Math.abs(actual.y() - d.y()) > 1.0e-9;
        boolean blockedZ = Math.abs(actual.z() - d.z()) > 1.0e-9;
        return new MoveResult(actual, e.onGround(), e.horizontalCollision, e.verticalCollision, blockedX, blockedY, blockedZ);
    }

    // mount / riders
    @Override public EntityRef vehicle() {
        Entity v = e.getVehicle();
        return v == null ? null : new NeoEntityRef(v);
    }
    @Override public List<EntityRef> passengers() {
        List<EntityRef> out = new ArrayList<>();
        for (Entity p : e.getPassengers()) out.add(new NeoEntityRef(p));
        return out;
    }

    // sub-surface
    private NeoWorldView world; // cached: the ground probes ask for it every tick; rebuilt if the entity changes level

    @Override public WorldView world() {
        NeoWorldView w = this.world;
        if (w == null || w.handle() != e.level()) {
            w = this.world = new NeoWorldView(e.level());  // thin wrapper over the entity's Level (same package)
        }
        return w;
    }
}
