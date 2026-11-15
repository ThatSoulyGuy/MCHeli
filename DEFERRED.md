# Deferred and unported features

This file lists what the 1.21.1 port knowingly does not do yet, compared with MCHeli 1.0.3 for Minecraft 1.7.10. Each
entry gives what the feature is in 1.7.10, why it is not ported, and what porting it would take. The last section lists
the gameplay changes the port made compared with its state before the fix campaign (commit `51c5396`).
[ARCHITECTURE.md](ARCHITECTURE.md) describes what does exist.

Reference paths are relative to `mcheli-1.7.10/src/main/java/mcheli/`. Line numbers are in that tree.

## Whole systems not ported

### UAV station and remote flight

- **In 1.7.10:** `uav/` (`MCH_EntityUavStation`, `MCH_ItemUavStation`, `MCH_GuiUavStation`, `MCH_ContainerUavStation`,
  `MCH_UavPacketHandler`, `MCH_UavPacketStatus`). A player at a station, or with the portable controller for small UAVs,
  flies a UAV config (`UAV = true`, `SmallUAV`) remotely through the station's camera. The UAV signal falls off over
  120 blocks (`hud/MCH_HudItem.getUAV_Fs`, :310-321), and a UAV without a hatch can zoom outside gunner mode.
- **In the port:** UAV spawn items are hidden from the creative tabs, refuse placement with the reference message
  (`VehicleSpawnItem.use`) and get no crafting recipe (10 bundled lines, plus e767's, which needs `mcheli:uav_station`).
  The HUD seams `uav_fs` and `UAV_DIST` report "no station" (`MchHudVarState.uavStationDistance`). `Role` has no station
  role, and `AircraftUniqueId` (the station link) is not saved.
- **Why:** it needs a new entity, item, menu and a remote-control path (the operator's input and camera are not on the
  vehicle), which is a feature of its own.
- **To port:** the station entity, item and container; a station role in `Role`; routing the operator's
  `ServerboundControlPayload` and rotation to the linked UAV; the render-view swap to the UAV (the same `CameraMixin`
  path the TV-missile camera needs); the link id in NBT; the signal range; UAV recipes; the UAV always-zoom in
  `MchKeybinds`; the UAV pilot's dismount when a UAV is destroyed, before the ejection seats (`destroyAircraft`,
  `MCH_EntityAircraft:611-613`; the ejection seat's UAV pilot rule in `canEjectSeat` is already ported).

### TV-missile camera

- **In 1.7.10:** while a TV missile (`MCH_EntityTvMissile`, mode 0 of a `TVMissile` weapon) is in flight, the operator
  views through it (`MCH_EntityAircraft.isMissileCameraMode`, the `MCH_ViewEntityDummy` camera at
  `MCH_EntityAircraft:3259-3276`), steers it with his look, and sees the TVM HUD with the `gui/noise.png` overlay.
  `MCH_PacketNotifyTVMissileEntity` tells the clients which entity is the active missile.
- **In the port:** a mode-0 TV missile turns toward the block the operator's sight line meets, or flies along his look
  (`MchBullet` TV guidance). Nobody looks through it. The `TVM_*` HUD values read 0.
- **Why:** it needs a camera-entity swap and a client-side notion of the active missile, which nothing else needed yet.
- **To port:** send the operator's client its active TV missile (the server hook `AbstractMchVehicle.isActiveTvMissile`
  exists; the reference sent the notice every 3 ticks for 9 ticks after the shot). Switch the render view to the
  missile in gunner or camera view through `CameraMixin`/`MchGunnerView`, feed the steering from the operator's look as
  a direction (reference `setMotion(look)`), draw `info.hudTvMissile` with `TVM_POS_X/Y/Z` and `TVM_DIFF`, draw the
  noise overlay, and hide the missile's own trail and model while viewed.

### Multiplay: teams, spotting, markers, scoreboard

- **In 1.7.10:** `multiplay/` (`MCH_Multiplay`, `MCH_MultiplayClient`, the scoreboard and team-creation GUIs,
  `MCH_GuiTargetMarker`, the spot packets, the mod-list and screenshot tools, `MCH_PacketLargeData`) and
  `MCH_MarkEntityPos`. Players spot entities and blocks for their team, markers show through walls, and admins can
  inspect clients. Keys `KeyScoreboard` (L) and `KeyMultiplayManager` (M). Config `DisplayEntityMarker`,
  `EntityMarkerSize`, `BlockMarkerSize`, `DisplayMarkThroughWall` and the `MCH_ServerSettings.enablePVP` mirror.
- **In the port:** none of it. Vanilla teams and PvP still apply, because damage is credited to the operating player
  and a player cannot damage a vehicle whose pilot he may not harm. The IR strobe in night or thermal vision shows
  vanilla teammates.
- **Why:** a large, separate subsystem with its own GUIs and packets. The admin screenshot and mod-list tools should
  probably never be ported.
- **To port:** a team-visible spot list on the server (entity ids and block positions with a timeout), a spot payload
  and a clientbound marker list, a marker GUI layer, and then the targeting pod below. The scoreboard GUI is optional
  given vanilla `/team`.

