package com.miaokatze.gtsr.common.dimension.framework;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import net.minecraft.world.ChunkPosition;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.WorldChunkManager;

import com.miaokatze.gtsr.common.dimension.framework.structure.GTSRWorldgenHash;

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
    /** 所属维度的 def key（L1 账本键；null = 匿名 def，仅离线自检会出现）。 */
    private final String dimKey;

    public GTSRWorldChunkManager(long seed, GTSRDimensionDef def) {
        this.seed = seed;
        List<BiomeGenBase> expanded = new ArrayList<>();
        BiomeGenBase[] table = null;
        int[] weights = null;
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
        } else {
            this.dimKey = null;
        }
        this.weightedBiomes = expanded.isEmpty() ? null : expanded.toArray(new BiomeGenBase[0]);
        this.biomeSelector = def != null ? def.getBiomeSelector() : null;
        this.selectorTable = table;
        this.selectorWeights = weights;
        // L1 绑定：身份解析复用本 manager 的采样函数（同表、同种子、同 selector）
        if (this.dimKey != null) {
            GTSRBiomeAuthority.bind(this.dimKey, def.getResolvedDimId(), this::biomeAt);
        }
    }

    /**
     * per-chunk 群系选择纯函数（确定性哈希驱动）。
     * <p>
     * 返回 {@code null} = 该维度群系表为空（{@link #degraded()} == {@code EMPTY}）。
     * <b>P1 起不再有 plains 回退</b>；调用方必须显式处理 null（表层决定权在 P2）。
     * def 挂有 selector 时头部优先走空间连贯分区，否则 per-chunk 均匀掷骰；输出口径不变
     * （仍整 chunk 单一群系）。
     */
    public BiomeGenBase biomeAt(int chunkX, int chunkZ) {
        if (this.weightedBiomes == null) {
            return null;
        }
        if (this.biomeSelector != null && this.selectorTable != null) {
            final int index = this.biomeSelector
                .select(this.seed, chunkX, chunkZ, this.selectorTable.length, this.selectorWeights);
            return this.selectorTable[Math.floorMod(index, this.selectorTable.length)];
        }
        return this.weightedBiomes[hash(chunkX, chunkZ) % this.weightedBiomes.length];
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
