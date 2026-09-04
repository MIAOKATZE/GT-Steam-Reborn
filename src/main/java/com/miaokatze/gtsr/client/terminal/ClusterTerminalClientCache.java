package com.miaokatze.gtsr.client.terminal;

import net.minecraft.network.PacketBuffer;

import com.miaokatze.gtsr.common.terminal.ClusterTerminalData;
import com.miaokatze.gtsr.common.terminal.PacketTerminalData;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.Unpooled;

/**
 * 集群终端客户端静态缓存（terminal-native-ui N22，PLAN §4.3-D）。
 * <p>
 * 字符串键分型存取（Boolean/Integer/Long/byte[]/String 五分型，键名复用
 * {@link ClusterTerminalData} 的 KEY_ 常量字面量 = 旧 MUI2 同步轨 KEY_ 逐字同值）：
 * <ul>
 * <li><b>锚点匹配</b>：快照携带 pos+dim，GUI 只消费锚点与自身一致的回包
 * （防跨机错位，GTSWN GuiQuantumTerminal :79-84 范式）；</li>
 * <li><b>增量命中键全量覆盖</b>：服务端 payload = {@code [valid bool 恒 true][changedMask varint]
 * [命中键 keyByte + 分型 payload]}，解码后以整份 values 数组一次赋值替换（整体替换防撕裂，
 * GTSWN DeviceTerminalClientCache :28-69 语义）；只覆盖命中键，未变化键保留旧值；</li>
 * <li><b>snapshotVersion 单调门控</b>：同锚点仅接受版本 ≥ 已缓存回包的包（防 Netty 迟到旧包
 * 回写；分型与键序由 keyByte 唯一确定，与 N33 {@code writeKeyValue} 互为镜像）。</li>
 * </ul>
 * 线程模型：写入仅在客户端主线程（TerminalClientPacketSink 切线程后调用），绘制亦在主线程；
 * volatile 快照引用作为跨线程可见性兜底防御。
 */
@SideOnly(Side.CLIENT)
public final class ClusterTerminalClientCache {

    /** 键注册序（= N33 K_* 常量序 = 旧 KEY_ 注册序；下标即线上 keyByte）。 */
    private static final String[] KEY_ORDER = { ClusterTerminalData.KEY_ENABLED, ClusterTerminalData.KEY_HEAT,
        ClusterTerminalData.KEY_STEAM, ClusterTerminalData.KEY_LUBE, ClusterTerminalData.KEY_THRU,
        ClusterTerminalData.KEY_TOTAL, ClusterTerminalData.KEY_SUPPLY, ClusterTerminalData.KEY_TIER,
        ClusterTerminalData.KEY_SEGMENTS, ClusterTerminalData.KEY_BREAK, ClusterTerminalData.KEY_TOPO,
        ClusterTerminalData.KEY_RUN, ClusterTerminalData.KEY_SEL_LOGI, ClusterTerminalData.KEY_LE_UNITS,
        ClusterTerminalData.KEY_LE_CHAIN, ClusterTerminalData.KEY_LE_LOCK, ClusterTerminalData.KEY_LE_EXEC,
        ClusterTerminalData.KEY_LE_FAIL, ClusterTerminalData.KEY_LE_AVAIL, ClusterTerminalData.KEY_F_TIME,
        ClusterTerminalData.KEY_F_PAR, ClusterTerminalData.KEY_F_THRU, ClusterTerminalData.KEY_F_STEAM,
        ClusterTerminalData.KEY_F_TOTAL, ClusterTerminalData.KEY_F_FORMULA, ClusterTerminalData.KEY_BO_STRUCT,
        ClusterTerminalData.KEY_BO_LIVE, ClusterTerminalData.KEY_BO_SUM, ClusterTerminalData.KEY_BO_COST };

    private static final int KEY_COUNT = KEY_ORDER.length;

    /** byte[] 通道键序下标（N33 K_TOPO/K_RUN）。 */
    private static final int IDX_TOPO = 10;
    private static final int IDX_RUN = 11;

    /** byte[] 分型长度防御上限（KEY_TOPO 150 / KEY_RUN 30，放宽到页大小兜底防伪造）。 */
    private static final int BYTE_ARRAY_MAX = 4096;

    /** 一份已解码快照（不可变语义：values 写入后只读；锚点切换即整体换新实例）。 */
    private static final class Snapshot {

        final int x, y, z, dim;
        final int snapshotVersion;
        final Object[] values;

        Snapshot(int x, int y, int z, int dim, int snapshotVersion, Object[] values) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.dim = dim;
            this.snapshotVersion = snapshotVersion;
            this.values = values;
        }

