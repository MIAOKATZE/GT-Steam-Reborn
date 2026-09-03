package com.miaokatze.gtsr.loader.recipes;

import static com.miaokatze.gtsr.loader.recipes.RecipeLoaderUtils.get;
import static com.miaokatze.gtsr.loader.recipes.RecipeLoaderUtils.hasNull;
import static com.miaokatze.gtsr.loader.recipes.RecipeLoaderUtils.log;
import static com.miaokatze.gtsr.loader.recipes.RecipeLoaderUtils.warn;
import static gregtech.api.recipe.RecipeMaps.assemblerRecipes;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gtPlusPlus.xmod.gregtech.api.enums.GregtechItemList;

/**
 * 矿物处理集群 14 组件配方 v2（全组装机口径）——逐字端口用户 GTUDK 游戏内导出
 * （plan/配方/ 下 14 个 .java 模板，导出于 2026-09-04），v1.11.23 的 7 工作台 + 7 组装机配方整体弃用删除。
 *
 * <p>
 * 数值逐字忠实：duration 2400 ticks；eut 字面值 30/32/128 不换算 TierEU 常量（30/32 = LV 档、128 = MV 档；
 * 热力离心/筛选/磁选三个加工模块与主产物/副产物增幅为 MV，效率增幅 32 仍属 LV 档）；
 * 五个材料输入 chances 全部 10000（必消耗），编程电路以 .circuit(24) 复刻 GTUDK 的 (10000×5, 0) chances
 * 语义——GTUtility.getIntegratedCircuit 产出 stackSize=0 的配置电路（GT5U GTUtility.java:2717），
 * GTRecipe.consumeInput 对零量输入零消耗，即"槽内存在 24 号电路即匹配、永不消耗"。
 * 材料数量与奇点个数严格按导出：主控奇点 32、主/副产物增幅 32、其余 16。
 * </p>
 *
 * <p>
 * GTUDK 输出 meta - 15050 = MachineLoader 注册序（MetaTileEntityID BASE = 14700 + 相对 ID 351~364
 * = 15051~15064），产物一律 RecipeLoaderUtils.get(GTSRItemList.X, 1) 空防御获取。
 * 原料名目经 GT5U 5.09.54.20 权威源码核实（ItemList/OreDict 优先，运行时解析）：
 * 镀铜砖块 = ItemList.Casing_BronzePlatedBricks（gt.blockcasings:10，BlockCasings1.java:40）、
 * 隔热机械方块 = ItemList.Casing_HeatProof（:11，BlockCasings1.java:41）、
 * 青铜框架 = frameGt×Bronze、磁化钢框架 = frameGt×SteelMagnetic（gt.blockframes meta=材料号，
 * LoaderMetaPipeEntities.java:40-41 按 frameGt 矿词注册）、
 * 磁化钢块 = block×SteelMagnetic（gt.blockmetal6 数组序 14，LoaderGTBlockFluid.java:817-820）、
 * 青铜齿轮箱 = ItemList.Casing_Gearbox_Bronze（blockcasings2:2，BlockCasings2.java:41）、
 * 白铜线圈 = ItemList.Casing_Coil_Cupronickel（blockcasings5:0，BlockCasings5.java:66）、
 * 化学惰性机械方块 = ItemList.Casing_Chemically_Inert（blockcasings8:0，BlockCasings8.java:27）、
 * 机械臂（LV）= ItemList.Robot_Arm_LV（metaitem.01:32650，IDMetaItem01.java:342）、
 * 传送带（LV）= ItemList.Conveyor_Module_LV（metaitem.01:32630，IDMetaItem01.java:322）、
 * 青铜齿轮 = gearGt×Bronze、青铜转子 = rotor×Bronze（metaitem.02:31300/21300）、
 * 巨型青铜流体管道 = pipeHuge×Bronze（Bronze 流体管道 startId 5120 + huge 偏移 4 = meta 5124，
 * LoaderMetaPipeEntities.java:616-617）。
 * GT5U 机器：大型蒸汽研磨机/洗矿厂/离心机/熔炉 = gtPlusPlus GregtechItemList 的
 * Controller_SteamMaceratorMulti(31041)/SteamWasherMulti(31082)/SteamCentrifugeMulti(31080)/
 * SteamFurnaceMulti——GTUDK 熔炉条目 meta 14804 在 5.09.54.20 全库无注册，按同族研磨机/洗矿厂/离心机
 * meta 逐一吻合原则映射至 Steam Hearth（Steam Furnace Multi，MetaTileEntityIDs.java:1998），
 * 经 ItemList 运行时解析不依赖具体 meta；蒸汽锻造锤/进阶热力离心机/进阶筛选机/进阶两极磁化机
 * = ItemList.Machine_Bronze_Hammer(112)/Machine_MV_ThermalCentrifuge(382)/Machine_MV_Sifter(642)/
 * Machine_MV_Polarizer(552)。
 * </p>
 *
 * <p>
 * 外部依赖：miscutils（gtplusplus.blockcasings.2 工业筛选机机械方块/大型筛选格栅）与 etfuturum
 * （blast_furnace 高炉）不在编译期依赖内，经 GameRegistry.findItem 兜底获取；
 * 任一外部方块缺失时仅该条配方跳过注册并 warn，不影响其余配方注册。
 * </p>
 */
