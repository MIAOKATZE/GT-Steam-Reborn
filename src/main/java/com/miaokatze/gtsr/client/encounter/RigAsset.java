// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.client.encounter;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.miaokatze.gtsr.client.render.ClientDisplayList;

/**
 * 完整资源读取器。发行包使用无损编译网格，资源包仍可用OBJ覆盖；rig和PNG保持原路径。
 * 坐标和UV使用共享double数组，三角形只保存索引；不按每个角点展开八个double。
 * 世界单位为格，绝不可再乘1/16；parent delta先于子轨道，pivot是静止绝对坐标。
 */
public final class RigAsset {

    final JsonObject rig;
    final Map<String, JsonObject> groups = new LinkedHashMap<String, JsonObject>();
    private final RigMeshData mesh;
    public final ResourceLocation texture;
    public final String code;
    public final int triangles;
    private final Map<String, Integer> lists = new HashMap<String, Integer>();

    public void release() {
        for (int list : lists.values()) GL11.glDeleteLists(list, 1);
        lists.clear();
    }

    /**
     * resources根为assets/<modid>/liminal/<code>/。优先使用资源包中的models/<code>.obj，
     * 仅在该路径不存在时读取发行包models/<code>.rigmesh；格式错误不能静默退回默认模型。
     * models/rig_animation.json及textures/model/<code>.png继续由资源管理器独立覆盖。
     */
    public RigAsset(String modid, String code) throws IOException {
        if (!code.matches("[a-z0-9_-]+")) throw new IOException("Invalid code " + code);
        this.code = code;
        String root = "liminal/" + code + "/";
        texture = new ResourceLocation(modid, root + "textures/model/" + code + ".png");
        try (Reader reader = new InputStreamReader(
            open(new ResourceLocation(modid, root + "models/rig_animation.json")),
            "UTF-8")) {
            rig = new JsonParser().parse(reader)
                .getAsJsonObject();
        }
        for (JsonElement e : rig.getAsJsonArray("groups")) {
            JsonObject g = e.getAsJsonObject();
            String name = text(g, "name", "");
            if (groups.put(name, g) != null) throw new IOException("Duplicate group " + name);
        }
        for (String name : groups.keySet()) validateChain(name, new HashSet<String>());
        mesh = loadMesh(modid, root, code);
        for (String group : mesh.groups()
            .keySet()) if (!groups.containsKey(group)) throw new IOException("Missing rig group " + group);
        triangles = mesh.triangles();
    }

    private static RigMeshData loadMesh(String modid, String root, String code) throws IOException {
        InputStream obj;
        try {
            obj = open(new ResourceLocation(modid, root + "models/" + code + ".obj"));
        } catch (FileNotFoundException missingObj) {
            try (InputStream compiled = open(new ResourceLocation(modid, root + "models/" + code + ".rigmesh"))) {
                return RigMeshData.readCompiled(compiled);
            }
        }
        try (InputStream source = obj) {
            return RigMeshData.readObj(source);
        }
    }

    private static InputStream open(ResourceLocation path) throws IOException {
        return Minecraft.getMinecraft()
            .getResourceManager()
            .getResource(path)
            .getInputStream();
    }

    private void validateChain(String name, Set<String> seen) throws IOException {
        if (!groups.containsKey(name) || !seen.add(name)) throw new IOException("Invalid rig parent " + name);
        String p = text(groups.get(name), "parent", "");
        if (!p.isEmpty()) validateChain(p, seen);
    }

    static String text(JsonObject o, String k, String d) {
        return o.has(k) && !o.get(k)
            .isJsonNull() ? o.get(k)
                .getAsString() : d;
    }

    static double number(JsonObject o, String k, double d) {
        return o.has(k) ? o.get(k)
            .getAsDouble() : d;
    }

