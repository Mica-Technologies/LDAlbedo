package elucent.albedo.util;

import elucent.albedo.ConfigManager;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

public class ShaderManager {
    private static final Logger LOGGER = LogManager.getLogger("albedo");

    /**
     * Every program Albedo has compiled. Anything bound that is not in here belongs to somebody
     * else, and Albedo has no business binding over it or unbinding it.
     */
    private static final Set<Integer> OWN_PROGRAMS = new HashSet<>();

    private static ShaderManager currentShader = null;
    private static int currentProgram = -1;
    private static boolean warnedAboutForeignShader = false;
    private final int program;

    public ShaderManager(ResourceLocation shader, IResourceManager resourceManager) {
        this.program = ShaderUtil.loadProgram(
                String.format("%s:shaders/%s.vs", shader.getNamespace(), shader.getPath()),
                String.format("%s:shaders/%s.fs", shader.getNamespace(), shader.getPath()),
                resourceManager);
        OWN_PROGRAMS.add(this.program);
    }

    /**
     * Releases this program on the GPU. The instance is dead afterwards and must not be bound.
     *
     * <p>Removing the id from {@link #OWN_PROGRAMS} is not just tidiness. OpenGL is free to hand
     * a deleted program's id straight back out to the next caller, so a stale entry here would
     * eventually match some other mod's brand-new program and convince us it was one of ours --
     * exactly the misidentification the foreign-shader check exists to prevent.
     */
    public void dispose() {
        if (this.program == 0) {
            return;
        }
        if (currentProgram == this.program) {
            GL20.glUseProgram(0);
            currentProgram = -1;
            currentShader = null;
        }
        OWN_PROGRAMS.remove(this.program);
        GL20.glDeleteProgram(this.program);
    }

    public static ShaderManager getCurrentShader() {
        return currentShader;
    }

    /**
     * The program OpenGL actually has bound right now.
     *
     * <p>{@link #currentProgram} is only a cache of what Albedo last bound, and any mod calling
     * {@code glUseProgram} directly invalidates it without telling us. Asking the driver is the
     * only honest answer, so bind and unbind ask; per-uniform calls stay on the cache because
     * there are hundreds of those a frame and no chance for anyone else to intervene between
     * them.
     */
    private static int boundProgram() {
        return GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
    }

    /**
     * True when some other mod owns the pipeline: something is bound, and it is not ours.
     *
     * <p>BetterPortals rendering its portal framebuffers into the world is the motivating case.
     * Binding over that corrupts their render, and — because {@code glUniform} writes to
     * whatever is actually bound — writing our uniforms while their program is active corrupts
     * their shader's state too.
     */
    private static boolean foreignShaderActive() {
        int bound = boundProgram();
        return bound != 0 && !OWN_PROGRAMS.contains(bound);
    }

    /** Backs off for a foreign shader unless the user has explicitly told us not to. */
    private static boolean shouldYield() {
        if (ConfigManager.ignoreForeignShaders) {
            return false;
        }
        if (!foreignShaderActive()) {
            return false;
        }
        if (!warnedAboutForeignShader) {
            warnedAboutForeignShader = true;
            LOGGER.info("Another mod has its own shader bound (program {}); Albedo is suspending "
                    + "its lighting while that is the case. Set ignoreForeignShaders=true in "
                    + "config/Albedo.cfg to override this.", boundProgram());
        }
        return true;
    }

    public static void stopShader() {
        int bound = boundProgram();
        // Never unbind somebody else's program. The old code reset to 0 off a cached int, so a
        // foreign shader bound mid-pass got torn out from under its owner.
        if (bound != 0 && !OWN_PROGRAMS.contains(bound)) {
            currentProgram = bound;
            currentShader = null;
            return;
        }
        if (bound != 0) {
            GL20.glUseProgram(0);
        }
        currentProgram = 0;
        currentShader = null;
    }

    public static boolean isCurrentShader(ShaderManager shader) {
        return shader != null && currentProgram == shader.program;
    }

    public void useShader() {
        if (ShaderManager.shouldYield()) {
            // Keep the cache honest about not owning the pipeline, so setUniform stays quiet
            // too — otherwise its glUniform calls would land in the foreign program.
            currentProgram = boundProgram();
            currentShader = null;
            return;
        }
        // Bind unconditionally rather than trusting the cache: if anything rebound behind our
        // back, skipping here would leave us rendering through the wrong program.
        GL20.glUseProgram(this.program);
        currentProgram = this.program;
        currentShader = this;
    }

    public void setUniform(String uniform, int value) {
        if (ShaderManager.isCurrentShader(this)) {
            GL20.glUniform1i(GL20.glGetUniformLocation(currentProgram, uniform), value);
        }
    }

    public void setUniform(String uniform, boolean value) {
        if (ShaderManager.isCurrentShader(this)) {
            GL20.glUniform1i(GL20.glGetUniformLocation(currentProgram, uniform), value ? 1 : 0);
        }
    }

    public void setUniform(String uniform, float value) {
        if (ShaderManager.isCurrentShader(this)) {
            GL20.glUniform1f(GL20.glGetUniformLocation(currentProgram, uniform), value);
        }
    }

    public void setUniform(String uniform, int v1, int v2) {
        if (ShaderManager.isCurrentShader(this)) {
            GL20.glUniform2i(GL20.glGetUniformLocation(currentProgram, uniform), v1, v2);
        }
    }

    public void setUniform(String uniform, int v1, int v2, int v3) {
        if (ShaderManager.isCurrentShader(this)) {
            GL20.glUniform3i(GL20.glGetUniformLocation(currentProgram, uniform), v1, v2, v3);
        }
    }

    public void setUniform(String uniform, float v1, float v2) {
        if (ShaderManager.isCurrentShader(this)) {
            GL20.glUniform2f(GL20.glGetUniformLocation(currentProgram, uniform), v1, v2);
        }
    }

    /** Sets a vec3 from a 3-element array, as produced by the camera-relative helpers. */
    public void setUniform(String uniform, float[] vec3) {
        if (ShaderManager.isCurrentShader(this)) {
            GL20.glUniform3f(GL20.glGetUniformLocation(currentProgram, uniform), vec3[0], vec3[1], vec3[2]);
        }
    }

    public void setUniform(String uniform, float v1, float v2, float v3) {
        if (ShaderManager.isCurrentShader(this)) {
            GL20.glUniform3f(GL20.glGetUniformLocation(currentProgram, uniform), v1, v2, v3);
        }
    }

    public void setUniform(String uniform, float v1, float v2, float v3, float v4) {
        if (ShaderManager.isCurrentShader(this)) {
            GL20.glUniform4f(GL20.glGetUniformLocation(currentProgram, uniform), v1, v2, v3, v4);
        }
    }
}
