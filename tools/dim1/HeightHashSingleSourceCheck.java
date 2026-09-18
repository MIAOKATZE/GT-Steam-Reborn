import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <b>P3 判据 4：L2「单一真值」结构自检（可证伪）</b>
 * （plan §5 P3 / §2.1 L2「禁止各层自算高度；禁止手搓哈希（须走 GTSRWorldgenHash）」/
 * §2.4 判据 4「单一真值」+ 判据 5「新判据一律先 RED 后 GREEN」）。
 * <p>
 * 与 {@code SurfaceYParityCheck}（行为对拍）互补：那一份证明"合并前后逐位相同"，
 * 本份证明"仓内<b>只剩申报的那几处</b>实现"——即结构性收敛真的发生，而不是又多了几层转发。
 * <p>
 * ═══ <b>列名申报</b>（本工具的唯一预期表；改期望必须连同 plan 一起改，不允许默默放宽）═══
 * <pre>
 *  A. 哈希常数 10 个（splitmix 两常数 / splitmix 增量 / cell x,z 乘子 / column x,z 乘子 /
 *     chunk z 乘子 / 两把城市盐乘子 / 长整非负掩码）
 *        → 每个在<b>去注释后的代码行</b>里恰好 1 次，且只能在
 *          framework/structure/GTSRWorldgenHash.java
 *  B. splitmix 的 xor-shift 步（{@code >>> 33}）→ 恰好 6 次，同文件（终结器 3 + 截断器 2 +
 *     单步 xorShiftOnce 1）；其它文件 0 次
 *  C. 噪声核 valueNoise / unitNoise / floorDiv(double) / smooth(double) → 各恰好 1 处，同文件；
 *     旧名 hashUnit 作为独立实现体必须为 0 处（已被 unitNoise 吸收，不留残骸）
 *  D. World 列扫 {@code findSurfaceY(World,..)} → 恰好 3 处：
 *        framework/GTSRChunkProviderBase.java 1（P3 合并件，唯一"能并"的那份）
 *        shattered/WorldGenShatteredRuins.java 1（#7，真表面门更严，禁止并）
 *        common/world/WorldGenRunawaySingularity.java 1（#8，带 dim 下界语义，禁止并）
 *     无参 {@code findSurfaceY()} → 恰好 1 处：framework/GTSRDimTeleporter.java（#1，禁止并）
 *     {@code findSurfaceY} 定义总数 → 恰好 4（任何一处新拷贝都立刻变红）
 *  E. bedrockTop(long,..) / 裸数组自顶向下扫（{@code for (int y = 254}）/ mixColumn
 *     → 各恰好 1 处，均在 framework/GTSRChunkProviderBase.java
 *  F. 已申报合并的原站点（5 个 placer/scatter + 2 个 TerrainProfile）
 *     → 定义数必须为 0，且必须仍能解析到合并件（引用检查）
 *  G. 禁止合并的 3 处 → 必须各存在 1 处，且其源码含 {@code P3 登记} 标记与对应审计编号
 * </pre>
 * <p>
 * <b>用法</b>：{@code java HeightHashSingleSourceCheck [srcRoot=src/main/java]}
 * —— {@code srcRoot} 可指向<b>影子树</b>，用于演示本判据可被人为破坏变红
 * （RED→GREEN 两步见 {@code tools/dim1/surface_checks.sh} 的 [7] 段）。纯 JDK、零 MC 依赖。
 * 退出码：0 = 全绿；1 = 有申报项被破坏（逐条打 {@code FAIL <标记>:}）；2 = 源码根不存在。
 */
public final class HeightHashSingleSourceCheck {

    /** 唯一允许的哈希与噪声出处（相对 srcRoot 的路径后缀）。 */
    private static final String HASH_SOURCE =
        "com/miaokatze/gtsr/common/dimension/framework/structure/GTSRWorldgenHash.java";
    /** 唯一允许的列扫合并件 / bedrockTop / 裸数组扫出处。 */
    private static final String SCAN_SOURCE =
        "com/miaokatze/gtsr/common/dimension/framework/GTSRChunkProviderBase.java";
    /** 禁止合并的三处（审计 A-5 §1 #1/#7/#8）。 */
    private static final String SITE_TELEPORTER =
        "com/miaokatze/gtsr/common/dimension/framework/GTSRDimTeleporter.java";
    private static final String SITE_RUINS =
        "com/miaokatze/gtsr/common/dimension/shattered/WorldGenShatteredRuins.java";
    private static final String SITE_SINGULARITY = "com/miaokatze/gtsr/common/world/WorldGenRunawaySingularity.java";

    /** 申报常数 → 语义标签（出现任何一次都在申报里）。 */
    private static final Map<String, String> PINNED_CONSTANTS = new LinkedHashMap<>();
    /** 申报的"禁止合并"站点：文件 → 必须出现在其源码中的审计编号。 */
    private static final Map<String, String> NON_MERGED_SITES = new LinkedHashMap<>();
    /** 申报"已并入框架件"的原站点：不得再自带实现体，且必须仍能引用到合并件。 */
    private static final List<String> MERGED_SCAN_SITES = Arrays.asList(
        "com/miaokatze/gtsr/common/dimension/prosperity/ruins/ProsperitySurfaceScatter.java",
        "com/miaokatze/gtsr/common/dimension/prosperity/ruins/RuinedMachinePlacer.java",
        "com/miaokatze/gtsr/common/dimension/prosperity/ruins/ProsperityDecorPlacer.java",
        "com/miaokatze/gtsr/common/dimension/prosperity/ruins/ProsperityOutpostPlacer.java",
        "com/miaokatze/gtsr/common/dimension/shattered/ShatteredDecorPlacer.java");
    private static final List<String> MERGED_NOISE_SITES = Arrays.asList(
        "com/miaokatze/gtsr/common/dimension/prosperity/ProsperityTerrainProfile.java",
        "com/miaokatze/gtsr/common/dimension/shattered/ShatteredTerrainProfile.java");

    static {
        PINNED_CONSTANTS.put("0xFF51AFD7ED558CCDL", "splitmix64 混合常数 ①");
        PINNED_CONSTANTS.put("0xC4CEB9FE1A85EC53L", "splitmix64 混合常数 ②");
        PINNED_CONSTANTS.put("0xBF58476D1CE4E5B9L", "splitmix64 标准增量（本仓复用为乘子）");
        PINNED_CONSTANTS.put("0x9E3779B97F4A7C15L", "cell 级 x 乘子");
        PINNED_CONSTANTS.put("0xC2B2AE3D27D4EB4FL", "cell 级 z 乘子");
        PINNED_CONSTANTS.put("0x27D4EB2F165667C5L", "列/格点 x 乘子");
        PINNED_CONSTANTS.put("0x165667B19E3779F9L", "列/格点 z 乘子");
        PINNED_CONSTANTS.put("0x94D049BB133111EBL", "chunk 级边带 z 乘子");
        PINNED_CONSTANTS.put("0xD1B54A32D192ED03L", "城市选址盐乘子");
        PINNED_CONSTANTS.put("0x7FFFFFFFFFFFFFFFL", "长整非负掩码");

        NON_MERGED_SITES.put(SITE_TELEPORTER, "A-5 §1 #1");
        NON_MERGED_SITES.put(SITE_RUINS, "A-5 §1 #7");
        NON_MERGED_SITES.put(SITE_SINGULARITY, "A-5 §1 #8");
    }

    /** 一条申报：标记 + 匹配模式 + 允许文件集合 + 全仓期望总次数。 */
    private static final class Rule {

        final String tag;
        final Pattern pattern;
        final List<String> allowed;
        final Map<String, Integer> perFile;
        final int total;

        Rule(String tag, String regex, String... allowedWithCounts) {
            this.tag = tag;
            this.pattern = Pattern.compile(regex);
            this.allowed = new ArrayList<>();
            this.perFile = new TreeMap<>();
            int sum = 0;
            for (int i = 0; i < allowedWithCounts.length; i += 2) {
                final int c = Integer.parseInt(allowedWithCounts[i + 1]);
                this.allowed.add(allowedWithCounts[i]);
                this.perFile.put(allowedWithCounts[i], c);
                sum += c;
            }
            this.total = sum;
        }
    }

    private static final List<Rule> RULES = Arrays.asList(
        new Rule("XOR-SHIFT-33", ">>>\\s*33", HASH_SOURCE, "6"),
        new Rule("NOISE-VALUE", "\\bdouble\\s+valueNoise\\s*\\(", HASH_SOURCE, "1"),
        new Rule("NOISE-UNIT", "\\bdouble\\s+unitNoise\\s*\\(", HASH_SOURCE, "1"),
        new Rule("NOISE-FLOORDIV", "\\bint\\s+floorDiv\\s*\\(\\s*(?:final\\s+)?double\\b", HASH_SOURCE, "1"),
        new Rule("NOISE-SMOOTH", "\\bdouble\\s+smooth\\s*\\(\\s*(?:final\\s+)?double\\b", HASH_SOURCE, "1"),
        new Rule("NOISE-LEGACY-HASHUNIT", "\\bdouble\\s+hashUnit\\s*\\("),
        new Rule("SCAN-WORLD", "\\bint\\s+findSurfaceY\\s*\\(\\s*(?:final\\s+)?World\\b", SCAN_SOURCE, "1", SITE_RUINS,
            "1", SITE_SINGULARITY, "1"),
        new Rule("SCAN-TELEPORTER", "\\bint\\s+findSurfaceY\\s*\\(\\s*\\)", SITE_TELEPORTER, "1"),
        new Rule("SCAN-TOTAL", "\\bint\\s+findSurfaceY\\s*\\(", SCAN_SOURCE, "1", SITE_RUINS, "1", SITE_SINGULARITY,
            "1", SITE_TELEPORTER, "1"),
        new Rule("BEDROCK-CORE", "\\bint\\s+bedrockTop\\s*\\(\\s*(?:final\\s+)?long\\b", SCAN_SOURCE, "1"),
        new Rule("INLINE-SCAN", "for\\s*\\(\\s*int\\s+y\\s*=\\s*254\\s*;", SCAN_SOURCE, "1"),
        new Rule("MIXCOLUMN", "\\blong\\s+mixColumn\\s*\\(", SCAN_SOURCE, "1"));

    private static final List<String> PROBLEMS = new ArrayList<>();
    private static final Map<String, Integer> STATS = new TreeMap<>();

    private HeightHashSingleSourceCheck() {}

    public static void main(String[] args) throws IOException {
        final Path root = Paths.get(args.length > 0 ? args[0] : "src/main/java");
        if (!Files.isDirectory(root)) {
            System.out.println("SRC ROOT MISSING: " + root.toAbsolutePath());
            System.exit(2);
        }
        final List<Path> javaFiles = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.getFileName()
                    .toString()
                    .endsWith(".java")) {
                    javaFiles.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        Collections.sort(javaFiles);
        STATS.put("javaFiles", javaFiles.size());

        // 每个模式 key → 命中位置（file:line）
        final Map<String, List<String>> hits = new TreeMap<>();
        final Map<String, String> fileTexts = new TreeMap<>();
        for (final Path f : javaFiles) {
            final String rel = relativize(root, f);
            final String raw = readText(f);
            fileTexts.put(rel, raw);
            final List<String> code = codeLines(raw);
            for (int i = 0; i < code.size(); i++) {
                final String line = code.get(i);
                if (line.trim()
                    .isEmpty()) {
                    continue;
                }
                for (final Map.Entry<String, String> c : PINNED_CONSTANTS.entrySet()) {
                    if (line.contains(c.getKey())) {
                        bump(hits, "CONST:" + c.getKey(), rel + ':' + (i + 1));
                    }
                }
                for (final Rule r : RULES) {
                    if (r.pattern.matcher(line)
                        .find()) {
                        bump(hits, r.tag, rel + ':' + (i + 1));
                    }
                }
            }
        }

        // [A] 常数申报：每个恰好 1 次，且只能在唯一出处
        for (final Map.Entry<String, String> c : PINNED_CONSTANTS.entrySet()) {
            checkDeclared("HASH-LITERAL " + c.getKey() + "（" + c.getValue() + ")", hits.get("CONST:" + c.getKey()),
                single(HASH_SOURCE));
        }
        // [B..E] 模式申报
        for (final Rule r : RULES) {
            checkDeclared(r.tag + " " + r.pattern, hits.get(r.tag), declaration(r));
        }
        // [F] 已申报合并的原站点：不得自带实现体，且必须仍能引用合并件
        for (final String merged : MERGED_SCAN_SITES) {
            final String text = orEmpty(fileTexts.get(merged));
            if (text.contains("int findSurfaceY(")) {
                fail("MERGED-SCAN", "原站点仍自带列扫实现体: " + merged);
            }
            if (!text.contains("findSurfaceY")) {
                fail("MERGED-SCAN", "原站点已不引用任何列扫（改名或漏接？）: " + merged);
            }
        }
        for (final String merged : MERGED_NOISE_SITES) {
            final String text = orEmpty(fileTexts.get(merged));
            if (text.contains("valueNoise(long seed, double x, double z)") || text.contains("double hashUnit(")) {
                fail("MERGED-NOISE", "合并件仍自带噪声核实现体: " + merged);
            }
            if (!text.contains("GTSRWorldgenHash.valueNoise")) {
                fail("MERGED-NOISE", "合并件已不引用噪声唯一出处: " + merged);
            }
        }
        // [G] 禁止合并的站点必须在场且带登记标记
        for (final Map.Entry<String, String> site : NON_MERGED_SITES.entrySet()) {
            final String text = fileTexts.get(site.getKey());
            if (text == null) {
                fail("SITE-ROSTER", "申报的不可并站点文件缺失: " + site.getKey());
                continue;
            }
            if (!text.contains("int findSurfaceY(")) {
                fail("SITE-ROSTER", "申报的不可并站点被误并/删除: " + site.getKey() + " " + site.getValue());
            }
            if (!text.contains("P3 登记") || !text.contains(site.getValue())) {
                fail("SITE-ROSTER",
                    "不可并站点缺 P3 登记标记或审计编号 " + site.getValue() + ": " + site.getKey());
            }
        }

        for (final Map.Entry<String, Integer> e : STATS.entrySet()) {
            System.out.println("STAT " + e.getKey() + "=" + e.getValue());
        }
        for (final Map.Entry<String, List<String>> e : hits.entrySet()) {
            System.out.println("HIT " + e.getKey() + " count=" + e.getValue().size() + " at=" + e.getValue());
        }
        System.out.println("CHECKS problems=" + PROBLEMS.size() + " filesScanned=" + javaFiles.size());
        for (final String p : PROBLEMS) {
            System.out.println("  " + p);
        }
        if (!PROBLEMS.isEmpty()) {
            System.out.println("HEIGHT/HASH SINGLE-SOURCE: FAIL (" + PROBLEMS.size() + ")");
            System.exit(1);
        }
        System.out.println("HEIGHT/HASH SINGLE-SOURCE: PASS files=" + javaFiles.size());
    }

    // ─────────────────────────────── 申报比对 ───────────────────────────────

    /** 一条申报的期望形态：允许文件 → 该文件期望次数。 */
    private static Map<String, Integer> declaration(Rule r) {
        return r.perFile;
    }

    private static Map<String, Integer> single(String file) {
        final Map<String, Integer> m = new TreeMap<>();
        m.put(file, 1);
        return m;
    }

    /**
     * 实际命中与申报比对：总数必须相等、每个文件次数必须相等、且不得出现申报外的文件。
     * 任一被破坏都单独报一行（便于 RED 演示时一眼看到是哪条申报红）。
     */
    private static void checkDeclared(String what, List<String> found, Map<String, Integer> expected) {
        final List<String> hits = found == null ? Collections.<String>emptyList() : found;
        final Map<String, Integer> actual = new TreeMap<>();
        for (final String h : hits) {
            final int colon = h.lastIndexOf(':');
            actual.merge(h.substring(0, colon), 1, Integer::sum);
        }
        final int expectedTotal = sum(expected);
        STATS.merge("decl:" + what, hits.size(), Integer::sum);
        if (hits.size() != expectedTotal) {
            fail(whatToken(what), "申报 " + expectedTotal + " 处，实得 " + hits.size() + " 处: " + hits);
            return;
        }
        if (!actual.equals(new TreeMap<>(expected))) {
            fail(whatToken(what), "命中文件与申报不符 expected=" + expected + " actual=" + actual);
            return;
        }
        for (final Map.Entry<String, Integer> e : expected.entrySet()) {
            if (e.getValue() > 0 && !hits.isEmpty() && actual.get(e.getKey()) == null) {
                fail(whatToken(what), "申报文件无命中: " + e.getKey());
            }
        }
    }

    private static String whatToken(String what) {
        final int sp = what.indexOf(' ');
        return sp < 0 ? what : what.substring(0, sp);
    }

    private static int sum(Map<String, Integer> m) {
        int s = 0;
        for (final Integer v : m.values()) {
            s += v.intValue();
        }
        return s;
    }

    private static void fail(String tag, String message) {
        STATS.merge("problem:" + tag, 1, Integer::sum);
        PROBLEMS.add("FAIL " + tag + ": " + message);
    }

    private static void bump(Map<String, List<String>> map, String key, String value) {
        map.computeIfAbsent(key, k -> new ArrayList<>())
            .add(value);
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String relativize(Path root, Path file) {
        return root.relativize(file)
            .toString()
            .replace(java.io.File.separatorChar, '/');
    }

    private static String readText(Path file) {
        try {
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 读出<b>去注释、去字符串/字符字面量内容</b>后的代码行，且<b>逐行长度与原文一致</b>
     * （行号不错位）。必要性：P3 在 javadoc 里登记差异时会<b>写出</b>常数名与旧实现形状，
     * 不剥注释会把"文档"误判成"手搓哈希"，从而逼着大家删掉最有价值的差异登记。
     */
    private static List<String> codeLines(String text) {
        final List<String> out = new ArrayList<>();
        final StringBuilder line = new StringBuilder();
        boolean inBlock = false;
        boolean inString = false;
        boolean inChar = false;
        int i = 0;
        while (i < text.length()) {
            final char c = text.charAt(i);
            final char n = i + 1 < text.length() ? text.charAt(i + 1) : '\0';
            if (c == '\n') {
                out.add(line.toString());
                line.setLength(0);
                inString = false; // 真实 Java 不允许字面量跨行；保险复位
                inChar = false;
                i++;
                continue;
            }
            if (inBlock) {
                if (c == '*' && n == '/') {
                    inBlock = false;
                    line.append("  ");
                    i += 2;
                } else {
                    line.append(' ');
                    i++;
                }
                continue;
            }
            if (c == '/' && n == '/') {
                while (i < text.length() && text.charAt(i) != '\n') {
                    line.append(' ');
                    i++;
                }
                continue;
            }
            if (c == '/' && n == '*') {
                inBlock = true;
                line.append("  ");
                i += 2;
                continue;
            }
            if (inString || inChar) {
                final char quote = inString ? '"' : '\'';
                if (c == '\\') {
                    line.append(c);
                    line.append(n == '\0' ? ' ' : n);
                    i += 2;
                    continue;
                }
                if (c == quote) {
                    if (inString) {
                        inString = false;
                    } else {
                        inChar = false;
                    }
                    line.append(c);
                    i++;
                    continue;
                }
                line.append(' ');
                i++;
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '\'') {
                inChar = true;
            }
            line.append(c);
            i++;
        }
        out.add(line.toString());
        return out;
    }
}
