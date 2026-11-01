package diagnostic.launchwrapper;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Set;
import java.util.Collections;
import java.util.List;

import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

import org.spongepowered.asm.lib.ClassReader;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.lib.tree.MethodNode;
import org.spongepowered.asm.lib.tree.AbstractInsnNode;
import org.spongepowered.asm.lib.tree.MethodInsnNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.transformer.Proxy;
import org.spongepowered.asm.mixin.transformer.Config;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigSource;

/** Real Mixin prepare/apply using unmodified PersonalSpace bytecode; no game is started. */
public final class MixinPipelineProbe {

    private static final String TARGET = "me.eigenraven.personalspace.world.PersonalWorldProvider";

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        MixinEnvironment.getDefaultEnvironment().setSide(
            MixinEnvironment.Side.valueOf(System.getProperty("probe.side", "CLIENT")));
        System.out.println("PIPELINE_SIDE=" + MixinEnvironment.getCurrentEnvironment().getSide());
        Launch.classLoader.loadClass("diagnostic.trigger.EarlyTrigger");
        System.out.println("PIPELINE_EARLY_NEGATIVE=" + cached("negativeResourceCache"));
        if (!mode.equals("early") && cached("negativeResourceCache"))
            throw new AssertionError("Optional target was already probed in the early phase");
        boolean present = !mode.equals("absent");
        if (present) Launch.classLoader.addURL(new File(args[1]).toURI().toURL());
        System.out.println("PIPELINE_MOD_RESOURCE="
            + Launch.classLoader.getResource(TARGET.replace('.', '/') + ".class"));
        if (mode.equals("early")) {
            try {
                Launch.classLoader.loadClass(TARGET);
                throw new AssertionError("Expected original LaunchWrapper failure");
            } catch (ClassNotFoundException failure) {
                failure.printStackTrace(System.out);
                if (!(failure.getCause() instanceof NullPointerException)) throw failure;
                System.out.println("PIPELINE_REPRODUCED=true");
            }
            return;
        }
        // The same config/list registration calls used by GTNHMixins' CONSTRUCTING hook.
        Class<?> loaderClass = Launch.classLoader.loadClass("com.miaokatze.gtsr.main.GTSRLateMixinLoader");
        Object loader = loaderClass.newInstance();
        boolean annotation = false;
        for (java.lang.annotation.Annotation candidate : loaderClass.getAnnotations()) {
            if (candidate.annotationType().getName().equals("com.gtnewhorizon.gtnhmixins.LateMixin")) annotation = true;
        }
        if (!annotation) throw new AssertionError("FML cannot discover the late loader annotation");
        String configName = (String) loaderClass.getMethod("getMixinConfig").invoke(loader);
        @SuppressWarnings("unchecked")
        List<String> selected = (List<String>) loaderClass.getMethod("getMixins", Set.class).invoke(
            loader, present ? Collections.singleton("personalspace") : Collections.emptySet());
        System.out.println("PRODUCTION_LATE_SELECTION=" + selected);
        Config config = Config.create(configName, (IMixinConfigSource) null);
        Field configField = Config.class.getDeclaredField("config");
        configField.setAccessible(true);
        Object inner = configField.get(config);
        Field mixinClasses = inner.getClass().getDeclaredField("mixinClasses");
        mixinClasses.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<String> mixins = (List<String>) mixinClasses.get(inner);
        mixins.addAll(selected);
        Method register = Mixins.class.getDeclaredMethod("registerConfiguration", Config.class);
        register.setAccessible(true);
        register.invoke(null, config);
        Field transformerField = Proxy.class.getDeclaredField("transformer");
        transformerField.setAccessible(true);
        Object transformer = transformerField.get(null);
        Field processorField = transformer.getClass().getDeclaredField("processor");
        processorField.setAccessible(true);
        Object processor = processorField.get(transformer);
        Method select = processor.getClass().getDeclaredMethod("selectConfigs", MixinEnvironment.class);
        select.setAccessible(true);
        select.invoke(processor, MixinEnvironment.getCurrentEnvironment());
        Field extensions = processor.getClass().getDeclaredField("extensions");
        extensions.setAccessible(true);
        Method prepare = processor.getClass().getDeclaredMethod(
            "prepareConfigs", MixinEnvironment.class, extensions.getType());
        prepare.setAccessible(true);
        prepare.invoke(processor, MixinEnvironment.getCurrentEnvironment(), extensions.get(processor));
        if (!present) {
            if (!selected.isEmpty() || cached("negativeResourceCache") || cached("invalidClasses"))
                throw new AssertionError("Absent optional target was probed");
            System.out.println("ABSENT_OPTIONAL_TARGET=PASS");
            return;
        }
        byte[] original = Launch.classLoader.getClassBytes(TARGET);
        Method runTransformers = LaunchClassLoader.class.getDeclaredMethod(
            "runTransformers", String.class, String.class, byte[].class);
        runTransformers.setAccessible(true);
        byte[] transformed = (byte[]) runTransformers.invoke(Launch.classLoader, TARGET, TARGET, original);
        Files.write(Paths.get(args[2]), transformed);
        ClassNode node = new ClassNode();
        new ClassReader(transformed).accept(node, 0);
        int injected = 0;
        int callsites = 0;
        for (MethodNode method : node.methods) {
            if (method.name.contains("gtsr$night") || method.name.contains("gtsr$noSun")
                || method.name.contains("gtsr$midnight")) injected++;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    if (call.owner.equals(node.name) && (call.name.contains("gtsr$night")
                        || call.name.contains("gtsr$noSun") || call.name.contains("gtsr$midnight"))) callsites++;
                }
            }
        }
        System.out.println("ACTUAL_PROVIDER_INJECTED_HANDLERS=" + injected);
        System.out.println("ACTUAL_PROVIDER_INJECTED_CALLS=" + callsites);
        if (injected != 3 || callsites != 4)
            throw new AssertionError("Night mixin was not applied to actual PersonalSpace class");
        if (cached("negativeResourceCache") || cached("invalidClasses"))
            throw new AssertionError("Target cache was poisoned");
        Class<?> resolved = Launch.classLoader.loadClass(TARGET);
        if (resolved.getClassLoader() != Launch.classLoader) throw new AssertionError("Target escaped LaunchWrapper");
        System.out.println("ACTUAL_PROVIDER_CLASS_RESOLVED=" + resolved.getName());
        System.out.println("ACTUAL_PROVIDER_MIXIN_APPLY=PASS");
    }

    private static boolean cached(String fieldName) throws Exception {
        Field field = LaunchClassLoader.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return ((Set<?>) field.get(Launch.classLoader)).contains(TARGET);
    }
}
