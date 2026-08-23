package com.miaokatze.gtsr.common.gui.cluster;

import java.io.IOException;

import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.api.value.ISyncOrValue;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.DoubleSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandler;
import com.cleanroommc.modularui.widget.Widget;
import com.miaokatze.gtsr.common.machine.cluster.ChainLink;
import com.miaokatze.gtsr.common.machine.cluster.LogisticsChain;
import com.miaokatze.gtsr.common.machine.cluster.MTEBasicLogisticsUnit;
import com.miaokatze.gtsr.common.machine.cluster.MTESteamMineralLogisticsCluster;

/**
 * 集群 GUI 同步通道：S2C 标量 + C2S 动作（蒸汽矿物物流集群终端 GUI 专用）。
 *
 * <p>
 * S2C 标量通道（{@link #registerS2C}）：集群运行态全量键值（开关机/预热进度/蒸汽与润滑读数/
 * 吞吐/累计矿数/tier/段数/分页状态），GUI buildUI 时调用一次，全部为纯 S2C 单向 supplier
 * （口径同 MTECrustMatterAggregatorConfigGui.registerSyncValues 的 syncManager.syncValue 用法）。
 *
 * <p>
 * C2S 动作通道（{@link #registerC2S}）：单个面板级 {@link ClusterActionSyncHandler}
 * （口径同 AggregatorActionSyncHandler：构造 allowC2S()，客户端方法经 syncToServer(actionId, buf)
 * 按固定顺序写参，服务端 readOnServer switch 分发，PacketBuffer 读写顺序双端严格一致）。
 * 动作码取 {@link ClusterAction} 的 ordinal（禁止裸 int 常量），故枚举一旦发布禁止重排或中间插入。
 *
 * <p>
 * 注册机制说明：ModularUI2 在 buildUI 期间 panel 尚未挂接 screen（GuiManager 流程为
 * createPanel/buildUI → collectSyncValues → 容器装配 → 客户端 createScreen），且 2.3.79 无
 * panel → PanelSyncManager 反查路径，因此 {@link #registerC2S} 经一个隐形 carrier widget 把
 * handler 挂入面板子树，由框架的 WidgetTree.collectSyncValues 以确定性 auto key 在双端各自
 * buildUI 后完成注册（ButtonWidget 内建 handler 同款机制），仅凭 panel 参数即可在 buildUI 内注册。
 */
public final class ClusterGuiSync {

    private ClusterGuiSync() {}

    // ---- S2C 标量键（冻结契约：键值不得变更，双端 GUI 与服务端共用） ----

    /** 集群开关机状态（BooleanSyncValue，Toggle 按钮初始态）。 */
    public static final String KEY_ENABLED = "cl.enabled";
    /** 预热进度 0-100（DoubleSyncValue，驱动进度词条）。 */
    public static final String KEY_PREHEAT = "cl.preheat";
    /** 最近一秒蒸汽消耗 Lps（LongSyncValue，蒸汽经济读数）。 */
    public static final String KEY_STEAM_LPS = "cl.steamLps";
    /** 蒸汽是否短缺（BooleanSyncValue，红标判定）。 */
    public static final String KEY_STEAM_SHORT = "cl.steamShort";
    /** 润滑液是否充足（BooleanSyncValue，红标判定）。 */
    public static final String KEY_LUBE_OK = "cl.lubeOk";
    /** 最近一秒润滑液消耗 Lps（LongSyncValue，润滑读数）。 */
    public static final String KEY_LUBE_LPS = "cl.lubeLps";
    /** 最近一秒处理矿数（DoubleSyncValue，吞吐词条）。 */
    public static final String KEY_THROUGHPUT = "cl.thr";
    /** 集群累计处理矿数（LongSyncValue，NBT 持久记账）。 */
    public static final String KEY_TOTAL_ORE = "cl.totalOre";
    /** 结构 tier 下标 0-3，未成型/混拼 -1（IntSyncValue）。 */
    public static final String KEY_TIER = "cl.tier";
    /** 延伸段数（主段外成功成型的段数，IntSyncValue）。 */
    public static final String KEY_SEGMENTS = "cl.segments";
    /** 初始页判定：1 = 初始化引导页（结构未成型或无物流单元），0 = 正常页（IntSyncValue）。 */
    public static final String KEY_INIT_PAGE = "cl.initPage";
    /** 当前选中物流单元下标（IntSyncValue，链编辑器目标）。 */
    public static final String KEY_SEL_LOGI = "cl.selLogi";
    /** 公式区展开初始态（BooleanSyncValue，仅送初始 false；此后折叠为客户端本地状态，不落服务器）。 */
    public static final String KEY_FORMULA_OPEN = "cl.fopen";

