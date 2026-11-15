package mcheli.agnostic.vehicle;

/**
 * The rope-repelling rules of the reference aircraft, static and pure: how the ropes grow ({@link #ropeStep},
 * {@code onUpdate_Repelling}, EA:1831-1844), the rope-out time and the mob cadence ({@link #tickStep},
 * {@link #cadenceOpen}, {@link #fires}, {@code onUpdate_UnmountCrewRepelling}, EA:1846-1877), when a player may slide
 * ({@link #canRepelling}, EA:1827-1829) and down which rope ({@link #nextRope}, EA:1939), when the pilot may start
 * ({@link #canStartRepelling}, EA:3740-3749) and when a rope slider lets its rider go at the rope's end
 * ({@link #sliderReleased}, {@code MCH_EntityHide.onUpdateServer}:233), and what the pilot's free-look key does while
 * the ropes are down ({@link #freeLookAfterKey}).
 * The dependent entity feeds it the synced repelling flag, its own per-side counters, the altitude probe and the pose;
 * the ropes, the sliders and the seats stay on the entity.
 *
 * <p>The rope length and the tick counter are per side and never synced, as in the reference: both sides derive them
 * from the synced flag, the server lowering the rope 0.25 a tick and a client 0.3 (faithful; only the server's length
 * decides anything, the client's is what the rope render draws).
 */
public final class Repelling {
    private Repelling() {}

    /** The longest rope (reference EA:1836, {@code ropesLength > -50}). */
    public static final float MAX_ROPE = 50.0F;
    /** How far the server lowers the ropes a tick (reference EA:1837, {@code 0.25}, a double). */
    public static final double SERVER_ROPE_STEP = 0.25;
    /** How far a client lowers the ropes a tick (reference EA:1837, {@code 0.3F}, widened to a double). */
    public static final float CLIENT_ROPE_STEP = 0.3F;
    /** The rope-out time: the tick counter stops here and the mob cadence starts (reference EA:1851). */
    public static final int ROPE_OUT_TICKS = 60;
    /** A player may slide once the tick counter is past this (reference {@code canRepelling}, EA:1828). */
    public static final int SLIDE_AFTER_TICKS = 50;
    /** The pilot may start only with |pitch| and |roll| under this, in degrees (reference EA:3741). */
    public static final float MAX_START_TILT = 3.0F;
    /** ... and having moved less than this since the trail's oldest position (reference EA:3743). */
    public static final double MAX_START_DRIFT = 0.3;
    /** A slider lets go this far past the rope's end (reference {@code MCH_EntityHide}:233, {@code + 5.0F}). */
    public static final float SLIDER_SLACK = 5.0F;

    /**
     * Reference {@code onUpdate_Repelling} (EA:1831-1844), the rope length's step, on either side. While repelling the
     * ropes grow by 0.25 on the server and 0.3 on a client a tick, until they are 50 long or reach the altitude; a
     * config with hooks that is not repelling has them pulled in (0); one without hooks keeps its value.
     *
     * @param len       the rope length so far (0 or negative, {@code ropesLength})
     * @param hasHooks  the config has {@code AddRepellingHook} lines
     * @param repelling the synced repelling flag
     * @param alt       {@code getAlt} at the vehicle (the caller probes only while {@code hasHooks && repelling})
     * @param client    this is a client ({@code worldObj.isRemote})
     * @return the new length
     */
    public static float ropeStep(float len, boolean hasHooks, boolean repelling, int alt, boolean client) {
        if (!hasHooks) {
            return len;
        }
        if (!repelling) {
            return 0.0F;
        }
        if (len > -MAX_ROPE && len > -alt) {
            // Reference: (float)(ropesLength - (isRemote ? 0.3F : 0.25)), the float step widened to a double.
            return (float) (len - (client ? (double) CLIENT_ROPE_STEP : SERVER_ROPE_STEP));
        }
        return len;
    }

