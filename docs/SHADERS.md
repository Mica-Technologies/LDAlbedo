# Shaders

GLSL 1.20, fixed-function-era, in `src/main/resources/assets/albedo/shaders/`.
They are compiled on resource load by `ShaderUtil#init`, so **F3+T reloads
them** — you do not need to restart the game to iterate on GLSL.

| Program | Used for | Bound during |
| --- | --- | --- |
| `fastlight` | Terrain | `terrain`, `litParticles`, `translucent` |
| `entitylight` | Entities and block entities | `entities`, `blockEntities`, `hand` |
| `depth` | — | **Never bound.** Compiled and then unused; see [README](README.md#vestigial-code). |

## Coordinates

**Everything the shaders work in is relative to the camera.** Java subtracts the
camera position in double precision and uploads only the offset.

This is not a stylistic choice. A GLSL `float` cannot represent a block
coordinate past 2²³ (~8.4 million blocks) exactly, so absolute positions
quantise — lights snapping first to whole blocks, then to two, then four. Since
the offset is at most a few hundred blocks, it stays exact anywhere in the world.

Anything you add that carries a position must follow the same rule. Use
`EventManager#relativeToCamera(x, y, z)`, which returns the `float[3]` the
`setUniform(String, float[])` overload takes.

In `fastlight.vs` the terrain vertex position is reconstructed as:

```glsl
position = gl_Vertex.xyz + chunkOffset;   // chunkOffset = chunkOrigin - camera
```

`entitylight.vs` does the same with `entityPos`. Lit particles set the offset to
zero, because particle vertices already arrive camera-relative.

## Uniforms

Shared by both light programs:

| Uniform | Meaning |
| --- | --- |
| `lights[N].position` | Camera-relative light position |
| `lights[N].color` | RGBA; alpha scales intensity |
| `lights[N].heading` | Direction vector whose **length is the radius** |
| `lights[N].angle` | Cone half-angle, radians. `2π` = omnidirectional |
| `lightCount` | How many entries are valid |
| `sampler`, `lightmap` | Texture units 0 and 1 |
| `playerPos` | Camera-relative, for fog distance |
| `ticks` | Animation time |

`fastlight` additionally takes `chunkOffset`; `entitylight` takes `entityPos`,
`lightingEnabled` and `fogIntensity`.

> `fogIntensity` was uploaded by Java for years while no shader declared it, so
> the Nether special-case it exists for never once ran. If you add a uniform,
> declare it *and* check it arrives — a write to a name the program does not
> declare is silently discarded by OpenGL.

## How a light is applied

Both vertex shaders run two loops over the lights:

1. Accumulate `totalIntens` and `maxIntens`.
2. Accumulate colour, each light weighted by its share of the total.

Cone attenuation and distance attenuation are combined with `min()`, so a light
outside the cone or beyond the radius contributes nothing.

The share in loop 2 is computed as `intensShare`, a reciprocal hoisted out of the
loop and **forced to zero when `totalIntens` is zero**. That guard matters: a
light can sit inside its radius and still contribute nothing — a zero cone angle,
or a point exactly at the falloff edge — leaving the total at zero while the loop
still runs. Dividing then is `0/0`, which is undefined in GLSL and can spread NaN
through the whole colour.

The fragment shaders then blend Albedo's colour with the vanilla lightmap:

```glsl
lightdark = max(lightdark, lcolor_2);   // vivid but unrealistic
```

**This `max()` is why an Albedo light on a block that also emits vanilla light
(glowstone, a torch) looks like it is doing nothing** — the vanilla lightmap is
already bright there, so it wins. Always test coloured light on a *non-emitting*
block in the dark. There is a commented-out additive alternative in the source
that is more physically correct and more washed out.

## Fog

Both fragment shaders normalise distance across the fog band and scale by
density:

```glsl
float fogBand = max(gl_Fog.end - gl_Fog.start, 1.0e-4f);
float dist    = max(depth - gl_Fog.start, 0.0f) / fogBand;
float fog     = 1.0f - clamp(gl_Fog.density * dist * fogIntensity, 0.0f, 1.0f);
```

They must agree. When they did not — entities using raw depth and squaring
density while terrain normalised — entities faded on a curve unrelated to the
world behind them, which was invisible in the overworld and made Nether mobs go
dark within a few blocks.

## Working on the GLSL

- **F3+T** reloads shaders in a running client.
- A compile or link failure is thrown from `ShaderUtil.createShader`; the GL log
  is printed at load (`GL LOG:` lines, empty when clean).
- To check a program is bound at all, make it output a constant colour.
- To inspect a computed value, pack it into `lcolor` from the vertex shader and
  output that from the fragment shader — one channel per quantity.
- Verify in a **built jar** as well as the dev client where ASM is involved; the
  shaders themselves behave the same in both.
