package com.miaokatze.gtsr.common.dimension.framework;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.world.biome.BiomeGenBase;

import com.miaokatze.gtsr.main.GTSteamReborn;

/**
 * L1 群系权威——<b>群系身份的唯一出口</b>（dim78 体系化重构 P1，plan §2.1 L1 + §4 横切件 +
 * §7.1 U7 裁决）。
 * <p>
 * 存在的理由：改造前身份判定有两套并存约定——① {@code instanceof} 链（provider 表层/空气Lookup），
 * ② {@code biomeID - Config.*BiomeIdStart} 减法（{@code ProsperityWorldGenerator.biomeWeight}）。
 * 两者都隐含"四群系连号且 id 段唯一"的前提，而 P1 之后槽位是<b>逐群系顺延、允许非连续</b>的，
 * 减法约定立刻失真（顺延到 186 的群系被算成越界 ⇒ 权重恒回退 1.0），{@code instanceof} 链又无法
 * 表达"这个群系到底有没有注册上"。本片把出口收敛成一个：
 * <ul>
 * <li><b>写入方</b>：L0 持有者（{@code ProsperityBiomes} / {@code ShatteredBiomes}）在配槽成功/
 * 失败时调用 {@link #recordAllocation} / {@link #recordNoSlot}，形成分配账本；</li>
 * <li><b>绑定方</b>：{@link GTSRWorldChunkManager} 构造时把自身的空间采样绑进来
 * （{@link #bind}），保证 L1 与 L0 出自同一张群系表；</li>
 * <li><b>消费方</b>：只调 {@link #ordinalAt(int, int)} 拿 {@code {群系枚举, ordinal, degraded}}。</li>
 * </ul>
 * <b>硬约束（plan §2.1 L1 禁止项）</b>：本类<b>不读</b> {@code Chunk} 保存的 byte biome id——
 * 解析走 {@link Source}（确定性纯函数采样），因此不受 byte 平面 255 哨兵回填、id ≥256 别名、
 * 旧区块 NBT 字节这三类失真影响。消费方也不得再用 {@code instanceof} 链或 {@code id - idStart}
 * 减法自行判定身份。
 * <p>
 * 已知边界（P2/P2b 已收口登记）：provider 表层 {@code instanceof} 链与 {@code fillerMetaOf}/
 * {@code baseBlockOf}（P2b 起 {@code ChunkProviderProsperityRuins.identityOf} 两跳）、
 * {@code ProsperityAirLookup}（P2b 起 {@code ordinalAt} 口径）、{@code ShatteredBiomes.skyFogColorFor}
 * （P2 起 {@code identityOf} 口径）均已并本类。
 * <p>
 * <b>P9（L7）收口一处</b>：{@code GTSRChunkProviderBase.getPossibleCreatures} 原来经
 * {@code World.getBiomeGenForCoords} 读 Chunk byte 平面来决定刷怪表（P7c 时代登记为"已知边界、
 * 归 P9"），现已改走本类的 {@link #ordinalAt(int, int)}（确定性纯函数采样），byte 平面读面在
 * <b>刷怪</b>这条路上已不存在。
 * <p>
 * 仍走旧口径的只剩三处——{@code BlockProsperityRustLeaves:74} / {@code BlockProsperitySurface:112} /
 * {@code BlockProsperityTuft:105} 三处树叶与草的<b>取色</b>路径（经
 * {@code World.getBiomeGenForCoords} 读 byte 平面；正常态与本类同源，故不影响生成，
 * 但在短表/空表降级态与旧区块上会取到他方群系的颜色）。三处均挂 plan 裁决项 U-A
 * （P13 旧路径删除与收束时一并裁决）。
 * 本片收口 L1 本体、{@code ProsperityWorldGenerator} 与上述表层/空气/天空色三处消费面。
 */
public final class GTSRBiomeAuthority {

    /** dim78 def key（与 {@code CommonProxy} 构造 {@link GTSRDimensionDef} 处的字面量一致）。 */
    public static final String DIM_KEY_PROSPERITY = "prosperity-ruins";
    /** dim79 def key（同上）。 */
    public static final String DIM_KEY_SHATTERED = "shattered-lands";

