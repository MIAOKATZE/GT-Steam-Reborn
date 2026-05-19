package com.miaokatze.gtsr.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.HatchElement.Dynamo;
import static gregtech.api.enums.HatchElement.InputHatch;
import static gregtech.api.enums.HatchElement.OutputHatch;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.ArrayList;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEHatchDynamo;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTUtility;
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

    private static final int CASING_INDEX = GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings2, 0);

    private static IStructureDefinition<MTEMegaSteamTurbineArray> STRUCTURE_DEFINITION;

    private int mCasingAmount = 0;
    private int mStackCount = 0;
    private int mTotalHeight = 0;
    private int excessWater = 0;

    public MTEMegaSteamTurbineArray(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTEMegaSteamTurbineArray(String aName) {
        super(aName);
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
                .addShape(STRUCTURE_PIECE_BASE_2, transpose(new String[][] { { "HHH", "H~H", "HHH" } }))
                .addShape(STRUCTURE_PIECE_BASE_3, transpose(new String[][] { { "HHH", "HHH", "HHH" } }))
                .addShape(STRUCTURE_PIECE_STACK, transpose(new String[][] { { "SSS", "S S", "SSS" } }))
                .addShape(STRUCTURE_PIECE_STACK_HINT, transpose(new String[][] { { "SSS", "S S", "SSS" } }))
                .addShape(STRUCTURE_PIECE_CAP, transpose(new String[][] { { "TTT", "TTT", "TTT" } }))
                .addElement(
                    'H',
                    buildHatchAdder(MTEMegaSteamTurbineArray.class).atLeast(InputHatch, OutputHatch, Dynamo)
                        .casingIndex(CASING_INDEX)
                        .dot(1)
                        .buildAndChain(
                            onElementPass(
                                MTEMegaSteamTurbineArray::onCasingAdded,
                                ofBlock(GregTechAPI.sBlockCasings2, 0))))
                .addElement(
                    'S',
                    onElementPass(MTEMegaSteamTurbineArray::onCasingAdded, ofBlock(GregTechAPI.sBlockCasings2, 0)))
                .addElement(
                    'T',
                    onElementPass(MTEMegaSteamTurbineArray::onCasingAdded, ofBlock(GregTechAPI.sBlockCasings2, 0)))
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    private void onCasingAdded() {
        mCasingAmount++;
    }

    @Override
    public boolean checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack) {
        mCasingAmount = 0;
        mStackCount = 0;
        mTotalHeight = 0;

        if (!checkPiece(STRUCTURE_PIECE_BASE_1, 1, -1, 1)) return false;
        if (!checkPiece(STRUCTURE_PIECE_BASE_2, 1, 0, 1)) return false;
        if (!checkPiece(STRUCTURE_PIECE_BASE_3, 1, 1, 1)) return false;
        mTotalHeight = 3;

        mStackCount = 0;
        for (int i = 2; i < 12; i++) {
            if (!checkPiece(STRUCTURE_PIECE_STACK, 1, i, 1)) break;
            mStackCount++;
            mTotalHeight++;
        }

        if (mStackCount < 1 || mStackCount > 10) return false;

        if (!checkPiece(STRUCTURE_PIECE_CAP, 1, 2 + mStackCount, 1)) return false;
        mTotalHeight++;

        return mInputHatches.size() >= 1 && mOutputHatches.size() >= 1 && mDynamoHatches.size() >= 1;
    }

    private int getStackLayers() {
        return Math.max(1, mStackCount / 2);
    }

    private static final long[] V = GTValues.V;

    @Override
    public CheckRecipeResult checkProcessing() {
        ArrayList<FluidStack> tFluids = getStoredFluids();
        if (tFluids.isEmpty()) {
            mEUt = 0;
            mEfficiency = 0;
            return CheckRecipeResultRegistry.NO_FUEL_FOUND;
        }

        int stackLayers = getStackLayers();
        if (stackLayers <= 0) {
            return CheckRecipeResultRegistry.NO_FUEL_FOUND;
        }

        long voltage = V[1];
        float efficiency = Math.min(0.60f + (stackLayers - 1) * 0.07f, 0.95f);

        boolean hasSuperheated = false;
        boolean hasNormalSteam = false;
        for (FluidStack fs : tFluids) {
            if (GTModHandler.isSuperHeatedSteam(fs)) hasSuperheated = true;
            else if (GTModHandler.isAnySteam(fs)) hasNormalSteam = true;
        }

        long generatedEUt;
        long steamConsumption;

        if (hasSuperheated) {
            generatedEUt = (long) (voltage * 16 * stackLayers * efficiency);
            steamConsumption = (long) (voltage * 4 * stackLayers * (1 - 0.03 * stackLayers));
            FluidStack superheated = getSuperheatedSteam();
            if (superheated == null || superheated.amount < steamConsumption) {
                if (hasNormalSteam) {
                    generatedEUt = (long) (voltage * 4 * stackLayers * efficiency);
                    steamConsumption = (long) (voltage * 4 * stackLayers * (1 - 0.025 * stackLayers));
                    depleteSteam((int) steamConsumption);
                    int waterOutput = condenseSteam((int) steamConsumption);
                    addOutput(GTModHandler.getDistilledWater(waterOutput));
                } else {
                    mEUt = 0;
                    return CheckRecipeResultRegistry.NO_FUEL_FOUND;
                }
            } else {
                depleteSuperheatedSteam((int) steamConsumption);
                addOutput(gregtech.api.enums.Materials.Steam.getGas((int) steamConsumption));
            }
        } else if (hasNormalSteam) {
            generatedEUt = (long) (voltage * 4 * stackLayers * efficiency);
            steamConsumption = (long) (voltage * 4 * stackLayers * (1 - 0.025 * stackLayers));
            depleteSteam((int) steamConsumption);
            int waterOutput = condenseSteam((int) steamConsumption);
            addOutput(GTModHandler.getDistilledWater(waterOutput));
        } else {
            mEUt = 0;
            return CheckRecipeResultRegistry.NO_FUEL_FOUND;
        }

        long maxOutput = getMaximumOutput();
        if (generatedEUt > maxOutput) {
            generatedEUt = maxOutput;
        }

        int difference = (int) (generatedEUt - mEUt);
        int maxChange = Math.max(10, Math.abs(difference) / 100);
        if (Math.abs(difference) > maxChange) {
            mEUt += maxChange * (difference > 0 ? 1 : -1);
        } else {
            mEUt = (int) generatedEUt;
        }

        mMaxProgresstime = 1;
        mEfficiencyIncrease = 10;
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
        for (FluidStack fs : getStoredFluids()) {
            if (GTModHandler.isAnySteam(fs) && !GTModHandler.isSuperHeatedSteam(fs)) {
                if (fs.amount >= amount) {
                    depleteInput(new FluidStack(fs, amount));
                    return true;
                }
            }
        }
        return false;
    }

    private boolean depleteSuperheatedSteam(int amount) {
        for (FluidStack fs : getStoredFluids()) {
            if (GTModHandler.isSuperHeatedSteam(fs)) {
                if (fs.amount >= amount) {
                    depleteInput(new FluidStack(fs, amount));
                    return true;
                }
            }
        }
        return false;
    }

    private int condenseSteam(int steam) {
        excessWater += steam;
        int water = excessWater / GTValues.STEAM_PER_WATER;
        excessWater %= GTValues.STEAM_PER_WATER;
        return water;
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setInteger("excessWater", excessWater);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        excessWater = aNBT.getInteger("excessWater");
    }

    @Override
    public boolean addToMachineList(IGregTechTileEntity tTileEntity, int aBaseCasingIndex) {
        return addInputToMachineList(tTileEntity, CASING_INDEX) || addOutputToMachineList(tTileEntity, CASING_INDEX)
            || addDynamoToMachineList(tTileEntity, CASING_INDEX);
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece(STRUCTURE_PIECE_BASE_1, stackSize, hintsOnly, 1, -1, 1);
        buildPiece(STRUCTURE_PIECE_BASE_2, stackSize, hintsOnly, 1, 0, 1);
        buildPiece(STRUCTURE_PIECE_BASE_3, stackSize, hintsOnly, 1, 1, 1);
        int tTotalHeight = Math.max(3, GTStructureChannels.STRUCTURE_HEIGHT.getValueClamped(stackSize, 3, 12));
        for (int i = 2; i < tTotalHeight - 1; i++) {
            buildPiece(STRUCTURE_PIECE_STACK_HINT, stackSize, hintsOnly, 1, i, 1);
        }
        buildPiece(STRUCTURE_PIECE_CAP, stackSize, hintsOnly, 1, tTotalHeight - 1, 1);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        int built = survivalBuildPiece(STRUCTURE_PIECE_BASE_1, stackSize, 1, -1, 1, elementBudget, env, false, true);
        if (built >= 0) return built;
        built = survivalBuildPiece(STRUCTURE_PIECE_BASE_2, stackSize, 1, 0, 1, elementBudget, env, false, true);
        if (built >= 0) return built;
        built = survivalBuildPiece(STRUCTURE_PIECE_BASE_3, stackSize, 1, 1, 1, elementBudget, env, false, true);
        if (built >= 0) return built;
        int tTotalHeight = Math.max(3, GTStructureChannels.STRUCTURE_HEIGHT.getValueClamped(stackSize, 3, 12));
        for (int i = 2; i < tTotalHeight - 1; i++) {
            built = survivalBuildPiece(STRUCTURE_PIECE_STACK_HINT, stackSize, 1, i, 1, elementBudget, env, false, true);
            if (built >= 0) return built;
        }
        return survivalBuildPiece(
            STRUCTURE_PIECE_CAP,
            stackSize,
            1,
            tTotalHeight - 1,
            1,
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
        return 10000;
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
        return new String[] { EnumChatFormatting.AQUA + "Structure:",
            "1. 3x3 base: 3 layers with Input Hatches, Dynamo Hatches, Output Hatches",
            "2. Stack layers: 3x3 hollow casing (min 2, max 10 layers)", "3. Cap: 3x3 solid casing layer on top",
            "4. At least 1 Input Hatch, 1 Dynamo Hatch, and 1 Output Hatch required" };
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

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int aColorIndex, boolean aActive, boolean aRedstone) {
        if (side == facing) {
            return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(CASING_INDEX),
                aActive ? TextureFactory.of(Textures.BlockIcons.LARGETURBINE_NEW_ACTIVE5)
                    : TextureFactory.of(Textures.BlockIcons.LARGETURBINE_NEW5) };
        }
        return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(CASING_INDEX) };
    }
}
