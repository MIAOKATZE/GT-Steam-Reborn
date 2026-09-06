package com.miaokatze.gtsr.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlocksTiered;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static com.miaokatze.gtsr.common.api.enums.GTSRHatchElement.PressureSteamCoolingHatch;
import static com.miaokatze.gtsr.common.api.enums.GTSRHatchElement.PressureSteamInputHatch;
import static com.miaokatze.gtsr.common.api.enums.GTSRHatchElement.SteamCoolingHatch;
import static gregtech.api.enums.HatchElement.OutputHatch;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.FluidStack;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.ImmutableList;
import com.gtnewhorizon.structurelib.alignment.IAlignmentLimits;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizons.modularui.common.widget.DynamicPositionedColumn;
import com.gtnewhorizons.modularui.common.widget.FakeSyncWidget;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;
import com.gtnewhorizons.modularui.common.widget.TextWidget;
import com.miaokatze.gtsr.api.compat.ICoolingHatchHolder;
import com.miaokatze.gtsr.api.compat.SteamCoolingSupport;
import com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps;
import com.miaokatze.gtsr.common.api.progress.GTSRProgressBar;
import com.miaokatze.gtsr.common.api.progress.GTSRProgressEntry;
import com.miaokatze.gtsr.common.util.GTSRUtils;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.objects.overclockdescriber.OverclockDescriber;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrorRegistry;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.VoidProtectionHelper;
import gregtech.common.blocks.BlockCasings1;
import gregtech.common.blocks.BlockCasings2;
import gregtech.common.tileentities.machines.IDualInputHatch;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTESteamMultiBlockBase;

public class MTEAirCompressor extends MTESteamMultiBlockBase<MTEAirCompressor> implements ISurvivalConstructable {

    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static final int HORIZONTAL_OFF_SET = 1;
    private static final int VERTICAL_OFF_SET = 2;
    private static final int DEPTH_OFF_SET = 0;

    private static IStructureDefinition<MTEAirCompressor> STRUCTURE_DEFINITION = null;

    public int mSetTier = -1;
    protected int mCasingCount = 0;

