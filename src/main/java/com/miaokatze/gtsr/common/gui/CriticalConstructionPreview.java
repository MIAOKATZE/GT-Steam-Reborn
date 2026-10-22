package com.miaokatze.gtsr.common.gui;

import java.util.function.Supplier;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.cleanroommc.modularui.theme.WidgetTheme;
import com.miaokatze.miaokatzecore.client.render.GlScope;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Read-only drawing of server samples. No wall clock, extrapolation or world access. */
final class CriticalConstructionPreview implements IDrawable {

    private final Supplier<CriticalConstructionSnapshot> sample;

    CriticalConstructionPreview(Supplier<CriticalConstructionSnapshot> sample) {
        this.sample = sample;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void draw(GuiContext context, int x, int y, int width, int height, WidgetTheme theme) {
        try (GlScope ignored = new GlScope()) {
            GuiDraw.drawRect(x, y, width, height, 0xFF151C25);
            CriticalConstructionSnapshot current = sample.get();
            if (current.showsFoundation()) drawFoundation(current, x, y, width, height);
            else drawStructure(current, x, y, width, height);
        }
    }

    @SideOnly(Side.CLIENT)
    private static void drawFoundation(CriticalConstructionSnapshot current, int x, int y, int width, int height) {
        if (current.chunkCount() == 0) return;
        int minX = current.chunkX(0), maxX = minX, minZ = current.chunkZ(0), maxZ = minZ;
        for (int i = 1; i < current.chunkCount(); i++) {
            minX = Math.min(minX, current.chunkX(i));
            maxX = Math.max(maxX, current.chunkX(i));
            minZ = Math.min(minZ, current.chunkZ(i));
            maxZ = Math.max(maxZ, current.chunkZ(i));
        }
        double columns = ((double) maxX - minX + 1) * 4, rows = ((double) maxZ - minZ + 1) * 4;
        double scale = Math.min((width - 16) / columns, (height - 12) / rows);
        double originX = x + (width - columns * scale) / 2;
        double originY = y + (height - rows * scale) / 2;
        double cured = current.phase() >= 3 ? 1
            : current.phase() == 2
                ? 1 - CriticalConstructionSnapshot.ratio(current.curingRemaining(), current.curingDuration())
                : 0;
        int concrete = mix(0xFF647E92, 0xFF82B49B, cured);
        float cellSize = (float) scale;
        for (int chunk = 0; chunk < current.chunkCount(); chunk++) {
            double localX = ((double) current.chunkX(chunk) - minX) * 4;
            double localZ = ((double) current.chunkZ(chunk) - minZ) * 4;
            for (int cell = 0; cell < 16; cell++) {
                float cellX = (float) (originX + (localX + cell % 4) * scale);
                float cellY = (float) (originY + (localZ + cell / 4) * scale);
                GuiDraw.drawRect(cellX, cellY, cellSize, cellSize, 0xFF25303C);
                if (cell < current.chunkCells(chunk)) {
                    GuiDraw.drawRect(cellX, cellY, cellSize, cellSize, concrete);
                } else if (current.phase() == 1 && chunk == current.activeChunk() && cell == current.activeCell()) {
                    float fill = (float) CriticalConstructionSnapshot
                        .ratio(current.cellTicks(), current.cellDuration());
                    GuiDraw.drawRect(cellX, cellY + cellSize * (1 - fill), cellSize, cellSize * fill, 0xFFDFAD62);
                }
            }
            float chunkX = (float) (originX + localX * scale), chunkY = (float) (originY + localZ * scale);
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
                    0xFFFFCE83);
            }
        }
    }

    @SideOnly(Side.CLIENT)
    private static void drawStructure(CriticalConstructionSnapshot current, int x, int y, int width, int height) {
        boolean complete = current.status() == 2;
        double stageProgress = CriticalConstructionSnapshot.ratio(current.placed(), current.total());
        double baseProgress = complete || current.stage() > 3 ? 1
            : current.stage() > 0 ? (current.stage() - 1 + stageProgress) / 3 : 0;
        double bodyProgress = complete ? 1 : current.stage() >= 4 ? stageProgress : 0;
        if (current.status() == 3) {
            baseProgress = 1 - stageProgress;
            bodyProgress = 1 - stageProgress;
        }
        float unit = Math.min((width - 44) / 14f, (height - 14) / 14f);
        float center = x + width / 2f;
        float floor = y + height - 7;
        // Deliberately abstract ghost blocks: base and body are separate, rather than a fake world model.
        if (current.stage() <= 3 && !complete) {
            ghostBlocks(center - unit * 6, floor - unit * 3, unit, 12, 3, baseProgress, 0xFF72A8B2);
        } else {
            ghostBlocks(center - unit * 4, floor - unit * 10, unit, 8, 10, bodyProgress, 0xFFC39D62);
        }
        GuiDraw.drawRect(center - unit * 7, floor + 1, unit * 14, 1, 0xFF72828E);
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
