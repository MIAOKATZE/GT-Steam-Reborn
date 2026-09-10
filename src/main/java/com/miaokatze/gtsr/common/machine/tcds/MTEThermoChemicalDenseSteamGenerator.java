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
import java.util.Locale;

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
 * <li>供给驱动：设定流量 mFlow（L/t，NBT 持久化，>=1 默认 100，setter 钳 >=1）；每族实际流量 =
 * min(可用供给, mFlow)，部分供给自动降流量不停机；燃料每族取可用者中热值最高者，两族各有可用燃料
 * 即双燃料直加（无映射/矩阵门槛）</li>
 * <li>产出 = Σ各族 实际流量 × 热值 × η(mFlow) × 2 × (heat/100)；η(F)（仅乘产出热值项，不做消耗倍率）：
 * F<=500 → 1.0；500<F<=1000 → 500/F；F>1000 → 0.5×(1000/F)^0.4307（指数 = ln0.5/ln0.2，锚点
 * η(1000)=0.5、η(5000)=0.25；末段渐近 0 永不为 0）</li>
 * <li>最大热量上限 capTarget = maxHeatForTier(档位) × flowHeatFactor(mFlow)；flowHeatFactor(F)：
 * F<100 → max(0.5, F/100)（等比衰减，50% 地板）；100<=F<=500 → 1+0.5×(F-100)/400（线性升至 1.5）；
 * F>500 → 1.5；heat > capTarget 停机降温；输出档位九档 {100,80,60,40,20,10,5,2,1}%，最大热量查表
 * {200,160,120,80,40,10,5,2,1}%；热量初值 0%——新机 0% 起步，运行自热至 100%，富余可至 capTarget；
 * 旧档已写值保留，缺省回落 0%；热量每 20 tick 结算：运行 <100% +0.5、100%≤heat<heatCap +0.01、
 * 停机 −1（下限 0）</li>
 * <li>消耗：燃料 = 各族实际流量；空气 = Σ实际流量 × 100；蒸馏水 = 产出 ÷ 160；两阶段探测-实扣
 * （探测量 = 实际需求，与扣料一致）；空气/水任一不足 → 本 tick NO_RECIPE（不产出、不扣任何流体，
 * 二元停机降温，无线性降载）；缺水且 heat>100% 爆炸；实扣顺序：燃气 → 燃油 → 空气 → 蒸馏水</li>
 * <li>过热档：单燃料热值达族阈值（燃气 350 / 燃油 450 EU/L，不过线仍照常燃烧产普通档），
 * 双燃料需双过热（燃气 ≥350 且燃油 ≥450）</li>
 * <li>产出先进内部蒸汽当量缓冲（上限 840,000,000 L 当量，满则本 tick 停产不扣料），每 tick 按档位
 * 换算向全部输出仓分摊：非芯片 1:1（普通蒸汽/ic2superheatedsteam）；芯片致密蒸汽、
 * 致密过热蒸汽均 ÷1000，余量保留在缓冲</li>
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
    /** 热量初值（%）：新机 0% 起步，运行自热至 100%，供给富余可至热量链上限；旧档已写值保留，缺省回落 0% */
    private static final double HEAT_START = 0.0d;
    /** 设定流量缺省值（L/t）：旧档无 mFlow / 非法值回落 */
    private static final int FLOW_DEFAULT = 100;
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
    /**
     * 输出档位白名单（%）：螺丝刀 Shift+右键按此顺序轮换；前五档最大热量 = 2×档位，后四档 = 档位本身
     * （从 100% 基准分别降低 90/95/98/99）
     */
    private static final int[] OUTPUT_TIERS = { 100, 80, 60, 40, 20, 10, 5, 2, 1 };
    /**
     * 各档位最大热量（%）静态查表（与 OUTPUT_TIERS 同序，显式表非隐式公式）：v1.20.8 旧五档取值不变，
     * 旧档 NBT 值仍合法；产出自然封顶 = BASE × maxHeat ÷ 100
     */
    private static final int[] MAX_HEAT_BY_TIER = { 200, 160, 120, 80, 40, 10, 5, 2, 1 };
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
    /** 当前燃料族（GUI 显示）：0 无 / 1 燃气 / 2 燃油 / 3 油气双燃 */
    public int mCurrentFuelKind = 0;
    /**
     * 当前燃料具体流体名（GUI 显示态，服务端赋值客户端消费，不进 NBT）：
     * 单燃料 = 当前燃料；双燃料 = 燃气族燃料
     */
    public String mCurrentFuelFluidName = "";
    /** 本 tick 单燃料消耗（L/t，GUI 显示态，服务端赋值客户端消费，不进 NBT；仅 kind=1/2 非零） */
    public int mCurrentFuelConsumption = 0;
    /** 双燃料模式本 tick 燃气实际流量（L/t，GUI 显示态，不进 NBT；仅 kind=3 非零） */
    public int mCurrentGasConsumption = 0;
    /** 双燃料模式本 tick 燃油实际流量（L/t，GUI 显示态，不进 NBT；仅 kind=3 非零） */
    public int mCurrentLiquidConsumption = 0;
    /** 本 tick 空气消耗（L/t，GUI 显示态，服务端赋值客户端消费，不进 NBT） */
    public int mCurrentAirConsumption = 0;
    /** 本 tick 蒸馏水消耗（L/t，GUI 显示态，服务端赋值客户端消费，不进 NBT） */
    public int mCurrentWaterConsumption = 0;
    /**
     * 螺丝刀可切的输出档位（%）：取值 ∈ {100, 80, 60, 40, 20, 10, 5, 2, 1}（NBT 持久化，白名单校验，
     * v1.20.8 旧五档值仍合法），最大热量 = maxHeatForTier(档位) 查表（100 档 = 200%，行为与旧版一致）
     */
    public int mOutputTier = OUTPUT_TIER_DEFAULT;
    /**
     * 设定流量（L/t）：每族实际流量 = min(可用供给, 设定流量)（NBT 持久化，>=1，默认 100，非法回落 100）；
     * 产出效率 η 与最大热量上限因子 flowHeatFactor 均按设定流量取值
     */
    public int mFlow = FLOW_DEFAULT;
    /** 双燃料模式当前燃油具体流体名（GUI 显示态，服务端赋值客户端消费，不进 NBT） */
    public String mCurrentFuelLiquidName = "";
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

    // GTSR 进度词条：GUI 终端统一数值行（LSOA 配色纪律——标签 WHITE、产量类数值 GREEN、消耗类 GOLD、
    // 状态提示 AQUA）；行序：热量 → 输出档位 → 流量 → 蒸汽输出 → 燃料段（单燃料一行 / 双燃料两行，
    // 零值行自动隐藏）→ 空气消耗 → 蒸馏水消耗
    private void registerProgressEntries() {
        // 1 热量：当前% / 上限%（= 档位查表上限 × 流量热量因子，如 "250% (200×1.25)"）；零值仍显示（新机 0% 起步）
        registerEntry(
            GTSRProgressEntry
                .ofCustom(
                    "tcds_heat",
                    "gtsr.gui.tcds.heat",
                    EnumChatFormatting.GOLD,
                    () -> mHeat,
                    v -> String.format(Locale.ENGLISH, "%.1f%%", v) + " / " + heatCapDisplay())
                .showZero());
        // 2 输出档位：当前% 与最大热量%（查表值）
        registerEntryCustom(
            "tcds_tier",
            "gtsr.gui.tcds.output_tier",
            EnumChatFormatting.AQUA,
            () -> mOutputTier,
            v -> (int) v + "% / " + maxHeatForTier((int) v) + "%");
        // 3 流量：设定 L/t + 理论最大热量（= 档位上限 × 流量热量因子，供给足额时的热量封顶）
        registerEntryCustom(
            "tcds_flow",
            "gtsr.gui.tcds.flow",
            EnumChatFormatting.AQUA,
            () -> mFlow,
            v -> NumberFormatUtil.formatNumber((long) v) + " L/t "
                + EnumChatFormatting.WHITE
                + StatCollector.translateToLocalFormatted("gtsr.gui.tcds.flow_heat", heatCapDisplay()));
        // 4 蒸汽输出：当量 L/t + 模式后缀（致密档注明 ÷1000）
        registerEntryCustom(
            "tcds_output",
            "gtsr.gui.tcds.output",
            EnumChatFormatting.GREEN,
            () -> mCurrentOutputEquivalent,
            v -> NumberFormatUtil.formatNumber((long) v) + " L/t "
                + EnumChatFormatting.WHITE
                + outputModeSuffix()
                + (hasDenseSteamChip() ? EnumChatFormatting.GREEN + " ÷1000" : ""));
        // 5a 单燃料行（仅 kind=1/2 非零显示）：燃料名 · 实际流量 L/t
        registerEntryCustom(
            "tcds_fuel",
            "gtsr.gui.tcds.fuel",
            EnumChatFormatting.GOLD,
            () -> mCurrentFuelConsumption,
            v -> localizedFluidName(mCurrentFuelFluidName) + " · " + NumberFormatUtil.formatNumber((long) v) + " L/t");
        // 5b 双燃料燃气行（仅 kind=3 非零显示）
        registerEntryCustom(
            "tcds_fuel_gas",
            "gtsr.gui.tcds.fuel_gas",
            EnumChatFormatting.GOLD,
            () -> mCurrentGasConsumption,
            v -> localizedFluidName(mCurrentFuelFluidName) + " · " + NumberFormatUtil.formatNumber((long) v) + " L/t");
        // 5c 双燃料燃油行（仅 kind=3 非零显示）
        registerEntryCustom(
            "tcds_fuel_liquid",
            "gtsr.gui.tcds.fuel_liquid",
            EnumChatFormatting.GOLD,
            () -> mCurrentLiquidConsumption,
            v -> localizedFluidName(mCurrentFuelLiquidName) + " · " + NumberFormatUtil.formatNumber((long) v) + " L/t");
        // 6 空气消耗 L/t
        registerEntryCustom(
            "tcds_air",
            "gtsr.gui.tcds.air",
            EnumChatFormatting.GOLD,
            () -> mCurrentAirConsumption,
            v -> NumberFormatUtil.formatNumber((long) v) + " L/t");
        // 7 蒸馏水消耗 L/t
        registerEntryCustom(
            "tcds_water",
            "gtsr.gui.tcds.water",
            EnumChatFormatting.GOLD,
            () -> mCurrentWaterConsumption,
            v -> NumberFormatUtil.formatNumber((long) v) + " L/t");
    }

    /**
     * 热量上限显示（GUI 热量行右半 / 流量行理论最大热量）：= 档位查表上限 × 流量热量因子，
     * 因子 ≠ 1 时带分解，如 "250% (200×1.25)"；因子为 1 时仅显示上限（如 "200%"）。
     */
    public String heatCapDisplay() {
        int tierCap = maxHeatForTier(mOutputTier);
        double factor = flowHeatFactor(mFlow);
        double cap = tierCap * factor;
        String capText = cap == Math.floor(cap) ? String.valueOf((long) cap)
            : String.format(Locale.ENGLISH, "%.1f", cap);
        if (factor == 1.0d) return capText + "%";
        return capText + "% (" + tierCap + "×" + String.format(Locale.ENGLISH, "%.2f", factor) + ")";
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

    // ===== 燃料识别（双族扫描：每族取可用者中热值最高者，两族并存即双燃料直加）=====

    /** 燃料候选：流体 + 运行时热值（EU/L，findFuel(...).mSpecialValue）+ 族（true = 燃气族 / false = 燃油族） */
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

    /** 单次输入仓扫描结果：燃气/燃油各族可用最高热值代表 */
    private static final class FuelScan {

        private FuelCandidate bestGas;
        private FuelCandidate bestLiquid;

        private void record(FuelCandidate candidate) {
            // 每族取可用最高热值（同热值保留先扫到者，原 findBestFuel 语义）
            if (candidate.gasFamily) {
                if (bestGas == null || candidate.heatValue > bestGas.heatValue) bestGas = candidate;
            } else if (bestLiquid == null || candidate.heatValue > bestLiquid.heatValue) {
                bestLiquid = candidate;
            }
        }
    }

    /**
     * 扫全部输入仓（单次）：燃气族走 gasTurbineFuels（阈值 350）、燃油族走 dieselFuels ∪ denseLiquidFuels
     * （阈值 450），热值取 findFuel(...).mSpecialValue；两 map 均无 → 非燃料。每族同热值保留先扫到者。
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
                    scan.record(new FuelCandidate(fs.getFluid(), gasRecipe.mSpecialValue, true));
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
                    scan.record(new FuelCandidate(fs.getFluid(), liquidValue, false));
                }
            }
        }
        return scan;
    }

    // ===== 供给驱动（设定流量 setter / 效率曲线 / 流量热量因子）=====

    /** 设定流量读取（终端 GUI 同步 getter） */
    public int getFlow() {
        return mFlow;
    }

    /** 设定流量服务端钳制（终端 GUI 输入回写入口）：下限 1 L/t，无上限（超高流量由 η 曲线自然衰减） */
    public void setFlow(int flow) {
        mFlow = Math.max(1, flow);
        getBaseMetaTileEntity().markDirty();
    }

    /**
     * 流量燃烧效率 η（仅乘产出热值项，不做消耗倍率）：F<=500 → 1.0；500<F<=1000 → 500/F；
     * F>1000 → 0.5×(1000/F)^0.4307（指数 = ln(0.5)/ln(0.2)，锚点 η(1000)=0.5、η(5000)=0.25）；
     * 末段同斜率外推，渐近 0 永不为 0。
     */
    public static double flowEfficiency(int flow) {
        if (flow <= 500) return 1.0d;
        if (flow <= 1000) return 500.0d / flow;
        return 0.5d * Math.pow(1000.0d / flow, 0.4307d);
    }

    /**
     * 流量热量因子（最大热量上限缩放，随设定流量）：F<100 → max(0.5, F/100)（等比衰减，50% 地板）；
     * 100<=F<=500 → 1+0.5×(F-100)/400（线性升至 1.5）；F>500 → 1.5（封顶）。
     */
    public static double flowHeatFactor(int flow) {
        if (flow < 100) return Math.max(0.5d, flow / 100.0d);
        if (flow <= 500) return 1.0d + 0.5d * (flow - 100) / 400.0d;
        return 1.5d;
    }

    // ===== 每 tick 生产核心（checkProcessing 逐 tick 驱动：mMaxProgresstime = 1）=====

    @Override
    public CheckRecipeResult checkProcessing() {
        mCurrentOutputEquivalent = 0;
        mCurrentFuelKind = 0;
        mCurrentFuelFluidName = "";
        mCurrentFuelLiquidName = "";
        mCurrentFuelConsumption = 0;
        mCurrentGasConsumption = 0;
        mCurrentLiquidConsumption = 0;
        mCurrentAirConsumption = 0;
        mCurrentWaterConsumption = 0;
        if (!mMachine || !getBaseMetaTileEntity().isAllowedToWork()) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }
        // 缓冲满 → 本 tick 停产（不扣料，热量按停机衰减）
        if (mSteamEquivalentBuffer >= STEAM_BUFFER_CAPACITY) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }
        // a. 燃料识别（单次扫描，每族取最高热值）：两族皆无可用燃料 → 停机
        FuelScan scan = scanFuels();
        if (scan.bestGas == null && scan.bestLiquid == null) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }
        return processFuels(scan.bestGas, scan.bestLiquid);
    }

    /**
     * 供给驱动生产路径（探测-实扣两阶段，探测量 = 实际需求）：燃气/燃油各族取可用最高热值者，
     * 两族并存即双燃料直加。
     * <ul>
     * <li>每族实际流量 = min(mFlow, 可用供给)：部分供给自动降流量不停机；燃料消耗 = 各族实际流量</li>
     * <li>产出 = Σ各族 实际流量 × 热值 × η(mFlow) × 2 × (heat/100)</li>
     * <li>capTarget = maxHeatForTier(档位) × flowHeatFactor(mFlow)；heat > capTarget → 停机降温</li>
     * <li>空气 = Σ实际流量 × 100、蒸馏水 = 产出 ÷ 160，任一不足 → 本 tick 不产出不扣料（二元停机）；
     * 缺水且 heat>100% → 爆炸；实扣顺序：燃气 → 燃油 → 空气 → 蒸馏水</li>
     * </ul>
     */
    private CheckRecipeResult processFuels(FuelCandidate gas, FuelCandidate liquid) {
        // a. 每族实际流量 = min(mFlow, 可用供给)（探测窗口恰取 mFlow，probeFluidAmountAcross 返回值已按窗口封顶）
        int gasFlow = 0;
        int liquidFlow = 0;
        if (gas != null) {
            gasFlow = (int) Math.min(
                mFlow,
                GTSRHatchFluidAccess.probeFluidAmountAcross(mInputHatches, new FluidStack(gas.fluid, mFlow)));
        }
        if (liquid != null) {
            liquidFlow = (int) Math.min(
                mFlow,
                GTSRHatchFluidAccess.probeFluidAmountAcross(mInputHatches, new FluidStack(liquid.fluid, mFlow)));
        }
        long totalFlow = (long) gasFlow + liquidFlow;
        if (totalFlow <= 0) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        // b. 热量上限 capTarget = maxHeatForTier(档位) × flowHeatFactor(设定流量)；heat > capTarget → 停机降温
        double capTarget = maxHeatForTier(mOutputTier) * flowHeatFactor(mFlow);
        if (mHeat > capTarget) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        // c. 产出（long/double 全程防溢出）：Σ各族 流量 × 热值 × η(mFlow) × 2 × (heat/100)
        double eta = flowEfficiency(mFlow);
        double heatFactor = mHeat / 100.0d;
        long output = 0L;
        if (gasFlow > 0) {
            output += Math.round(gasFlow * (double) gas.heatValue * eta * 2.0d * heatFactor);
        }
        if (liquidFlow > 0) {
            output += Math.round(liquidFlow * (double) liquid.heatValue * eta * 2.0d * heatFactor);
        }

        // d. 空气与蒸馏水需求（long 计算，int 饱和钳制）：空气 = Σ实际流量 × 100；蒸馏水 = 产出 ÷ 160
        int airToConsume = saturateInt(totalFlow * (long) AIR_PER_FUEL);
        int waterToConsume = saturateInt(Math.round(output / (double) STEAM_PER_WATER));

        // e. 二元足额裁决（先全查后扣）：空气不足 → 本 tick 不产出、不扣任何流体（停机降温）
        if (airToConsume > 0
            && !GTSRHatchFluidAccess.hasEnoughAcross(mInputHatches, Materials.Air.getGas(airToConsume))) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        // f. 缺水裁决：蒸馏水不足 → 本 tick 完全不产出、不扣任何流体；
        // heat > 100% → 爆炸（基类 explodeMultiblock，MTEMultiBlockBase:1515）；≤100% → 安全停摆
        FluidStack waterWant = GTModHandler.getDistilledWater(waterToConsume);
        if (waterToConsume > 0 && !GTSRHatchFluidAccess.hasEnoughAcross(mInputHatches, waterWant)) {
            if (mHeat > 100.0d) {
                explodeMultiblock();
            }
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        // g. 先全过后扣（平炉 MTESiemensMartinFurnace 两阶段范式）：燃气 → 燃油 → 空气 → 蒸馏水
        if (gasFlow > 0) {
            GTSRHatchFluidAccess.depleteFluidAcross(mInputHatches, new FluidStack(gas.fluid, gasFlow));
        }
        if (liquidFlow > 0) {
            GTSRHatchFluidAccess.depleteFluidAcross(mInputHatches, new FluidStack(liquid.fluid, liquidFlow));
        }
        if (airToConsume > 0) {
            GTSRHatchFluidAccess.depleteFluidAcross(mInputHatches, Materials.Air.getGas(airToConsume));
        }
        if (waterToConsume > 0) {
            GTSRHatchFluidAccess.depleteFluidAcross(mInputHatches, waterWant);
        }

        // h. 缓冲入账 + 产出档位判定（单燃料按族阈值 / 双燃料需双过热；芯片在落仓时选择致密变体）+ GUI 显示字段
        mCurrentOutputEquivalent = (int) Math.min(Integer.MAX_VALUE, output);
        mSteamEquivalentBuffer += output;
        boolean dual = gas != null && liquid != null;
        if (dual) {
            mCurrentFuelKind = 3;
            mCurrentFuelFluidName = gas.fluid.getName();
            mCurrentFuelLiquidName = liquid.fluid.getName();
            mCurrentGasConsumption = gasFlow;
            mCurrentLiquidConsumption = liquidFlow;
            mSuperheatedTier = gas.heatValue >= GAS_SUPERHEAT_THRESHOLD
                && liquid.heatValue >= LIQUID_SUPERHEAT_THRESHOLD;
        } else {
            FuelCandidate fuel = gas != null ? gas : liquid;
            mCurrentFuelKind = fuel.gasFamily ? 1 : 2;
            mCurrentFuelFluidName = fuel.fluid.getName();
            mCurrentFuelConsumption = fuel.gasFamily ? gasFlow : liquidFlow;
            mSuperheatedTier = fuel.heatValue
                >= (fuel.gasFamily ? GAS_SUPERHEAT_THRESHOLD : LIQUID_SUPERHEAT_THRESHOLD);
        }
        mCurrentAirConsumption = airToConsume;
        mCurrentWaterConsumption = waterToConsume;
        mLastHeatCap = capTarget;

        // mMaxProgresstime = 1：基类 runMachine 每 tick 完成一次并立刻 checkRecipe 重启，实现连续逐 tick 生产
        mMaxProgresstime = 1;
        mEfficiency = 10000;
        mEfficiencyIncrease = 10000;
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    /** long → int 饱和钳制（消耗量防溢出）：超出 int 域时按 Integer.MAX_VALUE 截断 */
    private static int saturateInt(long value) {
        return (int) Math.min(Integer.MAX_VALUE, value);
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
     * 档位 → 最大热量（%）静态查表（MAX_HEAT_BY_TIER，显式表非隐式公式）：{100,80,60,40,20} → 2×档位、
     * {10,5,2,1} → 档位本身；白名单外档位兜底回落 100 档（200%）。
     */
    public static int maxHeatForTier(int tier) {
        for (int i = 0; i < OUTPUT_TIERS.length; i++) {
            if (OUTPUT_TIERS[i] == tier) return MAX_HEAT_BY_TIER[i];
        }
        return MAX_HEAT_BY_TIER[0];
    }

    /**
     * 螺丝刀右键：潜行轮换输出档位 100→80→60→40→20→10→5→2→1→100 并聊天栏播报（最大热量按查表值）；
     * 非潜行仅提示不切换（GT5U 分发层无 shift 门控，须自行判定）。降档后 mLastHeatCap 无需强制复位：
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
        GTUtility.sendChatTrans(aPlayer, "gtsr.chat.tcds.output_tier", mOutputTier, maxHeatForTier(mOutputTier));
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

    // ===== NBT（heat / 缓冲 / 档位 / 设定流量；工作态由基类 mMaxProgresstime/mProgresstime 持久化）=====

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setDouble("mHeat", mHeat);
        aNBT.setLong("mSteamEquivalentBuffer", mSteamEquivalentBuffer);
        aNBT.setBoolean("mSuperheatedTier", mSuperheatedTier);
        aNBT.setInteger("mOutputTier", mOutputTier);
        aNBT.setInteger("mFlow", mFlow);
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
        // 旧档无 mFlow 回落 100；非法值（<1，损坏/篡改档）同样回落 100
        int flow = aNBT.hasKey("mFlow") ? aNBT.getInteger("mFlow") : FLOW_DEFAULT;
        mFlow = flow >= 1 ? flow : FLOW_DEFAULT;
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

    // 遗留 GUI 文本路径（modular UI 终端行由 MTEThermoChemicalDenseSteamGeneratorGui 承载，LGB 双路径同范式）。
    // 热量/燃料/产出显示行已由 GTSRProgressBar 词条替代（清显示层冗余），保留芯片告警行与
    // FakeSyncer 同步通道（数值行同步态词条系统与 GUI registerSyncValues 各自承载，此处通道照旧补全）。
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
            .widget(new FakeSyncWidget.DoubleSyncer(() -> mHeat, val -> mHeat = val))
            .widget(
                new FakeSyncWidget.IntegerSyncer(() -> mCurrentOutputEquivalent, val -> mCurrentOutputEquivalent = val))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mCurrentFuelKind, val -> mCurrentFuelKind = val))
            .widget(new FakeSyncWidget.StringSyncer(() -> mCurrentFuelFluidName, val -> mCurrentFuelFluidName = val))
            .widget(new FakeSyncWidget.StringSyncer(() -> mCurrentFuelLiquidName, val -> mCurrentFuelLiquidName = val))
            .widget(
                new FakeSyncWidget.IntegerSyncer(() -> mCurrentFuelConsumption, val -> mCurrentFuelConsumption = val))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mCurrentGasConsumption, val -> mCurrentGasConsumption = val))
            .widget(
                new FakeSyncWidget.IntegerSyncer(
                    () -> mCurrentLiquidConsumption,
                    val -> mCurrentLiquidConsumption = val))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mCurrentAirConsumption, val -> mCurrentAirConsumption = val))
            .widget(
                new FakeSyncWidget.IntegerSyncer(
                    () -> mCurrentWaterConsumption,
                    val -> mCurrentWaterConsumption = val));
    }

    /** 流体注册名 → 本地化名；注册表未命中回退注册名字符串 */
    private static String localizedFluidName(String fluidName) {
        Fluid f = FluidRegistry.getFluid(fluidName);
        return f != null ? f.getLocalizedName(new FluidStack(f, 0)) : fluidName;
    }

    // ===== Tooltip（仿 MTELargeSolarOverpressureArray.createTooltip 三段式：核心描述 / 关键参数 / 结构 + 品牌尾缀）=====

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        // 核心描述段：2 行 WHITE + 1 行 AQUA 强调（供给驱动燃烧机制）
        tt.addMachineType(StatCollector.translateToLocal("gtsr.tooltip.tcds.type"))
            .addInfo(EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.tooltip.tcds.desc"))
            .addInfo(EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.tooltip.tcds.desc_2"))
            .addInfo(EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.tcds.desc_3"))
            .addSeparator()
            // 关键参数段（LSOA 配色纪律：BLUE/GOLD 标签 + GOLD 数值 + GRAY 单位 + GREEN 括注；数值硬编码，模板走 lang）
            .addInfo(
                EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.tcds.output_formula")
                    + EnumChatFormatting.GOLD
                    + StatCollector.translateToLocal("gtsr.tooltip.tcds.output_formula_unit")
                    + EnumChatFormatting.GREEN
                    + " "
                    + StatCollector.translateToLocal("gtsr.tooltip.tcds.output_formula_note"))
            .addInfo(
                EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.tcds.fuel_consumption")
                    + EnumChatFormatting.AQUA
                    + StatCollector.translateToLocal("gtsr.tooltip.tcds.fuel_consumption_unit"))
            .addInfo(
                EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.tcds.flow")
                    + EnumChatFormatting.GOLD
                    + "100"
                    + EnumChatFormatting.GRAY
                    + " L/t"
                    + EnumChatFormatting.GREEN
                    + " "
                    + StatCollector.translateToLocal("gtsr.tooltip.tcds.flow_note"))
            .addInfo(
                EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.tcds.efficiency")
                    + EnumChatFormatting.GOLD
                    + "100% / ~50% / ~25%"
                    + EnumChatFormatting.GRAY
                    + " 500 / 1000 / 5000 L/t"
                    + EnumChatFormatting.GREEN
                    + " "
                    + StatCollector.translateToLocal("gtsr.tooltip.tcds.efficiency_note"))
            .addInfo(
                EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.tcds.output_tiers")
                    + EnumChatFormatting.GOLD
                    + "100-80-60-40-20-10-5-2-1%"
                    + EnumChatFormatting.GRAY
                    + " "
                    + StatCollector.translateToLocal("gtsr.tooltip.tcds.output_tiers_unit"))
            .addInfo(
                EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.tcds.air_water")
                    + EnumChatFormatting.GOLD
                    + "x100 / ÷160"
                    + EnumChatFormatting.GRAY
                    + " "
                    + StatCollector.translateToLocal("gtsr.tooltip.tcds.air_water_unit"))
            .addInfo(EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.tooltip.tcds.water_warning"))
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
                EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.tcds.buffer")
                    + EnumChatFormatting.GOLD
                    + "840,000,000"
                    + EnumChatFormatting.GRAY
                    + " L")
            .addInfo(EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.tooltip.tcds.heat"))
            .addSeparator()
            .addInfo(EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.tcds.dual_fuel"))
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
