package com.miaokatze.gtsr.common.dimension.prosperity.remaster;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

/** Ownership is validated against the immutable prefab, never accepted from an interaction packet. */
public final class TileRemasterNode extends TileEntity {

    public String siteId = "", nodeId = "", role = "", display = "{}";
    private boolean loaded;

    public void initialize(String site, String node, String role) {
        this.siteId = site;
        this.nodeId = node;
        this.role = role;
        refresh();
    }

    public void refresh() {
        if (worldObj == null || worldObj.isRemote || siteId.isEmpty()) return;
        display = RemasterRuntime.view(this)
            .toString();
        markDirty();
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    @Override
    public void updateEntity() {
        if (worldObj != null && !worldObj.isRemote && !siteId.isEmpty()) {
            if (!loaded) {
                loaded = true;
                RemasterRuntime.initializeLoaded(this);
            }
            RemasterRuntime.tick(this);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound n) {
        super.readFromNBT(n);
        siteId = n.getString("siteId");
        nodeId = n.getString("nodeId");
        role = n.getString("role");
        display = n.hasKey("display") ? n.getString("display") : "{}";
    }

    @Override
    public void writeToNBT(NBTTagCompound n) {
        super.writeToNBT(n);
        n.setString("siteId", siteId);
        n.setString("nodeId", nodeId);
        n.setString("role", role);
        n.setString("display", display);
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound n = new NBTTagCompound();
        writeToNBT(n);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 7, n);
    }

    @Override
    public void onDataPacket(NetworkManager manager, S35PacketUpdateTileEntity packet) {
        readFromNBT(packet.func_148857_g());
    }
}