    static double sample(JsonArray keys, double t, boolean step) {
        if (keys == null || keys.size() == 0) return 0;
        JsonArray prev = keys.get(0)
            .getAsJsonArray();
        if (t <= prev.get(0)
            .getAsDouble())
            return prev.get(1)
                .getAsDouble();
        for (int i = 1; i < keys.size(); i++) {
            JsonArray next = keys.get(i)
                .getAsJsonArray();
            double end = next.get(0)
                .getAsDouble();
            if (t < end) return step ? prev.get(1)
                .getAsDouble()
                : prev.get(1)
                    .getAsDouble()
                    + (next.get(1)
                        .getAsDouble()
                        - prev.get(1)
                            .getAsDouble())
                        * (t - prev.get(0)
                            .getAsDouble())
                        / (end - prev.get(0)
                            .getAsDouble());
            prev = next;
        }
        return prev.get(1)
            .getAsDouble();
    }

    /** 显式检查命名clip，调用者不能用首个动画隐式代替idle或walk。 */
    public boolean hasClip(String name) {
        return rig.getAsJsonObject("animations")
            .has(name);
    }

    public double clipTime(String name, double gameTick) {
        JsonObject c = rig.getAsJsonObject("animations")
            .getAsJsonObject(name);
        if (c == null) return 0;
        double f = number(c, "frames", 1), t = Math.max(0, gameTick * number(c, "speed", 1));
        return c.has("loop") && c.get("loop")
            .getAsBoolean() ? t % f : Math.min(f, t);
    }

    private boolean visible(String name, JsonArray tracks, double t) {
        JsonObject g = groups.get(name);
        String p = text(g, "parent", "");
        if (!p.isEmpty() && !visible(p, tracks, t)) return false;
        boolean visible = number(g, "visible", number(g, "visibility", 1)) != 0;
        for (JsonElement e : tracks) {
            JsonObject tr = e.getAsJsonObject();
            String ch = text(tr, "channel", "");
            if (text(tr, "group", "").equals(name) && (ch.equals("visible") || ch.equals("visibility")))
                visible = sample(tr.getAsJsonArray("keys"), t, true) == 1;
        }
        return visible;
    }

    /** 逐层应用静止绝对pivot的delta，严格维持JSON中轨道顺序，不重新叠加静止变换。 */
    private void chain(String name, JsonArray tracks, double t) {
        JsonObject g = groups.get(name);
        String p = text(g, "parent", "");
        if (!p.isEmpty()) chain(p, tracks, t);
        for (JsonElement e : tracks) {
            JsonObject tr = e.getAsJsonObject();
            if (!text(tr, "group", "").equals(name)) continue;
            String ch = text(tr, "channel", "");
            if (ch.equals("visible") || ch.equals("visibility")) continue;
            String axis = text(tr, "axis", text(g, "axis", "+Y"));
            int a = "XYZ".indexOf(axis.charAt(axis.length() - 1));
            double sign = axis.startsWith("-") ? -1 : 1, v = sample(tr.getAsJsonArray("keys"), t, false);
            double x = a == 0 ? sign : 0, y = a == 1 ? sign : 0, z = a == 2 ? sign : 0;
            if (ch.equals("translate")) GL11.glTranslated(x * v, y * v, z * v);
            else if (ch.equals("rotate")) {
                JsonArray pivot = g.getAsJsonArray("pivot");
                double px = pivot.get(0)
                    .getAsDouble(),
                    py = pivot.get(1)
                        .getAsDouble(),
                    pz = pivot.get(2)
                        .getAsDouble();
                GL11.glTranslated(px, py, pz);
                GL11.glRotated(v, x, y, z);
                GL11.glTranslated(-px, -py, -pz);
            }
        }
    }

    private double[] uv(String name, String group, double t) {
        JsonObject frames = rig.getAsJsonObject("emissive_frames"),
            offsets = rig.getAsJsonObject("emissive_uv_offsets");
        if (offsets == null || offsets.entrySet()
            .isEmpty()) return new double[] { 0, 0 };
        int state = frames != null && frames.has(name) ? (int) sample(frames.getAsJsonArray(name), t, true) : 0;
        String key = text(groups.get(group), "emissive_uv_key", group);
        JsonArray values = offsets.has(key) ? offsets.getAsJsonArray(key)
            : offsets.has(group) ? offsets.getAsJsonArray(group)
                : offsets.entrySet()
                    .iterator()
                    .next()
                    .getValue()
                    .getAsJsonArray();
        JsonArray v = values.get(Math.max(0, Math.min(state, values.size() - 1)))
            .getAsJsonArray();
        return new double[] { v.get(0)
            .getAsDouble(),
            -v.get(1)
                .getAsDouble() };
    }

