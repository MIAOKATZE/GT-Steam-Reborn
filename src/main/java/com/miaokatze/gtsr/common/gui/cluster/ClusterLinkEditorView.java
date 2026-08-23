package com.miaokatze.gtsr.common.gui.cluster;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.IntValue;
import com.cleanroommc.modularui.value.sync.DoubleSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.value.sync.SyncHandler;
import com.cleanroommc.modularui.value.sync.ValueSyncHandler;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.menu.DropdownWidget;
import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;
import com.miaokatze.gtsr.common.gui.cluster.ClusterGuiSync.ClusterActionSyncHandler;
import com.miaokatze.gtsr.common.machine.cluster.BoosterState;
import com.miaokatze.gtsr.common.machine.cluster.ChainLink;
import com.miaokatze.gtsr.common.machine.cluster.ClusterChainFSM;
import com.miaokatze.gtsr.common.machine.cluster.ClusterChainFSM.Form;
import com.miaokatze.gtsr.common.machine.cluster.ClusterParams;
import com.miaokatze.gtsr.common.machine.cluster.ExecutionPlan;
import com.miaokatze.gtsr.common.machine.cluster.LogisticsChain;
import com.miaokatze.gtsr.common.machine.cluster.MTEBasicLogisticsUnit;
import com.miaokatze.gtsr.common.machine.cluster.MTESteamMineralLogisticsCluster;

/**
 * 集群三视图页 2「链路编辑器」（MUI2，对齐 HTML 规格 v2.5 §链路编辑器，游戏内紧凑适配 784×396 内容区）。
 *
 * <p>
 * 由主 GUI（{@code MTESteamMineralLogisticsClusterGui}）按并行契约调用 {@link #build}：
 * 本视图建立链路编辑页并经 {@code paged.addPage(...)} 挂为页序 1；全部子元素使用页面本地坐标。
 *
 * <p>
 * 布局（对齐 HTML v2.5，内容区上下两带 + 左右两列，790×396 内不溢出）：
 * <ul>
 * <li>顶行（y+0..14）：物流单元下拉（{@link DropdownWidget}，服务端每物流单元一项
 * 「物流单元 @段N」，无物流单元时占位「（未放置物流模块）」）+ 当前链状态 banner
 * （{@code IKey.dynamic}：链空→灰提示；有效→绿「✔ 有效链——最终产物『X』」；无效→红 getInvalidReasonKey）。</li>
 * <li>左列：标题「可用链步」+ 10 行链步按钮（两行 overlay：链步名 + 绿字「在链 ×N」/
 * 灰字基础秒与附加信息——洗矿「每批需水 1000L」、化洗「每批需化浴液 1000L」、磁选/热离「需通电」；
 * 不可用行保持灰化绘制并显示锁定原因 tooltip，可用行点击 {@code appendLink}）；
 * 其下预设区：灰字说明 + 6 个预设按钮（2 行 × 3，PresetNameKey 本地名自带秒数，
 * 点击 {@code applyPreset}，无物流单元时全部灰化）。</li>
 * <li>右列：标题「当前有序链」+「清空」按钮 → 当前链 chips 滚动列表（常驻 {@link ListWidget}，
 * 数据变化仅重建行内容不回顶：序号/链步名/该步耗时（base×TIER_TIME_FACTOR[tier]÷max(1,同类模块数)，
 * 速度增幅省略标注近似）/◀（moveLink(i,-1)）▶（+1）✖（removeLink(i)），空链占位提示）→
 * 产物状态机推演条（{@code IKey.dynamic} 串联 原矿 →(链步)→ 形态 →…→ 终态，
 * 终态绿 +「✓终」、末位非终态红，{@link ClusterChainFSM} 客户端纯函数推演）→
 * 公式折叠区（折叠头「公式演算」本地 boolean 翻转、不走路由；展开 4 行
 * {@code IKey.dynamic}：单物品耗时 / 有效并行 / 本链蒸汽 / 集群总蒸汽）。</li>
 * </ul>
 *
 * <p>
 * 同步设计：
 * <ul>
 * <li>选中物流单元：复用主壳 {@link ClusterGuiSync#registerS2C} 已注册的 {@code KEY_SEL_LOGI}
 * {@link IntSyncValue}（findSyncHandlerNullable 取回；取不到时兜底自注册同键）；下拉显示值经
 * {@link IntValue.Dynamic} 读该同步值缓存，选择动作走 {@code actions.selectLogistics(idx)} C2S。</li>
 * <li>「cl.le.state」：{@link StringSyncValue}（S2C），服务端 getter 编码编辑器全状态串
 * {@code lockCsv|countCsv|chainCsv|segCsv}（逐链步锁定原因种类 / 逐链步同类工作模块数 /
 * 当前选中单元链 ordinal 序列 / 物流单元段号序列，照 MTEGTSRRedstoneHatchGui 词条编码范式）；
 * 客户端 setter 解码进本地快照，changeListener 重建下拉选项与 chips 行（常驻列表实例不回顶，
 * 照 MTECrustMatterAggregatorConfigGui 范式）。结构 tier 经 KEY_TIER 同步值直读。</li>
 * <li>「cl.f.time|cl.f.par|cl.f.steam|cl.f.total」：4 个 {@link DoubleSyncValue}（S2C，键名前缀 cl.f. 固定），
 * 服务端 supplier 现算 {@link ExecutionPlan}（单物品耗时/有效并行/本链蒸汽/集群总蒸汽，
 * 入参 ExecutionPlan + BoosterState.aggregate + 选中单元链）。</li>
 * </ul>
 *
 * <p>
 * 文案说明：链步/预设/形态/锁定/banner 等走既有 lang key（gtsr.gui.cluster.*，lang 并行切片已备）；
 * 本视图局部文案（列标题/预设说明）按任务规格原文硬编码中文（照 ClusterBoosterView 同款口径，
 * 后续如需国际化可平移为 lang key）；「@段N」后缀照 ClusterBoosterView 增幅行同款硬编码。
 */
