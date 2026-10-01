package com.miaokatze.gtsr.common.dimension.prosperity.echo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;
import com.miaokatze.gtsr.common.dimension.framework.structure.PlacementGate;
import com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile;
import com.miaokatze.gtsr.common.dimension.prosperity.architecture.RoyalArchitecture;
import com.miaokatze.gtsr.common.dimension.prosperity.architecture.RuinsArchitecture;

/** Authored industrial silhouettes and furnished ruins, with local chunk clipping before writes. */
public final class RuinsBlueprint {

    public static final class Node {

        public final int index, x, y, z, tier, zone, objectiveIndex;
        public final String mob, role;

        public Node(int index, int x, int y, int z, int tier, String mob) {
            this(index, x, y, z, tier, mob, index == 0 ? "BOSS" : mob.isEmpty() ? "CHEST" : "GUARD", -1, -1);
        }

        public Node(int index, int x, int y, int z, int tier, String mob, String role, int zone, int objectiveIndex) {
            this.index = index;
            this.x = x;
            this.y = y;
            this.z = z;
            this.tier = tier;
            this.mob = mob;
            this.role = role;
            this.zone = zone;
            this.objectiveIndex = objectiveIndex;
        }
    }

    private RuinsBlueprint() {}

    private static final String[] BOSSES = { "dc-02", "dc-08", "di-02", "di-05", "di-08", "di-10", "di-13" };
    private static final String[] GUARDS = { "dr-03", "dr-05", "dr-10", "dr-11", "dr-07", "dr-15", "dr-01" };

    public static String mode(int kind) {
        switch (kind) {
            case 7:
            case 8:
            case 11:
            case 14:
            case 16:
            case 21:
            case 23:
                return "P";
            case 13:
            case 15:
            case 20:
            case 22:
            case 25:
                return "C";
            default:
                return "E";
        }
    }

    /** Coordinates shared by production geometry, interaction nodes and external previews. */
    public static List<Node> nodes(RuinSite s) {
        if (s.layout < 2) return RuinsLegacyBlueprint.nodes(s);
        List<Node> out = new ArrayList<>();
        if (s.kind < 7) {
            int bx = s.width / 2, bz = s.kind < 2 ? 112 : 43;
            int by = s.kind == 1 ? -24 : RuinTerrainLayout.level(s, bx, bz);
            out.add(new Node(0, bx, by + 1, bz, 0, BOSSES[s.kind], "BOSS", -1, -1));
            int zones = s.kind == 5 ? 2 : 3;
            int count = s.kind < 2 ? 4 : s.kind == 3 || s.kind == 5 ? 3 : 2;
            for (int zone = 0; zone < zones; zone++) {
                int[] a = zoneAnchor(s, zone);
                int y = s.kind == 1 ? -24 : RuinTerrainLayout.level(s, a[0], a[1]);
                for (int i = 0; i < count; i++) out.add(
                    new Node(
                        1 + zone * count + i,
                        a[0] - 6 + i * 4,
                        y + 1,
                        a[1] + 5,
                        0,
                        GUARDS[(s.kind + i) % GUARDS.length],
                        "GUARD",
                        zone,
                        -1));
                out.add(new Node(200 + zone, a[0], y + 1, a[1] - 5, 0, "", "CONTROL", zone, zone));
                out.add(new Node(100 + zone, a[0] + 7, y + 1, a[1] - 3, s.kind < 2 ? 4 : 3, "", "CHEST", zone, -1));
            }
            if (s.kind == 3) {
                int[] a = zoneAnchor(s, 2);
                int y = RuinTerrainLayout.level(s, a[0], a[1]);
                out.add(new Node(203, a[0] + 4, y + 1, a[1] - 5, 0, "", "CONTROL", 2, 3));
            }
            out.add(new Node(104, bx + 10, by + 1, bz + 18, s.kind < 2 ? 5 : 4, "", "CHEST", -1, -1));
        } else {
            String mode = mode(s.kind);
            int floor = smallLevel(s);
            if ("C".equals(mode)) {
                for (int i = 0; i < 3; i++) out.add(
                    new Node(i + 1, 8 + i * 7, floor + 1, 21, 0, GUARDS[(s.kind + i) % GUARDS.length], "GUARD", 0, -1));
            } else {
                int count = s.kind == 10 || s.kind == 19 || "P".equals(mode) ? 3 : 2;
                for (int i = 0; i < count; i++) {
                    int x = i == 1 && s.kind == 12 ? 8 : i == 0 ? 7 : i == 1 ? 23 : 15;
                    int z = i == 1 && s.kind == 12 ? 24 : i == 0 ? 8 : i == 1 ? 23 : 27;
                    int y = smallPointLevel(s, i);
                    out.add(
                        new Node(
                            ("P".equals(mode) ? 200 : 300) + i,
                            x,
                            y + 1,
                            z,
                            0,
                            "",
                            "P".equals(mode) ? "CONTROL" : "MEMORY",
                            0,
                            i));
                }
            }
            out.add(new Node(100, 25, floor + 1, 26, s.kind % 3 == 0 ? 2 : 1, "", "CHEST", 0, -1));
        }
        return out;
    }

    private static int smallLevel(RuinSite s) {
        return s.kind == 22 ? RuinTerrainLayout.level(s, 16, 16) - 6 : RuinTerrainLayout.level(s, 16, 16);
    }

    private static int smallPointLevel(RuinSite s, int i) {
        return smallLevel(s) - (i == 1 && s.kind == 17 ? 4 : i == 1 && s.kind == 12 ? 3 : 0);
    }

    private static int[] zoneAnchor(RuinSite s, int zone) {
        if (s.kind < 2) return new int[] { 48 + zone * 96, zone == 1 ? 44 : 72 };
        if (s.kind == 5) return new int[] { zone == 0 ? 18 : 78, 30 };
        return new int[] { 16 + zone * 32, 24 };
    }

