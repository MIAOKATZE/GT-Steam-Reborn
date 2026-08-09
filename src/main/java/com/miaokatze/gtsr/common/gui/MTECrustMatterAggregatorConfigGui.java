package com.miaokatze.gtsr.common.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
import com.cleanroommc.modularui.value.sync.DoubleSyncValue;
import com.cleanroommc.modularui.value.sync.DynamicSyncHandler;
import com.cleanroommc.modularui.value.sync.GenericListSyncHandler;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandler;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.DynamicSyncedWidget;
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
 * 当前蒸汽消耗（基础 24000 L/s × 同步倍率，tooltip 显示倍率明细）；
 * - 左侧 12 槽一列：槽 1 = 控制器槽（mInventory[1]，与主 GUI 同一数据源），槽 2-12 = 终端插件槽，
 * 栈上限 1、槽过滤仅接受维度显示物品；
 * - 右侧矿石浏览器：搜索（按下按钮才应用，按矿石本地化显示名匹配，天然兼容中文）、
 * 种类切换（全部/未过滤/已过滤）、逐矿过滤开关（"已解放权重"语义，C2S 切换）。
 *
 * 同步设计：
 * - "gtsr.cfg.oreList"：GenericListSyncHandler，服务端每 tick 检测变化并同步到客户端；
 * - "gtsr.cfg.oreMode"/"gtsr.cfg.fortune"/"gtsr.cfg.maxFortune"/"gtsr.cfg.steamMult"：
 * IntSyncValue/DoubleSyncValue，仅 S2C；
 * - "gtsr.cfg.aggregatorAction"：单个面板级 C2S 动作处理器，按钮点击发往服务端执行；
 * - listDynamic：DynamicSyncHandler，列表数据/搜索/种类变化时重建矿石列表控件（自带滚动条）。
 * 注意坑：DynamicSyncHandler 的 widgetProvider 内只允许「查找」不允许「注册」sync handler，
 * 故 gtsr.cfg.oreList/gtsr.cfg.aggregatorAction 均在 buildUI 注册（参考 MTESingularityHubStatusGui）。
 */
public class MTECrustMatterAggregatorConfigGui implements IGuiHolder<PosGuiData> {

    // 面板 420×350：GUI 缩放 2x 下 840×700，720p 窗口内完整显示（含底部玩家背包，不被窗口底边截断）
    private static final int PANEL_WIDTH = 420;
    private static final int PANEL_HEIGHT = 350;
    // 左列（维度槽）：标题 + 12 槽 + 刷新按钮，全部绝对定位
    private static final int LEFT_X = 8;
    private static final int SLOT_TITLE_Y = 44;
    private static final int SLOT_COL_Y = 58;
    // 12 槽每格 18px 紧排 → 58-274；刷新按钮置于槽列下方
    private static final int REFRESH_Y = 278;
    // 右侧矿石浏览器区（绝对坐标，全部直接挂面板，避免嵌套容器定位歧义）
    private static final int BROWSER_X = 124;
    private static final int BROWSER_Y = 44;
    private static final int BROWSER_W = 288;
    private static final int BROWSER_H = 222;
    private static final int SEARCH_Y = 48;
    private static final int HEADER_Y = 66;
    private static final int LIST_X = BROWSER_X + 4;
    private static final int LIST_Y = 84;
    private static final int LIST_W = BROWSER_W - 8;
    private static final int LIST_H = 176;
    // 表格列宽（标签栏与列表行共用，保证逐列对齐）
    private static final int COL_ICON = 16;
    private static final int COL_NAME = 100;
    private static final int COL_WEIGHT = 44;
    private static final int COL_DIM = 56;
    private static final int COL_ACTION = 44;
    // 插件槽数量（槽 2-12）
    private static final int PLUGIN_SLOT_COUNT = 11;
    private static final String[] ORE_MODE_NAMES = { "raw", "crushed", "purified" };

    // 列表滚动偏移保存：服务端数据变化触发整表重建时，旧列表 dispose 写回、新列表首次布局恢复，
    // 避免服务器频繁同步把拖动中的滚动条拉回顶部（仅客户端有实际意义，服务端恒为 0）
    private int listScrollValue;

    // 客户端搜索/种类过滤状态（不参与同步，仅作用于本地列表构建）
    private final StringValue searchValue = new StringValue("");
    private String searchText = "";
    private int categoryMode = 0;

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
        // 按钮动作同步（C2S）：所有行按钮共用同一个处理器
        AggregatorActionSyncHandler actionSync = new AggregatorActionSyncHandler(aggregator);

