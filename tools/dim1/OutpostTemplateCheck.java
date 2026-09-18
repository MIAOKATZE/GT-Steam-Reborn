import java.security.MessageDigest;
import java.util.Random;

import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;
import com.miaokatze.gtsr.common.dimension.framework.structure.RasterSink;
import com.miaokatze.gtsr.common.dimension.framework.structure.StructureBuilder;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperityOutpostPlacer;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperityOutpostPlacer.Outpost;
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
 * <li>6 变体尺寸契约：footprint ≤16×16、高度 ≤12（单 chunk 钳制前提）；形状串行列数自洽；</li>
 * <li>char→键全可解析：全部非 '.'/' ' 字符经 {@link CityVariants#blockKeyOf(char)} 得非 null 键，
 * meta ∈ 0..15（复用 CityVariants 全部键，含 'X'/'Z' GT5U 两键）；</li>
 * <li>GT5U 福利用量可见：每变体至少含 1 个 'X' 与 1 个 'Z'，并打印无损伤全量用量；</li>
 * <li>合成 heightfield 离线 raster 放置无越界：4 旋转 × 3 钳制原点 × 3 损伤档，16×16 chunk 窗
 * 计数 Sink 零越界回调、每组合法写入 &gt; 0；</li>
 * <li>确定性：同参双跑放置流 SHA-256 逐字节一致。</li>
 * </ol>
 */
public class OutpostTemplateCheck {

    /** 单 chunk 窗口径（与放置器钳制契约一致）。 */
    private static final int CHUNK = 16;
    private static final int MAX_HEIGHT = 12;
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
            totalX += accent[0];
            totalZ += accent[1];
            System.out
                .println("OUTPOST " + outpost.name + " " + outpost.sizeX + "x" + outpost.sizeY + "x" + outpost.sizeZ
                    + " accents X=" + accent[0] + " Z=" + accent[1] + " PASS");
        }
        System.out.println(
            "OUTPOSTTEMPLATE PASS: outposts=6 gt5uAccentsTotal X=" + totalX + " Z=" + totalZ
                + " (dims/chars/bounds/determinism all green)");
    }

    /** 断言①：footprint ≤16×16、高度 ≤12、形状串行列自洽。 */
    private static void checkDims(Outpost outpost) {
        if (outpost.sizeX < 1 || outpost.sizeX > CHUNK || outpost.sizeZ < 1 || outpost.sizeZ > CHUNK) {
            fail(outpost.name + ": footprint " + outpost.sizeX + "x" + outpost.sizeZ + " exceeds " + CHUNK + "x"
                + CHUNK);
        }
        if (outpost.sizeY < 1 || outpost.sizeY > MAX_HEIGHT) {
            fail(outpost.name + ": height " + outpost.sizeY + " exceeds " + MAX_HEIGHT);
        }
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

    private static void fail(String message) {
        System.out.println("OUTPOSTTEMPLATE FAIL: " + message);
        System.exit(1);
    }
}
