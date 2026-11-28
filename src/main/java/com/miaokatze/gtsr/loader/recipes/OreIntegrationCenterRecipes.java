// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.loader.recipes;

import static gregtech.api.enums.Materials.Bronze;
import static gregtech.api.enums.Materials.EV;
import static gregtech.api.enums.Materials.HV;
import static gregtech.api.enums.Materials.IV;
import static gregtech.api.enums.Materials.LuV;
import static gregtech.api.enums.Materials.MV;
import static gregtech.api.enums.Materials.NeodymiumMagnetic;
import static gregtech.api.enums.Materials.Polytetrafluoroethylene;
import static gregtech.api.enums.Materials.StainlessSteel;
import static gregtech.api.enums.Materials.Steel;
import static gregtech.api.enums.Materials.SteelMagnetic;
import static gregtech.api.enums.Materials.Titanium;
import static gregtech.api.enums.Materials.TungstenSteel;
import static gregtech.api.enums.OrePrefixes.bolt;
import static gregtech.api.enums.OrePrefixes.circuit;
import static gregtech.api.enums.OrePrefixes.frameGt;
import static gregtech.api.enums.OrePrefixes.gearGt;
import static gregtech.api.enums.OrePrefixes.pipeLarge;
import static gregtech.api.enums.OrePrefixes.plate;
import static gregtech.api.enums.OrePrefixes.plateDouble;
import static gregtech.api.enums.OrePrefixes.ring;
import static gregtech.api.enums.OrePrefixes.rotor;
import static gregtech.api.enums.OrePrefixes.stick;
import static gregtech.api.recipe.RecipeMaps.assemblerRecipes;
import static gregtech.api.util.GTRecipeConstants.AssemblyLine;
import static gregtech.api.util.GTRecipeConstants.RESEARCH_ITEM;
import static gregtech.api.util.GTRecipeConstants.SCANNING;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.ShapelessOreRecipe;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.items.CenterModuleItems;
import com.miaokatze.gtsr.common.machine.orecenter.CenterModule;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTUtility;
import gregtech.api.util.recipe.Scanning;
import gtPlusPlus.xmod.gregtech.api.enums.GregtechItemList;

public final class OreIntegrationCenterRecipes {

    private OreIntegrationCenterRecipes() {}

