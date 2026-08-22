package com.miaokatze.gtsr.loader.recipes;

import static com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps.airCompressorRecipes;
import static com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps.ammoniaPlantRecipes;
import static com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps.atmosphericCentrifugeRecipes;
import static com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps.largeCokeOvenRecipes;
import static com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps.siemensMartinRecipes;
import static com.miaokatze.gtsr.loader.recipes.RecipeLoaderUtils.filterNulls;
import static com.miaokatze.gtsr.loader.recipes.RecipeLoaderUtils.get;
import static com.miaokatze.gtsr.loader.recipes.RecipeLoaderUtils.hasNull;
import static com.miaokatze.gtsr.loader.recipes.RecipeLoaderUtils.log;
import static com.miaokatze.gtsr.loader.recipes.RecipeLoaderUtils.warn;
import static gregtech.api.recipe.RecipeMaps.assemblerRecipes;
import static gregtech.api.util.GTRecipeBuilder.INGOTS;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;

import bartworks.system.material.WerkstoffLoader;
import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.enums.TierEU;
import gregtech.api.util.GTOreDictUnificator;
import gtPlusPlus.core.fluids.GTPPFluids;

/**
 * 处理机族配方（SR-A03 组一，自 GTSRRecipeLoader 门面原样迁出）：大型焦炉/平炉/氨厂/
 * 巨型空压机/大气离心机/芯片/催化剂。方法体逐字未动；注册顺序与错误隔离由门面
 * {@code GTSRRecipeLoader.run()} 派发表 + safeRegister 单点决定，本类不做任何注册决策。
 */
public final class ProcessingMachineRecipes {

    private ProcessingMachineRecipes() {}

    public static void registerCokeOvenRecipes() {
        GTValues.RA.stdBuilder()
            .itemInputs(Materials.Coal.getGems(1))
            .itemOutputs(
                OreDictionary.getOres("fuelCoke")
                    .get(0)
                    .copy())
            .fluidOutputs(Materials.Creosote.getFluid(500))
            .duration(1800)
            .eut(0)
            .addTo(largeCokeOvenRecipes);
    }

