package com.miaokatze.gtsr.client.tooltip;

import static com.miaokatze.gtsr.client.tooltip.GTSRMachineTooltips.matchesKey;
import static com.miaokatze.gtsr.client.tooltip.GTSRMachineTooltips.plain;
import static com.miaokatze.gtsr.client.tooltip.GTSRMachineTooltips.tr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;
import com.miaokatze.gtsr.common.critical.CriticalChemicalMath;
import com.miaokatze.gtsr.common.critical.CriticalEnergyMath;
import com.miaokatze.gtsr.common.critical.CriticalStellarPolicy;

/**
 * Explicit editorial choices, not number/punctuation heuristics. Parameter rows are selected from each
 * machine's actual tooltip builder (including its structure page), so formulas and tier values have one source.
 * Small hatches keep their already concise native parameters. All original pages remain available with Shift.
 */
final class GTSRMachineTooltipProfiles {

    private static final String TOOLTIP = "gtsr.tooltip.";

    private GTSRMachineTooltipProfiles() {}

    static List<String> summary(GTSRItemList entry, List<String> primary, List<String> structure,
        List<String> additional) {
        List<String> fields = new ArrayList<>(primary);
        fields.addAll(structure);
        fields.addAll(additional);
        return switch (entry) {
            case SteamFluidDrill -> profile(
                fields,
                "fluid_drill",
                "shared.steam_cost",
                "+fluid_drill.rate_water",
                "+fluid_drill.mode_penalty");
            case CrustSteamBorer -> profile(
                fields,
                "crust_borer",
                "shared.steam_cost",
                "+shared.superheated_quadruples");
            case CrustMatterAggregator -> profile(
                fields,
                "crust_aggregator",
                "shared.steam_cost",
                "+crust_matter_agg.desc4",
                "+crust_matter_agg.desc4_2");
            case VeinSteamPyrolyzer -> profile(
                fields,
                "vein_pyrolyzer",
                "shared.steam_cost",
                "+=vein_pyrolyzer.hint_bronze",
                "+shared.superheated_quadruples");
            case LargeSteamFurnace -> profile(
                fields,
                "steam_furnace",
                "shared.parallel",
                "steam_furnace.speed",
                "+steam_furnace.steam_eff");
            case LargeSolarOverpressureArray -> profile(
                fields,
                "solar_array",
                "+~solar_array.base_output",
                "+solar_array.booster_content_3");
            case LargeGeothermalSteamBoiler -> profile(
                fields,
                "geothermal_boiler",
                "geothermal_boiler.water_info",
                "+~geothermal_boiler.max_output");
            case SteamSingularityEntangler -> profile(
                fields,
                "entangler",
                "entangler.desc2",
                "+entangler.desc4",
                "!entangler.overlimit_explode");
            case CriticalSingularityCompressor -> profile(
                fields,
                "critical_compressor",
                "critical_singularity_compressor.desc2_2",
                "+critical_singularity_compressor.desc4",
                "!critical_singularity_compressor.overlimit_explode");
            case DenseStateManipulator -> profile(
                fields,
                "dense_manipulator",
                "+dense_state_manipulator.desc2",
                "dense_state_manipulator.desc4");
            case SteamHubArray -> profile(
                fields,
                "steam_hub",
                "=steam_hub.height",
                "+steam_hub.chip_1",
                "+steam_hub.chip_2");
            case WaterHubArray -> profile(
                fields,
                "water_hub",
                "=water_hub.height",
                "+water_hub.chip_1",
                "+water_hub.chip_2");
            case SingularityDrillingHub -> profile(
                fields,
                "drilling_hub",
                "shared.steam_cost",
                "singularity_hub.node_cost_desc",
                "+singularity_hub.superheated_required");
            case LargeCokeOven -> profile(
                fields,
                "coke_oven",
                "coke_oven.parallel",
                "+coke_oven.desc2",
                "+coke_oven.desc3");
            case SiemensMartinFurnace -> profile(
                fields,
                "siemens_martin",
                "siemens_martin.air_cost",
                "siemens_martin.overheat_steam",
                "+siemens_martin.recipe_time_reduce",
                "+siemens_martin.overheat_recipe");
            case MegaSteamTurbineArray -> profile(
                fields,
                "mega_turbine",
                "turbine_array.tier_system",
                "+turbine_array.formula",
                "+turbine_array.singularity_mode_2");
            case KineticProcessingArray -> profile(
                fields,
                "kinetic_array",
                "kinetic_array.desc2",
                "+kinetic_array.desc2_2",
                "kinetic_array.desc3_2");
            case AmmoniaPlant -> profile(
                fields,
                "ammonia_plant",
                "ammonia_plant.heat_cost.1",
                "ammonia_plant.heat_cost.2",
                "+ammonia_plant.catalyst_detail.1");
            case AirCompressor -> profile(
                fields,
                "air_compressor",
                "shared.steam_cost",
                "+shared.superheated_quadruples",
                "+air_compressor.desc_3");
            case AtmosphericCentrifuge -> profile(
                fields,
                "atmospheric_centrifuge",
                "shared.steam_cost",
                "+shared.superheated_quadruples",
                "+atmospheric_centrifuge.chip_info.1");
            case GearSteamCompressor -> profile(
                fields,
                "gear_compressor",
                "gear_compressor.desc2",
                "+gear_compressor.bronze_sh_out",
                "+gear_compressor.steel_sh_out");
            case ReinforcedBrickBlastFurnace -> profile(
                fields,
                "brick_blast_furnace",
                "+reinforced_brick_blast_furnace.mechanism");
            case SpacetimeCalibration -> profile(
                fields,
                "spacetime",
                "@gtsr.spacetime.preheat",
                "@gtsr.spacetime.maintain",
                "+@gtsr.spacetime.safety");
            case TcdsController -> profile(
                fields,
                "tcds",
                "tcds.desc_3_a",
                "+tcds.output_formula",
                "+tcds.desc_2",
                "!tcds.water_warning");
            case CriticalLoom -> profile(
                fields,
                "critical_loom",
                "@gtsr.critical.foundation.cycle",
                "+@gtsr.critical.foundation.curing",
                "+@gtsr.critical.foundation.singularity");
            case CriticalSolar, CriticalTurbine, CriticalSun, CriticalBattery -> critical(entry);
            case SteamCacheNode, ReinforcedSteamCacheNode, OverpressureSteamCacheNode -> profile(fields, "steam_cache");
            case WaterCacheNode, ReinforcedWaterCacheNode, OverpressureWaterCacheNode -> profile(fields, "fluid_cache");
            case SingularitySteamCompartment, SingularitySteamOutputCompartment -> profile(fields, "steam_compartment");
            case SingularityFluidInputCompartment, SingularityFluidOutputCompartment -> profile(
                fields,
                "fluid_compartment");
            case SingularityMinerNode -> profile(
                fields,
                "miner_node",
                "+~miner_node.row_0_params",
                "+~miner_node.row_4_params");
            case SingularityDrillingNode -> profile(
                fields,
                "drilling_node",
                "+~drilling_node.row_0_params",
                "+~drilling_node.row_3_params");
            case ClusterController -> profile(
                fields,
                "cluster",
                "cluster.steam_econ",
                "+cluster.logistics",
                "+cluster.preheat");
            case ClusterUnitCrusher -> processing(fields, "crusher", "crusher.crush", "crusher.hammer");
            case ClusterUnitOreWasher -> processing(
                fields,
                "ore_washer",
                "ore_washer.ore_wash",
                "ore_washer.chem_bath",
                "ore_washer.simple_wash");
            case ClusterUnitCentrifuge -> processing(fields, "centrifuge", "centrifuge.func");
            case ClusterUnitThermalCentrifuge -> processing(
                fields,
                "thermal_centrifuge",
                "thermal_centrifuge.func",
                "thermal_centrifuge.power");
            case ClusterUnitSifter -> processing(fields, "sifter", "sifter.func");
            case ClusterUnitMagneticSeparator -> processing(
                fields,
                "magnetic_separator",
                "magnetic_separator.func",
                "magnetic_separator.power");
            case ClusterUnitFurnace -> processing(fields, "furnace", "furnace.func", "furnace.func_primary");
            case ClusterBoosterParallel -> booster(fields, "parallel");
            case ClusterBoosterSpeed -> booster(fields, "speed");
            case ClusterBoosterPrimary -> booster(fields, "primary");
            case ClusterBoosterSecondary -> booster(fields, "secondary");
            case ClusterBoosterSteamSaver -> booster(fields, "steam_saver");
            case ClusterUnitLogistics -> profile(
                fields,
                "cluster_logistics",
                "cluster.unit.logistics.segment_time",
                "cluster.unit.logistics.lubricant",
                "+cluster.unit.logistics.queue");
            // Frozen kinds and the 18 compact hatches/storage units keep every native warning and parameter.
            default -> nativeSummary(primary);
        };
    }

