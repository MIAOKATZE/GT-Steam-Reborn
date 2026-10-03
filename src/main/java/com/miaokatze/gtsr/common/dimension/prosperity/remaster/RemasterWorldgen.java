package com.miaokatze.gtsr.common.dimension.prosperity.remaster;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;
import com.miaokatze.gtsr.common.dimension.framework.structure.ChunkClampedSink;
import com.miaokatze.gtsr.common.dimension.prosperity.echo.EchoKind;
import com.miaokatze.gtsr.common.dimension.prosperity.echo.EntityOldEcho;
import com.miaokatze.gtsr.common.dimension.prosperity.echo.RuinSite;
import com.miaokatze.gtsr.common.dimension.prosperity.echo.RuinsEncounterData;
import com.miaokatze.gtsr.common.dimension.prosperity.encounter.TileEntitySealedChest;

/** Only the population owner's new slice writes rock, explicit air, interactive blocks and one-shot entities. */
public final class RemasterWorldgen {

    // revision3.refineTree's frozen V3 export: center (160,3,152), trunk height 142.
    private static final int TREE_SOURCE_X = 160, TREE_SOURCE_Z = 152;
    private static final int TREE_SOURCE_BASE_Y = 3, TREE_SOURCE_TRUNK_HEIGHT = 142;
    private static final int TREE_SOURCE_UPPER_MIN_Y = 78;

    private RemasterWorldgen() {}

    public static boolean allowed(World w, RemasterSite s) {
        if (!RemasterRollout.allowsGeneration(s)) return false;
        if (!RemasterData.get(w)
            .allowsSavedPlacement(s)) return false;
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
        RemasterData data = RemasterData.get(w);
        Map<String, RemasterSite> sites = new LinkedHashMap<>();
        // Keep saved identities, including Y, but freeze all paused IDs and nonstandard variants.
        for (RemasterSite s : data.inChunk(cx, cz))
            if (RemasterRollout.allowsGeneration(s) && !"tree-overlay".equals(s.layout)) sites.put(s.id(), s);
        for (RemasterSite s : RemasterPlanner.near(w.getSeed(), cx, cz))
            if (allowed(w, s)) sites.putIfAbsent(s.id(), s);
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
        if (!RemasterRollout.allowsGeneration(s) || !RemasterEntryApron.valid(s, p)) return 0;
        int writes = 0;
        if ("city-grid".equals(s.layout)) {
            if (s.roadVersion > 0) return RemasterCityBridge.geometry(s, sink, cx, cz);
            for (int x = cx << 4; x < (cx << 4) + 16; x++) for (int z = cz << 4; z < (cz << 4) + 16; z++) {
                int u = x - s.x, v = z - s.z;
                if (u < 0 || v < 0 || u >= 768 || v >= 384) continue;
                boolean road = u % 96 < 8 || v % 96 < 8;
                boolean plaza = u >= 680 && u < 760 && v >= 104 && v < 280;
                if (road || plaza) {
                    if (sink.setBlock(x, s.y - 1, z, "minecraft:stonebrick", 0, 2)) writes++;
                }
            }
            return writes;
        }
        int localX0 = (cx << 4) - s.x, localZ0 = (cz << 4) - s.z;
        JsonArray legacyEntry = "tree-overlay".equals(s.layout) && p.metadata.has("legacyEntranceBinding")
            ? p.metadata.getAsJsonObject("legacyEntranceBinding")
                .getAsJsonArray("sourceAt")
            : null;
        for (int lx = Math.floorDiv(localX0, 16); lx <= Math.floorDiv(localX0 + 15, 16); lx++)
            for (int lz = Math.floorDiv(localZ0, 16); lz <= Math.floorDiv(localZ0 + 15, 16); lz++)
                for (RemasterPrefab.Run r : p.slice(lx, lz)) {
                    String key = p.palette[r.paletteIndex];
                    // The fixed source's lower spiral ornaments have no height-independent natural support.
                    // The real tree generator owns that route; only its separately anchored entrance is added.
                    if ("tree-overlay".equals(s.layout) && r.y < TREE_SOURCE_UPPER_MIN_Y) continue;
                    if ("tree-overlay".equals(s.layout) && (key.startsWith("minecraft:air")
                        || key.toLowerCase(java.util.Locale.ROOT)
                            .contains("wood")
                        || key.toLowerCase(java.util.Locale.ROOT)
                            .contains("leaves")
                        || key.toLowerCase(java.util.Locale.ROOT)
                            .contains("zenithlog")
                        || key.startsWith("gtsr:royal_zenith_log"))) continue;
                    for (int i = 0; i < r.length; i++) {
                        if (legacyEntry != null && r.x + i == legacyEntry.get(0)
                            .getAsInt()
                            && r.y == legacyEntry.get(1)
                                .getAsInt()
                            && r.z == legacyEntry.get(2)
                                .getAsInt())
                            continue;
                        int x = s.x + r.x + i, y = s.y + r.y, z = s.z + r.z;
                        if (!owned(x, y, z, cx, cz)) continue;
                        int split = key.lastIndexOf('#');
                        String block = split < 0 ? key : key.substring(0, split);
                        int meta = split < 0 ? 0 : Integer.parseInt(key.substring(split + 1));
                        if (sink.setBlock(x, y, z, block, meta, 2)) writes++;
                    }
                }
        return writes + RemasterEntryApron.geometry(s, p, sink, cx, cz);
    }

