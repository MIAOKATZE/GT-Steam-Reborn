package com.miaokatze.gtsr.common.dimension.framework;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.world.ChunkPosition;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.WorldChunkManager;

import com.miaokatze.gtsr.common.dimension.framework.genlayer.GTSRGenLayerChain;
import com.miaokatze.gtsr.main.GTSteamReborn;

/**
 * 自写 BiomeProvider（dim1 S1 骨架；<b>B1 起群系分布走 GenLayer 链</b>，plan §1.2/§5.4 口径）。
 * <p>
 * <b>B1 双面消费（本片接线）</b>：每维（每 manager 实例）持有一条 {@link GTSRGenLayerChain}
 * （selector→Zoom×DEFAULT_ZOOM_LEVELS(=5，P17 S-A 由 4 递进)→Smooth 粗层 + VoronoiZoom 细层，入参 = def 群系表 id 的<b>等权</b>数组，
 * 不再引用 45/30/15/10 权重常量），两面分工仿 vanilla {@code WorldChunkManager}：
 * <ul>
 * <li><b>粗层身份面</b>（1:4）：{@link #biomeAt(int, int)}（chunk 粒度，代表点 = chunk 中心块
 * {@code (chunkX<<4)+8, (chunkZ<<4)+8}）、{@link #getBiomesForGeneration}（x/z 为粗层单位，
 * 供大区域查询）、{@link #areBiomesViable}——L1 身份（{@code GTSRBiomeAuthority.ordinalAt}）、
 * 结构门/城市门（{@code CityPlanner} 本地同参链，B2 起与 {@link #biomeAt} 逐位同一）、装饰
 * micro 档（{@link #microStrengthAt}，可用性随本面判）都吃这一面；</li>
 * <li><b>细层方块平面</b>（1:1，voronoi 有机边界）：{@link #loadBlockGeneratorData} /
 * {@link #getBiomeGenAt} / {@link #getRainfall}——{@code provideChunk} 的 16×16 逐列平面与
 * vanilla 懒回填回调（{@code Chunk.getBiomeGenForWorldCoords} → 单点 {@code getBiomeGenAt}）
 * 同源同解，故 EndlessIDs 回填路径与直接写逐位一致。</li>
 * </ul>
 * 两面出自<b>同一条链同一个种子</b>（纯函数：同 seed 同坐标恒同值），但 1:1 平面在粗层格边界
 * ±2 块内有 voronoi 抖动——chunk 身份面与方块平面在边界列可以合法不同（vanilla 同性质，
 * 非三面同解的破坏：三面指 chunk 生成平面、单点采样、懒回填三者互相同解）。
 * <p>
 * <b>种子礼仪</b>：链种子 = {@code seed ^ def.getSeedSalt()}（世界种子掺维度域分离盐），
 * 两维同世界种子时链输出互不相关；同 seed 的链只在本 manager 实例内存在，不跨 seed 复用。
 * <p>
 * <b>P1 降级语义（保持不变）</b>：群系表为空（EMPTY）时 {@link #biomeAt} 仍返回 {@code null}、
 * 平面槽位为 null（不回退 plains；缺列占位 0=ocean 由 {@code GTSRChunkProviderBase.writeBiomePlane}
 * /{@code BiomePlaneAccess} 承担，禁 255/1 规则沿用）。{@link #degraded()} 仍由
 * {@link GTSRBiomeAuthority} 账本推导；本类构造时把 {@link #biomeAt(int, int)} 绑入 L1，
 * 使"坐标→群系身份"与 chunk 生成期出自同一条链。
 * <p>
 * <b>输出 id 防御（写面零异常纪律）</b>：链自身已把输出钳制 ∈ 入参 id 集合；manager 层再做一道
 * id→本维群系表的查表校验，查不到时回落到首群系并<b>每 manager 只 log 一次</b>（不抛出）。
 * <p>
 * <b>B2 收口（本片）</b>：{@code BiomeZoneSelector} 的身份带职责（权重掷骰 / macro 带折算 /
 * 边带碎斑）与过渡出口 {@code bandRosterIndex} 已整体退役——城市门与装饰 micro 档自此直接
 * 吃链身份面：{@code CityPlanner.bandIndexAt} 在本地按同一构造参数（{@code seed ^ seedSalt}、
 * 名册已配槽 id 等权表、chunk 中心代表点）重建同一条链，与 {@link #biomeAt} 逐位同一
 * （由 {@code tools/dim1/BiomeBandHierarchyCheck} C1 在真实链上钉住）；{@link #microStrengthAt}
 * 的中性 1.0 门槛从"未挂 selector"改为"链缺席（空表降级）"。
 * <p>
 * 无 BiomeCache：链为纯函数采样，覆写 cleanupCache 为空操作（与 vanilla 有 BiomeCache 的
 * {@code getBiomeGenAt} 不同，我们以确定性优先）。
 */
