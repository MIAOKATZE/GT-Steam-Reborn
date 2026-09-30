package com.miaokatze.gtsr.common.dimension.prosperity.lore;

/** Stable content identity, separate from display order and server-owned event permissions. */
public final class JournalEntry {

    public final JournalTab tab;
    public final String id, key;
    public final int paragraphs, recordMask;

    public JournalEntry(JournalTab tab, String id, String key, int paragraphs, int recordMask) {
        this.tab = tab;
        this.id = id;
        this.key = key;
        this.paragraphs = paragraphs;
        this.recordMask = recordMask;
    }

    public String titleKey() {
        return key + ".title";
    }

    public String paragraphKey(int index) {
        return key + ".p" + index;
    }

    public boolean recordConfirmed(int progress) {
        return recordMask != 0 && (progress & recordMask) == recordMask;
    }

    public boolean isGiantTree() {
        return tab == JournalTab.CHAPTERS && id.equals("hanging_great_tree");
    }
}
