package com.miaokatze.gtsr.common.gui.cluster;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.PageButton;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.ProgressWidget;
import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;
import com.miaokatze.gtsr.common.gui.cluster.ClusterGuiSync.ClusterActionSyncHandler;
import com.miaokatze.gtsr.common.machine.cluster.ClusterParams;
import com.miaokatze.gtsr.common.machine.cluster.MTESteamMineralLogisticsCluster;
import com.miaokatze.gtsr.main.GTSteamReborn;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.modularui2.GTGuiTextures;

/**
 * 蒸汽矿物物流集群「集群终端」主壳 GUI（批2 E6 全量重写，620×340 = {@link ClusterParams} 冻结尺寸，
 * 3x 缩放恰贴 1080p）。纵向预算：标题栏 16（机器名+tier 徽标+运行状态+电源钮）→ 顶栏四卡区 40
 * （蒸汽/润滑/热量/吞吐，各约 142×32）→ 内容区 258 → 底栏 14（简短运行提示/异常摘要）。
 *
 * <p>
 * 布局硬约束（实机验证）：面板直接 child 全部显式绝对定位（Flow 不得作为面板直接 child——pos 失效被居中）；
 * 左侧页签轨 30 宽（3 页签 28×28 竖排，{@link PageButton} + {@link PagedWidget.Controller}，无 TabRow）；
 * 三页各由 {@link ClusterTopologyView}/{@link ClusterLinkEditorView}/{@link ClusterBoosterView} 填充。
 *
 * <p>
 * 同步与动作（全部经 {@link ClusterGuiSync}，视图零注册）：
 * {@link ClusterGuiSync#registerS2C} 注册 §4.3 全部快照/标量（含 E5 冻结契约
 * buildTopologySnapshot/getHeatPercent 等）；{@link ClusterGuiSync#registerC2S} 返回
 * {@link ClusterActionSyncHandler}（§4.4 加固）转发三视图。热量为 1% 量化 IntSyncValue
 * （禁 Double 每 tick 直推，批1 的 KEY_PREHEAT/KEY_THROUGHPUT Double 已废除）。
 *
 * <p>
 * 三态色（MC 色板近似）：正常绿 {@link EnumChatFormatting#GREEN}；警告橙
 * {@link EnumChatFormatting#GOLD}；错误红 {@link EnumChatFormatting#RED}；中性灰
 * {@link EnumChatFormatting#GRAY}；缺增幅流体紫 {@link EnumChatFormatting#DARK_PURPLE}；
 * tier 徽章真彩底（#C87E3B/#C2C8D0/#8EA2C8/#6E7F8C）经 {@link Rectangle} ARGB。
 * 蒸汽/润滑异常三重编码：红字 + ⚠ 图标 + 中文词。
 */
public class MTESteamMineralLogisticsClusterGui implements IGuiHolder<PosGuiData> {

    // —— 主壳布局常量（先声明：下方区块常量按此推导；三视图 build 参数即取这些值）——
    /** 内容区左上角 X（页签轨右侧，面板坐标）。 */
    static final int CONTENT_X = 32;
    /** 内容区左上角 Y（四卡区之下）。 */
    static final int CONTENT_Y = 62;
    /** 内容区宽（620 - 32 - 6 右缘）。 */
    static final int CONTENT_W = ClusterParams.GUI_WIDTH - CONTENT_X - 6;
    /** 内容区高（底栏之上）。 */
    static final int CONTENT_H = ClusterParams.GUI_HEIGHT - CONTENT_Y - 20;
    /** 底栏 Y（面板坐标，单行 14px）。 */
    static final int FOOTBAR_Y = ClusterParams.GUI_HEIGHT - 16;

    /** 四卡区（y 18..58，高 40；卡高 32 顶对齐 + 底部留白；标题栏即 y 0..16）。 */
    private static final int CARDS_Y = 18;
    private static final int CARD_W = 142;
    private static final int CARD_H = 32;
    private static final int CARD_GAP = 4;
    private static final int CARDS_X0 = CONTENT_X;
    /** 页签轨（x 2..30）：3 页签 28×28 竖排。 */
    private static final int TAB_X = 2;
    private static final int TAB_Y = CONTENT_Y;
    private static final int TAB_SIZE = 28;
    private static final int TAB_PITCH = 30;
    /** 电源钮（标题栏右部，避开右上角关闭钮）。 */
    private static final int POWER_BTN_X = ClusterParams.GUI_WIDTH - 116;
    private static final int POWER_BTN_W = 56;
    /** 页签 lang key（0=拓扑 / 1=链路 / 2=增幅）。 */
    private static final String[] TAB_LANG_KEYS = { "gtsr.cluster.gui.tab.topology", "gtsr.cluster.gui.tab.links",
        "gtsr.cluster.gui.tab.boosters" };
    /** tier 徽章真彩点（青铜/钢/钛/钨钢 + 未成型灰；预分配避免每帧新建）。 */
    private static final Rectangle[] TIER_BADGE_DOTS = { new Rectangle().color(0xFFC87E3B),
        new Rectangle().color(0xFFC2C8D0), new Rectangle().color(0xFF8EA2C8), new Rectangle().color(0xFF6E7F8C),
        new Rectangle().color(0xFF6E6E6E) };
    /** 卡底深色（#26262B，禁纯黑）。 */
    private static final int CARD_BG_ARGB = 0xFF26262B;

