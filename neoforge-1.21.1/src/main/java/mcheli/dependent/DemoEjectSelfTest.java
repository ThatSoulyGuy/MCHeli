package mcheli.dependent;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import mcheli.agnostic.aircraft.MCH_SeatInfo;
import mcheli.agnostic.helicopter.MCH_HeliInfoManager;
import mcheli.agnostic.info.MCH_InfoManagerBase;
import mcheli.agnostic.parachute.ParachuteFlight;
import mcheli.agnostic.plane.MCP_PlaneInfoManager;
import mcheli.agnostic.tank.MCH_TankInfoManager;
import mcheli.agnostic.value.Vec3d;
import mcheli.agnostic.vehicle.MCH_VehicleInfoManager;
import mcheli.dependent.control.MchControlNetwork;
import mcheli.dependent.control.ServerboundEjectSeatPayload;
import mcheli.dependent.control.ServerboundVehicleGuiPayload;
import mcheli.dependent.entity.AbstractMchVehicle;
import mcheli.dependent.entity.MchParachute;
import mcheli.dependent.menu.MchVehicleMenu;
import mcheli.dependent.registry.MchRegistries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The ejection seat (reference {@code MCH_EntityAircraft.ejectSeat}, {@code MCP_EntityPlane.getNumEjectionSeat},
 * {@code MCH_AircraftInventory} slots 3 and 4, {@code MCH_AircraftGuiContainer}), headless. The synchronous checks run
 * in {@link #start}; timed checks are {@link Probe}s, and the test ends when the last one has its verdict. The first
 * unmet requirement is the FAIL reason.
 * <ul>
 *   <li><b>E1 census</b> — over every loaded config, {@code numEjectionSeats()} is non-zero for exactly the 21
 *       {@code EnableEjectionSeat} planes: 1 for the single-seaters, 2 for the two-seaters; every helicopter, tank and
 *       ground vehicle has 0.</li>
 *   <li><b>E2 menu layout</b> — the riding menu has 3 fuel + {@code numEjectionSeats()} parachute + 36 player slots,
 *       the parachute slots at {@code (10 + 18i, 105)} over the vehicle's parachute container (a-10: 1, f-15e: 2,
 *       ah-64: 0), as the real {@code mcheli:vehicle_gui} OPEN handler builds it too; a client menu built from the open
 *       buffer has as many slots as the server's (also for an id that does not resolve, and a malformed count is
 *       bounded); the parachute slots refuse a fuel can and a stone and the fuel slots a parachute; shift-click puts
 *       the first parachute in slot 3 and the second in slot 4, a third stays, a fuel can still goes to fuel slot 0,
 *       and a parachute slot empties back to the player; the protocol is "2".</li>
 *   <li><b>E3 NBT and spill</b> — a parachute only in the second slot saves as {@code {SlotAC:4b}} in
 *       {@code FuelSlots} (after a fuel can's {@code SlotAC:0b}) and reloads into the second slot; an empty save is
 *       {@code FuelSlots:[]} as before; an out-of-range {@code SlotAC} is dropped and a stone saved in a parachute
 *       slot is no parachute; {@code haveParachute}/{@code consumeParachute} take slot 0 before slot 1; a discard drops
 *       both parachutes, a chunk unload none.</li>
 *   <li><b>E4 pilot eject</b> — an a-10 at an attitude, moving, its pilot's real {@code mcheli:eject_seat} payload: the
 *       pilot is off at once, standing where one type-2 parachute appears, at {@code pos + R·(seat0 + (0, 2, 0))}
 *       (also against the reference {@code RotVec3} with the 1.7.10 trig), with the vehicle's motion
 *       {@code + R·(0, 2, 0)} and a kick under 0.05 on X and Z, his yaw and his fall distance; the slot is empty and
 *       the forced canopy holds the switches for 40 ticks; on the next tick he rides it with its yaw equal to his,
 *       and the canopy swings open.</li>
 *   <li><b>E5 two seats</b> — an f-15e's pilot with a pig in seat 1 and 2 parachutes: both leave in one call, the
 *       pilot first, each parachute at its own seat point; both slots are empty; both ride on the next tick.</li>
 *   <li><b>E6 one parachute</b> — only the pilot leaves; the pig keeps seat 1.</li>
 *   <li><b>E7 player co-pilot</b> — the pilot ejects alone and 1 parachute is left; the seat-1 player's own payload
 *       then ejects him from his seat point, leaving 0.</li>
 *   <li><b>E8 refusals</b> — no parachute (a-10), no ejection seat (an an2 pilot, an an2 seat-2 rider, an ah-64 pilot,
 *       each with 2 parachutes aboard), and a wreck (an a-10 destroyed with nobody aboard keeps its parachute, and a
 *       player forced back aboard cannot eject): nobody moves, nothing is used up, nothing spawns.</li>
 *   <li><b>E9 DismountAll</b> — the pilot's DismountAll puts the seat-1 pig off plainly in the same call, before seat 1
 *       is looked at: one parachute (the pilot's), 1 left, and the pig rides nothing.</li>
 *   <li><b>E10 ground eject</b> — an a-10 on the ground: 20 ticks later the parachute is 24.08 ±1 above the eject
 *       point (no altitude gate, as in 1.7.10).</li>
 *   <li><b>E11 destruction</b> — an f-15e with a player pilot, a seat-1 pig and 2 parachutes, killed in one hit: a
 *       wreck with nobody aboard and both slots empty, and two type-2 parachutes for the pilot and the pig; the pilot
 *       rides his on the next tick.</li>
 *   <li><b>E12</b> — killed with 1 parachute: the pilot on a parachute, the pig put off plainly; with none, both
 *       plainly.</li>
 *   <li><b>E13</b> — a pig pilot with 1 parachute ejects on a parachute when the vehicle is killed.</li>
 * </ul>
 * Vehicles for the timed checks are held still by the test ({@link Pin}). The slot frames and the "Parachute" label on
 * the screen, the jump-key hold and its action-bar progress, the eject sound and the canopy animation on screen are
 * client-only (runClient checks).
 */
public final class DemoEjectSelfTest extends MchSelfTest {

    /** The bundled configs with {@code EnableEjectionSeat}, by their slot count (reference census, spec 4.1). */
    private static final Map<String, Integer> EXPECTED = new TreeMap<>();

    static {
        for (String n : new String[] {"a-10", "f-35a", "f-35b", "f-35c", "f22a", "harrier", "harrier_en", "mig29",
                "su-33", "su-47", "su37"}) {
            EXPECTED.put(n, 1);
        }
        for (String n : new String[] {"b-2a", "emb314", "f-15e", "f-15s_mtd", "f14d", "f4a", "fa18f", "t-4",
                "tornado-gr4", "tornado-ids"}) {
            EXPECTED.put(n, 2);
        }
    }

    private static final String E3_SLOT1_ONLY = "[{SlotAC:4b,count:1,id:\"mcheli:parachute\"}]";
    private static final String E3_FUEL_AND_SLOT1 =
        "[{SlotAC:0b,count:1,id:\"mcheli:fuel\"},{SlotAC:4b,count:1,id:\"mcheli:parachute\"}]";

    private final List<ServerPlayer> players = new ArrayList<>();
    private final List<Probe> probes = new ArrayList<>();
    private final List<String> verdicts = new ArrayList<>();
    private final List<Pin> pins = new ArrayList<>();

    @Override
    protected int timeoutTicks() {
        return 900;
    }

    @Override
    protected void start() {
        try {
            String census = testCensus();
            testMenuLayout();
            testOpenBuffer();
            testSlotRules();
            testNbt();
            testInventoryOrder();
            String spill = testSpill();
            this.verdicts.add("E1 " + census);
            this.verdicts.add("E2 layout, open buffer, slot rules, shift-click, protocol 2");
            this.verdicts.add("E3 FuelSlots SlotAC 3/4, reload, empty save, out of range, order, spill " + spill);
            startPilotEject();
            startTwoSeats();
            startOneParachute();
            startPlayerCoPilot();
            this.verdicts.add(testRefusals());
            startDismountAll();
            startGroundEject();
            startDestruction();
            startDestructionPlain();
            startMobPilot();
            LOG.info("[EJECT SELF-TEST] E1 census, E2 menu, E3 NBT/spill and the synchronous E4-E13 checks OK; {} timed "
                + "probes", this.probes.size());
        } catch (IllegalStateException e) {
            fail("{}", e.getMessage());
        }
    }

    @Override
    protected void tick() {
        try {
            for (Pin pin : this.pins) {
                pin.apply();
            }
            for (Iterator<Probe> it = this.probes.iterator(); it.hasNext(); ) {
                Probe p = it.next();
                if (p.step()) {
                    this.verdicts.add(p.verdict);
                    LOG.info("[EJECT SELF-TEST] {}", p.verdict);
                    it.remove();
                }
            }
        } catch (IllegalStateException e) {
            fail("{}", e.getMessage());
            return;
        }
        if (this.probes.isEmpty()) {
            finish(true, "{}", String.join("; ", this.verdicts));
        }
    }

    /** A timed check: {@link #step} runs once per test tick and returns true once {@link #verdict} is set. */
    private abstract static class Probe {
        String verdict;

        abstract boolean step();
    }

    // ---- E1 ----

    @SuppressWarnings("rawtypes")
    private String testCensus() {
        Map<String, Integer> found = new TreeMap<>();
        StringBuilder counts = new StringBuilder();
        census(found, counts, "heli", MCH_HeliInfoManager.getInstance(), MchRegistries.HELI.get());
        census(found, counts, "plane", MCP_PlaneInfoManager.getInstance(), MchRegistries.PLANE.get());
        census(found, counts, "tank", MCH_TankInfoManager.getInstance(), MchRegistries.TANK.get());
        census(found, counts, "vehicle", MCH_VehicleInfoManager.getInstance(), MchRegistries.VEHICLE.get());
        LOG.info("[EJECT SELF-TEST] E1 configs {}; ejection seats {}", counts, found);
        req(found.equals(EXPECTED), "numEjectionSeats is non-zero for exactly the 21 EnableEjectionSeat planes, "
            + "expected " + EXPECTED + ", got " + found);
        return "21 planes (" + found.values().stream().filter(n -> n == 1).count() + " x1, "
            + found.values().stream().filter(n -> n == 2).count() + " x2), " + counts + " configs";
    }

    /** {@code numEjectionSeats()} of every config {@code mgr} loaded, read from one entity of the category. */
    @SuppressWarnings("rawtypes")
    private void census(Map<String, Integer> found, StringBuilder counts, String label, MCH_InfoManagerBase mgr,
                        EntityType<? extends AbstractMchVehicle> type) {
        Map map = mgr.getMap();
        req(map != null && !map.isEmpty(), "the " + label + " configs are loaded");
        AbstractMchVehicle v = type.create(this.level);
        req(v != null, "the " + label + " entity creates");
        int n = 0;
        for (Object key : map.keySet()) {
            String name = String.valueOf(key);
            v.setConfigName(name);
            req(v.hostInfo() != null && name.equalsIgnoreCase(v.hostInfo().name), "the " + name + " config resolves");
            int seats = v.numEjectionSeats();
            if (seats != 0) {
                found.put(name, seats);
            }
            n++;
        }
        counts.append(counts.length() > 0 ? " " : "").append(label).append('=').append(n);
    }

    // ---- E2 ----

    private void testMenuLayout() {
        ServerPlayer p = player("e2layout");
        AbstractMchVehicle a10 = created(MchRegistries.PLANE.get(), "a-10");
        layout(new MchVehicleMenu(1, p.getInventory(), a10), a10, 1);
        AbstractMchVehicle f15 = created(MchRegistries.PLANE.get(), "f-15e");
        layout(new MchVehicleMenu(2, p.getInventory(), f15), f15, 2);
        AbstractMchVehicle ah = created(MchRegistries.HELI.get(), "ah-64");
        layout(new MchVehicleMenu(3, p.getInventory(), ah), ah, 0);

        // The real OPEN handler, from the f-15e's pilot: the same menu, and a passenger still gets none.
        AbstractMchVehicle live = spawn(MchRegistries.PLANE.get(), "f-15e", 0.5, 20.0, 0.5);
        ServerPlayer pilot = player("e2pilot");
        ServerPlayer rear = player("e2rear");
        req(board(pilot, live, 0) && board(rear, live, 1), "a pilot and a rear-seater board the f-15e");
        gui(ServerboundVehicleGuiPayload.open(live.getId()), pilot);
        req(pilot.containerMenu instanceof MchVehicleMenu m && m.vehicle() == live,
            "the OPEN payload opens the f-15e's riding menu for its pilot, got "
                + pilot.containerMenu.getClass().getSimpleName());
        layout((MchVehicleMenu) pilot.containerMenu, live, 2);
        gui(ServerboundVehicleGuiPayload.open(live.getId()), rear);
        req(rear.containerMenu == rear.inventoryMenu, "the riding menu stays pilot-only");
        pilot.closeContainer();
        pilot.stopRiding();
        rear.stopRiding();
        live.discard();
        LOG.info("[EJECT SELF-TEST] E2 layout OK (a-10 3+1+36, f-15e 3+2+36 also by payload, ah-64 3+0+36)");
    }

    /** The slot list of a riding menu with {@code n} parachute slots over {@code v}'s containers. */
    private static void layout(MchVehicleMenu m, AbstractMchVehicle v, int n) {
        String who = v.configName();
        req(m.parachuteSlots() == n && m.slots.size() == AbstractMchVehicle.FUEL_SLOTS + n + 36,
            who + ": 3 fuel + " + n + " parachute + 36 slots, got " + m.parachuteSlots() + " / " + m.slots.size());
        for (int i = 0; i < AbstractMchVehicle.FUEL_SLOTS; i++) {
            Slot s = m.slots.get(i);
            req(s.container == v.fuelInventory() && s.getContainerSlot() == i && s.x == 10 && s.y == 30 + 18 * i,
                who + ": fuel slot " + i + " at (10, " + (30 + 18 * i) + ")");
        }
        for (int i = 0; i < n; i++) {
            Slot s = m.slots.get(MchVehicleMenu.FUEL_END + i);
            req(s.container == v.parachuteInventory() && s.getContainerSlot() == i && s.x == 10 + 18 * i && s.y == 105,
                who + ": parachute slot " + i + " is menu slot " + (3 + i) + " at (" + (10 + 18 * i) + ", 105), got ("
                    + s.x + ", " + s.y + ")");
        }
        Slot inv = m.slots.get(MchVehicleMenu.FUEL_END + n);
        Slot hotbar = m.slots.get(MchVehicleMenu.FUEL_END + n + 27);
        req(inv.getContainerSlot() == 9 && inv.x == 25 && inv.y == 135 && hotbar.getContainerSlot() == 0
                && hotbar.x == 25 && hotbar.y == 195,
            who + ": the player's inventory starts at menu slot " + (3 + n) + " (getInventoryStartIndex)");
    }

    private void testOpenBuffer() {
        req("2".equals(MchControlNetwork.PROTOCOL), "the network protocol is 2, got " + MchControlNetwork.PROTOCOL);
        ServerPlayer p = player("e2buffer");
        AbstractMchVehicle f15 = spawn(MchRegistries.PLANE.get(), "f-15e", 8.5, 20.0, 0.5);
        RegistryFriendlyByteBuf buf = buffer();
        MchVehicleMenu.writeOpenData(buf, f15);
        MchVehicleMenu resolved = new MchVehicleMenu(4, p.getInventory(), buf);
        req(resolved.vehicle() == f15 && buf.readableBytes() == 0, "the client menu reads the whole open buffer and "
            + "resolves the f-15e");
        layout(resolved, f15, 2);

        RegistryFriendlyByteBuf unknown = buffer();
        unknown.writeVarInt(-12345);
        unknown.writeVarInt(2);
        MchVehicleMenu blind = new MchVehicleMenu(5, p.getInventory(), unknown);
        req(blind.vehicle() == null && blind.parachuteSlots() == 2
                && blind.slots.size() == new MchVehicleMenu(6, p.getInventory(), f15).slots.size()
                && blind.slots.get(MchVehicleMenu.FUEL_END + 1).container != f15.parachuteInventory(),
            "a client menu for an id that does not resolve yet still has the f-15e server menu's slots, over its own "
                + "containers, got " + blind.slots.size());

        RegistryFriendlyByteBuf bad = buffer();
        bad.writeVarInt(-12345);
        bad.writeVarInt(99);
        MchVehicleMenu bounded = new MchVehicleMenu(7, p.getInventory(), bad);
        req(bounded.parachuteSlots() == AbstractMchVehicle.PARACHUTE_SLOTS && bounded.slots.size() == 3 + 2 + 36,
            "a malformed parachute count is bounded by the 2 slots, got " + bounded.parachuteSlots());
        f15.discard();
    }

    private void testSlotRules() {
        ServerPlayer p = player("e2rules");
        AbstractMchVehicle f15 = created(MchRegistries.PLANE.get(), "f-15e");
        MchVehicleMenu m = new MchVehicleMenu(8, p.getInventory(), f15);
        Slot para0 = m.slots.get(MchVehicleMenu.FUEL_END);
        ItemStack chute = new ItemStack(MchRegistries.PARACHUTE_ITEM.get());
        req(para0.mayPlace(chute) && !para0.mayPlace(new ItemStack(MchRegistries.FUEL.get()))
                && !para0.mayPlace(new ItemStack(Items.STONE)) && para0.getMaxStackSize() == 1,
            "a parachute slot takes a parachute (one) and refuses a fuel can and a stone");
        req(!m.slots.get(0).mayPlace(chute), "a fuel slot refuses a parachute");

        // Shift-click from the player: parachutes fill the empty parachute slots in order, a fuel can its own slot.
        int invStart = MchVehicleMenu.FUEL_END + m.parachuteSlots();
        int hotbar0 = invStart + 27;
        p.getInventory().setItem(0, chute.copy());       // hotbar 0
        p.getInventory().setItem(9, chute.copy());       // main inventory, first slot
        p.getInventory().setItem(10, chute.copy());
        p.getInventory().setItem(11, new ItemStack(MchRegistries.FUEL.get()));
        m.clicked(hotbar0, 0, ClickType.QUICK_MOVE, p);
        boolean first = isChute(f15.parachuteInventory().getItem(0)) && f15.parachuteInventory().getItem(1).isEmpty()
            && p.getInventory().getItem(0).isEmpty();
        m.clicked(invStart, 0, ClickType.QUICK_MOVE, p);
        boolean second = isChute(f15.parachuteInventory().getItem(1)) && p.getInventory().getItem(9).isEmpty();
        m.clicked(invStart + 1, 0, ClickType.QUICK_MOVE, p);
        boolean thirdStays = isChute(p.getInventory().getItem(10));
        m.clicked(invStart + 2, 0, ClickType.QUICK_MOVE, p);
        boolean fuel = f15.fuelInventory().getItem(0).is(MchRegistries.FUEL.get())
            && p.getInventory().getItem(11).isEmpty();
        req(first && second && thirdStays && fuel, "shift-click: the first parachute goes to slot 3 (" + first
            + "), the second to slot 4 (" + second + "), a third stays (" + thirdStays + "), a fuel can goes to fuel "
            + "slot 0 (" + fuel + ")");

        // ...and a parachute slot empties back to the player.
        m.clicked(MchVehicleMenu.FUEL_END, 0, ClickType.QUICK_MOVE, p);
        req(f15.parachuteInventory().getItem(0).isEmpty() && countChutes(p) == 2,
            "shift-clicking parachute slot 3 moves it back to the player");
        p.getInventory().clearContent();
    }

    // ---- E3 ----

    private void testNbt() {
        AbstractMchVehicle f15 = created(MchRegistries.PLANE.get(), "f-15e");
        CompoundTag empty = f15.saveWithoutId(new CompoundTag());
        req("[]".equals(String.valueOf(empty.get("FuelSlots"))), "an f-15e with no can and no parachute saves "
            + "FuelSlots:[] as before, got " + empty.get("FuelSlots"));

        f15.parachuteInventory().setItem(1, new ItemStack(MchRegistries.PARACHUTE_ITEM.get()));
        CompoundTag tag = f15.saveWithoutId(new CompoundTag());
        String slots = String.valueOf(tag.get("FuelSlots"));
        LOG.info("[EJECT SELF-TEST] E3 observed FuelSlots={}", slots);
        req(E3_SLOT1_ONLY.equals(slots), "a parachute in the second slot saves as SlotAC 4: expected " + E3_SLOT1_ONLY
            + ", got " + slots);
        AbstractMchVehicle back = loaded(tag);
        req(back.parachuteInventory().getItem(0).isEmpty() && isChute(back.parachuteInventory().getItem(1))
                && back.fuelInventory().isEmpty(),
            "the parachute reloads into the second slot and nothing else fills");

        f15.fuelInventory().setItem(0, new ItemStack(MchRegistries.FUEL.get()));
        String both = String.valueOf(f15.saveWithoutId(new CompoundTag()).get("FuelSlots"));
        req(E3_FUEL_AND_SLOT1.equals(both), "the fuel can and the parachute save in slot order: expected "
            + E3_FUEL_AND_SLOT1 + ", got " + both);

        // Out of range (the reference's 10-slot inventory, slot 7) is dropped; a stone in a parachute slot loads but is
        // no parachute.
        ListTag odd = new ListTag();
        odd.add(slotEntry(7, new ItemStack(MchRegistries.PARACHUTE_ITEM.get())));
        odd.add(slotEntry(3, new ItemStack(Items.STONE)));
        CompoundTag oddTag = tag.copy();
        oddTag.put("FuelSlots", odd);
        AbstractMchVehicle oddBack = loaded(oddTag);
        req(oddBack.parachuteInventory().getItem(0).is(Items.STONE) && oddBack.parachuteInventory().getItem(1).isEmpty()
                && oddBack.fuelInventory().isEmpty() && !oddBack.haveParachute(),
            "SlotAC 7 is dropped, a stone at SlotAC 3 loads into the first parachute slot and does not count");
        LOG.info("[EJECT SELF-TEST] E3 NBT OK (SlotAC 4 golden, reload, slot order, empty, out of range, stone)");
    }

    private void testInventoryOrder() {
        AbstractMchVehicle f15 = created(MchRegistries.PLANE.get(), "f-15e");
        req(!f15.haveParachute(), "an empty f-15e has no parachute");
        f15.parachuteInventory().setItem(1, new ItemStack(MchRegistries.PARACHUTE_ITEM.get()));
        req(f15.haveParachute(), "a parachute in the second slot counts");
        f15.consumeParachute();
        req(f15.parachuteInventory().isEmpty() && !f15.haveParachute(), "consume takes the second slot's");

        f15.parachuteInventory().setItem(0, new ItemStack(MchRegistries.PARACHUTE_ITEM.get()));
        f15.parachuteInventory().setItem(1, new ItemStack(MchRegistries.PARACHUTE_ITEM.get()));
        f15.consumeParachute();
        req(f15.parachuteInventory().getItem(0).isEmpty() && isChute(f15.parachuteInventory().getItem(1)),
            "with both slots full, consume takes slot 0 first");
        f15.consumeParachute();
        f15.consumeParachute(); // nothing left: a no-op
        req(f15.parachuteInventory().isEmpty() && !f15.haveParachute(), "then slot 1, then nothing");

        f15.parachuteInventory().setItem(0, new ItemStack(Items.STONE));
        req(!f15.haveParachute(), "a stone does not count as a parachute");
        f15.parachuteInventory().setItem(1, new ItemStack(MchRegistries.PARACHUTE_ITEM.get()));
        f15.consumeParachute();
        req(f15.parachuteInventory().getItem(0).is(Items.STONE) && f15.parachuteInventory().getItem(1).isEmpty(),
            "consume skips the stone and takes the parachute");
    }

    private String testSpill() {
        AbstractMchVehicle d = spawn(MchRegistries.PLANE.get(), "f-15e", -16.5, 150.0, 0.5);
        d.parachuteInventory().setItem(0, new ItemStack(MchRegistries.PARACHUTE_ITEM.get()));
        d.parachuteInventory().setItem(1, new ItemStack(MchRegistries.PARACHUTE_ITEM.get()));
        d.fuelInventory().setItem(2, new ItemStack(MchRegistries.FUEL.get()));
        AABB boxD = around(d);
        d.discard();
        String discarded = collect(boxD);
        req("mcheli:fuel=1,mcheli:parachute=2".equals(discarded) && d.parachuteInventory().isEmpty(),
            "a discarded f-15e drops its fuel can and both parachutes, got " + discarded);

        AbstractMchVehicle u = spawn(MchRegistries.PLANE.get(), "f-15e", -16.5, 150.0, 16.5);
        u.parachuteInventory().setItem(0, new ItemStack(MchRegistries.PARACHUTE_ITEM.get()));
        AABB boxU = around(u);
        u.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        String unloaded = collect(boxU);
        req(unloaded.isEmpty() && isChute(u.parachuteInventory().getItem(0)),
            "a chunk unload drops nothing and keeps the parachute, got '" + unloaded + "'");
        LOG.info("[EJECT SELF-TEST] E3 spill OK (discard {}, unload none)", discarded);
        return "discard " + discarded + ", unload none";
    }

    // ---- E4-E13: the ejection seat (each in its own spot of the area, 24 blocks from the next) ----

    private void startPilotEject() {
        AbstractMchVehicle a10 = pinned("a-10", -39.5, 40.0, -39.5, 30.0F, -12.0F, 20.0F);
        ServerPlayer pilot = player("e4pilot");
        req(board(pilot, a10, 0), "E4: a player boards the a-10's pilot seat");
        pilot.setYRot(50.0F);
        pilot.yRotO = 50.0F;
        pilot.fallDistance = 1.25F;
        a10.parachuteInventory().setItem(0, new ItemStack(MchRegistries.PARACHUTE_ITEM.get()));
        req(a10.canEjectSeat(pilot) && a10.isCanopyClose() && a10.modeSwitchCooldownTicks() == 0,
            "E4: the pilot may eject, the canopy is shut and the switches are ready");
        a10.setDeltaMovement(0.3, -0.1, 0.2); // a moving plane: the launch adds its motion
        Vec3 motion = a10.getDeltaMovement();
        MCH_SeatInfo seat = a10.hostInfo().seatList.get(0);
        Vec3 expectAt = a10.position().add(body(a10, seat.pos.x(), seat.pos.y() + 2.0, seat.pos.z()));
        Vec3 reference = referenceTransform(a10, seat.pos.x(), seat.pos.y() + 2.0, seat.pos.z());
        Vec3 up = body(a10, 0.0, 2.0, 0.0);
        int mark = spawned().size();
        eject(a10, pilot);
        List<MchParachute> chutes = parachutesSince(mark);
        req(!pilot.isPassenger() && a10.pilot() == null && a10.getPassengers().isEmpty(),
            "E4: the pilot is off at once");
        req(chutes.size() == 1, "E4: one parachute opens, got " + chutes.size());
        MchParachute c = chutes.get(0);
        req(c.parachuteType() == ParachuteFlight.TYPE_SEAT && c.pendingUser() == pilot,
            "E4: an ejection seat (type 2) for the pilot, got type " + c.parachuteType());
        req(c.position().distanceTo(expectAt) <= 1.0e-4 && pilot.position().distanceTo(expectAt) <= 1.0e-4,
            "E4: the parachute and the pilot are at pos + R(seat0 + (0,2,0)) = " + fmt(expectAt) + ", got "
                + fmt(c.position()) + " / " + fmt(pilot.position()));
        req(c.position().distanceTo(reference) <= 0.01, "E4: the eject point matches the reference RotVec3(-yaw, "
            + "-pitch, -roll) " + fmt(reference) + ", got " + fmt(c.position()));
        Vec3 kick = c.getDeltaMovement().subtract(motion.add(up));
        req(Math.abs(kick.x) <= 0.05 && Math.abs(kick.z) <= 0.05 && Math.abs(kick.y) <= 1.0e-6,
            "E4: the launch is the plane's motion + R(0,2,0) with a kick under 0.05 on X and Z, kick " + fmt(kick));
        req(pilot.getDeltaMovement().equals(c.getDeltaMovement()), "E4: the pilot is launched with it");
        req(c.getYRot() == 50.0F && c.fallDistance == 1.25F, "E4: it takes the pilot's yaw and fall distance, got "
            + c.getYRot() + " / " + c.fallDistance);
        req(a10.parachuteInventory().isEmpty() && !a10.haveParachute(), "E4: the parachute slot is empty");
        req(a10.modeSwitchCooldownTicks() == 40, "E4: the canopy is forced open and the switches wait 40 ticks, got "
            + a10.modeSwitchCooldownTicks());
        this.probes.add(new At(1, () -> {
            req(pilot.getVehicle() == c, "E4: on the next tick the pilot rides his parachute");
            req(c.getYRot() == pilot.getYRot(), "E4: the parachute's yaw is the pilot's: " + c.getYRot() + " vs "
                + pilot.getYRot());
            req(!a10.isCanopyClose() && a10.modeSwitchCooldownTicks() == 39, "E4: the canopy swings open, and the "
                + "cooldown counts down from 40, got " + a10.modeSwitchCooldownTicks());
            a10.discard();
            LOG.info("[EJECT SELF-TEST] E4 observed eject point {}, launch {}, kick {} (unseeded)", fmt(expectAt),
                fmt(c.getDeltaMovement()), fmt(kick));
            return "E4 pilot eject (R(seat0 + (0,2,0)) = " + fmt(expectAt.subtract(a10.position()))
                + ", launch, kick, yaw, fall distance, slot, cooldown 40, canopy, rides on tick 1)";
        }));
    }

    private void startTwoSeats() {
        AbstractMchVehicle f15 = pinned("f-15e", -15.5, 40.0, -39.5, -60.0F, 5.0F, -10.0F);
        ServerPlayer pilot = player("e5pilot");
        Pig pig = pig(-15.5, 45.0, -39.5);
        req(board(pilot, f15, 0) && board(pig, f15, 1), "E5: a player pilot and a seat-1 pig board the f-15e");
        loadChutes(f15, 2);
        Vec3 at0 = seatPoint(f15, 0);
        Vec3 at1 = seatPoint(f15, 1);
        int mark = spawned().size();
        eject(f15, pilot);
        List<MchParachute> chutes = parachutesSince(mark);
        req(chutes.size() == 2 && chutes.get(0).pendingUser() == pilot && chutes.get(1).pendingUser() == pig,
            "E5: two parachutes, the pilot's then the pig's, got " + chutes.size());
        req(chutes.get(0).position().distanceTo(at0) <= 1.0e-4 && chutes.get(1).position().distanceTo(at1) <= 1.0e-4,
            "E5: each parachute at its own seat point " + fmt(at0) + " / " + fmt(at1) + ", got "
                + fmt(chutes.get(0).position()) + " / " + fmt(chutes.get(1).position()));
        req(f15.getPassengers().isEmpty() && pig.getVehicle() == null && f15.parachuteInventory().isEmpty(),
            "E5: both leave in one call and both slots are empty");
        this.probes.add(new At(1, () -> {
            req(pilot.getVehicle() == chutes.get(0) && pig.getVehicle() == chutes.get(1),
                "E5: the pilot and the pig ride their parachutes on the next tick");
            f15.discard();
            return "E5 two seats (pilot then pig, seat points, both slots used)";
        }));
    }

    private void startOneParachute() {
        AbstractMchVehicle f15 = pinned("f-15e", 8.5, 40.0, -39.5, 0.0F, 0.0F, 0.0F);
        ServerPlayer pilot = player("e6pilot");
        Pig pig = pig(8.5, 45.0, -39.5);
        req(board(pilot, f15, 0) && board(pig, f15, 1), "E6: a player pilot and a seat-1 pig board the f-15e");
        loadChutes(f15, 1);
        int mark = spawned().size();
        eject(f15, pilot);
        List<MchParachute> chutes = parachutesSince(mark);
        req(chutes.size() == 1 && chutes.get(0).pendingUser() == pilot && !pilot.isPassenger(),
            "E6: one parachute: only the pilot leaves, got " + chutes.size());
        req(pig.getVehicle() == f15 && f15.seatIndexOf(pig) == 1 && !f15.haveParachute(),
            "E6: the pig keeps seat 1 and the slot is empty");
        this.probes.add(new At(1, () -> {
            req(pilot.getVehicle() == chutes.get(0) && pig.getVehicle() == f15 && f15.seatIndexOf(pig) == 1,
                "E6: next tick the pilot rides his parachute and the pig is still in seat 1");
            pig.stopRiding();
            f15.discard();
            return "E6 one parachute (the pilot alone, the pig stays)";
        }));
    }

    private void startPlayerCoPilot() {
        AbstractMchVehicle f15 = pinned("f-15e", 32.5, 40.0, -39.5, 90.0F, 0.0F, 0.0F);
        ServerPlayer pilot = player("e7pilot");
        ServerPlayer rear = player("e7rear");
        req(board(pilot, f15, 0) && board(rear, f15, 1), "E7: two players board the f-15e");
        loadChutes(f15, 2);
        Vec3 at1 = seatPoint(f15, 1);
        int mark = spawned().size();
        eject(f15, pilot);
        List<MchParachute> first = parachutesSince(mark);
        req(first.size() == 1 && first.get(0).pendingUser() == pilot && !pilot.isPassenger(),
            "E7: the pilot ejects alone, got " + first.size() + " parachutes");
        req(rear.getVehicle() == f15 && f15.seatIndexOf(rear) == 1 && countParachutes(f15) == 1,
            "E7: the seat-1 player stays and 1 parachute is left, got " + countParachutes(f15));
        req(rear.getVehicle() == f15 && f15.canEjectSeat(rear), "E7: seat 1 may eject");
        int mark2 = spawned().size();
        eject(f15, rear);
        List<MchParachute> second = parachutesSince(mark2);
        req(second.size() == 1 && second.get(0).pendingUser() == rear && !rear.isPassenger()
                && second.get(0).position().distanceTo(at1) <= 1.0e-4,
            "E7: the seat-1 player's own payload ejects him from his seat point " + fmt(at1));
        req(countParachutes(f15) == 0, "E7: no parachute left");
        this.probes.add(new At(1, () -> {
            req(pilot.getVehicle() == first.get(0) && rear.getVehicle() == second.get(0),
                "E7: both ride their parachutes on the next tick");
            f15.discard();
            return "E7 player co-pilot (pilot alone, then seat 1 on his own key, 2 -> 1 -> 0)";
        }));
    }

    private String testRefusals() {
        // No parachute: the a-10's pilot stays.
        AbstractMchVehicle a10 = spawn(MchRegistries.PLANE.get(), "a-10", -39.5, 40.0, -15.5);
        ServerPlayer p1 = player("e8none");
        req(board(p1, a10, 0) && a10.canEjectSeat(p1), "E8: the a-10 pilot may eject");
        refused(a10, p1, "E8 no parachute");
        req(a10.isCanopyClose() && a10.modeSwitchCooldownTicks() == 0, "E8: no parachute: the canopy stays shut");
        p1.stopRiding();
        a10.discard();

        // No ejection seat: an an2 (EnableEjectionSeat off) pilot and seat-2 rider, an ah-64 pilot.
        AbstractMchVehicle an2 = spawn(MchRegistries.PLANE.get(), "an2", -39.5, 40.0, -15.5);
        ServerPlayer p2 = player("e8an2pilot");
        ServerPlayer p3 = player("e8an2seat2");
        req(board(p2, an2, 0) && board(p3, an2, 2), "E8: an an2 pilot and a seat-2 rider board");
        loadChutes(an2, 2);
        req(!an2.canEjectSeat(p2) && !an2.canEjectSeat(p3), "E8: neither may eject from an an2");
        refused(an2, p2, "E8 an2 pilot");
        refused(an2, p3, "E8 an2 seat 2");
        p2.stopRiding();
        p3.stopRiding();
        an2.discard();
        AbstractMchVehicle ah = spawn(MchRegistries.HELI.get(), "ah-64", -39.5, 40.0, -15.5);
        ServerPlayer p4 = player("e8ah64");
        req(board(p4, ah, 0), "E8: an ah-64 pilot boards");
        loadChutes(ah, 2);
        req(!ah.canEjectSeat(p4), "E8: a helicopter pilot may not eject");
        refused(ah, p4, "E8 ah-64 pilot");
        p4.stopRiding();
        ah.discard();

        // A wreck: destroyed with nobody aboard it keeps its parachute; a player forced back aboard cannot eject.
        AbstractMchVehicle wreck = spawn(MchRegistries.PLANE.get(), "a-10", -39.5, 40.0, -15.5);
        loadChutes(wreck, 1);
        int mark = spawned().size();
        req(wreck.hurt(this.level.damageSources().generic(), 99999.0F) && wreck.isDestroyed(),
            "E8: the a-10 is destroyed");
        req(parachutesSince(mark).isEmpty() && countParachutes(wreck) == 1,
            "E8: a wreck with nobody aboard opens nothing and keeps its parachute");
        ServerPlayer p5 = player("e8wreck");
        req(p5.startRiding(wreck, true) && wreck.seatIndexOf(p5) == 0 && wreck.canEjectSeat(p5),
            "E8: a player is forced back aboard the wreck's pilot seat");
        refused(wreck, p5, "E8 wreck");
        p5.stopRiding();
        wreck.discard();
        LOG.info("[EJECT SELF-TEST] E8 refusals OK");
        return "E8 refusals (no parachute, an2 pilot, an2 seat 2, ah-64 pilot, wreck)";
    }

    /** The eject payload from {@code p} changes nothing: still aboard in the same seat, the parachutes all left, none
     *  opened. */
    private void refused(AbstractMchVehicle v, ServerPlayer p, String what) {
        int seat = v.seatIndexOf(p);
        int chutes = countParachutes(v);
        int mark = spawned().size();
        eject(v, p);
        req(p.getVehicle() == v && v.seatIndexOf(p) == seat && countParachutes(v) == chutes
                && parachutesSince(mark).isEmpty(),
            what + ": the eject payload changes nothing (seat " + v.seatIndexOf(p) + ", parachutes "
                + countParachutes(v) + ")");
    }

    private void startDismountAll() {
        AbstractMchVehicle f15 = pinned("f-15e", -15.5, 40.0, -15.5, 0.0F, 0.0F, 0.0F);
        ServerPlayer pilot = player("e9pilot");
        Pig pig = pig(-15.5, 45.0, -15.5);
        req(board(pilot, f15, 0) && board(pig, f15, 1), "E9: a player pilot and a seat-1 pig board the f-15e");
        f15.controlState(0).dismountAll = true;
        loadChutes(f15, 2);
        int mark = spawned().size();
        eject(f15, pilot);
        List<MchParachute> chutes = parachutesSince(mark);
        req(chutes.size() == 1 && chutes.get(0).pendingUser() == pilot,
            "E9: DismountAll: only the pilot's parachute opens, got " + chutes.size());
        req(pig.getVehicle() == null && f15.getPassengers().isEmpty() && countParachutes(f15) == 1,
            "E9: the pig is put off plainly in the same call, and 1 parachute is left");
        this.probes.add(new At(1, () -> {
            req(pilot.getVehicle() == chutes.get(0) && pig.getVehicle() == null,
                "E9: next tick the pilot rides his parachute and the pig rides nothing");
            f15.discard();
            return "E9 DismountAll (the pig off plainly first, 2 -> 1)";
        }));
    }

    private void startGroundEject() {
        AbstractMchVehicle a10 = pinned("a-10", 8.5, 0.0, -15.5, 0.0F, 0.0F, 0.0F);
        ServerPlayer pilot = player("e10pilot");
        req(board(pilot, a10, 0), "E10: a player boards the grounded a-10");
        loadChutes(a10, 1);
        Vec3 at = seatPoint(a10, 0);
        int mark = spawned().size();
        eject(a10, pilot);
        List<MchParachute> chutes = parachutesSince(mark);
        req(chutes.size() == 1 && chutes.get(0).position().distanceTo(at) <= 1.0e-4,
            "E10: a grounded a-10 still ejects (no altitude gate)");
        MchParachute c = chutes.get(0);
        this.probes.add(new At(20, () -> {
            double rise = c.getY() - at.y;
            req(!c.isRemoved() && Math.abs(rise - 24.08) <= 1.0, "E10: 20 ticks after a ground eject the parachute is "
                + "24.08 +-1 above the eject point, got " + String.format(Locale.ROOT, "%.3f", rise));
            req(pilot.getVehicle() == c, "E10: the pilot rides it");
            a10.discard();
            return "E10 ground eject (+" + String.format(Locale.ROOT, "%.3f", rise) + " in 20 ticks)";
        }));
    }

    private void startDestruction() {
        AbstractMchVehicle f15 = pinned("f-15e", 32.5, 40.0, -15.5, 0.0F, 0.0F, 0.0F);
        ServerPlayer pilot = player("e11pilot");
        Pig pig = pig(32.5, 45.0, -15.5);
        req(board(pilot, f15, 0) && board(pig, f15, 1), "E11: a player pilot and a seat-1 pig board the f-15e");
        loadChutes(f15, 2);
        int mark = spawned().size();
        req(f15.hurt(this.level.damageSources().generic(), 99999.0F) && f15.isDestroyed(),
            "E11: one lethal hit destroys the f-15e");
        List<MchParachute> chutes = parachutesSince(mark);
        req(f15.getPassengers().isEmpty() && countParachutes(f15) == 0,
            "E11: the wreck has nobody aboard and both slots are empty");
        req(chutes.size() == 2 && chutes.get(0).pendingUser() == pilot && chutes.get(1).pendingUser() == pig
                && chutes.get(0).parachuteType() == ParachuteFlight.TYPE_SEAT
                && chutes.get(1).parachuteType() == ParachuteFlight.TYPE_SEAT,
            "E11: two ejection seats, the pilot's and the pig's, got " + chutes.size());
        this.probes.add(new At(1, () -> {
            req(pilot.getVehicle() == chutes.get(0), "E11: next tick the pilot rides his parachute");
            f15.discard();
            return "E11 destruction (pilot and pig on ejection seats, slots 2 -> 0)";
        }));
    }

    private void startDestructionPlain() {
        AbstractMchVehicle one = pinned("f-15e", -39.5, 40.0, 8.5, 0.0F, 0.0F, 0.0F);
        ServerPlayer pilot1 = player("e12pilot1");
        Pig pig1 = pig(-39.5, 45.0, 8.5);
        req(board(pilot1, one, 0) && board(pig1, one, 1), "E12: a player pilot and a seat-1 pig board the f-15e");
        loadChutes(one, 1);
        int mark = spawned().size();
        req(one.hurt(this.level.damageSources().generic(), 99999.0F) && one.isDestroyed(),
            "E12: the f-15e with one parachute is destroyed");
        List<MchParachute> chutes = parachutesSince(mark);
        req(chutes.size() == 1 && chutes.get(0).pendingUser() == pilot1 && pig1.getVehicle() == null
                && one.getPassengers().isEmpty() && countParachutes(one) == 0,
            "E12: one parachute: the pilot ejects on it, the pig is put off plainly, got " + chutes.size());

        AbstractMchVehicle none = pinned("f-15e", -15.5, 40.0, 8.5, 0.0F, 0.0F, 0.0F);
        ServerPlayer pilot0 = player("e12pilot0");
        Pig pig0 = pig(-15.5, 45.0, 8.5);
        req(board(pilot0, none, 0) && board(pig0, none, 1), "E12: a player pilot and a seat-1 pig board an f-15e");
        int mark0 = spawned().size();
        req(none.hurt(this.level.damageSources().generic(), 99999.0F) && none.isDestroyed(),
            "E12: the f-15e without a parachute is destroyed");
        req(parachutesSince(mark0).isEmpty() && none.getPassengers().isEmpty() && !pilot0.isPassenger()
                && pig0.getVehicle() == null,
            "E12: no parachute: both are put off plainly and nothing opens");
        this.probes.add(new At(1, () -> {
            req(pilot1.getVehicle() == chutes.get(0) && pig1.getVehicle() == null && pilot0.getVehicle() == null
                    && pig0.getVehicle() == null,
                "E12: next tick only the first pilot rides a parachute");
            one.discard();
            none.discard();
            return "E12 destruction with 1 (pilot on a parachute, pig plain) and 0 (both plain)";
        }));
    }

    private void startMobPilot() {
        AbstractMchVehicle a10 = pinned("a-10", 8.5, 40.0, 8.5, 0.0F, 0.0F, 0.0F);
        Pig pig = pig(8.5, 45.0, 8.5);
        req(board(pig, a10, 0), "E13: a pig takes the a-10's pilot seat on request");
        loadChutes(a10, 1);
        int mark = spawned().size();
        req(a10.hurt(this.level.damageSources().generic(), 99999.0F) && a10.isDestroyed(),
            "E13: the a-10 is destroyed");
        List<MchParachute> chutes = parachutesSince(mark);
        req(chutes.size() == 1 && chutes.get(0).pendingUser() == pig && pig.getVehicle() == null
                && countParachutes(a10) == 0,
            "E13: the pig pilot ejects on a parachute, got " + chutes.size());
        this.probes.add(new At(1, () -> {
            req(pig.getVehicle() == chutes.get(0), "E13: next tick the pig rides it");
            a10.discard();
            return "E13 mob pilot ejects on destruction";
        }));
    }

    // ---- helpers ----

    /** A check that runs once, on test tick {@code due} (the level has ticked {@code due} times since start); its
     *  body returns the verdict or throws. */
    private final class At extends Probe {
        private final int due;
        private final java.util.function.Supplier<String> body;

        At(int due, java.util.function.Supplier<String> body) {
            this.due = due;
            this.body = body;
        }

        @Override
        boolean step() {
            if (ticks() < this.due) {
                return false;
            }
            this.verdict = this.body.get();
            return true;
        }
    }

    /** A vehicle held at its pose: the level still ticks it, and the test puts it back every tick (position, zero
     *  motion, yaw, pitch, roll), so a pilotless plane neither falls nor tumbles. */
    private record Pin(AbstractMchVehicle v, Vec3 pos, float yaw, float pitch, float roll) {
        void apply() {
            if (this.v.isRemoved()) {
                return;
            }
            this.v.setPos(this.pos.x, this.pos.y, this.pos.z);
            this.v.setDeltaMovement(Vec3.ZERO);
            this.v.setYRot(this.yaw);
            this.v.yRotO = this.yaw;
            this.v.setXRot(this.pitch);
            this.v.xRotO = this.pitch;
            this.v.setRollAngle(this.roll);
        }
    }

    /** A plane of {@code config} in the world at the offset, at the given attitude, pinned there. */
    private AbstractMchVehicle pinned(String config, double dx, double dy, double dz, float yaw, float pitch,
                                      float roll) {
        AbstractMchVehicle v = spawn(MchRegistries.PLANE.get(), config, dx, dy, dz);
        Pin pin = new Pin(v, v.position(), yaw, pitch, roll);
        pin.apply();
        this.pins.add(pin);
        return v;
    }

    private Pig pig(double dx, double dy, double dz) {
        Pig pig = EntityType.PIG.create(this.level);
        req(pig != null, "a pig can be created");
        pig.setNoAi(true);
        pig.setPos(this.origin.getX() + dx, this.origin.getY() + dy, this.origin.getZ() + dz);
        this.level.addFreshEntity(pig);
        return pig;
    }

    private static void loadChutes(AbstractMchVehicle v, int n) {
        for (int i = 0; i < n; i++) {
            v.parachuteInventory().setItem(i, new ItemStack(MchRegistries.PARACHUTE_ITEM.get()));
        }
    }

    private static int countParachutes(AbstractMchVehicle v) {
        int n = 0;
        for (int i = 0; i < v.parachuteInventory().getContainerSize(); i++) {
            n += isChute(v.parachuteInventory().getItem(i)) ? 1 : 0;
        }
        return n;
    }

    /** The real {@code mcheli:eject_seat} handler, as if {@code sender} had sent it for {@code v}. */
    private static void eject(AbstractMchVehicle v, ServerPlayer sender) {
        ServerboundEjectSeatPayload.handle(new ServerboundEjectSeatPayload(v.getId()), payloadContext(sender));
    }

    private List<MchParachute> parachutesSince(int index) {
        List<MchParachute> out = new ArrayList<>();
        List<Entity> all = spawned();
        for (int i = index; i < all.size(); i++) {
            if (all.get(i) instanceof MchParachute c) {
                out.add(c);
            }
        }
        return out;
    }

    /** {@code (x, y, z)} through the vehicle's body rotation {@code Ry(-yaw)·Rx(pitch)·Rz(roll)} (JOML floats, as the
     *  entity's {@code bodyRotation}), not translated. */
    private static Vec3 body(AbstractMchVehicle v, double x, double y, double z) {
        Quaternionf q = new Quaternionf()
            .rotateY((float) Math.toRadians(-v.getYRot()))
            .rotateX((float) Math.toRadians(v.getXRot()))
            .rotateZ((float) Math.toRadians(v.getRollAngle()));
        Vector3f p = new Vector3f((float) x, (float) y, (float) z).rotate(q);
        return new Vec3(p.x, p.y, p.z);
    }

    /** Seat {@code s}'s eject point, {@code pos + R·(seat + (0, 2, 0))}. */
    private static Vec3 seatPoint(AbstractMchVehicle v, int s) {
        MCH_SeatInfo seat = v.hostInfo().seatList.get(s);
        return v.position().add(body(v, seat.pos.x(), seat.pos.y() + 2.0, seat.pos.z()));
    }

    /** The reference {@code getTransformedPosition} (EA:3319-3322): {@code MCH_Lib.RotVec3(x, y, z, -yaw, -pitch,
     *  -roll)} (roll about Z, then X, then Y, with the 1.7.10 table trig) plus the vehicle position. */
    private static Vec3 referenceTransform(AbstractMchVehicle v, double x, double y, double z) {
        Vec3d r = new Vec3d(x, y, z)
            .rotateAroundZ(-v.getRollAngle() / 180.0F * (float) Math.PI)
            .rotateAroundX(-v.getXRot() / 180.0F * (float) Math.PI)
            .rotateAroundY(-v.getYRot() / 180.0F * (float) Math.PI);
        return v.position().add(r.x(), r.y(), r.z());
    }

    private static String fmt(Vec3 p) {
        return String.format(Locale.ROOT, "(%.4f, %.4f, %.4f)", p.x, p.y, p.z);
    }

    /** A vehicle of {@code config}, never added to the world. */
    private <T extends AbstractMchVehicle> T created(EntityType<T> type, String config) {
        T v = type.create(this.level);
        req(v != null, "the entity type for " + config + " creates");
        v.setConfigName(config);
        req(v.hostInfo() != null && config.equalsIgnoreCase(v.hostInfo().name), "the " + config + " config is loaded");
        return v;
    }

    private <T extends AbstractMchVehicle> T spawn(EntityType<T> type, String config, double dx, double dy,
                                                   double dz) {
        T v = created(type, config);
        v.setPos(this.origin.getX() + dx, this.origin.getY() + dy, this.origin.getZ() + dz);
        this.level.addFreshEntity(v);
        return v;
    }

    /** A plane loaded from {@code tag} (its own config inside), never added to the world. */
    private AbstractMchVehicle loaded(CompoundTag tag) {
        AbstractMchVehicle v = MchRegistries.PLANE.get().create(this.level);
        req(v != null, "a plane entity creates");
        CompoundTag t = tag.copy();
        t.remove("UUID"); // a fresh identity, so the original stays the only one with its UUID
        v.load(t);
        return v;
    }

    private RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), this.level.registryAccess());
    }

    private CompoundTag slotEntry(int slotAc, ItemStack stack) {
        CompoundTag entry = new CompoundTag();
        entry.putByte("SlotAC", (byte) slotAc);
        return (CompoundTag) stack.save(this.level.registryAccess(), entry);
    }

    private ServerPlayer player(String label) {
        ServerPlayer p = newTestPlayer(label);
        p.setPos(this.origin.getX() + 0.5, this.origin.getY() + 20.0, this.origin.getZ() + 0.5);
        this.players.add(p);
        return p;
    }

    /** Seat {@code p} in {@code seat} of {@code v} (a forced mount, as a scripted crew would board). */
    private static boolean board(Entity p, AbstractMchVehicle v, int seat) {
        v.preferSeat(p, seat);
        return p.startRiding(v, true) && v.seatIndexOf(p) == seat;
    }

    private static void gui(ServerboundVehicleGuiPayload payload, ServerPlayer sender) {
        ServerboundVehicleGuiPayload.handle(payload, payloadContext(sender));
    }

    private static boolean isChute(ItemStack s) {
        return s.is(MchRegistries.PARACHUTE_ITEM.get()) && s.getCount() == 1;
    }

    private static int countChutes(ServerPlayer p) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            n += p.getInventory().getItem(i).is(MchRegistries.PARACHUTE_ITEM.get())
                ? p.getInventory().getItem(i).getCount() : 0;
        }
        return n;
    }

    private static AABB around(Entity e) {
        return AABB.ofSize(e.position(), 8.0, 8.0, 8.0);
    }

    /** Every dropped item in {@code box} as {@code id=count} sorted by id, each item entity discarded after it is
     *  counted. */
    private String collect(AABB box) {
        Map<String, Integer> counts = new TreeMap<>();
        for (ItemEntity e : this.level.getEntitiesOfClass(ItemEntity.class, box)) {
            counts.merge(BuiltInRegistries.ITEM.getKey(e.getItem().getItem()).toString(), e.getItem().getCount(),
                Integer::sum);
            e.discard();
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> en : counts.entrySet()) {
            sb.append(sb.length() > 0 ? "," : "").append(en.getKey()).append('=').append(en.getValue());
        }
        return sb.toString();
    }

    @Override
    protected void cleanup() {
        for (ServerPlayer p : this.players) {
            if (p.containerMenu != p.inventoryMenu) {
                p.closeContainer();
            }
            if (p.isPassenger()) {
                p.stopRiding();
            }
        }
    }

    private static void req(boolean cond, String what) {
        if (!cond) {
            throw new IllegalStateException(what);
        }
    }
}
