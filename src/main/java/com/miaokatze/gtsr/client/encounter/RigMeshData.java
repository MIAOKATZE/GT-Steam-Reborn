package com.miaokatze.gtsr.client.encounter;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Indexed, full precision encounter geometry. No game classes are needed by the build-time compiler.
 * Arrays returned by this class are read-only by contract; callers must not modify them.
 */
public final class RigMeshData {

    public static final int MAGIC = 0x4753524d;
    public static final int VERSION = 1;
    public static final int MAX_INPUT_BYTES = 64 * 1024 * 1024;
    public static final int MAX_LINE_CHARS = 1024 * 1024;
    public static final int MAX_GROUPS = 4096;
    public static final int MAX_GROUP_NAME_BYTES = 1024;
    public static final int MAX_POSITIONS = 500000;
    public static final int MAX_TEX_COORDS = 500000;
    public static final int MAX_TRIANGLES = 500000;
    public static final int MAX_FACES = 500000;
    public static final int MAX_FACE_CORNERS = 65536;
    public static final int MAX_SCALARS = 2500000;
    public static final double MAX_COORDINATE = 1e9;
    public static final double MAX_TEX_COORDINATE = 1e6;

    private final double[] positions;
    private final double[] texCoords;
    private final LinkedHashMap<String, int[]> groups;
    private final Map<String, int[]> groupView;
    // Original polygon lengths let the compiler write polygons without retaining a second corner array.
    private final LinkedHashMap<String, int[]> faceSizes;
    private final byte[] sourceSha256;
    private final int triangles;

    private RigMeshData(double[] positions, double[] texCoords, LinkedHashMap<String, int[]> groups,
        LinkedHashMap<String, int[]> faceSizes, byte[] sourceSha256, int triangles) {
        this.positions = positions;
        this.texCoords = texCoords;
        this.groups = groups;
        this.groupView = Collections.unmodifiableMap(groups);
        this.faceSizes = faceSizes;
        this.sourceSha256 = sourceSha256;
        this.triangles = triangles;
    }

    public double[] positions() {
        return positions;
    }

    /** Texture V has already undergone the original loader's {@code 1 - v} operation. */
    public double[] texCoords() {
        return texCoords;
    }

    /** First face occurrence determines group order; each triangle occupies six alternating v/t indices. */
    public Map<String, int[]> groups() {
        return groupView;
    }

    public int triangles() {
        return triangles;
    }

    public byte[] sourceSha256() {
        return sourceSha256.clone();
    }

    /** Retained primitive payload, including polygon boundaries and the provenance digest, excluding object headers. */
    public long retainedPrimitiveBytes() {
        long bytes = 8L * (positions.length + texCoords.length) + sourceSha256.length;
        for (int[] indices : groups.values()) bytes += 4L * indices.length;
        for (int[] sizes : faceSizes.values()) bytes += 4L * sizes.length;
        return bytes;
    }

