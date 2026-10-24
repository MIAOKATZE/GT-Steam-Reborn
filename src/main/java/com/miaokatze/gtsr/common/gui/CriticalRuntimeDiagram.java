package com.miaokatze.gtsr.common.gui;

import java.math.BigDecimal;
import java.util.function.Supplier;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.cleanroommc.modularui.theme.WidgetTheme;

/** Machine-specific, sampled process diagram; no interpolated measurements or synthetic history. */
final class CriticalRuntimeDiagram implements IDrawable {

    private static final int COPPER = 0xFFC48A50, GOLD = 0xFFEAC879, MUTED = 0xFF43535F, LIVE = 0xFF82B49B;
    private final Supplier<CriticalBusinessSnapshot> sample;

    CriticalRuntimeDiagram(Supplier<CriticalBusinessSnapshot> sample) {
        this.sample = sample;
    }

    @Override
    public void draw(GuiContext context, int x, int y, int width, int height, WidgetTheme theme) {
        CriticalBusinessSnapshot data = sample.get();
        int laneWidth = width - 52;
        switch (data.configuration().kind) {
            case BATTERY -> {
                for (int i = 0; i < 3; i++) {
                    int yy = y + 3 + i * 10;
                    GuiDraw.drawRect(x, yy, laneWidth, 6, MUTED);
                    float share = (float) Math.max(0, Math.min(1, number(data, "chemical.share." + i) / 100));
                    GuiDraw.drawRect(x, yy, laneWidth * share, 6, i == 0 ? COPPER : i == 1 ? GOLD : LIVE);
                }
                int window = (int) number(data, "chemical.window");
                for (int i = 0; i < 4; i++)
                    GuiDraw.drawRect(x + width - 42 + i * 10, y + 10, 8, 18, i == window ? GOLD : MUTED);
            }
            case TURBINE -> {
                for (int i = 0; i < 6; i++) {
                    int yy = y + 2 + i * 5;
                    GuiDraw.drawRect(x, yy, laneWidth, 2, number(data, "steam.volume." + i) > 0 ? COPPER : MUTED);
                }
                GuiDraw.drawRect(x + laneWidth, y + 2, 4, 29, GOLD);
                for (int i = 0; i < 5; i++)
                    GuiDraw.drawRect(x + laneWidth + 8 + i * 5, y + 4 + i % 2 * 5, 3, 26 - i % 2 * 10, COPPER);
            }
            case SOLAR -> {
                String[] branches = { "solar.water_status", "solar.salt_status", "solar.moon_dust" };
                for (int i = 0; i < 3; i++) {
                    String status = value(data, branches[i]);
                    int color = i == 2
                        ? number(data, "solar.moon_dust") + number(data, "solar.stone_dust") > 0 ? GOLD : MUTED
                        : "paid".equals(status) ? LIVE : "output_full".equals(status) ? COPPER : MUTED;
                    GuiDraw.drawRect(x, y + 3 + i * 10, 5, 6, color);
                    GuiDraw.drawRect(x + 8, y + 5 + i * 10, laneWidth + 25, 2, color);
                    GuiDraw.drawRect(x + laneWidth + 36, y + 3 + i * 10, 8, 6, color);
                }
            }
            case SUN -> {
                int phase = (int) number(data, "sun.phase");
                for (int i = 0; i < 4; i++)
                    GuiDraw.drawRect(x + i * 34, y + 10, 28, 12, phase < 5 && i == Math.min(3, phase) ? GOLD : MUTED);
                boolean paid = phase == 3 || phase == 4;
                GuiDraw.drawRect(x + width - 28, y + 3, 22, 28, paid ? COPPER : MUTED);
                GuiDraw.drawRect(x + width - 23, y + 8, 12, 18, paid ? GOLD : MUTED);
            }
            default -> {}
        }
    }

    private static String value(CriticalBusinessSnapshot data, String suffix) {
        for (int i = 0; i < data.runtimeCount(); i++)
            if (("ui.runtime." + suffix).equals(data.runtime(i, 0))) return data.runtime(i, 1);
        return "";
    }

    private static double number(CriticalBusinessSnapshot data, String suffix) {
        String value = value(data, suffix);
        int end = 0;
        while (end < value.length()
            && (Character.isDigit(value.charAt(end)) || value.charAt(end) == '.' || value.charAt(end) == '-')) end++;
        try {
            return end == 0 ? 0 : new BigDecimal(value.substring(0, end)).doubleValue();
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    @Override
    public boolean canApplyTheme() {
        return false;
    }
}
