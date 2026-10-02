package com.miaokatze.gtsr.common.dimension.prosperity.echo;

import java.util.List;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;
import com.miaokatze.gtsr.common.dimension.framework.structure.ChunkClampedSink;
import com.miaokatze.gtsr.common.dimension.framework.structure.StructureRegistry;
import com.miaokatze.gtsr.common.dimension.prosperity.encounter.ForgottenLakeEncounterRegistry;
import com.miaokatze.gtsr.common.dimension.prosperity.encounter.TileEntitySealedChest;

/** Only a newly generated owner's slice may write geometry and initialize its one-shot nodes. */
public final class RuinsWorldgen {

    private static boolean registered;

    private RuinsWorldgen() {}

    public static void registerVariants() {
        if (registered) return;
        for (int kind = 0; kind < RuinSite.NAMES.length; kind++) {
            final int k = kind;
            final int side = RuinSite.widthFor(kind, 2);
            final int depth = RuinSite.depthFor(kind, 2);
            StructureRegistry.register(
                new StructureRegistry.Entry(
                    "echo_" + RuinSite.NAMES[k],
                    StructureRegistry.Dimension.PROSPERITY,
                    side,
                    depth,
                    new StructureRegistry.Placer() {

                        public void place(BlockSink sink, int x, int y, int z, long seed) {
                            RuinSite s = new RuinSite(seed, k, x, y, z, 0);
                            for (int cx = x >> 4; cx <= (x + side - 1) >> 4; cx++)
                                for (int cz = z >> 4; cz <= (z + depth - 1) >> 4; cz++)
                                    RuinsBlueprint.build(s, sink, cx, cz);
                        }
                    }));
        }
        registered = true;
    }

    public static boolean generate(World w, int cx, int cz) {
        boolean legacy = false;
        for (RuinSite old : RuinsEncounterData.get(w)
            .existingSitesNear((cx << 4) + 8, (cz << 4) + 8, 128)) if (old.intersects(cx, cz)) {
                placeChunk(w, old, cx, cz);
                legacy = true;
            }
        return legacy;
    }

    public static void placeChunk(World w, RuinSite s, int cx, int cz) {
        if (w.isRemote || !s.intersects(cx, cz)) return;
        RuinsEncounterData data = RuinsEncounterData.get(w);
        if (!RuinObjectives.newSiteAllowed(w, s)) return;
        data.registerSite(s);
        String id = s.id(), geom = "geom:" + cx + ":" + cz;
        if (!data.created(id, geom)) {
            final int[] writes = { 0 };
            final BlockSink delegate = new ChunkClampedSink(w, cx, cz);
            RuinsBlueprint.build(s, new BlockSink() {

                public boolean setBlock(int x, int y, int z, Object b, int meta, int flags) {
                    if (delegate.setBlock(x, y, z, b, meta, flags)) {
                        writes[0]++;
                        return true;
                    }
                    return false;
                }
            }, cx, cz);
            if (writes[0] > 0) data.markCreated(id, geom);
        }
        if (!data.created(id, geom)) return;
        List<RuinsBlueprint.Node> nodes = RuinsBlueprint.nodes(s);
        RuinObjectives.initializeOwnedNodes(w, s, nodes, cx, cz);
        for (RuinsBlueprint.Node n : nodes) {
            if ("CONTROL".equals(n.role) || "MEMORY".equals(n.role)) continue;
            int x = s.x + n.x, y = s.y + n.y, z = s.z + n.z;
            if ((x >> 4) != cx || (z >> 4) != cz) continue;
            String node = (n.mob.isEmpty() ? "chest" : "entity") + n.index;
            if (data.created(id, node)) continue;
            if (n.mob.isEmpty()) {
                if (w.getTileEntity(x, y, z) != null) continue;
                if (!w.setBlock(x, y, z, ForgottenLakeEncounterRegistry.sealedChest, 2, 2)) continue;
                TileEntity t = w.getTileEntity(x, y, z);
                if (t instanceof TileEntitySealedChest) {
                    TileEntitySealedChest chest = (TileEntitySealedChest) t;
                    if (s.layout >= 2) RuinObjectives.configureChest(chest, s, n);
                    else if (s.kind >= 7) {
                        chest.initializeClickUnlock(n.tier, id);
                        chest.setStoryRelic(
                            com.miaokatze.gtsr.common.dimension.prosperity.lore.LoreSources.chestRelic(s.kind));
                    } else chest.initialize(n.tier, id, -1);
                    data.markCreated(id, node);
                }
            } else {
                if (data.dead(id, n.index)) {
                    data.markCreated(id, node);
                    continue;
                }
                EntityOldEcho entity = new EntityOldEcho(w);
                entity.initializeEcho(EchoKind.byCode(n.mob), id, x + .5, y, z + .5, false);
                entity.setNodeIndex(n.index);
                entity.configureObjective(s.layout, n.zone, "BOSS".equals(n.role));
                entity.setHomeYaw(180);
                if (w.spawnEntityInWorld(entity)) data.markCreated(id, node);
            }
        }
    }
}
