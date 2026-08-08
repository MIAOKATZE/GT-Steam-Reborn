package com.miaokatze.gtsr.common.gui;

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
import com.miaokatze.gtsr.common.machine.MTECrustMatterAggregator;

/**
 * 地壳物质聚合器 GUI：基类 MTESingularityMachineGui 已覆盖热量/档位状态/等级行与
 * gtsr.mode/gtsr.fuelTicks 同步（getModeForGui/getFuelTicksForGui），本类补充：
 * 奇点模式行（含剩余秒数）、维度行（覆盖/默认维度 + 无维度/无矿石状态）、当前挖掘矿名。
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
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager syncManager, ModularPanel parent) {
        ListWidget<IWidget, ?> widget = super.createTerminalTextWidget(syncManager, parent);
        String keyPrefix = multiblock.getGuiKeyPrefix();
        IntSyncValue modeSyncer = syncManager.findSyncHandler("gtsr.mode", IntSyncValue.class);
        IntSyncValue fuelSyncer = syncManager.findSyncHandler("gtsr.fuelTicks", IntSyncValue.class);
        StringSyncValue dimSyncer = syncManager.findSyncHandler("gtsr.dimLabel", StringSyncValue.class);
        BooleanSyncValue validSyncer = syncManager.findSyncHandler("gtsr.dropMapValid", BooleanSyncValue.class);

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
                .fullWidth());
    }
}
