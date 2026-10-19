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
        body.child(
            text(
                () -> "§l" + tr(session.snapshot() != null && session.snapshot().critical ? "cess" : "sse"),
                4,
                5,
                426,
                15,
                1.05f,
                TITLE).name("entanglement_title"));
        body.child(
            text(() -> "§l" + status(session), 432, 7, 166, 11, 0.8f, BODY).name("entanglement_status")
                .color(() -> statusColor(session)));
        body.child(line(25));

        ParentWidget<?> formula = new ParentWidget<>().name("entanglement_formula")
            .pos(0, 31)
            .size(600, 34)
            .background(TerminalUiStyle.CARD);
        formula.child(
            text(
                () -> "§l" + tr("formula_title") + "  §r§7" + tr(session.dirty() ? "formula_draft" : "formula_live"),
                8,
                4,
                584,
                9,
                0.7f,
                AMBER));
        formula.child(
            text(() -> heatEquation(session), 8, 17, 584, 12, 0.9f, TITLE).name("entanglement_heat_equation")
                .addTooltipLine(IKey.dynamic(() -> "§l" + tr("formula_terms")))
                .addTooltipLine(IKey.dynamic(() -> saturationEquation(session)))
                .addTooltipLine(IKey.dynamic(() -> tr("rule_4")))
                .addTooltipLine(IKey.dynamic(() -> tr("forecast_hint")))
                .tooltipAutoUpdate(true));
        body.child(formula);

        for (int metric = 0; metric < 4; metric++) {
            final int index = metric;
            ParentWidget<?> card = new ParentWidget<>().name("entanglement_metric_" + index)
                .pos(index * 151, 73)
                .size(147, 68)
                .background(TerminalUiStyle.CARD);
            card.child(
                text(() -> "§l" + metricName(session, index), 7, 5, 133, 10, 0.75f, BODY)
                    .name("entanglement_metric_label_" + index)
                    .color(() -> metricColor(session, index))
                    .addTooltipLine(IKey.dynamic(() -> metricRule(session, index, false)))
                    .addTooltipLine(IKey.dynamic(() -> metricRule(session, index, true)))
                    .addTooltipLine(IKey.dynamic(() -> tr(index == 0 ? "rule_2" : "rule_10")))
                    .tooltipAutoUpdate(true));
            card.child(
                text(() -> "§l" + metricValue(session, index), 7, 18, 133, 20, 1.55f, TITLE)
                    .name("entanglement_metric_value_" + index)
                    .color(() -> metricColor(session, index))
                    .addTooltipLine(IKey.dynamic(() -> metricRule(session, index, false)))
                    .addTooltipLine(IKey.dynamic(() -> metricRule(session, index, true)))
                    .addTooltipLine(IKey.dynamic(() -> metricHint(session, index)))
                    .tooltipAutoUpdate(true));
            card.child(
                new Decoration().pos(7, 40)
                    .size(133, 3)
                    .background(new Meter(() -> progress(session, index), () -> metricColor(session, index))));
            card.child(text(() -> metricNext(session, index), 7, 48, 133, 9, 0.7f, BODY));
            card.child(
                text(() -> metricDelta(session, index), 7, 59, 133, 8, 0.65f, MUTED)
                    .name("entanglement_metric_delta_" + index)
                    .color(() -> deltaColor(session, index))
                    .addTooltipLine(IKey.dynamic(() -> metricHint(session, index)))
                    .tooltipAutoUpdate(true));
            body.child(card);
        }

        body.child(text(() -> "§l" + tr("inputs"), 4, 149, 160, 12, 0.9f, TITLE));
        body.child(text(() -> tr("limit_hint"), 170, 151, 428, 9, 0.65f, MUTED));
        body.child(
            new Decoration().pos(0, 165)
                .size(600, 125)
                .background(TerminalUiStyle.CARD));
        String[] columns = { "fluid_hatch", "stored", "limit", "consume", "effect" };
        int[] x = { 8, 177, 280, 383, 475 }, widths = { 165, 99, 99, 88, 117 };
        for (int i = 0; i < columns.length; i++) {
            final String key = columns[i];
            body.child(text(() -> "§l" + tr(key), x[i], 169, widths[i], 10, 0.65f, GtsrGuiPalette.TEXT_LABEL));
        }
        for (int slot = 0; slot < EntanglementSnapshot.PAGE_SIZE; slot++) {
            final int index = slot;
            ParentWidget<?> row = new ParentWidget<>().name("entanglement_row_" + slot)
                .pos(0, 182 + slot * 18)
                .size(600, 18)
                .setEnabledIf(widget -> session.row(index) != null);
            row.child(
                text(() -> "§l" + fluidName(session, index), 8, 0, 165, 10, 0.8f, BODY)
                    .color(() -> fluidColor(session, index))
                    .addTooltipLine(IKey.dynamic(() -> tr("detected_hint")))
                    .addTooltipLine(IKey.dynamic(() -> fluidRule(session, index)))
                    .tooltipAutoUpdate(true));
            row.child(text(() -> position(session, index), 8, 10, 165, 7, 0.5f, 0xff8e9aa6));
            row.child(text(() -> {
                EntanglementSnapshot.Row input = session.row(index);
                return input == null ? "—" : "§l" + GtsrNumFormat.compact3(input.stored) + "§r mB";
            }, 177, 3, 99, 11, 0.85f, TITLE).addTooltipLine(IKey.dynamic(() -> {
                EntanglementSnapshot.Row input = session.row(index);
                return input == null ? "—" : GtsrNumFormat.grouped(input.stored) + " mB";
            }))
                .addTooltipLine(IKey.lang(PREFIX + "shared_me_hint"))
                .tooltipAutoUpdate(true));
            row.child(
                new TextFieldWidget().name("entanglement_limit_" + slot)
                    .pos(280, 0)
                    .size(96, 16)
                    .setScale(0.75f)
                    .setTextColor(AMBER)
                    .setMaxLength(19)
                    .hintText(tr("all"))
                    .autoUpdateOnChange(true)
                    .value(new StringValue.Dynamic(() -> session.draft(index), value -> session.edit(index, value)))
                    .addTooltipLine(IKey.lang(PREFIX + "limit_tooltip")));
            row.child(
                text(
                    () -> "§l" + GtsrNumFormat.compact3(session.planned(index)) + "§r mB/s",
                    383,
                    3,
                    88,
                    11,
                    0.75f,
                    BODY).color(() -> session.planned(index) > 0 ? GtsrGuiPalette.STATE_ONLINE : 0xff8e9aa6)
                        .addTooltipLine(IKey.dynamic(() -> GtsrNumFormat.grouped(session.planned(index)) + " mB/s"))
                        .tooltipAutoUpdate(true));
            row.child(
                text(() -> effect(session, index), 475, 3, 117, 11, 0.7f, AMBER)
                    .color(() -> session.planned(index) > 0 ? fluidColor(session, index) : MUTED)
                    .addTooltipLine(IKey.lang(PREFIX + "effect_hint"))
                    .addTooltipLine(IKey.dynamic(() -> fluidRule(session, index)))
                    .tooltipAutoUpdate(true));
            body.child(row);
        }
        body.child(text(() -> {
            EntanglementSnapshot snapshot = session.snapshot();
            return snapshot != null && snapshot.totalRows == 0 ? tr("no_inputs") : "";
        }, 12, 210, 574, 20, 0.85f, MUTED).name("entanglement_empty"));
        body.child(
            text(() -> warning(session), 5, 296, 458, 16, 0.7f, AMBER).name("entanglement_warning")
                .color(() -> warningColor(session))
                .addTooltipLine(IKey.lang(PREFIX + "forecast_hint")));
        body.child(
            button("previous", () -> session.page(-1)).pos(466, 296)
                .size(24, 16));
        body.child(text(() -> {
            EntanglementSnapshot snapshot = session.snapshot();
            return snapshot == null ? "—" : (snapshot.page + 1) + " / " + snapshot.pages();
        }, 493, 299, 73, 10, 0.75f, BODY));
        body.child(
            button("next", () -> session.page(1)).pos(572, 296)
                .size(24, 16));
        body.child(line(316));
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

    private static int metricColor(EntanglementTerminalSession session, int index) {
        EntanglementSnapshot snapshot = session.snapshot();
        if (snapshot == null) return MUTED;
        if (index == 3 && !snapshot.critical) return 0xff94bfdf;
        double fraction = progress(session, index);
        if (index > 0 && fraction >= 0.8d) return GtsrGuiPalette.STATE_OFFLINE;
        if (index == 2 && fraction <= 0.1d) return GtsrGuiPalette.STATE_ONLINE;
        return new int[] { 0xffffc45a, 0xffff9359, 0xffead06a, 0xffcba1ff }[index];
    }

    private static int fluidColor(EntanglementTerminalSession session, int slot) {
        EntanglementSnapshot.Row row = session.row(slot);
        return row == null ? MUTED
            : new int[] { 0xffc5deee, 0xffffc98c, 0xffff9d9d, 0xffffa53d, 0xff75d5ee, 0xffcf9eff }[row.kind];
    }

    private static double nextValue(EntanglementMath.Forecast forecast, int index) {
        return new double[] { forecast.heat, forecast.overlimit, forecast.instability, forecast.tear }[index];
    }

    private static String metricDelta(EntanglementTerminalSession session, int index) {
        EntanglementSnapshot snapshot = session.snapshot();
        EntanglementMath.Forecast forecast = session.forecast();
        if (snapshot == null || forecast == null) return "—";
        if (index == 3 && !snapshot.critical) return tr("fixed_state");
        double delta = nextValue(forecast, index) - value(snapshot, index);
        String format = delta != 0 && Math.abs(delta) < 0.001d ? "%+.5f%%/s" : "%+.3f%%/s";
        return String.format(Locale.ROOT, format, delta);
    }

    private static int deltaColor(EntanglementTerminalSession session, int index) {
        EntanglementSnapshot snapshot = session.snapshot();
        EntanglementMath.Forecast forecast = session.forecast();
        if (snapshot == null || forecast == null || index == 3 && !snapshot.critical) return MUTED;
        double delta = nextValue(forecast, index) - value(snapshot, index);
        if (Math.abs(delta) < 1e-10) return MUTED;
        boolean favorable = index == 0 ? delta > 0 || forecast.output : delta < 0;
        return favorable ? GtsrGuiPalette.STATE_ONLINE : GtsrGuiPalette.STATE_OFFLINE;
    }

    private static int statusColor(EntanglementTerminalSession session) {
        EntanglementSnapshot snapshot = session.snapshot();
        if (snapshot == null) return MUTED;
        if (!snapshot.formed) return GtsrGuiPalette.STATE_OFFLINE;
        if (!snapshot.allowed) return MUTED;
        return snapshot.cycling || EntanglementMath.grade(snapshot.amounts) >= 0 ? GtsrGuiPalette.STATE_ONLINE
            : GtsrGuiPalette.STATE_IDLE;
    }

    private static int warningColor(EntanglementTerminalSession session) {
        EntanglementSnapshot snapshot = session.snapshot();
        EntanglementMath.Forecast forecast = session.forecast();
        if (snapshot == null || forecast == null) return MUTED;
        if (!session.canApply() && !session.receipt()
            .equals("pending")) return GtsrGuiPalette.STATE_OFFLINE;
        if (forecast.overlimitExplosion || forecast.collapse
            || forecast.tearExplosion
            || forecast.chill
            || forecast.solidify) return GtsrGuiPalette.STATE_OFFLINE;
        return snapshot.formed && snapshot.allowed && snapshot.outputSpace && forecast.grade >= 0
            ? GtsrGuiPalette.STATE_ONLINE
            : GtsrGuiPalette.STATE_IDLE;
    }

    private static String metricRule(EntanglementTerminalSession session, int index, boolean second) {
        EntanglementSnapshot snapshot = session.snapshot();
        if (index == 3 && (snapshot == null || !snapshot.critical)) return tr("sse_solid");
        return tr(
            second ? new String[] { "rule_4", "rule_6", "rule_9", "rule_9" }[index]
                : new String[] { "rule_3", "rule_5", "rule_7", "rule_8" }[index]);
    }

    private static String fluidRule(EntanglementTerminalSession session, int slot) {
        EntanglementSnapshot.Row row = session.row(slot);
        return row == null ? "" : tr(row.kind < 3 ? "rule_1" : row.kind == 5 ? "rule_9" : "rule_5");
    }

    private static String heatEquation(EntanglementTerminalSession session) {
        EntanglementSnapshot snapshot = session.snapshot();
        EntanglementMath.Forecast forecast = session.forecast();
        if (snapshot == null || forecast == null) return tr("syncing");
        long[] requested = session.requested();
        int grade = EntanglementMath.grade(requested);
        long amount = grade < 0 ? 0 : requested[grade];
        double coefficient = grade < 0 ? 0 : new double[] { 0.5d, 1, 2 }[grade];
        double saturation = amount / (amount + (snapshot.critical ? 5000.0d : 200000.0d));
        String equation = "§e0.500% §7× §b" + number(
            coefficient) + " §7× §f" + number(saturation) + " §7× §6" + number(1 + snapshot.overlimit / 100);
        if (snapshot.critical) equation += " §7× §d" + number(1 + snapshot.tear / 100);
        if (!snapshot.formed || !snapshot.allowed) equation += " §7× §c0";
        return equation + " §7= " + (forecast.heatGain > 0 ? "§a" : "§7") + "§l+" + number(forecast.heatGain) + "%/s";
    }

    private static String saturationEquation(EntanglementTerminalSession session) {
        EntanglementSnapshot snapshot = session.snapshot();
        if (snapshot == null) return tr("syncing");
        long[] requested = session.requested();
        int grade = EntanglementMath.grade(requested);
        long amount = grade < 0 ? 0 : requested[grade], half = snapshot.critical ? 5000 : 200000;
        return tr("formula_saturation") + " = "
            + GtsrNumFormat.grouped(amount)
            + " / ("
            + GtsrNumFormat.grouped(amount)
            + " + "
            + GtsrNumFormat.grouped(half)
            + ") = "
            + number(amount / (amount + (double) half));
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
        return tr("next_second") + " " + number(nextValue(forecast, index)) + "%";
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
        if (row.kind < 3) return tr("heat") + " §l+" + number(forecast.heatGain) + "%";
        double delta = row.kind == 3 ? EntanglementMath.pyrotheum(requested[3])
            : row.kind == 4 ? EntanglementMath.cryotheum(requested[4]) : 0.001d * requested[5];
        return tr(row.kind == 3 ? "overlimit" : row.kind == 4 ? "instability" : "tear")
            + (row.kind == 3 ? " §l+" : " §l−")
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
        private final Rectangle full = new Rectangle();
        private final java.util.function.IntSupplier color;

        Meter(DoubleSupplier value, java.util.function.IntSupplier color) {
            this.value = value;
            this.color = color;
        }

        @SideOnly(Side.CLIENT)
        @Override
        public void draw(GuiContext context, int x, int y, int width, int height, WidgetTheme theme) {
            empty.draw(context, x, y, width, height, theme);
            full.color(color.getAsInt());
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