    /**
     * 动作码清单（双端唯一，线上码 = {@link #ordinal()}，禁止裸 int）。
     * 枚举顺序即线上协议：只允许尾部追加，禁止重排/中间插入/删除（双端版本不一致会错位分发）。
     */
    public enum ClusterAction {
        /** 开关机切换（无参）。 */
        TOGGLE_POWER,
        /** 选中物流单元（buf=[idx]）。 */
        SELECT_LOGISTICS,
        /** 链尾追加链步（buf=[ordinal]）。 */
        APPEND_LINK,
        /** 按索引删除链步（buf=[index]）。 */
        REMOVE_LINK,
        /** 链步位移（buf=[index,dir]，dir=-1 左移 / +1 右移）。 */
        MOVE_LINK,
        /** 清空当前链（无参）。 */
        CLEAR_CHAIN,
        /** 载入预设链（buf=[idx]，0..5）。 */
        APPLY_PRESET,
        /** 公式区折叠切换（无参；无服务端副作用，仅客户端本地翻页）。 */
        TOGGLE_FORMULA
    }

    /**
     * S2C 标量全量注册（GUI buildUI 时调用一次）。全部为纯 S2C 单向 supplier，
     * 服务端每 tick 检测变化推送，客户端经 findSyncHandler(KEY_*, XxxSyncValue.class) 取缓存读数。
     */
    public static void registerS2C(PanelSyncManager mgr, MTESteamMineralLogisticsCluster cluster) {
        mgr.syncValue(KEY_ENABLED, new BooleanSyncValue(cluster::isMachineEnabled));
        mgr.syncValue(KEY_PREHEAT, new DoubleSyncValue(cluster::getPreheatProgress));
        mgr.syncValue(
            KEY_STEAM_LPS,
            new LongSyncValue(
                () -> cluster.getSteamEconomy()
                    .getLastSteamLps()));
        mgr.syncValue(
            KEY_STEAM_SHORT,
            new BooleanSyncValue(
                () -> cluster.getSteamEconomy()
                    .isSteamShortage()));
        mgr.syncValue(
            KEY_LUBE_OK,
            new BooleanSyncValue(
                () -> cluster.getSteamEconomy()
                    .isLubricantOk()));
        mgr.syncValue(
            KEY_LUBE_LPS,
            new LongSyncValue(
                () -> cluster.getSteamEconomy()
                    .getLastLubricantLps()));
        mgr.syncValue(KEY_THROUGHPUT, new DoubleSyncValue(cluster::getLastThroughputOrePerSec));
        mgr.syncValue(KEY_TOTAL_ORE, new LongSyncValue(cluster::getTotalProcessedOre));
        mgr.syncValue(KEY_TIER, new IntSyncValue(cluster::getStructureTierIndex));
        mgr.syncValue(KEY_SEGMENTS, new IntSyncValue(cluster::getExtensionCount));
        mgr.syncValue(KEY_INIT_PAGE, new IntSyncValue(() -> {
            // 初始页判定：结构未成型或无物流单元 → 1（引导页），否则 0（正常页）
            if (cluster.getStructureTierIndex() < 0) return 1;
            return cluster.getTopology()
                .getLogisticsUnits()
                .isEmpty() ? 1 : 0;
        }));
        mgr.syncValue(KEY_SEL_LOGI, new IntSyncValue(cluster::getSelectedLogisticsIndex));
        // 公式区初始折叠（false）；此后折叠翻转为客户端本地状态（见 ClusterAction.TOGGLE_FORMULA）
        mgr.syncValue(KEY_FORMULA_OPEN, new BooleanSyncValue(() -> false));
    }

