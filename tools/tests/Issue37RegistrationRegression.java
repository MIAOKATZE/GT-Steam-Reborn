import java.io.File;
import java.lang.reflect.*;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.launchwrapper.*;

/** Actual GTSR registration against an upstream GT API jar, without a game launch. */
public final class Issue37RegistrationRegression {
    public static void main(String[] args) throws Exception {
        launch("Issue37Runtime", args[0]);
    }
    public static void launch(String runtime, String version) throws Exception {
        List<URL> urls = new ArrayList<>();
        for (String p : System.getProperty("java.class.path").split(File.pathSeparator))
            urls.add(Path.of(p).toUri().toURL());
        Launch.classLoader = new LaunchClassLoader(urls.toArray(new URL[0]));
        Launch.blackboard = new HashMap<>();
        Launch.blackboard.put("fml.deobfuscatedEnvironment", true);
        Launch.minecraftHome = new File(".");
        Thread.currentThread().setContextClassLoader(Launch.classLoader);
        Launch.classLoader.loadClass("cpw.mods.fml.common.Loader").getMethod("injectData", Object[].class)
            .invoke(null, (Object) new Object[] { "7", "10", "99", "99", "1.7.10", "9.05", new File("."), new ArrayList<>() });
        Method run = Launch.classLoader.loadClass(runtime).getMethod("run", String.class);
        run.setAccessible(true);
        run.invoke(null, version);
    }
}

class Issue37Runtime {
    static void set(Object o, Class<?> c, String name, Object value) throws Exception {
        Field f = c.getDeclaredField(name); f.setAccessible(true); f.set(o, value);
    }
    static sun.misc.Unsafe bootstrap(String version) throws Exception {
        Thread.currentThread().setName("Server thread");
        set(null, cpw.mods.fml.relauncher.FMLInjectionData.class, "minecraftHome", new File("."));
        Object side = Proxy.newProxyInstance(Issue37Runtime.class.getClassLoader(),
            new Class<?>[] { cpw.mods.fml.common.IFMLSidedHandler.class },
            (p,m,a) -> m.getName().equals("getSide") ? cpw.mods.fml.relauncher.Side.SERVER : null);
        set(cpw.mods.fml.common.FMLCommonHandler.instance(), cpw.mods.fml.common.FMLCommonHandler.class, "sidedDelegate", side);
        Object gt = Proxy.newProxyInstance(Issue37Runtime.class.getClassLoader(),
            new Class<?>[] { cpw.mods.fml.common.ModContainer.class }, (p,m,a) -> switch(m.getName()) {
                case "getVersion" -> version; case "getModId" -> "gregtech"; case "getName" -> "GregTech"; default -> null;
            });
        set(cpw.mods.fml.common.Loader.instance(), cpw.mods.fml.common.Loader.class, "namedMods", Map.of("gregtech",gt));
        net.minecraft.init.Bootstrap.func_151354_b();
        Field controllerField = cpw.mods.fml.common.Loader.class.getDeclaredField("modController");
        controllerField.setAccessible(true);
        Object controller = controllerField.get(cpw.mods.fml.common.Loader.instance());
        if (controller == null) {
            controller = new cpw.mods.fml.common.LoadController(cpw.mods.fml.common.Loader.instance());
            controllerField.set(cpw.mods.fml.common.Loader.instance(), controller);
        }
        cpw.mods.fml.common.ModMetadata metadata = new cpw.mods.fml.common.ModMetadata();
        metadata.modId="gtsr"; metadata.name="GTSR isolated regression"; metadata.version="1.21.47-prototype";
        set(controller, controller.getClass(), "activeContainer", new cpw.mods.fml.common.DummyModContainer(metadata));
        Field uf = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); uf.setAccessible(true);
        sun.misc.Unsafe u = (sun.misc.Unsafe) uf.get(null);
        gregtech.GTMod.GT = (gregtech.GTMod) u.allocateInstance(gregtech.GTMod.class);
        gregtech.GTMod.proxy = (gregtech.common.GTProxy) u.allocateInstance(gregtech.common.GTProxy.class);
        for (String name : List.of("steam", "ic2superheatedsteam", "supercriticalsteam", "densesteam", "densesuperheatedsteam", "densesupercriticalsteam"))
            net.minecraftforge.fluids.FluidRegistry.registerFluid(new net.minecraftforge.fluids.Fluid(name));
        gregtech.api.GregTechAPI.sPreloadStarted = true;
        gregtech.api.GregTechAPI.sPostloadStarted = false;
        for (Field f : gregtech.api.GregTechAPI.class.getFields()) if (f.getName().startsWith("sBlockCasings")) {
            try { f.set(null,u.allocateInstance(Class.forName("gregtech.common.blocks.BlockCasings"+f.getName().substring("sBlockCasings".length())))); }
            catch (ClassNotFoundException ignored) {}
        }
        gregtech.api.GregTechAPI.sBlockFrames = (net.minecraft.block.Block) u.allocateInstance(gregtech.common.blocks.BlockFrameBox.class);
        gregtech.api.GregTechAPI.sBlockMachines = new net.minecraft.block.Block(net.minecraft.block.material.Material.iron) {};
        Object br = cpw.mods.fml.common.registry.GameData.getBlockRegistry();
        Method raw = br.getClass().getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
        raw.setAccessible(true); raw.invoke(br, 3500, "fixture:machines", gregtech.api.GregTechAPI.sBlockMachines);
        Object ir = cpw.mods.fml.common.registry.GameData.getItemRegistry();
        raw = ir.getClass().getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
        raw.setAccessible(true); raw.invoke(ir, 3500, "fixture:machines", new gregtech.common.blocks.ItemMachines(gregtech.api.GregTechAPI.sBlockMachines));
        net.minecraft.block.Block glass = new net.minecraft.block.Block(net.minecraft.block.material.Material.glass) {};
        raw = br.getClass().getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
        raw.setAccessible(true); raw.invoke(br, 3501, "IC2:blockAlloyGlass", glass);
        gregtech.api.GregTechAPI.sBlockGlass1 = glass;
        System.out.println("SOURCE GT " + gregtech.api.GregTechAPI.class.getProtectionDomain().getCodeSource().getLocation());
        System.out.println("SOURCE GTSR " + com.miaokatze.gtsr.loader.MachineLoader.class.getProtectionDomain().getCodeSource().getLocation());
        return u;
    }
    public static void run(String version) throws Exception {
        bootstrap(version);
        com.miaokatze.gtsr.loader.MachineLoader.initMachines();
        int count = 0;
        for (var mte : gregtech.api.GregTechAPI.METATILEENTITIES) {
            if (mte != null && mte.getClass().getName().startsWith("com.miaokatze.gtsr.")) count++;
        }
        if (count < 75) throw new AssertionError("registration truncated: " + count);
        System.out.println("RESULT registration PASS version="+version+" machines="+count);
        int definitions = 0;
        Set<String> selected = Set.of("MTESteamHubArray", "MTEWaterHubArray", "MTEKineticProcessingArray",
            "MTESteamMineralLogisticsCluster", "MTEUnitCrusher");
        for (var mte : gregtech.api.GregTechAPI.METATILEENTITIES) {
            if (mte == null || !selected.contains(mte.getClass().getSimpleName())) continue;
            Object definition = mte.getClass().getMethod("getStructureDefinition").invoke(mte);
            if (definition == null) throw new AssertionError("null structure: " + mte.getClass());
            definitions++;
        }
        if (definitions != selected.size()) throw new AssertionError("missing definition probes: " + definitions);
        System.out.println("RESULT structures PASS version="+version+" definitions="+definitions);
    }
}
