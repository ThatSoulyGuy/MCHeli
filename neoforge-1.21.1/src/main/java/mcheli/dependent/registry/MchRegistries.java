package mcheli.dependent.registry;

import java.util.function.Supplier;
import mcheli.MCHeli;
import mcheli.dependent.entity.MchDemoHeli;
import mcheli.dependent.entity.MchDemoPlane;
import mcheli.dependent.entity.MchDemoTank;
import mcheli.dependent.entity.MchDemoVehicle;
import mcheli.dependent.item.HeliSpawnItem;
import mcheli.dependent.item.PlaneSpawnItem;
import mcheli.dependent.item.TankSpawnItem;
import mcheli.dependent.item.VehicleSpawnItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Central registration for the vertical slice: the demo vehicle {@link EntityType} and the spawn
 * {@link Item}. Wired onto the mod event bus from {@link MCHeli}.
 */
public final class MchRegistries {
    private MchRegistries() {}

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
        DeferredRegister.create(Registries.ENTITY_TYPE, MCHeli.MODID);
    public static final DeferredRegister.Items ITEMS =
        DeferredRegister.createItems(MCHeli.MODID);

    public static final Supplier<EntityType<MchDemoVehicle>> DEMO_VEHICLE =
        ENTITY_TYPES.register("demo_vehicle", () ->
            EntityType.Builder.<MchDemoVehicle>of(MchDemoVehicle::new, MobCategory.MISC)
                .sized(1.5F, 0.75F)
                .clientTrackingRange(10)
                .build("demo_vehicle"));

    public static final DeferredItem<VehicleSpawnItem> DEMO_VEHICLE_ITEM =
        ITEMS.registerItem("demo_vehicle_spawner", VehicleSpawnItem::new, new Item.Properties().stacksTo(1));

    public static final Supplier<EntityType<MchDemoHeli>> DEMO_HELI =
        ENTITY_TYPES.register("demo_heli", () ->
            EntityType.Builder.<MchDemoHeli>of(MchDemoHeli::new, MobCategory.MISC)
                // Body-sized footprint (ah-64 fuselage/mast). MC AABBs are square (width x height x width), so this
                // can't enclose the 15-wide rotor or 19-long tail; the reference used a tiny core AABB + separate
                // BoundingBox hit-volumes (not yet ported). Tune here.
                .sized(5.0F, 4.5F)
                .clientTrackingRange(10)
                .build("demo_heli"));

    public static final DeferredItem<HeliSpawnItem> DEMO_HELI_ITEM =
        ITEMS.registerItem("demo_heli_spawner", HeliSpawnItem::new, new Item.Properties().stacksTo(1));

    public static final Supplier<EntityType<MchDemoPlane>> DEMO_PLANE =
        ENTITY_TYPES.register("demo_plane", () ->
            EntityType.Builder.<MchDemoPlane>of(MchDemoPlane::new, MobCategory.MISC)
                .sized(6.0F, 4.0F) // a-10 fuselage + inner wings (square footprint; wings/tail extend beyond)
                .clientTrackingRange(10)
                .build("demo_plane"));

    public static final DeferredItem<PlaneSpawnItem> DEMO_PLANE_ITEM =
        ITEMS.registerItem("demo_plane_spawner", PlaneSpawnItem::new, new Item.Properties().stacksTo(1));

    public static final Supplier<EntityType<MchDemoTank>> DEMO_TANK =
        ENTITY_TYPES.register("demo_tank", () ->
            EntityType.Builder.<MchDemoTank>of(MchDemoTank::new, MobCategory.MISC)
                .sized(5.0F, 3.5F) // m1a2 hull (4.8 wide) + turret
                .clientTrackingRange(10)
                .build("demo_tank"));

    public static final DeferredItem<TankSpawnItem> DEMO_TANK_ITEM =
        ITEMS.registerItem("demo_tank_spawner", TankSpawnItem::new, new Item.Properties().stacksTo(1));

    // Dedicated MCHeli creative tabs — one per vehicle category, mirroring the loaded config buckets
    // (helicopters/planes/tanks/vehicles). Each tab's icon is its own spawn item; as real vehicles gain
    // items these tabs fill out. Replaces the temporary injection into vanilla Tools & Utilities.
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MCHeli.MODID);

    public static final Supplier<CreativeModeTab> TAB_HELICOPTERS =
        CREATIVE_TABS.register("helicopters", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.mcheli.helicopters"))
            .icon(() -> new ItemStack(DEMO_HELI_ITEM.get()))
            .displayItems((params, output) -> output.accept(DEMO_HELI_ITEM.get()))
            .build());

    public static final Supplier<CreativeModeTab> TAB_PLANES =
        CREATIVE_TABS.register("planes", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.mcheli.planes"))
            .icon(() -> new ItemStack(DEMO_PLANE_ITEM.get()))
            .displayItems((params, output) -> output.accept(DEMO_PLANE_ITEM.get()))
            .build());

    public static final Supplier<CreativeModeTab> TAB_TANKS =
        CREATIVE_TABS.register("tanks", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.mcheli.tanks"))
            .icon(() -> new ItemStack(DEMO_TANK_ITEM.get()))
            .displayItems((params, output) -> output.accept(DEMO_TANK_ITEM.get()))
            .build());

    public static final Supplier<CreativeModeTab> TAB_VEHICLES =
        CREATIVE_TABS.register("vehicles", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.mcheli.vehicles"))
            .icon(() -> new ItemStack(DEMO_VEHICLE_ITEM.get()))
            .displayItems((params, output) -> output.accept(DEMO_VEHICLE_ITEM.get()))
            .build());

    public static void register(IEventBus modBus) {
        ENTITY_TYPES.register(modBus);
        ITEMS.register(modBus);
        CREATIVE_TABS.register(modBus);
    }
}
