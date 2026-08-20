package com.miaokatze.gtsr.common.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.value.sync.DynamicSyncHandler;
import com.cleanroommc.modularui.value.sync.GenericListSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandler;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.DynamicSyncedWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.miaokatze.gtsr.main.GTSteamReborn;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.modularui2.GTGuiTextures;

/**
 * 蒸汽/蓄水枢纽阵列「缓存节点状态管理界面」（Modern UI 2）的公共基类。
 * 打开方式：手持枢纽终端右击枢纽控制器（服务端经对应 factory 打开）。
 * 功能：查看全部绑定缓存节点（图标、名字、坐标+维度、流体类型、储量/容量），
 * 远程循环传输速率、切换节点输出模式（节点→枢纽 / 枢纽→节点）、重命名节点。
 * 与钻井枢纽状态 UI 的差异：无开始/停止、无回收、无升级按钮（缓存节点不可经 UI 破坏）。
 *
 * 同步设计（与 MTESingularityHubStatusGui 相同的三段式）：
 * - "nodeList"：GenericListSyncHandler，服务端每 tick 检测变化并同步到客户端；
 * - "hubAction"：单个面板级 C2S 动作处理器，按钮点击携带节点坐标发往服务端执行；
 * - listDynamic：DynamicSyncHandler，列表数据变化时重建节点列表控件（自带滚动条）。
 *
 * 子类只需委托枢纽实例方法 + 提供标题 key 与节点图标映射（蒸汽 3 种 / 通用流体 3 种）。
 */
public abstract class MTECacheHubStatusGui implements IGuiHolder<PosGuiData> {

    private static final int PANEL_WIDTH = 320;
    private static final int PANEL_HEIGHT = 200;

    // 列表滚动偏移保存：服务端数据变化触发整表重建时，旧列表 dispose 写回、新列表首次布局恢复，
    // 避免服务器频繁同步把拖动中的滚动条拉回顶部（仅客户端有实际意义，服务端恒为 0）
    private int listScrollValue;

    // ===== 子类委托：枢纽实例方法 =====

    protected abstract NBTTagList getCacheNodeListTag();

    protected abstract void cycleNodeRate(int x, int y, int z, int dim);

    /** S4：循环节点容量上限档（缓存节点与接收仓生效；发送仓 no-op）。 */
    protected abstract void cycleNodeCap(int x, int y, int z, int dim);

    protected abstract void setNodeMode(int x, int y, int z, int dim, boolean output);

    /** 切换节点自动输出开关（向正面相邻容器推送流体，与方向模式解耦的独立开关）。 */
    protected abstract void setNodeAuto(int x, int y, int z, int dim, boolean auto);

    protected abstract void renameNode(int x, int y, int z, int dim, String name);

    protected abstract void teleportNode(EntityPlayer player, int x, int y, int z, int dim);

    // ===== 子类委托：显示差异 =====

    /** 面板标题 lang key。 */
    protected abstract String getTitleLangKey();

    /** 按 type 字符串静态映射对应缓存节点物品图标（纯客户端映射，零网络开销）；未知类型返回 null。 */
    protected abstract ItemStack getNodeIcon(String type);

    @SideOnly(Side.CLIENT)
    @Override
    public ModularScreen createScreen(PosGuiData data, ModularPanel mainPanel) {
        return new ModularScreen(GTSteamReborn.MODID, mainPanel);
    }

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        // 节点列表数据同步（S2C）：数据源为枢纽的 getCacheNodeListTag()
        GenericListSyncHandler<CacheNodeInfo> nodeListSync = new GenericListSyncHandler<>(
            () -> CacheNodeInfo.fromTagList(getCacheNodeListTag()),
            null,
            CacheNodeInfo::read,
            CacheNodeInfo::write,
            CacheNodeInfo::areEqual,
            null);

        // 按钮动作同步（C2S）：所有行按钮共用同一个处理器，按坐标定位节点
        CacheHubActionSyncHandler actionSync = new CacheHubActionSyncHandler();