### Targeting pods

- **In 1.7.10:** `weapon/MCH_WeaponTargetingPod`: `Type = TargetingPod` weapons (10 bundled: the `targeting_pod_*`
  family and `kurganets_spot`) spot what the operator aims at for the team.
- **In the port:** left out of every loadout (`VehicleWeapons.isFireable`). The `AddWeapon` lines naming
  `targeting_pod1` (AH-64) and `targeting_pod_tank` (KV-2, M26) name no defined weapon and are dropped, as in the
  reference.
- **Why and what it takes:** it is a front end to the multiplay spotting system above. Port that first.

### Ground laser target designator (GLTD)

- **In 1.7.10:** `gltd/` (`MCH_EntityGLTD`, `MCH_ItemGLTD`, `MCH_GuiGLTD`, `MCH_ClientGLTDTickHandler`,
  `MCH_PacketGLTDPlayerControl`, renderers). A placeable, rideable designator with its own camera and zoom that calls in
  CAS (`MCH_WeaponCAS`) at the lased point.
- **In the port:** not ported. Its recipe option `ItemRecipe_GLTD` is absent.
- **Why:** a separate rideable entity with its own camera and control packet.
- **To port:** the entity and item, a seat camera through `CameraMixin`, a control payload, and the existing
  `MchBullet.callAirstrike` as the strike.

### Entity racks: carrying vehicles and containers

- **In 1.7.10:** `AddRack` / `RideRack` (9 and 8 bundled configs), `aircraft/MCH_SeatRackInfo`,
  `MCH_IEntityCanRideAircraft` (implemented by aircraft and `container/MCH_EntityContainer`). An aircraft loads a nearby
  vehicle or cargo container onto a rack (`KeyPutToRack`, J) and drops it (`KeyDownFromRack`, U;
  `unmountAircraft`). Config `EnablePutRackInFlying`.
- **In the port:** racks parse, and rack slots are reserved in the seat index space but never boardable. Nothing can be
  carried. `MchContainer` is a placeable storage box only.
