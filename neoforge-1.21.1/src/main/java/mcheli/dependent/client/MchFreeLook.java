package mcheli.dependent.client;

/**
 * Client-only pilot free-look state — the 1.21.1 analogue of the reference free-look bit ({@code isFreeLookMode}). While
 * active, the pilot's mouse sweeps the camera/head around the cockpit WITHOUT turning the airframe, which holds its
 * heading (and keeps auto-leveling / air-banking with A/D).
 *
 * <p>A single client static suffices because the port's PILOT airframe rotation is client-authoritative
 * ({@link MchClientRotation} ships the heading to the server, which does not lerp an owned vehicle back): simply not
 * steering the hull with the mouse is enough to hold the heading — the server needs no free-look state. Read by
 * {@link MchClientRotation} (hold heading + skip the camera weld), {@code CameraMixin} (don't weld the camera to the
 * hull) and {@code MchHudVarState} (the {@code FREE LOOK} cue). Toggled by the free-look key; cleared on
 * dismount / vehicle-change (via {@link MchClientRotation}'s reset). Gunner free-look is a separate concern — a port
 * gunner already free-looks by default.
 */
public final class MchFreeLook {
    private MchFreeLook() {}

    private static boolean active;

    /** Whether the local pilot is currently free-looking. */
    public static boolean active() { return active; }

    /** Flip free-look on/off (edge-triggered by the toggle key). */
    public static void toggle() { active = !active; }

    /** Force free-look off — on dismount, vehicle change, or losing the pilot seat. */
    public static void clear() { active = false; }
}
