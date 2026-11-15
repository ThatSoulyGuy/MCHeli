package mcheli.agnostic.vehicle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import mcheli.agnostic.aircraft.MCH_Parts;
import mcheli.agnostic.vehicle.CrewUnmount.DropTick;
import mcheli.agnostic.vehicle.CrewUnmount.KeyAction;
import org.junit.jupiter.api.Test;

/**
 * {@link CrewUnmount} against a private oracle: verbatim copies of the reference
 * {@code MCH_EntityAircraft.unmountCrew()} (EA:3681-3701), {@code onUpdate_UnmountCrew} (EA:1892-1904), the seat walk
 * of {@code unmountCrew(true)} (EA:3751-3785), {@code canUnmount} (EA:1924-1932) and {@code canParachuting}
 * (EA:1972-1978), with {@code getAcInfo()}, the ground probes, the hatch and the seat occupants turned into parameters
 * and each effect recorded instead of performed.
 */
class CrewUnmountTest {

    /** The reference bodies, verbatim except for the parameters. */
    private static final class Oracle {
        /** EA:3681-3701; {@code getBlockIdY} returns a block id, so {@code > 0} and {@code != 0} are the same test. */
        static KeyAction unmountCrew(boolean haveRepellingHook, boolean isRepelling, boolean ground4,
                                     boolean canStartRepelling, boolean isParachuting, boolean isEnableParachuting,
                                     boolean ground10) {
            int getBlockIdY4 = ground4 ? 1 : 0;
            int getBlockIdY10 = ground10 ? 1 : 0;
            KeyAction called = KeyAction.NONE;
            if (haveRepellingHook) {
                if (!isRepelling) {
                    if (getBlockIdY4 > 0) {
                        called = KeyAction.DISMOUNT_MOBS; // this.unmountCrew(false)
                    } else if (canStartRepelling) {
                        called = KeyAction.START_REPELLING;
                    }
                } else {
                    called = KeyAction.STOP_REPELLING;
                }
            } else if (isParachuting) {
                called = KeyAction.STOP_DROP;
            } else if (isEnableParachuting && getBlockIdY10 == 0) {
                called = KeyAction.START_DROP;
            } else {
                called = KeyAction.DISMOUNT_MOBS;
            }
            return called;
        }

        /** EA:1892-1904: STOP for a {@code stopUnmountCrew()} on the ground branch, DROP when {@code unmountCrew(true)}
         *  is reached (its result then decides the second stop, which the caller applies). */
        static DropTick onUpdateUnmountCrew(boolean isParachuting, boolean ground10, boolean haveHatch,
                                            float hatchRotation, int countOnUpdate, int interval) {
            if (isParachuting) {
                if ((ground10 ? 1 : 0) != 0) {
                    return DropTick.STOP;
                } else if ((!haveHatch || hatchRotation > 89.0F) && countOnUpdate % interval == 0) {
                    return DropTick.DROP;
                }
            }
            return DropTick.WAIT;
        }

        /** EA:3751-3785 with {@code unmountParachute = true}: {@code seats[i]} holds seat id {@code i + 1}; players and
         *  racks are skipped and the first mob with seat id over 1 goes. {@code kinds[id]}: 0 empty, 1 player, 2 mob,
         *  3 a mob on a rack seat. */
        static int unmountCrewParachute(int[] kinds) {
            for (int i = 0; i < kinds.length - 1; i++) {
                int id = i + 1;
                if (kinds[id] != 0) {
                    boolean player = kinds[id] == 1;
                    boolean rack = kinds[id] == 3;
                    if (!player && !rack) {
                        if (id > 1) {
                            return id;
                        }
                    }
                }
            }
            return -1;
        }

        /** EA:1924-1932, {@code canUnmount}: the server's seat-parachuting check. */
        static boolean canUnmount(boolean haveAcInfo, boolean isEnableParachuting, int getSeatIdByEntity,
                                  boolean haveHatch, float getHatchRotation) {
            if (!haveAcInfo) {
                return false;
            } else if (!isEnableParachuting) {
                return false;
            } else {
                return getSeatIdByEntity <= 1 ? false : !haveHatch || !(getHatchRotation < 89.0F);
            }
        }

