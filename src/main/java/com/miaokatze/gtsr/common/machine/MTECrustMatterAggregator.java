package com.miaokatze.gtsr.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.isAir;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.GTValues.emptyItemStackArray;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.apache.commons.lang3.tuple.Pair;

import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElementCheckOnly;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.util.Vec3Impl;
import com.miaokatze.gtsr.api.compat.GTSRHatchFluidAccess;
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.gui.MTECrustMatterAggregatorGui;
import com.miaokatze.gtsr.common.machine.base.MTESingularityMachineBase;
import com.miaokatze.gtsr.common.machine.base.VoidMinerUtilityShim;
import com.miaokatze.gtsr.loader.BlockLoader;
import com.miaokatze.gtsr.main.GTSteamReborn;

import bwcrossmod.galacticgreg.VoidMinerUtility;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrorRegistry;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.tileentities.machines.IDualInputHatch;
import gregtech.common.tileentities.machines.IDualInputInventory;

/**
 * 地壳物质聚合器（Crust Matter Aggregator）——奇点地壳蒸汽掘进机（MTEVoidCrustSteamBorer）的全量改名替代。
 *
 * 脱离 GT++ 蒸汽基类自管蒸汽档位（脱离 MTESteamMultiBlockBase）：
 * - 档位 0=蒸汽 / 1=过热 / 2=超临界；致密流体（densesteam 族）整体优先于普通流体；
 * - 普通档消耗 1200 L/tick（24000 L/s）、周期 400 tick（20 秒）；致密档 12 L/tick（240 L/s）、周期 100 tick（5 秒）；
 * - 产出 = 10 * mHeat * GRADE_COEF[grade] * 奇点模式系数，实数累积（NBT 持久化），整数部分经
 * VoidMinerUtility DropMap 虚空采矿输出矿石。
 *
 * 热量自管（shouldDecayHeat=false）：每 20 tick 工作且有蒸汽时按档位增益
 * （{0.0005, 0.001, 0.005} × 致密 2 倍，cap 1.0），否则快速衰减 5%/20tick。
 *
 * 奇点模式（巨型蒸汽轮机式，非右键式）：mSingularityMode 0/1/2（无/蒸汽纠缠/临界），
 * SINGULARITY_DURATION_TICKS=4000（200 秒）；每 20 tick 检查：无模式时优先消耗临界蒸汽纠缠奇点
 * 进入模式 2，其次蒸汽纠缠奇点进入模式 1；模式中倒计时耗尽时按当前模式对应物品无缝续杯，失败退出。
 * 失控奇点节点（单 F 位，color "black"，attributeId -2=onlypull）参数随模式变化：
 * 模式 0 (6,0,1) / 模式 1 (8,0,2) / 模式 2 (12,0,4)（range, speed, damage）。
 *
 * 粒子（客户端，太阳能锅炉同款）：G 位（泥土位，54 个）机器工作即每 tick 1 个白色 cloud 粒子；
 * H 位（草方块位，36 个）按 mHeat/0.5 期望数概率补 1（热量驱动）。工作标志与热量经
 * getUpdateData/onValueUpdate 字节通道同步（bit0=工作，bit1-6=热量 6bit）。
 */
public class MTECrustMatterAggregator extends MTESingularityMachineBase implements ISurvivalConstructable {

    private static final String STRUCTURE_PIECE_MAIN = "main";
    // 控制器形状偏移 (列 2, 层 2, 行 2)
    private static final int HORIZONTAL_OFF_SET = 2;
    private static final int VERTICAL_OFF_SET = 2;
    private static final int DEPTH_OFF_SET = 2;

    // 蒸汽消耗：普通 1200 L/tick（24000 L/s）；致密 12 L/tick（240 L/s = 普通 1/100）
    public static final int NORMAL_STEAM_PER_TICK = 1200;
    public static final int DENSE_STEAM_PER_TICK = 12;
    public static final int NORMAL_STEAM_PER_SECOND = NORMAL_STEAM_PER_TICK * 20;
    public static final int DENSE_STEAM_PER_SECOND = DENSE_STEAM_PER_TICK * 20;
    // 周期：普通 400 tick（20 秒）/ 致密 100 tick（5 秒）
    public static final int NORMAL_CYCLE_TICKS = 400;
    public static final int DENSE_CYCLE_TICKS = 100;
    // 热量增益（每 20 tick）：{蒸汽, 过热, 超临界}；致密 ×2；否则衰减 5%/20tick
    private static final double[] HEAT_GAIN_PER_20T = { 0.0005d, 0.001d, 0.005d };
    private static final double HEAT_DECAY_PER_20T = 0.05d;
    // 奇点模式持续 200 秒
    private static final int SINGULARITY_DURATION_TICKS = 4000;
    // 产出系数（奇点模式 0/1/2 → 0.5/2/5）
    private static final double[] SINGULARITY_OUTPUT_COEF = { 0.5d, 2.0d, 5.0d };
    // 单次产出基数：10 * mHeat * GRADE_COEF[grade] * singCoef
    private static final double ORES_PER_HEAT_UNIT = 10.0d;

    private static final String ITEM_DIM_DISPLAY_CLASS = "gtneioreplugin.plugin.item.ItemDimensionDisplay";

