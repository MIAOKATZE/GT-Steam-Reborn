package com.miaokatze.gtsr.common.machine.tcds;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.isAir;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.HatchElement.InputHatch;
import static gregtech.api.enums.HatchElement.Maintenance;
import static gregtech.api.enums.HatchElement.Muffler;
import static gregtech.api.enums.HatchElement.OutputHatch;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_LARGE_BOILER;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_LARGE_BOILER_ACTIVE;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_LARGE_BOILER_ACTIVE_GLOW;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_LARGE_BOILER_GLOW;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;

import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;
import com.gtnewhorizon.structurelib.alignment.IAlignmentLimits;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizons.modularui.common.widget.DynamicPositionedColumn;
import com.gtnewhorizons.modularui.common.widget.FakeSyncWidget;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;
import com.gtnewhorizons.modularui.common.widget.TextWidget;
import com.miaokatze.gtsr.api.compat.GTSRHatchFluidAccess;
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.api.progress.GTSRProgressEntry;
import com.miaokatze.gtsr.common.gui.MTEThermoChemicalDenseSteamGeneratorGui;
import com.miaokatze.gtsr.common.machine.base.MTEGTSRMultiBlockBase;
import com.miaokatze.gtsr.common.util.GTSRUtils;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.blocks.BlockCasings4;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

/**
 * 热化学致密蒸汽发生系统（TCDS）：燃气/燃油双族连续流体多方块，无 RecipeMap。
 * <p>
 * 数值模型（单一权威，常量集中本类）：
 * <ul>
 * <li>基准产出 BASE_OUTPUT_PER_TICK = 210,000 L/t 蒸汽当量（= 4,200,000 L/s）@100% 热量，随 heat 线性放大</li>
 * <li>燃料：双族识别，扫全部输入仓取可用者中热值最高者为单燃料路径主力；燃气族（gasTurbineFuels）过热阈值
 * 350 EU/L，燃油族（dieselFuels ∪ denseLiquidFuels）过热阈值 450 EU/L；不过线仍照常燃烧产普通档蒸汽，
 * 不拒绝燃料。两族燃料并存且均命中静态类别映射（7 燃气类 × 7 燃油类，协同矩阵 v5）时进入协同燃烧：
 * 输出 ×协同系数/100，最大热量加共燃温度，每种燃料需求各 0.25 当量（合计恒等于单燃料 0.5 当量）</li>
 * <li>每 tick 顺序：基准需求 → fuelRatio（封顶 1）→ unconstrainedOutput/Fuel → airNeed = 燃料×100 →
 * airRatio（封顶 1，完全断气二元停机）→ 缺水二元停摆（heat>100% 爆炸）→ 全过后实扣 → 缓冲入账；
 * 协同分支实扣顺序：燃气 → 燃油 → 空气 → 蒸馏水</li>
 * <li>heatCap = min(200, 2×输出档位, 100×rawFuelRatio, 100×rawAirRatio)（未封顶比例；heat>heatCap 停机降温，
 * 供给富余可把热量推到 200% 使产出翻倍；螺丝刀 Shift+右键降档可压低上限）；热量初值 0%——新机 0% 起步，
 * 运行自热至 100%，供给富余可至 200%；旧档已写值保留，缺省回落 0%；热量每 20 tick 结算：运行 <100% +0.5、
 * 100%≤heat<heatCap +0.01、停机 −1（下限 0）</li>
 * <li>产出先进内部蒸汽当量缓冲（上限 840,000,000 L 当量，满则本 tick 停产不扣料），每 tick 按档位
 * 换算向全部输出仓分摊：非芯片 1:1（普通蒸汽/ic2superheatedsteam）；芯片致密蒸汽、
 * 致密过热蒸汽均 ÷1000，余量保留在缓冲</li>
 * <li>蒸馏水消耗 = 当量产出 ÷ 160，两阶段（探测全过 → 实扣）经 GTSRHatchFluidAccess 跨仓结算</li>
 * </ul>
 */
