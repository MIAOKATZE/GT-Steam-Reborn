package com.miaokatze.gtsr.common.dimension.prosperity.echo;

/** Immutable attack footprint shared by server predicates and client drawings. */
public final class CombatGeometry {

    public static final int CIRCLE = 0, RING = 1, LINE = 2, CONE = 3, POINT = 4, CHAIN = 5;
    public final int shape;
    public final double x, y, z, tx, tz, radius, inner;

    public CombatGeometry(int shape, double x, double y, double z, double tx, double tz, double radius, double inner) {
        this.shape = shape;
        this.x = x;
        this.y = y;
        this.z = z;
        this.tx = tx;
        this.tz = tz;
        this.radius = radius;
        this.inner = inner;
    }

    /** Nearest loaded collision surface below the locked point; never requests a chunk. */
    public static double groundY(net.minecraft.world.World world, double x, double y, double z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        if (!world.getChunkProvider()
            .chunkExists(bx >> 4, bz >> 4)) return y;
        for (int by = Math.min(255, (int) Math.floor(y - .01)); by >= Math.max(0, (int) Math.floor(y) - 8); by--) {
            net.minecraft.util.AxisAlignedBB box = world.getBlock(bx, by, bz)
                .getCollisionBoundingBoxFromPool(world, bx, by, bz);
            if (box != null) return box.maxY;
        }
        return y;
    }

    public boolean contains(double px, double py, double pz) {
        if (Math.abs(py - y) > 5) return false;
        double dx = px - x, dz = pz - z, d2 = dx * dx + dz * dz;
        if (shape == LINE || shape == CHAIN) {
            double ax = tx - x, az = tz - z, len = Math.max(.001, Math.sqrt(ax * ax + az * az));
            double along = (dx * ax + dz * az) / len;
            return along >= 0 && along <= len && Math.abs(dx * az - dz * ax) / len <= radius;
        }
        if (d2 > radius * radius || d2 < inner * inner) return false;
        if (shape == CONE) {
            double ax = tx - x, az = tz - z, len = Math.max(.001, Math.sqrt(ax * ax + az * az));
            return (dx * ax + dz * az) / len >= Math.sqrt(d2) * .70710678118;
        }
        return true;
    }
}
