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

    public static void init(IResourceManager manager) {
        fastLightProgram = new ShaderManager(new ResourceLocation("albedo:fastlight"), manager);
        entityLightProgram = new ShaderManager(new ResourceLocation("albedo:entitylight"), manager);
        depthProgram = new ShaderManager(new ResourceLocation("albedo:depth"), manager);
    }

    // TODO: verify SRG mapping - OpenGlHelper's shader-program wrapper names below (glCreateShader,
    // GL_VERTEX_SHADER/GL_FRAGMENT_SHADER, glAttachShader, glLinkProgram, glCreateProgram, glShaderSource,
    // glCompileShader, GL_COMPILE_STATUS) were recovered from SRG names in the decompiled production jar
    // (func_153195_b, field_153209_q/field_153210_r, func_153178_b, func_153179_f, func_153183_d,
    // func_153169_a, func_153170_c, field_153208_p) with high but not 100% confidence - the compiler will
    // reject any that are wrong, so fix on first build failure here.
    public static int loadProgram(String vsh, String fsh, IResourceManager manager) {
        int vertexShader = ShaderUtil.createShader(vsh, OpenGlHelper.GL_VERTEX_SHADER, manager);
        int fragmentShader = ShaderUtil.createShader(fsh, OpenGlHelper.GL_FRAGMENT_SHADER, manager);
        int program = OpenGlHelper.glCreateProgram();
        OpenGlHelper.glAttachShader(program, vertexShader);
        OpenGlHelper.glAttachShader(program, fragmentShader);
        OpenGlHelper.glLinkProgram(program);
        String s = GL20.glGetProgramInfoLog(program, 32768);
        System.out.println("GL LOG: " + s);
        return program;
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
