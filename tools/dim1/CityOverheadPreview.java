import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityPlan;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityPlanner;

/**
 * v1.20.34 城市形态异形化目检预览（一次性 main，tools/ 惯例；CityPlan 专属，CityRasterPreview
 * 的"整城俯视"等价链——单变体栅格之外补整城形态目检）。每 seed 渲染一座完整城市的俯视 PNG：
 * <ul>
 * <li>城界（insideCity 唯一边界函数逐格采样）外的荒野 = 近黑；城内地面 = 深褐；</li>
 * <li>中央大道（宽 5）= 亮金；开放次街 = 灰褐铺面；断开街段留地面（分段断续观感）；</li>
 * <li>保留 plot 按分区着色：核心=蓝 / 工业=橙 / 边缘=绿（空地 plot 加深 40%，落 rubble 件）；</li>
 * <li>几何门内但被连通闭包剔除的 plot = 暗红（目检"孤立地块直接剔除"的落点）。</li>
 * </ul>
 * 输出到 temp/city-shape-preview/（/temp/ 已 gitignored）。零 Minecraft 依赖：
 * {@code java -cp build/classes/java/main tools/dim1/CityOverheadPreview.java [outDir] [seed...]}
 */
public class CityOverheadPreview {

    /** 每格像素（最近邻放大，保像素轮廓）。 */
    private static final int PX = 3;
    private static final long[] DEFAULT_SEEDS = { 12345L, 0x50524F53L, -987654321L, 20260920L };

    // 调色板（ARGB）
    private static final int WILD = 0x141414; // 界外荒野
    private static final int GROUND = 0x3A342C; // 城内地面
    private static final int AVENUE = 0xE6C87A; // 中央大道
    private static final int STREET = 0x8E8272; // 开放次街铺面
    private static final int[] DISTRICT = { 0x5F8FC0, 0xC09050, 0x6E8F66 }; // 核心/工业/边缘
    private static final int DROPPED = 0x6E2020; // 闭包剔除 plot

