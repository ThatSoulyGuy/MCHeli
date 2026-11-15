package mcheli.dependent.client;

import mcheli.agnostic.vehicle.Repelling;
import mcheli.dependent.entity.AbstractMchVehicle;

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
 * gunner already free-looks by default. The readers ask {@link #effective}, which adds rope repelling: the reference
 * {@code isFreeLookMode} was the free-look bit OR {@code isRepelling()} (EA:5377-5379).
 */
public final class MchFreeLook {
    private MchFreeLook() {}

    private static boolean active;

    /** Whether the local pilot has toggled free-look on. */
    public static boolean active() { return active; }

    /**
     * Whether the pilot of {@code v} free-looks now: the toggle, or {@code v} repelling (reference
     * {@code isFreeLookMode}, EA:5377-5379). While the ropes are down the mouse no longer turns the hull
     * ({@code setAngles} zeroes it, EA:1164-1167) and the view looks around freely; it all snaps back when repelling
     * stops. The repelling flag is synced, so this needs nothing from the server; the toggle itself is left as it was.
     */
    public static boolean effective(AbstractMchVehicle v) {
        return active || v != null && v.isRepelling();
    }

    /**
     * The free-look key in {@code v} (edge-triggered): the toggle becomes the opposite of the free look in effect
     * ({@link Repelling#freeLookAfterKey}, the reference {@code isFreeLookMode() ? 2 : 1}), so outside repelling it
     * flips, and a press while {@code v} repels turns it off, so the view snaps back when the ropes come in.
     */
    public static void press(AbstractMchVehicle v) {
        active = Repelling.freeLookAfterKey(active, v != null && v.isRepelling());
    }

    /** Force free-look off — on dismount, vehicle change, or losing the pilot seat. */
    public static void clear() { active = false; }
}
