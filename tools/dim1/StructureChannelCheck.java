import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;
import com.miaokatze.gtsr.common.dimension.framework.structure.ChunkSpans;
import com.miaokatze.gtsr.common.dimension.framework.structure.StructureBuilder;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperityOutpostPlacer;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityBlockResolver;
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
 * <b>P16-B6 窗口口径随申报跨度派生（改这条的原因与它没被放宽的证据）</b>：P12 起 GREEN 档钉
 * {@code clipped == 0}，当时的窗是<b>单个</b> chunk(0,0)——那时全名册最大边长 16，一结构必落一 chunk。
 * P16-B3 的城内巨构（{@code great_forge} 24×24 / {@code titan_gearworks} 20×20）按设计就是横跨 2×2 chunk，
 * 于是实测 {@code clipped=24361}（归因见下）。这一红<b>不是</b>形态缺陷：把该 24361 次写入逐格定位后，
 * 它们全部来自这两个巨构、全部落在 {@code chunk(+0,+1)/(+1,+0)/(+1,+1)} 三个<b>邻槽</b>，
 * 且没有任何一格出申报框（26 个旧城变体与 6 个 outpost 裁掉数逐档为 0）。生产侧这些写由
 * {@code ChunkSliceSink} 交给邻 chunk 自己的 populate（城外跨片巨构同一套协议，切片器全仓一份），
 * "分片 ≤16×16×12 + 总 bbox = 分片实心并集"的成对断言在 {@code OutpostTemplateCheck}，B1 态本就是绿的。
 * <p>
 * 修法是<b>把窗随申报边长派生</b>而不是放宽判据：每条流按 {@code max(sizeX,sizeZ)} 经
 * {@link ChunkSpans#chunksAcross(int)} 得到 {@code across²} 个窗（16→1 个、20/24→4 个），
 * 每个窗仍是原来那个 {@link ChunkWindowSink}（钳制语义与生产 {@code ChunkClampedSink} 同形）。
 * GREEN 档从此同时钉四件事，任一失守各自指名变红：① <b>写必须留在申报框内</b>
 * （{@code [origin, origin+maxSide-1]}，旋转/偏移甩出框即红——正是 B3 自查的那类"rot180 甩 1 格进街带"）；
 * ② <b>申报框内不得有写接不住</b>（{@code clipped == 0}，等价于"实得跨度不超过申报跨度"⇒ 少报多写必红）；
 * ③ <b>每个申报窗都必须真有内容</b>（每窗 {@code landed > 0} ⇒ "申了 2×2 却只有原点那片有块"必红，
 * 与 {@code ChunkSpans.distinctChunkOffsets} 同一反空片口径）；④ 原有的 {@code calls == landed}
 * 与逐档落块真值。旧口径下 ①③ 完全无处可红（巨构的邻槽写被静默裁掉，落块真值与确定性摘要只覆盖
 * 一座的 1/4）；改后摘要覆盖<b>全部</b>被窗接受的写入，断言⑤的确定性面因此变强而非变弱。
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
 * <li>解析表契约（源码级）：{@code CityBlockResolver} 静态表 put 键集 == 白名单全集（10 键 S-A4 红线 + P16-B1 巨构两键 = 12 键），
 * 且保留 {@code instanceof Block} 直通；</li>
 * <li>城变体流（{@code CityVariants.ALL.length} 变体 × 12 seed，含 2 个城内巨构）：句柄全为 String 键、
 * 键 ∈ 白名单、<b>每变体真实落块 landed &gt; 0</b>（P12 修后口径，含 calls/clipped 申报）、
 * <b>P16-B6 另钉申报框内含 / 窗数随跨度 / 每窗有内容</b>、X/Z 两键与内腔 air 键全局覆盖；</li>
 * <li>outpost 流（6 变体）：同口径（边长 ≤16 ⇒ 窗数派生为 1，与 P12 态逐字同形）；</li>
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

    /** P16-B6：窗数与"需要多窗"的流数，申报进 PASS 行（读数可见 ⇒ 口径不是摆设）。 */
    private static long windowsTotal;
    private static int multiWindowStreams;
    private static int streamCount;

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

        /**
         * 本窗是否负责这一格。与 {@code ChunkClampedSink}/{@link ChunkWindowSink#setBlock} 的钳制判据
         * <b>同一式</b>（也即生产 {@code ChunkSliceSink.owns} 的那一式）；P16-B6 的申报窗组靠它路由，
         * 于是"谁接这一格"在全文件只有一处定义。
         */
        boolean owns(int x, int z) {
            return (x >> 4) == this.chunkX && (z >> 4) == this.chunkZ;
        }

        @Override
        public boolean setBlock(int x, int y, int z, Object block, int meta, int flags) {
            this.calls++;
            if (!owns(x, z)) {
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
     * P16-B6：<b>一条结构流 = 一组 chunk 窗</b>，窗数由该结构的<b>申报边长</b>经
     * {@link ChunkSpans#chunksAcross(int)} 派生（边长 ≤16 ⇒ 1 个窗，与 P12 态逐字同形；
     * 20/24 ⇒ 2×2 = 4 个窗）。每个窗仍是 {@link ChunkWindowSink} 本体，共享<b>同一份</b>摘要对象
     * ⇒ 摘要覆盖"被各自窗接受的全部写入"且顺序就是发射顺序（比旧的单窗口径更强）。
     * <p>
     * 路由规则：一笔写只交给<b>唯一</b> owning 窗；没有任何窗 own 它 ⇒ 计入本组的 {@code clipped}
     * （= 写出了申报跨度）。这里刻意不做"多窗依次尝试"，因为那会把同一格算进多个窗的计数。
     */
    private static final class SpanWindows implements BlockSink {

        private final ChunkWindowSink[] windows;
        private final int baseChunkX;
        private final int baseChunkZ;
        private final int across;
        private final int reach;
        private final int originX;
        private final int originZ;
        private final MessageDigest digest;
        long calls;
        long landed;
        long clipped;
        private int minCallX = Integer.MAX_VALUE;
        private int maxCallX = Integer.MIN_VALUE;
        private int minCallZ = Integer.MAX_VALUE;
        private int maxCallZ = Integer.MIN_VALUE;

        SpanWindows(int winChunkX, int winChunkZ, int reach, int originX, int originZ) throws Exception {
            this.reach = reach;
            this.originX = originX;
            this.originZ = originZ;
            this.baseChunkX = winChunkX;
            this.baseChunkZ = winChunkZ;
            this.across = ChunkSpans.chunksAcross(reach);
            this.windows = new ChunkWindowSink[this.across * this.across];
            this.digest = MessageDigest.getInstance("SHA-256");
            int n = 0;
            for (int dx = 0; dx < this.across; dx++) {
                for (int dz = 0; dz < this.across; dz++) {
                    this.windows[n++] = new ChunkWindowSink(winChunkX + dx, winChunkZ + dz, this.digest);
                }
            }
        }

        /** 申报框应覆盖的 chunk 数（申报口径，与 {@code RuinedColossusShapes.declaredChunksX()} 同语义）。 */
        int declaredChunks() {
            return this.across * this.across;
        }

        /** 逐窗落块真值（判据 ③ 用）：任一申报窗为空 ⇒ "申了跨片但没真跨"。 */
        long landedInWindow(int i) {
            return this.windows[i].landed;
        }

        /** 全部被接受的写入之和 == {@link #landed}（自洽核对，防路由把一格送进两个窗；纯读无副作用）。 */
        long sumWindowLanded() {
            long s = 0;
            for (final ChunkWindowSink w : this.windows) {
                s += w.landed;
            }
            return s;
        }

        /** 逐窗 y 越界之和（y 域不在申报框判据内，单独申报；纯读，避免累加字段被调用两次翻倍）。 */
        long sumWindowBadY() {
            long s = 0;
            for (final ChunkWindowSink w : this.windows) {
                s += w.badY;
            }
            return s;
        }

        @Override
        public boolean setBlock(int x, int y, int z, Object block, int meta, int flags) {
            this.calls++;
            if (x < this.minCallX) {
                this.minCallX = x;
            }
            if (x > this.maxCallX) {
                this.maxCallX = x;
            }
            if (z < this.minCallZ) {
                this.minCallZ = z;
            }
            if (z > this.maxCallZ) {
                this.maxCallZ = z;
            }
            final ChunkWindowSink owner = this.windowOwning(x, z);
            if (owner == null) {
                this.clipped++;
                return false;
            }
            if (owner.setBlock(x, y, z, block, meta, flags)) {
                this.landed++;
                return true;
            }
            return false; // 只有 y 越界会走到这里（badY 由 owning 窗自己记）
        }

        /** 申报跨度内的那一格窗；不在跨度内返回 null（{@code >>4} 与生产同一 floor 语义）。 */
        private ChunkWindowSink windowOwning(int x, int z) {
            final int dx = ChunkSpans.chunkOf(x) - this.baseChunkX;
            final int dz = ChunkSpans.chunkOf(z) - this.baseChunkZ;
            if (dx < 0 || dx >= this.across || dz < 0 || dz >= this.across) {
                return null;
            }
            final ChunkWindowSink w = this.windows[dx * this.across + dz];
            return w.owns(x, z) ? w : null;
        }

        /** 申报框（闭区间）——判据 ① 的对照量，来自名册的申报边长而非实测极值。 */
        boolean callsInsideDeclaredBox() {
            return this.calls > 0 && this.minCallX >= this.originX
                && this.maxCallX <= this.originX + this.reach - 1
                && this.minCallZ >= this.originZ
                && this.maxCallZ <= this.originZ + this.reach - 1;
        }

        String boxReadout() {
            return "x[" + this.minCallX + "," + this.maxCallX + "] z[" + this.minCallZ + "," + this.maxCallZ
                + "] 申报=" + this.reach;
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
        // P16-B1（城外跨 chunk 巨构的红线扩档；两键的允许 meta 与禁面见字段注释 + RuinFamilyCheck COLOSSUS 组）
        WHITELIST.add(CityBlockResolver.K_GT_FIREBOX);
        WHITELIST.add(CityBlockResolver.K_GT_GLASS);

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
            // 反假绿档：写入流一切照旧，但<b>整组</b>窗（P16-B6 后随申报跨度派生，仍是 1/4 扇）投到
            // chunk(60,60) 起（原点附近必有 calls ⇒ 每一扇窗的 landed 都必 0）
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
        // 判据原文一字未动（P12 的"零容忍被裁 + calls==landed"）；变的只是"窗"从 1 扇随申报跨度派生成
        // 一组 ⇒ clipped 的语义从"落在单 chunk 窗外"收紧为"落在<b>申报跨度</b>外"。逐流红在 checkStream
        // 里先指名（哪一条流、哪个申报框、第几扇空窗），这里只兜"全局算术不自洽"。
        if (clippedTotal != 0 || callsTotal != landedTotal) {
            fail("GREEN window must cover the streams but clipped=" + clippedTotal + " calls=" + callsTotal
                + " landed=" + landedTotal);
        }
        System.out.println(
            "STRUCTURECHANNEL PASS: wiring=resolver-wrapped resolverKeys=" + WHITELIST.size() + " cityVariants="
                + CityVariants.ALL.length
                + " outposts=" + ProsperityOutpostPlacer.ALL.length + " stringHandles=" + stringHandles
                + " gtBronze=" + gtBronze + " gtSteel=" + gtSteel + " air=" + airWrites
                + " calls=" + callsTotal + " landed=" + landedTotal + " clipped=" + clippedTotal
                + " streams=" + streamCount + " windows=" + windowsTotal + " spanStreams=" + multiWindowStreams);
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
        // P16-B1 口径同步：扫描不再钉死 "CityVariants." 前缀——燃烧室/防爆玻璃两枚新键的常量
        // 就住在 CityBlockResolver 自己身上（键表与红线同源），所以这里只认 resolver.put( 这个
        // 唯一形态，再取最后一个 '.' 之后的常量名。少一处前缀假设，多一处"常量名必须能反查键"的
        // 硬判据（constantNameOf 的 default 分支即 RED 面：往表里塞一个没登记的键立刻红）。
        final Set<String> putKeys = new TreeSet<>();
        int idx = 0;
        while ((idx = resolver.indexOf("resolver.put(", idx)) >= 0) {
            final int start = idx + "resolver.put(".length();
            final int end = resolver.indexOf(',', start);
            if (end < 0) {
                fail("CityBlockResolver: malformed resolver.put entry at offset " + idx);
            }
            final String ref = resolver.substring(start, end).trim();
            final int dot = ref.lastIndexOf('.');
            putKeys.add(dot < 0 ? ref : ref.substring(dot + 1));
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

    /** P16-B6：申报框锚点＝两条流的放置原点（今天都是世界 (0,0)；改原点必须同改 place 调用与这里）。 */
    private static final int STREAM_ORIGIN_X = 0;
    private static final int STREAM_ORIGIN_Z = 0;

    /** 断言③：全部城变体（含 2 城内巨构）放置流真实落块 + 申报跨度四判据 + 记录流确定性。 */
    private static void checkCityStreams(int winChunkX, int winChunkZ) throws Exception {
        final CityVariants.GroundFn flat = (x, z) -> 40;
        for (final CityVariants.Variant v : CityVariants.ALL) {
            final int reach = Math.max(v.sizeX, v.sizeZ);
            final SpanWindows recorder = windows(winChunkX, winChunkZ, reach);
            for (int s = 0; s < 12; s++) {
                CityVariants
                    .place(v, recorder, STREAM_ORIGIN_X, STREAM_ORIGIN_Z, flat, 0x51C17L + s,
                        BlockSink.FLAG_DIRECT);
            }
            checkStream("city variant " + v.name, recorder, () -> {
                final SpanWindows again = windows(winChunkX, winChunkZ, reach);
                for (int s = 0; s < 12; s++) {
                    CityVariants.place(v, again, STREAM_ORIGIN_X, STREAM_ORIGIN_Z, flat, 0x51C17L + s,
                        BlockSink.FLAG_DIRECT);
                }
                return again.digest.digest();
            });
        }
    }

    /** 断言④：6 outpost 放置流真实落块（自包 resolver 双层包裹幂等口径；边长 ≤16 ⇒ 窗组退化为 1 窗）。 */
    private static void checkOutpostStreams(int winChunkX, int winChunkZ) throws Exception {
        final CityVariants.GroundFn synthetic = (x, z) -> 40 + ((x * 7 + z * 13) % 9);
        boolean first = true;
        for (final Outpost outpost : ProsperityOutpostPlacer.ALL) {
            final int reach = Math.max(outpost.sizeX, outpost.sizeZ);
            final SpanWindows recorder = windows(winChunkX, winChunkZ, reach);
            final StructureBuilder builder = new StructureBuilder(recorder);
            for (int s = 0; s < 4; s++) {
                ProsperityOutpostPlacer
                    .place(builder, outpost, STREAM_ORIGIN_X, STREAM_ORIGIN_Z, s % 4,
                        CityVariants.MISSING_RATES[s % 3], new Random(0x4F557450L + s), synthetic,
                        BlockSink.FLAG_DIRECT);
            }
            if (first) {
                first = false;
                checkStream("outpost " + outpost.name, recorder, () -> {
                    final SpanWindows again = windows(winChunkX, winChunkZ, reach);
                    final StructureBuilder builder2 = new StructureBuilder(again);
                    for (int s = 0; s < 4; s++) {
                        ProsperityOutpostPlacer
                            .place(builder2, outpost, STREAM_ORIGIN_X, STREAM_ORIGIN_Z, s % 4,
                                CityVariants.MISSING_RATES[s % 3], new Random(0x4F557450L + s), synthetic,
                                BlockSink.FLAG_DIRECT);
                    }
                    return again.digest.digest();
                });
                continue;
            }
            checkStream("outpost " + outpost.name, recorder, null);
        }
    }

    /**
     * 一条结构流的判据（P12 的两条 + P16-B6 的三条），随后把申报量计入 PASS 行。
     * {@code rerun} 为 {@code null} 时跳过双跑摘要（outpost 只抽样 1 例，沿用 P12 的取样口径）。
     */
    private static void checkStream(String label, SpanWindows s, StreamSource rerun) throws Exception {
        tally(s);
        if (s.calls == 0) {
            fail(label + ": placer wrote NOTHING at all (calls=0 landed=0) — channel stream is empty");
        }
        if (s.landed == 0) {
            fail(label + ": wrote but ALL clipped by chunk window (calls=" + s.calls + " landed=0 clipped=" + s.clipped
                + " badY=" + s.sumWindowBadY() + " windows=" + s.declaredChunks()
                + ") — 'accept writes > 0' is NOT satisfied by mere emit calls");
        }
        // P16-B6 判据①：实得极值必须留在<b>申报</b>框内（对照量取名册边长，不取实测 ⇒ 甩出框必红）
        if (!s.callsInsideDeclaredBox()) {
            fail(label + ": 写出申报框 " + s.boxReadout() + " ⇒ 形态越界（旋转/偏移把块甩进街带或邻 chunk 之外）");
        }
        // P16-B6 判据②：申报框内每一格都必须被自己那扇窗接住（clipped 语义＝"写出了申报跨度"）
        if (s.clipped != 0) {
            fail(label + ": 申报跨度内有写落空 clipped=" + s.clipped + " " + s.boxReadout()
                + " windows=" + s.declaredChunks() + " ⇒ 申报边长小于实得跨度（少报多写）");
        }
        // P16-B6 判据③：每个申报窗都必须有内容（反"空分片凑数"）
        for (int i = 0; i < s.declaredChunks(); i++) {
            if (s.landedInWindow(i) == 0) {
                fail(label + ": 申报窗 #" + i + "/" + s.declaredChunks()
                    + " 零落块 ⇒ 申报了跨片但该槽没东西（分片实心并集与申报不符）");
            }
        }
        // 路由自洽：一写最多进一窗（否则 landed 会被重复计数，上面的判据全部失真）
        if (s.sumWindowLanded() != s.landed) {
            fail(label + ": 路由把同一格送进了多扇窗 sum(landed)=" + s.sumWindowLanded() + " != " + s.landed);
        }
        if (rerun != null) {
            digestCheck(s.digest, label, rerun);
        }
    }

    private static SpanWindows windows(int winChunkX, int winChunkZ, int reach) throws Exception {
        return new SpanWindows(winChunkX, winChunkZ, reach, STREAM_ORIGIN_X, STREAM_ORIGIN_Z);
    }

    private static ChunkWindowSink sink(int chunkX, int chunkZ) throws Exception {
        return new ChunkWindowSink(chunkX, chunkZ, MessageDigest.getInstance("SHA-256"));
    }

    private static void tally(SpanWindows s) {
        callsTotal += s.calls;
        landedTotal += s.landed;
        clippedTotal += s.clipped;
        windowsTotal += s.declaredChunks();
        streamCount++;
        if (s.declaredChunks() > 1) {
            multiWindowStreams++;
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
            case "gt5u:CasingFirebox":
                return "K_GT_FIREBOX";
            case "gt5u:ReinforcedGlass":
                return "K_GT_GLASS";
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
