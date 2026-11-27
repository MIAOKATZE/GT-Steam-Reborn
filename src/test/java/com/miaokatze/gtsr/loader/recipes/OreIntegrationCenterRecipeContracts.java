// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.loader.recipes;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.items.CenterModuleItems;
import com.miaokatze.gtsr.common.machine.orecenter.CenterModule;

import cpw.mods.fml.common.registry.GameData;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;
import gtPlusPlus.xmod.gregtech.api.enums.GregtechItemList;

/** Native GT .277 recipe APIs with isolated ingredient identities, not full mod initialization or a running game. */
public final class OreIntegrationCenterRecipeContracts {

    private static int checks;

    private OreIntegrationCenterRecipeContracts() {}

    public static void main(String[] args) throws Exception {
        Class<?> bootstrap = Class.forName("com.miaokatze.gtsr.compat.gtnl.GtnlNativeBootstrap");
        Method enter = bootstrap.getDeclaredMethod("enter", String.class, String[].class);
        enter.setAccessible(true);
        if (!(Boolean) enter.invoke(null, OreIntegrationCenterRecipeContracts.class.getName(), args)) return;
        Method setup = PortableWeaponRecipeContracts.class.getDeclaredMethod("bootstrapGt");
        setup.setAccessible(true);
        setup.invoke(null);
        // Isolate this regression even if a shared bootstrap later starts supplying unrelated GT++ entries.
        GregtechItemList obsoleteSeparator = GregtechItemList.Industrial_Electromagnetic_Separator;
        Field legacyStack = GregtechItemList.class.getDeclaredField("mStack");
        Field legacyUnset = GregtechItemList.class.getDeclaredField("mHasNotBeenSet");
        legacyStack.setAccessible(true);
        legacyUnset.setAccessible(true);
        Object previousStack = legacyStack.get(obsoleteSeparator);
        boolean previousUnset = legacyUnset.getBoolean(obsoleteSeparator);
        try {
            legacyStack.set(obsoleteSeparator, null);
            legacyUnset.setBoolean(obsoleteSeparator, true);
            prepareIngredients();
            check(!obsoleteSeparator.hasBeenSet(), "obsolete GT++ separator is absent from ingredient fixtures");
            verifyRecipes();
            check(!obsoleteSeparator.hasBeenSet(), "full recipe registration never initializes the obsolete separator");
        } finally {
            legacyStack.set(obsoleteSeparator, previousStack);
            legacyUnset.setBoolean(obsoleteSeparator, previousUnset);
        }
        System.out.println("PASS ore center manufacturing: " + checks + " checks; game_started=false");
    }

    private static void verifyRecipes() {
        List<IRecipe> crafting = CraftingManager.getInstance()
            .getRecipeList();
        int start = crafting.size();
        OreIntegrationCenterRecipes.register();
        List<IRecipe> addedCrafting = crafting.subList(start, crafting.size());
        check(addedCrafting.size() == 12, "ten mechanical packs, first shell and controller use the workbench");
        check(
            RecipeMaps.assemblerRecipes.getAllRecipes()
                .size() == 26,
            "seven electric I packs, seventeen II and two shells");
        check(
            GTRecipe.RecipeAssemblyLine.sAssemblylineRecipes.size() == 17,
            "all seventeen III packs use the native line");
        check(
            RecipeMaps.scannerFakeRecipes.getAllRecipes()
                .size() == 17,
            "native scanner research registered for every III pack");
        for (IRecipe recipe : addedCrafting) check(recipe.getRecipeSize() <= 9, "physical workbench slots fit");

        for (CenterModule module : CenterModule.values()) {
            for (int grade = 1; grade <= 3; grade++) {
                ItemStack output = CenterModuleItems.stack(module, grade);
                int craftingCount = 0;
                for (IRecipe recipe : addedCrafting) if (same(recipe.getRecipeOutput(), output)) craftingCount++;
                int assemblerCount = 0;
                for (GTRecipe recipe : RecipeMaps.assemblerRecipes.getAllRecipes()) {
                    if (same(recipe.mOutputs[0], output)) {
                        assemblerCount++;
                        check(grade != 3, "no assembler shortcut to " + module + " III");
                        assembler(module, grade, recipe);
                    }
                }
                int lineCount = 0;
                for (GTRecipe.RecipeAssemblyLine recipe : GTRecipe.RecipeAssemblyLine.sAssemblylineRecipes) {
                    if (same(recipe.mOutput, output)) {
                        lineCount++;
                        check(grade == 3, "line output is grade III");
                        line(module, recipe);
                    }
                }
                check(
                    craftingCount + assemblerCount + lineCount == 1,
                    "exactly one manufacturing route for " + module + " " + grade);
                if (grade == 3) check(lineCount == 1, "research line is compulsory for " + module);
            }
        }
        for (int grade = 1; grade <= 3; grade++) {
            ItemStack output = CenterModuleItems.shell(grade);
            int routes = 0;
            for (IRecipe recipe : addedCrafting) if (same(recipe.getRecipeOutput(), output)) routes++;
            for (GTRecipe recipe : RecipeMaps.assemblerRecipes.getAllRecipes())
                if (same(recipe.mOutputs[0], output)) routes++;
            check(routes == 1, "exactly one recipe for shell " + grade);
        }
    }

