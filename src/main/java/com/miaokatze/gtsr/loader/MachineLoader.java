package com.miaokatze.gtsr.loader;

import com.miaokatze.gtsr.common.api.enums.GTSRHatchElement;
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.api.enums.MetaTileEntityID;
import com.miaokatze.gtsr.common.machine.MTEAirCompressor;
import com.miaokatze.gtsr.common.machine.MTEAmmoniaPlant;
import com.miaokatze.gtsr.common.machine.MTEAtmosphericCentrifuge;
import com.miaokatze.gtsr.common.machine.MTECriticalSingularityCompressor;
import com.miaokatze.gtsr.common.machine.MTECrustMatterAggregator;
import com.miaokatze.gtsr.common.machine.MTECrustSteamBorer;
import com.miaokatze.gtsr.common.machine.MTEDenseStateManipulator;
import com.miaokatze.gtsr.common.machine.MTEGearSteamCompressor;
import com.miaokatze.gtsr.common.machine.MTEKineticProcessingArray;
import com.miaokatze.gtsr.common.machine.MTELargeCokeOven;
import com.miaokatze.gtsr.common.machine.MTELargeGeothermalSteamBoiler;
import com.miaokatze.gtsr.common.machine.MTELargeSolarOverpressureArray;
import com.miaokatze.gtsr.common.machine.MTELargeSteamFurnace;
import com.miaokatze.gtsr.common.machine.MTEMegaSteamTurbineArray;
import com.miaokatze.gtsr.common.machine.MTEReinforcedBrickBlastFurnace;
import com.miaokatze.gtsr.common.machine.MTESiemensMartinFurnace;
import com.miaokatze.gtsr.common.machine.MTESingularityDrillingHub;
import com.miaokatze.gtsr.common.machine.MTESingularityDrillingNode;
import com.miaokatze.gtsr.common.machine.MTESingularityFluidInputCompartment;
import com.miaokatze.gtsr.common.machine.MTESingularityFluidOutputCompartment;
import com.miaokatze.gtsr.common.machine.MTESingularityMinerNode;
import com.miaokatze.gtsr.common.machine.MTESingularitySteamCompartment;
import com.miaokatze.gtsr.common.machine.MTESingularitySteamOutputCompartment;
import com.miaokatze.gtsr.common.machine.MTESteamFluidDrill;
import com.miaokatze.gtsr.common.machine.MTESteamHubArray;
import com.miaokatze.gtsr.common.machine.MTESteamSingularityEntangler;
import com.miaokatze.gtsr.common.machine.MTEVeinSteamPyrolyzer;
import com.miaokatze.gtsr.common.machine.MTEWaterHubArray;
import com.miaokatze.gtsr.common.machine.base.MTEDistilledWaterHatch;
import com.miaokatze.gtsr.common.machine.base.MTEGTSRRedstoneHatch;
import com.miaokatze.gtsr.common.machine.base.MTEHatchPressureSteamInput;
import com.miaokatze.gtsr.common.machine.base.MTEHubStorageUnit;
import com.miaokatze.gtsr.common.machine.base.MTEMegaAirInputHatch;
import com.miaokatze.gtsr.common.machine.base.MTEOverpressureHubStorageUnit;
import com.miaokatze.gtsr.common.machine.base.MTEOverpressureSteamCacheNode;
import com.miaokatze.gtsr.common.machine.base.MTEOverpressureTurbineInputHatch;
import com.miaokatze.gtsr.common.machine.base.MTEOverpressureWaterCacheNode;
import com.miaokatze.gtsr.common.machine.base.MTEPressureSteamCoolingHatch;
import com.miaokatze.gtsr.common.machine.base.MTEPressureSteamOutputHatch;
import com.miaokatze.gtsr.common.machine.base.MTEReinforcedHubStorageUnit;
import com.miaokatze.gtsr.common.machine.base.MTEReinforcedSteamCacheNode;
import com.miaokatze.gtsr.common.machine.base.MTEReinforcedWaterCacheNode;
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
import com.miaokatze.gtsr.common.machine.cluster.MTEBasicLogisticsUnit;
import com.miaokatze.gtsr.common.machine.cluster.MTESteamMineralLogisticsCluster;
import com.miaokatze.gtsr.common.machine.cluster.MTEUnitCentrifuge;
import com.miaokatze.gtsr.common.machine.cluster.MTEUnitCrusher;
import com.miaokatze.gtsr.common.machine.cluster.MTEUnitFurnace;
import com.miaokatze.gtsr.common.machine.cluster.MTEUnitMagneticSeparator;
import com.miaokatze.gtsr.common.machine.cluster.MTEUnitOreWasher;
import com.miaokatze.gtsr.common.machine.cluster.MTEUnitParallelBooster;
import com.miaokatze.gtsr.common.machine.cluster.MTEUnitPrimaryBooster;
import com.miaokatze.gtsr.common.machine.cluster.MTEUnitSecondaryBooster;
import com.miaokatze.gtsr.common.machine.cluster.MTEUnitSifter;
import com.miaokatze.gtsr.common.machine.cluster.MTEUnitSpeedBooster;
import com.miaokatze.gtsr.common.machine.cluster.MTEUnitSteamSaverBooster;
import com.miaokatze.gtsr.common.machine.cluster.MTEUnitThermalCentrifuge;
import com.miaokatze.gtsr.register.CreativeTabManager;

