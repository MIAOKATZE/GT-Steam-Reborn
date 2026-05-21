package com.miaokatze.gtsr.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlocksTiered;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.GTValues.emptyItemStackArray;
import static gregtech.api.enums.HatchElement.InputBus;
import static gregtech.api.enums.HatchElement.InputHatch;
import static gregtech.api.enums.HatchElement.OutputBus;
import static gregtech.api.enums.HatchElement.OutputHatch;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ORE_DRILL;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ORE_DRILL_ACTIVE;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.ImmutableList;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps;
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTESteamMultiBase;

public class MTEAmmoniaPlant extends MTESteamMultiBase<MTEAmmoniaPlant> implements ISurvivalConstructable {

    private static final int HEAT_MAX = 10000;
    private static final int HEAT_INCREASE_PER_TICK = 10;
    private static final int HEAT_DECREASE_PER_TICK = 50;
    private static final int MAINTAIN_STEAM_PER_SEC = 12000;
    private static final int MAINTAIN_REFINERY_GAS_PER_SEC = 200;
    private static final int PREHEAT_STEAM_PER_SEC = 8000;
    private static final int PREHEAT_REFINERY_GAS_PER_SEC = 200;
    private static final int EXTRA_STEAM_FORMULA_CONSTANT = 16000;

    private static final int[][] CATALYST_DATA = { { 64, 64 }, { 96, 48 }, { 128, 64 }, { 192, 24 }, { 256, 16 },
        { 256, 8 }, { 256, 4 }, { 256, 1 } };

    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static IStructureDefinition<MTEAmmoniaPlant> STRUCTURE_DEFINITION = null;

    private int mHeatLevel = 0;
    private boolean mOverheatShutdown = false;
    private int mCatalystType = 0;
    private int mReactionTimeSec = 64;
    private int mParallelCount = 64;
    private long mRealtimeSteamCost = 0;
    private long mRealtimeSteamOutput = 0;

    private int mCasingTier = 0;
    private int mCasingCount = 0;

