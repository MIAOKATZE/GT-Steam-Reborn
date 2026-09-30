package com.miaokatze.gtsr.common.dimension.prosperity.echo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;
import com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile;
import com.miaokatze.gtsr.common.dimension.prosperity.architecture.RoyalArchitecture;
import com.miaokatze.gtsr.common.dimension.prosperity.architecture.RuinsArchitecture;

/** Authored industrial silhouettes and furnished ruins, with local chunk clipping before writes. */
public final class RuinsBlueprint {

    public static final class Node {

        public final int index, x, y, z, tier;
        public final String mob;

        Node(int index, int x, int y, int z, int tier, String mob) {
            this.index = index;
            this.x = x;
            this.y = y;
            this.z = z;
            this.tier = tier;
            this.mob = mob;
        }
    }

    private RuinsBlueprint() {}

    public static List<Node> nodes(RuinSite s) {
        List<Node> n = new ArrayList<>();
        if (s.kind < 7) {
            String[] bosses = { "dc-02", "dc-08", "di-02", "di-05", "di-08", "di-10", "di-13" };
            int c = s.width / 2;
            int by = s.kind == 1 ? -18 : 0;
            n.add(new Node(0, c, by + 1, c, 0, bosses[s.kind]));
            String[] mobs = s.kind == 0 ? new String[] { "dr-03", "dr-10", "dr-11" }
                : s.kind == 1 ? new String[] { "dr-05", "dr-15", "dr-07" } : new String[] { "dr-01", "dr-10", "dr-11" };
            for (int i = 0; i < 6; i++)
                n.add(new Node(i + 1, 8 + i % 3 * 10, by + 1, s.depth - 12 - i / 3 * 8, 0, mobs[i % 3]));
            n.add(new Node(100, c - 5, by + 1, s.depth - 10, s.kind < 2 ? 5 : 3, ""));
            n.add(new Node(101, c + 5, by + 1, s.depth - 10, s.kind < 2 ? 4 : 4, ""));
        } else {
            String[] mobs = { "dr-10", "dr-07", "dr-11", "dr-07", "dr-12", "dr-10", "dr-12", "dr-15", "dr-03", "dr-03",
                "dr-11", "dr-05", "dr-07", "dr-01", "dr-01", "dr-15", "dr-20", "dr-07", "dr-05", "dr-01" };
            n.add(new Node(0, 20, 1, 9, 0, mobs[s.kind - 7]));
            n.add(new Node(100, 25, 1, 11, 1 + (s.kind % 3 == 0 ? 1 : 0), ""));
        }
        return n;
    }

    public static void build(RuinSite s, BlockSink sink, int cx, int cz) {
        Draw d = new Draw(s, sink, cx, cz);
        if (s.kind == 0) foundry(d);
        else if (s.kind == 1) factory(d);
        else if (s.kind < 7) medium(d);
        else small(d);
        detailDecor(d);
        // Restore the public spine after furnishing, then ground every approach at its node level.
        // A service passage cuts only player headroom: upper walls, roofs and gantries remain intact.
        int center = s.width / 2;
        if (s.kind == 1) {
            d.servicePath(center - 1, 20, center + 1, s.depth - 1, -18);
            d.servicePath(43, 20, center + 1, 22, -18);
            d.servicePath(center - 1, 73, 52, 75, -18);
        } else if (s.kind < 7) {
            d.servicePath(center - 1, 0, center + 1, s.depth - 1, 0);
        } else {
            d.servicePath(0, 6, s.width - 1, 8, 0);
        }
        for (Node n : nodes(s)) {
            if (s.kind < 7) {
                d.servicePath(Math.min(center, n.x) - 1, n.z - 1, Math.max(center, n.x) + 1, n.z + 1, n.y - 1);
            } else {
                d.servicePath(n.x - 1, Math.min(7, n.z) - 1, n.x + 1, Math.max(7, n.z) + 1, n.y - 1);
            }
        }
        // Every entity/box anchor owns a floor and adequate headroom; keep loot reachable from the avenue.
        for (Node n : nodes(s)) {
            EchoKind kind = n.mob.isEmpty() ? null : EchoKind.byCode(n.mob);
            int collisionRadius = kind == null ? 1 : Math.max(1, (int) Math.ceil(kind.width / 2F - .5F));
            int r = n.index == 0 && s.kind < 2 ? 11 : collisionRadius;
            int headroom = kind == null ? 3 : Math.max(3, (int) Math.ceil(kind.height));
            // The DC court retains its broad movement space; DI clearance follows real dimensions.
            if (n.index == 0 && s.kind < 2) headroom = 22;
            d.floor(n.x - r, n.z - r, n.x + r, n.z + r, n.y - 1, "mossroot_paving");
            d.box(n.x - r, n.y, n.z - r, n.x + r, n.y + headroom, n.z + r, Blocks.air, 0);
        }
        d.landscape();
    }

