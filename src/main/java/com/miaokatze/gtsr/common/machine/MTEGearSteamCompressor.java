package com.miaokatze.gtsr.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlocksTiered;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.HatchElement.InputHatch;
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
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;
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
import com.miaokatze.gtsr.api.recipe.GTSRRecipeMaps;
import com.miaokatze.gtsr.common.machine.base.MTEHatchPressureSteamInput;
import com.miaokatze.gtsr.common.machine.base.MTEPressureSteamCoolingHatch;
import com.miaokatze.gtsr.common.machine.base.MTESteamCoolingHatch;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;

public class MTEGearSteamCompressor extends MTEEnhancedMultiBlockBase<MTEGearSteamCompressor>
    implements IConstructable, ISurvivalConstructable {

    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static final int HORIZONTAL_OFF_SET = 4;
    private static final int VERTICAL_OFF_SET = 3;
    private static final int DEPTH_OFF_SET = 1;
    private static final int BRONZE_CASING_INDEX = GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings1, 10);
    private static IStructureDefinition<MTEGearSteamCompressor> STRUCTURE_DEFINITION;

    private static final long[] STEAM_CONSUMPTION_PER_SEC = { 0, 6400, 25600 };
    private static final long[] SUPERHEATED_OUTPUT_PER_SEC = { 0, 1600, 6400 };
    private static final long[] DISTILLED_WATER_OUTPUT_PER_SEC = { 0, 30, 120 };

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static final List<Pair<Block, Integer>> ALLOWED_CASINGS = ImmutableList
        .of(Pair.of(GregTechAPI.sBlockCasings1, 10), Pair.of(GregTechAPI.sBlockCasings2, 0));

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static final List<Pair<Block, Integer>> PIPE_CASINGS = ImmutableList
        .of(Pair.of(GregTechAPI.sBlockCasings2, 12), Pair.of(GregTechAPI.sBlockCasings2, 13));

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static final List<Pair<Block, Integer>> GEAR_CASINGS = ImmutableList
        .of(Pair.of(GregTechAPI.sBlockCasings2, 2), Pair.of(GregTechAPI.sBlockCasings2, 3));

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static final List<Pair<Block, Integer>> FRAME_CASINGS = ImmutableList.of(
        Pair.of(GregTechAPI.sBlockFrames, Materials.Bronze.mMetaItemSubID),
        Pair.of(GregTechAPI.sBlockFrames, Materials.Steel.mMetaItemSubID));

    private int mCasingAmount = 0;
    private int mCasingTier = -1;
    private int mPipeTier = -1;
    private int mGearTier = -1;
    private int mFrameTier = -1;

    private final List<MTEHatchPressureSteamInput> mPressureSteamInputs = new ArrayList<>();
    private final List<MTESteamCoolingHatch> mSteamCoolingHatches = new ArrayList<>();
    private final List<MTEPressureSteamCoolingHatch> mPressureCoolingHatches = new ArrayList<>();

    private long mSteamConsumedLastTick = 0;
    private long mSuperheatedOutputLastTick = 0;
    private long mWaterOutputLastTick = 0;

    public MTEGearSteamCompressor(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTEGearSteamCompressor(String aName) {
        super(aName);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEGearSteamCompressor(mName);
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return GTSRRecipeMaps.gearSteamCompressorRecipes;
    }

    @Override
    public IStructureDefinition<MTEGearSteamCompressor> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            STRUCTURE_DEFINITION = StructureDefinition.<MTEGearSteamCompressor>builder()
                .addShape(
                    STRUCTURE_PIECE_MAIN,
                    transpose(
                        new String[][] {
                            { "         ", "         ", "  BBBBB  ", "  BBBBB  ", "  BBBBB  ", "         ",
                                "         " },
                            { "F       F", " BBBBBBB ", " BDDDDDB ", " BDDDDDB ", " BDDDDDB ", " BBBBBBB ",
                                "F       F" },
                            { "F       F", " CEEEEEC ", " C     C ", " C     C ", " C     C ", " CEEEEEC ",
                                "F       F" },
                            { "F       F", " CBB~BBC ", " BDDDDDB ", " BDDDDDB ", " BDDDDDB ", " CBBBBBC ",
                                "F       F" },
                            { "BB     BB", "BCBBBBBCB", "BBBBBBBBB", "BBBBBBBBB", "BBBBBBBBB", "BCBBBBBCB",
                                "BB     BB" } }))
                .addElement(
                    'B',
                    ofChain(
                        onElementPass(
                            MTEGearSteamCompressor::onCasingAdded,
                            ofBlocksTiered(
                                MTEGearSteamCompressor::getCasingTier,
                                ALLOWED_CASINGS,
                                -1,
                                (t, tier) -> t.mCasingTier = tier,
                                t -> t.mCasingTier)),
                        buildHatchAdder(MTEGearSteamCompressor.class)
                            .adder(MTEGearSteamCompressor::addPressureSteamToMachineList)
                            .hatchClass(MTEHatchPressureSteamInput.class)
                            .casingIndex(BRONZE_CASING_INDEX)
                            .dot(1)
                            .build(),
                        buildHatchAdder(MTEGearSteamCompressor.class)
                            .adder(MTEGearSteamCompressor::addSteamCoolingToMachineList)
                            .hatchClass(MTESteamCoolingHatch.class)
                            .casingIndex(BRONZE_CASING_INDEX)
                            .dot(2)
                            .build(),
                        buildHatchAdder(MTEGearSteamCompressor.class)
                            .adder(MTEGearSteamCompressor::addPressureCoolingToMachineList)
                            .hatchClass(MTEPressureSteamCoolingHatch.class)
                            .casingIndex(BRONZE_CASING_INDEX)
                            .dot(2)
                            .build(),
                        buildHatchAdder(MTEGearSteamCompressor.class).atLeast(InputHatch, OutputHatch)
                            .casingIndex(BRONZE_CASING_INDEX)
                            .dot(3)
                            .build()))
                .addElement(
                    'C',
                    onElementPass(
                        MTEGearSteamCompressor::onCasingAdded,
                        ofBlocksTiered(
                            MTEGearSteamCompressor::getPipeTier,
                            PIPE_CASINGS,
                            -1,
                            (t, tier) -> t.mPipeTier = tier,
                            t -> t.mPipeTier)))
                .addElement(
                    'D',
                    onElementPass(
                        MTEGearSteamCompressor::onCasingAdded,
                        ofBlocksTiered(
                            MTEGearSteamCompressor::getGearTier,
                            GEAR_CASINGS,
                            -1,
                            (t, tier) -> t.mGearTier = tier,
                            t -> t.mGearTier)))
                .addElement(
                    'E',
                    onElementPass(
                        MTEGearSteamCompressor::onCasingAdded,
                        ofBlock(GameRegistry.findBlock("IC2", "blockAlloyGlass"), 0)))
                .addElement(
                    'F',
                    onElementPass(
                        MTEGearSteamCompressor::onCasingAdded,
                        ofBlocksTiered(
                            MTEGearSteamCompressor::getFrameTier,
                            FRAME_CASINGS,
                            -1,
                            (t, tier) -> t.mFrameTier = tier,
                            t -> t.mFrameTier)))
                .build();
        }
        return STRUCTURE_DEFINITION;
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

    private boolean addSteamCoolingToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity mte = aTileEntity.getMetaTileEntity();
        if (mte instanceof MTESteamCoolingHatch hatch && !(mte instanceof MTEPressureSteamCoolingHatch)) {
            hatch.updateTexture(aBaseCasingIndex);
            mSteamCoolingHatches.add(hatch);
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

    private int getEffectiveTier() {
        int tier = Math.max(Math.max(mCasingTier, mPipeTier), Math.max(mGearTier, mFrameTier));
        if (tier <= 0 || tier > 2) return -1;
        if (mCasingTier > 0 && mPipeTier > 0 && mGearTier > 0 && mFrameTier > 0) {
            if (mCasingTier == mPipeTier && mPipeTier == mGearTier && mGearTier == mFrameTier) return tier;
        }
        return -1;
    }

    @Override
    public boolean checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack) {
        mCasingAmount = 0;
        mCasingTier = -1;
        mPipeTier = -1;
        mGearTier = -1;
        mFrameTier = -1;
        mPressureSteamInputs.clear();
        mSteamCoolingHatches.clear();
        mPressureCoolingHatches.clear();

        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET)) return false;

        int tier = getEffectiveTier();
        if (tier <= 0) return false;

        boolean hasSteamInput = !mInputHatches.isEmpty() || hasPressureSteamHatch();
        boolean hasOutput = !mOutputHatches.isEmpty() || hasSteamCoolingHatch() || !mPressureCoolingHatches.isEmpty();
        if (!hasSteamInput || !hasOutput) return false;

        updateAllHatchTextures();
        return true;
    }

    private boolean hasPressureSteamHatch() {
        return !mPressureSteamInputs.isEmpty();
    }

    private boolean hasSteamCoolingHatch() {
        return !mSteamCoolingHatches.isEmpty() || !mPressureCoolingHatches.isEmpty();
    }

    private boolean depleteSteam(long required) {
        if (required <= 0) return true;
        long remaining = required;
        for (var hatch : GTUtility.validMTEList(mInputHatches)) {
            FluidStack fs = hatch.getFluid();
            if (fs != null && GTModHandler.isAnySteam(fs) && fs.amount > 0) {
                int drained = (int) Math.min(remaining, fs.amount);
                depleteInput(new FluidStack(fs, drained));
                remaining -= drained;
                if (remaining <= 0) return true;
            }
        }
        for (MTEHatchPressureSteamInput hatch : mPressureSteamInputs) {
            FluidStack fs = hatch.getFluid();
            if (fs != null && GTModHandler.isAnySteam(fs) && fs.amount > 0) {
                int canDrain = (int) Math.min(remaining, fs.amount);
                hatch.drain(canDrain, true);
                remaining -= canDrain;
                if (remaining <= 0) return true;
            }
        }
        return remaining <= 0;
    }

    private void outputSuperheatedSteam(long amount) {
        if (amount <= 0) return;
        boolean pushedToCoolingHatch = false;
        for (MTEPressureSteamCoolingHatch hatch : mPressureCoolingHatches) {
            int toPush = (int) Math.min(amount, Integer.MAX_VALUE);
            int pushed = hatch.pushCoolingSteam(toPush);
            if (pushed > 0) {
                amount -= pushed;
                pushedToCoolingHatch = true;
            }
            if (amount <= 0) return;
        }
        if (!pushedToCoolingHatch || amount > 0) {
            int toOutput = (int) Math.min(amount, Integer.MAX_VALUE);
            if (toOutput > 0) {
                addOutput(FluidRegistry.getFluidStack("ic2superheatedsteam", toOutput));
            }
        }
    }

    private void outputDistilledWater(long amount) {
        if (amount <= 0) return;
        int toOutput = (int) Math.min(amount, Integer.MAX_VALUE);
        if (toOutput > 0) {
            addOutput(GTModHandler.getDistilledWater(toOutput));
        }
    }

    @Nonnull
    @Override
    public CheckRecipeResult checkProcessing() {
        int tier = getEffectiveTier();
        if (tier <= 0 || tier > 2) return CheckRecipeResultRegistry.INTERNAL_ERROR;

        long steamRequired = STEAM_CONSUMPTION_PER_SEC[tier] / 20;
        if (!depleteSteam(steamRequired)) {
            mSteamConsumedLastTick = 0;
            mSuperheatedOutputLastTick = 0;
            mWaterOutputLastTick = 0;
            return CheckRecipeResultRegistry.NO_FUEL_FOUND;
        }

        long superheatedOut = SUPERHEATED_OUTPUT_PER_SEC[tier] / 20;
        long waterOut = DISTILLED_WATER_OUTPUT_PER_SEC[tier] / 20;

        outputSuperheatedSteam(superheatedOut);
        outputDistilledWater(waterOut);

        mSteamConsumedLastTick = STEAM_CONSUMPTION_PER_SEC[tier];
        mSuperheatedOutputLastTick = SUPERHEATED_OUTPUT_PER_SEC[tier];
        mWaterOutputLastTick = DISTILLED_WATER_OUTPUT_PER_SEC[tier];

        mMaxProgresstime = 1;
        mEUt = 0;
        return CheckRecipeResultRegistry.GENERATING;
    }

    @Override
    public boolean addToMachineList(IGregTechTileEntity tTileEntity, int aBaseCasingIndex) {
        return addInputToMachineList(tTileEntity, aBaseCasingIndex)
            || addOutputToMachineList(tTileEntity, aBaseCasingIndex)
            || addPressureSteamToMachineList(tTileEntity, aBaseCasingIndex)
            || addSteamCoolingToMachineList(tTileEntity, aBaseCasingIndex)
            || addPressureCoolingToMachineList(tTileEntity, aBaseCasingIndex);
    }

    private void updateAllHatchTextures() {
        int textureIndex = getCasingTextureIndex();
        for (MTEHatchPressureSteamInput hatch : mPressureSteamInputs) {
            hatch.updateTexture(textureIndex);
        }
        for (MTESteamCoolingHatch hatch : mSteamCoolingHatches) {
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
    }

    private int getCasingTextureIndex() {
        int tier = getEffectiveTier();
        if (tier == 2) return GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings2, 0);
        return BRONZE_CASING_INDEX;
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
        return false;
    }

    @Override
    public String[] getStructureDescription(ItemStack stackSize) {
        return new String[0];
    }

    @Override
    public int getMaxEfficiency(ItemStack aStack) {
        return 10000;
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
        tt.addMachineType(StatCollector.translateToLocal("gtsr.tooltip.gear_compressor.type"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.gear_compressor.desc"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.gear_compressor.desc2"))
            .addSeparator()
            .addInfo(
                EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.tooltip.gear_compressor.bronze_steam_in")
                    + EnumChatFormatting.WHITE
                    + " 6,400 L/s")
            .addInfo(
                EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.gear_compressor.bronze_sh_out")
                    + EnumChatFormatting.WHITE
                    + " 1,600 L/s")
            .addInfo(
                EnumChatFormatting.BLUE
                    + StatCollector.translateToLocal("gtsr.tooltip.gear_compressor.bronze_water_out")
                    + EnumChatFormatting.WHITE
                    + " 30 L/s")
            .addSeparator()
            .addInfo(
                EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.tooltip.gear_compressor.steel_steam_in")
                    + EnumChatFormatting.WHITE
                    + " 25,600 L/s")
            .addInfo(
                EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.gear_compressor.steel_sh_out")
                    + EnumChatFormatting.WHITE
                    + " 6,400 L/s")
            .addInfo(
                EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.gear_compressor.steel_water_out")
                    + EnumChatFormatting.WHITE
                    + " 120 L/s")
            .beginStructureBlock(9, 5, 7, true)
            .addController(StatCollector.translateToLocal("gtsr.tooltip.gear_compressor.ctrl"))
            .addOtherStructurePart(
                StatCollector.translateToLocal("gtsr.tooltip.gear_compressor.steam_input"),
                StatCollector.translateToLocal("gtsr.tooltip.shared.any_casing"),
                1)
            .addOtherStructurePart(
                StatCollector.translateToLocal("gtsr.tooltip.gear_compressor.cooling_hatch"),
                StatCollector.translateToLocal("gtsr.tooltip.shared.any_casing"),
                2)
            .addOutputHatch(StatCollector.translateToLocal("gtsr.tooltip.gear_compressor.output_hatch"), 3)
            .addStructureInfo("")
            .addStructureInfo(
                EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.shared.bronze_steel_tier"))
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.casing"), 70, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.pipe"), 7, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.gear_box"), 4, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.frame"), 24, false)
            .addStructureHint("gtsr.tooltip.shared.no_maintenance")
            .toolTipFinisher(
                EnumChatFormatting.DARK_AQUA + StatCollector.translateToLocal("gtsr.tooltip.added_by")
                    + " "
                    + EnumChatFormatting.AQUA
                    + "GT"
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
    public String[] getInfoData() {
        if (!mMachine) {
            return new String[] {
                EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.gear_compressor.type"),
                EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.gui.building") };
        }
        int tier = getEffectiveTier();
        String tierText = tier == 2 ? StatCollector.translateToLocal("gtsr.gui.tier.steel")
            : StatCollector.translateToLocal("gtsr.gui.tier.bronze");
        return new String[] {
            EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.gear_compressor.type"),
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.tier")
                + EnumChatFormatting.GOLD
                + tierText,
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.gear_compressor.steam_in")
                + EnumChatFormatting.RED
                + GTUtility.formatNumbers(mSteamConsumedLastTick)
                + " L/s",
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.gear_compressor.sh_steam_out")
                + EnumChatFormatting.AQUA
                + GTUtility.formatNumbers(mSuperheatedOutputLastTick)
                + " L/s",
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.gear_compressor.water_out")
                + EnumChatFormatting.BLUE
                + GTUtility.formatNumbers(mWaterOutputLastTick)
                + " L/s" };
    }

    @Override
    protected void drawTexts(DynamicPositionedColumn screenElements, SlotWidget inventorySlot) {
        super.drawTexts(screenElements, inventorySlot);

        screenElements.widget(new FakeSyncWidget.IntegerSyncer(() -> mCasingTier, val -> mCasingTier = val));
        screenElements
            .widget(new FakeSyncWidget.LongSyncer(() -> mSteamConsumedLastTick, val -> mSteamConsumedLastTick = val));
        screenElements.widget(
            new FakeSyncWidget.LongSyncer(() -> mSuperheatedOutputLastTick, val -> mSuperheatedOutputLastTick = val));
        screenElements
            .widget(new FakeSyncWidget.LongSyncer(() -> mWaterOutputLastTick, val -> mWaterOutputLastTick = val));

        screenElements.widget(
            TextWidget
                .dynamicString(
                    () -> EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.gui.gear_compressor.tier")
                        + EnumChatFormatting.GREEN
                        + (mCasingTier == 2 ? StatCollector.translateToLocal("gtsr.gui.tier.steel")
                            : mCasingTier == 1 ? StatCollector.translateToLocal("gtsr.gui.tier.bronze")
                                : StatCollector.translateToLocal("gtsr.gui.tier.none")))
                .setTextAlignment(Alignment.CenterLeft)
                .setDefaultColor(COLOR_TEXT_WHITE.get())
                .setEnabled(w -> mMachine));

        screenElements.widget(
            TextWidget
                .dynamicString(
                    () -> EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.gui.gear_compressor.steam_in")
                        + EnumChatFormatting.RED
                        + GTUtility.formatNumbers(mSteamConsumedLastTick)
                        + " L/s")
                .setTextAlignment(Alignment.CenterLeft)
                .setDefaultColor(COLOR_TEXT_WHITE.get())
                .setEnabled(w -> mMachine));

        screenElements.widget(
            TextWidget
                .dynamicString(
                    () -> EnumChatFormatting.GOLD
                        + StatCollector.translateToLocal("gtsr.gui.gear_compressor.sh_steam_out")
                        + EnumChatFormatting.AQUA
                        + GTUtility.formatNumbers(mSuperheatedOutputLastTick)
                        + " L/s")
                .setTextAlignment(Alignment.CenterLeft)
                .setDefaultColor(COLOR_TEXT_WHITE.get())
                .setEnabled(w -> mMachine));

        screenElements.widget(
            TextWidget
                .dynamicString(
                    () -> EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.gui.gear_compressor.water_out")
                        + EnumChatFormatting.BLUE
                        + GTUtility.formatNumbers(mWaterOutputLastTick)
                        + " L/s")
                .setTextAlignment(Alignment.CenterLeft)
                .setDefaultColor(COLOR_TEXT_WHITE.get())
                .setEnabled(w -> mMachine));
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setInteger("mCasingTier", mCasingTier);
        aNBT.setInteger("mPipeTier", mPipeTier);
        aNBT.setInteger("mGearTier", mGearTier);
        aNBT.setInteger("mFrameTier", mFrameTier);
        aNBT.setLong("mSteamConsumedLastTick", mSteamConsumedLastTick);
        aNBT.setLong("mSuperheatedOutputLastTick", mSuperheatedOutputLastTick);
        aNBT.setLong("mWaterOutputLastTick", mWaterOutputLastTick);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mCasingTier = aNBT.getInteger("mCasingTier");
        mPipeTier = aNBT.getInteger("mPipeTier");
        mGearTier = aNBT.getInteger("mGearTier");
        mFrameTier = aNBT.getInteger("mFrameTier");
        mSteamConsumedLastTick = aNBT.getLong("mSteamConsumedLastTick");
        mSuperheatedOutputLastTick = aNBT.getLong("mSuperheatedOutputLastTick");
        mWaterOutputLastTick = aNBT.getLong("mWaterOutputLastTick");
    }
}
