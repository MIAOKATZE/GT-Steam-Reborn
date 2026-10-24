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
        List<String> overview = switch (entry) {
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
            case GearSteamCompressor -> gearOverview(primary);
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
            case SteamCacheNode, ReinforcedSteamCacheNode, OverpressureSteamCacheNode -> profile(
                fields,
                "steam_cache",
                "shared.fluid_type",
                "shared.capacity",
                "shared.output_rate",
                "shared.singularity_cost",
                "overpressure_steam_cache_node.singularity_cost",
                "overpressure_steam_cache_node.bind_requirement",
                "shared.bind_hint");
            case WaterCacheNode, ReinforcedWaterCacheNode, OverpressureWaterCacheNode -> profile(
                fields,
                "fluid_cache",
                "shared.fluid_type",
                "shared.capacity",
                "shared.output_rate",
                "water_cache_node.bind_target",
                "overpressure_water_cache_node.bind_requirement",
                "shared.bind_hint");
            case SingularitySteamCompartment, SingularitySteamOutputCompartment -> compartment(
                additional,
                "steam_compartment");
            case SingularityFluidInputCompartment, SingularityFluidOutputCompartment -> compartment(
                additional,
                "fluid_compartment");
            case SingularityMinerNode -> profile(
                fields,
                "miner_node",
                "miner_node.table_header",
                "+~miner_node.row_0_params",
                "+~miner_node.row_4_params");
            case SingularityDrillingNode -> profile(
                fields,
                "drilling_node",
                "drilling_node.table_header",
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
        return GTSRMachineTooltipNotes.append(entry, enrich(entry, overview, primary, structure, fields), fields);
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

    private static List<String> compartment(List<String> additional, String purpose) {
        // These fields belong to the compartment, not the similar GT hatch it inherits from.
        return profile(
            additional,
            purpose,
            "singularity_compartment.mode",
            "shared.capacity",
            "shared.output_rate",
            "shared.fluid_type",
            "singularity_compartment.bind_target_steam",
            "singularity_compartment.bind_target_fluid",
            "singularity_compartment.bind_cost_one",
            "singularity_compartment.bind_cost_free",
            "shared.bind_hint");
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
            // Native labels, values, warnings and the cluster's process colors are one authored unit.
            if (!duplicate) result.add(line);
        }
    }

    private static List<String> nativeSummary(List<String> source) {
        List<String> result = new ArrayList<>();
        for (String line : source) {
            if (!GTSRMachineTooltips.isBrand(line) && !GTSRMachineTooltips.isOldShiftHint(line)) result.add(line);
        }
        return result;
    }

    private static List<String> gearOverview(List<String> primary) {
        List<String> result = new ArrayList<>();
        int tier = 0;
        for (String row : nativeSummary(primary)) {
            // Both native consumption labels have the same translation. Their authored source order is
            // bronze then steel; keep each consumption/output/water triple together rather than regrouping
            // equal labels and making the six rates ambiguous.
            if (matchesKey(row, TOOLTIP + "gear_compressor.bronze_steam_in")) {
                result.add(
                    EnumChatFormatting.YELLOW
                        + StatCollector.translateToLocal(tier++ == 0 ? "gtsr.gui.tier.bronze" : "gtsr.gui.tier.steel"));
            }
            result.add(row);
        }
        return result;
    }

    /**
     * Per-machine reading order: native machine type, purpose/process, native parameters, operating limits.
     * A vertical bar marks a semantic paragraph, never an information quota. The selected source strings keep
     * their original GT colors and values; the condensed critical arithmetic continues to use policy constants.
     */
    private static List<String> enrich(GTSRItemList entry, List<String> overview, List<String> primary,
        List<String> structure, List<String> fields) {
        String extra = switch (entry) {
            case SteamFluidDrill -> "fluid_drill.desc|fluid_drill.mode_switch;fluid_drill.mode_bronze_only;"
                + "shared.superheated_quadruples|structure.SteamFluidDrill.hatches";
            case CrustSteamBorer -> "crust_borer.desc;crust_borer.desc_2|structure.CrustSteamBorer.hatches";
            case CrustMatterAggregator -> "crust_matter_agg.desc2;crust_matter_agg.desc3|"
                + "crust_matter_agg.desc5;crust_matter_agg.desc5_2;crust_matter_agg.desc8;"
                + "crust_matter_agg.desc9_2";
            case VeinSteamPyrolyzer -> "vein_pyrolyzer.desc;vein_pyrolyzer.desc_2|"
                + "structure.VeinSteamPyrolyzer.hatches";
            case LargeSteamFurnace -> "steam_furnace.desc;steam_furnace.desc_2|shared.steam_cost;"
                + "structure.LargeSteamFurnace.hatches";
            case LargeSolarOverpressureArray -> "solar_array.desc2;solar_array.desc2_2|"
                + "solar_array.booster_label;solar_array.calcification_d;solar_array.clear_calcification_hint;"
                + "overpressure.enable;overpressure.effects";
            case LargeGeothermalSteamBoiler -> "geothermal_boiler.heat|geothermal_boiler.chip_info;"
                + "geothermal_boiler.chip_info_2;geothermal_boiler.lava_rate_bronze;"
                + "geothermal_boiler.lava_rate_steel;geothermal_boiler.lava_rate_chip;"
                + "geothermal_boiler.clear_calcification_hint;overpressure.effects";
            case SteamSingularityEntangler -> "entangler.desc;entangler.desc_2|entangler.desc2_2;"
                + "entangler.desc3_2;entangler.overlimit_1;entangler.overlimit_4";
            case CriticalSingularityCompressor -> "critical_singularity_compressor.desc2|"
                + "critical_singularity_compressor.desc8;critical_singularity_compressor.desc3_2;"
                + "critical_singularity_compressor.overlimit_2;critical_singularity_compressor.overlimit_4;"
                + "critical_singularity_compressor.overlimit_5";
            case DenseStateManipulator -> "dense_state_manipulator.desc3|dense_state_manipulator.desc4_2;"
                + "dense_state_manipulator.desc4_3;dense_state_manipulator.desc5;"
                + "structure.special.dense_ports";
            case SteamHubArray -> "steam_hub.desc;steam_hub.desc2;steam_hub.desc2_2|"
                + "shared.screwdriver_overflow;shared.overflow_input_desc";
            case WaterHubArray -> "water_hub.desc;water_hub.desc2;water_hub.desc2_2|"
                + "shared.screwdriver_overflow;shared.overflow_input_desc";
            case SingularityDrillingHub -> "singularity_hub.desc2;singularity_hub.desc2_2|"
                + "structure.SingularityDrillingHub.hatches";
            case LargeCokeOven -> "coke_oven.desc;coke_oven.desc_2|coke_oven.formula;coke_oven.accel;"
                + "structure.LargeCokeOven.hatches";
            case SiemensMartinFurnace -> "siemens_martin.desc_2;siemens_martin.desc2|shared.steam_cost;"
                + "siemens_martin.overheat;siemens_martin.air_preheat";
            case MegaSteamTurbineArray -> "turbine_array.steam_progression|turbine_array.stacking_desc;"
                + "turbine_array.stacking_desc_2;turbine_array.stacking_desc_3;"
                + "turbine_array.formula_2;turbine_array.singularity_mode;"
                + "turbine_array.singularity_mode_3;turbine_array.power_param";
            case KineticProcessingArray -> "kinetic_array.desc|kinetic_array.desc2_3;~kinetic_array.energy;"
                + "~kinetic_array.steam_input";
            case AmmoniaPlant -> "ammonia_plant.desc_line_2;ammonia_plant.desc_line_3|"
                + "@gtsr.ammonia.output_ports;@gtsr.ammonia.output_ports.steam;"
                + "ammonia_plant.preheat.1;ammonia_plant.catalyst;@gtsr.ammonia.pending_policy";
            case AirCompressor -> "air_compressor.desc;air_compressor.desc_2|structure.AirCompressor.hatches";
            case AtmosphericCentrifuge -> "atmospheric_centrifuge.desc;atmospheric_centrifuge.desc_2|"
                + "structure.AtmosphericCentrifuge.hatches";
            case ReinforcedBrickBlastFurnace -> "reinforced_brick_blast_furnace.desc;"
                + "reinforced_brick_blast_furnace.desc2|reinforced_brick_blast_furnace.parallel_hint;"
                + "reinforced_brick_blast_furnace.speed_hint;structure.ReinforcedBrickBlastFurnace.hatches";
            case SpacetimeCalibration -> "|~@gtsr.spacetime.energy;~@gtsr.spacetime.maintenance;"
                + "@gtsr.spacetime.optional_io";
            case TcdsController -> "tcds.desc_a;tcds.desc_b|tcds.fuel_consumption;tcds.efficiency;"
                + "tcds.air_water;tcds.fuel_threshold;tcds.chip_info";
            case CriticalLoom -> "@gtsr.critical.tooltip.loom_only|@gtsr.critical.foundation.cost;"
                + "@gtsr.critical.foundation.hatches;@gtsr.critical.v2.loom;@gtsr.critical.v2.safe;"
                + "@gtsr.critical.management_hint";
            case CriticalSolar -> "@gtsr.critical.operation.solar.input|@gtsr.critical.operation.solar.output;"
                + "@gtsr.critical.operation.solar.night;@gtsr.critical.v2.loom;@gtsr.critical.management_hint";
            case CriticalTurbine -> "@gtsr.critical.operation.turbine.input|@gtsr.critical.operation.turbine.parallel;"
                + "@gtsr.critical.operation.trend;@gtsr.critical.operation.idle;@gtsr.critical.management_hint";
            case CriticalSun -> "@gtsr.critical.operation.sun.input|@gtsr.critical.operation.sun.cycle;"
                + "@gtsr.critical.operation.sun.output;@gtsr.critical.operation.sun.pause;"
                + "@gtsr.critical.management_hint";
            case CriticalBattery -> "@gtsr.critical.operation.battery.input|@gtsr.critical.operation.battery.windows;"
                + "@gtsr.critical.operation.battery.paid;@gtsr.critical.management_hint";
            case ClusterController -> "cluster.desc|cluster.steam_gate;cluster.segments;"
                + "cluster.channel_length;cluster.preview_tier";
            case ClusterUnitCrusher, ClusterUnitOreWasher, ClusterUnitCentrifuge, ClusterUnitThermalCentrifuge, ClusterUnitSifter, ClusterUnitMagneticSeparator, ClusterUnitFurnace -> "cluster.unit.desc_2";
            case ClusterBoosterParallel -> "cluster.unit.parallel.desc|cluster.unit.parallel.link_apply";
            case ClusterBoosterSpeed -> "cluster.unit.speed.desc|cluster.unit.speed.link_apply";
            case ClusterBoosterPrimary -> "cluster.unit.primary.desc|cluster.unit.primary.link_receive";
            case ClusterBoosterSecondary -> "cluster.unit.secondary.desc|cluster.unit.secondary.link_receive";
            case ClusterBoosterSteamSaver -> "cluster.unit.steam_saver.desc|cluster.unit.steam_saver.link_receive";
            case ClusterUnitLogistics -> "cluster.unit.logistics.desc|cluster.unit.logistics.soft_hammer";
            case SingularityMinerNode -> "miner_node.desc|shared.node_bind_hint;miner_node.upgrade_1;"
                + "miner_node.crushed_mode_hint";
            case SingularityDrillingNode -> "drilling_node.desc|shared.node_bind_hint;drilling_node.upgrade_1;"
                + "drilling_node.upgrade_3";
            // Native small hatches and storage units already expose their complete 3-6 useful fields.
            // Frozen controllers must state their real unavailable status, without invented capabilities.
            default -> null;
        };
        if (extra == null) return overview;
        List<String> result = new ArrayList<>();
        if (!structure.isEmpty() && !primary.isEmpty()) result.add(primary.get(0));
        String[] paragraphs = extra.split("\\|", -1);
        boolean nativePurpose = paragraphs[0].matches(".*\\.desc(?:;.*)?") || paragraphs[0].endsWith(".input");
        if (!nativePurpose && !overview.isEmpty()) result.add(overview.get(0));
        appendFields(result, fields, paragraphs[0]);
        separator(result, primary);
        for (int i = 1; i < overview.size(); i++) addUnique(result, overview.get(i));
        for (int i = 1; i < paragraphs.length; i++) {
            separator(result, primary);
            appendFields(result, fields, paragraphs[i]);
        }
        if (entry.name()
            .startsWith("ClusterUnit")
            || entry.name()
                .startsWith("ClusterBooster")) {
            separator(result, primary);
            // Module identity includes its actual footprint and connection/energy obligations.
            if (!structure.isEmpty()) addUnique(result, structure.get(0));
            select(result, structure, TOOLTIP + "cluster.unit.hatch.input", EnumChatFormatting.GRAY);
            select(result, structure, TOOLTIP + "cluster.unit.hatch.energy", EnumChatFormatting.GRAY);
        }
        trimSeparators(result);
        return result;
    }

    private static void appendFields(List<String> result, List<String> fields, String keys) {
        for (String key : keys.split(";")) {
            if (key.isEmpty()) continue;
            boolean embedded = key.startsWith("~");
            if (embedded) key = key.substring(1);
            key = key.startsWith("@") ? key.substring(1) : TOOLTIP + key;
            select(result, fields, embedded ? "~" + key : key, EnumChatFormatting.GRAY);
        }
    }

    private static void addUnique(List<String> result, String row) {
        for (String existing : result) if (plain(existing).equals(plain(row))) return;
        result.add(row);
    }

    private static void separator(List<String> result, List<String> source) {
        if (result.isEmpty() || isSeparator(result.get(result.size() - 1))) return;
        for (String row : source) {
            if (isSeparator(row)) {
                result.add(row);
                return;
            }
        }
        result.add(" ");
    }

    private static boolean isSeparator(String row) {
        return plain(row).trim()
            .isEmpty() || plain(row).matches("-{3,}");
    }

    private static void trimSeparators(List<String> result) {
        while (!result.isEmpty() && isSeparator(result.get(result.size() - 1))) result.remove(result.size() - 1);
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