public final class ClusterRecipes {

    private ClusterRecipes() {}

    /** 配方注册入口：由 GTSRRecipeLoader 派发表经 safeRegister 调用（签名不得变更）。 */
    public static void addRecipes() {
        registerAssemblerRecipes();
    }

    private static void registerAssemblerRecipes() {
        log("Registering cluster assembler recipes...");
        registerModuleRecipes();
        registerBoosterAndLogisticsRecipes();
        log("Cluster assembler recipes done.");
    }

    /** 主控 + 7 加工模块（GTUDK plan/配方/ 主控与研磨/洗矿/离心/热力离心/筛选/磁选/熔炉加工模块）。 */
    private static void registerModuleRecipes() {
        // --- 主控（蒸汽动力矿物处理物流工程集群，15051，eut 30）：镀铜砖 64 + 青铜框架 64 + 奇点 32 +
        // LV 电路 32 + 巨型青铜流体管道 64 ---
        addClusterRecipe(
            "ClusterController",
            get(GTSRItemList.ClusterController, 1),
            30,
            get(ItemList.Casing_BronzePlatedBricks, 64),
            frame(Materials.Bronze, 64),
            singularity(32),
            circuit(Materials.LV, 32),
            get(OrePrefixes.pipeHuge, Materials.Bronze, 64));

        // --- 粉碎加工模块（15052，eut 30）：大型蒸汽研磨机 8 + 蒸汽锻造锤 8 + 青铜框架 32 + 青铜齿轮 64 +
        // 奇点 16 ---
        addClusterRecipe(
            "ClusterUnitCrusher",
            get(GTSRItemList.ClusterUnitCrusher, 1),
            30,
            steamMulti(GregtechItemList.Controller_SteamMaceratorMulti, 8),
            get(ItemList.Machine_Bronze_Hammer, 8),
            frame(Materials.Bronze, 32),
            gear(Materials.Bronze, 64),
            singularity(16));

        // --- 洗矿加工模块（15053，eut 30）：大型蒸汽洗矿厂 8 + LV 电路 16 + 青铜框架 32 + 青铜转子 64 +
        // 奇点 16 ---
        addClusterRecipe(
            "ClusterUnitOreWasher",
            get(GTSRItemList.ClusterUnitOreWasher, 1),
            30,
            steamMulti(GregtechItemList.Controller_SteamWasherMulti, 8),
            circuit(Materials.LV, 16),
            frame(Materials.Bronze, 32),
            rotor(Materials.Bronze, 64),
            singularity(16));

        // --- 离心加工模块（15054，eut 30）：大型蒸汽离心机 8 + LV 电路 16 + 青铜框架 32 + 青铜齿轮箱 32 +
        // 奇点 16 ---
        addClusterRecipe(
            "ClusterUnitCentrifuge",
            get(GTSRItemList.ClusterUnitCentrifuge, 1),
            30,
            steamMulti(GregtechItemList.Controller_SteamCentrifugeMulti, 8),
            circuit(Materials.LV, 16),
            frame(Materials.Bronze, 32),
            get(ItemList.Casing_Gearbox_Bronze, 32),
            singularity(16));

        // --- 热力离心加工模块（15055，eut 128 = MV）：进阶热力离心机 8 + MV 电路 16 + 隔热机械方块 32 +
        // 白铜线圈方块 32 + 奇点 16 ---
        addClusterRecipe(
            "ClusterUnitThermalCentrifuge",
            get(GTSRItemList.ClusterUnitThermalCentrifuge, 1),
            128,
            get(ItemList.Machine_MV_ThermalCentrifuge, 8),
            circuit(Materials.MV, 16),
            get(ItemList.Casing_HeatProof, 32),
            get(ItemList.Casing_Coil_Cupronickel, 32),
            singularity(16));

        // --- 筛选加工模块（15056，eut 128 = MV）：进阶筛选机 8 + MV 电路 16 + 工业筛选机机械方块 32 +
        // 大型筛选格栅 64 + 奇点 16（miscutils 两项缺失时整条跳过）---
        addClusterRecipe(
            "ClusterUnitSifter",
            get(GTSRItemList.ClusterUnitSifter, 1),
            128,
            get(ItemList.Machine_MV_Sifter, 8),
            circuit(Materials.MV, 16),
            findExternal("miscutils", "gtplusplus.blockcasings.2", 5, 32),
            findExternal("miscutils", "gtplusplus.blockcasings.2", 6, 64),
            singularity(16));

        // --- 磁选加工模块（15057，eut 128 = MV）：进阶两极磁化机 8 + MV 电路 16 + 磁化钢块 32 +
        // 磁化钢框架 64 + 奇点 16 ---
        addClusterRecipe(
            "ClusterUnitMagneticSeparator",
            get(GTSRItemList.ClusterUnitMagneticSeparator, 1),
            128,
            get(ItemList.Machine_MV_Polarizer, 8),
            circuit(Materials.MV, 16),
            get(OrePrefixes.block, Materials.SteelMagnetic, 32),
            frame(Materials.SteelMagnetic, 64),
            singularity(16));

        // --- 熔炼加工模块（15058，eut 30）：大型蒸汽熔炉 8 + LV 电路 16 + 青铜框架 32 + 高炉 64 +
        // 奇点 16（etfuturum 缺失时整条跳过）---
        addClusterRecipe(
            "ClusterUnitFurnace",
            get(GTSRItemList.ClusterUnitFurnace, 1),
            30,
            steamMulti(GregtechItemList.Controller_SteamFurnaceMulti, 8),
            circuit(Materials.LV, 16),
            frame(Materials.Bronze, 32),
            findExternal("etfuturum", "blast_furnace", 0, 64),
            singularity(16));
    }

