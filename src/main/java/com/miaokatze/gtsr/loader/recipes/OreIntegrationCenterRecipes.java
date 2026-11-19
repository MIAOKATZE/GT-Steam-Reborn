// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.loader.recipes;

import static gregtech.api.enums.ItemList.Casing_BronzePlatedBricks;
import static gregtech.api.enums.ItemList.Casing_Coil_Cupronickel;
import static gregtech.api.enums.ItemList.Casing_Coil_Nichrome;
import static gregtech.api.enums.ItemList.Casing_Gearbox_Bronze;
import static gregtech.api.enums.ItemList.Casing_HeatProof;
import static gregtech.api.enums.ItemList.Conveyor_Module_HV;
import static gregtech.api.enums.ItemList.Conveyor_Module_IV;
import static gregtech.api.enums.ItemList.Electric_Motor_HV;
import static gregtech.api.enums.ItemList.Electric_Motor_IV;
import static gregtech.api.enums.ItemList.Electric_Piston_EV;
import static gregtech.api.enums.ItemList.Electric_Piston_HV;
import static gregtech.api.enums.ItemList.Electric_Piston_IV;
import static gregtech.api.enums.ItemList.Electric_Pump_EV;
import static gregtech.api.enums.ItemList.Electric_Pump_HV;
import static gregtech.api.enums.ItemList.Electric_Pump_IV;
import static gregtech.api.enums.ItemList.Electric_Pump_LV;
import static gregtech.api.enums.ItemList.Electric_Pump_LuV;
import static gregtech.api.enums.ItemList.Machine_LV_Extractor;
import static gregtech.api.enums.ItemList.Machine_LV_FluidExtractor;
import static gregtech.api.enums.ItemList.Machine_MV_Electrolyzer;
import static gregtech.api.enums.ItemList.Machine_MV_Polarizer;
import static gregtech.api.enums.ItemList.Machine_MV_ThermalCentrifuge;
import static gregtech.api.enums.ItemList.Machine_Multi_BlastFurnace;
import static gregtech.api.enums.ItemList.Machine_Multi_LargeChemicalReactor;
import static gregtech.api.enums.ItemList.Robot_Arm_HV;
import static gregtech.api.enums.ItemList.Robot_Arm_IV;
import static gregtech.api.enums.ItemList.Sensor_HV;
import static gregtech.api.enums.ItemList.Sensor_IV;
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
import static gregtech.api.enums.OrePrefixes.stickLong;
import static gregtech.api.recipe.RecipeMaps.assemblerRecipes;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
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

public final class OreIntegrationCenterRecipes {

    private OreIntegrationCenterRecipes() {}