    /**
     * C2S 动作通道注册（GUI buildUI 时调用一次），返回 handler 供视图按钮调用。
     * handler 经 {@link ClusterActionCarrierWidget} 挂入 panel 子树，由框架 collectSyncValues
     * 双端确定性注册（见类注释「注册机制说明」）。
     */
    public static ClusterActionSyncHandler registerC2S(ModularPanel panel, MTESteamMineralLogisticsCluster cluster) {
        ClusterActionSyncHandler handler = new ClusterActionSyncHandler(cluster);
        panel.child(new ClusterActionCarrierWidget(handler));
        return handler;
    }

    /**
     * 集群 C2S 动作处理器：所有终端按钮共用（口径同 AggregatorActionSyncHandler）。
     * 客户端方法 → syncToServer(actionId, buf 按固定顺序写参)；服务端 readOnServer 按同一顺序读参
     * 并分发到集群写路径（含越界/空判防御）。执行后无需额外刷新：S2C 标量通道自动推送新读数。
     */
    public static final class ClusterActionSyncHandler extends SyncHandler<ClusterActionSyncHandler> {

        private final MTESteamMineralLogisticsCluster cluster;

        private ClusterActionSyncHandler(MTESteamMineralLogisticsCluster cluster) {
            this.cluster = cluster;
            allowC2S();
        }

        // ===== 客户端调用：发送动作到服务端（buf 写参顺序见各方法） =====

        /** 开关机切换（无参）。服务端：setMachineEnabled(!isMachineEnabled())。 */
        public void togglePower() {
            syncToServer(ClusterAction.TOGGLE_POWER.ordinal(), buf -> {});
        }

        /** 选中物流单元（buf=[idx]）。服务端钳到 [0, max(0, 物流单元数-1)]。 */
        public void selectLogistics(int idx) {
            syncToServer(ClusterAction.SELECT_LOGISTICS.ordinal(), buf -> buf.writeInt(idx));
        }

        /** 链尾追加链步（buf=[ordinal]，ChainLink.ordinal()）。服务端做枚举越界与简易洗可用性防御。 */
        public void appendLink(int linkOrdinal) {
            syncToServer(ClusterAction.APPEND_LINK.ordinal(), buf -> buf.writeInt(linkOrdinal));
        }

        /** 按索引删除链步（buf=[index]）。服务端经 removeAt 越界安全删除。 */
        public void removeLink(int index) {
            syncToServer(ClusterAction.REMOVE_LINK.ordinal(), buf -> buf.writeInt(index));
        }

        /** 链步位移（buf=[index,dir]，dir=-1 左移 / +1 右移）。服务端经 move 越界安全交换。 */
        public void moveLink(int index, int dir) {
            syncToServer(ClusterAction.MOVE_LINK.ordinal(), buf -> {
                buf.writeInt(index);
                buf.writeInt(dir);
            });
        }

        /** 清空当前链（无参）。 */
        public void clearChain() {
            syncToServer(ClusterAction.CLEAR_CHAIN.ordinal(), buf -> {});
        }

        /** 载入预设链（buf=[idx]，0..5）。服务端钳验后 setLinks(getPresetLinks(idx))。 */
        public void applyPreset(int presetIdx) {
            syncToServer(ClusterAction.APPLY_PRESET.ordinal(), buf -> buf.writeInt(presetIdx));
        }

        /**
         * 公式区折叠切换（无参）：仅客户端本地翻页（视图自持本地状态），不落服务器；
         * 为动作清单一致性仍发线上动作，服务端保留空分支（无副作用）。
         */
        public void toggleFormulaFold() {
            syncToServer(ClusterAction.TOGGLE_FORMULA.ordinal(), buf -> {});
        }

        // ===== 服务端执行 =====

        @Override
        public void readOnClient(int id, PacketBuffer buf) throws IOException {}