    /** 5 增幅 + 物流模块（GTUDK plan/配方/ 并行/速度/效率/主产物/副产物增幅与物流模块）。 */
    private static void registerBoosterAndLogisticsRecipes() {
        // --- 并行增幅模块（15059，eut 30）：青铜框架 64 + 镀铜砖 32 + LV 电路 16 + 奇点 16 + 机械臂（LV）32 ---
        addClusterRecipe(
            "ClusterBoosterParallel",
            get(GTSRItemList.ClusterBoosterParallel, 1),
            30,
            frame(Materials.Bronze, 64),
            get(ItemList.Casing_BronzePlatedBricks, 32),
            circuit(Materials.LV, 16),
            singularity(16),
            get(ItemList.Robot_Arm_LV, 32));

        // --- 速度增幅模块（15060，eut 30）：青铜框架 64 + 镀铜砖 32 + LV 电路 16 + 奇点 16 + 传送带（LV）32 ---
        addClusterRecipe(
            "ClusterBoosterSpeed",
            get(GTSRItemList.ClusterBoosterSpeed, 1),
            30,
            frame(Materials.Bronze, 64),
            get(ItemList.Casing_BronzePlatedBricks, 32),
            circuit(Materials.LV, 16),
            singularity(16),
            get(ItemList.Conveyor_Module_LV, 32));

        // --- 效率增幅模块（15063，eut 32，LV 档字面值）：青铜框架 64 + 镀铜砖 32 + LV 电路 16 + 奇点 16 +
        // 青铜转子 64 ---
        addClusterRecipe(
            "ClusterBoosterSteamSaver",
            get(GTSRItemList.ClusterBoosterSteamSaver, 1),
            32,
            frame(Materials.Bronze, 64),
            get(ItemList.Casing_BronzePlatedBricks, 32),
            circuit(Materials.LV, 16),
            singularity(16),
            rotor(Materials.Bronze, 64));

        // --- 主产物增幅模块（15061，eut 128 = MV）：青铜框架 64 + 镀铜砖 32 + MV 电路 16 + 奇点 32 +
        // 青铜齿轮 64 ---
        addClusterRecipe(
            "ClusterBoosterPrimary",
            get(GTSRItemList.ClusterBoosterPrimary, 1),
            128,
            frame(Materials.Bronze, 64),
            get(ItemList.Casing_BronzePlatedBricks, 32),
            circuit(Materials.MV, 16),
            singularity(32),
            gear(Materials.Bronze, 64));

        // --- 副产物增幅模块（15062，eut 128 = MV）：青铜框架 64 + 镀铜砖 32 + MV 电路 16 + 奇点 32 +
        // 化学惰性机械方块 32 ---
        addClusterRecipe(
            "ClusterBoosterSecondary",
            get(GTSRItemList.ClusterBoosterSecondary, 1),
            128,
            frame(Materials.Bronze, 64),
            get(ItemList.Casing_BronzePlatedBricks, 32),
            circuit(Materials.MV, 16),
            singularity(32),
            get(ItemList.Casing_Chemically_Inert, 32));

        // --- 物流模块（15064，eut 30）：青铜框架 64 + 镀铜砖 32 + LV 电路 16 + 奇点 16 + 青铜齿轮 32 ---
        addClusterRecipe(
            "ClusterUnitLogistics",
            get(GTSRItemList.ClusterUnitLogistics, 1),
            30,
            frame(Materials.Bronze, 64),
            get(ItemList.Casing_BronzePlatedBricks, 32),
            circuit(Materials.LV, 16),
            singularity(16),
            gear(Materials.Bronze, 32));
    }

