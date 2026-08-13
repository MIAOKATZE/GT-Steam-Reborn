package com.miaokatze.gtsr.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlocksTiered;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static com.miaokatze.gtsr.common.api.enums.GTSRHatchElement.PressureSteamCoolingHatch;
import static com.miaokatze.gtsr.common.api.enums.GTSRHatchElement.PressureSteamInputHatch;
import static com.miaokatze.gtsr.common.api.enums.GTSRHatchElement.SteamCoolingHatch;
import static com.miaokatze.gtsr.common.api.enums.GTSRHatchElement.SteamOutputBus;
import static gregtech.api.enums.GTValues.emptyItemStackArray;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.ImmutableList;
import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;
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
import com.miaokatze.gtsr.common.api.progress.GTSRProgressBar;
import com.miaokatze.gtsr.common.api.progress.GTSRProgressEntry;
import com.miaokatze.gtsr.common.machine.base.VoidMinerUtilityShim;
import com.miaokatze.gtsr.common.util.GTSRUtils;

import bwcrossmod.galacticgreg.VoidMinerUtility;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrorRegistry;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.blocks.BlockCasings1;
import gregtech.common.blocks.BlockCasings2;
import gregtech.common.tileentities.machines.IDualInputHatch;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTESteamMultiBlockBase;

public class MTECrustSteamBorer extends MTESteamMultiBlockBase<MTECrustSteamBorer> implements ISurvivalConstructable {

    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static final int HORIZONTAL_OFF_SET = 4;
    private static final int VERTICAL_OFF_SET = 9;
    private static final int DEPTH_OFF_SET = 2;

    protected static final int STEAM_L_EUT = 100;
    public static final int WORK_TIME_TICKS = 500;
    public static final int STEAM_PER_SECOND = STEAM_L_EUT * 20;

    private static IStructureDefinition<MTECrustSteamBorer> STRUCTURE_DEFINITION = null;

    protected int mCountCasing = 0;
    public int mSetTier = -1;
    protected VoidMinerUtility.DropMap dropMap = null;
    protected VoidMinerUtility.DropMap extraDropMap = null;

    public int mCurrentDimId = 0;
    public boolean canMineInCurrentDim = false;
    public String mLastOreName = "";

    public MTECrustSteamBorer(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTECrustSteamBorer(String aName) {
        super(aName);
    }

    /**
     * GTSR 进度词条收集钩子（mixin 惰性触发一次）：注册顺序 = GUI 终端显示顺序（蒸汽消耗 → 工作周期）。
     * 不加 @Override：编译期 GT++ jar 无此方法，运行时由 mixin 注入后多态生效。
     */
    protected void gtsr$collectProgressEntries(GTSRProgressBar bar) {
        bar.registerEntry(
            GTSRProgressEntry.of(
                "steam_input",
                "gtsr.gui.crust_borer.steam_cost",
                "%,.0f L/s",
                EnumChatFormatting.RED,
                () -> STEAM_PER_SECOND));
        bar.registerEntry(
            GTSRProgressEntry.of(
                "work_cycle",
                "gtsr.gui.crust_borer.work_cycle",
                "%.0fs",
                EnumChatFormatting.YELLOW,
                () -> WORK_TIME_TICKS / 20.0));
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTECrustSteamBorer(mName);
    }

    @Override
    public String getMachineType() {
        return "蒸汽地壳钻探器";
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
    public static Integer getFireboxTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockCasings3 && meta == 13) return 1;
        if (block == GregTechAPI.sBlockCasings3 && meta == 14) return 2;
        return null;
    }

