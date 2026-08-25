package com.miaokatze.gtsr.common.machine.cluster;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * 集群总控 NBT 编解码器（独立静态工具类）。
 *
 * <p>
 * 职责：总控 {@link MTESteamMineralLogisticsCluster} 的 {@code saveNBTData}/{@code loadNBTData}
 * 委托本类完成集群运行态的对称读写。范式与 {@code MTESingularityDrillingHub} 的
 * {@code BoundDrillNode} 一致——只持久化坐标句柄，不持久化活引用。
 *
 * <p>
 * 读语义：旧档缺键一律安全跳过；读后不主动 connect，世界重载后的重连由总控 tick 周期任务按
 * {@code setPendingReconnectHints} 回填的句柄清单进行（本类不做重连，重连节流参照
 * {@code MTESingularityDrillingHub#resolveWorkerNode}）。结构 tier 与段数为纯记账键，载入不恢复
 * 成型——结构成型由重检（{@code checkMachine}）完成。
 *
 * <p>
 * 边界：物流单元自身的链配置持久化在单元 {@code saveNBTData}（另一切片负责），本类不碰。
 * 全部 NBT 键位于 cluster 前缀命名空间内；预热进度经 {@link ClusterPreheatController}
 * 的 {@code writeToNBT}/{@code readFromNBT} 委托（该类自带键 "clusterPreheat"）。
 */
public final class ClusterPersistence {

    /** 开关机位（关机时集群停产但保留预热衰减，M3 批接 GUI ToggleButton）。 */
    private static final String KEY_ENABLED = "clusterEnabled";

    /** 结构 tier 记账（0-3，未成型/混拼为 -1；载入仅供参考，不恢复成型）。 */
    private static final String KEY_TIER = "clusterTier";

    /** 段数记账（[1,10]，载入仅供参考，段划分由重检 rebuild 重新推导）。 */
    private static final String KEY_SEGMENTS = "clusterSegments";

    /** 单元句柄清单（NBTTagList，每项一个单元坐标句柄复合标签）。 */
    private static final String KEY_UNITS = "clusterUnits";

    /** 吞吐统计嵌套标签。 */
    private static final String KEY_STATS = "clusterStats";

    /** 统计内键：累计处理矿石数（long）。 */
    private static final String KEY_STATS_TOTAL_ORE = "totalProcessedOre";

    /** 最近蒸汽 LPS 显示记账（M5 吞吐批落地前写 0 占位，读取保持对称）。 */
    private static final String KEY_LAST_STEAM_LPS = "clusterLastSteamLps";

    /** 单元句柄条目内键：X 坐标。 */
    private static final String KEY_UNIT_X = "x";

    /** 单元句柄条目内键：Y 坐标。 */
    private static final String KEY_UNIT_Y = "y";

    /** 单元句柄条目内键：Z 坐标。 */
    private static final String KEY_UNIT_Z = "z";

    /** 单元句柄条目内键：维度 ID。 */
    private static final String KEY_UNIT_DIM = "dim";

    /** 单元句柄条目内键：垫位 ID（ClusterTopology.PAD_*）。 */
    private static final String KEY_UNIT_PAD = "pad";

    /** 单元句柄条目内键：段索引。 */
    private static final String KEY_UNIT_SEGMENT = "segment";

    /** 单元句柄 {@code int[]} 回填布局：{x, y, z, dim, pad, segment}。 */
    private static final int HINT_LENGTH = 6;

    /** 常量容器类，禁止实例化。 */
    private ClusterPersistence() {}

    /**
     * 总控侧持久化：machineEnabled / preheat 进度 / 结构 tier / 单元句柄列表 / 吞吐记账。
     *
     * @param cluster 集群总控
     * @param nbt     总控 saveNBTData 传入的根标签（键全部带 cluster 前缀，命名空间内唯一）
     */
    public static void write(MTESteamMineralLogisticsCluster cluster, NBTTagCompound nbt) {
        if (cluster == null || nbt == null) return;
        nbt.setBoolean(KEY_ENABLED, cluster.isMachineEnabled());

        ClusterPreheatController preheat = cluster.getPreheat();
        if (preheat != null) {
            preheat.writeToNBT(nbt);
        }

        nbt.setInteger(KEY_TIER, cluster.getStructureTierIndex());
        nbt.setInteger(
            KEY_SEGMENTS,
            cluster.getTopology()
                .getSegmentCount());

        NBTTagList unitList = new NBTTagList();
        for (MTEClusterUnitBase unit : cluster.getTopology()
            .getUnits()) {
            IGregTechTileEntity base = unit.getBaseMetaTileEntity();
            if (base == null) continue;
            int pad = unit.getPadId();
            int segment = unit.getSegmentIndex();
            // 段/垫句柄合法域收敛：segment ∈ [0,10)、pad ∈ [0,3)（对应 ClusterTopology 槽表容量）；
            // 合法拓扑经 putSlot 校验本不越界，此处为防御旧档/异常态句柄，越界条目不写
            if (segment < 0 || segment >= ClusterTopology.MAX_SEGMENTS) continue;
            if (pad < 0 || pad > ClusterTopology.PAD_LOGISTICS) continue;
            NBTTagCompound unitTag = new NBTTagCompound();
            unitTag.setInteger(KEY_UNIT_X, base.getXCoord());
            unitTag.setInteger(KEY_UNIT_Y, base.getYCoord());
            unitTag.setInteger(KEY_UNIT_Z, base.getZCoord());
            unitTag.setInteger(KEY_UNIT_DIM, base.getWorld().provider.dimensionId);
            unitTag.setInteger(KEY_UNIT_PAD, pad);
            unitTag.setInteger(KEY_UNIT_SEGMENT, segment);
            unitList.appendTag(unitTag);
        }
        nbt.setTag(KEY_UNITS, unitList);

        NBTTagCompound stats = new NBTTagCompound();
        stats.setLong(KEY_STATS_TOTAL_ORE, cluster.getTotalProcessedOre());
        nbt.setTag(KEY_STATS, stats);

        nbt.setLong(KEY_LAST_STEAM_LPS, 0L);
    }

    /**
     * 总控侧载入：与 {@link #write} 逐键对称。缺键一律安全跳过；读后不主动 connect——
     * 单元句柄清单经 {@code setPendingReconnectHints} 回填给总控，重连在总控 tick 周期任务里进行。
     *
     * @param cluster 集群总控
     * @param nbt     总控 loadNBTData 传入的根标签
     */
    public static void read(MTESteamMineralLogisticsCluster cluster, NBTTagCompound nbt) {
        if (cluster == null || nbt == null) return;
        if (nbt.hasKey(KEY_ENABLED)) {
            cluster.setMachineEnabled(nbt.getBoolean(KEY_ENABLED));
        }

        ClusterPreheatController preheat = cluster.getPreheat();
        if (preheat != null) {
            preheat.readFromNBT(nbt);
        }

        // 段数记账键：读出仅为保持写读对称，不写回总控——段划分由重检（checkMachine）重新推导。
        // 结构 tier：写回总控渲染字段（渲染过渡用——载入初至首次重检前控制器贴图按存档 tier 显示；
        // 缺键走新机器默认 -1，无旧档兼容），结构重检仍是最终权威（重检开头 rollbackTiers 复位后
        // 按实际方块重推导）
        if (nbt.hasKey(KEY_TIER)) cluster.applyLoadedCasingTier(nbt.getInteger(KEY_TIER));
        if (nbt.hasKey(KEY_SEGMENTS)) nbt.getInteger(KEY_SEGMENTS);

        if (nbt.hasKey(KEY_UNITS)) {
            List<int[]> hints = new ArrayList<>();
            NBTTagList unitList = nbt.getTagList(KEY_UNITS, 10);
            for (int i = 0; i < unitList.tagCount(); i++) {
                NBTTagCompound unitTag = unitList.getCompoundTagAt(i);
                int pad = unitTag.getInteger(KEY_UNIT_PAD);
                int segment = unitTag.getInteger(KEY_UNIT_SEGMENT);
                // 旧档（64 段时代）的越界句柄直接丢弃：句柄仅为重连提示，权威连接由结构重扫
                // 建立（plan 3.7，不把不在挂点的模块接入），丢提示无副作用且避免回填时越界
                if (segment < 0 || segment >= ClusterTopology.MAX_SEGMENTS) continue;
                if (pad < 0 || pad > ClusterTopology.PAD_LOGISTICS) continue;
                int[] hint = new int[HINT_LENGTH];
                hint[0] = unitTag.getInteger(KEY_UNIT_X);
                hint[1] = unitTag.getInteger(KEY_UNIT_Y);
                hint[2] = unitTag.getInteger(KEY_UNIT_Z);
                hint[3] = unitTag.getInteger(KEY_UNIT_DIM);
                hint[4] = pad;
                hint[5] = segment;
                hints.add(hint);
            }
            if (!hints.isEmpty()) {
                cluster.setPendingReconnectHints(hints);
            }
        }

        if (nbt.hasKey(KEY_STATS)) {
            NBTTagCompound stats = nbt.getCompoundTag(KEY_STATS);
            if (stats.hasKey(KEY_STATS_TOTAL_ORE)) {
                cluster.addProcessedOre(stats.getLong(KEY_STATS_TOTAL_ORE));
            }
        }

        // 显示记账键：读出保持写读对称，总控侧无 setter——最近 LPS 由 M5 吞吐批重算刷新
        if (nbt.hasKey(KEY_LAST_STEAM_LPS)) nbt.getLong(KEY_LAST_STEAM_LPS);
    }
}