    // 维度缩写 → 维度内部名（即 VoidMinerUtility.dropMapsByDimName 的 key）。
    // 数据来源：GTNEIOrePlugin 1.3.3 的 gtneioreplugin.util.DimensionHelper.ABBR_TO_INTERNAL 快照。
    // 设计理由：直接 abbr → dimName 查表，避免原方案 abbr → dimId → dimName 的二次跳转，可同时修复：
    // 1) TF(Twilight Forest) 通过 dimIdToName 拿不到 DropMap 的 bug
    // 2) EA(EndAsteroid) 错误映射到 dim 1 导致拿到 "The End" DropMap 的 bug
    // 3) 扩展支持的维度从原 5 个到 GTNH 全部 43 个
    // 维护策略：GTNH 新增维度时显式扩展此表。GTSR 与 GTNEIOrePlugin 是 compileOnly 关系，
    // 玩家可能未安装该插件，因此不能在运行时直接引用 DimensionHelper 类，必须自维护映射表。
    private static final Map<String, String> ABBR_TO_DIM_NAME = new HashMap<>();
    static {
        // —— 原版三个维度 ——
        ABBR_TO_DIM_NAME.put("Ow", "Overworld");
        ABBR_TO_DIM_NAME.put("Ne", "Nether");
        ABBR_TO_DIM_NAME.put("ED", "The End");
        // —— Twilight Forest（修复：原方案通过 dim 7 中转后 dimIdToName 返回 null）——
        ABBR_TO_DIM_NAME.put("TF", "Twilight Forest");
        // —— EndAsteroid（修复：原方案错误映射到 dim 1 导致拿到 The End 的 DropMap）——
        ABBR_TO_DIM_NAME.put("EA", "EndAsteroid");
        // —— ToxicEverglades（dimDarkWorld）——
        ABBR_TO_DIM_NAME.put("Eg", "dimensionDarkWorld");
        // —— Galacticraft Core / Planets ——
        ABBR_TO_DIM_NAME.put("Mo", "moon");
        ABBR_TO_DIM_NAME.put("Ma", "mars");
        ABBR_TO_DIM_NAME.put("As", "asteroids"); // 注意：As 维度 disableVoidMining，DropMap 为空
        // —— GalaxySpace 系列（GalacticGreg 提供 DropMap）——
        ABBR_TO_DIM_NAME.put("De", "deimos");
        ABBR_TO_DIM_NAME.put("Ph", "phobos");
        ABBR_TO_DIM_NAME.put("Ca", "callisto");
        ABBR_TO_DIM_NAME.put("Ce", "ceres");
        ABBR_TO_DIM_NAME.put("Eu", "europa");
        ABBR_TO_DIM_NAME.put("Ga", "ganymed");
        ABBR_TO_DIM_NAME.put("Rb", "ross128b");
        ABBR_TO_DIM_NAME.put("Io", "iojupiter");
        ABBR_TO_DIM_NAME.put("Me", "mercury");
        ABBR_TO_DIM_NAME.put("Ve", "venus");
        ABBR_TO_DIM_NAME.put("En", "enceladus");
        ABBR_TO_DIM_NAME.put("Mi", "miranda");
        ABBR_TO_DIM_NAME.put("Ob", "oberon");
        ABBR_TO_DIM_NAME.put("Ti", "titan");
        ABBR_TO_DIM_NAME.put("Ra", "ross128ba");
        ABBR_TO_DIM_NAME.put("Pr", "proteus");
        ABBR_TO_DIM_NAME.put("Tr", "triton");
        ABBR_TO_DIM_NAME.put("Ha", "haumea");
        ABBR_TO_DIM_NAME.put("KB", "kuiperbelt"); // 注意：KB 维度 disableVoidMining，DropMap 为空
        ABBR_TO_DIM_NAME.put("MM", "makemake");
        ABBR_TO_DIM_NAME.put("Pl", "pluto");
        // —— GalaxySpace 远程恒星系 ——
        ABBR_TO_DIM_NAME.put("BC", "barnarda2");
        ABBR_TO_DIM_NAME.put("BE", "barnarda4");
        ABBR_TO_DIM_NAME.put("BF", "barnarda5");
        ABBR_TO_DIM_NAME.put("CB", "centauribb");
        ABBR_TO_DIM_NAME.put("TE", "tcetie");
        ABBR_TO_DIM_NAME.put("VB", "vega1");
        // —— AmunRa / GalacticraftAmunRa 系列 ——
        ABBR_TO_DIM_NAME.put("An", "anubis");
        ABBR_TO_DIM_NAME.put("Ho", "horus");
        ABBR_TO_DIM_NAME.put("Mh", "maahes");
        ABBR_TO_DIM_NAME.put("MB", "asteroidbeltmehen"); // 注意：MB 维度 disableVoidMining，DropMap 为空
        ABBR_TO_DIM_NAME.put("Np", "neper");
        ABBR_TO_DIM_NAME.put("Se", "seth");
        // —— Deep Dark（Underdark）——
        ABBR_TO_DIM_NAME.put("DD", "Underdark");
    }

    private static Boolean pluginLoaded = null;
    private static Class<?> itemDimDisplayClass = null;
    private static java.lang.reflect.Method getDimensionMethod = null;

    private static IStructureDefinition<MTECrustMatterAggregator> STRUCTURE_DEFINITION = null;

    // 结构特殊位形状偏移缓存（静态惰性初始化，运行时扫描 SHAPE_MAIN 计算，勿硬编码）
    private static List<int[]> mSingularityOffsets = null;
    private static List<int[]> mParticleOffsetsG = null;
    private static List<int[]> mParticleOffsetsH = null;

    // —— 维度 / 虚空采矿 ——
    public String lastDimAbbr = "None";
    public String mLastOreName = "";
    public boolean dropMapValid = false;
    public int mCurrentDimId = 0;
    protected boolean mDefaultDimSupported = false;
    private VoidMinerUtility.DropMap dropMap = null;
    private VoidMinerUtility.DropMap extraDropMap = null;

    // —— 蒸汽档位（checkProcessing 时缓存，供周期内每 tick 扣减与完成时产出）——
    private int mActiveGrade = -1;
    private boolean mActiveDense = false;
    // 产出实数累积（NBT 持久化）
    private double mOreAccumulator = 0.0d;

    // —— 奇点模式（巨型蒸汽轮机式）——
    public int mSingularityMode = 0;
    public int mSingularityModeTicks = 0;
    private int mSingularityCheckCooldown = 0;

    // —— 客户端粒子状态（getUpdateData/onValueUpdate 字节通道同步）——
    private boolean mWorkingForFX = false;

