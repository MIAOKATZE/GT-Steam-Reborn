package com.miaokatze.gtsr.loader.recipes;

import java.util.Arrays;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.critical.recipe.CriticalShellCatalog;

import bartworks.system.material.WerkstoffLoader;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.enums.TierEU;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTUtility;
import gtPlusPlus.core.material.MaterialsAlloy;
import tectech.recipe.TTRecipeAdder;

/** RC2 physical manufacturing: finished casings and researched, complete machine controllers. */
final class CriticalManufacturingRecipes {

    private CriticalManufacturingRecipes() {}

    static void register() {
        for (CriticalShellCatalog.Family family : CriticalShellCatalog.Family.values()) {
            for (int identity = 0; identity < family.identities.size(); identity++) {
                ItemStack[] consumed = CriticalShellCatalog.ingredients(family);
                Object[] inputs = Arrays.copyOf(consumed, consumed.length + 1, Object[].class);
                if (family == CriticalShellCatalog.Family.C) {
                    inputs[1] = circuit(Materials.UV, 8);
                    inputs[2] = circuit(Materials.ZPM, 16);
                }
                inputs[inputs.length - 1] = GTUtility.getIntegratedCircuit(identity + 1);
                GTValues.RA.stdBuilder()
                    .itemInputs(inputs)
                    .itemOutputs(CriticalShellCatalog.shell(family.identities.get(identity), 16))
                    .fluidInputs(Materials.SolderingAlloy.getMolten(family.solder))
                    .duration(family.seconds * 20)
                    .eut(TierEU.RECIPE_UHV)
                    .addTo(RecipeMaps.assemblerRecipes);
            }
        }
        // Equal-tier ore-dictionary circuit alternatives remain accepted by the research assembly line.
        controller(
            GTSRItemList.CriticalLoom,
            ItemList.Robot_Arm_UHV,
            new Object[] { ItemList.Hull_MAX.get(8), shell("reinforced_panel", 64), shell("loom_guide", 64),
                shell("loom_controller", 32), circuit(Materials.UHV, 16), circuit(Materials.UV, 32),
                GTSRItemList.CriticalSteamEntangledSingularity.get(64), ItemList.Robot_Arm_UHV.get(16),
                ItemList.Electric_Motor_UHV.get(32), ItemList.Conveyor_Module_UHV.get(16), ItemList.Sensor_UHV.get(8),
                ItemList.Emitter_UHV.get(8), ItemList.Field_Generator_UHV.get(4),
                ore(OrePrefixes.plate, Materials.CosmicNeutronium, 32),
                ore(OrePrefixes.stickLong, Materials.Neutronium, 64),
                ore(OrePrefixes.cableGt04, Materials.Bedrockium, 16) },
            9216,
            4608,
            Materials.Lubricant.getFluid(16000),
            2304,
            120);
        controller(
            GTSRItemList.CriticalSolar,
            ItemList.Emitter_UHV,
            new Object[] { ItemList.Hull_MAX.get(8), shell("ceramic_armor", 64), shell("blue_field_glass", 64),
                shell("gravity_modulator", 32), circuit(Materials.UHV, 16), circuit(Materials.UV, 32),
                GTSRItemList.CriticalSteamEntangledSingularity.get(64), ItemList.Field_Generator_UHV.get(16),
                ItemList.Emitter_UHV.get(16), ItemList.Sensor_UHV.get(8), ItemList.Electric_Pump_UHV.get(16),
                ore(OrePrefixes.plate, Materials.CosmicNeutronium, 32),
                ore(OrePrefixes.stickLong, Materials.Neutronium, 64),
                ore(OrePrefixes.rotor, Materials.NaquadahAlloy, 32), ore(OrePrefixes.plate, Materials.Europium, 64),
                ore(OrePrefixes.cableGt04, Materials.Bedrockium, 16) },
            9216,
            4608,
            Materials.Lubricant.getFluid(8000),
            4608,
            120);
        controller(
            GTSRItemList.CriticalTurbine,
            ItemList.Electric_Motor_UHV,
            new Object[] { ItemList.Hull_MAX.get(8), shell("reinforced_panel", 64),
                shell("superconducting_contact", 64), shell("coolant_channel", 32), circuit(Materials.UHV, 16),
                circuit(Materials.UV, 32), GTSRItemList.CriticalSteamEntangledSingularity.get(64),
                ItemList.Electric_Motor_UHV.get(32), ItemList.Electric_Piston_UHV.get(16),
                ItemList.Electric_Pump_UHV.get(16), ItemList.Field_Generator_UHV.get(8),
                ore(OrePrefixes.plate, Materials.CosmicNeutronium, 32),
                ore(OrePrefixes.gearGt, Materials.NaquadahAlloy, 64),
                ore(OrePrefixes.stickLong, Materials.Neutronium, 64),
                ore(OrePrefixes.rotor, Materials.NaquadahAlloy, 32),
                ore(OrePrefixes.cableGt04, Materials.Bedrockium, 16) },
            9216,
            4608,
            Materials.Lubricant.getFluid(32000),
            2304,
            120);
        controller(
            GTSRItemList.CriticalBattery,
            ItemList.Electric_Pump_UHV,
            new Object[] { ItemList.Hull_MAX.get(8), shell("ceramic_armor", 64), shell("coolant_channel", 64),
                shell("superconducting_contact", 32), circuit(Materials.UHV, 16), circuit(Materials.UV, 32),
                GTSRItemList.CriticalSteamEntangledSingularity.get(64), ItemList.Electric_Pump_UHV.get(32),
                ItemList.Electric_Motor_UHV.get(16), ItemList.Emitter_UHV.get(16), ItemList.Sensor_UHV.get(16),
                ore(OrePrefixes.plate, Materials.CosmicNeutronium, 32),
                ore(OrePrefixes.plate, Materials.NaquadahAlloy, 64),
                ore(OrePrefixes.pipeMedium, Materials.Neutronium, 64), ore(OrePrefixes.foil, Materials.Platinum, 64),
                ore(OrePrefixes.cableGt04, Materials.Bedrockium, 16) },
            9216,
            4608,
            Materials.Lubricant.getFluid(8000),
            9216,
            120);
        controller(
            GTSRItemList.CriticalSun,
            ItemList.Field_Generator_UHV,
            new Object[] { ItemList.Hull_MAX.get(16), shell("gravity_modulator", 64), shell("blue_field_glass", 64),
                shell("superconducting_contact", 64), circuit(Materials.UHV, 32), circuit(Materials.UV, 64),
                GTSRItemList.CriticalSteamEntangledSingularity.get(64), ItemList.Field_Generator_UHV.get(32),
                ItemList.Emitter_UHV.get(32), ItemList.Sensor_UHV.get(16), ItemList.Electric_Pump_UHV.get(16),
                ore(OrePrefixes.plate, Materials.CosmicNeutronium, 64),
                ore(OrePrefixes.stickLong, Materials.Neutronium, 64), ItemList.Gravistar.get(32),
                ore(OrePrefixes.plate, Materials.Europium, 64), ore(OrePrefixes.cableGt04, Materials.Bedrockium, 32) },
            18432,
            9216,
            WerkstoffLoader.LiquidHelium.getFluidOrGas(32000),
            4608,
            240);
    }

