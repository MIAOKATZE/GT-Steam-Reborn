package com.miaokatze.gtsgu.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidHandler;

import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.miaokatze.gtsgu.common.machine.base.MTEPressureSteamStorageUnit;
import com.miaokatze.gtsgu.common.machine.base.MTEReinforcedSteamStorageUnit;
import com.miaokatze.gtsgu.common.machine.base.MTESteamHubInputHatch;
import com.miaokatze.gtsgu.common.machine.base.MTESteamHubOutputHatch;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IHatchElement;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTUtility;
import gregtech.api.util.IGTHatchAdder;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.misc.GTStructureChannels;

public class MTESteamHubArray extends MTEEnhancedMultiBlockBase<MTESteamHubArray>
    implements IConstructable, ISurvivalConstructable {

    private static final String STRUCTURE_PIECE_BASE = "base";
    private static final String STRUCTURE_PIECE_STACK = "stack";
    private static final String STRUCTURE_PIECE_STACK_HINT = "stackHint";
    private static final String STRUCTURE_PIECE_TOP_HINT = "topHint";
    private static final int MIN_TOTAL_HEIGHT = 2;
    private static final int MAX_TOTAL_HEIGHT = 13;
    private static final int AUTO_OUTPUT_RATE = 2_000_000;

    private static final int CASING_INDEX = GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings1, 10);

    private static final IStructureDefinition<MTESteamHubArray> STRUCTURE_DEFINITION;

    static {
        STRUCTURE_DEFINITION = StructureDefinition.<MTESteamHubArray>builder()
            .addShape(
                STRUCTURE_PIECE_BASE,
                transpose(new String[][] { { "HH~HH", "HHHHH", "HHHHH", "HHHHH", "HHHHH" } }))
            .addShape(
                STRUCTURE_PIECE_STACK,
                transpose(new String[][] { { "SSSSS", "SSSSS", "SSSSS", "SSSSS", "SSSSS" } }))
            .addShape(
                STRUCTURE_PIECE_STACK_HINT,
                transpose(new String[][] { { "TTTTT", "TTTTT", "TTTTT", "TTTTT", "TTTTT" } }))
            .addShape(
                STRUCTURE_PIECE_TOP_HINT,
                transpose(new String[][] { { "TTTTT", "TTTTT", "TTTTT", "TTTTT", "TTTTT" } }))
            .addElement(
                'H',
                buildHatchAdder(MTESteamHubArray.class)
                    .atLeast(SteamHubHatchElement.SteamInput, SteamHubHatchElement.SteamOutput)
                    .casingIndex(CASING_INDEX)
                    .dot(1)
                    .buildAndChain(
                        onElementPass(MTESteamHubArray::onCasingAdded, ofBlock(GregTechAPI.sBlockCasings1, 10))))
            .addElement(
                'S',
                buildHatchAdder(MTESteamHubArray.class)
                    .atLeast(SteamHubStorageElement.PressureUnit, SteamHubStorageElement.ReinforcedUnit)
                    .casingIndex(CASING_INDEX)
                    .dot(2)
                    .buildAndChain(
                        onElementPass(MTESteamHubArray::onCasingAdded, ofBlock(GregTechAPI.sBlockCasings1, 10))))
            .addElement(
                'T',
                buildHatchAdder(MTESteamHubArray.class)
                    .atLeast(SteamHubStorageElement.PressureUnit, SteamHubStorageElement.ReinforcedUnit)
                    .casingIndex(CASING_INDEX)
                    .dot(2)
                    .buildAndChain(GregTechAPI.sBlockCasings1, 10))
            .build();
    }

    private enum SteamHubHatchElement implements IHatchElement<MTESteamHubArray> {

        SteamInput(MTESteamHubArray::addSteamInputToMachineList, MTESteamHubInputHatch.class),
        SteamOutput(MTESteamHubArray::addSteamOutputToMachineList, MTESteamHubOutputHatch.class);

        private final List<Class<? extends IMetaTileEntity>> mteClasses;
        private final IGTHatchAdder<MTESteamHubArray> adder;

        @SafeVarargs
        SteamHubHatchElement(IGTHatchAdder<MTESteamHubArray> adder, Class<? extends IMetaTileEntity>... classes) {
            this.mteClasses = Collections.unmodifiableList(Arrays.asList(classes));
            this.adder = adder;
        }

        @Override
        public List<? extends Class<? extends IMetaTileEntity>> mteClasses() {
            return mteClasses;
        }

        @Override
        public IGTHatchAdder<? super MTESteamHubArray> adder() {
            return adder;
        }

        @Override
        public long count(MTESteamHubArray t) {
            return this == SteamInput ? t.mSteamInputHatches.size() : t.mSteamOutputHatches.size();
        }
    }

    private enum SteamHubStorageElement implements IHatchElement<MTESteamHubArray> {

        PressureUnit(MTESteamHubArray::addPressureUnitToMachineList, MTEPressureSteamStorageUnit.class),
        ReinforcedUnit(MTESteamHubArray::addReinforcedUnitToMachineList, MTEReinforcedSteamStorageUnit.class);

        private final List<Class<? extends IMetaTileEntity>> mteClasses;
        private final IGTHatchAdder<MTESteamHubArray> adder;

        @SafeVarargs
        SteamHubStorageElement(IGTHatchAdder<MTESteamHubArray> adder, Class<? extends IMetaTileEntity>... classes) {
            this.mteClasses = Collections.unmodifiableList(Arrays.asList(classes));
            this.adder = adder;
        }

        @Override
        public List<? extends Class<? extends IMetaTileEntity>> mteClasses() {
            return mteClasses;
        }

        @Override
        public IGTHatchAdder<? super MTESteamHubArray> adder() {
            return adder;
        }

        @Override
        public long count(MTESteamHubArray t) {
            return this == PressureUnit ? t.mPressureUnitCount : t.mReinforcedUnitCount;
        }
    }

    private final ArrayList<MTESteamHubInputHatch> mSteamInputHatches = new ArrayList<>();
    private final ArrayList<MTESteamHubOutputHatch> mSteamOutputHatches = new ArrayList<>();

    private int mPressureUnitCount = 0;
    private int mReinforcedUnitCount = 0;
    private int mCasingAmount = 0;
    private int mHeight = 0;
    private long mSteamStored = 0;
    private FluidStack mStoredFluidType = null;

    public MTESteamHubArray(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTESteamHubArray(String aName) {
        super(aName);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTESteamHubArray(mName);
    }

    @Override
    public IStructureDefinition<MTESteamHubArray> getStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @Override
    public boolean checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack) {
        for (MTESteamHubInputHatch hatch : mSteamInputHatches) {
            hatch.mController = null;
        }
        for (MTESteamHubOutputHatch hatch : mSteamOutputHatches) {
            hatch.mController = null;
        }

        mPressureUnitCount = 0;
        mReinforcedUnitCount = 0;
        mCasingAmount = 0;
        mHeight = 0;
        mSteamInputHatches.clear();
        mSteamOutputHatches.clear();

        if (!checkPiece(STRUCTURE_PIECE_BASE, 2, 0, 0)) return false;

        while (mHeight < MAX_TOTAL_HEIGHT - 1) {
            if (!checkPiece(STRUCTURE_PIECE_STACK, 2, mHeight + 1, 0)) break;
            mHeight++;
        }

        return mHeight >= MIN_TOTAL_HEIGHT - 1 && (mPressureUnitCount + mReinforcedUnitCount) > 0;
    }

    private void onCasingAdded() {
        mCasingAmount++;
    }

    public boolean addSteamInputToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity instanceof MTESteamHubInputHatch hatch) {
            hatch.updateTexture(aBaseCasingIndex);
            hatch.mController = this;
            return mSteamInputHatches.add(hatch);
        }
        return false;
    }

    public boolean addSteamOutputToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity instanceof MTESteamHubOutputHatch hatch) {
            hatch.updateTexture(aBaseCasingIndex);
            hatch.mController = this;
            return mSteamOutputHatches.add(hatch);
        }
        return false;
    }

    public boolean addPressureUnitToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity instanceof MTEPressureSteamStorageUnit) {
            mPressureUnitCount++;
            return true;
        }
        return false;
    }

    public boolean addReinforcedUnitToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity instanceof MTEReinforcedSteamStorageUnit) {
            mReinforcedUnitCount++;
            return true;
        }
        return false;
    }

    public boolean isFormed() {
        return mMachine;
    }

    public int receiveSteam(FluidStack aFluid, boolean doFill) {
        if (aFluid == null) return 0;
        if (!MTESteamHubOutputHatch.isSteamFluid(aFluid)) return 0;
        if (mStoredFluidType != null && !mStoredFluidType.isFluidEqual(aFluid)) return 0;

        long capacity = getTotalCapacity();
        long canAccept = capacity - mSteamStored;
        int toAccept = (int) Math.min(aFluid.amount, canAccept);

        if (doFill && toAccept > 0) {
            if (mStoredFluidType == null) {
                mStoredFluidType = new FluidStack(aFluid.getFluid(), 0);
            }
            mSteamStored += toAccept;
        }

        return toAccept;
    }

    public FluidStack extractSteam(int maxDrain, boolean doDrain) {
        if (mSteamStored <= 0 || mStoredFluidType == null) return null;

        int toDrain = (int) Math.min(maxDrain, mSteamStored);
        FluidStack result = new FluidStack(mStoredFluidType.getFluid(), toDrain);

        if (doDrain) {
            mSteamStored -= toDrain;
            if (mSteamStored <= 0) {
                mStoredFluidType = null;
            }
        }

        return result;
    }

    public FluidStack getStoredFluidStack() {
        if (mStoredFluidType == null || mSteamStored <= 0) return null;
        int amount = (int) Math.min(mSteamStored, Integer.MAX_VALUE);
        return new FluidStack(mStoredFluidType.getFluid(), amount);
    }

    private int getTotalHeightFromItemStack(ItemStack stackSize) {
        return Math.max(
            MIN_TOTAL_HEIGHT,
            GTStructureChannels.STRUCTURE_HEIGHT.getValueClamped(stackSize, MIN_TOTAL_HEIGHT, MAX_TOTAL_HEIGHT));
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece(STRUCTURE_PIECE_BASE, stackSize, hintsOnly, 2, 0, 0);
        int tTotalHeight = getTotalHeightFromItemStack(stackSize);
        for (int i = 1; i < tTotalHeight - 1; i++) {
            buildPiece(STRUCTURE_PIECE_STACK_HINT, stackSize, hintsOnly, 2, i, 0);
        }
        buildPiece(STRUCTURE_PIECE_TOP_HINT, stackSize, hintsOnly, 2, tTotalHeight - 1, 0);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        int built = survivalBuildPiece(STRUCTURE_PIECE_BASE, stackSize, 2, 0, 0, elementBudget, env, false, true);
        if (built >= 0) return built;
        int tTotalHeight = getTotalHeightFromItemStack(stackSize);
        for (int i = 1; i < tTotalHeight - 1; i++) {
            built = survivalBuildPiece(STRUCTURE_PIECE_STACK_HINT, stackSize, 2, i, 0, elementBudget, env, false, true);
            if (built >= 0) return built;
        }
        return survivalBuildPiece(
            STRUCTURE_PIECE_TOP_HINT,
            stackSize,
            2,
            tTotalHeight - 1,
            0,
            elementBudget,
            env,
            false,
            true);
    }

    @Override
    public String[] getStructureDescription(ItemStack stackSize) {
        return new String[] { EnumChatFormatting.AQUA + "Structure:",
            "1. 5x5 base layer with Bronze Plated Bricks and Hatches",
            "2. Stack Storage Units above the base (1-12 layers)",
            "3. At least 1 Input Hatch and 1 Output Hatch required",
            "4. Height: Level 1 = 2 layers, Level 12 = 13 layers" };
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

    public long getTotalCapacity() {
        return (long) mPressureUnitCount * MTEPressureSteamStorageUnit.PRESSURE_CAPACITY
            + (long) mReinforcedUnitCount * MTEReinforcedSteamStorageUnit.REINFORCED_CAPACITY;
    }

    public long getSteamStored() {
        return mSteamStored;
    }

    public int getPressureUnitCount() {
        return mPressureUnitCount;
    }

    public int getReinforcedUnitCount() {
        return mReinforcedUnitCount;
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (!aBaseMetaTileEntity.isServerSide() || !mMachine) return;

        long totalCapacity = getTotalCapacity();
        if (mSteamStored > totalCapacity) {
            mSteamStored = totalCapacity;
        }

        autoOutputSteam();
    }

    private void autoOutputSteam() {
        if (mSteamStored <= 0 || mStoredFluidType == null) return;
        for (MTESteamHubOutputHatch hatch : mSteamOutputHatches) {
            if (mSteamStored <= 0) break;
            IGregTechTileEntity hatchBase = hatch.getBaseMetaTileEntity();
            if (hatchBase == null) continue;
            ForgeDirection hatchFront = hatchBase.getFrontFacing();
            IFluidHandler adjacent = hatchBase.getITankContainerAtSide(hatchFront);
            if (adjacent == null) continue;

            int toPush = (int) Math.min(AUTO_OUTPUT_RATE, mSteamStored);
            FluidStack toExport = new FluidStack(mStoredFluidType.getFluid(), toPush);
            int pushed = adjacent.fill(hatchFront.getOpposite(), toExport, true);
            if (pushed > 0) {
                mSteamStored -= pushed;
                if (mSteamStored <= 0) {
                    mStoredFluidType = null;
                    return;
                }
            }
        }
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setLong("mSteamStored", mSteamStored);
        aNBT.setInteger("mPressureUnitCount", mPressureUnitCount);
        aNBT.setInteger("mReinforcedUnitCount", mReinforcedUnitCount);
        aNBT.setInteger("mHeight", mHeight);
        if (mStoredFluidType != null) {
            NBTTagCompound fluidTag = new NBTTagCompound();
            mStoredFluidType.writeToNBT(fluidTag);
            aNBT.setTag("mStoredFluidType", fluidTag);
        }
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mSteamStored = aNBT.getLong("mSteamStored");
        mPressureUnitCount = aNBT.getInteger("mPressureUnitCount");
        mReinforcedUnitCount = aNBT.getInteger("mReinforcedUnitCount");
        mHeight = aNBT.getInteger("mHeight");
        if (aNBT.hasKey("mStoredFluidType")) {
            mStoredFluidType = FluidStack.loadFluidStackFromNBT(aNBT.getCompoundTag("mStoredFluidType"));
        }
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int colorIndex, boolean active, boolean redstoneLevel) {
        if (side == facing) {
            if (active) {
                return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(CASING_INDEX),
                    TextureFactory.builder()
                        .addIcon(Textures.BlockIcons.OVERLAY_FRONT_VACUUM_FREEZER)
                        .extFacing()
                        .build(),
                    TextureFactory.builder()
                        .addIcon(Textures.BlockIcons.OVERLAY_FRONT_VACUUM_FREEZER_GLOW)
                        .extFacing()
                        .glow()
                        .build() };
            }
            return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(CASING_INDEX), TextureFactory.builder()
                .addIcon(Textures.BlockIcons.OVERLAY_FRONT_VACUUM_FREEZER)
                .extFacing()
                .build() };
        }
        return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(CASING_INDEX) };
    }

    @Override
    public String[] getInfoData() {
        long totalCapacity = getTotalCapacity();
        double fillRatio = totalCapacity > 0 ? (double) mSteamStored / totalCapacity * 100 : 0;
        return new String[] { EnumChatFormatting.BLUE + "Steam Hub Array",
            EnumChatFormatting.GRAY + "Status: "
                + (mMachine ? EnumChatFormatting.GREEN + "Running" : EnumChatFormatting.RED + "Incomplete"),
            EnumChatFormatting.GRAY + "Steam Type: "
                + EnumChatFormatting.AQUA
                + (mStoredFluidType != null ? mStoredFluidType.getLocalizedName() : "None"),
            EnumChatFormatting.GRAY + "Steam Stored: "
                + EnumChatFormatting.YELLOW
                + GTUtility.formatNumbers(mSteamStored)
                + " / "
                + GTUtility.formatNumbers(totalCapacity)
                + " L",
            EnumChatFormatting.GRAY + "Fill: " + EnumChatFormatting.GREEN + String.format("%.1f%%", fillRatio),
            EnumChatFormatting.GRAY + "Pressure Units: " + EnumChatFormatting.WHITE + mPressureUnitCount,
            EnumChatFormatting.GRAY + "Reinforced Units: " + EnumChatFormatting.WHITE + mReinforcedUnitCount };
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal("gtsgu.recipe.steam_hub_array"))
            .addInfo(StatCollector.translateToLocal("gtsgu.tooltip.steam_hub_array.info"))
            .addInfo(StatCollector.translateToLocal("gtsgu.tooltip.steam_hub_array.structure"))
            .addInfo(StatCollector.translateToLocal("gtsgu.tooltip.steam_hub_array.capacity"))
            .addInfo(StatCollector.translateToLocal("gtsgu.tooltip.steam_hub_array.output"))
            .addInfo(StatCollector.translateToLocal("gtsgu.tooltip.steam_hub_array.no_maintenance"))
            .addInfo(StatCollector.translateToLocal("gtsgu.tooltip.steam_hub_array.hatch_note"))
            .beginVariableStructureBlock(5, 5, 2, 13, 5, 5, false)
            .addController(StatCollector.translateToLocal("gtsgu.tooltip.steam_hub_array.ctrl_pos"))
            .addCasingInfoMin(StatCollector.translateToLocal("gtsgu.tooltip.steam_hub_array.casing"), 1, false)
            .addOtherStructurePart(
                StatCollector.translateToLocal("gtsgu.tooltip.steam_hub_array.input_hatch"),
                StatCollector.translateToLocal("gtsgu.tooltip.steam_hub_array.base_layer"),
                1)
            .addOtherStructurePart(
                StatCollector.translateToLocal("gtsgu.tooltip.steam_hub_array.output_hatch"),
                StatCollector.translateToLocal("gtsgu.tooltip.steam_hub_array.base_layer"),
                1)
            .addOtherStructurePart(
                StatCollector.translateToLocal("gtsgu.tooltip.steam_hub_array.storage_unit"),
                StatCollector.translateToLocal("gtsgu.tooltip.steam_hub_array.stack_layers"),
                2)
            .addSubChannelUsage(GTStructureChannels.STRUCTURE_HEIGHT)
            .toolTipFinisher();
        return tt;
    }
}
