package com.miaokatze.gtsr.loader;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.api.enums.MetaTileEntityID;
import com.miaokatze.gtsr.common.machine.MTEAirCompressor;
import com.miaokatze.gtsr.common.machine.MTEAmmoniaPlant;
import com.miaokatze.gtsr.common.machine.MTEAtmosphericCentrifuge;
import com.miaokatze.gtsr.common.machine.MTECrustSteamBorer;
import com.miaokatze.gtsr.common.machine.MTEGearSteamCompressor;
import com.miaokatze.gtsr.common.machine.MTEKineticProcessingArray;
import com.miaokatze.gtsr.common.machine.MTELargeCokeOven;
import com.miaokatze.gtsr.common.machine.MTELargeGeothermalSteamBoiler;
import com.miaokatze.gtsr.common.machine.MTELargeSolarOverpressureArray;
import com.miaokatze.gtsr.common.machine.MTELargeSteamFurnace;
import com.miaokatze.gtsr.common.machine.MTEMegaSteamTurbineArray;
import com.miaokatze.gtsr.common.machine.MTESiemensMartinFurnace;
import com.miaokatze.gtsr.common.machine.MTESingularityDrillingHub;
import com.miaokatze.gtsr.common.machine.MTESingularityDrillingNode;
import com.miaokatze.gtsr.common.machine.MTESingularityMinerNode;
import com.miaokatze.gtsr.common.machine.MTESteamFluidDrill;
import com.miaokatze.gtsr.common.machine.MTESteamHubArray;
import com.miaokatze.gtsr.common.machine.MTESteamSingularityCompressor;
import com.miaokatze.gtsr.common.machine.MTEVeinSteamPyrolyzer;
import com.miaokatze.gtsr.common.machine.MTEVoidCrustSteamBorer;
import com.miaokatze.gtsr.common.machine.MTEWaterHubArray;
import com.miaokatze.gtsr.common.machine.base.MTEHatchPressureSteamInput;
import com.miaokatze.gtsr.common.machine.base.MTEHubStorageUnit;
import com.miaokatze.gtsr.common.machine.base.MTEPressureSteamCoolingHatch;
import com.miaokatze.gtsr.common.machine.base.MTEPressureSteamOutputHatch;
import com.miaokatze.gtsr.common.machine.base.MTEReinforcedHubStorageUnit;
import com.miaokatze.gtsr.common.machine.base.MTEReinforcedSteamCacheNode;
import com.miaokatze.gtsr.common.machine.base.MTESteamCacheNode;
import com.miaokatze.gtsr.common.machine.base.MTESteamCoolingHatch;
import com.miaokatze.gtsr.common.machine.base.MTESteamHubInputHatch;
import com.miaokatze.gtsr.common.machine.base.MTESteamHubOutputHatch;
import com.miaokatze.gtsr.common.machine.base.MTESteamInputHatch;
import com.miaokatze.gtsr.common.machine.base.MTESteamOutputHatch;
import com.miaokatze.gtsr.common.machine.base.MTESteamOutputHatchGeneric;
import com.miaokatze.gtsr.common.machine.base.MTEWaterCacheNode;
import com.miaokatze.gtsr.common.machine.base.MTEWaterHubInputHatch;
import com.miaokatze.gtsr.common.machine.base.MTEWaterHubOutputHatch;
import com.miaokatze.gtsr.register.CreativeTabManager;

public class MachineLoader {

    public static void initMachines() {
        registerSingleBlockMachines();
        registerHubArrays();
        registerSteamProductionMachines();
        registerSteamPowerGeneration();
        registerWorkingMachines();
        registerSpecialMachines();
        registerAtypicalSteamMachines();
        registerHatches();
    }