    public static void build(RuinSite s, BlockSink sink, int cx, int cz) {
        if (s.layout < 2) {
            RuinsLegacyBlueprint.build(s, sink, cx, cz);
            return;
        }
        Draw d = new Draw(s, sink, cx, cz);
        if (s.kind == 0) foundry(d);
        else if (s.kind == 1) factory(d);
        else if (s.kind < 7) medium(d);
        else small(d);
        // Local node pads never clear a site-wide plane. Each node has a route to the main spine.
        int bx = s.width / 2, bz = s.kind < 2 ? 112 : s.kind < 7 ? 43 : 16;
        int by = s.kind == 1 ? -24 : s.kind < 7 ? RuinTerrainLayout.level(s, bx, bz) : smallLevel(s);
        for (Node n : nodes(s)) {
            boolean boss = "BOSS".equals(n.role);
            EchoKind k = n.mob.isEmpty() ? null : EchoKind.byCode(n.mob);
            int r = boss ? s.kind < 2 ? 18 : 7 : k == null ? 1 : Math.max(1, (int) Math.ceil(k.width / 2));
            int head = boss ? s.kind < 2 ? 28 : 12 : k == null ? 3 : Math.max(3, (int) Math.ceil(k.height));
            d.floor(n.x - r, n.z - r, n.x + r, n.z + r, n.y - 1, "sootstone_tiles");
            d.box(n.x - r, n.y, n.z - r, n.x + r, n.y + head, n.z + r, Blocks.air, 0);
            d.connect(bx, bz, by, n.x, n.z, n.y - 1);
            if ("CONTROL".equals(n.role)) {
                d.put(n.x, n.y - 1, n.z, "boiler_casing");
                d.put(n.x, n.y, n.z + 1, "foundry_nameplate");
            } else if ("MEMORY".equals(n.role)) {
                d.put(n.x - 1, n.y - 1, n.z, "rootwood_archive");
                d.put(n.x - 1, n.y, n.z, "oath_inscription");
            }
        }
        // Routing is complete before the authoritative collision pads are restored.
        for (Node n : nodes(s)) {
            EchoKind k = n.mob.isEmpty() ? null : EchoKind.byCode(n.mob);
            int r = "BOSS".equals(n.role) ? s.kind < 2 ? 18 : 7
                : k == null ? 1 : Math.max(1, (int) Math.ceil(k.width / 2));
            int head = "BOSS".equals(n.role) ? s.kind < 2 ? 28 : 12
                : k == null ? 3 : Math.max(3, (int) Math.ceil(k.height));
            d.floor(n.x - r, n.z - r, n.x + r, n.z + r, n.y - 1, "sootstone_tiles");
            d.box(n.x - r, n.y, n.z - r, n.x + r, n.y + head, n.z + r, Blocks.air, 0);
        }
        // The final collision pads deliberately clear their spaces. Restore the readable objective
        // furniture afterwards, so a control console or memory archive is never erased by that safety pass.
        for (Node n : nodes(s)) {
            if ("CONTROL".equals(n.role)) {
                d.put(n.x, n.y - 1, n.z, "boiler_casing");
                d.put(n.x, n.y, n.z + 1, "foundry_nameplate");
            } else if ("MEMORY".equals(n.role)) {
                d.put(n.x - 1, n.y - 1, n.z, "rootwood_archive");
                d.put(n.x - 1, n.y, n.z, "oath_inscription");
            }
        }
        d.stabilizeRailings();
        d.landscape();
    }

    private static void foundry(Draw d) {
        int court = RuinTerrainLayout.level(d.s, 144, 112);
        // A sixty-block gatehouse and long axial view frame the casting monarchy.
        d.connect(144, 2, RuinTerrainLayout.level(d.s, 144, 2), 144, 112, court);
        d.arch(144, 7, RuinTerrainLayout.level(d.s, 144, 7), 36, 29);
        for (int x : new int[] { 112, 176 }) {
            int y = RuinTerrainLayout.level(d.s, x, 18);
            d.hall(x - 12, 9, x + 12, 30, y, 18, "slag_masonry");
            d.tower(x, 18, y, 33);
            d.archive(x - 8, 24, y);
            d.bunk(x + 4, 24, y);
        }
        for (int zone = 0; zone < 3; zone++) {
            int[] a = zoneAnchor(d.s, zone);
            int y = RuinTerrainLayout.level(d.s, a[0], a[1]);
            d.hall(
                a[0] - 28,
                a[1] - 19,
                a[0] + 28,
                a[1] + 25,
                y,
                26,
                zone == 1 ? "rust_riveted_plate" : "furnace_firebrick");
            for (int x = a[0] - 21; x < a[0] + 24; x += 14) {
                d.furnace(x, a[1] + 9, y);
                d.chimney(x + 3, a[1] + 13, y + 19, 20 + zone * 5);
            }
            d.catwalk(a[0] - 26, a[1] + 3, a[0] + 26, a[1] + 3, y + 15);
            d.stairX(a[0] - 24, a[1] - 12, y, 15, 1);
            d.workshop(a[0] - 23, a[1] - 15, y);
            d.archive(a[0] + 15, a[1] - 15, y);
            d.pipe(a[0] - 25, a[1] - 16, a[0] + 25, a[1] - 16, y + 9);
            d.connect(a[0], a[1], y, 144, 112, court);
        }
        // Crown hall has a deep inhabited ambulatory around the open boss nave.
        d.hall(96, 86, 192, 156, court, 43, "slag_masonry");
        d.box(116, court + 1, 91, 172, court + 80, 151, Blocks.air, 0);
        for (int z = 92; z <= 154; z += 12) for (int x : new int[] { 103, 185 }) {
            d.pillar(x, court, z, 48, "cast_iron_pillar");
            d.box(x - 2, court + 32, z - 1, x + 2, court + 33, z + 1, d.mat("patina_trim"), 0);
        }
        d.catwalk(102, 150, 186, 150, court + 30);
        d.stairX(103, 145, court, 30, 1);
        // The casting nave is open to sky. Portal trusses span between grounded side piers.
        for (int z : new int[] { 96, 120, 144 }) {
            d.pillar(113, court, z, 47, "cast_iron_pillar");
            d.pillar(175, court, z, 47, "cast_iron_pillar");
            d.box(113, court + 45, z, 175, court + 46, z, d.mat("patina_trim"), 0);
            for (int x = 113; x <= 175; x += 4) {
                int rise = Math.min(x - 113, 175 - x) / 4;
                d.box(x, court + 46, z, x, court + 46 + rise, z, d.mat("cast_iron_pillar"), 0);
                d.put(x, court + 47 + rise, z, "rust_riveted_plate");
            }
        }
        for (int x : new int[] { 80, 208 }) {
            int y = RuinTerrainLayout.level(d.s, x, 150);
            d.hall(x - 18, 123, x + 18, 177, y, 17, "rootbound_brick");
            for (int z = 131; z < 171; z += 10) {
                d.workshop(x - 13, z, y);
                d.bunk(x + 8, z, y);
            }
            d.connect(x, 150, y, 144, 150, court);
            d.crane(x, 170, y, 26);
        }
        for (int x : new int[] { 20, 268 }) for (int z : new int[] { 114, 175 }) {
            int y = RuinTerrainLayout.level(d.s, x, z);
            d.tank(x, z, y, 9, 24);
            d.tower(x, z, y, 35);
        }
        d.hall(8, 132, 60, 174, RuinTerrainLayout.level(d.s, 34, 153), 15, "rust_riveted_plate");
        d.hall(228, 132, 279, 174, RuinTerrainLayout.level(d.s, 254, 153), 22, "verdigris_pressed_brick");
        d.pipe(24, 183, 264, 183, RuinTerrainLayout.level(d.s, 144, 183) + 8);
    }

