package mcheli.dependent.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * The fuel canister — the 1.21.1 port of {@code MCH_ItemFuel}. It is a DURABILITY bar: a full can holds
 * {@link #CAPACITY} units and its damage value is how much has already been drained. Drop it into a vehicle's fuel
 * slot ({@link mcheli.dependent.menu.MchVehicleMenu}) and the vehicle siphons it into its tank while it is grounded.
 *
 * <p>A used can refills two ways, exactly as in the reference: right-click with coal in your inventory
 * ({@link #use}, reference {@code MCH_ItemFuel.onItemRightClick}), or craft the can together with coal
 * ({@link MchFuelRefillRecipe}, reference {@code MCH_RecipeFuel}). Every coal restores {@link #COAL_REFILL} units and
 * every charcoal {@link #CHARCOAL_REFILL} — the reference's numbers. A can is crafted FULL ({@code "ICI"/"III"}).
 */
public class MchFuelItem extends Item {

    /** Units of fuel a full can holds (reference {@code setMaxDamage(600)}). */
    public static final int CAPACITY = 600;
    /** A piece of coal restores this many units (reference {@code coalType == 0}). */
    public static final int COAL_REFILL = 100;
    /** A piece of charcoal restores this many (reference {@code coalType == 1}; the poorer fuel). */
    public static final int CHARCOAL_REFILL = 75;

    public MchFuelItem(Properties props) {
        super(props.durability(CAPACITY)); // durability() already forces a max stack of 1
    }

    /** Fuel left in this can (capacity − damage). */
    public static int fuelLeft(ItemStack stack) {
        return Math.max(0, CAPACITY - stack.getDamageValue());
    }

    /** Drain up to {@code want} units from the can; returns how much was actually taken. */
    public static int drain(ItemStack stack, int want) {
        int have = fuelLeft(stack);
        int take = Math.min(Math.max(0, want), have);
        if (take > 0) {
            stack.setDamageValue(stack.getDamageValue() + take);
        }
        return take;
    }

    /**
     * Right-click refuel — the port of {@code MCH_ItemFuel.onItemRightClick}: server-side, survival only, and only a
     * used can. Burns CHARCOAL first (75/unit) then COAL (100/unit) from the main inventory, one piece at a time, up
     * to the can's capacity — the reference's exact order and per-item amounts.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !stack.isDamaged() || player.getAbilities().instabuild) {
            return InteractionResultHolder.pass(stack);
        }
        boolean refuelled = refuel(stack, player, Items.CHARCOAL, CHARCOAL_REFILL);
        refuelled |= refuel(stack, player, Items.COAL, COAL_REFILL);
        return refuelled ? InteractionResultHolder.consume(stack) : InteractionResultHolder.pass(stack);
    }

    /** Burn one kind of coal from the player's MAIN inventory into this can until it is full or the coal runs out. */
    private static boolean refuel(ItemStack can, Player player, Item coal, int perItem) {
        boolean any = false;
        Inventory inv = player.getInventory();
        // The reference scans mainInventory only (indices 0..35) — armor and offhand never count.
        for (int i = 0; i < 36 && can.isDamaged(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.is(coal)) {
                continue;
            }
            while (s.getCount() > 0 && can.isDamaged()) {
                can.setDamageValue(Math.max(0, can.getDamageValue() - perItem));
                s.shrink(1);
                any = true;
            }
        }
        return any;
    }
}
