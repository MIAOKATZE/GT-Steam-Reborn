package com.miaokatze.gtsr.common.dimension.prosperity.altar;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.BlockBush;
import net.minecraft.block.BlockHugeMushroom;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.BlockLog;
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
import gregtech.common.blocks.GTBlockOre;

/** Village-style 32 chunk candidate grid, followed by an independent 1/8 roll. */
public final class SpacetimeAltarWorldGenerator implements IWorldGenerator {

    private static final int SPACING = 32, SEPARATION = 8;
    private static final long CANDIDATE_SALT = 0x414C544152L, CHANCE_SALT = 0x5649533530L;
    private static List<Cell> cells;

    private static boolean eligible(World world) {
        return !world.isRemote && world.provider.dimensionId == 0
            && world.getWorldInfo()
                .isMapFeaturesEnabled();
    }

    /** Natural surface decorations may be cleared; constructed blocks and any tile are protected. */
    private static boolean decoration(World world, int x, int y, int z, Block block) {
        Material material = block.getMaterial();
        return block == Blocks.air || block == Blocks.snow_layer
            || block instanceof BlockHugeMushroom
            || block instanceof BlockBush
            || block instanceof BlockLeaves
            || block instanceof BlockLog
            || block.isLeaves(world, x, y, z)
            || block.isWood(world, x, y, z) && material == Material.wood
            || block.isReplaceable(world, x, y, z)
                && (material == Material.plants || material == Material.vine || material == Material.leaves)
            || block.getClass()
                .getName()
                .startsWith("com.pam.harvestcraft.")
                && block.getClass()
                    .getSimpleName()
                    .toLowerCase(java.util.Locale.ROOT)
                    .contains("garden");
    }

    private static boolean water(Block block) {
        return block.getMaterial() == Material.water;
    }

    private static boolean naturalHive(Block block) {
        return "Forestry:beehives".equals(String.valueOf(Block.blockRegistry.getNameForObject(block)));
    }

    private static boolean naturalGround(Block block) {
        if (block instanceof GTBlockOre) return true;
        if (block == Blocks.stone || block == Blocks.dirt
            || block == Blocks.grass
            || block == Blocks.sand
            || block == Blocks.gravel
            || block == Blocks.clay
            || block == Blocks.snow
            || block == Blocks.snow_layer
            || block == Blocks.ice
            || block == Blocks.packed_ice
            || block == Blocks.mycelium) return true;
        String name = String.valueOf(Block.blockRegistry.getNameForObject(block));
        return name.startsWith("gregtech:gt.blockstones")
            || name.startsWith("BiomesOPlenty:")
                && (block.getMaterial() == Material.ground || block.getMaterial() == Material.grass
                    || name.equals("BiomesOPlenty:stone"))
            || name.startsWith("ExtraBiomesXL:")
                && (block.getMaterial() == Material.ground || block.getMaterial() == Material.grass);
    }

