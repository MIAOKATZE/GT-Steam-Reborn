package com.miaokatze.gtsr.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlocksTiered;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.HatchElement.InputBus;
import static gregtech.api.enums.HatchElement.OutputBus;
import static gregtech.api.enums.HatchElement.OutputHatch;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.ImmutableList;
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
import com.miaokatze.gtsr.common.gui.MTELargeCokeOvenGui;
import com.miaokatze.gtsr.common.machine.base.MTEGTSRMultiBlockBase;
import com.miaokatze.gtsr.common.util.GTSRUtils;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrorRegistry;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.OverclockCalculator;
import gregtech.common.blocks.BlockCasings1;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;
import gregtech.common.tileentities.machines.IDualInputHatch;

public class MTELargeCokeOven extends MTEGTSRMultiBlockBase<MTELargeCokeOven>
    implements IConstructable, ISurvivalConstructable {

    private static final int TICKS_PER_SECOND = 20;
    private static final int TIER_UNKNOWN = -1;
    private static final int TIER_BRONZE = 1;
    private static final int TIER_STEEL = 2;
    private static final String OUTPUT_PROTECTION_NBT_KEY = "gtsr.cokeOutputProtection";

    // 热量以 1.0 表示满温；配方仅在启动时采样。
    private static final double HEAT_UP_PER_SECOND = 0.0001d; // 运行中：+0.01%/s
    private static final double HEAT_DOWN_PER_SECOND = 0.001d; // 停机时：-0.1%/s
    // 各等级并行数上限
    private static final int MAX_PARALLEL_T1 = 24; // 青铜
    private static final int MAX_PARALLEL_T2 = 64; // 钢
    private static final double STEEL_DURATION_REDUCTION = 0.25d;
    private static final double MAX_HEAT_DURATION_REDUCTION = 0.50d;
    private static final double MAX_TOTAL_DURATION_REDUCTION = 0.75d;

    public double mHeat = 0.0d;
    // 默认值 -1 表示「未确定」，与 checkMachine() 中的重置值一致。
    // 客户端在收到第一次 onValueUpdate 之前会保持该值，
    // 此时 getCasingTextureID() 返回等级1（青铜）贴图，避免显示错误的等级2底材。
    public int mTier = -1;

    // v1.9.39 修复：样板输入仓（MTEHatchCraftingInputME/Slave，implements IDualInputHatch）重定向到
    // mDualInputHatches（仿 GT5U addInputBusToMachineList）。此前裸 instanceof 会把样板仓收进
    // mInputBusses，随后被 GT5U getAllStoredInputs 对 CraftingInputME 的显式跳过逻辑忽略，
    // 导致样板输入静默失效（结构能成型、配方永不消耗）。
    @Override
    public boolean addInputBusToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity == null) return false;
        if (aMetaTileEntity instanceof IDualInputHatch dualHatch) {
            dualHatch.updateTexture(aBaseCasingIndex);
            dualHatch.updateCraftingIcon(this.getMachineCraftingIcon());
            if (!mDualInputHatches.contains(dualHatch)) {
                mDualInputHatches.add(dualHatch);
            }
            return true;
        }
        if (aMetaTileEntity instanceof MTEHatchInputBus hatch) {
            hatch.mRecipeMap = getRecipeMap();
            hatch.updateTexture(aBaseCasingIndex);
            return mInputBusses.add(hatch);
        }
        return false;
    }

    @Override
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

    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static final int HORIZONTAL_OFF_SET = 1;
    private static final int VERTICAL_OFF_SET = 5;
    private static final int DEPTH_OFF_SET = 0;
    private static IStructureDefinition<MTELargeCokeOven> STRUCTURE_DEFINITION = null;

    public MTELargeCokeOven(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        registerProgressEntries();
    }

    public MTELargeCokeOven(String aName) {
        super(aName);
        registerProgressEntries();
    }

    // GTSR 进度词条：注册顺序 = GUI 终端显示顺序（炉温；状态/配方时长/并行为文本行保留在 GUI）
    private void registerProgressEntries() {
        registerEntry(
            "temperature",
            "gtsr.gui.coke_oven.temperature",
            "%.1f%%",
            EnumChatFormatting.RED,
            () -> getHeat() * 100.0d);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTELargeCokeOven(mName);
    }

    @Override
    public boolean getDefaultHasMaintenanceChecks() {
        return false;
    }

    @Override
    public boolean shouldDisplayCheckRecipeResult() {
        return true;
    }

    @Override
    public boolean showRecipeTextInGUI() {
        return true;
    }

    @Override
    public boolean supportsVoidProtection() {
        return true;
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        // 切换为 GT5U 原版焦炉配方表（同 MTECokeOven.java）
        // 包含煤炭→焦煤、煤块→焦煤块、原木→木炭、甘蔗/仙人掌等共8个配方
        return RecipeMaps.cokeOvenRecipes;
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
        if (block == GregTechAPI.sBlockFrames && meta == gregtech.api.enums.Materials.Bronze.mMetaItemSubID) return 1;
        if (block == GregTechAPI.sBlockFrames && meta == gregtech.api.enums.Materials.Steel.mMetaItemSubID) return 2;
        return null;
    }

    @Override
    public IStructureDefinition<MTELargeCokeOven> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) STRUCTURE_DEFINITION = createStructureDefinition();
        return STRUCTURE_DEFINITION;
    }

    /** 所有分级部件共享 mTier，StructureLib 拒绝混用青铜和钢部件。 */
    private static IStructureDefinition<MTELargeCokeOven> createStructureDefinition() {
        final int bronzeCasingIndex = ((BlockCasings1) GregTechAPI.sBlockCasings1).getTextureIndex(10);

        return StructureDefinition.<MTELargeCokeOven>builder()
            .addShape(
                STRUCTURE_PIECE_MAIN,
                transpose(
                    new String[][] { { "   GFFF", "    F F", "   GFFF" }, { "   GFFF", "    F F", "   GFFF" },
                        { "BBBGFFF", "BBBBF F", "BBBGFFF" }, { "BBBGFFF", "BCCCC F", "BBBGFFF" },
                        { "BBBGFFF", "BCCCC F", "BBBGFFF" }, { "B~BGFFF", "BCCCC F", "BBBGFFF" },
                        { "BBBGEEE", "BBBDEEE", "BBBGEEE" } }))
            .addElement(
                'B',
                ofChain(
                    // casing-first: NEI 投影优先渲染外壳；真实 hatch 坐标上 casing 匹配失败后继续匹配 hatch adder。
                    ofBlocksTiered(
                        MTELargeCokeOven::getCasingTier,
                        ImmutableList
                            .of(Pair.of(GregTechAPI.sBlockCasings1, 10), Pair.of(GregTechAPI.sBlockCasings2, 0)),
                        -1,
                        (MTELargeCokeOven t, Integer tier) -> t.mTier = tier,
                        (MTELargeCokeOven t) -> t.mTier),
                    buildHatchAdder(MTELargeCokeOven.class).atLeast(InputBus, OutputBus)
                        .casingIndex(bronzeCasingIndex)
                        .hint(1)
                        .build(),
                    buildHatchAdder(MTELargeCokeOven.class).atLeast(OutputHatch)
                        .casingIndex(bronzeCasingIndex)
                        .hint(1)
                        .build()))
            .addElement(
                'C',
                ofBlocksTiered(
                    MTELargeCokeOven::getPipeTier,
                    ImmutableList.of(Pair.of(GregTechAPI.sBlockCasings2, 12), Pair.of(GregTechAPI.sBlockCasings2, 13)),
                    -1,
                    (MTELargeCokeOven t, Integer tier) -> { if (tier > t.mTier) t.mTier = tier; },
                    (MTELargeCokeOven t) -> t.mTier))
            .addElement(
                'D',
                ofBlocksTiered(
                    MTELargeCokeOven::getGearTier,
                    ImmutableList.of(Pair.of(GregTechAPI.sBlockCasings2, 2), Pair.of(GregTechAPI.sBlockCasings2, 3)),
                    -1,
                    (MTELargeCokeOven t, Integer tier) -> { if (tier > t.mTier) t.mTier = tier; },
                    (MTELargeCokeOven t) -> t.mTier))
            .addElement(
                'E',
                ofBlocksTiered(
                    MTELargeCokeOven::getFireboxTier,
                    ImmutableList.of(Pair.of(GregTechAPI.sBlockCasings3, 13), Pair.of(GregTechAPI.sBlockCasings3, 14)),
                    -1,
                    (MTELargeCokeOven t, Integer tier) -> { if (tier > t.mTier) t.mTier = tier; },
                    (MTELargeCokeOven t) -> t.mTier))
            .addElement('F', ofBlock(GregTechAPI.sBlockCasings4, 15))
            .addElement(
                'G',
                ofBlocksTiered(
                    MTELargeCokeOven::getFrameTier,
                    ImmutableList.of(
                        Pair.of(GregTechAPI.sBlockFrames, gregtech.api.enums.Materials.Bronze.mMetaItemSubID),
                        Pair.of(GregTechAPI.sBlockFrames, gregtech.api.enums.Materials.Steel.mMetaItemSubID)),
                    -1,
                    (MTELargeCokeOven t, Integer tier) -> { if (tier > t.mTier) t.mTier = tier; },
                    (MTELargeCokeOven t) -> t.mTier))
            .build();
    }

    private void updateHatchTexture() {
        int textureID = getCasingTextureID();
        for (MTEHatch h : mInputBusses) h.updateTexture(textureID);
        for (MTEHatch h : mOutputBusses) h.updateTexture(textureID);
        for (MTEHatch h : mOutputHatches) h.updateTexture(textureID);
        // v1.9.41 修复：补 mDualInputHatches（样板仓经自定义 adder 重定向至此），
        // 此前 tier2 时样板仓底材停滞青铜
        if (mDualInputHatches != null) {
            for (IDualInputHatch dualHatch : mDualInputHatches) {
                if (dualHatch != null) dualHatch.updateTexture(textureID);
            }
        }
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal("gtsr.tooltip.coke_oven.type"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.coke_oven.desc"))
            .addInfo(EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.coke_oven.desc_2"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.coke_oven.desc2"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.coke_oven.desc3"))
            .addSeparator()
            .addInfo(EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.tooltip.coke_oven.formula"))
            .addInfo(EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.coke_oven.accel"))
            // [GT-compat] beta 兼容层（beta1/beta2/beta3）：beta-3 起始参数序为 (w,h,l)，实参已按 beta-3 语义排列
            .beginStructureBlock(7, 7, 3, true)
            .addController(StatCollector.translateToLocal("gtsr.tooltip.coke_oven.ctrl"))
            .addInputBus(StatCollector.translateToLocal("gtsr.tooltip.coke_oven.input_bus"), 1)
            .addOutputBus(StatCollector.translateToLocal("gtsr.tooltip.coke_oven.output_bus"), 1)
            .addOutputHatch(StatCollector.translateToLocal("gtsr.tooltip.coke_oven.output_hatch"), 1)
            .addStructureInfo("")
            .addStructureInfo(
                EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.shared.bronze_steel_tier"))
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.casing"), 39, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.firebox"), 9, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.pipe"), 12, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.gear_box"), 1, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.frame"), 12, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.firebrick"), 37, false)
            .addStructureInfo(
                EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.tooltip.coke_oven.parallel"))
            .addStructureHint("gtsr.tooltip.shared.no_maintenance")
            .addInfo(GTSRUtils.getAddedByLine())
            .toolTipFinisher();
        return tt;
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
        // Note: checkStructure() already calls clearHatches() before calling checkMachine(),
        // so we must NOT call clearHatches() here again, otherwise checkPiece()-registered
        // hatches will be cleared before we can count them.
        mTier = -1;

        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET, errors)) {
            mTier = -1;
            return;
        }

        if (mTier < 1) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            mTier = -1;
            return;
        }

        // v1.9.39 修复：样板输入仓计入输入判定（重定向后位于 mDualInputHatches，不再计入 mInputBusses）
        if ((mInputBusses.isEmpty() && mDualInputHatches.isEmpty()) || mOutputBusses.isEmpty()) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            mTier = -1;
            return;
        }

        updateHatchTexture();
    }

    @Override
    public int getMaxParallelRecipes() {
        if (mTier == TIER_STEEL) return MAX_PARALLEL_T2;
        return MAX_PARALLEL_T1;
    }

    @Override
    protected ProcessingLogic createProcessingLogic() {
        return new ProcessingLogic() {

            @Nonnull
            @Override
            protected CheckRecipeResult validateRecipe(@Nonnull GTRecipe recipe) {
                // 焦炉配方 eut=0，无需电力检查；保留校验以兼容未来非零 eut 配方
                if (availableVoltage < recipe.mEUt) {
                    return CheckRecipeResultRegistry.insufficientPower(recipe.mEUt);
                }
                return CheckRecipeResultRegistry.SUCCESSFUL;
            }

            @Override
            @Nonnull
            protected OverclockCalculator createOverclockCalculator(@Nonnull GTRecipe recipe) {
                // 无电炉只按炉温缩时，不将不足一 tick 的加速转换为额外并行。
                return super.createOverclockCalculator(recipe).setNoOverclock(true);
            }
        }.setMaxParallelSupplier(this::getMaxParallelRecipes)
            .setSpeedBonusSupplier(this::getRecipeDurationMultiplier);
    }

    /** 只供 GT5U 在配方检查时采样；已开始批次使用父类最终 mMaxProgresstime。 */
    private double getRecipeDurationMultiplier() {
        double tierReduction = mTier == TIER_STEEL ? STEEL_DURATION_REDUCTION : 0.0d;
        double heatReduction = MAX_HEAT_DURATION_REDUCTION * getHeat();
        return 1.0d - Math.min(MAX_TOTAL_DURATION_REDUCTION, tierReduction + heatReduction);
    }

    @Override
    public void onPostTick(IGregTechTileEntity tile, long tick) {
        // GT5U 独占配方检索、输入消耗、进度推进及输出；这里只维护焦炉热量。
        super.onPostTick(tile, tick);
        if (!tile.isServerSide()) return;
        updateHeat(tile.isActive());
    }

    private void updateHeat(boolean active) {
        if (!mMachine && mStartUpCheck > 0) return;
        boolean working = mMachine && (mMaxProgresstime > 0 || active);
        double changePerSecond = working ? HEAT_UP_PER_SECOND : -HEAT_DOWN_PER_SECOND;
        setHeat(getHeat() + changePerSecond / TICKS_PER_SECOND);
    }

    public double getHeat() {
        return normalizedHeat(mHeat);
    }

    public void setHeat(double heat) {
        mHeat = normalizedHeat(heat);
    }

    public int getCokeTier() {
        return mTier;
    }

    public void setCokeTier(int tier) {
        mTier = tier == TIER_BRONZE || tier == TIER_STEEL ? tier : TIER_UNKNOWN;
    }

    public String getTemperatureText() {
        return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.coke_oven.temperature")
            + EnumChatFormatting.RED
            + String.format(Locale.ROOT, "%.1f%%", getHeat() * 100.0d)
            + EnumChatFormatting.RESET;
    }

    public String getStatusText() {
        String key;
        EnumChatFormatting color;
        if (mMaxProgresstime > 0) {
            key = "gtsr.gui.status.running";
            color = EnumChatFormatting.AQUA;
        } else if (getHeat() > 0.0d) {
            key = "gtsr.gui.coke_oven.status.cooling";
            color = EnumChatFormatting.BLUE;
        } else {
            key = "gtsr.gui.status.idle";
            color = EnumChatFormatting.WHITE;
        }
        return EnumChatFormatting.YELLOW + StatCollector.translateToLocal(
            "gtsr.gui.status") + " " + color + StatCollector.translateToLocal(key) + EnumChatFormatting.RESET;
    }

    /** 与父 GUI 进度分母相同，展示本批最终总时长，不用当前炉温重算。 */
    public String getRecipeTimeText() {
        String value = mMaxProgresstime > 0
            ? EnumChatFormatting.GOLD
                + String.format(Locale.ROOT, "%.2fs", mMaxProgresstime / (double) TICKS_PER_SECOND)
            : EnumChatFormatting.WHITE + "-";
        return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.coke_oven.recipe_time")
            + value
            + EnumChatFormatting.RESET;
    }

    public String getParallelText() {
        return EnumChatFormatting.YELLOW + StatCollector.translateToLocal(
            "gtsr.gui.parallel") + " " + EnumChatFormatting.GOLD + getMaxParallelRecipes() + EnumChatFormatting.RESET;
    }

    @Override
    protected @Nonnull MTEMultiBlockBaseGui<?> getGui() {
        return new MTELargeCokeOvenGui(this);
    }

    /** ModularUI 1 兼容适配；进度和总时长同步由父 GUI 提供。 */
    @Deprecated
    @Override
    protected void drawTexts(DynamicPositionedColumn screenElements, SlotWidget inventorySlot) {
        super.drawTexts(screenElements, inventorySlot);
        screenElements.widget(new TextWidget().setStringSupplier(this::getTemperatureText))
            .widget(new TextWidget().setStringSupplier(this::getStatusText))
            .widget(new TextWidget().setStringSupplier(this::getRecipeTimeText))
            .widget(new TextWidget().setStringSupplier(this::getParallelText))
            .widget(new FakeSyncWidget.DoubleSyncer(this::getHeat, this::setHeat))
            .widget(new FakeSyncWidget.IntegerSyncer(this::getCokeTier, this::setCokeTier));
    }

    @Override
    public String[] getInfoData() {
        ArrayList<String> info = new ArrayList<>();
        info.add(EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.coke_oven.type"));
        if (!mMachine) {
            info.add(EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.gui.building"));
        } else {
            info.add(getTemperatureText());
            info.add(getStatusText());
            info.add(getRecipeTimeText());
            info.add(getParallelText());
        }
        return info.toArray(new String[0]);
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setDouble("mHeat", getHeat());
        aNBT.setInteger("mTier", mTier);
        aNBT.setBoolean(OUTPUT_PROTECTION_NBT_KEY, true);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        setHeat(aNBT.getDouble("mHeat"));
        setCokeTier(aNBT.getInteger("mTier"));
        // 旧焦炉不支持保护，其默认 VOID_ALL 并非玩家主动选择；首次升级恢复保护。
        // 此后保留玩家在 GT 标准界面中明确选择的丢弃模式。
        if (!aNBT.getBoolean(OUTPUT_PROTECTION_NBT_KEY)) setVoidingMode(getDefaultVoidingMode());
    }

    private static double normalizedHeat(double heat) {
        return Double.isFinite(heat) ? Math.max(0.0d, Math.min(1.0d, heat)) : 0.0d;
    }

    @Override
    public IAlignmentLimits getAlignmentLimits() {
        return IAlignmentLimits.UPRIGHT;
    }

    protected int getCasingTextureID() {
        // v1.10.62：== 2 而非 >= 2——GT5U 字节同步通道 & 0x7F 掩码使未成型值 -1（0xFF）在客户端
        // 回绕为 127，>= 2 误判为钢外壳（刚放置即显示等级2底材）；== 2 仅真实等级2命中钢贴图
        if (mTier == 2) {
            return ((gregtech.common.blocks.BlockCasings2) GregTechAPI.sBlockCasings2).getTextureIndex(0);
        }
        return ((gregtech.common.blocks.BlockCasings1) GregTechAPI.sBlockCasings1).getTextureIndex(10);
    }

    /**
     * 客户端-服务端同步：把服务端的 mTier 通过单字节同步给客户端。
     * <p>
     * 修复问题：等级2大型焦炉退出重进存档后，控制器贴图错误地显示为等级1（青铜）底材。
     * 原因：客户端 mTier 默认值 1，未收到服务端 mTier=2 的同步，getCasingTextureID() 走等级1分支。
     * <p>
     * 通过 onValueUpdate/getUpdateData 实现 GTNH 标准的"无 GUI 持续同步"，
     * 服务端 mTier 变化时会自动推送到客户端，避免玩家必须重新触发结构检测才能恢复贴图。
     * <p>
     * 参考：MTELargeSteamFurnace / MTELargeGeothermalSteamBoiler 等机器的实现模式。
     */
    @Override
    public void onValueUpdate(byte aValue) {
        setCokeTier(aValue);
    }

    @Override
    public byte getUpdateData() {
        return (byte) mTier;
    }

    protected ITexture getFrontOverlay() {
        return TextureFactory.of(Textures.BlockIcons.OVERLAY_FRONT_STEAM_FURNACE);
    }

    protected ITexture getFrontOverlayActive() {
        return TextureFactory.of(Textures.BlockIcons.OVERLAY_FRONT_STEAM_FURNACE_ACTIVE);
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
}
