package mcheli.dependent.client.screen;

import mcheli.MCHeli;
import mcheli.agnostic.weapon.MCH_WeaponInfo;
import mcheli.agnostic.weapon.WeaponSlot;
import mcheli.dependent.control.ServerboundVehicleGuiPayload;
import mcheli.dependent.entity.AbstractMchVehicle;
import mcheli.dependent.menu.MchVehicleMenu;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The riding GUI — the 1.21.1 port of {@code MCH_AircraftGui}. Same geometry as the reference (210×236, drawn from the
 * bundled {@code textures/gui/gui.png}), same widgets in the same places: the fuel column on the left with its vertical
 * gauge and percentage, the weapon name + ammo readout, and Reload / weapon-cycle / Close buttons.
 *
 * <p>Reference geometry, reproduced exactly: background {@code (0,0,210,236)}; fuel bar 12 px wide at {@code x+57},
 * growing UP from {@code y+80} to a height of {@code fuelP × 50}, sourced from the sheet at {@code (215,0)}; the fuel
 * percentage at {@code (30,65)}, red below 20 %; the weapon name at {@code (79,30)}; the ammo readout
 * {@code "%4d/%4d"} at {@code (145,70)}, red when empty and green when full; Reload at {@code (85,40,50,20)}; the
 * weapon-cycle pair at {@code (140,40)} / {@code (160,40)}, both 20×20; Close at {@code (160,10,40,20)}.
 *
 * <p>All state changes are SERVERBOUND: the button ships a {@link ServerboundVehicleGuiPayload} and the server decides
 * (it re-checks grounded/not-full/affordable). The client only mirrors what the entity has synced.
 */
public class MchVehicleScreen extends AbstractContainerScreen<MchVehicleMenu> {

    private static final ResourceLocation TEXTURE =
        ResourceLocation.fromNamespaceAndPath(MCHeli.MODID, "textures/gui/gui.png");
    /** The reference's "full/OK" green, {@code 2675784} = {@code 0x28D448} (MCH_AircraftGui uses it for both the fuel
     *  percentage above 20 % and a full ammo readout). */
    private static final int GREEN = 0x28D448;

    private final AbstractMchVehicle vehicle;
    private Button reload;
    private Button next;
    private Button prev;
    /** Which weapon the GUI is SHOWING (the reference's {@code currentWeaponId}) — independent of the one selected for
     *  firing, so you can inspect and rearm a weapon you are not currently holding. */
    private int shownWeapon;

    public MchVehicleScreen(MchVehicleMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.vehicle = menu.vehicle();
        this.imageWidth = 210;
        this.imageHeight = 236;
    }

