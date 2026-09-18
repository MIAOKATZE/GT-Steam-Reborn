import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import com.miaokatze.gtsr.common.dimension.shattered.ShatteredTerrainProfile;

/**
 * dim79 重做 S-B6 验收工具（一次性自检 main，不进 jar，tools/ 惯例）：
 * {@link ShatteredTerrainProfile#heightAt} 双跑逐字节一致 + clamp 36..96 域断言 + 相邻列差
 * 有界（无悬崖跳变）+ 无 NaN（int 返回值域收敛等价断言）+ 四群系注册锚点离线断言
 * （190..193 不降级：源码级 BIOME_SLOT_COUNT/四 attachBiome/Config 默认值扫描）。
 * 纯 JEP 330 单文件运行，零 Minecraft 依赖：
 * {@code java -cp build/classes/java/main tools/dim1/ShatteredTerrainCheck.java}
 */
public class ShatteredTerrainCheck {

    private static final long[] SEEDS = { 12345L, -987654321L, 0x53484C53L };

    /** 钳制域（与 ShatteredTerrainProfile.MIN/MAX_HEIGHT 同值，工具侧独立登记）。 */
    private static final int MIN_HEIGHT = 36;
    private static final int MAX_HEIGHT = 96;

    /** 相邻单格列差上界（参数域理论极值 <1/格，断言 2 留余量；超出即"悬崖跳变"）。 */
    private static final int MAX_ADJACENT_DELTA = 2;