    /**
     * 维度级降级状态（plan §2.3 判据 2「降级可见」；用户只需 grep {@code [GTSR]} 即可分辨三态）。
     */
    public enum Degraded {
        /** 正常：名册内每个群系都拿到了槽位。 */
        NONE,
        /** 短表级：部分群系拿到槽位（群系表非空但不全）——生成可用，权重与层次退化。 */
        SHORT,
        /** 空表级：一个群系都没拿到槽位——群系表为空，表层不得铺任何方块（P2 消费本标记）。 */
        EMPTY
    }

    /**
     * 本模组全部自定义群系的<b>身份枚举</b>（L1 输出口径）。
     * <p>
     * {@link #ordinal} 是<b>该维度名册内</b>的下标（0..roster-1），与 def 权重表 /
     * {@code ProsperityWorldGenerator.MACHINE_WEIGHTS} 等消费表同口径；<b>不是</b> biome id，
     * 也<b>不是</b> {@code Enum#ordinal()}（枚举把两维串在一起，全局序号无消费语义）。
     * 新增群系必须在此登记——这是"名册单一真值"的 L1 侧投影。
     */
    public enum BiomeId {

        RUSTED_STEPPE(DIM_KEY_PROSPERITY, 0, true),
        GEARWORK_FOREST(DIM_KEY_PROSPERITY, 1, true),
        BRASS_WASTES(DIM_KEY_PROSPERITY, 2, true),
        FUMAROLE_SWAMP(DIM_KEY_PROSPERITY, 3, true),
        /**
         * 遗忘之川（v1.20.39 T5 第 5 成员，名册下标 4）。<b>不进 selector 等权名册</b>——
         * {@code CommonProxy}/GenLayer 链的 4 家名册不动 ⇒ 链身份面（{@link #ordinalAt} 的
         * Source 采样）永远解析不到它；其平面列由 populate 后置写入
         * （{@code ChunkProviderProsperityRuins.onPopulate} → {@code BiomePlaneAccess}），
         * 机器侧消费（三途余汽）走与平面写入同一谓词 {@code GTSRVoronoiRiverField.isSanzuColumn}。
         */
        SANZU_RIVER(DIM_KEY_PROSPERITY, 4, false),
        ASHEN_PRAIRIE(DIM_KEY_SHATTERED, 0, true),
        SLAGWOOD_GROVE(DIM_KEY_SHATTERED, 1, true),
        VITREOUS_WASTE(DIM_KEY_SHATTERED, 2, true),
        TAR_BASIN(DIM_KEY_SHATTERED, 3, true);

        private final String dimKey;
        private final int ordinal;
        /**
         * 是否进 def 群系表（= GenLayer 链 selector 等权名册成员）。
         * <p>
         * <b>v1.20.39 T8 真缺陷修复（T5 遗留，重钉暴露）</b>：{@code CityPlanner.bandIndexAt} 与
         * {@code GTSRGenLayerRosterFace} 的链入参从"账本已配槽成员"取——T5 给账本补第 5 元后，
         * 这两个身份面悄悄变成 5 元等权链，与 manager 的 def 表 4 元链在(std seed)同坐标解出不同
         * 群系，破坏"manager == 城门 == 地势取数"的三元同一（plan §3.3 红线："不进 GenLayer 链
         * selector，4 家等权名册未动"）。修法：selector 成员性作为名册单一真值的一部分登记在
         * 本枚举，两个链面只吃 {@link #inSelector()} 成员——与 manager 的 def 表挂接口径
         * （{@code ProsperityBiomes.attachBiome} 只给 4 家挂表）由构造保持一致。
         */
        private final boolean selector;

        BiomeId(String dimKey, int ordinal, boolean selector) {
            this.dimKey = dimKey;
            this.ordinal = ordinal;
            this.selector = selector;
        }

        /** 所属维度的 def key。 */
        public String dimKey() {
            return this.dimKey;
        }

        /** 该维名册内下标（权重表下标口径；<b>不叫</b> ordinal()——与 {@code Enum#ordinal()} 冲突）。 */
        public int rosterIndex() {
            return this.ordinal;
        }

        /** 是否进 def 群系表 / GenLayer 链 selector 等权名册（false = roster-only，plan §3.3）。 */
        public boolean inSelector() {
            return this.selector;
        }
    }

