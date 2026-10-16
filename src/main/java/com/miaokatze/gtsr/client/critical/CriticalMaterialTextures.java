package com.miaokatze.gtsr.client.critical;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.miaokatze.gtsr.common.critical.CriticalMaterials;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Stable terrain-atlas UVs: Minecraft advances sprite frames without rebuilding mesh display lists. */
@SideOnly(Side.CLIENT)
public final class CriticalMaterialTextures {

    public static final String[] FACE_NAMES = { "ny", "py", "nz", "pz", "nx", "px" };
    // Same outward face basis as the approved V2 pixel contract; PNG north is -v.
    public static final int[][] NORMALS = { { 0, -1, 0 }, { 0, 1, 0 }, { 0, 0, -1 }, { 0, 0, 1 }, { -1, 0, 0 },
        { 1, 0, 0 } };
    public static final int[][] U = { { 1, 0, 0 }, { 1, 0, 0 }, { -1, 0, 0 }, { 1, 0, 0 }, { 0, 0, 1 }, { 0, 0, -1 } };
    public static final int[][] V = { { 0, 0, 1 }, { 0, 0, -1 }, { 0, 1, 0 }, { 0, 1, 0 }, { 0, 1, 0 }, { 0, 1, 0 } };

    private static final Map<String, Map<String, IIcon>> ICONS = new HashMap<>();

    private CriticalMaterialTextures() {}

    public static void register(String alias, IIconRegister register) {
        JsonObject properties = CriticalMaterials.properties(alias);
        String id = properties.get("id")
            .getAsString();
        Map<String, IIcon> icons = new HashMap<>();
        if (properties.has("sprites")) {
            for (Map.Entry<String, JsonElement> group : properties.getAsJsonObject("sprites")
                .entrySet()) {
                registerPair(
                    icons,
                    group.getKey(),
                    group.getValue()
                        .getAsJsonObject(),
                    register);
            }
            if (properties.has("ctm")) {
                JsonObject groups = properties.getAsJsonObject("ctm")
                    .getAsJsonObject("sprites");
                for (Map.Entry<String, JsonElement> group : groups.entrySet()) {
                    for (Map.Entry<String, JsonElement> mask : group.getValue()
                        .getAsJsonObject()
                        .entrySet()) {
                        registerPair(
                            icons,
                            "ctm:" + group.getKey() + ':' + mask.getKey(),
                            mask.getValue()
                                .getAsJsonObject(),
                            register);
                    }
                }
            }
        } else {
            icons.put("side:albedo", register.registerIcon("gtsr:critical/" + id));
            icons.put("top:albedo", register.registerIcon("gtsr:critical/" + id + "__top"));
            if (properties.get("emissionStrength")
                .getAsDouble() > 0) {
                icons.put("side:emission", register.registerIcon("gtsr:critical/" + id + "_emission"));
            }
        }
        // Overwrite on every atlas stitch: never retain IIcons from a previous resource reload.
        ICONS.put(id, icons);
    }

    private static void registerPair(Map<String, IIcon> icons, String group, JsonObject pair, IIconRegister register) {
        for (String kind : new String[] { "albedo", "emission", "frozenAlbedo", "frozenEmission" }) {
            JsonElement name = pair.get(kind);
            if (name != null && !name.isJsonNull())
                icons.put(group + ':' + kind, register.registerIcon(name.getAsString()));
        }
    }

    public static IIcon icon(String alias, int side, int mask, boolean emission, boolean frozen) {
        JsonObject properties = CriticalMaterials.properties(alias);
        String id = properties.get("id")
            .getAsString();
        Map<String, IIcon> icons = ICONS.get(id);
        if (icons == null) return null;
        String face = FACE_NAMES[side];
        String group = side < 2 ? "top" : "side";
        String kind = emission ? "emission" : "albedo";
        String frozenKind = emission ? "frozenEmission" : "frozenAlbedo";
        if (properties.has("ctm")) {
            JsonObject ctm = properties.getAsJsonObject("ctm");
            group = ctm.getAsJsonObject("faceLookup")
                .get(face)
                .getAsString();
            mask &= ctm.getAsJsonObject("faceAllowedMask")
                .get(face)
                .getAsInt();
            return icons.get("ctm:" + group + ':' + mask + ':' + (frozen ? frozenKind : kind));
        }
        if (properties.has("faceSprites")) {
            JsonObject faceSprite = properties.getAsJsonObject("faceSprites")
                .getAsJsonObject(face);
            String target = "v2_" + faceSprite.get("material")
                .getAsString();
            group = faceSprite.get("group")
                .getAsString();
            icons = ICONS.get(target);
            if (icons == null) return null;
            return icons.get(group + ':' + (frozen ? frozenKind : kind));
        }
        return icons.get(group + ':' + kind);
    }

    /** Static chunk faces connect only matching exposed materials. Moving ownership is resolved by the TESR. */
    public static int worldMask(IBlockAccess world, int x, int y, int z, CriticalMaterials.MaterialBlock material,
        int side) {
        JsonObject properties = CriticalMaterials.properties(material.materialId);
        if (!properties.has("ctm") || material.proxy) return 0;
        String face = FACE_NAMES[side];
        int allowed = properties.getAsJsonObject("ctm")
            .getAsJsonObject("faceAllowedMask")
            .get(face)
            .getAsInt();
        int result = 0;
        for (int bit : new int[] { 1, 2, 4, 8 }) {
            if ((allowed & bit) == 0) continue;
            int[] basis = bit == 1 || bit == 4 ? V[side] : U[side];
            int sign = bit == 1 || bit == 8 ? -1 : 1;
            int nx = x + basis[0] * sign, ny = y + basis[1] * sign, nz = z + basis[2] * sign;
            if (!(world.getBlock(nx, ny, nz) instanceof CriticalMaterials.MaterialBlock next) || next.proxy
                || !material.materialId.equals(next.materialId)) continue;
            int[] normal = NORMALS[side];
            if (!next.shouldSideBeRendered(world, nx + normal[0], ny + normal[1], nz + normal[2], side)) continue;
            result |= bit;
        }
        return result;
    }
}