    public static void register() {
        // Nine physical ingredients; the first processing line remains obtainable with steam infrastructure.
        recipe(
            item(GTSRItemList.OreIntegrationCenter, 1),
            0,
            0,
            item(GregtechItemList.Controller_SteamMaceratorMulti, 1),
            item(GregtechItemList.Controller_SteamWasherMulti, 1),
            item(GregtechItemList.Controller_SteamCentrifugeMulti, 1),
            item(GregtechItemList.Controller_SteamFurnaceMulti, 1),
            item(ItemList.Casing_Gearbox_Bronze, 1),
            item(ItemList.Casing_BronzePlatedBricks, 1),
            singularity(3));

        recipe(
            CenterModuleItems.shell(1),
            0,
            0,
            ore(plateDouble, Bronze, 4),
            ore(frameGt, Bronze, 2),
            item(ItemList.Casing_Gearbox_Bronze, 1),
            singularity(2));
        recipe(
            CenterModuleItems.shell(2),
            480,
            600,
            CenterModuleItems.shell(1),
            item(ItemList.Casing_SolidSteel, 8),
            ore(frameGt, StainlessSteel, 4),
            ore(bolt, Steel, 16),
            ore(circuit, HV, 4),
            singularity(4));
        recipe(
            CenterModuleItems.shell(3),
            7680,
            800,
            CenterModuleItems.shell(2),
            item(ItemList.Casing_RobustTungstenSteel, 8),
            ore(frameGt, TungstenSteel, 4),
            ore(bolt, Titanium, 32),
            ore(circuit, IV, 8),
            singularity(8));

        module(
            CenterModule.CRUSHER,
            1,
            0,
            0,
            item(ItemList.Machine_Bronze_Macerator, 1),
            ore(gearGt, Bronze, 2),
            singularity(3));
        module(
            CenterModule.CRUSHER,
            2,
            480,
            1200,
            item(ItemList.Machine_HV_Macerator, 4),
            item(ItemList.Electric_Piston_HV, 8),
            ore(circuit, HV, 8),
            singularity(4));
        module(
            CenterModule.CRUSHER,
            3,
            7680,
            1600,
            item(GregtechItemList.Industrial_MacerationStack, 2),
            item(GregtechItemList.Casing_MacerationStack, 32),
            item(ItemList.Electric_Piston_IV, 16),
            ore(gearGt, TungstenSteel, 16),
            ore(circuit, IV, 16),
            singularity(8));

        module(
            CenterModule.WASHER,
            1,
            0,
            0,
            item(GregtechItemList.Controller_SteamWasherMulti, 1),
            ore(pipeLarge, Bronze, 2),
            singularity(3));
        module(
            CenterModule.WASHER,
            2,
            480,
            1200,
            item(ItemList.Machine_HV_OreWasher, 4),
            item(ItemList.Electric_Pump_HV, 8),
            ore(circuit, HV, 8),
            singularity(4));
        module(
            CenterModule.WASHER,
            3,
            7680,
            1600,
            item(GregtechItemList.Industrial_WashPlant, 2),
            item(GregtechItemList.Casing_WashPlant, 32),
            item(ItemList.Electric_Pump_IV, 16),
            ore(pipeLarge, Titanium, 16),
            ore(circuit, IV, 16),
            singularity(8));

        // There is no steam sifter: its mechanical screen avoids introducing an electric gate here.
        module(
            CenterModule.SIFTER,
            1,
            0,
            0,
            ore(ring, Bronze, 2),
            ore(frameGt, Bronze, 2),
            ore(plate, Bronze, 1),
            singularity(2));
        module(
            CenterModule.SIFTER,
            2,
            480,
            1200,
            item(ItemList.Machine_HV_Sifter, 4),
            item(ItemList.Electric_Motor_HV, 8),
            ore(circuit, HV, 8),
            singularity(4));
        module(
            CenterModule.SIFTER,
            3,
            7680,
            1600,
            item(GregtechItemList.Industrial_Sifter, 2),
            item(GregtechItemList.Casing_Sifter, 16),
            item(GregtechItemList.Casing_SifterGrate, 16),
            item(ItemList.Electric_Motor_IV, 16),
            ore(circuit, IV, 16),
            singularity(8));

        module(
            CenterModule.CENTRIFUGE,
            1,
            0,
            0,
            item(GregtechItemList.Controller_SteamCentrifugeMulti, 1),
            ore(rotor, Bronze, 2),
            singularity(3));
        module(
            CenterModule.CENTRIFUGE,
            2,
            480,
            1300,
            item(ItemList.Machine_HV_Centrifuge, 4),
            item(ItemList.Electric_Motor_HV, 8),
            ore(circuit, HV, 8),
            singularity(4));
        module(
            CenterModule.CENTRIFUGE,
            3,
            7680,
            1800,
            item(GregtechItemList.Industrial_Centrifuge, 2),
            item(GregtechItemList.Casing_Centrifuge1, 32),
            item(ItemList.Electric_Motor_IV, 16),
            ore(rotor, TungstenSteel, 16),
            ore(circuit, IV, 16),
            singularity(8));

        module(
            CenterModule.ELECTROLYZER,
            1,
            120,
            640,
            item(ItemList.Machine_MV_Electrolyzer, 1),
            ore(circuit, MV, 4),
            ore(plate, Steel, 8),
            singularity(4));
        module(
            CenterModule.ELECTROLYZER,
            2,
            480,
            1400,
            item(ItemList.Machine_HV_Electrolyzer, 4),
            item(ItemList.Electric_Pump_HV, 8),
            ore(circuit, HV, 8),
            singularity(6));
        module(
            CenterModule.ELECTROLYZER,
            3,
            7680,
            2000,
            item(GregtechItemList.Industrial_Electrolyzer, 2),
            item(GregtechItemList.Casing_Electrolyzer, 32),
            item(ItemList.Electric_Pump_IV, 16),
            ore(circuit, IV, 16),
            singularity(12));

        module(
            CenterModule.DRYER,
            1,
            7680,
            1200,
            item(GregtechItemList.GT_Dehydrator_IV, 1),
            item(ItemList.Electric_Pump_IV, 2),
            item(ItemList.Casing_Coil_Nichrome, 2),
            ore(plate, TungstenSteel, 8),
            ore(circuit, EV, 4),
            singularity(6));
        module(
            CenterModule.DRYER,
            2,
            7680,
            3000,
            item(GregtechItemList.GT_Dehydrator_IV, 4),
            item(ItemList.Electric_Pump_IV, 8),
            ore(rotor, TungstenSteel, 8),
            ore(circuit, IV, 8),
            singularity(12));
        module(
            CenterModule.DRYER,
            3,
            30720,
            3800,
            item(GregtechItemList.Controller_Vacuum_Furnace, 2),
            item(GregtechItemList.Casing_Vacuum_Furnace, 32),
            item(ItemList.Electric_Pump_LuV, 16),
            item(ItemList.Casing_Coil_Nichrome, 16),
            ore(circuit, LuV, 16),
            singularity(16));

        module(
            CenterModule.FURNACE,
            1,
            1920,
            950,
            item(ItemList.Machine_Multi_BlastFurnace, 1),
            ore(plate, Titanium, 8),
            item(ItemList.Casing_Coil_Nichrome, 2),
            singularity(6));
        module(
            CenterModule.FURNACE,
            2,
            1920,
            2300,
            item(ItemList.Machine_Multi_BlastFurnace, 4),
            item(ItemList.Casing_Coil_Nichrome, 8),
            item(ItemList.Electric_Piston_EV, 8),
            ore(circuit, EV, 8),
            singularity(8));
        module(
            CenterModule.FURNACE,
            3,
            7680,
            3000,
            item(GregtechItemList.Machine_Adv_BlastFurnace, 2),
            item(GregtechItemList.Casing_Adv_BlastFurnace, 32),
            item(ItemList.Casing_Coil_Nichrome, 16),
            item(ItemList.Electric_Piston_IV, 16),
            ore(circuit, IV, 16),
            singularity(12));

        module(
            CenterModule.REACTOR,
            1,
            1920,
            950,
            item(ItemList.Machine_Multi_LargeChemicalReactor, 1),
            ore(pipeLarge, Polytetrafluoroethylene, 4),
            item(ItemList.Electric_Pump_EV, 2),
            singularity(6));
        module(
            CenterModule.REACTOR,
            2,
            1920,
            2300,
            item(ItemList.Machine_EV_ChemicalReactor, 4),
            ore(pipeLarge, Polytetrafluoroethylene, 8),
            item(ItemList.Electric_Pump_EV, 8),
            ore(circuit, EV, 8),
            singularity(8));
        module(
            CenterModule.REACTOR,
            3,
            7680,
            3000,
            item(ItemList.Machine_Multi_LargeChemicalReactor, 2),
            item(ItemList.Casing_Chemically_Inert, 32),
            ore(pipeLarge, Polytetrafluoroethylene, 16),
            item(ItemList.Electric_Pump_IV, 16),
            ore(circuit, IV, 16),
            singularity(12));

        module(
            CenterModule.SEPARATOR,
            1,
            120,
            640,
            item(ItemList.Machine_MV_ElectromagneticSeparator, 1),
            ore(stick, SteelMagnetic, 8),
            ore(circuit, MV, 4),
            singularity(4));
        module(
            CenterModule.SEPARATOR,
            2,
            480,
            1500,
            item(ItemList.Machine_HV_ElectromagneticSeparator, 4),
            ore(stick, SteelMagnetic, 16),
            item(ItemList.Sensor_HV, 8),
            ore(circuit, HV, 8),
            singularity(6));
        module(
            CenterModule.SEPARATOR,
            3,
            7680,
            2000,
            item(ItemList.Machine_Multi_IndustrialElectromagneticSeparator, 2),
            item(ItemList.Casing_RobustTungstenSteel, 32),
            ore(stick, NeodymiumMagnetic, 16),
            item(ItemList.Sensor_IV, 16),
            ore(circuit, IV, 16),
            singularity(12));

        module(
            CenterModule.EXTRACTOR,
            1,
            30,
            540,
            item(ItemList.Machine_LV_Extractor, 1),
            item(ItemList.Machine_LV_FluidExtractor, 1),
            item(ItemList.Electric_Pump_LV, 2),
            singularity(3));
        module(
            CenterModule.EXTRACTOR,
            2,
            480,
            1300,
            item(ItemList.Machine_HV_Extractor, 4),
            item(ItemList.Machine_HV_FluidExtractor, 4),
            item(ItemList.Electric_Pump_HV, 8),
            ore(circuit, HV, 8),
            singularity(6));
        // KPA accepts both extractor basic machines. An extruder would purchase an unrelated function.
        module(
            CenterModule.EXTRACTOR,
            3,
            7680,
            1800,
            item(GTSRItemList.KineticProcessingArray, 2),
            item(ItemList.Machine_IV_Extractor, 8),
            item(ItemList.Machine_IV_FluidExtractor, 8),
            item(ItemList.Casing_RobustTungstenSteel, 32),
            item(ItemList.Electric_Pump_IV, 16),
            ore(circuit, IV, 16),
            singularity(10));

        module(
            CenterModule.THERMAL_CENTRIFUGE,
            1,
            120,
            720,
            item(ItemList.Machine_MV_ThermalCentrifuge, 1),
            item(ItemList.Casing_Coil_Cupronickel, 2),
            item(ItemList.Casing_HeatProof, 4),
            singularity(4));
        module(
            CenterModule.THERMAL_CENTRIFUGE,
            2,
            480,
            1640,
            item(ItemList.Machine_HV_ThermalCentrifuge, 4),
            item(ItemList.Casing_Coil_Nichrome, 4),
            item(ItemList.Electric_Motor_HV, 8),
            ore(circuit, HV, 8),
            singularity(6));
        module(
            CenterModule.THERMAL_CENTRIFUGE,
            3,
            7680,
            2120,
            item(GregtechItemList.Industrial_ThermalCentrifuge, 2),
            item(GregtechItemList.Casing_ThermalCentrifuge, 32),
            item(ItemList.Casing_Coil_Nichrome, 16),
            item(ItemList.Electric_Motor_IV, 16),
            ore(circuit, IV, 16),
            singularity(12));

        module(
            CenterModule.SMELTING,
            1,
            0,
            0,
            item(ItemList.Machine_Bronze_Furnace, 1),
            item(ItemList.Casing_BronzePlatedBricks, 2),
            ore(plateDouble, Bronze, 1),
            singularity(2));
        module(
            CenterModule.SMELTING,
            2,
            480,
            1080,
            item(ItemList.Machine_HV_E_Furnace, 4),
            item(ItemList.Electric_Piston_HV, 8),
            ore(circuit, HV, 8),
            singularity(4));
        module(
            CenterModule.SMELTING,
            3,
            7680,
            1560,
            item(ItemList.Machine_Multi_Furnace, 2),
            item(ItemList.Casing_HeatProof, 32),
            item(ItemList.Casing_Coil_Nichrome, 16),
            item(ItemList.Electric_Piston_IV, 16),
            ore(circuit, IV, 16),
            singularity(8));

        // Auxiliary packs buy their actual mechanical/control functions, without unrelated controllers.
        module(
            CenterModule.SPEED,
            1,
            0,
            0,
            ore(gearGt, Bronze, 2),
            ore(rotor, Bronze, 2),
            ore(stick, Steel, 1),
            singularity(3));
        module(
            CenterModule.SPEED,
            2,
            480,
            1200,
            ore(rotor, StainlessSteel, 8),
            item(ItemList.Conveyor_Module_HV, 8),
            item(ItemList.Electric_Motor_HV, 8),
            ore(circuit, HV, 8),
            singularity(4));
        module(
            CenterModule.SPEED,
            3,
            7680,
            1700,
            item(ItemList.Casing_Gearbox_TungstenSteel, 32),
            ore(rotor, TungstenSteel, 16),
            item(ItemList.Conveyor_Module_IV, 16),
            item(ItemList.Electric_Motor_IV, 16),
            ore(circuit, IV, 16),
            singularity(8));

        module(CenterModule.PARALLEL, 1, 0, 0, ore(gearGt, Bronze, 2), ore(frameGt, Bronze, 3), singularity(3));
        module(
            CenterModule.PARALLEL,
            2,
            480,
            1200,
            ore(frameGt, StainlessSteel, 8),
            item(ItemList.Robot_Arm_HV, 8),
            item(ItemList.Conveyor_Module_HV, 8),
            ore(circuit, HV, 8),
            singularity(4));
        module(
            CenterModule.PARALLEL,
            3,
            7680,
            1700,
            item(ItemList.Casing_RobustTungstenSteel, 32),
            ore(frameGt, TungstenSteel, 16),
            item(ItemList.Robot_Arm_IV, 16),
            item(ItemList.Conveyor_Module_IV, 16),
            ore(circuit, IV, 16),
            singularity(8));

        module(
            CenterModule.YIELD,
            1,
            0,
            0,
            ore(gearGt, Bronze, 1),
            ore(rotor, Bronze, 1),
            ore(plate, Steel, 1),
            singularity(5));
        module(
            CenterModule.YIELD,
            2,
            480,
            1800,
            ore(rotor, StainlessSteel, 8),
            item(ItemList.Sensor_HV, 8),
            item(ItemList.Robot_Arm_HV, 8),
            ore(circuit, HV, 8),
            singularity(8));
        module(
            CenterModule.YIELD,
            3,
            7680,
            2400,
            item(ItemList.Casing_RobustTungstenSteel, 32),
            ore(rotor, TungstenSteel, 16),
            item(ItemList.Sensor_IV, 16),
            item(ItemList.Robot_Arm_IV, 16),
            ore(circuit, IV, 16),
            singularity(16));

        module(CenterModule.SECONDARY, 1, 0, 0, ore(ring, Bronze, 2), ore(rotor, Bronze, 1), singularity(5));
        module(
            CenterModule.SECONDARY,
            2,
            480,
            1560,
            ore(ring, StainlessSteel, 16),
            item(ItemList.Sensor_HV, 8),
            item(ItemList.Electric_Pump_HV, 8),
            ore(circuit, HV, 8),
            singularity(8));
        module(
            CenterModule.SECONDARY,
            3,
            7680,
            2160,
            item(ItemList.Casing_RobustTungstenSteel, 32),
            ore(ring, TungstenSteel, 32),
            item(ItemList.Sensor_IV, 16),
            item(ItemList.Electric_Pump_IV, 16),
            ore(circuit, IV, 16),
            singularity(16));

        module(
            CenterModule.LOGISTICS,
            1,
            0,
            0,
            item(GregtechItemList.Hatch_Input_Bus_Steam, 1),
            item(GregtechItemList.Hatch_Output_Bus_Steam, 1),
            ore(frameGt, Bronze, 2),
            ore(pipeLarge, Bronze, 2),
            singularity(2));
        module(
            CenterModule.LOGISTICS,
            2,
            480,
            1040,
            item(ItemList.Casing_SolidSteel, 8),
            item(ItemList.Robot_Arm_HV, 8),
            item(ItemList.Conveyor_Module_HV, 8),
            item(ItemList.Electric_Pump_HV, 8),
            ore(circuit, HV, 8),
            singularity(4));
        module(
            CenterModule.LOGISTICS,
            3,
            7680,
            1560,
            item(ItemList.Casing_RobustTungstenSteel, 32),
            item(ItemList.Robot_Arm_IV, 16),
            item(ItemList.Conveyor_Module_IV, 16),
            item(ItemList.Electric_Pump_IV, 16),
            ore(circuit, IV, 16),
            singularity(8));
    }

