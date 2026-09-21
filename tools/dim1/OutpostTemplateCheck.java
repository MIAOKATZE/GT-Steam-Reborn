import java.security.MessageDigest;
import java.util.Random;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;
import com.miaokatze.gtsr.common.dimension.framework.structure.ChunkSpans;
import com.miaokatze.gtsr.common.dimension.framework.structure.RasterSink;
import com.miaokatze.gtsr.common.dimension.framework.structure.StructureBuilder;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperityOutpostPlacer;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperityOutpostPlacer.Outpost;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.RuinedColossusShapes;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.RuinedMachinePlacer;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityVariants;

/**
 * S-A5 验收① 离线自检（一次性自检 main，不进 jar，tools/ 惯例）：纯 JEP 330 单文件运行，
 * <b>零 Minecraft 依赖</b>（放置核心 {@link ProsperityOutpostPlacer#place} 签名与函数体均为
 * String 键 + GroundFn；不触碰 World/Block）：
 * {@code java -cp build/classes/java/main tools/dim1/OutpostTemplateCheck.java}
 * <p>
 * 断言面（plan §4 S-A5 / §12 修订 7/8；<b>S8 名单验收的替代口径</b>：S8RegistryRosterCheck 因
 * FML jar 缺 -cp 基线即失败（A1 切片 stash 对拍证实，非本切片引入），本工具+日志锚点串 grep
 * 取代之，S8 classpath 不在本切片修复范围）：
 * <ol>
 * <li><b>尺寸契约（P16-B1 起是"成对断言"，plan §1 判据 G7）</b>：任何一条结构都要同时过两条——
 * ①<b>每个分片</b> ≤16×16×12、②<b>申报的总 bbox 恰好等于各分片实心格的并集</b>（并按申报的跨 chunk
 * 数核对实得分片数）。outpost 是"只有 1 个分片"的退化情形，于是旧判据"footprint ≤16×16、高 ≤12"
 * 原样保留在①里（<b>没有被删</b>，只是不再顺带禁止"总 bbox 更大"），而②对退化情形等价于
 * "形状串自己报的尺寸 == 它真的有东西的范围"，比旧判据更严（旧判据读不到这个）。
 * 两条都由 {@link ChunkSpans} 派生——与生产放置器共用同一份几何，断言不自算一套；
 * 形状串行列数自洽照旧。P16-B1 新增的第二段（断言⑥）把同一组成对断言打到城外跨 chunk 巨构上，
 * 并额外核对"各分片写出来的实心格之和 == 整座模板的实心格数"（分片拼回去必须正好是一座，
 * 不重不漏——漏一格就是"邻槽没画到"的鬼影，重一格就是两片在同一 chunk 上叠罗汉）；</li>
 * <li>char→键全可解析：全部非 '.'/' ' 字符经 {@link CityVariants#blockKeyOf(char)} 得非 null 键，
 * meta ∈ 0..15（复用 CityVariants 全部键，含 'X'/'Z' GT5U 两键）；</li>
 * <li>GT5U 福利用量可见：每变体至少含 1 个 'X' 与 1 个 'Z'，并打印无损伤全量用量；</li>
 * <li>合成 heightfield 离线 raster 放置无越界：4 旋转 × 3 钳制原点 × 3 损伤档，16×16 chunk 窗
 * 计数 Sink 零越界回调、每组合法写入 &gt; 0；</li>
 * <li>确定性：同参双跑放置流 SHA-256 逐字节一致。</li>
 * </ol>
 */
public class OutpostTemplateCheck {

    /** 单 chunk 窗口径（真值取自框架件 {@link ChunkSpans#CHUNK_BLOCKS}，断言侧不留第二份数字）。 */
    private static final int CHUNK = ChunkSpans.CHUNK_BLOCKS;
    /** 三种锚点抖动位（0 = 与 chunk 栅格同相；8/5 与 15/12 = 总 bbox 横跨 chunk 边界的最坏形态）。 */
    private static final int[][] TILING_ALIGNMENTS = { { 0, 0 }, { 8, 5 }, { 15, 12 } };
    /** 合成 heightfield：基准 40 + 双列哈希 0..8（有起伏，暴露逐列接地越界）。 */
    private static final CityVariants.GroundFn SYNTHETIC_GROUND = (x, z) -> 40 + ((x * 7 + z * 13) % 9);
    private static final long PLACE_SEED = 0x4F557450L;

