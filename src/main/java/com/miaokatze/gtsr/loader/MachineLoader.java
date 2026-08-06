package com.miaokatze.gtsr.loader;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.api.enums.MetaTileEntityID;
import com.miaokatze.gtsr.common.machine.MTEAirCompressor;
import com.miaokatze.gtsr.common.machine.MTEAmmoniaPlant;
import com.miaokatze.gtsr.common.machine.MTEAtmosphericCentrifuge;
import com.miaokatze.gtsr.common.machine.MTECriticalSingularityCompressor;
import com.miaokatze.gtsr.common.machine.MTECrustSteamBorer;
import com.miaokatze.gtsr.common.machine.MTEDenseStateManipulator;
import com.miaokatze.gtsr.common.machine.MTEGearSteamCompressor;
import com.miaokatze.gtsr.common.machine.MTEKineticProcessingArray;
import com.miaokatze.gtsr.common.machine.MTELargeCokeOven;
import com.miaokatze.gtsr.common.machine.MTELargeGeothermalSteamBoiler;
import com.miaokatze.gtsr.common.machine.MTELargeSolarOverpressureArray;
import com.miaokatze.gtsr.common.machine.MTELargeSolarOverpressureArrayOLD;
import com.miaokatze.gtsr.common.machine.MTELargeSteamFurnace;
import com.miaokatze.gtsr.common.machine.MTEMegaSteamTurbineArray;
import com.miaokatze.gtsr.common.machine.MTEReinforcedBrickBlastFurnace;
import com.miaokatze.gtsr.common.machine.MTESiemensMartinFurnace;
import com.miaokatze.gtsr.common.machine.MTESingularityDrillingHub;
import com.miaokatze.gtsr.common.machine.MTESingularityDrillingNode;
import com.miaokatze.gtsr.common.machine.MTESingularityMinerNode;
import com.miaokatze.gtsr.common.machine.MTESteamFluidDrill;
import com.miaokatze.gtsr.common.machine.MTESteamHubArray;
import com.miaokatze.gtsr.common.machine.MTESteamSingularityCompressor;
import com.miaokatze.gtsr.common.machine.MTESteamSingularityCompressorOLD;
import com.miaokatze.gtsr.common.machine.MTEVeinSteamPyrolyzer;
import com.miaokatze.gtsr.common.machine.MTEVoidCrustSteamBorer;
import com.miaokatze.gtsr.common.machine.MTEVoidCrustSteamBorerOLD;
import com.miaokatze.gtsr.common.machine.MTEWaterHubArray;
import com.miaokatze.gtsr.common.machine.base.MTEDistilledWaterHatch;
import com.miaokatze.gtsr.common.machine.base.MTEHatchPressureSteamInput;
import com.miaokatze.gtsr.common.machine.base.MTEHubStorageUnit;
import com.miaokatze.gtsr.common.machine.base.MTELegacyConverter;
import com.miaokatze.gtsr.common.machine.base.MTEMegaAirInputHatch;
import com.miaokatze.gtsr.common.machine.base.MTEOverpressureHubStorageUnit;
import com.miaokatze.gtsr.common.machine.base.MTEOverpressureSteamCacheNode;
import com.miaokatze.gtsr.common.machine.base.MTEOverpressureTurbineInputHatch;
import com.miaokatze.gtsr.common.machine.base.MTEPressureSteamCoolingHatch;
import com.miaokatze.gtsr.common.machine.base.MTEPressureSteamOutputHatch;
import com.miaokatze.gtsr.common.machine.base.MTEReinforcedHubStorageUnit;
import com.miaokatze.gtsr.common.machine.base.MTEReinforcedSteamCacheNode;
import com.miaokatze.gtsr.common.machine.base.MTESteamCacheNode;
import com.miaokatze.gtsr.common.machine.base.MTESteamCoolingHatch;
import com.miaokatze.gtsr.common.machine.base.MTESteamHubInputHatch;
import com.miaokatze.gtsr.common.machine.base.MTESteamHubOutputHatch;
import com.miaokatze.gtsr.common.machine.base.MTESteamInputHatchGeneric;
import com.miaokatze.gtsr.common.machine.base.MTESteamOutputHatch;
import com.miaokatze.gtsr.common.machine.base.MTESteamOutputHatchGeneric;
import com.miaokatze.gtsr.common.machine.base.MTEWaterCacheNode;
import com.miaokatze.gtsr.common.machine.base.MTEWaterHubInputHatch;
import com.miaokatze.gtsr.common.machine.base.MTEWaterHubOutputHatch;
import com.miaokatze.gtsr.register.CreativeTabManager;

public class MachineLoader {

