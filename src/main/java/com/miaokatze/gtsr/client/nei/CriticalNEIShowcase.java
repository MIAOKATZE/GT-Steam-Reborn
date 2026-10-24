package com.miaokatze.gtsr.client.nei;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.resources.IReloadableResourceManager;

import org.lwjgl.opengl.GL11;

import com.miaokatze.gtsr.api.recipe.CriticalRecipeMaps;
import com.miaokatze.gtsr.api.recipe.CriticalRecipeMaps.DisplayInfo;
import com.miaokatze.gtsr.config.Config;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.util.GTRecipe;
import gregtech.nei.GTNEIDefaultHandler;

/** Fixed-budget 2D engineering cards. Text is native 1x, with native NEI stack hit boxes. */
@SideOnly(Side.CLIENT)
public final class CriticalNEIShowcase {

    private static final int STEEL = 0xFF26313A, EDGE = 0xFF53646F, COPPER = 0xFFC48A50;
    private static final int GOLD = 0xFFEAC879, INK = 0xFFE1E8EB, CYAN = 0xFF8DD0DC;
    private static final int MUTED = 0xFFADBDC6, DARK = 0xFF17212A;
    private static final int CACHE_LIMIT = 256;
    private static final String[] WINDOW_LABELS = { "1 x8", "2 x20", "3 x8", "4 x1" };
    private static final Map<GTRecipe, Page> PAGES = new LinkedHashMap<>(32, .75f, true);
    private static String language = "";

    private CriticalNEIShowcase() {}

    public static void registerReloadListener() {
        if (Minecraft.getMinecraft()
            .getResourceManager() instanceof IReloadableResourceManager resources)
            resources.registerReloadListener(manager -> PAGES.clear());
    }

