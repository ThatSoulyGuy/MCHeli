# MCHeli 1.21.1 port: architecture

This is a contributor guide to the port as it is now. The decompiled 1.7.10 mod in `mcheli-1.7.10/` is the ground
truth for behaviour, and this file says where each part of it lives in the port. Features that are not ported yet are
listed in [DEFERRED.md](DEFERRED.md), together with the gameplay changes the port has made.

Code conventions that run through both modules:

- Behaviour ported from the reference cites it in a comment, for example `reference MCH_EntityHeli:349-351` (line
  numbers in the `mcheli-1.7.10` tree).
- Faithful to the reference is the default. A deliberate divergence carries a short comment saying why.
- Agnostic logic that changes gets a JUnit test. The NeoForge module has no unit tests: compilation and the in-game
  self-tests are its gates.

## Modules and layering

| Module | Contents | Toolchain |
|---|---|---|
| `mcheli-agnostic` | All platform-neutral logic: definition parsing, flight models, weapon and projectile math, the HUD DSL, model parsing, and the SPI ports. JUnit 5 tests in `src/test/java`. | Java 21, plain `java-library` |
| `neoforge-1.21.1` | The mod itself: entities, items, input, rendering, networking, registration, config. It implements the SPI and embeds the agnostic jar (jarJar). | NeoForge 21.1.235, ModDevGradle 2.0.141, Java 21 (mod version 1.1.0) |
| `mcheli-1.7.10` | The decompiled, buildable MCHeli 1.0.3 for Minecraft 1.7.10, used as the reference. It is commented out in `settings.gradle`; see [its README](mcheli-1.7.10/README.md). | RetroFuturaGradle 1.4.9, Java 8, Forge 10.13.4.1614 |
| `reference/decompiled-srg` | A raw CFR decompile of the same jar (474 files, SRG names). It is not a Gradle project. Use it as a second opinion where the Vineflower output looks wrong. | none |

The layering is enforced by the compiler. `mcheli-agnostic` has no Minecraft, NeoForge, LWJGL or Mojang classes on
its classpath, so a platform import there fails to compile. `neoforge-1.21.1` depends on it in three ways:
`implementation` (compile), `additionalRuntimeClasspath` (dev runs) and `jarJar` (the shipped jar). Nothing depends
the other way. Agnostic code reaches the world only through the SPI ports and receives plain typed inputs (a
`ControlInput`, an `*Info` definition, a `*State` seam), never an entity class.

Dependent packages (`mcheli.dependent.*`): `entity` (vehicles, `MchBullet`, `MchExplosion`, flares, cartridges, the
container), `client` (renderers, input, camera, HUD overlay in `client.hud`, client effects in `client.particle`,
screens), `control` (payloads and the rider control state), `particle` (particle options and the effect payloads),
`port` (SPI implementations and content packs), `registry`, `item`, `menu`, `config`, `mixin`, and the self-tests in
`mcheli.dependent` itself. `mcheli.MCHeli` is the `@Mod` entry point.

## Agnostic packages

