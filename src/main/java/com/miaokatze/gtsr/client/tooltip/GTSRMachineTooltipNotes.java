package com.miaokatze.gtsr.client.tooltip;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.EnumChatFormatting;

import com.miaokatze.gtsr.common.api.enums.GTSRItemList;

/** Operating notes checked against each port's actual fill/drain, tick and interaction methods. */
final class GTSRMachineTooltipNotes {

    private GTSRMachineTooltipNotes() {}

    static List<String> append(GTSRItemList entry, List<String> overview, List<String> source) {
        String notes = switch (entry) {
            case SteamHubInputHatch -> "hub_input.free;hub_input.full;hub_input.containers;hub_input.manage";
            case WaterHubInputHatch -> "hub_input.free;hub_input.full;hub_input.containers;hub_input.manage;hub_water.type";
            case SteamHubOutputHatch -> "hub_output.formed;hub_output.live;hub_output.capacity;hub_output.pipe;"
                + "hub_output.threshold;hub_output.saved";
            case WaterHubOutputHatch -> "hub_output.formed;hub_output.live;hub_output.capacity;hub_output.pipe;"
                + "hub_output.threshold;hub_water.type;hub_output.saved";
            case HubStorageUnit -> "storage.position;storage.sum;storage.passive;storage.inventory;storage.match";
            case ReinforcedHubStorageUnit -> "storage.position;storage.sum;storage.passive;storage.inventory;storage.match";
            case OverpressureHubStorageUnit -> "storage.position;storage.sum;storage.passive;storage.inventory;"
                + "storage.match;storage.water_boost";
            case SteamInputHatchGeneric -> "input.process;input.buffer;input.gui;input.host";
            case PressureSteamHatch -> "pressure.containers;pressure.same;pressure.gate;input.host";
            case SteamOutputHatch -> "output.steam_role;output.buffer;output.front;output.backpressure;output.accepted;output.gui";
            case PressureSteamOutputHatch -> "output.pressure_role;output.buffer;output.front;output.backpressure;"
                + "output.accepted;output.gui";
            case SteamOutputHatchGeneric -> "output.generic_role;output.buffer;output.front;output.backpressure;"
                + "output.accepted;output.gui";
            case SteamCoolingHatch -> "cooling.machine;cooling.water;output.front;output.backpressure;output.accepted;cooling.side";
            case PressureSteamCoolingHatch -> "cooling.machine;cooling.steam;output.front;output.backpressure;"
                + "output.accepted;cooling.side";
            case OverpressureTurbineInputHatch -> "turbine_input.local;turbine_input.containers;"
                + "turbine_input.family;turbine_input.consumption;input.host";
            case MegaAirInputHatch -> "air.process;air.extra;air.exact;input.gui";
            case DistilledWaterHatch -> "water.source;water.conditions;water.reload;water.buffer;water.supply";
            case RedstoneHatch -> "redstone.entries;redstone.compare;redstone.invert;redstone.sides;redstone.saved";
            case SteamCacheNode, ReinforcedSteamCacheNode, OverpressureSteamCacheNode, WaterCacheNode, ReinforcedWaterCacheNode, OverpressureWaterCacheNode -> "cache.editor;cache.front;"
                + "cache.direction;cache.capacity;cache.void;@gtsr.tooltip.shared.bind_kept_on_drop;"
                + "@gtsr.tooltip.shared.bind_all_hint";
            case SingularitySteamCompartment -> "compartment.steam_receive;compartment.editor;compartment.receive_limit;"
                + "compartment.rate;compartment.drop;@gtsr.tooltip.singularity_compartment.mode_locked;"
                + "@gtsr.tooltip.shared.bind_all_hint";
            case SingularitySteamOutputCompartment -> "compartment.steam_send;compartment.editor;compartment.send_limit;"
                + "compartment.rate;compartment.drop;@gtsr.tooltip.singularity_compartment.mode_locked;"
                + "@gtsr.tooltip.shared.bind_all_hint";
            case SingularityFluidInputCompartment -> "compartment.fluid_receive;compartment.editor;compartment.receive_limit;"
                + "compartment.rate;compartment.drop;compartment.external;"
                + "@gtsr.tooltip.singularity_compartment.mode_locked;@gtsr.tooltip.shared.bind_all_hint";
            case SingularityFluidOutputCompartment -> "compartment.fluid_send;compartment.editor;compartment.send_limit;"
                + "compartment.rate;compartment.drop;compartment.external;compartment.lock;"
                + "@gtsr.tooltip.singularity_compartment.mode_locked;@gtsr.tooltip.shared.bind_all_hint";
            case SingularityMinerNode -> "remote.running;remote.items;remote.protection;remote.depth;"
                + "@gtsr.tooltip.shared.bind_all_hint";
            case SingularityDrillingNode -> "remote.running;remote.fluids;remote.protection;remote.pipe;"
                + "@gtsr.tooltip.shared.bind_all_hint";
            case ClusterUnitCrusher, ClusterUnitCentrifuge, ClusterUnitThermalCentrifuge, ClusterUnitSifter, ClusterUnitMagneticSeparator, ClusterUnitFurnace -> "cluster.role";
            case ClusterUnitOreWasher -> "cluster.role;cluster.wet";
            case ClusterUnitLogistics -> "cluster.chain;cluster.io";
            default -> null;
        };
        if (notes == null) return overview;
        List<String> result = new ArrayList<>(overview);
        result.add(" ");
        for (String key : notes.split(";")) {
            if (key.startsWith("@")) {
                for (String row : source) {
                    if (GTSRMachineTooltips.matchesKey(row, key.substring(1))) add(result, row);
                }
            } else {
                EnumChatFormatting color = key.equals("compartment.drop") || key.equals("cooling.side")
                    ? EnumChatFormatting.YELLOW
                    : EnumChatFormatting.GRAY;
                add(result, color + GTSRMachineTooltips.tr("guide." + key));
            }
        }
        return result;
    }

    private static void add(List<String> target, String row) {
        for (String old : target) {
            if (GTSRMachineTooltips.plain(old)
                .equals(GTSRMachineTooltips.plain(row))) return;
        }
        target.add(row);
    }
}
