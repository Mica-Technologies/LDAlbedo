# Light collection

How a light gets from a mod's code into the shader, each frame.

For the API side of this, see [API.md](API.md).

## The three sources

| Source | Collected by | When |
| --- | --- | --- |
| Block handlers registered via `Albedo.registerBlockHandler` | `EventManager#scanBlockLights`, cached in `EventManager.EXISTING` | Continuously, budgeted across ticks |
| `ILightProvider` on entities, tile entities, held items, armour | `LightManager#update` | Once per frame |
| `GatherLightsEvent` subscribers and `LightManager.addLight` | `LightManager#update` | Once per frame |

Block lights are cached because scanning is expensive; the other two are cheap
enough to redo every frame.

## The block scan

Blocks have no "tell me when you are near the player" hook, so Albedo sweeps the
volume around the player looking for registered blocks.

- Runs on the **client thread**, from `clientTick`. It used to run on its own
  background thread calling `World#getBlockState`, which raced chunk load and
  unload and could take the game down when a chunk vanished mid-scan.
- **Budgeted**: `SCAN_BUDGET_PER_TICK` (16,384) positions per tick. The volume is
  `(maxDistance + 1)³` — 274,625 positions at the default range — so a full sweep
  lands in about 17 ticks, just under a second. That is the latency between
  placing a light block and seeing it.
- Skips positions in unloaded chunks and drops any cached light there.
- Checks for a registered handler **before** allocating anything. This matters
  more than it looks: the old code built a list and an event object for every
  position in the volume, continuously.
- When a sweep finishes it re-centres on the player and prunes cached lights that
  fall outside the new range.

If no mod has registered any block handler, the scan returns immediately and
costs nothing — a bare Albedo install does no work here.

## Culling

Applied as lights are added, in `GatherLightsEvent#add` and
`LightManager#addLight`:

1. **Null** — dropped silently. `ILightProvider#provideLight` is `@Nullable` with
   a default returning `null`, so this is a normal path, not an error.
2. **Distance** — beyond `radius + maxDistance` from the camera.
3. **Frustum** — the light's bounding box must intersect the view frustum.

Then in `LightManager#update`, after everything is gathered:

4. **Sort** nearest-first, so that when there are more lights than `maxLights`
   the ones that survive are the ones nearest the camera.
5. **Occlusion**, only if enabled — see below.

## Occlusion (opt-in)

Off by default. When `enableOcclusion` is set, `cullOccluded` traces a ray from
the camera to each light and drops the ones something solid stands in front of.

Two details that are easy to get wrong:

- **Cost is bounded by `maxLights`.** Culling walks the sorted list and stops
  testing once that many lights have survived; anything past the cap was never
  going to be uploaded, so paying for a raytrace there would be waste.
- **A light must not occlude itself.** A block light sits *inside* its own block,
  so the ray always ends by hitting that block. A hit within 1.5 blocks of the
  light is therefore treated as the light's own geometry. Without this every
  block light hides itself and nothing renders — if you change the occlusion
  code, test a light in the open, not just one behind a wall.

The test uses `World#rayTraceBlocks` with `ignoreBlockWithoutBoundingBox = true`,
so air, torches and crops do not occlude, but anything with a collision box does
— including glass and leaves. That inaccuracy is deliberate and documented in the
config comment.

## Upload

`LightManager#uploadLights` writes the first `min(maxLights, MAX_SHADER_LIGHTS)`
entries into the bound program as `lights[i].position`, `.color`, `.heading` and
`.angle`, plus `lightCount`.

`lightCount` is that same upload count, **not** how many lights survived culling.
The two are easy to confuse and the difference matters: the shader loops
`for (i < lightCount)`, so naming a number larger than what was written sends it
reading uniform slots this frame never filled. Those slots are not empty — GL
uniforms persist across draws on the same program — so they still hold whichever
light sat there on an earlier frame, which renders as a ghost light at a stale
position and colour. Past index 99 it leaves the declared array altogether.

Positions are uploaded **relative to the camera**: subtracted in double precision
on the Java side, so the float that reaches OpenGL is small and exact wherever in
the world the player is. See [SHADERS.md](SHADERS.md#coordinates) — this is not
optional, and uploading absolute coordinates instead is what used to make lights
snap to a grid millions of blocks out.

`uploadLights` returns early when nothing of Albedo's is bound, which happens
when it has stood down for another mod's shader.

## Data model

`Light` carries position twice: `worldX/Y/Z` as `double` (what rendering uses)
and `x/y/z` as `float` (kept because dependent mods read them). Direction is
`rx/ry/rz` — a vector whose **length is the radius** — plus `angle`, the cone
half-angle in radians. An omnidirectional light is a cone with `angle = 2π`;
leaving it at zero makes the shader compute zero intensity and the light renders
nothing at all.