    public static void initMachines() {
        registerAllMachines();
        addItemsToCreativeTab();
    }

    private static void registerAllMachines() {
        // --- 单方块机器 ---
        GTSRItemList.SteamCacheNode.set(
            new MTESteamCacheNode(MetaTileEntityID.STEAM_CACHE_NODE.ID, "gtsr.steam.cache.node", "Steam Cache Node"));
        new MTELegacyConverter(
            MetaTileEntityID.STEAM_CACHE_NODE.OLD_ID,
            "gtsr.legacy.converter.steam_cache_node",
            "[OLD] Steam Cache Node",
            MetaTileEntityID.STEAM_CACHE_NODE.ID);
        GTSRItemList.ReinforcedSteamCacheNode.set(
            new MTEReinforcedSteamCacheNode(
                MetaTileEntityID.REINFORCED_STEAM_CACHE_NODE.ID,
                "gtsr.reinforced.steam.cache.node",
                "Reinforced Steam Cache Node"));
        new MTELegacyConverter(
            MetaTileEntityID.REINFORCED_STEAM_CACHE_NODE.OLD_ID,
            "gtsr.legacy.converter.reinforced_steam_cache_node",
            "[OLD] Reinforced Steam Cache Node",
            MetaTileEntityID.REINFORCED_STEAM_CACHE_NODE.ID);
        GTSRItemList.OverpressureHubStorageUnit.set(
            new MTEOverpressureHubStorageUnit(
                MetaTileEntityID.OVERPRESSURE_HUB_STORAGE_UNIT.ID,
                "gtsr.overpressure.hub.storage.unit",
                "Overpressure Hub Storage Unit"));
        new MTELegacyConverter(
            MetaTileEntityID.OVERPRESSURE_HUB_STORAGE_UNIT.OLD_ID,
            "gtsr.legacy.converter.overpressure_hub_storage_unit",
            "[OLD] Overpressure Hub Storage Unit",
            MetaTileEntityID.OVERPRESSURE_HUB_STORAGE_UNIT.ID);
        GTSRItemList.WaterCacheNode.set(
            new MTEWaterCacheNode(MetaTileEntityID.WATER_CACHE_NODE.ID, "gtsr.water.cache.node", "Water Cache Node"));
        new MTELegacyConverter(
            MetaTileEntityID.WATER_CACHE_NODE.OLD_ID,
            "gtsr.legacy.converter.water_cache_node",
            "[OLD] Water Cache Node",
            MetaTileEntityID.WATER_CACHE_NODE.ID);
        GTSRItemList.SingularityMinerNode.set(
            new MTESingularityMinerNode(
                MetaTileEntityID.SINGULARITY_MINER_NODE.ID,
                "gtsr.singularity.miner.node",
                "Singularity Miner Node"));
        new MTELegacyConverter(
            MetaTileEntityID.SINGULARITY_MINER_NODE.OLD_ID,
            "gtsr.legacy.converter.singularity_miner_node",
            "[OLD] Singularity Miner Node",
            MetaTileEntityID.SINGULARITY_MINER_NODE.ID);
        GTSRItemList.SingularityDrillingNode.set(
            new MTESingularityDrillingNode(
                MetaTileEntityID.SINGULARITY_DRILLING_NODE.ID,
                "gtsr.singularity.drilling.node",
                "Singularity Drilling Node"));
        new MTELegacyConverter(
            MetaTileEntityID.SINGULARITY_DRILLING_NODE.OLD_ID,
            "gtsr.legacy.converter.singularity_drilling_node",
            "[OLD] Singularity Drilling Node",
            MetaTileEntityID.SINGULARITY_DRILLING_NODE.ID);
        GTSRItemList.SteamSingularityCompressor.set(
            new MTESteamSingularityCompressor(
                MetaTileEntityID.STEAM_SINGULARITY_COMPRESSOR.ID,
                "gtsr.steam.singularity.compressor",
                "Steam Singularity Compressor"));
        new MTESteamSingularityCompressorOLD(
            MetaTileEntityID.STEAM_SINGULARITY_COMPRESSOR.OLD_ID,
            "gtsr.steam.singularity.compressor.old",
            "Steam Singularity Compressor");
        GTSRItemList.CriticalSingularityCompressor.set(
            new MTECriticalSingularityCompressor(
                MetaTileEntityID.CRITICAL_SINGULARITY_COMPRESSOR.ID,
                "gtsr.critical.singularity.compressor",
                "Critical Singularity Compressor"));
        GTSRItemList.DenseStateManipulator.set(
            new MTEDenseStateManipulator(
                MetaTileEntityID.DENSE_STATE_MANIPULATOR.ID,
                "gtsr.dense.state.manipulator",
                "Dense State Manipulator"));

        // --- 多方块机器: 存储枢纽及其仓室和模块 ---
        GTSRItemList.SteamHubArray
            .set(new MTESteamHubArray(MetaTileEntityID.STEAM_HUB_ARRAY.ID, "gtsr.steam.hub.array", "Steam Hub Array"));
        new MTELegacyConverter(
            MetaTileEntityID.STEAM_HUB_ARRAY.OLD_ID,
            "gtsr.legacy.converter.steam_hub_array",
            "[OLD] Steam Hub Array",
            MetaTileEntityID.STEAM_HUB_ARRAY.ID);
        GTSRItemList.WaterHubArray
            .set(new MTEWaterHubArray(MetaTileEntityID.WATER_HUB_ARRAY.ID, "gtsr.water.hub.array", "Water Hub Array"));
        new MTELegacyConverter(
            MetaTileEntityID.WATER_HUB_ARRAY.OLD_ID,
            "gtsr.legacy.converter.water_hub_array",
            "[OLD] Water Hub Array",
            MetaTileEntityID.WATER_HUB_ARRAY.ID);
        GTSRItemList.SteamHubInputHatch.set(
            new MTESteamHubInputHatch(
                MetaTileEntityID.STEAM_HUB_INPUT_HATCH.ID,
                "gtsr.steam.hub.input.hatch",
                "Steam Hub Input Hatch"));
        new MTELegacyConverter(
            MetaTileEntityID.STEAM_HUB_INPUT_HATCH.OLD_ID,
            "gtsr.legacy.converter.steam_hub_input_hatch",
            "[OLD] Steam Hub Input Hatch",
            MetaTileEntityID.STEAM_HUB_INPUT_HATCH.ID);
        GTSRItemList.SteamHubOutputHatch.set(
            new MTESteamHubOutputHatch(
                MetaTileEntityID.STEAM_HUB_OUTPUT_HATCH.ID,
                "gtsr.steam.hub.output.hatch",
                "Steam Hub Output Hatch"));
        new MTELegacyConverter(
            MetaTileEntityID.STEAM_HUB_OUTPUT_HATCH.OLD_ID,
            "gtsr.legacy.converter.steam_hub_output_hatch",
            "[OLD] Steam Hub Output Hatch",
            MetaTileEntityID.STEAM_HUB_OUTPUT_HATCH.ID);
        GTSRItemList.WaterHubInputHatch.set(
            new MTEWaterHubInputHatch(
                MetaTileEntityID.WATER_HUB_INPUT_HATCH.ID,
                "gtsr.water.hub.input.hatch",
                "Water Hub Input Hatch"));
        new MTELegacyConverter(
            MetaTileEntityID.WATER_HUB_INPUT_HATCH.OLD_ID,
            "gtsr.legacy.converter.water_hub_input_hatch",
            "[OLD] Water Hub Input Hatch",
            MetaTileEntityID.WATER_HUB_INPUT_HATCH.ID);
        GTSRItemList.WaterHubOutputHatch.set(
            new MTEWaterHubOutputHatch(
                MetaTileEntityID.WATER_HUB_OUTPUT_HATCH.ID,
                "gtsr.water.hub.output.hatch",
                "Water Hub Output Hatch"));
        new MTELegacyConverter(
            MetaTileEntityID.WATER_HUB_OUTPUT_HATCH.OLD_ID,
            "gtsr.legacy.converter.water_hub_output_hatch",
            "[OLD] Water Hub Output Hatch",
            MetaTileEntityID.WATER_HUB_OUTPUT_HATCH.ID);
        GTSRItemList.OverpressureTurbineInputHatch.set(
            new MTEOverpressureTurbineInputHatch(
                MetaTileEntityID.OVERPRESSURE_TURBINE_INPUT_HATCH.ID,
                "gtsr.overpressure.turbine.input.hatch",
                "Mega Overpressure Steam Input Hatch"));
        new MTELegacyConverter(
            MetaTileEntityID.OVERPRESSURE_TURBINE_INPUT_HATCH.OLD_ID,
            "gtsr.legacy.converter.overpressure_turbine_input_hatch",
            "[OLD] Mega Overpressure Steam Input Hatch",
            MetaTileEntityID.OVERPRESSURE_TURBINE_INPUT_HATCH.ID);
        GTSRItemList.HubStorageUnit.set(
            new MTEHubStorageUnit(MetaTileEntityID.HUB_STORAGE_UNIT.ID, "gtsr.hub.storage.unit", "Hub Storage Unit"));
        new MTELegacyConverter(
            MetaTileEntityID.HUB_STORAGE_UNIT.OLD_ID,
            "gtsr.legacy.converter.hub_storage_unit",
            "[OLD] Hub Storage Unit",
            MetaTileEntityID.HUB_STORAGE_UNIT.ID);
        GTSRItemList.ReinforcedHubStorageUnit.set(
            new MTEReinforcedHubStorageUnit(
                MetaTileEntityID.REINFORCED_HUB_STORAGE_UNIT.ID,
                "gtsr.reinforced.hub.storage.unit",
                "Reinforced Hub Storage Unit"));
        new MTELegacyConverter(
            MetaTileEntityID.REINFORCED_HUB_STORAGE_UNIT.OLD_ID,
            "gtsr.legacy.converter.reinforced_hub_storage_unit",
            "[OLD] Reinforced Hub Storage Unit",
            MetaTileEntityID.REINFORCED_HUB_STORAGE_UNIT.ID);
        GTSRItemList.OverpressureSteamCacheNode.set(
            new MTEOverpressureSteamCacheNode(
                MetaTileEntityID.OVERPRESSURE_STEAM_CACHE_NODE.ID,
                "gtsr.overpressure.steam.cache.node",
                "Overpressure Steam Cache Node"));
        new MTELegacyConverter(
            MetaTileEntityID.OVERPRESSURE_STEAM_CACHE_NODE.OLD_ID,
            "gtsr.legacy.converter.overpressure_steam_cache_node",
            "[OLD] Overpressure Steam Cache Node",
            MetaTileEntityID.OVERPRESSURE_STEAM_CACHE_NODE.ID);

        // --- 多方块机器: 蒸汽生产 ---
        GTSRItemList.LargeSolarOverpressureArray.set(
            new MTELargeSolarOverpressureArray(
                MetaTileEntityID.LARGE_SOLAR_OVERPRESSURE_ARRAY.ID,
                "gtsr.large.solar.overpressure.array",
                "Large Solar Overpressure Array"));
        new MTELargeSolarOverpressureArrayOLD(
            MetaTileEntityID.LARGE_SOLAR_OVERPRESSURE_ARRAY.OLD_ID,
            "gtsr.large.solar.overpressure.array.old",
            "Large Solar Overpressure Array");
        GTSRItemList.LargeGeothermalSteamBoiler.set(
            new MTELargeGeothermalSteamBoiler(
                MetaTileEntityID.LARGE_GEOTHERMAL_STEAM_BOILER.ID,
                "gtsr.large.geothermal.steam.boiler",
                "Large Geothermal Steam Boiler"));
        new MTELegacyConverter(
            MetaTileEntityID.LARGE_GEOTHERMAL_STEAM_BOILER.OLD_ID,
            "gtsr.legacy.converter.large_geothermal_steam_boiler",
            "[OLD] Large Geothermal Steam Boiler",
            MetaTileEntityID.LARGE_GEOTHERMAL_STEAM_BOILER.ID);

        // --- 多方块机器: 蒸汽产电 ---
        GTSRItemList.MegaSteamTurbineArray.set(
            new MTEMegaSteamTurbineArray(
                MetaTileEntityID.MEGA_STEAM_TURBINE_ARRAY.ID,
                "gtsr.mega.steam.turbine.array",
                "Mega Steam Turbine Array"));
        new MTELegacyConverter(
            MetaTileEntityID.MEGA_STEAM_TURBINE_ARRAY.OLD_ID,
            "gtsr.legacy.converter.mega_steam_turbine_array",
            "[OLD] Mega Steam Turbine Array",
            MetaTileEntityID.MEGA_STEAM_TURBINE_ARRAY.ID);

        // --- 多方块机器: 工作机器 ---
        GTSRItemList.SteamFluidDrill.set(
            new MTESteamFluidDrill(
                MetaTileEntityID.STEAM_FLUID_DRILL.ID,
                "gtsr.steam.fluid.drill",
                "Steam Fluid Drill"));
        new MTELegacyConverter(
            MetaTileEntityID.STEAM_FLUID_DRILL.OLD_ID,
            "gtsr.legacy.converter.steam_fluid_drill",
            "[OLD] Steam Fluid Drill",
            MetaTileEntityID.STEAM_FLUID_DRILL.ID);
        GTSRItemList.CrustSteamBorer.set(
            new MTECrustSteamBorer(
                MetaTileEntityID.CRUST_STEAM_BORER.ID,
                "gtsr.crust.steam.borer",
                "Crust Steam Borer"));
        new MTELegacyConverter(
            MetaTileEntityID.CRUST_STEAM_BORER.OLD_ID,
            "gtsr.legacy.converter.crust_steam_borer",
            "[OLD] Crust Steam Borer",
            MetaTileEntityID.CRUST_STEAM_BORER.ID);
        GTSRItemList.SingularityCrustSteamBorer.set(
            new MTEVoidCrustSteamBorer(
                MetaTileEntityID.SINGULARITY_CRUST_STEAM_BORER.ID,
                "gtsr.singularity.crust.steam.borer",
                "Singularity Crust Steam Borer"));
        new MTEVoidCrustSteamBorerOLD(
            MetaTileEntityID.SINGULARITY_CRUST_STEAM_BORER.OLD_ID,
            "gtsr.singularity.crust.steam.borer.old",
            "Singularity Crust Steam Borer");
        GTSRItemList.VeinSteamPyrolyzer.set(
            new MTEVeinSteamPyrolyzer(
                MetaTileEntityID.VEIN_STEAM_PYROLYZER.ID,
                "gtsr.vein.steam.pyrolyzer",
                "Vein Steam Pyrolyzer"));
        new MTELegacyConverter(
            MetaTileEntityID.VEIN_STEAM_PYROLYZER.OLD_ID,
            "gtsr.legacy.converter.vein_steam_pyrolyzer",
            "[OLD] Vein Steam Pyrolyzer",
            MetaTileEntityID.VEIN_STEAM_PYROLYZER.ID);
        GTSRItemList.LargeSteamFurnace.set(
            new MTELargeSteamFurnace(
                MetaTileEntityID.LARGE_STEAM_FURNACE.ID,
                "gtsr.large.steam.furnace",
                "Large Steam Furnace"));
        new MTELegacyConverter(
            MetaTileEntityID.LARGE_STEAM_FURNACE.OLD_ID,
            "gtsr.legacy.converter.large_steam_furnace",
            "[OLD] Large Steam Furnace",
            MetaTileEntityID.LARGE_STEAM_FURNACE.ID);
        GTSRItemList.AirCompressor
            .set(new MTEAirCompressor(MetaTileEntityID.AIR_COMPRESSOR.ID, "gtsr.air_compressor", "Air Compressor"));
        new MTELegacyConverter(
            MetaTileEntityID.AIR_COMPRESSOR.OLD_ID,
            "gtsr.legacy.converter.air_compressor",
            "[OLD] Air Compressor",
            MetaTileEntityID.AIR_COMPRESSOR.ID);
        GTSRItemList.AtmosphericCentrifuge.set(
            new MTEAtmosphericCentrifuge(
                MetaTileEntityID.ATMOSPHERIC_CENTRIFUGE.ID,
                "gtsr.atmospheric_centrifuge",
                "Atmospheric Centrifuge"));
        new MTELegacyConverter(
            MetaTileEntityID.ATMOSPHERIC_CENTRIFUGE.OLD_ID,
            "gtsr.legacy.converter.atmospheric_centrifuge",
            "[OLD] Atmospheric Centrifuge",
            MetaTileEntityID.ATMOSPHERIC_CENTRIFUGE.ID);
        GTSRItemList.KineticProcessingArray.set(
            new MTEKineticProcessingArray(
                MetaTileEntityID.KINETIC_PROCESSING_ARRAY.ID,
                "gtsr.kinetic.processing.array",
                "Kinetic Processing Array"));
        new MTELegacyConverter(
            MetaTileEntityID.KINETIC_PROCESSING_ARRAY.OLD_ID,
            "gtsr.legacy.converter.kinetic_processing_array",
            "[OLD] Kinetic Processing Array",
            MetaTileEntityID.KINETIC_PROCESSING_ARRAY.ID);

        // --- 多方块机器: 特殊机器 ---
        GTSRItemList.SingularityDrillingHub.set(
            new MTESingularityDrillingHub(
                MetaTileEntityID.SINGULARITY_DRILLING_HUB.ID,
                "gtsr.singularity.drilling.hub",
                "Singularity Drilling Hub"));
        new MTELegacyConverter(
            MetaTileEntityID.SINGULARITY_DRILLING_HUB.OLD_ID,
            "gtsr.legacy.converter.singularity_drilling_hub",
            "[OLD] Singularity Drilling Hub",
            MetaTileEntityID.SINGULARITY_DRILLING_HUB.ID);

        // --- 多方块机器: 非典型蒸汽机 ---
        GTSRItemList.LargeCokeOven
            .set(new MTELargeCokeOven(MetaTileEntityID.LARGE_COKE_OVEN.ID, "gtsr.large.coke.oven", "Large Coke Oven"));
        new MTELegacyConverter(
            MetaTileEntityID.LARGE_COKE_OVEN.OLD_ID,
            "gtsr.legacy.converter.large_coke_oven",
            "[OLD] Large Coke Oven",
            MetaTileEntityID.LARGE_COKE_OVEN.ID);
        GTSRItemList.SiemensMartinFurnace.set(
            new MTESiemensMartinFurnace(
                MetaTileEntityID.SIEMENS_MARTIN_FURNACE.ID,
                "gtsr.siemens.martin.furnace",
                "Siemens-Martin Furnace"));
        new MTELegacyConverter(
            MetaTileEntityID.SIEMENS_MARTIN_FURNACE.OLD_ID,
            "gtsr.legacy.converter.siemens_martin_furnace",
            "[OLD] Siemens-Martin Furnace",
            MetaTileEntityID.SIEMENS_MARTIN_FURNACE.ID);
        GTSRItemList.ReinforcedBrickBlastFurnace.set(
            new MTEReinforcedBrickBlastFurnace(
                MetaTileEntityID.REINFORCED_BRICK_BLAST_FURNACE.ID,
                "gtsr.reinforced.brick.blast.furnace",
                "Reinforced Brick Blast Furnace"));
        new MTELegacyConverter(
            MetaTileEntityID.REINFORCED_BRICK_BLAST_FURNACE.OLD_ID,
            "gtsr.legacy.converter.reinforced_brick_blast_furnace",
            "[OLD] Reinforced Brick Blast Furnace",
            MetaTileEntityID.REINFORCED_BRICK_BLAST_FURNACE.ID);
        GTSRItemList.AmmoniaPlant
            .set(new MTEAmmoniaPlant(MetaTileEntityID.AMMONIA_PLANT.ID, "gtsr.ammonia.plant", "Ammonia Plant"));
        new MTELegacyConverter(
            MetaTileEntityID.AMMONIA_PLANT.OLD_ID,
            "gtsr.legacy.converter.ammonia_plant",
            "[OLD] Ammonia Plant",
            MetaTileEntityID.AMMONIA_PLANT.ID);
        GTSRItemList.GearSteamCompressor.set(
            new MTEGearSteamCompressor(
                MetaTileEntityID.GEAR_STEAM_COMPRESSOR.ID,
                "gtsr.gear.steam.compressor",
                "Gear Steam Compressor"));
        new MTELegacyConverter(
            MetaTileEntityID.GEAR_STEAM_COMPRESSOR.OLD_ID,
            "gtsr.legacy.converter.gear_steam_compressor",
            "[OLD] Gear Steam Compressor",
            MetaTileEntityID.GEAR_STEAM_COMPRESSOR.ID);

        // --- 仓室 ---
        GTSRItemList.SteamInputHatchGeneric.set(
            new MTESteamInputHatchGeneric(
                MetaTileEntityID.STEAM_INPUT_HATCH_GENERIC.ID,
                "gtsr.steam.input.hatch",
                "Steam Input Hatch"));
        new MTELegacyConverter(
            MetaTileEntityID.STEAM_INPUT_HATCH_GENERIC.OLD_ID,
            "gtsr.legacy.converter.steam_input_hatch_generic",
            "[OLD] Steam Input Hatch",
            MetaTileEntityID.STEAM_INPUT_HATCH_GENERIC.ID);
        GTSRItemList.SteamOutputHatchGeneric.set(
            new MTESteamOutputHatchGeneric(
                MetaTileEntityID.STEAM_OUTPUT_HATCH_GENERIC.ID,
                "gtsr.steam.output.hatch.generic",
                "Output Hatch (Steam)"));
        new MTELegacyConverter(
            MetaTileEntityID.STEAM_OUTPUT_HATCH_GENERIC.OLD_ID,
            "gtsr.legacy.converter.steam_output_hatch_generic",
            "[OLD] Output Hatch (Steam)",
            MetaTileEntityID.STEAM_OUTPUT_HATCH_GENERIC.ID);
        GTSRItemList.PressureSteamHatch.set(
            new MTEHatchPressureSteamInput(
                MetaTileEntityID.PRESSURE_STEAM_HATCH.ID,
                "gtsr.pressure.steam.hatch",
                "Pressure Steam Hatch",
                0));
        new MTELegacyConverter(
            MetaTileEntityID.PRESSURE_STEAM_HATCH.OLD_ID,
            "gtsr.legacy.converter.pressure_steam_hatch",
            "[OLD] Pressure Steam Hatch",
            MetaTileEntityID.PRESSURE_STEAM_HATCH.ID);
        GTSRItemList.PressureSteamOutputHatch.set(
            new MTEPressureSteamOutputHatch(
                MetaTileEntityID.PRESSURE_STEAM_OUTPUT_HATCH.ID,
                "gtsr.pressure.steam.output.hatch",
                "Pressure Steam Output Hatch"));
        new MTELegacyConverter(
            MetaTileEntityID.PRESSURE_STEAM_OUTPUT_HATCH.OLD_ID,
            "gtsr.legacy.converter.pressure_steam_output_hatch",
            "[OLD] Pressure Steam Output Hatch",
            MetaTileEntityID.PRESSURE_STEAM_OUTPUT_HATCH.ID);
        GTSRItemList.SteamOutputHatch.set(
            new MTESteamOutputHatch(
                MetaTileEntityID.STEAM_OUTPUT_HATCH.ID,
                "gtsr.steam.output.hatch",
                "Steam Output Hatch"));
        new MTELegacyConverter(
            MetaTileEntityID.STEAM_OUTPUT_HATCH.OLD_ID,
            "gtsr.legacy.converter.steam_output_hatch",
            "[OLD] Steam Output Hatch",
            MetaTileEntityID.STEAM_OUTPUT_HATCH.ID);
        GTSRItemList.SteamCoolingHatch.set(
            new MTESteamCoolingHatch(
                MetaTileEntityID.STEAM_COOLING_HATCH.ID,
                "gtsr.steam.cooling.hatch",
                "Steam Cooling Hatch"));
        new MTELegacyConverter(
            MetaTileEntityID.STEAM_COOLING_HATCH.OLD_ID,
            "gtsr.legacy.converter.steam_cooling_hatch",
            "[OLD] Steam Cooling Hatch",
            MetaTileEntityID.STEAM_COOLING_HATCH.ID);
        GTSRItemList.PressureSteamCoolingHatch.set(
            new MTEPressureSteamCoolingHatch(
                MetaTileEntityID.PRESSURE_STEAM_COOLING_HATCH.ID,
                "gtsr.pressure.steam.cooling.hatch",
                "Pressure Steam Cooling Hatch"));
        new MTELegacyConverter(
            MetaTileEntityID.PRESSURE_STEAM_COOLING_HATCH.OLD_ID,
            "gtsr.legacy.converter.pressure_steam_cooling_hatch",
            "[OLD] Pressure Steam Cooling Hatch",
            MetaTileEntityID.PRESSURE_STEAM_COOLING_HATCH.ID);
        // 巨型空气输入仓：仿照 GT5U 液态空气仓，仅允许空气/下界空气
        GTSRItemList.MegaAirInputHatch.set(
            new MTEMegaAirInputHatch(
                MetaTileEntityID.MEGA_AIR_INPUT_HATCH.ID,
                "gtsr.mega.air.input.hatch",
                "Mega Air Input Hatch"));
        new MTELegacyConverter(
            MetaTileEntityID.MEGA_AIR_INPUT_HATCH.OLD_ID,
            "gtsr.legacy.converter.mega_air_input_hatch",
            "[OLD] Mega Air Input Hatch",
            MetaTileEntityID.MEGA_AIR_INPUT_HATCH.ID);
        // 蒸馏水仓：仿照 GT5U 蓄水仓（Reservoir Hatch），生成蒸馏水
        GTSRItemList.DistilledWaterHatch.set(
            new MTEDistilledWaterHatch(
                MetaTileEntityID.DISTILLED_WATER_HATCH.ID,
                "gtsr.distilled.water.hatch",
                "Distilled Water Hatch"));
        new MTELegacyConverter(
            MetaTileEntityID.DISTILLED_WATER_HATCH.OLD_ID,
            "gtsr.legacy.converter.distilled_water_hatch",
            "[OLD] Distilled Water Hatch",
            MetaTileEntityID.DISTILLED_WATER_HATCH.ID);
    }

