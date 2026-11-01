import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.miaokatzecore.api.recipe.RecipeRegistration;
import com.miaokatze.miaokatzecore.api.recipe.RecipeRegistrationLifecycle;

import cpw.mods.fml.common.eventhandler.EventBus;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.registry.GameData;
import cpw.mods.fml.relauncher.Side;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.recipe.RecipeCategory;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.recipe.RecipeMetadataKey;
import gregtech.api.recipe.metadata.SimpleRecipeMetadataKey;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipeBuilder;
import gregtech.api.util.GTRecipeConstants;
import gtPlusPlus.xmod.gregtech.loaders.RecipeGenMultisUsingFluidInsteadOfCells;

/** Actual .265 converter/query and Core registration stages; no Minecraft client/server launch. */
public final class LateNonCellRecipeRegression {
    public static void main(String[] args) throws Exception {
        System.setProperty("gtsr.noncell.fixed", args[1]);
        Issue37RegistrationRegression.launch("LateNonCellRecipeRuntime", args[0]);
    }
}

class LateNonCellRecipeRuntime {
    private static RecipeMetadataKey<String> STAGE;
    private static final List<String> trace = new ArrayList<>();
    private static int checks, failures, finalizers;
    private static Item input, output, cell;
    private static Fluid cellFluid, extraFluid, outputFluid;
    private static RecipeMap<?>[][] PAIRS;

    private static void check(String id, boolean condition) {
        checks++;
        if (!condition) failures++;
        System.out.println("CHECK " + id + "=" + (condition ? "PASS" : "FAIL"));
    }

    private static Item item(String name, int id) throws Exception {
        Item result = new Item().setUnlocalizedName("gtsr_noncell_probe_" + name).setHasSubtypes(true);
        Object registry = GameData.getItemRegistry();
        Method raw = registry.getClass().getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
        raw.setAccessible(true); raw.invoke(registry, id, "gtsr_noncell_probe:" + name, result);
        return result;
    }

    private static Fluid fluid(String name) {
        Fluid result = new Fluid("gtsr_noncell_probe_" + name);
        if (!FluidRegistry.registerFluid(result)) throw new AssertionError("Duplicate fixture fluid");
        return result;
    }

    private static GTRecipe recipe(String tag, int key, boolean filled, boolean skip) {
        GTRecipe recipe = GTRecipeBuilder.builder()
            .itemInputs(filled ? new ItemStack[] { new ItemStack(input, 3, key), new ItemStack(cell, 2, 1) }
                : new ItemStack[] { new ItemStack(input, 3, key) })
            .itemOutputs(filled ? new ItemStack[] { new ItemStack(output, 2, key), new ItemStack(cell, 2, 0) }
                : new ItemStack[] { new ItemStack(output, 2, key) })
            .outputChances(filled ? new int[] { 7000, 10000 } : new int[] { 7000 })
            .fluidInputs(new FluidStack(extraFluid, 100))
            .fluidOutputs(new FluidStack(outputFluid, 200))
            .duration(120 + key).eut(30 + key)
            .metadata(STAGE, tag)
            .metadata(GTRecipeConstants.CLEANROOM, true)
            .metadata(GTRecipeConstants.SKIP_CELL_RECIPE_GENERATION, skip)
            .build().get();
        recipe.mSpecialValue = 321;
        return recipe;
    }

    private static GTRecipe query(RecipeMap<?> map, String tag, int key, boolean converted, boolean filled) {
        ItemStack[] items = !filled || converted ? new ItemStack[] { new ItemStack(input, 3, key) }
            : new ItemStack[] { new ItemStack(input, 3, key), new ItemStack(cell, 2, 1) };
        FluidStack[] fluids = converted && filled ? new FluidStack[] { new FluidStack(extraFluid, 100), new FluidStack(cellFluid, 2000) }
            : new FluidStack[] { new FluidStack(extraFluid, 100) };
        return map.findRecipeQuery().items(items).fluids(fluids).voltage(Long.MAX_VALUE)
            .filter(recipe -> tag == null || tag.equals(recipe.getMetadata(STAGE))).find();
    }

