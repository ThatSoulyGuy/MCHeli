package mcheli.dependent.item;

import java.util.List;
import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.dependent.config.MchConfig;
import mcheli.dependent.entity.AbstractMchVehicle;
import mcheli.dependent.registry.MchRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The single spawn-item class for the whole fleet. Each of the 112 registered instances carries its {@link #category}
 * and its {@link #configName} (e.g. {@code ah-64}); using it spawns that category's entity and assigns the config
 * name, so ONE item class + ONE entity-per-category produce every distinct vehicle — the faithful 1.7.10 shape
 * ({@code MCH_ItemHeli.createAircraft} → {@code setTypeName}). The item's display name is the config
 * {@code DisplayName}, and its inventory icon is the vehicle's 3D model (see {@code VehicleItemRenderer}).
 *
 * <p>Placement is the port of {@code MCH_ItemAircraft.onItemRightClick} / {@code spawnAircraft} / {@code onTileClick}
 * (:41-139): a 5-block look ray that stops on a liquid source (so seaplanes and boats go on the water), the vehicle
 * centred on top of the struck block, refused when it would overlap a block or a solid entity, or the player's eye is
 * inside an entity. It consumes the item outside creative and restores the damage and fuel a recovered item carries.
 * Like vanilla boats (whose placement the reference copied) it works through {@link #use}, for a block or a water
 * surface alike. The vehicle is snapped to a cardinal yaw a quarter turn from the player's look, so it appears
 * side-on to them ({@link #placementYaw}, reference {@code onTileClick} → {@code initRotationYaw}). A dispenser places
 * vehicles too ({@link #DISPENSE}).
 */
public class VehicleSpawnItem extends Item {

    /** Reference look-ray length ({@code d3 = 5.0}). */
    private static final double PLACE_REACH = 5.0;

    public final MchRegistries.Category category;
    public final String configName;

    public VehicleSpawnItem(MchRegistries.Category category, String configName, Properties properties) {
        super(properties);
        this.category = category;
        this.configName = configName;
        // A plain map put; the items are constructed on the registration thread, after vanilla's own bootstrap.
        DispenserBlock.registerBehavior(this, DISPENSE);
    }

    /** The parsed config for this vehicle (null before configs load / for an unknown name). */
    public MCH_AircraftInfo info() { return MchRegistries.infoFor(this.category, this.configName); }

    /** True if this is a UAV/drone — hidden from the tabs + not spawnable by hand until the UAV station is ported. */
    public boolean isUAV() { MCH_AircraftInfo i = info(); return i != null && i.isUAV; }

    @Override
    public Component getName(ItemStack stack) {
        MCH_AircraftInfo i = info();
        String name = i != null && i.displayName != null && !i.displayName.isEmpty() ? i.displayName : this.configName;
        return Component.literal(name);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        MCH_AircraftInfo info = info();
        if (info == null) {
            return InteractionResultHolder.pass(stack);
        }
        // Reference W_WorldFunc.clip(world, eye, eye + look * 5, true): stopOnLiquid, which in 1.7.10 hit only source
        // blocks (BlockLiquid.canCollideCheck is meta == 0).
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(PLACE_REACH));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE,
            ClipContext.Fluid.SOURCE_ONLY, player));
        if (hit.getType() != HitResult.Type.BLOCK || eyeInsideAnEntity(level, player)) {
            return InteractionResultHolder.pass(stack);
        }
        BlockPos pos = hit.getBlockPos();
        if (MchConfig.SERVER.placeableOnSpongeOnly() && !isSponge(level.getBlockState(pos))) {
            return InteractionResultHolder.pass(stack); // reference :100-105
        }
        if (!canPlaceOn(level, pos, info)) {
            return InteractionResultHolder.fail(stack);
        }
        if (isUAV()) {
            // Reference spawnAircraft:116-126: a UAV is launched from a UAV station (not ported), never by hand.
            if (level.isClientSide) {
                player.displayClientMessage(info.isSmallUAV
                    ? Component.translatableWithFallback("mcheli.message.uav_station_or_controller",
                        "Please use the UAV station OR Portable Controller")
                    : Component.translatableWithFallback("mcheli.message.uav_station", "Please use the UAV station"),
                    false);
            }
            return InteractionResultHolder.fail(stack);
        }
        if (!level.isClientSide) {
            if (spawn(level, pos, placementYaw(player.getYRot()), stack, info) == null) {
                return InteractionResultHolder.fail(stack);
            }
            level.gameEvent(player, GameEvent.ENTITY_PLACE, hit.getLocation());
            // Reference :133-135: consumed unless creative. Server-side only, as vanilla boats do; the client copy
            // follows by inventory sync instead of predicting a placement the server might refuse.
            stack.consume(1, player);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** Reference {@code onTileClick}:47 {@code initRotationYaw(((floor(rotationYaw * 4 / 360 + 0.5) & 3) - 1) * 90)}:
     *  the player's yaw snapped to the nearest cardinal direction, then turned 90 degrees, so the vehicle stands
     *  side-on to the player. {@code transformLocal}'s rotateAroundY(-yaw) is the reference getTransformedPosition, so
     *  the same yaw gives the same world orientation. */
    private static float placementYaw(float playerYaw) {
        return ((Mth.floor(playerYaw * 4.0F / 360.0F + 0.5) & 3) - 1) * 90.0F;
    }

    /** Reference {@code onTileClick}: the vehicle goes centred on top of {@code pos}, and is refused if it would overlap
     *  anything solid there ({@code getCollidingBoundingBoxes} over its box shrunk by 0.1, which counts blocks and
     *  collidable entities). */
    private static boolean canPlaceOn(Level level, BlockPos pos, MCH_AircraftInfo info) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 1.0;
        double z = pos.getZ() + 0.5;
        float w = info.bodyWidth > 0.0F ? info.bodyWidth : 2.0F;
        float h = info.bodyHeight > 0.0F ? info.bodyHeight : 0.7F;
        return level.noCollision(new AABB(x - w / 2.0, y, z - w / 2.0, x + w / 2.0, y + h, z + w / 2.0).deflate(0.1));
    }

    /** SERVER: create, configure and add the vehicle on top of {@code pos} (reference {@code createAircraft} +
     *  {@code spawnAircraft}), restoring the data a recovered item carries. Returns null if the entity could not be
     *  created. The caller has run {@link #canPlaceOn} and the UAV refusal. */
    private AbstractMchVehicle spawn(Level level, BlockPos pos, float yaw, ItemStack stack, MCH_AircraftInfo info) {
        EntityType<? extends AbstractMchVehicle> type = MchRegistries.entityTypeFor(this.category);
        AbstractMchVehicle v = type.create(level);
        if (v == null) {
            return null;
        }
        // Assign the config BEFORE addFreshEntity so the spawn packet carries the name (client resolves the right
        // model/info + hitboxes) and the first server tick builds weapons/hitboxes against it.
        v.setConfigName(this.configName);
        v.setTextureName(info.getTextureName()); // reference createAircraft: each placement takes the next paint
        v.setPos(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
        v.setYRot(yaw);
        v.yRotO = yaw; // reference initRotationYaw sets prevRotationYaw too: no first-frame swing from 0
        v.readItemData(stack); // reference getAcDataFromItem: damage + fuel of a recovered vehicle
        level.addFreshEntity(v);
        return v;
    }

    /**
     * Reference {@code MCH_ItemAircraftDispenseBehavior} (registered for every vehicle item, MCH_MOD:394-451): a
     * dispenser places the vehicle on top of the block two steps out in front of it, under the same overlap and UAV
     * rules, and always uses up one item. Otherwise it does nothing (no item is spat out). Deliberate divergence: the
     * vehicle faces the dispense direction; the reference used one fixed yaw whatever the dispenser faced.
     */
    private static final DispenseItemBehavior DISPENSE = new DefaultDispenseItemBehavior() {
        @Override
        protected ItemStack execute(BlockSource src, ItemStack stack) {
            if (stack.getItem() instanceof VehicleSpawnItem item) {
                MCH_AircraftInfo info = item.info();
                Direction facing = src.state().getValue(DispenserBlock.FACING);
                BlockPos pos = src.pos().relative(facing, 2);
                if (info != null && !item.isUAV() && canPlaceOn(src.level(), pos, info)
                        && item.spawn(src.level(), pos, facing.toYRot(), stack, info) != null) {
                    stack.shrink(1);
                }
            }
            return stack;
        }
    };

    /** Reference {@code onItemRightClick}:84-97: no placement while the player's eye is inside a pickable entity's box
     *  (1.7.10 {@code canBeCollidedWith} / {@code getCollisionBorderSize} are 1.21 {@code isPickable} /
     *  {@code getPickRadius}). */
    private static boolean eyeInsideAnEntity(Level level, Player player) {
        Vec3 look = player.getViewVector(1.0F);
        List<Entity> near = level.getEntities(player,
            player.getBoundingBox().expandTowards(look.scale(PLACE_REACH)).inflate(1.0), Entity::isPickable);
        Vec3 eye = player.getEyePosition();
        for (Entity e : near) {
            if (e.getBoundingBox().inflate(e.getPickRadius()).contains(eye)) {
                return true;
            }
        }
        return false;
    }

    /** Reference {@code instanceof BlockSponge}; a wet sponge is the same block type in every version since 1.8. */
    private static boolean isSponge(BlockState s) {
        return s.is(Blocks.SPONGE) || s.is(Blocks.WET_SPONGE);
    }
}
