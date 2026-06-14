package com.miaokatze.gtsr.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlocksTiered;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidHandler;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.ImmutableList;
import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizons.modularui.common.widget.DynamicPositionedColumn;
import com.gtnewhorizons.modularui.common.widget.FakeSyncWidget;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;
import com.gtnewhorizons.modularui.common.widget.TextWidget;
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.gui.MTEWaterHubArrayGui;
import com.miaokatze.gtsr.common.machine.base.MTEHubStorageUnit;
import com.miaokatze.gtsr.common.machine.base.MTEReinforcedHubStorageUnit;
import com.miaokatze.gtsr.common.machine.base.MTEWaterHubInputHatch;
import com.miaokatze.gtsr.common.machine.base.MTEWaterHubOutputHatch;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IHatchElement;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrorRegistry;
import gregtech.api.util.GTUtility;
import gregtech.api.util.IGTHatchAdder;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;
import gregtech.common.misc.GTStructureChannels;

public class MTEWaterHubArray extends MTEEnhancedMultiBlockBase<MTEWaterHubArray>
    implements IConstructable, ISurvivalConstructable, com.miaokatze.gtsr.common.machine.base.IHubArray {

    private static final String STRUCTURE_PIECE_BASE = "base";
    private static final String STRUCTURE_PIECE_STACK = "stack";
    private static final String STRUCTURE_PIECE_CAP = "cap";
    private static final int HORIZONTAL_OFF_SET = 3;
    private static final int VERTICAL_OFF_SET = 0;
    private static final int DEPTH_OFF_SET = 0;
    private static final int AUTO_OUTPUT_RATE = 128_000;
    private static final int HUB_UNIT_CAPACITY = 64_000;
    private static final int REINFORCED_HUB_UNIT_CAPACITY = 256_000;
    private static final int BOUND_TRANSFER_RATE = 1_000_000;
    private static final int BOUND_TRANSFER_INTERVAL = 20;

    private static final int CASING_INDEX = GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings1, 10);

    private static IIconContainer CONTROLLER_OVERLAY;

    private static final IStructureDefinition<MTEWaterHubArray> STRUCTURE_DEFINITION;

    static {
        STRUCTURE_DEFINITION = StructureDefinition.<MTEWaterHubArray>builder()
            .addShape(
                STRUCTURE_PIECE_BASE,
                transpose(
                    new String[][] { { "  C~C  ", " CDCDC ", "CDCCCDC", "ECCCCCE", "CDCCCDC", " CDCDC ", "  CEC  " } }))
            .addShape(
                STRUCTURE_PIECE_STACK,
                transpose(
                    new String[][] { { "       ", "  DCD  ", " DAAAD ", "ECAAACE", " DAAAD ", "  DCD  ", "   E   " } }))
            .addShape(
                STRUCTURE_PIECE_CAP,
                transpose(
                    new String[][] { { "   C   ", "  CDC  ", " CCCCC ", "DCCCCCD", " CCCCC ", "  CDC  ", "   C   " } }))
            .addElement(
                'A',
                ofChain(
                    buildHatchAdder(MTEWaterHubArray.class)
                        .atLeast(WaterHubStorageElement.HubUnit, WaterHubStorageElement.ReinforcedHubUnit)
                        .casingIndex(CASING_INDEX)
                        .hint(2)
                        .buildAndChain(
                            onElementPass(
                                MTEWaterHubArray::onCasingAdded,
                                ofBlocksTiered(
                                    MTEWaterHubArray::getCasingTier,
                                    ImmutableList.of(
                                        Pair.of(GregTechAPI.sBlockCasings1, 10),
                                        Pair.of(GregTechAPI.sBlockCasings2, 0)),
                                    -1,
                                    (MTEWaterHubArray t, Integer tier) -> t.mCasingTier = tier,
                                    (MTEWaterHubArray t) -> t.mCasingTier)))))
            .addElement(
                'C',
                ofChain(
                    buildHatchAdder(MTEWaterHubArray.class).atLeast(WaterHubHatchElement.WaterOutput)
                        .casingIndex(CASING_INDEX)
                        .hint(1)
                        .build(),
                    buildHatchAdder(MTEWaterHubArray.class).atLeast(WaterHubHatchElement.WaterInput)
                        .casingIndex(CASING_INDEX)
                        .hint(1)
                        .build(),
                    onElementPass(
                        MTEWaterHubArray::onCasingAdded,
                        ofBlocksTiered(
                            MTEWaterHubArray::getCasingTier,
                            ImmutableList
                                .of(Pair.of(GregTechAPI.sBlockCasings1, 10), Pair.of(GregTechAPI.sBlockCasings2, 0)),
                            -1,
                            (MTEWaterHubArray t, Integer tier) -> t.mCasingTier = tier,
                            (MTEWaterHubArray t) -> t.mCasingTier))))
            .addElement(
                'D',
                onElementPass(
                    MTEWaterHubArray::onCasingAdded,
                    ofBlocksTiered(
                        MTEWaterHubArray::getPipeTier,
                        ImmutableList
                            .of(Pair.of(GregTechAPI.sBlockCasings2, 12), Pair.of(GregTechAPI.sBlockCasings2, 13)),
                        -1,
                        (MTEWaterHubArray t, Integer tier) -> t.mPipeTier = tier,
                        (MTEWaterHubArray t) -> t.mPipeTier)))
            .addElement(
                'E',
                onElementPass(
                    MTEWaterHubArray::onCasingAdded,
                    ofBlocksTiered(
                        MTEWaterHubArray::getFrameTier,
                        ImmutableList.of(
                            Pair.of(GregTechAPI.sBlockFrames, Materials.Bronze.mMetaItemSubID),
                            Pair.of(GregTechAPI.sBlockFrames, Materials.Steel.mMetaItemSubID)),
                        -1,
                        (MTEWaterHubArray t, Integer tier) -> t.mFrameTier = tier,
                        (MTEWaterHubArray t) -> t.mFrameTier)))
            .build();
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
    public static Integer getFrameTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockFrames && meta == Materials.Bronze.mMetaItemSubID) return 1;
        if (block == GregTechAPI.sBlockFrames && meta == Materials.Steel.mMetaItemSubID) return 2;
        return null;
    }

    private enum WaterHubHatchElement implements IHatchElement<MTEWaterHubArray> {

        WaterInput(MTEWaterHubArray::addWaterInputToMachineList, MTEWaterHubInputHatch.class),
        WaterOutput(MTEWaterHubArray::addWaterOutputToMachineList, MTEWaterHubOutputHatch.class);

        private final List<Class<? extends IMetaTileEntity>> mteClasses;
        private final IGTHatchAdder<MTEWaterHubArray> adder;

        @SafeVarargs
        WaterHubHatchElement(IGTHatchAdder<MTEWaterHubArray> adder, Class<? extends IMetaTileEntity>... classes) {
            this.mteClasses = Collections.unmodifiableList(Arrays.asList(classes));
            this.adder = adder;
        }

        @Override
        public List<? extends Class<? extends IMetaTileEntity>> mteClasses() {
            return mteClasses;
        }

        @Override
        public IGTHatchAdder<? super MTEWaterHubArray> adder() {
            return adder;
        }

        @Override
        public long count(MTEWaterHubArray t) {
            return this == WaterInput ? t.mWaterInputHatches.size() : t.mWaterOutputHatches.size();
        }
    }

    private enum WaterHubStorageElement implements IHatchElement<MTEWaterHubArray> {

        HubUnit(MTEWaterHubArray::addHubUnitToMachineList, MTEHubStorageUnit.class),
        ReinforcedHubUnit(MTEWaterHubArray::addReinforcedHubUnitToMachineList, MTEReinforcedHubStorageUnit.class);

        private final List<Class<? extends IMetaTileEntity>> mteClasses;
        private final IGTHatchAdder<MTEWaterHubArray> adder;

        @SafeVarargs
        WaterHubStorageElement(IGTHatchAdder<MTEWaterHubArray> adder, Class<? extends IMetaTileEntity>... classes) {
            this.mteClasses = Collections.unmodifiableList(Arrays.asList(classes));
            this.adder = adder;
        }

        @Override
        public List<? extends Class<? extends IMetaTileEntity>> mteClasses() {
            return mteClasses;
        }

        @Override
        public IGTHatchAdder<? super MTEWaterHubArray> adder() {
            return adder;
        }

        @Override
        public long count(MTEWaterHubArray t) {
            return this == HubUnit ? t.mHubUnitCount : t.mReinforcedHubUnitCount;
        }
    }

    public static class BoundCacheNode {

        public int x;
        public int y;
        public int z;
        public int dimensionId;
        public boolean isOutputMode;

        public BoundCacheNode(int x, int y, int z, int dimensionId, boolean isOutputMode) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.dimensionId = dimensionId;
            this.isOutputMode = isOutputMode;
        }

        public void writeToNBT(NBTTagCompound tag) {
            tag.setInteger("x", x);
            tag.setInteger("y", y);
            tag.setInteger("z", z);
            tag.setInteger("dim", dimensionId);
            tag.setBoolean("out", isOutputMode);
        }

        public static BoundCacheNode readFromNBT(NBTTagCompound tag) {
            return new BoundCacheNode(
                tag.getInteger("x"),
                tag.getInteger("y"),
                tag.getInteger("z"),
                tag.getInteger("dim"),
                tag.getBoolean("out"));
        }
    }

    private final ArrayList<MTEWaterHubInputHatch> mWaterInputHatches = new ArrayList<>();
    private final ArrayList<MTEWaterHubOutputHatch> mWaterOutputHatches = new ArrayList<>();
    private final ArrayList<BoundCacheNode> mBoundNodes = new ArrayList<>();

    public int mHubUnitCount = 0;
    public int mReinforcedHubUnitCount = 0;
    private int mCasingAmount = 0;
    public int mSetTier = -1;
    private int mCasingTier = -1;
    private int mPipeTier = -1;
    private int mFrameTier = -1;
    public int mStackCount = 0;
    public long mWaterStored = 0;
    private String mStoredFluidType = null;
    public boolean mOverflowInput = false;

    public MTEWaterHubArray(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTEWaterHubArray(String aName) {
        super(aName);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister aBlockIconRegister) {
        CONTROLLER_OVERLAY = Textures.BlockIcons.custom("gtsr:MTEWaterHubArray");
        super.registerIcons(aBlockIconRegister);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEWaterHubArray(mName);
    }

    @Override
    public IStructureDefinition<MTEWaterHubArray> getStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        for (MTEWaterHubInputHatch hatch : mWaterInputHatches) {
            hatch.mController = null;
        }
        for (MTEWaterHubOutputHatch hatch : mWaterOutputHatches) {
            hatch.mController = null;
        }

        mHubUnitCount = 0;
        mReinforcedHubUnitCount = 0;
        mCasingAmount = 0;
        mSetTier = -1;
        mCasingTier = -1;
        mPipeTier = -1;
        mFrameTier = -1;
        mStackCount = 0;
        mWaterInputHatches.clear();
        mWaterOutputHatches.clear();

        if (!checkPiece(STRUCTURE_PIECE_BASE, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET, errors)) {
            return;
        }

        for (int i = 0; i < 3; i++) {
            int bOffset = 1 + i;
            if (!checkPiece(STRUCTURE_PIECE_STACK, HORIZONTAL_OFF_SET, bOffset, DEPTH_OFF_SET)) break;
            mStackCount++;
        }

        if (mStackCount == 0) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }

        // Validate all tier fields are consistent
        if (mCasingTier <= 0 || mPipeTier <= 0 || mFrameTier <= 0) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }
        if (mCasingTier != mPipeTier || mCasingTier != mFrameTier) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }
        mSetTier = mCasingTier;

        if (mSetTier == 1 && mReinforcedHubUnitCount > 0) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }
        if (mSetTier >= 2 && mHubUnitCount > 0) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }

        if (mSetTier == 1 && mHubUnitCount <= 0) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }
        if (mSetTier >= 2 && mReinforcedHubUnitCount <= 0) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }

        int tierCasingIndex;
        if (mSetTier >= 2) {
            tierCasingIndex = GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings2, 0);
        } else {
            tierCasingIndex = CASING_INDEX;
        }
        for (MTEWaterHubInputHatch hatch : mWaterInputHatches) {
            hatch.updateTexture(tierCasingIndex);
        }
        for (MTEWaterHubOutputHatch hatch : mWaterOutputHatches) {
            hatch.updateTexture(tierCasingIndex);
        }

        getBaseMetaTileEntity().issueTileUpdate();
    }

    private void onCasingAdded() {
        mCasingAmount++;
    }

    public boolean addWaterInputToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity instanceof MTEWaterHubInputHatch hatch) {
            hatch.updateTexture(aBaseCasingIndex);
            hatch.mController = this;
            return mWaterInputHatches.add(hatch);
        }
        return false;
    }

    public boolean addWaterOutputToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity instanceof MTEWaterHubOutputHatch hatch) {
            hatch.updateTexture(aBaseCasingIndex);
            hatch.mController = this;
            return mWaterOutputHatches.add(hatch);
        }
        return false;
    }

    public boolean addHubUnitToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity instanceof MTEHubStorageUnit) {
            mHubUnitCount++;
            return true;
        }
        return false;
    }

    public boolean addReinforcedHubUnitToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity instanceof MTEReinforcedHubStorageUnit) {
            mReinforcedHubUnitCount++;
            return true;
        }
        return false;
    }

    public boolean isFormed() {
        return mMachine;
    }

    public static boolean isWaterFluid(FluidStack aFluid) {
        if (aFluid == null || aFluid.getFluid() == null) return false;
        String name = aFluid.getFluid()
            .getName();
        return "water".equals(name) || "ic2distilledwater".equals(name);
    }

    public static boolean isWaterFluidName(String fluidName) {
        return "water".equals(fluidName) || "ic2distilledwater".equals(fluidName);
    }

    @Override
    public int receiveFluid(FluidStack fluid, boolean doFill) {
        return receiveWater(fluid, doFill);
    }

    @Override
    public FluidStack extractFluid(int amount, boolean doDrain) {
        return extractWater(amount, doDrain);
    }

    public int receiveWater(FluidStack aFluid, boolean doFill) {
        if (aFluid == null) return 0;
        if (!isWaterFluid(aFluid)) return 0;
        if (mStoredFluidType != null && !mStoredFluidType.equals(
            aFluid.getFluid()
                .getName()))
            return 0;

        long capacity = getTotalCapacity();
        long canAccept = capacity - mWaterStored;

        if (mOverflowInput) {
            if (doFill) {
                if (mStoredFluidType == null) {
                    mStoredFluidType = aFluid.getFluid()
                        .getName();
                }
                long actualStore = Math.min(aFluid.amount, canAccept);
                mWaterStored += actualStore;
            }
            return aFluid.amount;
        }

        int toAccept = (int) Math.min(aFluid.amount, canAccept);

        if (doFill && toAccept > 0) {
            if (mStoredFluidType == null) {
                mStoredFluidType = aFluid.getFluid()
                    .getName();
            }
            mWaterStored += toAccept;
        }

        return toAccept;
    }

    public FluidStack extractWater(int maxDrain, boolean doDrain) {
        if (mWaterStored <= 0 || mStoredFluidType == null) return null;

        int toDrain = (int) Math.min(maxDrain, mWaterStored);
        FluidStack result = FluidStack.loadFluidStackFromNBT(createFluidTag(mStoredFluidType, toDrain));

        if (doDrain) {
            mWaterStored -= toDrain;
            if (mWaterStored <= 0) {
                mStoredFluidType = null;
            }
        }

        return result;
    }

    public FluidStack getStoredFluidStack() {
        if (mStoredFluidType == null || mWaterStored <= 0) return null;
        int amount = (int) Math.min(mWaterStored, Integer.MAX_VALUE);
        return FluidStack.loadFluidStackFromNBT(createFluidTag(mStoredFluidType, amount));
    }

    private static NBTTagCompound createFluidTag(String fluidName, int amount) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("FluidName", fluidName);
        tag.setInteger("Amount", amount);
        return tag;
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece(STRUCTURE_PIECE_BASE, stackSize, hintsOnly, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET);
        int tTotalHeight = Math.max(2, GTStructureChannels.STRUCTURE_HEIGHT.getValueClamped(stackSize, 2, 4));
        int stackCount = tTotalHeight - 1;
        for (int i = 0; i < stackCount; i++) {
            int bOffset = 1 + i;
            buildPiece(STRUCTURE_PIECE_STACK, stackSize, hintsOnly, HORIZONTAL_OFF_SET, bOffset, DEPTH_OFF_SET);
        }
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        int built = survivalBuildPiece(
            STRUCTURE_PIECE_BASE,
            stackSize,
            HORIZONTAL_OFF_SET,
            VERTICAL_OFF_SET,
            DEPTH_OFF_SET,
            elementBudget,
            env,
            false,
            true);
        if (built >= 0) return built;
        int tTotalHeight = Math.max(2, GTStructureChannels.STRUCTURE_HEIGHT.getValueClamped(stackSize, 2, 4));
        int stackCount = tTotalHeight - 1;
        for (int i = 0; i < stackCount; i++) {
            int bOffset = 1 + i;
            built = survivalBuildPiece(
                STRUCTURE_PIECE_STACK,
                stackSize,
                HORIZONTAL_OFF_SET,
                bOffset,
                DEPTH_OFF_SET,
                elementBudget,
                env,
                false,
                true);
            if (built >= 0) return built;
        }
        return -1;
    }

    @Override
    public String[] getStructureDescription(ItemStack stackSize) {
        return new String[] { EnumChatFormatting.AQUA + "Structure:", "1. BASE (1 layer): Controller layer (bottom)",
            "2. STACK (1 layer): Repeatable storage unit layer (1~3, on top of BASE)",
            "3. Total height: 2~4 layers (7x7x2 to 7x7x4)", "4. At least 1 Input Hatch and 1 Output Hatch required" };
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
        return (long) mHubUnitCount * HUB_UNIT_CAPACITY + (long) mReinforcedHubUnitCount * REINFORCED_HUB_UNIT_CAPACITY;
    }

    public long getWaterStored() {
        return mWaterStored;
    }

    public int getHubUnitCount() {
        return mHubUnitCount;
    }

    public int getReinforcedHubUnitCount() {
        return mReinforcedHubUnitCount;
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (!aBaseMetaTileEntity.isServerSide() || !mMachine) return;

        long totalCapacity = getTotalCapacity();
        if (mWaterStored > totalCapacity) {
            mWaterStored = totalCapacity;
        }

        autoOutputWater();

        if (aTick % BOUND_TRANSFER_INTERVAL == 0) {
            transferWithBoundNodes(aBaseMetaTileEntity);
        }
    }

    private void autoOutputWater() {
        if (mWaterStored <= 0 || mStoredFluidType == null) return;
        long capacity = getTotalCapacity();
        for (MTEWaterHubOutputHatch hatch : mWaterOutputHatches) {
            if (mWaterStored <= 0) break;
            if (hatch.mOverflowOutput && mWaterStored < (long) (capacity * 0.9)) continue;
            IGregTechTileEntity hatchBase = hatch.getBaseMetaTileEntity();
            if (hatchBase == null) continue;
            ForgeDirection hatchFront = hatchBase.getFrontFacing();
            IFluidHandler adjacent = hatchBase.getITankContainerAtSide(hatchFront);
            if (adjacent == null) continue;

            int toPush = (int) Math.min(AUTO_OUTPUT_RATE, mWaterStored);
            FluidStack toExport = FluidStack.loadFluidStackFromNBT(createFluidTag(mStoredFluidType, toPush));
            int pushed = adjacent.fill(hatchFront.getOpposite(), toExport, true);
            if (pushed > 0) {
                mWaterStored -= pushed;
                if (mWaterStored <= 0) {
                    mStoredFluidType = null;
                    return;
                }
            }
        }
    }

    private void transferWithBoundNodes(IGregTechTileEntity aBaseMetaTileEntity) {
        if (!hasHubSingularityChip()) {
            return;
        }

        World world = aBaseMetaTileEntity.getWorld();
        if (world == null) return;
        int dimId = world.provider.dimensionId;

        ArrayList<BoundCacheNode> invalidNodes = new ArrayList<>();

        for (BoundCacheNode node : mBoundNodes) {
            if (node.dimensionId != dimId) continue;

            TileEntity tile = world.getTileEntity(node.x, node.y, node.z);
            if (!(tile instanceof IGregTechTileEntity gtTile)) {
                invalidNodes.add(node);
                continue;
            }

            IMetaTileEntity mte = gtTile.getMetaTileEntity();
            if (!isWaterCacheNode(mte)) {
                invalidNodes.add(node);
                continue;
            }

            if (node.isOutputMode) {
                if (mWaterStored <= 0 || mStoredFluidType == null) continue;
                int toTransfer = (int) Math.min(BOUND_TRANSFER_RATE, mWaterStored);
                FluidStack toExport = FluidStack.loadFluidStackFromNBT(createFluidTag(mStoredFluidType, toTransfer));
                int filled = gtTile.fill(ForgeDirection.UNKNOWN, toExport, true);
                if (filled > 0) {
                    mWaterStored -= filled;
                    if (mWaterStored <= 0) {
                        mStoredFluidType = null;
                    }
                }
            } else {
                FluidStack drained = gtTile.drain(ForgeDirection.UNKNOWN, BOUND_TRANSFER_RATE, false);
                if (drained != null && isWaterFluid(drained)) {
                    int accepted = receiveWater(drained, true);
                    if (accepted > 0) {
                        gtTile.drain(ForgeDirection.UNKNOWN, accepted, true);
                    }
                }
            }
        }

        mBoundNodes.removeAll(invalidNodes);
    }

    private static boolean isWaterCacheNode(IMetaTileEntity mte) {
        if (mte == null) return false;
        return mte.getMetaName() != null && mte.getMetaName()
            .contains("water.cache.node");
    }

    private boolean hasHubSingularityChip() {
        ItemStack stack = getControllerSlot();
        return stack != null && GTSRItemList.HubSingularityChip.isStackEqual(stack, true, true);
    }

    @Override
    public void onScrewdriverRightClick(ForgeDirection side, EntityPlayer aPlayer, float aX, float aY, float aZ,
        ItemStack aTool) {
        mOverflowInput = !mOverflowInput;
        if (aPlayer.worldObj.isRemote) return;
        GTUtility.sendChatToPlayer(
            aPlayer,
            StatCollector.translateToLocal("gtsr.tooltip.shared.overflow_input") + ": "
                + (mOverflowInput ? EnumChatFormatting.GREEN + StatCollector.translateToLocal("gtsr.tooltip.shared.on")
                    : EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.tooltip.shared.off")));
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer, ForgeDirection side,
        float aX, float aY, float aZ) {
        ItemStack held = aPlayer.getHeldItem();

        if (held != null && GTSRItemList.HubSingularityChip.isStackEqual(held, true, true)) {
            if (aBaseMetaTileEntity.isServerSide()) {
                sendBindingDebug(aPlayer);
            }
            return true;
        }

        if (held == null) {
            return super.onRightclick(aBaseMetaTileEntity, aPlayer, side, aX, aY, aZ);
        }

        if (!GTSRItemList.WaterCacheNode.isStackEqual(held, false, true)) {
            return super.onRightclick(aBaseMetaTileEntity, aPlayer, side, aX, aY, aZ);
        }

        if (!aBaseMetaTileEntity.isServerSide()) return true;

        if (!hasHubSingularityChip()) {
            GTUtility.sendChatToPlayer(aPlayer, StatCollector.translateToLocal("gtsr.binding.no_chip"));
            return true;
        }

        // Water cache node requires 0 singularity to bind
        // (singularity_consumed flag still set for compatibility, but no actual consumption)
        if (!held.hasTagCompound() || !held.getTagCompound()
            .hasKey("gtsr.singularity_consumed")) {
            if (!held.hasTagCompound()) {
                held.setTagCompound(new NBTTagCompound());
            }
            held.getTagCompound()
                .setBoolean("gtsr.singularity_consumed", true);
        }

        int myX = aBaseMetaTileEntity.getXCoord();
        int myY = aBaseMetaTileEntity.getYCoord();
        int myZ = aBaseMetaTileEntity.getZCoord();
        int myDim = aBaseMetaTileEntity.getWorld().provider.dimensionId;
        String nodeName = held.getDisplayName();

        if (held.hasTagCompound() && held.getTagCompound()
            .hasKey("gtsr.hubPos")) {
            NBTTagCompound existing = held.getTagCompound()
                .getCompoundTag("gtsr.hubPos");
            int boundX = existing.getInteger("x");
            int boundY = existing.getInteger("y");
            int boundZ = existing.getInteger("z");
            int boundDim = existing.getInteger("dim");

            if (boundX == myX && boundY == myY && boundZ == myZ && boundDim == myDim) {
                boolean isOutput = existing.hasKey("output") && existing.getBoolean("output");

                if (!isOutput) {
                    existing.setBoolean("output", true);
                    GTUtility.sendChatToPlayer(
                        aPlayer,
                        StatCollector.translateToLocal("gtsr.binding.bound_input") + nodeName
                            + StatCollector.translateToLocal("gtsr.binding.mode_input"));
                } else {
                    held.getTagCompound()
                        .removeTag("gtsr.hubPos");
                    GTUtility.sendChatToPlayer(
                        aPlayer,
                        StatCollector.translateToLocal("gtsr.binding.cleared") + nodeName
                            + StatCollector.translateToLocal("gtsr.binding.binding"));
                }
                return true;
            }
        }

        if (!held.hasTagCompound()) {
            held.setTagCompound(new NBTTagCompound());
        }

        NBTTagCompound hubTag = new NBTTagCompound();
        hubTag.setInteger("x", myX);
        hubTag.setInteger("y", myY);
        hubTag.setInteger("z", myZ);
        hubTag.setInteger("dim", myDim);
        hubTag.setString("type", "water");
        hubTag.setBoolean("output", false);

        held.getTagCompound()
            .setTag("gtsr.hubPos", hubTag);

        GTUtility.sendChatToPlayer(
            aPlayer,
            StatCollector.translateToLocal("gtsr.binding.bound_output") + nodeName
                + StatCollector.translateToLocal("gtsr.binding.mode_output"));
        return true;
    }

    private BoundCacheNode findBoundNode(int x, int y, int z, int dimId) {
        for (BoundCacheNode node : mBoundNodes) {
            if (node.x == x && node.y == y && node.z == z && node.dimensionId == dimId) {
                return node;
            }
        }
        return null;
    }

    @Override
    public void registerCacheNode(int x, int y, int z, int dim, boolean isOutputMode) {
        BoundCacheNode existing = findBoundNode(x, y, z, dim);
        if (existing != null) {
            existing.isOutputMode = isOutputMode;
        } else {
            mBoundNodes.add(new BoundCacheNode(x, y, z, dim, isOutputMode));
        }
    }

    @Override
    public void unregisterCacheNode(int x, int y, int z, int dim) {
        BoundCacheNode existing = findBoundNode(x, y, z, dim);
        if (existing != null) {
            mBoundNodes.remove(existing);
        }
    }

    @Override
    public void updateCacheNodeMode(int x, int y, int z, int dim, boolean isOutputMode) {
        BoundCacheNode existing = findBoundNode(x, y, z, dim);
        if (existing != null) {
            existing.isOutputMode = isOutputMode;
        }
    }

    @Override
    public boolean acceptsNodeType(String type) {
        return "water".equals(type);
    }

    private void sendBindingDebug(EntityPlayer aPlayer) {
        GTUtility.sendChatToPlayer(aPlayer, StatCollector.translateToLocal("gtsr.binding.debug_title"));
        if (mBoundNodes.isEmpty()) {
            GTUtility.sendChatToPlayer(aPlayer, StatCollector.translateToLocal("gtsr.binding.debug_no_bindings"));
            return;
        }
        if (!hasHubSingularityChip()) {
            GTUtility.sendChatToPlayer(aPlayer, StatCollector.translateToLocal("gtsr.binding.debug_no_chip"));
        }
        for (BoundCacheNode node : mBoundNodes) {
            String mode = node.isOutputMode ? StatCollector.translateToLocal("gtsr.binding.debug_output")
                : StatCollector.translateToLocal("gtsr.binding.debug_input");
            String posInfo = StatCollector.translateToLocal("gtsr.binding.debug_node") + node.x
                + ", "
                + node.y
                + ", "
                + node.z
                + " "
                + StatCollector.translateToLocal("gtsr.binding.debug_mode")
                + mode;
            GTUtility.sendChatToPlayer(aPlayer, posInfo);
        }
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setLong("mWaterStored", mWaterStored);
        aNBT.setInteger("mHubUnitCount", mHubUnitCount);
        aNBT.setInteger("mReinforcedHubUnitCount", mReinforcedHubUnitCount);
        aNBT.setInteger("mSetTier", mSetTier);
        aNBT.setInteger("mCasingTier", mCasingTier);
        aNBT.setInteger("mPipeTier", mPipeTier);
        aNBT.setInteger("mFrameTier", mFrameTier);
        aNBT.setBoolean("mOverflowInput", mOverflowInput);
        if (mStoredFluidType != null) {
            aNBT.setString("mStoredFluidType", mStoredFluidType);
        }
        if (!mBoundNodes.isEmpty()) {
            NBTTagCompound boundListTag = new NBTTagCompound();
            boundListTag.setInteger("count", mBoundNodes.size());
            for (int i = 0; i < mBoundNodes.size(); i++) {
                NBTTagCompound nodeTag = new NBTTagCompound();
                mBoundNodes.get(i)
                    .writeToNBT(nodeTag);
                boundListTag.setTag("node" + i, nodeTag);
            }
            aNBT.setTag("mBoundNodes", boundListTag);
        }
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mWaterStored = aNBT.getLong("mWaterStored");
        mHubUnitCount = aNBT.getInteger("mHubUnitCount");
        mReinforcedHubUnitCount = aNBT.getInteger("mReinforcedHubUnitCount");
        mSetTier = aNBT.getInteger("mSetTier");
        mCasingTier = aNBT.getInteger("mCasingTier");
        mPipeTier = aNBT.getInteger("mPipeTier");
        mFrameTier = aNBT.getInteger("mFrameTier");
        mOverflowInput = aNBT.getBoolean("mOverflowInput");
        if (aNBT.hasKey("mStoredFluidType")) {
            mStoredFluidType = aNBT.getString("mStoredFluidType");
        }
        mBoundNodes.clear();
        if (aNBT.hasKey("mBoundNodes")) {
            NBTTagCompound boundListTag = aNBT.getCompoundTag("mBoundNodes");
            int count = boundListTag.getInteger("count");
            for (int i = 0; i < count; i++) {
                NBTTagCompound nodeTag = boundListTag.getCompoundTag("node" + i);
                mBoundNodes.add(BoundCacheNode.readFromNBT(nodeTag));
            }
        }
    }

    @Override
    public NBTTagCompound getDescriptionData() {
        NBTTagCompound data = super.getDescriptionData();
        if (data == null) data = new NBTTagCompound();
        data.setInteger("mSetTier", mSetTier);
        return data;
    }

    @Override
    public void onDescriptionPacket(NBTTagCompound data) {
        super.onDescriptionPacket(data);
        mSetTier = data.getInteger("mSetTier");
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int colorIndex, boolean active, boolean redstoneLevel) {
        int casingTextureId;
        if (mSetTier >= 2) {
            casingTextureId = GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings2, 0);
        } else {
            casingTextureId = CASING_INDEX;
        }
        if (side == facing) {
            return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(casingTextureId), TextureFactory.builder()
                .addIcon(CONTROLLER_OVERLAY)
                .extFacing()
                .build() };
        }
        return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(casingTextureId) };
    }

    @Override
    protected MTEMultiBlockBaseGui<?> getGui() {
        return new MTEWaterHubArrayGui(this);
    }

    @Deprecated
    @Override
    protected void drawTexts(DynamicPositionedColumn screenElements, SlotWidget inventorySlot) {
        super.drawTexts(screenElements, inventorySlot);
        screenElements.widget(new TextWidget().setStringSupplier(() -> {
            String tierText = mSetTier == 2 ? StatCollector.translateToLocal("gtsr.gui.tier.steel")
                : StatCollector.translateToLocal("gtsr.gui.tier.bronze");
            return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.tier")
                + EnumChatFormatting.GOLD
                + tierText
                + EnumChatFormatting.RESET;
        }))
            .widget(new TextWidget().setStringSupplier(() -> {
                ItemStack chip = getControllerSlot();
                String chipText;
                if (chip != null && GTSRItemList.HubSingularityChip.isStackEqual(chip, true, true)) {
                    chipText = EnumChatFormatting.GREEN
                        + StatCollector.translateToLocal("gtsr.gui.chip.singularity_installed");
                } else {
                    chipText = EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.gui.chip.none");
                }
                return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.chip")
                    + " "
                    + chipText
                    + EnumChatFormatting.RESET;
            }))
            .widget(new TextWidget().setStringSupplier(() -> {
                String status = mMaxProgresstime > 0
                    ? EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.gui.status.running")
                    : EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.gui.status.idle");
                return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.status")
                    + " "
                    + status
                    + EnumChatFormatting.RESET;
            }))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.water_hub.storage_units")
                        + " "
                        + EnumChatFormatting.GOLD
                        + (mHubUnitCount + mReinforcedHubUnitCount)
                        + "/"
                        + (9 * mStackCount)
                        + EnumChatFormatting.RESET))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.water_hub.water_buffer")
                        + " "
                        + EnumChatFormatting.LIGHT_PURPLE
                        + NumberFormatUtil.formatNumber(mWaterStored)
                        + " L"
                        + EnumChatFormatting.RESET))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.YELLOW
                        + StatCollector.translateToLocal("gtsr.gui.water_hub.total_capacity")
                        + " "
                        + EnumChatFormatting.LIGHT_PURPLE
                        + NumberFormatUtil.formatNumber(getTotalCapacity())
                        + " L"
                        + EnumChatFormatting.RESET))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mSetTier, val -> mSetTier = val))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mMaxProgresstime, val -> mMaxProgresstime = val))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mStackCount, val -> mStackCount = val))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mHubUnitCount, val -> mHubUnitCount = val))
            .widget(
                new FakeSyncWidget.IntegerSyncer(() -> mReinforcedHubUnitCount, val -> mReinforcedHubUnitCount = val))
            .widget(new FakeSyncWidget.LongSyncer(() -> mWaterStored, val -> mWaterStored = val));
    }

    @Override
    public String[] getInfoData() {
        ArrayList<String> info = new ArrayList<>();
        info.add(
            EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.water_hub.type")
                + EnumChatFormatting.RESET);
        if (!mMachine) {
            info.add(EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.gui.building"));
            return info.toArray(new String[0]);
        }
        String tierText = mSetTier == 2 ? StatCollector.translateToLocal("gtsr.gui.tier.steel")
            : StatCollector.translateToLocal("gtsr.gui.tier.bronze");
        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.tier")
                + EnumChatFormatting.GOLD
                + tierText
                + EnumChatFormatting.RESET);
        String statusKey = mMaxProgresstime > 0 ? "gtsr.gui.status.running" : "gtsr.gui.status.idle";
        EnumChatFormatting statusColor = mMaxProgresstime > 0 ? EnumChatFormatting.AQUA : EnumChatFormatting.GRAY;
        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.status")
                + " "
                + statusColor
                + StatCollector.translateToLocal(statusKey)
                + EnumChatFormatting.RESET);
        int totalUnits = mHubUnitCount + mReinforcedHubUnitCount;
        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.water_hub.storage_units")
                + " "
                + EnumChatFormatting.GOLD
                + totalUnits
                + "/"
                + (9 * mStackCount)
                + EnumChatFormatting.RESET);
        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.water_hub.water_buffer")
                + " "
                + EnumChatFormatting.LIGHT_PURPLE
                + NumberFormatUtil.formatNumber(mWaterStored)
                + " L"
                + EnumChatFormatting.RESET);
        return info.toArray(new String[0]);
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal("gtsr.tooltip.water_hub.type"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.water_hub.desc"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.water_hub.desc2"))
            .addInfo(
                EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.tooltip.shared.screwdriver_overflow"))
            .addInfo(
                EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.tooltip.shared.overflow_input_desc"))
            .beginStructureBlock(7, 4, 7, false)
            .addController(StatCollector.translateToLocal("gtsr.tooltip.water_hub.ctrl"))
            .addOtherStructurePart(
                StatCollector.translateToLocal("gtsr.tooltip.water_hub.hub_input"),
                StatCollector.translateToLocal("gtsr.tooltip.shared.any_casing"),
                1)
            .addOtherStructurePart(
                StatCollector.translateToLocal("gtsr.tooltip.water_hub.hub_output"),
                StatCollector.translateToLocal("gtsr.tooltip.shared.any_casing"),
                1)
            .addOtherStructurePart(
                StatCollector.translateToLocal("gtsr.tooltip.water_hub.storage"),
                StatCollector.translateToLocal("gtsr.tooltip.water_hub.storage"),
                2)
            .addStructureInfo("")
            .addStructureInfo(
                EnumChatFormatting.BLUE + "Bronze"
                    + EnumChatFormatting.DARK_PURPLE
                    + "/"
                    + EnumChatFormatting.BLUE
                    + "Steel "
                    + EnumChatFormatting.DARK_PURPLE
                    + "Tier")
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.casing"), 70, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.pipe"), 7, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.gear_box"), 4, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.frame"), 24, false)
            .addStructureHint("gtsr.tooltip.shared.no_maintenance")
            .addStructureHint("gtsr.tooltip.water_hub.hint_tier1")
            .addStructureHint("gtsr.tooltip.water_hub.hint_tier2")
            .addStructureHint("gtsr.tooltip.shared.hub_singularity_cost")
            .addStructureHint("gtsr.tooltip.shared.overflow_input_screwdriver")
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
}
