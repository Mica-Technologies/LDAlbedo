package elucent.albedo.lighting;

import elucent.albedo.Albedo;
import elucent.albedo.ConfigManager;
import elucent.albedo.event.GatherLightsEvent;
import elucent.albedo.util.ShaderManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;

public class LightManager {
    /**
     * How many lights the shaders can hold, fixed by the {@code uniform Light lights[100]}
     * declaration in {@code fastlight.vs} and {@code entitylight.vs}. Raising the config above
     * this does nothing; the extra lights are dropped rather than written past the array.
     */
    public static final int MAX_SHADER_LIGHTS = 100;

    public static Vec3d cameraPos;
    public static ICamera camera;
    public static ArrayList<Light> lights;
    public static DistComparator distComparator;

    static {
        lights = new ArrayList<>();
        distComparator = new DistComparator();
    }

    public static void uploadLights() {
        ShaderManager shader = ShaderManager.getCurrentShader();
        if (shader == null) {
            // Nothing of ours is bound — most likely another mod owns the pipeline and Albedo
            // has stood down for it. There is nowhere to put these uniforms.
            return;
        }
        // Only this many lights actually get uniform data written below, so this is what the
        // shader must be told to read. Reporting lights.size() instead would send it looking at
        // slots we never filled, which still hold whichever light occupied them last frame --
        // GL uniforms persist across draws on the same program -- and past index 99 it would run
        // off the end of the declared array entirely.
        int uploadCount = Math.min(Math.min(ConfigManager.maxLights, lights.size()), MAX_SHADER_LIGHTS);
        shader.setUniform("lightCount", uploadCount);
        // Everything the shader sees is relative to the camera. Subtracting in double precision
        // and uploading only the offset keeps the numbers small, so the float uniform stays
        // exact no matter how far from the origin the player is (upstream #8).
        double camX = cameraPos == null ? 0.0 : cameraPos.x;
        double camY = cameraPos == null ? 0.0 : cameraPos.y;
        double camZ = cameraPos == null ? 0.0 : cameraPos.z;
        for (int i = 0; i < uploadCount; ++i) {
            Light l = lights.get(i);
            shader.setUniform("lights[" + i + "].position",
                    (float) (l.worldX - camX), (float) (l.worldY - camY), (float) (l.worldZ - camZ));
            shader.setUniform("lights[" + i + "].color", l.r, l.g, l.b, l.a);
            shader.setUniform("lights[" + i + "].heading", l.rx, l.ry, l.rz);
            shader.setUniform("lights[" + i + "].angle", l.angle);
        }
    }

    /**
     * Submits a light for this frame, subject to the same distance and frustum culling
     * {@link GatherLightsEvent#add} applies.
     *
     * <p>Kept because mods compile against it. Albedo 1.1.0 dropped this method, and anything
     * built against an earlier version — WeissAlbedo is the one in the wild — then died with
     * {@code NoSuchMethodError} the moment it tried to contribute a light. It is public API
     * whether or not Albedo's own code calls it, so removing it again would re-break those mods.
     *
     * @param light the light to add; null is ignored, since "nothing to show right now" is a
     *              normal thing for a provider to report
     */
    public static void addLight(Light light) {
        if (light == null) {
            return;
        }
        float radius = light.radius();
        double cullDistance = radius + ConfigManager.maxDistance;
        if (cameraPos != null
                && cameraPos.squareDistanceTo(light.worldX, light.worldY, light.worldZ) > cullDistance * cullDistance) {
            return;
        }
        if (camera != null && !camera.isBoundingBoxInFrustum(new AxisAlignedBB(
                light.worldX - radius, light.worldY - radius, light.worldZ - radius,
                light.worldX + radius, light.worldY + radius, light.worldZ + radius))) {
            return;
        }
        lights.add(light);
    }

    private static Vec3d interpolate(Entity entity, float partialTicks) {
        return new Vec3d(
                entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * (double) partialTicks,
                entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * (double) partialTicks,
                entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * (double) partialTicks);
    }

