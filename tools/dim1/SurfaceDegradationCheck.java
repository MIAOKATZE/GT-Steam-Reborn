import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.BiomeGenBase;

import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority.Degraded;
import com.miaokatze.gtsr.common.dimension.framework.GTSRChunkProviderBase;
import com.miaokatze.gtsr.common.dimension.framework.GTSRDimensionDef;
import com.miaokatze.gtsr.common.dimension.framework.GTSRWorldChunkManager;
import com.miaokatze.gtsr.common.dimension.prosperity.ChunkProviderProsperityRuins;
import com.miaokatze.gtsr.common.dimension.shattered.ChunkProviderShatteredGrounds;

/**
 * <b>P2 判据 B 的运行时路径级离线断言</b>：降级态一律不铺表层（plan §5 P2 / §2.1 L3 禁止项 /
 * §2.3 判据 2「降级可见」/ §7.1 U7「槽全不够才降级且不铺表层」）。
 * <p>
 * 钉住的事实（P1 交给本片的硬事实 1）：EMPTY（空表）下改造前
 * {@code GTSRChunkProviderBase.provideChunk} 的 {@code chunkBiomes[i] = (byte) biomes[i].biomeID}
 * 会 <b>NPE</b>；改造后表层链在降级态必须①不抛②零方块写入③全维不出现
 * {@code plains}/{@code Blocks.grass}/{@code Blocks.dirt}。
 * <p>
 * 三态如何得到（全部走生产入口，不伪造）：
 * <ul>
 * <li><b>EMPTY</b>（dim79）：名册一个都不入账 ⇒ {@code degraded()==EMPTY}；def 不挂任何群系
 * ⇒ {@code GTSRWorldChunkManager.weightedBiomes==null} ⇒ {@code loadBlockGeneratorData}
 * 整数组 null（与实机 mode2 空表同一产出路径）；</li>
 * <li><b>SHORT</b>（dim78）：{@code recordAllocation} 2 个 + {@code recordNoSlot} 2 个
 * ⇒ {@code degraded()==SHORT}；数组里同时放「在场实例」「缺席实例」「null 列」三种，
 * 断言缺席处不铺、在场处照铺；</li>
 * <li><b>NONE 对照</b>（dim78，另建 JVM 内的第三场景）：4 个全部入账 ⇒ 每列都铺
 * ——防止"门永远关着"造成的假绿。</li>
 * </ul>
 * <b>诚实性</b>：不依赖 {@code Blocks.grass} 的实例可比性（离线恒 null）——降级态输出用
 * <b>白名单</b>断言（只允许 bedrock / stone / shatteredCorestone / null 四种槽位值）；
 * 不用"chunk 内 256 格同群系"式断言。
 * <p>
 * 命令见 {@code tools/dim1/surface_checks.sh}。
 */
public class SurfaceDegradationCheck {

    private static int failures = 0;
    private static int passed = 0;

    public static void main(String[] args) throws Exception {
        SurfaceHarness.initVanillaBlocks();
        SurfaceHarness.blockFamily();

        final BiomeGenBase[] p = SurfaceHarness.prosperityBiomes();
        final BiomeGenBase[] s = SurfaceHarness.shatteredBiomes();

        checkEmptyDim79(s);
        checkShortDim78(p);
        checkNormalDim78Control(p);

        if (failures > 0) {
            System.out.println("SURFACE DEGRADATION CHECK FAIL: failures=" + failures + " passed=" + passed);
            System.exit(1);
        }
        System.out.println("SURFACE DEGRADATION CHECK PASS: assertions=" + passed);
    }

    // ————— EMPTY（空表级）：不抛异常 / 零方块写入 / 全维无 plains·grass·dirt —————

