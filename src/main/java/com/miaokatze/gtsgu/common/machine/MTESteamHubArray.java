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
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

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
import gregtech.api.enums.Materials;
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
            return mSteamInputHatches.add(hatch);
        }
        return false;
    }

    public boolean addSteamOutputToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity instanceof MTESteamHubOutputHatch hatch) {
            hatch.updateTexture(aBaseCasingIndex);
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

    private long getTotalCapacity() {
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

        processSteamInput();
        processSteamOutput();

        long totalCapacity = getTotalCapacity();
        if (mSteamStored > totalCapacity) {
            mSteamStored = totalCapacity;
        }
    }

    private void processSteamInput() {
        for (MTESteamHubInputHatch hatch : mSteamInputHatches) {
            FluidStack fluid = hatch.getFluid();
            if (fluid == null || fluid.amount <= 0) continue;
            if (!MTESteamHubOutputHatch.isSteamFluid(fluid)) continue;

            long capacity = getTotalCapacity();
            long canAccept = capacity - mSteamStored;
            if (canAccept <= 0) continue;

            int toTransfer = (int) Math.min(fluid.amount, canAccept);
            if (toTransfer <= 0) continue;

            if (mStoredFluidType == null) {
                mStoredFluidType = new FluidStack(fluid.getFluid(), 0);
            }

            mSteamStored += toTransfer;
            fluid.amount -= toTransfer;
            if (fluid.amount <= 0) {
                hatch.setFillableStack(null);
            }
        }
    }

    private void processSteamOutput() {
        if (mSteamStored <= 0) return;
        for (MTESteamHubOutputHatch hatch : mSteamOutputHatches) {
            if (mSteamStored <= 0) break;
            FluidStack existing = hatch.getFluid();
            int currentAmount = existing != null ? existing.amount : 0;
            int hatchCapacity = hatch.getCapacity();
            int canOutput = (int) Math.min(mSteamStored, hatchCapacity - currentAmount);
            if (canOutput <= 0) continue;

            FluidStack output = createSteamFluidStack(canOutput);
            int filled = hatch.fill(output, true);
            if (filled > 0) {
                mSteamStored -= filled;
            }
        }
    }

    private FluidStack createSteamFluidStack(int amount) {
        if (mStoredFluidType != null) {
            return new FluidStack(mStoredFluidType.getFluid(), amount);
        }
        return Materials.Steam.getGas(amount);
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
        return new String[] { EnumChatFormatting.BLUE + "Steam Hub Array",
            EnumChatFormatting.GRAY + "Status: "
                + (mMachine ? EnumChatFormatting.GREEN + "Formed" : EnumChatFormatting.RED + "Incomplete"),
            EnumChatFormatting.GRAY + "Steam Stored: "
                + EnumChatFormatting.YELLOW
                + GTUtility.formatNumbers(mSteamStored)
                + " / "
                + GTUtility.formatNumbers(totalCapacity)
                + " L",
            EnumChatFormatting.GRAY + "Pressure Units: " + EnumChatFormatting.WHITE + mPressureUnitCount,
            EnumChatFormatting.GRAY + "Reinforced Units: " + EnumChatFormatting.WHITE + mReinforcedUnitCount,
            EnumChatFormatting.GRAY + "Fluid Type: "
                + EnumChatFormatting.AQUA
                + (mStoredFluidType != null ? mStoredFluidType.getLocalizedName() : "None") };
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType("Steam Hub Array")
            .addInfo("Stackable steam storage multiblock structure")
            .addInfo("Minimum 2 layers (base + 1 storage layer)")
            .addInfo("Maximum 13 layers (base + 12 storage layers)")
            .addInfo("Pressure Unit: 16,000,000 L each")
            .addInfo("Reinforced Unit: 64,000,000 L each")
            .addInfo("Stores Steam and Superheated Steam only")
            .addInfo("No maintenance hatch required")
            .beginVariableStructureBlock(5, 5, 2, 13, 5, 5, false)
            .addController("Front center of base layer")
            .addCasingInfoMin("Bronze Plated Bricks", 1, false)
            .addOtherStructurePart("Steam Hub Input Hatch", "Any base layer position", 1)
            .addOtherStructurePart("Steam Hub Output Hatch", "Any base layer position", 1)
            .addOtherStructurePart("Pressure/Reinforced Steam Storage Unit", "Stack layers above base", 2)
            .addSubChannelUsage(GTStructureChannels.STRUCTURE_HEIGHT)
            .toolTipFinisher();
        return tt;
    }
}
