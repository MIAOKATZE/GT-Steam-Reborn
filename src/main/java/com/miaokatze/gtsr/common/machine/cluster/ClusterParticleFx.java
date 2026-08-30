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
 * <h2>候选位（r9 精准 'e' 通道）</h2>
 * <ul>
 * <li>模块自身矩阵的 'e' 标记空气位：单元基类（{@link MTEClusterUnitBase}）在客户端 tick 一次性
 * 扫描 {@code getUnitShape()} 的 'e' 位经 {@link #registerAirCandidates} 注册，空列表（矩阵无
 * 'e'，如物流/洗矿/磁选/热离）跳过；热离/磁选的 P 能源位非空气、不进候选；</li>
 * <li>集群 'e' 精准候选：客户端按字节同步的延伸段数经
 * {@link ClusterStructureDef#clusterAirFxOffsets(int)} 推导主段+延伸段全部 'e' 位，经
 * {@link #registerClusterAirCandidates} 注册（同一 map 语义，重复注册以最后一次为准）。</li>
 * </ul>
 * 架构不强制每类模块必有候选位：无 'e' 即无该侧候选。
 *
 * <h2>窗口语义（默认关闭）</h2>
 * 结构未成型 / 预热 / 未执行时不喷粒子。唯一权威判据是主控字节同步的 {@code mWorkingForFX}
 * （getUpdateData bit0：结构正常工作四项判据——成型+开机+允许工作+供给锁存），主控 onPostTick
 * 客户端分支仅在 mWorkingForFX 为 true 时调用 {@link #spawnParticles}；单元侧另以
 * active（服务端 setActive(isUnitRunning) 同步）过滤，未运行模块不喷。
 *
 * <h2>世界换算</h2>
 * 候选位为模块/主控控制器相对坐标 {@code (x-offsetA, y-offsetB, z-offsetC)}，经各自
 * ExtendedFacing.getWorldOffset 旋转后叠加控制器世界坐标（偏移扫描思路对齐
 * MTELargeSolarOverpressureArray）。
 */
public final class ClusterParticleFx {

    /** 模块空气候选位：unit → 控制器相对偏移列表（客户端实例注册）。 */
    private static final Map<MTEClusterUnitBase, List<int[]>> unitAirCandidates = new ConcurrentHashMap<>();

    /** 集群 'e' 精准候选位：cluster → 控制器相对偏移列表（仅客户端注册；重复注册以最后一次为准）。 */
    private static final Map<MTESteamMineralLogisticsCluster, List<int[]>> clusterAirCandidates = new ConcurrentHashMap<>();

    private ClusterParticleFx() {}

    /** 模块侧（客户端 tick）注册自身 'e' 空气候选位；重复注册以最后一次为准。 */
    public static void registerAirCandidates(MTEClusterUnitBase unit, List<int[]> controllerRelativeOffsets) {
        if (unit == null) return;
        unitAirCandidates.put(unit, controllerRelativeOffsets);
    }

    /** 模块侧注销候选位（onRemoval 等）。 */
    public static void clearAirCandidates(MTEClusterUnitBase unit) {
        if (unit != null) unitAirCandidates.remove(unit);
    }

    /** 主控侧注册集群 'e' 精准候选位（控制器相对偏移，按延伸段数整体替换）。 */
    public static void registerClusterAirCandidates(MTESteamMineralLogisticsCluster cluster,
        List<int[]> controllerRelativeOffsets) {
        if (cluster == null) return;
        clusterAirCandidates.put(cluster, controllerRelativeOffsets);
    }

    /** 主控侧注销集群 'e' 精准候选位（checkMachine 复位段/断裂清理/onRemoval 配对）。 */
    public static void clearClusterAirCandidates(MTESteamMineralLogisticsCluster cluster) {
        if (cluster != null) clusterAirCandidates.remove(cluster);
    }

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
        List<int[]> clusterOffsets = clusterAirCandidates.get(cluster);
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
        Iterator<Map.Entry<MTESteamMineralLogisticsCluster, List<int[]>>> clusterIt = clusterAirCandidates.entrySet()
            .iterator();
        while (clusterIt.hasNext()) {
            MTESteamMineralLogisticsCluster cluster = clusterIt.next()
                .getKey();
            if (cluster.getBaseMetaTileEntity() == null || cluster.getBaseMetaTileEntity()
                .getWorld() != world) clusterIt.remove();
        }
        unitAirCandidates.keySet()
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
