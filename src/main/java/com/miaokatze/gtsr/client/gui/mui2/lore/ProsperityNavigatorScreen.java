package com.miaokatze.gtsr.client.gui.mui2.lore;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.factory.ClientGUI;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.miaokatze.gtsr.client.gui.terminal.GtsrGuiPalette;
import com.miaokatze.gtsr.common.commands.RuinLocateCommand;
import com.miaokatze.gtsr.common.dimension.prosperity.lore.NavigatorNetwork;
import com.miaokatze.gtsr.common.gui.mui2.TerminalUiStyle;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** A local directory; lookup admission, budgets and world access remain on the original server protocol. */
@SideOnly(Side.CLIENT)
public final class ProsperityNavigatorScreen extends ModularScreen {

    public static final String PANEL = "prosperity_navigator";
    private static int nextSerial;
    private final Model model;
    private final WorldClient world;
    private final int dimension;
    private boolean closed;

    public ProsperityNavigatorScreen(Model model) {
        super("gtsr", model.buildPanel());
        this.model = model;
        world = Minecraft.getMinecraft().theWorld;
        dimension = Minecraft.getMinecraft().thePlayer == null ? 0 : Minecraft.getMinecraft().thePlayer.dimension;
        pausesGame(false);
    }

    public static void open() {
        ClientGUI.open(new ProsperityNavigatorScreen(new Model()));
    }

    public Model model() {
        return model;
    }

    public void receive(NavigatorNetwork.Result result) {
        if (!closed && ModularScreen.getCurrent() == this) model.receive(result);
    }

