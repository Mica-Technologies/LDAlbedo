package elucent.albedo.asm;

import java.util.List;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public class ASMTransformer implements IClassTransformer {
    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (transformedName.equals("net.minecraft.client.renderer.ChunkRenderContainer")) {
            return this.patchRenderChunkASM(name, basicClass, name.compareTo(transformedName) != 0);
        }
        if (transformedName.equals("net.minecraft.client.renderer.entity.RenderManager")) {
            return this.patchRenderManagerASM(name, basicClass, name.compareTo(transformedName) != 0);
        }
        if (transformedName.equals("net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher")) {
            return this.patchTERendererASM(name, basicClass, name.compareTo(transformedName) != 0);
        }
        if (transformedName.equals("net.minecraft.client.renderer.GlStateManager")) {
            return this.patchGlStateManagerASM(name, basicClass, name.compareTo(transformedName) != 0);
        }
        if (transformedName.equals("net.minecraft.profiler.Profiler")) {
            return this.patchProfilerASM(name, basicClass, name.compareTo(transformedName) != 0);
        }
        if (transformedName.compareTo("net.minecraftforge.client.ForgeHooksClient") == 0) {
            return this.patchForgeHooksASM(name, basicClass, name.compareTo(transformedName) != 0);
        }
        return basicClass;
    }

    public byte[] patchForgeHooksASM(String name, byte[] bytes, boolean obfuscated) {
        String targetMethod;
        String transformTypeName;
        if (obfuscated) {
            targetMethod = "handleCameraTransforms";
            transformTypeName = "Lnet/minecraft/client/renderer/block/model/ItemCameraTransforms$TransformType;";
        } else {
            targetMethod = "handleCameraTransforms";
            transformTypeName = "Lnet/minecraft/client/renderer/block/model/ItemCameraTransforms$TransformType;";
        }
        ClassNode classNode = new ClassNode();
        ClassReader classReader = new ClassReader(bytes);
        classReader.accept(classNode, 0);
        List<MethodNode> methods = classNode.methods;
        for (MethodNode m : methods) {
            if (m.name.compareTo(targetMethod) != 0) continue;
            InsnList code = m.instructions;
            List<LocalVariableNode> vars = m.localVariables;
            int paramloc = -1;
            if (!obfuscated) {
                paramloc = 1;
            }
            for (int i = 0; i < vars.size() && paramloc == -1; ++i) {
                LocalVariableNode p = vars.get(i);
                if (p.desc.compareTo(transformTypeName) != 0) continue;
                paramloc = i;
            }
            if (paramloc <= -1) continue;
            MethodInsnNode method = new MethodInsnNode(184, "elucent/albedo/util/RenderUtil", "setTransform", "(" + transformTypeName + ")V", false);
            code.insertBefore(code.get(2), method);
            code.insertBefore(code.get(2), new VarInsnNode(25, paramloc));
            System.out.println("Successfully patched ForgeHooksClient!");
        }
        ClassWriter writer = new ClassWriter(3);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    public byte[] patchRenderItemASM(String name, byte[] bytes, boolean obfuscated) {
        String targetMethod;
        if (obfuscated) {
            targetMethod = "func_180454_a";
        } else {
            targetMethod = "renderItem";
        }
        ClassNode classNode = new ClassNode();
        ClassReader classReader = new ClassReader(bytes);
        classReader.accept(classNode, 0);
        ClassWriter writer = new ClassWriter(3);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    public byte[] patchGlStateManagerASM(String name, byte[] bytes, boolean obfuscated) {
        String enableLighting;
        String disableLighting;
        if (obfuscated) {
            enableLighting = "func_179145_e";
            disableLighting = "func_179140_f";
        } else {
            enableLighting = "enableLighting";
            disableLighting = "disableLighting";
        }
        ClassNode classNode = new ClassNode();
        ClassReader classReader = new ClassReader(bytes);
        classReader.accept(classNode, 0);
        List<MethodNode> methods = classNode.methods;
        for (MethodNode m : methods) {
            MethodInsnNode method;
            InsnList code;
            if (m.name.compareTo(enableLighting) == 0) {
                code = m.instructions;
                method = new MethodInsnNode(184, "elucent/albedo/util/RenderUtil", "enableLightingUniforms", "()V", false);
                code.insertBefore(code.get(2), method);
                System.out.println("Successfully loaded GlStateManager ASM!");
            }
            if (m.name.compareTo(disableLighting) != 0) continue;
            code = m.instructions;
            method = new MethodInsnNode(184, "elucent/albedo/util/RenderUtil", "disableLightingUniforms", "()V", false);
            code.insertBefore(code.get(2), method);
            System.out.println("Successfully loaded GlStateManager ASM!");
        }
        ClassWriter writer = new ClassWriter(3);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    public byte[] patchTERendererASM(String name, byte[] bytes, boolean obfuscated) {
        String entityName = "Lnet/minecraft/tileentity/TileEntity;";
        String targetMethod;
        String targetDesc = "(Lnet/minecraft/tileentity/TileEntity;FI)V";
        if (obfuscated) {
            targetMethod = "func_180546_a";
        } else {
            targetMethod = "render";
        }
        ClassNode classNode = new ClassNode();
        ClassReader classReader = new ClassReader(bytes);
        classReader.accept(classNode, 0);
        List<MethodNode> methods = classNode.methods;
        for (MethodNode m : methods) {
            if (m.name.compareTo(targetMethod) != 0 || m.desc.compareTo(targetDesc) != 0) continue;
            InsnList code = m.instructions;
            int paramloc = 1;
            AbstractInsnNode returnNode = null;
            for (AbstractInsnNode insn : code.toArray()) {
                if (insn.getOpcode() != 177) continue;
                returnNode = insn;
                break;
            }
            if (returnNode == null) continue;
            MethodInsnNode method = new MethodInsnNode(184, "elucent/albedo/event/RenderTileEntityEvent", "postNewEvent", "(" + entityName + ")V", false);
            code.insertBefore(code.get(2), method);
            code.insertBefore(code.get(2), new VarInsnNode(25, paramloc));
            System.out.println("Successfully loaded TileEntityRendererDispatcher ASM!");
        }
        ClassWriter writer = new ClassWriter(3);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    public byte[] patchRenderManagerASM(String name, byte[] bytes, boolean obfuscated) {
        String entityName = "Lnet/minecraft/entity/Entity;";
        String targetMethod;
        String targetDesc = "(Lnet/minecraft/entity/Entity;DDDFFZ)V";
        if (obfuscated) {
            targetMethod = "func_188391_a";
        } else {
            targetMethod = "renderEntity";
        }
        ClassNode classNode = new ClassNode();
        ClassReader classReader = new ClassReader(bytes);
        classReader.accept(classNode, 0);
        List<MethodNode> methods = classNode.methods;
        for (MethodNode m : methods) {
            if (m.name.compareTo(targetMethod) != 0 || m.desc.compareTo(targetDesc) != 0) continue;
            InsnList code = m.instructions;
            int paramloc = 1;
            AbstractInsnNode returnNode = null;
            for (AbstractInsnNode insn : code.toArray()) {
                if (insn.getOpcode() != 177) continue;
                returnNode = insn;
                break;
            }
            if (returnNode == null) continue;
            MethodInsnNode method = new MethodInsnNode(184, "elucent/albedo/event/RenderEntityEvent", "postNewEvent", "(" + entityName + ")V", false);
            code.insertBefore(code.get(2), method);
            code.insertBefore(code.get(2), new VarInsnNode(25, paramloc));
            System.out.println("Successfully loaded RenderManager ASM!");
        }
        ClassWriter writer = new ClassWriter(3);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    public byte[] patchProfilerASM(String name, byte[] bytes, boolean obfuscated) {
        String targetMethod = obfuscated ? "func_76318_c" : "endStartSection";
        ClassNode classNode = new ClassNode();
        ClassReader classReader = new ClassReader(bytes);
        classReader.accept(classNode, 0);
        List<MethodNode> methods = classNode.methods;
        for (MethodNode m : methods) {
            if (m.name.compareTo(targetMethod) != 0 || m.desc.compareTo("(Ljava/lang/String;)V") != 0) continue;
            InsnList code = m.instructions;
            int paramloc = 1;
            MethodInsnNode method = new MethodInsnNode(184, "elucent/albedo/event/ProfilerStartEvent", "postNewEvent", "(Ljava/lang/String;)V", false);
            code.insertBefore(code.get(2), method);
            code.insertBefore(code.get(2), new VarInsnNode(25, paramloc));
            System.out.println("Successfully loaded Profiler ASM!");
        }
        ClassWriter writer = new ClassWriter(3);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    public byte[] patchRenderChunkASM(String name, byte[] bytes, boolean obfuscated) {
        String renderChunkName = "Lnet/minecraft/client/renderer/chunk/RenderChunk;";
        String targetMethod;
        if (obfuscated) {
            targetMethod = "func_178003_a";
        } else {
            targetMethod = "preRenderChunk";
        }
        ClassNode classNode = new ClassNode();
        ClassReader classReader = new ClassReader(bytes);
        classReader.accept(classNode, 0);
        List<MethodNode> methods = classNode.methods;
        for (MethodNode m : methods) {
            if (m.name.compareTo(targetMethod) != 0 || m.desc.compareTo("(" + renderChunkName + ")V") != 0) continue;
            InsnList code = m.instructions;
            List<LocalVariableNode> vars = m.localVariables;
            int paramloc = -1;
            if (!obfuscated) {
                paramloc = 1;
            }
            for (int i = 0; i < vars.size() && paramloc == -1; ++i) {
                LocalVariableNode p = vars.get(i);
                if (p.desc.compareTo(renderChunkName) != 0) continue;
                paramloc = i;
            }
            AbstractInsnNode returnNode = null;
            for (AbstractInsnNode insn : code.toArray()) {
                if (insn.getOpcode() != 177) continue;
                returnNode = insn;
                break;
            }
            if (returnNode == null || paramloc <= -1) continue;
            code.insertBefore(returnNode, new VarInsnNode(25, paramloc));
            MethodInsnNode method = new MethodInsnNode(184, "elucent/albedo/util/RenderUtil", "renderChunkUniforms", "(" + renderChunkName + ")V", false);
            code.insertBefore(returnNode, method);
            System.out.println("Successfully loaded RenderChunk ASM!");
        }
        ClassWriter writer = new ClassWriter(3);
        classNode.accept(writer);
        return writer.toByteArray();
    }
}
