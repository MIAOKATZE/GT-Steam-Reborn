package com.miaokatze.gtsr.common.dimension.prosperity.echo;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

/** Authored ownership proof, checked against deterministic node coordinates before every interaction. */
public final class TileRuinMechanism extends TileEntity {

    public String siteId = "", role = "";
    public int nodeIndex = -1, objectiveIndex = -1, zone = -1;

    public void initialize(String id, RuinsBlueprint.Node n) {
        siteId = id;
        role = n.role;
        nodeIndex = n.index;
        objectiveIndex = n.objectiveIndex;
        zone = n.zone;
        markDirty();
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    public void readFromNBT(NBTTagCompound n) {
        super.readFromNBT(n);
        siteId = n.getString("siteId");
        role = n.getString("role");
        nodeIndex = n.getInteger("nodeIndex");
        objectiveIndex = n.getInteger("objectiveIndex");
        zone = n.getInteger("zone");
    }

    public void writeToNBT(NBTTagCompound n) {
        super.writeToNBT(n);
        n.setString("siteId", siteId);
        n.setString("role", role);
        n.setInteger("nodeIndex", nodeIndex);
        n.setInteger("objectiveIndex", objectiveIndex);
        n.setInteger("zone", zone);
    }

    public Packet getDescriptionPacket() {
        NBTTagCompound n = new NBTTagCompound();
        writeToNBT(n);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, n);
    }

    public void onDataPacket(NetworkManager manager, S35PacketUpdateTileEntity packet) {
        readFromNBT(packet.func_148857_g());
    }
}
