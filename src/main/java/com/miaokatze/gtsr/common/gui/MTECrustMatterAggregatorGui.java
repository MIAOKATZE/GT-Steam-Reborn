package com.miaokatze.gtsr.common.gui;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.DoubleSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;
import com.miaokatze.gtsr.common.machine.MTECrustMatterAggregator;

/**
 * 地壳物质聚合器 GUI：基类 MTESingularityMachineGui 已覆盖热量/档位状态/等级行与
 * gtsr.mode/gtsr.fuelTicks 同步（getModeForGui/getFuelTicksForGui），本类补充：
 * 奇点模式行（含剩余秒数）、维度行（覆盖/默认维度 + 无维度/无矿石状态）、当前挖掘矿名、
 * 定向模式 UU 消耗行（紫色粗体，含倍率；定向关闭时灰色占位）。
 */
public class MTECrustMatterAggregatorGui extends MTESingularityMachineGui<MTECrustMatterAggregator> {

    public MTECrustMatterAggregatorGui(MTECrustMatterAggregator multiblock) {
        super(multiblock);
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        syncManager.syncValue("gtsr.dimLabel", new StringSyncValue(multiblock::getDimensionDisplayName));
        syncManager.syncValue("gtsr.dropMapValid", new BooleanSyncValue(() -> multiblock.dropMapValid));
        // 蒸汽消耗倍率（矿石模式/时运/维度槽/过滤加成，S2C 驱动蒸汽消耗词条）
        syncManager.syncValue("gtsr.steamMult", new DoubleSyncValue(() -> multiblock.getSteamMultiplier()));
        // 定向模式开关与 UU 倍率（S2C 驱动 UU 物质消耗词条）
        syncManager.syncValue("gtsr.directionalMode", new BooleanSyncValue(() -> multiblock.getDirectionalMode()));
        syncManager.syncValue("gtsr.uuMult", new DoubleSyncValue(() -> multiblock.getUUMultiplier()));
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager syncManager, ModularPanel parent) {
        ListWidget<IWidget, ?> widget = super.createTerminalTextWidget(syncManager, parent);
        String keyPrefix = multiblock.getGuiKeyPrefix();
        IntSyncValue modeSyncer = syncManager.findSyncHandler("gtsr.mode", IntSyncValue.class);
        IntSyncValue fuelSyncer = syncManager.findSyncHandler("gtsr.fuelTicks", IntSyncValue.class);
        StringSyncValue dimSyncer = syncManager.findSyncHandler("gtsr.dimLabel", StringSyncValue.class);
        BooleanSyncValue validSyncer = syncManager.findSyncHandler("gtsr.dropMapValid", BooleanSyncValue.class);
        DoubleSyncValue steamMultSyncer = syncManager.findSyncHandler("gtsr.steamMult", DoubleSyncValue.class);
        BooleanSyncValue directionalSync = syncManager.findSyncHandler("gtsr.directionalMode", BooleanSyncValue.class);
        DoubleSyncValue uuMultSyncer = syncManager.findSyncHandler("gtsr.uuMult", DoubleSyncValue.class);

        return widget.child(IKey.dynamic(() -> {
            int mode = modeSyncer.getValue();
            String modeKey = mode == 2 ? "mode.critical" : mode == 1 ? "mode.steam" : "mode.off";
            return EnumChatFormatting.YELLOW + StatCollector.translateToLocal(keyPrefix + "mode")
                + EnumChatFormatting.GOLD
                + StatCollector.translateToLocal(keyPrefix + modeKey)
                + EnumChatFormatting.RESET;
        })
            .asWidget()
            .marginBottom(2)
            .fullWidth())
            .child(IKey.dynamic(() -> {
                String value = fuelSyncer.getValue() > 0 ? String.format("%ds", fuelSyncer.getValue() / 20)
                    : StatCollector.translateToLocal(keyPrefix + "fuel_no_fuel");
                return EnumChatFormatting.YELLOW + StatCollector.translateToLocal(keyPrefix + "fuel_time")
                    + EnumChatFormatting.RED
                    + value
                    + EnumChatFormatting.RESET;
            })
                .asWidget()
                .marginBottom(2)
                .fullWidth())
            .child(IKey.dynamic(() -> {
                String dimLabel = dimSyncer.getValue();
                String dimPart;
                if (dimLabel == null || dimLabel.isEmpty() || "None".equals(dimLabel)) {
                    dimPart = EnumChatFormatting.RED + StatCollector.translateToLocal(keyPrefix + "no_dimension");
                } else if (!validSyncer.getValue()) {
                    dimPart = EnumChatFormatting.RED + StatCollector.translateToLocal(keyPrefix + "no_ores");
                } else {
                    dimPart = EnumChatFormatting.GREEN + dimLabel;
                }
                return EnumChatFormatting.YELLOW + StatCollector.translateToLocal(keyPrefix + "dimension")
                    + dimPart
                    + EnumChatFormatting.RESET;
            })
                .asWidget()
                .marginBottom(2)
                .fullWidth())
            .child(IKey.dynamic(() -> {
                // 蒸汽消耗词条：普通档基准 24000 L/s × 当前倍率（矿石模式/时运/维度槽/过滤加成）
                double mult = steamMultSyncer.getValue();
                long perSecond = Math.round(MTECrustMatterAggregator.NORMAL_STEAM_PER_SECOND * mult);
                return EnumChatFormatting.YELLOW + StatCollector.translateToLocal(keyPrefix + "steam_cost")
                    + EnumChatFormatting.WHITE
                    + NumberFormatUtil.formatNumber(perSecond)
                    + " L/s "
                    + EnumChatFormatting.GRAY
                    + String.format(
                        StatCollector.translateToLocal(keyPrefix + "steam_cost.mult"),
                        String.format("%.2f", mult))
                    + EnumChatFormatting.RESET;
            })
                .asWidget()
                .marginBottom(2)
                .fullWidth())
            .child(IKey.dynamic(() -> {
                // 定向模式 UU 物质消耗词条：定向开启时显示速率与倍率（紫色粗体），关闭时显示灰色占位。
                // 速率 = 1 L/s × 倍率，与倍率数值恒等（UU 基础 1L/s），直接用同步值避免客户端字段陈旧。
                if (directionalSync.getValue()) {
                    double mult = uuMultSyncer.getValue();
                    return EnumChatFormatting.YELLOW + StatCollector.translateToLocal(keyPrefix + "uu_cost")
                        + EnumChatFormatting.LIGHT_PURPLE
                        + EnumChatFormatting.BOLD
                        + NumberFormatUtil.formatNumber(Math.round(mult))
                        + " L/s "
                        + String.format(
                            StatCollector.translateToLocal(keyPrefix + "uu_cost.mult"),
                            String.format("%.2f", mult))
                        + EnumChatFormatting.RESET;
                }
                return EnumChatFormatting.YELLOW + StatCollector.translateToLocal(keyPrefix + "uu_cost")
                    + EnumChatFormatting.GRAY
                    + StatCollector.translateToLocal(keyPrefix + "uu_cost.off")
                    + EnumChatFormatting.RESET;
            })
                .asWidget()
                .marginBottom(2)
                .fullWidth());
    }
}
