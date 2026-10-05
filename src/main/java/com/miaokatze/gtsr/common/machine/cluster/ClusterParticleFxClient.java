package com.miaokatze.gtsr.common.machine.cluster;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityCloudFX;
import net.minecraft.world.World;

import com.gtnewhorizon.structurelib.util.Vec3Impl;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * 集群工作粒子 FX 客户端渲染入口（主控 onPostTick 客户端分支调用，调用方以 mWorkingForFX 为唯一
 * 工作态权威判据）：从 {@link ClusterParticleFx} 候选位池（active 单元的 'e' 位 + 本主控集群 'e'
 * 精准候选）独立取两位各喷一个上升 cloud 粒子（EntityCloudFX 经 multipleParticleScaleBy(0.5F)
 * 细颗粒；+0.5 居中、0.8 格抖动、vy=0.3）；无候选位则无粒子。集群级候选不按 active 门控（调用方
 * 门控不变），单元级候选仍按 active 过滤。
 *
 * <p>
 * 分离原因（服务端类加载安全）：J8 校验器在类加载期解析全部被引用类，客户端类（Minecraft/
 * EntityCloudFX）混入公共登记簿会使专用服务器任何 register/clear 调用触发整类校验并抛
 * {@code NoClassDefFoundError: net/minecraft/client/particle/EntityFX}（服务器 tick 崩溃）；
 * 渲染侧独立成类后，登记簿在服务端不再触达任何客户端类。本类仅客户端调用路径可达。
 */
public final class ClusterParticleFxClient {

    private ClusterParticleFxClient() {}

    private static WeakReference<World> lastPrunedWorld = new WeakReference<>(null);
    private static long lastPruneTick;

    /**
     * 客户端入口（主控 onPostTick 客户端分支调用，调用方以 mWorkingForFX 为唯一工作态权威判据）：
     * 从候选位池（active 单元的 'e' 位 + 本主控集群 'e' 精准候选）独立取两位各喷一个 cloud 粒子；
     * 无候选位则无粒子。集群级候选不按 active 门控（调用方门控不变），单元级候选仍按 active 过滤。
     */
    public static void spawnParticles(MTESteamMineralLogisticsCluster cluster) {
        if (cluster == null || cluster.getBaseMetaTileEntity() == null) return;
        World world = cluster.getBaseMetaTileEntity()
            .getWorld();
        if (world == null) return;
        long tick = world.getTotalWorldTime();
        if (lastPrunedWorld.get() != world || tick < lastPruneTick || tick - lastPruneTick >= 20) {
            pruneStaleUnits(world);
            lastPrunedWorld = new WeakReference<>(world);
            lastPruneTick = tick;
        }
        int count = 0;
        for (Map.Entry<MTEClusterUnitBase, List<int[]>> entry : ClusterParticleFx.unitAirCandidates.entrySet()) {
            if (isActiveUnit(entry.getKey(), world)) count += entry.getValue()
                .size();
        }
        List<int[]> clusterOffsets = ClusterParticleFx.clusterAirCandidates.get(cluster);
        if (clusterOffsets != null) count += clusterOffsets.size();
        if (count == 0) return;
        // Sample the same uniform pool with replacement; transform only the two selected offsets.
        int first = world.rand.nextInt(count);
        int second = world.rand.nextInt(count);
        for (Map.Entry<MTEClusterUnitBase, List<int[]>> entry : ClusterParticleFx.unitAirCandidates.entrySet()) {
            MTEClusterUnitBase unit = entry.getKey();
            if (!isActiveUnit(unit, world)) continue;
            List<int[]> offsets = entry.getValue();
            if (first >= 0 && first < offsets.size()) {
                spawnUnitOffset(unit, offsets.get(first), world);
                first = -1;
            }
            if (second >= 0 && second < offsets.size()) {
                spawnUnitOffset(unit, offsets.get(second), world);
                second = -1;
            }
            if (first < 0 && second < 0) return;
            if (first >= 0) first -= offsets.size();
            if (second >= 0) second -= offsets.size();
        }
        if (clusterOffsets != null) {
            if (first >= 0 && first < clusterOffsets.size())
                spawnClusterOffset(cluster, clusterOffsets.get(first), world);
            if (second >= 0 && second < clusterOffsets.size()) {
                spawnClusterOffset(cluster, clusterOffsets.get(second), world);
            }
        }
    }

    private static void spawnUnitOffset(MTEClusterUnitBase unit, int[] off, World world) {
        IGregTechTileEntity base = unit.getBaseMetaTileEntity();
        spawnOne(
            base.getXCoord(),
            base.getYCoord(),
            base.getZCoord(),
            unit.getExtendedFacing()
                .getWorldOffset(new Vec3Impl(off[0], off[1], off[2])),
            world);
    }

    private static void spawnClusterOffset(MTESteamMineralLogisticsCluster cluster, int[] off, World world) {
        IGregTechTileEntity base = cluster.getBaseMetaTileEntity();
        spawnOne(
            base.getXCoord(),
            base.getYCoord(),
            base.getZCoord(),
            cluster.getExtendedFacing()
                .getWorldOffset(new Vec3Impl(off[0], off[1], off[2])),
            world);
    }

    /** 清理基座已失效（区块卸载残留）的单元/主控注册项。 */
    private static void pruneStaleUnits(World world) {
        Iterator<Map.Entry<MTESteamMineralLogisticsCluster, List<int[]>>> clusterIt = ClusterParticleFx.clusterAirCandidates
            .entrySet()
            .iterator();
        while (clusterIt.hasNext()) {
            MTESteamMineralLogisticsCluster cluster = clusterIt.next()
                .getKey();
            if (cluster.getBaseMetaTileEntity() == null || cluster.getBaseMetaTileEntity()
                .getWorld() != world) clusterIt.remove();
        }
        ClusterParticleFx.unitAirCandidates.keySet()
            .removeIf(
                unit -> unit.getBaseMetaTileEntity() == null || unit.getBaseMetaTileEntity()
                    .getWorld() != world);
    }

    /** 单元存活且本 tick active（服务端 setActive(isUnitRunning) 的客户端同步）才参与候选。 */
    private static boolean isActiveUnit(MTEClusterUnitBase unit, World world) {
        return unit.getBaseMetaTileEntity() != null && unit.getBaseMetaTileEntity()
            .getWorld() == world
            && unit.getBaseMetaTileEntity()
                .isActive();
    }

    /**
     * 在世界坐标（控制器 + 朝向偏移）处喷一个上升 cloud 粒子（EntityCloudFX 经
     * multipleParticleScaleBy(0.5F) 细颗粒；+0.5 居中、0.8 格抖动、vy=0.3 语义不变），
     * 加入客户端 EffectRenderer（仅客户端调用路径触达）。
     */
    private static void spawnOne(int x, int y, int z, Vec3Impl worldOff, World world) {
        EntityCloudFX fx = new EntityCloudFX(
            world,
            x + worldOff.get0() + 0.5D + (world.rand.nextDouble() - 0.5D) * 0.8D,
            y + worldOff.get1() + 0.5D,
            z + worldOff.get2() + 0.5D + (world.rand.nextDouble() - 0.5D) * 0.8D,
            0.0D,
            0.3D,
            0.0D);
        fx.multipleParticleScaleBy(0.5F);
        Minecraft.getMinecraft().effectRenderer.addEffect(fx);
    }
}
