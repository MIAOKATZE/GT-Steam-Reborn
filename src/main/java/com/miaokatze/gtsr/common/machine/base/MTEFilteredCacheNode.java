package com.miaokatze.gtsr.common.machine.base;

import static net.minecraft.util.StatCollector.translateToLocal;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.util.GTUtility;
import gregtech.common.tileentities.storage.MTEDigitalTankBase;

public abstract class MTEFilteredCacheNode extends MTEDigitalTankBase {

    public MTEFilteredCacheNode(int aID, String aName, String aNameRegional, int aTier) {
        super(aID, aName, aNameRegional, aTier);
    }

    public MTEFilteredCacheNode(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures);
    }

    protected int mHubX = 0;
    protected int mHubY = 0;
    protected int mHubZ = 0;
    protected int mHubDim = 0;
    protected String mHubType = "";
    protected boolean mIsOutputMode = true;
    protected boolean mRegistered = false;
    protected int mTransferRatePercent = 100;
    // 是否已绑定到枢纽（独立于 mHubDim，避免主世界 dim=0 被误判为未绑定）
    protected boolean mBound = false;

    private static final int[] TRANSFER_RATE_CYCLE = { 100, 80, 60, 40, 20, 10, 5, 1, 0 };

    protected abstract boolean isFluidAllowed(Fluid fluid);

    protected abstract int getBaseHubTransferRate();

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer, ForgeDirection side,
        float aX, float aY, float aZ) {
        if (aBaseMetaTileEntity.isServerSide()) {
            ItemStack held = aPlayer.getCurrentEquippedItem();
            if (held != null && (GTSRItemList.HubSingularityChip.isStackEqual(held, true, true)
                || GTSRItemList.ReinforcedHubSingularityChip.isStackEqual(held, true, true))) {
                // 用 mBound 判断绑定状态，避免主世界 dim=0 被误判为未绑定
                if (!mBound) {
                    GTUtility
                        .sendChatToPlayer(aPlayer, StatCollector.translateToLocal("gtsr.cache_node.need_bind_first"));
                    return true;
                }
                int currentIdx = -1;
                for (int i = 0; i < TRANSFER_RATE_CYCLE.length; i++) {
                    if (TRANSFER_RATE_CYCLE[i] == mTransferRatePercent) {
                        currentIdx = i;
                        break;
                    }
                }
                int nextIdx = (currentIdx + 1) % TRANSFER_RATE_CYCLE.length;
                mTransferRatePercent = TRANSFER_RATE_CYCLE[nextIdx];
                long actualRate = (long) getBaseHubTransferRate() * mTransferRatePercent / 100;
                String msg = StatCollector.translateToLocal("gtsr.cache_node.transfer_rate") + " "
                    + mTransferRatePercent
                    + "% ("
                    + String.format("%,d", actualRate)
                    + " "
                    + StatCollector.translateToLocal("gtsr.tooltip.shared.l_s")
                    + ")";
                GTUtility.sendChatToPlayer(aPlayer, msg);
                return true;
            }
        }
        return super.onRightclick(aBaseMetaTileEntity, aPlayer, side, aX, aY, aZ);
    }

    public int getTransferRatePercent() {
        return mTransferRatePercent;
    }

    public long getEffectiveHubTransferRate() {
        return (long) getBaseHubTransferRate() * mTransferRatePercent / 100;
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setBoolean("mIsOutputMode", mIsOutputMode);
        aNBT.setInteger("mTransferRatePercent", mTransferRatePercent);
        // 用 mBound 判断绑定状态，避免主世界 dim=0 被误判为未绑定
        if (mBound) {
            NBTTagCompound hubTag = new NBTTagCompound();
            hubTag.setInteger("x", mHubX);
            hubTag.setInteger("y", mHubY);
            hubTag.setInteger("z", mHubZ);
            hubTag.setInteger("dim", mHubDim);
            hubTag.setString("type", mHubType);
            aNBT.setTag("gtsr.hubPos", hubTag);
        }
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mIsOutputMode = aNBT.hasKey("mIsOutputMode") ? aNBT.getBoolean("mIsOutputMode") : true;
        mTransferRatePercent = aNBT.hasKey("mTransferRatePercent") ? aNBT.getInteger("mTransferRatePercent") : 100;
        if (aNBT.hasKey("gtsr.hubPos")) {
            NBTTagCompound hubTag = aNBT.getCompoundTag("gtsr.hubPos");
            mHubX = hubTag.getInteger("x");
            mHubY = hubTag.getInteger("y");
            mHubZ = hubTag.getInteger("z");
            mHubDim = hubTag.getInteger("dim");
            mHubType = hubTag.getString("type");
            // 物品NBT中 output=false 表示输出模式(节点→枢纽), output=true 表示输入模式(枢纽→节点)
            // mIsOutputMode=true 表示输出模式(节点→枢纽), mIsOutputMode=false 表示输入模式(枢纽→节点)
            // 语义一致：output字段的值取反即为mIsOutputMode的值
            if (hubTag.hasKey("output")) {
                mIsOutputMode = !hubTag.getBoolean("output");
            }
            // 已从 NBT 读取到绑定信息，标记为已绑定
            mBound = true;
        } else {
            mHubX = 0;
            mHubY = 0;
            mHubZ = 0;
            mHubDim = 0;
            mHubType = "";
            mIsOutputMode = true;
            mRegistered = false;
            // 无绑定信息，标记为未绑定
            mBound = false;
        }
    }

    @Override
    public void addAdditionalTooltipInformation(ItemStack stack, List<String> tooltip) {
        super.addAdditionalTooltipInformation(stack, tooltip);
        tooltip.add(
            EnumChatFormatting.AQUA + StatCollector.translateToLocal("gtsr.tooltip.cache_node.base_transfer_rate")
                + EnumChatFormatting.GREEN
                + String.format("%,d", getBaseHubTransferRate())
                + " "
                + StatCollector.translateToLocal("gtsr.tooltip.shared.l_s"));
        tooltip.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.tooltip.cache_node.chip_adjust"));
        if (stack != null && stack.hasTagCompound()
            && stack.getTagCompound()
                .hasKey("gtsr.hubPos")) {
            NBTTagCompound hubTag = stack.getTagCompound()
                .getCompoundTag("gtsr.hubPos");
            int hubX = hubTag.getInteger("x");
            int hubY = hubTag.getInteger("y");
            int hubZ = hubTag.getInteger("z");
            String hubType = hubTag.getString("type");
            boolean isOutput = hubTag.hasKey("output") && !hubTag.getBoolean("output");
            String mode = isOutput ? translateToLocal("gtsr.binding.debug_output")
                : translateToLocal("gtsr.binding.debug_input");
            tooltip.add(
                translateToLocal("gtsr.binding.bound_to") + " "
                    + hubType
                    + " @ "
                    + hubX
                    + ", "
                    + hubY
                    + ", "
                    + hubZ
                    + " ["
                    + mode
                    + "]");
        }
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (!aBaseMetaTileEntity.isServerSide()) return;

        // 用 mBound 判断绑定状态，避免主世界 dim=0 被误判为未绑定
        if (!mRegistered && mBound) {
            mRegistered = true;
            registerWithHub(aBaseMetaTileEntity);
        }
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

}
