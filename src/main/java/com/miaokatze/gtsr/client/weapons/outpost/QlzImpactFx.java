package com.miaokatze.gtsr.client.weapons.outpost;

/**
 * Adapted from GT-Outpost by MiaoKatze, AGPL-3.0-or-later.
 * Original algorithms retained; portable networking/pose replaces turret context.
 */
import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.World;

import org.lwjgl.opengl.GL11;

import com.miaokatze.gtsr.client.encounter.GlScope;

/** QLZ35mm: CombatEffectsClient.onImpact/drawBurst EXPLOSIVE -> SupportBlastMesh id2, actual radius2. */
public final class QlzImpactFx {

    private static final List<Impact> IMPACTS = new ArrayList<>();

    private static final class Impact {

        final double x, y, z;
        final long tick;
        final boolean ground;

        Impact(World w, double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
            tick = w.getTotalWorldTime();
            ground = groundSurface(w, x, y, z);
        }
    }

    public static void impact(World w, double x, double y, double z) {
        if (w == null || !w.isRemote) return;
        long now = w.getTotalWorldTime();
        for (Impact i : IMPACTS)
            if (now - i.tick < 16 && VisualOverlapPolicy.blastCovers(i.x, i.y, i.z, 2, x, y, z, 2)) return;
        if (IMPACTS.size() >= 32) IMPACTS.remove(0);
        IMPACTS.add(new Impact(w, x, y, z));
    }

    public static void reset() {
        IMPACTS.clear();
    }

    public static void render(World w, float partial) {
        double now = w.getTotalWorldTime() + partial;
        IMPACTS.removeIf(i -> now - i.tick >= SupportBlastMesh.impactLifetime(false, false) || now < i.tick);
        if (IMPACTS.isEmpty()) return;
        try (GlScope scope = new GlScope()) {
            GL11.glTranslated(
                -net.minecraft.client.renderer.entity.RenderManager.renderPosX,
                -net.minecraft.client.renderer.entity.RenderManager.renderPosY,
                -net.minecraft.client.renderer.entity.RenderManager.renderPosZ);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            for (Impact i : IMPACTS)
                SupportBlastMesh.renderImpact(false, i.x, i.y, i.z, (float) (now - i.tick), 2, 2, false, i.ground);
        }
    }

    public static boolean groundSurface(World world, double x, double y, double z) {
        return Double.isFinite(surfaceY(world, x, y, z));
    }

    public static double surfaceY(World world, double x, double y, double z) {
        if (world == null) return Double.NaN;
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        for (int by = (int) Math.floor(y); by >= (int) Math.floor(y - .8); by--) {
            if (!world.blockExists(bx, by, bz)) continue;
            net.minecraft.block.Block block = world.getBlock(bx, by, bz);
            net.minecraft.util.AxisAlignedBB box = block.getCollisionBoundingBoxFromPool(world, bx, by, bz);
            if (box != null && x >= box.minX
                && x <= box.maxX
                && z >= box.minZ
                && z <= box.maxZ
                && box.maxY <= y + .1
                && box.maxY >= y - .8) return box.maxY;
        }
        return Double.NaN;
    }
}
