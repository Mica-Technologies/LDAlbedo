# Configuration

Options live in `config/Albedo.cfg`, generated on first run. They are read
through `ConfigManager` and are all client-side.

| Option | Default | Effect |
| --- | --- | --- |
| `maxLights` | 40 | How many lights may render at once. Lights are sorted nearest-first, so the cap drops the furthest. Also bounds the cost of occlusion. |
| `maxDistance` | 64 | Cull radius for lights, and the side of the block-scan volume. Raising it costs cubically — the scan visits `(maxDistance + 1)³` positions per sweep. |
| `disableLights` | false | Turns Albedo's lighting off entirely. |
| `holiday` | true | Holiday events. Inherited from upstream. |
| `enableOcclusion` | **false** | Hide lights whose line of sight to the camera is blocked. |
| `ignoreForeignShaders` | **false** | Bind Albedo's shaders even when another mod already has one bound. |

## `enableOcclusion`

Albedo is cosmetic lighting: it never consults Minecraft's lighting engine,
which is what makes it cheap and is also why light passes through walls by
default. Upstream treats that as intended behaviour.

Turning this on traces a ray from the camera to each visible light and drops the
blocked ones, at the cost of one raytrace per visible light per frame (bounded
by `maxLights`).

It is deliberately approximate:

- A light around a corner is hidden even when the surface it would light is
  visible.
- Anything with a collision box occludes, so glass and leaves do too.

Leave it off unless light through walls bothers you more than the cost does.

## `ignoreForeignShaders`

Albedo normally stands down while another mod owns the shader pipeline, because
binding over it corrupts that mod's rendering — BetterPortals drawing portals
into the world is the usual case. The cost is that Albedo's lighting does not
draw while the other mod holds the pipeline. It logs a line the first time this
happens, so it is diagnosable from `latest.log`.

Turn this on only to diagnose a mod holding a shader bound more widely than it
should, or if you would rather have Albedo's lighting than that mod's rendering
be correct. Expect visual corruption in one or the other.

## Tuning notes

- **Lights vanish in a busy scene** — raise `maxLights`. The nearest survive, so
  this shows up as distant lights popping out.
- **A light appears about a second after you place the block** — expected. The
  block scan is budgeted across ticks; see
  [LIGHT_COLLECTION.md](LIGHT_COLLECTION.md).
- **Frame rate drops with occlusion on** — that is the raytracing. Lower
  `maxLights` before lowering `maxDistance`; the latter is cubic on scan cost.
- **A coloured light seems to do nothing** — check whether it is on a block that
  already emits vanilla light. The shader blends with `max()`, so vanilla
  brightness wins. See [SHADERS.md](SHADERS.md).
