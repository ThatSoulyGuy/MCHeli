package mcheli.agnostic.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import mcheli.agnostic.helicopter.MCH_HeliInfo;
import mcheli.agnostic.spi.EntityRef;
import mcheli.agnostic.spi.RandomSource;
import mcheli.agnostic.spi.Role;
import mcheli.agnostic.spi.WorldView;
import mcheli.agnostic.util.MCH_LowPassFilterFloat;
import mcheli.agnostic.value.AABB;
import mcheli.agnostic.value.MoveResult;
import mcheli.agnostic.value.RayHit;
import mcheli.agnostic.value.Vec3d;
import org.junit.jupiter.api.Test;

/**
 * Behavioural trace tests for {@link HeliControlMapping} run through {@link RotationSolver#applyControl}: the mouse
 * stick drives yaw (+roll), hovering suppresses pitch/roll, and {@code onUpdateAngles} auto-damps bank. The exact
 * matrix-compose arithmetic is covered by {@link RotationSolverTest}; here we assert the heli-specific mapping.
 */
class HeliControlMappingTest {

    /** Air everywhere (getBlockId 0) so the heli reads "in the air / off the ground". */
    static final class AirWorld implements WorldView {
        @Override public boolean isRemote() { return false; }
        @Override public long totalTime() { return 0L; }
        @Override public int getBlockId(int x, int y, int z) { return 0; }
        @Override public boolean isWater(int x, int y, int z) { return false; }
        @Override public String materialName(int x, int y, int z) { return "air"; }
        @Override public boolean isCollidable(int x, int y, int z) { return false; }
        @Override public boolean isAABBInWater(AABB box) { return false; }
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

    /** Rotation-only heli fake: rotation + a fixed airborne position + old enough to allow rotation. */
    static final class FakeHeli implements EntityRef {
        float yaw, pitch, roll;
        boolean grounded; // vanilla onGround flag
        private final WorldView world = new AirWorld();
        @Override public float yaw() { return yaw; }
        @Override public float pitch() { return pitch; }
        @Override public float roll() { return roll; }
        @Override public void setRotation(float y, float p) { yaw = y; pitch = p; }
        @Override public void setRoll(float r) { roll = r; }
        @Override public Vec3d position() { return new Vec3d(0.5, 100.0, 0.5); } // airborne
        @Override public int ageTicks() { return 100; }                          // >= 30 -> rotation allowed
        @Override public WorldView world() { return world; }
        // unused
        @Override public int id() { return 1; }
        @Override public boolean isSameAs(EntityRef o) { return this == o; }
        @Override public boolean isDead() { return false; }
        @Override public boolean isRemote() { return false; }
        @Override public Role role() { return Role.HELICOPTER; }
        @Override public Vec3d prevPosition() { return position(); }
        @Override public Vec3d motion() { return Vec3d.ZERO; }
        @Override public float prevYaw() { return yaw; }
        @Override public float prevPitch() { return pitch; }
        @Override public float prevRoll() { return roll; }
        @Override public float width() { return 1; }
        @Override public float height() { return 1; }
        @Override public AABB boundingBox() { return new AABB(0, 99, 0, 1, 100, 1); }
        @Override public boolean onGround() { return grounded; }
        @Override public boolean isInWater() { return false; }
        @Override public void setPosition(Vec3d p) { }
        @Override public void setMotion(Vec3d m) { }
        @Override public void addMotion(Vec3d d) { }
        @Override public MoveResult move(Vec3d d) { throw new UnsupportedOperationException(); }
        @Override public EntityRef vehicle() { return null; }
        @Override public List<EntityRef> passengers() { return List.of(); }
    }

    static final class FakeHeliState implements HeliState {
        boolean hovering, folded;
        @Override public boolean isBladeFolded() { return folded; }
        /** These traces drive the stick, which only happens with someone in the seat. Not read by
         *  {@code HeliControlMapping} (the predicate's sole consumer is {@code HeliFlightModel}), but stated
         *  explicitly because {@link HeliState#hasPilot()} deliberately has no default. */
        @Override public boolean hasPilot() { return true; }
        @Override public boolean isDestroyed() { return false; }
        @Override public boolean isHovering() { return hovering; }
        @Override public boolean isHoveringMode() { return hovering; }
        @Override public boolean isGunnerMode() { return false; }
        @Override public boolean canUseFuel(boolean checkOtherSeat) { return true; }
        @Override public boolean canUseFuel() { return true; }
        @Override public boolean canUseBlades() { return true; }
        @Override public boolean isCanopyClose() { return true; }
        @Override public void switchHoveringMode(boolean on) { hovering = on; }
        @Override public void switchGunnerMode(boolean on) { }
    }

    static MCH_LowPassFilterFloat primed(float v) {
        MCH_LowPassFilterFloat lp = new MCH_LowPassFilterFloat(10);
        for (int i = 0; i < 10; i++) {
            lp.put(v);
        }
        return lp;
    }

    static ControlInput stick(double stickX, double stickY, boolean left, boolean right, float partialTicks) {
        return new ControlInput(stickX, stickY, false, false, left, right, false, false, false, false, partialTicks);
    }

