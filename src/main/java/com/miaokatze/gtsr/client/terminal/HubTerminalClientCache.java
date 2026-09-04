package com.miaokatze.gtsr.client.terminal;

import java.util.List;

import net.minecraft.network.PacketBuffer;

import com.miaokatze.gtsr.common.terminal.CacheHubTerminalData;
import com.miaokatze.gtsr.common.terminal.PacketTerminalData;
import com.miaokatze.gtsr.common.terminal.SingularityHubTerminalData;
import com.miaokatze.gtsr.common.terminal.TerminalUiType;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.Unpooled;

/**
 * 枢纽终端客户端静态缓存（terminal-native-ui N20，PLAN §4.3-A/B）。
 * <p>
 * 双列表分仓：drilling（奇点钻井枢纽节点）/ cache（蒸汽/蓄水枢纽缓存节点）。
 * <ul>
 * <li><b>锚点匹配</b>：每份快照携带 pos+dim，GUI 只渲染锚点与自身一致的快照
 * （防跨机错位，GTSWN GuiQuantumTerminal :79-84 范式）；锚点不同的新包整体换仓。</li>
 * <li><b>整体替换防撕裂</b>：解码成功后以不可变快照对象一次赋值替换，绘制侧只会读到
 * 完整旧快照或完整新快照（GTSWN DeviceTerminalClientCache :28-69 语义）。</li>
 * <li><b>snapshotVersion 单调门控</b>：同锚点仅接受版本 ≥ 已缓存回包的包
 * （防 Netty 迟到旧包回写；当前服务端每包独立组装，位留作总装后增量回包的防回退闸）。</li>
 * </ul>
 * 线程模型：写入仅在客户端主线程（TerminalClientPacketSink 切线程后调用），
 * 绘制/轮询亦在主线程；volatile 修饰作为跨线程可见性的兜底防御。
 */
@SideOnly(Side.CLIENT)
public final class HubTerminalClientCache {

    /** 奇点钻井枢纽节点列表快照（不可变） */
    public static final class DrillingSnapshot {

        public final int x, y, z, dim;
        public final int snapshotVersion;
        public final List<SingularityHubTerminalData.HubNodeInfo> nodes;

        DrillingSnapshot(int x, int y, int z, int dim, int snapshotVersion,
            List<SingularityHubTerminalData.HubNodeInfo> nodes) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.dim = dim;
            this.snapshotVersion = snapshotVersion;
            this.nodes = nodes;
        }

