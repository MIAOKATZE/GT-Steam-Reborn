import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;
import com.miaokatze.gtsr.common.dimension.framework.structure.StructureBuilder;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperityOutpostPlacer;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperityOutpostPlacer.Outpost;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityVariants;

/**
 * S-A6 单点修复验收（A4A5 报告 §9-3 缺口销号）：/gtsr structure 指令通道 String 逻辑键放置断言。
 * 纯 JEP 330 单文件运行，<b>零 Minecraft 依赖</b>（放置核心 CityVariants.place /
 * ProsperityOutpostPlacer.place 均为 String 键 + GroundFn；解析层只做源码级核验）：
 * {@code java -cp build/classes/java/main tools/dim1/StructureChannelCheck.java [repoRoot]}
 * <p>
 * <b>P12 断言完整性修复（plan §12「补 StructureChannelCheck 恒真 RecordingSink 替换」）</b>：
 * 改造前的 {@code RecordingSink.setBlock} <b>恒 return true 且无条件 accepted++</b>——
 * "每变体接受写入 &gt; 0" 对任何非空模板流都不可能在结构上变红（P0 上报，P12 确认）。
 * 现替换为 {@link ChunkWindowSink}：钳制语义复刻生产 {@code ChunkClampedSink}
 * （{@code (x>>4)==chunkX && (z>>4)==chunkZ}，另钉 y∈[0,255]），返回值是"这一格到底落没落"的
 * 真值，计数分 {@code calls / landed / clipped / badY} 三态，于是断言第一次能区分
 * <b>"根本没写"（calls==0）</b>与<b>"写了但被 chunk 边界裁光"（calls&gt;0 ∧ landed==0）</b>。
 * 两种红各自给出指名消息；GREEN 档另申报 clipped 总数（正常应 0，非 0 说明窗口选错而非放过）。
 * <p>
 * 三档运行模式：
 * <ol>
 * <li>默认（GREEN）：断言①-⑤全跑，任何 landed==0/calls==0 变红；</li>
 * <li>{@code --red-clipped}（<b>必红</b>自证）：同一模板流改投远端窗 chunk(60,60)（结构必落
 * 原点附近 ⇒ 写入全裁）⇒ 必须 FAIL 非零退出。这条 RED 就是"人为让写入落空"的反假绿证据；</li>
 * <li>{@code --legacy-tautology-demo}：同一远端窗流同时喂旧恒真 sink ⇒ 申报
 * {@code legacy accepted>0 ∧ windowed landed==0}，即<b>旧实现在同样输入下会放过</b>的机器实证
 * （demo 自身 exit 0，它钉的是"旧判据不可失败"这一事实）。</li>
 * </ol>
 * 断言面（GREEN 档）：
 * <ol>
 * <li>指令通道接线（源码级）：{@code GTSRCommand.processStructure} 的 sink 链为
 * CountingSink(CityBlockResolver(DirectWorldSink))——裸 {@code new DirectWorldSink(} 全文件恰出现 1 次；</li>
 * <li>解析表契约（源码级）：{@code CityBlockResolver} 静态表 put 键集 == 10 键白名单全集，
 * 且保留 {@code instanceof Block} 直通；</li>
 * <li>城变体流（26 变体 × 12 seed）：句柄全为 String 键、键 ∈ 白名单、<b>每变体真实落块
 * landed &gt; 0</b>（P12 修后口径，含 calls/clipped 申报）、X/Z 两键与内腔 air 键全局覆盖；</li>
 * <li>outpost 流（6 变体）：同口径；</li>
 * <li>确定性：城/outpost 各 1 例同参双跑<b>落地流</b> SHA-256 逐字节一致（P12 起摘要只覆盖
 * 被窗接受的写入，比旧"全调用流"更强：被裁掉的写入也进不了确定性证明）。</li>
 * </ol>
 */
public class StructureChannelCheck {

