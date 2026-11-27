// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.common.util;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager.Ticket;

import com.miaokatze.gtsr.common.machine.base.IHubArray;
import com.miaokatze.gtsr.common.machine.base.MTERemoteWorkerNode;

import gregtech.api.enums.GTValues;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/** GTSR machines use external structure loading; only hubs and workers may own persistent tickets. */
public final class GtsrChunkLoadingPolicy {

    private GtsrChunkLoadingPolicy() {}

    public static boolean isGtsrOwner(TileEntity owner) {
        if (!(owner instanceof IGregTechTileEntity gt)) return false;
        IMetaTileEntity meta = gt.getMetaTileEntity();
        if (meta == null) return false;
        for (Class<?> type = meta.getClass(); type != null; type = type.getSuperclass()) if (type.getName()
            .startsWith("com.miaokatze.gtsr.")) return true;
        return false;
    }

    public static boolean permitsPersistentLoading(TileEntity owner) {
        if (!(owner instanceof IGregTechTileEntity gt)) return false;
        return gt.getMetaTileEntity() instanceof MTERemoteWorkerNode || gt.getMetaTileEntity() instanceof IHubArray;
    }

    /** GT already resolves saved owner coordinates during restart. Reject obsolete GTSR owners there. */
    public static List<Ticket> filterLegacyGtTickets(List<Ticket> tickets, World world) {
        // Passive reload returns no tickets in GT itself. Do not resolve owners earlier than GT would.
        if (!GTValues.alwaysReloadChunkloaders) return tickets;
        List<Ticket> kept = new ArrayList<>(tickets.size());
        for (Ticket ticket : tickets) {
            NBTTagCompound tag = ticket.getModData();
            if (tag.hasKey("OwnerX") && tag.hasKey("OwnerY") && tag.hasKey("OwnerZ")) {
                TileEntity owner = world
                    .getTileEntity(tag.getInteger("OwnerX"), tag.getInteger("OwnerY"), tag.getInteger("OwnerZ"));
                if (isGtsrOwner(owner) && (!GTValues.enableChunkloaders || !permitsPersistentLoading(owner))) continue;
            }
            kept.add(ticket);
        }
        return kept;
    }
}
