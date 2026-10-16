package com.miaokatze.gtsr.common.gui.mui2.cluster;

import net.minecraft.network.PacketBuffer;

import com.miaokatze.gtsr.common.terminal.ClusterTerminalData;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.Unpooled;

/** Immutable 32-key snapshot shared by both UI routes. */
public final class ClusterSnapshot {

    private static final String[] KEY_ORDER = { ClusterTerminalData.KEY_ENABLED, ClusterTerminalData.KEY_HEAT,
        ClusterTerminalData.KEY_STEAM, ClusterTerminalData.KEY_LUBE, ClusterTerminalData.KEY_THRU,
        ClusterTerminalData.KEY_TOTAL, ClusterTerminalData.KEY_SUPPLY, ClusterTerminalData.KEY_TIER,
        ClusterTerminalData.KEY_SEGMENTS, ClusterTerminalData.KEY_BREAK, ClusterTerminalData.KEY_TOPO,
        ClusterTerminalData.KEY_RUN, ClusterTerminalData.KEY_SEL_LOGI, ClusterTerminalData.KEY_LE_UNITS,
        ClusterTerminalData.KEY_LE_CHAIN, ClusterTerminalData.KEY_LE_LOCK, ClusterTerminalData.KEY_LE_EXEC,
        ClusterTerminalData.KEY_LE_FAIL, ClusterTerminalData.KEY_LE_AVAIL, ClusterTerminalData.KEY_F_TIME,
        ClusterTerminalData.KEY_F_PAR, ClusterTerminalData.KEY_F_THRU, ClusterTerminalData.KEY_F_STEAM,
        ClusterTerminalData.KEY_F_TOTAL, ClusterTerminalData.KEY_F_FORMULA, ClusterTerminalData.KEY_BO_STRUCT,
        ClusterTerminalData.KEY_BO_LIVE, ClusterTerminalData.KEY_BO_SUM, ClusterTerminalData.KEY_BO_COST,
        ClusterTerminalData.KEY_F_DETAIL, ClusterTerminalData.KEY_STATS, ClusterTerminalData.KEY_LE_CHAINS };

    private static final int KEY_COUNT = KEY_ORDER.length;

    /** byte[] 通道键序下标（N33 K_TOPO/K_RUN）。 */
    private static final int IDX_TOPO = 10;
    private static final int IDX_RUN = 11;

    /** String（CSV）通道键序下标（S1-T6 尾追：详情行 / 统计 / 全单元链快照）。 */
    private static final int IDX_F_DETAIL = 29;
    private static final int IDX_STATS = 30;
    private static final int IDX_LE_CHAINS = 31;

    /** Bounded arrays; production topology is 300 bytes and run state is 60 bytes. */
    private static final int BYTE_ARRAY_MAX = 4096;

    private final Object[] values;
    public final int version;
    public final int changedMask;

    private ClusterSnapshot(Object[] values, int version, int mask) {
        this.values = values;
        this.version = version;
        this.changedMask = mask;
    }

    public static ClusterSnapshot merge(ClusterSnapshot previous, int version, byte[] payload) {
        if (previous != null && version < previous.version) return previous;
        Object[] decoded = decode(payload);
        if (decoded == null) return previous;
        int mask = mask(payload);
        Object[] merged = previous == null ? new Object[KEY_COUNT] : previous.values.clone();
        for (int i = 0; i < KEY_COUNT; i++) if (decoded[i] != null) merged[i] = decoded[i];
        return new ClusterSnapshot(merged, version, mask);
    }

    public static int mask(byte[] payload) {
        try {
            PacketBuffer b = new PacketBuffer(Unpooled.wrappedBuffer(payload));
            return b.readBoolean() ? b.readVarIntFromBuffer() : 0;
        } catch (Exception ignored) {
            return 0;
        }
    }

    private Object raw(String key) {
        for (int i = 0; i < KEY_COUNT; i++) if (KEY_ORDER[i].equals(key)) return values[i];
        return null;
    }

    public boolean getBool(String key, boolean fallback) {
        Object v = raw(key);
        return v instanceof Boolean ? (Boolean) v : fallback;
    }

    public int getInt(String key, int fallback) {
        Object v = raw(key);
        return v instanceof Integer ? (Integer) v : fallback;
    }

    public long getLong(String key, long fallback) {
        Object v = raw(key);
        return v instanceof Long ? (Long) v : fallback;
    }

    public String getStr(String key, String fallback) {
        Object v = raw(key);
        return v instanceof String ? (String) v : fallback;
    }

    public byte[] getBytes(String key) {
        Object v = raw(key);
        return v instanceof byte[] ? ((byte[]) v).clone() : null;
    }

    public String getFDetail(String fallback) {
        return getStr(ClusterTerminalData.KEY_F_DETAIL, fallback);
    }

    public String getLeChains(String fallback) {
        return getStr(ClusterTerminalData.KEY_LE_CHAINS, fallback);
    }

    public String getStats(String fallback) {
        return getStr(ClusterTerminalData.KEY_STATS, fallback);
    }

    private static Object[] decode(byte[] payload) {
        if (payload == null || payload.length == 0) return null;
        try {
            PacketBuffer pb = new PacketBuffer(Unpooled.wrappedBuffer(payload));
            if (!pb.readBoolean()) {
                return null; // N33 约定本位恒 true（真 valid 由信封承载）
            }
            int changedMask = pb.readVarIntFromBuffer();
            // KEY_COUNT=32 时 (1 << KEY_COUNT) 按 JLS 位移取模回绕为 1，须按无符号 32 位比较
            long maskU = changedMask & 0xFFFFFFFFL;
            if (maskU >= (1L << KEY_COUNT)) {
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

    /**
     * CSV 字符串通道键（K_LE_UNITS/K_LE_CHAIN/K_LE_LOCK/K_F_FORMULA/K_BO_STRUCT/K_BO_LIVE/K_BO_SUM/K_BO_COST + S1-T6 尾追
     * F_DETAIL/STATS/LE_CHAINS）。
     */
    private static boolean isCsvKey(int key) {
        return key == 13 || key == 14
            || key == 15
            || key == 24
            || key == 25
            || key == 26
            || key == 27
            || key == 28
            || key == IDX_F_DETAIL
            || key == IDX_STATS
            || key == IDX_LE_CHAINS;
    }
}
