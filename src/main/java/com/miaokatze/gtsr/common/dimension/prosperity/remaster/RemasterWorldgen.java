package com.miaokatze.gtsr.common.dimension.prosperity.remaster;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;
import com.miaokatze.gtsr.common.dimension.framework.structure.ChunkClampedSink;
import com.miaokatze.gtsr.common.dimension.prosperity.echo.EchoKind;
import com.miaokatze.gtsr.common.dimension.prosperity.echo.EntityOldEcho;
import com.miaokatze.gtsr.common.dimension.prosperity.echo.RuinSite;
import com.miaokatze.gtsr.common.dimension.prosperity.echo.RuinsEncounterData;

/** Only the population owner's new slice writes rock, explicit air, interactive blocks and one-shot entities. */
public final class RemasterWorldgen {

    private RemasterWorldgen() {}

    public static boolean allowed(World w, RemasterSite s) {
        if ("city-plot".equals(s.layout)) {
            for (RemasterSite parent : RemasterPlanner
                .cell(s.seed, 0, Math.floorDiv(s.x >> 4, 128), Math.floorDiv(s.z >> 4, 128)))
                if ("city-grid".equals(parent.layout) && !allowed(w, parent)) return false;
        }
        int radius = Math.max(s.maxX() - s.minX(), s.maxZ() - s.minZ()) / 2 + 512;
        for (RuinSite old : RuinsEncounterData.get(w)
            .existingSitesNear((s.minX() + s.maxX()) / 2, (s.minZ() + s.maxZ()) / 2, radius))
            if (s.overlaps(old.x, old.z, old.x + old.width - 1, old.z + old.depth - 1, 0)) return false;
        return true;
    }

    public static boolean generate(World w, int cx, int cz) {
        if (w.isRemote) return false;
        Map<String, RemasterSite> sites = new LinkedHashMap<>();
        for (RemasterSite s : RemasterData.get(w)
            .inChunk(cx, cz)) if (!"tree-overlay".equals(s.layout)) sites.put(s.id(), s); // The natural tree pass must
                                                                                          // remain the geometry owner.
        for (RemasterSite s : RemasterPlanner.near(w.getSeed(), cx, cz)) if (allowed(w, s)) sites.put(s.id(), s);
        java.util.List<RemasterSite> ordered = new java.util.ArrayList<>(sites.values());
        ordered.sort(java.util.Comparator.comparingInt(s -> "city-grid".equals(s.layout) ? 0 : 1));
        for (RemasterSite s : ordered) placeChunk(w, s, cx, cz);
        return !sites.isEmpty();
    }

    private static boolean owned(int x, int y, int z, int cx, int cz) {
        return y >= 1 && y <= 254 && (x >> 4) == cx && (z >> 4) == cz;
    }

    public static RemasterPrefab activePlan(World w, RemasterSite s) {
        int variant = s.variant;
        if ("fiction_expansion_project".equals(s.prefab)) variant = Math.max(
            0,
            Math.min(
                2,
                RemasterData.get(w)
                    .state(s.id())
                    .getCompoundTag("engineering")
                    .getInteger("phase")));
        return RemasterCatalog.get(s.prefab, variant);
    }

    /** Pure production geometry entrypoint also used by the offline owner-chunk harness. */
    public static int geometry(RemasterSite s, RemasterPrefab p, BlockSink sink, int cx, int cz) {
        int writes = 0;
        if ("city-grid".equals(s.layout)) {
            for (int x = cx << 4; x < (cx << 4) + 16; x++) for (int z = cz << 4; z < (cz << 4) + 16; z++) {
                int u = x - s.x, v = z - s.z;
                if (u < 0 || v < 0 || u >= 768 || v >= 384) continue;
                boolean road = u % 96 < 8 || v % 96 < 8;
                boolean plaza = u >= 680 && u < 760 && v >= 104 && v < 280;
                if ((road || plaza) && sink.setBlock(x, s.y - 1, z, "minecraft:stonebrick", 0, 2)) writes++;
            }
            return writes;
        }
        int localX0 = (cx << 4) - s.x, localZ0 = (cz << 4) - s.z;
        for (int lx = Math.floorDiv(localX0, 16); lx <= Math.floorDiv(localX0 + 15, 16); lx++)
            for (int lz = Math.floorDiv(localZ0, 16); lz <= Math.floorDiv(localZ0 + 15, 16); lz++)
                for (RemasterPrefab.Run r : p.slice(lx, lz)) {
                    String key = p.palette[r.paletteIndex];
                    if ("tree-overlay".equals(s.layout) && (key.startsWith("minecraft:air")
                        || key.toLowerCase(java.util.Locale.ROOT)
                            .contains("wood")
                        || key.toLowerCase(java.util.Locale.ROOT)
                            .contains("leaves")))
                        continue;
                    for (int i = 0; i < r.length; i++) {
                        int x = s.x + r.x + i, y = s.y + r.y, z = s.z + r.z;
                        if (!owned(x, y, z, cx, cz)) continue;
                        int split = key.lastIndexOf('#');
                        String block = split < 0 ? key : key.substring(0, split);
                        int meta = split < 0 ? 0 : Integer.parseInt(key.substring(split + 1));
                        if (sink.setBlock(x, y, z, block, meta, 2)) writes++;
                    }
                }
        return writes;
    }

