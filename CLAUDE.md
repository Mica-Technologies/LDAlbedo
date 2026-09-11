# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Git Guidelines

- **Create commits** when work reaches a logical checkpoint — keep them descriptive and well-organized.
- **Never push** to any remote. The user will review and push manually.
- Use conventional, descriptive commit messages that explain *why*, not just *what*.
- Group related changes into single commits; don't lump unrelated work together.

## Build Commands

```bash
# Setup workspace (required first time, or after clean)
./gradlew setupDecompWorkspace

# Build the mod
./gradlew build

# Run Minecraft client in dev
./gradlew runClient

# Run Minecraft server in dev
./gradlew runServer

# Clean build artifacts
./gradlew clean
```

**Requirements:** Java 17 (Azul Zulu Community recommended). The build system uses RetroFuturaGradle targeting JVM 8. Heap is set to `-Xmx3G` in `gradle.properties` for decompilation.

**JDK Location:** The JDK is managed via IntelliJ and located at `C:\Users\<username>\.jdks\azul-17.0.x`. When running Gradle from the CLI, set `JAVA_HOME` to this path:
```bash
JAVA_HOME="C:/Users/<username>/.jdks/azul-17.0.19" ./gradlew build
```

## Architecture Overview

This is **LDAlbedo**, Mica-Technologies' fork of [Albedo](https://github.com/MysticMods/Albedo) (originally by Elucent / Horizon Studio), a **Minecraft 1.12.2 Forge mod** (mod ID: `albedo`) providing a shader-based dynamic lighting API that other mods build colored/animated light sources on top of (e.g. AlbedoTorches, WeissAlbedo, ColoredLights). The Java package is `elucent.albedo` — this is a **public compile-time API surface** other mods link against directly, so it is never renamed even under the Mica-Technologies fork identity. The build system is GregTechCEu Buildscripts (RetroFuturaGradle wrapper).

### Project history — why this matters here

Upstream released version 1.1.0 in parallel for both 1.12.2 and 1.13.2 in May 2019. The `MysticMods/Albedo` git history that this repo was forked from only ever contained the **1.13.2** side of that release (package `com.hrznstudio.albedo`) — the 1.12.2 source was never merged into this history and doesn't exist in any branch or tag here. Since 1.13.2 is an effectively-dead Forge target and the entire Mica-Technologies ecosystem (and every mod that actually depends on Albedo) is 1.12.2, this fork's source was recovered by decompiling the real shipped `albedo-1.12.2-1.1.0.jar` and cleaning up the result — not translated from the 1.13.2 code. A reference copy of that decompiled jar lives in the gitignored `example-source/` directory for future work.

### Source Layout

```
src/main/java/elucent/albedo/
├── Albedo.java              # @Mod entry point (loads on both sides) — capability registration, block light-handler registry
├── AlbedoClient.java        # Client-only startup (shader reload listener, EventManager), called behind a side check
├── ConfigManager.java        # Forge Configuration wiring
├── EventManager.java         # Central render-pass state machine — reacts to profiler section
│                              #   changes (via the ASM-injected ProfilerStartEvent) to decide
│                              #   which shader is active
│
├── asm/                       # Coremod (loaded via FMLCorePlugin, not a mod class)
│   ├── FMLPlugin.java         #   IFMLLoadingPlugin entry point
│   ├── AlbedoCore.java        #   DummyModContainer wrapping the coremod as a "mod" for FML
│   └── ASMTransformer.java    #   IClassTransformer — bytecode-patches 6 vanilla/Forge classes
│                              #   to inject static hook calls (see Key Subsystems below)
│
├── event/                     # POJO events posted by the ASM hooks and consumed by EventManager
│   ├── ProfilerStartEvent.java
│   ├── RenderEntityEvent.java
│   ├── RenderTileEntityEvent.java
│   ├── RenderChunkUniformsEvent.java
│   ├── LightUniformEvent.java
│   └── GatherLightsEvent.java
│
├── lighting/                  # Light data model and per-frame light collection
│   ├── Light.java              #   Light POJO (+ Builder)
│   ├── LightManager.java       #   Collects/culls lights each frame, pushes uniforms
│   ├── ILightProvider.java     #   Forge capability interface — attach custom lights to entities/TEs
│   └── DefaultLightProvider.java
│
└── util/
    ├── RenderUtil.java         #   Bridge called from the ASM-injected hook points
    ├── ShaderManager.java      #   GLSL program compile/link/uniform management
    ├── ShaderUtil.java         #   Resource-reload-driven shader (re)loading
    └── TriConsumer.java        #   Version-agnostic functional interface
```

