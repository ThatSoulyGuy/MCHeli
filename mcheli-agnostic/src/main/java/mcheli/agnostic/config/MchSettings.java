package mcheli.agnostic.config;

/**
 * The plain-value slice of the reference's global {@code MCH_Config} ({@code mcheli.cfg}) that AGNOSTIC code reads.
 *
 * <p>The dependent layer owns the real configuration (NeoForge {@code ModConfigSpec}, see
 * {@code mcheli.dependent.config.MchConfig}) and {@link #install installs} a snapshot here before the vehicle
 * definitions load; agnostic code only ever calls {@link #current()}. Until something is installed the
 * {@link #DEFAULTS} apply, which are the reference defaults, so a unit test (or any run that never installs) behaves
 * exactly like a stock {@code mcheli.cfg}.
 *
 * <p>This replaces the two 1.7.10 leftovers this package used to carry: {@code MCH_ConfigPrm} (the hand-rolled
 * typed config slot + text parser, now done by {@code ModConfigSpec}) and {@code MCH_ServerSettings} (the client-side
 * mirror of server-pushed settings, now done by NeoForge's server-config sync). Runtime gameplay options are NOT
 * mirrored here: agnostic sims/weapons receive those as parameters (e.g. {@code ControlInput.autoThrottleDown},
 * {@code WeaponSlot}'s {@code infinityAmmo}), so only values consumed while PARSING definitions live in this record.
 *
 * @param allHeliSpeed  reference {@code AllHeliSpeed} (MCH_Config:309), multiplied into every helicopter's
 *                      {@code Speed} at load (reference {@code MCH_HeliInfo.isValidData}:26)
 * @param allPlaneSpeed reference {@code AllPlaneSpeed} (MCH_Config:310), multiplied into every plane's {@code Speed}
 *                      and {@code SweepWingSpeed} (reference {@code MCP_PlaneInfo.isValidData}:79-80)
 * @param allTankSpeed  reference {@code AllTankSpeed} (MCH_Config:311), multiplied into every tank's {@code Speed}
 *                      (reference {@code MCH_TankInfo.isValidData}:68). The reference has no vehicle multiplier.
 */
public record MchSettings(double allHeliSpeed, double allPlaneSpeed, double allTankSpeed) {

    /** Reference {@code correctionParameter} clamp for the speed multipliers (MCH_Config:506-508). */
    public static final double MIN_SPEED_MULTIPLIER = 0.0;
    public static final double MAX_SPEED_MULTIPLIER = 1000.0;

    /** The reference defaults (MCH_Config:309-311). */
    public static final MchSettings DEFAULTS = new MchSettings(1.0, 1.0, 1.0);

    private static volatile MchSettings current = DEFAULTS;

    public MchSettings {
        allHeliSpeed = clampSpeed(allHeliSpeed);
        allPlaneSpeed = clampSpeed(allPlaneSpeed);
        allTankSpeed = clampSpeed(allTankSpeed);
    }

    /** The installed settings, or {@link #DEFAULTS} if none were installed. Never null. */
    public static MchSettings current() {
        return current;
    }

    /**
     * Install the settings agnostic code reads from now on ({@code null} restores the defaults). The dependent layer
     * calls this once its config is loaded and BEFORE loading definitions, since the speed multipliers are baked into
     * each definition at parse time, like the reference.
     */
    public static void install(MchSettings settings) {
        current = settings != null ? settings : DEFAULTS;
    }

    /** Reference {@code MCH_Lib.RNG(v, 0.0, 1000.0)}; a NaN (unparseable in the reference) falls back to 1.0. */
    private static double clampSpeed(double v) {
        if (Double.isNaN(v)) {
            return 1.0;
        }
        return v < MIN_SPEED_MULTIPLIER ? MIN_SPEED_MULTIPLIER : Math.min(v, MAX_SPEED_MULTIPLIER);
    }
}
