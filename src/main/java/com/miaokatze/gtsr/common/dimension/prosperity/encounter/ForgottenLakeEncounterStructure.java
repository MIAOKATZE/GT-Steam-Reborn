package com.miaokatze.gtsr.common.dimension.prosperity.encounter;

import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.framework.structure.StructureBuilder;

/** Shared deterministic geometry; mutable nodes are initialized only by their owner chunk. */
public final class ForgottenLakeEncounterStructure {

    public static final int PLATFORM_COUNT = 4, GUARDS_PER_PLATFORM = 3;

    public static String encounterId(World w, int ax, int az) {
        return w.provider.dimensionId + ":" + ax + ":" + az;
    }

    public static int[] platform(int ax, int az, int y0, int trunkH, int i) {
        int[] dx = { 30, 0, -30, 0 }, dz = { 0, 30, 0, -30 };
        return new int[] { ax + dx[i], y0 + trunkH - 36 + i * 9, az + dz[i] };
    }

    private static void block(StructureBuilder b, int x, int y, int z, Object o) {
        b.setBlock(x, y, z, o, 0, 2);
    }

    private static void deck(StructureBuilder b, int x, int y, int z, int r) {
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            block(b, x + dx, y, z + dz, BlocksGTSR.ruinedCasing);
            for (int h = 1; h <= 14; h++) block(b, x + dx, y + h, z + dz, Blocks.air);
            if ((Math.abs(dx) == r || Math.abs(dz) == r) && Math.abs(dx) + Math.abs(dz) > r + 2)
                block(b, x + dx, y + 1, z + dz, Blocks.iron_bars);
        }
    }

    public static void placeInto(World w, StructureBuilder b, int ax, int az, int y0, int trunkH) {
        // Complete the final eight-block riser as well: its west-axis landing may fall after its first step.
        // Square spiral: eight horizontal blocks for each rise, twelve blocks of clearance per turn.
        int top = y0 + trunkH + 2, steps = (top - y0) * 8 + 7;
        for (int s = 0; s <= steps; s++) {
            int t = s % 96, x, z;
            if (t < 24) {
                x = -12 + t;
                z = -12;
            } else if (t < 48) {
                x = 12;
                z = -12 + t - 24;
            } else if (t < 72) {
                x = 12 - (t - 48);
                z = 12;
            } else {
                x = -12;
                z = 12 - (t - 72);
            }
            int y = y0 + s / 8;
            for (int a = 0; a < 3; a++) {
                int xx = ax + x + (Math.abs(z) == 12 ? 0 : (x > 0 ? a : -a));
                int zz = az + z + (Math.abs(z) == 12 ? (z > 0 ? a : -a) : 0);
                block(b, xx, y, zz, BlocksGTSR.prosperityZenithLog);
                for (int h = 1; h <= 3; h++) block(b, xx, y + h, zz, Blocks.air);
            }
        }
        for (int i = 0; i < 4; i++) {
            int[] p = platform(ax, az, y0, trunkH, i);
            deck(b, p[0], p[1], p[2], 6);
            int[] offset = { 4, 7, 10, 1 };
            int base = y0 + offset[i] + 12 * Math.floorDiv(p[1] - y0 - offset[i], 12);
            for (int j = 14; j <= 30; j++) for (int width = -1; width <= 1; width++) {
                int x = ax + (i == 0 ? j : i == 2 ? -j : width), z = az + (i == 1 ? j : i == 3 ? -j : width);
                int y = Math.min(p[1], base + j - 14);
                block(b, x, y, z, BlocksGTSR.prosperityZenithLog);
                for (int h = 1; h <= 3; h++) block(b, x, y + h, z, Blocks.air);
            }

        }
        deck(b, ax, top, az, 9);
        int topBase = y0 + 10 + 12 * Math.floorDiv(top - y0 - 10, 12);
        for (int j = -14; j <= 0; j++) for (int a = -1; a <= 1; a++) {
            int y = Math.min(top, topBase + j + 14);
            block(b, ax + j, y, az + a, BlocksGTSR.prosperityZenithLog);
            for (int h = 1; h <= 3; h++) block(b, ax + j, y + h, az + a, Blocks.air);
        }

    }

    private static boolean owner(int x, int z, int cx, int cz) {
        return x >> 4 == cx && z >> 4 == cz;
    }

    private static void chest(World w, ForgottenLakeEncounterData d, String id, String node, int x, int y, int z,
        int tier, int platform, int cx, int cz) {
        if (!owner(x, z, cx, cz) || d.created(id, node)) return;
        if (!w.setBlock(x, y, z, ForgottenLakeEncounterRegistry.sealedChest, 0, 2)) return;
        TileEntity t = w.getTileEntity(x, y, z);
        if (t instanceof TileEntitySealedChest) {
            ((TileEntitySealedChest) t).initialize(tier, id, platform);
            d.createdNode(id, node);
        }
    }

    public static void initializeChunk(World w, int ax, int az, int y0, int trunkH, int cx, int cz) {
        if (w.isRemote) return;
        ForgottenLakeEncounterData d = ForgottenLakeEncounterData.get(w);
        String id = encounterId(w, ax, az);
        for (int i = 0; i < 4; i++) {
            int[] p = platform(ax, az, y0, trunkH, i);
            chest(w, d, id, "platformChest" + i, p[0], p[1] + 1, p[2], 2, i, cx, cz);
            for (int n = 0; n < 3; n++) {
                int x = p[0] - 3 + n * 3, z = p[2] + 3;
                String node = "guard" + (i * 3 + n);
                if (owner(x, z, cx, cz) && !d.created(id, node) && !d.platformCleared(id, i)) {
                    EntityResidualOathguard g = new EntityResidualOathguard(w);
                    g.initialize(id, i, x + .5, p[1] + 1, z + .5);
                    g.setOrdinal(n);
                    if (w.spawnEntityInWorld(g)) d.createdNode(id, node);
                }
            }
        }
        int top = y0 + trunkH + 3;
        if (owner(ax, az, cx, cz) && !d.created(id, "king") && !d.kingDead(id)) {
            EntitySilentKing king = new EntitySilentKing(w);
            king.initialize(id, -1, ax + .5, top, az + .5);
            if (w.spawnEntityInWorld(king)) d.createdNode(id, "king");
        }
        for (int i = 0; i < 9; i++) {
            int x = ax - 6 + (i % 3) * 6, z = az - 6 + (i / 3) * 6;
            if (x == ax && z == az) z = az + 8;
            chest(w, d, id, "throneChest" + i, x, top, z, i < 3 ? 3 : i < 6 ? 4 : 5, -1, cx, cz);
        }
    }
}
