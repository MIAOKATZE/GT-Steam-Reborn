package com.miaokatze.gtsr.client.weapons.outpost;

/**
 * Adapted from GT-Outpost by MiaoKatze, AGPL-3.0-or-later.
 * Original algorithms retained; portable networking/pose replaces turret context.
 */
import net.minecraft.client.gui.Gui;

public final class OutpostHudRings {

    private static void drawRingDot(float cx, float cy, float r, float deg, int color) {
        double theta = Math.toRadians(deg);
        int x = Math.round(cx + r * (float) Math.cos(theta) - 1.0F);
        int y = Math.round(cy + r * (float) Math.sin(theta) - 1.0F);
        drawRect(x, y, x + 2, y + 2, color);
    }

    private static void drawRingDotAA(float cx, float cy, float r, float deg, int color) {
        double theta = Math.toRadians(deg);
        float fx = cx + r * (float) Math.cos(theta) - 1.0F;
        float fy = cy + r * (float) Math.sin(theta) - 1.0F;
        int x = Math.round(fx);
        int y = Math.round(fy);
        drawRect(x, y, x + 2, y + 2, color); // 主格实色（与 drawRingDot 同式）
        int baseAlpha = color >>> 24;
        if (baseAlpha <= 0) {
            return; // 全透明：无邻格补块意义（防御）
        }
        float ex = x - fx;
        float ey = y - fy;
        if (Math.abs(ex) >= 0.25F) {
            int nx = x - (ex > 0.0F ? 1 : -1);
            drawRect(nx, y, nx + 2, y + 2, withAlpha(color, (baseAlpha / 255.0F) * Math.abs(ex)));
        }
        if (Math.abs(ey) >= 0.25F) {
            int ny = y - (ey > 0.0F ? 1 : -1);
            drawRect(x, ny, x + 2, ny + 2, withAlpha(color, (baseAlpha / 255.0F) * Math.abs(ey)));
        }
    }

    private static void drawArcBand(float cx, float cy, float rIn, float rOut, float startDeg, float endDeg,
        int segments, int color, boolean aa) {
        if (segments <= 0 || rOut <= rIn + 1.0F) {
            return; // 防御：除零/空带（带宽 ≤1px 无径向排可画；drawDotRing n<=0 直返同式）
        }
        float rMid = (rIn + rOut) * 0.5F;
        // 自适应子站：n=max(segments, ceil(中线弧长/0.5px))——每子站中线弧长 ≤0.5px（S-C C1
        // 站距升档 1px→0.5px=系数 ×2，配 aa 伪抗锯齿更平滑）
        int n = Math.max(segments, (int) Math.ceil(Math.toRadians(Math.abs(endDeg - startDeg)) * rMid * 2.0D));
        for (int i = 0; i <= n; i++) { // i=0..n 共 n+1 站，含端点保证闭合到 endDeg
            float deg = startDeg + (endDeg - startDeg) * i / n;
            for (float r = rIn + 1.0F; r < rOut; r += 2.0F) { // 径向排 rIn+1, rIn+3, …<rOut（排距 2px）
                if (aa) {
                    drawRingDotAA(cx, cy, r, deg, color);
                } else {
                    drawRingDot(cx, cy, r, deg, color);
                }
            }
        }
    }

    private static void drawArcCap(float cx, float cy, float midR, float thetaDeg, int color) {
        double theta = Math.toRadians(thetaDeg);
        float cos = (float) Math.cos(theta);
        float sin = (float) Math.sin(theta);
        float px = cx + midR * cos;
        float py = cy + midR * sin;
        drawCapDot(px, py, color);
        drawCapDot(px - sin, py + cos, color); // 切向（θ+90°）前 1px
        drawCapDot(px + sin, py - cos, color); // 切向后 1px
        drawCapDot(px - cos, py - sin, color); // 径向内 1px
        drawCapDot(px + cos, py + sin, color); // 径向外 1px
    }

    private static void drawCapDot(float x, float y, int color) {
        int x1 = Math.round(x) - 1;
        int y1 = Math.round(y) - 1;
        drawRect(x1, y1, x1 + 2, y1 + 2, color);
    }

    private static int withAlpha(int rgb, float alpha) {
        return ((int) (alpha * 255.0F) & 0xFF) << 24 | rgb & 0xFFFFFF;
    }

    private static void drawRect(int x, int y, int x2, int y2, int color) {
        Gui.drawRect(x, y, x2, y2, color);
    }

    private static String ringSmoothCodeName;
    private static float displayedRateFraction, displayedOverheat;
    private static final float RING_SMOOTH_FACTOR = .25F;

    public static void reset() {
        ringSmoothCodeName = null;
        displayedRateFraction = displayedOverheat = 0;
    }

    public static void draw(String codeName, float cx, float cy, float rate, float heat) {
        if (!codeName.equals(ringSmoothCodeName)) {
            ringSmoothCodeName = codeName;
            displayedRateFraction = rate;
            displayedOverheat = heat;
        } else {
            displayedRateFraction += (rate - displayedRateFraction) * RING_SMOOTH_FACTOR;
            displayedOverheat += (heat - displayedOverheat) * RING_SMOOTH_FACTOR;
        }
        band(cx, cy, 6, 10, displayedRateFraction, 0xFF40FF40);
        band(cx, cy, 10, 14, displayedOverheat, 0xFFFF7000);
    }

    private static void band(float x, float y, float in, float out, float progress, int color) {
        progress = Math.max(0, Math.min(1, progress));
        drawArcBand(x, y, in, out, -90, 270, 128, withAlpha(color, .14F), false);
        if (progress > 0) {
            float end = -90 + 360 * progress;
            drawArcBand(x, y, in, out, -90, end, 128, color, true);
            drawArcCap(x, y, (in + out) * .5F, -90, color);
            drawArcCap(x, y, (in + out) * .5F, end, color);
        }
    }
}