- **Why:** it needs one vehicle to carry another, plus its own keys and payloads.
- **To port:** a rack-riding relation (vanilla passengers with a rack seat map would fit the existing seat code), the
  load and unload rules (`canRideAircraft`, distance, in-flight option), two keys and payloads, and rendering the
  carried entity at the rack. In flight the reference dropped a rack's load on a parachute
  (`MCH_EntityAircraft:3990-4011`, the crew drop's `dropEntityParachute`, ported in `MchCrewDrop`).

### Tow chain

- **In 1.7.10:** `chain/` (`MCH_EntityChain`, `MCH_ItemChain`, `MCH_RenderChain`). A chain item links two entities so a
  vehicle can tow another.
- **In the port:** not ported (`ItemRecipe_Chain` is absent).
- **To port:** the chain entity with its two linked ends and pull physics, the item (which refuses to link a
  parachute, `chain/MCH_ItemChain:47`), and the renderer.

### Throwables

- **In 1.7.10:** `throwable/` (`MCH_EntityThrowable`, `MCH_ItemThrowable`, `MCH_ThrowableInfo` and its manager, the
  dispense behaviour, the renderer). The original jar shipped four definitions in `assets/mcheli/throwable/` (the M18
  smoke grenades).
- **In the port:** not ported, and the four definitions are not bundled. `MchSmokeOptions.TO_WHITE` is ready for their
  smoke.
- **To port:** an info manager over `throwable/` in the load order, the item and entity, the dispenser behaviour and the
  smoke.

### Light weapons

- **In 1.7.10:** `lweapon/` (`MCH_ItemLightWeaponBase`, `MCH_ItemLightWeaponBullet`, `MCH_GuiLightWeapon`,
  `MCH_ClientLightWeaponTickHandler`, `MCH_PacketLightWeaponPlayerControl`). Hand-held Stinger and Javelin launchers
  with lock-on. Config `LWeaponAutoFire`, `StingerLockRange`.
- **In the port:** not ported (no items, recipes or config options).
- **To port:** the launcher and missile items, a hand-held lock tracker (the vehicle `MchLockTracker` and
  `MCH_LockAudit` can be reused), a fire payload, and `MchBullet.spawnGuided` for the missile.

### The /mcheli command

- **In 1.7.10:** `command/MCH_Command` with `sendss`, `modlist`, `reconfig`, `title`, `fill`, `status`, `killentity`,
  `removeentity`, `attackentity`, `showboundingbox` and `list`, plus `MCH_GuiTitle` and its packets. A vehicle also
  stores a command (`AcCommand` NBT, `MCH_Command` on the item) that runs when it is destroyed
  (`MCH_EntityAircraft:920-927`). Config `EnableCommand`, `CommandPermission`.
- **In the port:** none of it.
- **To port:** a Brigadier command registered in `RegisterCommandsEvent` with the useful subcommands (`fill`, `status`,
  `title`, `showboundingbox`), then the vehicle command field, its NBT and item data, and the run on destruction.

### Drafting table

- **In 1.7.10:** `block/` (`MCH_DraftingTableBlock`, its tile entity, GUI, container, renderer and packets,
  `MCH_CurrentRecipe`). A crafting station that browses and crafts the vehicle recipes.
- **In the port:** vehicle recipes are ordinary crafting-table recipes (`MchRecipePack`), visible in the recipe book
  and in JEI/EMI. There is no drafting table block.
- **To port:** a block, block entity, menu and screen that list the generated recipes. Not needed for play.

### Range finder

- **In 1.7.10:** `tool/rangefinder/` (`MCH_ItemRangeFinder` and its renderer, `MCH_RecipeReloadRangeFinder`). A
  hand-held zooming range finder that spots for the team. Config `RangeFinderSpotDist`, `RangeFinderSpotTime`,
  `RangeFinderConsume`.
- **In the port:** not ported. Its spotting half depends on the multiplay system.

### A-10 strafing run for CAS

- **In 1.7.10:** `weapon/MCH_WeaponCAS` calls a flying `MCH_EntityA10` (rendered by `MCH_RenderA10`) that strafes the
  target from a compass direction.
- **In the port:** a CAS weapon or a marker rocket's airstrike rains a bombing run on the aim point instead
  (`MchBullet.callAirstrike`): the same ordnance on target without the aircraft.
- **To port:** a scripted flying entity that follows the reference attack path and fires the gun rounds.

### Config GUI beyond NeoForge's screen

- **In 1.7.10:** `gui/MCH_ConfigGui` and its widgets: an in-game options page with sliders and on/off buttons, a key
  binding page (`MCH_GuiListItemKeyBind`) and a TestMode developer page with reload buttons.
- **In the port:** the Mods-menu Config button opens NeoForge's generated `ConfigurationScreen` over the three config
  files, and keys are in vanilla Controls. That covers every ported option. The developer page is part of hot reload.

### Hot reload of definitions

- **In 1.7.10:** the TestMode config page reloads the vehicle, weapon and HUD definitions in place
  (`MCH_ConfigGui:474-516`), tells every client (`MCH_PacketNotifyInfoReloaded`, handled at
  `MCH_AircraftPacketHandler:339-375`) and re-types live entities (`onAcInfoReloaded`).
- **In the port:** F3+T reloads models, textures and GUI sheets (it clears `CompositeResourceSource` and the render
  caches). Definitions load once, at common setup. `MCH_BaseInfo.reload`, `preReload` and `canReloadItem` are kept as
  the agnostic half.
- **To port:** a trigger (a command or a dev key), a clientbound notice, re-parsing on both sides, and rebuilding each
  live vehicle's loadout, parts and hitboxes from the new info.

### Achievements

- **In 1.7.10:** `MCH_Achievement` (welcome, supplyFuel, supplyAmmo, aintWarHell, rideValkyries, reliefSupplies).
- **In the port:** none. They would be advancements with custom triggers fired from the same places.

### TestMode and debug tools

- **In 1.7.10:** `MCH_Config.TestMode` (the debug variable overlay, the `test_mode` HUD value, debug models in
  `debug/`, the developer config page).
- **In the port:** not ported. `test_mode` reads 0. `enableDebugBoundingBox` (F3+B hitboxes) is ported.

### Vehicles riding minecarts

- **In 1.7.10:** `MountMinecartHeli/Plane/Vehicle/Tank` let a vehicle mount a nearby empty minecart
  (`MCH_EntityAircraft:1915`, `canMountWithNearEmptyMinecart` :1981).
- **In the port:** not ported, and the options are absent.

### Content-pack handshake

- **In the port today:** NeoForge's registry sync already refuses a client that lacks a server pack's items or sound
  events, with a message listing the missing `mcheli:` ids. Not detected: packs only the client has, and same-named packs
  with different content.
- **To add:** a configuration task (`RegisterConfigurationTasksEvent` with an `ICustomConfigurationTask`) and a
  configuration-phase payload carrying the server's pack names and a content hash (configs and models). The client
  compares them with `MchContentPacks.mounts()` and disconnects with a translated message.

## Smaller gaps

- **Water wake.** `AddParticleSplash` (12 bundled configs) drives the spray a boat or floatplane throws up when moving
  on water (`plane/MCP_EntityPlane:568`, `tank/MCH_EntityTank:626`). It parses and nothing emits it.
- **Rotor wash on smoke.** The reference pushes smoke particles away from nearby rotors
  (`particles/MCH_EntityParticleSmoke.effectWind`, :79). It needs the client to read the throttle of aircraft within
  15 blocks.
- **Camera distance keys.** `KeyCameraDistanceUp/Down` (Page Up/Down) and `DisableThirdPersonCameraDistChange` adjust the
  third-person distance in the reference (`MCH_ClientCommonTickHandler:132-133`). Not ported.
- **Render options.** `HideKeybind` (on-screen key hints, among them "Eject seat" in an ejection-seat plane,
  `MCP_GuiPlane:85-88`, and "Parachuting / Repelling / Dismount : Y", `MCH_AircraftCommonGui:178-190`; the port shows
  only the jump key's hold progress on the action bar), `EnableModEntityRender` (riders drawn with the vehicle's
  pitch and roll), `MobRenderDistanceWeight` and the hide-held-item behaviour behind `DisableItemRender` are not ported.
- **Key-press sounds.** The reference client played OK/NG click sounds when a key's action was accepted or declined
  (also the NG sound of a passenger's jump key with nothing to do, `MCH_ClientSeatTickHandler:83`). Only the
  weapon-mode key clicks in the port.
- **Fire-rate padding.** The reference client padded each weapon's interval by delay band (+1/+2/+3/+6/+10 ticks,
  `weapon/MCH_WeaponCreator:88-102`, the client copy that gated fire), so the delay players saw was slightly longer
  than the port's server `Delay`. Revisit if exact rate parity matters.
- **Helicopter client throttle.** The reference client interpolated the throttle itself; the port's client reads the
  synced `DATA_SIM_THROTTLE` (`HeliFlightModel` javadoc).
- **Resupply ground check.** `canSupply` (fuel siphon and supply auras) uses a collision-shape probe with floor rounding,
  slightly different from the reference `getBlockIdY(this, 1, -3)`. Moving it to `GroundProbe` needs the `crewfuel`
  self-test's one-block fixture changed.
- **Tank ejection.** The reference tank packet handler ran `ejectSeat` (`tank/MCH_TankPacketHandler:36-37`), but no
  tank client ever sent it and a tank has no parachute slots, so it is not ported: `mcheli:eject_seat` is plane-only.
- **Cargo container physics.** `MchContainer` falls and settles with simplified physics; it does not float.
- **World-view details.** `NeoWorldView.spawnParticle` ignores parameterised particles (block, item, dust), and
  `isAABBInWater` over-reports water for shallow or flowing cells at the box's lower edge (both carry a TODO).
- **Not saved:** `AircraftUniqueId` (no UAV link yet), `AcDismounted`, and the heli rotor angle (cosmetic, client-side).
  The VTOL nozzle state is not saved either, which is faithful (the reference save mask is 24).
- **Unported keys.** `KeyCameraDistanceUp/Down`, `KeyPutToRack` (J), `KeyDownFromRack` (U), `KeyScoreboard` (L) and
  `KeyMultiplayManager` (M), each with its feature above.
- **Unported config options,** each with its feature: `TestMode`, `EnableCommand`, `CommandPermission`,
  `LWeaponAutoFire`, `StingerLockRange`, `MountMinecart*`, `EnablePutRackInFlying`, `RangeFinderSpot*`,
  `RangeFinderConsume`, the marker options, `DisableThirdPersonCameraDistChange`, `HideKeybind`,
  `EnableModEntityRender`, `MobRenderDistanceWeight`, `DisableItemRender`, and the `ItemRecipe_*` options of unported
  items (GLTD, chain, UAV stations, drafting table, range finder, Stinger, Javelin and their missiles).
  Options that are obsolete in 1.21 are dropped for good: the key codes (vanilla Controls), numeric item and block ids,
  log flags, version gating, the GL and texture-manager hacks, the creative-tab icon options, and the fuel, container,
  wrench and parachute recipes (datapack JSON now, `data/mcheli/recipe/`).

## Engineering follow-ups

- **What the `AbstractMchVehicle` split left.** The entity is now a coordinator over 15 components (see
  ARCHITECTURE.md, Entities and registration). The split moved code verbatim, so these were left because each would
  restructure code it promised not to change:
  - `fireSelectedWeapon`'s per-type dispatch (in `MchFireControl`) as an agnostic shot plan (`ShotPlan`), unit-tested
    per weapon type.
  - The part state machine (`MchVehicleParts`) and the seat assignment (`MchVehicleCrew`) as agnostic classes
    (`VehicleParts`, `SeatAssignments`).
  - The subclass wreck tumbles into `VehicleWreck` behind one per-type style (`wreckTumbleStyle`). The plane, tank and
    ground vehicle still override `seedWreckTumble`/`applyWreckTumble`, and the plane's override draws the RNG itself.
  - The hurt order (`MchVehicleHull.handleHurt`) as an agnostic plan.
  - The client interpolation and rotation block still in the entity: the roll axis, the lerp state, the low-pass
    buffer, the pending rider rotation.
  - Retiring the one-line delegates by moving their callers onto the components (about 45 weapon-HUD calls in 10
    files, the crew facts, HP and fuel, the part gates). They were kept so no caller had to change.
- **Observations from the split, not changed** (each may be faithful; check the reference before calling it a bug):
  - A rider restored from `Seats` keeps its entry until it re-boards that vehicle, so the same UUID boarding much later
    by a plain click gets its old seat back instead of the automatic one.
  - `isMissilePartHidden(name)` reads the first slot whose weapon name matches, so a weapon listed in two separate
    `AddWeapon` groups only hides by the first group's reload.
  - A dry weapon inside its switch lockout reports a positive reload fraction (`WeaponSlot.reloadFraction` takes the
    absolute value of the lockout's negative wait).
  - The running-gear field comment calls track side 0 left, while the tick treats side 1 (+X) as the hull's left.
    Check which side the renderer draws for each index.
  - `RecoilKick` treats a negative recoil sequence as a first observe, so a wrapped `DATA_RECOIL_SEQ` (2^31 shots) would
    stop the kick. Unreachable in practice.
  - The weapon-switch queue drains in `HashMap` order, which puts a newer seat that shares a bin first; with the seat
    counts in use the order is ascending.
  - On the server, `searchLightValue` aims with a zero camera-turret look (only the client advances it) and
    `wouldSwitchWeapon` advances a server-only HUD copy of the loadout. Both are client readers; nothing on the server
    depends on them.
  - A dismounted rider is placed once, by vanilla's `dismountTo`. The reference also held it at the unmount point for
    the next 5 ticks (`UnmountReserve`, `MCH_EntityAircraft:1367-1383`, 8 for a rack) and gave it the vehicle's fall
    distance.
  - `MchContainer` saves its 54 slots with vanilla `SimpleContainer.createTag`/`fromTag` (no slot index), so its items
    are packed into the first slots on reload. The reference saved a `Slot` byte per stack (`W_EntityContainer:173`,
    :189); the fuel and parachute slots now do the same with `SlotAC` and the vehicle cargo hold with `Slot`
    (`MchLogistics.save`).
  - Display text still formats numbers with the default locale (the HUD `DrawString` formats, the riding screen), as
    in the reference; config tokens, ids, keys and file names are locale-independent. The `hud` self-test's
    `x%.1f` format check assumes a decimal point, so it would fail on a JVM whose locale writes a decimal comma.
  - Self-test noise: the dispenser torch count (5 to 8) and the `hp` M3 lava damage (×2..9) vary between runs (unseeded
    entity RNG), and an offline run may log one authlib `publickeys` timeout trace.
- **Vehicle update interval.** Vehicle entity types keep vanilla's 3-tick update interval; the reference registered 10
  with velocity updates. Long-range smoothness of fast aircraft rests on client interpolation. The parachute does the
  same (vanilla-style interpolation, no client extrapolation), where the reference sent it every 10 ticks and
  extrapolated between updates (`MCH_EntityParachute:105-122`, :164-187), and so does the rope slider
  (`MCH_EntityHide:126-139`, :199-219).

## Behaviour changes vs the pre-campaign port

What players and server owners will notice compared with the port at `51c5396`. Most of these restore the 1.7.10
behaviour; the deliberate departures from 1.7.10 are marked.

### Combat

- Damage and kills from vehicle weapons are credited to the player who fired, not to the vehicle, so vanilla PvP and
  team friendly-fire rules apply. A player also cannot damage a vehicle whose pilot is a player he may not harm.
- Rounds despawn when the player who fired them dies or logs out. Before, they despawned when the firing vehicle died.
- A vehicle's own rounds and blasts never hurt that vehicle, its crew or the shooter. This departs from 1.7.10, where a
  pilot's rockets could hit his own aircraft at close range.
- Rounds from different vehicles or players can hit each other, so an AA gun can shoot down a missile. Rounds from the
  same vehicle or the same player pass through each other, and so do two rounds that both have no vehicle (or both no
  player) behind them, as in 1.7.10 (its `W_Entity.isEqual` treats two missing entities as equal).
- A hit marker flashes when your round hits something or its blast reaches an entity (client option `hitMarkColor`).
- The server checks every guided-missile lock against what it saw (lock time, seeker cone, range, line of sight). This
  departs from 1.7.10, which trusted the client. A modified client can no longer fire homing missiles at arbitrary
  targets, and seekers with a very short `LockTime` can no longer fire instantly.
- Stealthy vehicles are harder to lock, as in 1.7.10: a target's config `Stealth` (12 bundled aircraft, F-22A and
  F-35 among them) shortens the seeker's range by that fraction, narrows the cone it is acquired in, and lengthens the
  lock time, for its crew as well. The penetrator (`Stealth = 1.0`) cannot be locked at all. Before, stealth was
  ignored.
- Weapons have modes (K): cannon AP/HE, rocket plain/HEIAP, AT missile direct/top attack, TV missile guided/unguided. A
  TV missile now steers toward what its operator aims at.
- Switching weapons reloads a partly used magazine and locks fire for 25 ticks. A mode change locks it for 15.
- Weapon definitions are now fully validated at load, as in 1.7.10. The visible effects on bundled content: the MW-1
  now releases its bomblets (its `BombletSTime` of 0 becomes 1), and the LAU-68 magazine drops from 19 to 8 rounds.
- Smoke weapons trail coloured smoke while selected and the throttle is up. Selected dummy weapons are empty "safe"
  slots.
- Explosions look like the 1.7.10 ones (smoke, burning debris, dust) instead of the earlier approximation. Large
  blasts are heavy on the client, as they were in 1.7.10. A shielded entity inside a blast takes at least 1 damage.
- Airburst fuses trigger on any block in a 3x3 column, grass and still water included. Rockets, missiles and torpedoes
  no longer use the gun proximity fuse. An FAE round that times out makes a normal blast.

### Vehicles and items

- Survival players can break a vehicle that no player rides back into its item with a melee hit. Server options:
  `preventingBroken` disables it, `breakableOnlyPickaxe` requires a pickaxe. In creative the item drops only when
  sneaking (`dropItemInCreativeMode` flips that). Before, only a creative player could reclaim a vehicle.
- A recovered vehicle item keeps the vehicle's fuel and damage (`itemFuel`, `itemDamage`).
- Fuel cans keep their riding-menu slot across a save and reload (before, they moved to the first slot).
- Without a config `UnmountPosition`, a gunner or a crew mob gets off beside its own seat, as in 1.7.10. Before, every
  rider was put down beside the pilot seat.
- Placing a vehicle uses up the item in survival. A vehicle can be placed on a water surface, is refused when the spot
  is obstructed, stands side-on to the player at a cardinal heading, and takes the next paint scheme each time. A
  dispenser can place vehicles. An item whose config did not load does nothing (before, it spawned a fallback vehicle).
- Auto-repair is on by default: 1 % HP every 2 s after 30 s without damage, while HP is at least half
  (`autoRepairHP`). Medevac configs (nh90, an2, us-2) give their crew Regeneration.
- The first right-click on a vehicle with a closed canopy opens the canopy; the next one boards. An open canopy blocks
  flight and gunner mode. Canopies, hatches, folding and swing wings, weapon bays, light hatches and camera turrets
  animate.
- Night vision and thermal vision camera modes (N) on configs with `EnableNightVision`, and search lights on configs
  with `AddSearchLight`.
- Vehicles render much farther away (`renderDistanceWeight`, default 10, about 1000 blocks) and are tracked to 13
  chunks. Projectiles are tracked to 33 chunks and render out to 256 blocks. Both are still capped by the server view
  distance.
- Intact hulls render with the reference 0.75 grey tint, so vehicles look darker than before.
- Saved ammo is keyed by weapon name. Older saves are migrated when the vehicle's loadout is first built.
- A forced mount with no free seat (logging in aboard, `/ride`, a chunk reload) no longer loses the vehicle.
- Boarding obeys vanilla scoreboard teams, as in 1.7.10 (`checkTeam`). A player may not board a vehicle, or click one
  of its seats, while a player on a team he is not on rides it. A player on no team is refused by any teamed crew
  player, and a crew of players without a team never refuses anyone. Seat switches aboard are not gated, and a gunner
  refused this way keeps his seat when he tries to grab the pilot seat (1.7.10 left him on foot).
- The paint scheme cycles only on a sneaking right-click with the wrench, as in 1.7.10. A wrench click never boards.
  Any other sneaking right-click opens the vehicle's cargo hold, as in 1.7.10, and does nothing on a vehicle without
  one. Before, any sneaking right-click cycled the paint.
- Vehicles with `InventorySize` (26 bundled configs, 9 to 54 slots) have their cargo hold back: the vanilla chest
  screen, opened by a sneaking right-click or the riding menu's Inventory button, saved with the vehicle and spilled
  when it is broken, killed or its wreck despawns. Two deliberate departures from 1.7.10: the click that opens it is
  used up (the held item is not used as well), and a stack whose slot a smaller `InventorySize` no longer has (an
  edited config or pack) is kept in the save and comes back when the size does, where 1.7.10 deleted it.
- The parachute is back (`WWW / S S / _W_` of white wool and string, in every MCHeli creative tab). Used in the air it
  opens about 3 blocks above you and lets you down at 6 blocks a second without fall damage; W drifts you forward and
  sneaking lets go. As in 1.7.10 the item is used up on every use outside creative, also on the ground or while
  riding, where nothing opens, and the canopy is solid: it lands on mobs, items and vehicles below it. Three deliberate
  departures from 1.7.10, two of them reference bugs:
  - A parachute drifting without a rider turns to face the way it drifts. In 1.7.10 its heading was mirrored, so one
    drifting north or south turned to face backwards (reference bug, `MCH_EntityParachute:284`).
  - A rising ejection seat's smoke trail drifts along both horizontal axes with half the seat's motion. In 1.7.10 all
    three motion lines were written to the X axis, so the puffs moved along X by the seat's Z motion (reference bug,
    :203-205).
  - The ejection seat's boat landing probes the water with floor rounding over the world's real build height (1.7.10
    cast with `(int)`, off by one at negative coordinates, and stopped at y 0..255), and a parachute that touches water
    is removed by the server alone (1.7.10 also removed the client copy itself; the client now waits a moment for the
    server).
