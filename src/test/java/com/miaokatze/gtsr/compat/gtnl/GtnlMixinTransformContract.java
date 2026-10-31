package com.miaokatze.gtsr.compat.gtnl;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.lib.ClassReader;
import org.spongepowered.asm.lib.Type;
import org.spongepowered.asm.lib.tree.AnnotationNode;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.lib.tree.MethodInsnNode;
import org.spongepowered.asm.lib.tree.MethodNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Reuses the formal compatibility probe's Mixin bootstrap, without a game or FML lifecycle. */
public final class GtnlMixinTransformContract {

    private GtnlMixinTransformContract() {}

    public static void main(String[] args) throws Exception {
        Path output = args.length == 0 ? java.nio.file.Paths.get("build/gtnl-mixin-contract")
            : java.nio.file.Paths.get(args[0]);
        Files.createDirectories(output);
        List<URL> urls = new ArrayList<>();
        urls.add(
            output.toAbsolutePath()
                .toUri()
                .toURL());
        for (String path : System.getProperty("java.class.path")
            .split(File.pathSeparator)) {
            urls.add(
                new File(path).toURI()
                    .toURL());
        }
        JsonObject config;
        try (InputStream stream = GtnlMixinTransformContract.class.getClassLoader()
            .getResourceAsStream("mixins.gtsr.json")) {
            if (stream == null) throw new AssertionError("mixins.gtsr.json missing");
            config = new JsonParser().parse(new java.io.InputStreamReader(stream, StandardCharsets.UTF_8))
                .getAsJsonObject();
        }
        JsonArray selected = new JsonArray();
        // The shared steam parent supplies the neutral holder interfaces used by the optional mixins.
        selected.add(new com.google.gson.JsonPrimitive("MTESteamMultiBaseMixin"));
        for (JsonElement entry : config.getAsJsonArray("mixins")) {
            if (entry.getAsString()
                .replace('/', '.')
                .startsWith("gtnl.")) selected.add(entry);
        }
        config.add("mixins", selected);
        config.add("client", new JsonArray());
        config.add("server", new JsonArray());
        // Gate behavior has its own isolated-JVM test; this test forces every optional injection to apply.
        config.remove("plugin");
        Files.write(
            output.resolve("mixins.gtsr.gtnl-contract.json"),
            config.toString()
                .getBytes(StandardCharsets.UTF_8));

        Launch.classLoader = new LaunchClassLoader(urls.toArray(new URL[0]));
        Launch.blackboard = new HashMap<>();
        Launch.blackboard.put("TweakClasses", new ArrayList<String>());
        Launch.blackboard.put("Tweaks", new ArrayList<>());
        Launch.blackboard.put("fml.deobfuscatedEnvironment", true);
        for (String prefix : new String[] { "org.spongepowered.", "com.llamalad7.mixinextras.", "org.lwjgl.",
            GtnlMixinTransformContract.class.getName() }) {
            Launch.classLoader.addClassLoaderExclusion(prefix);
        }
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
        Mixins.addConfiguration("mixins.gtsr.gtnl-contract.json");
        Method phase = MixinEnvironment.class.getDeclaredMethod("gotoPhase", MixinEnvironment.Phase.class);
        phase.setAccessible(true);
        phase.invoke(null, MixinEnvironment.Phase.DEFAULT);
        Launch.classLoader.registerTransformer("org.spongepowered.asm.mixin.transformer.Proxy");
        IClassTransformer transformer = null;
        for (IClassTransformer registered : Launch.classLoader.getTransformers()) {
            if (registered.getClass()
                .getName()
                .equals("org.spongepowered.asm.mixin.transformer.Proxy")) {
                transformer = registered;
            }
        }
        if (transformer == null) throw new AssertionError("registered Mixin proxy missing");

        Set<String> targets = new LinkedHashSet<>();
        String mixinPackage = config.get("package")
            .getAsString();
        for (JsonElement entry : selected) {
            String name = mixinPackage + "."
                + entry.getAsString()
                    .replace('/', '.');
            ClassNode mixin = node(Launch.classLoader.getClassBytes(name));
            List<AnnotationNode> annotations = new ArrayList<>();
            if (mixin.visibleAnnotations != null) annotations.addAll(mixin.visibleAnnotations);
            if (mixin.invisibleAnnotations != null) annotations.addAll(mixin.invisibleAnnotations);
            for (AnnotationNode annotation : annotations) {
                if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) continue;
                for (int i = 0; i < annotation.values.size(); i += 2) {
                    String key = (String) annotation.values.get(i);
                    if (!key.equals("value") && !key.equals("targets")) continue;
                    for (Object target : (List<?>) annotation.values.get(i + 1)) {
                        targets.add(target instanceof Type type ? type.getClassName() : (String) target);
                    }
                }
            }
        }
        if (!targets
            .contains("com.miaokatze.gtsr.common.machine.MTESiemensMartinFurnace$SiemensMartinHatchElement$4")) {
            throw new AssertionError("Siemens source count must target the dispatched constant subclass bridge");
        }
        Set<String> sharedControllers = new LinkedHashSet<>(
            java.util.Arrays.asList(
                "MTEAirCompressor",
                "MTEAtmosphericCentrifuge",
                "MTELargeSteamFurnace",
                "MTEVeinSteamPyrolyzer",
                "MTECrustSteamBorer",
                "MTESteamFluidDrill",
                "MTESingularityDrillingHub"));
        for (String target : targets) {
            byte[] before = Launch.classLoader.getClassBytes(target);
            if (before == null) throw new AssertionError("target missing: " + target);
            byte[] after = transformer.transform(target, target, before);
            Path artifact = output.resolve("transformed")
                .resolve(target.replace('.', '/') + ".class");
            Files.createDirectories(artifact.getParent());
            Files.write(artifact, after);
            if (java.util.Arrays.equals(before, after))
                throw new AssertionError("Mixin proxy returned unchanged class bytes: " + target);
            ClassNode transformed = node(after);
            long handlers = transformed.methods.stream()
                .filter(method -> method.name.contains("gtsr$"))
                .count();
            if (handlers == 0) throw new AssertionError("optional injection was not merged: " + target);
            if (sharedControllers.remove(target.substring(target.lastIndexOf('.') + 1))) {
                boolean formed = transformed.methods.stream()
                    .filter(method -> method.name.equals("checkMachine"))
                    .anyMatch(
                        method -> callIndex(method, "gtsr$hasSteam") >= 0 || callIndex(method, "gtsr$countSteam") >= 0);
                if (!formed)
                    throw new AssertionError("shared steam structure received no actual source-count gate: " + target);
                System.out.println("PASS actual shared steam-source structure gate " + target);
            }
            if (target.endsWith(".MTESteamMultiBlockBase")) {
                MethodNode adder = method(
                    transformed,
                    "addToMachineList",
                    "(Lgregtech/api/interfaces/tileentity/IGregTechTileEntity;I)Z");
                if (callIndex(adder, "gtsr$addToMachineListHead") < 0) {
                    throw new AssertionError("native adder does not enter the common acceptance callback");
                }
                MethodNode acceptance = methodContaining(transformed, "gtsr$addToMachineListHead");
                assertAcceptanceHook(acceptance);
                MethodNode nativeFluidAdder = method(
                    transformed,
                    "addSteamInputFluidHatch",
                    "(Lgregtech/api/interfaces/tileentity/IGregTechTileEntity;I)Z");
                if (callIndex(nativeFluidAdder, "gtsr$onAddSteamInputFluidHatch") < 0) {
                    throw new AssertionError("native steam-fluid adder does not enter its acceptance callback");
                }
                assertAcceptanceHook(methodContaining(transformed, "gtsr$onAddSteamInputFluidHatch"));
                MethodNode compatibilityHook = method(
                    transformed,
                    "gtsr$addAdditionalSteamSource",
                    "(Lgregtech/api/interfaces/tileentity/IGregTechTileEntity;I)Z");
                if (!calls(compatibilityHook).stream()
                    .anyMatch(call -> call.contains("com/miaokatze/gtsr/compat/gtnl/PipelessSteamSupport.add("))) {
                    throw new AssertionError("compatibility hook does not reach actual pipeless hatch registration");
                }
                System.out.println("PASS actual common acceptance hook and pipeless registration dispatch");
            }
            if (target.endsWith(".MTESingularityMachineBase")) {
                preserveCalls(node(before), transformed, "processAggregationCycle");
                preserveCalls(node(before), transformed, "entanglementInputRows");
            }
            if (target.endsWith(".MTEKineticProcessingArray")) {
                preserveCalls(node(before), transformed, "onRunningTick");
            }
            if (target.endsWith("$SiemensMartinHatchElement$4")) {
                MethodNode bridge = method(transformed, "count", "(Ljava/lang/Object;)J");
                if (callIndex(bridge, "gtsr$countSources") < 0) {
                    throw new AssertionError(
                        "Siemens structure count must use the constant subclass's real Object bridge");
                }
                System.out.println("PASS actual Siemens constant count(Object) bridge includes extra sources");
            }
            if (target.endsWith(".MTEMegaSteamTurbineArray")) {
                preserveCalls(node(before), transformed, "checkProcessing");
                preserveCalls(node(before), transformed, "outputCoolingProduct");
                preserveCalls(node(before), transformed, "onRunningTick");
            }
            System.out.println("PASS transformed " + target + " handlers=" + handlers);
        }
        if (!sharedControllers.isEmpty())
            throw new AssertionError("shared controllers not transformed: " + sharedControllers);
        System.out
            .println("PASS GTNL actual Mixin application: " + targets.size() + " target classes; no game launched");
    }

    private static ClassNode node(byte[] bytes) {
        if (bytes == null) throw new AssertionError("compiled mixin missing");
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static void preserveCalls(ClassNode before, ClassNode after, String name) {
        boolean checked = false;
        for (MethodNode original : before.methods) {
            if (!original.name.equals(name)) continue;
            MethodNode transformed = method(after, name, original.desc);
            if (!calls(original).equals(calls(transformed))) {
                throw new AssertionError("native EU/raw-material/turbine calls changed: " + before.name + "#" + name);
            }
            checked = true;
        }
        if (!checked) throw new AssertionError("native behavior method missing: " + before.name + "#" + name);
        System.out.println("PASS native call sequence retained " + before.name + "#" + name);
    }

    private static List<String> calls(MethodNode method) {
        List<String> calls = new ArrayList<>();
        for (var instruction : method.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode call) {
                calls.add(call.getOpcode() + ":" + call.owner + "." + call.name + call.desc);
            }
        }
        return calls;
    }

    private static MethodNode method(ClassNode node, String name, String descriptor) {
        return node.methods.stream()
            .filter(method -> method.name.equals(name) && method.desc.equals(descriptor))
            .findFirst()
            .orElseThrow(() -> new AssertionError("method missing: " + node.name + "#" + name + descriptor));
    }

    private static MethodNode methodContaining(ClassNode node, String name) {
        return node.methods.stream()
            .filter(method -> method.name.contains(name))
            .findFirst()
            .orElseThrow(() -> new AssertionError("injected callback missing: " + node.name + "#" + name));
    }

    private static void assertAcceptanceHook(MethodNode callback) {
        int hook = callIndex(callback, "gtsr$addAdditionalSteamSource");
        int lastResult = -1;
        int index = 0;
        for (var instruction : callback.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode call && call.name.equals("setReturnValue")) lastResult = index;
            index++;
        }
        if (hook < 0 || lastResult <= hook) {
            throw new AssertionError(
                "common unknown-hatch result must consult the neutral acceptance hook: " + callback.name);
        }
    }

    private static int callIndex(MethodNode method, String name) {
        int index = 0;
        for (var instruction : method.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode call && call.name.contains(name)) return index;
            index++;
        }
        return -1;
    }
}
