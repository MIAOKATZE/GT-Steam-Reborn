package com.miaokatze.gtsr.common.dimension.framework;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.world.biome.BiomeGenBase;

import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority.BiomeId;

/**
 * 新维度群系抽象基类（dim1 S1 骨架，蓝本 GT5U BiomeEverglades.java:31-61）。
 * <p>
 * 构造统一完成：setBiomeName/setHeight/setTemperatureRainfall + 清空 4 个 spawn 列表 +
 * （<b>P9 / L7 起</b>）按 roster 幂等填充本维刷怪表。
 * getBiomeGrassColor/getBiomeFoliageColor 为子类覆写点（S2 草色 = 平铺 RGB；默认沿用原版
 * ColorizerGrass 温湿双线性）。
 * <p>
 * <b>P9 的行为变更（plan §2.1 L7「spawnLists 与 getPossibleCreatures 按带给权重」）</b>：
 * 改造前本构造器<b>无条件清空后不再填充</b>（dim1 裁剪范围无实体），配合
 * {@code GTSRWorldChunkManager.getBiomesToSpawnIn} 返回空表，构成"双机制零生物"。现在：
 * <ol>
 * <li>构造仍然清空（⇒ {@code Config.prosperityCreaturesEnabled=false} 时与 P8 基线<b>逐位一致</b>，
 * 也是幂等重填的前置）；</li>
 * <li>填充<b>不在构造里发生</b>，而是延迟到第一次读 spawn 列表时（{@link #getSpawnableList}）——
 * 因为构造发生在 {@code GTSRBiomeAuthority.recordAllocation} <b>之前</b>，那一刻本群系"是谁"
 * 还问不到账本（plan §2.1 L1 禁止 {@code instanceof} 链与 {@code id - idStart} 减法，故身份
 * 只能来自账本，见 {@link GTSRBiomeAuthority#identityOf}）；</li>
 * <li>填充内容由<b>注入式</b> {@link CreatureSpawnPolicy} 提供（{@link #setCreatureSpawnPolicy}）：
 * 框架保持零内容，dim78 的三档权重表在 {@code prosperity/entity/GTSRCreatureRoster} 声明一处。
 * 随坐标变化的调制（城窗 ×2）走同一策略的
 * {@link CreatureSpawnPolicy#scaleForLocation}，出口是 {@link #effectiveSpawnableList}。
 * dim79 的四个群系同样是本类子类，但 roster 未给它们声明任何生物 ⇒ 其 4 张表恒空 ⇒
 * 与 P8 基线一致（本片零跨界）。</li>
 * </ol>
 * <b>L7 禁止项（plan §2.1）</b>：不得用 {@code EntityRegistry.addSpawn} 的"漏传 biomes"形式
 * （那会污染主世界原版群系）；填充只写<b>本实例自己的</b> 4 张列表，故主世界在机制上不可达，
 * 另有 {@code tools/dim1/CreatureSpawnAuthorityCheck} 的"原版群系逐位不变"断言作回归网。
 * <p>
 * <b>槽位纪律（P1 L0 改写，plan §2.1 L0 + §7.1 U7 已锁定）</b>：super(biomeId) 构造即占
 * biomeList 槽且不可回退——调用方（群系持有者）必须先经 {@link #allocate(int, int)} 取得
 * <b>实际可用 id</b> 再构造。分配是<b>逐群系独立</b>的：每个群系有自己的首选 id，被占则向后
 * 顺延（允许非连续，不假设四连号）；<b>只有</b>扫描到 {@link #idCeiling()} 仍无空槽才算无槽，
 * 无槽由持有者记入 {@code GTSRBiomeAuthority} 账本并显式降级（degraded=SHORT/EMPTY），
 * 不再"静默跳过 + 空表回退 plains"。<b>P9 未动本段任何语义</b>
 * （{@link #isBiomeIdFree}/{@link #allocate}/{@link #HARD_ID_MAX} 逐字保持）。
 */
public abstract class GTSRBiomeBase extends BiomeGenBase {

    /**
     * 生物层策略钩子（L7；{@code null} = 尚未接线 ⇒ 保持 P1..P8 的清空态）。
     * <p>
     * 由内容侧（{@code prosperity/entity/GTSRCreatureRegistry.preInit}）在注册期安装一次。
     * 框架不持有任何权重数字（§2.4 判据 4「禁止同一数值多处漂移」），也不认识任何实体类。
     */
    public interface CreatureSpawnPolicy {

        /**
         * 把 {@code key} 这名册成员应有的刷怪条目写进群系实例自己的 4 张列表
         * （只能经 {@link GTSRBiomeBase#addCreatureSpawn} 写，别处拿不到 protected 列表）。
         */
        void fill(GTSRBiomeBase biome, BiomeId key);

