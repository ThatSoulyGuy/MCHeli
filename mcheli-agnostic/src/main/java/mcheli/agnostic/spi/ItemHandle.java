package mcheli.agnostic.spi;

/**
 * Opaque item identity, resolved via {@link Registrar#itemByName}. Implementations MUST provide value
 * {@code equals}/{@code hashCode} (by registry name) so it can key the vehicle/weapon definition maps
 * the way the reference used {@code net.minecraft.item.Item} by reference identity.
 */
public interface ItemHandle {
    String name();
}
