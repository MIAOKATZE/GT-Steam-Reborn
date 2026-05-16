package com.miaokatze.gtsr.common.machine;

import java.util.ArrayList;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.miaokatze.gtsr.common.machine.base.MTERemoteWorkerNode;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTUtility;

public class MTESingularityMinerNode extends MTERemoteWorkerNode {

    private static final Block MINING_PIPE_TIP_BLOCK = GTUtility
        .getBlockFromStack(GTModHandler.getIC2Item("miningPipeTip", 0));
    private static final int MINING_RADIUS = 8;
    private static final int FORTUNE = 0;

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
    private int mStatus = STATUS_OK;
    private boolean mLastAllowedToWork = true;
    private final ArrayList<ChunkCoordinates> mOrePositions = new ArrayList<>();
    private FakePlayer mFakePlayer;

    public MTESingularityMinerNode(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional, 4);
    }

    public MTESingularityMinerNode(String aName) {
        super(aName, 4);
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
    protected boolean isInputSlot(int aIndex) {
        return aIndex == 0 || aIndex == 1;
    }

    @Override
    protected int getInputSlotCount() {
        return 2;
    }

    @Override
    protected void addSlots(ModularPanel panel) {
        panel.child(
            new ItemSlot().slot(new ModularSlot(inventoryHandler, 0).slotGroup("node_inv"))
                .left(34)
                .top(24));

        panel.child(
            new ItemSlot().slot(new ModularSlot(inventoryHandler, 1).slotGroup("node_inv"))
                .left(52)
                .top(24));

        panel.child(
            new ItemSlot().slot(new ModularSlot(inventoryHandler, 2).slotGroup("node_inv"))
                .left(106)
                .top(24));

        panel.child(
            new ItemSlot().slot(new ModularSlot(inventoryHandler, 3).slotGroup("node_inv"))
                .left(124)
                .top(24));
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

        ArrayList<ItemStack> drops = block.getDrops(world, oreX, oreY, oreZ, meta, FORTUNE);

        GTUtility.eraseBlockByFakePlayer(getFakePlayer(aBaseMetaTileEntity), oreX, oreY, oreZ, false);

        if (drops != null) {
            for (ItemStack drop : drops) {
                if (drop != null && drop.getItem() != null) {
                    hub.pushNodeItemOutput(drop);
                }
            }
        }

        mStatus = STATUS_OK;
        mIsWorking = true;
        mWorkProgress = (mWorkProgress + 20) % WORK_CYCLE;
    }

    private void fillOreList(IGregTechTileEntity aBaseMetaTileEntity) {
        mOrePositions.clear();

        int x = aBaseMetaTileEntity.getXCoord();
        int y = aBaseMetaTileEntity.getYCoord();
        int z = aBaseMetaTileEntity.getZCoord();
        World world = aBaseMetaTileEntity.getWorld();
        int depthY = y + mTipDepth;

        for (int dz = -MINING_RADIUS; dz <= MINING_RADIUS; dz++) {
            for (int dx = -MINING_RADIUS; dx <= MINING_RADIUS; dx++) {
                int oreX = x + dx;
                int oreZ = z + dz;

                Block block = world.getBlock(oreX, depthY, oreZ);
                if (block == null || block == Blocks.air
                    || block == Blocks.bedrock
                    || GTUtility.getBlockHardnessAt(world, oreX, depthY, oreZ) < 0) {
                    continue;
                }

                int meta = world.getBlockMetadata(oreX, depthY, oreZ);
                if (GTUtility.isOre(block, meta)) {
                    mOrePositions.add(new ChunkCoordinates(dx, mTipDepth, dz));
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

        if (targetBlock != null && targetBlock != Blocks.air && targetBlock != Blocks.bedrock) {
            ArrayList<ItemStack> drops = targetBlock.getDrops(world, x, targetY, z, targetMeta, FORTUNE);
            MTESingularityDrillingHub hub = getBoundHub();
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
        mWorkProgress = (mWorkProgress + 20) % WORK_CYCLE;
        return true;
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (!aBaseMetaTileEntity.isServerSide()) return;

        boolean allowedToWork = aBaseMetaTileEntity.isAllowedToWork();

        if (mLastAllowedToWork && !allowedToWork) {
            mSoftDisabled = true;
            mDisabled = true;
            mForcedRetract = true;
            mRetractDone = false;
            mStatus = STATUS_SOFT_DISABLED;
        } else if (!mLastAllowedToWork && allowedToWork && mSoftDisabled) {
            mSoftDisabled = false;
            mDisabled = false;
            mRetractDone = false;
            mForcedRetract = false;
            mNeedsDescend = true;
            mOrePositions.clear();
            mStatus = STATUS_OK;
        }

        mLastAllowedToWork = allowedToWork;

        if (mDisabled && !mRetractDone && aTick % 5 == 0) {
            retractOnePipe(aBaseMetaTileEntity);
        }

        boolean shouldBeActive = !mDisabled && mStatus == STATUS_OK;
        if (shouldBeActive) {
            mIsWorking = true;
        }
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
        for (int i = 0; i < 2; i++) {
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
            for (int i = 0; i < 2; i++) {
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

        return new String[] { statusText, depthText, pipeText };
    }

    @Override
    protected ITexture getFrontOverlay() {
        return TextureFactory.of(Textures.BlockIcons.OVERLAY_FRONT_ORE_DRILL);
    }

    @Override
    protected ITexture getFrontOverlayActive() {
        return TextureFactory.of(Textures.BlockIcons.OVERLAY_FRONT_ORE_DRILL_ACTIVE);
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
        aNBT.setInteger("mStatus", mStatus);
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
        if (aNBT.hasKey("mStatus")) {
            mStatus = aNBT.getInteger("mStatus");
        }
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer, ForgeDirection side,
        float aX, float aY, float aZ) {
        if (!aBaseMetaTileEntity.isClientSide() && aPlayer.getHeldItem() == null && mHubX != 0) {
            GTUtility.sendChatToPlayer(
                aPlayer,
                StatCollector.translateToLocal(
                    "gtsr.binding.bound_to") + " Hub @ " + mHubX + ", " + mHubY + ", " + mHubZ);
        }
        return super.onRightclick(aBaseMetaTileEntity, aPlayer, side, aX, aY, aZ);
    }
}
