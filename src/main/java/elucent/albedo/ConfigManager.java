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

    public static boolean isLightingEnabled() {
        return !disableLights;
    }
}