        // 动态列表控件：数据变化时重建，widgetProvider 在双端执行
        DynamicSyncHandler listDynamic = new DynamicSyncHandler()
            .widgetProvider((pSyncManager, buf) -> buildNodeListWidget(pSyncManager))
            .allowC2S();
        // State changes still sync through nodeList, but only structural changes rebuild the scrollable list.
        final List<CacheNodeInfo>[] lastLayout = new List[] { null };
        nodeListSync.setChangeListener(() -> {
            List<CacheNodeInfo> current = (List<CacheNodeInfo>) nodeListSync.getValue();
            if (!CacheNodeInfo.sameLayout(lastLayout[0], current)) {
                lastLayout[0] = new ArrayList<>(current);
                listDynamic.notifyUpdate(buf -> {});
            } else {
                listDynamic.notifyUpdate(buf -> {});
            }
        });

        syncManager.syncValue("nodeList", nodeListSync);
        syncManager.syncValue("hubAction", actionSync);

        DynamicSyncedWidget<?> listArea = new DynamicSyncedWidget<>().pos(8, 22)
            .size(PANEL_WIDTH - 16, PANEL_HEIGHT - 30)
            .syncHandler(listDynamic);

        ModularPanel panel = ModularPanel.defaultPanel("cache_hub_status", PANEL_WIDTH, PANEL_HEIGHT)
            .background(GTGuiTextures.BACKGROUND_STANDARD)
            .child(ButtonWidget.panelCloseButton())
            .child(
                IKey.lang(getTitleLangKey())
                    .asWidget()
                    .pos(8, 6))
            .child(listArea);