| Package | Purpose |
|---|---|
| `eval` (`eval.eval.*`, `eval.util`) | The reference expression engine (`mcheli.eval`), ported as is. The HUD is its only user. |
| `math` | `MchMath` (the pure half of `MCH_Lib`: Minecraft's sin/cos table, angle wrapping, clamp, round), `MCH_Math` (rotation matrices and Euler conversion), `MCH_Color`. |
| `value` | Immutable records: `Vec3d`, `AABB`, `RayHit`, `MoveResult`. |
| `util` | `MCH_LowPassFilterFloat`, `MCH_Queue`. |
| `config` | `MchSettings` (the definition-time settings installed before parsing) and `MchDamageFactorList` (the rules of the config damage-factor lists). |
| `info` | The definition-file base: `MCH_BaseInfo`, `MCH_InfoManagerBase` (loads a directory through a `ResourceSource`), `MCH_InputFile`, `MCH_MobDropOption`, and `MCH_SoundsJson` (sound-event naming and grouping rules). |
| `aircraft` | `MCH_AircraftInfo` (the shared vehicle definition) with its seat, rack and flare info, `MCH_Parts` (the switchable-part animator), `MCH_PartStatus` (the bit layout of the synced part-status word), `MCH_MissileDetector` (the RWR alert spacing and the decoy-or-alert choice), `MCH_CameraMode`, `MCH_RecipeSpec` (`AddRecipe` lines) and `MCH_ItemFlattening` (1.7.10 item name and metadata to a 1.21 id). |
| `helicopter`, `plane`, `tank`, `vehicle` | The per-category definitions and managers. `vehicle` also holds the vehicle-entity rules that do not need Minecraft: `VehicleArmor`, `VehicleSalvage`, `VehicleFuel`, `VehicleRepair`, `VehicleSeats`, `VehicleWreck` (the despawn countdown, the crash blast and its one-shot gate, the heli tumble seeds), and the client animation state `RunningGear` (wheels and tracks), `RecoilKick` and `RadarSweep`. |
| `physics` | `MCH_BoundingBox` (per-part hitboxes), `MCH_Rotor` and `MCH_Blade` (rotor blade folding). |
| `sim` | Flight simulation. The `FlightModel`s (`HeliFlightModel`, `PlaneFlightModel`, `TankFlightModel`, `VehicleFlightModel`), the server tick `AircraftFlightController`, the mouse-to-attitude `RotationSolver` with one `ControlMapping` per category, `ControlInput`, the state seams (`AircraftState`, `HeliState`, `PlaneState`, `TankState`, `AircraftSimState`), the world probes (`GroundProbe`, `Buoyancy`, `WheelTerrainSampler`, `WheelTerrainSolver`), `LandingGear`, `ModeSwitchCooldown` and `AimLatch` (the pilot's latched aim and the turret slew). |
| `weapon` | Weapon definitions (`MCH_WeaponInfo` and its manager). Loadout and fire control: `VehicleWeapons`, `WeaponSlot`, `WeaponAim`, `MCH_WeaponMode`, `WeaponSwitchQueue` (the queued weapon switches), `RotBarrel` (which weapons spin a rotating barrel), `SeatWeaponSync` (the synced selection, mode and spent words and the rider notice) and `BarrelSpin` (the client gatling spin). Projectile math as plain functions: `MCH_WeaponBallistics`, `MCH_MissileGuidance`, `MCH_Detonation`, `MCH_ExplosionMath`, `MCH_LockGeometry`. Also `MCH_LockAudit`, `MCH_DamageFactor`, `MCH_BulletModel`, `MCH_Cartridge`, `MCH_SightType`. |
| `hud` | The HUD DSL. `MCH_Hud` parses a file and runs its items, `MCH_HudManager` loads them all, `HudExpression` and `HudVariables` bridge to the engine, `HudState` is what a HUD reads, `HudRenderer` is the draw canvas, and `MCH_HudItemStringArgs` lists the `DrawString` arguments. |
| `model` | The `.mqo` and `.obj` parsers (`MqoModel`, `ObjModel`, `MchModelLoader`) producing an `MchModel`, and `ModelPartIndex` (the reference part-span lookup). Both parsers decode UTF-8 whatever the JVM charset. |
| `spi` | The ports below. |

## SPI ports

| Port | What it offers | NeoForge implementation |
|---|---|---|
| `EntityRef` (with `Role`) | Identity, kinematics including MCHeli's roll axis, the bounding box, a collision-resolved `move`, the mount and passengers, and `world()`. It has no synced-data surface. | `port.NeoEntityRef`, which also maps entity classes to a `Role` |
| `WorldView` | Block, water and collision queries, entity box queries, ray traces, spawning, particles and sounds. | `port.NeoWorldView` |
| `ResourceSource` | Lists and opens definition files and loads models by name. | `NeoResourceSource` (bundled assets), `FileResourceSource` (one content pack), `CompositeResourceSource` (their union and the only model cache). Always use `MchContentPacks.resources()`. |
| `RandomSource` | Randomness for spread and jitter. | `NeoRandomSource` |
| `Logger` | Log sink. | `NeoLogger` |
| `ItemHandle` (with `Resolver`) | An existing item a weapon names in `DispenseItem` or `Item`, resolved once at load. | `NeoItemHandle` (the `MchLegacyItemNames` aliases plus a literal 1.7.10 dye table) |
| `ModelHandle` | An opaque model reference. | The agnostic `MchModel` itself; renderers downcast it. |

`Role` is `HELICOPTER`, `PLANE`, `TANK`, `VEHICLE` (the four MCHeli categories), `HITBOX` (a NeoForge `PartEntity`, in
place of `MCH_EntityHitBox`), `PROJECTILE` (`MchBullet` or a vanilla `Projectile`), `PLAYER`, `LIVING`, `MOB` (a
`Monster`, the reference `EntityMob`) and `OTHER`. There is no seat role, because riders are vanilla passengers. There
is no UAV role either: a UAV config spawns a helicopter or plane entity, so a weapon's `DamageFactor = plane` still
applies to it, as with the reference's exact-class lookup.

Retired ports: `SyncedData`, `Registrar`, `EntityFactory`, `Network`, `ClientContext` and `spi.HudRenderer` (the canvas
is now `hud.HudRenderer`). The value types `BlockPos` and `Direction` were deleted: block coordinates are int triples,
and block faces are handled natively in `MchBullet`.

## Risk decisions

These were set before the port was written. Each is marked with what became of it.

1. **`Vec3d` is immutable.** Kept. It is a record. Agnostic code reads motion as a snapshot and writes it back with
   `EntityRef.setMotion`. Fields that hold a vector are reassigned, never mutated (`MCH_BoundingBox`).
2. **`EntityHandle` is split into `EntityRef`, `SyncedData` and `Role`.** Changed: `SyncedData` was dropped. Entities
   keep native `EntityDataAccessor`s, and the agnostic sims receive typed inputs (`ControlInput`, the `*State` seams,
   the `*Info` definitions). No agnostic code reads a synced slot. `EntityRef` and `Role` stay as planned, with no
   `Class` literals in agnostic code, and entity-keyed sound lives on `WorldView.playSoundAtEntity`.
3. **`EntityFactory` keeps projectiles agnostic.** Retired. A projectile is the dependent entity `MchBullet`, spawned
   by its `spawn*` factories from the vehicle's fire control (`MchFireControl`). The pure parts are agnostic functions it calls:
   `MCH_WeaponBallistics`, `MCH_MissileGuidance`, `MCH_Detonation`, `MCH_ExplosionMath`, `MCH_LockGeometry`,
   `MCH_WeaponMode`. Networking is native `CustomPacketPayload` records, so the `Network` port went too.
4. **`HudRenderer` is a high-level 2D canvas.** Kept, as `mcheli.agnostic.hud.HudRenderer` (text, rectangles, lines,
   textured quads, a matrix stack and the screen size; no raw GL). Changed: the GL-heavy items were not rewritten
   natively. The graduation ladders, textures, camera-direction box and radar are agnostic `MCH_Hud` items drawn
   through the canvas. Only the camera vision effects are native (`MchCameraModeView`).
5. **Single-consumer ports are dropped.** Kept. NBT is native in the entities, with no `NbtIo` port. Changed in detail:
   there is no `DamageCategory` enum. `MCH_DamageFactor` is keyed by `Role` directly, and the trivial value types
   `BlockPos` and `Direction` were deleted rather than kept as plain data.
6. **The zero-import wrapper stubs are not agnostic logic.** Kept. None of `W_MOD`, `W_Version`, `W_ItemList`,
   `W_ResourcePath`, `ChatMessageComponent`, `IPacketHandler`, `ITickHandler` or `NetworkMod` exists in the port.
7. **The `MCH_Lib` split is compiler-verified.** Kept. Only `MchMath` remains (the sin/cos table, `getRotate360`,
   `wrapAngleTo180`, `getPosAngle`, `clamp`, `round`). `MchUtil` lost its last helper and was removed.

Later decisions that shape the code:

- **Seats are vanilla passengers plus a seat map** (`DATA_SEATS`, seat 0 = pilot). There is no `MCH_EntitySeat`. The
  seat rules live in `VehicleSeats`.
- **One entity type per category.** Every helicopter is the `mcheli:helicopter` entity, and the synced config name
  (`DATA_CONFIG`) says which one it is, as the reference's DataWatcher slot 20 did.
- **The server audits client-built missile locks** (`MCH_LockAudit`), which the reference did not do. See Weapons.

## Bootstrap and definition loading

The `MCHeli` constructor runs, in order:

1. `MchConfig.register`: the three config files.
2. `MchContentPacks.discover()`: finds content packs under `<gamedir>/mcheli/` and builds the shared
   `CompositeResourceSource`.
3. `MchRegistries.registerVehicles()`: lists every `helicopters/planes/tanks/vehicles/*.txt` in that resource set and
   mints one `VehicleSpawnItem` per file name, before any definition parses. An illegal id, a reserved id (`fuel`,
   `tab_*`, ...) or a cross-category duplicate is skipped with a warning; the first one wins. Then `register()` attaches
   the deferred registers.
4. `MchSounds.registerPackSounds(...)` and `MchSounds.register`: one `SoundEvent` per pack sound event, then the bundled
   ones.
5. Listeners for the pack finders (`MchPackResourcesFinder`), the payloads (`MchControlNetwork`) and, in a dev
   workspace with the property set, the self-tests.

Common setup then installs `MchSettings` from the common config and loads the definitions in the reference order
(`MCH_MOD.PreInit:137-142`): HUDs, weapons, weapon item resolution (`MCH_WeaponInfoManager.resolveItems`, after the
item registry is frozen), then helicopters, planes, tanks and vehicles. Weapons must load before vehicles: an `AddWeapon`
line naming an undefined weapon is dropped at parse time, as in the reference (the AH-64's `targeting_pod1`, for
example). Vehicle infos carry no item or model fields. A spawn item is found by config name (`MchRegistries.spawnItemFor`),
and a model is loaded by name (`<category>/<name>` resolves to `models/<category>/<name>.mqo`, then `.obj`).

Everything reads through `MchContentPacks.resources()`: registration, the definition parse and every model loader, so
all three see the same files.

### Content packs

A content pack is a folder under `<gamedir>/mcheli/<pack>/`. Its content can sit at the folder root (flat) or under
`assets/mcheli/` (the resource-pack layout); a folder with no recognisable content directory is ignored. A pack adds
vehicles, weapons, HUDs, models, textures and sounds without rebuilding the jar.

- **Two channels.** Configs and models go through `ResourceSource` (`CompositeResourceSource`). Textures, `.ogg` files,
  `sounds.json` and spawn-item models go through the vanilla client resource manager: one fixed, required resource pack
  (`mcheli/content_packs`, served by `MchPackResources`) at the bottom of the stack. It synthesises `pack.mcmeta`, a
  `sounds.json` holding only the events a pack owns, and a `builtin/entity` item model for each pack vehicle that does
  not ship one.
- **Precedence.** The bundled content wins every name collision, then the earlier pack by folder name. Packs only add
  content. Re-skinning a bundled vehicle is a normal resource pack's job.
- **Sound grouping** follows the reference `MCH_SoundsJson` rules: names are lower-cased, one trailing digit is
  stripped to form the event (`engine1.ogg` and `engine2.ogg` are two variants of `engine`), and an illegal name is
  skipped with a warning.
- **Registry-sync constraint.** Each pack vehicle is an item and each pack sound a sound event, and both registries are
  synced on join. A client that lacks a server's pack is disconnected by NeoForge's registry sync, which lists the
  missing `mcheli:` ids. Packs must therefore be installed on both sides. A pack only the client has, or a same-named
  pack with different files, is not detected (see DEFERRED.md).

### Recipes and sounds

Vehicle crafting recipes come from the definitions' `AddRecipe` lines. `MchVehicleRecipes` builds them and
`MchRecipePack`, a synthesised server data pack, serves them as ordinary `data/mcheli/recipe/<vehicle>.json` entries.
They therefore show up in the recipe book and in JEI/EMI and reload with `/reload`. A recipe is generated only when the
spawn item minted for that exact config exists. 1.7.10 ingredient names go through `MCH_ItemFlattening` (metadata
families, names whose meaning changed, removed names) before the plain registry lookup. A potion with a damage value
becomes a `neoforge:components` ingredient that matches its potion contents. UAV configs get no recipe, because the UAV
station is not ported: the 10 UAV lines and e767's, which needs `mcheli:uav_station`, leave 98 vehicle recipes. The
fuel-can refill is the hand-written `mcheli:fuel_refill` recipe.

The bundled sound events follow `sounds.json`. `wrench` groups `wrench1..3`, as the reference did. `lau-68_snd` is an
alias that plays `rocket_snd.ogg`, a deliberate fix: the original jar shipped no `lau-68_snd.ogg`, so those rockets
were silent in 1.7.10. `WorldView.playSound` resolves a bare name to an MCHeli or pack sound first, then to the vanilla
`minecraft:` sound of that name. A namespaced id goes straight to the sound registry, and an unknown name plays nothing.

`META-INF/neoforge.mods.toml` is a template in `neoforge-1.21.1/src/main/templates/`, expanded with the project version
by `generateModMetadata` (also an IDE sync task). Edit it there, not under `src/main/resources`.

## Configuration

The reference's single `mcheli.cfg` (`MCH_Config`) is split into three NeoForge `ModConfigSpec` files by who owns each
option. The code is in `mcheli.dependent.config`. Option names are the reference names in lowerCamelCase, and each
option cites its `MCH_Config` line.

| File | Contents | Notes |
|---|---|---|
| `config/mcheli-common.toml` (`MchConfig.COMMON`) | `allHeliSpeed`, `allPlaneSpeed`, `allTankSpeed` | Baked into each definition while it parses, so a change needs a game restart. Each side reads its own file; position is server-authoritative, so the server's value counts. |
| `<world>/serverconfig/mcheli-server.toml` (`MchConfig.SERVER`) | General (`infinityAmmo`, `infinityFuel`, `autoRepairHP`, `hurtResistantTime`, `aliveTimeOfCartridge`, `enableDebugBoundingBox`), vehicle items (`placeableOnSpongeOnly`, `itemDamage`, `itemFuel`, `preventingBroken`, `breakableOnlyPickaxe`, `dropItemInCreativeMode`), collision (block damage, entity damage, the car and tank break/no-break/material lists), weapons (`explosionDestroyBlock`, `explosionFlamingBlock`, `bulletBreakableBlocks`, `ignoreBulletHit`) and the global damage-factor lists | NeoForge syncs it to every client, the job `MCH_PacketNotifyServerSettings` did by hand, so client code may read it. |
| `config/mcheli-client.toml` (`MchConfig.CLIENT`) | Piloting (`autoThrottleDown*`, `dismountAll`), mouse (flight-sim and stick modes, `invertMouse`, `mouseSensitivity`, `switchWeaponWithMouseWheel`) and render options (`displayHUDThirdPerson`, `hitMarkColor`, `smoothShading`, `defaultExplosionParticle`, `renderDistanceWeight`, `disableRenderLivingSpecials`) | The two the server simulation needs (auto throttle down, dismount all) travel as bits of the control payload, as `MCH_PacketNotifyClientSetting` did. |

Every accessor is safe at any time: while its file is not loaded (the server file outside a world, the client file on a
dedicated server, or mid-unload on another thread) it returns the reference default. `MchServerRules` parses the
server list options once into ready rules: `bulletBreaks`, the crush tests, `applyDamageVsEntity`,
`applyDamageByExternal` and `ignoresBulletHit`. It rebuilds whenever the server file loads, reloads or unloads. Agnostic
code reads only `MchSettings`, the plain values installed before definitions parse, and gets runtime options as
parameters.

The Mods-menu Config button opens NeoForge's generated `ConfigurationScreen` over all three files
(`MchConfigClient`, a `dist = CLIENT` `@Mod` class). Options of unported features and 1.7.10-only knobs (key codes,
numeric ids, GL hacks, item recipes) are deliberately absent; see DEFERRED.md.

## Entities and registration

`MchRegistries` registers:

- Four vehicle entity types, one per category: `mcheli:helicopter` (`MchHelicopter`), `mcheli:plane` (`MchPlane`),
  `mcheli:tank` (`MchTank`) and `mcheli:vehicle` (`MchGroundVehicle`, the ground emplacements). All extend
  `AbstractMchVehicle` and are tracked to 13 chunks (the reference's 200 blocks). The collision core is resized from
  the config `Width`/`Height`; real hits use the per-part armour boxes.
- Projectiles: `mcheli:bullet` for rounds the client dead-reckons (updates every 5 ticks, the reference interval), and
  `mcheli:steered_bullet` for rounds whose path the server steers (guided missiles, torpedoes, ricocheting delay-fuse
  rounds; updates every tick). Both are `MchBullet`, tracked to 33 chunks (the reference's 530 blocks), never saved
  and never summonable. The old id `mcheli:demo_bullet` is an alias of `mcheli:bullet`.
- `mcheli:cartridge` (`MchCartridge`, a shell casing that exists only on clients), `mcheli:flare` (`MchFlare`) and
  `mcheli:container` (`MchContainer`, a placeable 54-slot cargo box).
- Items: one `VehicleSpawnItem` per vehicle config, plus `fuel`, `wrench`, `container` and the four flat `tab_*`
  icons. Each category has its own creative tab; UAV configs are hidden.
- Particles `weapon_fx`, `explode_fx` and `smoke_fx`, the riding menu `mcheli:vehicle`, and the `fuel_refill` recipe
  serializer.

`AbstractMchVehicle` is the shared vehicle base, and a coordinator. It keeps the 22 synced-data accessors, every
Entity override and protected subclass hook, `tick()` and the NBT methods as ordered call lists, the config identity
(`DATA_CONFIG` and the resolved `MCH_AircraftInfo`), the skin, the engine spool and throttle sync, and the rotation
authority and client interpolation (the roll axis, the lerp state, the rider's client-computed rotation). Every other
area is a `final` component in the same package, created in a field initializer, holding only a back-reference to the
entity:

| Component | Owns | Writes |
|---|---|---|
| `MchLogistics` | Fuel burn, the fuel-can slots and siphon, the fuel and ammo supply auras, the can spill on removal | `DATA_FUEL` |
| `MchCountermeasures` | Flares, the missile decoy, the RWR tones | `DATA_FLARE_STATE` |
| `MchVehicleAnimation` (public) | The client running gear, gatling barrel, rotor spin, radar sweep and blips, recoil kick | nothing |
| `MchVehicleEffects` | The client weapon and damage smoke, the server fire cosmetics | nothing |
| `MchWeaponSync` | The synced weapon view (selection, modes, totals, spent rails, the rider notice) and its HUD readers | `DATA_SEAT_WEAPONS`, `DATA_AMMO_ALL`, `DATA_WEAPON_SPENT` |
| `MchVehicleParts` (public) | Canopy, hatch, wing and sweep, weapon bays, light hatch, rotation parts, throttle lever, camera turret, landing gear, and their render read-models | `DATA_PART_STATUS`, `DATA_GEAR` |
| `MchVehicleVision` | Camera modes, the night-vision viewers, the search light | nothing (its bits go through `MchVehicleParts`) |
| `MchRiderPose` | The aim latch and turret slew, seat attachment, camera eyes, dismount, the model lift, HideGM groups, the view camera | nothing |
| `MchPartHitboxes` | The per-part boxes and the projectile ray queries | nothing |
| `MchTerrainContact` | The light sweep, crush and ram, attitude-crash and submersion damage | nothing |
| `MchVehicleCrew` | The seat map, per-seat controls, boarding and the team gate, the passenger hooks, forced-mount ejects, seat switching, gunner mode | `DATA_SEATS`, `DATA_GUNNER_MODE`, `DATA_SEAT_GUNNER_MODE` |
| `MchFireControl` (with `MchLockAudits`) | The loadout and its saved ammo, the switch queue, the weapon tick and the fire, destruct, the TV missile, resupply, the HUD mortar distance; the server lock audit | `DATA_FIRING`, `DATA_RELOADING`, `DATA_RECOIL_SEQ`, `DATA_RECOIL_VALUE`, `DATA_RECOIL_YAW` |
| `MchVehicleHull` | HP and the hurt pipeline, salvage and the spawn-item data, the wreck (tumble, crash detonation, despawn), auto repair, area repair and medevac regeneration | `DATA_DAMAGE_TAKEN`, `DATA_DESTROYED` |

The entity itself writes `DATA_ROLL`, `DATA_SIM_THROTTLE`, `DATA_THROTTLE`, `DATA_SKIN` and `DATA_CONFIG`;
`MchVehicleMath` holds the shared static helpers. Three rules hold the split together:

- **Facade.** The public and protected surface stays on the entity as one-line delegates, so callers, the state
  adapters and the four subclasses never see a component. Outside the components, only client code (the renderers,
  the HUD variables, the camera mixin) and the server self-test `DemoCrewFuelSelfTest` read the two public components
  directly, through `animation()` and `vehicleParts()`. Components reach each other through the entity's
  package-private accessors (`crew()`, `fireControl()`, `hull()`, ...) or its public methods.
- **Hooks stay virtual.** A component calls a subclass hook only through the entity (`v.destroyVehicle(...)`,
  `v.seedWreckTumble()`, ...) and names its own body for it `base*` or `handle*`, so an override is never skipped.
- **One writer per synced accessor**, as in the table.

The platform-neutral rules are agnostic classes (`vehicle`, `sim`, `aircraft` and `weapon` above). The category
subclasses supply the flight model, the rotation mapping and the category's own parts: the heli's rotor fold and hover
mode, the plane's VTOL nozzle, the tank's tracks.

## Flight simulation and authority

The split follows the reference:

- **Position is server-authoritative.** Each server tick the vehicle snapshots its seats' `MchControlState` into a
  `ControlInput` and runs `AircraftFlightController.tickServer` with its `FlightModel`: throttle and control, the
  `onUpdateAircraft` tail (`afterControl`: hover levelling, wreck throttle bleed), force integration, the
  collision-resolved `EntityRef.move`, post-move damping, then rotation wrapping. Clients interpolate the position.
  Only the pilot seat (seat 0) drives; a gunner's movement bits are dropped. The probes are agnostic: `GroundProbe` is
  the reference `getBlockIdY` (every non-air block but fire counts, liquids and plants included), `Buoyancy` the
  five-slice water fraction, `WheelTerrainSampler` the tank's per-wheel ground contact.
- **Rotation is client-authoritative for the pilot.** `MchClientRotation` runs every render frame. It builds a virtual
  stick from the local player's raw mouse turn (captured by `EntityTurnMixin` before vanilla's ±90° pitch clamp) times
  `MouseSensitivity × 0.1`, with the reference sign (mouse up = nose up, stacked with `invertMouse`), clamp and decay.
  It runs the `RotationSolver` with the vehicle's `ControlMapping` and welds the pilot's view to the hull on the axes
  the mapping overrides (a hovering heli or a plane in gunner mode frees them). The result goes to the server once per
  tick in `mcheli:rotation`. The server stores it and syncs it to other clients (yaw and pitch through the tracker,
  roll through `DATA_ROLL`); it does not rate-limit it, as the reference did not.
- **The rotation-owning client** also runs `clientControlTick`: the hover levelling for aircraft, and the tank's client
  throttle copy that feeds `TankControlMapping`.
- `simThrottle()` is the sim throttle on the server and the synced `DATA_SIM_THROTTLE` on a client, so client
  pre-checks (such as the plane's gunner-mode gate) read the real value.

Modes: heli hover mode (`MchHelicopter.DATA_HOVER`, the jump key) and pilot gunner mode (`DATA_GUNNER_MODE`, R) share
the throttle save and restore. VTOL (`MchPlane.DATA_VTOL`, the seat-previous key H for a plane pilot) ramps the nozzle
1.5° per tick on both sides. The tank brake is the held jump key. Target drones fly themselves and self-destruct when
their fuel runs out.

## Controls

Keys are vanilla `KeyMapping`s in the "MCHeli" category (`MchKeybinds`), except where the port reads a vanilla binding
while seated (`MchClientInput`). Every action is re-checked on the server.

| Action | Port default | Reference key (default) |
|---|---|---|
| Throttle, steer | W S A D (vanilla movement) | `KeyUp/Down/Left/Right` (W S A D) |
| Fire | Left mouse | `KeyAttack` (left mouse) |
| Next weapon / cycle | F (vanilla swap-offhand, suppressed while seated); mouse wheel (`switchWeaponWithMouseWheel`) | `KeySwitchWeapon1/2` (middle mouse, G); mouse wheel |
| Weapon mode | K | `KeySwitchWeaponMode` (X) |
| Gunner mode, camera positions (pilot); gunner camera on or off (switchable gunner seat) | R | `KeySwitchGunner` (H) |
| Gunner zoom; hatch or wing outside gunner mode | Z | `KeyZoom` (Z) |
| Camera mode or search light (seats 0, 1) | N | `KeyCameraMode` (C) |
| Flares (seats 0, 1) | V | `KeyFlare` (V) |
| Landing gear (pilot) | B | `KeyGearUpDown` (B) |
| Resupply menu (pilot); next seat (gunner) | G | `KeyGUI` (R) |
| Previous seat (gunner); rotor fold, VTOL (pilot) | H | `KeyExtra` (F) |
| Held seat-switch modifier | Left Alt | `KeyFreeLook` held (left Ctrl) |
| Free-look toggle (pilot) | M | `KeyFreeLook` (left Ctrl) |
| Hover mode (heli pilot); brake (tank driver, held) | Jump | `KeySwitchHovering` (Space) |

Where a port default differs, either the reference key is a 1.21 vanilla key (C, X, F, left Ctrl) or the port chose
its own layout early on (G, H, R). Unported reference keys are listed in DEFERRED.md.

## Vehicle systems

- **Seats.** Seat 0 is the pilot. The automatic rule keeps seat 0 for players; a mob gets it only on request
  (`preferSeat`). `ExclusionSeat` groups, `CanRide` and the rack-slot reservation are honoured. A seat switch re-keys
  the seat map; the rider never dismounts. A forced mount (login reattach, `/ride`, chunk reload) with no free seat
  takes a non-player crew member's seat, or ejects the rider on the next tick, so vanilla never discards the vehicle.
  A dedicated gunner seat is always in gunner mode. A switchable one (an `AddGunnerSeat` whose seventh value is
  `true`) enters gunner mode when boarded and its occupant toggles it with R (`DATA_SEAT_GUNNER_MODE`, one bit per
  seat, server-written and not saved); a ground emplacement ignores the key, as the reference did. Gunner mode drives
  the seat's camera position, the scope zoom, the seat pitch limits and the HUD `gunner_mode`. A player taking seat 1
  ends the pilot's gunner mode unless the config enables concurrent gunner mode.
- **Boarding** (`interact`, `interactAt`, reference `interactFirst`). A player may not board, nor click a seat, while
  a player of another team rides (`checkTeam`, vanilla scoreboard teams; a crew player with no team never refuses, a
  player with no team is refused by a teamed crew). A wrench click never boards; with sneak it cycles the paint
  scheme. A bare sneaking click does nothing (it opened the unported cargo inventory). The pilot seat obeys the
  canopy and the mode-switch cooldown below.
- **Parts** (`DATA_PART_STATUS`, server-written). Bits 0, 3 and 4 are the canopy, wing and hatch (the reference
  DataWatcher 31 positions), bit 6 the search light, bits 8 to 11 the camera modes of seats 0 and 1, and bits 16 to 31
  the open weapon bays. Agnostic `MCH_Parts` animators ramp toward the bits on both sides, so only the bits sync. The
  canopy opens on the first click on the pilot seat and closes when a pilot takes it; an open canopy blocks flight
  control and gunner mode. The pilot's zoom key (Z) outside gunner mode switches the hatch, or folds or sweeps a
  plane's wing when it has no hatch. Sweep wings also spread automatically. Weapon bays open while an occupied seat
  has a bay weapon selected. The landing gear is the agnostic `LandingGear` (status in `DATA_GEAR`, the B key, the
  reference auto-extend rules; a floatplane retracts over water by itself). Every manual part or mode switch (canopy,
  hatch, wing, gear, VTOL) shares one 20-tick `ModeSwitchCooldown`, which also blocks pilot boarding, canopy or not.
  NBT keeps `PartStatus` (wing and hatch only, the reference mask 24) and `SearchLight`.
- **Camera modes** (the N key, seats 0 and 1, configs with `EnableNightVision`): normal, night vision and thermal
  vision. The server keeps a 250-tick night-vision effect on the viewer, topped up at 210 ticks, and removes only that
  effect when the viewer leaves or sits down in normal mode. A viewer logging out is released by `MchServerEvents`
  (the logged-out event), because vanilla saves the player file before it dismounts him. Thermal vision is the post
  chain `mcheli:shaders/post/thermal.json` (white-hot luminance with Sobel edges, standing in for the 1.7.10 "pencil"
  shader). Night vision adds the green `alpha.png` noise, and teammates show an IR strobe in either mode. On a vehicle
  with `AddSearchLight` the same key toggles the light instead (a beam, a full-bright hull, and
  `EntityRenderDispatcherMixin` raising the block light of living entities in the beam).
- **Camera.** `CameraMixin` parents the camera to the vehicle: the config `CameraPosition`, a turret orbit for a
  `rotSeat`, the full hull rotation for a locked-view aircraft, and the faded camera roll for tanks. `MchGunnerView`
  owns the gunner zoom (doubling up to `CameraZoom`) and the seat pitch clamp. `MchFreeLook` is the pilot free-look
  toggle (M); the held modifier (Left Alt) turns the seat keys into "leave your seat". The gunner-mode key (R) also
  steps through the configured camera positions, as the reference `KeySwitchMode` state machine did.
- **HP, armour and wrecks.** Damage goes through the per-part boxes and `VehicleArmor`. At max damage the vehicle
  becomes a wreck (`DATA_DESTROYED`): the riders are ejected, the wreck tumbles and is darkened, and it is removed
  after 500 ticks. A player may not hurt a vehicle whose pilot is a player he may not harm (PvP off, or the same team
  without friendly fire). A melee hit from a player may instead break an unridden vehicle back into its item
  (`VehicleSalvage`, gated by `preventingBroken`, `breakableOnlyPickaxe` and `dropItemInCreativeMode`). The item keeps
  the fuel and damage (`MCH_Fuel`, `MCH_Damage`).
- **Fuel, supply and repair.** Fuel burns with the sim throttle (`VehicleFuel`) and refills from fuel cans in the riding
  menu while grounded. Supply vehicles refuel and rearm nearby vehicles. Auto-repair mends 1 % HP every 2 s after 30 s
  unhit while HP is at least `autoRepairHP`. The wrench mends 10 HP every 20 ticks. Medevac configs give their crew
  Regeneration.
- **Terrain.** Tanks and cars crush the blocks on the server's crush lists and ram entities (`collisionEntityDamage`,
  `collisionEntityTankDamage`). Every vehicle clears the snow layers, lily pads and cake under it (the reference
  `onUpdate_updateBlock` sweep). The crushing and the sweep are gated by `collisionDestroyBlock` and `mobGriefing`.

## Weapons and projectiles

**Loadout.** `VehicleWeapons.build` groups consecutive same-type `AddWeapon` lines into one `WeaponSlot` per selectable
weapon. Selectable types: machine guns, rockets, marker rockets, AA/AT/AS/TV missiles, bombs, torpedoes, CAS,
dispensers, smoke and dummy. Targeting pods are left out (DEFERRED.md). Each seat has its own selection, eligibility
follows who is aboard (a pilot may borrow an empty gunner seat's `canUsePilot` weapon), and the aim comes from the
operator's look (`WeaponAim`). Fire cadence, magazine, reload, heat and the switch lockouts are pure integer state in
`WeaponSlot`. Lockout and reload follow the reference client copy (25 ticks after a switch, 15 after a mode change, the
full `ReloadTime`). The fire interval is the server's `Delay`. Switching away reloads a partial magazine. Ammo totals
(magazine plus reserve) are per weapon. A weapon with `MaxAmmo` spawns empty and is rearmed from the riding menu with
the items its `Item` lines name, unless `infinityAmmo` is set. The save keys ammo by weapon name
(`AcWeaponsAmmoByName`, `name#n` for the n-th separate group of a weapon). A save holding only the legacy slot-ordered
`AcWeaponsAmmo` array is mapped through the pre-campaign slot order when the loadout is first built.

**Weapon modes.** `MCH_WeaponMode` gives what a mode means per type: cannon AP/HE, rocket plain/HEIAP, AT missile
direct/top attack, TV missile guided/unguided. The live mode is held per `WeaponSlot` on the server and synced (the
reference kept it on the client and sent it with each shot); the K key switches it. A shot's mode-dependent explosion
and piercing travel as an `MCH_WeaponInfo.shotVariant`.

**Firing.** The client sends the held fire bit in `mcheli:control`. The server's weapon tick
(`MchFireControl.tickServer`) fires each seat whose fire is held through `fireSelectedWeapon`, which picks the `MchBullet` factory for the type: `spawnWeapon` (guns,
rockets, marker rockets, dispensers whose round uses its item where it lands, an unguided TV missile),
`spawnGuided` (AA pursuit, AT direct or top attack, at a locked target), `spawnPointGuided` (AS at the aimed ground
point; TV toward the operator's aim), `spawnGuidedTorpedo` and `spawnTorpedo`, `spawnDropped` (bombs, released at the
carrier's velocity), and `callAirstrike` (CAS, and a planted marker rocket's airstrike: a bombing run at the point). A
`Destruct` bomb blows up a seat-less UAV helicopter in place. Guided kinds are `MchBullet.DATA_GUIDED` AA (1), AT direct (2), AT top attack (3), AS (4) and
TV (5); a guided torpedo sets `DATA_TORPEDO` with a guide point. Guidance runs on the server, and the client takes the
steered path through `lerpTo`. A TV missile turns, each tick, toward the block the operator's sight line meets within
the operator-to-missile distance plus 64 blocks. The sight ray is walked in chunk-sized legs and stops at the first
chunk that is not fully loaded (`ServerChunkCache.getChunkNow`; `hasChunk` also passes a chunk that is still loading,
and reading its blocks would hold the server thread until it is). When it meets nothing the missile flies along the
operator's look. It dies 1200 blocks from the operator. There is no missile camera (DEFERRED.md).

**Attribution** is the reference model. `MchBullet.shooter` is the operating entity (reference `shootingEntity`, the
seat's player). It gets the damage and kill credit (so PvP and team rules apply), the hit marker, the 5820-block
distance anchor, TV steering and the AT top-attack climb height. Every round despawns when its shooter dies or logs out,
and a guided AA/AT round without one is discarded. `MchBullet.launcher` is the vehicle the round left (reference
`shootingAircraft`): neither it nor its crew is hit by the round or its blast. Two rounds pass through each other when
their launchers are equal or their shooters are equal (`MchBullet.canHit`, reference `MCH_EntityBaseBullet:704-708`).
A missing owner on both sides counts as equal, as in the reference's `W_Entity.isEqual` (a null entity is id -1), so
ownerless rounds, such as the sibling bomblets of an ownerless cluster, never hit each other. Otherwise rounds of
different shooters can shoot each other down. Child rounds (bomblets, an airstrike's bombs) carry the parent's shooter
and launcher.

**Impact and explosions.** Impact and timeout rules are in `MCH_Detonation`. Proximity fuses apply to guns and bombs
only. Piercing rounds pass through blocks, and blocks on `bulletBreakableBlocks` are shot through. `MchExplosion.explode`
runs the reference entity phase itself (one hit per victim, `PartEntity` resolved to its parent, the global factor and
then the weapon's `DamageFactor`, `hurtResistantTime`, knockback scaled by Blast Protection, FAE ignition). It then runs
a vanilla `Explosion` for blocks only, gated by `mobGriefing` and `explosionDestroyBlock`. It plays the reference sound
and sends one `mcheli:explosion_fx` per player within 200 blocks; each client rebuilds the visual (`MchExplosionFx`). It
takes `exclude` (spared, with everyone aboard) and `attacker` (credited) separately. A round passes its launcher and
shooter, and its blast (`roundBlast`) also spares the shooter and flashes his hit marker when it reaches a hurtable
entity. A wreck blast spares nobody and credits a player killer. A crash blast credits nobody.

**Locks and countermeasures.** The lock is built on the client (`MchLockTracker`, the reference
`MCH_WeaponGuidanceSystem`): the seeker cone, line of sight and air/ground/water filters, ramping while fire is held.
The target's stealth (`AbstractMchVehicle.lockStealth`: a vehicle's config `Stealth`, a rider's that of the vehicle it
rides, otherwise 0) scales the lock as in the reference, through `MCH_LockGeometry`: the range is `lockRange * (1 - s)`
for acquiring and holding, the acquisition cone is `lockAngle * ((1 - s) / 2 + 0.5)` while the hold keeps `lockAngle`,
and the lock time is `lockTime * (1 + s)`. A `Stealth = 1` target (the penetrator) cannot be locked at all. Every port
crew member rides the hull, so the whole crew takes its stealth; in the reference only the pilot did (the other seats
rode a seat entity).
The client sends `mcheli:notify_lock` on the lock's first tick and every 15 ticks, and the completed target id in the
control payload. The server checks the claim with `MCH_LockAudit` before it launches a guided round. The claim must name
the entity the notices named, at least `minLockAge(lockTime)` = `max(1, lockTime - clamp(lockTime / 3, 1, 10))` ticks
must have passed since the first notice, and the target must have been inside the range and a widened cone with clear
sight. The server scales all three for the target's stealth as the client does (`MCH_LockAudit.serverLockRange`,
`serverAcquireConeAngle`, and the lengthened lock time), so a stealth-1 target's claim is never accepted. An honest
client claims `lockTime - 1` ticks after its first notice, so it is never delayed; the claim is re-checked every tick
the trigger is held, so jitter can only delay a shot. This deliberately departs from the
reference, which trusted the id. The notices also drive the target crew's RWR tone (`mcheli:rwr`, flare-equipped
vehicles only). Flares (`MchCountermeasures.tickServer`, the reference `MCH_MissileDetector`) decoy guided rounds
homing on the flaring vehicle during the burn window. The `MchFlare` entities themselves are cosmetic.

**Per-seat weapon sync.** `DATA_SEAT_WEAPONS` is a compound with `s` (each seat's selected weapon index, -1 for none)
and `m` (every weapon's mode, written only when one is non-zero). It changes only on a switch, a mode change or a crew
change, and every client needs it (smoke trails follow the pilot's selection, bays open by selection). The numbers that
move every tick (magazine, reload 0..1, heat 0..1, in preparation) reach only that seat's rider, through
`mcheli:seat_weapon`. It is sent when the quantised values or the rider's seat change, plus a refresh every 40 ticks.
`selectedAmmo`, `selectedReload`, `selectedHeat` and `selectedInPreparation(seat)` read that cache on a client and the
live loadout on the server. `DATA_AMMO_ALL` (every weapon's total, for the riding menu) and `DATA_WEAPON_SPENT` (fired
missiles vanish from their rails) are separate.

**Client-only effects.** Shell casings (one `mcheli:cartridge` per shot to players within 64 blocks) and blast debris
flares exist only on clients. `MchClientEffects.add` gives each a negative id so it can never clash with a real entity.
`MchSmokeOptions` flags are the reference `MCH_ParticleParam` kinds (`PLAIN`, `DIFFUSIBLE`, `SPLASH`, `TO_WHITE`); no
flags is the port's general smoke. The muzzle flash uses the explosion sheet (`MchExplodeParticle`).

## HUD engine

A HUD is a `hud/*.txt` file of draw commands. `MCH_HudManager` loads them; a vehicle names its HUDs per seat with
`HUD =`. `MCH_Hud` keeps the reference line grammar, strict `If`/`EndIf` pairing, centre-origin coordinates, one static
current colour, `Call` (guarded against cycles) and `Exit`. Every numeric argument is a `HudExpression`: parsed once by
the ported reference engine (`mcheli.agnostic.eval`), then evaluated on its double path every frame against
`HudVariables`. There is one engine; the old `MchExpr` evaluator is gone.

- **Variables.** `HudVariables` answers the built-in names in `HudState.VARIABLES` (the reference `updateVarMap` key
  set) from the `HudState`, and keeps assigned names in its own map. The static scope reads the `HudState` only during
  `MCH_Hud.draw` and is released at the end, so a left `ClientLevel` is not kept reachable. Assigned variables and the
  current colour persist across frames, as in the reference.
- **Format arguments.** `HudState.formatArg` must return the reference boxed type (`Integer`, `Float`, `Double`,
  `String` or `Date`), or `HudState.SKIP_STRING` to suppress the whole `DrawString`.
- **Error policy** (a deliberate divergence: the reference threw and stopped the client). A structural error (`If`
  inside `If`, a stray `EndIf`, an `If` open at the end of the file) rejects that one file. An expression that does not
  parse drops only its item, and a bad `If` condition reads as false, so its block stays hidden. An item that throws
  while drawing (an undefined variable, a bad literal, a format mismatch) is skipped for that frame and logged once.
  `MCH_Hud.draw` also takes a `DrawErrorListener` that hears every draw-time failure, so tests can fail on them.
- **Dependent side.** `MchHudOverlay` is a GUI layer above the crosshair. It picks the HUD of the seat the local player
  occupies (a helicopter or plane pilot in gunner mode sees the seat-1 HUD), draws only in first person unless
  `displayHUDThirdPerson` is set, and draws the night-vision noise first. `MchHudVarState` is the live `HudState`. It snapshots the viewer's own
  seat weapon once per frame, so a gunner sees his own weapon, ammo, reload, heat and lock. Its "seam" methods return
  the reference's "feature absent" values for systems the port lacks (UAV station, cargo inventory, the TV-missile
  readouts, TestMode), so the configs' `If` guards keep those blocks hidden. `NeoHudRenderer` draws over `GuiGraphics`
  in issue order (lines are flushed per call).
- One more divergence: a weapon `DrawString` argument is skipped while the viewer has no current weapon (the
  reference showed the last weapon's stale values). `DrawCameraRot` is drawn whenever a HUD lists it, pilot HUDs
  included, as in the reference.
- `BundledHudTest` derives one variable scenario per `If` gate from the shipped HUD files and asserts that every block
  opens and draws cleanly at least once.

## Networking

Every payload is a `CustomPacketPayload` record registered by `MchControlNetwork` with protocol version `"1"`. Bump it
on a wire-incompatible change. Every serverbound handler runs on the main thread and first requires that the sender
rides the addressed vehicle (for `notify_lock`, which names no vehicle, the one he rides); the table lists the checks
after that. Clientbound handlers are guarded with `FMLEnvironment.dist`, so a dedicated server never links client
classes.

| Id | Direction | Fields | Purpose | Server validation |
|---|---|---|---|---|
| `mcheli:control` | C→S | vehicleId, bits, lockTargetId | Held keys: throttle up/down, left/right, brake, free look, fire, plus the auto-throttle-down and dismount-all settings. Sent on change and as a keepalive every 100 ticks. | Seat must resolve. Drive bits and settings are kept only for seat 0, the brake only when the vehicle has one. Fire and free look apply to every seat. The lock id is stored raw and checked by the lock audit at fire time. |
| `mcheli:rotation` | C→S | vehicleId, yaw, pitch, roll | The pilot's client-built rotation, once per tick. | Non-finite angles are rejected. Seat 0 of a mouse-steered vehicle only. Yaw and roll are wrapped and pitch is clamped to ±90°; no rate limit, as in the reference. |
| `mcheli:weapon_switch` | C→S | vehicleId, direction | Next or previous weapon for the sender's seat (F, or the mouse wheel). | Not on a wreck, not while the pilot is in the resupply GUI. Presses are queued per seat, bounded at one lap of the loadout. |
| `mcheli:weapon_mode` | C→S | vehicleId | Switch the selected weapon's mode (K). | A seat with a selected weapon, not a wreck, no resupply GUI. Only a weapon with at least two modes and no `FixMode` changes; starts the 15-tick lockout. |
| `mcheli:vehicle_gui` | C→S | vehicleId, action, weapon | Open the riding menu, or reload a weapon from the player's inventory. | The sender's vehicle must be this one, and the sender must be in seat 0. |
| `mcheli:seat_switch` | C→S | vehicleId, action | Next seat, previous seat, or grab the pilot seat. | Moves only into an empty seat. Grabbing the pilot seat also needs `CanRide` and the seat rules (`ExclusionSeat`). |
| `mcheli:gunner_mode` | C→S | vehicleId | Toggle the pilot's gunner mode, or a switchable gunner seat's (R). | Seat 0: leaving is always allowed. Entering needs `EnableGunnerMode`, a closed canopy and no player in seat 1 (unless concurrent gunner mode); a helicopter must not be hovering and must have roll and pitch under 40°; a plane needs roll and pitch within 40°, throttle over 0.6 and nothing within 5 blocks below. Another seat: `canSwitchGunnerModeOtherSeat` (a switchable gunner seat, not on a ground emplacement). Any other seat is ignored. |
| `mcheli:camera_mode` | C→S | vehicleId | Step the seat's camera mode, or toggle the search light (N). | Seat 0 or 1. Search light: not a wreck. Camera: `EnableNightVision` and not a wreck. |
| `mcheli:fold_blade` | C→S | vehicleId | Fold or unfold a helicopter's rotor (H, parked). | A helicopter; seat 0 and `canSwitchFoldBlades`. |
| `mcheli:hatch_switch` | C→S | vehicleId | Open or close the hatch, else fold or sweep the wing (Z outside gunner mode). | Seat 0, not a wreck, the part at rest and the shared cooldown ready; the wing also needs `canFoldWing`/`canUnfoldWing`. |
| `mcheli:use_flare` | C→S | vehicleId | Dispense flares (V). | Seat 0 or 1 and `canDeployFlare`. |
| `mcheli:vtol_toggle` | C→S | vehicleId | Toggle the VTOL nozzle (H, plane pilot). | A plane; seat 0 and `canSwitchVtol` (VTOL config, cooldown ready, not mid-transition, roll within 30°). |
| `mcheli:hover_toggle` | C→S | vehicleId | Toggle helicopter hovering mode (jump). | A helicopter; seat 0 and `canSwitchHoveringMode` (not in gunner mode, blades usable, roll and pitch under 40°). |
| `mcheli:landing_gear` | C→S | vehicleId | Raise or lower the landing gear (B). | Seat 0, not a wreck, a gear config, `canFold`/`canUnfold` and the shared cooldown. |
| `mcheli:notify_lock` | C→S | targetId | "I am locking this entity": starts or feeds the lock audit and drives the target's RWR tone. | The sender's seat must hold a lock-on weapon and the target must exist. The RWR tone is relayed only within the weapon's lock range (plus slack), at most once per 10 ticks per seat, and only to a flare-equipped intact target. |
| `mcheli:rwr` | S→C | kind | The RWR tone, "being locked" or "missile inbound". | Sent to seats 0 and 1 of a flare-equipped vehicle. |
| `mcheli:explosion_fx` | S→C | x, y, z, size, inWater | One explosion's visual; the client rebuilds it. | Sent to players within 200 blocks. |
| `mcheli:cartridge` | S→C | casing config, position, gun angles, motion | One ejected shell casing, built on the client. | Sent to players within 64 blocks, once per shot. |
| `mcheli:hit_mark` | S→C | none | Flash the hit marker. | Sent to the operating player on a direct hit, or when his round's blast reaches a hurtable entity. |
| `mcheli:seat_weapon` | S→C | vehicleId, seat, ammo, reload, heat, inPreparation | A seat's magazine, reload, heat and preparation for its own rider's HUD. | Sent only to that seat's rider, when the quantised values or the seat change, plus every 40 ticks. |

## Synced data

| Entity | Accessors |
|---|---|
| `AbstractMchVehicle` | `DATA_CONFIG` (which vehicle), `DATA_SEATS` (seat index to entity id), `DATA_ROLL`, `DATA_SIM_THROTTLE` (the real throttle), `DATA_THROTTLE` (spooled engine power for rotors and sound), `DATA_SKIN`, `DATA_GEAR` (true = up), `DATA_PART_STATUS`, `DATA_GUNNER_MODE`, `DATA_SEAT_GUNNER_MODE` (switchable gunner seats, a bit per seat), `DATA_RELOADING` (pilot in the resupply GUI), `DATA_SEAT_WEAPONS`, `DATA_AMMO_ALL`, `DATA_WEAPON_SPENT`, `DATA_FLARE_STATE`, `DATA_RECOIL_SEQ`, `DATA_RECOIL_VALUE`, `DATA_RECOIL_YAW`, `DATA_FIRING` (gatling spin), `DATA_FUEL`, `DATA_DAMAGE_TAKEN`, `DATA_DESTROYED` |
| `MchHelicopter` | `DATA_FOLD` (rotor fold state 0 to 3), `DATA_HOVER` |
| `MchPlane` | `DATA_VTOL` (the nozzle target; the angle ramps on both sides) |
| `MchBullet` | Model, colour, gravity, acceleration factor, trail kind, size and start tick, marker state, `DATA_GUIDED`, `DATA_TORPEDO`, in-water gravity, `DATA_RICOCHET`, splash power, in-water velocity |
| `MchFlare` | The airburst fuse |

The `AbstractMchVehicle` accessors are declared in one block of that class, in id order (8 to 29; `MchHelicopter` and
`MchPlane` continue from 30), and nowhere else: an accessor for that class declared in another class would take its
id in that class's lazy static initialiser and shift or duplicate the layout. The `hp` self-test pins every id. Each
accessor has exactly one writing class (the entity or one component, see Entities and registration); the rest only
read it.

The rule is to sync status and let both sides run deterministic animation from it (parts, gear, nozzle, rotor fold).
Values one rider needs go in a targeted payload, not synced data (`mcheli:seat_weapon`). Hover mode and gear status are
not saved, as in the reference.

## Rendering

- `MchModelEntityRenderer` is the base renderer, one instance per category. It draws the `.mqo`/`.obj` model of
  whatever config the entity is, resolved by the synced config name with a cached lookup. The transform is yaw, pitch
  and roll with no scale (the parsers already divide by 100). The whole vehicle draws in one unsorted translucent pass
  (`MchRenderTypes.vehicle`, `entity_translucent_cull` without upload sorting, the reference GL technique), so glass
  shows the interior drawn before it in model order.
- Composition follows `MCH_RenderAircraft.doRender`: `renderPreBodyParts` (a plane's nozzles, wings with pylons and
  rotors, so the body glass blends over them), the `$body` span and the static declared parts, the category's
  `renderDynamicParts`, the common parts (`renderCommonParts`, canopy last), then the search-light beams
  (`MchRenderTypes.SEARCH_LIGHT`). Part lookups use the agnostic `ModelPartIndex`, cached per model. Every pass takes
  the reference hull tint (`wreckColor`: grey 191 intact, 38 for a wreck). `MchModelRenderer.shadeModel` mirrors
  `glShadeModel` (`smoothShading` and the config's `SmoothShading`).
- Spawn-item icons are the vehicle's own model (`VehicleItemRenderer`, a `builtin/entity` item model). Vehicles render
  out to `renderDistanceWeight` (default 10, about 1000 blocks) and rounds to 256 blocks, both capped by tracking.
- A client resource reload (F3+T) clears the model cache (`MchContentPacks.clearModelCaches`), the item icon cache and
  the HUD texture-size cache, so edited models and GUI sheets are picked up. Definitions do not reload.
- Mixins (`mcheli.mixins.json`, all client): `CameraMixin` (the vehicle camera), `EntityRenderDispatcherMixin` (search
  light on living entities) and `EntityTurnMixin` (raw mouse turn for the stick).
- GUI layers above the crosshair: the HUD (`MchHudOverlay`), the hit marker (`MchHitMarker`, coloured by
  `hitMarkColor`) and the wrench's target HP bar (`MchWrenchOverlay`). `MchRiderRenderHider` hides riders of
  `HideEntity` configs and the crew's name tags.

## In-game self-tests

The NeoForge module is tested by self-tests that run in a real dedicated server (`MchSelfTests`, dev workspace only):

```
./gradlew :neoforge-1.21.1:runServer -PmcheliSelfTests              # every test
./gradlew :neoforge-1.21.1:runServer -PmcheliSelfTests=crush,bomb   # a comma list of names
```

The run uses its own game directory, `neoforge-1.21.1/run/selftest`, prepared by `prepareSelfTestRun`
(`selftest.gradle`). It deletes the old world, writes a superflat `server.properties` with no structures, no monster
spawning, port 0 and no watchdog, copies the tracked `eula.txt`, and writes the synthetic content packs when
`contentpack` is selected. Do not set `spawn-animals=false`: it makes the server discard the tests' pigs. Tests run one
at a time, each in its own force-loaded area 384 blocks along +X, and every entity that joined during a test is
discarded afterwards. Each test logs one `[MCHELI-SELFTEST] RESULT: PASS|FAIL <name> (<ticks> ticks) - ...` line, the
run ends with a `SUMMARY` line and `<gamedir>/mcheli-selftest-results.txt`, the server halts, and `runServer` fails the
build on any failure.

| Name | Proves |
|---|---|
| `heli`, `plane` | A piloted vehicle develops lift or thrust and a pilotless twin does not (differential). |
| `tank` | A driven tank cruises straight without climbing, turns on the ground and crests a one-block step. |
| `vtol` | A Harrier in VTOL hovers in place while its conventional twin flies forward; the `canSwitchVtol` gate. |
| `bullet` | A round travels, hits, damages and despawns. |
| `weapon` | The AH-64 loadout and M230 ballistics, a vehicle's own fire path, explosion falloff, damage factors, cluster bomblets (the carrier is gone, exactly its `Bomblet` count of children exist, counted by identity, and at most one has stopped flying); the fire control (the gatling latch, the switch lockout, the recoil trigger, the TV missile handing over to the newest shot). |
| `lockfire` | The real client lock protocol through the real handlers: the audit refuses no-lock, self-lock, unannounced and instant claims; the honest claim launches on the completion tick and the missile homes and detonates; the same protocol on a `Stealth = 1` heli on that clear sight line launches nothing; the synced weapon view at the launch and through a manual reload (the spent rail hidden, then shown again). |
| `homing` | A guided AA missile curves onto an off-axis target and detonates; an unguided control does not. |
| `flare` | Flares spawn and decoy a guided missile at range; an unguided round is untouched. |
| `bomb`, `torpedo`, `cas`, `dispenser` | Bombs inherit the carrier's velocity; the torpedo water run converges to its cruise speed; CAS rains a bombing run on the target; a dispenser round places its item around the impact. |
| `particle` | Weapon visuals come from the configs (muzzle colour, cartridge model and scale, type-driven trails). |
| `hud` | The engine evaluates sample expressions; every HUD a loaded config names draws with zero draw-time failures. |
| `heat` | Barrel heat fills, locks out fire at the cap and cools. |
| `hp` | HP accumulation, per-part armour order, environmental gates, destruction and the wreck countdown; the synced-data id of every accessor, the NBT round trip (a heli, a legacy ammo save, a piloted tank, a wreck) and the spawn-item fuel and damage; the seeded wreck tumble, the one-shot crash detonation, and salvage into the spawn item. |
| `crewfuel` | The seat map, dynamic per-seat weapon eligibility, drive authority, the ammo economy and fuel burn; gunner mode (dedicated and switchable gunner seats); the team, wrench-paint and canopy-less cooldown boarding gates; the camera night vision released on logout; the fuel cans spilled by removal reason, the part switches (the canopy boarding gate, the hatch, the shared switch cooldown, the gear, the weapon bays, the search light) and the forced mounts (a full vehicle, a seat-less extra rider, DismountAll, the Seats restore). |
| `contentpack` | Both pack layouts load; illegal, reserved and duplicate names are skipped; sound grouping and precedence. |
| `crush` | An abandoned tank tilts onto a slope; a driven tank crushes the right blocks only; gunner aim; the rider pose (the turret slew and latch, the seat attachment, the camera eyes, dismount). |
| `recipes` | Every generated recipe loads (at least 98), no UAV recipe, potion and legacy-name ingredients, the fuel refill. |

To add a test, extend `MchSelfTest` and add it to `MchSelfTests.ALL`. The base class supplies the area, a timeout, a
`TestPlayer` that can ride vehicles, and helpers to call payload handlers with a test payload context.

## Build and run

The wrapper is Gradle 9.6.1. Once the Gradle caches are warm, these work with `--offline`.

```
./gradlew :mcheli-agnostic:test                  # agnostic unit tests (JUnit 5)
./gradlew :neoforge-1.21.1:compileJava           # compile the mod
./gradlew :neoforge-1.21.1:runClient             # dev client (run dir neoforge-1.21.1/run)
./gradlew :neoforge-1.21.1:runServer             # dev dedicated server
./gradlew :neoforge-1.21.1:build                 # mod jar in neoforge-1.21.1/build/libs, agnostic jar embedded
```

The quick gate before handing in a change is
`./gradlew :mcheli-agnostic:test :neoforge-1.21.1:compileJava --offline`, plus the self-tests for anything that runs
in game. The 1.7.10 reference is not part of the default build; [its README](mcheli-1.7.10/README.md) explains how to
build and run it.