    private static void controller(GTSRItemList output, ItemList research, Object[] items, int solder, int naquadria,
        FluidStack third, int pbi, int seconds) {
        boolean added = TTRecipeAdder.addResearchableAssemblylineRecipe(
            research.get(1),
            48000,
            64,
            (int) TierEU.RECIPE_UHV,
            1,
            items,
            new FluidStack[] { MaterialsAlloy.INDALLOY_140.getFluidStack(solder),
                Materials.Naquadria.getMolten(naquadria), third, Materials.Polybenzimidazole.getMolten(pbi) },
            output.get(1),
            seconds * 20,
            (int) TierEU.RECIPE_UHV);
        if (!added) throw new IllegalStateException("Invalid critical research assembly line: " + output);
    }

    private static Object[] circuit(Materials tier, int count) {
        return new Object[] { OrePrefixes.circuit.get(tier), count };
    }

    private static ItemStack shell(String id, int count) {
        return CriticalShellCatalog.shell("v2_" + id, count);
    }

    private static ItemStack ore(OrePrefixes prefix, Materials material, int count) {
        ItemStack stack = GTOreDictUnificator.get(prefix, material, count);
        if (stack == null)
            throw new IllegalStateException("Missing critical manufacturing material: " + prefix + material);
        return stack;
    }
}
