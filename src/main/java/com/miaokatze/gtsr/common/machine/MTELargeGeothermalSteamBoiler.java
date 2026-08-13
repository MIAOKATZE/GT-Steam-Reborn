package com.miaokatze.gtsr.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.isAir;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlocksTiered;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static com.miaokatze.gtsr.common.api.enums.GTSRHatchElement.SteamOutputBus;
import static gregtech.api.enums.HatchElement.InputHatch;
import static gregtech.api.enums.HatchElement.OutputHatch;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;
import static gregtech.api.util.GTStructureUtility.ofAnyWater;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.ImmutableList;
import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;
import com.gtnewhorizon.structurelib.alignment.IAlignmentLimits;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.util.Vec3Impl;
import com.gtnewhorizons.modularui.common.widget.DynamicPositionedColumn;
import com.gtnewhorizons.modularui.common.widget.FakeSyncWidget;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;
import com.gtnewhorizons.modularui.common.widget.TextWidget;
import com.miaokatze.gtsr.api.IShiftRightClickDecalcifiable;
import com.miaokatze.gtsr.api.compat.GTSRHatchFluidAccess;
import com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps;
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.event.GTSRMachineEvent;
import com.miaokatze.gtsr.common.gui.MTELargeGeothermalSteamBoilerGui;
import com.miaokatze.gtsr.common.machine.base.MTEGTSRMultiBlockBase;
import com.miaokatze.gtsr.common.machine.base.MTEPressureSteamOutputHatch;
import com.miaokatze.gtsr.common.machine.base.MTESteamOutputHatch;
import com.miaokatze.gtsr.common.util.GTSRUtils;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IHatchElement;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrorRegistry;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTStructureUtility;
import gregtech.api.util.IGTHatchAdder;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.shutdown.ShutDownReasonRegistry;
import gregtech.common.blocks.BlockCasings1;
import gregtech.common.blocks.BlockCasings2;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;
import gregtech.common.tileentities.machines.IDualInputHatch;

