package com.miaokatze.gtsr.common.gui.cluster;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.IntValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.value.sync.SyncHandler;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.menu.DropdownWidget;
import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;
import com.miaokatze.gtsr.common.gui.cluster.ClusterGuiSync.ClusterActionSyncHandler;
import com.miaokatze.gtsr.common.gui.widget.ScrollKeepingListWidget;
import com.miaokatze.gtsr.common.machine.cluster.ChainLink;
import com.miaokatze.gtsr.common.machine.cluster.ClusterChainFSM;
import com.miaokatze.gtsr.common.machine.cluster.ClusterChainFSM.Form;
import com.miaokatze.gtsr.common.machine.cluster.ClusterParams;
import com.miaokatze.gtsr.common.machine.cluster.LogisticsChain;
import com.miaokatze.gtsr.common.machine.cluster.MTESteamMineralLogisticsCluster;

/**
 * 集群三视图·页 1「链路」（批2 E6 重写；<b>无预设按钮</b>——预设数据已随 LogisticsChain 删除）。
 *
 * <p>
 * 布局（页内绝对定位，582×258）：
 * <ul>
 * <li>顶行 y0..14：物流模块下拉（位置+关联+授权状态；无物流模块空状态引导）+ 两级有效性横幅
 * （结构有效 = FSM 终态 / 当前可执行 = 服务端逐 link 真实查询，失败步名一并显示）；</li>
 * <li>左列（x0..272）：「可用链」10 行（链名/在链 ×N/耗时与介质需求/禁用原因；前置不满足
 * <b>恒渲染</b>灰字+锁因 tooltip，不用 setEnabledIf——禁用=整棵子树不渲染）——修订 FC 行高 ×2
 * （33px），10 行超列高装 {@link ScrollKeepingListWidget} 滚动容器（同 chips 防回顶），「可用链」
 * 标题与底部同步反馈行（已授权/未保存更改/链无效/模块未关联）及链长 N/16 钉在滚动区外；</li>
 * <li>右列（x280..582）：「当前有序链」chips 滚动列表（序号/名称/实际耗时/左移右移删除，可重复；
 * {@link ScrollKeepingListWidget} 防回顶）+ 清空钮与保存钮；状态机推演条（原矿→逐步→透传→终态）；
 * 可折叠性能详情（单物品耗时/有效并行/预测吞吐/本链蒸汽/集群总蒸汽——<b>全部服务端真值</b>，
 * ×100 定点 IntSyncValue 解码，客户端不推算）。</li>
 * </ul>
 *
 * <p>
 * 数据流（全部只读 {@link ClusterGuiSync} 键）：KEY_LE_UNITS（下拉数据）/ KEY_LE_CHAIN（整链快照，
 * 变化重建 chips）/ KEY_LE_LOCK（锁因+模块计数）/ KEY_LE_EXEC+KEY_LE_FAIL（两级横幅）/ KEY_SEL_LOGI /
 * KEY_F_*（性能真值）。C2S 仅经 {@link ClusterActionSyncHandler}（§4.4 加固：防抖+服务端复核，
 * 客户端只发索引/ordinal/整链 ordinal 数组）。<b>暂存保存流程</b>（决策7）：追加/删除/位移/清空四类
 * 编辑全部只改本地暂存 {@code stagedOrdinals}（干净态跟随 KEY_LE_CHAIN 快照，dirty 期间不被覆盖），
 * 不再即时发包；保存按钮客户端预校验（非空 + FSM 结构有效）通过后才发 SAVE_CHAIN，服务器快照
 * 追平暂存时自动清脏回到绿态。
 */
public final class ClusterLinkEditorView {

    /** 下拉菜单面板名（面板内唯一）。 */
    private static final String DROPDOWN_MENU_NAME = "clLinkEditorMenu";
    /** 顶行高（下拉 + 横幅）。 */
    private static final int TOP_ROW_H = 14;
    /** 下拉宽。 */
    private static final int DROPDOWN_W = 200;
    /** 左列宽。 */
    private static final int LEFT_W = 272;
    /** 右列起点与宽（页内坐标，合计 = 582 内容宽）。 */
    private static final int RIGHT_X = 280;
    private static final int RIGHT_W = 302;
    /** 列标题偏移。 */
    private static final int TITLE_DY = 17;
    /** 链步行区：起始偏移与行距（修订 FC 行高 ×2：34 = 33 按钮高 + 1 行距）。 */
    private static final int LINKS_DY = 28;
    private static final int LINK_PITCH = 34;
    /** 左列底部同步反馈行高（Y 按 contentH 钉底，不随行距下移）。 */
    private static final int FEEDBACK_H = 12;
    /** 左列可用链滚动区内缩（DISPLAY 背景面板与列表间留白，同右列 chips）。 */
    private static final int LINK_LIST_INSET = 2;
    /** 右列：chips 区。 */
    private static final int CHIPS_DY = 28;
    private static final int CHIPS_H = 88;
    /** 右列：FSM 推演条。 */
    private static final int FLOW_DY = CHIPS_DY + CHIPS_H + 4;
    private static final int FLOW_H = 26;
    /** 右列：性能折叠头与面板。 */
    private static final int FOLD_DY = FLOW_DY + FLOW_H + 4;
    private static final int FOLD_H = 12;
    private static final int PERF_DY = FOLD_DY + FOLD_H + 3;
    /** 同步反馈：待应用窗口（ms，本地编辑后等待 S2C 回流）。 */
    private static final long APPLY_WINDOW_MS = 1500L;

