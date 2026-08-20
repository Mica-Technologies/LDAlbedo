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

Options live in `config/Albedo.cfg`. Most are self-explanatory; this one is worth
calling out:

**`enableOcclusion`** (default `false`) — hide lights whose line of sight to the
camera is blocked by solid blocks.

Albedo is *cosmetic* lighting: it never touches Minecraft's own lighting engine,
which is what makes it cheap, and is also why light bleeds through walls by
default. That behaviour is inherited from upstream and is intentional there.
Turning this on traces a ray from the camera to each visible light and drops the
ones that are blocked, at the cost of one raytrace per visible light per frame
(bounded by `maxLights`).

It is deliberately approximate: a light around a corner is hidden even when the
surface it would light is visible, and any block with a collision box counts as
blocking, so glass and leaves occlude too. Leave it off unless light shining
through walls bothers you more than the performance cost does.

## Documentation

See [`CLAUDE.md`](CLAUDE.md) for build instructions, source layout, and an
overview of how the ASM coremod hooks into vanilla rendering.

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
