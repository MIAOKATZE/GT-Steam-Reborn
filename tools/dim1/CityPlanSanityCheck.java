import java.util.HashSet;
import java.util.Set;

import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityPlan;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityPlanner;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityVariants;

/**
 * S4b 渲染协议几何自检（一次性 main，tools/ 惯例）：对一座已知城市，验证
 * {@link CityPlan#forEachPlotInChunk} 在城市覆盖窗内每个 chunk 至少回调一次且锚点落在
 * contentReach 内、变体名均在注册表；街道列回调坐标全部落在该 chunk（协议=切片）。
 * dim78-fix S-A3 断言（v1.20.34 异形轮廓口径更新）：
 * <ul>
 * <li>(a) 边界判定同侧性——街道回调格全部被判城内（{@link CityPlan#insideCity(int, int)}
 *     唯一边界函数），且街道裁剪与 insideCity∧onStreet 独立重算无多无漏；</li>
 * <li>(b) 全部回调坐标在 {@link CityPlan#contentReachBlocks()} 内；</li>
 * <li>(c) plot 门 = 偏置几何门 ∧ 连通闭包——kept plot 必过「公开 {@link CityPlan#plotEdgeBias}
 *     + insideCity」独立重算的偏置几何门（证明 plot 侧没有第二套边界公式）；闭包剔除数
 *     （几何门内但非可达）打印登记（=0 孤立地块的专项断言在 CityShapeCheck）；</li>
 * <li>(d) 轮廓总扰动硬约束：{@code edgePerturbationBlocks()} ∈ [EDGE_TOT_FRAC_MIN,
 *     EDGE_TOT_FRAC_MAX]×radiusBlocks（自适应 25-35%，样本城 + 双 seed × 101×101 cell 实测）。</li>
 * </ul>
 * 零 Minecraft 依赖：
 * {@code java -cp build/classes/java/main tools/dim1/CityPlanSanityCheck.java}
 */
public class CityPlanSanityCheck {

