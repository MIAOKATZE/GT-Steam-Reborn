package com.miaokatze.gtsr.client.terminal;

import net.minecraft.network.PacketBuffer;

import com.miaokatze.gtsr.common.terminal.AggregatorTerminalData;
import com.miaokatze.gtsr.common.terminal.PacketTerminalData;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.Unpooled;

/**
 * 聚合器终端客户端静态缓存（terminal-native-ui N21，PLAN §4.3-C）。
 * <p>
 * 同 {@link HubTerminalClientCache} 范式：
 * <ul>
 * <li><b>锚点匹配</b>：快照携带 pos+dim，GUI 只渲染锚点与自身一致的快照（防跨机错位）；</li>
 * <li><b>整体替换防撕裂</b>：解码成功后以不可变快照一次赋值替换（绘制侧只会读到完整旧/新快照）；</li>
 * <li><b>snapshotVersion 单调门控</b>：同锚点仅接受版本 ≥ 已缓存回包（防迟到旧包回写；
 * 当前服务端每包独立组装恒 version=0，位留作总装后增量回包的防回退闸）。</li>
 * </ul>
 * 内容 = 8 标量（oreMode/fortune/steamMult/denseState/directionalMode/uuMult/
 * weightIncrease/dimIncrease）+ oreList（≤512 条），由
 * {@link AggregatorTerminalData#readSnapshot} 解码。
 * <p>
 * 线程模型：写入仅在客户端主线程（TerminalClientPacketSink 切线程后调用），
 * 绘制/轮询亦在主线程；volatile 修饰作为跨线程可见性的兜底防御。
 */
@SideOnly(Side.CLIENT)
public final class AggregatorClientCache {

    /** 锚点化快照（不可变） */
    public static final class Snapshot {

        public final int x, y, z, dim;
        public final int snapshotVersion;
        public final AggregatorTerminalData.Snapshot data;

        Snapshot(int x, int y, int z, int dim, int snapshotVersion, AggregatorTerminalData.Snapshot data) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.dim = dim;
            this.snapshotVersion = snapshotVersion;
            this.data = data;
        }

        /** @return 本快照锚点是否为给定 pos+dim（GUI 渲染前置校验） */
        public boolean matchesAnchor(int px, int py, int pz, int pdim) {
            return this.x == px && this.y == py && this.z == pz && this.dim == pdim;
        }
    }

    private static volatile Snapshot snapshot;

    private AggregatorClientCache() {}

    /** @return 当前聚合器快照（无回包/已失效为 null；GUI 须再验锚点） */
    public static Snapshot snapshot() {
        return snapshot;
    }

    /**
     * 收到 valid=true 回包：解码 payload 并整体替换缓存
     * （snapshotVersion 单调门控；解码失败整包丢弃）。
     */
    public static void accept(PacketTerminalData msg) {
        final int x = msg.getX();
        final int y = msg.getY();
        final int z = msg.getZ();
        final int dim = msg.getDim();
        final int version = msg.getSnapshotVersion();
        AggregatorTerminalData.Snapshot data = AggregatorTerminalData.readSnapshot(msg.getPayload());
        if (data == null) {
            return; // 解码失败：整包丢弃（保留旧快照，防撕裂）
        }
        Snapshot prev = snapshot;
        if (prev != null && prev.matchesAnchor(x, y, z, dim) && version < prev.snapshotVersion) {
            return; // 迟到旧包：单调门控拒绝
        }
        snapshot = new Snapshot(x, y, z, dim, version, data);
    }

    /**
     * 失效清理（valid=false 回包）：仅当缓存锚点与回包锚点一致时清空
     * （跨锚点的旧失效包不清新仓）。
     */
    public static void invalidate(PacketTerminalData msg) {
        Snapshot prev = snapshot;
        if (prev != null && prev.matchesAnchor(msg.getX(), msg.getY(), msg.getZ(), msg.getDim())) {
            snapshot = null;
        }
    }

    // ==================== 动作 payload 构造（客户端统一入口） ====================

    /** 无参动作 payload（CYCLE_ORE_MODE/CYCLE_FORTUNE/REFRESH_POOL/TOGGLE_DIRECTIONAL/CLEAR_CONFIG） */
    public static byte[] emptyPayload() {
        return new byte[0];
    }

    /**
     * 单矿动作 payload（TOGGLE_FILTER/TOGGLE_DIRECTIONAL_ORE）：
     * uniqueId UTF + meta int——旧 sendToggleFilter/sendToggleDirectionalOre 写序逐字保留
     * （旧源 :806-812；uid 解析失败服务端静默）。
     */
    public static byte[] oreIdPayload(String uniqueId, int meta) {
        PacketBuffer pb = new PacketBuffer(Unpooled.buffer(16));
        ByteBufUtils.writeUTF8String(pb, uniqueId == null ? "" : uniqueId);
        pb.writeInt(meta);
        byte[] payload = new byte[pb.readableBytes()];
        pb.readBytes(payload);
        return payload;
    }
}