    public static boolean placeGeometryChunk(final World w, final RemasterSite s, int cx, int cz) {
        if (!RemasterRollout.allowsGeneration(s)) return false;
        RemasterPrefab plan = "city-grid".equals(s.layout) ? null : activePlan(w, s);
        if (!RemasterEntryApron.valid(s, plan)) return false;
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
            TileEntity before = w.getTileEntity(x, y, z);
            boolean set = owned.setBlock(x, y, z, block, actualMeta, flags);
            if (w.getBlock(x, y, z) == block && w.getBlockMetadata(x, y, z) == actualMeta) {
                accepted[0]++;
                if (set) RemasterRuntime.recordGeneratedTile(w, s, x, y, z, before);
            } else failed[0] = true;
            return set;
        };
        RemasterTerrain.build(s, resolve, cx, cz);
        geometry(s, plan, resolve, cx, cz);
        boolean entry = placeTreeEntrance(w, s, cx, cz, true);
        return (accepted[0] > 0 || entry) && !failed[0];
    }

    /** The natural tree owns its platform; the entrance sign uses the real spiral-start anchor. */
    private static boolean placeTreeEntrance(World w, RemasterSite s, int cx, int cz, boolean admit) {
        if (!"tree-overlay".equals(s.layout) || RemasterData.get(w)
            .legacyTreeLayout(s) || !s.plan().metadata.has("legacyEntranceBinding")) return false;
        String nodeId = RemasterRuntime.string(s.plan().metadata.getAsJsonObject("legacyEntranceBinding"), "node", "");
        RemasterData data = RemasterData.get(w);
        String closed = "tree-entrance:closed", pending = "tree-entrance:pending";
        if (data.flag(s.id(), closed) || data.flag(s.id(), "node:" + nodeId)) return false;
        for (JsonObject n : RemasterRuntime.nodes(s)) {
            if (!nodeId.equals(RemasterRuntime.string(n, "id", ""))) continue;
            int[] at = RemasterRuntime.nodePosition(w, s, n);
            if (at == null || !owned(at[0], at[1], at[2], cx, cz)
                || !w.blockExists(at[0], at[1], at[2])
                || !w.blockExists(at[0], at[1] - 1, at[2])) return false;
            if (!admit && !data.flag(s.id(), pending)) return false;
            if (w.getBlock(at[0], at[1], at[2]) != Blocks.air || w.getTileEntity(at[0], at[1], at[2]) != null) {
                data.flag(s.id(), closed, true);
                data.flag(s.id(), pending, false);
                return false;
            }
            if (!w.getBlock(at[0], at[1] - 1, at[2])
                .isWood(w, at[0], at[1] - 1, at[2])) return false;
            data.flag(s.id(), pending, true);
            String authored = RemasterRuntime.string(n, "block", "gtsr:draft_notice_board#0");
            Block block = RemasterRuntime.resolve(authored);
            if (!w.setBlock(at[0], at[1], at[2], block, RemasterRuntime.meta(authored), 2)) return false;
            RemasterRuntime.recordGeneratedTile(w, s, at[0], at[1], at[2], null);
            data.flag(s.id(), pending, false);
            data.flag(s.id(), closed, true);
            return true;
        }
        return false;
    }

    public static void placeChunk(World w, RemasterSite s, int cx, int cz) {
        if (!RemasterRollout.allowsGeneration(s) || w.isRemote) return;
        RemasterData d = RemasterData.get(w);
        RemasterSite saved = d.site(s.id());
        if (saved != null) s = saved;
        if (!s.intersects(cx, cz) || !allowed(w, s)) return;
        d.register(s);
        if ("city-grid".equals(s.layout)) rememberCityPlots(w, d, s);
        String geom = "geom:" + cx + ":" + cz;
        if (!d.flag(s.id(), geom)) {
            if (!placeGeometryChunk(w, s, cx, cz)) return;
            d.flag(s.id(), geom, true);
        }
        completeChunk(w, s, cx, cz);
    }

    private static void rememberCityPlots(World w, RemasterData data, RemasterSite parent) {
        if (data.flag(parent.id(), "city-plots-planned")) return;
        for (RemasterSite child : RemasterPlanner.savedCityPlots(parent))
            if (data.site(child.id()) == null && allowed(w, child)) data.register(child);
        data.flag(parent.id(), "city-plots-planned", true);
    }

    /** Retry only the completion of existing geometry; never regenerate player-edited buildings. */
    public static void completeChunk(World w, RemasterSite s, int cx, int cz) {
        if (!RemasterRollout.allowsGeneration(s) || w.isRemote
            || !s.intersects(cx, cz)
            || !w.blockExists(cx << 4, s.y, cz << 4)) return;
        RemasterData d = RemasterData.get(w);
        placeTreeEntrance(w, s, cx, cz, false);
        if (!d.flag(s.id(), "geom:" + cx + ":" + cz)) {
            // A separately admitted entrance must complete even when this owner has no upper geometry.
            if ("tree-overlay".equals(s.layout)) {
                String entrance = RemasterRuntime
                    .string(s.plan().metadata.getAsJsonObject("legacyEntranceBinding"), "node", "");
                for (JsonObject n : RemasterRuntime.nodes(s)) {
                    if (!entrance.equals(RemasterRuntime.string(n, "id", ""))) continue;
                    int[] at = RemasterRuntime.nodePosition(w, s, n);
                    if (at != null && owned(at[0], at[1], at[2], cx, cz) && RemasterRuntime.installNode(w, s, n))
                        d.flag(s.id(), "node:" + entrance, true);
                }
            }
            return;
        }
        if ("city-grid".equals(s.layout)) {
            cityWitnessGuard(w, s, cx, cz);
            return;
        }
        List<JsonObject> nodes = RemasterRuntime.nodes(s);
        for (JsonObject n : nodes) {
            int[] at = RemasterRuntime.nodePosition(w, s, n);
            if (at == null) continue;
            int x = at[0], y = at[1], z = at[2];
            String id = RemasterRuntime.string(n, "id", "");
            if (!owned(x, y, z, cx, cz) || !w.blockExists(x, y, z)) continue;
            TileEntity existing = w.getTileEntity(x, y, z);
            boolean ownedChest = existing instanceof TileEntitySealedChest && (RemasterRuntime.generatedFor(existing, s)
                || ((TileEntitySealedChest) existing).isRemasterNode(s.id(), id));
            if ("tree-overlay".equals(s.layout) && (w.getBlock(x, y, z)
                .isWood(w, x, y, z)
                || w.getBlock(x, y, z)
                    .isLeaves(w, x, y, z)
                || (!(existing instanceof TileRemasterNode) && !ownedChest))) continue;
            if (RemasterRuntime.installNode(w, s, n)) d.flag(s.id(), "node:" + id, true);
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
            EntityOldEcho echo = RemasterSpawn.spawn(
                w,
                EchoKind.byCode(code),
                s.id(),
                x + .5,
                y,
                z + .5,
                i,
                ("dc-02".equals(code) || "dc-08".equals(code))
                    && "boss".equals(RemasterRuntime.string(spawn, "role", "")));
            if (echo != null) {
                net.minecraft.nbt.NBTTagCompound position = new net.minecraft.nbt.NBTTagCompound();
                position.setDouble("x", echo.posX);
                position.setDouble("y", echo.posY);
                position.setDouble("z", echo.posZ);
                d.state(s.id())
                    .setTag("spawn-pos:" + RemasterRuntime.string(spawn, "id", Integer.toString(i)), position);
                d.flag(s.id(), "entity:" + i, true);
            }
        }
    }

    /** The authored city witness lives on the independent plaza, outside all 28 final plots. */
    private static void cityWitnessGuard(World w, RemasterSite s, int cx, int cz) {
        RemasterData data = RemasterData.get(w);
        int[] at = RemasterCityBridge.witnessPosition(s);
        if (at == null) return;
        int x = at[0], y = at[1], z = at[2];
        if (!owned(x, y, z, cx, cz) || !w.blockExists(x, y, z)) return;
        int index = 0;
        for (JsonElement e : RemasterRuntime.array(s.plan().metadata, "spawns")) {
            JsonObject spawn = e.getAsJsonObject();
            int nodeIndex = index++;
            if (!"di-07".equals(RemasterRuntime.string(spawn, "code", ""))) continue;
            String spawnId = RemasterRuntime.string(spawn, "id", Integer.toString(nodeIndex));
            if (data.flag(s.id(), "entity:" + nodeIndex) || data.flag(s.id(), "dead:" + spawnId)) return;
            EntityOldEcho guard = RemasterSpawn
                .spawn(w, EchoKind.byCode("di-07"), s.id(), x + .5, y, z + .5, nodeIndex, false);
            if (guard != null) {
                net.minecraft.nbt.NBTTagCompound position = new net.minecraft.nbt.NBTTagCompound();
                position.setDouble("x", guard.posX);
                position.setDouble("y", guard.posY);
                position.setDouble("z", guard.posZ);
                data.state(s.id())
                    .setTag("spawn-pos:" + spawnId, position);
                data.flag(s.id(), "entity:" + nodeIndex, true);
            }
            return;
        }
    }

    /** Decorative/interactive overlay bound to the original natural tree, never a second royal encounter. */
    public static void treeOverlay(World w, int ax, int az, int y0, int cx, int cz) {
        int trunkH = com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperityDecorPlacer
            .islandTreeHeightAt(w.getSeed(), ax, az);
        String encounter = com.miaokatze.gtsr.common.dimension.prosperity.encounter.ForgottenLakeEncounterStructure
            .encounterId(w, ax, az);
        RemasterSite s = new RemasterSite(
            "forgotten_lake_court",
            0,
            w.getSeed(),
            ax - TREE_SOURCE_X,
            y0 + trunkH - TREE_SOURCE_BASE_Y - TREE_SOURCE_TRUNK_HEIGHT,
            az - TREE_SOURCE_Z,
            "tree-overlay");
        RemasterData data = RemasterData.get(w);
        RemasterSite saved = data.site(s.id());
        if (saved == null) {
            RemasterSite old = new RemasterSite(
                "forgotten_lake_court",
                0,
                w.getSeed(),
                ax - 154,
                y0,
                az - 154,
                "tree-overlay");
            RemasterSite previous = data.site(old.id());
            if (previous != null && encounter.equals(
                data.state(previous.id())
                    .getString("legacyEncounter")))
                saved = previous;
        }
        // A previously generated court keeps its identity, reward ledger and geometry; no automatic migration.
        if (saved != null) s = saved;
        if (!RemasterRollout.allowsGeneration(s)) return;
        data.register(s);
        net.minecraft.nbt.NBTTagCompound treeState = data.state(s.id());
        if (s.x == ax - 154 && s.z == az - 154) treeState.setInteger("treeLayoutRevision", 1);
        treeState.setInteger("legacyAnchorX", ax);
        treeState.setInteger("legacyAnchorY", y0);
        treeState.setInteger("legacyAnchorZ", az);
        treeState.setString("legacyEncounter", encounter);
        data.markDirty();
        if (data.legacyTreeLayout(s)) completeChunk(w, s, cx, cz);
        else placeChunk(w, s, cx, cz);
    }
}
