package com.miaokatze.gtsr.common.terminal;

import net.minecraft.network.PacketBuffer;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

/**
 * S2C 打开终端 UI 通知（terminal-native-ui N24，PLAN §4.1 轨 A / §4.2）。
 * <p>
 * 轨 A 的四个纯展示 UI 不走 openGui（防 windowId 污染背包容器，wiki
 * fml-opengui-windowid-semantics §3）：服务端复核通过后发本包，客户端
 * {@code TerminalClientPacketSink} 在主线程双校验（dim 匹配 + pos 处 TE
 * 为目标机器类），不符静默忽略（防伪造/竞态钓鱼，PLAN R3）。
 * <p>
 * 线上格式：uiType(varint) + dim(int) + x/y/z(int) + initialPage(varint，
 * 仅 CLUSTER_TERMINAL 有意义，其余类型恒 0)。防御性读取：坏包退化
 * {@code corrupt=true}，客户端判丢弃（GTSWN PacketSyncDeviceTerminalData 惰性丢弃范式）。
 */
public class PacketOpenTerminalUi implements IMessage {

    private TerminalUiType uiType;
    private int dim;
    private int x;
    private int y;
    private int z;
    private int initialPage;
    /** 坏包退化标记：true 时客户端静默丢弃（uiType 未知/读越界/异常长度） */
    private boolean corrupt;

    public PacketOpenTerminalUi() {}

    public PacketOpenTerminalUi(TerminalUiType uiType, int dim, int x, int y, int z, int initialPage) {
        this.uiType = uiType;
        this.dim = dim;
        this.x = x;
        this.y = y;
        this.z = z;
        this.initialPage = initialPage;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            PacketBuffer pb = new PacketBuffer(buf);
            // 结构先整包读出再判定（读序与 toBytes 严格一致）
            int uiTypeId = pb.readVarIntFromBuffer();
            this.dim = pb.readInt();
            this.x = pb.readInt();
            this.y = pb.readInt();
            this.z = pb.readInt();
            this.initialPage = pb.readVarIntFromBuffer();
            this.uiType = TerminalUiType.fromId(uiTypeId);
            if (this.uiType == null) {
                this.corrupt = true;
            }
        } catch (Exception e) {
            // 截断/越界：退化丢弃
            this.corrupt = true;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketBuffer pb = new PacketBuffer(buf);
        pb.writeVarIntToBuffer(this.uiType == null ? 0 : this.uiType.ordinal());
        pb.writeInt(this.dim);
        pb.writeInt(this.x);
        pb.writeInt(this.y);
        pb.writeInt(this.z);
        pb.writeVarIntToBuffer(this.initialPage);
    }

    public TerminalUiType getUiType() {
        return this.uiType;
    }

    public int getDim() {
        return this.dim;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public int getZ() {
        return this.z;
    }

    /** 初始页（仅 CLUSTER_TERMINAL 有意义；物流模块兼容入口 = PAGE_CHAIN_EDIT） */
    public int getInitialPage() {
        return this.initialPage;
    }

    public boolean isCorrupt() {
        return this.corrupt;
    }
}
