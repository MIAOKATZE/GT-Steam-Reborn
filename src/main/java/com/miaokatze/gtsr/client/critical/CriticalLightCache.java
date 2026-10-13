package com.miaokatze.gtsr.client.critical;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.miaokatze.gtsr.common.critical.CriticalStructureAlignment;

/** Small native lightmap samples per rigid group, never a voxel scan or a relight request. */
final class CriticalLightCache {

    static final int QUERY_LIMIT_PER_TICK = 16;
    private final Map<String, Sample> groups = new HashMap<>();
    private final ArrayDeque<Sample> pending = new ArrayDeque<>();
    private long budgetTick = Long.MIN_VALUE;
    private int used;

    int sample(World world, String key, double[][] positions, double[] pose, int originX, int originY, int originZ,
        int fallback) {
        return sample(world, key, positions, pose, (double) originX, originY, originZ, ForgeDirection.NORTH, fallback);
    }

    int sample(World world, String key, double[][] positions, double[] pose, double originX, double originY,
        double originZ, ForgeDirection facing, int fallback) {
        long now = world.getTotalWorldTime();
        if (now != budgetTick) {
            budgetTick = now;
            used = 0;
        }
        Sample cached = groups.computeIfAbsent(key, ignored -> new Sample());
        if (cached.facing != facing || cached.originX != originX
            || cached.originY != originY
            || cached.originZ != originZ) {
            cached.valid = false;
            cached.nextTick = Long.MIN_VALUE;
        }
        cached.positions = positions;
        cached.pose = pose;
        cached.originX = originX;
        cached.originY = originY;
        cached.originZ = originZ;
        cached.facing = facing;
        if (now >= cached.nextTick && !cached.queued) {
            cached.queued = true;
            pending.addLast(cached);
        }
        // FIFO prevents late meshes from starving behind repeatedly refreshed early meshes.
        while (!pending.isEmpty() && used + pending.peekFirst().positions.length <= QUERY_LIMIT_PER_TICK) {
            Sample next = pending.removeFirst();
            next.queued = false;
            refresh(world, next, now);
        }
        CriticalRenderMetrics.lastWorldLightSamples = used;
        return cached.valid ? cached.light : fallback;
    }

    private void refresh(World world, Sample cached, long now) {
        int sky = 0, block = 0;
        boolean loaded = false;
        double[] pose = cached.pose;
        for (double[] p : cached.positions) {
            int[] coordinates = sampleBlock(p, pose, cached.originX, cached.originY, cached.originZ, cached.facing);
            int x = coordinates[0], y = coordinates[1], z = coordinates[2];
            used++;
            if (y < 0 || y >= world.getHeight() || !world.blockExists(x, y, z)) continue;
            int light = world.getLightBrightnessForSkyBlocks(x, y, z, 0);
            CriticalRenderMetrics.nativeLightQueries++;
            loaded = true;
            block = Math.max(block, light & 65535);
            sky = Math.max(sky, light >>> 16);
        }
        if (loaded) {
            cached.light = block | sky << 16;
            cached.valid = true;
        }
        cached.nextTick = now + 4;
    }

    static int[] sampleBlock(double[] p, double[] pose, double originX, double originY, double originZ,
        ForgeDirection facing) {
        double x = pose[0] * p[0] + pose[4] * p[1] + pose[8] * p[2] + pose[12];
        double y = pose[1] * p[0] + pose[5] * p[1] + pose[9] * p[2] + pose[13];
        double z = pose[2] * p[0] + pose[6] * p[1] + pose[10] * p[2] + pose[14];
        return new int[] { (int) Math.floor(originX + CriticalStructureAlignment.rotateX(x, z, facing)),
            (int) Math.floor(originY + y),
            (int) Math.floor(originZ + CriticalStructureAlignment.rotateZ(x, z, facing)) };
    }

    private static final class Sample {

        long nextTick = Long.MIN_VALUE;
        int light;
        boolean valid;
        boolean queued;
        double[][] positions;
        double[] pose;
        double originX, originY, originZ;
        ForgeDirection facing;
    }
}
