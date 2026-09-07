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
 * 热化学致密蒸汽发生系统（TCDS）：单燃料连续流体多方块，无 RecipeMap。
 * <p>
 * 数值模型（单一权威，常量集中本类）：
 * <ul>
 * <li>基准产出 BASE_OUTPUT_PER_TICK = 210,000 L/t 蒸汽当量（= 4,200,000 L/s）@100% 热量，随 heat 线性放大</li>
 * <li>燃料：单燃料，扫全部输入仓取可用者中热值最高；燃气族（gasTurbineFuels）过热阈值 350 EU/L，
 * 燃油族（dieselFuels ∪ denseLiquidFuels）过热阈值 450 EU/L；不过线仍照常燃烧产普通档蒸汽，不拒绝燃料</li>
 * <li>每 tick 顺序：基准需求 → fuelRatio（封顶 1）→ unconstrainedOutput/Fuel → airNeed = 燃料×100 →
 * airRatio（封顶 1，完全断气二元停机）→ 缺水二元停摆（heat>100% 爆炸）→ 全过后实扣 → 缓冲入账</li>
 * <li>heatCap = min(200, 100×rawFuelRatio, 100×rawAirRatio)（未封顶比例；heat>heatCap 停机降温，
 * 供给富余可把热量推到 200% 使产出翻倍）；热量初值 0%——新机 0% 起步，运行自热至 100%，
 * 供给富余可至 200%；旧档已写值保留，缺省回落 0%；热量每 20 tick 结算：运行 <100% +0.5、
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
    /** 当前燃料族（GUI 显示）：0 无 / 1 燃气 / 2 燃油 */
    public int mCurrentFuelKind = 0;
    /** 当前燃料热值 EU/L（GUI 显示） */
    public int mCurrentFuelValue = 0;
    /** 当前燃料具体流体名（GUI 显示态，服务端赋值客户端消费，不进 NBT） */
    public String mCurrentFuelFluidName = "";
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
                // 'B' 不锈钢框架：gt.blockframes meta 306（Materials.Steel.mMetaItemSubID）
                .addElement('B', ofBlock(GregTechAPI.sBlockFrames, Materials.Steel.mMetaItemSubID))
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

    // ===== 燃料识别（用户裁决：单燃料，取可用者中热值最高）=====

    /** 燃料候选：流体 + 热值（EU/L）+ 族（true = 燃气族 / false = 燃油族） */
    private static final class FuelCandidate {

        private final Fluid fluid;
        private final int heatValue;
        private final boolean gasFamily;

        private FuelCandidate(Fluid fluid, int heatValue, boolean gasFamily) {
            this.fluid = fluid;
            this.heatValue = heatValue;
            this.gasFamily = gasFamily;
        }
    }

    /**
     * 扫全部输入仓：燃气族走 gasTurbineFuels（阈值 350）、燃油族走 dieselFuels ∪ denseLiquidFuels
     * （阈值 450），热值取 findFuel(...).mSpecialValue；两 map 均无 → 非燃料。同热值保留先扫到者。
     * 查找范式 MTELargeTurbineGas.getFuelValue。
     */
    private FuelCandidate findBestFuel() {
        FuelCandidate best = null;
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
                    if (best == null || gasRecipe.mSpecialValue > best.heatValue) {
                        best = new FuelCandidate(fs.getFluid(), gasRecipe.mSpecialValue, true);
                    }
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
                if (liquidValue > 0 && (best == null || liquidValue > best.heatValue)) {
                    best = new FuelCandidate(fs.getFluid(), liquidValue, false);
                }
            }
        }
        return best;
    }

    // ===== 每 tick 生产核心（checkProcessing 逐 tick 驱动：mMaxProgresstime = 1）=====

    @Override
    public CheckRecipeResult checkProcessing() {
        mCurrentOutputEquivalent = 0;
        mCurrentFuelKind = 0;
        mCurrentFuelValue = 0;
        mCurrentFuelFluidName = "";
        if (!mMachine || !getBaseMetaTileEntity().isAllowedToWork()) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }
        // 缓冲满 → 本 tick 停产（不扣料，热量按停机衰减）
        if (mSteamEquivalentBuffer >= STEAM_BUFFER_CAPACITY) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }
        // a. 燃料识别：无可用燃料 → 停机
        FuelCandidate fuel = findBestFuel();
        if (fuel == null) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }
        mCurrentFuelKind = fuel.gasFamily ? 1 : 2;
        mCurrentFuelValue = fuel.heatValue;
        mCurrentFuelFluidName = fuel.fluid.getName();

        // 基准需求 = (210,000 ÷ 热值) × 0.5 × (1 − 0.005×(heat−100))；热量系数随热量升高而降
        double heatCoefficient = 0.5d * (1.0d - 0.005d * (mHeat - 100.0d));
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

        // f. heatCap = min(200, 100×rawFuelRatio, 100×rawAirRatio)；heat > heatCap → 停机降温
        double heatCap = Math.min(HEAT_MAX, Math.min(100.0d * rawFuelRatio, 100.0d * rawAirRatio));
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
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        // 旧档已写值保留，缺省回落 0%（新机 0% 起步）
        mHeat = aNBT.hasKey("mHeat") ? aNBT.getDouble("mHeat") : HEAT_START;
        mSteamEquivalentBuffer = aNBT.getLong("mSteamEquivalentBuffer");
        mSuperheatedTier = aNBT.getBoolean("mSuperheatedTier");
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
            .widget(new FakeSyncWidget.StringSyncer(() -> mCurrentFuelFluidName, val -> mCurrentFuelFluidName = val));
    }

    /**
     * 燃料状态文本：无燃料 / 具体燃料流体名·热值（EU/L）。名称来自 mCurrentFuelFluidName（GUI 同步态），
     * 客户端经 FluidRegistry 本地化；注册表未命中时回退显示注册名字符串。
     */
    public String fuelDisplayText() {
        if (mCurrentFuelKind <= 0 || mCurrentFuelValue <= 0) {
            return StatCollector.translateToLocal("gtsr.gui.tcds.fuel_none");
        }
        Fluid f = FluidRegistry.getFluid(mCurrentFuelFluidName);
        String name = f != null ? f.getLocalizedName(new FluidStack(f, 0)) : mCurrentFuelFluidName;
        return name + " · " + NumberFormatUtil.formatNumber(mCurrentFuelValue) + " EU/L";
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