public class MTELargeGeothermalSteamBoiler extends MTEGTSRMultiBlockBase<MTELargeGeothermalSteamBoiler>
    implements IConstructable, ISurvivalConstructable, IShiftRightClickDecalcifiable {

    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static final int HORIZONTAL_OFF_SET = 3;
    private static final int VERTICAL_OFF_SET = 6;
    private static final int DEPTH_OFF_SET = 1;

    // GTUDK 导出结构（plan/大型地热锅炉等级2.java）：8 层 × 7 行 × 7 列；'~' 控制器在 (列 3, 层 6, 行 1)；
    // b=slice（层，顶→底）、c=row（深度线，前面第一）、a=char（列，自左向右）；
    // 字母语义：A=外壳+仓口、B=齿轮箱、C=管道、D=燃烧室、E=框架、F=泥土位（注水）、G=草方块位（粒子）、'-'=空气
    private static final String[][] SHAPE_MAIN = {
        { "       ", " EAAAE ", " AGGGA ", " AGGGA ", " AGGGA ", " EAAAE ", "       " },
        { "       ", " EAAAE ", " AEEEA ", " AEEEA ", " AEEEA ", " EAAAE ", "       " },
        { "       ", " EAAAE ", " A---A ", " A---A ", " A---A ", " EAAAE ", "       " },
        { "       ", " EAAAE ", " A---A ", " A---A ", " A---A ", " EAAAE ", "       " },
        { "       ", " ECCCE ", " C---C ", " C---C ", " C---C ", " ECCCE ", "       " },
        { " E   E ", "EBAAABE", " AFFFA ", " AFFFA ", " AFFFA ", "EBAAABE", " E   E " },
        { " E   E ", "EBA~ABE", " AFFFA ", " AFFFA ", " AFFFA ", "EBAAABE", " E   E " },
        { " E   E ", "EBDDDBE", " DDDDD ", " DDDDD ", " DDDDD ", "EBDDDBE", " E   E " } };

    private static IStructureDefinition<MTELargeGeothermalSteamBoiler> STRUCTURE_DEFINITION = null;
    // G 草方块位粒子形状偏移缓存（9 个，惰性扫描 SHAPE_MAIN，勿硬编码）
    private static List<int[]> mParticleOffsets = null;
    private static final NumberFormat numberFormat = NumberFormat.getNumberInstance();

    static {
        numberFormat.setMinimumFractionDigits(3);
        numberFormat.setMaximumFractionDigits(3);
    }

    public int mSetTier = -1;
    protected boolean needsWaterFill = false;
    protected int mCasingCount = 0;
    public double mHeat = 0.0d;
    public int mCurrentSteamOutput = 0;
    protected int mStructureGraceTicks = 100;

    // 升温速率整体 ×2（青铜 0.012%/tick、钢 0.006%/tick）；超压模式下再 ×0.2
    private static final double HEAT_UP_BRONZE = 0.00012d;
    private static final double HEAT_UP_STEEL = 0.00006d;
    private static final double HEAT_DOWN = 0.002d;
    // 过热芯片升温速率：0.004%/tick（设计值，慢于钢/青铜，换取超热蒸汽产出）
    // v1.8.8 曾误改为 0.01%/tick 试图修 5% 掉热，真正根因是配方重启 1tick 空窗（v1.8.10 已修）
    private static final double HEAT_UP_CHIP = 0.00004d;

    private static final int MAX_OUTPUT_BRONZE = 60_000;
    private static final int MAX_OUTPUT_STEEL = 150_000;

    private static final int BASE_RECIPE_TIME = 60;
    private static final int HEATED_RECIPE_TIME_BRONZE = 200;
    private static final int HEATED_RECIPE_TIME_STEEL = 100;
    private static final int OVERHEAT_CHIP_RECIPE_TIME = 80;

    private static final int LAVA_PER_RECIPE = 1000;

    // 钙化延迟为 1 小时；满垢后产出降至 1%，并每 10 分钟向所有者发送提醒
    private static final int STEAM_PER_WATER = 160;
    private static final long CALCIFICATION_DELAY_TICKS = 72_000L;
    private static final long CALCIFICATION_WARN_INTERVAL_TICKS = 600L * 20;
    private long mCalcificationWarnTimer = 0L;

    public double mCalcification = 0.0d;
    public long mRunningTicks = 0L;

    // 超压模式：热量可越过 1.0 升至 2.0，产出/钙化加速；螺丝刀切换，热量跌破 1.0 自动退出
    public boolean mOverpressure = false;
    // 缺水停机标志：水/蒸馏水耗尽时置 true 阻止蒸汽产出（含停机冷却期），玩家重新开机（GUI 电源开关/软锤）时清除
    private boolean mWaterStop = false;
    // 缺水提示已发送（NBT 持久）：每次停机周期只提示一次，重新开机时清除
    private boolean mNoWaterNotified = false;

    private final ArrayList<MTESteamOutputHatch> mSteamOutputHatches = new ArrayList<>();
    private final ArrayList<MTEPressureSteamOutputHatch> mPressureSteamOutputHatches = new ArrayList<>();

    /**
     * Local hatch element for the geothermal steam output hatches.
     * <p>
     * Wraps the custom adder that dispatches accepted hatches into the appropriate internal
     * lists. {@code mteBlacklist()} excludes the hatch classes from the StructureLib NEI hatch
     * item filter, so the preview does not render hatches on every valid casing position.
     */
    private enum GeothermalSteamOutputHatchElement implements IHatchElement<MTELargeGeothermalSteamBoiler> {

        SteamOutput(MTELargeGeothermalSteamBoiler::addSteamOutputToMachineList, MTESteamOutputHatch.class,
            MTEPressureSteamOutputHatch.class) {

            @Override
            public List<Class<? extends IMetaTileEntity>> mteBlacklist() {
                return ImmutableList.of(MTESteamOutputHatch.class, MTEPressureSteamOutputHatch.class);
            }
        };

        private final List<Class<? extends IMetaTileEntity>> mteClasses;
        private final IGTHatchAdder<MTELargeGeothermalSteamBoiler> adder;

        @SafeVarargs
        GeothermalSteamOutputHatchElement(IGTHatchAdder<MTELargeGeothermalSteamBoiler> adder,
            Class<? extends IMetaTileEntity>... classes) {
            this.mteClasses = Collections.unmodifiableList(Arrays.asList(classes));
            this.adder = adder;
        }

        @Override
        public List<? extends Class<? extends IMetaTileEntity>> mteClasses() {
            return mteClasses;
        }

        @Override
        public IGTHatchAdder<? super MTELargeGeothermalSteamBoiler> adder() {
            return adder;
        }

        @Override
        public long count(MTELargeGeothermalSteamBoiler t) {
            return t.mSteamOutputHatches.size() + t.mPressureSteamOutputHatches.size();
        }
    }

    public MTELargeGeothermalSteamBoiler(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        registerProgressEntries();
    }

    public MTELargeGeothermalSteamBoiler(String aName) {
        super(aName);
        registerProgressEntries();
    }

    // GTSR 进度词条：注册顺序 = GUI 终端显示顺序（热量/结垢/蒸汽输出，芯片警告行为文本行保留在 GUI）
    private void registerProgressEntries() {
        registerEntry(
            "temperature",
            "gtsr.gui.geothermal_boiler.heat",
            "%.3f%%",
            EnumChatFormatting.GOLD,
            () -> mHeat * 100.0d);
        registerEntry(
            "calcification",
            "gtsr.gui.geothermal_boiler.calcification",
            "%.3f%%",
            EnumChatFormatting.RED,
            () -> mCalcification * 100.0d);
        // 蒸汽输出行末尾 (蒸汽)/(超热蒸汽) 后缀：formatter 内读机器字段判定
        registerEntryCustom(
            "steam_output",
            "gtsr.gui.geothermal_boiler.steam_output",
            EnumChatFormatting.AQUA,
            () -> mCurrentSteamOutput,
            v -> NumberFormatUtil.formatNumber((long) v) + " L/s "
                + EnumChatFormatting.WHITE
                + (hasOverheatChip() ? StatCollector.translateToLocal("gtsr.gui.geothermal_boiler.superheated")
                    : StatCollector.translateToLocal("gtsr.gui.geothermal_boiler.steam")));
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTELargeGeothermalSteamBoiler(mName);
    }

    public String getMachineType() {
        return "Geothermal Boiler";
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return GTSRRecipeMaps.geothermalSteamBoilerRecipes;
    }

    @Override
    public boolean getDefaultHasMaintenanceChecks() {
        return false;
    }

    @Override
    public boolean shouldDisplayCheckRecipeResult() {
        return false;
    }

    @Override
    public boolean showRecipeTextInGUI() {
        return true;
    }

    @Nullable
    public static Integer getCasingTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockCasings1 && meta == 10) return 1;
        if (block == GregTechAPI.sBlockCasings2 && meta == 0) return 2;
        return null;
    }

    @Nullable
    public static Integer getFrameTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockFrames && meta == Materials.Bronze.mMetaItemSubID) return 1;
        if (block == GregTechAPI.sBlockFrames && meta == Materials.Steel.mMetaItemSubID) return 2;
        return null;
    }

    // v1.10.62：齿轮箱/管道/火箱 tier 提取器——配合 ofBlocksTiered 强制同元素同 tier（等级2 全钢，
    // 修复原 ofChain 双分支 + max setter 使青铜混入钢结构也能成型的问题）
    @Nullable
    public static Integer getGearTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockCasings2 && meta == 2) return 1;
        if (block == GregTechAPI.sBlockCasings2 && meta == 3) return 2;
        return null;
    }

    @Nullable
    public static Integer getPipeTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockCasings2 && meta == 12) return 1;
        if (block == GregTechAPI.sBlockCasings2 && meta == 13) return 2;
        return null;
    }

    @Nullable
    public static Integer getFireboxTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockCasings3 && meta == 13) return 1;
        if (block == GregTechAPI.sBlockCasings3 && meta == 14) return 2;
        return null;
    }

    protected int getCasingTextureID() {
        if (mSetTier == 2) {
            return ((BlockCasings2) GregTechAPI.sBlockCasings2).getTextureIndex(0);
        }
        return ((BlockCasings1) GregTechAPI.sBlockCasings1).getTextureIndex(10);
    }

    protected void updateHatchTexture() {
        int textureID = getCasingTextureID();
        // v1.9.41 修复：补充 mOutputHatches——v1.9.40 开放 OutputHatch 位后，ME 输出仓/普通输出仓
        // 注册到 mOutputHatches，此前仅被结构 adder 的 bronzeCasingIndex 初始化，等级2 重刷遗漏
        // 导致底材停留在青铜材质。
        for (MTEHatch h : mInputHatches) h.updateTexture(textureID);
        for (MTEHatch h : mOutputHatches) h.updateTexture(textureID);
        for (MTEHatch h : mOutputBusses) h.updateTexture(textureID);
        for (MTEHatch h : mSteamOutputHatches) h.updateTexture(textureID);
        for (MTEHatch h : mPressureSteamOutputHatches) h.updateTexture(textureID);
        // v1.10.6：样板仓（mDualInputHatches）纹理更新（InputHatch 元素可接受样板仓）
        for (IDualInputHatch dual : mDualInputHatches) {
            if (dual != null) dual.updateTexture(textureID);
        }
    }

    @Override
    public void onValueUpdate(byte aValue) {
        // 打包编码（太阳能阵列同款）：GT 事件通道会剥掉 bit7，故 mHeat 用 bit3-6 共 4 bit（精度 1/15≈6.7%），
        // 编码范围 0~2.0（超压模式热量上限）；mSetTier 1~2 占 bit1-2（0 表示未定级，避免 -1 补码坑），bit0 为运行标志
        int encodedTier = (aValue >> 1) & 0x03;
        mSetTier = encodedTier == 0 ? -1 : encodedTier;
        mHeat = ((aValue >> 3) & 0x0F) / 15.0d * 2.0d;
    }

    @Override
    public byte getUpdateData() {
        // 热量 0~2.0（超压模式）量化到 4bit 0~15
        int heatQuantized = (int) Math.round(mHeat / 2.0d * 15.0);
        if (heatQuantized < 0) heatQuantized = 0;
        if (heatQuantized > 15) heatQuantized = 15;
        int encodedTier = mSetTier <= 0 ? 0 : mSetTier;
        boolean running = mMaxProgresstime > 0 || mHeat > 0.01d;
        return (byte) ((heatQuantized << 3) | (encodedTier << 1) | (running ? 0x01 : 0x00));
    }

    @Override
    public IStructureDefinition<MTELargeGeothermalSteamBoiler> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            final int bronzeCasingIndex = ((BlockCasings1) GregTechAPI.sBlockCasings1).getTextureIndex(10);

            STRUCTURE_DEFINITION = StructureDefinition.<MTELargeGeothermalSteamBoiler>builder()
                .addShape(STRUCTURE_PIECE_MAIN, transpose(SHAPE_MAIN))
                .addElement(
                    'A',
                    ofChain(
                        // casing-first: NEI 投影优先渲染外壳；真实 hatch 坐标上 casing 匹配失败后继续匹配 hatch adder。
                        onElementPass(
                            MTELargeGeothermalSteamBoiler::onCasingAdded,
                            ofBlocksTiered(
                                MTELargeGeothermalSteamBoiler::getCasingTier,
                                ImmutableList.of(
                                    Pair.of(GregTechAPI.sBlockCasings1, 10),
                                    Pair.of(GregTechAPI.sBlockCasings2, 0)),
                                -1,
                                (MTELargeGeothermalSteamBoiler t, Integer tier) -> {
                                    if (tier > t.mSetTier) t.mSetTier = tier;
                                },
                                (MTELargeGeothermalSteamBoiler t) -> t.mSetTier)),
                        buildHatchAdder(MTELargeGeothermalSteamBoiler.class).atLeast(InputHatch)
                            .casingIndex(bronzeCasingIndex)
                            .hint(1)
                            .build(),
                        // Use atLeast(GeothermalSteamOutputHatchElement.SteamOutput) instead of hatchIds(...).
                        // Its mteBlacklist() excludes the steam output hatch classes so NEI does not render them on
                        // casing positions.
                        // v1.9.40 修复：移除 shouldReject 数量限制（蒸汽输出仓可放多个，分摊输出）。
                        buildHatchAdder(MTELargeGeothermalSteamBoiler.class)
                            .atLeast(GeothermalSteamOutputHatchElement.SteamOutput)
                            .casingIndex(bronzeCasingIndex)
                            .hint(1)
                            .build(),
                        // v1.9.40 修复：开放普通流体输出仓（任意 MTEHatchOutput 子类，含 ME 输出仓）。
                        // distributeSteam 优先填蒸汽输出仓，剩余量回退到此位置注册的输出仓。
                        buildHatchAdder(MTELargeGeothermalSteamBoiler.class).atLeast(OutputHatch)
                            .casingIndex(bronzeCasingIndex)
                            .hint(1)
                            .build(),
                        buildHatchAdder(MTELargeGeothermalSteamBoiler.class).atLeast(SteamOutputBus)
                            .casingIndex(bronzeCasingIndex)
                            .hint(1)
                            .build()))
                .addElement(
                    'B',
                    // v1.10.62：ofChain 双分支 → ofBlocksTiered（与 A/E 同构，共享 mSetTier getter/setter）：
                    // StructureLib 语义保证同元素同 tier，青铜混入钢结构即拒（等级2 全钢）
                    onElementPass(
                        MTELargeGeothermalSteamBoiler::onCasingAdded,
                        ofBlocksTiered(
                            MTELargeGeothermalSteamBoiler::getGearTier,
                            ImmutableList
                                .of(Pair.of(GregTechAPI.sBlockCasings2, 2), Pair.of(GregTechAPI.sBlockCasings2, 3)),
                            -1,
                            (MTELargeGeothermalSteamBoiler t, Integer tier) -> {
                                if (tier > t.mSetTier) t.mSetTier = tier;
                            },
                            (MTELargeGeothermalSteamBoiler t) -> t.mSetTier)))
                .addElement(
                    'C',
                    onElementPass(
                        MTELargeGeothermalSteamBoiler::onCasingAdded,
                        ofBlocksTiered(
                            MTELargeGeothermalSteamBoiler::getPipeTier,
                            ImmutableList
                                .of(Pair.of(GregTechAPI.sBlockCasings2, 12), Pair.of(GregTechAPI.sBlockCasings2, 13)),
                            -1,
                            (MTELargeGeothermalSteamBoiler t, Integer tier) -> {
                                if (tier > t.mSetTier) t.mSetTier = tier;
                            },
                            (MTELargeGeothermalSteamBoiler t) -> t.mSetTier)))
                .addElement(
                    'D',
                    onElementPass(
                        MTELargeGeothermalSteamBoiler::onCasingAdded,
                        ofBlocksTiered(
                            MTELargeGeothermalSteamBoiler::getFireboxTier,
                            ImmutableList
                                .of(Pair.of(GregTechAPI.sBlockCasings3, 13), Pair.of(GregTechAPI.sBlockCasings3, 14)),
                            -1,
                            (MTELargeGeothermalSteamBoiler t, Integer tier) -> {
                                if (tier > t.mSetTier) t.mSetTier = tier;
                            },
                            (MTELargeGeothermalSteamBoiler t) -> t.mSetTier)))
                .addElement(
                    'E',
                    onElementPass(
                        MTELargeGeothermalSteamBoiler::onCasingAdded,
                        ofBlocksTiered(
                            MTELargeGeothermalSteamBoiler::getFrameTier,
                            ImmutableList.of(
                                Pair.of(GregTechAPI.sBlockFrames, Materials.Bronze.mMetaItemSubID),
                                Pair.of(GregTechAPI.sBlockFrames, Materials.Steel.mMetaItemSubID)),
                            -1,
                            (MTELargeGeothermalSteamBoiler t, Integer tier) -> {
                                if (tier > t.mSetTier) t.mSetTier = tier;
                            },
                            (MTELargeGeothermalSteamBoiler t) -> t.mSetTier)))
                .addElement(
                    'F',
                    ofChain(
                        // 水位：泥土位可被水替代（洗矿机同款机制），否则接受空气（注水前）
                        ofAnyWater(false),
                        isAir()))
                .addElement('G', isAir())
                .addElement('-', isAir())
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    private void onCasingAdded() {
        mCasingCount++;
    }

    private boolean hasSteamOutputHatch() {
        return !mSteamOutputHatches.isEmpty() || !mPressureSteamOutputHatches.isEmpty();
    }

    /**
     * 覆写父类 addOutputBusToMachineList，使其兼容蒸汽版输出总线（MTEHatchSteamBusOutput）。
     * <p>
     * 父类 MTEMultiBlockBase.addOutputBusToMachineList 显式拒绝 MTEHatchSteamBusOutput（返回 false），
     * 导致大型地热蒸汽锅炉虽然结构允许 SteamOutputBus，但蒸汽输出总线无法注册到 mOutputBusses，
     * 副产物（黑曜石、灰烬粉等）无法输出。
     * 此处移除该拒绝逻辑，将蒸汽输出总线与标准输出总线一并加入 mOutputBusses 列表。
     * <p>
     * 注意：MTEHatchSteamBusOutput 是 MTEHatchOutputBus 的子类，
     * 故 instanceof MTEHatchOutputBus 会同时匹配标准与蒸汽两种输出总线。
     * <p>
     * 参考：MTELargeCokeOven.addOutputBusToMachineList、MTESiemensMartinFurnace.addOutputBusToMachineList。
     */
    public boolean addOutputBusToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity == null) return false;
        if (aMetaTileEntity instanceof MTEHatchOutputBus hatch) {
            hatch.updateTexture(aBaseCasingIndex);
            return mOutputBusses.add(hatch);
        }
        return false;
    }

    private boolean addSteamOutputToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        // 必须先识别更具体的 MTEPressureSteamOutputHatch，因为它是 MTESteamOutputHatch 的子类。
        // 否则耐压蒸汽输出仓会被错误注册到普通蒸汽输出仓列表，导致过热蒸汽无法输出。
        if (aMetaTileEntity instanceof MTEPressureSteamOutputHatch pressureHatch) {
            pressureHatch.updateTexture(aBaseCasingIndex);
            return mPressureSteamOutputHatches.add(pressureHatch);
        }
        if (aMetaTileEntity instanceof MTESteamOutputHatch hatch) {
            hatch.updateTexture(aBaseCasingIndex);
            return mSteamOutputHatches.add(hatch);
        }
        return false;
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
        mSetTier = -1;
        mCasingCount = 0;
        mSteamOutputHatches.clear();
        mPressureSteamOutputHatches.clear();

        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET, errors)) {
            // checkPiece 扫描可能已把 mSetTier 置 2，失败时必须重置
            mSetTier = -1;
            return;
        }
        if (mSetTier <= 0) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            mSetTier = -1;
            return;
        }
        if (!hasValidOutputHatchesForTier()) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            mSetTier = -1;
            return;
        }
        if (mInputHatches.isEmpty() && mDualInputHatches.isEmpty()) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            mSetTier = -1;
            return;
        }

        updateHatchTexture();
        needsWaterFill = true;
    }

    // v1.9.40 修复：输出判定放宽——蒸汽输出仓、耐压蒸汽输出仓或任意普通流体输出仓（含 ME 输出仓）
    // 任一存在即可成型；产物流体类型由各仓 canStoreFluid 自行过滤（蒸汽输出仓只收蒸汽，普通仓默认全收）。
    private boolean hasValidOutputHatchesForTier() {
        return !mSteamOutputHatches.isEmpty() || !mPressureSteamOutputHatches.isEmpty() || !mOutputHatches.isEmpty();
    }

    private boolean hasOverheatChip() {
        if (mSetTier != 2) return false;
        ItemStack stack = getControllerSlot();
        return stack != null && GTSRItemList.GeothermalOverheatChip.isStackEqual(stack, true, true);
    }

    private boolean hasInvalidChip() {
        if (mSetTier == 2) return false;
        ItemStack stack = getControllerSlot();
        return stack != null && GTSRItemList.GeothermalOverheatChip.isStackEqual(stack, true, true);
    }

    @Override
    public CheckRecipeResult checkProcessing() {
        FluidStack lava = drainLavaInput(LAVA_PER_RECIPE, false);
        if (lava == null || lava.amount < LAVA_PER_RECIPE) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        drainLavaInput(LAVA_PER_RECIPE, true);

        boolean hasChip = hasOverheatChip();
        java.util.ArrayList<ItemStack> outputs = new java.util.ArrayList<>();
        java.util.Random rng = getBaseMetaTileEntity().getWorld().rand;

        // 20% 黑曜石
        if (rng.nextDouble() < 0.20) outputs.add(new ItemStack(Blocks.obsidian, 1));
        // 10% 灰烬粉
        if (rng.nextDouble() < 0.10) outputs.add(GTOreDictUnificator.get(OrePrefixes.dust, Materials.Ash, 1));
        // 8% 硫粉
        if (rng.nextDouble() < 0.08) outputs.add(GTOreDictUnificator.get(OrePrefixes.dust, Materials.Sulfur, 1));
        // 4% 钽铁矿粉
        if (rng.nextDouble() < 0.04) outputs.add(GTOreDictUnificator.get(OrePrefixes.dust, Materials.Tantalite, 1));
        // 6% 氧化铝粉
        if (rng.nextDouble() < 0.06)
            outputs.add(GTOreDictUnificator.get(OrePrefixes.dust, Materials.Aluminiumoxide, 1));
        // 2% 铜锭
        if (rng.nextDouble() < 0.02) outputs.add(GTOreDictUnificator.get(OrePrefixes.ingot, Materials.Copper, 1));
        // 1% 锡锭
        if (rng.nextDouble() < 0.01) outputs.add(GTOreDictUnificator.get(OrePrefixes.ingot, Materials.Tin, 1));
        // 0.5% 银锭
        if (rng.nextDouble() < 0.005) outputs.add(GTOreDictUnificator.get(OrePrefixes.ingot, Materials.Silver, 1));
        // 0.35% 金锭
        if (rng.nextDouble() < 0.0035) outputs.add(GTOreDictUnificator.get(OrePrefixes.ingot, Materials.Gold, 1));

        // 装载地热过热芯片后额外产出
        if (hasChip) {
            // 0.1% 磷粉
            if (rng.nextDouble() < 0.001)
                outputs.add(GTOreDictUnificator.get(OrePrefixes.dust, Materials.Phosphorus, 1));
            // 0.05% 金红石粉
            if (rng.nextDouble() < 0.0005) outputs.add(GTOreDictUnificator.get(OrePrefixes.dust, Materials.Rutile, 1));
            // 0.02% 白钨矿粉
            if (rng.nextDouble() < 0.0002)
                outputs.add(GTOreDictUnificator.get(OrePrefixes.dust, Materials.Scheelite, 1));
        }

        mOutputItems = outputs.toArray(new ItemStack[0]);

        int duration = calculateActualDuration();
        mMaxProgresstime = duration;
        mEfficiency = 10000;
        mEfficiencyIncrease = 10000;

        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    // v1.9.39 修复：岩浆取流改用 GT5U 原生 depleteInput(FluidStack, boolean)。
    // 原实现用 MTEHatch.getFluid() + 单参 drain，对 ME 输入仓（MTEHatchInputME，本地罐恒空）恒返回 null，
    // 导致岩浆永远检测不到、锅炉无法产蒸汽。原生 2 参 depleteInput 走 drain(UNKNOWN,...)，
    // 兼容普通仓 / ME 输入仓 / 限制性输入仓，且支持跨仓合计；流体匹配用 GTModHandler.getLava 的
    // isFluidEqual 语义，替代硬编码流体名比较。
    private FluidStack drainLavaInput(int amount, boolean doDrain) {
        FluidStack lava = GTModHandler.getLava(amount);
        // v1.10.6：样板仓（mDualInputHatches）岩浆供流兜底（引用扣减，样板仓自结算）
        if (depleteInput(lava, !doDrain)) return lava;
        if (doDrain && GTSRHatchFluidAccess.depleteFluidFromDuals(mDualInputHatches, lava) >= lava.amount) {
            return lava;
        }
        return null;
    }

    private int calculateActualDuration() {
        boolean hasChip = hasOverheatChip();
        if (hasChip) {
            double multiplier = 1.0
                + mHeat * ((double) (OVERHEAT_CHIP_RECIPE_TIME - BASE_RECIPE_TIME) / BASE_RECIPE_TIME);
            return (int) (BASE_RECIPE_TIME * multiplier);
        }
        int heatedTime = mSetTier == 1 ? HEATED_RECIPE_TIME_BRONZE : HEATED_RECIPE_TIME_STEEL;
        double multiplier = 1.0 + mHeat * ((double) (heatedTime - BASE_RECIPE_TIME) / BASE_RECIPE_TIME);
        return (int) (BASE_RECIPE_TIME * multiplier);
    }

    private long getCalcificationFullTime() {
        // 等级1（青铜）12 小时满垢；等级2（钢）不装芯片 4 小时、装过热芯片 2 小时满垢
        if (mSetTier == 1) return 12L * 3600 * 20;
        return hasOverheatChip() ? 2L * 3600 * 20 : 4L * 3600 * 20;
    }

    /**
     * 结垢产出系数：未结垢时 100%，完全结垢时降至 1%（线性递减）。
     */
    private double getCalcificationOutputFactor() {
        return Math.max(0.01d, 1.0d - 0.99d * mCalcification);
    }

    /**
     * 满垢后每 10 分钟向所有者玩家发送一次聊天窗提醒。
     */
    private void tickCalcificationWarning() {
        if (mCalcification >= 1.0d) {
            if (mCalcificationWarnTimer <= 0L) {
                mCalcificationWarnTimer = CALCIFICATION_WARN_INTERVAL_TICKS;
                GTSRMachineEvent.sendToOwner(getBaseMetaTileEntity().getOwnerUuid(), "gtsr.chat.calcification_full");
            } else {
                mCalcificationWarnTimer--;
            }
        } else {
            mCalcificationWarnTimer = 0L;
        }
    }

    // G 草方块位粒子形状偏移（9 个）：(列 2-4, 层 0, 行 2-4) - (3, 6, 1)
    private static List<int[]> getParticleOffsets() {
        if (mParticleOffsets == null) {
            List<int[]> offsets = new ArrayList<>();
            for (int b = 0; b < SHAPE_MAIN.length; b++) {
                for (int c = 0; c < SHAPE_MAIN[b].length; c++) {
                    for (int a = 0; a < SHAPE_MAIN[b][c].length(); a++) {
                        if (SHAPE_MAIN[b][c].charAt(a) == 'G') {
                            offsets.add(new int[] { a - HORIZONTAL_OFF_SET, b - VERTICAL_OFF_SET, c - DEPTH_OFF_SET });
                        }
                    }
                }
            }
            mParticleOffsets = offsets;
        }
        return mParticleOffsets;
    }

    /**
     * 客户端：每 tick 按热量 mHeat 生成上升白色云朵粒子（仿砖高炉 vertical motion 0.3）。
     * 粒子期望数 = mHeat / 0.5：mHeat=50% → 1 个/tick（现状基准），100% → 2 个/tick，25% → 平均 0.5 个/tick
     * （小数部分用概率平滑）。
     */
    private void spawnCloudParticle() {
        if (mHeat <= 0.0d) return;
        double expected = mHeat / 0.5d;
        int n = (int) expected;
        if (getBaseMetaTileEntity().getWorld().rand.nextDouble() < expected - n) n++;
        for (int i = 0; i < n; i++) {
            spawnOneParticle();
        }
    }

    /** 在随机一个 G 草方块位生成单个上升白色云朵粒子 */
    private void spawnOneParticle() {
        List<int[]> offsets = getParticleOffsets();
        if (offsets.isEmpty()) return;
        IGregTechTileEntity base = getBaseMetaTileEntity();
        World world = base.getWorld();
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

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (aBaseMetaTileEntity.isClientSide()) {
            // 有热量即渲染气体粒子动画（数量 ∝ mHeat，见 spawnCloudParticle）
            if (mHeat > 0.0d) spawnCloudParticle();
            return;
        }

        if (mMachine) {
            mStructureGraceTicks = 100;
        } else if (mStructureGraceTicks > 0) {
            mStructureGraceTicks--;
        }

        if (mMachine) {
            tickCalcificationWarning();
        }

        if (needsWaterFill && aTick % 20 == 0) {
            // 注水轴序：fillStructureWithWater 假定结构布局为 [深度][层][列]（洗矿机手写布局），
            // 本结构 GTUDK 布局为 [层][行][列]，须传 transpose 后的形状（数组=行/深度、串=层/垂直）。
            if (GTStructureUtility.fillStructureWithWater(
                aBaseMetaTileEntity,
                getExtendedFacing(),
                transpose(SHAPE_MAIN),
                HORIZONTAL_OFF_SET,
                VERTICAL_OFF_SET,
                DEPTH_OFF_SET,
                'F')) {
                needsWaterFill = false;
            }
        }

        if (aTick % 20 == 0) {
            // 超压自动退出：热量跌破 1.0（停机冷却等）时自动关闭超压模式并提示一次
            if (mOverpressure && mHeat < 1.0d) {
                mOverpressure = false;
                GTSRMachineEvent.sendToOwner(getBaseMetaTileEntity().getOwnerUuid(), "gtsr.chat.overpressure.off");
            }
            // 配方完成同 tick 会立即 checkRecipe() 重启新配方，重启 tick 存在 1 tick 的 mProgresstime==0 空窗，
            // 机器实际连续运行，不应视为停机；岩浆耗尽时 mMaxProgresstime=0 仍会正确进入下方冷却分支
            boolean isRunning = mMaxProgresstime > 0;
            if (isRunning) {
                double rate = hasOverheatChip() ? HEAT_UP_CHIP : (mSetTier == 1 ? HEAT_UP_BRONZE : HEAT_UP_STEEL);
                if (mOverpressure) rate *= 0.2d;
                // 超压模式下热量上限放开到 2.0（普通模式仍钳 1.0）
                mHeat = Math.min(mOverpressure ? 2.0d : 1.0d, mHeat + rate);
            } else if (mMachine || mStructureGraceTicks <= 0) {
                // Cool down when structure is valid but idle, or after grace period when broken
                // 超压模式下散热加倍
                mHeat = Math.max(0.0d, mHeat - (mOverpressure ? HEAT_DOWN * 2.0d : HEAT_DOWN));
            }
        }

        // Steam generation from water (independent of lava recipe)
        // 缺水停机（mWaterStop）期间不产蒸汽：即使结构有效、热量仍高，也要等玩家重新开机
        if (aTick % 20 == 0 && mMachine && !mWaterStop && mHeat > 0.01d) {
            int maxOutput = mSetTier == 1 ? MAX_OUTPUT_BRONZE : MAX_OUTPUT_STEEL;
            int maxWaterNeeded = maxOutput / STEAM_PER_WATER;
            int consumedWater = (int) (maxWaterNeeded * mHeat * getCalcificationOutputFactor());

            // v1.10.60：水消耗改访问层（getTankInfo 探测+跨仓实扣），删窗口外 dual 兜底（免费流体），beta-1 安全
            // 优先消耗蒸馏水（不钙化）；总量不足时本次不产蒸汽（与岩浆检查语义一致）。
            boolean producedSteam = false;
            if (consumedWater > 0) {
                FluidStack distilledWater = GTModHandler.getDistilledWater(consumedWater);
                FluidStack water = GTModHandler.getWater(consumedWater);
                boolean hasDistilled = GTSRHatchFluidAccess.hasEnoughAcross(mInputHatches, distilledWater);
                boolean hasWater = GTSRHatchFluidAccess.hasEnoughAcross(mInputHatches, water);
                FluidStack toDeplete = hasDistilled ? distilledWater : (hasWater ? water : null);
                boolean isDistilledWater = hasDistilled;
                if (toDeplete != null
                    && GTSRHatchFluidAccess.depleteFluidAcross(mInputHatches, toDeplete) >= toDeplete.amount) {
                    int steamOutput = consumedWater * STEAM_PER_WATER;
                    mCurrentSteamOutput = steamOutput;
                    mRunningTicks += 20;

                    // Calcification logic
                    // 超压且用普通水：跳过 1 小时延迟门槛（立刻开始结垢）、每次增量 ×20（0.01→0.2，
                    // 满垢时间 = 原满垢 1/20）；interval 周期调制保留，避免逐秒高速结垢；蒸馏水豁免保持
                    if (!isDistilledWater && (mOverpressure || mRunningTicks > CALCIFICATION_DELAY_TICKS)) {
                        long calcificationInterval = getCalcificationFullTime() / 100;
                        if ((mRunningTicks / 20) % calcificationInterval == 0) {
                            mCalcification = Math.min(1.0d, mCalcification + (mOverpressure ? 0.2d : 0.01d));
                        }
                    }

                    // Distribute steam
                    if (steamOutput > 0) {
                        boolean isSuperheated = hasOverheatChip();
                        FluidStack steam = isSuperheated
                            ? FluidRegistry.getFluidStack("ic2superheatedsteam", steamOutput)
                            : Materials.Steam.getGas(steamOutput);
                        distributeSteam(steam);
                    }
                    producedSteam = true;
                }
            }

            // 缺水断电：本次确实尝试消耗水（consumedWater > 0）但水/蒸馏水（含样板仓兜底）全部不足。
            // stopMachine 复位 mMaxProgresstime（断电即降温）并 disableWorking（mWorks=false，结构 mMachine 不变），
            // 玩家通过 GUI 电源开关 / 软锤重新开机（onEnableWorking 清除 mWaterStop 与 mNoWaterNotified）。
            if (!producedSteam && consumedWater > 0) {
                mWaterStop = true;
                if (!mNoWaterNotified) {
                    mNoWaterNotified = true;
                    GTSRMachineEvent.sendToOwner(getBaseMetaTileEntity().getOwnerUuid(), "gtsr.chat.no_water");
                }
                stopMachine(ShutDownReasonRegistry.NONE);
            }

            if (!producedSteam) {
                mCurrentSteamOutput = 0;
            }
        } else if (aTick % 20 == 0 && (!mMachine || mWaterStop || mHeat <= 0.01d)) {
            mCurrentSteamOutput = 0;
        }
    }

    // v1.9.40 修复：蒸汽输出分配统一优先级——
    // 超热蒸汽：耐压蒸汽输出仓 → 普通蒸汽输出仓 → 全部流体输出仓（含 ME 输出仓）
    // 普通蒸汽：蒸汽输出仓 → 耐压蒸汽输出仓 → 全部流体输出仓（含 ME 输出仓）
    // 各仓 canStoreFluid 自行过滤（蒸汽输出仓只收蒸汽，普通仓默认全收），剩余量静默丢弃（既有 voiding 设计）。
    private void distributeSteam(FluidStack steam) {
        if (steam == null) return;

        boolean isSuperheated = "ic2superheatedsteam".equals(FluidRegistry.getFluidName(steam.getFluid()));

        if (isSuperheated) {
            fillSteamOutputHatches(steam, mPressureSteamOutputHatches);
            fillSteamOutputHatches(steam, mSteamOutputHatches);
        } else {
            fillSteamOutputHatches(steam, mSteamOutputHatches);
            fillSteamOutputHatches(steam, mPressureSteamOutputHatches);
        }
        fillSteamOutputHatches(steam, mOutputHatches);
    }

    private void fillSteamOutputHatches(FluidStack steam, List<? extends MTEHatchOutput> hatches) {
        for (MTEHatchOutput hatch : hatches) {
            if (steam.amount <= 0) break;
            int filled = hatch.fill(ForgeDirection.UNKNOWN, steam.copy(), true);
            steam.amount -= filled;
        }
    }

    @Override
    protected MTEMultiBlockBaseGui<?> getGui() {
        return new MTELargeGeothermalSteamBoilerGui(this);
    }

    @Override
    public boolean onShiftRightClick(EntityPlayer aPlayer, ForgeDirection side, float aX, float aY, float aZ) {
        if (!getBaseMetaTileEntity().isServerSide()) return true;
        if (mCalcification > 0.0d || mRunningTicks > 0L) {
            mCalcification = 0.0d;
            mRunningTicks = 0L;
            GTSRMachineEvent.sendToPlayer(aPlayer, "gtsr.chat.calcification_cleared");
            return true;
        }
        return false;
    }

    // 螺丝刀切换超压模式（覆写父类单配方锁定开关，本机不使用配方锁定）：
    // 开 → 关；关且热量 ≥100% → 开；关且热量不足 → 提示先升温
    @Override
    public void onScrewdriverRightClick(ForgeDirection side, EntityPlayer aPlayer, float aX, float aY, float aZ,
        ItemStack aTool) {
        if (aPlayer.worldObj.isRemote) return;
        if (mOverpressure) {
            mOverpressure = false;
            GTSRMachineEvent.sendToPlayer(aPlayer, "gtsr.chat.overpressure.off");
        } else if (mHeat >= 1.0d) {
            mOverpressure = true;
            GTSRMachineEvent.sendToPlayer(aPlayer, "gtsr.chat.overpressure.on");
        } else {
            GTSRMachineEvent.sendToPlayer(aPlayer, "gtsr.chat.overpressure.need_heat");
        }
        getBaseMetaTileEntity().markDirty();
    }

    // GT5U 原生重新开机入口（GUI 电源开关 / 软锤 / 工作控制 cover）：
    // 清除缺水停机标志与提示状态，让蒸汽产出与配方恢复
    @Override
    public void onEnableWorking() {
        super.onEnableWorking();
        mWaterStop = false;
        mNoWaterNotified = false;
    }

    @Deprecated
    @Override
    protected void drawTexts(DynamicPositionedColumn screenElements, SlotWidget inventorySlot) {
        super.drawTexts(screenElements, inventorySlot);
        screenElements
            .widget(
                TextWidget
                    .dynamicString(
                        () -> hasInvalidChip()
                            ? EnumChatFormatting.RED
                                + StatCollector.translateToLocal("gtsr.gui.geothermal_boiler.chip_tier2_warn")
                            : " ")
                    .setSynced(false)
                    .setDefaultColor(COLOR_TEXT_WHITE.get()))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.gui.geothermal_boiler.heat")
                        + EnumChatFormatting.GOLD
                        + numberFormat.format(mHeat * 100)
                        + "% "
                        + EnumChatFormatting.RESET))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.WHITE
                        + StatCollector.translateToLocal("gtsr.gui.geothermal_boiler.calcification")
                        + EnumChatFormatting.RED
                        + numberFormat.format(mCalcification * 100)
                        + "% "
                        + EnumChatFormatting.RESET))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.WHITE
                        + StatCollector.translateToLocal("gtsr.gui.geothermal_boiler.steam_output")
                        + EnumChatFormatting.AQUA
                        + NumberFormatUtil.formatNumber(mCurrentSteamOutput)
                        + " L/s "
                        + EnumChatFormatting.WHITE
                        + (hasOverheatChip() ? StatCollector.translateToLocal("gtsr.gui.geothermal_boiler.superheated")
                            : StatCollector.translateToLocal("gtsr.gui.geothermal_boiler.steam"))
                        + EnumChatFormatting.RESET))
            .widget(new FakeSyncWidget.DoubleSyncer(() -> mHeat, val -> mHeat = val))
            .widget(new FakeSyncWidget.DoubleSyncer(() -> mCalcification, val -> mCalcification = val))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mCurrentSteamOutput, val -> mCurrentSteamOutput = val));
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal("gtsr.tooltip.geothermal_boiler.type"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.geothermal_boiler.desc"))
            .addInfo(EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.geothermal_boiler.desc_2"))
            .addInfo(EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.tooltip.geothermal_boiler.heat"))
            .addInfo(
                EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.geothermal_boiler.water_info"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.geothermal_boiler.calcification"))
            .addInfo(
                EnumChatFormatting.AQUA
                    + StatCollector.translateToLocal("gtsr.tooltip.geothermal_boiler.calcification_2"))
            .addInfo(
                EnumChatFormatting.GREEN
                    + StatCollector.translateToLocal("gtsr.tooltip.geothermal_boiler.calcification_d"))
            .addInfo(
                EnumChatFormatting.AQUA
                    + StatCollector.translateToLocal("gtsr.tooltip.geothermal_boiler.clear_calcification_hint"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.geothermal_boiler.chip_info"))
            .addInfo(
                EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.geothermal_boiler.chip_info_2"))
            .addSeparator()
            .addInfo(
                EnumChatFormatting.BLUE + "Tier 1 "
                    + EnumChatFormatting.DARK_PURPLE
                    + StatCollector.translateToLocal("gtsr.tooltip.solar_array.tier_bronze")
                    + EnumChatFormatting.GOLD
                    + " 60,000"
                    + EnumChatFormatting.GRAY
                    + " L/s "
                    + StatCollector.translateToLocal("gtsr.tooltip.geothermal_boiler.max_output"))
            .addInfo(
                EnumChatFormatting.BLUE + "Tier 2 "
                    + EnumChatFormatting.DARK_PURPLE
                    + StatCollector.translateToLocal("gtsr.tooltip.solar_array.tier_steel")
                    + EnumChatFormatting.GOLD
                    + " 150,000"
                    + EnumChatFormatting.GRAY
                    + " L/s "
                    + StatCollector.translateToLocal("gtsr.tooltip.geothermal_boiler.max_output"))
            .addSeparator()
            .addInfo(
                EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.tooltip.geothermal_boiler.products"))
            .addInfo(
                EnumChatFormatting.GRAY
                    + StatCollector.translateToLocal("gtsr.tooltip.geothermal_boiler.products_line1"))
            .addInfo(
                EnumChatFormatting.GRAY
                    + StatCollector.translateToLocal("gtsr.tooltip.geothermal_boiler.products_line2"))
            .addInfo(
                EnumChatFormatting.GOLD
                    + StatCollector.translateToLocal("gtsr.tooltip.geothermal_boiler.chip_products"))
            .addInfo(
                EnumChatFormatting.GRAY
                    + StatCollector.translateToLocal("gtsr.tooltip.geothermal_boiler.chip_products_line"))
            .addSeparator()
            .addInfo(
                EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.tooltip.geothermal_boiler.lava_rate"))
            .addInfo(
                EnumChatFormatting.GRAY
                    + StatCollector.translateToLocal("gtsr.tooltip.geothermal_boiler.lava_rate_bronze"))
            .addInfo(
                EnumChatFormatting.GRAY
                    + StatCollector.translateToLocal("gtsr.tooltip.geothermal_boiler.lava_rate_steel"))
            .addInfo(
                EnumChatFormatting.GRAY
                    + StatCollector.translateToLocal("gtsr.tooltip.geothermal_boiler.lava_rate_chip"))
            .beginStructureBlock(7, 8, 7, false)
            .addController(StatCollector.translateToLocal("gtsr.tooltip.geothermal_boiler.ctrl"))
            .addInputHatch(StatCollector.translateToLocal("gtsr.tooltip.geothermal_boiler.input_hatch"), 1)
            .addOtherStructurePart(
                StatCollector.translateToLocal("gtsr.tooltip.geothermal_boiler.steam_output"),
                StatCollector.translateToLocal("gtsr.tooltip.shared.any_casing"),
                1)
            .addOutputBus(StatCollector.translateToLocal("gtsr.tooltip.geothermal_boiler.output_bus"), 1)
            .addStructureInfo("")
            .addStructureInfo(
                EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.shared.bronze_steel_tier"))
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.casing"), 70, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.firebox"), 21, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.pipe"), 12, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.gear_box"), 8, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.frame"), 53, false)
            .addStructureInfo(
                EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.tooltip.geothermal_boiler.chip")
                    + ": "
                    + StatCollector.translateToLocal("gtsr.tooltip.geothermal_boiler.chip_desc"))
            .addStructureHint("gtsr.tooltip.shared.no_maintenance")
            .addInfo(
                EnumChatFormatting.LIGHT_PURPLE + StatCollector.translateToLocal("gtsr.tooltip.overpressure.enable"))
            .addInfo(
                EnumChatFormatting.LIGHT_PURPLE + StatCollector.translateToLocal("gtsr.tooltip.overpressure.effects"))
            .addInfo(GTSRUtils.getAddedByLine())
            .toolTipFinisher();
        return tt;
    }

    @Override
    protected IAlignmentLimits getInitialAlignmentLimits() {
        return (d, r, f) -> d.offsetY == 0 && r.isNotRotated() && !f.isVerticallyFliped();
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int aColorIndex, boolean aActive, boolean aRedstone) {
        if (side == facing) {
            return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(getCasingTextureID()),
                aActive ? getFrontOverlayActive() : getFrontOverlay() };
        }
        return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(getCasingTextureID()) };
    }

    protected ITexture getFrontOverlay() {
        return TextureFactory.of(Textures.BlockIcons.OVERLAY_FRONT_STEAM_FURNACE);
    }

    protected ITexture getFrontOverlayActive() {
        return TextureFactory.of(Textures.BlockIcons.OVERLAY_FRONT_STEAM_FURNACE_ACTIVE);
    }

    @Override
    public int getMaxEfficiency(ItemStack aStack) {
        return 10000;
    }

    @Override
    public String[] getInfoData() {
        ArrayList<String> info = new ArrayList<>();
        info.add(EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.geothermal_boiler.type"));

        if (!mMachine) {
            info.add(EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.gui.building"));
            return info.toArray(new String[0]);
        }

        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.geothermal_boiler.heat")
                + EnumChatFormatting.RED
                + String.format("%.1f%%", mHeat * 100.0d));

        String statusKey;
        EnumChatFormatting statusColor;
        if (mMaxProgresstime > 0) {
            statusKey = "gtsr.gui.status.running";
            statusColor = EnumChatFormatting.AQUA;
        } else {
            statusKey = "gtsr.gui.status.idle";
            statusColor = EnumChatFormatting.GRAY;
        }
        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.status")
                + " "
                + statusColor
                + StatCollector.translateToLocal(statusKey));

        String steamOutputType = hasOverheatChip() ? StatCollector.translateToLocal("gtsr.gui.steam_type.superheated")
            : StatCollector.translateToLocal("gtsr.gui.steam_type.normal");
        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.geothermal_boiler.steam_output")
                + " "
                + EnumChatFormatting.LIGHT_PURPLE
                + steamOutputType);

        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.geothermal_boiler.calcification")
                + EnumChatFormatting.RED
                + String.format("%.1f%%", mCalcification * 100.0d));

        return info.toArray(new String[0]);
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setInteger("mSetTier", mSetTier);
        aNBT.setDouble("mHeat", mHeat);
        aNBT.setDouble("mCalcification", mCalcification);
        aNBT.setLong("mRunningTicks", mRunningTicks);
        aNBT.setLong("mCalcificationWarnTimer", mCalcificationWarnTimer);
        aNBT.setBoolean("mOverpressure", mOverpressure);
        aNBT.setBoolean("mWaterStop", mWaterStop);
        aNBT.setBoolean("mNoWaterNotified", mNoWaterNotified);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mSetTier = aNBT.getInteger("mSetTier");
        mHeat = aNBT.getDouble("mHeat");
        mCalcification = aNBT.getDouble("mCalcification");
        mRunningTicks = aNBT.getLong("mRunningTicks");
        mCalcificationWarnTimer = aNBT.getLong("mCalcificationWarnTimer");
        // 旧档缺省 false
        mOverpressure = aNBT.getBoolean("mOverpressure");
        mWaterStop = aNBT.getBoolean("mWaterStop");
        mNoWaterNotified = aNBT.getBoolean("mNoWaterNotified");
    }
}
