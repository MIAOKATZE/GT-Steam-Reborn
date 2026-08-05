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
import com.miaokatze.gtsr.api.compat.GTVersionCompat;
import com.miaokatze.gtsr.api.compat.ICoolingHatchHolder;
import com.miaokatze.gtsr.api.compat.SteamCoolingSupport;
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.machine.base.IHubArray;
import com.miaokatze.gtsr.common.machine.base.MTERemoteWorkerNode;
import com.miaokatze.gtsr.common.util.HubTeleportUtil;

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
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTESteamMultiBlockBase;

public class MTESingularityDrillingHub extends MTESteamMultiBlockBase<MTESingularityDrillingHub>
    implements ISurvivalConstructable, IHubArray {

    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static final int HORIZONTAL_OFF_SET = 9;
    private static final int VERTICAL_OFF_SET = 10;
    private static final int DEPTH_OFF_SET = 0;

    private static final int BASE_STEAM_PER_SECOND = 8_000;
    private static final int[] DRILL_NODE_STEAM_COST = { 2_000, 6_000, 12_000, 20_000 };
    private static final int[] MINER_NODE_STEAM_COST = { 5_000, 10_000, 20_000, 80_000, 240_000 };

    private static IStructureDefinition<MTESingularityDrillingHub> STRUCTURE_DEFINITION = null;

    private static class BoundDrillNode {

        final int x, y, z;
        final int dimensionId;
        final boolean isMiner;
        boolean isActive;
        transient MTERemoteWorkerNode cachedWorker;
        transient long lastLookupTick;
        transient long nextLookupTick;
        transient boolean lastLookupLoaded;

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
        // v1.9.41 修复：冷却仓纳入纹理更新（结构元素含 SteamCoolingHatch，此前冷却仓底材不随结构刷新）
        SteamCoolingSupport.updateHatchTextures((ICoolingHatchHolder) this, textureID);
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
                            .shouldReject(t -> !t.mSteamInputFluids.isEmpty() && !t.mInputHatches.isEmpty())
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
                    // 防爆玻璃：通过兼容层自动适配 beta-1（IC2 blockAlloyGlass）与 beta-2（GT5U sBlockGlass1 meta 10）
                    ofBlock(GTVersionCompat.getReinforcedGlassBlock(), GTVersionCompat.getReinforcedGlassMeta()))
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

        if (this.mSteamInputFluids.isEmpty() && this.mInputHatches.isEmpty()) {
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
     * Steam consumption is handled entirely by onPostTick(), so onRunningTick
     * only needs to push cooling products.
     */
    @Override
    public boolean onRunningTick(ItemStack aStack) {
        // Steam is consumed in onPostTick() via depleteInput().
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
        mSteamCost = totalCost;
        mIsSuperheated = hasSuperheatedSteamInHatch();

        // The hub power switch controls the hub itself. Superheated steam maintains the hub,
        // while working bound nodes only add their tier-dependent cost.
        boolean shouldBeActive = mMachine && aBaseMetaTileEntity.isAllowedToWork() && mIsSuperheated;

        // Consume steam directly in onPostTick, independent of the recipe system.
        if (shouldBeActive && aTick % 20 == 0) {
            FluidStack steamStack = FluidRegistry.getFluidStack("ic2superheatedsteam", totalCost);
            if (steamStack != null && depleteInput(steamStack)) {
                // v1.9.40 修复：onRunningTick 覆写不调 super，mixin 的冷却注入不触发，
                // 此处手动推送冷却产物（过热蒸汽 → 压力冷却仓转为普通蒸汽 1:1）。
                // 无压力冷却仓时产物按"冷却仅输出到冷却仓"原则静默丢弃（与 mixin 行为一致）。
                SteamCoolingSupport.pushCoolingProducts((ICoolingHatchHolder) this, totalCost, true);
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
            MTERemoteWorkerNode worker = resolveWorkerNode(node, true);
            if (worker == null && node.lastLookupLoaded) {
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

        // 手持枢纽终端右击：打开专属本枢纽的节点状态管理界面（Modern UI 2），
        // 不走芯片调试、不触发节点绑定，也不占用空手右键（空手右键仍打开普通机器 GUI）。
        // 注：原「空手+潜行」方案不可行——GT BaseMetaTileEntity 在潜行时拦截右击（用于贴墙放方块），
        // 本 MTE 的 onRightclick 根本收不到该事件，故改用持物右击方案。
        // 注2：旧方案为手持蒸汽纠缠奇点右击，现已改由枢纽终端承担（奇点回归纯合成材料）。
        if (held != null && GTSRItemList.HubTerminal.isStackEqual(held, false, true)) {
            if (aBaseMetaTileEntity.isServerSide()) {
                openHubStatusGui(aPlayer);
            }
            return true;
        }

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
            if (!consumeSteamEntangledSingularity(aPlayer)) {
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
            existing.cachedWorker = null;
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
        ensureChunkLoaded(world, x, z);
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof IGregTechTileEntity gte) {
            if (gte.getMetaTileEntity() instanceof MTERemoteWorkerNode node) {
                return node.getNodeType();
            }
        }
        return "miner";
    }

    private int resolveNodeTier(BoundDrillNode node) {
        MTERemoteWorkerNode workerNode = resolveWorkerNode(node, false);
        return workerNode == null ? 0 : workerNode.getDrillTier();
    }

    private boolean resolveNodeWorking(BoundDrillNode node) {
        MTERemoteWorkerNode workerNode = resolveWorkerNode(node, false);
        return workerNode != null && workerNode.isActivelyWorking();
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

    /**
     * 打开节点状态管理界面（Modern UI 2）。必须在服务端调用，
     * 实际打开逻辑委托给 HubStatusGuiFactory（独立 MUI2 factory，不影响主 GUI 的 MUI2 路径）。
     */
    public void openHubStatusGui(EntityPlayer player) {
        com.miaokatze.gtsr.common.gui.HubStatusGuiFactory.open(player, this);
    }

    /**
     * 按坐标解析绑定节点对应的 MTERemoteWorkerNode 实例；世界未加载、方块不存在
     * 或目标不是远程工作节点时返回 null。
     * 若目标区块未加载，会尝试主动加载一次（节点自身应持有 Forge chunk ticket，但在 ticket
     * 尚未生效或跨维度首次访问时仍可能处于未加载状态），以保证终端按钮等即时操作可用。
     */
    private MTERemoteWorkerNode resolveWorkerNode(int x, int y, int z, int dim) {
        BoundDrillNode bound = findBoundNode(x, y, z, dim);
        if (bound == null) return null;
        bound.cachedWorker = null;
        bound.lastLookupTick = 0;
        bound.nextLookupTick = 0;
        bound.lastLookupLoaded = false;
        return resolveWorkerNode(bound, true);
    }

    /** Resolves a bound worker once per hub tick; only explicit/periodic validation may load a chunk. */
    private MTERemoteWorkerNode resolveWorkerNode(BoundDrillNode bound, boolean loadChunk) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        World hubWorld = base == null ? null : base.getWorld();
        long now = hubWorld == null ? 0L : hubWorld.getTotalWorldTime();
        if (bound.cachedWorker != null && (bound.lastLookupTick == now || now < bound.nextLookupTick)) {
            return bound.cachedWorker;
        }
        if (!loadChunk && now < bound.nextLookupTick) return null;

        bound.lastLookupTick = now;
        bound.lastLookupLoaded = false;
        World world = DimensionManager.getWorld(bound.dimensionId);
        if (world == null) {
            bound.cachedWorker = null;
            bound.nextLookupTick = now + 20;
            return null;
        }
        if (!world.blockExists(bound.x, 0, bound.z)) {
            if (!loadChunk || !HubTeleportUtil.ensureChunkLoaded(world, bound.x, bound.z)) {
                bound.cachedWorker = null;
                bound.nextLookupTick = now + 20;
                return null;
            }
        }
        if (!world.blockExists(bound.x, bound.y, bound.z)) {
            bound.cachedWorker = null;
            bound.nextLookupTick = now + 20;
            return null;
        }

        bound.lastLookupLoaded = true;
        TileEntity te = world.getTileEntity(bound.x, bound.y, bound.z);
        if (te instanceof IGregTechTileEntity gte && gte.getMetaTileEntity() instanceof MTERemoteWorkerNode node) {
            bound.cachedWorker = node;
            bound.nextLookupTick = now + 20;
            return node;
        }
        bound.cachedWorker = null;
        bound.nextLookupTick = now + 20;
        return null;
    }

    /**
     * 在访问跨维度节点前，确保其所在 chunk 已加载到内存。
     * 仅作为节点自身 chunk ticket 的后备；成功加载后节点的 onPostTick 会自行维护 ticket。
     */
    private static void ensureChunkLoaded(World world, int x, int z) {
        if (world.blockExists(x, 0, z)) return;
        try {
            world.getChunkProvider()
                .loadChunk(x >> 4, z >> 4);
        } catch (Exception e) {
            // 加载失败（如世界生成器异常）时保持原行为：后续 blockExists 会返回 false
        }
    }

    /**
     * 序列化当前绑定节点列表（供状态 UI 同步显示）。
     * 每项含：坐标/维度/类型/tier/工作状态(working)/是否允许工作(allowed)/
     * 是否完全停止(retractable)/是否可回收(recyclable，停止或待机即可)。
     */
    public NBTTagList getNodeListTag() {
        NBTTagList list = new NBTTagList();
        for (BoundDrillNode node : mBoundNodes) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("x", node.x);
            tag.setInteger("y", node.y);
            tag.setInteger("z", node.z);
            tag.setInteger("dim", node.dimensionId);
            tag.setBoolean("isMiner", node.isMiner);
            tag.setInteger("tier", resolveNodeTier(node));
            tag.setBoolean("working", resolveNodeWorking(node));
            MTERemoteWorkerNode worker = resolveWorkerNode(node, false);
            boolean allowed = false;
            boolean retractable = false;
            if (worker != null && worker.getBaseMetaTileEntity() != null) {
                allowed = worker.getBaseMetaTileEntity()
                    .isAllowedToWork();
                retractable = worker.isFullyRetracted();
            }
            tag.setBoolean("allowed", allowed);
            tag.setBoolean("retractable", retractable);
            // 回收按钮可用状态：停止或待机即可回收；离线节点（worker 解析不到）不可回收
            tag.setBoolean("recyclable", worker != null && worker.isRecyclableNow());
            // 节点自定义名（无则为空串，客户端回退显示默认类型名）
            tag.setString("name", worker != null ? worker.getCustomName() : "");
            list.appendTag(tag);
        }
        return list;
    }

    /**
     * 状态 UI 远程开始/停止节点：直接切换节点底座的 allowedToWork 标志，
     * 节点 onPostTick 的边沿监听会完成软禁用/复位；同步更新绑定缓存的 isActive 标志。
     */
    public void setNodeActiveFromGui(int x, int y, int z, int dim, boolean active) {
        MTERemoteWorkerNode node = resolveWorkerNode(x, y, z, dim);
        if (node == null) return;
        IGregTechTileEntity base = node.getBaseMetaTileEntity();
        if (base == null) return;
        if (active) {
            base.enableWorking();
        } else {
            base.disableWorking();
        }
        BoundDrillNode bound = findBoundNode(x, y, z, dim);
        if (bound != null) {
            bound.isActive = active;
        }
    }

    /**
     * 状态 UI 远程升级节点（消耗玩家背包物品），返回是否升级成功。
     */
    public boolean upgradeNodeFromGui(EntityPlayer player, int x, int y, int z, int dim) {
        MTERemoteWorkerNode node = resolveWorkerNode(x, y, z, dim);
        return node != null && node.tryUpgrade(player);
    }

    /**
     * 状态 UI 重命名节点：名字在服务端做安全裁剪（剔 §/去首尾空白/≤24 字符），
     * 裁剪后为空表示清除自定义名（UI 回退默认类型名）。
     * 名字变化由 nodeList 每 tick 变化检测自动同步到枢纽状态 UI 客户端；
     * 节点方块自身（GUI 标题/Waila）另经 issueTileUpdate 触发 description packet 同步。
     */
    public void renameNodeFromGui(int x, int y, int z, int dim, String name) {
        MTERemoteWorkerNode node = resolveWorkerNode(x, y, z, dim);
        if (node == null) return;
        node.setCustomName(MTERemoteWorkerNode.sanitizeCustomName(name));
        // 触发节点 TE 重同步（S35 description packet），客户端 MTE 拿到新自定义名以更新 GUI 标题
        if (node.getBaseMetaTileEntity() != null) {
            node.getBaseMetaTileEntity()
                .issueTileUpdate();
        }
    }

    /**
     * 状态 UI 传送玩家到指定节点。
     * 目标为节点方块正上方 y+1；若该位置不安全，则向上查找最近安全空气格。
     * 每次传送消耗玩家背包 1 个蒸汽纠缠奇点，无冷却，支持跨维度。
     */
    public void teleportPlayerToNodeFromGui(EntityPlayer player, int x, int y, int z, int dim) {
        if (player == null) return;
        if (!canUseStatusAction(player) || findBoundNode(x, y, z, dim) == null) {
            GTUtility.sendChatToPlayer(player, StatCollector.translateToLocal("gtsr.hub_status.teleport_fail_node"));
            return;
        }

        World targetWorld = HubTeleportUtil.resolveTargetWorld(player, dim);
        if (targetWorld == null) {
            GTUtility.sendChatToPlayer(player, StatCollector.translateToLocal("gtsr.hub_status.teleport_fail_dim"));
            return;
        }
        if (!HubTeleportUtil.ensureChunkLoaded(targetWorld, x, z)) {
            GTUtility.sendChatToPlayer(player, StatCollector.translateToLocal("gtsr.hub_status.teleport_fail_node"));
            return;
        }

        MTERemoteWorkerNode node = resolveWorkerNode(x, y, z, dim);
        if (node == null) {
            GTUtility.sendChatToPlayer(player, StatCollector.translateToLocal("gtsr.hub_status.teleport_fail_node"));
            return;
        }
        int safeY = HubTeleportUtil.findSafeTeleportHeight(targetWorld, x, y, z);
        if (safeY < 0) {
            GTUtility.sendChatToPlayer(player, StatCollector.translateToLocal("gtsr.hub_status.teleport_fail_unsafe"));
            return;
        }
        if (!HubTeleportUtil.teleportPlayer(player, targetWorld, dim, x, safeY, z)) {
            GTUtility
                .sendChatToPlayer(player, StatCollector.translateToLocal("gtsr.hub_status.teleport_no_singularity"));
        }
    }

    private boolean canUseStatusAction(EntityPlayer player) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        World world = base == null ? null : base.getWorld();
        if (player == null || base == null || world == null || player.dimension != world.provider.dimensionId)
            return false;
        return base.canAccessData()
            && player.getDistanceSq(base.getXCoord() + 0.5D, base.getYCoord() + 0.5D, base.getZCoord() + 0.5D) <= 64.0D;
    }

    private void teleportPlayerToNodeLegacy(EntityPlayer player, int x, int y, int z, int dim) {
        teleportPlayerToNodeFromGui(player, x, y, z, dim);
        /*
         * if (!(player instanceof EntityPlayerMP playerMP)) return;
         * // 节点必须仍存在
         * MTERemoteWorkerNode node = resolveWorkerNode(x, y, z, dim);
         * if (node == null) {
         * GTUtility.sendChatToPlayer(player, StatCollector.translateToLocal("gtsr.hub_status.teleport_fail_node"));
         * return;
         * }
         * // 获取目标世界（同维度直接用玩家世界；跨维度按需初始化维度）
         * World targetWorld;
         * if (player.dimension == dim) {
         * targetWorld = player.worldObj;
         * } else {
         * if (!DimensionManager.isDimensionRegistered(dim)) {
         * GTUtility.sendChatToPlayer(player, StatCollector.translateToLocal("gtsr.hub_status.teleport_fail_dim"));
         * return;
         * }
         * targetWorld = DimensionManager.getWorld(dim);
         * if (targetWorld == null) {
         * DimensionManager.initDimension(dim);
         * targetWorld = DimensionManager.getWorld(dim);
         * }
         * if (targetWorld == null) {
         * GTUtility.sendChatToPlayer(player, StatCollector.translateToLocal("gtsr.hub_status.teleport_fail_dim"));
         * return;
         * }
         * }
         * // 查找安全落脚点（失败不消耗奇点）
         * int safeY = findSafeTeleportHeight(targetWorld, x, y, z);
         * if (safeY < 0) {
         * GTUtility.sendChatToPlayer(player, StatCollector.translateToLocal("gtsr.hub_status.teleport_fail_unsafe"));
         * return;
         * }
         * // 消耗蒸汽纠缠奇点
         * if (!consumeSteamEntangledSingularity(player)) {
         * GTUtility
         * .sendChatToPlayer(player, StatCollector.translateToLocal("gtsr.hub_status.teleport_no_singularity"));
         * return;
         * }
         * // 执行传送
         * double targetX = x + 0.5D;
         * double targetY = (double) safeY;
         * double targetZ = z + 0.5D;
         * if (player.dimension == dim) {
         * // 同维度：关闭 GUI 并直接设置位置
         * playerMP.closeScreen();
         * if (player.ridingEntity != null) player.mountEntity(null);
         * if (player.riddenByEntity != null) player.riddenByEntity.mountEntity(null);
         * playerMP.playerNetServerHandler
         * .setPlayerLocation(targetX, targetY, targetZ, player.rotationYaw, player.rotationPitch);
         * } else {
         * // 跨维度：使用 GT5U 统一工具，内部已处理坐骑脱离、关闭 GUI、加载区块、同步背包/状态等
         * GTUtility.moveEntityToDimensionAtCoords(playerMP, dim, targetX, targetY, targetZ);
         * }
         */
    }

    /**
     * 从节点方块正上方 y+1 开始，向上查找最近的安全落脚点。
     * 安全定义：落脚处（脚）与上方一格（头）均为空气，且脚下方块可承重（blocksMovement）。
     * 若找不到则返回 -1。
     */
    private static int findSafeTeleportHeight(World world, int x, int y, int z) {
        int maxY = world.getActualHeight() - 2;
        for (int ty = y + 1; ty <= maxY; ty++) {
            if (world.isAirBlock(x, ty, z) && world.isAirBlock(x, ty + 1, z)
                && world.getBlock(x, ty - 1, z)
                    .getMaterial()
                    .blocksMovement()) {
                return ty;
            }
        }
        return -1;
    }

    /**
     * 从玩家主物品栏消耗 1 个蒸汽纠缠奇点。
     * 
     * @return 是否成功消耗
     */
    private static boolean consumeSteamEntangledSingularity(EntityPlayer player) {
        for (int i = 0; i < player.inventory.mainInventory.length; i++) {
            ItemStack invStack = player.inventory.mainInventory[i];
            if (invStack != null && GTSRItemList.SteamEntangledSingularity.isStackEqual(invStack, true, true)) {
                invStack.stackSize--;
                if (invStack.stackSize <= 0) {
                    player.inventory.mainInventory[i] = null;
                }
                player.inventoryContainer.detectAndSendChanges();
                return true;
            }
        }
        return false;
    }

    /**
     * 状态 UI 快捷回收节点：允许「停止（不允许工作）或待机（未在实际工作）」的节点。
     * 流程：构造带 NBT 的节点本体 + 收集背包中已收回的采矿管道 + 立即清除世界中
     * 未收回的管道并按段数折算为 miningPipe 物品 → 从输出总线推出 →
     * 释放区块加载 → 世界中移除节点方块（不掉落，本体已手动输出）→ 注销绑定缓存。
     */
    public boolean recycleNodeFromGui(int x, int y, int z, int dim) {
        MTERemoteWorkerNode node = resolveWorkerNode(x, y, z, dim);
        if (node == null || !node.isRecyclableNow()) return false;
        IGregTechTileEntity base = node.getBaseMetaTileEntity();
        if (base == null) return false;
        World world = base.getWorld();
        if (world == null) return false;

        // 1. 构造节点本体物品：与 BaseMetaTileEntity.getDrops() 相同的构造方式
        // （sBlockMachines + metaTileID），setItemNBT 会写入 gtsr.hubPos 绑定信息
        // 与 singularity_consumed 标记，玩家重新放置后仍绑定本枢纽
        ItemStack nodeStack = new ItemStack(GregTechAPI.sBlockMachines, 1, base.getMetaTileID());
        NBTTagCompound itemTag = new NBTTagCompound();
        node.setItemNBT(itemTag);
        if (!itemTag.hasNoTags()) {
            nodeStack.setTagCompound(itemTag);
        }

        // 2. 收集节点背包中已收回的采矿管道
        List<ItemStack> pipes = node.drainStoredMiningPipes();

        // 2.5. 立即清除世界中未收回的管道段，按段数折算为 miningPipe 物品一并返还
        // （须在移除节点方块之前调用，此时节点底座与世界句柄仍有效；仅服务端实际改世界）
        int worldPipes = node.clearDeployedPipesAndReturnCount();
        ItemStack worldPipeStack = worldPipes > 0 ? GTModHandler.getIC2Item("miningPipe", worldPipes) : null;

        // 3. 从输出总线推出。addOutputPartial 不提供容量预检，放不下的部分会被销毁，
        // 因此假定总线有足够空间（与节点正常产出推送同一语义）
        pushNodeItemOutput(nodeStack);
        for (ItemStack pipe : pipes) {
            pushNodeItemOutput(pipe);
        }
        // getIC2Item 在 IC2 缺失时可能返回 null，判空防御
        if (worldPipeStack != null) {
            pushNodeItemOutput(worldPipeStack);
        }

        // 4. 释放该节点持有的全部区块加载 ticket，再移除世界中的节点方块并注销绑定
        node.releaseAllChunks();
        world.setBlockToAir(x, y, z);
        unregisterCacheNode(x, y, z, dim);
        return true;
    }

    public boolean isMachineRunning() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        return mMachine && base != null && base.isAllowedToWork();
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
    // 返回 Textures.BlockIcons.VOID（GT5U 官方"空纹理"常量，渲染器跳过 InvisibleIcon，无发光层）
    // 不能返回 null，否则 beta-2 的 createTextureWithCasing 会导致 GTTextureBuilder.build() 抛出
    // "iconContainer not specified!" 崩溃（创造物品栏渲染时触发）
    @Override
    protected IIconContainer getActiveGlowOverlay() {
        return Textures.BlockIcons.VOID;
    }

    @Override
    protected IIconContainer getInactiveGlowOverlay() {
        return Textures.BlockIcons.VOID;
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
            .addStructureHint("gtsr.tooltip.singularity_hub.hint_status")
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
        // v1.10.6：统一走 SteamCoolingSupport（mSteamInputFluids 本地罐 + mInputHatches 3参 drain 探测）
        return SteamCoolingSupport.hasSuperheatedSteam(this);
    }

    @Deprecated
    @Override
    protected void drawTexts(DynamicPositionedColumn screenElements, SlotWidget inventorySlot) {
        super.drawTexts(screenElements, inventorySlot);
        screenElements.widget(new TextWidget().setStringSupplier(() -> {
            return EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.gui.hub.terminal_hint")
                + EnumChatFormatting.RESET;
        }));
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