    private static boolean replaceable(World world, int x, int y, int z) {
        Block block = world.getBlock(x, y, z);
        return world.getTileEntity(x, y, z) == null && block != Blocks.bedrock
            && (decoration(world, x, y, z, block) || water(block) || naturalGround(block));
    }

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
        if (!eligible(world) || !candidate(world.getSeed(), chunkX, chunkZ)) return;
        String id = "altar:" + chunkX + ":" + chunkZ;
        SpacetimeAltarIndex index = SpacetimeAltarIndex.get(world);
        if (index.rejected(id) || index.find(id) != null) return;
        int x = chunkX * 16 + 1, z = chunkZ * 16 + 1, base = 1;
        List<Original> originals = new ArrayList<>();
        boolean success = false, writing = false;
        try {
            // The platform follows solid terrain and water, ignoring foliage and canopy gaps.
            for (int dx = 0; dx < 14; dx++) for (int dz = 0; dz < 14; dz++) {
                int px = x + dx, pz = z + dz, surface = -1;
                for (int y = Math.min(255, world.getHeightValue(px, pz) - 1); y >= 1; y--) {
                    Block old = world.getBlock(px, y, pz);
                    if (old.getMaterial() == Material.lava) throw new PlacementRefused("Protected surface");
                    if (naturalHive(old) && old.getMaterial()
                        .isSolid()) {
                        surface = Math.max(surface, y + 1);
                        break;
                    }
                    if (world.getTileEntity(px, y, pz) != null && !(surface > y && old.getMaterial()
                        .isSolid())) throw new PlacementRefused("Protected surface tile");
                    if (water(old)) {
                        surface = Math.max(surface, y);
                        continue;
                    }
                    if (decoration(world, px, y, pz, old)) continue;
                    if (!old.getMaterial()
                        .isSolid()) throw new PlacementRefused("Unsupported surface");
                    surface = Math.max(surface, y);
                    break;
                }
                if (surface < 1) throw new PlacementRefused("No supported terrain");
                base = Math.max(base, surface);
            }
            // Extreme natural peaks can be cut within the unchanged full air-inclusive model.
            base = Math.min(base, 241);
            List<Cell> model = model();
            List<Cell> foundation = new ArrayList<>();
            Block support = com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterRuntime
                .resolve("Thaumcraft:blockCosmeticSolid");
            for (Cell cell : model) if (cell.y == 0 && cell.block != Blocks.air) {
                int px = x + cell.x, pz = z + cell.z;
                boolean anchored = false;
                for (int y = base - 1; y >= 1; y--) {
                    Block old = world.getBlock(px, y, pz);
                    if (old.getMaterial() == Material.lava) throw new PlacementRefused("Protected foundation");
                    // Any existing solid rock/ore can bear weight without being overwritten.
                    if (old.getMaterial()
                        .isSolid() && !decoration(world, px, y, pz, old)) {
                        anchored = true;
                        break;
                    }
                    if (world.getTileEntity(px, y, pz) != null) throw new PlacementRefused("Protected foundation tile");
                    if (!replaceable(world, px, y, pz)) throw new PlacementRefused("Unsafe foundation");
                    foundation.add(new Cell(cell.x, y - base, cell.z, support, 7));
                }
                if (!anchored) throw new PlacementRefused("No foundation anchor");
            }
            List<Cell> placement = new ArrayList<>(foundation);
            placement.addAll(model);
            // Capture the whole transaction before the first write, including core/story/chest cells.
            for (Cell cell : placement) {
                int px = x + cell.x, py = base + cell.y, pz = z + cell.z;
                if (!replaceable(world, px, py, pz)) throw new PlacementRefused("Protected altar volume");
                originals.add(new Original(px, py, pz, world.getBlock(px, py, pz), world.getBlockMetadata(px, py, pz)));
            }
            writing = true;
            for (Cell cell : placement) {
                int px = x + cell.x, py = base + cell.y, pz = z + cell.z;
                if (world.getBlock(px, py, pz) != cell.block || world.getBlockMetadata(px, py, pz) != cell.meta)
                    if (!world.setBlock(px, py, pz, cell.block, cell.meta, 2))
                        throw new PlacementRefused("Altar placement refused");
            }
            if (!world.setBlock(x + 6, base + 8, z + 6, SpacetimeAltarBlocks.core, 0, 2))
                throw new PlacementRefused("Altar core refused");
            TileEntity tile = world.getTileEntity(x + 6, base + 8, z + 6);
            if (!(tile instanceof TileSpacetimeAltar)) throw new PlacementRefused("Altar core tile missing");
            ((TileSpacetimeAltar) tile).bind(id);
            if (!SpacetimeAltarStory.place(world, x, base, z, id))
                throw new PlacementRefused("Altar story/chest refused");
            index.add(id, x + 6, base + 8, z + 6);
            SpacetimeAltarIndex.Entry entry = index.find(id);
            success = entry != null && entry.valid && entry.x == x + 6 && entry.y == base + 8 && entry.z == z + 6;
            if (!success) throw new PlacementRefused("Altar index refused");
        } catch (RuntimeException failure) {
            if (failure instanceof PlacementRefused && !writing) index.reject(id);
            com.miaokatze.gtsr.main.GTSteamReborn.LOG
                .warn("[GTSR] Altar placement deferred at " + x + "," + z + ": " + failure.getMessage());
        } finally {
            if (!success && writing) for (int i = originals.size() - 1; i >= 0; i--) {
                Original old = originals.get(i);
                try {
                    if (world.getBlock(old.x, old.y, old.z) == old.block
                        && world.getBlockMetadata(old.x, old.y, old.z) == old.meta
                        && world.getTileEntity(old.x, old.y, old.z) == null) continue;
                    world.removeTileEntity(old.x, old.y, old.z);
                    world.setBlock(old.x, old.y, old.z, old.block, old.meta, 2);
                    if (world.getBlock(old.x, old.y, old.z) != old.block
                        || world.getBlockMetadata(old.x, old.y, old.z) != old.meta
                        || world.getTileEntity(old.x, old.y, old.z) != null)
                        throw new IllegalStateException("Restored altar cell does not match snapshot");
                } catch (RuntimeException rollbackFailure) {
                    com.miaokatze.gtsr.main.GTSteamReborn.LOG.error(
                        "[GTSR] Altar rollback could not restore " + old.x + "," + old.y + "," + old.z,
                        rollbackFailure);
                }
            }
        }
    }

    private static final class PlacementRefused extends RuntimeException {

        PlacementRefused(String message) {
            super(message);
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
