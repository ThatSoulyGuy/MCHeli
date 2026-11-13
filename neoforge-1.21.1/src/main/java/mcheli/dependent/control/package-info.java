/**
 * Rider input and the MCHeli network payloads.
 *
 * <p>This package owns the seam between a rider's raw key/mouse input and the platform-agnostic flight sim
 * ({@link mcheli.agnostic.sim}). {@link mcheli.dependent.control.MchControlState} is the mutable per-seat holder of the
 * raw control bits, latched modes and client settings; its {@link mcheli.dependent.control.MchControlState#snapshot(float)}
 * mints the immutable {@link mcheli.agnostic.sim.ControlInput} that
 * {@link mcheli.agnostic.sim.AircraftFlightController#tickServer} consumes each server tick.
 *
 * <p>The client side lives in {@code mcheli.dependent.client}: {@code MchClientInput} (held keys, fire trigger, lock id,
 * weapon switching, client settings), {@code MchClientRotation} (the client-authoritative mouse rotation) and
 * {@code MchKeybinds} (one-shot actions). They send the payloads here, registered by
 * {@link mcheli.dependent.control.MchControlNetwork}:
 * <ul>
 *   <li>held state: {@link mcheli.dependent.control.ServerboundControlPayload} (the port of the reference
 *       {@code MCH_PacketPlayerControlBase} bitset and {@code MCH_PacketNotifyClientSetting}, on change plus a
 *       keepalive) and {@link mcheli.dependent.control.ServerboundRotationPayload} (client-authoritative rotation);</li>
 *   <li>one-shot actions: weapon switch and weapon mode, seat switch, gunner mode, blade fold, VTOL, hover mode,
 *       landing gear, flares, the vehicle GUI, and the lock notice
 *       ({@link mcheli.dependent.control.ServerboundNotifyLockPayload});</li>
 *   <li>clientbound notices: the RWR tone ({@link mcheli.dependent.control.ClientboundRwrPayload}), the weapon hit
 *       marker ({@link mcheli.dependent.control.ClientboundHitMarkPayload}) and a rider's own seat-weapon numbers for
 *       his HUD ({@link mcheli.dependent.control.ClientboundSeatWeaponPayload}).</li>
 * </ul>
 * Every serverbound handler re-checks on the server that the sender rides the addressed vehicle and occupies a seat
 * that may do the action, plus the vehicle's own gates. The control holder stays free of Minecraft networking types,
 * so self-tests and drone drivers can populate it directly.
 */
package mcheli.dependent.control;