    /** Reference {@code onUpdate_UnmountCrewRepelling} (EA:1849-1852), both sides: the counter is 0 while not
     *  repelling and counts up to {@link #ROPE_OUT_TICKS}, where it stays. */
    public static int tickStep(boolean repelling, int t) {
        if (!repelling) {
            return 0;
        }
        return t < ROPE_OUT_TICKS ? t + 1 : t;
    }

    /** Reference EA:1853: the server's mob cadence runs on this tick, the branch the counter takes once it has reached
     *  {@link #ROPE_OUT_TICKS} (read it BEFORE {@link #tickStep}): from the 61st tick after the start on. */
    public static boolean cadenceOpen(boolean repelling, int t) {
        return repelling && t >= ROPE_OUT_TICKS;
    }

    /** Reference {@code canRepelling} (EA:1827-1829): a player may slide down a rope, from the 51st tick on. */
    public static boolean canRepelling(boolean repelling, int t) {
        return repelling && t > SLIDE_AFTER_TICKS;
    }

    /**
     * Reference {@code unmount} (EA:1939), the player rope slide's round robin: the rope after {@code last}, wrapping
     * to 0. {@code last} is the server's {@code lastUsedRopeIndex}, which the mob cadence also writes (EA:1869), so a
     * player takes the rope after the last one used by anyone.
     *
     * @param last  the rope used last, 0 before any (reference EA:222)
     * @param hooks the config's hook count, at least 1 (the caller checks {@code haveRepellingHook})
     */
    public static int nextRope(int last, int hooks) {
        return (last + 1) % hooks;
    }

    /**
     * Reference {@code canStartRepelling} (EA:3740-3749): hooks, hovering (hover mode or gunner mode, EA:5345-5347), the
     * hull level within 3 degrees on both axes, and (nearly) still: less than 0.3 from the trail's oldest position, all
     * strict.
     *
     * @param hasHooks the config has repelling hooks
     * @param hovering the vehicle's {@code isHovering()}
     * @param pitch    the hull pitch ({@code getRotPitch})
     * @param roll     the hull roll ({@code getRotRoll})
     * @param drift    {@code |prevPosition.oldest() - pos|}
     */
    public static boolean canStartRepelling(boolean hasHooks, boolean hovering, float pitch, float roll, double drift) {
        return hasHooks && hovering && Math.abs(pitch) < MAX_START_TILT && Math.abs(roll) < MAX_START_TILT
            && drift < MAX_START_DRIFT;
    }

    /** Reference EA:1856, {@code getCountOnUpdate() % hook.interval == 0}: a hook lowers a mob on this tick. The
     *  interval is parsed at least 1 ({@code toInt(s[3], 1, 100000)}). */
    public static boolean fires(int count, int interval) {
        return count % interval == 0;
    }

    /**
     * Reference {@code MCH_EntityHide.onUpdateServer} (:231-235): the slider lets its rider go at the rope's end, once
     * it is more than the server's rope length + 5 from the hook, strictly.
     *
     * @param refPosY   the slider's 1.7.10 {@code posY} (its box bottom + 0.5)
     * @param ropeY     the hook's world y ({@code getRopePos(idx).yCoord})
     * @param serverLen the server's rope length
     */
    public static boolean sliderReleased(double refPosY, double ropeY, float serverLen) {
        return Math.abs(refPosY - ropeY) > Math.abs(serverLen) + SLIDER_SLACK;
    }

    /**
     * The pilot's free-look toggle after a press of the free-look key (reference MCH_AircraftClientTickHandler:156-158
     * sends {@code switchFreeLook = isFreeLookMode() ? 2 : 1}, and MCH_HeliPacketHandler:117-118 sets the bit to
     * {@code switchFreeLook == 1}): the opposite of the free look in effect, which repelling forces on
     * ({@code isFreeLookMode}, EA:5377-5379). So outside repelling a press flips the toggle, and a press while repelling
     * always turns it off: when the ropes come in the view snaps back onto the hull.
     */
    public static boolean freeLookAfterKey(boolean toggle, boolean repelling) {
        return !(toggle || repelling);
    }
}