    private static void foundry(Draw d) {
        d.avenue(46, 0, 50, 95, 0);
        d.avenue(0, 45, 95, 51, 0);
        // Monumental paired halls frame a court, rather than filling an entire square platform.
        d.hall(7, 15, 30, 70, 0, 22, "furnace_firebrick");
        d.hall(65, 15, 88, 70, 0, 22, "slag_masonry");
        for (int z = 19; z < 70; z += 12) {
            d.furnace(12, z, 0);
            d.tank(80, z, 0, 4, 12);
        }
        for (int x : new int[] { 6, 89 }) for (int z : new int[] { 7, 88 }) {
            d.tower(x, z, 0, 28);
            d.pillar(x, 0, z, 35, "cast_iron_pillar");
        }
        // Broken loading bridge and service gantries stay above the 23-block boss clearance.
        d.catwalk(16, 70, 80, 70, 25);
        d.catwalk(16, 24, 80, 24, 25);
        d.box(22, 26, 68, 30, 26, 72, Blocks.air, 0);
        for (int z = 5; z < 90; z += 3) {
            d.put(37, 0, z, "rust_floor_grate");
            d.put(39, 0, z, "rust_floor_grate");
        }
        d.arch(48, 2, 0, 12, 17);
        d.arch(48, 91, 0, 12, 17);
        d.floor(32, 32, 64, 66, 0, "sootstone_tiles");
        for (int x = 34; x <= 62; x += 7) {
            d.pillar(x, 0, 68, 8, "cast_iron_pillar");
            d.put(x, 8, 68, "amber_lamp");
        }
        for (int x = 5; x <= 80; x += 25) d.shelter(x, 78, 0, 15, 11);
        d.crane(31, 12, 0, 20);
        d.pipe(7, 72, 88, 72, 3);
        d.workshop(10, 74, 0);
        d.archive(73, 78, 0);
    }

    private static void factory(Draw d) {
        // Lower production streets are semi-subterranean; generous ramps connect both surface exits.
        d.floor(8, 8, 87, 87, -19, "rootbound_brick");
        d.box(9, -18, 9, 86, 4, 86, Blocks.air, 0);
        d.wall(8, 8, 87, 87, -18, 2, "rootbound_brick");
        d.avenue(45, 5, 51, 90, -18);
        d.avenue(8, 45, 87, 51, -18);
        for (int x : new int[] { 17, 72 }) for (int z : new int[] { 18, 70 }) {
            d.hall(x - 7, z - 9, x + 7, z + 9, -18, 12, "verdigris_pressed_brick");
            d.tank(x, z, -17, 3, 8);
            d.pipe(x - 5, z - 7, x + 5, z - 7, -13);
            d.put(x, -6, z, "pressure_gauge");
        }
        d.hall(36, 35, 60, 62, -18, 25, "rootwood_archive");
        d.box(37, -17, 36, 59, 9, 61, Blocks.air, 0);
        // Open sunken atrium and root ribs make the silhouette visible from the surface.
        for (int x = 34; x <= 62; x += 7) d.pillar(x, -18, 64, 29, "cast_iron_pillar");
        d.catwalk(12, 76, 84, 76, 4);
        d.catwalk(12, 20, 84, 20, 4);
        d.ramp(44, 1, 0, 20, 1);
        d.ramp(51, 94, 0, 20, -1);
        d.box(42, -17, 21, 48, -14, 36, Blocks.air, 0);
        d.box(49, -17, 58, 55, -14, 74, Blocks.air, 0);
        for (int z = 30; z < 66; z += 5) {
            d.put(11, -18, z, "rust_floor_grate");
            d.put(84, -18, z, "rust_floor_grate");
        }
        d.arch(47, 2, 0, 13, 8);
        d.arch(51, 93, 0, 13, 8);
        d.workshop(11, 82, -18);
        d.archive(69, 81, -18);
        for (int x : new int[] { 6, 88 }) for (int z : new int[] { 6, 88 }) d.tower(x, z, 0, 11);
        d.pipe(6, 6, 90, 6, 3);
        d.pipe(6, 90, 90, 90, 3);
    }