        // 初始内容（sync handler 尚未初始化时会缓存，初始化后立即构建）
        listDynamic.notifyUpdate(buf -> {});
        return panel;
    }

    /**
     * 构建节点列表控件（DynamicSyncHandler 的 widgetProvider，双端执行）。
     * 行内按钮的 sync handler 只允许「查找」不允许「注册」，故 hubAction/nodeList 均在 buildUI 注册。
     */
    @SuppressWarnings("unchecked")
    private IWidget buildNodeListWidget(PanelSyncManager pSyncManager) {
        GenericListSyncHandler<CacheNodeInfo> listSync = pSyncManager
            .findSyncHandler("nodeList", GenericListSyncHandler.class);
        CacheHubActionSyncHandler actionSync = pSyncManager
            .findSyncHandler("hubAction", CacheHubActionSyncHandler.class);
        List<CacheNodeInfo> nodes = listSync != null ? (List<CacheNodeInfo>) listSync.getValue()
            : Collections.emptyList();

        ListWidget<IWidget, ?> list = new ScrollKeepingListWidget();
        list.widthRel(1f)
            .heightRel(1f);
        if (nodes.isEmpty()) {
            list.child(
                IKey.lang("gtsr.hub_status.empty")
                    .asWidget());
            return list;
        }
        for (CacheNodeInfo info : nodes) {
            list.child(buildNodeRow(info, actionSync, listSync));
        }
        return list;
    }

    /**
     * 重建时保持滚动位置的节点列表（参考 GT5U MTESplitterModuleGui.WorkaroundListWidget）：
     * dispose 时把当前滚动偏移写回 listScrollValue，首次 postResize 时恢复——
     * scrollTo 内部 clamp 会自动钳位条目减少导致的超界偏移，无需手动处理。
     * shouldScroll 保证仅在重建后的首次布局恢复一次，后续面板拖动等 resize 不回跳。
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

    /**
     * 构建单个缓存节点行：左侧图标 + 信息列（名字/坐标维度/流体储量/重命名行），
     * 右侧为速率/模式/自动输出三个 16×16 图标按钮横排（当前状态与说明经悬浮 tooltip 展示）。
     */
    private IWidget buildNodeRow(CacheNodeInfo info, CacheHubActionSyncHandler actionSync,
        GenericListSyncHandler<CacheNodeInfo> nodeListSync) {
        Supplier<CacheNodeInfo> currentInfo = () -> findNode(nodeListSync.getValue(), info);
        // 节点离线：坐标对应的世界/方块无法解析（type 为空串），仅展示绑定记录，禁用操作按钮
        boolean offline = info.type.isEmpty();
        // 节点名：有自定义名优先显示自定义名，否则回退节点物品默认名（图标栈的显示名，客户端自动本地化）
        ItemStack iconStack = getNodeIcon(info.type);
        String defaultName = iconStack != null ? iconStack.getDisplayName()
            : StatCollector.translateToLocal("gtsr.cache_hub_status.unknown_node");
        String nodeName = info.name.isEmpty() ? defaultName : info.name;

        String line1 = nodeName
            + (offline
                ? " " + EnumChatFormatting.RED
                    + StatCollector.translateToLocal("gtsr.cache_hub_status.offline")
                    + EnumChatFormatting.RESET
                : "");
        String line2 = EnumChatFormatting.GRAY + "("
            + info.x
            + ", "
            + info.y
            + ", "
            + info.z
            + ") DIM: "
            + info.dim
            + EnumChatFormatting.RESET;
        // 流体行：本地化流体名 + 储量/容量（K/M/G 千位递进，两位小数）
        IKey line3 = IKey.dynamic(() -> formatFluidLine(currentInfo.get() == null ? info : currentInfo.get()));

        // 速率循环按钮：进度图标，悬浮显示当前百分比与说明，点击发 C2S 循环到下一档（与芯片右击同一逻辑）
        ButtonWidget<?> rateButton = new ButtonWidget<>().size(16)
            .overlay(GTGuiTextures.OVERLAY_BUTTON_PROGRESS)
            .onMousePressed(mouseButton -> {
                CacheNodeInfo current = currentInfo.get();
                if (current != null) actionSync.sendCycleRate(current);
                return true;
            })
            .tooltipBuilder(t -> {
                CacheNodeInfo current = currentInfo.get();
                t.addLine(IKey.str((current == null ? info : current).rate + "%"))
                    .addLine(IKey.lang("gtsr.cache_hub_status.rate_tip"));
            })
            .onUpdateListener(button -> {
                CacheNodeInfo current = currentInfo.get();
                button.setEnabled(current != null && !current.type.isEmpty());
            }, true);
        rateButton.setEnabled(!offline);

        // 容量档循环按钮（S4）：罐图标，悬浮显示当前百分比与说明，点击发 C2S 循环到下一档
        // （与空手 Shift+右击同一逻辑）；发送类仓（type 以 _out 结尾）与离线节点禁用
        ButtonWidget<?> capButton = new ButtonWidget<>().size(16)
            .overlay(GTGuiTextures.OVERLAY_BUTTON_TANK_VOID_EXCESS)
            .onMousePressed(mouseButton -> {
                CacheNodeInfo current = currentInfo.get();
                if (current != null) actionSync.sendCycleCap(current);
                return true;
            })
            .tooltipBuilder(t -> {
                CacheNodeInfo current = currentInfo.get();
                t.addLine(IKey.str((current == null ? info : current).capPct + "%"))
                    .addLine(IKey.lang("gtsr.cache_hub_status.cap_tip"));
            })
            .onUpdateListener(button -> {
                CacheNodeInfo current = currentInfo.get();
                button.setEnabled(current != null && !current.type.isEmpty() && supportsCapTier(current.type));
            }, true);
        capButton.setEnabled(!offline && supportsCapTier(info.type));

        // 输出模式开关按钮：图标随状态切换（export=输出：节点→枢纽 / import=输入：枢纽→节点），点击切换
        ButtonWidget<?> modeButton = new ButtonWidget<>().size(16)
            .overlay(info.out ? GTGuiTextures.OVERLAY_BUTTON_EXPORT : GTGuiTextures.OVERLAY_BUTTON_IMPORT)
            .onMousePressed(mouseButton -> {
                CacheNodeInfo current = currentInfo.get();
                if (current != null) actionSync.sendSetMode(current);
                return true;
            })
            .tooltipBuilder(t -> {
                CacheNodeInfo current = currentInfo.get();
                boolean output = (current == null ? info : current).out;
                t.addLine(
                    IKey.lang(
                        output ? "gtsr.cache_hub_status.mode_tip_output" : "gtsr.cache_hub_status.mode_tip_input"));
            })
            .onUpdateListener(button -> {
                CacheNodeInfo current = currentInfo.get();
                if (current != null) {
                    button.overlay(
                        current.out ? GTGuiTextures.OVERLAY_BUTTON_EXPORT : GTGuiTextures.OVERLAY_BUTTON_IMPORT);
                    button.setEnabled(!current.type.isEmpty() && !current.modeLocked);
                }
            }, true);
        modeButton.setEnabled(!offline && !info.modeLocked);

        // 自动输出开关按钮：与方向模式解耦的独立开关（节点向正面相邻容器推送流体），
        // 电源图标随状态切换，悬浮显示当前开/关与说明，点击切换
        ButtonWidget<?> autoButton = new ButtonWidget<>().size(16)
            .overlay(
                info.auto ? GTGuiTextures.OVERLAY_BUTTON_POWER_SWITCH_ON
                    : GTGuiTextures.OVERLAY_BUTTON_POWER_SWITCH_OFF)
            .onMousePressed(mouseButton -> {
                CacheNodeInfo current = currentInfo.get();
                if (current != null) actionSync.sendSetAutoOutput(current);
                return true;
            })
            .tooltipBuilder(t -> {
                CacheNodeInfo current = currentInfo.get();
                boolean auto = (current == null ? info : current).auto;
                t.addLine(IKey.lang(auto ? "gtsr.cache_hub_status.auto_on" : "gtsr.cache_hub_status.auto_off"))
                    .addLine(IKey.lang("gtsr.cache_hub_status.auto_tip"));
            })
            .onUpdateListener(button -> {
                CacheNodeInfo current = currentInfo.get();
                if (current != null) {
                    button.overlay(
                        current.auto ? GTGuiTextures.OVERLAY_BUTTON_POWER_SWITCH_ON
                            : GTGuiTextures.OVERLAY_BUTTON_POWER_SWITCH_OFF);
                    button.setEnabled(!current.type.isEmpty());
                }
            }, true);
        autoButton.setEnabled(!offline);

        ButtonWidget<?> teleportButton = new ButtonWidget<>().size(16)
            .overlay(
                new ItemDrawable(com.miaokatze.gtsr.common.api.enums.GTSRItemList.SteamEntangledSingularity.get(1)))
            .onMousePressed(mouseButton -> {
                CacheNodeInfo current = currentInfo.get();
                if (current != null) actionSync.sendTeleport(current);
                return true;
            })
            .tooltipBuilder(t -> t.addLine(IKey.lang("gtsr.hub_status.teleport")));

        // 重命名文本框：纯客户端控件（StringValue 为本地值不会同步），
        // 初始文本为当前自定义名；点击确认按钮时才读取文本经 hubAction 发 C2S，服务端做裁剪
        TextFieldWidget renameField = new TextFieldWidget().width(150)
            .height(16)
            .setMaxLength(24)
            .value(new StringValue(info.name))
            .tooltipBuilder(t -> t.addLine(IKey.lang("gtsr.hub_status.rename_hint")));
        renameField.setEnabled(!offline);

        // 重命名确认按钮：读取文本框内容发送到服务端
        ButtonWidget<?> renameButton = new ButtonWidget<>().size(16)
            .overlay(GTGuiTextures.OVERLAY_BUTTON_CHECKMARK)
            .onMousePressed(mouseButton -> {
                actionSync.sendRename(info, renameField.getText());
                return true;
            })
            .tooltipBuilder(t -> t.addLine(IKey.lang("gtsr.hub_status.rename")));
        renameButton.setEnabled(!offline);

        Flow column = Flow.column()
            .width(184)
            .childPadding(1)
            .child(
                IKey.str(line1)
                    .asWidget())
            .child(
                IKey.str(line2)
                    .asWidget()
                    .scale(0.9f))
            .child(
                line3.asWidget()
                    .scale(0.9f))
            .child(
                Flow.row()
                    .height(16)
                    .childPadding(2)
                    .child(renameField)
                    .child(renameButton));

        Flow row = Flow.row()
            .widthRel(1f)
            .height(56)
            .childPadding(4)
            // 交叉轴居中：图标与按钮在 56 高的行内垂直居中
            .crossAxisAlignment(Alignment.CrossAxis.CENTER);
        // 节点图标：按类型静态映射对应缓存节点物品，渲染在行首防止混淆（纯客户端映射，零网络开销）
        if (iconStack != null) {
            row.child(
                new ItemDrawable(iconStack).asWidget()
                    .size(16));
        }
        row.child(column)
            .child(
                // 传送 + 三个 16px 图标按钮横排，宽度由子件撑开无需限宽
                // 历史教训（Bug1 根因）：Flow 构造器默认 sizeRel(1,1) 会占满整行宽，
                // v1.8.4 曾因此把按钮推到面板裁剪区外——row 显式 height(16) 后宽度随子件收缩
                Flow.row()
                    .height(16)
                    .childPadding(4)
                    .child(teleportButton)
                    .child(rateButton)
                    .child(capButton)
                    .child(modeButton)
                    .child(autoButton));
        return row;
    }

    /**
     * 容量档是否适用于该类型串（S4）：缓存节点与接收类仓（singularity_steam/singularity_fluid_in）
     * 适用；发送类仓（singularity_steam_out/singularity_fluid_out）罐只出不进、容量上限无意义，
     * 按钮禁用（与服务端 supportsCapacityTier 判定一致；空 type 为离线行，外层已另行禁用）。
     */
    private static boolean supportsCapTier(String type) {
        return type != null && !type.endsWith("_out");
    }

    /**
     * 流体注册名 → 本地化名（客户端按当前语言显示）；空串表示无流体。
     * 服务端只发注册名（见 MTEFilteredCacheNode.getStoredFluidName），避免服务端语言污染客户端。
     */
    private static String localizeFluid(String registryName) {
        if (registryName == null || registryName.isEmpty()) {
            return StatCollector.translateToLocal("gtsr.cache_hub_status.no_fluid");
        }
        Fluid fluid = FluidRegistry.getFluid(registryName);
        if (fluid == null) return registryName;
        return new FluidStack(fluid, 1).getLocalizedName();
    }

    /**
     * 储量格式化：K/M/G 千位递进、小数点后 2 位（如 12345678 → "12.35M"）；
     * 小于 1000 直接显示原值 + "L"（如 987 → "987L"）。
     */
    public static String formatKMG(long amount) {
        if (amount < 1000) return amount + "L";
        final String[] units = { "K", "M", "G" };
        double value = amount;
        int unit = -1;
        do {
            value /= 1000.0;
            unit++;
        } while (value >= 1000.0 && unit < units.length - 1);
        // Locale.ROOT 锁定小数点为 '.'，避免系统区域设置影响显示
        return String.format(Locale.ROOT, "%.2f%s", value, units[unit]);
    }

    private static String formatFluidLine(CacheNodeInfo info) {
        return EnumChatFormatting.AQUA + localizeFluid(info.fluid)
            + EnumChatFormatting.RESET
            + " "
            + formatKMG(info.stored)
            + EnumChatFormatting.GRAY
            + " / "
            + formatKMG(info.cap)
            + EnumChatFormatting.RESET;
    }

    private static CacheNodeInfo findNode(List<CacheNodeInfo> nodes, CacheNodeInfo key) {
        if (nodes == null || key == null) return null;
        for (CacheNodeInfo node : nodes) {
            if (node.x == key.x && node.y == key.y && node.z == key.z && node.dim == key.dim) return node;
        }
        return null;
    }

    /**
     * 列表中单个缓存节点的显示数据，与枢纽 getCacheNodeListTag() 的字段一一对应。
     */
    public static class CacheNodeInfo {

        public final int x, y, z, dim;
        /** 节点类型串（steam/reinforced_steam/overpressure_steam/water）；空串表示节点离线（无法解析） */
        public final String type;
        /** 节点自定义名（空串表示未自定义，显示时回退默认类型名） */
        public final String name;
        /** 当前存储流体的注册名（空串表示无流体） */
        public final String fluid;
        /** 当前储量（long：强化/超压节点容量超出 int 范围） */
        public final long stored;
        /** 节点容量（long） */
        public final long cap;
        /** 传输速率百分比（100→80→…→0 循环档位） */
        public final int rate;
        /** 容量上限百分比（S4：100→80→…→5 循环档位；发送类仓恒 100 不适用） */
        public final int capPct;
        /** 输出模式：true=节点→枢纽，false=枢纽→节点 */
        public final boolean out;
        /** 自动输出开关：true=节点向正面相邻容器推送流体（与方向模式解耦） */
        public final boolean auto;
        /** 输出模式锁定（奇点仓）；锁定时 GUI 模式按钮禁用。 */
        public final boolean modeLocked;

        CacheNodeInfo(int x, int y, int z, int dim, String type, String name, String fluid, long stored, long cap,
            int rate, int capPct, boolean out, boolean auto, boolean modeLocked) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.dim = dim;
            this.type = type;
            this.name = name;
            this.fluid = fluid;
            this.stored = stored;
            this.cap = cap;
            this.rate = rate;
            this.capPct = capPct;
            this.out = out;
            this.auto = auto;
            this.modeLocked = modeLocked;
        }

        public static List<CacheNodeInfo> fromTagList(NBTTagList tagList) {
            List<CacheNodeInfo> list = new ArrayList<>();
            for (int i = 0; i < tagList.tagCount(); i++) {
                NBTTagCompound tag = tagList.getCompoundTagAt(i);
                list.add(
                    new CacheNodeInfo(
                        tag.getInteger("x"),
                        tag.getInteger("y"),
                        tag.getInteger("z"),
                        tag.getInteger("dim"),
                        tag.getString("type"),
                        tag.getString("name"),
                        tag.getString("fluid"),
                        tag.getLong("stored"),
                        tag.getLong("cap"),
                        tag.getInteger("rate"),
                        tag.getInteger("capPct"),
                        tag.getBoolean("out"),
                        tag.getBoolean("auto"),
                        tag.getBoolean("modeLocked")));
            }
            return list;
        }

        public static CacheNodeInfo read(PacketBuffer buf) {
            return new CacheNodeInfo(
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                ByteBufUtils.readUTF8String(buf),
                ByteBufUtils.readUTF8String(buf),
                ByteBufUtils.readUTF8String(buf),
                buf.readLong(),
                buf.readLong(),
                buf.readInt(),
                buf.readInt(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean());
        }

        public static void write(PacketBuffer buf, CacheNodeInfo info) {
            buf.writeInt(info.x);
            buf.writeInt(info.y);
            buf.writeInt(info.z);
            buf.writeInt(info.dim);
            ByteBufUtils.writeUTF8String(buf, info.type);
            ByteBufUtils.writeUTF8String(buf, info.name);
            ByteBufUtils.writeUTF8String(buf, info.fluid);
            buf.writeLong(info.stored);
            buf.writeLong(info.cap);
            buf.writeInt(info.rate);
            buf.writeInt(info.capPct);
            buf.writeBoolean(info.out);
            buf.writeBoolean(info.auto);
            buf.writeBoolean(info.modeLocked);
        }

        public static boolean areEqual(CacheNodeInfo a, CacheNodeInfo b) {
            return a.x == b.x && a.y == b.y
                && a.z == b.z
                && a.dim == b.dim
                && a.type.equals(b.type)
                && a.name.equals(b.name)
                && a.fluid.equals(b.fluid)
                && a.stored == b.stored
                && a.cap == b.cap
                && a.rate == b.rate
                && a.capPct == b.capPct
                && a.out == b.out
                && a.auto == b.auto
                && a.modeLocked == b.modeLocked;
        }

        public static boolean sameLayout(List<CacheNodeInfo> a, List<CacheNodeInfo> b) {
            if (a == null || b == null || a.size() != b.size()) return false;
            for (int i = 0; i < a.size(); i++) {
                CacheNodeInfo left = a.get(i);
                CacheNodeInfo right = b.get(i);
                if (left.x != right.x || left.y != right.y
                    || left.z != right.z
                    || left.dim != right.dim
                    || !left.type.equals(right.type)
                    || !left.name.equals(right.name)) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * 面板级 C2S 动作处理器：客户端按钮点击 → 携带节点坐标发往服务端，
     * 服务端解析后调用枢纽对应方法（枢纽内部 resolve 失败则静默忽略，天然防伪造坐标），
     * 执行完毕触发列表刷新。
     * 非静态内部类：直接复用外层基类的抽象委托方法，两个子类无需重复实现。
     */
    public class CacheHubActionSyncHandler extends SyncHandler<CacheHubActionSyncHandler> {

        private static final int ACTION_CYCLE_RATE = 1;
        private static final int ACTION_SET_MODE = 2;
        private static final int ACTION_RENAME = 3;
        private static final int ACTION_SET_AUTO = 4;
        private static final int ACTION_TELEPORT = 5;
        private static final int ACTION_CYCLE_CAP = 6;

        public CacheHubActionSyncHandler() {
            allowC2S();
        }

        // ===== 客户端调用：发送动作到服务端 =====

        public void sendCycleRate(CacheNodeInfo info) {
            syncToServer(ACTION_CYCLE_RATE, buf -> writePos(buf, info));
        }

        // 容量档循环（S4）：服务端经 hub.cycleCacheNodeCapFromGui 分发（发送类仓 no-op）
        public void sendCycleCap(CacheNodeInfo info) {
            syncToServer(ACTION_CYCLE_CAP, buf -> writePos(buf, info));
        }

        // 模式切换：携带目标值（当前取反），服务端校验节点存在后写入
        public void sendSetMode(CacheNodeInfo info) {
            syncToServer(ACTION_SET_MODE, buf -> {
                writePos(buf, info);
                buf.writeBoolean(!info.out);
            });
        }

        // 自动输出开关切换：携带目标值（当前取反），服务端校验节点存在后写入
        public void sendSetAutoOutput(CacheNodeInfo info) {
            syncToServer(ACTION_SET_AUTO, buf -> {
                writePos(buf, info);
                buf.writeBoolean(!info.auto);
            });
        }

        // 重命名：携带坐标 + 新名字（UTF8），服务端接收后仍会裁剪（剔 §/去空白/≤24 字符）
        public void sendRename(CacheNodeInfo info, String name) {
            syncToServer(ACTION_RENAME, buf -> {
                writePos(buf, info);
                ByteBufUtils.writeUTF8String(buf, name == null ? "" : name);
            });
        }

        public void sendTeleport(CacheNodeInfo info) {
            syncToServer(ACTION_TELEPORT, buf -> writePos(buf, info));
        }

        private static void writePos(PacketBuffer buf, CacheNodeInfo info) {
            buf.writeInt(info.x);
            buf.writeInt(info.y);
            buf.writeInt(info.z);
            buf.writeInt(info.dim);
        }

        // ===== 服务端执行 =====

        @Override
        public void readOnClient(int id, PacketBuffer buf) throws IOException {}

        @Override
        public void readOnServer(int id, PacketBuffer buf) throws IOException {
            int x = buf.readInt();
            int y = buf.readInt();
            int z = buf.readInt();
            int dim = buf.readInt();
            EntityPlayer player = getSyncManager().getPlayer();
            switch (id) {
                case ACTION_CYCLE_RATE:
                    cycleNodeRate(x, y, z, dim);
                    break;
                case ACTION_CYCLE_CAP:
                    cycleNodeCap(x, y, z, dim);
                    break;
                case ACTION_SET_MODE:
                    setNodeMode(x, y, z, dim, buf.readBoolean());
                    break;
                case ACTION_SET_AUTO:
                    setNodeAuto(x, y, z, dim, buf.readBoolean());
                    break;
                case ACTION_RENAME:
                    renameNode(x, y, z, dim, ByteBufUtils.readUTF8String(buf));
                    break;
                case ACTION_TELEPORT:
                    teleportNode(player, x, y, z, dim);
                    break;
                default:
                    return;
            }
            // 动作执行后主动刷新一次列表（nodeList 变化监听通常也会触发，这里作为即时反馈）
        }
    }
}