public class GTSRWorldChunkManager extends WorldChunkManager {

    private final long seed;
    /**
     * 按权重展开的群系选择表（B1 起只剩"空表 ⇒ EMPTY"的判空口径：null = 空群系表，即
     * {@link GTSRBiomeAuthority.Degraded#EMPTY}；身份/平面采样已改走 {@link #genChain}，
     * 权重展开结果不再被消费。B2 起权重也不进入任何身份面）。
     */
    private final BiomeGenBase[] weightedBiomes;
    /** 本维 micro 强度层域分离盐输入（= def.seedSalt；匿名 def 为 0）。 */
    private final long microDomainSalt;
    /** 所属维度的 def key（L1 账本键；null = 匿名 def，仅离线自检会出现）。 */
    private final String dimKey;
    /**
     * B1 GenLayer 链（每维一条，链内含粗/细两根；null = 空表降级 {@code EMPTY}，无群系分布）。
     * <p>
     * 链种子 = {@code seed ^ def.getSeedSalt()}；链非线程安全（{@code getInts} 改写层内种子态），
     * 与 vanilla 每 WorldChunkManager 一条链同款纪律——跨线程并发查询由调用方规避。
     */
    private final GTSRGenLayerChain genChain;
    /** 链出参 id → 本维群系实例的查表（下标 = biome id；null 槽 = 非本维 id，防御路径用）。 */
    private final BiomeGenBase[] idToBiome;
    /** 防御回落目标（链出参查不到时的首群系；null = 无表）。 */
    private final BiomeGenBase fallbackBiome;
    /** 防御回落日志闸（每 manager 实例只 log 一次，写面零异常纪律）。 */
    private final AtomicBoolean invalidIdLogged = new AtomicBoolean(false);
    /**
     * {@link #getBiomesToSpawnIn()} 的返回值（P9 / L7 新增；def 群系表去权重快照，
     * 空表降级时为空表）。构造期一次成型、此后只读。
     */
    private final List<BiomeGenBase> biomesToSpawnIn;

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
        this.microDomainSalt = seedSalt;
        // B1：GenLayer 链接线——入参 = def 群系表 id 的等权数组（去权重；SHORT 态即已配槽子集），
        // 种子 = 世界种子 ^ def.seedSalt（维度域分离）。id 超出 [0,254] 的表项不进链（防御剔除，
        // 生产链路的配槽已保证 ≤254；全被剔除时链缺席，biomeAt 按空表口径返回 null）。
        GTSRGenLayerChain chain = null;
        BiomeGenBase[] lookup = null;
        BiomeGenBase fallback = null;
        if (table != null && table.length > 0) {
            final int[] chainIds = new int[table.length];
            int kept = 0;
            lookup = new BiomeGenBase[GTSRBiomeBase.HARD_ID_MAX + 1];
            for (final BiomeGenBase biome : table) {
                final int id = biome.biomeID;
                if (id < 0 || id > GTSRBiomeBase.HARD_ID_MAX) {
                    GTSteamReborn.LOG.warn(
                        "[GTSR] dim={} biome '{}' id {} outside [0,{}] — excluded from GenLayer chain (def table {})",
                        String.valueOf(this.dimKey),
                        biome.biomeName,
                        id,
                        GTSRBiomeBase.HARD_ID_MAX,
                        table.length);
                    continue;
                }
                chainIds[kept++] = id;
                lookup[id] = biome;
                if (fallback == null) {
                    fallback = biome;
                }
            }
            if (kept > 0) {
                chain = new GTSRGenLayerChain(seed ^ seedSalt, Arrays.copyOf(chainIds, kept));
            } else {
                lookup = null;
                fallback = null;
            }
        }
        this.genChain = chain;
        this.idToBiome = lookup;
        this.fallbackBiome = fallback;
        // P9/L7：getBiomesToSpawnIn 的出口——去权重、去 null（空表降级 ⇒ 空表，与 degraded=EMPTY 同口径）
        this.biomesToSpawnIn = table == null ? Collections.<BiomeGenBase>emptyList()
            : Collections.unmodifiableList(Arrays.asList(table));
        // L1 绑定：身份解析复用本 manager 的采样函数（同表、同种子、同一条 GenLayer 链）
        if (this.dimKey != null) {
            // P8：同一次 bind 顺带把 micro 强度的只读出口交给 L1（见 Source#microStrengthAtChunk）。
            // 身份面仍逐字是 this::biomeAt 那一份实现体，未加任何分支或缓存 ⇒ L1 语义零改动。
            GTSRBiomeAuthority.bind(this.dimKey, def.getResolvedDimId(), new GTSRBiomeAuthority.Source() {

                @Override
                public BiomeGenBase biomeAtChunk(final int chunkX, final int chunkZ) {
                    return GTSRWorldChunkManager.this.biomeAt(chunkX, chunkZ);
                }

                @Override
                public float microStrengthAtChunk(final int chunkX, final int chunkZ) {
                    return GTSRWorldChunkManager.this.microStrengthAt(chunkX, chunkZ);
                }
            });
        }
    }

    /**
     * per-chunk 群系选择纯函数（<b>B1 起内部改走 GenLayer 链粗层</b>，chunk 粒度输出口径不变）。
     * <p>
     * 代表点 = <b>chunk 中心块</b> {@code (chunkX<<4)+8, (chunkZ<<4)+8}（即粗层格
     * {@code chunkX*4+2, chunkZ*4+2}——chunk 覆盖的 4×4 粗格的中心格，比 A3 契约草案的
     * {@code (chunkX<<4)}（西北角粗格）更能代表整 chunk；契约以中心为准，链侧 javadoc 已同步）。
     * <p>
     * 纯函数语义：链逐格 {@code initChunkSeed}，同 (seed, 坐标) 恒同值、与查询顺序无关；
     * 链只在本实例内（不跨 seed 复用），L1 绑定的 {@code biomeAtChunk} 即本方法 ⇒ 身份面与
     * 生成期同源。返回 {@code null} = 空表降级（EMPTY，P1 起不回退 plains）。
     * <p>
     * <b>过渡态</b>：旧的 selector/band 首选路径与 per-chunk 权重掷骰已不再被本方法消费
     * （等权链取代权重展开）。
     */
    public BiomeGenBase biomeAt(int chunkX, int chunkZ) {
        if (this.genChain == null) {
            return null;
        }
        return biomeById(this.genChain.biomeAtCoarse((chunkX << 4) + 8, (chunkZ << 4) + 8));
    }

    /**
     * 链出参 id → 本维群系实例（manager 层防御钳制，B1）。
     * <p>
     * 链自身已保证 id ∈ 入参集合（{@code GTSRGenLayerChain.sanitize}）；这里再做一道查表校验：
     * 查不到（理论不可达）时回落到 {@link #fallbackBiome}（首群系）并<b>每实例只 log 一次</b>
     * ——不抛出、不打断世界生成（写面零异常纪律）。链缺席（EMPTY）时调用方不会走到这里。
     */
    private BiomeGenBase biomeById(int id) {
        if (this.idToBiome != null && id >= 0 && id <= GTSRBiomeBase.HARD_ID_MAX) {
            final BiomeGenBase biome = this.idToBiome[id];
            if (biome != null) {
                return biome;
            }
        }
        if (this.invalidIdLogged.compareAndSet(false, true)) {
            GTSteamReborn.LOG.warn(
                "[GTSR] dim={} GenLayer plane id {} resolved to no local biome — falling back to '{}' (once per manager)",
                String.valueOf(this.dimKey),
                id,
                this.fallbackBiome == null ? "null" : this.fallbackBiome.biomeName);
        }
        return this.fallbackBiome;
    }

    /**
     * 本维 def key（L1 账本键；{@code null} = 匿名 def）。
     * <p>
     * <b>P12 新增只读出口（plan §2.1 L8）</b>：进维一次性诊断行需要"维度 id ↔ def key ↔ 是否已绑"
     * 三元组，而 {@link GTSRChunkProviderBase} 只能经 {@code World} 摸到本 manager——没有这个出口
     * 它就只能猜。纯读构造期字段，<b>不</b>参与任何生成/身份判定。
     */
    public String dimKey() {
        return this.dimKey;
    }

    /**
     * 本维名册群系表规模（去权重快照条数；{@code GTSRChunkProviderBase} 与诊断行的只读出口）。
     * 空表降级时为 0，与 {@link #isEmptyDegraded()} 同口径。
     */
    public int biomeTableSize() {
        return this.biomesToSpawnIn.size();
    }

    /**
     * micro 层变体/装饰强度系数（P6 新增只读出口；0.7/1.0/1.3，见
     * {@link BiomeZoneSelector#MICRO_STRENGTHS}）。
     * <p>
     * <b>不参与群系身份</b>，且本片<b>不接入</b>散布/装饰的 K 与权重（P5 已锁定，plan §5 P6 禁止越界）；
     * 消费方是 P7/P8 的变体选择与装饰强度。<b>B2 起</b>：掷骰算法体不变，但中性 1.0 的门槛从
     * "未挂 selector / 空表降级"改为<b>"链缺席（空表降级 ⇒ 无身份面）"</b>——档位的可用性
     * 随链身份面判：群系身份面缺席时不伪造强度。
     */
    public float microStrengthAt(int chunkX, int chunkZ) {
        if (this.genChain == null) {
            return 1.0F;
        }
        return BiomeZoneSelector.microStrength(this.seed, chunkX, chunkZ, this.microDomainSalt);
    }

    /**
     * 本维降级状态（L1 账本推导；未绑定 def key 的匿名 def 按"表空即 EMPTY、表非空即 NONE"）。
     */
    public GTSRBiomeAuthority.Degraded degraded() {
        if (this.dimKey == null) {
            return this.weightedBiomes == null ? GTSRBiomeAuthority.Degraded.EMPTY : GTSRBiomeAuthority.Degraded.NONE;
        }
        return GTSRBiomeAuthority.forDimKey(this.dimKey)
            .degraded();
    }

    /** 是否为空表降级（表层/装饰消费侧的便捷判定）。 */
    public boolean isEmptyDegraded() {
        return this.weightedBiomes == null;
    }

    /**
     * 方块级单点采样（<b>细层 1:1</b>，B1 起对齐 vanilla {@code WorldChunkManager.getBiomeGenAt}）。
     * <p>
     * 这是 EndlessIDs/vanilla 懒回填回调的落点（{@code Chunk.getBiomeGenForWorldCoords} 的 255/-1
     * 哨兵路径最终调到这里）：与 {@code provideChunk} 直接写平面同一条细层链 ⇒ 回填值与直接写
     * 逐位同解。空表降级返回 {@code null}（P1 口径不变——我方从不写哨兵，该路径不会被本 mod 触发）。
     * 无 BiomeCache（确定性优先）：vanilla 在这里走缓存，我们覆写 {@link #cleanupCache} 为空操作。
     */
    @Override
    public BiomeGenBase getBiomeGenAt(int x, int z) {
        if (this.genChain == null) {
            return null;
        }
        return biomeById(this.genChain.biomeAtFine(x, z));
    }

    /**
     * 本维群系表（<b>P9 / L7 改造点</b>：原来恒返回空表，是"双机制零生物"的第二条机制）。
     * <p>
     * 返回 def 群系表的<b>去权重</b>快照（一个群系一项，不含 {@code weightedBiomes} 的权重展开，
     * 也不含空表降级产生的 {@code null} 槽位）。vanilla 唯一消费点是
     * {@code WorldServer.createSpawnPosition}（把本表喂给 {@link #findBiomePosition}），
     * 而本类的 {@code findBiomePosition} 恒返回 {@code null} ⇒ 本表内容不影响出生点结果，
     * 改造前后<b>逐位等价</b>；真正拿到收益的是第三方按该出口找"本维合法群系"的消费方
     * （刷怪/传送/探路类 mod）。
     * <p>
     * 与 L1 同源：本表就是 {@link GTSRBiomeAuthority} 账本里那一批实例（{@code def.getBiomeTable()}），
     * 不新造第二份群系来源。
     */
    @Override
    public List<BiomeGenBase> getBiomesToSpawnIn() {
        return this.biomesToSpawnIn;
    }

    /**
     * 湿度采样（<b>细层 1:1</b>，B1 起对齐 vanilla {@code WorldChunkManager.getRainfall}：
     * 逐方块列走 biomeIndexLayer 同款口径）。空表降级（无链）不写湿度：保持数组槽位现值
     * （新数组即 0.0F），不伪造 plains 湿度（plan §2.3 判据 2）。
     */
    @Override
    public float[] getRainfall(float[] listToReuse, int x, int z, int width, int length) {
        if (listToReuse == null || listToReuse.length < width * length) {
            listToReuse = new float[width * length];
        }
        if (this.genChain == null) {
            return listToReuse;
        }
        final int[] ids = this.genChain.fineInts(x, z, width, length);
        for (int dx = 0; dx < width; dx++) {
            for (int dz = 0; dz < length; dz++) {
                final BiomeGenBase biome = biomeById(ids[dx + dz * width]);
                if (biome != null) {
                    listToReuse[dx + dz * width] = biome.getIntRainfall() / 65536.0F;
                }
            }
        }
        return listToReuse;
    }

    /**
     * <b>粗层大区域查询</b>（B1 起对齐 vanilla {@code WorldChunkManager.getBiomesForGeneration}）：
     * x/z 是<b>粗层单位</b>（1 单位 = 4 方块；vanilla 调用方传 {@code chunkX*4} 一类粗层坐标），
     * 出参行主序 {@code out[dx + dz*width]}。空表降级时逻辑窗内槽位为 null（P1 起不填 plains）。
     */
    @Override
    public BiomeGenBase[] getBiomesForGeneration(BiomeGenBase[] listToReuse, int x, int z, int width, int length) {
        if (listToReuse == null || listToReuse.length < width * length) {
            listToReuse = new BiomeGenBase[width * length];
        }
        if (this.genChain == null) {
            Arrays.fill(listToReuse, 0, width * length, null);
            return listToReuse;
        }
        final int[] ids = this.genChain.coarseInts(x, z, width, length);
        for (int i = 0; i < width * length; i++) {
            listToReuse[i] = biomeById(ids[i]);
        }
        return listToReuse;
    }

    /**
     * <b>方块级平面</b>（B1 起对齐 vanilla {@code WorldChunkManager.loadBlockGeneratorData}）：
     * x/z 为<b>方块坐标</b>（1:1 细层，voronoi 有机边界），出参行主序 {@code out[dx + dz*width]}
     * ——{@code GTSRChunkProviderBase.provideChunk} 的 16×16 chunk 平面即本出口产出，
     * 经 {@code BiomePlaneAccess.write} 双通道落盘（写接口不变）。空表降级时逻辑窗内槽位为 null
     * （缺列占位 0=ocean、禁 255/1 规则由写通道承担）。
     */
    @Override
    public BiomeGenBase[] loadBlockGeneratorData(BiomeGenBase[] oldList, int x, int z, int width, int depth) {
        return getBiomeGenAt(oldList, x, z, width, depth, true);
    }

    /**
     * <b>方块级平面</b>（细层 1:1；与 {@link #loadBlockGeneratorData} 同面，vanilla 同款转发）。
     * {@code cacheFlag} 被忽略：无 BiomeCache（确定性优先，见类注释）。
     */
    @Override
    public BiomeGenBase[] getBiomeGenAt(BiomeGenBase[] listToReuse, int x, int z, int width, int length,
        boolean cacheFlag) {
        if (listToReuse == null || listToReuse.length < width * length) {
            listToReuse = new BiomeGenBase[width * length];
        }
        if (this.genChain == null) {
            Arrays.fill(listToReuse, 0, width * length, null);
            return listToReuse;
        }
        final int[] ids = this.genChain.fineInts(x, z, width, length);
        for (int i = 0; i < width * length; i++) {
            listToReuse[i] = biomeById(ids[i]);
        }
        return listToReuse;
    }

    /**
     * 可行性判定（<b>粗层</b>，B1 起对齐 vanilla {@code WorldChunkManager.areBiomesViable}：
     * {@code (x±range)>>2} 的粗层窗整窗 ∈ 入参群系表才算可行）。空表降级恒 {@code false}
     * （旧口径 contains(null) 恒 false 的等价收口：无身份即不可行）。
     */
    @Override
    public boolean areBiomesViable(int x, int z, int range, List<BiomeGenBase> biomes) {
        if (this.genChain == null) {
            return false;
        }
        final int startX = (x - range) >> 2;
        final int startZ = (z - range) >> 2;
        final int spanX = ((x + range) >> 2) - startX + 1;
        final int spanZ = ((z + range) >> 2) - startZ + 1;
        final int[] ids = this.genChain.coarseInts(startX, startZ, spanX, spanZ);
        for (final int id : ids) {
            if (!biomes.contains(biomeById(id))) {
                return false;
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
