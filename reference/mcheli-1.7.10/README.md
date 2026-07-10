# mcheli-1.7.10 — compiling & runnable reference workspace

Deobfuscated, buildable, **runnable** reference of MCHeli 1.0.3 (MC 1.7.10), used as
ground truth for the 1.21.1 port. Original author: Minato Tanaka (EMB4).

## Build & run
Gradle launches on a modern JDK (17–21); the 1.7.10 code compiles under a Java 8 Azul
toolchain that Gradle auto-provisions (foojay). Example with JDK 21:

```
JAVA_HOME=<jdk21> ./gradlew compileJava   # compile all 541 classes
JAVA_HOME=<jdk21> ./gradlew runClient     # launch the mod with full content (GUI)
JAVA_HOME=<jdk21> ./gradlew runServer     # headless server
```

**MCHeli is a "folder mod."** It reads ALL of its content via
`new File(sourcePath + "/assets/mcheli/…")`, where `sourcePath` is its mod-container
source, and `MCH_FileSearch` throws unless that path is a real **directory**. So the
build runs the mod from an exploded `build/folder-mod/` directory (compiled classes +
assets + `mcmod.info` merged), never a packaged jar, and drops the `-dev.jar` from the
run classpath. `extractModResources` pulls the assets (116 vehicles, 229 weapons,
models, textures, sounds, lang) out of the original jar at the repo root at build time,
so they are never duplicated into git (`build/` is gitignored).

A correct run logs, from PreInit:
`Read 229 weapons / Read 33 helicopters / Read 51 planes / … / Update sounds.json. 66 sounds`.

> Note for the port: this disk-based `File` loader is a major 1.21.1 item — the modern
> version must load content through the resource system, not `sourcePath/assets`.

## How `src/main/java` was produced (reproducible)
1. Normalized the distributed jar so class entry paths match packages (`mcheli/…`).
2. **Vineflower 1.12.0** decompiled the SRG-named classes → 474 `.java`.
3. Source-level **searge→MCP rename** using the MCP conf **bundled inside Forge**
   (`…/forge/1.7.10-10.13.4.1614-1.7.10/unpacked/conf/{methods,fields,params}.csv`) —
   the exact mappings RFG applies to Minecraft, so names match by construction (~11,013
   identifiers renamed; unmapped members stay searge on both sides, so they still resolve).
4. 13 Vineflower decompiler artifacts hand-fixed (lost-generic casts / duplicate
   switch-scope locals) to make `compileJava` pass — see commit history for the isolated diff.
5. A reflection string-literal corruption in `W_Reflection.java` (side effect of the
   text-level rename) was caught by an adversarial review and fixed (commit e8c481d).
6. An exhaustive body-equivalence check (recompile → re-decompile → diff all 474 files:
   398 identical, 76 triaged) found one Vineflower infidelity: `JavaExOperator.nn(double,…)`
   wrote the original's `Long`/`Double` result as a ternary, which JLS 15.25 numeric
   promotion collapses to always-`Double`. Cast the `Long` branch to `Object` so it returns
   `Long` when integral, matching the original bytecode.

## Toolchain
RetroFuturaGradle 1.4.9 · Gradle 8.8 wrapper · Java 8 (Azul via foojay) ·
Forge 10.13.4.1614 · MCP mappings stable_12 (Forge-bundled conf).
