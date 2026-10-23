package com.miaokatze.gtsr.client.nei;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

import org.lwjgl.opengl.GL11;

import com.miaokatze.gtsr.api.recipe.CriticalRecipeMaps;
import com.miaokatze.gtsr.api.recipe.CriticalRecipeMaps.DisplayInfo;
import com.miaokatze.gtsr.common.critical.CriticalMachineKind;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.util.GTRecipe;
import gregtech.nei.GTNEIDefaultHandler;

/** Hand-drawn steel/copper engineering cards. No world, dummy structure or per-frame geometry build. */
@SideOnly(Side.CLIENT)
public final class CriticalNEIShowcase {

    private static final int STEEL = 0xFF26313A, EDGE = 0xFF64737D, COPPER = 0xFFC48A50;
    private static final int GOLD = 0xFFEAC879, INK = 0xFFD6DEE2, CYAN = 0xFF79B9C8;
    private static final int CACHE_LIMIT = 256;
    private static final Map<GTRecipe, Page> PAGES = new LinkedHashMap<>(32, .75f, true);
    private static String language = "";

    private CriticalNEIShowcase() {}

    public static void background(float x, float y, float width, float height, float partialTicks) {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(x, y, 0);
            rect(0, 0, 170, 174, 0xFF10171D);
            rect(1, 1, 169, 173, EDGE);
            rect(2, 2, 168, 172, STEEL);
            rect(4, 4, 166, 16, 0xFF141D24);
            rect(4, 17, 166, 18, COPPER);
            for (int yy = 22; yy < 170; yy += 12) rect(5, yy, 165, yy + 1, 0xFF2C3842);
            for (int xx = 4; xx < 170; xx += 160)
                for (int yy = 4; yy < 174; yy += 164) rect(xx, yy, xx + 2, yy + 2, GOLD);
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    public static void draw(GTRecipe recipe) {
        DisplayInfo info = CriticalRecipeMaps.displayInfo(recipe);
        if (info == null) return;
        Page page = page(recipe, info);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            GL11.glDisable(GL11.GL_LIGHTING);
            label(page.title, 8, 6, fit(page.title, .8f, 154), GOLD);
            if (info.construction()) construction(info, page);
            else runtime(info, page);
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private static Page page(GTRecipe recipe, DisplayInfo info) {
        String current = Minecraft.getMinecraft().gameSettings.language;
        if (!current.equals(language)) {
            language = current;
            PAGES.clear();
        }
        Page cached = PAGES.get(recipe);
        if (cached != null) return cached;
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        boolean construction = info.construction();
        String title = CriticalRecipeMaps.text("showcase." + (construction ? "construction" : info.kind.key));
        List<String> lines = new ArrayList<>();
        for (String text : CriticalRecipeMaps.description(recipe))
            for (Object wrapped : font.listFormattedStringToWidth(text, construction ? 210 : 202))
                lines.add((String) wrapped);
        String[] phases = new String[3];
        for (int i = 0; i < phases.length; i++) phases[i] = CriticalRecipeMaps
            .text("showcase." + (construction ? "stage_" : info.kind.key + "_phase_") + i);
        String caption = construction
            ? net.minecraft.util.StatCollector.translateToLocal("gtsr.critical.kind." + info.kind.key)
            : CriticalRecipeMaps.text("showcase.ratio");
        cached = new Page(title, caption, phases, lines.toArray(new String[0]));
        PAGES.put(recipe, cached);
        if (PAGES.size() > CACHE_LIMIT) PAGES.remove(
            PAGES.keySet()
                .iterator()
                .next());
        return cached;
    }

    private static void construction(DisplayInfo info, Page page) {
        int stage = info.operation.charAt(info.operation.length() - 1) - '0';
        for (int i = 0; i < 3; i++) {
            int x = 8 + i * 53;
            rect(x, 22, x + 48, 32, i == stage ? COPPER : 0xFF17212A);
            label(page.phases[i], x + 3, 24, .65f, i == stage ? 0xFF141D24 : INK);
            if (i < 2) rect(x + 48, 26, x + 53, 28, GOLD);
        }
        label(page.caption, 9, 38, fit(page.caption, .7f, 132), INK);
        // An output slot remains a native NEI controller link; the product is the commissioned world structure.
        int scan = GTNEIDefaultHandler.getDrawTicks() % 118;
        rect(9 + scan, 51, 10 + scan, 53, GOLD);
        miniature(info.kind, stage);
        rect(8, 129, 162, 130, COPPER);
        textLines(page.lines, 9, 134, 153, 36);
    }

    private static void miniature(CriticalMachineKind kind, int stage) {
        // A deliberately schematic elevation, kept outside every native stack/tooltip hit box.
        rect(144, 121, 164, 124, stage == 0 ? GOLD : EDGE);
        rect(147, 116, 161, 121, stage == 1 ? COPPER : EDGE);
        rect(149, 112, 159, 116, EDGE);
        int body = stage == 2 ? COPPER : 0xFF43535F;
        if (kind == CriticalMachineKind.SOLAR) {
            rect(153, 94, 155, 112, body);
            rect(144, 93, 164, 96, body);
            rect(147, 88, 150, 94, body);
            rect(158, 88, 161, 94, body);
        } else if (kind == CriticalMachineKind.SUN) {
            rect(147, 90, 150, 110, body);
            rect(158, 90, 161, 110, body);
            rect(150, 88, 158, 91, body);
            rect(150, 108, 158, 111, body);
            rect(152, 97, 156, 102, GOLD);
        } else if (kind == CriticalMachineKind.TURBINE) {
            for (int i = 0; i < 3; i++) rect(146 + i * 6, 91 + i % 2 * 5, 150 + i * 6, 112, body);
        } else {
            for (int yy = 91; yy <= 107; yy += 8) rect(146, yy, 162, yy + 5, body);
            rect(152, 89, 156, 113, EDGE);
        }
        int scan = 86 + GTNEIDefaultHandler.getDrawTicks() % 37;
        rect(143, scan, 165, scan + 1, CYAN);
    }

    private static void runtime(DisplayInfo info, Page page) {
        for (int i = 0; i < 3; i++) {
            int x = 8 + i * 53;
            rect(x, 22, x + 48, 31, 0xFF17212A);
            label(page.phases[i], x + 3, 24, .6f, i == 1 ? GOLD : CYAN);
        }
        // The lane connects native clickable input/output stacks without drawing over their hit boxes.
        rect(54, 43, 129, 44, COPPER);
        int pulse = GTNEIDefaultHandler.getDrawTicks() % 70;
        rect(55 + pulse, 41, 58 + pulse, 46, GOLD);
        schematic(info.kind);
        label(page.caption, 9, 58, .65f, CYAN);
        rect(8, 68, 162, 69, COPPER);
        textLines(page.lines, 9, 74, 153, 95);
    }

    private static void schematic(CriticalMachineKind kind) {
        // Fixed bounded primitives: steam rotor, stellar cage, three irradiator rays or four chemical windows.
        if (kind == CriticalMachineKind.SUN) {
            rect(75, 33, 95, 51, EDGE);
            rect(78, 35, 92, 49, 0xFF17212A);
            rect(82, 39, 88, 45, GOLD);
        } else if (kind == CriticalMachineKind.TURBINE) {
            for (int i = 0; i < 5; i++) rect(74 + i * 4, 35 + i % 2 * 3, 76 + i * 4, 51 - i % 2 * 3, EDGE);
            rect(72, 42, 96, 44, GOLD);
        } else if (kind == CriticalMachineKind.SOLAR) {
            for (int i = 0; i < 3; i++) {
                rect(75, 35 + i * 6, 96, 36 + i * 6, GOLD);
                rect(72, 34 + i * 6, 75, 38 + i * 6, CYAN);
            }
        } else {
            for (int i = 0; i < 4; i++) {
                int x = 73 + i % 2 * 12, y = 34 + i / 2 * 10;
                rect(x, y, x + 10, y + 8, EDGE);
                rect(x + 2, y + 2, x + 8, y + 6, COPPER);
            }
        }
    }

    private static void textLines(String[] lines, int x, int y, int width, int height) {
        float scale = Math.min(.75f, height / (float) Math.max(1, lines.length * 10));
        for (String line : lines) scale = Math.min(scale, fit(line, scale, width));
        int yy = y;
        for (String line : lines) {
            label(line, x, yy, scale, INK);
            yy += Math.max(1, (int) (10 * scale));
        }
    }

    private static float fit(String text, float scale, int width) {
        return Math.min(scale, width / (float) Math.max(1, Minecraft.getMinecraft().fontRenderer.getStringWidth(text)));
    }

    private static void label(String text, int x, int y, float scale, int color) {
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(x, y, 0);
            GL11.glScalef(scale, scale, 1);
            Minecraft.getMinecraft().fontRenderer.drawString(text, 0, 0, color);
        } finally {
            GL11.glPopMatrix();
        }
    }

    private static void rect(int x1, int y1, int x2, int y2, int color) {
        Gui.drawRect(x1, y1, x2, y2, color);
    }

    private static final class Page {

        final String title, caption;
        final String[] phases, lines;

        Page(String title, String caption, String[] phases, String[] lines) {
            this.title = title;
            this.caption = caption;
            this.phases = phases;
            this.lines = lines;
        }
    }
}
