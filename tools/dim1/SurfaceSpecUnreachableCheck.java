import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <b>P13b U4（G-2 尾巴）：L3 表层 null 回退分支的"生产不可达"源级断言</b>。
 * <p>
 * 钉住的事实（{@code framework/GTSRChunkProviderBase}）：{@code surfaceSpec()} 默认实现返回
 * {@code null}，{@code replaceBlocksForBiome} 据此走 {@code applyVanillaBiomeTerrain}
 * （原版 {@code genTerrainBlocks} 回退链）。该分支<b>刻意保留不删</b>（S1 模板契约 + 离线
 * harness 驱动缝），但它当前在生产路径<b>不可达</b>且此前<b>零断言覆盖</b>——本工具补上这条钉：
 * "两个 provider 都覆写了 {@code surfaceSpec()} 且返回非 null" 一旦被破坏（某 provider 不再覆写 /
 * 覆写成 {@code return null} / 新增第三个 provider 未覆写），本断言立即变红。
 * <p>
 * ═══ <b>列名申报</b>（唯一预期表；改期望必须连 plan 一起改，不允许默默放宽）═══
 * <pre>
 *  FB-DEFAULT       framework 的 {@code SurfaceSpec surfaceSpec()} 定义恰 1 处，且方法体
 *                   {@code return null;}（回退分支在场——断言的对象本身还在）
 *  FB-NULLBRANCH    framework 的 {@code if (spec == null)} 恰 1 处
 *  FB-VANILLA       framework 的 {@code void applyVanillaBiomeTerrain(} 定义恰 1 处，且
 *                   {@code applyVanillaBiomeTerrain(chunkX...} 调用恰 1 处（缝未断线）
 *  SUBCLASS-ROSTER  全 srcRoot {@code extends GTSRChunkProviderBase} 命中文件数恰 2
 *                   （prosperity/shattered 两 provider；第 3 个 provider 一出现即红，
 *                   逼它对"覆写 or 走回退"做显式裁决）
 *  SPEC-OVERRIDE    每个子类文件：{@code surfaceSpec()} 覆写定义恰 1 处、方法体含
 *                   {@code return <非 null 表达式>;}（{@code return null;} 即红）、且
 *                   {@code new GTSRChunkProviderBase.SurfaceSpec(} 恰 1 处（spec() 分配点在场）
 * </pre>
 * <p>
 * <b>用法</b>：{@code java SurfaceSpecUnreachableCheck [srcRoot=src/main/java]}——纯 JDK、
 * 零 MC 依赖、只读源码。{@code srcRoot} 指向影子树即可演示单变量破坏变红
 * （GREEN/RED 两步见 {@code tools/dim1/surface_checks.sh} 的 [2i] 与 [19c]）。
 * 退出码：0 = 全绿；1 = 有申报项被破坏（逐条打 {@code FAIL <标记>}）；2 = 源码根不存在。
 */
public final class SurfaceSpecUnreachableCheck {

    private static final String FRAMEWORK =
        "com/miaokatze/gtsr/common/dimension/framework/GTSRChunkProviderBase.java";
    private static final Pattern DEF =
        Pattern.compile("\\b(?:GTSRChunkProviderBase\\.)?SurfaceSpec\\s+surfaceSpec\\s*\\(\\s*\\)");
    private static final Pattern EXTENDS = Pattern.compile("\\bextends\\s+GTSRChunkProviderBase\\b");
    private static final Pattern SPEC_NEW =
        Pattern.compile("new\\s+GTSRChunkProviderBase\\.SurfaceSpec\\s*\\(");

    private static final List<String> PROBLEMS = new ArrayList<>();
    private static int assertions = 0;

    private SurfaceSpecUnreachableCheck() {}

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

        final List<String> subclassFiles = new ArrayList<>();
        boolean frameworkSeen = false;
        for (final Path f : javaFiles) {
            final String rel = relativize(root, f);
            final List<String> code = codeLines(readText(f));
            if (rel.endsWith(FRAMEWORK)) {
                frameworkSeen = true;
                checkFramework(code);
            }
            boolean extendsBase = false;
            for (final String line : code) {
                if (EXTENDS.matcher(line)
                    .find()) {
                    extendsBase = true;
                    break;
                }
            }
            if (extendsBase) {
                subclassFiles.add(rel);
                checkOverride(rel, code);
            }
        }
        if (!frameworkSeen) {
            fail("FB-DEFAULT", "框架文件缺失: " + FRAMEWORK);
        }
        // SUBCLASS-ROSTER：恰 2 个生产 provider（申报见类注释表）
        verify("SUBCLASS-ROSTER", subclassFiles.size() == 2,
            "extends GTSRChunkProviderBase 命中 " + subclassFiles.size() + " 个文件（申报 2）: " + subclassFiles);

        System.out.println("CHECKS assertions=" + assertions + " problems=" + PROBLEMS.size()
            + " filesScanned=" + javaFiles.size());
        for (final String p : PROBLEMS) {
            System.out.println("  " + p);
        }
        if (!PROBLEMS.isEmpty()) {
            System.out.println("SURFACE-SPEC-UNREACHABLE: FAIL (" + PROBLEMS.size() + ")");
            System.exit(1);
        }
        System.out.println("SURFACE-SPEC-UNREACHABLE: PASS assertions=" + assertions
            + "（null 回退分支在场但被两覆写钉为生产不可达）");
    }

    /** framework 侧：回退分支在场（断言对象本身没被顺手删/改）。 */
    private static void checkFramework(List<String> code) {
        verify("FB-DEFAULT", countDefsWithNullReturn(code) == 1, "surfaceSpec() 默认 return null 定义应恰 1 处");
        verify("FB-NULLBRANCH", count(code, Pattern.compile("\\bif\\s*\\(\\s*spec\\s*==\\s*null\\s*\\)")) == 1,
            "if (spec == null) 分派应恰 1 处");
        verify("FB-VANILLA",
            count(code, Pattern.compile("\\bvoid\\s+applyVanillaBiomeTerrain\\s*\\(")) == 1
                && count(code, Pattern.compile("\\bapplyVanillaBiomeTerrain\\s*\\(\\s*chunkX")) == 1,
            "applyVanillaBiomeTerrain 定义/调用应各 1 处（回退缝未断线）");
    }

    /** 每个子类文件：覆写在场且返回非 null，spec() 分配点在场。 */
    private static void checkOverride(String rel, List<String> code) {
        final List<Integer> defs = defLines(code);
        verify("SPEC-OVERRIDE " + rel, defs.size() == 1, "surfaceSpec() 覆写定义应恰 1 处，实得 " + defs.size()
            + "（不再覆写 ⇒ 生产将走 null 回退分支 ⇒ 必须显式裁决）");
        if (defs.size() != 1) {
            return;
        }
        final String body = bodyOf(code, defs.get(0));
        verify("SPEC-OVERRIDE " + rel + " BODY", !body.contains("return null"), "覆写体 return null ⇒ 等价未覆写");
        verify("SPEC-OVERRIDE " + rel + " SPECNEW", count(code, SPEC_NEW) == 1,
            "new GTSRChunkProviderBase.SurfaceSpec( 分配点应恰 1 处");
    }

    // ─────────────────────────────── 计数原语 ───────────────────────────────

    private static List<Integer> defLines(List<String> code) {
        final List<Integer> out = new ArrayList<>();
        for (int i = 0; i < code.size(); i++) {
            if (DEF.matcher(code.get(i))
                .find()) {
                out.add(i);
            }
        }
        return out;
    }

    /** 定义行 → 数出「方法体首个 return 是 return null」的定义个数。 */
    private static int countDefsWithNullReturn(List<String> code) {
        int n = 0;
        for (final int i : defLines(code)) {
            if (bodyOf(code, i)
                .contains("return null")) {
                n++;
            }
        }
        return n;
    }

    /** 从定义行起收集到方法体（到首个配对收尾的大括号深度 0 为止；本仓这两个实现都是单行 return）。 */
    private static String bodyOf(List<String> code, int defLine) {
        final StringBuilder sb = new StringBuilder();
        int depth = 0;
        boolean opened = false;
        for (int i = defLine; i < code.size() && i < defLine + 12; i++) {
            final String line = code.get(i);
            sb.append(line);
            for (int c = 0; c < line.length(); c++) {
                final char ch = line.charAt(c);
                if (ch == '{') {
                    depth++;
                    opened = true;
                } else if (ch == '}') {
                    depth--;
                }
            }
            if (opened && depth <= 0) {
                break;
            }
        }
        return sb.toString();
    }

    private static int count(List<String> code, Pattern p) {
        int n = 0;
        for (final String line : code) {
            if (p.matcher(line)
                .find()) {
                n++;
            }
        }
        return n;
    }

    private static void verify(String tag, boolean ok, String detail) {
        assertions++;
        if (!ok) {
            fail(tag, detail);
        }
    }

    private static void fail(String tag, String message) {
        PROBLEMS.add("FAIL " + tag + ": " + message);
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
     * 去注释、去字符串/字符字面量内容、逐行长度与原文一致（行号不错位）——与
     * {@code HeightHashSingleSourceCheck.codeLines} 同一纪律：javadoc 里"写出"被钉的符号名
     * （如本次 U4 的注释本身）不得被当成代码命中。
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
                inString = false;
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
