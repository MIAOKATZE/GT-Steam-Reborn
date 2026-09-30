package com.miaokatze.gtsr.client.encounter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
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

/**
 * 完整资源读取器。直接读取装配后的OBJ/rig/PNG，不依赖宿主GTO的私有ObjModelLoader。
 * OBJ支持v/vt、负索引、g和多边形扇形三角化；MTL贴图路由由固定PNG覆盖。
 * 世界单位为格，绝不可再乘1/16；parent delta先于子轨道，pivot是静止绝对坐标。
 */
public final class RigAsset {

    final JsonObject rig;
    final Map<String, JsonObject> groups = new LinkedHashMap<String, JsonObject>();
    final Map<String, List<double[]>> meshes = new LinkedHashMap<String, List<double[]>>();
    public final ResourceLocation texture;
    public final String code;
    public final int triangles;
    private final Map<String, Integer> lists = new HashMap<String, Integer>();

    public void release() {
        for (int list : lists.values()) GL11.glDeleteLists(list, 1);
        lists.clear();
    }

    /**
     * resources根为assets/<modid>/liminal/<code>/；实际读取models/<code>.obj、
     * models/rig_animation.json，并绑定textures/model/<code>.png。
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
        List<double[]> vs = new ArrayList<double[]>(), uvs = new ArrayList<double[]>();
        String group = "root";
        int count = 0;
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(open(new ResourceLocation(modid, root + "models/" + code + ".obj")), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.split("#", 2)[0].trim();
                if (line.isEmpty()) continue;
                String[] w = line.split("\\s+");
                if (w[0].equals("v")) vs
                    .add(new double[] { Double.parseDouble(w[1]), Double.parseDouble(w[2]), Double.parseDouble(w[3]) });
                else if (w[0].equals("vt"))
                    uvs.add(new double[] { Double.parseDouble(w[1]), 1 - Double.parseDouble(w[2]) });
                else if (w[0].equals("g")) group = w[1];
                else if (w[0].equals("f")) {
                    if (!groups.containsKey(group)) throw new IOException("Missing rig group " + group);
                    List<double[]> out = meshes.get(group);
                    if (out == null) {
                        out = new ArrayList<double[]>();
                        meshes.put(group, out);
                    }
                    for (int i = 2; i < w.length - 1; i++) {
                        double[][] tri = new double[3][];
                        String[] f = { w[1], w[i], w[i + 1] };
                        for (int j = 0; j < 3; j++) {
                            String[] parts = f[j].split("/", -1);
                            if (parts.length < 2 || parts[1].isEmpty()) throw new IOException("Face without UV");
                            double[] v = vs.get(index(parts[0], vs.size())), uv = uvs.get(index(parts[1], uvs.size()));
                            tri[j] = new double[] { v[0], v[1], v[2], uv[0], uv[1], 0, 0, 0 };
                        }
                        double ax = tri[1][0] - tri[0][0], ay = tri[1][1] - tri[0][1], az = tri[1][2] - tri[0][2],
                            bx = tri[2][0] - tri[0][0], by = tri[2][1] - tri[0][1], bz = tri[2][2] - tri[0][2];
                        double nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx,
                            n = Math.sqrt(nx * nx + ny * ny + nz * nz);
                        if (n < 1e-12) throw new IOException("Degenerate triangle");
                        for (double[] v : tri) {
                            v[5] = nx / n;
                            v[6] = ny / n;
                            v[7] = nz / n;
                            out.add(v);
                        }
                        count++;
                    }
                }
            }
        } catch (RuntimeException ex) {
            throw new IOException("Invalid OBJ " + code, ex);
        }
        triangles = count;
        if (count == 0) throw new IOException("Empty OBJ " + code);
    }

    private static InputStream open(ResourceLocation path) throws IOException {
        return Minecraft.getMinecraft()
            .getResourceManager()
            .getResource(path)
            .getInputStream();
    }

    private static int index(String s, int size) {
        int i = Integer.parseInt(s);
        if (i == 0) throw new IllegalArgumentException("zero index");
        return i > 0 ? i - 1 : size + i;
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
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glColor4f(1, 1, 1, 1);
            for (Map.Entry<String, List<double[]>> part : meshes.entrySet()) {
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
                        list = GL11.glGenLists(1);
                        if (list == 0) throw new IllegalStateException("GL display list allocation failed");
                        GL11.glNewList(list, GL11.GL_COMPILE);
                        Tessellator tess = Tessellator.instance;
                        tess.startDrawing(GL11.GL_TRIANGLES);
                        for (double[] v : part.getValue()) {
                            tess.setNormal((float) v[5], (float) v[6], (float) v[7]);
                            tess.addVertexWithUV(v[0], v[1], v[2], v[3], v[4]);
                        }
                        tess.draw();
                        GL11.glEndList();
                        lists.put(group, list);
                    }
                    GL11.glCallList(list);
                }
            }
        }
    }
}
