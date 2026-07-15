package mcheli.dependent.registry;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import mcheli.MCHeli;
import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.helicopter.MCH_HeliInfoManager;
import mcheli.agnostic.plane.MCP_PlaneInfoManager;
import mcheli.agnostic.tank.MCH_TankInfoManager;
import mcheli.agnostic.vehicle.MCH_VehicleInfoManager;
import mcheli.dependent.entity.AbstractMchVehicle;
import mcheli.dependent.entity.MchBullet;
import mcheli.dependent.entity.MchCartridge;
import mcheli.dependent.entity.MchGroundVehicle;
import mcheli.dependent.entity.MchHelicopter;
import mcheli.dependent.entity.MchPlane;
import mcheli.dependent.entity.MchTank;
import mcheli.dependent.item.VehicleSpawnItem;
import mcheli.dependent.particle.MuzzleFxOptions;
import mcheli.dependent.port.NeoResourceSource;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

/**
 * Central registration for the FULL vehicle fleet. There are exactly FOUR vehicle {@link EntityType}s — one per
 * category (helicopter/plane/tank/vehicle) — each serving every config in that category; the specific vehicle is
 * carried by the entity's synced config name. Every bundled config becomes its OWN spawn {@link VehicleSpawnItem}
 * (scanned from the resources at mod construction), listed under its category's creative tab with its own name +
 * 3D-model icon. This mirrors 1.7.10 MCHeli (4 entity classes + one item per vehicle). Wired from {@link MCHeli}.
 */
public final class MchRegistries {
    private MchRegistries() {}
    private static final Logger LOG = LogUtils.getLogger();

    /** A vehicle category: its resource directory + the entity type that serves it. */
    public enum Category {
        HELICOPTER("helicopters"), PLANE("planes"), TANK("tanks"), VEHICLE("vehicles");
        public final String dir;
        Category(String dir) { this.dir = dir; }
    }

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
        DeferredRegister.create(Registries.ENTITY_TYPE, MCHeli.MODID);
    public static final DeferredRegister.Items ITEMS =
        DeferredRegister.createItems(MCHeli.MODID);
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
        DeferredRegister.create(Registries.PARTICLE_TYPE, MCHeli.MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MCHeli.MODID);
    public static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS =
        DeferredRegister.create(Registries.MENU, MCHeli.MODID);
    public static final DeferredRegister<net.minecraft.world.item.crafting.RecipeSerializer<?>> RECIPE_SERIALIZERS =
        DeferredRegister.create(Registries.RECIPE_SERIALIZER, MCHeli.MODID);

    // ---- one EntityType per category (faithful uniform 2.0x0.7 collision core; real hits use the per-part armor
    //      boxes, and getBoundingBoxForCulling() inflates so the big models don't cull). ----
    public static final Supplier<EntityType<MchHelicopter>> HELI =
        ENTITY_TYPES.register("helicopter", () -> EntityType.Builder.<MchHelicopter>of(MchHelicopter::new, MobCategory.MISC)
            .sized(2.0F, 0.7F).clientTrackingRange(10).build("helicopter"));
    public static final Supplier<EntityType<MchPlane>> PLANE =
        ENTITY_TYPES.register("plane", () -> EntityType.Builder.<MchPlane>of(MchPlane::new, MobCategory.MISC)
            .sized(2.0F, 0.7F).clientTrackingRange(10).build("plane"));
    public static final Supplier<EntityType<MchTank>> TANK =
        ENTITY_TYPES.register("tank", () -> EntityType.Builder.<MchTank>of(MchTank::new, MobCategory.MISC)
            .sized(2.0F, 0.7F).clientTrackingRange(10).build("tank"));
    public static final Supplier<EntityType<MchGroundVehicle>> VEHICLE =
        ENTITY_TYPES.register("vehicle", () -> EntityType.Builder.<MchGroundVehicle>of(MchGroundVehicle::new, MobCategory.MISC)
            .sized(2.0F, 0.7F).clientTrackingRange(10).build("vehicle"));

