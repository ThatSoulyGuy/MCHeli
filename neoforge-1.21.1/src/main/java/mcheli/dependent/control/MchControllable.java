package mcheli.dependent.control;

/**
 * Implemented by every MCHeli vehicle ({@code AbstractMchVehicle}) to expose its {@link MchControlState} — the seam the
 * serverbound control packet writes into and the entity's server tick reads from. Lets the packet handler treat
 * every controllable vehicle uniformly (resolve entity by id → cast → {@link #getControlState()}) without knowing
 * the concrete type.
 */
public interface MchControllable {
    MchControlState getControlState();
}