    private static void assembler(CenterModule module, int grade, GTRecipe recipe) {
        check(recipe.mInputs.length <= 9, "assembler input slots fit");
        check(amount(recipe.mInputs, CenterModuleItems.shell(grade)) == 1, "correct shell is consumed");
        if (grade == 2)
            check(amount(recipe.mInputs, CenterModuleItems.stack(module, 1)) == 1, "II consumes matching I pack");
        int selectors = 0;
        ItemStack[] offered = new ItemStack[recipe.mInputs.length];
        ItemStack selector = null;
        for (int i = 0; i < offered.length; i++) {
            offered[i] = recipe.mInputs[i].copy();
            if (offered[i].getItem() == ItemList.Circuit_Integrated.getItem()) {
                selectors++;
                check(offered[i].stackSize == 0, "selector has zero consumed quantity");
                check(
                    offered[i].getItemDamage() == CenterModuleItems.stack(module, 1)
                        .getItemDamage() / 3 + 1,
                    "fixed selector follows saved module order");
                offered[i].stackSize = 1;
                selector = offered[i];
            }
        }
        check(selectors == 1, "exactly one selector");
        check(
            recipe.isRecipeInputEqual(true, new FluidStack[0], offered),
            "native recipe matching consumes the ingredient set");
        check(selector != null && selector.stackSize == 1, "native consumption leaves the selector intact");
        if (grade == 2) {
            Materials circuitTier = module == CenterModule.DRYER ? Materials.IV
                : module == CenterModule.FURNACE || module == CenterModule.REACTOR ? Materials.EV : Materials.HV;
            check(
                amount(recipe.mInputs, GTOreDictUnificator.get(OrePrefixes.circuit, circuitTier, 1)) == 8,
                "II consumes eight real circuits");
        }
        int minimum = module == CenterModule.DRYER ? 7680
            : module == CenterModule.FURNACE || module == CenterModule.REACTOR ? 1920
                : module == CenterModule.EXTRACTOR ? 30 : 120;
        if (grade == 1) check(recipe.mEUt >= minimum, "existing electric, blast, chemical and dryer gates remain");
    }

    private static void line(CenterModule module, GTRecipe.RecipeAssemblyLine recipe) {
        ItemStack previous = CenterModuleItems.stack(module, 2);
        check(same(recipe.mResearchItem, previous), "research scans the corresponding II pack");
        check(amount(recipe.mInputs, previous) == 1, "line consumes the corresponding II pack");
        check(amount(recipe.mInputs, CenterModuleItems.shell(3)) == 1, "line consumes shell III");
        if (module == CenterModule.SEPARATOR) check(
            amount(recipe.mInputs, ItemList.Machine_Multi_IndustrialElectromagneticSeparator.get(1)) == 2,
            "separator III consumes two identities of the GT industrial electromagnetic separator entry");
        check(recipe.mInputs.length >= 4 && recipe.mInputs.length <= 16, "native line item slot bounds");
        check(recipe.mFluidInputs.length >= 1 && recipe.mFluidInputs.length <= 4, "native line fluid slot bounds");
        boolean dryer = module == CenterModule.DRYER;
        check(recipe.mEUt == (dryer ? 30720 : 7680), "recipe power is independent of LuV line infrastructure");
        check(recipe.mResearchVoltage == (dryer ? 30720 : 7680), "native scanner voltage is explicit");
        check(recipe.mResearchTime >= (dryer ? 3600 : 2400), "research duration is enforced");
        for (ItemStack input : recipe.mInputs) check(input.stackSize > 0, "all physical line ingredients are consumed");
        int scanRoutes = 0;
        for (GTRecipe scan : RecipeMaps.scannerFakeRecipes.getAllRecipes()) {
            if (same(scan.mOutputs[0], recipe.mOutput)) {
                scanRoutes++;
                check(same(scan.mInputs[0], previous), "native scanner advertises matching II input");
                check(
                    scan.mSpecialValue == -201 && scan.mSpecialItems != null,
                    "native scanned research data-stick path");
            }
        }
        check(scanRoutes == 1, "one scanner research route per III pack");
    }