        /**
         * 空间调制（L7「按带给权重」的另一半：城窗 ×2 这类<b>同群系内随坐标变化</b>的调制，
         * 默认原样返回）。实现方<b>不得</b>就地改 {@code declared} 里的条目（那是群系在册的声明表），
         * 需要改权重时必须新建 {@link BiomeGenBase.SpawnListEntry} 列表。
         */
        default List<SpawnListEntry> scaleForLocation(BiomeGenBase biome, EnumCreatureType creatureType,
            List<SpawnListEntry> declared, long worldSeed, int chunkX, int chunkZ) {
            return declared;
        }
    }

    private static volatile CreatureSpawnPolicy spawnPolicy;

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

    // ————————————————————————— L7 生物层（P9） —————————————————————————

    /**
     * 安装生物层策略钩子（生产链路只在 preInit 调一次）。
     * <p>
     * <b>语义边界</b>：幂等位在<b>群系实例</b>上（{@link #creatureSpawnsApplied}），所以换一个新
     * 策略只会影响"尚未填充过"的群系实例；已填过的不会重填（这正是判据 6「注册链重复执行
     * 不重复添加条目」的机制保证）。离线断言要观察"重跑注册链"，用新构造的群系实例，而不是
     * 换策略。
     */
    public static void setCreatureSpawnPolicy(CreatureSpawnPolicy policy) {
        spawnPolicy = policy;
    }

    /** 当前是否已接线生物层（只读；日志与离线断言用）。 */
    public static boolean isCreatureSpawnPolicyInstalled() {
        return spawnPolicy != null;
    }

    /** 一次性填充标记（每个群系实例一份；{@link #applyCreatureSpawnsOnce} 的幂等位）。 */
    private boolean creatureSpawnsApplied;

    /**
     * R1 声明真值账本（防线 4 的一部分）：{@link #addCreatureSpawn}（L7 唯一合法写入口）每写
     * 一条就登记「类型 → 实体类」。{@code EntityRegistry.addSpawn} 之类第三方注入直接改
     * {@code super.getSpawnableList} 返回的活列表、不经过本入口，故<b>不进账本</b>——账本即
     * 「本群系声明过什么」的判别依据（{@link #filterDeclaredSpawns}/{@link #retainDeclaredSpawns}）。
     * 框架仍不认识任何具体实体类（纯登记，零内容）。
     */
    private final Map<EnumCreatureType, Set<Class<?>>> declaredSpawnClasses = new EnumMap<>(EnumCreatureType.class);

    /**
     * 刷怪列表读取（vanilla {@code SpawnerAnimals} / Forge {@code EntityRegistry.addSpawn} /
     * 本仓 {@code GTSRChunkProviderBase.getPossibleCreatures} 的唯一入口）——
     * <b>P9 起在返回前做一次幂等填充</b>，见类注释的延迟理由。
     * <p>
     * 返回的是<b>声明表</b>（不含随坐标变化的调制）；要拿到某坐标的生效表请走
     * {@link #effectiveSpawnableList}。
     */
    @Override
    public List<SpawnListEntry> getSpawnableList(EnumCreatureType creatureType) {
        applyCreatureSpawnsOnce();
        return super.getSpawnableList(creatureType);
    }

    /**
     * 某 chunk 的<b>生效</b>刷怪表 = 声明表经 {@link CreatureSpawnPolicy#scaleForLocation} 调制
     * （L7 的唯一读出口，{@code getPossibleCreatures} 专用）。
     * <p>
     * <b>身份判定仍只问 L1 账本</b>（{@link GTSRBiomeAuthority#identityOf}）：不在册的群系实例
     * 一律原样交回声明表 ⇒ 框架对本维之外的群系零影响，且本方法内没有 {@code instanceof} 链、
     * 没有 {@code id - idStart} 减法、不读 Chunk byte 平面。
     */
    public static List<SpawnListEntry> effectiveSpawnableList(BiomeGenBase biome, EnumCreatureType creatureType,
        long worldSeed, int chunkX, int chunkZ) {
        if (biome == null) {
            return null;
        }
        final List<SpawnListEntry> declared = biome.getSpawnableList(creatureType);
        final CreatureSpawnPolicy policy = spawnPolicy;
        if (policy == null || declared == null || declared.isEmpty()) {
            return declared;
        }
        if (GTSRBiomeAuthority.identityOf(biome) == null) {
            return declared;
        }
        // R1 防线 4：活列表（声明表 + 可能的第三方 EntityRegistry.addSpawn 注入项）先按声明真值
        // 过滤出<b>副本</b>再交给策略调制——非声明实体类不进入生效表。这也覆盖了
        // GTSRChunkProviderBase.getPossibleCreatures 的出口（其唯一来源就是本方法）。
        final List<SpawnListEntry> owned = biome instanceof GTSRBiomeBase
            ? ((GTSRBiomeBase) biome).filterDeclaredSpawns(creatureType, declared)
            : declared;
        if (owned.isEmpty()) {
            return owned;
        }
        return policy.scaleForLocation(biome, creatureType, owned, worldSeed, chunkX, chunkZ);
    }

