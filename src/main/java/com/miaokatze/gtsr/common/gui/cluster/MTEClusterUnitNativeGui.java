package com.miaokatze.gtsr.common.gui.cluster;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.miaokatze.gtsr.common.machine.cluster.ClusterUnitStatus;
import com.miaokatze.gtsr.common.machine.cluster.MTEClusterUnitBase;

import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

/**
 * 集群单元（加工/增幅/物流全部模块共享）的 GT 原生 GUI（终验反馈：模块不使用独立 UI，
 * 词条并入 GT 原生多方块 GUI）。物流模块经 {@link MTEBasicLogisticsUnitNativeGui} 子类追加
 * 富词条；加工/增幅子类直接沿用本类。
 *
 * <p>
 * 通用词条：模块类型名（{@code gtsr.gui.cluster.unit_type.*} 客户端直读类型常量）、结构
 * tier（{@link MTEClusterUnitBase#getUnitStructureTier()} 服务端真值，-1=未验证）、六态状态
 * （{@link ClusterUnitStatus#getLangKey()} 复用现有键 + EnumChatFormatting 着色）、运行信号
 * 与集群连接。模块的集群引用/tank 等字段客户端 MTE 实例均无真值，一律 syncValue 服务端同步。
 */
public class MTEClusterUnitNativeGui extends MTEMultiBlockBaseGui<MTEEnhancedMultiBlockBase<?>> {

    /** 六态 → EnumChatFormatting（按 ordinal 冻结序：工作中绿/空转黄/缺处理流体蓝/缺增幅流体紫/待机灰/离线红）。 */
    private static final EnumChatFormatting[] STATUS_COLORS = { EnumChatFormatting.GREEN, // WORKING
        EnumChatFormatting.YELLOW, // IDLE
        EnumChatFormatting.AQUA, // FLUID_MISSING
        EnumChatFormatting.LIGHT_PURPLE, // BOOSTER_FLUID_MISSING
        EnumChatFormatting.GRAY, // STANDBY
        EnumChatFormatting.RED // NO_POWER_OR_INVALID
    };

    protected final MTEClusterUnitBase<?> unit;

    public MTEClusterUnitNativeGui(MTEClusterUnitBase<?> multiblock) {
        super(multiblock);
        this.unit = multiblock;
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        syncManager.syncValue("gtsr.unit.tier", new IntSyncValue(unit::getUnitStructureTier));
        syncManager.syncValue(
            "gtsr.unit.status",
            new IntSyncValue(
                () -> unit.getUnitStatus()
                    .ordinal()));
        syncManager.syncValue("gtsr.unit.running", new BooleanSyncValue(unit::isUnitRunning));
        syncManager.syncValue("gtsr.unit.linked", new BooleanSyncValue(() -> unit.getCluster() != null));
    }

    /** tier 下标（0-3）→ 现有四档 tier 名 lang 键（gtsr.gui.cluster.tier.*）。 */
    static String tierNameKey(int tier) {
        switch (tier) {
            case 0:
                return "gtsr.gui.cluster.tier.bronze";
            case 1:
                return "gtsr.gui.cluster.tier.steel";
            case 2:
                return "gtsr.gui.cluster.tier.titanium";
            default:
                return "gtsr.gui.cluster.tier.tungstensteel";
        }
    }

    /** 六态 ordinal → EnumChatFormatting（越界回退红色）。 */
    static EnumChatFormatting statusColor(int ordinal) {
        if (ordinal < 0 || ordinal >= STATUS_COLORS.length) return EnumChatFormatting.RED;
        return STATUS_COLORS[ordinal];
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager syncManager, ModularPanel parent) {
        IntSyncValue tierSync = syncManager.findSyncHandler("gtsr.unit.tier", IntSyncValue.class);
        IntSyncValue statusSync = syncManager.findSyncHandler("gtsr.unit.status", IntSyncValue.class);
        BooleanSyncValue runningSync = syncManager.findSyncHandler("gtsr.unit.running", BooleanSyncValue.class);
        BooleanSyncValue linkedSync = syncManager.findSyncHandler("gtsr.unit.linked", BooleanSyncValue.class);

        ListWidget<IWidget, ?> list = super.createTerminalTextWidget(syncManager, parent);
        // 模块类型名：类型级常量覆写，客户端 MTE 同类型直读安全
        list.child(
            IKey.dynamic(
                () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.cluster.native.unit.type")
                    + EnumChatFormatting.AQUA
                    + StatCollector.translateToLocal(unit.getUnitTypeNameKey())
                    + EnumChatFormatting.RESET)
                .asWidget()
                .marginBottom(2)
                .fullWidth())
            // 结构 tier：-1=未验证（灰）
            .child(IKey.dynamic(() -> {
                int tier = tierSync.getValue();
                EnumChatFormatting color = tier >= 0 ? EnumChatFormatting.GOLD : EnumChatFormatting.GRAY;
                String name = tier >= 0 ? StatCollector.translateToLocal(tierNameKey(tier))
                    : StatCollector.translateToLocal("gtsr.cluster.native.unit.tier.unverified");
                return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.cluster.native.unit.tier")
                    + color
                    + name
                    + EnumChatFormatting.RESET;
            })
                .asWidget()
                .marginBottom(2)
                .fullWidth())
            // 六态状态（复用 gtsr.gui.cluster.state.* 键）
            .child(IKey.dynamic(() -> {
                int ordinal = statusSync.getValue();
                ClusterUnitStatus[] values = ClusterUnitStatus.values();
                String statusKey = ordinal >= 0 && ordinal < values.length ? values[ordinal].getLangKey()
                    : ClusterUnitStatus.NO_POWER_OR_INVALID.getLangKey();
                return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.cluster.native.unit.status")
                    + statusColor(ordinal)
                    + StatCollector.translateToLocal(statusKey)
                    + EnumChatFormatting.RESET;
            })
                .asWidget()
                .marginBottom(2)
                .fullWidth())
            // 运行信号
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.cluster.native.unit.running")
                        + (runningSync.getValue() ? EnumChatFormatting.GREEN : EnumChatFormatting.GRAY)
                        + StatCollector.translateToLocal(
                            runningSync.getValue() ? "gtsr.cluster.native.unit.running.on"
                                : "gtsr.cluster.native.unit.running.off")
                        + EnumChatFormatting.RESET)
                    .asWidget()
                    .marginBottom(2)
                    .fullWidth())
            // 集群连接状态
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.cluster.native.unit.link")
                        + (linkedSync.getValue() ? EnumChatFormatting.GREEN : EnumChatFormatting.RED)
                        + StatCollector.translateToLocal(
                            linkedSync.getValue() ? "gtsr.cluster.native.unit.link.connected"
                                : "gtsr.cluster.native.unit.link.disconnected")
                        + EnumChatFormatting.RESET)
                    .asWidget()
                    .marginBottom(2)
                    .fullWidth());
        return list;
    }
}