    private static Map<String, Integer> targetSizes() {
        Map<String, Integer> result = new HashMap<>();
        for (RecipeMap<?>[] pair : PAIRS) result.put(pair[1].unlocalizedName, pair[1].getAllRecipes().size());
        return result;
    }

    public static void run(String version) throws Exception {
        Issue37Runtime.bootstrap(version);
        Issue37Runtime.set(null, cpw.mods.fml.relauncher.FMLRelaunchLog.class, "side", Side.SERVER);
        STAGE = SimpleRecipeMetadataKey.create(String.class, "gtsr_noncell_probe_stage");
        PAIRS = new RecipeMap<?>[][] {
            { RecipeMaps.centrifugeRecipes, RecipeMaps.centrifugeNonCellRecipes },
            { RecipeMaps.electrolyzerRecipes, RecipeMaps.electrolyzerNonCellRecipes },
            { RecipeMaps.mixerRecipes, RecipeMaps.mixerNonCellRecipes },
            { RecipeMaps.chemicalDehydratorRecipes, RecipeMaps.chemicalDehydratorNonCellRecipes },
            { RecipeMaps.coldTrapRecipes, RecipeMaps.nuclearSaltProcessingPlantRecipes },
            { RecipeMaps.reactorProcessingUnitRecipes, RecipeMaps.nuclearSaltProcessingPlantRecipes }
        };
        input = item("input", 4100); output = item("output", 4101); cell = item("cell", 4102);
        cellFluid = fluid("cell"); extraFluid = fluid("extra"); outputFluid = fluid("output");
        ItemList.Cell_Empty.set(new ItemStack(cell, 1, 0));
        ItemList.Circuit_Integrated.set(new ItemStack(item("circuit", 4103), 1, 0));
        FluidContainerRegistry.registerFluidContainer(new FluidStack(cellFluid, 1000), new ItemStack(cell, 1, 1), new ItemStack(cell, 1, 0));
        boolean fixed = Boolean.getBoolean("gtsr.noncell.fixed");
        for (int i = 0; i < PAIRS.length; i++) {
            PAIRS[i][0].add(recipe("foreign-before-" + i, 10 + i, false, false));
            RecipeGenMultisUsingFluidInsteadOfCells.generateRecipesNotUsingCells(PAIRS[i][0], PAIRS[i][1]);
        }
        Map<String, Integer> baselineSizes = targetSizes();
        Map<String, RecipeMap<?>> registeredMaps = new HashMap<>(RecipeMap.ALL_RECIPE_MAPS);
        Map<String, RecipeCategory> registeredCategories = new HashMap<>(RecipeCategory.ALL_RECIPE_CATEGORIES);
        GTRecipe[] own = new GTRecipe[PAIRS.length];
        RecipeRegistration.register("gtsr", () -> {
            trace.add("gtsr-main");
            for (int i = 0; i < PAIRS.length; i++) {
                own[i] = recipe("own-" + i, 30 + i, true, false); PAIRS[i][0].add(own[i]);
            }
            RecipeMaps.mixerRecipes.add(recipe("own-skip", 99, false, true));
        }, () -> {
            trace.add("gtsr-derived");
            if (fixed) check("shared-before-consumer-derived", query(PAIRS[0][1], "own-0", 30, true, true) != null
                && query(PAIRS[0][1], "foreign-after-0", 60, true, false) != null);
        });
        RecipeRegistration.register("foreign-late", () -> {
            trace.add("foreign-main");
            for (int i = 0; i < PAIRS.length; i++) PAIRS[i][0].add(recipe("foreign-after-" + i, 60 + i, false, false));
        }, () -> trace.add("foreign-derived"));
        Field field = RecipeRegistration.class.getDeclaredField("ENGINE"); field.setAccessible(true);
        Object engine = field.get(null);
        EventBus bus = new EventBus();
        Constructor<RecipeRegistrationLifecycle> constructor = fixed ? RecipeRegistrationLifecycle.class.getDeclaredConstructor(
            engine.getClass(), Side.class, EventBus.class, Runnable.class, boolean.class)
            : RecipeRegistrationLifecycle.class.getDeclaredConstructor(engine.getClass(), Side.class, EventBus.class, Runnable.class);
        constructor.setAccessible(true);
        Runnable finish = () -> { finalizers++; trace.add("finalize"); };
        RecipeRegistrationLifecycle lifecycle = fixed ? constructor.newInstance(engine, Side.SERVER, bus, finish, true)
            : constructor.newInstance(engine, Side.SERVER, bus, finish);
        // Invoke the actual Core lifecycle methods directly. Real event-bus transformation is a separate FML probe.
        lifecycle.onServerTick(new TickEvent.ServerTickEvent(TickEvent.Phase.START));
        check("start-tick-does-not-run", trace.isEmpty());
        lifecycle.onServerTick(new TickEvent.ServerTickEvent(TickEvent.Phase.END));
        check("core-all-main-before-all-derived", trace.equals(Arrays.asList("gtsr-main", "foreign-main", "gtsr-derived", "foreign-derived", "finalize")));
        check("core-complete", RecipeRegistration.isComplete() && finalizers == 1);
        for (int i = 0; i < PAIRS.length; i++) {
            check("source-own-present-" + i, query(PAIRS[i][0], "own-" + i, 30 + i, false, true) == own[i]);
            GTRecipe derived = query(PAIRS[i][1], "own-" + i, 30 + i, true, true);
            check("target-own-lookup-" + i, derived != null);
            if (derived != null) {
                check("metadata-limits-and-scalars-" + i, derived.mDuration == own[i].mDuration && derived.mEUt == own[i].mEUt
                    && derived.mSpecialValue == 321 && Boolean.TRUE.equals(derived.getMetadata(GTRecipeConstants.CLEANROOM))
                    && derived.getOutputChance(0) == 7000 && ("own-" + i).equals(derived.getMetadata(STAGE)));
                check("container-conversion-" + i, derived.mInputs.length == 1 && derived.mOutputs.length == 1
                    && derived.mFluidInputs.length == 2 && derived.mFluidOutputs.length == 1);
            }
            check("source-recipe-not-mutated-" + i, own[i].mInputs.length == 2 && own[i].mInputs[1].stackSize == 2
                && own[i].mOutputs.length == 2 && own[i].mFluidInputs.length == 1);
            check("foreign-before-preserved-" + i, query(PAIRS[i][1], null, 10 + i, true, false) != null);
            check("second-core-owner-automatic-" + i, (query(PAIRS[i][1], "foreign-after-" + i, 60 + i, true, false) != null) == fixed);
        }
        check("skip-cell-generation-preserved", query(RecipeMaps.mixerNonCellRecipes, "own-skip", 99, true, false) == null);
        check("no-new-public-recipe-map", RecipeMap.ALL_RECIPE_MAPS.equals(registeredMaps));
        check("no-new-public-recipe-category", RecipeCategory.ALL_RECIPE_CATEGORIES.equals(registeredCategories));
        Map<String, Integer> sizes = targetSizes();
        lifecycle.onServerTick(new TickEvent.ServerTickEvent(TickEvent.Phase.END));
        check("repeat-end-tick-idempotent", sizes.equals(targetSizes()) && finalizers == 1);
        System.out.println("RESULT mode=" + fixed + " checks=" + checks + " failures=" + failures + " baseline=" + baselineSizes + " after=" + sizes);
        System.out.println("SOURCE GT generator=" + RecipeGenMultisUsingFluidInsteadOfCells.class.getProtectionDomain().getCodeSource().getLocation());
        System.out.println("SOURCE Core lifecycle=" + RecipeRegistrationLifecycle.class.getProtectionDomain().getCodeSource().getLocation());
        if (fixed) System.out.println("SOURCE Core GT adapter=" + Class.forName("com.miaokatze.miaokatzecore.api.recipe.GTNonCellRecipes").getProtectionDomain().getCodeSource().getLocation());
        if (failures != 0) throw new AssertionError("NonCell target lookup failures=" + failures);
    }
}
