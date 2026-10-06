package com.miaokatze.gtsr.client.weapons.outpost;

/**
 * Adapted from GT-Outpost by MiaoKatze, AGPL-3.0-or-later.
 * Original algorithms retained; portable networking/pose replaces turret context.
 */

import org.lwjgl.opengl.GL11;

/** Small exposed-face voxel clouds: depth-tested volume with Minecraft-like square silhouettes. */
public final class PixelBlastMesh {

    private PixelBlastMesh() {}

    public enum Kind {
        FIREBALL,
        SMOKE,
        DEBRIS,
        GROUND_DUST,
        ENERGY
    }

    /** Shared typed entry: material/lifetime belongs to the payload caller; dust requires a real surface. */
    public static void particle(Kind kind, double x, double y, double z, double rx, double ry, double rz, float red,
        float green, float blue, float alpha, int seed, double floorY) {
        if (kind == null || kind == Kind.GROUND_DUST && !Double.isFinite(floorY)) return;
        cloud(x, y, z, rx, ry, rz, red, green, blue, alpha, seed, floorY);
    }

    public static void cloud(double x, double y, double z, double rx, double ry, double rz, float red, float green,
        float blue, float alpha, int seed) {
        cloud(x, y, z, rx, ry, rz, red, green, blue, alpha, seed, Double.NEGATIVE_INFINITY);
    }

    public static void cloud(double x, double y, double z, double rx, double ry, double rz, float red, float green,
        float blue, float alpha, int seed, double floorY) {
        if (alpha <= 0 || rx <= 0 || ry <= 0 || rz <= 0) return;
        // Only exterior faces are emitted. Interior faces would multiply opacity across every cell.
        GL11.glBegin(GL11.GL_QUADS);
        for (int iy = -2; iy <= 2; iy++) for (int iz = -2; iz <= 2; iz++) for (int ix = -2; ix <= 2; ix++) {
            if (!occupied(ix, iy, iz, seed) || y + (iy + .5) * ry / 2.5 <= floorY) continue;
            for (int face = 0; face < 6; face++) {
                int axis = face / 2, sign = face % 2 == 0 ? -1 : 1;
                if (occupied(
                    ix + (axis == 0 ? sign : 0),
                    iy + (axis == 1 ? sign : 0),
                    iz + (axis == 2 ? sign : 0),
                    seed)) continue;
                float shade = axis == 1 ? (sign > 0 ? 1F : .73F) : axis == 0 ? .86F : .94F;
                shade *= .95F + .025F * Math.floorMod(ix * 7 + iy * 3 + iz * 11 + seed, 3);
                GL11.glColor4f(red * shade, green * shade, blue * shade, alpha);
                for (int corner = 0; corner < 4; corner++) {
                    double u = corner == 0 || corner == 3 ? -.5 : .5;
                    double v = corner < 2 ? -.5 : .5;
                    double dx = ix + (axis == 0 ? sign * .5 : u);
                    double dy = iy + (axis == 1 ? sign * .5 : axis == 0 ? u : v);
                    double dz = iz + (axis == 2 ? sign * .5 : v);
                    GL11.glVertex3d(x + dx * rx / 2.5, Math.max(floorY, y + dy * ry / 2.5), z + dz * rz / 2.5);
                }
            }
        }
        GL11.glEnd();
    }

    private static boolean occupied(int x, int y, int z, int seed) {
        if (Math.abs(x) > 2 || Math.abs(y) > 2 || Math.abs(z) > 2) return false;
        int shell = x * x + y * y + z * z;
        return shell <= 5 || shell <= 6 && Math.floorMod(x * 13 + y * 7 + z * 19 + seed, 4) != 0;
    }

    private static void block(double x, double y, double z, double half, float alpha) {
        GL11.glBegin(GL11.GL_QUADS);
        for (int face = 0; face < 6; face++) {
            int axis = face / 2, sign = face % 2 == 0 ? -1 : 1;
            float shade = axis == 1 ? (sign > 0 ? 1F : .75F) : .90F;
            GL11.glColor4f(.92F * shade, .91F * shade, .87F * shade, alpha);
            for (int corner = 0; corner < 4; corner++) {
                double u = corner == 0 || corner == 3 ? -half : half;
                double v = corner < 2 ? -half : half;
                GL11.glVertex3d(
                    x + (axis == 0 ? sign * half : u),
                    y + (axis == 1 ? sign * half : axis == 0 ? u : v),
                    z + (axis == 2 ? sign * half : v));
            }
        }
        GL11.glEnd();
    }

    /** Discrete square dust chips, sized in world blocks and bounded by the real payload footprint. */
    public static void particles(double x, double y, double z, double radius, float age, int seed) {
        if (age < 0 || age >= 110 || radius <= 0) return;
        double q = Math.max(0, (age - 45) / 65D);
        float alpha = (float) ((1 - q * q * (3 - 2 * q)) * .28);
        for (int i = 0; i < 40; i++) {
            double angle = i * 2.39996 + seed * .37;
            double travel = Math.min(radius * .86, (1 - Math.exp(-age / 22D)) * radius * (.42 + (i % 4) * .12));
            double height = .12 + Math.max(0, age - 18) * (.008 + (i % 3) * .004);
            double half = Math.max(.055, Math.min(.20, radius * .009)) * (1 - age / 160D);
            block(x + Math.cos(angle) * travel, y + height, z + Math.sin(angle) * travel, half, alpha);
        }
    }
}
