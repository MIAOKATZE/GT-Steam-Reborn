package com.miaokatze.gtsr.common.terminal;

import net.minecraft.network.PacketBuffer;

import com.miaokatze.gtsr.common.machine.tcds.MTEThermoChemicalDenseSteamGenerator;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * TCDS（热化学致密蒸汽发生器）流量终端数据（PLAN：TCDS 流量设定迁机器终端 GUI，轨 A）。
 * <p>
 * 快照组装：设定流量 {@code mFlow}（L/t）+ 理论最大热量字符串（复用机器
 * {@link MTEThermoChemicalDenseSteamGenerator#heatCapDisplay()} 口径，如
 * {@code "250% (200×1.25)"}——纯数值+符号、Locale.ENGLISH 锁定小数点，无 lang 依赖，
 * 服务端组装不会语言污染客户端，与枢纽屏 localizeFluid 只发注册名同纪律）。
 * <p>
 * 动作执行：唯一动作 {@link #ACTION_SET_FLOW}——流量写入调用 TCDS 既有
 * {@link MTEThermoChemicalDenseSteamGenerator#setFlow(int)}，服务端钳 ≥1 为唯一权威
 * （客户端解析失败按 0 发包、不做安全假设；setter 自持 markDirty）。
 * <p>
 * 防御性读取（PLAN R9，SingularityHubTerminalData 同款）：动作/快照 payload 变长段每步
 * 先验可读字节数，越界/截断/尾部多余字节一律整包静默拒绝。
 */
public final class TcdsTerminalData {

    // actionCode（终端动作码：新增只许尾追，禁止复用/改义）
    /** 设定流量写入（payload：varint flow → setFlow 服务端钳 ≥1） */
    public static final int ACTION_SET_FLOW = 1;

    private TcdsTerminalData() {}

    // ==================== 服务端快照组装 ====================

    /**
     * 组装快照 payload：{@code [flow varint][heatCap UTF8]}。
     * 数据源 = {@code generator.getFlow()} 与 {@code generator.heatCapDisplay()}（每请求活取）。
     */
    public static byte[] assembleSnapshot(MTEThermoChemicalDenseSteamGenerator generator) {
        ByteBuf buf = Unpooled.buffer();
        PacketBuffer pb = new PacketBuffer(buf);
        pb.writeVarIntToBuffer(generator.getFlow());
        ByteBufUtils.writeUTF8String(buf, generator.heatCapDisplay());
        byte[] payload = new byte[buf.readableBytes()];
        buf.readBytes(payload);
        return payload;
    }

    // ==================== 服务端动作执行分发 ====================

    /** 动作分发（主线程排水后调用）。payload 布局：varint flow。 */
    public static void executeAction(MTEThermoChemicalDenseSteamGenerator generator, int actionCode, byte[] payload) {
        if (payload == null) {
            return;
        }
        ByteBuf buf = Unpooled.wrappedBuffer(payload);
        if (!buf.isReadable()) {
            return; // 空 payload：异常长度即断（静默拒绝）
        }
        switch (actionCode) {
            case ACTION_SET_FLOW:
                final int flow;
                try {
                    flow = new PacketBuffer(buf).readVarIntFromBuffer();
                } catch (RuntimeException e) {
                    return; // varint 越界/截断：整包静默拒绝
                }
                if (buf.isReadable()) {
                    return; // 尾部多余字节：整包静默拒绝
                }
                generator.setFlow(flow); // 服务端权威钳制 >=1（setter 自持，非法值不破坏数值模型）
                break;
            default:
                return; // 未知动作码：静默拒绝
        }
    }

    // ==================== 客户端解码（与 assembleSnapshot 严格对称） ====================

    /**
     * 解码快照 payload（客户端缓存用）；越界/截断/尾部多余字节返回 null
     * （调用方丢弃整包，防撕裂）。
     */
    public static Snapshot readSnapshot(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return null;
        }
        try {
            ByteBuf buf = Unpooled.wrappedBuffer(payload);
            PacketBuffer pb = new PacketBuffer(buf);
            int flow = pb.readVarIntFromBuffer();
            if (!buf.isReadable()) {
                return null;
            }
            String heatCap = ByteBufUtils.readUTF8String(buf);
            if (buf.isReadable()) {
                return null; // 尾部多余字节：整包退化丢弃
            }
            return new Snapshot(flow, heatCap);
        } catch (RuntimeException e) {
            return null; // 越界/截断：整包退化丢弃
        }
    }

    /**
     * 快照显示数据（不可变）：设定流量（L/t）+ 理论最大热量字符串（服务端 heatCapDisplay 口径）。
     */
    public static final class Snapshot {

        /** 设定流量（L/t，服务端 setter 钳 ≥1 后的权威值） */
        public final int flow;
        /** 理论最大热量显示串（如 "250% (200×1.25)"） */
        public final String heatCap;

        public Snapshot(int flow, String heatCap) {
            this.flow = flow;
            this.heatCap = heatCap;
        }
    }
}
