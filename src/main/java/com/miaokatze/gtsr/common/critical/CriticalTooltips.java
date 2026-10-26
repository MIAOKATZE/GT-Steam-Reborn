package com.miaokatze.gtsr.common.critical;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.miaokatze.gtsr.api.recipe.CriticalRecipeMaps;
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
        construction(tt);
        tt.addSeparator()
            .addInfo(EnumChatFormatting.LIGHT_PURPLE + tr("functional.decorative"))
            .addInfo(EnumChatFormatting.AQUA + tr("management_hint"));
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
        info(tt, EnumChatFormatting.WHITE, "loom.purpose");
        info(tt, EnumChatFormatting.AQUA, "loom.machines");
        info(tt, EnumChatFormatting.YELLOW, "loom.manage");
        info(tt, EnumChatFormatting.GREEN, "loom.ports");
    }

    private static void solar(MultiblockTooltipBuilder tt) {
        info(tt, EnumChatFormatting.WHITE, "solar.purpose");
        info(tt, EnumChatFormatting.AQUA, "solar.water");
        info(tt, EnumChatFormatting.GOLD, "solar.salt");
        info(tt, EnumChatFormatting.LIGHT_PURPLE, "solar.dust");
        info(tt, EnumChatFormatting.YELLOW, "cycle");
        info(tt, EnumChatFormatting.AQUA, "solar.coolant");
        info(tt, EnumChatFormatting.LIGHT_PURPLE, "solar.excitation");
        for (CriticalSolarPolicy.Entry entry : CriticalSolarPolicy.COOLANTS)
            policyEntry(tt, "coolant", entry, EnumChatFormatting.AQUA);
        for (CriticalSolarPolicy.Entry entry : CriticalSolarPolicy.EXCITERS)
            policyEntry(tt, "exciter", entry, EnumChatFormatting.LIGHT_PURPLE);
        tt.addInfo(
            EnumChatFormatting.GOLD + formatted(
                "solar.rated",
                CriticalSolarPolicy.BASE_AMPS_MAX,
                CriticalSolarPolicy.MAX_WATER_AMPS_MAX,
                CriticalSolarPolicy.MAX_SALT_AMPS_MAX));
        tt.addInfo(
            EnumChatFormatting.YELLOW + formatted("solar.private", CriticalSolarPolicy.PRIVATE_DIMENSION_FACTOR));
        info(tt, EnumChatFormatting.RED, "solar.dimension");
        tt.addSeparator();
        info(tt, EnumChatFormatting.AQUA, "solar.steam");
        info(tt, EnumChatFormatting.GOLD, "solar.dense");
        info(tt, EnumChatFormatting.GREEN, "solar.inventory");
        info(tt, EnumChatFormatting.LIGHT_PURPLE, "solar.parallel");
        info(tt, EnumChatFormatting.YELLOW, "solar.energy");
        info(tt, EnumChatFormatting.GREEN, "solar.helium");
        info(tt, EnumChatFormatting.GOLD, "solar.limits");
    }

    private static void turbine(MultiblockTooltipBuilder tt) {
        info(tt, EnumChatFormatting.WHITE, "turbine.purpose");
        info(tt, EnumChatFormatting.AQUA, "turbine.input");
        info(tt, EnumChatFormatting.GREEN, "turbine.output");
        info(tt, EnumChatFormatting.YELLOW, "cycle");
        info(tt, EnumChatFormatting.GOLD, "turbine.efficiency");
        info(tt, EnumChatFormatting.LIGHT_PURPLE, "turbine.parallel");
        tt.addSeparator();
        info(tt, EnumChatFormatting.GOLD, "turbine.heat");
        info(tt, EnumChatFormatting.AQUA, "turbine.dense");
        info(tt, EnumChatFormatting.GREEN, "turbine.warmup");
        info(tt, EnumChatFormatting.RED, "turbine.stable");
        info(tt, EnumChatFormatting.YELLOW, "turbine.load");
        info(tt, EnumChatFormatting.RED, "turbine.idle");
        info(tt, EnumChatFormatting.AQUA, "turbine.water");
        info(tt, EnumChatFormatting.LIGHT_PURPLE, "turbine.remainders");
    }

    private static void sun(MultiblockTooltipBuilder tt) {
        info(tt, EnumChatFormatting.WHITE, "sun.purpose");
        info(tt, EnumChatFormatting.AQUA, "sun.recipe");
        info(tt, EnumChatFormatting.YELLOW, "cycle");
        info(tt, EnumChatFormatting.GOLD, "sun.parallel");
        info(tt, EnumChatFormatting.RED, "sun.ignition");
        tt.addInfo(
            EnumChatFormatting.LIGHT_PURPLE + formatted(
                "sun.ignition_time",
                CriticalStellarPolicy.IGNITION_HELIUM3_L / CriticalStellarPolicy.DURATION_TICKS));
        tt.addInfo(
            EnumChatFormatting.RED + formatted(
                "sun.sustain",
                CriticalStellarPolicy.SUSTAIN_EUT,
                CriticalStellarPolicy.SUSTAIN_HELIUM3_L_PER_TICK));
        info(tt, EnumChatFormatting.GREEN, "sun.output");
        tt.addSeparator();
        info(tt, EnumChatFormatting.GOLD, "sun.discount");
        info(tt, EnumChatFormatting.AQUA, "sun.limits");
        info(tt, EnumChatFormatting.GREEN, "sun.switch");
        info(tt, EnumChatFormatting.YELLOW, "sun.pause");
        info(tt, EnumChatFormatting.RED, "sun.extinguish");
        info(tt, EnumChatFormatting.LIGHT_PURPLE, "sun.plasma");
    }

    private static void battery(MultiblockTooltipBuilder tt) {
        info(tt, EnumChatFormatting.WHITE, "battery.purpose");
        info(tt, EnumChatFormatting.AQUA, "battery.input");
        info(tt, EnumChatFormatting.GREEN, "battery.output");
        info(tt, EnumChatFormatting.YELLOW, "battery.cycle");
        info(tt, EnumChatFormatting.GOLD, "battery.compounds");
        info(tt, EnumChatFormatting.LIGHT_PURPLE, "battery.efficiency");
        tt.addSeparator();
        info(tt, EnumChatFormatting.AQUA, "battery.ratio");
        info(tt, EnumChatFormatting.GREEN, "battery.scaling");
        info(tt, EnumChatFormatting.YELLOW, "battery.heat");
        info(tt, EnumChatFormatting.GREEN, "battery.unused");
    }

    private static void construction(MultiblockTooltipBuilder tt) {
        tt.addSeparator();
        info(tt, EnumChatFormatting.YELLOW, "foundation.title");
        info(tt, EnumChatFormatting.AQUA, "foundation.pour");
        info(tt, EnumChatFormatting.RED, "foundation.heat");
        info(tt, EnumChatFormatting.LIGHT_PURPLE, "foundation.rest");
        tt.addSeparator();
        info(tt, EnumChatFormatting.YELLOW, "body.title");
        info(tt, EnumChatFormatting.AQUA, "body.build");
        info(tt, EnumChatFormatting.GREEN, "body.materials");
        info(tt, EnumChatFormatting.RED, "body.dismantle");
        tt.addSeparator();
        info(tt, EnumChatFormatting.YELLOW, "equipment.title");
        info(tt, EnumChatFormatting.AQUA, "equipment.ports");
        info(tt, EnumChatFormatting.LIGHT_PURPLE, "equipment.modules");
        info(tt, EnumChatFormatting.GREEN, "equipment.terminal");
    }

    private static void info(MultiblockTooltipBuilder tt, EnumChatFormatting color, String key) {
        tt.addInfo(color + tr("tooltip.native." + key));
    }

    private static void policyEntry(MultiblockTooltipBuilder tt, String group, CriticalSolarPolicy.Entry entry,
        EnumChatFormatting color) {
        tt.addInfo(
            color + formatted(
                "solar.medium",
                tr("solar." + group + "." + entry.id),
                entry.multiplier,
                CriticalRecipeMaps.SOLAR_MEDIUM_REFERENCE_AMPS,
                new java.math.BigDecimal(
                    CriticalSolarPolicy.costLitres(entry, CriticalRecipeMaps.solarMediumReferenceEU()))
                        .divide(java.math.BigDecimal.valueOf(CriticalEnergyMath.CYCLE_TICKS / 20))
                        .stripTrailingZeros()
                        .toPlainString()));
    }

    private static String formatted(String key, Object... values) {
        return StatCollector.translateToLocalFormatted("gtsr.critical.tooltip.native." + key, values);
    }

    private static void v2Structure(MultiblockTooltipBuilder tt, CriticalMachineKind preset) {
        tt.beginStructureBlock(112, 180, 112, false)
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
