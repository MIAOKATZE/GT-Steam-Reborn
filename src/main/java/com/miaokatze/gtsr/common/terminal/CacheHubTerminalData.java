package com.miaokatze.gtsr.common.terminal;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.PacketBuffer;

import com.miaokatze.gtsr.common.machine.MTESteamHubArray;
import com.miaokatze.gtsr.common.machine.MTEWaterHubArray;
import com.miaokatze.gtsr.common.machine.base.MTEHubArrayBase;
import com.miaokatze.gtsr.main.GTSteamReborn;

import cpw.mods.fml.common.network.ByteBufUtils;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * 蒸汽/蓄水缓存枢纽终端数据（terminal-native-ui N31，PLAN §4.3-B）。
 * <p>
 * 快照组装：supplier 移植旧 MUI2 缓存枢纽状态 GUI 基类（git 基线 b4fabb2：
 * {@code common/gui/} 下同名状态界面源码）的
 * {@code () -> CacheNodeInfo.fromTagList(getCacheNodeListTag())} 语义——
 * 服务端每请求调用 {@link MTEHubArrayBase#getCacheNodeListTag()}，
 * 按旧 {@code CacheNodeInfo.fromTagList} 的取键序（x,y,z,dim,type,name,fluid,stored,
 * cap,rate,capPct,out,auto,modeLocked）读出 DTO，再按旧 {@code CacheNodeInfo.write}
 * 的字段序逐字编码（写入序与取键序一致，读写严格对称）。
 * <p>
 * 动作执行：actionCode 语义=旧 CacheHubActionSyncHandler 码冻结（1 CYCLE_RATE /
 * 2 SET_MODE / 3 RENAME / 4 SET_AUTO / 5 TELEPORT / 6 CYCLE_CAP），每个动作的服务端
 * 最终调用与旧两薄壳委托表逐字对应（继承自 MTEHubArrayBase 的同一批 public 方法，
 * 虚分发到对应 array 实例；PLAN §7.2 表；player 从包会话传入，等价旧
 * getSyncManager().getPlayer()）：
 * <ul>
 * <li>1 CYCLE_RATE[pos] → {@code hub.cycleCacheNodeRateFromGui(x,y,z,dim)}；</li>
 * <li>2 SET_MODE[pos][bool 目标值=客户端取反] → {@code hub.setCacheNodeModeFromGui(x,y,z,dim,output)}
 * （modeLocked 节点服务端整体拒改由机器方法自持，与旧一致）；</li>
 * <li>3 RENAME[pos][UTF name] → {@code hub.renameCacheNodeFromGui(x,y,z,dim,name)}
 * （服务端裁剪由机器方法自持）；</li>
 * <li>4 SET_AUTO[pos][bool 目标值=客户端取反] → {@code hub.setCacheNodeAutoFromGui(x,y,z,dim,auto)}；</li>
 * <li>5 TELEPORT[pos] → {@code hub.teleportPlayerToNodeFromGui(player,x,y,z,dim)}；</li>
 * <li>6 CYCLE_CAP[pos] → {@code hub.cycleCacheNodeCapFromGui(x,y,z,dim)}
 * （发送类仓 no-op 由机器方法自持，与旧一致）。</li>
 * </ul>
 * uiType 与机器类交叉复核：STEAM_HUB 只接受 {@link MTESteamHubArray}、WATER_HUB 只接受
 * {@link MTEWaterHubArray}（锚点被换机器时静默拒绝，等价旧 factory 机器类守卫）。
 * 防御性读取（PLAN R9）：动作 payload 变长段每步先验可读字节数，异常长度即断、静默拒绝。
 */
public final class CacheHubTerminalData {

    /** 列表条目上限（PLAN §4.2：hub 列表 256，超限截断 + 服务端 warn） */
    public static final int MAX_NODES = 256;

    // actionCode（旧 CacheHubActionSyncHandler 码冻结，禁止裸 int 新增语义）
    /** 循环传输速率（payload：pos） */
    public static final int ACTION_CYCLE_RATE = 1;
    /** 输出模式切换（payload：pos + 目标 bool） */
    public static final int ACTION_SET_MODE = 2;
    /** 重命名（payload：pos + UTF8 名） */
    public static final int ACTION_RENAME = 3;
    /** 自动输出开关切换（payload：pos + 目标 bool） */
    public static final int ACTION_SET_AUTO = 4;
    /** 传送（payload：pos） */
    public static final int ACTION_TELEPORT = 5;
    /** 容量上限档循环（S4；发送类仓 no-op 由机器方法自持）（payload：pos） */
    public static final int ACTION_CYCLE_CAP = 6;

    private CacheHubTerminalData() {}

    // ==================== 服务端快照组装 ====================

    /**
     * 组装缓存节点列表 payload（带 uiType × 机器类交叉复核）：
     * STEAM_HUB 只接受 {@link MTESteamHubArray}、WATER_HUB 只接受 {@link MTEWaterHubArray}，
     * 不符返回 null（valid=false）。
     */
    public static byte[] assembleSnapshot(TerminalUiType uiType, IMetaTileEntity mte) {
        if (!(mte instanceof MTEHubArrayBase hub)) {
            return null; // 非 hub array：静默拒绝
        }
        if (uiType == TerminalUiType.STEAM_HUB && !(mte instanceof MTESteamHubArray)) {
            return null; // uiType 与机器类不符：静默拒绝（等价旧 factory 机器类守卫）
        }
        if (uiType == TerminalUiType.WATER_HUB && !(mte instanceof MTEWaterHubArray)) {
            return null;
        }
        return assembleSnapshot(hub);
    }

    /**
     * 组装缓存节点列表 payload：{@code [count varint] × CacheNodeInfo 编码}。
     * 数据源 = {@code hub.getCacheNodeListTag()}（旧 syncValue supplier 语义移植）。
     */
    public static byte[] assembleSnapshot(MTEHubArrayBase hub) {
        NBTTagList tagList = hub.getCacheNodeListTag();
        final int total = tagList.tagCount();
        final int count = Math.min(total, MAX_NODES);
        if (total > MAX_NODES) {
            GTSteamReborn.LOG
                .warn("[TerminalNet] 缓存枢纽节点列表 {} 条超出单包上限 {}，已截断", Integer.valueOf(total), Integer.valueOf(MAX_NODES));
        }
        ByteBuf buf = Unpooled.buffer();
        PacketBuffer pb = new PacketBuffer(buf);
        pb.writeVarIntToBuffer(count);
        for (int i = 0; i < count; i++) {
            // 逐字段取键序 = 旧 CacheNodeInfo.fromTagList
            NBTTagCompound tag = tagList.getCompoundTagAt(i);
            write(
                pb,
                new CacheNodeInfo(
                    tag.getInteger("x"),
                    tag.getInteger("y"),
                    tag.getInteger("z"),
                    tag.getInteger("dim"),
                    tag.getString("type"),
                    tag.getString("name"),
                    tag.getString("fluid"),
                    tag.getLong("stored"),
                    tag.getLong("cap"),
                    tag.getInteger("rate"),
                    tag.getInteger("capPct"),
                    tag.getBoolean("out"),
                    tag.getBoolean("auto"),
                    tag.getBoolean("modeLocked")));
        }
        byte[] payload = new byte[buf.readableBytes()];
        buf.readBytes(payload);
        return payload;
    }

    // ==================== 服务端动作执行分发 ====================

    /**
     * 动作分发（主线程排水后调用）。payload 布局：pos（x,y,z,dim int×4）+
     * 动作参数（SET_MODE/SET_AUTO bool / RENAME UTF8）——与旧 readOnServer 的读序严格一致。
     */
    public static void executeAction(TerminalUiType uiType, IMetaTileEntity mte, EntityPlayer player, int actionCode,
        byte[] payload) {
        if (!(mte instanceof MTEHubArrayBase hub)) {
            return; // 非 hub array：静默拒绝
        }
        if (uiType == TerminalUiType.STEAM_HUB && !(mte instanceof MTESteamHubArray)) {
            return; // uiType 与机器类不符：静默拒绝（等价旧 factory 机器类守卫）
        }
        if (uiType == TerminalUiType.WATER_HUB && !(mte instanceof MTEWaterHubArray)) {
            return;
        }
        if (payload == null) {
            return;
        }
        ByteBuf buf = Unpooled.wrappedBuffer(payload);
        if (buf.readableBytes() < 16) {
            return; // pos 都读不全：异常长度即断（静默拒绝）
        }
        PacketBuffer pb = new PacketBuffer(buf);
        int x = pb.readInt();
        int y = pb.readInt();
        int z = pb.readInt();
        int dim = pb.readInt();
        switch (actionCode) {
            case ACTION_CYCLE_RATE:
                hub.cycleCacheNodeRateFromGui(x, y, z, dim);
                break;
            case ACTION_SET_MODE:
                if (buf.readableBytes() < 1) {
                    return;
                }
                hub.setCacheNodeModeFromGui(x, y, z, dim, pb.readBoolean());
                break;
            case ACTION_SET_AUTO:
                if (buf.readableBytes() < 1) {
                    return;
                }
                hub.setCacheNodeAutoFromGui(x, y, z, dim, pb.readBoolean());
                break;
            case ACTION_RENAME:
                if (!buf.isReadable()) {
                    return;
                }
                hub.renameCacheNodeFromGui(x, y, z, dim, ByteBufUtils.readUTF8String(buf));
                break;
            case ACTION_TELEPORT:
                hub.teleportPlayerToNodeFromGui(player, x, y, z, dim);
                break;
            case ACTION_CYCLE_CAP:
                hub.cycleCacheNodeCapFromGui(x, y, z, dim);
                break;
            default:
                return; // 未知动作码：静默拒绝
        }
    }

    // ==================== 编码函数（旧 CacheNodeInfo.read/write 移植） ====================

    /** 列表写入（字段序与旧 CacheNodeInfo.write 逐字一致） */
    public static void write(PacketBuffer pb, CacheNodeInfo info) {
        pb.writeInt(info.x);
        pb.writeInt(info.y);
        pb.writeInt(info.z);
        pb.writeInt(info.dim);
        ByteBufUtils.writeUTF8String(pb, info.type);
        ByteBufUtils.writeUTF8String(pb, info.name);
        ByteBufUtils.writeUTF8String(pb, info.fluid);
        pb.writeLong(info.stored);
        pb.writeLong(info.cap);
        pb.writeInt(info.rate);
        pb.writeInt(info.capPct);
        pb.writeBoolean(info.out);
        pb.writeBoolean(info.auto);
        pb.writeBoolean(info.modeLocked);
    }

    /** 列表读取（与 {@link #write} 严格对称；客户端缓存用） */
    public static CacheNodeInfo read(PacketBuffer pb) {
        return new CacheNodeInfo(
            pb.readInt(),
            pb.readInt(),
            pb.readInt(),
            pb.readInt(),
            ByteBufUtils.readUTF8String(pb),
            ByteBufUtils.readUTF8String(pb),
            ByteBufUtils.readUTF8String(pb),
            pb.readLong(),
            pb.readLong(),
            pb.readInt(),
            pb.readInt(),
            pb.readBoolean(),
            pb.readBoolean(),
            pb.readBoolean());
    }

    /**
     * 解码完整列表 payload（客户端缓存用）：{@code [count varint] × read}；
     * 截断/越界返回 null（调用方丢弃整包，防撕裂）。
     */
    public static List<CacheNodeInfo> readList(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return null;
        }
        try {
            ByteBuf buf = Unpooled.wrappedBuffer(payload);
            PacketBuffer pb = new PacketBuffer(buf);
            int count = pb.readVarIntFromBuffer();
            if (count < 0 || count > MAX_NODES) {
                return null;
            }
            List<CacheNodeInfo> list = new ArrayList<CacheNodeInfo>(count);
            for (int i = 0; i < count; i++) {
                list.add(read(pb));
            }
            if (buf.isReadable()) {
                return null; // 尾部多余字节：整包退化丢弃
            }
            return list;
        } catch (RuntimeException e) {
            return null; // 越界/截断：整包退化丢弃
        }
    }

    /**
     * 列表中单个缓存节点的显示数据（字段与旧 MUI2 缓存枢纽状态 GUI 的 CacheNodeInfo 一一对应，
     * 构造序=旧构造序；type 空串=节点离线无法解析）。
     */
    public static final class CacheNodeInfo {

        public final int x, y, z, dim;
        /** 节点类型串（steam/reinforced_steam/overpressure_steam/water 等）；空串表示节点离线（无法解析） */
        public final String type;
        /** 节点自定义名（空串表示未自定义，显示时回退默认类型名） */
        public final String name;
        /** 当前存储流体的注册名（空串表示无流体） */
        public final String fluid;
        /** 当前储量（long：强化/超压节点容量超出 int 范围） */
        public final long stored;
        /** 节点容量（long） */
        public final long cap;
        /** 传输速率百分比（100→80→…→0 循环档位） */
        public final int rate;
        /** 容量上限百分比（S4：100→80→…→5 循环档位；发送类仓恒 100 不适用） */
        public final int capPct;
        /** 方向模式（与枢纽 transferOneNode 实际流向一致）：true=枢纽→节点，false=节点→枢纽 */
        public final boolean out;
        /** 自动输出开关：true=节点向正面相邻容器推送流体（与方向模式解耦） */
        public final boolean auto;
        /** 输出模式锁定（奇点仓）；锁定时 GUI 模式按钮禁用。 */
        public final boolean modeLocked;

        public CacheNodeInfo(int x, int y, int z, int dim, String type, String name, String fluid, long stored,
            long cap, int rate, int capPct, boolean out, boolean auto, boolean modeLocked) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.dim = dim;
            this.type = type;
            this.name = name;
            this.fluid = fluid;
            this.stored = stored;
            this.cap = cap;
            this.rate = rate;
            this.capPct = capPct;
            this.out = out;
            this.auto = auto;
            this.modeLocked = modeLocked;
        }

        /** 坐标+维度命中（旧 findNode 键语义） */
        public boolean matchesPos(int px, int py, int pz, int pdim) {
            return this.x == px && this.y == py && this.z == pz && this.dim == pdim;
        }
    }
}
