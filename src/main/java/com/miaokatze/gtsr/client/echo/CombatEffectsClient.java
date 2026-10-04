package com.miaokatze.gtsr.client.echo;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.opengl.GL11;

import com.miaokatze.gtsr.client.encounter.CubeRuneParticle;
import com.miaokatze.gtsr.common.dimension.prosperity.echo.CombatEffects;
import com.miaokatze.gtsr.common.dimension.prosperity.echo.CombatGeometry;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class CombatEffectsClient
    implements CombatEffects.Receiver, net.minecraft.client.resources.IResourceManagerReloadListener {

    private final Map<String, Visual> active = new HashMap<>();
    private WorldClient world;
    private final Map<java.util.UUID, Integer> latest = new java.util.LinkedHashMap<>();
    private final Map<java.util.UUID, Integer> latestMeteor = new java.util.LinkedHashMap<>();
    private final Map<java.util.UUID, Integer> meteorStages = new HashMap<>();
    private static boolean registered;
    private static final int METEOR_SIGNAL = 1900000000;
    private static final net.minecraft.util.ResourceLocation SIGIL = new net.minecraft.util.ResourceLocation(
        "gtsr:textures/fx/echo_attack_sigil.png");
    private static final float[][] COLORS = { { .75F, .43F, .22F }, { .25F, .66F, .51F }, { .95F, .68F, .23F },
        { .57F, .37F, .85F } };

    private static final class Visual {

        CombatEffects.Signal signal;
        int left;

        Visual(CombatEffects.Signal s) {
            signal = s;
            left = s.duration;
        }
    }

    public static void register() {
        if (registered) return;
        registered = true;
        CombatEffectsClient c = new CombatEffectsClient();
        CombatEffects.receiver = c;
        MinecraftForge.EVENT_BUS.register(c);
        FMLCommonHandler.instance()
            .bus()
            .register(c);
        net.minecraft.client.resources.IResourceManager manager = Minecraft.getMinecraft()
            .getResourceManager();
        if (manager instanceof net.minecraft.client.resources.IReloadableResourceManager)
            ((net.minecraft.client.resources.IReloadableResourceManager) manager).registerReloadListener(c);
    }

    public void onResourceManagerReload(net.minecraft.client.resources.IResourceManager manager) {
        active.clear();
        latest.clear();
        latestMeteor.clear();
        meteorStages.clear();
        world = null;
    }

    public void receive(final CombatEffects.Signal s) {
        final Minecraft mc = Minecraft.getMinecraft();
        final WorldClient received = mc.theWorld;
        mc.func_152344_a(new Runnable() {

            public void run() {
                if (mc.theWorld == null || mc.theWorld != received
                    || mc.thePlayer == null
                    || mc.thePlayer.dimension != s.dimension
                    || !mc.thePlayer.getUniqueID()
                        .equals(s.player))
                    return;
                Entity e = received.getEntityByID(s.entity);
                if (e == null || !e.getUniqueID()
                    .equals(s.source) || e.isDead) return;
                if (world != received) {
                    active.clear();
                    latest.clear();
                    latestMeteor.clear();
                    meteorStages.clear();
                    world = received;
                }
                boolean meteor = isMeteor(s.sequence);
                if (meteor) {
                    Integer prior = latestMeteor.get(s.source);
                    if (prior != null && s.sequence < prior) return;
                    Integer priorStage = meteorStages.get(s.source);
                    if (prior != null && s.sequence == prior && priorStage != null && s.stage < priorStage) return;
                    if (prior == null || s.sequence > prior) {
                        if (latestMeteor.size() >= 128 && !latestMeteor.containsKey(s.source)) {
                            java.util.UUID expired = latestMeteor.keySet()
                                .iterator()
                                .next();
                            latestMeteor.remove(expired);
                            meteorStages.remove(expired);
                        }
                        latestMeteor.put(s.source, s.sequence);
                        meteorStages.put(s.source, s.stage);
                        // A newer cast supersedes only the older meteor of this source.
                        Iterator<Visual> existing = active.values()
                            .iterator();
                        while (existing.hasNext()) {
                            CombatEffects.Signal oldSignal = existing.next().signal;
                            if (oldSignal.source.equals(s.source) && isMeteor(oldSignal.sequence)) existing.remove();
                        }
                    }
                }
                if (meteor) meteorStages.put(s.source, s.stage);
                String key = s.source + ":" + s.sequence;
                Visual old = active.get(key);
                Integer last = latest.get(s.source);
                int serial = s.sequence / 32;
                if (!meteor && last != null && serial < last && (old == null || s.stage == 0)) return;
                if (!meteor && (last == null || serial > last)) {
                    if (latest.size() >= 128 && !latest.containsKey(s.source)) latest.remove(
                        latest.keySet()
                            .iterator()
                            .next());
                    latest.put(s.source, serial);
                }
                if (old != null && old.signal.stage > s.stage) return;
                if (s.stage == 2 && s.duration == 0) active.remove(key);
                else {
                    if (active.size() >= 128 && !active.containsKey(key)) return;
                    active.put(key, new Visual(s));
                }
            }
        });
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || world != mc.theWorld || mc.thePlayer == null) {
            active.clear();
            latest.clear();
            latestMeteor.clear();
            meteorStages.clear();
            world = null;
            return;
        }
        if (mc.isGamePaused()) return;
        int budget = 48;
        Iterator<Visual> it = active.values()
            .iterator();
        while (it.hasNext()) {
            Visual v = it.next();
            Entity e = world.getEntityByID(v.signal.entity);
            if (--v.left < 0 || e == null
                || e.isDead
                || !e.getUniqueID()
                    .equals(v.signal.source)) {
                it.remove();
                continue;
            }
            if (budget <= 0) continue;
            CombatGeometry g = v.signal.geometry;
            if (mc.thePlayer.getDistanceSq(g.x, g.y, g.z) > 64 * 64) continue;
            int age = v.signal.duration - v.left;
            boolean warning = v.signal.stage == 0, impact = v.signal.stage == 1 || (v.signal.stage == 2 && age < 8);
            if (warning ? age % 6 != 0 : impact ? age % 2 != 0 : age % 6 != 0) continue;
            float[] col = COLORS[v.signal.palette];
            int count = Math.min(budget, warning ? 2 : impact ? 6 : 1);
            for (int i = 0; i < count; i++) {
                double u = ((v.signal.sequence * 7L + age * 3L + i * 11L) & 255) / 256D;
                double[] pos = point(
                    g,
                    u,
                    g.shape == CombatGeometry.LINE || g.shape == CombatGeometry.CHAIN ? .5 : .55);
                double y = CombatGeometry.groundY(world, pos[0], g.y + 2, pos[1]);
                double angle = u * Math.PI * 2;
                CubeRuneParticle.emit(
                    world,
                    pos[0],
                    y + (warning ? .25 : impact ? .8 : .5),
                    pos[1],
                    impact ? Math.cos(angle) * .035 : 0,
                    warning ? .008 : .045,
                    impact ? Math.sin(angle) * .035 : 0,
                    warning ? .055 : impact ? .16 : .07,
                    col[0],
                    col[1],
                    col[2],
                    warning ? 16 : impact ? 24 : 14);
                budget--;
            }
        }
    }

    private static final int MAX_VERTICES = 16384;
    private int verticesLeft;
    private final Map<Long, Surface> surfaces = new HashMap<>();
    private static final net.minecraft.util.ResourceLocation[] SEALS = {
        new net.minecraft.util.ResourceLocation("gtsr:textures/fx/echo_foundry_sigil.png"), SIGIL,
        new net.minecraft.util.ResourceLocation("gtsr:textures/fx/echo_king_sigil.png"),
        new net.minecraft.util.ResourceLocation("gtsr:textures/fx/echo_hive_sigil.png") };

    private static final class Surface {

        double base, top;

        Surface(double base, double top) {
            this.base = base;
            this.top = top;
        }
    }

    @SubscribeEvent
    public void render(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (world == null || mc.theWorld != world || mc.thePlayer == null) return;
        double p = event.partialTicks,
            ox = mc.thePlayer.lastTickPosX + (mc.thePlayer.posX - mc.thePlayer.lastTickPosX) * p,
            oy = mc.thePlayer.lastTickPosY + (mc.thePlayer.posY - mc.thePlayer.lastTickPosY) * p,
            oz = mc.thePlayer.lastTickPosZ + (mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ) * p;
        verticesLeft = MAX_VERTICES;
        surfaces.clear();
        try (com.miaokatze.gtsr.client.encounter.GlScope scope = new com.miaokatze.gtsr.client.encounter.GlScope()) {
            net.minecraft.client.renderer.OpenGlHelper
                .setActiveTexture(net.minecraft.client.renderer.OpenGlHelper.lightmapTexUnit);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            net.minecraft.client.renderer.OpenGlHelper
                .setActiveTexture(net.minecraft.client.renderer.OpenGlHelper.defaultTexUnit);
            GL11.glTranslated(-ox, -oy, -oz);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            GL11.glDepthMask(false);
            GL11.glLineWidth(2F);
            for (Visual v : active.values()) {
                CombatGeometry g = v.signal.geometry;
                double distance = mc.thePlayer.getDistanceSq(g.x, g.y, g.z);
                if (distance > 128 * 128 || verticesLeft < 1800) continue;
                int segments = distance > 48 * 48 ? 16 : 48;
                double age = Math.max(0, v.signal.duration - v.left + p);
                boolean warning = v.signal.stage == 0;
                double flash = warning ? 0 : v.signal.stage == 1 ? Math.max(0, 1 - age / 10) : Math.max(0, 1 - age / 8);
                double fade = warning ? 1 : Math.max(0, v.left / (double) Math.max(1, v.signal.duration));
                float[] color = COLORS[v.signal.palette];
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                tint(color, warning ? .14F : (float) (.13 * fade + .17 * flash));
                fill(g, segments);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
                tint(color, warning ? .95F : (float) (.35 * fade + .65 * flash));
                outline(g, segments, 0);
                // The second line and moving hatching stay inside the authored hit footprint.
                tint(color, warning ? .55F : (float) (.5 * fade));
                outline(g, segments, Math.min(.24, g.radius * .09));
                markings(g, v.signal.palette, age, warning, fade, segments);
                if (isMeteor(v.signal.sequence) && warning) meteor(v, p);
                if (warning) {
                    tint(color, .18F);
                    curtain(g, segments, .20, .14);
                } else {
                    double rise = (v.signal.palette == 2 ? 14 : v.signal.palette == 3 ? 10 : 8)
                        * (.35 + .65 * Math.sin(Math.min(1, age / 8) * Math.PI))
                        * (flash > 0 ? 1 : .2 * fade);
                    tint(color, (float) (.55 * flash + .12 * fade));
                    curtain(g, segments, rise, .8);
                    accents(g, v.signal.palette, age, flash, fade);
                    stamp(g, v.signal.palette, age, flash, fade);
                    // Diffusion sweeps only across the dangerous annulus or disk, never beyond its edge.
                    if (g.shape != CombatGeometry.LINE && g.shape != CombatGeometry.CHAIN
                        && g.shape != CombatGeometry.CONE) {
                        for (int layer = 0; layer < 2; layer++) {
                            double u = Math.min(1, (age + layer * 2) / 10), r = g.inner + (g.radius - g.inner) * u;
                            tint(color, (float) ((1 - u) * (.65 * flash + .25 * fade)));
                            circle(g, r, segments, .22 + layer * .12);
                        }
                    }
                }
            }
        } finally {
            surfaces.clear();
        }
    }

    private static boolean isMeteor(int sequence) {
        return sequence >= METEOR_SIGNAL;
    }

    /** One-second windup, landing six seconds after the cast begins. */
    private static double meteorHeight(double tick) {
        double progress = Math.max(0, Math.min(1, (tick - 20) / 100D));
        return 32 * (1 - progress * progress);
    }

    private void meteor(Visual v, double partial) {
        Entity source = world.getEntityByID(v.signal.entity);
        if (!(source instanceof com.miaokatze.gtsr.common.dimension.prosperity.encounter.EntitySilentKing)) return;
        int tick = ((com.miaokatze.gtsr.common.dimension.prosperity.encounter.EntitySilentKing) source)
            .getMeteorTicks();
        if (tick < 0) return;
        CombatGeometry g = v.signal.geometry;
        double height = meteorHeight(tick + partial);
        // Actual server-timed descent; the footprint remains at the locked danger location.
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(.78F, .48F, .96F, .8F);
        CubeRuneParticle.box(g.x, g.y + height + 1.5, g.z, 1.5);
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6 + (tick + partial) * .035;
            GL11.glColor4f(1F, .7F, .25F, .55F);
            CubeRuneParticle
                .box(g.x + Math.cos(a) * 2.2, g.y + height + 1.5 + Math.sin(a * 2) * .3, g.z + Math.sin(a) * 2.2, .12);
        }
        for (int i = 0; i < 5; i++) {
            GL11.glColor4f(.68F, .3F, 1F, (5 - i) * .09F);
            CubeRuneParticle.box(g.x, g.y + height + 3 + i * 1.3, g.z, 1.1 - i * .13);
        }
    }

    private static void tint(float[] c, float alpha) {
        GL11.glColor4f(c[0], c[1], c[2], Math.max(0, Math.min(1, alpha)));
    }

    private double floor(double x, double y, double z) {
        long key = ((long) (int) Math.floor(x) << 32) ^ ((int) Math.floor(z) & 0xffffffffL);
        Surface s = surfaces.get(key);
        if (s == null || Math.abs(s.base - y) > 2) {
            s = new Surface(y, CombatGeometry.groundY(world, x, y + 2, z));
            surfaces.put(key, s);
        }
        return s.top;
    }

    private void vertex(double x, double y, double z, double height) {
        if (verticesLeft <= 0) return;
        verticesLeft--;
        GL11.glVertex3d(x, floor(x, y, z) + .13 + height, z);
    }

    private void circle(CombatGeometry g, double radius, int n, double height) {
        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            vertex(g.x + Math.cos(a) * radius, g.y, g.z + Math.sin(a) * radius, height);
        }
        GL11.glEnd();
    }

    private static double[] point(CombatGeometry g, double u, double v) {
        if (g.shape == CombatGeometry.LINE || g.shape == CombatGeometry.CHAIN) {
            double dx = g.tx - g.x, dz = g.tz - g.z, len = Math.max(.001, Math.sqrt(dx * dx + dz * dz));
            return new double[] { g.x + dx * u - dz / len * g.radius * v, g.z + dz * u + dx / len * g.radius * v };
        }
        if (g.shape == CombatGeometry.CONE) u = Math.max(0, Math.min(1, u));
        double angle = g.shape == CombatGeometry.CONE ? Math.atan2(g.tz - g.z, g.tx - g.x) + (u - .5) * Math.PI / 2
            : u * Math.PI * 2;
        double radius = g.inner + (g.radius - g.inner) * v;
        return new double[] { g.x + Math.cos(angle) * radius, g.z + Math.sin(angle) * radius };
    }

    private void fill(CombatGeometry g, int n) {
        if (g.shape == CombatGeometry.LINE || g.shape == CombatGeometry.CHAIN) {
            GL11.glBegin(GL11.GL_QUADS);
            for (double[] uv : new double[][] { { 0, -1 }, { 1, -1 }, { 1, 1 }, { 0, 1 } }) {
                double[] p = point(g, uv[0], uv[1]);
                vertex(p[0], g.y, p[1], 0);
            }
            GL11.glEnd();
            return;
        }
        GL11.glBegin(GL11.GL_TRIANGLES);
        for (int i = 0; i < n; i++) {
            double[] a = point(g, i / (double) n, 0), b = point(g, (i + 1) / (double) n, 0),
                c = point(g, (i + 1) / (double) n, 1), d = point(g, i / (double) n, 1);
            vertex(a[0], g.y, a[1], 0);
            vertex(c[0], g.y, c[1], 0);
            vertex(d[0], g.y, d[1], 0);
            if (g.inner > 0) {
                vertex(a[0], g.y, a[1], 0);
                vertex(b[0], g.y, b[1], 0);
                vertex(c[0], g.y, c[1], 0);
            }
        }
        GL11.glEnd();
    }

    private void outline(CombatGeometry g, int n, double inset) {
        if (g.shape == CombatGeometry.LINE || g.shape == CombatGeometry.CHAIN) {
            double v = Math.max(0, 1 - inset / Math.max(.001, g.radius));
            GL11.glBegin(GL11.GL_LINE_LOOP);
            for (double[] uv : new double[][] { { 0, -v }, { 1, -v }, { 1, v }, { 0, v } }) {
                double[] p = point(g, uv[0], uv[1]);
                vertex(p[0], g.y, p[1], .01);
            }
            GL11.glEnd();
            return;
        }
        if (g.shape == CombatGeometry.CONE) {
            GL11.glBegin(GL11.GL_LINE_LOOP);
            vertex(g.x, g.y, g.z, .01);
            for (int i = 0; i <= n; i++) {
                double[] p = point(g, i / (double) n, 1 - inset / Math.max(.001, g.radius));
                vertex(p[0], g.y, p[1], .01);
            }
            GL11.glEnd();
            return;
        }
        circle(g, Math.max(g.inner, g.radius - inset), n, .01);
        if (g.inner > 0) circle(g, Math.min(g.radius, g.inner + inset), n, .01);
    }

    private void curtain(CombatGeometry g, int n, double height, double strength) {
        if (height <= 0) return;
        // Separate graded strips suggest steam while preserving exact floor boundaries.
        java.nio.FloatBuffer rgba = org.lwjgl.BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_CURRENT_COLOR, rgba);
        int edges = g.shape == CombatGeometry.LINE || g.shape == CombatGeometry.CHAIN ? 4 : n;
        for (int rim = 0; rim < (g.inner > 0 ? 2 : 1); rim++) {
            GL11.glBegin(GL11.GL_QUADS);
            for (int i = 0; i < edges; i++) {
                double[] a, b;
                if (edges == 4) {
                    double[][] uv = { { 0, -1 }, { 1, -1 }, { 1, 1 }, { 0, 1 } };
                    a = point(g, uv[i][0], uv[i][1]);
                    b = point(g, uv[(i + 1) % 4][0], uv[(i + 1) % 4][1]);
                } else {
                    a = point(g, i / (double) n, rim == 0 ? 1 : 0);
                    b = point(g, (i + 1) / (double) n, rim == 0 ? 1 : 0);
                }
                GL11.glColor4f(rgba.get(0), rgba.get(1), rgba.get(2), rgba.get(3) * (float) strength);
                vertex(a[0], g.y, a[1], 0);
                vertex(b[0], g.y, b[1], 0);
                GL11.glColor4f(rgba.get(0), rgba.get(1), rgba.get(2), 0);
                vertex(b[0], g.y, b[1], height);
                vertex(a[0], g.y, a[1], height);
            }
            GL11.glEnd();
        }
    }

    private void markings(CombatGeometry g, int palette, double age, boolean warning, double fade, int n) {
        float[] c = COLORS[palette];
        tint(c, (float) (warning ? .55 : .40 * fade));
        int marks = n == 16 ? 8 : palette == 2 ? 20 : 16;
        GL11.glBegin(GL11.GL_LINES);
        for (int i = 0; i < marks; i++) {
            double u = (i / (double) marks + age * (palette == 3 ? -.003 : .003)) % 1;
            double[] a = point(g, u, .82), b = point(g, u, .98);
            if (g.shape == CombatGeometry.LINE || g.shape == CombatGeometry.CHAIN) {
                a = point(g, u, -.82);
                b = point(g, u, .82);
            }
            vertex(a[0], g.y, a[1], .035);
            vertex(b[0], g.y, b[1], .035);
            if (palette == 2) {
                double[] d = point(g, u + .007, .9);
                vertex(b[0], g.y, b[1], .035);
                vertex(d[0], g.y, d[1], .035);
            }
        }
        GL11.glEnd();
    }

    private void accents(CombatGeometry g, int palette, double age, double flash, double fade) {
        int count = palette == 2 ? 12 : 16;
        float[] c = COLORS[palette];
        for (int i = 0; i < count; i++) {
            double u = .12 + .76 * ((i * .61803398875) % 1), v = .28 + ((i * .38196601125) % 1) * .5;
            if (g.shape == CombatGeometry.LINE || g.shape == CombatGeometry.CHAIN) v = v * 2 - 1;
            double[] p = point(g, u, v);
            double height = (palette == 2 ? 12 : palette == 3 ? 9 : 7) * flash;
            if (height < .05) continue;
            if (palette == 0) {
                // Furnace vents and upward fractured copper shards.
                tint(c, (float) (.8 * flash));
                GL11.glBegin(GL11.GL_LINES);
                vertex(p[0], g.y, p[1], .2);
                vertex(p[0], g.y, p[1], height * (.5 + .5 * v));
                GL11.glEnd();
                GL11.glBegin(GL11.GL_TRIANGLES);
                vertex(p[0] - .20, g.y, p[1], height);
                vertex(p[0] + .20, g.y, p[1], height + 1.15);
                vertex(p[0] + .1, g.y, p[1] + .22, height - .1);
                GL11.glEnd();
            } else if (palette == 3) {
                // Hive filaments end in suspended, faceted spore bulbs.
                tint(c, (float) (.65 * flash));
                GL11.glBegin(GL11.GL_LINE_STRIP);
                vertex(p[0], g.y, p[1], 0);
                vertex(p[0] + Math.sin(age * .2 + i) * .25, g.y, p[1], height * .55);
                vertex(p[0], g.y, p[1], height);
                GL11.glEnd();
                double r = .35 + .35 * flash;
                tint(c, (float) (.6 * flash));
                GL11.glBegin(GL11.GL_TRIANGLES);
                for (int k = 0; k < 4; k++) {
                    double a = k * Math.PI / 2, b = (k + 1) * Math.PI / 2;
                    vertex(p[0], g.y, p[1], height + r);
                    vertex(p[0] + Math.cos(a) * r, g.y, p[1] + Math.sin(a) * r, height);
                    vertex(p[0] + Math.cos(b) * r, g.y, p[1] + Math.sin(b) * r, height);
                }
                GL11.glEnd();
            } else {
                // Royal amber columns are capped by a three-point crown, patina by sparks.
                tint(c, (float) (.85 * flash));
                GL11.glBegin(GL11.GL_LINES);
                vertex(p[0], g.y, p[1], .1);
                vertex(p[0], g.y, p[1], height);
                GL11.glEnd();
                GL11.glBegin(GL11.GL_LINE_STRIP);
                vertex(p[0] - .35, g.y, p[1], height);
                vertex(p[0] - .28, g.y, p[1], height + .9);
                vertex(p[0], g.y, p[1], height + .4);
                vertex(p[0] + .28, g.y, p[1], height + .9);
                vertex(p[0] + .35, g.y, p[1], height);
                GL11.glEnd();
            }
        }
    }

    private void stamp(CombatGeometry g, int palette, double age, double flash, double fade) {
        if (g.shape == CombatGeometry.LINE || g.shape == CombatGeometry.CHAIN) return;
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(SEALS[palette]);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        tint(COLORS[palette], (float) (.9 * flash + .22 * fade));
        int count = g.inner > 0 || g.shape == CombatGeometry.CONE ? 6 : 1;
        for (int i = 0; i < count; i++) {
            double[] p = count == 1 ? new double[] { g.x, g.z } : point(g, (i + .5) / count, .5);
            double r = count == 1 ? Math.min(g.radius * .65, 4.5) : Math.min(.8, (g.radius - g.inner) * .25);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2d(0, 0);
            vertex(p[0] - r, g.y, p[1] - r, .06);
            GL11.glTexCoord2d(1, 0);
            vertex(p[0] + r, g.y, p[1] - r, .06);
            GL11.glTexCoord2d(1, 1);
            vertex(p[0] + r, g.y, p[1] + r, .06);
            GL11.glTexCoord2d(0, 1);
            vertex(p[0] - r, g.y, p[1] + r, .06);
            GL11.glEnd();
        }
        GL11.glDisable(GL11.GL_TEXTURE_2D);
    }
}
