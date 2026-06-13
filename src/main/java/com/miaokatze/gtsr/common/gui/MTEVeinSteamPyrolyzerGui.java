package com.miaokatze.gtsr.common.gui;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.ListWidget;

import gregtech.common.gui.modularui.multiblock.base.MTESteamMultiBlockBaseGui;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.MTESteamMultiBlockBase;

public class MTEVeinSteamPyrolyzerGui extends MTESteamMultiBlockBaseGui {

    public MTEVeinSteamPyrolyzerGui(MTESteamMultiBlockBase<?> multiblock) {
        super(multiblock);
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        com.miaokatze.gtsr.common.machine.MTEVeinSteamPyrolyzer machine = (com.miaokatze.gtsr.common.machine.MTEVeinSteamPyrolyzer) multiblock;
        syncManager.syncValue("gtsr.tier", new IntSyncValue(() -> machine.mSetTier));
        syncManager.syncValue("gtsr.lockedFluidName", new StringSyncValue(() -> machine.mLockedFluidName));
        syncManager.syncValue("gtsr.chipRangeBonus", new IntSyncValue(() -> machine.mChipRangeBonus));
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager syncManager, ModularPanel parent) {
        IntSyncValue tierSyncer = syncManager.findSyncHandler("gtsr.tier", IntSyncValue.class);
        StringSyncValue lockedFluidSyncer = syncManager.findSyncHandler("gtsr.lockedFluidName", StringSyncValue.class);
        IntSyncValue chipRangeSyncer = syncManager.findSyncHandler("gtsr.chipRangeBonus", IntSyncValue.class);

        return super.createTerminalTextWidget(syncManager, parent).child(IKey.dynamic(() -> {
            String tierText = tierSyncer.getValue() == 2 ? StatCollector.translateToLocal("gtsr.gui.tier.steel")
                : StatCollector.translateToLocal("gtsr.gui.tier.bronze");
            return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.tier")
                + EnumChatFormatting.GOLD
                + tierText;
        })
            .asWidget()
            .marginBottom(2)
            .fullWidth())
            .child(IKey.dynamic(() -> {
                boolean isSuperheated = ((com.miaokatze.gtsr.common.machine.MTEVeinSteamPyrolyzer) multiblock)
                    .hasSuperheatedSteamInHatch();
                return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.steam_type")
                    + EnumChatFormatting.YELLOW
                    + (isSuperheated ? StatCollector.translateToLocal("gtsr.gui.steam_type.superheated")
                        : StatCollector.translateToLocal("gtsr.gui.steam_type.normal"));
            })
                .asWidget()
                .marginBottom(2)
                .fullWidth())
            .child(IKey.dynamic(() -> {
                int chipRange = chipRangeSyncer.getValue();
                String chipText = chipRange >= 7 ? "T3(+7)"
                    : chipRange >= 3 ? "T2(+3)"
                        : chipRange >= 1 ? "T1(+1)" : StatCollector.translateToLocal("gtsr.gui.none");
                return EnumChatFormatting.YELLOW + StatCollector.translateToLocal("gtsr.gui.vein_pyrolyzer.chip")
                    + EnumChatFormatting.GREEN
                    + chipText;
            })
                .asWidget()
                .marginBottom(2)
                .fullWidth())
            .child(IKey.dynamic(() -> {
                String fluidName = lockedFluidSyncer.getValue();
                if (fluidName == null || fluidName.isEmpty()) {
                    return EnumChatFormatting.YELLOW
                        + StatCollector.translateToLocal("gtsr.gui.vein_pyrolyzer.target_fluid")
                        + EnumChatFormatting.LIGHT_PURPLE
                        + StatCollector.translateToLocal("gtsr.gui.none");
                }
                Fluid fluid = FluidRegistry.getFluid(fluidName);
                String localName = fluid != null ? fluid.getLocalizedName(new FluidStack(fluid, 0)) : fluidName;
                return EnumChatFormatting.YELLOW
                    + StatCollector.translateToLocal("gtsr.gui.vein_pyrolyzer.target_fluid")
                    + EnumChatFormatting.LIGHT_PURPLE
                    + localName;
            })
                .asWidget()
                .marginBottom(2)
                .fullWidth());
    }
}
