package com.fullyx.texturemixer.forge.bootstrap;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Installs the same screen and reload hooks as the Legacy Fabric build, after Forge's deobfuscator. */
public final class MinecraftHooks implements IClassTransformer {
    private static final String CLIENT = "net/minecraft/client/Minecraft";
    private static final String SCREEN = "Lnet/minecraft/client/gui/GuiScreen;";
    private static final String RELOADABLE = "net/minecraft/client/resources/IReloadableResourceManager";
    private static final String PLATFORM = "com/fullyx/texturemixer/legacy/LegacyPlatform";
    private static final String LIST = "Ljava/util/List;";

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytecode) {
        if (!"net.minecraft.client.Minecraft".equals(transformedName) || bytecode == null) return bytecode;
        ClassReader reader = new ClassReader(bytecode);
        ClassNode node = new ClassNode(Opcodes.ASM5);
        reader.accept(node, 0);
        boolean opened = false;
        boolean reloaded = false;
        for (Object entry : node.methods) {
            MethodNode method = (MethodNode) entry;
            if (matches(method.name, "func_147108_a", "displayGuiScreen") && method.desc.equals("(" + SCREEN + ")V")) {
                addScreenHook(method);
                opened = true;
            }
            if (matches(method.name, "func_110436_a", "refreshResources") && method.desc.equals("()V")) {
                reloaded = wrapReload(method);
            }
        }
        if (!opened || !reloaded) {
            throw new IllegalStateException("TextureMixer could not install the Minecraft 1.8.9 Forge client hooks");
        }
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static void addScreenHook(MethodNode method) {
        InsnList prefix = new InsnList();
        prefix.add(new VarInsnNode(Opcodes.ALOAD, 0));
        prefix.add(new VarInsnNode(Opcodes.ALOAD, 1));
        prefix.add(new MethodInsnNode(Opcodes.INVOKESTATIC, PLATFORM, "openScreen",
                "(L" + CLIENT + ";" + SCREEN + ")" + SCREEN, false));
        prefix.add(new VarInsnNode(Opcodes.ASTORE, 1));
        method.instructions.insert(prefix);
    }

    private static boolean wrapReload(MethodNode method) {
        for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) instruction;
            if (call.owner.equals(RELOADABLE) && matches(call.name, "func_110541_a", "reloadResources")
                    && call.desc.equals("(" + LIST + ")V")) {
                method.instructions.insertBefore(call, new VarInsnNode(Opcodes.ALOAD, 0));
                method.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC, PLATFORM, "reload",
                        "(L" + RELOADABLE + ";" + LIST + "L" + CLIENT + ";)V", false));
                return true;
            }
        }
        return false;
    }

    private static boolean matches(String name, String srg, String development) {
        return name.equals(srg) || name.equals(development);
    }
}
