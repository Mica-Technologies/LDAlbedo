package elucent.albedo.event;

import com.google.common.collect.ImmutableList;
import elucent.albedo.lighting.Light;
import java.util.ArrayList;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.common.eventhandler.Event;

public class GatherLightsEvent extends Event {
    private final ArrayList<Light> lights;
    private final float maxDistance;
    private final Vec3d cameraPosition;
    private final ICamera camera;

    public GatherLightsEvent(ArrayList<Light> lights, float maxDistance, Vec3d cameraPosition, ICamera camera) {
        this.lights = lights;
        this.maxDistance = maxDistance;
        this.cameraPosition = cameraPosition;
        this.camera = camera;
    }

    public ImmutableList<Light> getLightList() {
        return ImmutableList.copyOf(this.lights);
    }

    public float getMaxDistance() {
        return this.maxDistance;
    }

    public Vec3d getCameraPosition() {
        return this.cameraPosition;
    }

    public ICamera getCamera() {
        return this.camera;
    }

    public void add(Light light) {
        // ILightProvider#provideLight is @Nullable and its default implementation returns null,
        // so every provider that only implements gatherLights feeds a null through here.
        // Dropping it silently is correct: "this provider has no light right now" is normal.
        if (light == null) {
            return;
        }
        float radius = light.radius();
        if (this.cameraPosition != null) {
            double dist = MathHelper.sqrt(this.cameraPosition.squareDistanceTo(light.worldX, light.worldY, light.worldZ));
            if (dist > (double) (radius + this.maxDistance)) {
                return;
            }
        }
        if (this.camera != null && !this.camera.isBoundingBoxInFrustum(new AxisAlignedBB(
                light.worldX - radius, light.worldY - radius, light.worldZ - radius,
                light.worldX + radius, light.worldY + radius, light.worldZ + radius))) {
            return;
        }
        this.lights.add(light);
    }

    @Override
    public boolean isCancelable() {
        return false;
    }
}
