package com.miaokatze.gtsr.common.machinevisual;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.miaokatze.miaokatzecore.api.animation.AnimationV1;
import com.miaokatze.miaokatzecore.api.animation.Sampler;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;

/** Immutable packaged definitions. Resource packs cannot change the construction shapes. */
public final class MachineVisualDefinition {

    private static final Map<String, MachineVisualDefinition> DEFINITIONS = load();
    public final String id;
    public final long revision;
    public final List<Group> groups;
    public final double minX, minY, minZ, maxX, maxY, maxZ;
    private final String[][] shape;
    private final int[] offset;

    private MachineVisualDefinition(String id, long revision, List<Group> groups, String[][] shape, int[] offset,
        double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        this.id = id;
        this.revision = revision;
        this.groups = Collections.unmodifiableList(new ArrayList<>(groups));
        this.shape = shape;
        this.offset = offset;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    public static MachineVisualDefinition get(String id) {
        MachineVisualDefinition definition = DEFINITIONS.get(id);
        if (definition == null) throw new IllegalArgumentException("Unknown machine visual: " + id);
        return definition;
    }

    public static MachineVisualDefinition effects(String id, double minX, double minY, double minZ, double maxX,
        double maxY, double maxZ) {
        return new MachineVisualDefinition(
            id,
            1,
            Collections.emptyList(),
            new String[0][],
            new int[3],
            minX,
            minY,
            minZ,
            maxX,
            maxY,
            maxZ);
    }

    public String[][] shapeLayers() {
        String[][] copy = new String[shape.length][];
        for (int i = 0; i < shape.length; i++) copy[i] = shape[i].clone();
        return copy;
    }

    public int offset(int axis) {
        return offset[axis];
    }

    public int countToken(char token) {
        int count = 0;
        for (String[] layer : shape)
            for (String row : layer) for (int i = 0; i < row.length(); i++) if (row.charAt(i) == token) count++;
        return count;
    }

    public int size(int axis) {
        return axis == 0 ? shape[0][0].length() : axis == 1 ? shape.length : shape[0].length;
    }

    public static final class Cell {

        public final int x, y, z;
        public final String material;

        Cell(int x, int y, int z, String material) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.material = material;
        }

        public boolean matches(Block block, int meta) {
            return switch (material) {
                case "gear" -> (block == GregTechAPI.sBlockCasings2 && meta == 2)
                    || com.miaokatze.gtsr.common.structure.RebuiltMachineMaterials.gearTier(block, meta) != null;
                case "pipe" -> block == GregTechAPI.sBlockCasings2 && (meta == 12 || meta == 13);
                case "shell" -> (block == GregTechAPI.sBlockCasings1 && meta == 10)
                    || (block == GregTechAPI.sBlockCasings2 && meta == 0)
                    || (block == GregTechAPI.sBlockCasings4 && (meta == 0 || meta == 2));
                case "shaft" -> block == GregTechAPI.sBlockCasings2 && meta >= 12 && meta <= 15;
                case "frame" -> block == GregTechAPI.sBlockFrames
                    && (meta == Materials.Bronze.mMetaItemSubID || meta == Materials.Steel.mMetaItemSubID
                        || meta == Materials.Titanium.mMetaItemSubID
                        || meta == Materials.TungstenSteel.mMetaItemSubID);
                default -> false;
            };
        }
    }

    public static final class Group {

        public final String id, kind, axis;
        public final double travel, pivotX, pivotY, pivotZ;
        public final long periodMs;
        public final List<Cell> members;
        private final AnimationV1.Document curve;