        /** EA:1972-1978, {@code canParachuting}: the client's check, the hatch ternary included. */
        static boolean canParachuting(boolean haveAcInfo, boolean isEnableParachuting, int getSeatIdByEntity,
                                      boolean ground13, boolean haveHatch, float getHatchRotation) {
            int getBlockIdY13 = ground13 ? 1 : 0;
            if (!haveAcInfo || !isEnableParachuting || getSeatIdByEntity <= 1 || getBlockIdY13 != 0) {
                return false;
            } else {
                return haveHatch && getHatchRotation > 89.0F ? getSeatIdByEntity > 1 : getSeatIdByEntity > 1;
            }
        }
    }

    private static final float[] HATCH_ANGLES = {0.0F, 1.5F, 45.0F, 88.5F, 88.99F, 89.0F, 89.01F, 90.0F};

    @Test
    void theKeyMatchesTheReferenceOnEveryInput() {
        int checked = 0;
        for (int bits = 0; bits < 1 << 7; bits++) {
            boolean hooks = (bits & 1) != 0;
            boolean repelling = (bits & 2) != 0;
            boolean ground4 = (bits & 4) != 0;
            boolean canStart = (bits & 8) != 0;
            boolean dropping = (bits & 16) != 0;
            boolean enable = (bits & 32) != 0;
            boolean ground10 = (bits & 64) != 0;
            assertEquals(Oracle.unmountCrew(hooks, repelling, ground4, canStart, dropping, enable, ground10),
                CrewUnmount.onUnmountKey(hooks, repelling, ground4, canStart, dropping, enable, ground10),
                "inputs " + Integer.toBinaryString(bits));
            checked++;
        }
        assertEquals(128, checked);
    }

    @Test
    void theKeyPinnedCases() {
        // an2 (EnableParachuting, no hooks) at altitude starts a drop; again stops it; near the ground unloads plainly.
        assertEquals(KeyAction.START_DROP, CrewUnmount.onUnmountKey(false, false, false, false, false, true, false));
        assertEquals(KeyAction.STOP_DROP, CrewUnmount.onUnmountKey(false, false, false, false, true, true, false));
        assertEquals(KeyAction.STOP_DROP, CrewUnmount.onUnmountKey(false, false, false, false, true, true, true));
        assertEquals(KeyAction.DISMOUNT_MOBS, CrewUnmount.onUnmountKey(false, false, false, false, false, true, true));
        // A tank: always the plain unload.
        assertEquals(KeyAction.DISMOUNT_MOBS, CrewUnmount.onUnmountKey(false, false, false, false, false, false, false));
        // mh-6 (hooks): near the ground plainly, in the air only when repelling may start, and a stop while repelling.
        assertEquals(KeyAction.DISMOUNT_MOBS, CrewUnmount.onUnmountKey(true, false, true, true, false, false, true));
        assertEquals(KeyAction.NONE, CrewUnmount.onUnmountKey(true, false, false, false, false, false, false));
        assertEquals(KeyAction.START_REPELLING, CrewUnmount.onUnmountKey(true, false, false, true, false, false, false));
        assertEquals(KeyAction.STOP_REPELLING, CrewUnmount.onUnmountKey(true, true, true, false, false, false, true));
        // Hooks win over EnableParachuting and a running drop (the reference config never has both).
        assertEquals(KeyAction.NONE, CrewUnmount.onUnmountKey(true, false, false, false, true, true, false));
    }

    @Test
    void theDropTickMatchesTheReferenceOnEveryInput() {
        float[] rotations = {0.0F, 1.5F, 88.5F, 88.99F, 89.0F, 89.01F, 90.0F};
        int[] intervals = {1, 5, 10, 12, 3, -5, 100000};
        int checked = 0;
        for (int bits = 0; bits < 8; bits++) {
            boolean dropping = (bits & 1) != 0;
            boolean ground = (bits & 2) != 0;
            boolean hatch = (bits & 4) != 0;
            for (float rot : rotations) {
                for (int interval : intervals) {
                    for (int tick = 0; tick <= 130; tick++) {
                        assertEquals(Oracle.onUpdateUnmountCrew(dropping, ground, hatch, rot, tick, interval),
                            CrewUnmount.dropTick(dropping, ground, hatch, rot, tick, interval),
                            "dropping " + dropping + " ground " + ground + " hatch " + hatch + " rot " + rot
                                + " interval " + interval + " tick " + tick);
                        checked++;
                    }
                }
            }
        }
        assertEquals(8 * 7 * 7 * 131, checked);
    }

