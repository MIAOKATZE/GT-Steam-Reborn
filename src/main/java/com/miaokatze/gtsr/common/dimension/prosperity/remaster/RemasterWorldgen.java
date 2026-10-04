package com.miaokatze.gtsr.common.dimension.prosperity.remaster;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;
import com.miaokatze.gtsr.common.dimension.framework.structure.ChunkClampedSink;
import com.miaokatze.gtsr.common.dimension.prosperity.echo.EchoKind;
import com.miaokatze.gtsr.common.dimension.prosperity.echo.EntityOldEcho;

/** Only the population owner's new slice writes rock, explicit air, interactive blocks and one-shot entities. */
public final class RemasterWorldgen {

    private static final int TREE_SOURCE_X = 160, TREE_SOURCE_Z = 152;

    private RemasterWorldgen() {}

    public static boolean allowed(World w, RemasterSite s) {
        return RemasterRollout.allowsGeneration(s) && !"tree-overlay".equals(s.layout)
            && RemasterData.get(w)
                .allowsSavedPlacement(s);
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
        for (RemasterSite s : sites.values()) placeChunk(w, s, cx, cz);
        return !sites.isEmpty();
    }

    private static boolean owned(int x, int y, int z, int cx, int cz) {
        return y >= 1 && y <= 254 && (x >> 4) == cx && (z >> 4) == cz;
    }

    public static RemasterPrefab activePlan(World w, RemasterSite s) {
        return s.plan();
    }

    /** Pure production geometry entrypoint also used by the offline owner-chunk harness. */
    public static int geometry(RemasterSite s, RemasterPrefab p, BlockSink sink, int cx, int cz) {
        if (!RemasterRollout.allowsGeneration(s) || "tree-overlay".equals(s.layout)) return 0;
        int writes = 0;
        int localX0 = (cx << 4) - s.x, localZ0 = (cz << 4) - s.z;
        // Narrow column footings only beneath authored low structural floors, never a background pad.
        if ("natural-prefab".equals(s.layout)) {
            Map<Long, Integer> bottoms = new java.util.HashMap<>();
            for (int lx = Math.floorDiv(localX0, 16); lx <= Math.floorDiv(localX0 + 15, 16); lx++)
                for (int lz = Math.floorDiv(localZ0, 16); lz <= Math.floorDiv(localZ0 + 15, 16); lz++)
                    for (RemasterPrefab.Run r : p.slice(lx, lz)) {
                        String material = p.palette[r.paletteIndex];
                        if (r.y > 3 || !(material.contains("floor") || material.contains("tiles")
                            || material.contains("masonry")
                            || material.contains("brick")
                            || material.contains("paving")
                            || material.contains("riveted_plate"))) continue;
                        for (int i = 0; i < r.length; i++) {
                            int x = s.x + r.x + i, z = s.z + r.z;
                            if (!owned(x, s.y + r.y, z, cx, cz)) continue;
                            long key = (long) x << 32 ^ (z & 0xffffffffL);
                            bottoms.merge(key, s.y + r.y, Math::min);
                        }
                    }
            for (Map.Entry<Long, Integer> e : bottoms.entrySet()) {
                int x = (int) (e.getKey() >> 32), z = (int) (long) e.getKey();
                int ground = com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile
                    .heightAt(s.seed, x, z);
                for (int y = ground + 1; y < e.getValue(); y++)
                    if (owned(x, y, z, cx, cz) && sink.setBlock(x, y, z, "minecraft:stone", 0, 2)) writes++;
            }
        }
        for (int lx = Math.floorDiv(localX0, 16); lx <= Math.floorDiv(localX0 + 15, 16); lx++)
            for (int lz = Math.floorDiv(localZ0, 16); lz <= Math.floorDiv(localZ0 + 15, 16); lz++)
                for (RemasterPrefab.Run r : p.slice(lx, lz)) {
                    String key = p.palette[r.paletteIndex];
                    int split = key.lastIndexOf('#');
                    String block = split < 0 ? key : key.substring(0, split);
                    int meta = split < 0 ? 0 : Integer.parseInt(key.substring(split + 1));
                    for (int i = 0; i < r.length; i++) {
                        int x = s.x + r.x + i, y = s.y + r.y, z = s.z + r.z;
                        if (owned(x, y, z, cx, cz) && sink.setBlock(x, y, z, block, meta, 2)) writes++;
                    }
                }
        return writes;
    }