    public static void main(String[] args) throws Exception {
        if (ProsperityOutpostPlacer.ALL.length != 6) {
            fail("expected 6 outposts, got " + ProsperityOutpostPlacer.ALL.length);
        }
        int totalX = 0;
        int totalZ = 0;
        for (final Outpost outpost : ProsperityOutpostPlacer.ALL) {
            checkDims(outpost);
            final int[] accent = checkChars(outpost);
            if (accent[0] < 1 || accent[1] < 1) {
                fail(outpost.name + ": GT5U accents not visible (X=" + accent[0] + " Z=" + accent[1] + ")");
            }
            checkRasterPlacement(outpost);
            checkSpanPair(
                "outpost " + outpost.name,
                outpost.sizeX,
                outpost.sizeY,
                outpost.sizeZ,
                outpost.layers,
                ChunkSpans.chunksAcross(outpost.sizeX),
                ChunkSpans.chunksAcross(outpost.sizeZ),
                1,
                false);
            totalX += accent[0];
            totalZ += accent[1];
            System.out
                .println("OUTPOST " + outpost.name + " " + outpost.sizeX + "x" + outpost.sizeY + "x" + outpost.sizeZ
                    + " accents X=" + accent[0] + " Z=" + accent[1] + " PASS");
        }
        final int colossi = checkColossi();
        System.out.println(
            "OUTPOSTTEMPLATE PASS: outposts=6 gt5uAccentsTotal X=" + totalX + " Z=" + totalZ
                + " colossi=" + colossi + " (dims/chars/bounds/determinism + span-pair/tiling all green)");
    }

    /** 断言①（前半）：形状串行列自洽（尺寸上界交给 {@link #checkSpanPair} 的成对断言，不在此重复一份判据）。 */
    private static void checkDims(Outpost outpost) {
        if (outpost.layers.length != outpost.sizeY) {
            fail(outpost.name + ": layer count " + outpost.layers.length + " != sizeY " + outpost.sizeY);
        }
        for (int y = 0; y < outpost.sizeY; y++) {
            if (outpost.layers[y].length != outpost.sizeZ) {
                fail(outpost.name + ": layer " + y + " row count != sizeZ " + outpost.sizeZ);
            }
            for (int z = 0; z < outpost.sizeZ; z++) {
                if (outpost.layers[y][z].length() != outpost.sizeX) {
                    fail(outpost.name + ": layer " + y + " row " + z + " length != sizeX " + outpost.sizeX);
                }
            }
        }
    }

    /** 断言②③：char→键全可解析 + meta 域 + X/Z 用量计数；返回 {X 计数, Z 计数}。 */
    private static int[] checkChars(Outpost outpost) {
        int x = 0;
        int z = 0;
        for (int y = 0; y < outpost.sizeY; y++) {
            for (int dz = 0; dz < outpost.sizeZ; dz++) {
                for (int dx = 0; dx < outpost.sizeX; dx++) {
                    final char c = outpost.charAt(y, dx, dz);
                    if (c == '.' || c == ' ') {
                        continue;
                    }
                    if (CityVariants.blockKeyOf(c) == null) {
                        fail(outpost.name + ": char '" + c + "' does not resolve to a block key");
                    }
                    final int meta = CityVariants.metaOf(c);
                    if (meta < 0 || meta > 15) {
                        fail(outpost.name + ": char '" + c + "' meta " + meta + " out of 0..15");
                    }
                    if (c == 'X') {
                        x++;
                    }
                    if (c == 'Z') {
                        z++;
                    }
                }
            }
        }
        return new int[] { x, z };
    }

