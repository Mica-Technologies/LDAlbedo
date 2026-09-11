package elucent.albedo;

import com.google.common.collect.ImmutableMap;
import elucent.albedo.event.GatherLightsEvent;
import elucent.albedo.lighting.DefaultLightProvider;
import elucent.albedo.lighting.ILightProvider;
import elucent.albedo.util.TriConsumer;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTBase;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/**
 * Loads on both sides. Rendering is client-only, but the API is not: a mod that syncs light
 * settings from the server needs Albedo present there to hard-depend on it, and needs the
 * {@link ILightProvider} capability registered there to attach it from common code. Upstream
 * shipped this as {@code clientSideOnly}, which dropped the mod from a dedicated server's mod list
 * entirely and left the capability {@code null}.
 *
 * <p>{@code acceptableRemoteVersions = "*"} keeps connections working when only one side has
 * Albedo, as they did while it was client-only.
 */
@Mod(modid = "albedo", version = "1.0.0", acceptableRemoteVersions = "*", acceptedMinecraftVersions = "[1.12.2]")
public class Albedo {
    private static final Map<Block, TriConsumer<BlockPos, IBlockState, GatherLightsEvent>> MAP = new HashMap<>();

    @CapabilityInject(ILightProvider.class)
    public static Capability<ILightProvider> LIGHT_PROVIDER_CAPABILITY;

    public Albedo() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    public static TriConsumer<BlockPos, IBlockState, GatherLightsEvent> getLightHandler(Block block) {
        return MAP.get(block);
    }

    public static void registerBlockHandler(Block block, TriConsumer<BlockPos, IBlockState, GatherLightsEvent> handler) {
        MAP.put(block, handler);
    }

    public static ImmutableMap<Block, TriConsumer<BlockPos, IBlockState, GatherLightsEvent>> getBlockHandlers() {
        return ImmutableMap.copyOf(MAP);
    }

    @Mod.EventHandler
    public void preinit(FMLPreInitializationEvent event) {
        CapabilityManager.INSTANCE.register(ILightProvider.class, new Capability.IStorage<ILightProvider>() {
            @Nullable
            @Override
            public NBTBase writeNBT(Capability<ILightProvider> capability, ILightProvider instance, EnumFacing side) {
                return null;
            }

            @Override
            public void readNBT(Capability<ILightProvider> capability, ILightProvider instance, EnumFacing side, NBTBase nbt) {
            }
        }, DefaultLightProvider::new);
    }

    @Mod.EventHandler
    public void loadComplete(FMLPostInitializationEvent event) {
        if (event.getSide().isClient()) {
            AlbedoClient.init();
        }
    }
}
