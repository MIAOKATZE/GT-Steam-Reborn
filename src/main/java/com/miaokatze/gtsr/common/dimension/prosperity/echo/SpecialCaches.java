package com.miaokatze.gtsr.common.dimension.prosperity.echo;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.dimension.framework.structure.GTSRWorldgenHash;
import com.miaokatze.gtsr.common.dimension.framework.structure.PlacementGate;
import com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile;
import com.miaokatze.gtsr.common.dimension.prosperity.architecture.RoyalArchitecture;
import com.miaokatze.gtsr.common.dimension.prosperity.architecture.RuinsArchitecture;
import com.miaokatze.gtsr.common.dimension.prosperity.encounter.ForgottenLakeEncounterRegistry;
import com.miaokatze.gtsr.common.dimension.prosperity.encounter.TileEntitySealedChest;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.GrottoCarver;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityPlan;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityPlanner;

/** Three independent story caches. Call only from fresh populate, never from login or chunk-load events. */
public final class SpecialCaches {

    public static final int MIRE_EVENT = 8, ARCHIVE_EVENT = 9, RAIL_EVENT = 10;

    private SpecialCaches() {}

    public static String id(String type, long seed, int x, int z) {
        return "cache:" + type + ":" + seed + ":" + x + ":" + z;
    }

