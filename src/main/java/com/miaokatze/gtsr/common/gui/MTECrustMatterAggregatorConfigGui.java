package com.miaokatze.gtsr.common.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.item.IItemHandler;
import com.cleanroommc.modularui.utils.item.InvWrapper;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.DoubleSyncValue;
import com.cleanroommc.modularui.value.sync.GenericListSyncHandler;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandler;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;
import com.miaokatze.gtsr.common.machine.MTECrustMatterAggregator;
import com.miaokatze.gtsr.common.machine.MTECrustMatterAggregator.OreEntryInfo;
import com.miaokatze.gtsr.main.GTSteamReborn;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.modularui2.GTGuiTextures;
import gregtech.api.util.GTUtility;

/**
 * 地壳物质聚合器「终端配置界面」（Modern UI 2）。
 * 打开方式：手持枢纽终端右击聚合器（服务端经 AggregatorConfigGuiFactory 打开）。
 * 功能：
 * - 第一行配置：矿石模式循环（原矿/粗矿/粉碎矿，含蒸汽加成显示）、时运等级循环（奇点模式门控）、
 * 当前蒸汽消耗（基础 24000 L/s × 同步倍率，加粗显示，tooltip 显示倍率明细；定向模式下明细含定向倍率）；
 * - 左侧 25 槽 5×5 网格：槽 1 = 控制器槽（mInventory[1]，与主 GUI 同一数据源），槽 2-25 = 终端插件槽，
 * 栈上限 1、槽过滤仅接受维度显示物品；网格右侧为刷新按钮（Ore Plugin 放入/移除后手动重建矿池）；
 * - 定向模式区：切换按钮（C2S 切换，服务端进入时清空过滤与定向、重建矿池、强制停机并清空奇点模式）、
 * UU 物质消耗行（定向开：1 L/s × 倍率，紫色加粗；定向关：—，灰色）、4 行模式提示；
 * 定向模式下插件槽只出不进（canPut(false)），行按钮切换「定向」而非「过滤」；
 * - 右侧矿石浏览器：搜索（按下按钮才应用，按矿石本地化显示名匹配，天然兼容中文）、
 * 种类切换（全部/未过滤/已过滤）、逐矿过滤/定向切换（"已解放权重"语义，C2S 切换）。
 *
 * 同步设计：
 * - "gtsr.cfg.oreList"：GenericListSyncHandler，服务端每 tick 检测变化并同步到客户端；
 * - "gtsr.cfg.oreMode"/"gtsr.cfg.fortune"/"gtsr.cfg.maxFortune"/"gtsr.cfg.steamMult"：
 * IntSyncValue/DoubleSyncValue，仅 S2C；
 * - "gtsr.cfg.directionalMode"/"gtsr.cfg.uuMult"：BooleanSyncValue/DoubleSyncValue，仅 S2C
 * （定向开关与 UU 倍率；定向开关变化驱动插件槽进出限制重算）；
 * - "gtsr.cfg.aggregatorAction"：单个面板级 C2S 动作处理器，按钮点击发往服务端执行；
 * - 矿石列表为「常驻 ListWidget 实例 + 手动刷新行」（refreshOreList）：过滤/搜索/排序切换仅重建
 * 行内容，滚动位置随列表实例持续，不会把滚动条拉回顶部（弃 DynamicSyncedWidget 全量重建方案）。
 */
public class MTECrustMatterAggregatorConfigGui implements IGuiHolder<PosGuiData> {

    // 面板 420×350：GUI 缩放 3x 下 1260×1050，1080p 窗口内完整显示（含底部玩家背包，不被窗口底边截断）
    private static final int PANEL_WIDTH = 420;
    private static final int PANEL_HEIGHT = 350;
    // 左列（维度槽）：标题 + 25 槽 5×5 网格 + 刷新按钮 + 定向模式区，全部绝对定位。
    // 注意：槽列不用 Flow 容器（实机验证 Flow 作为面板 child 时 pos 失效被居中），
    // 25 个 ItemSlot 各自作为面板直接 child 显式 pos 定位。
    private static final int LEFT_X = 8;
    private static final int SLOT_TITLE_Y = 44;
    private static final int SLOT_COL_Y = 56;
    private static final int SLOT_SIZE = 16; // 槽格 16px；网格宽 5×17-1 = 84px（x 8-92），5 行 → y 56-140
    private static final int SLOT_GRID_COLS = 5;
    private static final int SLOT_GAP = 1; // 槽格间距
    private static final int REFRESH_X = LEFT_X + SLOT_GRID_COLS * (SLOT_SIZE + SLOT_GAP) + 2; // 95，槽区右侧
    private static final int REFRESH_Y = SLOT_COL_Y + 2 * (SLOT_SIZE + SLOT_GAP); // 90，槽区垂直居中
    private static final int DIRECTIONAL_Y = 140; // 定向模式切换按钮行
    private static final int UU_LINE_Y = 162; // UU 物质消耗行
    private static final int HINT_Y = 184; // 定向模式提示信息起始行
    // 右侧矿石浏览器区（绝对坐标，全部直接挂面板，避免嵌套容器定位歧义）
    private static final int BROWSER_X = 124;
    private static final int BROWSER_Y = 58; // 标题行（44-58）下方，不遮挡「矿石浏览器」标题
    private static final int BROWSER_W = 288;
    private static final int BROWSER_H = 208;
    private static final int SEARCH_Y = 62;
    private static final int HEADER_Y = 82;
    private static final int LIST_X = BROWSER_X + 4;
    private static final int LIST_Y = 100;
    private static final int LIST_W = BROWSER_W - 8;
    private static final int LIST_H = 156;
    // 表格列宽（标签栏与列表行共用，保证逐列对齐）
    private static final int COL_ICON = 16;
    private static final int COL_NAME = 100;
    private static final int COL_WEIGHT = 44;
    private static final int COL_DIM = 56;
    private static final int COL_ACTION = 44;
    // 搜索行控件位置（搜索框靠左，搜索/种类按钮组靠最右）
    private static final int SEARCH_FIELD_X = BROWSER_X + 4;
    private static final int SEARCH_FIELD_W = 124;
    private static final int CATEGORY_BTN_W = 88;
    private static final int SEARCH_BTN_W = 44;
    private static final int SEARCH_BTN_X = BROWSER_X + BROWSER_W - 4 - CATEGORY_BTN_W - 4 - SEARCH_BTN_W;
    private static final int CATEGORY_BTN_X = BROWSER_X + BROWSER_W - 4 - CATEGORY_BTN_W;
    // 插件槽数量（槽 2-25）
    private static final int PLUGIN_SLOT_COUNT = 24;
    private static final String[] ORE_MODE_NAMES = { "raw", "crushed", "purified" };

