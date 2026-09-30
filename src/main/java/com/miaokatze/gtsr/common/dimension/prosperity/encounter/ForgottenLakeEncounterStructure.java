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
                block(b, xx, y, zz, BlocksGTSR.prosperityZenithLog);
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
                wood(b, xx, y, zz, Math.abs(z) == 12 ? 4 : 8);
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
                    y = Math.min(p[1], base + j - 14);
                if (!inSlice(b, x, z)) continue;
                wood(b, x, y, z, i % 2 == 0 ? 4 : 8);
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
            int y = j < -14 ? top : Math.min(top, topBase + j + 14);
            if (!inSlice(b, ax + j, az + a)) continue;
            wood(b, ax + j, y, az + a, 4);
            for (int h = 1; h <= 4; h++) block(b, ax + j, y + h, az + a, Blocks.air);
        }
        royalRootBackdrop(b, ax, az, top);
        // Keep the entry sightline and king's full 6x12 collision column unobstructed.
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) if (inSlice(b, ax + x, az + z)) {
            wood(b, ax + x, top, az + z, 4);
            for (int h = 1; h <= 14; h++) block(b, ax + x, top + h, az + z, Blocks.air);
        }
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
                wood(b, xx, y, zz, Math.abs(z) == 12 ? 4 : 8);
                for (int h = 1; h <= 4; h++) block(b, xx, y + h, zz, Blocks.air);
            }
        }
        // The final westward riser joins the old spiral to the royal entrance and broad arena.
        int topBase = y0 + 10 + 12 * Math.floorDiv(top - y0 - 10, 12);
        for (int j = -55; j <= 0; j++) for (int a = -3; a <= 3; a++) {
            int y = j < -14 ? top : Math.min(top, topBase + j + 14);
            if (!inSlice(b, ax + j, az + a)) continue;
            wood(b, ax + j, y, az + a, 4);
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
        double start = room % 2 == 0 ? 12 : Math.sqrt(288);
        for (int t = 0; t <= 200; t++) {
            double u = t / 200D, r = start + (112 - start) * u, bend = 8 * Math.sin(Math.PI * u);
            int x = ax + (int) Math.round(cos * r - sin * bend), z = az + (int) Math.round(sin * r + cos * bend);
            int y = base + (int) Math.round((p[1] - base) * u + 2 * Math.sin(Math.PI * u));
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
                if (dx * dx + dz * dz > 12 || !inSlice(b, x + dx, z + dz)) continue;
                wood(b, x + dx, y, z + dz, Math.abs(cos) >= Math.abs(sin) ? 4 : 8);
                for (int h = 1; h <= 3; h++) wood(b, x + dx, y - h, z + dz, Math.abs(cos) >= Math.abs(sin) ? 4 : 8);
                for (int h = 1; h <= 5; h++) block(b, x + dx, y + h, z + dz, Blocks.air);
                if (dx * dx + dz * dz > 8) block(b, x + dx, y - 4, z + dz, BlocksGTSR.prosperityJadeLeaves);
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
    }

    public static void initializeChunk(World w, int ax, int az, int y0, int trunkH, int cx, int cz) {
        if (w.isRemote) return;
        ForgottenLakeEncounterData d = ForgottenLakeEncounterData.get(w);
        String id = encounterId(w, ax, az);
        d.registerLayout(id, 3);
        if (d.layoutVersion(id) == 1) {
            initializeLegacyChunk(w, ax, az, y0, trunkH, cx, cz);
            return;
        }
        if (d.layoutVersion(id) == 2) {
            initializeV2Chunk(w, ax, az, y0, trunkH, cx, cz);
            return;
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
