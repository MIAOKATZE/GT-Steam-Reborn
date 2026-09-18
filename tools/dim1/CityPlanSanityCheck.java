import java.util.HashSet;
import java.util.Set;

import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityPlan;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityPlanner;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityVariants;

/**
 * S4b 渲染协议几何自检（一次性 main，tools/ 惯例）：对一座已知城市，验证
 * {@link CityPlan#forEachPlotInChunk} 在城市覆盖窗内每个 chunk 至少回调一次且锚点落在
 * 13×13 plot 界内、变体名均在注册表；街道列回调坐标全部落在该 chunk（协议=切片）。
 * dim78-fix S-A3 扩两条断言：(a) 边界判定同侧性——plot 门与街道裁剪对唯一边界函数
 * {@link CityPlan#insideCity(int, int)} 全网格委托一致、街道回调格全部被判城内且与
 * insideCity∧onStreet 独立重算无多无漏；(b) 全部回调坐标在 reach（radius*16+32）内。
 * 另断言 Fourier 振幅硬约束 Σ|a_k|·radiusBlocks ≤ 12（样本城 + 双 seed × 101×101 cell 实测）。
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
        // —— S-A3：Fourier 振幅硬约束（样本城即断言；全量实测扫描见文末 sweep）——
        if (city.edgePerturbationBlocks() > CityPlan.MAX_EDGE_PERTURBATION + 1e-9) {
            fail("edge amplitude beyond hard cap: " + city.edgePerturbationBlocks());
        }
        // —— S-A3 断言 (a) 之一：plot 门对 insideCity 全网格委托一致（同 CityPlan kLim 口径）——
        final int lim = city.getRadiusChunks() + 1; // = radiusBlocks/16 + 1
        int gridChecked = 0;
        for (int k = -lim; k <= lim; k++) {
            for (int l = -lim; l <= lim; l++) {
                final int pcx = city.getCenterX() + k * 16 + 8;
                final int pcz = city.getCenterZ() + l * 16 + 8;
                if (city.plotInCircle(k, l) != city.insideCity(pcx, pcz)) {
                    fail("plot gate diverges from insideCity at plot (" + k + "," + l + ")");
                }
                gridChecked++;
            }
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
                    // 锚点必须落在城市 reach（radius*16+32）+ footprint 余量内
                    final int du = ox - fc.getCenterX();
                    final int dv = oz - fc.getCenterZ();
                    final int reach = fc.getRadiusChunks() * 16 + 32;
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
                    // S-A3 断言 (b)：街道回调坐标全部在 reach（radius*16+32）内
                    final int sdu = wx - fc.getCenterX();
                    final int sdv = wz - fc.getCenterZ();
                    final int sReach = fc.getRadiusChunks() * 16 + 32;
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
        // —— S-A3 振幅硬约束全量实测（双 seed × 101×101 cell；供切片报告登记最大值）——
        final long[] sweepSeeds = { seed, 0x50524F53L };
        double maxAmp = city.edgePerturbationBlocks();
        int citiesSwept = 0;
        for (final long s : sweepSeeds) {
            for (int sx = -50; sx <= 50; sx++) {
                for (int sz = -50; sz <= 50; sz++) {
                    final CityPlan p = CityPlanner.planFor(s, sx, sz);
                    if (p == null) {
                        continue;
                    }
                    citiesSwept++;
                    maxAmp = Math.max(maxAmp, p.edgePerturbationBlocks());
                }
            }
        }
        if (maxAmp > CityPlan.MAX_EDGE_PERTURBATION + 1e-9) {
            fail("amplitude sweep beyond hard cap: " + maxAmp);
        }
        System.out.println(
            "SANITY PASS: chunks=" + chunksChecked + " plotCallbacks=" + plotCallbacks + " streetColumns="
                + streetCallbacks
                + " distinctVariantsSeen="
                + variantsSeen.size()
                + " plotGateGrid=" + gridChecked
                + " citiesSwept=" + citiesSwept
                + " maxEdgeAmp=" + String.format("%.3f", maxAmp)
                + " (cap=" + CityPlan.MAX_EDGE_PERTURBATION + ") "
                + variantsSeen);
        System.out.println("SANITY DONE");
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