- The 21 ejection-seat planes (`EnableEjectionSeat`) have their parachute slots back in the riding menu (G), under the
  fuel cans: one for a single-seater, two for a two-seater, as in 1.7.10. Each parachute keeps its slot across a save
  and reload, and they spill with the fuel cans when the vehicle is broken or its wreck despawns. Deliberate departure
  from 1.7.10: the slots take only parachutes (1.7.10 took any item and gave a non-parachute back when the menu
  closed).
- The ejection seat is back on those planes. With a parachute in the slots, the pilot or the seat-1 rider holds the
  jump key and is thrown 2 blocks above his seat, rises about 26 blocks on the seat (also from the ground: there is no
  altitude limit, as in 1.7.10) and floats down once it opens; the canopy swings open and stays open, and every part
  switch waits 2 seconds. The pilot takes a mob co-pilot along while a second parachute is left; a player co-pilot
  ejects himself, and with the client option `dismountAll` the mob crew gets off plainly as the pilot ejects. A plane
  that is shot down ejects its pilot and seat 1, mobs included, while parachutes are left; a
  canopy-less B-2A ejects through its hull, as in 1.7.10. Three deliberate departures from 1.7.10, one of them a
  reference bug:
  - The jump key must be held for half a second from a press made in the seat (the action bar shows the hold;
    releasing it cancels; a key already held when boarding does not count), where 1.7.10 ejected on a single press of
    the same key that toggles a helicopter's hover mode.
  - The server checks that the sender sits in seat 0 or 1 of an ejection-seat plane that is not a wreck, the rule the
    client always checked. 1.7.10's server trusted the client and checked only the parachute slots (reference bug: the
    two gates disagreed).
  - Riders who do not eject from a destroyed vehicle are put off plainly, as the port already did, where 1.7.10 kept
    them aboard the burning wreck.
