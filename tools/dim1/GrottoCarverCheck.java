import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import net.minecraft.world.biome.BiomeGenBase;

import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile;
import com.miaokatze.gtsr.common.dimension.prosperity.river.GTSRVoronoiRiverField;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.GrottoCarver;

/**
 * <b>v1.20.54 P31 I-A 新增：GrottoCarver 层降洞离线判据（A–G 组 + D 组双负对照；批I 期
 * 手工跑，批II 挂载 surface_checks.sh [3u9] 定稿）</b>。使命：P31 层降大重构（N 级台地 12–20 格
 * 下行 + 底部静水潭）的量化门——悬空水 0 容忍 / 层降可走 / 密封（V-roofed 默认 + --vstrict 双
 * 模式）/ 负对照自证 / 池存在 / 露天度 / 选址净空（plan/p31-plan.md §3.4）。
 * <p>
 * ═══ 采样口径（= P17TerrainReliefCheck VARIANT 组 / P31GrottoPreProbe 同口径）═══
 * 16 seed（worldSeed = 0..15）× 1024² 窗（原点 (0,0)）× 步距 4 ⇒ 每 seed 256×256 列、全探针
 * 1,048,576 列。<b>纯函数求值、零世界读取</b>（仅离线哑元账本装配身份面）；水顶/湿核/台地
 * 全部直调生产 {@link GrottoCarver} 公开谓词（单一真值，零镜像——D 组扰动腿除外，见下）。
 * seed=10/12 原点窗零 site 是已知覆盖特性（p31-freeze §2 申报），E 组分母 16 不豁免。
 * <p>
 * ═══ 组判据（量化门 §3.4）═══
 * <ul>
 * <li><b>A 悬空水 0 容忍</b>：采样水列（{@link GrottoCarver#grottoWaterTopAt} ≥ floorY+1）逐水格
 * 4 侧邻 ∈ {实心, 水}（实心含留顶列的顶盖段 [yTop+1,h0]）——违例计数 <b>== 0</b>；</li>
 * <li><b>B 层降可走</b>：B1 riser = SPAN/TREADS ≤ 2（湖滨"高差 ≤2 台阶"口径 RVF:955-962；
 * 红档梯 TREADS 5→6，§3.5-2）；B2 每 seed site 列台地档数 ∈ [4,8]（TREADS+1=6 档全实现；
 * 逐 blob 档掩码 report-only——步距 4 下窄环带漏采是采样伪影，不作门）；B3 全 site 列总深
 * round(depth) ∈ [12,20]；邻列 floorY 落差 p95/max report-only（h0 场 ±1-2 与 o 带渐变主导，
 * 可走性主断言在 B1 的量化档差 ≤2）；</li>
 * <li><b>C 密封</b>：C1 侧暴露 == 0（与 A 同计数）；C2 顶暴露（<b>V-roofed 默认</b>，主代理 P31
 * 拍板：水顶可为完整顶盖下的洞腔空气，"顶暴露" = 见天/塌口连通）= 置水列 open（见天/塌口）
 * 计数 == 0；{@code --vstrict} 开关切严口径（水顶上一格必须实心/水——满腔水袋读数，
 * report 不作默认门）；</li>
 * <li><b>D 负对照自证</b>（两条都必须红 = 扰动被检出，否则检查器欠灵敏、禁信正绿）：
 * D-i 旧 RC1 语义（barrier = 邻列 carve 腔顶 −1，P30 :216/:278 缺陷载体）在新几何重算 ⇒
 * 悬空违例 &gt; 0；D-ii {@link GrottoCarver#GROTTO_WATER_DEPTH_MAX} +1（=3）扰动（判据侧
 * 镜像场重算，镜像纪律同 SwampPoolBlobCensusCheck）⇒ 悬空违例 &gt; 0 或水列数漂移；</li>
 * <li><b>E 池存在</b>：有潭 seed（水列 ≥ 4 采样格 ≈ 8×8 实格潭）≥ <b>12/16</b>（宁缺不悬不灭池）；</li>
 * <li><b>F 露天度</b>：openFrac = wetOpen/wet（wet = 湿核采样列，wetOpen = 其 open 列）∈
 * <b>[0.27,0.33]</b>（P30 带 0.30±0.03 延续；新几何新 THR 0.30 域内闭环，旧读数不迁移
 * plan §7.2）；</li>
 * <li><b>G 选址净空</b>：G1 site ∩ 河谷带（0 &lt; s &lt;
 * {@link GrottoCarver#GROTTO_RIVERBED_AVOID}）== 0（第七门实证）；G2 <b>水列 8 邻无未挖缘列
 * （m &lt; 0.5）== 0</b>——潭周环的结构门（≥1；未挖列床 = h0 顶高墙，水贴它是"潭壁"而非
 * "环缺口"）。<b>2 格环宽取 report-only 读数</b>（rimAt2：5×5 内 m 缘列数）——实测陡缘处
 * 台地带被 m 场梯度压缩到 1 格，任何湿域侵蚀环都会经"深干邻排干"机制塌池（E 门 11-13/16
 * 摆动 vs 硬门 ≥12，见 GrottoCarver 类注释 P31 I-A 申报 1）⇒ 2 格形与池存在性在陡缘几何里
 * 不可兼得，主代理裁量申报；洞内门洞壁〔m≥0.5 的非 site 实心列〕是合格潭壁，gateHoleAdj
 * 另计 report-only。</li>
 * </ul>
 * 退出码：0 = A–G 全绿且 D 双负对照均被检出；1 = 任一违例 / 探针失明（totSite==0）/
 * D 负对照意外干净。
 */