    @Test
    void theDropTickPinnedCases() {
        // The ground comes first, also with the hatch open on the cadence.
        assertEquals(DropTick.STOP, CrewUnmount.dropTick(true, true, true, 90.0F, 10, 10));
        assertEquals(DropTick.STOP, CrewUnmount.dropTick(true, true, false, 0.0F, 3, 5));
        // The hatch must be strictly past 89.
        assertEquals(DropTick.WAIT, CrewUnmount.dropTick(true, false, true, 89.0F, 10, 10));
        assertEquals(DropTick.DROP, CrewUnmount.dropTick(true, false, true, 89.01F, 10, 10));
        assertEquals(89.0F, CrewUnmount.HATCH_OPEN);
        // No hatch: a drop exactly on the multiples of the interval.
        for (int tick = 1; tick <= 40; tick++) {
            assertEquals(tick % 5 == 0 ? DropTick.DROP : DropTick.WAIT,
                CrewUnmount.dropTick(true, false, false, 0.0F, tick, 5), "an2 tick " + tick);
        }
        // Not dropping: nothing, not even on the ground.
        assertEquals(DropTick.WAIT, CrewUnmount.dropTick(false, true, false, 0.0F, 10, 1));
    }

    /** mv-22: Y forces the hatch open at 1.5 degrees a tick ({@code MCH_Parts} as the vehicle ticks it); it first
     *  exceeds 89 on the 60th tick after the switch, so the drops come on the first multiples of 10 from there. */
    @Test
    void theMv22HatchHoldsTheDropForSixtyTicks() {
        for (int yTick = 30; yTick < 50; yTick++) {
            MCH_Parts hatch = new MCH_Parts("Hatch", 90.0F, 1.5F);
            hatch.setStatus(true); // the forced open, on the key's tick (after that tick's parts update)
            int firstOpen = -1;
            List<Integer> drops = new ArrayList<>();
            for (int tick = yTick + 1; tick <= yTick + 130; tick++) {
                hatch.update(); // the parts tick at the start of the vehicle tick, the drop check after the move
                if (firstOpen < 0 && hatch.rotation > 89.0F) {
                    firstOpen = tick;
                }
                if (CrewUnmount.dropTick(true, false, true, hatch.rotation, tick, 10) == DropTick.DROP) {
                    drops.add(tick);
                }
            }
            assertEquals(yTick + 60, firstOpen, "the hatch passes 89 on the 60th tick after Y");
            int first = (yTick + 60 + 9) / 10 * 10;
            assertEquals(first, drops.get(0), "the first drop is the first multiple of 10 from there");
            for (int i = 0; i < drops.size(); i++) {
                assertEquals(first + 10 * i, drops.get(i), "then one every 10 ticks");
            }
        }
    }

    @Test
    void theDropSeatMatchesTheReference() {
        // Every occupancy of seats 1-5 by empty / player / mob, then a rack seat 6 (the reference lists racks after
        // the seats) that is empty or holds a mob. The port's seatCount (6) ends before the rack.
        int checked = 0;
        for (int code = 0; code < 3 * 3 * 3 * 3 * 3 * 2; code++) {
            int[] kinds = new int[7];
            int c = code;
            for (int id = 1; id <= 5; id++) {
                kinds[id] = c % 3;
                c /= 3;
            }
            kinds[6] = c == 0 ? 0 : 3;
            int expected = Oracle.unmountCrewParachute(kinds);
            int got = CrewUnmount.dropSeat(6, s -> kinds[s] == 2);
            assertEquals(expected, got, "occupancy " + java.util.Arrays.toString(kinds));
            checked++;
        }
        assertEquals(486, checked);
    }