    /** 指令通道与解析层的源文件（相对仓库根，与 §6 命令工作目录约定一致）。 */
    private static final Path GTSR_COMMAND_SRC = Paths
        .get("src", "main", "java", "com", "miaokatze", "gtsr", "common", "commands", "GTSRCommand.java");
    private static final Path RESOLVER_SRC = Paths
        .get("src", "main", "java", "com", "miaokatze", "gtsr", "common", "dimension", "prosperity", "ruins",
            "city", "CityBlockResolver.java");

    /** 解析白名单镜像 = CityBlockResolver 静态表键全集（常量直引，漂移即编译错）。 */
    private static final Set<String> WHITELIST = new TreeSet<>();

    private static int stringHandles;
    private static int nonStringHandles;
    private static long gtBronze;
    private static long gtSteel;
    private static long airWrites;

    /** 全流（GREEN 档）落块/被裁计数申报进 PASS 行。 */
    private static long callsTotal;
    private static long landedTotal;
    private static long clippedTotal;

    /** P12 修后唯一记录型 sink：chunk 窗钳制 + 落地流摘要（复刻 ChunkClampedSink 的接受/丢弃语义）。 */
    private static final class ChunkWindowSink implements BlockSink {

        private final int chunkX;
        private final int chunkZ;
        private final MessageDigest digest;
        long calls;
        long landed;
        long clipped;
        long badY;

        ChunkWindowSink(int chunkX, int chunkZ, MessageDigest digest) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.digest = digest;
        }

