package com.miaokatze.gtsr.common.dimension.prosperity.portal;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Exact approved voxels; unspecified cells are ignored, the tube cavity is explicitly air. */
public final class SpacetimeGeometry {

    private SpacetimeGeometry() {}

    public static String[][] shape() {
        char[][][] cells = new char[46][9][43];
        for (char[][] level : cells) for (char[] row : level) java.util.Arrays.fill(row, ' ');
        String path = "/assets/gtsr/structures/spacetime/portal-structure.json";
        try (InputStream stream = SpacetimeGeometry.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing " + path);
            JsonObject root = new JsonParser().parse(new InputStreamReader(stream, StandardCharsets.UTF_8))
                .getAsJsonObject();
            if (!"gtsr.spacetime.structure/v1".equals(
                root.get("schema")
                    .getAsString()))
                throw new IllegalStateException("Unexpected portal structure schema");
            Set<String> unique = new HashSet<>();
            int solids = 0, air = 0, controller = 0, energy = 0, maintenance = 0;
            for (JsonElement element : root.getAsJsonArray("cells")) {
                JsonArray cell = element.getAsJsonArray();
                int x = cell.get(0)
                    .getAsInt(),
                    y = cell.get(1)
                        .getAsInt(),
                    z = cell.get(2)
                        .getAsInt();
                if (x < 2 || x >= 45 || y < 0 || y >= 46 || z < 0 || z >= 9 || !unique.add(x + ":" + y + ":" + z))
                    throw new IllegalStateException("Invalid/duplicate portal cell " + cell);
                String kind = cell.get(3)
                    .getAsString();
                char symbol;
                switch (kind) {
                    case "casing":
                        symbol = 'C';
                        break;
                    case "coil":
                        symbol = 'Q';
                        break;
                    case "brass":
                        symbol = 'R';
                        break;
                    case "glass":
                        symbol = 'G';
                        break;
                    case "floor":
                        symbol = 'F';
                        break;
                    case "air":
                        symbol = '-';
                        air++;
                        break;
                    case "energy":
                        symbol = 'C';
                        energy++;
                        requirePosition(x, y, z, 23, 2, 6);
                        break;
                    case "maintenance":
                        symbol = 'C';
                        maintenance++;
                        requirePosition(x, y, z, 23, 2, 3);
                        break;
                    case "controller":
                        symbol = '~';
                        controller++;
                        requirePosition(x, y, z, 23, 2, 8);
                        break;
                    default:
                        throw new IllegalStateException("Unknown portal cell " + kind);
                }
                if (symbol != '-') solids++;
                // StructureLib ABC: left->right, top->bottom, front->back. The approved front is +Z.
                cells[45 - y][8 - z][x - 2] = symbol;
            }
            // The existing art and air totals are unchanged; only runtime semantics differ.
            if (cells[45 - 24][8 - 4][23 - 2] != ' ')
                throw new IllegalStateException("Portal core must remain outside the approved solid art");
            cells[45 - 24][8 - 4][23 - 2] = 'S';
            if (solids != 2703 || air != 1220 || controller != 1 || energy != 1 || maintenance != 1)
                throw new IllegalStateException("Portal voxel contract mismatch");
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot read portal structure", e);
        }
        String[][] shape = new String[46][9];
        for (int y = 0; y < 46; y++) for (int z = 0; z < 9; z++) shape[y][z] = new String(cells[y][z]);
        return shape;
    }

    private static void requirePosition(int x, int y, int z, int a, int b, int c) {
        if (x != a || y != b || z != c) throw new IllegalStateException("Portal functional bay moved");
    }
}
