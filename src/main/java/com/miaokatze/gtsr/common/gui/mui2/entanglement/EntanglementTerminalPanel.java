package com.miaokatze.gtsr.common.gui.mui2.entanglement;

import java.util.Locale;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.network.NetworkUtils;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.cleanroommc.modularui.theme.WidgetTheme;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.PageButton;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.miaokatze.gtsr.client.gui.terminal.GtsrGuiPalette;
import com.miaokatze.gtsr.common.gui.mui2.TerminalUiStyle;
import com.miaokatze.gtsr.common.gui.mui2.TerminalViewport;
import com.miaokatze.gtsr.common.machine.base.MTESingularityMachineBase;
import com.miaokatze.gtsr.common.terminal.entanglement.EntanglementMath;
import com.miaokatze.gtsr.common.terminal.entanglement.EntanglementSnapshot;
import com.miaokatze.gtsr.common.util.GtsrNumFormat;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** The approved overview-first layout on the existing 620x340 terminal canvas and textures. */
public final class EntanglementTerminalPanel {

    public static final String SYNC_KEY = "entanglement_session";
    private static final String PREFIX = "gtsr.entanglement_terminal.";
    private static final int TITLE = GtsrGuiPalette.TEXT_TITLE;
    private static final int BODY = GtsrGuiPalette.TEXT_BODY;
    private static final int MUTED = GtsrGuiPalette.TEXT_MUTED;
    private static final int AMBER = GtsrGuiPalette.TEXT_ACCENT;

    private EntanglementTerminalPanel() {}

