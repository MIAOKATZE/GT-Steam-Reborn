package com.miaokatze.gtsr.client.nei;

import static com.miaokatze.gtsr.api.recipe.CriticalNEILayout.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;

import org.lwjgl.opengl.GL11;

import com.miaokatze.gtsr.api.recipe.CriticalRecipeMaps;
import com.miaokatze.gtsr.api.recipe.CriticalRecipeMaps.DisplayInfo;
import com.miaokatze.gtsr.common.critical.CriticalStellarPolicy;
import com.miaokatze.gtsr.config.Config;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.util.GTRecipe;
import gregtech.nei.GTNEIDefaultHandler;

/** Native 1x labels and native slots; one entry per operation, without explanatory pages. */
@SideOnly(Side.CLIENT)
public final class CriticalNEIShowcase {

    private static final int INK = 0xFFE1E8EB, MUTED = 0xFFADBDC6, GOLD = 0xFFEAC879;
    private static final int EDGE = 0xFF53646F;

    private CriticalNEIShowcase() {}

    public static void registerReloadListener() {
        // No language-dependent render cache: resource changes are visible on the next frame.
    }

    public static void background(float x, float y, float width, float height, float partialTicks) {
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(x, y, 0);
            rect(0, 0, WIDTH, HEIGHT, 0xFF17212A);
            rect(1, 1, WIDTH - 1, HEIGHT - 1, 0xFF26313A);
            rect(5, 20, 165, 21, EDGE);
        } finally {
            GL11.glPopMatrix();
        }
    }

    public static void draw(GTRecipe recipe) {
        DisplayInfo info = CriticalRecipeMaps.displayInfo(recipe);
        if (info == null) return;
        if (info.construction()) {
            label(text("build.title"), 8, 7, GOLD);
            label(text("build.stage." + info.constructionStage()), 8, 27, INK);
            label(text("build.time"), 8, 150, MUTED);
            return;
        }
        label(text(info.operation + ".title"), 8, 7, GOLD);
        label(text("input"), 8, 27, MUTED);
        label(
            text(
                info.operation.equals("battery") || info.operation.startsWith("chemical_window_") ? "energy"
                    : "output"),
            OUTPUT_X,
            27,
            MUTED);
        arrow(arrowCenter(recipe.mFluidInputs.length > 0 || recipe.mFluidOutputs.length > 0));
        if (info.operation.startsWith("chemical_window_")) {
            label("EU", 146, 70, GOLD);
            label(info.baseEu + " EU", 8, 96, GOLD);
            label(recipe.mDuration + " t", 8, 112, INK);
            label(text("chemical.ratio." + info.spectrum), 8, 128, MUTED);
        } else if (info.operation.equals("battery")) {
            label("EU", 146, 70, GOLD);
            label(text("spectrum." + info.spectrum), 8, 96, INK);
            label(info.baseEu + " EU / L", 8, 110, INK);
            label(text("normal"), 8, 124, MUTED);
            label(recipe.mDuration + " t", 8, 140, MUTED);
        } else if (info.operation.equals("stellar_ignition")) {
            label(CriticalStellarPolicy.IGNITION_EU + " EU", 8, 96, INK);
            label(text("ignition.time"), 8, 112, MUTED);
        } else if (info.operation.equals("stellar_sustain")) {
            label(CriticalStellarPolicy.SUSTAIN_EUT + " EU/t", 8, 96, INK);
            label(recipe.mDuration + " t", 8, 112, INK);
            label(text("sustain.always"), 8, 128, MUTED);
        } else if (info.operation.equals("sun")) {
            label(recipe.mDuration + " t", 8, 96, INK);
            label(CriticalStellarPolicy.runEuPerTick(info.baseEu.longValueExact(), 1) + " EU/t", 8, 110, INK);
            label(text("per_parallel"), 8, 124, MUTED);
            label(text("fusion.sustain"), 8, 140, MUTED);
        } else if (info.operation.equals("turbine")) {
            label(recipe.mDuration + " t", 8, 96, INK);
            label(info.baseEu + " EU " + text("base"), 8, 110, INK);
            label(text("turbine.efficiency"), 8, 124, MUTED);
            label(text("ratio"), 8, 140, MUTED);
        } else {
            label(recipe.mDuration + " t / 0 EU", 8, 96, INK);
            label(text(info.operation.equals("solar_dust") ? "per_parallel" : "ratio"), 8, 112, MUTED);
        }
        // The bottom-right area remains empty for NEI's native favourite button.
    }

    private static void arrow(int center) {
        rect(78, center - 1, 130, center + 1, EDGE);
        rect(127, center - 4, 129, center + 4, GOLD);
        rect(129, center - 3, 131, center + 3, GOLD);
        rect(131, center - 2, 133, center + 2, GOLD);
        int pulse = Config.criticalUiReducedMotion ? 24 : GTNEIDefaultHandler.getDrawTicks() % 44;
        rect(79 + pulse, center - 2, 83 + pulse, center + 2, GOLD);
    }

    private static String text(String key) {
        return CriticalRecipeMaps.text("simple." + key);
    }

    private static void label(String text, int x, int y, int color) {
        Minecraft.getMinecraft().fontRenderer.drawString(text, x, y, color);
    }

    private static void rect(int x1, int y1, int x2, int y2, int color) {
        Gui.drawRect(x1, y1, x2, y2, color);
    }
}
