package mcheli.dependent.client;

import mcheli.dependent.entity.AbstractMchVehicle;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * A looping engine drone that follows a vehicle, its volume and pitch driven by the synced engine power (spooled
 * 0..1). It fades in as the throttle spools up and goes silent when the engine winds down, and self-stops when the
 * vehicle is removed. Client-only.
 */
public class VehicleEngineSound extends AbstractTickableSoundInstance {
    private final AbstractMchVehicle vehicle;

    public VehicleEngineSound(AbstractMchVehicle vehicle, SoundEvent event) {
        super(event, SoundSource.NEUTRAL, RandomSource.create());
        this.vehicle = vehicle;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.0F;
        this.pitch = 1.0F;
        this.x = vehicle.getX();
        this.y = vehicle.getY();
        this.z = vehicle.getZ();
    }

    public int vehicleId() {
        return this.vehicle.getId();
    }

    /**
     * CRITICAL: a looping engine sound starts at volume 0 (silent at rest) and fades in via {@link #tick()}. Minecraft's
     * {@code SoundEngine.play} SKIPS any sound whose volume is 0 at play time ("volume was zero") unless it opts in here —
     * without this override the drone is culled at creation and its {@code tick()} never runs, so it never fades in and
     * you hear nothing. This is the mechanism vanilla's own looping sounds (minecart, beacon) use.
     */
    @Override
    public boolean canStartSilent() {
        return true;
    }

    public void forceStop() {
        this.stop();
    }

    @Override
    public void tick() {
        if (this.vehicle.isRemoved() || !this.vehicle.isAlive()) {
            this.stop();
            return;
        }
        this.x = this.vehicle.getX();
        this.y = this.vehicle.getY();
        this.z = this.vehicle.getZ();
        // Per-type volume/pitch from the reference (heli rotor / jet spool / tank engine), scaled by the config
        // SoundVolume/SoundPitch — replaces the old generic 0.75/0.55 curve.
        this.volume = Math.max(0.0F, this.vehicle.engineSoundVolume());
        this.pitch = Math.max(0.05F, this.vehicle.engineSoundPitch());
    }
}