        Group(JsonObject json) {
            id = json.get("id")
                .getAsString();
            kind = json.get("kind")
                .getAsString();
            axis = json.get("axis")
                .getAsString();
            if (!(kind.equals("translate") || kind.equals("rotate_y") || kind.equals("rotate_z"))
                || !(axis.equals("x") || axis.equals("y") || axis.equals("z")))
                throw new IllegalArgumentException("Unsupported machine motion " + id);
            travel = json.get("travel")
                .getAsDouble();
            periodMs = json.get("periodMs")
                .getAsLong();
            if (!Double.isFinite(travel) || Math.abs(travel) > 1 || periodMs < 2 || periodMs > 60000)
                throw new IllegalArgumentException("Machine motion range " + id);
            JsonArray pivot = json.getAsJsonArray("pivot");
            pivotX = pivot.get(0)
                .getAsDouble();
            pivotY = pivot.get(1)
                .getAsDouble();
            pivotZ = pivot.get(2)
                .getAsDouble();
            if (!Double.isFinite(pivotX) || !Double.isFinite(pivotY) || !Double.isFinite(pivotZ))
                throw new IllegalArgumentException("Machine pivot " + id);
            List<Cell> cells = new ArrayList<>();
            java.util.Set<String> unique = new java.util.HashSet<>();
            for (JsonElement value : json.getAsJsonArray("members")) {
                JsonObject cell = value.getAsJsonObject();
                Cell c = new Cell(
                    cell.get("x")
                        .getAsInt(),
                    cell.get("y")
                        .getAsInt(),
                    cell.get("z")
                        .getAsInt(),
                    cell.get("material")
                        .getAsString());
                if (Math.abs(c.x) > 128 || Math.abs(c.y) > 128
                    || Math.abs(c.z) > 128
                    || !Arrays.asList("gear", "pipe", "frame", "shell", "shaft")
                        .contains(c.material)
                    || !unique.add(c.x + ":" + c.y + ":" + c.z))
                    throw new IllegalArgumentException("Machine member " + id);
                cells.add(c);
            }
            if (cells.isEmpty() || cells.size() > 2048)
                throw new IllegalArgumentException("Machine member count " + id);
            members = Collections.unmodifiableList(cells);
            boolean rotate = kind.startsWith("rotate_");
            List<AnimationV1.Key> keys = rotate
                ? Arrays.asList(
                    new AnimationV1.Key(0, 0, AnimationV1.Interpolation.LINEAR),
                    new AnimationV1.Key(periodMs, 360, AnimationV1.Interpolation.LINEAR))
                : Arrays.asList(
                    new AnimationV1.Key(0, 0, AnimationV1.Interpolation.SMOOTHSTEP),
                    new AnimationV1.Key(periodMs / 2, travel, AnimationV1.Interpolation.SMOOTHSTEP),
                    new AnimationV1.Key(periodMs, 0, AnimationV1.Interpolation.LINEAR));
            if (json.has("keyframes")) {
                if (rotate) throw new IllegalArgumentException("Rotation cannot override linear wrap " + id);
                keys = new ArrayList<>();
                long previous = -1;
                for (JsonElement element : json.getAsJsonArray("keyframes")) {
                    JsonArray pair = element.getAsJsonArray();
                    long time = pair.get(0)
                        .getAsLong();
                    double value = pair.get(1)
                        .getAsDouble();
                    if (time <= previous || time < 0
                        || time > periodMs
                        || !Double.isFinite(value)
                        || value < Math.min(0, travel)
                        || value > Math.max(0, travel)) throw new IllegalArgumentException("Machine keyframe " + id);
                    keys.add(new AnimationV1.Key(time, value, AnimationV1.Interpolation.SMOOTHSTEP));
                    previous = time;
                }
                if (keys.size() < 2 || previous != periodMs
                    || json.getAsJsonArray("keyframes")
                        .get(0)
                        .getAsJsonArray()
                        .get(0)
                        .getAsLong() != 0)
                    throw new IllegalArgumentException("Machine keyframe period " + id);
            }
            curve = new AnimationV1.Document(
                "gtsr:" + id,
                0,
                periodMs,
                0,
                Collections.singletonList(
                    new AnimationV1.Track(
                        "motion",
                        id,
                        rotate ? AnimationV1.Property.ROTATION_DEGREES
                            : axis.equals("x") ? AnimationV1.Property.X
                                : axis.equals("y") ? AnimationV1.Property.Y : AnimationV1.Property.Z,
                        keys)),
                Collections.emptyList(),
                Collections.emptySet());
        }

        /** Translation is in blocks; rotation is explicitly converted from Core degrees to radians. */
        public double sample(long decorativeMs) {
            double scalar = Sampler.sample(curve, Math.floorMod(decorativeMs, periodMs))
                .value("motion");
            return kind.startsWith("rotate_") ? Math.toRadians(scalar) : scalar;
        }
    }

    private static Map<String, MachineVisualDefinition> load() {
        try (InputStream stream = MachineVisualDefinition.class
            .getResourceAsStream("/assets/gtsr/machinevisual/motions.json")) {
            if (stream == null) throw new IllegalStateException("Missing packaged machine motions");
            byte[] bytes = com.google.common.io.ByteStreams.toByteArray(stream);
            JsonObject root = new JsonParser()
                .parse(new InputStreamReader(new java.io.ByteArrayInputStream(bytes), StandardCharsets.UTF_8))
                .getAsJsonObject();
            if (root.get("schema")
                .getAsInt() != 1) throw new IllegalArgumentException("Machine motion schema");
            Map<String, MachineVisualDefinition> result = new LinkedHashMap<>();
            for (JsonElement item : root.getAsJsonArray("definitions")) {
                JsonObject json = item.getAsJsonObject();
                long revision = java.nio.ByteBuffer.wrap(
                    MessageDigest.getInstance("SHA-256")
                        .digest(
                            json.toString()
                                .getBytes(StandardCharsets.UTF_8)))
                    .getLong();
                String id = json.get("id")
                    .getAsString();
                JsonArray layers = json.getAsJsonArray("shapeLayers"), offsets = json.getAsJsonArray("offset");
                String[][] shape = new String[layers.size()][];
                int[] offset = { offsets.get(0)
                    .getAsInt(),
                    offsets.get(1)
                        .getAsInt(),
                    offsets.get(2)
                        .getAsInt() };
                int width = -1, depth = -1;
                for (int y = 0; y < shape.length; y++) {
                    JsonArray rows = layers.get(y)
                        .getAsJsonArray();
                    if (depth < 0) depth = rows.size();
                    if (depth != rows.size()) throw new IllegalArgumentException("Machine shape depth " + id);
                    shape[y] = new String[depth];
                    for (int z = 0; z < depth; z++) {
                        shape[y][z] = rows.get(z)
                            .getAsString();
                        if (width < 0) width = shape[y][z].length();
                        if (width != shape[y][z].length())
                            throw new IllegalArgumentException("Machine shape width " + id);
                    }
                }
                List<Group> groups = new ArrayList<>();
                for (JsonElement group : json.getAsJsonArray("groups")) groups.add(new Group(group.getAsJsonObject()));
                if (groups.size() > 8 || result.containsKey(id))
                    throw new IllegalArgumentException("Machine groups " + id);
                result.put(
                    id,
                    new MachineVisualDefinition(
                        id,
                        revision,
                        groups,
                        shape,
                        offset,
                        -offset[0],
                        offset[1] - shape.length + 1,
                        -offset[2],
                        width - offset[0],
                        offset[1] + 2,
                        depth - offset[2]));
            }
            return Collections.unmodifiableMap(result);
        } catch (java.io.IOException | java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException("Unable to read packaged machine motions", error);
        }
    }
}
