package com.miaokatze.gtsr.common.gui;

import java.util.function.Supplier;

import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.cleanroommc.modularui.theme.WidgetTheme;
import com.miaokatze.gtsr.config.Config;
import com.miaokatze.miaokatzecore.client.render.GlScope;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Read-only 2D construction schematic. The active-cell pulse is decorative; progress never extrapolates. */
final class CriticalConstructionPreview implements IDrawable {

    private final Supplier<CriticalConstructionSnapshot> sample;
    private CriticalConstructionSnapshot boundsSample;
    private int minX, maxX, minZ, maxZ;

    CriticalConstructionPreview(Supplier<CriticalBusinessSnapshot> business) {
        this.sample = () -> business.get().construction;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void draw(GuiContext context, int x, int y, int width, int height, WidgetTheme theme) {
        try (GlScope ignored = new GlScope()) {
            GuiDraw.drawRect(x, y, width, height, 0xFF151C25);
            // Fixed drafting grid and corner marks. No model, world scan, texture allocation or widget rebuild.
            for (int column = 12; column < width; column += 16) GuiDraw.drawRect(x + column, y, 1, height, 0x163E6676);
            for (int row = 10; row < height; row += 16) GuiDraw.drawRect(x, y + row, width, 1, 0x163E6676);
            GuiDraw.drawRect(x + 3, y + 3, 12, 1, 0xFF856C42);
            GuiDraw.drawRect(x + 3, y + 3, 1, 9, 0xFF856C42);
            GuiDraw.drawRect(x + width - 15, y + height - 4, 12, 1, 0xFF856C42);
            GuiDraw.drawRect(x + width - 4, y + height - 12, 1, 9, 0xFF856C42);
            CriticalConstructionSnapshot current = sample.get();
            if (current.showsFoundation() && current.chunkCount() > 0)
                drawFoundation(current, context, x, y, width, height);
            else drawStructure(current, x, y, width, height);
        }
    }

    @SideOnly(Side.CLIENT)
    private void drawFoundation(CriticalConstructionSnapshot current, GuiContext context, int x, int y, int width,
        int height) {
        if (boundsSample != current) {
            minX = maxX = current.chunkX(0);
            minZ = maxZ = current.chunkZ(0);
            for (int i = 1; i < current.chunkCount(); i++) {
                minX = Math.min(minX, current.chunkX(i));
                maxX = Math.max(maxX, current.chunkX(i));
                minZ = Math.min(minZ, current.chunkZ(i));
                maxZ = Math.max(maxZ, current.chunkZ(i));
            }
            boundsSample = current;
        }
        double columns = ((double) maxX - minX + 1) * 4, rows = ((double) maxZ - minZ + 1) * 4;
        double scale = Math.min((width - 16) / columns, (height - 12) / rows);
        double originX = x + (width - columns * scale) / 2;
        double originY = y + (height - rows * scale) / 2;
        double cured = current.phase() == 3 ? 1
            : current.phase() == 2
                ? 1 - CriticalConstructionSnapshot.ratio(current.curingRemaining(), current.curingDuration())
                : 0;
        int concrete = mix(0xFF647E92, 0xFF82B49B, cured);
        if (current.phase() == 4) concrete = mix(0xFF647E92, 0xFFD6A36F, current.progress());
        int activeColor = 0xFFFFCE83;
        if (current.enabled() && current.reason() == 0 && !Config.criticalUiReducedMotion) {
            double pulse = 0.5 + 0.5 * Math.sin((context.getTick() % 40 + context.getPartialTicks()) * Math.PI / 20);
            activeColor = ((int) (184 + 55 * pulse) << 24) | 0xFFCE83;
        }
        float cellSize = (float) scale;
        for (int chunk = 0; chunk < current.chunkCount(); chunk++) {
            double localX = ((double) current.chunkX(chunk) - minX) * 4;
            double localZ = ((double) current.chunkZ(chunk) - minZ) * 4;
            float chunkX = (float) (originX + localX * scale), chunkY = (float) (originY + localZ * scale);
            // Tiny chunks cannot show sixteen distinct cells. Draw their sampled completion once instead.
            if (cellSize < 2) {
                GuiDraw.drawRect(
                    chunkX,
                    chunkY,
                    cellSize * 4,
                    cellSize * 4,
                    mix(0xFF25303C, concrete, current.chunkCells(chunk) / 16D));
                if (current.phase() == 1 && chunk == current.activeChunk())
                    outline(chunkX, chunkY, cellSize * 4, cellSize * 4, 1, 0xFFFFCE83);
                continue;
            }
            for (int cell = 0; cell < 16; cell++) {
                float cellX = (float) (originX + (localX + cell % 4) * scale);
                float cellY = (float) (originY + (localZ + cell / 4) * scale);
                GuiDraw.drawRect(cellX, cellY, cellSize, cellSize, 0xFF25303C);
                if (cell < current.chunkCells(chunk)) {
                    GuiDraw.drawRect(cellX, cellY, cellSize, cellSize, concrete);
                } else if (current.phase() == 1 && chunk == current.activeChunk() && cell == current.activeCell()) {
                    float fill = (float) CriticalConstructionSnapshot
                        .ratio(current.displayCellTicks(), current.cellDuration());
                    GuiDraw.drawRect(cellX, cellY + cellSize * (1 - fill), cellSize, cellSize * fill, 0xFFDFAD62);
                }
            }
            // Fine lines mark 4 x 4 blocks; heavy outlines mark the actual chunk boundary.
            for (int line = 1; line < 4; line++) {
                GuiDraw.drawRect(chunkX + cellSize * line, chunkY, 0.55f, cellSize * 4, 0x90536572);
                GuiDraw.drawRect(chunkX, chunkY + cellSize * line, cellSize * 4, 0.55f, 0x90536572);
            }
            outline(chunkX, chunkY, cellSize * 4, cellSize * 4, 1.35f, 0xFFBDD0D9);
            if (current.phase() == 1 && chunk == current.activeChunk() && current.activeCell() >= 0) {
                outline(
                    chunkX + cellSize * (current.activeCell() % 4),
                    chunkY + cellSize * (current.activeCell() / 4),
                    cellSize,
                    cellSize,
                    1,
                    activeColor);
            }
        }
    }

    @SideOnly(Side.CLIENT)
    private static void drawStructure(CriticalConstructionSnapshot current, int x, int y, int width, int height) {
        // Integer pixel sizes and fixed gaps keep the three silhouettes stable across stage transitions.
        int unit = Math.max(3, Math.min((width - 100) / 12, (height - 22) / 8));
        int center = x + (width - 92) / 2, floor = y + height - 9;
        drawPart(current, 0, center - unit * 6, floor - unit * 2, unit, 12, 2, x + width - 82, 0xFF72A8B2);
        drawPart(current, 1, center - unit * 9 / 2, floor - unit * 6 - 5, unit, 9, 4, x + width - 82, 0xFFC39D62);
        drawPart(current, 2, center - unit * 3, floor - unit * 8 - 10, unit, 6, 2, x + width - 82, 0xFF91B8DC);
        GuiDraw.drawRect(center - unit * 6 - 3, floor + 2, unit * 12 + 6, 1, 0xFF72828E);
    }

    @SideOnly(Side.CLIENT)
    private static void drawPart(CriticalConstructionSnapshot current, int part, int x, int y, int unit, int columns,
        int rows, int labelX, int color) {
        double progress = current.partProgress(part);
        boolean active = current.activePart() == part;
        for (int third = 0; third < 3; third++) ghostBlocks(
            x + columns * unit * third / 3,
            y,
            unit,
            columns / 3,
            rows,
            CriticalConstructionSnapshot.ratio(progress * 3 - third, 1),
            color);
        outline(x - 2, y - 2, columns * unit + 3, rows * unit + 3, active ? 2 : 1, active ? 0xFFFFCE83 : 0x805D7180);
        // Each third has its own fixed boundary; never paint equipment over the body.
        for (int third = 1; third < 3; third++)
            GuiDraw.drawRect(x + columns * unit * third / 3 - 1, y, 1, rows * unit - 1, 0xFF151C25);
        String key = part == 0 ? "base" : part == 1 ? "body" : "equipment";
        int labelY = y + rows * unit / 2 - 9;
        GuiDraw.drawRect(
            x + columns * unit + 4,
            labelY + 5,
            Math.max(0, labelX - x - columns * unit - 8),
            1,
            active ? 0xFFFFCE83 : 0x805D7180);
        GuiDraw.drawText(
            StatCollector.translateToLocal("gtsr.critical.view.part." + key),
            labelX,
            labelY,
            0.8f,
            active ? 0xFFFFCE83 : color,
            false);
        for (int third = 0; third < 3; third++) {
            double filled = CriticalConstructionSnapshot.ratio(progress * 3 - third, 1);
            int bx = labelX + third * 19;
            GuiDraw.drawRect(bx, labelY + 12, 16, 4, 0xFF303D49);
            GuiDraw.drawRect(bx, labelY + 12, (float) (16 * filled), 4, color);
        }
    }

    @SideOnly(Side.CLIENT)
    private static void ghostBlocks(float x, float y, float unit, int columns, int rows, double progress, int color) {
        double filled = CriticalConstructionSnapshot.ratio(progress, 1) * columns * rows;
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                float bx = x + column * unit, by = y + (rows - row - 1) * unit;
                float size = unit - 1;
                GuiDraw.drawRect(bx, by, size, size, 0x153F6576);
                outline(bx, by, size, size, 0.65f, 0x554F7C8F);
                double amount = Math.max(0, Math.min(1, filled - row * columns - column));
                if (amount > 0) GuiDraw.drawRect(bx, by, size, size, ((int) (amount * 166) << 24) | (color & 0xFFFFFF));
            }
        }
    }

    @SideOnly(Side.CLIENT)
    private static void outline(float x, float y, float width, float height, float weight, int color) {
        GuiDraw.drawRect(x, y, width, weight, color);
        GuiDraw.drawRect(x, y + height - weight, width, weight, color);
        GuiDraw.drawRect(x, y, weight, height, color);
        GuiDraw.drawRect(x + width - weight, y, weight, height, color);
    }

    private static int mix(int from, int to, double progress) {
        int result = 0xFF000000;
        for (int shift = 0; shift <= 16; shift += 8) {
            int a = (from >>> shift) & 255, b = (to >>> shift) & 255;
            result |= ((int) Math.round(a + (b - a) * progress)) << shift;
        }
        return result;
    }

    @Override
    public boolean canApplyTheme() {
        return false;
    }
}
