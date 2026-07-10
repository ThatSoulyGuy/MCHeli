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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
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
                .sized(2.0F, 1.0F)
                .clientTrackingRange(10)
                .build("demo_heli"));

    public static final DeferredItem<HeliSpawnItem> DEMO_HELI_ITEM =
        ITEMS.registerItem("demo_heli_spawner", HeliSpawnItem::new, new Item.Properties().stacksTo(1));

    public static final Supplier<EntityType<MchDemoPlane>> DEMO_PLANE =
        ENTITY_TYPES.register("demo_plane", () ->
            EntityType.Builder.<MchDemoPlane>of(MchDemoPlane::new, MobCategory.MISC)
                .sized(2.0F, 1.0F)
                .clientTrackingRange(10)
                .build("demo_plane"));

    public static final DeferredItem<PlaneSpawnItem> DEMO_PLANE_ITEM =
        ITEMS.registerItem("demo_plane_spawner", PlaneSpawnItem::new, new Item.Properties().stacksTo(1));

    public static final Supplier<EntityType<MchDemoTank>> DEMO_TANK =
        ENTITY_TYPES.register("demo_tank", () ->
            EntityType.Builder.<MchDemoTank>of(MchDemoTank::new, MobCategory.MISC)
                .sized(1.75F, 1.0F)
                .clientTrackingRange(10)
                .build("demo_tank"));

    public static final DeferredItem<TankSpawnItem> DEMO_TANK_ITEM =
        ITEMS.registerItem("demo_tank_spawner", TankSpawnItem::new, new Item.Properties().stacksTo(1));

    public static void register(IEventBus modBus) {
        ENTITY_TYPES.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(MchRegistries::addToCreativeTabs);
    }

    private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(DEMO_VEHICLE_ITEM.get());
            event.accept(DEMO_HELI_ITEM.get());
            event.accept(DEMO_PLANE_ITEM.get());
            event.accept(DEMO_TANK_ITEM.get());
        }
    }
}
