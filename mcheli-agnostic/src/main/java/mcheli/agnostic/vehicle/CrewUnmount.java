package mcheli.agnostic.vehicle;

import java.util.function.BooleanSupplier;
import java.util.function.IntPredicate;

/**
 * The crew-unmount rules of the reference aircraft, static and pure: what the pilot's unmount key (Y,
 * {@code KeyUnmountMob}) does ({@link #onUnmountKey}), when a running crew drop lets the next mob go
 * ({@link #dropTick}), who goes ({@link #dropSeat}), and when a passenger may parachute out of his seat
 * ({@link #canSeatParachute}). The dependent entity feeds it plain config values, the ground probes and the seat
 * occupants; the drop itself, the hatch and the parachute stay on the entity.
 */
public final class CrewUnmount {
    private CrewUnmount() {}

    /** What one press of the unmount key does. */
    public enum KeyAction {
        /** Nothing (a hooked vehicle in the air that may not start repelling). */
        NONE,
        /** Every mob gets off beside its own seat ({@code unmountCrew(false)}). */
        DISMOUNT_MOBS,
        /** Lower the ropes ({@code startRepelling}). */
        START_REPELLING,
        /** Stop repelling ({@code stopRepelling}). */
        STOP_REPELLING,
        /** Begin dropping the mob crew on parachutes ({@code startUnmountCrew}). */
        START_DROP,
        /** Stop a drop in progress ({@code stopUnmountCrew}). */
        STOP_DROP
    }

    /** What a running crew drop does this tick. */
    public enum DropTick {
        /** Nothing this tick (no drop running, the hatch still opening, or off the cadence). */
        WAIT,
        /** Let the next mob go ({@code unmountCrew(true)}); the drop stops when there was none. */
        DROP,
        /** Stop the drop: the vehicle came within 10 blocks of the ground. */
        STOP
    }

    /** The hatch angle a drop waits for, strictly exceeded (reference {@code onUpdate_UnmountCrew}, EA:1897). Seat
     *  parachuting waits for the same angle, reached rather than exceeded ({@link #canUnmount}, EA:1930). */
    public static final float HATCH_OPEN = 89.0F;

    /** The column seat parachuting needs clear below: nothing within 13 blocks (reference {@code canParachuting},
     *  EA:1973, {@code getBlockIdY(this, 3, -13)}). */
    public static final int PARACHUTING_GROUND_DEPTH = 13;

    /**
     * Reference {@code unmountCrew()} (EA:3681-3701), the pilot's unmount key. A config with repelling hooks: plain
     * dismount within 4 blocks of the ground, else start repelling when it may, and stop it while it runs. Otherwise a
     * drop in progress stops; an {@code EnableParachuting} config with nothing within 10 blocks below starts one; and
     * anything else puts the mobs off plainly.
     *
     * @param hasHooks          the config has {@code AddRepellingHook} lines ({@code haveRepellingHook})
     * @param repelling         the vehicle is repelling ({@code isRepelling})
     * @param groundWithin4     {@code getBlockIdY(this, 3, -4) > 0}
     * @param canStartRepelling {@code canStartRepelling()}
     * @param dropping          a crew drop is running ({@code isParachuting})
     * @param enableParachuting the config {@code EnableParachuting}
     * @param groundWithin10    {@code getBlockIdY(this, 3, -10) != 0}
     */
    public static KeyAction onUnmountKey(boolean hasHooks, boolean repelling, boolean groundWithin4,
                                         boolean canStartRepelling, boolean dropping, boolean enableParachuting,
                                         boolean groundWithin10) {
        if (hasHooks) {
            if (repelling) {
                return KeyAction.STOP_REPELLING;
            }
            if (groundWithin4) {
                return KeyAction.DISMOUNT_MOBS;
            }
            return canStartRepelling ? KeyAction.START_REPELLING : KeyAction.NONE;
        }
        if (dropping) {
            return KeyAction.STOP_DROP;
        }
        if (enableParachuting && !groundWithin10) {
            return KeyAction.START_DROP;
        }
        return KeyAction.DISMOUNT_MOBS;
    }

    /**
     * Reference {@code onUpdate_UnmountCrew} (EA:1892-1904), once a tick: a running drop stops within 10 blocks of the
     * ground; otherwise, once the hatch (if the config has one) is past {@link #HATCH_OPEN} degrees, a mob goes on
     * every tick that is a multiple of the {@code MobDropOption} interval.
     *
     * @param dropping       a crew drop is running ({@code isParachuting})
     * @param groundWithin10 {@code getBlockIdY(this, 3, -10) != 0}
     * @param haveHatch      the config has a hatch
     * @param hatchRotation  the hatch angle, 0 shut to 90 open ({@code getHatchRotation})
     * @param tick           the vehicle's tick counter ({@code countOnUpdate})
     * @param interval       the drop interval, through {@link #safeInterval}
     */
    public static DropTick dropTick(boolean dropping, boolean groundWithin10, boolean haveHatch, float hatchRotation,
                                    int tick, int interval) {
        if (!dropping) {
            return DropTick.WAIT;
        }
        if (groundWithin10) {
            return DropTick.STOP;
        }
        if ((!haveHatch || hatchRotation > HATCH_OPEN) && tick % interval == 0) {
            return DropTick.DROP;
        }
        return DropTick.WAIT;
    }

