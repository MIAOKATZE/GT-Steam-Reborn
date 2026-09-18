import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.security.MessageDigest;
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
import com.miaokatze.gtsr.common.dimension.framework.GTSRChunkProviderBase;
import com.miaokatze.gtsr.common.dimension.framework.GTSRDimensionDef;
import com.miaokatze.gtsr.common.dimension.framework.GTSRWorldChunkManager;

/**
 * <b>正常态表层逐字节对拍载体</b>（P2 核心回归判据；本工具<b>自身不断言</b>，只输出确定性摘要清单）。
 * <p>
 * 用法：同一份工具源码，分别在<b>改造前</b>（P1 工作树快照 → {@code temp/p2-base/classes}）与
 * <b>改造后</b>（当前工作树 → {@code temp/p2/classes}）的 class 上各跑一遍，两份输出
 * {@code diff} 为空 ⇒ 「表层链上收框架 + 降级门 + 转置统一不改正常态行为」得证
 * （plan §5 P2 / §8 风险 4「去重引入行为漂移 ⇒ 逐字节对拍」）。
 * <p>
 * 运行路径（共用 {@code SurfaceHarness} 的离线装配）：真实 {@code generateTerrain}（反射调
 * provider 覆写实现）→ 真实 {@code GTSRWorldChunkManager.loadBlockGeneratorData}
 * （生产者下标口径 {@code dx + dz*16}）→ 真实 {@code ChunkProviderXxx.applyBiomeSurface}
 * （provideChunk 事件段之后的同一实现体；Forge 事件段离线不可靠故跳过，两跑口径相同）。
 * <p>
 * 正常态前提：8 个名册成员全部 {@code recordAllocation} 进 {@link GTSRBiomeAuthority}
 * ⇒ {@code degraded=NONE} ⇒ P2 的降级门必须完全不介入（摘要头部会打印实际 degraded）。
 * <p>
 * 每 chunk 一行 {@code sha256(65536 × (label 序号 int32 + meta byte))} + 方块直方图。
 * label 取 {@code BlocksGTSR} / {@code Blocks} 的 public static 字段名，按<b>字典序</b>赋号
 * （与反射 {@code getFields()} 顺序无关 ⇒ 跨构建可复现）；出现未命名方块实例时记入
 * {@code # unmapped=} 行（两跑都必须为空，否则对拍退化为恒等自证）。
 * <p>
 * 命令见文件末 {@link #USAGE}。
 */
public class SurfaceByteParityDump {

    private static final String USAGE = "java SurfaceByteParityDump [chunksPerAxis=16]  (> 文件)";

    private static int grid = 16;