    private static void registerSingleBlockMachines() {
        GTSRItemList.SteamCacheNode.set(
            new MTESteamCacheNode(MetaTileEntityID.STEAM_CACHE_NODE.ID, "gtsr.steam.cache.node", "Steam Cache Node"));
        CreativeTabManager.addItemToTab(GTSRItemList.SteamCacheNode.get(1));

        GTSRItemList.ReinforcedSteamCacheNode.set(
            new MTEReinforcedSteamCacheNode(
                MetaTileEntityID.REINFORCED_STEAM_CACHE_NODE.ID,
                "gtsr.reinforced.steam.cache.node",
                "Reinforced Steam Cache Node"));
        CreativeTabManager.addItemToTab(GTSRItemList.ReinforcedSteamCacheNode.get(1));

        GTSRItemList.WaterCacheNode.set(
            new MTEWaterCacheNode(MetaTileEntityID.WATER_CACHE_NODE.ID, "gtsr.water.cache.node", "Water Cache Node"));
        CreativeTabManager.addItemToTab(GTSRItemList.WaterCacheNode.get(1));

        GTSRItemList.SingularityMinerNode.set(
            new MTESingularityMinerNode(
                MetaTileEntityID.SINGULARITY_MINER_NODE.ID,
                "gtsr.singularity.miner.node",
                "Singularity Miner Node"));
        CreativeTabManager.addItemToTab(GTSRItemList.SingularityMinerNode.get(1));

        GTSRItemList.SingularityDrillingNode.set(
            new MTESingularityDrillingNode(
                MetaTileEntityID.SINGULARITY_DRILLING_NODE.ID,
                "gtsr.singularity.drilling.node",
                "Singularity Drilling Node"));
        CreativeTabManager.addItemToTab(GTSRItemList.SingularityDrillingNode.get(1));

        GTSRItemList.SteamSingularityCompressor.set(
            new MTESteamSingularityCompressor(
                MetaTileEntityID.STEAM_SINGULARITY_COMPRESSOR.ID,
                "gtsr.steam.singularity.compressor",
                "Steam Singularity Compressor"));
        CreativeTabManager.addItemToTab(GTSRItemList.SteamSingularityCompressor.get(1));
    }

    private static void registerHubArrays() {
        GTSRItemList.SteamHubArray
            .set(new MTESteamHubArray(MetaTileEntityID.STEAM_HUB_ARRAY.ID, "gtsr.steam.hub.array", "Steam Hub Array"));
        CreativeTabManager.addItemToTab(GTSRItemList.SteamHubArray.get(1));

        GTSRItemList.WaterHubArray
            .set(new MTEWaterHubArray(MetaTileEntityID.WATER_HUB_ARRAY.ID, "gtsr.water.hub.array", "Water Hub Array"));
        CreativeTabManager.addItemToTab(GTSRItemList.WaterHubArray.get(1));
    }

    private static void registerSteamProductionMachines() {
        GTSRItemList.LargeSolarOverpressureArray.set(
            new MTELargeSolarOverpressureArray(
                MetaTileEntityID.LARGE_SOLAR_OVERPRESSURE_ARRAY.ID,
                "gtsr.large.solar.overpressure.array",
                "Large Solar Overpressure Array"));
        CreativeTabManager.addItemToTab(GTSRItemList.LargeSolarOverpressureArray.get(1));

        GTSRItemList.LargeGeothermalSteamBoiler.set(
            new MTELargeGeothermalSteamBoiler(
                MetaTileEntityID.LARGE_GEOTHERMAL_STEAM_BOILER.ID,
                "gtsr.large.geothermal.steam.boiler",
                "Large Geothermal Steam Boiler"));
        CreativeTabManager.addItemToTab(GTSRItemList.LargeGeothermalSteamBoiler.get(1));

        GTSRItemList.GearSteamCompressor.set(
            new MTEGearSteamCompressor(
                MetaTileEntityID.GEAR_STEAM_COMPRESSOR.ID,
                "gtsr.gear.steam.compressor",
                "Gear Steam Compressor"));
        CreativeTabManager.addItemToTab(GTSRItemList.GearSteamCompressor.get(1));
    }

    private static void registerSteamPowerGeneration() {
        GTSRItemList.MegaSteamTurbineArray.set(
            new MTEMegaSteamTurbineArray(
                MetaTileEntityID.MEGA_STEAM_TURBINE_ARRAY.ID,
                "gtsr.mega.steam.turbine.array",
                "Mega Steam Turbine Array"));
        CreativeTabManager.addItemToTab(GTSRItemList.MegaSteamTurbineArray.get(1));
    }