    /**
     * Add items to creative tab in the same order as MetaTileEntityID enum.
     */
    private static void addItemsToCreativeTab() {
        // --- 单方块机器 (1-5) ---
        CreativeTabManager.addItemToTab(GTSRItemList.SteamCacheNode.get(1));
        CreativeTabManager.addItemToTab(GTSRItemList.ReinforcedSteamCacheNode.get(1));
        CreativeTabManager.addItemToTab(GTSRItemList.OverpressureSteamCacheNode.get(1));
        CreativeTabManager.addItemToTab(GTSRItemList.WaterCacheNode.get(1));
        CreativeTabManager.addItemToTab(GTSRItemList.SteamSingularityCompressor.get(1));
        // --- 多方块机器: 枢纽及仓室模块 (6-14) ---
        CreativeTabManager.addItemToTab(GTSRItemList.SteamHubArray.get(1));
        CreativeTabManager.addItemToTab(GTSRItemList.WaterHubArray.get(1));
        CreativeTabManager.addItemToTab(GTSRItemList.SteamHubInputHatch.get(1));
        CreativeTabManager.addItemToTab(GTSRItemList.SteamHubOutputHatch.get(1));
        CreativeTabManager.addItemToTab(GTSRItemList.WaterHubInputHatch.get(1));
        CreativeTabManager.addItemToTab(GTSRItemList.WaterHubOutputHatch.get(1));
        CreativeTabManager.addItemToTab(GTSRItemList.HubStorageUnit.get(1));
        CreativeTabManager.addItemToTab(GTSRItemList.ReinforcedHubStorageUnit.get(1));
        CreativeTabManager.addItemToTab(GTSRItemList.OverpressureHubStorageUnit.get(1));
        // --- 多方块机器: 蒸汽生产 (15-16) ---
        CreativeTabManager.addItemToTab(GTSRItemList.LargeSolarOverpressureArray.get(1));
        // 蒸馏水仓：创造物品栏排序在大型超压太阳能阵列后面
        CreativeTabManager.addItemToTab(GTSRItemList.DistilledWaterHatch.get(1));
        CreativeTabManager.addItemToTab(GTSRItemList.LargeGeothermalSteamBoiler.get(1));
        // --- 多方块机器: 蒸汽产电及特殊仓室 (17-18) ---
        CreativeTabManager.addItemToTab(GTSRItemList.MegaSteamTurbineArray.get(1));
        CreativeTabManager.addItemToTab(GTSRItemList.OverpressureTurbineInputHatch.get(1));
        // --- 多方块机器: 工作机器 (19-26) ---
        CreativeTabManager.addItemToTab(GTSRItemList.SteamFluidDrill.get(1));
        CreativeTabManager.addItemToTab(GTSRItemList.CrustSteamBorer.get(1));
        CreativeTabManager.addItemToTab(GTSRItemList.SingularityCrustSteamBorer.get(1));
        CreativeTabManager.addItemToTab(GTSRItemList.VeinSteamPyrolyzer.get(1));
        CreativeTabManager.addItemToTab(GTSRItemList.LargeSteamFurnace.get(1));
        CreativeTabManager.addItemToTab(GTSRItemList.AirCompressor.get(1));
        CreativeTabManager.addItemToTab(GTSRItemList.AtmosphericCentrifuge.get(1));
        CreativeTabManager.addItemToTab(GTSRItemList.KineticProcessingArray.get(1));
        // --- 多方块机器: 特殊机器 (27-29) ---
        CreativeTabManager.addItemToTab(GTSRItemList.SingularityDrillingHub.get(1));
        CreativeTabManager.addItemToTab(GTSRItemList.SingularityMinerNode.get(1));
        CreativeTabManager.addItemToTab(GTSRItemList.SingularityDrillingNode.get(1));
        // --- 多方块机器: 非典型蒸汽机 (30-33) ---
        CreativeTabManager.addItemToTab(GTSRItemList.LargeCokeOven.get(1));
        CreativeTabManager.addItemToTab(GTSRItemList.SiemensMartinFurnace.get(1));
        CreativeTabManager.addItemToTab(GTSRItemList.ReinforcedBrickBlastFurnace.get(1));
        CreativeTabManager.addItemToTab(GTSRItemList.AmmoniaPlant.get(1));
        CreativeTabManager.addItemToTab(GTSRItemList.GearSteamCompressor.get(1));
        // --- 仓室 (34-40) ---
        CreativeTabManager.addItemToTab(GTSRItemList.SteamInputHatchGeneric.get(1));
        CreativeTabManager.addItemToTab(GTSRItemList.SteamOutputHatchGeneric.get(1));
        CreativeTabManager.addItemToTab(GTSRItemList.PressureSteamHatch.get(1));
        CreativeTabManager.addItemToTab(GTSRItemList.PressureSteamOutputHatch.get(1));
        CreativeTabManager.addItemToTab(GTSRItemList.SteamOutputHatch.get(1));
        CreativeTabManager.addItemToTab(GTSRItemList.SteamCoolingHatch.get(1));
        CreativeTabManager.addItemToTab(GTSRItemList.PressureSteamCoolingHatch.get(1));
        // --- 仓室 (41) ---
        CreativeTabManager.addItemToTab(GTSRItemList.MegaAirInputHatch.get(1));
        // --- 新增奇点机器 (43-44) ---
        CreativeTabManager.addItemToTab(GTSRItemList.CriticalSingularityCompressor.get(1));
        CreativeTabManager.addItemToTab(GTSRItemList.DenseStateManipulator.get(1));
    }
}
