// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.client.encounter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Standalone Java8 contracts. Expected model streams come from an independent source-OBJ oracle. */
public final class RigMeshDataContracts {

    private static int checks;

    private interface Action {

        void run() throws Exception;
    }

    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
        checks++;
    }

    private static void rejected(String name, Action action) throws Exception {
        try {
            action.run();
        } catch (IOException expected) {
            checks++;
            return;
        }
        throw new AssertionError("Accepted invalid input: " + name);
    }

    private static RigMeshData obj(String text) throws IOException {
        return RigMeshData.readObj(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] compiled(RigMeshData mesh) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        mesh.writeCompiled(out);
        return out.toByteArray();
    }

    private static RigMeshData binary(byte[] bytes) throws IOException {
        return RigMeshData.readCompiled(new ByteArrayInputStream(bytes));
    }

    private static String hex(byte[] data) {
        StringBuilder out = new StringBuilder();
        for (byte b : data) out.append(String.format("%02x", b & 255));
        return out.toString();
    }

    private static String resolvedDigest(RigMeshData mesh) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buffer);
        double[] p = mesh.positions(), uv = mesh.texCoords();
        for (Map.Entry<String, int[]> group : mesh.groups()
            .entrySet()) {
            byte[] name = group.getKey()
                .getBytes(StandardCharsets.UTF_8);
            out.writeInt(name.length);
            out.write(name);
            out.writeInt(group.getValue().length / 2);
            int[] corners = group.getValue();
            for (int i = 0; i < corners.length; i += 2) {
                int vi = corners[i] * 3, ti = corners[i + 1] * 2;
                out.writeLong(Double.doubleToRawLongBits(p[vi]));
                out.writeLong(Double.doubleToRawLongBits(p[vi + 1]));
                out.writeLong(Double.doubleToRawLongBits(p[vi + 2]));
                out.writeLong(Double.doubleToRawLongBits(uv[ti]));
                out.writeLong(Double.doubleToRawLongBits(uv[ti + 1]));
            }
        }
        out.close();
        return hex(digest.digest(buffer.toByteArray()));
    }

    private static void exact(double[] actual, double[] expected, String name) {
        check(actual.length == expected.length, name + " length");
        for (int i = 0; i < actual.length; i++)
            check(Double.doubleToRawLongBits(actual[i]) == Double.doubleToRawLongBits(expected[i]), name + " bit " + i);
    }

    private static String fixture() {
        return "# Hand-authored unit square: no production parser provides these expectations\n"
            + "v -0.0 0 0\nv 1 0 0\nv 1 1 0\nv 0 1 0\n"
            + "vt 0 0\nvt 1 0\nvt 1 1\nvt 0 1\n"
            + "vn 0 0 1\nusemtl ignored\ng first\ng second\nf 1/1/9 2/2/9 3/3/9 4/4/9\n"
            + "g first\nf -4/-4 -2/-2 -1/-1 # Negative indices resolve at the face\n";
    }

    private static void fixtures() throws Exception {
        RigMeshData mesh = obj(fixture());
        for (RigMeshData value : new RigMeshData[] { mesh, binary(compiled(mesh)) }) {
            exact(value.positions(), new double[] { -0.0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0 }, "literal positions");
            exact(value.texCoords(), new double[] { 0, 1, 1, 1, 1, 0, 0, 0 }, "literal flipped UV");
            check(
                value.groups()
                    .keySet()
                    .toString()
                    .equals("[second, first]"),
                "First face order, not declaration order");
            check(
                Arrays.equals(
                    value.groups()
                        .get("second"),
                    new int[] { 0, 0, 1, 1, 2, 2, 0, 0, 2, 2, 3, 3 }),
                "Literal quad fan");
            check(
                Arrays.equals(
                    value.groups()
                        .get("first"),
                    new int[] { 0, 0, 2, 2, 3, 3 }),
                "Literal negative-index triangle");
            check(value.triangles() == 3, "Literal triangle count");
            check(
                value.retainedPrimitiveBytes() == 8L * 20 + 4L * 18 + 4L * 2 + 32,
                "Exact retained primitive payload");
        }
        check(Arrays.equals(compiled(mesh), compiled(binary(compiled(mesh)))), "Deterministic re-encode");
        byte[] digest = mesh.sourceSha256();
        digest[0] ^= 1;
        check(!Arrays.equals(digest, mesh.sourceSha256()), "Provenance digest is defensive");
        check(mesh.groups() == mesh.groups(), "Cached group view avoids per-frame map allocation");
        try {
            mesh.groups()
                .clear();
            throw new AssertionError("Mutable group view");
        } catch (UnsupportedOperationException expected) {
            checks++;
        }
        for (String ending : new String[] { "\r", "\r\n" }) check(
            resolvedDigest(obj(fixture().replace("\n", ending))).equals(resolvedDigest(mesh)),
            "OBJ line ending " + ending.length());
        String[] bad = { "", "v 0 0 0\n", fixture().replace("-0.0", "NaN"), fixture().replace("-0.0", "Infinity"),
            fixture().replace("-0.0", "1000000001"), fixture().replace("vt 0 0", "vt 1000001 0"),
            fixture().replace("vt 0 0", "vt 0 NaN"), fixture().replace("1/1/9", "0/1/9"),
            fixture().replace("1/1/9", "9/1/9"), fixture().replace("1/1/9", "-9/1/9"),
            fixture().replace("1/1/9", "1/0/9"), fixture().replace("1/1/9", "1/9/9"),
            fixture().replace("1/1/9", "1//9"), fixture().replace("f 1/1/9 2/2/9 3/3/9 4/4/9", "f 1/1 2/2"),
            fixture().replace("v 1 1 0", "v 2 0 0"), fixture().replace("g second", "g"),
            fixture().replace("g second", "g " + repeat('x', 1025)) };
        for (int i = 0; i < bad.length; i++) {
            final String text = bad[i];
            rejected("OBJ fixture " + i, () -> obj(text));
        }
        rejected("Invalid UTF8", () -> RigMeshData.readObj(new ByteArrayInputStream(new byte[] { (byte) 0xc3, 0x28 })));
        rejected("Line limit", () -> obj("#" + repeat('x', RigMeshData.MAX_LINE_CHARS + 1)));
        rejected("Input byte limit", () -> RigMeshData.readObj(new InputStream() {

            private long left = RigMeshData.MAX_INPUT_BYTES + 1L;
            private int column;

            public int read() {
                if (left-- <= 0) return -1;
                return column++ % 64 == 63 ? '\n' : '#';
            }
        }));
    }

    private static String repeat(char c, int count) {
        char[] text = new char[count];
        Arrays.fill(text, c);
        return new String(text);
    }

    private static void varint(DataOutputStream out, int value) throws IOException {
        do {
            int next = value & 127;
            value >>>= 7;
            out.writeByte(next | (value == 0 ? 0 : 128));
        } while (value != 0);
    }

    /** Independent binary fixture: a literal unit triangle, raw scalars 0 and 1, no Java writer involved. */
    private static byte[] literalBinary(int vertexIndex, boolean degenerate, boolean duplicateGroup)
        throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x4753524d);
        out.writeByte(1);
        out.write(new byte[32]);
        varint(out, 2);
        out.writeLong(0);
        out.writeLong(0x3ff0000000000000L);
        varint(out, 3);
        for (int n : new int[] { 0, 0, 0, 1, 0, 0, 0, 1, 0 }) varint(out, n);
        varint(out, 3);
        for (int n : new int[] { 0, 1, 1, 1, 0, 0 }) varint(out, n);
        varint(out, duplicateGroup ? 2 : 1);
        for (int g = 0; g < (duplicateGroup ? 2 : 1); g++) {
            varint(out, 4);
            out.writeBytes("root");
            varint(out, 1);
            varint(out, 3);
            for (int n : new int[] { vertexIndex, 0, 1, 1, degenerate ? 1 : 2, 2 }) varint(out, n);
        }
        out.close();
        return bytes.toByteArray();
    }

    private static void malformedBinary() throws Exception {
        byte[] good = literalBinary(0, false, false);
        RigMeshData mesh = binary(good);
        exact(mesh.positions(), new double[] { 0, 0, 0, 1, 0, 0, 0, 1, 0 }, "Independent binary position table");
        exact(mesh.texCoords(), new double[] { 0, 1, 1, 1, 0, 0 }, "Independent binary UV table");
        check(mesh.triangles() == 1, "Independent binary triangle");
        for (int i = 0; i < good.length; i++) {
            final byte[] cut = Arrays.copyOf(good, i);
            rejected("Truncation " + i, () -> binary(cut));
        }
        byte[] magic = good.clone();
        magic[0] = 0;
        rejected("Magic", () -> binary(magic));
        byte[] version = good.clone();
        version[4] = 2;
        rejected("Version", () -> binary(version));
        byte[] trailing = Arrays.copyOf(good, good.length + 1);
        rejected("Trailing byte", () -> binary(trailing));
        rejected("Vertex index bounds", () -> binary(literalBinary(3, false, false)));
        rejected("Degenerate triangle", () -> binary(literalBinary(0, true, false)));
        rejected("Duplicate groups", () -> binary(literalBinary(0, false, true)));
        byte[] nan = good.clone();
        Arrays.fill(nan, 38, 46, (byte) 0xff);
        rejected("Nonfinite scalar", () -> binary(nan));
        byte[] noncanonical = new byte[good.length + 1];
        System.arraycopy(good, 0, noncanonical, 0, 37);
        noncanonical[37] = (byte) 0x82;
        noncanonical[38] = 0;
        System.arraycopy(good, 38, noncanonical, 39, good.length - 38);
        rejected("Noncanonical varint", () -> binary(noncanonical));
        byte[] overflow = Arrays.copyOf(good, 43);
        Arrays.fill(overflow, 37, 43, (byte) 0xff);
        rejected("Overflow varint", () -> binary(overflow));
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("<assets/gtsr/liminal> <golden.tsv>");
        fixtures();
        malformedBinary();
        Path root = Paths.get(args[0]);
        Set<String> active = new LinkedHashSet<>();
        for (String line : Files.readAllLines(root.resolve("active-models.txt"), StandardCharsets.UTF_8)) {
            String code = line.trim();
            if (code.isEmpty() || code.startsWith("#")) continue;
            check(code.matches("(?:dr|di|dc|do)-[0-9]{2}"), "Manifest model code: " + code);
            check(active.add(code), "Manifest model is unique: " + code);
        }
        check(!active.isEmpty(), "Manifest has active models");
        Set<String> covered = new LinkedHashSet<>();
        long indexed = 0, oldPrimitive = 0, totalTriangles = 0;
        int models = 0;
        for (String line : Files.readAllLines(Paths.get(args[1]), StandardCharsets.UTF_8)) {
            if (line.trim()
                .isEmpty()) continue;
            String[] fields = line.split("\t");
            String code = fields[0];
            if (!active.contains(code)) continue;
            check(covered.add(code), code + " Independent golden is unique");
            byte[] source = Files.readAllBytes(root.resolve(code + "/models/" + code + ".obj"));
            check(
                hex(
                    MessageDigest.getInstance("SHA-256")
                        .digest(source)).equals(fields[1]),
                code + " Golden source identity");
            RigMeshData original = RigMeshData.readObj(new ByteArrayInputStream(source));
            RigMeshData loaded = binary(compiled(original));
            int faces = Integer.parseInt(fields[7]);
            for (RigMeshData value : new RigMeshData[] { original, loaded }) {
                check(resolvedDigest(value).equals(fields[2]), code + " Independent resolved xyz/UV bit/order digest");
                check(value.triangles() == Integer.parseInt(fields[3]), code + " Triangles");
                check(
                    value.groups()
                        .size() == Integer.parseInt(fields[4]),
                    code + " Group count");
                check(
                    value.positions().length == Integer.parseInt(fields[5]) * 3,
                    code + " Original vertex order retained");
                check(
                    value.texCoords().length == Integer.parseInt(fields[8]) * 2,
                    code + " Independent unique UV raw-bit pairs");
                check(hex(value.sourceSha256()).equals(fields[1]), code + " SHA provenance");
                long payload = 8L * (value.positions().length + value.texCoords().length) + 32 + faces * 4L;
                for (int[] corners : value.groups()
                    .values()) payload += corners.length * 4L;
                check(payload == value.retainedPrimitiveBytes(), code + " Primitive bytes independent arithmetic");
            }
            check(Arrays.equals(compiled(original), compiled(loaded)), code + " Stable binary roundtrip");
            indexed += loaded.retainedPrimitiveBytes();
            oldPrimitive += loaded.triangles() * 3L * 8 * 8;
            totalTriangles += loaded.triangles();
            models++;
            System.out.println(
                "MODEL " + code
                    + " triangles="
                    + loaded.triangles()
                    + " indexedPrimitiveBytes="
                    + loaded.retainedPrimitiveBytes());
        }
        check(covered.equals(active), "Every manifest model has an independent frozen source golden");
        check(models == active.size(), "Exactly the manifest models tested");
        check(indexed < oldPrimitive, "Retained primitive payload actually decreases");
        System.out.println(
            "PASS checks=" + checks
                + " models="
                + models
                + " triangles="
                + totalTriangles
                + " indexedPrimitiveBytes="
                + indexed
                + " oldExpandedPrimitiveBytes="
                + oldPrimitive
                + " scope=primitive_payload_excludes_Java_headers_GL_driver_and_heap_peaks");
    }
}
