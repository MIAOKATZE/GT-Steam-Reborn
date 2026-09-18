import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.profiler.Profiler;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.ISaveHandler;

import com.miaokatze.gtsr.common.blocks.BlocksGTSR;

/**
 * <b>P3 判据 2 + 判据 3 载体：L2 高度场 / 列扫 / 哈希站点的「改造前后逐位一致」对拍</b>
 * （plan §5 P3 / §2.1 L2 / §2.4 判据 4；§8 风险 4「去重引入行为漂移 ⇒ 逐字节对拍」）。
 * <p>
 * 与 {@code SurfaceByteParityDump}（判据 1，chunk 字节级）互补：本工具在<b>单个函数</b>粒度对拍，
 * 因而能指出<b>哪一个</b>站点漂移，而不只是"地表变了"。
 * <p>
 * <b>用法（同一份源码，两棵树各跑一次，diff {@code ^SITE} 行为空即绿）</b>：
 * BASE = 本片开工前快照树，AFTER = 当前树；一键复跑见 {@code tools/dim1/surface_checks.sh --parity}
 * 的 [5] 段（编译两份 class + 跑两遍 + diff）。
 * <p>
 * <b>为什么一份源码能同时服务两棵树</b>：每个站点按<b>候选清单</b>（先"自有实现"、后"合并件"）
 * 反射解析第一个可用方法，全程字符串反射、零编译期引用被测方法名——
 * <ul>
 * <li>BASE 树里 {@code ProsperitySurfaceScatter#findSurfaceY} 等自有份存在 ⇒ 命中自有份；</li>
 * <li>AFTER 树里自有份已删 ⇒ 命中框架唯一件 {@code GTSRChunkProviderBase#findSurfaceY}。</li>
 * </ul>
 * 于是<b>同一个 SITE 键</b>在两棵树分别测"改造前那份实现"与"改造后的合并件"，入参完全相同，
 * {@code diff} 为空即"同一输入同一输出逐位相同"得证。命中实况打进 {@code # resolved} 头行，
 * 便于人工核对没有把两侧都指到同一个实现（那会退化为恒等自证）。
 * <p>
 * 覆盖 22 个 SITE 键：高度场 2（各 12288 点，≥1 万口径）、噪声核 4（含合并前后的
 * {@code valueNoise}/{@code hashUnit}）、列扫 6（合并 5 份 #2-#6 + <b>未合并</b>的 #7 严格件）、
 * 哈希 8（{@code mixColumn}、两维 {@code bedrockTop}、{@code BiomeZoneSelector.mix}、
 * {@code GTSRWorldChunkManager.hash}、{@code CityPlanner.mix}、{@code CityVariants.mix}
 * ＋既有三公开入口回归键）、数组内联扫 2（#9/#10 两条静态缝，摘要覆盖整块 65536 数组）。
 * <p>
 * <b>不在本工具执行、改由字节码等值证明的两处</b>：{@code GTSRDimTeleporter#findSurfaceY}（#1，
 * 字段类型是 {@code WorldServer}，离线合成代价不成比例）与
 * {@code WorldGenRunawaySingularity#findSurfaceY}（#8）——两文件本片<b>只加注释、零代码改动</b>，
 * 由 {@code surface_checks.sh} 的 [6] 段用 {@code javap -p -c} 对 BASE/AFTER 反汇编逐行比对闭合
 * （字节码相同 ⇒ 同一输入必得同一输出，比采样更强的判据）。
 * <p>
 * 退出码：0 = 全部站点可解析且采样数达标；非 0 = 有站点缺失或高度采样不足 1 万。
 */
public class SurfaceYParityCheck {

    /** 高度场每维采样点数（判据 2 要求 ≥1 万；3 seed × 64 × 64 = 12288）。 */
    private static final int HEIGHT_AXES = 64;
    private static final int HEIGHT_SEEDS = 3;
    private static final int HEIGHT_POINTS = HEIGHT_AXES * HEIGHT_AXES * HEIGHT_SEEDS;

