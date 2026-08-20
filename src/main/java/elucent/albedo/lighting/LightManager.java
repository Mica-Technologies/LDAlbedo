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
        shader.setUniform("lightCount", lights.size());
        for (int i = 0; i < Math.min(ConfigManager.maxLights, lights.size()); ++i) {
            if (i >= lights.size()) continue;
            Light l = lights.get(i);
            shader.setUniform("lights[" + i + "].position", l.x, l.y, l.z);
            shader.setUniform("lights[" + i + "].color", l.r, l.g, l.b, l.a);
            shader.setUniform("lights[" + i + "].heading", l.rx, l.ry, l.rz);
            shader.setUniform("lights[" + i + "].angle", l.angle);
        }
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
        Vec3d lightPos = new Vec3d(light.x, light.y, light.z);
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
            double dist1 = cameraPos.squareDistanceTo(a.x, a.y, a.z);
            double dist2 = cameraPos.squareDistanceTo(b.x, b.y, b.z);
            return Double.compare(dist1, dist2);
        }
    }
}
