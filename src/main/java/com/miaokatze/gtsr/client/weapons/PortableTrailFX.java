package com.miaokatze.gtsr.client.weapons;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayDeque;
import java.util.Deque;

import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

import com.miaokatze.gtsr.client.encounter.GlScope;
import com.miaokatze.gtsr.client.weapons.outpost.GatlingFxProfile;
import com.miaokatze.gtsr.common.weapons.EntityWeaponProjectile;

/** Full flight segments form a continuous, feathered gray ribbon, independent of projectile speed. */
final class PortableTrailFX {

    private static final int LIFE = 12, MAX_SEGMENTS = 2048;
    private static final Deque<Segment> SEGMENTS = new ArrayDeque<>();
    private static World owner;
    private static final FloatBuffer MODELVIEW = BufferUtils.createFloatBuffer(16),
        PROJECTION = BufferUtils.createFloatBuffer(16), CAMERA = BufferUtils.createFloatBuffer(3);
    private static final IntBuffer VIEWPORT = BufferUtils.createIntBuffer(16);

    private static final class Segment {

        final double x, y, z, dx, dy, dz;
        int age;

        Segment(Entity projectile) {
            EntityWeaponProjectile entity = (EntityWeaponProjectile) projectile;
            Vec3 start = PortableProjectileRenderer.visualPoint(entity, 0);
            Vec3 end = PortableProjectileRenderer.visualPoint(entity, 1);
            x = start.xCoord;
            y = start.yCoord;
            z = start.zCoord;
            dx = end.xCoord - x;
            dy = end.yCoord - y;
            dz = end.zCoord - z;
        }
    }

    static void reset() {
        SEGMENTS.clear();
        owner = null;
    }

    static void tick(World world) {
        if (owner != world) {
            reset();
            owner = world;
        }
        for (Segment segment : SEGMENTS) segment.age++;
        while (!SEGMENTS.isEmpty() && SEGMENTS.peekFirst().age >= LIFE) SEGMENTS.removeFirst();
    }

    static void emit(Entity projectile) {
        Segment segment = new Segment(projectile);
        if (segment.dx * segment.dx + segment.dy * segment.dy + segment.dz * segment.dz < 1e-8) return;
        if (SEGMENTS.size() >= MAX_SEGMENTS) SEGMENTS.removeFirst();
        SEGMENTS.addLast(segment);
    }

    static void render(World world, float partial) {
        if (owner != world || SEGMENTS.isEmpty()) return;
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MODELVIEW);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, PROJECTION);
        GL11.glGetInteger(GL11.GL_VIEWPORT, VIEWPORT);
        // The player position is not the camera in third person. Unproject the actual near-plane center.
        if (!GLU.gluUnProject(
            VIEWPORT.get(0) + VIEWPORT.get(2) * .5F,
            VIEWPORT.get(1) + VIEWPORT.get(3) * .5F,
            0,
            MODELVIEW,
            PROJECTION,
            VIEWPORT,
            CAMERA)) return;
        double cx = CAMERA.get(0) + RenderManager.renderPosX, cy = CAMERA.get(1) + RenderManager.renderPosY,
            cz = CAMERA.get(2) + RenderManager.renderPosZ;
        try (GlScope scope = new GlScope()) {
            GL11.glTranslated(-RenderManager.renderPosX, -RenderManager.renderPosY, -RenderManager.renderPosZ);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDepthMask(false);
            Tessellator tess = Tessellator.instance;
            tess.startDrawing(GL11.GL_TRIANGLES);
            for (Segment s : SEGMENTS) {
                float phase = Math.min(1, (s.age + partial) / LIFE);
                float alpha = GatlingFxProfile.TRAIL_STREAK_ALPHA0 * (1 - phase) * (1 - phase);
                double vx = cx - (s.x + s.dx * .5), vy = cy - (s.y + s.dy * .5), vz = cz - (s.z + s.dz * .5);
                double rx = s.dy * vz - s.dz * vy, ry = s.dz * vx - s.dx * vz, rz = s.dx * vy - s.dy * vx;
                double length = Math.sqrt(rx * rx + ry * ry + rz * rz);
                if (length < 1e-8) continue;
                double width = .09 + .06 * phase;
                rx *= width / length;
                ry *= width / length;
                rz *= width / length;
                double rise = .025 * phase;
                // Vertex alpha feathers both edges without a square sprite or cube.
                for (int band = 0; band < 8; band++) {
                    double a = -1 + band * .25, b = a + .25;
                    float aa = alpha * (float) (1 - a * a), ab = alpha * (float) (1 - b * b);
                    vertex(tess, s, 0, a, rx, ry, rz, rise, aa);
                    vertex(tess, s, 1, a, rx, ry, rz, rise, aa);
                    vertex(tess, s, 1, b, rx, ry, rz, rise, ab);
                    vertex(tess, s, 0, a, rx, ry, rz, rise, aa);
                    vertex(tess, s, 1, b, rx, ry, rz, rise, ab);
                    vertex(tess, s, 0, b, rx, ry, rz, rise, ab);
                }
            }
            tess.draw();
        }
    }

    private static void vertex(Tessellator tess, Segment s, int end, double edge, double rx, double ry, double rz,
        double rise, float alpha) {
        tess.setColorRGBA_F(
            GatlingFxProfile.TRAIL_STREAK_R,
            GatlingFxProfile.TRAIL_STREAK_G,
            GatlingFxProfile.TRAIL_STREAK_B,
            alpha);
        tess.addVertex(s.x + s.dx * end + rx * edge, s.y + s.dy * end + ry * edge + rise, s.z + s.dz * end + rz * edge);
    }
}