    private static void medium(Draw d) {
        d.avenue(21, 0, 27, 47, 0);
        d.avenue(2, 24, 45, 28, 0);
        d.arch(24, 2, 0, 10, 11);
        switch (d.s.kind) {
            case 2: // Boiler shrine: a pressure vessel amid ribbed buttresses.
                d.floor(10, 10, 38, 38, 0, "furnace_firebrick");
                for (int x : new int[] { 10, 38 }) for (int z : new int[] { 10, 38 }) {
                    d.tower(x, z, 0, 14);
                    d.put(x, 15, z, "silent_bell_base");
                }
                d.tank(24, 35, 0, 6, 14);
                d.pipe(10, 9, 38, 9, 3);
                for (int z = 14; z < 35; z += 5) {
                    d.pillar(9, 0, z, 9, "cast_iron_pillar");
                    d.pillar(39, 0, z, 9, "cast_iron_pillar");
                }
                d.shelter(4, 37, 0, 12, 8);
                d.shelter(32, 37, 0, 12, 8);
                break;
            case 3: // Mill: asymmetric sawtooth shed, looms and loading aisle.
                d.hall(5, 10, 18, 40, 0, 12, "rootwood_archive");
                d.hall(30, 7, 43, 39, 0, 15, "verdigris_pressed_brick");
                for (int z = 14; z < 35; z += 6) {
                    d.loom(11, z, 0);
                    d.loom(36, z, 0);
                }
                d.catwalk(12, 9, 36, 9, 9);
                d.pipe(4, 41, 44, 41, 3);
                break;
            case 4: // Watch: four irregular watchtowers and low cover, no boxed arena.
                d.tower(8, 9, 0, 22);
                d.tower(39, 8, 0, 16);
                d.tower(10, 34, 0, 12);
                d.tower(38, 36, 0, 20);
                d.catwalk(8, 9, 39, 9, 12);
                for (int z = 17; z < 32; z += 7) {
                    d.box(6, 0, z, 14, 2, z + 1, d.mat("slag_masonry"), 0);
                    d.box(32, 0, z, 41, 2, z + 1, d.mat("slag_masonry"), 0);
                }
                d.shelter(15, 40, 0, 17, 6);
                break;
            case 5: // Broken bridge and barracks: off-axis breach leaves side escape lanes.
                d.hall(3, 9, 16, 37, 0, 12, "rust_riveted_plate");
                d.hall(32, 9, 45, 37, 0, 12, "rootbound_brick");
                d.catwalk(10, 8, 38, 8, 6);
                d.catwalk(10, 34, 20, 34, 6);
                d.catwalk(28, 34, 38, 34, 6);
                d.box(17, -2, 31, 30, -1, 36, d.mat("slag_masonry"), 0);
                for (int z = 13; z < 35; z += 7) {
                    d.bunk(7, z, 0);
                    d.bunk(36, z, 0);
                }
                d.arch(24, 43, 0, 12, 8);
                break;
            default: // Clock station: bell belfry, clock face and two station halls.
                d.tower(9, 10, 0, 24);
                d.put(9, 25, 10, "silent_bell_base");
                d.put(9, 22, 7, "pressure_gauge");
                d.hall(3, 23, 15, 39, 0, 11, "verdigris_pressed_brick");
                d.hall(34, 7, 44, 37, 0, 14, "sootstone_tiles");
                for (int x = 17; x < 33; x += 4) {
                    d.pillar(x, 0, 10, 7, "cast_iron_pillar");
                    d.put(x, 7, 10, "suspended_chain");
                }
                d.pipe(3, 42, 45, 42, 2);
                d.archive(35, 30, 0);
                break;
        }
        d.workshop(6, 42, 0);
        d.archive(34, 41, 0);
        for (int x : new int[] { 2, 45 }) for (int z : new int[] { 2, 45 }) {
            d.pillar(x, 0, z, 5, "cast_iron_pillar");
            d.put(x, 5, z, "amber_lamp");
        }
    }