public final class ClusterLinkEditorView {

    /** 本视图在 PagedWidget 的页序（0=拓扑总览 / 1=链路编辑 / 2=增幅面板，与主壳页签轨一致）。 */
    private static final int PAGE_INDEX = 1;
    /** 编辑器全状态串同步键（S2C StringSyncValue，cl.le = link editor）。 */
    private static final String KEY_EDITOR_STATE = "cl.le.state";
    /** 公式四值同步键（S2C DoubleSyncValue，前缀 cl.f. 固定）。 */
    private static final String KEY_F_TIME = "cl.f.time";
    private static final String KEY_F_PAR = "cl.f.par";
    private static final String KEY_F_STEAM = "cl.f.steam";
    private static final String KEY_F_TOTAL = "cl.f.total";
    /** 下拉菜单面板名（面板内唯一即可）。 */
    private static final String DROPDOWN_MENU_NAME = "clLinkEditorMenu";

    // —— 锁定原因种类（状态串第一段，与 LogisticsChain.getLinkLockReasonKey 的四类 key 一一对应） ——
    private static final int LOCK_NONE = 0;
    private static final int LOCK_SIMPLE_WASH = 1;
    private static final int LOCK_MODULE = 2;
    private static final int LOCK_POWER = 3;
    private static final int LOCK_UNFORMED = 4;

    // —— 布局（相对 contentX/contentY 的纵向偏移，右列起点由列宽推导） ——
    /** 顶行高（下拉 + banner）。 */
    private static final int TOP_ROW_H = 13;
    /** 左右列列间距。 */
    private static final int COLUMN_GAP = 8;
    /** 左列起始纵向偏移（顶行之下）。 */
    private static final int COLUMN_DY = 15;
    /** 链步行区起始偏移（列标题之下）。 */
    private static final int LINKS_DY = 27;
    /** 紧凑窗口内完整展示十条链步，预设保持在底部。 */
    private static final int LINK_PITCH = 18;
    private static final int LINK_ROW_H = 17;
    /** 预设区：说明行偏移 / 按钮行距 / 按钮高 / 按钮间距（2 行 × 3 列）。 */
    private static final int PRESET_LABEL_DY = LINKS_DY + ChainLink.values().length * LINK_PITCH + 3;
    private static final int PRESET_BTN_H = 14;
    private static final int PRESET_GAP = 3;
    /** 右列：chips 起始偏移与高度。 */
    private static final int CHIPS_DY = 27;
    private static final int CHIPS_H = 88;
    /** 右列：推演条 / 折叠头 / 公式区起始偏移与高度。 */
    private static final int FLOW_DY = CHIPS_DY + CHIPS_H + 4;
    private static final int FLOW_H = 38;
    private static final int FOLD_DY = FLOW_DY + FLOW_H + 4;
    private static final int FOLD_BTN_H = 13;
    private static final int FORMULA_DY = FOLD_DY + FOLD_BTN_H + 3;

    /** 链步枚举缓存（10 项，ordinal 即状态串各 CSV 段下标）。 */
    private static final ChainLink[] LINKS = ChainLink.values();

    private final PanelSyncManager sync;
    private final ClusterActionSyncHandler actions;
    private final MTESteamMineralLogisticsCluster machine;
    /** 选中物流单元下标同步值（主壳 registerS2C 注册的 KEY_SEL_LOGI，兜底自注册）。 */
    private final IntSyncValue selSync;

    // —— 客户端快照（「cl.le.state」解码结果；服务端侧不渲染，解码不执行亦无碍） ——
    private int[] lockKinds = new int[LINKS.length];
    private int[] unitCounts = new int[LINKS.length];
    private List<Integer> chainOrdinals = new ArrayList<>();
    private List<Integer> logisticsSegments = new ArrayList<>();

    // —— 本地 UI 状态（不落服务器） ——
    /** 公式折叠区展开态（本地 boolean，点击本地翻转，不走路由）。 */
    private boolean formulaOpen = false;

    // —— 常驻部件引用（数据变化仅重建行内容，实例持续） ——
    private DropdownWidget<Integer, ?> unitDropdown;
    private ListWidget<IWidget, ?> chipsList;
    private ListWidget<IWidget, ?> formulaList;
    private DoubleSyncValue fTimeSync;
    private DoubleSyncValue fParallelSync;
    private DoubleSyncValue fSteamSync;
    private DoubleSyncValue fTotalSteamSync;

