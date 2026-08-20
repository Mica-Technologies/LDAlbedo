package elucent.albedo.lighting;

import elucent.albedo.event.GatherLightsEvent;
import net.minecraft.entity.Entity;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public interface ILightProvider {
    void gatherLights(GatherLightsEvent event, Entity context);

    @SideOnly(Side.CLIENT)
    @Deprecated
    default Light provideLight() {
        return null;
    }
}