    @Nullable
    public static Integer getFrameTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockFrames && meta == Materials.Bronze.mMetaItemSubID) return 1;
        if (block == GregTechAPI.sBlockFrames && meta == Materials.Steel.mMetaItemSubID) return 2;
        return null;
    }

    protected int getCasingTextureID() {
        if (mSetTier >= 2) {
            return ((BlockCasings2) GregTechAPI.sBlockCasings2).getTextureIndex(0);
        }
        return ((BlockCasings1) GregTechAPI.sBlockCasings1).getTextureIndex(10);
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
    public void onValueUpdate(byte aValue) {}

    @Override
    public byte getUpdateData() {
        return 0;
    }

    protected boolean hasSuperheatedSteamInHatch() {
        // v1.10.6：统一走 SteamCoolingSupport（mSteamInputFluids 本地罐 + mInputHatches 3参 drain 探测）
        return SteamCoolingSupport.hasSuperheatedSteam(this);
    }

    @Override
    public IStructureDefinition<MTECrustSteamBorer> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            final int bronzeCasingIndex = ((BlockCasings1) GregTechAPI.sBlockCasings1).getTextureIndex(10);

            STRUCTURE_DEFINITION = StructureDefinition.<MTECrustSteamBorer>builder()
                .addShape(
                    STRUCTURE_PIECE_MAIN,
                    transpose(
                        new String[][] {
                            { "         ", "         ", "         ", "         ", "    F    ", "         ", "         ",
                                "         ", "         " },
                            { "         ", "         ", "         ", "         ", "    F    ", "         ", "         ",
                                "         ", "         " },
                            { "         ", "         ", "         ", "         ", "    F    ", "         ", "         ",
                                "         ", "         " },
                            { "         ", "         ", "         ", "         ", "    F    ", "         ", "         ",
                                "         ", "         " },
                            { "         ", "         ", "         ", "    F    ", "   FCF   ", "    F    ", "         ",
                                "         ", "         " },
                            { "         ", "         ", "         ", "    F    ", "   FCF   ", "    F    ", "         ",
                                "         ", "         " },
                            { "         ", "         ", "         ", "    F    ", "   FCF   ", "    F    ", "         ",
                                "         ", "         " },
                            { "         ", "         ", "         ", "   EFE   ", "   FCF   ", "   EFE   ", "         ",
                                "         ", "         " },
                            { "         ", "         ", "  FD DF  ", "  DCBCD  ", "   BCB   ", "  DCBCD  ", "  FD DF  ",
                                "         ", "         " },
                            { "         ", "   B B   ", "  FB~BF  ", " BB   BB ", "  F C F  ", " BB   BB ", "  FBFBF  ",
                                "   B B   ", "         " },
                            { "   B B   ", "  BBBBB  ", " BFFFFFB ", "BBF   FBB", " BF C FB ", "BBF   FBB", " BFFFFFB ",
                                "  BBBBB  ", "   B B   " } }))
                .addElement(
                    'B',
                    ofChain(
                        // casing-first: NEI 投影优先渲染外壳；真实 hatch 坐标上 casing 匹配失败后继续匹配 hatch adder。
                        onElementPass(
                            MTECrustSteamBorer::onCasingAdded,
                            ofBlocksTiered(
                                MTECrustSteamBorer::getCasingTier,
                                ImmutableList.of(
                                    Pair.of(GregTechAPI.sBlockCasings1, 10),
                                    Pair.of(GregTechAPI.sBlockCasings2, 0)),
                                -1,
                                (MTECrustSteamBorer t, Integer tier) -> t.mSetTier = tier,
                                (MTECrustSteamBorer t) -> t.mSetTier)),
                        // Use atLeast(PressureSteamInputHatch) instead of hatchIds(...). The hatch element's
                        // mteBlacklist() excludes MTEHatchPressureSteamInput.class, preventing NEI from rendering
                        // the pressure steam hatch at every casing position.
                        buildHatchAdder(MTECrustSteamBorer.class).atLeast(PressureSteamInputHatch)
                            .casingIndex(bronzeCasingIndex)
                            .hint(1)
                            .shouldReject(t -> !t.mSteamInputFluids.isEmpty() && !t.mInputHatches.isEmpty())
                            .build(),
                        buildHatchAdder(MTECrustSteamBorer.class).atLeast(SteamOutputBus)
                            .casingIndex(bronzeCasingIndex)
                            .hint(1)
                            .build(),
                        // v1.9.40 新增：冷却仓元素（可选）。蒸汽消耗的冷却产物（普通→蒸馏水 160:1、
                        // 过热→蒸汽 1:1）由 mixin 推入对应冷却仓，此前结构无此元素导致产物滞留/丢失。
                        buildHatchAdder(MTECrustSteamBorer.class).atLeast(SteamCoolingHatch, PressureSteamCoolingHatch)
                            .casingIndex(bronzeCasingIndex)
                            .hint(1)
                            .build()))
                .addElement(
                    'C',
                    onElementPass(
                        MTECrustSteamBorer::onCasingAdded,
                        ofBlocksTiered(
                            MTECrustSteamBorer::getPipeTier,
                            ImmutableList
                                .of(Pair.of(GregTechAPI.sBlockCasings2, 12), Pair.of(GregTechAPI.sBlockCasings2, 13)),
                            -1,
                            (MTECrustSteamBorer t, Integer tier) -> { if (tier > t.mSetTier) t.mSetTier = tier; },
                            (MTECrustSteamBorer t) -> t.mSetTier)))
                .addElement(
                    'D',
                    onElementPass(
                        MTECrustSteamBorer::onCasingAdded,
                        ofBlocksTiered(
                            MTECrustSteamBorer::getGearTier,
                            ImmutableList
                                .of(Pair.of(GregTechAPI.sBlockCasings2, 2), Pair.of(GregTechAPI.sBlockCasings2, 3)),
                            -1,
                            (MTECrustSteamBorer t, Integer tier) -> { if (tier > t.mSetTier) t.mSetTier = tier; },
                            (MTECrustSteamBorer t) -> t.mSetTier)))
                .addElement(
                    'E',
                    onElementPass(
                        MTECrustSteamBorer::onCasingAdded,
                        ofBlocksTiered(
                            MTECrustSteamBorer::getFireboxTier,
                            ImmutableList
                                .of(Pair.of(GregTechAPI.sBlockCasings3, 13), Pair.of(GregTechAPI.sBlockCasings3, 14)),
                            -1,
                            (MTECrustSteamBorer t, Integer tier) -> { if (tier > t.mSetTier) t.mSetTier = tier; },
                            (MTECrustSteamBorer t) -> t.mSetTier)))
                .addElement(
                    'F',
                    onElementPass(
                        MTECrustSteamBorer::onCasingAdded,
                        ofBlocksTiered(
                            MTECrustSteamBorer::getFrameTier,
                            ImmutableList.of(
                                Pair.of(GregTechAPI.sBlockFrames, Materials.Bronze.mMetaItemSubID),
                                Pair.of(GregTechAPI.sBlockFrames, Materials.Steel.mMetaItemSubID)),
                            -1,
                            (MTECrustSteamBorer t, Integer tier) -> { if (tier > t.mSetTier) t.mSetTier = tier; },
                            (MTECrustSteamBorer t) -> t.mSetTier)))
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    private void onCasingAdded() {
        mCountCasing++;
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
        mCountCasing = 0;
        mSetTier = -1;

        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET, errors)) {
            mSetTier = -1;
            return;
        }

        // 取消双注册后，蒸汽输出总线只在 mSteamOutputs 中，需要合并计数
        // v1.9.40 修复：输出数量 ==1 → >=1（蒸汽输入仓数量由结构 shouldReject 保证，此处仅要求存在）
        if ((this.mSteamInputFluids.size() < 1 && this.mInputHatches.size() < 1)
            || (this.mOutputBusses.size() + this.mSteamOutputs.size()) < 1) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            mSetTier = -1;
            return;
        }

        updateAllHatchTextures();
    }

    @Override
    public void onFirstTick(IGregTechTileEntity aBaseMetaTileEntity) {
        super.onFirstTick(aBaseMetaTileEntity);
        mCurrentDimId = getBaseMetaTileEntity().getWorld().provider.dimensionId;
        canMineInCurrentDim = isValidDimension(mCurrentDimId);
        if (canMineInCurrentDim) {
            calculateDropMap();
        }
    }

    protected boolean isValidDimension(int dimId) {
        return dimId == 0 || dimId == -1;
    }

    protected void calculateDropMap() {
        String dimName = VoidMinerUtilityShim.dimIdToName(mCurrentDimId);
        if (dimName == null) {
            dropMap = new VoidMinerUtility.DropMap();
            extraDropMap = new VoidMinerUtility.DropMap();
            return;
        }
        dropMap = VoidMinerUtilityShim.getDropMap(dimName);
        extraDropMap = VoidMinerUtilityShim.getExtraDropMap(dimName);
        dropMap.isDistributionCached(extraDropMap);
    }

    @Override
    public CheckRecipeResult checkProcessing() {
        if (!canMineInCurrentDim) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        if (getTotalSteamStored() > 0) {
            lEUt = -STEAM_L_EUT;
            mMaxProgresstime = WORK_TIME_TICKS;
            // 显式置满效率：自定义 checkProcessing 不经标准流程的 mEfficiency 初始化，
            // 父类 onRunningTick 按 -lEUt*10000/max(1000,mEfficiency) 扣蒸汽，
            // 效率 <=1000 时 10 倍消耗且缺汽停机归零效率形成恶性循环（同热解机 bug）
            mEfficiency = 10000;
            mEfficiencyIncrease = 10000;
            mOutputItems = emptyItemStackArray;
            updateSlots();
            return CheckRecipeResultRegistry.SUCCESSFUL;
        }

        return CheckRecipeResultRegistry.NO_RECIPE;
    }

    @Override
    protected void outputAfterRecipe() {
        if (dropMap != null && dropMap.getTotalWeight() > 0) {
            GTUtility.ItemId oreId = dropMap.nextOre();
            if (oreId != null) {
                ItemStack oreStack = oreId.getItemStack();
                if (oreStack != null) {
                    addOutputPartial(oreStack);
                    mLastOreName = oreStack.getDisplayName();
                }
            }
        }
    }

    @Override
    public int getMaxEfficiency(ItemStack aStack) {
        return 10000;
    }

    @Override
    public int getTierRecipes() {
        return 0;
    }

    @Override
    public boolean supportsPowerPanel() {
        return false;
    }

    @Override
    protected IIconContainer getInactiveOverlay() {
        return Textures.BlockIcons.OVERLAY_FRONT_ORE_DRILL;
    }

    @Override
    protected IIconContainer getActiveOverlay() {
        return Textures.BlockIcons.OVERLAY_FRONT_ORE_DRILL_ACTIVE;
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
        tt.addMachineType(StatCollector.translateToLocal("gtsr.tooltip.crust_borer.type"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.crust_borer.desc"))
            .addInfo(EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.crust_borer.desc_2"))
            .addSeparator()
            .addInfo(
                EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.tooltip.shared.steam_cost")
                    + EnumChatFormatting.WHITE
                    + " 2000 L/s")
            .addInfo(
                EnumChatFormatting.GREEN + StatCollector.translateToLocal("gtsr.tooltip.shared.superheated_quadruples"))
            .beginStructureBlock(9, 11, 9, false)
            .addController(StatCollector.translateToLocal("gtsr.tooltip.crust_borer.ctrl"))
            .addOutputBus(StatCollector.translateToLocal("gtsr.tooltip.crust_borer.output_bus"), 1)
            .addOtherStructurePart(
                StatCollector.translateToLocal("gtsr.tooltip.crust_borer.steam_input"),
                StatCollector.translateToLocal("gtsr.tooltip.shared.any_casing"),
                1)
            .addStructureInfo("")
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.casing"), 48, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.frame"), 43, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.gear_box"), 8, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.pipe"), 11, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.firebox"), 4, false)
            .addStructureInfo(
                EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.tooltip.shared.parallel")
                    + ": "
                    + EnumChatFormatting.GOLD
                    + "1")
            .addStructureHint("gtsr.tooltip.shared.no_maintenance")
            .addStructureHint("gtsr.tooltip.shared.optional_cooling")
            .toolTipFinisher(GTSRUtils.getAddedByLine());
        return tt;
    }

    @Override
    protected IAlignmentLimits getInitialAlignmentLimits() {
        return (d, r, f) -> d.offsetY == 0 && r.isNotRotated() && !f.isVerticallyFliped();
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setInteger("mCurrentDimId", mCurrentDimId);
        aNBT.setBoolean("canMineInCurrentDim", canMineInCurrentDim);
        aNBT.setString("mLastOreName", mLastOreName);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mCurrentDimId = aNBT.getInteger("mCurrentDimId");
        canMineInCurrentDim = aNBT.getBoolean("canMineInCurrentDim");
        mLastOreName = aNBT.getString("mLastOreName");
        if (canMineInCurrentDim) {
            calculateDropMap();
        }
    }

    @Deprecated
    @Override
    protected void drawTexts(DynamicPositionedColumn screenElements, SlotWidget inventorySlot) {
        super.drawTexts(screenElements, inventorySlot);
        screenElements
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.tier")
                        + EnumChatFormatting.GOLD
                        + (mSetTier == 2 ? StatCollector.translateToLocal("gtsr.gui.tier.steel")
                            : mSetTier == 1 ? StatCollector.translateToLocal("gtsr.gui.tier.bronze") : "None")))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mSetTier, val -> mSetTier = val))
            .widget(new TextWidget().setStringSupplier(() -> {
                String statusText;
                if (!canMineInCurrentDim) {
                    statusText = EnumChatFormatting.RED
                        + StatCollector.translateToLocal("gtsr.gui.crust_borer.invalid_dim");
                } else if (mMaxProgresstime <= 0) {
                    statusText = EnumChatFormatting.YELLOW
                        + StatCollector.translateToLocal("gtsr.gui.crust_borer.no_steam");
                } else {
                    statusText = EnumChatFormatting.GREEN + StatCollector.translateToLocal("gtsr.gui.status.running");
                }
                return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.status") + statusText;
            }))
            .widget(new FakeSyncWidget.BooleanSyncer(() -> canMineInCurrentDim, val -> canMineInCurrentDim = val))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mMaxProgresstime, val -> mMaxProgresstime = val))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.crust_borer.dimension")
                        + (canMineInCurrentDim ? EnumChatFormatting.GREEN + String.valueOf(mCurrentDimId)
                            : EnumChatFormatting.RED
                                + StatCollector.translateToLocal("gtsr.gui.crust_borer.invalid_dim"))))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mCurrentDimId, val -> mCurrentDimId = val))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.crust_borer.mining")
                        + (mLastOreName != null && !mLastOreName.isEmpty() ? EnumChatFormatting.GREEN + mLastOreName
                            : EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.gui.none"))))
            .widget(new FakeSyncWidget.StringSyncer(() -> mLastOreName, val -> mLastOreName = val))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.crust_borer.steam_cost")
                        + EnumChatFormatting.RED
                        + NumberFormatUtil.formatNumber(STEAM_PER_SECOND)
                        + " L/s"))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.crust_borer.work_cycle")
                        + EnumChatFormatting.YELLOW
                        + (WORK_TIME_TICKS / 20)
                        + "s"));
    }

    @Override
    protected gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui<?> getGui() {
        return new com.miaokatze.gtsr.common.gui.MTECrustSteamBorerGui(this);
    }

    @Override
    public String[] getInfoData() {
        if (!mMachine) {
            return new String[] {
                EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.crust_borer.type"),
                EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.gui.building") };
        }
        String dimInfo = canMineInCurrentDim ? EnumChatFormatting.GREEN + String.valueOf(mCurrentDimId)
            : EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.gui.crust_borer.invalid_dim");
        String oreInfo = mLastOreName != null && !mLastOreName.isEmpty() ? EnumChatFormatting.GREEN + mLastOreName
            : EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.gui.none");
        boolean boosted = hasSuperheatedSteamInHatch();
        int workTime = boosted ? WORK_TIME_TICKS / 4 : WORK_TIME_TICKS;
        String statusText;
        if (!canMineInCurrentDim) {
            statusText = EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.gui.crust_borer.invalid_dim");
        } else if (getTotalSteamStored() <= 0) {
            statusText = EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.crust_borer.no_steam");
        } else {
            statusText = EnumChatFormatting.GREEN + StatCollector.translateToLocal("gtsr.gui.status.running");
        }
        return new String[] { EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.crust_borer.type"),
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.tier")
                + EnumChatFormatting.GOLD
                + StatCollector.translateToLocal("gtsr.gui.tier.bronze"),
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.status") + statusText,
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.crust_borer.dimension") + dimInfo,
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.crust_borer.mining") + oreInfo,
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.crust_borer.steam_cost")
                + EnumChatFormatting.RED
                + NumberFormatUtil.formatNumber(STEAM_PER_SECOND)
                + " L/s",
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.crust_borer.work_cycle")
                + EnumChatFormatting.YELLOW
                + (workTime / 20)
                + "s" };
    }
}
