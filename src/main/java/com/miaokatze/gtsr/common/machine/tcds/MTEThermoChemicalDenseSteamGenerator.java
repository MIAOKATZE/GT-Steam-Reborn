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
 * <li>保底消耗（v1.20.9）：单燃料 demand = (BASE ÷ hv) × 0.5 × (1 − saving)、协同每种 0.25 当量同式，
 * saving = max(0, heat − 100) × 0.005（≤100% 恒 0，>100% 每超 1 个百分点节省 0.5%，等价每超 1 点 ×0.995）；
 * 消耗不含 heat/100 项（不随热量增长，只有 >100% 节省折扣），也不乘任何供给比例</li>
 * <li>二元足额判定（先全查后扣）：燃料与空气供给任一低于需求 → 本 tick NO_RECIPE（不产出、不扣任何流体，
 * 机器停机降温，无线性降载）；足额时满额扣料：各 demand、空气 = Σdemand × 100、蒸馏水 = 产出 ÷ 160；
 * 缺水二元停摆同前（heat>100% 爆炸）；实扣顺序：单燃料 燃料→空气→蒸馏水，协同 燃气→燃油→空气→蒸馏水</li>
 * <li>heatCap = min(热量链上限, 100×min(rawFuelRatio 们), 100×rawAirRatio)：rawRatio = available/demand
 * （不钳 1）；供给仅够 1× 时 cap=100（热量>100 自动停摆降温），供给富余按比例抬升至链上限；供给探测窗口 =
 * max(1, capFactor) × demand（capFactor = capTarget/100，正共燃温度据此可突破 200% 至 250%）；热量链上限
 * = maxHeatForTier(输出档位)（查表）+ 共燃温度（仅协同，钳 0，≤0 不燃烧；单燃料路径恒 >0）；输出档位九档
 * {100,80,60,40,20,10,5,2,1}%，最大热量查表 {200,160,120,80,40,10,5,2,1}%；热量初值 0%——新机 0% 起步，
 * 运行自热至 100%，供给富余可至链上限；旧档已写值保留，缺省回落 0%；热量每 20 tick 结算：运行 <100% +0.5、
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
    /** 热量初值（%）：新机 0% 起步，运行自热至 100%，供给富余可至热量链上限；旧档已写值保留，缺省回落 0% */
    private static final double HEAT_START = 0.0d;
    /** 保底消耗节省率（v1.20.9）：>100% 热量每超 1 个百分点总消耗 ×0.995（即节省 0.5%）；≤100% 恒无折扣 */
    private static final double HEAT_SAVING_PER_POINT = 0.005d;
    /** 单燃料消耗当量（保底消耗公式系数） */
    private static final double SINGLE_FUEL_QUOTA = 0.5d;
    /** 协同各燃料消耗当量（两种合计恒等于单燃料 0.5 当量，守恒） */
    private static final double SYNERGY_FUEL_QUOTA = 0.25d;
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
    /** 当前燃料族（GUI 显示）：0 无 / 1 燃气 / 2 燃油 / 3 油气协同 */
    public int mCurrentFuelKind = 0;
    /** 当前燃料具体流体名（GUI 显示态，服务端赋值客户端消费，不进 NBT） */
    public String mCurrentFuelFluidName = "";
    /** 本 tick 单燃料消耗（L/t，GUI 显示态，服务端赋值客户端消费，不进 NBT；仅 kind=1/2 非零） */
    public int mCurrentFuelConsumption = 0;
    /** 协同模式本 tick 燃气消耗（L/t，GUI 显示态，不进 NBT；仅 kind=3 非零） */
    public int mCurrentGasConsumption = 0;
    /** 协同模式本 tick 燃油消耗（L/t，GUI 显示态，不进 NBT；仅 kind=3 非零） */
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

    // GTSR 进度词条：GUI 终端统一数值行（v1.20.9 重排，LSOA 配色纪律——标签 WHITE、产量类数值 GREEN、
    // 消耗类 GOLD、状态提示 AQUA）；行序：热量 → 输出档位 → 蒸汽输出 → 燃料段（单燃料/协同互斥，
    // 零值行自动隐藏）→ 空气消耗 → 蒸馏水消耗
    private void registerProgressEntries() {
        // 1 热量：当前% / 上限%（协同含共燃温度分解，如 "218% (200+18)"）；零值仍显示（新机 0% 起步）
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
        // 3 蒸汽输出：当量 L/t + 模式后缀（致密档注明 ÷1000）
        registerEntryCustom(
            "tcds_output",
            "gtsr.gui.tcds.output",
            EnumChatFormatting.GREEN,
            () -> mCurrentOutputEquivalent,
            v -> NumberFormatUtil.formatNumber((long) v) + " L/t "
                + EnumChatFormatting.WHITE
                + outputModeSuffix()
                + (hasDenseSteamChip() ? EnumChatFormatting.GREEN + " ÷1000" : ""));
        // 4a 单燃料消耗（仅 kind=1/2 非零显示）：燃料名 · 消耗 L/t
        registerEntryCustom(
            "tcds_fuel",
            "gtsr.gui.tcds.fuel",
            EnumChatFormatting.GOLD,
            () -> mCurrentFuelConsumption,
            v -> localizedFluidName(mCurrentFuelFluidName) + " · " + NumberFormatUtil.formatNumber((long) v) + " L/t");
        // 4b 协同燃气消耗（仅 kind=3 非零显示）
        registerEntryCustom(
            "tcds_fuel_gas",
            "gtsr.gui.tcds.fuel_gas",
            EnumChatFormatting.GOLD,
            () -> mCurrentGasConsumption,
            v -> localizedFluidName(mCurrentFuelFluidName) + " · " + NumberFormatUtil.formatNumber((long) v) + " L/t");
        // 4c 协同燃油消耗（仅 kind=3 非零显示）
        registerEntryCustom(
            "tcds_fuel_liquid",
            "gtsr.gui.tcds.fuel_liquid",
            EnumChatFormatting.GOLD,
            () -> mCurrentLiquidConsumption,
            v -> localizedFluidName(mCurrentFuelLiquidName) + " · " + NumberFormatUtil.formatNumber((long) v) + " L/t");
        // 4d 协同系数（仅 kind=3 非零显示，附共燃温度 ±）
        registerEntryCustom(
            "tcds_synergy",
            "gtsr.gui.tcds.synergy",
            EnumChatFormatting.AQUA,
            () -> mCurrentSynergyPercent,
            v -> (int) v + "%" + synergyTempSuffix());
        // 5 空气消耗 L/t
        registerEntryCustom(
            "tcds_air",
            "gtsr.gui.tcds.air",
            EnumChatFormatting.GOLD,
            () -> mCurrentAirConsumption,
            v -> NumberFormatUtil.formatNumber((long) v) + " L/t");
        // 6 蒸馏水消耗 L/t
        registerEntryCustom(
            "tcds_water",
            "gtsr.gui.tcds.water",
            EnumChatFormatting.GOLD,
            () -> mCurrentWaterConsumption,
            v -> NumberFormatUtil.formatNumber((long) v) + " L/t");
    }

    /**
     * 热量上限显示（GUI 热量行右半）：档位查表上限 +（协同模式）共燃温度分解，
     * 如 "218% (200+18)" / "150% (200-50)"；非协同或温度 0 时仅显示链上限。
     */
    public String heatCapDisplay() {
        int tierCap = maxHeatForTier(mOutputTier);
        int temp = mCurrentFuelKind == 3 ? mCurrentSynergyTemp : 0;
        int cap = Math.max(0, tierCap + temp);
        return temp != 0 ? cap + "% (" + tierCap + (temp > 0 ? "+" : "-") + Math.abs(temp) + ")" : cap + "%";
    }

    /** 协同系数行共燃温度后缀（仅 kind=3 且温度非 0）：WHITE " · 共燃温度: %+d%%" */
    private String synergyTempSuffix() {
        if (mCurrentFuelKind != 3 || mCurrentSynergyTemp == 0) return "";
        return " " + EnumChatFormatting.WHITE
            + "· "
            + StatCollector.translateToLocalFormatted("gtsr.gui.tcds.synergy_temp", mCurrentSynergyTemp);
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
     * 协同输出 = 基准 × heat/100 × 系数/100（满额，v1.20.9 起不乘供给比例）；燃料需求与系数无关
     * （两种各 0.25 当量 ×(1−节省)，守恒）。热值一律运行时 findFuel(...).mSpecialValue 查询，矩阵不含热值。
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
        mCurrentFuelFluidName = "";
        mCurrentFuelLiquidName = "";
        mCurrentSynergyPercent = 0;
        mCurrentSynergyTemp = 0;
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

    /**
     * 单燃料生产路径（v1.20.9 保底消耗 + 二元足额）：燃气或燃油取可用热值最高者。
     * <ul>
     * <li>保底需求 demand = (BASE ÷ hv) × 0.5 × (1 − saving)，saving = max(0, heat−100)×0.005——
     * 不含 heat/100 项（消耗不随热量增长），不乘任何供给比例</li>
     * <li>二元足额（先全查后扣）：燃料与空气任一不足 → 本 tick 不产出、不扣任何流体，停机降温（不降载）；
     * 足额时满额扣料：round(demand)、round(airNeed)、水 = round(产出÷160)</li>
     * <li>heatCap = min(链上限 maxHeatForTier(档位), 100×rawFuelRatio, 100×rawAirRatio)（rawRatio 不钳 1）：
     * 供给仅够 1× 时 cap=100，热量>100 自动停摆降温；供给富余按比例抬升至链上限</li>
     * </ul>
     */
    private CheckRecipeResult processSingleFuel(FuelCandidate fuel) {
        mCurrentFuelKind = fuel.gasFamily ? 1 : 2;
        mCurrentFuelFluidName = fuel.fluid.getName();

        // a. 保底需求与空气需求（v1.20.9：消耗不随热量增长，只有 >100% 节省折扣）
        double demandFuel = ((double) BASE_OUTPUT_PER_TICK / fuel.heatValue) * SINGLE_FUEL_QUOTA
            * consumptionSavingFactor();
        double airNeed = demandFuel * AIR_PER_FUEL;

        // 热量链上限 capTarget = maxHeatForTier(档位)（单燃料路径恒 >0）；探测倍率 capFactor = capTarget/100
        double capTarget = maxHeatForTier(mOutputTier);
        double capFactor = capTarget / 100.0d;

        // b. 燃料探测：rawRatio = available/demand（不钳 1，供热上限富余度判定；demand≤0 守卫分支保留）
        double rawFuelRatio;
        if (demandFuel <= 0.0d) {
            rawFuelRatio = capFactor;
        } else {
            long availableFuel = GTSRHatchFluidAccess
                .probeFluidAmountAcross(mInputHatches, new FluidStack(fuel.fluid, probeAmount(capFactor, demandFuel)));
            rawFuelRatio = (double) availableFuel / demandFuel;
        }

        // c. 空气探测（同式；airNeed≤0 守卫分支保留）
        double rawAirRatio;
        if (airNeed <= 0.0d) {
            rawAirRatio = capFactor;
        } else {
            long availableAir = GTSRHatchFluidAccess
                .probeFluidAmountAcross(mInputHatches, Materials.Air.getGas(probeAmount(capFactor, airNeed)));
            rawAirRatio = (double) availableAir / airNeed;
        }

        // d. 二元足额判定：任一不足 → 本 tick 不产出、不扣任何流体（停机降温，无线性降载）
        if ((demandFuel > 0.0d && rawFuelRatio < 1.0d) || (airNeed > 0.0d && rawAirRatio < 1.0d)) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        // e. heatCap = min(capTarget, 100×rawFuelRatio, 100×rawAirRatio)；heat > heatCap → 停机降温
        double heatCap = Math.min(capTarget, Math.min(100.0d * rawFuelRatio, 100.0d * rawAirRatio));
        if (mHeat > heatCap) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        // f. 满额产出与消耗（不乘任何比例；消耗量四舍五入为整数 L）
        long outputRounded = Math.round(BASE_OUTPUT_PER_TICK * (mHeat / 100.0d));
        int fuelToConsume = (int) Math.round(demandFuel);
        int airToConsume = (int) Math.round(airNeed);
        int waterToConsume = (int) Math.round((double) outputRounded / STEAM_PER_WATER);

        // g. 缺水裁决（先查后扣）：蒸馏水不足 → 本 tick 完全不产出、不扣任何流体；
        // heat > 100% → 爆炸（基类 explodeMultiblock，MTEMultiBlockBase:1515）；≤100% → 安全停摆
        FluidStack waterWant = GTModHandler.getDistilledWater(waterToConsume);
        if (waterToConsume > 0 && !GTSRHatchFluidAccess.hasEnoughAcross(mInputHatches, waterWant)) {
            if (mHeat > 100.0d) {
                explodeMultiblock();
            }
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        // h. 先全过后扣（平炉 MTESiemensMartinFurnace 两阶段范式）：燃料 / 空气 / 蒸馏水顺序实扣
        if (fuelToConsume > 0) {
            GTSRHatchFluidAccess.depleteFluidAcross(mInputHatches, new FluidStack(fuel.fluid, fuelToConsume));
        }
        if (airToConsume > 0) {
            GTSRHatchFluidAccess.depleteFluidAcross(mInputHatches, Materials.Air.getGas(airToConsume));
        }
        if (waterToConsume > 0) {
            GTSRHatchFluidAccess.depleteFluidAcross(mInputHatches, waterWant);
        }

        // i. 缓冲入账 + 产出档位判定（热值达阈值 → 过热档；芯片在落仓时选择致密变体）+ GUI 消耗字段
        mCurrentOutputEquivalent = (int) Math.min(Integer.MAX_VALUE, outputRounded);
        mSteamEquivalentBuffer += outputRounded;
        mSuperheatedTier = fuel.heatValue >= (fuel.gasFamily ? GAS_SUPERHEAT_THRESHOLD : LIQUID_SUPERHEAT_THRESHOLD);
        mCurrentFuelConsumption = fuelToConsume;
        mCurrentAirConsumption = airToConsume;
        mCurrentWaterConsumption = waterToConsume;
        mLastHeatCap = heatCap;

        // mMaxProgresstime = 1：基类 runMachine 每 tick 完成一次并立刻 checkRecipe 重启，实现连续逐 tick 生产
        mMaxProgresstime = 1;
        mEfficiency = 10000;
        mEfficiencyIncrease = 10000;
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    /**
     * 消耗节省因子（保底消耗公式，v1.20.9）：1 − max(0, heat − 100) × 0.005。
     * 等价语义：热量 ≤100% 恒 1（无折扣），>100% 每超 1 个百分点总消耗 ×0.995；负节省忽略。
     */
    private double consumptionSavingFactor() {
        return 1.0d - Math.max(0.0d, mHeat - 100.0d) * HEAT_SAVING_PER_POINT;
    }

    /**
     * 供给探测窗口（L）：max(1, capFactor) × ceil(demand)。capFactor ≥1 时窗口扩至链上限所需供给倍率
     * （富余度/热量上限抬升探测，正共燃温度可至 2.5×）；低档位 capFactor&lt;1 时至少探满 1×demand，
     * 保证二元足额判定可满足（纯 capFactor 窗口会低于 demand，低档将永判不足）。
     */
    private static int probeAmount(double capFactor, double demand) {
        return (int) Math.min(Integer.MAX_VALUE, (long) Math.ceil(Math.max(1.0d, capFactor) * Math.ceil(demand)));
    }

    /**
     * 油气协同燃烧分支（v5 定稿 + v1.20.9 保底消耗/二元足额）：
     * <ul>
     * <li>保底需求 demand_i = (BASE ÷ hv_i) × 0.25 × (1 − saving)（各 0.25 当量，合计恒等于单燃料 0.5 当量；
     * 不含 heat/100 项，不乘任何供给比例）</li>
     * <li>输出 = 基准 × heat/100 × 协同系数/100（满额，无比例缩放）</li>
     * <li>capTarget = max(0, maxHeatForTier(档位) + 共燃温度)（≤0 → 无法燃烧本 tick 停机）；heatCap = min(capTarget,
     * 100×min(rawRatio 们))；探测倍率 capFactor = capTarget/100，正共燃温度据此可突破 200% 直至 250%</li>
     * <li>二元足额（先全查后扣）：燃气、燃油、空气任一不足 → 不产出不扣料，停机降温；
     * 实扣顺序：燃气 → 燃油 → 空气 → 蒸馏水；缺水爆炸/停摆裁决与单燃料路径一致</li>
     * </ul>
     */
    private CheckRecipeResult processSynergy(SynergyPair synergy) {
        FuelCandidate gas = synergy.gas;
        FuelCandidate liquid = synergy.liquid;
        mCurrentFuelKind = 3;
        mCurrentFuelFluidName = gas.fluid.getName();
        mCurrentFuelLiquidName = liquid.fluid.getName();
        mCurrentSynergyPercent = synergy.percent;
        mCurrentSynergyTemp = synergy.temp;

        // 目标热量上限 capTarget 与供给探测倍率 capFactor：正共燃温度突破 200% 的关键——
        // 探测窗口随 capTarget 扩展，使 100×rawRatio 供给比例项可放行至温度扩展后的上限（默认 2.0，+50 温度时 2.5）
        double capTarget = Math.max(0.0d, maxHeatForTier(mOutputTier) + synergy.temp);
        if (capTarget <= 0.0d) {
            // 档位与负共燃温度叠加钳 0（maxHeat=0 不工作）：无法维持燃烧，本 tick 停机
            return CheckRecipeResultRegistry.NO_RECIPE;
        }
        double capFactor = capTarget / 100.0d;

        // 保底需求（各 0.25 当量 ×(1−saving)，合计 = 单燃料 0.5 当量，守恒）；空气需求 = Σdemand × 100
        double savingFactor = consumptionSavingFactor();
        double demandGas = ((double) BASE_OUTPUT_PER_TICK / gas.heatValue) * SYNERGY_FUEL_QUOTA * savingFactor;
        double demandLiquid = ((double) BASE_OUTPUT_PER_TICK / liquid.heatValue) * SYNERGY_FUEL_QUOTA * savingFactor;
        double airNeed = (demandGas + demandLiquid) * AIR_PER_FUEL;

        // 燃料探测：rawRatio_i = available_i/demand_i（不钳 1）；demand≤0 守卫分支保留（ratio=capFactor 语义）
        double rawGasRatio;
        double rawLiquidRatio;
        if (demandGas <= 0.0d) {
            rawGasRatio = capFactor;
        } else {
            long availableGas = GTSRHatchFluidAccess
                .probeFluidAmountAcross(mInputHatches, new FluidStack(gas.fluid, probeAmount(capFactor, demandGas)));
            rawGasRatio = (double) availableGas / demandGas;
        }
        if (demandLiquid <= 0.0d) {
            rawLiquidRatio = capFactor;
        } else {
            long availableLiquid = GTSRHatchFluidAccess.probeFluidAmountAcross(
                mInputHatches,
                new FluidStack(liquid.fluid, probeAmount(capFactor, demandLiquid)));
            rawLiquidRatio = (double) availableLiquid / demandLiquid;
        }
        double rawFuelRatio = Math.min(rawGasRatio, rawLiquidRatio);

        // 空气探测（同式；airNeed≤0 守卫分支保留）
        double rawAirRatio;
        if (airNeed <= 0.0d) {
            rawAirRatio = capFactor;
        } else {
            long availableAir = GTSRHatchFluidAccess
                .probeFluidAmountAcross(mInputHatches, Materials.Air.getGas(probeAmount(capFactor, airNeed)));
            rawAirRatio = (double) availableAir / airNeed;
        }

        // 二元足额判定（先全查后扣）：燃气、燃油、空气任一不足 → 本 tick 不产出、不扣任何流体（停机降温）
        if ((demandGas > 0.0d && rawGasRatio < 1.0d) || (demandLiquid > 0.0d && rawLiquidRatio < 1.0d)
            || (airNeed > 0.0d && rawAirRatio < 1.0d)) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        // heatCap = capTarget，再与供给比例取 min；heat > heatCap → 停机降温
        double heatCap = Math.min(capTarget, Math.min(100.0d * rawFuelRatio, 100.0d * rawAirRatio));
        if (mHeat > heatCap) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        // 满额产出与消耗（不乘任何比例；消耗量四舍五入为整数 L）
        long outputRounded = Math.round(BASE_OUTPUT_PER_TICK * (mHeat / 100.0d) * (synergy.percent / 100.0d));
        int gasToConsume = (int) Math.round(demandGas);
        int liquidToConsume = (int) Math.round(demandLiquid);
        int airToConsume = (int) Math.round(airNeed);
        int waterToConsume = (int) Math.round((double) outputRounded / STEAM_PER_WATER);

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

        // 缓冲入账 + 过热致密档判定（协同推广：燃气热值 ≥350 且 燃油热值 ≥450 → 过热档，否则普通档）+ GUI 消耗字段
        mCurrentOutputEquivalent = (int) Math.min(Integer.MAX_VALUE, outputRounded);
        mSteamEquivalentBuffer += outputRounded;
        mSuperheatedTier = gas.heatValue >= GAS_SUPERHEAT_THRESHOLD && liquid.heatValue >= LIQUID_SUPERHEAT_THRESHOLD;
        mCurrentGasConsumption = gasToConsume;
        mCurrentLiquidConsumption = liquidToConsume;
        mCurrentAirConsumption = airToConsume;
        mCurrentWaterConsumption = waterToConsume;
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

    // 遗留 GUI 文本路径（modular UI 终端行由 MTEThermoChemicalDenseSteamGeneratorGui 承载，LGB 双路径同范式）。
    // v1.20.9：热量/燃料/产出显示行已由 GTSRProgressBar 词条替代（清显示层冗余），保留芯片告警行与
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
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mCurrentSynergyPercent, val -> mCurrentSynergyPercent = val))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mCurrentSynergyTemp, val -> mCurrentSynergyTemp = val))
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
        // 核心描述段：2 行 WHITE + 1 行 AQUA 强调（油气协同与共燃温度机制）
        tt.addMachineType(StatCollector.translateToLocal("gtsr.tooltip.tcds.type"))
            .addInfo(EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.tooltip.tcds.desc"))
            .addInfo(EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.tooltip.tcds.desc_2"))
            .addInfo(EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.tcds.desc_3"))
            .addSeparator()
            // 关键参数段（LSOA 配色纪律：BLUE/GOLD 标签 + GOLD 数值 + GRAY 单位 + GREEN 括注；数值硬编码，模板走 lang）
            .addInfo(
                EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.tcds.output_base")
                    + EnumChatFormatting.GOLD
                    + "210,000"
                    + EnumChatFormatting.GRAY
                    + " "
                    + StatCollector.translateToLocal("gtsr.tooltip.tcds.output_base_unit")
                    + EnumChatFormatting.GREEN
                    + " "
                    + StatCollector.translateToLocal("gtsr.tooltip.tcds.output_base_note"))
            .addInfo(
                EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.tcds.fuel_consumption")
                    + EnumChatFormatting.AQUA
                    + StatCollector.translateToLocal("gtsr.tooltip.tcds.fuel_consumption_unit"))
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
            .addInfo(EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.tcds.synergy"))
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
