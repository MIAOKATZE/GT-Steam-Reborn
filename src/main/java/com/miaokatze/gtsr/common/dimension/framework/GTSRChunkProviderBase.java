package com.miaokatze.gtsr.common.dimension.framework;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.function.Supplier;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFalling;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Blocks;
import net.minecraft.util.IProgressUpdate;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.terraingen.ChunkProviderEvent;
import net.minecraftforge.event.terraingen.PopulateChunkEvent;

import com.miaokatze.gtsr.common.dimension.framework.structure.GTSRWorldgenHash;
import com.miaokatze.gtsr.main.GTSteamReborn;

import cpw.mods.fml.common.eventhandler.Event.Result;

/**
 * 新维度 IChunkProvider 模板（dim1 S1，蓝本 GT5U ChunkProviderModded.java:37/98-130/409-474）。
 * <p>
 * provideChunk 主流程：确定性 chunk 种子 → {@link #generateTerrain}（子类钩子）→
 * {@link #replaceBlocksForBiome}（ChunkProviderEvent.ReplaceBiomeBlocks 事件 + L3 表层）→
 * Chunk 组装（biome 数组 + 天空光照）。populate：确定性种子 + PopulateChunkEvent Pre/Post +
 * {@link #onPopulate} 钩子。S1 默认 generateTerrain 生成基岩底 + 石层平台，保证空群系表下
 * 维度可运行；S2/S6a 子类覆写为专属噪声地形。
 * <p>
 * <b>P2 起本类是 L3 表层链的唯一实现体</b>（plan §5 P2 / §2.1 L3 / §3.1 更正 1）：
 * Forge 事件段（原两 provider 各 13 行逐字符重复）、扫描/落位内核、降级门全部上收；
 * 两个 provider 只提供一份声明式 {@link SurfaceSpec} + 材质钩子。三处真实语义差异
 * （top meta / 整段换主体 / filler 段后 break）由 spec 字段<b>显式</b>承载——
 * 两 provider 的表层主体<b>不等价</b>，不假装合并。
 * <p>
 * 结构生成不经本类：IWorldGenerator 由 S4/S6 注册（本切片硬边界）。
 */
public class GTSRChunkProviderBase implements IChunkProvider {

    protected final World worldObj;
    /** 确定性种子（构造入参，已含 def.seedSalt），populate 每次重播种用。 */
    private final long seed;
    protected final Random rand;

    public GTSRChunkProviderBase(World world, long seed) {
        this.worldObj = world;
        this.seed = seed;
        this.rand = new Random(seed);
    }

