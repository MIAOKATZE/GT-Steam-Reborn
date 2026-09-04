package com.miaokatze.gtsr.loader;

import static com.miaokatze.gtsr.loader.recipes.RecipeLoaderUtils.log;

import com.miaokatze.gtsr.loader.recipes.ClusterRecipes;
import com.miaokatze.gtsr.loader.recipes.DisplayRecipes;
import com.miaokatze.gtsr.loader.recipes.HatchRecipes;
import com.miaokatze.gtsr.loader.recipes.MiscRecipes;
import com.miaokatze.gtsr.loader.recipes.MultiblockMachineRecipes;
import com.miaokatze.gtsr.loader.recipes.NodeAndHubRecipes;
import com.miaokatze.gtsr.loader.recipes.ProcessingMachineRecipes;
import com.miaokatze.gtsr.main.GTSteamReborn;

/**
 * GTSR 配方注册门面（SR-A03 拆分后仅剩壳）：run() 25 行派发表与 safeRegister 错误隔离，
 * 配方实现按域迁至 recipes/ 六域类（处理机/节点枢纽/工作台装配/仓室/展示/杂项）与
 * RecipeLoaderUtils 工具类。派发表调用顺序、组名字符串与行内注释逐字未动——配方注册
 * 顺序 = run() 调用顺序，迁移零语义变化（无重平衡、try-catch 粒度不变）。
 */
public class GTSRRecipeLoader implements Runnable {

    // 【Bug2 加固】单个注册方法异常时记 ERROR（含方法名）后继续执行后续方法，
    // 杜绝单点异常穿透 run() 被 CommonProxy.postInit 捕获后导致后续全部配方静默团灭。
    private static void safeRegister(String name, Runnable r) {
        try {
            r.run();
            log("Registered " + name + " recipes successfully.");
        } catch (Exception e) {
            GTSteamReborn.LOG.error("[GTSR-Recipe] Failed to register " + name + " recipes: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        // 【Bug2 加固】全部注册调用经 safeRegister 独立 try-catch，单方法失败不再拖垮其余配方
        safeRegister("CokeOven", ProcessingMachineRecipes::registerCokeOvenRecipes);
        safeRegister("SiemensMartin", ProcessingMachineRecipes::registerSiemensMartinRecipes);
        safeRegister("Ammonia", ProcessingMachineRecipes::registerAmmoniaRecipes);
        safeRegister("AirCompressor", ProcessingMachineRecipes::registerAirCompressorRecipes);
        safeRegister("AtmosphericCentrifuge", ProcessingMachineRecipes::registerAtmosphericCentrifugeRecipes);
        safeRegister("Chip", ProcessingMachineRecipes::registerChipRecipes);
        safeRegister("Catalyst", ProcessingMachineRecipes::registerCatalystRecipes);
        safeRegister("CacheNode", NodeAndHubRecipes::registerCacheNodeRecipes);
        safeRegister("TinyPlanetBlock", NodeAndHubRecipes::registerTinyPlanetRecipe); // 新增：Botania Tiny Planet
                                                                                      // Block（魔力环绕器）工作台配方
                                                                                      // （Botania 未加载时自动跳过）
        safeRegister("HubTerminal", NodeAndHubRecipes::registerHubTerminalRecipe); // 枢纽终端工作台配方：中心蒸汽纠缠奇点 + 8 钢板环绕
        safeRegister("NodeNBTClear", NodeAndHubRecipes::registerNodeNBTClearRecipes); // 节点清 NBT 无序配方：1 节点(无视 NBT) → 1
                                                                                      // 干净节点
        safeRegister("Node", NodeAndHubRecipes::registerNodeRecipes);
        safeRegister("MultiblockWorkbench", MultiblockMachineRecipes::registerMultiblockWorkbenchRecipes);
        safeRegister("MultiblockAssembler", MultiblockMachineRecipes::registerMultiblockAssemblerRecipes);
        safeRegister("Hatch", HatchRecipes::registerHatchRecipes);
        safeRegister("DistilledWaterHatch", HatchRecipes::registerDistilledWaterHatchRecipe); // 蒸馏水仓：继承蓄水仓配方 +
                                                                                              // 3组蒸汽纠缠奇点
        safeRegister("GeothermalBoilerDisplay", DisplayRecipes::registerGeothermalBoilerDisplayRecipes);
        safeRegister("FluidDrillDisplay", DisplayRecipes::registerFluidDrillDisplayRecipes);
        safeRegister("GearSteamCompressorDisplay", DisplayRecipes::registerGearSteamCompressorDisplayRecipes);
        safeRegister(
            "SteamSingularityEntanglerDisplay",
            DisplayRecipes::registerSteamSingularityEntanglerDisplayRecipes);
        safeRegister(
            "CriticalSingularityCompressorDisplay",
            DisplayRecipes::registerCriticalSingularityCompressorDisplayRecipes);
        safeRegister("Implosion", MiscRecipes::registerImplosionRecipes); // 聚爆压缩机：2 临界奇点 → 8 普通奇点
        safeRegister("DenseStateManipulatorDisplay", DisplayRecipes::registerDenseStateManipulatorDisplayRecipes);
        safeRegister("ReinforcedBrickBlastFurnace", MiscRecipes::registerReinforcedBrickBlastFurnaceRecipe);
        safeRegister("Cluster", ClusterRecipes::addRecipes); // 矿物处理集群 14 组件：14 条全组装机配方
                                                             // （GTUDK 用户规格）：eut 30/32=LV、128=MV，磁选与筛选为 MV
    }
}
