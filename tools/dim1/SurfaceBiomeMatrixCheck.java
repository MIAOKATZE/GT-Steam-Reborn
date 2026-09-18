import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * dim78 S-A1 验收②（plan §4 S-A1 + §12 修订 2/4/10）离线矩阵断言（一次性自检 main，不进 jar，
 * tools/ 惯例）：四群系 (topBlock, field_150604_aj, fillerBlock, fillerMeta) 四元组互异、
 * ChunkProviderProsperityRuins fillerMetaOf/baseBlockOf 表与群系常量一致、12 新方块注册链完整
 * （BlocksGTSR 声明 + BlockLoader registerBlock）、四群系 biomeId 分配不降级（ ProsperityBiomes
 * 四槽 attachBiome + 成功/降级日志锚点 + Config id 段不重叠）、lang 键齐。
 * <p>
 * <b>零 Minecraft 依赖</b>：方块/群系类均继承 MC 类型，JEP330 无 MC classpath 下反射/类加载即
 * NoClassDefFoundError——故本工具以<b>源码文本断言</b>为口径（读 src/main/java 各文件，
 * 正则提取接线行与常量），运行方式：
 * {@code java -cp build/classes/java/main tools/dim1/SurfaceBiomeMatrixCheck.java}（cwd=仓库根）。
 * 已知边界：这是接线级（源码结构）断言，不证明运行时注册成功——运行时以 runServer 日志锚点
 * {@code [GTSR] prosperity biomes: ... registered (... 4/4 slots free)} 为准（用户实机后补项）。
 */
public class SurfaceBiomeMatrixCheck {

    private static final String[] BIOMES = {
        "BiomeRustedSteppe", "BiomeGearworkForest", "BiomeBrassWastes", "BiomeFumaroleSwamp" };

    private static final String[] NATURAL_BLOCKS = { "prosperitySteppeTop", "prosperitySteppeBase",
        "prosperityForestTop", "prosperityForestBase", "prosperityWastesTop", "prosperityWastesBase",
        "prosperitySwampTop", "prosperitySwampBase", "prosperityTuftRust", "prosperityTuftCopper",
        "prosperityRustLog", "prosperityRustLeaves" };

    private static final String[] LANG_FILES = { "src/main/resources/assets/gtsr/lang/en_US.lang",
        "src/main/resources/assets/gtsr/lang/zh_CN.lang" };

    private static final String SRC = "src/main/java/com/miaokatze/gtsr";