    private static ItemStack ore(OrePrefixes prefix, Materials material, int amount) {
        return requiredItem(GTOreDictUnificator.get(prefix, material, amount), prefix + material.mName, amount);
    }

    private static ItemStack item(ItemList item, int amount) {
        if (!item.hasBeenSet()) throw new IllegalStateException("Missing ore integration component: " + item);
        return requiredItem(item.get(amount), "ItemList." + item.name(), amount);
    }

    private static ItemStack item(GregtechItemList item, int amount) {
        if (!item.hasBeenSet()) throw new IllegalStateException("Missing ore integration component: " + item);
        return requiredItem(item.get(amount), "GregtechItemList." + item.name(), amount);
    }

    private static ItemStack item(GTSRItemList item, int amount) {
        if (!item.hasBeenSet()) throw new IllegalStateException("Missing ore integration component: " + item);
        return requiredItem(item.get(amount), "GTSRItemList." + item.name(), amount);
    }

    private static ItemStack requiredItem(ItemStack stack, String name, int amount) {
        if (RecipeLoaderUtils.hasNull(stack) || Item.getIdFromItem(stack.getItem()) <= 0
            || amount < 0
            || stack.stackSize != amount) {
            throw new IllegalStateException("Missing or invalid ore integration item: " + name);
        }
        return stack;
    }

