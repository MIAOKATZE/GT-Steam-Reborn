package com.miaokatze.gtsr.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static com.miaokatze.gtsr.common.api.enums.GTSRHatchElement.SteamOutputBus;
import static com.miaokatze.gtsr.common.structure.GTSRStructureUtility.minimumCasings;
import static gregtech.api.enums.HatchElement.InputHatch;
import static gregtech.api.enums.HatchElement.OutputHatch;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_LARGE_CHEMICAL_REACTOR;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_LARGE_CHEMICAL_REACTOR_ACTIVE;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizons.modularui.common.widget.DynamicPositionedColumn;
import com.gtnewhorizons.modularui.common.widget.FakeSyncWidget;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;
import com.gtnewhorizons.modularui.common.widget.TextWidget;
import com.miaokatze.gtsr.api.compat.GTSRHatchFluidAccess;
import com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps;
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.gui.MTEAmmoniaPlantGui;
import com.miaokatze.gtsr.common.machine.base.MTEGTSRMultiBlockBase;
import com.miaokatze.gtsr.common.machine.base.MTEPressureSteamOutputHatch;
import com.miaokatze.gtsr.common.util.GTSRUtils;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IOutputHatch;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrorRegistry;
import gregtech.api.util.FluidEjectionHelper;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;
import gregtech.common.tileentities.machines.IDualInputHatch;
import gregtech.common.tileentities.machines.outputme.MTEHatchOutputME;

public class MTEAmmoniaPlant extends MTEGTSRMultiBlockBase<MTEAmmoniaPlant> implements ISurvivalConstructable {

    private static final int HEAT_MAX = 10000;
    private static final int HEAT_INCREASE_PER_SEC = 10;
    private static final int HEAT_DECREASE_PER_SEC = 50;
    private static final int PREHEAT_STEAM_PER_SEC = 8000;
    private static final int PREHEAT_REFINERY_GAS_PER_SEC = 200;
    private static final int MAINTAIN_STEAM_PER_SEC = 12000;
    private static final int MAINTAIN_REFINERY_GAS_PER_SEC = 200;
    private static final int PREHEAT_STEAM_PER_TICK = PREHEAT_STEAM_PER_SEC / 20;
    private static final int PREHEAT_GAS_PER_TICK = PREHEAT_REFINERY_GAS_PER_SEC / 20;
    private static final int MAINTAIN_STEAM_PER_TICK = MAINTAIN_STEAM_PER_SEC / 20;
    private static final int MAINTAIN_GAS_PER_TICK = MAINTAIN_REFINERY_GAS_PER_SEC / 20;
    private static final int EXTRA_STEAM_FORMULA_CONSTANT = 16000;

    private static final int[][] CATALYST_DATA = { { 80, 64 }, { 96, 48 }, { 128, 32 }, { 192, 24 }, { 256, 16 },
        { 256, 8 }, { 256, 1 } };

    // Only retained mechanical casings on hatch-replaceable positions count toward this minimum.
    private static final int REPLACEABLE_CASING_POSITIONS = 393;
    private static final int MIN_MECHANICAL_CASINGS = minimumCasings(REPLACEABLE_CASING_POSITIONS);
    private int mMechanicalCasingCount;

    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static final int HORIZONTAL_OFF_SET = 10;
    private static final int VERTICAL_OFF_SET = 11;
    private static final int DEPTH_OFF_SET = 2;
    private static IStructureDefinition<MTEAmmoniaPlant> STRUCTURE_DEFINITION = null;

    public int mHeatLevel = 0;
    private int mCatalystType = 0;
    private int mReactionTimeSec = 64;
    public int mParallelCount = 64;
    public long mRealtimeSteamCost = 0;
    public long mRealtimeSteamOutput = 0;
    // Heat is integrated in twentieths so only actually paid ticks contribute to preheating.
    private int mHeatRemainder;
    // Main recipe output/duration and the fixed-load thermal charge must use the same batch profile.
    private long mBatchExtraSteamPerSec = -1L;
    // Protected thermal overflow is temporary: later paid ticks retry it; it is not persisted across unload.
    private long gtsr$pendingSuperheatedSteam = 0L;

    private int mCasingCount = 0;
    private int mStartUpCheck = 100;