    private static void factory(Draw d) {
        // Excavation follows three chambers and narrow streets; untouched soil separates the vaults.
        int y = -24;
        d.hall(105, 78, 183, 155, y, 39, "rootbound_brick");
        d.box(111, y + 1, 84, 177, 80, 150, Blocks.air, 0); // enormous collapsed daylight court
        d.wall(104, 77, 184, 156, y + 1, 30, "slag_masonry");
        for (int x : new int[] { 107, 181 })
            for (int z = 83; z < 156; z += 12) d.pillar(x, y, z, 41, "cast_iron_pillar");
        d.catwalk(108, 146, 180, 146, -8);
        d.catwalk(108, 88, 180, 88, 8);
        d.stairX(110, 143, y, 16, 1);
        for (int zone = 0; zone < 3; zone++) {
            int[] a = zoneAnchor(d.s, zone);
            d.hall(a[0] - 28, a[1] - 20, a[0] + 28, a[1] + 27, y, 19, "verdigris_pressed_brick");
            d.box(a[0] - 24, y + 1, a[1] - 16, a[0] + 24, y + 17, a[1] + 22, Blocks.air, 0);
            for (int x : new int[] { a[0] - 19, a[0] + 17 }) {
                d.tank(x, a[1] + 15, y, 5, 13);
                d.pipe(x, a[1] - 10, x, a[1] + 12, y + 7);
            }
            d.workshop(a[0] - 22, a[1] - 14, y);
            d.archive(a[0] + 12, a[1] - 14, y);
            d.connect(a[0], a[1], y, 144, 112, y);
            d.hall(a[0] - 10, a[1] + 32, a[0] + 10, a[1] + 55, -8, 10, "rootwood_archive");
            d.stairZ(a[0] + 8, a[1] + 28, y, 16, 1);
        }
        // Two opposite descending promenades remain usable without any progression barrier.
        d.descend(140, 2, RuinTerrainLayout.level(d.s, 140, 2), 140, 84, y);
        d.descend(148, 190, RuinTerrainLayout.level(d.s, 148, 190), 148, 151, y);
        for (int x : new int[] { 24, 264 }) {
            int surface = RuinTerrainLayout.level(d.s, x, 155);
            d.hall(x - 15, 135, x + 15, 178, surface, 16, "rust_riveted_plate");
            d.tank(x, 148, surface, 8, 18);
            d.crane(x - 10, 170, surface, 25);
            d.workshop(x - 9, 140, surface);
            d.bunk(x + 6, 140, surface);
            d.connect(x, 155, surface, 144, 180, RuinTerrainLayout.level(d.s, 144, 180));
        }
        for (int x : new int[] { 94, 194 }) {
            int h = RuinTerrainLayout.level(d.s, x, 90);
            d.tower(x, 90, h, 26);
            d.pillar(x, -24, 90, 24 + h, "cast_iron_pillar");
            d.pipe(x, 90, x, 145, h + 9);
        }
        // Lower manufacturing galleries stretch behind the atrium, with separate tool and sleeping bays.
        for (int x : new int[] { 64, 164 }) {
            d.hall(x, 158, x + 60, 184, y, 15, "rootbound_brick");
            for (int bx = x + 5; bx < x + 56; bx += 12) {
                d.loom(bx, 171, y);
                d.workshop(bx, 180, y);
                d.bunk(bx, 162, y);
            }
            d.catwalk(x + 3, 178, x + 57, 178, -14);
            d.stairX(x + 3, 176, y, 10, 1);
            d.connect(x + 30, 164, y, 144, 148, y);
        }
        // Fractured suspended production decks and ventilation ribs above the sunken streets.
        for (int z : new int[] { 104, 128 }) {
            d.catwalk(110, z, 126, z, -8);
            d.catwalk(162, z, 178, z, -8);
            for (int x : new int[] { 116, 172 }) {
                d.pillar(x, y, z, 16, "cast_iron_pillar");
                d.put(x, -7, z, "amber_lamp");
            }
        }
    }

    private static void medium(Draw d) {
        int center = RuinTerrainLayout.level(d.s, 48, 43);
        d.connect(48, 2, RuinTerrainLayout.level(d.s, 48, 2), 48, 69, RuinTerrainLayout.level(d.s, 48, 69));
        d.arch(48, 5, RuinTerrainLayout.level(d.s, 48, 5), 18, 16);
        for (int zone = 0; zone < (d.s.kind == 5 ? 2 : 3); zone++) {
            int[] a = zoneAnchor(d.s, zone);
            int y = RuinTerrainLayout.level(d.s, a[0], a[1]);
            if (d.s.kind == 4) {
                d.hall(a[0] - 10, 13, a[0] + 10, 34, y, 11, "slag_masonry");
                d.tower(a[0], 16, y, 24 + zone * 6);
                d.archive(a[0] - 7, 30, y);
            } else {
                d.hall(
                    a[0] - 12,
                    9,
                    a[0] + 12,
                    36,
                    y,
                    d.s.kind == 3 ? 20 : 16,
                    d.s.kind == 2 ? "furnace_firebrick"
                        : d.s.kind == 5 ? "rust_riveted_plate" : "verdigris_pressed_brick");
            }
            if (d.s.kind == 2) {
                d.tank(a[0], 16, y, 5, 15);
                d.furnace(a[0] - 9, 28, y);
            }
            if (d.s.kind == 3) for (int z = 12; z < 31; z += 7) {
                d.loom(a[0] - 8, z, y);
                d.loom(a[0] + 4, z, y);
            }
            if (d.s.kind == 5) for (int z = 13; z < 34; z += 7) {
                d.bunk(a[0] - 8, z, y);
                d.bunk(a[0] + 4, z, y);
            }
            if (d.s.kind == 6) {
                d.tower(a[0], 14, y, 19 + zone * 4);
                d.put(a[0], y + 20 + zone * 4, 14, "silent_bell_base");
            }
            d.workshop(a[0] - 8, 31, y);
            d.pipe(a[0] - 10, 11, a[0] + 10, 11, y + 7);
            d.connect(a[0], 24, y, 48, 43, center);
        }
        switch (d.s.kind) {
            case 2:
                d.hall(26, 39, 70, 65, center, 30, "furnace_firebrick");
                d.tank(48, 59, center, 8, 24);
                for (int x : new int[] { 28, 68 })
                    for (int z : new int[] { 40, 62 }) d.pillar(x, center, z, 35, "cast_iron_pillar");
                d.catwalk(28, 62, 68, 62, center + 18);
                d.stairX(28, 58, center, 18, 1);
                break;
            case 3:
                d.hall(8, 42, 88, 65, center, 23, "rootwood_archive");
                for (int x = 14; x < 84; x += 12) {
                    d.loom(x, 57, center);
                    d.chimney(x + 2, 61, center + 23, 6);
                }
                d.catwalk(12, 47, 84, 47, center + 12);
                d.stairX(12, 49, center, 12, 1);
                break;
            case 4:
                d.hall(34, 37, 62, 67, center, 17, "rootbound_brick");
                d.tower(48, 60, center, 39);
                d.catwalk(16, 38, 80, 38, center + 19);
                d.stairX(17, 41, center, 19, 1);
                d.floor(6, 52, 27, 66, center - 5, "rootbound_brick");
                d.wall(6, 52, 27, 66, center - 4, 5, "slag_masonry");
                d.descend(28, 55, center, 20, 55, center - 5);
                break;
            case 5:
                d.catwalk(18, 44, 78, 44, center + 7);
                d.stairX(18, 46, center, 7, 1);
                d.hall(34, 53, 62, 68, center - 6, 6, "slag_masonry");
                d.descend(48, 45, center, 48, 59, center - 6);
                d.archive(36, 65, center - 6);
                d.workshop(53, 65, center - 6);
                break;
            default:
                d.hall(32, 37, 64, 67, center, 28, "sootstone_tiles");
                d.tower(48, 59, center, 37);
                d.put(48, center + 38, 59, "silent_bell_base");
                d.catwalk(12, 39, 84, 39, center + 14);
                d.stairX(12, 41, center, 14, 1);
                for (int x = 5; x < 92; x += 4) {
                    d.put(x, center, 69, "rust_floor_grate");
                    d.put(x, center, 70, "rust_riveted_plate");
                }
                break;
        }
        d.hall(3, 43, 22, 66, RuinTerrainLayout.level(d.s, 12, 55), 9, "rootwood_archive");
        d.hall(73, 44, 92, 66, RuinTerrainLayout.level(d.s, 82, 55), 11, "rootbound_brick");
        d.archive(5, 61, RuinTerrainLayout.level(d.s, 12, 55));
        d.bunk(76, 61, RuinTerrainLayout.level(d.s, 82, 55));
    }

