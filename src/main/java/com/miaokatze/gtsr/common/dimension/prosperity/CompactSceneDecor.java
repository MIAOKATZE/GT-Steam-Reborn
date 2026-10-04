package com.miaokatze.gtsr.common.dimension.prosperity;

import net.minecraft.block.Block;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.framework.structure.GTSRWorldgenHash;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterCatalog;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterPrefab;

/** Owner-chunk, native-material landscaping outside the complete authored solid/AIR envelope. */
public final class CompactSceneDecor {

    private static final long SALT = 0x4150524F4E3730L;
    private static volatile boolean[] factorySurface, foundrySurface;

    private CompactSceneDecor() {}

    public static void decorate(World world, int cx, int cz) {
        long seed = world.getSeed();
        for (CompactSceneTerrain.Branch b : CompactSceneTerrain.cell(
            seed,
            Math.floorDiv(cx << 4, CompactSceneTerrain.CELL_SIZE),
            Math.floorDiv(cz << 4, CompactSceneTerrain.CELL_SIZE))) {
            for (int z = cz << 4; z < (cz << 4) + 16; z++) for (int x = cx << 4; x < (cx << 4) + 16; x++) {
                boolean apron = CompactSceneTerrain.decorColumn(b, x, z, 0);
                boolean core = landscapeColumn(b.roster, x - b.originX(), z - b.originZ(), 0);
                if (!apron && !core) continue;
                long h = GTSRWorldgenHash.cellSeed(seed, x, z, SALT);
                int y = ProsperityTerrainProfile.heightAt(seed, x, z);
                Block top = b.roster == 0 ? BlocksGTSR.prosperitySteppeTop : BlocksGTSR.prosperityForestTop;
                // No height scan: geology and decorations share the same pure column supplier.
                if (world.getBlock(x, y, z) != top || !world.isAirBlock(x, y + 1, z)) continue;
                int pick = (int) Math.floorMod(h, 100);
                // Patchy meadow grows over the buried roof; only short flora may enter its core.
                long patch = GTSRWorldgenHash
                    .cellSeed(seed, Math.floorDiv(x, 9), Math.floorDiv(z, 9), SALT ^ 0x464C4F5241L);
                int floraChance = apron ? b.roster == 1 ? 33 : 27 : Math.floorMod(patch, 5) == 0 ? 38 : 10;
                if (pick < floraChance) {
                    Block flora = pick < 5
                        ? (b.roster == 0 ? BlocksGTSR.prosperityFlowerRust : BlocksGTSR.prosperityFlowerPatina)
                        : (b.roster == 0 ? BlocksGTSR.prosperityTuftRust : BlocksGTSR.prosperityTuftCopper);
                    air(world, x, y + 1, z, flora);
                } else if (pick == 39
                    && (CompactSceneTerrain.decorColumn(b, x, z, 2)
                        || landscapeColumn(b.roster, x - b.originX(), z - b.originZ(), 2))
                    && (x & 15) >= 2
                    && (x & 15) <= 13
                    && (z & 15) >= 2
                    && (z & 15) <= 13) {
                        // Low, rounded native stone outcrops sit on the terrain, never erode it.
                        for (int oz = -1; oz <= 1; oz++) for (int ox = -1; ox <= 1; ox++) {
                            if (ox * ox + oz * oz > 1) continue;
                            int gy = ProsperityTerrainProfile.heightAt(seed, x + ox, z + oz);
                            if (world.getBlock(x + ox, gy, z + oz) == top)
                                air(world, x + ox, gy + 1, z + oz, BlocksGTSR.prosperityStone);
                        }
                        air(world, x, y + 2, z, BlocksGTSR.prosperityStone);
                    } else if (pick == 54
                        && (CompactSceneTerrain.decorColumn(b, x, z, 3)
                            || landscapeColumn(b.roster, x - b.originX(), z - b.originZ(), 3))
                        && (x & 15) >= 3
                        && (x & 15) <= 12
                        && (z & 15) >= 3
                        && (z & 15) <= 12) {
                            tree(world, x, y, z, (b.roster == 0 ? 3 : 4) + (int) (h >>> 16 & 1));
                        }
            }
        }
    }

    /** Core meadow excludes every authored surface solid/AIR column and the complete entry approach. */
    public static boolean factoryFloraColumn(CompactSceneTerrain.Branch b, int x, int z) {
        return b.roster == 0 && landscapeColumn(0, x - b.originX(), z - b.originZ(), 0);
    }

    public static boolean landscapeColumn(int roster, int lx, int lz, int radius) {
        if (lx - radius < 0 || lx + radius >= 120 || lz - radius < 0 || lz + radius >= 120) return false;
        if (roster == 0 && lx + radius >= 5 && lx - radius <= 34 && lz + radius >= 72 && lz - radius <= 110)
            return false;
        boolean[] mask = surfaceMask(roster);
        for (int z = lz - radius; z <= lz + radius; z++)
            for (int x = lx - radius; x <= lx + radius; x++) if (mask[z * 120 + x]) return false;
        return true;
    }

    private static boolean[] surfaceMask(int roster) {
        boolean[] mask = roster == 0 ? factorySurface : foundrySurface;
        if (mask != null) return mask;
        synchronized (CompactSceneDecor.class) {
            mask = roster == 0 ? factorySurface : foundrySurface;
            if (mask != null) return mask;
            mask = new boolean[120 * 120];
            RemasterPrefab plan = RemasterCatalog.get(roster == 0 ? "subsided_factory" : "fallen_foundry", 0);
            // One startup-sized pass, never a slice read per decorated column. All material kinds,
            // including explicit AIR, reserve the column whenever their authored Y reaches ground.
            for (int cz = 0; cz < 8; cz++)
                for (int cx = 0; cx < 8; cx++) for (RemasterPrefab.Run r : plan.slice(cx, cz)) {
                    if (roster == 0 && r.y < 0 || r.z < 0 || r.z >= 120) continue;
                    for (int i = 0; i < r.length; i++) {
                        int lx = r.x + i;
                        if (lx >= 0 && lx < 120) mask[r.z * 120 + lx] = true;
                    }
                }
            if (roster == 0) factorySurface = mask;
            else foundrySurface = mask;
            return mask;
        }
    }

    private static void tree(World world, int x, int y, int z, int height) {
        // Preflight the whole crown and stem; a failed check leaves every existing block untouched.
        for (int dy = 1; dy <= height + 2; dy++) {
            int radius = dy < height - 1 ? 0 : 2;
            for (int dz = -radius; dz <= radius; dz++)
                for (int dx = -radius; dx <= radius; dx++) if (!world.isAirBlock(x + dx, y + dy, z + dz)) return;
        }
        for (int dy = 1; dy <= height; dy++) air(world, x, y + dy, z, BlocksGTSR.prosperityCopperLog);
        for (int dy = height - 1; dy <= height + 2; dy++)
            for (int dz = -2; dz <= 2; dz++) for (int dx = -2; dx <= 2; dx++) {
                int radius = dy == height + 2 ? 1 : 2;
                if (dx * dx + dz * dz <= radius * radius + 1)
                    air(world, x + dx, y + dy, z + dz, BlocksGTSR.prosperityCopperLeaves);
            }
    }

    private static void air(World world, int x, int y, int z, Block block) {
        if (block != null && y > 0 && y < 255 && world.isAirBlock(x, y, z)) world.setBlock(x, y, z, block, 0, 2);
    }
}