    private static void requiredInputs(ItemStack[] inputs) {
        if (RecipeLoaderUtils.hasNull(inputs)) {
            throw new IllegalStateException("Missing ore integration recipe input");
        }
        for (int i = 0; i < inputs.length; i++) {
            // Zero-size GT circuits are intentional non-consumed selectors.
            requiredItem(inputs[i], "recipe input " + i, inputs[i].stackSize);
        }
    }

    private static FluidStack requiredFluid(FluidStack stack, String name, int amount) {
        Fluid fluid;
        try {
            fluid = stack == null ? null : stack.getFluid();
        } catch (NullPointerException invalidDelegate) {
            throw new IllegalStateException("Missing or invalid ore integration fluid: " + name, invalidDelegate);
        }
        if (fluid == null || FluidRegistry.getFluid(fluid.getName()) != fluid
            || amount <= 0
            || stack.amount != amount) {
            throw new IllegalStateException("Missing or invalid ore integration fluid: " + name);
        }
        return stack;
    }

    private static ItemStack singularity(int amount) {
        return item(GTSRItemList.SteamEntangledSingularity, amount);
    }

    private static void module(CenterModule module, int level, int eut, int duration, ItemStack... components) {
        // The selector is a non-consumed GT circuit. Real circuitry is purchased separately above.
        boolean selector = level < 3 && eut != 0;
        int baseInputs = level == 1 ? 1 : 2;
        ItemStack[] inputs = new ItemStack[baseInputs + components.length + (selector ? 1 : 0)];
        inputs[0] = CenterModuleItems.shell(level);
        if (level > 1) inputs[1] = CenterModuleItems.stack(module, level - 1);
        System.arraycopy(components, 0, inputs, baseInputs, components.length);
        if (selector) inputs[inputs.length - 1] = GTUtility.getIntegratedCircuit(selector(module));

        if (level == 3) {
            // Native scanning writes the assembly-line data stick for this exact grade-II research item.
            // The line itself needs LuV infrastructure; ordinary recipes draw IV, the dryer draws LuV.
            int researchEut = module == CenterModule.DRYER ? 30720 : 7680;
            int researchTicks = module == CenterModule.DRYER ? 3600 : 2400;
            requiredInputs(inputs);
            GTValues.RA.stdBuilder()
                .metadata(RESEARCH_ITEM, requiredItem(CenterModuleItems.stack(module, 2), "research " + module, 1))
                .metadata(SCANNING, new Scanning(researchTicks, researchEut))
                .itemInputs(inputs)
                .fluidInputs(
                    requiredFluid(Materials.SolderingAlloy.getMolten(1440), "SolderingAlloy", 1440),
                    requiredFluid(Materials.Lubricant.getFluid(1000), "Lubricant", 1000))
                .itemOutputs(requiredItem(CenterModuleItems.stack(module, level), "module " + module, 1))
                .duration(duration)
                .eut(eut)
                .addTo(AssemblyLine);
            return;
        }
        recipe(CenterModuleItems.stack(module, level), eut, duration, inputs);
    }