public final class GrottoCarverCheck {

    /** 名册序哑元 id 表（P17TerrainReliefCheck/P31GrottoPreProbe 同款离线装配）。 */
    private static final int[] IDS = { 180, 181, 182, 183 };

    private static final int SEEDS = 16;
    private static final int SIDE = 1024;
    private static final int STRIDE = 4;
    /** D-ii 扰动档：潭深帽 +1（生产 GROTTO_WATER_DEPTH_MAX=2 ⇒ 扰动 3）。 */
    private static final int DEPTH_MAX_PERTURBED = 3;

    public static void main(String[] args) {
        boolean vstrict = false;
        for (final String a : args) {
            if ("--vstrict".equals(a)) {
                vstrict = true;
            }
        }
        initLedger();
        final int g = SIDE / STRIDE;
        long totSite = 0, totWet = 0, totWetOpen = 0, totWater = 0;
        long sideHang = 0, topExpose = 0, topExposeStrict = 0;
        long g1SiteInValley = 0, g2RimNear = 0, g2RimAt2 = 0, gateHoleAdj = 0, landingCols = 0;
        long d1Viol = 0, d2Water = 0;
        long d1Water = 0;
        int seedsWithPool = 0;
        int minDepth = Integer.MAX_VALUE, maxDepth = Integer.MIN_VALUE;
        int maxPair = 0;
        final long[] pairHist = new long[16];
        final boolean[] seedTreadsOK = new boolean[SEEDS];
        boolean b2All = true;
        long t0 = System.currentTimeMillis();
        for (int s = 0; s < SEEDS; s++) {
            final long seed = s;
            PERTURB_FIELDS.clear(); // (seed, 区域) 双键语义：seed 轮换时整清，防跨 seed 串场
            long site = 0, wet = 0, wetOpen = 0, water = 0, d2WaterSeed = 0, d1WaterSeed = 0, landingSeed = 0;
            int treadMask = 0;
            // 列级缓存（4 邻落差与 blob 掩码复用）
            final boolean[] isSite = new boolean[g * g];
            final int[] floorYs = new int[g * g];
            final int[] treadOf = new int[g * g];
            for (int sz = 0; sz < g; sz++) {
                for (int sx = 0; sx < g; sx++) {
                    final int x = sx * STRIDE, z = sz * STRIDE;
                    final double m = GrottoCarver.grottoMAt(seed, x, z);
                    if (m < 0.5D || !GrottoCarver.grottoSiteAt(seed, x, z)) {
                        continue;
                    }
                    final int gi = sz * g + sx;
                    isSite[gi] = true;
                    site++;
                    treadOf[gi] = GrottoCarver.grottoTreadAt(seed, x, z);
                    treadMask |= 1 << treadOf[gi];
                    final int depth = (int) Math.round(GrottoCarver.grottoDepthAt(seed, x, z));
                    if (depth < minDepth) minDepth = depth;
                    if (depth > maxDepth) maxDepth = depth;
                    floorYs[gi] = ProsperityTerrainProfile.heightAt(seed, x, z) - depth;
                    // G1：第七门实证（site ∩ 0<s<AVOID）
                    final double sPress = -GTSRVoronoiRiverField.strengthAt(seed, x, z);
                    if (sPress > 0.0D && sPress < GrottoCarver.GROTTO_RIVERBED_AVOID) {
                        g1SiteInValley++;
                    }
                    if (GrottoCarver.grottoLandingAt(seed, x, z)) {
                        landingCols++;
                        landingSeed++;
                    }
                    // 湿核/潭
                    if (GrottoCarver.grottoWetAt(seed, x, z)) {
                        wet++;
                        if (GrottoCarver.grottoOpenAt(seed, x, z)) {
                            wetOpen++;
                        }
                        final int wTop = GrottoCarver.grottoWaterTopAt(seed, x, z);
                        if (wTop >= floorYs[gi] + 1) {
                            water++;
                            // A/C1：逐水格 4 侧邻 ∈ {实心, 水}
                            for (int y = floorYs[gi] + 1; y <= wTop; y++) {
                                for (int dir = 0; dir < 4; dir++) {
                                    final int nx = x + (dir == 0 ? 1 : dir == 1 ? -1 : 0);
                                    final int nz = z + (dir == 2 ? 1 : dir == 3 ? -1 : 0);
                                    if (!solidAt(seed, nx, nz, y) && !waterAt(seed, nx, nz, y)) {
                                        sideHang++;
                                    }
                                }
                            }
                            // C2 顶暴露：V-roofed = 置水列不得 open（见天/塌口）
                            if (GrottoCarver.grottoOpenAt(seed, x, z)) {
                                topExpose++;
                            }
                            // C2 严口径读数（--vstrict 才作门）：水顶上一格须实心/水
                            if (!solidAt(seed, x, z, wTop + 1) && !waterAt(seed, x, z, wTop + 1)) {
                                topExposeStrict++;
                            }
                            // G2：水列 8 邻无未挖缘列（Chebyshev-1 结构门——潭周环 ≥1：未挖列
                            // 床=h0 顶高墙，水贴它是"潭壁"而非"环缺口"）；5×5 陡缘压缩读数 report-only
                            for (int dz = -1; dz <= 1; dz++) {
                                for (int dx = -1; dx <= 1; dx++) {
                                    if (dx == 0 && dz == 0) {
                                        continue;
                                    }
                                    final double nm = GrottoCarver.grottoMAt(seed, x + dx, z + dz);
                                    if (nm < 0.5D) {
                                        g2RimNear++;
                                    }
                                }
                            }
                            for (int dz = -2; dz <= 2; dz++) {
                                for (int dx = -2; dx <= 2; dx++) {
                                    final double nm = GrottoCarver.grottoMAt(seed, x + dx, z + dz);
                                    if (nm < 0.5D) {
                                        g2RimAt2++;
                                    } else if (!GrottoCarver.grottoSiteAt(seed, x + dx, z + dz)) {
                                        gateHoleAdj++;
                                    }
                                }
                            }
                        }
                        // D-i：旧 RC1 barrier（carve 腔顶 −1）单趟钳制重算
                        final int d1 = waterTopOldRC1(seed, x, z);
                        if (d1 >= floorYs[gi] + 1) {
                            d1WaterSeed++;
                            for (int y = floorYs[gi] + 1; y <= d1; y++) {
                                for (int dir = 0; dir < 4; dir++) {
                                    final int nx = x + (dir == 0 ? 1 : dir == 1 ? -1 : 0);
                                    final int nz = z + (dir == 2 ? 1 : dir == 3 ? -1 : 0);
                                    if (!solidAt(seed, nx, nz, y) && !waterOldRC1At(seed, nx, nz, y)) {
                                        d1Viol++;
                                    }
                                }
                            }
                        }
                        // D-ii：潭深帽 +1 扰动（判据侧镜像场）
                        final int d2 = waterTopPerturbed(seed, x, z);
                        if (d2 >= floorYs[gi] + 1) {
                            d2WaterSeed++;
                        }
                    }
                }
            }
            // B 报表腿：4 邻样本对 floorY 落差（report-only）
            for (int sz = 0; sz < g; sz++) {
                for (int sx = 0; sx < g; sx++) {
                    final int gi = sz * g + sx;
                    if (!isSite[gi]) {
                        continue;
                    }
                    if (sx + 1 < g && isSite[gi + 1]) {
                        final int d = Math.abs(floorYs[gi] - floorYs[gi + 1]);
                        if (d > maxPair) maxPair = d;
                        if (d < pairHist.length) pairHist[d]++;
                    }
                    if (sz + 1 < g && isSite[gi + g]) {
                        final int d = Math.abs(floorYs[gi] - floorYs[gi + g]);
                        if (d > maxPair) maxPair = d;
                        if (d < pairHist.length) pairHist[d]++;
                    }
                }
            }
            final int treads = Integer.bitCount(treadMask);
            seedTreadsOK[s] = site == 0 || (treads >= 4 && treads <= 8);
            b2All &= seedTreadsOK[s];
            if (water >= 4) seedsWithPool++;
            totSite += site;
            totWet += wet;
            totWetOpen += wetOpen;
            totWater += water;
            d1Water += d1WaterSeed;
            d2Water += d2WaterSeed;
            System.out.println(
                "GROTTO seed=" + s + " site=" + site + " treads=" + treads + " wet=" + wet + " wetOpen=" + wetOpen
                    + " water=" + water + " d1Water=" + d1WaterSeed + " d1Viol=" + (d1Viol > 0 ? "+" : 0)
                    + " d2Water=" + d2WaterSeed + " landing=" + landingSeed);
        }
        final double openFrac = totWet == 0 ? 0.0D : totWetOpen / (double) totWet;
        final double riser = GrottoCarver.GROTTO_DEPTH_SPAN / GrottoCarver.GROTTO_TREADS;
        // ── 组判定 ──
        boolean fail = false;
        if (totSite == 0) {
            System.out.println("GROTTOCHECK SANITY totSite==0 (probe blind or site set gone) FAIL");
            fail = true;
        }
        System.out.println("GROTTOCHECK A sideHang=" + sideHang + " (gate ==0) " + pass(sideHang == 0));
        fail |= sideHang != 0;
        System.out.println(
            "GROTTOCHECK B riser=" + String.format("%.2f", riser) + " (<=2) " + pass(riser <= 2.0D)
                + " | seedTreadsAll=" + b2All + " ([4,8]) " + pass(b2All) + " | depthDomain=[" + minDepth + ","
                + maxDepth + "] ([12,20]) " + pass(minDepth >= 12 && maxDepth <= 20));
        fail |= riser > 2.0D || !b2All || minDepth < 12 || maxDepth > 20;
        System.out.println(
            "GROTTOCHECK C sideHang(same as A)=" + sideHang + " " + pass(sideHang == 0) + " | topExposeVRoofed="
                + topExpose + " (==0) " + pass(topExpose == 0) + (vstrict
                    ? " | topExposeStrict=" + topExposeStrict + " (==0, GATE) " + pass(topExposeStrict == 0)
                    : " | topExposeStrict=" + topExposeStrict + " (report, --vstrict to gate)"));
        fail |= sideHang != 0 || topExpose != 0 || (vstrict && topExposeStrict != 0);
        System.out.println(
            "GROTTOCHECK D oldRC1Viol=" + d1Viol + " (>0) " + pass(d1Viol > 0) + " | dm3 waterShift="
                + (totWater - d2Water) + " (baseline " + totWater + " -> " + d2Water + ", expect shift or viol) "
                + pass(d2Water != totWater));
        fail |= d1Viol <= 0 || d2Water == totWater;
        System.out.println(
            "GROTTOCHECK E seedsWithPool=" + seedsWithPool + "/" + SEEDS + " (>=12) " + pass(seedsWithPool >= 12));
        fail |= seedsWithPool < 12;
        System.out.println(
            "GROTTOCHECK F openFrac=" + String.format("%.4f", openFrac) + " (in [0.27,0.33]) "
                + pass(openFrac >= 0.27D && openFrac <= 0.33D));
        fail |= openFrac < 0.27D || openFrac > 0.33D;
        System.out.println(
            "GROTTOCHECK G siteInValley=" + g1SiteInValley + " (==0) " + pass(g1SiteInValley == 0)
                + " | rimAdjWater=" + g2RimNear + " (==0, Chebyshev-1 结构门) " + pass(g2RimNear == 0)
                + " | rimAt2Report=" + g2RimAt2 + " | gateHoleAdj=" + gateHoleAdj + " (report) | maxPairDeltaFloorY="
                + maxPair);
        fail |= g1SiteInValley != 0 || g2RimNear != 0;
        System.out.println(
            "GROTTOCHECK SUMMARY site=" + totSite + " wet=" + totWet + " wetOpen=" + totWetOpen + " water="
                + totWater + " landingCols=" + landingCols + " ms=" + (System.currentTimeMillis() - t0)
                + (fail ? " -> FAIL" : " -> ALL GREEN"));
        System.exit(fail ? 1 : 0);
    }

