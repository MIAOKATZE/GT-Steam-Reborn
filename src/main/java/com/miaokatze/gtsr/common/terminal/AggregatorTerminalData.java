package com.miaokatze.gtsr.common.terminal;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.api.gui.OreEntryInfo;
import com.miaokatze.gtsr.common.machine.MTECrustMatterAggregator;
import com.miaokatze.gtsr.main.GTSteamReborn;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.util.GTUtility;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * 聚合器终端数据（terminal-native-ui N32，PLAN §4.3-C）。
 * <p>
 * 快照组装：8 标量 supplier 移植旧 MUI2 聚合器配置 GUI（git 基线 b4fabb2：
 * {@code common/gui/} 下同名配置界面源码 :185-195）的 syncValue lambda 语义
 * （oreMode=mOreMode、fortune=mFortuneLevel、steamMult=getSteamMultiplier、
 * denseState=getActiveDense、directionalMode=getDirectionalMode、uuMult=getUUMultiplier、
 * weightIncrease=getWeightIncreasePercent、dimIncrease=getDimensionIncreasePercent）；
 * oreList 数据源 = {@code aggregator.getOreEntries()}（旧 GenericListSyncHandler supplier 同源）。
 * <p>
 * 协议（PLAN §4.3-C，字段序冻结）：8 标量（oreMode varint / fortune varint /
 * steamMult double 原值 / denseState bool / directionalMode bool / uuMult double /
 * weightIncrease double / dimIncrease double）+ oreList {@code [count varint] × 条目}；
 * 条目字段序 = 旧 OreEntryInfo read/write（旧源 :754-760）严格一致序
 * （矿石 → weight → 维度数量 → 各维度缩写 UTF8 → filtered → aimed），
 * 其中矿石由整 ItemStack 换为 uniqueId UTF + meta varint（新轨线上表示，
 * 与动作包 payload 同形；显示名/图标由客户端按 uniqueId 解析）。
 * <p>
 * 动作执行：actionCode 语义 = 旧 AggregatorActionSyncHandler 码冻结（1 CYCLE_ORE_MODE /
 * 2 CYCLE_FORTUNE / 3 TOGGLE_FILTER / 4 REFRESH_POOL / 5 TOGGLE_DIRECTIONAL /
 * 6 TOGGLE_DIRECTIONAL_ORE / 7 CLEAR_CONFIG），服务端最终调用与旧 readOnServer 逐行同参
 * （PLAN §7.3 表；player 从包会话传入）：
 * <ul>
 * <li>1/2/4/7 无参 → cycleOreMode / cycleFortuneLevel / forceRefreshPool /
 * clearCurrentModeConfig（同名机器方法）；</li>
 * <li>3/6 携带 uniqueId UTF + meta（旧编码格式 :806-812 逐字保留：writeUTF8String +
 * writeInt）→ 服务端解析后 setOreFiltered/setOreAimed 取反切换；</li>
 * <li>5 无参 → 取机器附近 16 格最近玩家做 chat 反馈后 toggleDirectionalMode(player)
 * （机器方法未对玩家判空，旧轨仅在找到玩家时调用，同款守卫）。</li>
 * </ul>
 * 复核：距离 + TE 存活由 TerminalNet.handleAction 统一复核后才分发本类；
 * uniqueId 解析失败（空名/无冒号/未注册）一律静默返回（与旧 readOnServer 同款防御）。
 * oreList 上限 {@value #MAX_ORE_ENTRIES} 条，超限截断 + 服务端 warn（PLAN §4.2）。
 */
public final class AggregatorTerminalData {

    /** oreList 条目上限（PLAN §4.2：oreList 512，超限截断 + 服务端 warn） */
    public static final int MAX_ORE_ENTRIES = 512;

    // actionCode（旧 AggregatorActionSyncHandler 码冻结，禁止裸 int 新增语义）
    /** 循环矿石模式（无 payload） */
    public static final int ACTION_CYCLE_ORE_MODE = 1;
    /** 循环时运档位（无 payload） */
    public static final int ACTION_CYCLE_FORTUNE = 2;
    /** 切换单矿过滤（payload：uniqueId UTF + meta int，旧编码格式） */
    public static final int ACTION_TOGGLE_FILTER = 3;
    /** 手动刷新矿池（无 payload） */
    public static final int ACTION_REFRESH_POOL = 4;
    /** 切换定向模式（无 payload；chat 反馈用机器附近玩家） */
    public static final int ACTION_TOGGLE_DIRECTIONAL = 5;
    /** 切换单矿定向（payload：uniqueId UTF + meta int，旧编码格式） */
    public static final int ACTION_TOGGLE_DIRECTIONAL_ORE = 6;
    /** 清除当前模式配置表（无 payload） */
    public static final int ACTION_CLEAR_CONFIG = 7;

    private AggregatorTerminalData() {}

    // ==================== 服务端快照组装 ====================

    /**
     * 组装聚合器快照 payload：8 标量 + {@code [count varint] × OreEntry 编码}。
     * 标量 supplier = 旧 syncValue lambda 同名机器 getter（见类注释）。
     */
    public static byte[] assembleSnapshot(MTECrustMatterAggregator aggregator) {
        List<OreEntryInfo> entries = aggregator.getOreEntries();
        final int total = entries.size();
        final int count = Math.min(total, MAX_ORE_ENTRIES);
        if (total > MAX_ORE_ENTRIES) {
            GTSteamReborn.LOG.warn(
                "[TerminalNet] 聚合器矿石列表 {} 条超出单包上限 {}，已截断",
                Integer.valueOf(total),
                Integer.valueOf(MAX_ORE_ENTRIES));
        }
        ByteBuf buf = Unpooled.buffer();
        PacketBuffer pb = new PacketBuffer(buf);
        pb.writeVarIntToBuffer(aggregator.mOreMode);
        pb.writeVarIntToBuffer(aggregator.mFortuneLevel);
        pb.writeDouble(aggregator.getSteamMultiplier());
        pb.writeBoolean(aggregator.getActiveDense());
        pb.writeBoolean(aggregator.getDirectionalMode());
        pb.writeDouble(aggregator.getUUMultiplier());
        pb.writeDouble(aggregator.getWeightIncreasePercent());
        pb.writeDouble(aggregator.getDimensionIncreasePercent());
        pb.writeVarIntToBuffer(count);
        for (int i = 0; i < count; i++) {
            writeOreEntry(pb, entries.get(i));
        }
        byte[] payload = new byte[buf.readableBytes()];
        buf.readBytes(payload);
        return payload;
    }

    // ==================== 服务端动作执行分发 ====================

    /**
     * 动作分发（TerminalNet.handleAction 已完成距离 + TE 存活复核，主线程调用）。
     * 动作 3/6 的 payload 读序与旧 sendToggleFilter/sendToggleDirectionalOre 写序严格一致
     * （先 UTF8 注册名后 int meta）；解析失败静默返回。
     */
    public static void executeAction(MTECrustMatterAggregator aggregator, EntityPlayer player, int actionCode,
        byte[] payload) {
        ByteBuf buf = Unpooled.wrappedBuffer(payload == null ? new byte[0] : payload);
        switch (actionCode) {
            case ACTION_CYCLE_ORE_MODE:
                aggregator.cycleOreMode();
                break;
            case ACTION_CYCLE_FORTUNE:
                aggregator.cycleFortuneLevel();
                break;
            case ACTION_TOGGLE_FILTER: {
                GTUtility.ItemId oreId = readOreId(buf);
                if (oreId == null) return; // 解析失败静默（旧 readOnServer 同款防御）
                aggregator.setOreFiltered(oreId, !aggregator.isOreFiltered(oreId));
                break;
            }
            case ACTION_REFRESH_POOL:
                aggregator.forceRefreshPool();
                break;
            case ACTION_TOGGLE_DIRECTIONAL: {
                // 服务端取机器附近玩家用于 chat 反馈；机器 toggleDirectionalMode 未对玩家判空，
                // 故仅在找到玩家时调用（玩家右击机器打开本界面，正常必在 16 格内）
                if (aggregator.getBaseMetaTileEntity() == null) return;
                World world = aggregator.getBaseMetaTileEntity()
                    .getWorld();
                EntityPlayer nearest = world.getClosestPlayer(
                    aggregator.getBaseMetaTileEntity()
                        .getXCoord(),
                    aggregator.getBaseMetaTileEntity()
                        .getYCoord(),
                    aggregator.getBaseMetaTileEntity()
                        .getZCoord(),
                    16.0d);
                if (nearest != null) aggregator.toggleDirectionalMode(nearest);
                break;
            }
            case ACTION_TOGGLE_DIRECTIONAL_ORE: {
                GTUtility.ItemId oreId = readOreId(buf);
                if (oreId == null) return;
                aggregator.setOreAimed(oreId, !aggregator.isOreAimed(oreId));
                break;
            }
            case ACTION_CLEAR_CONFIG:
                aggregator.clearCurrentModeConfig();
                break;
            default:
                return; // 未知动作码：静默拒绝
        }
    }

    /**
     * 动作 payload 解析（uniqueId UTF + meta int，旧 :806-812 逐字同序同型）：
     * 空名 / 无 ":" 分段 / 未注册物品 → null（调用方静默返回）。
     */
    private static GTUtility.ItemId readOreId(ByteBuf buf) {
        if (buf.readableBytes() < 2) return null; // UTF8 长度前缀都读不全：异常长度即断
        String name = ByteBufUtils.readUTF8String(buf);
        if (buf.readableBytes() < 4) return null;
        int meta = buf.readInt();
        if (name == null || name.isEmpty()) return null;
        String[] parts = name.split(":", 2);
        if (parts.length != 2) return null;
        Item item = GameRegistry.findItem(parts[0], parts[1]);
        if (item == null) return null;
        return GTUtility.ItemId.createNoCopy(item, meta, null);
    }

    // ==================== 编码函数（旧 OreEntryInfo read/write 移植 + uniqueId 换形） ====================

    /**
     * 单条矿石条目写入。字段序与旧 OreEntryInfo.write（旧源 :754-760）严格一致：
     * 矿石（新轨=uniqueId UTF + meta varint）→ weight(float) → 维度数量(varint) →
     * 各维度缩写(UTF8) → filtered → aimed。
     */
    private static void writeOreEntry(PacketBuffer pb, OreEntryInfo info) {
        String uid = "";
        int meta = 0;
        if (info.ore != null && info.ore.getItem() != null) {
            GameRegistry.UniqueIdentifier identifier = GameRegistry.findUniqueIdentifierFor(info.ore.getItem());
            if (identifier != null) uid = identifier.modId + ":" + identifier.name;
            meta = info.ore.getItemDamage();
        }
        ByteBufUtils.writeUTF8String(pb, uid);
        pb.writeVarIntToBuffer(meta);
        pb.writeFloat(info.weight);
        pb.writeVarIntToBuffer(info.dimAbbrs.size());
        for (String abbr : info.dimAbbrs) {
            ByteBufUtils.writeUTF8String(pb, abbr);
        }
        pb.writeBoolean(info.filtered);
        pb.writeBoolean(info.aimed);
    }

    /** 单条矿石条目读取（与 {@link #writeOreEntry} 严格对称；displayStack 解析失败为 null） */
    private static OreEntry readOreEntry(PacketBuffer pb) {
        String uid = ByteBufUtils.readUTF8String(pb);
        int meta = pb.readVarIntFromBuffer();
        float weight = pb.readFloat();
        int dimCount = pb.readVarIntFromBuffer();
        List<String> dimAbbrs = new ArrayList<String>(dimCount);
        for (int i = 0; i < dimCount; i++) {
            dimAbbrs.add(ByteBufUtils.readUTF8String(pb));
        }
        boolean filtered = pb.readBoolean();
        boolean aimed = pb.readBoolean();
        return new OreEntry(uid, meta, weight, dimAbbrs, filtered, aimed, resolveDisplayStack(uid, meta));
    }

    /** uniqueId → 显示用 ItemStack（common 侧 GameRegistry 解析；失败返回 null，客户端回退文本显示） */
    private static ItemStack resolveDisplayStack(String uid, int meta) {
        if (uid == null || uid.isEmpty()) return null;
        String[] parts = uid.split(":", 2);
        if (parts.length != 2) return null;
        Item item = GameRegistry.findItem(parts[0], parts[1]);
        if (item == null) return null;
        return new ItemStack(item, 1, meta);
    }

    /**
     * 解码完整快照 payload（客户端缓存用）：8 标量 + oreList；
     * 截断/越界返回 null（调用方丢弃整包，防撕裂）。
     */
    public static Snapshot readSnapshot(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return null;
        }
        try {
            ByteBuf buf = Unpooled.wrappedBuffer(payload);
            PacketBuffer pb = new PacketBuffer(buf);
            int oreMode = pb.readVarIntFromBuffer();
            int fortune = pb.readVarIntFromBuffer();
            double steamMult = pb.readDouble();
            boolean denseState = pb.readBoolean();
            boolean directionalMode = pb.readBoolean();
            double uuMult = pb.readDouble();
            double weightIncrease = pb.readDouble();
            double dimIncrease = pb.readDouble();
            int count = pb.readVarIntFromBuffer();
            if (count < 0 || count > MAX_ORE_ENTRIES) {
                return null;
            }
            List<OreEntry> ores = new ArrayList<OreEntry>(count);
            for (int i = 0; i < count; i++) {
                ores.add(readOreEntry(pb));
            }
            if (buf.isReadable()) {
                return null; // 尾部多余字节：整包退化丢弃
            }
            return new Snapshot(
                oreMode,
                fortune,
                steamMult,
                denseState,
                directionalMode,
                uuMult,
                weightIncrease,
                dimIncrease,
                ores);
        } catch (RuntimeException e) {
            return null; // 越界/截断：整包退化丢弃
        }
    }

    /** 快照不可变载体（8 标量 + 矿石列表；字段语义与旧 syncValue 一一对应） */
    public static final class Snapshot {

        /** 矿石模式（0 原矿 / 1 粗矿 / 2 粉碎矿；= 旧 mOreMode） */
        public final int oreMode;
        /** 时运档位值（3-15 奇数；= 旧 mFortuneLevel） */
        public final int fortune;
        /** 蒸汽消耗总倍率（= 旧 getSteamMultiplier，double 原值） */
        public final double steamMult;
        /** 当前档位是否致密流体（驱动蒸汽基准 240/24000 L/s） */
        public final boolean denseState;
        /** 定向模式开关 */
        public final boolean directionalMode;
        /** UU 倍率（定向关闭时为 0；= 旧 getUUMultiplier） */
        public final double uuMult;
        /** 权重消耗增加%（浏览器标题右侧 +X%；= 旧 getWeightIncreasePercent） */
        public final double weightIncrease;
        /** 维度消耗增加%（刷新按钮右侧 +X%；= 旧 getDimensionIncreasePercent） */
        public final double dimIncrease;
        /** 矿石列表（≤{@value #MAX_ORE_ENTRIES} 条；服务端组装序） */
        public final List<OreEntry> ores;

        Snapshot(int oreMode, int fortune, double steamMult, boolean denseState, boolean directionalMode, double uuMult,
            double weightIncrease, double dimIncrease, List<OreEntry> ores) {
            this.oreMode = oreMode;
            this.fortune = fortune;
            this.steamMult = steamMult;
            this.denseState = denseState;
            this.directionalMode = directionalMode;
            this.uuMult = uuMult;
            this.weightIncrease = weightIncrease;
            this.dimIncrease = dimIncrease;
            this.ores = ores;
        }
    }

    /**
     * 矿石浏览器单条条目（字段 = 旧 OreEntryInfo + uniqueId 换形）：
     * uniqueId/meta（动作回传键）、weight、dimAbbrs、filtered、aimed、displayStack（客户端解析）。
     */
    public static final class OreEntry {

        /** 矿石注册名（"modid:name"；解析失败为空串） */
        public final String uniqueId;
        /** 矿石 meta */
        public final int meta;
        /** 跨维权重和 */
        public final float weight;
        /** 出现维度缩写（如 ["Ow","Ne"]） */
        public final List<String> dimAbbrs;
        /** 是否被过滤 */
        public final boolean filtered;
        /** 是否被定向瞄准 */
        public final boolean aimed;
        /** 显示用 ItemStack（uniqueId 客户端解析产物；失败为 null，行渲染回退 uniqueId 文本） */
        public final ItemStack displayStack;

        OreEntry(String uniqueId, int meta, float weight, List<String> dimAbbrs, boolean filtered, boolean aimed,
            ItemStack displayStack) {
            this.uniqueId = uniqueId;
            this.meta = meta;
            this.weight = weight;
            this.dimAbbrs = dimAbbrs;
            this.filtered = filtered;
            this.aimed = aimed;
            this.displayStack = displayStack;
        }

        /** 动作键命中（uniqueId + meta 与本条相同） */
        public boolean matches(String uid, int itemMeta) {
            return this.meta == itemMeta && this.uniqueId != null && this.uniqueId.equals(uid);
        }
    }
}
