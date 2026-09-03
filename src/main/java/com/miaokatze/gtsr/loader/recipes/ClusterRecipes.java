package com.miaokatze.gtsr.loader.recipes;

import static com.miaokatze.gtsr.loader.recipes.RecipeLoaderUtils.filterNulls;
import static com.miaokatze.gtsr.loader.recipes.RecipeLoaderUtils.get;
import static com.miaokatze.gtsr.loader.recipes.RecipeLoaderUtils.hasNull;
import static com.miaokatze.gtsr.loader.recipes.RecipeLoaderUtils.log;
import static com.miaokatze.gtsr.loader.recipes.RecipeLoaderUtils.warn;
import static gregtech.api.recipe.RecipeMaps.assemblerRecipes;
import static gregtech.api.util.GTRecipeBuilder.INGOTS;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.enums.TierEU;
import gregtech.api.util.GTModHandler;

/**
 * 矿物处理集群 14 组件配方（蒸汽动力矿物处理物流工程集群：总控/工作/增幅/物流）：
 * 工作台 7 件（主控/粉碎/洗矿/离心/筛选/熔炼/物流，形状风格镜像 MultiblockMachineRecipes 工作台段）
 * + 组装机 7 件（磁选单元/并行增幅/速度增幅/蒸汽节省增幅 = LV 档；热力离心单元/主产物增幅/副产物增幅 = MV 档）。
 * 档位口径（用户确认，不得擅改）：磁选/并行/速度/节省(steam_saver) eut TierEU.RECIPE_LV；
 * 热力离心/主产物(primary)/副产物(secondary) eut TierEU.RECIPE_MV。
 * 产物 ItemStack 一律经 RecipeLoaderUtils.get(GTSRItemList.X, n) 空防御获取；
 * 组装机配方统一焊料 SolderingAlloy 2*INGOTS、时长 20*SECONDS，输入经 filterNulls + hasNull 整体跳过。
 */
public final class ClusterRecipes {

    private ClusterRecipes() {}

    /** 配方注册入口：先工作台后组装机，由 GTSRRecipeLoader.run() 派发表经 safeRegister 调用。 */
    public static void addRecipes() {
        registerCraftingRecipes();
        registerAssemblerRecipes();
    }

