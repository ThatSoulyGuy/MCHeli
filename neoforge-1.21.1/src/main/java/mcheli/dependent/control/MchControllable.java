package mcheli.dependent.control;

/**
 * Implemented by demo vehicle entities that expose a per-instance {@link MchControlState} — the seam the
 * serverbound control packet writes into and the entity's server tick reads from. Lets the packet handler treat
 * every controllable vehicle uniformly (resolve entity by id → cast → {@link #getControlState()}) without knowing
 * the concrete type.
 */
public interface MchControllable {
    MchControlState getControlState();
}