    private final MTESteamMineralLogisticsCluster machine;
    private PanelSyncManager syncManager;
    private ClusterActionSyncHandler actions;

    public MTESteamMineralLogisticsClusterGui(MTESteamMineralLogisticsCluster machine) {
        this.machine = machine;
    }

    public MTESteamMineralLogisticsCluster getMachine() {
        return machine;
    }

    /** buildUI 后可用（§4.4 加固的 C2S 动作处理器：togglePower/selectLogistics/saveChain 等；append/move 等旧编辑位已占位退役）。 */
    public ClusterActionSyncHandler getActions() {
        return actions;
    }

    /** buildUI 后可用（视图读 §4.3 快照用）。 */
    public PanelSyncManager getSyncManager() {
        return syncManager;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ModularScreen createScreen(PosGuiData data, ModularPanel mainPanel) {
        return new ModularScreen(GTSteamReborn.MODID, mainPanel);
    }

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        this.syncManager = syncManager;
        ModularPanel panel = ModularPanel
            .defaultPanel("gtsr_cluster_terminal", ClusterParams.GUI_WIDTH, ClusterParams.GUI_HEIGHT)
            .background(GTGuiTextures.BACKGROUND_STANDARD)
            .child(ButtonWidget.panelCloseButton());

        // 同步注册顺序：先 S2C 全量（§4.3），再 C2S 动作处理器（§4.4）
        ClusterGuiSync.registerS2C(syncManager, machine);
        this.actions = ClusterGuiSync.registerC2S(panel, machine);

        buildTitleBar(panel, syncManager);
        buildTopCards(panel, syncManager);

        // 内容区 PagedWidget + 控制器（绝对定位；初始页取机器字段——buildUI 期首包未到，
        // 机器字段是双端构造期唯一可靠来源，工厂 open 前服务端写入）
        PagedWidget<?> paged = new PagedWidget<>();
        paged.pos(CONTENT_X, CONTENT_Y)
            .size(CONTENT_W, CONTENT_H);
        PagedWidget.Controller controller = new PagedWidget.Controller();
        paged.controller(controller);
        panel.child(paged);

        // 左侧页签轨：PageButton 纵排（overlay 走 IKey 即 IDrawable；页签名恒 2 字适配 28×28）
        for (int i = 0; i < TAB_LANG_KEYS.length; i++) {
            panel.child(
                new PageButton(i, controller).pos(TAB_X, TAB_Y + i * TAB_PITCH)
                    .size(TAB_SIZE, TAB_SIZE)
                    .overlay(IKey.lang(TAB_LANG_KEYS[i])));
        }

        // 三视图按页签序各 addPage 一次（页内绝对定位，见各视图）
        ClusterTopologyView
            .build(panel, syncManager, actions, machine, paged, CONTENT_X, CONTENT_Y, CONTENT_W, CONTENT_H);
        ClusterLinkEditorView
            .build(panel, syncManager, actions, machine, paged, CONTENT_X, CONTENT_Y, CONTENT_W, CONTENT_H);
        ClusterBoosterView
            .build(panel, syncManager, actions, machine, paged, CONTENT_X, CONTENT_Y, CONTENT_W, CONTENT_H);
        paged.initialPage(Math.max(0, Math.min(TAB_LANG_KEYS.length - 1, machine.getGuiInitialPage())));

        // 底栏：简短运行提示/异常摘要（单行动态）
        panel.child(
            IKey.dynamic(() -> footbarText(syncManager))
                .asWidget()
                .pos(CONTENT_X, FOOTBAR_Y)
                .scale(0.7f));
        return panel;
    }

    // —— 标题栏：机器名 + tier 徽章（真彩点+名）+ 运行状态 + 电源钮 ——