    private static void checkEmptyDim79(BiomeGenBase[] s) throws Exception {
        final String tag = "EMPTY(dim79)";
        final GTSRDimensionDef def = SurfaceHarness.emptyDef(false); // 不挂任何群系
        final GTSRWorldChunkManager mgr = SurfaceHarness.manager(false, def);
        final GTSRBiomeAuthority authority = GTSRBiomeAuthority.forDimKey(GTSRBiomeAuthority.DIM_KEY_SHATTERED);
        check(authority.isBound(), tag + " authority bound by chunk manager (门生效前提)");
        check(authority.degraded() == Degraded.EMPTY, tag + " degraded==EMPTY，实=" + authority.degraded());
        check(mgr.isEmptyDegraded(), tag + " manager.isEmptyDegraded()");

        // ① 生产者口径：整数组 null（缺席以 null 呈现，不是 plains）
        final BiomeGenBase[] array = mgr.loadBlockGeneratorData(null, 0, 0, 16, 16);
        int nonNull = 0;
        for (final BiomeGenBase b : array) {
            if (b != null) {
                nonNull++;
            }
            if (b == BiomeGenBase.plains) {
                fail(tag + " producer must never emit BiomeGenBase.plains");
                return;
            }
        }
        check(nonNull == 0, tag + " loadBlockGeneratorData 全 null（无 plains 回退），nonNull=" + nonNull);

        // ② 不抛异常 + ③ 表层零方块写入（与"只跑 generateTerrain"的对照数组逐槽相等）
        final Method gen = SurfaceHarness.generateTerrain();
        final Method surface = SurfaceHarness.surfaceSeam(ChunkProviderShatteredGrounds.class);
        final GTSRChunkProviderBase provider = SurfaceHarness.provider(false);
        int cleanChunks = 0;
        final Map<String, Long> hist = new TreeMap<>();
        final int half = 4;
        for (int cx = -half; cx < half; cx++) {
            for (int cz = -half; cz < half; cz++) {
                final Block[] only = new Block[65536];
                final byte[] onlyMeta = new byte[65536];
                gen.invoke(provider, cx, cz, only, onlyMeta, null);
                final Block[] got = new Block[65536];
                final byte[] gotMeta = new byte[65536];
                gen.invoke(provider, cx, cz, got, gotMeta, null);
                final BiomeGenBase[] degraded = mgr.loadBlockGeneratorData(null, cx * 16, cz * 16, 16, 16);
                surface.invoke(null, SurfaceHarness.SEED, cx * 16, cz * 16, got, gotMeta, degraded);
                int drift = 0;
                for (int i = 0; i < 65536; i++) {
                    if (got[i] != only[i] || gotMeta[i] != onlyMeta[i]) {
                        drift++;
                    }
                    final String label = label(got[i], true);
                    hist.merge(label, 1L, Long::sum);
                }
                if (drift != 0) {
                    fail(tag + " chunk(" + cx + "," + cz + ") wrote " + drift + " slot(s) in degraded state");
                    return;
                }
                cleanChunks++;
            }
        }
        pass(tag + " 表层与填充层零写入，chunks=" + cleanChunks + "（真实 generateTerrain 主体原样）");

        // ④ 白名单：降级态全维只可能出现 bedrock / shatteredCorestone / null（不含 grass/dirt/top 族）
        checkWhitelist(tag, hist, "Blocks.bedrock", "BlocksGTSR.shatteredCorestone", "(empty)");

        // ⑤ Chunk byte 平面写点（provideChunk 的 NPE 现场）
        checkBiomePlane(tag, array);

        // ⑥ 采样：EMPTY 维度 biomeAt 永不落到 plains
        int plainsSeen = 0;
        int nullSeen = 0;
        for (int cx = -10; cx < 10; cx++) {
            for (int cz = -10; cz < 10; cz++) {
                final BiomeGenBase b = mgr.biomeAt(cx, cz);
                if (b == null) {
                    nullSeen++;
                }
                if (b == BiomeGenBase.plains) {
                    plainsSeen++;
                }
            }
        }
        check(plainsSeen == 0 && nullSeen == 400, tag + " 400 chunk 采样全 null 且无 plains，null=" + nullSeen
            + " plains=" + plainsSeen);

        // ⑦ 一次性日志锚点
        check(GTSRChunkProviderBase.logSurfaceNotLaidOnce("offline-empty", "EMPTY"),
            tag + " 降级日志锚点首次可打印（含级别名）");
        check(!GTSRChunkProviderBase.logSurfaceNotLaidOnce("offline-empty", "EMPTY"),
            tag + " 同一 dimKey/级别只打一次（一次性）");
    }

    // ————— SHORT（短表级）：缺席群系处不铺、在场群系处正常 —————

