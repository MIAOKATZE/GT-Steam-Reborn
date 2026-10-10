package com.miaokatze.gtsr.common.dimension.prosperity.altar;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import cpw.mods.fml.common.IWorldGenerator;
import cpw.mods.fml.common.registry.GameRegistry;

/** Village-style 32 chunk candidate grid, followed by an independent 1/8 roll. */
public final class SpacetimeAltarWorldGenerator implements IWorldGenerator {

    private static final int SPACING = 32, SEPARATION = 8;
    private static final long CANDIDATE_SALT = 0x414C544152L, CHANCE_SALT = 0x5649533530L;
    private static List<Cell> cells;

    public static boolean candidate(long seed, int chunkX, int chunkZ) {
        int rx = Math.floorDiv(chunkX, SPACING), rz = Math.floorDiv(chunkZ, SPACING);
        int[] position = candidateInRegion(seed, rx, rz);
        return position != null && chunkX == position[0] && chunkZ == position[1];
    }

    /** The exact generation roll, without loading terrain or promising a successful placement. */
    public static int[] candidateInRegion(long seed, int rx, int rz) {
        Random grid = new Random(seed + rx * 341873128712L + rz * 132897987541L + CANDIDATE_SALT);
        int x = rx * SPACING + grid.nextInt(SPACING - SEPARATION),
            z = rz * SPACING + grid.nextInt(SPACING - SEPARATION);
        return new Random(seed + x * 341873128712L + z * 132897987541L + CHANCE_SALT).nextInt(8) == 0
            ? new int[] { x, z }
            : null;
    }

    private static List<Cell> model() {
        if (cells != null) return cells;
        List<Cell> resolved = new ArrayList<>();
        try (InputStreamReader reader = new InputStreamReader(
            SpacetimeAltarWorldGenerator.class.getResourceAsStream("/assets/gtsr/structures/spacetime_altar.json"),
            StandardCharsets.UTF_8)) {
            for (JsonElement element : new JsonParser().parse(reader)
                .getAsJsonArray()) {
                JsonArray row = element.getAsJsonArray();
                String name = row.get(3)
                    .getAsString();
                int colon = name.indexOf(':');
                Block block = GameRegistry.findBlock(name.substring(0, colon), name.substring(colon + 1));
                if (block == null) throw new IllegalStateException("Missing altar block " + name);
                resolved.add(
                    new Cell(
                        row.get(0)
                            .getAsInt(),
                        row.get(1)
                            .getAsInt(),
                        row.get(2)
                            .getAsInt(),
                        block,
                        row.get(4)
                            .getAsInt()));
            }
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot bind altar voxel model", failure);
        }
        cells = resolved;
        return cells;
    }

    @Override
    public void generate(Random random, int chunkX, int chunkZ, World world, IChunkProvider generator,
        IChunkProvider provider) {
        if (world.isRemote || world.provider.dimensionId != 0
            || !world.getWorldInfo()
                .isMapFeaturesEnabled()
            || !candidate(world.getSeed(), chunkX, chunkZ)) return;
        int x = chunkX * 16 + 1, z = chunkZ * 16 + 1, min = 256, max = 0;
        // Every query and placement stays inside the populated chunk; no speculative neighbour loads.
        for (int dx = 0; dx < 14; dx++) for (int dz = 0; dz < 14; dz++) {
            int y = world.getHeightValue(x + dx, z + dz) - 1;
            while (y > 1 && (world.getBlock(x + dx, y, z + dz)
                .isLeaves(world, x + dx, y, z + dz)
                || world.getBlock(x + dx, y, z + dz)
                    .isWood(world, x + dx, y, z + dz)))
                y--;
            Material ground = world.getBlock(x + dx, y, z + dz)
                .getMaterial();
            if (!ground.isSolid() || ground.isLiquid() || y < 55 || y > 230) return;
            min = Math.min(min, y);
            max = Math.max(max, y);
        }
        if (max - min > 2) return;
        int base = max;
        List<Cell> model = model();
        for (Cell cell : model) {
            int px = x + cell.x, py = base + cell.y, pz = z + cell.z;
            Block old = world.getBlock(px, py, pz);
            if (world.getTileEntity(px, py, pz) != null || old == Blocks.bedrock
                || old.getMaterial()
                    .isLiquid())
                return;
            if (cell.y > 0 && old != Blocks.air
                && !old.isLeaves(world, px, py, pz)
                && !old.isWood(world, px, py, pz)
                && !old.isReplaceable(world, px, py, pz)) return;
        }
        String id = "altar:" + chunkX + ":" + chunkZ;
        List<Original> originals = new ArrayList<>();
        boolean success = false;
        try {
            for (Cell cell : model) {
                int px = x + cell.x, py = base + cell.y, pz = z + cell.z;
                originals.add(new Original(px, py, pz, world.getBlock(px, py, pz), world.getBlockMetadata(px, py, pz)));
                if (world.getBlock(px, py, pz) != cell.block || world.getBlockMetadata(px, py, pz) != cell.meta)
                    if (!world.setBlock(px, py, pz, cell.block, cell.meta, 2))
                        throw new IllegalStateException("Altar placement refused");
            }
            if (!world.setBlock(x + 6, base + 8, z + 6, SpacetimeAltarBlocks.core, 0, 2))
                throw new IllegalStateException("Altar core refused");
            TileEntity tile = world.getTileEntity(x + 6, base + 8, z + 6);
            if (!(tile instanceof TileSpacetimeAltar)) throw new IllegalStateException("Altar core tile missing");
            ((TileSpacetimeAltar) tile).bind(id);
            if (!SpacetimeAltarStory.place(world, x, base, z, id))
                throw new IllegalStateException("Altar story/chest refused");
            SpacetimeAltarIndex.get(world)
                .add(id, x + 6, base + 8, z + 6);
            success = true;
        } catch (RuntimeException failure) {
            com.miaokatze.gtsr.main.GTSteamReborn.LOG
                .warn("[GTSR] Altar placement rolled back at " + x + "," + z, failure);
        } finally {
            if (!success) for (Original old : originals) world.setBlock(old.x, old.y, old.z, old.block, old.meta, 2);
        }
    }

    private static final class Cell {

        final int x, y, z, meta;
        final Block block;

        Cell(int x, int y, int z, Block block, int meta) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.block = block;
            this.meta = meta;
        }
    }

    private static final class Original {

        final int x, y, z, meta;
        final Block block;

        Original(int x, int y, int z, Block block, int meta) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.block = block;
            this.meta = meta;
        }
    }
}
