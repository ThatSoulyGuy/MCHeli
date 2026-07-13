package mcheli.dependent.port;

import java.util.ArrayList;
import java.util.List;
import mcheli.agnostic.spi.EntityRef;
import mcheli.agnostic.spi.Role;
import mcheli.agnostic.spi.SyncedData;
import mcheli.agnostic.spi.WorldView;
import mcheli.agnostic.value.AABB;
import mcheli.agnostic.value.MoveResult;
import mcheli.agnostic.value.Vec3d;
import mcheli.dependent.entity.MchHelicopter;
import mcheli.dependent.entity.MchPlane;
import mcheli.dependent.entity.MchTank;
import mcheli.dependent.entity.MchGroundVehicle;
import mcheli.dependent.entity.RollHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The concrete {@link EntityRef} port: wraps a real Minecraft {@link Entity} and translates the agnostic
 * layer's kinematic reads/writes to it. This is the ONLY place the agnostic vehicle logic touches a
 * platform type; classification is mapped to {@link Role} here (the {@code instanceof} lives at the port
 * boundary, never in agnostic code).
 *
 * <p>The {@link #synced()} and {@link #world()} sub-surfaces are intentionally not implemented for the
 * vertical slice — the slice's agnostic driver ({@code SimpleVehicleLogic}) never reaches them. They are
 * the natural first work-items of the full dependent porting phase.
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

    @Override public Role role() {
        if (e instanceof Player) return Role.PLAYER;
        if (e instanceof MchHelicopter) return Role.HELICOPTER;
        if (e instanceof MchPlane) return Role.PLANE;
        if (e instanceof MchTank) return Role.TANK;
        if (e instanceof MchGroundVehicle) return Role.VEHICLE;
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

    // sub-surfaces
    @Override public SyncedData synced() {
        throw new UnsupportedOperationException("SyncedData port not implemented yet");
    }
    @Override public WorldView world() {
        return new NeoWorldView(e.level());  // thin wrapper over the entity's Level (same package)
    }
}
