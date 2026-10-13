package com.miaokatze.gtsr.common.critical;

import net.minecraft.util.StatCollector;

import gregtech.api.util.MultiblockTooltipBuilder;

/** Shared standard GT tooltip for the loom and all fixed machine variants. */
public final class CriticalTooltips {

    private CriticalTooltips() {}

    public static MultiblockTooltipBuilder create(CriticalMachineKind preset) {
        if (preset == null || preset == CriticalMachineKind.SUN || preset == CriticalMachineKind.SOLAR)
            return v2(preset);
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(tr(preset == null ? "type" : "kind." + preset.key))
            .addInfo(tr("tooltip.selection"))
            .addInfo(tr("tooltip.tiers"))
            .addInfo(tr("tooltip.plugins"))
            .addInfo(tr("tooltip.recipe"))
            .addInfo(tr("tooltip.loom_build"))
            .addInfo(tr("tooltip.loom_preview"))
            .addInfo(tr("tooltip.build"))
            .addInfo(tr("tooltip.safe"))
            .addSeparator()
            .beginStructureBlock(preset == null ? 15 : 80, preset == null ? 36 : 162, preset == null ? 15 : 80, false)
            .addController(tr("tooltip.controller"))
            .addInputBus(tr("tooltip.ports"))
            .addInputHatch(tr("tooltip.ports"))
            .addEnergyHatch(tr("tooltip.ports"))
            .addStructureInfo(tr("tooltip.loom_bounds"))
            .addStructureInfo(tr("tooltip.loom_hatches"))
            .addStructureInfo(tr("tooltip.bounds"))
            .addStructureInfo(tr("tooltip.material"))
            .addStructureInfo(tr("tooltip.hatches"))
            .addStructureInfo(tr("tooltip.optional_ports"))
            .addStructureInfo(tr("tooltip.loom_energy"))
            .addStructureInfo(tr("tooltip.physical_inputs"));
        if (preset == CriticalMachineKind.BATTERY || preset == CriticalMachineKind.TURBINE
            || preset == CriticalMachineKind.SUN
            || preset == CriticalMachineKind.ACCELERATOR)
            tt.addDynamoHatch(tr("tooltip.ports"))
                .addStructureInfo(tr("tooltip.dynamo"));
        tt.addStructureHint("gtsr.critical.tooltip.port_hint", 1);
        tt.toolTipFinisher("GTSteamReborn");
        return tt;
    }

    private static MultiblockTooltipBuilder v2(CriticalMachineKind preset) {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(tr(preset == null ? "v2.loom_type" : "kind." + preset.key))
            .addInfo(tr("v2.shared"))
            .addInfo(tr("v2.tier"))
            .addInfo(tr("tooltip.plugins"))
            .addInfo(tr("tooltip.recipe"))
            .addInfo(tr("v2.loom"))
            .addInfo(tr("v2.safe"))
            .addInfo(tr("v2.dismantle"))
            .addInfo(tr("v2.legacy"))
            .addSeparator()
            .beginStructureBlock(112, 180, 112, false)
            .addController(tr(preset == null ? "v2.loom_controller" : "v2.main_controller"))
            .addStructureInfo(tr("v2.region"))
            .addStructureInfo(tr("v2.controllers"))
            .addInputBus(tr("v2.ports"))
            .addInputHatch(tr("v2.ports"))
            .addEnergyHatch(tr("v2.ports"))
            .addDynamoHatch(tr("v2.ports"))
            .addStructureInfo(tr("v2.loom_ports"))
            .addStructureInfo(tr("v2.main_ports"))
            .addStructureInfo(tr("tooltip.physical_inputs"));
        tt.addStructureHint("gtsr.critical.tooltip.port_hint", 1);
        tt.toolTipFinisher("GTSteamReborn");
        return tt;
    }

    private static String tr(String key) {
        return StatCollector.translateToLocal("gtsr.critical." + key);
    }
}