    /** Reads the original loader's v/vt/g/f subset, including negative indices and fan triangulation. */
    public static RigMeshData readObj(InputStream input) throws IOException {
        MessageDigest digest = sha256();
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(
                new DigestInputStream(new LimitedInputStream(input), digest),
                StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)));
        Doubles positions = new Doubles(MAX_POSITIONS * 3), texCoords = new Doubles(MAX_TEX_COORDS * 2);
        LinkedHashMap<String, GroupBuilder> groups = new LinkedHashMap<>();
        String group = "root", line;
        int lineNumber = 0, triangles = 0, faces = 0;
        while ((line = readLine(reader)) != null) {
            lineNumber++;
            int comment = line.indexOf('#');
            if (comment >= 0) line = line.substring(0, comment);
            line = line.trim();
            if (line.isEmpty()) continue;
            String[] words = line.split("\\s+");
            try {
                if ("v".equals(words[0])) {
                    require(words.length >= 4, "Vertex needs three coordinates");
                    for (int i = 1; i <= 3; i++) positions.add(number(words[i], MAX_COORDINATE));
                } else if ("vt".equals(words[0])) {
                    require(words.length >= 3, "Texture vertex needs two coordinates");
                    texCoords.add(number(words[1], MAX_TEX_COORDINATE));
                    double v = 1 - number(words[2], MAX_TEX_COORDINATE);
                    require(Double.isFinite(v) && Math.abs(v) <= MAX_TEX_COORDINATE, "Texture coordinate out of range");
                    texCoords.add(v);
                } else if ("g".equals(words[0])) {
                    require(words.length >= 2, "Missing group name");
                    validateGroupName(words[1]);
                    group = words[1];
                } else if ("f".equals(words[0])) {
                    int corners = words.length - 1;
                    require(corners >= 3 && corners <= MAX_FACE_CORNERS, "Polygon corner count out of range");
                    require(faces < MAX_FACES, "Too many faces");
                    require(triangles <= MAX_TRIANGLES - (corners - 2), "Too many triangles");
                    GroupBuilder out = groups.get(group);
                    if (out == null) {
                        require(groups.size() < MAX_GROUPS, "Too many groups");
                        out = new GroupBuilder();
                        groups.put(group, out);
                    }
                    int[] polygon = new int[corners * 2];
                    for (int i = 0; i < corners; i++) {
                        String[] pair = words[i + 1].split("/", -1);
                        require(pair.length >= 2 && !pair[1].isEmpty(), "Face without UV");
                        polygon[i * 2] = index(pair[0], positions.size / 3);
                        polygon[i * 2 + 1] = index(pair[1], texCoords.size / 2);
                    }
                    addPolygon(out, polygon, corners, positions.values);
                    triangles += corners - 2;
                    faces++;
                }
            } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
                throw new IOException("Invalid OBJ at line " + lineNumber, e);
            } catch (IOException e) {
                throw new IOException("Invalid OBJ at line " + lineNumber + ": " + e.getMessage(), e);
            }
        }
        require(triangles > 0, "Empty OBJ");
        return finish(
            positions.toArray(),
            compactTexCoords(texCoords.toArray(), groups),
            groups,
            digest.digest(),
            triangles);
    }

    /** Reads a bounded GSRM stream, validating indices, geometry, counts and the complete stream before publication. */
    public static RigMeshData readCompiled(InputStream input) throws IOException {
        DataInputStream in = new DataInputStream(new LimitedInputStream(input));
        require(in.readInt() == MAGIC, "Invalid rig mesh magic");
        require(in.readUnsignedByte() == VERSION, "Unsupported rig mesh version");
        byte[] digest = new byte[32];
        in.readFully(digest);
        int scalarCount = readVarInt(in, MAX_SCALARS);
        require(scalarCount > 0, "Empty scalar dictionary");
        double[] scalars = new double[scalarCount];
        for (int i = 0; i < scalars.length; i++) {
            scalars[i] = Double.longBitsToDouble(in.readLong());
            require(Double.isFinite(scalars[i]), "Nonfinite scalar");
        }
        int positionCount = readVarInt(in, MAX_POSITIONS);
        require(positionCount > 0, "Empty position table");
        double[] positions = readCoordinates(in, scalars, positionCount * 3, MAX_COORDINATE);
        int texCount = readVarInt(in, MAX_TEX_COORDS);
        require(texCount > 0, "Empty texture coordinate table");
        double[] texCoords = readCoordinates(in, scalars, texCount * 2, MAX_TEX_COORDINATE);
        int groupCount = readVarInt(in, MAX_GROUPS);
        require(groupCount > 0, "Empty group table");
        LinkedHashMap<String, GroupBuilder> groups = new LinkedHashMap<>();
        int triangles = 0, faces = 0;
        for (int g = 0; g < groupCount; g++) {
            int nameLength = readVarInt(in, MAX_GROUP_NAME_BYTES);
            require(nameLength > 0, "Empty group name");
            byte[] name = new byte[nameLength];
            in.readFully(name);
            String group = decodeName(name);
            validateGroupName(group);
            require(!groups.containsKey(group), "Duplicate group " + group);
            int faceCount = readVarInt(in, MAX_FACES);
            require(faceCount > 0 && faces <= MAX_FACES - faceCount, "Face count out of range");
            GroupBuilder out = new GroupBuilder();
            groups.put(group, out);
            for (int f = 0; f < faceCount; f++) {
                int corners = readVarInt(in, MAX_FACE_CORNERS);
                require(corners >= 3, "Polygon has fewer than three corners");
                require(triangles <= MAX_TRIANGLES - (corners - 2), "Too many triangles");
                int[] polygon = new int[corners * 2];
                for (int i = 0; i < corners; i++) {
                    polygon[i * 2] = readVarInt(in, positionCount - 1);
                    polygon[i * 2 + 1] = readVarInt(in, texCount - 1);
                }
                addPolygon(out, polygon, corners, positions);
                triangles += corners - 2;
            }
            faces += faceCount;
        }
        require(triangles > 0, "Empty compiled mesh");
        require(in.read() == -1, "Trailing rig mesh data");
        return finish(positions, texCoords, groups, digest, triangles);
    }

    /** Writes full double raw bits and original polygons; this method does not close the caller's stream. */
    public void writeCompiled(OutputStream output) throws IOException {
        LinkedHashMap<Long, Integer> dictionary = new LinkedHashMap<>();
        for (double value : positions) scalar(dictionary, value);
        for (double value : texCoords) scalar(dictionary, value);
        DataOutputStream out = new DataOutputStream(output);
        out.writeInt(MAGIC);
        out.writeByte(VERSION);
        out.write(sourceSha256);
        writeVarInt(out, dictionary.size());
        for (long bits : dictionary.keySet()) out.writeLong(bits);
        writeVarInt(out, positions.length / 3);
        for (double value : positions) writeVarInt(out, dictionary.get(Double.doubleToRawLongBits(value)));
        writeVarInt(out, texCoords.length / 2);
        for (double value : texCoords) writeVarInt(out, dictionary.get(Double.doubleToRawLongBits(value)));
        writeVarInt(out, groups.size());
        for (Map.Entry<String, int[]> group : groups.entrySet()) {
            byte[] name = group.getKey()
                .getBytes(StandardCharsets.UTF_8);
            writeVarInt(out, name.length);
            out.write(name);
            int[] sizes = faceSizes.get(group.getKey()), indices = group.getValue();
            writeVarInt(out, sizes.length);
            int offset = 0;
            for (int corners : sizes) {
                writeVarInt(out, corners);
                // First triangle gives corners 0 and 1; every fan triangle supplies the next corner.
                writeCorner(out, indices, offset);
                writeCorner(out, indices, offset + 2);
                for (int t = 0; t < corners - 2; t++) writeCorner(out, indices, offset + t * 6 + 4);
                offset += (corners - 2) * 6;
            }
        }
        out.flush();
    }

    private static void scalar(LinkedHashMap<Long, Integer> dictionary, double value) throws IOException {
        long bits = Double.doubleToRawLongBits(value);
        if (!dictionary.containsKey(bits)) {
            require(dictionary.size() < MAX_SCALARS, "Too many distinct scalars");
            dictionary.put(bits, dictionary.size());
        }
    }

    private static void writeCorner(DataOutputStream out, int[] indices, int offset) throws IOException {
        writeVarInt(out, indices[offset]);
        writeVarInt(out, indices[offset + 1]);
    }

    private static double[] readCoordinates(DataInputStream in, double[] scalars, int length, double bound)
        throws IOException {
        double[] result = new double[length];
        for (int i = 0; i < length; i++) {
            double value = scalars[readVarInt(in, scalars.length - 1)];
            require(Math.abs(value) <= bound, "Coordinate out of range");
            result[i] = value;
        }
        return result;
    }

    private static double[] compactTexCoords(double[] original, LinkedHashMap<String, GroupBuilder> groups)
        throws IOException {
        // Resolve OBJ negative indices against the complete source table first; compact only after parsing.
        LinkedHashMap<TexCoordKey, Integer> unique = new LinkedHashMap<>();
        int[] remap = new int[original.length / 2];
        Doubles compact = new Doubles(MAX_TEX_COORDS * 2);
        for (int i = 0; i < remap.length; i++) {
            TexCoordKey key = new TexCoordKey(original[i * 2], original[i * 2 + 1]);
            Integer index = unique.get(key);
            if (index == null) {
                index = unique.size();
                unique.put(key, index);
                compact.add(original[i * 2]);
                compact.add(original[i * 2 + 1]);
            }
            remap[i] = index;
        }
        for (GroupBuilder group : groups.values())
            for (int i = 1; i < group.indices.size; i += 2) group.indices.values[i] = remap[group.indices.values[i]];
        return compact.toArray();
    }

    private static RigMeshData finish(double[] positions, double[] texCoords,
        LinkedHashMap<String, GroupBuilder> builders, byte[] digest, int triangles) {
        LinkedHashMap<String, int[]> groups = new LinkedHashMap<>(), sizes = new LinkedHashMap<>();
        for (Map.Entry<String, GroupBuilder> group : builders.entrySet()) {
            groups.put(group.getKey(), group.getValue().indices.toArray());
            sizes.put(group.getKey(), group.getValue().sizes.toArray());
        }
        return new RigMeshData(positions, texCoords, groups, sizes, digest, triangles);
    }

    private static void addPolygon(GroupBuilder group, int[] polygon, int corners, double[] positions)
        throws IOException {
        for (int i = 1; i < corners - 1; i++) {
            validateTriangle(positions, polygon[0], polygon[i * 2], polygon[(i + 1) * 2]);
            group.indices.add(polygon[0]);
            group.indices.add(polygon[1]);
            group.indices.add(polygon[i * 2]);
            group.indices.add(polygon[i * 2 + 1]);
            group.indices.add(polygon[(i + 1) * 2]);
            group.indices.add(polygon[(i + 1) * 2 + 1]);
        }
        group.sizes.add(corners);
    }

    private static void validateTriangle(double[] p, int a, int b, int c) throws IOException {
        a *= 3;
        b *= 3;
        c *= 3;
        double ax = p[b] - p[a], ay = p[b + 1] - p[a + 1], az = p[b + 2] - p[a + 2];
        double bx = p[c] - p[a], by = p[c + 1] - p[a + 1], bz = p[c + 2] - p[a + 2];
        double nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
        require(Double.isFinite(length) && length >= 1e-12, "Degenerate triangle");
    }

    private static int index(String text, int count) throws IOException {
        int value = Integer.parseInt(text);
        require(value != 0, "Zero OBJ index");
        long result = value > 0 ? (long) value - 1 : (long) count + value;
        require(result >= 0 && result < count, "OBJ index out of range");
        return (int) result;
    }

    private static double number(String text, double bound) throws IOException {
        double value = Double.parseDouble(text);
        require(Double.isFinite(value) && Math.abs(value) <= bound, "Coordinate out of range");
        return value;
    }

    private static String readLine(BufferedReader reader) throws IOException {
        StringBuilder line = new StringBuilder();
        int c;
        while ((c = reader.read()) != -1 && c != '\n' && c != '\r') {
            require(line.length() < MAX_LINE_CHARS, "OBJ line too long");
            line.append((char) c);
        }
        if (c == '\r') {
            reader.mark(1);
            if (reader.read() != '\n') reader.reset();
        }
        if (c == -1 && line.length() == 0) return null;
        return line.toString();
    }

    private static void validateGroupName(String name) throws IOException {
        require(
            !name.isEmpty() && name.getBytes(StandardCharsets.UTF_8).length <= MAX_GROUP_NAME_BYTES,
            "Group name length out of range");
        for (int i = 0; i < name.length(); i++) require(
            !Character.isWhitespace(name.charAt(i)) && !Character.isISOControl(name.charAt(i)),
            "Invalid group name");
    }

    private static String decodeName(byte[] bytes) throws IOException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
        } catch (CharacterCodingException e) {
            throw new IOException("Invalid UTF-8 group name", e);
        }
    }

    private static int readVarInt(DataInputStream in, int maximum) throws IOException {
        int result = 0;
        for (int shift = 0; shift <= 28; shift += 7) {
            int b = in.readUnsignedByte();
            require(shift != 28 || (b & 0xf8) == 0, "Varint overflow");
            result |= (b & 127) << shift;
            if ((b & 128) == 0) {
                require(shift == 0 || (b & 127) != 0, "Noncanonical varint");
                require(result >= 0 && result <= maximum, "Varint out of range");
                return result;
            }
        }
        throw new IOException("Varint overflow");
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        require(value >= 0, "Negative unsigned varint");
        while ((value & ~127) != 0) {
            out.writeByte((value & 127) | 128);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Missing JDK SHA-256", e);
        }
    }

    private static void require(boolean condition, String message) throws IOException {
        if (!condition) throw new IOException(message);
    }

    private static final class GroupBuilder {

        final Ints indices = new Ints(MAX_TRIANGLES * 6);
        final Ints sizes = new Ints(MAX_FACES);
    }

    private static final class TexCoordKey {

        private final long u, v;

        TexCoordKey(double u, double v) {
            this.u = Double.doubleToRawLongBits(u);
            this.v = Double.doubleToRawLongBits(v);
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof TexCoordKey)) return false;
            TexCoordKey key = (TexCoordKey) other;
            return u == key.u && v == key.v;
        }

        @Override
        public int hashCode() {
            return 31 * (int) (u ^ (u >>> 32)) + (int) (v ^ (v >>> 32));
        }
    }

    private static final class Ints {

        private int[] values = new int[16];
        private int size;
        private final int limit;

        Ints(int limit) {
            this.limit = limit;
        }

        void add(int value) throws IOException {
            require(size < limit, "Index array exceeds limit");
            if (size == values.length) values = Arrays.copyOf(values, Math.min(limit, values.length * 2));
            values[size++] = value;
        }

        int[] toArray() {
            return Arrays.copyOf(values, size);
        }
    }

    private static final class Doubles {

        private double[] values = new double[16];
        private int size;
        private final int limit;

        Doubles(int limit) {
            this.limit = limit;
        }

        void add(double value) throws IOException {
            require(size < limit, "Coordinate array exceeds limit");
            if (size == values.length) values = Arrays.copyOf(values, Math.min(limit, values.length * 2));
            values[size++] = value;
        }

        double[] toArray() {
            return Arrays.copyOf(values, size);
        }
    }

    private static final class LimitedInputStream extends FilterInputStream {

        private long count;

        LimitedInputStream(InputStream input) {
            super(input);
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) increment(1);
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int read = in.read(bytes, offset, (int) Math.min(length, MAX_INPUT_BYTES - count + 1));
            if (read > 0) increment(read);
            return read;
        }

        private void increment(int amount) throws IOException {
            count += amount;
            require(count <= MAX_INPUT_BYTES, "Rig mesh input exceeds limit");
        }
    }
}