    // Stable configurations mirror CenterModuleItems' explicit metadata order, never enum ordinals.
    private static int selector(CenterModule module) {
        return switch (module) {
            case CRUSHER -> 1;
            case WASHER -> 2;
            case SIFTER -> 3;
            case CENTRIFUGE -> 4;
            case ELECTROLYZER -> 5;
            case DRYER -> 6;
            case FURNACE -> 7;
            case REACTOR -> 8;
            case SEPARATOR -> 9;
            case EXTRACTOR -> 10;
            case THERMAL_CENTRIFUGE -> 11;
            case SMELTING -> 12;
            case SPEED -> 13;
            case PARALLEL -> 14;
            case YIELD -> 15;
            case SECONDARY -> 16;
            case LOGISTICS -> 17;
            default -> throw new IllegalArgumentException("Unknown ore integration module: " + module);
        };
    }

    private static void recipe(ItemStack output, int eut, int duration, ItemStack... inputs) {
        requiredInputs(inputs);
        requiredItem(output, "recipe output", 1);
        if (eut != 0) {
            GTValues.RA.stdBuilder()
                .itemInputs(inputs)
                .itemOutputs(output)
                .duration(duration)
                .eut(eut)
                .addTo(assemblerRecipes);
            return;
        }
        List<ItemStack> slots = new ArrayList<>();
        for (ItemStack input : inputs) {
            for (int i = 0; i < input.stackSize; i++) {
                ItemStack member = input.copy();
                member.stackSize = 1;
                slots.add(member);
            }
        }
        if (slots.size() > 9) throw new IllegalArgumentException("Ore integration crafting recipe exceeds nine slots");
        GameRegistry.addRecipe(new ShapelessOreRecipe(output, slots.toArray()));
    }
}