    public static boolean placeGeometryChunk(final World w, final RemasterSite s, int cx, int cz) {
        if (!RemasterRollout.allowsGeneration(s)) return false;
        if ("tree-overlay".equals(s.layout)) return false;
        RemasterPrefab plan = activePlan(w, s);
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
            TileEntity before = w.getTileEntity(x, y, z);
            boolean set = owned.setBlock(x, y, z, block, actualMeta, flags);
            if (w.getBlock(x, y, z) == block && w.getBlockMetadata(x, y, z) == actualMeta) {
                accepted[0]++;
                if (set) RemasterRuntime.recordGeneratedTile(w, s, x, y, z, before);
            } else failed[0] = true;
            return set;
        };
        geometry(s, plan, resolve, cx, cz);
        return accepted[0] > 0 && !failed[0];
    }

    public static void placeChunk(World w, RemasterSite s, int cx, int cz) {
        if (!RemasterRollout.allowsGeneration(s) || w.isRemote) return;
        RemasterData d = RemasterData.get(w);
        RemasterSite saved = d.site(s.id());
        if (saved != null) s = saved;
        if (!s.intersects(cx, cz) || !allowed(w, s)) return;
        d.register(s);
        String geom = "geom:" + cx + ":" + cz;
        if (!d.flag(s.id(), geom)) {
            if (!placeGeometryChunk(w, s, cx, cz)) return;
            d.flag(s.id(), geom, true);
        }
        completeChunk(w, s, cx, cz);
    }

    /** Retry only the completion of existing geometry; never regenerate player-edited buildings. */
    public static void completeChunk(World w, RemasterSite s, int cx, int cz) {
        completeChunk(w, s, cx, cz, false);
    }

    /** Mission completion reuses spawn admission without installing nodes or admitting ordinary guards. */
    public static void completeBossChunk(World w, RemasterSite s, int cx, int cz) {
        completeChunk(w, s, cx, cz, true);
    }

    private static void completeChunk(World w, RemasterSite s, int cx, int cz, boolean bossOnly) {
        if (!RemasterRollout.allowsGeneration(s) || w.isRemote
            || !s.intersects(cx, cz)
            || !w.blockExists(cx << 4, s.y, cz << 4)) return;
        if ("tree-overlay".equals(s.layout)) return;
        RemasterData d = RemasterData.get(w);
        if (!d.flag(s.id(), "geom:" + cx + ":" + cz)) return;
        List<JsonObject> nodes = RemasterRuntime.nodes(s);
        if (!bossOnly) for (JsonObject n : nodes) {
            int[] at = RemasterRuntime.nodePosition(w, s, n);
            if (at == null) continue;
            int x = at[0], y = at[1], z = at[2];
            String id = RemasterRuntime.string(n, "id", "");
            if (!owned(x, y, z, cx, cz) || !w.blockExists(x, y, z)) continue;
            if (RemasterRuntime.installNode(w, s, n)) d.flag(s.id(), "node:" + id, true);
        }
        int index = 0;
        for (JsonElement e : RemasterRuntime.array(s.plan().metadata, "spawns")) {
            JsonObject spawn = e.getAsJsonObject();
            int i = index++;
            if (bossOnly && !"boss".equals(RemasterRuntime.string(spawn, "role", ""))) continue;
            int x = s.x + RemasterRuntime.integer(spawn, "x", 0), y = s.y + RemasterRuntime.integer(spawn, "y", 0),
                z = s.z + RemasterRuntime.integer(spawn, "z", 0);
            String code = RemasterRuntime.string(spawn, "code", "");
            if (!owned(x, y, z, cx, cz) || !w.blockExists(x, y, z)
                || d.flag(s.id(), "entity:" + i)
                || code.isEmpty()
                || ("compact-prefab".equals(s.layout) && "dr-09".equals(code))
                || "dc-10".equals(code)) continue;
            if (spawn.has("spawn") && !spawn.get("spawn")
                .getAsBoolean()) continue;
            if (d.flag(s.id(), "dead:" + RemasterRuntime.string(spawn, "id", Integer.toString(i)))
                || !RemasterRuntime.spawnReady(w, s, spawn)) continue;
            EntityOldEcho echo = RemasterSpawn.spawn(
                w,
                EchoKind.byCode(code),
                s.id(),
                x + .5,
                y,
                z + .5,
                i,
                "boss".equals(RemasterRuntime.string(spawn, "role", "")));
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

    /** Record the real native tree owner after its original generator admitted this chunk. */
    public static void treeOverlay(World w, int ax, int az, int y0, int cx, int cz) {
        String encounter = com.miaokatze.gtsr.common.dimension.prosperity.encounter.ForgottenLakeEncounterStructure
            .encounterId(w, ax, az);
        RemasterSite site = new RemasterSite(
            "forgotten_lake_court",
            0,
            w.getSeed(),
            ax - TREE_SOURCE_X,
            y0,
            az - TREE_SOURCE_Z,
            "tree-overlay");
        if (w.isRemote || !RemasterRollout.allowsGeneration(site)) return;
        RemasterData data = RemasterData.get(w);
        data.register(site);
        net.minecraft.nbt.NBTTagCompound state = data.state(site.id());
        state.setInteger("legacyAnchorX", ax);
        state.setInteger("legacyAnchorY", y0);
        state.setInteger("legacyAnchorZ", az);
        state.setString("legacyEncounter", encounter);
        data.markDirty();
        if (site.intersects(cx, cz)) data.flag(site.id(), "geom:" + cx + ":" + cz, true);
    }
}