    /** The entity type that serves a category. */
    public static EntityType<? extends AbstractMchVehicle> entityTypeFor(Category cat) {
        return switch (cat) {
            case HELICOPTER -> HELI.get();
            case PLANE -> PLANE.get();
            case TANK -> TANK.get();
            case VEHICLE -> VEHICLE.get();
        };
    }

    /** The parsed config for a vehicle (from the category's manager); null before configs load or for an unknown name. */
    public static MCH_AircraftInfo infoFor(Category cat, String name) {
        return switch (cat) {
            case HELICOPTER -> MCH_HeliInfoManager.get(name);
            case PLANE -> MCP_PlaneInfoManager.get(name);
            case TANK -> MCH_TankInfoManager.get(name);
            case VEHICLE -> MCH_VehicleInfoManager.get(name);
        };
    }

    // ---- the 112 per-vehicle spawn items, scanned from the bundled configs at construction ----
    private static final Map<Category, List<DeferredItem<VehicleSpawnItem>>> ITEMS_BY_CATEGORY = new EnumMap<>(Category.class);
    private static final Map<String, DeferredItem<VehicleSpawnItem>> ITEM_BY_NAME = new HashMap<>();

    /** Register one spawn item per bundled config, keyed by the config base-name (e.g. {@code ah-64}). Call from the
     *  MCHeli constructor BEFORE {@link #register}, so the DeferredRegister entries exist when the RegisterEvent fires. */
    public static void registerVehicles() {
        NeoResourceSource res = new NeoResourceSource();
        for (Category cat : Category.values()) {
            List<String> names = new ArrayList<>();
            for (String file : res.list(cat.dir)) {
                if (file.endsWith(".txt")) {
                    names.add(file.substring(0, file.length() - 4));
                }
            }
            Collections.sort(names); // Files.list order is filesystem-dependent — sort for a stable menu
            List<DeferredItem<VehicleSpawnItem>> list = new ArrayList<>();
            for (String name : names) {
                DeferredItem<VehicleSpawnItem> item = ITEMS.registerItem(name,
                    props -> new VehicleSpawnItem(cat, name, props), new Item.Properties().stacksTo(1));
                list.add(item);
                ITEM_BY_NAME.put(name, item);
            }
            ITEMS_BY_CATEGORY.put(cat, list);
            if (names.isEmpty()) {
                LOG.error("MCHeli: scanned ZERO {} configs — the creative tab will be empty (resource scan failed?)", cat.dir);
            } else {
                LOG.info("MCHeli: registered {} {} spawn items", names.size(), cat.dir);
            }
        }
    }

    /** The registered spawn item for a config name (for creative salvage drops), or null if none. */
    public static Item spawnItemFor(String configName) {
        DeferredItem<VehicleSpawnItem> d = ITEM_BY_NAME.get(configName);
        return d != null ? d.get() : null;
    }

    private static List<DeferredItem<VehicleSpawnItem>> itemsOf(Category cat) {
        return ITEMS_BY_CATEGORY.getOrDefault(cat, Collections.emptyList());
    }

    /** All registered spawn items (for the client 3D-icon extension registration). */
    public static List<VehicleSpawnItem> allSpawnItems() {
        List<VehicleSpawnItem> out = new ArrayList<>();
        for (DeferredItem<VehicleSpawnItem> d : ITEM_BY_NAME.values()) {
            out.add(d.get());
        }
        return out;
    }

    // Projectile + cartridge + particle (NOT per-vehicle; unchanged).
    public static final Supplier<EntityType<MchBullet>> DEMO_BULLET =
        ENTITY_TYPES.register("demo_bullet", () ->
            EntityType.Builder.<MchBullet>of(MchBullet::new, MobCategory.MISC)
                .sized(0.2F, 0.2F).clientTrackingRange(6).updateInterval(1).build("demo_bullet"));

