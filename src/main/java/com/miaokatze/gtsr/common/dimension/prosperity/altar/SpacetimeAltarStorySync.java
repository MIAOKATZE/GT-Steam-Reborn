package com.miaokatze.gtsr.common.dimension.prosperity.altar;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterBlocks;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.TileRemasterNode;

/** The shared console block cannot choose its client TE type until the server description arrives. */
public final class SpacetimeAltarStorySync {

    private SpacetimeAltarStorySync() {}

    /**
     * Restores only the two registered console types from an authoritative S35 description. Same-type
     * packets retain their existing readFromNBT path; disk NBT and the block factory are unchanged.
     *
     * @return true when a cross-type description was handled or rejected
     */
    public static boolean restoreDescriptionType(TileEntity current, S35PacketUpdateTileEntity packet) {
        NBTTagCompound tag = packet.func_148857_g();
        if (tag == null) return false;
        String id = tag.getString("id");
        boolean story = current instanceof TileRemasterNode && "gtsr.spacetimeAltarStory".equals(id);
        boolean node = current instanceof TileSpacetimeAltarStory && "gtsr.remasterNode7".equals(id);
        if (!story && !node) return false;

        World world = current.getWorldObj();
        int x = packet.func_148856_c(), y = packet.func_148855_d(), z = packet.func_148854_e();
        if (world == null || !world.isRemote
            || world.provider.dimensionId != 0
            || current.xCoord != x
            || current.yCoord != y
            || current.zCoord != z
            || !tag.hasKey("x", 3)
            || !tag.hasKey("y", 3)
            || !tag.hasKey("z", 3)
            || tag.getInteger("x") != x
            || tag.getInteger("y") != y
            || tag.getInteger("z") != z
            || world.getTileEntity(x, y, z) != current
            || world.getBlock(x, y, z) != RemasterBlocks.get("gtsr:draft_pressure_console")) return true;
        if (story && (!tag.hasKey("altarInstance", 8) || tag.getString("altarInstance")
            .isEmpty()
            || tag.getString("altarInstance")
                .length() > 120))
            return true;

        TileEntity restored = TileEntity.createAndLoadEntity(tag);
        if (story ? !(restored instanceof TileSpacetimeAltarStory) : !(restored instanceof TileRemasterNode))
            return true;
        // World/Chunk installs the fresh tile, assigns its world/coordinates and invalidates the old one.
        world.setTileEntity(x, y, z, restored);
        world.func_147451_t(x, y, z);
        world.markBlockForUpdate(x, y, z);
        return true;
    }
}
