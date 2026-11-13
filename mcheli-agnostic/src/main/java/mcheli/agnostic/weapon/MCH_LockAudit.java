package mcheli.agnostic.weapon;

/**
 * The server's check on a client-built missile lock, one per seat. The lock itself is built on the client, as in the
 * reference ({@code MCH_WeaponGuidanceSystem.lock} returns false when {@code !isRemote}), and the client ships only the
 * completed target id. The reference server fired at whatever id arrived ({@code MCH_WeaponAAMissile.shot}:
 * {@code getEntityByID(prm.option1)} plus a dead check); this port deliberately re-checks the claim so a modified client
 * cannot fire a homing missile at an arbitrary loaded entity.
 *
 * <p>What the server can observe, and what is checked against it:
 * <ul>
 *   <li><b>The candidate and when its lock began.</b> The client's RWR notice ({@code notify_lock}) names the entity it is
 *       locking from the first tick of the lock on ({@link #observeCandidate}). A fire claim must name that entity, and
 *       at least {@link #minLockAge} ticks (the lock time less a jitter slack that scales with it) must have passed
 *       since its first notice.</li>
 *   <li><b>Seeker cone, range and line of sight.</b> Each server tick the owner samples the candidate against the
 *       operator's look ({@link #sample}): the target must have been inside the range and a widened acquisition cone
 *       (server rotation and positions lag the client's by a few ticks) with a clear line of sight at least once, as on
 *       the reference's acquisition, and inside the widened hold cone within the reference's hold window
 *       ({@code continueLockCount} = {@code min(lockCountMax / 3, 20)} ticks) plus {@link #LATENCY_SLACK} before the
 *       shot.</li>
 *   <li><b>The target's stealth</b> ({@code Stealth}, 0..1) scales all of it as the reference's lock does
 *       ({@code MCH_WeaponGuidanceSystem.lock} :142-145, :181-190, {@code getLockCountMax} :65; the scalers are
 *       {@link MCH_LockGeometry}): the range shrinks ({@link #serverLockRange}), the acquisition cone narrows while the
 *       hold cone does not ({@link #serverAcquireConeAngle}), and the owner passes the stealth-lengthened lock time to
 *       {@link #plausible}.</li>
 * </ul>
 * The owner also uses {@link #allowRelay} to rate-limit the RWR warning it relays to the target's crew. ZERO Minecraft
 * imports; the dependent vehicle supplies the world queries.
 */
public final class MCH_LockAudit {

    /** The most ticks a lock may complete early by on the server's clock (packet jitter between the notice and the
     *  claim); a short lock gets a third of its lock time instead ({@link #minLockAge}). */
    public static final int TIME_SLACK = 10;
    /** Ticks of network latency allowed between the client's last in-cone tick and the server seeing the shot. */
    public static final int LATENCY_SLACK = 10;
    /** A notice older than this starts a new candidacy even for the same entity (the client re-notifies every 15). */
    public static final int CANDIDATE_TIMEOUT = 40;
    /** Minimum ticks between two relayed RWR warnings from one seat (the client paces them at 15). */
    public static final int RELAY_INTERVAL = 10;
    /** Degrees added to the seeker cone for the server's lagging view of the operator's look and the target. */
    public static final float CONE_SLACK_DEG = 10.0F;

    private int candidate = -1;
    private long firstSeen;
    private long lastNotice = Long.MIN_VALUE / 2;
    private long lastInCone = Long.MIN_VALUE / 2;
    private boolean sawWithSight;
    private long lastRelay = Long.MIN_VALUE / 2;

    /** The entity the client last said it is locking, or -1. */
    public int candidate() {
        return this.candidate;
    }

    /** Record a lock notice for {@code targetId} at server tick {@code now}. A new entity, or a notice after a long gap,
     *  starts a fresh candidacy. */
    public void observeCandidate(int targetId, long now) {
        if (targetId < 0) {
            return;
        }
        if (targetId != this.candidate || now - this.lastNotice > CANDIDATE_TIMEOUT) {
            this.candidate = targetId;
            this.firstSeen = now;
            this.lastInCone = Long.MIN_VALUE / 2;
            this.sawWithSight = false;
        }
        this.lastNotice = now;
    }

