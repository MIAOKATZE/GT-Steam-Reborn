package com.miaokatze.gtsr.common.blocks;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.util.ForgeDirection;

import com.miaokatze.gtsr.common.dimension.prosperity.portal.SpacetimeAttraction;
import com.miaokatze.gtsr.common.machine.MTESpacetimeCalibration;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/** Stable visuals, with attraction exclusively leased to a live calibration machine. */
public final class TileSpacetimeSingularity extends TileRunawaySingularity {

    private final SpacetimeAttraction attraction = new SpacetimeAttraction();
    private int ownerX, ownerY, ownerZ, ownerDimension, preheatTicks;
    private ForgeDirection front = ForgeDirection.NORTH;
    private boolean hasOwner, leased, active, visible;
    private long heartbeat, lastSync = Long.MIN_VALUE;

    public TileSpacetimeSingularity() {
        setParams(32, 0, 0, -1, ATTRIBUTE_NULL_PLUS, "light_blue", 16);
        setType(SingularityType.STABLE);
        setDestroyBlocks(false);
    }

    public void updateCalibration(IGregTechTileEntity owner, ForgeDirection direction, int ticks, boolean running) {
        if (worldObj == null || worldObj.isRemote
            || owner == null
            || owner.getWorld() != worldObj
            || direction == null
            || direction == ForgeDirection.UNKNOWN) return;
        if (hasOwner && !ownedBy(owner)) return;
        boolean changed = !leased || ownerX != owner.getXCoord()
            || ownerY != owner.getYCoord()
            || ownerZ != owner.getZCoord()
            || front != direction
            || active != running;
        ownerX = owner.getXCoord();
        ownerY = owner.getYCoord();
        ownerZ = owner.getZCoord();
        ownerDimension = owner.getWorld().provider.dimensionId;
        hasOwner = true;
        front = direction;
        preheatTicks = Math.max(0, Math.min(600, ticks));
        heartbeat = worldObj.getTotalWorldTime();
        leased = true;
        active = running;
        boolean nextVisible = running || preheatTicks > 0;
        changed |= visible != nextVisible;
        visible = nextVisible;
        if (!active) attraction.releaseAll();
        if (changed || heartbeat - lastSync >= 20 || lastSync == Long.MIN_VALUE) sync();
    }

    public boolean ownedBy(IGregTechTileEntity owner) {
        return owner != null && owner.getWorld() == worldObj
            && ownerX == owner.getXCoord()
            && ownerY == owner.getYCoord()
            && ownerZ == owner.getZCoord()
            && hasOwner
            && ownerDimension == owner.getWorld().provider.dimensionId;
    }

    public ForgeDirection getFront() {
        return front;
    }

    @Override
    public int getAttributeId() {
        return ATTRIBUTE_NULL_PLUS;
    }

    @Override
    public void setType(SingularityType ignored) {
        super.setType(SingularityType.STABLE);
    }

    @Override
    public double getActiveFactor() {
        return visible ? (active ? 1.0 : Math.max(.1, preheatTicks / 600.0)) : 0;
    }

