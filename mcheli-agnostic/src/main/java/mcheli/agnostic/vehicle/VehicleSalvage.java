package mcheli.agnostic.vehicle;

/**
 * Who may break a vehicle back into its spawn item, and when that drops the item: the player-break rules of
 * {@code MCH_EntityAircraft.attackEntityFrom} (:861-877, :937-957) with the three server options
 * {@code PreventingBroken}, {@code BreakableOnlyPickaxe} and {@code DropItemInCreativeMode} (all false by default,
 * {@code MCH_Config}:299-301). Pure so the decision table is unit-tested.
 */
public final class VehicleSalvage {
    private VehicleSalvage() {}

    /**
     * Reference {@code isDamegeSourcePlayer}: true when this hit reclaims the vehicle instead of damaging it. Only a
     * player's melee hit ({@code "player"} damage type) qualifies. A creative player always breaks. Otherwise, unless
     * {@code preventingBroken}: with {@code breakableOnlyPickaxe} a pickaxe in the main hand breaks (whoever rides),
     * else the vehicle breaks only while no player rides it.
     *
     * @param playerMelee         the source is a player's melee attack
     * @param creative            that player is in creative mode
     * @param holdingPickaxe      that player holds a pickaxe in the main hand
     * @param riddenByPlayer      any seat (pilot or crew) holds a player (reference {@code isRidePlayer})
     */
    public static boolean isPlayerBreak(boolean playerMelee, boolean creative, boolean preventingBroken,
                                        boolean breakableOnlyPickaxe, boolean holdingPickaxe, boolean riddenByPlayer) {
        if (!playerMelee) {
            return false;
        }
        if (creative) {
            return true;
        }
        if (preventingBroken) {
            return false;
        }
        return breakableOnlyPickaxe ? holdingPickaxe : !riddenByPlayer;
    }

    /**
     * Whether a player break of an intact vehicle drops its spawn item (reference :939-949). A survival break always
     * drops it. A creative break drops it when sneaking, or, with {@code dropItemInCreativeMode}, when NOT sneaking.
     */
    public static boolean dropsItem(boolean creative, boolean sneaking, boolean dropItemInCreativeMode) {
        return !creative || dropItemInCreativeMode != sneaking;
    }

    /** Whether a player break clears an existing WRECK (reference :955-957): only a creative player can. */
    public static boolean clearsWreck(boolean playerBreak, boolean creative) {
        return playerBreak && creative;
    }
}