    private void buildTitleBar(ModularPanel panel, PanelSyncManager sync) {
        panel.child(
            IKey.lang("gt.blockmachines.gtsr.cluster.controller.name")
                .asWidget()
                .pos(4, 4)
                .scale(0.85f));
        // tier 徽章：真彩方点（随结构 tier 联动，未成型灰）+ tier 名
        ParentWidget<?> tierDot = new ParentWidget<>().pos(210, 6)
            .size(5, 5)
            .background(TIER_BADGE_DOTS[4]);
        tierDot.onUpdateListener(w -> {
            int tier = ClusterGuiSync.intOf(sync, ClusterGuiSync.KEY_TIER, -1);
            w.background(TIER_BADGE_DOTS[tier >= 0 && tier < 4 ? tier : 4]);
        }, true);
        panel.child(tierDot);
        panel.child(IKey.dynamic(() -> {
            int tier = ClusterGuiSync.intOf(sync, ClusterGuiSync.KEY_TIER, -1);
            return tier
                >= 0 ? EnumChatFormatting.WHITE + StatCollector.translateToLocal(ClusterParams.ClusterTier.get(tier)
                    .getLangKey())
                    : EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.cluster.gui.title.unformed");
        })
            .asWidget()
            .pos(218, 4)
            .scale(0.8f));
        // 运行状态：预热中（橙）/运行中（绿）/停机（灰）
        panel.child(
            IKey.dynamic(() -> runStateText(sync))
                .asWidget()
                .pos(290, 4)
                .scale(0.8f));
        // 电源钮：ButtonWidget → actions.togglePower() 专用 C2S 通道（不依赖同步值可写）
        panel.child(
            new ButtonWidget<>().pos(POWER_BTN_X, 1)
                .size(POWER_BTN_W, 14)
                .overlay(
                    IKey.dynamic(
                        () -> StatCollector.translateToLocal(
                            ClusterGuiSync.boolOf(sync, ClusterGuiSync.KEY_ENABLED, false) ? "gtsr.gui.cluster.power.on"
                                : "gtsr.gui.cluster.power.off")))
                .onMousePressed(mouseButton -> {
                    actions.togglePower();
                    return true;
                })
                .tooltipBuilder(t -> t.addLine(IKey.lang("gtsr.gui.cluster.power.tip"))));
    }

    /** 运行状态文案：未成型灰/停机灰/预热中橙/运行中绿（enabled + 满热 + 成型）。 */
    private static String runStateText(PanelSyncManager sync) {
        boolean enabled = ClusterGuiSync.boolOf(sync, ClusterGuiSync.KEY_ENABLED, false);
        int tier = ClusterGuiSync.intOf(sync, ClusterGuiSync.KEY_TIER, -1);
        if (tier < 0) return EnumChatFormatting.GRAY + tr("gtsr.cluster.gui.title.unformed");
        if (!enabled) return EnumChatFormatting.GRAY + tr("gtsr.cluster.gui.title.standby");
        int heat = ClusterGuiSync.intOf(sync, ClusterGuiSync.KEY_HEAT, 0);
        return heat >= 100 ? EnumChatFormatting.GREEN + tr("gtsr.cluster.gui.title.run")
            : EnumChatFormatting.GOLD + tr("gtsr.cluster.gui.title.preheating");
    }

    // —— 顶栏四卡（各 142×32 深底卡；全部面板直接 child 绝对定位） ——

    private void buildTopCards(ModularPanel panel, PanelSyncManager sync) {
        // 卡 1：蒸汽 L/s——停机为 0；异常三重编码（红字+⚠+中文词）
        panel.child(card(CARDS_X0, tr("gtsr.cluster.gui.card.steam")));
        panel.child(
            IKey.dynamic(
                () -> rateLine(
                    sync,
                    ClusterGuiSync.KEY_STEAM,
                    ClusterGuiSync.SUPPLY_STEAM_SHORT,
                    "gtsr.cluster.gui.card.steam.short"))
                .asWidget()
                .pos(CARDS_X0 + 3, CARDS_Y + 11)
                .scale(0.9f));
        // 卡 2：润滑 L/s——同法
        int lubeX = CARDS_X0 + (CARD_W + CARD_GAP);
        panel.child(card(lubeX, tr("gtsr.cluster.gui.card.lube")));
        panel.child(
            IKey.dynamic(
                () -> rateLine(
                    sync,
                    ClusterGuiSync.KEY_LUBE,
                    ClusterGuiSync.SUPPLY_LUBE_SHORT,
                    "gtsr.cluster.gui.card.lube.short"))
                .asWidget()
                .pos(lubeX + 3, CARDS_Y + 11)
                .scale(0.9f));
        // 卡 3：热量百分比——1% 步进橙色数字 + 进度条（数字后备常在）
        int heatX = CARDS_X0 + (CARD_W + CARD_GAP) * 2;
        panel.child(card(heatX, tr("gtsr.cluster.gui.card.heat")));
        panel.child(
            IKey.dynamic(() -> EnumChatFormatting.GOLD.toString() + EnumChatFormatting.BOLD + heatOf(sync) + "%")
                .asWidget()
                .pos(heatX + 3, CARDS_Y + 11)
                .scale(0.9f));
        panel.child(
            new ProgressWidget().pos(heatX + 3, CARDS_Y + 24)
                .size(CARD_W - 6, 7)
                .texture(GuiTextures.PROGRESS_ARROW, CARD_W - 6)
                .progress(() -> heatOf(sync) / 100.0D));
        // 卡 4：吞吐——真实矿/s + 累计小字
        int thruX = CARDS_X0 + (CARD_W + CARD_GAP) * 3;
        panel.child(card(thruX, tr("gtsr.cluster.gui.card.thru")));
        panel.child(IKey.dynamic(() -> {
            long thru = ClusterGuiSync.intOf(sync, ClusterGuiSync.KEY_THRU, 0);
            String number = NumberFormatUtil.formatNumber(thru);
            return (thru > 0 ? EnumChatFormatting.GREEN : EnumChatFormatting.WHITE) + number
                + " "
                + tr("gtsr.cluster.gui.card.thru.unit");
        })
            .asWidget()
            .pos(thruX + 3, CARDS_Y + 11)
            .scale(0.9f));
        panel.child(
            IKey.dynamic(
                () -> EnumChatFormatting.GRAY + String.format(
                    tr("gtsr.cluster.gui.card.thru.total"),
                    NumberFormatUtil.formatNumber(ClusterGuiSync.longOf(sync, ClusterGuiSync.KEY_TOTAL, 0L))))
                .asWidget()
                .pos(thruX + 3, CARDS_Y + 24)
                .scale(0.6f));
    }

