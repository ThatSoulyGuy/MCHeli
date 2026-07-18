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
            ServerboundFoldBladePayload.TYPE,
            ServerboundFoldBladePayload.STREAM_CODEC,
            ServerboundFoldBladePayload::handle);
        registrar.playToServer(
            ServerboundUseFlarePayload.TYPE,
            ServerboundUseFlarePayload.STREAM_CODEC,
            ServerboundUseFlarePayload::handle);
        registrar.playToServer(
            ServerboundVtolPayload.TYPE,
            ServerboundVtolPayload.STREAM_CODEC,
            ServerboundVtolPayload::handle);
    }
}
