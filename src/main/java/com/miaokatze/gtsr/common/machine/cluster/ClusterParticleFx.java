package com.miaokatze.gtsr.common.machine.cluster;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.world.World;

import com.gtnewhorizon.structurelib.util.Vec3Impl;

/**
 * 集群工作粒子 FX（仅客户端生效）：真实批执行窗口内，每 tick 从候选位池随机取一位喷一个上升
 * "cloud" 粒子。
 *
 * <h2>候选位</h2>
 * <ul>
 * <li>加工模块自身矩阵的严格空气位（F 与 '-'）：模块类在客户端 tick 一次性注册
 * {@link #registerAirCandidates}，热离/磁选的 P 能源位非空气、不进候选；</li>
 * <li>集群挂点中心：主控侧 {@link #registerMountCenters} 注册。</li>
 * </ul>
 * 架构不强制每类模块必有候选位：物流模块不注册即无粒子。
 *
 * <h2>窗口语义（默认关闭）</h2>
 * 结构未成型 / 预热 / 无真实批执行时不喷粒子。窗口由主控真实批执行驱动：
 * {@link #setParticleWindow(boolean)}（成型+满热+实际执行批开，否则关）；主控字节同步的
 * {@link #isFxWorking}「距最近真实批 &lt; 40t」判据同为放行条件，二者任一开窗即可。单元侧另以
 * active（服务端 setActive(isUnitRunning) 同步）过滤，未运行模块不喷。
 *
 * <h2>世界换算</h2>
 * 候选位为模块控制器相对坐标 {@code (x-offsetA, y-offsetB, z-offsetC)}，经模块自身
 * ExtendedFacing.getWorldOffset 旋转后叠加控制器世界坐标（偏移扫描思路对齐
 * MTELargeSolarOverpressureArray）。
 */
public final class ClusterParticleFx {

    /** 真实批窗口长度：距最近一次成功批 < 40t 视为工作态（结算节拍 20t 的双周期余量）。 */
    public static final long WORKING_WINDOW_TICKS = 40L;

    /** 主控真实批执行驱动的粒子开关窗口；默认关闭。 */
    private static volatile boolean particleWindowOpen = false;

    /** 模块空气候选位：unit → 控制器相对偏移列表（客户端实例注册）。 */
    private static final Map<MTEClusterUnitBase, List<int[]>> unitAirCandidates = new ConcurrentHashMap<>();

    /** 集群挂点中心候选位：cluster → 控制器相对偏移列表（主控侧注册）。 */
    private static final Map<MTESteamMineralLogisticsCluster, List<int[]>> clusterMountCenters = new ConcurrentHashMap<>();

    private ClusterParticleFx() {}

    /** 主控调用：真实批执行窗口开关（成型 + 满热 + 实际执行批时开，否则关）。 */
    public static void setParticleWindow(boolean open) {
        particleWindowOpen = open;
    }

    /** @return 显式窗口是否开启（不含 isFxWorking 判据）。 */
    public static boolean isParticleWindowOpen() {
        return particleWindowOpen;
    }

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
     * 真实批窗口判据（主控 getUpdateData 字节协议沿用）：距最近一次成功批 < 40t。
     */
    public static boolean isFxWorking(MTESteamMineralLogisticsCluster cluster) {
        if (cluster == null || cluster.getBaseMetaTileEntity() == null) return false;
        long lastBatch = cluster.getLastBatchServerTick();
        if (lastBatch == Long.MIN_VALUE) return false;
        return cluster.getBaseMetaTileEntity()
            .getTimer() - lastBatch < WORKING_WINDOW_TICKS;
    }

    /** @return 粒子窗口是否放行：显式窗口开，或主控真实批窗口（isFxWorking）开。 */
    public static boolean isParticleWindowOpen(MTESteamMineralLogisticsCluster cluster) {
        return particleWindowOpen || isFxWorking(cluster);
    }

    /**
     * 客户端入口（主控 onPostTick 客户端分支调用，签名沿用旧协议）：窗口放行时从候选位池
     * （active 单元的空气位 + 本主控挂点中心）随机取一位喷一个 cloud 粒子；窗口关或无候选位则无粒子。
     */
    public static void spawnParticles(MTESteamMineralLogisticsCluster cluster) {
        if (cluster == null || cluster.getBaseMetaTileEntity() == null) return;
        if (!isParticleWindowOpen(cluster)) return;
        World world = cluster.getBaseMetaTileEntity()
            .getWorld();
        if (world == null) return;
        pruneStaleUnits(world);
        int total = countActiveUnitCandidates(world) + mountCenterCount(cluster);
        if (total <= 0) return;
        int pick = world.rand.nextInt(total);
        if (trySpawnUnitCandidate(world, pick)) return;
        int[] off = clusterMountCenters.get(cluster)
            .get(pick - countActiveUnitCandidates(world));
        spawnOne(
            cluster.getBaseMetaTileEntity()
                .getXCoord(),
            cluster.getBaseMetaTileEntity()
                .getYCoord(),
            cluster.getBaseMetaTileEntity()
                .getZCoord(),
            cluster.getExtendedFacing()
                .getWorldOffset(new Vec3Impl(off[0], off[1], off[2])),
            world);
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

    /** 在世界坐标（控制器 + 朝向偏移）处喷一个上升 cloud 粒子（参数对齐既有 0.8 格抖动）。 */
    private static void spawnOne(int x, int y, int z, Vec3Impl worldOff, World world) {
        world.spawnParticle(
            "cloud",
            x + worldOff.get0() + 0.5D + (world.rand.nextDouble() - 0.5D) * 0.8D,
            y + worldOff.get1() + 0.5D,
            z + worldOff.get2() + 0.5D + (world.rand.nextDouble() - 0.5D) * 0.8D,
            0.0D,
            0.3D,
            0.0D);
    }
}