    private ClusterLinkEditorView(PanelSyncManager sync, ClusterActionSyncHandler actions,
        MTESteamMineralLogisticsCluster machine) {
        this.sync = sync;
        this.actions = actions;
        this.machine = machine;
        this.selSync = selSyncValue(sync, machine);
    }

    /**
     * 构建链路编辑器页（三视图并行契约入口，双端执行）。
     *
     * @param panel    主面板（契约保留参数；本视图内容挂在自己建立的页容器上）
     * @param sync     面板同步管理器（注册 cl.le.state 与 cl.f.* 四值；经 findSyncHandlerNullable 取回
     *                 主壳注册的 KEY_SEL_LOGI / KEY_TIER）
     * @param actions  面板级 C2S 动作处理器（selectLogistics/appendLink/removeLink/moveLink/
     *                 clearChain/applyPreset）
     * @param machine  集群总控（服务端数据源：拓扑 / 选中物流单元 / 结构 tier）
     * @param paged    主 GUI 的三页容器（本视图填充第 {@link #PAGE_INDEX} 页）
     * @param contentX 页内容区左上角 X（面板绝对坐标）
     * @param contentY 页内容区左上角 Y（面板绝对坐标）
     * @param contentW 页内容区宽
     * @param contentH 页内容区高
     */
    public static void build(ModularPanel panel, PanelSyncManager sync, ClusterActionSyncHandler actions,
        MTESteamMineralLogisticsCluster machine, PagedWidget<?> paged, int contentX, int contentY, int contentW,
        int contentH) {
        ClusterLinkEditorView view = new ClusterLinkEditorView(sync, actions, machine);
        view.registerSync();
        ParentWidget<?> page = new ParentWidget<>().size(contentW, contentH);
        paged.addPage(page);
        // 左右列分栏：十个链步留在左列，右列容纳当前链、推演与公式。
        int leftW = Math.max(230, Math.min(270, contentW / 2));
        int rightX = leftW + COLUMN_GAP;
        int rightW = contentW - leftW - COLUMN_GAP;
        view.buildTopRow(page, 0, 0, contentW);
        view.buildLinkColumn(page, 0, 0, leftW);
        view.buildChainColumn(page, rightX, 0, rightW, contentH);
        // 初始一次构建（sync handler 尚未初始化时快照为空态提示，首同步到达后 changeListener 再刷新）
        view.refreshDropdownOptions();
        view.rebuildChips();
        view.rebuildFormula();
    }

    // ==================== 数据同步 ====================

    /** 注册编辑器同步值：cl.le.state 全状态串（S2C）+ cl.f.* 公式四值（S2C）。 */
    private void registerSync() {
        StringSyncValue stateSync = new StringSyncValue(() -> "", this::decodeState, this::encodeState, null);
        stateSync.setChangeListener(() -> {
            refreshDropdownOptions();
            rebuildChips();
        });
        sync.syncValue(KEY_EDITOR_STATE, stateSync);
        fTimeSync = new DoubleSyncValue(
            () -> ExecutionPlan
                .itemTimeSec(selectedLinks(), tierIdxClamped(), machine.getTopology(), currentBooster()));
        fParallelSync = new DoubleSyncValue(() -> ExecutionPlan.effectiveParallel(tierIdxClamped(), currentBooster()));
        fSteamSync = new DoubleSyncValue(() -> ExecutionPlan.chainSteamLps(selectedLinks(), tierIdxClamped()));
        fTotalSteamSync = new DoubleSyncValue(
            () -> ExecutionPlan.totalSteamLps(
                machine.getTopology()
                    .getLogisticsUnits(),
                machine.getTopology(),
                tierIdxClamped(),
                currentBooster()));
        sync.syncValue(KEY_F_TIME, fTimeSync);
        sync.syncValue(KEY_F_PAR, fParallelSync);
        sync.syncValue(KEY_F_STEAM, fSteamSync);
        sync.syncValue(KEY_F_TOTAL, fTotalSteamSync);
    }

    /** 取主壳注册的 KEY_SEL_LOGI 同步值（registerS2C 先于视图构建注册）；取不到时兜底自注册同键。 */
    private static IntSyncValue selSyncValue(PanelSyncManager sync, MTESteamMineralLogisticsCluster machine) {
        SyncHandler<?> handler = sync.findSyncHandlerNullable(ClusterGuiSync.KEY_SEL_LOGI);
        if (handler instanceof IntSyncValue value) return value;
        IntSyncValue created = new IntSyncValue(machine::getSelectedLogisticsIndex);
        sync.syncValue(ClusterGuiSync.KEY_SEL_LOGI, created);
        return created;
    }

    /** 服务端编码编辑器全状态串：lockCsv|countCsv|chainCsv|segCsv（四段 CSV，竖线分隔）。 */
    private String encodeState() {
        StringBuilder sb = new StringBuilder(96);
        for (int i = 0; i < LINKS.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(lockKindOf(LogisticsChain.getLinkLockReasonKey(LINKS[i], machine.getTopology())));
        }
        sb.append('|');
        for (int i = 0; i < LINKS.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(
                machine.getTopology()
                    .countUnits(LINKS[i].getRequiredUnitClass()));
        }
        sb.append('|');
        MTEBasicLogisticsUnit unit = machine.getSelectedLogisticsUnit();
        if (unit != null) {
            int[] ordinals = unit.getChain()
                .toOrdinalArray();
            for (int i = 0; i < ordinals.length; i++) {
                if (i > 0) sb.append(',');
                sb.append(ordinals[i]);
            }
        }
        sb.append('|');
        List<MTEBasicLogisticsUnit> units = machine.getTopology()
            .getLogisticsUnits();
        for (int i = 0; i < units.size(); i++) {
            MTEBasicLogisticsUnit u = units.get(i);
            if (i > 0) sb.append(',');
            sb.append(u != null ? u.getSegmentIndex() : -1);
        }
        return sb.toString();
    }

