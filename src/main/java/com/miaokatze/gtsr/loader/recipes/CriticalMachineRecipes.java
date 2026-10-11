package com.miaokatze.gtsr.loader.recipes;

import com.miaokatze.gtsr.api.recipe.CriticalRecipeMaps;
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.critical.CriticalConfiguration;
import com.miaokatze.gtsr.common.critical.CriticalGeometry;
import com.miaokatze.gtsr.common.critical.CriticalLoomStructure;
import com.miaokatze.gtsr.common.critical.CriticalMachineKind;
import com.miaokatze.gtsr.common.critical.CriticalTier;
import com.miaokatze.gtsr.common.critical.recipe.CriticalRecipes;
import com.miaokatze.gtsr.common.critical.recipe.RecipeMatch;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMapBackend;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTUtility;

/** Controller crafting and NEI examples share the real runtime batch definitions. */
public final class CriticalMachineRecipes {

    private static boolean registered;
    private static final GTSRItemList[] CONTROLLERS = { GTSRItemList.CriticalSolar, GTSRItemList.CriticalTurbine,
        GTSRItemList.CriticalProcessing, GTSRItemList.CriticalEntangler, GTSRItemList.CriticalSun,
        GTSRItemList.CriticalDimension, GTSRItemList.CriticalBattery, GTSRItemList.CriticalAssembly,
        GTSRItemList.CriticalAccelerator };

    private CriticalMachineRecipes() {}

    public static void register() {
        if (registered) return;
        registerLoomMaterials();
        if (!GTModHandler.addCraftingRecipe(
            GTSRItemList.CriticalLoom.get(1),
            new Object[] { "RFR", "SCE", "RMR", 'R', ItemList.Robot_Arm_ZPM.get(1), 'F',
                ItemList.Field_Generator_ZPM.get(1), 'S', ItemList.Sensor_ZPM.get(1), 'C', ItemList.Casing_ZPM.get(1),
                'E', ItemList.Emitter_ZPM.get(1), 'M', OrePrefixes.circuit.get(Materials.ZPM) })) {
            throw new IllegalStateException("Failed to register critical loom crafting recipe");
        }
        GTValues.RA.stdBuilder()
            .itemInputs(
                ItemList.Casing_ZPM.get(1),
                ItemList.Robot_Arm_ZPM.get(4),
                ItemList.Field_Generator_ZPM.get(2),
                ItemList.Sensor_ZPM.get(1),
                ItemList.Emitter_ZPM.get(1))
            .fluidInputs(Materials.SolderingAlloy.getMolten(1152))
            .itemOutputs(GTSRItemList.CriticalLoom.get(1))
            .duration(1200)
            .eut(131072)
            .addTo(RecipeMaps.assemblerRecipes);
        for (CriticalMachineKind kind : CriticalMachineKind.values()) {
            GTValues.RA.stdBuilder()
                .itemInputs(
                    GTSRItemList.CriticalLoom.get(1),
                    GTUtility.getIntegratedCircuit(kind.ordinal() + 1),
                    ItemList.Field_Generator_ZPM.get(1),
                    ItemList.Sensor_ZPM.get(1))
                .fluidInputs(Materials.SolderingAlloy.getMolten(288))
                .itemOutputs(CONTROLLERS[kind.ordinal()].get(1))
                .duration(400)
                .eut(131072)
                .addTo(RecipeMaps.assemblerRecipes);
            for (CriticalTier tier : CriticalTier.values()) {
                CriticalConfiguration cfg = new CriticalConfiguration(tier, kind, 0, 0, 0);
                int index = 0;
                for (RecipeMatch batch : CriticalRecipes.list(cfg)) {
                    int code = CriticalRecipeMaps.displayCode(cfg, index++);
                    addBatch(batch, code, CriticalRecipeMaps.forKind(kind));
                    addBatch(batch, code, CriticalRecipeMaps.displayRecipes);
                }
            }
        }
        registered = true;
    }

    private static void registerLoomMaterials() {
        java.util.Set<net.minecraft.block.Block> shells = new java.util.LinkedHashSet<>();
        int circuit = 1;
        for (CriticalGeometry.Voxel voxel : CriticalLoomStructure.voxels()) {
            net.minecraft.block.Block shell = CriticalLoomStructure.shell(voxel);
            if (!shells.add(shell)) continue;
            GTValues.RA.stdBuilder()
                .itemInputs(
                    ItemList.Casing_ZPM.get(1),
                    gregtech.api.util.GTOreDictUnificator.get(OrePrefixes.plate, Materials.TungstenSteel, 4),
                    GTUtility.getIntegratedCircuit(circuit++))
                .fluidInputs(Materials.SolderingAlloy.getMolten(144))
                .itemOutputs(new net.minecraft.item.ItemStack(shell, 16))
                .duration(100)
                .eut(131072)
                .addTo(RecipeMaps.assemblerRecipes);
        }
    }

    private static void addBatch(RecipeMatch batch, int code, RecipeMap<RecipeMapBackend> map) {
        GTValues.RA.stdBuilder()
            .itemInputs(batch.itemInputs)
            .itemOutputs(batch.itemOutputs)
            .fluidInputs(batch.fluidInputs)
            .fluidOutputs(batch.fluidOutputs)
            .duration(batch.durationTicks)
            .eut(batch.euPerTick)
            .specialValue(code)
            .ignoreCollision()
            .addTo(map);
    }
}
