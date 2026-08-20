package elucent.albedo.event;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.Event;

public class ProfilerStartEvent extends Event {
    private final String section;

    private ProfilerStartEvent(String section) {
        this.section = section;
    }

    public static void postNewEvent(String section) {
        MinecraftForge.EVENT_BUS.post(new ProfilerStartEvent(section));
    }

    public String getSection() {
        return this.section;
    }

    @Override
    public boolean isCancelable() {
        return false;
    }
}