    public static ModularPanel build(PosGuiData data, MTESingularityMachineBase machine, PanelSyncManager manager) {
        EntanglementTerminalSession session = new EntanglementTerminalSession(data, machine);
        manager.syncValue(SYNC_KEY, session);
        ParentWidget<?> body = new ParentWidget<>().name("entanglement_body")
            .size(604, 332)
            .pos(0, 0);
        body.child(text(() -> {
            EntanglementSnapshot snapshot = session.snapshot();
            return tr(snapshot != null && snapshot.critical ? "cess" : "sse");
        }, 4, 5, 426, 13, 1, TITLE).name("entanglement_title"));
        body.child(text(() -> status(session), 432, 7, 166, 11, 0.7f, GtsrGuiPalette.STATE_ONLINE));
        body.child(line(24));
        PagedWidget.Controller controller = new PagedWidget.Controller();
        body.child(
            new PageButton(0, controller).name("entanglement_overview_tab")
                .pos(4, 28)
                .size(85, 16)
                .background(TerminalUiStyle.BUTTON)
                .overlay(
                    IKey.lang(PREFIX + "overview")
                        .color(BODY)));
        body.child(
            new PageButton(1, controller).name("entanglement_rules_tab")
                .pos(93, 28)
                .size(85, 16)
                .background(TerminalUiStyle.BUTTON)
                .overlay(
                    IKey.lang(PREFIX + "rules")
                        .color(BODY)));
        ParentWidget<?> overview = new ParentWidget<>().name("entanglement_overview")
            .size(604, 274);
        for (int metric = 0; metric < 4; metric++) {
            final int index = metric;
            ParentWidget<?> card = new ParentWidget<>().name("entanglement_metric_" + index)
                .pos(index * 151, 0)
                .size(147, 65)
                .background(TerminalUiStyle.CARD);
            card.child(text(() -> metricName(session, index), 7, 5, 133, 9, 0.7f, BODY));
            card.child(text(() -> metricValue(session, index), 7, 18, 133, 16, 1.3f, TITLE));
            card.child(
                new Decoration().pos(7, 37)
                    .size(133, 3)
                    .background(new Meter(() -> progress(session, index), index == 3 ? 0xffb78bdb : AMBER)));
            card.child(text(() -> metricNext(session, index), 7, 44, 133, 9, 0.65f, BODY));
            card.child(text(() -> metricHint(session, index), 7, 54, 133, 8, 0.6f, MUTED));
            overview.child(card);
        }
        overview.child(text(() -> tr("inputs"), 4, 73, 160, 11, 0.85f, TITLE));
        overview.child(text(() -> tr("limit_hint"), 170, 75, 428, 9, 0.65f, MUTED));
        overview.child(
            new Decoration().pos(0, 90)
                .size(600, 148)
                .background(TerminalUiStyle.CARD));
        String[] columns = { "fluid_hatch", "stored", "limit", "consume", "effect" };
        int[] x = { 8, 177, 280, 383, 475 }, widths = { 165, 99, 99, 88, 117 };
        for (int i = 0; i < columns.length; i++) {
            final String key = columns[i];
            overview.child(text(() -> tr(key), x[i], 94, widths[i], 10, 0.65f, MUTED));
        }
        for (int slot = 0; slot < EntanglementSnapshot.PAGE_SIZE; slot++) {
            final int index = slot;
            int y = 108 + slot * 21;
            ParentWidget<?> row = new ParentWidget<>().name("entanglement_row_" + slot)
                .pos(0, y)
                .size(600, 21)
                .setEnabledIf(widget -> session.row(index) != null);
            row.child(text(() -> fluidName(session, index), 8, 0, 165, 10, 0.75f, BODY));
            row.child(text(() -> position(session, index), 8, 11, 165, 8, 0.55f, MUTED));
            row.child(text(() -> {
                EntanglementSnapshot.Row input = session.row(index);
                return input == null ? "—" : GtsrNumFormat.compact3(input.stored) + " mB";
            }, 177, 4, 99, 11, 0.75f, BODY).addTooltipLine(IKey.dynamic(() -> {
                EntanglementSnapshot.Row input = session.row(index);
                return input == null ? "—" : GtsrNumFormat.grouped(input.stored) + " mB";
            }))
                .addTooltipLine(IKey.lang(PREFIX + "shared_me_hint"))
                .tooltipAutoUpdate(true));
            row.child(
                new TextFieldWidget().name("entanglement_limit_" + slot)
                    .pos(280, 1)
                    .size(96, 17)
                    .setScale(0.7f)
                    .setTextColor(AMBER)
                    .setMaxLength(19)
                    .hintText(tr("all"))
                    .autoUpdateOnChange(true)
                    .value(new StringValue.Dynamic(() -> session.draft(index), value -> session.edit(index, value)))
                    .addTooltipLine(IKey.lang(PREFIX + "limit_tooltip")));
            row.child(
                text(() -> GtsrNumFormat.compact3(session.planned(index)) + " mB/s", 383, 4, 88, 11, 0.7f, BODY)
                    .addTooltipLine(IKey.dynamic(() -> GtsrNumFormat.grouped(session.planned(index)) + " mB/s"))
                    .tooltipAutoUpdate(true));
            row.child(
                text(() -> effect(session, index), 475, 4, 117, 11, 0.65f, AMBER)
                    .addTooltipLine(IKey.lang(PREFIX + "effect_hint")));
            overview.child(row);
        }
        overview.child(text(() -> {
            EntanglementSnapshot snapshot = session.snapshot();
            return snapshot != null && snapshot.totalRows == 0 ? tr("no_inputs") : "";
        }, 12, 149, 574, 18, 0.8f, MUTED));
        overview.child(
            text(() -> warning(session), 5, 242, 458, 23, 0.7f, AMBER).name("entanglement_warning")
                .addTooltipLine(IKey.lang(PREFIX + "forecast_hint")));
        overview.child(
            button("previous", () -> session.page(-1)).pos(466, 242)
                .size(24, 17));
        overview.child(text(() -> {
            EntanglementSnapshot snapshot = session.snapshot();
            return snapshot == null ? "—" : (snapshot.page + 1) + " / " + snapshot.pages();
        }, 493, 246, 73, 10, 0.75f, BODY));
        overview.child(
            button("next", () -> session.page(1)).pos(572, 242)
                .size(24, 17));
        ParentWidget<?> rules = new ParentWidget<>().name("entanglement_rules")
            .size(604, 274);
        for (int row = 0; row < 11; row++) {
            final int line = row;
            rules.child(text(() -> tr("rule_" + line), 8, 4 + row * 22, 584, 20, 0.8f, BODY));
        }
        rules.child(text(() -> tr("sse_solid"), 8, 249, 584, 14, 0.85f, AMBER));
        body.child(
            new PagedWidget<>().name("entanglement_pages")
                .pos(0, 50)
                .size(604, 264)
                .controller(controller)
                .addPage(overview)
                .addPage(rules));
        body.child(line(316));
        // Footer stays outside the pages; changing tabs never submits a draft.
        body.child(
            text(() -> tr("receipt_" + session.receipt()), 4, 319, 290, 9, 0.6f, MUTED).name("entanglement_receipt"));
        body.child(
            button("discard", session::discard).pos(300, 318)
                .size(78, 14));
        body.child(
            button("defaults", session::defaults).pos(382, 318)
                .size(108, 14));
        body.child(
            button("apply", session::apply).name("entanglement_apply")
                .pos(494, 318)
                .size(106, 14)
                .addTooltipLine(IKey.lang(PREFIX + "apply_hint")));
        return ModularPanel.defaultPanel(EntanglementTerminalFactory.PANEL_NAME, 620, 340)
            .background(TerminalUiStyle.CLUSTER_PANEL)
            .child(
                TerminalViewport.wrapExact("entanglement_viewport", body, 604, 332)
                    .pos(8, 0)
                    .widthRelOffset(1f, -16)
                    .heightRelOffset(1f, -8));
    }

