package com.miaokatze.gtsr.common.dimension.prosperity.encounter;

import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.framework.structure.StructureBuilder;
import com.miaokatze.gtsr.common.dimension.prosperity.architecture.RoyalArchitecture;

/** Shared deterministic geometry; mutable nodes are initialized only by their owner chunk. */
public final class ForgottenLakeEncounterStructure {

    public static final int PLATFORM_COUNT = 8, GUARDS_PER_PLATFORM = 3;

    public static String encounterId(World w, int ax, int az) {
        return w.provider.dimensionId + ":" + ax + ":" + az;
    }

    public static int[] legacyPlatform(int ax, int az, int y0, int trunkH, int i) {
        int[] dx = { 30, 0, -30, 0 }, dz = { 0, 30, 0, -30 };
        return new int[] { ax + dx[i], y0 + trunkH - 36 + i * 9, az + dz[i] };
    }

    private static void block(StructureBuilder b, int x, int y, int z, Object o) {
        b.setBlock(x, y, z, o, 0, 2);
    }

    private static void logStep(StructureBuilder b, int x, int y, int z, int facing) {
        if (!inSlice(b, x, z)) return;
        b.setBlock(x, y, z, RoyalArchitecture.get("zenith_log_stairs"), facing, 2);
    }

    private static void deck(StructureBuilder b, int x, int y, int z, int r) {
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            block(b, x + dx, y, z + dz, BlocksGTSR.ruinedCasing);
            for (int h = 1; h <= 14; h++) block(b, x + dx, y + h, z + dz, Blocks.air);
            if ((Math.abs(dx) == r || Math.abs(dz) == r) && Math.abs(dx) + Math.abs(dz) > r + 2)
                block(b, x + dx, y + 1, z + dz, Blocks.iron_bars);
        }
    }

    private static void placeLegacyInto(World w, StructureBuilder b, int ax, int az, int y0, int trunkH) {
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
                if (s > 0 && s % 8 == 0)
                    logStep(b, xx, y, zz, t == 0 ? 3 : t <= 24 ? 0 : t <= 48 ? 2 : t <= 72 ? 1 : 3);
                else block(b, xx, y, zz, BlocksGTSR.prosperityZenithLog);
                for (int h = 1; h <= 3; h++) block(b, xx, y + h, zz, Blocks.air);
            }
        }
        for (int i = 0; i < 4; i++) {
            int[] p = legacyPlatform(ax, az, y0, trunkH, i);
            deck(b, p[0], p[1], p[2], 6);
            int[] offset = { 4, 7, 10, 1 };
            int base = y0 + offset[i] + 12 * Math.floorDiv(p[1] - y0 - offset[i], 12);
            for (int j = 14; j <= 30; j++) for (int width = -1; width <= 1; width++) {
                int x = ax + (i == 0 ? j : i == 2 ? -j : width), z = az + (i == 1 ? j : i == 3 ? -j : width);
                int y = Math.min(p[1], base + j - 14);
                if (j > 14 && y > Math.min(p[1], base + j - 15))
                    logStep(b, x, y, z, i == 0 ? 0 : i == 1 ? 2 : i == 2 ? 1 : 3);
                else block(b, x, y, z, BlocksGTSR.prosperityZenithLog);
                for (int h = 1; h <= 3; h++) block(b, x, y + h, z, Blocks.air);
            }

        }
        deck(b, ax, top, az, 9);
        int topBase = y0 + 10 + 12 * Math.floorDiv(top - y0 - 10, 12);
        for (int j = -14; j <= 0; j++) for (int a = -1; a <= 1; a++) {
            int y = Math.min(top, topBase + j + 14);
            if (j > -14 && y > Math.min(top, topBase + j + 13)) logStep(b, ax + j, y, az + a, 0);
            else block(b, ax + j, y, az + a, BlocksGTSR.prosperityZenithLog);
            for (int h = 1; h <= 3; h++) block(b, ax + j, y + h, az + a, Blocks.air);
        }

        restoreSpiralWalk(b, ax, az, y0, trunkH, 12, 8, 3);
        restoreCourtConnector(b, ax, az, y0, trunkH, 12, 3);
    }

    public static final float KING_YAW = 90F;
    public static final int[] ROOM_GUARD_COUNTS = { 3, 4, 5, 4, 3, 4, 5, 4 };
    public static final int[] ROOM_CHEST_COUNTS = { 1, 2, 2, 1, 1, 2, 2, 1 };

    public static int[] platformV2(int ax, int az, int y0, int trunkH, int i) {
        int[] dx = { 38, 0, -38, 0 }, dz = { 0, 38, 0, -38 };
        return new int[] { ax + dx[i], y0 + trunkH - 42 + i * 9, az + dz[i] };
    }

    public static int[] throne(int ax, int az, int y0, int trunkH) {
        return new int[] { ax, y0 + trunkH + 3, az };
    }

    public static int[] throneEntrance(int ax, int az, int y0, int trunkH) {
        return new int[] { ax - 55, y0 + trunkH + 3, az };
    }

    public static float platformYawV2(int i) {
        return new float[] { 90F, 180F, -90F, 0F }[i];
    }

    public static int[] guardPositionV2(int ax, int az, int y0, int trunkH, int platform, int ordinal) {
        int[] p = platformV2(ax, az, y0, trunkH, platform);
        int[] lateral = { -5, 0, 5, -3, 3 };
        int back = ordinal < 3 ? 4 : 7;
        p[0] += platform == 0 ? back : platform == 2 ? -back : lateral[ordinal];
        p[2] += platform == 1 ? back : platform == 3 ? -back : lateral[ordinal];
        p[1]++;
        return p;
    }

    public static int[] chestPositionV2(int ax, int az, int y0, int trunkH, int platform, int ordinal) {
        int[] p = platformV2(ax, az, y0, trunkH, platform);
        p[0] += platform % 2 == 0 ? 0 : ordinal == 0 ? -7 : 7;
        p[2] += platform % 2 == 1 ? 0 : ordinal == 0 ? -7 : 7;
        p[1]++;
        return p;
    }

    private static void wood(StructureBuilder b, int x, int y, int z, int axis) {
        b.setBlock(x, y, z, BlocksGTSR.prosperityZenithLog, axis, 2);
    }

    private static boolean inSlice(StructureBuilder b, int x, int z) {
        return x >= b.minX() && x <= b.maxX() && z >= b.minZ() && z <= b.maxZ();
    }

    /** A sloping central court and five thick outward roots; leaf beds fill the gaps below. */
    private static boolean courtRoot(double dx, double dz) {
        double localAngle = Math.atan2(dz, dx);
        double core = 31 + 3 * Math.sin(localAngle * 5 + .6) + 2 * Math.sin(localAngle * 9);
        if (dx * dx + dz * dz <= core * core) return true;
        double[] angles = { Math.PI, 0, .9, -.9, Math.PI / 2 };
        for (double angle : angles) {
            double along = dx * Math.cos(angle) + dz * Math.sin(angle);
            double across = -dx * Math.sin(angle) + dz * Math.cos(angle);
            double bend = 3 * Math.sin(along / 17);
            if (along > 25 && along < 65 && Math.abs(across - bend) < 11 - (along - 25) * .12) return true;
        }
        return false;
    }

    public static int courtLift(int dx, int dz) {
        double distance = Math.sqrt(dx * dx + dz * dz);
        double blend = Math.min(1, Math.max(0, (distance - 8) / 12));
        double ridge = .9 + .65 * Math.sin((dx + 2 * dz) / 15D) + .6 * Math.cos((2 * dx - dz) / 19D);
        return Math.min(2, Math.max(0, (int) (ridge * blend)));
    }

    public static int[] throneChestPosition(int ax, int az, int y0, int trunkH, int i) {
        int x = 18 + (i % 3) * 8, z = -7 + (i / 3) * 7;
        // The furthest row rests on the eastern root rather than a detached square platform.
        return new int[] { ax + x, y0 + trunkH + 3 + courtLift(x, z), az + z };
    }

    private static void livingDeck(StructureBuilder b, int x, int y, int z, int radius, boolean throne) {
        livingDeck(b, x, y, z, radius, throne, 1D);
    }

    private static void livingDeck(StructureBuilder b, int x, int y, int z, int radius, boolean throne,
        double courtScale) {
        for (int dx = Math.max(-radius, b.minX() == Integer.MIN_VALUE ? -radius : b.minX() - x); dx
            <= Math.min(radius, b.maxX() == Integer.MAX_VALUE ? radius : b.maxX() - x); dx++)
            for (int dz = Math.max(-radius, b.minZ() == Integer.MIN_VALUE ? -radius : b.minZ() - z); dz
                <= Math.min(radius, b.maxZ() == Integer.MAX_VALUE ? radius : b.maxZ() - z); dz++) {
                    double distance = Math.sqrt(dx * dx + dz * dz), angle = Math.atan2(dz, dx);
                    boolean root = throne ? courtRoot(dx * courtScale, dz * courtScale)
                        : distance <= radius - 1.4 + 1.4 * Math.sin(angle * 9 + .4);
                    if (!root) {
                        if (throne && distance * courtScale < 45 + 4 * Math.sin(angle * 7)) {
                            // Recessed living leaf beds bridge the root gaps, never a level metal disk.
                            int leafY = y - 2 + (Math.abs(dx * 7 + dz * 11) % 3 == 0 ? 1 : 0);
                            block(b, x + dx, leafY, z + dz, BlocksGTSR.prosperityJadeLeaves);
                            for (int h = 1; h <= 3; h++)
                                block(b, x + dx, leafY - h, z + dz, BlocksGTSR.prosperityJadeLeaves);
                        }
                        continue;
                    }
                    int surface = y + (throne ? courtLift(dx, dz) : 0);
                    int axis = Math.abs(dx) >= Math.abs(dz) ? 4 : 8;
                    wood(b, x + dx, surface, z + dz, axis);
                    if (throne && RoyalArchitecture.get("heartwood") != null) {
                        double band = Math.sin(angle * 9 + distance * .035);
                        if (Math.abs(band) < .10) block(b, x + dx, surface, z + dz, RoyalArchitecture.get("crownwood"));
                        else if (Math.abs(band) < .27)
                            block(b, x + dx, surface, z + dz, RoyalArchitecture.get("heartwood"));
                    }
                    int depth = throne ? 3 + (int) (3 * Math.max(0, 1 - distance / 70))
                        : Math.max(1, (int) ((radius - distance) / 3) + 1);
                    for (int h = 1; h <= depth; h++) wood(b, x + dx, surface - h, z + dz, axis);
                    for (int h = 1; h <= (throne ? 14 : 6); h++) block(b, x + dx, surface + h, z + dz, Blocks.air);
                    if (distance > (throne ? 30 : radius - 4)) for (int h = depth + 1; h <= depth + 5; h++)
                        block(b, x + dx, surface - h, z + dz, BlocksGTSR.prosperityJadeLeaves);
                }
    }

    private static int[] roomCell(int[] p, int room, int u, int v) {
        return new int[] { p[0] + (room == 0 ? u : room == 2 ? -u : v), p[2] + (room == 1 ? u : room == 3 ? -u : v) };
    }

    /** A half-enclosed living room with a side doorway and hanging leaf baffle. */
    private static void treeRoom(StructureBuilder b, int[] p, int room) {
        for (int u = -10; u <= 10; u++) for (int v = -10; v <= 10; v++) {
            double r = Math.sqrt(u * u + v * v);
            int[] cell = roomCell(p, room, u, v);
            if (!inSlice(b, cell[0], cell[1])) continue;
            if (r >= 9 && r <= 10.6 && u >= -5)
                for (int h = 1; h <= 6; h++) block(b, cell[0], p[1] + h, cell[1], BlocksGTSR.prosperityJadeLeaves);
            // The front baffle prevents the bridge from displaying every guard at once.
            if (u == -6 && Math.abs(v) <= 9 && !(v >= -5 && v <= -2))
                for (int h = 1; h <= 6; h++) block(b, cell[0], p[1] + h, cell[1], BlocksGTSR.prosperityJadeLeaves);
            if (r < 10.5 && u >= -6) {
                int roof = p[1] + 8 + (int) Math.max(0, 2 - r / 5);
                block(b, cell[0], roof, cell[1], BlocksGTSR.prosperityJadeLeaves);
                block(b, cell[0], roof + 1, cell[1], BlocksGTSR.prosperityJadeLeaves);
            }
        }
        // Thick curved roots bind the leafy dome, with an opening around the side doorway.
        for (int k = 0; k < 7; k++) for (int h = 0; h <= 10; h++) {
            double angle = -Math.PI / 2 + k * Math.PI / 6;
            int u = (int) Math.round(Math.cos(angle) * (10 - h * .28)),
                v = (int) Math.round(Math.sin(angle) * (10 - h * .28));
            int[] cell = roomCell(p, room, u, v);
            if (inSlice(b, cell[0], cell[1])) wood(b, cell[0], p[1] + h, cell[1], room % 2 == 0 ? 4 : 8);
        }
        for (int u = -10; u <= -3; u++) for (int v = -5; v <= -2; v++) {
            int[] cell = roomCell(p, room, u, v);
            if (!inSlice(b, cell[0], cell[1])) continue;
            wood(b, cell[0], p[1], cell[1], room % 2 == 0 ? 4 : 8);
            for (int h = 1; h <= 4; h++) block(b, cell[0], p[1] + h, cell[1], Blocks.air);
        }
        // A short clear lateral approach joins the bridge to the offset doorway.
        for (int u = -10; u <= -8; u++) for (int v = -5; v <= 2; v++) {
            int[] cell = roomCell(p, room, u, v);
            if (!inSlice(b, cell[0], cell[1])) continue;
            wood(b, cell[0], p[1], cell[1], room % 2 == 0 ? 4 : 8);
            for (int h = 1; h <= 4; h++) block(b, cell[0], p[1] + h, cell[1], Blocks.air);
        }
    }

    private static void rootTube(StructureBuilder b, int ax, int az, int top, double x, double y, double z, int radius,
        int axis) {
        int cx = ax + (int) Math.round(x), cy = top + (int) Math.round(y), cz = az + (int) Math.round(z);
        for (int ox = -radius; ox <= radius; ox++)
            for (int oy = -radius; oy <= radius; oy++) for (int oz = -radius; oz <= radius; oz++) {
                if (ox * ox + oy * oy + oz * oz > radius * radius + radius) continue;
                if (inSlice(b, cx + ox, cz + oz)) wood(b, cx + ox, cy + oy, cz + oz, axis);
            }
    }

    private static void royalRootBackdrop(StructureBuilder b, int ax, int az, int top) {
        double peak = Math.min(27, Math.max(20, 255 - top - 7));
        // Two great root arches rise behind the court and grow into leafy branch crowns.
        for (int side : new int[] { -1, 1 }) for (int t = 0; t <= 64; t++) {
            double u = t / 64D;
            double x = 14 + 42 * u, z = side * (5 + 32 * Math.sin(u * Math.PI / 2)),
                y = 4 + peak * Math.sin(u * Math.PI);
            rootTube(b, ax, az, top, x, y, z, t < 40 ? 3 : 2, 4);
            if (t > 24 && t % 8 == 0) {
                for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) for (int dy = -2; dy <= 3; dy++) {
                    if (dx * dx + dz * dz + dy * dy * 2 > 40) continue;
                    int xx = ax + (int) Math.round(x) + dx, zz = az + (int) Math.round(z) + dz;
                    if (inSlice(b, xx, zz))
                        block(b, xx, top + (int) Math.round(y) + dy, zz, BlocksGTSR.prosperityJadeLeaves);
                }
            }
        }
        // Five interlaced crown branches behind the king, with thick bases and tapered antler forks.
        for (int k = -2; k <= 2; k++) for (int t = 0; t <= 30; t++) {
            double u = t / 30D, x = 12 + 16 * u, z = k * (3 + 5 * u), y = 2 + peak * u;
            rootTube(b, ax, az, top, x, y, z, t < 12 ? 3 : 2, 0);
            if (t > 17) for (int side : new int[] { -1, 1 })
                rootTube(b, ax, az, top, x + u * 4, y - 2, z + side * (t - 17) * .6, 1, 8);
        }
        // Broad living armrests leave the 6x12 king silhouette clear in the western entrance view.
        for (int x = -3; x <= 14; x++) for (int z : new int[] { -7, -6, 6, 7 }) {
            int h = 2 + (x + 3) / 6;
            for (int y = 1; y <= h; y++) if (inSlice(b, ax + x, az + z)) wood(b, ax + x, top + y, az + z, 4);
        }
        // Massive root back, beyond the king's collision box, carries an inset crown motif.
        for (int x = 8; x <= 12; x++) for (int z = -5; z <= 5; z++) for (int y = 1; y <= 15 - Math.abs(z); y++) {
            if (!inSlice(b, ax + x, az + z)) continue;
            wood(b, ax + x, top + y, az + z, 0);
            if (x == 8 && y > 5 && RoyalArchitecture.get("crownwood") != null)
                block(b, ax + x, top + y, az + z, RoyalArchitecture.get("crownwood"));
        }
    }

    private static void placeV2Into(World w, StructureBuilder b, int ax, int az, int y0, int trunkH) {
        ForgottenLakeEncounterData data = w == null ? null : ForgottenLakeEncounterData.get(w);
        String id = w == null ? "" : encounterId(w, ax, az);
        if (data != null) {
            data.registerLayout(id, 2);
            if (data.layoutVersion(id) == 1) {
                placeLegacyInto(w, b, ax, az, y0, trunkH);
                return;
            }
        }
        int top = y0 + trunkH + 2;
        livingDeck(b, ax, top, az, 69, true);
        // An open spiral wrapped around the wider living trunk; the final horizontal run faces west.
        int steps = (top - y0) * 8 + 7;
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
            for (int a = 0; a < 4; a++) {
                int xx = ax + x + (Math.abs(z) == 12 ? 0 : (x > 0 ? a : -a));
                int zz = az + z + (Math.abs(z) == 12 ? (z > 0 ? a : -a) : 0);
                if (!inSlice(b, xx, zz)) continue;
                if (s > 0 && s % 8 == 0)
                    logStep(b, xx, y, zz, t == 0 ? 3 : t <= 24 ? 0 : t <= 48 ? 2 : t <= 72 ? 1 : 3);
                else wood(b, xx, y, zz, Math.abs(z) == 12 ? 4 : 8);
                for (int h = 1; h <= 4; h++) block(b, xx, y + h, zz, Blocks.air);
            }
        }
        int[] offsets = { 4, 7, 10, 1 };
        for (int i = 0; i < 4; i++) {
            int[] p = platformV2(ax, az, y0, trunkH, i);
            livingDeck(b, p[0], p[1], p[2], 11, false);
            int base = y0 + offsets[i] + 12 * Math.floorDiv(p[1] - y0 - offsets[i], 12);
            for (int j = 14; j <= 38; j++) for (int width = -2; width <= 2; width++) {
                int x = ax + (i == 0 ? j : i == 2 ? -j : width), z = az + (i == 1 ? j : i == 3 ? -j : width),
                    y = Math.min(p[1], base + Math.max(0, j - 15));
                if (!inSlice(b, x, z)) continue;
                if (j > 14 && y > Math.min(p[1], base + Math.max(0, j - 16)))
                    logStep(b, x, y, z, i == 0 ? 0 : i == 1 ? 2 : i == 2 ? 1 : 3);
                else wood(b, x, y, z, i % 2 == 0 ? 4 : 8);
                for (int h = 1; h <= 4; h++) block(b, x, y + h, z, Blocks.air);
            }
            treeRoom(b, p, i);
            if (RoyalArchitecture.get("crown_fern") != null) for (int side : new int[] { -1, 1 }) {
                block(b, p[0] + side * 8, p[1] + 1, p[2] + side * 5, RoyalArchitecture.get("crown_fern"));
                block(b, p[0] + side * 6, p[1] + 1, p[2] - side * 8, RoyalArchitecture.get("oath_flower"));
            }
        }
        // The final westward riser joins the old spiral to the royal entrance and broad arena.
        int topBase = y0 + 10 + 12 * Math.floorDiv(top - y0 - 10, 12);
        for (int j = -55; j <= 0; j++) for (int a = -3; a <= 3; a++) {
            int y = j < -14 ? top - (int) Math.round((top - topBase) * (j + 55) / 41D)
                : Math.min(top, topBase + j + 14);
            if (!inSlice(b, ax + j, az + a)) continue;
            int previousY = j <= -14 ? top - (int) Math.round((top - topBase) * (j + 54) / 41D)
                : Math.min(top, topBase + j + 13);
            if (y > previousY) logStep(b, ax + j, y, az + a, 0);
            else wood(b, ax + j, y, az + a, 4);
            if (j > -55 && y < previousY) logStep(b, ax + j - 1, previousY, az + a, 1);
            for (int h = 1; h <= 4; h++) block(b, ax + j, y + h, az + a, Blocks.air);
        }
        royalRootBackdrop(b, ax, az, top);
        // Keep the entry sightline and king's full 6x12 collision column unobstructed.
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) if (inSlice(b, ax + x, az + z)) {
            wood(b, ax + x, top, az + z, 4);
            for (int h = 1; h <= 14; h++) block(b, ax + x, top + h, az + z, Blocks.air);
        }
        restoreSpiralWalk(b, ax, az, y0, trunkH, 12, 8, 4);
        restoreCourtConnector(b, ax, az, y0, trunkH, 12, 4);
    }

    private static void initializeV2Chunk(World w, int ax, int az, int y0, int trunkH, int cx, int cz) {
        if (w.isRemote) return;
        ForgottenLakeEncounterData d = ForgottenLakeEncounterData.get(w);
        String id = encounterId(w, ax, az);
        d.registerLayout(id, 2);
        if (d.layoutVersion(id) == 1) {
            initializeLegacyChunk(w, ax, az, y0, trunkH, cx, cz);
            return;
        }
        for (int i = 0; i < 4; i++) {
            for (int n = 0; n < ROOM_CHEST_COUNTS[i]; n++) {
                int[] c = chestPositionV2(ax, az, y0, trunkH, i, n);
                chest(w, d, id, "platformChest" + i + (n == 0 ? "" : "_" + n), c[0], c[1], c[2], 2, i, cx, cz);
            }
            for (int n = 0; n < d.guardCount(id, i); n++) {
                int[] p = guardPositionV2(ax, az, y0, trunkH, i, n);
                String node = "guard" + d.guardIndex(id, i, n);
                if (owner(p[0], p[2], cx, cz) && !d.created(id, node)
                    && !d.guardDead(id, i, n)
                    && !d.platformCleared(id, i)) {
                    EntityResidualOathguard g = new EntityResidualOathguard(w);
                    g.initialize(id, i, p[0] + .5, p[1], p[2] + .5);
                    g.setOrdinal(n);
                    g.setHomeYaw(platformYawV2(i));
                    if (w.spawnEntityInWorld(g)) d.createdNode(id, node);
                }
            }
        }
        int[] t = throne(ax, az, y0, trunkH);
        if (owner(ax, az, cx, cz) && !d.created(id, "king") && !d.kingDead(id)) {
            EntitySilentKing king = new EntitySilentKing(w);
            king.initialize(id, -1, ax + .5, t[1], az + .5);
            king.setHomeYaw(KING_YAW);
            if (w.spawnEntityInWorld(king)) d.createdNode(id, "king");
        }
        for (int i = 0; i < 9; i++) {
            int[] c = throneChestPosition(ax, az, y0, trunkH, i);
            chest(w, d, id, "throneChest" + i, c[0], c[1], c[2], i < 3 ? 3 : i < 6 ? 4 : 5, -1, cx, cz);
        }
    }

    private static void placeCourtV3(StructureBuilder b, int ax, int az, int y0, int trunkH) {
        int top = y0 + trunkH + 2;
        livingDeck(b, ax, top, az, 84, true, .8D);
        // An open spiral wrapped around the wider living trunk; the final horizontal run faces west.
        int steps = (top - y0) * 16 + 15;
        for (int s = 0; s <= steps; s++) {
            int t = s % 192, x, z;
            if (t < 48) {
                x = -24 + t;
                z = -24;
            } else if (t < 96) {
                x = 24;
                z = -24 + t - 48;
            } else if (t < 144) {
                x = 24 - (t - 96);
                z = 24;
            } else {
                x = -24;
                z = 24 - (t - 144);
            }
            int y = y0 + s / 16;
            for (int a = 0; a < 4; a++) {
                int xx = ax + x + (Math.abs(z) == 24 ? 0 : (x > 0 ? a : -a));
                int zz = az + z + (Math.abs(z) == 24 ? (z > 0 ? a : -a) : 0);
                if (!inSlice(b, xx, zz)) continue;
                if (s > 0 && s % 16 == 0)
                    logStep(b, xx, y, zz, t == 0 ? 3 : t <= 48 ? 0 : t <= 96 ? 2 : t <= 144 ? 1 : 3);
                else wood(b, xx, y, zz, Math.abs(z) == 24 ? 4 : 8);
                for (int h = 1; h <= 4; h++) block(b, xx, y + h, zz, Blocks.air);
            }
        }
        // The final westward riser joins the old spiral to the royal entrance and broad arena.
        int topBase = y0 + 10 + 12 * Math.floorDiv(top - y0 - 10, 12);
        for (int j = -55; j <= 0; j++) for (int a = -3; a <= 3; a++) {
            int y = j < -28 ? top - (int) Math.round((top - topBase) * (j + 55) / 27D)
                : Math.min(top, topBase + (j + 28) / 2);
            if (!inSlice(b, ax + j, az + a)) continue;
            int previousY = j <= -28 ? top - (int) Math.round((top - topBase) * (j + 54) / 27D)
                : Math.min(top, topBase + (j + 27) / 2);
            if (y > previousY) logStep(b, ax + j, y, az + a, 0);
            else wood(b, ax + j, y, az + a, 4);
            if (j > -55 && y < previousY) logStep(b, ax + j - 1, previousY, az + a, 1);
            for (int h = 1; h <= 4; h++) block(b, ax + j, y + h, az + a, Blocks.air);
        }
        royalRootBackdrop(b, ax, az, top);
        // Keep the entry sightline and king's full 6x12 collision column unobstructed.
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) if (inSlice(b, ax + x, az + z)) {
            wood(b, ax + x, top, az + z, 4);
            for (int h = 1; h <= 14; h++) block(b, ax + x, top + h, az + z, Blocks.air);
        }
    }

    public static final int ROOM_RADIUS = 19;

    public static int[] platform(int ax, int az, int y0, int trunkH, int i) {
        double angle = i * Math.PI / 4;
        return new int[] { ax + (int) Math.round(112 * Math.cos(angle)), y0 + trunkH - 66 + i * 9,
            az + (int) Math.round(112 * Math.sin(angle)) };
    }

    public static float platformYaw(int room) {
        float yaw = 90 + room * 45;
        return yaw >= 180 ? yaw - 360 : yaw;
    }

    private static int[] roomCellV3(int[] p, int room, int u, int v) {
        double a = room * Math.PI / 4;
        return new int[] { p[0] + (int) Math.round(u * Math.cos(a) - v * Math.sin(a)),
            p[2] + (int) Math.round(u * Math.sin(a) + v * Math.cos(a)) };
    }

    public static int[] guardPosition(int ax, int az, int y0, int trunkH, int room, int ordinal) {
        int[] p = platform(ax, az, y0, trunkH, room);
        int[] u = { 3, 6, 3, 10, 10 }, v = { -8, 0, 8, -5, 5 };
        int[] c = roomCellV3(p, room, u[ordinal], v[ordinal]);
        return new int[] { c[0], p[1] + 1, c[1] };
    }

    public static int[] platformEntrance(int ax, int az, int y0, int trunkH, int room) {
        int[] p = platform(ax, az, y0, trunkH, room), c = roomCellV3(p, room, -10, -7);
        return new int[] { c[0], p[1] + 1, c[1] };
    }

    public static float guardYaw(int room, int ordinal) {
        int[] guard = guardPosition(0, 0, 72, 140, room, ordinal), door = platformEntrance(0, 0, 72, 140, room);
        return (float) Math.toDegrees(Math.atan2(guard[0] - door[0], door[2] - guard[2]));
    }

    public static int[] chestPosition(int ax, int az, int y0, int trunkH, int room, int ordinal) {
        int[] p = platform(ax, az, y0, trunkH, room), c = roomCellV3(p, room, 11, ordinal == 0 ? -7 : 7);
        return new int[] { c[0], p[1] + 1, c[1] };
    }

    private static void roomBlock(StructureBuilder b, int[] p, int room, int u, int h, int v, String block) {
        int[] c = roomCellV3(p, room, u, v);
        if (inSlice(b, c[0], c[1])) block(b, c[0], p[1] + h, c[1], RoyalArchitecture.get(block));
    }

    private static void royalRoomV3(StructureBuilder b, int[] p, int room) {
        livingDeck(b, p[0], p[1], p[2], ROOM_RADIUS, false);
        for (int u = -19; u <= 19; u++) for (int v = -19; v <= 19; v++) {
            double r = Math.sqrt(u * u + v * v);
            int[] c = roomCellV3(p, room, u, v);
            if (!inSlice(b, c[0], c[1])) continue;
            if (r >= 17 && r < 19.6 && u >= -9)
                for (int h = 1; h <= 9; h++) block(b, c[0], p[1] + h, c[1], BlocksGTSR.prosperityJadeLeaves);
            if (u == -10 && Math.abs(v) < 17 && !(v >= -9 && v <= -5))
                for (int h = 1; h <= 8; h++) block(b, c[0], p[1] + h, c[1], BlocksGTSR.prosperityJadeLeaves);
            if (r < 19 && u >= -10) {
                int roof = p[1] + 10 + (int) Math.max(0, 4 - r / 5);
                block(b, c[0], roof, c[1], BlocksGTSR.prosperityJadeLeaves);
                block(b, c[0], roof + 1, c[1], BlocksGTSR.prosperityJadeLeaves);
            }
        }
        for (int k = 0; k < 9; k++) for (int h = 0; h <= 13; h++) {
            double a = -Math.PI / 2 + k * Math.PI / 8;
            int[] c = roomCellV3(
                p,
                room,
                (int) Math.round(Math.cos(a) * (18 - h * .3)),
                (int) Math.round(Math.sin(a) * (18 - h * .3)));
            if (inSlice(b, c[0], c[1])) wood(b, c[0], p[1] + h, c[1], 0);
        }
        // An offset entrance turns through the leaf screen before revealing the barracks.
        for (int u = -18; u <= -6; u++) for (int v = -9; v <= -5; v++) {
            int[] c = roomCellV3(p, room, u, v);
            if (!inSlice(b, c[0], c[1])) continue;
            wood(b, c[0], p[1], c[1], room % 4 < 2 ? 4 : 8);
            for (int h = 1; h <= 5; h++) block(b, c[0], p[1] + h, c[1], Blocks.air);
        }
        for (int u = -18; u <= -15; u++) for (int v = -9; v <= 3; v++) {
            int[] c = roomCellV3(p, room, u, v);
            if (!inSlice(b, c[0], c[1])) continue;
            wood(b, c[0], p[1], c[1], room % 4 < 2 ? 4 : 8);
            for (int h = 1; h <= 5; h++) block(b, c[0], p[1] + h, c[1], Blocks.air);
        }
        if (RoyalArchitecture.get("heartwood") == null) return;
        // Two timber oath beds, carved headboards and a rack of upright oath weapons.
        for (int v : new int[] { -13, 12 }) for (int u = -5; u <= -1; u++) for (int width = 0; width < 2; width++) {
            roomBlock(b, p, room, u, 1, v + width, "heartwood_slab");
            if (u == -1) roomBlock(b, p, room, u, 2, v + width, "oathwood");
        }
        for (int v = -4; v <= 6; v++) {
            roomBlock(b, p, room, 15, 1, v, "root_fence");
            roomBlock(b, p, room, 15, 2, v, "oath_fence");
            if ((v & 1) == 0) roomBlock(b, p, room, 15, 3, v, "crownwood");
        }
        // Root-seat dining table with four small seats, and a resin lamp recessed in each rear corner.
        for (int u = -6; u <= -3; u++) for (int v = 6; v <= 9; v++) roomBlock(b, p, room, u, 2, v, "heartwood_slab");
        for (int u : new int[] { -6, -3 })
            for (int v : new int[] { 6, 9 }) roomBlock(b, p, room, u, 1, v, "root_fence");
        for (int u : new int[] { -8, -1 }) roomBlock(b, p, room, u, 1, 7, "heartwood_stairs");
        for (int v : new int[] { -11, 11 }) {
            roomBlock(b, p, room, 11, 1, v, "rootstone");
            roomBlock(b, p, room, 11, 2, v, "amber_lattice");
            roomBlock(b, p, room, 11, 3, v, "amber_pane");
        }
        // Four distinct interior rhythms: shrine, arms store, oath archive and resting lodge.
        String altar = room % 4 == 0 ? "crownwood"
            : room % 4 == 1 ? "patina_mosaic" : room % 4 == 2 ? "oathwood" : "heartwood";
        for (int u = -3; u <= 1; u++) for (int v = -13; v <= -10; v++) roomBlock(b, p, room, u, 1, v, altar);
        roomBlock(b, p, room, -1, 2, -12, "amber_lattice");
        if (room % 4 == 2)
            for (int h = 2; h <= 5; h++) for (int v = -12; v <= -10; v++) roomBlock(b, p, room, 1, h, v, "oathwood");
        if (room % 4 == 1) for (int u = 7; u <= 10; u++) roomBlock(b, p, room, u, 1, -12, "patina_mosaic");
        if (room % 4 == 3) for (int u = 5; u <= 8; u++) roomBlock(b, p, room, u, 1, 12, "heartwood_slab");
    }

    private static void roomBranchV3(StructureBuilder b, int ax, int az, int y0, int trunkH, int room) {
        int[] p = platform(ax, az, y0, trunkH, room);
        int[] phase = { 4, 6, 7, 9, 10, 0, 1, 3 };
        int base = y0 + phase[room] + 12 * Math.floorDiv(p[1] - y0 - phase[room], 12);
        double angle = room * Math.PI / 4, cos = Math.cos(angle), sin = Math.sin(angle);
        double start = room % 2 == 0 ? 24 : Math.sqrt(1152);
        // Reduce oversampled curve positions to one authored walking height per column.
        java.util.List<int[]> route = new java.util.ArrayList<>();
        for (int t = 0; t <= 200; t++) {
            double u = t / 200D, r = start + (112 - start) * u, bend = 8 * Math.sin(Math.PI * u);
            int x = ax + (int) Math.round(cos * r - sin * bend), z = az + (int) Math.round(sin * r + cos * bend);
            int y = base + (int) Math.round((p[1] - base) * u + 2 * Math.sin(Math.PI * u));
            // Preserve the original leaf fringe exactly; only walking timber is reshaped.
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
                if (dx * dx + dz * dz > 8 && dx * dx + dz * dz <= 12 && inSlice(b, x + dx, z + dz))
                    block(b, x + dx, y - 4, z + dz, BlocksGTSR.prosperityJadeLeaves);
            }
            if (!route.isEmpty()) {
                int[] previous = route.get(route.size() - 1);
                if (previous[0] == x && previous[2] == z) {
                    previous[1] = y;
                    continue;
                }
                if (previous[0] != x && previous[2] != z) route.add(new int[] { x, previous[1], previous[2] });
            }
            route.add(new int[] { x, y, z });
        }
        java.util.Map<String, int[]> floor = new java.util.LinkedHashMap<>();
        for (int i = 0; i < route.size(); i++) {
            int[] q = route.get(i), previous = route.get(i == 0 ? 0 : i - 1);
            boolean alongX = q[0] != previous[0] || i == 0 && Math.abs(cos) >= Math.abs(sin);
            for (int side = -3; side <= 3; side++) {
                int x = q[0] + (alongX ? 0 : side), z = q[2] + (alongX ? side : 0);
                String key = x + ":" + z;
                int[] old = floor.get(key);
                if (old == null || q[1] < old[1]) floor.put(key, new int[] { x, q[1], z });
            }
        }
        for (int[] q : route) floor.put(q[0] + ":" + q[2], q);
        for (int[] q : floor.values()) {
            if (!inSlice(b, q[0], q[2])) continue;
            for (int h = 0; h <= 3; h++) wood(b, q[0], q[1] - h, q[2], Math.abs(cos) >= Math.abs(sin) ? 4 : 8);
            for (int h = 1; h <= 5; h++) block(b, q[0], q[1] + h, q[2], Blocks.air);
        }
        // Install rises after their support timber, so subsequent sections cannot bury half steps.
        for (int i = 1; i < route.size(); i++) {
            int[] a = route.get(i - 1), c = route.get(i);
            if (a[1] == c[1]) continue;
            int[] high = a[1] > c[1] ? a : c, low = a[1] > c[1] ? c : a;
            int facing = high[0] != low[0] ? (high[0] > low[0] ? 0 : 1) : (high[2] > low[2] ? 2 : 3);
            boolean alongX = high[0] != low[0];
            for (int side = -3; side <= 3; side++) {
                int x = high[0] + (alongX ? 0 : side), z = high[2] + (alongX ? side : 0);
                int[] f = floor.get(x + ":" + z);
                if (f == null || f[1] != high[1]) continue;
                logStep(b, x, high[1], z, facing);
            }
        }
    }

    private static void clearNodeV3(StructureBuilder b, int[] p, int width, int height) {
        for (int dx = -width; dx <= width; dx++) for (int dz = -width; dz <= width; dz++) {
            if (!inSlice(b, p[0] + dx, p[2] + dz)) continue;
            for (int h = 0; h < height; h++) block(b, p[0] + dx, p[1] + h, p[2] + dz, Blocks.air);
        }
    }

    public static void placeInto(World w, StructureBuilder b, int ax, int az, int y0, int trunkH) {
        if (w != null) {
            ForgottenLakeEncounterData data = ForgottenLakeEncounterData.get(w);
            String id = encounterId(w, ax, az);
            data.registerLayout(id, 3);
            if (data.layoutVersion(id) == 1) {
                placeLegacyInto(w, b, ax, az, y0, trunkH);
                return;
            }
            if (data.layoutVersion(id) == 2) {
                placeV2Into(w, b, ax, az, y0, trunkH);
                return;
            }
        }
        // V2 court is replayed as a shared core, while its lower rooms are suppressed below.
        placeCourtV3(b, ax, az, y0, trunkH);
        for (int room = 0; room < 8; room++) {
            int[] p = platform(ax, az, y0, trunkH, room);
            royalRoomV3(b, p, room);
            roomBranchV3(b, ax, az, y0, trunkH, room);
            // Branch entry crosses the front screen, so restore its sideways turn last.
            for (int u = -10; u <= -10; u++) for (int v = -4; v <= 6; v++) {
                int[] c = roomCellV3(p, room, u, v);
                if (!inSlice(b, c[0], c[1])) continue;
                for (int h = 1; h <= 7; h++) block(b, c[0], p[1] + h, c[1], BlocksGTSR.prosperityJadeLeaves);
            }
            for (int n = 0; n < ROOM_GUARD_COUNTS[room]; n++)
                clearNodeV3(b, guardPosition(ax, az, y0, trunkH, room, n), 1, 3);
            for (int n = 0; n < ROOM_CHEST_COUNTS[room]; n++)
                clearNodeV3(b, chestPosition(ax, az, y0, trunkH, room, n), 0, 2);
        }
        // High room branches can cut beneath the court boxes; restore their footing and headroom.
        for (int i = 0; i < 9; i++) {
            int[] c = throneChestPosition(ax, az, y0, trunkH, i);
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                if (!inSlice(b, c[0] + dx, c[2] + dz)) continue;
                int floor = c[1] - (Math.max(Math.abs(dx), Math.abs(dz)) == 2 ? 2 : 1);
                wood(b, c[0] + dx, floor, c[2] + dz, 0);
                for (int h = 1; h <= 4; h++) block(b, c[0] + dx, floor + h, c[2] + dz, Blocks.air);
            }
        }
        java.util.List<int[]> tips = com.miaokatze.gtsr.common.dimension.prosperity.ruins.IslandMegaTree
            .branchChestPositions(w == null ? 0L : w.getSeed(), ax, az, y0, trunkH);
        for (int[] c : tips) for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            if (!inSlice(b, c[0] + dx, c[2] + dz)) continue;
            wood(b, c[0] + dx, c[1] - 1, c[2] + dz, 0);
            for (int h = 0; h < 4; h++) block(b, c[0] + dx, c[1] + h, c[2] + dz, Blocks.air);
        }
        restoreSpiralWalk(b, ax, az, y0, trunkH, 24, 16, 4);
        restoreBranchSpiralJoins(b, ax, az, y0, trunkH, tips);
        restoreCourtConnector(b, ax, az, y0, trunkH, 24, 4);
    }

    /** Match the outer timber landing to the lower room deck before the sideways door turn. */
    private static void restoreBranchRoomSteps(StructureBuilder b, int ax, int az, int y0, int trunkH) {
        for (int room = 0; room < 8; room++) {
            int[] p = platform(ax, az, y0, trunkH, room), phase = { 4, 6, 7, 9, 10, 0, 1, 3 };
            int base = y0 + phase[room] + 12 * Math.floorDiv(p[1] - y0 - phase[room], 12);
            double a = room * Math.PI / 4, cos = Math.cos(a), sin = Math.sin(a),
                radius = room % 2 == 0 ? 24 : Math.sqrt(1152);
            int[] start = null;
            for (int t = 0; t <= 200; t++) {
                double u = t / 200D, r = radius + (112 - radius) * u, bend = 8 * Math.sin(Math.PI * u);
                int x = ax + (int) Math.round(cos * r - sin * bend), z = az + (int) Math.round(sin * r + cos * bend);
                if (Math.hypot(x - p[0], z - p[2]) <= 14) break;
                start = new int[] { x, base + (int) Math.round((p[1] - base) * u + 2 * Math.sin(Math.PI * u)), z };
            }
            int[] dest = roomCellV3(p, room, -17, 0);
            int count = Math.max(Math.abs(dest[0] - start[0]), Math.abs(dest[1] - start[2]));
            int[] previous = start;
            java.util.List<int[]> steps = new java.util.ArrayList<>();
            for (int i = 1; i <= count; i++) {
                int[] q = { start[0] + (int) Math.round((dest[0] - start[0]) * i / (double) count),
                    start[1] + (int) Math.round((p[1] - start[1]) * i / (double) count),
                    start[2] + (int) Math.round((dest[1] - start[2]) * i / (double) count) };
                // This is the existing outer walking timber, before the leaf screen.
                // Its transverse support could otherwise leave a full block above
                // the lower landing and bury the half step on the return path.
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                    if (!inSlice(b, q[0] + dx, q[2] + dz)) continue;
                    wood(b, q[0] + dx, q[1], q[2] + dz, Math.abs(cos) >= Math.abs(sin) ? 4 : 8);
                    for (int h = 1; h <= 2; h++) block(b, q[0] + dx, q[1] + h, q[2] + dz, Blocks.air);
                }
                if (q[1] != previous[1]) {
                    int[] high = q[1] > previous[1] ? q : previous, low = q[1] > previous[1] ? previous : q;
                    int facing = high[0] != low[0] ? (high[0] > low[0] ? 0 : 1) : (high[2] > low[2] ? 2 : 3);
                    steps.add(new int[] { high[0], high[1], high[2], facing });
                }
                previous = q;
            }
            for (int[] step : steps) if (inSlice(b, step[0], step[2])) logStep(b, step[0], step[1], step[2], step[3]);
        }
    }

    /** The final spiral replay can lower an early branch rise inside its walking band. */
    private static int branchJoinFloor(int x, int z, int y0, int expected) {
        int t;
        if (z >= -27 && z <= -24 && x >= -24 && x <= 24) t = x + 24;
        else if (x >= 24 && x <= 27 && z > -24 && z < 24) t = 72 + z;
        else if (z >= 24 && z <= 27 && x >= -24 && x <= 24) t = 120 - x;
        else if (x >= -27 && x <= -24 && z > -24 && z < 24) t = 168 - z;
        else return expected;
        int floor = y0 + t / 16;
        floor += 12 * Math.round((expected - floor) / 12F);
        return Math.abs(floor - expected) <= 3 ? floor : expected;
    }

    private static int actualBranchFloor(int x, int z, int y0, int expected, int ax, int az,
        java.util.List<int[]> tips) {
        int floor = branchJoinFloor(x, z, y0, expected);
        for (int[] tip : tips)
            if (Math.abs(ax + x - tip[0]) <= 2 && Math.abs(az + z - tip[2]) <= 2 && Math.abs(tip[1] - 1 - floor) <= 3)
                floor = tip[1] - 1;
        return floor;
    }

    private static void restoreBranchSpiralJoins(StructureBuilder b, int ax, int az, int y0, int trunkH,
        java.util.List<int[]> tips) {
        for (int room = 0; room < 8; room++) {
            int[] p = platform(ax, az, y0, trunkH, room), phase = { 4, 6, 7, 9, 10, 0, 1, 3 };
            int base = y0 + phase[room] + 12 * Math.floorDiv(p[1] - y0 - phase[room], 12);
            double a = room * Math.PI / 4, cos = Math.cos(a), sin = Math.sin(a),
                start = room % 2 == 0 ? 24 : Math.sqrt(1152);
            java.util.List<int[]> route = new java.util.ArrayList<>();
            for (int t = 0; t <= 200; t++) {
                double u = t / 200D, r = start + (112 - start) * u, bend = 8 * Math.sin(Math.PI * u);
                int x = (int) Math.round(cos * r - sin * bend), z = (int) Math.round(sin * r + cos * bend);
                int y = base + (int) Math.round((p[1] - base) * u + 2 * Math.sin(Math.PI * u));
                if (!route.isEmpty()) {
                    int[] previous = route.get(route.size() - 1);
                    if (previous[0] == x && previous[2] == z) {
                        previous[1] = y;
                        continue;
                    }
                    if (previous[0] != x && previous[2] != z) route.add(new int[] { x, previous[1], previous[2] });
                }
                route.add(new int[] { x, y, z });
            }
            java.util.Map<String, Integer> floors = new java.util.HashMap<>();
            for (int i = 0; i < route.size(); i++) {
                int[] q = route.get(i), previous = route.get(i == 0 ? 0 : i - 1);
                boolean alongX = q[0] != previous[0] || i == 0 && Math.abs(cos) >= Math.abs(sin);
                for (int side = -3; side <= 3; side++) {
                    int x = q[0] + (alongX ? 0 : side), z = q[2] + (alongX ? side : 0);
                    String key = x + ":" + z;
                    Integer old = floors.get(key);
                    if (old == null || q[1] < old) floors.put(key, q[1]);
                }
            }
            for (int[] q : route) floors.put(q[0] + ":" + q[2], q[1]);
            int[] entry = route.get(0);
            for (int[] q : route) {
                if (Math.hypot(ax + q[0] - p[0], az + q[2] - p[2]) <= 19) break;
                entry = q;
            }
            int accessStart = route.size();
            route.add(entry.clone());
            for (int[] uv : new int[][] { { -17, 0 }, { -17, -7 }, { -8, -7 } }) {
                int[] c = roomCellV3(p, room, uv[0], uv[1]);
                int x = c[0] - ax, z = c[1] - az, previous[] = route.get(route.size() - 1);
                int steps = Math.max(Math.abs(x - previous[0]), Math.abs(z - previous[2]));
                for (int j = 1; j <= steps; j++) {
                    int xx = previous[0] + (int) Math.round((x - previous[0]) * j / (double) steps);
                    int zz = previous[2] + (int) Math.round((z - previous[2]) * j / (double) steps);
                    int[] last = route.get(route.size() - 1);
                    if (xx != last[0] && zz != last[2])
                        route.add(new int[] { xx, floors.getOrDefault(xx + ":" + last[2], p[1]), last[2] });
                    route.add(new int[] { xx, floors.getOrDefault(xx + ":" + zz, p[1]), zz });
                }
            }
            for (int i = 1; i < route.size(); i++) {
                int[] before = route.get(i - 1), after = route.get(i);
                if (Math.abs(before[0] - after[0]) + Math.abs(before[2] - after[2]) != 1) continue;
                int beforeY = actualBranchFloor(before[0], before[2], y0, before[1], ax, az, tips);
                int afterY = actualBranchFloor(after[0], after[2], y0, after[1], ax, az, tips);
                if (Math.abs(afterY - beforeY) != 1 || i < accessStart && beforeY == before[1] && afterY == after[1])
                    continue;
                int[] low = beforeY < afterY ? before : after, high = beforeY < afterY ? after : before;
                int lowY = Math.min(beforeY, afterY), highY = lowY + 1;
                boolean alongX = high[0] != low[0];
                int facing = alongX ? (high[0] > low[0] ? 0 : 1) : (high[2] > low[2] ? 2 : 3);
                for (int side = -3; side <= 3; side++) {
                    int hx = high[0] + (alongX ? 0 : side), hz = high[2] + (alongX ? side : 0);
                    Integer floor = floors.get(hx + ":" + hz);
                    if (floor == null && Math.hypot(ax + hx - p[0], az + hz - p[2]) <= 19) floor = p[1];
                    if (floor == null || actualBranchFloor(hx, hz, y0, floor, ax, az, tips) != highY) continue;
                    // Keep the spiral's own transverse stair direction and every chest anchor.
                    if (branchJoinFloor(hx, hz, y0, highY + 1) != highY + 1) continue;
                    boolean anchor = false;
                    for (int[] tip : tips) if (tip[0] == ax + hx && tip[2] == az + hz) anchor = true;
                    if (anchor) continue;
                    logStep(b, ax + hx, highY, az + hz, facing);
                    block(
                        b,
                        ax + low[0] + (alongX ? 0 : side),
                        highY + 2,
                        az + low[2] + (alongX ? side : 0),
                        Blocks.air);
                }
            }
        }
    }

    /** Shared walking surface wins over the transverse support timber of an intersecting room branch. */
    private static void restoreSpiralWalk(StructureBuilder b, int ax, int az, int y0, int trunkH, int radius,
        int riseRun, int width) {
        int sideLength = radius * 2, period = sideLength * 4, steps = (trunkH + 2) * riseRun + riseRun - 1;
        for (int s = 0; s <= steps; s++) {
            int t = s % period, x, z;
            if (t < sideLength) {
                x = -radius + t;
                z = -radius;
            } else if (t < sideLength * 2) {
                x = radius;
                z = -radius + t - sideLength;
            } else if (t < sideLength * 3) {
                x = radius - (t - sideLength * 2);
                z = radius;
            } else {
                x = -radius;
                z = radius - (t - sideLength * 3);
            }
            int y = y0 + s / riseRun;
            for (int a = 0; a < width; a++) {
                int xx = ax + x + (Math.abs(z) == radius ? 0 : (x > 0 ? a : -a));
                int zz = az + z + (Math.abs(z) == radius ? (z > 0 ? a : -a) : 0);
                if (!inSlice(b, xx, zz)) continue;
                if (s > 0 && s % riseRun == 0) logStep(
                    b,
                    xx,
                    y,
                    zz,
                    t == 0 ? 3 : t <= sideLength ? 0 : t <= sideLength * 2 ? 2 : t <= sideLength * 3 ? 1 : 3);
                else wood(b, xx, y, zz, Math.abs(z) == radius ? 4 : 8);
                for (int h = 1; h <= 2; h++) block(b, xx, y + h, zz, Blocks.air);
            }
        }

    }

    private static int connectorFloor(int j, int top, int base, int radius, int width) {
        int westLanding = -radius - width + 1;
        if (j <= westLanding) return top - (int) Math.round((top - base) * (j + 55) / (double) (westLanding + 55));
        if (j < -radius) return base;
        return base + (int) Math.round((top - base) * (j + radius) / (double) radius);
    }

    /** Join the actual spiral crossing height, including its entire transverse landing. */
    private static void restoreCourtConnector(StructureBuilder b, int ax, int az, int y0, int trunkH, int radius,
        int width) {
        int top = y0 + trunkH + 2, base = y0 + 10 + 12 * Math.floorDiv(trunkH + 2 - 10, 12);
        for (int j = -55; j <= 0; j++) for (int side = -3; side <= 3; side++) {
            int y = connectorFloor(j, top, base, radius, width),
                previous = connectorFloor(j - 1, top, base, radius, width);
            if (!inSlice(b, ax + j, az + side)) continue;
            if (j > -55 && y > previous) logStep(b, ax + j, y, az + side, 0);
            else wood(b, ax + j, y, az + side, 4);
            if (j > -55 && y < previous) logStep(b, ax + j - 1, previous, az + side, 1);
            for (int h = 1; h <= 4; h++) block(b, ax + j, y + h, az + side, Blocks.air);
        }
    }

    public static int[] encounterDimensions(World w, int ax, int az) {
        return new int[] {
            com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile.heightAt(w.getSeed(), ax, az),
            com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperityDecorPlacer
                .islandTreeHeightAt(w.getSeed(), ax, az) };
    }

    public static void ensureBounds(World w, int ax, int az) {
        int[] dims = encounterDimensions(w, ax, az);
        ForgottenLakeEncounterData.get(w)
            .registerBounds(encounterId(w, ax, az), ax, az, dims[0], dims[0] + dims[1] + 16, 160);
    }

    public static void initializeChunk(World w, int ax, int az, int y0, int trunkH, int cx, int cz) {
        if (w.isRemote) return;
        ForgottenLakeEncounterData d = ForgottenLakeEncounterData.get(w);
        String id = encounterId(w, ax, az);
        d.registerLayout(id, 3);
        d.registerBounds(id, ax, az, y0, y0 + trunkH + 16, 160);
        if (d.layoutVersion(id) == 1) {
            initializeLegacyChunk(w, ax, az, y0, trunkH, cx, cz);
            return;
        }
        if (d.layoutVersion(id) == 2) {
            initializeV2Chunk(w, ax, az, y0, trunkH, cx, cz);
            return;
        }
        java.util.List<int[]> tips = com.miaokatze.gtsr.common.dimension.prosperity.ruins.IslandMegaTree
            .branchChestPositions(w.getSeed(), ax, az, y0, trunkH);
        for (int i = 0; i < tips.size(); i++) {
            int[] c = tips.get(i);
            String node = "branchChest" + i;
            if (!owner(c[0], c[2], cx, cz) || d.created(id, node)) continue;
            if (!w.setBlock(c[0], c[1], c[2], ForgottenLakeEncounterRegistry.sealedChest, 0, 2)) continue;
            TileEntity tile = w.getTileEntity(c[0], c[1], c[2]);
            if (tile instanceof TileEntitySealedChest) {
                ((TileEntitySealedChest) tile).initializeClickUnlock(1 + (i % 2), id);
                d.createdNode(id, node);
            }
        }
        for (int room = 0; room < 8; room++) {
            for (int n = 0; n < ROOM_CHEST_COUNTS[room]; n++) {
                int[] c = chestPosition(ax, az, y0, trunkH, room, n);
                chest(w, d, id, "platformChest" + room + (n == 0 ? "" : "_" + n), c[0], c[1], c[2], 2, room, cx, cz);
            }
            for (int n = 0; n < d.guardCount(id, room); n++) {
                int[] p = guardPosition(ax, az, y0, trunkH, room, n);
                String node = "guard" + d.guardIndex(id, room, n);
                if (owner(p[0], p[2], cx, cz) && !d.created(id, node)
                    && !d.guardDead(id, room, n)
                    && !d.platformCleared(id, room)) {
                    EntityResidualOathguard guard = new EntityResidualOathguard(w);
                    guard.initialize(id, room, p[0] + .5, p[1], p[2] + .5);
                    guard.setOrdinal(n);
                    guard.setHomeYaw(guardYaw(room, n));
                    if (w.spawnEntityInWorld(guard)) d.createdNode(id, node);
                }
            }
        }
        int[] t = throne(ax, az, y0, trunkH);
        if (owner(ax, az, cx, cz) && !d.created(id, "king") && !d.kingDead(id)) {
            EntitySilentKing king = new EntitySilentKing(w);
            king.initialize(id, -1, ax + .5, t[1], az + .5);
            king.setHomeYaw(KING_YAW);
            if (w.spawnEntityInWorld(king)) d.createdNode(id, "king");
        }
        for (int i = 0; i < 9; i++) {
            int[] c = throneChestPosition(ax, az, y0, trunkH, i);
            chest(w, d, id, "throneChest" + i, c[0], c[1], c[2], i < 3 ? 3 : i < 6 ? 4 : 5, -1, cx, cz);
        }
        com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterWorldgen.treeOverlay(w, ax, az, y0, cx, cz);
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

    private static void initializeLegacyChunk(World w, int ax, int az, int y0, int trunkH, int cx, int cz) {
        if (w.isRemote) return;
        ForgottenLakeEncounterData d = ForgottenLakeEncounterData.get(w);
        String id = encounterId(w, ax, az);
        for (int i = 0; i < 4; i++) {
            int[] p = legacyPlatform(ax, az, y0, trunkH, i);
            chest(w, d, id, "platformChest" + i, p[0], p[1] + 1, p[2], 2, i, cx, cz);
            for (int n = 0; n < 3; n++) {
                int x = p[0] - 3 + n * 3, z = p[2] + 3;
                String node = "guard" + (i * 3 + n);
                if (owner(x, z, cx, cz) && !d.created(id, node) && !d.platformCleared(id, i)) {
                    EntityResidualOathguard g = new EntityResidualOathguard(w);
                    g.initialize(id, i, x + .5, p[1] + 1, z + .5);
                    g.setOrdinal(n);
                    g.setHomeYaw(platformYawV2(i));
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
