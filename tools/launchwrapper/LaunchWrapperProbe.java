package diagnostic.launchwrapper;

import java.io.File;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.HashMap;
import java.util.Set;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;
import java.util.Enumeration;

import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

import org.spongepowered.asm.service.mojang.MixinServiceLaunchWrapper;

import cpw.mods.fml.common.ModClassLoader;
import cpw.mods.fml.common.discovery.ASMDataTable;
import cpw.mods.fml.common.discovery.ModCandidate;
import cpw.mods.fml.common.discovery.ContainerType;

/** Isolated Java 8 diagnostic. Does not start Minecraft or change a launcher instance. */
public final class LaunchWrapperProbe {

    private static final String TARGET = "me.eigenraven.personalspace.world.PersonalWorldProvider";

    public static void main(String[] args) throws Exception {
        Launch.classLoader = new LaunchClassLoader(new URL[0]);
        Launch.blackboard = new HashMap<String, Object>();
        MixinServiceLaunchWrapper service = new MixinServiceLaunchWrapper();
        boolean early = args[0].equals("early");
        if (early) {
            try {
                service.getClassBytes(TARGET, false);
                throw new AssertionError("Target unexpectedly visible before mod discovery");
            } catch (ClassNotFoundException expected) {
                System.out.println("EARLY_MIXIN_LOOKUP=" + expected);
            }
        }
        System.out.println("NEGATIVE_BEFORE_DISCOVERY=" + cached("negativeResourceCache"));
        // Execute the native Forge constructMod classpath/cache steps as well: its class keys use '/'.
        File modFile = new File(args[1]);
        ModClassLoader mods = new ModClassLoader(Launch.classLoader);
        ModCandidate candidate = new ModCandidate(modFile, modFile, ContainerType.JAR);
        Field table = ModCandidate.class.getDeclaredField("table");
        table.setAccessible(true);
        table.set(candidate, new ASMDataTable());
        try (ZipFile jar = new ZipFile(modFile)) {
            Enumeration<? extends ZipEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.endsWith(".class")) candidate.addClassEntry(name);
            }
        }
        mods.addFile(modFile);
        System.out.println("FML_SLASH_CLASS_KEY=" + candidate.getClassList().contains(TARGET.replace('.', '/')));
        System.out.println("FML_DOTTED_CLASS_KEY=" + candidate.getClassList().contains(TARGET));
        mods.clearNegativeCacheFor(candidate.getClassList());
        System.out.println("NEGATIVE_AFTER_NATIVE_FML_CLEAR=" + cached("negativeResourceCache"));
        if (early && !cached("negativeResourceCache")) throw new AssertionError("Native FML cleared the dotted miss");
        URL resource = Launch.classLoader.getResource(TARGET.replace('.', '/') + ".class");
        if (resource == null) throw new AssertionError("PersonalSpace jar genuinely missing target");
        System.out.println("RESOURCE_AFTER_DISCOVERY=" + resource);
        System.out.println("CLASS_BYTES_NULL_AFTER_DISCOVERY=" + (Launch.classLoader.getClassBytes(TARGET) == null));
        try {
            Launch.classLoader.loadClass(TARGET);
            System.out.println("LOAD=PASS");
        } catch (Throwable failure) {
            failure.printStackTrace(System.out);
            if (!early || !(failure instanceof ClassNotFoundException)
                || !(failure.getCause() instanceof NullPointerException)) throw failure;
            System.out.println("REPRODUCED_LW_CNFE_NPE=true");
        }
        System.out.println("INVALID_AFTER_LOAD=" + cached("invalidClasses"));
    }

    private static boolean cached(String fieldName) throws Exception {
        Field field = LaunchClassLoader.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return ((Set<?>) field.get(Launch.classLoader)).contains(TARGET);
    }
}