    /** One server-tick sample of the candidate: inside the range and the widened hold cone ({@code inRangeAndCone}),
     *  and whether it was also inside the widened acquisition cone with a clear line of sight ({@code acquiredInSight};
     *  only consulted until that has been seen once). The acquisition cone is never wider than the hold cone. */
    public void sample(long now, boolean inRangeAndCone, boolean acquiredInSight) {
        if (inRangeAndCone) {
            this.lastInCone = now;
            if (acquiredInSight) {
                this.sawWithSight = true;
            }
        }
    }

    /** Whether {@link #sample} still needs a line-of-sight test (the reference tests sight on acquisition only). */
    public boolean needsSightCheck() {
        return !this.sawWithSight;
    }

    /**
     * Is a fire claim on {@code claimedId} at tick {@code now} consistent with a lock of {@code lockCountMax} ticks the
     * server watched build? {@code lockCountMax} is the target's lock time, stealth included
     * ({@code MCH_LockGeometry.effectiveCountMax}, reference {@code getLockCountMax}), which also sizes the hold window.
     */
    public boolean plausible(int claimedId, long now, int lockCountMax) {
        if (claimedId < 0 || claimedId != this.candidate || !this.sawWithSight) {
            return false;
        }
        int max = Math.max(1, lockCountMax);
        if (now - this.firstSeen < minLockAge(max)) {
            return false;
        }
        int hold = Math.min(max / 3, 20);
        return now - this.lastInCone <= hold + LATENCY_SLACK;
    }

    /**
     * The youngest, on the server's clock, that a lock of {@code lockCountMax} ticks may be when its claim is accepted:
     * the lock time less a slack for packet jitter of a third of the lock time (at least one tick, at most
     * {@link #TIME_SLACK}), and never under one tick, so a notice and a claim that arrive together never pass. A fixed
     * slack would leave a short lock (the bundled seekers start at 10 ticks) with no minimum at all. A real client
     * claims {@code lockCountMax - 1} ticks after its first notice (the notice goes out on the lock's first tick,
     * MchClientInput), so on an even link it is never held back; a jittered claim that comes in early only waits out
     * the difference, because the claim is re-checked every tick the trigger stays down.
     */
    public static int minLockAge(int lockCountMax) {
        int max = Math.max(1, lockCountMax);
        int slack = Math.max(1, Math.min(TIME_SLACK, max / 3));
        return Math.max(1, max - slack);
    }

    /** Rate limit for the RWR relay: true (and recorded) at most once every {@link #RELAY_INTERVAL} ticks. */
    public boolean allowRelay(long now) {
        if (now - this.lastRelay < RELAY_INTERVAL) {
            return false;
        }
        this.lastRelay = now;
        return true;
    }

    /** The widened cone half-angle the server samples with (see the class javadoc): the seeker angle, plus
     *  {@link #CONE_SLACK_DEG}, plus the angle a target moving {@code targetSpeed} blocks/tick covers in five ticks at
     *  {@code distance} blocks. */
    public static float serverConeAngle(float lockAngle, double targetSpeed, double distance) {
        double lead = Math.toDegrees(Math.atan2(targetSpeed * 5.0, Math.max(distance, 1.0)));
        return (float) (lockAngle + CONE_SLACK_DEG + lead);
    }

    /** The widened ACQUISITION cone half-angle: {@link #serverConeAngle} of the seeker cone narrowed for the target's
     *  stealth ({@link MCH_LockGeometry#effectiveAngle}), the cone the reference acquires in ({@code lock()} :144). The
     *  reference holds a lock in the plain {@code lockAngle} cone (:190), which {@link #serverConeAngle} widens. */
    public static float serverAcquireConeAngle(float lockAngle, float targetStealth, double targetSpeed,
                                               double distance) {
        return serverConeAngle(MCH_LockGeometry.effectiveAngle(lockAngle, targetStealth), targetSpeed, distance);
    }

    /** The lock range the server allows: the seeker range shortened for the target's stealth
     *  ({@link MCH_LockGeometry#effectiveRange}, reference {@code lock()} :143, :182) plus 10 % and 4 blocks for the
     *  positions the server and client disagree on. A target no seeker can lock at any range (stealth 1) gets 0: the
     *  reference's {@code d < range * range} then never holds. */
    public static double serverLockRange(double lockRange, float targetStealth) {
        double range = MCH_LockGeometry.effectiveRange(lockRange, targetStealth);
        return range > 0.0 ? range * 1.1 + 4.0 : 0.0;
    }
}
