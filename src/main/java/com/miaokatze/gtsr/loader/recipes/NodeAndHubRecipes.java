package com.miaokatze.gtsr.loader.recipes;

import static com.miaokatze.gtsr.loader.recipes.RecipeLoaderUtils.filterNulls;
import static com.miaokatze.gtsr.loader.recipes.RecipeLoaderUtils.get;
import static com.miaokatze.gtsr.loader.recipes.RecipeLoaderUtils.hasNull;
import static com.miaokatze.gtsr.loader.recipes.RecipeLoaderUtils.log;
import static com.miaokatze.gtsr.loader.recipes.RecipeLoaderUtils.warn;
import static gregtech.api.recipe.RecipeMaps.assemblerRecipes;
import static gregtech.api.util.GTRecipeBuilder.INGOTS;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

import net.minecraft.item.ItemStack;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;

import cpw.mods.fml.common.Loader;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.enums.TierEU;
import gregtech.api.util.GTModHandler;
import gtPlusPlus.xmod.gregtech.api.enums.GregtechItemList;

/**
 * 节点/枢纽族配方（SR-A03 组二，自 GTSRRecipeLoader 门面原样迁出）：六种缓存节点合成表/
 * Botania 魔力环绕器/枢纽终端/节点洗白无序表/采矿与钻井节点。方法体逐字未动；
 * 注册顺序与错误隔离由门面 run() 派发表 + safeRegister 单点决定。
 */
public final class NodeAndHubRecipes {

    private NodeAndHubRecipes() {}

    public static void registerCacheNodeRecipes() {
        log("Registering cache node recipes...");

        ItemStack steamCacheResult = GTSRItemList.SteamCacheNode.get(1);
        if (steamCacheResult == null) {
            warn("SteamCacheNode item is null, skipping recipe!");
        } else {
            GTModHandler.addCraftingRecipe(
                steamCacheResult,
                GTModHandler.RecipeBits.BITSD,
                new Object[] { "MPM", "PTP", "MPM", 'M', "plateTripleBronze", 'P', "pipeLargeBronze", 'T',
                    GregtechItemList.GTFluidTank_ULV.get(1) });
        }

        ItemStack reinforcedResult = GTSRItemList.ReinforcedSteamCacheNode.get(1);
        if (reinforcedResult == null) {
            warn("ReinforcedSteamCacheNode item is null, skipping recipe!");
        } else {
            ItemStack steamCacheInput = GTSRItemList.SteamCacheNode.get(1);
            if (steamCacheInput == null) {
                warn("SteamCacheNode input is null, skipping ReinforcedSteamCacheNode recipe!");
            } else {
                GTModHandler.addCraftingRecipe(
                    reinforcedResult,
                    GTModHandler.RecipeBits.BITSD,
                    new Object[] { "ABA", "CDC", "ABA", 'A', get(OrePrefixes.plateTriple, Materials.Steel, 1), 'B',
                        GTSRItemList.SteamEntangledSingularity.get(1), 'C',
                        get(OrePrefixes.pipeHuge, Materials.Steel, 1), 'D', steamCacheInput });
            }
        }

        ItemStack waterResult = GTSRItemList.WaterCacheNode.get(1);
        if (waterResult == null) {
            warn("WaterCacheNode item is null, skipping recipe!");
        } else {
            ItemStack bcTank = GTModHandler.getModItem("BuildCraft|Factory", "tankBlock", 1);
            if (bcTank == null) {
                warn("BuildCraft tank is null, trying alternate mod ID...");
                bcTank = GTModHandler.getModItem("BuildCraft:Factory", "tankBlock", 1);
            }
            if (bcTank == null) {
                warn("BuildCraft tank still null, skipping WaterCacheNode recipe!");
            } else {
                GTModHandler.addCraftingRecipe(
                    waterResult,
                    GTModHandler.RecipeBits.BITSD,
                    new Object[] { "MPM", "PTP", "MPM", 'M', "plateTripleBronze", 'P', "pipeLargeBronze", 'T',
                        bcTank });
            }
        }

        // 耐压通用流体缓存节点：下位节点（通用流体缓存节点）升级式，镜像耐压蒸汽缓存节点
        ItemStack reinforcedWaterResult = GTSRItemList.ReinforcedWaterCacheNode.get(1);
        if (reinforcedWaterResult == null) {
            warn("ReinforcedWaterCacheNode item is null, skipping recipe!");
        } else {
            ItemStack waterCacheInput = GTSRItemList.WaterCacheNode.get(1);
            if (waterCacheInput == null) {
                warn("WaterCacheNode input is null, skipping ReinforcedWaterCacheNode recipe!");
            } else {
                GTModHandler.addCraftingRecipe(
                    reinforcedWaterResult,
                    GTModHandler.RecipeBits.BITSD,
                    new Object[] { "ABA", "CDC", "ABA", 'A', get(OrePrefixes.plateTriple, Materials.Steel, 1), 'B',
                        GTSRItemList.SteamEntangledSingularity.get(1), 'C',
                        get(OrePrefixes.pipeHuge, Materials.Steel, 1), 'D', waterCacheInput });
            }
        }

        log("Cache node recipes done.");
    }