    /**
     * 空间采样源——由 {@link GTSRWorldChunkManager} 在构造时绑入（同一张群系表、同一个种子、
     * 同一个 selector，故 L1 与 chunk 生成期所见必然一致）。
     */
    public interface Source {

        /**
         * chunk 粒度群系。
         *
         * @return 群系实例；{@code null} = 该维群系表为空（{@link Degraded#EMPTY}），
         *         <b>不</b>回退 plains（P1 起 plains 回退已删除）
         */
        BiomeGenBase biomeAtChunk(int chunkX, int chunkZ);

        /**
         * micro 强度层只读出口（<b>P8 新增，plan §5 P8「收口 microStrengthAt 悬空层」</b>）。
         * <p>
         * <b>为什么是 default 方法而不是抽象方法</b>：本接口是函数式接口，既有接线方
         * （{@code GTSRBiomeAuthorityTestBiomeBinds} / {@code tools/dim1/BiomeAllocationCheck:173,250,330}）
         * 一律用 {@code mgr::biomeAt} 方法引用绑定——再加一个抽象方法会把它们全部编译打断，
         * 而那些调用点不在本片允许路径内。default 保持单抽象方法 ⇒ 方法引用照常编译，
         * 未覆写的接线方拿到中性 1.0F（= 不伪造强度，与 {@code microStrengthAt} 的降级口径同源）。
         * <p>
         * <b>本方法不参与群系身份</b>（P6 红线）：它只给结构侧（P8 废墟族）做"密度/规模档"调制，
         * 算法体仍是 {@link GTSRWorldChunkManager#microStrengthAt(int, int)} 那一份，本类不自算。
         */
        default float microStrengthAtChunk(final int chunkX, final int chunkZ) {
            return 1.0F;
        }
    }

    /** 单个名册成员的分配账目。 */
    private static final class Entry {

        final BiomeId id;
        /** 首选 id（= {@code *BiomeIdStart + 维内下标}）；配槽前为 -1。 */
        int preferredId = -1;
        int actualId = GTSRBiomeBase.NO_SLOT;
        BiomeGenBase biome;
        /** 首选槽被谁占（owner 快照；null = 首选槽当时空闲）。 */
        String preferredOccupant;

        Entry(BiomeId id) {
            this.id = id;
        }

        boolean allocated() {
            return this.biome != null;
        }
    }

    /** 一次坐标解析的结果（plan §5 P1 接口形状：{@code ordinalAt(x,z) → {BiomeId, degraded?}}）。 */
    public static final class Resolution {

        /** 群系枚举；{@code null} = 该坐标解析不到本维名册成员（降级态或外来群系）。 */
        public final BiomeId biomeId;
        /** 该维名册内下标（{@code -1} 同上）。 */
        public final int ordinal;
        /** L0 实际分配的 biome id（{@code -1} = 未注册）。 */
        public final int actualId;
        /** 群系实例（{@code null} = 空表降级）。 */
        public final BiomeGenBase biome;
        /** 维度级降级状态（不是单点状态：SHORT 时已注册群系的解析仍带 SHORT）。 */
        public final Degraded degraded;

        Resolution(BiomeId biomeId, int ordinal, int actualId, BiomeGenBase biome, Degraded degraded) {
            this.biomeId = biomeId;
            this.ordinal = ordinal;
            this.actualId = actualId;
            this.biome = biome;
            this.degraded = degraded;
        }

        /** 是否解析到名册成员（消费方据此决定"用权重"还是"走自己的降级口径"）。 */
        public boolean resolved() {
            return this.biomeId != null;
        }

        @Override
        public String toString() {
            return (this.biomeId == null ? "(none)" : this.biomeId.name()) + "@"
                + this.actualId
                + " degraded="
                + this.degraded;
        }
    }

    private static final Map<String, GTSRBiomeAuthority> BY_DIM_KEY = new LinkedHashMap<>();
    private static final Map<Integer, GTSRBiomeAuthority> BY_DIM_ID = new HashMap<>();
    /** 未绑定维度的共享空权威（degraded=EMPTY，一切解析都落空；不抛异常、不伪造身份）。 */
    private static final GTSRBiomeAuthority UNBOUND = new GTSRBiomeAuthority("(unbound)");