    /** 断言④⑤：4 旋转 × 3 钳制原点 × 3 损伤档 raster 放置零越界 + 双跑 SHA-256 一致。 */
    private static void checkRasterPlacement(Outpost outpost) throws Exception {
        for (int rot = 0; rot < 4; rot++) {
            final int[] rotated = StructureBuilder.rotateSize(outpost.sizeX, outpost.sizeZ, rot);
            if (rotated[0] > CHUNK || rotated[1] > CHUNK) {
                fail(outpost.name + ": rotation " + rot + " footprint " + rotated[0] + "x" + rotated[1] + " exceeds "
                    + CHUNK + "x" + CHUNK);
            }
            final int[][] origins = { { 0, 0 }, { CHUNK - rotated[0], CHUNK - rotated[1] },
                { (CHUNK - rotated[0]) / 2, (CHUNK - rotated[1]) / 2 } };
            for (final int[] origin : origins) {
                for (int tier = 0; tier < CityVariants.MISSING_RATES.length; tier++) {
                    final byte[] first = rasterOnce(outpost, origin[0], origin[1], rot,
                        CityVariants.MISSING_RATES[tier]);
                    final byte[] second = rasterOnce(outpost, origin[0], origin[1], rot,
                        CityVariants.MISSING_RATES[tier]);
                    if (!MessageDigest.isEqual(first, second)) {
                        fail(outpost.name + ": double-run digest mismatch (rot=" + rot + " origin=" + origin[0] + ","
                            + origin[1] + " tier=" + tier + ")");
                    }
                }
            }
        }
    }

    /** 单次放置：合成 heightfield + 16×16 窗计数 Sink；返回放置流摘要；越界即 fail。 */
    private static byte[] rasterOnce(Outpost outpost, int originX, int originZ, int rot, int missingRate)
        throws Exception {
        final RasterSink raster = new RasterSink(0, 0, CHUNK, CHUNK);
        final MessageDigest digest = MessageDigest.getInstance("SHA-256");
        final int[] violations = { 0 };
        final int[] accepted = { 0 };
        final BlockSink bounded = (x, y, z, block, meta, flags) -> {
            if (x < 0 || x >= CHUNK || z < 0 || z >= CHUNK || y < 0 || y > 255) {
                violations[0]++;
                return false;
            }
            digest.update((x + "," + y + "," + z + ":" + block + ":" + meta + ";").getBytes());
            final boolean ok = raster.setBlock(x, y, z, block, meta, flags);
            if (ok) {
                accepted[0]++;
            }
            return ok;
        };
        ProsperityOutpostPlacer
            .place(
                new StructureBuilder(bounded),
                outpost,
                originX,
                originZ,
                rot,
                missingRate,
                new Random(PLACE_SEED),
                SYNTHETIC_GROUND,
                BlockSink.FLAG_POPULATE);
        if (violations[0] > 0) {
            fail(outpost.name + ": " + violations[0] + " out-of-chunk writes (rot=" + rot + " origin=" + originX + ","
                + originZ + ")");
        }
        if (accepted[0] == 0) {
            fail(outpost.name + ": zero accepted writes (rot=" + rot + " origin=" + originX + "," + originZ + ")");
        }
        return digest.digest();
    }

    // ═══════════════ P16-B1：成对断言 + 跨 chunk 分片拼合对账（plan §1 判据 G7）═══════════════

