package com.miaokatze.gtsr.common.gui;

import static gregtech.api.enums.Mods.GregTech;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.drawable.UITexture;
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
import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.machine.MTESingularityDrillingHub;
import com.miaokatze.gtsr.main.GTSteamReborn;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.modularui2.GTGuiTextures;
import gregtech.api.util.GTUtility;

/**
 * 奇点钻井枢纽「节点状态管理界面」（Modern UI 2）。
 * 打开方式：空手 + 潜行右击枢纽（服务端经 HubStatusGuiFactory 打开）。
 * 功能：查看全部绑定节点（维度+坐标、类型、等级、工作状态），
 * 远程开始/停止、快捷回收（仅完全停止的节点）、消耗背包物品升级节点。
 *
 * 同步设计：
 * - "nodeList"：GenericListSyncHandler，服务端每 tick 检测变化并同步到客户端；
 * - "hubAction"：单个面板级 C2S 动作处理器，按钮点击携带节点坐标发往服务端执行；
 * - listDynamic：DynamicSyncHandler，列表数据变化时重建节点列表控件（自带滚动条）。
 */
public class MTESingularityHubStatusGui implements IGuiHolder<PosGuiData> {

    private static final int PANEL_WIDTH = 320;
    private static final int PANEL_HEIGHT = 200;

    private final MTESingularityDrillingHub hub;