    @Test
    void theDropSeatPinnedCases() {
        Set<Integer> mobs = Set.of(1, 3, 4);
        assertEquals(3, CrewUnmount.dropSeat(5, mobs::contains), "seat 1 is skipped");
        assertEquals(-1, CrewUnmount.dropSeat(5, s -> s == 1), "a mob only in seat 1 never goes");
        assertEquals(-1, CrewUnmount.dropSeat(2, s -> true), "a two-seater drops nobody");
        assertEquals(2, CrewUnmount.dropSeat(14, s -> s >= 2), "the lowest seat first");
        assertEquals(-1, CrewUnmount.dropSeat(5, s -> false), "players and empty seats are skipped");
    }

    @Test
    void anIntervalOfZeroIsReadAsOne() {
        assertEquals(1, CrewUnmount.safeInterval(0));
        assertEquals(1, CrewUnmount.safeInterval(1));
        assertEquals(5, CrewUnmount.safeInterval(5));
        assertEquals(12, CrewUnmount.safeInterval(12));
        assertEquals(-5, CrewUnmount.safeInterval(-5), "a negative interval is kept, as the reference");
        for (int tick = 1; tick <= 20; tick++) {
            assertEquals(DropTick.DROP, CrewUnmount.dropTick(true, false, false, 0.0F, tick,
                CrewUnmount.safeInterval(0)), "interval 0 drops every tick");
            assertEquals(tick % 5 == 0 ? DropTick.DROP : DropTick.WAIT,
                CrewUnmount.dropTick(true, false, false, 0.0F, tick, CrewUnmount.safeInterval(-5)));
        }
    }

    // ---- seat parachuting ----

    /** Both reference checks on every input: {@code canParachuting} ignores the hatch whatever its angle (the ternary
     *  answers the same on both branches), {@code canUnmount} ignores the ground. */
    @Test
    void theSeatChecksMatchTheReferenceOnEveryInput() {
        int checked = 0;
        for (int seat = -1; seat <= 15; seat++) {
            for (int bits = 0; bits < 8; bits++) {
                boolean enable = (bits & 1) != 0;
                boolean ground = (bits & 2) != 0;
                boolean hatch = (bits & 4) != 0;
                for (float rot : HATCH_ANGLES) {
                    String what = "seat " + seat + " enable " + enable + " ground " + ground + " hatch " + hatch
                        + " rot " + rot;
                    assertEquals(Oracle.canParachuting(true, enable, seat, ground, hatch, rot),
                        CrewUnmount.canParachuting(enable, seat, ground), "canParachuting " + what);
                    assertEquals(Oracle.canUnmount(true, enable, seat, hatch, rot),
                        CrewUnmount.canUnmount(enable, seat, hatch, rot), "canUnmount " + what);
                    checked++;
                }
            }
        }
        assertEquals(17 * 8 * HATCH_ANGLES.length, checked);
        // No config: both false (the entity passes no config as "not allowed" before it asks).
        assertFalse(Oracle.canParachuting(false, true, 2, false, false, 0.0F));
        assertFalse(Oracle.canUnmount(false, true, 2, false, 0.0F));
    }

    /** The port's gate is both reference checks at once, on every input, and asks for the ground only when the rest
     *  holds. */
    @Test
    void theSeatGateIsBothReferenceChecks() {
        for (int seat = -1; seat <= 15; seat++) {
            for (int bits = 0; bits < 8; bits++) {
                boolean enable = (bits & 1) != 0;
                boolean ground = (bits & 2) != 0;
                boolean hatch = (bits & 4) != 0;
                for (float rot : HATCH_ANGLES) {
                    int[] asked = {0};
                    boolean got = CrewUnmount.canSeatParachute(enable, seat, hatch, rot, () -> {
                        asked[0]++;
                        return ground;
                    });
                    boolean unmount = Oracle.canUnmount(true, enable, seat, hatch, rot);
                    String what = "seat " + seat + " enable " + enable + " ground " + ground + " hatch " + hatch
                        + " rot " + rot;
                    assertEquals(unmount && Oracle.canParachuting(true, enable, seat, ground, hatch, rot), got, what);
                    assertEquals(unmount ? 1 : 0, asked[0], "the ground probe is taken only when needed, " + what);
                }
            }
        }
    }

