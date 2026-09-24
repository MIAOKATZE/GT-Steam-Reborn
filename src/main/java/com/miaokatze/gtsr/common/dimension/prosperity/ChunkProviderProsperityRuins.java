package com.miaokatze.gtsr.common.dimension.prosperity;

import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;

import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.framework.BiomePlaneAccess;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority.BiomeId;
import com.miaokatze.gtsr.common.dimension.framework.GTSRChunkProviderBase;
import com.miaokatze.gtsr.common.dimension.framework.GTSRSurfaceBorderBand;
import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;
import com.miaokatze.gtsr.common.dimension.framework.structure.ChunkClampedSink;
import com.miaokatze.gtsr.common.dimension.framework.structure.GTSRWorldgenHash;
import com.miaokatze.gtsr.common.dimension.prosperity.biome.BiomeBrassWastes;
import com.miaokatze.gtsr.common.dimension.prosperity.biome.BiomeFumaroleSwamp;
import com.miaokatze.gtsr.common.dimension.prosperity.biome.BiomeGearworkForest;
import com.miaokatze.gtsr.common.dimension.prosperity.biome.BiomeRustedSteppe;
import com.miaokatze.gtsr.common.dimension.prosperity.biome.BiomeSanzuRiver;
import com.miaokatze.gtsr.common.dimension.prosperity.river.GTSRRiverPlacer;
import com.miaokatze.gtsr.common.dimension.prosperity.river.GTSRVoronoiRiverField;
import com.miaokatze.gtsr.main.GTSteamReborn;

/**
 * 繁荣维度地形生成器（dim1 S4b，plan §1.2 :59-65 / 02 §2 参数表；S-A1 起自然区按群系独立化）。
 * <p>
 * 地形模型 = {@link ProsperityTerrainProfile#heightAt} 高度场（全维度统一缓丘 + 低频幅度调制，
 * 模板异变简化口径，见 Profile 类注释）：每列 y=0..bedrockDepth 基岩（深度 1-4，02 §2.1
 * "基岩层 y0-4"口径）、其上 stone 填至 heightAt，以上留空气。<b>v1.20.39 T4 起 heightAt 已含
 * 河谷压低链（plan §3.2），自然水只经 populate 后置的河流水面回填出现（本类 {@link #onPopulate}
 * → GTSRRiverPlacer），generateTerrain 阶段仍零流体</b>；无洞穴/矿洞（02 §4/§5 裁剪，plan §1 范围红线）。
 * <p>
 * 表面与主体替换（<b>P2 起表层链上收框架</b>，S-A1 plan §12 修订 4）：generateTerrain 保持
 * stone 主体（框架 provideChunk 在 generateTerrain 之后才加载 biomes 数组，主体替换无法前移）；
 * 框架内核逐列自顶向下——首个"stone + 上方空气"裸露面落 top（meta 走 biome.field_150604_aj），
 * 其下 filler 1-2 格落 biome.fillerBlock（meta 经 {@link #fillerMetaOf} 直写），其余 stone 主体
 * 整段替换为群系 base 方块（{@link #baseBlockOf}）。本类对该链的贡献只剩一份声明式
 * {@link #spec()}（扫描主体＝stone、写 top/filler meta、整段换主体、不提前 break）与两个材质
 * 钩子；事件段、降级门、下标口径均在 {@code GTSRChunkProviderBase}。不调
 * {@code biome.genTerrainBlocks}（其 parabolic/62 带逻辑服务于原版噪声管线的 meta 现状）。
 * <p>
 * <b>高度红线</b>：地形与古代城（IWorldGenerator 通道）共用 {@link ProsperityTerrainProfile
 * #heightAt(World.getSeed(), x, z)} 同一纯函数——本类不做任何跨 chunk 方块读取；基岩带与
 * heightAt 本切片零改动。
 */
public class ChunkProviderProsperityRuins extends GTSRChunkProviderBase {

    public ChunkProviderProsperityRuins(World world, long seed) {
        super(world, seed);
    }