        /** @return 本快照锚点是否为给定 pos+dim（GUI 渲染前置校验） */
        public boolean matchesAnchor(int px, int py, int pz, int pdim) {
            return this.x == px && this.y == py && this.z == pz && this.dim == pdim;
        }
    }

    /** 蒸汽/蓄水枢纽缓存节点列表快照（不可变） */
    public static final class CacheSnapshot {

        public final int x, y, z, dim;
        public final int snapshotVersion;
        public final List<CacheHubTerminalData.CacheNodeInfo> nodes;

        CacheSnapshot(int x, int y, int z, int dim, int snapshotVersion,
            List<CacheHubTerminalData.CacheNodeInfo> nodes) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.dim = dim;
            this.snapshotVersion = snapshotVersion;
            this.nodes = nodes;
        }

        /** @return 本快照锚点是否为给定 pos+dim（GUI 渲染前置校验） */
        public boolean matchesAnchor(int px, int py, int pz, int pdim) {
            return this.x == px && this.y == py && this.z == pz && this.dim == pdim;
        }
    }

    private static volatile DrillingSnapshot drilling;
    private static volatile CacheSnapshot cache;

    private HubTerminalClientCache() {}

    /** @return 当前钻井枢纽快照（无回包/已失效为 null；GUI 须再验锚点） */
    public static DrillingSnapshot drillingSnapshot() {
        return drilling;
    }

    /** @return 当前缓存枢纽快照（无回包/已失效为 null；GUI 须再验锚点） */
    public static CacheSnapshot cacheSnapshot() {
        return cache;
    }

    /**
     * 收到 valid=true 回包：按 uiType 解码 payload 并整体替换对应仓
     * （snapshotVersion 单调门控；解码失败整包丢弃）。
     */
    public static void accept(PacketTerminalData msg) {
        final int x = msg.getX();
        final int y = msg.getY();
        final int z = msg.getZ();
        final int dim = msg.getDim();
        final int version = msg.getSnapshotVersion();
        if (msg.getUiType() == TerminalUiType.SINGULARITY_HUB) {
            List<SingularityHubTerminalData.HubNodeInfo> nodes = SingularityHubTerminalData.readList(msg.getPayload());
            if (nodes == null) {
                return; // 解码失败：整包丢弃（保留旧快照，防撕裂）
            }
            DrillingSnapshot prev = drilling;
            if (prev != null && prev.matchesAnchor(x, y, z, dim) && version < prev.snapshotVersion) {
                return; // 迟到旧包：单调门控拒绝
            }
            drilling = new DrillingSnapshot(x, y, z, dim, version, nodes);
        } else if (msg.getUiType() == TerminalUiType.STEAM_HUB || msg.getUiType() == TerminalUiType.WATER_HUB) {
            List<CacheHubTerminalData.CacheNodeInfo> nodes = CacheHubTerminalData.readList(msg.getPayload());
            if (nodes == null) {
                return; // 解码失败：整包丢弃（保留旧快照，防撕裂）
            }
            CacheSnapshot prev = cache;
            if (prev != null && prev.matchesAnchor(x, y, z, dim) && version < prev.snapshotVersion) {
                return; // 迟到旧包：单调门控拒绝
            }
            cache = new CacheSnapshot(x, y, z, dim, version, nodes);
        }
    }

    /**
     * 失效清理（valid=false 回包）：仅当缓存锚点与回包锚点一致时清空对应仓
     * （跨锚点的旧失效包不清新仓）。STEAM/WATER 共用 cache 仓，回包锚点即凭据。
     */
    public static void invalidate(PacketTerminalData msg) {
        final int x = msg.getX();
        final int y = msg.getY();
        final int z = msg.getZ();
        final int dim = msg.getDim();
        if (msg.getUiType() == TerminalUiType.SINGULARITY_HUB) {
            DrillingSnapshot prev = drilling;
            if (prev != null && prev.matchesAnchor(x, y, z, dim)) {
                drilling = null;
            }
        } else if (msg.getUiType() == TerminalUiType.STEAM_HUB || msg.getUiType() == TerminalUiType.WATER_HUB) {
            CacheSnapshot prev = cache;
            if (prev != null && prev.matchesAnchor(x, y, z, dim)) {
                cache = null;
            }
        }
    }

    /** 动作 payload 构造（客户端统一入口）：pos（x,y,z,dim int×4） */
    public static byte[] posPayload(int x, int y, int z, int dim) {
        PacketBuffer pb = new PacketBuffer(Unpooled.buffer(16));
        pb.writeInt(x);
        pb.writeInt(y);
        pb.writeInt(z);
        pb.writeInt(dim);
        return readAll(pb);
    }

    /** 动作 payload 构造：pos + bool（TOGGLE/SET_MODE/SET_AUTO 的目标值，与旧 send 序一致） */
    public static byte[] posBoolPayload(int x, int y, int z, int dim, boolean value) {
        PacketBuffer pb = new PacketBuffer(Unpooled.buffer(17));
        pb.writeInt(x);
        pb.writeInt(y);
        pb.writeInt(z);
        pb.writeInt(dim);
        pb.writeBoolean(value);
        return readAll(pb);
    }

    /** 动作 payload 构造：pos + UTF8 名（RENAME；null 归一为空串，与旧 sendRename 一致） */
    public static byte[] posNamePayload(int x, int y, int z, int dim, String name) {
        PacketBuffer pb = new PacketBuffer(Unpooled.buffer(32));
        pb.writeInt(x);
        pb.writeInt(y);
        pb.writeInt(z);
        pb.writeInt(dim);
        ByteBufUtils.writeUTF8String(pb, name == null ? "" : name);
        return readAll(pb);
    }

    private static byte[] readAll(PacketBuffer pb) {
        byte[] payload = new byte[pb.readableBytes()];
        pb.readBytes(payload);
        return payload;
    }
}
