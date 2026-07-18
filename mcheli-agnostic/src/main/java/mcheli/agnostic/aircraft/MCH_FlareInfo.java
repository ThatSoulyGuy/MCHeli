package mcheli.agnostic.aircraft;

/**
 * Pure flare/countermeasure state machine + volley-spread math — the agnostic half of the reference {@code MCH_Flare}
 * (the entity spawning, particle trail, and the missile-decoy scan stay in the dependent layer). ZERO Minecraft
 * imports, so the dispense timing and per-type ejection geometry are unit-testable without a world.
 *
 * <p>The port runs this SERVER-side only (flares are server-authoritative entities synced to clients), so the
 * reference's {@code +50} client-side {@code tickWait} delay — a 1.7.10 client-spawn artifact — is intentionally
 * dropped.
 */
public final class MCH_FlareInfo {

    /** Decoy + RWR search radius (reference {@code MCH_MissileDetector.SEARCH_RANGE}). */
    public static final int SEARCH_RANGE = 60;

    /** One flare type's schedule (reference {@code MCH_Flare.FlareParam}). {@code num} = flares per volley,
     *  {@code interval} = ticks between volleys, {@code tickWait} = total window, {@code tickEnable} = the leading
     *  portion of that window in which the decoy is active, {@code numFlareMax} = max volleys. */
    public static final class FlareParam {
        public final int num;
        public final int interval;
        public final int tickWait;
        public final int tickEnable;
        public final int numFlareMax;
        FlareParam(int num, int interval, int tickWait, int tickEnable, int numFlareMax) {
            this.num = num;
            this.interval = interval;
            this.tickWait = tickWait;
            this.tickEnable = tickEnable;
            this.numFlareMax = numFlareMax;
        }
    }

    // reference FLARE_DATA table (MCH_Flare.java:33-43); 0/6/7/8/9 alias type 1.
    private static final FlareParam[] DATA = buildTable();

    private static FlareParam[] buildTable() {
        FlareParam[] d = new FlareParam[11];
        d[1] = new FlareParam(1, 3, 200, 100, 16);
        d[2] = new FlareParam(3, 5, 300, 200, 16);
        d[3] = new FlareParam(2, 3, 200, 100, 16);
        d[4] = new FlareParam(1, 3, 200, 100, 16);
        d[5] = new FlareParam(2, 3, 200, 100, 16);
        d[10] = new FlareParam(8, 1, 250, 60, 1);
        d[0] = d[6] = d[7] = d[8] = d[9] = d[1];
        return d;
    }

    /** The schedule for a (clamped/aliased) flare type; never null. */
    public static FlareParam params(int type) {
        return (type >= 0 && type < DATA.length && DATA[type] != null) ? DATA[type] : DATA[1];
    }

    // --- live dispense state (reference MCH_Flare.tick/numFlare/flareType) ---
    private int tick;       // counts DOWN from tickWait
    private int numFlare;   // VOLLEYS emitted so far (each emits `num` entities)
    private int flareType;

    /** Begin a dispense of {@code type} (reference {@code use}). */
    public void use(int type) {
        this.flareType = type;
        this.tick = params(type).tickWait;
        this.numFlare = 0;
    }

    /** Advance the window one tick (reference {@code update}: {@code if (tick > 0) tick--}). Call once per server tick. */
    public void tickDown() {
        if (this.tick > 0) {
            this.tick--;
        }
    }

    /** True this tick if a volley should be emitted now (reference: {@code tick > 0 && tick % interval == 0 &&
     *  numFlare < numFlareMax}). The caller emits {@link #volleyNum()} flares then calls {@link #onVolleySpawned()}. */
    public boolean shouldSpawnVolley() {
        FlareParam p = params(this.flareType);
        return this.tick > 0 && this.tick % p.interval == 0 && this.numFlare < p.numFlareMax;
    }

    public void onVolleySpawned() {
        this.numFlare++;
    }

    /** The decoy-active window: the FIRST {@code tickEnable} ticks after a dispense (reference {@code isUsing}). */
    public boolean isUsing() {
        FlareParam p = params(this.flareType);
        return this.tick != 0 && this.tick > p.tickWait - p.tickEnable;
    }

    /** A dispense window is running (reference {@code isInPreparation}). */
    public boolean isPreparing() {
        return this.tick != 0;
    }

    /** Ready to dispense again only once the whole window has elapsed (reference {@code canUseFlare}: tick == 0). */
    public boolean canUseFlare() {
        return this.tick == 0;
    }

    public int flareType() { return this.flareType; }

    /** Flares per volley for the current type. */
    public int volleyNum() { return params(this.flareType).num; }

    /** The airburst fuse for the current type (type 10 = timed ring pop at 10 ticks; others live out their lifetime). */
    public int fuseCount() { return this.flareType == 10 ? 10 : 0; }

    /** HUD cooldown fraction 1..0 (1 = just fired, 0 = ready again). */
    public float cooldownFraction() {
        FlareParam p = params(this.flareType);
        return p.tickWait > 0 ? (float) this.tick / p.tickWait : 0.0F;
    }

    public void reset() {
        this.tick = 0;
        this.numFlare = 0;
        this.flareType = 0;
    }

    /**
     * Per-type ejection velocity for flare index {@code i} of a {@code num}-flare volley (reference
     * {@code MCH_Flare.spawnFlare}), given the aircraft's velocity + heading and three random rolls in [0,1). Returns
     * {@code {vx, vy, vz}} already scaled by the reference's final {@code *0.5}. The aircraft velocity is folded in for
     * every type except the type-10 ring (which ejects in a fixed circle).
     */
    public static double[] volleyMotion(int type, int i, int num, double motX, double motY, double motZ,
                                        double yawDeg, float r0, float r1, float r2) {
        double tx = 0.0;
        double ty = motY;
        double tz = 0.0;
        double r = yawDeg;
        double d2r = Math.PI / 180.0;
        if (type == 1) {
            // reference: sin(rand*360) / cos(rand*360) with TWO independent rand draws (radians — a scrambled random
            // direction), so tx and tz are independent (NOT on the unit circle).
            tx = Math.sin(r0 * 360.0);
            tz = Math.cos(r1 * 360.0);
        } else if (type == 2 || type == 3) {
            if (i == 0) {
                r += 90.0;
            } else if (i == 1) {
                r -= 90.0;
            } else if (i == 2) {
                r += 180.0;
            }
            r *= d2r;
            tx = -Math.sin(r) + (r1 - 0.5) * 0.6;
            tz = Math.cos(r) + (r2 - 0.5) * 0.6;
        } else if (type == 4) {
            r *= d2r;
            tx = -Math.sin(r) + (r1 - 0.5) * 1.3;
            tz = Math.cos(r) + (r2 - 0.5) * 1.3;
        } else if (type == 5) {
            r *= d2r;
            tx = (-Math.sin(r) + (r1 - 0.5) * 0.9) * 0.3;
            tz = (Math.cos(r) + (r2 - 0.5) * 0.9) * 0.3;
        }
        tx += motX;
        ty += motY / 2.0;
        tz += motZ;
        if (type == 10) {
            double rr = (yawDeg + 360.0 / num / 2.0 + i * (360.0 / num)) * d2r;
            tx = -Math.sin(rr) * 2.0;
            tz = Math.cos(rr) * 2.0;
            ty = 0.7;
        }
        return new double[] {tx * 0.5, ty * 0.5, tz * 0.5};
    }

    /** Whether type 4 uses the floaty physics override (reduced gravity + higher drag). */
    public static boolean isFloatyType(int type) {
        return type == 4;
    }
}