    /**
     * 注册 Botania Tiny Planet 工作台合成配方。
     * <p>
     * 配方形状（用户需求：LV 电路板居中，8 个蒸汽纠缠奇点绕一圈）：
     * 
     * <pre>
     *   S S S
     *   S C S
     *   S S S
     * </pre>
     * 
     * 其中：
     * - C = LV 电路板（任意种类，通过 OrePrefixes.circuit.get(Materials.LV) 匹配 OreDict）
     * - S = 蒸汽纠缠奇点（GTSRItemList.SteamEntangledSingularity）
     * 产物：Botania:tinyPlanetBlock（魔力环绕器）。原版配方为行星项链（tinyPlanet）+ 8 石头 = 魔力环绕器；
     * 本配方直接产出方块，方便检索。
     * <p>
     * 安全处理：Botania 是 GTNH 必装模组，但出于代码健壮性仍用 Loader.isModLoaded 判断。
     * 若 Botania 未加载、产物或材料为 null，则跳过配方注册并记录警告日志。
     */
    public static void registerTinyPlanetRecipe() {
        log("Registering Tiny Planet crafting recipe...");

        // 1. 运行时检测 Botania 是否加载（与 MTEVoidCrustSteamBorer.isPluginLoaded 风格一致）
        if (!Loader.isModLoaded("Botania")) {
            log("Botania not loaded, skipping Tiny Planet recipe.");
            return;
        }

        // 2. 获取产物：Botania:tinyPlanetBlock（魔力环绕器方块，注册名为小写驼峰式；原版配方为行星项链 tinyPlanet + 8 石头 = 魔力环绕器）
        ItemStack tinyPlanetBlock = GTModHandler.getModItem("Botania", "tinyPlanetBlock", 1);
        if (tinyPlanetBlock == null) {
            warn("Botania:tinyPlanetBlock item is null, skipping Tiny Planet recipe!");
            return;
        }

        // 3. 获取中心材料：LV 电路板（OrePrefixes.circuit + Materials.LV 匹配任意种类 LV 电路板）
        ItemStack lvCircuit = get(OrePrefixes.circuit, Materials.LV, 1);
        if (lvCircuit == null) {
            warn("LV circuit is null, skipping Tiny Planet recipe!");
            return;
        }

        // 4. 获取环绕材料：蒸汽纠缠奇点
        ItemStack singularity = GTSRItemList.SteamEntangledSingularity.get(1);
        if (singularity == null) {
            warn("SteamEntangledSingularity is null, skipping Tiny Planet recipe!");
            return;
        }

        // 5. 注册工作台配方：SSS / SCS / SSS（C 居中，S 绕一圈）
        GTModHandler.addCraftingRecipe(
            tinyPlanetBlock,
            GTModHandler.RecipeBits.BITSD,
            new Object[] { "SSS", "SCS", "SSS", 'S', singularity, 'C', lvCircuit });

        log("Tiny Planet recipe registered.");
    }

    /**
     * 注册枢纽终端工作台合成配方。
     * <p>
     * 配方形状（中心 1 个蒸汽纠缠奇点，8 个钢板环绕一圈）：
     *
     * <pre>
     *   S S S
     *   S C S
     *   S S S
     * </pre>
     *
     * 其中：
     * - C = 蒸汽纠缠奇点（GTSRItemList.SteamEntangledSingularity）
     * - S = 钢板（OrePrefixes.plate + Materials.Steel，匹配矿物词典）
     * 产物：枢纽终端（GTSRItemList.HubTerminal）
     * <p>
     * 安全处理：沿用本类 hasNull/warn 防御模式，任一材料为 null 时跳过注册并记录警告。
     */
    public static void registerHubTerminalRecipe() {
        log("Registering Hub Terminal crafting recipe...");

        ItemStack terminalOut = get(GTSRItemList.HubTerminal, 1);
        ItemStack steelPlate = get(OrePrefixes.plate, Materials.Steel, 1);
        ItemStack singularity = get(GTSRItemList.SteamEntangledSingularity, 1);

        if (hasNull(terminalOut, steelPlate, singularity)) {
            warn("Skipped HubTerminal recipe - output or inputs are null");
            return;
        }

        GTModHandler.addCraftingRecipe(
            terminalOut,
            GTModHandler.RecipeBits.BITSD,
            new Object[] { "SSS", "SCS", "SSS", 'S', steelPlate, 'C', singularity });

        log("Hub Terminal recipe registered.");
    }