    private static final IdentityHashMap<Block, String> LABELS = new IdentityHashMap<>();
    private static final Map<String, Integer> CODES = new TreeMap<>();
    private static final Map<String, Long> UNMAPPED = new TreeMap<>();

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            grid = Integer.parseInt(args[0]);
        }
        SurfaceHarness.initVanillaBlocks();
        SurfaceHarness.blockFamily();
        buildLabels();

        final BiomeGenBase[] p = SurfaceHarness.prosperityBiomes();
        final BiomeGenBase[] s = SurfaceHarness.shatteredBiomes();
        SurfaceHarness.recordAllAllocations(p, s);

        final PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, "UTF-8");
        out.println("# SurfaceByteParityDump grid=" + grid + "x" + grid + " seed=" + SurfaceHarness.SEED
            + " usage=" + USAGE);
        digestDim(out, true, p);
        digestDim(out, false, s);
        out.println("# unmapped=" + UNMAPPED);
        out.flush();
    }

    private static String degraded(String dimKey) {
        return GTSRBiomeAuthority.forDimKey(dimKey)
            .degraded()
            .name();
    }

    private static void digestDim(PrintStream out, boolean is78, BiomeGenBase[] biomes) throws Exception {
        final String dimKey = is78 ? GTSRBiomeAuthority.DIM_KEY_PROSPERITY : GTSRBiomeAuthority.DIM_KEY_SHATTERED;
        final GTSRDimensionDef def = SurfaceHarness.def(is78, biomes,
            is78 ? SurfaceHarness.prosperityWeights() : SurfaceHarness.shatteredWeights());
        final GTSRWorldChunkManager mgr = SurfaceHarness.manager(is78, def);
        out.println("DIMHDR dim=" + (is78 ? "78" : "79") + " degraded=" + degraded(dimKey) + " bound="
            + GTSRBiomeAuthority.forDimKey(dimKey)
                .isBound() + " allocated=" + GTSRBiomeAuthority.forDimKey(dimKey).allocatedCount());
        final GTSRChunkProviderBase provider = SurfaceHarness.provider(is78);
        final java.lang.reflect.Method gen = SurfaceHarness.generateTerrain();
        final java.lang.reflect.Method surface = SurfaceHarness.surfaceSeam(provider.getClass());

        final MessageDigest md = MessageDigest.getInstance("SHA-256");
        final ByteArrayOutputStream buf = new ByteArrayOutputStream(65536 * 5);
        final DataOutputStream dos = new DataOutputStream(buf);
        final Map<String, Long> dimHist = new TreeMap<>();
        int chunks = 0;
        int half = grid / 2;
        for (int cx = -half; cx < -half + grid; cx++) {
            for (int cz = -half; cz < -half + grid; cz++) {
                final Block[] blocks = new Block[65536];
                final byte[] meta = new byte[65536];
                gen.invoke(provider, cx, cz, blocks, meta, null);
                final BiomeGenBase[] array = mgr.loadBlockGeneratorData(null, cx * 16, cz * 16, 16, 16);
                surface.invoke(null, SurfaceHarness.SEED, cx * 16, cz * 16, blocks, meta, array);

                buf.reset();
                final Map<String, Long> counts = new TreeMap<>();
                for (int i = 0; i < 65536; i++) {
                    final String label = labelOf(blocks[i]);
                    dos.writeInt(CODES.get(label));
                    dos.writeByte(meta[i]);
                    counts.merge(label, 1L, Long::sum);
                }
                dos.flush();
                out.println("CHUNK dim=" + (is78 ? "78" : "79") + " cx=" + cx + " cz=" + cz + " sha="
                    + hex(md.digest(buf.toByteArray())) + " hist=" + hist(counts));
                for (final Map.Entry<String, Long> e : counts.entrySet()) {
                    dimHist.merge(e.getKey(), e.getValue(), Long::sum);
                }
                chunks++;
            }
        }
        out.println("AGG dim=" + (is78 ? "78" : "79") + " chunks=" + chunks + " hist=" + hist(dimHist));
    }

    private static String hist(Map<String, Long> counts) {
        final StringBuilder sb = new StringBuilder();
        for (final Map.Entry<String, Long> e : counts.entrySet()) {
            if (sb.length() > 0) {
                sb.append('|');
            }
            sb.append(e.getKey())
                .append(':')
                .append(e.getValue());
        }
        return sb.toString();
    }

    private static String labelOf(Block b) {
        if (b == null) {
            return "(empty)";
        }
        final String label = LABELS.get(b);
        if (label == null) {
            UNMAPPED.merge(b.getClass()
                .getName(), 1L, Long::sum);
            return "(unmapped)";
        }
        return label;
    }

    private static void buildLabels() throws Exception {
        final List<String> names = new ArrayList<>();
        names.add("(empty)");
        names.add("(unmapped)");
        collect(BlocksGTSR.class, "BlocksGTSR", names);
        collect(Blocks.class, "Blocks", names);
        names.sort(String::compareTo);
        int next = 0;
        for (final String n : names) {
            CODES.put(n, next++);
        }
    }

    private static void collect(Class<?> holder, String prefix, List<String> names) throws IllegalAccessException {
        for (final Field f : holder.getFields()) {
            if (f.getType() != Block.class) {
                continue;
            }
            final Object v = f.get(null);
            if (v == null) {
                continue;
            }
            final String label = prefix + "." + f.getName();
            LABELS.putIfAbsent((Block) v, label);
            names.add(label);
        }
    }

    private static String hex(byte[] bytes) {
        final StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (final byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 15, 16))
                .append(Character.forDigit(b & 15, 16));
        }
        return sb.toString();
    }
}
