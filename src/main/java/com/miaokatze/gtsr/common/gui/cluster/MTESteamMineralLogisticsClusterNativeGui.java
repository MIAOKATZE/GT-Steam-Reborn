package com.miaokatze.gtsr.common.gui.cluster;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.DoubleSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;
import com.miaokatze.gtsr.common.machine.cluster.ClusterTopology;
import com.miaokatze.gtsr.common.machine.cluster.MTEBasicAmplifierUnit;
import com.miaokatze.gtsr.common.machine.cluster.MTEBasicLogisticsUnit;
import com.miaokatze.gtsr.common.machine.cluster.MTEBasicProcessingUnit;
import com.miaokatze.gtsr.common.machine.cluster.MTESteamMineralLogisticsCluster;

import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

/**
 * 蒸汽矿物物流集群总控的 GT 原生 GUI（终验反馈：集群不使用独立 UI，终端词条并入 GT 原生
 * 多方块 GUI，范式同 MTELargeSteamFurnaceGui / MTECrustMatterAggregatorGui）。
 *
 * <p>
 * 全部集群字段（成型/段数/tier/热量/蒸汽/润滑/吞吐/累计/模块计数/供给异常位/总电源）均为
 * 服务端真值，经 {@code registerSyncValues} 的 syncValue 注册同步；客户端渲染只读 syncer
 * 缓存值 + lang 键本地化，绝不客户端直读集群字段（客户端 MTE 实例无拓扑/经济真值）。
 * tier 名复用 {@code gtsr.gui.cluster.tier.*} 现有键；其余词条键为
 * {@code gtsr.cluster.native.*}。
 */
public class MTESteamMineralLogisticsClusterNativeGui extends MTEMultiBlockBaseGui<MTEEnhancedMultiBlockBase<?>> {

    private final MTESteamMineralLogisticsCluster cluster;

    public MTESteamMineralLogisticsClusterNativeGui(MTESteamMineralLogisticsCluster multiblock) {
        super(multiblock);
        this.cluster = multiblock;
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        syncManager.syncValue("gtsr.cluster.formed", new BooleanSyncValue(() -> cluster.mMachine));
        syncManager.syncValue("gtsr.cluster.segs", new IntSyncValue(this::segmentCount));
        syncManager.syncValue("gtsr.cluster.tier", new IntSyncValue(cluster::getStructureTierIndex));
        syncManager.syncValue("gtsr.cluster.enabled", new BooleanSyncValue(cluster::isMachineEnabled));
        syncManager.syncValue("gtsr.cluster.heat", new IntSyncValue(cluster::getHeatPercent));
        syncManager.syncValue("gtsr.cluster.steam", new IntSyncValue(cluster::getSteamLps));
        syncManager.syncValue("gtsr.cluster.lube", new IntSyncValue(cluster::getLubricantLps));
        syncManager.syncValue("gtsr.cluster.thru", new IntSyncValue(cluster::getThroughputPerSec));
        // long 累计经 Double 通道同步（2^53 内精确），客户端 NumberFormatUtil 格式化
        syncManager.syncValue("gtsr.cluster.total", new DoubleSyncValue(() -> (double) cluster.getTotalProcessedOre()));
        syncManager.syncValue("gtsr.cluster.proc", new IntSyncValue(() -> countUnits(MTEBasicProcessingUnit.class)));
        syncManager.syncValue("gtsr.cluster.boost", new IntSyncValue(() -> countUnits(MTEBasicAmplifierUnit.class)));
        syncManager.syncValue("gtsr.cluster.logi", new IntSyncValue(() -> countUnits(MTEBasicLogisticsUnit.class)));
        syncManager.syncValue("gtsr.cluster.supply", new IntSyncValue(cluster::getSupplyFlags));
    }

    /** 服务端只读：当前段数（topology 为 null——未成型/结构重检重建期——返回 0）。 */
    private int segmentCount() {
        ClusterTopology topology = cluster.getTopology();
        return topology == null ? 0 : topology.getSegmentCount();
    }

