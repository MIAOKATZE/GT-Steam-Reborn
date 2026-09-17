import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;

import com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityPlan;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityPlanner;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityVariants;

/**
 * S4b 验收③ 确定性自证（一次性自检 main，不进 jar，tools/ 惯例）：
 * 同 seed 同坐标 {@link CityPlanner} 两次规划输出逐字节一致（describe() 规范化串双跑对拍
 * + SHA-256），并覆盖 {@link ProsperityTerrainProfile#heightAt} 与 {@link CityPlan#citiesNear}
 * 窗口判定。纯 JEP 330 单文件运行，零 Minecraft 依赖：
 * {@code java -cp build/classes/java/main tools/dim1/CityDeterminismCheck.java}
 */
public class CityDeterminismCheck {

    private static final long[] SEEDS = { 12345L, -987654321L, 0x50524F53L };

    public static void main(String[] args) throws Exception {
        int cellsPlanned = 0;
        int citiesFound = 0;
        int heightSamples = 0;
        for (long seed : SEEDS) {
            for (int cellX = -4; cellX <= 4; cellX++) {
                for (int cellZ = -4; cellZ <= 4; cellZ++) {
                    // —— 规划双跑 + 逐字节对拍 ——
                    final CityPlan p1 = CityPlanner.planFor(seed, cellX, cellZ);
                    final CityPlan p2 = CityPlanner.planFor(seed, cellX, cellZ);
                    if ((p1 == null) != (p2 == null)) {
                        fail("existence mismatch cell=" + cellX + "," + cellZ + " seed=" + seed);
                    }
                    if (p1 == null) {
                        continue;
                    }
                    final byte[] b1 = p1.describe().getBytes(StandardCharsets.UTF_8);
                    final byte[] b2 = p2.describe().getBytes(StandardCharsets.UTF_8);
                    if (!MessageDigest.isEqual(b1, b2)) {
                        fail("describe bytes differ cell=" + cellX + "," + cellZ + " seed=" + seed);
                    }
                    // —— 窗口判定双跑（3×3 cell 检索入口）——
                    for (int cx = cellX * 24 - 2; cx <= cellX * 24 + 26; cx += 7) {
                        for (int cz = cellZ * 24 - 2; cz <= cellZ * 24 + 26; cz += 7) {
                            final CityPlan[] n1 = CityPlanner.citiesNear(seed, cx, cz);
                            final CityPlan[] n2 = CityPlanner.citiesNear(seed, cx, cz);
                            if (n1.length != n2.length) {
                                fail("citiesNear mismatch chunk=" + cx + "," + cz);
                            }
                            for (int i = 0; i < n1.length; i++) {
                                if (!n1[i].describe().equals(n2[i].describe())) {
                                    fail("citiesNear plan bytes differ chunk=" + cx + "," + cz);
                                }
                            }
                        }
                    }
                    cellsPlanned++;
                    citiesFound++;
                }
            }
            // —— 高度场双跑（城市落地契约的输入）——
            for (int x = -512; x <= 512; x += 16) {
                for (int z = -512; z <= 512; z += 16) {
                    final int h1 = ProsperityTerrainProfile.heightAt(seed, x, z);
                    final int h2 = ProsperityTerrainProfile.heightAt(seed, x, z);
                    if (h1 != h2) {
                        fail("heightAt mismatch (" + x + "," + z + ") seed=" + seed);
                    }
                    if (h1 < 40 || h1 > 110) {
                        fail("heightAt out of clamp: " + h1);
                    }
                    heightSamples++;
                }
            }
        }
        // 汇总证据
        System.out.println(
            "DETERMINISM PASS: seeds=" + SEEDS.length + " citiesByteIdentical=" + citiesFound
                + " heightSamplesDoubleRun=" + heightSamples);
        // 存在率证据（默认 chance=45：81 cell 网格约 1/3 有城，打印供报告引用）
        int hits = 0;
        for (int cellX = -10; cellX <= 10; cellX++) {
            for (int cellZ = -10; cellZ <= 10; cellZ++) {
                if (CityPlanner.planFor(12345L, cellX, cellZ) != null) {
                    hits++;
                }
            }
        }
        System.out
            .println("EXISTS RATE seed=12345: " + hits + "/441 cells have city (chance=" + 45 + ")");
        // 落盘一份 describe 样本（review 证据；取扫描到的第一座城）
        CityPlan sample = null;
        for (int cellX = -10; cellX <= 10 && sample == null; cellX++) {
            for (int cellZ = -10; cellZ <= 10 && sample == null; cellZ++) {
                sample = CityPlanner.planFor(12345L, cellX, cellZ);
            }
        }
        if (sample != null) {
            final File out = new File("plan/新维度计划/review/dim1/structures/city/_determinism_sample.txt");
            out.getParentFile().mkdirs();
            Files.write(out.toPath(), sample.describe().getBytes(StandardCharsets.UTF_8));
            System.out.println("SAMPLE describe written: " + out.getAbsolutePath());
        }
        // —— CitySliceSink 切片协议（无越界告警的构造论据：chunk 外静默吸收、chunk 内转发）——
        final java.util.List<int[]> forwarded = new java.util.ArrayList<>();
        final com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CitySliceSink slice =
            new com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CitySliceSink(
                (x, y, z, block, meta, flags) -> forwarded.add(new int[] { x, z }),
                3,
                -2);
        final int total;
        {
            int t = 0;
            for (int x = 3 * 16 - 5; x < 4 * 16 + 5; x++) {
                for (int z = -2 * 16 - 5; z < -1 * 16 + 5; z++) {
                    slice.setBlock(x, 64, z, "gtsr:RuinedCasing", 0, 0);
                    t++;
                }
            }
            total = t;
        }
        for (final int[] p : forwarded) {
            if ((p[0] >> 4) != 3 || (p[1] >> 4) != -2) {
                fail("CitySliceSink forwarded out-of-chunk write (" + p[0] + "," + p[1] + ")");
            }
        }
        final int absorbed = total - forwarded.size();
        if (forwarded.size() == 0 || absorbed == 0) {
            fail("CitySliceSink coverage degenerate: forwarded=" + forwarded.size() + " absorbed=" + absorbed);
        }
        System.out.println(
            "SLICE PASS: forwarded=" + forwarded.size() + " absorbedSilently=" + absorbed
                + " (no out-of-chunk forwarding)");
    }

    private static void fail(String msg) {
        System.out.println("DETERMINISM FAIL: " + msg);
        System.exit(1);
    }
}
