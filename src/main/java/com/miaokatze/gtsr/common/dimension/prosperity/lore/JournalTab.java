package com.miaokatze.gtsr.common.dimension.prosperity.lore;

/** Four fixed presentation tabs; no tab or entry selection grants server progression. */
public enum JournalTab {

    BIOMES("biomes"),
    ITEMS("items"),
    STRUCTURES("structures"),
    CHAPTERS("chapters");

    public final String id;

    JournalTab(String id) {
        this.id = id;
    }

    public String titleKey() {
        return "lore.tab." + id;
    }
}