    public static void main(String[] args) {
        final long seed = 12345L;
        CityPlan city = null;
        outer: for (int cellX = -10; cellX <= 10; cellX++) {
            for (int cellZ = -10; cellZ <= 10; cellZ++) {
                city = CityPlanner.planFor(seed, cellX, cellZ);
                if (city != null) {
                    break outer;
                }
            }
        }
        if (city == null) {
            System.out.println("SANITY FAIL: no city found in scan grid");
            System.exit(1);
        }
        final CityPlan fc = city;
        System.out.println(
            "CITY centerChunk=" + city.getCenterChunkX() + "," + city.getCenterChunkZ() + " radius="
                + city.getRadiusChunks());
        // —— S-A3 断言 (d) 之一：总扰动硬约束（自适应 25-35% × 城半径）——
        final double capBlocks = CityPlan.EDGE_TOT_FRAC_MAX * city.getRadiusChunks() * 16;
        final double minBlocks = CityPlan.EDGE_TOT_FRAC_MIN * city.getRadiusChunks() * 16;
        if (city.edgePerturbationBlocks() > capBlocks + 1e-9) {
            fail("edge perturbation beyond hard cap: " + city.edgePerturbationBlocks() + " > " + capBlocks);
        }
        if (city.edgePerturbationBlocks() < minBlocks - 1e-9) {
            fail("edge perturbation below floor: " + city.edgePerturbationBlocks() + " < " + minBlocks);
        }
        // —— S-A3 断言 (c)：kept plot ⊆ 独立重算偏置几何门（plotIndexLimit 口径网格）——
        final int lim = city.plotIndexLimit();
        int gridChecked = 0;
        int keptPlots = 0;
        int geometricIn = 0;
        int droppedByClosure = 0;
        int avenueAdjacentKept = 0;
        for (int k = -lim; k <= lim; k++) {
            for (int l = -lim; l <= lim; l++) {
                final boolean kept = city.plotInCircle(k, l);
                final boolean geoIn = independentGeometricIn(city, k, l);
                if (kept && !geoIn) {
                    fail("kept plot (" + k + "," + l + ") fails independent biased geometric gate"
                        + " (second boundary formula suspected)");
                }
                if (kept) {
                    keptPlots++;
                    if (k == -1 || k == 0 || l == -1 || l == 0) {
                        avenueAdjacentKept++;
                    }
                }
                if (geoIn) {
                    geometricIn++;
                    if (!kept) {
                        droppedByClosure++;
                    }
                }
                gridChecked++;
            }
        }
        if (keptPlots == 0 || avenueAdjacentKept == 0) {
            fail("degenerate plot set: kept=" + keptPlots + " avenueAdjacentKept=" + avenueAdjacentKept);
        }
        int chunksChecked = 0;
        int plotCallbacks = 0;
        int streetCallbacks = 0;
        final Set<String> variantsSeen = new HashSet<>();
        for (int cx = city.getCenterChunkX() - city.getRadiusChunks() - 1; cx <= city.getCenterChunkX()
            + city.getRadiusChunks() + 1; cx++) {
            for (int cz = city.getCenterChunkZ() - city.getRadiusChunks() - 1; cz <= city.getCenterChunkZ()
            + city.getRadiusChunks() + 1; cz++) {
                final int fcx = cx;
                final int fcz = cz;
                chunksChecked++;
                city.forEachPlotInChunk(cx, cz, (variant, ox, oz, plotSeed) -> {
                    final CityVariants.Variant v = CityVariants.byName(variant);
                    if (v == null) {
                        fail("unregistered variant in plot: " + variant);
                    }
                    // 锚点必须落在城市 contentReach 内（半径 + 扰动 + 偏置 + 内容余量）
                    final int du = ox - fc.getCenterX();
                    final int dv = oz - fc.getCenterZ();
                    final int reach = fc.contentReachBlocks();
                    if (du * du + dv * dv > reach * reach) {
                        fail("anchor beyond city reach: (" + ox + "," + oz + ") variant=" + variant);
                    }
                    variantsSeen.add(variant);
                });
                city.forEachStreetColumn(cx, cz, (wx, wz, sleeper) -> {
                    if ((wx >> 4) != fcx || (wz >> 4) != fcz) {
                        fail("street column outside visited chunk (" + wx + "," + wz + ") for chunk " + fcx + ","
                            + fcz);
                    }
                    // S-A3 断言 (a) 之二：街道回调格必须被唯一边界函数判定为城内（与 plot 门同侧）
                    if (!fc.insideCity(wx, wz)) {
                        fail("street column judged OUTSIDE by insideCity: (" + wx + "," + wz + ")");
                    }
                    // S-A3 断言 (b)：街道回调坐标全部在 contentReach 内
                    final int sdu = wx - fc.getCenterX();
                    final int sdv = wz - fc.getCenterZ();
                    final int sReach = fc.contentReachBlocks();
                    if (sdu * sdu + sdv * sdv > sReach * sReach) {
                        fail("street column beyond city reach: (" + wx + "," + wz + ")");
                    }
                });
                // S-A3 断言 (a) 之三：街道裁剪与 insideCity∧onStreet 独立重算无多无漏（双向同侧）
                if (countStreets(city, cx, cz) != countInsideStreet(city, cx, cz)) {
                    fail("street clip diverges from insideCity+onStreet recount in chunk " + cx + "," + cz);
                }
                plotCallbacks += countPlots(city, cx, cz);
                streetCallbacks += countStreets(city, cx, cz);
            }
        }
        if (plotCallbacks == 0 || streetCallbacks == 0) {
            fail("degenerate: plots=" + plotCallbacks + " streets=" + streetCallbacks);
        }
        // —— S-A3 断言 (d) 之二：总扰动约束全量实测（双 seed × 101×101 cell；按城半径归一）——
        final long[] sweepSeeds = { seed, 0x50524F53L };
        double maxRel = city.edgePerturbationBlocks() / (double) (city.getRadiusChunks() * 16);
        double minRel = maxRel;
        int citiesSwept = 0;
        for (final long s : sweepSeeds) {
            for (int sx = -50; sx <= 50; sx++) {
                for (int sz = -50; sz <= 50; sz++) {
                    final CityPlan p = CityPlanner.planFor(s, sx, sz);
                    if (p == null) {
                        continue;
                    }
                    citiesSwept++;
                    final double rel = p.edgePerturbationBlocks() / (double) (p.getRadiusChunks() * 16);
                    if (rel > CityPlan.EDGE_TOT_FRAC_MAX + 1e-9 || rel < CityPlan.EDGE_TOT_FRAC_MIN - 1e-9) {
                        fail("amplitude sweep out of band: rel=" + rel + " at cell " + sx + "," + sz);
                    }
                    maxRel = Math.max(maxRel, rel);
                    minRel = Math.min(minRel, rel);
                }
            }
        }
        System.out.println(
            "SANITY PASS: chunks=" + chunksChecked + " plotCallbacks=" + plotCallbacks + " streetColumns="
                + streetCallbacks
                + " distinctVariantsSeen="
                + variantsSeen.size()
                + " plotGateGrid=" + gridChecked
                + " keptPlots=" + keptPlots
                + " geometricIn=" + geometricIn
                + " droppedByClosure=" + droppedByClosure
                + " avenueAdjacentKept=" + avenueAdjacentKept
                + " citiesSwept=" + citiesSwept
                + " edgeTotFrac=[" + String.format("%.3f", minRel) + "," + String.format("%.3f", maxRel) + "]"
                + " (band=[" + CityPlan.EDGE_TOT_FRAC_MIN + "," + CityPlan.EDGE_TOT_FRAC_MAX + "]) "
                + variantsSeen);
        System.out.println("SANITY DONE");
    }

