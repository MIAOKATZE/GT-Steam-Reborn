package com.miaokatze.gtsr.common.dimension.framework;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import net.minecraft.world.ChunkPosition;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.WorldChunkManager;

import com.miaokatze.gtsr.common.dimension.framework.structure.GTSRWorldgenHash;
import com.miaokatze.gtsr.config.Config;

/**
 * 自写 BiomeProvider（dim1 S1，不走 GenLayer；plan §1.2/§5.4 口径）。
 * <p>
 * 群系选择纯函数 {@link #biomeAt(int, int)}，两级策略（dim78 修复 S-A2）：
 * <ol>
 * <li>def 挂有 {@link GTSRDimensionDef.BiomeSelector} 时（头部优先）：委托 selector 按空间连贯
 * 分区（如 {@link BiomeZoneSelector}）返回权重表下标；</li>
 * <li>否则维持原行为：对 def 群系权重表按权重展开成数组，用 splitmix 终结哈希（世界种子掺
 * chunk 坐标）取模——确定性、与 chunk 生成顺序无关。</li>
 * </ol>
 * <b>P1 行为变更（plan §2.3 判据 2「降级可见」/ §7.1 U7）：群系表为空时不再回退原版 plains，
 * 而是返回 {@code null} 表示"该坐标没有群系身份"</b>。此前空表→plains 的静默回退正是 dim79
 * 实机"整维 plains + 原版草方块"的最后一环（{@code BiomeGenBase} 构造默认 topBlock=草）。
 * 降级判定口径：
 * <ul>
 * <li>{@link #degraded()} 返回 {@link GTSRBiomeAuthority.Degraded}（NONE/SHORT/EMPTY），
 * 由 {@link GTSRBiomeAuthority} 的分配账本推导（L0 持有者写入）；</li>
 * <li>本类构造时把 {@link #biomeAt(int, int)} 作为 {@link GTSRBiomeAuthority.Source} 绑入 L1，
 * 使"坐标→群系身份"与 chunk 生成期出自同一张表；</li>
 * <li><b>表层是否铺方块属 P2 决策</b>（plan §5 P2「降级态一律不铺表层」）——本片只把标记正确
 * 传出，不改表层行为。注意 {@code GTSRChunkProviderBase.provideChunk:121-124} 目前直接读
 * {@code biomes[i].biomeID}，EMPTY 态需要其补 null 守卫（P2 的允许文件，本片刻意不动）。</li>
 * </ul>
 * 无 BiomeCache：哈希为 O(1) 纯函数，覆写 cleanupCache 为空操作。
 */
public class GTSRWorldChunkManager extends WorldChunkManager {

    private final long seed;
    /** 按权重展开的群系选择表（null = 空群系表，即 {@link GTSRBiomeAuthority.Degraded#EMPTY}）。 */
    private final BiomeGenBase[] weightedBiomes;
    /** 可选群系选择策略（S-A2；null = 原 per-chunk 均匀掷骰行为）。 */
    private final GTSRDimensionDef.BiomeSelector biomeSelector;
    /** def 群系表快照（selector 路径按权重表下标取用；与 {@link #selectorWeights} 下标对齐）。 */
    private final BiomeGenBase[] selectorTable;
    /** def 权重表快照（selector 路径入参）。 */
    private final int[] selectorWeights;
    /**
     * H-1 身份层委托（P6）：{@link #biomeSelector} 的方法引用形态，交给
     * {@link BiomeZoneSelector#bandIdentity} 做带尺度折算（null = 本维未挂 selector）。
     */
    private final BiomeZoneSelector.ZoneDelegate bandDelegate;
    /** 本维 macro 带尺度（chunk，已 {@link BiomeZoneSelector#normalizeMacroCell 合法性化}）。 */
    private final int macroCell;
    /** 本维 micro 强度层域分离盐输入（= def.seedSalt；匿名 def 为 0）。 */
    private final long microDomainSalt;
    /** 所属维度的 def key（L1 账本键；null = 匿名 def，仅离线自检会出现）。 */
    private final String dimKey;

    public GTSRWorldChunkManager(long seed, GTSRDimensionDef def) {
        this.seed = seed;
        List<BiomeGenBase> expanded = new ArrayList<>();
        BiomeGenBase[] table = null;
        int[] weights = null;
        long seedSalt = 0L;
        if (def != null) {
            List<BiomeGenBase> biomeTable = def.getBiomeTable();
            weights = def.getBiomeWeights();
            if (!biomeTable.isEmpty()) {
                table = biomeTable.toArray(new BiomeGenBase[0]);
            }
            for (int i = 0; i < biomeTable.size(); i++) {
                int weight = Math.max(1, weights[i]);
                for (int w = 0; w < weight; w++) {
                    expanded.add(biomeTable.get(i));
                }
            }
            this.dimKey = def.getKey();
            seedSalt = def.getSeedSalt();
        } else {
            this.dimKey = null;
        }
        this.weightedBiomes = expanded.isEmpty() ? null : expanded.toArray(new BiomeGenBase[0]);
        this.biomeSelector = def != null ? def.getBiomeSelector() : null;
        this.bandDelegate = this.biomeSelector == null ? null : this.biomeSelector::select;
        this.selectorTable = table;
        this.selectorWeights = weights;
        this.macroCell = BiomeZoneSelector.normalizeMacroCell(macroBandChunksFor(this.dimKey));
        this.microDomainSalt = seedSalt;
        // L1 绑定：身份解析复用本 manager 的采样函数（同表、同种子、同 selector）
        if (this.dimKey != null) {
            GTSRBiomeAuthority.bind(this.dimKey, def.getResolvedDimId(), this::biomeAt);
        }
    }