- The pilot's crew-unmount key is back (Unmount Mobs, Y, as in 1.7.10). It puts every mob of the crew off beside its
  own seat; players stay. On the An-2 and the MV-22 (`EnableParachuting`) with nothing within 10 blocks below, it
  drops the mob crew instead, one at a time from seat 2 up on parachutes (every 5 ticks from the An-2, every 10 from
  the MV-22, whose rear hatch swings open first, ignoring the switch cooldown, and must be fully open), from where the
  plane was half a second earlier; Y again, the ground within 10 blocks, nobody left or closing the hatch stops it.
  On a helicopter with repelling hooks it unloads only within 4 blocks of the ground, and higher up lowers the ropes
  (see below). Another rider's Y opens the cargo hold where there is one, as in 1.7.10. Two
  deliberate departures from 1.7.10, both reference bugs:
  - The server checks that the sender is the pilot of a vehicle that is not a wreck, the rule the client always
    checked. 1.7.10's server ran the unload for any rider who sent it (the two gates disagreed).
  - A `MobDropOption` interval of 0 drops every tick. 1.7.10 divided by it and threw an exception every tick of the
    drop (no bundled config has 0).
- A player passenger can parachute out of the An-2 and the MV-22 again (seat 2 and up, as in 1.7.10): he holds the
  jump key and leaves on a parachute from the crew drop's point, with the plane's motion, once nothing is within 13
  blocks below and the MV-22's rear hatch is fully open. No parachute item is needed, as in 1.7.10. Two deliberate
  departures from 1.7.10, one of them a reference bug:
  - The jump key must be held for half a second (the action bar shows the hold), where 1.7.10 jumped on a single press.
  - The client and the server check the same rule. 1.7.10's client ignored the MV-22's hatch (so the request behind a
    shut hatch was silently refused), and its server checked neither the 13 blocks below nor a wreck, trusting the
    client (reference bug: the two gates disagreed).