    /** Dense summaries use the same published business constants; full operating rules remain in Shift. */
    private static List<String> critical(GTSRItemList entry) {
        List<String> result = new ArrayList<>();
        switch (entry) {
            case CriticalSolar -> {
                formatted(result, EnumChatFormatting.GRAY, "summary.critical_solar");
                formatted(
                    result,
                    EnumChatFormatting.AQUA,
                    "compact.solar.parameters",
                    CriticalEnergyMath.CYCLE_TICKS,
                    CriticalEnergyMath.IRRADIATION_PARALLEL);
                formatted(result, EnumChatFormatting.GREEN, "compact.solar.water_salt");
                formatted(result, EnumChatFormatting.GREEN, "compact.solar.dust");
            }
            case CriticalTurbine -> {
                formatted(result, EnumChatFormatting.GRAY, "summary.critical_turbine");
                formatted(
                    result,
                    EnumChatFormatting.AQUA,
                    "compact.turbine.parameters",
                    CriticalEnergyMath.CYCLE_TICKS,
                    CriticalEnergyMath.MIN_EFFICIENCY,
                    CriticalEnergyMath.MAX_EFFICIENCY);
                formatted(
                    result,
                    EnumChatFormatting.GREEN,
                    "compact.turbine.heat",
                    CriticalEnergyMath.steamHeatPerL(0)
                        .doubleValue() / 2,
                    CriticalEnergyMath.steamHeatPerL(1)
                        .doubleValue() / 2,
                    CriticalEnergyMath.steamHeatPerL(2)
                        .doubleValue() / 2,
                    CriticalEnergyMath.steamHeatPerL(3)
                        .divide(CriticalEnergyMath.steamHeatPerL(0)));
                formatted(result, EnumChatFormatting.GREEN, "compact.turbine.output");
            }
            case CriticalSun -> {
                formatted(result, EnumChatFormatting.GRAY, "summary.critical_sun");
                formatted(
                    result,
                    EnumChatFormatting.GREEN,
                    "compact.sun.parameters",
                    CriticalStellarPolicy.MAX_PARALLEL);
                formatted(
                    result,
                    EnumChatFormatting.AQUA,
                    "compact.sun.power",
                    CriticalStellarPolicy.SUSTAIN_EUT,
                    CriticalStellarPolicy.SUSTAIN_HELIUM3_L_PER_TICK);
                formatted(
                    result,
                    EnumChatFormatting.YELLOW,
                    "compact.sun.ignition",
                    CriticalStellarPolicy.IGNITION_EU,
                    CriticalStellarPolicy.IGNITION_HELIUM3_L);
            }
            case CriticalBattery -> {
                formatted(result, EnumChatFormatting.GRAY, "summary.critical_battery");
                formatted(
                    result,
                    EnumChatFormatting.AQUA,
                    "compact.battery.parameters",
                    CriticalEnergyMath.CYCLE_TICKS,
                    CriticalChemicalMath.CHEMICAL_BASE_HEAT_CAP_EU);
                formatted(
                    result,
                    EnumChatFormatting.GREEN,
                    "compact.battery.economy",
                    CriticalChemicalMath.cap(1)
                        .multiply(java.math.BigInteger.valueOf(CriticalChemicalMath.multiplier(1))));
                formatted(
                    result,
                    EnumChatFormatting.GREEN,
                    "compact.battery.heavy",
                    CriticalChemicalMath.cap(2)
                        .multiply(java.math.BigInteger.valueOf(CriticalChemicalMath.multiplier(2))));
            }
            default -> throw new IllegalArgumentException("Not a released critical machine: " + entry);
        }
        return result;
    }

