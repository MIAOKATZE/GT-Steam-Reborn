package com.miaokatze.gtsr.common.terminal;

/**
 * 终端 UI 类型（terminal-native-ui N23，PLAN §4.2）。
 * <p>
 * 线上网码 = ordinal，禁止裸 int；枚举顺序即线上协议：只允许尾部追加，
 * 禁止重排/中间插入/删除。
 * <p>
 * {@link #AGGREGATOR} 为尾部追加位：聚合器由 FML openGui 双端打开（PLAN §4.1 轨 B），
 * 不走 {@code PacketOpenTerminalUi}（open 包仅覆盖轨 A 的四个纯展示 UI）；
 * 该 ordinal 仅作动作/请求/数据包的 uiType 复用位（Request/Data/Action 三包通用）。
 */
public enum TerminalUiType {

    /** 奇点钻井枢纽状态（轨 A：S2C open + 轮询，S3 接入）。 */
    SINGULARITY_HUB,

    /** 蒸汽枢纽阵列缓存节点状态（轨 A，S3 接入）。 */
    STEAM_HUB,

    /** 蓄水枢纽阵列缓存节点状态（轨 A，S3 接入）。 */
    WATER_HUB,

    /** 集群终端主壳（轨 A：initialPage 仅此类型有意义，S5 接入）。 */
    CLUSTER_TERMINAL,

    /**
     * 聚合器终端配置（尾追复用位，S4 接入）：由 openGui 双端打开，
     * 不发 open 包；仅 Request/Data/Action 包以本 ordinal 标识 uiType。
     */
    AGGREGATOR;

    /**
     * 线上 varint → 枚举的安全解析（未知 id 返回 null，调用方静默丢弃）。
     */
    public static TerminalUiType fromId(int id) {
        TerminalUiType[] values = values();
        if (id < 0 || id >= values.length) {
            return null;
        }
        return values[id];
    }
}
