package com.miaokatze.gtsr.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlocksTiered;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.HatchElement.Energy;
import static gregtech.api.enums.HatchElement.InputBus;
import static gregtech.api.enums.HatchElement.InputHatch;
import static gregtech.api.enums.HatchElement.OutputBus;
import static gregtech.api.enums.HatchElement.OutputHatch;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ORE_DRILL;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ORE_DRILL_ACTIVE;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ORE_DRILL_ACTIVE_GLOW;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ORE_DRILL_GLOW;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
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
import com.miaokatze.gtsr.common.machine.base.MTEPressureSteamCoolingHatch;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEBasicMachineWithRecipe;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.api.recipe.metadata.CompressionTierKey;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;

public class MTEKineticProcessingArray extends MTEEnhancedMultiBlockBase<MTEKineticProcessingArray>
    implements IConstructable, ISurvivalConstructable {

    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static final int HORIZONTAL_OFF_SET = 3;
    private static final int VERTICAL_OFF_SET = 2;
    private static final int DEPTH_OFF_SET = 2;
    private static final int SOLID_STEEL_CASING_INDEX = GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings2, 0);
    private static IStructureDefinition<MTEKineticProcessingArray> STRUCTURE_DEFINITION;

    private static final double[] STEAM_CONSUMPTION_RATES = { 8.0, 4.0, 2.0, 1.0, 0.5, 0.25, 0.1, 0.1, 0.1, 0.1, 0.1,
        0.1, 0.1, 0.1, 0.1, 0.1 };

    private int mCasingAmount = 0;
    private int mCasingTier = -1;

    private ItemStack internalMachineStack = null;
    private RecipeMap<?> recipeMap = null;
    private long voltage = 0;
    private int mMachineTier = 0;
    private int maxParallel = 0;
    private int mStackSize = 0;

    private final List<MTEHatchPressureSteamInput> mPressureSteamInputs = new ArrayList<>();
    private final List<MTEPressureSteamCoolingHatch> mPressureCoolingHatches = new ArrayList<>();

    private double mSteamRate = 0;
    private long mSteamPerAmp = 0;
    private long mRealtimeSteamCost = 0;
    private int mParallelCount = 0;
    private String mMachineName = "";

    public MTEKineticProcessingArray(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTEKineticProcessingArray(String aName) {
        super(aName);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEKineticProcessingArray(mName);
    }

    @Override
    public IStructureDefinition<MTEKineticProcessingArray> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            STRUCTURE_DEFINITION = StructureDefinition.<MTEKineticProcessingArray>builder()
                .addShape(
                    STRUCTURE_PIECE_MAIN,
                    transpose(
                        new String[][] {
                            { "  BBB  ", " BDDDB ", "BDCBCDB", "BDBBBDB", "BDCBCDB", " BDDDB ", "  BBB  " },
                            { "       ", " EB BE ", " BCBCB ", "  BDB  ", " BCBCB ", " EB BE ", "       " },
                            { "       ", " E   E ", "  C~C  ", "  BDB  ", "  CBC  ", " E   E ", "       " },
                            { "       ", " EB BE ", " BBBBB ", "  BDB  ", " BBBBB ", " EB BE ", "       " },
                            { "  BBB  ", " BBBBB ", "BBBBBBB", "BBBBBBB", "BBBBBBB", " BBBBB ", "  BBB  " } }))
                .addElement(
                    'B',
                    ofChain(
                        buildHatchAdder(MTEKineticProcessingArray.class)
                            .atLeast(InputBus, InputHatch, OutputBus, OutputHatch, Energy)
                            .casingIndex(SOLID_STEEL_CASING_INDEX)
                            .dot(1)
                            .buildAndChain(
                                onElementPass(
                                    MTEKineticProcessingArray::onCasingAdded,
                                    ofBlocksTiered(
                                        MTEKineticProcessingArray::getCasingTier,
                                        ALLOWED_CASINGS,
                                        -1,
                                        (t, tier) -> t.mCasingTier = tier,
                                        t -> t.mCasingTier))),
                        buildHatchAdder(MTEKineticProcessingArray.class)
                            .adder(MTEKineticProcessingArray::addPressureSteamToMachineList)
                            .hatchClass(MTEHatchPressureSteamInput.class)
                            .casingIndex(SOLID_STEEL_CASING_INDEX)
                            .dot(2)
                            .build(),
                        buildHatchAdder(MTEKineticProcessingArray.class)
                            .adder(MTEKineticProcessingArray::addPressureCoolingToMachineList)
                            .hatchClass(MTEPressureSteamCoolingHatch.class)
                            .casingIndex(SOLID_STEEL_CASING_INDEX)
                            .dot(3)
                            .build()))
                .addElement(
                    'C',
                    onElementPass(
                        MTEKineticProcessingArray::onCasingAdded,
                        ofBlocksTiered(
                            MTEKineticProcessingArray::getPipeTier,
                            PIPE_CASINGS,
                            -1,
                            (t, tier) -> t.mCasingTier = Math.max(t.mCasingTier, tier),
                            t -> t.mCasingTier)))
                .addElement(
                    'D',
                    onElementPass(
                        MTEKineticProcessingArray::onCasingAdded,
                        ofBlocksTiered(
                            MTEKineticProcessingArray::getGearTier,
                            GEAR_CASINGS,
                            -1,
                            (t, tier) -> t.mCasingTier = Math.max(t.mCasingTier, tier),
                            t -> t.mCasingTier)))
                .addElement(
                    'E',
                    onElementPass(
                        MTEKineticProcessingArray::onCasingAdded,
                        ofBlocksTiered(
                            MTEKineticProcessingArray::getFrameTier,
                            FRAME_CASINGS,
                            -1,
                            (t, tier) -> t.mCasingTier = Math.max(t.mCasingTier, tier),
                            t -> t.mCasingTier)))
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static final List<Pair<Block, Integer>> ALLOWED_CASINGS = ImmutableList.of(
        Pair.of(GregTechAPI.sBlockCasings2, 0),
        Pair.of(GregTechAPI.sBlockCasings4, 1),
        Pair.of(GregTechAPI.sBlockCasings4, 2),
        Pair.of(GregTechAPI.sBlockCasings4, 0),
        Pair.of(GregTechAPI.sBlockCasings8, 5),
        Pair.of(GregTechAPI.sBlockCasings8, 6),
        Pair.of(GregTechAPI.sBlockCasings8, 7));

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static final List<Pair<Block, Integer>> PIPE_CASINGS = ImmutableList.of(
        Pair.of(GregTechAPI.sBlockCasings2, 13),
        Pair.of(GregTechAPI.sBlockCasings4, 11),
        Pair.of(GregTechAPI.sBlockCasings8, 1));

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static final List<Pair<Block, Integer>> GEAR_CASINGS = ImmutableList
        .of(Pair.of(GregTechAPI.sBlockCasings2, 3), Pair.of(GregTechAPI.sBlockCasings4, 9));

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static final List<Pair<Block, Integer>> FRAME_CASINGS = ImmutableList.of(
        Pair.of(GregTechAPI.sBlockFrames, Materials.Steel.mMetaItemSubID),
        Pair.of(GregTechAPI.sBlockFrames, Materials.Aluminium.mMetaItemSubID),
        Pair.of(GregTechAPI.sBlockFrames, Materials.StainlessSteel.mMetaItemSubID),
        Pair.of(GregTechAPI.sBlockFrames, Materials.Titanium.mMetaItemSubID),
        Pair.of(GregTechAPI.sBlockFrames, Materials.TungstenSteel.mMetaItemSubID),
        Pair.of(GregTechAPI.sBlockFrames, Materials.Iridium.mMetaItemSubID),
        Pair.of(GregTechAPI.sBlockFrames, Materials.NaquadahAlloy.mMetaItemSubID));

    @Nullable
    public static Integer getCasingTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockCasings2 && meta == 0) return 1;
        if (block == GregTechAPI.sBlockCasings4) {
            if (meta == 1) return 2;
            if (meta == 2) return 3;
            if (meta == 0) return 4;
        }
        if (block == GregTechAPI.sBlockCasings8) {
            if (meta == 5) return 5;
            if (meta == 6) return 6;
            if (meta == 7) return 7;
        }
        return null;
    }

    @Nullable
    public static Integer getPipeTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockCasings2 && meta == 13) return 1;
        if (block == GregTechAPI.sBlockCasings4 && meta == 11) return 2;
        if (block == GregTechAPI.sBlockCasings8 && meta == 1) return 3;
        return null;
    }

    @Nullable
    public static Integer getGearTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockCasings2 && meta == 3) return 1;
        if (block == GregTechAPI.sBlockCasings4 && meta == 9) return 2;
        return null;
    }

    @Nullable
    public static Integer getFrameTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockFrames) {
            if (meta == Materials.Steel.mMetaItemSubID) return 1;
            if (meta == Materials.Titanium.mMetaItemSubID) return 2;
            if (meta == Materials.TungstenSteel.mMetaItemSubID) return 3;
            if (meta == Materials.Chrome.mMetaItemSubID) return 4;
            if (meta == Materials.Iridium.mMetaItemSubID) return 5;
            if (meta == Materials.Osmium.mMetaItemSubID) return 6;
            if (meta == Materials.NaquadahAlloy.mMetaItemSubID) return 7;
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

    private boolean addPressureCoolingToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity mte = aTileEntity.getMetaTileEntity();
        if (mte instanceof MTEPressureSteamCoolingHatch hatch) {
            hatch.updateTexture(aBaseCasingIndex);
            mPressureCoolingHatches.add(hatch);
            return true;
        }
        return false;
    }

    @Override
    public boolean checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack) {
        mCasingAmount = 0;
        mCasingTier = -1;
        mPressureSteamInputs.clear();
        mPressureCoolingHatches.clear();

        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET)) return false;
        if (mCasingTier <= 0 || mCasingTier > 7) return false;

        boolean hasEnergy = !mEnergyHatches.isEmpty();
        boolean hasSteamInput = !mPressureSteamInputs.isEmpty();
        boolean hasInput = !mInputBusses.isEmpty() || !mInputHatches.isEmpty();
        boolean hasOutput = !mOutputBusses.isEmpty() || !mOutputHatches.isEmpty() || !mPressureCoolingHatches.isEmpty();
        if (!hasEnergy || !hasSteamInput || !hasInput || !hasOutput) return false;

        updateAllHatchTextures();
        return true;
    }

    private void checkInternalMachine() {
        ItemStack controllerStack = getControllerSlot();

        if (controllerStack == null || controllerStack.stackSize < 1) {
            resetMachineInfo();
            return;
        }

        boolean machineChanged = internalMachineStack == null
            || !ItemStack.areItemStacksEqual(internalMachineStack, controllerStack);

        if (machineChanged) {
            MTEBasicMachineWithRecipe mte = getMTE(controllerStack);
            if (mte == null) {
                resetMachineInfo();
                return;
            }
            internalMachineStack = controllerStack.copy();
            internalMachineStack.stackSize = 1;
            recipeMap = mte.getRecipeMap();
            voltage = GTValues.V[mte.mTier] * mte.mAmperage;
            mMachineTier = mte.mTier;
            mMachineName = mte.getInventoryName();

            for (var tInputBus : mInputBusses) {
                tInputBus.mRecipeMap = recipeMap;
            }
            for (var tInputHatch : mInputHatches) {
                tInputHatch.mRecipeMap = recipeMap;
            }
        }

        mStackSize = controllerStack.stackSize;
        maxParallel = (1 + 2 * mMachineTier) + mStackSize;
    }

    private void resetMachineInfo() {
        if (internalMachineStack == null && recipeMap == null) return;
        internalMachineStack = null;
        recipeMap = null;
        voltage = 0;
        mMachineTier = 0;
        maxParallel = 0;
        mStackSize = 0;
        mMachineName = "";
        mSteamRate = 0;
        mSteamPerAmp = 0;
        mRealtimeSteamCost = 0;
        mParallelCount = 0;

        for (var tInputBus : mInputBusses) {
            tInputBus.mRecipeMap = null;
        }
        for (var tInputHatch : mInputHatches) {
            tInputHatch.mRecipeMap = null;
        }
    }

    @Nullable
    private static MTEBasicMachineWithRecipe getMTE(ItemStack itemStack) {
        if (itemStack == null || itemStack.stackSize < 1) return null;
        if (itemStack.getItem() != Item.getItemFromBlock(GregTechAPI.sBlockMachines)) return null;
        int meta = itemStack.getItemDamage();
        if (meta < 0 || meta >= GregTechAPI.MAXIMUM_METATILE_IDS) return null;
        IMetaTileEntity mte = GregTechAPI.METATILEENTITIES[meta];
        if (mte instanceof MTEBasicMachineWithRecipe) return (MTEBasicMachineWithRecipe) mte;
        return null;
    }

    private long getEffectiveVoltage() {
        if (mMachineTier <= mCasingTier) return voltage;
        if (mCasingTier >= 7) return voltage;
        return GTValues.V[mCasingTier];
    }

    private double getSteamConsumptionRate() {
        int tier = mMachineTier;
        if (tier < 0) tier = 0;
        if (tier >= STEAM_CONSUMPTION_RATES.length) tier = STEAM_CONSUMPTION_RATES.length - 1;
        return STEAM_CONSUMPTION_RATES[tier];
    }

    private long calculateSteamCostPerTick() {
        if (mEUt >= 0) return 0;
        double rate = getSteamConsumptionRate();
        return (long) (rate * Math.abs(mEUt));
    }

    private boolean hasSufficientSuperheatedSteam(long required) {
        if (required <= 0) return true;
        long total = 0;
        for (MTEHatchInput hatch : GTUtility.validMTEList(mInputHatches)) {
            FluidStack fs = hatch.getFluid();
            if (fs != null && GTModHandler.isSuperHeatedSteam(fs) && fs.amount > 0) {
                total += fs.amount;
                if (total >= required) return true;
            }
        }
        for (MTEHatchPressureSteamInput hatch : mPressureSteamInputs) {
            FluidStack fs = hatch.getFluid();
            if (fs != null && GTModHandler.isSuperHeatedSteam(fs) && fs.amount > 0) {
                total += fs.amount;
                if (total >= required) return true;
            }
        }
        return false;
    }

    private boolean depleteSuperheatedSteam(long required) {
        long remaining = required;
        for (MTEHatchInput hatch : GTUtility.validMTEList(mInputHatches)) {
            FluidStack fs = hatch.getFluid();
            if (fs != null && GTModHandler.isSuperHeatedSteam(fs) && fs.amount > 0) {
                int drained = (int) Math.min(remaining, fs.amount);
                depleteInput(new FluidStack(fs, drained));
                remaining -= drained;
                if (remaining <= 0) return true;
            }
        }
        for (MTEHatchPressureSteamInput hatch : mPressureSteamInputs) {
            FluidStack fs = hatch.getFluid();
            if (fs != null && GTModHandler.isSuperHeatedSteam(fs) && fs.amount > 0) {
                int canDrain = (int) Math.min(remaining, fs.amount);
                hatch.drain(canDrain, true);
                remaining -= canDrain;
                if (remaining <= 0) return true;
            }
        }
        return remaining <= 0;
    }

    private void outputCoolingSteam(long steamAmount) {
        if (steamAmount <= 0) return;
        boolean pushedToCoolingHatch = false;
        for (MTEPressureSteamCoolingHatch hatch : mPressureCoolingHatches) {
            int toPush = (int) Math.min(steamAmount, Integer.MAX_VALUE);
            int pushed = hatch.pushCoolingSteam(toPush);
            if (pushed > 0) {
                steamAmount -= pushed;
                pushedToCoolingHatch = true;
            }
            if (steamAmount <= 0) return;
        }
        if (!pushedToCoolingHatch || steamAmount > 0) {
            int toOutput = (int) Math.min(steamAmount, Integer.MAX_VALUE);
            if (toOutput > 0) {
                addOutput(Materials.Steam.getGas(toOutput));
            }
        }
    }

    @Override
    public boolean onRunningTick(ItemStack aStack) {
        if (mEUt < 0) {
            long steamPerTick = calculateSteamCostPerTick();
            if (steamPerTick > 0) {
                if (!depleteSuperheatedSteam(steamPerTick)) {
                    stopMachine();
                    return false;
                }
                outputCoolingSteam(steamPerTick);
                mRealtimeSteamCost = steamPerTick;
            }
        }
        return super.onRunningTick(aStack);
    }

    @Nonnull
    @Override
    public CheckRecipeResult checkProcessing() {
        checkInternalMachine();
        if (internalMachineStack == null) {
            return SimpleCheckRecipeResult.ofFailure("no_machine");
        }

        long effectiveV = getEffectiveVoltage();
        double rate = getSteamConsumptionRate();
        long estimatedSteamPerTick = (long) (rate * effectiveV);

        if (!hasSufficientSuperheatedSteam(estimatedSteamPerTick)) {
            return SimpleCheckRecipeResult.ofFailure("insufficient_steam");
        }

        mSteamRate = rate;
        mSteamPerAmp = (long) (rate * effectiveV);
        mParallelCount = maxParallel;

        CheckRecipeResult result = super.checkProcessing();

        if (result.wasSuccessful()) {
            mRealtimeSteamCost = calculateSteamCostPerTick();
            if (processingLogic != null) {
                mParallelCount = processingLogic.getCurrentParallels();
            }
        }

        return result;
    }

    @Override
    protected ProcessingLogic createProcessingLogic() {
        return new ProcessingLogic() {

            @Nonnull
            @Override
            protected CheckRecipeResult validateRecipe(@Nonnull GTRecipe recipe) {
                if (recipe.getMetadataOrDefault(CompressionTierKey.INSTANCE, 0) > 0) {
                    return CheckRecipeResultRegistry.NO_RECIPE;
                }
                return CheckRecipeResultRegistry.SUCCESSFUL;
            }
        }.setMaxParallelSupplier(this::getTrueParallel);
    }

    @Override
    protected void setProcessingLogicPower(ProcessingLogic logic) {
        logic.setAvailableVoltage(getEffectiveVoltage());
        logic.setAvailableAmperage(getMaxParallelRecipes());
        logic.setAmperageOC(false);
    }

    @Override
    public int getMaxParallelRecipes() {
        return maxParallel;
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return recipeMap;
    }

    @Override
    protected boolean canUseControllerSlotForRecipe() {
        return false;
    }

    @Override
    protected void drawTexts(DynamicPositionedColumn screenElements, SlotWidget inventorySlot) {
        super.drawTexts(screenElements, inventorySlot);

        screenElements.widget(new FakeSyncWidget.IntegerSyncer(() -> mMachineTier, val -> mMachineTier = val));
        screenElements.widget(new FakeSyncWidget.DoubleSyncer(() -> mSteamRate, val -> mSteamRate = val));
        screenElements.widget(new FakeSyncWidget.LongSyncer(() -> mSteamPerAmp, val -> mSteamPerAmp = val));
        screenElements.widget(new FakeSyncWidget.LongSyncer(() -> mRealtimeSteamCost, val -> mRealtimeSteamCost = val));
        screenElements.widget(new FakeSyncWidget.IntegerSyncer(() -> mParallelCount, val -> mParallelCount = val));
        screenElements.widget(new FakeSyncWidget.StringSyncer(() -> mMachineName, val -> mMachineName = val));

        screenElements.widget(
            TextWidget
                .dynamicString(
                    () -> EnumChatFormatting.GOLD + "Tier: "
                        + EnumChatFormatting.GREEN
                        + GTValues.VN[mMachineTier > 0 && mMachineTier < GTValues.VN.length ? mMachineTier : 0])
                .setTextAlignment(Alignment.CenterLeft)
                .setDefaultColor(COLOR_TEXT_WHITE.get())
                .setEnabled(w -> mMachine));

        screenElements.widget(
            TextWidget
                .dynamicString(
                    () -> EnumChatFormatting.GOLD + "Steam Rate: "
                        + EnumChatFormatting.AQUA
                        + String.format("%.2f", mSteamRate))
                .setTextAlignment(Alignment.CenterLeft)
                .setDefaultColor(COLOR_TEXT_WHITE.get())
                .setEnabled(w -> mMachine));

        screenElements.widget(
            TextWidget
                .dynamicString(
                    () -> EnumChatFormatting.GOLD + "Steam/A: "
                        + EnumChatFormatting.YELLOW
                        + GTUtility.formatNumbers(mSteamPerAmp)
                        + " L/t")
                .setTextAlignment(Alignment.CenterLeft)
                .setDefaultColor(COLOR_TEXT_WHITE.get())
                .setEnabled(w -> mMachine));

        screenElements.widget(
            TextWidget
                .dynamicString(
                    () -> EnumChatFormatting.GOLD + "HP Steam: "
                        + EnumChatFormatting.RED
                        + GTUtility.formatNumbers(mRealtimeSteamCost)
                        + " L/t")
                .setTextAlignment(Alignment.CenterLeft)
                .setDefaultColor(COLOR_TEXT_WHITE.get())
                .setEnabled(w -> mMachine));

        screenElements.widget(
            TextWidget
                .dynamicString(
                    () -> EnumChatFormatting.GOLD + "Parallel: " + EnumChatFormatting.LIGHT_PURPLE + mParallelCount)
                .setTextAlignment(Alignment.CenterLeft)
                .setDefaultColor(COLOR_TEXT_WHITE.get())
                .setEnabled(w -> mMachine));
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setInteger("mCasingTier", mCasingTier);
        aNBT.setInteger("mMachineTier", mMachineTier);
        aNBT.setLong("voltage", voltage);
        aNBT.setInteger("maxParallel", maxParallel);
        aNBT.setInteger("mStackSize", mStackSize);
        aNBT.setDouble("mSteamRate", mSteamRate);
        aNBT.setLong("mSteamPerAmp", mSteamPerAmp);
        aNBT.setLong("mRealtimeSteamCost", mRealtimeSteamCost);
        aNBT.setInteger("mParallelCount", mParallelCount);
        if (mMachineName != null && !mMachineName.isEmpty()) {
            aNBT.setString("mMachineName", mMachineName);
        }
        if (internalMachineStack != null) {
            NBTTagCompound stackNBT = new NBTTagCompound();
            internalMachineStack.writeToNBT(stackNBT);
            aNBT.setTag("internalMachineStack", stackNBT);
        }
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mCasingTier = aNBT.getInteger("mCasingTier");
        mMachineTier = aNBT.getInteger("mMachineTier");
        voltage = aNBT.getLong("voltage");
        maxParallel = aNBT.getInteger("maxParallel");
        mStackSize = aNBT.getInteger("mStackSize");
        mSteamRate = aNBT.getDouble("mSteamRate");
        mSteamPerAmp = aNBT.getLong("mSteamPerAmp");
        mRealtimeSteamCost = aNBT.getLong("mRealtimeSteamCost");
        mParallelCount = aNBT.getInteger("mParallelCount");
        mMachineName = aNBT.getString("mMachineName");
        if (aNBT.hasKey("internalMachineStack")) {
            internalMachineStack = ItemStack.loadItemStackFromNBT(aNBT.getCompoundTag("internalMachineStack"));
        }
    }

    @Override
    public boolean addToMachineList(IGregTechTileEntity tTileEntity, int aBaseCasingIndex) {
        return addInputToMachineList(tTileEntity, aBaseCasingIndex)
            || addOutputToMachineList(tTileEntity, aBaseCasingIndex)
            || addEnergyInputToMachineList(tTileEntity, aBaseCasingIndex)
            || addPressureSteamToMachineList(tTileEntity, aBaseCasingIndex)
            || addPressureCoolingToMachineList(tTileEntity, aBaseCasingIndex);
    }

    private void updateAllHatchTextures() {
        int textureIndex = getCasingTextureIndex();
        for (MTEHatchPressureSteamInput hatch : mPressureSteamInputs) {
            hatch.updateTexture(textureIndex);
        }
        for (MTEPressureSteamCoolingHatch hatch : mPressureCoolingHatches) {
            hatch.updateTexture(textureIndex);
        }
        for (var inputHatch : GTUtility.validMTEList(mInputHatches)) {
            inputHatch.updateTexture(textureIndex);
        }
        for (var outputHatch : GTUtility.validMTEList(mOutputHatches)) {
            outputHatch.updateTexture(textureIndex);
        }
        for (var inputBus : GTUtility.validMTEList(mInputBusses)) {
            inputBus.updateTexture(textureIndex);
        }
        for (var outputBus : GTUtility.validMTEList(mOutputBusses)) {
            outputBus.updateTexture(textureIndex);
        }
        for (var energyHatch : GTUtility.validMTEList(mEnergyHatches)) {
            energyHatch.updateTexture(textureIndex);
        }
    }

    private int getCasingTextureIndex() {
        switch (mCasingTier) {
            case 1:
                return GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings2, 0);
            case 2:
                return GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings4, 1);
            case 3:
                return GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings4, 2);
            case 4:
                return GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings4, 0);
            case 5:
                return GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings8, 5);
            case 6:
                return GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings8, 6);
            case 7:
                return GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings8, 7);
            default:
                return SOLID_STEEL_CASING_INDEX;
        }
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

    @Override
    public boolean supportsPowerPanel() {
        return true;
    }

    @Override
    public String[] getStructureDescription(ItemStack stackSize) {
        return new String[0];
    }

    @Override
    public int getMaxEfficiency(ItemStack aStack) {
        return 10000;
    }

    public int getTierRecipes() {
        return mMachineTier;
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int aColorIndex, boolean aActive, boolean aRedstone) {
        int casingIndex = getCasingTextureIndex();
        if (side == facing) {
            if (aActive) return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(casingIndex),
                TextureFactory.of(OVERLAY_FRONT_ORE_DRILL_ACTIVE), TextureFactory.builder()
                    .addIcon(OVERLAY_FRONT_ORE_DRILL_ACTIVE_GLOW)
                    .extFacing()
                    .glow()
                    .build() };
            return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(casingIndex),
                TextureFactory.of(OVERLAY_FRONT_ORE_DRILL), TextureFactory.builder()
                    .addIcon(OVERLAY_FRONT_ORE_DRILL_GLOW)
                    .extFacing()
                    .glow()
                    .build() };
        }
        return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(casingIndex) };
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType("Kinetic Processing Array")
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.kinetic_processing_array.info"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.kinetic_processing_array.info2"))
            .addSeparator()
            .beginStructureBlock(7, 5, 7, true)
            .addController(StatCollector.translateToLocal("gtsr.tooltip.kinetic_processing_array.controller"))
            .addEnergyHatch(StatCollector.translateToLocal("gtsr.tooltip.kinetic_processing_array.energy"), 1)
            .addInputHatch(StatCollector.translateToLocal("gtsr.tooltip.kinetic_processing_array.steam_input"), 2)
            .addInputBus(StatCollector.translateToLocal("gtsr.tooltip.kinetic_processing_array.input"), 1)
            .addOutputBus(StatCollector.translateToLocal("gtsr.tooltip.kinetic_processing_array.output"), 1)
            .addOutputHatch(StatCollector.translateToLocal("gtsr.tooltip.kinetic_processing_array.output_hatch"), 1)
            .toolTipFinisher();
        return tt;
    }
}
