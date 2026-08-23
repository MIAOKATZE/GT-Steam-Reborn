package com.miaokatze.gtsr.common.gui.cluster;

import java.util.Locale;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.DoubleValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandler;
import com.cleanroommc.modularui.value.sync.ValueSyncHandler;
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
 * 蒸汽矿物物流集群「集群终端」三视图主壳 GUI（ModularUI 2，560×330 = ClusterParams.GUI_WIDTH/HEIGHT）。
 * 布局：
 * - 顶栏（y 4..56，横贯全宽除页签轨）：蒸汽 L/s（短缺红字 ⚠ 前缀）、润滑液 L/s（不足红标）、
 * 预热进度条 + 并列 "N%" 数字（降级数字并存）、吞吐 矿/s（&gt;0 绿字）+ 累计小字、开关机按钮；
 * - 左侧竖向页签轨（x 5..27，宽 ~24）：三个 PageButton 纵排切换 PagedWidget 三页；
 * - 内容区 PagedWidget（x 30..814，y 60..456）：三页各由 ClusterTopologyView / ClusterLinkEditorView /
 * ClusterBoosterView 并行切片填充（本壳只建页容器并转发构建参数）；
 * - 底部 footbar 灰色小字一行。
 *
 * 同步设计（ClusterGuiSync 并行契约）：
 * - ClusterGuiSync.registerS2C(syncManager, machine) 注册全部 KEY_* 展示值（S2C）；
 * 本壳经 findSyncHandlerNullable 取回并驱动 IKey.dynamic / ProgressWidget 动态展示
 * （数值读取对同步值具体类型不敏感：Double/Int/Long/Float SyncValue 一律取 double）；
 * - ClusterGuiSync.registerC2S(panel, machine) 返回 ClusterActionSyncHandler（togglePower() 等 C2S 动作），
 * 经 getActions() 暴露并在 buildUI 内转发给三视图构建。
 *
 * 坐标约定：PagedWidget 位于内容区；三个视图分别建立一张使用内容区本地坐标的页面。
 * 传给视图的 contentX/Y 仅保留兼容签名，实际布局以 contentW/H 为界，不得越出页面。
 */
public class MTESteamMineralLogisticsClusterGui implements IGuiHolder<PosGuiData> {

    /** 页签轨：x 5..27（宽 ~24），三个 22×22 页签纵排，与内容区（x 30 起）不重叠。 */
    private static final int TAB_RAIL_X = 5;
    private static final int TAB_RAIL_Y = 46;
    private static final int TAB_SIZE = 20;
    private static final int TAB_SPACING = 24;
    /** 内容区（PagedWidget）：紧凑窗口中保留左侧页签轨、顶栏和底部提示。 */
    private static final int CONTENT_X = 30;
    private static final int CONTENT_Y = 42;
    private static final int CONTENT_W = ClusterParams.GUI_WIDTH - CONTENT_X - 6;
    private static final int CONTENT_H = ClusterParams.GUI_HEIGHT - CONTENT_Y - 24;
    private static final int FOOTBAR_Y = ClusterParams.GUI_HEIGHT - 17;
    /** 紧凑顶栏各区块绝对定位。 */
    private static final int TOP_STEAM_X = 32;
    private static final int TOP_LUBE_X = 132;
    private static final int TOP_PREHEAT_X = 234;
    private static final int TOP_PREHEAT_BAR_W = 68;
    private static final int TOP_PREHEAT_PCT_X = TOP_PREHEAT_X + TOP_PREHEAT_BAR_W + 4;
    private static final int TOP_THROUGHPUT_X = 330;
    private static final int TOP_POWER_X = 464;
    private static final int TOP_POWER_W = 82;
    /** 页签 lang key（0=拓扑 / 1=联动 / 2=加速，lang 并行切片补）。 */
    private static final String[] TAB_LANG_KEYS = { "gtsr.gui.cluster.tab.topology", "gtsr.gui.cluster.tab.links",
        "gtsr.gui.cluster.tab.boosters" };

    private final MTESteamMineralLogisticsCluster machine;
    private PanelSyncManager syncManager;
    private ModularPanel panel;
    private ClusterActionSyncHandler actions;

    public MTESteamMineralLogisticsClusterGui(MTESteamMineralLogisticsCluster machine) {
        this.machine = machine;
    }

    public MTESteamMineralLogisticsCluster getMachine() {
        return machine;
    }

    /** buildUI 后可用（registerC2S 返回的 C2S 动作处理器：togglePower() 等）。 */
    public ClusterActionSyncHandler getActions() {
        return actions;
    }

    /** buildUI 后可用（供外部/视图再取 KEY_* 同步值）。 */
    public PanelSyncManager getSyncManager() {
        return syncManager;
    }

