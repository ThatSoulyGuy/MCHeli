# MCHeli 1.21.1 Port — Architecture

Two **compiler-enforced** layers:

- **`mcheli-agnostic`** — pure Java, **zero** Minecraft/NeoForge/LWJGL on the classpath. All
  platform-neutral logic: the `eval` expression engine, flight/geometry math, config parsing,
  vehicle/weapon definitions, ballistics/guidance logic, HUD DSL logic, plus the **SPI**
  (`mcheli.agnostic.spi`) — interfaces the dependent layer implements. Any accidental platform
  import here fails to compile. Unit-testable without spinning up Minecraft.
- **`neoforge-1.21.1`** — the dependent layer: entities, items, blocks, rendering, networking,
  registration, screens. Depends on `mcheli-agnostic` and implements its SPI against NeoForge 1.21.1.

Classification of the 1.7.10 reference (473 files): **123 agnostic · 88 coercible · 262 hard-dependent.**
Coercible logic moves into agnostic behind ports; hard-dependent is rewritten natively.

## Package layout (`mcheli.agnostic.*`)
`eval` · `math` · `value` · `util` · `config` · `physics` · `info` · `weapon` · `hud` · `spi`

## SPI (ports the dependent layer implements)
Value types: `Vec3d` `AABB` `RayHit` `BlockPos` `Direction` `ItemHandle` `ModelHandle`.
Ports: `WorldView` · `EntityRef` (+ `SyncedData`, `Role`) · `Registrar` · `ResourceSource` ·
`RandomSource` · `Logger` · `EntityFactory` · `HudRenderer` · `Network` · `ClientContext`.

## Risk decisions (locked up front)
1. **`Vec3d` is IMMUTABLE** (a `record`). MC mutates `motionX/Y/Z` in place; agnostic never shares a
   mutable vector — it reads motion as a `Vec3d` snapshot and writes a new one via
   `EntityRef.setMotion(Vec3d)`. Mutation happens at the port boundary, not on a shared object.
2. **`EntityHandle` is decomposed, not a god-interface.** `EntityRef` (identity, pos/motion/rotation
   reads + minimal writes, mount/rider), `SyncedData` (DataWatcher slot get/set), and a `Role` enum
   for classification. **No `Class` literals in agnostic** — `instanceof MCH_EntityHeli` becomes
   `ref.role() == Role.HELI`, with the dependent adapter tagging each entity's role. Entity-keyed
   sound moves to `WorldView`.
3. **`EntityFactory` breaks the apparent circularity.** A projectile = an agnostic logic core
   (flight/homing/impact over ports) + a thin dependent `Entity` shell that holds the logic and
   delegates `tick()`. `EntityFactory.spawn(spec)` hides the concrete constructor — no
   agnostic→dependent compile edge. The one-weapon vertical slice validates this first.
4. **`HudRenderer` stays a high-level 2D canvas** (text/rect/line/textured-quad + matrix + screen
   dims); it never exposes raw GL. GL-heavy items (`MCH_HudItemGraduation`, `MCH_HudItemTexture`,
   camera effects) are rewritten native, not coerced.
5. **Single-consumer ports dropped in favor of native rewrite / enum inputs.** No `NbtIo` port (NBT is
   intrinsically MC persistence → native). No `DamageClassifier` port — the pure Class-keyed factor
   table stays agnostic keyed by `Role`/tag, and the dependent layer passes a `DamageCategory` enum.
   Trivial value types (`BlockPos`, `Direction`) are plain data, not ports.
6. **The 8 zero-import wrapper stubs are NOT agnostic logic.** `W_MOD`, `W_Version`, `W_ItemList`,
   `W_ResourcePath`, `ChatMessageComponent`, `IPacketHandler`, `ITickHandler`, `NetworkMod` are
   platform/version glue or dead 1.6/1.7 Forge interfaces — rewritten or deleted in the dependent
   layer, never relocated as agnostic code.
7. **`MCH_Lib` split correctness is compiler-verified.** Only verified-pure helpers move to agnostic
   `MchMath`/`MchUtil`; the no-MC-classpath module rejects any that secretly touch the platform.

## Build order
0. Split `MCH_Lib`; stand up the no-MC module. (done: eval + math/util/config islands compile)
1. Fill self-contained islands (eval, math). — done
2. Value types + core SPI interfaces.
3. Coerce the data/loader layer (`*Info`/`*InfoManager`).
4. Coerce weapon/ballistics logic (+ `EntityFactory`/`Network`).
5. Coerce HUD DSL onto `HudRenderer`.
6. Dependent layer: implement all ports natively against NeoForge 1.21.1.
+ **Vertical slice** (one weapon end-to-end) validates the ports before scaling.
