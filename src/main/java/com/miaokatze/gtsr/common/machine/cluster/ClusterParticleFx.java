package com.miaokatze.gtsr.common.machine.cluster;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityCloudFX;
import net.minecraft.world.World;

import com.gtnewhorizon.structurelib.util.Vec3Impl;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * 集群工作粒子 FX（仅客户端生效）：主控工作态（mWorkingForFX 字节同步）期间，每 tick 独立取两位
 * 喷两个上升 "cloud" 粒子（密度×2），经 EntityCloudFX 缩放 0.5 的细颗粒。
 *
 * <h2>候选位</h2>
 * <ul>
 * <li>加工模块自身矩阵的 '-' 内腔空气位：模块类在客户端 tick 一次性注册
 * {@link #registerAirCandidates}，热离/磁选的 P 能源位非空气、不进候选；</li>
 * <li>集群挂点中心：双端各注册各自实例 key——服务端成型扫描（总控 checkMachine 收尾）、
 * 客户端惰性扫描（总控客户端 onPostTick 首次成型信号时）经 {@link #registerMountCenters} 注册。</li>
 * </ul>
 * 架构不强制每类模块必有候选位：物流模块不注册即无粒子。
 *
 * <h2>窗口语义（默认关闭）</h2>
 * 结构未成型 / 预热 / 未执行时不喷粒子。唯一权威判据是主控字节同步的 {@code mWorkingForFX}
 * （getUpdateData bit0：结构正常工作四项判据——成型+开机+允许工作+供给锁存），主控 onPostTick
 * 客户端分支仅在 mWorkingForFX 为 true 时调用 {@link #spawnParticles}；单元侧另以
 * active（服务端 setActive(isUnitRunning) 同步）过滤，未运行模块不喷。
 * 旧静态粒子窗（setParticleWindow/isParticleWindowOpen）与「距最近真实批 &lt; 40t」的
 * {@code isFxWorking} 判据已删除——bit0 改按结构正常工作口径直接服务端判定，无需静态跨实例窗口。
 *
 * <h2>世界换算</h2>
 * 候选位为模块控制器相对坐标 {@code (x-offsetA, y-offsetB, z-offsetC)}，经模块自身
 * ExtendedFacing.getWorldOffset 旋转后叠加控制器世界坐标（偏移扫描思路对齐
 * MTELargeSolarOverpressureArray）。
 */
public final class ClusterParticleFx {

    /** 模块空气候选位：unit → 控制器相对偏移列表（客户端实例注册）。 */
    private static final Map<MTEClusterUnitBase, List<int[]>> unitAirCandidates = new ConcurrentHashMap<>();

    /** 集群挂点中心候选位：cluster → 控制器相对偏移列表（双端各注册各自实例 key：服务端成型扫描、客户端惰性扫描）。 */
    private static final Map<MTESteamMineralLogisticsCluster, List<int[]>> clusterMountCenters = new ConcurrentHashMap<>();

    private ClusterParticleFx() {}

    /** 模块侧（客户端 tick）注册自身严格空气候选位；重复注册以最后一次为准。 */
    public static void registerAirCandidates(MTEClusterUnitBase unit, List<int[]> controllerRelativeOffsets) {
        if (unit == null) return;
        unitAirCandidates.put(unit, controllerRelativeOffsets);
    }

    /** 模块侧注销候选位（onRemoval 等）。 */
    public static void clearAirCandidates(MTEClusterUnitBase unit) {
        if (unit != null) unitAirCandidates.remove(unit);
    }

    /** 主控侧注册挂点中心候选位（控制器相对偏移）。 */
    public static void registerMountCenters(MTESteamMineralLogisticsCluster cluster,
        List<int[]> controllerRelativeOffsets) {
        if (cluster == null) return;
        clusterMountCenters.put(cluster, controllerRelativeOffsets);
    }

    /** 主控侧注销挂点中心候选位。 */
    public static void clearMountCenters(MTESteamMineralLogisticsCluster cluster) {
        if (cluster != null) clusterMountCenters.remove(cluster);
    }

    /**
     * 客户端入口（主控 onPostTick 客户端分支调用，调用方以 mWorkingForFX 为唯一工作态权威判据）：
     * 从候选位池（active 单元的空气位 + 本主控挂点中心）独立取两位各喷一个 cloud 粒子；无候选位则无粒子。
     */
    public static void spawnParticles(MTESteamMineralLogisticsCluster cluster) {
        if (cluster == null || cluster.getBaseMetaTileEntity() == null) return;
        World world = cluster.getBaseMetaTileEntity()
            .getWorld();
        if (world == null) return;
        pruneStaleUnits(world);
        List<Candidate> candidates = new ArrayList<>();
        for (Map.Entry<MTEClusterUnitBase, List<int[]>> entry : unitAirCandidates.entrySet()) {
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
        List<int[]> centers = clusterMountCenters.get(cluster);
        if (centers != null) {
            IGregTechTileEntity base = cluster.getBaseMetaTileEntity();
            for (int[] off : centers) {
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

    /** 清理基座已失效（区块卸载残留）的单元注册项。 */
    private static void pruneStaleUnits(World world) {
        Iterator<Map.Entry<MTESteamMineralLogisticsCluster, List<int[]>>> mountIt = clusterMountCenters.entrySet()
            .iterator();
        while (mountIt.hasNext()) {
            MTESteamMineralLogisticsCluster cluster = mountIt.next()
                .getKey();
            if (cluster.getBaseMetaTileEntity() == null || cluster.getBaseMetaTileEntity()
                .getWorld() != world) mountIt.remove();
        }
        unitAirCandidates.keySet()
            .removeIf(
                unit -> unit.getBaseMetaTileEntity() == null || unit.getBaseMetaTileEntity()
                    .getWorld() != world);
    }

    /** 本世界内 active 单元的空气候选位总数。 */
    private static int countActiveUnitCandidates(World world) {
        int count = 0;
        for (Map.Entry<MTEClusterUnitBase, List<int[]>> entry : unitAirCandidates.entrySet()) {
            if (isActiveUnit(entry.getKey(), world)) count += entry.getValue()
                .size();
        }
        return count;
    }

    private static int mountCenterCount(MTESteamMineralLogisticsCluster cluster) {
        List<int[]> centers = clusterMountCenters.get(cluster);
        return centers == null ? 0 : centers.size();
    }

    /**
     * 按随机序号 pick 尝试在某个 active 单元的候选位喷粒子；pick 落在单元候选区段内喷出并返回
     * true，否则（落在本主控挂点中心区段）返回 false 且不喷。
     */
    private static boolean trySpawnUnitCandidate(World world, int pick) {
        for (Map.Entry<MTEClusterUnitBase, List<int[]>> entry : unitAirCandidates.entrySet()) {
            List<int[]> offsets = entry.getValue();
            if (!isActiveUnit(entry.getKey(), world) || offsets.isEmpty()) continue;
            if (pick >= offsets.size()) {
                pick -= offsets.size();
                continue;
            }
            int[] off = offsets.get(pick);
            MTEClusterUnitBase unit = entry.getKey();
            spawnOne(
                unit.getBaseMetaTileEntity()
                    .getXCoord(),
                unit.getBaseMetaTileEntity()
                    .getYCoord(),
                unit.getBaseMetaTileEntity()
                    .getZCoord(),
                unit.getExtendedFacing()
                    .getWorldOffset(new Vec3Impl(off[0], off[1], off[2])),
                world);
            return true;
        }
        return false;
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
