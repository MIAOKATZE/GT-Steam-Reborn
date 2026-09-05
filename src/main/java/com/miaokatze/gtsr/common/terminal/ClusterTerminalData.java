package com.miaokatze.gtsr.common.terminal;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.api.compat.GTSRHatchFluidAccess;
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

import cpw.mods.fml.common.network.ByteBufUtils;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * 集群终端数据（terminal-native-ui N33，PLAN §4.3-D）：24 键（29 通道，见下）快照组装 +
 * 动作执行分发，supplier 逐个移植原集群同步实现（git 基线 b4fabb2）的
 * syncValue 注册段（:97-166）与编码 helper（:297-571），语义逐字不变；采样包装移植其
 * {@code SampledValue}（:601-626，间隔语义不变），状态改挂每玩家会话（旧轨 = 每 GUI 实例，
 * 新轨 GUI 无服务端实例，会话复位于锚点变化/空闲超时，等价「GUI 重开即重建」）。
 *
 * <p>
 * <b>S2C 快照协议</b>（payload 布局，PLAN §4.3-D 冻结）：
 * {@code [valid boolean][changedMask varint:位图标记本包实际携带的键] + 各命中键 [keyByte][分型 payload]}。
 * {@code valid} 恒 true——复核失败时 {@link TerminalNet} 直接以信封 valid=false + 空 payload 回包
 * （与枢纽/聚合器 A/B/C 同款，本类不被调用）；键序 = 下方 K_* 常量序（即旧 KEY_ 注册序）。
 * 分型编码：bool={@code writeBoolean}、varint 标量={@code writeVarIntToBuffer}（TIER/BREAK 的
 * -1 等负值无损）、long={@code writeLong}、byte[]={varint 长度 + 字节}、CSV 字符串=UTF8。
 * 只有值变化（相对本会话上次组装缓存）的键入包，天然对齐旧「变化即发」；客户端全量覆盖命中键。
 *
 * <p>
 * <b>键清单（29 通道；计划口径「24 键」= 本清单的习惯名，与 wiki
 * mods/gtsr/ui/cluster-gui-sync-protocol.md §1 逐键一致）</b>：
 * 标量 19（KEY_ENABLED bool、KEY_HEAT/KEY_STEAM/KEY_LUBE/KEY_THRU/KEY_SUPPLY/KEY_TIER/
 * KEY_SEGMENTS/KEY_BREAK/KEY_SEL_LOGI/KEY_LE_EXEC/KEY_LE_FAIL/KEY_LE_AVAIL/KEY_F_TIME/
 * KEY_F_PAR/KEY_F_THRU/KEY_F_STEAM/KEY_F_TOTAL varint、KEY_TOTAL long）+ byte[] 2
 * （KEY_TOPO 300B=60 槽×5B、KEY_RUN 60B 空槽 255）+ CSV 8（KEY_LE_UNITS/KEY_LE_CHAIN/
 * KEY_LE_LOCK/KEY_F_FORMULA/KEY_BO_STRUCT/KEY_BO_LIVE/KEY_BO_SUM/KEY_BO_COST）。
 * KEY_* 字符串常量字面量与原集群同步实现逐字相同（客户端缓存键复用）。
 *
 * <p>
 * <b>typeId / errId / linkId 稳定注册表</b>（KEY_TOPO 第 0/3/4 字节，冻结：只允许尾部追加，
 * 照 wiki §1.1-1.4）：typeId 0=空槽、1..7=加工七分型（粉碎/洗矿/离心/热力离心/筛选/磁选/熔炼）、
 * 8..12=8+BoosterType.ordinal()（并行/速度/主产物/副产物/节汽）、13=物流、255=占位未识别
 * （未运行加工/增幅，不得伪装空位）；errId 0=无、1=模块冲突、2=tier 不匹配、3=未关联集群、
 * 4=延伸断裂（结构级错误不编进快照，走 KEY_BREAK 独立通道）；linkId 物流槽=拓扑物流列表
 * （结构扫描序）下标 0..9，非物流槽与空槽=255。编码端为总控
 * {@link MTESteamMineralLogisticsCluster#buildTopologySnapshot()}（本类只透传字节）。
 *
 * <p>
 * <b>C2S 动作分发</b>（{@link ClusterTerminalActions} 枚举，线上码=ordinal 冻结只尾追）：
 * 防抖复用 {@link TerminalNet#handleAction} + {@link TerminalServerSessions}
 * （UUID+tick+actionCode+payload 数组哈希摘要；SAVE_CHAIN 变长 payload 的数组哈希由包层
 * Arrays.hashCode 承担，异常长度在包层即断）。服务端复核（不通过一律静默拒绝，伪造包零副作用）：
 * <ul>
 * <li>TOGGLE_POWER：terminalValid（基 TE 存在未死且指向本总控）→ setMachineEnabled 取反
 * （旧 ClusterActionSyncHandler 对应分支照旧，无 checkedUnit）；</li>
 * <li>SELECT_LOGISTICS：terminalValid → 索引 ∈ [0, 物流单元数) → setSelectedLogisticsIndex
 * （同上照旧）；</li>
 * <li>SAVE_CHAIN 复核链逐字移植 wiki §3.1：terminalValid → checkedUnit（选中单元在册引用级
 * 比对，内含 tier ≥ 0）→ 显式复核 tier ≥ 0 → len ∈ [1,16] → 每个 ordinal 界内 →
 * {@code LogisticsChain.fromOrdinalArray} 非空且 isValidStructure → setLinks 整表写入 +
 * markChainDirty（源实现无 notifyChainWritten 方法，脏清除由客户端 KEY_LE_CHAIN 快照追平承载）；
 * 任一步失败静默拒绝；</li>
 * <li>占位动作 APPEND_LINK/REMOVE_LINK/MOVE_LINK/CLEAR_CHAIN/APPLY_PRESET/TOGGLE_FORMULA：
 * 服务端一律拒绝（GUI 无入口）。</li>
 * </ul>
 * 客户端零回传计算结果纪律不变：线上参数只有索引与 ordinal（SELECT_LOGISTICS buf=[idx int]、
 * SAVE_CHAIN buf=[len int][ordinal int × len]、TOGGLE_POWER 空）。
 */
public final class ClusterTerminalData {

    // ==================== 线上键名（冻结字面量，客户端缓存键复用） ====================

    /** 顶栏标量组：开关机（bool）。 */
    public static final String KEY_ENABLED = "cl.enabled";
    /** 顶栏标量组：热量百分比 1% 量化（varint，10t 采样）。 */
    public static final String KEY_HEAT = "cl.heat";
    /** 顶栏标量组：蒸汽 L/s（varint，10t 采样）。 */
    public static final String KEY_STEAM = "cl.steam";
    /** 顶栏标量组：润滑 L/s（varint，10t 采样）。 */
    public static final String KEY_LUBE = "cl.lube";
    /** 顶栏标量组：真实吞吐 矿/s（varint，10t 采样）。 */
    public static final String KEY_THRU = "cl.thru";
    /** 顶栏标量组：累计处理矿数（long）。 */
    public static final String KEY_TOTAL = "cl.total";
    /** 顶栏标量组：供给异常位（varint，10t 采样；bit0=蒸汽短缺，bit1=润滑不足）。 */
    public static final String KEY_SUPPLY = "cl.supply";
    /** 结构 tier 下标 0-3，未成型 -1（varint）。 */
    public static final String KEY_TIER = "cl.tier";
    /** 延伸段数 0-9（varint）。 */
    public static final String KEY_SEGMENTS = "cl.segs";
    /** 延伸断裂段下标，-1 无（varint）。 */
    public static final String KEY_BREAK = "cl.brk";
    /** 拓扑快照：60 槽 × 5 字节（byte[]；内容变化才入包，数组相等检测）。 */
    public static final String KEY_TOPO = "cl.topo";
    /** 运行状态：60 槽状态 ordinal 字节（byte[]，20t 采样；空槽 255）。 */
    public static final String KEY_RUN = "cl.run";
    /** 链路页：选中物流单元下标（varint）。 */
    public static final String KEY_SEL_LOGI = "cl.selLogi";
    /** 链路页：物流单元清单 CSV（{@code seg:flags}，flags：bit0 已关联/bit1 自成型/bit2 电源开/bit3 当前可执行）。 */
    public static final String KEY_LE_UNITS = "cl.le.units";
    /** 链路页：选中单元整链快照（link ordinal CSV）。 */
    public static final String KEY_LE_CHAIN = "cl.le.chain";
    /** 链路页：链步可用性（每链步 {@code lockKind:moduleCount} × 10，逗号分隔）。 */
    public static final String KEY_LE_LOCK = "cl.le.lock";
    /** 链路页：两级有效性（0 结构无效 / 1 结构有效当前不可执行 / 2 当前可执行）。 */
    public static final String KEY_LE_EXEC = "cl.le.exec";
    /** 链路页：第一个失败步（0 无 / 其余 = link ordinal+1）。 */
    public static final String KEY_LE_FAIL = "cl.le.fail";
    /** 链路页：物流模块可用性 bitset（bit i = 第 i 个单元链当前可执行）。 */
    public static final String KEY_LE_AVAIL = "cl.le.avail";
    /** 链路页性能组：单物品耗时（秒 ×100 定点）。 */
    public static final String KEY_F_TIME = "cl.f.time";
    /** 链路页性能组：有效并行。 */
    public static final String KEY_F_PAR = "cl.f.par";
    /** 链路页性能组：预测吞吐（矿/s ×100 定点）。 */
    public static final String KEY_F_THRU = "cl.f.thru";
    /** 链路页性能组：本链蒸汽 L/s（×100 定点）。 */
    public static final String KEY_F_STEAM = "cl.f.steam";
    /** 链路页性能组：集群总蒸汽 L/s（×100 定点）。 */
    public static final String KEY_F_TOTAL = "cl.f.total";
    /** 链路页性能组：实际加权公式文本（ExecutionPlan 同源实值）。 */
    public static final String KEY_F_FORMULA = "cl.f.formula";
    /** 增幅页：结构字段（{@code typeOrdinal:tier:segment:flags} 条目 CSV）。 */
    public static final String KEY_BO_STRUCT = "cl.bo.struct";
    /** 增幅页：tank/可用性（{@code amountLiters:available} 条目 CSV，20t 采样）。 */
    public static final String KEY_BO_LIVE = "cl.bo.live";
    /** 增幅页：汇总 8 字段 CSV（×100 定点 + 生效 N + 失效 N，20t 采样）。 */
    public static final String KEY_BO_SUM = "cl.bo.sum";
    /** 增幅页：实耗组（{@code lpsX10:base[:pct:tier:type]...} 条目 CSV，20t 采样）。 */
    public static final String KEY_BO_COST = "cl.bo.cost";

    // ==================== 键序（线上 keyByte = 下标；= 旧 KEY_ 注册序） ====================

    private static final int K_ENABLED = 0;
    private static final int K_HEAT = 1;
    private static final int K_STEAM = 2;
    private static final int K_LUBE = 3;
    private static final int K_THRU = 4;
    private static final int K_TOTAL = 5;
    private static final int K_SUPPLY = 6;
    private static final int K_TIER = 7;
    private static final int K_SEGMENTS = 8;
    private static final int K_BREAK = 9;
    private static final int K_TOPO = 10;
    private static final int K_RUN = 11;
    private static final int K_SEL_LOGI = 12;
    private static final int K_LE_UNITS = 13;
    private static final int K_LE_CHAIN = 14;
    private static final int K_LE_LOCK = 15;
    private static final int K_LE_EXEC = 16;
    private static final int K_LE_FAIL = 17;
    private static final int K_LE_AVAIL = 18;
    private static final int K_F_TIME = 19;
    private static final int K_F_PAR = 20;
    private static final int K_F_THRU = 21;
    private static final int K_F_STEAM = 22;
    private static final int K_F_TOTAL = 23;
    private static final int K_F_FORMULA = 24;
    private static final int K_BO_STRUCT = 25;
    private static final int K_BO_LIVE = 26;
    private static final int K_BO_SUM = 27;
    private static final int K_BO_COST = 28;
    private static final int KEY_COUNT = 29;

    /**
     * 采样间隔表（tick，下标 = 键序；0 = 直读，每次组装重算）：标量组 10t、KEY_RUN/增幅 live 组
     * 20t、KEY_TOPO/结构标量/整链快照/结构串 revision 界直读——与原集群同步注册段
     * （:200-282）逐键一致。
     */
    private static final int[] SAMPLE_INTERVALS = {
        // cl.enabled cl.heat cl.steam cl.lube cl.thru cl.total cl.supply cl.tier cl.segs cl.brk
        0, 10, 10, 10, 10, 0, 10, 0, 0, 0,
        // cl.topo cl.run cl.selLogi cl.le.units cl.le.chain cl.le.lock cl.le.exec cl.le.fail cl.le.avail
        0, 20, 0, 10, 0, 10, 10, 10, 20,
        // cl.f.time cl.f.par cl.f.thru cl.f.steam cl.f.total cl.f.formula cl.bo.struct cl.bo.live cl.bo.sum cl.bo.cost
        10, 10, 10, 10, 10, 10, 0, 20, 20, 20 };

    // ==================== typeId / errId 稳定注册表（冻结常量；编码端在总控） ====================

    /** 拓扑快照 typeId：空槽（空槽类型值冻结）。 */
    public static final int TYPE_EMPTY = 0;
    /** 拓扑快照 typeId：占位未识别（未运行加工/增幅，不得伪装空位）。 */
    public static final int TYPE_UNRECOGNIZED = 255;
    /** errId：模块冲突（垫位被占/一槽多模块）。 */
    public static final int ERR_MODULE_CONFLICT = 1;
    /** errId：单元 tier 与主控不一致。 */
    public static final int ERR_TIER_MISMATCH = 2;
    /** errId：单元未关联集群。 */
    public static final int ERR_NOT_CONNECTED = 3;
    /** errId：所在段延伸断裂（结构级错误，不编进快照，走 KEY_BREAK 通道）。 */
    public static final int ERR_EXTENSION_BREAK = 4;
    /** 运行状态字节：空槽哨兵。 */
    public static final byte RUN_EMPTY = (byte) 255;
    /** 供给异常位：蒸汽短缺。 */
    public static final int SUPPLY_STEAM_SHORT = 0x01;
    /** 供给异常位：润滑不足。 */
    public static final int SUPPLY_LUBE_SHORT = 0x02;

    /** 槽位总数（ClusterTopology.SLOT_COUNT 同值；避免反向 import 语义耦合，数值冻结）。 */
    private static final int SLOT_COUNT = 60;

    /** 会话空闲复位阈值（tick）：超过视为新会话，缓存全量重建（首包全量键，等价旧轨 GUI 重开重建）。 */
    private static final long SESSION_GAP_TICKS = 40L;

    /** 每玩家集群终端组装会话（玩家同时至多打开一个终端 GUI；主线程排水后访问，无需并发容器）。 */
    private static final Map<UUID, ClusterSession> SESSIONS = new HashMap<UUID, ClusterSession>();

    private ClusterTerminalData() {}

    // ==================== 服务端快照组装 ====================

    /**
     * 组装 24 键快照 payload（TerminalNet.handleRequest 完成 TE 存活/距离复核后主线程调用）：
     * {@code [valid][changedMask varint][命中键 keyByte + 分型 payload]}。每键经采样间隔门控取值
     * 后与本会话上次发送值比对，仅变化键入包；会话首包（新玩家/锚点变化/空闲超时复位）全量。
     */
    public static byte[] assembleSnapshot(MTESteamMineralLogisticsCluster cluster, UUID playerId) {
        long wallTick = TerminalServerSessions.currentServerTick();
        IGregTechTileEntity base = cluster.getBaseMetaTileEntity();
        int dim = base != null && base.getWorld() != null ? base.getWorld().provider.dimensionId : 0;
        int x = base != null ? base.getXCoord() : 0;
        int y = base != null ? base.getYCoord() : 0;
        int z = base != null ? base.getZCoord() : 0;
        ClusterSession session = SESSIONS.get(playerId);
        if (session == null || session.dim != dim
            || session.x != x
            || session.y != y
            || session.z != z
            || wallTick - session.lastWallTick > SESSION_GAP_TICKS) {
            session = new ClusterSession(dim, x, y, z);
            SESSIONS.put(playerId, session);
        }
        session.lastWallTick = wallTick;
        Object[] current = new Object[KEY_COUNT];
        int changedMask = 0;
        for (int key = 0; key < KEY_COUNT; key++) {
            current[key] = session.currentValue(key, cluster);
            if (session.lastSent[key] == null || !valueEquals(current[key], session.lastSent[key])) {
                changedMask |= (1 << key);
            }
        }
        ByteBuf buf = Unpooled.buffer();
        PacketBuffer pb = new PacketBuffer(buf);
        pb.writeBoolean(true); // [valid]：本类仅在信封 valid=true 时被调用（PLAN §4.3-D 布局首位）
        pb.writeVarIntToBuffer(changedMask);
        for (int key = 0; key < KEY_COUNT; key++) {
            if ((changedMask & (1 << key)) != 0) {
                session.lastSent[key] = current[key];
                pb.writeByte(key);
                writeKeyValue(pb, key, current[key]);
            }
        }
        byte[] payload = new byte[buf.readableBytes()];
        buf.readBytes(payload);
        return payload;
    }

    /** 分型写入（读侧对称实现见客户端缓存 S5b；类型由键序唯一确定，无类型标签）。 */
    private static void writeKeyValue(PacketBuffer pb, int key, Object value) {
        switch (key) {
            case K_ENABLED -> pb.writeBoolean((Boolean) value);
            case K_TOTAL -> pb.writeLong((Long) value);
            case K_TOPO, K_RUN -> {
                byte[] arr = (byte[]) value;
                pb.writeVarIntToBuffer(arr.length);
                pb.writeBytes(arr);
            }
            case K_LE_UNITS, K_LE_CHAIN, K_LE_LOCK, K_F_FORMULA, K_BO_STRUCT, K_BO_LIVE, K_BO_SUM, K_BO_COST -> ByteBufUtils
                .writeUTF8String(pb, (String) value);
            default -> pb.writeVarIntToBuffer((Integer) value);
        }
    }

    /** 值相等（byte[] 逐字节，其余 equals；null 守恒）。 */
    private static boolean valueEquals(Object a, Object b) {
        if (a == null || b == null) return a == b;
        if (a instanceof byte[] && b instanceof byte[]) return Arrays.equals((byte[]) a, (byte[]) b);
        return a.equals(b);
    }

    // ==================== 单键取值（supplier 移植自原集群同步实现，语义逐字不变） ====================

    /**
     * 单键当前值（Boolean/Integer/Long/byte[]/String 五分型之一）。采样键（间隔 &gt; 0）经会话缓存
     * 门控：每 interval tick 至多重算一次（首次立即算；基 TE 失联 now&lt;0 时每次重算的防御口径），
     * 直读键每次重算——变化检测只比结果，天然对齐旧「变化即发」。
     */
    private static Object computeValue(int key, MTESteamMineralLogisticsCluster cluster) {
        switch (key) {
            case K_ENABLED:
                return cluster.isMachineEnabled();
            case K_HEAT:
                return cluster.getHeatPercent();
            case K_STEAM:
                return cluster.getSteamLps();
            case K_LUBE:
                return cluster.getLubricantLps();
            case K_THRU:
                return cluster.getThroughputPerSec();
            case K_TOTAL:
                return cluster.getTotalProcessedOre();
            case K_SUPPLY:
                return cluster.getSupplyFlags();
            case K_TIER:
                return cluster.getStructureTierIndex();
            case K_SEGMENTS:
                return cluster.getExtensionCount();
            case K_BREAK:
                return cluster.getTopology()
                    .getBrokenExtensionSegment();
            case K_TOPO:
                return cluster.buildTopologySnapshot();
            case K_RUN:
                return buildRunStates(cluster);
            case K_SEL_LOGI:
                return cluster.getSelectedLogisticsIndex();
            case K_LE_UNITS:
                return encodeUnits(cluster);
            case K_LE_CHAIN:
                return encodeChain(cluster);
            case K_LE_LOCK:
                return encodeLocks(cluster);
            case K_LE_EXEC:
                return chainExecLevel(cluster);
            case K_LE_FAIL:
                return firstFailingLink(cluster);
            case K_LE_AVAIL:
                return encodeAvailBitset(cluster);
            case K_F_TIME:
                return toX100(formulaTimeSec(cluster));
            case K_F_PAR:
                return ExecutionPlan.effectiveParallel(tierIdx(cluster), boosterSnapshot(cluster));
            case K_F_THRU:
                return toX100(
                    ExecutionPlan.chainThroughputPerSec(
                        selectedLinks(cluster),
                        tierIdx(cluster),
                        cluster.getTopology(),
                        boosterSnapshot(cluster)));
            case K_F_STEAM:
                return toX100(
                    ExecutionPlan.chainSteamLps(selectedLinks(cluster), tierIdx(cluster), cluster.getTopology()));
            case K_F_TOTAL:
                return toX100(
                    ExecutionPlan.totalSteamLps(
                        cluster.getTopology()
                            .getLogisticsUnits(),
                        cluster.getTopology(),
                        tierIdx(cluster),
                        boosterSnapshot(cluster)));
            case K_F_FORMULA:
                return formulaText(cluster);
            case K_BO_STRUCT:
                return encodeBoosterStruct(cluster);
            case K_BO_LIVE:
                return encodeBoosterLive(cluster);
            case K_BO_SUM:
                return encodeBoosterSummary(cluster);
            case K_BO_COST:
                return encodeBoosterCost(cluster);
            default:
                return Integer.valueOf(0); // 不可达（键序封闭）；防御回退
        }
    }

    /** 运行状态字节构建：拓扑槽序（seg 升序 × pad 升序）→ 状态 ordinal；空槽 255；不足 60 槽补空。 */
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

    /** 两级有效性：0 结构无效 / 1 结构有效当前不可执行 / 2 当前可执行。 */
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

    /** 增幅 live 串：条目逗号分隔，条目内 {@code amount:available}（tank 存量 L 与本秒可用；供给只读）。 */
    private static String encodeBoosterLive(MTESteamMineralLogisticsCluster cluster) {
        StringBuilder sb = new StringBuilder(40);
        List<MTEBasicAmplifierUnit> units = cluster.getTopology()
            .getBoosterUnits();
        for (MTEBasicAmplifierUnit unit : units) {
            if (unit == null || unit.getBoosterType() == null) continue;
            if (sb.length() > 0) sb.append(',');
            Fluid locked = unit.getBoosterFluidForAccess();
            long available = locked == null ? 0L
                : GTSRHatchFluidAccess
                    .probeFluidAmountAcross(unit.getInputHatchesForAccess(), new FluidStack(locked, 1));
            sb.append(Math.min(Integer.MAX_VALUE, available))
                .append(':')
                .append(unit.isFluidAvailable() ? 1 : 0);
        }
        return sb.toString();
    }

    /** 增幅汇总串：8 字段 CSV（速度/并行/主产物/副产物/节汽/惩罚 ×100 定点 + 生效 N + 失效 N）。 */
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
     * 增幅实耗串：条目逗号分隔（与 KEY_BO_STRUCT 同序同过滤，下标一一对应），条目内
     * {@code lpsX10:base:pct:tier:type...}——首字段为联动加成后实际秒耗 ×10 定点，次字段基础表值
     * L/s，其后为施加方三元组 {@code pct:tier:typeOrdinal}（{@code amplifierSurchargeSources()} 真值）。
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

    // ==================== 取值辅助（supplier 侧专用，移植自旧实现） ====================

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

    /** 锁定原因 key → 稳定种类。0 可用/2 缺模块/3 未成型/4 需通电（1=简易洗缺失已废弃，2/3/4 编码不变保兼容）。 */
    private static int lockKindOf(String reasonKey) {
        if (reasonKey == null) return 0;
        switch (reasonKey) {
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

    /** 服务端当前 tick（基 TE 失联时返回 -1 → 调用方每次重算的防御口径）。 */
    private static long serverTick(MTESteamMineralLogisticsCluster cluster) {
        IGregTechTileEntity base = cluster.getBaseMetaTileEntity();
        return base != null ? base.getTimer() : -1L;
    }

    // ==================== C2S 动作分发（复核不通过一律静默拒绝） ====================

    /**
     * 动作分发（TerminalNet.handleAction 已完成 TE 存活/距离复核与 UUID+tick+action+payload
     * 数组哈希防抖，主线程调用）。参数读序与旧 ClusterActionSyncHandler 严格一致
     * （SELECT_LOGISTICS=[idx int]、SAVE_CHAIN=[len int][ordinal × len]、TOGGLE_POWER 无参）；
     * 占位动作在读参前一律拒绝。
     */
    public static void executeAction(MTESteamMineralLogisticsCluster cluster, int actionCode, byte[] payload) {
        ClusterTerminalActions[] actions = ClusterTerminalActions.values();
        if (actionCode < 0 || actionCode >= actions.length) return;
        ClusterTerminalActions action = actions[actionCode];
        // 占位动作（APPEND/REMOVE/MOVE/CLEAR/APPLY_PRESET/TOGGLE_FORMULA）：服务端一律拒绝，
        // GUI 无入口（PLAN §7.4-5）；不读参、零副作用
        switch (action) {
            case APPEND_LINK:
            case REMOVE_LINK:
            case MOVE_LINK:
            case CLEAR_CHAIN:
            case APPLY_PRESET:
            case TOGGLE_FORMULA:
                return;
            default:
                break;
        }
        ByteBuf buf = Unpooled.wrappedBuffer(payload == null ? new byte[0] : payload);
        int idx = 0;
        int[] chainOrdinals = null;
        switch (action) {
            case SELECT_LOGISTICS:
                if (buf.readableBytes() < 4) return; // idx 读不全：异常长度即断
                idx = buf.readInt();
                break;
            case SAVE_CHAIN:
                if (buf.readableBytes() < 4) return;
                int len = buf.readInt();
                // 异常长度不再继续读（伪造包零副作用；数组哈希摘要已在包层参与防抖）
                if (len < 0 || len > ClusterParams.CHAIN_MAX_LINKS) return;
                if (buf.readableBytes() < len * 4) return;
                chainOrdinals = new int[len];
                for (int i = 0; i < len; i++) {
                    chainOrdinals[i] = buf.readInt();
                }
                break;
            default:
                break; // TOGGLE_POWER：无参
        }
        executeChecked(cluster, action, idx, chainOrdinals);
    }

    /**
     * 服务端复核 + 执行（移植旧 ClusterActionSyncHandler.executeChecked 对应分支）：
     * TOGGLE_POWER 仅 terminalValid；SELECT_LOGISTICS 复核索引界内；SAVE_CHAIN 走 wiki §3.1
     * 完整复核链。复核不通过一律静默拒绝——伪造包不产生任何副作用。
     */
    private static void executeChecked(MTESteamMineralLogisticsCluster cluster, ClusterTerminalActions action, int idx,
        int[] ordinals) {
        if (!terminalValid(cluster)) return;
        switch (action) {
            case TOGGLE_POWER:
                cluster.setMachineEnabled(!cluster.isMachineEnabled());
                break;
            case SELECT_LOGISTICS: {
                int size = cluster.getTopology()
                    .getLogisticsUnits()
                    .size();
                if (idx < 0 || idx >= size) return;
                cluster.setSelectedLogisticsIndex(idx);
                break;
            }
            case SAVE_CHAIN: {
                MTEBasicLogisticsUnit unit = checkedUnit(cluster);
                if (unit == null || unit.getChain() == null) return;
                // 主控必须成型（checkedUnit 已拦截；显式复核与旧轨口径一致）
                if (cluster.getStructureTierIndex() < 0) return;
                // 链长界：len ∈ [1, CHAIN_MAX_LINKS]
                if (ordinals == null || ordinals.length < 1 || ordinals.length > ClusterParams.CHAIN_MAX_LINKS) return;
                ChainLink[] values = ChainLink.values();
                for (int ordinal : ordinals) {
                    // ordinal 全部界内（越界即伪造，整包拒绝）
                    if (ordinal < 0 || ordinal >= values.length) return;
                }
                // 服务端终态复核（恰好一个终态产物）：不满足静默拒绝零副作用
                LogisticsChain candidate = LogisticsChain.fromOrdinalArray(ordinals);
                if (candidate.isEmpty() || !candidate.isValidStructure()) return;
                unit.getChain()
                    .setLinks(candidate.getLinks());
                unit.markChainDirty();
                break;
            }
            default:
                break; // 占位动作已在上层拒绝；不可达
        }
    }

    /** 终端有效性复核：基 TE 存在且未死、仍指向本总控（旧 terminalValid 逐字移植）。 */
    private static boolean terminalValid(MTESteamMineralLogisticsCluster cluster) {
        IGregTechTileEntity base = cluster.getBaseMetaTileEntity();
        return base != null && !base.isDead() && base.getMetaTileEntity() == cluster;
    }

    /**
     * 目标物流单元复核：主控成型（tier ≥ 0）+ 选中单元属于本主控 topology 的物流清单
     * （引用级比对，防跨集群/已拆除单元伪造）（旧 checkedUnit 逐字移植）。
     */
    private static MTEBasicLogisticsUnit checkedUnit(MTESteamMineralLogisticsCluster cluster) {
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

    // ==================== 每玩家组装会话（采样缓存 + 变化检测基线） ====================

    /**
     * 单玩家组装会话：锚点（dim/x/y/z）+ 上次组装墙钟 + 三组状态——lastSent（上次发送值，
     * 变化检测基线）、sampledCache/sampledTick/primed（采样间隔门控，SampledValue 语义）。
     * 旧轨状态挂 GUI 实例（重开即重建）；新轨 GUI 无服务端实例，由 assembleSnapshot 在
     * 锚点变化或空闲超 {@link #SESSION_GAP_TICKS} 时重建（首包全量键，无撕裂窗口）。
     */
    private static final class ClusterSession {

        final int dim;
        final int x;
        final int y;
        final int z;
        long lastWallTick;
        final Object[] lastSent = new Object[KEY_COUNT];
        final Object[] sampledCache = new Object[KEY_COUNT];
        final long[] sampledTick = new long[KEY_COUNT];
        final boolean[] primed = new boolean[KEY_COUNT];

        ClusterSession(int dim, int x, int y, int z) {
            this.dim = dim;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        /** 单键当前值：采样键经间隔门控（旧 SampledValue.get 逐字语义），直读键每次重算。 */
        Object currentValue(int key, MTESteamMineralLogisticsCluster cluster) {
            int interval = SAMPLE_INTERVALS[key];
            if (interval <= 0) {
                return computeValue(key, cluster);
            }
            long now = serverTick(cluster);
            if (!primed[key] || now < 0 || now - sampledTick[key] >= interval) {
                sampledCache[key] = computeValue(key, cluster);
                sampledTick[key] = now;
                primed[key] = true;
            }
            return sampledCache[key];
        }
    }
}
