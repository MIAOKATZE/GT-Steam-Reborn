package com.miaokatze.gtsr.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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

import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.machine.base.MTEHubStorageUnit;
import com.miaokatze.gtsr.common.machine.base.MTEReinforcedHubStorageUnit;
import com.miaokatze.gtsr.common.machine.base.MTEWaterHubInputHatch;
import com.miaokatze.gtsr.common.machine.base.MTEWaterHubOutputHatch;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
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

public class MTEWaterHubArray extends MTEEnhancedMultiBlockBase<MTEWaterHubArray>
    implements IConstructable, ISurvivalConstructable, com.miaokatze.gtsr.common.machine.base.IHubArray {

    private static final String STRUCTURE_PIECE_BASE = "base";
    private static final String STRUCTURE_PIECE_STACK = "stack";
    private static final String STRUCTURE_PIECE_STACK_HINT = "stackHint";
    private static final String STRUCTURE_PIECE_TOP_HINT = "topHint";
    private static final int MIN_TOTAL_HEIGHT = 2;
    private static final int MAX_TOTAL_HEIGHT = 16;
    private static final int AUTO_OUTPUT_RATE = 128_000;
    private static final int HUB_UNIT_CAPACITY = 64_000;
    private static final int REINFORCED_HUB_UNIT_CAPACITY = 256_000;
    private static final int BOUND_TRANSFER_RATE = 1_000_000;
    private static final int BOUND_TRANSFER_INTERVAL = 20;

    private static final int CASING_INDEX = GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings1, 10);

    private static Textures.BlockIcons.CustomIcon CONTROLLER_OVERLAY;

    private static final IStructureDefinition<MTEWaterHubArray> STRUCTURE_DEFINITION;

    static {
        STRUCTURE_DEFINITION = StructureDefinition.<MTEWaterHubArray>builder()
            .addShape(STRUCTURE_PIECE_BASE, transpose(new String[][] { { "H~H", "HHH", "HHH" } }))
            .addShape(STRUCTURE_PIECE_STACK, transpose(new String[][] { { "SSS", "SSS", "SSS" } }))
            .addShape(STRUCTURE_PIECE_STACK_HINT, transpose(new String[][] { { "TTT", "TTT", "TTT" } }))
            .addShape(STRUCTURE_PIECE_TOP_HINT, transpose(new String[][] { { "TTT", "TTT", "TTT" } }))
            .addElement(
                'H',
                buildHatchAdder(MTEWaterHubArray.class)
                    .atLeast(WaterHubHatchElement.WaterInput, WaterHubHatchElement.WaterOutput)
                    .casingIndex(CASING_INDEX)
                    .dot(1)
                    .buildAndChain(
                        onElementPass(MTEWaterHubArray::onCasingAdded, ofBlock(GregTechAPI.sBlockCasings1, 10))))
            .addElement(
                'S',
                buildHatchAdder(MTEWaterHubArray.class)
                    .atLeast(WaterHubStorageElement.HubUnit, WaterHubStorageElement.ReinforcedHubUnit)
                    .casingIndex(CASING_INDEX)
                    .dot(2)
                    .buildAndChain(
                        onElementPass(MTEWaterHubArray::onCasingAdded, ofBlock(GregTechAPI.sBlockCasings1, 10))))
            .addElement(
                'T',
                buildHatchAdder(MTEWaterHubArray.class)
                    .atLeast(WaterHubStorageElement.HubUnit, WaterHubStorageElement.ReinforcedHubUnit)
                    .casingIndex(CASING_INDEX)
                    .dot(2)
                    .buildAndChain(GregTechAPI.sBlockCasings1, 10))
            .build();
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

    private int mHubUnitCount = 0;
    private int mReinforcedHubUnitCount = 0;
    private int mCasingAmount = 0;
    private int mHeight = 0;
    private long mWaterStored = 0;
    private String mStoredFluidType = null;

    public MTEWaterHubArray(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTEWaterHubArray(String aName) {
        super(aName);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister aBlockIconRegister) {
        CONTROLLER_OVERLAY = new Textures.BlockIcons.CustomIcon("gtsr:MTEWaterHubArray");
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
    public boolean checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack) {
        for (MTEWaterHubInputHatch hatch : mWaterInputHatches) {
            hatch.mController = null;
        }
        for (MTEWaterHubOutputHatch hatch : mWaterOutputHatches) {
            hatch.mController = null;
        }

        mHubUnitCount = 0;
        mReinforcedHubUnitCount = 0;
        mCasingAmount = 0;
        mHeight = 0;
        mWaterInputHatches.clear();
        mWaterOutputHatches.clear();

        if (!checkPiece(STRUCTURE_PIECE_BASE, 1, 0, 0)) return false;

        while (mHeight < MAX_TOTAL_HEIGHT - 1) {
            if (!checkPiece(STRUCTURE_PIECE_STACK, 1, mHeight + 1, 0)) break;
            mHeight++;
        }

        return mHeight >= MIN_TOTAL_HEIGHT - 1 && (mHubUnitCount + mReinforcedHubUnitCount) > 0;
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

    private int getTotalHeightFromItemStack(ItemStack stackSize) {
        return Math.max(
            MIN_TOTAL_HEIGHT,
            GTStructureChannels.STRUCTURE_HEIGHT.getValueClamped(stackSize, MIN_TOTAL_HEIGHT, MAX_TOTAL_HEIGHT));
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece(STRUCTURE_PIECE_BASE, stackSize, hintsOnly, 1, 0, 0);
        int tTotalHeight = getTotalHeightFromItemStack(stackSize);
        for (int i = 1; i < tTotalHeight - 1; i++) {
            buildPiece(STRUCTURE_PIECE_STACK_HINT, stackSize, hintsOnly, 1, i, 0);
        }
        buildPiece(STRUCTURE_PIECE_TOP_HINT, stackSize, hintsOnly, 1, tTotalHeight - 1, 0);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        int built = survivalBuildPiece(STRUCTURE_PIECE_BASE, stackSize, 1, 0, 0, elementBudget, env, false, true);
        if (built >= 0) return built;
        int tTotalHeight = getTotalHeightFromItemStack(stackSize);
        for (int i = 1; i < tTotalHeight - 1; i++) {
            built = survivalBuildPiece(STRUCTURE_PIECE_STACK_HINT, stackSize, 1, i, 0, elementBudget, env, false, true);
            if (built >= 0) return built;
        }
        return survivalBuildPiece(
            STRUCTURE_PIECE_TOP_HINT,
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
    public String[] getStructureDescription(ItemStack stackSize) {
        return new String[] { EnumChatFormatting.AQUA + "Structure:",
            "1. 3x3 base layer with Bronze Plated Bricks and Hatches",
            "2. Stack Storage Units above the base (1-15 layers)",
            "3. At least 1 Input Hatch and 1 Output Hatch required",
            "4. Height: Level 1 = 2 layers, Level 15 = 16 layers" };
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
        for (MTEWaterHubOutputHatch hatch : mWaterOutputHatches) {
            if (mWaterStored <= 0) break;
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
                FluidStack drained = gtTile.drain(ForgeDirection.UNKNOWN, BOUND_TRANSFER_RATE, false);
                if (drained != null && isWaterFluid(drained)) {
                    int accepted = receiveWater(drained, true);
                    if (accepted > 0) {
                        gtTile.drain(ForgeDirection.UNKNOWN, accepted, true);
                    }
                }
            } else {
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
        aNBT.setInteger("mHeight", mHeight);
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
        mHeight = aNBT.getInteger("mHeight");
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
    public String[] getInfoData() {
        long totalCapacity = getTotalCapacity();
        double fillRatio = totalCapacity > 0 ? (double) mWaterStored / totalCapacity * 100 : 0;

        boolean hasChip = hasHubSingularityChip();
        int outputCount = 0;
        int inputCount = 0;
        if (hasChip) {
            for (BoundCacheNode node : mBoundNodes) {
                if (node.isOutputMode) outputCount++;
                else inputCount++;
            }
        }

        ArrayList<String> info = new ArrayList<>();
        info.add(EnumChatFormatting.BLUE + "Water Hub Array");
        info.add(
            EnumChatFormatting.GRAY + "Status: "
                + (mMachine ? EnumChatFormatting.GREEN + "Running" : EnumChatFormatting.RED + "Incomplete"));
        info.add(
            EnumChatFormatting.GRAY + "Fluid Type: "
                + EnumChatFormatting.AQUA
                + (mStoredFluidType != null ? mStoredFluidType : "None"));
        info.add(
            EnumChatFormatting.GRAY + "Water Stored: "
                + EnumChatFormatting.YELLOW
                + GTUtility.formatNumbers(mWaterStored)
                + " / "
                + GTUtility.formatNumbers(totalCapacity)
                + " L");
        info.add(EnumChatFormatting.GRAY + "Fill: " + EnumChatFormatting.GREEN + String.format("%.1f%%", fillRatio));
        info.add(EnumChatFormatting.GRAY + "Hub Units: " + EnumChatFormatting.WHITE + mHubUnitCount);
        info.add(
            EnumChatFormatting.GRAY + "Reinforced Hub Units: " + EnumChatFormatting.WHITE + mReinforcedHubUnitCount);

        if (hasChip) {
            info.add(
                EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.binding.debug_input")
                    + ": "
                    + EnumChatFormatting.WHITE
                    + inputCount);
            info.add(
                EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.binding.debug_output")
                    + ": "
                    + EnumChatFormatting.WHITE
                    + outputCount);
            info.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.gui.binding_hint"));
        }

        return info.toArray(new String[0]);
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal("gtsr.recipe.water_hub_array"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.water_hub_array.info"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.water_hub_array.structure"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.water_hub_array.capacity"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.water_hub_array.output"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.water_hub_array.no_maintenance"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.water_hub_array.hatch_note"))
            .beginVariableStructureBlock(3, 3, 2, 16, 3, 3, false)
            .addController(StatCollector.translateToLocal("gtsr.tooltip.water_hub_array.ctrl"))
            .addCasingInfoMin(StatCollector.translateToLocal("gtsr.tooltip.water_hub_array.casing"), 1, false)
            .addOtherStructurePart(
                StatCollector.translateToLocal("gtsr.tooltip.water_hub_array.input_hatch"),
                StatCollector.translateToLocal("gtsr.tooltip.water_hub_array.base_layer"),
                1)
            .addOtherStructurePart(
                StatCollector.translateToLocal("gtsr.tooltip.water_hub_array.output_hatch"),
                StatCollector.translateToLocal("gtsr.tooltip.water_hub_array.base_layer"),
                1)
            .addOtherStructurePart(
                StatCollector.translateToLocal("gtsr.tooltip.water_hub_array.storage_unit"),
                StatCollector.translateToLocal("gtsr.tooltip.water_hub_array.stack_layers"),
                2)
            .addSubChannelUsage(GTStructureChannels.STRUCTURE_HEIGHT)
            .toolTipFinisher();
        return tt;
    }
}