    private final String dimKey;
    /** 名册成员（按 {@link BiomeId#rosterIndex()} 维内顺序），分配前即已存在。 */
    private final List<Entry> roster = new ArrayList<>();
    private final Map<BiomeId, Entry> byKey = new EnumMap<>(BiomeId.class);
    private final Map<BiomeGenBase, Entry> byInstance = new IdentityHashMap<>();
    private Source source;
    private int dimId = -1;
    private boolean unboundWarned;

    private GTSRBiomeAuthority(String dimKey) {
        this.dimKey = dimKey;
        for (final BiomeId id : BiomeId.values()) {
            if (id.dimKey()
                .equals(dimKey)) {
                final Entry entry = new Entry(id);
                this.roster.add(entry);
                this.byKey.put(id, entry);
            }
        }
    }

    // ————— 写入侧（L0 持有者） —————

    /**
     * 记录一次成功配槽（持有者在 {@code new BiomeXxx(actualId)} 之后立刻调用）。
     * 幂等：同名册成员重复记录以最后一次为准。
     */
    public static synchronized void recordAllocation(BiomeId key, int preferredId, int actualId, BiomeGenBase biome) {
        final Entry entry = entryOf(key, preferredId);
        entry.actualId = actualId;
        entry.biome = biome;
        if (biome != null) {
            forDimKey(key.dimKey()).byInstance.put(biome, entry);
        }
    }

    /** 记录一次"扫到上界仍无空槽"（degraded 由账本自动推导，无需持有者显式上报）。 */
    public static synchronized void recordNoSlot(BiomeId key, int preferredId, String preferredOccupant) {
        final Entry entry = entryOf(key, preferredId);
        entry.actualId = GTSRBiomeBase.NO_SLOT;
        entry.biome = null;
        entry.preferredOccupant = preferredOccupant;
    }

    /** 记录首选槽的占用者名（owner 快照，L8；即使该成员最终顺延成功也要记）。 */
    public static synchronized void recordPreferredOccupant(BiomeId key, int preferredId, String occupant) {
        entryOf(key, preferredId).preferredOccupant = occupant;
    }

    private static Entry entryOf(BiomeId key, int preferredId) {
        final Entry entry = forDimKey(key.dimKey()).byKey.get(key);
        if (entry == null) {
            throw new IllegalArgumentException("biome not in roster: " + key);
        }
        entry.preferredId = preferredId;
        return entry;
    }

    // ————— 绑定侧（L0 群系表 → L1 采样源） —————

    /**
     * 绑定空间采样源（{@link GTSRWorldChunkManager} 构造内调用；一个维度后绑的覆盖先绑的，
     * 与 vanilla 的"当前世界的 chunk manager 即权威"口径一致）。
     */
    public static synchronized void bind(String dimKey, int dimId, Source source) {
        final GTSRBiomeAuthority authority = forDimKey(dimKey);
        authority.source = source;
        authority.dimId = dimId;
        if (dimId >= 0) {
            BY_DIM_ID.put(dimId, authority);
        }
    }

    // ————— 读取侧（L1 唯一身份出口） —————

    /** 按 def key 取权威（惰性创建；名册固定，故离线工具可在注册前取得实例）。 */
    public static synchronized GTSRBiomeAuthority forDimKey(String dimKey) {
        GTSRBiomeAuthority authority = BY_DIM_KEY.get(dimKey);
        if (authority == null) {
            authority = new GTSRBiomeAuthority(dimKey);
            BY_DIM_KEY.put(dimKey, authority);
        }
        return authority;
    }

    /**
     * 按维度 id 取权威（消费方主入口，如 {@code forDimension(world.provider.dimensionId)}）。
     * 未绑定（维度未注册 / 群系表从未建过）时返回共享的 {@link #UNBOUND}——其
     * {@code degraded()==EMPTY} 且一切解析落空，调用方按降级口径处理即可，不抛异常。
     */
    public static synchronized GTSRBiomeAuthority forDimension(int dimId) {
        final GTSRBiomeAuthority authority = BY_DIM_ID.get(dimId);
        if (authority != null) {
            return authority;
        }
        if (!UNBOUND.unboundWarned) {
            UNBOUND.unboundWarned = true;
            GTSteamReborn.LOG.warn(
                "[GTSR] biome authority unbound for dimId={} (no chunk manager bound yet; "
                    + "every lookup resolves as degraded=EMPTY)",
                dimId);
        }
        return UNBOUND;
    }

