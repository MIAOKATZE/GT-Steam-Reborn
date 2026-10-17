package com.miaokatze.gtsr.client.gui.mui2.lore;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.factory.ClientGUI;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.miaokatze.gtsr.common.dimension.prosperity.echo.RuinSite;
import com.miaokatze.gtsr.common.dimension.prosperity.lore.JournalCatalog;
import com.miaokatze.gtsr.common.dimension.prosperity.lore.JournalChapter;
import com.miaokatze.gtsr.common.dimension.prosperity.lore.JournalEntry;
import com.miaokatze.gtsr.common.dimension.prosperity.lore.JournalTab;
import com.miaokatze.gtsr.common.dimension.prosperity.lore.LoreNetwork;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Read-only local journal. The four permission fields only come from the original S2C snapshot. */
@SideOnly(Side.CLIENT)
public final class ProsperityJournalScreen extends ModularScreen {

    public static final String PANEL = "prosperity_journal";
    private final Model model;
    private final WorldClient world;

    public ProsperityJournalScreen(Model model) {
        super("gtsr", model.buildPanel());
        this.model = model;
        world = Minecraft.getMinecraft().theWorld;
        pausesGame(false);
    }

    public static void open(LoreNetwork.Snapshot snapshot) {
        Model model = new Model(snapshot.kingUnlocked, snapshot.progress, snapshot.sceneEntries, snapshot.sceneBattles);
        ClientGUI.open(new ProsperityJournalScreen(model));
    }

    public void receive(LoreNetwork.Snapshot snapshot) {
        model.permissions(snapshot.kingUnlocked, snapshot.progress, snapshot.sceneEntries, snapshot.sceneBattles);
    }

    public Model model() {
        return model;
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (Minecraft.getMinecraft().theWorld != world || Minecraft.getMinecraft().thePlayer == null) {
            close();
            return;
        }
        model.restoreScroll();
    }

    @Override
    public void onResize(int width, int height) {
        getMainPanel().size(Math.max(1, Math.min(490, width - 12)), Math.max(1, Math.min(300, height - 12)));
        super.onResize(width, height);
    }

    @Override
    public boolean onKeyPressed(char key, int code) {
        if (code == 203 || code == 205) {
            LoreWidgets.scroll(model.body, code == 203 ? -1 : 1);
        } else if (code == 200 || code == 208) {
            List<JournalEntry> entries = JournalCatalog.entries(model.tab);
            int next = Math
                .max(0, Math.min(entries.size() - 1, model.selections[model.tab.ordinal()] + (code == 200 ? -1 : 1)));
            model.selectEntry(entries.get(next).id);
        } else if (key >= '1' && key <= '4') model.selectTab(JournalTab.values()[key - '1']);
        else return super.onKeyPressed(key, code);
        return true;
    }

    public static final class Model {

        private boolean kingUnlocked, appendix;
        private int progress, sceneEntries, sceneBattles;
        private JournalTab tab = JournalTab.CHAPTERS;
        private final int[] selections = new int[JournalTab.values().length];
        private ListWidget<IWidget, ?> directory, body;
        private ButtonWidget<?> appendixButton;
        private int restoreBodyScroll = -1, restoreAfterUpdates;
        private int directorySelectionUpdates;

        public Model(boolean kingUnlocked, int progress, int sceneEntries, int sceneBattles) {
            this.kingUnlocked = kingUnlocked;
            this.progress = progress;
            this.sceneEntries = sceneEntries;
            this.sceneBattles = sceneBattles;
        }

        public JournalTab tab() {
            return tab;
        }

        public boolean appendix() {
            return appendix;
        }

        public JournalEntry currentEntry() {
            return JournalCatalog.entries(tab)
                .get(selections[tab.ordinal()]);
        }

        public void permissions(boolean king, int newProgress, int entries, int battles) {
            if (kingUnlocked == king && progress == newProgress && sceneEntries == entries && sceneBattles == battles)
                return;
            kingUnlocked = king;
            progress = newProgress;
            sceneEntries = entries;
            sceneBattles = battles;
            int scroll = body == null ? 0 : body.getScrollY();
            refreshBody();
            restoreBodyScroll = scroll;
            restoreAfterUpdates = 2;
        }

        public void selectTab(JournalTab selected) {
            tab = selected;
            appendix = false;
            refreshDirectory();
            refreshBody();
            directorySelectionUpdates = 2;
        }

        public void selectEntry(String id) {
            List<JournalEntry> entries = JournalCatalog.entries(tab);
            for (int i = 0; i < entries.size(); i++) if (entries.get(i).id.equals(id)) {
                selections[tab.ordinal()] = i;
                appendix = false;
                refreshBody();
                directorySelectionUpdates = 2;
                return;
            }
        }

        /** Ordered original paragraph keys, exposed for content parity probes without font/GL measurement. */
        public List<String> paragraphKeys() {
            List<String> keys = new ArrayList<>();
            JournalEntry entry = currentEntry();
            boolean scene = true, battle = true;
            if (entry.isGiantTree()) scene = (sceneEntries & (1 << 30)) != 0 || kingUnlocked;
            else if (entry.tab == JournalTab.STRUCTURES)
                for (int i = 0; i < RuinSite.NAMES.length; i++) if (entry.id.equals(RuinSite.NAMES[i])) {
                    scene = (sceneEntries & (1 << i)) != 0 || entry.recordConfirmed(progress);
                    battle = (sceneBattles & (1 << i)) != 0;
                }
            if (!scene) keys.add("gtsr.scene.story_locked");
            else if (appendix && entry.isGiantTree()) {
                if (kingUnlocked) for (int i = 0; i < JournalChapter.KING_EPILOGUE.paragraphs; i++)
                    keys.add(JournalChapter.KING_EPILOGUE.paragraphKey(i));
                else keys.add("lore.locked.body");
            } else {
                for (int i = 0; i < entry.paragraphs; i++) keys.add(
                    entry.tab == JournalTab.STRUCTURES && i > 0 && !battle ? "lore.ruin.ending.pending"
                        : entry.paragraphKey(i));
                if (entry.recordMask != 0) {
                    keys.add(entry.recordConfirmed(progress) ? "lore.record.confirmed" : "lore.record.pending");
                    if (entry.recordConfirmed(progress)) keys.add(entry.key + ".record");
                }
            }
            return keys;
        }

