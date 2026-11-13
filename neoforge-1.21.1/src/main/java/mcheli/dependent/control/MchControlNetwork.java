package mcheli.dependent.control;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers the MCHeli network payloads. Wired onto the mod event bus from {@link mcheli.MCHeli}. Version "1" is the
 * protocol version; bump it on a wire-incompatible change.
 */
public final class MchControlNetwork {
    private MchControlNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(
            ServerboundControlPayload.TYPE,
            ServerboundControlPayload.STREAM_CODEC,
            ServerboundControlPayload::handle);
        registrar.playToServer(
            ServerboundRotationPayload.TYPE,
            ServerboundRotationPayload.STREAM_CODEC,
            ServerboundRotationPayload::handle);
        registrar.playToServer(
            ServerboundWeaponSwitchPayload.TYPE,
            ServerboundWeaponSwitchPayload.STREAM_CODEC,
            ServerboundWeaponSwitchPayload::handle);
        registrar.playToServer(
            ServerboundWeaponModePayload.TYPE,
            ServerboundWeaponModePayload.STREAM_CODEC,
            ServerboundWeaponModePayload::handle);
        registrar.playToServer(
            ServerboundVehicleGuiPayload.TYPE,
            ServerboundVehicleGuiPayload.STREAM_CODEC,
            ServerboundVehicleGuiPayload::handle);
        registrar.playToServer(
            ServerboundSeatSwitchPayload.TYPE,
            ServerboundSeatSwitchPayload.STREAM_CODEC,
            ServerboundSeatSwitchPayload::handle);
        registrar.playToServer(
            ServerboundGunnerModePayload.TYPE,
            ServerboundGunnerModePayload.STREAM_CODEC,
            ServerboundGunnerModePayload::handle);
        registrar.playToServer(
            ServerboundCameraModePayload.TYPE,
            ServerboundCameraModePayload.STREAM_CODEC,
            ServerboundCameraModePayload::handle);
        registrar.playToServer(
            ServerboundFoldBladePayload.TYPE,
            ServerboundFoldBladePayload.STREAM_CODEC,
            ServerboundFoldBladePayload::handle);
        registrar.playToServer(
            ServerboundHatchPayload.TYPE,
            ServerboundHatchPayload.STREAM_CODEC,
            ServerboundHatchPayload::handle);
        registrar.playToServer(
            ServerboundUseFlarePayload.TYPE,
            ServerboundUseFlarePayload.STREAM_CODEC,
            ServerboundUseFlarePayload::handle);
        registrar.playToServer(
            ServerboundVtolPayload.TYPE,
            ServerboundVtolPayload.STREAM_CODEC,
            ServerboundVtolPayload::handle);
        registrar.playToServer(
            ServerboundHoverPayload.TYPE,
            ServerboundHoverPayload.STREAM_CODEC,
            ServerboundHoverPayload::handle);
        registrar.playToServer(
            ServerboundGearPayload.TYPE,
            ServerboundGearPayload.STREAM_CODEC,
            ServerboundGearPayload::handle);
        registrar.playToServer(
            ServerboundNotifyLockPayload.TYPE,
            ServerboundNotifyLockPayload.STREAM_CODEC,
            ServerboundNotifyLockPayload::handle);
        // The port's first CLIENTBOUND payload — the RWR cockpit warning tone (handler is Dist-guarded).
        registrar.playToClient(
            ClientboundRwrPayload.TYPE,
            ClientboundRwrPayload.STREAM_CODEC,
            ClientboundRwrPayload::handle);
        // Client-built effects: the explosion visual and the ejected shell casing (handlers are Dist-guarded).
        registrar.playToClient(
            mcheli.dependent.particle.ClientboundExplosionFxPayload.TYPE,
            mcheli.dependent.particle.ClientboundExplosionFxPayload.STREAM_CODEC,
            mcheli.dependent.particle.ClientboundExplosionFxPayload::handle);
        registrar.playToClient(
            mcheli.dependent.particle.ClientboundCartridgePayload.TYPE,
            mcheli.dependent.particle.ClientboundCartridgePayload.STREAM_CODEC,
            mcheli.dependent.particle.ClientboundCartridgePayload::handle);
        // The weapon hit marker (reference MCH_PacketNotifyHitBullet); handler Dist-guarded like the RWR tone.
        registrar.playToClient(
            ClientboundHitMarkPayload.TYPE,
            ClientboundHitMarkPayload.STREAM_CODEC,
            ClientboundHitMarkPayload::handle);
        // A seat's magazine/reload/heat for its own rider's HUD (reference MCH_PacketNotifyAmmoNum); Dist-guarded.
        registrar.playToClient(
            ClientboundSeatWeaponPayload.TYPE,
            ClientboundSeatWeaponPayload.STREAM_CODEC,
            ClientboundSeatWeaponPayload::handle);
    }
}
