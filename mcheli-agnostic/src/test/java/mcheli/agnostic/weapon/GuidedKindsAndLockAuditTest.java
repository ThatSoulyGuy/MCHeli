package mcheli.agnostic.weapon;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The guided-weapon kinds added on top of AA/AT direct homing (AT top attack, AS ground point, guided torpedo), the
 * mortar landing-distance march ({@code MCH_WeaponBase.getLandInDistance}) and the server's lock audit. Zero Minecraft.
 */
class GuidedKindsAndLockAuditTest {

    // ---- AT top attack (MCH_EntityATMissile guidanceType 1) ----

    @Test
    void topAttackClimbsLevelsThenDives() {
        int rig = 7;
        assertArrayEquals(new double[]{10, 164, 20}, MCH_MissileGuidance.topAttackAim(rig + 1, rig, 10, 0, 20, 14), 1e-9);
        assertArrayEquals(new double[]{10, 164, 20}, MCH_MissileGuidance.topAttackAim(rig + 20, rig, 10, 0, 20, 14), 1e-9);
        assertArrayEquals(new double[]{10, 14, 20}, MCH_MissileGuidance.topAttackAim(rig + 21, rig, 10, 0, 20, 14), 1e-9);
        assertArrayEquals(new double[]{10, 0, 20}, MCH_MissileGuidance.topAttackAim(rig + 31, rig, 10, 0, 20, 14), 1e-9);
        assertTrue(MCH_MissileGuidance.isTopAttackDiveTick(rig + 35, rig));
        assertFalse(MCH_MissileGuidance.isTopAttackDiveTick(rig + 34, rig));
    }

    // ---- AS missile (MCH_EntityASMissile) ----

    @Test
    void asMissileAimsAboveTheTargetForTenTicksThenAtIt() {
        MCH_MissileGuidance.Motion early = MCH_MissileGuidance.asMissile(0, 0, 3, 0, 0, 0, 0, 0, 100, 2.0, false, 5);
        assertTrue(early.y > 0.0, "the first 10 ticks steer at a point 20 blocks above the target");
        assertEquals(2.0, Math.sqrt(early.x * early.x + early.y * early.y + early.z * early.z), 1e-9);
        MCH_MissileGuidance.Motion late = MCH_MissileGuidance.asMissile(0, 0, 3, 0, 0, 0, 0, 0, 100, 2.0, false, 10);
        assertEquals(0.0, late.y, 1e-9);
        assertEquals(2.0, late.z, 1e-9);
    }

    @Test
    void glidingAsMissileSteersOnlyHorizontally() {
        MCH_MissileGuidance.Motion m = MCH_MissileGuidance.asMissile(0, -0.4, 1, 0, 50, 0, 30, 0, 40, 1.7, true, 30);
        assertEquals(-0.4, m.y, 1e-12, "a gliding bomb keeps its falling motion");
        assertTrue(m.x > 0.0 && m.z > 0.0, "and turns toward the point");
    }

    @Test
    void torpedoSpeedRampsIntoTheDeadBand() {
        assertEquals(1.1, MCH_MissileGuidance.torpedoSpeed(1.0, 2.5), 1e-9);
        assertEquals(2.6, MCH_MissileGuidance.torpedoSpeed(2.6, 2.5), 1e-9);
        assertEquals(2.8, MCH_MissileGuidance.torpedoSpeed(2.9, 2.5), 1e-9);
    }

    // ---- mortar landing distance (MCH_WeaponBase.getLandInDistance) ----

    /** Flat ground: the top face of y = 0 blocks. */
    private static int[] ground(double x0, double y0, double z0, double x1, double y1, double z1) {
        if (y1 > 1.0) {
            return null;
        }
        double f = y0 == y1 ? 0.0 : (y0 - 1.0) / (y0 - y1);
        return new int[]{(int) Math.floor(x0 + (x1 - x0) * f), (int) Math.floor(z0 + (z1 - z0) * f)};
    }