    private static void registerWorkingMachines() {
        GTSRItemList.SteamFluidDrill.set(
            new MTESteamFluidDrill(
                MetaTileEntityID.STEAM_FLUID_DRILL.ID,
                "gtsr.steam.fluid.drill",
                "Steam Fluid Drill"));
        CreativeTabManager.addItemToTab(GTSRItemList.SteamFluidDrill.get(1));

        GTSRItemList.CrustSteamBorer.set(
            new MTECrustSteamBorer(
                MetaTileEntityID.CRUST_STEAM_BORER.ID,
                "gtsr.crust.steam.borer",
                "Crust Steam Borer"));
        CreativeTabManager.addItemToTab(GTSRItemList.CrustSteamBorer.get(1));

        GTSRItemList.SingularityCrustSteamBorer.set(
            new MTEVoidCrustSteamBorer(
                MetaTileEntityID.SINGULARITY_CRUST_STEAM_BORER.ID,
                "gtsr.singularity.crust.steam.borer",
                "Singularity Crust Steam Borer"));
        CreativeTabManager.addItemToTab(GTSRItemList.SingularityCrustSteamBorer.get(1));

        GTSRItemList.VeinSteamPyrolyzer.set(
            new MTEVeinSteamPyrolyzer(
                MetaTileEntityID.VEIN_STEAM_PYROLYZER.ID,
                "gtsr.vein.steam.pyrolyzer",
                "Vein Steam Pyrolyzer"));
        CreativeTabManager.addItemToTab(GTSRItemList.VeinSteamPyrolyzer.get(1));

        GTSRItemList.LargeSteamFurnace.set(
            new MTELargeSteamFurnace(
                MetaTileEntityID.LARGE_STEAM_FURNACE.ID,
                "gtsr.large.steam.furnace",
                "Large Steam Furnace"));
        CreativeTabManager.addItemToTab(GTSRItemList.LargeSteamFurnace.get(1));

        GTSRItemList.AirCompressor
            .set(new MTEAirCompressor(MetaTileEntityID.AIR_COMPRESSOR.ID, "gtsr.air.compressor", "Air Compressor"));
        CreativeTabManager.addItemToTab(GTSRItemList.AirCompressor.get(1));

        GTSRItemList.AtmosphericCentrifuge.set(
            new MTEAtmosphericCentrifuge(
                MetaTileEntityID.ATMOSPHERIC_CENTRIFUGE.ID,
                "gtsr.atmospheric.centrifuge",
                "Atmospheric Centrifuge"));
        CreativeTabManager.addItemToTab(GTSRItemList.AtmosphericCentrifuge.get(1));

        GTSRItemList.KineticProcessingArray.set(
            new MTEKineticProcessingArray(
                MetaTileEntityID.KINETIC_PROCESSING_ARRAY.ID,
                "gtsr.kinetic.processing.array",
                "Kinetic Processing Array"));
        CreativeTabManager.addItemToTab(GTSRItemList.KineticProcessingArray.get(1));
    }

    private static void registerSpecialMachines() {
        GTSRItemList.SingularityDrillingHub.set(
            new MTESingularityDrillingHub(
                MetaTileEntityID.SINGULARITY_DRILLING_HUB.ID,
                "gtsr.singularity.drilling.hub",
                "Singularity Drilling Hub"));
        CreativeTabManager.addItemToTab(GTSRItemList.SingularityDrillingHub.get(1));
    }

    private static void registerAtypicalSteamMachines() {
        GTSRItemList.LargeCokeOven
            .set(new MTELargeCokeOven(MetaTileEntityID.LARGE_COKE_OVEN.ID, "gtsr.large.coke.oven", "Large Coke Oven"));
        CreativeTabManager.addItemToTab(GTSRItemList.LargeCokeOven.get(1));

        GTSRItemList.SiemensMartinFurnace.set(
            new MTESiemensMartinFurnace(
                MetaTileEntityID.SIEMENS_MARTIN_FURNACE.ID,
                "gtsr.siemens.martin.furnace",
                "Siemens-Martin Furnace"));
        CreativeTabManager.addItemToTab(GTSRItemList.SiemensMartinFurnace.get(1));

        GTSRItemList.AmmoniaPlant
            .set(new MTEAmmoniaPlant(MetaTileEntityID.AMMONIA_PLANT.ID, "gtsr.ammonia.plant", "Ammonia Plant"));
        CreativeTabManager.addItemToTab(GTSRItemList.AmmoniaPlant.get(1));
    }

