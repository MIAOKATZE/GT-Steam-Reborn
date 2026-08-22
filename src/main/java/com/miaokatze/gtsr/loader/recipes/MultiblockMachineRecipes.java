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
import net.minecraftforge.fluids.FluidRegistry;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;

import bartworks.system.material.WerkstoffLoader;
import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.enums.TierEU;
import gregtech.api.util.GTModHandler;
import gtPlusPlus.core.material.MaterialsAlloy;

/**
 * 多方块工作台与组装机配方（SR-A03 组三，自 GTSRRecipeLoader 门面原样迁出）：
 * 12 台机器工作台配方 + 组装机配方。原 404 行单方法 registerMultiblockAssemblerRecipes
 * 按「装配线机器/GTUDK 机器/轮机阵列/升级芯片/超压节点/奇点仓/存储与输入仓」七段切成
 * 私有方法（不建新类），段调用顺序与原方法体行序逐段一致 = 配方注册顺序零变化；
 * 工作台方法体逐字未动。
 */
public final class MultiblockMachineRecipes {

    private MultiblockMachineRecipes() {}

    public static void registerMultiblockAssemblerRecipes() {
        log("Registering multiblock assembler recipes...");
        registerAssemblerLineMachines();
        registerAssemblerGtudkMachines();
        registerAssemblerTurbineAndKinetic();
        registerAssemblerUpgradeChips();
        registerAssemblerOverpressureCacheNodes();
        registerAssemblerSingularityCompartments();
        registerAssemblerHubStorageAndTurbineHatch();
        log("Multiblock assembler recipes done.");
    }

