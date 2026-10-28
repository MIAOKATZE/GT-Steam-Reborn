package com.miaokatze.gtsr.common.critical;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.miaokatze.gtsr.common.util.GTSRUtils;

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
            tt.addInfo(GTSRUtils.getAddedByLine())
                .toolTipFinisher();
            return tt;
        }
        if (preset == null) {
            loom(tt);
        } else {
            switch (preset) {
                case SOLAR -> solar(tt);
                case TURBINE -> turbine(tt);
                case SUN -> sun(tt);
                case BATTERY -> battery(tt);
                default -> throw new IllegalArgumentException("Unreleased critical machine: " + preset);
            }
        }
        if (preset != null) {
            tt.addSeparator()
                .addInfo(EnumChatFormatting.BOLD + tr("tooltip.native.construction"));
        }
        if (preset == null || CriticalMachineState.supportsV2Kind(preset)) {
            v2Structure(tt, preset);
        } else {
            legacyStructure(tt);
        }
        tt.addInfo(GTSRUtils.getAddedByLine())
            .toolTipFinisher();
        return tt;
    }

    private static void loom(MultiblockTooltipBuilder tt) {
        info(tt, "loom.purpose");
        info(tt, "loom.machines");
        tt.addSeparator();
        info(tt, "loom.manage");
        info(tt, "loom.ports");
    }

    private static void solar(MultiblockTooltipBuilder tt) {
        info(tt, "solar.purpose");
        info(tt, "solar.water");
        info(tt, "solar.salt");
        info(tt, "solar.plasma");
        info(tt, "cycle");
        info(tt, "solar.power_off");
        tt.addSeparator();
        info(tt, "solar.steam_amount");
        info(tt, "solar.salt_amount");
        info(tt, "solar.plasma_amount");
        info(tt, "solar.stock");
        info(tt, "solar.private");
        info(tt, "solar.heat_steam");
        info(tt, "solar.heat_converted");
        info(tt, "solar.dimension");
        tt.addSeparator();
        info(tt, "solar.media_fixed");
        info(tt, "solar.coolant");
        for (CriticalSolarPolicy.Entry entry : CriticalSolarPolicy.COOLANTS)
            policyEntry(tt, "coolant", entry, EnumChatFormatting.AQUA);
        tt.addSeparator();
        info(tt, "solar.excitation");
        for (CriticalSolarPolicy.Entry entry : CriticalSolarPolicy.EXCITERS)
            policyEntry(tt, "exciter", entry, EnumChatFormatting.LIGHT_PURPLE);
        tt.addSeparator();
        info(tt, "solar.example_water_combination");
        info(tt, "solar.example_water_output");
        info(tt, "solar.example_salt_output");
    }

    private static void turbine(MultiblockTooltipBuilder tt) {
        info(tt, "turbine.input");
        info(tt, "turbine.output");
        info(tt, "cycle");
        tt.addSeparator();
        info(tt, "turbine.heat");
        info(tt, "turbine.generation");
        info(tt, "turbine.example");
        tt.addSeparator();
        info(tt, "turbine.efficiency");
        info(tt, "turbine.load");
        info(tt, "turbine.warmup");
        info(tt, "turbine.idle");
        info(tt, "turbine.water");
    }

    private static void sun(MultiblockTooltipBuilder tt) {
        info(tt, "sun.purpose");
        info(tt, "cycle");
        info(tt, "sun.parallel");
        tt.addSeparator();
        info(tt, "sun.ignition");
        info(tt, "sun.ignition_time");
        info(tt, "sun.sustain");
        info(tt, "sun.pause");
        info(tt, "sun.extinguish");
        info(tt, "sun.plasma");
        tt.addSeparator();
        info(tt, "sun.discount");
        info(tt, "sun.power");
        info(tt, "sun.example");
        info(tt, "sun.limits");
        info(tt, "sun.switch");
    }

    private static void battery(MultiblockTooltipBuilder tt) {
        info(tt, "battery.purpose");
        info(tt, "battery.input");
        info(tt, "battery.output");
        info(tt, "cycle");
        tt.addSeparator();
        info(tt, "battery.compounds");
        info(tt, "battery.ratio");
        info(tt, "battery.scaling");
        tt.addSeparator();
        info(tt, "battery.quality");
        info(tt, "battery.efficiency");
        info(tt, "battery.heat");
        info(tt, "battery.example");
    }

    private static void info(MultiblockTooltipBuilder tt, String key) {
        tt.addInfo(EnumChatFormatting.GRAY + tr("tooltip.native." + key));
    }

    private static void policyEntry(MultiblockTooltipBuilder tt, String group, CriticalSolarPolicy.Entry entry,
        EnumChatFormatting color) {
        tt.addInfo(
            EnumChatFormatting.GRAY + formatted(
                "solar.medium",
                color + tr("solar." + group + "." + entry.id) + EnumChatFormatting.GRAY,
                java.math.BigDecimal.valueOf(entry.multiplier)
                    .stripTrailingZeros()
                    .toPlainString(),
                String.format(java.util.Locale.ROOT, "%,d", (long) entry.litresPerSecond)));
    }

    private static String formatted(String key, Object... values) {
        return StatCollector.translateToLocalFormatted("gtsr.critical.tooltip.native." + key, values);
    }

    private static void v2Structure(MultiblockTooltipBuilder tt, CriticalMachineKind preset) {
        tt.beginStructureBlock(112, 180, 112, false)
            .addController(tr(preset == null ? "v2.loom_controller" : "v2.main_controller"))
            .addStructureInfo(tr("tooltip.native.structure.region"))
            .addStructureInfo(tr("tooltip.native.structure.base"))
            .addInputBus(tr("v2.ports"))
            .addInputHatch(tr("v2.ports"))
            .addOutputBus(tr("v2.ports"))
            .addOutputHatch(tr("v2.ports"))
            .addEnergyHatch(tr("v2.ports"));
        if (preset == null) {
            tt.addStructureInfo(tr("tooltip.native.structure.loom_ports"));
        } else {
            tt.addStructureInfo(tr("tooltip.native.structure.main_ports"))
                .addStructureInfo(tr("tooltip.native.structure.main_ports_span"));
        }
        tt.addStructureInfo(tr("tooltip.physical_inputs"))
            .addStructureInfo(tr("tooltip.native.structure.hatches"));
    }

    private static void legacyStructure(MultiblockTooltipBuilder tt) {
        tt.beginStructureBlock(80, 162, 80, false)
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
