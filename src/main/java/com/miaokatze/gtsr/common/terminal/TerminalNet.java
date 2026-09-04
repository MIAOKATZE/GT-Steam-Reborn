package com.miaokatze.gtsr.common.terminal;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.entity.player.EntityPlayerMP;

import com.miaokatze.gtsr.client.terminal.TerminalClientPacketSink;
import com.miaokatze.gtsr.common.machine.MTECrustMatterAggregator;
import com.miaokatze.gtsr.common.machine.MTESingularityDrillingHub;
import com.miaokatze.gtsr.common.machine.cluster.MTESteamMineralLogisticsCluster;
import com.miaokatze.gtsr.main.GTSteamReborn;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * 终端网络通道（terminal-native-ui N28，PLAN §4.2）：channel {@code "gtsr_terminal"}，
 * 独立于 gtsr_fx（粒子是效果面、终端是控制面，discriminator 空间不混用）；
 * 注册点在 {@code GTSRFXNet.init()} 尾部追加（M8）。
 * <p>
 * discriminator 分配（尾部追加）：
 * <ul>
 * <li>0 = {@link PacketOpenTerminalUi}（S2C，轨 A 纯展示 UI）</li>
 * <li>1 = {@link PacketTerminalRequest}（C2S 轮询请求）</li>
 * <li>2 = {@link PacketTerminalData}（S2C 数据回包）</li>
 * <li>3 = {@link PacketTerminalAction}（C2S 动作）</li>
 * </ul>
 * 线程纪律（GTSRFXNet 注释红线逐字继承）：common 侧 handler 零 lambda、零客户端类引用；
 * S2C 一律经 {@code TerminalClientPacketSink}（@SideOnly(CLIENT)）切客户端主线程；
 * C2S 一律 Netty 线程入队、{@link ServerDrain} 于 ServerTickEvent(END) 主线程排水后分发
 * （GTSWN PanelActionQueue/DeviceTerminalActionQueue 范式；1.7.10 服务端无
 * MinecraftServer.addScheduledTask，已对 RFG 反编译源核实，故用 tick 排水等价实现）。
 * <p>
 * 频控与防抖：请求包每玩家 ≥5t 一次（超出静默丢弃）；动作包 UUID+tick+action+参数摘要
 * 防抖（{@link TerminalServerSessions}）。payload 上限防护：单包 payload
 * ≤{@value #MAX_PAYLOAD_BYTES} 字节（PLAN §4.2）。
 * <p>
 * 服务端数据组装/动作执行 router：本文件单 switch（见 {@link #assembleData}/
 * {@link #executeAction}），stub 返回 valid=false / no-op，S3-S5 各自替换对应分支
 * （S3=SINGULARITY_HUB/STEAM_HUB/WATER_HUB 已落地，S4=AGGREGATOR 已落地，
 * S5=CLUSTER_TERMINAL）。
 */
public class TerminalNet {

    /** 单包 payload 字节上限（PLAN §4.2：列表条目上限之外的整体兜底） */
    public static final int MAX_PAYLOAD_BYTES = 32 * 1024;

    /** 请求频控间隔（tick）：每玩家 ≥5t 一次，超出静默丢弃 */
    private static final long REQUEST_COOLDOWN_TICKS = 5L;

    private static final SimpleNetworkWrapper NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel("gtsr_terminal");

    /** C2S 待处理任务（Netty 线程入队 → ServerTickEvent(END) 主线程排水） */
    private static final ConcurrentLinkedQueue<PendingTask> PENDING = new ConcurrentLinkedQueue<PendingTask>();

    /** 每玩家上次放行请求的服务端 tick（仅主线程访问：排水后分发） */
    private static final Map<UUID, Long> LAST_REQUEST_TICK = new HashMap<UUID, Long>();

    /**
     * 快照版本单调序列（PLAN §4.2：每服务端组装自增；客户端仅接受版本 ≥ 已缓存回包，
     * 防 Netty 迟到旧包回写覆盖新数据。仅 ServerDrain 主线程访问，与 {@link #LAST_REQUEST_TICK} 同口径）。
     */
    private static int SNAPSHOT_VERSION_SEQ;

    /**
     * 注册全部终端包与 C2S 排水监听。需在 {@code GTSRFXNet.init()} 尾部调用一次
     * （双端执行；注册顺序即 discriminator 编号，勿调整已占用编号）。
     */
    public static void register() {
        NETWORK.registerMessage(OpenHandler.class, PacketOpenTerminalUi.class, 0, Side.CLIENT);
        NETWORK.registerMessage(RequestHandler.class, PacketTerminalRequest.class, 1, Side.SERVER);
        NETWORK.registerMessage(DataHandler.class, PacketTerminalData.class, 2, Side.CLIENT);
        NETWORK.registerMessage(ActionHandler.class, PacketTerminalAction.class, 3, Side.SERVER);
        FMLCommonHandler.instance()
            .bus()
            .register(new ServerDrain());
    }

    // ==================== 服务端发送入口（M1-M6 触发点 S3-S5 接入） ====================

    /**
     * 服务端：通知玩家打开轨 A 终端 UI（无 windowId）。调用前须完成服务端复核
     * （基 TE 存活/机器类/物流模块换算等，逐字对齐旧 factory.open 分支）。
     */
    public static void sendOpen(TerminalUiType uiType, EntityPlayerMP player, int x, int y, int z, int initialPage) {
        NETWORK.sendTo(new PacketOpenTerminalUi(uiType, player.dimension, x, y, z, initialPage), player);
    }

    /** 服务端：回终端数据（复核失败传 valid=false，payload 传空数组） */
    private static void sendData(EntityPlayerMP player, TerminalUiType uiType, int dim, int x, int y, int z,
        boolean valid, int snapshotVersion, byte[] payload) {
        NETWORK.sendTo(new PacketTerminalData(uiType, dim, x, y, z, valid, snapshotVersion, payload), player);
    }

    /** 客户端：发送轮询请求（GUI updateScreen 周期调用，主线程） */
    public static void sendRequestFromClient(TerminalUiType uiType, int dim, int x, int y, int z) {
        NETWORK.sendToServer(new PacketTerminalRequest(uiType, dim, x, y, z));
    }

    /** 客户端：发送动作（参数仅索引/ordinal/名称等原始值，零计算结果回传） */
    public static void sendActionFromClient(TerminalUiType uiType, int dim, int x, int y, int z, int actionCode,
        byte[] payload) {
        NETWORK.sendToServer(new PacketTerminalAction(uiType, dim, x, y, z, actionCode, payload));
    }

    // ==================== C2S 主线程处理（ServerDrain 排水后调用） ====================

    /** 轮询请求处理：频控 ≥5t → TE 存活/距离复核 → assembleData router 回包 */
    private static void handleRequest(EntityPlayerMP player, PacketTerminalRequest msg) {
        if (msg.isCorrupt() || msg.getUiType() == null) {
            return; // 坏包静默丢弃
        }
        if (player == null || player.playerNetServerHandler == null) {
            return; // 已掉线
        }
        if (player.dimension != msg.getDim()) {
            return; // 跨维请求拒绝（64 格复核前提）
        }
        long now = TerminalServerSessions.currentServerTick();
        Long last = LAST_REQUEST_TICK.get(player.getUniqueID());
        if (last != null && now - last.longValue() < REQUEST_COOLDOWN_TICKS) {
            return; // 频控：超出 ≥5t 间隔静默丢弃
        }
        LAST_REQUEST_TICK.put(player.getUniqueID(), Long.valueOf(now));
        // 服务端复核（PLAN §4.1 轨 A-3）：TE 存活 && 距离 ≤64；失败回 valid=false → 客户端自关
        IGregTechTileEntity base = TerminalServerSessions
            .getAliveBaseTile(msg.getDim(), msg.getX(), msg.getY(), msg.getZ());
        if (base == null
            || !TerminalServerSessions.withinInteractionRange(player, msg.getX(), msg.getY(), msg.getZ())) {
            TerminalNet.sendData(
                player,
                msg.getUiType(),
                msg.getDim(),
                msg.getX(),
                msg.getY(),
                msg.getZ(),
                false,
                0,
                new byte[0]);
            return;
        }
        byte[] payload = TerminalNet.assembleData(msg.getUiType(), player, base);
        if (payload == null) {
            // 组装未实现（S3-S5 stub）：回 valid=false，客户端不得渲染
            TerminalNet.sendData(
                player,
                msg.getUiType(),
                msg.getDim(),
                msg.getX(),
                msg.getY(),
                msg.getZ(),
                false,
                0,
                new byte[0]);
            return;
        }
        TerminalNet.sendData(
            player,
            msg.getUiType(),
            msg.getDim(),
            msg.getX(),
            msg.getY(),
            msg.getZ(),
            true,
            ++SNAPSHOT_VERSION_SEQ,
            payload);
    }

    /** 动作处理：TE 存活/距离复核 → 防抖 → executeAction router（复核不通过一律静默拒绝） */
    private static void handleAction(EntityPlayerMP player, PacketTerminalAction msg) {
        if (msg.isCorrupt() || msg.getUiType() == null) {
            return; // 坏包静默丢弃（含异常长度即断标记）
        }
        if (player == null || player.playerNetServerHandler == null) {
            return;
        }
        if (player.dimension != msg.getDim()) {
            return;
        }
        IGregTechTileEntity base = TerminalServerSessions
            .getAliveBaseTile(msg.getDim(), msg.getX(), msg.getY(), msg.getZ());
        if (base == null
            || !TerminalServerSessions.withinInteractionRange(player, msg.getX(), msg.getY(), msg.getZ())) {
            return;
        }
        int digest = TerminalServerSessions.payloadDigest(msg.getPayload());
        if (TerminalServerSessions.isDuplicateAction(
            player.getUniqueID(),
            msg.getActionCode(),
            TerminalServerSessions.currentServerTick(),
            digest)) {
            return; // 重复包静默丢弃
        }
        TerminalNet.executeAction(msg.getUiType(), player, base, msg.getActionCode(), msg.getPayload());
    }

    /**
     * 服务端数据组装 router（单文件单 switch，S3-S5 各自替换本文件对应分支，其余勿动）：
     * <ul>
     * <li>S3（已落地）→ case SINGULARITY_HUB（N30）/ STEAM_HUB、WATER_HUB（N31）快照组装；</li>
     * <li>S4（已落地）→ case AGGREGATOR（N32）；</li>
     * <li>S5（已落地）→ case CLUSTER_TERMINAL（N33，24 键变化位图分型打包 + SampledValue 采样）。</li>
     * </ul>
     *
     * @return payload 字节（valid=true 回包），null = 组装未实现/复核失败（valid=false）
     */
    private static byte[] assembleData(TerminalUiType uiType, EntityPlayerMP player, IGregTechTileEntity base) {
        switch (uiType) {
            case SINGULARITY_HUB:
                if (base.getMetaTileEntity() instanceof MTESingularityDrillingHub hub) {
                    return SingularityHubTerminalData.assembleSnapshot(hub);
                }
                return null; // 机器类不符（锚点被换机器）：valid=false
            case STEAM_HUB:
            case WATER_HUB:
                return CacheHubTerminalData.assembleSnapshot(uiType, base.getMetaTileEntity());
            case CLUSTER_TERMINAL:
                if (base.getMetaTileEntity() instanceof MTESteamMineralLogisticsCluster cluster) {
                    return ClusterTerminalData.assembleSnapshot(cluster, player.getUniqueID());
                }
                return null; // 机器类不符（锚点被换机器）：valid=false
            case AGGREGATOR:
                if (base.getMetaTileEntity() instanceof MTECrustMatterAggregator aggregator) {
                    return AggregatorTerminalData.assembleSnapshot(aggregator);
                }
                return null; // 机器类不符（锚点被换机器）：valid=false
            default:
                return null;
        }
    }

    /**
     * 服务端动作执行 router（单文件单 switch，S3-S5 各自替换本文件对应分支，其余勿动）：
     * <ul>
     * <li>S3（已落地）→ case SINGULARITY_HUB（N30 五动作）/ STEAM_HUB、WATER_HUB（N31 六动作委托表）；</li>
     * <li>S4（已落地）→ case AGGREGATOR（N32 七动作）；</li>
     * <li>S5（已落地）→ case CLUSTER_TERMINAL（N34 枚举分发，占位动作服务端拒绝）。</li>
     * </ul>
     */
    private static void executeAction(TerminalUiType uiType, EntityPlayerMP player, IGregTechTileEntity base,
        int actionCode, byte[] payload) {
        switch (uiType) {
            case SINGULARITY_HUB:
                if (base.getMetaTileEntity() instanceof MTESingularityDrillingHub hub) {
                    SingularityHubTerminalData.executeAction(hub, player, actionCode, payload);
                }
                break; // 机器类不符：静默拒绝
            case STEAM_HUB:
            case WATER_HUB:
                CacheHubTerminalData.executeAction(uiType, base.getMetaTileEntity(), player, actionCode, payload);
                break; // 两薄壳委托表（N31）
            case CLUSTER_TERMINAL:
                if (base.getMetaTileEntity() instanceof MTESteamMineralLogisticsCluster cluster) {
                    ClusterTerminalData.executeAction(cluster, actionCode, payload);
                }
                break; // 机器类不符：静默拒绝
            case AGGREGATOR:
                if (base.getMetaTileEntity() instanceof MTECrustMatterAggregator aggregator) {
                    AggregatorTerminalData.executeAction(aggregator, player, actionCode, payload);
                }
                break; // 机器类不符：静默拒绝
            default:
                break;
        }
    }

    // ==================== C2S handler（Netty 线程：只入队，零世界访问） ====================

    /** C2S 轮询请求：Netty 线程入队，主线程排水（见 ServerDrain） */
    public static class RequestHandler implements IMessageHandler<PacketTerminalRequest, IMessage> {

        @Override
        public IMessage onMessage(PacketTerminalRequest msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            TerminalNet.PENDING.offer(new PendingTask(player, msg, null));
            return null;
        }
    }

    /** C2S 动作：Netty 线程入队，主线程排水（见 ServerDrain） */
    public static class ActionHandler implements IMessageHandler<PacketTerminalAction, IMessage> {

        @Override
        public IMessage onMessage(PacketTerminalAction msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            TerminalNet.PENDING.offer(new PendingTask(player, null, msg));
            return null;
        }
    }

    // ==================== S2C handler（@SideOnly 方法体服务端剥离，GTSRFXNet 先例） ====================

    /** S2C open 包：handler 只经 TerminalClientPacketSink 静态直调（PLAN §4.7-2，零 lambda 零 client import） */
    public static class OpenHandler implements IMessageHandler<PacketOpenTerminalUi, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketOpenTerminalUi msg, MessageContext ctx) {
            TerminalClientPacketSink.handleOpenScheduled(msg);
            return null;
        }
    }

    /** S2C data 包：同上，主线程写缓存分派（缓存就位前 sink 内忽略） */
    public static class DataHandler implements IMessageHandler<PacketTerminalData, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketTerminalData msg, MessageContext ctx) {
            TerminalClientPacketSink.handleDataScheduled(msg);
            return null;
        }
    }

    // ==================== 排水与任务载体 ====================

    /**
     * 自宿主排水监听：ServerTickEvent(END) 主线程逐条处理，单条异常仅记日志丢弃。
     * <p>
     * 可见性红线：FML 事件总线为每个 {@code @SubscribeEvent} 监听器由独立 classloader
     * 生成 {@code ASMEventHandler_<n>_<类>_<方法>} 包装类（落在 cpw.mods.fml.common.eventhandler
     * 包），跨包直接引用监听类——监听类必须 public，否则首个服务器 tick 即
     * {@code IllegalAccessError}（v1.11.27 实测崩溃 2026-09-04_12.45.12-server 教训；
     * 注册期走反射不报错，init 阶段无法暴露）。
     */
    public static final class ServerDrain {

        @SubscribeEvent
        public void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            PendingTask task;
            while ((task = TerminalNet.PENDING.poll()) != null) {
                try {
                    if (task.request != null) {
                        TerminalNet.handleRequest(task.player, task.request);
                    } else if (task.action != null) {
                        TerminalNet.handleAction(task.player, task.action);
                    }
                } catch (Throwable t) {
                    GTSteamReborn.LOG.error("[TerminalNet] 终端 C2S 任务应用异常（丢弃该条，继续后续）", t);
                }
            }
        }
    }

    /** C2S 待处理任务（request/action 二选一非空；fromBytes 后线程封闭，可直接引用传递） */
    private static final class PendingTask {

        final EntityPlayerMP player;
        final PacketTerminalRequest request;
        final PacketTerminalAction action;

        PendingTask(EntityPlayerMP player, PacketTerminalRequest request, PacketTerminalAction action) {
            this.player = player;
            this.request = request;
            this.action = action;
        }
    }
}