    private static ButtonWidget<?> button(String key, Runnable action) {
        return TerminalUiStyle.button(PREFIX + key)
            .name("entanglement_" + key)
            .onMousePressed(button -> {
                if (button == 0) action.run();
                return true;
            });
    }

    private static Decoration line(int y) {
        return new Decoration().pos(4, y)
            .size(596, 1)
            .background(new Rectangle().color(GtsrGuiPalette.DIVIDER));
    }

    private static String tr(String key) {
        return net.minecraft.util.StatCollector.translateToLocal(PREFIX + key);
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static String metricName(EntanglementTerminalSession session, int index) {
        return tr(new String[] { "heat", "overlimit", "instability", "tear" }[index]);
    }

    private static double value(EntanglementSnapshot snapshot, int index) {
        return new double[] { snapshot.heat, snapshot.overlimit, snapshot.instability, snapshot.tear }[index];
    }

    private static String metricValue(EntanglementTerminalSession session, int index) {
        EntanglementSnapshot snapshot = session.snapshot();
        if (snapshot == null) return "—";
        if (index == 3 && !snapshot.critical) return tr("solidified");
        return String.format(Locale.ROOT, "%.2f%%", value(snapshot, index));
    }

    private static double progress(EntanglementTerminalSession session, int index) {
        EntanglementSnapshot snapshot = session.snapshot();
        if (snapshot == null || index == 3 && !snapshot.critical) return 0;
        return value(snapshot, index) / (index == 1 ? snapshot.critical ? 1000 : 500 : 100);
    }

    private static String metricNext(EntanglementTerminalSession session, int index) {
        EntanglementSnapshot snapshot = session.snapshot();
        EntanglementMath.Forecast forecast = session.forecast();
        if (snapshot == null || forecast == null) return "—";
        if (index == 3 && !snapshot.critical) return tr("no_tear");
        double next = new double[] { forecast.heat, forecast.overlimit, forecast.instability, forecast.tear }[index];
        return tr("next_second") + " "
            + number(next)
            + "% ("
            + String.format(Locale.ROOT, "%+.3f", next - value(snapshot, index))
            + ")";
    }

    private static String metricHint(EntanglementTerminalSession session, int index) {
        EntanglementSnapshot snapshot = session.snapshot();
        if (index == 3 && (snapshot == null || !snapshot.critical)) return tr("fixed_state");
        if (index == 0 && session.forecast() != null)
            return tr("heat_cap") + " · ×" + number(session.forecast().multiplier);
        return tr(
            index == 1 && snapshot != null && snapshot.critical ? "overlimit_cap_cess"
                : new String[] { "heat_cap", "overlimit_cap_sse", "instability_cap", "tear_cap" }[index]);
    }

    private static String fluidName(EntanglementTerminalSession session, int slot) {
        EntanglementSnapshot.Row row = session.row(slot);
        EntanglementSnapshot snapshot = session.snapshot();
        return row == null ? "" : tr((row.kind < 3 && snapshot.critical ? "dense_" : "fluid_") + row.kind);
    }

    private static String position(EntanglementTerminalSession session, int slot) {
        EntanglementSnapshot.Row row = session.row(slot);
        return row == null ? "" : tr("hatch") + " [" + row.x + ", " + row.y + ", " + row.z + "]";
    }

    private static String effect(EntanglementTerminalSession session, int slot) {
        EntanglementSnapshot.Row row = session.row(slot);
        EntanglementMath.Forecast forecast = session.forecast();
        EntanglementSnapshot snapshot = session.snapshot();
        if (row == null || forecast == null || snapshot == null || session.planned(slot) == 0) return "—";
        long[] requested = session.requested();
        // Effects are per-fluid totals, as documented by the column tooltip; draft preview uses the same totals.
        if (row.kind < 3) return tr("heat") + " +" + number(forecast.heatGain) + "%";
        double delta = row.kind == 3 ? EntanglementMath.pyrotheum(requested[3])
            : row.kind == 4 ? EntanglementMath.cryotheum(requested[4]) : 0.001d * requested[5];
        return tr(row.kind == 3 ? "overlimit" : row.kind == 4 ? "instability" : "tear") + (row.kind == 3 ? " +" : " −")
            + number(Math.abs(delta))
            + "%";
    }

    private static String status(EntanglementTerminalSession session) {
        EntanglementSnapshot snapshot = session.snapshot();
        return snapshot == null ? tr("syncing")
            : tr(
                !snapshot.formed ? "unformed"
                    : !snapshot.allowed ? "stopped"
                        : snapshot.cycling || EntanglementMath.grade(snapshot.amounts) >= 0 ? "running" : "idle");
    }

    private static String warning(EntanglementTerminalSession session) {
        EntanglementSnapshot snapshot = session.snapshot();
        EntanglementMath.Forecast forecast = session.forecast();
        if (snapshot == null || forecast == null) return tr("syncing");
        if (!session.canApply() && !session.receipt()
            .equals("pending")) return tr("receipt_" + session.receipt());
        if (forecast.overlimitExplosion || forecast.collapse || forecast.tearExplosion) return tr("warn_explosion");
        if (forecast.chill) return tr("warn_chill");
        if (forecast.solidify) return tr("warn_solidify");
        if (!snapshot.outputSpace) return tr("warn_output");
        if (!snapshot.formed || !snapshot.allowed) return tr("warn_stopped");
        if (forecast.grade < 0) return tr("warn_idle");
        return tr("warn_safe");
    }

    private static TextWidget<?> text(Supplier<String> text, int x, int y, int width, int height, float scale,
        int color) {
        return IKey.dynamic(() -> clip(text.get(), (int) (width / scale)))
            .asWidget()
            .pos(x, y)
            .size(width, height)
            .scale(scale)
            .textAlign(Alignment.CenterLeft)
            .color(color)
            .shadow(true);
    }

    private static String clip(String text, int width) {
        return NetworkUtils.isDedicatedClient() ? clientClip(text, width) : text;
    }

    @SideOnly(Side.CLIENT)
    private static String clientClip(String text, int width) {
        if (Minecraft.getMinecraft().fontRenderer.getStringWidth(text) <= width) return text;
        return Minecraft.getMinecraft().fontRenderer.trimStringToWidth(text, Math.max(0, width - 9)) + "...";
    }

    private static final class Meter implements IDrawable {

        private final DoubleSupplier value;
        private final Rectangle empty = new Rectangle().color(0xff262a30);
        private final Rectangle full;

        Meter(DoubleSupplier value, int color) {
            this.value = value;
            full = new Rectangle().color(color);
        }

        @SideOnly(Side.CLIENT)
        @Override
        public void draw(GuiContext context, int x, int y, int width, int height, WidgetTheme theme) {
            empty.draw(context, x, y, width, height, theme);
            int filled = (int) (width * Math.max(0, Math.min(1, value.getAsDouble())));
            if (filled > 0) full.draw(context, x, y, filled, height, theme);
        }
    }

    private static final class Decoration extends Widget<Decoration> {

        @Override
        public boolean canHover() {
            return false;
        }
    }
}