    /**
     * 坐标 → 群系身份（<b>块坐标</b>入参，与 vanilla {@code World.getBiomeGenForCoords} 同口径，
     * 内部按 chunk 粒度解析：{@code x >> 4}）。
     * <p>
     * 解析链：{@link Source}（确定性纯函数采样，非 Chunk byte 平面）→ 实例经账本映射为
     * {@link BiomeId}。空表降级返回"身份不可得"形态（{@code biomeId=null}、
     * {@code ordinal=-1}、{@code degraded=EMPTY}），<b>绝不</b>回退 plains。
     */
    public Resolution ordinalAt(int x, int z) {
        final Source resolver = this.source;
        if (resolver == null) {
            return new Resolution(null, -1, GTSRBiomeBase.NO_SLOT, null, degraded());
        }
        return of(resolver.biomeAtChunk(x >> 4, z >> 4));
    }

    /**
     * micro 强度层只读出口（<b>P8 新增，收口 plan §5 P6 留下的"结构侧消费点 == 0"悬空层</b>）。
     * <p>
     * 入参口径与 {@link #ordinalAt(int, int)} 完全一致（<b>块坐标</b>，内部按 chunk 粒度解析），
     * 因为两者的消费方都在结构侧、拿到的都是 chunk 中心块坐标；实现体仍是
     * {@link GTSRBiomeAuthority.Source#microStrengthAtChunk(int, int)} 交给的那一份
     * {@code GTSRWorldChunkManager.microStrengthAt}（L1 不自己算强度）。
     * <p>
     * 未绑定 / 接线方未覆写 default ⇒ 返回<b>中性 1.0F</b>（与 {@code microStrengthAt} 的
     * "未挂 selector 或空表降级时不伪造强度"同一口径）。本方法<b>不参与群系身份</b>，
     * 也不得被用来推身份（P6 红线：身份只有 {@link #ordinalAt} 一条出口）。
     */
    public float microStrengthAt(int x, int z) {
        final Source resolver = this.source;
        return resolver == null ? 1.0F : resolver.microStrengthAtChunk(x >> 4, z >> 4);
    }

    /** 群系实例 → 身份解析（供已持有实例的消费方使用；同一账本，同一降级口径）。 */
    public Resolution of(BiomeGenBase biome) {
        final Degraded degraded = degraded();
        if (biome == null) {
            return new Resolution(null, -1, GTSRBiomeBase.NO_SLOT, null, degraded);
        }
        final Entry entry = this.byInstance.get(biome);
        if (entry == null) {
            // 不是本维名册成员（外部群系混入 / 我方未注册）：身份未知，交给消费方降级处理
            return new Resolution(null, -1, biome.biomeID, biome, degraded);
        }
        return new Resolution(entry.id, entry.id.rosterIndex(), entry.actualId, biome, degraded);
    }

    /** 名册成员的实际 id（未注册返回 {@link GTSRBiomeBase#NO_SLOT}）。 */
    public int actualIdOf(BiomeId key) {
        final Entry entry = this.byKey.get(key);
        return entry == null ? GTSRBiomeBase.NO_SLOT : entry.actualId;
    }

    public BiomeGenBase biomeOf(BiomeId key) {
        final Entry entry = this.byKey.get(key);
        return entry == null ? null : entry.biome;
    }

    /**
     * 群系实例 → 名册身份（<b>P9 新增只读出口，跨两维查找</b>；{@code null} = 该实例不在任何维的
     * 分配账本里）。
     * <p>
     * 为什么需要它：L7 的填充点在 {@code GTSRBiomeBase} 自己身上（任务包允许路径只含框架基类，
     * 不含八个群系子类和 {@code ProsperityBiomes}），而基类只知道"我是谁"这一件事需要问账本。
     * 本出口仍然只读 {@link Entry}（即 L0 写入的分配账本），<b>不</b>碰 Chunk byte 平面、
     * <b>不</b>做 {@code instanceof} 链、<b>不</b>用 {@code id - idStart} 减法——与
     * {@link #of(BiomeGenBase)} 同一份数据源，只是把"身份"而不是"解析结果"交出去。
     * <p>
     * 未入账（{@code degraded=SHORT/EMPTY} 的缺席成员、外部群系）返回 {@code null}，
     * 调用方必须按"无身份 ⇒ 不填充"处理，不得伪造。
     */
    public static synchronized BiomeId identityOf(BiomeGenBase biome) {
        final Entry entry = entryOfInstance(biome);
        return entry == null ? null : entry.id;
    }