    private static String pass(boolean ok) {
        return ok ? "PASS" : "FAIL";
    }

    // ───────────────────────── 列级实心/水分类（A/C 组复算，生产谓词单一真值）─────────────────────

    /** y 格在 (x,z) 列是否<b>实心</b>（含留顶列顶盖段 [h0−t+1,h0]；carve 后口径）。 */
    private static boolean solidAt(long seed, int x, int z, int y) {
        final double m = GrottoCarver.grottoMAt(seed, x, z);
        final int h0 = ProsperityTerrainProfile.heightAt(seed, x, z);
        if (m < 0.5D || !GrottoCarver.grottoSiteAt(seed, x, z)) {
            return y <= h0;
        }
        final int floorY = GrottoCarver.grottoFloorYAt(seed, x, z);
        if (y <= floorY) {
            return true;
        }
        if (GrottoCarver.grottoOpenAt(seed, x, z)) {
            return false; // open 列腔直通地表
        }
        return y >= h0 - roofThicknessMirror(m) + 1; // 留顶列：顶盖段实心
    }

    /** y 格在 (x,z) 列是否<b>水</b>（生产水顶单一真值）。 */
    private static boolean waterAt(long seed, int x, int z, int y) {
        final double m = GrottoCarver.grottoMAt(seed, x, z);
        if (m < GrottoCarver.GROTTO_WATER_M || !GrottoCarver.grottoSiteAt(seed, x, z)) {
            return false;
        }
        final int floorY = GrottoCarver.grottoFloorYAt(seed, x, z);
        final int w = GrottoCarver.grottoWaterTopAt(seed, x, z);
        return w >= floorY + 1 && y >= floorY + 1 && y <= w;
    }

