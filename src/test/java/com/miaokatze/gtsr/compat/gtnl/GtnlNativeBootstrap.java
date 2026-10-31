package com.miaokatze.gtsr.compat.gtnl;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

/** The existing native regression bootstrap, restricted to MC registries and an FML side/container. */
final class GtnlNativeBootstrap {

    private GtnlNativeBootstrap() {}

    static boolean enter(String mainClass, String[] args) throws Exception {
        if (!(GtnlNativeBootstrap.class.getClassLoader() instanceof LaunchClassLoader)) {
            List<URL> urls = new ArrayList<>();
            for (String path : System.getProperty("java.class.path")
                .split(File.pathSeparator)) {
                urls.add(
                    new File(path).toURI()
                        .toURL());
            }
            Launch.classLoader = new LaunchClassLoader(urls.toArray(new URL[0]));
            Launch.blackboard = new HashMap<>();
            Launch.blackboard.put("fml.deobfuscatedEnvironment", true);
            Launch.minecraftHome = new File(".");
            Thread.currentThread()
                .setContextClassLoader(Launch.classLoader);
            Launch.classLoader.loadClass("cpw.mods.fml.common.Loader")
                .getMethod("injectData", Object[].class)
                .invoke(
                    null,
                    (Object) new Object[] { "7", "10", "99", "99", "1.7.10", "9.05", new File("."),
                        new ArrayList<>() });
            Launch.classLoader.loadClass(mainClass)
                .getMethod("main", String[].class)
                .invoke(null, (Object) args);
            return false;
        }
        Thread.currentThread()
            .setName("Server thread");
        set(cpw.mods.fml.relauncher.FMLInjectionData.class, null, "minecraftHome", new File("."));
        Object side = Proxy.newProxyInstance(
            GtnlNativeBootstrap.class.getClassLoader(),
            new Class<?>[] { cpw.mods.fml.common.IFMLSidedHandler.class },
            (proxy, method, values) -> method.getName()
                .equals("getSide") ? cpw.mods.fml.relauncher.Side.SERVER : null);
        set(
            cpw.mods.fml.common.FMLCommonHandler.class,
            cpw.mods.fml.common.FMLCommonHandler.instance(),
            "sidedDelegate",
            side);
        net.minecraft.init.Bootstrap.func_151354_b();
        cpw.mods.fml.common.Loader loader = cpw.mods.fml.common.Loader.instance();
        Field field = cpw.mods.fml.common.Loader.class.getDeclaredField("modController");
        field.setAccessible(true);
        Object controller = field.get(loader);
        if (controller == null) {
            controller = new cpw.mods.fml.common.LoadController(loader);
            field.set(loader, controller);
        }
        cpw.mods.fml.common.ModMetadata metadata = new cpw.mods.fml.common.ModMetadata();
        metadata.modId = "gtsr";
        metadata.name = "GTSR isolated GTNL contract";
        metadata.version = "contract";
        set(
            cpw.mods.fml.common.LoadController.class,
            controller,
            "activeContainer",
            new cpw.mods.fml.common.DummyModContainer(metadata));
        return true;
    }

    private static void set(Class<?> type, Object instance, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(instance, value);
    }
}
