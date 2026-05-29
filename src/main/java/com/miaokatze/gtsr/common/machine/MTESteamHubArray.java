package com.miaokatze.gtsr.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
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
import com.miaokatze.gtsr.common.machine.base.MTEHubStorageUnit;
import com.miaokatze.gtsr.common.machine.base.MTEReinforcedHubStorageUnit;
import com.miaokatze.gtsr.common.machine.base.MTESteamHubInputHatch;
import com.miaokatze.gtsr.common.machine.base.MTESteamHubOutputHatch;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
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

public class MTESteamHubArray extends MTEEnhancedMultiBlockBase<MTESteamHubArray>
    implements IConstructable, ISurvivalConstructable, com.miaokatze.gtsr.common.machine.base.IHubArray {

    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static final int HORIZONTAL_OFF_SET = 4;
    private static final int VERTICAL_OFF_SET = 1;
    private static final int DEPTH_OFF_SET = 1;
    private static final int AUTO_OUTPUT_RATE = 2_000_000;
    private static final int TRANSFER_RATE = 1_000_000;

    private static final int CASING_INDEX = GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings1, 10);

    private static Textures.BlockIcons.CustomIcon CONTROLLER_OVERLAY;

    private static final IStructureDefinition<MTESteamHubArray> STRUCTURE_DEFINITION;

    static {
        STRUCTURE_DEFINITION = StructureDefinition.<MTESteamHubArray>builder()
            .addShape(
                STRUCTURE_PIECE_MAIN,
                transpose(
                    new String[][] {
                        { "         ", "   FDF   ", "  AAAAA  ", " FAAAAAF ", " DAAAAAD ", " FAAAAAF ", "  AAAAA  ",
                            "   FDF   ", "         " },
                        { "         ", "   F~F   ", "  ECCCE  ", " FCCCCCF ", " DCCCCCD ", " FCCCCCF ", "  ECCCE  ",
                            "   FDF   ", "         " },
                        { "   CCC   ", "  CFCFC  ", " CCCCCCC ", "CFCCCCCFC", "CCCCCCCCC", "CFCCCCCFC", " CCCCCCC ",
                            "  CFCFC  ", "   CCC   " } }))
            .addElement(
                'A',
                ofChain(
                    buildHatchAdder(MTESteamHubArray.class)
                        .atLeast(SteamHubStorageElement.PressureUnit, SteamHubStorageElement.ReinforcedUnit)
                        .casingIndex(CASING_INDEX)
                        .dot(2)
                        .buildAndChain(
                            onElementPass(MTESteamHubArray::onCasingAdded, ofBlock(GregTechAPI.sBlockCasings1, 10)))))
            .addElement(
                'C',
                ofChain(
                    buildHatchAdder(MTESteamHubArray.class)
                        .atLeast(SteamHubHatchElement.SteamInput, SteamHubHatchElement.SteamOutput)
                        .casingIndex(CASING_INDEX)
                        .dot(1)
                        .buildAndChain(
                            onElementPass(
                                MTESteamHubArray::onCasingAdded,
                                ofBlocksTiered(
                                    MTESteamHubArray::getCasingTier,
                                    ImmutableList.of(
                                        Pair.of(GregTechAPI.sBlockCasings1, 10),
                                        Pair.of(GregTechAPI.sBlockCasings2, 0)),
                                    -1,
                                    (MTESteamHubArray t, Integer tier) -> t.mSetTier = Math.max(t.mSetTier, tier),
                                    (MTESteamHubArray t) -> t.mSetTier)))))
            .addElement(
                'D',
                onElementPass(
                    MTESteamHubArray::onCasingAdded,
                    ofBlocksTiered(
                        MTESteamHubArray::getPipeTier,
                        ImmutableList
                            .of(Pair.of(GregTechAPI.sBlockCasings2, 12), Pair.of(GregTechAPI.sBlockCasings2, 13)),
                        -1,
                        (MTESteamHubArray t, Integer tier) -> t.mSetTier = Math.max(t.mSetTier, tier),
                        (MTESteamHubArray t) -> t.mSetTier)))
            .addElement(
                'E',
                onElementPass(
                    MTESteamHubArray::onCasingAdded,
                    ofBlocksTiered(
                        MTESteamHubArray::getGearTier,
                        ImmutableList
                            .of(Pair.of(GregTechAPI.sBlockCasings2, 2), Pair.of(GregTechAPI.sBlockCasings2, 3)),
                        -1,
                        (MTESteamHubArray t, Integer tier) -> t.mSetTier = Math.max(t.mSetTier, tier),
                        (MTESteamHubArray t) -> t.mSetTier)))
            .addElement(
                'F',
                onElementPass(
                    MTESteamHubArray::onCasingAdded,
                    ofBlocksTiered(
                        MTESteamHubArray::getFrameTier,
                        ImmutableList.of(
                            Pair.of(GregTechAPI.sBlockFrames, Materials.Bronze.mMetaItemSubID),
                            Pair.of(GregTechAPI.sBlockFrames, Materials.Steel.mMetaItemSubID)),
                        -1,
                        (MTESteamHubArray t, Integer tier) -> t.mSetTier = Math.max(t.mSetTier, tier),
                        (MTESteamHubArray t) -> t.mSetTier)))
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
        ReinforcedUnit(MTESteamHubArray::addReinforcedUnitToMachineList, MTEReinforcedHubStorageUnit.class);

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

    private int mPressureUnitCount = 0;
    private int mReinforcedUnitCount = 0;
    private int mCasingAmount = 0;
    private int mSetTier = -1;
    private long mSteamStored = 0;
    private FluidStack mStoredFluidType = null;
    private long mTickCounter = 0;

    public MTESteamHubArray(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTESteamHubArray(String aName) {
        super(aName);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister aBlockIconRegister) {
        CONTROLLER_OVERLAY = new Textures.BlockIcons.CustomIcon("gtsr:MTESteamHubArray");
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
        mSetTier = -1;
        mSteamInputHatches.clear();
        mSteamOutputHatches.clear();

        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET)) return false;

        if (mSetTier <= 0) return false;

        return (mPressureUnitCount + mReinforcedUnitCount) > 0;
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
    public String[] getStructureDescription(ItemStack stackSize) {
        return new String[] { EnumChatFormatting.AQUA + "Structure:",
            "1. 9x9x3 fixed structure with Bronze/Steel dual-tier casing",
            "2. Layer 0: Storage unit slots (A) surrounded by Frames (F) and Pipes (D)",
            "3. Layer 1: Controller (~) with Gears (E), Pipes (D), Casing (C), Frames (F)",
            "4. Layer 2: Solid top cover of Casing (C) and Frames (F)",
            "5. At least 1 Input Hatch and 1 Output Hatch required" };
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
        return (long) mPressureUnitCount * 16_000_000 + (long) mReinforcedUnitCount * 64_000_000;
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

        String type = null;
        boolean isReinforced = false;
        if (GTSRItemList.SteamCacheNode.isStackEqual(held, false, true)) {
            type = "steam";
        } else if (GTSRItemList.ReinforcedSteamCacheNode.isStackEqual(held, false, true)) {
            type = "reinforced_steam";
            isReinforced = true;
        }

        if (type == null) {
            return super.onRightclick(aBaseMetaTileEntity, aPlayer, side, aX, aY, aZ);
        }

        if (!aBaseMetaTileEntity.isServerSide()) return true;

        if (!hasChipInstalled()) {
            GTUtility.sendChatToPlayer(aPlayer, StatCollector.translateToLocal("gtsr.binding.no_chip"));
            return true;
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
        return stack != null && GTSRItemList.HubSingularityChip.isStackEqual(stack, true, true);
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
        return "steam".equals(type) || "reinforced_steam".equals(type);
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
                FluidStack drained = gte.drain(ForgeDirection.UNKNOWN, TRANSFER_RATE, false);
                if (drained != null && drained.amount > 0) {
                    int received = receiveSteam(drained, true);
                    if (received > 0) gte.drain(ForgeDirection.UNKNOWN, received, true);
                }
            } else {
                FluidStack toSend = extractSteam(TRANSFER_RATE, false);
                if (toSend != null && toSend.amount > 0) {
                    int filled = gte.fill(ForgeDirection.UNKNOWN, toSend, true);
                    if (filled > 0) extractSteam(filled, true);
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
        aNBT.setInteger("mSetTier", mSetTier);
        aNBT.setLong("mTickCounter", mTickCounter);
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
        mSetTier = aNBT.getInteger("mSetTier");
        mTickCounter = aNBT.getLong("mTickCounter");
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
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int colorIndex, boolean active, boolean redstoneLevel) {
        if (side == facing) {
            return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(CASING_INDEX), TextureFactory.builder()
                .addIcon(CONTROLLER_OVERLAY)
                .extFacing()
                .build() };
        }
        return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(CASING_INDEX) };
    }

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
                        + (mPressureUnitCount + mReinforcedUnitCount)
                        + "/25"
                        + EnumChatFormatting.RESET))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.steam_hub.steam_buffer")
                        + " "
                        + EnumChatFormatting.LIGHT_PURPLE
                        + GTUtility.formatNumbers(mSteamStored)
                        + " L"
                        + EnumChatFormatting.RESET))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mSetTier, val -> mSetTier = val))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mMaxProgresstime, val -> mMaxProgresstime = val))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mPressureUnitCount, val -> mPressureUnitCount = val))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mReinforcedUnitCount, val -> mReinforcedUnitCount = val))
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
        int totalUnits = mPressureUnitCount + mReinforcedUnitCount;
        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.steam_hub.storage_units")
                + " "
                + EnumChatFormatting.GOLD
                + totalUnits
                + "/25"
                + EnumChatFormatting.RESET);
        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.steam_hub.steam_buffer")
                + " "
                + EnumChatFormatting.LIGHT_PURPLE
                + GTUtility.formatNumbers(mSteamStored)
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
            .beginStructureBlock(9, 3, 9, false)
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
                    + "Steel "
                    + EnumChatFormatting.DARK_PURPLE
                    + "Tier")
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.casing"), 70, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.pipe"), 7, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.gear_box"), 4, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.frame"), 24, false)
            .addStructureHint("gtsr.tooltip.shared.no_maintenance")
            .addStructureHint("gtsr.tooltip.steam_hub.hint_tier1")
            .addStructureHint("gtsr.tooltip.steam_hub.hint_tier2")
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