    /** 客户端解码全状态串进本地快照（畸形段防御性回退空态，不崩客户端）。 */
    private void decodeState(String encoded) {
        this.lockKinds = parseCsv(field(encoded, 0), LINKS.length);
        this.unitCounts = parseCsv(field(encoded, 1), LINKS.length);
        this.chainOrdinals = parseBoundedCsv(field(encoded, 2), LINKS.length);
        this.logisticsSegments = parseBoundedCsv(field(encoded, 3), Integer.MAX_VALUE);
    }

    /** 取状态串第 index 段（越界/空串返回空段）。 */
    private static String field(String encoded, int index) {
        if (encoded == null || encoded.isEmpty()) return "";
        String[] fields = encoded.split("\\|", -1);
        return index < fields.length ? fields[index] : "";
    }

    /** 解析定长 int CSV（畸形项回退 0）。 */
    private static int[] parseCsv(String csv, int length) {
        int[] out = new int[length];
        if (csv == null || csv.isEmpty()) return out;
        String[] parts = csv.split(",", -1);
        int n = Math.min(length, parts.length);
        for (int i = 0; i < n; i++) {
            try {
                out[i] = Integer.parseInt(parts[i].trim());
            } catch (NumberFormatException ignored) {
                out[i] = 0;
            }
        }
        return out;
    }

    /** 解析变长 int CSV 为列表（越界 [0,bound) 的项静默丢弃，防枚举演进脏数据）。 */
    private static List<Integer> parseBoundedCsv(String csv, int bound) {
        List<Integer> out = new ArrayList<>();
        if (csv == null || csv.isEmpty()) return out;
        for (String part : csv.split(",", -1)) {
            try {
                int value = Integer.parseInt(part.trim());
                if (value >= 0 && value < bound) out.add(value);
            } catch (NumberFormatException ignored) {
                // 畸形项丢弃即可
            }
        }
        return out;
    }

    /** getLinkLockReasonKey 返回 key → 状态串锁定种类（null=可用）。 */
    private static int lockKindOf(String reasonKey) {
        if (reasonKey == null) return LOCK_NONE;
        switch (reasonKey) {
            case "gtsr.gui.cluster.link.locked_simple_wash":
                return LOCK_SIMPLE_WASH;
            case "gtsr.gui.cluster.link.locked_module":
                return LOCK_MODULE;
            case "gtsr.gui.cluster.link.locked_unformed":
                return LOCK_UNFORMED;
            default:
                return LOCK_POWER;
        }
    }

    // ==================== 顶行（下拉 + banner） ====================

    private void buildTopRow(ParentWidget<?> page, int contentX, int contentY, int contentW) {
        unitDropdown = new DropdownWidget<>(DROPDOWN_MENU_NAME, Integer.class);
        unitDropdown.pos(contentX, contentY)
            .size(240, TOP_ROW_H)
            .value(new IntValue.Dynamic(this::selectedDisplayIndex, this::onUnitSelected))
            .optionToWidget(
                (idx, forSelectedDisplay) -> IKey.dynamic(() -> formatUnitOption(idx))
                    .asWidget())
            .maxVerticalMenuSize(96);
        page.child(unitDropdown);
        // 链状态 banner：链空→灰提示；有效→绿「✔ 有效链——最终产物『X』」；无效→红 invalid_not_terminal
        page.child(
            IKey.dynamic(this::formatBanner)
                .asWidget()
                .pos(contentX + 252, contentY + 2)
                .scale(0.9f)
                .width(Math.max(120, contentW - 252)));
    }

    /** 下拉显示值：无物流单元显 -1（占位项）；有则读 KEY_SEL_LOGI 缓存并钳到选项范围。 */
    private int selectedDisplayIndex() {
        if (logisticsSegments.isEmpty()) return -1;
        return Math.max(0, Math.min(logisticsSegments.size() - 1, selSync.getIntValue()));
    }

    /** 下拉选择：仅正向分发 C2S（服务端钳位后经 KEY_SEL_LOGI 推回权威值）。 */
    private void onUnitSelected(int idx) {
        if (idx >= 0) actions.selectLogistics(idx);
    }

    /** 下拉选项文案：占位项灰字；正常项「物流单元 @段N」（@段N 后缀照 ClusterBoosterView 同款硬编码）。 */
    private String formatUnitOption(int idx) {
        if (idx < 0 || idx >= logisticsSegments.size()) {
            return EnumChatFormatting.GRAY + tr("gtsr.gui.cluster.editor.no_logistics");
        }
        return EnumChatFormatting.WHITE + tr("gtsr.gui.cluster.unit_type.logistics")
            + EnumChatFormatting.GRAY
            + String.format(tr("gtsr.gui.cluster.editor.segment"), logisticsSegments.get(idx));
    }