    public MTEAirCompressor(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTEAirCompressor(String aName) {
        super(aName);
    }

    /**
     * GTSR 进度词条收集钩子（mixin 惰性触发一次）：注册顺序 = GUI 终端显示顺序（并行数）。
     * 不加 @Override：编译期 GT++ jar 无此方法，运行时由 mixin 注入后多态生效。
     */
    protected void gtsr$collectProgressEntries(GTSRProgressBar bar) {
        bar.registerEntry(
            GTSRProgressEntry
                .of("parallel", "gtsr.gui.parallel", "%.0f", EnumChatFormatting.GOLD, () -> getMaxParallelRecipes()));
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEAirCompressor(mName);
    }

    @Override
    public String getMachineType() {
        return "空气压缩机";
    }

    @Override
    public boolean isHighPressure() {
        return mSetTier >= 2;
    }

    @Nullable
    public static Integer getCasingTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockCasings1 && meta == 10) return 1;
        if (block == GregTechAPI.sBlockCasings2 && meta == 0) return 2;
        return null;
    }

    @Nullable
    public static Integer getPipeTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockCasings2 && meta == 12) return 1;
        if (block == GregTechAPI.sBlockCasings2 && meta == 13) return 2;
        return null;
    }

    @Nullable
    public static Integer getGearTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockCasings2 && meta == 2) return 1;
        if (block == GregTechAPI.sBlockCasings2 && meta == 3) return 2;
        return null;
    }

    @Nullable
    public static Integer getFrameTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockFrames && meta == Materials.Bronze.mMetaItemSubID) return 1;
        if (block == GregTechAPI.sBlockFrames && meta == Materials.Steel.mMetaItemSubID) return 2;
        return null;
    }

    protected int getCasingTextureID() {
        if (mSetTier == 2) {
            return ((BlockCasings2) GregTechAPI.sBlockCasings2).getTextureIndex(0);
        }
        return ((BlockCasings1) GregTechAPI.sBlockCasings1).getTextureIndex(10);
    }

    /**
     * v1.10.62：覆写 GT5U ICasingTextureProvider 钩子（GT5U SolarFactory 同款），控制器贴图按
     * getCasingTextureID()（== 2 判定）推导——修复未成型时字节同步回绕值 127 被基类
     * isHighPressure() >= 2 误判为钢外壳（刚放置即显示等级2底材）的问题。
     * 不加 @Override：该接口仅 beta-2 存在（beta-1 无 ICasingTextureProvider），beta-1 下本方法为惰性方法。
     */
    public ITexture getCasingTexture() {
        return Textures.BlockIcons.getCasingTextureForId(getCasingTextureID());
    }

    protected void updateAllHatchTextures() {
        int textureID = getCasingTextureID();
        for (MTEHatch h : mSteamInputs) h.updateTexture(textureID);
        for (MTEHatch h : mSteamOutputs) h.updateTexture(textureID);
        for (MTEHatch h : mSteamInputFluids) h.updateTexture(textureID);
        for (MTEHatch h : mInputHatches) h.updateTexture(textureID);
        for (MTEHatch h : mOutputHatches) h.updateTexture(textureID);
        for (MTEHatch h : mInputBusses) h.updateTexture(textureID);
        for (MTEHatch h : mOutputBusses) h.updateTexture(textureID);
        if (mDualInputHatches != null) {
            for (IDualInputHatch dualHatch : mDualInputHatches) {
                if (dualHatch != null) dualHatch.updateTexture(textureID);
            }
        }
        SteamCoolingSupport.updateHatchTextures((ICoolingHatchHolder) this, textureID);
    }

    @Override
    public void onValueUpdate(byte aValue) {
        mSetTier = aValue;
    }

    @Override
    public byte getUpdateData() {
        return (byte) mSetTier;
    }

    @Override
    public IStructureDefinition<MTEAirCompressor> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            final int bronzeCasingIndex = ((BlockCasings1) GregTechAPI.sBlockCasings1).getTextureIndex(10);

            STRUCTURE_DEFINITION = StructureDefinition.<MTEAirCompressor>builder()
                .addShape(
                    STRUCTURE_PIECE_MAIN,
                    transpose(
                        new String[][] { { "EBE", "CDC", "BBB", "CDC", "EBE" }, { "EBE", "CDC", "B B", "CDC", "EBE" },
                            { "E~E", "CDC", "B B", "CDC", "EBE" }, { "EBE", "BBB", "BBB", "BBB", "EBE" } }))
                .addElement(
                    'B',
                    ofChain(
                        // casing-first: NEI 投影优先渲染外壳；真实 hatch 坐标上 casing 匹配失败后继续匹配 hatch adder。
                        onElementPass(
                            MTEAirCompressor::onCasingAdded,
                            ofBlocksTiered(
                                MTEAirCompressor::getCasingTier,
                                ImmutableList.of(
                                    Pair.of(GregTechAPI.sBlockCasings1, 10),
                                    Pair.of(GregTechAPI.sBlockCasings2, 0)),
                                -1,
                                (MTEAirCompressor t, Integer tier) -> t.mSetTier = tier,
                                (MTEAirCompressor t) -> t.mSetTier)),
                        // Use atLeast(PressureSteamInputHatch) instead of hatchIds(...). Its mteBlacklist()
                        // excludes MTEHatchPressureSteamInput.class so NEI does not render it on casing positions.
                        buildHatchAdder(MTEAirCompressor.class).atLeast(PressureSteamInputHatch)
                            .casingIndex(bronzeCasingIndex)
                            .hint(1)
                            .shouldReject(t -> !t.mSteamInputFluids.isEmpty() && !t.mInputHatches.isEmpty())
                            .build(),
                        buildHatchAdder(MTEAirCompressor.class).atLeast(OutputHatch)
                            .casingIndex(bronzeCasingIndex)
                            .hint(1)
                            .build(),
                        // v1.9.40 新增：冷却仓元素（可选）。蒸汽消耗的冷却产物（普通→蒸馏水 160:1、
                        // 过热→蒸汽 1:1）由 mixin 推入对应冷却仓，此前结构无此元素导致产物滞留/丢失。
                        buildHatchAdder(MTEAirCompressor.class).atLeast(SteamCoolingHatch, PressureSteamCoolingHatch)
                            .casingIndex(bronzeCasingIndex)
                            .hint(1)
                            .build()))
                .addElement(
                    'C',
                    onElementPass(
                        MTEAirCompressor::onCasingAdded,
                        ofBlocksTiered(
                            MTEAirCompressor::getPipeTier,
                            ImmutableList
                                .of(Pair.of(GregTechAPI.sBlockCasings2, 12), Pair.of(GregTechAPI.sBlockCasings2, 13)),
                            -1,
                            (MTEAirCompressor t, Integer tier) -> { if (tier > t.mSetTier) t.mSetTier = tier; },
                            (MTEAirCompressor t) -> t.mSetTier)))
                .addElement(
                    'D',
                    onElementPass(
                        MTEAirCompressor::onCasingAdded,
                        ofBlocksTiered(
                            MTEAirCompressor::getGearTier,
                            ImmutableList
                                .of(Pair.of(GregTechAPI.sBlockCasings2, 2), Pair.of(GregTechAPI.sBlockCasings2, 3)),
                            -1,
                            (MTEAirCompressor t, Integer tier) -> { if (tier > t.mSetTier) t.mSetTier = tier; },
                            (MTEAirCompressor t) -> t.mSetTier)))
                .addElement(
                    'E',
                    onElementPass(
                        MTEAirCompressor::onCasingAdded,
                        ofBlocksTiered(
                            MTEAirCompressor::getFrameTier,
                            ImmutableList.of(
                                Pair.of(GregTechAPI.sBlockFrames, Materials.Bronze.mMetaItemSubID),
                                Pair.of(GregTechAPI.sBlockFrames, Materials.Steel.mMetaItemSubID)),
                            -1,
                            (MTEAirCompressor t, Integer tier) -> { if (tier > t.mSetTier) t.mSetTier = tier; },
                            (MTEAirCompressor t) -> t.mSetTier)))
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    private void onCasingAdded() {
        mCasingCount++;
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
        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET, errors)) {
            mSetTier = -1;
            return;
        }
        if (mSetTier <= 0) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            mSetTier = -1;
            return;
        }
        if (mSteamInputFluids.isEmpty() && mInputHatches.isEmpty()) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            mSetTier = -1;
            return;
        }
        if (mOutputHatches.isEmpty()) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            mSetTier = -1;
            return;
        }
        updateAllHatchTextures();
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return GTSRRecipeMaps.airCompressorRecipes;
    }

    @Override
    public OverclockDescriber getOverclockDescriber() {
        return null;
    }

    @Override
    public CheckRecipeResult checkProcessing() {
        if (getTotalSteamStored() <= 0) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }
        boolean isNether = getBaseMetaTileEntity().getWorld().provider.dimensionId == -1;
        int amount = 800 * getMaxParallelRecipes();
        FluidStack outputFluid = isNether ? Materials.NetherAir.getFluid(amount) : Materials.Air.getGas(amount);

        // v1.7.26 修复：先预检查输出仓空间是否足够。
        // 原实现直接设置 mOutputFluids 后由 onPostTick 的 addFluidOutputs 尝试输出，
        // 若输出仓已满则触发 stopMachine(FLUID_OUTPUT_FAILED) 强制关机。
        // 现改为先预检查，若空间不足则返回 FLUID_OUTPUT_FULL（GUI 显示"流体输出空间不足"），
        // 机器保持待机状态，等输出仓有空间后继续工作，而不是强制关机。
        // 压缩空气（Air）和下界空气（NetherAir）共用同一条输出仓，故两种产物都需要预检查。
        //
        // v1.7.30 修复：将 lEUt/mMaxProgresstime/mEfficiencyIncrease 的设置移到 VPH 检查通过之后。
        // 原实现将这些字段设置在 VPH 检查之前，导致 VPH 返回 FLUID_OUTPUT_FULL 时
        // mMaxProgresstime 仍然是 20，vanilla runMachine() 看到 mMaxProgresstime > 0
        // 认为机器正在运行配方，但 mOutputFluids=null（VPH 失败时直接 return 未设置），
        // 表现为"GUI 显示流体输出空间不足但机器仍然在工作"。
        // 与 vanilla MTEMultiBlockBase.checkProcessing() 的顺序保持一致：
        // if (!result.wasSuccessful()) return result; // 失败时直接 return，mMaxProgresstime 保持 0
        // mMaxProgresstime = processingLogic.getDuration(); // 只有成功时才设置
        VoidProtectionHelper vph = new VoidProtectionHelper().setMachine(this)
            .setFluidOutputs(new FluidStack[] { outputFluid })
            .setMaxParallel(1)
            .build();
        if (vph.isFluidFull()) {
            return CheckRecipeResultRegistry.FLUID_OUTPUT_FULL;
        }

        lEUt = mSetTier == 2 ? -60 : -20;
        mMaxProgresstime = 20;
        mEfficiencyIncrease = 10000;
        mOutputFluids = new FluidStack[] { outputFluid };
        updateSlots();
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    @Override
    public int getMaxParallelRecipes() {
        return mSetTier == 2 ? 4 : 1;
    }

    @Override
    public int getTierRecipes() {
        return 0;
    }

    @Override
    public int getMaxEfficiency(ItemStack aStack) {
        return 10000;
    }

    @Override
    public boolean supportsPowerPanel() {
        return false;
    }

    @Override
    protected IIconContainer getInactiveOverlay() {
        return Textures.BlockIcons.OVERLAY_FRONT_STEAM_COMPRESSOR;
    }

    @Override
    protected IIconContainer getActiveOverlay() {
        return Textures.BlockIcons.OVERLAY_FRONT_STEAM_COMPRESSOR_ACTIVE;
    }

    // beta-2 兼容：MTESteamMultiBlockBase 将 getActiveGlowOverlay/getInactiveGlowOverlay 改为 abstract
    // 返回 Textures.BlockIcons.VOID（GT5U 官方"空纹理"常量，渲染器跳过 InvisibleIcon，无发光层）
    // 不能返回 null，否则 beta-2 的 createTextureWithCasing 会导致 GTTextureBuilder.build() 抛出
    // "iconContainer not specified!" 崩溃（创造物品栏渲染时触发）
    @Override
    protected IIconContainer getActiveGlowOverlay() {
        return Textures.BlockIcons.VOID;
    }

    @Override
    protected IIconContainer getInactiveGlowOverlay() {
        return Textures.BlockIcons.VOID;
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal("gtsr.tooltip.air_compressor.type"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.air_compressor.desc"))
            .addInfo(EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.air_compressor.desc_2"))
            .addInfo(EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.air_compressor.desc_3"))
            .addSeparator()
            .addInfo(
                EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.tooltip.shared.steam_cost")
                    + EnumChatFormatting.WHITE
                    + " 400/1200 L/s"
                    + EnumChatFormatting.GRAY
                    + " ("
                    + StatCollector.translateToLocal("gtsr.gui.tier.bronze")
                    + "/"
                    + StatCollector.translateToLocal("gtsr.gui.tier.steel")
                    + ")")
            .addInfo(
                EnumChatFormatting.GREEN + StatCollector.translateToLocal("gtsr.tooltip.shared.superheated_quadruples"))
            .addInfo(
                EnumChatFormatting.LIGHT_PURPLE + StatCollector.translateToLocal("gtsr.tooltip.air_compressor.nether"))
            // [GT-compat] beta 兼容层（beta1/beta2/beta3）：beta-3 起始参数序为 (w,h,l)，实参已按 beta-3 语义排列
            .beginStructureBlock(3, 4, 5, false)
            .addController(StatCollector.translateToLocal("gtsr.tooltip.air_compressor.ctrl"))
            .addOutputHatch(StatCollector.translateToLocal("gtsr.tooltip.air_compressor.output_hatch"), 1)
            .addOtherStructurePart(
                StatCollector.translateToLocal("gtsr.tooltip.air_compressor.steam_input"),
                StatCollector.translateToLocal("gtsr.tooltip.shared.any_casing"),
                1)
            .addStructureInfo("")
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.casing"), 23, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.pipe"), 12, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.gear_box"), 6, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.frame"), 16, false)
            .addStructureInfo(
                EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.tooltip.shared.parallel")
                    + ": "
                    + EnumChatFormatting.GOLD
                    + "1"
                    + EnumChatFormatting.GRAY
                    + " ("
                    + StatCollector.translateToLocal("gtsr.gui.tier.bronze")
                    + ")"
                    + EnumChatFormatting.GOLD
                    + "/4"
                    + EnumChatFormatting.GRAY
                    + " ("
                    + StatCollector.translateToLocal("gtsr.gui.tier.steel")
                    + ")")
            .addStructureHint("gtsr.tooltip.shared.no_maintenance")
            .addStructureHint("gtsr.tooltip.air_compressor.optional_cooling")
            .addInfo(GTSRUtils.getAddedByLine())
            .toolTipFinisher();
        return tt;
    }

    @Override
    protected IAlignmentLimits getInitialAlignmentLimits() {
        return (d, r, f) -> d.offsetY == 0 && r.isNotRotated() && !f.isVerticallyFliped();
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setInteger("mSetTier", mSetTier);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mSetTier = aNBT.getInteger("mSetTier");
    }

    public boolean hasSuperheatedSteamInHatch() {
        // v1.10.6：统一走 SteamCoolingSupport（mSteamInputFluids 本地罐 + mInputHatches 3参 drain 探测）
        return SteamCoolingSupport.hasSuperheatedSteam(this);
    }

    @Override
    protected void drawTexts(DynamicPositionedColumn screenElements, SlotWidget inventorySlot) {
        super.drawTexts(screenElements, inventorySlot);
        screenElements.widget(new TextWidget().setStringSupplier(() -> {
            String tierText = mSetTier == 2 ? StatCollector.translateToLocal("gtsr.gui.tier.steel")
                : StatCollector.translateToLocal("gtsr.gui.tier.bronze");
            return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.tier")
                + EnumChatFormatting.GOLD
                + tierText
                + EnumChatFormatting.RESET;
        }))
            .widget(new TextWidget().setStringSupplier(() -> {
                String steamType = hasSuperheatedSteamInHatch()
                    ? StatCollector.translateToLocal("gtsr.gui.steam_type.superheated")
                    : StatCollector.translateToLocal("gtsr.gui.steam_type.normal");
                return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.steam_type")
                    + EnumChatFormatting.YELLOW
                    + steamType
                    + EnumChatFormatting.RESET;
            }))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.parallel")
                        + EnumChatFormatting.YELLOW
                        + getMaxParallelRecipes()
                        + EnumChatFormatting.RESET))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mSetTier, val -> mSetTier = val));
    }

    @Override
    protected gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui<?> getGui() {
        return new com.miaokatze.gtsr.common.gui.MTEAirCompressorGui(this);
    }

    @Override
    public String[] getInfoData() {
        if (!mMachine) {
            return new String[] {
                EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.air_compressor.type"),
                EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.gui.building") };
        }
        String tierText = mSetTier == 2 ? StatCollector.translateToLocal("gtsr.gui.tier.steel")
            : StatCollector.translateToLocal("gtsr.gui.tier.bronze");
        String steamType = hasSuperheatedSteamInHatch()
            ? StatCollector.translateToLocal("gtsr.gui.steam_type.superheated")
            : StatCollector.translateToLocal("gtsr.gui.steam_type.normal");
        return new String[] {
            EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.air_compressor.type"),
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.tier")
                + EnumChatFormatting.GOLD
                + tierText,
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.steam_type")
                + EnumChatFormatting.YELLOW
                + steamType,
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.parallel")
                + EnumChatFormatting.YELLOW
                + getMaxParallelRecipes() };
    }
}