    /**
     * Who the next drop lets go (reference {@code unmountCrew(true)}, EA:3751-3785): the lowest seat from 2 up whose
     * occupant is a mob. The pilot and seat 1 never go, nor does a player; rack seats lie outside {@code seatCount}.
     *
     * @param seatCount the vehicle's seats, the pilot's included
     * @param mobAt     whether a seat holds a mob (not a player, not empty)
     * @return the seat, or -1 when no mob is left to drop
     */
    public static int dropSeat(int seatCount, IntPredicate mobAt) {
        for (int s = 2; s < seatCount; s++) {
            if (mobAt.test(s)) {
                return s;
            }
        }
        return -1;
    }

    /**
     * The {@code MobDropOption} interval the cadence divides by. Deliberate divergence: reference bug. An interval of 0
     * is read as 1, where the reference's {@code countOnUpdate % 0} threw an {@code ArithmeticException} every tick of
     * a drop (the config value is not clamped, {@code MCH_AircraftInfo}:700-705). Every other value is kept, so a
     * negative interval fires on the multiples of its magnitude, as it did.
     */
    public static int safeInterval(int interval) {
        return interval == 0 ? 1 : interval;
    }

    /**
     * Reference {@code canParachuting} (EA:1972-1978), the CLIENT's check before a passenger's jump key
     * ({@code KeyParachuting}) is sent: an {@code EnableParachuting} config, a seat from 2 up, and nothing within
     * {@link #PARACHUTING_GROUND_DEPTH} blocks below. Its last line tests the hatch and answers {@code seatId > 1} on
     * both branches, so the hatch never mattered here; {@link #canUnmount} is the server's check.
     *
     * @param enableParachuting the config {@code EnableParachuting}
     * @param seat              the rider's seat ({@code getSeatIdByEntity}: 0 the pilot, -1 not aboard)
     * @param groundWithin13    {@code getBlockIdY(this, 3, -13) != 0}
     */
    public static boolean canParachuting(boolean enableParachuting, int seat, boolean groundWithin13) {
        return enableParachuting && seat > 1 && !groundWithin13;
    }

    /**
     * Reference {@code canUnmount} (EA:1924-1932), the SERVER's check before it parachutes a passenger out: an
     * {@code EnableParachuting} config, a seat from 2 up, and a hatch (where the config has one) at
     * {@link #HATCH_OPEN} degrees or more. It does not look at the ground.
     *
     * @param enableParachuting the config {@code EnableParachuting}
     * @param seat              the rider's seat ({@code getSeatIdByEntity}: 0 the pilot, -1 not aboard)
     * @param haveHatch         the config has a hatch
     * @param hatchRotation     the hatch angle, 0 shut to 90 open ({@code getHatchRotation})
     */
    public static boolean canUnmount(boolean enableParachuting, int seat, boolean haveHatch, float hatchRotation) {
        return enableParachuting && seat > 1 && (!haveHatch || !(hatchRotation < HATCH_OPEN));
    }

    /**
     * The port's one seat-parachuting gate, the same on both sides: the jump key's pre-check and the server's re-check
     * before it parachutes the passenger out. It is {@link #canUnmount} and {@link #canParachuting} together. The
     * ground probe is taken only when the rest holds (it reads up to 3 x 3 x 13 blocks).
     *
     * <p>Deliberate divergence: reference bug (the client and server gates disagreed). The reference client ignored
     * the hatch (the no-op ternary of EA:1976), so an MV-22 passenger behind a shut hatch sent a request the server
     * silently refused; and the server skipped the 13-block ground check its client made, trusting the client for it.
     * Here the client also waits for the hatch, and the server also checks the ground: an honest player sees the
     * reference behaviour, minus the request that could never work.
     *
     * @param enableParachuting the config {@code EnableParachuting}
     * @param seat              the rider's seat (0 the pilot, -1 not aboard)
     * @param haveHatch         the config has a hatch
     * @param hatchRotation     the hatch angle, 0 shut to 90 open
     * @param groundWithin13    {@code getBlockIdY(this, 3, -13) != 0}, asked only when the other conditions hold
     */
    public static boolean canSeatParachute(boolean enableParachuting, int seat, boolean haveHatch,
                                           float hatchRotation, BooleanSupplier groundWithin13) {
        return canUnmount(enableParachuting, seat, haveHatch, hatchRotation)
            && canParachuting(enableParachuting, seat, groundWithin13.getAsBoolean());
    }
}
