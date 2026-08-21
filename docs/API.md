# Adding lights from your mod

Everything here is on the **client**. Albedo is `clientSideOnly = true` and does
not exist on a dedicated server — guard anything that touches it accordingly.

There are three ways to contribute a light, in rough order of how often you want
them.

## 1. Blocks: register a handler

For a block that should glow, register a handler once during init. Albedo scans
the blocks around the player and calls your handler for each matching block it
finds.

```java
Albedo.registerBlockHandler(MyBlocks.GLOWING_LAMP, (pos, state, event) ->
        event.add(Light.builder()
                .pos(pos)
                .color(1.0f, 0.4f, 0.1f, 1.0f)
                .radius(12.0f)
                .build()));
```

The handler receives the `BlockPos`, its `IBlockState` (so you can vary colour
by metadata or block properties) and a `GatherLightsEvent` to add to. Adding
nothing is fine and is how you make a block conditionally dark.

Handlers are called from a budgeted scan on the client thread, so a full sweep
of the area around the player takes about a second — a light appearing is
prompt but not instantaneous. See [LIGHT_COLLECTION.md](LIGHT_COLLECTION.md).

## 2. Entities and tile entities: the capability

Attach `Albedo.LIGHT_PROVIDER_CAPABILITY` to an entity, tile entity or item
stack, and implement `ILightProvider`:

```java
public class MyLight implements ILightProvider {
    @Override
    public void gatherLights(GatherLightsEvent event, Entity context) {
        event.add(Light.builder()
                .pos(context)
                .color(0.2f, 0.6f, 1.0f, 1.0f)
                .radius(8.0f)
                .build());
    }
}
```

Albedo checks the entity itself, its held equipment, its armour, and — for
`EntityItem` — the stack it carries. Tile entities in the loaded list are
checked too. An object may also simply `implement ILightProvider` directly
rather than exposing it as a capability.

`ILightProvider#provideLight()` is the older single-light form. It is
`@Nullable`, its default implementation returns `null`, and returning `null` is
normal and harmless — Albedo drops it. Prefer `gatherLights`, which can add any
number of lights.

## 3. Anything else: subscribe to `GatherLightsEvent`

```java
@SubscribeEvent
public void onGatherLights(GatherLightsEvent event) {
    event.add(myLight);
}
```

Posted once per frame while lights are being collected. Use this for lights
that belong to neither a block nor an entity.

`LightManager.addLight(Light)` does the same thing outside the event. It exists
because mods compile against it; see the compatibility note at the bottom.

## Building a `Light`

Prefer the builder. It is the only route that gets every field right:

```java
Light.builder()
     .pos(blockPos)              // or Vec3d, Entity, or raw doubles
     .color(r, g, b, a)          // 0..1 floats, or color(int, hasAlpha)
     .radius(12.0f)              // omnidirectional
     .build();
```

For a cone (spot light) use `.direction(x, y, z, angle)` instead of `.radius`,
where the vector's *length* is the radius and `angle` is the cone half-angle in
radians.

### Two traps worth knowing

- **Use the builder's position methods that take a `BlockPos`, `Vec3d`, `Entity`
  or `double`.** Those keep full precision. `pos(float, float, float)` cannot,
  and a `float` stops being able to hold a block coordinate past ~8.4 million
  blocks from the origin.
- **The raw `Light` constructors are fine now but were not always.** The 8-arg
  point-light constructor used to leave the cone angle at zero, which the shader
  turned into zero intensity — such lights rendered *nothing at all*. Fixed, but
  if you are debugging against an older Albedo and your light is invisible, that
  is why.

### Reading a light's position

`Light` carries the position twice:

- `worldX` / `worldY` / `worldZ` (`double`) — what Albedo renders from. Use
  these.
- `x` / `y` / `z` (`float`) — kept because mods read them. Lossy far from the
  origin.

## Reacting to Albedo's own uniform upload

`LightUniformEvent` fires each frame after Albedo has uploaded its lights and
before terrain draws, with Albedo's shader bound. Subscribe to it if you need to
push your own uniforms into that shader at the right moment. Albedo posts it and
never consumes it — it is there for you.

## Compatibility notes for API consumers

Albedo 1.1.0 removed `LightManager.addLight(Light)`. Mods built against earlier
versions died with:

```
NoSuchMethodError: elucent.albedo.lighting.LightManager.addLight(Lelucent/albedo/lighting/Light;)V
```

It has been restored and will not be removed again. If you are maintaining a mod
that hit this, no change is needed on your side.

More generally: the `elucent.albedo` package name is kept deliberately, despite
this being the Mica-Technologies fork, precisely so existing dependents keep
linking.
