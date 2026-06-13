package com.miaokatze.gtsr.common.machine.base;

import static net.minecraft.util.StatCollector.translateToLocal;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;
import com.miaokatze.gtsr.common.machine.MTESingularityDrillingHub;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.HarvestTool;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.modularui.IAddUIWidgets;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTModHandler;

public abstract class MTERemoteWorkerNode extends MetaTileEntity implements IAddUIWidgets {

    private static Item sMiningPipeItem = null;

    protected static Item getMiningPipeItem() {
        if (sMiningPipeItem == null) {
            ItemStack pipe = GTModHandler.getIC2Item("miningPipe", 0);
            if (pipe != null) {
                sMiningPipeItem = pipe.getItem();
            }
        }
        return sMiningPipeItem;
    }

    protected static boolean isMiningPipe(ItemStack aStack) {
        if (aStack == null) return false;
        Item pipeItem = getMiningPipeItem();
        return pipeItem != null && aStack.getItem() == pipeItem;
    }

    protected int mHubX = 0;
    protected int mHubY = 0;
    protected int mHubZ = 0;
    protected int mHubDim = 0;
    protected String mHubType = "";
    protected boolean mIsOutputMode = true;
    protected boolean mRegistered = false;

    protected boolean mIsWorking = false;
    protected int mWorkProgress = 0;
    protected static final int WORK_CYCLE = 20;

    public MTERemoteWorkerNode(int aID, String aName, String aNameRegional, int aInvSlotCount) {
        super(aID, aName, aNameRegional, aInvSlotCount);
    }

    public MTERemoteWorkerNode(String aName, int aInvSlotCount) {
        super(aName, aInvSlotCount);
    }

    public abstract void doWork(IGregTechTileEntity aBaseMetaTileEntity);

    public abstract String getNodeType();

    public int getDrillTier() {
        return 0;
    }

    /**
     * Returns true if this node is actively working and should consume steam from the hub.
     * Drilling nodes always consume; miner nodes only consume when actively working.
     */
    public boolean isActivelyWorking() {
        return true;
    }

    @Override
    public boolean isFacingValid(ForgeDirection facing) {
        return facing != ForgeDirection.UP && facing != ForgeDirection.DOWN && facing != ForgeDirection.UNKNOWN;
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        if (isBound()) {
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

    protected boolean isBound() {
        return mHubX != 0 || mHubY != 0 || mHubZ != 0 || mHubDim != 0;
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (!aBaseMetaTileEntity.isServerSide()) return;

        if (!mRegistered && isBound()) {
            mRegistered = registerWithHub(aBaseMetaTileEntity);
        }

        aBaseMetaTileEntity.setActive(mIsWorking);
        mIsWorking = false;
    }

    @Override
    public String[] getInfoData() {
        return new String[0];
    }

    protected boolean registerWithHub(IGregTechTileEntity aBaseMetaTileEntity) {
        World world = DimensionManager.getWorld(mHubDim);
        if (world == null || !world.blockExists(mHubX, mHubY, mHubZ)) return false;

        TileEntity te = world.getTileEntity(mHubX, mHubY, mHubZ);
        if (!(te instanceof IGregTechTileEntity gte)) return false;

        if (!(gte.getMetaTileEntity() instanceof IHubArray hub)) return false;

        if (!hub.acceptsNodeType(mHubType)) return false;

        hub.registerCacheNode(
            aBaseMetaTileEntity.getXCoord(),
            aBaseMetaTileEntity.getYCoord(),
            aBaseMetaTileEntity.getZCoord(),
            aBaseMetaTileEntity.getWorld().provider.dimensionId,
            mIsOutputMode);
        return true;
    }

    protected MTESingularityDrillingHub getBoundHub() {
        if (!isBound()) return null;
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
        if (aBaseMetaTileEntity.isServerSide()) {
            openGui(aPlayer);
        }
        return true;
    }

    @Override
    protected boolean useMui2() {
        return false;
    }

    @Override
    public int getGUIHeight() {
        return 182;
    }

    @Override
    public void addUIWidgets(ModularWindow.Builder builder, UIBuildContext buildContext) {
        final int slotCount = mInventory.length;
        builder.widget(new SlotWidget(inventoryHandler, 0).setPos(52, 24));
        for (int i = 1; i < slotCount; i++) {
            builder.widget(new SlotWidget(inventoryHandler, i).setPos(106 + (i - 1) * 18, 24));
        }
        addDisplayTexts(builder);
    }

    protected void addDisplayTexts(ModularWindow.Builder builder) {}

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int aColorIndex, boolean aActive, boolean aRedstone) {
        ITexture background = TextureFactory.of(GregTechAPI.sBlockCasings2, 0);
        if (side == facing) {
            return new ITexture[] { background, aActive ? getFrontOverlayActive() : getFrontOverlay() };
        }
        return new ITexture[] { background };
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
        return mWorkProgress;
    }

    @Override
    public int maxProgresstime() {
        return maxProgresstimeInternal();
    }

    /**
     * Subclasses override this to return their work cycle length.
     * Used by both Waila progress display and internal tracking.
     */
    protected int maxProgresstimeInternal() {
        return WORK_CYCLE;
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
        return isInputSlot(aIndex) && isMiningPipe(aStack);
    }

    protected boolean isInputSlot(int aIndex) {
        return aIndex == 0;
    }

    @Override
    public boolean allowPullStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection side,
        ItemStack aStack) {
        return !isInputSlot(aIndex);
    }

    @Override
    public byte getTileEntityBaseType() {
        return HarvestTool.WrenchLevel0.toTileEntityBaseType();
    }

    protected ItemStack consumeMiningPipeFromInputs() {
        int inputCount = getInputSlotCount();
        for (int i = 0; i < inputCount; i++) {
            ItemStack stack = mInventory[i];
            if (isMiningPipe(stack) && stack.stackSize > 0) {
                stack.stackSize--;
                ItemStack result = stack.copy();
                result.stackSize = 1;
                if (stack.stackSize <= 0) {
                    mInventory[i] = null;
                }
                return result;
            }
        }
        return null;
    }

    protected int getInputSlotCount() {
        return 1;
    }
}