public class MTEThermoChemicalDenseSteamGenerator extends MTEGTSRMultiBlockBase<MTEThermoChemicalDenseSteamGenerator>
    implements IConstructable, ISurvivalConstructable {

    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static final int HORIZONTAL_OFF_SET = 4;
    private static final int VERTICAL_OFF_SET = 5;
    private static final int DEPTH_OFF_SET = 0;

    // GTUDK 导出结构（config/GTUDK/structures/热化学-致密蒸汽发生系统.java）：11 层 × 28 行 × 12 列；
    // 层在前（顶→底）、行在前（正面行 0）、列自左向右；'~' 控制器在（列 4, 层 5, 行 0）；
    // 'A' = gt.blockcasings4:1 洁净不锈钢机械方块（兼四类仓室位）、'B' = gt.blockframes:306 不锈钢框架、
    // '-' = 强制空气、'e' = 蒸汽粒子动画位（空气 + 偏移收集）、' ' = skip。
    // 注册时 transpose(SHAPE_MAIN) 轴交换回 StructureLib canonical [深][层][列]。
    private static final String[][] SHAPE_MAIN = {
        { "            ", "            ", "         AA ", "        A--A", "        A--A", "         AA ",
            "            ", "         AA ", "        A--A", "        A--A", "         AA ", "            ",
            "         AA ", "        A--A", "        A--A", "         AA ", "            ", "         AA ",
            "        A--A", "        A--A", "         AA ", "            ", "         AA ", "        A--A",
            "        A--A", "         AA ", "            ", "            " },
        { "            ", "            ", "   AAA  BAAB", "   AAA  AeeA", "   AAA  AeeA", "   AAA  BAAB",
            "   AAA      ", "   AAA  BAAB", "   AAA  AeeA", "   AAA  AeeA", "   AAA  BAAB", "   AAA      ",
            "   AAA  BAAB", "   AAA  AeeA", "   AAA  AeeA", "   AAA  BAAB", "   AAA      ", "   AAA  BAAB",
            "   AAA  AeeA", "   AAA  AeeA", "   AAA  BAAB", "   AAA      ", "   AAA  BAAB", "   AAA  AeeA",
            "   AAA  AeeA", "   AAA  BAAB", "            ", "            " },
        { "            ", "   AAA      ", "  A A A BAAB", "  A A A ABBA", "  A A A ABBA", "  A A A BAAB",
            "  A A A     ", "  A A A BAAB", "  A A A ABBA", "  A A A ABBA", "  A A A BAAB", "  A A A     ",
            "  A A A BAAB", "  A A A ABBA", "  A A A ABBA", "  A A A BAAB", "  A A A     ", "  A A A BAAB",
            "  A A A ABBA", "  A A A ABBA", "  A A A BAAB", "  A A A     ", "  A A A BAAB", "  A A A ABBA",
            "  A A A ABBA", "  A A A BAAB", "   AAA      ", "            " },
        { "    A       ", "  AA-AA     ", " A-A-A-ABAAB", " A-A-A-AA--A", " A-A-A-AA--A", " A-A-A-ABAAB",
            " A-A-A-A    ", " A-A-A-ABAAB", " A-A-A-AA--A", " A-A-A-AA--A", " A-A-A-ABAAB", " A-A-A-A    ",
            " A-A-A-ABAAB", " A-A-A-AA--A", " A-A-A-AA--A", " A-A-A-ABAAB", " A-A-A-A    ", " A-A-A-ABAAB",
            " A-A-A-AA--A", " A-A-A-AA--A", " A-A-A-ABAAB", " A-A-A-A    ", " A-A-A-ABAAB", " A-A-A-AA--A",
            " A-A-A-AA--A", " A-A-A-ABAAB", "  AA AA     ", "    A       " },
        { "   AAA      ", " AAAAAAA    ", "A-A-A-A-AAAB", "A-A-A-A-A--A", "A-A-A-A-A--A", "A-A-A-A-AAAB",
            "A-A-A-A-A   ", "A-A-A-A-AAAB", "A-A-A-A-A--A", "A-A-A-A-A--A", "A-A-A-A-AAAB", "A-A-A-A-A   ",
            "A-A-A-A-AAAB", "A-A-A-A-A--A", "A-A-A-A-A--A", "A-A-A-A-AAAB", "A-A-A-A-A   ", "A-A-A-A-AAAB",
            "A-A-A-A-A--A", "A-A-A-A-A--A", "A-A-A-A-AAAB", "A-A-A-A-A   ", "A-A-A-A-AAAB", "A-A-A-A-A--A",
            "A-A-A-A-A--A", "A-A-A-A-AAAB", " AAAAAAA    ", "   AAA      " },
        { "  AA~AA     ", " AAA-AAA    ", "A--A-A--AAAB", "A--A-A--A--A", "A--A-A--A--A", "A--A-A--AAAB",
            "A--A-A--A   ", "A--A-A--AAAB", "A--A-A--A--A", "A--A-A--A--A", "A--A-A--AAAB", "A--A-A--A   ",
            "A--A-A--AAAB", "A--A-A--A--A", "A--A-A--A--A", "A--A-A--AAAB", "A--A-A--A   ", "A--A-A--AAAB",
            "A--A-A--A--A", "A--A-A--A--A", "A--A-A--AAAB", "A--A-A--A   ", "A--A-A--AAAB", "A--A-A--A--A",
            "A--A-A--A--A", "A--A-A--AAAB", " AAA-AAA    ", "  AAAAA     " },
        { "   AAA      ", " AA---AA    ", "A-------AAAB", "A-------A--A", "A-------A--A", "A-------AAAB",
            "A-------A   ", "A-------AAAB", "A-------A--A", "A-------A--A", "A-------AAAB", "A-------A   ",
            "A-------AAAB", "A-------A--A", "A-------A--A", "A-------AAAB", "A-------A   ", "A-------AAAB",
            "A-------A--A", "A-------A--A", "A-------AAAB", "A-------A   ", "A-------AAAB", "A-------A--A",
            "A-------A--A", "A-------AAAB", " AA---AA    ", "   AAA      " },
        { "    A       ", "  AAAAA     ", "BA-----ABAAB", " A-----AA--A", " A-----AA--A", " A-----ABAAB",
            "BA-----A    ", " A-----ABAAB", " A-----AA--A", " A-----AA--A", "BA-----ABAAB", " A-----A    ",
            " A-----ABAAB", "BA-----AA--A", " A-----AA--A", " A-----ABAAB", " A-----A    ", "BA-----ABAAB",
            " A-----AA--A", " A-----AA--A", " A-----ABAAB", "BA-----A    ", " A-----ABAAB", " A-----AA--A",
            " A-----AA--A", "BA-----ABAAB", "  AAAAA     ", "    A       " },
        { "            ", "   AAA      ", "B A---A BAAB", "  A---A A--A", "  A---A A--A", "  A---A BAAB",
            "B A---A     ", "  A---A BAAB", "  A---A A--A", "  A---A A--A", "B A---A BAAB", "  A---A     ",
            "  A---A BAAB", "B A---A A--A", "  A---A A--A", "  A---A BAAB", "  A---A     ", "B A---A BAAB",
            "  A---A A--A", "  A---A A--A", "  A---A BAAB", "B A---A     ", "  A---A BAAB", "  A---A A--A",
            "  A---A A--A", "B A---A BAAB", "   AAA      ", "            " },
        { "            ", "            ", "B  AAA  BAAB", "   AAA  A--A", "   AAA  A--A", "   AAA  BAAB",
            "B  AAA      ", "   AAA  BAAB", "   AAA  A--A", "   AAA  A--A", "B  AAA  BAAB", "   AAA      ",
            "   AAA  BAAB", "B  AAA  A--A", "   AAA  A--A", "   AAA  BAAB", "   AAA      ", "B  AAA  BAAB",
            "   AAA  A--A", "   AAA  A--A", "   AAA  BAAB", "B  AAA      ", "   AAA  BAAB", "   AAA  A--A",
            "   AAA  A--A", "B  AAA  BAAB", "            ", "            " },
        { "            ", "            ", "B       BAAB", "        AAAA", "        AAAA", "        BAAB",
            "B           ", "        BAAB", "        AAAA", "        AAAA", "B       BAAB", "            ",
            "        BAAB", "B       AAAA", "        AAAA", "        BAAB", "            ", "B       BAAB",
            "        AAAA", "        AAAA", "        BAAB", "B           ", "        BAAB", "        AAAA",
            "        AAAA", "B       BAAB", "            ", "            " } };

    /** ic2 过热蒸汽采样（1 mB；惰性查 FluidRegistry，避免类加载期时序依赖；LGB 同名流体字符串） */
    private static FluidStack getIc2SuperheatedSteam() {
        return FluidRegistry.getFluidStack("ic2superheatedsteam", 1);
    }

    private static IStructureDefinition<MTEThermoChemicalDenseSteamGenerator> STRUCTURE_DEFINITION = null;
    // 'e' 蒸汽粒子动画位偏移缓存（20 个，惰性扫描 SHAPE_MAIN，勿硬编码；集群 collectAirFxOffsets 同范式）
    private static List<int[]> mFxOffsets = null;

    // ===== 数值模型常量（单一权威；plan 契约逐条对应）=====
    /** 基准产出：4,200,000 L/s = 210,000 L/t 蒸汽当量 @100% 热量 */
    private static final long BASE_OUTPUT_PER_TICK = 210_000L;
    /** 热量上限（%）：供给富余（rawRatio≥2）时可升至 200%，产出翻倍 */
    private static final double HEAT_MAX = 200.0d;
    /** 热量初值（%）：新机 0% 起步，运行自热至 100%，供给富余可至 200%；旧档已写值保留，缺省回落 0% */
    private static final double HEAT_START = 0.0d;
    /** 燃气族过热阈值（EU/L，mSpecialValue 热值）：达到 → 过热档 */
    private static final int GAS_SUPERHEAT_THRESHOLD = 350;
    /** 燃油族过热阈值（EU/L）：达到 → 过热档；不过线仍照常燃烧产普通档，不拒绝燃料 */
    private static final int LIQUID_SUPERHEAT_THRESHOLD = 450;
    /** 水汽比：1 mB 蒸馏水 = 160 L 蒸汽当量 */
    private static final int STEAM_PER_WATER = 160;
    /** 空气比：1 L 燃料需 100 L 空气（Materials.Air，仅 Gas 形态） */
    private static final int AIR_PER_FUEL = 100;
    /** 蒸汽当量缓冲上限：840,000,000 L 当量（200% 热量满产 100 秒）；满则本 tick 停产不扣料 */
    private static final long STEAM_BUFFER_CAPACITY = 840_000_000L;
    /** 致密系数：芯片普通档 ÷1000（致密蒸汽）、过热档 ÷1000（致密过热蒸汽） */
    private static final int DENSE_DIVISOR = 1_000;
    private static final int DENSE_SUPERHEATED_DIVISOR = 1_000;
    /** 输出档位白名单（%）：螺丝刀 Shift+右键轮换，最大热量 = 2×档位 */
    private static final int[] OUTPUT_TIERS = { 100, 80, 60, 40, 20 };
    /** 输出档位缺省值（旧档无 mOutputTier / 非法值回落） */
    private static final int OUTPUT_TIER_DEFAULT = 100;

    // ===== 运行状态（NBT 持久化：heat / 缓冲 / 档位；工作态由基类 mMaxProgresstime 持久化）=====
    // 下列 GUI 同步字段为 public（ModularUI DoubleSyncValue/IntSyncValue 客户端 setter 回写，LGB 同范式）
    /** 热量（%），double，每 20 tick 结算，初值 0.0（新机 0% 起步，运行自热至 100%） */
    public double mHeat = HEAT_START;
    /** 蒸汽当量缓冲（L 当量）：产出先进缓冲，每 tick 按档位换算向输出仓分摊，余量保留 */
    private long mSteamEquivalentBuffer = 0L;
    /** 最近产出档位：true = 过热档（燃料热值达阈值），决定缓冲换算的目标流体族 */
    public boolean mSuperheatedTier = false;
    /** 最近一次 checkProcessing 的热量上限（%），供 20 tick 热量结算的升温分支使用（每产出 tick 刷新） */
    private double mLastHeatCap = 100.0d;
    /** 本 tick 蒸汽当量产出（L/t，GUI 显示） */
    public int mCurrentOutputEquivalent = 0;
    /** 当前燃料族（GUI 显示）：0 无 / 1 燃气 / 2 燃油 / 3 油气协同 */
    public int mCurrentFuelKind = 0;
    /** 当前燃料热值 EU/L（GUI 显示） */
    public int mCurrentFuelValue = 0;
    /** 当前燃料具体流体名（GUI 显示态，服务端赋值客户端消费，不进 NBT） */
    public String mCurrentFuelFluidName = "";
    /**
     * 螺丝刀可切的输出档位（%）：取值 ∈ {100, 80, 60, 40, 20}（NBT 持久化，白名单校验），
     * 最大热量 = 2×档位（100 档 = 200% = HEAT_MAX，行为与旧版一致）
     */
    public int mOutputTier = OUTPUT_TIER_DEFAULT;
    /** 协同模式当前燃油具体流体名（GUI 显示态，服务端赋值客户端消费，不进 NBT） */
    public String mCurrentFuelLiquidName = "";
    /** 协同模式协同系数（%，矩阵 v5 查表值；GUI 显示态，不进 NBT） */
    public int mCurrentSynergyPercent = 0;
    /** 协同模式共燃温度（百分点，加到最大热量上；GUI 显示态，不进 NBT） */
    public int mCurrentSynergyTemp = 0;
    /** 客户端粒子工作态（getUpdateData/onValueUpdate bit0 通道同步；集群 mWorkingForFX 同范式） */
    protected boolean mWorkingForFX = false;
    /** 客户端 'e' 候选登记边沿标记（true = 已登记） */
    private boolean fxCandidatesRegistered = false;

    public MTEThermoChemicalDenseSteamGenerator(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        registerProgressEntries();
    }

    public MTEThermoChemicalDenseSteamGenerator(String aName) {
        super(aName);
        registerProgressEntries();
    }

    // GTSR 进度词条：GUI 终端显示（热量%、当量产出速率 + 模式后缀）
    private void registerProgressEntries() {
        // 热量词条零值仍显示（showZero）：新机 0% 起步时 GUI 也要能看到热量行
        registerEntry(
            GTSRProgressEntry.of("tcds_heat", "gtsr.gui.tcds.heat", "%.1f%%", EnumChatFormatting.GOLD, () -> mHeat)
                .showZero());
        registerEntryCustom(
            "tcds_output",
            "gtsr.gui.tcds.output",
            EnumChatFormatting.AQUA,
            () -> mCurrentOutputEquivalent,
            v -> NumberFormatUtil.formatNumber((long) v) + " L/t " + EnumChatFormatting.WHITE + outputModeSuffix());
    }

    /** 产出模式后缀：普通蒸汽/过热蒸汽（非芯片）与致密蒸汽/致密过热蒸汽（芯片） */
    public String outputModeSuffix() {
        boolean chip = hasDenseSteamChip();
        if (mSuperheatedTier) {
            return StatCollector
                .translateToLocal(chip ? "gtsr.gui.tcds.dense_superheated" : "gtsr.gui.tcds.superheated");
        }
        return StatCollector.translateToLocal(chip ? "gtsr.gui.tcds.dense_steam" : "gtsr.gui.tcds.steam");
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEThermoChemicalDenseSteamGenerator(mName);
    }

    public String getMachineType() {
        return "ThermoChemical Dense Steam Generator";
    }

    // ===== 结构定义 =====

    /** 'A' 外壳底材贴图索引：gt.blockcasings4 meta 1（洁净不锈钢机械方块） */
    private int getCasingTextureID() {
        return ((BlockCasings4) GregTechAPI.sBlockCasings4).getTextureIndex(1);
    }

    @Override
    public IStructureDefinition<MTEThermoChemicalDenseSteamGenerator> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            final int casingIndex = getCasingTextureID();
            STRUCTURE_DEFINITION = StructureDefinition.<MTEThermoChemicalDenseSteamGenerator>builder()
                .addShape(STRUCTURE_PIECE_MAIN, transpose(SHAPE_MAIN))
                // 'A' 双态位（集群 casingOrControllerInputSlot 双态范式）：casing-first（NEI 投影优先渲染
                // 外壳，真实 hatch 坐标 casing 匹配失败后继续匹配仓室 adder）；四类仓室各 atLeast(1)
                .addElement(
                    'A',
                    ofChain(
                        ofBlock(GregTechAPI.sBlockCasings4, 1),
                        buildHatchAdder(MTEThermoChemicalDenseSteamGenerator.class)
                            .atLeast(Maintenance, Muffler, InputHatch, OutputHatch)
                            .casingIndex(casingIndex)
                            .hint(1)
                            .build()))
                // 'B' 不锈钢框架：gt.blockframes meta 306
                .addElement('B', ofBlock(GameRegistry.findBlock("gregtech", "gt.blockframes"), 306))
                // 'e' 蒸汽粒子动画位：结构上是空气，偏移在惰性扫描时收集供客户端喷粒
                .addElement('e', isAir())
                .addElement('-', isAir())
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        // 四类仓室 atLeast(1) 的校验由 buildHatchAdder 在 checkPiece 内完成（缺任一类即不成型）
        checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET, errors);
    }

    // NEI/投影：单 piece（无档位通道，集群 construct/survivalConstruct 简化版）
    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece(STRUCTURE_PIECE_MAIN, stackSize, hintsOnly, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        return survivalBuildPiece(
            STRUCTURE_PIECE_MAIN,
            stackSize,
            HORIZONTAL_OFF_SET,
            VERTICAL_OFF_SET,
            DEPTH_OFF_SET,
            elementBudget,
            env,
            false,
            true);
    }

    // ===== 油气协同燃烧 v5（定稿）：静态类别映射 + 系数/温度矩阵 =====

    /** 燃气类别（矩阵行序）：GH 氢 / GA 烷烃 / GO 烯烃 / GG 煤气 / GT 芳烃气 / GI 硫杂 / GX 异星 */
    private enum GasCategory {

        GH,
        GA,
        GO,
        GG,
        GT,
        GI,
        GX
    }

    /** 燃油类别（矩阵列序）：OL 轻馏分 / OD 动力油 / OR 赛级 / OO 含氧 / OH 重油 / OT 焦油 / OE 硝化 */
    private enum LiquidCategory {

        OL,
        OD,
        OR,
        OO,
        OH,
        OT,
        OE
    }

    private static final int GAS_CATEGORY_COUNT = GasCategory.values().length;
    private static final int LIQUID_CATEGORY_COUNT = LiquidCategory.values().length;

    /**
     * 同源干涉特殊对流体：nefariousoil（BartWorks werkstoff，流体注册名按小写材料名 best-effort）；
     * 燃气类 = GX 且流体命中此名时覆盖矩阵值。
     */
    private static final String NEFARIOUS_SOIL_FLUID = "nefariousoil";
    /** 特殊对覆盖：GX × nefariousoil → 系数 85%、温度 0 */
    private static final int SYNERGY_SPECIAL_PERCENT = 85;
    private static final int SYNERGY_SPECIAL_TEMP = 0;

    /**
     * 协同系数矩阵 v5（%）：行=燃气 GH/GA/GO/GG/GT/GI/GX，列=燃油 OL/OD/OR/OO/OH/OT/OE。
     * 协同输出 = 基准 × heat/100 × fuelRatio × airRatio × 系数/100；燃料需求与系数无关
     * （两种各 0.25 当量，守恒）。热值一律运行时 findFuel(...).mSpecialValue 查询，矩阵不含热值。
     */
    private static final int[][] SYNERGY_PERCENT = { { 132, 155, 217, 128, 166, 182, 245 }, // GH
        { 118, 150, 183, 112, 138, 105, 147 }, // GA
        { 108, 132, 158, 118, 136, 82, 134 }, // GO
        { 88, 106, 128, 82, 134, 102, 95 }, // GG
        { 72, 98, 138, 94, 112, 46, 116 }, // GT
        { 30, 26, 44, 47, 20, 29, 51 }, // GI
        { 228, 262, 385, 195, 214, 238, 336 } // GX
    };

    /** 共燃温度矩阵 v5（百分点，加到最大热量上）：行列序同 SYNERGY_PERCENT */
    private static final int[][] SYNERGY_TEMP = { { 18, 24, 33, 9, 15, 6, 50 }, // GH
        { 14, 18, 27, -3, 11, -5, 38 }, // GA
        { 9, 13, 22, -6, 4, -16, 31 }, // GO
        { -13, -8, 2, -23, -4, -28, 19 }, // GG
        { -2, 6, 17, -11, 4, -33, 24 }, // GT
        { -38, -27, -14, -25, -50, -43, -8 }, // GI
        { 32, 37, 46, 19, 26, 42, 48 } // GX
    };

    /**
     * 流体注册名 → 燃气类别映射（键均为小写注册名；TreeMap CASE_INSENSITIVE_ORDER 与小写键组合实现
     * 等价 equalsIgnoreCase 匹配）。未命中映射的燃气不参与协同（回退单燃料逻辑，保守）。
     */
    private static final Map<String, GasCategory> GAS_CATEGORY_BY_FLUID = buildGasCategoryMap();

    /**
     * 流体注册名 → 燃油类别映射（键均为小写注册名；等价 equalsIgnoreCase 匹配）。
     * 未命中映射的燃油不参与协同（回退单燃料逻辑，保守）。
     */
    private static final Map<String, LiquidCategory> LIQUID_CATEGORY_BY_FLUID = buildLiquidCategoryMap();

    private static Map<String, GasCategory> buildGasCategoryMap() {
        Map<String, GasCategory> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        // GH 氢
        putAll(map, GasCategory.GH, "hydrogen");
        // GA 烷烃（NatruralGas 为上游材料名拼写错误，流体注册名实为 gas_natural_gas）
        putAll(
            map,
            GasCategory.GA,
            "methane",
            "gas_natural_gas",
            "gas_gas",
            "ethane",
            "propane",
            "butane",
            "liquid_lpg");
        // GO 烯烃
        putAll(map, GasCategory.GO, "ethylene", "propene", "butene", "butadiene");
        // GG 煤气（coalgas：GT5U GTPPFluids.CoalGas 经 generateFluidNonMolten("CoalGas") 按小写注册，
        // RecipeLoaderCoalTar.generateFuelRecipes 以 GasTurbine FUEL_VALUE 96 入燃料表）
        putAll(map, GasCategory.GG, "woodgas", "carbonmonoxide", "coalgas");
        // GT 芳烃气
        putAll(map, GasCategory.GT, "benzene", "liquid_toluene", "phenol", "liquid_naphtha");
        // GI 硫杂
        putAll(map, GasCategory.GI, "gas_sulfuricgas", "liquid_sulfuricnaphtha");
        // GX 异星（BartWorks werkstoff，流体注册名按小写材料名 best-effort）
        putAll(map, GasCategory.GX, "nefariousgas");
        return map;
    }

    private static Map<String, LiquidCategory> buildLiquidCategoryMap() {
        Map<String, LiquidCategory> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        // OL 轻馏分
        putAll(map, LiquidCategory.OL, "liquid_light_fuel", "octane");
        // OD 动力油
        putAll(map, LiquidCategory.OD, "fuel", "gasoline", "biodiesel");
        // OR 赛级
        putAll(map, LiquidCategory.OR, "highoctanegasoline");
        // OO 含氧
        putAll(map, LiquidCategory.OO, "bioethanol", "methanol", "glycerol", "biomass", "fishoil", "seedoil");
        // OH 重油（liquid_sufluriclight_fuel 为上游拼写错误，照抄）
        putAll(
            map,
            LiquidCategory.OH,
            "liquid_heavy_fuel",
            "oil",
            "liquid_light_oil",
            "liquid_medium_oil",
            "liquid_heavy_oil",
            "liquid_extra_heavy_oil",
            "liquid_sulfuricheavy_fuel",
            "liquid_sufluriclight_fuel",
            "naphthenicacid");
        // OE 硝化
        putAll(map, LiquidCategory.OE, "nitrofuel", "nitrocoalfuel");
        // OT 焦油（nefariousoil 为 BartWorks werkstoff，流体注册名按小写材料名 best-effort）
        putAll(map, LiquidCategory.OT, "coalfuel", "creosote", "nefariousoil");
        return map;
    }

    private static <E extends Enum<E>> void putAll(Map<String, E> map, E category, String... fluidNames) {
        for (String name : fluidNames) {
            map.put(name, category);
        }
    }

    // ===== 燃料识别（双族扫描：单燃料取可用者中热值最高；两族命中映射时协同配对）=====

    /**
     * 燃料候选：流体 + 运行时热值（EU/L，findFuel(...).mSpecialValue）+ 族（true = 燃气族 / false = 燃油族）
     * + 协同类别索引（燃气矩阵行 / 燃油矩阵列；-1 = 未命中静态映射，不参与协同）。
     */
    private static final class FuelCandidate {

        private final Fluid fluid;
        private final int heatValue;
        private final boolean gasFamily;
        private final int categoryIndex;

        private FuelCandidate(Fluid fluid, int heatValue, boolean gasFamily, int categoryIndex) {
            this.fluid = fluid;
            this.heatValue = heatValue;
            this.gasFamily = gasFamily;
            this.categoryIndex = categoryIndex;
        }
    }

    /** 单次输入仓扫描结果：单燃料全局最优 + 燃气/燃油各类别最高热值代表（协同配对用） */
    private static final class FuelScan {

        private FuelCandidate bestSingle;
        private final FuelCandidate[] bestGasByCategory = new FuelCandidate[GAS_CATEGORY_COUNT];
        private final FuelCandidate[] bestLiquidByCategory = new FuelCandidate[LIQUID_CATEGORY_COUNT];

        private void record(FuelCandidate candidate) {
            // 单燃料路径：全族热值最高（同热值保留先扫到者，原 findBestFuel 语义）
            if (bestSingle == null || candidate.heatValue > bestSingle.heatValue) {
                bestSingle = candidate;
            }
            // 协同路径：每类别取可用最高热值代表
            if (candidate.categoryIndex < 0) return;
            FuelCandidate[] slots = candidate.gasFamily ? bestGasByCategory : bestLiquidByCategory;
            if (slots[candidate.categoryIndex] == null
                || candidate.heatValue > slots[candidate.categoryIndex].heatValue) {
                slots[candidate.categoryIndex] = candidate;
            }
        }
    }

    /** 协同配对：燃气代表 + 燃油代表 + 协同系数（%）+ 共燃温度（百分点，加到最大热量上） */
    private static final class SynergyPair {

        private final FuelCandidate gas;
        private final FuelCandidate liquid;
        private final int percent;
        private final int temp;

        private SynergyPair(FuelCandidate gas, FuelCandidate liquid, int percent, int temp) {
            this.gas = gas;
            this.liquid = liquid;
            this.percent = percent;
            this.temp = temp;
        }
    }

    /**
     * 扫全部输入仓（单次）：燃气族走 gasTurbineFuels（阈值 350）、燃油族走 dieselFuels ∪ denseLiquidFuels
     * （阈值 450），热值取 findFuel(...).mSpecialValue；两 map 均无 → 非燃料。同热值保留先扫到者。
     * 同时按静态类别映射收集燃气/燃油各类别最高热值代表（协同配对用；未命中映射不参与协同）。
     * 查找范式 MTELargeTurbineGas.getFuelValue。
     */
    private FuelScan scanFuels() {
        FuelScan scan = new FuelScan();
        for (MTEHatch hatch : GTUtility.validMTEList(mInputHatches)) {
            FluidTankInfo[] tanks = hatch.getTankInfo(ForgeDirection.UNKNOWN);
            if (tanks == null) continue;
            for (FluidTankInfo tank : tanks) {
                if (tank == null || tank.fluid == null || tank.fluid.amount <= 0) continue;
                FluidStack fs = tank.fluid;
                // 燃气族
                GTRecipe gasRecipe = RecipeMaps.gasTurbineFuels.getBackend()
                    .findFuel(fs);
                if (gasRecipe != null && gasRecipe.mSpecialValue > 0) {
                    scan.record(
                        new FuelCandidate(
                            fs.getFluid(),
                            gasRecipe.mSpecialValue,
                            true,
                            categoryIndex(GAS_CATEGORY_BY_FLUID, fs.getFluid())));
                    continue;
                }
                // 燃油族（柴油 ∪ 半流体）
                GTRecipe dieselRecipe = RecipeMaps.dieselFuels.getBackend()
                    .findFuel(fs);
                GTRecipe denseRecipe = RecipeMaps.denseLiquidFuels.getBackend()
                    .findFuel(fs);
                int liquidValue = Math.max(
                    dieselRecipe != null ? dieselRecipe.mSpecialValue : 0,
                    denseRecipe != null ? denseRecipe.mSpecialValue : 0);
                if (liquidValue > 0) {
                    scan.record(
                        new FuelCandidate(
                            fs.getFluid(),
                            liquidValue,
                            false,
                            categoryIndex(LIQUID_CATEGORY_BY_FLUID, fs.getFluid())));
                }
            }
        }
        return scan;
    }

    /** 流体注册名 → 类别序数；映射为小写键 + CASE_INSENSITIVE_ORDER（等价 equalsIgnoreCase），未命中 → -1 */
    private static <E extends Enum<E>> int categoryIndex(Map<String, E> map, Fluid fluid) {
        E category = map.get(fluid.getName());
        return category == null ? -1 : category.ordinal();
    }

    /**
     * 协同配对：两族均有类别映射命中时，遍历可得类对取协同系数最高者
     * （平手先燃气热值降序、再燃油热值降序）；GX × nefariousoil 特殊对覆盖（同源干涉）。
     */
    private static SynergyPair findBestSynergyPair(FuelScan scan) {
        SynergyPair best = null;
        for (int g = 0; g < GAS_CATEGORY_COUNT; g++) {
            FuelCandidate gas = scan.bestGasByCategory[g];
            if (gas == null) continue;
            for (int l = 0; l < LIQUID_CATEGORY_COUNT; l++) {
                FuelCandidate liquid = scan.bestLiquidByCategory[l];
                if (liquid == null) continue;
                int percent = SYNERGY_PERCENT[g][l];
                int temp = SYNERGY_TEMP[g][l];
                if (g == GasCategory.GX.ordinal() && NEFARIOUS_SOIL_FLUID.equalsIgnoreCase(liquid.fluid.getName())) {
                    percent = SYNERGY_SPECIAL_PERCENT;
                    temp = SYNERGY_SPECIAL_TEMP;
                }
                if (betterSynergy(best, gas, liquid, percent)) {
                    best = new SynergyPair(gas, liquid, percent, temp);
                }
            }
        }
        return best;
    }

    /** 配对比较：协同系数最高；平手先燃气热值降序、再燃油热值降序 */
    private static boolean betterSynergy(SynergyPair best, FuelCandidate gas, FuelCandidate liquid, int percent) {
        if (best == null) return true;
        if (percent != best.percent) return percent > best.percent;
        if (gas.heatValue != best.gas.heatValue) return gas.heatValue > best.gas.heatValue;
        return liquid.heatValue > best.liquid.heatValue;
    }

    // ===== 每 tick 生产核心（checkProcessing 逐 tick 驱动：mMaxProgresstime = 1）=====

    @Override
    public CheckRecipeResult checkProcessing() {
        mCurrentOutputEquivalent = 0;
        mCurrentFuelKind = 0;
        mCurrentFuelValue = 0;
        mCurrentFuelFluidName = "";
        mCurrentFuelLiquidName = "";
        mCurrentSynergyPercent = 0;
        mCurrentSynergyTemp = 0;
        if (!mMachine || !getBaseMetaTileEntity().isAllowedToWork()) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }
        // 缓冲满 → 本 tick 停产（不扣料，热量按停机衰减）
        if (mSteamEquivalentBuffer >= STEAM_BUFFER_CAPACITY) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }
        // a. 燃料识别（单次扫描同时收集单燃料最优与协同类别代表）：无可用燃料 → 停机
        FuelScan scan = scanFuels();
        if (scan.bestSingle == null) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }
        // a+. 协同分支：两族均有类别映射命中 → 协同配对燃烧；单族或未命中映射 → 回退单燃料（保守）
        SynergyPair synergy = findBestSynergyPair(scan);
        if (synergy != null) {
            return processSynergy(synergy);
        }
        return processSingleFuel(scan.bestSingle);
    }

    /** 单燃料生产路径（原 checkProcessing 主体原样保留）：燃气或燃油取可用热值最高者 */
    private CheckRecipeResult processSingleFuel(FuelCandidate fuel) {
        mCurrentFuelKind = fuel.gasFamily ? 1 : 2;
        mCurrentFuelValue = fuel.heatValue;
        mCurrentFuelFluidName = fuel.fluid.getName();

        // 基准需求 = (210,000 ÷ 热值) × 热量系数；热量系数 ≤100% 恒 0.5，>100% 每超 1 个百分点总消耗 ×0.995
        double heatCoefficient = mHeat <= 100.0d ? 0.5d : 0.5d * (1.0d - 0.005d * (mHeat - 100.0d));
        double baseDemand = ((double) BASE_OUTPUT_PER_TICK / fuel.heatValue) * heatCoefficient;
        // 探测上限取 2×基准需求（heatCap=200% 所需供给）：rawRatio 封顶 2，产出用比例封顶 1
        long availableFuel = GTSRHatchFluidAccess
            .probeFluidAmountAcross(mInputHatches, new FluidStack(fuel.fluid, (int) (2L * Math.ceil(baseDemand))));
        double rawFuelRatio = baseDemand <= 0.0d ? 2.0d : (double) availableFuel / baseDemand;
        double fuelRatio = Math.min(1.0d, rawFuelRatio);

        // a/b. 不受限产出与燃料量
        double unconstrainedOutput = BASE_OUTPUT_PER_TICK * (mHeat / 100.0d) * fuelRatio;
        double unconstrainedFuel = (unconstrainedOutput / fuel.heatValue) * heatCoefficient;

        // c. 空气需求 = 燃料 × 100；可用空气 == 0 → 二元停机（不产出不扣料）
        double airNeed = unconstrainedFuel * AIR_PER_FUEL;
        long availableAir = 0;
        double rawAirRatio;
        if (airNeed <= 0.0d) {
            rawAirRatio = 2.0d;
        } else {
            int airProbe = (int) Math.min(Integer.MAX_VALUE, 2L * (long) Math.ceil(airNeed));
            availableAir = GTSRHatchFluidAccess.probeFluidAmountAcross(mInputHatches, Materials.Air.getGas(airProbe));
            if (availableAir <= 0) {
                return CheckRecipeResultRegistry.NO_RECIPE;
            }
            rawAirRatio = (double) availableAir / airNeed;
        }
        double airRatio = Math.min(1.0d, rawAirRatio);

        // f. heatCap = min(200, 2×输出档位, 100×rawFuelRatio, 100×rawAirRatio)；heat > heatCap → 停机降温
        // （档位 100 时 2×100=200=HEAT_MAX 行为不变；螺丝刀降档即降低热量上限）
        double heatCap = Math
            .min(HEAT_MAX, Math.min(2.0d * mOutputTier, Math.min(100.0d * rawFuelRatio, 100.0d * rawAirRatio)));
        if (mHeat > heatCap) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        // d. 实际值（airRatio 缩放；消耗量四舍五入为整数 L）
        double actualOutput = unconstrainedOutput * airRatio;
        double actualFuel = unconstrainedFuel * airRatio;
        int fuelToConsume = (int) Math.round(actualFuel);
        int airToConsume = (int) Math.round(airNeed * airRatio);
        int waterToConsume = (int) Math.round(actualOutput / STEAM_PER_WATER);

        // e. 缺水裁决（用户拍板）：蒸馏水不足 → 本 tick 完全不产出、不扣任何流体；
        // heat > 100% → 爆炸（基类 explodeMultiblock，MTEMultiBlockBase:1515）；≤100% → 安全停摆
        FluidStack waterWant = GTModHandler.getDistilledWater(waterToConsume);
        if (waterToConsume > 0 && !GTSRHatchFluidAccess.hasEnoughAcross(mInputHatches, waterWant)) {
            if (mHeat > 100.0d) {
                explodeMultiblock();
            }
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        // 先全过后扣（平炉 MTESiemensMartinFurnace 两阶段范式）：燃料 / 空气 / 蒸馏水顺序实扣
        if (fuelToConsume > 0) {
            GTSRHatchFluidAccess.depleteFluidAcross(mInputHatches, new FluidStack(fuel.fluid, fuelToConsume));
        }
        if (airToConsume > 0) {
            GTSRHatchFluidAccess.depleteFluidAcross(mInputHatches, Materials.Air.getGas(airToConsume));
        }
        if (waterToConsume > 0) {
            GTSRHatchFluidAccess.depleteFluidAcross(mInputHatches, waterWant);
        }

        // g. 缓冲入账 + 产出档位判定（热值达阈值 → 过热档；芯片在落仓时选择致密变体）
        long outputRounded = Math.round(actualOutput);
        mCurrentOutputEquivalent = (int) Math.min(Integer.MAX_VALUE, outputRounded);
        mSteamEquivalentBuffer += outputRounded;
        mSuperheatedTier = fuel.heatValue >= (fuel.gasFamily ? GAS_SUPERHEAT_THRESHOLD : LIQUID_SUPERHEAT_THRESHOLD);
        mLastHeatCap = heatCap;

        // mMaxProgresstime = 1：基类 runMachine 每 tick 完成一次并立刻 checkRecipe 重启，实现连续逐 tick 生产
        mMaxProgresstime = 1;
        mEfficiency = 10000;
        mEfficiencyIncrease = 10000;
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    /**
     * 油气协同燃烧分支（v5 定稿）：
     * <ul>
     * <li>需求 demand_i = (基准 × heat/100 ÷ hv_i) × 0.25 × 过热惩罚（>100% 每超 1 点 ×0.995），
     * 两种合计恒等于单燃料 0.5 当量</li>
     * <li>输出 = 基准 × heat/100 × fuelRatio × airRatio × 协同系数/100</li>
     * <li>heatCap = max(0, min(200, 2×输出档位) + 共燃温度)（=capTarget），再与 100×rawFuelRatio、100×rawAirRatio 取 min；
     * 供给探测倍率取 capTarget/100，正共燃温度据此可突破 200% 直至温度扩展上限（+50 → 250%）</li>
     * <li>实扣顺序：燃气 → 燃油 → 空气 → 蒸馏水；缺水爆炸/停摆裁决与单燃料路径一致</li>
     * </ul>
     */
    private CheckRecipeResult processSynergy(SynergyPair synergy) {
        FuelCandidate gas = synergy.gas;
        FuelCandidate liquid = synergy.liquid;
        mCurrentFuelKind = 3;
        mCurrentFuelValue = gas.heatValue;
        mCurrentFuelFluidName = gas.fluid.getName();
        mCurrentFuelLiquidName = liquid.fluid.getName();
        mCurrentSynergyPercent = synergy.percent;
        mCurrentSynergyTemp = synergy.temp;

        // 目标热量上限 capTarget 与供给探测倍率 capFactor：正共燃温度突破 200% 的关键——
        // 探测上限随 capTarget 扩展，使 100×rawRatio 供给比例项可放行至温度扩展后的上限（默认 2.0，+50 温度时 2.5）
        double capTarget = Math.max(0.0d, Math.min(HEAT_MAX, 2.0d * mOutputTier) + synergy.temp);
        if (capTarget <= 0.0d) {
            // 档位与负共燃温度叠加钳 0：无法维持燃烧，本 tick 停机
            return CheckRecipeResultRegistry.NO_RECIPE;
        }
        double capFactor = capTarget / 100.0d;

        // 每种燃料需求（0.25 当量 × 2 种 = 0.5 当量，守恒）；过热惩罚与单燃料热量系数同式
        double heatPenalty = mHeat > 100.0d ? (1.0d - 0.005d * (mHeat - 100.0d)) : 1.0d;
        double demandGas = ((double) BASE_OUTPUT_PER_TICK * mHeat / 100.0d / gas.heatValue) * 0.25d * heatPenalty;
        double demandLiquid = ((double) BASE_OUTPUT_PER_TICK * mHeat / 100.0d / liquid.heatValue) * 0.25d * heatPenalty;

        // fuelRatio = min(1, avail_G/need_G, avail_L/need_L)；探测上限 = capFactor×需求（probeFluidAmountAcross 范式），
        // demand≤0 → ratio=capFactor（供给充裕语义，上限同 capTarget）
        double rawFuelRatio;
        if (demandGas <= 0.0d || demandLiquid <= 0.0d) {
            rawFuelRatio = capFactor;
        } else {
            long availableGas = GTSRHatchFluidAccess.probeFluidAmountAcross(
                mInputHatches,
                new FluidStack(
                    gas.fluid,
                    (int) Math.min(Integer.MAX_VALUE, (long) Math.ceil(capFactor * Math.ceil(demandGas)))));
            long availableLiquid = GTSRHatchFluidAccess.probeFluidAmountAcross(
                mInputHatches,
                new FluidStack(
                    liquid.fluid,
                    (int) Math.min(Integer.MAX_VALUE, (long) Math.ceil(capFactor * Math.ceil(demandLiquid)))));
            rawFuelRatio = Math.min((double) availableGas / demandGas, (double) availableLiquid / demandLiquid);
        }
        double fuelRatio = Math.min(1.0d, rawFuelRatio);

        // 不受限实耗（含 fuelRatio 缩放）与空气需求；可用空气 == 0 → 二元停机（不产出不扣料）
        double unconstrainedFuelGas = demandGas * fuelRatio;
        double unconstrainedFuelLiquid = demandLiquid * fuelRatio;
        double airNeed = (unconstrainedFuelGas + unconstrainedFuelLiquid) * AIR_PER_FUEL;
        double rawAirRatio;
        if (airNeed <= 0.0d) {
            rawAirRatio = capFactor;
        } else {
            int airProbe = (int) Math.min(Integer.MAX_VALUE, (long) Math.ceil(capFactor * Math.ceil(airNeed)));
            long availableAir = GTSRHatchFluidAccess
                .probeFluidAmountAcross(mInputHatches, Materials.Air.getGas(airProbe));
            if (availableAir <= 0) {
                return CheckRecipeResultRegistry.NO_RECIPE;
            }
            rawAirRatio = (double) availableAir / airNeed;
        }
        double airRatio = Math.min(1.0d, rawAirRatio);

        // heatCap = capTarget，再与供给比例取 min；heat > heatCap → 停机降温
        double heatCap = Math.min(capTarget, Math.min(100.0d * rawFuelRatio, 100.0d * rawAirRatio));
        if (mHeat > heatCap) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        // 实际值（airRatio 缩放；消耗量四舍五入为整数 L）
        double actualOutput = BASE_OUTPUT_PER_TICK * (mHeat / 100.0d)
            * fuelRatio
            * airRatio
            * (synergy.percent / 100.0d);
        int gasToConsume = (int) Math.round(unconstrainedFuelGas * airRatio);
        int liquidToConsume = (int) Math.round(unconstrainedFuelLiquid * airRatio);
        int airToConsume = (int) Math.round(airNeed * airRatio);
        int waterToConsume = (int) Math.round(actualOutput / STEAM_PER_WATER);

        // 缺水裁决（同单燃料路径）：蒸馏水不足 → 本 tick 完全不产出、不扣任何流体；
        // heat > 100% → 爆炸；≤100% → 安全停摆
        FluidStack waterWant = GTModHandler.getDistilledWater(waterToConsume);
        if (waterToConsume > 0 && !GTSRHatchFluidAccess.hasEnoughAcross(mInputHatches, waterWant)) {
            if (mHeat > 100.0d) {
                explodeMultiblock();
            }
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        // 先全过后扣（协同顺序）：燃气 → 燃油 → 空气 → 蒸馏水
        if (gasToConsume > 0) {
            GTSRHatchFluidAccess.depleteFluidAcross(mInputHatches, new FluidStack(gas.fluid, gasToConsume));
        }
        if (liquidToConsume > 0) {
            GTSRHatchFluidAccess.depleteFluidAcross(mInputHatches, new FluidStack(liquid.fluid, liquidToConsume));
        }
        if (airToConsume > 0) {
            GTSRHatchFluidAccess.depleteFluidAcross(mInputHatches, Materials.Air.getGas(airToConsume));
        }
        if (waterToConsume > 0) {
            GTSRHatchFluidAccess.depleteFluidAcross(mInputHatches, waterWant);
        }

        // 缓冲入账 + 过热致密档判定（协同推广：燃气热值 ≥350 且 燃油热值 ≥450 → 过热档，否则普通档）
        long outputRounded = Math.round(actualOutput);
        mCurrentOutputEquivalent = (int) Math.min(Integer.MAX_VALUE, outputRounded);
        mSteamEquivalentBuffer += outputRounded;
        mSuperheatedTier = gas.heatValue >= GAS_SUPERHEAT_THRESHOLD && liquid.heatValue >= LIQUID_SUPERHEAT_THRESHOLD;
        mLastHeatCap = heatCap;

        mMaxProgresstime = 1;
        mEfficiency = 10000;
        mEfficiencyIncrease = 10000;
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    // ===== 芯片（控制器槽 mInventory[1]，LGB :569-577 范式）=====

    /** 控制器槽装入致密蒸汽芯片 → 产出致密变体（普通档 ÷1000 / 过热档 ÷1000） */
    public boolean hasDenseSteamChip() {
        ItemStack stack = getControllerSlot();
        return stack != null && GTSRItemList.TcdsBoostChip.isStackEqual(stack, true, true);
    }

    /** 控制器槽错装其他物品 → GUI 告警（LGB :943 范式） */
    public boolean hasInvalidChipSlotItem() {
        ItemStack stack = getControllerSlot();
        return stack != null && !hasDenseSteamChip();
    }

    // ===== 落仓：缓冲 → 输出仓（每 tick 分摊，LGB distributeSteam 范式）=====

    /**
     * 每 tick 按档位换算目标流体（非芯片 1:1；芯片两档均 ÷1000，余量保留在缓冲），向全部输出仓
     * 分摊；装不下的部分以蒸汽当量形态留在缓冲，缓冲满后本 tick 停产（输出堵 → 停机衰减热量）。
     */
    private void distributeBufferToOutputHatches() {
        if (mSteamEquivalentBuffer <= 0L || mOutputHatches.isEmpty()) return;
        boolean chip = hasDenseSteamChip();
        int divisor;
        FluidStack sample;
        if (mSuperheatedTier) {
            divisor = chip ? DENSE_SUPERHEATED_DIVISOR : 1;
            sample = chip ? Materials.DenseSuperheatedSteam.getGas(1) : getIc2SuperheatedSteam();
        } else {
            divisor = chip ? DENSE_DIVISOR : 1;
            sample = chip ? Materials.DenseSteam.getGas(1) : Materials.Steam.getGas(1);
        }
        if (sample == null || sample.getFluid() == null) return;
        int convertible = (int) Math.min(Integer.MAX_VALUE, mSteamEquivalentBuffer / divisor);
        if (convertible <= 0) return;
        FluidStack out = new FluidStack(sample.getFluid(), convertible);
        for (MTEHatchOutput hatch : mOutputHatches) {
            if (out.amount <= 0) break;
            int filled = hatch.fill(ForgeDirection.UNKNOWN, out.copy(), true);
            out.amount -= filled;
        }
        // 实际落仓部分折算回当量扣减，余量保留在缓冲
        mSteamEquivalentBuffer -= (long) (convertible - out.amount) * divisor;
    }

    // ===== tick 驱动 =====

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (aBaseMetaTileEntity.isClientSide()) {
            // 工作态（bit0）驱动 'e' 位蒸汽粒子：边沿登记/清理候选位，工作时每 tick 喷粒
            updateClientFxCandidates();
            if (mWorkingForFX) {
                TcdsParticleFxClient.spawnParticles(this);
            }
            return;
        }

        // 每 tick 分摊输出（缓冲 → 输出仓）
        distributeBufferToOutputHatches();

        // 每 20 tick 热量结算（heat 为 %，NBT 持久化）
        if (aTick % 20 == 0) {
            boolean running = mMaxProgresstime > 0; // 最近一次 checkProcessing 成功产出
            if (running) {
                if (mHeat < 100.0d) {
                    mHeat = Math.min(100.0d, mHeat + 0.5d);
                } else if (mHeat < mLastHeatCap) {
                    mHeat = Math.min(mLastHeatCap, mHeat + 0.01d);
                }
            } else {
                // 停机（缺水停摆/缺气/无燃料/输出堵/过热保护/未成型）→ 每 20 tick −1%，下限 0
                mHeat = Math.max(0.0d, mHeat - 1.0d);
            }
        }
    }

    // ===== 螺丝刀输出档位（范式 MTEDenseStateManipulator.onScrewdriverRightClick）=====

    /** 输出档位白名单校验 */
    private static boolean isValidOutputTier(int tier) {
        for (int t : OUTPUT_TIERS) {
            if (t == tier) return true;
        }
        return false;
    }

    /**
     * 螺丝刀右键：潜行轮换输出档位 100→80→60→40→20→100 并聊天栏播报；非潜行仅提示不切换
     * （GT5U 分发层无 shift 门控，须自行判定）。降档后 mLastHeatCap 无需强制复位：
     * heat > 新 cap 走 checkProcessing 既有停机降温分支，恢复产出的 tick 会按新档位
     * 重算 heatCap 并在 mLastHeatCap 处自然刷新（20t 热量结算消费该值）。
     */
    @Override
    public void onScrewdriverRightClick(ForgeDirection side, EntityPlayer aPlayer, float aX, float aY, float aZ,
        ItemStack aTool) {
        if (aPlayer.worldObj.isRemote) return;
        if (!aPlayer.isSneaking()) {
            GTUtility.sendChatTrans(aPlayer, "gtsr.chat.tcds.output_tier.hint");
            return;
        }
        int index = -1;
        for (int i = 0; i < OUTPUT_TIERS.length; i++) {
            if (OUTPUT_TIERS[i] == mOutputTier) {
                index = i;
                break;
            }
        }
        // 白名单外异常值（正常被 NBT 加载守卫拦下）兜底回 100
        mOutputTier = index < 0 ? OUTPUT_TIER_DEFAULT : OUTPUT_TIERS[(index + 1) % OUTPUT_TIERS.length];
        GTUtility.sendChatTrans(aPlayer, "gtsr.chat.tcds.output_tier", mOutputTier, 2 * mOutputTier);
        getBaseMetaTileEntity().markDirty();
    }

    // ===== 客户端粒子 =====

    /** 'e' 偏移收集（惰性扫描 SHAPE_MAIN，集群 ClusterStructureDef.collectAirFxOffsets 同范式） */
    private static List<int[]> getFxOffsets() {
        if (mFxOffsets == null) {
            List<int[]> offsets = new ArrayList<>();
            for (int layer = 0; layer < SHAPE_MAIN.length; layer++) {
                for (int row = 0; row < SHAPE_MAIN[layer].length; row++) {
                    String line = SHAPE_MAIN[layer][row];
                    for (int col = 0; col < line.length(); col++) {
                        if (line.charAt(col) == 'e') {
                            offsets.add(
                                new int[] { col - HORIZONTAL_OFF_SET, layer - VERTICAL_OFF_SET, row - DEPTH_OFF_SET });
                        }
                    }
                }
            }
            mFxOffsets = offsets;
        }
        return mFxOffsets;
    }

    /**
     * 客户端 onPostTick 粒子链（updateClientFxCandidates / TcdsParticleFxClient.spawnParticles）依赖持续客户端 tick。
     * 基类默认 false 会 tryDisableTicking 摘出 tick 列表，致粒子链不执行；与 MTECharcoalPit 等 GT5U 先例同范式。
     */
    @Override
    public boolean needsClientTick() {
        return true;
    }

    /** 客户端 'e' 候选登记/清理（工作态边沿各执行一次，集群 updateClientFxAirCandidates 同范式） */
    private void updateClientFxCandidates() {
        if (mWorkingForFX) {
            if (!fxCandidatesRegistered) {
                TcdsParticleFx.registerCandidates(this, getFxOffsets());
                fxCandidatesRegistered = true;
            }
        } else if (fxCandidatesRegistered) {
            fxCandidatesRegistered = false;
            TcdsParticleFx.clearCandidates(this);
        }
    }

    @Override
    public void onRemoval() {
        TcdsParticleFx.clearCandidates(this);
        super.onRemoval();
    }

    // ===== 客户端工作态同步（bit0，集群 getUpdateData/onValueUpdate 范式）=====

    @Override
    public byte getUpdateData() {
        // bit0 = 工作态（最近一次 checkProcessing 成功产出 = mMaxProgresstime > 0），驱动客户端粒子
        return (byte) (mMaxProgresstime > 0 ? 0x01 : 0x00);
    }

    @Override
    public void onValueUpdate(byte aValue) {
        mWorkingForFX = (aValue & 0x01) != 0;
    }

    // ===== NBT（heat / 缓冲 / 档位；工作态由基类 mMaxProgresstime/mProgresstime 持久化）=====

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setDouble("mHeat", mHeat);
        aNBT.setLong("mSteamEquivalentBuffer", mSteamEquivalentBuffer);
        aNBT.setBoolean("mSuperheatedTier", mSuperheatedTier);
        aNBT.setInteger("mOutputTier", mOutputTier);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        // 旧档已写值保留，缺省回落 0%（新机 0% 起步）
        mHeat = aNBT.hasKey("mHeat") ? aNBT.getDouble("mHeat") : HEAT_START;
        mSteamEquivalentBuffer = aNBT.getLong("mSteamEquivalentBuffer");
        mSuperheatedTier = aNBT.getBoolean("mSuperheatedTier");
        // 旧档无 mOutputTier 回落 100；白名单校验，非法值（损坏/篡改档）同样回落 100
        int tier = aNBT.hasKey("mOutputTier") ? aNBT.getInteger("mOutputTier") : OUTPUT_TIER_DEFAULT;
        mOutputTier = isValidOutputTier(tier) ? tier : OUTPUT_TIER_DEFAULT;
    }

    // ===== GUI / 对齐 / 杂项 =====

    @Override
    protected MTEMultiBlockBaseGui<?> getGui() {
        return new MTEThermoChemicalDenseSteamGeneratorGui(this);
    }

    @Override
    public boolean getDefaultHasMaintenanceChecks() {
        return true; // 维护检查保持基类默认开启（显式声明，防上游默认变更静默破坏契约）
    }

    @Override
    public boolean shouldDisplayCheckRecipeResult() {
        return false;
    }

    @Override
    protected IAlignmentLimits getInitialAlignmentLimits() {
        return (d, r, f) -> d.offsetY == 0 && r.isNotRotated() && !f.isVerticallyFliped();
    }

    @Override
    public int getMaxEfficiency(ItemStack aStack) {
        return 10000;
    }

    // 复刻 GT5U 大型钨钢锅炉官方 casing+overlay 逐层组装语义（beta-1 无 casing 纹理提供者接口，
    // 不 implements，循 MTEGearSteamCompressor 手工组装逐层等价范式）：
    // 正面 = casing 底层 + overlay 层（OFF/ON 同款大型钨钢锅炉，随面向旋转）+ 辉光层（glow，随面向旋转）。
    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int aColorIndex, boolean aActive, boolean aRedstone) {
        if (side == facing) {
            return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(getCasingTextureID()),
                TextureFactory.builder()
                    .addIcon(aActive ? OVERLAY_FRONT_LARGE_BOILER_ACTIVE : OVERLAY_FRONT_LARGE_BOILER)
                    .extFacing()
                    .build(),
                TextureFactory.builder()
                    .addIcon(aActive ? OVERLAY_FRONT_LARGE_BOILER_ACTIVE_GLOW : OVERLAY_FRONT_LARGE_BOILER_GLOW)
                    .extFacing()
                    .glow()
                    .build() };
        }
        return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(getCasingTextureID()) };
    }

    @Override
    public String[] getInfoData() {
        ArrayList<String> info = new ArrayList<>();
        info.add(EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.tcds.type"));
        if (!mMachine) {
            info.add(EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.gui.building"));
            return info.toArray(new String[0]);
        }
        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.tcds.heat")
                + EnumChatFormatting.GOLD
                + String.format("%.1f%%", mHeat));
        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.tcds.output")
                + EnumChatFormatting.AQUA
                + NumberFormatUtil.formatNumber(mCurrentOutputEquivalent)
                + " L/t "
                + EnumChatFormatting.WHITE
                + outputModeSuffix());
        return info.toArray(new String[0]);
    }

    // 遗留 GUI 文本路径（modular UI 终端行由 MTEThermoChemicalDenseSteamGeneratorGui 承载，LGB 双路径同范式）
    @Deprecated
    @Override
    protected void drawTexts(DynamicPositionedColumn screenElements, SlotWidget inventorySlot) {
        super.drawTexts(screenElements, inventorySlot);
        screenElements
            .widget(
                TextWidget
                    .dynamicString(
                        () -> hasInvalidChipSlotItem()
                            ? EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.gui.tcds.chip_warn")
                            : " ")
                    .setSynced(false)
                    .setDefaultColor(COLOR_TEXT_WHITE.get()))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.gui.tcds.heat")
                        + EnumChatFormatting.GOLD
                        + String.format("%.1f%%", mHeat)
                        + " "
                        + EnumChatFormatting.RESET))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.gui.tcds.fuel")
                        + EnumChatFormatting.AQUA
                        + fuelDisplayText()
                        + EnumChatFormatting.RESET))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.gui.tcds.output")
                        + EnumChatFormatting.AQUA
                        + NumberFormatUtil.formatNumber(mCurrentOutputEquivalent)
                        + " L/t "
                        + EnumChatFormatting.WHITE
                        + outputModeSuffix()
                        + EnumChatFormatting.RESET))
            .widget(new FakeSyncWidget.DoubleSyncer(() -> mHeat, val -> mHeat = val))
            .widget(
                new FakeSyncWidget.IntegerSyncer(() -> mCurrentOutputEquivalent, val -> mCurrentOutputEquivalent = val))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mCurrentFuelKind, val -> mCurrentFuelKind = val))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mCurrentFuelValue, val -> mCurrentFuelValue = val))
            .widget(new FakeSyncWidget.StringSyncer(() -> mCurrentFuelFluidName, val -> mCurrentFuelFluidName = val))
            .widget(new FakeSyncWidget.StringSyncer(() -> mCurrentFuelLiquidName, val -> mCurrentFuelLiquidName = val))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mCurrentSynergyPercent, val -> mCurrentSynergyPercent = val))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mCurrentSynergyTemp, val -> mCurrentSynergyTemp = val));
    }

    /**
     * 燃料状态文本：无燃料 / 具体燃料流体名·热值（EU/L）/ 协同模式（kind=3）双燃料名+协同系数（lang 模板拼装）。
     * 名称来自 mCurrentFuelFluidName / mCurrentFuelLiquidName（GUI 同步态），客户端经 FluidRegistry 本地化；
     * 注册表未命中时回退显示注册名字符串。
     */
    public String fuelDisplayText() {
        if (mCurrentFuelKind <= 0 || mCurrentFuelValue <= 0) {
            return StatCollector.translateToLocal("gtsr.gui.tcds.fuel_none");
        }
        String gasName = localizedFluidName(mCurrentFuelFluidName);
        if (mCurrentFuelKind == 3) {
            return StatCollector.translateToLocalFormatted(
                "gtsr.gui.tcds.fuel_synergy",
                gasName,
                localizedFluidName(mCurrentFuelLiquidName),
                mCurrentSynergyPercent);
        }
        return gasName + " · " + NumberFormatUtil.formatNumber(mCurrentFuelValue) + " EU/L";
    }

    /** 流体注册名 → 本地化名；注册表未命中回退注册名字符串 */
    private static String localizedFluidName(String fluidName) {
        Fluid f = FluidRegistry.getFluid(fluidName);
        return f != null ? f.getLocalizedName(new FluidStack(f, 0)) : fluidName;
    }

    // ===== Tooltip（三段式：Info / Structure / Additional + 品牌尾缀）=====

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal("gtsr.tooltip.tcds.type"))
            .addInfo(EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.tooltip.tcds.desc"))
            .addInfo(EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.tcds.desc_2"))
            .addSeparator()
            // 数值段（Java 端配色拼接：BLUE 标签 + GOLD 数字 + GRAY 单位/后缀；数值硬编码，单位文案走 lang）
            .addInfo(
                EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.tcds.output_base")
                    + EnumChatFormatting.GOLD
                    + "210,000"
                    + EnumChatFormatting.GRAY
                    + " "
                    + StatCollector.translateToLocal("gtsr.tooltip.tcds.output_base_unit"))
            .addInfo(
                EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.tcds.fuel_consumption")
                    + EnumChatFormatting.GRAY
                    + StatCollector.translateToLocal("gtsr.tooltip.tcds.fuel_consumption_unit"))
            .addInfo(
                EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.tcds.fuel_threshold")
                    + EnumChatFormatting.GOLD
                    + "350"
                    + EnumChatFormatting.GRAY
                    + " / "
                    + EnumChatFormatting.GOLD
                    + "450"
                    + EnumChatFormatting.GRAY
                    + " EU/L")
            .addInfo(
                EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.tcds.water_ratio")
                    + EnumChatFormatting.GOLD
                    + "160"
                    + EnumChatFormatting.GRAY
                    + " "
                    + StatCollector.translateToLocal("gtsr.tooltip.tcds.water_ratio_unit"))
            .addInfo(
                EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.tcds.air")
                    + EnumChatFormatting.GOLD
                    + "100"
                    + EnumChatFormatting.GRAY
                    + " "
                    + StatCollector.translateToLocal("gtsr.tooltip.tcds.air_unit"))
            .addInfo(
                EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.tcds.buffer")
                    + EnumChatFormatting.GOLD
                    + "840,000,000"
                    + EnumChatFormatting.GRAY
                    + " L")
            .addInfo(EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.tooltip.tcds.heat"))
            .addInfo(EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.tcds.synergy"))
            .addInfo(EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.tooltip.tcds.water_warning"))
            .addSeparator()
            .addInfo(EnumChatFormatting.LIGHT_PURPLE + StatCollector.translateToLocal("gtsr.tooltip.tcds.chip_info"))
            .addSeparator()
            // [GT-compat] beta 兼容层（beta1/beta2/beta3）：beta-3 起始参数序为 (w,h,l)，实参按 beta-3 语义排列
            .beginStructureBlock(12, 11, 28, false)
            .addController(EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.tooltip.tcds.ctrl"))
            .addMaintenanceHatch(StatCollector.translateToLocal("gtsr.tooltip.tcds.maintenance"))
            .addMufflerHatch(StatCollector.translateToLocal("gtsr.tooltip.tcds.muffler"))
            .addInputHatch(StatCollector.translateToLocal("gtsr.tooltip.tcds.input_hatch"), 1)
            .addOutputHatch(StatCollector.translateToLocal("gtsr.tooltip.tcds.output_hatch"), 1)
            .addStructureInfo("")
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.tcds.casing"), 1191, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.tcds.frame"), 218, false)
            .addInfo(EnumChatFormatting.LIGHT_PURPLE + StatCollector.translateToLocal("gtsr.tooltip.tcds.chip_desc"))
            .addSeparator()
            .addInfo(GTSRUtils.getAddedByLine())
            .toolTipFinisher();
        return tt;
    }
}