    private static void small(Draw d) {
        int y = smallLevel(d.s);
        d.connect(16, 1, RuinTerrainLayout.level(d.s, 16, 1), 16, 29, y);
        switch (d.s.kind) {
            case 7:
                d.hall(2, 3, 12, 15, y, 8, "rust_riveted_plate");
                d.hall(19, 17, 29, 29, y, 7, "slag_masonry");
                d.tower(25, 7, y, 14);
                d.pipe(7, 13, 26, 13, y + 4);
                d.workshop(4, 5, y);
                d.archive(21, 19, y);
                for (int z : new int[] { 19, 24 }) {
                    d.box(2, y, z, 12, y, z, d.mat("rust_riveted_plate"), 0);
                    d.put(5, y + 1, z, "boiler_casing");
                }
                break;
            case 8:
                d.hall(2, 3, 13, 16, y, 11, "verdigris_pressed_brick");
                d.tank(24, 9, y, 5, 10);
                d.pipe(7, 14, 24, 14, y + 6);
                d.workshop(4, 5, y);
                d.hall(18, 20, 29, 29, y, 6, "rootbound_brick");
                d.floor(3, 20, 12, 29, y - 5, "slag_masonry");
                d.wall(3, 20, 12, 29, y - 4, 5, "rootbound_brick");
                d.descend(14, 20, y, 9, 25, y - 5);
                break;
            case 9:
                d.hall(2, 3, 13, 15, y, 11, "rootbound_brick");
                d.hall(19, 17, 29, 29, y, 7, "rootwood_archive");
                d.box(3, y + 5, 4, 12, y + 5, 14, d.mat("royal_heartwood"), 0);
                d.stairZ(11, 5, y, 5, 1);
                for (int z : new int[] { 5, 11 }) {
                    d.bunk(4, z, y);
                    d.bunk(4, z, y + 5);
                    d.bunk(21, z + 14, y);
                }
                d.workshop(21, 19, y);
                d.furnace(3, 20, y);
                break;
            case 10:
                d.crane(5, 7, y, 19);
                d.catwalk(4, 17, 29, 17, y + 2);
                d.stairZ(27, 19, y, 2, -1);
                d.hall(19, 3, 29, 13, y, 8, "rust_riveted_plate");
                d.tower(22, 6, y, 10);
                d.workshop(21, 5, y);
                d.box(2, y, 21, 12, y + 3, 29, d.mat("rootwood_archive"), 0);
                d.box(4, y + 1, 22, 10, y + 2, 28, Blocks.air, 0);
                break;
            case 11:
                for (int x : new int[] { 3, 13, 23 }) d.furnace(x, 3, y);
                d.arch(16, 18, y, 18, 11);
                d.hall(2, 21, 12, 29, y, 6, "sootstone_tiles");
                d.archive(4, 26, y);
                d.floor(20, 21, 29, 29, y, "furnace_firebrick");
                d.put(24, y + 1, 24, "silent_bell_base");
                break;
            case 12:
                d.hall(2, 3, 13, 16, y, 7, "rootbound_brick");
                d.hall(19, 18, 29, 29, y, 6, "royal_heartwood");
                for (int z : new int[] { 5, 11 }) d.bunk(4, z, y);
                d.archive(20, 19, y);
                d.workshop(4, 13, y);
                d.floor(2, 19, 13, 29, y - 3, "slag_masonry");
                d.wall(2, 19, 13, 29, y - 2, 3, "rootbound_brick");
                d.descend(16, 24, y, 10, 24, y - 3);
                break;
            case 13:
                d.hall(2, 3, 13, 17, y, 9, "sootstone_tiles");
                d.hall(19, 3, 29, 17, y, 11, "furnace_firebrick");
                d.furnace(4, 5, y);
                d.furnace(21, 6, y);
                d.archive(3, 15, y);
                d.workshop(21, 15, y);
                d.pipe(6, 17, 25, 17, y + 5);
                d.shelter(3, 24, y, 10, 6);
                break;
            case 14:
                d.tower(7, 7, y, 19);
                d.hall(19, 18, 29, 29, y, 9, "rootwood_archive");
                d.catwalk(6, 13, 26, 13, y + 8);
                d.stairX(4, 15, y, 8, 1);
                d.pillar(25, y, 13, 12, "cast_iron_pillar");
                d.put(25, y + 12, 13, "patina_window");
                d.workshop(21, 20, y);
                d.archive(21, 26, y);
                break;
            case 15:
                d.hall(2, 3, 12, 15, y, 7, "rust_riveted_plate");
                d.workshop(4, 5, y);
                for (int z : new int[] { 17, 25 }) {
                    d.floor(2, z - 1, 29, z + 1, y, "rust_floor_grate");
                    d.box(3, y + 1, z, 11, y + 1, z, d.mat("boiler_casing"), 0);
                }
                d.hall(20, 3, 29, 14, y - 4, 5, "slag_masonry");
                d.descend(16, 7, y, 23, 7, y - 4);
                d.archive(22, 11, y - 4);
                break;
            case 16:
                for (int x : new int[] { 6, 24 }) {
                    d.tank(x, 8, y, 4, 12);
                    d.tank(x, 23, y, 3, 7);
                }
                d.pipe(6, 15, 24, 15, y + 5);
                d.pipe(6, 8, 6, 23, y + 4);
                d.pipe(24, 8, 24, 23, y + 4);
                d.hall(11, 20, 20, 29, y, 7, "rust_riveted_plate");
                d.workshop(13, 22, y);
                break;
            case 17:
                d.hall(2, 3, 13, 16, y, 10, "rootwood_archive");
                d.archive(4, 5, y);
                d.archive(4, 13, y);
                d.hall(19, 18, 29, 29, y - 4, 5, "rootbound_brick");
                d.descend(15, 18, y, 23, 22, y - 4);
                d.archive(21, 19, y - 4);
                d.workshop(21, 26, y - 4);
                d.shelter(18, 2, y, 11, 10);
                break;
            case 18:
                d.pillar(7, y, 7, 15, "royal_heartwood");
                d.box(3, y + 10, 3, 11, y + 14, 11, Blocks.leaves, 4);
                d.tank(24, 8, y, 4, 9);
                d.pipe(9, 12, 24, 12, y + 5);
                d.catwalk(7, 15, 26, 15, y + 3);
                d.stairX(7, 17, y, 3, 1);
                d.hall(2, 20, 13, 29, y, 7, "rootwood_archive");
                d.workshop(4, 22, y);
                d.shelter(20, 21, y, 10, 8);
                break;
            case 19:
                d.catwalk(3, 8, 27, 8, y + 1);
                d.catwalk(3, 23, 27, 23, y + 1);
                d.catwalkZ(16, 8, 23, y + 1);
                d.hall(2, 2, 12, 12, y, 6, "royal_heartwood");
                d.hall(20, 20, 29, 29, y, 8, "rootwood_archive");
                d.workshop(4, 4, y);
                d.archive(22, 22, y);
                d.bunk(22, 26, y);
                break;
            case 20:
                d.arch(16, 8, y, 22, 16);
                d.arch(16, 21, y, 16, 11);
                d.hall(2, 17, 11, 29, y, 7, "slag_masonry");
                d.bunk(4, 22, y);
                d.archive(4, 19, y);
                d.shelter(20, 20, y, 10, 9);
                d.workshop(22, 22, y);
                break;
            case 21:
                d.tower(6, 7, y, 16);
                d.tower(26, 25, y, 19);
                d.catwalk(6, 15, 26, 15, y + 6);
                d.stairX(6, 17, y, 6, 1);
                d.hall(2, 20, 12, 29, y, 7, "rust_riveted_plate");
                d.hall(20, 2, 29, 12, y, 6, "rootwood_archive");
                d.workshop(4, 22, y);
                d.archive(22, 4, y);
                break;
            case 22:
                d.hall(2, 3, 29, 29, y, 8, "rootbound_brick");
                d.box(6, y + 8, 9, 13, y + 11, 16, Blocks.air, 0);
                d.descend(14, 1, y + 6, 14, 14, y);
                d.descend(18, 31, y + 6, 18, 19, y);
                d.archive(3, 5, y);
                d.archive(22, 5, y);
                d.workshop(4, 25, y);
                d.box(3, y + 1, 12, 8, y + 2, 16, d.mat("rootwood_archive"), 0);
                d.put(6, y + 3, 13, "mire_reeds");
                break;
            case 23:
                d.hall(2, 3, 13, 17, y, 13, "furnace_firebrick");
                d.tank(7, 8, y, 4, 10);
                d.put(7, y + 11, 8, "silent_bell_base");
                d.hall(19, 17, 29, 29, y, 9, "sootstone_tiles");
                d.arch(24, 21, y, 8, 8);
                for (int x = 20; x < 29; x += 2) d.pillar(x, y, 28, 4 + (x % 3), "pressure_pipe");
                d.pipe(7, 17, 25, 17, y + 6);
                d.archive(21, 19, y);
                break;
            case 24:
                for (int x : new int[] { 4, 14, 24 }) d.arch(x, 8, y, 8, 12);
                d.box(1, y + 12, 7, 29, y + 12, 9, d.mat("rootbound_brick"), 0);
                d.box(12, y + 12, 7, 18, y + 13, 9, Blocks.air, 0);
                d.hall(2, 20, 13, 29, y, 7, "slag_masonry");
                d.hall(20, 19, 29, 29, y, 6, "rootwood_archive");
                d.workshop(4, 22, y);
                d.archive(22, 21, y);
                d.stairZ(26, 12, y, 12, 1);
                break;
            case 25:
                d.hall(2, 3, 12, 13, y, 8, "verdigris_pressed_brick");
                d.workshop(4, 5, y);
                d.tower(24, 8, y, 14);
                for (int x = -6; x <= 6; x++) for (int yy = -6; yy <= 6; yy++) {
                    double r = Math.hypot(x, yy);
                    if (r > 4.4 && r < 6.3) d.put(16 + x, y + 8 + yy, 16, "patina_trim");
                }
                for (int x : new int[] { 10, 22 }) d.pillar(x, y, 16, 8, "cast_iron_pillar");
                d.box(10, y + 8, 16, 22, y + 8, 16, d.mat("royal_oathwood"), 0);
                d.shelter(3, 24, y, 10, 6);
                for (int x = 3; x < 29; x += 4) {
                    d.put(x, y + 1, 28, "boiler_casing");
                    d.put(x, y + 2, 28, "royal_crown_fern");
                }
                break;
            default:
                d.shelter(2, 3, y, 11, 12);
                d.shelter(19, 18, y, 11, 11);
                d.bunk(4, 5, y);
                d.bunk(21, 21, y);
                d.archive(4, 12, y);
                d.workshop(21, 19, y);
                d.tower(25, 6, y, 12);
                d.arch(7, 22, y, 9, 7);
                d.floor(3, 23, 12, 29, y - 3, "rootbound_brick");
                d.wall(3, 23, 12, 29, y - 2, 3, "slag_masonry");
                break;
        }
        if (d.s.kind != 11 && d.s.kind != 14 && d.s.kind != 26) d.shelter(21, 24, y, 9, 6);
        smallSilhouette(d, y);
    }

