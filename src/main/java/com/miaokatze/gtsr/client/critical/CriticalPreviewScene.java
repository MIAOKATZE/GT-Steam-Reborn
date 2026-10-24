package com.miaokatze.gtsr.client.critical;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.util.IIcon;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import com.miaokatze.gtsr.client.critical.CriticalMiniatureModel.Part;
import com.miaokatze.gtsr.client.critical.CriticalMiniatureModel.Surface;
import com.miaokatze.gtsr.common.critical.CriticalPreviewDescriptor;

/** Small authored silhouettes, with no relationship to the dynamic-only world TESR mesh. */
final class CriticalPreviewScene {

    private static final FloatBuffer ATLAS_WEIGHT = BufferUtils.createFloatBuffer(4);
    private final CriticalMiniatureModel model;
    private final List<Mesh> meshes = new ArrayList<>();
    private boolean prepared;
    private double visualSeconds, lastSeconds = Double.NaN;
    boolean failed, ready;
    final double[] projectedLow, projectedHigh;

    CriticalPreviewScene(CriticalPreviewDescriptor descriptor) {
        model = CriticalMiniatureModel.create(descriptor.layout.kind);
        projectedLow = model.projectedLow;
        projectedHigh = model.projectedHigh;
    }

    /** Cold first call yields to the readable 2D fallback; second call uploads this small bounded study once. */
    void prepare() {
        if (ready || failed) return;
        if (!prepared) {
            prepared = true;
            return;
        }
        for (Part part : model.parts) {
            int list = GL11.glGenLists(1);
            if (list == 0) throw new IllegalStateException("Miniature list unavailable");
            meshes.add(new Mesh(list, part));
            GL11.glNewList(list, GL11.GL_COMPILE);
            try {
                GL11.glBegin(GL11.GL_QUADS);
                try {
                    for (float[] quad : part.quads) emit(quad, part.surface);
                } finally {
                    GL11.glEnd();
                }
            } finally {
                GL11.glEndList();
            }
        }
        meshes.sort(Comparator.comparingDouble(m -> depth(m.part)));
        ready = true;
    }