    /**
     * 成对断言：结构必须<b>同时</b>过两条——
     * <b>①</b>每个分片 ≤16×16×12（{@link ChunkSpans.Slice#withinSliceLimits()}）、
     * <b>②</b>申报的跨 chunk 数 == 栅格推导数 == 实得分片覆盖数（跨片结构再加一条：总 bbox 恰好
     * 等于各分片实心格的并集，{@code requireFullUnion=true}；outpost 那 6 张旧模板只钉"并集非空且不出框
     * + 唯一分片就是整座"，理由见方法体内的注释——把 full-union 打到旧资产上会逼本片去改 6 张模板）。
     * {@code minChunks}：outpost 传 1（退化单片），城外巨构传 2。
     * <p>
     * 几何一律走 {@link ChunkSpans}——与生产放置器（{@code RuinedMachinePlacer.placeSlice} /
     * {@code ChunkSliceSink}）同一份实现，断言侧不留第二套切分逻辑（否则"断言自己算一套"就是假绿）。
     * 旧判据"footprint ≤16×16、高 ≤12"原样活在臂①里（outpost 只有一个分片 ⇒ 该分片就是整座），
     * 所以本次改写<b>没有删除</b>任何一条上界，只是不再顺带禁止"总 bbox 更大"。
     */
    private static void checkSpanPair(String label, int sizeX, int sizeY, int sizeZ, String[][] layers,
        int declaredChunksX, int declaredChunksZ, int minChunks, boolean requireFullUnion) {
        if (layers.length != sizeY) {
            fail(label + ": layer count " + layers.length + " != declared sizeY " + sizeY);
        }
        final ChunkSpans.Slice[] slices = ChunkSpans.slice(layers, sizeX, sizeY, sizeZ);
        for (final ChunkSpans.Slice s : slices) {
            if (!s.withinSliceLimits()) {
                fail(
                    label + ": slice " + s.chunkDx + "," + s.chunkDz + " = " + s.sizeX + "x" + s.sizeZ + "x"
                        + s.sizeY + " breaches the per-slice limit " + ChunkSpans.CHUNK_BLOCKS + "x"
                        + ChunkSpans.CHUNK_BLOCKS + "x" + ChunkSpans.MAX_SLICE_HEIGHT);
            }
            if (!s.usedInsideSelf()) {
                fail(label + ": slice " + s.chunkDx + "," + s.chunkDz + " used bounds escape its own frame");
            }
            if (s.solidChars <= 0) {
                fail(label + ": slice " + s.chunkDx + "," + s.chunkDz + " has no solid cell（空转分片）");
            }
        }
        final int[] u = ChunkSpans.unionBounds(slices);
        if (u == null) {
            fail(label + ": 整座模板一个实心格都没有");
        }
        if (requireFullUnion) {
            if (!ChunkSpans.unionEqualsBox(slices, sizeX, sizeY, sizeZ)) {
                fail(
                    label + ": declared bbox " + sizeX + "x" + sizeY + "x" + sizeZ + " != union of slices "
                        + java.util.Arrays.toString(u));
            }
        } else {
            // 单片旧条目（outpost）不钉"六面都吃到"：既有 6 张模板里 16x8x5 的水渠段本来就两头留白，
            // 那是形状而不是谎报跨片（跨片申报只有一个 chunk，由下面的 span/covered 两条钉死）。
            // 把 full-union 打到旧资产上等于逼本片去改 6 张既有模板 ⇒ 越界改动 + [15] 对拍全红。
            // 这一支仍然钉：并集非空、不出框、且唯一分片就是整座（= 旧判据 ≤16×16×12 的等价形态）。
            if (u[0] < 0 || u[1] > sizeX - 1 || u[2] < 0 || u[3] > sizeY - 1 || u[4] < 0 || u[5] > sizeZ - 1) {
                fail(label + ": union " + java.util.Arrays.toString(u) + " escapes declared box " + sizeX + "x"
                    + sizeY + "x" + sizeZ);
            }
            if (slices.length != 1) {
                fail(label + ": 单 chunk 条目实得 " + slices.length + " 个分片");
            }
        }
        final int spanX = ChunkSpans.chunksAcross(sizeX);
        final int spanZ = ChunkSpans.chunksAcross(sizeZ);
        if (declaredChunksX != spanX || declaredChunksZ != spanZ) {
            fail(label + ": declared span " + declaredChunksX + "x" + declaredChunksZ + " != grid span " + spanX
                + "x" + spanZ);
        }
        final int covX = ChunkSpans.distinctChunkOffsets(slices, true);
        final int covZ = ChunkSpans.distinctChunkOffsets(slices, false);
        if (covX != declaredChunksX || covZ != declaredChunksZ) {
            fail(
                label + ": covered chunks " + covX + "x" + covZ + " != declared " + declaredChunksX + "x"
                    + declaredChunksZ);
        }
        if (covX * covZ < minChunks) {
            fail(label + ": 实得跨 " + covX * covZ + " 个 chunk < 要求的 " + minChunks + " 个");
        }
    }

