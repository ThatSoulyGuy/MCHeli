package mcheli.dependent.item;

import mcheli.dependent.entity.MchContainer;
import mcheli.dependent.registry.MchRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Places a {@link MchContainer} cargo box — the 1.21.1 port of {@code MCH_ItemContainer}. Right-click a block face and
 * a container entity spawns on that side, consuming one item (unless the placer is in creative).
 */
public class MchContainerItem extends Item {

    public MchContainerItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (!level.isClientSide) {
            BlockPos pos = ctx.getClickedPos().relative(ctx.getClickedFace());
            MchContainer container = MchRegistries.CONTAINER.get().create(level);
            if (container == null) {
                return InteractionResult.FAIL;
            }
            container.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            if (ctx.getPlayer() != null) {
                container.setYRot(ctx.getPlayer().getYRot());
            }
            level.addFreshEntity(container);
            level.playSound(null, container.getX(), container.getY(), container.getZ(),
                SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
            if (ctx.getPlayer() == null || !ctx.getPlayer().getAbilities().instabuild) {
                ctx.getItemInHand().shrink(1);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
