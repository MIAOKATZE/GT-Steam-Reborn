package com.miaokatze.gtsr.common.critical;

import net.minecraft.nbt.NBTTagCompound;

/** Detached runtime view. Neither constructor inputs nor returned tags can mutate this snapshot. */
public final class CriticalRuntimeSnapshot {

    private final NBTTagCompound energy, stellar;

    CriticalRuntimeSnapshot(NBTTagCompound energy, NBTTagCompound stellar) {
        this.energy = CriticalStateCodec.copy(energy);
        this.stellar = CriticalStateCodec.copy(stellar);
    }

    public NBTTagCompound energy() {
        return CriticalStateCodec.copy(energy);
    }

    public NBTTagCompound stellar() {
        return CriticalStateCodec.copy(stellar);
    }
}