    private static void small(Draw d) {
        d.avenue(0, 6, 31, 8, 0);
        switch (d.s.kind - 7) {
            case 0:
                d.hall(2, 1, 12, 12, 0, 6, "rust_riveted_plate");
                d.put(5, 2, 2, "steam_valve");
                d.catwalk(13, 3, 28, 3, 1);
                d.put(15, 1, 4, "pressure_gauge");
                break;
            case 1:
                d.hall(2, 1, 13, 13, 0, 8, "verdigris_pressed_brick");
                d.tank(21, 4, 0, 3, 7);
                d.pipe(8, 3, 27, 3, 2);
                d.put(17, 1, 11, "steam_valve");
                break;
            case 2:
                d.shelter(1, 1, 0, 12, 12);
                d.shelter(17, 1, 0, 13, 12);
                d.bunk(4, 3, 0);
                d.bunk(19, 3, 0);
                break;
            case 3:
                d.crane(7, 3, 0, 12);
                d.box(12, 0, 2, 26, 0, 5, d.mat("rust_riveted_plate"), 0);
                d.put(15, 1, 3, "suspended_chain");
                break;
            case 4:
                d.arch(8, 4, 0, 11, 7);
                d.floor(3, 1, 28, 13, 0, "sootstone_tiles");
                d.put(8, 1, 4, "silent_bell_base");
                d.put(13, 1, 3, "oath_inscription");
                break;
            case 5:
                d.box(2, -2, 2, 29, -1, 12, d.mat("slag_masonry"), 0);
                d.box(3, 0, 2, 28, 1, 2, d.mat("rootbound_brick"), 0);
                d.shelter(3, 4, 0, 12, 8);
                d.bunk(5, 6, 0);
                d.workshop(16, 4, 0);
                break;
            case 6:
                d.furnace(7, 3, 0);
                d.pillar(12, 0, 4, 12, "sootstone_tiles");
                d.floor(16, 2, 29, 12, 0, "sootstone_tiles");
                for (int x = 18; x < 29; x += 3) d.put(x, 1, 4, "oath_inscription");
                break;
            case 7:
                d.tower(7, 5, 0, 14);
                d.catwalk(6, 4, 25, 4, 6);
                d.pillar(23, 0, 4, 8, "cast_iron_pillar");
                d.put(23, 8, 4, "patina_window");
                break;
            case 8:
                d.floor(1, 4, 29, 10, 0, "rust_floor_grate");
                d.hall(2, 1, 10, 11, 0, 6, "rust_riveted_plate");
                d.box(12, 1, 3, 22, 2, 4, d.mat("boiler_casing"), 0);
                break;
            case 9:
                d.tank(7, 4, 0, 3, 8);
                d.tank(24, 4, 0, 3, 5);
                d.pipe(5, 11, 27, 11, 2);
                for (int x = 7; x < 28; x += 5) d.put(x, 3, 11, "steam_valve");
                break;
            case 10:
                d.hall(2, 1, 16, 13, 0, 8, "rootwood_archive");
                d.archive(4, 3, 0);
                d.archive(11, 3, 0);
                d.put(18, 1, 3, "foundry_nameplate");
                break;
            case 11:
                d.pillar(7, 0, 4, 10, "royal_heartwood");
                d.box(4, 5, 2, 10, 7, 6, Blocks.leaves, 4);
                d.pipe(8, 8, 28, 8, 2);
                d.tank(24, 3, 0, 3, 5);
                break;
            case 12:
                d.catwalk(2, 4, 29, 4, 1);
                d.catwalk(2, 11, 29, 11, 1);
                for (int x = 3; x < 31; x += 4) d.put(x, 1, 1, "mire_reeds");
                d.shelter(3, 6, 0, 12, 6);
                break;
            case 13:
                d.arch(7, 4, 0, 10, 9);
                d.arch(22, 11, 0, 10, 6);
                d.put(3, 1, 12, "oath_inscription");
                d.put(13, 1, 3, "rootbound_brick");
                break;
            case 14:
                d.tower(5, 4, 0, 9);
                d.tower(26, 4, 0, 12);
                d.catwalk(5, 4, 26, 4, 5);
                d.put(7, 10, 4, "amber_lamp");
                d.put(26, 13, 4, "amber_lamp");
                break;
            case 15:
                d.floor(2, 1, 16, 13, -3, "rootbound_brick");
                d.box(3, -2, 2, 15, 1, 12, Blocks.air, 0);
                d.wall(2, 1, 16, 13, -2, 2, "rootbound_brick");
                d.box(3, 2, 2, 15, 2, 12, d.mat("royal_heartwood"), 0);
                d.ramp(12, 14, 0, 4, -1);
                d.put(4, -2, 4, "mire_reeds");
                break;
            case 16:
                d.hall(2, 1, 15, 13, 0, 10, "furnace_firebrick");
                d.tank(8, 4, 0, 3, 6);
                d.arch(22, 5, 0, 9, 6);
                d.put(8, 7, 4, "silent_bell_base");
                break;
            case 17:
                for (int x = 4; x < 30; x += 8) {
                    d.arch(x, 4, 0, 7, 8);
                    d.put(x, 9, 4, "pressure_pipe");
                }
                d.box(13, 9, 3, 18, 9, 5, Blocks.air, 0);
                break;
            case 18:
                d.floor(2, 1, 29, 13, 0, "mossroot_paving");
                d.pillar(8, 0, 4, 3, "cast_iron_pillar");
                d.put(8, 3, 4, "silent_bell_base");
                for (int x = 3; x < 30; x += 4) {
                    d.put(x, 1, 2, "mire_reeds");
                    d.put(x, 1, 12, "royal_crown_fern");
                }
                break;
            default:
                d.shelter(2, 1, 0, 12, 11);
                d.shelter(19, 2, 0, 10, 9);
                d.workshop(3, 10, 0);
                d.pillar(16, 0, 2, 6, "cast_iron_pillar");
                d.put(16, 6, 2, "oath_inscription");
                break;
        }
        // Weathered supply chest alcove: small ruins do not present a boss lock.
        d.floor(23, 9, 28, 13, 0, "mossroot_paving");
        d.box(24, 1, 13, 27, 2, 13, d.mat("rootwood_archive"), 0);
    }