    @Test
    void lobbedShellLandsDownrangeAndAFasterShellFlyFarther() {
        // 45 degrees up along +Z from (0.5, 1, 0.5) over flat ground whose top is y = 1.
        double slow = MCH_WeaponBallistics.landInDistance(-0.04F, 1.2F, 0, 1, 1, 0.5, 1.0, 0.5,
            GuidedKindsAndLockAuditTest::ground);
        double fast = MCH_WeaponBallistics.landInDistance(-0.04F, 2.0F, 0, 1, 1, 0.5, 1.0, 0.5,
            GuidedKindsAndLockAuditTest::ground);
        // Drag-free: range ~ v^2 / g = 1.44 / 0.04 = 36 blocks, a little more from the 3-block launch height.
        assertTrue(slow > 30.0 && slow < 50.0, "got " + slow);
        assertTrue(fast > slow, "a faster shell flies farther (" + fast + " vs " + slow + ")");
    }

    @Test
    void landInDistanceRejectsFlatShotsAndNonFallingWeapons() {
        MCH_WeaponBallistics.BlockRay sky = (a, b, c, e, f, g) -> null;
        assertEquals(-1.0, MCH_WeaponBallistics.landInDistance(0.0F, 2.0F, 0, 1, 1, 0, 0, 0, sky), "gravity >= 0");
        assertEquals(-1.0, MCH_WeaponBallistics.landInDistance(-0.04F, 2.0F, 0, -1, 1, 0, 0, 0, sky), "aimed down");
        assertTrue(MCH_WeaponBallistics.landInDistance(-0.04F, 2.0F, 0, 1, 1, 0, 0, 0, sky) > 0.0,
            "with nothing in the way the arc lands where it falls back below the launcher");
    }

    // ---- server lock audit ----

    @Test
    void auditAcceptsALockItWatchedBuild() {
        MCH_LockAudit a = new MCH_LockAudit();
        a.observeCandidate(42, 100);
        for (long t = 100; t <= 130; t++) {
            a.sample(t, true, true);
        }
        assertTrue(a.plausible(42, 130, 30));
        assertFalse(a.plausible(43, 130, 30), "a different entity than the one being locked");
        assertFalse(a.plausible(-1, 130, 30));
    }

    @Test
    void auditRejectsAnEarlyClaimAndANeverSeenTarget() {
        MCH_LockAudit early = new MCH_LockAudit();
        early.observeCandidate(7, 0);
        early.sample(0, true, true);
        assertFalse(early.plausible(7, 5, 70), "a 70-tick lock cannot complete after 5 ticks");
        early.sample(60, true, true);
        assertTrue(early.plausible(7, 70 - MCH_LockAudit.TIME_SLACK, 70), "within the time slack of the lock time");

        MCH_LockAudit blind = new MCH_LockAudit();
        blind.observeCandidate(7, 0);
        for (long t = 0; t <= 40; t++) {
            blind.sample(t, true, false);
        }
        assertTrue(blind.needsSightCheck());
        assertFalse(blind.plausible(7, 40, 20), "the target was never in line of sight");

        MCH_LockAudit offCone = new MCH_LockAudit();
        offCone.observeCandidate(7, 0);
        offCone.sample(0, true, true);
        assertFalse(offCone.plausible(7, 100, 20), "out of the cone longer than the hold window");
    }