    /**
     * 本维 macro 带尺度（P6 H-1 接线；plan §7.1 已锁定 U2）。
     * <p>
     * 只有两维各自的 Config 键，<b>其它 dimKey/匿名 def 一律取 micro 口径（16）</b>——即"未接线的消费方
     * 保持改造前单层行为"，与 {@code tools/dim1/BiomeBandHierarchyCheck} 的"dim79 带尺度必须仍是 16"
     * 断言互为防线（dim79 的 Config 键默认 16，即便被误改也不影响其接线值语义，但会被该断言判红）。
     */
    private static int macroBandChunksFor(String dimKey) {
        if (GTSRBiomeAuthority.DIM_KEY_PROSPERITY.equals(dimKey)) {
            return Config.prosperityBiomeMacroBandChunks;
        }
        if (GTSRBiomeAuthority.DIM_KEY_SHATTERED.equals(dimKey)) {
            return Config.shatteredBiomeMacroBandChunks;
        }
        return BiomeZoneSelector.MICRO_CELL_CHUNKS;
    }

    /**
     * per-chunk 群系选择纯函数（确定性哈希驱动）。
     * <p>
     * 返回 {@code null} = 该维度群系表为空（{@link #degraded()} == {@code EMPTY}）。
     * <b>P1 起不再有 plains 回退</b>；调用方必须显式处理 null（表层决定权在 P2）。
     * def 挂有 selector 时头部优先走空间连贯分区，否则 per-chunk 均匀掷骰；输出口径不变
     * （仍整 chunk 单一群系）。
     * <p>
     * <b>P6（H-1 分层）</b>：selector 路径的身份现在按 <b>macro 带</b>解析
     * （{@link BiomeZoneSelector#bandIdentity}，带尺度见 {@link #macroBandChunksFor(String)}），
     * 不再逐 chunk 掷"本 chunk 属于哪个带"之外的东西；带尺度 == micro（16）时
     * {@code bandIdentity} 退化为直接委托，与改造前<b>逐位相同</b>（dim79 即此情形，零变化）。
     * L1 语义与降级三态一字未改（仍同一张表、同一 {@code bind}、同样的 null 口径）。
     */
    public BiomeGenBase biomeAt(int chunkX, int chunkZ) {
        if (this.weightedBiomes == null) {
            return null;
        }
        final int index = bandRosterIndex(chunkX, chunkZ);
        if (index >= 0) {
            return this.selectorTable[Math.floorMod(index, this.selectorTable.length)];
        }
        return this.weightedBiomes[hash(chunkX, chunkZ) % this.weightedBiomes.length];
    }

    /**
     * H-1 群系带身份下标（本维权重表/名册下标；P6 新增只读出口）。
     *
     * @return ∈ [0, biomeCount)；{@code -1} = 本维未挂 selector 或群系表为空（消费方按降级口径处理，
     *         与 {@link #biomeAt} 的 null 口径同源，<b>不</b>伪造身份）
     */
    public int bandRosterIndex(int chunkX, int chunkZ) {
        if (this.bandDelegate == null || this.selectorTable == null) {
            return -1;
        }
        return BiomeZoneSelector
            .bandIdentity(
                this.bandDelegate,
                this.seed,
                chunkX,
                chunkZ,
                this.selectorTable.length,
                this.selectorWeights,
                this.macroCell);
    }

    /** 本维 macro 带尺度（chunk，已合法性化；离线自检与日志用）。 */
    public int macroBandChunks() {
        return this.macroCell;
    }

    /**
     * micro 层变体/装饰强度系数（P6 新增只读出口；0.7/1.0/1.3，见
     * {@link BiomeZoneSelector#MICRO_STRENGTHS}）。
     * <p>
     * <b>不参与群系身份</b>，且本片<b>不接入</b>散布/装饰的 K 与权重（P5 已锁定，plan §5 P6 禁止越界）；
     * 消费方是 P7/P8 的变体选择与装饰强度。未挂 selector 或空表降级时返回中性 1.0F（不伪造强度）。
     */
    public float microStrengthAt(int chunkX, int chunkZ) {
        if (this.bandDelegate == null || this.selectorTable == null) {
            return 1.0F;
        }
        return BiomeZoneSelector.microStrength(this.seed, chunkX, chunkZ, this.microDomainSalt);
    }

