package com.miaokatze.gtsr.common.machine.cluster;

import java.util.ArrayList;
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
        pruneStaleUnits(world);
        List<Candidate> candidates = new ArrayList<>();
        for (Map.Entry<MTEClusterUnitBase, List<int[]>> entry : ClusterParticleFx.unitAirCandidates.entrySet()) {
            if (!isActiveUnit(entry.getKey(), world)) continue;
            MTEClusterUnitBase unit = entry.getKey();
            IGregTechTileEntity base = unit.getBaseMetaTileEntity();
            if (base == null) continue;
            for (int[] off : entry.getValue()) {
                candidates.add(
                    new Candidate(
                        base.getXCoord(),
                        base.getYCoord(),
                        base.getZCoord(),
                        unit.getExtendedFacing()
                            .getWorldOffset(new Vec3Impl(off[0], off[1], off[2]))));
            }
        }
        List<int[]> clusterOffsets = ClusterParticleFx.clusterAirCandidates.get(cluster);
        if (clusterOffsets != null) {
            IGregTechTileEntity base = cluster.getBaseMetaTileEntity();
            for (int[] off : clusterOffsets) {
                candidates.add(
                    new Candidate(
                        base.getXCoord(),
                        base.getYCoord(),
                        base.getZCoord(),
                        cluster.getExtendedFacing()
                            .getWorldOffset(new Vec3Impl(off[0], off[1], off[2]))));
            }
        }
        if (candidates.isEmpty()) return;
        for (int i = 0; i < 2; i++) {
            Candidate candidate = candidates.get(world.rand.nextInt(candidates.size()));
            spawnOne(candidate.x, candidate.y, candidate.z, candidate.offset, world);
        }
    }

    private static final class Candidate {

        private final int x;
        private final int y;
        private final int z;
        private final Vec3Impl offset;

        private Candidate(int x, int y, int z, Vec3Impl offset) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.offset = offset;
        }
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
