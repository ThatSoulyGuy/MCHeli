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
        float power = this.vehicle.getEnginePower();
        this.volume = power * 0.75F;        // silent when off, up to 0.75 at full throttle
        this.pitch = 0.7F + power * 0.55F;  // spools the pitch up with throttle
    }
}