    /** 群系实例 → 所属维度的 def key（{@code null} 同上；与 {@link #identityOf} 同一账本）。 */
    public static synchronized String dimKeyOf(BiomeGenBase biome) {
        final Entry entry = entryOfInstance(biome);
        return entry == null ? null : entry.id.dimKey();
    }

    /** 名册成员列表（只读快照，按维内 {@link BiomeId#rosterIndex()} 顺序；P9 生物层遍历用）。 */
    public List<BiomeId> rosterKeys() {
        final List<BiomeId> out = new ArrayList<>(this.roster.size());
        for (final Entry entry : this.roster) {
            out.add(entry.id);
        }
        return out;
    }

    // ═════════ P14 只读定位出口（plan §5 P14「tpdim 群系名 → 最近带内 chunk」）═════════
    //
    // 本段三件（rosterBiomeNames / findRosterByName / nearestBiomeChunk）全部<b>只读</b>：
    // 名册名取自 {@link Entry#biome} 的 {@code biomeName}（配槽账本单一真值，不新造第二份名单）；
    // 身份判定复用 {@link #byInstance} 同一账本（与 {@link #of(BiomeGenBase)} 同一数据源），
    // 空间采样走已绑定的 {@link Source}（确定性纯函数，<b>不</b>读 Chunk byte 平面、
    // <b>不</b>碰 World.getBiomeGenForCoords）。禁止项（配槽/降级/带算法语义）一字未动。

    /**
     * 名册成员显示名列表（已配槽成员，按维内顺序；未配槽成员在降级态下没有可解析身份，
     * 不列——tab 补全与名字解析共用本出口，与 {@link #rosterKeys()} 同源）。
     */
    public List<String> rosterBiomeNames() {
        final List<String> out = new ArrayList<>(this.roster.size());
        for (final Entry entry : this.roster) {
            if (entry.biome != null) {
                out.add(entry.biome.biomeName);
            }
        }
        return out;
    }

    /**
     * 群系名 → 名册身份（大小写不敏感；接受显示名原文、下划线别名与含多余空白形态——
     * 服务端 1.7.10 的 {@code String.split(" ")} 不识别引号，故带空格名字由调用方把尾随参数
     * 以空格重join 后传入，这里再折叠连续空白并把 {@code '_'} 视为空格别名）。
     *
     * @return 名册成员；{@code null} = 不在该维（已配槽）名册内，调用方必须给可读错误
     */
    public BiomeId findRosterByName(String rawName) {
        if (rawName == null) {
            return null;
        }
        final String normalized = normalizeBiomeName(rawName);
        if (normalized.isEmpty()) {
            return null;
        }
        for (final Entry entry : this.roster) {
            if (entry.biome != null && normalized.equalsIgnoreCase(normalizeBiomeName(entry.biome.biomeName))) {
                return entry.id;
            }
        }
        return null;
    }

    /** 名字归一化（{@link #findRosterByName} 与工具对拍共用）：trim + 剥外层双引号 + 连续空白折叠 + 下划线视作空格。 */
    public static String normalizeBiomeName(String raw) {
        String s = raw.trim();
        // 1.7.10 服务端切分不识别引号，`"Rusted Steppe"` 会裂成 `"Rusted` + `Steppe"` 两 token，
        // 由指令层重 join 回到本方法——这里剥掉残留的一对外层双引号。
        if (s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
            s = s.substring(1, s.length() - 1)
                .trim();
        }
        return s.replaceAll("\\s+", " ")
            .replace('_', ' ')
            .trim();
    }