    /**
     * 工作台 7 件：主控（中等成本）/粉碎/洗矿/离心/筛选（低成本框式）/熔炼/物流。
     * 材料字符串为 oredict 名（小写字母不占用，保留给工具），特例物料（镀铜砖壳/LV 电路/原版熔炉）用 ItemStack。
     */
    private static void registerCraftingRecipes() {
        log("Registering cluster crafting recipes...");
        ItemStack bronzePlatedBricks = get(ItemList.Casing_BronzePlatedBricks, 1);
        ItemStack circuitLV = get(OrePrefixes.circuit, Materials.LV, 1);
        // GT5U 未注册裸 "furnace" oredict（LoaderGTOreDictionary.java:205 注册的是 craftingFurnace），
        // 按任务 fallback 改用原版熔炉 ItemStack（wildcard 元数据，镜像 MultiblockMachineRecipes 活塞先例）
        ItemStack vanillaFurnace = new ItemStack(Blocks.furnace, 1, OreDictionary.WILDCARD_VALUE);
        if (hasNull(bronzePlatedBricks, circuitLV)) {
            warn(
                "Skipped cluster crafting recipes - shared inputs (Casing_BronzePlatedBricks/circuit LV) contain null");
            return;
        }

        // --- 主控（中等成本）：三重钢板角柱 + 钢板墙 + 中心钢框架 + 镀铜砖壳 + LV 电路 ---
        ItemStack controller = get(GTSRItemList.ClusterController, 1);
        if (controller == null) {
            warn("ClusterController item is null, skipping recipe!");
        } else {
            GTModHandler.addCraftingRecipe(
                controller,
                GTModHandler.RecipeBits.BITSD,
                new Object[] { "APA", "PFP", "PBC", 'A', "plateTripleSteel", 'P', "plateSteel", 'F', "frameGtSteel",
                    'B', bronzePlatedBricks, 'C', circuitLV });
        }

        // --- 粉碎单元：钢齿轮组 + 钢板 + 中心钢框架 ---
        ItemStack crusher = get(GTSRItemList.ClusterUnitCrusher, 1);
        if (crusher == null) {
            warn("ClusterUnitCrusher item is null, skipping recipe!");
        } else {
            GTModHandler.addCraftingRecipe(
                crusher,
                GTModHandler.RecipeBits.BITSD,
                new Object[] { "PGP", "GFG", "PPP", 'P', "plateSteel", 'G', "gearGtSteel", 'F', "frameGtSteel" });
        }

        // --- 洗矿单元：青铜大管道侧墙 + 钢板 + 青铜框架 + 青铜齿轮 ---
        ItemStack oreWasher = get(GTSRItemList.ClusterUnitOreWasher, 1);
        if (oreWasher == null) {
            warn("ClusterUnitOreWasher item is null, skipping recipe!");
        } else {
            GTModHandler.addCraftingRecipe(
                oreWasher,
                GTModHandler.RecipeBits.BITSD,
                new Object[] { "PBP", "GFG", "PBP", 'P', "plateSteel", 'B', "pipeLargeBronze", 'G', "gearGtBronze", 'F',
                    "frameGtBronze" });
        }

        // --- 离心单元：钢板 + 钢转子轴 + 钢齿轮 + 钢框架 ---
        ItemStack centrifuge = get(GTSRItemList.ClusterUnitCentrifuge, 1);
        if (centrifuge == null) {
            warn("ClusterUnitCentrifuge item is null, skipping recipe!");
        } else {
            GTModHandler.addCraftingRecipe(
                centrifuge,
                GTModHandler.RecipeBits.BITSD,
                new Object[] { "PGP", "RFR", "PGP", 'P', "plateSteel", 'G', "gearGtSteel", 'R', "rotorSteel", 'F',
                    "frameGtSteel" });
        }

        // --- 筛选单元（低成本框式）：钢杆框 + 中心钢板 ---
        ItemStack sifter = get(GTSRItemList.ClusterUnitSifter, 1);
        if (sifter == null) {
            warn("ClusterUnitSifter item is null, skipping recipe!");
        } else {
            GTModHandler.addCraftingRecipe(
                sifter,
                GTModHandler.RecipeBits.BITSD,
                new Object[] { "S S", "SPS", "S S", 'S', "stickSteel", 'P', "plateSteel" });
        }

        // --- 熔炼单元：钢板壳 + 镀铜砖壳 + 原版熔炉 ---
        ItemStack furnace = get(GTSRItemList.ClusterUnitFurnace, 1);
        if (furnace == null) {
            warn("ClusterUnitFurnace item is null, skipping recipe!");
        } else {
            GTModHandler.addCraftingRecipe(
                furnace,
                GTModHandler.RecipeBits.BITSD,
                new Object[] { "PPP", "PBP", "PFP", 'P', "plateSteel", 'B', bronzePlatedBricks, 'F', vanillaFurnace });
        }

        // --- 物流单元：青铜大管道 + 青铜齿轮 + 三重青铜板 + LV 电路 ---
        ItemStack logistics = get(GTSRItemList.ClusterUnitLogistics, 1);
        if (logistics == null) {
            warn("ClusterUnitLogistics item is null, skipping recipe!");
        } else {
            GTModHandler.addCraftingRecipe(
                logistics,
                GTModHandler.RecipeBits.BITSD,
                new Object[] { "PTP", "GCG", "PTP", 'P', "pipeLargeBronze", 'T', "plateTripleBronze", 'G',
                    "gearGtBronze", 'C', circuitLV });
        }

        log("Cluster crafting recipes done.");
    }

