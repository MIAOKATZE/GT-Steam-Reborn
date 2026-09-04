package com.miaokatze.gtsr.common.terminal;

import net.minecraft.network.PacketBuffer;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

/**
 * C2S 终端动作包（terminal-native-ui N27，PLAN §4.2/§4.3）。
 * <p>
 * 结构：uiType(varint) + dim/x/y/z(int) + actionCode(varint) + payload
 * （varint 长度 + 字节）。actionCode 语义=旧码冻结（PLAN §4.3 各表；
 * 集群终端动作枚举见 {@link ClusterTerminalActions}，线上码=ordinal）。
 * <p>
 * 线程与防抖纪律（PLAN §4.2，移植原集群动作处理 :663-715）：服务端 Netty 线程
 * 收包后经 {@link TerminalNet} 入队、ServerTickEvent(END) 主线程排水；
 * <b>整包先读出再判定</b>（本类 fromBytes 承担，读序与写序严格一致），变长 payload
 * 以 {@code Arrays.hashCode} 摘要参与防抖（{@link TerminalServerSessions}），
 * 异常长度即断、不再继续读（每个 C2S 包独立 buffer，伪造包零副作用，PLAN R9）。
 */
public class PacketTerminalAction implements IMessage {

    private TerminalUiType uiType;
    private int dim;
    private int x;
    private int y;
    private int z;
    private int actionCode;
    private byte[] payload;
    /** 坏包退化标记：true 时服务端静默丢弃 */
    private boolean corrupt;

    public PacketTerminalAction() {}

    public PacketTerminalAction(TerminalUiType uiType, int dim, int x, int y, int z, int actionCode, byte[] payload) {
        this.uiType = uiType;
        this.dim = dim;
        this.x = x;
        this.y = y;
        this.z = z;
        this.actionCode = actionCode;
        this.payload = payload == null ? new byte[0] : payload;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            PacketBuffer pb = new PacketBuffer(buf);
            // 整包先读出再判定（读序与 toBytes 严格一致）
            int uiTypeId = pb.readVarIntFromBuffer();
            this.dim = pb.readInt();
            this.x = pb.readInt();
            this.y = pb.readInt();
            this.z = pb.readInt();
            this.actionCode = pb.readVarIntFromBuffer();
            int len = pb.readVarIntFromBuffer();
            if (len < 0 || len > TerminalNet.MAX_PAYLOAD_BYTES) {
                // 异常长度即断：不继续读（伪造包零副作用）
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
        pb.writeVarIntToBuffer(this.actionCode);
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

    /** 动作码（线上码=旧码/ordinal 冻结，禁止裸 int 新增语义） */
    public int getActionCode() {
        return this.actionCode;
    }

    /** 变长动作参数（只读语义；防抖摘要 = Arrays.hashCode） */
    public byte[] getPayload() {
        return this.payload == null ? new byte[0] : this.payload;
    }

    public boolean isCorrupt() {
        return this.corrupt;
    }
}