    /** 按同步快照重建下拉选项（物流单元增删时；列表实例持续，菜单缓存随 deleteMenu 失效）。 */
    private void refreshDropdownOptions() {
        if (unitDropdown == null) return;
        unitDropdown.clearOptions();
        if (logisticsSegments.isEmpty()) {
            unitDropdown.option(-1);
        } else {
            for (int i = 0; i < logisticsSegments.size(); i++) {
                unitDropdown.option(i);
            }
        }
        unitDropdown.deleteMenu();
    }

    /** 链状态 banner 文案（推演经 ClusterChainFSM 客户端纯函数，见 LogisticsChain.isValidStructure 同口径）。 */
    private String formatBanner() {
        if (chainOrdinals.isEmpty()) {
            return EnumChatFormatting.GRAY + tr("gtsr.gui.cluster.chain.empty_hint");
        }
        Form finalForm = ClusterChainFSM.simulate(chainLinks());
        if (ClusterChainFSM.isTerminal(finalForm)) {
            return EnumChatFormatting.GREEN + "✔ "
                + String.format(tr("gtsr.gui.cluster.chain.preview_valid"), tr(ClusterChainFSM.formLangKey(finalForm)));
        }
        return EnumChatFormatting.RED + "✖ " + tr("gtsr.gui.cluster.chain.invalid_not_terminal");
    }

    // ==================== 左列：可用链步 + 预设 ====================

    private void buildLinkColumn(ParentWidget<?> page, int contentX, int contentY, int leftW) {
        page.child(
            IKey.str(
                EnumChatFormatting.GOLD.toString() + EnumChatFormatting.BOLD
                    + tr("gtsr.gui.cluster.editor.available_links"))
                .asWidget()
                .pos(contentX, contentY + COLUMN_DY)
                .scale(0.75f));
        for (int i = 0; i < LINKS.length; i++) {
            page.child(buildLinkRow(LINKS[i], contentX, contentY + LINKS_DY + i * LINK_PITCH, leftW));
        }
        // 预设区：灰字说明 + 6 个预设按钮（2 行 × 3 列，本地名自带 T1 基准秒数）
        page.child(
            IKey.str(EnumChatFormatting.GRAY + tr("gtsr.gui.cluster.editor.presets"))
                .asWidget()
                .pos(contentX, contentY + PRESET_LABEL_DY)
                .scale(0.7f));
        int btnW = (leftW - PRESET_GAP * 2) / 3;
        for (int i = 0; i < LogisticsChain.PRESET_COUNT; i++) {
            final int presetIdx = i;
            int col = i % 3;
            int row = i / 3;
            page.child(
                new ButtonWidget<>()
                    .pos(
                        contentX + col * (btnW + PRESET_GAP),
                        contentY + PRESET_LABEL_DY + 10 + row * (PRESET_BTN_H + PRESET_GAP))
                    .size(btnW, PRESET_BTN_H)
                    .overlay(IKey.lang(LogisticsChain.getPresetNameKey(presetIdx)))
                    .onMousePressed(mouseButton -> {
                        actions.applyPreset(presetIdx);
                        return true;
                    })
                    .setEnabledIf(w -> !logisticsSegments.isEmpty())
                    .tooltipDynamic(t -> t.addLine(IKey.str(EnumChatFormatting.GRAY + presetSummary(presetIdx)))));
        }
    }

    /** 单个链步行：两行 overlay（链步名 + 绿字在链计数 / 灰字基础秒与附加信息），不可用灰化 + 锁定 tooltip。 */
    private IWidget buildLinkRow(ChainLink link, int x, int y, int width) {
        return new ButtonWidget<>().pos(x, y)
            .size(width, LINK_ROW_H)
            .overlay(IKey.dynamic(() -> formatLinkOverlay(link)))
            .onMousePressed(mouseButton -> {
                if (isLinkAvailable(link.ordinal())) actions.appendLink(link.ordinal());
                return true;
            })
            // 不调用 setEnabledIf：MUI2 会跳过 disabled widget 的绘制，导致锁定的链步整行空白。
            // 锁定行保留渲染，以灰字和 tooltip 表示原因；点击由上面的可用性判定拦截。
            .tooltipDynamic(t -> {
                t.addLine(IKey.lang("gtsr.gui.cluster.chain.append"));
                int kind = lockKinds[link.ordinal()];
                if (kind != LOCK_NONE) {
                    t.addLine(IKey.str(EnumChatFormatting.RED + lockReasonText(link, kind)));
                }
            });
    }

