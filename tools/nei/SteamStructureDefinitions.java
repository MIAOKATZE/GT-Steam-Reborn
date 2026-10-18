import java.io.File;
import java.lang.reflect.*;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;

import net.minecraft.launchwrapper.*;

/** Executes the real GT hatch builder and StructureLib stack contract without launching a game. */
public final class SteamStructureDefinitions {
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
        Method run = Launch.classLoader.loadClass("SteamStructureRuntime").getMethod("run", String.class);
        run.setAccessible(true);
        run.invoke(null, args[0]);
    }
}

class SteamStructureRuntime {
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

    public static void run(String mode) throws Exception {
        Thread.currentThread().setName("Server thread");
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        unsafe = (sun.misc.Unsafe) field.get(null);
        var handler = cpw.mods.fml.common.FMLCommonHandler.instance();
        Field side = handler.getClass().getDeclaredField("sidedDelegate");
        side.setAccessible(true);
        side.set(handler, Proxy.newProxyInstance(SteamStructureRuntime.class.getClassLoader(),
            new Class<?>[] { cpw.mods.fml.common.IFMLSidedHandler.class },
            (p, m, a) -> m.getName().equals("getSide") ? cpw.mods.fml.relauncher.Side.SERVER : null));
        for (Class<?> type : new Class<?>[] { gregtech.api.util.HatchElementBuilder.class,
            com.gtnewhorizon.structurelib.structure.IStructureElementChain.class,
            Class.forName("blockrenderer6343.integration.nei.StructureHacks", false, SteamStructureRuntime.class.getClassLoader()) }) {
            var location = type.getProtectionDomain().getCodeSource().getLocation();
            var jarLocation = location.getProtocol().equals("jar")
                ? ((java.net.JarURLConnection) location.openConnection()).getJarFileURL() : location;
            String hash = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(java.nio.file.Files.readAllBytes(Path.of(jarLocation.toURI()))));
            String requiredVersion = type == gregtech.api.util.HatchElementBuilder.class ? "GT5-Unofficial-5.09.54.205-dev.jar"
                : type == com.gtnewhorizon.structurelib.structure.IStructureElementChain.class ? "StructureLib-1.4.45-dev.jar"
                : "blockrenderer6343-1.4.24.jar";
            require(jarLocation.toString().contains(requiredVersion), "wrong dependency source: " + jarLocation);
            String expected = System.getProperty("audit.sha." + type.getName());
            require(expected != null && expected.equals(hash), "dependency SHA mismatch: " + type.getName());
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

        int fixtureId = 3510;
        for (Field casing : gregtech.api.GregTechAPI.class.getFields()) {
            if (!casing.getName().startsWith("sBlockCasings")) continue;
            Object block = casing.get(null);
            if (!(block instanceof net.minecraft.block.Block b)) continue;
            raw(cpw.mods.fml.common.registry.GameData.getBlockRegistry(), fixtureId, "fixture:" + casing.getName(), b);
            raw(cpw.mods.fml.common.registry.GameData.getItemRegistry(), fixtureId, "fixture:" + casing.getName(), new net.minecraft.item.ItemBlock(b));
            fixtureId++;
        }
        gregtech.api.GregTechAPI.sBlockFrames = net.minecraft.init.Blocks.stone;
        gregtech.api.GregTechAPI.sBlockGlass1 = net.minecraft.init.Blocks.glass;
        com.miaokatze.gtsr.common.api.enums.GTSRHatchElement.registerMachineHatchClasses(
            com.miaokatze.gtsr.common.machine.base.MTESteamCoolingHatch.class,
            com.miaokatze.gtsr.common.machine.base.MTEPressureSteamCoolingHatch.class,
            com.miaokatze.gtsr.common.machine.base.MTEHatchPressureSteamInput.class);
        String[] names = "MTEAirCompressor MTEAmmoniaPlant MTEAtmosphericCentrifuge MTEGearSteamCompressor MTELargeCokeOven MTELargeGeothermalSteamBoiler MTELargeSolarOverpressureArray MTELargeSteamFurnace MTEReinforcedBrickBlastFurnace MTESiemensMartinFurnace MTESteamFluidDrill MTEVeinSteamPyrolyzer MTECrustSteamBorer".split(" ");
        int checked = 0;
        for (String name : names) {
            Object machine = unsafe.allocateInstance(Class.forName("com.miaokatze.gtsr.common.machine." + name));
            for (Class<?> c = machine.getClass(); c != null; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    f.setAccessible(true);
                    if (f.getType() == java.util.ArrayList.class || f.getType() == java.util.List.class) f.set(machine, new ArrayList<>());
                    else if (f.getType() == java.util.HashSet.class || f.getType() == java.util.Set.class) f.set(machine, new HashSet<>());
                }
            }
            Class<?> machineType = machine.getClass();
            var source = machineType.getProtectionDomain().getCodeSource().getLocation();
            String resource = "/" + machineType.getName().replace('.', '/') + ".class";
            byte[] classBytes;
            try (var input = machineType.getResourceAsStream(resource)) { classBytes = input.readAllBytes(); }
            String classHash = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(classBytes));
            require(Path.of(source.toURI()).normalize().equals(Path.of(java.net.URI.create(System.getProperty("audit.productionSource"))).resolve(resource.substring(1)).normalize()), "unexpected production source: " + source);
            require(classHash.equals(System.getProperty("audit.classSha." + name)), "production class SHA mismatch: " + name);
            System.out.println("PRODUCTION " + name + " " + source + " SHA256=" + classHash);
            var definition = (com.gtnewhorizon.structurelib.structure.StructureDefinition<Object>) machineType.getMethod("getStructureDefinition").invoke(machine);
            int expectedHatchChars = name.equals("MTEAmmoniaPlant") ? 2 : 1;
            int hatchChars = 0, nativeHatches = 0, casingStacks = 0;
            for (var entry : definition.getElements().entrySet()) {
                if (!(entry.getValue() instanceof com.gtnewhorizon.structurelib.structure.IStructureElementChain chain)) continue;
                if (entry.getKey() == 'E' && name.equals("MTELargeSolarOverpressureArray")) continue;
                int elementHatches = 0;
                for (var fallbackObject : chain.fallbacks()) {
                    var fallback = (com.gtnewhorizon.structurelib.structure.IStructureElement<Object>) fallbackObject;
                    var blocks = fallback.getBlocksToPlace(machine, null, 0, 0, 0, new net.minecraft.item.ItemStack(net.minecraft.init.Items.stick, 1), null);
                    if (blocks == null) continue;
                    if (fallback.getClass().getName().startsWith("gregtech.api.util.HatchElementBuilder$")) {
                        require(blocks.getStacks() == null, name + " hatch no longer predicate-only");
                        elementHatches++; nativeHatches++;
                    }
                }
                if (elementHatches == 0) continue;
                hatchChars++;
                for (int tier = 1; tier <= (name.equals("MTELargeSolarOverpressureArray") ? 3 : 2); tier++) {
                    var trigger = new net.minecraft.item.ItemStack(net.minecraft.init.Items.stick, tier);
                    var blocks = entry.getValue().getBlocksToPlace(machine, null, 0, 0, 0, trigger, null);
                    int explicit = 0;
                    for (net.minecraft.item.ItemStack stack : blocks.getStacks()) {
                        require(stack.getItem() != null, name + " invalid casing item");
                        require(!(stack.getItem() instanceof gregtech.common.blocks.ItemMachines), name + " hatch leaked in preview");
                        explicit++;
                    }
                    require(explicit == 1, name + " casing candidates=" + explicit);
                    casingStacks++;
                }
            }
            require(hatchChars == expectedHatchChars, name + " hatch chars=" + hatchChars);
            System.out.println("MACHINE " + name + " PASS hatchChars=" + hatchChars + " nativeHatches=" + nativeHatches + " tierCasingQueries=" + casingStacks);
            checked++;
        }
        Object dormantMachine = unsafe.allocateInstance(com.miaokatze.gtsr.common.machine.MTEAirCompressor.class);
        var ordinaryOutput = hatch(gregtech.api.metatileentity.implementations.MTEHatchOutput.class, 30300);
        var dormant = com.miaokatze.gtsr.common.api.enums.GTSRHatchElement.SteamOutputHatch;
        boolean matches = dormant.matchesHatch(ordinaryOutput);
        boolean added = dormant.adder().apply((gregtech.api.metatileentity.implementations.MTEMultiBlockBase) dormantMachine, ordinaryOutput.getBaseMetaTileEntity(), (short) 0);
        require(matches && !added, "dormant SteamOutputHatch contract expectation changed");
        System.out.println("DORMANT SteamOutputHatch ordinaryOutput matches=" + matches + " adder=" + added + " mixinsNotApplied=true");
        System.out.println("RESULT machine-definitions PASS machines=" + checked);
    }
}
