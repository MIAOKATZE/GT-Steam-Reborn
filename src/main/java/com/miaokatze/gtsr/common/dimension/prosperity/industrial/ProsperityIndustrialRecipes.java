package com.miaokatze.gtsr.common.dimension.prosperity.industrial;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.common.dimension.prosperity.air.GTSRProsperityAirMaterials;
import com.miaokatze.gtsr.main.GTSteamReborn;

import bartworks.system.material.WerkstoffLoader;
import goodgenerator.items.GGMaterial;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.recipe.RecipeMetadataKey;
import gregtech.api.recipe.metadata.SimpleRecipeMetadataKey;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipeBuilder;
import gregtech.api.util.GTUtility;
import gtnhlanth.common.register.WerkstoffMaterialPool;

/** Six industrial lines, using registered material forms and the current integer ledger. */
public final class ProsperityIndustrialRecipes {

    public static final RecipeMetadataKey<String> INDUSTRIAL_STAGE = SimpleRecipeMetadataKey
        .create(String.class, "gtsr_industrial_r6_stage");
    private static int registeredRecipeCount;

    private ProsperityIndustrialRecipes() {}

    public static void registerAll() {
        if (registeredRecipeCount != 0) throw new IllegalStateException("Prosperity R6 recipes already registered");
        ProsperityIndustrialMaterials.verifyRegistration();
        List<ResolvedStage> resolved = new ArrayList<>();
        Set<String> stageIds = new HashSet<>();
        // Resolve the entire ledger before mutating any map. Missing ingredients fail with the stage and ID.
        for (IndustrialRecipeLedger.Stage stage : IndustrialRecipeLedger.get().recipes) {
            if (!stageIds.add(stage.id)) throw new IllegalStateException("Duplicate industrial stage: " + stage.id);
            ResolvedStage tower = resolve(stage);
            resolved.add(tower);
            if (hasDistilleryAlternative(tower)) resolved.add(distilleryAlternative(tower));
        }
        for (ResolvedStage stage : resolved) {
            GTRecipeBuilder builder = GTValues.RA.stdBuilder()
                .itemInputs(stage.itemsIn)
                .itemOutputs(stage.itemsOut)
                .fluidInputs(stage.fluidsIn)
                .fluidOutputs(stage.fluidsOut)
                .duration(stage.definition.ticks)
                .eut(stage.definition.EUt)
                .metadata(INDUSTRIAL_STAGE, stage.definition.id);
            Collection<GTRecipe> added = builder.addTo(stage.map);
            if (added.size() != 1) {
                throw new IllegalStateException(
                    "Industrial stage " + stage.definition.id
                        + " rejected by "
                        + stage.definition.machineMap
                        + ": registered="
                        + added.size());
            }
            registeredRecipeCount++;
        }
        verifyRegistration();
    }

    public static int getRegisteredRecipeCount() {
        return registeredRecipeCount;
    }

    /** Inspect actual recipe maps after registration, including exact consumed quantities and zero-size tools. */
    public static void verifyRegistration() {
        ProsperityIndustrialMaterials.verifyRegistration();
        int total = 0;
        List<ResolvedStage> expectedStages = new ArrayList<>();
        for (IndustrialRecipeLedger.Stage definition : IndustrialRecipeLedger.get().recipes) {
            ResolvedStage stage = resolve(definition);
            expectedStages.add(stage);
            if (hasDistilleryAlternative(stage)) expectedStages.add(distilleryAlternative(stage));
        }
        for (ResolvedStage expected : expectedStages) {
            IndustrialRecipeLedger.Stage definition = expected.definition;
            int matches = 0;
            for (GTRecipe recipe : expected.map.getAllRecipes()) {
                if (!definition.id.equals(recipe.getMetadataOrDefault(INDUSTRIAL_STAGE, ""))) continue;
                matches++;
                if (recipe.mEUt != definition.EUt || recipe.mDuration != definition.ticks
                    || !sameItems(recipe.mInputs, expected.itemsIn)
                    || !sameItems(recipe.mOutputs, expected.itemsOut)
                    || !sameFluids(recipe.mFluidInputs, expected.fluidsIn)
                    || !sameFluids(recipe.mFluidOutputs, expected.fluidsOut)) {
                    throw new IllegalStateException(
                        "Industrial live recipe differs from R6 integer batch: " + definition.id);
                }
            }
            if (matches != 1)
                throw new IllegalStateException("Industrial stage " + definition.id + " live matches=" + matches);
            total += matches;
            GTSteamReborn.LOG.info(
                "[GTSR] industrial recipe probe: " + definition.id
                    + " map="
                    + definition.machineMap
                    + " EUt="
                    + definition.EUt
                    + " ticks="
                    + definition.ticks
                    + " ok");
        }
        if (total != expectedStages.size() || registeredRecipeCount != total) {
            throw new IllegalStateException(
                "Prosperity industrial recipe count mismatch: live=" + total + " registered=" + registeredRecipeCount);
        }
        GTSteamReborn.LOG.info("[GTSR] prosperity industrial recipe live audit: lines=6 stages=" + total);
    }