    private static void emit(float[] q, Surface surface) {
        IIcon icon = surface.emission ? null : CriticalMaterialTextures.icon(surface.atlas, 1, 0, false, true);
        if (!surface.emission && icon == null)
            throw new IllegalStateException("Miniature atlas missing: " + surface.atlas);
        float ax = q[3] - q[0], ay = q[4] - q[1], az = q[5] - q[2], bx = q[6] - q[0], by = q[7] - q[1],
            bz = q[8] - q[2];
        double nx = az * by - ay * bz, ny = ax * bz - az * bx, nz = ay * bx - ax * by;
        double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length > .00001) GL11.glNormal3d(nx / length, ny / length, nz / length);
        for (int v = 0; v < 4; v++) {
            // Sample a quiet central region of the existing atlas, not a noisy repeated 16px voxel tile.
            if (icon != null) GL11.glTexCoord2d(
                icon.getInterpolatedU(v == 0 || v == 3 ? 6.5 : 9.5),
                icon.getInterpolatedV(v < 2 ? 6.5 : 9.5));
            GL11.glVertex3f(q[v * 3], q[v * 3 + 1], q[v * 3 + 2]);
        }
    }

    double radius() {
        return 24;
    }

    void draw(CriticalPreviewDescriptor descriptor, double seconds, boolean reducedMotion) {
        if (!Double.isFinite(seconds)) seconds = 0;
        if (!Double.isNaN(lastSeconds) && !reducedMotion && !descriptor.paused)
            visualSeconds += Math.max(0, Math.min(.1, seconds - lastSeconds));
        lastSeconds = seconds;
        // Keep broad copper/steel colors; existing atlas detail contributes only a subtle material variation.
        ATLAS_WEIGHT.clear();
        ATLAS_WEIGHT.put(new float[] { .15F, .15F, .15F, .15F })
            .flip();
        GL11.glTexEnv(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_COLOR, ATLAS_WEIGHT);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL13.GL_COMBINE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_RGB, GL13.GL_INTERPOLATE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE0_RGB, GL11.GL_TEXTURE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE1_RGB, GL13.GL_PRIMARY_COLOR);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE2_RGB, GL13.GL_CONSTANT);
        for (int operand : new int[] { GL13.GL_OPERAND0_RGB, GL13.GL_OPERAND1_RGB, GL13.GL_OPERAND2_RGB })
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, operand, GL11.GL_SRC_COLOR);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_ALPHA, GL11.GL_REPLACE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE0_ALPHA, GL13.GL_PRIMARY_COLOR);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND0_ALPHA, GL11.GL_SRC_ALPHA);
        for (int pass = 0; pass < 2; pass++) for (Mesh mesh : meshes) {
            Part part = mesh.part;
            boolean complete = descriptor.demonstration || part.stage <= descriptor.completedStage;
            if ((complete ? 0 : 1) != pass) continue;
            boolean current = part.stage == descriptor.currentStage;
            GL11.glDepthMask(complete);
            if (complete) GL11.glDisable(GL11.GL_BLEND);
            else {
                GL11.glEnable(GL11.GL_BLEND);
                OpenGlHelper.glBlendFunc(
                    GL11.GL_SRC_ALPHA,
                    GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE,
                    GL11.GL_ONE_MINUS_SRC_ALPHA);
            }
            if (part.surface.emission) {
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL11.glDisable(GL11.GL_LIGHTING);
            } else {
                GL11.glEnable(GL11.GL_TEXTURE_2D);
                GL11.glEnable(GL11.GL_LIGHTING);
            }
            Surface c = part.surface;
            if (complete) GL11.glColor4f(c.r, c.g, c.b, 1);
            else if (current) GL11.glColor4f(1, .73F, .36F, (float) (.42 + descriptor.stageProgress * .2));
            else GL11.glColor4f(.33F, .42F, .48F, .16F);
            GL11.glPushMatrix();
            try {
                pose(part, complete ? visualSeconds : 0);
                GL11.glCallList(mesh.list);
            } finally {
                GL11.glPopMatrix();
            }
        }
    }

    private static void pose(Part p, double seconds) {
        GL11.glTranslated(p.x, p.y, p.z);
        double a = seconds * p.speed + p.phase;
        switch (p.motion) {
            case ROTATE_X -> GL11.glRotated(Math.toDegrees(a), 1, 0, 0);
            case ROTATE_Y -> GL11.glRotated(Math.toDegrees(a), 0, 1, 0);
            case ROCK_Z -> GL11.glRotated(Math.toDegrees(Math.sin(a) * p.amplitude), 0, 0, 1);
            case PISTON_Y -> GL11.glTranslated(0, Math.sin(a) * p.amplitude, 0);
            case FLOW_Y -> GL11.glTranslated(0, ((a / (Math.PI * 2)) % 1 + 1) % 1 * p.amplitude, 0);
            case BREATHE -> {
                double s = 1 + Math.sin(a) * p.amplitude;
                GL11.glScaled(s, s, s);
            }
            default -> {}
        }
    }

    private static double depth(Part p) {
        return p.x * .406 + p.y * .375 + p.z * .833;
    }

    int listCount() {
        return meshes.size();
    }

    int faceCount() {
        return model.faces;
    }

    double motionSeconds() {
        return visualSeconds;
    }

    void fail() {
        failed = true;
        close();
    }

    void close() {
        for (Mesh mesh : meshes) GL11.glDeleteLists(mesh.list, 1);
        meshes.clear();
    }

    private static final class Mesh {

        final int list;
        final Part part;

        Mesh(int list, Part part) {
            this.list = list;
            this.part = part;
        }
    }
}
