package com.miaokatze.gtsr.common.dimension.framework;

import java.util.function.IntConsumer;

import net.minecraft.world.biome.BiomeGenBase;

/**
 * 新维度群系抽象基类（dim1 S1 骨架，蓝本 GT5U BiomeEverglades.java:31-61）。
 * <p>
 * 构造统一完成：setBiomeName/setHeight/setTemperatureRainfall + 清空 4 个 spawn 列表
 * （裁剪范围无实体，plan §1 范围红线）。getBiomeGrassColor/getBiomeFoliageColor 为子类
 * 覆写点（S2 草色 = 平铺 RGB；默认沿用原版 ColorizerGrass 温湿双线性）。
 * <p>
 * <b>槽位纪律（P1 L0 改写，plan §2.1 L0 + §7.1 U7 已锁定）</b>：super(biomeId) 构造即占
 * biomeList 槽且不可回退——调用方（群系持有者）必须先经 {@link #allocate(int, int)} 取得
 * <b>实际可用 id</b> 再构造。分配是<b>逐群系独立</b>的：每个群系有自己的首选 id，被占则向后
 * 顺延（允许非连续，不假设四连号）；<b>只有</b>扫描到 {@link #idCeiling()} 仍无空槽才算无槽，
 * 无槽由持有者记入 {@code GTSRBiomeAuthority} 账本并显式降级（degraded=SHORT/EMPTY），
 * 不再"静默跳过 + 空表回退 plains"。
 */
public abstract class GTSRBiomeBase extends BiomeGenBase {

    protected GTSRBiomeBase(int biomeId, String biomeName, Height height, float temperature, float rainfall) {
        super(biomeId);
        this.setBiomeName(biomeName);
        this.setHeight(height);
        this.setTemperatureRainfall(temperature, rainfall);
        this.spawnableMonsterList.clear();
        this.spawnableCreatureList.clear();
        this.spawnableWaterCreatureList.clear();
        this.spawnableCaveCreatureList.clear();
    }

    /**
     * biome id 的<b>机制级硬上界</b>（P1 / plan §3 新发现，U7 已锁定）。
     * <p>
     * 依据（本轮 javap 实测 {@code net.minecraft.world.chunk.Chunk#getBiomeGenForWorldCoords}
     * 字节码，即 {@code Chunk.java:1400-1411} 段）：chunk 的群系平面是
     * {@code blockBiomeArray : byte[]}，读回时做 {@code & 255}，故
     * <ul>
     * <li>id ≥256 会被<b>静默别名</b>（256→0=ocean、260→4=plains 等），注册成功但游戏内解析成
     * 他方群系——不是报错，是错数据；</li>
     * <li>255 是 vanilla 的<b>懒回填哨兵</b>：命中 255 时 {@code Chunk} 会回调
     * {@code WorldChunkManager.getBiomeGenAt} 并把结果 {@code bastore} <b>写回落盘字节</b>；
     * 占用 255 的群系会被静默改写，故亦不可用。</li>
     * </ul>
     * 因此可用 id 上界 = 254。EndlessIDs 一类把 {@code biomeList} 扩到 65536 的 mod
     * <b>不等于</b>高位可用——扩的只是注册表，byte 平面约束照旧。
     */
    public static final int HARD_ID_MAX = 254;

    /** {@link #allocate(int, int)} 的"无槽"返回值（-1 不是合法 biome id）。 */
    public static final int NO_SLOT = -1;

    /**
     * 实际扫描上界 = {@code min(}{@link #HARD_ID_MAX}{@code , 注册表实际表长 - 1)}。
     * <p>
     * 表长经 Forge 加的 {@code BiomeGenBase.getBiomeGenArray()} 读取（vanilla 的
     * {@code biomeList} 是 private，且被 EndlessIDs 之类 mod 扩容后长度会变），
     * 故不再写死 256；无论表长多大，硬上界 254 始终生效（{@link #HARD_ID_MAX}）。
     * 表为 null（类初始化早期）时保守回退 0，等价于"无槽可用"。
     */
    public static int idCeiling() {
        final BiomeGenBase[] table = BiomeGenBase.getBiomeGenArray();
        if (table == null) {
            return 0;
        }
        return Math.min(HARD_ID_MAX, table.length - 1);
    }

