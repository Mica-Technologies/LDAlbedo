# The render pipeline

`EventManager` is the heart of the mod. It is a state machine driven by vanilla's
own profiler section names, and it decides — for every pass of every frame —
which shader is bound and what it has been told.

## Why the profiler

Minecraft's renderer calls `Profiler#endStartSection("terrain")`,
`("entities")`, `("gui")` and so on as it works through a frame. Those calls
already describe the frame in order, for free. Albedo patches
`endStartSection` (see [COREMOD.md](COREMOD.md)) and treats each name as "this
pass is starting now".

The consequence worth internalising: **Albedo's behaviour is coupled to vanilla
profiler section names.** Rename or reorder one and the corresponding Albedo
behaviour silently stops. This is also why other mods that render inside an
existing section can surprise it.

## The sections Albedo acts on

Handled in `EventManager#onProfilerChange`, in the order vanilla emits them:

| Section | What Albedo does |
| --- | --- |
| `terrain` | The big one. Binds **fastlight**, uploads tick/sampler/lightmap/player uniforms, and — once per frame — collects, uploads and clears the light list (below). |
| `sky` | Unbinds. The sky must not be lit. |
| `litParticles` | Binds fastlight with a zero chunk offset, because particle vertices already arrive relative to the camera. |
| `particles`, `weather` | Unbind. |
| `entities` | Binds **entitylight**, enables its lighting, and sets `fogIntensity` (1, or 1/64 in the Nether). |
| `blockEntities` | Binds entitylight with lighting enabled. |
| `outline`, `aboveClouds`, `destroyProgress` | Unbind. |
| `translucent` | Rebinds fastlight with its sampler/lightmap/player uniforms. |
| `hand` | Binds entitylight for the held item. |
| `gui` | Sets `isGui = true` and unbinds, so the HUD is never shaded. |

`isGui` matters beyond the GUI pass: `RenderUtil` checks it before reacting to
`GlStateManager` lighting toggles, so vanilla's UI drawing cannot disturb
Albedo's state.

## The once-per-frame light upload

Inside the `terrain` branch, guarded by `postedLights` so it happens exactly once
per frame:

1. Copy every cached block light from `EXISTING` into `LightManager.lights`.
2. `LightManager.update(world)` — gather entity/tile-entity/event lights, sort
   nearest-first, and optionally cull occluded ones.
3. `stopShader()`, post `LightUniformEvent` (other mods' hook), rebind.
4. `uploadLights()` into fastlight, then again into entitylight — both programs
   need the same light set.
5. `LightManager.clear()`.

`postedLights` is reset in `onRenderWorldLast`, which also force-unbinds so no
shader leaks out of the world render into anything else.

## Per-object hooks

- `onRenderEntity` — sets `entityPos` for the entity about to draw, so
  entitylight can light it relative to its own origin. Lightning bolts get the
  shader dropped entirely; they are pure emissive geometry and shading them
  looks wrong.
- `onRenderTileEntity` — the same for block entities, with end portals and end
  gateways excluded for the same reason.
- `onRenderChunk` — sets the chunk offset for the terrain chunk about to draw.

## Shader binding rules

All binding goes through `ShaderManager`, which:

- asks OpenGL what is *actually* bound before binding or unbinding, rather than
  trusting a cached value;
- knows which programs are Albedo's own, and **stands down** rather than binding
  over a foreign one (BetterPortals rendering portals into the world is the
  motivating case);
- refuses to unbind a program that is not Albedo's.

Uniform writes still use the cached value — there are hundreds per frame and
nothing can intervene between them and the bind that set it. See
[SHADERS.md](SHADERS.md) for the uniform list and
[CONFIGURATION.md](CONFIGURATION.md) for the `ignoreForeignShaders` escape hatch.

## Debugging a frame

The fastest way to find out whether a shader is bound and running at all is to
make it output a constant. In `fastlight.fs`:

```glsl
gl_FragColor = vec4(1.0, 0.0, 0.0, 1.0);   // terrain turns red if bound
```

To ask a question about the light maths instead, encode answers into colour
channels from the vertex shader — for example red for "lightCount > 0", green
for computed intensity — and output `lcolor` directly. That technique found the
real cause of two separate bugs faster than reading the code did.
