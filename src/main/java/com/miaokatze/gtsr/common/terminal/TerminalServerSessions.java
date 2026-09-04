package com.miaokatze.gtsr.common.terminal;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * 终端服务端会话公共件（terminal-native-ui N29，PLAN §4.2）。
 * <p>
 * C2S 动作防抖表（移植原集群动作处理 :663-715 纪律）：
 * <ul>
 * <li><b>整包先读出再判定</b>：由各包类 fromBytes 承担（读序与写序严格一致），
 * 防抖判定发生在排水分发之前；</li>
 * <li><b>变长 payload 摘要</b>：{@link #payloadDigest}（Arrays.hashCode），
 * 异常长度在 fromBytes 即断、不参与防抖；</li>
 * <li><b>防抖键</b>：同玩家 + 同 tick + 同 actionCode + 同参数摘要 → 重复包静默丢弃。</li>
 * </ul>
 * 服务端复核公共方法（PLAN §4.1 轨 A-3）：TE 存活复核、64 格距离复核
 * （口径 = 原服务端交互距离常量）。
 * 全部状态仅服务端主线程访问（TerminalNet 排水后分发），无需并发容器。
 */
public final class TerminalServerSessions {

    /** 终端交互复核距离（格），与旧 MUI2 轨 canInteractWith 同口径 */
    public static final double MAX_INTERACTION_DISTANCE = 64.0D;

    /** 每玩家最近一次放行的动作（防抖：uuid+tick+action+摘要全同即重复） */
    private static final Map<UUID, DebounceEntry> LAST_ACTION = new HashMap<UUID, DebounceEntry>();

    private TerminalServerSessions() {}

    /**
     * 防抖判定（主线程调用）：同玩家 + 同 tick + 同 actionCode + 同参数摘要视为重复包，
     * 静默丢弃；否则记录本次动作并放行。
     *
     * @return true = 重复包（调用方静默丢弃）
     */
    public static boolean isDuplicateAction(UUID playerId, int actionCode, long serverTick, int paramDigest) {
        if (playerId == null) {
            return false;
        }
        DebounceEntry last = LAST_ACTION.get(playerId);
        if (last != null && last.actionCode == actionCode
            && last.tick == serverTick
            && last.paramDigest == paramDigest) {
            return true;
        }
        LAST_ACTION.put(playerId, new DebounceEntry(actionCode, serverTick, paramDigest));
        return false;
    }

    /** 变长 payload 防抖摘要（Arrays.hashCode；null 安全） */
    public static int payloadDigest(byte[] payload) {
        return payload == null ? 0 : Arrays.hashCode(payload);
    }

    /** 当前服务端 tick（overworld 总 tick 基准，GTSWN currentTick 同款） */
    public static long currentServerTick() {
        MinecraftServer server = MinecraftServer.getServer();
        World overworld = server == null ? null : server.worldServerForDimension(0);
        return overworld == null ? 0L : overworld.getTotalWorldTime();
    }

    /**
     * TE 存活复核：按 dim/x/y/z 解析 GT 基 TE（非 GT 方块实体/坐标无 TE → null）。
     * 存活 = 基 TE 非空；调用方（S3-S5 分支）按旧语义追加机器类匹配/canAccessData 等机器级复核。
     */
    public static IGregTechTileEntity getAliveBaseTile(int dim, int x, int y, int z) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) {
            return null;
        }
        World world = server.worldServerForDimension(dim);
        if (world == null) {
            return null;
        }
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof IGregTechTileEntity)) {
            return null;
        }
        return (IGregTechTileEntity) te;
    }

    /**
     * 64 格距离复核（玩家与终端坐标平方距离 ≤ 64²，与旧 canInteractWith 等价）。
     * 调用方先保证 player.dimension == 包 dim（跨维距离无意义，直接拒绝）。
     */
    public static boolean withinInteractionRange(EntityPlayerMP player, int x, int y, int z) {
        if (player == null) {
            return false;
        }
        double maxDistSq = MAX_INTERACTION_DISTANCE * MAX_INTERACTION_DISTANCE;
        double dx = player.posX - (x + 0.5D);
        double dy = player.posY - (y + 0.5D);
        double dz = player.posZ - (z + 0.5D);
        return dx * dx + dy * dy + dz * dz <= maxDistSq;
    }

    /** 防抖表条目（不可变） */
    private static final class DebounceEntry {

        final int actionCode;
        final long tick;
        final int paramDigest;

        DebounceEntry(int actionCode, long tick, int paramDigest) {
            this.actionCode = actionCode;
            this.tick = tick;
            this.paramDigest = paramDigest;
        }
    }
}