public class MachineLoader {

    public static void initMachines() {
        // O2-B03①：先灌入 api 层 GTSRHatchElement 的 machine 具体仓类（晚绑定注册表），
        // 须早于任何机器注册/结构检测/NEI 仓类查询
        GTSRHatchElement.registerMachineHatchClasses(
            MTESteamCoolingHatch.class,
            MTEPressureSteamCoolingHatch.class,
            MTEHatchPressureSteamInput.class);
        registerAllMachines();
        addItemsToCreativeTab();
    }

    private static void registerAllMachines() {
        // --- 单方块机器 ---
        GTSRItemList.SteamCacheNode.set(
            new MTESteamCacheNode(MetaTileEntityID.STEAM_CACHE_NODE.ID, "gtsr.steam.cache.node", "Steam Cache Node"));
        GTSRItemList.ReinforcedSteamCacheNode.set(
            new MTEReinforcedSteamCacheNode(
                MetaTileEntityID.REINFORCED_STEAM_CACHE_NODE.ID,
                "gtsr.reinforced.steam.cache.node",
                "Reinforced Steam Cache Node"));
        GTSRItemList.OverpressureHubStorageUnit.set(
            new MTEOverpressureHubStorageUnit(
                MetaTileEntityID.OVERPRESSURE_HUB_STORAGE_UNIT.ID,
                "gtsr.overpressure.hub.storage.unit",
                "Overpressure Hub Storage Unit"));
        GTSRItemList.WaterCacheNode.set(
            new MTEWaterCacheNode(
                MetaTileEntityID.WATER_CACHE_NODE.ID,
                "gtsr.water.cache.node",
                "Universal Fluid Cache Node"));
        // 耐压/超压通用流体缓存节点：全新 ID，无旧存档机器（同红石仓模式）
        GTSRItemList.ReinforcedWaterCacheNode.set(
            new MTEReinforcedWaterCacheNode(
                MetaTileEntityID.REINFORCED_WATER_CACHE_NODE.ID,
                "gtsr.reinforced.water.cache.node",
                "Reinforced Universal Fluid Cache Node"));
        GTSRItemList.OverpressureWaterCacheNode.set(
            new MTEOverpressureWaterCacheNode(
                MetaTileEntityID.OVERPRESSURE_WATER_CACHE_NODE.ID,
                "gtsr.overpressure.water.cache.node",
                "Overpressure Universal Fluid Cache Node"));
        // 奇点仓四件套（模式锁定）：蒸汽两仓绑蒸汽枢纽阵列、流体两仓绑蓄水枢纽阵列；
        // 全新 ID（单方块段 8-11），无旧存档机器
        GTSRItemList.SingularitySteamCompartment.set(
            new MTESingularitySteamCompartment(
                MetaTileEntityID.SINGULARITY_STEAM_COMPARTMENT.ID,
                "gtsr.singularity.steam.compartment",
                "Singularity Steam Compartment"));
        GTSRItemList.SingularitySteamOutputCompartment.set(
            new MTESingularitySteamOutputCompartment(
                MetaTileEntityID.SINGULARITY_STEAM_OUTPUT_COMPARTMENT.ID,
                "gtsr.singularity.steam.output.compartment",
                "Singularity Steam Output Compartment"));
        GTSRItemList.SingularityFluidInputCompartment.set(
            new MTESingularityFluidInputCompartment(
                MetaTileEntityID.SINGULARITY_FLUID_INPUT_COMPARTMENT.ID,
                "gtsr.singularity.fluid.input.compartment",
                "Singularity Fluid Input Compartment"));
        GTSRItemList.SingularityFluidOutputCompartment.set(
            new MTESingularityFluidOutputCompartment(
                MetaTileEntityID.SINGULARITY_FLUID_OUTPUT_COMPARTMENT.ID,
                "gtsr.singularity.fluid.output.compartment",
                "Singularity Fluid Output Compartment"));
        GTSRItemList.SingularityMinerNode.set(
            new MTESingularityMinerNode(
                MetaTileEntityID.SINGULARITY_MINER_NODE.ID,
                "gtsr.singularity.miner.node",
                "Singularity Miner Node"));
        GTSRItemList.SingularityDrillingNode.set(
            new MTESingularityDrillingNode(
                MetaTileEntityID.SINGULARITY_DRILLING_NODE.ID,
                "gtsr.singularity.drilling.node",
                "Singularity Drilling Node"));
        GTSRItemList.SteamSingularityEntangler.set(
            new MTESteamSingularityEntangler(
                MetaTileEntityID.STEAM_SINGULARITY_ENTANGLER.ID,
                "gtsr.steam.singularity.entangler",
                "Steam Singularity Entangler"));
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
        GTSRItemList.WaterHubArray
            .set(new MTEWaterHubArray(MetaTileEntityID.WATER_HUB_ARRAY.ID, "gtsr.water.hub.array", "Water Hub Array"));
        GTSRItemList.SteamHubInputHatch.set(
            new MTESteamHubInputHatch(
                MetaTileEntityID.STEAM_HUB_INPUT_HATCH.ID,
                "gtsr.steam.hub.input.hatch",
                "Steam Hub Input Hatch"));
        GTSRItemList.SteamHubOutputHatch.set(
            new MTESteamHubOutputHatch(
                MetaTileEntityID.STEAM_HUB_OUTPUT_HATCH.ID,
                "gtsr.steam.hub.output.hatch",
                "Steam Hub Output Hatch"));
        GTSRItemList.WaterHubInputHatch.set(
            new MTEWaterHubInputHatch(
                MetaTileEntityID.WATER_HUB_INPUT_HATCH.ID,
                "gtsr.water.hub.input.hatch",
                "Water Hub Input Hatch"));
        GTSRItemList.WaterHubOutputHatch.set(
            new MTEWaterHubOutputHatch(
                MetaTileEntityID.WATER_HUB_OUTPUT_HATCH.ID,
                "gtsr.water.hub.output.hatch",
                "Water Hub Output Hatch"));
        GTSRItemList.OverpressureTurbineInputHatch.set(
            new MTEOverpressureTurbineInputHatch(
                MetaTileEntityID.OVERPRESSURE_TURBINE_INPUT_HATCH.ID,
                "gtsr.overpressure.turbine.input.hatch",
                "Mega Overpressure Steam Input Hatch"));
        GTSRItemList.HubStorageUnit.set(
            new MTEHubStorageUnit(MetaTileEntityID.HUB_STORAGE_UNIT.ID, "gtsr.hub.storage.unit", "Hub Storage Unit"));
        GTSRItemList.ReinforcedHubStorageUnit.set(
            new MTEReinforcedHubStorageUnit(
                MetaTileEntityID.REINFORCED_HUB_STORAGE_UNIT.ID,
                "gtsr.reinforced.hub.storage.unit",
                "Reinforced Hub Storage Unit"));
        GTSRItemList.OverpressureSteamCacheNode.set(
            new MTEOverpressureSteamCacheNode(
                MetaTileEntityID.OVERPRESSURE_STEAM_CACHE_NODE.ID,
                "gtsr.overpressure.steam.cache.node",
                "Overpressure Steam Cache Node"));

        // --- 多方块机器: 蒸汽生产 ---
        GTSRItemList.LargeSolarOverpressureArray.set(
            new MTELargeSolarOverpressureArray(
                MetaTileEntityID.LARGE_SOLAR_OVERPRESSURE_ARRAY.ID,
                "gtsr.large.solar.overpressure.array",
                "Large Solar Overpressure Array"));
        GTSRItemList.LargeGeothermalSteamBoiler.set(
            new MTELargeGeothermalSteamBoiler(
                MetaTileEntityID.LARGE_GEOTHERMAL_STEAM_BOILER.ID,
                "gtsr.large.geothermal.steam.boiler",
                "Large Geothermal Steam Boiler"));

        // --- 多方块机器: 蒸汽产电 ---
        GTSRItemList.MegaSteamTurbineArray.set(
            new MTEMegaSteamTurbineArray(
                MetaTileEntityID.MEGA_STEAM_TURBINE_ARRAY.ID,
                "gtsr.mega.steam.turbine.array",
                "Mega Steam Turbine Array"));

        // --- 多方块机器: 工作机器 ---
        GTSRItemList.SteamFluidDrill.set(
            new MTESteamFluidDrill(
                MetaTileEntityID.STEAM_FLUID_DRILL.ID,
                "gtsr.steam.fluid.drill",
                "Steam Fluid Drill"));
        GTSRItemList.CrustSteamBorer.set(
            new MTECrustSteamBorer(
                MetaTileEntityID.CRUST_STEAM_BORER.ID,
                "gtsr.crust.steam.borer",
                "Crust Steam Borer"));
        GTSRItemList.CrustMatterAggregator.set(
            new MTECrustMatterAggregator(
                MetaTileEntityID.SINGULARITY_CRUST_STEAM_BORER.ID,
                "gtsr.crust.matter.aggregator",
                "Crust Matter Aggregator"));
        GTSRItemList.VeinSteamPyrolyzer.set(
            new MTEVeinSteamPyrolyzer(
                MetaTileEntityID.VEIN_STEAM_PYROLYZER.ID,
                "gtsr.vein.steam.pyrolyzer",
                "Vein Steam Pyrolyzer"));
        GTSRItemList.LargeSteamFurnace.set(
            new MTELargeSteamFurnace(
                MetaTileEntityID.LARGE_STEAM_FURNACE.ID,
                "gtsr.large.steam.furnace",
                "Large Steam Furnace"));
        GTSRItemList.AirCompressor
            .set(new MTEAirCompressor(MetaTileEntityID.AIR_COMPRESSOR.ID, "gtsr.air_compressor", "Air Compressor"));
        GTSRItemList.AtmosphericCentrifuge.set(
            new MTEAtmosphericCentrifuge(
                MetaTileEntityID.ATMOSPHERIC_CENTRIFUGE.ID,
                "gtsr.atmospheric_centrifuge",
                "Atmospheric Centrifuge"));
        GTSRItemList.KineticProcessingArray.set(
            new MTEKineticProcessingArray(
                MetaTileEntityID.KINETIC_PROCESSING_ARRAY.ID,
                "gtsr.kinetic.processing.array",
                "Kinetic Processing Array"));

        // --- 多方块机器: 特殊机器 ---
        GTSRItemList.SingularityDrillingHub.set(
            new MTESingularityDrillingHub(
                MetaTileEntityID.SINGULARITY_DRILLING_HUB.ID,
                "gtsr.singularity.drilling.hub",
                "Singularity Drilling Hub"));

        // --- 多方块机器: 非典型蒸汽机 ---
        GTSRItemList.LargeCokeOven
            .set(new MTELargeCokeOven(MetaTileEntityID.LARGE_COKE_OVEN.ID, "gtsr.large.coke.oven", "Large Coke Oven"));
        GTSRItemList.SiemensMartinFurnace.set(
            new MTESiemensMartinFurnace(
                MetaTileEntityID.SIEMENS_MARTIN_FURNACE.ID,
                "gtsr.siemens.martin.furnace",
                "Siemens-Martin Furnace"));
        GTSRItemList.ReinforcedBrickBlastFurnace.set(
            new MTEReinforcedBrickBlastFurnace(
                MetaTileEntityID.REINFORCED_BRICK_BLAST_FURNACE.ID,
                "gtsr.reinforced.brick.blast.furnace",
                "Reinforced Brick Blast Furnace"));
        GTSRItemList.AmmoniaPlant
            .set(new MTEAmmoniaPlant(MetaTileEntityID.AMMONIA_PLANT.ID, "gtsr.ammonia.plant", "Ammonia Plant"));
        GTSRItemList.GearSteamCompressor.set(
            new MTEGearSteamCompressor(
                MetaTileEntityID.GEAR_STEAM_COMPRESSOR.ID,
                "gtsr.gear.steam.compressor",
                "Gear Steam Compressor"));

        // --- 仓室 ---
        GTSRItemList.SteamInputHatchGeneric.set(
            new MTESteamInputHatchGeneric(
                MetaTileEntityID.STEAM_INPUT_HATCH_GENERIC.ID,
                "gtsr.steam.input.hatch",
                "Steam Input Hatch"));
        GTSRItemList.SteamOutputHatchGeneric.set(
            new MTESteamOutputHatchGeneric(
                MetaTileEntityID.STEAM_OUTPUT_HATCH_GENERIC.ID,
                "gtsr.steam.output.hatch.generic",
                "Output Hatch (Steam)"));
        GTSRItemList.PressureSteamHatch.set(
            new MTEHatchPressureSteamInput(
                MetaTileEntityID.PRESSURE_STEAM_HATCH.ID,
                "gtsr.pressure.steam.hatch",
                "Pressure Steam Hatch",
                0));
        GTSRItemList.PressureSteamOutputHatch.set(
            new MTEPressureSteamOutputHatch(
                MetaTileEntityID.PRESSURE_STEAM_OUTPUT_HATCH.ID,
                "gtsr.pressure.steam.output.hatch",
                "Pressure Steam Output Hatch"));
        GTSRItemList.SteamOutputHatch.set(
            new MTESteamOutputHatch(
                MetaTileEntityID.STEAM_OUTPUT_HATCH.ID,
                "gtsr.steam.output.hatch",
                "Steam Output Hatch"));
        GTSRItemList.SteamCoolingHatch.set(
            new MTESteamCoolingHatch(
                MetaTileEntityID.STEAM_COOLING_HATCH.ID,
                "gtsr.steam.cooling.hatch",
                "Steam Cooling Hatch"));
        GTSRItemList.PressureSteamCoolingHatch.set(
            new MTEPressureSteamCoolingHatch(
                MetaTileEntityID.PRESSURE_STEAM_COOLING_HATCH.ID,
                "gtsr.pressure.steam.cooling.hatch",
                "Pressure Steam Cooling Hatch"));
        // 巨型空气输入仓：仿照 GT5U 液态空气仓，仅允许空气/下界空气
        GTSRItemList.MegaAirInputHatch.set(
            new MTEMegaAirInputHatch(
                MetaTileEntityID.MEGA_AIR_INPUT_HATCH.ID,
                "gtsr.mega.air.input.hatch",
                "Mega Air Input Hatch"));
        // 蒸馏水仓：仿照 GT5U 蓄水仓（Reservoir Hatch），生成蒸馏水
        GTSRItemList.DistilledWaterHatch.set(
            new MTEDistilledWaterHatch(
                MetaTileEntityID.DISTILLED_WATER_HATCH.ID,
                "gtsr.distilled.water.hatch",
                "Distilled Water Hatch"));
        // 红石仓：任意多方块机器通用红石信号输出仓（267 为新 ID，无旧 ID 转换需求）
        GTSRItemList.RedstoneHatch
            .set(new MTEGTSRRedstoneHatch(MetaTileEntityID.REDSTONE_HATCH.ID, "gtsr.redstone_hatch", "Redstone Hatch"));

        // 蒸汽动力矿物处理物流工程集群（新 ID 段）
        GTSRItemList.ClusterController.set(
            new MTESteamMineralLogisticsCluster(
                MetaTileEntityID.CLUSTER_CONTROLLER.ID,
                "gtsr.cluster.controller",
                "Steam Mineral Logistics Cluster"));
        GTSRItemList.ClusterUnitCrusher.set(
            new MTEUnitCrusher(
                MetaTileEntityID.CLUSTER_UNIT_CRUSHER.ID,
                "gtsr.cluster.unit.crusher",
                "Cluster Crushing Unit"));
        GTSRItemList.ClusterUnitOreWasher.set(
            new MTEUnitOreWasher(
                MetaTileEntityID.CLUSTER_UNIT_ORE_WASHER.ID,
                "gtsr.cluster.unit.ore_washer",
                "Cluster Ore Washing Unit"));
        GTSRItemList.ClusterUnitCentrifuge.set(
            new MTEUnitCentrifuge(
                MetaTileEntityID.CLUSTER_UNIT_CENTRIFUGE.ID,
                "gtsr.cluster.unit.centrifuge",
                "Cluster Centrifuge Unit"));
        GTSRItemList.ClusterUnitThermalCentrifuge.set(
            new MTEUnitThermalCentrifuge(
                MetaTileEntityID.CLUSTER_UNIT_THERMOCENTRIFUGE.ID,
                "gtsr.cluster.unit.thermal_centrifuge",
                "Cluster Thermal Centrifuge Unit"));
        GTSRItemList.ClusterUnitSifter.set(
            new MTEUnitSifter(
                MetaTileEntityID.CLUSTER_UNIT_SIFTER.ID,
                "gtsr.cluster.unit.sifter",
                "Cluster Sifting Unit"));
        GTSRItemList.ClusterUnitMagneticSeparator.set(
            new MTEUnitMagneticSeparator(
                MetaTileEntityID.CLUSTER_UNIT_MAGNETIC_SEPARATOR.ID,
                "gtsr.cluster.unit.magnetic_separator",
                "Cluster Magnetic Separation Unit"));
        GTSRItemList.ClusterUnitFurnace.set(
            new MTEUnitFurnace(
                MetaTileEntityID.CLUSTER_UNIT_FURNACE.ID,
                "gtsr.cluster.unit.furnace",
                "Cluster Furnace Unit"));
        GTSRItemList.ClusterBoosterParallel.set(
            new MTEUnitParallelBooster(
                MetaTileEntityID.CLUSTER_BOOSTER_PARALLEL.ID,
                "gtsr.cluster.booster.parallel",
                "Cluster Parallel Amplifier"));
        GTSRItemList.ClusterBoosterSpeed.set(
            new MTEUnitSpeedBooster(
                MetaTileEntityID.CLUSTER_BOOSTER_SPEED.ID,
                "gtsr.cluster.booster.speed",
                "Cluster Speed Amplifier"));
        GTSRItemList.ClusterBoosterPrimary.set(
            new MTEUnitPrimaryBooster(
                MetaTileEntityID.CLUSTER_BOOSTER_PRIMARY.ID,
                "gtsr.cluster.booster.primary",
                "Cluster Primary Output Amplifier"));
        GTSRItemList.ClusterBoosterSecondary.set(
            new MTEUnitSecondaryBooster(
                MetaTileEntityID.CLUSTER_BOOSTER_SECONDARY.ID,
                "gtsr.cluster.booster.secondary",
                "Cluster Secondary Output Amplifier"));
        GTSRItemList.ClusterBoosterSteamSaver.set(
            new MTEUnitSteamSaverBooster(
                MetaTileEntityID.CLUSTER_BOOSTER_STEAM_SAVER.ID,
                "gtsr.cluster.booster.steam_saver",
                "Cluster Steam Saver Amplifier"));
        GTSRItemList.ClusterUnitLogistics.set(
            new MTEBasicLogisticsUnit(
                MetaTileEntityID.CLUSTER_UNIT_LOGISTICS.ID,
                "gtsr.cluster.unit.logistics",
                "Cluster Logistics Unit"));
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
        CreativeTabManager.addItemToTab(GTSRItemList.ReinforcedWaterCacheNode.get(1));
        CreativeTabManager.addItemToTab(GTSRItemList.OverpressureWaterCacheNode.get(1));
        // 奇点仓四件套：紧随缓存节点组（单方块段）
        CreativeTabManager.addItemToTab(GTSRItemList.SingularitySteamCompartment.get(1));
        CreativeTabManager.addItemToTab(GTSRItemList.SingularitySteamOutputCompartment.get(1));
        CreativeTabManager.addItemToTab(GTSRItemList.SingularityFluidInputCompartment.get(1));
        CreativeTabManager.addItemToTab(GTSRItemList.SingularityFluidOutputCompartment.get(1));
        CreativeTabManager.addItemToTab(GTSRItemList.SteamSingularityEntangler.get(1));
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
        CreativeTabManager.addItemToTab(GTSRItemList.CrustMatterAggregator.get(1));
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
        // 红石仓：任意多方块机器通用红石信号输出仓
        CreativeTabManager.addItemToTab(GTSRItemList.RedstoneHatch.get(1));
        // --- 新增奇点机器 (43-44) ---
        CreativeTabManager.addItemToTab(GTSRItemList.CriticalSingularityCompressor.get(1));
        CreativeTabManager.addItemToTab(GTSRItemList.DenseStateManipulator.get(1));
        // --- 集群家族：总控、七工作单元、五增幅器与物流单元（顺序与注册一致） ---
        CreativeTabManager.addItemToTab(GTSRItemList.ClusterController.get(1));
        CreativeTabManager.addItemToTab(GTSRItemList.ClusterUnitCrusher.get(1));
        CreativeTabManager.addItemToTab(GTSRItemList.ClusterUnitOreWasher.get(1));
        CreativeTabManager.addItemToTab(GTSRItemList.ClusterUnitCentrifuge.get(1));
        CreativeTabManager.addItemToTab(GTSRItemList.ClusterUnitThermalCentrifuge.get(1));
        CreativeTabManager.addItemToTab(GTSRItemList.ClusterUnitSifter.get(1));
        CreativeTabManager.addItemToTab(GTSRItemList.ClusterUnitMagneticSeparator.get(1));
        CreativeTabManager.addItemToTab(GTSRItemList.ClusterUnitFurnace.get(1));
        CreativeTabManager.addItemToTab(GTSRItemList.ClusterBoosterParallel.get(1));
        CreativeTabManager.addItemToTab(GTSRItemList.ClusterBoosterSpeed.get(1));
        CreativeTabManager.addItemToTab(GTSRItemList.ClusterBoosterPrimary.get(1));
        CreativeTabManager.addItemToTab(GTSRItemList.ClusterBoosterSecondary.get(1));
        CreativeTabManager.addItemToTab(GTSRItemList.ClusterBoosterSteamSaver.get(1));
        CreativeTabManager.addItemToTab(GTSRItemList.ClusterUnitLogistics.get(1));
    }
}