    /** {@link #nearestBiomeChunk} 的状态申报（HIT 恒有命中；另两态 {@code biome==null} 或有保险丝前 best）。 */
    public enum NearestStatus {
        /** 环带步进已扫过"不可能更近"的外沿，命中即真·最近（块坐标欧氏距离，chunk 粒度）。 */
        HIT,
        /** 半径上限内未找到（可读错误出口，玩家不动）。 */
        NOT_FOUND,
        /** 步数保险丝先到：命中存在但未证全局最近（回执必须申报）。 */
        STEP_LIMIT
    }

    /** 一次环带搜索的结果账目（P14 判据 3/4 的实测载体：命中点 + 距离 + 步数 + 状态）。 */
    public static final class NearestBiomeChunk {

        /** 命中群系（{@code null} = 未命中）。 */
        public final BiomeId biome;
        /** 命中 chunk 坐标（未命中时 {@code -1}）。 */
        public final int chunkX;
        public final int chunkZ;
        /** 命中点到原点 (originChunkX, originChunkZ) 的欧氏距离平方（chunk 粒度；未命中 -1）。 */
        public final long distSq;
        /** 完整扫过的环带数（半径，chunk；含命中后为证明最近而继续扫的环带）。 */
        public final int ringsScanned;
        /** 实际评估的 chunk 数（判据 4 的步数实测列）。 */
        public final long steps;
        public final NearestStatus status;

        private NearestBiomeChunk(BiomeId biome, int chunkX, int chunkZ, long distSq, int ringsScanned, long steps,
            NearestStatus status) {
            this.biome = biome;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.distSq = distSq;
            this.ringsScanned = ringsScanned;
            this.steps = steps;
            this.status = status;
        }

        static NearestBiomeChunk of(EntryProbe best, long originDistSq, int rings, long steps, NearestStatus status) {
            if (best == null) {
                return new NearestBiomeChunk(null, -1, -1, -1L, rings, steps, status);
            }
            return new NearestBiomeChunk(best.id, best.cx, best.cz, originDistSq, rings, steps, status);
        }

        @Override
        public String toString() {
            return status + " biome="
                + biome
                + " chunk=("
                + chunkX
                + ","
                + chunkZ
                + ")"
                + " distSq="
                + distSq
                + " rings="
                + ringsScanned
                + " steps="
                + steps;
        }
    }

    /** 搜索内部 best 账目（chunk 坐标 + 名册身份）。 */
    private static final class EntryProbe {

        final int cx;
        final int cz;
        final BiomeId id;

        EntryProbe(int cx, int cz, BiomeId id) {
            this.cx = cx;
            this.cz = cz;
            this.id = id;
        }
    }

