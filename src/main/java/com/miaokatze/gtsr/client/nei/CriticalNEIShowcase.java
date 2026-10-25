package com.miaokatze.gtsr.client.nei;

import static com.miaokatze.gtsr.api.recipe.CriticalNEILayout.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;

import org.lwjgl.opengl.GL11;

import com.miaokatze.gtsr.api.recipe.CriticalRecipeMaps;
import com.miaokatze.gtsr.api.recipe.CriticalRecipeMaps.DisplayInfo;
import com.miaokatze.gtsr.common.critical.CriticalChemicalMath;
import com.miaokatze.gtsr.common.critical.CriticalEnergyMath;
import com.miaokatze.gtsr.common.critical.CriticalStellarPolicy;
import com.miaokatze.gtsr.config.Config;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.enums.GTValues;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;
import gregtech.nei.GTNEIDefaultHandler;

/** Native slots and labels surround operation-specific, two-dimensional process diagrams. */
@SideOnly(Side.CLIENT)
public final class CriticalNEIShowcase {

    private static final int INK = 0xFFE1E8EB, MUTED = 0xFFADBDC6, GOLD = 0xFFEAC879;
    private static final int EDGE = 0xFF53646F, DARK = 0xFF17212A;
    private static final int WATER = 0xFF75CAD8, HEAT = 0xFFF1AB70, PLASMA = 0xFFD2A4EB;
    private static final int[] SPECTRA = { HEAT, WATER, PLASMA };

    private CriticalNEIShowcase() {}

    public static void registerReloadListener() {
        // Labels use the live language table; no cached text survives a resource reload.
    }

    public static void background(float x, float y, float width, float height, float partialTicks) {
        background(x, y, false);
    }

    public static void constructionBackground(float x, float y, float width, float height, float partialTicks) {
        background(x, y, true);
    }