    /** Fine details precede final public passages and entity clearance. No gameplay machine is implied. */
    private static void detailDecor(Draw d) {
        if (d.s.kind == 0) {
            d.roofDetail(7, 15, 30, 70, 0, 22);
            d.roofDetail(65, 15, 88, 70, 0, 22);
            for (int z = 19; z < 70; z += 12) d.chimney(15, z + 4, 19, 10 + z % 4);
            for (int x = 5; x <= 80; x += 25) d.workbenchDetail(x + 3, 80, 0);
        } else if (d.s.kind == 1) {
            for (int x : new int[] { 17, 72 })
                for (int z : new int[] { 18, 70 }) d.roofDetail(x - 7, z - 9, x + 7, z + 9, -18, 12);
            // The central court remains open to sky: only the side sheds acquire lantern ridges.
            d.workbenchDetail(12, 82, -18);
            d.workbenchDetail(73, 82, -18);
        } else if (d.s.kind < 7) {
            switch (d.s.kind) {
                case 3:
                    d.roofDetail(5, 10, 18, 40, 0, 12);
                    d.roofDetail(30, 7, 43, 39, 0, 15);
                    break;
                case 5:
                    d.roofDetail(3, 9, 16, 37, 0, 12);
                    d.roofDetail(32, 9, 45, 37, 0, 12);
                    break;
                case 6:
                    d.roofDetail(3, 23, 15, 39, 0, 11);
                    d.roofDetail(34, 7, 44, 37, 0, 14);
                    break;
                default:
                    d.pipe(6, 12, 14, 12, 6);
                    d.put(6, 5, 12, "steam_valve");
                    break;
            }
            d.workbenchDetail(6, 42, 0);
        } else if (d.s.kind == 25) {
            // Upright open gear, alternating teeth and a rootwood axle; plants grow through its bore.
            for (int x = -5; x <= 5; x++) for (int y = -5; y <= 5; y++) {
                double r = Math.hypot(x, y);
                boolean tooth = Math.abs(x) == 5 && Math.abs(y) <= 1 || Math.abs(y) == 5 && Math.abs(x) <= 1;
                if (r >= 3.15 && r <= 4.6 || tooth)
                    d.put(9 + x, 6 + y, 3, tooth ? "rust_riveted_plate" : "patina_trim");
            }
            d.pillar(9, 0, 3, 3, "royal_oathwood");
            d.put(9, 4, 3, "royal_crown_fern");
            d.pillar(9, 5, 3, 5, "suspended_chain");
            d.shelter(2, 10, 0, 11, 5);
            for (int x : new int[] { 3, 14, 28 }) {
                d.put(x, 1, 12, "boiler_casing");
                d.put(x, 2, 12, "royal_crown_fern");
            }
            d.box(3, 6, 11, 6, 6, 13, Blocks.leaves, 4);
            d.put(12, 2, 3, "foundry_nameplate");
        } else if (d.s.kind == 11) {
            // Cold crucible has a recessed bowl, fractured rear arch and a readable front plaque.
            d.box(5, 1, 1, 11, 1, 5, d.mat("furnace_firebrick"), 0);
            d.wall(5, 1, 11, 5, 2, 1, "boiler_casing");
            d.box(6, 2, 2, 10, 3, 4, Blocks.air, 0);
            d.put(8, 2, 3, "rust_floor_grate");
            d.arch(19, 3, 0, 8, 8);
            d.box(20, 6, 3, 21, 10, 3, Blocks.air, 0);
            d.put(8, 2, 1, "foundry_nameplate");
            d.put(4, 1, 2, "cast_iron_pillar");
            d.put(4, 2, 2, "amber_lamp");
            d.put(12, 1, 2, "cast_iron_pillar");
            d.put(12, 2, 2, "amber_lamp");
            d.put(16, 1, 2, "rootbound_brick");
            d.put(17, 1, 3, "patina_trim");
        } else if (d.s.kind == 10) {
            d.put(20, 12, 3, "steam_valve");
            d.put(19, 12, 3, "pressure_gauge");
            d.pipe(8, 3, 19, 3, 13);
            d.put(20, 4, 3, "riveted_hatch");
            for (int x = 12; x <= 26; x++) d.put(x, 1, 2, "iron_catwalk_fence");
            d.workbenchDetail(3, 11, 0);
        }
    }

    private static final class Draw {

        final RuinSite s;
        final BlockSink sink;
        final int minX, maxX, minZ, maxZ;
        final Set<Integer> authored = new HashSet<>();

        Draw(RuinSite s, BlockSink sink, int cx, int cz) {
            this.s = s;
            this.sink = sink;
            minX = Math.max(0, (cx << 4) - s.x);
            maxX = Math.min(s.width - 1, (cx << 4) + 15 - s.x);
            minZ = Math.max(0, (cz << 4) - s.z);
            maxZ = Math.min(s.depth - 1, (cz << 4) + 15 - s.z);
        }