    @Override
    public void updateEntity() {
        // Deliberately do not enter the parent's expiry, absorption, damage or pull paths.
        if (worldObj == null || worldObj.isRemote) return;
        if (!leased) {
            // A persisted identity survives its lease. Retire orphan cores only after the owner chunk loads.
            if (hasOwner && worldObj.blockExists(ownerX, ownerY, ownerZ)) {
                TileEntity tile = worldObj.getTileEntity(ownerX, ownerY, ownerZ);
                boolean valid = tile instanceof IGregTechTileEntity && ownedBy((IGregTechTileEntity) tile)
                    && ((IGregTechTileEntity) tile).getMetaTileEntity() instanceof MTESpacetimeCalibration
                    && ((MTESpacetimeCalibration) ((IGregTechTileEntity) tile).getMetaTileEntity())
                        .ownsSingularity(xCoord, yCoord, zCoord);
                if (!valid && worldObj.getTileEntity(xCoord, yCoord, zCoord) == this
                    && worldObj.getBlock(xCoord, yCoord, zCoord) == BlocksGTSR.spacetimeSingularity)
                    worldObj.setBlockToAir(xCoord, yCoord, zCoord);
            }
            return;
        }
        if (worldObj.getTotalWorldTime() - heartbeat > 40 || !worldObj.blockExists(ownerX, ownerY, ownerZ)) {
            shutdown();
            return;
        }
        TileEntity tile = worldObj.getTileEntity(ownerX, ownerY, ownerZ);
        if (!(tile instanceof IGregTechTileEntity)) {
            shutdown();
            return;
        }
        IGregTechTileEntity owner = (IGregTechTileEntity) tile;
        if (!ownedBy(owner) || !(owner.getMetaTileEntity() instanceof MTESpacetimeCalibration)) {
            shutdown();
            return;
        }
        MTESpacetimeCalibration machine = (MTESpacetimeCalibration) owner.getMetaTileEntity();
        if (!machine.ownsSingularity(xCoord, yCoord, zCoord)) {
            shutdown();
            return;
        }
        if (!active || !machine.isPortalActive()) {
            attraction.releaseAll();
            return;
        }
        attraction.tick(machine, Vec3.createVectorHelper(xCoord + .5, yCoord + .5, zCoord + .5));
    }

    public void shutdown() {
        boolean changed = leased || visible || active;
        attraction.releaseAll();
        leased = active = visible = false;
        preheatTicks = 0;
        if (changed && worldObj != null && !worldObj.isRemote) sync();
    }

    private void sync() {
        markDirty();
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        lastSync = worldObj.getTotalWorldTime();
    }

    @Override
    public void invalidate() {
        shutdown();
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        shutdown();
        super.onChunkUnload();
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setBoolean("calibrationHasOwner", hasOwner);
        tag.setInteger("calibrationOwnerX", ownerX);
        tag.setInteger("calibrationOwnerY", ownerY);
        tag.setInteger("calibrationOwnerZ", ownerZ);
        tag.setInteger("calibrationOwnerDimension", ownerDimension);
        tag.setInteger("calibrationFront", front.ordinal());
        tag.setInteger("calibrationPreheat", preheatTicks);
        tag.setBoolean("calibrationActive", active);
        tag.setBoolean("calibrationVisible", visible);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        NBTTagCompound normalized = (NBTTagCompound) tag.copy();
        NBTTagCompound parameters = normalized.getCompoundTag("gtsrSingularity");
        parameters.setDouble("range", 32);
        parameters.setDouble("speed", 0);
        parameters.setDouble("damage", 0);
        parameters.setInteger("duration", -1);
        parameters.setInteger("attribute", ATTRIBUTE_NULL_PLUS);
        parameters.setString("color", "light_blue");
        parameters.setDouble("fxRadius", 16);
        parameters.setBoolean("destroyBlocks", false);
        parameters.setString("type", SingularityType.STABLE.name());
        normalized.setTag("gtsrSingularity", parameters);
        super.readFromNBT(normalized);
        setType(SingularityType.STABLE);
        setDestroyBlocks(false);
        hasOwner = tag.getBoolean("calibrationHasOwner");
        ownerX = tag.getInteger("calibrationOwnerX");
        ownerY = tag.getInteger("calibrationOwnerY");
        ownerZ = tag.getInteger("calibrationOwnerZ");
        ownerDimension = tag.getInteger("calibrationOwnerDimension");
        front = ForgeDirection.getOrientation(tag.getInteger("calibrationFront"));
        if (front == ForgeDirection.UNKNOWN) front = ForgeDirection.NORTH;
        preheatTicks = Math.max(0, Math.min(600, tag.getInteger("calibrationPreheat")));
        boolean clientPacket = worldObj != null && worldObj.isRemote;
        active = clientPacket && tag.getBoolean("calibrationActive");
        visible = clientPacket && tag.getBoolean("calibrationVisible");
        // NBT restores visuals only. Server attraction requires a fresh machine heartbeat.
        leased = false;
    }
}