    @Test
    void lockAgeScalesWithTheLockTimeSoNoBundledSeekerCompletesInstantly() {
        // Every LockTime the bundled AA/AT seekers ship (the default, 30, among them).
        for (int lockTime : new int[] {10, 12, 13, 14, 15, 20, 25, 30, 50, 70}) {
            int min = MCH_LockAudit.minLockAge(lockTime);
            assertTrue(min >= lockTime - MCH_LockAudit.TIME_SLACK && min >= lockTime * 2 / 3,
                "lock " + lockTime + " must take at least two thirds of its time, got " + min);

            MCH_LockAudit cheat = new MCH_LockAudit();
            cheat.observeCandidate(9, 1000);
            cheat.sample(1000, true, true);
            assertFalse(cheat.plausible(9, 1000, lockTime), "notice and claim in one tick, lock " + lockTime);
            assertFalse(cheat.plausible(9, 1000 + lockTime / 2, lockTime), "a half-built lock, lock " + lockTime);
            assertTrue(honestClaimPasses(lockTime), "the honest claim of lock " + lockTime);
        }
        assertEquals(7, MCH_LockAudit.minLockAge(10), "LockTime 10: a third of the lock is the jitter slack");
        assertEquals(20, MCH_LockAudit.minLockAge(30), "LockTime 30: the full TIME_SLACK");
        assertEquals(60, MCH_LockAudit.minLockAge(70), "the slack never exceeds TIME_SLACK");
        // The parser's range ends (LockTime 2..1000): a short lock keeps one tick of slack, and nothing ever passes in
        // its notice's own tick.
        assertEquals(1, MCH_LockAudit.minLockAge(2));
        assertTrue(honestClaimPasses(2));
        assertEquals(990, MCH_LockAudit.minLockAge(1000));
        assertTrue(honestClaimPasses(1000));
        assertEquals(1, MCH_LockAudit.minLockAge(1), "a one-tick lock is never accepted in its notice's tick");
    }

    /** A real client: the first notice on the lock's first tick, the claim when its counter reaches the lock time,
     *  {@code lockTime - 1} ticks later (MchLockTracker / MchClientInput), with the target in the cone all along. */
    private static boolean honestClaimPasses(int lockTime) {
        MCH_LockAudit real = new MCH_LockAudit();
        real.observeCandidate(9, 1000);
        for (long t = 1000; t < 1000 + lockTime; t++) {
            real.sample(t, true, true);
        }
        return real.plausible(9, 1000 + lockTime - 1, lockTime);
    }

    @Test
    void auditHoldWindowAndCandidateTimeout() {
        MCH_LockAudit a = new MCH_LockAudit();
        a.observeCandidate(5, 0);
        a.sample(0, true, true);
        a.sample(29, true, true);
        // lockCountMax 30 -> hold min(10, 20) + latency 10 = 20 ticks after the last in-cone sample
        assertTrue(a.plausible(5, 49, 30));
        assertFalse(a.plausible(5, 50, 30));

        a.observeCandidate(5, 200); // a notice after a long silence restarts the candidacy
        a.sample(200, true, true);
        assertFalse(a.plausible(5, 205, 30), "the restarted lock has not had time to build");
    }

    @Test
    void relayIsRateLimitedAndTheServerConeWidens() {
        MCH_LockAudit a = new MCH_LockAudit();
        assertTrue(a.allowRelay(0));
        assertFalse(a.allowRelay(MCH_LockAudit.RELAY_INTERVAL - 1));
        assertTrue(a.allowRelay(MCH_LockAudit.RELAY_INTERVAL));

        assertEquals(5.0F + MCH_LockAudit.CONE_SLACK_DEG, MCH_LockAudit.serverConeAngle(5.0F, 0.0, 100.0), 1e-4F);
        assertTrue(MCH_LockAudit.serverConeAngle(5.0F, 3.0, 30.0) > MCH_LockAudit.serverConeAngle(5.0F, 3.0, 300.0),
            "a fast target close by gets more slack than the same target far away");
    }

    // ---- the target's stealth (reference MCH_WeaponGuidanceSystem: lock() :142-145, :181-190, getLockCountMax) ----

    /** Every {@code Stealth} the bundled aircraft ship (penetrator's 1.0 aside). */
    private static final float[] BUNDLED_STEALTH = {0.1F, 0.15F, 0.2F, 0.3F, 0.35F, 0.4F, 0.5F};

