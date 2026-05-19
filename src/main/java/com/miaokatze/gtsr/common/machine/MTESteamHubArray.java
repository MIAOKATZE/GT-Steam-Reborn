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
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
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
import com.miaokatze.gtsr.common.machine.base.MTESteamHubInputHatch;
import com.miaokatze.gtsr.common.machine.base.MTESteamHubOutputHatch;

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

public class MTESteamHubArray extends MTEEnhancedMultiBlockBase<MTESteamHubArray>
    implements IConstructable, ISurvivalConstructable, com.miaokatze.gtsr.common.machine.base.IHubArray {

    private static final String STRUCTURE_PIECE_BASE = "base";
    private static final String STRUCTURE_PIECE_STACK = "stack";
    private static final String STRUCTURE_PIECE_STACK_HINT = "stackHint";
    private static final String STRUCTURE_PIECE_TOP_HINT = "topHint";
    private static final int MIN_TOTAL_HEIGHT = 2;
    private static final int MAX_TOTAL_HEIGHT = 13;
    private static final int AUTO_OUTPUT_RATE = 2_000_000;
    private static final int TRANSFER_RATE = 1_000_000;

    private static final int CASING_INDEX = GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings1, 10);

    private static Textures.BlockIcons.CustomIcon CONTROLLER_OVERLAY;

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
    private int mHeight = 0;
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
        aNBT.setInteger("mHeight", mHeight);
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
        mHeight = aNBT.getInteger("mHeight");
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
    public String[] getInfoData() {
        long totalCapacity = getTotalCapacity();
        double fillRatio = totalCapacity > 0 ? (double) mSteamStored / totalCapacity * 100 : 0;

        boolean hasChip = hasChipInstalled();
        int outputCount = 0;
        int inputCount = 0;
        if (hasChip) {
            for (BoundCacheNode node : mBoundNodes) {
                if (node.isOutputMode) outputCount++;
                else inputCount++;
            }
        }

        ArrayList<String> info = new ArrayList<>();
        info.add(EnumChatFormatting.BLUE + "Steam Hub Array");
        info.add(
            EnumChatFormatting.GRAY + "Status: "
                + (mMachine ? EnumChatFormatting.GREEN + "Running" : EnumChatFormatting.RED + "Incomplete"));
        info.add(
            EnumChatFormatting.GRAY + "Steam Type: "
                + EnumChatFormatting.AQUA
                + (mStoredFluidType != null ? mStoredFluidType.getLocalizedName() : "None"));
        info.add(
            EnumChatFormatting.GRAY + "Steam Stored: "
                + EnumChatFormatting.YELLOW
                + GTUtility.formatNumbers(mSteamStored)
                + " / "
                + GTUtility.formatNumbers(totalCapacity)
                + " L");
        info.add(EnumChatFormatting.GRAY + "Fill: " + EnumChatFormatting.GREEN + String.format("%.1f%%", fillRatio));
        info.add(EnumChatFormatting.GRAY + "Pressure Units: " + EnumChatFormatting.WHITE + mPressureUnitCount);
        info.add(EnumChatFormatting.GRAY + "Reinforced Units: " + EnumChatFormatting.WHITE + mReinforcedUnitCount);

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
        tt.addMachineType(StatCollector.translateToLocal("gtsr.recipe.steam_hub_array"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.steam_hub_array.info"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.steam_hub_array.structure"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.steam_hub_array.capacity"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.steam_hub_array.output"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.steam_hub_array.no_maintenance"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.steam_hub_array.hatch_note"))
            .beginVariableStructureBlock(5, 5, 2, 13, 5, 5, false)
            .addController(StatCollector.translateToLocal("gtsr.tooltip.steam_hub_array.ctrl_pos"))
            .addCasingInfoMin(StatCollector.translateToLocal("gtsr.tooltip.steam_hub_array.casing"), 1, false)
            .addOtherStructurePart(
                StatCollector.translateToLocal("gtsr.tooltip.steam_hub_array.input_hatch"),
                StatCollector.translateToLocal("gtsr.tooltip.steam_hub_array.base_layer"),
                1)
            .addOtherStructurePart(
                StatCollector.translateToLocal("gtsr.tooltip.steam_hub_array.output_hatch"),
                StatCollector.translateToLocal("gtsr.tooltip.steam_hub_array.base_layer"),
                1)
            .addOtherStructurePart(
                StatCollector.translateToLocal("gtsr.tooltip.steam_hub_array.storage_unit"),
                StatCollector.translateToLocal("gtsr.tooltip.steam_hub_array.stack_layers"),
                2)
            .addSubChannelUsage(GTStructureChannels.STRUCTURE_HEIGHT)
            .toolTipFinisher();
        return tt;
    }
}
