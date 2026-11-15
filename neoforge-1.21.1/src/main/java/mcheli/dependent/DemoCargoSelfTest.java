package mcheli.dependent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import mcheli.dependent.control.ServerboundVehicleGuiPayload;
import mcheli.dependent.entity.AbstractMchVehicle;
import mcheli.dependent.menu.MchVehicleMenu;
import mcheli.dependent.registry.MchRegistries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The cargo hold (config {@code InventorySize}, the reference aircraft's own {@code W_EntityContainer} inventory),
 * headless. Every check runs synchronously in {@link #start}; the first unmet requirement is the FAIL reason.
 * <ul>
 *   <li><b>K1 sizes</b> — the hold has the config's slot count, clamped to 54 at parse (the c5's 64), and its chest
 *       screen {@code size / 9} rows (the centauro's 10 slots show 9). {@code cargoSize} is also the HUD
 *       {@code inventory} value.</li>
 *   <li><b>K2 sneak click</b> — on the bell206l (27 slots) a sneaking click with an empty hand, or holding any item but
 *       the wrench, opens the vanilla 3-row chest menu over the hold and does not board (reference
 *       {@code interactFirst}:4326-4329); the seat click hands a sneaking click on; a sneaking wrench click still
 *       cycles the paint and opens nothing; a plain click never opens it; a wreck and the hold-less ah-64 open nothing;
 *       a wreck keeps its cargo until it is removed.</li>
 *   <li><b>K3 range</b> — the menu's {@code stillValid} (reference {@code isUseableByPlayer}:1018-1025): 8 blocks on
 *       foot, 64 for a seated rider, never once the vehicle is removed.</li>
 *   <li><b>K4 shift-click</b> — the chest menu's {@code quickMoveStack}: a stack from the player goes to the first free
 *       cargo slot, a second one merges onto it, a fuel can goes to the cargo too (the chest screen has no fuel slot,
 *       as in the reference), and a cargo stack goes back to the player's last hotbar slot.</li>
 *   <li><b>K5 riding GUI button</b> — the real {@code mcheli:vehicle_gui} handler: OPEN_CARGO from the pilot (it
 *       replaces the riding menu) and from a passenger opens the hold; a player not aboard is refused; the riding menu
 *       stays pilot-only and keeps its 3 fuel slots + 36 with and without a hold.</li>
 *   <li><b>K6 NBT</b> — the hold saves as {@code Items} with {@code Slot} bytes (golden), reloads into the same slots and
 *       re-saves identically; a save without the key loads an empty hold; a hold-less vehicle writes no key; under a
 *       smaller config (a save loaded as the 9-slot mh-60g, or a live config change) the stack without a slot is kept
 *       aside, saved back with its slot and returned under the larger config; a chest screen on a replaced hold closes.</li>
 *   <li><b>K7 spill</b> — a discard and a kill spill the hold, the stacks kept aside and the fuel cans; a chunk unload
 *       and a dimension change spill nothing and keep the items (the fuel-can dupe guard).</li>
 * </ul>
 * The chest screen itself (the vanilla one) and the riding GUI's Inventory button are client-only (a runClient check).
 */
public final class DemoCargoSelfTest extends MchSelfTest {

    private static final InteractionHand HAND = InteractionHand.MAIN_HAND;
    /** The bell206l's saved hold: 3 diamonds in slot 5, 64 sticks in slot 26 (recorded on the first run). */
    private static final String K6_ITEMS =
        "[{Slot:5b,count:3,id:\"minecraft:diamond\"},{Slot:26b,count:64,id:\"minecraft:stick\"}]";

    private final List<ServerPlayer> players = new ArrayList<>();

    @Override
    protected void start() {
        try {
            String sizes = testSizes();
            testSneakClick();
            testRange();
            testShiftClick();
            testGuiButton();
            testNbt();
            String spill = testSpill();
            finish(true, "sizes {}; sneak click (3-row chest, wrench paint kept, wreck and hold-less refused), range "
                + "8/64, shift-click both ways, GUI button any seat, Items/Slot NBT round trip, legacy save empty, "
                + "smaller config kept aside, spill {}", sizes, spill);
        } catch (IllegalStateException e) {
            fail("{}", e.getMessage());
        }
    }

    // ---- K1 ----

    private String testSizes() {
        StringBuilder sb = new StringBuilder();
        size(sb, MchRegistries.HELI.get(), "bell206l", 27, 3, 0.5);
        size(sb, MchRegistries.HELI.get(), "ch47", 54, 6, 4.5);
        size(sb, MchRegistries.PLANE.get(), "c5", 54, 6, 8.5);           // InventorySize = 64, clamped at parse
        size(sb, MchRegistries.TANK.get(), "centauro", 10, 1, 12.5);
        size(sb, MchRegistries.HELI.get(), "mh-60g", 9, 1, 16.5);
        size(sb, MchRegistries.HELI.get(), "ah-64", 0, 0, 20.5);
        LOG.info("[CARGO SELF-TEST] K1 sizes OK ({})", sb);
        return sb.toString();
    }

    private void size(StringBuilder sb, EntityType<? extends AbstractMchVehicle> type, String config, int slots,
                      int rows, double dz) {
        AbstractMchVehicle v = spawn(type, config, -40.5, 20.0, dz);
        req(v.cargoSize() == slots && v.cargoRows() == rows && v.cargoInventory().getContainerSize() == slots,
            config + ": a hold of " + slots + " slots in " + rows + " rows, got " + v.cargoSize() + " / "
                + v.cargoRows() + " rows / container " + v.cargoInventory().getContainerSize());
        sb.append(sb.length() > 0 ? " " : "").append(config).append('=').append(slots).append('/').append(rows);
        v.discard();
    }

    // ---- K2 ----

    private void testSneakClick() {
        AbstractMchVehicle bell = spawn(MchRegistries.HELI.get(), "bell206l", 0.5, 20.0, 0.5);
        ServerPlayer p = player("k2", bell, 3.0);
        p.setShiftKeyDown(true);
        req(!bell.interactAt(p, Vec3.ZERO, HAND).consumesAction() && p.containerMenu == p.inventoryMenu,
            "a sneaking seat click is handed on to the vehicle click and opens nothing itself");
        req(bell.interact(p, HAND).consumesAction(), "a sneaking empty-hand click on the bell206l is consumed");
        ChestMenu cm = chestOf(p, bell);
        req(cm.getType() == MenuType.GENERIC_9x3 && cm.getRowCount() == 3 && cm.slots.size() == 27 + 36,
            "the bell206l's hold opens as the vanilla 3-row chest menu (27 + 36 slots), got rows " + cm.getRowCount()
                + " slots " + cm.slots.size());
        req(!p.isPassenger(), "the sneaking click does not board");
        p.closeContainer();

        int skins = Math.max(1, bell.hostInfo().getTextureNameCount());
        int skin0 = bell.getSkinIndex();
        p.setItemInHand(HAND, new ItemStack(MchRegistries.WRENCH.get()));
        req(!bell.interact(p, HAND).consumesAction() && p.containerMenu == p.inventoryMenu
                && bell.getSkinIndex() == (skin0 + 1) % skins,
            "a sneaking wrench click still cycles the paint (" + skin0 + " -> " + bell.getSkinIndex() + " of " + skins
                + ") and opens no hold");
        p.setItemInHand(HAND, new ItemStack(Items.COBBLESTONE));
        req(bell.interact(p, HAND).consumesAction() && chestOf(p, bell) != null,
            "a sneaking click holding another item opens the hold (only the wrench is set apart)");
        p.closeContainer();
        p.setItemInHand(HAND, ItemStack.EMPTY);
        p.setShiftKeyDown(false);
        bell.interact(p, HAND); // a plain click boards (the bell206l has no canopy)
        req(p.containerMenu == p.inventoryMenu, "a plain click never opens the hold");
        if (p.isPassenger()) {
            p.stopRiding();
        }
        bell.discard();

        AbstractMchVehicle ah = spawn(MchRegistries.HELI.get(), "ah-64", 4.5, 20.0, 0.5);
        ServerPlayer q = player("k2ah64", ah, 3.0);
        q.setShiftKeyDown(true);
        req(!ah.interact(q, HAND).consumesAction() && q.containerMenu == q.inventoryMenu && !q.isPassenger(),
            "a sneaking click on the hold-less ah-64 passes and opens nothing");
        req(!ah.openCargo(q), "the ah-64 has no hold to open");
        ah.discard();

        AbstractMchVehicle cen = spawn(MchRegistries.TANK.get(), "centauro", 8.5, 20.0, 0.5);
        ServerPlayer c = player("k2cen", cen, 3.0);
        c.setShiftKeyDown(true);
        req(cen.interact(c, HAND).consumesAction(), "a sneaking click on the centauro is consumed");
        ChestMenu cc = chestOf(c, cen);
        req(cc.getType() == MenuType.GENERIC_9x1 && cc.slots.size() == 9 + 36
                && cen.cargoInventory().getContainerSize() == 10,
            "the centauro's 10-slot hold opens as a 1-row chest (9 + 36 slots), got rows " + cc.getRowCount()
                + " slots " + cc.slots.size());
        c.closeContainer();
        cen.discard();

        // A wreck: the click is refused (reference interactFirst gates on isDestroyed), the cargo stays in the hold,
        // and it spills when the wreck is removed. High up, so the destruction blast touches nothing.
        AbstractMchVehicle wreck = spawn(MchRegistries.HELI.get(), "bell206l", 0.5, 150.0, 40.5);
        wreck.cargoInventory().setItem(4, new ItemStack(Items.DIAMOND, 2));
        req(wreck.hurt(this.level.damageSources().generic(), 99999.0F) && wreck.isDestroyed(),
            "the bell206l is wrecked");
        ServerPlayer w = player("k2wreck", wreck, 3.0);
        w.setShiftKeyDown(true);
        req(!wreck.interact(w, HAND).consumesAction() && w.containerMenu == w.inventoryMenu,
            "a sneaking click on a wreck opens no hold");
        req(ItemStack.matches(wreck.cargoInventory().getItem(4), new ItemStack(Items.DIAMOND, 2)),
            "the destruction leaves the cargo in the hold");
        AABB box = around(wreck);
        wreck.discard();
        String dropped = collect(box);
        req(dropped.equals("minecraft:diamond=2"), "the removed wreck spills its cargo, got " + dropped);
        LOG.info("[CARGO SELF-TEST] K2 sneak click OK (3-row and 1-row chests, wrench paint kept, plain click, wreck "
            + "and ah-64 refused, wreck spill {})", dropped);
    }

    // ---- K3 ----

    private void testRange() {
        AbstractMchVehicle bell = spawn(MchRegistries.HELI.get(), "bell206l", 16.5, 20.0, 0.5);
        ServerPlayer p = player("k3foot", bell, 3.0);
        p.setShiftKeyDown(true);
        bell.interact(p, HAND);
        ChestMenu cm = chestOf(p, bell);
        boolean near = cm.stillValid(p);
        p.setPos(bell.getX() + 7.9, bell.getY(), bell.getZ());
        boolean at79 = cm.stillValid(p);
        p.setPos(bell.getX() + 8.1, bell.getY(), bell.getZ());
        boolean at81 = cm.stillValid(p);
        req(near && at79 && !at81, "on foot the hold is usable within 8 blocks only, got 3: " + near + " 7.9: " + at79
            + " 8.1: " + at81);
        p.closeContainer();

        // A seated rider keeps it within 64 blocks. Moving the hull without a tick leaves the rider where he sat, so
        // his distance grows; a player on foot at the rider's spot is out of reach at once.
        ServerPlayer r = player("k3rider", bell, 0.0);
        req(board(r, bell, 1), "a rider takes seat 1 of the bell206l");
        req(bell.openCargo(r), "the rider opens the hold");
        ChestMenu rm = chestOf(r, bell);
        Vec3 home = bell.position();
        bell.setPos(home.x + 30.0, home.y, home.z);
        double d30 = Math.sqrt(r.distanceToSqr(bell));
        p.setPos(r.getX(), r.getY(), r.getZ());
        boolean rider30 = rm.stillValid(r);
        boolean foot30 = bell.cargoInventory().stillValid(p);
        bell.setPos(home.x + 70.0, home.y, home.z);
        boolean rider70 = rm.stillValid(r);
        bell.setPos(home.x, home.y, home.z);
        req(d30 > 8.0 && d30 < 64.0 && rider30 && !foot30 && !rider70, "a seated rider reaches the hold within 64 "
            + "blocks and a player on foot does not: rider at " + d30 + " " + rider30 + ", on foot " + foot30
            + ", rider at 70 " + rider70);
        r.stopRiding();
        bell.discard();
        req(!rm.stillValid(r), "the hold of a removed vehicle is no longer usable");
        r.closeContainer();
        LOG.info("[CARGO SELF-TEST] K3 range OK (on foot 8, seated 64, removed none)");
    }

    // ---- K4 ----

    private void testShiftClick() {
        AbstractMchVehicle bell = spawn(MchRegistries.HELI.get(), "bell206l", 24.5, 20.0, 0.5);
        ServerPlayer p = player("k4", bell, 3.0);
        p.getInventory().clearContent();
        p.getInventory().setItem(9, new ItemStack(Items.COBBLESTONE, 20)); // first main slot
        p.getInventory().setItem(10, new ItemStack(MchRegistries.FUEL.get()));
        p.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 5));  // first hotbar slot
        p.setShiftKeyDown(true);
        bell.interact(p, HAND);
        ChestMenu cm = chestOf(p, bell);
        SimpleContainer hold = bell.cargoInventory();
        int base = cm.getRowCount() * 9; // the player's main inventory starts after the cargo rows, the hotbar 27 later
        req(cm.slots.get(base).container == p.getInventory() && cm.slots.get(base).getContainerSlot() == 9
                && cm.slots.get(base + 27).getContainerSlot() == 0,
            "the chest menu lists the cargo, then the player's main inventory, then the hotbar");
        cm.quickMoveStack(p, base);
        boolean in1 = hold.getItem(0).is(Items.COBBLESTONE) && hold.getItem(0).getCount() == 20
            && p.getInventory().getItem(9).isEmpty();
        cm.quickMoveStack(p, base + 27);
        boolean in2 = hold.getItem(0).getCount() == 25 && p.getInventory().getItem(0).isEmpty();
        cm.quickMoveStack(p, base + 1);
        boolean can = hold.getItem(1).is(MchRegistries.FUEL.get()) && bell.fuelInventory().isEmpty()
            && p.getInventory().getItem(10).isEmpty();
        cm.quickMoveStack(p, 0);
        boolean out = hold.getItem(0).isEmpty() && p.getInventory().getItem(8).is(Items.COBBLESTONE)
            && p.getInventory().getItem(8).getCount() == 25;
        req(in1, "shift-click moves 20 cobblestone from the player into cargo slot 0, got " + hold.getItem(0));
        req(in2, "a second shift-click merges 5 more onto it, got " + hold.getItem(0));
        req(can, "a fuel can shift-clicks into the cargo (the chest screen has no fuel slot), got " + hold.getItem(1));
        req(out, "shift-clicking the cargo stack moves it to the player's last hotbar slot, got "
            + p.getInventory().getItem(8));
        p.closeContainer();
        p.getInventory().clearContent();
        hold.clearContent();
        bell.discard();
        LOG.info("[CARGO SELF-TEST] K4 shift-click OK (player -> cargo, merge, fuel can -> cargo, cargo -> player)");
    }

    // ---- K5 ----

    private void testGuiButton() {
        AbstractMchVehicle bell = spawn(MchRegistries.HELI.get(), "bell206l", 32.5, 20.0, 0.5);
        ServerPlayer pilot = player("k5pilot", bell, 0.0);
        ServerPlayer rider = player("k5rider", bell, 0.0);
        ServerPlayer outsider = player("k5out", bell, 3.0);
        req(board(pilot, bell, 0) && board(rider, bell, 1), "a pilot and a passenger board the bell206l");
        gui(ServerboundVehicleGuiPayload.open(bell.getId()), pilot);
        req(pilot.containerMenu instanceof MchVehicleMenu m && ridingMenuUnchanged(m, bell),
            "the riding menu of a vehicle with a hold keeps its 3 fuel slots + 36");
        gui(ServerboundVehicleGuiPayload.openCargo(bell.getId()), pilot);
        chestOf(pilot, bell); // the pilot's Inventory button swaps the riding menu for the hold
        gui(ServerboundVehicleGuiPayload.openCargo(bell.getId()), rider);
        chestOf(rider, bell); // any seat (reference onPacketIndOpenScreen: getAircraft_RiddenOrControl)
        gui(ServerboundVehicleGuiPayload.openCargo(bell.getId()), outsider);
        req(outsider.containerMenu == outsider.inventoryMenu, "a player not aboard cannot open the hold by payload");
        gui(ServerboundVehicleGuiPayload.open(bell.getId()), rider);
        req(rider.containerMenu instanceof ChestMenu, "the riding menu stays pilot-only");
        pilot.closeContainer();
        rider.closeContainer();
        pilot.stopRiding();
        rider.stopRiding();
        bell.discard();

        AbstractMchVehicle ah = spawn(MchRegistries.HELI.get(), "ah-64", 36.5, 20.0, 0.5);
        ServerPlayer ap = player("k5ah64", ah, 0.0);
        req(board(ap, ah, 0), "a pilot boards the ah-64");
        gui(ServerboundVehicleGuiPayload.open(ah.getId()), ap);
        req(ap.containerMenu instanceof MchVehicleMenu m2 && ridingMenuUnchanged(m2, ah),
            "the riding menu of the hold-less ah-64 is unchanged (3 fuel slots + 36)");
        gui(ServerboundVehicleGuiPayload.openCargo(ah.getId()), ap);
        req(ap.containerMenu instanceof MchVehicleMenu, "OPEN_CARGO on a vehicle without a hold changes nothing");
        ap.closeContainer();
        ap.stopRiding();
        ah.discard();
        LOG.info("[CARGO SELF-TEST] K5 GUI button OK (pilot and passenger open, outsider refused, riding menu kept)");
    }

    private static boolean ridingMenuUnchanged(MchVehicleMenu m, AbstractMchVehicle v) {
        return m.slots.size() == AbstractMchVehicle.FUEL_SLOTS + 36 && m.slots.get(0).container == v.fuelInventory()
            && m.slots.get(AbstractMchVehicle.FUEL_SLOTS - 1).container == v.fuelInventory()
            && m.slots.get(AbstractMchVehicle.FUEL_SLOTS).getContainerSlot() == 9;
    }

    private static void gui(ServerboundVehicleGuiPayload payload, ServerPlayer sender) {
        ServerboundVehicleGuiPayload.handle(payload, payloadContext(sender));
    }

    // ---- K6 ----

    private void testNbt() {
        AbstractMchVehicle bell = spawn(MchRegistries.HELI.get(), "bell206l", 0.5, 20.0, 16.5);
        bell.cargoInventory().setItem(5, new ItemStack(Items.DIAMOND, 3));
        bell.cargoInventory().setItem(26, new ItemStack(Items.STICK, 64));
        CompoundTag tag = bell.saveWithoutId(new CompoundTag());
        String items = String.valueOf(tag.get("Items"));
        LOG.info("[CARGO SELF-TEST] K6 observed Items={}", items);
        req(items.equals(K6_ITEMS), "the hold saves as Items with Slot bytes: expected " + K6_ITEMS + ", got " + items);

        AbstractMchVehicle back = loaded(tag);
        req(holds(back, 5, Items.DIAMOND, 3) && holds(back, 26, Items.STICK, 64) && filled(back) == 2,
            "the hold reloads into the same slots");
        String resave = String.valueOf(back.saveWithoutId(new CompoundTag()).get("Items"));
        req(resave.equals(K6_ITEMS), "the reloaded hold re-saves identically, got " + resave);

        CompoundTag legacy = tag.copy();
        legacy.remove("Items");
        AbstractMchVehicle old = loaded(legacy);
        req(old.cargoInventory().getContainerSize() == 27 && old.cargoInventory().isEmpty(),
            "a save without Items (before the hold) loads an empty 27-slot hold");

        AbstractMchVehicle ah = MchRegistries.HELI.get().create(this.level);
        ah.setConfigName("ah-64");
        req(!ah.saveWithoutId(new CompoundTag()).contains("Items"), "a vehicle without a hold writes no Items key");

        // The 27-slot save loaded as the 9-slot mh-60g: slot 5 stays, the stick in slot 26 is kept aside, saved back
        // with its slot, and is in slot 26 again once the save loads as the bell206l.
        CompoundTag small = tag.copy();
        small.putString("Config", "mh-60g");
        AbstractMchVehicle shrunk = loaded(small);
        boolean shrunkHold = shrunk.cargoInventory().getContainerSize() == 9 && holds(shrunk, 5, Items.DIAMOND, 3)
            && filled(shrunk) == 1;
        CompoundTag shrunkTag = shrunk.saveWithoutId(new CompoundTag());
        String shrunkItems = String.valueOf(shrunkTag.get("Items"));
        req(shrunkHold && shrunkItems.equals(K6_ITEMS), "under the 9-slot config slot 5 stays and the stick is kept "
            + "aside and saved with its slot, got hold " + shrunkHold + " Items " + shrunkItems);
        shrunkTag.putString("Config", "bell206l");
        AbstractMchVehicle regrown = loaded(shrunkTag);
        req(holds(regrown, 26, Items.STICK, 64) && holds(regrown, 5, Items.DIAMOND, 3) && filled(regrown) == 2,
            "back under the 27-slot config the stick is in slot 26 again");

        // The same live, with the chest screen open: the replaced hold closes it.
        ServerPlayer p = player("k6live", bell, 3.0);
        p.setShiftKeyDown(true);
        bell.interact(p, HAND);
        ChestMenu cm = chestOf(p, bell);
        bell.setConfigName("mh-60g");
        boolean liveSmall = bell.cargoInventory().getContainerSize() == 9 && holds(bell, 5, Items.DIAMOND, 3)
            && filled(bell) == 1 && !cm.stillValid(p);
        bell.setConfigName("bell206l");
        boolean liveBack = bell.cargoInventory().getContainerSize() == 27 && holds(bell, 26, Items.STICK, 64)
            && holds(bell, 5, Items.DIAMOND, 3);
        req(liveSmall && liveBack, "a live config change keeps the stack without a slot aside and returns it, and "
            + "closes the screen on the old hold: small " + liveSmall + " back " + liveBack);
        p.closeContainer();
        bell.cargoInventory().clearContent();
        bell.discard();
        LOG.info("[CARGO SELF-TEST] K6 NBT OK (Items/Slot golden, reload, legacy empty, no key without a hold, "
            + "smaller config kept aside on load and live)");
    }

    // ---- K7 ----

    private String testSpill() {
        AbstractMchVehicle a = spawn(MchRegistries.HELI.get(), "bell206l", -16.5, 150.0, 0.5);
        a.cargoInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 40));
        a.cargoInventory().setItem(13, new ItemStack(Items.DIAMOND, 2));
        a.fuelInventory().setItem(0, new ItemStack(MchRegistries.FUEL.get()));
        AABB boxA = around(a);
        a.discard();
        String discarded = collect(boxA);
        req(discarded.equals("mcheli:fuel=1,minecraft:cobblestone=40,minecraft:diamond=2")
                && a.cargoInventory().isEmpty() && a.fuelInventory().isEmpty(),
            "a discarded vehicle spills its cargo and its fuel can, and keeps none, got " + discarded);

        AbstractMchVehicle k = spawn(MchRegistries.HELI.get(), "bell206l", -16.5, 150.0, 12.5);
        k.cargoInventory().setItem(3, new ItemStack(Items.STICK, 7));
        AABB boxK = around(k);
        k.kill();
        String killed = collect(boxK);
        req(killed.equals("minecraft:stick=7"), "a killed vehicle spills its cargo, got " + killed);

        AbstractMchVehicle u = spawn(MchRegistries.HELI.get(), "bell206l", -16.5, 150.0, 24.5);
        u.cargoInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 40));
        AABB boxU = around(u);
        u.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        String unloaded = collect(boxU);
        boolean uKept = u.cargoInventory().getItem(0).getCount() == 40;

        AbstractMchVehicle d = spawn(MchRegistries.HELI.get(), "bell206l", -16.5, 150.0, 36.5);
        d.cargoInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 40));
        AABB boxD = around(d);
        d.remove(Entity.RemovalReason.CHANGED_DIMENSION);
        String moved = collect(boxD);
        boolean dKept = d.cargoInventory().getItem(0).getCount() == 40;
        req(unloaded.isEmpty() && uKept && moved.isEmpty() && dKept, "a chunk unload and a dimension change spill "
            + "nothing and keep the cargo (dupe guard), got unload '" + unloaded + "' kept " + uKept + ", dimension '"
            + moved + "' kept " + dKept);

        // Stacks kept aside spill too: the 27-slot save of K6's shape loaded as the 9-slot mh-60g.
        AbstractMchVehicle src = MchRegistries.HELI.get().create(this.level);
        src.setConfigName("bell206l");
        src.cargoInventory().setItem(5, new ItemStack(Items.DIAMOND, 3));
        src.cargoInventory().setItem(26, new ItemStack(Items.STICK, 64));
        CompoundTag tag = src.saveWithoutId(new CompoundTag());
        tag.putString("Config", "mh-60g");
        AbstractMchVehicle s = loaded(tag);
        s.setPos(this.origin.getX() - 16.5, this.origin.getY() + 150.0, this.origin.getZ() + 44.5);
        AABB boxS = around(s);
        s.discard();
        String aside = collect(boxS);
        req(aside.equals("minecraft:diamond=3,minecraft:stick=64"),
            "a vehicle spills the stacks it kept aside with its hold, got " + aside);
        LOG.info("[CARGO SELF-TEST] K7 spill OK (discard {}, kill {}, unload none, dimension none, aside {})",
            discarded, killed, aside);
        return "discard " + discarded + ", kill " + killed + ", unload/dimension none, aside " + aside;
    }

    // ---- helpers ----

    private <T extends AbstractMchVehicle> T spawn(EntityType<T> type, String config, double dx, double dy,
                                                   double dz) {
        T v = type.create(this.level);
        req(v != null, "the entity type for " + config + " creates");
        v.setConfigName(config);
        req(v.hostInfo() != null && config.equalsIgnoreCase(v.hostInfo().name), "the " + config + " config is loaded");
        v.setPos(this.origin.getX() + dx, this.origin.getY() + dy, this.origin.getZ() + dz);
        this.level.addFreshEntity(v);
        return v;
    }

    /** A helicopter loaded from {@code tag} (its own config inside), never added to the world. */
    private AbstractMchVehicle loaded(CompoundTag tag) {
        AbstractMchVehicle v = MchRegistries.HELI.get().create(this.level);
        req(v != null, "a helicopter entity creates");
        CompoundTag t = tag.copy();
        t.remove("UUID"); // a fresh identity, so the original stays the only one with its UUID
        v.load(t);
        return v;
    }

    private ServerPlayer player(String label, AbstractMchVehicle near, double dx) {
        ServerPlayer p = newTestPlayer(label);
        p.setPos(near.getX() + dx, near.getY(), near.getZ());
        this.players.add(p);
        return p;
    }

    /** Seat {@code p} in {@code seat} of {@code v} (a forced mount, as a scripted crew would board). */
    private static boolean board(Entity p, AbstractMchVehicle v, int seat) {
        v.preferSeat(p, seat);
        return p.startRiding(v, true) && v.seatIndexOf(p) == seat;
    }

    /** The chest menu {@code p} has open, which must be over {@code v}'s hold. */
    private static ChestMenu chestOf(ServerPlayer p, AbstractMchVehicle v) {
        req(p.containerMenu instanceof ChestMenu, p.getGameProfile().getName() + " has a chest menu open, got "
            + p.containerMenu.getClass().getSimpleName());
        ChestMenu cm = (ChestMenu) p.containerMenu;
        req(cm.getContainer() == v.cargoInventory(), "the open chest menu is over the vehicle's hold");
        return cm;
    }

    private static boolean holds(AbstractMchVehicle v, int slot, Item item, int count) {
        ItemStack s = v.cargoInventory().getItem(slot);
        return s.is(item) && s.getCount() == count;
    }

    private static int filled(AbstractMchVehicle v) {
        int n = 0;
        for (int i = 0; i < v.cargoInventory().getContainerSize(); i++) {
            n += v.cargoInventory().getItem(i).isEmpty() ? 0 : 1;
        }
        return n;
    }

    private static AABB around(Entity e) {
        return AABB.ofSize(e.position(), 8.0, 8.0, 8.0);
    }

    /** Every dropped item in {@code box} as {@code id=count} sorted by id (the stacks are split as they drop), each
     *  item entity discarded after it is counted. */
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
