package com.miaokatze.gtsr.client.weapons;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraftforge.client.model.obj.Face;
import net.minecraftforge.client.model.obj.GroupObject;
import net.minecraftforge.client.model.obj.TextureCoordinate;
import net.minecraftforge.client.model.obj.Vertex;
import net.minecraftforge.client.model.obj.WavefrontObject;

/** Rehydrates Forge's own faces and groups; all part selection and face drawing stay in Forge. */
public final class ForgeWeaponModel extends WavefrontObject {

    public ForgeWeaponModel(WeaponModelData data) {
        this(data, "compiled weapon");
    }

    public ForgeWeaponModel(WeaponModelData data, String sourceName) {
        // Forge's private currentGroupObject must keep the original last drawing mode, including for VBO mixins.
        super(sourceName, seed(data));
        groupObjects.clear();
        vertices.clear();
        textureCoordinates.clear();
        vertexNormals.clear();
        for (float[] value : data.positions) vertices.add(new Vertex(value[0], value[1], value[2]));
        for (float[] value : data.texCoords)
            textureCoordinates.add(new TextureCoordinate(value[0], value[1], value[2]));
        List<Vertex> normals = new ArrayList<>();
        for (float[] value : data.normals) normals.add(new Vertex(value[0], value[1], value[2]));
        for (WeaponModelData.Group source : data.groups) {
            GroupObject group = new GroupObject(source.name, source.mode);
            for (WeaponModelData.Polygon polygon : source.faces) {
                Face face = new Face();
                face.faceNormal = normals.get(polygon.normal);
                face.vertices = new Vertex[polygon.positions.length];
                if (polygon.texCoords != null)
                    face.textureCoordinates = new TextureCoordinate[polygon.texCoords.length];
                for (int i = 0; i < face.vertices.length; i++) {
                    face.vertices[i] = vertices.get(polygon.positions[i]);
                    if (face.textureCoordinates != null)
                        face.textureCoordinates[i] = textureCoordinates.get(polygon.texCoords[i]);
                }
                group.faces.add(face);
            }
            groupObjects.add(group);
        }
    }

    private static ByteArrayInputStream seed(WeaponModelData data) {
        int mode = data.groups.get(data.groups.size() - 1).mode;
        String obj = "g compiled\n";
        if (mode != -1) {
            obj += "v 0 0 0\nv 1 0 0\nv 0 1 0\n";
            obj += mode == 4 ? "f 1 2 3\n" : "v 1 1 0\nf 1 2 3 4\n";
        }
        return new ByteArrayInputStream(obj.getBytes(StandardCharsets.US_ASCII));
    }

    /** Called by the build compiler on a model parsed by the actual Forge WavefrontObject. */
    public static WeaponModelData capture(WavefrontObject model, byte[] sourceSha256) throws IOException {
        Table positions = new Table(), uv = new Table(), normals = new Table();
        List<WeaponModelData.Group> groups = new ArrayList<>();
        for (GroupObject group : model.groupObjects) {
            if (group == null) throw new IOException("Forge OBJ has no group");
            List<WeaponModelData.Polygon> faces = new ArrayList<>();
            for (Face face : group.faces) {
                if (face.faceNormal == null) throw new IOException("Forge OBJ has no parsed face normal");
                int normal = normals.id(face.faceNormal);
                int[] refs = new int[face.vertices.length];
                int[] texRefs = face.textureCoordinates == null ? null : new int[face.textureCoordinates.length];
                for (int i = 0; i < refs.length; i++) {
                    refs[i] = positions.id(face.vertices[i]);
                    if (texRefs != null) {
                        TextureCoordinate t = face.textureCoordinates[i];
                        texRefs[i] = uv.id(t.u, t.v, t.w);
                    }
                }
                faces.add(new WeaponModelData.Polygon(normal, refs, texRefs));
            }
            groups.add(new WeaponModelData.Group(group.name, group.glDrawingMode, faces));
        }
        return new WeaponModelData(positions.values(), uv.values(), normals.values(), groups, sourceSha256);
    }

    private static final class Table {

        private final Map<Key, Integer> indices = new LinkedHashMap<>();
        private final List<float[]> values = new ArrayList<>();

        int id(Vertex value) {
            return id(value.x, value.y, value.z);
        }

        int id(float x, float y, float z) {
            Key key = new Key(x, y, z);
            Integer index = indices.get(key);
            if (index == null) {
                index = values.size();
                indices.put(key, index);
                values.add(new float[] { x, y, z });
            }
            return index;
        }

        float[][] values() {
            return values.toArray(new float[values.size()][]);
        }
    }

    private static final class Key {

        private final int[] bits;

        Key(float x, float y, float z) {
            bits = new int[] { Float.floatToRawIntBits(x), Float.floatToRawIntBits(y), Float.floatToRawIntBits(z) };
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(bits);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key && Arrays.equals(bits, ((Key) other).bits);
        }
    }
}