    private static boolean hasDistilleryAlternative(ResolvedStage stage) {
        return stage.map == RecipeMaps.distillationTowerRecipes && stage.fluidsOut.length == 1;
    }

    /** GT5U205 DistilleryRecipes uses circuit 1, 2x duration, 1/4 EU/t and exact integer batch divisors. */
    private static ResolvedStage distilleryAlternative(ResolvedStage tower) {
        if (tower.fluidsIn.length != 1 || tower.itemsIn.length != 0 || tower.itemsOut.length != 0)
            throw new IllegalStateException("Unsupported single-output tower batch: " + tower.definition.id);
        int ratio = 1;
        for (int divisor : new int[] { 2, 5, 10, 25, 50 }) {
            if (tower.fluidsIn[0].amount % divisor == 0 && tower.fluidsIn[0].amount / divisor >= 25
                && tower.fluidsOut[0].amount % divisor == 0
                && tower.fluidsOut[0].amount / divisor >= 25) {
                ratio = divisor;
            }
        }
        IndustrialRecipeLedger.Stage alternative = new IndustrialRecipeLedger.Stage();
        alternative.id = tower.definition.id + "-DISTILLERY";
        alternative.lineId = tower.definition.lineId;
        alternative.machineMap = "distilleryRecipes";
        alternative.EUt = tower.definition.EUt / 4;
        alternative.ticks = Math.max(1, 2 * tower.definition.ticks / ratio);
        IndustrialRecipeLedger.Amount circuit = new IndustrialRecipeLedger.Amount();
        circuit.id = "standard:ItemList.Circuit_Integrated";
        circuit.kind = "item";
        circuit.amount = 1;
        circuit.meta = 1;
        circuit.consumed = false;
        alternative.inputs = Arrays.asList(circuit, dividedFluid(tower.definition.inputs.get(0), ratio));
        alternative.outputs = Arrays.asList(dividedFluid(tower.definition.outputs.get(0), ratio));
        return resolve(alternative);
    }

    private static IndustrialRecipeLedger.Amount dividedFluid(IndustrialRecipeLedger.Amount amount, int ratio) {
        IndustrialRecipeLedger.Amount divided = new IndustrialRecipeLedger.Amount();
        divided.id = amount.id;
        divided.kind = amount.kind;
        divided.unit = amount.unit;
        divided.amount = amount.amount / ratio;
        return divided;
    }

    private static ResolvedStage resolve(IndustrialRecipeLedger.Stage definition) {
        try {
            ResolvedStage result = new ResolvedStage();
            result.definition = definition;
            result.map = map(definition.machineMap);
            result.itemsIn = items(definition.inputs);
            result.itemsOut = items(definition.outputs);
            result.fluidsIn = fluids(definition.inputs);
            result.fluidsOut = fluids(definition.outputs);
            return result;
        } catch (RuntimeException e) {
            throw new IllegalStateException("Cannot resolve industrial stage " + definition.id, e);
        }
    }

