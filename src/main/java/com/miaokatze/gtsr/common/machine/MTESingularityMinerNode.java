package com.miaokatze.gtsr.common.machine;

import static gregtech.api.metatileentity.BaseTileEntity.TOOLTIP_DELAY;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.gtnewhorizons.modularui.common.widget.ButtonWidget;
import com.gtnewhorizons.modularui.common.widget.FakeSyncWidget;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;
import com.gtnewhorizons.modularui.common.widget.TextWidget;
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.machine.base.MTERemoteWorkerNode;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.enums.Textures;
import gregtech.api.gui.modularui.GTUITextures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.objects.ItemData;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTUtility;
import gregtech.common.ores.OreInfo;
import gregtech.common.ores.OreManager;

public class MTESingularityMinerNode extends MTERemoteWorkerNode {

    private static final Block MINING_PIPE_TIP_BLOCK = GTUtility
        .getBlockFromStack(GTModHandler.getIC2Item("miningPipeTip", 0));
    private static final int[] MINING_RADIUS = { 14, 24, 32, 48 }; // 28×28, 48×48, 64×64, 96×96
    // 时运统一取高值：贫瘠矿与普通矿等同，不再区分
    private static final int[] FORTUNE = { 5, 5, 6, 7 };
    private static final int[] MINER_WORK_CYCLE = { 160, 100, 60, 20 }; // 8s, 5s, 3s, 1s
    private static final int[] SINGULARITY_COST = { 0, 16, 32, 64 };
    private static final int[] MINER_NODE_STEAM_COST = { 2_000, 5_000, 12_000, 20_000 };

    private static final int STATUS_OK = 0;
    private static final int STATUS_NO_PIPE = 1;
    private static final int STATUS_NO_HUB = 2;
    private static final int STATUS_HUB_OFF = 3;
    private static final int STATUS_BEDROCK = 4;
    private static final int STATUS_SOFT_DISABLED = 5;
    private static final int STATUS_UNMINABLE = 6;

    private int mTipDepth = 0;
    private boolean mDisabled = false;
    private boolean mRetractDone = false;
    private boolean mNeedsDescend = true;
    private boolean mSoftDisabled = false;
    private boolean mForcedRetract = false;
    private boolean mHasStarted = false;
    private int mStatus = STATUS_OK;
    private boolean mLastAllowedToWork = true;
    private int mCycleTimer = 0;
    private int mMinerTier = 0; // 0=基础, 1=强化I, 2=强化II, 3=强化III
    // 粉碎矿模式：螺丝刀右击切换，开启后普通矿物掉落物转换为 3 倍数量的粉碎矿
    private boolean mCrushedMode = false;
    private final ArrayList<ChunkCoordinates> mOrePositions = new ArrayList<>();
    private FakePlayer mFakePlayer;