    /** 链步枚举缓存（ordinal 即 KEY_LE_LOCK 各段下标）。 */
    private static final ChainLink[] LINKS = ChainLink.values();

    private final PanelSyncManager sync;
    private final ClusterActionSyncHandler actions;
    /** 公式折叠区展开态（客户端本地状态，不落服务器）。 */
    private boolean formulaOpen = false;
    /** chips 滚动偏移回写。 */
    private int chipsScrollValue;
    /** 可用链列表滚动偏移回写。 */
    private int linksScrollValue;
    /** 同步反馈：最近一次客户端拒绝时间戳（0=无；锁定点击与保存校验失败共用）。 */
    private long lastRejectAt;
    /** 本地暂存链（决策7）：干净态恒跟随服务器快照（见 {@link #displayOrdinals}），dirty 期间为独立编辑副本不被覆盖。 */
    private List<Integer> stagedOrdinals = new ArrayList<>();
    /** 暂存脏标记：首个本地编辑置位；服务器快照追平暂存（保存生效回流）时自动清除。 */
    private boolean stagingDirty = false;
    /** 常驻部件。 */
    private DropdownWidget<Integer, ?> unitDropdown;
    private ListWidget<IWidget, ?> chipsList;
    private ListWidget<IWidget, ?> perfList;

    private ClusterLinkEditorView(PanelSyncManager sync, ClusterActionSyncHandler actions) {
        this.sync = sync;
        this.actions = actions;
    }

    /** 构建链路页（三视图契约入口，双端执行；panel/machine/contentX/Y 为契约保留参数）。 */
    public static void build(ModularPanel panel, PanelSyncManager sync, ClusterActionSyncHandler actions,
        MTESteamMineralLogisticsCluster machine, PagedWidget<?> paged, int contentX, int contentY, int contentW,
        int contentH) {
        ClusterLinkEditorView view = new ClusterLinkEditorView(sync, actions);
        // 整链快照/单元清单变化监听：chips 行与下拉选项按 S2C 推送重建（常驻实例不回顶）
        SyncHandler<?> chainSyncHandler = sync.findSyncHandlerNullable(ClusterGuiSync.KEY_LE_CHAIN);
        if (chainSyncHandler instanceof StringSyncValue chainSync) {
            chainSync.setChangeListener(view::rebuildChips);
        }
        SyncHandler<?> unitsSyncHandler = sync.findSyncHandlerNullable(ClusterGuiSync.KEY_LE_UNITS);
        if (unitsSyncHandler instanceof StringSyncValue unitsSync) {
            unitsSync.setChangeListener(view::refreshDropdownOptions);
        }
        ParentWidget<?> page = new ParentWidget<>().size(contentW, contentH);
        view.buildTopRow(page);
        view.buildLinkColumn(page, contentH);
        view.buildChainColumn(page, contentH);
        paged.addPage(page);
        // 初始构建（sync handler 未初始化时为空态，首同步后 changeListener 再刷新）
        view.refreshDropdownOptions();
        view.rebuildChips();
        view.rebuildPerf();
    }

    // ==================== 顶行：下拉 + 两级有效性横幅 ====================

    private void buildTopRow(ParentWidget<?> page) {
        unitDropdown = new DropdownWidget<>(DROPDOWN_MENU_NAME, Integer.class);
        unitDropdown.pos(0, 0)
            .size(DROPDOWN_W, TOP_ROW_H)
            .value(new IntValue.Dynamic(this::selectedDisplayIndex, this::onUnitSelected))
            .optionToWidget(
                (idx, forSelectedDisplay) -> IKey.dynamic(() -> formatUnitOption(idx))
                    .asWidget())
            .maxVerticalMenuSize(80);
        page.child(unitDropdown);
        // 两级横幅（恒渲染单行动态）：结构有效（FSM 终态）+ 当前可执行（服务端逐 link 查询 + 失败步）
        page.child(
            IKey.dynamic(() -> bannerText())
                .asWidget()
                .pos(DROPDOWN_W + 12, 2)
                .scale(0.75f)
                .width(560 - DROPDOWN_W));
    }

    private int selectedDisplayIndex() {
        int unitCount = unitSegments().length;
        if (unitCount == 0) return -1;
        return Math.max(0, Math.min(unitCount - 1, ClusterGuiSync.intOf(sync, ClusterGuiSync.KEY_SEL_LOGI, 0)));
    }

    /** 下拉选择：仅正向分发 C2S（服务端复核后经 KEY_SEL_LOGI 推回权威值）。 */
    private void onUnitSelected(int idx) {
        if (idx >= 0) actions.selectLogistics(idx);
    }