    /**
     * 本维降级状态（L1 账本推导；未绑定 def key 的匿名 def 按"表空即 EMPTY、表非空即 NONE"）。
     */
    public GTSRBiomeAuthority.Degraded degraded() {
        if (this.dimKey == null) {
            return this.weightedBiomes == null
                ? GTSRBiomeAuthority.Degraded.EMPTY
                : GTSRBiomeAuthority.Degraded.NONE;
        }
        return GTSRBiomeAuthority.forDimKey(this.dimKey).degraded();
    }

    /** 是否为空表降级（表层/装饰消费侧的便捷判定）。 */
    public boolean isEmptyDegraded() {
        return this.weightedBiomes == null;
    }

    /**
     * 非负 31 位 chunk 掷骰（权重表取下标用）。
     * <p>
     * <b>P3（plan §2.1 L2「禁止手搓哈希」）</b>：算法体改走 {@link GTSRWorldgenHash}，
     * <b>输入形状与常数一字未改</b>——注意本方法的 x/z 乘子是
     * {@link GTSRWorldgenHash#CELL_MUL_X} 与 {@link GTSRWorldgenHash#SPLITMIX_INCREMENT}
     * 的<b>混搭</b>（改造前私有常数 {@code HASH_CONSTANT_Z} 取的是 splitmix64 的标准增量、
     * 被本仓当作 z 乘子使用；它<b>不等于</b> {@code cellSeed} 的 {@code CELL_MUL_Z}），
     * 故只能走通用入口 {@code mixSeed} 逐位承袭，不能并到 {@code cellSeed}。
     * 掩码（非负 31 位）也原样保留，取自 {@link GTSRWorldgenHash#NON_NEGATIVE_31}。
     */
    private int hash(int chunkX, int chunkZ) {
        final long h = GTSRWorldgenHash.mixSeed(
            this.seed,
            chunkX,
            chunkZ,
            GTSRWorldgenHash.CELL_MUL_X,
            GTSRWorldgenHash.SPLITMIX_INCREMENT,
            0L);
        return (int) (GTSRWorldgenHash.splitmix64(h) & GTSRWorldgenHash.NON_NEGATIVE_31);
    }

    @Override
    public BiomeGenBase getBiomeGenAt(int x, int z) {
        return biomeAt(x >> 4, z >> 4);
    }

    @Override
    public List<BiomeGenBase> getBiomesToSpawnIn() {
        return Collections.emptyList();
    }

    @Override
    public float[] getRainfall(float[] listToReuse, int x, int z, int width, int length) {
        if (listToReuse == null || listToReuse.length < width * length) {
            listToReuse = new float[width * length];
        }
        for (int dx = 0; dx < width; dx++) {
            for (int dz = 0; dz < length; dz++) {
                final BiomeGenBase biome = biomeAt((x + dx) >> 4, (z + dz) >> 4);
                // 空表降级（biome==null）不写湿度：保持数组槽位现值（新数组即 0.0F），
                // 不伪造 plains 湿度（plan §2.3 判据 2）
                if (biome != null) {
                    listToReuse[dx + dz * width] = biome.getIntRainfall() / 65536.0F;
                }
            }
        }
        return listToReuse;
    }

    @Override
    public BiomeGenBase[] getBiomesForGeneration(BiomeGenBase[] listToReuse, int x, int z, int width, int length) {
        if (listToReuse == null || listToReuse.length < width * length) {
            listToReuse = new BiomeGenBase[width * length];
        }
        // 空表降级时数组槽位为 null（P1 起不填 plains）；provider 侧对 null 列的处理归 P2
        for (int dx = 0; dx < width; dx++) {
            for (int dz = 0; dz < length; dz++) {
                listToReuse[dx + dz * width] = biomeAt((x + dx) >> 4, (z + dz) >> 4);
            }
        }
        return listToReuse;
    }

    @Override
    public BiomeGenBase[] loadBlockGeneratorData(BiomeGenBase[] oldList, int x, int z, int width, int depth) {
        return getBiomesForGeneration(oldList, x, z, width, depth);
    }

    @Override
    public BiomeGenBase[] getBiomeGenAt(BiomeGenBase[] listToReuse, int x, int z, int width, int length,
        boolean cacheFlag) {
        return getBiomesForGeneration(listToReuse, x, z, width, length);
    }

    @Override
    public boolean areBiomesViable(int x, int z, int range, List<BiomeGenBase> biomes) {
        int startX = x - range >> 2;
        int startZ = z - range >> 2;
        int spanX = (range * 2 >> 2) + 1;
        int spanZ = (range * 2 >> 2) + 1;
        for (int dx = 0; dx < spanX; dx++) {
            for (int dz = 0; dz < spanZ; dz++) {
                if (!biomes.contains(biomeAt((startX + dx << 2) >> 4, (startZ + dz << 2) >> 4))) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public ChunkPosition findBiomePosition(int x, int z, int range, List<BiomeGenBase> biomes, Random random) {
        return null;
    }

    @Override
    public void cleanupCache() {}
}