    /** 链步行 overlay 两行文案：首行 名称 + 在链 ×N（绿）；次行 基础秒 + 附加信息 + 锁定原因（红）。 */
    private String formatLinkOverlay(ChainLink link) {
        EnumChatFormatting availabilityColor = isLinkAvailable(link.ordinal()) ? EnumChatFormatting.WHITE
            : EnumChatFormatting.DARK_GRAY;
        StringBuilder first = new StringBuilder(availabilityColor.toString()).append(tr(link.getLangKey()));
        int count = countInChain(link.ordinal());
        if (count > 0) {
            first.append("  ")
                .append(EnumChatFormatting.GREEN)
                .append(String.format(tr("gtsr.gui.cluster.chain.in_chain"), count));
        }
        StringBuilder second = new StringBuilder(
            isLinkAvailable(link.ordinal()) ? EnumChatFormatting.GRAY.toString()
                : EnumChatFormatting.DARK_GRAY.toString())
                    .append(String.format(tr("gtsr.gui.cluster.editor.link_seconds"), link.getBaseSeconds()));
        switch (link) {
            case ORE_WASH:
                second.append(" · ")
                    .append(tr("gtsr.gui.cluster.editor.need_water"));
                break;
            case CHEM_BATH:
                second.append(" · ")
                    .append(tr("gtsr.gui.cluster.editor.need_chem"));
                break;
            case MAGNETIC_SEPARATOR:
                second.append(" · ")
                    .append(tr("gtsr.gui.cluster.editor.need_power"));
                break;
            case THERMOCENTRIFUGE:
                second.append(" · ")
                    .append(tr("gtsr.gui.cluster.editor.need_power"));
                break;
            default:
                break;
        }
        int kind = lockKinds[link.ordinal()];
        if (kind != LOCK_NONE) {
            second.append(" · ")
                .append(EnumChatFormatting.RED)
                .append(lockReasonText(link, kind));
        }
        return first + "\n" + second;
    }

    /** 锁定原因文案（客户端由种类 + 链步反查：module 类带所需工作单元名填充 %s）。 */
    private String lockReasonText(ChainLink link, int kind) {
        switch (kind) {
            case LOCK_SIMPLE_WASH:
                return tr("gtsr.gui.cluster.link.locked_simple_wash");
            case LOCK_MODULE:
                return String.format(tr("gtsr.gui.cluster.link.locked_module"), tr(unitTypeKey(link)));
            case LOCK_UNFORMED:
                return tr("gtsr.gui.cluster.link.locked_unformed");
            default:
                return tr("gtsr.gui.cluster.link.locked_power");
        }
    }

    /** 链步所需工作单元类型的本地化键（与 ChainLink.getRequiredUnitClass 的类映射一一对应，客户端安全）。 */
    private static String unitTypeKey(ChainLink link) {
        switch (link) {
            case CRUSH:
            case HAMMER:
                return "gtsr.gui.cluster.unit_type.crusher";
            case SIMPLE_WASH:
            case ORE_WASH:
            case CHEM_BATH:
                return "gtsr.gui.cluster.unit_type.ore_washer";
            case CENTRIFUGE:
                return "gtsr.gui.cluster.unit_type.centrifuge";
            case THERMOCENTRIFUGE:
                return "gtsr.gui.cluster.unit_type.thermal_centrifuge";
            case SIFTER:
                return "gtsr.gui.cluster.unit_type.sifter";
            case MAGNETIC_SEPARATOR:
                return "gtsr.gui.cluster.unit_type.magnetic_separator";
            case FURNACE:
                return "gtsr.gui.cluster.unit_type.furnace";
            default:
                return "gtsr.gui.cluster.unit_type.crusher";
        }
    }