- Rope repelling is back on the seven hooked helicopters (CH-47, Ka-27, MH-6, MH-60G, Mi-24PS, SH-3, SH-60), as in
  1.7.10. The pilot hovers (or is in gunner mode), holds the helicopter level and still, and presses Y: black ropes
  grow from the hooks, and after 3 seconds each rope lowers one mob of the crew (seat 2 and up) every 1.5 seconds. It
  slides down under its hook with no fall damage and gets off on the ground, at the end of the 50-block rope (then it
  falls), or wherever it is when repelling stops: Y again, a press of the hover key (outside gunner mode, where the
  key does nothing, as in 1.7.10), running out of fuel while hovering, the last player getting off or the
  helicopter's destruction. A player passenger (seat 1 and up) can slide
  down himself once the ropes have been out 2.5 seconds: he holds the jump key, takes the rope after the last one used
  (by a player or a mob) and starts 4 blocks below its hook, 2 lower than a mob, as in 1.7.10. While the ropes are
  down the pilot looks around freely: the mouse no longer turns the helicopter, W, S, A and D do nothing and the
  HUD's FREE LOOK cue lights, as in 1.7.10, until the ropes come in (a press of the free-look key meanwhile leaves
  free look off afterwards, as in 1.7.10). Three deliberate departures from 1.7.10:
  - The jump key must be held for half a second to slide (the action bar shows the hold), where 1.7.10 slid on a
    single press.
  - A rope grows down to the real ground over the whole 1.21 build height. 1.7.10 counted the air below only down to
    y 0, so over ground below y 0 its ropes stopped short, and below y 0 they never grew; it also rounded toward zero
    at negative coordinates.
  - The rope slider updates every 3 ticks with vanilla client interpolation, as the vehicles and the parachute (1.7.10
    sent it every 10 ticks and extrapolated in between), and a client waits for the server to remove a slider that
    lets go (1.7.10 also removed its own copy). The ropes are drawn about a third of a block above the point the
    sliders hang under, because the port lifts the vehicle model off the ground by its lowest point.