    /**
     * 地形填充钩子（子类覆写）。S1 默认：y=0 基岩 + y=1..64 石层，其余空气。
     * Block[] 下标约定 x<<12 | z<<8 | y（与原版 ChunkProviderGenerate 一致）。
     */
    protected void generateTerrain(int chunkX, int chunkZ, Block[] blocks, byte[] metadata,
        BiomeGenBase[] biomesForGeneration) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                final int column = x << 12 | z << 8;
                blocks[column] = Blocks.bedrock;
                for (int y = 1; y <= 64; y++) {
                    blocks[column | y] = Blocks.stone;
                }
            }
        }
    }

    /** populate 钩子（子类覆写）：装饰/湖/植被等，本切片留空（结构走 IWorldGenerator 通道）。 */
    protected void onPopulate(Random random, int chunkX, int chunkZ) {}

    /**
     * 原始 Block[] 数组"空"判定（<b>框架级约定</b>，v1.20.30 终验修复）：provideChunk 以
     * {@code new Block[65536]} 建列——未写入槽位为 <b>null</b>（{@code Blocks.air} 实例仅存在于
     * 已写入槽位；{@code new Chunk(world, blocks, ...)} 构造时才把 null 视作空气）。
     * 任何在 Chunk 组装前扫描"裸露面/上方空气"的子类必须走本判定，
     * 直接比较 {@code == Blocks.air} 会把空气误判为实体（dim78/dim79 表层替换 no-op 事故根因）。
     */
    protected static boolean isAirOrEmpty(Block block) {
        return block == null || block == Blocks.air;
    }

    /**
     * 列级确定性混合哈希（P2 上收：原两 provider 各一份逐字符相同的 7 行 {@code mix}；
     * P3 再把哈希<b>算法</b>收进 {@link GTSRWorldgenHash#fillerColumnHash}，本方法只剩声明式缝）。
     * 表层 filler 深度用；入参是<b>世界坐标</b>（{@code baseX + x} / {@code baseZ + z}），
     * 故跨 chunk 无缝且与扫描循环的 x/z 语义同侧（转置闭合的另一半）。
     * <p>
     * <b>逐位不变</b>：本方法是 {@code GTSRWorldgenHash.fillerColumnHash} 的同义转发，
     * 保留于此只因它是 L3 表层内核的框架内调用点（plan §2.1 L2「禁止手搓哈希」要求算法体
     * 唯一，不要求删掉调用缝）。
     */
    protected static long mixColumn(long worldSeed, int x, int z) {
        return GTSRWorldgenHash.fillerColumnHash(worldSeed, x, z);
    }

    /**
     * 列基岩顶 y（0..3）：确定性列哈希，跨 chunk 无缝。
     * <p>
     * <b>P3 合并件</b>：改造前 {@code ChunkProviderProsperityRuins.bedrockTop}（:71-75）与
     * {@code ChunkProviderShatteredGrounds.bedrockTop}（:70-74）<b>实现体逐字符相同</b>
     * （仅注释不同），故两份并一份；哈希算法在
     * {@link GTSRWorldgenHash#bedrockTopHash}（其历史口径是「{@code cellSeed} + 单步 xor-shift」，
     * 与 {@link #mixColumn} 的截断终结器不同族）。
     */
    protected static int bedrockTop(long worldSeed, int x, int z) {
        return GTSRWorldgenHash.bedrockTopHash(worldSeed, x, z);
    }

    /**
     * 世界列扫描求地表 y（自上而下第一处非空气方块；找不到返回 -1）——<b>L2/L4/L5 共用的
     * 唯一列扫真值</b>。
     * <p>
     * <b>P3 合并件</b>（plan §5 P3 / 审计 A-5 §1）：改造前 dim78 侧 {@code ProsperitySurfaceScatter}
     * (:137)、{@code RuinedMachinePlacer} (:147)、{@code ProsperityDecorPlacer} (:197)、
     * {@code ProsperityOutpostPlacer} (:412) 与 dim79 侧 {@code ShatteredDecorPlacer} (:151) 各有一份
     * 实现体；逐个 {@code diff}（去 {@code Material} 书写形式后）实测<b>逐字符等价</b>
     * ——同为 {@code for(y=255;y>0;y--)} + {@code block != null && material != Material.air} + 兜底 -1，
     * 故五份并一份，五个调用点改为静态导入本方法。
     * <p>
     * <b>以下四处列扫被审计判为语义各异，本片刻意不并入本方法</b>（并了就会改变生成结果）：
     * <ol>
     * <li>{@code GTSRDimTeleporter.findSurfaceY()}——无坐标参数（恒查 (0,0) 列）、起点
     * {@code getActualHeight()-1} 而非 255、兜底 <b>64</b> 而非 -1；</li>
     * <li>{@code WorldGenShatteredRuins.findSurfaceY}——多一道「上方须为空气」真表面门，
     * 会跳过洞穴顶/悬块下层，比本方法<b>更严</b>；</li>
     * <li>{@code WorldGenRunawaySingularity.findSurfaceY(world,x,z,dim)}——实例方法、带
     * {@code dim} 参、起点按 {@code dim==-1?127:255} 分支（下界语义）；</li>
     * <li>{@link #applyBiomeSurface} 内的 {@code for(y=254;y>0;y--)} 内联扫——扫的是<b>裸数组</b>
     * （不读 World）、起点 254、门是"是否为本维主体方块"而非"是否空气"，且两维在同一内联里
     * 由 {@link SurfaceSpec} 承载三处差异（top meta / 整段换主体 / break）。</li>
     * </ol>
     * 四者各自保留原语义并在自身位置登记差异；合并需先裁决采用哪套口径（属后续片）。
     */
    public static int findSurfaceY(World world, int x, int z) {
        for (int y = 255; y > 0; y--) {
            final Block block = world.getBlock(x, y, z);
            if (block != null && block.getMaterial() != Material.air) {
                return y;
            }
        }
        return -1;
    }

    // ————————————————————————— L3 表层（P2 上收） —————————————————————————

    /**
     * 降级态是否铺表层——plan §5 P2「回退：单布尔开关」。
     * <p>
     * {@code false} = 本片判据 B（降级态表层与填充层一律不写）；置 {@code true} 即回到改造前
     * "降级也照铺"的行为，单点回退。本片<b>不</b>把它做成 Config 键（{@code config/Config.java}
     * 不在 P2 允许改动清单内，且 plan §2.4 判据 4 禁止同一数值多处漂移——需要 Config 化时由
     * 后续片单点接管本常量）。
     */
    protected static final boolean LAY_SURFACE_WHEN_DEGRADED = false;

    /** {@link SurfaceSpec#fillerMeta} 返回本值表示<b>不写</b> metadata（保持槽位现值）。 */
    protected static final int NO_META_WRITE = -1;

    /**
     * L3 表层规格——两维表层差异的<b>显式</b>承载（plan §3.1 更正 1：表层主体不等价，
     * 真正逐字符重复的只有事件段 13 行与 {@code mix} 7 行，故合并必须参数化而非假装同源）。
     * <p>
     * 三处被点名差异与本类字段的对应：
     * <ol>
     * <li><b>top meta</b>：{@link #topWritesBiomeMeta}——dim78 写 {@code biome.field_150604_aj}
     * （独立方块族的 top meta 由群系声明），dim79 <b>不写</b>（表层 top 恒 meta0，写与不写
     * 在现状字节相同，但写入模式必须原样保留，故不合并）；伴随字段 {@link #fillerMeta}
     * 表达 filler 段 meta 来源（dim78 查表 / dim79 不写＝{@link #NO_META_WRITE}）；</li>
     * <li><b>整段换主体</b>：{@link #wholeBody}——dim78 把 filler 段以下<b>全部</b>主体换成群系
     * base（plan §12 修订 4），dim79 传 {@code null}＝主体保持 corestone 原样；</li>
     * <li><b>break</b>：{@link #stopAfterFiller}——dim79 filler 段写完即停扫（伴随
     * {@link #skipDanglingFiller} 的悬空防御），dim78 必须一路扫到 y=1 才能整段换主体。</li>
     * </ol>
     * 另有两项<b>数据</b>差异（非语义分支）：{@link #dimKey}（L1 账本键）与
     * {@link #bodyBlock}（扫描触发方块：dim78 {@code Blocks.stone} / dim79
     * {@code BlocksGTSR.shatteredCorestone}）。
     */
    public static final class SurfaceSpec {

        final String dimKey;
        /** 主体方块<b>惰性</b>读取：BlocksGTSR 的静态字段在 BlockLoader 之后才非 null。 */
        final Supplier<Block> bodyBlock;
        final boolean topWritesBiomeMeta;
        final ToIntFunction<BiomeGenBase> fillerMeta;
        /** {@code null} = 不换主体（差异②）。 */
        final Function<BiomeGenBase, Block> wholeBody;
        final boolean stopAfterFiller;
        /** 差异③的伴随：filler 段是否跳过"上方为空气"的悬空主体格（dim79 防御口径）。 */
        final boolean skipDanglingFiller;

        public SurfaceSpec(String dimKey, Supplier<Block> bodyBlock, boolean topWritesBiomeMeta,
            ToIntFunction<BiomeGenBase> fillerMeta, Function<BiomeGenBase, Block> wholeBody, boolean stopAfterFiller,
            boolean skipDanglingFiller) {
            this.dimKey = dimKey;
            this.bodyBlock = bodyBlock;
            this.topWritesBiomeMeta = topWritesBiomeMeta;
            this.fillerMeta = fillerMeta;
            this.wholeBody = wholeBody;
            this.stopAfterFiller = stopAfterFiller;
            this.skipDanglingFiller = skipDanglingFiller;
        }
    }

    /**
     * 本 provider 的 L3 表层规格；返回 {@code null} = 沿用 S1 原版 {@code genTerrainBlocks} 管线
     * （dim78/dim79 的 provider 均已覆写本方法给出声明式规格）。
     */
    protected SurfaceSpec surfaceSpec() {
        return null;
    }

    /**
     * 群系表面应用（P2 起 = 事件段 + 声明式表层）：先 POST
     * {@code ChunkProviderEvent.ReplaceBiomeBlocks}（第三方 terraingen 钩子契约，兼容 GT5U
     * replaceBlocksForBiome），DENY 则整段不动；再按 {@link #surfaceSpec()} 落表层，
     * 无规格（S1 模板）时回退原版 {@code biome.genTerrainBlocks}。
     */
    protected void replaceBlocksForBiome(int chunkX, int chunkZ, Block[] blocks, byte[] metadata,
        BiomeGenBase[] biomes) {
        if (!postReplaceBiomeBlocksEvent(chunkX, chunkZ, blocks, metadata, biomes)) {
            return;
        }
        final SurfaceSpec spec = surfaceSpec();
        if (spec == null) {
            applyVanillaBiomeTerrain(chunkX, chunkZ, blocks, metadata, biomes);
            return;
        }
        applyBiomeSurface(this.worldObj.getSeed(), chunkX * 16, chunkZ * 16, blocks, metadata, biomes, spec);
    }

    /**
     * Forge 事件段（P2 上收：原两 provider 各 13 行逐字符重复）。
     *
     * @return {@code false} = 事件被 DENY，调用方必须一个方块都不动
     */
    protected boolean postReplaceBiomeBlocksEvent(int chunkX, int chunkZ, Block[] blocks, byte[] metadata,
        BiomeGenBase[] biomes) {
        ChunkProviderEvent.ReplaceBiomeBlocks event = new ChunkProviderEvent.ReplaceBiomeBlocks(
            this,
            chunkX,
            chunkZ,
            blocks,
            metadata,
            biomes,
            null);
        MinecraftForge.EVENT_BUS.post(event);
        return event.getResult() != Result.DENY;
    }

    /** S1 模板路径（无 {@link SurfaceSpec} 时）：逐列调原版 {@code genTerrainBlocks}。 */
    private void applyVanillaBiomeTerrain(int chunkX, int chunkZ, Block[] blocks, byte[] metadata,
        BiomeGenBase[] biomes) {
        // 模板路径无维度名册（dimKey=null，不查 L1 账本），但空表降级门同样生效（判据 B）
        if (!surfaceMayBeLaid(null, biomes)) {
            return;
        }
        for (int x = 0; x < 16; ++x) {
            for (int z = 0; z < 16; ++z) {
                final BiomeGenBase biome = biomeAtColumn(biomes, x, z);
                if (biome == null) {
                    continue;
                }
                biome.genTerrainBlocks(
                    this.worldObj,
                    this.rand,
                    blocks,
                    metadata,
                    chunkX * 16 + x,
                    chunkZ * 16 + z,
                    0.0D);
            }
        }
    }

    /**
     * L3 表层内核（两维共用唯一实现体；正常态与改造前逐字节一致由
     * {@code tools/dim1/SurfaceByteParityDump} 对拍保证）。
     * <p>
     * <b>下标口径（P2 判据 C 转置闭合）</b>：读 {@code biomes[x + z * 16]}——与生产者
     * {@code GTSRWorldChunkManager.getBiomesForGeneration} 的 {@code dx + dz * width}
     * （亦即 vanilla {@code loadBlockGeneratorData} 的 {@code x + z*width}）同侧。
     * 改造前两 provider 读 {@code biomes[z + x * 16]}，与生产者<b>互为转置</b>；现状整 chunk
     * 单一群系故无可见差异，但表层一旦按列生效即成镜像（plan §3 新发现第 5 行）。
     * <p>
     * <b>降级口径（P2 判据 B）</b>：整数组无一群系（空表级）时一格都不铺；逐列缺席
     * （null 或 L1 点名不到）时该列一格都不铺——主体保持 {@code generateTerrain} 的原样，
     * 因此不可能出现 {@code plains}/{@code Blocks.grass}/{@code Blocks.dirt}。
     * <p>
     * <b>P3 登记（审计 A-5 §1 的 #9/#10 两处内联列扫）</b>：本方法内的
     * {@code for(y=254;y>0;y--)} 是<b>全仓唯一</b>的数组内向列扫（改造前两 provider 各一份，
     * P2 已随表层上收合一），它与 {@link #findSurfaceY(World,int,int)} <b>不可互相合并</b>：
     * 本内联扫裸数组、起点 254、以"是否为本维主体方块"为门，且必须在同一循环里承载
     * top/filler/主体三段状态机；{@code findSurfaceY} 读 {@code World}、起点 255、
     * 以"是否空气"为门且无状态。两者输入域与谓词都不同，合并即改变生成结果。
     *
     * @param baseX / baseZ chunk 原点<b>世界坐标</b>（filler 深度哈希按世界坐标，跨 chunk 无缝）
     */
    public static void applyBiomeSurface(long worldSeed, int baseX, int baseZ, Block[] blocks, byte[] metadata,
        BiomeGenBase[] biomes, SurfaceSpec spec) {
        if (!surfaceMayBeLaid(spec.dimKey, biomes)) {
            return;
        }
        final Block body = spec.bodyBlock.get();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                final BiomeGenBase biome = biomeAtColumn(biomes, x, z);
                if (biome == null || !columnSurfaceable(spec.dimKey, biome)) {
                    continue; // 降级/缺席列：表层与填充层一律不写（判据 B）
                }
                final int column = x << 12 | z << 8;
                final int depth = 1 + (int) (mixColumn(worldSeed, baseX + x, baseZ + z) & 1); // filler 1-2 格
                boolean topPlaced = false;
                int fillerLeft = spec.stopAfterFiller ? depth : 0;
                for (int y = 254; y > 0; y--) {
                    final int idx = column | y;
                    if (blocks[idx] != body) {
                        continue;
                    }
                    if (!topPlaced) {
                        if (!isAirOrEmpty(blocks[idx + 1])) {
                            continue; // 尚未到达裸露面（上方仍被主体覆盖）
                        }
                        blocks[idx] = biome.topBlock;
                        if (spec.topWritesBiomeMeta) {
                            metadata[idx] = (byte) biome.field_150604_aj; // 差异①
                        }
                        topPlaced = true;
                        if (!spec.stopAfterFiller) {
                            fillerLeft = depth;
                        }
                        continue;
                    }
                    if (spec.skipDanglingFiller && isAirOrEmpty(blocks[idx + 1])) {
                        continue; // 悬空主体格（不应出现，防御跳过）
                    }
                    if (fillerLeft > 0) {
                        blocks[idx] = biome.fillerBlock;
                        final int fillerMeta = spec.fillerMeta.applyAsInt(biome);
                        if (fillerMeta != NO_META_WRITE) {
                            metadata[idx] = (byte) fillerMeta;
                        }
                        fillerLeft--;
                        if (spec.stopAfterFiller && fillerLeft <= 0) {
                            break; // 差异③
                        }
                    } else if (spec.wholeBody != null) {
                        blocks[idx] = spec.wholeBody.apply(biome); // 差异②
                    }
                }
            }
        }
    }

    /**
     * 列 → 群系（<b>唯一</b>下标出口，判据 C）。数组由
     * {@code GTSRWorldChunkManager.loadBlockGeneratorData} 按 {@code dx + dz * width} 产出。
     */
    protected static BiomeGenBase biomeAtColumn(BiomeGenBase[] biomes, int x, int z) {
        if (biomes == null) {
            return null;
        }
        final int i = x + z * 16;
        return i < biomes.length ? biomes[i] : null;
    }

    /** chunk 级降级门：整数组无任何群系身份时一格都不铺（并留下一次性日志锚点）。 */
    private static boolean surfaceMayBeLaid(String dimKey, BiomeGenBase[] biomes) {
        if (LAY_SURFACE_WHEN_DEGRADED) {
            return true;
        }
        if (biomes == null) {
            logSurfaceNotLaidOnce(dimKey, "NULL_TABLE");
            return false;
        }
        for (int i = 0; i < biomes.length; i++) {
            if (biomes[i] != null) {
                return true;
            }
        }
        logSurfaceNotLaidOnce(dimKey, degradedLabel(dimKey));
        return false;
    }

    /**
     * 列级降级门（{@code dimKey == null} = 匿名/模板路径，不查账本）。
     * <p>
     * 正常态（{@code degraded == NONE}）<b>零介入</b>——这是"改造前后逐字节一致"的前提；
     * SHORT 时 L1 点名得到的群系照常铺表层，点名不到（该群系无槽位＝缺席）的列不铺。
     * 账本未绑定（离线合成缝 / 维度未注册）时不介入，与既有工具的驱动口径一致。
     */
    private static boolean columnSurfaceable(String dimKey, BiomeGenBase biome) {
        if (LAY_SURFACE_WHEN_DEGRADED || dimKey == null) {
            return true;
        }
        final GTSRBiomeAuthority authority = GTSRBiomeAuthority.forDimKey(dimKey);
        if (!authority.isBound()) {
            return true;
        }
        if (authority.degraded() == GTSRBiomeAuthority.Degraded.NONE) {
            return true;
        }
        if (authority.of(biome)
            .resolved()) {
            return true;
        }
        logSurfaceNotLaidOnce(dimKey, degradedLabel(dimKey));
        return false;
    }

    private static String degradedLabel(String dimKey) {
        return dimKey == null ? "UNKNOWN"
            : GTSRBiomeAuthority.forDimKey(dimKey)
                .degraded()
                .name();
    }

    /** 已打过降级日志的 {@code dimKey/级别} 组合（一次性，L8）。 */
    private static final Set<String> SURFACE_SKIP_LOGGED = ConcurrentHashMap.newKeySet();

    /**
     * 降级态"表层未铺"的一次性日志锚点（plan §2.3 判据 2「降级可见」+ §2.1 L8「禁止无日志的降级」）。
     * 同一 {@code dimKey/级别} 组合只打一行（chunk 级门与列级缺席共用）。
     *
     * @return {@code true} = 本次是该内容的首次打印（供离线断言验证"一次性"）
     */
    public static boolean logSurfaceNotLaidOnce(String dimKey, String degradedLevel) {
        if (!SURFACE_SKIP_LOGGED.add(String.valueOf(dimKey) + '/' + degradedLevel)) {
            return false;
        }
        GTSteamReborn.LOG
            .warn("[GTSR] dim={} surface NOT laid: 本维度处于 {} 级降级，表层与填充层一律未写"
                + "（保持 generateTerrain 主体原样，不回退 plains/grass/dirt；plan §5 P2 判据 B）",
                String.valueOf(dimKey),
                degradedLevel);
        return true;
    }

    /** 降级/缺席列写进 Chunk byte 平面的占位 id（详见 {@link #writeBiomePlane}）。 */
    public static final byte MISSING_BIOME_PLANE_ID = (byte) 0;

    /**
     * Chunk byte 群系平面的<b>唯一</b>写点（P2 判据 B 的崩溃守卫；provideChunk 与离线断言共用）。
     * <p>
     * 改造前这里是 {@code chunkBiomes[i] = (byte) biomes[i].biomeID}——空表降级（P1 起
     * {@code GTSRWorldChunkManager} 不再回退 plains，槽位为 null）下直接 <b>NPE</b>（P1 交给
     * 本片的硬事实 1）。缺席列写 {@link #MISSING_BIOME_PLANE_ID}：
     * <ul>
     * <li>不能写 255——vanilla 把 255 当懒回填哨兵，会回调 chunk manager 并把结果<b>写回落盘</b>
     * （P1 实测，且回退链可能落到 plains）；</li>
     * <li>不能写 1——那是 plains，违反"绝不出现 plains"；</li>
     * <li>0 = vanilla ocean，非空、非哨兵、非 plains，且降级维度的地表本来就保持主体石。</li>
     * </ul>
     */
    protected static void writeBiomePlane(byte[] target, BiomeGenBase[] biomes) {
        if (target == null) {
            return;
        }
        for (int i = 0; i < target.length; i++) {
            final BiomeGenBase biome = biomes == null || i >= biomes.length ? null : biomes[i];
            target[i] = biome == null ? MISSING_BIOME_PLANE_ID : (byte) biome.biomeID;
        }
    }

    @Override
    public Chunk provideChunk(int chunkX, int chunkZ) {
        // P7 登记（plan §2.1 L2「禁止手搓哈希，须走 GTSRWorldgenHash」的<b>已知例外</b>，只登记不改值）：
        // 下一行是本仓第 10 处手搓 (cx,cz)→long 混合，与 GTSRWorldgenHash.chunkSeed 不同族。
        // <b>本片及 P5/P6 一律不动它的数值</b>：它喂给 vanilla 的 rand 流，改它等于移动两维全部
        // 结构落点（含本文件之下 generateTerrain/replaceBlocksForBiome 的消费者），会把 P3/P5/P6
        // 的逐字节对拍基线与 review/dim1 的 _determinism_sample.txt 钉全部作废。
        // 收口时机：P13（旧生成路径删除）统一换 GTSRWorldgenHash 并重钉全部样本，见
        // plan/investigation/p7b-placement-contract-20260919.md §0（同一条登记的实测出口在 §6）。
        this.rand.setSeed(chunkX * 341873128712L + chunkZ * 132897987541L);
        Block[] blocks = new Block[65536];
        byte[] metadata = new byte[65536];
        generateTerrain(chunkX, chunkZ, blocks, metadata, null);
        BiomeGenBase[] biomes = this.worldObj.getWorldChunkManager()
            .loadBlockGeneratorData(null, chunkX * 16, chunkZ * 16, 16, 16);
        replaceBlocksForBiome(chunkX, chunkZ, blocks, metadata, biomes);

        Chunk chunk = new Chunk(this.worldObj, blocks, metadata, chunkX, chunkZ);
        writeBiomePlane(chunk.getBiomeArray(), biomes);
        chunk.generateSkylightMap();
        return chunk;
    }

    @Override
    public void populate(IChunkProvider chunkProvider, int chunkX, int chunkZ) {
        BlockFalling.fallInstantly = false;
        // 确定性 populate 种子：世界种子派生长奇数混合 chunk 坐标（GT5U/原版范式，掺入维度 salt）
        this.rand.setSeed(this.seed);
        long a = this.rand.nextLong() / 2L * 2L + 1L;
        long b = this.rand.nextLong() / 2L * 2L + 1L;
        this.rand.setSeed(chunkX * a + chunkZ * b ^ this.seed);
        boolean hasVillage = false;

        MinecraftForge.EVENT_BUS
            .post(new PopulateChunkEvent.Pre(chunkProvider, this.worldObj, this.rand, chunkX, chunkZ, hasVillage));

        onPopulate(this.rand, chunkX, chunkZ);

        MinecraftForge.EVENT_BUS
            .post(new PopulateChunkEvent.Post(chunkProvider, this.worldObj, this.rand, chunkX, chunkZ, hasVillage));

        BlockFalling.fallInstantly = false;
    }

    @Override
    public boolean chunkExists(int chunkX, int chunkZ) {
        return true;
    }

    @Override
    public Chunk loadChunk(int chunkX, int chunkZ) {
        return provideChunk(chunkX, chunkZ);
    }

    @Override
    public boolean saveChunks(boolean all, IProgressUpdate progress) {
        return true;
    }

    @Override
    public void saveExtraData() {}

    @Override
    public boolean unloadQueuedChunks() {
        return false;
    }

    @Override
    public boolean canSave() {
        return true;
    }

    @Override
    public String makeString() {
        return "GTSRBaseLevelSource";
    }

    /**
     * 刷怪身份读取点——<b>P9（L7 生物层）责任</b>：本方法经
     * {@code World.getBiomeGenForCoords} 走 Chunk byte 平面（plan §2.1 L1 禁止项、L7
     * "禁止读 byte id 决定刷怪"）。P2 只登记不动（任务包约定），改造后 byte 平面在降级列
     * 写 {@link #MISSING_BIOME_PLANE_ID} 而非 plains，但"按带刷怪"须由 P9 改为
     * {@code GTSRBiomeAuthority.ordinalAt} 口径。
     */
    @Override
    public List<BiomeGenBase.SpawnListEntry> getPossibleCreatures(EnumCreatureType creatureType, int x, int y, int z) {
        BiomeGenBase biome = this.worldObj.getBiomeGenForCoords(x, z);
        return biome != null ? biome.getSpawnableList(creatureType) : null;
    }

    @Override
    public ChunkPosition func_147416_a(World world, String structureName, int x, int y, int z) {
        return null;
    }

    @Override
    public int getLoadedChunkCount() {
        return 0;
    }

    @Override
    public void recreateStructures(int chunkX, int chunkZ) {}
}