    private static final int CASING_TEXTURE_ID = GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings2, 0);

    public MTEAmmoniaPlant(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        registerProgressEntries();
    }

    public MTEAmmoniaPlant(String aName) {
        super(aName);
        registerProgressEntries();
    }

    // GTSR 进度词条：注册顺序 = GUI 终端显示顺序（热量/蒸汽消耗/高压蒸汽/并行；催化剂与状态为文本行保留在 GUI）
    private void registerProgressEntries() {
        // 热量（mHeatLevel 0-10000，词条值归一为 0-100 百分比口径，与全库其他机器/红石仓阈值一致；
        // 值颜色随热度分级，formatter 内按 0-100 值取色）
        registerEntryCustom(
            "temperature",
            "gtsr.gui.ammonia_plant.heat",
            EnumChatFormatting.WHITE,
            () -> mHeatLevel / 100.0,
            v -> (v <= 0 ? EnumChatFormatting.WHITE
                : v < 50 ? EnumChatFormatting.RED
                    : v < 80 ? EnumChatFormatting.GOLD : v < 100 ? EnumChatFormatting.GREEN : EnumChatFormatting.YELLOW)
                + String.format("%.1f%%", v));
        registerEntry(
            "steam_input",
            "gtsr.gui.ammonia_plant.steam",
            "%,.0f L/s",
            EnumChatFormatting.AQUA,
            () -> mRealtimeSteamCost);
        registerEntry(
            "hp_steam",
            "gtsr.ammonia.nominal_conversion",
            "%,.0f L/s",
            EnumChatFormatting.LIGHT_PURPLE,
            () -> mRealtimeSteamOutput);
        registerEntry("parallel", "gtsr.ammonia.parallel_limit", "%.0f", EnumChatFormatting.GOLD, () -> mParallelCount);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEAmmoniaPlant(mName);
    }

    @Override
    public IStructureDefinition<MTEAmmoniaPlant> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            STRUCTURE_DEFINITION = StructureDefinition.<MTEAmmoniaPlant>builder()
                .addShape(
                    STRUCTURE_PIECE_MAIN,
                    transpose(
                        new String[][] {
                            { "             ", "             ", "             ", "             ", "H  EEE       ",
                                "H EEEEE      ", "HHEEEEE      ", "H EEEEE      ", "H  EEE       ", "             " },
                            { "             ", "             ", "             ", "   EEE       ", "H E   E      ",
                                " E F F E     ", "CC  G  E     ", " E F F E     ", "H E   E      ", "   EEE       " },
                            { "             ", "             ", "             ", "   EEE       ", "H E   E      ",
                                " E F F E     ", "CC  G  E     ", " E F F E     ", "H E   E      ", "   EEE       " },
                            { "             ", "             ", "             ", "   EEE       ", "H E   E      ",
                                " E F F E     ", "CE  G  E     ", " E F F E     ", "H E   E      ", "   EEE       " },
                            { "             ", "             ", "             ", "   EEE       ", "H E   E      ",
                                " E F F E     ", "CE  G  E     ", " E F F E     ", "H E   E      ", "   EEE       " },
                            { "             ", "             ", "             ", "   EEE       ", "H E   E      ",
                                " E F F E     ", "CE  G  E     ", " E F F E     ", "H E   E      ", "   EEE       " },
                            { "             ", "             ", "             ", "   EEE       ", "H E   E      ",
                                " E F F E     ", "CE  G  E     ", " E F F E     ", "H E   E      ", "   EEE       " },
                            { "             ", "             ", "             ", "   EEE       ", "H E   E      ",
                                " E F F E     ", "CE  G  E     ", " E F F E     ", "H E   E      ", "   EEE       " },
                            { "             ", "        HBBBH", "        BBBBB", "   EEE  BBBBB", "H E   E BBBBB",
                                " E F F EBBBBB", "CE  G  EBBBBB", " E F F EBBBBB", "H E   E BBBBB", "   EEE  HBBBH" },
                            { "             ", "        H   H", "         BBB ", "   EEE   B B ", "H E   E  B B ",
                                " E F F CCC B ", "CE  G  CCC B ", " E F F CCC B ", "H E   E  BBB ", "   EEE  H   H" },
                            { "             ", "        H   H", "   EEE   BBB ", "   EEE   B B ", "H E   E  B B ",
                                " E F F CCC B ", "CE  G  CCC B ", " E F F CCC B ", "H E   E  BBB ", "   EEE  H   H" },
                            { "             ", "   EEE  H   H", "   EFE   B~B ", "  EEFEE  B B ", "H E   E  B B ",
                                " E F F CCC B ", "CE  G  CCC B ", " E F F CCC B ", "H E   E  BBB ", "   EEE  H   H" },
                            { "             ", "   EEE  H   H", "  EEFFFFFBBB ", "  EEFEE  B B ", "H E   E BB B ",
                                " E F F CCC B ", "CE  G  CCC B ", " E F F CCC B ", "H E   E BBBB ", "   EEE  H   H" },
                            { "   EEE       ", "  EEEEE HBBBH", " EEEEEEEBDDDB", " EEEEEEEBDDDB", "HEEEEEEBBDDDB",
                                "EEEEEEEBBDDDB", "EEEEEEEBBDDDB", "EEEEEEEBBDDDB", "HEEEEEEBBDDDB",
                                "  EEEEE HBBBH" } }))
                .addElement(
                    'B',
                    ofChain(
                        buildHatchAdder(MTEAmmoniaPlant.class).atLeast(InputHatch, SteamOutputBus, OutputHatch)
                            .casingIndex(CASING_TEXTURE_ID)
                            .hint(1)
                            .build(),
                        onElementPass(
                            t -> t.mMechanicalCasingCount++,
                            onElementPass(MTEAmmoniaPlant::onCasingAdded, ofBlock(GregTechAPI.sBlockCasings2, 0)))))
                .addElement('C', onElementPass(MTEAmmoniaPlant::onCasingAdded, ofBlock(GregTechAPI.sBlockCasings2, 13)))
                .addElement('D', onElementPass(MTEAmmoniaPlant::onCasingAdded, ofBlock(GregTechAPI.sBlockCasings3, 14)))
                .addElement(
                    'E',
                    ofChain(
                        buildHatchAdder(MTEAmmoniaPlant.class).atLeast(InputHatch, SteamOutputBus, OutputHatch)
                            .casingIndex(CASING_TEXTURE_ID)
                            .hint(2)
                            .build(),
                        onElementPass(
                            t -> t.mMechanicalCasingCount++,
                            onElementPass(MTEAmmoniaPlant::onCasingAdded, ofBlock(GregTechAPI.sBlockCasings8, 0)))))
                .addElement('F', onElementPass(MTEAmmoniaPlant::onCasingAdded, ofBlock(GregTechAPI.sBlockCasings8, 1)))
                .addElement('G', onElementPass(MTEAmmoniaPlant::onCasingAdded, ofBlock(GregTechAPI.sBlockCasings8, 1)))
                .addElement(
                    'H',
                    onElementPass(
                        MTEAmmoniaPlant::onCasingAdded,
                        ofBlock(GregTechAPI.sBlockFrames, Materials.Steel.mMetaItemSubID)))
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
        mMechanicalCasingCount = 0;
        mCasingCount = 0;
        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET, errors)) {
            return;
        }
        if (mMechanicalCasingCount < MIN_MECHANICAL_CASINGS) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }
        if (mInputHatches.isEmpty() && mDualInputHatches.isEmpty()) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }
        if (mOutputHatches.isEmpty()) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }
        if (mOutputBusses.isEmpty()) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }
        updateCatalyst();
        updateAllHatchTextures();
    }

    protected void updateAllHatchTextures() {
        int textureID = CASING_TEXTURE_ID;
        for (MTEHatch h : mInputHatches) h.updateTexture(textureID);
        for (MTEHatch h : mOutputHatches) h.updateTexture(textureID);
        for (MTEHatch h : mOutputBusses) h.updateTexture(textureID);
        for (IDualInputHatch dualHatch : mDualInputHatches) {
            if (dualHatch != null) dualHatch.updateTexture(textureID);
        }
    }

    private void updateCatalyst() {
        ItemStack controllerStack = getControllerSlot();
        mCatalystType = 0;
        mReactionTimeSec = 64;
        mParallelCount = 64;

        if (controllerStack == null) return;

        if (GTSRItemList.AmmoniaCatalystNickel.isStackEqual(controllerStack, true, true)) {
            mCatalystType = 1;
        } else if (GTSRItemList.AmmoniaCatalystPlatinum.isStackEqual(controllerStack, true, true)) {
            mCatalystType = 2;
        } else if (GTSRItemList.AmmoniaCatalystUranium.isStackEqual(controllerStack, true, true)) {
            mCatalystType = 3;
        } else if (GTSRItemList.AmmoniaCatalystOsmium.isStackEqual(controllerStack, true, true)) {
            mCatalystType = 4;
        } else if (GTSRItemList.AmmoniaCatalystFeCo.isStackEqual(controllerStack, true, true)) {
            mCatalystType = 5;
        } else if (GTSRItemList.AmmoniaCatalystRuthenium.isStackEqual(controllerStack, true, true)) {
            mCatalystType = 6;
        } else if (GTSRItemList.AmmoniaCatalystQuantum.isStackEqual(controllerStack, true, true)) {
            mCatalystType = 7;
        } else {
            return;
        }

        if (mCatalystType > 0 && mCatalystType <= CATALYST_DATA.length) {
            int catalystDataIndex = mCatalystType - 1;
            mParallelCount = CATALYST_DATA[catalystDataIndex][0];
            mReactionTimeSec = CATALYST_DATA[catalystDataIndex][1];
        }
    }

    public int getCatalystType() {
        return mCatalystType;
    }

    public void syncCatalystType(int catalystType) {
        mCatalystType = catalystType;
    }

    @Override
    protected boolean canUseControllerSlotForRecipe() {
        return false;
    }

    @Override
    public int getMaxEfficiency(ItemStack aStack) {
        return 10000;
    }

    @Override
    public int getMaxParallelRecipes() {
        return mParallelCount;
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return GTSRRecipeMaps.ammoniaPlantRecipes;
    }

    public int getTierRecipes() {
        return 0;
    }

    public boolean supportsPowerPanel() {
        return false;
    }

    // v1.7.27 修复：override 父类默认 false，让 GUI "溢出销毁"按钮可点击。
    // 玩家可通过 voidingMode 控制流体输出满时的行为：
    // - VOID_NONE（不销毁）：预检查触发，机器待机，等输出仓有空间后继续
    // - VOID_FLUID/VOID_ALL（销毁）：预检查跳过，机器持续运作，多余流体由 vanilla FluidEjectionHelper 自动销毁
    @Override
    public boolean supportsVoidProtection() {
        return true;
    }

    @Override
    public boolean getDefaultHasMaintenanceChecks() {
        return false;
    }

    @Override
    protected ProcessingLogic createProcessingLogic() {
        return new ProcessingLogic() {

            @Nonnull
            @Override
            protected CheckRecipeResult validateRecipe(@Nonnull GTRecipe recipe) {
                return CheckRecipeResultRegistry.SUCCESSFUL;
            }
        }.setMaxParallelSupplier(this::getTrueParallel);
    }

    @Override
    protected void setProcessingLogicPower(ProcessingLogic logic) {
        logic.setAvailableVoltage(8);
        logic.setAvailableAmperage(getMaxParallelRecipes());
        logic.setAmperageOC(false);
    }

    @Override
    public CheckRecipeResult checkProcessing() {
        if (!getBaseMetaTileEntity().isServerSide()) return CheckRecipeResultRegistry.NO_RECIPE;

        if (mHeatLevel < HEAT_MAX) return CheckRecipeResultRegistry.NO_RECIPE;

        if (!hasMaintainInputs()) return CheckRecipeResultRegistry.NO_RECIPE;

        setupProcessingLogic(processingLogic);
        CheckRecipeResult result = doCheckRecipe();
        result = postCheckRecipe(result, processingLogic);

        if (result.wasSuccessful()) {
            FluidStack[] outputFluids = processingLogic.getOutputFluids();

            // v1.7.28 修复：移除手动 VoidProtectionHelper 二次检查（v1.7.27 遗留的 dead code）。
            // vanilla ParallelHelper（doCheckRecipe 内部）已经基于 voidingMode 做了流体输出预检查：
            // - VOID_NONE：输出仓满时返回 FLUID_OUTPUT_FULL（机器待机，等空间后继续）
            // - VOID_FLUID/VOID_ALL：预检查跳过，机器持续运作，多余流体由 vanilla FluidEjectionHelper 自动销毁
            // 此处重复检查会与 vanilla 判定不同步（setMaxParallel(1) 与实际并行 64 不一致），
            // 反而把已通过的配方误判为 FLUID_OUTPUT_FULL，导致销毁按钮失效。

            mEfficiency = 10000;
            mEfficiencyIncrease = 10000;
            mMaxProgresstime = mReactionTimeSec * 20;
            mBatchExtraSteamPerSec = (long) mParallelCount * EXTRA_STEAM_FORMULA_CONSTANT / mReactionTimeSec;
            setEnergyUsage(processingLogic);
            mOutputItems = processingLogic.getOutputItems();
            mOutputFluids = outputFluids;
            updateSlots();
            return CheckRecipeResultRegistry.SUCCESSFUL;
        }

        // v1.7.28 修复：保留 result 而非转成 NO_RECIPE，让 FLUID_OUTPUT_FULL 等
        // 失败原因能正确传递给 GUI 显示（"流体输出空间不足"等）。
        return result;
    }

    @Override
    public boolean onRunningTick(ItemStack aStack) {
        if (!getBaseMetaTileEntity().isServerSide()) return true;

        if (mHeatLevel >= HEAT_MAX && mMaxProgresstime > 0) {
            // Legacy active saves have no batch charge; derive it once from their restored catalyst configuration.
            if (mBatchExtraSteamPerSec < 0) {
                mBatchExtraSteamPerSec = (long) mParallelCount * EXTRA_STEAM_FORMULA_CONSTANT / mReactionTimeSec;
            }
            long extraSteamPerSec = mBatchExtraSteamPerSec;
            int steamPerTick = MAINTAIN_STEAM_PER_TICK + (int) (extraSteamPerSec / 20);
            // Probe the whole running tick before paying either resource, not base and reaction steam separately.
            if (!consumeFuelAndSteam(MAINTAIN_GAS_PER_TICK, steamPerTick)) {
                mRealtimeSteamCost = 0;
                mRealtimeSteamOutput = 0;
                stopMachine();
                return false;
            }

            mRealtimeSteamCost = MAINTAIN_STEAM_PER_SEC + extraSteamPerSec;
            mRealtimeSteamOutput = MAINTAIN_STEAM_PER_SEC + extraSteamPerSec;
        }

        return true;
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        if (aBaseMetaTileEntity.isServerSide()) updateCatalyst();
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (!aBaseMetaTileEntity.isServerSide()) return;

        if (mMachine) {
            mStartUpCheck = 100;
        } else if (mStartUpCheck > 0) {
            mStartUpCheck--;
        }

        boolean inGracePeriod = mStartUpCheck > 0;

        if (mMachine && aBaseMetaTileEntity.isAllowedToWork()) {
            if (mHeatLevel < HEAT_MAX && mMaxProgresstime > 0) {
                stopMachine();
            }

            if (mMaxProgresstime <= 0) {
                boolean consumed = false;

                if (mHeatLevel < HEAT_MAX) {
                    if (consumeFuelAndSteam(PREHEAT_GAS_PER_TICK, PREHEAT_STEAM_PER_TICK)) {
                        adjustHeat(HEAT_INCREASE_PER_SEC);
                        mRealtimeSteamCost = PREHEAT_STEAM_PER_SEC;
                        mRealtimeSteamOutput = PREHEAT_STEAM_PER_SEC;
                        consumed = true;
                    }
                } else {
                    if (consumeFuelAndSteam(MAINTAIN_GAS_PER_TICK, MAINTAIN_STEAM_PER_TICK)) {
                        mRealtimeSteamCost = MAINTAIN_STEAM_PER_SEC;
                        mRealtimeSteamOutput = MAINTAIN_STEAM_PER_SEC;
                        consumed = true;
                    }
                }

                if (!consumed) {
                    adjustHeat(-HEAT_DECREASE_PER_SEC);
                    mRealtimeSteamCost = 0;
                    mRealtimeSteamOutput = 0;
                }
            }
        } else if (mMachine && !aBaseMetaTileEntity.isAllowedToWork()) {
            if (mMaxProgresstime > 0) {
                stopMachine();
            }
            adjustHeat(-HEAT_DECREASE_PER_SEC);
            mRealtimeSteamCost = 0;
            mRealtimeSteamOutput = 0;
        } else if (!mMachine && !inGracePeriod) {
            adjustHeat(-HEAT_DECREASE_PER_SEC);
            mRealtimeSteamCost = 0;
            mRealtimeSteamOutput = 0;
        }
    }

    private void adjustHeat(int twentieths) {
        int heat = Math.max(0, Math.min(HEAT_MAX * 20, mHeatLevel * 20 + mHeatRemainder + twentieths));
        mHeatLevel = heat / 20;
        mHeatRemainder = heat % 20;
    }

    /**
     * Require both full inputs before extraction and verify the actual receipts. This is not an atomic AE transaction:
     * a network change may still cause a partial payment, which must never authorize heat or recipe progress.
     * Convert only steam actually extracted, including a partial extraction before a failed tick.
     */
    private boolean consumeFuelAndSteam(int gasAmount, int steamAmount) {
        if (gasAmount > 0 && !hasRefineryGas(gasAmount)) return false;
        // v1.10.60：窗口外 depleteInput 在 beta-1 + ME 仓会双扣，探测改访问层（getTankInfo 跨仓合计）
        if (steamAmount > 0
            && !GTSRHatchFluidAccess.hasEnoughAcross(mInputHatches, Materials.Steam.getGas(steamAmount))) {
            return false;
        }
        if (gasAmount > 0
            && GTSRHatchFluidAccess.depleteFluidAcross(mInputHatches, Materials.Gas.getGas(gasAmount)) < gasAmount) {
            return false;
        }
        int paidSteam = steamAmount > 0
            ? GTSRHatchFluidAccess.depleteFluidAcross(mInputHatches, Materials.Steam.getGas(steamAmount))
            : 0;
        pushSuperheatedSteam(paidSteam);
        return paidSteam == steamAmount;
    }

    private boolean hasMaintainInputs() {
        return hasRefineryGas(MAINTAIN_GAS_PER_TICK)
            && GTSRHatchFluidAccess.hasEnoughAcross(mInputHatches, Materials.Steam.getGas(MAINTAIN_STEAM_PER_TICK));
    }

    // v1.9.39 修复：炼油气取流改用 GT5U 原生 depleteInput(FluidStack, boolean)。
    // 原实现用 MTEHatchInput.getFluid()/2参 drain，对 ME 输入仓（MTEHatchInputME，本地罐恒空）恒返回 null，
    // 导致预热/维持炼油气永远取不到、整机无法运转。原生 2 参 depleteInput 走 drain(UNKNOWN,...)，
    // 兼容普通仓 / ME 输入仓 / 限制性输入仓，且支持跨仓合计。
    // 注（v1.10.8 修正注释）：本类继承 MTEEnhancedMultiBlockBase，1 参 depleteInput 即 GT5U 原生
    // （= 2 参 false 语义）；MTESteamMultiBaseMixin 仅注入 GT++ MTESteamMultiBlockBase，对本类不生效。
    private boolean hasRefineryGas(int amount) {
        // v1.10.60：窗口外 depleteInput 在 beta-1 + ME 仓会双扣，探测改访问层（getTankInfo 跨仓合计）
        return GTSRHatchFluidAccess.hasEnoughAcross(mInputHatches, Materials.Gas.getGas(amount));
    }

    /** True only for ports assigned to continuous thermal output, never the unfiltered recipe output fallback. */
    private boolean isSuperheatedSteamPort(IOutputHatch hatch) {
        if (hatch instanceof MTEPressureSteamOutputHatch) return true;
        FluidStack steam = FluidRegistry.getFluidStack("ic2superheatedsteam", 1);
        return steam != null && hatch.isFiltered() && hatch.isFilteredToFluid(GTUtility.FluidId.create(steam));
    }

    @Override
    public List<IOutputHatch> getOutputHatches() {
        List<IOutputHatch> hatches = new ArrayList<>(super.getOutputHatches());
        hatches.removeIf(this::isSuperheatedSteamPort);
        return hatches;
    }

    @Override
    public boolean canDumpFluidToME(List<GTUtility.FluidId> outputs) {
        // The upstream shortcut scans all mOutputHatches. Use the same primary set as precheck and delivery.
        List<IOutputHatch> hatches = getOutputHatches();
        for (GTUtility.FluidId output : outputs) {
            boolean accepted = false;
            for (IOutputHatch hatch : hatches) {
                if (hatch instanceof MTEHatchOutputME me && me.hasPhysicalSpace()
                    && !me.getCheckMode()
                    && (!me.isFiltered() || me.isFilteredToFluid(output))) {
                    accepted = true;
                    break;
                }
            }
            if (!accepted) return false;
        }
        return true;
    }

    /** Keep the full long balance; each call submits only one bounded native transaction. */
    private void pushSuperheatedSteam(int amount) {
        if (amount <= 0) return;
        FluidStack superheatedSteam = FluidRegistry.getFluidStack("ic2superheatedsteam", amount);
        if (superheatedSteam == null) return;
        long total = gtsr$pendingSuperheatedSteam + amount;
        int requested = (int) Math.min(total, Integer.MAX_VALUE);
        int remaining = fillSuperheatedSteam(superheatedSteam, requested);
        long delivered = (long) requested - remaining;
        gtsr$pendingSuperheatedSteam = voidingMode.protectFluid ? total - delivered : 0L;
    }

    private int fillSuperheatedSteam(FluidStack superheatedSteam, int amount) {
        List<IOutputHatch> hatches = new ArrayList<>();
        for (IOutputHatch hatch : super.getOutputHatches()) {
            if (isSuperheatedSteamPort(hatch)) hatches.add(hatch);
        }
        FluidStack remaining = superheatedSteam.copy();
        remaining.amount = amount;
        // GT205's helper applies filtering, canStoreFluid and ME transaction rules; direct storePartial uses raw fill.
        FluidEjectionHelper helper = new FluidEjectionHelper(hatches, voidingMode.protectFluid);
        helper.ejectStack(remaining);
        helper.commit();
        return remaining.amount;
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setInteger("mHeatLevel", mHeatLevel);
        aNBT.setInteger("mCatalystType", mCatalystType);
        aNBT.setInteger("gtsrHeatRemainder", mHeatRemainder);
        aNBT.setLong("gtsrBatchExtraSteamPerSec", mBatchExtraSteamPerSec);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mHeatLevel = aNBT.getInteger("mHeatLevel");
        mCatalystType = aNBT.getInteger("mCatalystType");
        mHeatRemainder = Math.max(0, Math.min(19, aNBT.getInteger("gtsrHeatRemainder")));
        mBatchExtraSteamPerSec = aNBT.hasKey("gtsrBatchExtraSteamPerSec") ? aNBT.getLong("gtsrBatchExtraSteamPerSec")
            : -1L;
    }

    private String getStatusText() {
        if (mHeatLevel <= 0) return StatCollector.translateToLocal("gtsr.gui.ammonia_plant.cold");
        if (mHeatLevel < HEAT_MAX) return StatCollector.translateToLocal("gtsr.gui.ammonia_plant.preheating");
        if (mMaxProgresstime > 0) return StatCollector.translateToLocal("gtsr.gui.ammonia_plant.running");
        return StatCollector.translateToLocal("gtsr.gui.ammonia_plant.standby");
    }

    private EnumChatFormatting getStatusColor() {
        if (mHeatLevel <= 0) return EnumChatFormatting.GRAY;
        if (mHeatLevel < HEAT_MAX) return EnumChatFormatting.GOLD;
        if (mMaxProgresstime > 0) return EnumChatFormatting.GREEN;
        return EnumChatFormatting.YELLOW;
    }

    @Override
    protected @Nonnull MTEMultiBlockBaseGui<?> getGui() {
        return new MTEAmmoniaPlantGui(this);
    }

    @Deprecated
    @Override
    protected void drawTexts(DynamicPositionedColumn screenElements, SlotWidget inventorySlot) {
        super.drawTexts(screenElements, inventorySlot);
        screenElements.widget(new TextWidget().setStringSupplier(() -> {
            double heatPct = mHeatLevel / 100.0;
            EnumChatFormatting heatColor;
            if (heatPct <= 0) heatColor = EnumChatFormatting.GRAY;
            else if (heatPct < 50) heatColor = EnumChatFormatting.RED;
            else if (heatPct < 80) heatColor = EnumChatFormatting.GOLD;
            else if (heatPct < 100) heatColor = EnumChatFormatting.GREEN;
            else heatColor = EnumChatFormatting.YELLOW;
            return EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.gui.ammonia_plant.heat")
                + " "
                + heatColor
                + String.format("%.1f%%", heatPct)
                + " "
                + EnumChatFormatting.RESET;
        }))
            .widget(new TextWidget().setStringSupplier(() -> {
                String catalystName = mCatalystType > 0
                    ? EnumChatFormatting.GREEN
                        + StatCollector.translateToLocal("gtsr.gui.ammonia_plant.catalyst." + mCatalystType)
                    : EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.gui.not_installed");
                return EnumChatFormatting.WHITE + StatCollector.translateToLocal(
                    "gtsr.gui.ammonia_plant.catalyst") + " " + catalystName + " " + EnumChatFormatting.RESET;
            }))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.gui.ammonia_plant.status")
                        + " "
                        + getStatusColor()
                        + getStatusText()
                        + " "
                        + EnumChatFormatting.RESET))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.gui.ammonia_plant.steam")
                        + " "
                        + EnumChatFormatting.AQUA
                        + NumberFormatUtil.formatNumber(mRealtimeSteamCost)
                        + " L/s "
                        + EnumChatFormatting.RESET))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.ammonia.nominal_conversion")
                        + " "
                        + EnumChatFormatting.LIGHT_PURPLE
                        + NumberFormatUtil.formatNumber(mRealtimeSteamOutput)
                        + " L/s "
                        + EnumChatFormatting.RESET))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.ammonia.parallel_limit")
                        + " "
                        + EnumChatFormatting.GOLD
                        + mParallelCount
                        + " "
                        + EnumChatFormatting.RESET))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mHeatLevel, val -> mHeatLevel = val))
            .widget(new FakeSyncWidget.LongSyncer(() -> mRealtimeSteamCost, val -> mRealtimeSteamCost = val))
            .widget(new FakeSyncWidget.LongSyncer(() -> mRealtimeSteamOutput, val -> mRealtimeSteamOutput = val))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mParallelCount, val -> mParallelCount = val))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mCatalystType, val -> mCatalystType = val))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mMaxProgresstime, val -> mMaxProgresstime = val));
    }

    @Override
    public String[] getInfoData() {
        ArrayList<String> info = new ArrayList<>();
        info.add(
            EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.ammonia_plant.type")
                + EnumChatFormatting.RESET);

        if (!mMachine) {
            info.add(EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.gui.building"));
            return info.toArray(new String[0]);
        }

        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.ammonia_plant.heat")
                + " "
                + EnumChatFormatting.RED
                + String.format("%.1f%%", mHeatLevel / 100.0)
                + EnumChatFormatting.RESET);

        String statusKey;
        EnumChatFormatting statusColor;
        if (mMaxProgresstime > 0) {
            statusKey = "gtsr.gui.status.running";
            statusColor = EnumChatFormatting.GREEN;
        } else if (mHeatLevel > 0) {
            statusKey = "gtsr.gui.ammonia_plant.status.preheating";
            statusColor = EnumChatFormatting.GREEN;
        } else {
            statusKey = "gtsr.gui.status.idle";
            statusColor = EnumChatFormatting.GRAY;
        }
        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.status")
                + " "
                + statusColor
                + StatCollector.translateToLocal(statusKey)
                + EnumChatFormatting.RESET);

        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.ammonia_plant.steam")
                + " "
                + EnumChatFormatting.AQUA
                + NumberFormatUtil.formatNumber(mRealtimeSteamCost)
                + " L/s"
                + EnumChatFormatting.RESET);

        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.ammonia.nominal_conversion")
                + " "
                + EnumChatFormatting.LIGHT_PURPLE
                + NumberFormatUtil.formatNumber(mRealtimeSteamOutput)
                + " L/s"
                + EnumChatFormatting.RESET);

        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.ammonia.parallel_limit")
                + " "
                + EnumChatFormatting.GOLD
                + mParallelCount
                + EnumChatFormatting.RESET);

        String catalystName = mCatalystType > 0
            ? EnumChatFormatting.GREEN
                + StatCollector.translateToLocal("gtsr.gui.ammonia_plant.catalyst." + mCatalystType)
            : EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.gui.not_installed");
        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.ammonia_plant.catalyst")
                + " "
                + catalystName
                + EnumChatFormatting.RESET);

        return info.toArray(new String[0]);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int aColorIndex, boolean aActive, boolean aRedstone) {
        if (side == facing) {
            return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(CASING_TEXTURE_ID),
                aActive ? getFrontOverlayActive() : getFrontOverlay() };
        }
        return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(CASING_TEXTURE_ID) };
    }

    protected ITexture getFrontOverlay() {
        return TextureFactory.of(OVERLAY_FRONT_LARGE_CHEMICAL_REACTOR);
    }

    protected ITexture getFrontOverlayActive() {
        return TextureFactory.of(OVERLAY_FRONT_LARGE_CHEMICAL_REACTOR_ACTIVE);
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal("gtsr.tooltip.ammonia_plant.type"))
            .addInfo(
                EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.tooltip.ammonia_plant.desc.1")
                    + EnumChatFormatting.GREEN
                    + StatCollector.translateToLocal("gtsr.tooltip.ammonia_plant.desc.2")
                    + EnumChatFormatting.GRAY
                    + StatCollector.translateToLocal("gtsr.tooltip.ammonia_plant.desc.3")
                    + EnumChatFormatting.RED
                    + StatCollector.translateToLocal("gtsr.tooltip.ammonia_plant.desc.4")
                    + EnumChatFormatting.GRAY
                    + StatCollector.translateToLocal("gtsr.tooltip.ammonia_plant.desc.5")
                    + EnumChatFormatting.RED
                    + StatCollector.translateToLocal("gtsr.tooltip.ammonia_plant.desc.6")
                    + EnumChatFormatting.GRAY
                    + StatCollector.translateToLocal("gtsr.tooltip.ammonia_plant.desc.7"))
            .addInfo(EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.ammonia_plant.desc_line_2"))
            .addInfo(EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.ammonia_plant.desc_line_3"))
            .addInfo(StatCollector.translateToLocal("gtsr.ammonia.output_ports"))
            .addInfo(StatCollector.translateToLocal("gtsr.ammonia.output_ports.steam"))
            .addInfo(StatCollector.translateToLocal("gtsr.ammonia.pending_policy"))
            .addSeparator()
            .addInfo(
                EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.tooltip.ammonia_plant.preheat.1")
                    + EnumChatFormatting.RED
                    + StatCollector.translateToLocal("gtsr.tooltip.ammonia_plant.preheat.2")
                    + EnumChatFormatting.GRAY
                    + StatCollector.translateToLocal("gtsr.tooltip.ammonia_plant.preheat.3"))
            .addInfo(
                EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.ammonia_plant.preheat_line_2"))
            .addInfo(
                EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.tooltip.ammonia_plant.heat_cost.1")
                    + EnumChatFormatting.WHITE
                    + "8,000 L/s "
                    + StatCollector.translateToLocal("gtsr.tooltip.ammonia_plant.steam_unit")
                    + " + 200 L/s "
                    + StatCollector.translateToLocal("gtsr.tooltip.ammonia_plant.refinery_gas"))
            .addInfo(
                EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.tooltip.ammonia_plant.heat_cost.2")
                    + EnumChatFormatting.WHITE
                    + "12,000 L/s "
                    + StatCollector.translateToLocal("gtsr.tooltip.ammonia_plant.steam_unit")
                    + " + 200 L/s "
                    + StatCollector.translateToLocal("gtsr.tooltip.ammonia_plant.refinery_gas"))
            .addSeparator()
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.ammonia_plant.catalyst"))
            .addInfo(
                EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.ammonia_plant.catalyst_line_2"))
            .addInfo(
                EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.tooltip.ammonia_plant.catalyst_detail.1")
                    + EnumChatFormatting.GOLD
                    + "64\u2192256"
                    + EnumChatFormatting.GRAY
                    + StatCollector.translateToLocal("gtsr.tooltip.ammonia_plant.catalyst_detail.2")
                    + EnumChatFormatting.GOLD
                    + "64s\u21921s"
                    + EnumChatFormatting.GRAY
                    + StatCollector.translateToLocal("gtsr.tooltip.ammonia_plant.catalyst_detail.3"))
            // [GT-compat] beta 兼容层（beta1/beta2/beta3）：beta-3 起始参数序为 (w,h,l)，实参已按 beta-3 语义排列
            .beginStructureBlock(13, 14, 10, false)
            .addCasingInfoMin(
                StatCollector.translateToLocal("gtsr.tooltip.shared.mechanical_casing"),
                MIN_MECHANICAL_CASINGS,
                false)
            .addStructureInfo(StatCollector.translateToLocal("gtsr.tooltip.structure.AmmoniaPlant.hatches"))
            .addController(StatCollector.translateToLocal("gtsr.tooltip.ammonia_plant.ctrl"))
            .addInputHatch(StatCollector.translateToLocal("gtsr.tooltip.ammonia_plant.input_hatch"), 1)
            .addOutputBus(StatCollector.translateToLocal("gtsr.tooltip.ammonia_plant.output_bus"), 1)
            .addOutputHatch(StatCollector.translateToLocal("gtsr.tooltip.ammonia_plant.output_hatch"), 1)
            .addStructureInfo("")
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.steel_casing"), 116, false)
            .addCasingInfoExactly(
                StatCollector.translateToLocal("gtsr.tooltip.ammonia_plant.advanced_casing"),
                272,
                false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.pipe"), 50, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.firebox"), 21, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.frame"), 52, false)
            .addStructureHint("gtsr.tooltip.shared.no_maintenance")
            .addInfo(GTSRUtils.getAddedByLine())
            .toolTipFinisher();
        return tt;
    }

    public String getMachineType() {
        return "Ammonia Plant";
    }
}
