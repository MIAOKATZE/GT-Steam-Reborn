package com.miaokatze.gtsr.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.HatchElement.InputHatch;
import static gregtech.api.enums.HatchElement.OutputBus;
import static gregtech.api.enums.HatchElement.OutputHatch;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_LARGE_CHEMICAL_REACTOR;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_LARGE_CHEMICAL_REACTOR_ACTIVE;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.ArrayList;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizons.modularui.common.widget.DynamicPositionedColumn;
import com.gtnewhorizons.modularui.common.widget.FakeSyncWidget;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;
import com.gtnewhorizons.modularui.common.widget.TextWidget;
import com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps;
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;

public class MTEAmmoniaPlant extends MTEEnhancedMultiBlockBase<MTEAmmoniaPlant> implements ISurvivalConstructable {

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

    private static final int[][] CATALYST_DATA = { { 64, 64 }, { 96, 48 }, { 128, 64 }, { 192, 24 }, { 256, 16 },
        { 256, 8 }, { 256, 4 }, { 256, 1 } };

    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static final int HORIZONTAL_OFF_SET = 9;
    private static final int VERTICAL_OFF_SET = 11;
    private static final int DEPTH_OFF_SET = 2;
    private static IStructureDefinition<MTEAmmoniaPlant> STRUCTURE_DEFINITION = null;

    private int mHeatLevel = 0;
    private int mCatalystType = 0;
    private int mReactionTimeSec = 64;
    private int mParallelCount = 64;
    private long mRealtimeSteamCost = 0;
    private long mRealtimeSteamOutput = 0;

    private int mCasingCount = 0;
    private int mStartUpCheck = 100;

