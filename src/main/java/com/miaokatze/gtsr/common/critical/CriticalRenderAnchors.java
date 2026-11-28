// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.critical;

import net.minecraft.block.Block;
import net.minecraftforge.common.util.ForgeDirection;

import cpw.mods.fml.common.registry.GameRegistry;

/** Logical render origin. The registered block remains only to identify and remove old owned anchors. */
public final class CriticalRenderAnchors {

    public static final Block BLOCK = new BlockCriticalRenderAnchor();
    private static boolean registered;
    private static final java.util.Map<CriticalMachineState, Boolean> LEGACY_CHECKED = new java.util.WeakHashMap<>();

    private CriticalRenderAnchors() {}

    public static int getX(CriticalMachineState state) {
        return state.blockWorldX(state.getControllerX(), state.getGeometryVersion() == 2 ? 34 : 32);
    }

    public static int getY(CriticalMachineState state) {
        return state.getOriginY() + state.getControllerY()
            + (state.getGeometryVersion() == 2 && state.getV2LayoutRevision() >= 30 ? 2 : 1);
    }

    public static int getZ(CriticalMachineState state) {
        return state.blockWorldZ(state.getControllerX(), state.getGeometryVersion() == 2 ? 34 : 32);
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
        if (!LEGACY_CHECKED.containsKey(state)) {
            int x = getX(state), z = getZ(state);
            if (state.worldObj.getChunkProvider()
                .chunkExists(x >> 4, z >> 4)) {
                removeOwnedLegacy(state, state.getStructureFacing());
                LEGACY_CHECKED.put(state, Boolean.TRUE);
            }
        }
        com.miaokatze.gtsr.common.machinevisual.VirtualStructureNetwork.observeCritical(state, independentLoomPresent);
    }

    public static void remove(CriticalMachineState state) {
        remove(state, state.getStructureFacing());
    }

    /** An uncommissioned controller may turn; remove the previous frame's owned visual before rebinding. */
    static void remove(CriticalMachineState state, ForgeDirection frame) {
        if (state.worldObj == null || state.worldObj.isRemote) return;
        com.miaokatze.gtsr.common.machinevisual.VirtualStructureNetwork.remove(state.getHost());
        LEGACY_CHECKED.remove(state);
        removeOwnedLegacy(state, frame);
    }

    /** One-time compatibility writes only; never replaces an unrelated block or TileEntity. */
    private static void removeOwnedLegacy(CriticalMachineState state, ForgeDirection frame) {
        int depth = (state.getGeometryVersion() == 2 ? 34 : 32) - state.getControllerZ();
        int x = state.xCoord + CriticalStructureAlignment.rotateX(0, depth, frame), y = getY(state),
            z = state.zCoord + CriticalStructureAlignment.rotateZ(0, depth, frame);
        if (state.worldObj.blockExists(x, y, z)
            && state.worldObj.getTileEntity(x, y, z) instanceof TileEntityCriticalRenderAnchor anchor
            && anchor.ownedBy(state)) state.worldObj.setBlockToAir(x, y, z);
        if (state.getGeometryVersion() == 2 && state.getV2LayoutRevision() >= 30
            && state.worldObj.blockExists(x, y - 1, z)
            && state.worldObj.getTileEntity(x, y - 1, z) instanceof TileEntityCriticalRenderAnchor previous
            && previous.ownedBy(state)) state.worldObj.setBlockToAir(x, y - 1, z);
    }
}