        Block mat(String id) {
            Block b = id.startsWith("royal_") ? RoyalArchitecture.get(id.substring(6)) : RuinsArchitecture.get(id);
            if (b == null) throw new IllegalArgumentException("Unknown ruins material: " + id);
            return b;
        }

        void put(int x, int y, int z, String id) {
            put(x, y, z, mat(id), 0);
        }

        void put(int x, int y, int z, Block b, int meta) {
            if (x >= minX && x <= maxX && z >= minZ && z <= maxZ && s.y + y > 1 && s.y + y < 255) {
                authored.add((x * 256 + s.y + y) * 256 + z);
                sink.setBlock(s.x + x, s.y + y, s.z + z, b, meta, 2);
            }
        }

        void box(int x0, int y0, int z0, int x1, int y1, int z1, Block b, int meta) {
            for (int x = Math.max(x0, minX); x <= Math.min(x1, maxX); x++)
                for (int z = Math.max(z0, minZ); z <= Math.min(z1, maxZ); z++)
                    for (int y = y0; y <= y1; y++) put(x, y, z, b, meta);
        }

        void floor(int x0, int z0, int x1, int z1, int y, String id) {
            for (int x = Math.max(x0, minX); x <= Math.min(x1, maxX); x++)
                for (int z = Math.max(z0, minZ); z <= Math.min(z1, maxZ); z++) {
                    int ground = ProsperityTerrainProfile.heightAt(s.seed, s.x + x, s.z + z) - s.y;
                    box(x, Math.min(ground, y), z, x, y, z, mat("rootbound_brick"), 0);
                    put(x, y, z, id);
                    if (ground > y) box(x, y + 1, z, x, ground + 2, z, Blocks.air, 0);
                }
        }

        void servicePath(int x0, int z0, int x1, int z1, int y) {
            for (int x = Math.max(x0, minX); x <= Math.min(x1, maxX); x++) {
                for (int z = Math.max(z0, minZ); z <= Math.min(z1, maxZ); z++) {
                    int ground = ProsperityTerrainProfile.heightAt(s.seed, s.x + x, s.z + z) - s.y;
                    if (ground < y) box(x, ground, z, x, y - 1, z, mat("rootbound_brick"), 0);
                    put(x, y, z, "mossroot_paving");
                    box(x, y + 1, z, x, y + 3, z, Blocks.air, 0);
                }
            }
        }

        void avenue(int x0, int z0, int x1, int z1, int y) {
            floor(x0, z0, x1, z1, y, "mossroot_paving");
            box(x0, y + 1, z0, x1, y + 4, z1, Blocks.air, 0);
        }

        void pillar(int x, int y, int z, int h, String id) {
            box(x, y, z, x, y + h - 1, z, mat(id), 0);
        }

        void wall(int x0, int z0, int x1, int z1, int y, int h, String id) {
            box(x0, y, z0, x1, y + h, z0, mat(id), 0);
            box(x0, y, z1, x1, y + h, z1, mat(id), 0);
            box(x0, y, z0, x0, y + h, z1, mat(id), 0);
            box(x1, y, z0, x1, y + h, z1, mat(id), 0);
        }

        void hall(int x0, int z0, int x1, int z1, int y, int h, String id) {
            floor(x0, z0, x1, z1, y, "sootstone_tiles");
            box(x0 + 1, y + 1, z0 + 1, x1 - 1, y + h - 1, z1 - 1, Blocks.air, 0);
            wall(x0, z0, x1, z1, y + 1, h - 1, id);
            for (int z = z0 + 3; z < z1; z += 5) {
                box(x0, y + 3, z, x0, y + 5, z + 2, mat("patina_window"), 0);
                box(x1, y + 3, z, x1, y + 5, z + 2, mat("patina_window"), 0);
                pillar(x0, y, z, h + 1, "cast_iron_pillar");
                pillar(x1, y, z, h + 1, "cast_iron_pillar");
            }
            int center = (x0 + x1) / 2;
            box(center - 1, y + 1, z0, center + 1, y + 4, z0, Blocks.air, 0);
            box(center - 1, y + 1, z1, center + 1, y + 4, z1, Blocks.air, 0);
            int sideCenter = (z0 + z1) / 2;
            box(x0, y + 1, sideCenter - 1, x0, y + 4, sideCenter + 1, Blocks.air, 0);
            box(x1, y + 1, sideCenter - 1, x1, y + 4, sideCenter + 1, Blocks.air, 0);
            box(x0, y + h, z0, x1, y + h, z1, mat("rust_riveted_plate"), 0);
            for (int x = x0; x <= x1; x++) {
                int rise = Math.min(x - x0, x1 - x) / 3;
                box(x, y + h + rise, z0, x, y + h + rise, z1, mat("riveted_plate_slab"), 0);
            }
            put(center, y + 5, z0, "foundry_nameplate");
        }

