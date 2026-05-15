package com.miaokatze.gtsr.common.machine;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.miaokatze.gtsr.common.machine.base.MTERemoteWorkerNode;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.modularui2.GTGuis;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTUtility;

public class MTESingularityMinerNode extends MTERemoteWorkerNode {

    private int mDepth = 0;
    private int mBaseY = -1;
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
    public boolean allowPullStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection side,
        ItemStack aStack) {
        return aIndex > 1;
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

    private ItemStack consumeMiningPipe() {
        for (int i = 0; i <= 1; i++) {
            ItemStack stack = mInventory[i];
            if (isMiningPipe(stack) && stack.stackSize > 0) {
                stack.stackSize--;
                if (stack.stackSize <= 0) {
                    mInventory[i] = null;
                }
                return stack;
            }
        }
        return null;
    }

    @Override
    public void doWork(IGregTechTileEntity aBaseMetaTileEntity) {
        MTESingularityDrillingHub hub = getBoundHub();
        if (hub == null) {
            return;
        }

        ItemStack consumed = consumeMiningPipe();
        if (consumed == null) {
            return;
        }

        Block pipeBlock = GTUtility.getBlockFromItem(consumed.getItem());
        if (pipeBlock == null || pipeBlock == Blocks.air) {
            return;
        }

        if (mBaseY < 0) {
            mBaseY = aBaseMetaTileEntity.getYCoord();
        }

        int mineY = mBaseY - 1 - mDepth;
        if (mineY <= 1) {
            mDepth = 0;
            mineY = mBaseY - 1;
        }

        World world = aBaseMetaTileEntity.getWorld();
        int x = aBaseMetaTileEntity.getXCoord();
        int z = aBaseMetaTileEntity.getZCoord();

        Block block = world.getBlock(x, mineY, z);
        int meta = world.getBlockMetadata(x, mineY, z);

        ItemStack oreDrop = null;
        if (block != null && block != Blocks.air && block != Blocks.bedrock) {
            oreDrop = new ItemStack(block, 1, meta);
            GTUtility.eraseBlockByFakePlayer(getFakePlayer(aBaseMetaTileEntity), x, mineY, z, false);
        } else if (block == Blocks.bedrock) {
            mDepth = 0;
            mIsWorking = true;
            mWorkProgress = 0;
            return;
        }

        if (oreDrop != null && oreDrop.getItem() != null) {
            hub.pushNodeItemOutput(oreDrop);
        }

        GTUtility.setBlockByFakePlayer(getFakePlayer(aBaseMetaTileEntity), x, mineY, z, pipeBlock, 0, false);

        mDepth++;
        mIsWorking = true;
        mWorkProgress = (mWorkProgress + 20) % WORK_CYCLE;
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
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings uiSettings) {
        syncManager.registerSlotGroup("node_inv", 4);
        ModularPanel panel = GTGuis.mteTemplatePanelBuilder(this, data, syncManager, uiSettings)
            .doesAddCoverTabs(false)
            .build();

        panel.child(
            new ItemSlot().slot(new ModularSlot(inventoryHandler, 0).slotGroup("node_inv"))
                .left(62)
                .top(22));

        panel.child(
            new ItemSlot().slot(new ModularSlot(inventoryHandler, 1).slotGroup("node_inv"))
                .left(62)
                .top(40));

        panel.child(
            new ItemSlot().slot(new ModularSlot(inventoryHandler, 2).slotGroup("node_inv"))
                .left(116)
                .top(22));

        panel.child(
            new ItemSlot().slot(new ModularSlot(inventoryHandler, 3).slotGroup("node_inv"))
                .left(116)
                .top(40));

        return panel;
    }

    @Override
    public void saveNBTData(net.minecraft.nbt.NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setInteger("mDepth", mDepth);
        aNBT.setInteger("mBaseY", mBaseY);
    }

    @Override
    public void loadNBTData(net.minecraft.nbt.NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mDepth = aNBT.getInteger("mDepth");
        mBaseY = aNBT.getInteger("mBaseY");
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