    /** Caller supplies position/yaw. Lightmap and every matrix restored even if a group fails. */
    public void render(String name, double gameTick) {
        render(name, gameTick, 1F);
    }

    /** Alpha belongs to the model draw, including emissive parts and cached geometry. */
    public void render(String name, double gameTick, float alpha) {
        alpha = Math.max(0, Math.min(1, alpha));
        if (alpha <= 0) return;
        JsonObject clip = rig.getAsJsonObject("animations")
            .getAsJsonObject(name);
        JsonArray tracks = clip != null ? clip.getAsJsonArray("tracks") : new JsonArray();
        if (tracks == null) tracks = new JsonArray();
        double t = clipTime(name, gameTick);
        try (GlScope scope = new GlScope()) {
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            if (alpha < 1) {
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GL11.glDisable(GL11.GL_ALPHA_TEST);
                GL11.glDepthMask(false);
            } else GL11.glDisable(GL11.GL_BLEND);
            GL11.glColor4f(1, 1, 1, alpha);
            for (Map.Entry<String, int[]> part : mesh.groups()
                .entrySet()) {
                String group = part.getKey();
                if (!visible(group, tracks, t)) continue;
                boolean glow = group.endsWith("__glow");
                JsonArray eg = rig.getAsJsonArray("emissive_groups");
                if (eg != null) for (JsonElement e : eg) glow |= e.getAsString()
                    .equals(group);
                try (GlScope groupScope = new GlScope()) {
                    chain(group, tracks, t);
                    double[] offset = glow ? uv(name, group, t) : new double[] { 0, 0 };
                    if (glow) {
                        GL11.glDisable(GL11.GL_LIGHTING);
                        OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
                        GL11.glDisable(GL11.GL_TEXTURE_2D);
                        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
                    }
                    GL11.glMatrixMode(GL11.GL_TEXTURE);
                    GL11.glTranslated(offset[0], offset[1], 0);
                    GL11.glMatrixMode(GL11.GL_MODELVIEW);
                    Integer list = lists.get(group);
                    if (list == null) {
                        list = ClientDisplayList.compile(() -> {
                            Tessellator tess = Tessellator.instance;
                            tess.startDrawing(GL11.GL_TRIANGLES);
                            drawMesh(tess, part.getValue());
                            tess.draw();
                        });
                        lists.put(group, list);
                    }
                    GL11.glCallList(list);
                }
            }
        }
    }

    /**
     * 与旧OBJ扇形三角化后的角点顺序及法线运算顺序一致。只在编译display list时执行，
     * 后续动画仍通过原有分组矩阵和缓存列表绘制，不每帧展开网格或创建角点数组。
     */
    private void drawMesh(Tessellator tess, int[] indices) {
        double[] positions = mesh.positions(), texCoords = mesh.texCoords();
        for (int i = 0; i < indices.length; i += 6) {
            int p0 = indices[i] * 3, p1 = indices[i + 2] * 3, p2 = indices[i + 4] * 3;
            double ax = positions[p1] - positions[p0], ay = positions[p1 + 1] - positions[p0 + 1],
                az = positions[p1 + 2] - positions[p0 + 2], bx = positions[p2] - positions[p0],
                by = positions[p2 + 1] - positions[p0 + 1], bz = positions[p2 + 2] - positions[p0 + 2];
            double nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx,
                n = Math.sqrt(nx * nx + ny * ny + nz * nz);
            for (int corner = 0; corner < 6; corner += 2) {
                int p = indices[i + corner] * 3, uv = indices[i + corner + 1] * 2;
                tess.setNormal((float) (nx / n), (float) (ny / n), (float) (nz / n));
                tess.addVertexWithUV(
                    positions[p],
                    positions[p + 1],
                    positions[p + 2],
                    texCoords[uv],
                    texCoords[uv + 1]);
            }
        }
    }
}
