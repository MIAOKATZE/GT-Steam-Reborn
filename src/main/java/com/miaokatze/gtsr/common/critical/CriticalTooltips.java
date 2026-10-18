package com.miaokatze.gtsr.common.critical;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import gregtech.api.util.MultiblockTooltipBuilder;

/** Standard GT tooltips with operating rules specific to each released machine. */
public final class CriticalTooltips {

    private CriticalTooltips() {}

    public static MultiblockTooltipBuilder create(CriticalMachineKind preset) {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(tr(preset == null ? "v2.loom_type" : "kind." + preset.key));
        if (preset != null && !preset.isReleased()) {
            tt.addInfo(EnumChatFormatting.YELLOW + tr("tooltip.frozen"))
                .addInfo(tr("tooltip.unobtainable"));
            tt.toolTipFinisher("GTSteamReborn");
            return tt;
        }
        if (preset == null) {
            tt.addInfo(tr("tooltip.selection"))
                .addInfo(EnumChatFormatting.AQUA + tr("tooltip.loom_only"));
        } else {
            tt.addInfo(EnumChatFormatting.AQUA + tr("operation." + preset.key + ".input"))
                .addInfo(tr("operation." + preset.key + ".cycle"))
                .addInfo(tr("operation." + preset.key + ".parallel"))
                .addInfo(EnumChatFormatting.YELLOW + tr("operation." + preset.key + ".energy"))
                .addInfo(tr("operation." + preset.key + ".efficiency"))
                .addInfo(EnumChatFormatting.GREEN + tr("operation." + preset.key + ".output"));
            if (preset == CriticalMachineKind.BATTERY || preset == CriticalMachineKind.TURBINE) {
                tt.addInfo(tr("operation.trend"))
                    .addInfo(tr("operation.idle"));
            }
            if (preset == CriticalMachineKind.SOLAR) tt.addInfo(tr("operation.solar.night"));
        }
        tt.addInfo(tr("functional.decorative"))
            .addInfo(tr("tooltip.unobtainable"))
            .addInfo(tr("management_hint"))
            .addSeparator();
        if (preset == null || CriticalMachineState.supportsV2Kind(preset)) {
            v2Structure(tt, preset);
        } else {
            legacyStructure(tt);
        }
        tt.toolTipFinisher("GTSteamReborn");
        return tt;
    }

    private static void v2Structure(MultiblockTooltipBuilder tt, CriticalMachineKind preset) {
        tt.addInfo(tr("v2.shared"))
            .addInfo(tr("v2.loom"))
            .addInfo(tr("v2.safe"))
            .addInfo(tr("v2.dismantle"))
            .addInfo(tr("v2.legacy"))
            .beginStructureBlock(112, 180, 112, false)
            .addController(tr(preset == null ? "v2.loom_controller" : "v2.main_controller"))
            .addStructureInfo(tr("v2.region"))
            .addStructureInfo(tr("v2.controllers"))
            .addInputBus(tr("v2.ports"))
            .addInputHatch(tr("v2.ports"))
            .addOutputBus(tr("v2.ports"))
            .addOutputHatch(tr("v2.ports"))
            .addEnergyHatch(tr("v2.ports"))
            .addStructureInfo(tr("v2.loom_ports"))
            .addStructureInfo(tr("v2.main_ports"))
            .addStructureInfo(tr("tooltip.physical_inputs"))
            .addStructureInfo(tr("tooltip.port_hint"));
    }

    private static void legacyStructure(MultiblockTooltipBuilder tt) {
        tt.addInfo(tr("tooltip.loom_build"))
            .addInfo(tr("tooltip.loom_preview"))
            .addInfo(tr("tooltip.build"))
            .addInfo(tr("tooltip.safe"))
            .beginStructureBlock(80, 162, 80, false)
            .addController(tr("tooltip.controller"))
            .addInputBus(tr("tooltip.ports"))
            .addInputHatch(tr("tooltip.ports"))
            .addOutputHatch(tr("tooltip.ports"))
            .addEnergyHatch(tr("tooltip.ports"))
            .addStructureInfo(tr("tooltip.loom_bounds"))
            .addStructureInfo(tr("tooltip.loom_hatches"))
            .addStructureInfo(tr("tooltip.bounds"))
            .addStructureInfo(tr("tooltip.material"))
            .addStructureInfo(tr("tooltip.hatches"))
            .addStructureInfo(tr("tooltip.loom_energy"))
            .addStructureInfo(tr("tooltip.physical_inputs"))
            .addStructureInfo(tr("tooltip.port_hint"));
    }

    private static String tr(String key) {
        return StatCollector.translateToLocal("gtsr.critical." + key);
    }
}