    /** 顶厚镜像（= 生产 roofThicknessAt 同式同值，判据只读）。 */
    private static int roofThicknessMirror(double m) {
        final double t = Math.max(0.0D, Math.min(1.0D, (m - GrottoCarver.GROTTO_RIM_M)
            / (GrottoCarver.GROTTO_ROOF_FULL_M - GrottoCarver.GROTTO_RIM_M)));
        final double e = t * t * (3.0D - 2.0D * t);
        return (int) Math.round(GrottoCarver.GROTTO_ROOF_T * e);
    }

    // ───────────────────────── D-i：旧 RC1 barrier 语义（barrier = carve 腔顶 −1）─────────────────────

    /** 旧 post-carve 腔顶（P30 :208-217 语义：留顶列 = h0 − t = 腔内空气顶，非实心顶）。 */
    private static int oldCarveCavityTopAt(long seed, int x, int z) {
        final double m = GrottoCarver.grottoMAt(seed, x, z);
        if (m < 0.5D || !GrottoCarver.grottoSiteAt(seed, x, z)) {
            return ProsperityTerrainProfile.heightAt(seed, x, z);
        }
        if (GrottoCarver.grottoOpenAt(seed, x, z)) {
            return GrottoCarver.grottoFloorYAt(seed, x, z);
        }
        return ProsperityTerrainProfile.heightAt(seed, x, z) - roofThicknessMirror(m);
    }

