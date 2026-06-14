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
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
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
import com.miaokatze.gtsr.common.gui.MTESteamHubArrayGui;
import com.miaokatze.gtsr.common.machine.base.MTEFilteredCacheNode;
import com.miaokatze.gtsr.common.machine.base.MTEHubStorageUnit;
import com.miaokatze.gtsr.common.machine.base.MTEOverpressureHubStorageUnit;
import com.miaokatze.gtsr.common.machine.base.MTEReinforcedHubStorageUnit;
import com.miaokatze.gtsr.common.machine.base.MTESteamHubInputHatch;
import com.miaokatze.gtsr.common.machine.base.MTESteamHubOutputHatch;

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

public class MTESteamHubArray extends MTEEnhancedMultiBlockBase<MTESteamHubArray>
    implements IConstructable, ISurvivalConstructable, com.miaokatze.gtsr.common.machine.base.IHubArray {

    private static final String STRUCTURE_PIECE_BASE = "base";
    private static final String STRUCTURE_PIECE_STACK = "stack";
    private static final String STRUCTURE_PIECE_CAP = "cap";
    private static final int HORIZONTAL_OFF_SET = 4;
    private static final int VERTICAL_OFF_SET = 0;
    private static final int DEPTH_OFF_SET = 1;
    private static final int AUTO_OUTPUT_RATE = 2_000_000;
    private static final int TRANSFER_RATE = 1_000_000;

    private static final int CASING_INDEX = GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings1, 10);

    private static IIconContainer CONTROLLER_OVERLAY;

    private static final IStructureDefinition<MTESteamHubArray> STRUCTURE_DEFINITION;

    static {
        STRUCTURE_DEFINITION = StructureDefinition.<MTESteamHubArray>builder()
            .addShape(
                STRUCTURE_PIECE_BASE,
                transpose(
                    new String[][] { { "         ", "   F~F   ", "  ECCCE  ", " FCCCCCF ", " DCCCCCD ", " FCCCCCF ",
                        "  ECCCE  ", "   FDF   ", "         " } }))
            .addShape(
                STRUCTURE_PIECE_STACK,
                transpose(
                    new String[][] { { "         ", "   FDF   ", "  AAAAA  ", " FAAAAAF ", " DAAAAAD ", " FAAAAAF ",
                        "  AAAAA  ", "   FDF   ", "         " } }))
            .addShape(
                STRUCTURE_PIECE_CAP,
                transpose(
                    new String[][] { { "   CCC   ", "  CFCFC  ", " CCCCCCC ", "CFCCCCCFC", "CCCCCCCCC", "CFCCCCCFC",
                        " CCCCCCC ", "  CFCFC  ", "   CCC   " } }))
            .addElement(
                'A',
                ofChain(
                    buildHatchAdder(MTESteamHubArray.class)
                        .atLeast(
                            SteamHubStorageElement.PressureUnit,
                            SteamHubStorageElement.ReinforcedUnit,
                            SteamHubStorageElement.OverpressureUnit)
                        .casingIndex(CASING_INDEX)
                        .hint(2)
                        .buildAndChain(
                            onElementPass(
                                MTESteamHubArray::onCasingAdded,
                                ofBlocksTiered(
                                    MTESteamHubArray::getCasingTier,
                                    ImmutableList.of(
                                        Pair.of(GregTechAPI.sBlockCasings1, 10),
                                        Pair.of(GregTechAPI.sBlockCasings2, 0),
                                        Pair.of(GregTechAPI.sBlockCasings4, 0)),
                                    -1,
                                    (MTESteamHubArray t, Integer tier) -> t.mCasingTier = tier,
                                    (MTESteamHubArray t) -> t.mCasingTier)))))
            .addElement(
                'C',
                ofChain(
                    buildHatchAdder(MTESteamHubArray.class)
                        .atLeast(SteamHubHatchElement.SteamInput, SteamHubHatchElement.SteamOutput)
                        .casingIndex(CASING_INDEX)
                        .hint(1)
                        .buildAndChain(
                            onElementPass(
                                MTESteamHubArray::onCasingAdded,
                                ofBlocksTiered(
                                    MTESteamHubArray::getCasingTier,
                                    ImmutableList.of(
                                        Pair.of(GregTechAPI.sBlockCasings1, 10),
                                        Pair.of(GregTechAPI.sBlockCasings2, 0),
                                        Pair.of(GregTechAPI.sBlockCasings4, 0)),
                                    -1,
                                    (MTESteamHubArray t, Integer tier) -> t.mCasingTier = tier,
                                    (MTESteamHubArray t) -> t.mCasingTier)))))
            .addElement(
                'D',
                onElementPass(
                    MTESteamHubArray::onCasingAdded,
                    ofBlocksTiered(
                        MTESteamHubArray::getPipeTier,
                        ImmutableList.of(
                            Pair.of(GregTechAPI.sBlockCasings2, 12),
                            Pair.of(GregTechAPI.sBlockCasings2, 13),
                            Pair.of(GregTechAPI.sBlockCasings2, 15)),
                        -1,
                        (MTESteamHubArray t, Integer tier) -> t.mPipeTier = tier,
                        (MTESteamHubArray t) -> t.mPipeTier)))
            .addElement(
                'E',
                onElementPass(
                    MTESteamHubArray::onCasingAdded,
                    ofBlocksTiered(
                        MTESteamHubArray::getGearTier,
                        ImmutableList.of(
                            Pair.of(GregTechAPI.sBlockCasings2, 2),
                            Pair.of(GregTechAPI.sBlockCasings2, 3),
                            Pair.of(GregTechAPI.sBlockCasings2, 15)),
                        -1,
                        (MTESteamHubArray t, Integer tier) -> t.mGearTier = tier,
                        (MTESteamHubArray t) -> t.mGearTier)))
            .addElement(
                'F',
                onElementPass(
                    MTESteamHubArray::onCasingAdded,
                    ofBlocksTiered(
                        MTESteamHubArray::getFrameTier,
                        ImmutableList.of(
                            Pair.of(GregTechAPI.sBlockFrames, Materials.Bronze.mMetaItemSubID),
                            Pair.of(GregTechAPI.sBlockFrames, Materials.Steel.mMetaItemSubID),
                            Pair.of(GregTechAPI.sBlockFrames, Materials.TungstenSteel.mMetaItemSubID)),
                        -1,
                        (MTESteamHubArray t, Integer tier) -> t.mFrameTier = tier,
                        (MTESteamHubArray t) -> t.mFrameTier)))
            .build();
    }

    @Nullable
    public static Integer getCasingTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockCasings1 && meta == 10) return 1;
        if (block == GregTechAPI.sBlockCasings2 && meta == 0) return 2;
        if (block == GregTechAPI.sBlockCasings4 && meta == 0) return 3;
        return null;
    }

    @Nullable
    public static Integer getPipeTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockCasings2 && meta == 12) return 1;
        if (block == GregTechAPI.sBlockCasings2 && meta == 13) return 2;
        if (block == GregTechAPI.sBlockCasings2 && meta == 15) return 3;
        return null;
    }

    @Nullable
    public static Integer getGearTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockCasings2 && meta == 2) return 1;
        if (block == GregTechAPI.sBlockCasings2 && meta == 3) return 2;
        if (block == GregTechAPI.sBlockCasings2 && meta == 15) return 3;
        return null;
    }

    @Nullable
    public static Integer getFrameTier(Block block, int meta) {
        if (block == GregTechAPI.sBlockFrames && meta == Materials.Bronze.mMetaItemSubID) return 1;
        if (block == GregTechAPI.sBlockFrames && meta == Materials.Steel.mMetaItemSubID) return 2;
        if (block == GregTechAPI.sBlockFrames && meta == Materials.TungstenSteel.mMetaItemSubID) return 3;
        return null;
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

        PressureUnit(MTESteamHubArray::addPressureUnitToMachineList, MTEHubStorageUnit.class),
        ReinforcedUnit(MTESteamHubArray::addReinforcedUnitToMachineList, MTEReinforcedHubStorageUnit.class),
        OverpressureUnit(MTESteamHubArray::addOverpressureUnitToMachineList, MTEOverpressureHubStorageUnit.class);

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
            if (this == PressureUnit) return t.mPressureUnitCount;
            if (this == ReinforcedUnit) return t.mReinforcedUnitCount;
            return t.mOverpressureUnitCount;
        }
    }

    private static class BoundCacheNode {

        final int x, y, z;
        final int dimensionId;
        final boolean isReinforced;
        boolean isOutputMode;
        transient IGregTechTileEntity cachedTile;
        transient long lastLookupTick;

        BoundCacheNode(int x, int y, int z, int dim, boolean reinforced, boolean outputMode) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.dimensionId = dim;
            this.isReinforced = reinforced;
            this.isOutputMode = outputMode;
        }

        void invalidateCache() {
            cachedTile = null;
            lastLookupTick = 0;
        }
    }

    private final ArrayList<MTESteamHubInputHatch> mSteamInputHatches = new ArrayList<>();
    private final ArrayList<MTESteamHubOutputHatch> mSteamOutputHatches = new ArrayList<>();
    private final ArrayList<BoundCacheNode> mBoundNodes = new ArrayList<>();

    public int mPressureUnitCount = 0;
    public int mReinforcedUnitCount = 0;
    public int mOverpressureUnitCount = 0;
    private int mCasingAmount = 0;
    public int mSetTier = -1;
    private int mCasingTier = -1;
    private int mPipeTier = -1;
    private int mGearTier = -1;
    private int mFrameTier = -1;
    public int mStackCount = 0;
    public long mSteamStored = 0;
    private FluidStack mStoredFluidType = null;
    private long mTickCounter = 0;
    public boolean mOverflowInput = false;

    public MTESteamHubArray(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTESteamHubArray(String aName) {
        super(aName);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister aBlockIconRegister) {
        CONTROLLER_OVERLAY = Textures.BlockIcons.custom("gtsr:MTESteamHubArray");
        super.registerIcons(aBlockIconRegister);
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
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        for (MTESteamHubInputHatch hatch : mSteamInputHatches) {
            hatch.mController = null;
        }
        for (MTESteamHubOutputHatch hatch : mSteamOutputHatches) {
            hatch.mController = null;
        }

        mPressureUnitCount = 0;
        mReinforcedUnitCount = 0;
        mOverpressureUnitCount = 0;
        mCasingAmount = 0;
        mSetTier = -1;
        mCasingTier = -1;
        mPipeTier = -1;
        mGearTier = -1;
        mFrameTier = -1;
        mStackCount = 0;
        mSteamInputHatches.clear();
        mSteamOutputHatches.clear();

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

        if (!checkPiece(STRUCTURE_PIECE_CAP, HORIZONTAL_OFF_SET, -1, DEPTH_OFF_SET, errors)) {
            return;
        }

        // Validate all tier fields are consistent
        if (mCasingTier <= 0 || mPipeTier <= 0 || mGearTier <= 0 || mFrameTier <= 0) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }
        if (mCasingTier != mPipeTier || mCasingTier != mGearTier || mCasingTier != mFrameTier) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }
        mSetTier = mCasingTier;

        if (mSetTier == 1 && (mReinforcedUnitCount > 0 || mOverpressureUnitCount > 0)) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }
        if (mSetTier == 2 && (mPressureUnitCount > 0 || mOverpressureUnitCount > 0)) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }
        if (mSetTier >= 3 && (mPressureUnitCount > 0 || mReinforcedUnitCount > 0)) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }

        if (mSetTier >= 3 && mOverpressureUnitCount <= 0) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }

        if ((mPressureUnitCount + mReinforcedUnitCount + mOverpressureUnitCount) <= 0) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }

        int tierCasingIndex;
        if (mSetTier >= 3) {
            tierCasingIndex = GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings4, 0);
        } else if (mSetTier == 2) {
            tierCasingIndex = GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings2, 0);
        } else {
            tierCasingIndex = CASING_INDEX;
        }
        for (MTESteamHubInputHatch hatch : mSteamInputHatches) {
            hatch.updateTexture(tierCasingIndex);
        }
        for (MTESteamHubOutputHatch hatch : mSteamOutputHatches) {
            hatch.updateTexture(tierCasingIndex);
        }

        getBaseMetaTileEntity().issueTileUpdate();
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
        if (aMetaTileEntity instanceof MTEHubStorageUnit) {
            mPressureUnitCount++;
            return true;
        }
        return false;
    }

    public boolean addReinforcedUnitToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity instanceof MTEReinforcedHubStorageUnit) {
            mReinforcedUnitCount++;
            return true;
        }
        return false;
    }

    public boolean addOverpressureUnitToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity instanceof MTEOverpressureHubStorageUnit) {
            mOverpressureUnitCount++;
            return true;
        }
        return false;
    }

    public boolean isFormed() {
        return mMachine;
    }

    @Override
    public int receiveFluid(FluidStack fluid, boolean doFill) {
        return receiveSteam(fluid, doFill);
    }

    @Override
    public FluidStack extractFluid(int amount, boolean doDrain) {
        return extractSteam(amount, doDrain);
    }

    public int receiveSteam(FluidStack aFluid, boolean doFill) {
        if (aFluid == null) return 0;
        boolean isAllowed = mSetTier >= 3 && hasReinforcedChipInstalled()
            ? MTESteamHubOutputHatch.isAnySteamFluid(aFluid)
            : MTESteamHubOutputHatch.isSteamFluid(aFluid);
        if (!isAllowed) return 0;
        if (mStoredFluidType != null && !mStoredFluidType.isFluidEqual(aFluid)) return 0;

        long capacity = getTotalCapacity();
        long canAccept = capacity - mSteamStored;

        if (mOverflowInput) {
            if (doFill) {
                if (mStoredFluidType == null) {
                    mStoredFluidType = new FluidStack(aFluid.getFluid(), 0);
                }
                long actualStore = Math.min(aFluid.amount, canAccept);
                mSteamStored += actualStore;
            }
            return aFluid.amount;
        }

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

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece(STRUCTURE_PIECE_CAP, stackSize, hintsOnly, HORIZONTAL_OFF_SET, -1, DEPTH_OFF_SET);
        buildPiece(STRUCTURE_PIECE_BASE, stackSize, hintsOnly, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET);
        int tTotalHeight = Math.max(3, GTStructureChannels.STRUCTURE_HEIGHT.getValueClamped(stackSize, 3, 5));
        int stackCount = tTotalHeight - 2;
        for (int i = 0; i < stackCount; i++) {
            int bOffset = 1 + i;
            buildPiece(STRUCTURE_PIECE_STACK, stackSize, hintsOnly, HORIZONTAL_OFF_SET, bOffset, DEPTH_OFF_SET);
        }
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        int built = survivalBuildPiece(
            STRUCTURE_PIECE_CAP,
            stackSize,
            HORIZONTAL_OFF_SET,
            -1,
            DEPTH_OFF_SET,
            elementBudget,
            env,
            false,
            true);
        if (built >= 0) return built;
        built = survivalBuildPiece(
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
        int tTotalHeight = Math.max(3, GTStructureChannels.STRUCTURE_HEIGHT.getValueClamped(stackSize, 3, 5));
        int stackCount = tTotalHeight - 2;
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
        return new String[] { EnumChatFormatting.AQUA + "Structure:", "1. CAP (1 layer): Base foundation (bottom)",
            "2. BASE (1 layer): Controller layer", "3. STACK (1 layer): Repeatable storage unit layer (1~3)",
            "4. Total height: 3~5 layers (9x9x3 to 9x9x5)", "5. At least 1 Input Hatch and 1 Output Hatch required" };
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
        long base = (long) mPressureUnitCount * 16_000_000 + (long) mReinforcedUnitCount * 64_000_000
            + (long) mOverpressureUnitCount * 512_000_000;
        return hasReinforcedChipInstalled() ? base * 10 : base;
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

        mTickCounter++;
        if (mTickCounter % 20 == 0) {
            transferWithBoundNodes();
        }
    }

    private int getNodeTransferRate(IGregTechTileEntity gte) {
        IMetaTileEntity mte = gte.getMetaTileEntity();
        if (mte instanceof MTEFilteredCacheNode cacheNode) {
            return (int) Math.min(cacheNode.getEffectiveHubTransferRate(), Integer.MAX_VALUE);
        }
        return TRANSFER_RATE;
    }

    private void autoOutputSteam() {
        if (mSteamStored <= 0 || mStoredFluidType == null) return;
        long capacity = getTotalCapacity();
        for (MTESteamHubOutputHatch hatch : mSteamOutputHatches) {
            if (mSteamStored <= 0) break;
            if (hatch.mOverflowOutput && mSteamStored < (long) (capacity * 0.9)) continue;
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

        if (held != null && (GTSRItemList.HubSingularityChip.isStackEqual(held, true, true)
            || GTSRItemList.ReinforcedHubSingularityChip.isStackEqual(held, true, true))) {
            if (aBaseMetaTileEntity.isServerSide()) {
                sendBindingDebug(aPlayer);
            }
            return true;
        }

        if (held == null) {
            return super.onRightclick(aBaseMetaTileEntity, aPlayer, side, aX, aY, aZ);
        }

        String type = null;
        boolean isReinforced = false;
        if (GTSRItemList.SteamCacheNode.isStackEqual(held, false, true)) {
            type = "steam";
        } else if (GTSRItemList.ReinforcedSteamCacheNode.isStackEqual(held, false, true)) {
            type = "reinforced_steam";
            isReinforced = true;
        } else if (GTSRItemList.OverpressureSteamCacheNode.isStackEqual(held, false, true)) {
            type = "overpressure_steam";
        }

        if (type == null) {
            return super.onRightclick(aBaseMetaTileEntity, aPlayer, side, aX, aY, aZ);
        }

        if (!aBaseMetaTileEntity.isServerSide()) return true;

        if ("overpressure_steam".equals(type) && !hasReinforcedChipInstalled()) {
            GTUtility.sendChatToPlayer(
                aPlayer,
                StatCollector.translateToLocal("gtsr.binding.overpressure_no_reinforced_chip"));
            return true;
        }

        if (!hasChipInstalled()) {
            GTUtility.sendChatToPlayer(aPlayer, StatCollector.translateToLocal("gtsr.binding.no_chip"));
            return true;
        }

        if (!held.hasTagCompound() || !held.getTagCompound()
            .hasKey("gtsr.singularity_consumed")) {
            // Singularity cost depends on node type: steam=0, reinforced_steam=1, overpressure_steam=8
            int singularityCost = 0;
            if ("reinforced_steam".equals(type)) {
                singularityCost = 1;
            } else if ("overpressure_steam".equals(type)) {
                singularityCost = 8;
            }

            if (singularityCost > 0) {
                int found = 0;
                for (ItemStack invStack : aPlayer.inventory.mainInventory) {
                    if (invStack != null && GTSRItemList.SteamEntangledSingularity.isStackEqual(invStack, true, true)) {
                        found += invStack.stackSize;
                    }
                }
                if (found < singularityCost) {
                    GTUtility.sendChatToPlayer(
                        aPlayer,
                        StatCollector.translateToLocal("gtsr.binding.no_singularity") + " (" + singularityCost + ")");
                    return true;
                }
                int remaining = singularityCost;
                for (int i = 0; i < aPlayer.inventory.mainInventory.length && remaining > 0; i++) {
                    ItemStack invStack = aPlayer.inventory.mainInventory[i];
                    if (invStack != null && GTSRItemList.SteamEntangledSingularity.isStackEqual(invStack, true, true)) {
                        int toConsume = Math.min(remaining, invStack.stackSize);
                        invStack.stackSize -= toConsume;
                        remaining -= toConsume;
                        if (invStack.stackSize <= 0) {
                            aPlayer.inventory.mainInventory[i] = null;
                        }
                    }
                }
                aPlayer.inventoryContainer.detectAndSendChanges();
            }
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
        hubTag.setString("type", type);
        hubTag.setBoolean("output", false);
        hubTag.setBoolean("reinforced", isReinforced);

        held.getTagCompound()
            .setTag("gtsr.hubPos", hubTag);

        GTUtility.sendChatToPlayer(
            aPlayer,
            StatCollector.translateToLocal("gtsr.binding.bound_output") + nodeName
                + StatCollector.translateToLocal("gtsr.binding.mode_output"));
        return true;
    }

    private boolean hasChipInstalled() {
        ItemStack stack = getControllerSlot();
        return stack != null && (GTSRItemList.HubSingularityChip.isStackEqual(stack, true, true)
            || GTSRItemList.ReinforcedHubSingularityChip.isStackEqual(stack, true, true));
    }

    private boolean hasReinforcedChipInstalled() {
        if (mSetTier < 3) return false;
        ItemStack stack = getControllerSlot();
        return stack != null && GTSRItemList.ReinforcedHubSingularityChip.isStackEqual(stack, true, true);
    }

    private BoundCacheNode findBoundNode(int x, int y, int z, int dim) {
        for (BoundCacheNode node : mBoundNodes) {
            if (node.x == x && node.y == y && node.z == z && node.dimensionId == dim) {
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
            mBoundNodes.add(new BoundCacheNode(x, y, z, dim, false, isOutputMode));
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
        return "steam".equals(type) || "reinforced_steam".equals(type) || "overpressure_steam".equals(type);
    }

    private void sendBindingDebug(EntityPlayer aPlayer) {
        GTUtility.sendChatToPlayer(aPlayer, StatCollector.translateToLocal("gtsr.binding.debug_title"));
        if (mBoundNodes.isEmpty()) {
            GTUtility.sendChatToPlayer(aPlayer, StatCollector.translateToLocal("gtsr.binding.debug_no_bindings"));
            return;
        }
        if (!hasChipInstalled()) {
            GTUtility.sendChatToPlayer(aPlayer, StatCollector.translateToLocal("gtsr.binding.debug_no_chip"));
        }
        for (BoundCacheNode node : mBoundNodes) {
            String mode = node.isOutputMode ? StatCollector.translateToLocal("gtsr.binding.debug_output")
                : StatCollector.translateToLocal("gtsr.binding.debug_input");
            String posInfo = StatCollector.translateToLocal("gtsr.binding.debug_node") + "("
                + node.x
                + ", "
                + node.y
                + ", "
                + node.z
                + ") DIM:"
                + node.dimensionId
                + " "
                + StatCollector.translateToLocal("gtsr.binding.debug_mode")
                + mode;
            GTUtility.sendChatToPlayer(aPlayer, posInfo);
        }
    }

    private void transferWithBoundNodes() {
        if (!hasChipInstalled()) {
            return;
        }

        ArrayList<BoundCacheNode> invalidNodes = new ArrayList<>();

        for (BoundCacheNode node : mBoundNodes) {
            World world = DimensionManager.getWorld(node.dimensionId);
            if (world == null) continue;

            TileEntity te = world.getTileEntity(node.x, node.y, node.z);
            if (!(te instanceof IGregTechTileEntity)) {
                invalidNodes.add(node);
                continue;
            }
            IGregTechTileEntity gte = (IGregTechTileEntity) te;

            if (node.isOutputMode) {
                int nodeRate = getNodeTransferRate(gte);
                FluidStack toSend = extractSteam(nodeRate, false);
                if (toSend != null && toSend.amount > 0) {
                    int filled = gte.fill(ForgeDirection.UNKNOWN, toSend, true);
                    if (filled > 0) extractSteam(filled, true);
                }
            } else {
                int nodeRate = getNodeTransferRate(gte);
                FluidStack drained = gte.drain(ForgeDirection.UNKNOWN, nodeRate, false);
                if (drained != null && drained.amount > 0) {
                    int received = receiveSteam(drained, true);
                    if (received > 0) gte.drain(ForgeDirection.UNKNOWN, received, true);
                }
            }
        }

        mBoundNodes.removeAll(invalidNodes);
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setLong("mSteamStored", mSteamStored);
        aNBT.setInteger("mPressureUnitCount", mPressureUnitCount);
        aNBT.setInteger("mReinforcedUnitCount", mReinforcedUnitCount);
        aNBT.setInteger("mOverpressureUnitCount", mOverpressureUnitCount);
        aNBT.setInteger("mSetTier", mSetTier);
        aNBT.setInteger("mCasingTier", mCasingTier);
        aNBT.setInteger("mPipeTier", mPipeTier);
        aNBT.setInteger("mGearTier", mGearTier);
        aNBT.setInteger("mFrameTier", mFrameTier);
        aNBT.setLong("mTickCounter", mTickCounter);
        aNBT.setBoolean("mOverflowInput", mOverflowInput);
        if (mStoredFluidType != null) {
            NBTTagCompound fluidTag = new NBTTagCompound();
            mStoredFluidType.writeToNBT(fluidTag);
            aNBT.setTag("mStoredFluidType", fluidTag);
        }
        if (!mBoundNodes.isEmpty()) {
            NBTTagList boundList = new NBTTagList();
            for (BoundCacheNode node : mBoundNodes) {
                NBTTagCompound nodeTag = new NBTTagCompound();
                nodeTag.setInteger("x", node.x);
                nodeTag.setInteger("y", node.y);
                nodeTag.setInteger("z", node.z);
                nodeTag.setInteger("dim", node.dimensionId);
                nodeTag.setBoolean("reinforced", node.isReinforced);
                nodeTag.setBoolean("outputMode", node.isOutputMode);
                boundList.appendTag(nodeTag);
            }
            aNBT.setTag("mBoundNodes", boundList);
        }
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mSteamStored = aNBT.getLong("mSteamStored");
        mPressureUnitCount = aNBT.getInteger("mPressureUnitCount");
        mReinforcedUnitCount = aNBT.getInteger("mReinforcedUnitCount");
        mOverpressureUnitCount = aNBT.getInteger("mOverpressureUnitCount");
        mSetTier = aNBT.getInteger("mSetTier");
        mCasingTier = aNBT.getInteger("mCasingTier");
        mPipeTier = aNBT.getInteger("mPipeTier");
        mGearTier = aNBT.getInteger("mGearTier");
        mFrameTier = aNBT.getInteger("mFrameTier");
        mTickCounter = aNBT.getLong("mTickCounter");
        mOverflowInput = aNBT.getBoolean("mOverflowInput");
        if (aNBT.hasKey("mStoredFluidType")) {
            mStoredFluidType = FluidStack.loadFluidStackFromNBT(aNBT.getCompoundTag("mStoredFluidType"));
        }
        mBoundNodes.clear();
        if (aNBT.hasKey("mBoundNodes")) {
            NBTTagList boundList = aNBT.getTagList("mBoundNodes", 10);
            for (int i = 0; i < boundList.tagCount(); i++) {
                NBTTagCompound nodeTag = boundList.getCompoundTagAt(i);
                int x = nodeTag.getInteger("x");
                int y = nodeTag.getInteger("y");
                int z = nodeTag.getInteger("z");
                int dim = nodeTag.getInteger("dim");
                boolean reinforced = nodeTag.getBoolean("reinforced");
                boolean outputMode = nodeTag.getBoolean("outputMode");
                mBoundNodes.add(new BoundCacheNode(x, y, z, dim, reinforced, outputMode));
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
        if (mSetTier >= 3) {
            casingTextureId = GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings4, 0);
        } else if (mSetTier == 2) {
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
        return new MTESteamHubArrayGui(this);
    }

    @Deprecated
    @Override
    protected void drawTexts(DynamicPositionedColumn screenElements, SlotWidget inventorySlot) {
        super.drawTexts(screenElements, inventorySlot);
        screenElements.widget(new TextWidget().setStringSupplier(() -> {
            String tierText;
            if (mSetTier >= 3) {
                tierText = StatCollector.translateToLocal("gtsr.gui.tier.tungstensteel");
            } else if (mSetTier == 2) {
                tierText = StatCollector.translateToLocal("gtsr.gui.tier.steel");
            } else {
                tierText = StatCollector.translateToLocal("gtsr.gui.tier.bronze");
            }
            return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.tier")
                + EnumChatFormatting.GOLD
                + tierText
                + EnumChatFormatting.RESET;
        }))
            .widget(new TextWidget().setStringSupplier(() -> {
                ItemStack chip = getControllerSlot();
                String chipText;
                if (chip != null && GTSRItemList.ReinforcedHubSingularityChip.isStackEqual(chip, true, true)) {
                    if (mSetTier >= 3) {
                        chipText = EnumChatFormatting.GREEN
                            + StatCollector.translateToLocal("gtsr.gui.chip.reinforced_installed");
                    } else {
                        chipText = EnumChatFormatting.RED
                            + StatCollector.translateToLocal("gtsr.gui.chip.need_higher_tier");
                    }
                } else if (chip != null && GTSRItemList.HubSingularityChip.isStackEqual(chip, true, true)) {
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
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.steam_hub.storage_units")
                        + " "
                        + EnumChatFormatting.GOLD
                        + (mPressureUnitCount + mReinforcedUnitCount + mOverpressureUnitCount)
                        + "/"
                        + (25 * mStackCount)
                        + EnumChatFormatting.RESET))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.steam_hub.steam_buffer")
                        + " "
                        + EnumChatFormatting.LIGHT_PURPLE
                        + NumberFormatUtil.formatNumber(mSteamStored)
                        + " L"
                        + EnumChatFormatting.RESET))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.YELLOW
                        + StatCollector.translateToLocal("gtsr.gui.steam_hub.total_capacity")
                        + " "
                        + EnumChatFormatting.LIGHT_PURPLE
                        + NumberFormatUtil.formatNumber(getTotalCapacity())
                        + " L"
                        + EnumChatFormatting.RESET))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mSetTier, val -> mSetTier = val))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mMaxProgresstime, val -> mMaxProgresstime = val))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mStackCount, val -> mStackCount = val))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mPressureUnitCount, val -> mPressureUnitCount = val))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mReinforcedUnitCount, val -> mReinforcedUnitCount = val))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mOverpressureUnitCount, val -> mOverpressureUnitCount = val))
            .widget(new FakeSyncWidget.LongSyncer(() -> mSteamStored, val -> mSteamStored = val));
    }

    @Override
    public String[] getInfoData() {
        ArrayList<String> info = new ArrayList<>();
        info.add(
            EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.steam_hub.type")
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
        int totalUnits = mPressureUnitCount + mReinforcedUnitCount + mOverpressureUnitCount;
        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.steam_hub.storage_units")
                + " "
                + EnumChatFormatting.GOLD
                + totalUnits
                + "/"
                + (25 * mStackCount)
                + EnumChatFormatting.RESET);
        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.steam_hub.steam_buffer")
                + " "
                + EnumChatFormatting.LIGHT_PURPLE
                + NumberFormatUtil.formatNumber(mSteamStored)
                + " L"
                + EnumChatFormatting.RESET);
        return info.toArray(new String[0]);
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal("gtsr.tooltip.steam_hub.type"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.steam_hub.desc"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.steam_hub.desc2"))
            .addInfo(
                EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.tooltip.shared.screwdriver_overflow"))
            .addInfo(
                EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.tooltip.shared.overflow_input_desc"))
            .beginStructureBlock(9, 5, 9, false)
            .addController(StatCollector.translateToLocal("gtsr.tooltip.steam_hub.ctrl"))
            .addOtherStructurePart(
                StatCollector.translateToLocal("gtsr.tooltip.steam_hub.hub_input"),
                StatCollector.translateToLocal("gtsr.tooltip.shared.any_casing"),
                1)
            .addOtherStructurePart(
                StatCollector.translateToLocal("gtsr.tooltip.steam_hub.hub_output"),
                StatCollector.translateToLocal("gtsr.tooltip.shared.any_casing"),
                1)
            .addOtherStructurePart(
                StatCollector.translateToLocal("gtsr.tooltip.steam_hub.storage"),
                StatCollector.translateToLocal("gtsr.tooltip.steam_hub.storage"),
                2)
            .addStructureInfo("")
            .addStructureInfo(
                EnumChatFormatting.BLUE + "Bronze"
                    + EnumChatFormatting.DARK_PURPLE
                    + "/"
                    + EnumChatFormatting.BLUE
                    + "Steel"
                    + EnumChatFormatting.DARK_PURPLE
                    + "/"
                    + EnumChatFormatting.BLUE
                    + "TungstenSteel "
                    + EnumChatFormatting.DARK_PURPLE
                    + "Tier")
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.casing"), 70, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.pipe"), 7, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.gear_box"), 4, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.frame"), 24, false)
            .addStructureHint("gtsr.tooltip.shared.no_maintenance")
            .addStructureHint("gtsr.tooltip.steam_hub.hint_tier1")
            .addStructureHint("gtsr.tooltip.steam_hub.hint_tier2")
            .addStructureHint("gtsr.tooltip.steam_hub.hint_tier3")
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
