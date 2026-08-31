package elucent.albedo.util;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.resource.IResourceType;
import net.minecraftforge.client.resource.ISelectiveResourceReloadListener;
import net.minecraftforge.client.resource.VanillaResourceType;
import org.apache.commons.io.IOUtils;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ARBShaderObjects;
import org.lwjgl.opengl.GL20;

public class ShaderUtil implements ISelectiveResourceReloadListener {
    public static ShaderManager fastLightProgram;
    public static ShaderManager entityLightProgram;
    public static ShaderManager depthProgram;

    /**
     * Compiles the three programs, releasing any previous set first.
     *
     * <p>This runs again on every shader resource reload -- F3+T, a resource pack change, any
     * mod that reloads resources -- so without the disposal the old programs would be dropped
     * on the floor still allocated, leaking a little GPU memory each time and leaving their ids
     * permanently in the "ours" set.
     */
    public static void init(IResourceManager manager) {
        disposeProgram(fastLightProgram);
        disposeProgram(entityLightProgram);
        disposeProgram(depthProgram);
        fastLightProgram = new ShaderManager(new ResourceLocation("albedo:fastlight"), manager);
        entityLightProgram = new ShaderManager(new ResourceLocation("albedo:entitylight"), manager);
        depthProgram = new ShaderManager(new ResourceLocation("albedo:depth"), manager);
    }

    private static void disposeProgram(ShaderManager shader) {
        if (shader != null) {
            shader.dispose();
        }
    }

    public static int loadProgram(String vsh, String fsh, IResourceManager manager) {
        int vertexShader = ShaderUtil.createShader(vsh, OpenGlHelper.GL_VERTEX_SHADER, manager);
        int fragmentShader = ShaderUtil.createShader(fsh, OpenGlHelper.GL_FRAGMENT_SHADER, manager);
        int program = OpenGlHelper.glCreateProgram();
        OpenGlHelper.glAttachShader(program, vertexShader);
        OpenGlHelper.glAttachShader(program, fragmentShader);
        OpenGlHelper.glLinkProgram(program);
        String s = GL20.glGetProgramInfoLog(program, 32768);
        System.out.println("GL LOG: " + s);
        // The linked program holds its own reference to each shader object, so marking them for
        // deletion now does not unlink anything -- it just hands the last reference to the
        // program, and they go when it does. Without this they outlive every program ever built.
        deleteShader(vertexShader);
        deleteShader(fragmentShader);
        return program;
    }

    private static void deleteShader(int shader) {
        if (shader != 0) {
            OpenGlHelper.glDeleteShader(shader);
        }
    }

    public static int createShader(String filename, int shaderType, IResourceManager manager) {
        int shader = OpenGlHelper.glCreateShader(shaderType);
        if (shader == 0) {
            return 0;
        }
        try {
            byte[] abyte = IOUtils.toByteArray(new BufferedInputStream(manager.getResource(new ResourceLocation(filename)).getInputStream()));
            ByteBuffer buffer = BufferUtils.createByteBuffer(abyte.length);
            buffer.put(abyte);
            buffer.position(0);
            OpenGlHelper.glShaderSource(shader, buffer);
        } catch (Exception e) {
            e.printStackTrace();
        }
        OpenGlHelper.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, OpenGlHelper.GL_COMPILE_STATUS) == 0) {
            throw new RuntimeException("Error creating shader: " + ShaderUtil.getLogInfo(shader));
        }
        return shader;
    }

    public static String getLogInfo(int obj) {
        return ARBShaderObjects.glGetInfoLogARB(obj, ARBShaderObjects.glGetObjectParameteriARB(obj, 35716));
    }

    public static String readFileAsString(String filename, IResourceManager manager) throws Exception {
        System.out.println("Loading shader [" + filename + "]...");
        InputStream in = null;
        try {
            IResource resource = manager.getResource(new ResourceLocation(filename));
            in = resource.getInputStream();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        String s = "";
        if (in != null) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                s = reader.lines().collect(Collectors.joining("\n"));
            }
        }
        return s;
    }

    @Override
    public void onResourceManagerReload(IResourceManager resourceManager, Predicate<IResourceType> resourcePredicate) {
        if (resourcePredicate.test(VanillaResourceType.SHADERS)) {
            ShaderUtil.init(resourceManager);
        }
    }
}