    /** D-i 水顶：单趟 N8 min（8 邻 oldCarveCavityTopAt − 1，P30 :272-283 逐字语义）。 */
    private static int waterTopOldRC1(long seed, int x, int z) {
        if (GrottoCarver.grottoOpenAt(seed, x, z)) {
            return -1;
        }
        final int floorY = GrottoCarver.grottoFloorYAt(seed, x, z);
        int wTop = floorY + GrottoCarver.GROTTO_WATER_DEPTH_MAX;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                final int barrier = oldCarveCavityTopAt(seed, x + dx, z + dz) - 1;
                if (barrier < wTop) {
                    wTop = barrier;
                }
            }
        }
        return wTop;
    }

    /** D-i 邻列水分类（D 场自洽：邻水 = D-i 水顶）。 */
    private static boolean waterOldRC1At(long seed, int x, int z, int y) {
        final double m = GrottoCarver.grottoMAt(seed, x, z);
        if (m < GrottoCarver.GROTTO_WATER_M || GrottoCarver.grottoTreadAt(seed, x, z) != GrottoCarver.GROTTO_TREADS
            || !GrottoCarver.grottoSiteAt(seed, x, z)) {
            return false;
        }
        final int floorY = GrottoCarver.grottoFloorYAt(seed, x, z);
        final int w = waterTopOldRC1(seed, x, z);
        return w >= floorY + 1 && y >= floorY + 1 && y <= w;
    }

    // ───────────────── D-ii：潭深帽 +1 扰动（区域场镜像，nominal = floorY + 3；镜像纪律同 census）─────────────────

    private static final int PERTURB_REGION = 64; // 扰动场用更小区域（只查采样列局部，节省构建成本）
    private static final int PERTURB_WINDOW = 48;
    private static final int PERTURB_SIDE = PERTURB_REGION + 2 * PERTURB_WINDOW;
    private static final HashMap<Long, int[][]> PERTURB_FIELDS = new HashMap<>();

    /** D-ii 水顶：同构区域场不动点，唯 nominal = floorY + DEPTH_MAX_PERTURBED。 */
    private static int waterTopPerturbed(long seed, int x, int z) {
        final int rx = Math.floorDiv(x, PERTURB_REGION) * PERTURB_REGION;
        final int rz = Math.floorDiv(z, PERTURB_REGION) * PERTURB_REGION;
        final Long key = Long.valueOf(((long) rx << 32) | (rz & 0xFFFFFFFFL));
        int[][] f = PERTURB_FIELDS.get(key);
        if (f == null) {
            f = buildPerturbedField(seed, rx, rz);
            PERTURB_FIELDS.put(key, f);
        }
        final int fi = (x - rx + PERTURB_WINDOW) + (z - rz + PERTURB_WINDOW) * PERTURB_SIDE;
        return f[0][fi] >= f[1][fi] + 1 ? f[0][fi] : -1;
    }

    private static int[][] buildPerturbedField(long seed, int rx, int rz) {
        final int w = PERTURB_SIDE;
        final int[] nominal = new int[w * w], bed = new int[w * w], tops = new int[w * w];
        for (int lz = 0; lz < w; lz++) {
            for (int lx = 0; lx < w; lx++) {
                final int i = lz * w + lx;
                final int x = rx - PERTURB_WINDOW + lx, z = rz - PERTURB_WINDOW + lz;
                final double m = GrottoCarver.grottoMAt(seed, x, z);
                final boolean site = m >= 0.5D && GrottoCarver.grottoSiteAt(seed, x, z);
                bed[i] = site ? GrottoCarver.grottoFloorYAt(seed, x, z)
                    : ProsperityTerrainProfile.heightAt(seed, x, z);
                final boolean wetCore = site && m >= GrottoCarver.GROTTO_WATER_M
                    && GrottoCarver.grottoTreadAt(seed, x, z) == GrottoCarver.GROTTO_TREADS;
                nominal[i] = wetCore && !GrottoCarver.grottoOpenAt(seed, x, z) ? bed[i] + DEPTH_MAX_PERTURBED
                    : Integer.MIN_VALUE;
                tops[i] = nominal[i];
            }
        }
        for (int pass = 0; pass < 96; pass++) {
            boolean changed = false;
            final int[] cur = tops.clone();
            for (int lz = 0; lz < w; lz++) {
                for (int lx = 0; lx < w; lx++) {
                    final int i = lz * w + lx;
                    if (nominal[i] == Integer.MIN_VALUE) {
                        continue;
                    }
                    int t = cur[i];
                    for (int dz = -1; dz <= 1; dz++) {
                        final int nz = lz + dz;
                        if (nz < 0 || nz >= w) {
                            continue;
                        }
                        for (int dx = -1; dx <= 1; dx++) {
                            if (dx == 0 && dz == 0) {
                                continue;
                            }
                            final int nx = lx + dx;
                            if (nx < 0 || nx >= w) {
                                continue;
                            }
                            final int b = Math.max(cur[nz * w + nx], bed[nz * w + nx]);
                            if (b < t) {
                                t = b;
                            }
                        }
                    }
                    if (t < cur[i]) {
                        tops[i] = t;
                        changed = true;
                    }
                }
            }
            if (!changed) {
                break;
            }
        }
        return new int[][] { tops, bed };
    }

    /** 离线账本初始化（P31GrottoPreProbe 同式；无账本 ⇒ chainRosterIndexAt 恒 −1、无 roster 3）。 */
    private static void initLedger() {
        final List<GTSRBiomeAuthority.BiomeId> keys = new ArrayList<>();
        for (final GTSRBiomeAuthority.BiomeId key : GTSRBiomeAuthority.BiomeId.values()) {
            if (GTSRBiomeAuthority.DIM_KEY_PROSPERITY.equals(key.dimKey())) {
                keys.add(key);
            }
        }
        for (int i = 0; i < IDS.length; i++) {
            GTSRBiomeAuthority.recordAllocation(keys.get(i), IDS[i], IDS[i], new BiomeGenBase(IDS[i]) {});
        }
    }
}