    @Test
    void serverLockRangeShrinksWithStealthAndIsGoneAtFullStealth() {
        assertEquals(200.0 * 1.1 + 4.0, MCH_LockAudit.serverLockRange(200.0, 0.0F), 1e-9, "no stealth: the old range");
        assertEquals(100.0 * 1.1 + 4.0, MCH_LockAudit.serverLockRange(200.0, 0.5F), 1e-6, "fl282: half the range");
        assertEquals(0.0, MCH_LockAudit.serverLockRange(200.0, 1.0F), 1e-9,
            "Stealth 1 (penetrator): no client can lock it at any range, so the server allows none either");
        for (float s : BUNDLED_STEALTH) {
            double server = MCH_LockAudit.serverLockRange(200.0, s);
            assertTrue(server > MCH_LockGeometry.effectiveRange(200.0, s), "never short of the client's range, " + s);
            assertTrue(server < MCH_LockAudit.serverLockRange(200.0, 0.0F), "shorter than an unstealthy target's, " + s);
        }
    }

    @Test
    void acquisitionConeNarrowsWithStealthWhileTheHoldConeDoesNot() {
        float hold = MCH_LockAudit.serverConeAngle(5.0F, 1.0, 50.0);
        assertEquals(hold, MCH_LockAudit.serverAcquireConeAngle(5.0F, 0.0F, 1.0, 50.0), 1e-5F, "no stealth");
        assertEquals(hold - 2.5F, MCH_LockAudit.serverAcquireConeAngle(5.0F, 1.0F, 1.0, 50.0), 1e-4F,
            "Stealth 1 halves the seeker cone (reference lockAngle * (stealth / 2 + 0.5))");
        for (float s : BUNDLED_STEALTH) {
            float acquire = MCH_LockAudit.serverAcquireConeAngle(5.0F, s, 1.0, 50.0);
            assertTrue(acquire < hold && acquire > hold - 2.5F, "between the plain and the halved cone, " + s);
        }
    }

    @Test
    void aStealthyTargetNeedsItsLengthenedLockTime() {
        // A LockTime-30 seeker on a Stealth-0.5 target (fl282): the reference getLockCountMax is 45 ticks, not 30.
        int plain = MCH_LockGeometry.effectiveCountMax(30, 0.0F);
        int stealthy = MCH_LockGeometry.effectiveCountMax(30, 0.5F);
        assertEquals(45, stealthy);
        MCH_LockAudit a = new MCH_LockAudit();
        a.observeCandidate(3, 0);
        for (long t = 0; t < plain; t++) {
            a.sample(t, true, true);
        }
        assertTrue(a.plausible(3, plain - 1, plain), "the claim of a client that ignores stealth, judged without it");
        assertFalse(a.plausible(3, plain - 1, stealthy), "... is refused once the target's stealth counts");
        for (long t = plain; t < stealthy; t++) {
            a.sample(t, true, true);
        }
        assertTrue(a.plausible(3, stealthy - 1, stealthy), "the honest claim on the stealthy target");
        // The hold window follows the lengthened lock too: min(45 / 3, 20) = 15 ticks, plus the latency slack.
        assertTrue(a.plausible(3, stealthy - 1 + 15 + MCH_LockAudit.LATENCY_SLACK, stealthy));
        assertFalse(a.plausible(3, stealthy + 15 + MCH_LockAudit.LATENCY_SLACK, stealthy));
    }

    @Test
    void sightOnlyCountsFromInsideTheAcquisitionCone() {
        // Held in the (wider) hold cone the whole time but never seen in sight inside the acquisition cone.
        MCH_LockAudit a = new MCH_LockAudit();
        a.observeCandidate(4, 0);
        for (long t = 0; t <= 30; t++) {
            a.sample(t, true, false);
        }
        assertTrue(a.needsSightCheck());
        assertFalse(a.plausible(4, 30, 30), "never acquired");
        a.sample(31, true, true);
        assertFalse(a.needsSightCheck());
        assertTrue(a.plausible(4, 31, 30), "acquired once, then held");
    }
}
