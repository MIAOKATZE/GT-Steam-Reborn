package com.miaokatze.gtsr.common.critical;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;

/** One bounded visual snapshot per installation. No production, inventory or structure scan. */
public final class TileEntityCriticalRenderAnchor extends TileEntity {

    private final CriticalMachineState visual = new CriticalMachineState();
    private NBTTagCompound descriptor = new NBTTagCompound();
    private int ownerX, ownerY, ownerZ;
    private long lastSync = Long.MIN_VALUE;

    @Override
    public boolean canUpdate() {
        return false;
    }

    public boolean ownedBy(CriticalMachineState state) {
        if (ownerX != state.xCoord || ownerY != state.yCoord || ownerZ != state.zCoord) return false;
        String hostId = descriptor.getString("visualHostId");
        // Old saves have no host token: only their original structure may adopt them once.
        return hostId.isEmpty() ? descriptor.getString("structureId")
            .equals(state.getStructureId()) : hostId.equals(state.getVisualHostId());
    }

    public void syncFrom(CriticalMachineState state) {
        syncFrom(state, true);
    }

    public void syncFrom(CriticalMachineState state, boolean loomVisible) {
        NBTTagCompound next = state.getVisualData();
        next.setString("visualHostId", state.getVisualHostId());
        next.setBoolean("loomVisible", loomVisible);
        if (ownedBy(state) && descriptor.equals(next)) return;
        long now = worldObj.getTotalWorldTime();
        if (ownedBy(state) && sameStatus(descriptor, next)
            && lastSync != Long.MIN_VALUE
            && now >= lastSync
            && now - lastSync < 20) return;
        ownerX = state.xCoord;
        ownerY = state.yCoord;
        ownerZ = state.zCoord;
        descriptor = (NBTTagCompound) next.copy();
        lastSync = now;
        markDirty();
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    private static boolean sameStatus(NBTTagCompound previous, NBTTagCompound next) {
        NBTTagCompound a = (NBTTagCompound) previous.copy(), b = (NBTTagCompound) next.copy();
        for (String clock : new String[] { "cycleTicks", "constructionElapsed", "cursor", "status" }) {
            a.removeTag(clock);
            b.removeTag(clock);
        }
        return a.equals(b);
    }

    public boolean isLoomVisible() {
        return descriptor.hasKey("loomVisible") ? descriptor.getBoolean("loomVisible")
            : !descriptor.getString("structureId")
                .isEmpty();
    }

    public CriticalMachineState getVisualState() {
        boolean worldChanged = visual.worldObj != worldObj;
        visual.worldObj = worldObj;
        visual.xCoord = ownerX;
        visual.yCoord = ownerY;
        visual.zCoord = ownerZ;
        if (worldChanged) visual.readVisualData(descriptor);
        return visual;
    }

    private void applyDescriptor() {
        getVisualState().readVisualData(descriptor);
    }

    @Override
    public void writeToNBT(NBTTagCompound n) {
        super.writeToNBT(n);
        n.setInteger("ownerX", ownerX);
        n.setInteger("ownerY", ownerY);
        n.setInteger("ownerZ", ownerZ);
        n.setTag("visual", descriptor.copy());
    }

    @Override
    public void readFromNBT(NBTTagCompound n) {
        super.readFromNBT(n);
        ownerX = n.getInteger("ownerX");
        ownerY = n.getInteger("ownerY");
        ownerZ = n.getInteger("ownerZ");
        descriptor = (NBTTagCompound) n.getCompoundTag("visual")
            .copy();
        // Disk snapshots are visual history, never evidence that a reloaded server structure is intact.
        if (worldObj == null || !worldObj.isRemote) {
            descriptor.setBoolean("valid", false);
            descriptor.setBoolean("loomVisible", false);
        }
        applyDescriptor();
        CriticalLoomProxyVisibility.refresh(this);
    }

    @Override
    public void validate() {
        super.validate();
        CriticalLoomProxyVisibility.refresh(this);
    }

    @Override
    public void invalidate() {
        CriticalLoomProxyVisibility.remove(this);
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        CriticalLoomProxyVisibility.remove(this);
        super.onChunkUnload();
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound n = new NBTTagCompound();
        writeToNBT(n);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, n);
    }

    @Override
    public void onDataPacket(NetworkManager network, S35PacketUpdateTileEntity packet) {
        readFromNBT(packet.func_148857_g());
        if (worldObj != null) worldObj.markBlockRangeForRenderUpdate(xCoord, yCoord, zCoord, xCoord, yCoord, zCoord);
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return getVisualState().getRenderBoundingBox();
    }

    @Override
    public double getMaxRenderDistanceSquared() {
        return 512D * 512D;
    }
}