    /** 装配线机器段（SR-A03-R2 分段，原 404 行单方法内部按注释块切分）：热解机/氨厂/平炉/钻井枢纽/纠缠装置。 */
    private static void registerAssemblerLineMachines() {
        GTValues.RA.stdBuilder()
            .itemInputs(
                get(OrePrefixes.frameGt, Materials.Bronze, 4),
                ItemList.Casing_Firebox_Bronze.get(8),
                get(OrePrefixes.gearGt, Materials.Bronze, 4),
                get(OrePrefixes.circuit, Materials.LV, 4),
                get(OrePrefixes.plateTriple, Materials.Bronze, 12))
            .itemOutputs(GTSRItemList.VeinSteamPyrolyzer.get(1))
            .fluidInputs(Materials.SolderingAlloy.getMolten(288))
            .duration(30 * SECONDS)
            .eut(TierEU.RECIPE_LV)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                ItemList.Machine_Multi_LargeChemicalReactor.get(2),
                get(OrePrefixes.frameGt, Materials.Steel, 4),
                ItemList.Casing_Firebox_Steel.get(6),
                get(OrePrefixes.frameGt, Materials.Polytetrafluoroethylene, 8),
                ItemList.Casing_Pipe_Polytetrafluoroethylene.get(8),
                get(OrePrefixes.plateDense, Materials.StainlessSteel, 8),
                get(OrePrefixes.circuit, Materials.HV, 6),
                ItemList.Electric_Pump_HV.get(12))
            .itemOutputs(GTSRItemList.AmmoniaPlant.get(1))
            .fluidInputs(Materials.SolderingAlloy.getMolten(1728))
            .duration(720 * SECONDS)
            .eut(TierEU.RECIPE_HV)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                get(OrePrefixes.frameGt, Materials.Steel, 4),
                ItemList.Casing_Firebox_Steel.get(4),
                get(OrePrefixes.plateDense, Materials.Steel, 12),
                ItemList.Casing_Firebricks.get(32),
                get(OrePrefixes.circuit, Materials.LV, 2),
                get(OrePrefixes.pipeLarge, Materials.Steel, 4))
            .itemOutputs(GTSRItemList.SiemensMartinFurnace.get(1))
            .fluidInputs(Materials.SolderingAlloy.getMolten(288))
            .duration(120 * SECONDS)
            .eut(TierEU.RECIPE_LV)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTSRItemList.SteamEntangledSingularity.get(16),
                get(OrePrefixes.frameGt, Materials.Steel, 8),
                get(OrePrefixes.plateTriple, Materials.Steel, 16),
                ItemList.Sensor_LV.get(12),
                ItemList.Electric_Pump_LV.get(8),
                ItemList.Conveyor_Module_LV.get(8))
            .itemOutputs(GTSRItemList.SingularityDrillingHub.get(1))
            .fluidInputs(Materials.SolderingAlloy.getMolten(1152))
            .duration(60 * SECONDS)
            .eut(TierEU.RECIPE_LV)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                get(OrePrefixes.frameGt, Materials.Steel, 8),
                get(OrePrefixes.plateTriple, Materials.Steel, 16),
                get(OrePrefixes.circuit, Materials.LV, 4),
                ItemList.Electric_Piston_LV.get(12),
                get(OrePrefixes.pipeHuge, Materials.Steel, 4),
                get(OrePrefixes.plate, Materials.Obsidian, 32))
            .itemOutputs(GTSRItemList.SteamSingularityEntangler.get(1))
            .fluidInputs(Materials.SolderingAlloy.getMolten(2304))
            .duration(60 * SECONDS)
            .eut(TierEU.RECIPE_LV)
            .addTo(assemblerRecipes);
    }

    /** GTUDK 机器段：临界纠缠奇点稳定装置/致密态操纵装置（带 null 防御整体跳过语义）。 */
    private static void registerAssemblerGtudkMachines() {
        // --- 临界蒸汽纠缠奇点稳定装置 (GTUDK 配方：替换原 LV 配方，IV 级) ---
        ItemStack cscOut = get(GTSRItemList.CriticalSingularityCompressor, 1);
        if (!hasNull(cscOut)) {
            ItemStack[] cscInputs = filterNulls(
                new ItemStack(GameRegistry.findItem("gregtech", "gt.blockcasings8"), 64, 6),
                new ItemStack(GameRegistry.findItem("gregtech", "bw.frames"), 64, 88),
                new ItemStack(GameRegistry.findItem("gregtech", "gt.blockframes"), 32, 70),
                get(OrePrefixes.circuit, Materials.UV, 16),
                new ItemStack(GameRegistry.findItem("gregtech", "gt.blockcasings4"), 32, 7),
                new ItemStack(GameRegistry.findItem("gregtech", "gt.blockcasings4"), 32, 6),
                GTSRItemList.SteamEntangledSingularity.get(64),
                GTSRItemList.SteamEntangledSingularity.get(64),
                GTSRItemList.SteamEntangledSingularity.get(64));
            if (!hasNull(cscInputs)) {
                GTValues.RA.stdBuilder()
                    .itemInputs(cscInputs)
                    .itemOutputs(cscOut)
                    .fluidInputs(FluidRegistry.getFluidStack("molten.indalloy140", 32000))
                    .duration(4800)
                    .eut(32768)
                    .addTo(assemblerRecipes);
            } else {
                warn("Skipped CriticalSingularityCompressor recipe - inputs contain null");
            }
        } else {
            warn("Skipped CriticalSingularityCompressor recipe - output is null");
        }

        // --- 致密态操纵装置 (GTUDK 配方：替换原 LV 配方，IV 级) ---
        ItemStack dsmOut = get(GTSRItemList.DenseStateManipulator, 1);
        if (!hasNull(dsmOut)) {
            ItemStack[] dsmInputs = filterNulls(
                new ItemStack(GameRegistry.findItem("gregtech", "gt.blockcasings8"), 64, 6),
                new ItemStack(GameRegistry.findItem("gregtech", "bw.frames"), 64, 88),
                new ItemStack(GameRegistry.findItem("bartworks", "BW_TieredGlass"), 64, 3),
                get(OrePrefixes.circuit, Materials.UV, 16),
                new ItemStack(GameRegistry.findItem("gregtech", "gt.blockcasings4"), 64, 7),
                new ItemStack(GameRegistry.findItem("gregtech", "gt.metaitem.01"), 16, 32645),
                GTSRItemList.SteamEntangledSingularity.get(64),
                GTSRItemList.SteamEntangledSingularity.get(64),
                GTSRItemList.SteamEntangledSingularity.get(64));
            if (!hasNull(dsmInputs)) {
                GTValues.RA.stdBuilder()
                    .itemInputs(dsmInputs)
                    .itemOutputs(dsmOut)
                    .fluidInputs(FluidRegistry.getFluidStack("molten.indalloy140", 32000))
                    .duration(4800)
                    .eut(32768)
                    .addTo(assemblerRecipes);
            } else {
                warn("Skipped DenseStateManipulator recipe - inputs contain null");
            }
        } else {
            warn("Skipped DenseStateManipulator recipe - output is null");
        }
    }

    /** 轮机与阵列段：巨型轮机阵列/动能处理阵列/地壳物质聚合器。 */
    private static void registerAssemblerTurbineAndKinetic() {
        GTValues.RA.stdBuilder()
            .itemInputs(
                GTSRItemList.SteamEntangledSingularity.get(24),
                get(OrePrefixes.frameGt, Materials.Steel, 12),
                get(OrePrefixes.plateTriple, Materials.Steel, 24),
                get(OrePrefixes.circuit, Materials.LV, 8),
                ItemList.Electric_Piston_LV.get(12),
                get(OrePrefixes.rotor, Materials.Steel, 16))
            .itemOutputs(GTSRItemList.MegaSteamTurbineArray.get(1))
            .fluidInputs(Materials.SolderingAlloy.getMolten(1152))
            .duration(45 * SECONDS)
            .eut(TierEU.RECIPE_LV)
            .addTo(assemblerRecipes);

        ItemStack paOut = get(GTSRItemList.KineticProcessingArray, 1);
        if (!hasNull(paOut)) {
            ItemStack[] inputs = filterNulls(
                GTSRItemList.SteamEntangledSingularity.get(8),
                get(OrePrefixes.frameGt, Materials.Steel, 4),
                get(OrePrefixes.circuit, Materials.LV, 4),
                ItemList.Electric_Motor_LV.get(4),
                ItemList.Conveyor_Module_LV.get(4),
                ItemList.Electric_Pump_LV.get(4));
            GTValues.RA.stdBuilder()
                .itemInputs(inputs)
                .itemOutputs(paOut)
                .fluidInputs(Materials.SolderingAlloy.getMolten(16 * INGOTS))
                .duration(60 * SECONDS)
                .eut(TierEU.RECIPE_LV)
                .addTo(assemblerRecipes);
        } else {
            warn("Skipped KineticProcessingArray recipe - output is null");
        }

        ItemStack voidBorerOut = get(GTSRItemList.CrustMatterAggregator, 1);
        if (!hasNull(voidBorerOut)) {
            ItemStack[] inputs = filterNulls(
                GTSRItemList.SteamEntangledSingularity.get(8),
                GTSRItemList.CrustSteamBorer.get(1),
                get(OrePrefixes.plateTriple, Materials.Steel, 8),
                get(OrePrefixes.frameGt, Materials.Steel, 2),
                get(OrePrefixes.circuit, Materials.LV, 2),
                ItemList.Electric_Piston_LV.get(4));
            GTValues.RA.stdBuilder()
                .itemInputs(inputs)
                .itemOutputs(voidBorerOut)
                .fluidInputs(Materials.SolderingAlloy.getMolten(4 * INGOTS))
                .duration(30 * SECONDS)
                .eut(TierEU.RECIPE_LV)
                .addTo(assemblerRecipes);
        } else {
            warn("Skipped CrustMatterAggregator recipe - output is null");
        }
    }

    /** 升级芯片段：强化奇点枢纽芯片/轮机循环超限芯片（后者不用 filterNulls，缺失整体跳过）。 */
    private static void registerAssemblerUpgradeChips() {
        // --- 强化奇点枢纽升级芯片 ---
        ItemStack reinforcedChipOut = get(GTSRItemList.ReinforcedHubSingularityChip, 1);
        if (!hasNull(reinforcedChipOut)) {
            ItemStack[] inputs = filterNulls(
                GTSRItemList.SteamEntangledSingularity.get(64),
                GTSRItemList.SteamEntangledSingularity.get(64),
                GTSRItemList.SteamEntangledSingularity.get(64),
                GTSRItemList.SteamEntangledSingularity.get(64),
                GTSRItemList.SteamEntangledSingularity.get(64),
                GTSRItemList.SteamEntangledSingularity.get(64),
                get(OrePrefixes.circuit, Materials.UV, 16),
                get(OrePrefixes.plateDense, Materials.Europium, 32),
                get(OrePrefixes.plateDense, WerkstoffLoader.RhodiumPlatedPalladium.getGTMaterial(), 64));
            if (!hasNull(inputs)) {
                GTValues.RA.stdBuilder()
                    .itemInputs(inputs)
                    .itemOutputs(reinforcedChipOut)
                    .fluidInputs(Materials.Radon.getGas(128000))
                    .duration(960 * SECONDS)
                    .eut(TierEU.RECIPE_LuV)
                    .addTo(assemblerRecipes);
            } else {
                warn("Skipped ReinforcedHubSingularityChip recipe - inputs contain null");
            }
        } else {
            warn("Skipped ReinforcedHubSingularityChip recipe - output is null");
        }

        // --- 蒸汽轮机循环超限芯片（GTUDK 模板：7 项物料，IV 级 12000t/131072EU/t）---
        ItemStack turbineChipOut = get(GTSRItemList.SteamTurbineCycleOverlimitChip, 1);
        if (!hasNull(turbineChipOut)) {
            // 注意：不用 filterNulls —— 任一物料缺失（如 GoodGenerator 未安装）应整体跳过，而不是降配注册
            ItemStack[] inputs = { get(OrePrefixes.circuit, Materials.UV, 64), // 模板 gt.metaitem.01:22070（UV
                                                                               // 电路）→ 任意 UV 电路（OreDict）
                new ItemStack(GameRegistry.findItem("GoodGenerator", "compactFusionCoil"), 32, 2),
                GTSRItemList.CriticalSteamEntangledSingularity.get(64),
                new ItemStack(GameRegistry.findItem("gregtech", "gt.metaitem.01"), 32, 32677), // 力场发生器 VIII（GT5U lang
                                                                                               // 已确认）
                new ItemStack(GameRegistry.findItem("gregtech", "gt.metaitem.01"), 64, 32616) }; // 电动泵 ZPM（GT5U lang
                                                                                                 // 已确认）
            if (!hasNull(inputs)) {
                GTValues.RA.stdBuilder()
                    .itemInputs(inputs)
                    .itemOutputs(turbineChipOut)
                    .fluidInputs(FluidRegistry.getFluidStack("oganesson", 1000))
                    .duration(12000)
                    .eut(131072)
                    .addTo(assemblerRecipes);
            } else {
                warn("Skipped SteamTurbineCycleOverlimitChip recipe - inputs contain null");
            }
        } else {
            warn("Skipped SteamTurbineCycleOverlimitChip recipe - output is null");
        }
    }

    /** 超压缓存节点段：超压蒸汽/超压通用流体缓存节点（下位节点升级式）。 */
    private static void registerAssemblerOverpressureCacheNodes() {
        // --- 超压蒸汽缓存节点 ---
        ItemStack overpressureCacheOut = get(GTSRItemList.OverpressureSteamCacheNode, 1);
        if (!hasNull(overpressureCacheOut)) {
            ItemStack[] inputs = filterNulls(
                GTSRItemList.CriticalSteamEntangledSingularity.get(16),
                GTSRItemList.ReinforcedSteamCacheNode.get(1),
                get(OrePrefixes.circuit, Materials.LuV, 4),
                ItemList.Sensor_LuV.get(2),
                get(OrePrefixes.screw, WerkstoffLoader.RhodiumPlatedPalladium.getGTMaterial(), 64),
                get(OrePrefixes.plateDense, WerkstoffLoader.RhodiumPlatedPalladium.getGTMaterial(), 16));
            if (!hasNull(inputs)) {
                GTValues.RA.stdBuilder()
                    .itemInputs(inputs)
                    .circuit(1)
                    .itemOutputs(overpressureCacheOut)
                    .duration(90 * SECONDS)
                    .eut(TierEU.RECIPE_LuV)
                    .addTo(assemblerRecipes);
            } else {
                warn("Skipped OverpressureSteamCacheNode recipe - inputs contain null");
            }
        } else {
            warn("Skipped OverpressureSteamCacheNode recipe - output is null");
        }

        // --- 超压通用流体缓存节点（镜像超压蒸汽缓存节点：下位节点升级式组装机配方） ---
        ItemStack overpressureWaterCacheOut = get(GTSRItemList.OverpressureWaterCacheNode, 1);
        if (!hasNull(overpressureWaterCacheOut)) {
            ItemStack[] inputs = filterNulls(
                GTSRItemList.CriticalSteamEntangledSingularity.get(16),
                GTSRItemList.ReinforcedWaterCacheNode.get(1),
                get(OrePrefixes.circuit, Materials.LuV, 4),
                ItemList.Sensor_LuV.get(2),
                get(OrePrefixes.screw, WerkstoffLoader.RhodiumPlatedPalladium.getGTMaterial(), 64),
                get(OrePrefixes.plateDense, WerkstoffLoader.RhodiumPlatedPalladium.getGTMaterial(), 16));
            if (!hasNull(inputs)) {
                GTValues.RA.stdBuilder()
                    .itemInputs(inputs)
                    .circuit(1)
                    .itemOutputs(overpressureWaterCacheOut)
                    .duration(90 * SECONDS)
                    .eut(TierEU.RECIPE_LuV)
                    .addTo(assemblerRecipes);
            } else {
                warn("Skipped OverpressureWaterCacheNode recipe - inputs contain null");
            }
        } else {
            warn("Skipped OverpressureWaterCacheNode recipe - output is null");
        }
    }

    /** 奇点仓四件套段（GTUDK 组装机配方，电路 1/2 区分输入输出仓）。 */
    private static void registerAssemblerSingularityCompartments() {
        // --- 奇点仓四件套（GTUDK 组装机配方） ---
        ItemStack[] singularityInputs = filterNulls(
            GTSRItemList.ReinforcedWaterCacheNode.get(1),
            GTSRItemList.SteamEntangledSingularity.get(24),
            ItemList.Sensor_LV.get(12),
            get(OrePrefixes.circuit, Materials.LV, 24),
            get(OrePrefixes.plate, Materials.PulsatingIron, 48));
        ItemStack singularitySteamCompartment = get(GTSRItemList.SingularityFluidInputCompartment, 1);
        if (!hasNull(singularityInputs) && !hasNull(singularitySteamCompartment)) GTValues.RA.stdBuilder()
            .itemInputs(singularityInputs)
            .circuit(1)
            .itemOutputs(singularitySteamCompartment)
            .fluidInputs(Materials.SolderingAlloy.getMolten(1144))
            .duration(3200)
            .eut(TierEU.RECIPE_LV)
            .addTo(assemblerRecipes);
        ItemStack[] singularitySteamOutputInputs = filterNulls(
            GTSRItemList.ReinforcedWaterCacheNode.get(1),
            GTSRItemList.SteamEntangledSingularity.get(24),
            ItemList.Emitter_LV.get(12),
            get(OrePrefixes.circuit, Materials.LV, 24),
            get(OrePrefixes.plate, Materials.PulsatingIron, 48));
        ItemStack singularitySteamOutputCompartment = get(GTSRItemList.SingularityFluidOutputCompartment, 1);
        if (!hasNull(singularitySteamOutputInputs) && !hasNull(singularitySteamOutputCompartment))
            GTValues.RA.stdBuilder()
                .itemInputs(singularitySteamOutputInputs)
                .circuit(2)
                .itemOutputs(singularitySteamOutputCompartment)
                .fluidInputs(Materials.SolderingAlloy.getMolten(1144))
                .duration(3200)
                .eut(TierEU.RECIPE_LV)
                .addTo(assemblerRecipes);
        ItemStack[] singularityFluidInputs = filterNulls(
            GTSRItemList.ReinforcedSteamCacheNode.get(1),
            GTSRItemList.SteamEntangledSingularity.get(16),
            ItemList.Sensor_LV.get(8),
            get(OrePrefixes.circuit, Materials.LV, 16),
            get(OrePrefixes.plateTriple, Materials.Steel, 32));
        ItemStack singularityFluidInputCompartment = get(GTSRItemList.SingularitySteamCompartment, 1);
        if (!hasNull(singularityFluidInputs) && !hasNull(singularityFluidInputCompartment)) GTValues.RA.stdBuilder()
            .itemInputs(singularityFluidInputs)
            .circuit(1)
            .itemOutputs(singularityFluidInputCompartment)
            .fluidInputs(Materials.SolderingAlloy.getMolten(1144))
            .duration(3200)
            .eut(TierEU.RECIPE_LV)
            .addTo(assemblerRecipes);
        ItemStack[] singularityFluidOutputInputs = filterNulls(
            GTSRItemList.ReinforcedSteamCacheNode.get(1),
            GTSRItemList.SteamEntangledSingularity.get(16),
            ItemList.Emitter_LV.get(8),
            get(OrePrefixes.circuit, Materials.LV, 16),
            get(OrePrefixes.plateTriple, Materials.Steel, 32));
        ItemStack singularityFluidOutputCompartment = get(GTSRItemList.SingularitySteamOutputCompartment, 1);
        if (!hasNull(singularityFluidOutputInputs) && !hasNull(singularityFluidOutputCompartment))
            GTValues.RA.stdBuilder()
                .itemInputs(singularityFluidOutputInputs)
                .circuit(2)
                .itemOutputs(singularityFluidOutputCompartment)
                .fluidInputs(Materials.SolderingAlloy.getMolten(1144))
                .duration(3200)
                .eut(TierEU.RECIPE_LV)
                .addTo(assemblerRecipes);
    }

    /** 存储与输入仓段：超压枢纽存储单元/超压巨型轮机阵列输入仓。 */
    private static void registerAssemblerHubStorageAndTurbineHatch() {
        // --- 超压枢纽存储单元 ---
        ItemStack overpressureHubOut = get(GTSRItemList.OverpressureHubStorageUnit, 1);
        if (!hasNull(overpressureHubOut)) {
            ItemStack[] inputs = filterNulls(
                GTSRItemList.SteamEntangledSingularity.get(16),
                GTSRItemList.ReinforcedHubStorageUnit.get(1),
                ItemList.Super_Tank_IV.get(1),
                get(OrePrefixes.screw, Materials.TungstenSteel, 16),
                get(OrePrefixes.plateDense, Materials.TungstenSteel, 4));
            if (!hasNull(inputs)) {
                GTValues.RA.stdBuilder()
                    .itemInputs(inputs)
                    .itemOutputs(overpressureHubOut)
                    .duration(80 * SECONDS)
                    .eut(TierEU.RECIPE_IV)
                    .addTo(assemblerRecipes);
            } else {
                warn("Skipped OverpressureHubStorageUnit recipe - inputs contain null");
            }
        } else {
            warn("Skipped OverpressureHubStorageUnit recipe - output is null");
        }

        // --- 超压巨型轮机阵列输入仓 ---
        ItemStack overpressureTurbineOut = get(GTSRItemList.OverpressureTurbineInputHatch, 1);
        if (!hasNull(overpressureTurbineOut)) {
            ItemStack[] inputs = filterNulls(
                GTSRItemList.SteamEntangledSingularity.get(64),
                GTSRItemList.SteamEntangledSingularity.get(64),
                GTSRItemList.PressureSteamHatch.get(1),
                ItemList.Quantum_Tank_LV.get(1),
                ItemList.Electric_Pump_LuV.get(16),
                get(OrePrefixes.screw, WerkstoffLoader.RhodiumPlatedPalladium.getGTMaterial(), 16),
                get(OrePrefixes.plateDense, WerkstoffLoader.RhodiumPlatedPalladium.getGTMaterial(), 4));
            if (!hasNull(inputs)) {
                GTValues.RA.stdBuilder()
                    .itemInputs(inputs)
                    .itemOutputs(overpressureTurbineOut)
                    .fluidInputs(Materials.SolderingAlloy.getMolten(2880))
                    .duration(360 * SECONDS)
                    .eut(TierEU.RECIPE_LuV)
                    .addTo(assemblerRecipes);
            } else {
                warn("Skipped OverpressureTurbineInputHatch recipe - inputs contain null");
            }
        } else {
            warn("Skipped OverpressureTurbineInputHatch recipe - output is null");
        }
    }

    public static void registerMultiblockWorkbenchRecipes() {
        log("Registering multiblock workbench recipes...");

        ItemStack copperPlatedBrick = ItemList.Casing_BronzePlatedBricks.get(1);
        ItemStack roseGoldFrame = MaterialsAlloy.TUMBAGA.getFrameBox(1);
        if (roseGoldFrame == null) {
            warn("MaterialsAlloy.TUMBAGA.getFrameBox(1) returned null! Falling back to RoseGold frame.");
            roseGoldFrame = get(OrePrefixes.frameGt, Materials.RoseGold, 1);
        }
        ItemStack efrBlastFurnace = GTModHandler.getModItem("etfuturum", "blast_furnace", 1);
        if (efrBlastFurnace == null) {
            warn("EFR blast_furnace (etfuturum) is null!");
        }
        ItemStack piston = new ItemStack(
            net.minecraft.init.Blocks.piston,
            1,
            net.minecraftforge.oredict.OreDictionary.WILDCARD_VALUE);
        ItemStack stickyPiston = new ItemStack(
            net.minecraft.init.Blocks.sticky_piston,
            1,
            net.minecraftforge.oredict.OreDictionary.WILDCARD_VALUE);
        ItemStack glass = new ItemStack(
            net.minecraft.init.Blocks.glass,
            1,
            net.minecraftforge.oredict.OreDictionary.WILDCARD_VALUE);
        ItemStack brickBlock = new ItemStack(
            net.minecraft.init.Blocks.brick_block,
            1,
            net.minecraftforge.oredict.OreDictionary.WILDCARD_VALUE);

        GTModHandler.addCraftingRecipe(
            GTSRItemList.AirCompressor.get(1),
            GTModHandler.RecipeBits.BITSD,
            new Object[] { "ABA", "CDC", "EFE", 'A', copperPlatedBrick, 'B', "pipeHugeBronze", 'C', piston, 'D',
                roseGoldFrame, 'E', "gearGtBronze", 'F', "gearGtBronze" });

        GTModHandler.addCraftingRecipe(
            GTSRItemList.AtmosphericCentrifuge.get(1),
            GTModHandler.RecipeBits.BITSD,
            new Object[] { "ABA", "CDC", "BBB", 'A', copperPlatedBrick, 'B', "pipeHugeBronze", 'C', "gearGtBronze", 'D',
                roseGoldFrame });

        GTModHandler.addCraftingRecipe(
            GTSRItemList.CrustSteamBorer.get(1),
            GTModHandler.RecipeBits.BITSD,
            new Object[] { "ABA", "CDC", "CEC", 'A', copperPlatedBrick, 'B', "gearGtBronze", 'C', "gearGtBronze", 'D',
                roseGoldFrame, 'E', "pipeLargeBronze" });

        GTModHandler.addCraftingRecipe(
            GTSRItemList.GearSteamCompressor.get(1),
            GTModHandler.RecipeBits.BITSD,
            new Object[] { "ABA", "CDC", "CEC", 'A', copperPlatedBrick, 'B', "pipeLargeBronze", 'C', piston, 'D',
                roseGoldFrame, 'E', "pipeHugeBronze" });

        // 点2修复：RC 焦炉在 GTNH 2.9.0 已禁用，改用 GT5U 新添加的 CokeOvenController（焦炉控制器）
        GTModHandler.addCraftingRecipe(
            GTSRItemList.LargeCokeOven.get(1),
            GTModHandler.RecipeBits.BITSD,
            new Object[] { "ABA", "CDC", "ABA", 'A', copperPlatedBrick, 'B', brickBlock, 'C', efrBlastFurnace, 'D',
                ItemList.CokeOvenController.get(1) });

        GTModHandler.addCraftingRecipe(
            GTSRItemList.LargeGeothermalSteamBoiler.get(1),
            GTModHandler.RecipeBits.BITSD,
            new Object[] { "ABA", "CDC", "ABA", 'A', copperPlatedBrick, 'B', "pipeHugeBronze", 'C', efrBlastFurnace,
                'D', ItemList.Machine_Steel_Boiler_Lava.get(1) });

        GTModHandler.addCraftingRecipe(
            GTSRItemList.LargeSteamFurnace.get(1),
            GTModHandler.RecipeBits.BITSD,
            new Object[] { "ABA", "CDC", "ABA", 'A', copperPlatedBrick, 'B', ItemList.Casing_Firebox_Bronze.get(1), 'C',
                efrBlastFurnace, 'D', roseGoldFrame });

        GTModHandler.addCraftingRecipe(
            GTSRItemList.SteamFluidDrill.get(1),
            GTModHandler.RecipeBits.BITSD,
            new Object[] { "ABA", "CDC", "BEB", 'A', copperPlatedBrick, 'B', "gearGtBronze", 'C',
                get(OrePrefixes.frameGt, Materials.Bronze, 1), 'D', roseGoldFrame, 'E', "pipeMediumBronze" });

        GTModHandler.addCraftingRecipe(
            GTSRItemList.LargeSolarOverpressureArray.get(1),
            GTModHandler.RecipeBits.BITSD,
            new Object[] { "AAA", "BDB", "BEB", 'A', glass, 'B', copperPlatedBrick, 'D', roseGoldFrame, 'E',
                get(OrePrefixes.block, Materials.Silver, 1) });

        GTModHandler.addCraftingRecipe(
            GTSRItemList.SteamHubArray.get(1),
            GTModHandler.RecipeBits.BITSD,
            new Object[] { "ABA", "CDC", "EBE", 'A', copperPlatedBrick, 'B', "pipeHugeBronze", 'C',
                get(OrePrefixes.frameGt, Materials.Bronze, 1), 'D', roseGoldFrame, 'E', "plateTripleBronze" });

        GTModHandler.addCraftingRecipe(
            GTSRItemList.WaterHubArray.get(1),
            GTModHandler.RecipeBits.BITSD,
            new Object[] { "ABA", "CDC", "EEE", 'A', copperPlatedBrick, 'B', "plateTripleBronze", 'C',
                "plateTripleBronze", 'D', roseGoldFrame, 'E', "pipeMediumBronze" });

        log("Multiblock workbench recipes done.");
    }
}
