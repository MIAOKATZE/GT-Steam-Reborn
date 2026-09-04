package com.miaokatze.gtsr.common.terminal;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.StatCollector;

import com.miaokatze.gtsr.common.machine.MTESingularityDrillingHub;
import com.miaokatze.gtsr.main.GTSteamReborn;

import cpw.mods.fml.common.network.ByteBufUtils;
import gregtech.api.util.GTUtility;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * 奇点钻井枢纽终端数据（terminal-native-ui N30，PLAN §4.3-A）。
 * <p>
 * 快照组装：supplier 移植旧 MUI2 钻井枢纽状态 GUI（git 基线 b4fabb2：
 * {@code common/gui/} 下同名状态界面源码）的
 * {@code () -> HubNodeInfo.fromTagList(hub.getNodeListTag())} 语义——
 * 服务端每请求调用 {@link MTESingularityDrillingHub#getNodeListTag()}，
 * 按旧 {@code HubNodeInfo.fromTagList} 的取键序（x,y,z,dim,isMiner,tier,working,
 * allowed,retractable,recyclable,name）读出 DTO，再按旧 {@code HubNodeInfo.write}
 * 的字段序逐字编码（写入序与取键序一致，读写严格对称）。
 * <p>
 * 动作执行：actionCode 语义=旧 HubActionSyncHandler 码冻结（1 TOGGLE / 2 RECYCLE /
 * 3 UPGRADE / 4 RENAME / 5 TELEPORT），每个动作的服务端最终调用与旧 readOnServer
 * 逐行同参（PLAN §7.1 表；player 从包会话传入，等价旧 getSyncManager().getPlayer()）：
 * <ul>
 * <li>1 TOGGLE[pos][bool 目标值=客户端取反] → {@code hub.setNodeActiveFromGui(x,y,z,dim,active)}；</li>
 * <li>2 RECYCLE[pos] → {@code hub.recycleNodeFromGui(x,y,z,dim)}，false 时服务端走原 chat
 * {@code gtsr.hub_status.recycle_fail}（红字通道保留）；</li>
 * <li>3 UPGRADE[pos] → {@code hub.upgradeNodeFromGui(player,x,y,z,dim)}（返回值忽略，与旧一致）；</li>
 * <li>4 RENAME[pos][UTF name] → {@code hub.renameNodeFromGui(x,y,z,dim,name)}
 * （服务端裁剪剔 §/去空白/≤24 由机器方法自持，与旧一致）；</li>
 * <li>5 TELEPORT[pos] → {@code hub.teleportPlayerToNodeFromGui(player,x,y,z,dim)}。</li>
 * </ul>
 * 防御性读取（PLAN R9）：动作 payload 变长段每步先验可读字节数，异常长度即断、静默拒绝
 * （坐标解析失败在机器方法内建 resolve 静默，与旧一致）。
 */
public final class SingularityHubTerminalData {

    /** 列表条目上限（PLAN §4.2：hub 列表 256，超限截断 + 服务端 warn） */
    public static final int MAX_NODES = 256;

    // actionCode（旧 HubActionSyncHandler 码冻结，禁止裸 int 新增语义）
    /** 开始/停止切换（payload：pos + 目标 bool） */
    public static final int ACTION_TOGGLE = 1;
    /** 快捷回收（payload：pos） */
    public static final int ACTION_RECYCLE = 2;
    /** 消耗背包升级（payload：pos） */
    public static final int ACTION_UPGRADE = 3;
    /** 重命名（payload：pos + UTF8 名） */
    public static final int ACTION_RENAME = 4;
    /** 传送（payload：pos） */
    public static final int ACTION_TELEPORT = 5;

    private SingularityHubTerminalData() {}

    // ==================== 服务端快照组装 ====================

    /**
     * 组装节点列表 payload：{@code [count varint] × HubNodeInfo 编码}。
     * 数据源 = {@code hub.getNodeListTag()}（旧 syncValue supplier 语义移植）。
     */
    public static byte[] assembleSnapshot(MTESingularityDrillingHub hub) {
        NBTTagList tagList = hub.getNodeListTag();
        final int total = tagList.tagCount();
        final int count = Math.min(total, MAX_NODES);
        if (total > MAX_NODES) {
            GTSteamReborn.LOG
                .warn("[TerminalNet] 奇点钻井枢纽节点列表 {} 条超出单包上限 {}，已截断", Integer.valueOf(total), Integer.valueOf(MAX_NODES));
        }
        ByteBuf buf = Unpooled.buffer();
        PacketBuffer pb = new PacketBuffer(buf);
        pb.writeVarIntToBuffer(count);
        for (int i = 0; i < count; i++) {
            // 逐字段取键序 = 旧 HubNodeInfo.fromTagList
            NBTTagCompound tag = tagList.getCompoundTagAt(i);
            write(
                pb,
                new HubNodeInfo(
                    tag.getInteger("x"),
                    tag.getInteger("y"),
                    tag.getInteger("z"),
                    tag.getInteger("dim"),
                    tag.getBoolean("isMiner"),
                    tag.getInteger("tier"),
                    tag.getBoolean("working"),
                    tag.getBoolean("allowed"),
                    tag.getBoolean("retractable"),
                    tag.getBoolean("recyclable"),
                    tag.getString("name")));
        }
        byte[] payload = new byte[buf.readableBytes()];
        buf.readBytes(payload);
        return payload;
    }

    // ==================== 服务端动作执行分发 ====================

    /**
     * 动作分发（主线程排水后调用）。payload 布局：pos（x,y,z,dim int×4）+
     * 动作参数（TOGGLE bool / RENAME UTF8）——与旧 readOnServer 的读序严格一致。
     */
    public static void executeAction(MTESingularityDrillingHub hub, EntityPlayer player, int actionCode,
        byte[] payload) {
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
            case ACTION_TOGGLE:
                if (buf.readableBytes() < 1) {
                    return;
                }
                hub.setNodeActiveFromGui(x, y, z, dim, pb.readBoolean());
                break;
            case ACTION_RECYCLE:
                if (!hub.recycleNodeFromGui(x, y, z, dim)) {
                    GTUtility.sendChatToPlayer(player, StatCollector.translateToLocal("gtsr.hub_status.recycle_fail"));
                }
                break;
            case ACTION_UPGRADE:
                hub.upgradeNodeFromGui(player, x, y, z, dim);
                break;
            case ACTION_RENAME:
                if (!buf.isReadable()) {
                    return;
                }
                hub.renameNodeFromGui(x, y, z, dim, ByteBufUtils.readUTF8String(buf));
                break;
            case ACTION_TELEPORT:
                hub.teleportPlayerToNodeFromGui(player, x, y, z, dim);
                break;
            default:
                return; // 未知动作码：静默拒绝
        }
    }

    // ==================== 编码函数（旧 HubNodeInfo.read/write 移植） ====================

    /** 列表写入（字段序与旧 HubNodeInfo.write 逐字一致：recyclable 在 retractable 之后、name 之前） */
    public static void write(PacketBuffer pb, HubNodeInfo info) {
        pb.writeInt(info.x);
        pb.writeInt(info.y);
        pb.writeInt(info.z);
        pb.writeInt(info.dim);
        pb.writeBoolean(info.isMiner);
        pb.writeInt(info.tier);
        pb.writeBoolean(info.working);
        pb.writeBoolean(info.allowed);
        pb.writeBoolean(info.retractable);
        pb.writeBoolean(info.recyclable);
        ByteBufUtils.writeUTF8String(pb, info.name);
    }

    /** 列表读取（与 {@link #write} 严格对称；客户端缓存用） */
    public static HubNodeInfo read(PacketBuffer pb) {
        return new HubNodeInfo(
            pb.readInt(),
            pb.readInt(),
            pb.readInt(),
            pb.readInt(),
            pb.readBoolean(),
            pb.readInt(),
            pb.readBoolean(),
            pb.readBoolean(),
            pb.readBoolean(),
            pb.readBoolean(),
            ByteBufUtils.readUTF8String(pb));
    }

    /**
     * 解码完整列表 payload（客户端缓存用）：{@code [count varint] × read}；
     * 截断/越界返回 null（调用方丢弃整包，防撕裂）。
     */
    public static List<HubNodeInfo> readList(byte[] payload) {
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
            List<HubNodeInfo> list = new ArrayList<HubNodeInfo>(count);
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
     * 列表中单个节点的显示数据（字段与旧 MUI2 钻井枢纽状态 GUI 的 HubNodeInfo 一一对应，
     * 构造序=旧构造序）。
     */
    public static final class HubNodeInfo {

        public final int x, y, z, dim;
        public final boolean isMiner;
        public final int tier;
        /** 节点是否实际工作中（消耗蒸汽） */
        public final boolean working;
        /** 节点底座 allowedToWork 标志（开始/停止按钮的当前状态） */
        public final boolean allowed;
        /** 是否完全停止（管道全部收回且未工作） */
        public final boolean retractable;
        /** 是否可回收（停止或待机即可，回收按钮可用状态） */
        public final boolean recyclable;
        /** 节点自定义名（空串表示未自定义，显示时回退默认类型名） */
        public final String name;

        public HubNodeInfo(int x, int y, int z, int dim, boolean isMiner, int tier, boolean working, boolean allowed,
            boolean retractable, boolean recyclable, String name) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.dim = dim;
            this.isMiner = isMiner;
            this.tier = tier;
            this.working = working;
            this.allowed = allowed;
            this.retractable = retractable;
            this.recyclable = recyclable;
            this.name = name;
        }

        /** 坐标+维度命中（旧 findNode 键语义） */
        public boolean matchesPos(int px, int py, int pz, int pdim) {
            return this.x == px && this.y == py && this.z == pz && this.dim == pdim;
        }
    }
}
