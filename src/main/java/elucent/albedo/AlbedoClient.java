package elucent.albedo;

import elucent.albedo.util.ShaderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraftforge.common.MinecraftForge;

/**
 * Client-only startup: shader loading and the render state machine.
 *
 * <p>Kept out of {@link Albedo} so that the mod class, which now loads on a dedicated server too,
 * never references a client class. Only ever called behind a client-side check.
 */
final class AlbedoClient {
    private AlbedoClient() {
    }

    static void init() {
        ((IReloadableResourceManager) Minecraft.getMinecraft().getResourceManager())
                .registerReloadListener((IResourceManagerReloadListener) new ShaderUtil());
        MinecraftForge.EVENT_BUS.register(new EventManager());
    }
}