    /** 下拉选项文案：物流模块 @段N · 已关联/未关联 · 已授权/未授权（flags 见 KEY_LE_UNITS）。 */
    private String formatUnitOption(int idx) {
        int[] segments = unitSegments();
        if (idx < 0 || idx >= segments.length) {
            return EnumChatFormatting.GRAY + tr("gtsr.cluster.gui.link.unit.none");
        }
        int flags = unitFlags()[idx];
        boolean connected = (flags & 0x01) != 0;
        boolean powered = (flags & 0x04) != 0;
        return EnumChatFormatting.WHITE + tr("gtsr.gui.cluster.unit_type.logistics")
            + EnumChatFormatting.GRAY
            + String.format(tr("gtsr.gui.cluster.editor.segment"), segments[idx])
            + " · "
            + (connected ? EnumChatFormatting.GREEN + tr("gtsr.cluster.gui.link.unit.linked")
                : EnumChatFormatting.RED + tr("gtsr.cluster.gui.link.unit.unlinked"))
            + " · "
            + (powered ? EnumChatFormatting.GREEN + tr("gtsr.cluster.gui.link.unit.authorized")
                : EnumChatFormatting.RED + tr("gtsr.cluster.gui.link.unit.unauthorized"));
    }

    /** 物流单元段号数组（KEY_LE_UNITS 解析缓存直读）。 */
    private int[] unitSegments() {
        String encoded = ClusterGuiSync.strOf(sync, ClusterGuiSync.KEY_LE_UNITS, "");
        List<Integer> segs = new ArrayList<>();
        List<Integer> flags = new ArrayList<>();
        parseUnits(encoded, segs, flags);
        int[] out = new int[segs.size()];
        for (int i = 0; i < out.length; i++) out[i] = segs.get(i);
        return out;
    }

    /** 物流单元 flags 数组（与 unitSegments 同解析）。 */
    private int[] unitFlags() {
        String encoded = ClusterGuiSync.strOf(sync, ClusterGuiSync.KEY_LE_UNITS, "");
        List<Integer> segs = new ArrayList<>();
        List<Integer> flags = new ArrayList<>();
        parseUnits(encoded, segs, flags);
        int[] out = new int[flags.size()];
        for (int i = 0; i < out.length; i++) out[i] = flags.get(i);
        return out;
    }

    /** 解析 "seg:flags,seg:flags,..."（畸形段跳过）。 */
    private static void parseUnits(String encoded, List<Integer> segs, List<Integer> flags) {
        if (encoded == null || encoded.isEmpty()) return;
        for (String entry : encoded.split(",", -1)) {
            int colon = entry.indexOf(':');
            if (colon <= 0) continue;
            try {
                segs.add(
                    Integer.parseInt(
                        entry.substring(0, colon)
                            .trim()));
                flags.add(
                    Integer.parseInt(
                        entry.substring(colon + 1)
                            .trim()));
            } catch (NumberFormatException ignored) {
                // 畸形段跳过（segs/flags 已加项需成对——异常前不添加）
            }
        }
    }

    /** 两级横幅文案：结构（FSM 终态，绿/红）+ 当前（服务端可执行查询，绿/红+失败步名）。 */
    private String bannerText() {
        int exec = ClusterGuiSync.intOf(sync, ClusterGuiSync.KEY_LE_EXEC, 0);
        boolean structValid = exec >= 1;
        StringBuilder sb = new StringBuilder();
        sb.append(
            structValid ? EnumChatFormatting.GREEN + "✔ " + tr("gtsr.cluster.gui.link.banner.struct_ok")
                : EnumChatFormatting.RED + "✖ " + tr("gtsr.cluster.gui.link.banner.struct_bad"));
        sb.append(EnumChatFormatting.GRAY)
            .append(" | ");
        if (exec == 2) {
            sb.append(EnumChatFormatting.GREEN)
                .append("✔ ")
                .append(tr("gtsr.cluster.gui.link.banner.exec_ok"));
        } else {
            int fail = ClusterGuiSync.intOf(sync, ClusterGuiSync.KEY_LE_FAIL, 0);
            String failName = fail > 0 && fail <= LINKS.length ? tr(LINKS[fail - 1].getLangKey()) : "--";
            sb.append(EnumChatFormatting.RED)
                .append("✖ ")
                .append(String.format(tr("gtsr.cluster.gui.link.banner.exec_bad"), failName));
        }
        return sb.toString();
    }

    // ==================== 左列：可用链 + 同步反馈（无预设） ====================

