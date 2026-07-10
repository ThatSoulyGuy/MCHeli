package mcheli.dependent.item;

import mcheli.dependent.entity.MchDemoPlane;
import mcheli.dependent.registry.MchRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Right-click a block to spawn a {@link MchDemoPlane} above it, facing the player — the human-facing spawn path
 * for the plane demo (the headless self-test spawns directly instead).
 */
public class PlaneSpawnItem extends Item {

    public PlaneSpawnItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!level.isClientSide) {
            BlockPos pos = context.getClickedPos().above();
            MchDemoPlane plane = MchRegistries.DEMO_PLANE.get().create(level);
            if (plane != null) {
                plane.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                if (context.getPlayer() != null) {
                    plane.setYRot(context.getPlayer().getYRot());
                }
                level.addFreshEntity(plane);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