    private static void formatted(List<String> result, EnumChatFormatting color, String key, Object... values) {
        result.add(color + String.format(Locale.ROOT, tr(key), values));
    }

    private static List<String> processing(List<String> fields, String name, String... functions) {
        List<String> result = new ArrayList<>();
        select(result, fields, TOOLTIP + "cluster.unit." + name + ".desc", EnumChatFormatting.GRAY);
        select(result, fields, TOOLTIP + "cluster.unit.processing.tier_effect", EnumChatFormatting.AQUA);
        for (String function : functions) {
            select(result, fields, TOOLTIP + "cluster.unit." + function, EnumChatFormatting.GREEN);
        }
        return result;
    }

    private static List<String> booster(List<String> fields, String name) {
        List<String> result = profile(
            fields,
            "cluster_booster",
            "+cluster.unit." + name + ".value",
            "cluster.unit.booster.fluid_cost");
        select(result, fields, TOOLTIP + "cluster.unit.booster.penalty", EnumChatFormatting.YELLOW);
        select(result, fields, TOOLTIP + "cluster.unit.booster.penalty.none", EnumChatFormatting.YELLOW);
        return result;
    }

    private static List<String> profile(List<String> source, String purpose, String... fields) {
        List<String> result = new ArrayList<>();
        result.add(EnumChatFormatting.GRAY + tr("summary." + purpose));
        for (String field : fields) {
            EnumChatFormatting color = EnumChatFormatting.AQUA;
            if (field.startsWith("+")) {
                color = EnumChatFormatting.GREEN;
                field = field.substring(1);
            } else if (field.startsWith("!")) {
                color = EnumChatFormatting.RED;
                field = field.substring(1);
            }
            if (field.startsWith("=")) {
                result.add(color + StatCollector.translateToLocal(TOOLTIP + field.substring(1)));
                continue;
            }
            boolean embedded = field.startsWith("~");
            if (embedded) field = field.substring(1);
            String key = field.startsWith("@") ? field.substring(1) : TOOLTIP + field;
            if (embedded) key = "~" + key;
            select(result, source, key, color);
        }
        return result;
    }

