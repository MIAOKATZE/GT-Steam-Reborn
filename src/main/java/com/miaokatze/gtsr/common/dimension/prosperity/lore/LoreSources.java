package com.miaokatze.gtsr.common.dimension.prosperity.lore;

import com.miaokatze.gtsr.common.dimension.prosperity.echo.EchoKind;

/** Stable, authored witnesses. No recipe or executable machine behavior is inferred from their names. */
public final class LoreSources {

    private LoreSources() {}

    public static String chestRelic(int kind) {
        switch (kind) {
            case 7:
                return "switchyard_key";
            case 8:
                return "pump_valve_core";
            case 9:
                return "shelter_workbadge";
            case 11:
                return "cold_furnace_ember";
            case 12:
                return "trench_medic_badge";
            case 14:
                return "sigh_glass";
            case 17:
                return "brass_chronicle";
            case 18:
                return "royal_sap";
            case 19:
                return "mire_memory";
            case 23:
                return "broken_edict";
            default:
                return "";
        }
    }

    public static String bossRelic(EchoKind kind) {
        switch (kind) {
            case DI02:
                return "patina_seal";
            case DI05:
                return "webbed_shuttle";
            case DI08:
                return "cracked_sighting_lens";
            case DI10:
                return "bridge_pass";
            case DI13:
                return "resonant_clapper";
            case DC02:
                return "foundry_heart_fragment";
            case DC08:
                return "hive_memory_knot";
            default:
                return "";
        }
    }
}
