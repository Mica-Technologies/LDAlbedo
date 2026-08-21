# LDAlbedo documentation

Albedo is a **cosmetic** dynamic lighting library for Minecraft Forge 1.12.2. It
adds no lights of its own; it gives other mods an API for coloured, dynamic,
per-block and per-entity light, and renders it with its own GLSL shaders.

The word *cosmetic* is load-bearing. Albedo never touches Minecraft's lighting
engine, never changes block light levels, and by default never checks whether
anything is in the way. That is why it is cheap, and why light passes through
walls unless you opt in (see [Configuration](CONFIGURATION.md)).

## Where to start

| If you want to… | Read |
| --- | --- |
| Add lights from your own mod | **[API.md](API.md)** |
| Change how Albedo behaves | [CONFIGURATION.md](CONFIGURATION.md) |
| Understand how a frame is drawn | [RENDER_PIPELINE.md](RENDER_PIPELINE.md) |
| Understand where lights come from | [LIGHT_COLLECTION.md](LIGHT_COLLECTION.md) |
| Work on the GLSL | [SHADERS.md](SHADERS.md) |
| Work on the bytecode patches | [COREMOD.md](COREMOD.md) |

For build commands and repo layout see [`../CLAUDE.md`](../CLAUDE.md).

## How the pieces fit

```
                    ┌──────────────────────────────────────────┐
                    │ COREMOD (asm/)                           │
                    │ patches 6 vanilla/Forge classes at load  │
                    └───────────────┬──────────────────────────┘
                                    │ injected static calls
                                    ▼
   Profiler#endStartSection ──▶ ProfilerStartEvent ──▶ ┌───────────────┐
   RenderManager#renderEntity ─▶ RenderEntityEvent ──▶ │ EventManager  │
   TERDispatcher#render ──────▶ RenderTileEntityEvent ▶│  (the render  │
   ChunkRenderContainer ──────▶ RenderUtil ───────────▶│ state machine)│
   GlStateManager#en/disable ─▶ RenderUtil ───────────▶└───────┬───────┘
                                                               │ binds + feeds
                                                               ▼
   block handlers ─┐                              ┌────────────────────┐
   ILightProvider  ├─▶ LightManager.lights ──────▶│ ShaderManager      │
   GatherLightsEvent┘   (cull, sort, occlude)     │ fastlight/entity   │
                                                  └────────────────────┘
```

Two things drive everything:

1. **The coremod** turns vanilla render events Forge does not expose into
   Albedo events. Without it nothing happens at all.
2. **`EventManager`** listens to those events and decides, for each pass of the
   frame, which shader is bound and what it knows about the lights nearby.

## Vestigial code

Documented so nobody spends an afternoon working out what these are for. They
are currently inert:

- **`ShaderUtil.depthProgram`** and `assets/albedo/shaders/depth.{vs,fs}` — the
  program is compiled at resource load and then never bound by anything.
- **`RenderUtil.itemTransformType`** / `RenderUtil.setTransform` — populated by
  the `ForgeHooksClient#handleCameraTransforms` patch, but nothing ever reads
  the field, so that patch currently feeds nothing.
- **`RenderUtil.lightingEnabled`** — never read. (Not to be confused with the
  shader uniform of the same name, which *is* used.)
- **`ASMTransformer.patchRenderItemASM`** — never called from `transform`, and
  its body does nothing but round-trip the class.

`LightUniformEvent` looks similar but is **not** dead: Albedo posts it and never
consumes it because it exists for other mods (see [API.md](API.md)).