    private static void smallSilhouette(Draw d, int y) {
        switch (d.s.kind) {
            case 8:
                // An open pumping trestle, exposed waterwheel and overhead pressure line.
                d.box(3, y + 3, 3, 12, y + 16, 16, Blocks.air, 0);
                for (int x : new int[] { 2, 13 })
                    for (int z : new int[] { 3, 16 }) d.pillar(x, y, z, 17, "cast_iron_pillar");
                d.box(2, y + 16, 3, 13, y + 16, 3, d.mat("patina_trim"), 0);
                d.box(2, y + 16, 16, 13, y + 16, 16, d.mat("patina_trim"), 0);
                d.box(2, y + 16, 3, 2, y + 16, 16, d.mat("patina_trim"), 0);
                d.box(13, y + 16, 3, 13, y + 16, 16, d.mat("patina_trim"), 0);
                d.box(2, y + 16, 9, 13, y + 16, 9, d.mat("cast_iron_pillar"), 0);
                d.pipe(3, 7, 12, 7, y + 11);
                d.catwalk(2, 14, 13, 14, y + 6);
                d.stairX(2, 18, y, 6, 1);
                d.wheel(7, y + 8, 17, 5);
                d.put(7, y + 8, 16, "boiler_casing");
                break;
            case 9:
                d.catwalk(2, 2, 13, 2, y + 5);
                d.stairX(3, 17, y, 5, 1);
                for (int x : new int[] { 2, 13 }) d.pillar(x, y, 1, 6, "royal_oathwood");
                d.box(4, y + 7, 3, 11, y + 9, 3, d.mat("patina_window"), 0);
                d.box(20, y + 6, 25, 28, y + 12, 29, Blocks.air, 0); // collapsed sleeping wing exposes bunks
                break;
            case 11:
                d.box(2, y + 1, 21, 12, y + 13, 29, Blocks.air, 0);
                for (int x = 3; x <= 29; x++) for (int z = 3; z <= 29; z++) {
                    double r = Math.hypot(x - 16, z - 16);
                    if (r > 11.4 && r < 13.0 && !(z > 23 && Math.abs(x - 16) < 4))
                        d.box(x, y, z, x, y + 2 + (x + z) % 4, z, d.mat("furnace_firebrick"), 0);
                }
                d.arch(16, 28, y, 16, 9);
                d.archive(3, 24, y);
                break;
            case 12:
                // Low trench revetments and a cloth-like stepped timber canopy, rather than a second warehouse.
                d.box(2, y + 4, 3, 13, y + 12, 16, Blocks.air, 0);
                d.box(19, y + 3, 18, 29, y + 12, 29, Blocks.air, 0);
                for (int x : new int[] { 3, 12 })
                    for (int z : new int[] { 4, 15 }) d.pillar(x, y, z, 5, "royal_oathwood");
                for (int x = 3; x <= 12; x++) d.box(
                    x,
                    y + 5 + Math.min(x - 3, 12 - x) / 2,
                    4,
                    x,
                    y + 5 + Math.min(x - 3, 12 - x) / 2,
                    15,
                    d.mat("royal_heartwood_slab"),
                    0);
                d.wall(19, 18, 29, 29, y + 1, 1, "rootbound_brick");
                d.bunk(21, 20, y);
                d.bunk(21, 25, y);
                break;
            case 14:
                // The measuring chamber opens into a circular instrument platform with a fractured dome.
                d.box(19, y + 4, 18, 29, y + 16, 29, Blocks.air, 0);
                d.wheel(23, y + 7, 23, 6);
                for (int x = 18; x <= 29; x++) for (int z = 18; z <= 29; z++) {
                    double r = Math.hypot(x - 23, z - 23);
                    if (r > 4.7 && r < 6.0) d.put(x, y + 1, z, "patina_trim");
                    if (r < 5.5 && !(z < 23 && x > 23)) {
                        int h = 10 + (int) Math.sqrt(Math.max(0, 30 - r * r));
                        d.box(x, y + h - 1, z, x, y + h, z, d.mat("patina_window"), 0);
                    }
                }
                for (int x : new int[] { 18, 28 }) d.pillar(x, y, 23, 13, "cast_iron_pillar");
                for (int z : new int[] { 18, 28 }) d.pillar(23, y, z, 13, "cast_iron_pillar");
                d.box(18, y + 12, 23, 28, y + 12, 23, d.mat("patina_trim"), 0);
                break;
            case 18:
                // Exposed root ribs weave into a living crown; the collecting floor remains clear.
                for (int dir : new int[] { -1, 1 }) for (int i = 0; i < 8; i++) {
                    d.box(
                        7 + dir * i,
                        y + Math.max(0, 4 - i / 2),
                        7,
                        7 + dir * i,
                        y + Math.max(0, 4 - i / 2) + 1,
                        7,
                        d.mat("royal_oathwood"),
                        0);
                    d.box(
                        7,
                        y + Math.max(0, 4 - i / 2),
                        7 + dir * i,
                        7,
                        y + Math.max(0, 4 - i / 2) + 1,
                        7 + dir * i,
                        d.mat("royal_oathwood"),
                        0);
                }
                d.box(2, y + 12, 2, 12, y + 15, 12, Blocks.leaves, 4);
                d.pipe(12, 19, 28, 19, y + 8);
                break;
            case 19:
                d.box(2, y + 3, 2, 12, y + 12, 12, Blocks.air, 0);
                for (int x : new int[] { 2, 12 })
                    for (int z : new int[] { 2, 12 }) d.pillar(x, y, z, 6, "royal_oathwood");
                d.box(2, y + 6, 2, 12, y + 6, 12, d.mat("royal_heartwood_slab"), 0);
                break;
            case 23:
                d.box(20, y + 6, 18, 28, y + 14, 28, Blocks.air, 0);
                for (int z : new int[] { 19, 25 }) d.arch(24, z, y, 10, 12);
                d.pipe(18, 29, 29, 29, y + 5);
                break;
            case 26:
                // Two sloping expedition tents and open racks; no box-shaped lodging shell.
                d.box(2, y + 1, 3, 13, y + 8, 15, Blocks.air, 0);
                d.box(19, y + 1, 18, 30, y + 8, 29, Blocks.air, 0);
                d.tent(2, 3, y, 11, 12);
                d.tent(19, 18, y, 11, 11);
                d.bunk(4, 6, y);
                d.archive(4, 12, y);
                d.workshop(21, 19, y);
                d.bunk(21, 25, y);
                break;
            default:
                break;
        }
    }

