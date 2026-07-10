package mcheli.dependent.item;

import mcheli.dependent.entity.MchDemoVehicle;
import mcheli.dependent.registry.MchRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Right-click a block with this item to spawn a {@link MchDemoVehicle} on top of it, facing the player.
 * The human-facing spawn path for the vertical slice (the headless self-test spawns directly instead).
 */
public class VehicleSpawnItem extends Item {

    public VehicleSpawnItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!level.isClientSide) {
            BlockPos pos = context.getClickedPos().above();
            MchDemoVehicle vehicle = MchRegistries.DEMO_VEHICLE.get().create(level);
            if (vehicle != null) {
                vehicle.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                if (context.getPlayer() != null) {
                    vehicle.setYRot(context.getPlayer().getYRot());
                }
                level.addFreshEntity(vehicle);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