        // 动态列表控件：数据变化时重建，widgetProvider 在双端执行
        DynamicSyncHandler listDynamic = new DynamicSyncHandler()
            .widgetProvider((pSyncManager, buf) -> buildOreListWidget(pSyncManager))
            .allowC2S();
        final List<OreEntryInfo>[] lastLayout = new List[] { null };
        oreListSync.setChangeListener(() -> {
            List<OreEntryInfo> current = (List<OreEntryInfo>) oreListSync.getValue();
            if (!sameOreLayout(lastLayout[0], current)) {
                lastLayout[0] = current == null ? null : new ArrayList<>(current);
                listDynamic.notifyUpdate(buf -> {});
            }
        });

        syncManager.syncValue("gtsr.cfg.oreList", oreListSync);
        syncManager.syncValue("gtsr.cfg.oreMode", oreModeSync);
        syncManager.syncValue("gtsr.cfg.fortune", fortuneSync);
        syncManager.syncValue("gtsr.cfg.maxFortune", maxFortuneSync);
        syncManager.syncValue("gtsr.cfg.steamMult", steamMultSync);
        syncManager.syncValue("gtsr.cfg.aggregatorAction", actionSync);

        DynamicSyncedWidget<?> listArea = new DynamicSyncedWidget<>().pos(LIST_X, LIST_Y)
            .size(LIST_W, LIST_H)
            .syncHandler(listDynamic);

        ModularPanel panel = ModularPanel.defaultPanel("aggregator_config", PANEL_WIDTH, PANEL_HEIGHT)
            .background(GTGuiTextures.BACKGROUND_STANDARD)
            .child(ButtonWidget.panelCloseButton())
            .child(
                IKey.lang("gtsr.aggregator_config.title")
                    .asWidget()
                    .pos(8, 6))
            .child(buildConfigRow(oreModeSync, fortuneSync, maxFortuneSync, steamMultSync, actionSync))
            // 左列：维度槽标题 + 12 槽 + 刷新按钮（全部绝对定位，槽列固定最左）
            .child(
                IKey.lang("gtsr.aggregator_config.slots_title")
                    .asWidget()
                    .pos(LEFT_X, SLOT_TITLE_Y))
            .child(buildSlotColumn())
            .child(buildRefreshButton(actionSync))
            // 右侧矿池栏：标题 + 背景框 + 搜索行 + 表格标签栏 + 矿石列表（全部面板直接绝对定位，
            // 不嵌套容器子元素，避免 MUI2 嵌套定位歧义导致的错位/重叠）
            .child(
                IKey.lang("gtsr.aggregator_config.browser_title")
                    .asWidget()
                    .pos(BROWSER_X, SLOT_TITLE_Y))
            .child(buildBrowserBackground())
            .child(buildSearchField())
            .child(buildSearchButton(listDynamic))
            .child(buildCategoryButton(listDynamic))
            .child(buildTableHeader())
            .child(listArea)
            // 玩家背包（与枢纽状态面板的关键区别：本界面是物品操作界面，需要背包）
            .child(SlotGroupWidget.playerInventory(true));

