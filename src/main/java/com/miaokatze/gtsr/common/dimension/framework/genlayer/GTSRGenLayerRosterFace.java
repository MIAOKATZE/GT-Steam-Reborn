package com.miaokatze.gtsr.common.dimension.framework.genlayer;

import java.util.Arrays;

import net.minecraft.world.biome.BiomeGenBase;

import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeBase;

/**
 * <b>seed 作用域的 coarse 身份面</b>：方块坐标 → L1 维内名册下标（dim1 P17 S-A 新增，
 * 需求"增加地形差异，不同地形的效果不一样"的地势分档取数口）。
 * <p>
 * <b>为什么需要它</b>：{@code ProsperityTerrainProfile.heightAt} 要按群系改振幅，但该类既不能读
 * 方块也不能读 {@code World}（城/机器/废墟/outpost 四族与地形填充共用这一个纯函数——"高度红线"）。
 * 本类将取数限定为一条<b>只依赖 (chainSeed, 坐标) 的纯函数</b>，于是身份可以进入高度面而红线不破：
 * <ul>
 * <li>零方块读取：只用 {@link GTSRBiomeAuthority} 的<b>内存分配账本</b>（id ↔ 名册成员）+
 * {@link GTSRGenLayerChain}（纯 int 层），全类无 {@code World}/{@code Chunk}/{@code getBlock} 任一处
 * 引用（源级断言见 {@code tools/dim1/P17TerrainReliefCheck} 的 REDLINE 组）；</li>
 * <li>同格同值：身份出口与 {@code GTSRWorldChunkManager.biomeAt} / {@code CityPlanner.bandIndexAt}
 * 用的是<b>同一个</b> {@link GTSRGenLayerChain#biomeAtCoarse(int, int)} 粗层面（1:4，非 voronoi 细面）
 * 与同一份名册序 id 表 ⇒ 任一坐标在本类、在 L1 {@code ordinalAt}、在城门上得到同一个名册下标
 * （行为级对拍由 {@code P17TerrainReliefCheck} 的 SOURCE 组在真实账本上逐点钉）；</li>
 * <li>每调用一条短命链（与 {@code CityPlanner.bandIndexAt} 同款形状）：<b>零共享可变链实例</b>、
 * 零缓存 ⇒ 不与 manager 常驻链互踩 {@code chunkSeed}，也不引入第二真值源。</li>
 * </ul>
 * <p>
 * <b>降级口径（唯一出口，不伪造身份）</b>：名册一个成员都没配槽（{@link GTSRBiomeAuthority.Degraded#EMPTY}）
 * 时返回 {@link #NO_IDENTITY} = {@code -1}。调用方<b>必须</b>把 -1 当作"档表里的默认档"处理，
 * <b>不得</b>写"身份 == 某群系 ⇒ 抑制"这类等值判断（plan §2 第 8 条纪律；实测真实配槽后
 * 524288 chunk 上 -1 = 0 次，见 {@code plan/tmp/p17-a/A-biome-scale.md} §4）。
 * <p>
 * <b>坐标契约</b>：入参是<b>方块坐标</b>，粗层换算 {@code >> 2} 由链自己完成（与
 * {@link GTSRGenLayerChain#biomeAtCoarse(int, int)} 逐字同一口径）；{@code chainSeed} 必须是
 * {@code worldSeed ^ def.seedSalt}（维度域分离），与 manager/城门同式——盐值传错不会崩，但会得到
 * 一个与身份面无关的排列，故 {@code ProsperityTerrainProfile} 把盐申报成公开常数并由判据钉同值。
 */
public final class GTSRGenLayerRosterFace {

    /** 身份不可得（该维名册零配槽 = EMPTY 降级）；调用方走档表默认档。 */
    public static final int NO_IDENTITY = -1;

    private GTSRGenLayerRosterFace() {}