    private static void select(List<String> result, List<String> source, String key, EnumChatFormatting color) {
        for (String line : source) {
            if (!matchesKey(line, key)) continue;
            String text = plain(line);
            boolean duplicate = false;
            for (String existing : result) if (plain(existing).equals(text)) duplicate = true;
            if (!duplicate) result.add(color + text);
        }
    }

    private static List<String> nativeSummary(List<String> source) {
        List<String> result = new ArrayList<>();
        for (String line : source) {
            if (!GTSRMachineTooltips.isBrand(line) && !GTSRMachineTooltips.isOldShiftHint(line)) result.add(line);
        }
        return result;
    }

    static List<String> nodeDocumentationKeys(GTSRItemList entry) {
        List<String> keys = new ArrayList<>();
        switch (entry) {
            case SingularityMinerNode, SingularityDrillingNode -> {
                String prefix = TOOLTIP
                    + (entry == GTSRItemList.SingularityMinerNode ? "miner_node." : "drilling_node.");
                keys.add(prefix + "table_title");
                keys.add(prefix + "table_header");
                keys.add(prefix + "upgrade_title");
                for (int i = 0; i <= 4; i++) {
                    keys.add(prefix + "upgrade_" + i);
                }
                keys.add(TOOLTIP + "shared.node_bind_hint");
                keys.add(TOOLTIP + "shared.bind_all_hint");
                keys.add(prefix + "crushed_mode_hint");
            }
            case SteamCacheNode, ReinforcedSteamCacheNode, OverpressureSteamCacheNode, WaterCacheNode, ReinforcedWaterCacheNode, OverpressureWaterCacheNode, SingularitySteamCompartment, SingularitySteamOutputCompartment, SingularityFluidInputCompartment, SingularityFluidOutputCompartment -> {
                Collections.addAll(
                    keys,
                    TOOLTIP + "cache_node.chip_adjust",
                    TOOLTIP + "shared.bind_kept_on_drop",
                    TOOLTIP + "shared.cache_node_standalone",
                    TOOLTIP + "shared.cache_node_hub_transfer",
                    TOOLTIP + "shared.bind_hint",
                    TOOLTIP + "shared.bind_all_hint",
                    TOOLTIP + "singularity_compartment.mode_locked");
            }
            default -> {}
        }
        return keys;
    }

    static boolean isNodeDocumentationLine(GTSRItemList entry, String line, List<String> keys) {
        String text = plain(line);
        for (String key : keys) {
            if (StatCollector.canTranslate(key) && text.equals(plain(StatCollector.translateToLocal(key)))) return true;
        }
        if (entry == GTSRItemList.SingularityMinerNode || entry == GTSRItemList.SingularityDrillingNode) {
            String prefix = TOOLTIP + (entry == GTSRItemList.SingularityMinerNode ? "miner_node." : "drilling_node.");
            for (int i = 0; i <= 4; i++) {
                String level = prefix + "row_" + i + "_level";
                String params = prefix + "row_" + i + "_params";
                if (StatCollector.canTranslate(level) && StatCollector.canTranslate(params)
                    && text.equals(
                        StatCollector.translateToLocal(level) + " | " + StatCollector.translateToLocal(params))) {
                    return true;
                }
            }
        }
        return false;
    }
}