        @Override
        public void readOnServer(int id, PacketBuffer buf) throws IOException {
            ClusterAction[] actions = ClusterAction.values();
            if (id < 0 || id >= actions.length) return;
            switch (actions[id]) {
                case TOGGLE_POWER -> cluster.setMachineEnabled(!cluster.isMachineEnabled());
                // 读写顺序须严格一致：[idx]（见 selectLogistics）
                case SELECT_LOGISTICS -> {
                    int idx = buf.readInt();
                    int max = Math.max(
                        0,
                        cluster.getTopology()
                            .getLogisticsUnits()
                            .size() - 1);
                    cluster.setSelectedLogisticsIndex(Math.max(0, Math.min(idx, max)));
                }
                // 读写顺序须严格一致：[ordinal]（见 appendLink）
                case APPEND_LINK -> {
                    int linkOrdinal = buf.readInt();
                    MTEBasicLogisticsUnit unit = cluster.getSelectedLogisticsUnit();
                    if (unit == null || unit.getChain() == null) return;
                    ChainLink[] values = ChainLink.values();
                    if (linkOrdinal < 0 || linkOrdinal >= values.length) return;
                    ChainLink link = values[linkOrdinal];
                    // GT++ 简易洗配方图缺失时忽略（与编辑器灰化口径一致）
                    if (link == ChainLink.SIMPLE_WASH && !ChainLink.isSimpleWashAvailable()) return;
                    unit.getChain()
                        .append(link);
                }
                // 读写顺序须严格一致：[index]（见 removeLink；removeAt 自带越界安全）
                case REMOVE_LINK -> {
                    int index = buf.readInt();
                    MTEBasicLogisticsUnit unit = cluster.getSelectedLogisticsUnit();
                    if (unit == null || unit.getChain() == null) return;
                    unit.getChain()
                        .removeAt(index);
                }
                // 读写顺序须严格一致：[index,dir]（见 moveLink；move 自带越界安全）
                case MOVE_LINK -> {
                    int index = buf.readInt();
                    int dir = buf.readInt();
                    MTEBasicLogisticsUnit unit = cluster.getSelectedLogisticsUnit();
                    if (unit == null || unit.getChain() == null) return;
                    unit.getChain()
                        .move(index, dir);
                }
                case CLEAR_CHAIN -> {
                    MTEBasicLogisticsUnit unit = cluster.getSelectedLogisticsUnit();
                    if (unit == null || unit.getChain() == null) return;
                    unit.getChain()
                        .clear();
                }
                // 读写顺序须严格一致：[idx]（见 applyPreset；下标钳验后再取预设，防 IllegalArgumentException）
                case APPLY_PRESET -> {
                    int presetIdx = buf.readInt();
                    MTEBasicLogisticsUnit unit = cluster.getSelectedLogisticsUnit();
                    if (unit == null || unit.getChain() == null) return;
                    if (presetIdx < 0 || presetIdx >= LogisticsChain.PRESET_COUNT) return;
                    unit.getChain()
                        .setLinks(LogisticsChain.getPresetLinks(presetIdx));
                }
                // 空分支：公式折叠为客户端本地状态，无服务端副作用（见 toggleFormulaFold）
                case TOGGLE_FORMULA -> {}
            }
        }
    }

    /**
     * 隐形 carrier widget（0 尺寸、不渲染）：把 {@link ClusterActionSyncHandler} 挂入面板子树，
     * 由 WidgetTree.collectSyncValues 在双端 buildUI 后各以确定性 auto key 注册进 PanelSyncManager
     * （对「isSynced 且未注册」的 widget handler 自动注册，ButtonWidget 内建 handler 同款机制）。
     * 本类不承载任何绘制或交互。
     */
    private static final class ClusterActionCarrierWidget extends Widget<ClusterActionCarrierWidget> {

        private ClusterActionCarrierWidget(ClusterActionSyncHandler handler) {
            setSyncOrValue(handler);
            invisible();
        }

        /** 仅接受集群动作 handler（覆盖 ISynced 默认「拒绝一切 handler」，使 setSyncOrValue 校验通过）。 */
        @Override
        public boolean isValidSyncOrValue(ISyncOrValue value) {
            return value == null || value instanceof ClusterActionSyncHandler;
        }
    }
}
