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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GlStateManager;
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

    /**
     * How many block positions the incremental scan visits per client tick. The scan volume is
     * {@code (maxDistance + 1)^3} — 274,625 positions at the default range — so this sets the
     * refresh rate: at 16,384 per tick a full sweep completes in roughly 17 ticks, just under a
     * second, which is well inside the window where a light appearing feels immediate.
     */
    private static final int SCAN_BUDGET_PER_TICK = 16384;

    int ticks = 0;
    boolean postedLights = false;
    boolean precedesEntities = true;
    String section = "";

    /** Cursor into the sweep currently in progress; null when a fresh sweep is due. */
    private Iterator<BlockPos.MutableBlockPos> scanCursor;

    /**
     * Formerly spawned a background thread that scanned world blocks for light handlers.
     *
     * <p>That was unsafe: {@code World#getBlockState} was being called off the client thread,
     * racing chunk load/unload, which could take the game down when a chunk went away
     * mid-scan. Scanning now happens incrementally on the client thread — see
     * {@link #scanBlockLights()} — so there is no longer a thread to start.
     *
     * @deprecated retained as a no-op so anything that called it keeps linking. Does nothing.
     */
    @Deprecated
    public void startThread() {
        // No-op. Scanning is driven from clientTick.
    }

    /**
     * Visits a bounded slice of the blocks around the player each tick, recording lights from
     * any registered block handler into {@link #EXISTING}.
     *
     * <p>Runs on the client thread, which is what makes the world access here legal. The work
     * is budgeted rather than done in one pass so a full sweep costs a fraction of a tick
     * instead of stalling the frame.
     */
    private void scanBlockLights() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        WorldClient world = mc.world;

        // No world, or nothing registered any block lights: keep no state and do no work. A
        // bare Albedo install with no dependent mods never gets past this point.
        if (player == null || world == null || Albedo.getBlockHandlers().isEmpty()) {
            if (!EXISTING.isEmpty()) {
                EXISTING.clear();
            }
            this.scanCursor = null;
            return;
        }

        int maxDistance = ConfigManager.maxDistance;
        if (this.scanCursor == null || !this.scanCursor.hasNext()) {
            // Previous sweep finished (or none started): re-centre on where the player is now.
            // Anything that has drifted out of range is dropped, since this sweep will not
            // visit those positions to clear them individually.
            int r = maxDistance / 2;
            BlockPos playerPos = player.getPosition();
            pruneOutOfRange(playerPos, r);
            this.scanCursor = BlockPos.getAllInBoxMutable(
                    playerPos.add(-r, -r, -r), playerPos.add(r, r, r)).iterator();
        }

        for (int budget = SCAN_BUDGET_PER_TICK; budget > 0 && this.scanCursor.hasNext(); budget--) {
            BlockPos.MutableBlockPos pos = this.scanCursor.next();

            // Chunks stream in and out constantly; asking an unloaded one for its state is the
            // exact call that used to be a race. On the client thread it is merely wrong, so
            // skip it and let a later sweep pick the block up once the chunk is present.
            if (!world.isBlockLoaded(pos, false)) {
                EXISTING.remove(pos);
                continue;
            }

            IBlockState state = world.getBlockState(pos);
            TriConsumer<BlockPos, IBlockState, GatherLightsEvent> consumer =
                    Albedo.getLightHandler(state.getBlock());
            if (consumer == null) {
                // Overwhelmingly the common case. Checking before allocating matters: the old
                // code built a list and an event for every position in the volume.
                EXISTING.remove(pos);
                continue;
            }

            ArrayList<Light> lights = new ArrayList<>();
            consumer.apply(pos, state, new GatherLightsEvent(
                    lights, maxDistance, LightManager.cameraPos, LightManager.camera));
            if (lights.isEmpty()) {
                EXISTING.remove(pos);
            } else {
                EXISTING.put(pos.toImmutable(), lights);
            }
        }
    }

    /**
     * Expresses a world position as an offset from the camera, narrowed to float.
     *
     * <p>Every position the shaders work in is camera-relative. Doing the subtraction here, in
     * double precision, means the float that reaches OpenGL only ever holds a small number --
     * a few hundred blocks at most -- and stays exact. Handing the shader an absolute
     * coordinate instead is what made lights snap to a grid past 2^23 blocks out (upstream #8),
     * since a float simply cannot hold a block coordinate that large.
     */
    private static float[] relativeToCamera(double x, double y, double z) {
        Vec3d camera = LightManager.cameraPos;
        if (camera == null) {
            return new float[] {(float) x, (float) y, (float) z};
        }
        return new float[] {
                (float) (x - camera.x),
                (float) (y - camera.y),
                (float) (z - camera.z)};
    }

    /** Drops recorded lights that the sweep starting at {@code centre} will not reach. */
    private static void pruneOutOfRange(BlockPos centre, int r) {
        synchronized (EXISTING) {
            EXISTING.keySet().removeIf(pos ->
                    Math.abs(pos.getX() - centre.getX()) > r
                            || Math.abs(pos.getY() - centre.getY()) > r
                            || Math.abs(pos.getZ() - centre.getZ()) > r);
        }
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
                ShaderUtil.fastLightProgram.setUniform("playerPos", relativeToCamera(
                        Minecraft.getMinecraft().player.posX,
                        Minecraft.getMinecraft().player.posY,
                        Minecraft.getMinecraft().player.posZ));
                if (!this.postedLights) {
                    synchronized (EXISTING) {
                        EXISTING.forEach((pos, lights) -> LightManager.lights.addAll(lights));
                    }
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
                    ShaderUtil.entityLightProgram.setUniform("playerPos", relativeToCamera(
                        Minecraft.getMinecraft().player.posX,
                        Minecraft.getMinecraft().player.posY,
                        Minecraft.getMinecraft().player.posZ));
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
                ShaderUtil.fastLightProgram.setUniform("playerPos", relativeToCamera(
                        Minecraft.getMinecraft().player.posX,
                        Minecraft.getMinecraft().player.posY,
                        Minecraft.getMinecraft().player.posZ));
                // Particle vertices already arrive relative to the camera, so no extra offset.
                ShaderUtil.fastLightProgram.setUniform("chunkOffset", 0.0f, 0.0f, 0.0f);
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
                ShaderUtil.fastLightProgram.setUniform("playerPos", relativeToCamera(
                        Minecraft.getMinecraft().player.posX,
                        Minecraft.getMinecraft().player.posY,
                        Minecraft.getMinecraft().player.posZ));
            }
            if (event.getSection().compareTo("hand") == 0) {
                ShaderUtil.entityLightProgram.useShader();
                ShaderUtil.fastLightProgram.setUniform("entityPos", relativeToCamera(
                        Minecraft.getMinecraft().player.posX,
                        Minecraft.getMinecraft().player.posY,
                        Minecraft.getMinecraft().player.posZ));
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
                ShaderUtil.entityLightProgram.setUniform("entityPos", relativeToCamera(
                        event.getEntity().posX,
                        event.getEntity().posY + event.getEntity().height / 2.0f,
                        event.getEntity().posZ));
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
                ShaderUtil.entityLightProgram.setUniform("entityPos", relativeToCamera(
                        event.getEntity().getPos().getX(),
                        event.getEntity().getPos().getY(),
                        event.getEntity().getPos().getZ()));
            }
        }
    }

    @SubscribeEvent
    public void onRenderChunk(RenderChunkUniformsEvent event) {
        if (ConfigManager.isLightingEnabled() && ShaderManager.isCurrentShader(ShaderUtil.fastLightProgram)) {
            BlockPos pos = event.getChunk().getPosition();
            ShaderUtil.fastLightProgram.setUniform("chunkOffset",
                    relativeToCamera(pos.getX(), pos.getY(), pos.getZ()));
        }
    }

    @SubscribeEvent
    public void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            ++this.ticks;
            if (ConfigManager.isLightingEnabled()) {
                this.scanBlockLights();
            } else if (!EXISTING.isEmpty()) {
                EXISTING.clear();
                this.scanCursor = null;
            }
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