    /** The final water-top predicate proves an actual small pool, rather than a rejected wet candidate. */
    public static boolean mireCandidate(long seed, int x, int z) {
        if (!GrottoCarver.grottoSiteAt(seed, x, z) || !GrottoCarver.grottoLandingAt(seed, x, z)
            || GrottoCarver.grottoWetAt(seed, x, z)) return false;
        int floor = GrottoCarver.grottoFloorYAt(seed, x, z);
        if (ProsperityTerrainProfile.heightAt(seed, x, z) - floor < 3) return false;
        for (int dx = -12; dx <= 12; dx += 2) for (int dz = -12; dz <= 12; dz += 2) {
            if (dx * dx + dz * dz <= 144 && GrottoCarver.grottoWaterTopAt(seed, x + dx, z + dz) >= 0) return true;
        }
        // LandingAt already proves a wet neighbour one block away; a two-step scan can miss odd columns.
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) if (GrottoCarver.grottoWaterTopAt(seed, x + dx, z + dz) >= 0) return true;
        return false;
    }

    public static boolean cityCandidate(CityPlan city) {
        return city != null && city.plotInCircle(0, 0);
    }

    /** A rare single-chunk 12x10 site, matching the real prosperity biome authority and strict dry gate. */
    public static boolean railCandidate(long seed, int cx, int cz) {
        long h = GTSRWorldgenHash.cellSeed(seed, Math.floorDiv(cx, 24), Math.floorDiv(cz, 24), 0x5241494CL);
        int cellX = Math.floorDiv(cx, 24) * 24, cellZ = Math.floorDiv(cz, 24) * 24;
        if (cx != cellX + (int) Math.floorMod(h >>> 8, 24) || cz != cellZ + (int) Math.floorMod(h >>> 24, 24))
            return false;
        int biome = CityPlanner.bandIndexAt(seed, cx, cz);
        if (biome != 0 && biome != 2) return false;
        if (CityPlanner.citiesNear(seed, cx, cz).length != 0 || !RuinsSitePlanner.near(seed, cx, cz)
            .isEmpty()) return false;
        int x = (cx << 4) + 2, z = (cz << 4) + 2;
        if (!PlacementGate.dryFootprintStrict(seed, x, z - 1, x + 11, z + 9)) return false;
        int min = 255, max = 0;
        for (int xx = x; xx <= x + 11; xx++) for (int zz = z; zz <= z + 9; zz++) {
            int y = ProsperityTerrainProfile.heightAt(seed, xx, zz);
            min = Math.min(min, y);
            max = Math.max(max, y);
        }
        int platform = ProsperityTerrainProfile.heightAt(seed, x + 6, z + 5) + 1;
        int exit = ProsperityTerrainProfile.heightAt(seed, x + 10, z - 1) + 1;
        return max - min <= 8 && Math.abs(platform - exit) <= 9;
    }

    /** Cities are passed from the existing actual city planner; an empty list selects non-city caches. */
    public static void generate(World w, long seed, int cx, int cz, CityPlan[] cities) {
        generate(w, seed, cx, cz, cities, true);
    }

    public static void generate(World w, long seed, int cx, int cz, CityPlan[] cities, boolean allowRail) {
        if (w.isRemote) return;
        if (cities != null && cities.length != 0) {
            for (CityPlan city : cities) if (cityCandidate(city)) cityCellar(w, seed, cx, cz, city);
            return;
        }
        if (!RuinsSitePlanner.near(seed, cx, cz)
            .isEmpty()) return;
        long roll = GTSRWorldgenHash.cellSeed(seed, cx, cz, 0x4D495245L);
        if (Math.floorMod(roll, 8) == 0) {
            for (int dx = 2; dx <= 12; dx += 2) for (int dz = 2; dz <= 12; dz += 2) {
                int x = (cx << 4) + dx, z = (cz << 4) + dz;
                if (mireCandidate(seed, x, z) && mireCache(w, seed, cx, cz, x, z)) return;
            }
        }
        if (allowRail && railCandidate(seed, cx, cz)) railStation(w, seed, cx, cz);
    }

    private static Block material(String key) {
        Block b = key.startsWith("royal_") ? RoyalArchitecture.get(key.substring(6)) : RuinsArchitecture.get(key);
        if (b == null) throw new IllegalArgumentException("Unknown cache material: " + key);
        return b;
    }

    /** No floor excavation or water writes: a shelf, one lamp and chest occupy existing dry cavity. */
    private static boolean mireCache(World w, long seed, int cx, int cz, int x, int z) {
        String id = id("mire", seed, x, z);
        RuinsEncounterData data = RuinsEncounterData.get(w);
        int y = GrottoCarver.grottoFloorYAt(seed, x, z) + 1;
        if (data.created(id, "chest")) return true;
        if (!dryStanding(w, cx, cz, x, y, z, 3)) return false;
        // Decoration remains wholly in the owner chunk and preserves the player's adjacent escape space.
        if (!dryStanding(w, cx, cz, x + 1, y, z, 3)) return false;
        if (!data.created(id, "geom:" + cx + ":" + cz)) {
            if (!decorationAvailable(w, cx, cz, x - 1, y, z)) return false;
            if (!put(w, cx, cz, x - 1, y, z, material("royal_heartwood_slab"), 0)
                || !put(w, cx, cz, x - 1, y + 1, z, material("amber_lamp"), 0)
                || !put(w, cx, cz, x - 1, y + 2, z, Blocks.leaves, 4)) return false;
            data.markCreated(id, "geom:" + cx + ":" + cz);
        }
        return chest(w, cx, cz, x, y, z, id, MIRE_EVENT, "mire_sounding_weight");
    }

    /** A partial failed write may resume using its already placed expected ornament, never unknown solids. */
    private static boolean decorationAvailable(World w, int cx, int cz, int x, int y, int z) {
        if ((x >> 4) != cx || (z >> 4) != cz
            || !w.getBlock(x, y - 1, z)
                .getMaterial()
                .blocksMovement())
            return false;
        Block[] expected = { material("royal_heartwood_slab"), material("amber_lamp"), Blocks.leaves };
        for (int dy = 0; dy < 3; dy++) {
            if (w.getTileEntity(x, y + dy, z) != null) return false;
            Block actual = w.getBlock(x, y + dy, z);
            if (!w.isAirBlock(x, y + dy, z) && actual != expected[dy]) return false;
        }
        return true;
    }

    private static boolean dryStanding(World w, int cx, int cz, int x, int y, int z, int head) {
        if ((x >> 4) != cx || (z >> 4) != cz
            || !w.getBlock(x, y - 1, z)
                .getMaterial()
                .blocksMovement())
            return false;
        for (int dy = 0; dy < head; dy++)
            if (!w.isAirBlock(x, y + dy, z) || w.getTileEntity(x, y + dy, z) != null) return false;
        return true;
    }

    private static void cityCellar(World w, long seed, int cx, int cz, CityPlan city) {
        int x = city.getCenterX() + 8, z = city.getCenterZ() + 8;
        int ground = ProsperityTerrainProfile.heightAt(seed, x, z) + 1, floor = ground - 5;
        if ((cx << 4) > x + 2 || (cx << 4) + 15 < x - 3 || (cz << 4) > z + 2 || (cz << 4) + 15 < z - 3 || floor < 4)
            return;
        String id = id("archive", seed, x, z);
        RuinsEncounterData data = RuinsEncounterData.get(w);
        String geom = "geom:" + cx + ":" + cz;
        if (!data.created(id, geom)) {
            boolean shaftOwner = ((x + 1) >> 4) == cx && ((z + 1) >> 4) == cz;
            int exitY = shaftOwner ? cityExitY(w, cx, cz, x + 1, z + 1, ground + 1) : ground + 2;
            if (shaftOwner && exitY < 0) return;
            // Preflight every local volume before any write. Never inspect or force-load a neighbouring chunk.
            for (int xx = x - 3; xx <= x + 2; xx++) for (int zz = z - 3; zz <= z + 2; zz++) {
                if ((xx >> 4) != cx || (zz >> 4) != cz) continue;
                for (int yy = floor; yy <= ground + 2; yy++)
                    if (w.getTileEntity(xx, yy, zz) != null || w.getBlock(xx, yy, zz)
                        .getMaterial()
                        .isLiquid()) return;
            }
            if (shaftOwner) {
                for (int yy = floor + 1; yy <= exitY + 1; yy++) for (int xx = x + 1; xx <= x + 2; xx++)
                    if (w.getTileEntity(xx, yy, z + 1) != null || w.getBlock(xx, yy, z + 1)
                        .getMaterial()
                        .isLiquid()) return;
            }
            boolean ok = true;
            for (int xx = x - 3; xx <= x + 2; xx++) for (int zz = z - 3; zz <= z + 2; zz++) {
                if ((xx >> 4) != cx || (zz >> 4) != cz) continue;
                ok &= put(w, cx, cz, xx, floor, zz, material("rootbound_brick"), 0);
                for (int yy = floor + 1; yy <= floor + 3; yy++) {
                    boolean wall = xx == x - 3 || xx == x + 2 || zz == z - 3 || zz == z + 2;
                    ok &= put(w, cx, cz, xx, yy, zz, wall ? material("royal_heartwood") : Blocks.air, 0);
                }
                ok &= put(w, cx, cz, xx, floor + 4, zz, material("royal_heartwood"), 0);
            }
            for (int yy = floor + 1; yy <= exitY; yy++) {
                if (shaftOwner) {
                    ok &= put(w, cx, cz, x + 2, yy, z + 1, material("royal_oathwood"), 0);
                    ok &= put(w, cx, cz, x + 1, yy, z + 1, Blocks.ladder, 4);
                }
            }
            if (shaftOwner) {
                ok &= put(w, cx, cz, x + 1, exitY + 1, z + 1, Blocks.air, 0);
                // The exit neighbour was proven air with a supporting floor; keep two blocks of headroom.
                for (int[] step : new int[][] { { -1, 0 }, { 0, -1 }, { 0, 1 } }) {
                    int ex = x + 1 + step[0], ez = z + 1 + step[1];
                    if ((ex >> 4) == cx && (ez >> 4) == cz
                        && w.isAirBlock(ex, exitY, ez)
                        && w.isAirBlock(ex, exitY + 1, ez)
                        && w.getBlock(ex, exitY - 1, ez)
                            .getMaterial()
                            .blocksMovement()) {
                        ok &= put(w, cx, cz, ex, exitY, ez, Blocks.air, 0);
                        ok &= put(w, cx, cz, ex, exitY + 1, ez, Blocks.air, 0);
                        break;
                    }
                }
            }
            ok &= put(w, cx, cz, x - 2, floor + 1, z - 1, material("rootwood_archive"), 0);
            ok &= put(w, cx, cz, x - 2, floor + 2, z - 1, material("amber_lamp"), 0);
            ok &= put(w, cx, cz, x, floor + 2, z - 2, material("foundry_nameplate"), 0);
            if (ok) data.markCreated(id, geom);
        }
        if (data.created(id, geom) && (x >> 4) == cx && (z >> 4) == cz)
            chest(w, cx, cz, x, floor + 1, z, id, ARCHIVE_EVENT, "sealed_archive_tube");
    }

    /** Find a supported side exit in the actual city template, including rooms or roof terraces. */
    private static int cityExitY(World w, int cx, int cz, int x, int z, int minimum) {
        for (int y = minimum; y <= Math.min(253, minimum + 40); y++) {
            if (!w.isAirBlock(x, y, z) || !w.isAirBlock(x, y + 1, z)) continue;
            for (int[] step : new int[][] { { -1, 0 }, { 0, -1 }, { 0, 1 } }) {
                int ex = x + step[0], ez = z + step[1];
                if ((ex >> 4) == cx && (ez >> 4) == cz
                    && w.isAirBlock(ex, y, ez)
                    && w.isAirBlock(ex, y + 1, ez)
                    && w.getBlock(ex, y - 1, ez)
                        .getMaterial()
                        .blocksMovement()
                    && w.getTileEntity(ex, y - 1, ez) == null) return y;
            }
        }
        return -1;
    }

    private static void railStation(World w, long seed, int cx, int cz) {
        int x = (cx << 4) + 2, z = (cz << 4) + 2;
        int floor = ProsperityTerrainProfile.heightAt(seed, x + 6, z + 5) + 1;
        String id = id("rail", seed, x, z);
        RuinsEncounterData data = RuinsEncounterData.get(w);
        String geom = "geom:" + cx + ":" + cz;
        if (!data.created(id, geom)) {
            for (int xx = x; xx < x + 12; xx++) for (int zz = z; zz < z + 10; zz++)
                for (int yy = Math.min(floor - 8, ProsperityTerrainProfile.heightAt(seed, xx, zz)); yy
                    <= Math.max(floor + 5, ProsperityTerrainProfile.heightAt(seed, xx, zz) + 2); yy++)
                    if (w.getTileEntity(xx, yy, zz) != null || w.getBlock(xx, yy, zz)
                        .getMaterial()
                        .isLiquid()) return;
            int exitFloor = ProsperityTerrainProfile.heightAt(seed, x + 10, z - 1) + 1;
            for (int zz = z; zz <= z + 9; zz++) {
                for (int yy = Math.min(floor, exitFloor); yy <= Math.max(floor, exitFloor) + 4; yy++)
                    if (w.getTileEntity(x + 10, yy, zz) != null || w.getBlock(x + 10, yy, zz)
                        .getMaterial()
                        .isLiquid()) return;
            }
            boolean ok = true;
            for (int xx = x; xx < x + 12; xx++) for (int zz = z; zz < z + 10; zz++) {
                int ground = ProsperityTerrainProfile.heightAt(seed, xx, zz);
                for (int yy = Math.min(ground, floor); yy < floor; yy++)
                    ok &= put(w, cx, cz, xx, yy, zz, material("rootbound_brick"), 0);
                ok &= put(w, cx, cz, xx, floor, zz, material("mossroot_paving"), 0);
                for (int yy = floor + 1; yy <= Math.max(floor + 4, ground + 2); yy++)
                    ok &= put(w, cx, cz, xx, yy, zz, Blocks.air, 0);
            }
            for (int xx = x; xx < x + 12; xx++) ok &= put(w, cx, cz, xx, floor + 1, z + 1, Blocks.rail, 1);
            for (int xx : new int[] { x + 1, x + 8 })
                for (int zz : new int[] { z + 4, z + 8 }) for (int yy = floor + 1; yy <= floor + 4; yy++)
                    ok &= put(w, cx, cz, xx, yy, zz, material("royal_oathwood"), 0);
            for (int xx = x + 1; xx <= x + 8; xx++) for (int zz = z + 4; zz <= z + 8; zz++)
                ok &= put(w, cx, cz, xx, floor + 5, zz, material("riveted_plate_slab"), 0);
            ok &= put(w, cx, cz, x + 2, floor + 1, z + 7, material("salvage_workbench"), 0);
            ok &= put(w, cx, cz, x + 3, floor + 2, z + 7, material("pressure_gauge"), 3);
            ok &= put(w, cx, cz, x + 8, floor + 3, z + 4, material("amber_lamp"), 0);
            // A ten-column side ramp meets the measured front approach; it is never buried under paving.
            int delta = floor - exitFloor;
            for (int i = 0; i <= 9; i++) {
                int yy = floor - (int) Math.floor(i * delta / 9D);
                int zz = z + 9 - i;
                for (int dy = yy + 1; dy <= Math.max(floor, yy) + 4; dy++)
                    ok &= put(w, cx, cz, x + 10, dy, zz, Blocks.air, 0);
                ok &= put(w, cx, cz, x + 10, yy, zz, material("firebrick_stairs"), delta >= 0 ? 2 : 3);
            }
            if (ok) data.markCreated(id, geom);
        }
        if (data.created(id, geom)) chest(w, cx, cz, x + 6, floor + 1, z + 7, id, RAIL_EVENT, "rail_repair_token");
    }

    /** Out-of-slice decorations are a no-op, never a neighbour write or neighbour lookup. */
    private static boolean put(World w, int cx, int cz, int x, int y, int z, Block b, int meta) {
        if ((x >> 4) != cx || (z >> 4) != cz) return true;
        if (y < 1 || y > 254) return false;
        return w.getBlock(x, y, z) == b && w.getBlockMetadata(x, y, z) == meta || w.setBlock(x, y, z, b, meta, 2);
    }

    private static boolean chest(World w, int cx, int cz, int x, int y, int z, String id, int event, String relic) {
        if ((x >> 4) != cx || (z >> 4) != cz) return false;
        RuinsEncounterData data = RuinsEncounterData.get(w);
        if (data.created(id, "chest")) return true;
        if (!w.isAirBlock(x, y, z) || w.getTileEntity(x, y, z) != null) return false;
        if (!w.setBlock(x, y, z, ForgottenLakeEncounterRegistry.sealedChest, 2, 2)) return false;
        TileEntity tile = w.getTileEntity(x, y, z);
        if (!(tile instanceof TileEntitySealedChest)) return false;
        ((TileEntitySealedChest) tile).initializeStoryCache(1, id, event, relic);
        data.markCreated(id, "chest");
        return true;
    }
}