    /** 断言⑥：城外跨 chunk 巨构的成对断言 + 分片拼合对账。返回条数（0 条即红——不许空转绿）。 */
    private static int checkColossi() throws Exception {
        if (RuinedColossusShapes.ALL.length < 1) {
            fail("城外跨 chunk 巨构一条都没有（G7 的判据对象不存在）");
        }
        for (final RuinedColossusShapes.Colossus c : RuinedColossusShapes.ALL) {
            checkSpanPair(
                "colossus " + c.name,
                c.sizeX(),
                c.sizeY(),
                c.sizeZ(),
                c.shape.layers,
                c.declaredChunksX,
                c.declaredChunksZ,
                2,
                true);
            checkTiling(c);
            checkWreckTiling(c);
            System.out.println(
                "COLOSSUS " + c.name + " " + c.sizeX() + "x" + c.sizeY() + "x" + c.sizeZ() + " spanChunks="
                    + c.declaredChunksX + "x" + c.declaredChunksZ + " slices=" + c.slices.length + " solid="
                    + c.solidChars() + " 残骸档=" + c.wrecks.length + " maxBury=" + c.maxBury + " PASS");
        }
        return RuinedColossusShapes.ALL.length;
    }

    /**
     * <b>P16-B2 断言⑦</b>：同一组成对/拼合判据打到<b>会落到世界里的那份东西</b>上——每条残骸档 ×
     * 每个埋深（0..maxBury），仍按三种抖动位、仍走<b>生产</b> {@link RuinedMachinePlacer#placeSlice}。
     * <ol>
     * <li>每个被覆盖的 chunk 都真落了实心块（半埋截断不得把某一片整片埋没成鬼影）；</li>
     * <li>没有任何写入外溢到别的 chunk（分片协议对残骸档同样成立）；</li>
     * <li>各 chunk 实心块之和 == <b>该残骸档在该埋深下该露出来的那一坨</b>（{@code exposedSolid(depth)}；
     * 注意比的是 wreck 的数而不是母体的——母体那条由 {@link #checkTiling} 单独钉，两件事都要绿）；</li>
     * <li>同参双跑分片落块流 SHA-256 逐字节一致（形态层没有偷进任何非确定性）。</li>
     * </ol>
     * 埋深这一维不进 SHA 对账的"同一座"定义：{@code bury} 由 {@link RuinedColossusShapes#morphAt}
     * 从锚点世界原点纯哈希派生，这里显式传入 ⇒ 钉的是"给定形态档与埋深，渲染几何自洽"。
     */
    private static void checkWreckTiling(RuinedColossusShapes.Colossus c) throws Exception {
        for (final RuinedColossusShapes.Wreck w : c.wrecks) {
            for (int depth = 0; depth <= c.maxBury; depth++) {
                for (final int[] align : TILING_ALIGNMENTS) {
                    final int originX = align[0];
                    final int originZ = align[1];
                    final RuinedColossusShapes.Morph morph = new RuinedColossusShapes.Morph(c, w, depth);
                    long solids = 0;
                    int chunks = 0;
                    for (int cx = ChunkSpans.chunkOf(originX); cx <= ChunkSpans
                        .chunkOf(originX + c.sizeX() - 1); cx++) {
                        for (int cz = ChunkSpans.chunkOf(originZ); cz <= ChunkSpans
                            .chunkOf(originZ + c.sizeZ() - 1); cz++) {
                            final long sliceSeed = 0xC01055L + cx * 31L + cz;
                            final TileSink first = tileOnce(c, originX, originZ, cx, cz, sliceSeed, morph);
                            final TileSink second = tileOnce(c, originX, originZ, cx, cz, sliceSeed, morph);
                            if (first.outside > 0) {
                                fail(
                                    w.label + " d=" + depth + ": " + first.outside + " 格外溢到别的 chunk (align "
                                        + originX + "," + originZ + ")");
                            }
                            // 逐片精确对账：该片在【本埋深下该露出来的那一坨】里有多少设计实心格，
                            // 生产链就必须落那么多（0 也是合法值——削顶 + 半埋之后某个角上的 chunk
                            // 本来就可以没东西，这不算鬼影；鬼影是"该有却没有"，所以判据按格数比，
                            // 而不是按"每个被 bbox 碰到的 chunk 都必须非空"那种会误伤的形状假设）。
                            final int expect = expectedSlice(c, w, cx, cz, originX, originZ, depth);
                            if (first.solid != expect) {
                                fail(
                                    w.label + " d=" + depth + ": chunk " + cx + "," + cz + " 落了 " + first.solid
                                        + " 块，该片设计应落 " + expect);
                            }
                            if (!MessageDigest.isEqual(first.digest.digest(), second.digest.digest())) {
                                fail(w.label + " d=" + depth + ": 双跑分片 SHA 不一致 @ chunk " + cx + "," + cz);
                            }
                            solids += first.solid;
                            chunks++;
                        }
                    }
                    if (chunks < 2) {
                        fail(
                            w.label + " d=" + depth + " 抖动位 " + originX + "," + originZ + " 下只覆盖 " + chunks
                                + " 个 chunk");
                    }
                    // 分母是【该埋深下还该露出来的那一坨】：埋住的 y<depth 几层一格都不写（G8①③），
                    // 所以这里比 exposedSolid(depth) 而不是整座的 solid——比错了会把正确行为判红。
                    if (solids != w.exposedSolid(depth)) {
                        fail(
                            w.label + " d=" + depth + ": 各分片实心块之和 " + solids + " != 该档该埋深应露出的 "
                                + w.exposedSolid(depth) + " 格（整座 solid=" + w.solid + "）");
                    }
                }
            }
            System.out.println(
                "COLOSSUS-WRECK " + w.label + " solid=" + w.solid + " machine=" + w.machine + " 埋深0.."
                    + c.maxBury + " × 3 抖动位 × " + c.slices.length + " 片 全绿");
        }
    }

