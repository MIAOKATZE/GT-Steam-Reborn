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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;

import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElementCheckOnly;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.util.Vec3Impl;
import com.miaokatze.gtsr.api.compat.GTVersionCompat;
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.api.gui.OreEntryInfo;
import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.event.GTSRMachineEvent;
import com.miaokatze.gtsr.common.gui.AggregatorConfigGuiFactory;
import com.miaokatze.gtsr.common.gui.MTECrustMatterAggregatorGui;
import com.miaokatze.gtsr.common.machine.base.MTESingularityMachineBase;
import com.miaokatze.gtsr.common.machine.base.VoidMinerUtilityShim;
import com.miaokatze.gtsr.common.util.GTSROutputBusCompat;
import com.miaokatze.gtsr.common.util.GTSRUtils;
import com.miaokatze.gtsr.common.util.OreCrushedUtil;
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
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
import gregtech.api.objects.ItemData;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrorRegistry;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.GTMockWorld;
import gregtech.common.ores.OreInfo;
import gregtech.common.ores.OreManager;

/**
 * 地壳物质聚合器（Crust Matter Aggregator）——奇点地壳蒸汽掘进机（MTEVoidCrustSteamBorer）的全量改名替代。
 *
 * 脱离 GT++ 蒸汽基类自管蒸汽档位（脱离 MTESteamMultiBlockBase）：
 * - 档位 0=蒸汽 / 1=过热 / 2=超临界；致密流体（densesteam 族）整体优先于普通流体；
 * - 普通档消耗 1200 L/tick（24000 L/s）、周期 400 tick（20 秒）；致密档 12 L/tick（240 L/s）、周期 100 tick（5 秒）；
 * - 产出 = 10 * GRADE_COEF[grade] * 奇点模式系数（热量机制已删除，按恒满处理），实数累积（NBT 持久化），
 * 整数部分经 VoidMinerUtility DropMap 虚空采矿输出矿石。
 *
 * 热量机制已删除：产出恒满、粒子 H 位恒满浓度、字节通道热量位恒 63；基类热量字段与
 * shouldDecayHeat=false 覆写保留无害（基类不再对其做增益/衰减）。
 *
 * 奇点模式（巨型蒸汽轮机式，非右键式）：mSingularityMode 0/1/2（无/蒸汽纠缠/临界），
 * SINGULARITY_DURATION_TICKS=4000（200 秒）；每 20 tick 检查（**仅开机消耗，v1.10.51**）：
 * 无模式时优先消耗临界蒸汽纠缠奇点进入模式 2，其次蒸汽纠缠奇点进入模式 1；
 * 模式中倒计时耗尽时按当前模式对应物品无缝续杯，失败退出；关机/停机期间倒计时照走但不消耗。
 * 失控奇点节点（单 F 位，color "black"，attributeId -2=onlypull）参数随模式变化：
 * 模式 0 (6,0,1, fx10) / 模式 1 (8,0,2, fx15) / 模式 2 (12,0,4, fx20)（range, speed, damage, fxRadius）。
 *
 * 定向模式（螺丝刀切换，服务端）：矿池包含全部 25 槽（v1.10.55 起不再限制维度），插件槽可正常放入；
 * 定向矿石集合内的矿石被瞄准，抽取时跳过非定向矿石；每 tick 消耗 UU 物质
 * （率 = (1+矿石模式/时运加成) × 定向倍率 L/s，定向倍率 = 1 + 2500% ÷ 最低 3 个定向权重之和，权重和为 0 不可运行）；
 * 蒸汽倍率固定 +200% + 20%/额外维度槽 × 定向倍率；定向模式下失控奇点节点颜色变紫。
 *
 * 粒子（客户端，太阳能锅炉同款）：G 位（泥土位，54 个）机器工作即每 tick 1 个白色 cloud 粒子；
 * H 位（草方块位，36 个）机器工作即每 tick 2 个（恒满浓度，热量机制已删除）。工作标志经
 * getUpdateData/onValueUpdate 字节通道同步（bit0=工作，bit1-6 恒 63）。
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
    // 产出系数（奇点模式 0/1/2 → 0.5/2/5）
    private static final double[] SINGULARITY_OUTPUT_COEF = { 0.5d, 2.0d, 5.0d };
    // 单次产出基数：10 * GRADE_COEF[grade] * singCoef（热量恒满）
    private static final double ORES_PER_HEAT_UNIT = 10.0d;
    // 矿石模式蒸汽加成（v1.10.54：0 原矿 +0% / 1 粗矿 +100% / 2 粉碎矿 +200%）
    public static final double[] ORE_MODE_STEAM_BONUS = { 0.0d, 1.0d, 2.0d };
    // 时运档位集合（奇数 3-15 = III/V/VII/IX/XI/XIII/XV），索引=(档位值-3)/2
    public static final int[] FORTUNE_LEVELS = { 3, 5, 7, 9, 11, 13, 15 };
    // 时运档位蒸汽加成（按档位索引：III=+0% … XV=+300%）
    public static final double[] FORTUNE_STEAM_BONUS = { 0.0d, 0.5d, 1.0d, 1.5d, 2.0d, 2.5d, 3.0d };
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
    // 时运档位值 3-15 奇数，索引=(v-3)/2（III/V/VII 恒可用、IX/XI 需奇点模式、XIII/XV 需临界模式）
    public int mFortuneLevel = 3;
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
    // 插件槽 IInventory 适配器（惰性创建）
    private IInventory mPluginSlotInventory = null;

    // —— 蒸汽档位（checkProcessing 时缓存，供周期内每 tick 扣减与完成时产出）——
    private int mActiveGrade = -1;
    private boolean mActiveDense = false;
    // 产出实数累积（NBT 持久化）
    private double mOreAccumulator = 0.0d;

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
                return block == BlocksGTSR.runawaySingularity || block.isAir(world, x, y, z);
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

    /** 25 个维度槽（槽 1 = 控制器槽 mInventory[1]，槽 2-25 = 插件槽；v1.10.55 起定向模式不再限制维度，全部槽参与）。 */
    private List<ItemStack> getDimensionStacks() {
        List<ItemStack> stacks = new ArrayList<>();
        stacks.add(mInventory[1]);
        Collections.addAll(stacks, mPluginSlots);
        return stacks;
    }

    /** 收集当前 25 槽中去重后的维度缩写列表（保持槽位顺序）；v1.10.55 起定向模式不再限制维度（全部槽参与）。 */
    private List<String> collectDimensionAbbrs() {
        List<String> abbrs = new ArrayList<>();
        for (ItemStack stack : getDimensionStacks()) {
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

    /** 重建多维度矿池（无插件槽回退默认当前维度）。 */
    private void rebuildPool() {
        mPoolDirty = false;
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

    // —— 定向模式（定向抽取：全部维度槽矿池 + 定向矿石集合，UU 物质驱动；v1.10.55 不再限制维度）——

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

    /** 当前矿池内被定向矿石的权重和（定向模式可运行性判定用）。 */
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

    /** 定向内容最低 3 个权重之和（跨池逐 (矿,维) 条目收集后取最小 3 个，不足 3 个累加全部；无定向返回 0）。 */
    private double getDirectionalLowestWeightSum() {
        List<Float> weights = new ArrayList<>();
        for (PoolDim pd : mPool) {
            for (Map.Entry<GTUtility.ItemId, Float> entry : pd.dropMap.getInternalMap()
                .entrySet()) {
                if (mDirectionalOres.contains(entry.getKey())) weights.add(entry.getValue());
            }
        }
        if (weights.isEmpty()) return 0.0d;
        Collections.sort(weights);
        double sum = 0.0d;
        for (int i = 0; i < Math.min(3, weights.size()); i++) sum += weights.get(i);
        return sum;
    }

    /** 定向倍率 = 1 + 2500% ÷ 最低 3 个定向权重之和（权重和为 0 返回 0，表示不可运行）。 */
    public double getDirectionalFactor() {
        double sum = getDirectionalLowestWeightSum();
        return sum <= 0.0d ? 0.0d : 1.0d + 25.0d / sum;
    }

    /**
     * 消耗增加% 展示值（终端 UI「浏览器标题右侧」+X%）：定向模式 = 2500% ÷ 最低 3 个定向权重之和（v1.10.55 去掉 100% 基线）；
     * 筛选模式 = 权重和 + 5×k×(k-1)/2（v1.10.52 递增公式，倍率项 1+消耗增加/100）。
     */
    public double getWeightIncreasePercent() {
        if (mDirectionalMode) {
            double sum = getDirectionalLowestWeightSum();
            return sum <= 0.0d ? 0.0d : 2500.0d / sum;
        }
        return getFilterCostIncrease();
    }

    /** 维度槽消耗增加% 展示值（终端 UI「刷新按钮右侧」+X%）：定向模式 = 200% + 20%×额外维度槽数（v1.10.55 解除维度限制后按全部槽计数）；筛选模式 = 20% × 额外维度槽数。 */
    public double getDimensionIncreasePercent() {
        int slotCount = 0;
        for (ItemStack stack : getDimensionStacks()) {
            if (isDimensionDisplayItem(stack)) slotCount++;
        }
        int extra = Math.max(0, slotCount - 1);
        return mDirectionalMode ? 200.0d + 20.0d * extra : 20.0d * extra;
    }

    /** UU 倍率 = (1 + 矿石模式加成 + 时运加成) × 定向倍率。 */
    public double getUUMultiplier() {
        return (1.0d + ORE_MODE_STEAM_BONUS[Math.min(Math.max(mOreMode, 0), 2)]
            + FORTUNE_STEAM_BONUS[getFortuneIndex(mFortuneLevel)]) * getDirectionalFactor();
    }

    /** UU 消耗速率（L/s）：UU 基础 1 L/s × UU 倍率。 */
    public double getUURatePerSecond() {
        return 1.0d * getUUMultiplier();
    }

    /**
     * 切换定向模式（服务端入口，幂等）：v1.10.55 切换模式不再重置过滤/定向配置（独立持久化），仅强制刷新矿池；
     * 强制停机并清空奇点模式；时运即时钳位到奇点模式 0 的上限 7（III），与 checkProcessing 运行前钳位双保险。
     */
    public void toggleDirectionalMode(EntityPlayer aPlayer) {
        mDirectionalMode = !mDirectionalMode;
        forceRefreshPool();
        mMaxProgresstime = 0;
        mProgresstime = 0;
        mSingularityMode = 0;
        mSingularityModeTicks = 0;
        // 与 checkProcessing 运行前钳位双保险（此处按奇点模式 0 上限即时钳位）
        if (mFortuneLevel > 7) mFortuneLevel = 7;
        if (getBaseMetaTileEntity() != null) getBaseMetaTileEntity().markDirty();
        if (aPlayer != null) {
            GTSRMachineEvent.sendToPlayer(
                aPlayer,
                mDirectionalMode ? "gtsr.aggregator_config.directional.chat.on"
                    : "gtsr.aggregator_config.directional.chat.off");
        }
    }

    /** 清除当前模式配置表（终端「清除配置」按钮 C2S）：过滤模式清过滤表、定向模式清定向表；另一张表保留（两表独立持久化）。 */
    public void clearCurrentModeConfig() {
        if (mDirectionalMode) {
            mDirectionalOres.clear();
        } else {
            mFilteredOres.clear();
        }
        if (getBaseMetaTileEntity() != null) getBaseMetaTileEntity().markDirty();
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

    /** 终端插件槽（容量 24、栈上限 1、仅接受维度显示物品）的轻量 IInventory 适配器。 */
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

    /** 当前档位是否致密流体（服务端；checkProcessing 时缓存，供 GUI 蒸汽基准按致密/普通切换）。 */
    public boolean getActiveDense() {
        return mActiveDense;
    }

    // —— 主流程 ——

    @Override
    public CheckRecipeResult checkProcessing() {
        rebuildPoolIfNeeded();

        // 时运钳位已迁移至 onPostTick 每 tick 即时降档（v1.10.56：checkProcessing 仅周期开始时调用，
        // 运行中不触发——超限档位在 onPostTick 收敛，此处无需重复）

        if (!hasUsableDimension()) {
            return SimpleCheckRecipeResult.ofFailure("gtsr.gui.crust_matter_agg.no_dimension");
        }
        if (!dropMapValid) {
            return SimpleCheckRecipeResult.ofFailure("gtsr.gui.crust_matter_agg.no_ores");
        }
        if (mDirectionalMode && getDirectionalWeightSum() <= 0.0f) {
            return SimpleCheckRecipeResult.ofFailure("gtsr.gui.crust_matter_agg.no_direction");
        }
        if (mDirectionalMode && getUUMatterTotal() <= 0) {
            return SimpleCheckRecipeResult.ofFailure("gtsr.gui.crust_matter_agg.no_uumatter");
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
        // 产出 = 10 * GRADE_COEF[grade] * singCoef（热量机制已删除，恒满），实数累积，整数部分经过滤加权抽取输出
        mOreAccumulator += ORES_PER_HEAT_UNIT * GRADE_COEF[mActiveGrade] * SINGULARITY_OUTPUT_COEF[mSingularityMode];
        int out = (int) Math.floor(mOreAccumulator);
        for (int i = 0; i < out; i++) {
            GTUtility.ItemId oreId = extractNextOre();
            if (oreId == null) break;
            ItemStack oreStack = oreId.getItemStack();
            if (oreStack == null) break;
            // v1.10.61：voidingMode.protectItem=true 限流——矿石放不下时本周期停止抽取、accumulator 不扣，
            // 该矿石保留在累积状态，输出空间足后由下个周期继续输出（不再销毁矿石）
            if (!outputOre(oreStack)) break;
            mLastOreName = oreStack.getDisplayName();
            mOreAccumulator -= 1.0d;
        }
        updateSlots();
    }

    // —— 产出形态转换（粗矿 / 粉碎矿模式）——

    /** 挖掘掉落结果：模板物品 + 总数（含原版时运额外份数）。 */
    private static class MinedDrop {

        final ItemStack template;
        final int count;

        MinedDrop(ItemStack template, int count) {
            this.template = template;
            this.count = count;
        }
    }

    /**
     * 按挖掘掉落取粗矿：GT 矿 adapter fortune=0 基础（普通 1/rich 2）+ 原版时运公式额外份数；非 GT 矿 block.getDrops(fortune) 直通。解析失败返回
     * null（调用方回退原样输出）。
     */
    private MinedDrop mineCrudeDrops(ItemStack rawOre) {
        try {
            Item item = rawOre.getItem();
            if (item instanceof ItemBlock) {
                Block block = ((ItemBlock) item).field_150939_a;
                int meta = rawOre.getItemDamage();
                try (OreInfo<?> info = OreManager.getOreInfo(block, meta)) {
                    if (info != null) {
                        // GT 矿：force isNatural=true（GTNH 世界矿石 isNatural=false 会导致 adapter 以非自然形态处理），
                        // fortune=0 取基础掉落（普通 1 个 / rich 2 个），绕过 GTOreAdapter 内部 fortune>3 截断
                        boolean origNatural = info.isNatural;
                        info.isNatural = true;
                        List<ItemStack> drops;
                        try {
                            drops = OreManager.getAdapter(info)
                                .getOreDrops(ThreadLocalRandom.current(), info, false, 0);
                        } finally {
                            info.isNatural = origNatural;
                        }
                        if (drops == null || drops.isEmpty()) return null;
                        // 原版时运公式：额外 = max(0, nextInt(档位+2)-1)
                        int extra = Math.max(
                            0,
                            ThreadLocalRandom.current()
                                .nextInt(mFortuneLevel + 2) - 1);
                        return new MinedDrop(drops.get(0), drops.size() + extra);
                    }
                    // 非 GT 矿（vanilla/其他 mod）：GTMockWorld 挖掘掉落，fortune 直通原版公式
                    GTMockWorld mockWorld = new GTMockWorld();
                    mockWorld.clear();
                    mockWorld.setBlock(0, 0, 0, block, meta, 0);
                    List<ItemStack> drops = block.getDrops(mockWorld, 0, 0, 0, meta, mFortuneLevel);
                    if (drops == null || drops.isEmpty()) return null;
                    return new MinedDrop(drops.get(0), drops.get(0).stackSize);
                }
            }
        } catch (Throwable t) {
            GTSteamReborn.LOG.warn("[CrustMatterAggregator] 粗矿掉落解析失败，回退原矿输出", t);
        }
        return null;
    }

    /**
     * 按当前矿石模式输出单个原矿（dropMap 物品为矿石方块）：
     * 模式 0 原样输出 ×1（不吃时运）；
     * 模式 1 按挖掘掉落输出粗矿（GT 矿基础 1/2 个 + 原版时运公式额外份数，非 GT 矿 block.getDrops(fortune) 直通）；
     * 模式 2 按该粗矿的研磨机配方主产物数量 ×1.5 输出粉碎矿（红石/冰晶石等特殊矿自动得到实际数量，
     * 如红石 ×10→×15、普通矿 ×2→×3）；已是加工形态不转换；无配方回退 crushed×2（=×3 现状）；目标形态不存在回退原样输出 ×1。
     *
     * @return v1.10.61：该矿石是否已实际放入输出总线（protectItem=false 销毁模式恒 true；
     *         protectItem=true 限流模式整组放不下返回 false，调用方不扣 mOreAccumulator）
     */
    private boolean outputOre(ItemStack rawOre) {
        ItemStack out;
        if (mOreMode == 0) {
            out = rawOre;
        } else {
            MinedDrop mined = mineCrudeDrops(rawOre);
            if (mined == null) {
                out = rawOre;
            } else if (mOreMode == 1) {
                out = GTUtility.copyAmount(mined.count, mined.template);
            } else {
                // 模式 2：已是加工形态的掉落不再转换（镜像采矿节点 skip 语义）
                if (OreCrushedUtil.isProcessedForm(mined.template)) {
                    out = GTUtility.copyAmount(mined.count, mined.template);
                } else {
                    ItemStack product = OreCrushedUtil.getCrushedProduct(mined.template);
                    int perCrude = 2; // 无配方回退默认 2（×1.5 = 3，保持 v1.10.5x 现状）
                    if (product == null) {
                        Materials material = getOreMaterial(mined.template);
                        product = material == null ? null : GTOreDictUnificator.get(OrePrefixes.crushed, material, 1);
                    } else {
                        perCrude = product.stackSize;
                    }
                    if (product == null) {
                        out = rawOre;
                    } else {
                        product.stackSize = (int) Math.round(mined.count * perCrude * 1.5d);
                        out = product;
                    }
                }
            }
        }
        // v1.10.61：voidingMode.protectItem=true（限流）试放——整组放得下才放入，放不下不放入不扣累积；
        // false（允许销毁）保持原 addOutputPartial 语义（放多少算多少，放不下即销毁）
        if (!voidingMode.protectItem) {
            addOutputPartial(out);
            return true;
        }
        return tryOutputOre(out);
    }

    /**
     * v1.10.61：试放单组矿石到输出总线（voidingMode.protectItem=true 限流）：
     * 依次对每个输出总线做 storePartial 模拟探测（storePartial 模拟模式同样会扣减入参 stackSize，
     * 故用副本探测），整组放得下才实放并返回 true；所有总线都放不下时整组不放入、返回 false——
     * 矿石保留在 mOreAccumulator 累积状态，下次周期继续尝试，不销毁。
     * v1.10.74：探测与实放走 GTSROutputBusCompat——ME 输出总线 cache 满时过滤放行视为可放（探测与实放一致）。
     */
    private boolean tryOutputOre(ItemStack ore) {
        if (GTUtility.isStackInvalid(ore)) return false;
        for (MTEHatchOutputBus bus : GTUtility.validMTEList(mOutputBusses)) {
            if (GTSROutputBusCompat.storePartial(bus, GTUtility.copyOrNull(ore), true)) {
                GTSROutputBusCompat.storePartial(bus, ore, false);
                return true;
            }
        }
        return false;
    }

    /** 取物品的 GT 材料（非 GT 物品返回 null）。 */
    private Materials getOreMaterial(ItemStack stack) {
        ItemData data = GTOreDictUnificator.getItemData(stack);
        if (data == null || data.mMaterial == null || data.mMaterial.mMaterial == null) return null;
        return data.mMaterial.mMaterial;
    }

    /** 周期内每 tick 从输入仓扣减当前档位蒸汽；不足返回 false（周期停止）。 */
    private boolean depleteSteamForTick() {
        if (mActiveGrade < 0) return false;
        int basePerTick = mActiveDense ? DENSE_STEAM_PER_TICK : NORMAL_STEAM_PER_TICK;
        // 蒸汽倍率：矿石模式/时运/维度槽/筛选（定向含固定+200%与定向倍率）加成（向上取整）
        int remaining = (int) Math.ceil(basePerTick * getSteamMultiplier());
        // 基类 gradeProbeStacks 为 private，此处自行构造当前档位流体请求（致密档只扣致密流体，普通档只扣普通流体）
        FluidStack request = FluidRegistry
            .getFluidStack((mActiveDense ? DENSE_FLUID_NAMES : NORMAL_FLUID_NAMES)[mActiveGrade], 1);
        if (request == null) return false;
        for (MTEHatch hatch : getSteamInputHatches()) {
            if (remaining <= 0) break;
            // v1.10.55：探测走 getTankInfo（beta-1 MTEHatchInputME.drain 忽略 doDrain，模拟即真实提取——getTankInfo 双版本安全）
            int available = 0;
            FluidTankInfo[] tanks = hatch.getTankInfo(ForgeDirection.UNKNOWN);
            if (tanks != null) {
                for (FluidTankInfo tank : tanks) {
                    if (tank != null && tank.fluid != null && tank.fluid.isFluidEqual(request))
                        available += tank.fluid.amount;
                }
            }
            if (available <= 0) continue;
            int toDrain = Math.min(remaining, available);
            FluidStack drainReq = request.copy();
            drainReq.amount = toDrain;
            FluidStack drained = hatch.drain(ForgeDirection.UNKNOWN, drainReq, true);
            remaining -= drained == null ? 0 : drained.amount;
        }
        // v1.10.55：删除样板仓（dual）扣减路径——普通 ME 输入仓经主仓循环 3 参 drain 实扣已足够；
        // CraftingInputME 窗口外 getAllFluids 返回 pattern 副本，扣减静默失效（免费流体 bug，v1.10.8 物品版同型）
        return remaining <= 0;
    }

    /**
     * 定向模式周期内每 tick 扣减 UU 物质（率 = getUURatePerSecond()/20 L/tick，实数累积取整扣减，
     * 主流体输入仓扣减，仿 depleteSteamForTick）；扣不足返回 false（周期停止）。
     */
    private boolean depleteUUMatterForTick() {
        mUuAccumulator += getUURatePerSecond() / 20.0d;
        int toDrain = (int) Math.floor(mUuAccumulator);
        if (toDrain <= 0) return true;
        FluidStack request = getUUMatterRequest(1);
        if (request == null) return false;
        int remaining = toDrain;
        for (MTEHatch hatch : getSteamInputHatches()) {
            if (remaining <= 0) break;
            // v1.10.55：探测走 getTankInfo（beta-1 MTEHatchInputME.drain 忽略 doDrain，模拟即真实提取——getTankInfo 双版本安全）
            int available = 0;
            FluidTankInfo[] tanks = hatch.getTankInfo(ForgeDirection.UNKNOWN);
            if (tanks != null) {
                for (FluidTankInfo tank : tanks) {
                    if (tank != null && tank.fluid != null && tank.fluid.isFluidEqual(request))
                        available += tank.fluid.amount;
                }
            }
            if (available <= 0) continue;
            int toTake = Math.min(remaining, available);
            FluidStack drainReq = request.copy();
            drainReq.amount = toTake;
            FluidStack drained = hatch.drain(ForgeDirection.UNKNOWN, drainReq, true);
            remaining -= drained == null ? 0 : drained.amount;
        }
        // 已成功扣减部分从累积中扣除，未扣足部分保留待下次运行（仅记录真实欠账）
        mUuAccumulator -= (toDrain - remaining);
        return remaining <= 0;
    }

    /** UU 物质流体请求（v1.10.55 双名兼容：GTNH 注册名 ic2uumatter 优先，回退旧名 uumatter）。 */
    private static FluidStack getUUMatterRequest(int amount) {
        FluidStack req = FluidRegistry.getFluidStack("ic2uumatter", amount);
        return req != null ? req : FluidRegistry.getFluidStack("uumatter", amount);
    }

    /**
     * 定向模式 UU 物质总存量（输入仓 getTankInfo 只读求和，v1.10.55 不再读样板仓）。
     * 存量 ≤ 0 时 checkProcessing 拒绝启动并显示 no_uumatter 键。
     */
    private int getUUMatterTotal() {
        FluidStack request = getUUMatterRequest(1);
        if (request == null) return 0;
        int total = 0;
        for (MTEHatch hatch : getSteamInputHatches()) {
            FluidTankInfo[] tanks = hatch.getTankInfo(ForgeDirection.UNKNOWN);
            if (tanks == null) continue;
            for (FluidTankInfo tank : tanks) {
                if (tank != null && tank.fluid != null && tank.fluid.amount > 0 && tank.fluid.isFluidEqual(request)) {
                    total += tank.fluid.amount;
                }
            }
        }
        return total;
    }

    // —— 矿石模式 / 时运（终端 UI 调用，服务端执行）——

    /** 循环矿石模式 0(原矿)→1(粗矿)→2(粉碎矿)→0；切回原矿模式时清时运回默认 III。 */
    public void cycleOreMode() {
        mOreMode = (mOreMode + 1) % 3;
        if (mOreMode == 0) mFortuneLevel = 3;
        if (getBaseMetaTileEntity() != null) getBaseMetaTileEntity().markDirty();
    }

    /** 循环时运档位：在当前 7 档位集合内循环（III→V→…→XV→III）；原矿模式直接回默认 III。 */
    public void cycleFortuneLevel() {
        if (mOreMode == 0) {
            mFortuneLevel = 3;
        } else {
            int idx = getFortuneIndex(mFortuneLevel);
            mFortuneLevel = FORTUNE_LEVELS[(idx + 1) % 7];
        }
        if (getBaseMetaTileEntity() != null) getBaseMetaTileEntity().markDirty();
    }

    /** 当前奇点模式允许的时运上限：模式 0/1/2 → 7/11/15（III/V/VII 恒可用、IX/XI 需奇点模式、XIII/XV 需临界模式）；超限档位由 checkProcessing 运行前钳位兜底。 */
    public int getMaxAllowedFortuneLevel() {
        switch (mSingularityMode) {
            case 2:
                return 15;
            case 1:
                return 11;
            default:
                return 7;
        }
    }

    /** 时运档位值 → 档位索引（档位值 3-15 奇数，索引=(v-3)/2；越界钳位到 0..6）。 */
    private static int getFortuneIndex(int level) {
        return Math.min(Math.max((level - 3) / 2, 0), 6);
    }

    // —— 蒸汽倍率 ——

    /**
     * 筛选模式消耗增加%（v1.10.52）：= 被过滤矿石权重和 + 5×k×(k-1)/2
     * （k = 被过滤矿数；第 n 个被过滤矿的递增项 = 5×(n-1)，即第 1 个 +0、第 2 个 +5、第 3 个 +10 ……）。
     */
    public double getFilterCostIncrease() {
        int k = mFilteredOres.size();
        return getFilteredWeightSum() + 5.0d * k * (k - 1) / 2.0d;
    }

    /**
     * 蒸汽消耗倍率 = (1+矿石模式加成+时运加成) × (1+0.2×(维度物品槽数-1)) × (1+筛选消耗增加/100)。
     * 定向模式：固定 +200% + 20%/额外维度槽 × 定向倍率（v1.10.55 解除维度限制后按全部槽计数；UU 不吃维度项）。
     * depleteSteamForTick 按 basePerTick × 该倍率向上取整扣减。
     */
    public double getSteamMultiplier() {
        double modeBonus = ORE_MODE_STEAM_BONUS[Math.min(Math.max(mOreMode, 0), 2)];
        double fortuneBonus = FORTUNE_STEAM_BONUS[getFortuneIndex(mFortuneLevel)];
        if (mDirectionalMode) {
            int slotCount = 0;
            for (ItemStack stack : getDimensionStacks()) {
                if (isDimensionDisplayItem(stack)) slotCount++;
            }
            // 维度加成 = 固定 +200% + 20%/额外维度槽（v1.10.55 解除维度限制后按全部槽计数；UU 不吃维度项）
            return (1.0d + modeBonus + fortuneBonus) * (3.0d + SLOT_STEAM_PER_EXTRA * Math.max(0, slotCount - 1))
                * getDirectionalFactor();
        }
        int slotCount = 0;
        for (ItemStack stack : getDimensionStacks()) {
            if (isDimensionDisplayItem(stack)) slotCount++;
        }
        return (1.0d + modeBonus + fortuneBonus) * (1.0d + SLOT_STEAM_PER_EXTRA * Math.max(0, slotCount - 1))
            * (1.0d + getFilterCostIncrease() / 100.0d);
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (aBaseMetaTileEntity.isClientSide()) {
            spawnParticles(aBaseMetaTileEntity);
            return;
        }
        // 时运钳位（即时，v1.10.56）：超过奇点模式上限（无奇点7/奇点11/临界15）的档位每 tick 自动降档——
        // checkProcessing 仅周期开始时调用（运行中不触发），奇点模式到期/降级也由此立即兜底；
        // 终端按钮恒可轮切全 7 档，超限档位在这里收敛到上限（仅在变化时 markDirty）
        if (mFortuneLevel > getMaxAllowedFortuneLevel()) {
            mFortuneLevel = getMaxAllowedFortuneLevel();
            aBaseMetaTileEntity.markDirty();
        }
        if (mMachine) {
            // 奇点模式倒计时与每 20 tick 检查（进入/无缝续杯）已统一由父类 MTESingularityModeMachineBase.onPostTick 处理
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
    }

    // —— 奇点模式（巨型蒸汽轮机式，非右键式；进入/续杯/退出主流程已统一由父类 MTESingularityModeMachineBase 处理）——

    /** 奇点模式到期/回落后回调：时运上限降为 7（III），超限时钳位（原 checkSingularityMode 退出分支逻辑）。 */
    @Override
    protected void onSingularityModeExpired() {
        if (mFortuneLevel > 7) mFortuneLevel = 7;
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

    /** G 位固定 1 个/tick（工作即喷）；H 位工作即 2 个/tick（恒满浓度，热量机制已删除）。 */
    private void spawnParticles(IGregTechTileEntity aBaseMetaTileEntity) {
        World world = aBaseMetaTileEntity.getWorld();
        if (mWorkingForFX) {
            spawnOneParticle(world, aBaseMetaTileEntity, getParticleOffsetsG());
            for (int i = 0; i < 2; i++) {
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

    // —— 字节通道：bit0=工作（粒子 G/H 位开关），bit1-6 恒 63（热量机制已删除）——

    @Override
    public void onValueUpdate(byte aValue) {
        mWorkingForFX = (aValue & 0x01) != 0;
    }

    @Override
    public byte getUpdateData() {
        boolean working = mMachine && mMaxProgresstime > 0
            && getBaseMetaTileEntity() != null
            && getBaseMetaTileEntity().isAllowedToWork();
        // 热量机制已删除：bit1-6 恒置满（63）
        return (byte) ((63 << 1) | (working ? 0x01 : 0x00));
    }

    // —— NBT ——

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
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
        // v1.10.55：过滤/定向配置独立持久化（两个表，ItemId → ItemStack NBT 中转重建）
        aNBT.setTag("mFilteredOres", writeOreIdList(mFilteredOres));
        // 定向模式（开关 + 定向矿石集合）
        aNBT.setBoolean("mDirectionalMode", mDirectionalMode);
        aNBT.setTag("mDirectionalOres", writeOreIdList(mDirectionalOres));
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mOreAccumulator = aNBT.getDouble("oreAccumulator");
        lastDimAbbr = aNBT.getString("lastDimAbbr");
        mLastOreName = aNBT.getString("mLastOreName");
        dropMapValid = aNBT.getBoolean("dropMapValid");
        mCurrentDimId = aNBT.getInteger("mCurrentDimId");
        mOreMode = aNBT.getInteger("mOreMode");
        mFortuneLevel = aNBT.getInteger("mFortuneLevel");
        if (mOreMode < 0 || mOreMode > 2) mOreMode = 0;
        // 时运档位校验：仅接受 FORTUNE_LEVELS 集合内的档位值（3-15 奇数），非法值回默认 III
        if (mFortuneLevel < 3 || mFortuneLevel > 15 || mFortuneLevel % 2 == 0) mFortuneLevel = 3;
        NBTTagList pluginSlots = aNBT.getTagList("mPluginSlots", 10);
        for (int i = 0; i < pluginSlots.tagCount() && i < mPluginSlots.length; i++) {
            mPluginSlots[i] = ItemStack.loadItemStackFromNBT(pluginSlots.getCompoundTagAt(i));
        }
        readOreIdList(aNBT.getTagList("mFilteredOres", 10), mFilteredOres);
        // 定向模式开关与定向矿石集合
        mDirectionalMode = aNBT.getBoolean("mDirectionalMode");
        readOreIdList(aNBT.getTagList("mDirectionalOres", 10), mDirectionalOres);
        // 槽位与 mCurrentDimId 均已就绪（基类 Inventory 先于 loadNBTData 载入），重建矿池
        rebuildPool();
    }

    /** 矿石 ID 集合 → NBT 列表（ItemStack 中转，ItemId 无 readFromNBT 工厂）。 */
    private static NBTTagList writeOreIdList(Set<GTUtility.ItemId> ores) {
        NBTTagList list = new NBTTagList();
        for (GTUtility.ItemId id : ores) {
            if (id == null) continue;
            ItemStack stack = id.getItemStack(1);
            if (stack != null) list.appendTag(stack.writeToNBT(new NBTTagCompound()));
        }
        return list;
    }

    /** NBT 列表 → 矿石 ID 集合（先清空目标集再填充；旧档无键时 getTagList 返回空列表，安全）。 */
    private static void readOreIdList(NBTTagList list, Set<GTUtility.ItemId> target) {
        target.clear();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            ItemStack stack = ItemStack.loadItemStackFromNBT(tag);
            if (stack != null) {
                // 新格式（v1.10.55，ItemStack 中转，保留 NBT）
                GTUtility.ItemId id = GTUtility.ItemId.create(stack);
                if (id != null) target.add(id);
                continue;
            }
            // 旧格式回退（v1.10.54 及更早存档：注册名 "modid:name" + meta，无 NBT）
            Item item = findItemByName(tag.getString("item"));
            if (item != null) target.add(GTUtility.ItemId.createNoCopy(item, tag.getShort("meta"), null));
        }
    }

    /** 按 "modid:name" 解析物品注册名（无法解析返回 null；旧格式 NBT 回退用）。 */
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
            .addInfo(EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.tooltip.crust_matter_agg.desc4_2"))
            .addInfo(EnumChatFormatting.DARK_PURPLE + StatCollector.translateToLocal(keyPrefix + "desc5"))
            .addInfo(
                EnumChatFormatting.DARK_PURPLE
                    + StatCollector.translateToLocal("gtsr.tooltip.crust_matter_agg.desc5_2"))
            .addInfo(EnumChatFormatting.LIGHT_PURPLE + StatCollector.translateToLocal(keyPrefix + "desc8"))
            .addInfo(
                EnumChatFormatting.LIGHT_PURPLE
                    + StatCollector.translateToLocal("gtsr.tooltip.crust_matter_agg.desc8_2"))
            .addInfo(
                EnumChatFormatting.LIGHT_PURPLE
                    + StatCollector.translateToLocal("gtsr.tooltip.crust_matter_agg.desc8_3"))
            .addInfo(EnumChatFormatting.LIGHT_PURPLE + StatCollector.translateToLocal(keyPrefix + "desc9"))
            .addInfo(
                EnumChatFormatting.LIGHT_PURPLE
                    + StatCollector.translateToLocal("gtsr.tooltip.crust_matter_agg.desc9_2"))
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
                StatCollector.translateToLocal("gtsr.tooltip.crust_matter_agg.steam_input_hatch"),
                StatCollector.translateToLocal(keyPrefix + "steam_input"),
                1)
            .addInputBus(StatCollector.translateToLocal(keyPrefix + "input_bus"), 1)
            .addOutputBus(StatCollector.translateToLocal(keyPrefix + "output_bus"), 1)
            .addStructureInfo("")
            .addStructureInfo(EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal(keyPrefix + "desc6"))
            .addStructureInfo(EnumChatFormatting.DARK_PURPLE + StatCollector.translateToLocal(keyPrefix + "desc7"))
            .addStructureHint("gtsr.tooltip.shared.no_maintenance")
            .addStructureHint(keyPrefix + "hint_dimension")
            .addInfo(GTSRUtils.getAddedByLine())
            .toolTipFinisher();
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

    /** 螺丝刀右击：切换定向模式（服务端；幂等；不清空过滤/定向配置，仅强制刷新矿池并重置奇点模式，见 toggleDirectionalMode）。 */
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
