package com.miaokatze.gtsr.common.dimension.framework.genlayer;

import net.minecraft.world.gen.layer.GenLayer;
import net.minecraft.world.gen.layer.IntCache;

import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeBase;

/**
 * GenLayer 链根层：对每格从入参群系 id 表<b>等权轮盘</b>选一个 id（dim78/dim79 GenLayer 化切片 A）。
 * <p>
 * 本类是纯新增链的<b>唯一随机源</b>——后续 {@link GTSRGenLayerChain} 的
 * {@code GenLayerZoom}/{@code GenLayerSmooth}/{@code GenLayerVoronoiZoom} 只复制/平滑/重采样父层
 * 输出，不发明新 id；因此整链的 id 值域完全由本类的入参表决定。
 * <p>
 * 逐格调用 {@code initChunkSeed(x, z)} 再 {@code nextInt}（GenLayer 标准 LCG，蓝本
 * {@code GenLayer.java:137-165}）⇒ 每格取值只依赖 (worldGenSeed, x, z)，与查询窗口位置/大小/顺序
 * 无关——这是 B1 接线时"三面同解"（chunk 生成、getBiomeGenAt、离线复算）依赖的纯函数语义。
 * 世界种子的注入走标准 {@link GenLayer#initWorldGenSeed(long)} 沿链传播（由
 * {@link GTSRGenLayerChain} 构造统一完成，本类 parent 为 null 即链根）。
 * <p>
 * id 值域约束（与 {@link GTSRBiomeBase#HARD_ID_MAX} 同口径）：构造期 fail fast——空表或任一 id
 * 超出 [0, 254] 直接抛 {@link IllegalArgumentException}（接线期编程错误，不是运行期数据错误）。
 * 255 是 vanilla 懒回填哨兵、id ≥ 256 会被 byte 平面静默别名，故上界取 254。
 */
public class GTSRGenLayerSelector extends GenLayer {

    /**
     * 根层域分离 baseSeed（GenLayer 构造器把它与链上其它层的盐一起混入 worldGenSeed；
     * 取 3000L 避开 vanilla 常用盐 1/10/100/1000/2000 家族，防止与同世界其它 GenLayer 链同型）。
     */
    public static final long SELECTOR_BASE_SEED = 3000L;

    /** 等权轮盘表（构造期防御拷贝，此后只读）。 */
    private final int[] biomeIds;

    /**
     * @param baseSeed GenLayer 标准 baseSeed（域分离盐；独立使用传 {@link #SELECTOR_BASE_SEED}）
     * @param biomeIds 候选群系 id 表（≥1 项，每项 ∈ [0, {@link GTSRBiomeBase#HARD_ID_MAX}]；
     *                 重复项会使该 id 权重翻倍——等权语义下调用方应传去重表）
     * @throws IllegalArgumentException biomeIds 为 null/空，或任一 id 越界（接线期 fail fast）
     */
    public GTSRGenLayerSelector(long baseSeed, int[] biomeIds) {
        super(baseSeed);
        if (biomeIds == null || biomeIds.length == 0) {
            throw new IllegalArgumentException("biomeIds must not be null or empty");
        }
        for (int i = 0; i < biomeIds.length; i++) {
            final int id = biomeIds[i];
            if (id < 0 || id > GTSRBiomeBase.HARD_ID_MAX) {
                throw new IllegalArgumentException(
                    "biomeIds[" + i + "]=" + id + " out of [0," + GTSRBiomeBase.HARD_ID_MAX + "]");
            }
        }
        this.biomeIds = biomeIds.clone();
    }

    /** 本层候选表快照（诊断/自检用；每次调用防御拷贝）。 */
    public int[] biomeIds() {
        return this.biomeIds.clone();
    }

    /**
     * 每格独立等权掷骰。输出布局沿用 vanilla 行主序：{@code out[x + z * areaWidth]}。
     */
    @Override
    public int[] getInts(int areaX, int areaZ, int areaWidth, int areaHeight) {
        final int[] out = IntCache.getIntCache(areaWidth * areaHeight);
        for (int z = 0; z < areaHeight; z++) {
            for (int x = 0; x < areaWidth; x++) {
                this.initChunkSeed(areaX + x, areaZ + z);
                out[x + z * areaWidth] = this.biomeIds[this.nextInt(this.biomeIds.length)];
            }
        }
        return out;
    }
}
