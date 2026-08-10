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
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChatComponentTranslation;
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
import com.miaokatze.gtsr.api.compat.GTVersionCompat;
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.gui.AggregatorConfigGuiFactory;
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
import gregtech.api.enums.OrePrefixes;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.objects.ItemData;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrorRegistry;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTRecipe;
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
 * 模式 0 (6,0,1, fx10) / 模式 1 (8,0,2, fx15) / 模式 2 (12,0,4, fx20)（range, speed, damage, fxRadius）。
 *
 * 定向模式（螺丝刀切换，服务端）：矿池只认槽 1（控制器槽），插件槽（槽 2-25）禁用放入；
 * 定向矿石集合内的矿石被瞄准，抽取时跳过非定向矿石；每 tick 消耗 UU 物质
 * （率 = (1+矿石模式/时运加成) × 10/定向权重和 L/s，权重和为 0 不可运行）；
 * 蒸汽倍率固定 +100% 取代维度槽增幅与过滤项；定向模式下失控奇点节点颜色变紫。
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
    // 矿石模式蒸汽加成（0 原矿 / 1 粗矿 / 2 粉碎矿）
    public static final double[] ORE_MODE_STEAM_BONUS = { 0.0d, 0.2d, 0.5d };
    // 时运等级 0-6 蒸汽加成
    public static final double[] FORTUNE_STEAM_BONUS = { 0.0d, 0.5d, 1.0d, 1.2d, 1.5d, 1.8d, 2.0d };
    // 每个额外维度物品槽的蒸汽加成
    public static final double SLOT_STEAM_PER_EXTRA = 0.2d;

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

    // —— 维度 / 虚空采矿（多维度矿池）——
    public String lastDimAbbr = "None";
    public String mLastOreName = "";
    public boolean dropMapValid = false;
    public int mCurrentDimId = 0;
    protected boolean mDefaultDimSupported = false;
    // 矿石模式：0=原矿 / 1=粗矿 / 2=粉碎矿
    public int mOreMode = 0;
    // 时运等级 0-6（上限随奇点模式 2/4/6）
    public int mFortuneLevel = 0;
    // 终端插件槽（UI 槽 2-25；槽 1 = mInventory[1]，同一数据源）
    private final ItemStack[] mPluginSlots = new ItemStack[24];
    // 被过滤（"已解放权重"）的矿石
    private final Set<GTUtility.ItemId> mFilteredOres = new HashSet<>();
    // 定向模式开关（服务端；螺丝刀切换）
    private boolean mDirectionalMode = false;
    // 定向矿石集合（定向模式抽取目标）
    private final Set<GTUtility.ItemId> mDirectionalOres = new HashSet<>();
    // 定向模式 UU 物质小数累积（不持久化）
    private double mUuAccumulator = 0.0d;
    // 多维度矿池（无插件槽时含默认当前维度）
    private final List<PoolDim> mPool = new ArrayList<>();
    // 矿池重建标记（槽位适配器写入时置位，checkProcessing 轮询消费）
    private boolean mPoolDirty = false;
    // 原生粉碎倍率缓存（池重建时失效）
    private final Map<GTUtility.ItemId, Integer> mNativeCrushedFactorCache = new HashMap<>();
    // 插件槽 IInventory 适配器（惰性创建）
    private IInventory mPluginSlotInventory = null;

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

    // 地壳物质聚合器无等级概念，GUI 终端隐藏等级行。
    @Override
    public boolean isHideTierInGui() {
        return true;
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
            // A = 仓室位（固体钢机械外壳）：casing-first，NEI 投影优先渲染外壳；
            // 真实 hatch 坐标上 casing 匹配失败后继续匹配 hatch adder（流体输入/耐压蒸汽/输入总线可选，输出总线必填）
            .addElement(
                'A',
                ofChain(
                    ofBlock(GameRegistry.findBlock("gregtech", "gt.blockcasings2"), 0),
                    // 流体输入仓（MTEHatchInput 含 ME 输入仓 + 耐压蒸汽输入仓；shouldReject 防重复），可选
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
                    // 输出总线 = 矿石输出（必填）
                    buildHatchAdder(MTECrustMatterAggregator.class).atLeast(SingularityHatchElement.SteamOutputBus)
                        .casingIndex(casingIndex)
                        .hint(1)
                        .build()))
            // B = 纯钢齿轮箱外壳
            .addElement('B', ofBlock(GameRegistry.findBlock("gregtech", "gt.blockcasings2"), 3))
            .addElement('C', ofBlock(GameRegistry.findBlock("gregtech", "gt.blockcasings2"), 13))
            .addElement('D', ofBlock(GregTechAPI.sBlockFrames, Materials.Steel.mMetaItemSubID))
            // E = 防爆玻璃（beta-1: IC2 blockAlloyGlass/meta0；beta-2: gt.blockglass1/meta10，经 GTVersionCompat 适配）
            .addElement(
                'E',
                ofBlock(GTVersionCompat.getReinforcedGlassBlock(), GTVersionCompat.getReinforcedGlassMeta()))
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

        // 结构要求：输出总线 ≥1 必填；流体输入仓/耐压蒸汽仓/物品输入总线（奇点燃料）全部可选
        if (mOutputBusses.isEmpty()) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }
        updateAllHatchTextures();
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int colorIndex, boolean aActive, boolean redstoneLevel) {
        // 控制器沿用旧 SCSB/CSB 系列材质：正面 固体钢外壳 + 采矿钻头叠层（OVERLAY_FRONT_ORE_DRILL），
        // 其余面仅外壳；不继承基类 Entangler 面板。叠层为 GT5U 内置常量，无需 registerIcons。
        int casingIndex = GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings2, 0);
        if (side == facing) {
            return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(casingIndex),
                TextureFactory.of(
                    aActive ? Textures.BlockIcons.OVERLAY_FRONT_ORE_DRILL_ACTIVE
                        : Textures.BlockIcons.OVERLAY_FRONT_ORE_DRILL) };
        }
        return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(casingIndex) };
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

    // —— 维度 / 虚空采矿（多维度矿池：槽 1 = mInventory[1]，槽 2-25 = mPluginSlots；无槽回退默认当前维度）——

    @Override
    public void onFirstTick(IGregTechTileEntity aBaseMetaTileEntity) {
        super.onFirstTick(aBaseMetaTileEntity);
        mCurrentDimId = aBaseMetaTileEntity.getWorld().provider.dimensionId;
        rebuildPool();
    }

    /** 判定物品是否为 GT NEI Ore Plugin 的维度显示物品（判空安全）。 */
    public static boolean isDimensionDisplayItem(ItemStack stack) {
        if (stack == null) return false;
        if (!isPluginLoaded()) return false;
        Item item = stack.getItem();
        return item != null && itemDimDisplayClass != null && itemDimDisplayClass.isInstance(item);
    }

    /** 反射读取单个维度显示物品的维度缩写（非维度物品或读取失败返回 null）。 */
    private String readDimensionAbbrFromStack(ItemStack stack) {
        if (!isDimensionDisplayItem(stack)) return null;
        try {
            Object result = getDimensionMethod.invoke(null, stack);
            if (result instanceof String) return (String) result;
        } catch (Exception e) {
            GTSteamReborn.LOG.warn("[CrustMatterAggregator] 读取维度物品失败", e);
        }
        return null;
    }

    /** 25 个维度槽（槽 1 = 控制器槽 mInventory[1]，槽 2-25 = 插件槽；定向模式仅槽 1 生效，见 collectDimensionAbbrs）。 */
    private List<ItemStack> getDimensionStacks() {
        List<ItemStack> stacks = new ArrayList<>();
        stacks.add(mInventory[1]);
        Collections.addAll(stacks, mPluginSlots);
        return stacks;
    }

    /** 收集当前 25 槽中去重后的维度缩写列表（保持槽位顺序）；定向模式只收集槽 1（控制器槽），插件槽忽略。 */
    private List<String> collectDimensionAbbrs() {
        List<String> abbrs = new ArrayList<>();
        // 定向模式只认槽 1：重建矿池时插件槽内容不参与
        List<ItemStack> stacks = mDirectionalMode ? Collections.singletonList(mInventory[1]) : getDimensionStacks();
        for (ItemStack stack : stacks) {
            String abbr = readDimensionAbbrFromStack(stack);
            if (abbr == null || "None".equals(abbr) || abbrs.contains(abbr)) continue;
            abbrs.add(abbr);
        }
        return abbrs;
    }

    /** 构建单个维度池条目：查表取 DropMap 并缓存分布（extra 合并入 internalMap）。 */
    private PoolDim createPoolDim(String dimAbbr, String dimName) {
        if (dimName == null) {
            return new PoolDim(dimAbbr, null, new VoidMinerUtility.DropMap(), new VoidMinerUtility.DropMap());
        }
        VoidMinerUtility.DropMap dropMap = VoidMinerUtilityShim.getDropMap(dimName);
        VoidMinerUtility.DropMap extraDropMap = VoidMinerUtilityShim.getExtraDropMap(dimName);
        dropMap.isDistributionCached(extraDropMap);
        return new PoolDim(dimAbbr, dimName, dropMap, extraDropMap);
    }

    /** 重建多维度矿池（无插件槽回退默认当前维度）；同时使原生粉碎倍率缓存失效。 */
    private void rebuildPool() {
        mPoolDirty = false;
        mNativeCrushedFactorCache.clear();
        mPool.clear();
        List<String> abbrs = collectDimensionAbbrs();
        if (abbrs.isEmpty()) {
            lastDimAbbr = "None";
            mDefaultDimSupported = false;
            String dimName = VoidMinerUtilityShim.dimIdToName(mCurrentDimId);
            if (dimName != null) {
                mDefaultDimSupported = true;
                mPool.add(createPoolDim("None", dimName));
            }
        } else {
            mDefaultDimSupported = false;
            StringBuilder summary = new StringBuilder();
            for (String abbr : abbrs) {
                if (summary.length() > 0) summary.append("+");
                summary.append(abbr);
                String dimName = ABBR_TO_DIM_NAME.get(abbr);
                if (dimName == null) {
                    GTSteamReborn.LOG.warn(
                        "[CrustMatterAggregator] 未知维度缩写: " + abbr + "（ABBR_TO_DIM_NAME 表中无此条目，请确认 GTNEIOrePlugin 版本）");
                    mPool.add(createPoolDim(abbr, null));
                    continue;
                }
                mPool.add(createPoolDim(abbr, dimName));
            }
            lastDimAbbr = summary.toString();
        }
        float totalWeight = 0.0f;
        for (PoolDim pd : mPool) {
            totalWeight += pd.dropMap.getTotalWeight();
        }
        dropMapValid = totalWeight > 0;
    }

    /** 槽位内容变化时由插件槽适配器调用：下次 checkProcessing 重建矿池。 */
    public void markPoolDirty() {
        mPoolDirty = true;
    }

    /**
     * 手动刷新矿池（终端 UI「刷新」按钮 C2S 调用）：立即重建并持久化，不等 checkProcessing 轮询，
     * 使「放入/移除维度物品」立即反映到矿石浏览器与抽取池。
     */
    public void forceRefreshPool() {
        markPoolDirty();
        rebuildPool();
        if (getBaseMetaTileEntity() != null) getBaseMetaTileEntity().markDirty();
    }

    /** 控制器槽（mInventory[1]，终端槽 1 与主 GUI 控制器槽同一数据源）栈上限 1：点击/shift-click 全路径生效。 */
    @Override
    public int getSlotLimit(int slot) {
        if (slot == 1) return 1;
        return super.getSlotLimit(slot);
    }

    /** 25 槽维度集合与当前池是否一致（含无槽模式下当前维度变化检测）。 */
    private boolean isPoolCurrent() {
        List<String> current = collectDimensionAbbrs();
        if (current.isEmpty()) {
            if (getBaseMetaTileEntity().getWorld().provider.dimensionId != mCurrentDimId) return false;
            if (mPool.isEmpty()) return !mDefaultDimSupported;
            return mPool.size() == 1 && "None".equals(mPool.get(0).dimAbbr);
        }
        if (mPool.size() != current.size()) return false;
        for (PoolDim pd : mPool) {
            if (!current.contains(pd.dimAbbr)) return false;
        }
        return true;
    }

    /** 槽位轮询：池过期（槽位变化/默认维度传送）或标记脏时重建。 */
    private void rebuildPoolIfNeeded() {
        if (mPoolDirty || !isPoolCurrent()) {
            rebuildPool();
        }
    }

    /** GUI 维度显示名：多槽显示缩写摘要（如 "Ow+Ne"），无槽时显示默认维度名。 */
    public String getDimensionDisplayName() {
        if (mPool.isEmpty()) {
            String dimName = VoidMinerUtilityShim.dimIdToName(mCurrentDimId);
            return dimName != null ? dimName : ("Dim " + mCurrentDimId);
        }
        StringBuilder sb = new StringBuilder();
        for (PoolDim pd : mPool) {
            if (sb.length() > 0) sb.append("+");
            sb.append("None".equals(pd.dimAbbr) ? pd.dimName : pd.dimAbbr);
        }
        return sb.toString();
    }

    private boolean hasUsableDimension() {
        return !mPool.isEmpty();
    }

    // —— 过滤与加权抽取（"已解放权重"语义：被过滤矿石完全从抽取分布中剔除）——

    public boolean isOreFiltered(GTUtility.ItemId id) {
        return id != null && mFilteredOres.contains(id);
    }

    public void setOreFiltered(GTUtility.ItemId id, boolean filtered) {
        if (id == null) return;
        if (filtered) {
            mFilteredOres.add(id);
        } else {
            mFilteredOres.remove(id);
        }
        if (getBaseMetaTileEntity() != null) getBaseMetaTileEntity().markDirty();
    }

    /** 被过滤矿石跨维权重和（蒸汽倍率加成用）。 */
    public float getFilteredWeightSum() {
        float sum = 0.0f;
        for (PoolDim pd : mPool) {
            for (Map.Entry<GTUtility.ItemId, Float> entry : pd.dropMap.getInternalMap()
                .entrySet()) {
                if (mFilteredOres.contains(entry.getKey())) sum += entry.getValue();
            }
        }
        return sum;
    }

    // —— 定向模式（定向抽取：仅槽 1 维度矿池 + 定向矿石集合，UU 物质驱动）——

    public boolean getDirectionalMode() {
        return mDirectionalMode;
    }

    /** 定向模式中该矿石是否被瞄准（定向抽取目标）。 */
    public boolean isOreAimed(GTUtility.ItemId id) {
        return id != null && mDirectionalOres.contains(id);
    }

    /** 增删定向矿石集合（服务端语义，仿 setOreFiltered）。 */
    public void setOreAimed(GTUtility.ItemId id, boolean aimed) {
        if (id == null) return;
        if (aimed) {
            mDirectionalOres.add(id);
        } else {
            mDirectionalOres.remove(id);
        }
        if (getBaseMetaTileEntity() != null) getBaseMetaTileEntity().markDirty();
    }

    /** 当前矿池内被定向矿石的权重和（UU 倍率与可运行性判定用）。 */
    public float getDirectionalWeightSum() {
        float sum = 0.0f;
        for (PoolDim pd : mPool) {
            for (Map.Entry<GTUtility.ItemId, Float> entry : pd.dropMap.getInternalMap()
                .entrySet()) {
                if (mDirectionalOres.contains(entry.getKey())) sum += entry.getValue();
            }
        }
        return sum;
    }

    /** 定向倍率 = 2500% ÷ 定向权重和（权重和为 0 返回 0，表示不可运行）。 */
    public double getDirectionalFactor() {
        float sum = getDirectionalWeightSum();
        return sum <= 0.0f ? 0.0d : 25.0d / sum;
    }

    /**
     * 消耗增加% 展示值（终端 UI「浏览器标题右侧」+X%）：定向模式 = 2500% ÷ 定向权重和；
     * 筛选模式 = 被过滤矿石权重和（即消耗增加百分比，倍率项 1+权重和/100）。
     */
    public double getWeightIncreasePercent() {
        if (mDirectionalMode) {
            float sum = getDirectionalWeightSum();
            return sum <= 0.0f ? 0.0d : 2500.0d / sum;
        }
        return getFilteredWeightSum();
    }

    /** 维度槽消耗增加% 展示值（终端 UI「刷新按钮右侧」+X%）：定向模式 = 固定 200%；筛选模式 = 20% × 额外维度槽数。 */
    public double getDimensionIncreasePercent() {
        if (mDirectionalMode) return 200.0d;
        int slotCount = 0;
        for (ItemStack stack : getDimensionStacks()) {
            if (isDimensionDisplayItem(stack)) slotCount++;
        }
        return 20.0d * Math.max(0, slotCount - 1);
    }

    /** UU 倍率 = (1 + 矿石模式加成 + 时运加成) × 定向倍率。 */
    public double getUUMultiplier() {
        return (1.0d + ORE_MODE_STEAM_BONUS[Math.min(Math.max(mOreMode, 0), 2)]
            + FORTUNE_STEAM_BONUS[Math.min(Math.max(mFortuneLevel, 0), 6)]) * getDirectionalFactor();
    }

    /** UU 消耗速率（L/s）：UU 基础 1 L/s × UU 倍率。 */
    public double getUURatePerSecond() {
        return 1.0d * getUUMultiplier();
    }

    /**
     * 切换定向模式（服务端入口，幂等）：进入时清空过滤与定向集合；立即按新模式重建矿池（定向只留槽 1）；
     * 强制停机并清空奇点模式；时运钳位到奇点模式 0 的上限 2。
     */
    public void toggleDirectionalMode(EntityPlayer aPlayer) {
        mDirectionalMode = !mDirectionalMode;
        if (mDirectionalMode) {
            mFilteredOres.clear();
            mDirectionalOres.clear();
        }
        forceRefreshPool();
        mMaxProgresstime = 0;
        mProgresstime = 0;
        mSingularityMode = 0;
        mSingularityModeTicks = 0;
        if (mFortuneLevel > 2) mFortuneLevel = 2;
        if (getBaseMetaTileEntity() != null) getBaseMetaTileEntity().markDirty();
        if (aPlayer != null) {
            aPlayer.addChatMessage(
                new ChatComponentTranslation(
                    mDirectionalMode ? "gtsr.aggregator_config.directional.chat.on"
                        : "gtsr.aggregator_config.directional.chat.off"));
        }
    }

    /** 过滤加权抽取：跨池各维 internalMap（已含 extra 合并）按权重累加后线性游走，跳过被过滤矿石；定向模式另跳过非定向矿石。 */
    private GTUtility.ItemId extractNextOre() {
        double total = 0.0d;
        for (PoolDim pd : mPool) {
            for (Map.Entry<GTUtility.ItemId, Float> entry : pd.dropMap.getInternalMap()
                .entrySet()) {
                if (mFilteredOres.contains(entry.getKey())
                    || (mDirectionalMode && !mDirectionalOres.contains(entry.getKey()))) continue;
                total += entry.getValue();
            }
        }
        if (total <= 0.0d) return null;
        double r = ThreadLocalRandom.current()
            .nextDouble() * total;
        for (PoolDim pd : mPool) {
            for (Map.Entry<GTUtility.ItemId, Float> entry : pd.dropMap.getInternalMap()
                .entrySet()) {
                if (mFilteredOres.contains(entry.getKey())
                    || (mDirectionalMode && !mDirectionalOres.contains(entry.getKey()))) continue;
                r -= entry.getValue();
                if (r < 0.0d) return entry.getKey();
            }
        }
        return null; // 浮点误差兜底
    }

    /** 矿池单维度条目：维度缩写/内部名 + 主 DropMap（extra 已合并）。 */
    private static class PoolDim {

        final String dimAbbr;
        final String dimName;
        final VoidMinerUtility.DropMap dropMap;
        final VoidMinerUtility.DropMap extraDropMap;

        PoolDim(String dimAbbr, String dimName, VoidMinerUtility.DropMap dropMap,
            VoidMinerUtility.DropMap extraDropMap) {
            this.dimAbbr = dimAbbr;
            this.dimName = dimName;
            this.dropMap = dropMap;
            this.extraDropMap = extraDropMap;
        }
    }

    // —— 浏览器数据（后续终端 UI 切片用）——

    /** 矿石浏览器条目：矿石、跨维权重和、所在维度缩写列表、是否被过滤、是否被定向瞄准。 */
    public static class OreEntryInfo {

        public final ItemStack ore;
        public float weight;
        public final List<String> dimAbbrs;
        public final boolean filtered;
        public final boolean aimed;

        public OreEntryInfo(ItemStack ore, float weight, List<String> dimAbbrs, boolean filtered) {
            this(ore, weight, dimAbbrs, filtered, false);
        }

        public OreEntryInfo(ItemStack ore, float weight, List<String> dimAbbrs, boolean filtered, boolean aimed) {
            this.ore = ore;
            this.weight = weight;
            this.dimAbbrs = dimAbbrs;
            this.filtered = filtered;
            this.aimed = aimed;
        }
    }

    /** 矿石浏览器数据（服务端调用）：按 GTUtility.ItemId 跨维合并权重与维度缩写；无池返回空列表。 */
    public List<OreEntryInfo> getOreEntries() {
        List<OreEntryInfo> entries = new ArrayList<>();
        if (mPool.isEmpty()) return entries;
        Map<GTUtility.ItemId, OreEntryInfo> byId = new LinkedHashMap<>();
        for (PoolDim pd : mPool) {
            for (Map.Entry<GTUtility.ItemId, Float> entry : pd.dropMap.getInternalMap()
                .entrySet()) {
                OreEntryInfo info = byId.get(entry.getKey());
                if (info == null) {
                    info = new OreEntryInfo(
                        entry.getKey()
                            .getItemStack(),
                        entry.getValue(),
                        new ArrayList<>(),
                        mFilteredOres.contains(entry.getKey()),
                        mDirectionalOres.contains(entry.getKey()));
                    byId.put(entry.getKey(), info);
                } else {
                    info.weight += entry.getValue();
                }
                info.dimAbbrs.add(pd.dimAbbr);
            }
        }
        entries.addAll(byId.values());
        return entries;
    }

    // —— 插件槽位适配器 ——

    /** 终端插件槽（容量 24、栈上限 1、仅接受维度显示物品；定向模式只出不进）的轻量 IInventory 适配器。 */
    public IInventory getPluginSlotInventory() {
        if (mPluginSlotInventory == null) mPluginSlotInventory = new PluginSlotInventory();
        return mPluginSlotInventory;
    }

    private class PluginSlotInventory implements IInventory {

        @Override
        public int getSizeInventory() {
            return mPluginSlots.length;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot < 0 || slot >= mPluginSlots.length) return null;
            return mPluginSlots[slot];
        }

        @Override
        public ItemStack decrStackSize(int slot, int amount) {
            ItemStack stack = getStackInSlot(slot);
            if (stack == null) return null;
            ItemStack result;
            if (stack.stackSize <= amount) {
                result = stack;
                mPluginSlots[slot] = null;
            } else {
                result = stack.splitStack(amount);
            }
            onPluginSlotChanged();
            return result;
        }

        @Override
        public ItemStack getStackInSlotOnClosing(int slot) {
            return getStackInSlot(slot);
        }

        @Override
        public void setInventorySlotContents(int slot, ItemStack stack) {
            if (slot < 0 || slot >= mPluginSlots.length) return;
            // 定向模式只出不进：仅拒绝非 null 放入；置空（取走）必须放行——
            // MUI2 取走路径为 splitStack 原地减 0 后 putStack(null) 落回本方法，若整体拒绝会残留
            // 0 尺寸幽灵 stack，槽位仍可重复取 → 刷物品。放入另有 isItemValidForSlot + ModularSlot.filter 双拒。
            if (mDirectionalMode && stack != null) return;
            // 仅接受维度显示物品，其余物品直接拒绝
            if (stack != null && !isDimensionDisplayItem(stack)) return;
            mPluginSlots[slot] = stack;
            onPluginSlotChanged();
        }

        @Override
        public String getInventoryName() {
            return "gtsr.crust_matter_agg.pluginSlots";
        }

        @Override
        public boolean hasCustomInventoryName() {
            return true;
        }

        @Override
        public int getInventoryStackLimit() {
            return 1;
        }

        @Override
        public void markDirty() {
            onPluginSlotChanged();
        }

        @Override
        public boolean isUseableByPlayer(EntityPlayer player) {
            return true;
        }

        @Override
        public void openInventory() {}

        @Override
        public void closeInventory() {}

        @Override
        public boolean isItemValidForSlot(int slot, ItemStack stack) {
            // 定向模式只出不进
            if (mDirectionalMode) return false;
            return isDimensionDisplayItem(stack);
        }

        /** 槽位内容变化：标记矿池重建并持久化。 */
        private void onPluginSlotChanged() {
            markPoolDirty();
            MTECrustMatterAggregator.this.markDirty();
        }
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
        rebuildPoolIfNeeded();

        if (!hasUsableDimension()) {
            return SimpleCheckRecipeResult.ofFailure("gtsr.gui.crust_matter_agg.no_dimension");
        }
        if (!dropMapValid) {
            return SimpleCheckRecipeResult.ofFailure("gtsr.gui.crust_matter_agg.no_ores");
        }
        if (mDirectionalMode && getDirectionalWeightSum() <= 0.0f) {
            return SimpleCheckRecipeResult.ofFailure("gtsr.gui.crust_matter_agg.no_direction");
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
        if (mPool.isEmpty() || !dropMapValid || mActiveGrade < 0) {
            updateSlots();
            return;
        }
        // 产出 = 10 * mHeat * GRADE_COEF[grade] * singCoef，实数累积，整数部分经过滤加权抽取输出
        mOreAccumulator += ORES_PER_HEAT_UNIT * mHeat
            * GRADE_COEF[mActiveGrade]
            * SINGULARITY_OUTPUT_COEF[mSingularityMode];
        int out = (int) Math.floor(mOreAccumulator);
        for (int i = 0; i < out; i++) {
            GTUtility.ItemId oreId = extractNextOre();
            if (oreId == null) break;
            ItemStack oreStack = oreId.getItemStack();
            if (oreStack == null) break;
            outputOre(oreStack);
            mLastOreName = oreStack.getDisplayName();
            mOreAccumulator -= 1.0d;
        }
        updateSlots();
    }

    // —— 产出形态转换（粗矿 / 粉碎矿模式）——

    /** 按当前矿石模式输出单个原矿：模式 0 原样输出；模式 1/2 转 crushed（无 crushed 形态保持原矿）。 */
    private void outputOre(ItemStack rawOre) {
        if (mOreMode == 0) {
            addOutputPartial(rawOre);
            return;
        }
        int nativeFactor = getNativeCrushedFactor(rawOre);
        int modeMultiplier = mOreMode == 2 ? 3 : 1;
        int count = nativeFactor * modeMultiplier * (1 + mFortuneLevel);
        Materials material = getOreMaterial(rawOre);
        ItemStack crushed = material == null ? null : GTOreDictUnificator.get(OrePrefixes.crushed, material, count);
        if (crushed == null) {
            addOutputPartial(rawOre);
        } else {
            addOutputPartial(crushed);
        }
    }

    /** 取物品的 GT 材料（非 GT 物品返回 null）。 */
    private Materials getOreMaterial(ItemStack stack) {
        ItemData data = GTOreDictUnificator.getItemData(stack);
        if (data == null || data.mMaterial == null || data.mMaterial.mMaterial == null) return null;
        return data.mMaterial.mMaterial;
    }

    /** 原生粉碎倍率兜底小表：macerator 配方查询不可行时按材料名取实际值（GT5U ProcessingOre：macerator 输出 = 2×mOreMultiplier）。 */
    private static final Map<String, Integer> NATIVE_CRUSHED_FACTOR_FALLBACK = new HashMap<>();
    static {
        NATIVE_CRUSHED_FACTOR_FALLBACK.put("Redstone", 10);
        NATIVE_CRUSHED_FACTOR_FALLBACK.put("Cryolite", 8);
    }

    /**
     * 原矿原生粉碎倍率（带缓存，池重建时失效）：默认 2；优先查 RecipeMaps.maceratorRecipes 对该矿的配方输出中
     * crushed/crushedPurified/crushedCentrifuged/dust 类产物数量，与 2 取 max；查询不可行时回退默认 2，
     * 并对红石/冰晶石按兜底小表取值。
     */
    private int getNativeCrushedFactor(ItemStack rawOre) {
        GTUtility.ItemId id = GTUtility.ItemId.create(rawOre);
        Integer cached = mNativeCrushedFactorCache.get(id);
        if (cached != null) return cached;
        int factor = 2;
        boolean queried = false;
        try {
            GTRecipe recipe = RecipeMaps.maceratorRecipes.findRecipeQuery()
                .items(rawOre)
                .find();
            queried = true;
            if (recipe != null && recipe.mOutputs != null) {
                int crushedSum = 0;
                for (ItemStack out : recipe.mOutputs) {
                    if (out == null || out.getItem() == null) continue;
                    ItemData data = GTOreDictUnificator.getItemData(out);
                    if (data == null || data.mPrefix == null) continue;
                    OrePrefixes prefix = data.mPrefix;
                    if (prefix == OrePrefixes.crushed || prefix == OrePrefixes.crushedPurified
                        || prefix == OrePrefixes.crushedCentrifuged
                        || prefix == OrePrefixes.dust
                        || prefix == OrePrefixes.dustImpure
                        || prefix == OrePrefixes.dustPure
                        || prefix == OrePrefixes.dustRefined
                        || prefix == OrePrefixes.dustSmall
                        || prefix == OrePrefixes.dustTiny) {
                        crushedSum += out.stackSize;
                    }
                }
                if (crushedSum > 0) factor = Math.max(2, crushedSum);
            }
        } catch (Throwable t) {
            queried = false;
            GTSteamReborn.LOG.warn("[CrustMatterAggregator] macerator 配方查询失败，回退默认粉碎倍率", t);
        }
        if (!queried) {
            Materials material = getOreMaterial(rawOre);
            if (material != null) {
                Integer fallback = NATIVE_CRUSHED_FACTOR_FALLBACK.get(material.mName);
                if (fallback != null) factor = Math.max(2, fallback);
            }
        }
        mNativeCrushedFactorCache.put(id, factor);
        return factor;
    }

    /** 周期内每 tick 从输入仓扣减当前档位蒸汽；不足返回 false（周期停止）。 */
    private boolean depleteSteamForTick() {
        if (mActiveGrade < 0) return false;
        int basePerTick = mActiveDense ? DENSE_STEAM_PER_TICK : NORMAL_STEAM_PER_TICK;
        // 蒸汽倍率：矿石模式/时运/额外维度槽/被过滤矿石权重加成（向上取整）
        int remaining = (int) Math.ceil(basePerTick * getSteamMultiplier());
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

    /**
     * 定向模式周期内每 tick 扣减 UU 物质（率 = getUURatePerSecond()/20 L/tick，实数累积取整扣减，
     * 主流体输入仓 + 样板仓双路，仿 depleteSteamForTick）；扣不足返回 false（周期停止）。
     */
    private boolean depleteUUMatterForTick() {
        mUuAccumulator += getUURatePerSecond() / 20.0d;
        int toDrain = (int) Math.floor(mUuAccumulator);
        if (toDrain <= 0) return true;
        FluidStack request = FluidRegistry.getFluidStack("uumatter", 1);
        if (request == null) return false;
        int remaining = toDrain;
        for (MTEHatch hatch : getSteamInputHatches()) {
            if (remaining <= 0) break;
            FluidStack full = request.copy();
            full.amount = Integer.MAX_VALUE;
            FluidStack available = hatch.drain(ForgeDirection.UNKNOWN, full, false);
            if (available == null || available.amount <= 0) continue;
            int toTake = Math.min(remaining, available.amount);
            FluidStack drainReq = request.copy();
            drainReq.amount = toTake;
            hatch.drain(ForgeDirection.UNKNOWN, drainReq, true);
            remaining -= toTake;
        }
        // 主仓不足时继续扣样板仓（ME 输入仓；太阳能锅炉同款路径）
        if (remaining > 0) {
            FluidStack dualReq = request.copy();
            dualReq.amount = remaining;
            remaining -= GTSRHatchFluidAccess.depleteFluidFromDuals(mDualInputHatches, dualReq);
        }
        // 已成功扣减部分从累积中扣除，未扣足部分保留待下次运行（仅记录真实欠账）
        mUuAccumulator -= (toDrain - remaining);
        return remaining <= 0;
    }

    // —— 矿石模式 / 时运（终端 UI 调用，服务端执行）——

    /** 循环矿石模式 0(原矿)→1(粗矿)→2(粉碎矿)→0；切回原矿模式时清零时运。 */
    public void cycleOreMode() {
        mOreMode = (mOreMode + 1) % 3;
        if (mOreMode == 0) mFortuneLevel = 0;
        if (getBaseMetaTileEntity() != null) getBaseMetaTileEntity().markDirty();
    }

    /** 循环时运等级：(当前+1)%7 后钳位到奇点模式上限；原矿模式直接回 0。 */
    public void cycleFortuneLevel() {
        if (mOreMode == 0) {
            mFortuneLevel = 0;
        } else {
            // 在 0..当前允许上限内循环（直接 %7 再钳位会在上限<6 时卡死在上限无法降级）
            mFortuneLevel = (mFortuneLevel + 1) % (getMaxAllowedFortuneLevel() + 1);
        }
        if (getBaseMetaTileEntity() != null) getBaseMetaTileEntity().markDirty();
    }

    /** 当前奇点模式允许的时运上限：模式 0/1/2 → 2/4/6。 */
    public int getMaxAllowedFortuneLevel() {
        switch (mSingularityMode) {
            case 2:
                return 6;
            case 1:
                return 4;
            default:
                return 2;
        }
    }

    // —— 蒸汽倍率 ——

    /**
     * 蒸汽消耗倍率 = (1+矿石模式加成+时运加成) × (1+0.2×(维度物品槽数-1)) × (1+被过滤矿石权重和/100)。
     * 定向模式：固定 +200% 取代维度槽增幅与过滤项，再乘定向倍率（UU 加速后蒸汽同步放大）。
     * depleteSteamForTick 按 basePerTick × 该倍率向上取整扣减。
     */
    public double getSteamMultiplier() {
        double modeBonus = ORE_MODE_STEAM_BONUS[Math.min(Math.max(mOreMode, 0), 2)];
        double fortuneBonus = FORTUNE_STEAM_BONUS[Math.min(Math.max(mFortuneLevel, 0), 6)];
        if (mDirectionalMode) return (1.0d + modeBonus + fortuneBonus) * 3.0d * getDirectionalFactor();
        int slotCount = 0;
        for (ItemStack stack : getDimensionStacks()) {
            if (isDimensionDisplayItem(stack)) slotCount++;
        }
        return (1.0d + modeBonus + fortuneBonus) * (1.0d + SLOT_STEAM_PER_EXTRA * Math.max(0, slotCount - 1))
            * (1.0d + getFilteredWeightSum() / 100.0d);
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
            // 定向模式：周期内每 tick 额外扣减 UU 物质；不足则停（与蒸汽扣减同语义）
            if (mDirectionalMode && aBaseMetaTileEntity.isAllowedToWork()
                && mMaxProgresstime > 0
                && !depleteUUMatterForTick()) {
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
                // 奇点模式回落后时运上限降为 2，超限时钳位
                if (mFortuneLevel > 2) mFortuneLevel = 2;
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
        double fxRadius;
        switch (mSingularityMode) {
            case 2:
                range = 12.0d;
                damage = 4.0d;
                fxRadius = 20.0d;
                break;
            case 1:
                range = 8.0d;
                damage = 2.0d;
                fxRadius = 15.0d;
                break;
            default:
                range = 6.0d;
                damage = 1.0d;
                fxRadius = 10.0d;
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
                mDirectionalMode ? "purple" : "black",
                fxRadius));
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
        aNBT.setInteger("mOreMode", mOreMode);
        aNBT.setInteger("mFortuneLevel", mFortuneLevel);
        // 插件槽（槽 2-25；槽 1 = mInventory[1] 由基类 Inventory 列表保存）
        NBTTagList pluginSlots = new NBTTagList();
        for (ItemStack stack : mPluginSlots) {
            NBTTagCompound slotTag = new NBTTagCompound();
            if (stack != null) stack.writeToNBT(slotTag);
            pluginSlots.appendTag(slotTag);
        }
        aNBT.setTag("mPluginSlots", pluginSlots);
        // 被过滤矿石（item 注册名 + meta）
        NBTTagList filteredList = new NBTTagList();
        for (GTUtility.ItemId id : mFilteredOres) {
            GameRegistry.UniqueIdentifier uid = GameRegistry.findUniqueIdentifierFor(id.item());
            if (uid == null) continue;
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("item", uid.modId + ":" + uid.name);
            tag.setShort("meta", (short) id.metaData());
            filteredList.appendTag(tag);
        }
        aNBT.setTag("mFilteredOres", filteredList);
        // 定向模式（开关 + 定向矿石集合）
        aNBT.setBoolean("mDirectionalMode", mDirectionalMode);
        NBTTagList directionalList = new NBTTagList();
        for (GTUtility.ItemId id : mDirectionalOres) {
            GameRegistry.UniqueIdentifier uid = GameRegistry.findUniqueIdentifierFor(id.item());
            if (uid == null) continue;
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("item", uid.modId + ":" + uid.name);
            tag.setShort("meta", (short) id.metaData());
            directionalList.appendTag(tag);
        }
        aNBT.setTag("mDirectionalOres", directionalList);
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
        mOreMode = aNBT.getInteger("mOreMode");
        mFortuneLevel = aNBT.getInteger("mFortuneLevel");
        if (mOreMode < 0 || mOreMode > 2) mOreMode = 0;
        if (mFortuneLevel < 0) mFortuneLevel = 0;
        mFortuneLevel = Math.min(mFortuneLevel, getMaxAllowedFortuneLevel());
        NBTTagList pluginSlots = aNBT.getTagList("mPluginSlots", 10);
        for (int i = 0; i < pluginSlots.tagCount() && i < mPluginSlots.length; i++) {
            mPluginSlots[i] = ItemStack.loadItemStackFromNBT(pluginSlots.getCompoundTagAt(i));
        }
        NBTTagList filteredList = aNBT.getTagList("mFilteredOres", 10);
        mFilteredOres.clear();
        for (int i = 0; i < filteredList.tagCount(); i++) {
            NBTTagCompound tag = filteredList.getCompoundTagAt(i);
            Item item = findItemByName(tag.getString("item"));
            if (item == null) continue;
            mFilteredOres.add(GTUtility.ItemId.createNoCopy(item, tag.getShort("meta"), null));
        }
        // 定向模式必须先于 rebuildPool() 读入（重建矿池依赖定向模式状态：定向只认槽 1）
        mDirectionalMode = aNBT.getBoolean("mDirectionalMode");
        NBTTagList directionalList = aNBT.getTagList("mDirectionalOres", 10);
        mDirectionalOres.clear();
        for (int i = 0; i < directionalList.tagCount(); i++) {
            NBTTagCompound tag = directionalList.getCompoundTagAt(i);
            Item item = findItemByName(tag.getString("item"));
            if (item == null) continue;
            mDirectionalOres.add(GTUtility.ItemId.createNoCopy(item, tag.getShort("meta"), null));
        }
        // 槽位与 mCurrentDimId 均已就绪（基类 Inventory 先于 loadNBTData 载入），重建矿池
        rebuildPool();
    }

    /** 按 "modid:name" 解析物品注册名（无法解析返回 null）。 */
    private static Item findItemByName(String itemName) {
        if (itemName == null || itemName.isEmpty()) return null;
        String[] parts = itemName.split(":", 2);
        if (parts.length != 2) return null;
        return GameRegistry.findItem(parts[0], parts[1]);
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
            .addInfo(EnumChatFormatting.LIGHT_PURPLE + StatCollector.translateToLocal(keyPrefix + "desc8"))
            .addInfo(EnumChatFormatting.LIGHT_PURPLE + StatCollector.translateToLocal(keyPrefix + "desc9"))
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

    /**
     * 手持枢纽终端右击：打开终端配置界面（Modern UI 2），不占用空手右键（空手右键仍打开普通机器 GUI）。
     * 注：原「空手+潜行」方案不可行——GT BaseMetaTileEntity 在潜行时拦截右击（用于贴墙放方块），
     * 本 MTE 的 onRightclick 根本收不到该事件，故沿用枢纽的同款持物右击方案。
     */
    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer, ForgeDirection side,
        float aX, float aY, float aZ) {
        ItemStack held = aPlayer.getHeldItem();
        if (held != null && GTSRItemList.HubTerminal.isStackEqual(held, false, true)) {
            if (aBaseMetaTileEntity.isServerSide()) {
                openConfigGui(aPlayer);
            }
            return true;
        }
        return super.onRightclick(aBaseMetaTileEntity, aPlayer, side, aX, aY, aZ);
    }

    /** 服务端调用：为玩家打开终端配置界面。 */
    public void openConfigGui(EntityPlayer player) {
        AggregatorConfigGuiFactory.open(player, this);
    }

    /** 螺丝刀右击：切换定向模式（服务端；幂等，进入时清空过滤/定向集合并按新模式重建矿池）。 */
    @Override
    public void onScrewdriverRightClick(ForgeDirection side, EntityPlayer aPlayer, float aX, float aY, float aZ,
        ItemStack aTool) {
        if (getBaseMetaTileEntity() == null || getBaseMetaTileEntity().isClientSide()) return;
        toggleDirectionalMode(aPlayer);
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
