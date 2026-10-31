package com.miaokatze.gtsr.compat.gtnl;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Fresh class loaders exercise the real early Forge configuration reader and optional Mixin gate. */
public final class GtnlSelectionContract {

    private static final String PREFIX = "com.miaokatze.gtsr.mixin.gtnl.";

    private GtnlSelectionContract() {}

    public static void main(String[] args) throws Exception {
        Path output = args.length == 0 ? java.nio.file.Paths.get("build/gtnl-selection-contract")
            : java.nio.file.Paths.get(args[0]);
        Files.createDirectories(output);
        output = Files.createTempDirectory(output, "run-");
        check(output.resolve("defaults"), true, null, null, false, false);
        check(output.resolve("off"), true, false, false, false, false);
        check(output.resolve("enhancement"), true, true, false, true, false);
        check(output.resolve("pipeless"), true, false, true, false, true);
        check(output.resolve("both"), true, true, true, true, true);
        check(output.resolve("absent"), false, true, true, false, false);
        Path legacy = output.resolve("legacy");
        Files.createDirectories(legacy.resolve("config"));
        Files.write(legacy.resolve("config/gtsr.cfg"), configuration(true, false).getBytes(StandardCharsets.UTF_8));
        check(legacy, true, null, null, true, false);
        Path currentWins = output.resolve("current-wins");
        Files.createDirectories(currentWins.resolve("config"));
        Files.write(currentWins.resolve("config/gtsr.cfg"), configuration(true, true).getBytes(StandardCharsets.UTF_8));
        check(currentWins, true, false, false, false, false);
        System.out.println(
            "PASS optional Mixin selection: disk flags, defaults, independent switches, absence and legacy precedence");
    }

    private static void check(Path game, boolean present, Boolean enhancement, Boolean pipeless,
        boolean expectedEnhancement, boolean expectedPipeless) throws Exception {
        game = game.toAbsolutePath();
        Files.createDirectories(game);
        Path config = game.resolve("config/gtsr/gtsr.cfg");
        if (enhancement != null) {
            Files.createDirectories(config.getParent());
            Files.write(config, configuration(enhancement, pipeless).getBytes(StandardCharsets.UTF_8));
        }
        byte[] before = Files.exists(config) ? Files.readAllBytes(config) : null;
        List<URL> urls = new ArrayList<>();
        for (String path : System.getProperty("java.class.path")
            .split(File.pathSeparator)) {
            urls.add(
                java.nio.file.Paths.get(path)
                    .toUri()
                    .toURL());
        }
        try (URLClassLoader loader = new URLClassLoader(
            urls.toArray(new URL[0]),
            ClassLoader.getSystemClassLoader()
                .getParent()) {

            @Override
            public URL getResource(String name) {
                return !present && name.startsWith("com/science/gtnl/") ? null : super.getResource(name);
            }

            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("com.science.gtnl."))
                    throw new AssertionError("selection eagerly defined optional class: " + name);
                return super.loadClass(name, resolve);
            }
        }) {
            Class<?> injectionData = loader.loadClass("cpw.mods.fml.relauncher.FMLInjectionData");
            Field directory = injectionData.getDeclaredField("minecraftHome");
            directory.setAccessible(true);
            directory.set(null, game.toFile());
            Class<?> pluginClass = loader.loadClass("com.miaokatze.gtsr.mixin.GTSRMixinPlugin");
            Object plugin = pluginClass.getConstructor()
                .newInstance();
            Method select = pluginClass.getMethod("shouldApplyMixin", String.class, String.class);
            assertSelection(select, plugin, "GTNLNativeSteamMachineMixin", present);
            assertSelection(select, plugin, "SteamMultiMachineBaseGTNLMixin", expectedEnhancement);
            assertSelection(select, plugin, "GTNLSteamTypeMixin", expectedEnhancement);
            // All pipeless mixins use the same explicit switch; the gate must never enable it from mod presence alone.
            assertSelection(select, plugin, "SharedSteamSourceMixin", expectedPipeless);
            assertSelection(select, plugin, "SingularitySteamSourceMixin", expectedPipeless);
            assertSelection(select, plugin, "TurbineSteamSourceMixin", expectedPipeless);
            if (!(boolean) select.invoke(plugin, "ignored", "com.miaokatze.gtsr.mixin.MTEHatchInputMixin")) {
                throw new AssertionError("GTNL flag disabled an unrelated required Mixin");
            }
        }
        if (before == null ? Files.exists(config) : !java.util.Arrays.equals(before, Files.readAllBytes(config))) {
            throw new AssertionError("early Mixin selection rewrote player configuration: " + game);
        }
        System.out.println("PASS early selection " + game.getFileName());
    }

    private static void assertSelection(Method select, Object plugin, String name, boolean expected) throws Exception {
        boolean actual = (boolean) select.invoke(plugin, "ignored", PREFIX + name);
        if (expected != actual) throw new AssertionError(name + ": expected=" + expected + ", actual=" + actual);
    }

    private static String configuration(boolean enhancement, boolean pipeless) {
        return "general {\n B:gtnlEnhancement=" + enhancement + "\n B:gtnlPipelessSteam=" + pipeless + "\n}\n";
    }
}
