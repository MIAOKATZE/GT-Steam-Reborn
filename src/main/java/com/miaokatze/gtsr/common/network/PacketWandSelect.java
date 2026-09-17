package com.miaokatze.gtsr.common.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

/**
 * 奇点调谐棒选择提交包（C2S，disc 0）：词条游标 + 增量档位索引（原始索引，钳位在服务端）。
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
