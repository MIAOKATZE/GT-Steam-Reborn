package com.miaokatze.gtsr.common.dimension.prosperity.lore;

/** Stable chapter IDs; translations and pagination are presentation, never progression keys. */
public enum JournalChapter {

    PROLOGUE("prologue", 3, false),
    STEAM("steam", 3, false),
    NETWORKS("networks", 3, false),
    STEPPE("steppe", 3, false),
    FOREST("forest", 3, false),
    WASTES("wastes", 3, false),
    SWAMP("swamp", 3, false),
    RIVERS("rivers", 3, false),
    LAKE("lake", 3, false),
    SEALS("seals", 3, false),
    HANGING_GREAT_TREE("hanging_great_tree", 5, false),
    KING_EPILOGUE("king_epilogue", 4, true);

    public final String id;
    public final int paragraphs;
    public final boolean requiresKingAchievement;

    JournalChapter(String id, int paragraphs, boolean locked) {
        this.id = id;
        this.paragraphs = paragraphs;
        requiresKingAchievement = locked;
    }

    public String titleKey() {
        return "lore.chapter." + id + ".title";
    }

    public String paragraphKey(int paragraph) {
        return "lore.chapter." + id + ".p" + paragraph;
    }
}
