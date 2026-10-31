package com.miaokatze.gtsr.compat.gtnl;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.lib.ClassReader;
import org.spongepowered.asm.lib.Type;
import org.spongepowered.asm.lib.tree.AbstractInsnNode;
import org.spongepowered.asm.lib.tree.AnnotationNode;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.lib.tree.FieldInsnNode;
import org.spongepowered.asm.lib.tree.MethodInsnNode;
import org.spongepowered.asm.lib.tree.MethodNode;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Checks compiled injection selectors against actual dependency bytecode; does not launch Forge. */
public final class GtnlMixinShapeContract {

    private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static int checked;

    private GtnlMixinShapeContract() {}

    public static void main(String[] args) throws Exception {
        JsonObject config;
        try (InputStream stream = resource("mixins.gtsr.json")) {
            config = new JsonParser()
                .parse(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8))
                .getAsJsonObject();
        }
        String prefix = config.get("package")
            .getAsString();
        int mixins = 0;
        for (String section : java.util.Arrays.asList("mixins", "client", "server")) {
            JsonArray entries = config.getAsJsonArray(section);
            if (entries == null) continue;
            for (JsonElement entry : entries) {
                String suffix = entry.getAsString()
                    .replace('/', '.');
                if (!suffix.startsWith("gtnl.")) continue;
                inspect(prefix + "." + suffix);
                mixins++;
            }
        }
        require(mixins > 1, "enhancement and pipeless injections must both be registered");
        require(checked > 0, "no injection selectors checked");
        System.out.println("PASS GTNL compiled injection shapes: " + mixins + " mixins, " + checked + " selectors");
    }

    private static void inspect(String name) throws Exception {
        ClassNode mixin = read(name);
        AnnotationNode declaration = annotation(mixin.visibleAnnotations, MIXIN);
        if (declaration == null) declaration = annotation(mixin.invisibleAnnotations, MIXIN);
        require(declaration != null, "missing @Mixin: " + name);
        List<String> targets = new ArrayList<>();
        for (Object target : list(value(declaration, "value"))) targets.add(((Type) target).getClassName());
        for (Object target : list(value(declaration, "targets"))) targets.add((String) target);
        require(!targets.isEmpty(), "missing targets: " + name);
        for (String target : targets) {
            ClassNode targetClass = read(target);
            for (MethodNode handler : mixin.methods) {
                List<AnnotationNode> annotations = new ArrayList<>();
                if (handler.visibleAnnotations != null) annotations.addAll(handler.visibleAnnotations);
                if (handler.invisibleAnnotations != null) annotations.addAll(handler.invisibleAnnotations);
                for (AnnotationNode injection : annotations) {
                    if (!injection.desc.startsWith("Lorg/spongepowered/asm/mixin/injection/")
                        && !injection.desc.startsWith("Lcom/llamalad7/mixinextras/injector/")) continue;
                    Object selectors = value(injection, "method");
                    if (selectors == null) continue;
                    boolean optional = Integer.valueOf(0)
                        .equals(value(injection, "require"));
                    for (Object selector : list(selectors)) {
                        List<MethodNode> methods = matching(targetClass, (String) selector);
                        String label = name + "#" + handler.name + " -> " + target + "#" + selector;
                        if (methods.isEmpty() && optional) continue;
                        require(!methods.isEmpty(), "target method missing: " + label);
                        for (Object at : list(value(injection, "at"))) {
                            if (at instanceof AnnotationNode point) checkPoint(methods, point, label, optional);
                        }
                        checked++;
                    }
                }
            }
        }
    }

    private static void checkPoint(List<MethodNode> methods, AnnotationNode at, String label, boolean optional) {
        String kind = (String) value(at, "value");
        if (!"INVOKE".equals(kind) && !"FIELD".equals(kind)) return;
        String target = (String) value(at, "target");
        require(target != null && target.startsWith("L") && target.contains(";"), "missing @At target: " + label);
        int separator = target.indexOf(';');
        String owner = target.substring(1, separator);
        String member = target.substring(separator + 1);
        int matches = 0;
        for (MethodNode method : methods) {
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction instanceof MethodInsnNode call && owner.equals(call.owner)
                    && member.equals(call.name + call.desc)) matches++;
                if (instruction instanceof FieldInsnNode field && owner.equals(field.owner)
                    && member.equals(field.name + ":" + field.desc)) matches++;
            }
        }
        if (matches == 0 && optional) return;
        Integer ordinal = (Integer) value(at, "ordinal");
        require(
            matches > 0 && (ordinal == null || ordinal < 0 || ordinal < matches),
            "@At does not match actual instructions: " + label + " / " + target + " / ordinal=" + ordinal);
    }

    private static List<MethodNode> matching(ClassNode target, String selector) {
        List<MethodNode> methods = new ArrayList<>();
        for (MethodNode method : target.methods) {
            if (selector.equals(method.name) || selector.equals(method.name + method.desc)) methods.add(method);
        }
        return methods;
    }

    private static AnnotationNode annotation(List<AnnotationNode> annotations, String descriptor) {
        if (annotations != null) {
            for (AnnotationNode annotation : annotations) if (descriptor.equals(annotation.desc)) return annotation;
        }
        return null;
    }

    private static Object value(AnnotationNode annotation, String key) {
        if (annotation.values != null) {
            for (int i = 0; i < annotation.values.size(); i += 2) {
                if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
            }
        }
        return null;
    }

    private static List<?> list(Object value) {
        return value == null ? java.util.Collections.emptyList()
            : value instanceof List<?>values ? values : java.util.Collections.singletonList(value);
    }

    private static ClassNode read(String name) throws Exception {
        try (InputStream stream = resource(name.replace('.', '/') + ".class")) {
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }

    private static InputStream resource(String name) {
        InputStream stream = GtnlMixinShapeContract.class.getClassLoader()
            .getResourceAsStream(name);
        require(stream != null, "classpath resource missing: " + name);
        return stream;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
