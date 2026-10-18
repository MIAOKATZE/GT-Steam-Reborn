import java.io.File;
import java.lang.reflect.*;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;

import net.minecraft.launchwrapper.*;

/** Executes the real GT hatch builder and StructureLib stack contract without launching a game. */
public final class HatchPreviewRegression {
    public static void main(String[] args) throws Exception {
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
        Method run = Launch.classLoader.loadClass("HatchPreviewRuntime").getMethod("run", String.class);
        run.setAccessible(true);
        run.invoke(null, args[0]);
    }
}

class HatchPreviewRuntime {
    static sun.misc.Unsafe unsafe;
    static net.minecraft.block.Block machines;
    static int cases;

    static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static void raw(Object registry, int id, String name, Object value) throws Exception {
        Method method = registry.getClass().getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
        method.setAccessible(true);
        method.invoke(registry, id, name, value);
    }

    static gregtech.api.interfaces.metatileentity.IMetaTileEntity hatch(Class<?> type, int id) throws Exception {
        var mte = (gregtech.api.metatileentity.MetaTileEntity) unsafe.allocateInstance(type);
        var base = (gregtech.api.interfaces.tileentity.IGregTechTileEntity) Proxy.newProxyInstance(
            type.getClassLoader(), new Class<?>[] { gregtech.api.interfaces.tileentity.IGregTechTileEntity.class },
            (p, m, a) -> switch (m.getName()) {
                case "getMetaTileID" -> id;
                case "getMetaTileEntity" -> mte;
                case "toString" -> type.getSimpleName();
                default -> null;
            });
        mte.setBaseMetaTileEntity(base);
        gregtech.api.GregTechAPI.METATILEENTITIES[id] = mte;
        return mte;
    }