    private void buildLinkColumn(ParentWidget<?> page, int contentH) {
        page.child(
            IKey.str(EnumChatFormatting.GOLD.toString() + EnumChatFormatting.BOLD + tr("gtsr.cluster.gui.link.avail"))
                .asWidget()
                .pos(0, TITLE_DY - 12)
                .scale(0.7f));
        // 可用链滚动区（修订 FC）：10 行 ×34 = 340 超出列区剩余高，装入 ScrollKeepingListWidget
        // （同右列 chips 防回顶方案）；标题与底部反馈行钉在滚动区外，不随滚动移走
        int feedbackDy = contentH - FEEDBACK_H;
        int listH = feedbackDy - LINKS_DY - 2;
        page.child(
            new ParentWidget<>().pos(0, LINKS_DY)
                .size(LEFT_W, listH)
                .background(GuiTextures.DISPLAY));
        ListWidget<IWidget, ?> linksList = new ScrollKeepingListWidget(
            () -> linksScrollValue,
            value -> linksScrollValue = value);
        linksList.pos(LINK_LIST_INSET, LINKS_DY + LINK_LIST_INSET)
            .size(LEFT_W - 2 * LINK_LIST_INSET, listH - 2 * LINK_LIST_INSET);
        for (int i = 0; i < LINKS.length; i++) {
            linksList.child(buildLinkRow(LINKS[i]));
        }
        page.child(linksList);
        // 同步反馈行 + 链长计数（动态，钉底）
        page.child(
            IKey.dynamic(this::feedbackText)
                .asWidget()
                .pos(0, feedbackDy)
                .scale(0.65f));
        page.child(
            IKey.dynamic(
                () -> EnumChatFormatting.GRAY + String.format(
                    tr("gtsr.cluster.gui.link.chain.len"),
                    displayOrdinals().size(),
                    ClusterParams.CHAIN_MAX_LINKS))
                .asWidget()
                .pos(150, feedbackDy)
                .scale(0.65f));
    }

    /**
     * 单个链步行（恒渲染——不用 setEnabledIf，禁用行灰字+锁因仍在）：两行 overlay
     * （名称+在链×N / 基准秒+介质需求+锁因红字），可用点击本地暂存追加（决策7：不发 C2S），
     * 锁定点击或链长已满本地拒绝（反馈行提示）。
     * 修订 FC：行高 ×2（33px；overlay 无折叠内容仍两行）；列表内由 ListWidget 纵向堆叠布局
     * （不设主轴 pos），widthRel 填满列宽，marginBottom 留 1px 行距。
     */
    private IWidget buildLinkRow(ChainLink link) {
        return new ButtonWidget<>().widthRel(1f)
            .height(LINK_PITCH - 1)
            .marginBottom(1)
            .overlay(IKey.dynamic(() -> formatLinkOverlay(link)))
            .onMousePressed(mouseButton -> {
                if (lockKind(link.ordinal()) == 0 && displayOrdinals().size() < ClusterParams.CHAIN_MAX_LINKS) {
                    stageAppend(link.ordinal());
                } else {
                    lastRejectAt = System.currentTimeMillis();
                }
                return true;
            })
            .tooltipDynamic(t -> {
                t.addLine(IKey.lang("gtsr.gui.cluster.chain.append"));
                int kind = lockKind(link.ordinal());
                if (kind != 0) t.addLine(IKey.str(EnumChatFormatting.RED + lockReasonText(link, kind)));
            });
    }

    /** 链步行 overlay：首行 名称 + 在链 ×N（绿）；次行 基准秒 + 介质需求 + 锁因（红）。 */
    private String formatLinkOverlay(ChainLink link) {
        boolean available = lockKind(link.ordinal()) == 0;
        EnumChatFormatting base = available ? EnumChatFormatting.WHITE : EnumChatFormatting.DARK_GRAY;
        StringBuilder first = new StringBuilder(base.toString()).append(tr(link.getLangKey()));
        int count = countInChain(link.ordinal());
        if (count > 0) {
            first.append("  ")
                .append(EnumChatFormatting.GREEN)
                .append(String.format(tr("gtsr.gui.cluster.chain.in_chain"), count));
        }
        StringBuilder second = new StringBuilder(
            available ? EnumChatFormatting.GRAY.toString() : EnumChatFormatting.DARK_GRAY.toString())
                .append(String.format(tr("gtsr.gui.cluster.editor.link_seconds"), link.getBaseSeconds()))
                .append(" · ")
                .append(mediumText(link));
        int kind = lockKind(link.ordinal());
        if (kind != 0) {
            second.append(" · ")
                .append(EnumChatFormatting.RED)
                .append(lockReasonText(link, kind));
        }
        return first + "\n" + second;
    }

    /** 介质需求短文案（每批 1000L 水/化浴液、需持续通电、简易水洗）。 */
    private static String mediumText(ChainLink link) {
        return switch (link) {
            case ORE_WASH -> tr("gtsr.cluster.gui.link.need_water");
            case CHEM_BATH -> tr("gtsr.cluster.gui.link.need_chem");
            case MAGNETIC_SEPARATOR, THERMOCENTRIFUGE -> tr("gtsr.cluster.gui.link.need_power");
            case SIMPLE_WASH -> tr("gtsr.cluster.gui.link.need_simple_wash");
            default -> tr("gtsr.cluster.gui.link.no_medium");
        };
    }

