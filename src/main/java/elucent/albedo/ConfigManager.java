package elucent.albedo;

import net.minecraftforge.common.config.Config;

@Config(modid = "albedo", name = "Albedo")
public class ConfigManager {
    @Config.RangeInt(min = 0, max = 1000)
    @Config.Comment("The maximum number of lights allowed to render in a scene. Lights are sorted nearest-first, so further-away lights will be culled after nearer lights.")
    @Config.LangKey("albedo.config.maxLights")
    public static int maxLights = 40;

    @Config.RangeInt(min = 16, max = 256)
    @Config.Comment("The maximum distance lights can be before being culled.")
    @Config.LangKey("albedo.config.maxDistance")
    public static int maxDistance = 64;

    @Config.Comment("Disables albedo lighting.")
    @Config.LangKey("albedo.config.disableLights")
    public static boolean disableLights = false;

    @Config.Comment("Holiday Events")
    @Config.LangKey("albedo.config.holidy")
    public static boolean holiday = true;

    @Config.Comment({
            "Hide lights whose line of sight to the camera is blocked by solid blocks.",
            "",
            "Albedo is cosmetic lighting: it does not touch Minecraft's own lighting engine, which",
            "is why it is cheap, and it is also why light bleeds through walls by default.",
            "Enabling this traces a ray from the camera to each light and drops the ones that are",
            "blocked, which stops the bleed at the cost of one raytrace per visible light per frame.",
            "",
            "It is deliberately approximate. A light around a corner is hidden even when the surface",
            "it would light is visible, and any block with a collision box counts as blocking, so",
            "glass and leaves occlude too. Leave this off unless light shining through walls bothers",
            "you more than the performance cost does."
    })
    @Config.LangKey("albedo.config.enableOcclusion")
    public static boolean enableOcclusion = false;

    @Config.Comment({
            "Bind Albedo's shaders even when another mod already has one bound.",
            "",
            "Albedo normally stands down while another mod owns the shader pipeline, because",
            "binding over it corrupts that mod's rendering. BetterPortals drawing its portals into",
            "the world is the usual example. The cost is that Albedo's lighting does not draw for",
            "as long as the other mod holds the pipeline.",
            "",
            "Turn this on only to diagnose a mod that holds a shader bound more widely than it",
            "should, or if you would rather have Albedo's lighting than that mod's rendering be",
            "correct. Expect visual corruption in one mod or the other."
    })
    @Config.LangKey("albedo.config.ignoreForeignShaders")
    public static boolean ignoreForeignShaders = false;

    public static boolean isLightingEnabled() {
        return !disableLights;
    }

    public static boolean isOcclusionEnabled() {
        return enableOcclusion;
    }
}