    /** 合成世界列扫网格边长（64×64 = 每站点 4096 列，判据 2 的 ≥1 万口径按 6 个列扫站点合计）。 */
    private static final int COLUMN_SIDE = 64;

    /** 通用哈希站点采样规模（每站点 4096 组输入）。 */
    private static final int HASH_SAMPLES = 4096;

    private static final String P_PROFILE = "com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile";
    private static final String S_PROFILE = "com.miaokatze.gtsr.common.dimension.shattered.ShatteredTerrainProfile";
    private static final String HASH = "com.miaokatze.gtsr.common.dimension.framework.structure.GTSRWorldgenHash";
    private static final String PROV_BASE = "com.miaokatze.gtsr.common.dimension.framework.GTSRChunkProviderBase";
    private static final String ZONE = "com.miaokatze.gtsr.common.dimension.framework.BiomeZoneSelector";
    private static final String MGR = "com.miaokatze.gtsr.common.dimension.framework.GTSRWorldChunkManager";
    private static final String SCATTER = "com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperitySurfaceScatter";
    private static final String MACHINE = "com.miaokatze.gtsr.common.dimension.prosperity.ruins.RuinedMachinePlacer";
    private static final String DECOR = "com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperityDecorPlacer";
    private static final String OUTPOST = "com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperityOutpostPlacer";
    private static final String SH_DECOR = "com.miaokatze.gtsr.common.dimension.shattered.ShatteredDecorPlacer";
    private static final String SH_RUINS = "com.miaokatze.gtsr.common.dimension.shattered.WorldGenShatteredRuins";
    private static final String P_PROV = "com.miaokatze.gtsr.common.dimension.prosperity.ChunkProviderProsperityRuins";
    private static final String S_PROV = "com.miaokatze.gtsr.common.dimension.shattered.ChunkProviderShatteredGrounds";
    private static final String PLANNER = "com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityPlanner";
    private static final String VARIANTS = "com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityVariants";

    private static final PrintStream OUT = openUtf8Stdout();