    private static final class Draw {

        final RuinSite s;
        final BlockSink sink;
        final int minX, maxX, minZ, maxZ;
        final Set<Long> authored = new HashSet<>();
        final Set<Long> topSupport = new HashSet<>();
        final Set<Long> railings = new HashSet<>();
        final boolean[][] dry = new boolean[16][16];

        Draw(RuinSite s, BlockSink sink, int cx, int cz) {
            this.s = s;
            this.sink = sink;
            minX = Math.max(0, (cx << 4) - s.x);
            maxX = Math.min(s.width - 1, (cx << 4) + 15 - s.x);
            minZ = Math.max(0, (cz << 4) - s.z);
            maxZ = Math.min(s.depth - 1, (cz << 4) + 15 - s.z);
            for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++)
                dry[x - minX][z - minZ] = PlacementGate.dryPointAt(s.seed, s.x + x, s.z + z);
        }

        Block mat(String id) {
            Block b = id.startsWith("royal_") ? RoyalArchitecture.get(id.substring(6)) : RuinsArchitecture.get(id);
            if (b == null) throw new IllegalArgumentException("Unknown ruins material: " + id);
            return b;
        }

        void put(int x, int y, int z, String id) {
            put(x, y, z, mat(id), "pressure_gauge".equals(id) ? 1 : 0);
        }