    public static final Supplier<EntityType<MchCartridge>> CARTRIDGE =
        ENTITY_TYPES.register("cartridge", () ->
            EntityType.Builder.<MchCartridge>of(MchCartridge::new, MobCategory.MISC)
                .sized(0.15F, 0.15F).clientTrackingRange(4).updateInterval(3).noSave().noSummon().build("cartridge"));

    /** The cargo container — a placeable 54-slot storage entity (reference {@code MCH_EntityContainer}). */
    public static final Supplier<EntityType<mcheli.dependent.entity.MchContainer>> CONTAINER =
        ENTITY_TYPES.register("container", () ->
            EntityType.Builder.<mcheli.dependent.entity.MchContainer>of(
                    mcheli.dependent.entity.MchContainer::new, MobCategory.MISC)
                .sized(2.0F, 1.0F).clientTrackingRange(8).build("container"));

    public static final Supplier<ParticleType<MuzzleFxOptions>> WEAPON_FX =
        PARTICLES.register("weapon_fx", () -> new ParticleType<MuzzleFxOptions>(false) {
            @Override public MapCodec<MuzzleFxOptions> codec() { return MuzzleFxOptions.CODEC; }
            @Override public StreamCodec<? super RegistryFriendlyByteBuf, MuzzleFxOptions> streamCodec() {
                return MuzzleFxOptions.STREAM_CODEC;
            }
        });

    /** The big config-scaled explosion fireball (port of {@code MCH_EntityParticleExplode}). */
    public static final Supplier<ParticleType<mcheli.dependent.particle.MchExplodeOptions>> EXPLODE_FX =
        PARTICLES.register("explode_fx", () -> new ParticleType<mcheli.dependent.particle.MchExplodeOptions>(false) {
            @Override public MapCodec<mcheli.dependent.particle.MchExplodeOptions> codec() {
                return mcheli.dependent.particle.MchExplodeOptions.CODEC;
            }
            @Override public StreamCodec<? super RegistryFriendlyByteBuf, mcheli.dependent.particle.MchExplodeOptions> streamCodec() {
                return mcheli.dependent.particle.MchExplodeOptions.STREAM_CODEC;
            }
        });

    /** The soft grey→white smoke billboard (port of {@code MCH_EntityParticleSmoke}) — rotor down-wash + damage smoke. */
    public static final Supplier<ParticleType<mcheli.dependent.particle.MchSmokeOptions>> SMOKE_FX =
        PARTICLES.register("smoke_fx", () -> new ParticleType<mcheli.dependent.particle.MchSmokeOptions>(false) {
            @Override public MapCodec<mcheli.dependent.particle.MchSmokeOptions> codec() {
                return mcheli.dependent.particle.MchSmokeOptions.CODEC;
            }
            @Override public StreamCodec<? super RegistryFriendlyByteBuf, mcheli.dependent.particle.MchSmokeOptions> streamCodec() {
                return mcheli.dependent.particle.MchSmokeOptions.STREAM_CODEC;
            }
        });

    // Flat "original" 2D icons for the TAB BUTTONS only — a plain item per category with an item/generated model
    // pointing at a representative vehicle's original sprite (textures/items/<name>.png). The vehicle SPAWN items keep
    // their 3D-model icons; these tab icons render flat. Never added to a tab's displayItems, so they never appear as
    // spawnable items (an item in no tab is also absent from the creative search).
    /** The fuel canister — put it in a vehicle's fuel slot (the riding GUI) and the vehicle siphons it into its tank. */
    public static final DeferredItem<mcheli.dependent.item.MchFuelItem> FUEL =
        ITEMS.registerItem("fuel", mcheli.dependent.item.MchFuelItem::new, new Item.Properties());

