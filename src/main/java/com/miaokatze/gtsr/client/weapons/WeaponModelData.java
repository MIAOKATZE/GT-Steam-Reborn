// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.client.weapons;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Exact Forge OBJ draw data. Float bits, face order and primitive boundaries are never changed. */
public final class WeaponModelData {

    private static final int MAGIC = 0x4754574D;
    private static final int VERSION = 1;
    private static final int MAX_BYTES = 32 * 1024 * 1024;
    private static final int MAX_VALUES = 1_000_000;
    private static final int MAX_GROUPS = 65_536;
    private static final int MAX_FACES = 1_000_000;

    final float[][] positions;
    final float[][] texCoords;
    final float[][] normals;
    final List<Group> groups;
    private final byte[] sourceSha256;

    static final class Polygon {

        final int normal;
        final int[] positions;
        final int[] texCoords;

        Polygon(int normal, int[] positions, int[] texCoords) {
            this.normal = normal;
            this.positions = positions;
            this.texCoords = texCoords;
        }
    }

    static final class Group {

        final String name;
        final int mode;
        final List<Polygon> faces;

        Group(String name, int mode, List<Polygon> faces) {
            this.name = name;
            this.mode = mode;
            this.faces = Collections.unmodifiableList(faces);
        }
    }

    WeaponModelData(float[][] positions, float[][] texCoords, float[][] normals, List<Group> groups,
        byte[] sourceSha256) throws IOException {
        this.positions = positions;
        this.texCoords = texCoords;
        this.normals = normals;
        this.groups = Collections.unmodifiableList(groups);
        this.sourceSha256 = sourceSha256.clone();
        validate();
    }

    public byte[] sourceSha256() {
        return sourceSha256.clone();
    }

    public int groupCount() {
        return groups.size();
    }

    public int faceCount() {
        int count = 0;
        for (Group group : groups) count += group.faces.size();
        return count;
    }

