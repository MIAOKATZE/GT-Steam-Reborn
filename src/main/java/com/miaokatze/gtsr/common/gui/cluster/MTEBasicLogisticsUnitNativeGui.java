package com.miaokatze.gtsr.common.gui.cluster;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.miaokatze.gtsr.common.machine.cluster.ChainLink;
import com.miaokatze.gtsr.common.machine.cluster.LogisticsChain;
import com.miaokatze.gtsr.common.machine.cluster.MTEBasicLogisticsUnit;
import com.miaokatze.gtsr.common.machine.cluster.MTESteamMineralLogisticsCluster;

/**
 * 物流模块的 GT 原生 GUI（终验反馈：物流不使用独立 MUI2 UI），继承
 * {@link MTEClusterUnitNativeGui} 通用词条，追加物流富词条：段/垫位置、链摘要
 * （长度+可执行+失败步原因）、物理电源开关与软锤复位指引。
 *
 * <p>
 * 失败步原因为服务端计算的 lang 键（链结构无效取 {@link LogisticsChain#getInvalidReasonKey()}，
 * 链步锁定取 {@link LogisticsChain#getLinkLockReasonKey}，均复用现有 gtsr.gui.cluster.* 键），
 * 经 StringSyncValue 同步后客户端本地化；段/垫同为服务端真值同步。工作态进度显示由
 * GT 原生配方进度行承担（showRecipeTextInGUI 恢复默认，空闲自动隐藏）。
 */
public class MTEBasicLogisticsUnitNativeGui extends MTEClusterUnitNativeGui {

    private final MTEBasicLogisticsUnit logistics;

    public MTEBasicLogisticsUnitNativeGui(MTEBasicLogisticsUnit multiblock) {
        super(multiblock);
        this.logistics = multiblock;
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        syncManager.syncValue("gtsr.logi.seg", new IntSyncValue(logistics::getSegmentIndex));
        syncManager.syncValue("gtsr.logi.pad", new IntSyncValue(logistics::getPadId));
        syncManager.syncValue(
            "gtsr.logi.chainLen",
            new IntSyncValue(
                () -> logistics.getChain()
                    .length()));
        syncManager.syncValue("gtsr.logi.chainExec", new BooleanSyncValue(logistics::isChainExecutableNow));
        syncManager.syncValue("gtsr.logi.chainFail", new StringSyncValue(this::computeChainFailKey));
        syncManager.syncValue("gtsr.logi.power", new BooleanSyncValue(logistics::isPowerAllowed));
    }