    private static ItemStack[] items(List<IndustrialRecipeLedger.Amount> amounts) {
        List<ItemStack> result = new ArrayList<>();
        for (IndustrialRecipeLedger.Amount amount : amounts) {
            if (!"item".equals(amount.kind)) continue;
            if (amount.amount <= 0) throw new IllegalStateException("Non-positive item batch: " + amount.id);
            ItemStack stack;
            if ("standard:ItemList.Circuit_Integrated".equals(amount.id)) {
                stack = GTUtility.getIntegratedCircuit(amount.meta);
            } else if (amount.id.startsWith("planned:")) {
                stack = GTOreDictUnificator
                    .get(OrePrefixes.dust, ProsperityIndustrialMaterials.get(amount.id), amount.amount);
            } else if (amount.id.startsWith("standard:Materials.")) {
                stack = GTOreDictUnificator.get(OrePrefixes.dust, standardMaterial(amount.id), amount.amount);
            } else {
                switch (amount.id) {
                    case "standard:WerkstoffLoader.IrLeachResidue":
                        stack = WerkstoffLoader.IrLeachResidue.get(OrePrefixes.dust, amount.amount);
                        break;
                    case "standard:WerkstoffLoader.IrOsLeachResidue":
                        stack = WerkstoffLoader.IrOsLeachResidue.get(OrePrefixes.dust, amount.amount);
                        break;
                    case "standard:WerkstoffLoader.PDMetallicPowder":
                        stack = WerkstoffLoader.PDMetallicPowder.get(OrePrefixes.dust, amount.amount);
                        break;
                    case "standard:WerkstoffLoader.PTMetallicPowder":
                        stack = WerkstoffLoader.PTMetallicPowder.get(OrePrefixes.dust, amount.amount);
                        break;
                    case "standard:WerkstoffMaterialPool.SamariumOreConcentrate":
                        stack = WerkstoffMaterialPool.SamariumOreConcentrate.get(OrePrefixes.dust, amount.amount);
                        break;
                    default:
                        throw new IllegalStateException("Unknown real item getter: " + amount.id);
                }
            }
            if (stack == null || stack.getItem() == null)
                throw new IllegalStateException("Missing item form: " + amount.id);
            stack = stack.copy();
            stack.stackSize = Boolean.FALSE.equals(amount.consumed) ? 0 : amount.amount;
            result.add(stack);
        }
        return result.toArray(new ItemStack[0]);
    }

    private static FluidStack[] fluids(List<IndustrialRecipeLedger.Amount> amounts) {
        List<FluidStack> result = new ArrayList<>();
        for (IndustrialRecipeLedger.Amount amount : amounts) {
            if (!"fluid".equals(amount.kind)) continue;
            if (amount.amount <= 0) throw new IllegalStateException("Non-positive fluid batch: " + amount.id);
            FluidStack stack;
            if (amount.id.startsWith("planned:")) {
                stack = materialFluid(ProsperityIndustrialMaterials.get(amount.id), amount.amount);
            } else if (amount.id.startsWith("standard:Materials.")) {
                stack = materialFluid(standardMaterial(amount.id), amount.amount);
            } else {
                switch (amount.id) {
                    case "wastesigh":
                        stack = rawGas(GTSRProsperityAirMaterials.WastesSigh, amount.amount);
                        break;
                    case "thickgrease":
                        stack = rawGas(GTSRProsperityAirMaterials.ThickGrease, amount.amount);
                        break;
                    case "metalgrit":
                        stack = rawGas(GTSRProsperityAirMaterials.MetalGrit, amount.amount);
                        break;
                    case "umbralmire":
                        stack = rawGas(GTSRProsperityAirMaterials.UmbralMire, amount.amount);
                        break;
                    case "sanzu_residual_steam":
                        stack = rawGas(GTSRProsperityAirMaterials.SanzuResidualSteam, amount.amount);
                        break;
                    case "withered_breath":
                        stack = rawGas(GTSRProsperityAirMaterials.WitheredBreath, amount.amount);
                        break;
                    case "standard:GTModHandler.getDistilledWater":
                        stack = GTModHandler.getDistilledWater(amount.amount);
                        break;
                    case "standard:IC2.coolant":
                        stack = FluidRegistry.getFluidStack("ic2coolant", amount.amount);
                        break;
                    case "standard:Werkstoff.Neon":
                        stack = WerkstoffLoader.Neon.getFluidOrGas(amount.amount);
                        break;
                    case "standard:Werkstoff.Krypton":
                        stack = WerkstoffLoader.Krypton.getFluidOrGas(amount.amount);
                        break;
                    case "standard:Werkstoff.Xenon":
                        stack = WerkstoffLoader.Xenon.getFluidOrGas(amount.amount);
                        break;
                    case "standard:WerkstoffLoader.FormicAcid":
                        stack = WerkstoffLoader.FormicAcid.getFluidOrGas(amount.amount);
                        break;
                    case "standard:GGMaterial.oxalate":
                        stack = GGMaterial.oxalate.getFluidOrGas(amount.amount);
                        break;
                    default:
                        throw new IllegalStateException("Unknown real fluid getter: " + amount.id);
                }
            }
            if (stack == null || stack.getFluid() == null || stack.amount != amount.amount) {
                throw new IllegalStateException("Missing registered fluid form: " + amount.id);
            }
            result.add(stack);
        }
        return result.toArray(new FluidStack[0]);
    }