    public MTESingularityMinerNode(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, 3);
    }

    public MTESingularityMinerNode(String aName) {
        super(aName, 3);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTESingularityMinerNode(mName);
    }

    @Override
    public String getNodeType() {
        return "miner";
    }

    @Override
    public int getDrillTier() {
        return mMinerTier;
    }

    @Override
    public boolean isActivelyWorking() {
        return !mDisabled && mHasStarted;
    }

    @Override
    protected int getWorkChunkRadius() {
        // 采矿范围为以节点为中心 ±R 的正方形（R=MINING_RADIUS[tier]，14/24/32/48），
        // 换算为 chunk 半径需向上取整 (R+15)/16：T0=1, T1=2, T2=2, T3=3，
        // 保证工作范围跨区块边界时相邻区块也被加载
        return (MINING_RADIUS[mMinerTier] + 15) / 16;
    }

    @Override
    protected int maxProgresstimeInternal() {
        return MINER_WORK_CYCLE[mMinerTier];
    }

    @Override
    public int getProgresstime() {
        return mCycleTimer;
    }

    @Override
    public String[] getDescription() {
        return new String[] {
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.tooltip.miner_node.desc") };
    }

    @Override
    public void addAdditionalTooltipInformation(ItemStack stack, List<String> tooltip) {
        tooltip.add(
            EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.miner_node.range")
                + EnumChatFormatting.GOLD
                + StatCollector.translateToLocal("gtsr.tooltip.miner_node.range_base"));
        tooltip.add(
            EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.miner_node.fortune")
                + EnumChatFormatting.GOLD
                + StatCollector.translateToLocal("gtsr.tooltip.miner_node.fortune_base"));
        tooltip.add(
            EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.shared.work_cycle")
                + EnumChatFormatting.GREEN
                + StatCollector.translateToLocal("gtsr.tooltip.miner_node.work_cycle_base"));
        tooltip.add(
            EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.tooltip.miner_node.steam_cost")
                + EnumChatFormatting.GOLD
                + StatCollector.translateToLocal("gtsr.tooltip.miner_node.steam_cost_base"));
        tooltip.add(EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.tooltip.shared.singularity_cost"));
        tooltip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.tooltip.miner_node.requires_pipe"));
        tooltip
            .add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.tooltip.miner_node.crushed_mode_hint"));
        tooltip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.tooltip.shared.node_bind_hint"));
        tooltip
            .add(EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.tooltip.miner_node.upgrade_title"));
        tooltip.add(EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.tooltip.miner_node.upgrade_desc"));
        tooltip.add(
            EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.tooltip.added_by")
                + " "
                + EnumChatFormatting.AQUA
                + "GT"
                + EnumChatFormatting.GREEN
                + "-"
                + EnumChatFormatting.GOLD
                + "Steam"
                + EnumChatFormatting.RED
                + "-"
                + EnumChatFormatting.BLUE
                + "Reborn");
    }

    @Override
    protected boolean isInputSlot(int aIndex) {
        return aIndex >= 0 && aIndex < 3;
    }

    @Override
    protected int getInputSlotCount() {
        return 3;
    }

    @Override
    public void addUIWidgets(ModularWindow.Builder builder, UIBuildContext buildContext) {
        builder.widget(new SlotWidget(inventoryHandler, 0).setPos(52, 24));
        builder.widget(new SlotWidget(inventoryHandler, 1).setPos(70, 24));
        builder.widget(new SlotWidget(inventoryHandler, 2).setPos(88, 24));
        // 升级按钮：点击后从玩家背包消耗对应等级钻井场物品与奇点进行升级
        builder.widget(new ButtonWidget().setOnClick((clickData, widget) -> {
            if (clickData.mouseButton == 0 && !widget.isClient()) {
                tryUpgrade(buildContext.getPlayer());
            }
        })
            .setPlayClickSound(true)
            .setBackground(GTUITextures.BUTTON_STANDARD, GTUITextures.OVERLAY_BUTTON_ARROW_GREEN_UP)
            .dynamicTooltip(this::getUpgradeTooltip)
            .setTooltipShowUpDelay(TOOLTIP_DELAY)
            .setPos(150, 24)
            .setSize(18, 18));
        addDisplayTexts(builder);
    }

    @Override
    protected void addDisplayTexts(ModularWindow.Builder builder) {
        builder.widget(new TextWidget().setStringSupplier(() -> {
            switch (mStatus) {
                case STATUS_OK:
                    return EnumChatFormatting.GREEN + StatCollector.translateToLocal("gtsr.node.status.ok");
                case STATUS_NO_PIPE:
                    return EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.node.status.no_pipe");
                case STATUS_NO_HUB:
                    return EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.node.status.no_hub");
                case STATUS_HUB_OFF:
                    return EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.node.status.hub_off");
                case STATUS_BEDROCK:
                    return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.node.status.bedrock");
                case STATUS_SOFT_DISABLED:
                    return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.node.status.soft_disabled");
                case STATUS_UNMINABLE:
                    return EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.node.status.unminable");
                default:
                    return EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.node.status.unknown");
            }
        })
            .setDefaultColor(0xFFFFFFFF)
            .setPos(10, 52));

        builder.widget(
            new TextWidget().setStringSupplier(
                () -> EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.node.depth") + ": " + mTipDepth)
                .setDefaultColor(0xFFFFFFFF)
                .setPos(10, 64));

        builder.widget(
            new TextWidget()
                .setStringSupplier(
                    () -> EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.node.pipes_retracted")
                        + ": "
                        + (mRetractDone ? StatCollector.translateToLocal("gtsr.node.yes")
                            : StatCollector.translateToLocal("gtsr.node.no")))
                .setDefaultColor(0xFFFFFFFF)
                .setPos(10, 76));

        builder.widget(
            new TextWidget()
                .setStringSupplier(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.miner_node.tier")
                        + " "
                        + EnumChatFormatting.AQUA
                        + (mMinerTier == 0 ? StatCollector.translateToLocal("gtsr.gui.miner_node.base")
                            : StatCollector.translateToLocal("gtsr.gui.miner_node.enhanced") + toRoman(mMinerTier)))
                .setDefaultColor(0xFFFFFFFF)
                .setPos(10, 88));

        // 粉碎矿模式状态显示（与等级同行右侧，玩家背包区从 y=99 开始，避免重叠）
        builder.widget(
            new TextWidget()
                .setStringSupplier(
                    () -> EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.gui.miner_node.crushed_mode")
                        + ": "
                        + (mCrushedMode
                            ? EnumChatFormatting.GREEN
                                + StatCollector.translateToLocal("gtsr.gui.miner_node.crushed_mode.on")
                            : EnumChatFormatting.GRAY
                                + StatCollector.translateToLocal("gtsr.gui.miner_node.crushed_mode.off")))
                .setDefaultColor(0xFFFFFFFF)
                .setPos(90, 88));

        builder.widget(new FakeSyncWidget.IntegerSyncer(() -> mStatus, val -> mStatus = val));
        builder.widget(new FakeSyncWidget.IntegerSyncer(() -> mTipDepth, val -> mTipDepth = val));
        builder.widget(new FakeSyncWidget.BooleanSyncer(() -> mRetractDone, val -> mRetractDone = val));
        builder.widget(new FakeSyncWidget.IntegerSyncer(() -> mMinerTier, val -> mMinerTier = val));
        builder.widget(new FakeSyncWidget.BooleanSyncer(() -> mCrushedMode, val -> mCrushedMode = val));
    }

    private FakePlayer getFakePlayer(IGregTechTileEntity aBaseMetaTileEntity) {
        if (mFakePlayer == null) {
            mFakePlayer = GTUtility.getFakePlayer(aBaseMetaTileEntity);
        }
        if (mFakePlayer != null) {
            mFakePlayer.setWorld(aBaseMetaTileEntity.getWorld());
            mFakePlayer.setPosition(
                aBaseMetaTileEntity.getXCoord(),
                aBaseMetaTileEntity.getYCoord(),
                aBaseMetaTileEntity.getZCoord());
        }
        return mFakePlayer;
    }

    @Override
    public void doWork(IGregTechTileEntity aBaseMetaTileEntity) {
        if (mDisabled) {
            mStatus = mSoftDisabled ? STATUS_SOFT_DISABLED : STATUS_BEDROCK;
            return;
        }

        MTESingularityDrillingHub hub = getBoundHub();
        if (hub == null) {
            mStatus = STATUS_NO_HUB;
            return;
        }

        if (!hub.isMachineRunning()) {
            mStatus = STATUS_HUB_OFF;
            return;
        }

        World world = aBaseMetaTileEntity.getWorld();

        if (mNeedsDescend) {
            if (!tryDescendPipe(aBaseMetaTileEntity)) {
                return;
            }
            mNeedsDescend = false;
            fillOreList(aBaseMetaTileEntity);

            if (mOrePositions.isEmpty()) {
                mNeedsDescend = true;
                return;
            }
        }

        if (mOrePositions.isEmpty()) {
            fillOreList(aBaseMetaTileEntity);

            if (mOrePositions.isEmpty()) {
                mNeedsDescend = true;
                return;
            }
        }

        int x = aBaseMetaTileEntity.getXCoord();
        int y = aBaseMetaTileEntity.getYCoord();
        int z = aBaseMetaTileEntity.getZCoord();
        ChunkCoordinates orePos = mOrePositions.remove(0);
        int oreX = x + orePos.posX;
        int oreY = y + orePos.posY;
        int oreZ = z + orePos.posZ;

        Block block = world.getBlock(oreX, oreY, oreZ);
        if (block == null || block == Blocks.air
            || block == Blocks.bedrock
            || GTUtility.getBlockHardnessAt(world, oreX, oreY, oreZ) < 0) {
            return;
        }

        int meta = world.getBlockMetadata(oreX, oreY, oreZ);

        if (!GTUtility.eraseBlockByFakePlayer(getFakePlayer(aBaseMetaTileEntity), oreX, oreY, oreZ, true)) {
            mDisabled = true;
            mStatus = STATUS_UNMINABLE;
            return;
        }

        // Fortune: force isNatural=true for GT ores to bypass the adapter's fortune=0 restriction.
        // In GTNH, world ores have isNatural=false (meta 0-7999 range) due to how
        // TileEntityReplacementManager handles chunk loading. Since our machine mines
        // naturally generated ores, we force isNatural=true before getting drops.
        // Non-GT ores (vanilla/other mods) use block.getDrops() with fortune directly.
        // 时运统一取高值（贫瘠矿与普通矿等同）
        int fortune = FORTUNE[mMinerTier];

        ArrayList<ItemStack> drops;
        try (OreInfo<?> info = OreManager.getOreInfo(block, meta)) {
            if (info != null) {
                boolean origNatural = info.isNatural;
                info.isNatural = true;
                drops = OreManager.getAdapter(info)
                    .getOreDrops(ThreadLocalRandom.current(), info, false, fortune);
                info.isNatural = origNatural;
            } else {
                drops = block.getDrops(world, oreX, oreY, oreZ, meta, fortune);
            }
        }

        // Remove the block from the world
        world.setBlockToAir(oreX, oreY, oreZ);

        // 粉碎矿模式：将矿石掉落物替换为 3 倍数量的对应粉碎矿
        if (mCrushedMode && drops != null) {
            applyCrushedMode(drops);
        }

        if (drops != null) {
            for (ItemStack drop : drops) {
                if (drop != null && drop.getItem() != null) {
                    hub.pushNodeItemOutput(drop);
                }
            }
        }

        mStatus = STATUS_OK;
        mHasStarted = true;
        mIsWorking = true;
        mWorkProgress = (mWorkProgress + 20) % MINER_WORK_CYCLE[mMinerTier];
    }

    private void fillOreList(IGregTechTileEntity aBaseMetaTileEntity) {
        mOrePositions.clear();

        int x = aBaseMetaTileEntity.getXCoord();
        int y = aBaseMetaTileEntity.getYCoord();
        int z = aBaseMetaTileEntity.getZCoord();
        World world = aBaseMetaTileEntity.getWorld();
        int radius = MINING_RADIUS[mMinerTier];

        // Scan all layers from the node position down to the pipe tip
        for (int dy = mTipDepth; dy <= 0; dy++) {
            int scanY = y + dy;
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    int oreX = x + dx;
                    int oreZ = z + dz;

                    Block block = world.getBlock(oreX, scanY, oreZ);
                    if (block == null || block == Blocks.air
                        || block == Blocks.bedrock
                        || GTUtility.getBlockHardnessAt(world, oreX, scanY, oreZ) < 0) {
                        continue;
                    }

                    int meta = world.getBlockMetadata(oreX, scanY, oreZ);

                    // Two-tier ore detection:
                    // 1. GTUtility.isOre() - matches GT5U's MTEMiner behavior.
                    // Finds natural GT ores and Ore Dictionary ores (vanilla, other mods).
                    // Returns false for non-natural GT ores (player-placed, meta < 8000).
                    // 2. OreManager.getOreInfo() fallback - catches non-natural GT ores
                    // that GTUtility.isOre() misses (player-placed GT ores).
                    boolean isOreBlock = GTUtility.isOre(block, meta);
                    if (!isOreBlock) {
                        try (OreInfo<?> info = OreManager.getOreInfo(block, meta)) {
                            if (info != null) {
                                isOreBlock = true;
                            }
                        }
                    }

                    if (isOreBlock) {
                        mOrePositions.add(new ChunkCoordinates(dx, dy, dz));
                    }
                }
            }
        }
    }

    private boolean tryDescendPipe(IGregTechTileEntity aBaseMetaTileEntity) {
        int x = aBaseMetaTileEntity.getXCoord();
        int y = aBaseMetaTileEntity.getYCoord();
        int z = aBaseMetaTileEntity.getZCoord();
        World world = aBaseMetaTileEntity.getWorld();

        int targetY = y + mTipDepth - 1;
        if (targetY < 0) {
            mDisabled = true;
            mStatus = STATUS_BEDROCK;
            return false;
        }

        boolean isBedrock = GTUtility.getBlockHardnessAt(world, x, targetY, z) < 0;
        if (isBedrock) {
            mDisabled = true;
            mStatus = STATUS_BEDROCK;
            return false;
        }

        Block targetBlock = world.getBlock(x, targetY, z);
        int targetMeta = world.getBlockMetadata(x, targetY, z);

        if (!GTUtility.eraseBlockByFakePlayer(getFakePlayer(aBaseMetaTileEntity), x, targetY, z, true)) {
            mDisabled = true;
            mStatus = STATUS_UNMINABLE;
            return false;
        }

        ItemStack consumed = consumeMiningPipeFromInputs();
        if (consumed == null) {
            mStatus = STATUS_NO_PIPE;
            return false;
        }

        if (mTipDepth < 0) {
            int prevTipY = y + mTipDepth;
            if (world.getBlock(x, prevTipY, z) == MINING_PIPE_TIP_BLOCK) {
                Block pipeBlock = GTUtility.getBlockFromItem(consumed.getItem());
                if (pipeBlock != null && pipeBlock != Blocks.air) {
                    world.setBlock(x, prevTipY, z, pipeBlock);
                }
            }
        }

        // 时运统一取高值（贫瘠矿与普通矿等同）
        int fortune = FORTUNE[mMinerTier];
        if (targetBlock != null && targetBlock != Blocks.air && targetBlock != Blocks.bedrock) {
            ArrayList<ItemStack> drops;
            try (OreInfo<?> info = OreManager.getOreInfo(targetBlock, targetMeta)) {
                if (info != null) {
                    boolean origNatural = info.isNatural;
                    info.isNatural = true;
                    drops = OreManager.getAdapter(info)
                        .getOreDrops(ThreadLocalRandom.current(), info, false, fortune);
                    info.isNatural = origNatural;
                } else {
                    drops = targetBlock.getDrops(world, x, targetY, z, targetMeta, fortune);
                }
            }
            MTESingularityDrillingHub hub = getBoundHub();
            // 粉碎矿模式：将矿石掉落物替换为 3 倍数量的对应粉碎矿
            if (mCrushedMode && drops != null) {
                applyCrushedMode(drops);
            }
            if (drops != null && hub != null) {
                for (ItemStack drop : drops) {
                    if (drop != null && drop.getItem() != null) {
                        hub.pushNodeItemOutput(drop);
                    }
                }
            }
        }

        GTUtility.eraseBlockByFakePlayer(getFakePlayer(aBaseMetaTileEntity), x, targetY, z, false);
        GTUtility
            .setBlockByFakePlayer(getFakePlayer(aBaseMetaTileEntity), x, targetY, z, MINING_PIPE_TIP_BLOCK, 0, false);

        mTipDepth--;
        mStatus = STATUS_OK;
        mIsWorking = true;
        mWorkProgress = (mWorkProgress + 20) % MINER_WORK_CYCLE[mMinerTier];
        return true;
    }

    /**
     * 粉碎矿模式：将掉落物中的矿石类物品替换为对应粉碎矿（crushed），数量 = 原数量 × 3。
     * 现有代码在 getOreDrops(..., fortune) 阶段已应用时运加成，故 3 倍基准直接乘在已含时运的数量上
     * （对应 GT5U 采矿场 "3x crushed vs normal" 的语义，时运不重复追加）。
     * 已是粉碎/粉末/宝石等加工形态、或无对应粉碎矿（非 GT 矿字典物品）的掉落物保持不变。
     */
    private void applyCrushedMode(ArrayList<ItemStack> drops) {
        for (int i = 0; i < drops.size(); i++) {
            ItemStack drop = drops.get(i);
            if (drop == null || drop.getItem() == null) continue;

            ItemData itemData = GTOreDictUnificator.getItemData(drop);
            if (itemData == null || itemData.mMaterial == null || itemData.mMaterial.mMaterial == null) continue;

            // 已是粉碎/粉/宝石形态的掉落物不再转换（贫瘠小矿的掉落物即属此类）
            OrePrefixes prefix = itemData.mPrefix;
            if (prefix == OrePrefixes.crushed || prefix == OrePrefixes.crushedCentrifuged
                || prefix == OrePrefixes.crushedPurified
                || prefix == OrePrefixes.dustImpure
                || prefix == OrePrefixes.dustPure
                || prefix == OrePrefixes.dustRefined
                || prefix == OrePrefixes.dust
                || prefix == OrePrefixes.gem
                || prefix == OrePrefixes.gemChipped
                || prefix == OrePrefixes.gemExquisite
                || prefix == OrePrefixes.gemFlawed
                || prefix == OrePrefixes.gemFlawless) {
                continue;
            }

            // 查找该材料对应的粉碎矿形态；查不到（无粉碎形态的宝石类材料、非 GT 矿）则保持原样
            ItemStack crushed = GTOreDictUnificator.get(OrePrefixes.crushed, itemData.mMaterial.mMaterial, 1);
            if (crushed == null) continue;

            crushed.stackSize = drop.stackSize * 3;
            drops.set(i, crushed);
        }
    }

    @Override
    public void onScrewdriverRightClick(ForgeDirection side, EntityPlayer aPlayer, float aX, float aY, float aZ,
        ItemStack aTool) {
        // 仅服务端切换，避免客户端重复切换导致状态不同步
        if (!getBaseMetaTileEntity().isServerSide()) return;
        mCrushedMode = !mCrushedMode;
        GTUtility.sendChatToPlayer(
            aPlayer,
            StatCollector.translateToLocal(
                mCrushedMode ? "gtsr.node.miner.crushed_mode.on" : "gtsr.node.miner.crushed_mode.off"));
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        if (!aBaseMetaTileEntity.isServerSide()) return;

        if (!mRegistered && isBound()) {
            mRegistered = registerWithHub(aBaseMetaTileEntity);
        }

        // 本类未调用 super.onPostTick（避免基类 setActive/mIsWorking 逻辑与本类状态机冲突），
        // 故显式调用基类区块加载维护，按当前等级范围申请/释放工作区块
        updateChunkLoading(aBaseMetaTileEntity);

        boolean currentlyAllowed = aBaseMetaTileEntity.isAllowedToWork();
        if (currentlyAllowed && !mLastAllowedToWork) {
            mSoftDisabled = false;
            mDisabled = false;
            mHasStarted = false;
            mRetractDone = false;
            mForcedRetract = false;
            mNeedsDescend = true;
            mOrePositions.clear();
            mStatus = STATUS_OK;
            mCycleTimer = 0;
        } else if (!currentlyAllowed && mLastAllowedToWork) {
            mSoftDisabled = true;
            mDisabled = true;
            mHasStarted = false;
            mForcedRetract = true;
            mRetractDone = false;
            mStatus = STATUS_SOFT_DISABLED;
        }
        mLastAllowedToWork = currentlyAllowed;

        if (mDisabled && !mRetractDone) {
            retractOnePipe(aBaseMetaTileEntity);
        } else if (!mDisabled) {
            mCycleTimer++;
            mWorkProgress = mCycleTimer;
            if (mCycleTimer >= MINER_WORK_CYCLE[mMinerTier]) {
                mCycleTimer = 0;
                mIsWorking = true;
                doWork(aBaseMetaTileEntity);
            }
        }

        boolean shouldBeActive = mStatus == STATUS_OK && mHasStarted;
        aBaseMetaTileEntity.setActive(shouldBeActive);
    }

    private void retractOnePipe(IGregTechTileEntity aBaseMetaTileEntity) {
        if (mTipDepth == 0) {
            mRetractDone = true;
            mForcedRetract = false;
            return;
        }

        int x = aBaseMetaTileEntity.getXCoord();
        int y = aBaseMetaTileEntity.getYCoord();
        int z = aBaseMetaTileEntity.getZCoord();
        World world = aBaseMetaTileEntity.getWorld();
        int actualY = y + mTipDepth;

        Block currentBlock = world.getBlock(x, actualY, z);
        if (currentBlock != MINING_PIPE_TIP_BLOCK) {
            mRetractDone = true;
            mForcedRetract = false;
            return;
        }

        boolean canRecover = false;
        int inputCount = getInputSlotCount();
        for (int i = 0; i < inputCount; i++) {
            ItemStack slot = mInventory[i];
            if (slot == null || slot.stackSize < slot.getMaxStackSize()) {
                canRecover = true;
                break;
            }
        }
        if (!canRecover) {
            return;
        }

        if (mTipDepth < -1) {
            world.setBlock(x, actualY + 1, z, MINING_PIPE_TIP_BLOCK);
        }

        world.setBlockToAir(x, actualY, z);

        ItemStack recoveredPipe = GTModHandler.getIC2Item("miningPipe", 0);
        if (recoveredPipe != null) {
            ItemStack pipe = recoveredPipe.copy();
            pipe.stackSize = 1;
            for (int i = 0; i < inputCount; i++) {
                if (mInventory[i] == null) {
                    mInventory[i] = pipe;
                    break;
                } else if (isMiningPipe(mInventory[i]) && mInventory[i].stackSize < mInventory[i].getMaxStackSize()) {
                    mInventory[i].stackSize++;
                    break;
                }
            }
        }

        mTipDepth++;
    }

    @Override
    public String[] getInfoData() {
        String statusText;
        switch (mStatus) {
            case STATUS_OK:
                statusText = EnumChatFormatting.GREEN + StatCollector.translateToLocal("gtsr.node.status.ok");
                break;
            case STATUS_NO_PIPE:
                statusText = EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.node.status.no_pipe");
                break;
            case STATUS_NO_HUB:
                statusText = EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.node.status.no_hub");
                break;
            case STATUS_HUB_OFF:
                statusText = EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.node.status.hub_off");
                break;
            case STATUS_BEDROCK:
                statusText = EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.node.status.bedrock");
                break;
            case STATUS_SOFT_DISABLED:
                statusText = EnumChatFormatting.YELLOW
                    + StatCollector.translateToLocal("gtsr.node.status.soft_disabled");
                break;
            case STATUS_UNMINABLE:
                statusText = EnumChatFormatting.RED + StatCollector.translateToLocal("gtsr.node.status.unminable");
                break;
            default:
                statusText = EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.node.status.unknown");
                break;
        }

        String depthText = EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.node.depth")
            + ": "
            + mTipDepth;

        String pipeText = EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.node.pipes_retracted")
            + ": "
            + (mRetractDone ? StatCollector.translateToLocal("gtsr.node.yes")
                : StatCollector.translateToLocal("gtsr.node.no"));

        String tierText = EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.miner_node.tier")
            + ": "
            + EnumChatFormatting.AQUA
            + (mMinerTier == 0 ? StatCollector.translateToLocal("gtsr.gui.miner_node.base")
                : StatCollector.translateToLocal("gtsr.gui.miner_node.enhanced") + toRoman(mMinerTier));

        return new String[] { statusText, depthText, pipeText, tierText };
    }

    private static IIconContainer OVERLAY_OFF;
    private static IIconContainer OVERLAY_ON;

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister aBlockIconRegister) {
        OVERLAY_OFF = Textures.BlockIcons.custom("gtsr:MTESingularityMinerNode_OFF");
        OVERLAY_ON = Textures.BlockIcons.custom("gtsr:MTESingularityMinerNode_ON");
        super.registerIcons(aBlockIconRegister);
    }

    @Override
    protected ITexture getFrontOverlay() {
        return TextureFactory.of(OVERLAY_OFF);
    }

    @Override
    protected ITexture getFrontOverlayActive() {
        return TextureFactory.of(OVERLAY_ON);
    }

    @Override
    public void saveNBTData(net.minecraft.nbt.NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setInteger("mTipDepth", mTipDepth);
        aNBT.setBoolean("mDisabled", mDisabled);
        aNBT.setBoolean("mRetractDone", mRetractDone);
        aNBT.setBoolean("mNeedsDescend", mNeedsDescend);
        aNBT.setBoolean("mSoftDisabled", mSoftDisabled);
        aNBT.setBoolean("mForcedRetract", mForcedRetract);
        aNBT.setBoolean("mHasStarted", mHasStarted);
        aNBT.setInteger("mStatus", mStatus);
        aNBT.setInteger("mCycleTimer", mCycleTimer);
        aNBT.setInteger("mMinerTier", mMinerTier);
        aNBT.setBoolean("mCrushedMode", mCrushedMode);
    }

    @Override
    public void loadNBTData(net.minecraft.nbt.NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mTipDepth = aNBT.getInteger("mTipDepth");
        mDisabled = aNBT.getBoolean("mDisabled");
        mRetractDone = aNBT.getBoolean("mRetractDone");
        mNeedsDescend = aNBT.getBoolean("mNeedsDescend");
        if (aNBT.hasKey("mSoftDisabled")) {
            mSoftDisabled = aNBT.getBoolean("mSoftDisabled");
        }
        if (aNBT.hasKey("mForcedRetract")) {
            mForcedRetract = aNBT.getBoolean("mForcedRetract");
        }
        if (aNBT.hasKey("mHasStarted")) {
            mHasStarted = aNBT.getBoolean("mHasStarted");
        }
        if (aNBT.hasKey("mStatus")) {
            mStatus = aNBT.getInteger("mStatus");
        }
        if (aNBT.hasKey("mCycleTimer")) {
            mCycleTimer = aNBT.getInteger("mCycleTimer");
        }
        if (aNBT.hasKey("mMinerTier")) {
            mMinerTier = aNBT.getInteger("mMinerTier");
        }
        if (aNBT.hasKey("mCrushedMode")) {
            mCrushedMode = aNBT.getBoolean("mCrushedMode");
        }
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer, ForgeDirection side,
        float aX, float aY, float aZ) {
        if (!aBaseMetaTileEntity.isServerSide()) return true;

        ItemStack held = aPlayer.getHeldItem();

        // Default: show binding info
        if (held == null && mHubX != 0) {
            GTUtility.sendChatToPlayer(
                aPlayer,
                StatCollector.translateToLocal(
                    "gtsr.binding.bound_to") + " Hub @ " + mHubX + ", " + mHubY + ", " + mHubZ);
        }
        return super.onRightclick(aBaseMetaTileEntity, aPlayer, side, aX, aY, aZ);
    }

    /**
     * 节点是否已完全停止：已禁止工作且采矿管道全部收回（mTipDepth==0，含从未下管）。
     * 仅完全停止的节点允许从枢纽状态 UI 快捷回收。
     */
    @Override
    public boolean isFullyRetracted() {
        return super.isFullyRetracted() && mTipDepth == 0;
    }

    /**
     * 尝试升级节点：从玩家背包查找并消耗对应等级的矿石钻井场物品与蒸汽纠缠奇点。
     * 升级成功返回 true 并发送聊天提示；失败时发送原因提示并返回 false。
     * public：枢纽状态 UI 的远程升级按钮通过基类引用多态调用。
     */
    @Override
    public boolean tryUpgrade(EntityPlayer player) {
        // 已满级
        if (mMinerTier >= 3) {
            GTUtility.sendChatToPlayer(player, StatCollector.translateToLocal("gtsr.gui.miner_node.max_tier"));
            return false;
        }
        int targetTier = mMinerTier + 1;
        // 从背包查找目标等级对应的矿石钻井场物品（升级需逐级进行，目标等级固定为当前+1）
        int drillSlot = findDrillItemSlot(player, targetTier);
        if (drillSlot < 0) {
            GTUtility.sendChatToPlayer(player, StatCollector.translateToLocal("gtsr.gui.miner_node.need_drill"));
            return false;
        }
        int cost = SINGULARITY_COST[targetTier];
        if (!consumeSingularityItems(player, cost)) {
            GTUtility.sendChatToPlayer(
                player,
                StatCollector.translateToLocal("gtsr.gui.miner_node.need_singularity") + " " + cost);
            return false;
        }
        ItemStack drillStack = player.inventory.mainInventory[drillSlot];
        drillStack.stackSize--;
        if (drillStack.stackSize <= 0) player.inventory.mainInventory[drillSlot] = null;
        mMinerTier = targetTier;
        player.inventoryContainer.detectAndSendChanges();
        GTUtility.sendChatToPlayer(
            player,
            StatCollector.translateToLocal("gtsr.gui.miner_node.upgrade_success") + toRoman(targetTier));
        // 等级已变化：释放旧范围区块加载 ticket，下一 tick 按新范围重新申请
        onTierChanged();
        return true;
    }

    /**
     * 在玩家背包中查找目标等级对应的矿石钻井场物品，返回槽位下标，未找到返回 -1。
     */
    private int findDrillItemSlot(EntityPlayer player, int targetTier) {
        for (int i = 0; i < player.inventory.mainInventory.length; i++) {
            ItemStack stack = player.inventory.mainInventory[i];
            if (stack == null) continue;
            boolean match = switch (targetTier) {
                case 1 -> ItemList.OreDrill1.isStackEqual(stack, false, true);
                case 2 -> ItemList.OreDrill2.isStackEqual(stack, false, true);
                case 3 -> ItemList.OreDrill3.isStackEqual(stack, false, true);
                default -> false;
            };
            if (match) return i;
        }
        return -1;
    }

    /**
     * 获取目标等级对应的矿石钻井场物品（用于升级按钮 tooltip 展示）。
     */
    private ItemStack getDrillItemForTier(int tier) {
        return switch (tier) {
            case 1 -> ItemList.OreDrill1.get(1);
            case 2 -> ItemList.OreDrill2.get(1);
            case 3 -> ItemList.OreDrill3.get(1);
            default -> null;
        };
    }

    /**
     * 升级按钮的动态 tooltip：说明下一级所需钻井场物品与奇点数量。
     */
    private List<String> getUpgradeTooltip() {
        List<String> tips = new ArrayList<>();
        tips.add(
            EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.gui.node_upgrade.tooltip.title")
                + EnumChatFormatting.RESET);
        if (mMinerTier >= 3) {
            tips.add(EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.node_upgrade.tooltip.max"));
            return tips;
        }
        int next = mMinerTier + 1;
        tips.add(
            EnumChatFormatting.GREEN + StatCollector.translateToLocal("gtsr.gui.node_upgrade.tooltip.next")
                + " "
                + StatCollector.translateToLocal("gtsr.gui.miner_node.enhanced")
                + toRoman(next));
        ItemStack drillStack = getDrillItemForTier(next);
        if (drillStack != null) {
            tips.add(
                EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.gui.node_upgrade.tooltip.drill")
                    + " "
                    + drillStack.getDisplayName());
        }
        tips.add(
            EnumChatFormatting.GOLD + StatCollector.translateToLocal("gtsr.gui.node_upgrade.tooltip.singularity")
                + " "
                + SINGULARITY_COST[next]);
        return tips;
    }

    private String toRoman(int num) {
        return switch (num) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> String.valueOf(num);
        };
    }

    /**
     * Determines if the given block+meta is a natural ore (eligible for fortune).
     * For GT ores, checks OreInfo.isNatural (natural ores have meta >= 8000).
     * For non-GT ores (vanilla/other mods), falls back to GTUtility.isOre() via Ore Dictionary.
     */
    private boolean isNaturalOre(Block block, int meta) {
        try (OreInfo<?> info = OreManager.getOreInfo(block, meta)) {
            if (info != null) return info.isNatural;
        }
        // Non-GT ores (vanilla iron/gold/coal, other mod ores) are always "natural"
        return GTUtility.isOre(block, meta);
    }

    private boolean consumeSingularityItems(EntityPlayer player, int count) {
        int found = 0;
        for (ItemStack stack : player.inventory.mainInventory) {
            if (stack != null && GTSRItemList.SteamEntangledSingularity.isStackEqual(stack, false, true)) {
                found += stack.stackSize;
            }
        }
        if (found < count) return false;

        int remaining = count;
        for (int i = 0; i < player.inventory.mainInventory.length && remaining > 0; i++) {
            ItemStack stack = player.inventory.mainInventory[i];
            if (stack != null && GTSRItemList.SteamEntangledSingularity.isStackEqual(stack, false, true)) {
                int toConsume = Math.min(remaining, stack.stackSize);
                stack.stackSize -= toConsume;
                remaining -= toConsume;
                if (stack.stackSize <= 0) {
                    player.inventory.mainInventory[i] = null;
                }
            }
        }
        player.inventoryContainer.detectAndSendChanges();
        return true;
    }
}
