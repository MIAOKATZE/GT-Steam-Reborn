import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import cpw.mods.fml.common.registry.GameData;
import cpw.mods.fml.relauncher.Side;
import gregtech.api.enums.ItemList;
import gregtech.api.recipe.RecipeCategory;
import gregtech.api.recipe.RecipeCategorySetting;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBuilder;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.recipe.RecipeMetadataKey;
import gregtech.api.recipe.metadata.SimpleRecipeMetadataKey;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipeBuilder;
import gregtech.api.util.GTRecipeConstants;
import gtPlusPlus.core.util.recipe.GTRecipeUtils;
import gtPlusPlus.core.util.recipe.RecipeHashStrat;
import gtPlusPlus.xmod.gregtech.loaders.RecipeGenMultisUsingFluidInsteadOfCells;

/** Actual .265 converter and Core delta adapter, with independent source containers and semantic variants. */
public final class CoreNonCellDedupRegression {
    public static void main(String[] args) throws Exception {
        Issue37RegistrationRegression.launch("CoreNonCellDedupRuntime", args[0]);
    }
}

class CoreNonCellDedupRuntime {
    private static RecipeMetadataKey<String> MARK;
    private static Item input, output, cell;
    private static Fluid fluid;
    private static int checks, failures;

    private static void check(String id, boolean condition) {
        checks++;
        if (!condition) failures++;
        System.out.println("CHECK " + id + "=" + (condition ? "PASS" : "FAIL"));
    }

    private static Item item(String name, int id) throws Exception {
        Item result = new Item().setUnlocalizedName("core_dedup_" + name).setHasSubtypes(true);
        Object registry = GameData.getItemRegistry();
        Method raw = registry.getClass().getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
        raw.setAccessible(true);
        raw.invoke(registry, id, "core_dedup:" + name, result);
        return result;
    }

    private static GTRecipe recipe(int key, int container, String mark, boolean skip, int chance) {
        GTRecipeBuilder builder = GTRecipeBuilder.builder()
            .itemInputs(new ItemStack(input, 3, key), new ItemStack(cell, 1, container))
            .itemOutputs(new ItemStack(output, 2, key), new ItemStack(cell, 1, 0))
            .outputChances(chance, 10000)
            .duration(120).eut(30)
            .metadata(MARK, mark)
            .metadata(GTRecipeConstants.SKIP_CELL_RECIPE_GENERATION, skip);
        return builder.build().get();
    }

    private static GTRecipe normalized(int key, String mark) {
        return GTRecipeBuilder.builder()
            .itemInputs(new ItemStack(input, 3, key))
            .itemOutputs(new ItemStack(output, 2, key))
            .fluidInputs(new FluidStack(fluid, 1000))
            .duration(120).eut(30)
            .metadata(MARK, mark)
            .metadata(GTRecipeConstants.SKIP_CELL_RECIPE_GENERATION, false)
            .build().get();
    }

    private static List<GTRecipe> recipes(RecipeMap<?> map, int key) {
        List<GTRecipe> result = new ArrayList<>();
        for (GTRecipe recipe : map.getAllRecipes()) {
            for (ItemStack ingredient : recipe.mInputs) {
                if (ingredient.getItem() == input && ingredient.getItemDamage() == key) {
                    result.add(recipe);
                    break;
                }
            }
        }
        return result;
    }

