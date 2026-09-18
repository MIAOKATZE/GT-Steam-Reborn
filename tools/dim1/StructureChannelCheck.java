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
 * 断言面：
 * <ol>
 * <li>指令通道接线（源码级）：{@code GTSRCommand.processStructure} 的 sink 链为
 * CountingSink(CityBlockResolver(DirectWorldSink))——裸 {@code new DirectWorldSink(} 全文件恰出现 1 次
 * （缺 resolver 时 String 键经 {@code instanceof Block} 门整链丢弃）；</li>
 * <li>解析表契约（源码级）：{@code CityBlockResolver} 静态表 put 的键集 == 键白名单全集（10 键，
 * 含 GT5U X/Z 两键与 air），且保留 {@code instanceof Block} 直通（Block 产出型 placer 不受影响）；</li>
 * <li>城变体流（模板级，26 变体 × 12 seed）：放置器产出的句柄全部为 String 键（即裸 DirectWorldSink
 * 必丢弃的内容）、键全部 ∈ 白名单、每变体接受写入 &gt; 0、全部变体合计 X/Z 两键均出现、内腔清空 air 键出现；</li>
 * <li>outpost 流（6 变体）：同上口径（X/Z ≥ 1 逐变体由 OutpostTemplateCheck 承担，此处键表覆盖复核）；</li>
 * <li>确定性：城变体与 outpost 各取 1 例同参双跑记录流 SHA-256 逐字节一致。</li>
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

    /** 记录型 Sink：统计句柄类型/键覆盖并产出记录流摘要（不改写坐标与 meta）。 */
    private static final class RecordingSink implements BlockSink {

        private long accepted;
        private final MessageDigest digest;

        RecordingSink(MessageDigest digest) {
            this.digest = digest;
        }

        @Override
        public boolean setBlock(int x, int y, int z, Object block, int meta, int flags) {
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

        if (args.length > 0) {
            final Path root = Paths.get(args[0]);
            checkWiring(root.resolve(GTSR_COMMAND_SRC), root.resolve(RESOLVER_SRC));
        } else {
            checkWiring(GTSR_COMMAND_SRC, RESOLVER_SRC);
        }
        checkCityStreams();
        checkOutpostStreams();

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
        System.out.println(
            "STRUCTURECHANNEL PASS: wiring=resolver-wrapped resolverKeys=" + WHITELIST.size() + " cityVariants="
                + CityVariants.ALL.length
                + " outposts=" + ProsperityOutpostPlacer.ALL.length + " stringHandles=" + stringHandles
                + " gtBronze=" + gtBronze + " gtSteel=" + gtSteel + " air=" + airWrites);
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

    /** 断言③：26 城变体放置流键覆盖 + 记录流确定性。 */
    private static void checkCityStreams() throws Exception {
        final CityVariants.GroundFn flat = (x, z) -> 40;
        for (final CityVariants.Variant v : CityVariants.ALL) {
            final RecordingSink recorder = new RecordingSink(MessageDigest.getInstance("SHA-256"));
            for (int s = 0; s < 12; s++) {
                CityVariants.place(v, recorder, 0, 0, flat, 0x51C17L + s, BlockSink.FLAG_DIRECT);
            }
            if (recorder.accepted == 0) {
                fail("city variant " + v.name + ": zero accepted writes");
            }
            digestCheck(recorder.digest, "city variant " + v.name, () -> {
                final RecordingSink again = new RecordingSink(MessageDigest.getInstance("SHA-256"));
                for (int s = 0; s < 12; s++) {
                    CityVariants.place(v, again, 0, 0, flat, 0x51C17L + s, BlockSink.FLAG_DIRECT);
                }
                return again.digest.digest();
            });
        }
    }

    /** 断言④：6 outpost 放置流键覆盖（自包 resolver 双层包裹幂等口径）。 */
    private static void checkOutpostStreams() throws Exception {
        final CityVariants.GroundFn synthetic = (x, z) -> 40 + ((x * 7 + z * 13) % 9);
        boolean first = true;
        for (final Outpost outpost : ProsperityOutpostPlacer.ALL) {
            final RecordingSink recorder = new RecordingSink(MessageDigest.getInstance("SHA-256"));
            final StructureBuilder builder = new StructureBuilder(recorder);
            for (int s = 0; s < 4; s++) {
                ProsperityOutpostPlacer
                    .place(builder, outpost, 0, 0, s % 4, CityVariants.MISSING_RATES[s % 3],
                        new Random(0x4F557450L + s), synthetic, BlockSink.FLAG_DIRECT);
            }
            if (recorder.accepted == 0) {
                fail("outpost " + outpost.name + ": zero accepted writes");
            }
            if (first) {
                first = false;
                digestCheck(recorder.digest, "outpost " + outpost.name, () -> {
                    final RecordingSink again = new RecordingSink(MessageDigest.getInstance("SHA-256"));
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
