package mcheli.dependent.client;

import mcheli.dependent.control.ClientboundRwrPayload;
import mcheli.dependent.registry.MchSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * CLIENT-ONLY handler for the RWR cockpit tone (reference {@code locked} / {@code alert}). Plays a non-positional UI
 * sound the local pilot hears in the cockpit, rate-limited per kind so a stream of warning packets becomes a steady
 * beep rather than a buzz. Loaded lazily (only when a clientbound {@link ClientboundRwrPayload} arrives on the client).
 */
public final class MchRwrClient {

    private MchRwrClient() {}

    private static final int COOLDOWN_TICKS = 12; // min spacing between plays of the SAME kind
    private static int lastLockedTick = -1000;
    private static int lastAlertTick = -1000;

    /** Suppress only inside the FORWARD cooldown window. {@code mc.player.tickCount} restarts at 0 whenever the
     *  LocalPlayer is recreated (respawn / dimension change / reconnect), so a negative delta means the clock reset —
     *  play through it rather than muting the safety tone for minutes until the counter re-climbs. */
    private static boolean inCooldown(int now, int last) {
        int delta = now - last;
        return delta >= 0 && delta < COOLDOWN_TICKS;
    }

    public static void play(byte kind) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        int now = mc.player.tickCount;
        SoundEvent sound;
        if (kind == ClientboundRwrPayload.ALERT) {
            if (inCooldown(now, lastAlertTick)) {
                return;
            }
            lastAlertTick = now;
            sound = MchSounds.byName("alert");
        } else {
            if (inCooldown(now, lastLockedTick)) {
                return;
            }
            lastLockedTick = now;
            sound = MchSounds.byName("locked");
        }
        if (sound != null) {
            // A non-positional cockpit tone the local pilot always hears (reference RWR sound-FX path).
            mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0F));
        }
    }
}