    public MTECrustMatterAggregator(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTECrustMatterAggregator(String aName) {
        super(aName);
    }

    @Override
    protected String getTooltipKeyPrefix() {
        return "gtsr.tooltip.crust_matter_agg.";
    }

    @Override
    public String getGuiKeyPrefix() {
        return "gtsr.gui.crust_matter_agg.";
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTECrustMatterAggregator(mName);
    }

    @Override
    protected int getRequiredTier() {
        return 1;
    }

    @Override
    protected double getHeatMax() {
        return 1.0d;
    }

    @Override
    protected long getHeatHalfPoint() {
        return 1L;
    }

    @Override
    protected boolean includeDenseSteam() {
        return true;
    }

    @Override
    protected ItemStack getAggregationOutput() {
        return null;
    }

    @Override
    protected boolean requiresOutputHatch() {
        return false;
    }

    @Override
    protected boolean requiresInputBus() {
        return false;
    }

    @Override
    protected boolean shouldDecayHeat() {
        return false;
    }

    @Override
    public int getModeForGui() {
        return mSingularityMode;
    }

    @Override
    public int getFuelTicksForGui() {
        return mSingularityModeTicks;
    }

    @Override
    protected boolean shouldRenderEntanglementSingularity(IGregTechTileEntity aBaseMetaTileEntity) {
        // 奇点模式期间（含关机）必定渲染失控奇点；其余状态沿用基类"工作即出现"语义
        return mSingularityMode != 0 || super.shouldRenderEntanglementSingularity(aBaseMetaTileEntity);
    }

    private static synchronized boolean isPluginLoaded() {
        if (pluginLoaded == null) {
            if (!Loader.isModLoaded("gtneioreplugin")) {
                pluginLoaded = false;
            } else {
                try {
                    itemDimDisplayClass = Class
                        .forName(ITEM_DIM_DISPLAY_CLASS, true, MTECrustMatterAggregator.class.getClassLoader());
                    getDimensionMethod = itemDimDisplayClass.getMethod("getDimension", ItemStack.class);
                    pluginLoaded = true;
                } catch (ClassNotFoundException | NoClassDefFoundError | NoSuchMethodException e) {
                    pluginLoaded = false;
                }
            }
        }
        return pluginLoaded;
    }

    // Shape: GTUDK export — Y slices (top -> bottom); each row = depth line (front face first);
    // each char = horizontal axis (left -> right, seen from the machine front).
    // 注册：.addShape(STRUCTURE_PIECE_MAIN, transpose(SHAPE_MAIN))，旋转由 StructureLib ExtendedFacing 自动处理
    // 控制器 '~' 位于 (列 2, 层 2, 行 2)；F 失控奇点位 1 个 (16,3,16)；G 泥土粒子位 54 个（层 4）；
    // H 草方块粒子位 36 个（层 2）。
    private static final String[][] SHAPE_MAIN = {
        { "                          ", "                          ", "                          ",
            "                          ", "                          ", "                          ",
            "                          ", "        AAA           AAA ", "       A---A         A---A",
            "       A---A         A---A", "       A---A         A---A", "        AAA           AAA ",
            "                          ", "                          ", "                          ",
            "                          ", "                          ", "                          ",
            "                          ", "                          ", "                          ",
            "        AAA           AAA ", "       A---A         A---A", "       A---A         A---A",
            "       A---A         A---A", "        AAA           AAA " },
        { "                          ", "                          ", "  A                       ",
            " AAA                      ", "                          ", "                          ",
            "                          ", "        AAA           AAA ", "       A---A         A---A",
            "       A---A         A---A", "       A---A         A---A", "        AAA           AAA ",
            "                          ", "                          ", "                          ",
            "               ---        ", "               ---        ", "               ---        ",
            "                          ", "                          ", "                          ",
            " AAA    AAA           AAA ", "A---A  A---A         A---A", "A---A  A---A         A---A",
            "A---A  A---A         A---A", " AAA    AAA           AAA " },
        { "        AAA    AAA    AAA ", "       A---A  A---A  A---A", " B~B   A---A  A---A  A---A",
            " BBB   A---A  A---A  A---A", "        AAA    AAA    AAA ", "                       E  ",
            "                       E  ", " AAA    AAA           AAA ", "A---A  AHHHA         AHHHA",
            "A---A  AHHHAEEEEEEEEEAHHHA", "A---A  AHHHA    E    AHHHA", " AAA    AAA     E     AAA ",
            "         E      E      E  ", "         E      E      E  ", " AAA     E    EEEEE    E  ",
            "A---A    E    E---E    E  ", "A---A    EEEEEE---EEEEEE  ", "A---A    E    E---E    E  ",
            " AAA     E    EEEEE    E  ", "         E      E      E  ", "         E      E      E  ",
            " AAA    AAA     E     AAA ", "A---A  AHHHA    E    AHHHA", "A---AEEAHHHAEEEEEEEEEAHHHA",
            "A---A  AHHHA         AHHHA", " AAA    AAA           AAA " },
        { "        AAA    AAA    AAA ", " AAA   A---A  A---A  A---A", " AAA   A---A  A---A  A---A",
            " AAA   A---A  A---A  A---A", "        AAA    AAA    ACA ", "                      ECE ",
            "                      ECE ", " AAA   DAAAD         DACAD", "A---A  ACCCAEEEEEEEEEACCCA",
            "A---A  ACCCCCCCCCCCCCCCCCA", "A---A  ACCCAEEEECEEEEACCCA", " AAA   DACAD   ECE   DACAD",
            "        ECE    ECE    ECE ", "        ECE  DEECEED  ECE ", " AAA    ECE  E-----E  ECE ",
            "A---A   ECEEEE-----EEEECE ", "A---A   ECCCCC--F--CCCCCE ", "A---A   ECEEEE-----EEEECE ",
            " AAA    ECE  E-----E  ECE ", "        ECE  DEECEED  ECE ", "        ECE    ECE    ECE ",
            " AAA   DACAD   ECE   DACAD", "A   AEEACCCAEEEECEEEEACCCA", "A   CCCCCCCCCCCCCCCCCCCCCA",
            "A   AEEACCCAEEEEEEEEEACCCA", " AAA   DAAAD         DAAAD" },
        { "AAAAA  DAAAD  DAAAD  DAAAD", "ACCCA  AGGGA  AGGGA  AGGGA", "ACCCAEEAGGGAEEAGGGAEEAGGGA",
            "ACCCA  AGGGA  AGGGA  AGGGA", "AAAAA  DAAAD  DAAAD  DACAD", "  E                   ECE ",
            "  E                   ECE ", "DAAAD  DAAAD         DACAD", "AGGGA  ACCCAEEEEEEEEEACCCA",
            "AGGGA  ACCCCCCCCCCCCCCCCCA", "AGGGA  ACCCAEEEECEEEEACCCA", "DAAAD  DACAD   ECE   DACAD",
            "  E     ECE    ECE    ECE ", "  E     ECE  DEECEED  ECE ", "DAAAD   ECE  E-----E  ECE ",
            "AGGGA   ECEEEE-----EEEECE ", "AGGGA   ECCCCC-----CCCCCE ", "AGGGA   ECEEEE-----EEEECE ",
            "DAAAD   ECE  E------  ECE ", "  E     ECE  DEECEED  ECE ", "  E     ECE    ECE    ECE ",
            "DAAAD  DACAD   ECE   DACAD", "AGGGAEEACCCAEEEECEEEEACCCA", "AGGGCCCCCCCCCCCCCCCCCCCCCA",
            "AGGGAEEACCCAEEEEEEEEEACCCA", "DAAAD  DAAAD         DAAAD" },
        { "DEEED  DAAAD  DAAAD  DAAAD", "ECCCEEEACCCAEEACCCAEEACCCA", "ECCCCCCCCCCCCCCCCC CCCCCCA",
            "ECCCEEEACCCAEEACCCAEEACCCA", "DECED  DAAAD  DAAAD  DAAAD", " ECE                   E  ",
            " ECE                   E  ", "DACAD  D   D         D E D", "ACCCA   EEE           EEE ",
            "ACCCA   EEEEEEEEEEEEEEEEE ", "ACCCA   EEE           EEE ", "DACAD  D E D         D E D",
            " ECE     E             E  ", " ECE     E   D-----D   E  ", "DACAD    E   -------   E  ",
            "ACCCA    E   -------   E  ", "ACCCA    E   -------   E  ", "ACCCA    E   -------   E  ",
            "DACAD    E   -------   E  ", " ECE     E   D-----D   E  ", " ECE     E             E  ",
            "DACAD  D E D         D E D", "ACCCA   EEE           EEE ", "ACCC EEEEEEEEEEEEEEEEEEEE ",
            "ACCCA   EEE           EEE ", "DACAD  D   D         D   D" },
        { "D   D  D   D  D   D  D   D", " EEE    AAA    AAA    AAA ", " EEEEEEEEEEEEEEEEEEEEEEEEE",
            " EEE    AAA    AAA    AAA ", "D E D  D   D  D   D  D   D", "  E                       ",
            "  E                       ", "D E D  D   D         D   D", " AEA                      ",
            " AEA                      ", " AEA                      ", "D E D  D   D         D   D",
            "  E                       ", "  E          D-----D      ", "D E D        -------      ",
            " AEA         -------      ", " AEA         -------      ", " AEA         -------      ",
            "D E D        -------      ", "  E          D-----D      ", "  E                       ",
            "D E D  D   D         D   D", " AEA                      ", " AEA                      ",
            " AEA                      ", "D E D  D   D         D   D" },
        { "D   D  D   D  D   D  D   D", "                          ", "                          ",
            "                          ", "D   D  D   D  D   D  D   D", "                          ",
            "                          ", "D   D  D   D         D   D", "                          ",
            "                          ", "                          ", "D   D  D   D         D   D",
            "                          ", "             D     D      ", "D   D                     ",
            "                          ", "                          ", "                          ",
            "D   D                     ", "             D     D      ", "                          ",
            "D   D  D   D         D   D", "                          ", "                          ",
            "                          ", "D   D  D   D         D   D" } };

    private static IStructureDefinition<MTECrustMatterAggregator> createStructureDefinition() {
        int casingIndex = GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings2, 0);
        // 失控奇点定位位（导出为泥土）：接受失控奇点方块或空气（砖高炉式容错：运行期间此处生成奇点，结构判定仍有效）。
        // CheckOnly 不放置：构建/全息投影保持空气，奇点仅由机器运行时惰性生成
        IStructureElementCheckOnly<MTECrustMatterAggregator> singularityLocator = new IStructureElementCheckOnly<MTECrustMatterAggregator>() {

            @Override
            public boolean check(MTECrustMatterAggregator t, World world, int x, int y, int z) {
                Block block = world.getBlock(x, y, z);
                return block == BlockLoader.blockRunawaySingularity || block.isAir(world, x, y, z);
            }
        };

        return StructureDefinition.<MTECrustMatterAggregator>builder()
            .addShape(STRUCTURE_PIECE_MAIN, transpose(SHAPE_MAIN))
            .addElement('A', ofBlock(GameRegistry.findBlock("gregtech", "gt.blockcasings2"), 0))
            .addElement(
                'B',
                ofChain(
                    // casing-first: NEI 投影优先渲染外壳；真实 hatch 坐标上 casing 匹配失败后继续匹配 hatch adder。
                    ofBlock(GameRegistry.findBlock("gregtech", "gt.blockcasings2"), 3),
                    // 流体输入仓（MTEHatchInput 含 ME 输入仓 + 耐压蒸汽输入仓；shouldReject 防重复）
                    buildHatchAdder(MTECrustMatterAggregator.class).atLeast(SingularityHatchElement.SteamInput)
                        .casingIndex(casingIndex)
                        .hint(1)
                        .shouldReject(t -> !t.mInputHatches.isEmpty() && !t.mPressureSteamInputs.isEmpty())
                        .build(),
                    // 物品输入总线 = 奇点燃料仓（蒸汽纠缠/临界蒸汽纠缠奇点），可选
                    buildHatchAdder(MTECrustMatterAggregator.class).atLeast(SingularityHatchElement.SteamInputBus)
                        .casingIndex(casingIndex)
                        .hint(1)
                        .build(),
                    // 输出总线 = 矿石输出
                    buildHatchAdder(MTECrustMatterAggregator.class).atLeast(SingularityHatchElement.SteamOutputBus)
                        .casingIndex(casingIndex)
                        .hint(1)
                        .build()))
            .addElement('C', ofBlock(GameRegistry.findBlock("gregtech", "gt.blockcasings2"), 13))
            .addElement('D', ofBlock(GregTechAPI.sBlockFrames, Materials.Steel.mMetaItemSubID))
            .addElement('E', ofBlock(GameRegistry.findBlock("gregtech", "gt.blockglass1"), 10))
            // F = 失控奇点定位位（CheckOnly：接受奇点方块或空气，构建/投影不放置）
            .addElement('F', singularityLocator)
            // G/H = 纯空气位（粒子喷口，导出为泥土/草方块但结构上按空气处理）
            .addElement('G', isAir())
            .addElement('H', isAir())
            .addElement('-', isAir())
            .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public IStructureDefinition<MTESingularityMachineBase> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) STRUCTURE_DEFINITION = createStructureDefinition();
        return (IStructureDefinition<MTESingularityMachineBase>) (IStructureDefinition<?>) STRUCTURE_DEFINITION;
    }

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

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        mPressureSteamInputs.clear();
        mTier = getRequiredTier();

        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET, errors)) return;

        // 结构要求：流体输入 ≥1（超临界/致密态流体必需）、输出总线 ≥1；物品输入总线（奇点燃料）可选
        if (mInputHatches.isEmpty() && mPressureSteamInputs.isEmpty() && mDualInputHatches.isEmpty()) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }
        if (mOutputBusses.isEmpty()) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }
        updateAllHatchTextures();
    }

    // —— 结构特殊位形状偏移缓存（(列, 层, 行) - 控制器 (2,2,2)）——

    private static List<int[]> scanShape(char target) {
        List<int[]> offsets = new ArrayList<>();
        for (int b = 0; b < SHAPE_MAIN.length; b++) {
            for (int c = 0; c < SHAPE_MAIN[b].length; c++) {
                String row = SHAPE_MAIN[b][c];
                for (int a = 0; a < row.length(); a++) {
                    if (row.charAt(a) == target) {
                        offsets.add(new int[] { a - HORIZONTAL_OFF_SET, b - VERTICAL_OFF_SET, c - DEPTH_OFF_SET });
                    }
                }
            }
        }
        return offsets;
    }

    /** F 失控奇点定位位（1 个）形状偏移 */
    private static List<int[]> getSingularityOffsets() {
        if (mSingularityOffsets == null) mSingularityOffsets = scanShape('F');
        return mSingularityOffsets;
    }

    /** G 泥土粒子位（54 个）形状偏移 */
    private static List<int[]> getParticleOffsetsG() {
        if (mParticleOffsetsG == null) mParticleOffsetsG = scanShape('G');
        return mParticleOffsetsG;
    }

    /** H 草方块粒子位（36 个）形状偏移 */
    private static List<int[]> getParticleOffsetsH() {
        if (mParticleOffsetsH == null) mParticleOffsetsH = scanShape('H');
        return mParticleOffsetsH;
    }

    // —— 维度 / 虚空采矿（沿用旧 SCSB 的 VoidMinerUtilityShim/dropMap 与维度解析；任务 4：无插件物品时默认当前维度）——

    @Override
    public void onFirstTick(IGregTechTileEntity aBaseMetaTileEntity) {
        super.onFirstTick(aBaseMetaTileEntity);
        mCurrentDimId = aBaseMetaTileEntity.getWorld().provider.dimensionId;
        if (isPluginLoaded()) {
            String dimAbbr = readDimensionOverride();
            if (!"None".equals(dimAbbr)) {
                lastDimAbbr = dimAbbr;
                recalculateDropMap(dimAbbr);
                return;
            }
        }
        calculateDefaultDropMap();
    }

    /** 反射读取 mInventory[1] 中的 GT NEI Ore Plugin 维度显示物品（无物品返回 "None"）。 */
    private String readDimensionOverride() {
        if (!isPluginLoaded()) return "None";
        try {
            ItemStack slotStack = mInventory[1];
            if (slotStack != null) {
                Item slotItem = slotStack.getItem();
                if (slotItem != null) {
                    if (itemDimDisplayClass != null && itemDimDisplayClass.isInstance(slotItem)) {
                        Object result = getDimensionMethod.invoke(null, slotStack);
                        if (result instanceof String) return (String) result;
                    }
                }
            }
        } catch (Exception e) {
            GTSteamReborn.LOG.error("[CrustMatterAggregator] 读取维度覆盖失败，使用默认值 None", e);
        }
        return "None";
    }

    /** 默认当前维度（mInventory[1] 无插件物品时）：dimId → dimName → DropMap。 */
    private void calculateDefaultDropMap() {
        dropMap = null;
        extraDropMap = null;
        dropMapValid = false;
        String dimName = VoidMinerUtilityShim.dimIdToName(mCurrentDimId);
        if (dimName == null) {
            mDefaultDimSupported = false;
            return;
        }
        mDefaultDimSupported = true;
        dropMap = VoidMinerUtilityShim.getDropMap(dimName);
        extraDropMap = VoidMinerUtilityShim.getExtraDropMap(dimName);
        dropMap.isDistributionCached(extraDropMap);
        dropMapValid = dropMap.getTotalWeight() > 0;
    }

    /** 插件维度覆盖（abbr → dimName 查表）。 */
    private void recalculateDropMap(String dimAbbr) {
        dropMap = null;
        extraDropMap = null;
        dropMapValid = false;

        if ("None".equals(dimAbbr)) return;

        String dimName = ABBR_TO_DIM_NAME.get(dimAbbr);
        if (dimName == null) {
            GTSteamReborn.LOG
                .warn("[CrustMatterAggregator] 未知维度缩写: " + dimAbbr + "（ABBR_TO_DIM_NAME 表中无此条目，请确认 GTNEIOrePlugin 版本）");
            dropMap = new VoidMinerUtility.DropMap();
            extraDropMap = new VoidMinerUtility.DropMap();
            return;
        }

        dropMap = VoidMinerUtilityShim.getDropMap(dimName);
        extraDropMap = VoidMinerUtilityShim.getExtraDropMap(dimName);
        dropMap.isDistributionCached(extraDropMap);
        dropMapValid = dropMap.getTotalWeight() > 0;
        if (!dropMapValid) {
            GTSteamReborn.LOG.warn(
                "[CrustMatterAggregator] 维度 " + dimName
                    + " ("
                    + dimAbbr
                    + ") 的 DropMap 为空或总权重为 0，可能是 GalacticGreg 未生成该维度矿石数据"
                    + "（Asteroid 类型维度如 As/KB/MB 默认 disableVoidMining）");
        }
    }

    /** GUI 维度显示名：覆盖模式显示缩写，默认模式显示维度名。 */
    public String getDimensionDisplayName() {
        if (!"None".equals(lastDimAbbr)) return lastDimAbbr;
        String dimName = VoidMinerUtilityShim.dimIdToName(mCurrentDimId);
        return dimName != null ? dimName : ("Dim " + mCurrentDimId);
    }

    private boolean hasUsableDimension() {
        if (!"None".equals(lastDimAbbr)) return ABBR_TO_DIM_NAME.containsKey(lastDimAbbr);
        return mDefaultDimSupported;
    }

    // —— 蒸汽档位探测（致密优先：densesupercritical > densesuperheated > densesteam；否则普通 超临界 > 过热 > 蒸汽）——

    private int findGrade() {
        for (int grade = 2; grade >= 0; grade--) {
            if (probeGrade(grade, true, true)) return grade;
        }
        for (int grade = 2; grade >= 0; grade--) {
            if (probeGrade(grade, false, false)) return grade;
        }
        return -1;
    }

    // —— 主流程 ——

    @Override
    public CheckRecipeResult checkProcessing() {
        if (isPluginLoaded()) {
            String dimAbbr = readDimensionOverride();
            if ("None".equals(dimAbbr)) {
                // 插件物品被移除：回到默认当前维度
                if (!"None".equals(lastDimAbbr)) {
                    lastDimAbbr = "None";
                    calculateDefaultDropMap();
                }
            } else if (!dimAbbr.equals(lastDimAbbr)) {
                lastDimAbbr = dimAbbr;
                recalculateDropMap(dimAbbr);
            }
        } else if (!"None".equals(lastDimAbbr)) {
            lastDimAbbr = "None";
            calculateDefaultDropMap();
        }
        // 默认维度模式：传送后当前维度变化时重算
        int dimId = getBaseMetaTileEntity().getWorld().provider.dimensionId;
        if ("None".equals(lastDimAbbr) && dimId != mCurrentDimId) {
            mCurrentDimId = dimId;
            calculateDefaultDropMap();
        }

        if (!hasUsableDimension()) {
            return SimpleCheckRecipeResult.ofFailure("gtsr.gui.crust_matter_agg.no_dimension");
        }
        if (!dropMapValid) {
            return SimpleCheckRecipeResult.ofFailure("gtsr.gui.crust_matter_agg.no_ores");
        }

        int grade = findGrade();
        if (grade < 0) {
            return SimpleCheckRecipeResult.ofFailure("gtsr.gui.crust_matter_agg.no_steam");
        }
        boolean dense = probeGrade(grade, true, true);
        mActiveGrade = grade;
        mActiveDense = dense;
        mMaxProgresstime = dense ? DENSE_CYCLE_TICKS : NORMAL_CYCLE_TICKS;
        // 显式置满效率：自定义 checkProcessing 不经标准流程的 mEfficiency 初始化
        mEfficiency = 10000;
        mEfficiencyIncrease = 10000;
        mOutputItems = emptyItemStackArray;
        updateSlots();
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    @Override
    protected void outputAfterRecipe() {
        if (dropMap == null || dropMap.getTotalWeight() <= 0 || mActiveGrade < 0) {
            updateSlots();
            return;
        }
        // 产出 = 10 * mHeat * GRADE_COEF[grade] * singCoef，实数累积，整数部分输出
        mOreAccumulator += ORES_PER_HEAT_UNIT * mHeat
            * GRADE_COEF[mActiveGrade]
            * SINGULARITY_OUTPUT_COEF[mSingularityMode];
        int out = (int) Math.floor(mOreAccumulator);
        for (int i = 0; i < out; i++) {
            GTUtility.ItemId oreId = dropMap.nextOre();
            if (oreId == null) break;
            ItemStack oreStack = oreId.getItemStack();
            if (oreStack == null) break;
            addOutputPartial(oreStack);
            mLastOreName = oreStack.getDisplayName();
            mOreAccumulator -= 1.0d;
        }
        updateSlots();
    }

    /** 周期内每 tick 从输入仓扣减当前档位蒸汽；不足返回 false（周期停止）。 */
    private boolean depleteSteamForTick() {
        if (mActiveGrade < 0) return false;
        int remaining = mActiveDense ? DENSE_STEAM_PER_TICK : NORMAL_STEAM_PER_TICK;
        // 基类 gradeProbeStacks 为 private，此处自行构造当前档位流体请求（致密档只扣致密流体，普通档只扣普通流体）
        FluidStack request = FluidRegistry
            .getFluidStack((mActiveDense ? DENSE_FLUID_NAMES : NORMAL_FLUID_NAMES)[mActiveGrade], 1);
        if (request == null) return false;
        for (MTEHatch hatch : getSteamInputHatches()) {
            if (remaining <= 0) break;
            FluidStack full = request.copy();
            full.amount = Integer.MAX_VALUE;
            FluidStack available = hatch.drain(ForgeDirection.UNKNOWN, full, false);
            if (available == null || available.amount <= 0) continue;
            int toDrain = Math.min(remaining, available.amount);
            FluidStack drainReq = request.copy();
            drainReq.amount = toDrain;
            hatch.drain(ForgeDirection.UNKNOWN, drainReq, true);
            remaining -= toDrain;
        }
        // 主仓不足时继续扣样板仓（ME 输入仓；太阳能锅炉同款路径）
        if (remaining > 0) {
            FluidStack dualReq = request.copy();
            dualReq.amount = remaining;
            remaining -= GTSRHatchFluidAccess.depleteFluidFromDuals(mDualInputHatches, dualReq);
        }
        return remaining <= 0;
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (aBaseMetaTileEntity.isClientSide()) {
            spawnParticles(aBaseMetaTileEntity);
            return;
        }
        if (mMachine) {
            // 奇点模式：每 tick 倒计时，每 20 tick 检查进入/无缝续杯
            if (mSingularityModeTicks > 0) mSingularityModeTicks--;
            if (++mSingularityCheckCooldown >= 20) {
                mSingularityCheckCooldown = 0;
                // 包配方窗口使 ME 输入总线的虚拟引用可读（getStackInSlot 仅窗口内有效）；
                // 与基类 checkRecipe 的窗口嵌套安全（start/end 均幂等）
                startRecipeProcessing();
                checkSingularityMode();
                endRecipeProcessing();
            }
            // 周期内每 tick 蒸汽扣减；不足则停（周期结束，由 checkProcessing 重判）
            if (aBaseMetaTileEntity.isAllowedToWork() && mMaxProgresstime > 0 && !depleteSteamForTick()) {
                mMaxProgresstime = 0;
                mProgresstime = 0;
            }
        }
        // 热量：每 20 tick；有蒸汽供给（结构有效+允许工作+探测到蒸汽档位）时按档位增益，否则快速衰减。
        // 不用 mMaxProgresstime>0 判定：周期完成与 20tick 检查点撞车时（致密态周期仅 100 tick，1/5 概率）会误判停机扣 5% 热量
        if (aTick % 20 == 0) {
            if (mMachine && aBaseMetaTileEntity.isAllowedToWork()) {
                int grade = findGrade();
                if (grade >= 0) {
                    boolean dense = probeGrade(grade, true, true);
                    mHeat = Math.min(1.0d, mHeat + HEAT_GAIN_PER_20T[grade] * (dense ? 2.0d : 1.0d));
                } else {
                    mHeat = Math.max(0.0d, mHeat - HEAT_DECAY_PER_20T);
                }
            } else {
                mHeat = Math.max(0.0d, mHeat - HEAT_DECAY_PER_20T);
            }
        }
    }

    // —— 奇点模式（巨型蒸汽轮机式，非右键式）——

    /**
     * 检查并维持奇点模式。
     * 不在模式时：优先消耗 1 个临界蒸汽纠缠奇点进入模式 2，其次 1 个蒸汽纠缠奇点进入模式 1。
     * 模式中倒计时耗尽时：立即按当前模式对应物品无缝续杯；成功续满，失败才退出模式。
     */
    private void checkSingularityMode() {
        if (mSingularityMode == 0) {
            if (consumeSingularityFromInputBuses(1, GTSRItemList.CriticalSteamEntangledSingularity)) {
                mSingularityMode = 2;
                mSingularityModeTicks = SINGULARITY_DURATION_TICKS;
                getBaseMetaTileEntity().markDirty();
            } else if (consumeSingularityFromInputBuses(1, GTSRItemList.SteamEntangledSingularity)) {
                mSingularityMode = 1;
                mSingularityModeTicks = SINGULARITY_DURATION_TICKS;
                getBaseMetaTileEntity().markDirty();
            }
        } else if (mSingularityModeTicks <= 0) {
            GTSRItemList fuel = mSingularityMode == 2 ? GTSRItemList.CriticalSteamEntangledSingularity
                : GTSRItemList.SteamEntangledSingularity;
            if (consumeSingularityFromInputBuses(1, fuel)) {
                mSingularityModeTicks = SINGULARITY_DURATION_TICKS;
                getBaseMetaTileEntity().markDirty();
            } else {
                mSingularityMode = 0;
                getBaseMetaTileEntity().markDirty();
            }
        }
    }

    /**
     * 从输入总线与样板仓中消耗指定数量的奇点燃料（巨型蒸汽轮机式）。
     * 先收集候选防部分消耗；样板仓 getItemInputs 引用为仓内持久数据（窗口无关），网络结算由样板仓自身完成。
     *
     * @return 是否成功消耗全部数量
     */
    private boolean consumeSingularityFromInputBuses(int amount, GTSRItemList singularity) {
        int remaining = amount;
        // 先收集所有可消耗的槽位，避免部分消耗后无法回滚
        List<Pair<MTEHatchInputBus, Integer>> candidates = new ArrayList<>();
        for (MTEHatchInputBus bus : GTUtility.validMTEList(mInputBusses)) {
            if (bus == null) continue;
            for (int i = 0; i < bus.getSizeInventory(); i++) {
                ItemStack stack = bus.getStackInSlot(i);
                if (stack != null && singularity.isStackEqual(stack, false, true)) {
                    candidates.add(Pair.of(bus, i));
                    remaining -= stack.stackSize;
                    if (remaining <= 0) break;
                }
            }
            if (remaining <= 0) break;
        }
        if (remaining > 0) {
            for (IDualInputHatch dual : mDualInputHatches) {
                if (dual == null) continue;
                Iterator<? extends IDualInputInventory> it = dual.inventories();
                while (it.hasNext()) {
                    ItemStack[] items = it.next()
                        .getItemInputs();
                    if (items == null) continue;
                    for (ItemStack stack : items) {
                        if (stack == null || !singularity.isStackEqual(stack, false, true)) continue;
                        int toConsume = Math.min(remaining, stack.stackSize);
                        stack.stackSize -= toConsume;
                        remaining -= toConsume;
                        if (remaining <= 0) break;
                    }
                    if (remaining <= 0) break;
                }
                if (remaining <= 0) break;
            }
        }
        if (remaining > 0) {
            return false;
        }
        // 实际消耗
        remaining = amount;
        for (Pair<MTEHatchInputBus, Integer> candidate : candidates) {
            MTEHatchInputBus bus = candidate.getLeft();
            int slot = candidate.getRight();
            ItemStack stack = bus.getStackInSlot(slot);
            if (stack == null) continue;
            int toConsume = Math.min(remaining, stack.stackSize);
            stack.stackSize -= toConsume;
            remaining -= toConsume;
            if (stack.stackSize <= 0) {
                bus.setInventorySlotContents(slot, null);
            } else {
                bus.setInventorySlotContents(slot, stack);
            }
            if (remaining <= 0) break;
        }
        for (MTEHatchInputBus bus : GTUtility.validMTEList(mInputBusses)) {
            if (bus != null) bus.updateSlots();
        }
        return true;
    }

    // —— 失控奇点节点（动态：单 F 位，参数随模式变化）——

    @Override
    protected List<EntanglementSpec> getEntanglementSpecs() {
        List<int[]> fOffsets = getSingularityOffsets();
        if (fOffsets.isEmpty()) return Collections.emptyList();
        int[] off = fOffsets.get(0);
        Vec3Impl worldOff = getExtendedFacing().getWorldOffset(new Vec3Impl(off[0], off[1], off[2]));
        double range;
        double damage;
        switch (mSingularityMode) {
            case 2:
                range = 12.0d;
                damage = 4.0d;
                break;
            case 1:
                range = 8.0d;
                damage = 2.0d;
                break;
            default:
                range = 6.0d;
                damage = 1.0d;
                break;
        }
        return Collections.singletonList(
            new EntanglementSpec(
                worldOff.get0(),
                worldOff.get1(),
                worldOff.get2(),
                range,
                0.0d,
                damage,
                -1,
                -2,
                "black"));
    }

    // —— 粒子（客户端，太阳能锅炉同款）——

    /** G 位固定 1 个/tick（工作即喷）；H 位按 mHeat/0.5 期望数概率补 1（热量驱动）。 */
    private void spawnParticles(IGregTechTileEntity aBaseMetaTileEntity) {
        World world = aBaseMetaTileEntity.getWorld();
        if (mWorkingForFX) {
            spawnOneParticle(world, aBaseMetaTileEntity, getParticleOffsetsG());
        }
        if (mHeat > 0.0d) {
            double expected = mHeat / 0.5d;
            int n = (int) expected;
            if (world.rand.nextDouble() < expected - n) n++;
            for (int i = 0; i < n; i++) {
                spawnOneParticle(world, aBaseMetaTileEntity, getParticleOffsetsH());
            }
        }
    }

    /** 在随机一个粒子位生成单个上升白色云朵粒子 */
    private void spawnOneParticle(World world, IGregTechTileEntity base, List<int[]> offsets) {
        if (offsets.isEmpty()) return;
        int[] off = offsets.get(world.rand.nextInt(offsets.size()));
        Vec3Impl worldOff = getExtendedFacing().getWorldOffset(new Vec3Impl(off[0], off[1], off[2]));
        world.spawnParticle(
            "cloud",
            base.getXCoord() + worldOff.get0() + 0.5D + (world.rand.nextDouble() - 0.5D) * 0.8D,
            base.getYCoord() + worldOff.get1() + 0.5D,
            base.getZCoord() + worldOff.get2() + 0.5D + (world.rand.nextDouble() - 0.5D) * 0.8D,
            0.0D,
            0.3D,
            0.0D);
    }

    // —— 字节通道：bit0=工作（粒子 G 位开关），bit1-6=热量 6bit（1/63 精度）——

    @Override
    public void onValueUpdate(byte aValue) {
        mWorkingForFX = (aValue & 0x01) != 0;
        mHeat = ((aValue >> 1) & 0x3F) / 63.0d;
    }

    @Override
    public byte getUpdateData() {
        boolean working = mMachine && mMaxProgresstime > 0
            && getBaseMetaTileEntity() != null
            && getBaseMetaTileEntity().isAllowedToWork();
        int heatQuantized = (int) Math.round(mHeat * 63.0);
        if (heatQuantized < 0) heatQuantized = 0;
        if (heatQuantized > 63) heatQuantized = 63;
        return (byte) ((heatQuantized << 1) | (working ? 0x01 : 0x00));
    }

    // —— NBT ——

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setInteger("mSingularityMode", mSingularityMode);
        aNBT.setInteger("mSingularityModeTicks", mSingularityModeTicks);
        aNBT.setDouble("oreAccumulator", mOreAccumulator);
        aNBT.setString("lastDimAbbr", lastDimAbbr);
        aNBT.setString("mLastOreName", mLastOreName);
        aNBT.setBoolean("dropMapValid", dropMapValid);
        aNBT.setInteger("mCurrentDimId", mCurrentDimId);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mSingularityMode = aNBT.getInteger("mSingularityMode");
        mSingularityModeTicks = aNBT.getInteger("mSingularityModeTicks");
        mOreAccumulator = aNBT.getDouble("oreAccumulator");
        lastDimAbbr = aNBT.getString("lastDimAbbr");
        mLastOreName = aNBT.getString("mLastOreName");
        dropMapValid = aNBT.getBoolean("dropMapValid");
        mCurrentDimId = aNBT.getInteger("mCurrentDimId");
        if ("None".equals(lastDimAbbr)) {
            calculateDefaultDropMap();
        } else if (isPluginLoaded()) {
            recalculateDropMap(lastDimAbbr);
        }
    }

    // —— tooltip / GUI / 信息数据 ——

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        String keyPrefix = getTooltipKeyPrefix();
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal(keyPrefix + "type"))
            .addInfo(StatCollector.translateToLocal(keyPrefix + "desc"))
            .addInfo(EnumChatFormatting.AQUA + StatCollector.translateToLocal(keyPrefix + "desc2"))
            .addInfo(EnumChatFormatting.GREEN + StatCollector.translateToLocal(keyPrefix + "desc3"))
            .addInfo(EnumChatFormatting.RED + StatCollector.translateToLocal(keyPrefix + "desc4"))
            .addInfo(EnumChatFormatting.DARK_PURPLE + StatCollector.translateToLocal(keyPrefix + "desc5"))
            .addSeparator()
            .addInfo(
                EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.tooltip.shared.steam_cost")
                    + EnumChatFormatting.WHITE
                    + " "
                    + NumberFormatUtil.formatNumber(NORMAL_STEAM_PER_SECOND)
                    + " L/s ("
                    + StatCollector.translateToLocal(keyPrefix + "steam_cost_normal")
                    + ") / "
                    + NumberFormatUtil.formatNumber(DENSE_STEAM_PER_SECOND)
                    + " L/s ("
                    + StatCollector.translateToLocal(keyPrefix + "steam_cost_dense")
                    + ")")
            .beginStructureBlock(26, 26, 8, false)
            .addController(StatCollector.translateToLocal(keyPrefix + "ctrl"))
            .addOtherStructurePart(
                StatCollector.translateToLocal("gtsr.tooltip.shared.steam_input_hatch"),
                StatCollector.translateToLocal(keyPrefix + "steam_input"),
                1)
            .addInputBus(StatCollector.translateToLocal(keyPrefix + "input_bus"), 1)
            .addOutputBus(StatCollector.translateToLocal(keyPrefix + "output_bus"), 1)
            .addStructureInfo("")
            .addStructureInfo(EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal(keyPrefix + "desc6"))
            .addStructureInfo(EnumChatFormatting.DARK_PURPLE + StatCollector.translateToLocal(keyPrefix + "desc7"))
            .addStructureHint("gtsr.tooltip.shared.no_maintenance")
            .addStructureHint(keyPrefix + "hint_dimension")
            .toolTipFinisher(
                EnumChatFormatting.AQUA + "GT"
                    + EnumChatFormatting.GREEN
                    + "-"
                    + EnumChatFormatting.GOLD
                    + "Steam"
                    + EnumChatFormatting.RED
                    + "-"
                    + EnumChatFormatting.BLUE
                    + "Reborn");
        return tt;
    }

    @Override
    protected gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui<?> getGui() {
        return new MTECrustMatterAggregatorGui(this);
    }

    @Override
    public String[] getInfoData() {
        String tooltipKeyPrefix = getTooltipKeyPrefix();
        String guiKeyPrefix = getGuiKeyPrefix();
        ArrayList<String> info = new ArrayList<>();
        info.add(
            EnumChatFormatting.BLUE + StatCollector.translateToLocal(tooltipKeyPrefix + "type")
                + EnumChatFormatting.RESET);
        if (!mMachine) {
            info.add(EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.gui.building"));
            return info.toArray(new String[0]);
        }
        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal(guiKeyPrefix + "heat")
                + EnumChatFormatting.RED
                + String.format("%.1f%%", mHeat * 100.0d)
                + EnumChatFormatting.RESET);
        String modeKey = mSingularityMode == 2 ? guiKeyPrefix + "mode.critical"
            : mSingularityMode == 1 ? guiKeyPrefix + "mode.steam" : guiKeyPrefix + "mode.off";
        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal(guiKeyPrefix + "mode")
                + EnumChatFormatting.GOLD
                + StatCollector.translateToLocal(modeKey)
                + EnumChatFormatting.RESET);
        String fuelValue = mSingularityModeTicks > 0 ? String.format("%ds", mSingularityModeTicks / 20)
            : StatCollector.translateToLocal(guiKeyPrefix + "fuel_no_fuel");
        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal(guiKeyPrefix + "fuel_time")
                + EnumChatFormatting.RED
                + fuelValue
                + EnumChatFormatting.RESET);
        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal(guiKeyPrefix + "dimension")
                + EnumChatFormatting.GREEN
                + getDimensionDisplayName()
                + EnumChatFormatting.RESET);
        return info.toArray(new String[0]);
    }
}
