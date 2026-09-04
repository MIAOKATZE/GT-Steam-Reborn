package com.miaokatze.gtsr.common.terminal;

/**
 * 集群终端 C2S 动作码清单（terminal-native-ui N34，逐字迁址自
 * 原集群动作枚举；S5 迁址落地接线，S2 仅冻结保序）。
 * <p>
 * 线上网码 = ordinal，随 {@link TerminalUiType#CLUSTER_TERMINAL} 的
 * PacketTerminalAction.actionCode 传输（双端唯一，禁止裸 int）。
 *
 * <p>
 * 动作码清单（双端唯一，线上码 = ordinal，禁止裸 int）。
 * 枚举顺序即线上协议：只允许尾部追加，禁止重排/中间插入/删除。
 * {@link #APPLY_PRESET} 为批1 遗留位（预设数据已删）：服务端一律拒绝、GUI 无入口，占位保序。
 */
public enum ClusterTerminalActions {

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
