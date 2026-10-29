import java.util.*;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.loader.recipes.CriticalMEHatchRecipes;

import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.enums.TierEU;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipeConstants;
import gregtech.api.util.GTUtility;
import gtPlusPlus.core.material.MaterialsAlloy;
import gtnhintergalactic.recipe.IGRecipeMaps;

/** Actual GT205 recipe registration with isolated ingredient identities; no game or native registry claim. */
public final class CriticalMERecipesContract {
    public static void main(String[] args) throws Exception {
        Issue37RegistrationRegression.launch("CriticalMERecipesRuntime", "5.09.54.205");
    }
}

class CriticalMERecipesRuntime {
    static final java.io.PrintStream OUT = System.out;
    static int checks;
    static int nextId = 21000;
    static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
    }
    static ItemStack identity() { return new ItemStack(Items.feather, 1, nextId++); }
    static Fluid fluid(String name) {
        Fluid value = new Fluid("critical_me_fixture_" + name);
        FluidRegistry.registerFluid(value);
        return value;
    }
    static ItemStack ore(OrePrefixes prefix, Materials material) {
        ItemStack stack = identity();
        String key = prefix.get(material).toString();
        GTOreDictUnificator.getName2StackMap().put(key, stack);
        OreDictionary.registerOre(key, stack);
        return stack;
    }
    static void ingredient(GTRecipe recipe, ItemStack expected, int amount) {
        int matches = 0;
        for (ItemStack actual : recipe.mInputs)
            if (GTUtility.areStacksEqual(actual, expected)) {
                check(actual.stackSize == amount, "exact ingredient count");
                matches++;
            }
        check(matches == 1, "exactly one slot per ingredient");
    }
    static void fluidIngredient(GTRecipe recipe, Fluid expected, int amount) {
        int matches = 0;
        for (FluidStack actual : recipe.mFluidInputs)
            if (actual.getFluid() == expected) {
                check(actual.amount == amount, "exact fluid volume");
                matches++;
            }
        check(matches == 1, "exactly one slot per fluid");
    }
    public static void run(String version) throws Exception {
        Issue37Runtime.bootstrap(version);
        Issue37Runtime.set(null, cpw.mods.fml.relauncher.FMLRelaunchLog.class, "side", cpw.mods.fml.relauncher.Side.SERVER);
        gregtech.api.enums.GTValues.RA = (gregtech.api.interfaces.internal.IGTRecipeAdder) java.lang.reflect.Proxy.newProxyInstance(
            CriticalMERecipesRuntime.class.getClassLoader(), new Class<?>[] { gregtech.api.interfaces.internal.IGTRecipeAdder.class },
            (proxy, method, args) -> method.getName().equals("stdBuilder") ? gregtech.api.util.GTRecipeBuilder.builder() : null);
        ItemList[] originals = { ItemList.Hatch_Input_ME_Advanced, ItemList.Hatch_Input_Bus_ME_Advanced,
            ItemList.Hatch_Output_ME, ItemList.Hatch_Output_Bus_ME };
        GTSRItemList[] outputs = { GTSRItemList.CriticalInputHatchME, GTSRItemList.CriticalInputBusME,
            GTSRItemList.CriticalOutputHatchME, GTSRItemList.CriticalOutputBusME };
        for (ItemList item : originals) item.set(identity());
        for (GTSRItemList item : outputs) item.set(identity());
        for (ItemList item : new ItemList[] { ItemList.Hull_MAX, ItemList.Electric_Pump_UHV,
            ItemList.Conveyor_Module_UHV, ItemList.Sensor_UHV, ItemList.Emitter_UHV }) item.set(identity());
        GTSRItemList.CriticalSteamEntangledSingularity.set(identity());
        ItemStack circuit = ore(OrePrefixes.circuit, Materials.UHV);
        ItemStack plate = ore(OrePrefixes.plate, Materials.CosmicNeutronium);
        ItemStack cable = ore(OrePrefixes.cableGt02, Materials.Bedrockium);
        Fluid solder = fluid("indalloy"), lubricant = fluid("lubricant"), pbi = fluid("pbi");
        MaterialsAlloy.INDALLOY_140.setFluid(solder);
        Materials.Lubricant.mFluid = lubricant;
        Materials.Polybenzimidazole.mStandardMoltenFluid = pbi;
        int assemblyLines = GTRecipe.RecipeAssemblyLine.sAssemblylineRecipes.size();
        OUT.println("SOURCE critical ME recipe " + CriticalMEHatchRecipes.class.getProtectionDomain().getCodeSource().getLocation());
        OUT.println("SOURCE critical ME item list " + GTSRItemList.class.getProtectionDomain().getCodeSource().getLocation());
        CriticalMEHatchRecipes.register();
        check(GTRecipe.RecipeAssemblyLine.sAssemblylineRecipes.size() == assemblyLines,
            "space-module crafting does not add ordinary assembly-line recipes");
        for (int i = 0; i < outputs.length; i++) {
            GTRecipe found = null;
            int count = 0;
            for (RecipeMap<?> map : RecipeMap.ALL_RECIPE_MAPS.values())
                for (GTRecipe recipe : map.getAllRecipes())
                    for (ItemStack output : recipe.mOutputs)
                        if (outputs[i].isStackEqual(output)) {
                            check(map == IGRecipeMaps.spaceAssemblerRecipes, "only real space-assembler map");
                            check(!recipe.mFakeRecipe && recipe.mEnabled, "enabled executable recipe");
                            found = recipe;
                            count++;
                        }
            check(count == 1, "exactly one way to craft each new hatch");
            check(found.mOutputs.length == 1 && found.mOutputs[0].stackSize == 1, "one finished hatch");
            check(found.mInputs.length == 8 && found.mFluidInputs.length == 3, "eight item and three fluid slots");
            check(found.mDuration == 1200 && found.mEUt == TierEU.RECIPE_UHV, "60 seconds at UHV");
            check(Objects.equals(found.getMetadata(IGRecipeMaps.MODULE_TIER), 1), "actual module-tier metadata");
            check(found.getMetadata(IGRecipeMaps.SPACE_PROJECT) == null
                && found.getMetadata(IGRecipeMaps.SPACE_LOCATION) == null, "no extra space-project gate");
            check(!found.getMetadataOrDefault(GTRecipeConstants.LOW_GRAVITY, false), "no low-gravity restriction");
            ingredient(found, originals[i].get(1), 1);
            ingredient(found, ItemList.Hull_MAX.get(1), 1);
            ingredient(found, (i % 2 == 0 ? ItemList.Electric_Pump_UHV : ItemList.Conveyor_Module_UHV).get(1), 2);
            ingredient(found, (i < 2 ? ItemList.Sensor_UHV : ItemList.Emitter_UHV).get(1), 2);
            ingredient(found, circuit, 4);
            ingredient(found, plate, 8);
            ingredient(found, cable, 4);
            ingredient(found, GTSRItemList.CriticalSteamEntangledSingularity.get(1), 16);
            fluidIngredient(found, solder, 2304);
            fluidIngredient(found, lubricant, 1000);
            fluidIngredient(found, pbi, 1152);
            OUT.println("RECIPE " + outputs[i] + " map=spaceAssembler tier=1 eut=" + found.mEUt
                + " ticks=" + found.mDuration + " singularities=16");
        }
        OUT.println("PASS CriticalMERecipesContract checks=" + checks);
    }
}