    public MTESingularityHubStatusGui(MTESingularityDrillingHub hub) {
        this.hub = hub;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ModularScreen createScreen(PosGuiData data, ModularPanel mainPanel) {
        return new ModularScreen(GTSteamReborn.MODID, mainPanel);
    }

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        // 节点列表数据同步（S2C）：数据源为枢纽的 getNodeListTag()
        GenericListSyncHandler<HubNodeInfo> nodeListSync = new GenericListSyncHandler<>(
            () -> HubNodeInfo.fromTagList(hub.getNodeListTag()),
            null,
            HubNodeInfo::read,
            HubNodeInfo::write,
            HubNodeInfo::areEqual,
            null);

        // 按钮动作同步（C2S）：所有行按钮共用同一个处理器，按坐标定位节点
        HubActionSyncHandler actionSync = new HubActionSyncHandler(hub);

        // 动态列表控件：数据变化时重建，widgetProvider 在双端执行
        DynamicSyncHandler listDynamic = new DynamicSyncHandler()
            .widgetProvider((pSyncManager, buf) -> buildNodeListWidget(pSyncManager))
            .allowC2S();
        actionSync.setRefreshListener(() -> listDynamic.notifyUpdate(buf -> {}));
        nodeListSync.setChangeListener(() -> listDynamic.notifyUpdate(buf -> {}));

        syncManager.syncValue("nodeList", nodeListSync);
        syncManager.syncValue("hubAction", actionSync);

        DynamicSyncedWidget<?> listArea = new DynamicSyncedWidget<>().pos(8, 22)
            .size(PANEL_WIDTH - 16, PANEL_HEIGHT - 30)
            .syncHandler(listDynamic);

        ModularPanel panel = ModularPanel.defaultPanel("hub_status", PANEL_WIDTH, PANEL_HEIGHT)
            .background(GTGuiTextures.BACKGROUND_STANDARD)
            .child(ButtonWidget.panelCloseButton())
            .child(
                IKey.lang("gtsr.hub_status.title")
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
        GenericListSyncHandler<HubNodeInfo> listSync = pSyncManager
            .findSyncHandler("nodeList", GenericListSyncHandler.class);
        HubActionSyncHandler actionSync = pSyncManager.findSyncHandler("hubAction", HubActionSyncHandler.class);
        List<HubNodeInfo> nodes = listSync != null ? (List<HubNodeInfo>) listSync.getValue() : Collections.emptyList();

        ListWidget<IWidget, ?> list = new ListWidget<>();
        list.widthRel(1f)
            .heightRel(1f);
        if (nodes.isEmpty()) {
            list.child(
                IKey.lang("gtsr.hub_status.empty")
                    .asWidget());
            return list;
        }
        for (HubNodeInfo info : nodes) {
            list.child(buildNodeRow(info, actionSync));
        }
        return list;
    }

    /**
     * 构建单个节点行：第一行为节点名/等级/状态，第二行为坐标与维度，第三行为重命名控件，
     * 右侧为操作按钮。
     */
    private IWidget buildNodeRow(HubNodeInfo info, HubActionSyncHandler actionSync) {
        String typeName = StatCollector
            .translateToLocal(info.isMiner ? "gtsr.drilling.node_miner" : "gtsr.drilling.node_driller");
        // 节点名：有自定义名优先显示自定义名，否则回退默认类型名
        String nodeName = info.name.isEmpty() ? typeName : info.name;
        // 状态文本：不允许工作=已停止；允许且实际工作中=运行中；允许但空闲=待机
        String statusText;
        EnumChatFormatting statusColor;
        if (!info.allowed) {
            statusText = StatCollector.translateToLocal("gtsr.hub_status.status.stopped");
            statusColor = EnumChatFormatting.RED;
        } else if (info.working) {
            statusText = StatCollector.translateToLocal("gtsr.gui.status.running");
            statusColor = EnumChatFormatting.GREEN;
        } else {
            statusText = StatCollector.translateToLocal("gtsr.gui.status.idle");
            statusColor = EnumChatFormatting.YELLOW;
        }
        String line1 = nodeName + " "
            + EnumChatFormatting.AQUA
            + "Mk"
            + (info.tier + 1)
            + EnumChatFormatting.RESET
            + "  "
            + statusColor
            + statusText
            + EnumChatFormatting.RESET;
        String line2 = EnumChatFormatting.GRAY + "("
            + info.x
            + ", "
            + info.y
            + ", "
            + info.z
            + ") DIM: "
            + info.dim
            + EnumChatFormatting.RESET;

        // 开始/停止切换按钮
        ButtonWidget<?> toggleButton = new ButtonWidget<>().size(16)
            .overlay(
                info.allowed ? GTGuiTextures.OVERLAY_BUTTON_POWER_SWITCH_OFF
                    : GTGuiTextures.OVERLAY_BUTTON_POWER_SWITCH_ON)
            .onMousePressed(mouseButton -> {
                actionSync.sendToggle(info);
                return true;
            })
            .tooltipBuilder(t -> t.addLine(IKey.lang(info.allowed ? "gtsr.hub_status.stop" : "gtsr.hub_status.start")));

        // 快捷回收按钮：仅「完全停止」的节点可用
        ButtonWidget<?> recycleButton = new ButtonWidget<>().size(16)
            .overlay(GTGuiTextures.OVERLAY_BUTTON_EXPORT)
            .onMousePressed(mouseButton -> {
                actionSync.sendRecycle(info);
                return true;
            })
            .tooltipBuilder(
                t -> t.addLine(
                    IKey.lang(info.retractable ? "gtsr.hub_status.recycle" : "gtsr.hub_status.recycle_disabled")));
        recycleButton.setEnabled(info.retractable);

        // 升级按钮：消耗背包物品（图标与节点自带UI的绿色上箭头保持一致）
        ButtonWidget<?> upgradeButton = new ButtonWidget<>().size(16)
            .overlay(UITexture.fullImage(GregTech.ID, "gui/overlay_button/arrow_green_up"))
            .onMousePressed(mouseButton -> {
                actionSync.sendUpgrade(info);
                return true;
            })
            .tooltipBuilder(t -> t.addLine(IKey.lang("gtsr.hub_status.upgrade")));

        // 重命名文本框：纯客户端控件（不注册 sync handler，StringValue 为本地值不会同步），
        // 初始文本为当前自定义名；点击确认按钮时才读取文本经 hubAction 发 C2S，服务端做裁剪
        TextFieldWidget renameField = new TextFieldWidget().width(150)
            .height(16)
            .setMaxLength(24)
            .value(new StringValue(info.name))
            .tooltipBuilder(t -> t.addLine(IKey.lang("gtsr.hub_status.rename_hint")));

        // 重命名确认按钮：读取文本框内容发送到服务端
        ButtonWidget<?> renameButton = new ButtonWidget<>().size(16)
            .overlay(GTGuiTextures.OVERLAY_BUTTON_CHECKMARK)
            .onMousePressed(mouseButton -> {
                actionSync.sendRename(info, renameField.getText());
                return true;
            })
            .tooltipBuilder(t -> t.addLine(IKey.lang("gtsr.hub_status.rename")));

        // 节点图标：按类型静态映射对应节点物品，渲染在行首防止混淆（纯客户端映射，零网络开销）
        ItemStack iconStack = (info.isMiner ? GTSRItemList.SingularityMinerNode : GTSRItemList.SingularityDrillingNode)
            .get(1);
        IWidget iconWidget = new ItemDrawable(iconStack).asWidget()
            .size(16);

        return Flow.row()
            .widthRel(1f)
            .height(44)
            .childPadding(4)
            // 交叉轴居中：图标与按钮在 44 高的行内垂直居中
            .crossAxisAlignment(Alignment.CrossAxis.CENTER)
            .child(iconWidget)
            .child(
                Flow.column()
                    .width(200)
                    .childPadding(1)
                    .child(
                        IKey.str(line1)
                            .asWidget())
                    .child(
                        IKey.str(line2)
                            .asWidget()
                            .scale(0.9f))
                    .child(
                        Flow.row()
                            .height(16)
                            .childPadding(2)
                            .child(renameField)
                            .child(renameButton)))
            .child(toggleButton)
            .child(recycleButton)
            .child(upgradeButton);
    }

    /**
     * 列表中单个节点的显示数据，与 MTESingularityDrillingHub.getNodeListTag() 的字段一一对应。
     */
    public static class HubNodeInfo {

        public final int x, y, z, dim;
        public final boolean isMiner;
        public final int tier;
        /** 节点是否实际工作中（消耗蒸汽） */
        public final boolean working;
        /** 节点底座 allowedToWork 标志（开始/停止按钮的当前状态） */
        public final boolean allowed;
        /** 是否完全停止（允许快捷回收） */
        public final boolean retractable;
        /** 节点自定义名（空串表示未自定义，显示时回退默认类型名） */
        public final String name;

        HubNodeInfo(int x, int y, int z, int dim, boolean isMiner, int tier, boolean working, boolean allowed,
            boolean retractable, String name) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.dim = dim;
            this.isMiner = isMiner;
            this.tier = tier;
            this.working = working;
            this.allowed = allowed;
            this.retractable = retractable;
            this.name = name;
        }

        public static List<HubNodeInfo> fromTagList(NBTTagList tagList) {
            List<HubNodeInfo> list = new ArrayList<>();
            for (int i = 0; i < tagList.tagCount(); i++) {
                NBTTagCompound tag = tagList.getCompoundTagAt(i);
                list.add(
                    new HubNodeInfo(
                        tag.getInteger("x"),
                        tag.getInteger("y"),
                        tag.getInteger("z"),
                        tag.getInteger("dim"),
                        tag.getBoolean("isMiner"),
                        tag.getInteger("tier"),
                        tag.getBoolean("working"),
                        tag.getBoolean("allowed"),
                        tag.getBoolean("retractable"),
                        tag.getString("name")));
            }
            return list;
        }

        public static HubNodeInfo read(PacketBuffer buf) {
            return new HubNodeInfo(
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readBoolean(),
                buf.readInt(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                ByteBufUtils.readUTF8String(buf));
        }

        public static void write(PacketBuffer buf, HubNodeInfo info) {
            buf.writeInt(info.x);
            buf.writeInt(info.y);
            buf.writeInt(info.z);
            buf.writeInt(info.dim);
            buf.writeBoolean(info.isMiner);
            buf.writeInt(info.tier);
            buf.writeBoolean(info.working);
            buf.writeBoolean(info.allowed);
            buf.writeBoolean(info.retractable);
            ByteBufUtils.writeUTF8String(buf, info.name);
        }

        public static boolean areEqual(HubNodeInfo a, HubNodeInfo b) {
            return a.x == b.x && a.y == b.y
                && a.z == b.z
                && a.dim == b.dim
                && a.isMiner == b.isMiner
                && a.tier == b.tier
                && a.working == b.working
                && a.allowed == b.allowed
                && a.retractable == b.retractable
                && a.name.equals(b.name);
        }
    }

    /**
     * 面板级 C2S 动作处理器：客户端按钮点击 → 携带节点坐标发往服务端，
     * 服务端解析后调用枢纽对应方法，执行完毕触发列表刷新。
     */
    public static class HubActionSyncHandler extends SyncHandler<HubActionSyncHandler> {

        private static final int ACTION_TOGGLE = 1;
        private static final int ACTION_RECYCLE = 2;
        private static final int ACTION_UPGRADE = 3;
        private static final int ACTION_RENAME = 4;

        private final MTESingularityDrillingHub hub;
        private Runnable refreshListener = () -> {};

        public HubActionSyncHandler(MTESingularityDrillingHub hub) {
            this.hub = hub;
            allowC2S();
        }

        public void setRefreshListener(Runnable refreshListener) {
            this.refreshListener = refreshListener;
        }

        // ===== 客户端调用：发送动作到服务端 =====

        public void sendToggle(HubNodeInfo info) {
            syncToServer(ACTION_TOGGLE, buf -> {
                writePos(buf, info);
                buf.writeBoolean(!info.allowed);
            });
        }

        public void sendRecycle(HubNodeInfo info) {
            syncToServer(ACTION_RECYCLE, buf -> writePos(buf, info));
        }

        public void sendUpgrade(HubNodeInfo info) {
            syncToServer(ACTION_UPGRADE, buf -> writePos(buf, info));
        }

        // 重命名：携带坐标 + 新名字（UTF8），服务端接收后仍会裁剪（剔 §/去空白/≤24 字符）
        public void sendRename(HubNodeInfo info, String name) {
            syncToServer(ACTION_RENAME, buf -> {
                writePos(buf, info);
                ByteBufUtils.writeUTF8String(buf, name == null ? "" : name);
            });
        }

        private static void writePos(PacketBuffer buf, HubNodeInfo info) {
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
                case ACTION_TOGGLE:
                    hub.setNodeActiveFromGui(x, y, z, dim, buf.readBoolean());
                    break;
                case ACTION_RECYCLE:
                    if (!hub.recycleNodeFromGui(x, y, z, dim)) {
                        GTUtility
                            .sendChatToPlayer(player, StatCollector.translateToLocal("gtsr.hub_status.recycle_fail"));
                    }
                    break;
                case ACTION_UPGRADE:
                    hub.upgradeNodeFromGui(player, x, y, z, dim);
                    break;
                case ACTION_RENAME:
                    hub.renameNodeFromGui(x, y, z, dim, ByteBufUtils.readUTF8String(buf));
                    break;
                default:
                    return;
            }
            // 动作执行后主动刷新一次列表（nodeList 变化监听通常也会触发，这里作为即时反馈）
            refreshListener.run();
        }
    }
}