    /** The maintenance wrench — hold right-click on a damaged vehicle to mend it (reference {@code MCH_ItemWrench}). */
    public static final DeferredItem<mcheli.dependent.item.MchWrench> WRENCH =
        ITEMS.registerItem("wrench", mcheli.dependent.item.MchWrench::new, new Item.Properties().durability(250));

    /** Places a {@link mcheli.dependent.entity.MchContainer} cargo box (reference {@code MCH_ItemContainer}). */
    public static final DeferredItem<mcheli.dependent.item.MchContainerItem> CONTAINER_ITEM =
        ITEMS.registerItem("container", mcheli.dependent.item.MchContainerItem::new, new Item.Properties());

    /** Refill a used fuel can by crafting it with coal (reference {@code MCH_RecipeFuel}). */
    public static final Supplier<net.minecraft.world.item.crafting.RecipeSerializer<mcheli.dependent.item.MchFuelRefillRecipe>> FUEL_REFILL =
        RECIPE_SERIALIZERS.register("fuel_refill", () ->
            new net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer<>(mcheli.dependent.item.MchFuelRefillRecipe::new));

    /** The riding GUI's menu (fuel slots + reload). */
    public static final Supplier<net.minecraft.world.inventory.MenuType<mcheli.dependent.menu.MchVehicleMenu>> VEHICLE_MENU =
        MENUS.register("vehicle", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(
            mcheli.dependent.menu.MchVehicleMenu::new));

    public static final DeferredItem<Item> ICON_HELI = ITEMS.registerItem("tab_helicopter", Item::new, new Item.Properties());
    public static final DeferredItem<Item> ICON_PLANE = ITEMS.registerItem("tab_plane", Item::new, new Item.Properties());
    public static final DeferredItem<Item> ICON_TANK = ITEMS.registerItem("tab_tank", Item::new, new Item.Properties());
    public static final DeferredItem<Item> ICON_VEHICLE = ITEMS.registerItem("tab_vehicle", Item::new, new Item.Properties());

    // ---- one creative tab per category, enumerating every (non-UAV) vehicle in it ----
    public static final Supplier<CreativeModeTab> TAB_HELICOPTERS = tab(Category.HELICOPTER, "helicopters", ICON_HELI);
    public static final Supplier<CreativeModeTab> TAB_PLANES = tab(Category.PLANE, "planes", ICON_PLANE);
    public static final Supplier<CreativeModeTab> TAB_TANKS = tab(Category.TANK, "tanks", ICON_TANK);
    public static final Supplier<CreativeModeTab> TAB_VEHICLES = tab(Category.VEHICLE, "vehicles", ICON_VEHICLE);

    private static Supplier<CreativeModeTab> tab(Category cat, String key, Supplier<? extends Item> icon) {
        return CREATIVE_TABS.register(key, () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.mcheli." + key))
            .icon(() -> new ItemStack(icon.get()))   // flat original tab-button icon
            .displayItems((params, output) -> {
                // Runs lazily at GUI open (after configs load) so isUAV() is known: UAV vehicles are hidden until the
                // UAV station is ported. The listed spawn items keep their 3D-model icons (VehicleItemRenderer).
                for (DeferredItem<VehicleSpawnItem> d : itemsOf(cat)) {
                    VehicleSpawnItem it = d.get();
                    if (!it.isUAV()) {
                        output.accept(it);
                    }
                }
                output.accept(new ItemStack(FUEL.get()));        // every category burns fuel, so every tab offers the can
                output.accept(new ItemStack(WRENCH.get()));      // ...and every vehicle can be mended with the wrench
                output.accept(new ItemStack(CONTAINER_ITEM.get())); // ...and the cargo box rides with any of them
            })
            .build());
    }

    public static void register(IEventBus modBus) {
        ENTITY_TYPES.register(modBus);
        ITEMS.register(modBus);
        CREATIVE_TABS.register(modBus);
        PARTICLES.register(modBus);
        MENUS.register(modBus);
        RECIPE_SERIALIZERS.register(modBus);
    }
}
