package com.miaokatze.gtsr.common.machine.base;

import static net.minecraft.util.StatCollector.translateToLocal;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.ForgeDirection;

import com.miaokatze.gtsr.common.machine.MTESingularityDrillingHub;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.HarvestTool;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTUtility;

public abstract class MTERemoteWorkerNode extends MetaTileEntity {

    protected int mHubX = 0;
    protected int mHubY = 0;
    protected int mHubZ = 0;
    protected int mHubDim = 0;
    protected String mHubType = "";
    protected boolean mIsOutputMode = true;
    protected boolean mRegistered = false;

    protected boolean mIsWorking = false;
    protected int mWorkProgress = 0;
    protected static final int WORK_CYCLE = 160;

    public MTERemoteWorkerNode(int aID, String aName, String aNameRegional, int aInvSlotCount) {
        super(aID, aName, aNameRegional, aInvSlotCount);
    }

    public MTERemoteWorkerNode(String aName, int aInvSlotCount) {
        super(aName, aInvSlotCount);
    }

    public abstract void doWork(IGregTechTileEntity aBaseMetaTileEntity);

    public abstract String getNodeType();

    protected int getCasingTextureID() {
        return GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings2, 0);
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        if (mHubDim != 0) {
            NBTTagCompound hubTag = new NBTTagCompound();
            hubTag.setInteger("x", mHubX);
            hubTag.setInteger("y", mHubY);
            hubTag.setInteger("z", mHubZ);
            hubTag.setInteger("dim", mHubDim);
            hubTag.setString("type", mHubType);
            hubTag.setBoolean("output", mIsOutputMode);
            aNBT.setTag("gtsr.hubPos", hubTag);
        }
        aNBT.setBoolean("mIsWorking", mIsWorking);
        aNBT.setInteger("mWorkProgress", mWorkProgress);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        if (aNBT.hasKey("gtsr.hubPos")) {
            NBTTagCompound hubTag = aNBT.getCompoundTag("gtsr.hubPos");
            mHubX = hubTag.getInteger("x");
            mHubY = hubTag.getInteger("y");
            mHubZ = hubTag.getInteger("z");
            mHubDim = hubTag.getInteger("dim");
            mHubType = hubTag.getString("type");
            mIsOutputMode = hubTag.hasKey("output") && hubTag.getBoolean("output");
        } else {
            mHubX = 0;
            mHubY = 0;
            mHubZ = 0;
            mHubDim = 0;
            mHubType = "";
            mIsOutputMode = true;
            mRegistered = false;
        }
        mIsWorking = aNBT.getBoolean("mIsWorking");
        mWorkProgress = aNBT.getInteger("mWorkProgress");
    }

    @Override
    public void addAdditionalTooltipInformation(ItemStack stack, List<String> tooltip) {
        super.addAdditionalTooltipInformation(stack, tooltip);
        if (stack != null && stack.hasTagCompound()
            && stack.getTagCompound()
                .hasKey("gtsr.hubPos")) {
            NBTTagCompound hubTag = stack.getTagCompound()
                .getCompoundTag("gtsr.hubPos");
            int hubX = hubTag.getInteger("x");
            int hubY = hubTag.getInteger("y");
            int hubZ = hubTag.getInteger("z");
            String hubType = hubTag.getString("type");
            tooltip.add(
                translateToLocal("gtsr.binding.bound_to") + " " + hubType + " @ " + hubX + ", " + hubY + ", " + hubZ);
        }
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (!aBaseMetaTileEntity.isServerSide()) return;

        if (!mRegistered && mHubDim != 0) {
            mRegistered = true;
            registerWithHub(aBaseMetaTileEntity);
        }

        aBaseMetaTileEntity.setActive(mIsWorking);
    }

    private void registerWithHub(IGregTechTileEntity aBaseMetaTileEntity) {
        World world = DimensionManager.getWorld(mHubDim);
        if (world == null || !world.blockExists(mHubX, mHubY, mHubZ)) return;

        TileEntity te = world.getTileEntity(mHubX, mHubY, mHubZ);
        if (!(te instanceof IGregTechTileEntity gte)) return;

        if (!(gte.getMetaTileEntity() instanceof IHubArray hub)) return;

        if (!hub.acceptsNodeType(mHubType)) return;

        hub.registerCacheNode(
            aBaseMetaTileEntity.getXCoord(),
            aBaseMetaTileEntity.getYCoord(),
            aBaseMetaTileEntity.getZCoord(),
            aBaseMetaTileEntity.getWorld().provider.dimensionId,
            mIsOutputMode);
    }

    protected MTESingularityDrillingHub getBoundHub() {
        if (mHubDim == 0) return null;
        World world = DimensionManager.getWorld(mHubDim);
        if (world == null || !world.blockExists(mHubX, mHubY, mHubZ)) return null;

        TileEntity te = world.getTileEntity(mHubX, mHubY, mHubZ);
        if (!(te instanceof IGregTechTileEntity gte)) return null;

        if (gte.getMetaTileEntity() instanceof MTESingularityDrillingHub hub) {
            return hub;
        }
        return null;
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer, ForgeDirection side,
        float aX, float aY, float aZ) {
        ItemStack held = aPlayer.getHeldItem();
        if (held == null || aBaseMetaTileEntity.isClientSide()) {
            return super.onRightclick(aBaseMetaTileEntity, aPlayer, side, aX, aY, aZ);
        }

        NBTTagCompound tag = held.getTagCompound();
        if (tag != null && tag.hasKey("gtsr.hubPos")) {
            NBTTagCompound hubTag = tag.getCompoundTag("gtsr.hubPos");
            int hubX = hubTag.getInteger("x");
            int hubY = hubTag.getInteger("y");
            int hubZ = hubTag.getInteger("z");
            int hubDim = hubTag.getInteger("dim");

            if (hubX == mHubX && hubY == mHubY && hubZ == mHubZ && hubDim == mHubDim) {
                tag.removeTag("gtsr.hubPos");
                mHubX = 0;
                mHubY = 0;
                mHubZ = 0;
                mHubDim = 0;
                mHubType = "";
                mRegistered = false;
                GTUtility.sendChatToPlayer(
                    aPlayer,
                    translateToLocal("gtsr.binding.cleared") + held.getDisplayName()
                        + translateToLocal("gtsr.binding.binding"));
                return true;
            }
        }

        return super.onRightclick(aBaseMetaTileEntity, aPlayer, side, aX, aY, aZ);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int aColorIndex, boolean aActive, boolean aRedstone) {
        int casingId = getCasingTextureID();
        if (side == facing) {
            return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(casingId),
                aActive ? getFrontOverlayActive() : getFrontOverlay() };
        }
        return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(casingId) };
    }

    protected ITexture getFrontOverlay() {
        return TextureFactory.of(Textures.BlockIcons.OVERLAY_FRONT_STEAM_FURNACE);
    }

    protected ITexture getFrontOverlayActive() {
        return TextureFactory.of(Textures.BlockIcons.OVERLAY_FRONT_STEAM_FURNACE_ACTIVE);
    }

    @Override
    public boolean isElectric() {
        return false;
    }

    @Override
    public boolean isPneumatic() {
        return false;
    }

    @Override
    public boolean isSteampowered() {
        return false;
    }

    @Override
    public boolean isEnetInput() {
        return false;
    }

    @Override
    public boolean isEnetOutput() {
        return false;
    }

    @Override
    public long getMinimumStoredEU() {
        return 0;
    }

    @Override
    public long maxEUStore() {
        return 0;
    }

    @Override
    public long maxEUInput() {
        return 0;
    }

    @Override
    public long maxEUOutput() {
        return 0;
    }

    @Override
    public long maxAmperesIn() {
        return 0;
    }

    @Override
    public long maxAmperesOut() {
        return 0;
    }

    @Override
    public int getProgresstime() {
        return mIsWorking ? mWorkProgress : 0;
    }

    @Override
    public int maxProgresstime() {
        return mIsWorking ? WORK_CYCLE : 0;
    }

    @Override
    public boolean isAccessAllowed(EntityPlayer aPlayer) {
        return true;
    }

    @Override
    public String[] getDescription() {
        return new String[] { StatCollector.translateToLocal("gt.blockmachines." + mName + ".name") };
    }

    @Override
    public boolean allowPutStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection side,
        ItemStack aStack) {
        return aIndex == 0;
    }

    @Override
    public boolean allowPullStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection side,
        ItemStack aStack) {
        return aIndex > 0;
    }

    @Override
    public byte getTileEntityBaseType() {
        return HarvestTool.WrenchLevel0.toTileEntityBaseType();
    }
}
