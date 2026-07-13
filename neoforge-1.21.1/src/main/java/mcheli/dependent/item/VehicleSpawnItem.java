package mcheli.dependent.item;

import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.dependent.entity.AbstractMchVehicle;
import mcheli.dependent.registry.MchRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * The single spawn-item class for the whole fleet. Each of the 112 registered instances carries its {@link #category}
 * and its {@link #configName} (e.g. {@code ah-64}); right-clicking a block spawns that category's entity above it and
 * assigns the config name, so ONE item class + ONE entity-per-category produce every distinct vehicle — the faithful
 * 1.7.10 shape ({@code MCH_ItemHeli.createAircraft} → {@code setTypeName}). The item's display name is the config
 * {@code DisplayName}, and its inventory icon is the vehicle's 3D model (see {@code VehicleItemRenderer}).
 */
public class VehicleSpawnItem extends Item {

    public final MchRegistries.Category category;
    public final String configName;

    public VehicleSpawnItem(MchRegistries.Category category, String configName, Properties properties) {
        super(properties);
        this.category = category;
        this.configName = configName;
    }

    /** The parsed config for this vehicle (null before configs load / for an unknown name). */
    public MCH_AircraftInfo info() { return MchRegistries.infoFor(this.category, this.configName); }

    /** True if this is a UAV/drone — hidden from the tabs + not right-click spawnable until the UAV station is ported. */
    public boolean isUAV() { MCH_AircraftInfo i = info(); return i != null && i.isUAV; }

    @Override
    public Component getName(ItemStack stack) {
        MCH_AircraftInfo i = info();
        String name = i != null && i.displayName != null && !i.displayName.isEmpty() ? i.displayName : this.configName;
        return Component.literal(name);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (isUAV()) {
            return InteractionResult.FAIL; // needs the UAV station (not ported); item is hidden from tabs anyway
        }
        Level level = context.getLevel();
        if (!level.isClientSide) {
            BlockPos pos = context.getClickedPos().above();
            EntityType<? extends AbstractMchVehicle> type = MchRegistries.entityTypeFor(this.category);
            AbstractMchVehicle v = type.create(level);
            if (v != null) {
                // Assign the config BEFORE addFreshEntity so the spawn packet carries the name (client resolves the
                // right model/info + hitboxes) and the first server tick builds weapons/hitboxes against it.
                v.setConfigName(this.configName);
                v.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                if (context.getPlayer() != null) {
                    v.setYRot(context.getPlayer().getYRot());
                }
                level.addFreshEntity(v);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
