package com.miaokatze.gtsr.loader;

import static com.miaokatze.gtsr.loader.recipes.RecipeLoaderUtils.log;

import com.miaokatze.gtsr.common.dimension.prosperity.industrial.ProsperityIndustrialRecipes;
import com.miaokatze.gtsr.loader.recipes.AbyssalObsessionRecipes;
import com.miaokatze.gtsr.loader.recipes.ClusterRecipes;
import com.miaokatze.gtsr.loader.recipes.CriticalMEHatchRecipes;
import com.miaokatze.gtsr.loader.recipes.CriticalMachineRecipes;
import com.miaokatze.gtsr.loader.recipes.DisplayRecipes;
import com.miaokatze.gtsr.loader.recipes.HatchRecipes;
import com.miaokatze.gtsr.loader.recipes.MiscRecipes;
import com.miaokatze.gtsr.loader.recipes.MultiblockMachineRecipes;
import com.miaokatze.gtsr.loader.recipes.NodeAndHubRecipes;
import com.miaokatze.gtsr.loader.recipes.ProcessingMachineRecipes;
import com.miaokatze.gtsr.loader.recipes.ProsperityAirCompressorRecipes;
import com.miaokatze.gtsr.loader.recipes.SpacetimeMachineRecipes;

/**
 * GTSR 配方注册门面。主配方与派生视图交由 Core 分阶段执行，组内失败携带组名上抛，
 * 不让统一注册管线把缺失配方组记录为成功；各组配方内容及执行顺序保持不变。
 */
public class GTSRRecipeLoader implements Runnable {

    private static void registerGroup(String name, Runnable r) {
        try {
            r.run();
            log("Registered " + name + " recipes successfully.");
        } catch (Exception e) {
            throw new IllegalStateException("[GTSR-Recipe] Failed to register " + name + " recipes", e);
        }
    }

    /** Snapshot native work recipes only after all registered main recipe callbacks have completed. */
    public static void registerCriticalRuntimeViews() {
        registerGroup("CriticalMachines", CriticalMachineRecipes::register);
    }

    @Override
    public void run() {
        registerGroup("CokeOven", ProcessingMachineRecipes::registerCokeOvenRecipes);
        registerGroup("SiemensMartin", ProcessingMachineRecipes::registerSiemensMartinRecipes);
        registerGroup("Ammonia", ProcessingMachineRecipes::registerAmmoniaRecipes);
        registerGroup("AirCompressor", ProcessingMachineRecipes::registerAirCompressorRecipes);
        registerGroup("ProsperityAirCompressor", ProsperityAirCompressorRecipes::register);
        registerGroup("AtmosphericCentrifuge", ProcessingMachineRecipes::registerAtmosphericCentrifugeRecipes);
        registerGroup("Chip", ProcessingMachineRecipes::registerChipRecipes);
        registerGroup("Catalyst", ProcessingMachineRecipes::registerCatalystRecipes);
        registerGroup(
            "CriticalFoundation",
            com.miaokatze.gtsr.common.critical.CriticalFoundationMaterials::registerRecipes);
        registerGroup("CacheNode", NodeAndHubRecipes::registerCacheNodeRecipes);
        registerGroup("TinyPlanetBlock", NodeAndHubRecipes::registerTinyPlanetRecipe); // 新增：Botania Tiny Planet
                                                                                       // Block（魔力环绕器）工作台配方
                                                                                       // （Botania 未加载时自动跳过）
        registerGroup("HubTerminal", NodeAndHubRecipes::registerHubTerminalRecipe); // 枢纽终端工作台配方：中心蒸汽纠缠奇点 + 8 钢板环绕
        registerGroup("NodeNBTClear", NodeAndHubRecipes::registerNodeNBTClearRecipes); // 节点清 NBT 无序配方：1 节点(无视 NBT) → 1
                                                                                       // 干净节点
        registerGroup("Node", NodeAndHubRecipes::registerNodeRecipes);
        registerGroup("MultiblockWorkbench", MultiblockMachineRecipes::registerMultiblockWorkbenchRecipes);
        registerGroup("MultiblockAssembler", MultiblockMachineRecipes::registerMultiblockAssemblerRecipes);
        registerGroup("Hatch", HatchRecipes::registerHatchRecipes);
        registerGroup("CriticalMEHatch", CriticalMEHatchRecipes::register);
        registerGroup("DistilledWaterHatch", HatchRecipes::registerDistilledWaterHatchRecipe); // 蒸馏水仓：继承蓄水仓配方 +
                                                                                               // 3组蒸汽纠缠奇点
        registerGroup("GeothermalBoilerDisplay", DisplayRecipes::registerGeothermalBoilerDisplayRecipes);
        registerGroup("FluidDrillDisplay", DisplayRecipes::registerFluidDrillDisplayRecipes);
        registerGroup("GearSteamCompressorDisplay", DisplayRecipes::registerGearSteamCompressorDisplayRecipes);
        registerGroup(
            "SteamSingularityEntanglerDisplay",
            DisplayRecipes::registerSteamSingularityEntanglerDisplayRecipes);
        registerGroup(
            "CriticalSingularityCompressorDisplay",
            DisplayRecipes::registerCriticalSingularityCompressorDisplayRecipes);
        registerGroup("Implosion", MiscRecipes::registerImplosionRecipes); // 聚爆压缩机：2 临界奇点 → 8 普通奇点
        registerGroup("DenseStateManipulatorDisplay", DisplayRecipes::registerDenseStateManipulatorDisplayRecipes);
        registerGroup("ReinforcedBrickBlastFurnace", MiscRecipes::registerReinforcedBrickBlastFurnaceRecipe);
        registerGroup("Cluster", ClusterRecipes::addRecipes); // 矿物处理集群 14 组件：14 条全组装机配方
                                                              // （GTUDK 用户规格）：eut 30/32=LV、128=MV，磁选与筛选为 MV
        registerGroup("ProsperityIndustrial", ProsperityIndustrialRecipes::registerAll);
        registerGroup("AbyssalObsession", AbyssalObsessionRecipes::register);
        registerGroup("SpacetimeEngineering", SpacetimeMachineRecipes::register);
        registerGroup("PortableWeapons", com.miaokatze.gtsr.loader.recipes.PortableWeaponRecipes::register);
    }
}
