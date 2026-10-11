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

    @Override
    public boolean canUpdate() {
        return false;
    }

    public boolean ownedBy(CriticalMachineState state) {
        return ownerX == state.xCoord && ownerY == state.yCoord
            && ownerZ == state.zCoord
            && descriptor.getString("structureId")
                .equals(state.getStructureId());
    }

    public void syncFrom(CriticalMachineState state) {
        NBTTagCompound next = state.getVisualData();
        if (ownedBy(state) && descriptor.equals(next)) return;
        ownerX = state.xCoord;
        ownerY = state.yCoord;
        ownerZ = state.zCoord;
        descriptor = (NBTTagCompound) next.copy();
        markDirty();
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
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
        descriptor = n.getCompoundTag("visual");
        applyDescriptor();
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