    // 列表滚动偏移保存：服务端数据变化触发整表重建时，旧列表 dispose 写回、新列表首次布局恢复，
    // 避免服务器频繁同步把拖动中的滚动条拉回顶部（仅客户端有实际意义，服务端恒为 0）

    // 客户端搜索/种类过滤状态（不参与同步，仅作用于本地列表构建）
    private final StringValue searchValue = new StringValue("");
    private String searchText = "";
    private int categoryMode = 0;

    // 矿石列表为「常驻 ListWidget 实例 + 手动刷新行」：滚动位置随实例持续，
    // 过滤/搜索/排序切换时仅重建行内容，滚动条位置保持不变（避免全量重建导致跳顶）。
    private GenericListSyncHandler<OreEntryInfo> oreListSync;
    private AggregatorActionSyncHandler actionSync;
    private ListWidget<IWidget, ?> oreListWidget;
    // 定向模式同步值（S2C）：行按钮定向化、插件槽进出限制、蒸汽明细切换均依赖它
    private BooleanSyncValue directionalSync;

    private final MTECrustMatterAggregator aggregator;

    public MTECrustMatterAggregatorConfigGui(MTECrustMatterAggregator aggregator) {
        this.aggregator = aggregator;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ModularScreen createScreen(PosGuiData data, ModularPanel mainPanel) {
        return new ModularScreen(GTSteamReborn.MODID, mainPanel);
    }

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        // 矿石浏览器数据同步（S2C）：数据源为聚合器 getOreEntries()（按 GTUtility.ItemId 跨维合并）
        GenericListSyncHandler<OreEntryInfo> oreListSync = new GenericListSyncHandler<>(
            aggregator::getOreEntries,
            null,
            MTECrustMatterAggregatorConfigGui::readOreInfo,
            MTECrustMatterAggregatorConfigGui::writeOreInfo,
            MTECrustMatterAggregatorConfigGui::oreInfoEqual,
            null);
        // 配置值同步（S2C）
        IntSyncValue oreModeSync = new IntSyncValue(() -> aggregator.mOreMode);
        IntSyncValue fortuneSync = new IntSyncValue(() -> aggregator.mFortuneLevel);
        IntSyncValue maxFortuneSync = new IntSyncValue(aggregator::getMaxAllowedFortuneLevel);
        DoubleSyncValue steamMultSync = new DoubleSyncValue(aggregator::getSteamMultiplier);
        // 定向模式同步（S2C）：开关 + UU 倍率（定向关闭时 UU 倍率为 0）
        BooleanSyncValue directionalSync = new BooleanSyncValue(() -> aggregator.getDirectionalMode());
        DoubleSyncValue uuMultSync = new DoubleSyncValue(aggregator::getUUMultiplier);
        this.directionalSync = directionalSync;
        // 按钮动作同步（C2S）：所有行按钮共用同一个处理器
        this.actionSync = new AggregatorActionSyncHandler(aggregator);
        this.oreListSync = oreListSync;
        // 数据变化（服务端推送/过滤切换）→ 刷新行内容；列表实例常驻，滚动位置保持不变
        oreListSync.setChangeListener(() -> refreshOreList());

        syncManager.syncValue("gtsr.cfg.oreList", oreListSync);
        syncManager.syncValue("gtsr.cfg.oreMode", oreModeSync);
        syncManager.syncValue("gtsr.cfg.fortune", fortuneSync);
        syncManager.syncValue("gtsr.cfg.maxFortune", maxFortuneSync);
        syncManager.syncValue("gtsr.cfg.steamMult", steamMultSync);
        syncManager.syncValue("gtsr.cfg.directionalMode", directionalSync);
        syncManager.syncValue("gtsr.cfg.uuMult", uuMultSync);
        syncManager.syncValue("gtsr.cfg.aggregatorAction", this.actionSync);

        // 矿石列表：常驻 ListWidget 实例（滚动位置随实例持续；行内容更新不重置滚动条）
        ListWidget<IWidget, ?> oreListWidget = new ListWidget<>();
        this.oreListWidget = oreListWidget;
        oreListWidget.pos(LIST_X, LIST_Y)
            .size(LIST_W, LIST_H);

        ModularPanel panel = ModularPanel.defaultPanel("aggregator_config", PANEL_WIDTH, PANEL_HEIGHT)
            .background(GTGuiTextures.BACKGROUND_STANDARD)
            .child(ButtonWidget.panelCloseButton())
            .child(
                IKey.lang("gtsr.aggregator_config.title")
                    .asWidget()
                    .pos(8, 6))
            .child(
                buildConfigRow(
                    oreModeSync,
                    fortuneSync,
                    maxFortuneSync,
                    steamMultSync,
                    directionalSync,
                    uuMultSync,
                    this.actionSync))
            // 左列：维度槽标题 + 25 槽 5×5 + 刷新按钮（全部绝对定位，槽列固定最左）
            .child(
                IKey.lang("gtsr.aggregator_config.slots_title")
                    .asWidget()
                    .pos(LEFT_X, SLOT_TITLE_Y))
            // 右侧矿池栏：标题 + 背景框 + 搜索行 + 表格标签栏 + 矿石列表（全部面板直接绝对定位，
            // 不嵌套容器子元素，避免 MUI2 嵌套定位歧义导致的错位/重叠）
            .child(
                IKey.lang("gtsr.aggregator_config.browser_title")
                    .asWidget()
                    .pos(BROWSER_X, SLOT_TITLE_Y))
            .child(buildBrowserBackground())
            .child(buildSearchField())
            .child(buildSearchButton())
            .child(buildCategoryButton())
            .child(buildTableHeader())
            .child(oreListWidget)
            // 玩家背包（与枢纽状态面板的关键区别：本界面是物品操作界面，需要背包）
            .child(SlotGroupWidget.playerInventory(true));

        // 25 个槽逐个作为面板直接 child 显式定位（不用 Flow 容器——实机验证 Flow 作为面板
        // child 时 pos 失效被居中布局，导致槽列卡在面板正中间）
        ModularSlot controllerSlot = new ModularSlot(aggregator.inventoryHandler, aggregator.getControllerSlotIndex()) {

            @Override
            public int getSlotStackLimit() {
                return 1;
            }
        };
        panel.child(buildPluginSlot(controllerSlot, true, 0));
        IItemHandler pluginHandler = new InvWrapper(aggregator.getPluginSlotInventory());
        // 插件槽引用留存：定向模式下只出不进（ModularSlot.canPut(false)），由 directionalSync 变化监听驱动
        List<ModularSlot> pluginSlots = new ArrayList<>(PLUGIN_SLOT_COUNT);
        for (int i = 0; i < PLUGIN_SLOT_COUNT; i++) {
            ModularSlot pluginSlot = new ModularSlot(pluginHandler, i);
            pluginSlots.add(pluginSlot);
            panel.child(buildPluginSlot(pluginSlot, false, i + 1));
        }
        // 定向模式只出不进：插件槽在定向模式下只可拿走不可放入（服务端同样拒绝放入，此处为客户端交互限制；
        // controllerSlot 槽 1 不受限）。同步值首包到达即触发监听，配合下方立即应用保证状态正确。
        directionalSync.setChangeListener(() -> {
            boolean directional = directionalSync.getValue();
            for (ModularSlot s : pluginSlots) {
                s.canPut(!directional);
            }
        });
        directionalSync.getChangeListener()
            .run();
        // 刷新按钮：Ore Plugin 放入/移除槽后矿池不会立刻重建，点击手动刷新（C2S 服务端立即重建矿池）
        panel.child(buildRefreshButton(actionSync));
        // 定向模式切换按钮：C2S 切换；服务端进入时清空过滤/定向、重建矿池、强制停机并清空奇点模式
        panel.child(buildDirectionalButton(directionalSync, actionSync));
        // UU 物质消耗行：定向开 → 1 L/s × 倍率（紫色加粗）；定向关 → —（灰色）
        panel.child(
            IKey.dynamic(() -> formatUUCostLine(uuMultSync.getDoubleValue(), directionalSync.getValue()))
                .asWidget()
                .pos(LEFT_X, UU_LINE_Y));
        // 定向模式提示信息（4 行，自 HINT_Y 起每行 12px，白/灰交替）
        panel.child(
            IKey.str(
                EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.aggregator_config.directional.hint1"))
                .asWidget()
                .pos(LEFT_X, HINT_Y));
        panel.child(
            IKey.str(
                EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.aggregator_config.directional.hint2"))
                .asWidget()
                .pos(LEFT_X, HINT_Y + 12));
        panel.child(
            IKey.str(
                EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.aggregator_config.directional.hint3"))
                .asWidget()
                .pos(LEFT_X, HINT_Y + 24));
        panel.child(
            IKey.str(
                EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.aggregator_config.directional.hint4"))
                .asWidget()
                .pos(LEFT_X, HINT_Y + 36));

        // 初始刷新（sync handler 尚未初始化时数据为空，首次同步到达后 changeListener 再刷新）
        refreshOreList();
        return panel;
    }

    // —— 第一行配置按钮 / 文本 ——

    private IWidget buildConfigRow(IntSyncValue oreModeSync, IntSyncValue fortuneSync, IntSyncValue maxFortuneSync,
        DoubleSyncValue steamMultSync, BooleanSyncValue directionalSync, DoubleSyncValue uuMultSync,
        AggregatorActionSyncHandler actionSync) {
        // 矿石模式循环按钮：显示当前模式名 + 蒸汽加成（如「粉碎矿 +50%」），点击 C2S 循环
        ButtonWidget<?> oreModeButton = new ButtonWidget<>().size(104, 18)
            .overlay(IKey.dynamic(() -> formatOreModeLabel(oreModeSync.getIntValue())))
            .onMousePressed(mouseButton -> {
                actionSync.sendCycleOreMode();
                return true;
            })
            .tooltipBuilder(t -> t.addLine(IKey.lang("gtsr.aggregator_config.ore_mode.tip")));

        // 时运循环按钮：原矿模式或已达上限时禁用（上限 = 同步的 maxAllowedFortune），点击 C2S 循环
        ButtonWidget<?> fortuneButton = new ButtonWidget<>().size(104, 18)
            .overlay(IKey.dynamic(() -> formatFortuneLabel(fortuneSync.getIntValue())))
            .onMousePressed(mouseButton -> {
                actionSync.sendCycleFortune();
                return true;
            })
            .tooltipBuilder(t -> {
                t.addLine(IKey.lang("gtsr.aggregator_config.fortune.tip"));
                t.addLine(IKey.lang("gtsr.aggregator_config.fortune.gate"));
            })
            .onUpdateListener(button -> {
                // 仅粗矿/粉碎矿模式可按下；时运循环在 0..上限内回绕（服务端 cycleFortuneLevel 钳位），
                // 故已达上限时仍可按下（按下会回到 0），不能禁用
                boolean rawMode = oreModeSync.getIntValue() == 0;
                button.setEnabled(!rawMode);
            }, true);

        // 蒸汽消耗动态文本：基础 24000 L/s（普通档 1200 L/tick）× 同步的蒸汽倍率，tooltip 显示倍率明细
        IWidget steamCostText = IKey.dynamic(() -> formatSteamCostLine(steamMultSync.getDoubleValue()))
            .asWidget()
            .scale(0.9f)
            .tooltipBuilder(t -> {
                t.addLine(IKey.lang("gtsr.aggregator_config.steam_cost.tip"));
                t.addLine(
                    IKey.dynamic(
                        () -> EnumChatFormatting.GRAY + "× " + String.format("%.2f", steamMultSync.getDoubleValue())));
                t.addLine(IKey.dynamic(() -> {
                    int mode = oreModeSync.getIntValue();
                    int fortune = fortuneSync.getIntValue();
                    // 明细 =（1+矿石模式加成+时运加成）×（维度槽/过滤加成）；后者由总倍率反推。
                    // 定向模式：固定 +100%（×2.00）× 定向倍率（定向倍率 = UU 倍率 ÷ 模式/时运加成反推）
                    double modeFortune = 1.0d
                        + MTECrustMatterAggregator.ORE_MODE_STEAM_BONUS[Math.min(Math.max(mode, 0), 2)]
                        + MTECrustMatterAggregator.FORTUNE_STEAM_BONUS[Math.min(Math.max(fortune, 0), 6)];
                    if (directionalSync.getValue()) {
                        double dirFactor = uuMultSync.getDoubleValue() / modeFortune;
                        return EnumChatFormatting.GRAY
                            + StatCollector.translateToLocal("gtsr.aggregator_config.steam_cost.detail_dir")
                            + String.format("%.2f", modeFortune)
                            + " × 2.00 × "
                            + String.format("%.2f", dirFactor);
                    }
                    double env = steamMultSync.getDoubleValue() / modeFortune;
                    return EnumChatFormatting.GRAY
                        + StatCollector.translateToLocal("gtsr.aggregator_config.steam_cost.detail")
                        + String.format("%.2f", modeFortune)
                        + " × "
                        + String.format("%.2f", env);
                }));
            });

        return Flow.row()
            .pos(8, 22)
            .height(18)
            .childPadding(4)
            .child(oreModeButton)
            .child(fortuneButton)
            .child(steamCostText);
    }

    /** 矿石模式按钮文案：模式名 + 蒸汽加成百分比（如「粉碎矿 +50%」）。 */
    private static String formatOreModeLabel(int mode) {
        int m = Math.min(Math.max(mode, 0), 2);
        String name = StatCollector.translateToLocal("gtsr.aggregator_config.ore_mode." + ORE_MODE_NAMES[m]);
        int bonus = (int) Math.round(MTECrustMatterAggregator.ORE_MODE_STEAM_BONUS[m] * 100.0d);
        return name + " +" + bonus + "%";
    }

    /** 时运按钮文案：「时运 Lv n」（键值含 %d 占位符）。 */
    private static String formatFortuneLabel(int level) {
        return String.format(StatCollector.translateToLocal("gtsr.aggregator_config.fortune_level"), level);
    }

    /** 蒸汽消耗主文本：基础 24000 L/s × 同步倍率（整体加粗，金色粗体附倍率后缀）。 */
    private static String formatSteamCostLine(double steamMult) {
        long perSecond = Math.round(MTECrustMatterAggregator.NORMAL_STEAM_PER_SECOND * steamMult);
        return EnumChatFormatting.BOLD + StatCollector.translateToLocal("gtsr.aggregator_config.steam_cost")
            + " "
            + NumberFormatUtil.formatNumber(perSecond)
            + " L/s "
            + EnumChatFormatting.GOLD.toString()
            + EnumChatFormatting.BOLD
            + String.format(
                StatCollector.translateToLocal("gtsr.aggregator_config.steam_cost.mult"),
                String.format("%.2f", steamMult));
    }

    /**
     * UU 物质消耗行：定向开 → 1 L/s × 倍率（紫色加粗，uu_cost 为前缀键 + uu_cost.mult 倍率后缀）；
     * 定向关 → 灰字 "—"（uu_cost.off）。注：uu_cost 键值无 %s 占位，只能拼接（与主 GUI 一致）。
     */
    private static String formatUUCostLine(double mult, boolean directional) {
        if (!directional) {
            return EnumChatFormatting.GRAY + StatCollector.translateToLocal("gtsr.aggregator_config.uu_cost")
                + StatCollector.translateToLocal("gtsr.aggregator_config.uu_cost.off");
        }
        String ratePart = NumberFormatUtil.formatNumber(Math.round(mult)) + " L/s"
            + String.format(
                StatCollector.translateToLocal("gtsr.aggregator_config.uu_cost.mult"),
                String.format("%.2f", mult));
        return EnumChatFormatting.LIGHT_PURPLE.toString() + EnumChatFormatting.BOLD
            + StatCollector.translateToLocal("gtsr.aggregator_config.uu_cost")
            + ratePart;
    }

    // —— 左侧 25 槽 5×5 网格 + 刷新按钮 + 定向模式区 ——

    /** 刷新按钮：Ore Plugin 放入/移除槽后矿池不会立刻重建，点击手动刷新（C2S 服务端立即重建矿池）。 */
    private IWidget buildRefreshButton(AggregatorActionSyncHandler actionSync) {
        return new ButtonWidget<>().pos(REFRESH_X, REFRESH_Y)
            .size(26, 16)
            .overlay(IKey.lang("gtsr.aggregator_config.refresh"))
            .onMousePressed(mouseButton -> {
                actionSync.sendRefreshPool();
                return true;
            })
            .tooltipBuilder(t -> t.addLine(IKey.lang("gtsr.aggregator_config.refresh.tip")));
    }

    /** 定向模式切换按钮：C2S 切换；文案随定向开关动态切换（on/off）。 */
    private IWidget buildDirectionalButton(BooleanSyncValue directionalSync, AggregatorActionSyncHandler actionSync) {
        return new ButtonWidget<>().pos(LEFT_X, DIRECTIONAL_Y)
            .size(90, 18)
            .overlay(
                IKey.dynamic(
                    () -> StatCollector.translateToLocal(
                        directionalSync.getValue() ? "gtsr.aggregator_config.directional.on"
                            : "gtsr.aggregator_config.directional.off")))
            .onMousePressed(mouseButton -> {
                actionSync.sendToggleDirectional();
                return true;
            })
            .tooltipBuilder(t -> t.addLine(IKey.lang("gtsr.aggregator_config.directional.tip")));
    }

    /**
     * 单个维度槽（面板直接 child 显式定位，不用 Flow 容器——实机验证 Flow 作为面板 child 时
     * pos 失效被居中布局）。5×5 网格：x = LEFT_X + (index % 5) × 17，y = SLOT_COL_Y + (index / 5) × 17；
     * index 0 = 控制器槽（左上角），1-24 = 插件槽。
     */
    private ItemSlot buildPluginSlot(ModularSlot slot, boolean isControllerSlot, int index) {
        // 槽过滤：仅接受维度显示物品（ModularSlot.filter 在 GUI 插入时校验，服务端适配器兜底拒绝）
        slot.filter(MTECrustMatterAggregator::isDimensionDisplayItem)
            .singletonSlotGroup();
        ItemSlot itemSlot = new ItemSlot().slot(slot)
            .pos(
                LEFT_X + (index % SLOT_GRID_COLS) * (SLOT_SIZE + SLOT_GAP),
                SLOT_COL_Y + (index / SLOT_GRID_COLS) * (SLOT_SIZE + SLOT_GAP))
            .size(SLOT_SIZE);
        if (isControllerSlot) {
            // 槽 1 空槽提示：说明其为控制器槽（与主 GUI 同一数据源），可放维度显示物品
            itemSlot.tooltipDynamic(t -> t.addLine(IKey.lang("gtsr.aggregator_config.slot1_hint")));
        }
        return itemSlot;
    }

    // —— 右侧矿石浏览器（背景框 / 搜索行 / 表格标签栏；全部面板直接绝对定位）——

    /** 浏览器背景框：仅装饰（DISPLAY 纹理），不含子元素，避免嵌套定位歧义。 */
    private IWidget buildBrowserBackground() {
        return new ParentWidget<>().pos(BROWSER_X, BROWSER_Y)
            .size(BROWSER_W, BROWSER_H)
            .background(GuiTextures.DISPLAY);
    }

    /** 搜索输入框：本地文本（不同步），按下搜索按钮才应用。 */
    private IWidget buildSearchField() {
        return new TextFieldWidget().pos(BROWSER_X + 4, SEARCH_Y)
            .size(SEARCH_FIELD_W, 16)
            .setMaxLength(32)
            .value(searchValue)
            .tooltipBuilder(t -> t.addLine(IKey.lang("gtsr.aggregator_config.search_hint")));
    }

    /** 搜索按钮：按下才应用搜索（对矿石本地化显示名匹配，天然兼容中文），位于按钮组左侧。 */
    private IWidget buildSearchButton() {
        return new ButtonWidget<>().pos(SEARCH_BTN_X, SEARCH_Y)
            .size(SEARCH_BTN_W, 16)
            .overlay(IKey.lang("gtsr.aggregator_config.search"))
            .onMousePressed(mouseButton -> {
                searchText = searchValue.getStringValue()
                    .trim();
                refreshOreList();
                return true;
            })
            .tooltipBuilder(t -> t.addLine(IKey.lang("gtsr.aggregator_config.search_hint")));
    }

    /**
     * 展示种类/排序切换按钮：全部 → 未过滤 → 已过滤 → 权重升序 → 权重降序 → 循环
     * （贴浏览器框右缘）。
     */
    private IWidget buildCategoryButton() {
        return new ButtonWidget<>().pos(CATEGORY_BTN_X, SEARCH_Y)
            .size(CATEGORY_BTN_W, 16)
            .overlay(IKey.dynamic(() -> StatCollector.translateToLocal(categoryLangKey(categoryMode))))
            .onMousePressed(mouseButton -> {
                categoryMode = (categoryMode + 1) % 5;
                refreshOreList();
                return true;
            })
            .tooltipBuilder(t -> t.addLine(IKey.lang("gtsr.aggregator_config.category.tip")));
    }

    /** 表格标签栏：与列表行同列宽（图标/名称/权重/维度/操作），表格式对齐。 */
    private IWidget buildTableHeader() {
        Flow row = Flow.row()
            .pos(BROWSER_X + 4, HEADER_Y)
            .height(14)
            .childPadding(4);
        // 图标列无标题（空文本占位对齐）
        row.child(
            IKey.str("")
                .asWidget()
                .width(COL_ICON));
        row.child(headerLabel("gtsr.aggregator_config.col.name", COL_NAME));
        row.child(headerLabel("gtsr.aggregator_config.col.weight", COL_WEIGHT));
        row.child(headerLabel("gtsr.aggregator_config.col.dim", COL_DIM));
        row.child(headerLabel("gtsr.aggregator_config.col.action", COL_ACTION));
        return row;
    }

    /** 标签栏单元格：橙色加粗小字，固定列宽（矿石浏览器内文字统一提亮）。 */
    private static IWidget headerLabel(String key, int width) {
        return IKey
            .str(EnumChatFormatting.GOLD.toString() + EnumChatFormatting.BOLD + StatCollector.translateToLocal(key))
            .asWidget()
            .width(width)
            .scale(0.8f);
    }

    /**
     * 刷新矿石列表行内容（客户端本地执行；服务端同样调用但无渲染影响）。
     * 列表实例常驻（滚动位置随实例持续），此处仅 removeAll + 重建行：
     * 过滤/搜索/排序切换不会把滚动条拉回顶部。
     */
    private void refreshOreList() {
        if (oreListWidget == null || oreListSync == null) return;
        List<OreEntryInfo> all = oreListSync.getValue();
        // 客户端过滤：种类（全部/未过滤/已过滤）+ 搜索词（矿石本地化显示名，兼容中文）
        List<OreEntryInfo> visible = new ArrayList<>();
        if (all != null) {
            for (OreEntryInfo info : all) {
                if (info.ore == null) continue;
                if (categoryMode == 1 && info.filtered) continue;
                if (categoryMode == 2 && !info.filtered) continue;
                if (!searchText.isEmpty() && !info.ore.getDisplayName()
                    .toLowerCase()
                    .contains(searchText.toLowerCase())) {
                    continue;
                }
                visible.add(info);
            }
        }
        // 权重排序（种类 3=权重升序 / 4=权重降序）
        if (categoryMode == 3) {
            visible.sort(Comparator.comparingDouble(o -> o.weight));
        } else if (categoryMode == 4) {
            visible.sort(
                Comparator.comparingDouble((OreEntryInfo o) -> o.weight)
                    .reversed());
        }
        oreListWidget.removeAll();
        if (visible.isEmpty()) {
            oreListWidget.child(
                IKey.str(EnumChatFormatting.WHITE + StatCollector.translateToLocal("gtsr.aggregator_config.empty"))
                    .asWidget());
            return;
        }
        for (OreEntryInfo info : visible) {
            oreListWidget.child(buildOreRow(info));
        }
    }

    /** 构建单个矿石行：图标 + 本地化名 + 权重 + 出现维度 + 过滤/定向开关按钮，列宽与标签栏逐列对齐。 */
    private IWidget buildOreRow(OreEntryInfo info) {
        // 矿石图标（16px）+ 本地化显示名（客户端 ItemStack.getDisplayName()，兼容中文）
        IWidget icon = new ItemDrawable(info.ore).asWidget()
            .size(16);
        // 权重：整数或一位小数；出现维度缩写（如 "Ow+Ne"）；行文字统一提亮（白/橙加粗，深色背景可读）
        String nameText = EnumChatFormatting.WHITE.toString() + EnumChatFormatting.BOLD + info.ore.getDisplayName();
        String weightText = EnumChatFormatting.GOLD.toString() + EnumChatFormatting.BOLD + formatWeight(info.weight);
        String dimText = EnumChatFormatting.GOLD.toString() + EnumChatFormatting.BOLD + String.join("+", info.dimAbbrs);

        // 过滤/定向开关按钮：定向模式下切换「定向」（aimed，服务端矿池只产出定向矿石），
        // 否则切换「过滤」（filtered）；按下 C2S 切换
        ButtonWidget<?> filterButton = new ButtonWidget<>().size(COL_ACTION, 16)
            .overlay(
                IKey.dynamic(
                    () -> directionalSync.getValue()
                        ? StatCollector.translateToLocal(
                            info.aimed ? "gtsr.aggregator_config.directional_ore.off"
                                : "gtsr.aggregator_config.directional_ore")
                        : StatCollector.translateToLocal(
                            info.filtered ? "gtsr.aggregator_config.unfilter" : "gtsr.aggregator_config.filter")))
            .onMousePressed(mouseButton -> {
                if (directionalSync.getValue()) {
                    actionSync.sendToggleDirectionalOre(info);
                } else {
                    actionSync.sendToggleFilter(info);
                }
                return true;
            })
            .tooltipBuilder(t -> {
                if (directionalSync.getValue()) {
                    t.addLine(IKey.lang("gtsr.aggregator_config.directional_ore_hint"));
                } else if (info.filtered) {
                    t.addLine(IKey.lang("gtsr.aggregator_config.unfilter"));
                    t.addLine(IKey.lang("gtsr.aggregator_config.filtered_hint"));
                } else {
                    t.addLine(IKey.lang("gtsr.aggregator_config.filter"));
                }
            });

        // 行布局：列宽与 buildTableHeader 标签栏一致（16/100/44/56/44），等距排列，按钮靠最右
        return Flow.row()
            .widthRel(1f)
            .height(18)
            .childPadding(4)
            .crossAxisAlignment(Alignment.CrossAxis.CENTER)
            .child(icon)
            .child(
                IKey.str(nameText)
                    .asWidget()
                    .width(COL_NAME)
                    .scale(0.9f))
            .child(
                IKey.str(weightText)
                    .asWidget()
                    .width(COL_WEIGHT)
                    .scale(0.9f))
            .child(
                IKey.str(dimText)
                    .asWidget()
                    .width(COL_DIM)
                    .scale(0.9f))
            .child(filterButton);
    }

    /** 权重显示：整数输出整数，否则一位小数。 */
    private static String formatWeight(float weight) {
        if (!Float.isInfinite(weight) && weight == Math.floor(weight)) {
            return String.format("%.0f", weight);
        }
        return String.format("%.1f", weight);
    }

    /** 种类/排序 5 态：全部 / 未过滤 / 已过滤 / 权重升序 / 权重降序。 */
    private static String categoryLangKey(int mode) {
        return switch (mode) {
            case 1 -> "gtsr.aggregator_config.category.unfiltered";
            case 2 -> "gtsr.aggregator_config.category.filtered";
            case 3 -> "gtsr.aggregator_config.category.asc";
            case 4 -> "gtsr.aggregator_config.category.desc";
            default -> "gtsr.aggregator_config.category.all";
        };
    }

    // —— OreEntryInfo 序列化（读写顺序须严格一致，参考 MTESingularityHubStatusGui.HubNodeInfo）——

    private static OreEntryInfo readOreInfo(PacketBuffer buf) {
        ItemStack ore = ByteBufUtils.readItemStack(buf);
        float weight = buf.readFloat();
        int dimCount = buf.readInt();
        List<String> dimAbbrs = new ArrayList<>(dimCount);
        for (int i = 0; i < dimCount; i++) {
            dimAbbrs.add(ByteBufUtils.readUTF8String(buf));
        }
        boolean filtered = buf.readBoolean();
        boolean aimed = buf.readBoolean();
        return new OreEntryInfo(ore, weight, dimAbbrs, filtered, aimed);
    }

    private static void writeOreInfo(PacketBuffer buf, OreEntryInfo info) {
        // 读写顺序须严格一致：物品 → weight(float) → 维度数量 → 各维度缩写(UTF8) → filtered → aimed
        ByteBufUtils.writeItemStack(buf, info.ore);
        buf.writeFloat(info.weight);
        buf.writeInt(info.dimAbbrs.size());
        for (String abbr : info.dimAbbrs) {
            ByteBufUtils.writeUTF8String(buf, abbr);
        }
        buf.writeBoolean(info.filtered);
        buf.writeBoolean(info.aimed);
    }

    private static boolean oreInfoEqual(OreEntryInfo a, OreEntryInfo b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.weight != b.weight) return false;
        if (a.filtered != b.filtered) return false;
        if (a.aimed != b.aimed) return false;
        if (!a.dimAbbrs.equals(b.dimAbbrs)) return false;
        if (a.ore == null || b.ore == null) return a.ore == b.ore;
        // 同一矿石（物品 + meta + NBT）视为相等
        return GTUtility.ItemId.create(a.ore)
            .equals(GTUtility.ItemId.create(b.ore));
    }

    /**
     * 面板级 C2S 动作处理器：客户端按钮点击 → 携带动作发往服务端执行。
     * 执行后无需额外刷新：getOreEntries 变化监听自动触发列表重建，配置值同步自动更新按钮文案。
     */
    public static class AggregatorActionSyncHandler extends SyncHandler<AggregatorActionSyncHandler> {

        private static final int ACTION_CYCLE_ORE_MODE = 1;
        private static final int ACTION_CYCLE_FORTUNE = 2;
        private static final int ACTION_TOGGLE_FILTER = 3;
        private static final int ACTION_REFRESH_POOL = 4;
        private static final int ACTION_TOGGLE_DIRECTIONAL = 5;
        private static final int ACTION_TOGGLE_DIRECTIONAL_ORE = 6;

        private final MTECrustMatterAggregator aggregator;

        public AggregatorActionSyncHandler(MTECrustMatterAggregator aggregator) {
            this.aggregator = aggregator;
            allowC2S();
        }

        // ===== 客户端调用：发送动作到服务端 =====

        public void sendCycleOreMode() {
            syncToServer(ACTION_CYCLE_ORE_MODE, buf -> {});
        }

        public void sendCycleFortune() {
            syncToServer(ACTION_CYCLE_FORTUNE, buf -> {});
        }

        /** 携带矿石注册名（"modid:name"）+ meta 到服务端，由服务端解析后切换过滤状态。 */
        public void sendToggleFilter(OreEntryInfo info) {
            syncToServer(ACTION_TOGGLE_FILTER, buf -> {
                GameRegistry.UniqueIdentifier uid = GameRegistry.findUniqueIdentifierFor(info.ore.getItem());
                ByteBufUtils.writeUTF8String(buf, uid == null ? "" : (uid.modId + ":" + uid.name));
                buf.writeInt(info.ore.getItemDamage());
            });
        }

        /** 手动刷新矿池：服务端立即重建（forceRefreshPool），列表数据变化自动推送。 */
        public void sendRefreshPool() {
            syncToServer(ACTION_REFRESH_POOL, buf -> {});
        }

        /** 切换定向模式（无 payload；服务端取机器附近玩家做 chat 反馈）。 */
        public void sendToggleDirectional() {
            syncToServer(ACTION_TOGGLE_DIRECTIONAL, buf -> {});
        }

        /** 携带矿石注册名（"modid:name"）+ meta 到服务端，定向模式下切换该矿石的定向瞄准状态。 */
        public void sendToggleDirectionalOre(OreEntryInfo info) {
            syncToServer(ACTION_TOGGLE_DIRECTIONAL_ORE, buf -> {
                GameRegistry.UniqueIdentifier uid = GameRegistry.findUniqueIdentifierFor(info.ore.getItem());
                ByteBufUtils.writeUTF8String(buf, uid == null ? "" : (uid.modId + ":" + uid.name));
                buf.writeInt(info.ore.getItemDamage());
            });
        }

        // ===== 服务端执行 =====

        @Override
        public void readOnClient(int id, PacketBuffer buf) throws IOException {}

        @Override
        public void readOnServer(int id, PacketBuffer buf) throws IOException {
            switch (id) {
                case ACTION_CYCLE_ORE_MODE:
                    aggregator.cycleOreMode();
                    break;
                case ACTION_CYCLE_FORTUNE:
                    aggregator.cycleFortuneLevel();
                    break;
                case ACTION_TOGGLE_FILTER: {
                    // 读写顺序须严格一致：先 UTF8 注册名后 int meta（见 sendToggleFilter）
                    String name = ByteBufUtils.readUTF8String(buf);
                    int meta = buf.readInt();
                    if (name == null || name.isEmpty()) return;
                    String[] parts = name.split(":", 2);
                    if (parts.length != 2) return;
                    Item item = GameRegistry.findItem(parts[0], parts[1]);
                    if (item == null) return;
                    GTUtility.ItemId oreId = GTUtility.ItemId.createNoCopy(item, meta, null);
                    aggregator.setOreFiltered(oreId, !aggregator.isOreFiltered(oreId));
                    break;
                }
                case ACTION_REFRESH_POOL:
                    aggregator.forceRefreshPool();
                    break;
                case ACTION_TOGGLE_DIRECTIONAL: {
                    // 服务端取机器附近玩家用于 chat 反馈；机器 toggleDirectionalMode 未对玩家判空，
                    // 故仅在找到玩家时调用（玩家右击机器打开本界面，正常必在 16 格内）
                    EntityPlayer p = aggregator.getBaseMetaTileEntity() != null ? aggregator.getBaseMetaTileEntity()
                        .getWorld()
                        .getClosestPlayer(
                            aggregator.getBaseMetaTileEntity()
                                .getXCoord(),
                            aggregator.getBaseMetaTileEntity()
                                .getYCoord(),
                            aggregator.getBaseMetaTileEntity()
                                .getZCoord(),
                            16.0d)
                        : null;
                    if (p != null) aggregator.toggleDirectionalMode(p);
                    break;
                }
                case ACTION_TOGGLE_DIRECTIONAL_ORE: {
                    // 读写顺序须严格一致：先 UTF8 注册名后 int meta（见 sendToggleDirectionalOre）
                    String name = ByteBufUtils.readUTF8String(buf);
                    int meta = buf.readInt();
                    if (name == null || name.isEmpty()) return;
                    String[] parts = name.split(":", 2);
                    if (parts.length != 2) return;
                    Item item = GameRegistry.findItem(parts[0], parts[1]);
                    if (item == null) return;
                    GTUtility.ItemId oreId = GTUtility.ItemId.createNoCopy(item, meta, null);
                    aggregator.setOreAimed(oreId, !aggregator.isOreAimed(oreId));
                    break;
                }
                default:
                    return;
            }
            // 无需额外刷新：getOreEntries 变化监听自动触发列表重建
        }
    }
}
