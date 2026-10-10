package com.miaokatze.gtsr.common.dimension.prosperity.altar;

import java.io.DataInputStream;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraft.world.chunk.storage.RegionFile;
import net.minecraft.world.chunk.storage.RegionFileCache;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.storage.ThreadedFileIOBase;

/** Read saved chunk snapshots only; never instantiate chunks or create missing region files. */
final class SavedAltarCandidateProbe {

    private static final long MAX_NBT_BYTES = 2L * 1024 * 1024;
    private static final Result UNKNOWN = new Result(AltarSearchQueue.UNKNOWN, -1);
    private static final Result PLANNED = new Result(AltarSearchQueue.PLANNED, -1);
    private static final Result REJECTED = new Result(AltarSearchQueue.REJECTED, -1);
    private static final Field IO_QUEUE = ioQueueField();

    private SavedAltarCandidateProbe() {}

    static Result inspect(ChunkProviderServer provider, int x, int z) {
        if (!(provider.currentChunkLoader instanceof AnvilChunkLoader) || IO_QUEUE == null) return UNKNOWN;
        AnvilChunkLoader loader = (AnvilChunkLoader) provider.currentChunkLoader;
        try {
            // The loader stays in this queue while the worker writes, including after it dequeues
            // a pending chunk. A pending-set-only check could expose the previous disk snapshot.
            List<?> queue = (List<?>) IO_QUEUE.get(ThreadedFileIOBase.threadedIOInstance);
            if (saving(queue, loader)) return UNKNOWN;
            File directory = loader.chunkSaveLocation;
            File region = new File(new File(directory, "region"), "r." + (x >> 5) + "." + (z >> 5) + ".mca");
            if (!region.exists()) return saving(queue, loader) ? UNKNOWN : PLANNED;
            if (!region.isFile() || region.length() < 8192 || region.length() % 4096 != 0) return UNKNOWN;
            // This API creates missing region files, so it must only be called after the existence check.
            RegionFile saved = RegionFileCache.createOrLoadRegionFile(directory, x, z);
            synchronized (saved) {
                if (!saved.isChunkSaved(x & 31, z & 31)) return saving(queue, loader) ? UNKNOWN : PLANNED;
            }
            try (DataInputStream input = saved.getChunkDataInputStream(x & 31, z & 31)) {
                if (input == null) return UNKNOWN;
                NBTTagCompound root = CompressedStreamTools.func_152456_a(input, new NBTSizeTracker(MAX_NBT_BYTES));
                if (saving(queue, loader) || !root.hasKey("Level", 10)) return UNKNOWN;
                return classify(root.getCompoundTag("Level"), x, z);
            }
        } catch (Exception failure) {
            // Unsupported mappings, malformed/oversized NBT and transient I/O never retire a candidate.
            return UNKNOWN;
        }
    }

    static Result classify(NBTTagCompound level, int x, int z) {
        if (!level.hasKey("TerrainPopulated", 1) || !level.hasKey("xPos", 3)
            || !level.hasKey("zPos", 3)
            || level.getInteger("xPos") != x
            || level.getInteger("zPos") != z
            || !level.hasKey("TileEntities", 9)
            || !level.hasKey("Sections", 9)) return UNKNOWN;
        int coreX = x * 16 + 7, coreZ = z * 16 + 7;
        String id = "altar:" + x + ":" + z;
        NBTTagList tiles = level.getTagList("TileEntities", 10);
        NBTTagList sections = level.getTagList("Sections", 10);
        NBTTagList rawTiles = (NBTTagList) level.getTag("TileEntities");
        NBTTagList rawSections = (NBTTagList) level.getTag("Sections");
        if (rawTiles.tagCount() != 0 && rawTiles.func_150303_d() != 10
            || rawSections.tagCount() != 0 && rawSections.func_150303_d() != 10) return UNKNOWN;
        for (int i = 0; i < sections.tagCount(); i++) {
            NBTTagCompound section = sections.getCompoundTagAt(i);
            if (!section.hasKey("Y", 1) || !section.hasKey("Blocks", 7)
                || section.getByteArray("Blocks").length != 4096
                || (section.hasKey("Add") && (!section.hasKey("Add", 7) || section.getByteArray("Add").length != 2048)))
                return UNKNOWN;
        }
        for (int i = 0; i < tiles.tagCount(); i++) {
            NBTTagCompound tile = tiles.getCompoundTagAt(i);
            int y = tile.getInteger("y");
            if (!"gtsr.spacetimeAltar".equals(tile.getString("id")) || !id.equals(tile.getString("altarInstance"))
                || tile.getInteger("x") != coreX
                || tile.getInteger("z") != coreZ
                || y < 0
                || y >= 256) continue;
            int block = savedBlock(level.getTagList("Sections", 10), 7, y, 7);
            if (block < 0) return UNKNOWN;
            if (block == Block.getIdFromBlock(SpacetimeAltarBlocks.core)) return new Result(AltarSearchQueue.ACTUAL, y);
        }
        return level.getBoolean("TerrainPopulated") ? REJECTED : PLANNED;
    }

    private static int savedBlock(NBTTagList sections, int x, int y, int z) {
        for (int i = 0; i < sections.tagCount(); i++) {
            NBTTagCompound section = sections.getCompoundTagAt(i);
            if ((section.getByte("Y") & 255) != y >> 4) continue;
            byte[] blocks = section.getByteArray("Blocks");
            if (blocks.length != 4096) return -1;
            int offset = (y & 15) << 8 | z << 4 | x;
            int id = blocks[offset] & 255;
            if (section.hasKey("Add")) {
                byte[] add = section.getByteArray("Add");
                if (add.length != 2048) return -1;
                id |= ((add[offset >> 1] >> ((offset & 1) * 4)) & 15) << 8;
            }
            return id;
        }
        return 0;
    }

    private static Field ioQueueField() {
        try {
            // Resolve the sole instance List by type so production obfuscation cannot bypass the guard.
            Field queue = null;
            for (Field field : ThreadedFileIOBase.class.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || !List.class.isAssignableFrom(field.getType())) continue;
                if (queue != null) return null;
                queue = field;
            }
            if (queue != null) queue.setAccessible(true);
            return queue;
        } catch (RuntimeException failure) {
            return null;
        }
    }

    private static boolean saving(List<?> queue, AnvilChunkLoader loader) {
        synchronized (queue) {
            return queue.contains(loader);
        }
    }

    static final class Result {

        final int status, y;

        Result(int status, int y) {
            this.status = status;
            this.y = y;
        }
    }
}
