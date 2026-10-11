package com.miaokatze.gtsr.common.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

/**
 * 兼容 Shift 空气选择界面的 C2S 包；服务端拒绝越界值并更新奇点族 profile。
 */
public class PacketWandSelect implements IMessage {

    public int entry;
    public int deltaIndex;

    public PacketWandSelect() {}

    public PacketWandSelect(int entry, int deltaIndex) {
        this.entry = entry;
        this.deltaIndex = deltaIndex;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.entry = buf.readByte();
        this.deltaIndex = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(this.entry);
        buf.writeByte(this.deltaIndex);
    }
}