    private static final int BRONZE_CASING_INDEX = 10;

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
                        new String[][] { { "CCC", "CCC", "CCC" }, { "C~C", "C C", "CCC" }, { "CCC", "CCC", "CCC" } }))
                .addElement(
                    'C',
                    ofChain(
                        buildHatchAdder(MTEAmmoniaPlant.class).atLeast(InputBus, InputHatch, OutputBus, OutputHatch)
                            .casingIndex(BRONZE_CASING_INDEX)
                            .dot(1)
                            .buildAndChain(
                                onElementPass(
                                    MTEAmmoniaPlant::onCasingAdded,
                                    ofBlocksTiered(
                                        MTEAmmoniaPlant::getCasingTier,
                                        ImmutableList.of(
                                            Pair.of(GregTechAPI.sBlockCasings1, 10),
                                            Pair.of(GregTechAPI.sBlockCasings2, 0)),
                                        -1,
                                        (MTEAmmoniaPlant t,
                                            Integer tier) -> t.mCasingTier = Math.max(t.mCasingTier, tier),
                                        (MTEAmmoniaPlant t) -> t.mCasingTier)))))
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    @Nullable
    private static Integer getCasingTier(Block aBlock, int aMeta) {
        if (aBlock == GregTechAPI.sBlockCasings1 && aMeta == 10) return 1;
        if (aBlock == GregTechAPI.sBlockCasings2 && aMeta == 0) return 2;
        return null;
    }

    private void onCasingAdded() {
        mCasingCount++;
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece(STRUCTURE_PIECE_MAIN, stackSize, hintsOnly, 1, 1, 0);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        return survivialBuildPiece(STRUCTURE_PIECE_MAIN, stackSize, 1, 1, 0, elementBudget, env, false, true);
    }

    @Override
    public boolean checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack) {
        mCasingTier = 0;
        mCasingCount = 0;
        if (!checkPiece(STRUCTURE_PIECE_MAIN, 1, 1, 0)) return false;
        if (mCasingCount < 10) return false;
        if (mCasingTier < 1) return false;
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
        if (getBaseMetaTileEntity().isServerSide()) {
            if (mOverheatShutdown) {
                return SimpleCheckRecipeResult.ofFailure("overheat_shutdown");
            }

            if (mHeatLevel < HEAT_MAX) {
                return doPreheat();
            }

            return doRecipeProcessing();
        }
        return CheckRecipeResultRegistry.NO_RECIPE;
    }

    private CheckRecipeResult doPreheat() {
        int steamPerTick = PREHEAT_STEAM_PER_SEC / 20;
        int gasPerTick = PREHEAT_REFINERY_GAS_PER_SEC / 20;

        if (!depleteRefineryGas(gasPerTick)) {
            stopMachine();
            return SimpleCheckRecipeResult.ofFailure("no_refinery_gas");
        }
        if (!tryConsumeSteam(steamPerTick)) {
            stopMachine();
            return SimpleCheckRecipeResult.ofFailure("no_steam");
        }

        outputSuperheatedSteam(steamPerTick);

        mHeatLevel = Math.min(HEAT_MAX, mHeatLevel + HEAT_INCREASE_PER_TICK);
        mMaxProgresstime = 20;
        lEUt = 0;
        mEfficiencyIncrease = 0;
        mOutputItems = emptyItemStackArray;
        mOutputFluids = null;
        updateSlots();
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    private CheckRecipeResult doRecipeProcessing() {
        int steamPerTick = MAINTAIN_STEAM_PER_SEC / 20;
        int gasPerTick = MAINTAIN_REFINERY_GAS_PER_SEC / 20;

        if (!depleteRefineryGas(gasPerTick)) {
            stopMachine();
            return SimpleCheckRecipeResult.ofFailure("no_refinery_gas");
        }
        if (!tryConsumeSteam(steamPerTick)) {
            stopMachine();
            return SimpleCheckRecipeResult.ofFailure("no_steam");
        }

        outputSuperheatedSteam(steamPerTick);

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
            lEUt = 0;
        } else {
            mMaxProgresstime = 20;
            lEUt = 0;
            mEfficiencyIncrease = 0;
            mOutputItems = emptyItemStackArray;
            mOutputFluids = null;
        }

        updateSlots();
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    @Override
    public boolean onRunningTick(ItemStack aStack) {
        if (getBaseMetaTileEntity().isServerSide()) {
            if (mHeatLevel >= HEAT_MAX && mMaxProgresstime > 20) {
                long extraSteamPerSec = (long) mParallelCount * EXTRA_STEAM_FORMULA_CONSTANT / mReactionTimeSec;
                long extraSteamPerTick = extraSteamPerSec / 20;

                if (!tryConsumeSteam((int) Math.min(extraSteamPerTick, Integer.MAX_VALUE))) {
                    mOverheatShutdown = true;
                    stopMachine();
                    return false;
                }

                outputSuperheatedSteam((int) Math.min(extraSteamPerTick, Integer.MAX_VALUE));
                mRealtimeSteamCost = MAINTAIN_STEAM_PER_SEC + extraSteamPerSec;
                mRealtimeSteamOutput = MAINTAIN_STEAM_PER_SEC + extraSteamPerSec;
            } else {
                mRealtimeSteamCost = mHeatLevel < HEAT_MAX ? PREHEAT_STEAM_PER_SEC : MAINTAIN_STEAM_PER_SEC;
                mRealtimeSteamOutput = mRealtimeSteamCost;
            }
        }
        return true;
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (aBaseMetaTileEntity.isServerSide()) {
            if (!mMachine && mHeatLevel > 0) {
                mHeatLevel = Math.max(0, mHeatLevel - HEAT_DECREASE_PER_TICK);
            }
        }
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
        for (MTEHatch tHatch : GTUtility.validMTEList(mSteamInputFluids)) {
            if (tHatch instanceof MTEHatchInput) {
                MTEHatchInput inputHatch = (MTEHatchInput) tHatch;
                FluidStack tLiquid = inputHatch.getFluid();
                if (tLiquid != null && tLiquid.isFluidEqual(refineryGas)) {
                    FluidStack drained = inputHatch.drain(amount, false);
                    if (drained != null && drained.amount >= amount) {
                        inputHatch.drain(amount, true);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void outputSuperheatedSteam(int amount) {
        if (amount <= 0) return;
        net.minecraftforge.fluids.Fluid superheated = net.minecraftforge.fluids.FluidRegistry
            .getFluid("ic2superheatedsteam");
        if (superheated != null) {
            addOutput(new FluidStack(superheated, amount));
        }
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setInteger("mHeatLevel", mHeatLevel);
        aNBT.setBoolean("mOverheatShutdown", mOverheatShutdown);
        aNBT.setInteger("mCatalystType", mCatalystType);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mHeatLevel = aNBT.getInteger("mHeatLevel");
        mOverheatShutdown = aNBT.getBoolean("mOverheatShutdown");
        mCatalystType = aNBT.getInteger("mCatalystType");
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int aColorIndex, boolean aActive, boolean aRedstone) {
        if (side == facing) {
            return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(BRONZE_CASING_INDEX),
                aActive ? getFrontOverlayActive() : getFrontOverlay() };
        }
        return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(BRONZE_CASING_INDEX) };
    }

    @Override
    protected ITexture getFrontOverlay() {
        return TextureFactory.of(OVERLAY_FRONT_ORE_DRILL);
    }

    @Override
    protected ITexture getFrontOverlayActive() {
        return TextureFactory.of(OVERLAY_FRONT_ORE_DRILL_ACTIVE);
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal("gtsr.tooltip.ammonia_plant.type"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.ammonia_plant.info"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.ammonia_plant.info2"))
            .addSeparator()
            .beginStructureBlock(3, 3, 3, false)
            .addController(StatCollector.translateToLocal("gtsr.tooltip.ammonia_plant.controller"))
            .addInputBus(StatCollector.translateToLocal("gtsr.tooltip.ammonia_plant.input_bus"), 1)
            .addInputHatch(StatCollector.translateToLocal("gtsr.tooltip.ammonia_plant.input_hatch"), 1)
            .addOutputBus(StatCollector.translateToLocal("gtsr.tooltip.ammonia_plant.output_bus"), 1)
            .addOutputHatch(StatCollector.translateToLocal("gtsr.tooltip.ammonia_plant.output_hatch"), 1)
            .toolTipFinisher("GTSR");
        return tt;
    }

    @Override
    public String getMachineType() {
        return "Ammonia Plant";
    }

    @Override
    public int getTierRecipes() {
        return 1;
    }

    @Override
    protected void outputAfterRecipe() {
        super.outputAfterRecipe();
    }
}
