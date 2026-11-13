// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.client.network;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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
import org.spongepowered.asm.lib.tree.AbstractInsnNode;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.lib.tree.FieldInsnNode;
import org.spongepowered.asm.lib.tree.FieldNode;
import org.spongepowered.asm.lib.tree.MethodInsnNode;
import org.spongepowered.asm.lib.tree.MethodNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

/** Applies the real two Mixins to cached Forge-1614 classes without starting Minecraft or opening a channel. */
public final class LoginIdentityMixinContracts {

    private static final String ACCESS = "LoginSuccessProfileAccess", HOOK = "NetHandlerLoginClientIdentityMixin";
    private static int checks;

    private LoginIdentityMixinContracts() {}

    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
        checks++;
        System.out.println("PASS " + name);
    }

    public static void main(String[] args) throws Exception {
        Path output = args.length == 0 ? Paths.get("build/login-identity-mixin-contract") : Paths.get(args[0]);
        boolean srg = args.length > 1 && args[1].equals("srg");
        String loginMethod = srg ? "func_147390_a" : "handleLoginSuccess";
        System.out
            .println("Verifying actual Forge 1614 " + (srg ? "reobfuscated SRG" : "MCP development") + " classes");
        check(!Boolean.getBoolean("mixin.env.disableRefMap"), "real generated refmap remains enabled");
        check(
            Boolean.getBoolean("mixin.env.remapRefMap") == !srg,
            srg ? "production SRG refmap is applied without development remapping"
                : "development uses the official SRG-to-MCP refmap remapper");
        if (srg) {
            Path archive = Paths.get(args[2])
                .toAbsolutePath();
            for (String resource : new String[] { "com/miaokatze/gtsr/mixin/" + ACCESS + ".class",
                "com/miaokatze/gtsr/mixin/" + HOOK + ".class", "mixins.gtsr.refmap.json" }) {
                URL url = LoginIdentityMixinContracts.class.getClassLoader()
                    .getResource(resource);
                check(
                    url != null && url.openConnection() instanceof JarURLConnection
                        && Paths.get(
                            ((JarURLConnection) url.openConnection()).getJarFileURL()
                                .toURI())
                            .equals(archive),
                    "SRG contract uses the actual ordinary runtime archive: " + resource);
            }
        }
        Files.createDirectories(output);
        JsonObject config = json("mixins.gtsr.json");
        check(count(config.getAsJsonArray("client"), ACCESS) == 1, "accessor registered exactly once on client");
        check(count(config.getAsJsonArray("client"), HOOK) == 1, "login hook registered exactly once on client");
        check(
            count(config.getAsJsonArray("mixins"), ACCESS) + count(config.getAsJsonArray("mixins"), HOOK)
                + count(config.getAsJsonArray("server"), ACCESS)
                + count(config.getAsJsonArray("server"), HOOK) == 0,
            "dedicated config never selects the client identity Mixins");
        JsonObject mappings = json(
            config.get("refmap")
                .getAsString()).getAsJsonObject("mappings");
        check(
            mappings.getAsJsonObject("com/miaokatze/gtsr/mixin/" + HOOK)
                .get("handleLoginSuccess(Lnet/minecraft/network/login/server/S02PacketLoginSuccess;)V")
                .getAsString()
                .equals(
                    "Lnet/minecraft/client/network/NetHandlerLoginClient;func_147390_a(Lnet/minecraft/network/login/server/S02PacketLoginSuccess;)V"),
            "hook refmap preserves the exact 1.7.10 handleLoginSuccess SRG descriptor");
        check(
            mappings.getAsJsonObject("com/miaokatze/gtsr/mixin/" + ACCESS)
                .get("field_149602_a")
                .getAsString()
                .equals("field_149602_a:Lcom/mojang/authlib/GameProfile;"),
            "accessor refmap preserves S02's actual private GameProfile descriptor");
        // This @Shadow field has the same MCP/SRG name, so the processor need not emit a refmap entry.
        // Check the actual official field and the merged callback's GETFIELD below instead.
        ClassNode officialHandler = resourceNode("net/minecraft/client/network/NetHandlerLoginClient.class");
        MethodNode officialSuccess = method(
            officialHandler,
            loginMethod,
            "(Lnet/minecraft/network/login/server/S02PacketLoginSuccess;)V");
        int officialHandshake = 0;
        for (AbstractInsnNode instruction : officialSuccess.instructions.toArray())
            if (instruction instanceof MethodInsnNode
                && ((MethodInsnNode) instruction).owner.equals("cpw/mods/fml/common/network/internal/FMLNetworkHandler")
                && ((MethodInsnNode) instruction).name.equals("fmlClientHandshake")) officialHandshake++;
        check(officialHandshake == 1, "input is Forge-patched login handler with one real FML handshake");
        boolean managerField = false;
        for (FieldNode field : officialHandler.fields) managerField |= field.name.equals("field_147393_d")
            && field.desc.equals("Lnet/minecraft/network/NetworkManager;")
            && (field.access & (Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL)) == (Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL);
        check(managerField, "official login handler retains the exact private final NetworkManager shadow field");

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
        JsonArray selected = new JsonArray();
        selected.add(new JsonPrimitive(ACCESS));
        selected.add(new JsonPrimitive(HOOK));
        config.add("mixins", new JsonArray());
        config.add("client", selected);
        config.add("server", new JsonArray());
        config.remove("plugin");
        Files.write(
            output.resolve("mixins.gtsr.login-contract.json"),
            config.toString()
                .getBytes(StandardCharsets.UTF_8));
        Launch.classLoader = new LaunchClassLoader(urls.toArray(new URL[0]));
        Launch.blackboard = new HashMap<>();
        Launch.blackboard.put("TweakClasses", new ArrayList<String>());
        Launch.blackboard.put("Tweaks", new ArrayList<>());
        Launch.blackboard.put("fml.deobfuscatedEnvironment", !srg);
        for (String prefix : new String[] { "org.spongepowered.", "org.lwjgl.",
            LoginIdentityMixinContracts.class.getName() }) Launch.classLoader.addClassLoaderExclusion(prefix);
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
        MixinEnvironment.getDefaultEnvironment()
            .setSide(MixinEnvironment.Side.CLIENT);
        Mixins.addConfiguration("mixins.gtsr.login-contract.json");
        Method phase = MixinEnvironment.class.getDeclaredMethod("gotoPhase", MixinEnvironment.Phase.class);
        phase.setAccessible(true);
        phase.invoke(null, MixinEnvironment.Phase.DEFAULT);
        Launch.classLoader.registerTransformer("org.spongepowered.asm.mixin.transformer.Proxy");
        IClassTransformer transformer = null;
        for (IClassTransformer candidate : Launch.classLoader.getTransformers()) if (candidate.getClass()
            .getName()
            .equals("org.spongepowered.asm.mixin.transformer.Proxy")) transformer = candidate;
        check(transformer != null, "real Mixin transformer registered");
        ClassNode packet = transform(transformer, "net.minecraft.network.login.server.S02PacketLoginSuccess", output);
        check(
            packet.interfaces.contains("com/miaokatze/gtsr/mixin/" + ACCESS),
            "real S02 packet implements the client profile accessor");
        MethodNode getter = method(packet, "gtsr$getLoginProfile", "()Lcom/mojang/authlib/GameProfile;");
        boolean readsProfile = false;
        for (AbstractInsnNode instruction : getter.instructions.toArray()) if (instruction instanceof FieldInsnNode) {
            FieldInsnNode field = (FieldInsnNode) instruction;
            readsProfile |= field.owner.equals(packet.name) && field.desc.equals("Lcom/mojang/authlib/GameProfile;")
                && (field.name.equals("field_149602_a") || field.name.equals("profile"));
        }
        check(readsProfile, "generated accessor reads the actual private S02 GameProfile descriptor");
        ClassNode handler = transform(transformer, "net.minecraft.client.network.NetHandlerLoginClient", output);
        MethodNode success = method(
            handler,
            loginMethod,
            "(Lnet/minecraft/network/login/server/S02PacketLoginSuccess;)V");
        int hook = -1, hooks = 0, handshake = -1, cursor = 0;
        for (AbstractInsnNode instruction : success.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.name.contains("gtsr$captureLoginIdentity")) {
                    hook = cursor;
                    hooks++;
                }
                if (call.name.equals("fmlClientHandshake")) handshake = cursor;
            }
            cursor++;
        }
        check(
            hooks == 1 && hook >= 0 && handshake > hook,
            "exactly one injected capture actually precedes Forge's modded client handshake");
        boolean bindsRealManager = false, readsPacketProfile = false, captures = false;
        for (MethodNode method : handler.methods) if (method.name.contains("gtsr$captureLoginIdentity")) {
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction instanceof FieldInsnNode) {
                    FieldInsnNode field = (FieldInsnNode) instruction;
                    bindsRealManager |= field.getOpcode() == Opcodes.GETFIELD && field.owner.equals(handler.name)
                        && field.name.equals("field_147393_d")
                        && field.desc.equals("Lnet/minecraft/network/NetworkManager;");
                }
                if (instruction instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    readsPacketProfile |= call.owner.equals("com/miaokatze/gtsr/mixin/" + ACCESS)
                        && call.name.equals("gtsr$getLoginProfile")
                        && call.desc.equals("()Lcom/mojang/authlib/GameProfile;");
                    captures |= call.owner.equals("com/miaokatze/gtsr/client/network/ClientRecipientIdentity")
                        && call.name.equals("recordLogin")
                        && call.desc
                            .equals("(Lnet/minecraft/network/NetworkManager;Lcom/mojang/authlib/GameProfile;)V");
                }
            }
        }
        check(
            bindsRealManager && readsPacketProfile && captures,
            "merged callback binds S02 accessor profile to the actual login NetworkManager");
        System.out.println("PASS login identity " + (srg ? "SRG" : "MCP") + " Mixin contracts: " + checks);
    }

    private static ClassNode transform(IClassTransformer transformer, String target, Path output) throws Exception {
        byte[] before = Launch.classLoader.getClassBytes(target);
        check(before != null, "cached official class present: " + target);
        byte[] after = transformer.transform(target, target, before);
        check(!java.util.Arrays.equals(before, after), "actual Mixin changes official class: " + target);
        Path file = output.resolve("transformed")
            .resolve(target.replace('.', '/') + ".class");
        Files.createDirectories(file.getParent());
        Files.write(file, after);
        ClassNode node = new ClassNode();
        new ClassReader(after).accept(node, 0);
        return node;
    }

    private static MethodNode method(ClassNode node, String name, String descriptor) {
        for (MethodNode method : node.methods)
            if (method.name.equals(name) && method.desc.equals(descriptor)) return method;
        throw new AssertionError("Missing actual method " + node.name + "." + name + descriptor);
    }

    private static JsonObject json(String name) throws Exception {
        try (InputStream input = LoginIdentityMixinContracts.class.getClassLoader()
            .getResourceAsStream(name)) {
            if (input == null) throw new AssertionError("missing classpath resource: " + name);
            return new JsonParser().parse(new InputStreamReader(input, StandardCharsets.UTF_8))
                .getAsJsonObject();
        }
    }

    private static ClassNode resourceNode(String name) throws Exception {
        try (InputStream input = LoginIdentityMixinContracts.class.getClassLoader()
            .getResourceAsStream(name)) {
            if (input == null) throw new AssertionError("missing official class resource: " + name);
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }

    private static int count(JsonArray entries, String wanted) {
        int count = 0;
        for (JsonElement entry : entries) if (wanted.equals(entry.getAsString())) count++;
        return count;
    }
}