    /**
     * 节点「洗白」无序配方：1 个节点(无视 NBT) → 1 个干净节点，帮助玩家清除节点上的 NBT 数据
     * （绑定信息/自定义名/奇点消耗标记等）。vanilla 无序配方匹配不校验 NBT，输出为全新干净栈。
     * 覆盖全部 12 种节点：采矿/钻井节点 + 6 种缓存节点 + 4 种奇点仓。
     * 注意：会同时清掉 gtsr.singularity_consumed 标记，属预期行为（允许玩家重新绑定，计划已确认）。
     */
    public static void registerNodeNBTClearRecipes() {
        log("Registering node NBT-clearing shapeless recipes...");

        GTSRItemList[] nodeItems = { GTSRItemList.SingularityMinerNode, GTSRItemList.SingularityDrillingNode,
            GTSRItemList.SteamCacheNode, GTSRItemList.ReinforcedSteamCacheNode, GTSRItemList.OverpressureSteamCacheNode,
            GTSRItemList.WaterCacheNode, GTSRItemList.ReinforcedWaterCacheNode, GTSRItemList.OverpressureWaterCacheNode,
            GTSRItemList.SingularitySteamCompartment, GTSRItemList.SingularitySteamOutputCompartment,
            GTSRItemList.SingularityFluidInputCompartment, GTSRItemList.SingularityFluidOutputCompartment };

        for (GTSRItemList node : nodeItems) {
            ItemStack clean = get(node, 1);
            if (hasNull(clean)) {
                warn("Skipped NBT-clear recipe for " + node.name() + " - stack is null");
                continue;
            }
            GTModHandler.addShapelessCraftingRecipe(clean, new Object[] { clean });
        }

        log("Node NBT-clearing recipes registered: " + nodeItems.length);
    }

    public static void registerNodeRecipes() {
        log("Registering mining/drilling node recipes...");
        ItemStack miningPipe = GTModHandler.getIC2Item("miningPipe", 8);
        if (miningPipe == null) {
            warn("IC2 miningPipe is null! Trying alternate retrieval...");
            miningPipe = GTModHandler.getIC2Item("miningPipe", 1);
        }

        ItemStack minerOut = get(GTSRItemList.SingularityMinerNode, 1);
        if (!hasNull(minerOut) && miningPipe != null) {
            ItemStack[] inputs = filterNulls(
                get(GTSRItemList.SteamEntangledSingularity, 2),
                get(OrePrefixes.frameGt, Materials.Steel, 1),
                get(OrePrefixes.gearGt, Materials.Steel, 8),
                get(OrePrefixes.plateDense, Materials.Steel, 4),
                miningPipe,
                get(ItemList.Electric_Piston_LV, 4),
                get(OrePrefixes.circuit, Materials.LV, 2));
            GTValues.RA.stdBuilder()
                .itemInputs(inputs)
                .itemOutputs(minerOut)
                .fluidInputs(Materials.SolderingAlloy.getMolten(2 * INGOTS))
                .duration(20 * SECONDS)
                .eut(TierEU.RECIPE_LV)
                .addTo(assemblerRecipes);
        } else {
            warn("Skipped SingularityMinerNode recipe" + (miningPipe == null ? " - miningPipe is null" : ""));
        }

        ItemStack drillOut = get(GTSRItemList.SingularityDrillingNode, 1);
        if (!hasNull(drillOut) && miningPipe != null) {
            ItemStack[] inputs = filterNulls(
                get(GTSRItemList.SteamEntangledSingularity, 2),
                get(OrePrefixes.frameGt, Materials.Steel, 1),
                get(OrePrefixes.pipeHuge, Materials.Steel, 4),
                get(OrePrefixes.plateDense, Materials.Steel, 4),
                miningPipe,
                get(ItemList.Electric_Pump_LV, 6),
                get(OrePrefixes.circuit, Materials.LV, 2));
            GTValues.RA.stdBuilder()
                .itemInputs(inputs)
                .itemOutputs(drillOut)
                .fluidInputs(Materials.SolderingAlloy.getMolten(2 * INGOTS))
                .duration(20 * SECONDS)
                .eut(TierEU.RECIPE_LV)
                .addTo(assemblerRecipes);
        } else {
            warn("Skipped SingularityDrillingNode recipe" + (miningPipe == null ? " - miningPipe is null" : ""));
        }

        log("Node recipes done.");
    }
}