    /** 锁因文案（kind → 既有锁定 key；module 类带所需单元名填充 %s）。 */
    private static String lockReasonText(ChainLink link, int kind) {
        return switch (kind) {
            case 1 -> tr("gtsr.gui.cluster.link.locked_simple_wash");
            case 2 -> String.format(tr("gtsr.gui.cluster.link.locked_module"), tr(unitTypeKey(link)));
            case 3 -> tr("gtsr.gui.cluster.link.locked_unformed");
            default -> tr("gtsr.gui.cluster.link.locked_power");
        };
    }

    /** 链步所需工作单元类型 lang key（与 ChainLink.getRequiredUnitClass 的映射一一对应，客户端安全）。 */
    private static String unitTypeKey(ChainLink link) {
        return switch (link) {
            case CRUSH, HAMMER -> "gtsr.gui.cluster.unit_type.crusher";
            case SIMPLE_WASH, ORE_WASH, CHEM_BATH -> "gtsr.gui.cluster.unit_type.ore_washer";
            case CENTRIFUGE -> "gtsr.gui.cluster.unit_type.centrifuge";
            case THERMOCENTRIFUGE -> "gtsr.gui.cluster.unit_type.thermal_centrifuge";
            case SIFTER -> "gtsr.gui.cluster.unit_type.sifter";
            case MAGNETIC_SEPARATOR -> "gtsr.gui.cluster.unit_type.magnetic_separator";
            case FURNACE -> "gtsr.gui.cluster.unit_type.furnace";
        };
    }

    /**
     * 同步反馈行（优先级从高到低，决策9）：模块未关联（灰）/ 保存被拒·链无效（红，复用 lastRejectAt
     * 窗口）/ 暂存未保存更改（橙）/ 已授权可执行（绿——保存生效后快照追平暂存即回到此绿态确认）。
     * 本方法每帧求值，兼作快照追平检测点（相等才清 dirty，显示内容不变无渲染抖动）。
     */
    private String feedbackText() {
        checkSnapshotCaughtUp();
        long now = System.currentTimeMillis();
        if (unitSegments().length == 0) {
            return EnumChatFormatting.GRAY + tr("gtsr.cluster.gui.link.unit.none");
        }
        if (now - lastRejectAt < APPLY_WINDOW_MS) {
            return EnumChatFormatting.RED + "✖ " + tr("gtsr.cluster.gui.link.chain.invalid");
        }
        if (stagingDirty) {
            return EnumChatFormatting.GOLD + tr("gtsr.cluster.gui.link.chain.unsaved");
        }
        return EnumChatFormatting.GREEN + "✔ " + tr("gtsr.cluster.gui.link.sync.ok");
    }

    /**
     * 快照追平检测（幂等）：仅当暂存脏且服务器 KEY_LE_CHAIN 快照与本地暂存完全相等
     * （SAVE_CHAIN 已被服务端接受并回推）时清脏；不等则保持 dirty 继续等待（服务端静默拒绝时
     * 橙态持续，用户可修改后重试保存）。
     */
    private void checkSnapshotCaughtUp() {
        if (!stagingDirty) return;
        if (chainOrdinals().equals(stagedOrdinals)) {
            stagingDirty = false;
        }
    }

    // ==================== 右列：当前有序链 + FSM 推演 + 性能详情 ====================

    private void buildChainColumn(ParentWidget<?> page, int contentH) {
        // 标题 + 清空钮
        page.child(
            IKey.str(EnumChatFormatting.GOLD.toString() + EnumChatFormatting.BOLD + tr("gtsr.cluster.gui.link.chain"))
                .asWidget()
                .pos(RIGHT_X, TITLE_DY - 12)
                .scale(0.7f));
        // 保存钮（决策7）：清空钮左侧同规格；客户端预校验通过才发 SAVE_CHAIN，非法红字反馈不发包
        page.child(
            new ButtonWidget<>().pos(RIGHT_X + RIGHT_W - 86, TITLE_DY - 14)
                .size(42, 12)
                .overlay(IKey.lang("gtsr.cluster.gui.link.chain.save"))
                .onMousePressed(mouseButton -> {
                    attemptSave();
                    return true;
                })
                .tooltipBuilder(t -> {
                    t.addLine(IKey.lang("gtsr.cluster.gui.link.chain.save"));
                    t.addLine(IKey.lang("gtsr.cluster.gui.link.chain.invalid"));
                }));
        page.child(
            new ButtonWidget<>().pos(RIGHT_X + RIGHT_W - 42, TITLE_DY - 14)
                .size(42, 12)
                .overlay(IKey.lang("gtsr.cluster.gui.link.chain.clear"))
                .onMousePressed(mouseButton -> {
                    if (!displayOrdinals().isEmpty()) {
                        stageClear();
                    }
                    return true;
                })
                .tooltipBuilder(t -> t.addLine(IKey.lang("gtsr.gui.cluster.chain.remove"))));

        // chips 滚动列表（常驻实例 + 重建行内容；ScrollKeepingListWidget 防回顶）
        page.child(
            new ParentWidget<>().pos(RIGHT_X, CHIPS_DY)
                .size(RIGHT_W, CHIPS_H)
                .background(GuiTextures.DISPLAY));
        chipsList = new ScrollKeepingListWidget(() -> chipsScrollValue, value -> chipsScrollValue = value);
        chipsList.pos(RIGHT_X + 2, CHIPS_DY + 2)
            .size(RIGHT_W - 4, CHIPS_H - 4);
        page.child(chipsList);

        // FSM 推演条：原矿 →(链步)→ 形态 →…→ 终态（TextWidget 按宽折行）
        page.child(
            new ParentWidget<>().pos(RIGHT_X, FLOW_DY)
                .size(RIGHT_W, FLOW_H)
                .background(GuiTextures.DISPLAY)
                .child(
                    new TextWidget<>(IKey.dynamic(this::formatFlowLine)).pos(3, 2)
                        .size(RIGHT_W - 6, FLOW_H - 4)));

        // 性能折叠头（客户端本地翻转，不走路由）
        page.child(
            new ButtonWidget<>().pos(RIGHT_X, FOLD_DY)
                .size(RIGHT_W, FOLD_H)
                .overlay(IKey.dynamic(() -> tr("gtsr.cluster.gui.link.perf") + (formulaOpen ? " ▾" : " ▸")))
                .onMousePressed(mouseButton -> {
                    formulaOpen = !formulaOpen;
                    rebuildPerf();
                    return true;
                }));

        // 性能详情（折叠时清空；展开 5 行服务端真值）
        int perfH = Math.max(30, contentH - PERF_DY);
        page.child(
            new ParentWidget<>().pos(RIGHT_X, PERF_DY)
                .size(RIGHT_W, perfH)
                .background(GuiTextures.DISPLAY));
        perfList = new ListWidget<>();
        perfList.pos(RIGHT_X + 3, PERF_DY + 2)
            .size(RIGHT_W - 6, perfH - 4);
        page.child(perfList);
    }