    /**
     * 组装机 7 件（风格镜像 NodeAndHubRecipes 节点段：显式电路 ItemStack，不用 .circuit(n)）：
     * 磁选单元/并行增幅/速度增幅/蒸汽节省增幅 = LV 档；热力离心单元/主产物增幅/副产物增幅 = MV 档。
     */
    private static void registerAssemblerRecipes() {
        log("Registering cluster assembler recipes...");

        // --- 磁选单元（LV 档组装机配方）：镀铜砖壳 + 钢框架 + LV 马达 + 磁化铁杆（GT5U LV 档 STICK_MAGNETIC =
        // stick IronMagnetic，oredict stickIronMagnetic）+ LV 电路 ---
        ItemStack magneticSeparator = get(GTSRItemList.ClusterUnitMagneticSeparator, 1);
        if (!hasNull(magneticSeparator)) {
            ItemStack[] inputs = filterNulls(
                get(ItemList.Casing_BronzePlatedBricks, 1),
                get(OrePrefixes.frameGt, Materials.Steel, 1),
                get(ItemList.Electric_Motor_LV, 1),
                get(OrePrefixes.stick, Materials.IronMagnetic, 1),
                get(OrePrefixes.circuit, Materials.LV, 1));
            if (!hasNull(inputs)) {
                GTValues.RA.stdBuilder()
                    .itemInputs(inputs)
                    .itemOutputs(magneticSeparator)
                    .fluidInputs(Materials.SolderingAlloy.getMolten(2 * INGOTS))
                    .duration(20 * SECONDS)
                    .eut(TierEU.RECIPE_LV)
                    .addTo(assemblerRecipes);
            } else {
                warn("Skipped ClusterUnitMagneticSeparator recipe - inputs contain null");
            }
        } else {
            warn("ClusterUnitMagneticSeparator item is null, skipping recipe!");
        }

        // --- 并行增幅器（LV 档组装机配方）：LV 电路 x2 + 钢齿轮 + LV 电动泵 ---
        ItemStack parallelBooster = get(GTSRItemList.ClusterBoosterParallel, 1);
        if (!hasNull(parallelBooster)) {
            ItemStack[] inputs = filterNulls(
                get(OrePrefixes.circuit, Materials.LV, 2),
                get(OrePrefixes.gearGt, Materials.Steel, 1),
                get(ItemList.Electric_Pump_LV, 1));
            if (!hasNull(inputs)) {
                GTValues.RA.stdBuilder()
                    .itemInputs(inputs)
                    .itemOutputs(parallelBooster)
                    .fluidInputs(Materials.SolderingAlloy.getMolten(2 * INGOTS))
                    .duration(20 * SECONDS)
                    .eut(TierEU.RECIPE_LV)
                    .addTo(assemblerRecipes);
            } else {
                warn("Skipped ClusterBoosterParallel recipe - inputs contain null");
            }
        } else {
            warn("ClusterBoosterParallel item is null, skipping recipe!");
        }

        // --- 速度增幅器（LV 档组装机配方）：LV 电路 + LV 电动活塞 + 红石 ---
        ItemStack speedBooster = get(GTSRItemList.ClusterBoosterSpeed, 1);
        if (!hasNull(speedBooster)) {
            ItemStack[] inputs = filterNulls(
                get(OrePrefixes.circuit, Materials.LV, 1),
                get(ItemList.Electric_Piston_LV, 1),
                get(OrePrefixes.dust, Materials.Redstone, 1));
            if (!hasNull(inputs)) {
                GTValues.RA.stdBuilder()
                    .itemInputs(inputs)
                    .itemOutputs(speedBooster)
                    .fluidInputs(Materials.SolderingAlloy.getMolten(2 * INGOTS))
                    .duration(20 * SECONDS)
                    .eut(TierEU.RECIPE_LV)
                    .addTo(assemblerRecipes);
            } else {
                warn("Skipped ClusterBoosterSpeed recipe - inputs contain null");
            }
        } else {
            warn("ClusterBoosterSpeed item is null, skipping recipe!");
        }

        // --- 蒸汽节省增幅器（LV 档组装机配方）：LV 电路 + LV 电动泵 + 小钢流体管道 ---
        ItemStack steamSaverBooster = get(GTSRItemList.ClusterBoosterSteamSaver, 1);
        if (!hasNull(steamSaverBooster)) {
            ItemStack[] inputs = filterNulls(
                get(OrePrefixes.circuit, Materials.LV, 1),
                get(ItemList.Electric_Pump_LV, 1),
                get(OrePrefixes.pipeSmall, Materials.Steel, 1));
            if (!hasNull(inputs)) {
                GTValues.RA.stdBuilder()
                    .itemInputs(inputs)
                    .itemOutputs(steamSaverBooster)
                    .fluidInputs(Materials.SolderingAlloy.getMolten(2 * INGOTS))
                    .duration(20 * SECONDS)
                    .eut(TierEU.RECIPE_LV)
                    .addTo(assemblerRecipes);
            } else {
                warn("Skipped ClusterBoosterSteamSaver recipe - inputs contain null");
            }
        } else {
            warn("ClusterBoosterSteamSaver item is null, skipping recipe!");
        }

        // --- 热力离心单元（MV 档组装机配方）：MV 马达 + 钢转子 + 耐热板 + MV 电路 + 镀铜砖壳。
        // 耐热板：GT5U 无 plateHeatproof oredict（S0 核实），实际名目为耐热机器壳 ItemList.Casing_HeatProof
        // （殷钢制，GT5U ItemList.java:800 / BlockCasings1.java:41）---
        ItemStack thermalCentrifuge = get(GTSRItemList.ClusterUnitThermalCentrifuge, 1);
        if (!hasNull(thermalCentrifuge)) {
            ItemStack[] inputs = filterNulls(
                get(ItemList.Electric_Motor_MV, 1),
                get(OrePrefixes.rotor, Materials.Steel, 1),
                get(ItemList.Casing_HeatProof, 1),
                get(OrePrefixes.circuit, Materials.MV, 1),
                get(ItemList.Casing_BronzePlatedBricks, 1));
            if (!hasNull(inputs)) {
                GTValues.RA.stdBuilder()
                    .itemInputs(inputs)
                    .itemOutputs(thermalCentrifuge)
                    .fluidInputs(Materials.SolderingAlloy.getMolten(2 * INGOTS))
                    .duration(20 * SECONDS)
                    .eut(TierEU.RECIPE_MV)
                    .addTo(assemblerRecipes);
            } else {
                warn("Skipped ClusterUnitThermalCentrifuge recipe - inputs contain null");
            }
        } else {
            warn("ClusterUnitThermalCentrifuge item is null, skipping recipe!");
        }

        // --- 主产物增幅器（MV 档组装机配方）：MV 电路 + MV 传感器（GT5U 实际名目 ItemList.Sensor_MV，
        // 任务括注 Electric_Sensor_MV 不存在）+ 三重钢板 ---
        ItemStack primaryBooster = get(GTSRItemList.ClusterBoosterPrimary, 1);
        if (!hasNull(primaryBooster)) {
            ItemStack[] inputs = filterNulls(
                get(OrePrefixes.circuit, Materials.MV, 1),
                get(ItemList.Sensor_MV, 1),
                get(OrePrefixes.plateTriple, Materials.Steel, 1));
            if (!hasNull(inputs)) {
                GTValues.RA.stdBuilder()
                    .itemInputs(inputs)
                    .itemOutputs(primaryBooster)
                    .fluidInputs(Materials.SolderingAlloy.getMolten(2 * INGOTS))
                    .duration(20 * SECONDS)
                    .eut(TierEU.RECIPE_MV)
                    .addTo(assemblerRecipes);
            } else {
                warn("Skipped ClusterBoosterPrimary recipe - inputs contain null");
            }
        } else {
            warn("ClusterBoosterPrimary item is null, skipping recipe!");
        }

        // --- 副产物增幅器（MV 档组装机配方）：MV 电路 + MV 传送带模块（GT5U 实际名目
        // ItemList.Conveyor_Module_MV，任务括注 Electric_Conveyor_MV 不存在）+ 三重钢板 ---
        ItemStack secondaryBooster = get(GTSRItemList.ClusterBoosterSecondary, 1);
        if (!hasNull(secondaryBooster)) {
            ItemStack[] inputs = filterNulls(
                get(OrePrefixes.circuit, Materials.MV, 1),
                get(ItemList.Conveyor_Module_MV, 1),
                get(OrePrefixes.plateTriple, Materials.Steel, 1));
            if (!hasNull(inputs)) {
                GTValues.RA.stdBuilder()
                    .itemInputs(inputs)
                    .itemOutputs(secondaryBooster)
                    .fluidInputs(Materials.SolderingAlloy.getMolten(2 * INGOTS))
                    .duration(20 * SECONDS)
                    .eut(TierEU.RECIPE_MV)
                    .addTo(assemblerRecipes);
            } else {
                warn("Skipped ClusterBoosterSecondary recipe - inputs contain null");
            }
        } else {
            warn("ClusterBoosterSecondary item is null, skipping recipe!");
        }

        log("Cluster assembler recipes done.");
    }
}