    public static boolean placeGeometryChunk(final World w, final RemasterSite s, int cx, int cz) {
        final BlockSink owned = new ChunkClampedSink(w, cx, cz);
        final int[] accepted = { 0 };
        final boolean[] failed = { false };
        BlockSink resolve = (x, y, z, b, meta, flags) -> {
            if (!owned(x, y, z, cx, cz) || !w.blockExists(x, y, z)) {
                failed[0] = true;
                return false;
            }
            String authored = b.toString() + "#" + meta;
            Block block = RemasterRuntime.resolve(authored);
            int actualMeta = RemasterRuntime.meta(authored);
            if ("tree-overlay".equals(s.layout)) {
                Block current = w.getBlock(x, y, z);
                if (current.isWood(w, x, y, z) || current.isLeaves(w, x, y, z)) return false;
                TileEntity existing = w.getTileEntity(x, y, z);
                if (existing != null && !(existing instanceof TileRemasterNode)) return false;
            }
            boolean set = owned.setBlock(x, y, z, block, actualMeta, flags);
            if (w.getBlock(x, y, z) == block && w.getBlockMetadata(x, y, z) == actualMeta) accepted[0]++;
            else failed[0] = true;
            return set;
        };
        RemasterTerrain.build(s, resolve, cx, cz);
        geometry(s, "city-grid".equals(s.layout) ? null : activePlan(w, s), resolve, cx, cz);
        return accepted[0] > 0 && !failed[0];
    }

    public static void placeChunk(World w, RemasterSite s, int cx, int cz) {
        if (w.isRemote || !s.intersects(cx, cz) || !allowed(w, s)) return;
        RemasterData d = RemasterData.get(w);
        d.register(s);
        String geom = "geom:" + cx + ":" + cz;
        if (!d.flag(s.id(), geom)) {
            if (!placeGeometryChunk(w, s, cx, cz)) return;
            d.flag(s.id(), geom, true);
        }
        if ("city-grid".equals(s.layout)) {
            cityWitnessGuard(w, s, cx, cz);
            return;
        }
        List<JsonObject> nodes = RemasterRuntime.nodes(s);
        for (JsonObject n : nodes) {
            int x = s.x + RemasterRuntime.integer(n, "x", 0), y = s.y + RemasterRuntime.integer(n, "y", 0),
                z = s.z + RemasterRuntime.integer(n, "z", 0);
            String id = RemasterRuntime.string(n, "id", ""), role = RemasterRuntime.string(n, "role", "control");
            if (!owned(x, y, z, cx, cz) || !w.blockExists(x, y, z) || d.flag(s.id(), "node:" + id)) continue;
            String key = RemasterRuntime.string(n, "block", "gtsr:draft6_iris_controller#0");
            if ("chest".equals(role)) key = "gtsr:draft7_seal_chest#0";
            else if ("spawner".equals(role) && !n.has("block")) key = "gtsr:draft6_spawner_stable#0";
            Block block = RemasterRuntime.resolve(key);
            if (block == Blocks.air) block = RemasterBlocks.get("gtsr:draft6_iris_controller");
            if (block == null) continue;
            if ("tree-overlay".equals(s.layout) && (w.getBlock(x, y, z)
                .isWood(w, x, y, z)
                || w.getBlock(x, y, z)
                    .isLeaves(w, x, y, z)
                || (w.getTileEntity(x, y, z) != null && !(w.getTileEntity(x, y, z) instanceof TileRemasterNode))))
                continue;
            if (!w.setBlock(x, y, z, block, RemasterRuntime.meta(key), 2) && w.getBlock(x, y, z) != block) continue;
            TileEntity t = w.getTileEntity(x, y, z);
            if (t instanceof TileRemasterNode) {
                ((TileRemasterNode) t).initialize(s.id(), id, role);
                d.flag(s.id(), "node:" + id, true);
            }
        }
        if ("tree-overlay".equals(s.layout)) return; // Original encounter ledger owns the 32 guards and dedicated king.
        int index = 0;
        for (JsonElement e : RemasterRuntime.array(s.plan().metadata, "spawns")) {
            JsonObject spawn = e.getAsJsonObject();
            int i = index++;
            int x = s.x + RemasterRuntime.integer(spawn, "x", 0), y = s.y + RemasterRuntime.integer(spawn, "y", 0),
                z = s.z + RemasterRuntime.integer(spawn, "z", 0);
            String code = RemasterRuntime.string(spawn, "code", "");
            if (!owned(x, y, z, cx, cz) || !w.blockExists(x, y, z)
                || d.flag(s.id(), "entity:" + i)
                || code.isEmpty()
                || "dr-09".equals(code)
                || "dc-10".equals(code)) continue;
            if (spawn.has("spawn") && !spawn.get("spawn")
                .getAsBoolean()) continue;
            if (d.flag(s.id(), "dead:" + RemasterRuntime.string(spawn, "id", Integer.toString(i)))) {
                d.flag(s.id(), "entity:" + i, true);
                continue;
            }
            EntityOldEcho echo = new EntityOldEcho(w);
            echo.initializeEcho(EchoKind.byCode(code), s.id(), x + .5, y, z + .5, false);
            echo.setNodeIndex(i);
            echo.configureObjective(
                7,
                0,
                ("dc-02".equals(code) || "dc-08".equals(code))
                    && "boss".equals(RemasterRuntime.string(spawn, "role", "")));
            if (w.spawnEntityInWorld(echo)) d.flag(s.id(), "entity:" + i, true);
        }
    }