        @Override
        public boolean setBlock(int x, int y, int z, Object block, int meta, int flags) {
            this.calls++;
            if ((x >> 4) != this.chunkX || (z >> 4) != this.chunkZ) {
                this.clipped++;
                return false; // 与 ChunkClampedSink 同口径：越窗丢弃，false = 这一格没变成方块
            }
            if (y < 0 || y > 255) {
                this.badY++;
                return false;
            }
            if (block instanceof String) {
                stringHandles++;
                if (CityVariants.K_GT_BRONZE.equals(block)) {
                    gtBronze++;
                }
                if (CityVariants.K_GT_STEEL.equals(block)) {
                    gtSteel++;
                }
                if (CityVariants.K_AIR.equals(block)) {
                    airWrites++;
                }
            } else {
                nonStringHandles++;
            }
            this.digest.update((x + "," + y + "," + z + ":" + block + ":" + meta + ";").getBytes());
            this.landed++;
            return true;
        }
    }

    /**
     * 旧恒真 sink 的<b>原样复刻</b>（setBlock 无钳制、恒 return true、accepted 随 calls 线性增长）。
     * 只被 {@code --legacy-tautology-demo} 使用，用途是机器实证"旧判据在同样输入下必然放过"；
     * 除 demo 外全文件无任何引用——GREEN 档不可能误用它。
     */
    private static final class LegacyAlwaysTrueSink implements BlockSink {

        long accepted;

        @Override
        public boolean setBlock(int x, int y, int z, Object block, int meta, int flags) {
            this.accepted++;
            return true;
        }
    }

    public static void main(String[] args) throws Exception {
        WHITELIST.add(CityVariants.K_CASING);
        WHITELIST.add(CityVariants.K_DEBRIS);
        WHITELIST.add(CityVariants.K_SURFACE);
        WHITELIST.add(CityVariants.K_STONE);
        WHITELIST.add(CityVariants.K_COBBLE);
        WHITELIST.add(CityVariants.K_GRAVEL);
        WHITELIST.add(CityVariants.K_BARS);
        WHITELIST.add(CityVariants.K_AIR);
        WHITELIST.add(CityVariants.K_GT_BRONZE);
        WHITELIST.add(CityVariants.K_GT_STEEL);

        String mode = "green";
        Path srcRoot = Paths.get(".");
        for (final String a : args) {
            if (a.startsWith("--")) {
                mode = a;
            } else {
                srcRoot = Paths.get(a);
            }
        }

        if ("--red-clipped".equals(mode)) {
            // 反假绿档：写入流一切照旧，但 sink 的窗投到 (60,60)（原点附近必有 calls ⇒ landed 必 0）
            System.out.println("STRUCTURECHANNEL RED-MODE(clipped-window): expect a named FAIL below, exit must be non-zero");
            checkCityStreams(60, 60);
            checkOutpostStreams(60, 60);
            System.out.println("STRUCTURECHANNEL RED-MODE UNEXPECTED: nothing was clipped-red (this line must never print)");
            System.exit(1);
            return;
        }
        if ("--legacy-tautology-demo".equals(mode)) {
            legacyTautologyDemo();
            return;
        }

        checkWiring(srcRoot.resolve(GTSR_COMMAND_SRC), srcRoot.resolve(RESOLVER_SRC));
        checkCityStreams(0, 0);
        checkOutpostStreams(0, 0);

        if (stringHandles == 0) {
            fail("no String-keyed handle observed (channel content cannot be the resolver's subject)");
        }
        if (nonStringHandles != 0) {
            fail("unexpected non-String handle from offline placers: " + nonStringHandles);
        }
        if (gtBronze == 0 || gtSteel == 0) {
            fail("GT5U keys not reachable via template streams (bronze=" + gtBronze + " steel=" + gtSteel + ")");
        }
        if (airWrites == 0) {
            fail("cavity-clearing air key never emitted");
        }
        if (clippedTotal != 0 || callsTotal != landedTotal) {
            fail("GREEN window must cover the streams but clipped=" + clippedTotal + " calls=" + callsTotal
                + " landed=" + landedTotal);
        }
        System.out.println(
            "STRUCTURECHANNEL PASS: wiring=resolver-wrapped resolverKeys=" + WHITELIST.size() + " cityVariants="
                + CityVariants.ALL.length
                + " outposts=" + ProsperityOutpostPlacer.ALL.length + " stringHandles=" + stringHandles
                + " gtBronze=" + gtBronze + " gtSteel=" + gtSteel + " air=" + airWrites
                + " calls=" + callsTotal + " landed=" + landedTotal + " clipped=" + clippedTotal);
    }

    /** 断言①②：指令链接线 + 解析表键集契约（源码级）。 */
    private static void checkWiring(Path commandSrc, Path resolverSrc) throws Exception {
        final String command = new String(Files.readAllBytes(commandSrc), "UTF-8");
        if (!command.contains("new CountingSink(new CityBlockResolver(new DirectWorldSink(")) {
            fail("GTSRCommand structure channel is not wired as CountingSink(CityBlockResolver(DirectWorldSink))");
        }
        if (countOccurrences(command, "new DirectWorldSink(") != 1) {
            fail("GTSRCommand contains a second (possibly bare) DirectWorldSink path");
        }
        final String resolver = new String(Files.readAllBytes(resolverSrc), "UTF-8");
        final Set<String> putKeys = new TreeSet<>();
        int idx = 0;
        while ((idx = resolver.indexOf("resolver.put(CityVariants.", idx)) >= 0) {
            final int start = idx + "resolver.put(CityVariants.".length();
            final int end = resolver.indexOf(',', start);
            if (end < 0) {
                fail("CityBlockResolver: malformed resolver.put entry at offset " + idx);
            }
            putKeys.add(resolver.substring(start, end).trim());
            idx = start;
        }
        final Set<String> expected = new TreeSet<>();
        for (final String key : WHITELIST) {
            // K_ 常量名反查（编译期常量内联不影响源码 token 核对）
            expected.add(constantNameOf(key));
        }
        if (!putKeys.equals(expected)) {
            fail("CityBlockResolver key table mismatch: put=" + putKeys + " expected=" + expected);
        }
        if (!resolver.contains("if (block instanceof Block)")) {
            fail("CityBlockResolver lost the Block-instance passthrough");
        }
    }

    /** 断言③：26 城变体放置流<b>真实落块</b> + 记录流确定性（窗参数由 mode 决定）。 */
    private static void checkCityStreams(int winChunkX, int winChunkZ) throws Exception {
        final CityVariants.GroundFn flat = (x, z) -> 40;
        for (final CityVariants.Variant v : CityVariants.ALL) {
            final ChunkWindowSink recorder = sink(winChunkX, winChunkZ);
            for (int s = 0; s < 12; s++) {
                CityVariants.place(v, recorder, 0, 0, flat, 0x51C17L + s, BlockSink.FLAG_DIRECT);
            }
            tally(recorder);
            assertLanded(v.name, recorder);
            digestCheck(recorder.digest, "city variant " + v.name, () -> {
                final ChunkWindowSink again = sink(winChunkX, winChunkZ);
                for (int s = 0; s < 12; s++) {
                    CityVariants.place(v, again, 0, 0, flat, 0x51C17L + s, BlockSink.FLAG_DIRECT);
                }
                return again.digest.digest();
            });
        }
    }

    /** 断言④：6 outpost 放置流真实落块（自包 resolver 双层包裹幂等口径）。 */
    private static void checkOutpostStreams(int winChunkX, int winChunkZ) throws Exception {
        final CityVariants.GroundFn synthetic = (x, z) -> 40 + ((x * 7 + z * 13) % 9);
        boolean first = true;
        for (final Outpost outpost : ProsperityOutpostPlacer.ALL) {
            final ChunkWindowSink recorder = sink(winChunkX, winChunkZ);
            final StructureBuilder builder = new StructureBuilder(recorder);
            for (int s = 0; s < 4; s++) {
                ProsperityOutpostPlacer
                    .place(builder, outpost, 0, 0, s % 4, CityVariants.MISSING_RATES[s % 3],
                        new Random(0x4F557450L + s), synthetic, BlockSink.FLAG_DIRECT);
            }
            tally(recorder);
            assertLanded("outpost " + outpost.name, recorder);
            if (first) {
                first = false;
                digestCheck(recorder.digest, "outpost " + outpost.name, () -> {
                    final ChunkWindowSink again = sink(winChunkX, winChunkZ);
                    final StructureBuilder builder2 = new StructureBuilder(again);
                    for (int s = 0; s < 4; s++) {
                        ProsperityOutpostPlacer
                            .place(builder2, outpost, 0, 0, s % 4, CityVariants.MISSING_RATES[s % 3],
                                new Random(0x4F557450L + s), synthetic, BlockSink.FLAG_DIRECT);
                    }
                    return again.digest.digest();
                });
            }
        }
    }

    private static ChunkWindowSink sink(int chunkX, int chunkZ) throws Exception {
        return new ChunkWindowSink(chunkX, chunkZ, MessageDigest.getInstance("SHA-256"));
    }

    private static void tally(ChunkWindowSink s) {
        callsTotal += s.calls;
        landedTotal += s.landed;
        clippedTotal += s.clipped;
    }

    /** P12 修后的核心判据：区分"根本没写"与"写了但被 chunk 边界裁光"，两者各报各的红。 */
    private static void assertLanded(String label, ChunkWindowSink s) {
        if (s.calls == 0) {
            fail(label + ": placer wrote NOTHING at all (calls=0 landed=0) — channel stream is empty");
        }
        if (s.landed == 0) {
            fail(label + ": wrote but ALL clipped by chunk window (calls=" + s.calls + " landed=0 clipped=" + s.clipped
                + " badY=" + s.badY + ") — 'accept writes > 0' is NOT satisfied by mere emit calls");
        }
    }

    /**
     * 反假绿对照（P12 判据 4）：同一"全裁"场景同时喂旧恒真 sink——旧 accepted 计数照样 &gt; 0，
     * 证明旧实现的"接受写入&gt;0"在写入全部落空时也会绿；新窗 sink 的 landed==0 则必红。
     * 本 demo 自身 PASS（它钉的是旧缺陷不可失败这一事实），GREEN 档不经过这里。
     */
    private static void legacyTautologyDemo() throws Exception {
        final CityVariants.GroundFn flat = (x, z) -> 40;
        final StringBuilder detail = new StringBuilder();
        long legacyCalls = 0;
        for (final CityVariants.Variant v : CityVariants.ALL) {
            final ChunkWindowSink windowed = sink(60, 60); // 远端窗：结构必落原点附近 ⇒ 全裁
            final LegacyAlwaysTrueSink legacy = new LegacyAlwaysTrueSink();
            final BlockSink twin = (x, y, z, block, meta, flags) -> {
                legacy.setBlock(x, y, z, block, meta, flags);
                return windowed.setBlock(x, y, z, block, meta, flags);
            };
            for (int s = 0; s < 12; s++) {
                CityVariants.place(v, twin, 0, 0, flat, 0x51C17L + s, BlockSink.FLAG_DIRECT);
            }
            if (windowed.calls == 0 || windowed.landed != 0 || windowed.clipped == 0) {
                fail("demo premise broken on " + v.name + ": calls=" + windowed.calls + " landed=" + windowed.landed
                    + " clipped=" + windowed.clipped);
            }
            if (legacy.accepted == 0) {
                fail("legacy sink accepted==0 — it is no longer the always-true counter being refuted");
            }
            legacyCalls += legacy.accepted;
            if (detail.length() == 0) {
                detail.append(v.name)
                    .append(": legacy accepted=")
                    .append(legacy.accepted)
                    .append(" vs windowed landed=0");
            }
        }
        System.out.println("STRUCTURECHANNEL LEGACY-TAUTONOMY CONFIRMED: variants=" + CityVariants.ALL.length
            + " legacyAcceptedTotal=" + legacyCalls + " (>0 ⇒ 旧恒真判据对同一全裁流必绿)"
            + " windowedLandedTotal=0 (⇒ 新判据必红) sample[" + detail + "]");
    }

    /** 断言⑤：记录流双跑 SHA-256 一致（首轮摘要 vs 复跑摘要）。 */
    private static void digestCheck(final MessageDigest firstDigest, String label, StreamSource rerun)
        throws Exception {
        final byte[] first = firstDigest.digest();
        if (!MessageDigest.isEqual(first, rerun.get())) {
            fail(label + ": double-run stream digest mismatch");
        }
    }

    private interface StreamSource {

        byte[] get() throws Exception;
    }

    /** 键 → K_ 常量名（源码 token 核对用；调用方以常量登记，此处仅字符串映射）。 */
    private static String constantNameOf(String key) {
        switch (key) {
            case "gtsr:RuinedCasing":
                return "K_CASING";
            case "gtsr:RuinDebris":
                return "K_DEBRIS";
            case "gtsr:ProsperitySurface":
                return "K_SURFACE";
            case "minecraft:stone":
                return "K_STONE";
            case "minecraft:cobblestone":
                return "K_COBBLE";
            case "minecraft:gravel":
                return "K_GRAVEL";
            case "minecraft:iron_bars":
                return "K_BARS";
            case "minecraft:air":
                return "K_AIR";
            case "gt5u:CasingBronzePlated":
                return "K_GT_BRONZE";
            case "gt5u:CasingSolidSteel":
                return "K_GT_STEEL";
            default:
                return fail("whitelist key has no constant name: " + key);
        }
    }

    private static int countOccurrences(String text, String token) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(token, idx)) >= 0) {
            count++;
            idx += token.length();
        }
        return count;
    }

    private static String fail(String message) {
        System.out.println("STRUCTURECHANNEL FAIL: " + message);
        System.exit(1);
        return null;
    }
}