    public static void background(float x, float y, float width, float height, float partialTicks) {
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(x, y, 0);
            rect(0, 0, 170, 174, 0xFF10171D);
            rect(1, 1, 169, 173, EDGE);
            rect(2, 2, 168, 172, STEEL);
            rect(3, 3, 167, 18, DARK);
            rect(3, 18, 167, 19, COPPER);
            rect(8, 153, 162, 154, EDGE);
        } finally {
            GL11.glPopMatrix();
        }
    }

    public static void draw(GTRecipe recipe) {
        DisplayInfo info = CriticalRecipeMaps.displayInfo(recipe);
        if (info == null) return;
        Page page = page(recipe, info);
        label(page.title, 8, 7, GOLD);
        if (info.construction()) construction(info, page);
        else runtime(info, page);
        label(page.footer, 8, 160, MUTED);
    }

    private static Page page(GTRecipe recipe, DisplayInfo info) {
        String current = Minecraft.getMinecraft().gameSettings.language;
        if (!current.equals(language)) {
            language = current;
            PAGES.clear();
        }
        Page cached = PAGES.get(recipe);
        if (cached != null) return cached;
        boolean construction = info.construction();
        String key = "card." + (construction ? "build" : info.operation);
        String title = CriticalRecipeMaps.text(key + ".title");
        String subtitle = construction ? CriticalRecipeMaps.text("card.kind." + info.kind.key)
            : CriticalRecipeMaps.text(key + ".section." + info.page);
        String body = construction
            ? CriticalRecipeMaps.text("card.build.body." + (info.constructionStage() == 0 ? "foundation" : "physical"))
            : CriticalRecipeMaps
                .formatted(key + ".body." + info.page, compact(info.baseEu), info.spectrum + 1, recipe.mDuration);
        List<String> lines = new ArrayList<>();
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        for (String row : body.split("\\|"))
            for (Object wrapped : font.listFormattedStringToWidth(row, 154)) lines.add((String) wrapped);
        String footer = construction ? CriticalRecipeMaps.formatted("card.build.footer", info.page + 1, info.pages)
            : CriticalRecipeMaps.formatted("card.footer", info.page + 1, info.pages);
        cached = new Page(title, subtitle, footer, lines.toArray(new String[0]));
        PAGES.put(recipe, cached);
        if (PAGES.size() > CACHE_LIMIT) PAGES.remove(
            PAGES.keySet()
                .iterator()
                .next());
        return cached;
    }

    /** Exact EU values remain in tooltips; large figures use engineering notation on the card. */
    private static String compact(BigInteger value) {
        String digits = value.toString();
        if (digits.length() <= 10) return digits;
        return digits.charAt(0) + "." + digits.substring(1, 4) + "e" + (digits.length() - 1);
    }

    private static void construction(DisplayInfo info, Page page) {
        int stage = info.constructionStage();
        label(page.subtitle, 8, 24, INK);
        // Foundation cells, then the four actual paid voxel stages; material pages do not alter payment.
        for (int step = 0; step < 5; step++) {
            int x = 8 + step * 26;
            rect(x, 37, x + 23, 40, step <= stage ? COPPER : DARK);
            if (step == stage) {
                int fill = Config.criticalUiReducedMotion ? 12 : GTNEIDefaultHandler.getDrawTicks() / 3 % 23;
                rect(x, 37, x + fill, 40, GOLD);
            }
        }
        label((stage + 1) + "/5 " + CriticalRecipeMaps.text("card.build.stage." + stage), 8, 47, CYAN);
        lines(page.lines, 8, 110);
    }

    private static void runtime(DisplayInfo info, Page page) {
        label(page.subtitle, 8, 24, INK);
        // Inputs x=10,32; output x=142; all y=40. Graph never overlaps a native slot.
        rect(56, 48, 139, 49, EDGE);
        rect(135, 45, 137, 52, COPPER);
        rect(137, 46, 139, 51, COPPER);
        rect(139, 47, 140, 50, COPPER);
        int pulse = Config.criticalUiReducedMotion ? 37 : GTNEIDefaultHandler.getDrawTicks() % 74;
        rect(59 + pulse, 47, 62 + pulse, 50, GOLD);
        if (info.operation.equals("battery")) label("EU", 144, 45, GOLD);
        diagram(info);
        lines(page.lines, 8, 94);
    }

    private static void diagram(DisplayInfo info) {
        int tick = Config.criticalUiReducedMotion ? 12 : GTNEIDefaultHandler.getDrawTicks() / 2;
        if (info.operation.startsWith("solar_")) {
            int active = info.operation.equals("solar_water") ? 0 : info.operation.equals("solar_salt") ? 1 : 2;
            for (int lane = 0; lane < 3; lane++) {
                int x = 8 + lane * 53;
                rect(x, 63, x + 48, 86, DARK);
                label(CriticalRecipeMaps.text("card.solar.lane." + lane), x + 4, 66, lane == active ? GOLD : MUTED);
                rect(x + 4, 80, x + 44, 82, lane == active ? COPPER : EDGE);
                if (lane == active) {
                    int cursor = tick % 34;
                    rect(x + 5 + cursor, 79, x + 9 + cursor, 83, GOLD);
                }
            }
        } else if (info.operation.equals("turbine")) {
            label("1x", 8, 66, CYAN);
            label("20x", 141, 66, GOLD);
            for (int i = 0; i < 10; i++) rect(27 + i * 11, 67, 35 + i * 11, 73, i <= tick / 4 % 10 ? COPPER : DARK);
            rect(28, 81, 146, 82, EDGE);
            int cursor = tick % 108;
            rect(139 - cursor, 80, 143 - cursor, 83, CYAN);
            rect(26, 79, 28, 84, CYAN);
        } else if (info.operation.equals("sun")) {
            for (int state = 0; state < 3; state++) {
                int x = 8 + state * 53;
                rect(x, 65, x + 48, 83, DARK);
                rect(x, 65, x + 48, 67, state == info.page ? COPPER : EDGE);
                label(CriticalRecipeMaps.text("card.sun.state." + state), x + 4, 70, state == info.page ? GOLD : MUTED);
                if (state == info.page) {
                    int energy = tick % 38;
                    rect(x + 4, 80, x + 44, 82, EDGE);
                    rect(x + 4 + energy, 80, x + 6 + energy, 82, GOLD);
                }
            }
        } else {
            for (int window = 0; window < 4; window++) {
                int x = 8 + window * 39;
                rect(x, 65, x + 36, 84, DARK);
                rect(x, 65, x + 36, 67, window == tick / 20 % 4 ? COPPER : EDGE);
                label(WINDOW_LABELS[window], x + 3, 73, CYAN);
            }
        }
    }

    private static void lines(String[] lines, int x, int y) {
        // Pagination is authored in the catalogue, never hidden by truncation or smaller fonts.
        for (int i = 0; i < lines.length; i++) label(lines[i], x, y + i * 10, INK);
    }

    private static void label(String text, int x, int y, int color) {
        Minecraft.getMinecraft().fontRenderer.drawString(text, x, y, color);
    }

    private static void rect(int x1, int y1, int x2, int y2, int color) {
        Gui.drawRect(x1, y1, x2, y2, color);
    }

    private static final class Page {

        final String title, subtitle, footer;
        final String[] lines;

        Page(String title, String subtitle, String footer, String[] lines) {
            this.title = title;
            this.subtitle = subtitle;
            this.footer = footer;
            this.lines = lines;
        }
    }
}