    private static void checkShortDim78(BiomeGenBase[] p) throws Exception {
        final String tag = "SHORT(dim78)";
        SurfaceHarness.recordProsperityShort(2, p); // 0,1 在场；2,3 缺席
        final GTSRBiomeAuthority authority = GTSRBiomeAuthority.forDimKey(GTSRBiomeAuthority.DIM_KEY_PROSPERITY);
        final GTSRDimensionDef def = SurfaceHarness.def(true, new BiomeGenBase[] { p[0], p[1] },
            new int[] { 45, 30 });
        final GTSRWorldChunkManager mgr = SurfaceHarness.manager(true, def);
        check(authority.isBound(), tag + " authority bound");
        check(authority.degraded() == Degraded.SHORT, tag + " degraded==SHORT，实=" + authority.degraded());
        check(authority.allocatedCount() == 2, tag + " allocatedCount==2");

        // 逐列混排：在场 / 缺席 / null（非"整 chunk 同群系"，见类注释诚实性段）
        final Block[] blocks = new Block[65536];
        final byte[] meta = new byte[65536];
        final BiomeGenBase[] biomes = new BiomeGenBase[256];
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                final int slot = (x + z) % 4;
                // slot 0/1 = 在场两群系；2 = 缺席群系实例（账本记为 no-slot）；3 = null
                biomes[x + z * 16] = slot == 0 ? p[0] : slot == 1 ? p[1] : slot == 2 ? p[2] : null;
            }
        }
        final Method gen = SurfaceHarness.generateTerrain();
        final GTSRChunkProviderBase provider = SurfaceHarness.provider(true);
        gen.invoke(provider, 0, 0, blocks, meta, null);
        SurfaceHarness.surfaceSeam(ChunkProviderProsperityRuins.class)
            .invoke(null, SurfaceHarness.SEED, 0, 0, blocks, meta, biomes);

        int presentOk = 0;
        int absentBad = 0;
        int nullBad = 0;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                final BiomeGenBase assigned = biomes[x + z * 16];
                final int height = heightAt78(x, z);
                final Block surface = blocks[(x << 12 | z << 8) | height];
                final boolean present = assigned == p[0] || assigned == p[1];
                if (present) {
                    if (surface != assigned.topBlock) {
                        fail(tag + " present biome column (" + x + "," + z + ") not surfaced: " + surface);
                        return;
                    }
                    if (meta[(x << 12 | z << 8) | height] != (byte) assigned.field_150604_aj) {
                        fail(tag + " present biome column (" + x + "," + z + ") top meta drift");
                        return;
                    }
                    presentOk++;
                } else {
                    if (surface != Blocks.stone) {
                        if (assigned == null) {
                            nullBad++;
                        } else {
                            absentBad++;
                        }
                    }
                    // 缺席/null 列的整段主体都不得被换（base 换装同样是写入）
                    for (int y = height - 1; y > 3; y--) {
                        if (blocks[(x << 12 | z << 8) | y] != Blocks.stone) {
                            if (assigned == null) {
                                nullBad++;
                            } else {
                                absentBad++;
                            }
                            break;
                        }
                    }
                }
            }
        }
        check(presentOk > 0, tag + " 在场群系列已正常铺表层 cols=" + presentOk);
        check(absentBad == 0, tag + " 缺席群系列零铺表层（不写 top/filler/base）bad=" + absentBad);
        check(nullBad == 0, tag + " null 列零铺表层 bad=" + nullBad);

        final Map<String, Long> hist = new TreeMap<>();
        for (int i = 0; i < 65536; i++) {
            hist.merge(label(blocks[i], true), 1L, Long::sum);
        }
        // 在场群系 top/base 允许出现；缺席群系的 top/base 必须完全不出现
        check(!hist.containsKey(labelOf(BlocksGTSR.prosperityWastesTop))
            && !hist.containsKey(labelOf(BlocksGTSR.prosperityWastesBase))
            && !hist.containsKey(labelOf(BlocksGTSR.prosperitySwampTop))
            && !hist.containsKey(labelOf(BlocksGTSR.prosperitySwampBase)),
            tag + " 缺席群系方块（wastes/swamp top+base）全 chunk 零出现");
        check(!hist.containsKey("Blocks.grass") && !hist.containsKey("Blocks.dirt"),
            tag + " 无 grass/dirt");
        checkBiomePlane(tag, biomes);
    }

    /** NONE 对照：同一 JVM 内 4 群系全入账后必须每列都铺（防"门永久关闭"假绿）。 */
    private static void checkNormalDim78Control(BiomeGenBase[] p) throws Exception {
        final String tag = "NONE-control(dim78)";
        SurfaceHarness.recordProsperityAll(p);
        final GTSRBiomeAuthority authority = GTSRBiomeAuthority.forDimKey(GTSRBiomeAuthority.DIM_KEY_PROSPERITY);
        check(authority.degraded() == Degraded.NONE, tag + " degraded==NONE，实=" + authority.degraded());
        final Block[] blocks = new Block[65536];
        final byte[] meta = new byte[65536];
        final BiomeGenBase[] biomes = new BiomeGenBase[256];
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                biomes[x + z * 16] = p[(x * 3 + z * 5) & 3]; // 逐列异群系（同时喂转置面）
            }
        }
        final GTSRChunkProviderBase provider = SurfaceHarness.provider(true);
        SurfaceHarness.runRealSurface(provider, 0, 0, blocks, meta, biomes);
        int laid = 0;
        int drift = 0;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                final BiomeGenBase assigned = biomes[x + z * 16];
                if (blocks[(x << 12 | z << 8) | heightAt78(x, z)] == assigned.topBlock) {
                    laid++;
                } else {
                    drift++;
                }
            }
        }
        check(laid == 256 && drift == 0, tag + " 正常态 256/256 列全部铺表层（降级门零介入）drift=" + drift);
    }

    // ————— 共用 —————

    /** Chunk byte 平面写点：null 输入不抛异常、不写 plains(1)、不写 255 哨兵。 */
    private static void checkBiomePlane(String tag, BiomeGenBase[] biomes) throws Exception {
        // writeBiomePlane 是框架 protected static —— getMethod 只返回 public 成员，必须用
        // getDeclaredMethod + setAccessible（否则 NoSuchMethodException）
        final Method write = GTSRChunkProviderBase.class.getDeclaredMethod("writeBiomePlane", byte[].class,
            BiomeGenBase[].class);
        write.setAccessible(true);
        final byte[] plane = new byte[256];
        try {
            write.invoke(null, plane, biomes);
        } catch (ReflectiveOperationException e) {
            if (e.getCause() instanceof NullPointerException) {
                fail(tag + " provideChunk byte 平面写入在降级态抛 NPE（改造前崩溃态未修）");
                return;
            }
            throw e;
        }
        int plains = 0;
        int sentinel = 0;
        int zeros = 0;
        for (final byte b : plane) {
            final int id = b & 255;
            if (id == 1) {
                plains++;
            }
            if (id == 255) {
                sentinel++;
            }
            if (id == 0) {
                zeros++;
            }
        }
        check(plains == 0, tag + " byte 平面无 plains(id=1) plains=" + plains);
        check(sentinel == 0, tag + " byte 平面无 255 懒回填哨兵 sentinel=" + sentinel);
        final int expectedZero = tag.startsWith("EMPTY") ? 256 : countAbsent(biomes);
        check(zeros == expectedZero, tag + " 缺席列写 MISSING_BIOME_PLANE_ID(0) 数量=" + zeros + " 期望="
            + expectedZero);
    }

    private static int countAbsent(BiomeGenBase[] biomes) {
        int n = 0;
        for (int i = 0; i < 256; i++) {
            if (biomes == null || i >= biomes.length || biomes[i] == null) {
                n++;
            }
        }
        return n;
    }

    private static void checkWhitelist(String tag, Map<String, Long> hist, String... allowed) {
        final List<String> bad = new ArrayList<>();
        final java.util.Set<String> ok = new java.util.HashSet<>();
        for (final String a : allowed) {
            ok.add(a);
        }
        for (final String seen : hist.keySet()) {
            if (!ok.contains(seen)) {
                bad.add(seen + "=" + hist.get(seen));
            }
        }
        check(bad.isEmpty(), tag + " 降级态方块白名单（只允许 " + String.join(",", allowed) + "）越界项=" + bad);
    }

    private static String labelOf(Block b) {
        return label(b, false);
    }

    /**
     * 方块身份标签（<b>不</b>依赖 Blocks.grass 等未装配实例的可比性：未在白名单字段里出现的
     * 实例统一标为 {@code OTHER:<class>}，白名单断言因此对任何未知方块都敏感）。
     */
    private static String label(Block b, boolean collapseUnknown) {
        if (b == null) {
            return "(empty)";
        }
        if (b == Blocks.stone) {
            return "Blocks.stone";
        }
        if (b == Blocks.bedrock) {
            return "Blocks.bedrock";
        }
        if (b == BlocksGTSR.shatteredCorestone) {
            return "BlocksGTSR.shatteredCorestone";
        }
        final String known = KNOWN.get(b);
        if (known != null) {
            return known;
        }
        return collapseUnknown ? "OTHER:" + b.getClass()
            .getName() : "UNKNOWN:" + b.getClass()
                .getName();
    }

    private static final IdentityHashMap<Block, String> KNOWN = new IdentityHashMap<>();

    static {
        try {
            for (final java.lang.reflect.Field f : BlocksGTSR.class.getFields()) {
                if (f.getType() == Block.class && f.get(null) != null) {
                    KNOWN.putIfAbsent((Block) f.get(null), "BlocksGTSR." + f.getName());
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** dim78 列高（真实 {@code ProsperityTerrainProfile.heightAt}，chunk 原点 0,0 ⇒ 世界坐标 = 列内坐标）。 */
    private static int heightAt78(int x, int z) {
        return com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile.heightAt(
            SurfaceHarness.SEED, x, z);
    }

    private static void check(boolean ok, String msg) {
        if (ok) {
            pass(msg);
        } else {
            fail(msg);
        }
    }

    private static void pass(String msg) {
        passed++;
        System.out.println("DEG PASS: " + msg);
    }

    private static void fail(String msg) {
        failures++;
        System.out.println("DEG FAIL: " + msg);
    }
}