    public static void main(String[] args) throws Exception {
        final Path root = findRepoRoot();
        final Map<String, String[]> matrix = new LinkedHashMap<>();
        // —— 1. 四群系接线 + meta 常量（四元组互异的前半：源码事实）——
        for (final String biome : BIOMES) {
            final String src = read(root, SRC + "/common/dimension/prosperity/biome/" + biome + ".java");
            final String top = expectGroup(src, "this.topBlock = BlocksGTSR\\.(\\w+);", biome);
            final String filler = expectGroup(src, "this.fillerBlock = BlocksGTSR\\.(\\w+);", biome);
            final int topMeta = Integer
                .parseInt(expectGroup(src, "public static final int TOP_META = (\\d+);", biome));
            final int fillerMeta = Integer
                .parseInt(expectGroup(src, "public static final int FILLER_META = (\\d+);", biome));
            check(topMeta >= 0 && topMeta < 16, biome + " TOP_META out of 0..15: " + topMeta);
            check(fillerMeta >= 0 && fillerMeta < 16, biome + " FILLER_META out of 0..15: " + fillerMeta);
            check(!top.equals("prosperitySurface") && !filler.equals("prosperitySurface"),
                biome + " still wired to frozen meta block prosperitySurface (plan §12-2 violation)");
            matrix.put(biome, new String[] { top, String.valueOf(topMeta), filler, String.valueOf(fillerMeta) });
        }
        // —— 2. 四元组互异（top/topMeta/filler/fillerMeta 逐对比较）——
        for (int i = 0; i < BIOMES.length; i++) {
            for (int j = i + 1; j < BIOMES.length; j++) {
                final String[] a = matrix.get(BIOMES[i]);
                final String[] b = matrix.get(BIOMES[j]);
                final boolean identical = a[0].equals(b[0]) && a[1].equals(b[1]) && a[2].equals(b[2])
                    && a[3].equals(b[3]);
                check(!identical, "surface quadruple identical: " + BIOMES[i] + " vs " + BIOMES[j]);
                check(!a[0].equals(b[0]), "topBlock collision: " + BIOMES[i] + " vs " + BIOMES[j]);
                check(!a[2].equals(b[2]), "fillerBlock collision: " + BIOMES[i] + " vs " + BIOMES[j]);
            }
        }
        // —— 3. fillerMetaOf / baseBlockOf 表与群系常量一致（ChunkProvider 同步，:129-143 区域）——
        final String provider = read(root, SRC + "/common/dimension/prosperity/ChunkProviderProsperityRuins.java");
        for (int i = 0; i < BIOMES.length; i++) {
            final String biome = BIOMES[i];
            final String baseField = matrix.get(biome)[2];
            check(provider.contains("return " + biome + ".FILLER_META;"),
                "fillerMetaOf table missing constant wiring for " + biome);
            check(provider.contains("return BlocksGTSR." + baseField + ";"),
                "baseBlockOf table missing " + baseField + " for " + biome);
            final int count = countOf(provider, "instanceof " + biome);
            check(count >= 2, provider + " instanceof " + biome + " wiring count=" + count + " (<2)");
        }
        // —— 4. 注册链完整：BlocksGTSR 声明 + BlockLoader registerBlock + 创造栏 ——
        final String blocks = read(root, SRC + "/common/blocks/BlocksGTSR.java");
        final String loader = read(root, SRC + "/loader/BlockLoader.java");
        for (final String field : NATURAL_BLOCKS) {
            check(blocks.contains("public static Block " + field + ";"), "BlocksGTSR missing field " + field);
            check(loader.contains("registerBlock(BlocksGTSR." + field + ","), "BlockLoader missing register " + field);
            check(loader.contains("BlocksGTSR." + field), "BlockLoader missing init " + field);
        }
        check(loader.contains("prosperity natural blocks registered"), "BlockLoader missing evidence log anchor");
        // —— 5. biomeId 分配不降级（对照 ProsperityBiomes.java:43-70 逻辑）——
        final String biomes = read(root, SRC + "/common/dimension/prosperity/biome/ProsperityBiomes.java");
        final String config = read(root, SRC + "/config/Config.java");
        check(biomes.contains("private static final int BIOME_SLOT_COUNT = 4;"), "BIOME_SLOT_COUNT != 4");
        for (int slot = 0; slot < 4; slot++) {
            check(biomes.contains("attachBiome(def, start + " + slot + ","), "missing attachBiome slot " + slot);
        }
        check(biomes.contains("[GTSR] prosperity biomes: "), "missing success log anchor (plan §6.1 grep point)");
        check(biomes.contains("already occupied by"), "missing degrade warn anchor (plan R3)");
        final int idStart = Integer.parseInt(expectGroup(config, "prosperityBiomeIdStart = (\\d+);", "Config"));
        final int shatteredStart = Integer
            .parseInt(expectGroup(config, "shatteredBiomeIdStart = (\\d+);", "Config"));
        check(idStart == 180, "prosperityBiomeIdStart moved: " + idStart);
        check(idStart + 4 <= shatteredStart, "id range overlap: prosperity " + idStart + ".." + (idStart + 3));
        // —— 6. lang 键齐（en_US + zh_CN，12 键 × 2 文件）——
        for (final String lang : LANG_FILES) {
            final String text = read(root, lang);
            for (final String field : NATURAL_BLOCKS) {
                final String key = "tile." + Character.toUpperCase(field.charAt(0)) + field.substring(1) + ".name=";
                check(text.contains(key), lang + " missing lang key " + key);
            }
        }
        // —— 汇总证据 ——
        System.out.println("SURFACE MATRIX (biome -> topBlock/topMeta/fillerBlock/fillerMeta):");
        for (final Map.Entry<String, String[]> e : matrix.entrySet()) {
            System.out.println(
                "  " + String.format("%-22s", e.getKey()) + " -> " + String.join(" / ", e.getValue()));
        }
        System.out.println("MATRIX PASS: biomes=4 quadruplesDistinct=true providerTableSync=true");
        System.out.println(
            "REGISTRATION PASS: blocks=" + NATURAL_BLOCKS.length + " (8 terrain + 4 decor) declared+registered");
        System.out.println("BIOMEID PASS: slots 4/4 wired, idStart=" + idStart + ".." + (idStart + 3) + " no-degrade anchors present");
        final Set<String> textureNames = new LinkedHashSet<>();
        for (final String field : NATURAL_BLOCKS) {
            textureNames.add(field);
        }
        System.out.println("LANG PASS: " + LANG_FILES.length + " lang files x " + textureNames.size() + " keys");
    }

    /** 正则提取（唯一匹配），缺失/多匹配即 FAIL。 */
    private static String expectGroup(String src, String regex, String where) throws IOException {
        final Matcher m = Pattern.compile(regex).matcher(src);
        check(m.find(), where + " missing pattern: " + regex);
        final String group = m.group(1);
        check(!m.find(), where + " duplicated pattern: " + regex);
        return group;
    }

    private static int countOf(String src, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = src.indexOf(needle, idx)) >= 0) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    private static String read(Path root, String rel) throws IOException {
        final Path path = root.resolve(rel);
        check(Files.exists(path), "missing file: " + rel);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    /** 从 cwd 向上找仓库根（含 src/main/java 的最近祖先），支持子目录运行。 */
    private static Path findRepoRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 5 && dir != null; i++) {
            if (Files.exists(dir.resolve("src/main/java"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("repo root with src/main/java not found from cwd");
    }

    private static void check(boolean ok, String msg) {
        if (!ok) {
            System.out.println("MATRIX FAIL: " + msg);
            System.exit(1);
        }
    }
}