        void put(int x, int y, int z, Block b, int meta) {
            if (x >= minX && x <= maxX
                && z >= minZ
                && z <= maxZ
                && s.y + y > 1
                && s.y + y < 255
                && dry[x - minX][z - minZ]) {
                long key = ((long) x * 256 + s.y + y) * 512 + z;
                authored.add(key);
                if (b == RuinsArchitecture.get("iron_catwalk_fence")) railings.add(key);
                else railings.remove(key);
                boolean up = b.isNormalCube() || b == RuinsArchitecture.get("rust_floor_grate") && (meta & 1) == 1
                    || (b == RuinsArchitecture.get("firebrick_slab") || b == RuinsArchitecture.get("riveted_plate_slab")
                        || b == RoyalArchitecture.get("heartwood_slab")
                        || b == RoyalArchitecture.get("oathwood_slab")) && (meta & 1) == 1
                    || b instanceof net.minecraft.block.BlockStairs && (meta & 4) != 0
                    || b instanceof net.minecraft.block.BlockSlab && (meta & 8) != 0;
                if (up) topSupport.add(key);
                else topSupport.remove(key);
                sink.setBlock(s.x + x, s.y + y, s.z + z, b, meta, 2);
            }
        }

        void box(int x0, int y0, int z0, int x1, int y1, int z1, Block b, int meta) {
            for (int x = Math.max(x0, minX); x <= Math.min(x1, maxX); x++)
                for (int z = Math.max(z0, minZ); z <= Math.min(z1, maxZ); z++)
                    for (int y = y0; y <= y1; y++) put(x, y, z, b, meta);
        }

        void floor(int x0, int z0, int x1, int z1, int y, String id) {
            // Thin local deck plus explicit foundation piers, never a filled site-wide plinth.
            Block surface = mat(id);
            int meta = "rust_floor_grate".equals(id) ? 1 : 0;
            for (int x = Math.max(x0, minX); x <= Math.min(x1, maxX); x++)
                for (int z = Math.max(z0, minZ); z <= Math.min(z1, maxZ); z++) {
                    int ground = RuinTerrainLayout.ground(s, x, z);
                    put(x, y - 1, z, mat("rootbound_brick"), 0);
                    put(x, y, z, surface, meta);
                    if (ground > y) box(x, y + 1, z, x, ground + 2, z, Blocks.air, 0);
                    boolean pier = ((x == x0 || x == x1) && (z - z0) % 6 == 0)
                        || ((z == z0 || z == z1) && (x - x0) % 6 == 0);
                    if (pier && ground < y - 1) box(x, ground, z, x, y - 2, z, mat("slag_masonry"), 0);
                    else if (ground < y && y - ground <= 3) box(x, ground, z, x, y - 2, z, mat("rootbound_brick"), 0);
                }
        }

        void connect(int x0, int z0, int y0, int x1, int z1, int y1) {
            // Manhattan streets give predictable stairs and preserve the terrain between routes.
            int dx = Math.abs(x1 - x0), dz = Math.abs(z1 - z0), length = dx + dz;
            if (length == 0) return;
            for (int i = 0; i <= length; i++) {
                int x = i <= dx ? x0 + Integer.signum(x1 - x0) * i : x1;
                int z = i <= dx ? z0 : z0 + Integer.signum(z1 - z0) * (i - dx);
                int y = y0 + (int) Math.round((y1 - y0) * (double) i / length);
                // Clip before the per-column foundation and clearance work.
                if (x + 1 < minX || x - 1 > maxX || z + 1 < minZ || z - 1 > maxZ) continue;
                floor(x - 1, z - 1, x + 1, z + 1, y, "mossroot_paving");
                box(x - 1, y + 1, z - 1, x + 1, y + 3, z + 1, Blocks.air, 0);
            }
        }

        void descend(int x0, int z0, int y0, int x1, int z1, int y1) {
            int length = Math.abs(x1 - x0) + Math.abs(z1 - z0), drop = Math.abs(y1 - y0);
            if (drop <= length) {
                connect(x0, z0, y0, x1, z1, y1);
                return;
            }
            // A steep terrain edge receives a switchback instead of an unwalkable two-block riser.
            int extra = (drop - length + 1) / 2 + 3;
            int sign = Math.max(x0, x1) + extra < s.width - 2 ? 1 : -1;
            int total = length + extra * 2;
            int ya = y0 + (int) Math.round((y1 - y0) * (extra / (double) total));
            int yb = y0 + (int) Math.round((y1 - y0) * ((extra + length) / (double) total));
            connect(x0, z0, y0, x0 + sign * extra, z0, ya);
            connect(x0 + sign * extra, z0, ya, x1 + sign * extra, z1, yb);
            connect(x1 + sign * extra, z1, yb, x1, z1, y1);
        }

        void stairX(int x, int z, int y, int h, int dir) {
            for (int i = 0; i <= h; i++) {
                int xx = x + i * dir;
                floor(xx, z - 1, xx, z + 1, y + i, "sootstone_tiles");
                box(xx, y + i + 1, z - 1, xx, y + i + 4, z + 1, Blocks.air, 0);
                if (i < h) box(xx, y + i, z - 1, xx, y + i, z + 1, mat("firebrick_stairs"), dir > 0 ? 1 : 0);
            }
        }

        void stairZ(int x, int z, int y, int h, int dir) {
            for (int i = 0; i <= h; i++) {
                int zz = z + i * dir;
                floor(x - 1, zz, x + 1, zz, y + i, "sootstone_tiles");
                box(x - 1, y + i + 1, zz, x + 1, y + i + 4, zz, Blocks.air, 0);
                if (i < h) box(x - 1, y + i, zz, x + 1, y + i, zz, mat("firebrick_stairs"), dir > 0 ? 3 : 2);
            }
        }

        void catwalkZ(int x, int z0, int z1, int y) {
            box(x - 1, y, z0, x + 1, y, z1, mat("rust_floor_grate"), 1);
            for (int z = Math.max(z0, minZ); z <= Math.min(z1, maxZ); z++) {
                put(x - 1, y + 1, z, "iron_catwalk_fence");
                put(x + 1, y + 1, z, "iron_catwalk_fence");
                if ((z - z0) % 8 == 0) groundedPillar(x, y, z);
            }
        }

