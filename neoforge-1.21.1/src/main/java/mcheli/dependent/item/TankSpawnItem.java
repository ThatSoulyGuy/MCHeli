package mcheli.dependent.item;

import mcheli.dependent.entity.MchDemoTank;
import mcheli.dependent.registry.MchRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Right-click a block to spawn a {@link MchDemoTank} above it, facing the player — the human-facing spawn path
 * for the tank demo (the headless self-test spawns directly instead).
 */
public class TankSpawnItem extends Item {

    public TankSpawnItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!level.isClientSide) {
            BlockPos pos = context.getClickedPos().above();
            MchDemoTank tank = MchRegistries.DEMO_TANK.get().create(level);
            if (tank != null) {
                tank.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                if (context.getPlayer() != null) {
                    tank.setYRot(context.getPlayer().getYRot());
                }
                level.addFreshEntity(tank);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
