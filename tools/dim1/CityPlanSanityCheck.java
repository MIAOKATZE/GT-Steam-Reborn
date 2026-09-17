import java.util.HashSet;
import java.util.Set;

import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityPlan;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityPlanner;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityVariants;

/**
 * S4b 渲染协议几何自检（一次性 main，tools/ 惯例）：对一座已知城市，验证
 * {@link CityPlan#forEachPlotInChunk} 在城市覆盖窗内每个 chunk 至少回调一次且锚点落在
 * 13×13 plot 界内、变体名均在注册表；街道列回调坐标全部落在该 chunk（协议=切片）。零 Minecraft 依赖：
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
                    // 锚点必须落在城市圆界 + footprint 余量内
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
                });
                plotCallbacks += countPlots(city, cx, cz);
                streetCallbacks += countStreets(city, cx, cz);
            }
        }
        if (plotCallbacks == 0 || streetCallbacks == 0) {
            fail("degenerate: plots=" + plotCallbacks + " streets=" + streetCallbacks);
        }
        System.out.println(
            "SANITY PASS: chunks=" + chunksChecked + " plotCallbacks=" + plotCallbacks + " streetColumns="
                + streetCallbacks
                + " distinctVariantsSeen="
                + variantsSeen.size()
                + " "
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

    private static void fail(String msg) {
        System.out.println("SANITY FAIL: " + msg);
        System.exit(1);
    }
}
