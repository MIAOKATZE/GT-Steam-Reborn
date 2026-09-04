package com.miaokatze.gtsr.common.terminal;

import net.minecraft.network.PacketBuffer;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

/**
 * C2S 终端数据轮询请求（terminal-native-ui N25，PLAN §4.2/§4.3）。
 * <p>
 * 客户端 GUI updateScreen 周期发送（pollTimer 初值 0 首帧即发，GTSWN 轮询-应答范式）；
 * 服务端在主线程排水后执行：每玩家 ≥5t 频控（超出静默丢弃）+ TE 存活/距离复核，
 * 回 {@link PacketTerminalData}（复核失败回 valid=false → 客户端自关，等价旧轨
 * canInteractWith 64 格语义）。
 * <p>
 * 线上格式：uiType(varint) + dim(int) + x/y/z(int)。防御性读取：坏包退化
 * {@code corrupt=true}，服务端静默丢弃。
 */
public class PacketTerminalRequest implements IMessage {

    private TerminalUiType uiType;
    private int dim;
    private int x;
    private int y;
    private int z;
    /** 坏包退化标记：true 时服务端静默丢弃 */
    private boolean corrupt;

    public PacketTerminalRequest() {}

    public PacketTerminalRequest(TerminalUiType uiType, int dim, int x, int y, int z) {
        this.uiType = uiType;
        this.dim = dim;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            PacketBuffer pb = new PacketBuffer(buf);
            int uiTypeId = pb.readVarIntFromBuffer();
            this.dim = pb.readInt();
            this.x = pb.readInt();
            this.y = pb.readInt();
            this.z = pb.readInt();
            this.uiType = TerminalUiType.fromId(uiTypeId);
            if (this.uiType == null) {
                this.corrupt = true;
            }
        } catch (Exception e) {
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

    public boolean isCorrupt() {
        return this.corrupt;
    }
}
