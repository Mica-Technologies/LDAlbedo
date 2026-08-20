package elucent.albedo;

import elucent.albedo.event.GatherLightsEvent;
import elucent.albedo.event.LightUniformEvent;
import elucent.albedo.event.ProfilerStartEvent;
import elucent.albedo.event.RenderChunkUniformsEvent;
import elucent.albedo.event.RenderEntityEvent;
import elucent.albedo.event.RenderTileEntityEvent;
import elucent.albedo.lighting.Light;
import elucent.albedo.lighting.LightManager;
import elucent.albedo.util.ShaderManager;
import elucent.albedo.util.ShaderUtil;
import elucent.albedo.util.TriConsumer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.tileentity.TileEntityEndGateway;
import net.minecraft.tileentity.TileEntityEndPortal;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.DimensionType;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

public class EventManager {
    public static final Map<BlockPos, List<Light>> EXISTING = Collections.synchronizedMap(new HashMap<>());
    public static boolean isGui = false;
    int ticks = 0;
    boolean postedLights = false;
    boolean precedesEntities = true;
    String section = "";
    Thread thread;

    // NOTE: not thread-safe (see upstream issue #3) - carried over as-is from the recovered 1.12.2 source.
    public void startThread() {
        this.thread = new Thread(() -> {
            while (!this.thread.isInterrupted()) {
                if (Minecraft.getMinecraft().player == null) continue;
                EntityPlayerSP player = Minecraft.getMinecraft().player;
                if (Minecraft.getMinecraft().world == null) continue;
                WorldClient reader = Minecraft.getMinecraft().world;
                BlockPos playerPos = player.getPosition();
                int maxDistance = ConfigManager.maxDistance;
                int r = maxDistance / 2;
                Iterable<BlockPos.MutableBlockPos> posIterable = BlockPos.getAllInBoxMutable(playerPos.add(-r, -r, -r), playerPos.add(r, r, r));
                for (BlockPos.MutableBlockPos pos : posIterable) {
                    Vec3d cameraPosition = LightManager.cameraPos;
                    ICamera camera = LightManager.camera;
                    IBlockState state = reader.getBlockState(pos);
                    ArrayList<Light> lights = new ArrayList<>();
                    GatherLightsEvent lightsEvent = new GatherLightsEvent(lights, maxDistance, cameraPosition, camera);
                    TriConsumer<BlockPos, IBlockState, GatherLightsEvent> consumer = Albedo.getLightHandler(state.getBlock());
                    if (consumer != null) {
                        consumer.apply(pos, state, lightsEvent);
                    }
                    if (lights.isEmpty()) {
                        EXISTING.remove(pos);
                        continue;
                    }
                    EXISTING.put(pos.toImmutable(), lights);
                }
            }
        });
        this.thread.start();
    }