    /** 卡容器：深底 #26262B + 灰色小标签（左上）。 */
    private static ParentWidget<?> card(int x, String label) {
        return new ParentWidget<>().pos(x, CARDS_Y)
            .size(CARD_W, CARD_H)
            .background(new Rectangle().color(CARD_BG_ARGB))
            .child(
                IKey.str(EnumChatFormatting.GRAY + label)
                    .asWidget()
                    .pos(3, 2)
                    .scale(0.6f));
    }

    /** 速率行：数值 + 单位；异常（供给位）三重编码——红字 + ⚠ 图标 + 中文词。 */
    private static String rateLine(PanelSyncManager sync, String valueKey, int supplyBit, String shortKey) {
        int value = ClusterGuiSync.intOf(sync, valueKey, 0);
        boolean alert = (ClusterGuiSync.intOf(sync, ClusterGuiSync.KEY_SUPPLY, 0) & supplyBit) != 0;
        String number = NumberFormatUtil.formatNumber(value);
        if (alert) {
            return EnumChatFormatting.RED + "\u26a0 "
                + EnumChatFormatting.BOLD
                + number
                + " "
                + EnumChatFormatting.RED
                + tr("gtsr.cluster.gui.card.unit.lps")
                + " \u26a0 "
                + tr(shortKey);
        }
        return EnumChatFormatting.WHITE + number + " " + tr("gtsr.cluster.gui.card.unit.lps");
    }

    /** 热量百分比（1% 量化 IntSyncValue 缓存直读；钳 0..100）。 */
    private static int heatOf(PanelSyncManager sync) {
        return Math.max(0, Math.min(100, ClusterGuiSync.intOf(sync, ClusterGuiSync.KEY_HEAT, 0)));
    }

    /** 底栏：异常摘要（红）优先，其次未成型提示，默认运行提示（灰）。 */
    private static String footbarText(PanelSyncManager sync) {
        int supply = ClusterGuiSync.intOf(sync, ClusterGuiSync.KEY_SUPPLY, 0);
        if ((supply & ClusterGuiSync.SUPPLY_STEAM_SHORT) != 0) {
            return EnumChatFormatting.RED + "\u26a0 " + tr("gtsr.cluster.gui.foot.steam_short");
        }
        if ((supply & ClusterGuiSync.SUPPLY_LUBE_SHORT) != 0) {
            return EnumChatFormatting.RED + "\u26a0 " + tr("gtsr.cluster.gui.foot.lube_short");
        }
        if (ClusterGuiSync.intOf(sync, ClusterGuiSync.KEY_TIER, -1) < 0) {
            return EnumChatFormatting.GRAY + tr("gtsr.cluster.gui.foot.unformed");
        }
        int brk = ClusterGuiSync.intOf(sync, ClusterGuiSync.KEY_BREAK, -1);
        if (brk >= 1) {
            return EnumChatFormatting.RED + String.format(tr("gtsr.cluster.gui.topo.error.ext"), brk);
        }
        return EnumChatFormatting.GRAY + tr("gtsr.cluster.gui.foot.normal");
    }

    /** lang 简写。 */
    private static String tr(String key) {
        return StatCollector.translateToLocal(key);
    }
}