    /** 重建 chips 行（KEY_LE_CHAIN 变化时由外部 changeListener 或本地暂存编辑后调用；空链占位提示）。 */
    private void rebuildChips() {
        if (chipsList == null) return;
        chipsList.removeAll();
        List<Integer> ordinals = displayOrdinals();
        if (ordinals.isEmpty()) {
            chipsList.child(
                IKey.str(EnumChatFormatting.GRAY + tr("gtsr.cluster.gui.link.chain.empty"))
                    .asWidget()
                    .scale(0.8f));
            return;
        }
        for (int i = 0; i < ordinals.size(); i++) {
            chipsList.child(buildChipRow(ordinals.get(i), i));
        }
    }

    /** 单 chip 行：序号 / 名称 / 实际耗时（基准×tier÷同类模块数，显示口径） / ◀ ▶ ✖。 */
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
                    .scale(0.75f))
            .child(
                IKey.str(EnumChatFormatting.WHITE + tr(LINKS[linkOrdinal].getLangKey()))
                    .asWidget()
                    .width(44)
                    .scale(0.65f))
            .child(
                IKey.dynamic(() -> EnumChatFormatting.GREEN + stepTimeText(linkOrdinal))
                    .asWidget()
                    .width(46)
                    .scale(0.6f))
            .child(
                new ButtonWidget<>().size(14, 13)
                    .overlay(IKey.str("◀"))
                    .onMousePressed(mouseButton -> {
                        stageMove(idx, -1);
                        return true;
                    })
                    .tooltipBuilder(t -> t.addLine(IKey.lang("gtsr.gui.cluster.chain.move_left"))))
            .child(
                new ButtonWidget<>().size(14, 13)
                    .overlay(IKey.str("▶"))
                    .onMousePressed(mouseButton -> {
                        stageMove(idx, 1);
                        return true;
                    })
                    .tooltipBuilder(t -> t.addLine(IKey.lang("gtsr.gui.cluster.chain.move_right"))))
            .child(
                new ButtonWidget<>().size(16, 13)
                    .overlay(IKey.str(EnumChatFormatting.RED + "✖"))
                    .onMousePressed(mouseButton -> {
                        stageRemove(idx);
                        return true;
                    })
                    .tooltipBuilder(t -> t.addLine(IKey.lang("gtsr.gui.cluster.chain.remove"))));
    }

    /** chip 实际耗时显示：base × TIER_TIME_FACTOR[tier] ÷ max(1, 同类模块数)（读 KEY_TIER/KEY_LE_LOCK 缓存）。 */
    private String stepTimeText(int linkOrdinal) {
        int tier = ClusterGuiSync.intOf(sync, ClusterGuiSync.KEY_TIER, 0);
        double factor = ClusterParams.TIER_TIME_FACTOR[Math.max(0, Math.min(ClusterParams.TIER_COUNT - 1, tier))];
        int[] counts = lockCounts();
        int modules = Math.max(1, linkOrdinal < counts.length ? counts[linkOrdinal] : 0);
        return formatSec(LINKS[linkOrdinal].getBaseSeconds() * factor / modules) + "s";
    }

    /** FSM 推演条：原矿 →(链步)→ 形态 →…→ 终态（终态绿 + ✓终；末位非终态红）。 */
    private String formatFlowLine() {
        List<Integer> ordinals = displayOrdinals();
        if (ordinals.isEmpty()) {
            return EnumChatFormatting.GRAY + tr("gtsr.cluster.gui.link.chain.empty");
        }
        StringBuilder sb = new StringBuilder();
        Form form = ClusterChainFSM.start();
        sb.append(formTag(form, false));
        for (int i = 0; i < ordinals.size(); i++) {
            Form next = ClusterChainFSM.next(form, LINKS[ordinals.get(i)]);
            boolean last = i == ordinals.size() - 1;
            sb.append(EnumChatFormatting.GRAY)
                .append(" →(")
                .append(EnumChatFormatting.WHITE)
                .append(tr(LINKS[ordinals.get(i)].getLangKey()))
                .append(EnumChatFormatting.GRAY)
                .append(")→ ")
                .append(formTag(next, last));
            form = next;
        }
        return sb.toString();
    }

    private static String formTag(Form form, boolean last) {
        boolean terminal = ClusterChainFSM.isTerminal(form);
        EnumChatFormatting color = terminal ? EnumChatFormatting.GREEN
            : (last ? EnumChatFormatting.RED : EnumChatFormatting.AQUA);
        return color + tr(ClusterChainFSM.formLangKey(form))
            + (terminal ? " ✓" + tr("gtsr.gui.cluster.chain.preview_terminal") : "");
    }

    /** 重建性能详情（折叠清空；展开 5 行 ×100 定点真值：耗时/并行/吞吐/本链蒸汽/总蒸汽）。 */
    private void rebuildPerf() {
        if (perfList == null) return;
        perfList.removeAll();
        if (!formulaOpen) return;
        perfList.child(perfLine("gtsr.cluster.gui.link.perf.time", () -> {
            int raw = ClusterGuiSync.intOf(sync, ClusterGuiSync.KEY_F_TIME, 0);
            return EnumChatFormatting.GREEN + String.format("%.2f", raw / 100.0D) + " s";
        }));
        perfList.child(
            perfLine(
                "gtsr.cluster.gui.link.perf.parallel",
                () -> EnumChatFormatting.GREEN
                    + String.valueOf(ClusterGuiSync.intOf(sync, ClusterGuiSync.KEY_F_PAR, 0))));
        perfList.child(perfLine("gtsr.cluster.gui.link.perf.thru", () -> {
            int raw = ClusterGuiSync.intOf(sync, ClusterGuiSync.KEY_F_THRU, 0);
            return EnumChatFormatting.GREEN + String.format("%.2f", raw / 100.0D)
                + " "
                + tr("gtsr.cluster.gui.card.thru.unit");
        }));
        perfList.child(
            perfLine(
                "gtsr.cluster.gui.link.perf.steam",
                () -> NumberFormatUtil.formatNumber(ClusterGuiSync.intOf(sync, ClusterGuiSync.KEY_F_STEAM, 0))
                    + " L/s"));
        perfList.child(
            perfLine(
                "gtsr.cluster.gui.link.perf.steam_total",
                () -> EnumChatFormatting.RED
                    + NumberFormatUtil.formatNumber(ClusterGuiSync.intOf(sync, ClusterGuiSync.KEY_F_TOTAL, 0))
                    + " L/s"));
    }

    /** 性能行：黄标签 = 绿值（IKey.dynamic 随同步缓存刷新）。 */
    private static IWidget perfLine(String labelKey, java.util.function.Supplier<String> value) {
        return IKey.dynamic(() -> EnumChatFormatting.YELLOW + tr(labelKey) + " = " + value.get())
            .asWidget()
            .scale(0.7f);
    }

    // ==================== 本地暂存编辑（决策7：不发 C2S，保存按钮统一下发） ====================

    /** 当前展示链：干净态跟随服务器快照（KEY_LE_CHAIN），暂存脏期间为本地编辑副本。 */
    private List<Integer> displayOrdinals() {
        return stagingDirty ? stagedOrdinals : chainOrdinals();
    }

    /** 干净态首次本地编辑：物化快照副本为独立暂存并置脏（此后不被快照覆盖）。 */
    private void ensureStaged() {
        if (!stagingDirty) {
            stagedOrdinals = new ArrayList<>(chainOrdinals());
            stagingDirty = true;
        }
    }

    /** 暂存链尾追加（保持 CHAIN_MAX_LINKS 上限）并重建 chips。 */
    private void stageAppend(int ordinal) {
        ensureStaged();
        if (stagedOrdinals.size() >= ClusterParams.CHAIN_MAX_LINKS) return;
        stagedOrdinals.add(ordinal);
        rebuildChips();
    }

    /** 暂存链步位移（-1 左移 / +1 右移；越界安全忽略）并重建 chips。 */
    private void stageMove(int index, int dir) {
        ensureStaged();
        if (index < 0 || index >= stagedOrdinals.size()) return;
        int target = index + dir;
        if (target < 0 || target >= stagedOrdinals.size()) return;
        Collections.swap(stagedOrdinals, index, target);
        rebuildChips();
    }

    /** 按索引删除暂存链步（越界安全忽略）并重建 chips。 */
    private void stageRemove(int index) {
        ensureStaged();
        if (index < 0 || index >= stagedOrdinals.size()) return;
        stagedOrdinals.remove(index);
        rebuildChips();
    }

    /** 清空暂存链并重建 chips。 */
    private void stageClear() {
        ensureStaged();
        stagedOrdinals.clear();
        rebuildChips();
    }

    /**
     * 保存暂存链（决策7/9）：staged 非空且 FSM 结构有效才发 SAVE_CHAIN；否则红字反馈不发包。
     * 结构校验为纯函数客户端安全：按 ordinal 建 {@link LogisticsChain} 后复用 {@link LogisticsChain#isValidStructure()}。
     */
    private void attemptSave() {
        List<Integer> staged = displayOrdinals();
        boolean valid = !staged.isEmpty() && isValidStructure(staged);
        if (!valid) {
            lastRejectAt = System.currentTimeMillis();
            return;
        }
        int[] ordinals = new int[staged.size()];
        for (int i = 0; i < ordinals.length; i++) {
            ordinals[i] = staged.get(i);
        }
        actions.saveChain(ordinals);
    }

    /** 客户端结构校验（纯函数）：恰好一个终态产物（FSM 终态 ∈ {DUST, INGOT}）。 */
    private static boolean isValidStructure(List<Integer> ordinals) {
        int[] arr = new int[ordinals.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = ordinals.get(i);
        }
        return LogisticsChain.fromOrdinalArray(arr)
            .isValidStructure();
    }

    // ==================== 快照读数 ====================

    /** 当前链 ordinal 列表（KEY_LE_CHAIN 解析，越界项丢弃）。 */
    private List<Integer> chainOrdinals() {
        return ClusterGuiSync.parseIntList(ClusterGuiSync.strOf(sync, ClusterGuiSync.KEY_LE_CHAIN, ""), LINKS.length);
    }

    /** 链步锁因数组（KEY_LE_LOCK 第 1 段 per-link "kind:count"）。 */
    private int[] lockKinds() {
        String encoded = ClusterGuiSync.strOf(sync, ClusterGuiSync.KEY_LE_LOCK, "");
        int[] out = new int[LINKS.length];
        if (encoded.isEmpty()) return out;
        String[] entries = encoded.split(",", -1);
        int n = Math.min(LINKS.length, entries.length);
        for (int i = 0; i < n; i++) {
            int colon = entries[i].indexOf(':');
            if (colon <= 0) continue;
            try {
                out[i] = Integer.parseInt(
                    entries[i].substring(0, colon)
                        .trim());
            } catch (NumberFormatException ignored) {
                out[i] = 0;
            }
        }
        return out;
    }

    private int lockKind(int linkOrdinal) {
        int[] kinds = lockKinds();
        return linkOrdinal >= 0 && linkOrdinal < kinds.length ? kinds[linkOrdinal] : 0;
    }

    /** 链步同类模块计数数组（KEY_LE_LOCK 第 2 段 per-link）。 */
    private int[] lockCounts() {
        String encoded = ClusterGuiSync.strOf(sync, ClusterGuiSync.KEY_LE_LOCK, "");
        int[] out = new int[LINKS.length];
        if (encoded.isEmpty()) return out;
        String[] entries = encoded.split(",", -1);
        int n = Math.min(LINKS.length, entries.length);
        for (int i = 0; i < n; i++) {
            int colon = entries[i].indexOf(':');
            if (colon <= 0) continue;
            try {
                out[i] = Integer.parseInt(
                    entries[i].substring(colon + 1)
                        .trim());
            } catch (NumberFormatException ignored) {
                out[i] = 0;
            }
        }
        return out;
    }

    /** 「在链 ×N」计数（读展示链：干净态快照 / 暂存脏期间 staged）。 */
    private int countInChain(int linkOrdinal) {
        int count = 0;
        for (int ordinal : displayOrdinals()) {
            if (ordinal == linkOrdinal) count++;
        }
        return count;
    }

    /** 重建下拉选项（物流单元增删时；实例持续，菜单缓存随 deleteMenu 失效）。 */
    private void refreshDropdownOptions() {
        if (unitDropdown == null) return;
        unitDropdown.clearOptions();
        int unitCount = unitSegments().length;
        if (unitCount == 0) {
            unitDropdown.option(-1);
        } else {
            for (int i = 0; i < unitCount; i++) {
                unitDropdown.option(i);
            }
        }
        unitDropdown.deleteMenu();
    }

    /** 秒数一位小数（整数显整数）。 */
    private static String formatSec(double seconds) {
        if (Math.abs(seconds - Math.rint(seconds)) < 1e-6) {
            return String.valueOf((long) Math.rint(seconds));
        }
        return String.format("%.1f", seconds);
    }

    private static String tr(String key) {
        return StatCollector.translateToLocal(key);
    }
}