    /** The authored city witness lives on the independent plaza, outside all 28 final plots. */
    private static void cityWitnessGuard(World w, RemasterSite s, int cx, int cz) {
        RemasterData data = RemasterData.get(w);
        int x = s.x + 724, y = s.y, z = s.z + 196;
        if (!owned(x, y, z, cx, cz) || !w.blockExists(x, y, z)) return;
        int index = 0;
        for (JsonElement e : RemasterRuntime.array(s.plan().metadata, "spawns")) {
            JsonObject spawn = e.getAsJsonObject();
            int nodeIndex = index++;
            if (!"di-07".equals(RemasterRuntime.string(spawn, "code", ""))) continue;
            String spawnId = RemasterRuntime.string(spawn, "id", Integer.toString(nodeIndex));
            if (data.flag(s.id(), "entity:" + nodeIndex) || data.flag(s.id(), "dead:" + spawnId)) return;
            EntityOldEcho guard = new EntityOldEcho(w);
            guard.initializeEcho(EchoKind.byCode("di-07"), s.id(), x + .5, y, z + .5, false);
            guard.setNodeIndex(nodeIndex);
            guard.configureObjective(7, 0, false);
            if (!w.blockExists(x - 1, y, z - 1) || !w.blockExists(x + 1, y + 4, z + 1)
                || w.getBlock(x, y - 1, z) == Blocks.air
                || !w.getCollidingBoundingBoxes(guard, guard.boundingBox)
                    .isEmpty()
                || !w.checkNoEntityCollision(guard.boundingBox)
                || w.isAnyLiquid(guard.boundingBox)) return;
            if (w.spawnEntityInWorld(guard)) {
                net.minecraft.nbt.NBTTagCompound position = new net.minecraft.nbt.NBTTagCompound();
                position.setInteger("x", x);
                position.setInteger("y", y);
                position.setInteger("z", z);
                data.state(s.id())
                    .setTag("spawn-pos:" + spawnId, position);
                data.flag(s.id(), "entity:" + nodeIndex, true);
            }
            return;
        }
    }

    /** Decorative/interactive overlay bound to the original natural tree, never a second royal encounter. */
    public static void treeOverlay(World w, int ax, int az, int y0, int cx, int cz) {
        int anchorY = Math.min(
            y0,
            254 - RemasterCatalog.descriptor("forgotten_lake_court", 0)
                .get("yMax")
                .getAsInt());
        RemasterSite s = new RemasterSite(
            "forgotten_lake_court",
            0,
            w.getSeed(),
            ax - 154,
            anchorY,
            az - 154,
            "tree-overlay");
        RemasterData.get(w)
            .register(s);
        RemasterData.get(w)
            .state(s.id())
            .setString(
                "legacyEncounter",
                com.miaokatze.gtsr.common.dimension.prosperity.encounter.ForgottenLakeEncounterStructure
                    .encounterId(w, ax, az));
        RemasterData.get(w)
            .markDirty();
        placeChunk(w, s, cx, cz);
    }
}