    /**
     * 声明真值过滤（R1 防线 4；只服务 {@link #effectiveSpawnableList} 与
     * {@code DimensionInterferenceGuard}，返回<b>新副本</b>，不动在册列表）：
     * 仅保留经 {@link #addCreatureSpawn} 声明过的「类型 × 实体类」条目。
     * <ul>
     * <li>活列表与声明完全一致 ⇒ 原实例返回（零分配，行为与过滤前逐位一致）；</li>
     * <li>有注入项 ⇒ 返回剔除后的新列表（声明表真值不被污染，副本口径）；</li>
     * <li>声明为空（dim79 四群系 / 生物层关闭 / 未接线）而活列表非空 ⇒ 返回空表
     * （本群系没有声明任何生物，第三方注入不得生效）。</li>
     * </ul>
     */
    private List<SpawnListEntry> filterDeclaredSpawns(EnumCreatureType creatureType, List<SpawnListEntry> live) {
        final Set<Class<?>> declared = this.declaredSpawnClasses.get(creatureType);
        if (declared == null || declared.isEmpty()) {
            return live.isEmpty() ? live : new ArrayList<>(0);
        }
        boolean hasForeign = false;
        for (final SpawnListEntry entry : live) {
            if (!declared.contains(entry.entityClass)) {
                hasForeign = true;
                break;
            }
        }
        if (!hasForeign) {
            return live;
        }
        final List<SpawnListEntry> out = new ArrayList<>(live.size());
        for (final SpawnListEntry entry : live) {
            if (declared.contains(entry.entityClass)) {
                out.add(entry);
            }
        }
        return out;
    }

    /**
     * 就地保留声明条目（R1 防线 3：{@code WorldEvent.PotentialSpawns} 守卫用）：
     * 从 {@code list}（事件携带的候选表）里移除一切非声明「类型 × 实体类」条目。
     * 声明为空时清空整表（语义同 {@link #filterDeclaredSpawns} 的空声明分支）。
     * 先做一次幂等填充（保证惰性注册在读取前完成），再按账本裁决。
     */
    public final void retainDeclaredSpawns(EnumCreatureType creatureType, List<SpawnListEntry> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        this.applyCreatureSpawnsOnce();
        final Set<Class<?>> declared = this.declaredSpawnClasses.get(creatureType);
        if (declared == null || declared.isEmpty()) {
            list.clear();
            return;
        }
        list.removeIf(entry -> entry == null || !declared.contains(entry.entityClass));
    }

    /**
     * 幂等填充（同一实例重复调用只填一次；未接线时<b>不置位</b>，等接线后再填）。
     * <p>
     * 身份来自 {@link GTSRBiomeAuthority#identityOf}（L1 账本，唯一出口）：未入账
     * （短表/空表降级的缺席成员）直接返回 ⇒ 保持清空态，不伪造刷怪表（plan §2.3 判据 2）。
     */
    public final void applyCreatureSpawnsOnce() {
        if (this.creatureSpawnsApplied) {
            return;
        }
        final CreatureSpawnPolicy policy = spawnPolicy;
        if (policy == null) {
            return;
        }
        this.creatureSpawnsApplied = true;
        final BiomeId key = GTSRBiomeAuthority.identityOf(this);
        if (key == null) {
            return;
        }
        policy.fill(this, key);
    }

    /** 本实例是否已经跑过填充（只读；离线断言用）。 */
    public final boolean areCreatureSpawnsApplied() {
        return this.creatureSpawnsApplied;
    }

    /**
     * 写一条刷怪条目进本群系自己的列表（L7 唯一写入口，{@link CreatureSpawnPolicy} 专用）。
     * <p>
     * {@code weight <= 0} 视为"该带不出现该生物"，<b>不</b>写入（vanilla
     * {@code WeightedRandom} 对 0 权条目仍会占位，写 0 会让"缺席"变成"必然不中但仍被抽中概率 0"
     * 的第三种状态，故在写入口直接挡掉）。
     */
    public final void addCreatureSpawn(EnumCreatureType creatureType, Class<? extends EntityLiving> entityClass,
        int weight, int minGroup, int maxGroup) {
        if (creatureType == null || entityClass == null || weight <= 0) {
            return;
        }
        listOf(creatureType).add(new SpawnListEntry(entityClass, weight, minGroup, maxGroup));
        // R1 声明真值账本：与写列表同一原子动作登记（见 declaredSpawnClasses 注释）
        Set<Class<?>> declared = this.declaredSpawnClasses.get(creatureType);
        if (declared == null) {
            declared = new HashSet<>();
            this.declaredSpawnClasses.put(creatureType, declared);
        }
        declared.add(entityClass);
    }

    /** {@code super.getSpawnableList} 的等价私有实现（<b>不</b>经被覆写的公开入口 ⇒ 无自递归）。 */
    private List<SpawnListEntry> listOf(EnumCreatureType creatureType) {
        if (creatureType == EnumCreatureType.monster) {
            return this.spawnableMonsterList;
        }
        if (creatureType == EnumCreatureType.creature) {
            return this.spawnableCreatureList;
        }
        if (creatureType == EnumCreatureType.waterCreature) {
            return this.spawnableWaterCreatureList;
        }
        return this.spawnableCaveCreatureList;
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