    @Override
    protected void init() {
        super.init();
        int weapons = this.vehicle != null ? this.vehicle.weaponCount() : 0;
        this.shownWeapon = 0;

        this.reload = addRenderableWidget(Button.builder(Component.translatable("gui.mcheli.reload"), b -> sendReload())
            .bounds(this.leftPos + 85, this.topPos + 40, 50, 20).build());
        this.next = addRenderableWidget(Button.builder(Component.literal("<<"), b -> cycle(-1))
            .bounds(this.leftPos + 140, this.topPos + 40, 20, 20).build());
        this.prev = addRenderableWidget(Button.builder(Component.literal(">>"), b -> cycle(1))
            .bounds(this.leftPos + 160, this.topPos + 40, 20, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.mcheli.close"), b -> onClose())
            .bounds(this.leftPos + 160, this.topPos + 10, 40, 20).build());

        this.next.active = weapons >= 2;
        this.prev.active = weapons >= 2;
        this.reload.active = canReload();
    }

    private void cycle(int step) {
        int n = this.vehicle != null ? this.vehicle.weaponCount() : 0;
        if (n > 0) {
            this.shownWeapon = Math.floorMod(this.shownWeapon + step, n);
        }
        this.reload.active = canReload();
    }

    private void sendReload() {
        if (this.vehicle != null && canReload()) {
            PacketDistributor.sendToServer(
                ServerboundVehicleGuiPayload.reload(this.vehicle.getId(), this.shownWeapon));
            this.reload.active = false; // the server's answer arrives as synced ammo; re-enabled by the tick below
        }
    }

    /**
     * The client's view of the reference {@code canPlayerSupplyAmmo}: the weapon must have a finite reserve, not be
     * full, the vehicle must be grounded, AND the player must be able to afford the weapon's {@code Item =} ammo cost
     * (most bundled weapons need iron ingots + gunpowder). The server re-checks all of it, so a stale client cannot
     * cheat — this only decides whether the Reload button looks enabled.
     */
    private boolean canReload() {
        if (this.vehicle == null) {
            return false;
        }
        WeaponSlot w = this.vehicle.weaponAt(this.shownWeapon);
        if (w == null || !w.hasEconomy() || !this.vehicle.canSupply()) {
            return false;
        }
        if (this.vehicle.ammoOf(this.shownWeapon) >= w.maxAmmo()) {
            return false;
        }
        return canAfford(w);
    }

    /** True if the local player holds enough of every ammo item the weapon requires (reference {@code canPlayerSupplyAmmo}
     *  round-item loop). No creative bypass — the reference consumes items in every game mode. */
    private boolean canAfford(WeaponSlot w) {
        if (this.minecraft == null || this.minecraft.player == null) {
            return false;
        }
        for (MCH_WeaponInfo.RoundItem ri : w.info.roundItems) {
            if (countHeld(ri) < ri.num) {
                return false;
            }
        }
        return true;
    }

    /** How many of {@code ri} the local player holds in the MAIN inventory (0..35), matched by registry path — the
     *  same match the server uses. */
    private int countHeld(MCH_WeaponInfo.RoundItem ri) {
        Player p = this.minecraft.player;
        Item want = itemOf(ri);
        if (want == Items.AIR) {
            return 0;
        }
        int n = 0;
        for (int i = 0; i < 36 && i < p.getInventory().getContainerSize(); i++) {
            if (p.getInventory().getItem(i).is(want)) {
                n += p.getInventory().getItem(i).getCount();
            }
        }
        return n;
    }

    /** Resolve a {@code RoundItem}'s config name (e.g. {@code "iron_ingot"}) to a vanilla item; AIR if it does not
     *  exist in 1.21 (e.g. legacy metadata items like {@code dye}). */
    private static Item itemOf(MCH_WeaponInfo.RoundItem ri) {
        if (ri.itemName == null || ri.itemName.isEmpty()) {
            return Items.AIR;
        }
        ResourceLocation rl = ResourceLocation.tryParse(
            ri.itemName.contains(":") ? ri.itemName : "minecraft:" + ri.itemName);
        return rl != null ? BuiltInRegistries.ITEM.get(rl) : Items.AIR;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        this.reload.active = canReload(); // the reference re-polls canReload on a timer; the synced ammo drives it here
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        // gui.png is a 512×512 sheet whose art is authored at 2× density: the reference draws it with 1.7.10's
        // drawTexturedModalRect, which hard-normalizes UVs by 256, so a (0,0,210,236) blit actually samples the
        // 420×472-texel panel. Reproduce that by passing 256 as the texture size to the 8-arg blit — the (u,v,w,h)
        // are then u/256 of the bound 512px texture, i.e. exactly the reference's texel span.
        g.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight, 256, 256);

        if (this.vehicle == null) {
            return;
        }
        // Fuel column: a 12-px bar that grows upward from y+80, sourced from the sheet's gauge fill at (215,0).
        // getFuelP() is fuel/maxFuel (0 for a config with no tank) and is deliberately NOT creative-special-cased:
        // the reference shows the true level even when a creative occupant makes fuel free.
        float fuelP = this.vehicle.getFuelP();
        int fill = (int) (fuelP * 50.0F);
        if (fill > 0) {
            g.blit(TEXTURE, x + 57, y + 80 - fill, 215, 0, 12, fill, 256, 256);
        }
        int pct = (int) (fuelP * 100.0F + 0.5F);
        int color = pct > 20 ? GREEN : 0xFF0000;
        g.drawString(this.font, String.format("%3d", pct) + "%", x + 30, y + 65, color, false);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // Foreground coordinates are container-local (the matrix is already translated by leftPos/topPos).
        g.drawString(this.font, this.title, 10, 10, 0xFFFFFF, false);

        WeaponSlot w = this.vehicle != null ? this.vehicle.weaponAt(this.shownWeapon) : null;
        if (w == null) {
            g.drawString(this.font, Component.translatable("gui.mcheli.no_weapon"), 79, 45, 0xFFFFFF, false);
            return;
        }
        String name = w.info.displayName != null && !w.info.displayName.isEmpty() ? w.info.displayName : w.weaponName;
        g.drawString(this.font, name, 79, 30, 0xFFFFFF, false);

        if (w.hasEconomy()) {
            int rest = this.vehicle.ammoOf(this.shownWeapon);
            int max = w.maxAmmo();
            // The reference's three-way colouring: red at empty, green at full, white in between.
            int color = rest == 0 ? 0xFF0000 : (rest >= max ? GREEN : 0xFFFFFF);
            g.drawString(this.font, String.format("%4d/%4d", rest, max), 145, 70, color, false);
            drawRoundItems(g, w);
        }
    }

    /**
     * Draw the ammo items a reload consumes — the reference's {@code MCH_AircraftGui:160-172}: each item's icon at
     * {@code (85 + 20i, 62)} and its required count just below at {@code (90 + 20i, 80)}. The count is red when the
     * player cannot afford it, so it's obvious what to go and craft.
     */
    private void drawRoundItems(GuiGraphics g, WeaponSlot w) {
        int i = 0;
        for (MCH_WeaponInfo.RoundItem ri : w.info.roundItems) {
            Item item = itemOf(ri);
            if (item == Items.AIR) {
                continue;
            }
            int ix = 85 + 20 * i;
            g.renderItem(new ItemStack(item), ix, 62);
            boolean enough = countHeld(ri) >= ri.num;
            g.drawString(this.font, Integer.toString(ri.num), ix + 5, 80, enough ? 0xFFFFFF : 0xFF0000, false);
            i++;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