    @Test
    void theSeatGatePinnedCases() {
        // The hatch must have reached 89 (the server's >= 89), with no hatch nothing waits.
        assertFalse(CrewUnmount.canUnmount(true, 2, true, 88.99F));
        assertTrue(CrewUnmount.canUnmount(true, 2, true, 89.0F));
        assertTrue(CrewUnmount.canUnmount(true, 2, false, 0.0F));
        assertEquals(89.0F, CrewUnmount.HATCH_OPEN);
        assertEquals(13, CrewUnmount.PARACHUTING_GROUND_DEPTH);
        // Seat 1 never, seat 2 yes, for both checks and the gate; the pilot and a non-rider never.
        for (int seat : new int[] {-1, 0, 1}) {
            assertFalse(CrewUnmount.canParachuting(true, seat, false), "canParachuting seat " + seat);
            assertFalse(CrewUnmount.canUnmount(true, seat, false, 0.0F), "canUnmount seat " + seat);
            assertFalse(CrewUnmount.canSeatParachute(true, seat, false, 0.0F, () -> false), "gate seat " + seat);
        }
        assertTrue(CrewUnmount.canParachuting(true, 2, false));
        assertTrue(CrewUnmount.canUnmount(true, 2, false, 0.0F));
        assertTrue(CrewUnmount.canSeatParachute(true, 2, false, 0.0F, () -> false));
        // EnableParachuting off: never.
        assertFalse(CrewUnmount.canSeatParachute(false, 5, false, 90.0F, () -> false));
        // Exactly at 89.0 the passenger may go while the crew drop still waits (its strict > 89); at 89.01 both go.
        assertTrue(CrewUnmount.canSeatParachute(true, 3, true, 89.0F, () -> false));
        assertEquals(DropTick.WAIT, CrewUnmount.dropTick(true, false, true, 89.0F, 10, 10));
        assertTrue(CrewUnmount.canSeatParachute(true, 3, true, 89.01F, () -> false));
        assertEquals(DropTick.DROP, CrewUnmount.dropTick(true, false, true, 89.01F, 10, 10));
    }

    /** Where the two reference checks disagreed (quirk 6), the port's gate says no on both sides. */
    @Test
    void theSeatGateRefusesWhereTheReferenceChecksDisagreed() {
        // An MV-22 passenger behind a shut hatch, high up: the reference client sent, its server refused.
        assertTrue(Oracle.canParachuting(true, true, 3, false, true, 0.0F));
        assertFalse(Oracle.canUnmount(true, true, 3, true, 0.0F));
        assertFalse(CrewUnmount.canSeatParachute(true, 3, true, 0.0F, () -> false));
        // An An-2 passenger 11 above the ground: the reference client refused, its server would have dropped him.
        assertFalse(Oracle.canParachuting(true, true, 2, true, false, 0.0F));
        assertTrue(Oracle.canUnmount(true, true, 2, false, 0.0F));
        assertFalse(CrewUnmount.canSeatParachute(true, 2, false, 0.0F, () -> true));
        // Where they agreed, nothing changes: the hatch open and nothing below, both yes.
        assertTrue(Oracle.canParachuting(true, true, 3, false, true, 90.0F) && Oracle.canUnmount(true, true, 3, true,
            90.0F));
        assertTrue(CrewUnmount.canSeatParachute(true, 3, true, 90.0F, () -> false));
    }

    /** mv-22: after the pilot opens the hatch (1.5 degrees a tick, {@code MCH_Parts}), a passenger may go from the 60th
     *  tick (88.5 at the 59th, 90 at the 60th). */
    @Test
    void theMv22PassengerWaitsSixtyTicksForTheHatch() {
        MCH_Parts hatch = new MCH_Parts("Hatch", 90.0F, 1.5F);
        hatch.setStatus(true);
        int first = -1;
        for (int t = 1; t <= 70; t++) {
            hatch.update();
            boolean may = CrewUnmount.canSeatParachute(true, 3, true, hatch.rotation, () -> false);
            if (may && first < 0) {
                first = t;
            }
            if (first >= 0) {
                assertTrue(may, "it stays open, tick " + t);
            }
        }
        assertEquals(60, first);
    }
}
