package com.miaokatze.gtsr.common.critical;

import net.minecraft.util.StatCollector;

import gregtech.api.util.MultiblockTooltipBuilder;

/** Shared standard GT tooltip for the loom and all fixed machine variants. */
public final class CriticalTooltips {

    private CriticalTooltips() {}

    public static MultiblockTooltipBuilder create(CriticalMachineKind preset) {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(tr(preset == null ? "type" : "kind." + preset.key))
            .addInfo(tr("tooltip.selection"))
            .addInfo(tr("tooltip.tiers"))
            .addInfo(tr("tooltip.plugins"))
            .addInfo(tr("tooltip.recipe"))
            .addInfo(tr("tooltip.build"))
            .addInfo(tr("tooltip.safe"))
            .addSeparator()
            .beginStructureBlock(80, 162, 80, false)
            .addController(tr("tooltip.controller"))
            .addInputBus(tr("tooltip.ports"), 1)
            .addOutputBus(tr("tooltip.ports"), 1)
            .addInputHatch(tr("tooltip.ports"), 1)
            .addOutputHatch(tr("tooltip.ports"), 1)
            .addEnergyHatch(tr("tooltip.ports"), 1)
            .addDynamoHatch(tr("tooltip.ports"), 1)
            .addStructureInfo(tr("tooltip.bounds"))
            .addStructureInfo(tr("tooltip.material"))
            .addStructureInfo(tr("tooltip.hatches"))
            .addStructureInfo(tr("tooltip.physical_inputs"))
            .addStructureInfo(tr("tooltip.dynamo"))
            .toolTipFinisher("GTSteamReborn");
        return tt;
    }

    private static String tr(String key) {
        return StatCollector.translateToLocal("gtsr.critical." + key);
    }
}