        public void toggleAppendix() {
            if (!currentEntry().isGiantTree()) return;
            appendix = !appendix;
            refreshBody();
        }

        private String titleKey() {
            return appendix && currentEntry().isGiantTree()
                ? kingUnlocked ? JournalChapter.KING_EPILOGUE.titleKey() : "lore.locked.title"
                : currentEntry().titleKey();
        }

        public ModularPanel buildPanel() {
            ModularPanel panel = new ModularPanel(PANEL).size(490, 300)
                .background(LoreUiStyle.JOURNAL_PANEL);
            directory = new ListWidget<>().name("journal.directory")
                .width(112)
                .heightRel(1f)
                .scrollDirection(new VerticalScrollData());
            body = new ListWidget<>().name("journal.paragraphs")
                .widthRel(1f)
                .heightRelOffset(1f, -24)
                .scrollDirection(new VerticalScrollData());
            Flow tabs = Flow.row()
                .widthRel(1f)
                .height(22)
                .childPadding(3);
            for (JournalTab value : JournalTab.values()) tabs.child(
                LoreWidgets
                    .action(
                        LoreUiStyle.button(IKey.lang(value.titleKey()), () -> tab == value),
                        "journal.tab." + value.id,
                        () -> { if (tab != value) selectTab(value); })
                    .expanded());
            appendixButton = LoreWidgets.button(
                "journal.appendix",
                IKey.lang(
                    () -> appendix ? "lore.appendix.return" : kingUnlocked ? "lore.appendix" : "lore.appendix.locked"),
                this::toggleAppendix)
                .width(86);
            Flow article = Flow.col()
                .expanded()
                .heightRel(1f)
                .background(LoreUiStyle.JOURNAL_PAPER)
                .childPadding(4)
                .child(
                    Flow.row()
                        .widthRel(1f)
                        .height(20)
                        .child(
                            IKey.lang(this::titleKey)
                                .asWidget()
                                .color(LoreUiStyle.JOURNAL_ARTICLE_TITLE)
                                .shadow(false)
                                .expanded())
                        .child(appendixButton))
                .child(body);
            Flow footer = Flow.row()
                .widthRel(1f)
                .height(20)
                .childPadding(5)
                .child(
                    LoreWidgets.button("journal.previous", "<", () -> LoreWidgets.scroll(body, -1))
                        .width(25))
                .child(
                    LoreWidgets.button("journal.close", "gui.done", LoreWidgets::close)
                        .expanded())
                .child(
                    LoreWidgets.button("journal.next", ">", () -> LoreWidgets.scroll(body, 1))
                        .width(25));
            panel.child(
                Flow.col()
                    .sizeRel(1f)
                    .padding(8)
                    .childPadding(5)
                    .child(
                        IKey.lang("lore.book.title")
                            .asWidget()
                            .color(LoreUiStyle.JOURNAL_TITLE)
                            .shadow(false)
                            .widthRel(1f)
                            .height(18))
                    .child(tabs)
                    .child(
                        Flow.row()
                            .widthRel(1f)
                            .expanded()
                            .childPadding(7)
                            .child(directory)
                            .child(article))
                    .child(footer));
            refreshDirectory();
            refreshBody();
            return panel;
        }

        private void refreshDirectory() {
            if (directory == null) return;
            directory.removeAll();
            List<JournalEntry> entries = JournalCatalog.entries(tab);
            for (int index = 0; index < entries.size(); index++) {
                JournalEntry entry = entries.get(index);
                final int number = index + 1;
                directory.child(
                    LoreWidgets
                        .action(
                            LoreUiStyle.forestEntry(
                                IKey.dynamic(
                                    () -> number + " · "
                                        + net.minecraft.util.StatCollector.translateToLocal(entry.titleKey())),
                                () -> currentEntry().id.equals(entry.id)),
                            "journal.entry." + entry.id,
                            () -> selectEntry(entry.id))
                        .widthRel(1f)
                        .height(27)
                        .tooltipBuilder(t -> t.addLine(IKey.lang(entry.titleKey()))));
            }
        }

        private void refreshBody() {
            if (body == null) return;
            restoreBodyScroll = -1;
            LoreWidgets.paragraphs(body, paragraphKeys(), LoreUiStyle.JOURNAL_TEXT);
            body.getScrollData()
                .scrollTo(body.getScrollArea(), 0);
            appendixButton.setEnabled(currentEntry().isGiantTree());
        }

        private void restoreScroll() {
            if (directorySelectionUpdates > 0 && --directorySelectionUpdates == 0) {
                int top = selections[tab.ordinal()] * 27;
                if (top < directory.getScrollY() || top + 27 > directory.getScrollY() + directory.getArea().height)
                    directory.getScrollData()
                        .scrollTo(directory.getScrollArea(), top);
            }
            if (restoreBodyScroll < 0 || --restoreAfterUpdates > 0) return;
            body.getScrollData()
                .scrollTo(body.getScrollArea(), restoreBodyScroll);
            restoreBodyScroll = -1;
        }
    }
}