        void groundedPillar(int x, int top, int z) {
            if (x < minX || x > maxX || z < minZ || z > maxZ) return;
            box(
                x,
                s.kind == 1 ? Math.min(-24, RuinTerrainLayout.ground(s, x, z)) : RuinTerrainLayout.ground(s, x, z),
                z,
                x,
                top - 1,
                z,
                mat("cast_iron_pillar"),
                0);
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

        void wheel(int x, int y, int z, int r) {
            for (int xx = Math.max(-r, minX - x); xx <= Math.min(r, maxX - x); xx++) for (int yy = -r; yy <= r; yy++) {
                double radius = Math.hypot(xx, yy);
                if (radius > r - 1.2 && radius < r + .3 || (xx == 0 || yy == 0) && radius < r)
                    put(x + xx, y + yy, z, "cast_iron_pillar");
            }
            groundedPillar(x - r, y, z);
            groundedPillar(x + r, y, z);
            box(x - r, y, z, x + r, y, z, mat("patina_trim"), 0);
        }

        void tent(int x, int z, int y, int width, int dep) {
            for (int xx = x; xx < x + width; xx++) {
                int h = Math.min(xx - x, x + width - 1 - xx) + 1;
                box(xx, y + h, z, xx, y + h, z + dep - 1, mat("royal_heartwood"), 0);
                if (xx == x || xx == x + width - 1) box(xx, y, z, xx, y + 1, z + dep - 1, mat("royal_oathwood"), 0);
            }
            int c = x + width / 2;
            pillar(c, y, z + dep - 1, width / 2 + 1, "royal_oathwood");
            put(c, y + width / 2 + 2, z, "oath_inscription");
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
            for (int x = Math.max(x0, minX); x <= Math.min(x1, maxX); x++) {
                int rise = Math.min(x - x0, x1 - x) / 3;
                box(x, y + h, z0, x, y + h + rise, z1, mat("rust_riveted_plate"), 0);
                box(x, y + h + rise + 1, z0, x, y + h + rise + 1, z1, mat("riveted_plate_slab"), 0);
            }
            for (int xx = x0 + 3; xx < x1 - 2; xx += 6) for (int yy = 7; yy < h - 3; yy += 7) {
                box(xx, y + yy, z0, Math.min(xx + 2, x1 - 1), y + yy + 4, z0, mat("patina_window"), 0);
                box(xx, y + yy, z1, Math.min(xx + 2, x1 - 1), y + yy + 4, z1, mat("patina_window"), 0);
            }
            for (int zz = z0 + 3; zz < z1 - 2; zz += 7) for (int yy = 7; yy < h - 3; yy += 7) {
                box(x0, y + yy, zz, x0, y + yy + 4, Math.min(zz + 2, z1 - 1), mat("patina_window"), 0);
                box(x1, y + yy, zz, x1, y + yy + 4, Math.min(zz + 2, z1 - 1), mat("patina_window"), 0);
            }
            for (int zz = z0 + 5; zz < z1; zz += 12) {
                groundedPillar(x0 - 1, y, zz);
                groundedPillar(x1 + 1, y, zz);
                pillar(x0 - 1, y, zz, h - 1, "slag_masonry");
                pillar(x1 + 1, y, zz, h - 1, "slag_masonry");
                box(x0 - 1, y + h - 1, zz, x1 + 1, y + h - 1, zz, mat("patina_trim"), 0);
                if (zz + 3 < z1) box(x0 + 2, y + h, zz, x0 + 5, y + h + 8, zz + 3, Blocks.air, 0);
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
            box(x - 3, y + h, z - 3, x + 3, y + h, z + 3, mat("rust_floor_grate"), 1);
            wall(x - 3, z - 3, x + 3, z + 3, y + h + 1, 0, "iron_catwalk_fence");
            put(x, y + h + 1, z, "amber_lamp");
            pillar(x - 3, y, z, h + 1, "cast_iron_pillar");
            for (int yy = 1; yy <= h; yy++) put(x - 2, y + yy, z, Blocks.ladder, 5);
        }

        void tank(int x, int z, int y, int r, int h) {
            for (int xx = Math.max(x - r, minX); xx <= Math.min(x + r, maxX); xx++)
                for (int zz = Math.max(z - r, minZ); zz <= Math.min(z + r, maxZ); zz++) {
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
            int axis = x0 == x1 ? 2 : 4;
            for (int x = Math.max(x0, minX); x <= Math.min(x1, maxX); x++)
                for (int z = Math.max(z0, minZ); z <= Math.min(z1, maxZ); z++) {
                    put(x, y, z, mat("pressure_pipe"), axis);
                    if (((x - x0) + (z - z0)) % 8 == 0) {
                        groundedPillar(x, y, z);
                        put(x, y - 1, z, "patina_trim");
                    }
                }
        }

        void catwalk(int x0, int z0, int x1, int z1, int y) {
            box(x0, y, z0 - 1, x1, y, z1 + 1, mat("rust_floor_grate"), 1);
            for (int x = Math.max(x0, minX); x <= Math.min(x1, maxX); x++) {
                put(x, y + 1, z0 - 1, "iron_catwalk_fence");
                put(x, y + 1, z1 + 1, "iron_catwalk_fence");
                if ((x - x0) % 8 == 0) groundedPillar(x, y, z0);
            }
        }

        void arch(int x, int z, int y, int span, int h) {
            int r = span / 2;
            pillar(x - r, y, z, h, "rootbound_brick");
            pillar(x + r, y, z, h, "rootbound_brick");
            for (int xx = -r; xx <= r; xx++) {
                int yy = h - (int) (Math.abs(xx) * (h / (double) (r + 1)));
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
            box(x + 1, y, z, x + 4, y, z + 2, mat("rust_floor_grate"), 1);
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

        void stabilizeRailings() {
            // Later room cuts and graded paths may remove a bridge edge. Keep its remaining rails honest.
            for (long key : new ArrayList<Long>(railings)) {
                long below = key - 512;
                int z = (int) (key % 512), worldY = (int) ((key / 512) % 256), x = (int) (key / (256L * 512));
                boolean natural = !authored.contains(below) && worldY - 1 <= s.y + RuinTerrainLayout.ground(s, x, z);
                if (!topSupport.contains(below) && !natural) put(x, worldY - s.y, z, Blocks.air, 0);
            }
        }

        void landscape() {
            for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
                int edge = Math.min(Math.min(x, s.width - 1 - x), Math.min(z, s.depth - 1 - z));
                long hash = (s.seed + x * 73428767L + z * 912931L) ^ s.kind * 347;
                if (edge < 3 && Math.floorMod(hash, 11) == 0) {
                    int ground = ProsperityTerrainProfile.heightAt(s.seed, s.x + x, s.z + z) - s.y;
                    if (authored.contains(((long) x * 256 + s.y + ground + 1) * 512 + z)) continue;
                    if (s.biome == 3) put(x, ground + 1, z, "mire_reeds");
                    else if (s.biome == 1) put(x, ground + 1, z, "royal_crown_fern");
                    else if (s.biome == 0) put(x, ground + 1, z, "royal_oath_flower");
                    else put(x, ground + 1, z, Blocks.deadbush, 0);
                }
            }
        }
    }
}