    private static void prepareIngredients() throws Exception {
        Method fixture = PortableWeaponRecipeContracts.class.getDeclaredMethod("item", String.class);
        fixture.setAccessible(true);
        Item components = (Item) fixture.invoke(null, "gtsr_ore_recipe_contract:components");
        int metadata = 0;
        // Reviewed, finite recipe dependencies. Add new identities explicitly when a recipe changes;
        // populating every enum value hides production references to controllers GT never registers.
        ItemList[] gtIngredients = { ItemList.Casing_BronzePlatedBricks, ItemList.Casing_Chemically_Inert,
            ItemList.Casing_Coil_Cupronickel, ItemList.Casing_Coil_Nichrome, ItemList.Casing_Gearbox_Bronze,
            ItemList.Casing_Gearbox_TungstenSteel, ItemList.Casing_HeatProof, ItemList.Casing_RobustTungstenSteel,
            ItemList.Casing_SolidSteel, ItemList.Conveyor_Module_HV, ItemList.Conveyor_Module_IV,
            ItemList.Electric_Motor_HV, ItemList.Electric_Motor_IV, ItemList.Electric_Piston_EV,
            ItemList.Electric_Piston_HV, ItemList.Electric_Piston_IV, ItemList.Electric_Pump_EV,
            ItemList.Electric_Pump_HV, ItemList.Electric_Pump_IV, ItemList.Electric_Pump_LuV, ItemList.Electric_Pump_LV,
            ItemList.Machine_Bronze_Furnace, ItemList.Machine_Bronze_Macerator, ItemList.Machine_EV_ChemicalReactor,
            ItemList.Machine_HV_Centrifuge, ItemList.Machine_HV_E_Furnace, ItemList.Machine_HV_Electrolyzer,
            ItemList.Machine_HV_ElectromagneticSeparator, ItemList.Machine_HV_Extractor,
            ItemList.Machine_HV_FluidExtractor, ItemList.Machine_HV_Macerator, ItemList.Machine_HV_OreWasher,
            ItemList.Machine_HV_Sifter, ItemList.Machine_HV_ThermalCentrifuge, ItemList.Machine_IV_Extractor,
            ItemList.Machine_IV_FluidExtractor, ItemList.Machine_LV_Extractor, ItemList.Machine_LV_FluidExtractor,
            ItemList.Machine_Multi_BlastFurnace, ItemList.Machine_Multi_Furnace,
            ItemList.Machine_Multi_IndustrialElectromagneticSeparator, ItemList.Machine_Multi_LargeChemicalReactor,
            ItemList.Machine_MV_Electrolyzer, ItemList.Machine_MV_ElectromagneticSeparator,
            ItemList.Machine_MV_ThermalCentrifuge, ItemList.Robot_Arm_HV, ItemList.Robot_Arm_IV, ItemList.Sensor_HV,
            ItemList.Sensor_IV,
            // Native assembly-line research advertises these data carriers.
            ItemList.Tool_DataStick, ItemList.Tool_DataOrb };
        GregtechItemList[] gtppIngredients = { GregtechItemList.Casing_Adv_BlastFurnace,
            GregtechItemList.Casing_Centrifuge1, GregtechItemList.Casing_Electrolyzer,
            GregtechItemList.Casing_MacerationStack, GregtechItemList.Casing_Sifter,
            GregtechItemList.Casing_SifterGrate, GregtechItemList.Casing_ThermalCentrifuge,
            GregtechItemList.Casing_Vacuum_Furnace, GregtechItemList.Casing_WashPlant,
            GregtechItemList.Controller_SteamCentrifugeMulti, GregtechItemList.Controller_SteamFurnaceMulti,
            GregtechItemList.Controller_SteamMaceratorMulti, GregtechItemList.Controller_SteamWasherMulti,
            GregtechItemList.Controller_Vacuum_Furnace, GregtechItemList.GT_Dehydrator_IV,
            GregtechItemList.Hatch_Input_Bus_Steam, GregtechItemList.Hatch_Output_Bus_Steam,
            GregtechItemList.Industrial_Centrifuge, GregtechItemList.Industrial_Electrolyzer,
            GregtechItemList.Industrial_MacerationStack, GregtechItemList.Industrial_Sifter,
            GregtechItemList.Industrial_ThermalCentrifuge, GregtechItemList.Industrial_WashPlant,
            GregtechItemList.Machine_Adv_BlastFurnace };
        for (ItemList entry : gtIngredients) entry.set(new ItemStack(components, 1, metadata++));
        for (GregtechItemList entry : gtppIngredients) entry.set(new ItemStack(components, 1, metadata++));
        ItemList.Circuit_Integrated.set((Item) fixture.invoke(null, "gtsr_ore_recipe_contract:selector"));
        for (GTSRItemList entry : Arrays.asList(
            GTSRItemList.SteamEntangledSingularity,
            GTSRItemList.OreIntegrationCenter,
            GTSRItemList.KineticProcessingArray)) {
            entry.set(new ItemStack(components, 1, metadata++));
        }
        metadata = ores(components, metadata, OrePrefixes.bolt, Materials.Steel, Materials.Titanium);
        metadata = ores(
            components,
            metadata,
            OrePrefixes.circuit,
            Materials.MV,
            Materials.HV,
            Materials.EV,
            Materials.IV,
            Materials.LuV);
        metadata = ores(
            components,
            metadata,
            OrePrefixes.frameGt,
            Materials.Bronze,
            Materials.StainlessSteel,
            Materials.TungstenSteel);
        metadata = ores(components, metadata, OrePrefixes.gearGt, Materials.Bronze, Materials.TungstenSteel);
        metadata = ores(
            components,
            metadata,
            OrePrefixes.pipeLarge,
            Materials.Bronze,
            Materials.Titanium,
            Materials.Polytetrafluoroethylene);
        metadata = ores(
            components,
            metadata,
            OrePrefixes.plate,
            Materials.Bronze,
            Materials.Steel,
            Materials.Titanium,
            Materials.TungstenSteel);
        metadata = ores(components, metadata, OrePrefixes.plateDouble, Materials.Bronze);
        metadata = ores(
            components,
            metadata,
            OrePrefixes.ring,
            Materials.Bronze,
            Materials.StainlessSteel,
            Materials.TungstenSteel);
        metadata = ores(
            components,
            metadata,
            OrePrefixes.rotor,
            Materials.Bronze,
            Materials.StainlessSteel,
            Materials.TungstenSteel);
        ores(
            components,
            metadata,
            OrePrefixes.stick,
            Materials.Steel,
            Materials.SteelMagnetic,
            Materials.NeodymiumMagnetic);
        Fluid solder = new Fluid("gtsr_ore_recipe_contract_solder");
        Fluid lubricant = new Fluid("gtsr_ore_recipe_contract_lubricant");
        check(
            FluidRegistry.registerFluid(solder) && FluidRegistry.registerFluid(lubricant),
            "isolated line fluids registered");
        Materials.SolderingAlloy.mStandardMoltenFluid = solder;
        Materials.Lubricant.mFluid = lubricant;

        Constructor<CenterModuleItems> constructor = CenterModuleItems.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        CenterModuleItems modules = constructor.newInstance();
        Field instance = CenterModuleItems.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, modules);
        // Persistent assembly-line hashes require a registered identity even in this isolated fixture.
        Field controllerField = cpw.mods.fml.common.Loader.class.getDeclaredField("modController");
        controllerField.setAccessible(true);
        Object controller = controllerField.get(cpw.mods.fml.common.Loader.instance());
        Field active = cpw.mods.fml.common.LoadController.class.getDeclaredField("activeContainer");
        active.setAccessible(true);
        Object previous = active.get(controller);
        cpw.mods.fml.common.ModMetadata owner = new cpw.mods.fml.common.ModMetadata();
        owner.modId = "gtsr_ore_recipe_contract";
        owner.name = "Isolated ore-center recipe identities";
        active.set(controller, new cpw.mods.fml.common.DummyModContainer(owner));
        try {
            GameData.getItemRegistry()
                .addObject(28800, "gtsr_ore_recipe_contract:modules", modules);
        } finally {
            active.set(controller, previous);
        }
    }

    private static int ores(Item components, int metadata, OrePrefixes prefix, Materials... materials) {
        for (Materials material : materials)
            GTOreDictUnificator.set(prefix, material, new ItemStack(components, 1, metadata++));
        return metadata;
    }

    private static boolean same(ItemStack left, ItemStack right) {
        return left != null && right != null && GTUtility.areStacksEqual(left, right, true);
    }

    private static int amount(ItemStack[] inputs, ItemStack expected) {
        int total = 0;
        for (ItemStack input : inputs) if (same(input, expected)) total += input.stackSize;
        return total;
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
    }
}