    @Override
    public void onClose() {
        closed = true;
        model.pending = false;
        super.onClose();
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld != world || mc.thePlayer == null
            || mc.thePlayer.dimension != dimension
            || !NavigatorNetwork.holdsCompass(mc.thePlayer)) {
            model.pending = false;
            close();
            return;
        }
        model.tick(System.currentTimeMillis());
    }

    @Override
    public void onResize(int width, int height) {
        getMainPanel().size(Math.max(1, Math.min(440, width - 16)), Math.max(1, Math.min(330, height - 16)));
        super.onResize(width, height);
    }

    public static final class Model {

        private final List<String> ids = RuinLocateCommand.names();
        private String query = "", statusKey = "gtsr.navigator.hint", language = "";
        private int selected = -1, requestSerial, requestedIndex = -1, x, z, distance;
        private long requestedAt;
        private boolean pending;
        private ListWidget<IWidget, ?> directory;

        public boolean pending() {
            return pending;
        }

        public int requestSerial() {
            return requestSerial;
        }

        public int requestedIndex() {
            return requestedIndex;
        }

        public int selectedIndex() {
            return selected;
        }

        public String statusKey() {
            return statusKey;
        }

        public String query() {
            return query;
        }

        public void search(String value) {
            if (query.equals(value)) return;
            query = value;
            refreshDirectory();
        }

        public void select(int index) {
            if (index >= 0 && index < ids.size()) selected = index;
        }

        public void request() {
            if (selected < 0 || pending) return;
            pending = true;
            requestedAt = System.currentTimeMillis();
            requestedIndex = selected;
            nextSerial = nextSerial == Integer.MAX_VALUE ? 1 : nextSerial + 1;
            requestSerial = nextSerial;
            statusKey = "gtsr.navigator.searching";
            NavigatorNetwork.query(requestedIndex, requestSerial);
        }

        public void receive(NavigatorNetwork.Result result) {
            if (!pending || result.serial != requestSerial || result.index != requestedIndex) return;
            pending = false;
            x = result.x;
            z = result.z;
            distance = result.distance;
            statusKey = result.status == 1 ? "gtsr.navigator.coordinates"
                : result.status == 3 ? "gtsr.navigator.cooldown"
                    : result.status == 4 ? "gtsr.navigator.timeout" : "gtsr.navigator.missing";
        }

        public void tick(long now) {
            if (pending && now - requestedAt > 60000) {
                pending = false;
                statusKey = "gtsr.navigator.timeout";
            }
            String currentLanguage = Minecraft.getMinecraft().gameSettings.language;
            if (!language.equals(currentLanguage)) {
                language = currentLanguage;
                refreshDirectory();
            }
        }

        public ModularPanel buildPanel() {
            ModularPanel panel = new ModularPanel(PANEL).size(440, 330)
                .background(TerminalUiStyle.PANEL);
            directory = new ListWidget<>().name("navigator.directory")
                .widthRel(1f)
                .expanded()
                .scrollDirection(new VerticalScrollData());
            TextFieldWidget search = new TextFieldWidget().name("navigator.search")
                .widthRel(1f)
                .height(20)
                .value(new StringValue.Dynamic(() -> query, this::search))
                .setMaxLength(64)
                .autoUpdateOnChange(true)
                .setFocusOnGuiOpen(true);
            panel.child(
                Flow.col()
                    .sizeRel(1f)
                    .padding(10)
                    .childPadding(6)
                    .child(
                        IKey.lang("gtsr.navigator.title")
                            .asWidget()
                            .color(GtsrGuiPalette.TEXT_TITLE)
                            .widthRel(1f)
                            .height(18))
                    .child(search)
                    .child(directory)
                    .child(
                        IKey.dynamic(
                            () -> "gtsr.navigator.coordinates".equals(statusKey)
                                ? StatCollector.translateToLocalFormatted(statusKey, x, z, distance)
                                : StatCollector.translateToLocal(statusKey))
                            .asWidget()
                            .color(GtsrGuiPalette.TEXT_BODY)
                            .name("navigator.status")
                            .widthRel(1f)
                            .textAlign(Alignment.TopLeft))
                    .child(
                        Flow.row()
                            .widthRel(1f)
                            .height(20)
                            .childPadding(4)
                            .child(
                                LoreWidgets.button("navigator.previous", "<", () -> LoreWidgets.scroll(directory, -1))
                                    .width(24))
                            .child(
                                LoreWidgets.button("navigator.query", "gtsr.navigator.query", this::request)
                                    .expanded()
                                    .setEnabledIf(w -> selected >= 0 && !pending))
                            .child(
                                LoreWidgets.button("navigator.next", ">", () -> LoreWidgets.scroll(directory, 1))
                                    .width(24))
                            .child(
                                LoreWidgets.button("navigator.close", "gui.done", LoreWidgets::close)
                                    .width(60))));
            language = Minecraft.getMinecraft().gameSettings.language;
            refreshDirectory();
            return panel;
        }

        private void refreshDirectory() {
            if (directory == null) return;
            directory.removeAll();
            String filter = query.trim()
                .toLowerCase(Locale.ROOT);
            int found = 0;
            for (int index = 0; index < ids.size(); index++) {
                final int identity = index;
                String id = ids.get(index);
                if (!id.contains(filter) && !RuinLocateCommand.displayName(id)
                    .toLowerCase(Locale.ROOT)
                    .contains(filter)) continue;
                found++;
                directory.child(
                    LoreWidgets.button("navigator.entry." + id, "", () -> select(identity))
                        .widthRel(1f)
                        .height(27)
                        .tooltipBuilder(t -> t.addLine(IKey.dynamic(() -> RuinLocateCommand.displayName(id))))
                        .overlay(
                            IKey.dynamic(() -> (selected == identity ? "§e● " : "") + RuinLocateCommand.displayName(id))
                                .color(GtsrGuiPalette.TEXT_BODY)));
            }
            if (found == 0) directory.child(
                IKey.lang("gtsr.navigator.no_match")
                    .asWidget()
                    .color(GtsrGuiPalette.TEXT_MUTED)
                    .widthRel(1f));
            directory.getScrollData()
                .scrollTo(directory.getScrollArea(), 0);
        }
    }
}