    /**
     * 高度场地形填充：Block[] 下标 x&lt;&lt;12 | z&lt;&lt;8 | y（S1 框架约定）。
     * 每列：基岩（y=0 恒有，1..3 按列哈希递减概率，整体深度 1-4 对齐 02 §2.1 基岩带）+
     * stone 至 heightAt（含）。
     * <p>
     * <b>P3</b>：本方法内的 {@link #bedrockTop(long,int,int)} 与 {@code ChunkProviderShatteredGrounds}
     * 的同名私有件<b>逐字符相同</b>，已并为框架件一份；哈希算法本身在
     * {@code GTSRWorldgenHash.bedrockTopHash}（本片只搬位置，不改任何数值）。
     */
    @Override
    protected void generateTerrain(int chunkX, int chunkZ, Block[] blocks, byte[] metadata,
        BiomeGenBase[] biomesForGeneration) {
        final long worldSeed = this.worldObj.getSeed();
        final int baseX = chunkX * 16;
        final int baseZ = chunkZ * 16;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                final int column = x << 12 | z << 8;
                final int height = ProsperityTerrainProfile.heightAt(worldSeed, baseX + x, baseZ + z);
                final int bedrockTop = bedrockTop(worldSeed, baseX + x, baseZ + z);
                for (int y = 0; y <= bedrockTop; y++) {
                    blocks[column | y] = Blocks.bedrock;
                }
                for (int y = bedrockTop + 1; y <= height; y++) {
                    blocks[column | y] = Blocks.stone;
                }
            }
        }
    }

    /**
     * 表层/主体替换核心（P2 起为框架 {@code GTSRChunkProviderBase.applyBiomeSurface} 的
     * <b>dim78 声明式规格 + 公开静态缝</b>）：
     * <ul>
     * <li>规格：扫描主体 {@code Blocks.stone}、top 写 {@code biome.field_150604_aj}（差异①）、
     * filler meta 查 {@link #fillerMetaOf}、filler 段以下<b>整段</b>换 {@link #baseBlockOf}
     * （差异②）、<b>不</b>提前 break（差异③，必须扫到 y=1 才能整段换主体）、不跳过悬空格；</li>
     * <li>零 World 依赖的纯数组变换由框架保证，本静态缝供
     * {@code tools/dim1/ReplaceSurfaceRuntimeCheck} / {@code SurfaceByteParityDump} /
     * {@code SurfaceDegradationCheck} / {@code SurfaceTranspositionCheck} 离线驱动同一运行时代码；</li>
     * <li>降级态（缺席群系 / 空表）是否铺表层由框架门统一决定（plan §5 P2 判据 B）：
     * 缺席列一格不写，绝不出现 plains/grass/dirt。</li>
     * </ul>
     */
    public static void applyBiomeSurface(long worldSeed, int baseX, int baseZ, Block[] blocks, byte[] metadata,
        BiomeGenBase[] biomes) {
        GTSRChunkProviderBase.applyBiomeSurface(worldSeed, baseX, baseZ, blocks, metadata, biomes, spec());
    }

    /** 本 provider 的表层规格（每次新建：{@code bodyBlock} 惰性读，避免早于 BlockLoader 定值）。 */
    private static GTSRChunkProviderBase.SurfaceSpec spec() {
        return new GTSRChunkProviderBase.SurfaceSpec(
            GTSRBiomeAuthority.DIM_KEY_PROSPERITY,
            () -> Blocks.stone,
            true,
            ChunkProviderProsperityRuins::fillerMetaOf,
            ChunkProviderProsperityRuins::baseBlockOf,
            false,
            false,
            // v1.20.41 P20 S5（plan §15.4）：湖滨湿带 = S1 混合带的包装层（先原样委托、只在湿带列
            // 改派同维名册内的河滩料）。域外列逐字退回 S1 结果，见 LakeWetBandTopSelector 契约段。
            // SurfaceSpecUnreachableCheck 的"分配点恰 1 处"计数不受影响（仍是本行一处 new）。
            new LakeWetBandTopSelector());
    }

    @Override
    protected GTSRChunkProviderBase.SurfaceSpec surfaceSpec() {
        return spec();
    }

    /**
     * L1 群系身份（两跳：实例账本 → 实际 id 反查，与 {@code ShatteredBiomes.identityOf} 同形状；
     * plan §2.1 L1「身份的唯一出口」）。返回 {@code null} = 本维名册点名不到（外来群系 /
     * 未注册实例）。第二跳仅命中账本记录的<b>实际 id</b>（{@link GTSRBiomeAuthority#actualIdOf}），
     * 不做 {@code biomeID - idStart} 减法，供离线合成实例与注册后新建实例同样可命名。
     */
    public static BiomeId identityOf(BiomeGenBase biome) {
        if (biome == null) {
            return null;
        }
        final GTSRBiomeAuthority authority = GTSRBiomeAuthority.forDimKey(GTSRBiomeAuthority.DIM_KEY_PROSPERITY);
        final GTSRBiomeAuthority.Resolution byInstance = authority.of(biome);
        if (byInstance.resolved()) {
            return byInstance.biomeId;
        }
        for (final BiomeId key : BiomeId.values()) {
            if (key.dimKey()
                .equals(GTSRBiomeAuthority.DIM_KEY_PROSPERITY) && authority.actualIdOf(key) == biome.biomeID) {
                return key;
            }
        }
        return null;
    }

    /**
     * 群系主体 base 方块映射（S-A1，plan §12 修订 4：自然区主体 stone 按群系替换为 base 方块；
     * v1.20.39 G4 地底石化，plan §3.7：四群系 wholeBody 统一改返 prosperityStone——整段同质单一变体，
     * 第二变体无消费面不做。topBlock/filler 表层链保留现状，各群系 base 壤土方块仍注册、仍作 filler）。
     * <b>P2b 起身份读面收口 L1</b>：经 {@link #identityOf} 解析为 {@link BiomeId} 后 switch——
     * 不再 {@code instanceof} 判类（plan §2.1 L1 禁止项），也不读 Chunk 保存的 byte 平面。
     * 名册点名不到（S1 平坦模板群系 / 外来群系 / 缺席实例）回退 {@link Blocks#stone}，与
     * 改造前 instanceof 未命中口径逐格一致（行为矩阵由
     * {@code tools/dim1/SurfaceBiomeMatrixCheck} 行为钉断言，逐字节回归由
     * {@code SurfaceByteParityDump} 对拍断言；wholeBody 口径变化的重钉归 v1.20.39 T8）。
     */
    private static Block baseBlockOf(BiomeGenBase biome) {
        final BiomeId key = identityOf(biome);
        if (key == null) {
            return Blocks.stone;
        }
        switch (key) {
            case RUSTED_STEPPE: {
                return BlocksGTSR.prosperityStone;
            }
            case GEARWORK_FOREST: {
                return BlocksGTSR.prosperityStone;
            }
            case BRASS_WASTES: {
                return BlocksGTSR.prosperityStone;
            }
            case FUMAROLE_SWAMP: {
                return BlocksGTSR.prosperityStone;
            }
            case SANZU_RIVER: {
                // T5：遗忘之川 wholeBody 同走 prosperityStone（G4 全群系统一口径；生产路径
                // provideChunk 期平面是链面 4 家，本分支供 populate 后置写平面后的离线/复算消费面）
                return BlocksGTSR.prosperityStone;
            }
            default: {
                return Blocks.stone;
            }
        }
    }

    /**
     * filler meta 群系映射（S-A1 起独立方块族 meta 恒 0，plan §12 修订 2/4）。
     * <b>P2b 起身份读面收口 L1</b>：同 {@link #baseBlockOf} 经 {@link #identityOf} 后 switch，
     * meta <b>数值仍逐字取自各群系类的 {@code FILLER_META} 常量</b>（群系侧单一真值，本方法
     * 只做"身份 → 常量"的选取，不复制数值）；名册点名不到回退 meta 0，与改造前 instanceof
     * 未命中口径一致。表与群系常量的对应关系由 {@code tools/dim1/SurfaceBiomeMatrixCheck}
     * 的<b>行为钉</b>（真实表层链输出逐格对账）钉住，不再是源码文本钉。
     */
    private static int fillerMetaOf(BiomeGenBase biome) {
        final BiomeId key = identityOf(biome);
        if (key == null) {
            return 0;
        }
        switch (key) {
            case RUSTED_STEPPE: {
                return BiomeRustedSteppe.FILLER_META;
            }
            case GEARWORK_FOREST: {
                return BiomeGearworkForest.FILLER_META;
            }
            case BRASS_WASTES: {
                return BiomeBrassWastes.FILLER_META;
            }
            case FUMAROLE_SWAMP: {
                return BiomeFumaroleSwamp.FILLER_META;
            }
            case SANZU_RIVER: {
                return BiomeSanzuRiver.FILLER_META;
            }
            default: {
                return 0;
            }
        }
    }

    /**
     * populate 钩子（<b>= dim78 河流水面回填的唯一生产入口</b>，v1.20.39 T4 起按 plan §3.2
     * 接 {@link GTSRRiverPlacer} 新河流场：水面回填（s≥WET_MIN 且 h1&lt;SEA_LEVEL 置水至
     * y=67）/ riverStyle 档表（浅滩·干谷断流·沼地河）/ 瀑布落差列处理 / 河床料铺放；
     * <b>T5 起追加巨湖回填与遗忘之川群系指派</b>，见 {@link #fillSanzuLakes} 与
     * {@link #assignSanzuRiverBiome}；<b>v1.20.40 P19 §E 起追加沼泽微池回填</b>
     * （{@link #fillSwampPools}）、§I 起全部水体 = 深渊执念（{@link GTSRRiverPlacer#waterMaterial()}，
     * 视觉色由 BlockAbyssalFluid.colorMultiplier 按群系分档）——后置链序 plan §5：水面回填 →
     * 巨湖回填 → 微池回填 → sanzu 写平面 → decorate
     * （decorate 在本方法返回之后才由 GameRegistry.generateWorld 驱动，写入当趟生效）。
     * <p>
     * <b>为什么只能挂在这里</b>（P17-Q2 裁决，码据见 {@link GTSRRiverPlacer} 类注释）：框架表层内核
     * {@code GTSRChunkProviderBase.applyBiomeSurface} 的列门是「本格须为主体方块」({{@code :348})+
     * 「上方须为空气或空槽」（{@code :352} 与 {@code isAirOrEmpty:90-92}）——在
     * {@link #generateTerrain} 阶段往裸数组里灌水会让"水在 stone 之上"的那些列<b>整列不铺表层、
     * 不换主体</b>（P17-B 码据）。那是三份表层字节对拍（{@code SurfaceByteParityDump} /
     * {@code SurfaceDegradationCheck} / {@code SurfaceTranspositionCheck}）的口径面，本片<b>一字未动</b>。
     * 故水只能在 Chunk 组装、表层替换之后写，本方法是 dim78 唯一那个钩子（框架 {@code populate}
     * 只转调它，形与 dim79 侧 {@code ChunkProviderShatteredGrounds:109} 的装饰挂点同构）。
     * <p>
     * <b>不消费 {@code random} 参数</b>：河流与 sanzu 的全部判定走 {@link GTSRVoronoiRiverField} 的
     * 纯函数（必须"任一 chunk 可独立重算"才接得上跨 chunk 的河道），因此本方法一处都不碰
     * {@code this.rand} ⇒ 既有 rand 取数序一字不变，兄弟挂点（结构/装饰在
     * {@code GameRegistry.generateWorld} 阶段、<b>晚于</b>本方法）看到的世界状态只多了"河/湖/sanzu"。
     * <p>
     * 与四族同源：本方法不新算任何高度——断面用的地表就是
     * {@link ProsperityTerrainProfile#heightAt}（与 {@link #generateTerrain} 同一算式，且已含
     * 河谷压低与巨湖压低链）。
     */
    @Override
    protected void onPopulate(Random random, int chunkX, int chunkZ) {
        final long worldSeed = this.worldObj.getSeed();
        final BlockSink sink = new ChunkClampedSink(this.worldObj, chunkX, chunkZ);
        GTSRRiverPlacer.place(this.worldObj, worldSeed, chunkX, chunkZ, sink);
        fillSanzuLakes(worldSeed, chunkX, chunkZ, sink);
        fillSwampPools(worldSeed, chunkX, chunkZ, sink);
        assignSanzuRiverBiome(worldSeed, chunkX, chunkZ);
    }

    // ═════════════════ v1.20.39 T5（plan §3.3）：巨湖回填 + 遗忘之川指派 ═════════════════

    /** 巨湖回填观察窗口（chunk 数；与 GTSRRiverPlacer 的 256 口径同款量级，单独一条 lake 行）。 */
    private static final int LAKE_LOG_WINDOW_CHUNKS = 1024;

    private static final AtomicLong LAKE_CHUNKS_SERVED = new AtomicLong();
    private static final AtomicLong LAKE_WATER_CELLS = new AtomicLong();
    /** v1.20.41 P20 S5：已写岛底柱的<b>列数</b>（每湖 5 柱 × 3×3 = 45 列量级，供 §15.6 判据 3 取数）。 */
    private static final AtomicLong LAKE_PILLAR_COLUMNS = new AtomicLong();
    /** v1.20.41 P20 S5：岛底柱实际<b>写入的方块格数</b>（口径 = 本地表之上的部分，见 {@link #fillSanzuLakes}）。 */
    private static final AtomicLong LAKE_PILLAR_CELLS = new AtomicLong();
    /** 沼泽微池回填观察（v1.20.40 P19 §E；窗口与巨湖行同款）。 */
    private static final AtomicLong SWAMP_CHUNKS_SERVED = new AtomicLong();
    private static final AtomicLong SWAMP_WATER_CELLS = new AtomicLong();
    /**
     * v1.20.41 P20 S5：沼泽三档 + NONE 的"<b>实际送水</b>列数"分桶累计（下标 =
     * {@code TerrainVariants.SWAMP_TIER_NONE/POOL/DEEP/MARSH} 的 int 值）。与
     * {@link #SWAMP_WATER_CELLS}（水格数）互补：本表回答"哪一档真的被灌到了"，
     * 是 §21-F 三档分布位移的复测取数口。
     */
    private static final AtomicLong[] SWAMP_TIER_WATERED_COLS = { new AtomicLong(), new AtomicLong(), new AtomicLong(),
        new AtomicLong() };

    /**
     * <b>湖滨湿带的表层选择器</b>（v1.20.41 P20 S5，plan §15.4 第三条形态「湿带：水陆之间一条
     * <b>不积水</b>的半湿表层带」）。
     * <p>
     * <b>它是 P20 S1 混合带的包装层，不是第二真值</b>——这是 §15.4「复用 S1 的表层钩子出口
     * （{@code SurfaceTopSelector}），不得另起第二真值」的落地形态：
     * <ol>
     * <li>群系交界的<b>皮肤选择</b>逻辑一行都不在此重写：本类第一个动作就是
     * {@link GTSRSurfaceBorderBand#forChunk} 拿到 S1 那份 selector，并把每一列<b>原样委托</b>给它；
     * 委托结果在本类眼里只是"这一列 S1 会铺什么"，湿带只在 S1 答案之上做一次<b>范围极窄的改派</b>
     * （判据 = {@link GTSRVoronoiRiverField#lakeWetBandAt}，三门：湖滨带内 + 地表贴水 +
     * 不深于水面 2 格）。域外的列 ⇒ 逐字返回委托结果 ⇒ 与框架默认路径逐字节相同。
     * <b>P22 A2b（G3）改写上句的判据</b>：三门布尔谓词换成同包内
     * {@link LakeWetBandTopSelector#wetBandGravelAt}——核心（三门全真）恒砾一字不动，仅在各门
     * 外缘加五档噪声覆盖率外檐（A2a 读数：布尔边 ⇒ 外缘 100% 材质阶跃）；
     * {@code lakeWetBandAt} 本体仍是河流场的公开谓词（消费面 = 本包装层核心 + 离线探针）。</li>
     * <li>改派只取<b>同维名册内已注册的方块</b>（{@link BlocksGTSR#prosperityRiverGravel}，
     * 即 {@code GTSRRiverPlacer} 现有的河滩料），零新方块（H-4 ⇒ 名册读数 408 不变），
     * 也不引入 plains/grass/dirt（S1 降级口径的强条件仍然成立）。</li>
     * <li>框架侧那条"spec.topSelector == null ⇒ 走 {@code forChunk} 默认"的解析序
     * （{@code GTSRChunkProviderBase.applyBiomeSurface:394}）<b>不被绕过、只被前移</b>：
     * 本类替框架调了同一次 {@code forChunk}，入参（dimKey / 未掺盐 worldSeed / baseX / baseZ）
     * 与框架那行逐字同值 ⇒ 两条路只会剩一条生效，不会同时存在两份混合带实现。</li>
     * </ol>
     * <p>
     * <b>为什么 delegate 是懒绑定的</b>：{@code SurfaceSpec} 由 {@code surfaceSpec()} <b>每 chunk
     * 新建一份</b>（{@code GTSRChunkProviderBase:293}），但该钩子签名里没有 chunk 坐标，而
     * {@code forChunk} 的固定成本（一条短命粗层链 + 一张粗格身份窗）绝不可下沉到逐列
     * （S1 类注释的同一条纪律）。故本实例在<b>首列</b>用世界坐标反推 chunk 原点
     * （{@code x & ~15}）解析一次并持有；实例的生命周期 = 一个 chunk ⇒ 状态不可能跨 chunk 泄漏，
     * 而框架警告的"任何跨列状态都会把 chunk 边界写进地表"在这里由"坐标一变即重解析"挡死
     * （生产不可达，防御口径）。
     */
    private static final class LakeWetBandTopSelector implements GTSRChunkProviderBase.SurfaceTopSelector {

        /** S1 混合带本体；{@code null} = 本维不启用混合带（未绑定/白名单外），此时基线 = biome.topBlock。 */
        private GTSRChunkProviderBase.SurfaceTopSelector blended;
        private boolean resolved;
        private int boundBaseX;
        private int boundBaseZ;

        /** G3（P22 A2b）羽化盐域：ASCII {@code "WETB"}——新域，与 BORDER/河/湖/主干/振幅各域互不相关。 */
        private static final long S_WETB_JITTER = 0x57455442L;
        /** 羽化噪声波长（方块）：{@code 13 % 16 = 13}（H-1），与 S1 BORDER 同数值但独立盐域。 */
        private static final double WETB_JITTER_SCALE = 13.0D;
        /**
         * 门①外檐的"一格"湖压当量：带压宽 {@code SHORE−WATER = 0.02} ÷ 实测环带宽 ≈14 格
         * ≈ 0.00143，取 {@code 0.0015}（压力梯度按环带均摊，湖形 warp 处会有 ±数格出入，
         * 但外檐总深 ≤5 格当量 ⇒ 对判定形状不敏感；实际覆盖率以探针复测为准）。
         */
        private static final double WETB_HALO_UNIT = 0.0015D;
        /**
         * 外檐五档铺砾阈值（{@code valueNoise} ∈ [-1,1)，{@code >= 阈值} 即铺）。分位基准取自
         * {@code plan/tmp/p22-a2b/calib}（scale=13、同 seed 网格：-0.80≈0.97 / -0.74≈0.96 /
         * -0.65≈0.93 / -0.60≈0.92 / -0.50≈0.85）；但 valueNoise 是 13 格<b>平滑场</b> ⇒ 边界按
         * "段"整体制裁、实测覆盖系统性偏离网格分位（段内近 0 或近 1），故阈值经三轮 A2bProbe
         * 实测校准：第一轮（-0.71/-0.58/-0.40/-0.25/-0.06，MAX=8）dryPureSkin=7.4%；第二轮
         * （-0.77/-0.71/-0.58/-0.50/-0.40，MAX=10）stepRate=5.59% 达标、dryPureSkin=5.27% 微超；
         * 本值 = 第三轮（终读数复跑见 {@code plan/tmp/p22-a2b/PROGRESS.md}，两向申报）。
         * 逐档递减即羽化梯度，檐缘无连续等值线；出檐 >{@link #WETB_HALO_MAX} 格当量不铺
         * （陡崖/急压段的硬边 = 地形自身的边，非布尔门产物，不属本判据对象，保留并披露）。
         */
        private static final double WETB_T1 = -0.80D;
        private static final double WETB_T2 = -0.74D;
        private static final double WETB_T3 = -0.65D;
        private static final double WETB_T4 = -0.60D;
        private static final double WETB_T5 = -0.50D;
        /** 外檐最大深度（格当量）：超界整档排除，不向远处外扩。 */
        private static final double WETB_HALO_MAX = 10.0D;

        @Override
        public Block topAt(long seed, int x, int z, BiomeGenBase biome) {
            if (!this.resolved) {
                this.resolved = true;
                this.boundBaseX = x & ~15;
                this.boundBaseZ = z & ~15;
                this.blended = GTSRSurfaceBorderBand
                    .forChunk(GTSRBiomeAuthority.DIM_KEY_PROSPERITY, seed, this.boundBaseX, this.boundBaseZ);
            } else if ((x & ~15) != this.boundBaseX || (z & ~15) != this.boundBaseZ) {
                // 越出本实例绑定的 chunk（生产不可达：spec 每 chunk 新建）⇒ 重绑定，绝不沿用旧窗
                this.boundBaseX = x & ~15;
                this.boundBaseZ = z & ~15;
                this.blended = GTSRSurfaceBorderBand
                    .forChunk(GTSRBiomeAuthority.DIM_KEY_PROSPERITY, seed, this.boundBaseX, this.boundBaseZ);
            }
            final Block base = this.blended == null ? biome.topBlock : this.blended.topAt(seed, x, z, biome);
            if (base == BlocksGTSR.prosperityRiverGravel) {
                return base; // 已是湿料，省一次湖场求值
            }
            // P22 A2b（G3）：布尔改派换为「核心恒砾 + 三门外缘五档噪声外檐」（见 wetBandGravelAt）；
            // 改造前表达式为 `lakeWetBandAt(seed,x,z) ? gravel : base`（三门全布尔 ⇒ 外缘 100% 阶跃）。
            return wetBandGravelAt(seed, x, z) ? BlocksGTSR.prosperityRiverGravel : base;
        }

        /**
         * P22 A2b（G1）：filler 段直通 S1 混合带（与 top 同一档裁定、同一 meta 等值门，见
         * {@code GTSRSurfaceBorderBand#fillerAt}）。湿带/羽化檐列<b>不改</b>下垫——湿料语义只作用
         * 在裸露面 top（plan §15.4 原契约），湖床底下的填充层维持群系列。
         * <p>
         * <b>不重复绑定</b>（刻意不调 forChunk 第二条路）：内核同列循环先写 top 后写 filler、
         * spec 每 chunk 新建 ⇒ 本方法被调时 topAt 已完成绑定；若未触发过（理论不可达）则
         * {@code blended==null} 恰为直通回退，不存在"用错 chunk 窗"的路径。Sanzu L4 的
         * "forChunk 实参面计数恰 2"钉因此零移动。
         */
        @Override
        public Block fillerAt(long seed, int x, int z, BiomeGenBase biome) {
            return this.blended == null ? biome.fillerBlock : this.blended.fillerAt(seed, x, z, biome);
        }

        /**
         * <b>湿带外缘羽化判定</b>（P22 A2b，G3）：本列裸露面是否铺湿料
         * （{@link BlocksGTSR#prosperityRiverGravel}）。
         * <p>
         * A2a 取证（{@code plan/tmp/p22-a2a/REPORT.md} §1）：改造前三门（湖滨带 ∧ h≤SEA ∧
         * h≥SEA−{@code LAKE_WET_BAND_DROP}，与 {@code GTSRVoronoiRiverField#lakeWetBandAt} 同式）
         * 是纯布尔 ⇒ 湿带外缘 2133/2133 = 100% 材质阶跃、94.2% 为砾→异族皮全族跳。本判定
         * <b>核心一字不动</b>：核心分支<b>直接复用</b> {@code lakeWetBandAt}（非复刻）⇒ 三门全真
         * 恒铺砾由构造保证，plan §15.4 的湿带语义与湖岸衔接判据（Sanzu A 组）不受影响。
         * <p>
         * 外缘改为<b>连续出檐距离</b> e 上的<b>五档覆盖率外檐</b>：
         * <ul>
         * <li>{@code e = max(高度出窗块数, 距带区间 [WATER,SHORE) 的格当量数)}——高度腿按<b>方块</b>
         * 计（h−sea 本身就是格点跳），湖压腿按 {@link #WETB_HALO_UNIT}（粗格/格当量）折算，
         * 两腿共用阈值梯（几何上陡岸 1 方块与 1 粗格当量同属"贴檐"，无第三条真值）；
         * "双门同出窗"的角部列自动归入较高档；<b>湖盆侧与陆侧对称羽化</b>：A2a 边集实测 39%
         * 的"外缘边"面向湖盆侧（水线列），只羽化陆侧到不了验收线；浅水砾滩本就是水陆之间的
         * 自然形态，深湖盆（e 超界）恒不铺——改派集合 = 核心 ∪ 双侧外檐散点；</li>
         * <li>档一 e≤1 覆盖 ≈0.96、档二 e≤2 ≈0.95、档三 e≤3 ≈0.90、档四 e≤5 ≈0.85、
         * 档五 e≤10 ≈0.80、e>10 不铺（不向远处外扩；陡崖/急压段本就留硬边，见阈值注释）；</li>
         * <li>裁定 = 逐列值噪声（盐域 {@code "WETB"}、波长 13）对照档位阈值——纯世界坐标
         * 函数 ⇒ 跨 chunk 一致；覆盖率随 e 逐档递减 ⇒ 檐缘无连续等值线（羽化的定义面）。</li>
         * </ul>
         * 候选方块仍只有 prosperityRiverGravel（零新方块，H-4）。验收读数（阶跃率/全族跳/
         * 各档覆盖率）在 {@code plan/tmp/p22-a2b/PROGRESS.md}（A2bProbe after 跑）。
         */
        private static boolean wetBandGravelAt(long worldSeed, int x, int z) {
            // 核心 = 原三门谓词<b>逐字复用</b>（lakeWetBandAt 本体，非复刻）：三门全真恒铺砾，
            // 改派集合包含关系由构造保证（A2a 验收"核心不变"的机制面凭据）。
            if (GTSRVoronoiRiverField.lakeWetBandAt(worldSeed, x, z)) {
                return true;
            }
            // 外檐 = 到三门判定域 [WATER, SHORE)×[SEA−drop, SEA] 的连续出檐距离（格当量），
            // 干侧与湖盆侧共用同一标尺：水线两侧都是"湿→干"的自然渐变带（浅滩砾），
            // 而 A2a 边集里 39% 的外缘边实际面向湖盆侧——只羽化陆侧永远到不了验收线。
            final double lake = GTSRVoronoiRiverField.lakeAt(worldSeed, x, z);
            final int sea = ProsperityTerrainProfile.SEA_LEVEL;
            final int drop = GTSRVoronoiRiverField.LAKE_WET_BAND_DROP;
            final int h = ProsperityTerrainProfile.heightAt(worldSeed, x, z);
            double e = Math.max(h - sea, sea - drop - h);
            final double eBand = Math.max(
                (lake - GTSRVoronoiRiverField.LAKE_SHORE) / WETB_HALO_UNIT,
                (GTSRVoronoiRiverField.LAKE_WATER_LEVEL - lake) / WETB_HALO_UNIT);
            if (eBand > e) {
                e = eBand;
            }
            if (e > WETB_HALO_MAX) {
                return false; // 湖心深盆 / 高台 / 带远侧：整档排除，不外扩
            }
            final double n = wetBandJitter(worldSeed, x, z);
            final double t = e <= 1.0D ? WETB_T1
                : e <= 2.0D ? WETB_T2 : e <= 3.0D ? WETB_T3 : e <= 5.0D ? WETB_T4 : WETB_T5;
            return n >= t;
        }

        /** 羽化抖动场（独立盐域 + 波长 13；与 S1 BORDER_JITTER 无相关）。 */
        private static double wetBandJitter(long worldSeed, int x, int z) {
            return GTSRWorldgenHash.valueNoise(worldSeed ^ S_WETB_JITTER, x / WETB_JITTER_SCALE, z / WETB_JITTER_SCALE);
        }
    }

    /**
     * <b>巨湖水面回填</b>（populate 后置，水面口径 68 与河流回填同一条）：主干带内
     * {@code lakeAt < LAKE_SHORE}（湖水区+湖滨带；带外 lakeAt 恒 {@code NO_LAKE} 哨兵 ⇒ 零成本
     * 短路）且列地表 {@code h1 < SEA_LEVEL} 的列，从地表向上置水至 y=67。地形压低（渐深湖床）
     * 已由 {@link ProsperityTerrainProfile#heightAt} 完成（本方法只回填，不切地形）；与河流回填的
     * 重叠列两次写同值水，幂等。同样不消费 populate 的 {@code Random}（纯函数判定）。
     * <p>
     * v1.20.40（P19 §D/§I）：湖水区已随 heightCore 渐深（湖心最深 {@code LAKE_CENTER_DEPTH}
     * 格）+ 湖形 domain-warp 破圆；水体 = {@link GTSRRiverPlacer#waterMaterial()}（深渊执念，
     * meta 0 静态源）。
     * <p>
     * <b>v1.20.41 P20 S5（plan §15.3/§15.5）追加两处</b>：
     * <ol>
     * <li><b>水深 10 → 28</b> 使本段的回填水柱从 9–10 格变成 <b>27–28 格</b>（床 40/41 → 水面顶 67）；
     * 置水高度式 {@code y ∈ [h+1, SEA_LEVEL−1]} <b>一字未改</b> ⇒ 水柱长度是 heightCore 床深的
     * 被动读数，本方法不另立水深真值。</li>
     * <li><b>岛底柱</b>：置水循环之后同 chunk 再扫一趟 {@link GTSRVoronoiRiverField#islandPillarAt}
     * 为真的列，把 {@code y ∈ [LAKE_PILLAR_FLOOR_Y, LAKE_PILLAR_TOP_Y]} = [40,71] 中<b>本地表之上</b>
     * 的格写 {@link BlocksGTSR#prosperityStone}（已注册、非 top ⇒ H-4 安全）。
     * 本地表以下本来就是 {@link #generateTerrain} 填的实心 stone ⇒ 只补 [h+1,71] 即得
     * <b>[40,71] 逐格连续</b>的柱，写量从 32 格/列降到最多 31、中心柱常为 0。
     * 逐列谓词 ⇒ 每根柱的每一列由<b>它自己所在 chunk</b> 写满同一个 y 区间，3×3 截面骑在 chunk
     * 边界上也只是"两边各写自己那几列"，{@code ChunkClampedSink} 零越界丢弃。
     * 与 {@code ChunkSpans.MAX_SLICE_HEIGHT = 12} 无关（§15.5 表末行：柱高 32 格走不了字符盘模板路）。
     * <b>不消费 populate 的 {@code Random}</b>（同既有纪律，H-3）。</li>
     * </ol>
     * 观察口径：{@code LAKE_WATER_CELLS} 在写柱<b>之前</b>累计 ⇒ 被柱吃到的那几格水同时计入两个
     * 计数器（水柱长度读数会略偏大，属已知观察计数器语义，不改）。
     */
    private static void fillSanzuLakes(long worldSeed, int chunkX, int chunkZ, BlockSink sink) {
        final Block water = GTSRRiverPlacer.waterMaterial();
        int waterCells = 0;
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                final int x = (chunkX << 4) + lx;
                final int z = (chunkZ << 4) + lz;
                if (GTSRVoronoiRiverField.lakeAt(worldSeed, x, z) >= GTSRVoronoiRiverField.LAKE_SHORE) {
                    continue;
                }
                final int h = ProsperityTerrainProfile.heightAt(worldSeed, x, z);
                if (h < ProsperityTerrainProfile.SEA_LEVEL) {
                    for (int y = h + 1; y <= ProsperityTerrainProfile.SEA_LEVEL - 1; y++) {
                        if (sink.setBlock(x, y, z, water, 0, BlockSink.FLAG_POPULATE)) {
                            waterCells++;
                        }
                    }
                }
            }
        }
        LAKE_WATER_CELLS.addAndGet(waterCells);
        // ═══ v1.20.41 P20 S5（plan §15.5）岛底柱：置水之后写柱 ⇒ 柱身覆盖水格，后写者胜 ═══
        int pillarColumns = 0;
        int pillarCells = 0;
        final int top = GTSRVoronoiRiverField.LAKE_PILLAR_TOP_Y;
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                final int x = (chunkX << 4) + lx;
                final int z = (chunkZ << 4) + lz;
                if (!GTSRVoronoiRiverField.islandPillarAt(worldSeed, x, z)) {
                    continue;
                }
                pillarColumns++;
                final int h = ProsperityTerrainProfile.heightAt(worldSeed, x, z);
                for (int y = Math.max(GTSRVoronoiRiverField.LAKE_PILLAR_FLOOR_Y, h + 1); y <= top; y++) {
                    if (sink.setBlock(x, y, z, BlocksGTSR.prosperityStone, 0, BlockSink.FLAG_POPULATE)) {
                        pillarCells++;
                    }
                }
            }
        }
        LAKE_PILLAR_COLUMNS.addAndGet(pillarColumns);
        LAKE_PILLAR_CELLS.addAndGet(pillarCells);
        if (LAKE_CHUNKS_SERVED.incrementAndGet() % LAKE_LOG_WINDOW_CHUNKS == 0) {
            GTSteamReborn.LOG.info(
                "[GTSR] dim78 sanzu lake over {} chunks: waterCells={} pillarColumns={} pillarCells={}"
                    + " (trunk-gated lakePressure, abyssal fluid, per-column pillar predicate)",
                LAKE_CHUNKS_SERVED.get(),
                LAKE_WATER_CELLS.get(),
                LAKE_PILLAR_COLUMNS.get(),
                LAKE_PILLAR_CELLS.get());
        }
    }

    /**
     * <b>沼泽微池回填</b>（v1.20.40 P19 plan §E，与 {@link #fillSanzuLakes} 同构的 populate
     * 后置回填）：roster 3（喷气沼泽）的微池列（{@link GTSRVoronoiRiverField#swampLakeAt}
     * &lt; {@link GTSRVoronoiRiverField#SWAMP_POOL_WATER_LEVEL}，水径 8-16 格）且列地表低于
     * 本段池水面（{@code h < pool−1}，与河流回填同一门）——从地表向上置水至 y=pool−1，
     * 水面 = 本段池水位（与沼泽河同水面，微池成"水面贴地的沼地水网"）。地形压低已由
     * heightCore 微池段完成（压至 {@code pool−2±0.5} ⇒ 常态 1-2 层水）。roster 门走
     * {@link GTSRRiverPlacer#tierGrid}（与 placer/heightCore 同一身份面，无第二真值）；
     * 水体 = {@link GTSRRiverPlacer#waterMaterial()}；不消费 populate 的 {@code Random}。
     * <p>
     * <b>v1.20.41 P20 S5 两处改动</b>（plan §5 S6-4 的"三档回填门"由 §23-C 转派到本片：本方法是
     * §23-C 点名的<b>第三处</b> {@code h < pool − 1} 复刻的宿主，与"回填门改取档位"是同一次改动，
     * 顺路收口零额外风险）：
     * <ol>
     * <li><b>回填门改取 {@link TerrainVariants#swampTierAt}</b>（需求 3「表面水池 / 深水池 / 水沼地」
     * 三档各走各自置水语义）：本列只要被判成<b>任一</b>水体档（POOL/DEEP/MARSH）就送水，不再只吃
     * 微池 Voronoi 压力门。改造前"地形侧已按档挖好床、回填侧却只认微池门"＝<b>挖而不灌</b>，
     * 正是需求 3 抱怨"太单调"的机制面。微池压力门作为 POOL 档的<b>并集</b>入口保留（防既有
     * 微池水网退化）。<b>置水高度式一字不改</b>（{@code y ∈ [h+1, pool−1]}）⇒ 三档水层厚度
     * 由 S4/S4b 已验收的<b>地形侧下挖深度</b>给出（POOL 1–2 层 / DEEP 5–9 层 / MARSH 0–1 层），
     * <b>零新增水位常量</b>。本片<b>只消费</b>档位，不改 {@code TerrainVariants} 的判档语义
     * （§21-D 的互斥修复已验收）。</li>
     * <li><b>消灭第三处复刻</b>：门 {@code h < pool − 1} 改走
     * {@link GTSRVoronoiRiverField#submergedAt(int, int)} 唯一出口。该出口<b>签名锁死 int</b>
     * （§23-A 对拍：合计 255,428 样本差异 0，而 double 形参对照臂差 2,093 例，全部来自
     * {@code pool = Integer.MIN_VALUE} 处的 int 回绕）⇒ <b>禁止"顺手泛化"</b>。</li>
     * </ol>
     * 观察：日志行增印三档<b>实际送水列数</b>分桶，供 §21-F 的三档分布位移复测取数。
     * <p>
     * <b>v1.20.42 P22 A3 两处改动</b>（沼泽水体避群系边缘 + 包含性收口）：
     * <ol>
     * <li><b>边缘门</b>：入口先乘 {@link TerrainVariants#swampInteriorAt}（coarse Chebyshev R=4 内
     * roster 全 3 才回填）。三档腿经 {@code swampTierAt} 的 {@code SWG_TIER} 槽已同门自动 NONE
     * （{@code TerrainVariants.swampGates} 单点分流），本方法补微池腿 ⇒ 三档+微池统一"距群系边缘
     * ≥16 格"；微池的<b>地形压低在 heightCore 微池段</b>（本片禁区）⇒ 边缘微池留 1-2 格干洼地、
     * 不置水（设计代价，见 A3 交付披露）。</li>
     * <li><b>包含性钳制（N8 不动点）</b>：18×18 网格上把沼泽水顶只降不升地钳到 8 邻阻挡面 min
     * （干列/被钳干空坑列 = 固体顶 h，0 余量——"8 邻固体顶 ≥ 水顶"验收口径，A1b 残潭 N8 钳制
     * 同族；水邻 = 其水面，水—水同面不流）——收口 v1.20.40 申报"微池 69/71 桶低地悬空"（改前
     * 8 seed×512² 实测悬空 874 列 = 边缘 681 + 腹地低地 193；1 级钳制仍漏 276 对"空坑邻外流"，
     * 故取 Jacobi 不动点，实测 2-3 趟收敛）；钳到本床面之下则整列不置水（"宁缺不悬"，
     * wg41-E-options D2 处置先例）。置水区间高度式 {@code y ∈ [h+1, 顶]} 与三档水层厚度语义
     * （由地形侧下挖深度给出）不变——钳制只在"会外流"的列上削顶。</li>
     * </ol>
     */
    private static void fillSwampPools(long worldSeed, int chunkX, int chunkZ, BlockSink sink) {
        final int baseX = chunkX << 4;
        final int baseZ = chunkZ << 4;
        final int[] tiers = GTSRRiverPlacer.tierGrid(worldSeed, baseX, baseZ);
        final Block water = GTSRRiverPlacer.waterMaterial();
        int waterCells = 0;
        final int[] wateredByTier = new int[4];
        // ═══ v1.20.42 P22 A3 快挡：7×7 档格无 roster 3 ⇒ 全 chunk 无沼泽水体（边缘门要求水列
        // 所在粗格本身为 3，档格覆盖 [base−4, base+24) ⊇ 列域 [base, base+16)）——非沼泽 chunk
        // 零网格求值，比改造前"逐列 tierAt+swampTierAt 短路"更便宜。═══
        boolean anySwamp = false;
        for (int i = 0; i < tiers.length && !anySwamp; i++) {
            anySwamp = tiers[i] == 3;
        }
        if (anySwamp) {
            // ═══ v1.20.42 P22 A3：沼泽水顶场（区域缓存）═══ 包含性钳制的低地排干链是池尺度
            // （探针实测最深 43 趟），per-chunk 有界窗口追不上（1/2/4/8 圈环实测漏 59/31/43/72 对，
            // 全窗不动点理想值 0）⇒ 钳制在 256 格<b>区域场</b>上一次算清（+64 环），按 (seed, 区域)
            // 缓存——纯函数 ⇒ 同列恒同值、跨 chunk 接缝一致（每列只从<b>自己区域</b>的场取值，
            // 与哪个 chunk 先生成无关）。场内逐列存钳后水顶/床高/档位，本方法只读场置水。
            // 更长的跨区域排干链（>64 环深）理论可漏——A3 探针 8 seed×512² 实测 0（判据 P17 A3 组）。═══
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    final int x = baseX + lx;
                    final int z = baseZ + lz;
                    final SwampFieldGrid field = swampFieldAt(worldSeed, x, z);
                    final int fi = x - field.originX
                        + SWAMP_FIELD_MARGIN
                        + (z - field.originZ + SWAMP_FIELD_MARGIN) * SWAMP_FIELD_SIDE;
                    final int fillTop = field.top[fi];
                    final int h = field.h[fi];
                    if (fillTop < h + 1) {
                        continue; // 无水体名义项或被钳干（宁缺不悬）
                    }
                    for (int y = h + 1; y <= fillTop; y++) {
                        if (sink.setBlock(x, y, z, water, 0, BlockSink.FLAG_POPULATE)) {
                            waterCells++;
                        }
                    }
                    final int swamp = field.st[fi];
                    wateredByTier[swamp]++;
                }
            }
        }
        SWAMP_WATER_CELLS.addAndGet(waterCells);
        for (int t = 0; t < 4; t++) {
            SWAMP_TIER_WATERED_COLS[t].addAndGet(wateredByTier[t]);
        }
        if (SWAMP_CHUNKS_SERVED.incrementAndGet() % LAKE_LOG_WINDOW_CHUNKS == 0) {
            GTSteamReborn.LOG.info(
                "[GTSR] dim78 swamp pools over {} chunks: waterCells={} wateredCols none={} pool={} deep={}"
                    + " marsh={} (tier-gated backfill via swampTierAt; gate = submergedAt"
                    + " + swampInterior edge gate + region-field containment clamp)",
                SWAMP_CHUNKS_SERVED.get(),
                SWAMP_WATER_CELLS.get(),
                SWAMP_TIER_WATERED_COLS[0].get(),
                SWAMP_TIER_WATERED_COLS[1].get(),
                SWAMP_TIER_WATERED_COLS[2].get(),
                SWAMP_TIER_WATERED_COLS[3].get());
        }
    }

    // ═══════════════ v1.20.42 P22 A3：沼泽水顶场（区域缓存的包含性钳制）═══════════════

    /** 水顶场区域边长（方块，2 的幂，区域原点 = 坐标按本值对齐）。 */
    private static final int SWAMP_FIELD_REGION = 256;
    /** 场环宽（方块）：排干链跨区域可见深度的保守界（探针实测最深链 ~43 格 ⇒ 64 留余量）。 */
    private static final int SWAMP_FIELD_MARGIN = 64;
    /** 场边长 = 区域 + 两侧环。 */
    private static final int SWAMP_FIELD_SIDE = SWAMP_FIELD_REGION + 2 * SWAMP_FIELD_MARGIN;
    /** 每 seed 保留的区域场数（超限整清——纪律同 Profile 各表，重算值不变）。 */
    private static final int SWAMP_FIELD_CACHE_CAP = 4;
    private static final ThreadLocal<HashMap<Long, HashMap<Long, SwampFieldGrid>>> SWAMP_FIELD_CACHE = ThreadLocal
        .withInitial(HashMap::new);

    /**
     * 沼泽水顶场（v1.20.42 P22 A3）：一个 256×256 区域（+64 环）上的<b>钳后水顶场</b>——
     * {@link #fillSwampPools} 的唯一取数口。构建（纯函数，同 seed 同区域恒同值）：
     * <ol>
     * <li>逐列 nominal（三档/微池名义水顶，含 A3 边缘门与 submerged 门）与 fixed（河/主干/
     * 巨湖/残潭水面，其它置水通道不动）；</li>
     * <li>N8 Jacobi 不动点钳制：干列/被钳干列的阻挡面 = 固体顶 h（"8 邻固体顶 ≥ 水顶"验收口径，
     * 0 余量；A1b 残潭 N8 钳制同族），水邻 = 其当前水面；单调下降必收敛（趟上限 96 为保守界）；</li>
     * <li>被钳到床面之下的列 top &lt; h+1 ⇒ 整列不置水（"宁缺不悬"，wg41-E-options D2 先例）。</li>
     * </ol>
     * 缓存纪律：线程私有、(seed, 区域原点) 键、上限 {@link #SWAMP_FIELD_CACHE_CAP} 超限整清
     * （重算值不变，同 {@code ProsperityTerrainProfile} 各表）。首触一个区域做一次
     * {@code 384²} 列求值（数百 ms 量级、摊到该区域 256 个 chunk）；此后每 chunk 只读场。
     */
    private static final class SwampFieldGrid {

        final int originX;
        final int originZ;
        /** 钳后水顶（-1 = 无沼泽水体名义项）；置水另行判 top ≥ h+1。 */
        final int[] top = new int[SWAMP_FIELD_SIDE * SWAMP_FIELD_SIDE];
        /** 床高（heightAt 整链终值，含微池/三档/河谷/巨湖压低）。 */
        final int[] h = new int[SWAMP_FIELD_SIDE * SWAMP_FIELD_SIDE];
        /** {@code TerrainVariants.swampTierAt} 档位（微池列 = NONE 桶）。 */
        final byte[] st = new byte[SWAMP_FIELD_SIDE * SWAMP_FIELD_SIDE];

        SwampFieldGrid(long worldSeed, int regionX, int regionZ) {
            this.originX = regionX;
            this.originZ = regionZ;
            final int w = SWAMP_FIELD_SIDE;
            final int[] nominal = new int[w * w];
            final int[] fixed = new int[w * w];
            for (int lz = 0; lz < w; lz++) {
                for (int lx = 0; lx < w; lx++) {
                    final int i = lz * w + lx;
                    final int x = regionX - SWAMP_FIELD_MARGIN + lx;
                    final int z = regionZ - SWAMP_FIELD_MARGIN + lz;
                    final int tier = ProsperityTerrainProfile.chainRosterIndexAt(worldSeed, x >> 2, z >> 2);
                    final int p = GTSRVoronoiRiverField.poolLevelAt(worldSeed, x, z, tier);
                    final int hv = ProsperityTerrainProfile.heightAt(worldSeed, x, z);
                    this.h[i] = hv;
                    nominal[i] = -1;
                    fixed[i] = -1;
                    this.st[i] = 0;
                    final boolean sub = GTSRVoronoiRiverField.submergedAt(hv, p);
                    if (tier == 3) {
                        final int t = sub ? TerrainVariants.swampTierAt(worldSeed, x, z, 3) : 0;
                        this.st[i] = (byte) t;
                        // A3 边缘门（三档腿经 swampTierAt 的 SWG_TIER 槽同门自动 NONE + 微池腿显式乘
                        // swampInteriorAt——TerrainVariants.swampGates 单点分流，两侧同一真值）
                        if (sub && TerrainVariants.swampInteriorAt(worldSeed, x, z)
                            && (t != TerrainVariants.SWAMP_TIER_NONE
                                || GTSRVoronoiRiverField.swampLakeAt(worldSeed, x, z, 3)
                                    < GTSRVoronoiRiverField.SWAMP_POOL_WATER_LEVEL)) {
                            nominal[i] = p - 1;
                        }
                    }
                    if (sub && GTSRVoronoiRiverField.wetAt(worldSeed, x, z, tier)) {
                        fixed[i] = p - 1; // 河/主干水（A1a 新门）
                    }
                    if (GTSRVoronoiRiverField.lakeAt(worldSeed, x, z) < GTSRVoronoiRiverField.LAKE_SHORE
                        && hv < ProsperityTerrainProfile.SEA_LEVEL) {
                        fixed[i] = Math.max(fixed[i], ProsperityTerrainProfile.SEA_LEVEL - 1); // 巨湖水
                    }
                    if (tier == 3 && GTSRVoronoiRiverField.swampRiverPoolAt(worldSeed, x, z, 3) > 0.0D) {
                        fixed[i] = Math.max(fixed[i], p + GTSRVoronoiRiverField.SWAMP_RIVER_POOL_FILL_TOP); // 残潭（A1b）
                    }
                }
            }
            // N8 不动点钳制（Jacobi 逐趟，趟用上趟快照 ⇒ 确定性与扫描序无关；单调下降必收敛）
            final int[] tops = nominal.clone();
            for (int pass = 0; pass < 96; pass++) {
                boolean changed = false;
                final int[] cur = tops.clone();
                for (int lz = 0; lz < w; lz++) {
                    for (int lx = 0; lx < w; lx++) {
                        final int i = lz * w + lx;
                        if (nominal[i] < 0) {
                            continue;
                        }
                        int t = cur[i];
                        for (int dz = -1; dz <= 1; dz++) {
                            final int nz = lz + dz;
                            if (nz < 0 || nz >= w) {
                                continue;
                            }
                            for (int dx = -1; dx <= 1; dx++) {
                                if (dx == 0 && dz == 0) {
                                    continue;
                                }
                                final int nx = lx + dx;
                                if (nx < 0 || nx >= w) {
                                    continue;
                                }
                                final int j = nz * w + nx;
                                final int b = fixed[j] >= 0
                                    ? Math.max(fixed[j], cur[j] >= this.h[j] + 1 ? cur[j] : this.h[j])
                                    : (cur[j] >= this.h[j] + 1 ? cur[j] : this.h[j]);
                                if (b < t) {
                                    t = b;
                                }
                            }
                        }
                        if (t < cur[i]) {
                            tops[i] = t;
                            changed = true;
                        }
                    }
                }
                if (!changed) {
                    break;
                }
            }
            System.arraycopy(tops, 0, this.top, 0, tops.length);
        }
    }

    /** 列所在区域的水顶场（构建并缓存；区域原点 = 坐标按 {@link #SWAMP_FIELD_REGION} 对齐）。 */
    private static SwampFieldGrid swampFieldAt(long worldSeed, int x, int z) {
        final HashMap<Long, HashMap<Long, SwampFieldGrid>> bySeed = SWAMP_FIELD_CACHE.get();
        HashMap<Long, SwampFieldGrid> regions = bySeed.get(worldSeed);
        if (regions == null) {
            regions = new HashMap<>();
            bySeed.put(worldSeed, regions);
        }
        final int regX = Math.floorDiv(x, SWAMP_FIELD_REGION) * SWAMP_FIELD_REGION;
        final int regZ = Math.floorDiv(z, SWAMP_FIELD_REGION) * SWAMP_FIELD_REGION;
        final Long key = Long.valueOf(packRegion(regX, regZ));
        SwampFieldGrid field = regions.get(key);
        if (field == null) {
            if (regions.size() >= SWAMP_FIELD_CACHE_CAP) {
                regions.clear();
            }
            field = new SwampFieldGrid(worldSeed, regX, regZ);
            regions.put(key, field);
        }
        return field;
    }

    /** (regX, regZ) → long 打包（低 32 位 regZ；与 Profile.packCell 同式负坐标两侧一致）。 */
    private static long packRegion(int regX, int regZ) {
        return ((long) regX << 32) | (regZ & 0xFFFFFFFFL);
    }

    /**
     * <b>遗忘之川群系指派</b>（populate 后置，RTG BiomeAnalyzer.newRepair 先例；plan §3.3
     * 已定机制）：列满足 {@code trunk>0 且 s≥0.7 且 h1≤68}（{@link GTSRVoronoiRiverField#isSanzuColumn}
     * 单点谓词，与空气压缩机的三途余汽判定同一份）⇒ 经 {@link BiomePlaneAccess#writeColumn}
     * （群系平面<b>唯一写通道</b>，short/byte 双通道）写 {@link BiomeSanzuRiver}。细长形状天然来自
     * "河道核 × 主干带"交集，无第二套形状逻辑。sanzu 未配槽（无槽降级）时账本点名不到实例 ⇒
     * 平面一格不写（与 ProsperityAirLookup 的同门判定一致，不伪造）。写后置
     * {@code chunk.isModified = true}：平面列不属于方块写，须显式标脏防丢（GT5U
     * {@code GTWorldgenerator:734} 先例）。
     */
    private void assignSanzuRiverBiome(long worldSeed, int chunkX, int chunkZ) {
        final BiomeGenBase sanzu = GTSRBiomeAuthority.forDimKey(GTSRBiomeAuthority.DIM_KEY_PROSPERITY)
            .biomeOf(BiomeId.SANZU_RIVER);
        if (sanzu == null) {
            return;
        }
        final Chunk chunk = this.worldObj.getChunkFromChunkCoords(chunkX, chunkZ);
        if (chunk == null) {
            return;
        }
        boolean wrote = false;
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                final int x = (chunkX << 4) + lx;
                final int z = (chunkZ << 4) + lz;
                if (GTSRVoronoiRiverField.isSanzuColumn(worldSeed, x, z)) {
                    wrote |= BiomePlaneAccess.writeColumn(chunk, lx, lz, sanzu);
                }
            }
        }
        if (wrote) {
            chunk.isModified = true;
        }
    }
}
