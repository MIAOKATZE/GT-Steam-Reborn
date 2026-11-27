// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.machine.recovery;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.lib.ClassReader;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.lib.tree.MethodInsnNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;

/** Applies the production injections to actual dependency classes; does not start Minecraft. */
public final class RecoveryMixinTransformContracts {

    public static void main(String[] args) throws Exception {
        Path output = java.nio.file.Paths.get(args.length == 0 ? "build/recovery-mixin-contract" : args[0]);
        Files.createDirectories(output);
        List<URL> urls = new ArrayList<>();
        urls.add(
            output.toAbsolutePath()
                .toUri()
                .toURL());
        for (String path : System.getProperty("java.class.path")
            .split(File.pathSeparator))
            urls.add(
                new File(path).toURI()
                    .toURL());
        String config = "{\"required\":true,\"minVersion\":\"0.8.5-GTNH\",\"target\":\"@env(DEFAULT)\",\"compatibilityLevel\":\"JAVA_8\","
            + "\"package\":\"com.miaokatze.gtsr.mixin\",\"refmap\":\"mixins.gtsr.refmap.json\","
            + "\"mixins\":[\"StructureCheckerMixin\",\"MTEMultiBlockRedstoneLifecycleMixin\",\"MTEMultiBlockRecoveryMixin\",\"MTEEnhancedRecoveryMixin\",\"MixinGtsrChunkLoadingPolicy\"]}";
        Files.write(output.resolve("mixins.gtsr.recovery-contract.json"), config.getBytes(StandardCharsets.UTF_8));
        Launch.classLoader = new LaunchClassLoader(urls.toArray(new URL[0]));
        Launch.blackboard = new HashMap<>();
        Launch.blackboard.put("TweakClasses", new ArrayList<String>());
        Launch.blackboard.put("Tweaks", new ArrayList<>());
        Launch.blackboard.put("fml.deobfuscatedEnvironment", true);
        for (String prefix : new String[] { "org.spongepowered.", "com.llamalad7.mixinextras.", "org.lwjgl.",
            RecoveryMixinTransformContracts.class.getName() }) Launch.classLoader.addClassLoaderExclusion(prefix);
        Field platform = MixinBootstrap.class.getDeclaredField("platform");
        platform.setAccessible(true);
        platform.set(null, new org.spongepowered.asm.launch.platform.MixinPlatformManager() {

            @Override
            public void init() {}

            @Override
            public Collection<String> getPhaseProviderClasses() {
                return Collections.emptyList();
            }
        });
        MixinBootstrap.init();
        com.llamalad7.mixinextras.MixinExtrasBootstrap.init();
        MixinEnvironment.getDefaultEnvironment()
            .setSide(MixinEnvironment.Side.SERVER);
        Mixins.addConfiguration("mixins.gtsr.recovery-contract.json");
        Method phase = MixinEnvironment.class.getDeclaredMethod("gotoPhase", MixinEnvironment.Phase.class);
        phase.setAccessible(true);
        phase.invoke(null, MixinEnvironment.Phase.DEFAULT);
        Launch.classLoader.registerTransformer("org.spongepowered.asm.mixin.transformer.Proxy");
        IClassTransformer transformer = null;
        // Registering a new proxy deactivates the earlier one installed by MixinBootstrap.
        for (IClassTransformer candidate : Launch.classLoader.getTransformers()) if (candidate.getClass()
            .getName()
            .equals("org.spongepowered.asm.mixin.transformer.Proxy")) transformer = candidate;
        if (transformer == null) throw new AssertionError("Mixin proxy missing");
        if (args.length > 1 && args[1].equals("runtime")) {
            Launch.classLoader.loadClass("cpw.mods.fml.common.Loader")
                .getMethod("injectData", Object[].class)
                .invoke(
                    null,
                    (Object) new Object[] { "7", "10", "99", "99", "1.7.10", "9.05", new File("."),
                        new ArrayList<>() });
            Launch.classLoader.loadClass("com.miaokatze.gtsr.common.machine.recovery.RecoveryMixinRuntimeFixture")
                .getMethod("main", String[].class)
                .invoke(null, (Object) new String[0]);
            return;
        }
        for (String name : new String[] { "gregtech.api.metatileentity.implementations.MTEMultiBlockBase",
            "gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase",
            "gregtech.api.objects.GTChunkManager", "gregtech.api.structure.StructureChecker" }) {
            byte[] original = Launch.classLoader.getClassBytes(name);
            if (original == null) throw new AssertionError("Missing actual target " + name);
            byte[] transformed = transformer.transform(name, name, original);
            if (Arrays.equals(original, transformed)) throw new AssertionError("Untransformed " + name);
            ClassNode node = new ClassNode();
            new ClassReader(transformed).accept(node, 0);
            long handlers = node.methods.stream()
                .filter(method -> method.name.contains("gtsr$"))
                .count();
            if (handlers == 0) throw new AssertionError("Missing actual handlers " + name);
            if (name.endsWith(".MTEEnhancedMultiBlockBase")) {
                ClassNode before = new ClassNode();
                new ClassReader(original).accept(before, 0);
                for (String desc : new String[] { "(Ljava/lang/String;III)Z",
                    "(Ljava/lang/String;IIILjava/util/List;)Z" }) {
                    var source = before.methods.stream()
                        .filter(m -> m.name.equals("checkPiece") && m.desc.equals(desc))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("Missing source overload " + desc));
                    var actual = node.methods.stream()
                        .filter(m -> m.name.equals("checkPiece") && m.desc.equals(desc))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("Missing transformed overload " + desc));
                    long nativeSites = Arrays.stream(source.instructions.toArray())
                        .filter(i -> i instanceof MethodInsnNode && ((MethodInsnNode) i).name.equals("iterate"))
                        .count();
                    long wrappers = Arrays.stream(actual.instructions.toArray())
                        .filter(
                            i -> i instanceof MethodInsnNode
                                && ((MethodInsnNode) i).name.contains("gtsr$loadedStructureOnly"))
                        .count();
                    long remaining = Arrays.stream(actual.instructions.toArray())
                        .filter(i -> i instanceof MethodInsnNode && ((MethodInsnNode) i).name.equals("iterate"))
                        .count();
                    long returns = Arrays.stream(source.instructions.toArray())
                        .filter(i -> i.getOpcode() == Opcodes.IRETURN)
                        .count();
                    long hooks = Arrays.stream(actual.instructions.toArray())
                        .filter(
                            i -> i instanceof MethodInsnNode
                                && ((MethodInsnNode) i).name.contains("gtsr$incompletePiece"))
                        .count();
                    if (nativeSites != 2 || wrappers != nativeSites || remaining != 0 || hooks < returns)
                        throw new AssertionError(
                            "Incomplete overload " + desc
                                + " native="
                                + nativeSites
                                + " wrappers="
                                + wrappers
                                + " unguarded="
                                + remaining
                                + " returnHooks="
                                + hooks
                                + "/"
                                + returns);
                    System.out.println(
                        "PASS actual checkPiece overload " + desc
                            + " guardedIterates="
                            + wrappers
                            + " returnHooks="
                            + hooks);
                }
            }
            if (name.endsWith(".MTEMultiBlockBase")) {
                if (!node.interfaces.contains("com/miaokatze/gtsr/common/machine/recovery/IGtsrStructureRecovery"))
                    throw new AssertionError("Recovery state interface missing");
                boolean nativeCheck = node.methods.stream()
                    .filter(method -> method.name.equals("onPostTick"))
                    .anyMatch(method -> {
                        for (org.spongepowered.asm.lib.tree.AbstractInsnNode instruction : method.instructions
                            .toArray())
                            if (instruction instanceof MethodInsnNode
                                && ((MethodInsnNode) instruction).name.equals("checkStructure")) return true;
                        return false;
                    });
                if (!nativeCheck) throw new AssertionError("Native startup/event check was removed");
            }
            Files.write(output.resolve(name.substring(name.lastIndexOf('.') + 1) + ".class"), transformed);
            System.out.println("PASS transformed " + name + " handlers=" + handlers);
        }
        System.out
            .println("PASS recovery mixin targets=4 with both checkPiece overloads; no Minecraft process or world IO");
    }
}
