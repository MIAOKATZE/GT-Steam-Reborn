package com.miaokatze.gtsr.common.critical;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;

import cpw.mods.fml.common.registry.GameRegistry;

/** Canonical (40,1,32) is empty in every base/machine/loom model. Only this one coordinate is reserved. */
public final class CriticalRenderAnchors {

    public static final Block BLOCK = new BlockCriticalRenderAnchor();
    private static boolean registered;

    private CriticalRenderAnchors() {}

    public static int getX(CriticalMachineState state) {
        return state.getOriginX() + state.getControllerX();
    }

    public static int getY(CriticalMachineState state) {
        return state.getOriginY() + state.getControllerY() + 1;
    }

    public static int getZ(CriticalMachineState state) {
        return state.getOriginZ() + (state.getGeometryVersion() == 2 ? 34 : 32);
    }

    public static void register() {
        if (registered) return;
        GameRegistry.registerBlock(BLOCK, CriticalMaterials.ConstructionOnlyItemBlock.class, "critical_render_anchor");
        GameRegistry.registerTileEntity(TileEntityCriticalRenderAnchor.class, "gtsr.critical.render_anchor");
        registered = true;
    }

    public static void ensure(CriticalMachineState state) {
        ensure(state, false);
    }

    /** The host supplies a cached, complete loom check; the anchor never scans the structure. */
    public static void ensure(CriticalMachineState state, boolean independentLoomPresent) {
        if (state.worldObj == null || state.worldObj.isRemote) return;
        if (state.getHost() == null
            || state.worldObj.getTileEntity(state.xCoord, state.yCoord, state.zCoord) != state.getHost()) return;
        boolean installation = !state.getStructureId()
            .isEmpty() && state.getState() != 0;
        if (!independentLoomPresent && !installation) {
            remove(state);
            return;
        }
        int x = getX(state), y = getY(state), z = getZ(state);
        if (!state.worldObj.blockExists(x, y, z)) return;
        TileEntity tile = state.worldObj.getTileEntity(x, y, z);
        if (tile instanceof TileEntityCriticalRenderAnchor anchor && anchor.ownedBy(state)) {
            anchor.syncFrom(state, independentLoomPresent || installation);
        } else if (state.worldObj.isAirBlock(x, y, z)) {
            if (state.worldObj.setBlock(x, y, z, BLOCK, 0, 3)
                && state.worldObj.getTileEntity(x, y, z) instanceof TileEntityCriticalRenderAnchor anchor)
                anchor.syncFrom(state, independentLoomPresent || installation);
        }
    }

    public static void remove(CriticalMachineState state) {
        if (state.worldObj == null || state.worldObj.isRemote) return;
        int x = getX(state), y = getY(state), z = getZ(state);
        if (state.worldObj.blockExists(x, y, z)
            && state.worldObj.getTileEntity(x, y, z) instanceof TileEntityCriticalRenderAnchor anchor
            && anchor.ownedBy(state)) state.worldObj.setBlockToAir(x, y, z);
    }
}