    /**
     * 该维名册<b>已配槽</b>成员的 biome id 表（按 {@link GTSRBiomeAuthority.BiomeId#rosterIndex()}
     * 顺序；与 {@code CityPlanner.bandIndexAt} 的取表<b>同序</b>（⇒ 两条链的入参数组逐位同序），
     * 越界 id 的剔除口径则对齐 {@code GTSRWorldChunkManager}（下方 68-70 行的注释给出为何取后者）。
     *
     * @return 长度 = 已配槽且 id ∈ [0, {@link GTSRBiomeBase#HARD_ID_MAX}] 的成员数（正常态 4）；
     *         空表 = 该维无身份面
     */
    public static int[] allocatedRosterIds(String dimKey) {
        return allocatedRosterIds(GTSRBiomeAuthority.forDimKey(dimKey), dimKey);
    }

    private static int[] allocatedRosterIds(GTSRBiomeAuthority authority, String dimKey) {
        final int[] ids = new int[authority.rosterSize()];
        int kept = 0;
        for (final GTSRBiomeAuthority.BiomeId key : GTSRBiomeAuthority.BiomeId.values()) {
            if (!dimKey.equals(key.dimKey()) || !key.inSelector()) {
                // v1.20.39 T8 真缺陷修复：只吃 selector 成员（plan §3.3）。T5 把 sanzu 记进账本后，
                // 不过滤会让本链面变成 5 元等权链，与 manager 的 def 表 4 元链同坐标不同解——
                // "同格同值"不变量（本类 javadoc / BBH A1+C1 / P17 SOURCE 组）被静默破坏。
                continue;
            }
            final BiomeGenBase biome = authority.biomeOf(key);
            if (biome == null) {
                continue;
            }
            // 越界 id 剔除（与 GTSRWorldChunkManager 构链处的同一防御同形，不是第二份真值：
            // GTSRBiomeBase 的 254 硬上界使生产态不可达，但若真出现，本类必须像 manager 那样
            // "剔除降级"而不是让 GTSRGenLayerChain 构造期 IAE 打进 worldgen 的高度函数里。
            if (biome.biomeID < 0 || biome.biomeID > GTSRBiomeBase.HARD_ID_MAX) {
                continue;
            }
            ids[kept++] = biome.biomeID;
        }
        return kept == ids.length ? ids : Arrays.copyOf(ids, kept);
    }

    /**
     * 方块坐标 → 名册下标（coarse 身份面）。
     *
     * @param chainSeed {@code worldSeed ^ def.seedSalt}（域分离后的链种子）
     * @param dimKey    维 def key（{@link GTSRBiomeAuthority#DIM_KEY_PROSPERITY} 等）
     * @return {@code [0, rosterSize)} 内的名册下标；{@link #NO_IDENTITY} = 无身份面（EMPTY）
     */
    public static int rosterIndexAt(long chainSeed, String dimKey, int blockX, int blockZ) {
        final GTSRBiomeAuthority authority = GTSRBiomeAuthority.forDimKey(dimKey);
        final int[] ids = allocatedRosterIds(authority, dimKey);
        if (ids.length == 0) {
            return NO_IDENTITY;
        }
        final int id = new GTSRGenLayerChain(chainSeed, ids).biomeAtCoarse(blockX, blockZ);
        // 链出参 id → 名册下标（账本反查；与 CityPlanner.bandIndexAt 的第二跳同一口径——用
        // rosterIndex() 而不是数组下标，SHORT 降级态（部分成员没抢到槽）下依然正确）。
        for (final GTSRBiomeAuthority.BiomeId key : GTSRBiomeAuthority.BiomeId.values()) {
            if (!dimKey.equals(key.dimKey())) {
                continue;
            }
            final BiomeGenBase biome = authority.biomeOf(key);
            if (biome != null && biome.biomeID == id) {
                return key.rosterIndex();
            }
        }
        // 理论不可达：链输出已被 sanitizeOne 钳制 ∈ 入参 id 集合，而入参 id 全部来自本维账本。
        // 真到了这里说明名册在链构造与反查之间被并发改动——按无身份处理（默认档），不伪造。
        return NO_IDENTITY;
    }
}
