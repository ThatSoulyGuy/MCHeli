package mcheli.dependent.item;

import mcheli.dependent.entity.MchDemoHeli;
import mcheli.dependent.registry.MchRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Right-click a block with this item to spawn a {@link MchDemoHeli} above it, facing the player — the human-facing
 * spawn path for the flying demo (the headless self-test spawns directly instead).
 */
public class HeliSpawnItem extends Item {

    public HeliSpawnItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!level.isClientSide) {
            BlockPos pos = context.getClickedPos().above();
            MchDemoHeli heli = MchRegistries.DEMO_HELI.get().create(level);
            if (heli != null) {
                heli.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                if (context.getPlayer() != null) {
                    heli.setYRot(context.getPlayer().getYRot());
                }
                level.addFreshEntity(heli);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