    /** 服务端只读：按类型统计已连接模块数（topology 为 null 时全为 0）。 */
    private int countUnits(Class<? extends com.miaokatze.gtsr.common.machine.cluster.MTEClusterUnitBase> type) {
        ClusterTopology topology = cluster.getTopology();
        return topology == null ? 0 : topology.countUnits(type);
    }

    /** tier 下标（0-3）→ 现有四档 tier 名 lang 键（gtsr.gui.cluster.tier.*）。 */
    private static String tierNameKey(int tier) {
        switch (tier) {
            case 0:
                return "gtsr.gui.cluster.tier.bronze";
            case 1:
                return "gtsr.gui.cluster.tier.steel";
            case 2:
                return "gtsr.gui.cluster.tier.titanium";
            case 3:
                return "gtsr.gui.cluster.tier.tungstensteel";
            default:
                return "gtsr.cluster.native.structure.unformed";
        }
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager syncManager, ModularPanel parent) {
        BooleanSyncValue formedSync = syncManager.findSyncHandler("gtsr.cluster.formed", BooleanSyncValue.class);
        IntSyncValue segsSync = syncManager.findSyncHandler("gtsr.cluster.segs", IntSyncValue.class);
        IntSyncValue tierSync = syncManager.findSyncHandler("gtsr.cluster.tier", IntSyncValue.class);
        BooleanSyncValue enabledSync = syncManager.findSyncHandler("gtsr.cluster.enabled", BooleanSyncValue.class);
        IntSyncValue heatSync = syncManager.findSyncHandler("gtsr.cluster.heat", IntSyncValue.class);
        IntSyncValue steamSync = syncManager.findSyncHandler("gtsr.cluster.steam", IntSyncValue.class);
        IntSyncValue lubeSync = syncManager.findSyncHandler("gtsr.cluster.lube", IntSyncValue.class);
        IntSyncValue thruSync = syncManager.findSyncHandler("gtsr.cluster.thru", IntSyncValue.class);
        DoubleSyncValue totalSync = syncManager.findSyncHandler("gtsr.cluster.total", DoubleSyncValue.class);
        IntSyncValue procSync = syncManager.findSyncHandler("gtsr.cluster.proc", IntSyncValue.class);
        IntSyncValue boostSync = syncManager.findSyncHandler("gtsr.cluster.boost", IntSyncValue.class);
        IntSyncValue logiSync = syncManager.findSyncHandler("gtsr.cluster.logi", IntSyncValue.class);
        IntSyncValue supplySync = syncManager.findSyncHandler("gtsr.cluster.supply", IntSyncValue.class);

        ListWidget<IWidget, ?> list = super.createTerminalTextWidget(syncManager, parent);
        // 结构状态：成型时绿字 + 段数 N/10 + tier 名；未成型红字
        list.child(IKey.dynamic(() -> {
            if (!formedSync.getValue()) {
                return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.cluster.native.structure")
                    + EnumChatFormatting.RED
                    + StatCollector.translateToLocal("gtsr.cluster.native.structure.unformed")
                    + EnumChatFormatting.RESET;
            }
            return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.cluster.native.structure")
                + EnumChatFormatting.GREEN
                + StatCollector.translateToLocal("gtsr.cluster.native.structure.formed")
                + EnumChatFormatting.WHITE
                + " · "
                + String.format(
                    StatCollector.translateToLocal("gtsr.cluster.native.segments"),
                    String.valueOf(segsSync.getValue()))
                + " · "
                + EnumChatFormatting.GOLD
                + StatCollector.translateToLocal(tierNameKey(tierSync.getValue()))
                + EnumChatFormatting.RESET;
        })
            .asWidget()
            .marginBottom(2)
            .fullWidth())
            // 总电源开关
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.cluster.native.power")
                        + (enabledSync.getValue() ? EnumChatFormatting.GREEN : EnumChatFormatting.RED)
                        + StatCollector.translateToLocal(
                            enabledSync.getValue() ? "gtsr.cluster.native.power.on" : "gtsr.cluster.native.power.off")
                        + EnumChatFormatting.RESET)
                    .asWidget()
                    .marginBottom(2)
                    .fullWidth())
            // 热量百分比（<100 黄，满热绿）
            .child(IKey.dynamic(() -> {
                int heat = heatSync.getValue();
                EnumChatFormatting color = heat >= 100 ? EnumChatFormatting.GREEN : EnumChatFormatting.YELLOW;
                return EnumChatFormatting.YELLOW + StatCollector
                    .translateToLocal("gtsr.cluster.native.heat") + color + heat + "%" + EnumChatFormatting.RESET;
            })
                .asWidget()
                .marginBottom(2)
                .fullWidth())
            // 蒸汽 L/s（停机 0 由服务端口径保证）
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.cluster.native.steam")
                        + EnumChatFormatting.WHITE
                        + NumberFormatUtil.formatNumber(steamSync.getValue())
                        + " L/s"
                        + EnumChatFormatting.RESET)
                    .asWidget()
                    .marginBottom(2)
                    .fullWidth())
            // 润滑油 L/s
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.cluster.native.lube")
                        + EnumChatFormatting.WHITE
                        + NumberFormatUtil.formatNumber(lubeSync.getValue())
                        + " L/s"
                        + EnumChatFormatting.RESET)
                    .asWidget()
                    .marginBottom(2)
                    .fullWidth())
            // 吞吐/s + 累计
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.cluster.native.throughput")
                        + EnumChatFormatting.WHITE
                        + NumberFormatUtil.formatNumber(thruSync.getValue())
                        + EnumChatFormatting.GRAY
                        + " · "
                        + StatCollector.translateToLocal("gtsr.cluster.native.total")
                        + EnumChatFormatting.WHITE
                        + NumberFormatUtil.formatNumber(Math.round(totalSync.getValue()))
                        + EnumChatFormatting.RESET)
                    .asWidget()
                    .marginBottom(2)
                    .fullWidth())
            // 模块计数（加工/增幅/物流已连接）
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.cluster.native.modules")
                        + EnumChatFormatting.WHITE
                        + String.format(
                            StatCollector.translateToLocal("gtsr.cluster.native.modules.count"),
                            String.valueOf(procSync.getValue()),
                            String.valueOf(boostSync.getValue()),
                            String.valueOf(logiSync.getValue()))
                        + EnumChatFormatting.RESET)
                    .asWidget()
                    .marginBottom(2)
                    .fullWidth())
            // 供给异常行：正常绿字；短缺项红字拼接（bit0=蒸汽不足，bit1=润滑不足，bit2=断供降温中）
            .child(IKey.dynamic(() -> {
                int flags = supplySync.getValue();
                if (flags == 0) {
                    return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.cluster.native.supply")
                        + EnumChatFormatting.GREEN
                        + StatCollector.translateToLocal("gtsr.cluster.native.supply.ok")
                        + EnumChatFormatting.RESET;
                }
                StringBuilder sb = new StringBuilder();
                if ((flags & 0x01) != 0) {
                    sb.append(StatCollector.translateToLocal("gtsr.cluster.native.supply.steam_short"));
                    sb.append(" / ");
                }
                if ((flags & 0x02) != 0) {
                    sb.append(StatCollector.translateToLocal("gtsr.cluster.native.supply.lube_short"));
                    sb.append(" / ");
                }
                if ((flags & 0x04) != 0) {
                    sb.append(StatCollector.translateToLocal("gtsr.cluster.native.supply.thermal"));
                }
                String text = sb.toString();
                if (text.endsWith(" / ")) text = text.substring(0, text.length() - 3);
                return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.cluster.native.supply")
                    + EnumChatFormatting.RED
                    + text
                    + EnumChatFormatting.RESET;
            })
                .asWidget()
                .marginBottom(2)
                .fullWidth());
        return list;
    }
}
