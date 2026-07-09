package com.miaokatze.gtsr.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static com.miaokatze.gtsr.common.api.enums.GTSRHatchElement.PressureSteamInputHatch;
import static com.miaokatze.gtsr.common.api.enums.GTSRHatchElement.SteamCoolingHatch;
import static com.miaokatze.gtsr.common.api.enums.GTSRHatchElement.SteamOutputBus;
import static gregtech.api.enums.HatchElement.OutputHatch;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.ArrayList;
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
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;
import com.gtnewhorizon.structurelib.alignment.IAlignmentLimits;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizons.modularui.common.widget.DynamicPositionedColumn;
import com.gtnewhorizons.modularui.common.widget.FakeSyncWidget;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;
import com.gtnewhorizons.modularui.common.widget.TextWidget;
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.machine.base.IHubArray;
import com.miaokatze.gtsr.common.machine.base.MTERemoteWorkerNode;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrorRegistry;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTEHatchCustomFluidBase;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTESteamMultiBlockBase;

public class MTESingularityDrillingHub extends MTESteamMultiBlockBase<MTESingularityDrillingHub>
    implements ISurvivalConstructable, IHubArray {

    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static final int HORIZONTAL_OFF_SET = 9;
    private static final int VERTICAL_OFF_SET = 10;
    private static final int DEPTH_OFF_SET = 0;

    private static final int BASE_STEAM_PER_SECOND = 8_000;
    private static final int[] DRILL_NODE_STEAM_COST = { 2_000, 6_000, 12_000, 20_000 };
    private static final int[] MINER_NODE_STEAM_COST = { 2_000, 5_000, 12_000, 20_000 };

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
    public int mBoundNodeCount = 0;
    public int mSteamCost = 0;
    public boolean mIsSuperheated = false;
    public boolean mIsActivelyRunning = false;

    private static IIconContainer OVERLAY_OFF;
    private static IIconContainer OVERLAY_ON;

    public MTESingularityDrillingHub(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTESingularityDrillingHub(String aName) {
        super(aName);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister aBlockIconRegister) {
        OVERLAY_OFF = Textures.BlockIcons.custom("gtsr:MTESingularityDrillingHub_OFF");
        OVERLAY_ON = Textures.BlockIcons.custom("gtsr:MTESingularityDrillingHub_ON");
        super.registerIcons(aBlockIconRegister);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTESingularityDrillingHub(mName);
    }

    @Override
    public String getMachineType() {
        return "奇点钻探枢纽";
    }

    @Override
    public boolean isHighPressure() {
        return true;
    }

    protected int getCasingTextureID() {
        return GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings2, 0);
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
                        new String[][] {
                            { "            ", "            ", "            ", "            ", "            ",
                                "            ", "            ", "  BBB   BBB ", " B   B B   B", " B   B B   B",
                                " B   B B   B", "  BBB   BBB " },
                            { "            ", "            ", "            ", "            ", "            ",
                                "            ", "            ", "  BBB   BBB ", " BGGGB BGGGB", " BGGGB BGGGB",
                                " BGGGB BGGGB", "  BBB   BBB " },
                            { "            ", "            ", "            ", "            ", "            ",
                                "            ", "            ", "  BBB   BBB ", " B   B B   B", " B   B B   B",
                                " B   B B   B", "  BBB   BBB " },
                            { "            ", "         F  ", "        FCF ", "         F  ", "            ",
                                "            ", "            ", "  BBB   BBB ", " B   B B   B", " B   B B   B",
                                " B   B B   B", "  BBB   BBB " },
                            { "         F  ", "        BCB ", "       FC CF", "        BCB ", "         F  ",
                                "            ", "            ", " GBBBG GBBBG", " B   B B   B", " B   B B   B",
                                " B   B B   B", " GBBBG GBBBG" },
                            { "       GBBBG", "       B C B", "       BC CB", "       B C B", "       GBBBG",
                                "            ", "            ", " GBBBG GBBBG", " B   B B   B", " B   B B   B",
                                " B   B B   B", " GBBBG GBBBG" },
                            { "       GFFFG", "       F C F", "       FC CF", "       F C F", "       GFCFG",
                                "         C  ", "         C  ", " GBBBG GBCBG", " B   B B   B", " B   CCC   B",
                                " B   B B   B", " GBBBG GBBBG" },
                            { "       GFFFG", "       F C F", " FFFFFFFC CF", "       F C F", "       GFCFG",
                                "         C  ", "         C  ", " GBBBG GBCBG", " B   B B   B", " B   CCC   B",
                                " B   B B   B", " GBBBG GBBBG" },
                            { "       GFFFG", "GBBBBBBF C F", "DCCCCCCCC CF", "GEEEEEEF C F", "       GFCFG",
                                "         C  ", "         C  ", " GBBBG GBCBG", " B   B B   B", " B   CCC   B",
                                " B   B B   B", " GBBBG GBBBG" },
                            { "       GFFFG", "GBBBBBBF C F", "DCCCCCCCC CF", "GEEEEEEF C F", "       GFCFG",
                                "         C  ", "         C  ", " GBBBG GBCBG", " B   B B   B", " B   CCC   B",
                                " B   B B   B", " GBBBG GBBBG" },
                            { "       GF~FG", "GBBBBBBFEEEF", "DDDDDDDFEEEF", "GEEEEEEFEEEF", "       GFBFG",
                                "         G  ", "         G  ", " GBBBG GBBBG", " BEEEB BEEEB", " BEEEBGBEEEB",
                                " BEEEB BEEEB", " GBBBG GBBBG" },
                            { " BBBBBBGBBBG", "GBBBBBBBBBBB", "BBBBBBBBBBBB", "GBBBBBBBBBBB", " BBBBBBGBBBG",
                                "         G  ", "         G  ", " GBBBG GBBBG", " BBBBB BBBBB", " BBBBBGBBBBB",
                                " BBBBB BBBBB", " GBBBG GBBBG" } }))
                .addElement('~', onElementPass(x -> {}, ofBlock(GregTechAPI.sBlockCasings2, 0)))
                .addElement(
                    'B',
                    ofChain(
                        // casing-first: NEI 投影优先渲染外壳；真实 hatch 坐标上 casing 匹配失败后继续匹配 hatch adder。
                        onElementPass(MTESingularityDrillingHub::onCasingAdded, ofBlock(GregTechAPI.sBlockCasings2, 0)),
                        // Use atLeast(PressureSteamInputHatch) instead of hatchIds(...). Its mteBlacklist()
                        // excludes MTEHatchPressureSteamInput.class so NEI does not render it on casing positions.
                        buildHatchAdder(MTESingularityDrillingHub.class).atLeast(PressureSteamInputHatch)
                            .casingIndex(casingIndex)
                            .hint(1)
                            .shouldReject(t -> !t.mSteamInputFluids.isEmpty())
                            .build(),
                        buildHatchAdder(MTESingularityDrillingHub.class).atLeast(SteamOutputBus)
                            .casingIndex(casingIndex)
                            .hint(1)
                            .build(),
                        buildHatchAdder(MTESingularityDrillingHub.class).atLeast(OutputHatch)
                            .casingIndex(casingIndex)
                            .hint(1)
                            .build(),
                        // Use atLeast(SteamCoolingHatch) instead of hatchClass(...). Its mteBlacklist()
                        // excludes MTESteamCoolingHatch.class and MTEPressureSteamCoolingHatch.class.
                        buildHatchAdder(MTESingularityDrillingHub.class).atLeast(SteamCoolingHatch)
                            .casingIndex(casingIndex)
                            .hint(2)
                            .build()))
                .addElement('C', ofBlock(GregTechAPI.sBlockCasings2, 13))
                .addElement('D', ofBlock(GregTechAPI.sBlockCasings2, 3))
                .addElement('E', ofBlock(GregTechAPI.sBlockCasings3, 14))
                .addElement(
                    'F',
                    ofBlock(cpw.mods.fml.common.registry.GameRegistry.findBlock("IC2", "blockAlloyGlass"), 0))
                .addElement('G', ofBlock(GregTechAPI.sBlockFrames, Materials.Steel.mMetaItemSubID))
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
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        mCasingCount = 0;

        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET, errors)) {
            return;
        }

        if (this.mSteamInputFluids.isEmpty()) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }
        if (this.mOutputBusses.isEmpty() && this.mSteamOutputs.isEmpty()) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }
        if (this.mOutputHatches.isEmpty()) {
            errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
            return;
        }

        updateHatchTexture();
    }

    @Override
    public CheckRecipeResult checkProcessing() {
        // Hub doesn't use the recipe system for steam consumption.
        // Steam consumption and active state are handled entirely in onPostTick().
        // Returning NO_RECIPE prevents the recipe system from interfering.
        return CheckRecipeResultRegistry.NO_RECIPE;
    }

    /**
     * Override to prevent MTESteamMultiBlockBaseMixin's superheated steam 4x speed boost.
     * The drilling hub requires superheated steam but does NOT get speed boost from it.
     * Steam consumption is handled entirely by checkProcessing(), so onRunningTick
     * only needs to push cooling products.
     */
    @Override
    public boolean onRunningTick(ItemStack aStack) {
        // Steam is consumed in checkProcessing() via depleteInput().
        // No 4x speed boost, no additional steam consumption here.
        // Just push cooling products (handled by the mixin's gtsr$pushCoolingProducts).
        return true;
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        if (!aBaseMetaTileEntity.isServerSide() || !mMachine) {
            super.onPostTick(aBaseMetaTileEntity, aTick);
            return;
        }

        mBoundNodeCount = mBoundNodes.size();
        int totalCost = BASE_STEAM_PER_SECOND;
        boolean hasActiveNode = false;
        for (BoundDrillNode node : mBoundNodes) {
            if (!node.isActive) continue;
            boolean working = resolveNodeWorking(node);
            if (!working) continue;
            hasActiveNode = true;
            int tier = resolveNodeTier(node);
            if (node.isMiner) {
                totalCost += MINER_NODE_STEAM_COST[Math.min(tier, MINER_NODE_STEAM_COST.length - 1)];
            } else {
                totalCost += DRILL_NODE_STEAM_COST[Math.min(tier, DRILL_NODE_STEAM_COST.length - 1)];
            }
        }
        mSteamCost = totalCost;
        mIsSuperheated = hasSuperheatedSteamInHatch();

        // Hub is active only when it has superheated steam AND at least one node is working.
        // This prevents steam consumption when no nodes are actively working.
        boolean shouldBeActive = mMachine && mIsSuperheated && hasActiveNode;

        // Consume steam directly in onPostTick, independent of the recipe system.
        if (shouldBeActive && aTick % 20 == 0) {
            FluidStack steamStack = FluidRegistry.getFluidStack("ic2superheatedsteam", totalCost);
            if (steamStack != null && depleteInput(steamStack)) {
                mEfficiencyIncrease = 10000;
            }
        }

        super.onPostTick(aBaseMetaTileEntity, aTick);

        // Override active state: super.onPostTick() calls setActive(mMaxProgresstime > 0),
        // but since checkProcessing() always returns NO_RECIPE, mMaxProgresstime stays 0.
        // We directly set the active state based on actual working condition,
        // which triggers scheduleTexturePacket() to sync the active texture to the client.
        aBaseMetaTileEntity.setActive(shouldBeActive);
        mIsActivelyRunning = shouldBeActive;

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

        if (!held.hasTagCompound() || !held.getTagCompound()
            .hasKey("gtsr.singularity_consumed")) {
            boolean consumed = false;
            for (int i = 0; i < aPlayer.inventory.mainInventory.length; i++) {
                ItemStack invStack = aPlayer.inventory.mainInventory[i];
                if (invStack != null && GTSRItemList.SteamEntangledSingularity.isStackEqual(invStack, true, true)) {
                    invStack.stackSize--;
                    if (invStack.stackSize <= 0) {
                        aPlayer.inventory.mainInventory[i] = null;
                    }
                    aPlayer.inventoryContainer.detectAndSendChanges();
                    consumed = true;
                    break;
                }
            }
            if (!consumed) {
                GTUtility.sendChatToPlayer(aPlayer, StatCollector.translateToLocal("gtsr.binding.no_singularity"));
                return true;
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

    private int resolveNodeTier(BoundDrillNode node) {
        World world = DimensionManager.getWorld(node.dimensionId);
        if (world == null || !world.blockExists(node.x, node.y, node.z)) return 0;
        TileEntity te = world.getTileEntity(node.x, node.y, node.z);
        if (te instanceof IGregTechTileEntity gte) {
            if (gte.getMetaTileEntity() instanceof MTERemoteWorkerNode workerNode) {
                return workerNode.getDrillTier();
            }
        }
        return 0;
    }

    private boolean resolveNodeWorking(BoundDrillNode node) {
        World world = DimensionManager.getWorld(node.dimensionId);
        if (world == null || !world.blockExists(node.x, node.y, node.z)) return false;
        TileEntity te = world.getTileEntity(node.x, node.y, node.z);
        if (te instanceof IGregTechTileEntity gte) {
            if (gte.getMetaTileEntity() instanceof MTERemoteWorkerNode workerNode) {
                return workerNode.isActivelyWorking();
            }
        }
        return false;
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
        addOutputPartial(stack);
    }

    public boolean isMachineRunning() {
        return mMachine && getBaseMetaTileEntity().isAllowedToWork();
    }

    public void pushNodeFluidOutput(FluidStack fluid) {
        if (fluid == null || fluid.amount <= 0) return;
        for (MTEHatch hatch : mOutputHatches) {
            if (fluid.amount <= 0) break;
            int tAmount = hatch.fill(fluid, false);
            if (tAmount >= fluid.amount) {
                hatch.fill(fluid, true);
                break;
            } else if (tAmount > 0) {
                fluid.amount -= hatch.fill(fluid, true);
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
    protected IIconContainer getInactiveOverlay() {
        return OVERLAY_OFF;
    }

    @Override
    protected IIconContainer getActiveOverlay() {
        return OVERLAY_ON;
    }

    // beta-2 兼容：MTESteamMultiBlockBase 将 getActiveGlowOverlay/getInactiveGlowOverlay 改为 abstract
    // 当前返回 null（与 beta-1 默认行为等价，无发光层），后续可补充发光纹理资源
    @Override
    protected IIconContainer getActiveGlowOverlay() {
        return null;
    }

    @Override
    protected IIconContainer getInactiveGlowOverlay() {
        return null;
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(StatCollector.translateToLocal("gtsr.tooltip.singularity_hub.type"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.singularity_hub.desc"))
            .addInfo(StatCollector.translateToLocal("gtsr.tooltip.singularity_hub.desc2"))
            .addSeparator()
            .addInfo(
                EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.tooltip.shared.steam_cost")
                    + EnumChatFormatting.WHITE
                    + " "
                    + NumberFormatUtil.formatNumber(BASE_STEAM_PER_SECOND)
                    + " + "
                    + StatCollector.translateToLocal("gtsr.tooltip.singularity_hub.node_cost_desc"))
            .addInfo(
                EnumChatFormatting.RED
                    + StatCollector.translateToLocal("gtsr.tooltip.singularity_hub.superheated_required"))
            .beginStructureBlock(12, 12, 12, false)
            .addController(StatCollector.translateToLocal("gtsr.tooltip.singularity_hub.ctrl"))
            .addOtherStructurePart(
                StatCollector.translateToLocal("gtsr.tooltip.shared.steam_input_hatch"),
                StatCollector.translateToLocal("gtsr.tooltip.singularity_hub.steam_input"),
                1)
            .addOutputBus(StatCollector.translateToLocal("gtsr.tooltip.singularity_hub.output_bus"), 1)
            .addOutputHatch(StatCollector.translateToLocal("gtsr.tooltip.singularity_hub.output_hatch"), 1)
            .addStructureInfo("")
            .addStructureInfo(
                EnumChatFormatting.DARK_PURPLE + StatCollector.translateToLocal("gtsr.tooltip.shared.steel_only"))
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.steel_casing"), 381, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.steel_pipe_casing"), 67, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.steel_gear_box_casing"), 9, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.steel_firebox_casing"), 45, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.reinforced_glass"), 66, false)
            .addCasingInfoExactly(StatCollector.translateToLocal("gtsr.tooltip.shared.steel_frame_box"), 124, false)
            .addStructureHint("gtsr.tooltip.shared.no_maintenance")
            .addStructureHint("gtsr.tooltip.singularity_hub.hint_node")
            .addStructureHint("gtsr.tooltip.singularity_hub.hint_chunk")
            .addStructureHint("gtsr.tooltip.shared.hub_singularity_cost")
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

    private boolean hasSuperheatedSteamInHatch() {
        for (MTEHatchCustomFluidBase hatch : mSteamInputFluids) {
            FluidStack fs = hatch.getFluid();
            if (fs != null && fs.getFluid() != null
                && "ic2superheatedsteam".equals(
                    fs.getFluid()
                        .getName())
                && fs.amount > 0) {
                return true;
            }
        }
        return false;
    }

    @Deprecated
    @Override
    protected void drawTexts(DynamicPositionedColumn screenElements, SlotWidget inventorySlot) {
        super.drawTexts(screenElements, inventorySlot);
        screenElements.widget(new TextWidget().setStringSupplier(() -> {
            String status = mIsActivelyRunning
                ? EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.gui.status.running")
                : EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.gui.status.idle");
            return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.status")
                + " "
                + status
                + EnumChatFormatting.RESET;
        }))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.YELLOW
                        + StatCollector.translateToLocal("gtsr.gui.singularity_hub.bound_nodes")
                        + " "
                        + EnumChatFormatting.GOLD
                        + mBoundNodeCount
                        + EnumChatFormatting.RESET))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.tooltip.shared.steam_cost")
                        + " "
                        + EnumChatFormatting.RED
                        + NumberFormatUtil.formatNumber(mSteamCost)
                        + " L/s"
                        + EnumChatFormatting.RESET))
            .widget(
                new TextWidget().setStringSupplier(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.steam_type")
                        + " "
                        + EnumChatFormatting.YELLOW
                        + StatCollector.translateToLocal("gtsr.gui.steam_type.superheated")
                        + EnumChatFormatting.RESET))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mBoundNodeCount, val -> mBoundNodeCount = val))
            .widget(new FakeSyncWidget.IntegerSyncer(() -> mSteamCost, val -> mSteamCost = val))
            .widget(new FakeSyncWidget.BooleanSyncer(() -> mIsSuperheated, val -> mIsSuperheated = val))
            .widget(new FakeSyncWidget.BooleanSyncer(() -> mIsActivelyRunning, val -> mIsActivelyRunning = val));
    }

    @Override
    protected gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui<?> getGui() {
        return new com.miaokatze.gtsr.common.gui.MTESingularityDrillingHubGui(this);
    }

    @Override
    public String[] getInfoData() {
        int activeNodeCount = 0;
        for (BoundDrillNode node : mBoundNodes) {
            if (node.isActive) activeNodeCount++;
        }

        ArrayList<String> info = new ArrayList<>();
        info.add(
            EnumChatFormatting.BLUE + StatCollector.translateToLocal("gtsr.tooltip.singularity_hub.type")
                + EnumChatFormatting.RESET);
        if (!mMachine) {
            info.add(EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.gui.building"));
            return info.toArray(new String[0]);
        }
        String statusKey;
        EnumChatFormatting statusColor;
        if (mIsActivelyRunning) {
            statusKey = "gtsr.gui.status.running";
            statusColor = EnumChatFormatting.AQUA;
        } else {
            statusKey = "gtsr.gui.status.idle";
            statusColor = EnumChatFormatting.GRAY;
        }
        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.status")
                + " "
                + statusColor
                + StatCollector.translateToLocal(statusKey)
                + EnumChatFormatting.RESET);
        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.singularity_hub.bound_nodes")
                + " "
                + EnumChatFormatting.GOLD
                + mBoundNodes.size()
                + EnumChatFormatting.RESET);
        int totalCost = BASE_STEAM_PER_SECOND;
        for (BoundDrillNode node : mBoundNodes) {
            if (!node.isActive) continue;
            boolean working = resolveNodeWorking(node);
            if (!working) continue;
            int tier = resolveNodeTier(node);
            if (node.isMiner) {
                totalCost += MINER_NODE_STEAM_COST[Math.min(tier, MINER_NODE_STEAM_COST.length - 1)];
            } else {
                totalCost += DRILL_NODE_STEAM_COST[Math.min(tier, DRILL_NODE_STEAM_COST.length - 1)];
            }
        }
        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.tooltip.shared.steam_cost")
                + " "
                + EnumChatFormatting.RED
                + NumberFormatUtil.formatNumber(totalCost)
                + " L/s"
                + EnumChatFormatting.RESET);
        info.add(
            EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.steam_type")
                + " "
                + EnumChatFormatting.YELLOW
                + StatCollector.translateToLocal("gtsr.gui.steam_type.superheated")
                + EnumChatFormatting.RESET);
        return info.toArray(new String[0]);
    }
}