    private static void tag(GTRecipe recipe, String value) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("variant", value);
        recipe.mInputs[0].setTagCompound(tag);
        recipe.isNBTSensitive = true;
    }

    public static void run(String version) throws Exception {
        Issue37Runtime.bootstrap(version);
        Issue37Runtime.set(null, cpw.mods.fml.relauncher.FMLRelaunchLog.class, "side", Side.SERVER);
        MARK = SimpleRecipeMetadataKey.create(String.class, "core_dedup_mark");
        input = item("input", 4200); output = item("output", 4201); cell = item("cell", 4202);
        ItemList.Cell_Empty.set(new ItemStack(cell, 1, 0));
        ItemList.Circuit_Integrated.set(new ItemStack(item("circuit", 4203), 1, 0));
        fluid = new Fluid("core_dedup_fluid");
        if (!FluidRegistry.registerFluid(fluid)) throw new AssertionError("Fixture fluid duplicate");
        for (int i : new int[] { 1, 2 }) FluidContainerRegistry.registerFluidContainer(new FluidStack(fluid, 1000),
            new ItemStack(cell, 1, i), new ItemStack(cell, 1, 0));

        RecipeMap<?> nativeSource = RecipeMapBuilder.of("core_dedup.native_input").maxIO(4, 4, 4, 4).disableRegisterNEI().build();
        RecipeMap<?> nativeTarget = RecipeMapBuilder.of("core_dedup.native_output").maxIO(4, 4, 4, 4).disableRegisterNEI().build();
        GTRecipe first = recipe(1, 1, "containers", false, 10000);
        GTRecipe second = recipe(1, 2, "containers", false, 10000);
        nativeSource.add(recipe(1, 1, "containers", false, 10000));
        nativeSource.add(recipe(1, 2, "containers", false, 10000));
        RecipeGenMultisUsingFluidInsteadOfCells.generateRecipesNotUsingCells(nativeSource, nativeTarget);
        check("native-two-containers-collapse", recipes(nativeTarget, 1).size() == 1);
        nativeSource.getBackend().clearRecipes();
        nativeTarget.getBackend().clearRecipes();

        RecipeMap<?> target = RecipeMaps.mixerNonCellRecipes;
        RecipeCategory special = new RecipeCategory("core_dedup.special", target,
            () -> RecipeCategorySetting.ENABLE, builder -> builder);
        RecipeCategory sourceCategoryA = new RecipeCategory("core_dedup.source_a", RecipeMaps.mixerRecipes,
            () -> RecipeCategorySetting.ENABLE, builder -> builder);
        RecipeCategory sourceCategoryB = new RecipeCategory("core_dedup.source_b", RecipeMaps.mixerRecipes,
            () -> RecipeCategorySetting.ENABLE, builder -> builder);
        GTRecipe existing = normalized(2, "existing");
        existing.mOutputChances = new int[] { 10000, 10000 };
        target.add(existing);
        GTRecipe categoryExisting = normalized(5, "category");
        categoryExisting.mOutputChances = new int[] { 10000, 10000 };
        categoryExisting.setRecipeCategory(special);
        target.add(categoryExisting);

        Map<String, RecipeMap<?>> mapsBefore = new HashMap<>(RecipeMap.ALL_RECIPE_MAPS);
        Map<String, RecipeCategory> categoriesBefore = new HashMap<>(RecipeCategory.ALL_RECIPE_CATEGORIES);
        Class<?> adapter = Class.forName("com.miaokatze.miaokatzecore.api.recipe.GTNonCellRecipes");
        Constructor<?> constructor = adapter.getDeclaredConstructor(); constructor.setAccessible(true);
        Object delta = constructor.newInstance();
        Method before = adapter.getDeclaredMethod("beforeMain"); before.setAccessible(true); before.invoke(delta);

        List<GTRecipe> sources = new ArrayList<>();
        sources.add(first); sources.add(second);
        sources.add(recipe(2, 1, "existing", false, 10000));
        sources.add(recipe(3, 1, "metadata-a", false, 10000));
        sources.add(recipe(3, 2, "metadata-b", false, 10000));
        sources.add(recipe(4, 1, "skip", true, 10000));
        sources.add(recipe(4, 2, "non-skip", false, 10000));
        sources.add(recipe(5, 1, "category", false, 10000));
        sources.add(recipe(6, 1, "chance", false, 7000));
        sources.add(recipe(6, 2, "chance", false, 8000));
        sources.add(recipe(7, 1, "duration", false, 10000));
        GTRecipe slower = recipe(7, 2, "duration", false, 10000); slower.mDuration = 240; sources.add(slower);
        GTRecipe nbtA = recipe(8, 1, "nbt", false, 10000); tag(nbtA, "a"); sources.add(nbtA);
        GTRecipe nbtB = recipe(8, 2, "nbt", false, 10000); tag(nbtB, "b"); sources.add(nbtB);
        GTRecipe specialA = recipe(10, 1, "special", false, 10000);
        specialA.mSpecialItems = new ItemStack(output, 1, 10); sources.add(specialA);
        GTRecipe specialB = recipe(10, 2, "special", false, 10000);
        specialB.mSpecialItems = new ItemStack(output, 1, 11); sources.add(specialB);
        GTRecipe valueA = recipe(11, 1, "special-value", false, 10000); valueA.mSpecialValue = 10; sources.add(valueA);
        GTRecipe valueB = recipe(11, 2, "special-value", false, 10000); valueB.mSpecialValue = 11; sources.add(valueB);
        for (int flag = 0; flag < 6; flag++) {
            GTRecipe ordinary = recipe(30 + flag, 1, "flags", false, 10000);
            GTRecipe changed = recipe(30 + flag, 2, "flags", false, 10000);
            switch (flag) {
                case 0 -> changed.mEnabled = false;
                case 1 -> changed.mHidden = true;
                case 2 -> changed.mFakeRecipe = true;
                case 3 -> changed.mCanBeBuffered = false;
                case 4 -> changed.mNeedsEmptyOutput = true;
                case 5 -> changed.isNBTSensitive = true;
            }
            sources.add(ordinary); sources.add(changed);
        }
        GTRecipe outputA = recipe(12, 1, "output", false, 10000); sources.add(outputA);
        GTRecipe outputB = recipe(12, 2, "output", false, 10000);
        outputB.mOutputs[0].setItemDamage(112); sources.add(outputB);
        for (int i : new int[] { 1, 2 }) {
            GTRecipe circuit = recipe(13, i, "circuit", false, 10000);
            circuit.setInputs(circuit.mInputs[0], circuit.mInputs[1], ItemList.Circuit_Integrated.getWithDamage(1, i));
            sources.add(circuit);
        }
        GTRecipe ordered = recipe(14, 1, "unordered", false, 10000);
        ordered.setInputs(ordered.mInputs[0], new ItemStack(input, 1, 900), ordered.mInputs[1]); sources.add(ordered);
        GTRecipe reordered = recipe(14, 2, "unordered", false, 10000);
        reordered.setInputs(new ItemStack(input, 1, 900), reordered.mInputs[0], reordered.mInputs[1]); sources.add(reordered);
        GTRecipe chanceOrderA = recipe(15, 1, "chance-association", false, 10000);
        chanceOrderA.setOutputs(chanceOrderA.mOutputs[0], new ItemStack(output, 1, 901), chanceOrderA.mOutputs[1]);
        chanceOrderA.mOutputChances = new int[] { 7000, 8000, 10000 }; sources.add(chanceOrderA);
        GTRecipe chanceOrderB = recipe(15, 2, "chance-association", false, 10000);
        chanceOrderB.setOutputs(new ItemStack(output, 1, 901), chanceOrderB.mOutputs[0], chanceOrderB.mOutputs[1]);
        chanceOrderB.mOutputChances = new int[] { 7000, 8000, 10000 }; sources.add(chanceOrderB);
        for (int i : new int[] { 1, 2 }) {
            GTRecipe taggedFluid = recipe(16, i, "fluid-nbt", false, 10000);
            FluidStack ingredient = new FluidStack(fluid, 500);
            ingredient.tag = new NBTTagCompound(); ingredient.tag.setInteger("variant", i);
            taggedFluid.setFluidInputs(ingredient); sources.add(taggedFluid);
            GTRecipe fluidChance = recipe(17, i, "fluid-chance", false, 10000);
            fluidChance.setFluidOutputs(new FluidStack(fluid, 500));
            fluidChance.mFluidOutputChances = new int[] { 6000 + i * 1000 }; sources.add(fluidChance);
        }
        GTRecipe sourceCategoryRecipeA = recipe(18, 1, "source-category", false, 10000);
        sourceCategoryRecipeA.setRecipeCategory(sourceCategoryA); sources.add(sourceCategoryRecipeA);
        GTRecipe sourceCategoryRecipeB = recipe(18, 2, "source-category", false, 10000);
        sourceCategoryRecipeB.setRecipeCategory(sourceCategoryB); sources.add(sourceCategoryRecipeB);
        for (GTRecipe source : sources) RecipeMaps.mixerRecipes.add(source);
        RecipeMaps.coldTrapRecipes.add(recipe(9, 1, "shared-target", false, 10000));
        RecipeMaps.reactorProcessingUnitRecipes.add(recipe(9, 2, "shared-target", false, 10000));

        Method derive = adapter.getDeclaredMethod("derive"); derive.setAccessible(true); derive.invoke(delta);
        check("core-two-containers-collapse", recipes(target, 1).size() == 1);
        check("core-existing-target-not-appended", recipes(target, 2).size() == 1);
        check("existing-target-object-preserved", recipes(target, 2).contains(existing));
        check("different-metadata-preserved", recipes(target, 3).size() == 2
            && recipes(target, 3).stream().anyMatch(r -> "metadata-a".equals(r.getMetadata(MARK)))
            && recipes(target, 3).stream().anyMatch(r -> "metadata-b".equals(r.getMetadata(MARK))));
        check("skip-excluded-non-skip-preserved", recipes(target, 4).size() == 1
            && "non-skip".equals(recipes(target, 4).get(0).getMetadata(MARK)));
        check("different-target-category-preserved", recipes(target, 5).size() == 2
            && recipes(target, 5).contains(categoryExisting) && categoryExisting.getRecipeCategory() == special);
        check("different-chances-preserved", recipes(target, 6).size() == 2);
        check("same-input-different-duration-preserved", recipes(target, 7).size() == 2);
        check("different-nbt-preserved", recipes(target, 8).size() == 2
            && recipes(target, 8).stream().allMatch(r -> r.isNBTSensitive));
        check("cross-source-shared-target-collapse", recipes(RecipeMaps.nuclearSaltProcessingPlantRecipes, 9).size() == 1);
        check("different-special-items-preserved", recipes(target, 10).size() == 2);
        check("different-special-value-preserved", recipes(target, 11).size() == 2);
        for (int flag = 0; flag < 6; flag++) check("different-flag-preserved-" + flag, recipes(target, 30 + flag).size() == 2);
        check("same-input-different-output-preserved", recipes(target, 12).size() == 2);
        check("different-circuit-preserved", recipes(target, 13).size() == 2
            && recipes(target, 13).stream().anyMatch(r -> java.util.Arrays.stream(r.mInputs)
                .anyMatch(stack -> stack.getItemDamage() == 1 && ItemList.Circuit_Integrated.isStackEqual(stack, true, true)))
            && recipes(target, 13).stream().anyMatch(r -> java.util.Arrays.stream(r.mInputs)
                .anyMatch(stack -> stack.getItemDamage() == 2 && ItemList.Circuit_Integrated.isStackEqual(stack, true, true))));
        check("native-unordered-input-equivalence-retained", recipes(target, 14).size() == 1);
        check("chance-output-association-preserved", recipes(target, 15).size() == 2);
        check("different-fluid-nbt-preserved", recipes(target, 16).size() == 2);
        check("different-fluid-chance-preserved", recipes(target, 17).size() == 2);
        check("different-source-category-entries-preserved", recipes(target, 18).size() == 2);
        check("deduplicated-recipe-native-query", target.findRecipeQuery().items(new ItemStack(input, 3, 1))
            .fluids(new FluidStack(fluid, 1000)).voltage(Long.MAX_VALUE).find() == recipes(target, 1).get(0));
        check("no-temporary-map-residue", RecipeMap.ALL_RECIPE_MAPS.equals(mapsBefore));
        check("no-temporary-category-residue", RecipeCategory.ALL_RECIPE_CATEGORIES.equals(categoriesBefore));
        check("source-container-and-metadata-unmodified", first.mInputs.length == 2 && second.mInputs.length == 2
            && first.mInputs[1].getItemDamage() == 1 && second.mInputs[1].getItemDamage() == 2
            && "containers".equals(first.getMetadata(MARK)));
        check("native-equivalence-ignores-metadata", RecipeHashStrat.areRecipesEqual(recipes(target, 3).get(0), recipes(target, 3).get(1)));
        check("native-equivalence-ignores-category", RecipeHashStrat.areRecipesEqual(categoryExisting, recipes(target, 5).stream()
            .filter(r -> r != categoryExisting).findFirst().get()));
        check("native-equivalence-ignores-chance", GTRecipeUtils.removeDuplicates(new ArrayList<>(recipes(target, 6))).size() == 1);

        System.out.println("RESULT checks=" + checks + " failures=" + failures);
        System.out.println("SOURCE GT=" + RecipeGenMultisUsingFluidInsteadOfCells.class.getProtectionDomain().getCodeSource().getLocation());
        System.out.println("SOURCE Core=" + adapter.getProtectionDomain().getCodeSource().getLocation());
        if (failures != 0) throw new AssertionError("Core NonCell dedup failures=" + failures);
    }
}