    /**
     * 独立重算 plot 偏置几何门（S-A3 断言 (c) 的对照腿）：用公开 API
     * {@link CityPlan#plotEdgeBias} + {@link CityPlan#insideCity(int, int)} 复刻
     * 「plot 中心沿径向偏置 bias 后问唯一边界函数」的采样——若 CityPlan 内部藏了第二套
     * 边界公式，kept 集与本重算必出现分歧。
     */
    private static boolean independentGeometricIn(CityPlan city, int k, int l) {
        final double du = k * CityPlan.STREET_SPACING + 8.0;
        final double dv = l * CityPlan.STREET_SPACING + 8.0;
        final double d = Math.sqrt(du * du + dv * dv);
        if (d < 1e-9) {
            return true;
        }
        final double scale = (d + city.plotEdgeBias(k, l)) / d;
        return city.insideCity(
            city.getCenterX() + (int) Math.round(du * scale),
            city.getCenterZ() + (int) Math.round(dv * scale));
    }

    private static int countPlots(CityPlan city, int cx, int cz) {
        final int[] n = { 0 };
        city.forEachPlotInChunk(cx, cz, (v, x, z, s) -> n[0]++);
        return n[0];
    }

    private static int countStreets(CityPlan city, int cx, int cz) {
        final int[] n = { 0 };
        city.forEachStreetColumn(cx, cz, (x, z, s) -> n[0]++);
        return n[0];
    }

    /**
     * 独立重算：chunk 内 insideCity∧onStreet 格数（S-A3 断言 (a) 的对照腿——街道裁剪若旁路
     * 唯一边界函数，城界附近计数必出现偏差）。
     */
    private static int countInsideStreet(CityPlan city, int cx, int cz) {
        final int[] n = { 0 };
        for (int x = cx * 16; x < cx * 16 + 16; x++) {
            for (int z = cz * 16; z < cz * 16 + 16; z++) {
                if (city.insideCity(x, z) && city.onStreet(x, z)) {
                    n[0]++;
                }
            }
        }
        return n[0];
    }

    private static void fail(String msg) {
        System.out.println("SANITY FAIL: " + msg);
        System.exit(1);
    }
}
