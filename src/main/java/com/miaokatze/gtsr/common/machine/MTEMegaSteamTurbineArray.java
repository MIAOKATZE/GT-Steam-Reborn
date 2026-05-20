package com.miaokatze.gtsr.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.isAir;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlocksTiered;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.HatchElement.Dynamo;
import static gregtech.api.enums.HatchElement.InputHatch;
import static gregtech.api.enums.HatchElement.OutputHatch;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.ImmutableList;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizons.modularui.api.math.Alignment;
import com.gtnewhorizons.modularui.common.widget.DynamicPositionedColumn;
import com.gtnewhorizons.modularui.common.widget.FakeSyncWidget;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;
import com.gtnewhorizons.modularui.common.widget.TextWidget;
import com.miaokatze.gtsr.common.machine.base.MTEHatchPressureSteamInput;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEHatchDynamo;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.RenderOverlay;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTUtility;
import gregtech.api.util.GTUtilityClient;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.misc.GTStructureChannels;

public class MTEMegaSteamTurbineArray extends MTEEnhancedMultiBlockBase<MTEMegaSteamTurbineArray>
    implements IConstructable, ISurvivalConstructable {

    private static final String STRUCTURE_PIECE_BASE_1 = "base1";
    private static final String STRUCTURE_PIECE_BASE_2 = "base2";
    private static final String STRUCTURE_PIECE_BASE_3 = "base3";
    private static final String STRUCTURE_PIECE_STACK = "stack";
    private static final String STRUCTURE_PIECE_STACK_HINT = "stackHint";
    private static final String STRUCTURE_PIECE_CAP = "cap";

    private static final int SOLID_STEEL_CASING_INDEX = GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings2, 0);
    private static IStructureDefinition<MTEMegaSteamTurbineArray> STRUCTURE_DEFINITION;

    private int mCasingAmount = 0;
    private int mStackCount = 0;
    private int mCasingTier = -1;
    private int excessWater = 0;

    private int mTheoreticalEUt = 0;
    private int mSteamConsumption = 0;

    private final List<MTEHatchPressureSteamInput> mPressureSteamInputs = new ArrayList<>();
    protected final List<RenderOverlay.OverlayTicket> overlayTickets = new ArrayList<>();

    public MTEMegaSteamTurbineArray(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTEMegaSteamTurbineArray(String aName) {
        super(aName);
    }

    private static final int MAX_EFFICIENCY_STEAM = 15000;
    private static final int MAX_EFFICIENCY_HP_STEAM = 20000;

    @Override
    protected void drawTexts(DynamicPositionedColumn screenElements, SlotWidget inventorySlot) {
        super.drawTexts(screenElements, inventorySlot);

        screenElements.widget(new FakeSyncWidget.IntegerSyncer(() -> mCasingTier, val -> mCasingTier = val));
        screenElements.widget(new FakeSyncWidget.IntegerSyncer(() -> mStackCount, val -> mStackCount = val));
        screenElements.widget(new FakeSyncWidget.IntegerSyncer(() -> mTheoreticalEUt, val -> mTheoreticalEUt = val));
        screenElements
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mSteamConsumption, val -> mSteamConsumption = val));

        screenElements.widget(
            TextWidget
                .dynamicString(
                    () -> EnumChatFormatting.GOLD + "EU/t: "
                        + EnumChatFormatting.AQUA
                        + GTUtility.formatNumbers(getVoltage() * 4 * (getStackLayers() + 1) * 2)
                        + EnumChatFormatting.GRAY
                        + " (HP: "
                        + EnumChatFormatting.RED
                        + GTUtility.formatNumbers(getVoltage() * 8 * (getStackLayers() + 1) * 2)
                        + EnumChatFormatting.GRAY
                        + ")")
                .setTextAlignment(Alignment.CenterLeft)
                .setDefaultColor(COLOR_TEXT_WHITE.get())
                .setEnabled(w -> mMachine));

        screenElements.widget(
            TextWidget
                .dynamicString(
                    () -> EnumChatFormatting.GOLD + "Steam: "
                        + EnumChatFormatting.AQUA
                        + GTUtility.formatNumbers(calcSteamConsumption(false))
                        + " L/t"
                        + EnumChatFormatting.GRAY
                        + " (HP: "
                        + EnumChatFormatting.RED
                        + GTUtility.formatNumbers(calcSteamConsumption(true))
                        + ")")
                .setTextAlignment(Alignment.CenterLeft)
                .setDefaultColor(COLOR_TEXT_WHITE.get())
                .setEnabled(w -> mMachine));

        screenElements.widget(
            TextWidget
                .dynamicString(
                    () -> EnumChatFormatting.GOLD + "Savings: "
                        + EnumChatFormatting.GREEN
                        + String.format("%.0f%%", 0.05 * getStackLayers() * 100))
                .setTextAlignment(Alignment.CenterLeft)
                .setDefaultColor(COLOR_TEXT_WHITE.get())
                .setEnabled(w -> mMachine));

        screenElements.widget(
            TextWidget
                .dynamicString(
                    () -> EnumChatFormatting.GOLD + "Eff: "
                        + (mEfficiency >= MAX_EFFICIENCY_HP_STEAM ? EnumChatFormatting.LIGHT_PURPLE
                            : mEfficiency >= 10000 ? EnumChatFormatting.GREEN : EnumChatFormatting.YELLOW)
                        + String.format("%.1f%%", mEfficiency / 100.0)
                        + (mEfficiency >= MAX_EFFICIENCY_HP_STEAM ? " MAX" : ""))
                .setTextAlignment(Alignment.CenterLeft)
                .setDefaultColor(COLOR_TEXT_WHITE.get())
                .setEnabled(w -> mMachine));

        screenElements.widget(
            TextWidget
                .dynamicString(
                    () -> EnumChatFormatting.GOLD + "Output: "
                        + EnumChatFormatting.GREEN
                        + GTUtility.formatNumbers(Math.abs(mEUt))
                        + " EU/t")
                .setTextAlignment(Alignment.CenterLeft)
                .setDefaultColor(COLOR_TEXT_WHITE.get())
                .setEnabled(w -> mMachine));
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEMegaSteamTurbineArray(mName);
    }

    @Override
    public IStructureDefinition<MTEMegaSteamTurbineArray> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            STRUCTURE_DEFINITION = StructureDefinition.<MTEMegaSteamTurbineArray>builder()
                .addShape(STRUCTURE_PIECE_BASE_1, transpose(new String[][] { { "HHH", "HHH", "HHH" } }))
                .addShape(STRUCTURE_PIECE_BASE_2, transpose(new String[][] { { "H~H", "H H", "HHH" } }))
                .addShape(STRUCTURE_PIECE_BASE_3, transpose(new String[][] { { "HHH", "HHH", "HHH" } }))
                .addShape(STRUCTURE_PIECE_STACK, transpose(new String[][] { { "SSS", "SXS", "SSS" } })) // X=isAir()
                                                                                                        // 防止CAP被误识别为STACK
                .addShape(STRUCTURE_PIECE_STACK_HINT, transpose(new String[][] { { "SSS", "SXS", "SSS" } }))
                .addShape(STRUCTURE_PIECE_CAP, transpose(new String[][] { { "TTT", "TTT", "TTT" } }))
                .addElement('X', isAir())
                .addElement(
                    'H',
                    ofChain(
                        buildHatchAdder(MTEMegaSteamTurbineArray.class)
                            .adder(MTEMegaSteamTurbineArray::addPressureSteamToMachineList)
                            .hatchClass(MTEHatchPressureSteamInput.class)
                            .casingIndex(SOLID_STEEL_CASING_INDEX)
                            .dot(1)
                            .build(),
                        buildHatchAdder(MTEMegaSteamTurbineArray.class).atLeast(InputHatch, OutputHatch)
                            .casingIndex(SOLID_STEEL_CASING_INDEX)
                            .dot(1)
                            .build(),
                        buildHatchAdder(MTEMegaSteamTurbineArray.class).atLeast(Dynamo)
                            .casingIndex(SOLID_STEEL_CASING_INDEX)
                            .dot(1)
                            .buildAndChain(
                                onElementPass(
                                    MTEMegaSteamTurbineArray::onCasingAdded,
                                    ofBlocksTiered(
                                        MTEMegaSteamTurbineArray::getCasingTier,
                                        ALLOWED_CASINGS,
                                        -1,
                                        (t, tier) -> t.mCasingTier = tier,
                                        t -> t.mCasingTier)))))
                .addElement(
                    'S',
                    onElementPass(
                        MTEMegaSteamTurbineArray::onCasingAdded,
                        ofBlocksTiered(
                            MTEMegaSteamTurbineArray::getCasingTier,
                            ALLOWED_CASINGS,
                            -1,
                            (t, tier) -> t.mCasingTier = tier,
                            t -> t.mCasingTier)))
                .addElement(
                    'T',
                    onElementPass(
                        MTEMegaSteamTurbineArray::onCasingAdded,
                        ofBlocksTiered(
                            MTEMegaSteamTurbineArray::getCasingTier,
                            ALLOWED_CASINGS,
                            -1,
                            (t, tier) -> t.mCasingTier = tier,
                            t -> t.mCasingTier)))
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static final List<Pair<Block, Integer>> ALLOWED_CASINGS = ImmutableList.of(
        Pair.of(GregTechAPI.sBlockCasings2, 0),
        Pair.of(GregTechAPI.sBlockCasings1, 2),
        Pair.of(GregTechAPI.sBlockCasings4, 1),
        Pair.of(GregTechAPI.sBlockCasings4, 2),
        Pair.of(GregTechAPI.sBlockCasings4, 0),
        Pair.of(GregTechAPI.sBlockCasings8, 6),
        Pair.of(GregTechAPI.sBlockCasings8, 7));

    @Nullable
    public static Integer getCasingTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockCasings2 && meta == 0) return 1;
        if (block == GregTechAPI.sBlockCasings1 && meta == 2) return 2;
        if (block == GregTechAPI.sBlockCasings4) {
            if (meta == 1) return 3;
            if (meta == 2) return 4;
            if (meta == 0) return 5;
        }
        if (block == GregTechAPI.sBlockCasings8) {
            if (meta == 6) return 6;
            if (meta == 7) return 7;
        }
        return null;
    }

    private void onCasingAdded() {
        mCasingAmount++;
    }

    private boolean addPressureSteamToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity mte = aTileEntity.getMetaTileEntity();
        if (mte instanceof MTEHatchPressureSteamInput hatch) {
            hatch.updateTexture(aBaseCasingIndex);
            mPressureSteamInputs.add(hatch);
            return true;
        }
        return false;
    }

    private boolean hasPressureSteamHatch() {
        return !mPressureSteamInputs.isEmpty();
    }

    @Override
    public boolean checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack) {
        mCasingAmount = 0;
        mStackCount = 0;
        mCasingTier = -1;
        mEfficiency = 0;
        mPressureSteamInputs.clear();

        if (!checkPiece(STRUCTURE_PIECE_BASE_1, 1, -1, 0)) return false;
        if (!checkPiece(STRUCTURE_PIECE_BASE_2, 1, 0, 0)) return false;
        if (!checkPiece(STRUCTURE_PIECE_BASE_3, 1, 1, 0)) return false;

        mStackCount = 0;
        for (int i = 2; i < 12; i++) {
            if (!checkPiece(STRUCTURE_PIECE_STACK, 1, i, 0)) break;
            mStackCount++;
        }

        if (mStackCount < 0 || mStackCount > 10) return false;
        if (mCasingTier <= 0 || mCasingTier > 7) return false;

        int capY = 2 + mStackCount;
        if (!checkPiece(STRUCTURE_PIECE_CAP, 1, capY, 0)) return false;

        boolean hasInput = !mInputHatches.isEmpty() || hasPressureSteamHatch();
        if (!hasInput || mOutputHatches.isEmpty() || mDynamoHatches.isEmpty()) return false;

        updateAllHatchTextures();
        return true;
    }

    private int getStackLayers() {
        return Math.max(0, mStackCount);
    }

    private long getVoltage() {
        return GTValues.V[mCasingTier > 0 ? mCasingTier : 1];
    }

    private float getCustomEfficiency() {
        int eff = mEfficiency;
        if (eff <= 0) return 0.0f;
        return eff / 10000.0f;
    }

    private long calcSteamConsumption(boolean isHP) {
        int stackLayers = getStackLayers();
        long voltage = getVoltage();
        int n = stackLayers + 1;
        float savings = 0.05f * stackLayers;
        if (isHP) {
            long baseEU = voltage * 8 * n;
            return (long) (baseEU * Math.max(0, 1 - savings));
        } else {
            long baseEU = voltage * 4 * n;
            return (long) (baseEU * Math.max(0, 1 - savings) / 0.5f);
        }
    }

    @Override
    public CheckRecipeResult checkProcessing() {
        ArrayList<FluidStack> tFluids = getStoredFluids();
        if (tFluids.isEmpty() && mPressureSteamInputs.isEmpty()) {
            mEUt = 0;
            mTheoreticalEUt = 0;
            mSteamConsumption = 0;
            mEfficiency = Math.max(0, mEfficiency - 500);
            return CheckRecipeResultRegistry.NO_FUEL_FOUND;
        }

        int stackLayers = getStackLayers();
        long voltage = getVoltage();
        int n = stackLayers + 1;
        float efficiency = getCustomEfficiency();
        float savings = 0.05f * stackLayers;

        boolean hasSuperheated = false;
        boolean hasNormalSteam = false;
        for (FluidStack fs : tFluids) {
            if (GTModHandler.isSuperHeatedSteam(fs)) hasSuperheated = true;
            else if (GTModHandler.isAnySteam(fs)) hasNormalSteam = true;
        }

        for (MTEHatchPressureSteamInput hatch : mPressureSteamInputs) {
            FluidStack fs = hatch.getFluid();
            if (fs != null && fs.amount > 0) {
                if (GTModHandler.isAnySteam(fs) && !GTModHandler.isSuperHeatedSteam(fs)) hasNormalSteam = true;
            }
        }

        long generatedEUt;
        long steamConsumption;

        if (hasSuperheated) {
            generatedEUt = (long) (voltage * 8 * n * efficiency);
            steamConsumption = (long) (voltage * 8 * n * Math.max(0, 1 - savings));
            FluidStack superheated = getSuperheatedSteam();
            if (superheated == null || superheated.amount < steamConsumption) {
                if (hasNormalSteam) {
                    generatedEUt = (long) (voltage * 4 * n * efficiency);
                    long steamBaseEU = (long) (voltage * 4 * n * Math.max(0, 1 - savings));
                    steamConsumption = (long) (steamBaseEU / 0.5f);
                    this.mTheoreticalEUt = (int) generatedEUt;
                    this.mSteamConsumption = (int) steamConsumption;
                    depleteSteam((int) steamConsumption);
                    int waterOutput = condenseSteam((int) steamConsumption);
                    addOutput(GTModHandler.getDistilledWater(waterOutput));
                } else {
                    mEUt = 0;
                    mTheoreticalEUt = 0;
                    mSteamConsumption = 0;
                    mEfficiency = Math.max(0, mEfficiency - 500);
                    return CheckRecipeResultRegistry.NO_FUEL_FOUND;
                }
            } else {
                this.mTheoreticalEUt = (int) generatedEUt;
                this.mSteamConsumption = (int) steamConsumption;
                depleteSuperheatedSteam((int) steamConsumption);
                addOutput(gregtech.api.enums.Materials.Steam.getGas((int) steamConsumption));
            }
        } else if (hasNormalSteam) {
            generatedEUt = (long) (voltage * 4 * n * efficiency);
            long steamBaseEU = (long) (voltage * 4 * n * Math.max(0, 1 - savings));
            steamConsumption = (long) (steamBaseEU / 0.5f);
            this.mTheoreticalEUt = (int) generatedEUt;
            this.mSteamConsumption = (int) steamConsumption;
            depleteSteam((int) steamConsumption);
            int waterOutput = condenseSteam((int) steamConsumption);
            addOutput(GTModHandler.getDistilledWater(waterOutput));
        } else {
            mEUt = 0;
            mTheoreticalEUt = 0;
            mSteamConsumption = 0;
            mEfficiency = Math.max(0, mEfficiency - 500);
            return CheckRecipeResultRegistry.NO_FUEL_FOUND;
        }

        int difference = (int) (generatedEUt - mEUt);
        int maxChange = Math.max(10, Math.abs(difference) / 100);
        if (Math.abs(difference) > maxChange) {
            mEUt += maxChange * (difference > 0 ? 1 : -1);
        } else {
            mEUt = (int) generatedEUt;
        }

        mMaxProgresstime = 1;
        int maxEff = hasSuperheated ? MAX_EFFICIENCY_HP_STEAM : MAX_EFFICIENCY_STEAM;
        if (mEfficiency < 10000) {
            mEfficiencyIncrease = 10;
        } else if (mEfficiency < maxEff) {
            mEfficiencyIncrease = 1;
        } else {
            mEfficiencyIncrease = 0;
        }

        return CheckRecipeResultRegistry.GENERATING;
    }

    public long getMaximumOutput() {
        long aTotal = 0;
        for (MTEHatchDynamo aDynamo : GTUtility.validMTEList(mDynamoHatches)) {
            aTotal += aDynamo.maxAmperesOut() * aDynamo.maxEUOutput();
        }
        return aTotal;
    }

    private FluidStack getSuperheatedSteam() {
        for (FluidStack fs : getStoredFluids()) {
            if (GTModHandler.isSuperHeatedSteam(fs)) {
                return fs;
            }
        }
        return null;
    }

    private boolean depleteSteam(int amount) {
        int remaining = amount;
        for (FluidStack fs : getStoredFluids()) {
            if (GTModHandler.isAnySteam(fs) && !GTModHandler.isSuperHeatedSteam(fs)) {
                int canDrain = Math.min(fs.amount, remaining);
                if (canDrain > 0) {
                    depleteInput(new FluidStack(fs, canDrain));
                    remaining -= canDrain;
                }
                if (remaining <= 0) return true;
            }
        }
        for (MTEHatchPressureSteamInput hatch : mPressureSteamInputs) {
            FluidStack fs = hatch.getFluid();
            if (fs != null && GTModHandler.isAnySteam(fs) && !GTModHandler.isSuperHeatedSteam(fs)) {
                int canDrain = Math.min(fs.amount, remaining);
                if (canDrain > 0) {
                    hatch.drain(canDrain, true);
                    remaining -= canDrain;
                }
                if (remaining <= 0) return true;
            }
        }
        return remaining <= 0;
    }

    private boolean depleteSuperheatedSteam(int amount) {
        int remaining = amount;
        for (FluidStack fs : getStoredFluids()) {
            if (GTModHandler.isSuperHeatedSteam(fs)) {
                int canDrain = Math.min(fs.amount, remaining);
                if (canDrain > 0) {
                    depleteInput(new FluidStack(fs, canDrain));
                    remaining -= canDrain;
                }
                if (remaining <= 0) return true;
            }
        }
        for (MTEHatchPressureSteamInput hatch : mPressureSteamInputs) {
            FluidStack fs = hatch.getFluid();
            if (fs != null && GTModHandler.isSuperHeatedSteam(fs)) {
                int canDrain = Math.min(fs.amount, remaining);
                if (canDrain > 0) {
                    hatch.drain(canDrain, true);
                    remaining -= canDrain;
                }
                if (remaining <= 0) return true;
            }
        }
        return remaining <= 0;
    }

    private int condenseSteam(int steam) {
        excessWater += steam;
        int water = excessWater / GTValues.STEAM_PER_WATER;
        excessWater %= GTValues.STEAM_PER_WATER;
        return water;
    }

    @Override
    public String[] getInfoData() {
        int stackLayers = getStackLayers();
        long voltage = getVoltage();
        int n = stackLayers + 1;
        float eff = getCustomEfficiency();
        float savings = 0.05f * stackLayers;

        long maxEUt = voltage * 4 * n * 2;
        long maxHPEUt = voltage * 8 * n * 2;
        long steamCons = calcSteamConsumption(false);
        long hpSteamCons = calcSteamConsumption(true);

        return new String[] {
            EnumChatFormatting.GOLD + "EU/t: "
                + EnumChatFormatting.AQUA
                + GTUtility.formatNumbers(maxEUt)
                + EnumChatFormatting.GRAY
                + " (HP: "
                + EnumChatFormatting.RED
                + GTUtility.formatNumbers(maxHPEUt)
                + ")",
            EnumChatFormatting.GOLD + "Steam: "
                + EnumChatFormatting.AQUA
                + GTUtility.formatNumbers(steamCons)
                + " L/t"
                + EnumChatFormatting.GRAY
                + " (HP: "
                + EnumChatFormatting.RED
                + GTUtility.formatNumbers(hpSteamCons)
                + ")",
            EnumChatFormatting.GOLD + "Savings: " + EnumChatFormatting.GREEN + String.format("%.0f%%", savings * 100),
            EnumChatFormatting.GOLD + "Efficiency: "
                + (eff >= 2.0f ? EnumChatFormatting.LIGHT_PURPLE
                    : eff >= 1.0f ? EnumChatFormatting.GREEN : EnumChatFormatting.YELLOW)
                + String.format("%.1f%%", eff * 100)
                + (eff >= 2.0f ? " MAX" : "") };
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setInteger("excessWater", excessWater);
        aNBT.setInteger("mCasingTier", mCasingTier);
        aNBT.setInteger("mTheoreticalEUt", mTheoreticalEUt);
        aNBT.setInteger("mSteamConsumption", mSteamConsumption);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        excessWater = aNBT.getInteger("excessWater");
        mCasingTier = aNBT.getInteger("mCasingTier");
        mTheoreticalEUt = aNBT.getInteger("mTheoreticalEUt");
        mSteamConsumption = aNBT.getInteger("mSteamConsumption");
    }

    @Override
    public boolean addToMachineList(IGregTechTileEntity tTileEntity, int aBaseCasingIndex) {
        return addPressureSteamToMachineList(tTileEntity, aBaseCasingIndex)
            || addInputToMachineList(tTileEntity, aBaseCasingIndex)
            || addOutputToMachineList(tTileEntity, aBaseCasingIndex)
            || addDynamoToMachineList(tTileEntity, aBaseCasingIndex);
    }

    private void updateAllHatchTextures() {
        int textureIndex = getCasingTextureIndex();
        for (MTEHatchPressureSteamInput hatch : mPressureSteamInputs) {
            hatch.updateTexture(textureIndex);
        }
        for (var inputHatch : GTUtility.validMTEList(mInputHatches)) {
            inputHatch.updateTexture(textureIndex);
        }
        for (var outputHatch : GTUtility.validMTEList(mOutputHatches)) {
            outputHatch.updateTexture(textureIndex);
        }
        for (var dynamoHatch : GTUtility.validMTEList(mDynamoHatches)) {
            dynamoHatch.updateTexture(textureIndex);
        }
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece(STRUCTURE_PIECE_BASE_1, stackSize, hintsOnly, 1, -1, 0);
        buildPiece(STRUCTURE_PIECE_BASE_2, stackSize, hintsOnly, 1, 0, 0);
        buildPiece(STRUCTURE_PIECE_BASE_3, stackSize, hintsOnly, 1, 1, 0);
        int tTotalHeight = Math.max(3, GTStructureChannels.STRUCTURE_HEIGHT.getValueClamped(stackSize, 3, 13));
        for (int i = 2; i < tTotalHeight - 1; i++) {
            buildPiece(STRUCTURE_PIECE_STACK_HINT, stackSize, hintsOnly, 1, i, 0);
        }
        buildPiece(STRUCTURE_PIECE_CAP, stackSize, hintsOnly, 1, tTotalHeight - 1, 0);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        int built = survivalBuildPiece(STRUCTURE_PIECE_BASE_1, stackSize, 1, -1, 0, elementBudget, env, false, true);
        if (built >= 0) return built;
        built = survivalBuildPiece(STRUCTURE_PIECE_BASE_2, stackSize, 1, 0, 0, elementBudget, env, false, true);
        if (built >= 0) return built;
        built = survivalBuildPiece(STRUCTURE_PIECE_BASE_3, stackSize, 1, 1, 0, elementBudget, env, false, true);
        if (built >= 0) return built;
        int tTotalHeight = Math.max(3, GTStructureChannels.STRUCTURE_HEIGHT.getValueClamped(stackSize, 3, 13));
        for (int i = 2; i < tTotalHeight - 1; i++) {
            built = survivalBuildPiece(STRUCTURE_PIECE_STACK_HINT, stackSize, 1, i, 0, elementBudget, env, false, true);
            if (built >= 0) return built;
        }
        return survivalBuildPiece(
            STRUCTURE_PIECE_CAP,
            stackSize,
            1,
            tTotalHeight - 1,
            0,
            elementBudget,
            env,
            false,
            true);
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
        return false;
    }

    public String getMachineType() {
        return "Mega Steam Turbine Array";
    }

    @Override
    public int getMaxEfficiency(ItemStack aStack) {
        return 20000;
    }

    public int getTierRecipes() {
        return 0;
    }

    @Override
    public boolean supportsPowerPanel() {
        return false;
    }

    @Override
    public String[] getStructureDescription(ItemStack stackSize) {
        return new String[0];
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(getMachineType())
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.mega_steam_turbine.info"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.mega_steam_turbine.info2"))
            .addSeparator()
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.mega_steam_turbine.info3"))
            .beginStructureBlock(3, 5, 3, true)
            .addController(StatCollector.translateToLocal("gtsr.tooltip.mega_steam_turbine.controller"))
            .addInputHatch(StatCollector.translateToLocal("gtsr.tooltip.mega_steam_turbine.input"), 1)
            .addDynamoHatch(StatCollector.translateToLocal("gtsr.tooltip.mega_steam_turbine.dynamo"), 1)
            .addOutputHatch(StatCollector.translateToLocal("gtsr.tooltip.mega_steam_turbine.output"), 1)
            .toolTipFinisher();
        return tt;
    }

    private int getCasingTextureIndex() {
        switch (mCasingTier) {
            case 1:
                return GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings2, 0);
            case 2:
                return GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings1, 2);
            case 3:
                return GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings4, 1);
            case 4:
                return GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings4, 2);
            case 5:
                return GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings4, 0);
            case 6:
                return GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings8, 6);
            case 7:
                return GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings8, 7);
            default:
                return SOLID_STEEL_CASING_INDEX;
        }
    }

    @Override
    public void onFirstTick(IGregTechTileEntity aBaseMetaTileEntity) {
        super.onFirstTick(aBaseMetaTileEntity);
        setTurbineOverlay();
    }

    protected void setTurbineOverlay() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        if (tile.isServerSide()) return;

        IIconContainer[] tTextures;
        if (tile.isActive()) tTextures = getTurbineTextureActive();
        else if (mMachine) tTextures = getTurbineTextureFull();
        else tTextures = getTurbineTextureEmpty();

        GTUtilityClient.setTurbineOverlay(
            tile.getWorld(),
            tile.getXCoord(),
            tile.getYCoord(),
            tile.getZCoord(),
            getExtendedFacing(),
            tTextures,
            overlayTickets);
    }

    public IIconContainer[] getTurbineTextureActive() {
        return Textures.BlockIcons.TURBINE_NEW_ACTIVE;
    }

    public IIconContainer[] getTurbineTextureFull() {
        return Textures.BlockIcons.TURBINE_NEW;
    }

    public IIconContainer[] getTurbineTextureEmpty() {
        return Textures.BlockIcons.TURBINE_NEW_EMPTY;
    }

    @Override
    public void onTextureUpdate() {
        setTurbineOverlay();
    }

    @Override
    public void onRemoval() {
        super.onRemoval();
        if (getBaseMetaTileEntity().isClientSide()) GTUtilityClient.clearTurbineOverlay(overlayTickets);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int aColorIndex, boolean aActive, boolean aRedstone) {
        int casingIndex = getCasingTextureIndex();
        if (side == facing) {
            return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(casingIndex),
                aActive ? TextureFactory.of(Textures.BlockIcons.LARGETURBINE_NEW_ACTIVE5)
                    : TextureFactory.of(Textures.BlockIcons.LARGETURBINE_NEW5) };
        }
        return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(casingIndex) };
    }
}