    /**
     * 由内向外的<b>环带步进</b>定位：以 (originChunkX, originChunkZ) 为心，按切比雪夫环带
     * r=0,1,2…（每环 8r 个 chunk）逐格问 {@link #of} 同一份 {@link byInstance} 账本，命中后继续
     * 扫到"外沿最小可能距离已严格大于当前 best"（{@code (r+1)² > bestDistSq}）才返回——
     * 因此 HIT 的命中点是 <b>chunk 粒度欧氏距离（到原点 chunk 中心连线）下的真最近点</b>，
     * 与穷举对拍距离差恒为 0（{@code tools/dim1/TpdimNearestBiomeCheck} 判据 3 钉住）。
     * <p>
     * 代价上界（plan §5 P14「不在指令线程做无界扫描」）：环带到 {@code maxRadiusChunks} 为止
     * （到界未命中 ⇒ {@link NearestStatus#NOT_FOUND}）；评估数到 {@code maxSteps} 保险丝为止
     * （截断 ⇒ {@link NearestStatus#STEP_LIMIT}，有 best 则照常带回）。未绑定 {@link Source} 时
     * 不搜索，直接 {@code NOT_FOUND, steps=0}（降级可见：调用方按"权威未就位"报可读错误，不指野点）。
     */
    public NearestBiomeChunk nearestBiomeChunk(BiomeId target, int originChunkX, int originChunkZ, int maxRadiusChunks,
        int maxSteps) {
        final Source resolver = this.source;
        if (resolver == null || target == null || maxRadiusChunks < 0) {
            return NearestBiomeChunk.of(null, -1L, 0, 0L, NearestStatus.NOT_FOUND);
        }
        final long stepsCap = Math.max(1L, maxSteps);
        long steps = 0L;
        EntryProbe best = null;
        long bestDistSq = Long.MAX_VALUE;
        int rings = 0;
        for (int r = 0; r <= maxRadiusChunks; r++) {
            rings++;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue; // 只走环带轮廓（内部格在更早的环已评估过）
                    }
                    final long ddx = dx;
                    final long ddz = dz;
                    final long distSq = ddx * ddx + ddz * ddz;
                    if (best != null && distSq > bestDistSq) {
                        continue; // 本格已不可能优于 best（欧氏距离）——免一次评估；环完整性留给终止判据
                    }
                    if (++steps > stepsCap) {
                        return NearestBiomeChunk
                            .of(best, best == null ? -1L : bestDistSq, rings, steps, NearestStatus.STEP_LIMIT);
                    }
                    final Entry hit = this.byInstance.get(resolver.biomeAtChunk(originChunkX + dx, originChunkZ + dz));
                    if (hit != null && hit.id == target) {
                        best = new EntryProbe(originChunkX + dx, originChunkZ + dz, target);
                        bestDistSq = distSq;
                    }
                }
            }
            // HIT 终止判据：下一环最小欧氏距离 (r+1) 已严格大于当前 best ⇒ best 即真最近，无需再扫
            if (best != null && (long) (r + 1) * (r + 1) > bestDistSq) {
                return NearestBiomeChunk.of(best, bestDistSq, rings, steps, NearestStatus.HIT);
            }
        }
        // 走到这里 = 半径内所有环已完整扫过：best（若有）即"半径内真最近"，只是外沿证明因半径封顶未触发
        // （回执照带 rings/steps，命令消息始终申报已搜半径，判据 4 的超限档由 NOT_FOUND 承担）
        return NearestBiomeChunk.of(
            best,
            best == null ? -1L : bestDistSq,
            rings,
            steps,
            best == null ? NearestStatus.NOT_FOUND : NearestStatus.HIT);
    }

    private static Entry entryOfInstance(BiomeGenBase biome) {
        if (biome == null) {
            return null;
        }
        for (final GTSRBiomeAuthority authority : BY_DIM_KEY.values()) {
            final Entry entry = authority.byInstance.get(biome);
            if (entry != null) {
                return entry;
            }
        }
        return null;
    }

    /** 维内名册规模（枚举登记数，与是否成功配槽无关）。 */
    public int rosterSize() {
        return this.roster.size();
    }

    /** 实际拿到槽位的成员数。 */
    public synchronized int allocatedCount() {
        int n = 0;
        for (final Entry entry : this.roster) {
            if (entry.allocated()) {
                n++;
            }
        }
        return n;
    }

    /** 降级状态推导：全配到=NONE，一个没配到=EMPTY，其余=SHORT。 */
    public synchronized Degraded degraded() {
        final int allocated = allocatedCount();
        if (allocated == 0) {
            return Degraded.EMPTY;
        }
        return allocated < rosterSize() ? Degraded.SHORT : Degraded.NONE;
    }

    /** 该维是否已绑定采样源（世界已建 chunk manager）。 */
    public boolean isBound() {
        return this.source != null;
    }

    /** 绑定时登记的实际维度 id（-1 = 只按 def key 绑过、未按维度 id 建索引）。 */
    public int boundDimId() {
        return this.dimId;
    }

    /**
     * 分配账本摘要：{@code 180->183, 181->184, 182->NONE, 183->NONE}
     * （首选 id → 实际 id；{@code NONE} = 该成员无槽）。持有者把它拼进一行可 grep 的汇总日志。
     */
    public synchronized String allocationSummary() {
        final StringBuilder sb = new StringBuilder();
        for (final Entry entry : this.roster) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(entry.preferredId)
                .append("->")
                .append(entry.allocated() ? String.valueOf(entry.actualId) : "NONE");
        }
        return sb.toString();
    }

    /** owner 快照摘要（仅列首选槽被占的成员）：{@code 180=other:Firefly Forest, ...}。 */
    public synchronized String occupantSummary() {
        final StringBuilder sb = new StringBuilder();
        for (final Entry entry : this.roster) {
            if (entry.preferredOccupant == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(entry.preferredId)
                .append('=')
                .append(entry.preferredOccupant);
        }
        return sb.toString();
    }

    public String dimKey() {
        return this.dimKey;
    }
}