        void shelter(int x, int z, int y, int w, int dep) {
            floor(x, z, x + w - 1, z + dep - 1, y, "royal_heartwood");
            for (int xx : new int[] { x, x + w - 1 })
                for (int zz : new int[] { z, z + dep - 1 }) pillar(xx, y, zz, 5, "royal_oathwood");
            box(x, y + 5, z, x + w - 1, y + 5, z + dep - 1, mat("royal_heartwood_slab"), 0);
            box(x, y + 1, z + dep - 1, x + w - 1, y + 3, z + dep - 1, mat("rootwood_archive"), 0);
            put(x + w / 2, y + 4, z + dep - 2, "amber_lamp");
        }

        void tower(int x, int z, int y, int h) {
            floor(x - 2, z - 2, x + 2, z + 2, y, "rootbound_brick");
            for (int xx : new int[] { x - 2, x + 2 })
                for (int zz : new int[] { z - 2, z + 2 }) pillar(xx, y, zz, h, "cast_iron_pillar");
            box(x - 3, y + h, z - 3, x + 3, y + h, z + 3, mat("rust_floor_grate"), 0);
            wall(x - 3, z - 3, x + 3, z + 3, y + h + 1, 0, "iron_catwalk_fence");
            put(x, y + h + 1, z, "amber_lamp");
            for (int yy = 0; yy < h; yy++) put(x - 2, y + yy, z, Blocks.ladder, 5);
        }

        void tank(int x, int z, int y, int r, int h) {
            for (int xx = x - r; xx <= x + r; xx++) for (int zz = z - r; zz <= z + r; zz++) {
                double dist = Math.hypot(xx - x, zz - z);
                if (dist <= r + .2) {
                    for (int yy = 0; yy <= h; yy++) {
                        if (dist >= r - 1 || yy == 0 || yy == h)
                            put(xx, y + yy, zz, yy % 4 == 0 ? "patina_trim" : "pressure_reservoir");
                    }
                }
            }
            put(x, y + 2, z - r, "pressure_gauge");
            put(x + 1, y + 1, z - r, "steam_valve");
        }

        void pipe(int x0, int z0, int x1, int z1, int y) {
            for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) put(x, y, z, "pressure_pipe");
        }

        void catwalk(int x0, int z0, int x1, int z1, int y) {
            box(x0, y, z0 - 1, x1, y, z1 + 1, mat("rust_floor_grate"), 0);
            for (int x = x0; x <= x1; x++) {
                put(x, y + 1, z0 - 1, "iron_catwalk_fence");
                put(x, y + 1, z1 + 1, "iron_catwalk_fence");
                if ((x - x0) % 8 == 0) pillar(x, 0, z0, y, "cast_iron_pillar");
            }
        }

        void arch(int x, int z, int y, int span, int h) {
            int r = span / 2;
            pillar(x - r, y, z, h, "rootbound_brick");
            pillar(x + r, y, z, h, "rootbound_brick");
            for (int xx = -r; xx <= r; xx++) {
                int yy = h - (int) (Math.abs(xx) * 1.7);
                put(x + xx, y + yy, z, "patina_trim");
                put(x + xx, y + yy + 1, z, "rust_riveted_plate");
            }
            put(x, y + h + 2, z, "oath_inscription");
        }

        void crane(int x, int z, int y, int h) {
            floor(x - 2, z - 1, x + 2, z + 2, y, "slag_masonry");
            pillar(x, y, z, h, "cast_iron_pillar");
            box(x, y + h, z, x + 15, y + h + 1, z, mat("rust_riveted_plate"), 0);
            pillar(x + 13, y + h - 7, z, 7, "suspended_chain");
            put(x - 1, y + 2, z, "steam_valve");
        }

        void furnace(int x, int z, int y) {
            box(x, y, z, x + 5, y + 7, z + 5, mat("furnace_firebrick"), 0);
            box(x + 1, y + 1, z, x + 4, y + 3, z + 3, Blocks.air, 0);
            box(x + 1, y, z, x + 4, y, z + 2, mat("rust_floor_grate"), 0);
            pillar(x + 3, y + 8, z + 4, 11, "sootstone_tiles");
            put(x + 1, y + 5, z, "riveted_hatch");
            put(x + 4, y + 5, z, "pressure_gauge");
        }

        void loom(int x, int z, int y) {
            for (int xx : new int[] { x, x + 3 })
                for (int zz : new int[] { z, z + 3 }) pillar(xx, y + 1, zz, 3, "royal_heartwood");
            box(x, y + 4, z, x + 3, y + 4, z + 3, mat("rust_riveted_plate"), 0);
            box(x + 1, y + 2, z + 1, x + 2, y + 3, z + 2, mat("patina_window"), 0);
            put(x + 3, y + 1, z, "steam_valve");
        }

