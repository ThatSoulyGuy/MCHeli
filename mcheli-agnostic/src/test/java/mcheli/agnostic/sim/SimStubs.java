package mcheli.agnostic.sim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mcheli.agnostic.spi.EntityRef;
import mcheli.agnostic.spi.RandomSource;
import mcheli.agnostic.spi.Role;
import mcheli.agnostic.spi.WorldView;
import mcheli.agnostic.value.AABB;
import mcheli.agnostic.value.MoveResult;
import mcheli.agnostic.value.RayHit;
import mcheli.agnostic.value.Vec3d;

/** Small shared stubs for the tests of the per-tick helpers and the landing gear. */
final class SimStubs {
    private SimStubs() {}

    /** A scripted block world with the production predicate semantics (NeoWorldView): {@code isCollidable} counts
     *  solids and water, {@code hasCollision} solids only. */
    static final class Blocks implements WorldView {
        static final int SOLID = 1, WATER = 2;
        final Map<Long, Integer> cells = new HashMap<>();

        static long k(int x, int y, int z) { return ((long) x * 73856093L) ^ ((long) y * 19349663L) ^ ((long) z * 83492791L); }
        Blocks set(int x, int y, int z, int c) { cells.put(k(x, y, z), c); return this; }
        /** Fill the square x,z in [-r, r] at height y. */
        Blocks layer(int y, int r, int c) {
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    set(x, y, z, c);
                }
            }
            return this;
        }

        @Override public int getBlockId(int x, int y, int z) { return cells.getOrDefault(k(x, y, z), 0); }
        @Override public boolean isWater(int x, int y, int z) { return getBlockId(x, y, z) == WATER; }
        @Override public boolean isCollidable(int x, int y, int z) { return getBlockId(x, y, z) != 0; }
        @Override public boolean hasCollision(int x, int y, int z) { return getBlockId(x, y, z) == SOLID; }
        @Override public boolean isAABBInWater(AABB box) { return false; }
        @Override public boolean isRemote() { return false; }
        @Override public long totalTime() { return 0; }
        @Override public String materialName(int x, int y, int z) { return "stub"; }
        @Override public List<EntityRef> entitiesInAABB(AABB box) { return List.of(); }
        @Override public List<EntityRef> entitiesInAABB(AABB box, Role role) { return List.of(); }
        @Override public RayHit rayTraceBlocks(Vec3d from, Vec3d to) { return null; }
        @Override public RayHit clip(Vec3d from, Vec3d to, boolean stopOnLiquid) { return null; }
        @Override public void spawn(EntityRef entity) { }
        @Override public void spawnParticle(String name, Vec3d pos, Vec3d velocity) { }
        @Override public void playSound(Vec3d pos, String name, float volume, float pitch) { }
        @Override public void playSoundAtEntity(EntityRef entity, String name, float volume, float pitch) { }
        @Override public RandomSource random() { throw new UnsupportedOperationException(); }
    }

    /** A positionable entity with rotation, ground/water flags and a passenger list; move() applies the full delta. */
    static final class Craft implements EntityRef {
        double px, py, pz, mx, my, mz;
        float yaw, pitch, roll;
        boolean onGround, inWater;
        WorldView world = new Blocks();
        final List<EntityRef> passengers = new ArrayList<>();

        Craft at(double x, double y, double z) { px = x; py = y; pz = z; return this; }
        Craft in(WorldView w) { world = w; return this; }

        @Override public Vec3d position() { return new Vec3d(px, py, pz); }
        @Override public Vec3d motion() { return new Vec3d(mx, my, mz); }
        @Override public float yaw() { return yaw; }
        @Override public float pitch() { return pitch; }
        @Override public float roll() { return roll; }
        @Override public boolean onGround() { return onGround; }
        @Override public boolean isInWater() { return inWater; }
        @Override public List<EntityRef> passengers() { return passengers; }
        @Override public WorldView world() { return world; }
        @Override public void setPosition(Vec3d p) { px = p.x(); py = p.y(); pz = p.z(); }
        @Override public void setMotion(Vec3d m) { mx = m.x(); my = m.y(); mz = m.z(); }
        @Override public void setRotation(float y, float p) { yaw = y; pitch = p; }
        @Override public void setRoll(float r) { roll = r; }
        @Override public MoveResult move(Vec3d d) {
            px += d.x(); py += d.y(); pz += d.z();
            return new MoveResult(d, onGround, false, false, false, false, false);
        }
        @Override public int id() { return 1; }
        @Override public boolean isSameAs(EntityRef o) { return this == o; }
        @Override public boolean isDead() { return false; }
        @Override public boolean isRemote() { return false; }
        @Override public Role role() { return Role.PLANE; }
        @Override public Vec3d prevPosition() { return position(); }
        @Override public float prevYaw() { return yaw; }
        @Override public float prevPitch() { return pitch; }
        @Override public float prevRoll() { return roll; }
        @Override public float width() { return 1.0F; }
        @Override public float height() { return 1.0F; }
        @Override public AABB boundingBox() { return new AABB(px - 0.5, py, pz - 0.5, px + 0.5, py + 1, pz + 0.5); }
        @Override public int ageTicks() { return 100; }
        @Override public void addMotion(Vec3d d) { mx += d.x(); my += d.y(); mz += d.z(); }
        @Override public EntityRef vehicle() { return null; }
    }

    /** A plain mutable {@link AircraftState}. */
    static final class State implements AircraftState {
        boolean destroyed, exploded, hovering, pilot = true;
        @Override public boolean isDestroyed() { return destroyed; }
        @Override public boolean hasPilot() { return pilot; }
        @Override public boolean isHovering() { return hovering; }
        @Override public boolean isExploded() { return exploded; }
    }
}