    public void writeCompiled(OutputStream output) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream payload = new DataOutputStream(buffer);
        writeTable(payload, positions);
        writeTable(payload, texCoords);
        writeTable(payload, normals);
        writeVar(payload, groups.size());
        for (Group group : groups) {
            payload.writeUTF(group.name);
            payload.writeInt(group.mode);
            writeVar(payload, group.faces.size());
            int previousPosition = 0, previousUv = 0, previousNormal = 0;
            for (Polygon face : group.faces) {
                payload.writeByte(face.positions.length | (face.texCoords == null ? 0 : 16));
                writeDelta(payload, face.normal - previousNormal);
                previousNormal = face.normal;
                for (int i = 0; i < face.positions.length; i++) {
                    writeDelta(payload, face.positions[i] - previousPosition);
                    previousPosition = face.positions[i];
                    if (face.texCoords != null) {
                        writeDelta(payload, face.texCoords[i] - previousUv);
                        previousUv = face.texCoords[i];
                    }
                }
            }
        }
        byte[] bytes = buffer.toByteArray();
        if (bytes.length > MAX_BYTES) throw new IOException("Weapon mesh exceeds size limit");
        DataOutputStream out = new DataOutputStream(output);
        out.writeInt(MAGIC);
        out.writeInt(VERSION);
        out.write(sourceSha256);
        out.writeInt(bytes.length);
        out.write(checksum(sourceSha256, bytes));
        out.write(bytes);
    }

    public static WeaponModelData readCompiled(InputStream input) throws IOException {
        DataInputStream in = new DataInputStream(input);
        if (in.readInt() != MAGIC) throw new IOException("Invalid weapon mesh magic");
        if (in.readInt() != VERSION) throw new IOException("Unsupported weapon mesh version");
        byte[] source = new byte[32];
        in.readFully(source);
        int length = in.readInt();
        if (length < 0 || length > MAX_BYTES) throw new IOException("Invalid weapon mesh size");
        byte[] expected = new byte[32];
        in.readFully(expected);
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        if (in.read() != -1) throw new IOException("Trailing weapon mesh bytes");
        if (!MessageDigest.isEqual(expected, checksum(source, bytes)))
            throw new IOException("Weapon mesh checksum mismatch");
        DataInputStream payload = new DataInputStream(new ByteArrayInputStream(bytes));
        float[][] positions = readTable(payload), uv = readTable(payload), normals = readTable(payload);
        int groupCount = count(payload, MAX_GROUPS);
        if (groupCount == 0) throw new IOException("Missing weapon mesh groups");
        List<Group> groups = new ArrayList<>();
        int totalFaces = 0;
        for (int g = 0; g < groupCount; g++) {
            String name = payload.readUTF();
            int mode = payload.readInt();
            int faceCount = count(payload, MAX_FACES - totalFaces);
            totalFaces += faceCount;
            List<Polygon> faces = new ArrayList<>();
            int previousPosition = 0, previousUv = 0, previousNormal = 0;
            for (int f = 0; f < faceCount; f++) {
                int flags = payload.readUnsignedByte(), corners = flags & 15;
                if ((flags & ~31) != 0 || (corners != 3 && corners != 4))
                    throw new IOException("Invalid weapon mesh polygon");
                previousNormal = index(payload, previousNormal, normals.length);
                int[] refs = new int[corners], texRefs = (flags & 16) == 0 ? null : new int[corners];
                for (int i = 0; i < corners; i++) {
                    refs[i] = previousPosition = index(payload, previousPosition, positions.length);
                    if (texRefs != null) texRefs[i] = previousUv = index(payload, previousUv, uv.length);
                }
                faces.add(new Polygon(previousNormal, refs, texRefs));
            }
            groups.add(new Group(name, mode, faces));
        }
        if (payload.read() != -1) throw new IOException("Trailing weapon mesh payload");
        return new WeaponModelData(positions, uv, normals, groups, source);
    }

    private void validate() throws IOException {
        if (sourceSha256.length != 32 || groups.isEmpty() || groups.size() > MAX_GROUPS)
            throw new IOException("Invalid weapon mesh metadata");
        for (float[][] table : Arrays.asList(positions, texCoords, normals)) {
            if (table.length > MAX_VALUES) throw new IOException("Too many weapon mesh values");
            for (float[] value : table) {
                if (value.length != 3) throw new IOException("Invalid weapon mesh tuple");
                for (float number : value) if (Float.isInfinite(number) || Float.isNaN(number))
                    throw new IOException("Nonfinite weapon mesh value");
            }
        }
        int count = 0;
        for (Group group : groups) {
            if (group.name == null || (group.mode != -1 && group.mode != 4 && group.mode != 7)
                || (!group.faces.isEmpty() && group.mode == -1)) throw new IOException("Invalid weapon mesh group");
            count += group.faces.size();
            if (count > MAX_FACES) throw new IOException("Too many weapon mesh faces");
            for (Polygon face : group.faces) {
                int size = face.positions.length;
                if ((size != 3 && size != 4) || group.mode != (size == 3 ? 4 : 7)
                    || face.normal < 0
                    || face.normal >= normals.length
                    || (face.texCoords != null && face.texCoords.length != size))
                    throw new IOException("Invalid weapon mesh face");
                for (int ref : face.positions)
                    if (ref < 0 || ref >= positions.length) throw new IOException("Invalid position reference");
                if (face.texCoords != null) for (int ref : face.texCoords)
                    if (ref < 0 || ref >= texCoords.length) throw new IOException("Invalid texture reference");
            }
        }
    }

    private static void writeTable(DataOutputStream out, float[][] table) throws IOException {
        writeVar(out, table.length);
        // Byte planes retain all 32 bits while keeping adjacent exponent/sign bytes together for ZIP.
        for (int component = 0; component < 3; component++) for (int shift = 24; shift >= 0; shift -= 8)
            for (float[] value : table) out.writeByte(Float.floatToRawIntBits(value[component]) >>> shift);
    }

    private static float[][] readTable(DataInputStream in) throws IOException {
        int size = count(in, MAX_VALUES);
        if ((long) size * 12 > in.available()) throw new IOException("Truncated weapon mesh table");
        float[][] table = new float[size][3];
        for (int component = 0; component < 3; component++) {
            int[] bits = new int[size];
            for (int shift = 24; shift >= 0; shift -= 8)
                for (int i = 0; i < size; i++) bits[i] |= in.readUnsignedByte() << shift;
            for (int i = 0; i < size; i++) table[i][component] = Float.intBitsToFloat(bits[i]);
        }
        return table;
    }

    private static void writeVar(DataOutputStream out, int number) throws IOException {
        while ((number & ~127) != 0) {
            out.writeByte((number & 127) | 128);
            number >>>= 7;
        }
        out.writeByte(number);
    }

    private static int readVar(DataInputStream in) throws IOException {
        int number = 0;
        for (int shift = 0; shift <= 28; shift += 7) {
            int next = in.readUnsignedByte();
            if (shift == 28 && (next & 248) != 0) throw new IOException("Oversized weapon mesh varint");
            number |= (next & 127) << shift;
            if ((next & 128) == 0) {
                if (shift > 0 && next == 0) throw new IOException("Noncanonical weapon mesh varint");
                return number;
            }
        }
        throw new IOException("Unterminated weapon mesh varint");
    }

    private static int count(DataInputStream in, int maximum) throws IOException {
        int count = readVar(in);
        if (count > maximum) throw new IOException("Weapon mesh count exceeds limit");
        return count;
    }

    private static void writeDelta(DataOutputStream out, int delta) throws IOException {
        writeVar(out, (delta << 1) ^ (delta >> 31));
    }

    private static int index(DataInputStream in, int previous, int size) throws IOException {
        int zigzag = readVar(in);
        long index = (long) previous + ((zigzag >>> 1) ^ -(zigzag & 1));
        if (index < 0 || index >= size) throw new IOException("Invalid weapon mesh reference");
        return (int) index;
    }

    private static byte[] checksum(byte[] source, byte[] payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(source);
            return digest.digest(payload);
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
