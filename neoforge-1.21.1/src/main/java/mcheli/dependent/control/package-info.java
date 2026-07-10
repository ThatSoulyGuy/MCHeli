/**
 * Server-side control-input sourcing for piloted MCHeli vehicles.
 *
 * <p>This package owns the seam between a rider's raw key/mouse input and the platform-agnostic flight sim
 * ({@link mcheli.agnostic.sim}). The single class here, {@link mcheli.dependent.control.MchControlState}, is a
 * mutable per-vehicle holder of the raw control bits, mouse deltas and latched mode flags; its
 * {@link mcheli.dependent.control.MchControlState#snapshot(float)} method mints the immutable
 * {@link mcheli.agnostic.sim.ControlInput} that
 * {@link mcheli.agnostic.sim.AircraftFlightController#tickServer} consumes each server tick.
 *
 * <p><strong>Deferred: the client capture -&gt; packet -&gt; server plumbing.</strong> In the original mod the flow is
 * <em>client keybinds/mouse helper</em> &rarr; encode the held flags into a bitset
 * (see {@code mcheli.aircraft.MCH_PacketPlayerControlBase} in the 1.7.10 reference) &rarr; send a serverbound packet
 * &rarr; the server decodes and stamps the bits onto the piloted entity for that tick. That whole chain is a
 * <em>networking</em> concern and is intentionally NOT built yet:
 * <ul>
 *   <li>the client-side capture (keybind registration, {@code MouseHandler} delta averaging, flight-sim roll-stick
 *       accumulation, invert/sensitivity config) is TODO;</li>
 *   <li>the {@code ServerboundControlPacket} record, its {@code StreamCodec}, and the NeoForge
 *       {@code PayloadRegistrar} handler that writes into {@code MchControlState} are TODO;</li>
 *   <li>the derivation of the latched mode flags (freeLook / gunner / flight-sim / auto-throttle) from the packet's
 *       toggle bits, seat config and vehicle config is TODO.</li>
 * </ul>
 * Until those land, {@code MchControlState} can be populated directly (tests, AI/drone drivers, orchestrator wiring)
 * and snapshotted into a {@code ControlInput}. Keeping this holder free of any Minecraft networking type is
 * deliberate: when the packet handler is added it will simply mutate these fields, and nothing downstream changes.
 */
package mcheli.dependent.control;
