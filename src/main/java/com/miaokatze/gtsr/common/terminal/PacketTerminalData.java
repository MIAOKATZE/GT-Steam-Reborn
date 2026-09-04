package com.miaokatze.gtsr.common.terminal;

import net.minecraft.network.PacketBuffer;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

/**
 * S2C 终端数据回包（terminal-native-ui N26，PLAN §4.2/§4.3）。
 * <p>
 * 结构：uiType(varint) + dim/x/y/z(int) + valid(boolean) + snapshotVersion(int)
 * + 分型 payload（varint 长度 + 字节）。各 UI 的 payload 字段序冻结于 PLAN §4.3
 * 各表（A/B=节点列表、C=8 标量+矿石列表、D=24 键变化位图分型打包），由 S3-S5 的
 * 服务端组装类（N30-N33）与客户端缓存（N20-N22）填充/解码；本切片只承载协议骨架
 * 与防御性读入纪律。
 * <p>
 * snapshotVersion 语义（PLAN §4.2）：服务端每组装自增；客户端仅接受版本 ≥ 已缓存
 * 回包的包（防迟到旧包回写，单调门控由各客户端缓存持有）。
 * <p>
 * 防御性读取（GTSWN PacketSyncDeviceTerminalData 范式）：payload 长度异常（负数或
 * 超过 {@link TerminalNet#MAX_PAYLOAD_BYTES}）即断、不再继续读；截断/越界整包退化
 * {@code corrupt=true}，客户端判丢弃。
 */
public class PacketTerminalData implements IMessage {

    private TerminalUiType uiType;
    private int dim;
    private int x;
    private int y;
    private int z;
    /** false = 服务端复核失败（TE 失活/超 64 格/机器类不符/组装未实现）：客户端清缓存并自关 */
    private boolean valid;
    private int snapshotVersion;
    private byte[] payload;
    /** 坏包退化标记：true 时客户端静默丢弃 */
    private boolean corrupt;

    public PacketTerminalData() {}

    public PacketTerminalData(TerminalUiType uiType, int dim, int x, int y, int z, boolean valid, int snapshotVersion,
        byte[] payload) {
        this.uiType = uiType;
        this.dim = dim;
        this.x = x;
        this.y = y;
        this.z = z;
        this.valid = valid;
        this.snapshotVersion = snapshotVersion;
        this.payload = payload == null ? new byte[0] : payload;
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
            this.valid = pb.readBoolean();
            this.snapshotVersion = pb.readInt();
            int len = pb.readVarIntFromBuffer();
            if (len < 0 || len > TerminalNet.MAX_PAYLOAD_BYTES) {
                // 异常长度即断：不再继续读（PLAN R9，每个包独立 buffer 零副作用）
                this.corrupt = true;
                return;
            }
            this.payload = new byte[len];
            pb.readBytes(this.payload);
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
        byte[] data = this.payload == null ? new byte[0] : this.payload;
        pb.writeVarIntToBuffer(this.uiType == null ? 0 : this.uiType.ordinal());
        pb.writeInt(this.dim);
        pb.writeInt(this.x);
        pb.writeInt(this.y);
        pb.writeInt(this.z);
        pb.writeBoolean(this.valid);
        pb.writeInt(this.snapshotVersion);
        pb.writeVarIntToBuffer(data.length);
        pb.writeBytes(data);
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

    public boolean isValid() {
        return this.valid;
    }

    /** 快照版本（服务端每组装自增；客户端单调门控防迟到旧包） */
    public int getSnapshotVersion() {
        return this.snapshotVersion;
    }

    /** 分型 payload（只读语义：客户端缓存不得修改） */
    public byte[] getPayload() {
        return this.payload == null ? new byte[0] : this.payload;
    }

    public boolean isCorrupt() {
        return this.corrupt;
    }
}
