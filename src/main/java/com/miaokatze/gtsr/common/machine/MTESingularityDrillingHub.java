package com.miaokatze.gtsr.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.HatchElement.OutputBus;
import static gregtech.api.enums.HatchElement.OutputHatch;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.ArrayList;

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

import com.gtnewhorizon.structurelib.alignment.IAlignmentLimits;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.api.enums.MetaTileEntityID;
import com.miaokatze.gtsr.common.machine.base.IHubArray;
import com.miaokatze.gtsr.common.machine.base.MTEPressureSteamCoolingHatch;
import com.miaokatze.gtsr.common.machine.base.MTERemoteWorkerNode;
import com.miaokatze.gtsr.common.machine.base.MTESteamCoolingHatch;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTESteamMultiBase;

public class MTESingularityDrillingHub extends MTESteamMultiBase<MTESingularityDrillingHub>
    implements ISurvivalConstructable, IHubArray {

    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static final int HORIZONTAL_OFF_SET = 1;
    private static final int VERTICAL_OFF_SET = 1;
    private static final int DEPTH_OFF_SET = 0;

    private static final int BASE_STEAM_PER_SECOND = 12_000;
    private static final int STEAM_PER_NODE = 12_000;

    private static IStructureDefinition<MTESingularityDrillingHub> STRUCTURE_DEFINITION = null;

    private static class BoundDrillNode {

        final int x, y, z;
        final int dimensionId;
        final boolean isMiner;
        boolean isActive;

        BoundDrillNode(int x, int y, int z, int dim, boolean isMiner) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.dimensionId = dim;
            this.isMiner = isMiner;
            this.isActive = true;
        }
    }

    private final ArrayList<BoundDrillNode> mBoundNodes = new ArrayList<>();
    private int mCasingCount = 0;

    public MTESingularityDrillingHub(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTESingularityDrillingHub(String aName) {
        super(aName);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTESingularityDrillingHub(mName);
    }

    @Override
    public String getMachineType() {
        return StatCollector.translateToLocal("gtsr.recipe.singularity_drilling_hub");
    }

    protected int getCasingTextureID() {
        return GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings1, 10);
    }

    protected void updateHatchTexture() {
        int textureID = getCasingTextureID();
        for (MTEHatch h : mSteamInputFluids) h.updateTexture(textureID);
        for (MTEHatch h : mOutputBusses) h.updateTexture(textureID);
        for (MTEHatch h : mOutputHatches) h.updateTexture(textureID);
    }

    @Override
    public void onValueUpdate(byte aValue) {}

    @Override
    public byte getUpdateData() {
        return 0;
    }

    @Override
    public IStructureDefinition<MTESingularityDrillingHub> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            final int casingIndex = getCasingTextureID();

            STRUCTURE_DEFINITION = StructureDefinition.<MTESingularityDrillingHub>builder()
                .addShape(
                    STRUCTURE_PIECE_MAIN,
                    transpose(
                        new String[][] { { "CCC", "CCC", "CCC" }, { "C~C", "C C", "CCC" }, { "CCC", "CCC", "CCC" } }))
                .addElement(
                    'C',
                    ofChain(
                        buildHatchAdder(MTESingularityDrillingHub.class).adder(MTESteamMultiBase::addToMachineList)
                            .hatchClass(MTESteamCoolingHatch.class)
                            .casingIndex(casingIndex)
                            .dot(2)
                            .build(),
                        buildHatchAdder(MTESingularityDrillingHub.class).adder(MTESteamMultiBase::addToMachineList)
                            .hatchClass(MTEPressureSteamCoolingHatch.class)
                            .casingIndex(casingIndex)
                            .dot(2)
                            .build(),
                        buildHatchAdder(MTESingularityDrillingHub.class).adder(MTESteamMultiBase::addToMachineList)
                            .hatchIds(31040, MetaTileEntityID.PRESSURE_STEAM_HATCH.ID)
                            .casingIndex(casingIndex)
                            .dot(1)
                            .shouldReject(t -> !t.mSteamInputFluids.isEmpty())
                            .build(),
                        buildHatchAdder(MTESingularityDrillingHub.class).atLeast(OutputBus)
                            .casingIndex(casingIndex)
                            .dot(1)
                            .build(),
                        buildHatchAdder(MTESingularityDrillingHub.class).atLeast(OutputHatch)
                            .casingIndex(casingIndex)
                            .dot(1)
                            .buildAndChain(
                                onElementPass(
                                    MTESingularityDrillingHub::onCasingAdded,
                                    ofBlock(GregTechAPI.sBlockCasings1, 10)))))
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    private void onCasingAdded() {
        mCasingCount++;
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
    public boolean checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack) {
        mCasingCount = 0;

        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET)) {
            return false;
        }

        if (this.mSteamInputFluids.isEmpty()) return false;
        if (this.mOutputBusses.isEmpty() && this.mSteamOutputs.isEmpty()) return false;
        if (this.mOutputHatches.isEmpty()) return false;

        updateHatchTexture();
        return true;
    }

    @Override
    public CheckRecipeResult checkProcessing() {
        int activeNodeCount = 0;
        for (BoundDrillNode node : mBoundNodes) {
            if (node.isActive) activeNodeCount++;
        }

        int totalCost = BASE_STEAM_PER_SECOND + activeNodeCount * STEAM_PER_NODE;

        FluidStack steamStack = Materials.Steam.getGas(totalCost);
        if (!depleteInput(steamStack)) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        for (BoundDrillNode node : mBoundNodes) {
            if (!node.isActive) continue;

            World world = DimensionManager.getWorld(node.dimensionId);
            if (world == null || !world.blockExists(node.x, node.y, node.z)) {
                node.isActive = false;
                continue;
            }

            TileEntity te = world.getTileEntity(node.x, node.y, node.z);
            if (!(te instanceof IGregTechTileEntity gte)) {
                node.isActive = false;
                continue;
            }

            if (gte.getMetaTileEntity() instanceof MTERemoteWorkerNode workerNode) {
                workerNode.doWork(gte);
            }
        }

        mMaxProgresstime = 20;
        mEfficiencyIncrease = 10000;

        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (!aBaseMetaTileEntity.isServerSide() || !mMachine) return;

        if (aTick % 20 == 0) {
            transferWithBoundNodes();
        }
    }

    private void transferWithBoundNodes() {
        ArrayList<BoundDrillNode> invalidNodes = new ArrayList<>();

        for (BoundDrillNode node : mBoundNodes) {
            World world = DimensionManager.getWorld(node.dimensionId);
            if (world == null) continue;

            TileEntity te = world.getTileEntity(node.x, node.y, node.z);
            if (!(te instanceof IGregTechTileEntity)) {
                invalidNodes.add(node);
                continue;
            }

            IGregTechTileEntity gte = (IGregTechTileEntity) te;
            if (!(gte.getMetaTileEntity() instanceof MTERemoteWorkerNode)) {
                invalidNodes.add(node);
            }
        }

        mBoundNodes.removeAll(invalidNodes);
    }

    private boolean hasChipInstalled() {
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

        String type = null;
        boolean isMiner = false;
        if (GTSRItemList.SingularityMinerNode.isStackEqual(held, false, true)) {
            type = "miner";
            isMiner = true;
        } else if (GTSRItemList.SingularityDrillingNode.isStackEqual(held, false, true)) {
            type = "driller";
        }

        if (type == null) {
            return super.onRightclick(aBaseMetaTileEntity, aPlayer, side, aX, aY, aZ);
        }

        if (!aBaseMetaTileEntity.isServerSide()) return true;

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
                held.getTagCompound()
                    .removeTag("gtsr.hubPos");
                GTUtility.sendChatToPlayer(
                    aPlayer,
                    StatCollector.translateToLocal("gtsr.binding.cleared") + nodeName
                        + StatCollector.translateToLocal("gtsr.binding.binding"));
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
        hubTag.setBoolean("output", true);
        hubTag.setBoolean("miner", isMiner);

        held.getTagCompound()
            .setTag("gtsr.hubPos", hubTag);

        GTUtility.sendChatToPlayer(aPlayer, StatCollector.translateToLocal("gtsr.binding.bound") + nodeName);
        return true;
    }

    private BoundDrillNode findBoundNode(int x, int y, int z, int dim) {
        for (BoundDrillNode node : mBoundNodes) {
            if (node.x == x && node.y == y && node.z == z && node.dimensionId == dim) {
                return node;
            }
        }
        return null;
    }

    @Override
    public int receiveFluid(FluidStack fluid, boolean doFill) {
        return 0;
    }

    @Override
    public FluidStack extractFluid(int amount, boolean doDrain) {
        return null;
    }

    @Override
    public void registerCacheNode(int x, int y, int z, int dim, boolean isOutputMode) {
        BoundDrillNode existing = findBoundNode(x, y, z, dim);
        if (existing == null) {
            String type = resolveNodeTypeAt(x, y, z, dim);
            boolean isMiner = "miner".equals(type);
            mBoundNodes.add(new BoundDrillNode(x, y, z, dim, isMiner));
        }
    }

    @Override
    public void unregisterCacheNode(int x, int y, int z, int dim) {
        BoundDrillNode existing = findBoundNode(x, y, z, dim);
        if (existing != null) {
            mBoundNodes.remove(existing);
        }
    }

    @Override
    public void updateCacheNodeMode(int x, int y, int z, int dim, boolean isOutputMode) {}

    @Override
    public boolean acceptsNodeType(String type) {
        return "miner".equals(type) || "driller".equals(type);
    }

    private String resolveNodeTypeAt(int x, int y, int z, int dim) {
        World world = DimensionManager.getWorld(dim);
        if (world == null) return "miner";
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof IGregTechTileEntity gte) {
            if (gte.getMetaTileEntity() instanceof MTERemoteWorkerNode node) {
                return node.getNodeType();
            }
        }
        return "miner";
    }

    private void sendBindingDebug(EntityPlayer aPlayer) {
        GTUtility.sendChatToPlayer(aPlayer, StatCollector.translateToLocal("gtsr.drilling.debug_title"));
        if (mBoundNodes.isEmpty()) {
            GTUtility.sendChatToPlayer(aPlayer, StatCollector.translateToLocal("gtsr.binding.debug_no_bindings"));
            return;
        }
        for (BoundDrillNode node : mBoundNodes) {
            String nodeType = node.isMiner ? StatCollector.translateToLocal("gtsr.drilling.node_miner")
                : StatCollector.translateToLocal("gtsr.drilling.node_driller");
            String status = node.isActive
                ? EnumChatFormatting.GREEN + StatCollector.translateToLocal("gtsr.info.geothermal_boiler.running")
                : EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.binding.debug_invalid");
            String posInfo = nodeType + " @ ("
                + node.x
                + ", "
                + node.y
                + ", "
                + node.z
                + ") DIM:"
                + node.dimensionId
                + " "
                + status;
            GTUtility.sendChatToPlayer(aPlayer, posInfo);
        }
    }

    public void pushNodeItemOutput(ItemStack stack) {
        if (stack == null) return;
        addOutputPartial(stack, false);
    }

    public boolean isMachineRunning() {
        return mMachine && getBaseMetaTileEntity().isAllowedToWork();
    }

    public void pushNodeFluidOutput(FluidStack fluid) {
        if (fluid == null || fluid.amount <= 0) return;
        for (MTEHatch hatch : mOutputHatches) {
            if (fluid.amount <= 0) break;
            IGregTechTileEntity hatchBase = hatch.getBaseMetaTileEntity();
            if (hatchBase == null) continue;
            IFluidHandler handler = hatchBase.getITankContainerAtSide(ForgeDirection.UNKNOWN);
            if (handler == null) continue;
            int filled = handler.fill(ForgeDirection.UNKNOWN, fluid, true);
            if (filled > 0) {
                fluid.amount -= filled;
            }
        }
    }

    @Override
    public int getMaxParallelRecipes() {
        return 1;
    }

    public double getEuDiscountForParallelism() {
        return 1.0d;
    }

    @Override
    public boolean isCorrectMachinePart(ItemStack aStack) {
        return true;
    }

    @Override
    public boolean checkRecipe(ItemStack aStack) {
        return true;
    }

    public int getOutputSlot() {
        return 0;
    }

    @Override
    public int getMaxEfficiency(ItemStack aStack) {
        return 10000;
    }

    @Override
    public int getTierRecipes() {
        return 0;
    }

    @Override
    public boolean supportsPowerPanel() {
        return false;
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int aColorIndex, boolean aActive, boolean aRedstone) {
        if (side == facing) {
            return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(getCasingTextureID()),
                aActive ? getFrontOverlayActive() : getFrontOverlay() };
        }
        return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(getCasingTextureID()) };
    }

    @Override
    protected ITexture getFrontOverlay() {
        return TextureFactory.of(Textures.BlockIcons.OVERLAY_FRONT_STEAM_FURNACE);
    }

    @Override
    protected ITexture getFrontOverlayActive() {
        return TextureFactory.of(Textures.BlockIcons.OVERLAY_FRONT_STEAM_FURNACE_ACTIVE);
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(getMachineType())
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.singularity_drilling_hub.0"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.singularity_drilling_hub.1"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.singularity_drilling_hub.2"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.singularity_drilling_hub.3"))
            .addInfo(
                EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.tooltip.singularity_drilling_hub.4")
                    + EnumChatFormatting.WHITE
                    + " "
                    + GTUtility.formatNumbers(BASE_STEAM_PER_SECOND)
                    + " + N×"
                    + GTUtility.formatNumbers(STEAM_PER_NODE)
                    + " L/s"
                    + EnumChatFormatting.RESET)
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.singularity_drilling_hub.5"))
            .beginStructureBlock(3, 3, 3, false)
            .addController(StatCollector.translateToLocal("gtsr.tooltip.singularity_drilling_hub.ctrl"))
            .addStructureInfo(
                EnumChatFormatting.GOLD
                    + StatCollector.translateToLocal("gtsr.tooltip.singularity_drilling_hub.casing"))
            .addStructureInfo(
                EnumChatFormatting.AQUA
                    + StatCollector.translateToLocal("gtsr.tooltip.singularity_drilling_hub.input_hatch")
                    + EnumChatFormatting.GRAY
                    + " "
                    + StatCollector.translateToLocal("gtsr.tooltip.crust_steam_borer.any_casing"))
            .addOutputBus(
                EnumChatFormatting.GOLD + "1"
                    + EnumChatFormatting.GRAY
                    + " "
                    + StatCollector.translateToLocal("gtsr.tooltip.crust_steam_borer.any_casing"),
                1)
            .addOutputHatch(
                EnumChatFormatting.GOLD + "1"
                    + EnumChatFormatting.GRAY
                    + " "
                    + StatCollector.translateToLocal("gtsr.tooltip.crust_steam_borer.any_casing"),
                1)
            .addStructureInfo(
                EnumChatFormatting.GRAY
                    + StatCollector.translateToLocal("gtsr.tooltip.steam_singularity_compressor.cooling"))
            .toolTipFinisher();
        return tt;
    }

    @Override
    protected IAlignmentLimits getInitialAlignmentLimits() {
        return (d, r, f) -> d.offsetY == 0 && r.isNotRotated() && !f.isVerticallyFliped();
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        if (!mBoundNodes.isEmpty()) {
            NBTTagList boundList = new NBTTagList();
            for (BoundDrillNode node : mBoundNodes) {
                NBTTagCompound nodeTag = new NBTTagCompound();
                nodeTag.setInteger("x", node.x);
                nodeTag.setInteger("y", node.y);
                nodeTag.setInteger("z", node.z);
                nodeTag.setInteger("dim", node.dimensionId);
                nodeTag.setBoolean("miner", node.isMiner);
                nodeTag.setBoolean("active", node.isActive);
                boundList.appendTag(nodeTag);
            }
            aNBT.setTag("mBoundNodes", boundList);
        }
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mBoundNodes.clear();
        if (aNBT.hasKey("mBoundNodes")) {
            NBTTagList boundList = aNBT.getTagList("mBoundNodes", 10);
            for (int i = 0; i < boundList.tagCount(); i++) {
                NBTTagCompound nodeTag = boundList.getCompoundTagAt(i);
                int x = nodeTag.getInteger("x");
                int y = nodeTag.getInteger("y");
                int z = nodeTag.getInteger("z");
                int dim = nodeTag.getInteger("dim");
                boolean miner = nodeTag.getBoolean("miner");
                boolean active = nodeTag.getBoolean("active");
                BoundDrillNode node = new BoundDrillNode(x, y, z, dim, miner);
                node.isActive = active;
                mBoundNodes.add(node);
            }
        }
    }

    @Override
    public String[] getInfoData() {
        int minerCount = 0;
        int drillerCount = 0;
        for (BoundDrillNode node : mBoundNodes) {
            if (node.isMiner) minerCount++;
            else drillerCount++;
        }

        ArrayList<String> info = new ArrayList<>();
        info.add(EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.recipe.singularity_drilling_hub"));
        info.add(
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.info.geothermal_boiler.status")
                + (mMachine
                    ? EnumChatFormatting.GREEN + StatCollector.translateToLocal("gtsr.info.geothermal_boiler.running")
                    : EnumChatFormatting.RED
                        + StatCollector.translateToLocal("gtsr.info.geothermal_boiler.incomplete")));
        info.add(
            EnumChatFormatting.GRAY + "Steam: "
                + EnumChatFormatting.RED
                + GTUtility.formatNumbers(BASE_STEAM_PER_SECOND)
                + " + N×"
                + GTUtility.formatNumbers(STEAM_PER_NODE)
                + " L/s");

        info.add(
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.drilling.node_miner")
                + ": "
                + EnumChatFormatting.WHITE
                + minerCount);
        info.add(
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.drilling.node_driller")
                + ": "
                + EnumChatFormatting.WHITE
                + drillerCount);
        info.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.gui.binding_hint"));

        return info.toArray(new String[0]);
    }
}
