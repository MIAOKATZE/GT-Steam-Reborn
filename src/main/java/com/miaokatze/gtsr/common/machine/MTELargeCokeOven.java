package com.miaokatze.gtsr.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlocksTiered;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.HatchElement.InputBus;
import static gregtech.api.enums.HatchElement.OutputBus;
import static gregtech.api.enums.HatchElement.OutputHatch;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.ArrayList;
import java.util.List;

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

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
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
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

public class MTELargeCokeOven extends MTEEnhancedMultiBlockBase<MTELargeCokeOven>
    implements IConstructable, ISurvivalConstructable {

    // 炉温升降速率（每秒变化量，1.0 = 100%）
    private static final double HEAT_UP_PER_SECOND = 0.0001d; // 运行中：+0.01%/s
    private static final double HEAT_DOWN_PER_SECOND = 0.001d; // 停机时：-0.1%/s
    // 各等级并行数上限
    private static final int MAX_PARALLEL_T1 = 24; // 青铜
    private static final int MAX_PARALLEL_T2 = 64; // 钢

    public double mHeat = 0.0d;
    // 默认值 -1 表示「未确定」，与 checkMachine() 中的重置值一致。
    // 客户端在收到第一次 onValueUpdate 之前会保持该值，
    // 此时 getCasingTextureID() 返回等级1（青铜）贴图，避免显示错误的等级2底材。
    public int mTier = -1;
    private int mParallel = 0;
    public int mOriginalRecipeTime = 0;

    public boolean addInputBusToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity == null) return false;
        if (aMetaTileEntity instanceof MTEHatchInputBus hatch) {
            hatch.mRecipeMap = getRecipeMap();
            hatch.updateTexture(aBaseCasingIndex);
            return mInputBusses.add(hatch);
        }
        return false;
    }

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
    }

    public MTELargeCokeOven(String aName) {
        super(aName);
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
        if (STRUCTURE_DEFINITION == null) {
            STRUCTURE_DEFINITION = StructureDefinition.<MTELargeCokeOven>builder()
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
                        onElementPass(
                            MTELargeCokeOven::onCasingAdded,
                            ofBlocksTiered(
                                MTELargeCokeOven::getCasingTier,
                                ImmutableList.of(
                                    Pair.of(GregTechAPI.sBlockCasings1, 10),
                                    Pair.of(GregTechAPI.sBlockCasings2, 0)),
                                -1,
                                (MTELargeCokeOven t, Integer tier) -> t.mTier = tier,
                                (MTELargeCokeOven t) -> t.mTier)),
                        buildHatchAdder(MTELargeCokeOven.class).atLeast(InputBus, OutputBus)
                            .casingIndex(10)
                            .hint(1)
                            .build(),
                        buildHatchAdder(MTELargeCokeOven.class).atLeast(OutputHatch)
                            .casingIndex(10)
                            .hint(1)
                            .build()))
                .addElement(
                    'C',
                    onElementPass(
                        MTELargeCokeOven::onCasingAdded,
                        ofBlocksTiered(
                            MTELargeCokeOven::getPipeTier,
                            ImmutableList
                                .of(Pair.of(GregTechAPI.sBlockCasings2, 12), Pair.of(GregTechAPI.sBlockCasings2, 13)),
                            -1,
                            (MTELargeCokeOven t, Integer tier) -> { if (tier > t.mTier) t.mTier = tier; },
                            (MTELargeCokeOven t) -> t.mTier)))
                .addElement(
                    'D',
                    onElementPass(
                        MTELargeCokeOven::onCasingAdded,
                        ofBlocksTiered(
                            MTELargeCokeOven::getGearTier,
                            ImmutableList
                                .of(Pair.of(GregTechAPI.sBlockCasings2, 2), Pair.of(GregTechAPI.sBlockCasings2, 3)),
                            -1,
                            (MTELargeCokeOven t, Integer tier) -> { if (tier > t.mTier) t.mTier = tier; },
                            (MTELargeCokeOven t) -> t.mTier)))
                .addElement(
                    'E',
                    onElementPass(
                        MTELargeCokeOven::onCasingAdded,
                        ofBlocksTiered(
                            MTELargeCokeOven::getFireboxTier,
                            ImmutableList
                                .of(Pair.of(GregTechAPI.sBlockCasings3, 13), Pair.of(GregTechAPI.sBlockCasings3, 14)),
                            -1,
                            (MTELargeCokeOven t, Integer tier) -> { if (tier > t.mTier) t.mTier = tier; },
                            (MTELargeCokeOven t) -> t.mTier)))
                .addElement(
                    'F',
                    onElementPass(MTELargeCokeOven::onCasingAdded, ofBlock(GregTechAPI.sBlockCasings4, 15)))
                .addElement(
                    'G',
                    onElementPass(
                        MTELargeCokeOven::onCasingAdded,
                        ofBlocksTiered(
                            MTELargeCokeOven::getFrameTier,
                            ImmutableList.of(
                                Pair.of(GregTechAPI.sBlockFrames, gregtech.api.enums.Materials.Bronze.mMetaItemSubID),
                                Pair.of(GregTechAPI.sBlockFrames, gregtech.api.enums.Materials.Steel.mMetaItemSubID)),
                            -1,
                            (MTELargeCokeOven t, Integer tier) -> { if (tier > t.mTier) t.mTier = tier; },
                            (MTELargeCokeOven t) -> t.mTier)))
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    private void onCasingAdded() {}

    private void updateHatchTexture() {
        int textureID = getCasingTextureID();
        for (MTEHatch h : mInputBusses) h.updateTexture(textureID);
        for (MTEHatch h : mOutputBusses) h.updateTexture(textureID);
        for (MTEHatch h : mOutputHatches) h.updateTexture(textureID);
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal("gtsr.tooltip.coke_oven.type"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.coke_oven.desc"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.coke_oven.desc2"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.coke_oven.desc3"))
            .addSeparator()
            .addInfo(EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.tooltip.coke_oven.formula"))
            .addInfo(EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.coke_oven.accel"))
            .beginStructureBlock(3, 7, 7, true)
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
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.frame"), 14, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.firebrick"), 45, false)
            .addStructureInfo(
                EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.tooltip.coke_oven.parallel"))
            .addStructureHint("gtsr.tooltip.shared.no_maintenance")
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
            return;
        }

        if (mTier < 1) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }

        if (mInputBusses.size() < 1 || mOutputBusses.size() < 1) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }

        updateHatchTexture();
    }

    @Override
    public int getMaxParallelRecipes() {
        if (mTier >= 2) return MAX_PARALLEL_T2;
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
                // 基础加工速度：青铜（mTier=1）120% → duration×(1/1.2)；
                // 钢（mTier=2）200% → duration×(1/2.0)
                double baseDurationMultiplier = (mTier == 2) ? (1.0 / 2.0) : (1.0 / 1.2);
                // 炉温加速：每1%炉温叠加1%工作速度
                // 工作速度 = 基础速度 × (1 + mHeat)
                // 故 duration 乘数 = 基础速度 / (1 + mHeat)
                double durationModifier = baseDurationMultiplier / (1.0 + mHeat);
                return OverclockCalculator.ofNoOverclock(recipe)
                    .setDurationModifier(durationModifier);
            }
        }.setMaxParallelSupplier(this::getMaxParallelRecipes);
    }

    @Override
    @Nonnull
    public CheckRecipeResult checkProcessing() {
        CheckRecipeResult result = super.checkProcessing();
        if (!result.wasSuccessful()) return result;
        // OverclockCalculator 已在 createOverclockCalculator 中应用了基础倍率与炉温加速，
        // super.checkProcessing() 返回的 mMaxProgresstime 即为最终配方时长，无需再手动重算
        mParallel = processingLogic.getCurrentParallels();
        mOriginalRecipeTime = mMaxProgresstime;
        return result;
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);

        if (!aBaseMetaTileEntity.isServerSide()) return;

        if (mMachine && aTick % 20 == 0) {
            if (mMaxProgresstime > 0) {
                mHeat = Math.min(1.0d, mHeat + HEAT_UP_PER_SECOND);
            } else {
                mHeat = Math.max(0.0d, mHeat - HEAT_DOWN_PER_SECOND);
            }
        }

        aBaseMetaTileEntity.setActive(mMaxProgresstime > 0);
    }

    @Override
    protected @Nonnull MTEMultiBlockBaseGui<?> getGui() {
        return new MTELargeCokeOvenGui(this);
    }

    @Deprecated
    @Override
    protected void drawTexts(DynamicPositionedColumn screenElements, SlotWidget inventorySlot) {
        super.drawTexts(screenElements, inventorySlot);
        screenElements
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.coke_oven.temperature")
                        + EnumChatFormatting.RED
                        + String.format("%.1f%%", mHeat * 100.0d)
                        + EnumChatFormatting.RESET))
            .widget(new TextWidget().setStringSupplier(() -> {
                String statusKey;
                EnumChatFormatting statusColor;
                if (mMaxProgresstime > 0) {
                    statusKey = "gtsr.gui.status.running";
                    statusColor = EnumChatFormatting.AQUA;
                } else if (mHeat > 0) {
                    statusKey = "gtsr.gui.coke_oven.status.heating";
                    statusColor = EnumChatFormatting.GREEN;
                } else {
                    statusKey = "gtsr.gui.status.idle";
                    statusColor = EnumChatFormatting.GRAY;
                }
                return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.status")
                    + " "
                    + statusColor
                    + StatCollector.translateToLocal(statusKey)
                    + EnumChatFormatting.RESET;
            }))
            .widget(new TextWidget().setStringSupplier(() -> {
                // 直接显示当前配方总时长（已含基础倍率与炉温加速），避免二次加速
                if (mMaxProgresstime > 0) {
                    int totalSeconds = mMaxProgresstime / 20;
                    return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.coke_oven.recipe_time")
                        + EnumChatFormatting.GOLD
                        + totalSeconds
                        + "s"
                        + EnumChatFormatting.RESET;
                }
                return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.coke_oven.recipe_time")
                    + EnumChatFormatting.GRAY
                    + "-"
                    + EnumChatFormatting.RESET;
            }))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.parallel")
                        + " "
                        + EnumChatFormatting.GOLD
                        + getMaxParallelRecipes()
                        + EnumChatFormatting.RESET))
            .widget(new FakeSyncWidget.DoubleSyncer(() -> mHeat, val -> mHeat = val))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mMaxProgresstime, val -> mMaxProgresstime = val))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mProgresstime, val -> mProgresstime = val))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mTier, val -> mTier = val))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mOriginalRecipeTime, val -> mOriginalRecipeTime = val));
    }

    @Override
    public String[] getInfoData() {
        ArrayList<String> info = new ArrayList<>();
        info.add(EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.coke_oven.type"));

        if (!mMachine) {
            info.add(EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.gui.building"));
            return info.toArray(new String[0]);
        }

        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.coke_oven.temperature")
                + EnumChatFormatting.RED
                + String.format("%.1f%%", mHeat * 100.0d));

        String statusKey;
        EnumChatFormatting statusColor;
        if (mMaxProgresstime > 0) {
            statusKey = "gtsr.gui.status.running";
            statusColor = EnumChatFormatting.AQUA;
        } else if (mHeat > 0) {
            statusKey = "gtsr.gui.coke_oven.status.heating";
            statusColor = EnumChatFormatting.GREEN;
        } else {
            statusKey = "gtsr.gui.status.idle";
            statusColor = EnumChatFormatting.GRAY;
        }
        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.status")
                + " "
                + statusColor
                + StatCollector.translateToLocal(statusKey));

        if (mMaxProgresstime > 0) {
            int secondsRemaining = (mMaxProgresstime - mProgresstime) / 20;
            info.add(
                EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.coke_oven.recipe_time")
                    + EnumChatFormatting.GOLD
                    + secondsRemaining
                    + "s");
        }

        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.parallel")
                + " "
                + EnumChatFormatting.GOLD
                + (mTier >= 2 ? "24/64" : "24"));

        return info.toArray(new String[0]);
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setDouble("mHeat", mHeat);
        aNBT.setInteger("mTier", mTier);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mHeat = aNBT.getDouble("mHeat");
        mTier = aNBT.getInteger("mTier");
    }

    @Override
    public IAlignmentLimits getAlignmentLimits() {
        return IAlignmentLimits.UPRIGHT;
    }

    protected int getCasingTextureID() {
        if (mTier >= 2) {
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
        mTier = aValue;
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
