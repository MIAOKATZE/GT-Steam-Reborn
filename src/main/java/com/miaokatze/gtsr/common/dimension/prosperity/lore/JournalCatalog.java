package com.miaokatze.gtsr.common.dimension.prosperity.lore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Read-only catalog; old chapter IDs survive independently of tab ordering. */
public final class JournalCatalog {

    private static final List<JournalEntry> ALL = new ArrayList<JournalEntry>();
    static {
        ALL.add(new JournalEntry(JournalTab.BIOMES, "steppe", "lore.entry.biomes.steppe", 2, 0));
        ALL.add(new JournalEntry(JournalTab.BIOMES, "forest", "lore.entry.biomes.forest", 2, 0));
        ALL.add(new JournalEntry(JournalTab.BIOMES, "wastes", "lore.entry.biomes.wastes", 2, 0));
        ALL.add(new JournalEntry(JournalTab.BIOMES, "swamp", "lore.entry.biomes.swamp", 2, 0));
        ALL.add(new JournalEntry(JournalTab.BIOMES, "rivers", "lore.entry.biomes.rivers", 2, 0));
        ALL.add(new JournalEntry(JournalTab.BIOMES, "lake", "lore.entry.biomes.lake", 2, 0));
        ALL.add(new JournalEntry(JournalTab.ITEMS, "oath_fragment", "lore.entry.items.oath_fragment", 2, 0));
        ALL.add(new JournalEntry(JournalTab.ITEMS, "old_crown", "lore.entry.items.old_crown", 2, 0));
        ALL.add(new JournalEntry(JournalTab.ITEMS, "brass_chronicle", "lore.entry.items.brass_chronicle", 2, 0));
        ALL.add(new JournalEntry(JournalTab.ITEMS, "patina_seal", "lore.entry.items.patina_seal", 2, 0));
        ALL.add(new JournalEntry(JournalTab.ITEMS, "sigh_glass", "lore.entry.items.sigh_glass", 2, 0));
        ALL.add(new JournalEntry(JournalTab.ITEMS, "mire_memory", "lore.entry.items.mire_memory", 2, 0));
        ALL.add(new JournalEntry(JournalTab.ITEMS, "royal_sap", "lore.entry.items.royal_sap", 2, 0));
        ALL.add(new JournalEntry(JournalTab.ITEMS, "broken_edict", "lore.entry.items.broken_edict", 2, 0));
        ALL.add(new JournalEntry(JournalTab.ITEMS, "switchyard_key", "lore.entry.items.switchyard_key", 2, 0));
        ALL.add(new JournalEntry(JournalTab.ITEMS, "pump_valve_core", "lore.entry.items.pump_valve_core", 2, 0));
        ALL.add(new JournalEntry(JournalTab.ITEMS, "cold_furnace_ember", "lore.entry.items.cold_furnace_ember", 2, 0));
        ALL.add(new JournalEntry(JournalTab.ITEMS, "shelter_workbadge", "lore.entry.items.shelter_workbadge", 2, 0));
        ALL.add(new JournalEntry(JournalTab.ITEMS, "trench_medic_badge", "lore.entry.items.trench_medic_badge", 2, 0));
        ALL.add(new JournalEntry(JournalTab.ITEMS, "bridge_pass", "lore.entry.items.bridge_pass", 2, 0));
        ALL.add(new JournalEntry(JournalTab.ITEMS, "webbed_shuttle", "lore.entry.items.webbed_shuttle", 2, 0));
        ALL.add(
            new JournalEntry(
                JournalTab.ITEMS,
                "cracked_sighting_lens",
                "lore.entry.items.cracked_sighting_lens",
                2,
                0));
        ALL.add(new JournalEntry(JournalTab.ITEMS, "resonant_clapper", "lore.entry.items.resonant_clapper", 2, 0));
        ALL.add(
            new JournalEntry(
                JournalTab.ITEMS,
                "foundry_heart_fragment",
                "lore.entry.items.foundry_heart_fragment",
                2,
                0));
        ALL.add(new JournalEntry(JournalTab.ITEMS, "hive_memory_knot", "lore.entry.items.hive_memory_knot", 2, 0));
        ALL.add(new JournalEntry(JournalTab.ITEMS, "sky_ritual_foil", "lore.entry.items.sky_ritual_foil", 2, 0));
        ALL.add(
            new JournalEntry(JournalTab.ITEMS, "mire_sounding_weight", "lore.entry.items.mire_sounding_weight", 2, 0));
        ALL.add(
            new JournalEntry(JournalTab.ITEMS, "sealed_archive_tube", "lore.entry.items.sealed_archive_tube", 2, 0));
        ALL.add(new JournalEntry(JournalTab.ITEMS, "rail_repair_token", "lore.entry.items.rail_repair_token", 2, 0));
        ALL.add(
            new JournalEntry(JournalTab.STRUCTURES, "fallen_foundry", "lore.entry.structures.fallen_foundry", 2, 32));
        ALL.add(
            new JournalEntry(
                JournalTab.STRUCTURES,
                "subsided_factory",
                "lore.entry.structures.subsided_factory",
                2,
                64));
        ALL.add(new JournalEntry(JournalTab.STRUCTURES, "boiler_shrine", "lore.entry.structures.boiler_shrine", 2, 1));
        ALL.add(new JournalEntry(JournalTab.STRUCTURES, "weaving_mill", "lore.entry.structures.weaving_mill", 2, 2));
        ALL.add(new JournalEntry(JournalTab.STRUCTURES, "sniper_watch", "lore.entry.structures.sniper_watch", 2, 4));
        ALL.add(
            new JournalEntry(JournalTab.STRUCTURES, "mirror_barracks", "lore.entry.structures.mirror_barracks", 2, 8));
        ALL.add(
            new JournalEntry(
                JournalTab.STRUCTURES,
                "resonant_station",
                "lore.entry.structures.resonant_station",
                2,
                16));
        ALL.add(new JournalEntry(JournalTab.STRUCTURES, "switchhouse", "lore.entry.structures.switchhouse", 2, 0));
        ALL.add(new JournalEntry(JournalTab.STRUCTURES, "pumping_house", "lore.entry.structures.pumping_house", 2, 0));
        ALL.add(
            new JournalEntry(JournalTab.STRUCTURES, "workers_lodging", "lore.entry.structures.workers_lodging", 2, 0));
        ALL.add(new JournalEntry(JournalTab.STRUCTURES, "shore_crane", "lore.entry.structures.shore_crane", 2, 0));
        ALL.add(new JournalEntry(JournalTab.STRUCTURES, "cold_altar", "lore.entry.structures.cold_altar", 2, 128));
        ALL.add(
            new JournalEntry(
                JournalTab.STRUCTURES,
                "trench_infirmary",
                "lore.entry.structures.trench_infirmary",
                2,
                0));
        ALL.add(new JournalEntry(JournalTab.STRUCTURES, "ash_crematory", "lore.entry.structures.ash_crematory", 2, 0));
        ALL.add(
            new JournalEntry(
                JournalTab.STRUCTURES,
                "rift_observatory",
                "lore.entry.structures.rift_observatory",
                2,
                128));
        ALL.add(new JournalEntry(JournalTab.STRUCTURES, "weighbridge", "lore.entry.structures.weighbridge", 2, 0));
        ALL.add(new JournalEntry(JournalTab.STRUCTURES, "valve_yard", "lore.entry.structures.valve_yard", 2, 0));
        ALL.add(
            new JournalEntry(JournalTab.STRUCTURES, "archivist_kiosk", "lore.entry.structures.archivist_kiosk", 2, 0));
        ALL.add(new JournalEntry(JournalTab.STRUCTURES, "sap_collector", "lore.entry.structures.sap_collector", 2, 0));
        ALL.add(
            new JournalEntry(JournalTab.STRUCTURES, "mire_boardwalk", "lore.entry.structures.mire_boardwalk", 2, 0));
        ALL.add(new JournalEntry(JournalTab.STRUCTURES, "pilgrim_arch", "lore.entry.structures.pilgrim_arch", 2, 128));
        ALL.add(new JournalEntry(JournalTab.STRUCTURES, "signal_bridge", "lore.entry.structures.signal_bridge", 2, 0));
        ALL.add(new JournalEntry(JournalTab.STRUCTURES, "fungus_cellar", "lore.entry.structures.fungus_cellar", 2, 0));
        ALL.add(new JournalEntry(JournalTab.STRUCTURES, "boiler_chapel", "lore.entry.structures.boiler_chapel", 2, 0));
        ALL.add(
            new JournalEntry(JournalTab.STRUCTURES, "broken_aqueduct", "lore.entry.structures.broken_aqueduct", 2, 0));
        ALL.add(new JournalEntry(JournalTab.STRUCTURES, "gear_garden", "lore.entry.structures.gear_garden", 2, 0));
        ALL.add(
            new JournalEntry(JournalTab.STRUCTURES, "expedition_camp", "lore.entry.structures.expedition_camp", 2, 0));
        ALL.add(
            new JournalEntry(
                JournalTab.STRUCTURES,
                "mire_grotto_cache",
                "lore.entry.structures.mire_grotto_cache",
                2,
                256));
        ALL.add(
            new JournalEntry(
                JournalTab.STRUCTURES,
                "city_archive_cache",
                "lore.entry.structures.city_archive_cache",
                2,
                512));
        ALL.add(
            new JournalEntry(
                JournalTab.STRUCTURES,
                "rail_service_cache",
                "lore.entry.structures.rail_service_cache",
                2,
                1024));
        ALL.add(new JournalEntry(JournalTab.CHAPTERS, "prologue", "lore.chapter.prologue", 3, 0));
        ALL.add(new JournalEntry(JournalTab.CHAPTERS, "rise", "lore.chapter.rise", 3, 0));
        ALL.add(new JournalEntry(JournalTab.CHAPTERS, "steam", "lore.chapter.steam", 3, 0));
        ALL.add(new JournalEntry(JournalTab.CHAPTERS, "networks", "lore.chapter.networks", 3, 0));
        ALL.add(new JournalEntry(JournalTab.CHAPTERS, "collapse", "lore.chapter.collapse", 3, 0));
        ALL.add(new JournalEntry(JournalTab.CHAPTERS, "ember_roads", "lore.chapter.ember_roads", 3, 0));
        ALL.add(new JournalEntry(JournalTab.CHAPTERS, "steppe", "lore.chapter.steppe", 3, 0));
        ALL.add(new JournalEntry(JournalTab.CHAPTERS, "forest", "lore.chapter.forest", 3, 0));
        ALL.add(new JournalEntry(JournalTab.CHAPTERS, "wastes", "lore.chapter.wastes", 3, 0));
        ALL.add(new JournalEntry(JournalTab.CHAPTERS, "swamp", "lore.chapter.swamp", 3, 0));
        ALL.add(new JournalEntry(JournalTab.CHAPTERS, "rivers", "lore.chapter.rivers", 3, 0));
        ALL.add(new JournalEntry(JournalTab.CHAPTERS, "lake", "lore.chapter.lake", 3, 0));
        ALL.add(new JournalEntry(JournalTab.CHAPTERS, "seals", "lore.chapter.seals", 3, 0));
        ALL.add(new JournalEntry(JournalTab.CHAPTERS, "five_watches", "lore.chapter.five_watches", 3, 31));
        ALL.add(new JournalEntry(JournalTab.CHAPTERS, "foundry_subsidence", "lore.chapter.foundry_subsidence", 3, 96));
        ALL.add(new JournalEntry(JournalTab.CHAPTERS, "sky_court", "lore.chapter.sky_court", 3, 128));
        ALL.add(new JournalEntry(JournalTab.CHAPTERS, "hanging_great_tree", "lore.chapter.hanging_great_tree", 5, 0));
    }

    private JournalCatalog() {}

    public static List<JournalEntry> entries(JournalTab tab) {
        List<JournalEntry> selected = new ArrayList<JournalEntry>();
        for (JournalEntry entry : ALL) if (entry.tab == tab) selected.add(entry);
        return Collections.unmodifiableList(selected);
    }
}
