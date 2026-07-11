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
    }
}
