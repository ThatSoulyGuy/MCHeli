# mcheli-1.7.10 — compiling & runnable reference workspace

Deobfuscated, buildable, **runnable** reference of MCHeli 1.0.3 (MC 1.7.10), used as
ground truth for the 1.21.1 port. Original author: Minato Tanaka (EMB4).

## Status in this repository
This module is **not part of the default build**: `include 'mcheli-1.7.10'` is commented out
in the root `settings.gradle`. That happened in ab00fd7, the same commit that moved the
repository's Gradle wrapper from 8.8 to 9.6.1. This workspace (RetroFuturaGradle 1.4.9) was
only ever set up here under Gradle 8.8: at 61f47c6, the commit before, all three modules were
included with the 8.8 wrapper. Whether RFG 1.4.9 works under Gradle 9.6.1 has not been checked.

The sources in `src/main/java` are still useful without building them: the port cites them
(`reference MCH_EntityHeli:349-351` means a line in this tree).

## Re-enabling and running it
1. **Restore the original jar.** Its runtime content is extracted from
   `MC1.7.10_mcheli_1.0.3.zip` (75 MB) at the repository root, which is not in the working
   tree. It was committed in ad27026 and removed in 0be2e5a, so it is still in git history:
   ```
   git show ad27026:MC1.7.10_mcheli_1.0.3.zip > MC1.7.10_mcheli_1.0.3.zip
   ```
   Or point the build at a copy elsewhere with `-PmcheliJar=/path/to/the.zip`. The zip is not
   gitignored, so take care not to commit it again. Without it, `compileJava` still works but
   `runClient`/`runServer` load no vehicles, weapons or sounds.
2. **Include the module**: uncomment `include 'mcheli-1.7.10'` in `settings.gradle`.
3. **Use Gradle 8.8 if 9.6.1 fails.** Either switch the wrapper for the session
   (`distributionUrl=.../gradle-8.8-bin.zip` in `gradle/wrapper/gradle-wrapper.properties`,
   as before ab00fd7) or run an installed Gradle 8.8 instead of `./gradlew`. Revert both
   changes before committing.
4. Build and run from the repository root. Gradle itself runs on a modern JDK (17–21); the
   1.7.10 code compiles under a Java 8 Azul toolchain that Gradle provisions (foojay):
   ```
   ./gradlew :mcheli-1.7.10:compileJava   # compile the reference
   ./gradlew :mcheli-1.7.10:runClient     # launch the mod with full content (GUI)
   ./gradlew :mcheli-1.7.10:runServer     # headless server
   ```

**MCHeli is a "folder mod."** It reads ALL of its content via
`new File(sourcePath + "/assets/mcheli/…")`, where `sourcePath` is its mod-container
source, and `MCH_FileSearch` throws unless that path is a real **directory**. So the
build runs the mod from an exploded `build/folder-mod/` directory (compiled classes +
assets + `mcmod.info` merged), never a packaged jar, and drops the `-dev.jar` from the
run classpath. `extractModResources` pulls the assets (112 vehicles, 229 weapons, 4
throwables, HUDs, models, textures, sounds, lang) out of the original jar at build time
into `build/` (gitignored), so this workspace never duplicates them into git.

A correct run logs, from PreInit:
`Read 229 weapons / Read 33 helicopters / Read 51 planes / … / Update sounds.json. 66 sounds`.

## Where the content lives for the port
The 1.21.1 port does **not** read the jar. It keeps its own committed copy of the content
under `neoforge-1.21.1/src/main/resources/assets/mcheli/` (vehicle, weapon and HUD configs,
models, textures, sounds; added in 0ea34d7, baf163c, 95b7734 and abfbb11) and loads it through
the resource system instead of `sourcePath/assets`. The port adds its own lang file,
`sounds.json`, item models and shaders. It does not carry over the jar's four `throwable/`
definitions or its `Readme_*.txt` and `MCHeli_ParameterList.pdf` documents. The repository
records no licence for this content (the port's `neoforge.mods.toml` says "All Rights
Reserved"), so confirm redistribution rights before publishing it.

## How `src/main/java` was produced (reproducible)
1. Normalized the distributed jar so class entry paths match packages (`mcheli/…`).
2. **Vineflower 1.12.0** decompiled the SRG-named classes → 474 `.java`.
3. Source-level **searge→MCP rename** using the MCP conf **bundled inside Forge**
   (`…/forge/1.7.10-10.13.4.1614-1.7.10/unpacked/conf/{methods,fields,params}.csv`) —
   the exact mappings RFG applies to Minecraft, so names match by construction (~11,013
   identifiers renamed; unmapped members stay searge on both sides, so they still resolve).
4. 13 Vineflower decompiler artifacts hand-fixed (lost-generic casts / duplicate
   switch-scope locals) to make `compileJava` pass.
5. A reflection string-literal corruption in `W_Reflection.java` (side effect of the
   text-level rename) was caught by an adversarial review and fixed.
6. An exhaustive body-equivalence check (recompile → re-decompile → diff all 474 files:
   398 identical, 76 triaged) found one Vineflower infidelity: `JavaExOperator.nn(double,…)`
   wrote the original's `Long`/`Double` result as a ternary, which JLS 15.25 numeric
   promotion collapses to always-`Double`. Cast the `Long` branch to `Object` so it returns
   `Long` when integral, matching the original bytecode.

Steps 4 and 5 were done before this repository's history begins: the whole workspace
arrived already fixed in 6c83972, so there is no isolated diff here, and the commit id the
original notes gave for step 5 (e8c481d) does not exist in this repository.

### Known decompiler artifact still in this tree
The body-equivalence check in step 6 missed one more Vineflower artifact. In
`src/main/java/mcheli/eval/util/NumberUtil.java`, `parseLongBin`, `parseLongOct` and
`parseLongDec` fold the `'0'` digit into the `default: throw` arm
(`case '0': default: throw new NumberFormatException(...)`). The original bytecode, and the
independent CFR decompile in `reference/decompiled-srg/mcheli/eval/util/NumberUtil.java`,
accept the digit. So this runnable reference rejects literals such as `0b10`, `0o70` and
`10L` in eval expressions, which the original jar parses. The port's copy
(`mcheli-agnostic/.../eval/util/NumberUtil.java`) is fixed. When you compare HUD or eval
behaviour against this workspace, keep this in mind.

## Toolchain
RetroFuturaGradle 1.4.9 · Gradle 8.8 (the repository wrapper is now 9.6.1; see above) ·
Java 8 (Azul via foojay) · Forge 10.13.4.1614 · MCP mappings stable_12 (Forge-bundled conf).