    public static void main(String[] args) throws Exception {
        final File outDir = new File(args.length > 0 ? args[0] : "temp/city-shape-preview");
        if (!outDir.exists() && !outDir.mkdirs()) {
            throw new IllegalStateException("cannot create output dir: " + outDir.getAbsolutePath());
        }
        final long[] seeds;
        if (args.length > 1) {
            seeds = new long[args.length - 1];
            for (int i = 1; i < args.length; i++) {
                seeds[i - 1] = Long.parseLong(args[i]);
            }
        } else {
            seeds = DEFAULT_SEEDS;
        }
        int done = 0;
        for (final long seed : seeds) {
            CityPlan p = null;
            outer: for (int cellX = -9; cellX <= 9; cellX++) {
                for (int cellZ = -9; cellZ <= 9; cellZ++) {
                    p = CityPlanner.planFor(seed, cellX, cellZ);
                    if (p != null) {
                        break outer;
                    }
                }
            }
            if (p == null) {
                System.out.println("OVERHEAD seed=" + seed + " no city in scan grid, skipped");
                continue;
            }
            final int reach = p.contentReachBlocks();
            final int side = 2 * reach + 1;
            final BufferedImage img = new BufferedImage(side * PX, side * PX, BufferedImage.TYPE_INT_RGB);
            int keptPlots = 0;
            int droppedPlots = 0;
            int openSegs = 0;
            int closedSegs = 0;
            for (int bz = 0; bz < side; bz++) {
                final int wz = p.getCenterZ() - reach + bz;
                for (int bx = 0; bx < side; bx++) {
                    final int wx = p.getCenterX() - reach + bx;
                    int rgb = WILD;
                    final int u = wx - p.getCenterX();
                    final int v = wz - p.getCenterZ();
                    if (p.insideCity(wx, wz)) {
                        if (p.onStreet(wx, wz)) {
                            rgb = Math.abs(u) <= CityPlan.AVENUE_WIDTH / 2 || Math.abs(v) <= CityPlan.AVENUE_WIDTH / 2
                                ? AVENUE
                                : STREET;
                        } else {
                            rgb = GROUND;
                        }
                    }
                    // plot 着色（plot 带与街带互斥，覆盖不冲突）
                    final int k = Math.floorDiv(u - 2, CityPlan.STREET_SPACING);
                    final int l = Math.floorDiv(v - 2, CityPlan.STREET_SPACING);
                    final boolean inPlotBand = u >= k * CityPlan.STREET_SPACING + 2
                        && u <= k * CityPlan.STREET_SPACING + 2 + CityPlan.PLOT_DEPTH - 1
                        && v >= l * CityPlan.STREET_SPACING + 2
                        && v <= l * CityPlan.STREET_SPACING + 2 + CityPlan.PLOT_DEPTH - 1;
                    if (inPlotBand) {
                        if (p.plotInCircle(k, l)) {
                            keptPlots++;
                            int c = DISTRICT[p.districtOf(k, l)];
                            if (p.plotEmpty(k, l)) {
                                c = darken(c, 0.6f); // 空地 plot（rubble 小件）加深
                            }
                            rgb = c;
                        } else if (independentGeometricIn(p, k, l)) {
                            droppedPlots++;
                            rgb = DROPPED; // 几何门内但连通闭包剔除
                        }
                    }
                    for (int sy = 0; sy < PX; sy++) {
                        for (int sx = 0; sx < PX; sx++) {
                            img.setRGB(bx * PX + sx, bz * PX + sy, 0xFF000000 | rgb);
                        }
                    }
                }
            }
            // 街段统计（标注用）：line≠0 全段开放占比
            final int lim = p.plotIndexLimit();
            for (int line = -lim; line <= lim; line++) {
                if (line == 0) {
                    continue;
                }
                for (int index = -lim; index <= lim; index++) {
                    if (p.streetSegmentOpen(true, line, index)) {
                        openSegs++;
                    } else {
                        closedSegs++;
                    }
                    if (p.streetSegmentOpen(false, line, index)) {
                        openSegs++;
                    } else {
                        closedSegs++;
                    }
                }
            }
            // 中心十字（城市中心锚）
            final Graphics2D g = img.createGraphics();
            g.setColor(java.awt.Color.WHITE);
            final int c0 = reach * PX + PX / 2;
            g.drawLine(c0 - 8, c0, c0 + 8, c0);
            g.drawLine(c0, c0 - 8, c0, c0 + 8);
            g.dispose();
            final File out = new File(outDir, "city-overhead-seed" + seed + ".png");
            ImageIO.write(img, "png", out);
            System.out.println(
                "OVERHEAD seed=" + seed + " radius=" + p.getRadiusChunks()
                    + " centerChunk=" + p.getCenterChunkX() + "," + p.getCenterChunkZ()
                    + " keptPlots=" + keptPlots / (CityPlan.PLOT_DEPTH * CityPlan.PLOT_DEPTH)
                    + " droppedByClosure=" + droppedPlots / (CityPlan.PLOT_DEPTH * CityPlan.PLOT_DEPTH)
                    + " segOpenRate=" + String.format("%.2f", openSegs / (double) (openSegs + closedSegs))
                    + " -> " + out.getAbsolutePath());
            done++;
        }
        System.out.println("OVERHEAD DONE " + done + " city overheads -> " + outDir.getAbsolutePath());
    }

    /** 独立重算 plot 偏置几何门（与 CityPlanSanityCheck 同口径：plotEdgeBias + insideCity）。 */
    private static boolean independentGeometricIn(CityPlan p, int k, int l) {
        final double du = k * CityPlan.STREET_SPACING + 8.0;
        final double dv = l * CityPlan.STREET_SPACING + 8.0;
        final double d = Math.sqrt(du * du + dv * dv);
        if (d < 1e-9) {
            return true;
        }
        final double scale = (d + p.plotEdgeBias(k, l)) / d;
        return p.insideCity(
            p.getCenterX() + (int) Math.round(du * scale),
            p.getCenterZ() + (int) Math.round(dv * scale));
    }

    private static int darken(int rgb, float f) {
        final int r = (int) (((rgb >> 16) & 0xFF) * f);
        final int g = (int) (((rgb >> 8) & 0xFF) * f);
        final int b = (int) ((rgb & 0xFF) * f);
        return (r << 16) | (g << 8) | b;
    }
}
