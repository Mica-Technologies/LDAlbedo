package elucent.albedo.lighting;

import elucent.albedo.Albedo;
import elucent.albedo.ConfigManager;
import elucent.albedo.event.GatherLightsEvent;
import elucent.albedo.util.ShaderManager;
import java.util.ArrayList;
import java.util.Comparator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
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