    /** buildUI 后可用（主面板实例）。 */
    public ModularPanel getPanel() {
        return panel;
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
        this.panel = panel;
        // 同步注册顺序：先 S2C 展示值（KEY_* 全量），再 C2S 动作处理器；随后顶栏/视图经 findSyncHandlerNullable 取回
        ClusterGuiSync.registerS2C(syncManager, machine);
        this.actions = ClusterGuiSync.registerC2S(panel, machine);

        buildTopBar(panel, syncManager);

        // 内容区 PagedWidget + 控制器；初始页取 machine.getGuiInitialPage()：
        // buildUI 期客户端尚未收到首包同步（findSyncHandler(KEY_INIT_PAGE) 默认 0），
        // 机器字段是双端构造期唯一可靠来源（服务端 set 后 openGui，客户端构造期一致），后续翻页为纯本地 UI 状态
        PagedWidget<?> paged = new PagedWidget<>();
        paged.pos(CONTENT_X, CONTENT_Y)
            .size(CONTENT_W, CONTENT_H);
        PagedWidget.Controller controller = new PagedWidget.Controller();
        paged.controller(controller);
        // 单页契约：壳只持有 PagedWidget，不预建空页；三个视图各自按页序 addPage 一次。
        panel.child(paged);

        // 左侧页签轨：PageButton 纵排（主题自带的选中态高亮即可，无需显式 tab 纹理）
        for (int i = 0; i < TAB_LANG_KEYS.length; i++) {
            panel.child(
                new PageButton(i, controller).pos(TAB_RAIL_X, TAB_RAIL_Y + i * TAB_SPACING)
                    .size(TAB_SIZE, TAB_SIZE)
                    .overlay(IKey.lang(TAB_LANG_KEYS[i])));
        }

        // 三视图按页签序各 addPage 一次，所有页均使用 PagedWidget 本地坐标。
        ClusterTopologyView
            .build(panel, syncManager, actions, machine, paged, CONTENT_X, CONTENT_Y, CONTENT_W, CONTENT_H);
        ClusterLinkEditorView
            .build(panel, syncManager, actions, machine, paged, CONTENT_X, CONTENT_Y, CONTENT_W, CONTENT_H);
        ClusterBoosterView
            .build(panel, syncManager, actions, machine, paged, CONTENT_X, CONTENT_Y, CONTENT_W, CONTENT_H);
        // 页面建齐后设置初始页，避免在空页集合上被框架提前钳位到 0。
        paged.initialPage(Math.max(0, Math.min(TAB_LANG_KEYS.length - 1, machine.getGuiInitialPage())));

        // 底部 footbar：灰色小字一行
        panel.child(
            IKey.dynamic(() -> EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.gui.cluster.footbar"))
                .asWidget()
                .pos(CONTENT_X, FOOTBAR_Y)
                .scale(0.8f));
        return panel;
    }

    // —— 顶栏（5 区，全部面板直接 child 绝对定位）——

    private void buildTopBar(ModularPanel panel, PanelSyncManager syncManager) {
        // 蒸汽区：动态行 = 标签 + 速率（KEY_STEAM_SHORT 为 true 时整行红字 + ⚠ 前缀）
        panel.child(
            IKey.dynamic(
                () -> formatRateLine(
                    "gtsr.gui.cluster.top.steam",
                    syncNumber(syncManager, ClusterGuiSync.KEY_STEAM_LPS),
                    syncBoolean(syncManager, ClusterGuiSync.KEY_STEAM_SHORT)))
                .asWidget()
                .pos(TOP_STEAM_X, 8)
                .scale(0.8f));
        // 润滑液区：同法（KEY_LUBE_OK 为 false 时红标）
        panel.child(
            IKey.dynamic(
                () -> formatRateLine(
                    "gtsr.gui.cluster.top.lube",
                    syncNumber(syncManager, ClusterGuiSync.KEY_LUBE_LPS),
                    !syncBoolean(syncManager, ClusterGuiSync.KEY_LUBE_OK)))
                .asWidget()
                .pos(TOP_LUBE_X, 8)
                .scale(0.8f));
        // 预热区：标签 + 进度条（PROGRESS_ARROW 为上下叠放的空/满双态纹理，拉伸为横条）+ 并列 "N%" 数字
        panel.child(
            IKey.lang("gtsr.gui.cluster.top.preheat")
                .asWidget()
                .pos(TOP_PREHEAT_X, 3)
                .scale(0.75f));
        panel.child(
            new ProgressWidget().pos(TOP_PREHEAT_X, 14)
                .size(TOP_PREHEAT_BAR_W, 10)
                .texture(GuiTextures.PROGRESS_ARROW, TOP_PREHEAT_BAR_W)
                .value(
                    new DoubleValue.Dynamic(() -> clamp01(syncNumber(syncManager, ClusterGuiSync.KEY_PREHEAT)), null)));
        // 降级数字并存：进度条右侧始终并列动态百分比（纹理缺失/渲染异常时仍有数字可读）
        panel.child(
            IKey.dynamic(
                () -> EnumChatFormatting.BLACK + String.format(
                    Locale.ROOT,
                    "%d%%",
                    Math.round(clamp01(syncNumber(syncManager, ClusterGuiSync.KEY_PREHEAT)) * 100)))
                .asWidget()
                .pos(TOP_PREHEAT_PCT_X, 16)
                .scale(0.75f));
        // 吞吐区：主行动态（>0 绿字），其下累计小字灰字
        panel.child(
            IKey.dynamic(
                () -> String.format(
                    StatCollector.translateToLocal("gtsr.gui.cluster.top.throughput"),
                    throughputValue(syncNumber(syncManager, ClusterGuiSync.KEY_THROUGHPUT))))
                .asWidget()
                .pos(TOP_THROUGHPUT_X, 8)
                .scale(0.8f));
        panel.child(
            IKey.dynamic(
                () -> EnumChatFormatting.GRAY + String.format(
                    StatCollector.translateToLocal("gtsr.gui.cluster.top.total"),
                    NumberFormatUtil.formatNumber(Math.round(syncNumber(syncManager, ClusterGuiSync.KEY_TOTAL_ORE)))))
                .asWidget()
                .pos(TOP_THROUGHPUT_X, 19)
                .scale(0.65f));
        // 开关机：不用 ToggleButton 接 KEY_ENABLED 布尔同步值——ToggleButton 点击经 IIntValue.setIntValue
        // 写回同步值，双向生效前提是 KEY_ENABLED 开 allowC2S（并行契约未冻结该行为）；
        // ButtonWidget → actions.togglePower() 走专用 C2S 通道（与聚合器配置 GUI 定向切换按钮同范式），
        // 状态显示经 KEY_ENABLED 同步值动态文案，实现简单可靠且不依赖同步值可写
        panel.child(
            new ButtonWidget<>().pos(TOP_POWER_X, 7)
                .size(TOP_POWER_W, 18)
                .overlay(
                    IKey.dynamic(
                        () -> StatCollector.translateToLocal(
                            syncBoolean(syncManager, ClusterGuiSync.KEY_ENABLED) ? "gtsr.gui.cluster.power.on"
                                : "gtsr.gui.cluster.power.off")))
                .onMousePressed(mouseButton -> {
                    actions.togglePower();
                    return true;
                })
                .tooltipBuilder(t -> t.addLine(IKey.lang("gtsr.gui.cluster.power.tip"))));
    }

    /** 速率行：把 lang 格式串的 %s 替换为千位分隔实际读数；alert 为 true 时整行红字并加 ⚠ 前缀。 */
    private static String formatRateLine(String labelKey, double litersPerSecond, boolean alert) {
        String line = String.format(
            StatCollector.translateToLocal(labelKey),
            NumberFormatUtil.formatNumber(Math.round(litersPerSecond)));
        return alert ? EnumChatFormatting.RED + "\u26a0 " + line : EnumChatFormatting.BLACK + line;
    }

    /** 吞吐值串（千位分隔整数，嵌入 %s 键）：&gt;0 绿字，否则黑字。 */
    private static String throughputValue(double oresPerSecond) {
        long rounded = Math.round(oresPerSecond);
        String number = NumberFormatUtil.formatNumber(rounded);
        return rounded > 0 ? EnumChatFormatting.GREEN.toString() + number
            : EnumChatFormatting.BLACK.toString() + number;
    }

    /**
     * 按同步键读取数值：对并行切片注册的具体数值同步类型不敏感
     * （Double/Int/Long/Float SyncValue 均经 ValueSyncHandler.getValue() 取 Number 转 double），取不到回 0。
     */
    private static double syncNumber(PanelSyncManager syncManager, String key) {
        SyncHandler<?> handler = syncManager.findSyncHandlerNullable(key);
        if (handler instanceof ValueSyncHandler<?, ?>value && value.getValue() instanceof Number number) {
            return number.doubleValue();
        }
        return 0.0d;
    }

    /** 按同步键读取布尔（BooleanSyncValue）；取不到或非布尔回 false。 */
    private static boolean syncBoolean(PanelSyncManager syncManager, String key) {
        SyncHandler<?> handler = syncManager.findSyncHandlerNullable(key);
        return handler instanceof ValueSyncHandler<?, ?>value && Boolean.TRUE.equals(value.getValue());
    }

    private static double clamp01(double value) {
        return value < 0.0d ? 0.0d : (value > 1.0d ? 1.0d : value);
    }
}