### Controls

- The default zoom key moved from C to Z (C is vanilla's save-toolbar key). Outside gunner mode the pilot's Z opens the
  hatch, or folds or sweeps a plane's wing. Players who rebound the key keep their binding.
- New keys: Camera Mode / Search Light (N), Switch Weapon Mode (K), Landing Gear (B), Unmount Mobs (Y, the 1.7.10
  default; see Vehicles and items). The 1.7.10 defaults for the first two (C and X) collide with vanilla keys.
- The jump key toggles a helicopter's hovering mode for its pilot and is the tank brake (held), as SPACE was in 1.7.10.
  Held for half a second in a plane's seat 0 or 1, it ejects, in seat 2 or up of the An-2 or the MV-22 it
  parachutes you out, and in seat 1 or up of a repelling helicopter it slides you down a rope (1.7.10: one press; see
  Vehicles and items).
- The weapon switch key (vanilla swap-offhand, F) no longer also swaps your hands while seated. The mouse wheel switches
  weapons too (client option `switchWeaponWithMouseWheel`, on by default).
- Mouse flight: moving the mouse up now pitches the nose up (it was inverted), at the 1.7.10 stick gain, about 6.7 times
  the earlier gain at the default sensitivity. Steep climbs and dives keep their full vertical input. For the old feel,
  set `invertMouse = true` and `mouseSensitivity` to about 1.5.
- Planes keep their gear down after takeoff until the pilot presses B. The gear comes down by itself when slow near the
  ground, and a floatplane retracts it over water.
- Every manual part or mode switch (canopy, hatch, wing, gear, VTOL) shares one 20-tick cooldown, which also delays
  boarding the pilot seat, on vehicles with or without a canopy (as in 1.7.10).
- Switchable gunner seats (an `AddGunnerSeat` whose seventh value is `true`; 21 bundled configs, including the AH-64,
  Mi-24, Mi-28, AH-1Z, F-15E and CB90 gunner stations) start in gunner mode when boarded, as in 1.7.10: the seat's
  gunner camera, the scope zoom (Z), the seat pitch limits and the HUD `gunner_mode` value. R switches between that
  camera and the normal seat view. Before, these seats never entered gunner mode.
- A player taking seat 1 ends the pilot's gunner mode unless the config enables concurrent gunner mode
  (`onMountPlayerSeat`). Before, the pilot stayed in gunner mode.
- Plane gunner mode uses the 1.7.10 gate (roll and pitch within 40°, throttle over 0.6, nothing within 5 blocks below)
  and frees the pilot's view.

### Flight

- The ground probes now see grass, flowers, snow layers, water and lava, so helicopter ground effect and the plane and
  tank on-ground friction apply over them.
- The near-ground test `solidBlockBelow` (1.7.10 `getBlockIdY(this, 3, -3)`) drives both the wreck tumble and the
  attitude-crash damage of an intact vehicle (`onUpdate_CollisionGroundDamage`), and both now treat water, tall grass,
  flowers and snow layers as ground, as in 1.7.10. An aircraft banked or pitched past 40° within 3 blocks of water or
  a meadow now takes crash damage (a tank only past 91°).
- Helicopters have the 1.7.10 hovering mode (jump key). Switching it levels the pilot's view, and leaving gunner or
  hover mode restores the throttle you had before.
- Tank hulls can tilt past `MinRotationPitch`/`MaxRotationPitch` on slopes (those limits bind only the rider's look).
- A wreck's spin follows its bleeding throttle. Target drones self-destruct when their fuel runs out, and a target
  drone's wreck disappears after 2.5 s instead of 25 s, as in 1.7.10.

### HUD

- Gunners see their own weapon, ammo, reload and heat. Before, gunner seats showed the pilot's weapon.
- The low-fuel warning blinks. The altitude readout follows the 1.7.10 block scan (height above water over the sea).
  The reload percentage counts the right way, and ammo is padded to four digits.
- Helicopter pilot HUDs show the camera-direction box, as in 1.7.10.
- The stick dot on the pilot HUDs (`stick_x`, `stick_y`) uses the 1.7.10 sign and four-frame smoothing: it moves up
  when the mouse moves up.
- A HUD file with an unknown variable or a bad expression no longer shows silent zeros: the item is skipped and the
  problem logged once. Every bundled HUD is clean.

### For server owners

- Configuration is three files: `config/mcheli-common.toml` (speed multipliers, restart needed),
  `<world>/serverconfig/mcheli-server.toml` (gameplay, synced to clients) and `config/mcheli-client.toml` (per
  player).
- Content packs under `<gamedir>/mcheli/` must be installed on the server and every client; a client missing one is
  refused at login with the list of missing ids.
- The server and its clients must run the same MCHeli build: the network protocol is now version 2 (the riding menu
  carries the parachute slots), so a client or server from before this change is refused at login.
- UAV configs cannot be placed or crafted until the UAV station is ported.
- Weapon packs parse the same under any system locale. Before, a server on a Turkish locale misread weapon types and
  names spelled with an upper-case `I` (`AAMISSILE`, `DamageFactor = HELICOPTER`).
- Blast block damage obeys both `mobGriefing` and `explosionDestroyBlock`. Vehicle block crushing obeys `mobGriefing`
  and `collisionDestroyBlock`.