    public static void registerSiemensMartinRecipes() {
        GTValues.RA.stdBuilder()
            .itemInputs(Materials.Iron.getIngots(2), Materials.Coal.getGems(2))
            .itemOutputs(Materials.Steel.getIngots(2), Materials.Ash.getDust(1))
            .duration(1600 * SECONDS)
            .eut(0)
            .addTo(siemensMartinRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(Materials.Iron.getDust(2), Materials.Coal.getGems(2))
            .itemOutputs(Materials.Steel.getIngots(2), Materials.Ash.getDust(1))
            .duration(1600 * SECONDS)
            .eut(0)
            .addTo(siemensMartinRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(Materials.Iron.getIngots(2), Materials.Coal.getDust(2))
            .itemOutputs(Materials.Steel.getIngots(2), Materials.Ash.getDust(1))
            .duration(1600 * SECONDS)
            .eut(0)
            .addTo(siemensMartinRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(Materials.Iron.getDust(2), Materials.Coal.getDust(2))
            .itemOutputs(Materials.Steel.getIngots(2), Materials.Ash.getDust(1))
            .duration(1600 * SECONDS)
            .eut(0)
            .addTo(siemensMartinRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                Materials.Iron.getIngots(2),
                OreDictionary.getOres("fuelCoke")
                    .get(0)
                    .copy())
            .itemOutputs(Materials.Steel.getIngots(2), Materials.Ash.getDust(1))
            .outputChances(10000, 7000)
            .duration(1200 * SECONDS)
            .eut(0)
            .addTo(siemensMartinRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                Materials.Iron.getDust(2),
                OreDictionary.getOres("fuelCoke")
                    .get(0)
                    .copy())
            .itemOutputs(Materials.Steel.getIngots(2), Materials.Ash.getDust(1))
            .outputChances(10000, 7000)
            .duration(1200 * SECONDS)
            .eut(0)
            .addTo(siemensMartinRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(Materials.Iron.getIngots(2), Materials.Carbon.getDust(1))
            .itemOutputs(Materials.Steel.getIngots(2), Materials.Ash.getDust(1))
            .outputChances(10000, 3000)
            .duration(800 * SECONDS)
            .eut(0)
            .addTo(siemensMartinRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(Materials.Iron.getDust(2), Materials.Carbon.getDust(1))
            .itemOutputs(Materials.Steel.getIngots(2), Materials.Ash.getDust(1))
            .outputChances(10000, 3000)
            .duration(800 * SECONDS)
            .eut(0)
            .addTo(siemensMartinRecipes);
    }

    public static void registerAmmoniaRecipes() {
        GTValues.RA.stdBuilder()
            .fluidInputs(Materials.Gas.getGas(1000), Materials.Nitrogen.getGas(1000))
            .fluidOutputs(Materials.Ammonia.getGas(1000))
            .itemOutputs(Materials.Ash.getDust(1), Materials.Carbon.getDust(1))
            .outputChances(10000, 1000)
            .duration(64 * SECONDS)
            .eut(0)
            .addTo(ammoniaPlantRecipes);
    }

    public static void registerAirCompressorRecipes() {
        GTValues.RA.stdBuilder()
            .fluidOutputs(Materials.Air.getGas(800))
            .duration(20)
            .eut(-60)
            .addTo(airCompressorRecipes);

        try {
            FluidStack netherAir = Materials.NetherAir.getFluid(800);
            if (netherAir != null) {
                GTValues.RA.stdBuilder()
                    .fluidOutputs(netherAir)
                    .duration(20)
                    .eut(-60)
                    .addTo(airCompressorRecipes);
            } else {
                warn("Materials.NetherAir.getFluid(800) returned null, skipping Nether Air recipe!");
            }
        } catch (Exception e) {
            warn("Failed to register Nether Air compressor recipe: " + e.getMessage());
        }
    }

    public static void registerAtmosphericCentrifugeRecipes() {
        GTValues.RA.stdBuilder()
            .fluidInputs(Materials.Air.getGas(10000))
            .fluidOutputs(Materials.Nitrogen.getGas(7800), Materials.Oxygen.getGas(2100))
            .duration(1000)
            .eut(-25)
            .addTo(atmosphericCentrifugeRecipes);

        GTValues.RA.stdBuilder()
            .fluidInputs(Materials.Air.getGas(2000000))
            .fluidOutputs(
                Materials.Nitrogen.getGas(1560000),
                Materials.Oxygen.getGas(420000),
                Materials.Argon.getGas(3860),
                Materials.CarbonDioxide.getGas(600),
                WerkstoffLoader.Neon.getFluidOrGas(40),
                Materials.Methane.getGas(4),
                WerkstoffLoader.Krypton.getFluidOrGas(2),
                Materials.Helium.getGas(1))
            .duration(40000)
            .eut(-250)
            .addTo(atmosphericCentrifugeRecipes);

        try {
            FluidStack netherAirInput = Materials.NetherAir.getFluid(10000);
            if (netherAirInput == null) {
                warn("Materials.NetherAir.getFluid() returned null, skipping Nether Air centrifuge recipes!");
                return;
            }
            GTValues.RA.stdBuilder()
                .fluidInputs(netherAirInput)
                .fluidOutputs(
                    Materials.NitrogenDioxide.getGas(1400),
                    Materials.SulfurDioxide.getGas(3800),
                    Materials.SulfurTrioxide.getGas(2100))
                .duration(1000)
                .eut(-25)
                .addTo(atmosphericCentrifugeRecipes);
        } catch (Exception e) {
            warn("Failed to register Nether Air basic centrifuge recipe: " + e.getMessage());
        }

        try {
            FluidStack netherAirInput2 = Materials.NetherAir.getFluid(100000);
            FluidStack anthracene = (GTPPFluids.Anthracene != null) ? new FluidStack(GTPPFluids.Anthracene, 2500)
                : null;
            if (netherAirInput2 == null || anthracene == null) {
                warn(
                    "Skipping Nether Air rare gas centrifuge recipe - NetherAir=" + (netherAirInput2 != null)
                        + ", Anthracene="
                        + (anthracene != null));
                return;
            }
            GTValues.RA.stdBuilder()
                .fluidInputs(netherAirInput2)
                .fluidOutputs(
                    Materials.NitrogenDioxide.getGas(14000),
                    Materials.SulfurDioxide.getGas(35000),
                    Materials.SulfurTrioxide.getGas(20000),
                    Materials.Chlorine.getGas(2000),
                    WerkstoffLoader.Neon.getFluidOrGas(1200),
                    anthracene,
                    Materials.Radon.getGas(1))
                .duration(20000)
                .eut(-250)
                .addTo(atmosphericCentrifugeRecipes);
        } catch (Exception e) {
            warn("Failed to register Nether Air rare gas centrifuge recipe: " + e.getMessage());
        }
    }

    public static void registerChipRecipes() {
        log("Registering chip recipes...");

        ItemStack t1out = get(GTSRItemList.VeinPyrolyzerChipT1, 1);
        ItemStack t2out = get(GTSRItemList.VeinPyrolyzerChipT2, 1);
        ItemStack t3out = get(GTSRItemList.VeinPyrolyzerChipT3, 1);
        ItemStack geoOut = get(GTSRItemList.GeothermalOverheatChip, 1);
        ItemStack hubOut = get(GTSRItemList.HubSingularityChip, 1);
        ItemStack rareOut = get(GTSRItemList.RareGasSeparationChip, 1);

        if (!hasNull(t1out)) {
            ItemStack[] inputs = filterNulls(
                get(ItemList.OilDrill2, 1),
                get(GTSRItemList.SteamEntangledSingularity, 16),
                get(OrePrefixes.plateDouble, Materials.StainlessSteel, 32),
                get(OrePrefixes.circuit, Materials.HV, 8));
            GTValues.RA.stdBuilder()
                .itemInputs(inputs)
                .itemOutputs(t1out)
                .fluidInputs(Materials.SolderingAlloy.getMolten(4 * INGOTS))
                .duration(30 * SECONDS)
                .eut(TierEU.RECIPE_HV)
                .addTo(assemblerRecipes);
        } else {
            warn("Skipped VeinPyrolyzerChipT1 recipe - output is null");
        }

        if (!hasNull(t2out)) {
            ItemStack[] inputs = filterNulls(
                get(ItemList.OilDrill3, 1),
                get(GTSRItemList.SteamEntangledSingularity, 32),
                get(OrePrefixes.plateTriple, Materials.Titanium, 64),
                get(OrePrefixes.circuit, Materials.EV, 16),
                get(GTSRItemList.VeinPyrolyzerChipT1, 1));
            GTValues.RA.stdBuilder()
                .itemInputs(inputs)
                .itemOutputs(t2out)
                .fluidInputs(Materials.SolderingAlloy.getMolten(6 * INGOTS))
                .duration(45 * SECONDS)
                .eut(TierEU.RECIPE_EV)
                .addTo(assemblerRecipes);
        } else {
            warn("Skipped VeinPyrolyzerChipT2 recipe - output is null");
        }

        if (!hasNull(t3out)) {
            ItemStack[] inputs = filterNulls(
                get(ItemList.OilDrill4, 1),
                get(GTSRItemList.SteamEntangledSingularity, 64),
                get(OrePrefixes.plateDense, Materials.TungstenSteel, 64),
                get(OrePrefixes.circuit, Materials.IV, 32),
                get(GTSRItemList.VeinPyrolyzerChipT2, 1));
            GTValues.RA.stdBuilder()
                .itemInputs(inputs)
                .itemOutputs(t3out)
                .fluidInputs(Materials.SolderingAlloy.getMolten(8 * INGOTS))
                .duration(60 * SECONDS)
                .eut(TierEU.RECIPE_IV)
                .addTo(assemblerRecipes);
        } else {
            warn("Skipped VeinPyrolyzerChipT3 recipe - output is null");
        }

        if (!hasNull(geoOut)) {
            ItemStack[] inputs = filterNulls(
                get(GTSRItemList.SteamEntangledSingularity, 6),
                get(OrePrefixes.plate, Materials.Gold, 4),
                get(OrePrefixes.plate, Materials.Steel, 4),
                get(OrePrefixes.circuit, Materials.LV, 2),
                get(ItemList.Electric_Pump_LV, 8));
            GTValues.RA.stdBuilder()
                .itemInputs(inputs)
                .itemOutputs(geoOut)
                .fluidInputs(Materials.SolderingAlloy.getMolten(2 * INGOTS))
                .duration(20 * SECONDS)
                .eut(TierEU.RECIPE_LV)
                .addTo(assemblerRecipes);
        } else {
            warn("Skipped GeothermalOverheatChip recipe - output is null");
        }

        if (!hasNull(hubOut)) {
            ItemStack hubBoard = new ItemStack(GameRegistry.findItem("gregtech", "gt.metaitem.01"), 1, 19305);
            ItemStack hubCircuit = get(OrePrefixes.circuit, Materials.LV, 2); // 任意 LV 电路（OreDict，模板 dreamcraft
                                                                              // CircuitLV 的替代）
            ItemStack hubSingularity = GTSRItemList.SteamEntangledSingularity.get(1);
            if (!hasNull(hubBoard, hubCircuit, hubSingularity)) {
                GameRegistry
                    .addShapedRecipe(hubOut, "AAA", "BCB", "AAA", 'A', hubBoard, 'B', hubCircuit, 'C', hubSingularity);
            } else {
                warn("Skipped HubSingularityChip crafting recipe - inputs contain null");
            }
        } else {
            warn("Skipped HubSingularityChip recipe - output is null");
        }

        if (!hasNull(rareOut)) {
            ItemStack[] inputs = filterNulls(
                get(GTSRItemList.SteamEntangledSingularity, 16),
                get(OrePrefixes.plateDouble, Materials.StainlessSteel, 32),
                get(OrePrefixes.circuit, Materials.HV, 8),
                get(OrePrefixes.screw, Materials.StainlessSteel, 64),
                get(ItemList.Electric_Motor_HV, 32));
            GTValues.RA.stdBuilder()
                .itemInputs(inputs)
                .itemOutputs(rareOut)
                .fluidInputs(Materials.SolderingAlloy.getMolten(6 * INGOTS))
                .duration(40 * SECONDS)
                .eut(TierEU.RECIPE_HV)
                .addTo(assemblerRecipes);
        } else {
            warn("Skipped RareGasSeparationChip recipe - output is null");
        }

        log("Chip recipes done.");
    }

    public static void registerCatalystRecipes() {
        log("Registering catalyst recipes (tier order: Ni->Pt->U->Os->FeCo->Ru->Quantum)...");

        ItemStack niOut = get(GTSRItemList.AmmoniaCatalystNickel, 1);
        if (!hasNull(niOut)) {
            ItemStack[] inputs = filterNulls(
                get(OrePrefixes.dust, Materials.Nickel, 48),
                get(OrePrefixes.dust, Materials.Aluminium, 24),
                get(OrePrefixes.plate, Materials.StainlessSteel, 32));
            GTValues.RA.stdBuilder()
                .itemInputs(inputs)
                .itemOutputs(niOut)
                .fluidInputs(Materials.Oxygen.getGas(24000))
                .duration(480 * SECONDS)
                .eut(TierEU.RECIPE_HV)
                .addTo(assemblerRecipes);
        } else {
            warn("Skipped AmmoniaCatalystNickel recipe");
        }

        ItemStack ptOut = get(GTSRItemList.AmmoniaCatalystPlatinum, 1);
        if (!hasNull(ptOut)) {
            ItemStack[] inputs = filterNulls(
                get(OrePrefixes.dust, Materials.Platinum, 16),
                get(OrePrefixes.dust, Materials.Aluminium, 48),
                get(OrePrefixes.plate, Materials.Titanium, 32));
            GTValues.RA.stdBuilder()
                .itemInputs(inputs)
                .itemOutputs(ptOut)
                .fluidInputs(Materials.Hydrogen.getGas(16000))
                .duration(640 * SECONDS)
                .eut(TierEU.RECIPE_EV)
                .addTo(assemblerRecipes);
        } else {
            warn("Skipped AmmoniaCatalystPlatinum recipe");
        }

        ItemStack uOut = get(GTSRItemList.AmmoniaCatalystUranium, 1);
        if (!hasNull(uOut)) {
            ItemStack[] inputs = filterNulls(
                get(OrePrefixes.dust, Materials.Uranium, 16),
                get(OrePrefixes.dust, Materials.Iron, 32),
                get(OrePrefixes.dust, Materials.Aluminium, 24),
                get(OrePrefixes.plate, Materials.TungstenSteel, 48));
            GTValues.RA.stdBuilder()
                .itemInputs(inputs)
                .itemOutputs(uOut)
                .fluidInputs(Materials.Nitrogen.getGas(16000))
                .duration(800 * SECONDS)
                .eut(TierEU.RECIPE_IV)
                .addTo(assemblerRecipes);
        } else {
            warn("Skipped AmmoniaCatalystUranium recipe");
        }

        ItemStack osOut = get(GTSRItemList.AmmoniaCatalystOsmium, 1);
        if (!hasNull(osOut)) {
            ItemStack[] inputs = filterNulls(
                get(OrePrefixes.dust, Materials.Osmium, 48),
                get(OrePrefixes.dust, Materials.Aluminium, 40),
                get(OrePrefixes.dust, Materials.Silicon, 8),
                get(OrePrefixes.plateDense, Materials.TungstenSteel, 48));
            GTValues.RA.stdBuilder()
                .itemInputs(inputs)
                .itemOutputs(osOut)
                .fluidInputs(Materials.Nitrogen.getGas(24000))
                .duration(800 * SECONDS)
                .eut(TierEU.RECIPE_IV)
                .addTo(assemblerRecipes);
        } else {
            warn("Skipped AmmoniaCatalystOsmium recipe");
        }

        ItemStack feCoOut = get(GTSRItemList.AmmoniaCatalystFeCo, 1);
        if (!hasNull(feCoOut)) {
            ItemStack luvPlate = GTOreDictUnificator.get(OrePrefixes.plateDense, Materials.Osmiridium, 32);
            if (luvPlate == null) {
                warn("Osmiridium dense plate (32) returned null!");
            }
            ItemStack[] inputs = filterNulls(
                get(OrePrefixes.dust, Materials.Iron, 64),
                get(OrePrefixes.dust, Materials.Cobalt, 8),
                get(OrePrefixes.dust, Materials.Aluminium, 16),
                luvPlate);
            GTValues.RA.stdBuilder()
                .itemInputs(inputs)
                .itemOutputs(feCoOut)
                .fluidInputs(Materials.Oxygen.getGas(32000))
                .duration(960 * SECONDS)
                .eut(TierEU.RECIPE_LuV)
                .addTo(assemblerRecipes);
        } else {
            warn("Skipped AmmoniaCatalystFeCo recipe");
        }

        ItemStack ruOut = get(GTSRItemList.AmmoniaCatalystRuthenium, 1);
        if (!hasNull(ruOut)) {
            ItemStack rutheniumDust = WerkstoffLoader.Ruthenium.get(OrePrefixes.dust, 16);
            if (rutheniumDust == null) {
                warn("WerkstoffLoader.Ruthenium.get(dust, 16) returned null!");
            }
            ItemStack[] inputs = filterNulls(
                rutheniumDust,
                get(OrePrefixes.dust, Materials.Carbon, 40),
                get(OrePrefixes.dust, Materials.Aluminium, 8),
                get(OrePrefixes.plateDense, Materials.Iridium, 48));
            GTValues.RA.stdBuilder()
                .itemInputs(inputs)
                .itemOutputs(ruOut)
                .fluidInputs(Materials.Hydrogen.getGas(24000))
                .duration(1200 * SECONDS)
                .eut(TierEU.RECIPE_ZPM)
                .addTo(assemblerRecipes);
        } else {
            warn("Skipped AmmoniaCatalystRuthenium recipe");
        }

        ItemStack qOut = get(GTSRItemList.AmmoniaCatalystQuantum, 1);
        if (!hasNull(qOut)) {
            ItemStack quantumDust = Materials.Quantium.getDust(64);
            if (quantumDust == null) {
                warn("Materials.Quantium.getDust(64) returned null!");
            }
            ItemStack[] inputs = filterNulls(
                get(OrePrefixes.dust, Materials.CosmicNeutronium, 16),
                quantumDust,
                get(OrePrefixes.plateDense, Materials.Osmium, 48));
            GTValues.RA.stdBuilder()
                .itemInputs(inputs)
                .itemOutputs(qOut)
                .fluidInputs(WerkstoffLoader.Krypton.getFluidOrGas(32000))
                .duration(1440 * SECONDS)
                .eut(TierEU.RECIPE_UV)
                .addTo(assemblerRecipes);
        } else {
            warn("Skipped AmmoniaCatalystQuantum recipe");
        }

        log("Catalyst recipes done.");
    }
}
