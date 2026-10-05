package com.miaokatze.gtsr.common.terminal;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.PacketBuffer;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.machine.base.IHubCacheNode;
import com.miaokatze.gtsr.common.machine.base.MTERemoteWorkerNode;

import cpw.mods.fml.common.network.ByteBufUtils;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/** Nearby handheld editor: its anchor is the node itself, never a client supplied remote target. */
public final class CacheNodeTerminalData {

    private CacheNodeTerminalData() {}

    public static boolean canUse(EntityPlayer player, IGregTechTileEntity base) {
        return player instanceof net.minecraft.entity.player.EntityPlayerMP
            && !(player instanceof net.minecraftforge.common.util.FakePlayer)
            && base != null
            && base.getWorld() != null
            && player.dimension == base.getWorld().provider.dimensionId
            && base.canAccessData()
            && base.isUseableByPlayer(player)
            && base.getMetaTileEntity() instanceof IHubCacheNode node
            && node.isBoundToHub()
            && GTSRItemList.HubTerminal.isStackEqual(player.getHeldItem(), false, true)
            && player.getDistanceSq(base.getXCoord() + .5, base.getYCoord() + .5, base.getZCoord() + .5) <= 64;
    }

    public static byte[] assembleSnapshot(EntityPlayer player, IGregTechTileEntity base) {
        if (!canUse(player, base)) return null;
        IHubCacheNode node = (IHubCacheNode) base.getMetaTileEntity();
        PacketBuffer pb = new PacketBuffer(Unpooled.buffer());
        pb.writeVarIntToBuffer(1);
        CacheHubTerminalData.write(
            pb,
            new CacheHubTerminalData.CacheNodeInfo(
                base.getXCoord(),
                base.getYCoord(),
                base.getZCoord(),
                base.getWorld().provider.dimensionId,
                node.supportsCapacityTier() ? "node" : "node_out",
                node.getCustomName(),
                node.getStoredFluidName(),
                node.getStoredFluidAmount(),
                node.getFluidCapacityLong(),
                node.getTransferRatePercent(),
                node.getCapacityLimitPercent(),
                node.isOutputMode(),
                node.isAutoOutput(),
                node.isOutputModeLocked(),
                node.getEffectiveHubTransferRate(),
                node.getMaximumHubTransferRate(),
                node.getMaximumFluidCapacity()));
        byte[] result = new byte[pb.readableBytes()];
        pb.readBytes(result);
        return result;
    }

    public static void executeAction(EntityPlayer player, IGregTechTileEntity base, int action, byte[] payload) {
        if (!canUse(player, base) || payload == null || payload.length < 16) return;
        IHubCacheNode node = (IHubCacheNode) base.getMetaTileEntity();
        ByteBuf buf = Unpooled.wrappedBuffer(payload);
        if (buf.readInt() != base.getXCoord() || buf.readInt() != base.getYCoord()
            || buf.readInt() != base.getZCoord()
            || buf.readInt() != player.dimension) return;
        switch (action) {
            case CacheHubTerminalData.ACTION_SET_RATE:
                if (buf.readableBytes() != 8) return;
                long rate = buf.readLong();
                if (rate < 0 || rate > node.getMaximumHubTransferRate()) return;
                node.setHubTransferRate(rate);
                break;
            case CacheHubTerminalData.ACTION_SET_CAPACITY:
                if (!node.supportsCapacityTier() || buf.readableBytes() != 8) return;
                long capacity = buf.readLong();
                if (capacity < 1 || capacity > node.getMaximumFluidCapacity()) return;
                node.setFluidCapacityLimit(capacity);
                break;
            case CacheHubTerminalData.ACTION_RENAME:
                if (!buf.isReadable()) return;
                String name = ByteBufUtils.readUTF8String(buf);
                if (buf.isReadable()) return;
                node.setCustomName(MTERemoteWorkerNode.sanitizeCustomName(name));
                break;
            default:
                return;
        }
        base.issueTileUpdate();
    }
}
