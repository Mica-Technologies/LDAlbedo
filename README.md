# LDAlbedo

LDAlbedo is Mica-Technologies' fork of [Albedo](https://github.com/MysticMods/Albedo)
(originally by Elucent / Horizon Studio) — a lighting library mod for Minecraft
Forge 1.12.2. Albedo doesn't add any lights on its own; it gives other mods a
shader-based API for colored, dynamic, per-block or per-entity lighting.
[AlbedoTorches](https://www.curseforge.com/minecraft/mc-mods/albedotorches),
[WeissAlbedo](https://www.curseforge.com/minecraft/mc-mods/weissalbedo), and
[ColoredLights](https://www.curseforge.com/minecraft/mc-mods/coloredlights)
are all built on top of it.

**This mod is open source and under a permissive license.** As such, it can be
included in any modpack on any platform without prior permission. See the
[LICENSE file](LICENSE) for more details.

## Project history

Upstream released version 1.1.0 in parallel for **both** 1.12.2 and 1.13.2 in
May 2019. The public `MysticMods/Albedo` git history only ever preserved the
1.13.2 side of that release; the 1.12.2 source it actually ships as on
CurseForge was never merged into that history and doesn't exist in any branch
there. Since 1.13.2 never really got modded and Forge abandoned it almost
immediately, this fork tracks **1.12.2** — the version everything else in this
ecosystem, and everything that depends on Albedo, actually uses. The 1.12.2
source in this repo was recovered by decompiling the real shipped
`albedo-1.12.2-1.1.0.jar` and cleaning up the result, not by translating the
1.13.2 code. See `CLAUDE.md` for details if you're working on this codebase.

The Java package (`elucent.albedo`) is kept exactly as upstream shipped it —
it's a public API surface other mods compile against directly, so it can't
change just because the fork changed hands.

## Configuration

Options live in `config/Albedo.cfg`. Two are worth knowing about, both **off by
default**:

- **`enableOcclusion`** — stop light passing through walls. Albedo is *cosmetic*
  lighting and never consults Minecraft's lighting engine, which is what makes it
  cheap and is also why light bleeds by default. Enabling this costs one raytrace
  per visible light per frame.
- **`ignoreForeignShaders`** — bind Albedo's shaders even when another mod
  already has one bound. Albedo normally stands down so it does not corrupt that
  mod's rendering.

[docs/CONFIGURATION.md](docs/CONFIGURATION.md) documents every option, the
trade-offs, and how to tune them.

## Documentation

Full technical documentation lives in [`docs/`](docs/):

| Document | Covers |
| --- | --- |
| [docs/API.md](docs/API.md) | **Adding lights from your own mod** |
| [docs/CONFIGURATION.md](docs/CONFIGURATION.md) | Every config option and how to tune it |
| [docs/RENDER_PIPELINE.md](docs/RENDER_PIPELINE.md) | How a frame is drawn; the profiler-driven state machine |
| [docs/LIGHT_COLLECTION.md](docs/LIGHT_COLLECTION.md) | Where lights come from, culling, occlusion |
| [docs/SHADERS.md](docs/SHADERS.md) | The GLSL, uniforms, and the camera-relative coordinate system |
| [docs/COREMOD.md](docs/COREMOD.md) | The bytecode patches and why each exists |

See [`CLAUDE.md`](CLAUDE.md) for build commands and repo layout.

## Contributing

Looking to contribute? Let us know!

- Pull requests (PRs) are always welcome
- Let us know what has or has not been tested
- GitHub issues are available for additional assistance should you need it

## Disclaimer

LDAlbedo is not affiliated with the original, official Albedo mod, Forge, or
Minecraft. LDAlbedo has been developed to continue support for Forge 1.12.2 for
the mods and modpacks that depend on it. Other developers may maintain LDAlbedo
(or derivative) support for other versions of Forge through their own forks or
contributions. Support for other versions of Forge is not guaranteed.