    public static void update(World world) {
        ILightProvider provider;
        Minecraft mc = Minecraft.getMinecraft();
        Entity cameraEntity = mc.getRenderViewEntity();
        if (cameraEntity == null) {
            if (cameraPos == null) {
                cameraPos = new Vec3d(0.0, 0.0, 0.0);
            }
            camera = null;
            return;
        }
        cameraPos = LightManager.interpolate(cameraEntity, mc.getRenderPartialTicks());
        camera = new Frustum();
        camera.setPosition(LightManager.cameraPos.x, LightManager.cameraPos.y, LightManager.cameraPos.z);
        GatherLightsEvent event = new GatherLightsEvent(lights, ConfigManager.maxDistance, cameraPos, camera);
        MinecraftForge.EVENT_BUS.post(event);
        int maxDist = ConfigManager.maxDistance;
        for (Entity e : world.getEntitiesWithinAABB(Entity.class, new AxisAlignedBB(
                LightManager.cameraPos.x - maxDist, LightManager.cameraPos.y - maxDist, LightManager.cameraPos.z - maxDist,
                LightManager.cameraPos.x + maxDist, LightManager.cameraPos.y + maxDist, LightManager.cameraPos.z + maxDist))) {
            if (e instanceof ILightProvider) {
                event.add(((ILightProvider) e).provideLight());
            }
            if (e instanceof EntityItem && ((EntityItem) e).getItem().hasCapability(Albedo.LIGHT_PROVIDER_CAPABILITY, null)) {
                provider = ((EntityItem) e).getItem().getCapability(Albedo.LIGHT_PROVIDER_CAPABILITY, null);
                provider.gatherLights(event, e);
            }
            if (!e.hasCapability(Albedo.LIGHT_PROVIDER_CAPABILITY, null)) continue;
            provider = e.getCapability(Albedo.LIGHT_PROVIDER_CAPABILITY, null);
            provider.gatherLights(event, e);
            for (ItemStack itemStack : e.getHeldEquipment()) {
                if (!itemStack.hasCapability(Albedo.LIGHT_PROVIDER_CAPABILITY, null)) continue;
                provider = itemStack.getCapability(Albedo.LIGHT_PROVIDER_CAPABILITY, null);
                provider.gatherLights(event, e);
            }
            for (ItemStack itemStack : e.getArmorInventoryList()) {
                if (!itemStack.hasCapability(Albedo.LIGHT_PROVIDER_CAPABILITY, null)) continue;
                provider = itemStack.getCapability(Albedo.LIGHT_PROVIDER_CAPABILITY, null);
                provider.gatherLights(event, e);
            }
        }
        for (TileEntity t : world.loadedTileEntityList) {
            if (t instanceof ILightProvider) {
                event.add(((ILightProvider) t).provideLight());
            }
            if (!t.hasCapability(Albedo.LIGHT_PROVIDER_CAPABILITY, null)) continue;
            provider = t.getCapability(Albedo.LIGHT_PROVIDER_CAPABILITY, null);
            provider.gatherLights(event, null);
        }
        lights.sort(distComparator);

        if (ConfigManager.isOcclusionEnabled()) {
            cullOccluded(world, cameraEntity.getPositionEyes(mc.getRenderPartialTicks()));
        }
    }

    /**
     * Removes lights that cannot be seen from {@code eyes} because solid blocks are in the way.
     *
     * <p>Off by default. Albedo does not consult Minecraft's lighting engine at all — that is
     * what makes it cheap — so without this, light passes straight through walls. Expects
     * {@link #lights} to already be sorted nearest-first.
     */
    private static void cullOccluded(World world, Vec3d eyes) {
        // Only the nearest maxLights entries are ever uploaded. Once that many have survived,
        // the rest are dropped untested: paying for a raytrace whose answer nobody reads is
        // exactly the cost this feature is already being criticised for.
        int budget = ConfigManager.maxLights;
        Iterator<Light> iter = lights.iterator();
        while (iter.hasNext()) {
            Light light = iter.next();
            if (budget <= 0 || isOccluded(world, eyes, light)) {
                iter.remove();
            } else {
                budget--;
            }
        }
    }

    /** True when solid geometry stands between {@code eyes} and {@code light}. */
    private static boolean isOccluded(World world, Vec3d eyes, Light light) {
        Vec3d lightPos = new Vec3d(light.worldX, light.worldY, light.worldZ);
        // Blocks without a collision box (air, torches, crops) are ignored, so they do not
        // occlude. Blocks that have one do — including glass, which is the main inaccuracy here.
        RayTraceResult hit = world.rayTraceBlocks(eyes, lightPos, false, true, false);
        if (hit == null) {
            return false;
        }
        // A light attached to a block sits inside that block, so the ray always ends by hitting
        // it. Only treat the hit as occlusion when it happens meaningfully short of the light,
        // otherwise every block light would occlude itself and never render.
        return hit.hitVec.distanceTo(eyes) < lightPos.distanceTo(eyes) - 1.5;
    }

    public static void clear() {
        lights.clear();
    }

    public static class DistComparator implements Comparator<Light> {
        @Override
        public int compare(Light a, Light b) {
            double dist1 = cameraPos.squareDistanceTo(a.worldX, a.worldY, a.worldZ);
            double dist2 = cameraPos.squareDistanceTo(b.worldX, b.worldY, b.worldZ);
            return Double.compare(dist1, dist2);
        }
    }
}
