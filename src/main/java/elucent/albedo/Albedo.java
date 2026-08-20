package elucent.albedo;

import com.google.common.collect.ImmutableMap;
import elucent.albedo.event.GatherLightsEvent;
import elucent.albedo.lighting.DefaultLightProvider;
import elucent.albedo.lighting.ILightProvider;
import elucent.albedo.util.ShaderUtil;
import elucent.albedo.util.TriConsumer;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
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

@Mod(modid = "albedo", version = "1.0.0", clientSideOnly = true, acceptedMinecraftVersions = "[1.12.2]")
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
        ((IReloadableResourceManager) Minecraft.getMinecraft().getResourceManager())
                .registerReloadListener((IResourceManagerReloadListener) new ShaderUtil());
        MinecraftForge.EVENT_BUS.register(new EventManager());
    }
}