    private static void background(float x, float y, boolean construction) {
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(x, y, 0);
            rect(0, 0, WIDTH, HEIGHT, DARK);
            rect(1, 1, WIDTH - 1, HEIGHT - 1, 0xFF26313A);
            rect(5, 17, 165, 18, EDGE);
            if (!construction) rect(5, 91, 165, 92, EDGE);
        } finally {
            GL11.glPopMatrix();
        }
    }

    public static void draw(GTRecipe recipe) {
        DisplayInfo info = CriticalRecipeMaps.displayInfo(recipe);
        if (info == null) return;
        if (info.construction()) {
            construction(info);
            return;
        }
        String operation = info.operation;
        label(text(operation + ".title"), 8, 6, GOLD);
        label(text("intro." + operation), 8, 21, MUTED);
        label(text("input"), INPUT_X, 32, MUTED);
        boolean chemical = operation.equals("battery") || operation.startsWith("chemical_window_");
        label(text(chemical ? "energy" : "output"), OUTPUT_X, 32, MUTED);
        if (chemical) {
            chemical(info, new Plot(flow(info, true)));
            label("EU", OUTPUT_X, FLUID_Y + 5, GOLD);
            if (operation.equals("battery")) {
                rows(
                    text("spectrum." + info.spectrum),
                    number(new java.math.BigDecimal(info.baseEu)) + " EU/L",
                    text("chemical.normal"),
                    text("chemical.cap"),
                    text("chemical.cycle"));
            } else {
                java.math.BigDecimal average = new java.math.BigDecimal(info.baseEu)
                    .divide(java.math.BigDecimal.valueOf(CriticalEnergyMath.CYCLE_TICKS));
                rows(
                    CriticalRecipeMaps.formatted("simple.chemical.average", number(average)),
                    voltage(
                        average.setScale(0, java.math.RoundingMode.CEILING)
                            .longValueExact(),
                        true),
                    text("chemical.ratio." + info.spectrum),
                    text("chemical.heat"),
                    text("chemical.cycle"));
            }
        } else if (operation.startsWith("stellar_") || operation.equals("sun")) {
            stellar(operation, new Plot(flow(info, true)));
            if (operation.equals("stellar_ignition")) {
                rows(
                    text("stellar.phase_time"),
                    power(CriticalStellarPolicy.IGNITION_EU / CriticalStellarPolicy.DURATION_TICKS),
                    voltage(CriticalStellarPolicy.IGNITION_EU / CriticalStellarPolicy.DURATION_TICKS),
                    text("ignition.helium"),
                    text("ignition.once"));
            } else if (operation.equals("stellar_sustain")) {
                rows(
                    text("stellar.phase_time"),
                    power(CriticalStellarPolicy.SUSTAIN_EUT),
                    voltage(CriticalStellarPolicy.SUSTAIN_EUT),
                    text("sustain.helium"),
                    text("sustain.always"));
            } else {
                rows(text("fusion.time"), text("fusion.native"));
            }
        } else if (operation.equals("turbine")) {
            turbine(new Plot(flow(info, true)));
            rows(
                CriticalRecipeMaps.formatted(
                    "simple.turbine.heat_per_l",
                    number(
                        new java.math.BigDecimal(info.baseEu)
                            .divide(java.math.BigDecimal.valueOf(recipe.mFluidInputs[0].amount)))),
                CriticalRecipeMaps.formatted(
                    "simple.turbine.efficiency",
                    CriticalEnergyMath.MIN_EFFICIENCY / 100,
                    CriticalEnergyMath.MAX_EFFICIENCY / 100),
                CriticalRecipeMaps.formatted("simple.turbine.trend", CriticalEnergyMath.EFFICIENCY_STEP / 100.0),
                text("turbine.cycle"),
                text("turbine.condensation"));
        } else if (operation.equals("solar_dust")) {
            irradiation(new Plot(flow(info, false)));
            rows(
                text("irradiation.cycle"),
                text("irradiation.parallel"),
                text("irradiation.shared"),
                text("irradiation.no_energy"),
                text("irradiation.output"));
        } else {
            boiler(operation.equals("solar_salt"), new Plot(flow(info, true)));
            rows(
                text("solar.cycle"),
                text("solar.stock"),
                text("solar.dimension"),
                text(operation.equals("solar_salt") ? "solar.salt" : "solar.water"),
                text("solar.night"));
        }
    }

    private static void construction(DisplayInfo info) {
        label(text("build.title"), 8, 6, GOLD);
        label(text("build.target." + info.kind.key), 8, 20, INK);
        int group = info.constructionGroup();
        for (int i = 0; i < 4; i++) {
            int x = 8 + i * 39;
            frame(x, 31, 37, 13, i <= group ? GOLD : EDGE);
            if (i < group) rect(x + 1, 32, x + 36, 43, 0xFF3D443F);
            if (i == group) {
                rect(x + 1, 32, x + 36, 43, 0xFF5B5140);
                rect(x + 1, 43, x + 2 + phase(34), 44, GOLD);
            }
            centered(text("build.group." + i), x + 18, 33, i <= group ? GOLD : MUTED);
        }
        for (int row = 0; row < 3; row++) {
            int y = BUILD_Y + row * BUILD_ROW_PITCH;
            label(text("build.row." + info.constructionStage() + "." + row), BUILD_X, y - 11, INK);
        }
        label(text("build.note." + info.constructionStage()), 8, 158, MUTED);
    }

    /** Both collection circuits remain visible; only this recipe's branch carries heat. */
    private static void boiler(boolean salt, Plot p) {
        for (int branch = 0; branch < 2; branch++) {
            int y = -20 + branch * 22;
            boolean active = salt == (branch == 1);
            int color = active ? (salt ? HEAT : WATER) : EDGE;
            p.line(-31, y + 7, -13, y + 7, color);
            p.frame(-13, y, 26, 16, color);
            for (int i = 0; i < 4; i++) {
                int level = active ? 4 + (phase(20) + i * 3) % 8 : 4;
                p.rect(-9 + i * 5, y + 14 - level, -6 + i * 5, y + 14, color);
            }
            p.line(13, y + 7, 31, y + 7, color);
            if (active) p.rect(15 + phase(14), y + 6, 18 + phase(14), y + 9, color);
        }
        int activeY = salt ? 9 : -13, color = salt ? HEAT : WATER;
        p.line(-31, 0, -31, activeY, color);
        p.line(31, activeY, 31, 0, color);
        p.input(color);
        p.output(color);
    }

    /** A ratchet rotor drives the power shaft; the lower pipe closes the condensate loop. */
    private static void turbine(Plot p) {
        p.input(HEAT);
        p.line(-31, 0, -31, -5, HEAT);
        p.line(-31, -5, -14, -5, HEAT);
        p.ring(0, -5, 14, EDGE);
        p.ring(0, -5, 11, HEAT);
        int rampTicks = 240;
        double t = phase(rampTicks);
        double ratio = (double) CriticalEnergyMath.MAX_EFFICIENCY / CriticalEnergyMath.MIN_EFFICIENCY;
        double angle = Math.PI / 32 * (t + (ratio - 1) * t * t / (2 * rampTicks));
        for (int i = 0; i < 6; i++) {
            double a = angle + i * Math.PI / 3;
            int x = (int) (Math.cos(a) * 11), y = -5 + (int) (Math.sin(a) * 11);
            p.line(0, -5, x, y, GOLD);
            p.line(x, y, x + (int) (Math.cos(a + 1) * 3), y + (int) (Math.sin(a + 1) * 3), GOLD);
            if (i == 0) p.rect(x - 1, y - 1, x + 2, y + 2, PLASMA);
        }
        p.rect(-2, -7, 3, -2, INK);
        p.line(14, -5, 28, -5, GOLD);
        p.chevron(29, -5, GOLD);
        p.line(31, 0, 31, 15, WATER);
        p.line(31, 15, -29, 15, WATER);
        p.line(-29, 15, -29, 7, WATER);
        p.rect(28 - phase(56), 14, 31 - phase(56), 17, WATER);
        p.output(WATER);
    }

    private static void irradiation(Plot p) {
        p.input(GOLD);
        p.line(-31, 0, -24, 0, GOLD);
        p.ring(-8, 0, 10, GOLD);
        p.rect(-13, -5, -2, 6, HEAT);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            p.line(
                -8 + (int) (Math.cos(a) * 13),
                (int) (Math.sin(a) * 13),
                -8 + (int) (Math.cos(a) * 16),
                (int) (Math.sin(a) * 16),
                GOLD);
        }
        for (int i = 0; i < 3; i++) {
            int x = 11 + (phase(18) + i * 6) % 18;
            p.line(x, -6, x + 3, -9, PLASMA);
            p.line(x, 0, x + 3, 0, GOLD);
            p.line(x, 6, x + 3, 9, PLASMA);
        }
        p.output(GOLD);
        p.frame(23, 18, 6, 6, PLASMA);
        p.rect(12, 23, 15, 26, GOLD);
        p.rect(19, 27, 22, 30, HEAT);
    }

    /** Ignition expands, sustain orbits steadily, fusion feeds three converging streams. */
    private static void stellar(String operation, Plot p) {
        boolean ignition = operation.equals("stellar_ignition"), fusion = operation.equals("sun");
        int radius = ignition ? 6 + phase(28) / 4 : 12;
        p.input(ignition ? HEAT : WATER);
        p.line(-31, 0, -17, 0, ignition ? HEAT : WATER);
        p.ring(0, 0, radius + 3, PLASMA);
        p.ring(0, 0, radius, ignition ? HEAT : GOLD);
        if (fusion) {
            for (int i = 0; i < 3; i++) {
                int y = -14 + i * 14;
                p.line(-31, y, -12, 0, SPECTRA[i]);
                int x = -31 + phase(24) * 19 / 24;
                int py = y - y * (x + 31) / 19;
                p.rect(x, py, x + 3, py + 2, SPECTRA[i]);
            }
        } else {
            double angle = phase(64) * Math.PI / 32;
            for (int i = 0; i < 2; i++) {
                int x = (int) (Math.cos(angle + i * Math.PI) * (radius + 3));
                int y = (int) (Math.sin(angle + i * Math.PI) * (radius + 3));
                p.rect(x - 1, y - 1, x + 2, y + 2, ignition ? HEAT : WATER);
            }
        }
        p.line(16, 0, 31, 0, PLASMA);
        p.output(PLASMA);
        p.rect(-4, -4, 5, 5, HEAT);
        p.rect(-2, -2, 3, 3, GOLD);
        p.rect(0, -1, 2, 2, INK);
    }

    private static void chemical(DisplayInfo info, Plot p) {
        int window = info.operation.equals("battery") ? 3 : info.spectrum;
        for (int i = 0; i < 4; i++) {
            int x = -27 + i % 2 * 29, y = -29 + i / 2 * 21;
            p.frame(x, y, 26, 18, i == window ? GOLD : EDGE);
            if (i == window) p.rect(x + 1, y + 1, x + 25, y + 17, 0xFF4C473A);
            centered("x" + CriticalChemicalMath.multiplier(i), p.x(x + 13), p.y(y + 5), i == window ? GOLD : MUTED);
            if (i == window) p.rect(x + 2, y + 16, x + 3 + phase(22), y + 17, GOLD);
        }
        for (int i = 0; i < 3; i++) {
            int size = window == 0 && i == 2 ? 2 : window == 2 && i == 2 ? 16 : 9;
            p.rect(-27 + i * 18, 12, -27 + i * 18 + size, 16, SPECTRA[i]);
        }
        p.input(GOLD);
        p.line(28, -20, 31, -20, GOLD);
        p.line(31, -20, 31, 0, GOLD);
        p.output(GOLD);
    }

    private static int phase(int duration) {
        return Config.criticalUiReducedMotion ? duration / 2
            : Math.floorMod(GTNEIDefaultHandler.getDrawTicks(), duration);
    }

    private static void chevron(int x, int y, int color) {
        line(x - 3, y - 3, x, y, color);
        line(x, y, x - 3, y + 3, color);
    }

    private static void frame(int x, int y, int width, int height, int color) {
        rect(x, y, x + width, y + 1, color);
        rect(x, y + height - 1, x + width, y + height, color);
        rect(x, y, x + 1, y + height, color);
        rect(x + width - 1, y, x + width, y + height, color);
    }

    /** Diagram geometry is local to the real native slot gap; labels retain their native 1x font. */
    private static final class Plot {

        private final Flow flow;
        private final int width;

        private Plot(Flow flow) {
            this.flow = flow;
            width = flow.diagramWidth;
        }

        private int x(int offset) {
            return flow.centerX + Math.round(offset * width / 64.0F);
        }

        private int y(int offset) {
            return flow.centerY + offset;
        }

        private void rect(int x1, int y1, int x2, int y2, int color) {
            CriticalNEIShowcase.rect(x(x1), y(y1), x(x2), y(y2), color);
        }

        private void line(int x1, int y1, int x2, int y2, int color) {
            CriticalNEIShowcase.line(x(x1), y(y1), x(x2), y(y2), color);
        }

        private void frame(int x, int y, int width, int height, int color) {
            line(x, y, x + width - 1, y, color);
            line(x, y + height - 1, x + width - 1, y + height - 1, color);
            line(x, y, x, y + height - 1, color);
            line(x + width - 1, y, x + width - 1, y + height - 1, color);
        }

        private void ring(int x, int y, int radius, int color) {
            for (int i = 0; i < 24; i++) {
                double a = i * Math.PI / 12, b = (i + 1) * Math.PI / 12;
                line(
                    x + (int) Math.round(Math.cos(a) * radius),
                    y + (int) Math.round(Math.sin(a) * radius),
                    x + (int) Math.round(Math.cos(b) * radius),
                    y + (int) Math.round(Math.sin(b) * radius),
                    color);
            }
        }

        private void chevron(int x, int y, int color) {
            line(x - 3, y - 3, x, y, color);
            line(x, y, x - 3, y + 3, color);
        }

        private void input(int color) {
            CriticalNEIShowcase.line(flow.inputRight + 1, flow.centerY, x(-31), flow.centerY, color);
        }

        private void output(int color) {
            int edge = flow.outputLeft - 3;
            CriticalNEIShowcase.line(x(31), flow.centerY, edge, flow.centerY, color);
            CriticalNEIShowcase.chevron(edge, flow.centerY, color);
        }
    }

    private static void line(int x1, int y1, int x2, int y2, int color) {
        if (x1 == x2 || y1 == y2) {
            rect(Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2) + 1, Math.max(y1, y2) + 1, color);
            return;
        }
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        for (int i = 0; i <= steps; i++) {
            int x = x1 + (steps == 0 ? 0 : (x2 - x1) * i / steps);
            int y = y1 + (steps == 0 ? 0 : (y2 - y1) * i / steps);
            rect(x, y, x + 1, y + 1, color);
        }
    }

    private static String voltage(long eut) {
        return voltage(eut, false);
    }

    private static String voltage(long eut, boolean equivalent) {
        int maximum = Math.min(14, Math.min(GTValues.V.length, GTValues.VN.length) - 1);
        int tier = Math.max(0, Math.min(maximum, GTUtility.getTier(eut)));
        if (eut > GTValues.V[maximum]) {
            long amps = eut / GTValues.V[maximum] + (eut % GTValues.V[maximum] == 0 ? 0 : 1);
            return CriticalRecipeMaps.formatted(
                equivalent ? "simple.chemical.equivalent_amps" : "simple.stellar.voltage_amps",
                GTValues.VN[maximum],
                String.format(java.util.Locale.ROOT, "%,d", amps));
        }
        return CriticalRecipeMaps
            .formatted(equivalent ? "simple.chemical.equivalent_tier" : "simple.stellar.voltage", GTValues.VN[tier]);
    }

    private static String power(long eut) {
        return String.format(java.util.Locale.ROOT, "%,d EU/t", eut);
    }

    private static String number(java.math.BigDecimal value) {
        java.text.DecimalFormat format = new java.text.DecimalFormat(
            "#,##0.####",
            java.text.DecimalFormatSymbols.getInstance(java.util.Locale.ROOT));
        return format.format(value);
    }

    private static void rows(String... rows) {
        for (int i = 0; i < rows.length; i++) label(rows[i], 8, 97 + i * 13, i == 0 ? GOLD : i == 4 ? MUTED : INK);
    }

    private static String text(String key) {
        return CriticalRecipeMaps.text("simple." + key);
    }

    private static void centered(String text, int center, int y, int color) {
        label(text, center - Minecraft.getMinecraft().fontRenderer.getStringWidth(text) / 2, y, color);
    }

    private static void label(String text, int x, int y, int color) {
        Minecraft.getMinecraft().fontRenderer.drawString(text, x, y, color);
    }

    private static void rect(int x1, int y1, int x2, int y2, int color) {
        Gui.drawRect(x1, y1, x2, y2, color);
    }
}