        void bunk(int x, int z, int y) {
            box(x, y + 1, z, x + 2, y + 1, z + 1, mat("royal_heartwood_slab"), 0);
            pillar(x, y, z, 3, "royal_oathwood");
            put(x + 3, y + 1, z, "rootwood_archive");
        }

        void workshop(int x, int z, int y) {
            box(x, y, z, x + 4, y, z + 2, mat("sootstone_tiles"), 0);
            for (int xx = x; xx <= x + 3; xx++) put(xx, y + 1, z, "salvage_workbench");
            put(x + 1, y + 2, z, "pressure_gauge");
            put(x + 4, y + 1, z, "boiler_casing");
        }

        void archive(int x, int z, int y) {
            for (int xx = x; xx < x + 5; xx++)
                for (int yy = y + 1; yy < y + 4; yy++) put(xx, yy, z, "rootwood_archive");
            put(x + 2, y + 1, z + 1, "foundry_nameplate");
        }

        void ramp(int x, int z, int y, int steps, int direction) {
            for (int i = 0; i < steps; i++) {
                int yy = y - i;
                box(
                    x - 2,
                    yy,
                    z + i * direction,
                    x + 2,
                    yy,
                    z + i * direction,
                    mat("firebrick_stairs"),
                    direction > 0 ? 3 : 2);
                box(x - 2, yy + 1, z + i * direction, x + 2, yy + 4, z + i * direction, Blocks.air, 0);
            }
        }

        void roofDetail(int x0, int z0, int x1, int z1, int y, int h) {
            int center = (x0 + x1) / 2;
            for (int z = z0 + 4; z < z1 - 2; z += 8) {
                // Raised glazed lanterns punctuate the stepped roof rather than replacing its volume.
                int roof = y + h + (center - x0) / 3;
                box(center - 2, roof + 1, z - 1, center + 2, roof + 1, z + 1, mat("patina_trim"), 0);
                box(center - 1, roof + 2, z - 1, center + 1, roof + 3, z + 1, mat("patina_window"), 0);
                box(center - 2, roof + 4, z - 2, center + 2, roof + 4, z + 2, mat("riveted_plate_slab"), 0);
                for (int x : new int[] { x0, x1 }) {
                    put(x, y + h + 1, z, "riveted_hatch");
                    put(x, y + 2, z, "rootbound_brick");
                    put(x, y + 3, z + 1, Blocks.leaves, 4);
                }
                pipe(x0 + 1, z, x0 + 3, z, y + 7);
                put(x0 + 2, y + 6, z, "pressure_gauge");
            }
            for (int z = z0 + 1; z <= z1; z += 3) {
                put(x0, y + h, z, "patina_trim");
                put(x1, y + h, z, "patina_trim");
            }
        }

        void chimney(int x, int z, int y, int h) {
            // Broad brick base, narrow throat, reinforcing collars, and a chipped asymmetric crown.
            box(x - 1, y, z - 1, x + 1, y + 2, z + 1, mat("furnace_firebrick"), 0);
            pillar(x, y + 3, z, h - 2, "sootstone_tiles");
            for (int yy = y + 4; yy <= y + h; yy += 4) wall(x - 1, z - 1, x + 1, z + 1, yy, 0, "patina_trim");
            put(x - 1, y + h + 1, z, "slag_masonry");
            put(x, y + h + 2, z + 1, "slag_masonry");
            put(x + 1, y + h + 1, z, "riveted_plate_slab");
        }

        void workbenchDetail(int x, int z, int y) {
            put(x, y + 1, z, "salvage_workbench");
            put(x + 1, y + 1, z, "rootwood_archive");
            put(x, y + 2, z, "pressure_gauge");
            put(x + 2, y + 1, z, "riveted_hatch");
            pillar(x + 3, y, z, 3, "cast_iron_pillar");
            put(x + 3, y + 3, z, "amber_lamp");
            pipe(x, z + 1, x + 2, z + 1, y + 3);
        }

        void landscape() {
            for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
                int edge = Math.min(Math.min(x, s.width - 1 - x), Math.min(z, s.depth - 1 - z));
                long hash = (s.seed + x * 73428767L + z * 912931L) ^ s.kind * 347;
                if (edge < 3 && Math.floorMod(hash, 11) == 0) {
                    int ground = ProsperityTerrainProfile.heightAt(s.seed, s.x + x, s.z + z) - s.y;
                    if (authored.contains((x * 256 + s.y + ground + 1) * 256 + z)) continue;
                    if (s.biome == 3) put(x, ground + 1, z, "mire_reeds");
                    else if (s.biome == 1) put(x, ground + 1, z, "royal_crown_fern");
                    else if (s.biome == 0) put(x, ground + 1, z, "royal_oath_flower");
                    else put(x, ground + 1, z, Blocks.deadbush, 0);
                }
            }
        }
    }
}