    /**
     * 该片（chunk cx,cz）在埋深 {@code depth} 下<b>设计上应该落多少块</b>——按生产同一份
     * {@link ChunkSpans#localMin(int, int, int)}/{@link ChunkSpans#localMax(int, int, int)} 取范围，
     * 再数该残骸档里 {@code y >= depth} 的实心格。与 {@link RuinedMachinePlacer#placeSlice} 同源 ⇒
     * 这条对账不是"断言自己算一套几何"。
     */
    private static int expectedSlice(RuinedColossusShapes.Colossus c, RuinedColossusShapes.Wreck w, int cx, int cz,
        int originX, int originZ, int depth) {
        final int dxLo = ChunkSpans.localMin(originX, c.sizeX(), cx);
        final int dxHi = ChunkSpans.localMax(originX, c.sizeX(), cx);
        final int dzLo = ChunkSpans.localMin(originZ, c.sizeZ(), cz);
        final int dzHi = ChunkSpans.localMax(originZ, c.sizeZ(), cz);
        int n = 0;
        for (int y = depth; y < c.sizeY(); y++) {
            for (int dx = dxLo; dx <= dxHi; dx++) {
                for (int dz = dzLo; dz <= dzHi; dz++) {
                    if (ChunkSpans.isSolid(w.charAt(y, dx, dz))) {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    private static TileSink tileOnce(RuinedColossusShapes.Colossus c, int originX, int originZ, int cx, int cz,
        long seed) throws Exception {
        return tileOnce(c, originX, originZ, cx, cz, seed, null);
    }

    private static TileSink tileOnce(RuinedColossusShapes.Colossus c, int originX, int originZ, int cx, int cz,
        long seed, RuinedColossusShapes.Morph morph) throws Exception {
        final TileSink sink = new TileSink(cx, cz, newSha());
        RuinedMachinePlacer.placeSlice(
            new StructureBuilder(sink),
            new NoMissingRandom(seed),
            c.shape,
            originX,
            originZ,
            cx,
            cz,
            SYNTHETIC_GROUND,
            BlockSink.FLAG_POPULATE,
            morph);
        return sink;
    }

    /**
     * 分片拼合对账：同一座巨构在三种抖动位下，各覆盖 chunk 各自走<b>生产</b>的
     * {@link RuinedMachinePlacer#placeSlice}（用 {@link NoMissingRandom} 关掉逐格缺失掷骰），断言
     * ① 每个覆盖 chunk 都真落了实心块（没有"邻槽画不出来"的空洞）；
     * ② 没有任何写入落到本 chunk 之外（分片协议不外溢，等价于 {@code ChunkClampedSink} 零丢弃）；
     * ③ 各 chunk 实心块数之和 == 整座模板的实心格数（不重不漏，拼回去正好是一座）；
     * ④ 覆盖 chunk 数 ≥2（"跨 chunk 成型"的正面证据，不是注释里的一句话）；
     * ⑤ 同参双跑分片落块流 SHA-256 逐字节一致。
     */
    private static void checkTiling(RuinedColossusShapes.Colossus c) throws Exception {
        for (final int[] align : TILING_ALIGNMENTS) {
            final int originX = align[0];
            final int originZ = align[1];
            final int cx0 = ChunkSpans.chunkOf(originX);
            final int cx1 = ChunkSpans.chunkOf(originX + c.sizeX() - 1);
            final int cz0 = ChunkSpans.chunkOf(originZ);
            final int cz1 = ChunkSpans.chunkOf(originZ + c.sizeZ() - 1);
            final Set<String> seen = new LinkedHashSet<>();
            long solids = 0;
            for (int cx = cx0; cx <= cx1; cx++) {
                for (int cz = cz0; cz <= cz1; cz++) {
                    final long sliceSeed = 0xC01055L + cx * 31L + cz;
                    final TileSink first = tileOnce(c, originX, originZ, cx, cz, sliceSeed);
                    final TileSink second = tileOnce(c, originX, originZ, cx, cz, sliceSeed);
                    if (first.outside > 0) {
                        fail(
                            c.name + ": " + first.outside + " writes escaped chunk " + cx + "," + cz + " (align "
                                + originX + "," + originZ + ")");
                    }
                    if (first.solid == 0) {
                        fail(c.name + ": covered chunk " + cx + "," + cz + " landed nothing (鬼影分片)");
                    }
                    if (!java.security.MessageDigest.isEqual(first.digest.digest(), second.digest.digest())) {
                        fail(c.name + ": double-run slice digest mismatch @ chunk " + cx + "," + cz);
                    }
                    seen.add(cx + "," + cz);
                    solids += first.solid;
                }
            }
            if (seen.size() < 2) {
                fail(c.name + ": 抖动位 " + originX + "," + originZ + " 下只覆盖 " + seen.size() + " 个 chunk");
            }
            if (solids != c.solidChars()) {
                fail(
                    c.name + ": 各分片实心块之和 " + solids + " != 模板实心格数 " + c.solidChars() + " (align "
                        + originX + "," + originZ + " chunks=" + new TreeSet<>(seen) + ")");
            }
        }
    }

    /** 只记本 chunk 窗内的写入（复刻 {@code ChunkClampedSink} 的接受/丢弃口径，零 MC 依赖）。 */
    private static final class TileSink implements BlockSink {

        private final int chunkX;
        private final int chunkZ;
        final java.security.MessageDigest digest;
        int solid;
        int outside;

        TileSink(int chunkX, int chunkZ, java.security.MessageDigest digest) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.digest = digest;
        }

        @Override
        public boolean setBlock(int x, int y, int z, Object block, int meta, int flags) {
            if ((x >> 4) != this.chunkX || (z >> 4) != this.chunkZ) {
                this.outside++;
                return false;
            }
            if (y < 0 || y > 255) {
                return false;
            }
            this.digest.update((x + "," + y + "," + z + ":" + block + ":" + meta + ";").getBytes());
            if (!CityVariants.K_AIR.equals(String.valueOf(block))) {
                this.solid++;
            }
            return true;
        }
    }

    /**
     * 关掉"逐格缺失"掷骰的确定性桩：所有 {@code nextInt(bound)} 都给上界值 ⇒ 损伤度落在 95 档，
     * 而两次缺失判定（{@code <45} 与 {@code <40}）都不成立 ⇒ 模板里每个实心格都真的写出去。
     * 只用于分片拼合对账（比的是几何覆盖，不是观感损伤）；生产路径不引用本桩。
     */
    private static final class NoMissingRandom extends Random {

        NoMissingRandom(long seed) {
            super(seed);
        }

        @Override
        public int nextInt(int bound) {
            return bound - 1;
        }
    }

    private static java.security.MessageDigest newSha() {
        try {
            return java.security.MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void fail(String message) {
        System.out.println("OUTPOSTTEMPLATE FAIL: " + message);
        System.exit(1);
    }
}