    private static FluidStack rawGas(Materials holder, int amount) {
        return holder == null ? null : holder.getGas(amount);
    }

    private static FluidStack materialFluid(Materials material, int amount) {
        // Phase is chosen from the registered material, never by inventing a FluidRegistry alias.
        if (material.mFluid != null) return material.getFluid(amount);
        return material.getGas(amount);
    }

    private static Materials standardMaterial(String id) {
        String name = id.substring("standard:Materials.".length());
        try {
            Materials material = (Materials) Materials.class.getField(name)
                .get(null);
            if (material == null || material == Materials._NULL)
                throw new IllegalStateException("Missing GT material " + name);
            return material;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("No real Materials getter for " + id, e);
        }
    }

    private static RecipeMap<?> map(String name) {
        switch (name) {
            case "vacuumFreezerRecipes":
                return RecipeMaps.vacuumFreezerRecipes;
            case "multiblockChemicalReactorRecipes":
                return RecipeMaps.multiblockChemicalReactorRecipes;
            case "distillationTowerRecipes":
                return RecipeMaps.distillationTowerRecipes;
            case "distilleryRecipes":
                return RecipeMaps.distilleryRecipes;
            case "centrifugeRecipes":
                return RecipeMaps.centrifugeRecipes;
            case "mixerRecipes":
                return RecipeMaps.mixerRecipes;
            case "fluidHeaterRecipes":
                return RecipeMaps.fluidHeaterRecipes;
            case "chemicalBathRecipes":
                return RecipeMaps.chemicalBathRecipes;
            case "electroMagneticSeparatorRecipes":
                return RecipeMaps.electroMagneticSeparatorRecipes;
            default:
                throw new IllegalStateException("Unknown industrial recipe map: " + name);
        }
    }

    private static boolean sameItems(ItemStack[] actual, ItemStack[] expected) {
        if (actual.length != expected.length) return false;
        for (int i = 0; i < actual.length; i++) {
            if (!GTUtility.areStacksEqual(actual[i], expected[i]) || actual[i].stackSize != expected[i].stackSize)
                return false;
        }
        return true;
    }

    private static boolean sameFluids(FluidStack[] actual, FluidStack[] expected) {
        if (actual.length != expected.length) return false;
        for (int i = 0; i < actual.length; i++) {
            if (!actual[i].isFluidEqual(expected[i]) || actual[i].amount != expected[i].amount) return false;
        }
        return true;
    }

    private static final class ResolvedStage {

        private IndustrialRecipeLedger.Stage definition;
        private RecipeMap<?> map;
        private ItemStack[] itemsIn, itemsOut;
        private FluidStack[] fluidsIn, fluidsOut;
    }
}