### Key Subsystems

| Subsystem | Purpose | Key Classes |
|---|---|---|
| **ASM Coremod** | Patches 6 vanilla/Forge classes at class-load time to inject hook calls Albedo needs but Forge doesn't expose as events | `asm/ASMTransformer.java` — targets `ChunkRenderContainer#preRenderChunk`, `RenderManager#renderEntity`, `TileEntityRendererDispatcher#render`, `GlStateManager#enableLighting`/`disableLighting`, `Profiler#endStartSection`, `ForgeHooksClient#handleCameraTransforms`. See [`docs/COREMOD.md`](docs/COREMOD.md) |
| **Render State Machine** | Decides which shader is active based on which vanilla render pass (profiler section) is currently running | `EventManager.java`, driven by `ProfilerStartEvent` |
| **Light Collection** | Gathers lights from block/entity/TE providers each frame, culls, and uploads to the active shader | `lighting/LightManager.java`, `event/GatherLightsEvent.java` |
| **Capability API** | Lets other mods attach custom light-emission logic to entities/tile entities | `lighting/ILightProvider.java`, `Albedo.LIGHT_PROVIDER_CAPABILITY` |
| **Block Light Registry** | Static registry so blocks can declare light-emission behavior without a capability | `Albedo.registerBlockHandler`/`getLightHandler` |
| **Shaders** | GLSL for terrain (`fastlight`) and entities (`entitylight`); `depth` is compiled but never bound | `util/ShaderManager.java`, `assets/albedo/shaders/*.{vs,fs}`. See [`docs/SHADERS.md`](docs/SHADERS.md) |

### Entry Points

1. **Mod Entry:** `Albedo.java` — `@Mod` annotated class, loaded on **both** sides (`acceptableRemoteVersions = "*"`, no longer `clientSideOnly` as upstream shipped it) so mods that integrate with Albedo can hard-depend on it from a server. Registers the `ILightProvider` capability in `preinit` on both sides; in `loadComplete` it calls `AlbedoClient.init()` on the client only, which adds the resource-reload listener and `EventManager`. `Albedo` itself must never reference a client class.
2. **Coremod Entry:** `asm/FMLPlugin.java` — loaded via `META-INF/MANIFEST.MF`'s `FMLCorePlugin` entry (set by the `coreModClass` buildscript property, not hand-written), installs `ASMTransformer`.
3. **Render Hook Bridge:** `util/RenderUtil.java` — the static methods the ASM-injected calls actually invoke.

### Dependencies

None beyond vanilla Forge 1.12.2 — Albedo has no external library dependencies.

### Version

Version is derived from Git tags. No manual version setting needed (see `modVersion` in `buildscript.properties`).

## Resources

```
src/main/resources/
├── mcmod.info              # Forge mod metadata (token-substituted by processResources)
├── pack.mcmeta              # Resource pack manifest
└── assets/albedo/shaders/   # GLSL: depth.{vs,fs}, entitylight.{vs,fs}, fastlight.{vs,fs}
```

## Local-only reference material

`example-source/` (gitignored) holds the decompiled reference for the actual
shipped 1.12.2 jar, kept around for verifying ports/fixes against the real
original behavior. Never commit it.

## In-depth system documentation

`docs/` holds a document per subsystem. Start at [`docs/README.md`](docs/README.md),
which indexes them and carries an architecture diagram.

- [`docs/API.md`](docs/API.md) — the public API, for mods adding lights
- [`docs/RENDER_PIPELINE.md`](docs/RENDER_PIPELINE.md) — the profiler-driven render state machine
- [`docs/LIGHT_COLLECTION.md`](docs/LIGHT_COLLECTION.md) — light sources, culling, occlusion
- [`docs/SHADERS.md`](docs/SHADERS.md) — GLSL, uniforms, camera-relative coordinates
- [`docs/COREMOD.md`](docs/COREMOD.md) — the ASM patches
- [`docs/CONFIGURATION.md`](docs/CONFIGURATION.md) — config options

`docs/agent-plans/` (gitignored) holds phased implementation plans for larger
pieces of work, including the current plan for the upstream issue backlog.