        // 初始内容（sync handler 尚未初始化时会缓存，初始化后立即构建）
        listDynamic.notifyUpdate(buf -> {});
        return panel;
    }

    // —— 第一行配置按钮 / 文本 ——

    private IWidget buildConfigRow(IntSyncValue oreModeSync, IntSyncValue fortuneSync, IntSyncValue maxFortuneSync,
        DoubleSyncValue steamMultSync, AggregatorActionSyncHandler actionSync) {
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
                    // 明细 =（1+矿石模式加成+时运加成）×（维度槽/过滤加成）；后者由总倍率反推
                    double modeFortune = 1.0d
                        + MTECrustMatterAggregator.ORE_MODE_STEAM_BONUS[Math.min(Math.max(mode, 0), 2)]
                        + MTECrustMatterAggregator.FORTUNE_STEAM_BONUS[Math.min(Math.max(fortune, 0), 6)];
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

    /** 蒸汽消耗主文本：基础 24000 L/s × 同步倍率。 */
    private static String formatSteamCostLine(double steamMult) {
        long perSecond = Math.round(MTECrustMatterAggregator.NORMAL_STEAM_PER_SECOND * steamMult);
        return StatCollector.translateToLocal("gtsr.aggregator_config.steam_cost") + " "
            + NumberFormatUtil.formatNumber(perSecond)
            + " L/s";
    }

    // —— 左侧 12 槽一列 + 刷新按钮 ——

    private IWidget buildSlotColumn() {
        // 槽 1：控制器槽（mInventory[1]，与主 GUI 同一数据源 inventoryHandler，栈上限 1）
        ModularSlot controllerSlot = new ModularSlot(aggregator.inventoryHandler, aggregator.getControllerSlotIndex()) {

            @Override
            public int getSlotStackLimit() {
                return 1;
            }
        };
        // 槽 2-12：终端插件槽（容量 11、栈上限 1、仅接受维度显示物品）
        IItemHandler pluginHandler = new InvWrapper(aggregator.getPluginSlotInventory());
        Flow column = Flow.column()
            .pos(LEFT_X, SLOT_COL_Y)
            .childPadding(0)
            .child(buildPluginSlot(controllerSlot, true));
        for (int i = 0; i < PLUGIN_SLOT_COUNT; i++) {
            column.child(buildPluginSlot(new ModularSlot(pluginHandler, i), false));
        }
        return column;
    }

    /** 刷新按钮：Ore Plugin 放入/移除槽后矿池不会立刻重建，点击手动刷新（C2S 服务端立即重建矿池）。 */
    private IWidget buildRefreshButton(AggregatorActionSyncHandler actionSync) {
        return new ButtonWidget<>().pos(LEFT_X, REFRESH_Y)
            .size(48, 16)
            .overlay(IKey.lang("gtsr.aggregator_config.refresh"))
            .onMousePressed(mouseButton -> {
                actionSync.sendRefreshPool();
                return true;
            })
            .tooltipBuilder(t -> t.addLine(IKey.lang("gtsr.aggregator_config.refresh.tip")));
    }

    private ItemSlot buildPluginSlot(ModularSlot slot, boolean isControllerSlot) {
        // 槽过滤：仅接受维度显示物品（ModularSlot.filter 在 GUI 插入时校验，服务端适配器兜底拒绝）
        slot.filter(MTECrustMatterAggregator::isDimensionDisplayItem)
            .singletonSlotGroup();
        ItemSlot itemSlot = new ItemSlot().slot(slot);
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
            .size(150, 16)
            .setMaxLength(32)
            .value(searchValue)
            .tooltipBuilder(t -> t.addLine(IKey.lang("gtsr.aggregator_config.search_hint")));
    }

    /** 搜索按钮：按下才应用搜索（对矿石本地化显示名匹配，天然兼容中文），位于按钮组左侧。 */
    private IWidget buildSearchButton(DynamicSyncHandler listDynamic) {
        return new ButtonWidget<>().pos(BROWSER_X + BROWSER_W - 4 - 76 - 4 - 48, SEARCH_Y)
            .size(48, 16)
            .overlay(IKey.lang("gtsr.aggregator_config.search"))
            .onMousePressed(mouseButton -> {
                searchText = searchValue.getStringValue()
                    .trim();
                listDynamic.notifyUpdate(buf -> {});
                return true;
            })
            .tooltipBuilder(t -> t.addLine(IKey.lang("gtsr.aggregator_config.search_hint")));
    }

    /** 展示种类切换按钮：全部 → 未过滤 → 已过滤 → 循环（贴浏览器框右缘）。 */
    private IWidget buildCategoryButton(DynamicSyncHandler listDynamic) {
        return new ButtonWidget<>().pos(BROWSER_X + BROWSER_W - 4 - 76, SEARCH_Y)
            .size(76, 16)
            .overlay(IKey.dynamic(() -> StatCollector.translateToLocal(categoryLangKey(categoryMode))))
            .onMousePressed(mouseButton -> {
                categoryMode = (categoryMode + 1) % 3;
                listDynamic.notifyUpdate(buf -> {});
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

    /** 标签栏单元格：灰色小字，固定列宽。 */
    private static IWidget headerLabel(String key, int width) {
        return IKey.str(EnumChatFormatting.GRAY + StatCollector.translateToLocal(key))
            .asWidget()
            .width(width)
            .scale(0.8f);
    }

    /**
     * 构建矿石列表控件（DynamicSyncHandler 的 widgetProvider，双端执行）。
     * 行内按钮的 sync handler 只允许「查找」不允许「注册」，故 oreList/aggregatorAction 均在 buildUI 注册。
     */
    @SuppressWarnings("unchecked")
    private IWidget buildOreListWidget(PanelSyncManager pSyncManager) {
        GenericListSyncHandler<OreEntryInfo> listSync = pSyncManager
            .findSyncHandler("gtsr.cfg.oreList", GenericListSyncHandler.class);
        AggregatorActionSyncHandler actionSync = pSyncManager
            .findSyncHandler("gtsr.cfg.aggregatorAction", AggregatorActionSyncHandler.class);
        List<OreEntryInfo> all = listSync != null ? (List<OreEntryInfo>) listSync.getValue() : Collections.emptyList();

        // 客户端过滤：种类（全部/未过滤/已过滤）+ 搜索词（矿石本地化显示名）
        List<OreEntryInfo> visible = new ArrayList<>();
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

        ListWidget<IWidget, ?> list = new ScrollKeepingListWidget();
        list.widthRel(1f)
            .heightRel(1f);
        if (visible.isEmpty()) {
            list.child(
                IKey.lang("gtsr.aggregator_config.empty")
                    .asWidget());
            return list;
        }
        for (OreEntryInfo info : visible) {
            list.child(buildOreRow(info, actionSync));
        }
        return list;
    }

    /**
     * 重建时保持滚动位置的矿石列表（参考 MTESingularityHubStatusGui.ScrollKeepingListWidget）：
     * dispose 时把当前滚动偏移写回 listScrollValue，首次 postResize 时恢复——
     * scrollTo 内部 clamp 会自动钳位条目减少导致的超界偏移，无需手动处理。
     */
    private class ScrollKeepingListWidget extends ListWidget<IWidget, ScrollKeepingListWidget> {

        private boolean shouldScroll = true;

        @Override
        public void postResize() {
            super.postResize();
            if (shouldScroll && getScrollData() != null) {
                getScrollData().scrollTo(getScrollArea(), listScrollValue);
                shouldScroll = false;
            }
        }

        @Override
        public void dispose() {
            super.dispose();
            // 未初始化即被丢弃时 scrollData 为 null，保留旧值即可
            if (getScrollData() != null) {
                listScrollValue = getScrollData().getScroll();
            }
        }
    }

    /** 构建单个矿石行：图标 + 本地化名 + 权重 + 出现维度 + 过滤开关按钮，列宽与标签栏逐列对齐。 */
    private IWidget buildOreRow(OreEntryInfo info, AggregatorActionSyncHandler actionSync) {
        // 矿石图标（16px）+ 本地化显示名（客户端 ItemStack.getDisplayName()，兼容中文）
        IWidget icon = new ItemDrawable(info.ore).asWidget()
            .size(16);
        IKey name = IKey.str(info.ore.getDisplayName());
        // 权重：整数或一位小数；出现维度缩写（如 "Ow+Ne"）
        String weightText = formatWeight(info.weight);
        String dimText = String.join("+", info.dimAbbrs);

        // 过滤开关按钮：按下 C2S 切换；已过滤行按钮文字/样式区分并带提示
        ButtonWidget<?> filterButton = new ButtonWidget<>().size(COL_ACTION, 16)
            .overlay(
                IKey.dynamic(
                    () -> StatCollector.translateToLocal(
                        info.filtered ? "gtsr.aggregator_config.unfilter" : "gtsr.aggregator_config.filter")))
            .onMousePressed(mouseButton -> {
                actionSync.sendToggleFilter(info);
                return true;
            })
            .tooltipBuilder(t -> {
                if (info.filtered) {
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
                name.asWidget()
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

    private static String categoryLangKey(int mode) {
        return switch (mode) {
            case 1 -> "gtsr.aggregator_config.category.unfiltered";
            case 2 -> "gtsr.aggregator_config.category.filtered";
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
        return new OreEntryInfo(ore, weight, dimAbbrs, filtered);
    }

    private static void writeOreInfo(PacketBuffer buf, OreEntryInfo info) {
        // 读写顺序须严格一致：物品 → weight(float) → 维度数量 → 各维度缩写(UTF8) → filtered
        ByteBufUtils.writeItemStack(buf, info.ore);
        buf.writeFloat(info.weight);
        buf.writeInt(info.dimAbbrs.size());
        for (String abbr : info.dimAbbrs) {
            ByteBufUtils.writeUTF8String(buf, abbr);
        }
        buf.writeBoolean(info.filtered);
    }

    private static boolean oreInfoEqual(OreEntryInfo a, OreEntryInfo b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.weight != b.weight) return false;
        if (a.filtered != b.filtered) return false;
        if (!a.dimAbbrs.equals(b.dimAbbrs)) return false;
        if (a.ore == null || b.ore == null) return a.ore == b.ore;
        // 同一矿石（物品 + meta + NBT）视为相等
        return GTUtility.ItemId.create(a.ore)
            .equals(GTUtility.ItemId.create(b.ore));
    }

    /** 列表重建判定：任一显示相关字段变化即需重建（权重变化同样触发，保证文本新鲜）。 */
    private static boolean sameOreLayout(List<OreEntryInfo> a, List<OreEntryInfo> b) {
        if (a == null || b == null || a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!oreInfoEqual(a.get(i), b.get(i))) return false;
        }
        return true;
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
                default:
                    return;
            }
            // 无需额外刷新：getOreEntries 变化监听自动触发列表重建
        }
    }
}