    static void scenario(String mode, Class<?> type, int id, boolean required) throws Exception {
        var mte = hatch(type, id);
        int[] count = { 0 };
        gregtech.api.interfaces.IHatchElement<Object> element = new gregtech.api.interfaces.IHatchElement<>() {
            public List<Class<? extends gregtech.api.interfaces.metatileentity.IMetaTileEntity>> mteClasses() {
                return List.of(type.asSubclass(gregtech.api.interfaces.metatileentity.IMetaTileEntity.class));
            }
            public gregtech.api.util.IGTHatchAdder<Object> adder() { return (t, te, index) -> true; }
            public String name() { return type.getSimpleName(); }
            public String getDisplayName() { return name(); }
            public long count(Object t) { return count[0]; }
        };
        var builder = gregtech.api.util.HatchElementBuilder.<Object>builder();
        if (required) builder.atLeast(element); else builder.anyOf(element);
        builder.casingIndex(10).hint(1);
        var nativeElement = builder.build();
        com.gtnewhorizon.structurelib.structure.IStructureElement<Object> actual = nativeElement;
        if (mode.equals("red")) {
            Method wrapper = Class.forName("com.miaokatze.gtsr.common.structure.GTSRStructureUtility")
                .getMethod("hatchWithCandidates", gregtech.api.util.HatchElementBuilder.class,
                    gregtech.api.interfaces.IHatchElement[].class);
            actual = (com.gtnewhorizon.structurelib.structure.IStructureElement<Object>) wrapper.invoke(null, builder,
                new gregtech.api.interfaces.IHatchElement[] { element });
        }
        var stack = mte.getStackForm(1);
        var trigger = new net.minecraft.item.ItemStack(net.minecraft.init.Items.stick);
        var nativeBlocks = nativeElement.getBlocksToPlace(null, null, 0, 0, 0, trigger, null);
        require(nativeBlocks.getStacks() == null, "upstream contract changed: hatch stacks must be predicate-only");
        require(nativeBlocks.getPredicate().test(stack), "allowed hatch rejected");
        require(builder.getShouldSkip().test(null, mte.getBaseMetaTileEntity()), "existing hatch must be skipped");
        require(!builder.getHatchItemFilter().apply(null, trigger).test(new net.minecraft.item.ItemStack(net.minecraft.init.Items.stick)), "unrelated item accepted");
        var chain = com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain(actual,
            com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock(net.minecraft.init.Blocks.stone, 0));
        var blocks = chain.getBlocksToPlace(null, null, 0, 0, 0, trigger, null);
        int hatches = 0, casings = 0;
        for (var candidate : blocks.getStacks()) {
            if (candidate.getItem() instanceof gregtech.common.blocks.ItemMachines) hatches++;
            if (candidate.getItem() == net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.stone)) casings++;
        }
        require(casings == 1, "casing disappeared");
        require(hatches == (mode.equals("red") ? 1 : 0), "unexpected hatch preview catalog");
        Method consume = Class.forName("blockrenderer6343.integration.nei.StructureHacks").getMethod(
            "getStacksForElement", Object.class, com.gtnewhorizon.structurelib.structure.IStructureElement.class,
            Class.forName("blockrenderer6343.client.utils.ConstructableData"));
        var rendered = (Iterable<net.minecraft.item.ItemStack>) consume.invoke(null, null, chain, null);
        int rendererHatches = 0;
        for (var candidate : rendered)
            if (candidate.getItem() instanceof gregtech.common.blocks.ItemMachines) rendererHatches++;
        require(rendererHatches == hatches, "actual BlockRenderer consumer differs from stack contract");
        count[0] = 1;
        require(nativeElement.getBlocksToPlace(null, null, 0, 0, 0, trigger, null).getPredicate().test(stack) != required,
            "required count or optional hatch semantics changed");
        System.out.println("CASE " + type.getSimpleName() + " required=" + required + " previewHatches=" + hatches + " casings=" + casings);
        cases++;
    }

    public static void run(String mode) throws Exception {
        Thread.currentThread().setName("Server thread");
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        unsafe = (sun.misc.Unsafe) field.get(null);
        var handler = cpw.mods.fml.common.FMLCommonHandler.instance();
        Field side = handler.getClass().getDeclaredField("sidedDelegate");
        side.setAccessible(true);
        side.set(handler, Proxy.newProxyInstance(HatchPreviewRuntime.class.getClassLoader(),
            new Class<?>[] { cpw.mods.fml.common.IFMLSidedHandler.class },
            (p, m, a) -> m.getName().equals("getSide") ? cpw.mods.fml.relauncher.Side.SERVER : null));
        for (Class<?> type : new Class<?>[] { gregtech.api.util.HatchElementBuilder.class,
            com.gtnewhorizon.structurelib.structure.IStructureElementChain.class,
            Class.forName("blockrenderer6343.integration.nei.StructureHacks", false, HatchPreviewRuntime.class.getClassLoader()) }) {
            var location = type.getProtectionDomain().getCodeSource().getLocation();
            var jarLocation = location.getProtocol().equals("jar")
                ? ((java.net.JarURLConnection) location.openConnection()).getJarFileURL() : location;
            String hash = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(java.nio.file.Files.readAllBytes(Path.of(jarLocation.toURI()))));
            System.out.println("SOURCE " + type.getName() + " " + location + " SHA256=" + hash);
        }
        net.minecraft.init.Bootstrap.func_151354_b();
        for (Field casing : gregtech.api.GregTechAPI.class.getFields()) {
            if (!casing.getName().startsWith("sBlockCasings")) continue;
            try {
                casing.set(null, unsafe.allocateInstance(Class.forName("gregtech.common.blocks.BlockCasings"
                    + casing.getName().substring("sBlockCasings".length()))));
            } catch (ClassNotFoundException ignored) {}
        }
        machines = new net.minecraft.block.Block(net.minecraft.block.material.Material.iron) {};
        raw(cpw.mods.fml.common.registry.GameData.getBlockRegistry(), 3500, "fixture:machines", machines);
        raw(cpw.mods.fml.common.registry.GameData.getItemRegistry(), 3500, "fixture:machines", new gregtech.common.blocks.ItemMachines(machines));
        gregtech.api.GregTechAPI.sBlockMachines = machines;
        Class<?>[] types = {
            com.miaokatze.gtsr.common.machine.base.MTESteamCoolingHatch.class,
            com.miaokatze.gtsr.common.machine.base.MTEPressureSteamCoolingHatch.class,
            com.miaokatze.gtsr.common.machine.MTESingularitySteamCompartment.class,
            com.miaokatze.gtsr.common.machine.MTESingularitySteamOutputCompartment.class,
            com.miaokatze.gtsr.common.machine.MTESingularityFluidInputCompartment.class,
            com.miaokatze.gtsr.common.machine.MTESingularityFluidOutputCompartment.class
        };
        for (int i = 0; i < types.length; i++) {
            scenario(mode, types[i], 30000 + i, false);
            scenario(mode, types[i], 30000 + i, true);
        }
        System.out.println("RESULT " + mode + " PASS cases=" + cases);
    }
}