    /**
     * 服务端只读：链失败原因 lang 键（可执行返回 null；空链/结构无效/链步锁定按序取现有键）。
     * 与 {@link MTEBasicLogisticsUnit#isChainExecutableNow()} 口径对齐，但不重复电源门控
     * （电源状态由独立词条展示）。
     */
    private String computeChainFailKey() {
        LogisticsChain chain = logistics.getChain();
        if (chain.isEmpty()) return "gtsr.cluster.native.logistics.chain.empty";
        if (!chain.isValidStructure()) return chain.getInvalidReasonKey();
        MTESteamMineralLogisticsCluster cluster = logistics.getCluster();
        if (cluster == null) return "gtsr.cluster.native.unit.link.disconnected";
        if (chain.isExecutable(cluster.getTopology())) return null;
        for (ChainLink link : chain.getLinks()) {
            if (!LogisticsChain.isLinkAvailable(link, cluster.getTopology())) {
                return LogisticsChain.getLinkLockReasonKey(link, cluster.getTopology());
            }
        }
        return "gtsr.cluster.native.logistics.chain.blocked";
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager syncManager, ModularPanel parent) {
        IntSyncValue segSync = syncManager.findSyncHandler("gtsr.logi.seg", IntSyncValue.class);
        IntSyncValue padSync = syncManager.findSyncHandler("gtsr.logi.pad", IntSyncValue.class);
        IntSyncValue chainLenSync = syncManager.findSyncHandler("gtsr.logi.chainLen", IntSyncValue.class);
        BooleanSyncValue chainExecSync = syncManager.findSyncHandler("gtsr.logi.chainExec", BooleanSyncValue.class);
        StringSyncValue chainFailSync = syncManager.findSyncHandler("gtsr.logi.chainFail", StringSyncValue.class);
        BooleanSyncValue powerSync = syncManager.findSyncHandler("gtsr.logi.power", BooleanSyncValue.class);

        ListWidget<IWidget, ?> list = super.createTerminalTextWidget(syncManager, parent);
        // 段/垫位置（padId<0 = 未入位）
        list.child(IKey.dynamic(() -> {
            if (padSync.getValue() < 0 || segSync.getValue() < 0) {
                return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.cluster.native.logistics.slot")
                    + EnumChatFormatting.GRAY
                    + StatCollector.translateToLocal("gtsr.cluster.native.logistics.slot.unplaced")
                    + EnumChatFormatting.RESET;
            }
            return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.cluster.native.logistics.slot")
                + EnumChatFormatting.WHITE
                + String.format(
                    StatCollector.translateToLocal("gtsr.cluster.native.logistics.slot.value"),
                    String.valueOf(segSync.getValue()),
                    String.valueOf(padSync.getValue()))
                + EnumChatFormatting.RESET;
        })
            .asWidget()
            .marginBottom(2)
            .fullWidth())
            // 链摘要：长度 + 可执行（绿）/不可执行（红 + 失败步原因）
            .child(IKey.dynamic(() -> {
                String failKey = chainFailSync.getValue();
                if ("gtsr.cluster.native.logistics.chain.empty".equals(failKey)) {
                    return EnumChatFormatting.YELLOW
                        + StatCollector.translateToLocal("gtsr.cluster.native.logistics.chain")
                        + EnumChatFormatting.GRAY
                        + StatCollector.translateToLocal("gtsr.cluster.native.logistics.chain.empty")
                        + EnumChatFormatting.RESET;
                }
                boolean executable = chainExecSync.getValue();
                String summary = executable
                    ? StatCollector.translateToLocal("gtsr.cluster.native.logistics.chain.executable")
                    : StatCollector.translateToLocal("gtsr.cluster.native.logistics.chain.blocked");
                StringBuilder sb = new StringBuilder(
                    EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.cluster.native.logistics.chain")
                        + EnumChatFormatting.WHITE
                        + String.format(
                            StatCollector.translateToLocal("gtsr.cluster.native.logistics.chain.length"),
                            String.valueOf(chainLenSync.getValue()))
                        + (executable ? EnumChatFormatting.GREEN : EnumChatFormatting.RED)
                        + " · "
                        + summary);
                if (!executable && failKey != null && !failKey.isEmpty()) {
                    sb.append(EnumChatFormatting.RED)
                        .append(" (")
                        .append(StatCollector.translateToLocal(failKey))
                        .append(')');
                }
                return sb.append(EnumChatFormatting.RESET)
                    .toString();
            })
                .asWidget()
                .marginBottom(2)
                .fullWidth())
            // 物理电源：关机时红字附软锤复位指引（GT 标准启停切换）
            .child(IKey.dynamic(() -> {
                if (powerSync.getValue()) {
                    return EnumChatFormatting.YELLOW
                        + StatCollector.translateToLocal("gtsr.cluster.native.logistics.power")
                        + EnumChatFormatting.GREEN
                        + StatCollector.translateToLocal("gtsr.cluster.native.logistics.power.on")
                        + EnumChatFormatting.RESET;
                }
                return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.cluster.native.logistics.power")
                    + EnumChatFormatting.RED
                    + StatCollector.translateToLocal("gtsr.cluster.native.logistics.power.off")
                    + EnumChatFormatting.RESET;
            })
                .asWidget()
                .marginBottom(2)
                .fullWidth());
        return list;
    }
}