    /** 预设 tooltip 摘要：预设链步名按序「A → B → C」。 */
    private static String presetSummary(int presetIdx) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (ChainLink link : LogisticsChain.getPresetLinks(presetIdx)) {
            if (!first) sb.append(" → ");
            sb.append(tr(link.getLangKey()));
            first = false;
        }
        return sb.toString();
    }

    // ==================== 右列：当前链 chips + 推演条 + 公式折叠区 ====================

    private void buildChainColumn(ParentWidget<?> page, int rightX, int contentY, int rightW, int contentH) {
        // 标题 + 清空按钮（链空时灰化）
        page.child(
            IKey.str(
                EnumChatFormatting.GOLD.toString() + EnumChatFormatting.BOLD
                    + tr("gtsr.gui.cluster.editor.current_chain"))
                .asWidget()
                .pos(rightX, contentY + COLUMN_DY)
                .scale(0.75f));
        page.child(
            new ButtonWidget<>().pos(rightX + rightW - 46, contentY + COLUMN_DY - 2)
                .size(46, 13)
                .overlay(IKey.lang("gtsr.gui.cluster.editor.clear"))
                .onMousePressed(mouseButton -> {
                    actions.clearChain();
                    return true;
                })
                .setEnabledIf(w -> !chainOrdinals.isEmpty())
                .tooltipBuilder(t -> t.addLine(IKey.lang("gtsr.gui.cluster.chain.remove"))));

        // chips 滚动列表：常驻实例，数据变化仅重建行内容（滚动位置不回顶）
        page.child(
            new ParentWidget<>().pos(rightX, contentY + CHIPS_DY)
                .size(rightW, CHIPS_H)
                .background(GuiTextures.DISPLAY));
        chipsList = new ListWidget<>();
        chipsList.pos(rightX + 2, contentY + CHIPS_DY + 2)
            .size(rightW - 4, CHIPS_H - 4);
        page.child(chipsList);

        // 状态机推演条：原矿 →(链步)→ 形态 →…→ 终态（TextWidget 按宽折行，长链不溢出）
        page.child(
            new ParentWidget<>().pos(rightX, contentY + FLOW_DY)
                .size(rightW, FLOW_H)
                .background(GuiTextures.DISPLAY)
                .child(
                    new TextWidget<>(IKey.dynamic(this::formatFlowLine)).pos(3, 2)
                        .size(rightW - 6, FLOW_H - 4)));

        // 公式折叠头：本地 boolean 翻转（不走路由）；展开 4 行动态公式
        page.child(
            new ButtonWidget<>().pos(rightX, contentY + FOLD_DY)
                .size(112, FOLD_BTN_H)
                .overlay(IKey.dynamic(() -> tr("gtsr.gui.cluster.formula.foldout") + (formulaOpen ? " ▾" : " ▸")))
                .onMousePressed(mouseButton -> {
                    formulaOpen = !formulaOpen;
                    rebuildFormula();
                    return true;
                }));

        // 公式区（深色底 + 滚动列表：折叠时清空，展开 4 行）
        int formulaH = Math.max(40, contentH - FORMULA_DY);
        page.child(
            new ParentWidget<>().pos(rightX, contentY + FORMULA_DY)
                .size(rightW, formulaH)
                .background(GuiTextures.DISPLAY));
        formulaList = new ListWidget<>();
        formulaList.pos(rightX + 3, contentY + FORMULA_DY + 2)
            .size(rightW - 6, formulaH - 4);
        page.child(formulaList);
    }

    /** 重建当前链 chips 行（数据变化时调用；空链显示占位提示）。 */
    private void rebuildChips() {
        if (chipsList == null) return;
        chipsList.removeAll();
        if (chainOrdinals.isEmpty()) {
            chipsList.child(
                IKey.str(EnumChatFormatting.GRAY + tr("gtsr.gui.cluster.chain.empty_hint"))
                    .asWidget()
                    .scale(0.9f));
            return;
        }
        for (int i = 0; i < chainOrdinals.size(); i++) {
            chipsList.child(buildChipRow(chainOrdinals.get(i), i));
        }
    }

    /** 单个 chip 行：序号 / 链步名 / 该步耗时（近似式，速度增幅省略） / ◀ ▶ ✖。 */
    private IWidget buildChipRow(int linkOrdinal, int index) {
        final int idx = index;
        return Flow.row()
            .widthRel(1f)
            .height(14)
            .childPadding(2)
            .crossAxisAlignment(Alignment.CrossAxis.CENTER)
            .child(
                IKey.str(EnumChatFormatting.GOLD.toString() + EnumChatFormatting.BOLD + (idx + 1) + ".")
                    .asWidget()
                    .width(14)
                    .scale(0.85f))
            .child(
                IKey.str(EnumChatFormatting.WHITE + tr(LINKS[linkOrdinal].getLangKey()))
                    .asWidget()
                    .width(42)
                    .scale(0.72f))
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.GREEN
                        + String.format(tr("gtsr.gui.cluster.editor.step_time"), formatSec(stepTimeSec(linkOrdinal))))
                    .asWidget()
                    .width(58)
                    .scale(0.7f))
            .child(
                new ButtonWidget<>().size(14, 13)
                    .overlay(IKey.str("◀"))
                    .onMousePressed(mouseButton -> {
                        actions.moveLink(idx, -1);
                        return true;
                    })
                    .tooltipBuilder(t -> t.addLine(IKey.lang("gtsr.gui.cluster.chain.move_left"))))
            .child(
                new ButtonWidget<>().size(14, 13)
                    .overlay(IKey.str("▶"))
                    .onMousePressed(mouseButton -> {
                        actions.moveLink(idx, 1);
                        return true;
                    })
                    .tooltipBuilder(t -> t.addLine(IKey.lang("gtsr.gui.cluster.chain.move_right"))))
            .child(
                new ButtonWidget<>().size(16, 13)
                    .overlay(IKey.str(EnumChatFormatting.RED + "✖"))
                    .onMousePressed(mouseButton -> {
                        actions.removeLink(idx);
                        return true;
                    })
                    .tooltipBuilder(t -> t.addLine(IKey.lang("gtsr.gui.cluster.chain.remove"))));
    }

    /** 推演条文案：原矿 →(链步)→ 形态 →…→ 终态（终态绿 + ✓终；末位非终态红）。 */
    private String formatFlowLine() {
        if (chainOrdinals.isEmpty()) {
            return EnumChatFormatting.GRAY + tr("gtsr.gui.cluster.chain.empty_hint");
        }
        List<ChainLink> links = chainLinks();
        StringBuilder sb = new StringBuilder();
        Form form = ClusterChainFSM.start();
        sb.append(formTag(form, false));
        for (int i = 0; i < links.size(); i++) {
            Form next = ClusterChainFSM.next(form, links.get(i));
            boolean last = i == links.size() - 1;
            sb.append(EnumChatFormatting.GRAY)
                .append(" →(")
                .append(EnumChatFormatting.WHITE)
                .append(
                    tr(
                        links.get(i)
                            .getLangKey()))
                .append(EnumChatFormatting.GRAY)
                .append(")→ ")
                .append(formTag(next, last));
            form = next;
        }
        return sb.toString();
    }

    /** 推演条形态标签：终态绿 +「✓终」，末位非终态红，中间形态浅蓝。 */
    private String formTag(Form form, boolean last) {
        boolean terminal = ClusterChainFSM.isTerminal(form);
        EnumChatFormatting color = terminal ? EnumChatFormatting.GREEN
            : (last ? EnumChatFormatting.RED : EnumChatFormatting.AQUA);
        return color + tr(ClusterChainFSM.formLangKey(form))
            + (terminal ? " ✓" + tr("gtsr.gui.cluster.chain.preview_terminal") : "");
    }

    /** 重建公式区（折叠时清空；展开 4 行动态公式，数值经 cl.f.* 四个同步值服务端现算）。 */
    private void rebuildFormula() {
        if (formulaList == null) return;
        formulaList.removeAll();
        if (!formulaOpen) return;
        formulaList.child(
            IKey.dynamic(
                () -> EnumChatFormatting.YELLOW + tr("gtsr.gui.cluster.formula.time")
                    + " = "
                    + EnumChatFormatting.GREEN
                    + String.format("%.2f", fTimeSync.getDoubleValue())
                    + " s")
                .asWidget()
                .scale(0.85f));
        formulaList.child(
            IKey.dynamic(
                () -> EnumChatFormatting.YELLOW + tr("gtsr.gui.cluster.formula.parallel")
                    + " = "
                    + EnumChatFormatting.GREEN
                    + (long) Math.round(fParallelSync.getDoubleValue()))
                .asWidget()
                .scale(0.85f));
        formulaList.child(
            IKey.dynamic(
                () -> EnumChatFormatting.YELLOW + tr("gtsr.gui.cluster.formula.chain_steam")
                    + " = "
                    + EnumChatFormatting.RED
                    + NumberFormatUtil.formatNumber(Math.round(fSteamSync.getDoubleValue()))
                    + " L/s")
                .asWidget()
                .scale(0.85f));
        formulaList.child(
            IKey.dynamic(
                () -> EnumChatFormatting.YELLOW + tr("gtsr.gui.cluster.formula.total_steam")
                    + " = "
                    + EnumChatFormatting.RED
                    + NumberFormatUtil.formatNumber(Math.round(fTotalSteamSync.getDoubleValue()))
                    + " L/s")
                .asWidget()
                .scale(0.85f));
    }

    // ==================== 快照读数与服务端取值辅助 ====================

    /** 链步是否可用（快照锁定种类为 0）。 */
    private boolean isLinkAvailable(int linkOrdinal) {
        return linkOrdinal >= 0 && linkOrdinal < lockKinds.length && lockKinds[linkOrdinal] == LOCK_NONE;
    }

    /** 「在链 ×N」计数：快照链 ordinal 列表中该链步出现次数。 */
    private int countInChain(int linkOrdinal) {
        int count = 0;
        for (int ordinal : chainOrdinals) {
            if (ordinal == linkOrdinal) count++;
        }
        return count;
    }

    /** chip 该步耗时（近似式）：base × TIER_TIME_FACTOR[tier] ÷ max(1, 同类工作模块数)（速度增幅省略）。 */
    private double stepTimeSec(int linkOrdinal) {
        double factor = ClusterParams.TIER_TIME_FACTOR[tierIndex()];
        int modules = Math.max(1, linkOrdinal < unitCounts.length ? unitCounts[linkOrdinal] : 0);
        return LINKS[linkOrdinal].getBaseSeconds() * factor / modules;
    }

    /** 结构 tier（读主壳 KEY_TIER 同步值缓存，钳到 [0, TIER_COUNT-1]；缺省 0）。 */
    private int tierIndex() {
        SyncHandler<?> handler = sync.findSyncHandlerNullable(ClusterGuiSync.KEY_TIER);
        if (handler instanceof ValueSyncHandler<?, ?>value && value.getValue() instanceof Number number) {
            return Math.max(0, Math.min(ClusterParams.TIER_COUNT - 1, number.intValue()));
        }
        return 0;
    }

    /** 服务端公式 supplier 用 tier（getStructureTierIndex 可能 -1，钳到 [0, TIER_COUNT-1]）。 */
    private int tierIdxClamped() {
        return Math.max(0, Math.min(ClusterParams.TIER_COUNT - 1, machine.getStructureTierIndex()));
    }

    /** 服务端：当前选中物流单元的链 links live 视图（未选中返回 null，ExecutionPlan 防御口径兼容）。 */
    private List<ChainLink> selectedLinks() {
        MTEBasicLogisticsUnit unit = machine.getSelectedLogisticsUnit();
        return unit != null ? unit.getChain()
            .getLinks() : null;
    }

    /** 服务端：增幅聚合快照（BoosterState.aggregate，空列表返回 EMPTY 单例）。 */
    private BoosterState currentBooster() {
        return BoosterState.aggregate(
            machine.getTopology()
                .getBoosterUnits());
    }

    /** 快照链 ordinal 列表 → ChainLink 列表（FSM 推演入参）。 */
    private List<ChainLink> chainLinks() {
        List<ChainLink> links = new ArrayList<>(chainOrdinals.size());
        for (int ordinal : chainOrdinals) {
            links.add(LINKS[ordinal]);
        }
        return links;
    }

    /** 秒数一位小数（整数显整数）。 */
    private static String formatSec(double seconds) {
        if (Math.abs(seconds - Math.rint(seconds)) < 1e-6) {
            return String.valueOf((long) Math.rint(seconds));
        }
        return String.format("%.1f", seconds);
    }

    /** lang 键本地化（简写）。 */
    private static String tr(String key) {
        return StatCollector.translateToLocal(key);
    }
}