    private static final int CASING_TEXTURE_ID = GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings2, 0);

    public MTEAmmoniaPlant(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTEAmmoniaPlant(String aName) {
        super(aName);
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
                        buildHatchAdder(MTEAmmoniaPlant.class).atLeast(InputHatch, OutputBus, OutputHatch)
                            .casingIndex(CASING_TEXTURE_ID)
                            .dot(1)
                            .buildAndChain(
                                onElementPass(MTEAmmoniaPlant::onCasingAdded, ofBlock(GregTechAPI.sBlockCasings2, 0)))))
                .addElement('C', onElementPass(MTEAmmoniaPlant::onCasingAdded, ofBlock(GregTechAPI.sBlockCasings2, 13)))
                .addElement('D', onElementPass(MTEAmmoniaPlant::onCasingAdded, ofBlock(GregTechAPI.sBlockCasings3, 14)))
                .addElement(
                    'E',
                    ofChain(
                        buildHatchAdder(MTEAmmoniaPlant.class).atLeast(InputHatch, OutputBus, OutputHatch)
                            .casingIndex(CASING_TEXTURE_ID)
                            .dot(2)
                            .buildAndChain(
                                onElementPass(MTEAmmoniaPlant::onCasingAdded, ofBlock(GregTechAPI.sBlockCasings4, 9)))))
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
    public boolean checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack) {
        mCasingCount = 0;
        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET)) return false;
        if (mCasingCount < 10) return false;
        if (mInputHatches.isEmpty()) return false;
        if (mOutputHatches.isEmpty()) return false;
        if (mOutputBusses.isEmpty()) return false;
        updateCatalyst();
        return true;
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

        if (mCatalystType >= 0 && mCatalystType < CATALYST_DATA.length) {
            mParallelCount = CATALYST_DATA[mCatalystType][0];
            mReactionTimeSec = CATALYST_DATA[mCatalystType][1];
        }
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

        setupProcessingLogic(processingLogic);
        CheckRecipeResult result = doCheckRecipe();
        result = postCheckRecipe(result, processingLogic);

        if (result.wasSuccessful()) {
            mEfficiency = 10000;
            mEfficiencyIncrease = 10000;
            mMaxProgresstime = mReactionTimeSec * 20;
            setEnergyUsage(processingLogic);
            mOutputItems = processingLogic.getOutputItems();
            mOutputFluids = processingLogic.getOutputFluids();
            updateSlots();
            return CheckRecipeResultRegistry.SUCCESSFUL;
        }

        return CheckRecipeResultRegistry.NO_RECIPE;
    }

    @Override
    public boolean onRunningTick(ItemStack aStack) {
        if (!getBaseMetaTileEntity().isServerSide()) return true;

        if (mHeatLevel >= HEAT_MAX && mMaxProgresstime > 0) {
            if (!depleteRefineryGas(MAINTAIN_GAS_PER_TICK)) {
                stopMachine();
                return false;
            }
            if (!consumeSteam(MAINTAIN_STEAM_PER_TICK)) {
                stopMachine();
                return false;
            }
            pushSuperheatedSteam(MAINTAIN_STEAM_PER_TICK);

            long extraSteamPerSec = (long) mParallelCount * EXTRA_STEAM_FORMULA_CONSTANT / mReactionTimeSec;
            long extraSteamPerTick = extraSteamPerSec / 20;
            if (extraSteamPerTick > 0) {
                if (!consumeSteam((int) Math.min(extraSteamPerTick, Integer.MAX_VALUE))) {
                    stopMachine();
                    return false;
                }
                pushSuperheatedSteam((int) Math.min(extraSteamPerTick, Integer.MAX_VALUE));
            }

            mRealtimeSteamCost = MAINTAIN_STEAM_PER_SEC + extraSteamPerSec;
            mRealtimeSteamOutput = MAINTAIN_STEAM_PER_SEC + extraSteamPerSec;
        }

        return true;
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (!aBaseMetaTileEntity.isServerSide()) return;

        if (mMachine) {
            mStartUpCheck = 100;
        } else if (mStartUpCheck > 0) {
            mStartUpCheck--;
        }

        boolean isSecondTick = aTick % 20 == 0;
        boolean inGracePeriod = mStartUpCheck > 0;

        if (mMachine && aBaseMetaTileEntity.isAllowedToWork()) {
            if (mHeatLevel < HEAT_MAX && mMaxProgresstime > 0) {
                stopMachine();
            }

            if (mMaxProgresstime <= 0) {
                boolean consumed = false;

                if (mHeatLevel < HEAT_MAX) {
                    if (depleteRefineryGas(PREHEAT_GAS_PER_TICK) && consumeSteam(PREHEAT_STEAM_PER_TICK)) {
                        pushSuperheatedSteam(PREHEAT_STEAM_PER_TICK);
                        if (isSecondTick) {
                            mHeatLevel = Math.min(HEAT_MAX, mHeatLevel + HEAT_INCREASE_PER_SEC);
                        }
                        mRealtimeSteamCost = PREHEAT_STEAM_PER_SEC;
                        mRealtimeSteamOutput = PREHEAT_STEAM_PER_SEC;
                        consumed = true;
                    }
                } else {
                    if (depleteRefineryGas(MAINTAIN_GAS_PER_TICK) && consumeSteam(MAINTAIN_STEAM_PER_TICK)) {
                        pushSuperheatedSteam(MAINTAIN_STEAM_PER_TICK);
                        mRealtimeSteamCost = MAINTAIN_STEAM_PER_SEC;
                        mRealtimeSteamOutput = MAINTAIN_STEAM_PER_SEC;
                        consumed = true;
                    }
                }

                if (!consumed) {
                    if (mHeatLevel > 0 && isSecondTick) {
                        mHeatLevel = Math.max(0, mHeatLevel - HEAT_DECREASE_PER_SEC);
                    }
                    mRealtimeSteamCost = 0;
                    mRealtimeSteamOutput = 0;
                }
            }
        } else if (mMachine && !aBaseMetaTileEntity.isAllowedToWork()) {
            if (mMaxProgresstime > 0) {
                stopMachine();
            }
            if (mHeatLevel > 0 && isSecondTick) {
                mHeatLevel = Math.max(0, mHeatLevel - HEAT_DECREASE_PER_SEC);
            }
            mRealtimeSteamCost = 0;
            mRealtimeSteamOutput = 0;
        } else if (!mMachine && mHeatLevel > 0 && isSecondTick && !inGracePeriod) {
            mHeatLevel = Math.max(0, mHeatLevel - HEAT_DECREASE_PER_SEC);
            mRealtimeSteamCost = 0;
            mRealtimeSteamOutput = 0;
        }
    }

    private boolean consumeSteam(int amount) {
        FluidStack steam = Materials.Steam.getGas(amount);
        return depleteInput(steam);
    }

    private boolean depleteRefineryGas(int amount) {
        FluidStack refineryGas = Materials.Gas.getGas(amount);
        for (MTEHatchInput tHatch : GTUtility.validMTEList(mInputHatches)) {
            FluidStack tLiquid = tHatch.getFluid();
            if (tLiquid != null && tLiquid.isFluidEqual(refineryGas)) {
                FluidStack drained = tHatch.drain(amount, false);
                if (drained != null && drained.amount >= amount) {
                    tHatch.drain(amount, true);
                    return true;
                }
            }
        }
        return false;
    }

    private void pushSuperheatedSteam(int amount) {
        if (amount <= 0) return;
        Fluid superheated = FluidRegistry.getFluid("ic2superheatedsteam");
        if (superheated == null) return;
        int remaining = amount;
        for (MTEHatchOutput tHatch : GTUtility.validMTEList(mOutputHatches)) {
            int filled = tHatch.fill(new FluidStack(superheated, remaining), false);
            if (filled > 0) {
                tHatch.fill(new FluidStack(superheated, filled), true);
                remaining -= filled;
                if (remaining <= 0) return;
            }
        }
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setInteger("mHeatLevel", mHeatLevel);
        aNBT.setInteger("mCatalystType", mCatalystType);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mHeatLevel = aNBT.getInteger("mHeatLevel");
        mCatalystType = aNBT.getInteger("mCatalystType");
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
                        + GTUtility.formatNumbers(mRealtimeSteamCost)
                        + " L/s "
                        + EnumChatFormatting.RESET))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.gui.ammonia_plant.hp_steam")
                        + " "
                        + EnumChatFormatting.LIGHT_PURPLE
                        + GTUtility.formatNumbers(mRealtimeSteamOutput)
                        + " L/s "
                        + EnumChatFormatting.RESET))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.gui.ammonia_plant.parallel")
                        + " "
                        + EnumChatFormatting.GOLD
                        + mParallelCount
                        + " "
                        + EnumChatFormatting.RESET))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mHeatLevel, val -> mHeatLevel = val))
            .widget(new FakeSyncWidget.LongSyncer(() -> mRealtimeSteamCost, val -> mRealtimeSteamCost = val))
            .widget(new FakeSyncWidget.LongSyncer(() -> mRealtimeSteamOutput, val -> mRealtimeSteamOutput = val))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mParallelCount, val -> mParallelCount = val))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mMaxProgresstime, val -> mMaxProgresstime = val));
    }

    @Override
    public String[] getInfoData() {
        ArrayList<String> info = new ArrayList<>();
        info.add(
            EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.gui.ammonia_plant.name")
                + EnumChatFormatting.RESET);
        info.add(
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.gui.ammonia_plant.heat")
                + " "
                + EnumChatFormatting.RED
                + String.format("%.1f%%", mHeatLevel / 100.0)
                + EnumChatFormatting.RESET);
        info.add(
            EnumChatFormatting.GRAY + StatCollector.translateToLocal(
                "gtsr.gui.ammonia_plant.status") + " " + getStatusColor() + getStatusText() + EnumChatFormatting.RESET);
        info.add(
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.gui.ammonia_plant.steam")
                + " "
                + EnumChatFormatting.AQUA
                + GTUtility.formatNumbers(mRealtimeSteamCost)
                + " L/s"
                + EnumChatFormatting.RESET);
        info.add(
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.gui.ammonia_plant.hp_steam")
                + " "
                + EnumChatFormatting.LIGHT_PURPLE
                + GTUtility.formatNumbers(mRealtimeSteamOutput)
                + " L/s"
                + EnumChatFormatting.RESET);
        info.add(
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.gui.ammonia_plant.parallel")
                + " "
                + EnumChatFormatting.GOLD
                + mParallelCount
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
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.ammonia_plant.info"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.ammonia_plant.info2"))
            .addSeparator()
            .beginStructureBlock(10, 14, 13, false)
            .addController(StatCollector.translateToLocal("gtsr.tooltip.ammonia_plant.controller"))
            .addInputHatch(StatCollector.translateToLocal("gtsr.tooltip.ammonia_plant.input_hatch"), 1)
            .addOutputBus(StatCollector.translateToLocal("gtsr.tooltip.ammonia_plant.output_bus"), 1)
            .addOutputHatch(StatCollector.translateToLocal("gtsr.tooltip.ammonia_plant.output_hatch"), 1)
            .toolTipFinisher("GTSR");
        return tt;
    }

    public String getMachineType() {
        return "Ammonia Plant";
    }
}