    /**
     * 组装机配方统一落入口：五个材料输入 chances 全 10000（builder 默认），24 号编程电路经 .circuit(24)
     * 附加为末位输入（存在即匹配、不消耗），duration 2400 ticks、eut 字面值（30/32 = LV，128 = MV）；
     * 产物或任一输入为 null 时整条跳过并 warn。
     */
    private static void addClusterRecipe(String name, ItemStack output, int eut, ItemStack... inputs) {
        if (output == null) {
            warn(name + " item is null, skipping recipe!");
            return;
        }
        if (hasNull(inputs)) {
            warn("Skipped " + name + " recipe - inputs contain null");
            return;
        }
        GTValues.RA.stdBuilder()
            .itemInputs(inputs)
            .circuit(24)
            .itemOutputs(output)
            .duration(2400)
            .eut(eut)
            .addTo(assemblerRecipes);
    }

    /** frameGt 矿词框架（青铜框架 = gt.blockframes:300，磁化钢框架 = :355，meta = 材料号）。 */
    private static ItemStack frame(Materials material, int amount) {
        return get(OrePrefixes.frameGt, material, amount);
    }

    /** circuit 矿词电路（LV = circuitBasic / MV = circuitGood，任一匹配矿词物品皆可，镜像 GTUDK 矿词语义）。 */
    private static ItemStack circuit(Materials tier, int amount) {
        return get(OrePrefixes.circuit, tier, amount);
    }

    /** gearGt 矿词齿轮（青铜齿轮 = gt.metaitem.02:31300）。 */
    private static ItemStack gear(Materials material, int amount) {
        return get(OrePrefixes.gearGt, material, amount);
    }

    /** rotor 矿词转子（青铜转子 = gt.metaitem.02:21300）。 */
    private static ItemStack rotor(Materials material, int amount) {
        return get(OrePrefixes.rotor, material, amount);
    }

    /** 蒸汽纠缠奇点（本 mod gtsr:SteamEntangledSingularity，主控/主/副产物 32、其余 16）。 */
    private static ItemStack singularity(int amount) {
        return get(GTSRItemList.SteamEntangledSingularity, amount);
    }

    /**
     * GT5U 大型蒸汽多方块控制器（gtPlusPlus GregtechItemList，运行时解析）：研磨机 31041 / 洗矿厂 31082 /
     * 离心机 31080 / 熔炉 Steam Hearth（GTUDK 熔炉 meta 14804 在 5.09.54.20 无对应注册，见类 javadoc）。
     */
    private static ItemStack steamMulti(GregtechItemList item, int amount) {
        ItemStack stack = item.get(amount);
        if (stack == null) {
            warn("GregtechItemList." + item.name() + " returned null!");
        }
        return stack;
    }

    /**
     * 外部 mod 方块 findItem 兜底（miscutils/etfuturum 非编译期依赖）：物品未注册时返回 null 并 warn，
     * 由 addClusterRecipe 跳过该条配方，与仓内 null 防御惯例一致。
     */
    private static ItemStack findExternal(String modId, String itemName, int meta, int amount) {
        Item item = GameRegistry.findItem(modId, itemName);
        if (item == null) {
            warn("External item " + modId + ":" + itemName + " not found, its cluster recipe will be skipped!");
            return null;
        }
        return new ItemStack(item, amount, meta);
    }
}
