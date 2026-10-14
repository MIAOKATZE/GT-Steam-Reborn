package com.miaokatze.gtsr.common.critical;

import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraftforge.event.world.ExplosionEvent;

/** Coordinate ownership, never a property of a shared registered material or GT machine block. */
public final class CriticalBlockProtection {

    private static final Map<CriticalMachineState, ClientClaim> CLIENT = new WeakHashMap<>();

    private CriticalBlockProtection() {}

    public static boolean protects(World world, int x, int y, int z) {
        if (world == null) return false;
        Block block = world.getBlock(x, y, z);
        if (!(block instanceof CriticalMaterials.MaterialBlock) && block != gregtech.api.GregTechAPI.sBlockMachines)
            return false;
        if (!world.isRemote) return CriticalWorldData.get(world)
            .protectsPlayerBreak(world, x, y, z);
        synchronized (CLIENT) {
            for (Map.Entry<CriticalMachineState, ClientClaim> entry : CLIENT.entrySet()) {
                CriticalMachineState source = entry.getKey(), claim = entry.getValue().state;
                if (source.worldObj != world || !contains(claim, x, y, z) || !isClientSourceLoaded(source)) continue;
                if (claim.protectsPlayerBreak(x, y, z)) return true;
            }
        }
        return false;
    }

    private static boolean contains(CriticalMachineState state, int x, int y, int z) {
        return x >= state.getOriginX() && x < state.getOriginX() + state.getRegionSize()
            && y >= state.getOriginY()
            && y < state.getOriginY() + state.getRegionHeight()
            && z >= state.getOriginZ()
            && z < state.getOriginZ() + state.getRegionSize();
    }

    /** A stale visual object must not protect a later manually rebuilt installation at the same coordinates. */
    private static boolean isClientSourceLoaded(CriticalMachineState source) {
        World world = source.worldObj;
        if (world.getChunkProvider()
            .chunkExists(source.xCoord >> 4, source.zCoord >> 4)
            && CriticalMachineState.resolve(world.getTileEntity(source.xCoord, source.yCoord, source.zCoord)) == source)
            return true;
        int x = CriticalRenderAnchors.getX(source), z = CriticalRenderAnchors.getZ(source);
        if (!world.getChunkProvider()
            .chunkExists(x >> 4, z >> 4)) return false;
        TileEntity tile = world.getTileEntity(x, CriticalRenderAnchors.getY(source), z);
        return tile instanceof TileEntityCriticalRenderAnchor anchor && !anchor.isInvalid()
            && anchor.getVisualState() == source;
    }

    /** Invoked after visual decoding; no WorldSavedData is ever created or consulted on the client. */
    public static void readClientSnapshot(CriticalMachineState source, NBTTagCompound visual) {
        if (source.worldObj == null || !source.worldObj.isRemote) return;
        synchronized (CLIENT) {
            if (!visual.hasKey("protection")) {
                CLIENT.remove(source);
                return;
            }
            NBTTagCompound snapshot = visual.getCompoundTag("protection");
            ClientClaim previous = CLIENT.get(source);
            if (previous != null && previous.state.worldObj == source.worldObj
                && previous.state.xCoord == source.xCoord
                && previous.state.yCoord == source.yCoord
                && previous.state.zCoord == source.zCoord
                && previous.snapshot.equals(snapshot)) return;
            CriticalMachineState claim = new CriticalMachineState();
            claim.worldObj = source.worldObj;
            claim.xCoord = source.xCoord;
            claim.yCoord = source.yCoord;
            claim.zCoord = source.zCoord;
            claim.readProtectionSnapshot(snapshot);
            CLIENT.put(source, new ClientClaim(claim, (NBTTagCompound) snapshot.copy()));
        }
    }

    private static final class ClientClaim {

        final CriticalMachineState state;
        final NBTTagCompound snapshot;

        ClientClaim(CriticalMachineState state, NBTTagCompound snapshot) {
            this.state = state;
            this.snapshot = snapshot;
        }
    }

    /** A cached claim holds its World; explicitly release it before the old client World is discarded. */
    public static void unloadClientWorld(World world) {
        synchronized (CLIENT) {
            CLIENT.entrySet()
                .removeIf(entry -> entry.getKey().worldObj == world || entry.getValue().state.worldObj == world);
        }
    }

    /** Forge fires this after ray collection and before the actual block destruction pass. */
    public static void filterExplosion(ExplosionEvent.Detonate event) {
        if (event.world.isRemote) return;
        Iterator<ChunkPosition> blocks = event.getAffectedBlocks()
            .iterator();
        while (blocks.hasNext()) {
            ChunkPosition position = blocks.next();
            if (protects(event.world, position.chunkPosX, position.chunkPosY, position.chunkPosZ)) blocks.remove();
        }
    }
}