        boolean matchesAnchor(int px, int py, int pz, int pdim) {
            return this.x == px && this.y == py && this.z == pz && this.dim == pdim;
        }
    }

    private static volatile Snapshot current;

    private ClusterTerminalClientCache() {}

    // ==================== GUI 侧读取（分型存取） ====================

    /** @return 缓存是否存在且锚点与给定 pos+dim 一致（GUI 渲染前置校验） */
    public static boolean isAnchored(int x, int y, int z, int dim) {
        Snapshot s = current;
        return s != null && s.matchesAnchor(x, y, z, dim);
    }

    /** 读 bool 键（缺包/类型不符回 fallback，旧 boolOf 同口径）。 */
    public static boolean getBool(String key, boolean fallback) {
        Object v = raw(key);
        return v instanceof Boolean ? (Boolean) v : fallback;
    }

    /** 读 int 键（缺包/类型不符回 fallback，旧 intOf 同口径）。 */
    public static int getInt(String key, int fallback) {
        Object v = raw(key);
        return v instanceof Integer ? (Integer) v : fallback;
    }

    /** 读 long 键（缺包/类型不符回 fallback，旧 longOf 同口径）。 */
    public static long getLong(String key, long fallback) {
        Object v = raw(key);
        return v instanceof Long ? (Long) v : fallback;
    }

    /** 读 byte[] 键（缺包/类型不符/空回 null，旧 bytesOf 同口径）。 */
    public static byte[] getBytes(String key) {
        Object v = raw(key);
        return v instanceof byte[] ? (byte[]) v : null;
    }

    /** 读字符串键（缺包/类型不符回 fallback；null 守恒为 fallback，旧 strOf 同口径）。 */
    public static String getStr(String key, String fallback) {
        Object v = raw(key);
        return v instanceof String ? (String) v : fallback;
    }

    private static Object raw(String key) {
        Snapshot s = current;
        if (s == null) return null;
        for (int i = 0; i < KEY_COUNT; i++) {
            if (KEY_ORDER[i].equals(key)) return s.values[i];
        }
        return null;
    }

    // ==================== sink 侧写入 ====================

    /**
     * 收到 valid=true 回包：解码分型 payload；同锚点增量包以旧快照为基底合并（仅覆盖 changedMask
     * 命中键，未变化键保留旧值——服务端 payload 只携命中键，解码数组未命中下标为 null）；
     * 锚点切换则整体换新（服务端新会话/锚点复位必发全量包）；snapshotVersion 单调门控；
     * 解码失败整包丢弃保留旧快照防撕裂。
     */
    public static void accept(PacketTerminalData msg) {
        final int x = msg.getX();
        final int y = msg.getY();
        final int z = msg.getZ();
        final int dim = msg.getDim();
        final int version = msg.getSnapshotVersion();
        Object[] decoded = decode(msg.getPayload());
        if (decoded == null) {
            return; // 解码失败：整包丢弃（保留旧快照，防撕裂）
        }
        Snapshot prev = current;
        if (prev != null && prev.matchesAnchor(x, y, z, dim)) {
            if (version < prev.snapshotVersion) {
                return; // 迟到旧包：单调门控拒绝
            }
            Object[] merged = new Object[KEY_COUNT];
            System.arraycopy(prev.values, 0, merged, 0, KEY_COUNT);
            for (int i = 0; i < KEY_COUNT; i++) {
                if (decoded[i] != null) {
                    merged[i] = decoded[i];
                }
            }
            decoded = merged;
        }
        current = new Snapshot(x, y, z, dim, version, decoded);
    }

    /** 失效清理（valid=false 回包）：仅当缓存锚点与回包锚点一致时清空。 */
    public static void invalidate(PacketTerminalData msg) {
        Snapshot s = current;
        if (s != null && s.matchesAnchor(msg.getX(), msg.getY(), msg.getZ(), msg.getDim())) {
            current = null;
        }
    }

    /**
     * 解码 payload（与 N33 {@code writeKeyValue} 逐键互为镜像；分型由键序唯一确定，无类型标签）：
     * {@code [valid bool][changedMask varint][命中键 keyByte + 分型 payload]}。畸形/截断/未知键
     * 整包拒绝（伪造包零副作用）。
     */
    private static Object[] decode(byte[] payload) {
        if (payload == null || payload.length == 0) return null;
        try {
            PacketBuffer pb = new PacketBuffer(Unpooled.wrappedBuffer(payload));
            if (!pb.readBoolean()) {
                return null; // N33 约定本位恒 true（真 valid 由信封承载）
            }
            int changedMask = pb.readVarIntFromBuffer();
            if (changedMask < 0 || changedMask >= (1 << KEY_COUNT)) {
                return null; // 位图越界：脏数据/伪造
            }
            Object[] out = new Object[KEY_COUNT];
            for (int key = 0; key < KEY_COUNT; key++) {
                if ((changedMask & (1 << key)) == 0) continue;
                if (pb.readableBytes() < 1) return null; // 截断
                int keyByte = pb.readByte() & 0xFF;
                if (keyByte != key) return null; // 键序不符（服务端按键序写）：整包拒绝
                out[key] = readKeyValue(pb, key);
                if (out[key] == null) return null;
            }
            return out;
        } catch (Exception e) {
            return null; // 截断/畸形：整包丢弃
        }
    }

    /** 单键分型读取（镜像 N33 writeKeyValue 的分型表；类型不符/越界返回 null 即整包拒绝）。 */
    private static Object readKeyValue(PacketBuffer pb, int key) {
        if (key == IDX_TOPO || key == IDX_RUN) {
            int len = pb.readVarIntFromBuffer();
            if (len < 0 || len > BYTE_ARRAY_MAX || pb.readableBytes() < len) return null;
            byte[] arr = new byte[len];
            pb.readBytes(arr);
            return arr;
        }
        if (isCsvKey(key)) {
            return ByteBufUtils.readUTF8String(pb);
        }
        if (key == 0) {
            return pb.readBoolean();
        }
        if (key == 5) {
            return pb.readLong();
        }
        return pb.readVarIntFromBuffer(); // 其余 19 键 = varint 标量
    }

    /** CSV 字符串通道键（K_LE_UNITS/K_LE_CHAIN/K_LE_LOCK/K_F_FORMULA/K_BO_STRUCT/K_BO_LIVE/K_BO_SUM/K_BO_COST）。 */
    private static boolean isCsvKey(int key) {
        return key == 13 || key == 14 || key == 15 || key == 24 || key == 25 || key == 26 || key == 27 || key == 28;
    }
}