    /** biomeList 槽位空闲校验（配槽前调用；返回 true 才允许 new 该群系）。 */
    public static boolean isBiomeIdFree(int biomeId) {
        return biomeId >= 0 && biomeId <= idCeiling() && BiomeGenBase.getBiome(biomeId) == null;
    }

    /**
     * 配槽原语（P1 L0，plan §5 P1 接口形状）：从 {@code preferredId} 起<b>升序</b>扫描到
     * {@code min(maxId, }{@link #idCeiling()}{@code )}，返回首个空闲 id；整段无空槽返回
     * {@link #NO_SLOT}。
     * <p>
     * 语义要点：① 允许<b>非连续</b>结果（逐群系独立调用本原语，四群系不保证连号）；
     * ② 扫描方向与上界写死为常量（升序 + {@link #HARD_ID_MAX}），可被离线断言；
     * ③ 本方法<b>只读</b>注册表，占槽仍由构造发生（调用方成功 new 之后该槽自然被占，
     * 故同维度内后续群系会顺延到更远的空槽，永不撞号）。
     *
     * @param preferredId 首选 id（来自 Config 的 {@code *BiomeIdStart + 群系序号}）；大于
     *                    {@link #idCeiling()} 时直接判无槽（不产生 ≥255 的 id）
     * @param maxId       本次扫描上界（Config 只能收紧，函数内部再夹一次硬上界）
     * @return 实际可用 id，或 {@link #NO_SLOT}
     */
    public static int allocate(int preferredId, int maxId) {
        return allocate(preferredId, maxId, null);
    }

    /**
     * 配槽原语 + owner 快照回调（L8：每个"扫到时已被占"的槽都回调一次 id，供持有者打印占用者
     * 群系名；回调为 null 时静默）。扫描本身与 {@link #allocate(int, int)} 完全一致。
     */
    public static int allocate(int preferredId, int maxId, IntConsumer onSlotOccupied) {
        final int ceiling = idCeiling();
        final int from = Math.max(0, preferredId);
        final int to = Math.min(maxId, ceiling);
        for (int id = from; id <= to; id++) {
            if (BiomeGenBase.getBiome(id) == null) {
                return id;
            }
            if (onSlotOccupied != null) {
                onSlotOccupied.accept(id);
            }
        }
        return NO_SLOT;
    }

    /**
     * 槽位占用者身份快照（owner 快照日志用；<b>不</b>用于群系身份判定——那是
     * {@code GTSRBiomeAuthority} 的职责）。{@code gtsr:} 前缀标明是我方已注册群系（顺延途中
     * 会撞上本维度先行群系自己占的槽，与外部 mod 抢占必须可区分）。
     *
     * @return 占用者群系名，或 {@code "unknown"}（槽被占但取不到实例 / 越界）
     */
    public static String occupantName(int biomeId) {
        final BiomeGenBase occupant = BiomeGenBase.getBiome(biomeId);
        if (occupant == null) {
            return "unknown";
        }
        return (occupant instanceof GTSRBiomeBase ? "gtsr:" : "other:") + occupant.biomeName;
    }

    /**
     * 草色覆写点：子类按需返回固定 RGB（签名对齐 vanilla getBiomeGrassColor(int, int, int)）。
     * 消费端（草方块 tint）走方块 colorMultiplier 邻域均值（S2 落地，BlockDarkWorldPollutedDirt 样板）。
     */
    // @Override public int getBiomeGrassColor(int x, int y, int z) { return ...; }

    /** 叶色覆写点：同 getBiomeGrassColor。 */
    // @Override public int getBiomeFoliageColor(int x, int y, int z) { return ...; }
}
