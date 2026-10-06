package com.miaokatze.gtsr.client.travel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.miaokatze.gtsr.client.encounter.CubeRuneParticle;
import com.miaokatze.gtsr.common.dimension.prosperity.travel.AnchorIntent;
import com.miaokatze.gtsr.common.dimension.prosperity.travel.SpacetimeEffects;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Client tick drains the network; this registry owns only spacetime visuals. */
public final class SpacetimeClient {

    private static final ConcurrentLinkedQueue<SpacetimeEffects.Signal> QUEUE = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger QUEUED = new AtomicInteger();
    private static final Map<String, Visual> PORTALS = new LinkedHashMap<>();
    private static final List<Visual> BURSTS = new ArrayList<>();
    private static Object owner;
    private static long clock;

    private static final class Visual {

        final SpacetimeEffects.Signal signal;
        final long born;
        final ChamberParticle particle;

        Visual(SpacetimeEffects.Signal s) {
            signal = s;
            born = clock;
            particle = new ChamberParticle(this);
            Minecraft.getMinecraft().effectRenderer.addEffect(particle);
        }
    }

    public static void register() {
        SpacetimeClient client = new SpacetimeClient();
        MinecraftForge.EVENT_BUS.register(client);
        FMLCommonHandler.instance()
            .bus()
            .register(client);
        SpacetimeEffects.receiver = s -> {
            if (QUEUED.incrementAndGet() > 128) {
                QUEUED.decrementAndGet();
                return;
            }
            QUEUE.add(s);
        };
    }

    public static void anchorRightClick() {
        if (Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU)) AnchorIntent.alt();
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getMinecraft();
        clock++;
        if (mc.theWorld != owner) {
            owner = mc.theWorld;
            for (Visual v : PORTALS.values()) v.particle.setDead();
            for (Visual v : BURSTS) v.particle.setDead();
            PORTALS.clear();
            BURSTS.clear();
        }
        SpacetimeEffects.Signal s;
        while ((s = QUEUE.poll()) != null) {
            QUEUED.decrementAndGet();
            if (mc.theWorld == null || mc.thePlayer == null
                || s.dimension != mc.thePlayer.dimension
                || mc.thePlayer.getDistanceSq(s.x, s.y, s.z) > 128 * 128) continue;
            if (s.kind == 2) {
                if (BURSTS.size() < 16) BURSTS.add(new Visual(s));
                continue;
            }
            String key = s.keyX + ":" + s.keyY + ":" + s.keyZ;
            Visual previous = PORTALS.get(key);
            if (s.kind == 0) {
                PORTALS.remove(key);
                if (previous != null) previous.particle.setDead();
            } else if (previous != null || PORTALS.size() < 32) {
                if (previous != null) previous.particle.setDead();
                PORTALS.put(key, new Visual(s));
            }
        }
        PORTALS.entrySet()
            .removeIf(
                entry -> clock - entry.getValue().born > 45 || mc.theWorld == null
                    || !mc.theWorld.blockExists(
                        entry.getValue().signal.keyX,
                        entry.getValue().signal.keyY,
                        entry.getValue().signal.keyZ));
        BURSTS.removeIf(v -> clock - v.born > 24);
    }

    private static void renderVisual(Visual visual, float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        Entity camera = mc.renderViewEntity;
        if (mc.theWorld == null || camera == null) return;
        double x = camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * partialTicks;
        double y = camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * partialTicks;
        double z = camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * partialTicks;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(-x, -y, -z);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_CULL_FACE);
            if (visual.signal.kind == 1) {
                Visual v = visual;
                SpacetimeEffects.Signal s = v.signal;
                if (camera.getDistanceSq(s.x, s.y, s.z) > 128 * 128) return;
                GL11.glPushMatrix();
                try {
                    GL11.glTranslated(s.x, s.y, s.z);
                    ForgeDirection f = ForgeDirection.getOrientation(s.front);
                    if (f.offsetX != 0) GL11.glRotated(90, 0, 1, 0);
                    else if (f.offsetY != 0) GL11.glRotated(90, 1, 0, 0);
                    double age = clock + partialTicks;
                    // The real TileSpacetimeSingularity alone renders the centre.
                    float warmup = s.preheatTicks > 0 ? .2F + .8F * s.preheatTicks / 600F : 1F;
                    GL11.glColor4f(.25F, .65F, 1F, .48F * warmup);
                    for (int i = 0; i < 24; i++) {
                        double a = i * Math.PI / 12 + age * .012;
                        CubeRuneParticle
                            .box(Math.cos(a) * 17.5, Math.sin(a) * 17.5, Math.sin(age * .035 + i) * .22, .18);
                    }
                } finally {
                    GL11.glPopMatrix();
                }
            }
            if (visual.signal.kind == 2) {
                Visual v = visual;
                double age = clock - v.born + partialTicks, fade = 1 - age / 24;
                GL11.glColor4f(.3F, .65F, 1F, (float) Math.max(0, fade * .7));
                for (int i = 0; i < 32; i++) {
                    double a = i * 2.399963229728653, vy = (i / 31D - .5) * 2,
                        rad = Math.sqrt(Math.max(0, 1 - vy * vy));
                    double radius = .3 + age * .12;
                    CubeRuneParticle.box(
                        v.signal.x + Math.cos(a) * rad * radius,
                        v.signal.y + .9 + vy * radius,
                        v.signal.z + Math.sin(a) * rad * radius,
                        .08 * fade);
                }
            }
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    /** Layer zero runs after opaque blocks and before translucent casing glass. */
    private static final class ChamberParticle extends EntityFX {

        private final Visual visual;

        ChamberParticle(Visual visual) {
            super(Minecraft.getMinecraft().theWorld, visual.signal.x, visual.signal.y, visual.signal.z, 0, 0, 0);
            this.visual = visual;
            particleMaxAge = visual.signal.kind == 2 ? 25 : 46;
        }

        @Override
        public int getFXLayer() {
            return 0;
        }

        @Override
        public void onUpdate() {
            if (++particleAge > particleMaxAge || worldObj != owner
                || (visual.signal.kind == 1 && !PORTALS.containsValue(visual))
                || (visual.signal.kind == 2 && !BURSTS.contains(visual))) setDead();
        }

        @Override
        public void renderParticle(Tessellator tess, float partialTicks, float rx, float rz, float ry, float rxz,
            float ryz) {
            if (isDead) return;
            // Flush the shared batch before immediate-mode cube geometry, then reopen it.
            tess.draw();
            try {
                renderVisual(visual, partialTicks);
            } finally {
                tess.startDrawingQuads();
            }
        }
    }
}
