package com.miaokatze.gtsr.common.gui.cluster;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.api.value.ISyncOrValue;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.ByteArraySyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.value.sync.SyncHandler;
import com.cleanroommc.modularui.widget.Widget;
import com.miaokatze.gtsr.common.machine.cluster.BoosterState;
import com.miaokatze.gtsr.common.machine.cluster.ChainLink;
import com.miaokatze.gtsr.common.machine.cluster.ClusterParams;
import com.miaokatze.gtsr.common.machine.cluster.ClusterTopology;
import com.miaokatze.gtsr.common.machine.cluster.ClusterUnitStatus;
import com.miaokatze.gtsr.common.machine.cluster.ExecutionPlan;
import com.miaokatze.gtsr.common.machine.cluster.LogisticsChain;
import com.miaokatze.gtsr.common.machine.cluster.MTEBasicAmplifierUnit;
import com.miaokatze.gtsr.common.machine.cluster.MTEBasicLogisticsUnit;
import com.miaokatze.gtsr.common.machine.cluster.MTEClusterUnitBase;
import com.miaokatze.gtsr.common.machine.cluster.MTESteamMineralLogisticsCluster;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * 集群终端 GUI 同步通道（批2 E6 全量重写，plan §4.3/§4.4）：全部 S2C 快照 + 单一 C2S 动作处理器的
 * 唯一归属地；三视图（拓扑/链路/增幅）只经 {@code findSyncHandlerNullable} 读缓存做纯展示，
 * 不再各自注册同步值。
 *
 * <p>
 * <b>E5 主控冻结契约</b>（并行时序：以下签名由主控切片提供，GUI 侧按契约调用）：
 * <ul>
 * <li>{@code byte[] buildTopologySnapshot()}——30 槽 × 5 字节 {@code [typeId,tier,stateOrdinal,errId,linkId]}，
 * 槽位由下标推导 {@code seg=index/3, pad=index%3}，结构 revision 变化时内容变化（本类只透传，
 * ByteArraySyncValue 的数组相等检测负责按变化发送）；</li>
 * <li>{@code int getHeatPercent()}（1% 量化）、{@code int getSteamLps()}、{@code int getLubricantLps()}、
 * {@code int getThroughputPerSec()}、{@code int getSupplyFlags()}（位约定：bit0=蒸汽短缺，bit1=润滑不足）；</li>
 * <li>其余（{@code isMachineEnabled/getTotalProcessedOre/getStructureTierIndex/getExtensionCount/
 * getSelectedLogisticsIndex}）为既有公开访问器。</li>
 * </ul>
 *
 * <p>
 * <b>typeId 稳定注册表</b>（快照第 0 字节，冻结：只允许尾部追加，禁止重排）：
 * 0=空槽，1=粉碎，2=洗矿，3=离心，4=热力离心，5=筛选，6=磁选，7=熔炼，
 * 8..12=并行/速度/主产物/副产物/节汽增幅，13=物流，255=占位未识别（未运行加工/增幅，
 * GUI 显示「未运行，暂无法识别」，不得伪装空位）。lang 键复用既有
 * {@code gtsr.gui.cluster.unit_type.*} 族。
 *
 * <p>
 * <b>errId 稳定注册表</b>（快照第 3 字节）：0=无，1=模块冲突，2=tier 不匹配，3=未关联集群，
 * 4=延伸断裂。未知值客户端按通用异常显示（防御旧版本/脏数据）。
 *
 * <p>
 * <b>linkId</b>（快照第 4 字节）：物流槽 = 该模块在拓扑物流列表中的下标（0..9）；非物流槽与空槽 = 255。
 *
 * <p>
 * <b>运行状态字节</b>（{@link #KEY_RUN}，30 字节）：每槽 1 字节 = {@link ClusterUnitStatus} ordinal；
 * 空槽 = 255。20t 采样重建，变化即发（无变化零流量），不得每槽分散高频同步。
 *
 * <p>
 * <b>采样纪律</b>（plan §4.3.2/4.3.3）：标量组 10t、运行/增幅 live 组 20t；全部经
 * {@link SampledValue} 包装（服务端有效，客户端读同步缓存不触发 supplier）。热量/吞吐等
 * <b>禁止</b> DoubleSyncValue 每 tick 直推——批1 的 11 个 DoubleSyncValue（KEY_PREHEAT/KEY_THROUGHPUT/
 * cl.f.×4/cl.b.×5）全部废除，公式与增幅数值改 ×100 定点 IntSyncValue。
 *
 * <p>
 * <b>C2S 加固</b>（§4.4，{@link ClusterActionSyncHandler}）：动作防抖（玩家 UUID+tick+action+参数摘要）；
 * 服务端复核终端有效/主控成型/目标物流模块属于本 topology/索引与 ordinal 越界/链长 ≤16；
 * 客户端只回传索引与 ordinal，不回传任何计算结果；{@link ClusterAction} 枚举只尾部追加；
 * APPLY_PRESET 预设动作已随预设数据删除而服务端拒绝且 GUI 无入口。
 *
 * <p>
 * 注册机制说明（承批1）：ModularUI2 在 buildUI 期间 panel 尚未挂接 screen，故 C2S handler 经
 * {@link ClusterActionCarrierWidget} 挂入面板子树，由框架 WidgetTree.collectSyncValues 在双端
 * buildUI 后以确定性 auto key 注册。
 */
public final class ClusterGuiSync {

    private ClusterGuiSync() {}

    // ==================== S2C 同步键（冻结实线协议） ====================

    /** 顶栏标量组：开关机（BooleanSyncValue）。 */
    public static final String KEY_ENABLED = "cl.enabled";
    /** 顶栏标量组：热量百分比 1% 量化（IntSyncValue，10t 采样；替代批1 KEY_PREHEAT Double）。 */
    public static final String KEY_HEAT = "cl.heat";
    /** 顶栏标量组：蒸汽 L/s（IntSyncValue，10t 采样）。 */
    public static final String KEY_STEAM = "cl.steam";
    /** 顶栏标量组：润滑 L/s（IntSyncValue，10t 采样）。 */
    public static final String KEY_LUBE = "cl.lube";
    /** 顶栏标量组：真实吞吐 矿/s（IntSyncValue，10t 采样；替代批1 KEY_THROUGHPUT Double）。 */
    public static final String KEY_THRU = "cl.thru";
    /** 顶栏标量组：累计处理矿数（LongSyncValue；大数超 int 域）。 */
    public static final String KEY_TOTAL = "cl.total";
    /** 顶栏标量组：供给异常位（IntSyncValue，10t 采样；bit0=蒸汽短缺，bit1=润滑不足）。 */
    public static final String KEY_SUPPLY = "cl.supply";
    /** 结构 tier 下标 0-3，未成型 -1（IntSyncValue）。 */
    public static final String KEY_TIER = "cl.tier";
    /** 延伸段数 0-9（IntSyncValue；层数 N/9 显示用）。 */
    public static final String KEY_SEGMENTS = "cl.segs";
    /** 延伸断裂段下标，-1 无（IntSyncValue；异常摘要 extension_break 显示用）。 */
    public static final String KEY_BREAK = "cl.brk";
    /** 拓扑快照：30 槽 × 5 字节（ByteArraySyncValue；结构 revision 变化时发）。 */
    public static final String KEY_TOPO = "cl.topo";
    /** 运行状态：30 槽状态 ordinal 字节（ByteArraySyncValue，20t 采样；空槽 255）。 */
    public static final String KEY_RUN = "cl.run";

    /** 链路页：选中物流单元下标（IntSyncValue）。 */
    public static final String KEY_SEL_LOGI = "cl.selLogi";
    /** 链路页：物流单元清单 CSV（StringSyncValue）——每单元 {@code seg:flags}，flags：bit0 已关联/bit1 自成型/bit2 电源开/bit3 当前可执行。 */
    public static final String KEY_LE_UNITS = "cl.le.units";
    /** 链路页：选中单元整链快照（StringSyncValue，link ordinal CSV；提交后变化即发）。 */
    public static final String KEY_LE_CHAIN = "cl.le.chain";
    /** 链路页：链步可用性（StringSyncValue）——每链步 {@code lockKind:moduleCount} × 10，逗号分隔。 */
    public static final String KEY_LE_LOCK = "cl.le.lock";
    /** 链路页：两级有效性（IntSyncValue）——0 结构无效 / 1 结构有效当前不可执行 / 2 当前可执行。 */
    public static final String KEY_LE_EXEC = "cl.le.exec";
    /** 链路页：第一个失败步（IntSyncValue）——0 无 / 其余 = link ordinal+1（服务端逐 link 真实查询）。 */
    public static final String KEY_LE_FAIL = "cl.le.fail";
    /** 链路页：物流模块可用性 bitset（IntSyncValue，bit i = 第 i 个单元链当前可执行）。 */
    public static final String KEY_LE_AVAIL = "cl.le.avail";
    /** 链路页性能组：单物品耗时（IntSyncValue，秒 ×100，服务端 ExecutionPlan 真值）。 */
    public static final String KEY_F_TIME = "cl.f.time";
    /** 链路页性能组：有效并行（IntSyncValue，服务端真值）。 */
    public static final String KEY_F_PAR = "cl.f.par";
    /** 链路页性能组：预测吞吐（IntSyncValue，矿/s ×100，服务端真值）。 */
    public static final String KEY_F_THRU = "cl.f.thru";
    /** 链路页性能组：本链蒸汽 L/s（IntSyncValue，服务端真值）。 */
    public static final String KEY_F_STEAM = "cl.f.steam";
    /** 链路页性能组：集群总蒸汽 L/s（IntSyncValue，服务端真值）。 */
    public static final String KEY_F_TOTAL = "cl.f.total";
    /** 链路页性能组：实际加权公式（StringSyncValue，ExecutionPlan 同源实值）。 */
    public static final String KEY_F_FORMULA = "cl.f.formula";

    /**
     * 增幅页：结构字段（StringSyncValue）——条目逗号分隔，条目内 {@code typeOrdinal:tier:segment:flags}（flags：bit0 已关联/bit1 自成型）；结构 revision
     * 界。
     */
    public static final String KEY_BO_STRUCT = "cl.bo.struct";
    /** 增幅页：tank/可用性（StringSyncValue）——条目逗号分隔，条目内 {@code amountLiters:available}（0/1），20t 采样。 */
    public static final String KEY_BO_LIVE = "cl.bo.live";
    /**
     * 增幅页：汇总组 CSV（StringSyncValue，20t
     * 采样）——{@code speedX100,parallel,primaryX100,secondaryX100,saverX100,penaltyX100,activeN,failedN}（服务端 BoosterState
     * 真值）。
     */
    public static final String KEY_BO_SUM = "cl.bo.sum";
    /**
     * 增幅页：S7 实耗组（StringSyncValue，20t 采样）——条目逗号分隔、与 KEY_BO_STRUCT 下标一一对应，
     * 条目内冒号分隔：首字段实耗 L/s ×10 定点、次字段基础表值 L/s，其后为联动施加方三元组
     * {@code pct:tier:typeOrdinal}（可零至多组；服务端 {@code amplifierSurchargeSources()} 真值）。
     */
    public static final String KEY_BO_COST = "cl.bo.cost";

    // ==================== typeId / errId 稳定注册表（E5 快照编码共用） ====================

    /** 拓扑快照 typeId：空槽。 */
    public static final int TYPE_EMPTY = 0;
    /** 拓扑快照 typeId：占位未识别（未运行加工/增幅）。 */
    public static final int TYPE_UNRECOGNIZED = 255;

    /** errId：模块冲突（垫位被占/一槽多模块）。 */
    public static final int ERR_MODULE_CONFLICT = 1;
    /** errId：单元 tier 与主控不一致。 */
    public static final int ERR_TIER_MISMATCH = 2;
    /** errId：单元未关联集群。 */
    public static final int ERR_NOT_CONNECTED = 3;
    /** errId：所在段延伸断裂。 */
    public static final int ERR_EXTENSION_BREAK = 4;

    /** 运行状态字节：空槽哨兵。 */
    public static final byte RUN_EMPTY = (byte) 255;

    /** 供给异常位：蒸汽短缺。 */
    public static final int SUPPLY_STEAM_SHORT = 0x01;
    /** 供给异常位：润滑不足。 */
    public static final int SUPPLY_LUBE_SHORT = 0x02;

    /** 槽位总数（ClusterTopology.SLOT_COUNT 同值；避免 GUI 包反向 import 语义耦合，数值冻结）。 */
    private static final int SLOT_COUNT = 30;

    // ==================== 注册入口 ====================

    /**
     * S2C 全量注册（GUI buildUI 调用一次；服务端 supplier 采样后经框架变化检测推送）。
     */
    public static void registerS2C(PanelSyncManager mgr, MTESteamMineralLogisticsCluster cluster) {
        // —— 顶栏标量组（10t 采样；§4.3.3 禁止 Double 每 tick 直推）——
        mgr.syncValue(KEY_ENABLED, new BooleanSyncValue(cluster::isMachineEnabled));
        mgr.syncValue(KEY_HEAT, new IntSyncValue(sampledInt(cluster, 10, cluster::getHeatPercent)));
        mgr.syncValue(KEY_STEAM, new IntSyncValue(sampledInt(cluster, 10, cluster::getSteamLps)));
        mgr.syncValue(KEY_LUBE, new IntSyncValue(sampledInt(cluster, 10, cluster::getLubricantLps)));
        mgr.syncValue(KEY_THRU, new IntSyncValue(sampledInt(cluster, 10, cluster::getThroughputPerSec)));
        mgr.syncValue(KEY_TOTAL, new LongSyncValue(cluster::getTotalProcessedOre));
        mgr.syncValue(KEY_SUPPLY, new IntSyncValue(sampledInt(cluster, 10, cluster::getSupplyFlags)));

        // —— 结构标量（revision 界，变化即发）——
        mgr.syncValue(KEY_TIER, new IntSyncValue(cluster::getStructureTierIndex));
        mgr.syncValue(KEY_SEGMENTS, new IntSyncValue(cluster::getExtensionCount));
        mgr.syncValue(
            KEY_BREAK,
            new IntSyncValue(
                () -> cluster.getTopology()
                    .getBrokenExtensionSegment()));

        // —— 拓扑快照（E5 契约：结构 revision 变化时数组内容变化，数组相等检测按变化发送）——
        mgr.syncValue(KEY_TOPO, new ByteArraySyncValue(cluster::buildTopologySnapshot, null));

        // —— 运行状态 30B（本类构建，20t 采样；§4.3.2）——
        mgr.syncValue(KEY_RUN, new ByteArraySyncValue(sampledBytes(cluster, 20, () -> buildRunStates(cluster)), null));

        // —— 链路页（§4.3.4：整链快照 + 可用性 + 选中 + 两级有效性 + 失败步 + 服务端公式真值）——
        mgr.syncValue(KEY_SEL_LOGI, new IntSyncValue(cluster::getSelectedLogisticsIndex));
        mgr.syncValue(KEY_LE_UNITS, new StringSyncValue(sampledString(cluster, 10, () -> encodeUnits(cluster))));
        mgr.syncValue(KEY_LE_CHAIN, new StringSyncValue(() -> encodeChain(cluster)));
        mgr.syncValue(KEY_LE_LOCK, new StringSyncValue(sampledString(cluster, 10, () -> encodeLocks(cluster))));
        mgr.syncValue(KEY_LE_EXEC, new IntSyncValue(sampledInt(cluster, 10, () -> chainExecLevel(cluster))));
        mgr.syncValue(KEY_LE_FAIL, new IntSyncValue(sampledInt(cluster, 10, () -> firstFailingLink(cluster))));
        mgr.syncValue(KEY_LE_AVAIL, new IntSyncValue(sampledInt(cluster, 20, () -> encodeAvailBitset(cluster))));
        mgr.syncValue(KEY_F_TIME, new IntSyncValue(sampledInt(cluster, 10, () -> toX100(formulaTimeSec(cluster)))));
        mgr.syncValue(
            KEY_F_PAR,
            new IntSyncValue(
                sampledInt(
                    cluster,
                    10,
                    () -> ExecutionPlan.effectiveParallel(tierIdx(cluster), boosterSnapshot(cluster)))));
        mgr.syncValue(
            KEY_F_THRU,
            new IntSyncValue(
                sampledInt(
                    cluster,
                    10,
                    () -> toX100(
                        ExecutionPlan.chainThroughputPerSec(
                            selectedLinks(cluster),
                            tierIdx(cluster),
                            cluster.getTopology(),
                            boosterSnapshot(cluster))))));
        mgr.syncValue(
            KEY_F_STEAM,
            new IntSyncValue(
                sampledInt(
                    cluster,
                    10,
                    () -> toX100(
                        ExecutionPlan
                            .chainSteamLps(selectedLinks(cluster), tierIdx(cluster), cluster.getTopology())))));
        mgr.syncValue(
            KEY_F_TOTAL,
            new IntSyncValue(
                sampledInt(
                    cluster,
                    10,
                    () -> toX100(
                        ExecutionPlan.totalSteamLps(
                            cluster.getTopology()
                                .getLogisticsUnits(),
                            cluster.getTopology(),
                            tierIdx(cluster),
                            boosterSnapshot(cluster))))));
        mgr.syncValue(KEY_F_FORMULA, new StringSyncValue(sampledString(cluster, 10, () -> formulaText(cluster))));

        // —— 增幅页（§4.3.5：结构字段与 tank/可用性分离；结构串 revision 界、live/汇总/实耗 20t）——
        mgr.syncValue(KEY_BO_STRUCT, new StringSyncValue(() -> encodeBoosterStruct(cluster)));
        mgr.syncValue(KEY_BO_LIVE, new StringSyncValue(sampledString(cluster, 20, () -> encodeBoosterLive(cluster))));
        mgr.syncValue(KEY_BO_SUM, new StringSyncValue(sampledString(cluster, 20, () -> encodeBoosterSummary(cluster))));
        mgr.syncValue(KEY_BO_COST, new StringSyncValue(sampledString(cluster, 20, () -> encodeBoosterCost(cluster))));
    }

    /**
     * C2S 动作通道注册（GUI buildUI 调用一次），返回 handler 供视图按钮调用
     * （挂载机制见类注释「注册机制说明」）。
     */
    public static ClusterActionSyncHandler registerC2S(ModularPanel panel, MTESteamMineralLogisticsCluster cluster) {
        ClusterActionSyncHandler handler = new ClusterActionSyncHandler(cluster);
        panel.child(new ClusterActionCarrierWidget(handler));
        return handler;
    }

    // ==================== 服务端编码（supplier 侧） ====================

    /** 运行状态字节构建：拓扑槽序（seg 升序 × pad 升序）→ 状态 ordinal；空槽 255；不足 30 槽补空。 */
    private static byte[] buildRunStates(MTESteamMineralLogisticsCluster cluster) {
        byte[] out = new byte[SLOT_COUNT];
        Arrays.fill(out, RUN_EMPTY);
        List<ClusterTopology.SlotSnapshot> slots = cluster.getTopology()
            .getSlots();
        for (int i = 0; i < slots.size() && i < SLOT_COUNT; i++) {
            MTEClusterUnitBase unit = slots.get(i).unit;
            if (unit != null) {
                int ordinal = unit.getUnitStatus()
                    .ordinal();
                out[i] = (byte) (ordinal >= 0 && ordinal < ClusterUnitStatus.values().length ? ordinal : RUN_EMPTY);
            }
        }
        return out;
    }

    /** 链路页物流单元清单串：{@code seg:flags} CSV（flags 位见 {@link #KEY_LE_UNITS}）。 */
    private static String encodeUnits(MTESteamMineralLogisticsCluster cluster) {
        StringBuilder sb = new StringBuilder(40);
        List<MTEBasicLogisticsUnit> units = cluster.getTopology()
            .getLogisticsUnits();
        for (int i = 0; i < units.size(); i++) {
            MTEBasicLogisticsUnit unit = units.get(i);
            if (i > 0) sb.append(',');
            int flags = 0;
            if (unit != null) {
                if (unit.isClusterConnected()) flags |= 0x01;
                if (unit.isUnitStructureFormed()) flags |= 0x02;
                if (unit.isPowerAllowed()) flags |= 0x04;
                if (unit.isChainExecutableNow()) flags |= 0x08;
            }
            sb.append(unit != null ? unit.getSegmentIndex() : -1)
                .append(':')
                .append(flags);
        }
        return sb.toString();
    }

    /** 链路页整链快照串：选中单元链 ordinal CSV（未选中空串）。 */
    private static String encodeChain(MTESteamMineralLogisticsCluster cluster) {
        MTEBasicLogisticsUnit unit = cluster.getSelectedLogisticsUnit();
        if (unit == null || unit.getChain() == null) return "";
        int[] ordinals = unit.getChain()
            .toOrdinalArray();
        StringBuilder sb = new StringBuilder(ordinals.length * 3);
        for (int i = 0; i < ordinals.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(ordinals[i]);
        }
        return sb.toString();
    }

    /** 链路页链步可用性串：每链步 {@code lockKind:moduleCount}（lockKind 见 {@link #lockKindOf}）。 */
    private static String encodeLocks(MTESteamMineralLogisticsCluster cluster) {
        StringBuilder sb = new StringBuilder(48);
        ChainLink[] links = ChainLink.values();
        ClusterTopology topology = cluster.getTopology();
        for (int i = 0; i < links.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(lockKindOf(LogisticsChain.getLinkLockReasonKey(links[i], topology)))
                .append(':')
                .append(topology.countUnits(links[i].getRequiredUnitClass()));
        }
        return sb.toString();
    }

    /** 两级有效性（§4.3.4）：0 结构无效 / 1 结构有效当前不可执行 / 2 当前可执行。 */
    private static int chainExecLevel(MTESteamMineralLogisticsCluster cluster) {
        MTEBasicLogisticsUnit unit = cluster.getSelectedLogisticsUnit();
        if (unit == null || unit.getChain() == null
            || unit.getChain()
                .isEmpty())
            return 0;
        if (!unit.getChain()
            .isValidStructure()) return 0;
        return unit.isChainExecutableNow() ? 2 : 1;
    }

    /** 第一个失败 link（服务端逐 link 真实查询；0 无 / ordinal+1；结构无效先于 link 失败）。 */
    private static int firstFailingLink(MTESteamMineralLogisticsCluster cluster) {
        MTEBasicLogisticsUnit unit = cluster.getSelectedLogisticsUnit();
        if (unit == null || unit.getChain() == null) return 0;
        LogisticsChain chain = unit.getChain();
        if (chain.isEmpty()) return 0;
        if (!chain.isValidStructure()) return 0;
        ClusterTopology topology = cluster.getTopology();
        for (ChainLink link : chain.getLinks()) {
            if (LogisticsChain.getLinkLockReasonKey(link, topology) != null) return link.ordinal() + 1;
        }
        return 0;
    }

    /** 物流模块可用性 bitset（bit i = units[i] 链当前可执行）。 */
    private static int encodeAvailBitset(MTESteamMineralLogisticsCluster cluster) {
        int bits = 0;
        List<MTEBasicLogisticsUnit> units = cluster.getTopology()
            .getLogisticsUnits();
        for (int i = 0; i < units.size() && i < 10; i++) {
            if (units.get(i) != null && units.get(i)
                .isChainExecutableNow()) bits |= (1 << i);
        }
        return bits;
    }

    /** 增幅结构串：条目逗号分隔，条目内 {@code typeOrdinal:tier:segment:flags}（结构 revision 界）。 */
    private static String encodeBoosterStruct(MTESteamMineralLogisticsCluster cluster) {
        StringBuilder sb = new StringBuilder(48);
        List<MTEBasicAmplifierUnit> units = cluster.getTopology()
            .getBoosterUnits();
        for (MTEBasicAmplifierUnit unit : units) {
            if (unit == null || unit.getBoosterType() == null) continue;
            if (sb.length() > 0) sb.append(',');
            int flags = 0;
            if (unit.getCluster() != null) flags |= 0x01;
            if (unit.isUnitStructureFormed()) flags |= 0x02;
            sb.append(
                unit.getBoosterType()
                    .ordinal())
                .append(':')
                .append(unit.getStructureTier())
                .append(':')
                .append(unit.getSegmentIndex())
                .append(':')
                .append(flags);
        }
        return sb.toString();
    }

    /** 增幅 live 串：条目逗号分隔，条目内 {@code amount:available}（tank 存量 L 与本秒可用；20t 采样；供给只读）。 */
    private static String encodeBoosterLive(MTESteamMineralLogisticsCluster cluster) {
        StringBuilder sb = new StringBuilder(40);
        List<MTEBasicAmplifierUnit> units = cluster.getTopology()
            .getBoosterUnits();
        for (MTEBasicAmplifierUnit unit : units) {
            if (unit == null || unit.getBoosterType() == null) continue;
            if (sb.length() > 0) sb.append(',');
            net.minecraftforge.fluids.Fluid locked = unit.getBoosterFluidForAccess();
            long available = locked == null ? 0L
                : com.miaokatze.gtsr.api.compat.GTSRHatchFluidAccess.probeFluidAmountAcross(
                    unit.getInputHatchesForAccess(),
                    new net.minecraftforge.fluids.FluidStack(locked, 1));
            sb.append(Math.min(Integer.MAX_VALUE, available))
                .append(':')
                .append(unit.isFluidAvailable() ? 1 : 0);
        }
        return sb.toString();
    }

    /** 增幅汇总串：8 字段 CSV（速度/并行/主产物/副产物/节汽/惩罚 ×100 定点 + 生效 N + 失效 N；服务端聚合真值）。 */
    private static String encodeBoosterSummary(MTESteamMineralLogisticsCluster cluster) {
        BoosterState state = boosterSnapshot(cluster);
        return toX100(state.getSpeedBonus()) + ","
            + state.getParallelBonus()
            + ","
            + toX100(state.getPrimaryBonus())
            + ","
            + toX100(state.getSecondaryBonus())
            + ","
            + toX100(state.getSaverBonusRaw())
            + ","
            + toX100(state.getPenaltyProduct())
            + ","
            + state.getActiveCount()
            + ","
            + state.getFailedCount();
    }

    /**
     * 增幅实耗串（S7）：条目逗号分隔（与 KEY_BO_STRUCT 同序同过滤，下标一一对应），条目内
     * {@code lpsX10:base:pct:tier:type:pct:tier:type...}——首字段为联动加成后实际秒耗 ×10 定点
     * （显示一位小数），次字段基础表值 L/s，其后为施加方三元组 {@code pct:tier:typeOrdinal}
     * （{@code amplifierSurchargeSources()} 真值；速度/并行加成表同值，类型须显式携带供客户端
     * 本地化拼装公式串），保证显示 = 实扣同一实现。
     */
    private static String encodeBoosterCost(MTESteamMineralLogisticsCluster cluster) {
        StringBuilder sb = new StringBuilder(64);
        List<MTEBasicAmplifierUnit> units = cluster.getTopology()
            .getBoosterUnits();
        for (MTEBasicAmplifierUnit unit : units) {
            if (unit == null || unit.getBoosterType() == null) continue;
            if (sb.length() > 0) sb.append(',');
            int tier = unit.getUnitStructureTier();
            boolean valid = tier >= 0 && tier < ClusterParams.TIER_COUNT;
            int wip = cluster.countWipLogisticsUnits();
            long lpsX10 = Math.round(unit.amplifierFluidPerSecExact() * wip * 10.0D);
            int base = valid ? ClusterParams.amplifierFluidLps(unit.getBoosterType(), tier) : 0;
            sb.append(lpsX10)
                .append(':')
                .append(base);
            for (int[] source : unit.amplifierSurchargeSources()) {
                sb.append(':')
                    .append(source[0])
                    .append(':')
                    .append(source[1])
                    .append(':')
                    .append(source[2]);
            }
        }
        return sb.toString();
    }

    // ==================== 服务端取值辅助（supplier 侧专用） ====================

    /** 公式：单物品耗时（秒，服务端真值）。 */
    private static double formulaTimeSec(MTESteamMineralLogisticsCluster cluster) {
        return ExecutionPlan
            .itemTimeSec(selectedLinks(cluster), tierIdx(cluster), cluster.getTopology(), boosterSnapshot(cluster));
    }

    /** 实际加权公式文本：分步蒸汽×有效耗时权重，数值与 ExecutionPlan.chainSteamLps 同源。 */
    private static String formulaText(MTESteamMineralLogisticsCluster cluster) {
        List<ChainLink> links = selectedLinks(cluster);
        int tier = tierIdx(cluster);
        if (links == null || links.isEmpty()) return "0 L/s";
        double weighted = 0.0;
        double weights = 0.0;
        StringBuilder terms = new StringBuilder();
        for (ChainLink link : links) {
            if (link == null) continue;
            double seconds = link.getBaseTicks() * ClusterParams.TIER_TIME_FACTOR[tier] / ChainLink.TICKS_PER_SECOND;
            double steam = link.getBaseSteamLps();
            weighted += steam * seconds;
            weights += seconds;
            if (terms.length() > 0) terms.append(" + ");
            terms.append(Math.round(steam))
                .append("×")
                .append(Math.round(seconds * 20.0D));
        }
        double result = weights <= 0.0 ? 0.0 : weighted / weights;
        return terms.append("/")
            .append(Math.round(weights * 20.0D))
            .append("t = ")
            .append(String.format(Locale.ROOT, "%.0f", result))
            .append(" L/s")
            .toString();
    }

    /** 选中单元链 live 视图（未选中 null，ExecutionPlan 防御口径兼容）。 */
    private static List<ChainLink> selectedLinks(MTESteamMineralLogisticsCluster cluster) {
        MTEBasicLogisticsUnit unit = cluster.getSelectedLogisticsUnit();
        return unit != null && unit.getChain() != null ? unit.getChain()
            .getLinks() : null;
    }

    /** 增幅聚合快照（BoosterState.aggregate；空列表返回 EMPTY 单例）。 */
    private static BoosterState boosterSnapshot(MTESteamMineralLogisticsCluster cluster) {
        return BoosterState.aggregate(
            cluster.getTopology()
                .getBoosterUnits());
    }

    /** 服务端公式用 tier（getStructureTierIndex 可能 -1，钳到 [0, TIER_COUNT-1]）。 */
    private static int tierIdx(MTESteamMineralLogisticsCluster cluster) {
        return Math.max(0, Math.min(ClusterParams.TIER_COUNT - 1, cluster.getStructureTierIndex()));
    }

    /** 锁定原因 key → 稳定种类（与 §4.3.4「禁用原因」显示共用）。0 可用/1 简易洗缺失/2 缺模块/3 未成型/4 需通电。 */
    public static int lockKindOf(String reasonKey) {
        if (reasonKey == null) return 0;
        switch (reasonKey) {
            case "gtsr.gui.cluster.link.locked_simple_wash":
                return 1;
            case "gtsr.gui.cluster.link.locked_module":
                return 2;
            case "gtsr.gui.cluster.link.locked_unformed":
                return 3;
            default:
                return 4;
        }
    }

    /** 小数 → ×100 定点 int（服务端编码专用；客户端显示侧各自除回）。 */
    private static int toX100(double value) {
        return Math.round((float) (value * 100.0D));
    }

    // ==================== 采样包装（§4.3.2/4.3.3 采样纪律） ====================

    /** 服务端当前 tick（基 TE 失联时返回 -1 → 调用方每次重算的防御口径）。 */
    private static long serverTick(MTESteamMineralLogisticsCluster cluster) {
        IGregTechTileEntity base = cluster.getBaseMetaTileEntity();
        return base != null ? base.getTimer() : -1L;
    }

    private static java.util.function.IntSupplier sampledInt(MTESteamMineralLogisticsCluster cluster, int interval,
        java.util.function.IntSupplier source) {
        return new SampledValue<>(cluster, interval, source::getAsInt)::get;
    }

    private static Supplier<byte[]> sampledBytes(MTESteamMineralLogisticsCluster cluster, int interval,
        Supplier<byte[]> source) {
        return new SampledValue<>(cluster, interval, source);
    }

    private static Supplier<String> sampledString(MTESteamMineralLogisticsCluster cluster, int interval,
        Supplier<String> source) {
        return new SampledValue<>(cluster, interval, source);
    }

    /**
     * 周期采样包装：supplier 每 {@code interval} tick 至多重算一次并缓存（首次调用立即算），
     * 其间返回缓存值——框架每 tick 的变化检测只比缓存，天然满足「10-20t 采样」；
     * 状态类快照变化仍会在下一采样点立即发出。仅服务端有意义（客户端读同步缓存不触发）。
     */
    private static final class SampledValue<T> implements Supplier<T> {

        private final MTESteamMineralLogisticsCluster cluster;
        private final int interval;
        private final Supplier<T> source;
        private T cached;
        private long lastTick = Long.MIN_VALUE;
        private boolean primed;

        private SampledValue(MTESteamMineralLogisticsCluster cluster, int interval, Supplier<T> source) {
            this.cluster = cluster;
            this.interval = interval;
            this.source = source;
        }

        @Override
        public T get() {
            long now = serverTick(cluster);
            if (!primed || now < 0 || now - lastTick >= interval) {
                cached = source.get();
                lastTick = now;
                primed = true;
            }
            return cached;
        }
    }

    // ==================== C2S 动作通道（§4.4 加固） ====================

    /**
     * 动作码清单（双端唯一，线上码 = ordinal，禁止裸 int）。
     * 枚举顺序即线上协议：只允许尾部追加，禁止重排/中间插入/删除。
     * {@link #APPLY_PRESET} 为批1 遗留位（预设数据已删）：服务端一律拒绝、GUI 无入口，占位保序。
     */
    public enum ClusterAction {
        /** 开关机切换（无参）。 */
        TOGGLE_POWER,
        /** 选中物流单元（buf=[idx]）。 */
        SELECT_LOGISTICS,
        /** 链尾追加链步（buf=[ordinal]）。GUI 已改暂存保存流程，无调用入口，保留占位（同 APPLY_PRESET 先例）。 */
        APPEND_LINK,
        /** 按索引删除链步（buf=[index]）。GUI 已改暂存保存流程，无调用入口，保留占位（同 APPLY_PRESET 先例）。 */
        REMOVE_LINK,
        /** 链步位移（buf=[index,dir]，-1 左移 / +1 右移）。GUI 已改暂存保存流程，无调用入口，保留占位（同 APPLY_PRESET 先例）。 */
        MOVE_LINK,
        /** 清空当前链（无参）。GUI 已改暂存保存流程，无调用入口，保留占位（同 APPLY_PRESET 先例）。 */
        CLEAR_CHAIN,
        /** 已废弃：预设载入（预设数据已删除；服务端拒绝，GUI 无入口，保序占位）。 */
        APPLY_PRESET,
        /** @deprecated 协议冻结兼容位；公式面板现常驻，禁止客户端发送。 */
        @Deprecated
        TOGGLE_FORMULA,
        /** 整链保存（buf=[len][ordinals...]）。暂存保存流程的唯一链写入入口；服务端终态复核通过后整表写入。 */
        SAVE_CHAIN
    }

    /**
     * 集群 C2S 动作处理器（§4.4）：
     * <ul>
     * <li><b>防抖</b>：同玩家 + 同 tick + 同 action + 同参数摘要视为重复包，静默丢弃；</li>
     * <li><b>服务端复核</b>（{@link #terminalValid} 与各 action 分支）：终端（基 TE）有效、
     * 主控成型（tier ≥ 0）、目标物流单元属于本主控 topology、索引/ordinal 不越界、链长 ≤
     * {@link ClusterParams#CHAIN_MAX_LINKS}；</li>
     * <li><b>客户端零回传计算结果</b>：线上参数只有索引与 ordinal（无公式/结论/蒸汽/吞吐）；</li>
     * <li>枚举只尾部追加（{@link ClusterAction} 类注释）。</li>
     * </ul>
     */
    public static final class ClusterActionSyncHandler extends SyncHandler<ClusterActionSyncHandler> {

        private final MTESteamMineralLogisticsCluster cluster;

        // —— 防抖状态（单 handler 服务单玩家会话；GUI 重开即重建）——
        private UUID lastPlayer;
        private int lastActionId = -1;
        private long lastTick = Long.MIN_VALUE;
        private int lastParamDigest = 0;

        private ClusterActionSyncHandler(MTESteamMineralLogisticsCluster cluster) {
            this.cluster = cluster;
            allowC2S();
        }

        // ===== 客户端调用：发送动作到服务端（参数仅索引/ordinal，零计算结果回传） =====

        /** 开关机切换（无参）。服务端复核后 setMachineEnabled(!isMachineEnabled())。 */
        public void togglePower() {
            syncToServer(ClusterAction.TOGGLE_POWER.ordinal(), buf -> {});
        }

        /** 选中物流单元（buf=[idx]）。服务端复核 idx ∈ [0, 物流单元数) 后写入。 */
        public void selectLogistics(int idx) {
            syncToServer(ClusterAction.SELECT_LOGISTICS.ordinal(), buf -> buf.writeInt(idx));
        }

        /** 链尾追加链步（buf=[ordinal]）。服务端复核：主控成型 + 单元在册 + ordinal 越界 + 链长 < 上限。 */
        public void appendLink(int linkOrdinal) {
            syncToServer(ClusterAction.APPEND_LINK.ordinal(), buf -> buf.writeInt(linkOrdinal));
        }

        /** 按索引删除链步（buf=[index]）。服务端复核单元在册与索引界。 */
        public void removeLink(int index) {
            syncToServer(ClusterAction.REMOVE_LINK.ordinal(), buf -> buf.writeInt(index));
        }

        /** 链步位移（buf=[index,dir]）。服务端复核单元在册与索引界。 */
        public void moveLink(int index, int dir) {
            syncToServer(ClusterAction.MOVE_LINK.ordinal(), buf -> {
                buf.writeInt(index);
                buf.writeInt(dir);
            });
        }

        /** 清空当前链（无参）。服务端复核单元在册。 */
        public void clearChain() {
            syncToServer(ClusterAction.CLEAR_CHAIN.ordinal(), buf -> {});
        }

        /**
         * 整链保存（buf=[len][ordinals...]，决策8）。暂存保存流程的唯一链写入入口：客户端预校验
         * （非空 + FSM 结构有效）通过后才调用；服务端仍全量复核（终态复核 + 界界 + SIMPLE_WASH 可用）。
         */
        public void saveChain(int[] ordinals) {
            syncToServer(ClusterAction.SAVE_CHAIN.ordinal(), buf -> {
                int len = ordinals != null ? ordinals.length : 0;
                buf.writeInt(len);
                for (int i = 0; i < len; i++) {
                    buf.writeInt(ordinals[i]);
                }
            });
        }

        // ===== 服务端执行（复核 + 分发） =====

        @Override
        public void readOnClient(int id, PacketBuffer buf) throws IOException {}

        @Override
        public void readOnServer(int id, PacketBuffer buf) throws IOException {
            ClusterAction[] actions = ClusterAction.values();
            if (id < 0 || id >= actions.length) return;
            // 动作与参数先整包读出（读序与客户端写序严格一致），防抖判定后才分发
            ClusterAction action = actions[id];
            int p1 = 0, p2 = 0;
            int[] chainOrdinals = null;
            switch (action) {
                case SELECT_LOGISTICS:
                case APPEND_LINK:
                case REMOVE_LINK:
                    p1 = buf.readInt();
                    break;
                case MOVE_LINK:
                    p1 = buf.readInt();
                    p2 = buf.readInt();
                    break;
                // 变长 payload（决策8）：p1=len，防抖摘要拼入数组哈希；异常长度不再继续读
                // （每个 C2S 包独立 buffer，伪造包零副作用）
                case SAVE_CHAIN:
                    int len = buf.readInt();
                    p1 = len;
                    if (len >= 0 && len <= ClusterParams.CHAIN_MAX_LINKS) {
                        chainOrdinals = new int[len];
                        for (int i = 0; i < len; i++) {
                            chainOrdinals[i] = buf.readInt();
                        }
                        p2 = Arrays.hashCode(chainOrdinals);
                    }
                    break;
                default:
                    break;
            }
            if (duplicatePacket(id, p1, p2)) return;
            executeChecked(action, p1, p2, chainOrdinals);
        }

        /** 防抖：同玩家 + 同 tick + 同 action + 同参数摘要（拼接散列）→ 重复包静默丢弃。 */
        private boolean duplicatePacket(int actionId, int p1, int p2) {
            EntityPlayer player = getSyncManager().getPlayer();
            UUID uuid = player != null ? player.getUniqueID() : null;
            long tick = serverTick(cluster);
            int digest = p1 * 31 + p2;
            if (uuid != null && uuid.equals(lastPlayer)
                && actionId == lastActionId
                && tick == lastTick
                && digest == lastParamDigest) return true;
            lastPlayer = uuid;
            lastActionId = actionId;
            lastTick = tick;
            lastParamDigest = digest;
            return false;
        }

        /**
         * 服务端复核 + 分发（复核不通过一律静默拒绝——伪造包不产生任何副作用）。
         * SAVE_CHAIN 的变长数组经 {@code ordinals} 承接（其余动作为 null）。
         */
        private void executeChecked(ClusterAction action, int p1, int p2, int[] ordinals) {
            if (!terminalValid()) return;
            switch (action) {
                case TOGGLE_POWER -> cluster.setMachineEnabled(!cluster.isMachineEnabled());
                case SELECT_LOGISTICS -> {
                    int size = cluster.getTopology()
                        .getLogisticsUnits()
                        .size();
                    if (p1 < 0 || p1 >= size) return;
                    cluster.setSelectedLogisticsIndex(p1);
                }
                case APPEND_LINK -> {
                    MTEBasicLogisticsUnit unit = checkedUnit();
                    if (unit == null || unit.getChain() == null) return;
                    // 主控必须成型（未成型 topology 为空，实际已由 checkedUnit 拦截；显式复核保留）
                    if (cluster.getStructureTierIndex() < 0) return;
                    ChainLink[] values = ChainLink.values();
                    if (p1 < 0 || p1 >= values.length) return;
                    // 链长上限 16（append 内部同限，此处显式复核口径）
                    if (unit.getChain()
                        .length() >= ClusterParams.CHAIN_MAX_LINKS) return;
                    ChainLink link = values[p1];
                    // GT++ 简易洗配方图缺失时忽略（与编辑器灰化口径一致）
                    if (link == ChainLink.SIMPLE_WASH && !ChainLink.isSimpleWashAvailable()) return;
                    unit.markChainDirty();
                    unit.getChain()
                        .append(link);
                    cluster.notifyChainWritten(getSyncManager().getPlayer(), unit);
                }
                case REMOVE_LINK -> {
                    MTEBasicLogisticsUnit unit = checkedUnit();
                    if (unit == null || unit.getChain() == null) return;
                    if (p1 < 0 || p1 >= unit.getChain()
                        .length()) return;
                    unit.markChainDirty();
                    unit.getChain()
                        .removeAt(p1);
                    cluster.notifyChainWritten(getSyncManager().getPlayer(), unit);
                }
                case MOVE_LINK -> {
                    MTEBasicLogisticsUnit unit = checkedUnit();
                    if (unit == null || unit.getChain() == null) return;
                    if (p1 < 0 || p1 >= unit.getChain()
                        .length()) return;
                    if (p2 != -1 && p2 != 1) return;
                    unit.markChainDirty();
                    unit.getChain()
                        .move(p1, p2);
                    cluster.notifyChainWritten(getSyncManager().getPlayer(), unit);
                }
                case CLEAR_CHAIN -> {
                    MTEBasicLogisticsUnit unit = checkedUnit();
                    if (unit == null || unit.getChain() == null) return;
                    unit.markChainDirty();
                    unit.getChain()
                        .clear();
                    cluster.notifyChainWritten(getSyncManager().getPlayer(), unit);
                }
                case APPLY_PRESET -> {
                    // 预设已删除（§4.4.5）：服务端一律拒绝，GUI 无入口
                    return;
                }
                case TOGGLE_FORMULA -> {
                    // 已废弃兼容位：枚举 ordinal 协议冻结，保留空处理，不再发送。
                }
                case SAVE_CHAIN -> {
                    MTEBasicLogisticsUnit unit = checkedUnit();
                    if (unit == null || unit.getChain() == null) return;
                    // 主控必须成型（checkedUnit 已拦截；显式复核与 APPEND 口径一致）
                    if (cluster.getStructureTierIndex() < 0) return;
                    // 链长界：len ∈ [1, CHAIN_MAX_LINKS]
                    if (ordinals == null || ordinals.length < 1 || ordinals.length > ClusterParams.CHAIN_MAX_LINKS)
                        return;
                    ChainLink[] values = ChainLink.values();
                    for (int ordinal : ordinals) {
                        // ordinal 全部界内（越界即伪造，整包拒绝）
                        if (ordinal < 0 || ordinal >= values.length) return;
                        // GT++ 简易洗配方图缺失时拒绝（同 APPEND 口径）
                        if (values[ordinal] == ChainLink.SIMPLE_WASH && !ChainLink.isSimpleWashAvailable()) return;
                    }
                    // 服务端终态复核（恰好一个终态产物）：不满足静默拒绝零副作用
                    LogisticsChain candidate = LogisticsChain.fromOrdinalArray(ordinals);
                    if (candidate.isEmpty() || !candidate.isValidStructure()) return;
                    unit.getChain()
                        .setLinks(candidate.getLinks());
                    unit.markChainDirty();
                    cluster.notifyChainWritten(getSyncManager().getPlayer(), unit);
                }
            }
        }

        /** 终端有效性复核：基 TE 存在且未死、仍指向本总控。 */
        private boolean terminalValid() {
            IGregTechTileEntity base = cluster.getBaseMetaTileEntity();
            return base != null && !base.isDead() && base.getMetaTileEntity() == cluster;
        }

        /**
         * 目标物流单元复核：主控成型（tier ≥ 0）+ 选中单元属于本主控 topology 的物流清单
         * （引用级比对，防跨集群/已拆除单元伪造）。
         */
        private MTEBasicLogisticsUnit checkedUnit() {
            if (cluster.getStructureTierIndex() < 0) return null;
            MTEBasicLogisticsUnit unit = cluster.getSelectedLogisticsUnit();
            if (unit == null) return null;
            List<MTEBasicLogisticsUnit> units = cluster.getTopology()
                .getLogisticsUnits();
            for (MTEBasicLogisticsUnit inTopology : units) {
                if (inTopology == unit) return unit;
            }
            return null;
        }
    }

    /**
     * 隐形 carrier widget（0 尺寸、不渲染）：把 {@link ClusterActionSyncHandler} 挂入面板子树，
     * 由框架 WidgetTree.collectSyncValues 双端确定性注册（ButtonWidget 内建 handler 同款机制）。
     */
    private static final class ClusterActionCarrierWidget extends Widget<ClusterActionCarrierWidget> {

        private ClusterActionCarrierWidget(ClusterActionSyncHandler handler) {
            setSyncOrValue(handler);
            invisible();
        }

        /** 仅接受集群动作 handler（覆盖 ISynced 默认「拒绝一切 handler」）。 */
        @Override
        public boolean isValidSyncOrValue(ISyncOrValue value) {
            return value == null || value instanceof ClusterActionSyncHandler;
        }
    }

    // ==================== 客户端读数辅助（视图共用） ====================

    /** 读 IntSyncValue 缓存（找不到/类型不符回 fallback）。 */
    public static int intOf(PanelSyncManager sync, String key, int fallback) {
        SyncHandler<?> handler = sync.findSyncHandlerNullable(key);
        if (handler instanceof IntSyncValue value) return value.getIntValue();
        return fallback;
    }

    /** 读 LongSyncValue 缓存（找不到/类型不符回 fallback）。 */
    public static long longOf(PanelSyncManager sync, String key, long fallback) {
        SyncHandler<?> handler = sync.findSyncHandlerNullable(key);
        if (handler instanceof LongSyncValue value) return value.getLongValue();
        return fallback;
    }

    /** 读 BooleanSyncValue 缓存（找不到/类型不符回 fallback）。 */
    public static boolean boolOf(PanelSyncManager sync, String key, boolean fallback) {
        SyncHandler<?> handler = sync.findSyncHandlerNullable(key);
        if (handler instanceof BooleanSyncValue value) {
            Boolean v = value.getValue();
            return v != null && v;
        }
        return fallback;
    }

    /** 读 StringSyncValue 缓存（找不到/类型不符回 fallback；null 值守恒为 fallback）。 */
    public static String strOf(PanelSyncManager sync, String key, String fallback) {
        SyncHandler<?> handler = sync.findSyncHandlerNullable(key);
        if (handler instanceof StringSyncValue value) {
            String v = value.getValue();
            return v != null ? v : fallback;
        }
        return fallback;
    }

    /** 读 ByteArraySyncValue 缓存（找不到/类型不符/空回 fallback）。 */
    public static byte[] bytesOf(PanelSyncManager sync, String key, byte[] fallback) {
        SyncHandler<?> handler = sync.findSyncHandlerNullable(key);
        if (handler instanceof ByteArraySyncValue value) {
            byte[] v = value.getValue();
            return v != null && v.length > 0 ? v : fallback;
        }
        return fallback;
    }

    /** 客户端解析定长 int CSV（畸形项回退 0；越界段截断）。 */
    public static int[] parseIntCsv(String csv, int length) {
        int[] out = new int[length];
        if (csv == null || csv.isEmpty()) return out;
        String[] parts = csv.split(",", -1);
        int n = Math.min(length, parts.length);
        for (int i = 0; i < n; i++) {
            try {
                out[i] = Integer.parseInt(parts[i].trim());
            } catch (NumberFormatException ignored) {
                out[i] = 0;
            }
        }
        return out;
    }

    /** 客户端解析变长 int CSV 为列表（越界 [0,bound) 项丢弃，防枚举演进脏数据）。 */
    public static List<Integer> parseIntList(String csv, int bound) {
        List<Integer> out = new ArrayList<>();
        if (csv == null || csv.isEmpty()) return out;
        for (String part : csv.split(",", -1)) {
            try {
                int value = Integer.parseInt(part.trim());
                if (value >= 0 && value < bound) out.add(value);
            } catch (NumberFormatException ignored) {
                // 畸形项丢弃
            }
        }
        return out;
    }
}