    @Test
    void mouseStickYawsAndBanks() {
        FakeHeli e = new FakeHeli();
        MCH_HeliInfo info = new MCH_HeliInfo("test_heli"); // mobility*=1, limitRotation=false, onGroundPitch=0
        HeliControlMapping map = new HeliControlMapping(e, info, new AircraftSimState(), new FakeHeliState());

        // stickX 100 -> yaw (clamp 40)*0.06*0.5 = 1.2; roll uses factor (1+prev0)/2 = 0.5 -> 40*0.5*0.06*0.5 = 0.6,
        // then bank-damped *~0.98. pitch stays 0 (stickY 0).
        RotationSolver.applyControl(e, info, stick(100, 0, false, false, 0.5F), primed(0.5F), map);

        assertEquals(1.2f, e.yaw, 0.2f);
        assertTrue(e.roll > 0.3f && e.roll < 0.7f, "banked right ~0.59, was " + e.roll);
        assertEquals(0.0f, e.pitch, 0.1f);
    }

    @Test
    void parkedHeliIgnoresTheMouseEvenWhenTheProbeSeesNoGround() {
        // Resting on ground the 2-row probe cannot see (a terrace edge past the rounded centre): the probe says
        // "air" but Minecraft reports ground contact -> no mouse pitch/roll/yaw, and the on-ground auto-level runs.
        FakeHeli e = new FakeHeli();
        e.grounded = true;
        e.pitch = 10.0F;
        MCH_HeliInfo info = new MCH_HeliInfo("test_heli");
        HeliControlMapping map = new HeliControlMapping(e, info, new AircraftSimState(), new FakeHeliState());
        assertTrue(!map.canUpdatePitch() && !map.canUpdateRoll() && !map.canUpdateYaw(), "no mouse rotation on the ground");
        for (int i = 0; i < 20; i++) {
            RotationSolver.applyControl(e, info, stick(100, 1000, false, false, 0.5F), primed(0.5F), map);
        }
        assertTrue(Math.abs(e.roll) < 0.01F, "no bank on the ground, was " + e.roll);
        assertTrue(e.pitch < 10.0F && e.pitch > 0.0F, "pitch levels toward onGroundPitch, was " + e.pitch);
    }

    @Test
    void hoveringSuppressesPitch() {
        FakeHeli e = new FakeHeli();
        MCH_HeliInfo info = new MCH_HeliInfo("test_heli");
        FakeHeliState mod = new FakeHeliState();
        mod.hovering = true; // canUpdatePitch/Roll = base && !hovering = false
        HeliControlMapping map = new HeliControlMapping(e, info, new AircraftSimState(), mod);

        // Large pitch stick, but hovering suppresses pitch control -> pitch stays 0.
        RotationSolver.applyControl(e, info, stick(0, 1000, false, false, 0.5F), primed(0.5F), map);

        assertEquals(0.0f, e.pitch, 0.05f);
    }

    @Test
    void foldedHeliTurnsOnTheSpotWithTheStrafeKeys() {
        // MCH_EntityHeli:449-457: parked with the blades folded, A/D yaw the hull 0.5 deg per tick (x partialTicks);
        // canUpdateYaw is false on the ground, so this is the only yaw.
        FakeHeli e = new FakeHeli();
        e.grounded = true;
        MCH_HeliInfo info = new MCH_HeliInfo("test_heli");
        FakeHeliState mod = new FakeHeliState();
        mod.folded = true;
        HeliControlMapping map = new HeliControlMapping(e, info, new AircraftSimState(), mod);

        RotationSolver.applyControl(e, info, stick(0, 0, true, false, 0.5F), primed(0.5F), map);
        assertEquals(-0.25f, e.yaw, 1e-4f);
        RotationSolver.applyControl(e, info, stick(0, 0, false, true, 0.5F), primed(0.5F), map);
        assertEquals(0.0f, e.yaw, 1e-4f);

        mod.folded = false; // unfolded blades on the ground: no key yaw
        RotationSolver.applyControl(e, info, stick(0, 0, true, false, 0.5F), primed(0.5F), map);
        assertEquals(0.0f, e.yaw, 1e-4f);
    }

    @Test
    void hoveringFreesTheViewPitchOnly() {
        // isOverridePlayerPitch = !isHovering (MCH_EntityHeli:388-390); the yaw stays welded to the hull.
        FakeHeliState mod = new FakeHeliState();
        HeliControlMapping map = new HeliControlMapping(new FakeHeli(), new MCH_HeliInfo("test_heli"), new AircraftSimState(), mod);
        assertTrue(map.overridesRiderYaw() && map.overridesRiderPitch());
        mod.hovering = true;
        assertTrue(map.overridesRiderYaw());
        assertTrue(!map.overridesRiderPitch());
    }

    @Test
    void bankAutoDampingDecaysRoll() {
        FakeHeli e = new FakeHeli();
        e.roll = 20.0f; // banked
        MCH_HeliInfo info = new MCH_HeliInfo("test_heli");
        HeliControlMapping map = new HeliControlMapping(e, info, new AircraftSimState(), new FakeHeliState());

        // No stick input; onUpdateAngles damps bank toward level: 20 in (0.1,65) -> *(1-0.04*0.5)=0.98 -> ~19.6.
        RotationSolver.applyControl(e, info, stick(0, 0, false, false, 0.5F), primed(0.5F), map);

        assertTrue(e.roll < 20.0f && e.roll > 19.0f, "bank decayed toward level ~19.6, was " + e.roll);
    }
}
