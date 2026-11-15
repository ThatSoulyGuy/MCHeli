package mcheli.dependent.item;

import mcheli.agnostic.parachute.ParachuteFlight;
import mcheli.dependent.entity.MchParachute;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The hand-held parachute, the port of {@code parachute/MCH_ItemParachute} (stack size 1, :11). Used in the air, it
 * opens a type-1 {@link MchParachute} 3.5 blocks above the player (and half a block off along +X and +Z, as the
 * reference placed it), which takes over his yaw, motion and fall distance and lifts him under its canopy on its first
 * tick; his own fall distance is cleared.
 *
 * <p>Kept from the reference (a gameplay choice, quirk 1): the item is used up on every use outside creative, also on
 * the ground or while riding, where nothing opens (:31-33, on both sides).
 */
public class MchParachuteItem extends Item {

    public MchParachuteItem(Properties props) {
        super(props);
    }

    /** Reference {@code onItemRightClick} (:14-36). */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel server && !player.isPassenger() && !player.onGround()) {
            MchParachute.deploy(server, player.getX() + 0.5, player.getY() + 3.5, player.getZ() + 0.5,
                ParachuteFlight.TYPE_HAND, player, player.getYRot(), player.getDeltaMovement(), player.fallDistance);
            player.fallDistance = 0.0F;
        }
        stack.consume(1, player); // skips a player with infinite materials (creative), as the reference did
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