    public static void register() {
        recipe(
            GTSRItemList.OreIntegrationCenter.get(1),
            0,
            0,
            item(Casing_BronzePlatedBricks, 3),
            item(Casing_Gearbox_Bronze, 1),
            ore(plate, Steel, 1),
            ore(pipeLarge, Bronze, 2),
            singularity(2));

        recipe(CenterModuleItems.shell(1), 0, 0, ore(plate, Bronze, 2), ore(frameGt, Bronze, 1), ore(plate, Steel, 1));
        recipe(
            CenterModuleItems.shell(2),
            480,
            300,
            ore(plate, StainlessSteel, 8),
            ore(frameGt, StainlessSteel, 2),
            ore(bolt, Steel, 8));
        recipe(
            CenterModuleItems.shell(3),
            7680,
            400,
            ore(plate, TungstenSteel, 8),
            ore(frameGt, TungstenSteel, 2),
            ore(bolt, Titanium, 8));

        module(
            CenterModule.CRUSHER,
            1,
            0,
            0,
            ore(gearGt, Bronze, 2),
            ore(plateDouble, Bronze, 1),
            ore(stick, Steel, 1),
            singularity(3));
        module(
            CenterModule.CRUSHER,
            2,
            480,
            600,
            ore(gearGt, StainlessSteel, 4),
            ore(stickLong, Steel, 4),
            item(Electric_Piston_HV, 2),
            singularity(2));
        module(
            CenterModule.CRUSHER,
            3,
            7680,
            800,
            ore(gearGt, TungstenSteel, 4),
            ore(stickLong, TungstenSteel, 4),
            item(Electric_Piston_IV, 2),
            singularity(4));

        module(CenterModule.WASHER, 1, 0, 0, ore(pipeLarge, Bronze, 2), ore(rotor, Bronze, 2), singularity(3));
        module(
            CenterModule.WASHER,
            2,
            480,
            600,
            ore(pipeLarge, StainlessSteel, 4),
            ore(rotor, StainlessSteel, 4),
            item(Electric_Pump_HV, 2),
            singularity(2));
        module(
            CenterModule.WASHER,
            3,
            7680,
            800,
            ore(pipeLarge, Titanium, 4),
            ore(rotor, TungstenSteel, 4),
            item(Electric_Pump_IV, 2),
            singularity(4));

        module(
            CenterModule.SIFTER,
            1,
            0,
            0,
            ore(ring, Bronze, 2),
            ore(frameGt, Bronze, 1),
            ore(plate, Bronze, 2),
            singularity(2));
        module(
            CenterModule.SIFTER,
            2,
            480,
            600,
            ore(ring, StainlessSteel, 8),
            ore(plate, StainlessSteel, 8),
            item(Electric_Motor_HV, 2),
            singularity(2));
        module(
            CenterModule.SIFTER,
            3,
            7680,
            800,
            ore(ring, TungstenSteel, 8),
            ore(plate, TungstenSteel, 8),
            item(Electric_Motor_IV, 2),
            singularity(4));

        module(
            CenterModule.CENTRIFUGE,
            1,
            0,
            0,
            ore(rotor, Bronze, 2),
            ore(gearGt, Bronze, 1),
            ore(pipeLarge, Bronze, 1),
            singularity(3));
        module(
            CenterModule.CENTRIFUGE,
            2,
            480,
            650,
            ore(rotor, StainlessSteel, 4),
            ore(gearGt, StainlessSteel, 4),
            item(Electric_Motor_HV, 2),
            singularity(2));
        module(
            CenterModule.CENTRIFUGE,
            3,
            7680,
            900,
            ore(rotor, TungstenSteel, 4),
            ore(gearGt, TungstenSteel, 4),
            item(Electric_Motor_IV, 2),
            singularity(4));

        module(
            CenterModule.ELECTROLYZER,
            1,
            120,
            640,
            item(Machine_MV_Electrolyzer, 1),
            ore(circuit, MV, 4),
            ore(plate, Steel, 8),
            singularity(4));
        module(
            CenterModule.ELECTROLYZER,
            2,
            480,
            700,
            ore(stickLong, StainlessSteel, 4),
            ore(circuit, HV, 4),
            item(Electric_Pump_HV, 2),
            singularity(3));
        module(
            CenterModule.ELECTROLYZER,
            3,
            7680,
            1000,
            ore(stickLong, TungstenSteel, 4),
            ore(circuit, IV, 4),
            item(Electric_Pump_IV, 2),
            singularity(6));

        module(
            CenterModule.DRYER,
            1,
            7680,
            1200,
            item(Electric_Pump_IV, 2),
            item(Casing_Coil_Nichrome, 2),
            ore(plate, TungstenSteel, 8),
            ore(circuit, EV, 4),
            singularity(6));
        module(
            CenterModule.DRYER,
            2,
            7680,
            1500,
            item(Electric_Pump_IV, 4),
            ore(rotor, TungstenSteel, 4),
            ore(circuit, IV, 4),
            singularity(6));
        module(
            CenterModule.DRYER,
            3,
            30720,
            1900,
            item(Electric_Pump_LuV, 2),
            ore(gearGt, TungstenSteel, 8),
            ore(circuit, LuV, 4),
            singularity(8));

        module(
            CenterModule.FURNACE,
            1,
            1920,
            950,
            item(Machine_Multi_BlastFurnace, 1),
            ore(plate, Titanium, 8),
            item(Casing_Coil_Nichrome, 2),
            singularity(6));
        module(
            CenterModule.FURNACE,
            2,
            1920,
            1150,
            item(Casing_Coil_Nichrome, 4),
            item(Electric_Piston_EV, 2),
            ore(circuit, EV, 4),
            singularity(4));
        module(
            CenterModule.FURNACE,
            3,
            7680,
            1500,
            ore(plate, TungstenSteel, 12),
            item(Casing_Coil_Nichrome, 8),
            item(Electric_Piston_IV, 2),
            singularity(6));

        module(
            CenterModule.REACTOR,
            1,
            1920,
            950,
            item(Machine_Multi_LargeChemicalReactor, 1),
            ore(pipeLarge, Polytetrafluoroethylene, 4),
            item(Electric_Pump_EV, 2),
            singularity(6));
        module(
            CenterModule.REACTOR,
            2,
            1920,
            1150,
            ore(pipeLarge, Polytetrafluoroethylene, 8),
            ore(rotor, Titanium, 4),
            item(Electric_Pump_EV, 4),
            singularity(4));
        module(
            CenterModule.REACTOR,
            3,
            7680,
            1500,
            ore(pipeLarge, Polytetrafluoroethylene, 12),
            ore(rotor, TungstenSteel, 4),
            item(Electric_Pump_IV, 4),
            singularity(6));

        module(
            CenterModule.SEPARATOR,
            1,
            120,
            640,
            item(Machine_MV_Polarizer, 1),
            ore(stick, SteelMagnetic, 8),
            ore(circuit, MV, 4),
            singularity(4));
        module(
            CenterModule.SEPARATOR,
            2,
            480,
            750,
            ore(stick, SteelMagnetic, 16),
            ore(plate, StainlessSteel, 8),
            item(Sensor_HV, 2),
            singularity(3));
        module(
            CenterModule.SEPARATOR,
            3,
            7680,
            1000,
            ore(stick, NeodymiumMagnetic, 8),
            ore(plate, TungstenSteel, 8),
            item(Sensor_IV, 2),
            singularity(6));

        module(
            CenterModule.EXTRACTOR,
            1,
            30,
            540,
            item(Machine_LV_Extractor, 1),
            item(Machine_LV_FluidExtractor, 1),
            item(Electric_Pump_LV, 2),
            singularity(3));
        module(
            CenterModule.EXTRACTOR,
            2,
            480,
            650,
            ore(pipeLarge, StainlessSteel, 4),
            item(Electric_Piston_HV, 2),
            item(Electric_Pump_HV, 2),
            singularity(3));
        module(
            CenterModule.EXTRACTOR,
            3,
            7680,
            900,
            ore(pipeLarge, Titanium, 4),
            item(Electric_Piston_IV, 2),
            item(Electric_Pump_IV, 2),
            singularity(5));

        module(
            CenterModule.THERMAL_CENTRIFUGE,
            1,
            120,
            720,
            item(Machine_MV_ThermalCentrifuge, 1),
            item(Casing_Coil_Cupronickel, 2),
            item(Casing_HeatProof, 4),
            singularity(4));
        module(
            CenterModule.THERMAL_CENTRIFUGE,
            2,
            480,
            820,
            item(Casing_Coil_Nichrome, 2),
            ore(rotor, StainlessSteel, 4),
            item(Electric_Motor_HV, 2),
            singularity(3));
        module(
            CenterModule.THERMAL_CENTRIFUGE,
            3,
            7680,
            1060,
            item(Casing_Coil_Nichrome, 4),
            ore(rotor, TungstenSteel, 4),
            item(Electric_Motor_IV, 2),
            singularity(6));

        module(
            CenterModule.SMELTING,
            1,
            0,
            0,
            item(Casing_BronzePlatedBricks, 2),
            ore(plateDouble, Bronze, 1),
            ore(pipeLarge, Bronze, 1),
            singularity(2));
        module(
            CenterModule.SMELTING,
            2,
            480,
            540,
            item(Casing_BronzePlatedBricks, 8),
            ore(plate, StainlessSteel, 8),
            item(Electric_Piston_HV, 2),
            singularity(2));
        module(
            CenterModule.SMELTING,
            3,
            7680,
            780,
            item(Casing_HeatProof, 8),
            ore(plate, TungstenSteel, 8),
            item(Electric_Piston_IV, 2),
            singularity(4));

        module(
            CenterModule.SPEED,
            1,
            0,
            0,
            ore(gearGt, Bronze, 2),
            ore(rotor, Bronze, 1),
            ore(stick, Steel, 1),
            singularity(2));
        module(
            CenterModule.SPEED,
            2,
            480,
            600,
            ore(rotor, StainlessSteel, 4),
            item(Conveyor_Module_HV, 2),
            item(Electric_Motor_HV, 2),
            singularity(2));
        module(
            CenterModule.SPEED,
            3,
            7680,
            850,
            ore(rotor, TungstenSteel, 4),
            item(Conveyor_Module_IV, 2),
            item(Electric_Motor_IV, 2),
            singularity(4));

        module(CenterModule.PARALLEL, 1, 0, 0, ore(gearGt, Bronze, 2), ore(frameGt, Bronze, 2), singularity(2));
        module(
            CenterModule.PARALLEL,
            2,
            480,
            600,
            ore(frameGt, StainlessSteel, 4),
            item(Robot_Arm_HV, 2),
            item(Conveyor_Module_HV, 2),
            singularity(2));
        module(
            CenterModule.PARALLEL,
            3,
            7680,
            850,
            ore(frameGt, TungstenSteel, 4),
            item(Robot_Arm_IV, 2),
            item(Conveyor_Module_IV, 2),
            singularity(4));

        module(
            CenterModule.YIELD,
            1,
            0,
            0,
            ore(gearGt, Bronze, 1),
            ore(rotor, Bronze, 1),
            ore(plate, Steel, 1),
            singularity(4));
        module(
            CenterModule.YIELD,
            2,
            480,
            900,
            ore(rotor, StainlessSteel, 4),
            item(Sensor_HV, 2),
            ore(circuit, HV, 4),
            singularity(4));
        module(
            CenterModule.YIELD,
            3,
            7680,
            1200,
            ore(rotor, TungstenSteel, 4),
            item(Sensor_IV, 2),
            ore(circuit, IV, 4),
            singularity(8));

        module(CenterModule.SECONDARY, 1, 0, 0, ore(ring, Bronze, 2), ore(rotor, Bronze, 1), singularity(4));
        module(
            CenterModule.SECONDARY,
            2,
            480,
            780,
            ore(ring, StainlessSteel, 8),
            item(Sensor_HV, 2),
            ore(circuit, HV, 4),
            singularity(4));
        module(
            CenterModule.SECONDARY,
            3,
            7680,
            1080,
            ore(ring, TungstenSteel, 8),
            item(Sensor_IV, 2),
            ore(circuit, IV, 4),
            singularity(8));

        module(CenterModule.LOGISTICS, 1, 0, 0, ore(frameGt, Bronze, 2), ore(pipeLarge, Bronze, 2), singularity(2));
        module(
            CenterModule.LOGISTICS,
            2,
            480,
            520,
            ore(frameGt, StainlessSteel, 4),
            item(Robot_Arm_HV, 2),
            item(Conveyor_Module_HV, 2),
            singularity(2));
        module(
            CenterModule.LOGISTICS,
            3,
            7680,
            780,
            ore(frameGt, TungstenSteel, 4),
            item(Robot_Arm_IV, 2),
            item(Conveyor_Module_IV, 2),
            singularity(4));
    }

    private static ItemStack ore(OrePrefixes prefix, Materials material, int amount) {
        ItemStack result = GTOreDictUnificator.get(prefix, material, amount);
        if (result == null) {
            throw new IllegalStateException("Missing ore integration recipe input: " + prefix + material.mName);
        }
        return result;
    }

    private static ItemStack item(ItemList item, int amount) {
        if (!item.hasBeenSet()) throw new IllegalStateException("Missing ore integration component: " + item);
        return item.get(amount);
    }

    private static ItemStack singularity(int amount) {
        return GTSRItemList.SteamEntangledSingularity.get(amount);
    }

    private static void module(CenterModule module, int level, int eut, int duration, ItemStack... components) {
        ItemStack[] inputs = new ItemStack[components.length + (level == 1 ? 1 : 2)];
        inputs[0] = CenterModuleItems.shell(level);
        if (level > 1) inputs[1] = CenterModuleItems.stack(module, level - 1);
        System.arraycopy(components, 0, inputs, inputs.length - components.length, components.length);
        recipe(CenterModuleItems.stack(module, level), eut, duration, inputs);
    }

    private static void recipe(ItemStack output, int eut, int duration, ItemStack... inputs) {
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