    private static PrintStream openUtf8Stdout() {
        try {
            return new PrintStream(new FileOutputStream(FileDescriptor.out), true, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return System.out;
        }
    }

    /** SITE 键 → 实际命中的实现（{@code 类#方法/入参数}）。 */
    private static final Map<String, String> RESOLVED = new TreeMap<>();

    private static int failures;
    private static long totalAssertions;

    public static void main(String[] args) throws Exception {
        SurfaceHarness.initVanillaBlocks();
        SurfaceHarness.blockFamily();
        final BiomeGenBase[] prosperity = SurfaceHarness.prosperityBiomes();
        final BiomeGenBase[] shattered = SurfaceHarness.shatteredBiomes();
        SurfaceHarness.recordAllAllocations(prosperity, shattered);

        final World world = synthWorld();

        siteHeight("height.prosperity_heightAt", P_PROFILE + "#heightAt");
        siteHeight("height.shattered_heightAt", S_PROFILE + "#heightAt");

        siteNoise("noise.prosperity_valueNoise", P_PROFILE + "#valueNoise", HASH + "#valueNoise");
        siteNoise("noise.shattered_valueNoise", S_PROFILE + "#valueNoise", HASH + "#valueNoise");
        siteNoise("noise.prosperity_hashUnit", P_PROFILE + "#hashUnit", HASH + "#unitNoise");
        siteNoise("noise.shattered_hashUnit", S_PROFILE + "#hashUnit", HASH + "#unitNoise");

        siteColumnScan("scan.scatter_surface_y", SCATTER, world);
        siteColumnScan("scan.machine_surface_y", MACHINE, world);
        siteColumnScan("scan.decor_surface_y", DECOR, world);
        siteColumnScan("scan.outpost_surface_y", OUTPOST, world);
        siteColumnScan("scan.shattered_decor_surface_y", SH_DECOR, world);
        siteColumnScan("scan.shattered_ruins_surface_y_STRICT", SH_RUINS, world);

        siteHashLong("hash.zone_mix", new String[] { ZONE + "#mix", HASH + "#splitmix64" });
        siteHashColumn("hash.provider_mixColumn", new String[] { PROV_BASE + "#mixColumn" });
        siteHashColumn("hash.prosperity_bedrockTop", new String[] { P_PROV + "#bedrockTop",
            PROV_BASE + "#bedrockTop" });
        siteHashColumn("hash.shattered_bedrockTop", new String[] { S_PROV + "#bedrockTop",
            PROV_BASE + "#bedrockTop" });
        siteHashSalt("hash.city_planner_mix", PLANNER + "#mix");
        siteHashSalt("hash.city_variants_mix", VARIANTS + "#mix");
        siteChunkManagerHash();
        siteLegacyHashEntries();

        siteArrayScan("surface.array_scan_dim78", P_PROV, prosperity, true);
        siteArrayScan("surface.array_scan_dim79", S_PROV, shattered, false);

        for (final Map.Entry<String, String> e : RESOLVED.entrySet()) {
            OUT.println("# resolved " + e.getKey() + " -> " + e.getValue());
        }
        OUT.println("HEIGHT_POINTS_PER_PROFILE=" + HEIGHT_POINTS + " (判据 2 下限 10000: "
            + (HEIGHT_POINTS >= 10000 ? "OK" : "TOO FEW") + ")");
        OUT.println("SITES=" + RESOLVED.size() + " missing=" + failures);
        OUT.flush();
        if (failures != 0 || HEIGHT_POINTS < 10000) {
            System.out.println("SURFACE Y PARITY: FAIL missing=" + failures);
            System.exit(1);
        }
        System.out.println("SURFACE Y PARITY: SITES=" + RESOLVED.size() + " assertions=" + totalAssertions
            + " heightPointsPerProfile=" + HEIGHT_POINTS);
    }

    // ─────────────────────────────── 站点 ───────────────────────────────

    /** 高度场站点：{@code heightAt(long,int,int)}，3 seed × 64×64 步长 40 的网格。 */
    private static void siteHeight(String key, String spec) throws Exception {
        final Method m = pick(key, new String[] { spec });
        final Acc acc = new Acc();
        for (int si = 0; si < HEIGHT_SEEDS; si++) {
            final long seed = seedOf(si);
            for (int i = 0; i < HEIGHT_AXES; i++) {
                for (int j = 0; j < HEIGHT_AXES; j++) {
                    final int x = -3000 + i * 40;
                    final int z = -2000 + j * 40;
                    acc.addLong(((Number) m.invoke(null, seed, x, z)).intValue());
                }
            }
        }
        report(key, acc);
    }

    /** 噪声站点：{@code (long,double,double)} 与 {@code (long,int,int)} 两种形状共用入参。 */
    private static void siteNoise(String key, String ownSpec, String mergedSpec) throws Exception {
        final Method m = pick(key, new String[] { ownSpec, mergedSpec });
        final boolean doubleArgs = m.getParameterTypes()[2] == double.class;
        final Acc acc = new Acc();
        for (int si = 0; si < 2; si++) {
            final long seed = seedOf(si);
            for (int x = -500; x <= 500; x += 7) {
                for (int z = -400; z <= 400; z += 11) {
                    final Object r = doubleArgs
                        ? m.invoke(null, seed, (double) x / 48.0D, (double) z / 48.0D)
                        : m.invoke(null, seed, x, z);
                    acc.addDouble(((Double) r).doubleValue());
                }
            }
        }
        report(key, acc);
    }

    /** 列扫站点：对合成世界的每一列求 surfaceY（含悬块/洞穴顶/空气材质/全空病理列）。 */
    private static void siteColumnScan(String key, String owner, World world) throws Exception {
        final Method m = pick(key, new String[] { owner + "#findSurfaceY", PROV_BASE + "#findSurfaceY" });
        final Acc acc = new Acc();
        for (int x = 0; x < COLUMN_SIDE; x++) {
            for (int z = 0; z < COLUMN_SIDE; z++) {
                acc.addLong(((Number) m.invoke(null, world, x, z)).intValue());
            }
        }
        report(key, acc);
    }

    /** {@code long -> long} 形状的手搓哈希站点。 */
    private static void siteHashLong(String key, String[] candidates) throws Exception {
        final Method m = pick(key, candidates);
        final Acc acc = new Acc();
        for (int i = 0; i < HASH_SAMPLES; i++) {
            acc.addLong(((Number) m.invoke(null, 0x1234567L * i + 7)).longValue());
        }
        report(key, acc);
    }

    /** {@code (long,int,int) -> long/int} 形状的列向手搓哈希站点。 */
    private static void siteHashColumn(String key, String[] candidates) throws Exception {
        final Method m = pick(key, candidates);
        final Acc acc = new Acc();
        for (int i = 0; i < HASH_SAMPLES; i++) {
            final Object r = m.invoke(null, 0x9E3779B97F4A7C15L * i, i - 2000, (i & 255) * 13);
            acc.addLong(toLong(r));
        }
        report(key, acc);
    }

    /** {@code (long,long) -> long} 形状的城市盐分路手搓哈希站点。 */
    private static void siteHashSalt(String key, String spec) throws Exception {
        final Method m = pick(key, new String[] { spec });
        final Acc acc = new Acc();
        for (int i = 0; i < HASH_SAMPLES; i++) {
            acc.addLong(((Number) m.invoke(null, 0xD1B54A32L * i - 3, (long) (i * 31) - 5)).longValue());
        }
        report(key, acc);
    }

    /**
     * {@code GTSRWorldChunkManager.hash(int,int)}：私有<b>实例</b>方法，方法体只读 {@code seed}
     * 字段 ⇒ 用 {@code Unsafe.allocateInstance} 绕开 {@code WorldChunkManager} 构造后直写 seed
     * （口径同 {@code SurfaceHarness.mockWorld}）。
     */
    private static void siteChunkManagerHash() throws Exception {
        final Method m = pick("hash.chunkmgr_hash", new String[] { MGR + "#hash" });
        final sun.misc.Unsafe u = SurfaceHarness.unsafe();
        final Class<?> cls = Class.forName(MGR);
        final Object mgr = u.allocateInstance(cls);
        final Field seedField = fieldOf(cls, "seed");
        u.putLong(mgr, u.objectFieldOffset(seedField), SurfaceHarness.SEED);
        final Acc acc = new Acc();
        for (int i = 0; i < HASH_SAMPLES; i++) {
            acc.addLong(((Number) m.invoke(mgr, i - 2048, i * 7 - 1000)).intValue());
        }
        report("hash.chunkmgr_hash", acc);
    }

    /** 既有三个公开入口的回归键（本片把 {@code cellSeed}/{@code blockSeed} 内联字面量具名化）。 */
    private static void siteLegacyHashEntries() throws Exception {
        final Method chunk = pick("hash.legacy_public_entries", new String[] { HASH + "#chunkSeed" });
        final Method cell = pickExisting(HASH + "#cellSeed");
        final Method block = pickExisting(HASH + "#blockSeed");
        final Acc acc = new Acc();
        for (int i = 0; i < HASH_SAMPLES; i++) {
            final long seed = 0x9E3779B97F4A7C15L * i;
            acc.addLong((Long) chunk.invoke(null, seed, i - 2000, i * 3));
            acc.addLong((Long) cell.invoke(null, seed, i - 2000, i * 3, (long) (i - 77)));
            acc.addLong((Long) block.invoke(null, seed, i - 2000, i & 255, i * 3));
        }
        report("hash.legacy_public_entries", acc);
    }

    /**
     * 数组内联列扫站点（审计 #9/#10）：真实 {@code applyBiomeSurface} 静态缝，摘要覆盖整块
     * 65536 数组的方块标签 + meta ⇒ filler 深度哈希与三段状态机任何一位变化都会变红。
     */
    private static void siteArrayScan(String key, String owner, BiomeGenBase[] biomes, boolean prosperity)
        throws Exception {
        final Method m = pick(key, new String[] { owner + "#applyBiomeSurface" });
        final Labels labels = new Labels();
        final Acc acc = new Acc();
        for (int si = 0; si < 2; si++) {
            final long seed = seedOf(si);
            final Block[] blocks = new Block[65536];
            final byte[] meta = new byte[65536];
            final int baseX = si == 0 ? 0 : 320;
            final int baseZ = si == 0 ? -64 : 96;
            fillColumnTerrain(blocks, seed, prosperity);
            final BiomeGenBase[] cols = new BiomeGenBase[256];
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    cols[x + z * 16] = biomes[(x + z) % biomes.length];
                }
            }
            m.invoke(null, seed, baseX, baseZ, blocks, meta, cols);
            for (int i = 0; i < 65536; i++) {
                acc.addLong(labels.codeOf(blocks[i]));
                acc.addLong(meta[i]);
            }
        }
        if (!labels.unmapped.isEmpty()) {
            OUT.println("# unmapped " + key + " " + labels.unmapped);
        }
        report(key, acc);
    }

    /** 内联扫的输入地形：主体填到 {@code 60 + (x*3+z*5+seed 高位)%17}，偶数列加一格悬空主体。 */
    private static void fillColumnTerrain(Block[] blocks, long seed, boolean prosperity) {
        final Block body = prosperity ? Blocks.stone : BlocksGTSR.shatteredCorestone;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                final int column = x << 12 | z << 8;
                blocks[column] = Blocks.bedrock;
                final int h = 60 + Math.floorMod(x * 3 + z * 5 + (int) (seed >>> 24), 17);
                for (int y = 1; y <= h; y++) {
                    blocks[column | y] = body;
                }
                if ((x & 1) == 0) {
                    blocks[column | (h + 3)] = body; // 悬空主体格：触发 dim79 skipDanglingFiller 分支
                }
            }
        }
    }

    // ─────────────────────────────── 工具件 ───────────────────────────────

    private static long seedOf(int i) {
        return i == 0 ? SurfaceHarness.SEED : SurfaceHarness.SEED ^ (0x5A5AL * (i + 1));
    }

    private static long toLong(Object r) {
        if (r instanceof Long) {
            return ((Long) r).longValue();
        }
        if (r instanceof Integer) {
            return ((Integer) r).intValue();
        }
        if (r instanceof Double) {
            return Double.doubleToLongBits(((Double) r).doubleValue());
        }
        throw new IllegalStateException("unexpected return type " + r.getClass().getName());
    }

    private static void report(String key, Acc acc) {
        OUT.println("SITE " + key + " n=" + acc.n + " digest=" + hex(acc.v));
        totalAssertions += acc.n;
    }

    /** 按候选清单反射解析第一个可用方法（含私有/保护），并登记命中项。 */
    private static Method pick(String key, String[] candidates) {
        for (final String spec : candidates) {
            final Method m = lookup(spec);
            if (m != null) {
                RESOLVED.put(
                    key,
                    m.getDeclaringClass()
                        .getName() + "#" + m.getName() + "/" + m.getParameterTypes().length);
                return m;
            }
        }
        OUT.println("MISSING " + key + " candidates=" + Arrays.toString(candidates));
        failures++;
        throw new IllegalStateException("no implementation resolvable for site " + key);
    }

    /** 只有一个候选、两棵树都必须存在的解析。 */
    private static Method pickExisting(String spec) {
        final Method m = lookup(spec);
        if (m == null) {
            throw new IllegalStateException("required method missing: " + spec);
        }
        return m;
    }

    private static Method lookup(String spec) {
        final int hash = spec.indexOf('#');
        final String cls = spec.substring(0, hash);
        final String name = spec.substring(hash + 1);
        try {
            Class<?> c = Class.forName(cls);
            while (c != null) {
                for (final Method m : c.getDeclaredMethods()) {
                    if (m.getName().equals(name) && !m.isSynthetic()) {
                        m.setAccessible(true);
                        return m;
                    }
                }
                c = c.getSuperclass();
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // 该候选在本树不存在（例如合并后自有份已删）——交给 pick 继续下一个候选
        }
        return null;
    }

    private static Field fieldOf(Class<?> c, String name) throws NoSuchFieldException {
        Class<?> k = c;
        while (k != null) {
            try {
                final Field f = k.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                k = k.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name + " not found under " + c.getName());
    }

    private static String hex(long v) {
        final StringBuilder sb = new StringBuilder(16);
        for (int i = 60; i >= 0; i -= 4) {
            sb.append(Character.forDigit((int) ((v >>> i) & 15), 16));
        }
        return sb.toString();
    }

    /** 64 位 FNV-1a 变体累加器：逐值折叠，任何一位输出变化都会改变摘要。 */
    private static final class Acc {

        private long v = 0xCBF29CE484222325L;
        private long n;

        void addLong(long bits) {
            v ^= bits;
            v *= 0x100000001B3L;
            v ^= (v >>> 29);
            n++;
        }

        void addDouble(double d) {
            addLong(Double.doubleToLongBits(d));
        }
    }

    /**
     * 方块实例 → 稳定序号（与 {@code SurfaceByteParityDump} 同口径：{@code BlocksGTSR}/{@code Blocks}
     * 的 public static {@code Block} 字段名按<b>字典序</b>编号，与反射 {@code getFields()} 顺序无关）。
     */
    private static final class Labels {

        private final IdentityHashMap<Block, Integer> identity = new IdentityHashMap<>();
        private final Map<String, Integer> byName = new TreeMap<>();
        private final Map<String, Long> unmapped = new TreeMap<>();
        private int emptyIndex;
        private int unmappedIndex;

        Labels() throws IllegalAccessException {
            final List<String> names = new ArrayList<>();
            names.add("(empty)");
            names.add("(unmapped)");
            collect(BlocksGTSR.class, "BlocksGTSR", names);
            collect(Blocks.class, "Blocks", names);
            Collections.sort(names, Comparator.<String>naturalOrder());
            for (int i = 0; i < names.size(); i++) {
                byName.put(names.get(i), i);
            }
            emptyIndex = names.indexOf("(empty)");
            unmappedIndex = names.indexOf("(unmapped)");
            bind(BlocksGTSR.class, "BlocksGTSR");
            bind(Blocks.class, "Blocks");
        }

        private static void collect(Class<?> holder, String prefix, List<String> names)
            throws IllegalAccessException {
            for (final Field f : holder.getFields()) {
                if (f.getType() == Block.class && f.get(null) != null) {
                    names.add(prefix + "." + f.getName());
                }
            }
        }

        private void bind(Class<?> holder, String prefix) throws IllegalAccessException {
            for (final Field f : holder.getFields()) {
                if (f.getType() != Block.class) {
                    continue;
                }
                final Object v = f.get(null);
                final Integer idx = v == null ? null : byName.get(prefix + "." + f.getName());
                if (idx != null) {
                    identity.put((Block) v, idx.intValue());
                }
            }
        }

        int codeOf(Block b) {
            if (b == null) {
                return emptyIndex;
            }
            final Integer i = identity.get(b);
            if (i == null) {
                unmapped.merge(b.getClass().getName(), 1L, Long::sum);
                return unmappedIndex;
            }
            return i.intValue();
        }
    }

    // ─────────────────────────── 合成世界（列扫输入）───────────────────────────

    /**
     * 只为列扫提供 {@code getBlock}/{@code isAirBlock}/{@code getActualHeight} 的合成世界。
     * <p>
     * {@code World} 构造器在离线 JVM 不可安全执行 ⇒ 与 {@code SurfaceHarness.mockWorld} 同一口径，
     * 用 {@code Unsafe.allocateInstance} 跳过构造器体，再直赋 {@link #grid}/{@link #airLike}。
     * 列内容按下述<b>确定性规则</b>在两棵树生成<b>完全相同</b>的输入（这是"同一输入"的前提）：
     * <ul>
     * <li>一般列：y=1..(70 + (x*5+z*3)%12) 实心石，y=0 基岩；</li>
     * <li>x==3：顶上方 2 格再放一格（<b>悬块</b>——框架件与 #7 严格件的差异列）；</li>
     * <li>x==5：顶部下方挖 3 格（<b>洞穴顶</b>）；</li>
     * <li>x==7：顶格为空气<b>材质</b>方块（走 {@code Material.air} 分支，非 null 分支）；</li>
     * <li>x==9：全空列（兜底 -1 路径）；</li>
     * <li>z==11 且 x 为偶数：顶推到 254（上边界）。</li>
     * </ul>
     */
    private static World synthWorld() throws Exception {
        final sun.misc.Unsafe u = SurfaceHarness.unsafe();
        final SynthWorld w = (SynthWorld) u.allocateInstance(SynthWorld.class);
        final Field gridField = SynthWorld.class.getDeclaredField("grid");
        final Field airField = SynthWorld.class.getDeclaredField("airLike");
        gridField.setAccessible(true);
        airField.setAccessible(true);
        // 真构造（走 Block(Material) 构造器）——allocateInstance 会跳过构造器体，
        // 那样 materialValue 仍是 null，Material.air 分支就测不到。
        final Block airLike = new AirBlock();
        final Block[][][] grid = new Block[COLUMN_SIDE][256][COLUMN_SIDE];
        for (int x = 0; x < COLUMN_SIDE; x++) {
            for (int z = 0; z < COLUMN_SIDE; z++) {
                final int kind = x % 12; // 病理列按 12 一周期铺开，保证每类都有足量样本
                if (kind == 9) {
                    continue; // 全空列：兜底 -1 路径
                }
                grid[x][0][z] = Blocks.bedrock;
                int top = 70 + (x * 5 + z * 3) % 12;
                if (z == COLUMN_SIDE - 1 && (x & 1) == 0) {
                    top = 254; // 上边界（不越界，两实现仍应一致）
                }
                if (kind == 11) {
                    top = 255; // 顶到世界高：只有 #7 严格件会因"上方非空"一路下扫到 -1
                }
                for (int y = 1; y <= top; y++) {
                    grid[x][y][z] = Blocks.stone;
                }
                if (kind == 3) {
                    grid[x][top + 2][z] = Blocks.stone; // 悬块
                } else if (kind == 5) {
                    for (int y = top; y > top - 3; y--) {
                        grid[x][y][z] = null; // 洞穴顶
                    }
                } else if (kind == 7) {
                    grid[x][top][z] = airLike; // 空气材质（非 null，走 Material.air 分支）
                }
            }
        }
        gridField.set(w, grid);
        airField.set(w, airLike);
        return w;
    }

    /** 抽象方法补齐的 {@code World} 子类（承袭 {@code SurfaceHarness.HarnessWorld} 三条抽象）。 */
    private static final class SynthWorld extends World {

        private Block[][][] grid;
        private Block airLike;

        private SynthWorld() {
            super((ISaveHandler) null, (String) null, (WorldProvider) null, (WorldSettings) null, (Profiler) null);
        }

        @Override
        public Block getBlock(int x, int y, int z) {
            if (x < 0 || z < 0 || x >= COLUMN_SIDE || z >= COLUMN_SIDE || y < 0) {
                return null;
            }
            if (y > 255) {
                // 镜像 vanilla World.getBlock 的"界外视为实心"口径（1.7.10 返回 bedrock 哨兵），
                // 否则 #7 严格件与框架件在 y=255 顶列上的真实差异会被抹平（假绿）。
                return Blocks.bedrock;
            }
            return grid[x][y][z];
        }

        @Override
        public boolean isAirBlock(int x, int y, int z) {
            final Block b = getBlock(x, y, z);
            return b == null || b == airLike || b.getMaterial() == Material.air;
        }

        @Override
        public int getActualHeight() {
            return 256;
        }

        @Override
        protected IChunkProvider createChunkProvider() {
            return null;
        }

        @Override
        protected int func_152379_p() {
            return 0;
        }

        @Override
        public Entity getEntityByID(int entityId) {
            return null;
        }
    }

    /** 空气材质方块（验证 {@code Material.air} 分支——{@code Blocks.air} 在离线被换成了石头实例）。 */
    private static final class AirBlock extends Block {

        private AirBlock() {
            super(Material.air);
        }
    }
}
