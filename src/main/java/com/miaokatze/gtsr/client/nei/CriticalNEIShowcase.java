package com.miaokatze.gtsr.client.nei;

import static com.miaokatze.gtsr.api.recipe.CriticalNEILayout.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;
import com.gtnewhorizon.gtnhlib.util.numberformatting.options.FormatOptions;
import com.miaokatze.gtsr.api.recipe.CriticalRecipeMaps;
import com.miaokatze.gtsr.api.recipe.CriticalRecipeMaps.DisplayInfo;
import com.miaokatze.gtsr.common.critical.CriticalConstructionProgress;
import com.miaokatze.gtsr.common.critical.CriticalEnergyMath;
import com.miaokatze.gtsr.common.critical.CriticalFoundationChunk;
import com.miaokatze.gtsr.common.critical.CriticalStellarPolicy;
import com.miaokatze.gtsr.config.Config;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.enums.GTValues;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;
import gregtech.api.util.tooltip.TooltipHelper;
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

    public static void chemicalBackground(float x, float y, float width, float height, float partialTicks) {
        background(x, y, true);
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(x, y, 0);
            rect(5, CHEMICAL_DIVIDER_Y, 165, CHEMICAL_DIVIDER_Y + 1, EDGE);
        } finally {
            GL11.glPopMatrix();
        }
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
        label(info.solar == null ? text("intro." + operation) : text("solar.intro"), 8, 21, MUTED);
        boolean chemical = operation.equals("chemical_compound");
        if (!chemical) label(text("output"), OUTPUT_X, 32, MUTED);
        label(text(chemical ? "chemical.inputs" : "input"), INPUT_X, chemical ? 30 : 32, MUTED);
        if (chemical) {
            chemical(info, new Plot(flow(info, true)));
            chemicalRows(
                CriticalRecipeMaps.formatted("simple.chemical.efficiency", info.compound.peakMultiplier),
                CriticalRecipeMaps
                    .formatted("simple.chemical.input_heat", number(new java.math.BigDecimal(info.inputHeat))),
                CriticalRecipeMaps
                    .formatted("simple.chemical.output_heat", number(new java.math.BigDecimal(info.baseEu))));
        } else if (operation.startsWith("stellar_") || operation.equals("sun")) {
            stellar(operation, new Plot(flow(info, true)));
            if (operation.equals("stellar_ignition")) {
                rows(
                    text("stellar.phase_time"),
                    power(CriticalStellarPolicy.IGNITION_EU / CriticalStellarPolicy.DURATION_TICKS),
                    CriticalRecipeMaps.formatted(
                        "simple.ignition.helium",
                        number(java.math.BigDecimal.valueOf(CriticalStellarPolicy.IGNITION_HELIUM3_L))),
                    text("ignition.once"));
            } else if (operation.equals("stellar_sustain")) {
                rows(
                    text("stellar.phase_time"),
                    power(CriticalStellarPolicy.SUSTAIN_EUT),
                    CriticalRecipeMaps.formatted(
                        "simple.sustain.helium",
                        number(
                            java.math.BigDecimal.valueOf(
                                CriticalStellarPolicy.DURATION_TICKS
                                    * CriticalStellarPolicy.SUSTAIN_HELIUM3_L_PER_TICK))),
                    text("sustain.always"));
            } else {
                rows(
                    text("fusion.time"),
                    power(info.baseEu.longValueExact()),
                    CriticalRecipeMaps.formatted("simple.fusion.discount", fusionDiscount(info)));
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
                CriticalRecipeMaps.formatted("simple.turbine.warmup", CriticalEnergyMath.TURBINE_WARMUP_TICKS / 20),
                text("turbine.cycle"),
                text("turbine.condensation"));
        } else if (operation.equals("solar_dust")) {
            irradiation(new Plot(flow(info, false)));
            rows(
                text("irradiation.cycle"),
                text("irradiation.parallel"),
                text("irradiation.no_energy"),
                text("irradiation.output"));
        } else {
            boiler(operation.equals("solar_salt"), new Plot(flow(info, true)));
            if (info.solar != null) rows(solarRows(info.solar));
        }
    }

    /** Seven native-font lines: quantities describe the frozen funded batch, never the int slot ratio. */
    public static String[] solarRows(CriticalRecipeMaps.SolarDisplay solar) {
        boolean salt = solar.mode == CriticalRecipeMaps.SteamMode.SALT;
        return new String[] { CriticalRecipeMaps
            .formatted("simple.solar.formula", solarNumber(solar.coefficient), solarNumber(solar.halfSaturationStock)),
            text("solar.variables"), text("solar.multiplier"),
            CriticalRecipeMaps.formatted(
                "simple.solar.example",
                solarNumber(solar.exampleStock),
                number(java.math.BigDecimal.valueOf(solar.multiplier))),
            CriticalRecipeMaps
                .formatted("simple.solar.input." + (salt ? "salt" : "water"), solarNumber(solar.inputLitres)),
            CriticalRecipeMaps.formatted("simple.solar.output", solarNumber(solar.outputLitres)),
            CriticalRecipeMaps.formatted("simple.solar.heat", solarNumber(solar.heatEU)) };
    }

    private static String solarNumber(java.math.BigInteger value) {
        java.math.BigDecimal amount = new java.math.BigDecimal(value);
        java.math.BigDecimal tera = java.math.BigDecimal.valueOf(1_000_000_000_000L);
        java.math.BigDecimal giga = java.math.BigDecimal.valueOf(1_000_000_000L);
        if (amount.compareTo(tera) >= 0) return number(amount.divide(tera)) + "T";
        if (amount.compareTo(giga) >= 0) return number(amount.divide(giga)) + "G";
        return number(amount);
    }

    private static void construction(DisplayInfo info) {
        label(text("build.title"), 8, 6, GOLD);
        label(text("build.target." + info.kind.key), 8, 20, INK);
        int group = info.constructionGroup();
        for (int i = 0; i < 3; i++) {
            int x = 8 + i * 53;
            frame(x, 31, 48, 13, i <= group ? GOLD : EDGE);
            if (i < group) rect(x + 1, 32, x + 47, 43, 0xFF3D443F);
            if (i == group) {
                rect(x + 1, 32, x + 47, 43, 0xFF5B5140);
                rect(x + 1, 43, x + 2 + phase(45), 44, GOLD);
            }
            centered(text("build.group." + i), x + 24, 33, i <= group ? GOLD : MUTED);
        }
        for (int row = 0; row < 3; row++) {
            int y = BUILD_Y + row * BUILD_ROW_PITCH;
            int ticks = info.constructionStage() != 0 ? CriticalConstructionProgress.STAGE_DURATION
                : row == 0 ? CriticalFoundationChunk.CELL_DURATION
                    : row == 1 ? CriticalFoundationChunk.HEATING_DURATION : CriticalFoundationChunk.CURING_DURATION;
            int time = ticks / (info.constructionStage() == 0 && row == 1 ? 20 * 60
                : info.constructionStage() == 0 && row == 2 ? 20 * 60 * 60 : 20);
            String unit = info.constructionStage() == 0 && row == 1 ? "minutes"
                : info.constructionStage() == 0 && row == 2 ? "hours"
                    : info.constructionStage() == 0 && row == 0 ? "cell" : "step";
            String duration = info.constructionStage() == 0
                ? CriticalRecipeMaps.formatted("simple.build.time." + unit, time)
                : CriticalRecipeMaps.formatted("simple.build.time.steps", time, info.constructionSteps(row));
            var font = Minecraft.getMinecraft().fontRenderer;
            int timeX = BUILD_TIME_RIGHT_X - font.getStringWidth(duration);
            String title = text("build.row." + info.constructionStage() + "." + row);
            if (info.constructionStage() != 0 || row == 2) {
                // Two native-font lines fit above a row without touching the previous slots.
                // Keep five pixels before the right-aligned duration.
                var lines = font.listFormattedStringToWidth(title, timeX - BUILD_X - 5);
                int titleY = lines.size() == 1 ? y - 11 : y - 18;
                for (String line : lines) {
                    label(line, BUILD_X, titleY, INK);
                    titleY += 9;
                }
            } else label(title, BUILD_X, y - 11, INK);
            label(duration, timeX, y - 11, MUTED);
        }
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
        int x = p.flow.centerX - CHEMICAL_BAR_WIDTH / 2;
        label(text("chemical.bar"), x - 5, 29, MUTED);
        frame(x, CHEMICAL_BAR_Y, CHEMICAL_BAR_WIDTH, CHEMICAL_BAR_HEIGHT, EDGE);
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(x, CHEMICAL_BAR_Y + CHEMICAL_BAR_HEIGHT - 2, 0);
            GL11.glScalef(1, info.compound.peakMultiplier / 100.0F, 1);
            rect(2, -(CHEMICAL_BAR_HEIGHT - 4), CHEMICAL_BAR_WIDTH - 2, 0, GOLD);
        } finally {
            GL11.glPopMatrix();
        }
        label("100x", x + CHEMICAL_BAR_WIDTH + 4, CHEMICAL_BAR_Y, GOLD);
        label("1x", x + CHEMICAL_BAR_WIDTH + 4, CHEMICAL_BAR_Y + CHEMICAL_BAR_HEIGHT - 9, MUTED);
    }

    public static double chemicalFillHeight(int exampleMultiplier) {
        return (CHEMICAL_BAR_HEIGHT - 4) * exampleMultiplier / 100.0;
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

    public static String power(long eut) {
        // GT205 TooltipHelper.euRateText style; keep full integers even above its default
        // exponent threshold by passing the native formatter an explicit display option.
        String value = TooltipHelper.EU_VOLT_COLOR
            + NumberFormatUtil.formatNumber(
                eut,
                new FormatOptions().setDecimalPlaces(0)
                    .disableExponentialFormatting())
            + TooltipHelper.TIER_COLOR
            + " EU/t"
            + net.minecraft.util.EnumChatFormatting.GRAY;
        int maximum = gregtech.api.enums.VoltageIndex.MAX;
        int tier = Math.max(1, Math.min(maximum, GTUtility.getTier(eut)));
        long amps = eut <= GTValues.V[maximum] ? 1
            : eut / GTValues.V[maximum] + (eut % GTValues.V[maximum] == 0 ? 0 : 1);
        return value + " ("
            + TooltipHelper.AMP_COLOR
            + NumberFormatUtil.formatNumber(
                amps,
                new FormatOptions().setDecimalPlaces(0)
                    .disableExponentialFormatting())
            + "A "
            + GTUtility.getColoredTierNameFromTier((byte) tier)
            + ")";
    }

    private static String maxAmps(int amps) {
        return TooltipHelper.AMP_COLOR + NumberFormatUtil.formatNumber(
            amps,
            new FormatOptions().setDecimalPlaces(0)
                .disableExponentialFormatting())
            + "A "
            + GTUtility.getColoredTierNameFromTier((byte) gregtech.api.enums.VoltageIndex.MAX)
            + net.minecraft.util.EnumChatFormatting.GRAY;
    }

    public static String fusionDiscount(DisplayInfo info) {
        if (info.nativeEut <= 0) return "0";
        return number(
            java.math.BigDecimal.valueOf(info.nativeEut)
                .subtract(new java.math.BigDecimal(info.baseEu))
                .multiply(java.math.BigDecimal.valueOf(100))
                .divide(java.math.BigDecimal.valueOf(info.nativeEut), 4, java.math.RoundingMode.HALF_UP));
    }

    private static String number(java.math.BigDecimal value) {
        java.text.DecimalFormat format = new java.text.DecimalFormat(
            "#,##0.####",
            java.text.DecimalFormatSymbols.getInstance(java.util.Locale.ROOT));
        return format.format(value);
    }

    private static void chemicalRows(String... rows) {
        int y = CHEMICAL_TEXT_Y;
        for (int i = 0; i < rows.length; i++) {
            for (String line : Minecraft.getMinecraft().fontRenderer
                .listFormattedStringToWidth(rows[i], CHEMICAL_TEXT_WIDTH)) {
                label(line, 8, y, i == 0 ? GOLD : INK);
                y += 11;
            }
        }
    }

    private static void rows(String... rows) {
        int y = 97;
        var font = Minecraft.getMinecraft().fontRenderer;
        for (int i = 0; i < rows.length; i++) {
            java.util.List<String> lines;
            int suffix = rows[i].lastIndexOf(" (");
            if (rows[i].contains(" EU/t") && suffix >= 0 && font.getStringWidth(rows[i]) > 154) {
                // Keep the complete current/tier suffix together when the paid number needs its own line.
                lines = new java.util.ArrayList<>(font.listFormattedStringToWidth(rows[i].substring(0, suffix), 154));
                lines.add(net.minecraft.util.EnumChatFormatting.GRAY + rows[i].substring(suffix + 1));
            } else lines = font.listFormattedStringToWidth(rows[i], 154);
            for (String line : lines) {
                label(line, 8, y, i == 0 ? GOLD : i == 4 ? MUTED : INK);
                y += 11;
            }
        }
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
