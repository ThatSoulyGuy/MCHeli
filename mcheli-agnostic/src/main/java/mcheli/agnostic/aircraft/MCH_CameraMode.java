package mcheli.agnostic.aircraft;

/**
 * The camera-mode rules of the reference {@code MCH_Camera} (night vision / thermal vision), without the potion and
 * shader side effects the dependent layer performs. ZERO Minecraft imports.
 */
public final class MCH_CameraMode {
    private MCH_CameraMode() {}

    public static final int NORMAL = 0;
    public static final int NIGHT_VISION = 1;
    public static final int THERMAL_VISION = 2;

    /** Seats that own a camera mode (reference {@code mode = new int[]{0, 0}}, {@code isValidUid}): the pilot and
     *  seat 1 only. Every other seat reads {@link #NORMAL} and cannot switch. */
    public static final int SEATS = 2;

    public static boolean isValidSeat(int seat) {
        return seat >= 0 && seat < SEATS;
    }

    /** Reference {@code getModeNum}: 3 modes with post-shader support, 2 without (thermal needs the shader). */
    public static int modeCount(boolean shaderSupport) {
        return shaderSupport ? 3 : 2;
    }

    /** Reference {@code setMode}: a negative mode is 0, anything else wraps by {@link #modeCount}. */
    public static int normalize(int mode, boolean shaderSupport) {
        return mode < 0 ? 0 : mode % modeCount(shaderSupport);
    }

    /** Reference {@code switchCameraMode(player)}: {@code setMode(getMode + 1)}. */
    public static int next(int mode, boolean shaderSupport) {
        return normalize(mode + 1, shaderSupport);
    }

    /** Reference {@code getModeName}: the caption for a mode ("" for normal). */
    public static String name(int mode) {
        return mode == NIGHT_VISION ? "NIGHT VISION" : mode == THERMAL_VISION ? "THERMAL VISION" : "";
    }

    /** Reference {@code MCH_Camera.updateViewer}: in both vision modes the viewer is kept under a 250-tick night-vision
     *  potion, and when the mode returns to normal a short one (0 < duration < 500, i.e. one this camera applied) is
     *  removed. These two return whether to act for the given state.
     *
     *  <p>The reference re-applied the potion every tick (its gate, {@code duration < 500}, is always true for a
     *  250-tick potion), which in 1.21 sends an effect packet every tick. This tops it up only once it is down to
     *  {@link #NIGHT_VISION_REFRESH} ticks — before vanilla's fade-out flicker starts at 200 — so the view is identical
     *  and a longer potion the player drank is left alone exactly as before. */
    public static boolean shouldApplyNightVision(int mode, int currentDuration) {
        return (mode == NIGHT_VISION || mode == THERMAL_VISION) && currentDuration <= NIGHT_VISION_REFRESH;
    }

    /** Remaining duration at which the camera tops its night vision back up (see {@link #shouldApplyNightVision}). */
    public static final int NIGHT_VISION_REFRESH = 210;

    /** See {@link #shouldApplyNightVision}; {@code currentDuration} is 0 when the viewer has no night vision. */
    public static boolean shouldRemoveNightVision(int mode, int lastMode, int currentDuration) {
        return mode == NORMAL && lastMode != NORMAL && currentDuration > 0 && currentDuration < 500;
    }

    /** Duration of the night vision the camera applies (reference {@code new PotionEffect(nightVision, 250, 0, true)}). */
    public static final int NIGHT_VISION_TICKS = 250;

    /**
     * Is a viewer's night-vision effect the one a camera applied, so a camera may take it away again? The camera's
     * effect is the only one with its signature: ambient, no particles, amplifier 0 and at most
     * {@link #NIGHT_VISION_TICKS} left. A drunk, splashed or tipped-arrow potion is not ambient and shows particles, and
     * {@code /effect} is not ambient either, so none of them is ever stripped (the reference's bare {@code duration < 500}
     * test also took a short potion). A potion that was already within {@link #NIGHT_VISION_REFRESH} ticks of running
     * out when the camera topped it up becomes the camera's effect (the game merges the two), and goes with it.
     */
    public static boolean isCameraNightVision(boolean ambient, boolean showsParticles, int amplifier, int duration) {
        return ambient && !showsParticles && amplifier == 0 && duration > 0 && duration <= NIGHT_VISION_TICKS;
    }
}