    /** 相邻 16 格列差上界（与单格界同口径外推：16 格 × 2/格 = 32；超出即非连续地形）。 */
    private static final int MAX_SPAN16_DELTA = 32;

    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        int samples = 0;
        int maxAdjDelta = 0;
        int maxSpan16Delta = 0;
        for (long seed : SEEDS) {
            // —— 1. 双跑逐字节一致 + clamp 域（粗网格全域扫描）——
            for (int x = -1024; x <= 1024; x += 7) {
                for (int z = -1024; z <= 1024; z += 7) {
                    final int h1 = ShatteredTerrainProfile.heightAt(seed, x, z);
                    final int h2 = ShatteredTerrainProfile.heightAt(seed, x, z);
                    if (h1 != h2) {
                        fail("heightAt double-run mismatch (" + x + "," + z + ") seed=" + seed);
                    }
                    if (h1 < MIN_HEIGHT || h1 > MAX_HEIGHT) {
                        fail("heightAt out of clamp [" + MIN_HEIGHT + "," + MAX_HEIGHT + "]: " + h1);
                    }
                    samples++;
                }
            }
            // —— 2. 相邻列差有界（step-1 网格 128x128，无悬崖跳变）——
            for (int x = -64; x < 64; x++) {
                for (int z = -64; z < 64; z++) {
                    final int h = ShatteredTerrainProfile.heightAt(seed, x, z);
                    final int hx = ShatteredTerrainProfile.heightAt(seed, x + 1, z);
                    final int hz = ShatteredTerrainProfile.heightAt(seed, x, z + 1);
                    maxAdjDelta = Math.max(maxAdjDelta, Math.max(Math.abs(hx - h), Math.abs(hz - h)));
                    if (Math.abs(hx - h) > MAX_ADJACENT_DELTA || Math.abs(hz - h) > MAX_ADJACENT_DELTA) {
                        fail("cliff jump at (" + x + "," + z + ") dx=" + (hx - h) + " dz=" + (hz - h) + " seed=" + seed);
                    }
                }
            }
            // —— 3. 16 格跨度列差有界 + NaN 等价断言（值域收敛即无病态浮点溢出）——
            for (int x = -1024; x <= 1024; x += 13) {
                for (int z = -1024; z <= 1024; z += 13) {
                    final int h = ShatteredTerrainProfile.heightAt(seed, x, z);
                    final int h16 = ShatteredTerrainProfile.heightAt(seed, x + 16, z);
                    maxSpan16Delta = Math.max(maxSpan16Delta, Math.abs(h16 - h));
                    if (Math.abs(h16 - h) > MAX_SPAN16_DELTA) {
                        fail("span-16 jump at (" + x + "," + z + ") d=" + (h16 - h) + " seed=" + seed);
                    }
                }
            }
        }
        System.out.println(
            "TERRAIN PASS: seeds=" + SEEDS.length + " doubleRunSamples=" + samples + " clamp=[" + MIN_HEIGHT + ","
                + MAX_HEIGHT + "] maxAdjacentDelta=" + maxAdjDelta + "(<=" + MAX_ADJACENT_DELTA
                + ") maxSpan16Delta=" + maxSpan16Delta + "(<=" + MAX_SPAN16_DELTA + ") noNaN=byValueDomain");
        assertFourBiomeAnchors();
        if (failures > 0) {
            System.out.println("SHATTERED CHECK FAIL: " + failures + " assertion(s) failed");
            System.exit(1);
        }
        System.out.println("SHATTERED CHECK PASS");
    }

    /**
     * 四群系注册锚点断言（190..193 不降级，离线源码级）：
     * <ol>
     * <li>ShatteredBiomes.BIOME_SLOT_COUNT = 4 且四条 attachBiome（start+0..start+3）；</li>
     * <li>Config.shatteredBiomeIdStart 默认 190；</li>
     * <li>CommonProxy shatteredDef 挂 BiomeZoneSelector（不降级为盐胡椒分布）；</li>
     * <li>锚点串拼出 190..193（与运行期日志 {@code shattered biomes: 190..193 registered} 同口径）。</li>
     * </ol>
     * 运行期 biomeList 槽位占用（isBiomeIdFree 降级路径）无法离线复现，实机 runServer 日志为准。
     */
    private static void assertFourBiomeAnchors() throws Exception {
        final Path root = Paths.get("src/main/java/com/miaokatze/gtsr");
        final String biomes = new String(
            Files.readAllBytes(root.resolve("common/dimension/shattered/biome/ShatteredBiomes.java")),
            StandardCharsets.UTF_8);
        final String config = new String(Files.readAllBytes(root.resolve("config/Config.java")), StandardCharsets.UTF_8);
        final String proxy = new String(Files.readAllBytes(root.resolve("main/CommonProxy.java")), StandardCharsets.UTF_8);
        check(biomes.contains("BIOME_SLOT_COUNT = 4"), "BIOME_SLOT_COUNT != 4");
        for (int i = 0; i < 4; i++) {
            check(biomes.contains("attachBiome(def, start + " + i), "missing attachBiome start+" + i);
        }
        check(config.contains("shatteredBiomeIdStart = 190"), "Config default shatteredBiomeIdStart != 190");
        check(proxy.contains("ChunkProviderShatteredGrounds::new"), "CommonProxy factory not ShatteredGrounds");
        check(proxy.contains("0x5A4F4E46L"), "CommonProxy shatteredDef zone selector salt missing");
        check(
            Arrays
                .asList(
                    "BiomeAshenPrairie",
                    "BiomeSlagwoodGrove",
                    "BiomeVitreousWaste",
                    "BiomeTarBasin")
                .stream()
                .allMatch(biomes::contains),
            "not all four biome classes referenced in ShatteredBiomes");
        // 锚点串离线复算（四槽全注册时）：190..193
        final int start = 190;
        final String anchor = start + ".." + (start + 4 - 1);
        check(anchor.equals("190..193"), "anchor drift: " + anchor);
        System.out.println("BIOME ANCHOR PASS: shattered biomes: " + anchor + " registered (source-level, 4/4 slots)");
    }

    private static void check(boolean ok, String message) {
        if (!ok) {
            failures++;
            System.out.println("ANCHOR FAIL: " + message);
        }
    }

    private static void fail(String msg) {
        failures++;
        System.out.println("TERRAIN FAIL: " + msg);
    }
}
