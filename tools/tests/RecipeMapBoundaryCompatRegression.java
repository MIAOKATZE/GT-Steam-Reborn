import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Real GT++ map owners and Critical fuel/mode consumers, without launching Minecraft. */
public final class RecipeMapBoundaryCompatRegression {
    public static void main(String[] args) throws Exception {
        Issue37RegistrationRegression.launch("RecipeMapBoundaryCompatRuntime", args[0]);
    }
}

class RecipeMapBoundaryCompatRuntime {
    public static void run(String version) throws Exception {
        Issue37Runtime.bootstrap(version);
        auditDirectFields();
        String owner = version.equals("5.09.52.594") ? "gtPlusPlus.api.recipe.GTPPRecipeMaps"
            : "gregtech.api.recipe.RecipeMaps";
        for (String name : List.of("rocketFuels", "quantumForceTransformerRecipes", "electrolyzerNonCellRecipes",
            "centrifugeNonCellRecipes", "mixerNonCellRecipes", "chemicalDehydratorRecipes",
            "chemicalDehydratorNonCellRecipes", "multiblockRockBreakerRecipes")) {
            Field expected = Class.forName(owner).getField(name);
            Object actual = com.miaokatze.gtsr.api.compat.GTVersionCompat.requireGppRecipeMap(name);
            if (actual == null || actual != expected.get(null)
                || actual != com.miaokatze.gtsr.api.compat.GTVersionCompat.requireGppRecipeMap(name))
                throw new AssertionError("Map identity/cache mismatch: " + name);
        }
        String missing = "gtsr_regression_missing_recipe_map";
        try {
            com.miaokatze.gtsr.api.compat.GTVersionCompat.requireGppRecipeMap(missing);
            throw new AssertionError("Missing mandatory map was accepted");
        } catch (IllegalStateException expected) {
            if (!expected.getMessage().equals("Missing required GT recipe map: " + missing)) throw expected;
        }
        System.out.println("RESULT map-owners version=" + version + " owner=" + owner + " identity-cache=8/8 missing=PASS");

        addFuel(gregtech.api.recipe.RecipeMaps.dieselFuels, "gtsr_probe_diesel", 100);
        addFuel(gregtech.api.recipe.RecipeMaps.gasTurbineFuels, "gtsr_probe_gas", 200);
        var rocket = com.miaokatze.gtsr.api.compat.GTVersionCompat.requireGppRecipeMap("rocketFuels");
        addFuel(rocket, "gtsr_probe_rocket", 300);
        addFuel(rocket, "gtsr_probe_diesel", 999);
        Map<String, java.math.BigInteger> fuels = new TreeMap<>();
        for (var fuel : com.miaokatze.gtsr.common.critical.CriticalGTRecipes.chemicalFuels()) {
            if (fuel.fluid.amount != 1) throw new AssertionError("Fuel amount is not normalized");
            fuels.put(fuel.fluid.getFluid().getName(), fuel.euPerL);
        }
        for (var entry : Map.of("gtsr_probe_diesel", 100L, "gtsr_probe_gas", 200L, "gtsr_probe_rocket", 900L).entrySet())
            if (!java.math.BigInteger.valueOf(entry.getValue()).equals(fuels.get(entry.getKey())))
                throw new AssertionError("Fuel value/priority changed: " + fuels);
        System.out.println("RESULT chemicalFuels version=" + version + " diesel=100 gas=200 rocket=900 duplicate-priority=PASS");

        var qft = com.miaokatze.gtsr.api.compat.GTVersionCompat.requireGppRecipeMap("quantumForceTransformerRecipes");
        var modes = com.miaokatze.gtsr.common.critical.CriticalProcessingModes.list(
            com.miaokatze.gtsr.common.critical.CriticalMachineKind.PROCESSING);
        if (!modes.contains(qft.unlocalizedName)
            || com.miaokatze.gtsr.common.critical.CriticalProcessingModes.map(qft.unlocalizedName) != qft
            || com.miaokatze.gtsr.common.critical.CriticalProcessingModes.display(qft.unlocalizedName) == null)
            throw new AssertionError("QFT mode no longer resolves the native map");
        System.out.println("RESULT processing-modes version=" + version + " count=" + modes.size() + " qft=PASS");
    }

    private static void addFuel(gregtech.api.recipe.RecipeMap<?> map, String name, int eu) {
        var fluid = net.minecraftforge.fluids.FluidRegistry.getFluid(name);
        if (fluid == null) {
            fluid = new net.minecraftforge.fluids.Fluid(name);
            net.minecraftforge.fluids.FluidRegistry.registerFluid(fluid);
        }
        var recipe = gregtech.api.util.GTRecipeBuilder.builder()
            .fluidInputs(new net.minecraftforge.fluids.FluidStack(fluid, 1))
            .duration(20).eut(0).specialValue(eu).build().orElseThrow();
        map.getBackend().compileRecipe(recipe);
    }

    /** Scope is only production references whose bytecode owner is GT RecipeMaps. */
    private static void auditDirectFields() throws Exception {
        Path classes = Path.of(com.miaokatze.gtsr.common.critical.CriticalGTRecipes.class
            .getProtectionDomain().getCodeSource().getLocation().toURI());
        if (Files.isRegularFile(classes) && classes.toString().endsWith(".class")) {
            for (String ignored : com.miaokatze.gtsr.common.critical.CriticalGTRecipes.class.getName().split("\\.")) {
                classes = classes.getParent();
            }
        }
        if (!Files.isDirectory(classes)) throw new AssertionError("Expected compiled production classes directory");
        Map<String, List<String>> fields = new TreeMap<>();
        try (var paths = Files.walk(classes)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".class")).toList()) {
                ClassNode node = new ClassNode();
                new ClassReader(Files.readAllBytes(path)).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                for (Object object : node.methods) {
                    MethodNode method = (MethodNode) object;
                    for (AbstractInsnNode instruction : method.instructions.toArray())
                        if (instruction instanceof FieldInsnNode field
                            && field.owner.equals("gregtech/api/recipe/RecipeMaps"))
                            fields.computeIfAbsent(field.name, key -> new ArrayList<>()).add(node.name + "#" + method.name);
                }
            }
        }
        if (fields.isEmpty()) throw new AssertionError("Production RecipeMaps field scan was empty");
        for (var entry : fields.entrySet()) {
            try {
                gregtech.api.recipe.RecipeMaps.class.getField(entry.getKey());
            } catch (NoSuchFieldException missing) {
                throw new AssertionError("Incompatible direct RecipeMaps field: " + entry.getKey() + " " + entry.getValue(), missing);
            }
        }
        System.out.println("RESULT direct-field-audit unique=" + fields.size() + " missing=0");
    }
}