    @SubscribeEvent
    public void onProfilerChange(ProfilerStartEvent event) {
        this.section = event.getSection();
        if (ConfigManager.isLightingEnabled()) {
            if (event.getSection().compareTo("terrain") == 0) {
                isGui = false;
                this.precedesEntities = true;
                ShaderUtil.fastLightProgram.useShader();
                ShaderUtil.fastLightProgram.setUniform("ticks", (float) this.ticks + Minecraft.getMinecraft().getRenderPartialTicks());
                ShaderUtil.fastLightProgram.setUniform("sampler", 0);
                ShaderUtil.fastLightProgram.setUniform("lightmap", 1);
                ShaderUtil.fastLightProgram.setUniform("playerPos", (float) Minecraft.getMinecraft().player.posX, (float) Minecraft.getMinecraft().player.posY, (float) Minecraft.getMinecraft().player.posZ);
                if (!this.postedLights) {
                    if (this.thread == null || !this.thread.isAlive()) {
                        this.startThread();
                    }
                    EXISTING.forEach((pos, lights) -> LightManager.lights.addAll(lights));
                    LightManager.update(Minecraft.getMinecraft().world);
                    ShaderManager.stopShader();
                    MinecraftForge.EVENT_BUS.post(new LightUniformEvent());
                    ShaderUtil.fastLightProgram.useShader();
                    LightManager.uploadLights();
                    ShaderUtil.entityLightProgram.useShader();
                    ShaderUtil.entityLightProgram.setUniform("ticks", (float) this.ticks + Minecraft.getMinecraft().getRenderPartialTicks());
                    ShaderUtil.entityLightProgram.setUniform("sampler", 0);
                    ShaderUtil.entityLightProgram.setUniform("lightmap", 1);
                    LightManager.uploadLights();
                    ShaderUtil.entityLightProgram.setUniform("playerPos", (float) Minecraft.getMinecraft().player.posX, (float) Minecraft.getMinecraft().player.posY, (float) Minecraft.getMinecraft().player.posZ);
                    ShaderUtil.entityLightProgram.setUniform("lightingEnabled", GL11.glIsEnabled(2896));
                    ShaderUtil.fastLightProgram.useShader();
                    this.postedLights = true;
                    LightManager.clear();
                }
            }
            if (event.getSection().compareTo("sky") == 0) {
                ShaderManager.stopShader();
            }
            if (event.getSection().compareTo("litParticles") == 0) {
                ShaderUtil.fastLightProgram.useShader();
                ShaderUtil.fastLightProgram.setUniform("sampler", 0);
                ShaderUtil.fastLightProgram.setUniform("lightmap", 1);
                ShaderUtil.fastLightProgram.setUniform("playerPos", (float) Minecraft.getMinecraft().player.posX, (float) Minecraft.getMinecraft().player.posY, (float) Minecraft.getMinecraft().player.posZ);
                ShaderUtil.fastLightProgram.setUniform("chunkX", 0);
                ShaderUtil.fastLightProgram.setUniform("chunkY", 0);
                ShaderUtil.fastLightProgram.setUniform("chunkZ", 0);
            }
            if (event.getSection().compareTo("particles") == 0) {
                ShaderManager.stopShader();
            }
            if (event.getSection().compareTo("weather") == 0) {
                ShaderManager.stopShader();
            }
            if (event.getSection().compareTo("entities") == 0 && Minecraft.getMinecraft().isCallingFromMinecraftThread()) {
                ShaderUtil.entityLightProgram.useShader();
                ShaderUtil.entityLightProgram.setUniform("lightingEnabled", true);
                ShaderUtil.entityLightProgram.setUniform("fogIntensity", Minecraft.getMinecraft().world.provider.getDimensionType() == DimensionType.NETHER ? 0.015625f : 1.0f);
            }
            if (event.getSection().compareTo("blockEntities") == 0 && Minecraft.getMinecraft().isCallingFromMinecraftThread()) {
                ShaderUtil.entityLightProgram.useShader();
                ShaderUtil.entityLightProgram.setUniform("lightingEnabled", true);
            }
            if (event.getSection().compareTo("outline") == 0) {
                ShaderManager.stopShader();
            }
            if (event.getSection().compareTo("aboveClouds") == 0) {
                ShaderManager.stopShader();
            }
            if (event.getSection().compareTo("destroyProgress") == 0) {
                ShaderManager.stopShader();
            }
            if (event.getSection().compareTo("translucent") == 0) {
                ShaderUtil.fastLightProgram.useShader();
                ShaderUtil.fastLightProgram.setUniform("sampler", 0);
                ShaderUtil.fastLightProgram.setUniform("lightmap", 1);
                ShaderUtil.fastLightProgram.setUniform("playerPos", (float) Minecraft.getMinecraft().player.posX, (float) Minecraft.getMinecraft().player.posY, (float) Minecraft.getMinecraft().player.posZ);
            }
            if (event.getSection().compareTo("hand") == 0) {
                ShaderUtil.entityLightProgram.useShader();
                ShaderUtil.fastLightProgram.setUniform("entityPos", (float) Minecraft.getMinecraft().player.posX, (float) Minecraft.getMinecraft().player.posY, (float) Minecraft.getMinecraft().player.posZ);
                this.precedesEntities = true;
            }
            if (event.getSection().compareTo("gui") == 0) {
                isGui = true;
                ShaderManager.stopShader();
            }
        }
    }

    @SubscribeEvent
    public void onRenderEntity(RenderEntityEvent event) {
        if (ConfigManager.isLightingEnabled()) {
            if (event.getEntity() instanceof EntityLightningBolt) {
                ShaderManager.stopShader();
            } else if (this.section.equalsIgnoreCase("entities") || this.section.equalsIgnoreCase("blockEntities")) {
                ShaderUtil.entityLightProgram.useShader();
            }
            if (ShaderManager.isCurrentShader(ShaderUtil.entityLightProgram)) {
                ShaderUtil.entityLightProgram.setUniform("entityPos", (float) event.getEntity().posX, (float) event.getEntity().posY + event.getEntity().height / 2.0f, (float) event.getEntity().posZ);
            }
        }
    }

    @SubscribeEvent
    public void onRenderTileEntity(RenderTileEntityEvent event) {
        if (ConfigManager.isLightingEnabled()) {
            if (event.getEntity() instanceof TileEntityEndPortal || event.getEntity() instanceof TileEntityEndGateway) {
                ShaderManager.stopShader();
            } else if (this.section.equalsIgnoreCase("entities") || this.section.equalsIgnoreCase("blockEntities")) {
                ShaderUtil.entityLightProgram.useShader();
            }
            if (ShaderManager.isCurrentShader(ShaderUtil.entityLightProgram)) {
                ShaderUtil.entityLightProgram.setUniform("entityPos", (float) event.getEntity().getPos().getX(), (float) event.getEntity().getPos().getY(), (float) event.getEntity().getPos().getZ());
            }
        }
    }

    @SubscribeEvent
    public void onRenderChunk(RenderChunkUniformsEvent event) {
        if (ConfigManager.isLightingEnabled() && ShaderManager.isCurrentShader(ShaderUtil.fastLightProgram)) {
            BlockPos pos = event.getChunk().getPosition();
            ShaderUtil.fastLightProgram.setUniform("chunkX", pos.getX());
            ShaderUtil.fastLightProgram.setUniform("chunkY", pos.getY());
            ShaderUtil.fastLightProgram.setUniform("chunkZ", pos.getZ());
        }
    }

    @SubscribeEvent
    public void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            ++this.ticks;
        }
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        this.postedLights = false;
        if (Minecraft.getMinecraft().isCallingFromMinecraftThread()) {
            GlStateManager.disableLighting();
            ShaderManager.stopShader();
        }
    }
}