    private static void registerHatches() {
        GTSRItemList.SteamInputHatch.set(
            new MTESteamInputHatch(
                MetaTileEntityID.STEAM_INPUT_HATCH.ID,
                "gtsr.steam.input.hatch",
                "Steam Input Hatch"));
        CreativeTabManager.addItemToTab(GTSRItemList.SteamInputHatch.get(1));

        GTSRItemList.SteamOutputHatch.set(
            new MTESteamOutputHatch(
                MetaTileEntityID.STEAM_OUTPUT_HATCH.ID,
                "gtsr.steam.output.hatch",
                "Steam Output Hatch"));
        CreativeTabManager.addItemToTab(GTSRItemList.SteamOutputHatch.get(1));

        GTSRItemList.SteamOutputHatchGeneric.set(
            new MTESteamOutputHatchGeneric(
                MetaTileEntityID.STEAM_OUTPUT_HATCH_GENERIC.ID,
                "gtsr.steam.output.hatch.generic",
                "Output Hatch (Steam)"));
        CreativeTabManager.addItemToTab(GTSRItemList.SteamOutputHatchGeneric.get(1));

        GTSRItemList.PressureSteamHatch.set(
            new MTEHatchPressureSteamInput(
                MetaTileEntityID.PRESSURE_STEAM_HATCH.ID,
                "gtsr.pressure.steam.hatch",
                "Pressure Steam Hatch",
                0));
        CreativeTabManager.addItemToTab(GTSRItemList.PressureSteamHatch.get(1));

        GTSRItemList.PressureSteamOutputHatch.set(
            new MTEPressureSteamOutputHatch(
                MetaTileEntityID.PRESSURE_STEAM_OUTPUT_HATCH.ID,
                "gtsr.pressure.steam.output.hatch",
                "Pressure Steam Output Hatch"));
        CreativeTabManager.addItemToTab(GTSRItemList.PressureSteamOutputHatch.get(1));

        GTSRItemList.SteamCoolingHatch.set(
            new MTESteamCoolingHatch(
                MetaTileEntityID.STEAM_COOLING_HATCH.ID,
                "gtsr.steam.cooling.hatch",
                "Steam Cooling Hatch"));
        CreativeTabManager.addItemToTab(GTSRItemList.SteamCoolingHatch.get(1));

        GTSRItemList.PressureSteamCoolingHatch.set(
            new MTEPressureSteamCoolingHatch(
                MetaTileEntityID.PRESSURE_STEAM_COOLING_HATCH.ID,
                "gtsr.pressure.steam.cooling.hatch",
                "Pressure Steam Cooling Hatch"));
        CreativeTabManager.addItemToTab(GTSRItemList.PressureSteamCoolingHatch.get(1));

        GTSRItemList.SteamHubInputHatch.set(
            new MTESteamHubInputHatch(
                MetaTileEntityID.STEAM_HUB_INPUT_HATCH.ID,
                "gtsr.steam.hub.input.hatch",
                "Steam Hub Input Hatch"));
        CreativeTabManager.addItemToTab(GTSRItemList.SteamHubInputHatch.get(1));

        GTSRItemList.SteamHubOutputHatch.set(
            new MTESteamHubOutputHatch(
                MetaTileEntityID.STEAM_HUB_OUTPUT_HATCH.ID,
                "gtsr.steam.hub.output.hatch",
                "Steam Hub Output Hatch"));
        CreativeTabManager.addItemToTab(GTSRItemList.SteamHubOutputHatch.get(1));

        GTSRItemList.WaterHubInputHatch.set(
            new MTEWaterHubInputHatch(
                MetaTileEntityID.WATER_HUB_INPUT_HATCH.ID,
                "gtsr.water.hub.input.hatch",
                "Water Hub Input Hatch"));
        CreativeTabManager.addItemToTab(GTSRItemList.WaterHubInputHatch.get(1));

        GTSRItemList.WaterHubOutputHatch.set(
            new MTEWaterHubOutputHatch(
                MetaTileEntityID.WATER_HUB_OUTPUT_HATCH.ID,
                "gtsr.water.hub.output.hatch",
                "Water Hub Output Hatch"));
        CreativeTabManager.addItemToTab(GTSRItemList.WaterHubOutputHatch.get(1));

        GTSRItemList.HubStorageUnit.set(
            new MTEHubStorageUnit(MetaTileEntityID.HUB_STORAGE_UNIT.ID, "gtsr.hub.storage.unit", "Hub Storage Unit"));
        CreativeTabManager.addItemToTab(GTSRItemList.HubStorageUnit.get(1));

        GTSRItemList.ReinforcedHubStorageUnit.set(
            new MTEReinforcedHubStorageUnit(
                MetaTileEntityID.REINFORCED_HUB_STORAGE_UNIT.ID,
                "gtsr.reinforced.hub.storage.unit",
                "Reinforced Hub Storage Unit"));
        CreativeTabManager.addItemToTab(GTSRItemList.ReinforcedHubStorageUnit.get(1));
    }
}
