package com.miaokatze.gtsr.common.dimension.prosperity.river;

import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.framework.genlayer.GTSRGenLayerRosterFace;
import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;
import com.miaokatze.gtsr.common.dimension.framework.structure.GTSRWorldgenHash;
import com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile;
import com.miaokatze.gtsr.main.GTSteamReborn;

/**
 * dim78 河流水系的<b>落块器</b>（dim1 P17 S-C）：在 {@code onPopulate} 窗口里按
 * {@link GTSRRiverNetwork} 那套纯函数模型把河道切成水。全部算式与几何证明在
 * {@link GTSRRiverNetwork}（本类只负责"按模型读写世界"），需求原话「增加河流」+
 * 「沼泽……河网密集」的兑现点即本文件与那张档表。
 *
 * <p>
 * ═══ 写入协议（三条都是既存纪律，本片零新发明） ═══
 * <ul>
 * <li><b>只写本 chunk</b>：跨 chunk 方块读与写一并禁止——写入经 {@link BlockSink}
 * （生产传 {@code ChunkClampedSink}，协议层钳制），几何读取一律走
 * {@link ProsperityTerrainProfile#heightAtWithReliefTier} 这条<b>解析纯函数</b>，
 * 连邻列的地表都不碰 {@code World} ⇒ 框架的"provider 不做跨 chunk 方块读取"红线不动；</li>
 * <li><b>{@link BlockSink#FLAG_POPULATE}（=2）</b>：只送客户端、不触发邻块更新——与原版
 * {@code WorldGenLakes:108} 事后灌水的 flag 一字同形。这一条是"我们自己不把自己的水源叫醒"
 * 的实现手段（后果与不可避的那一半见 {@link GTSRRiverNetwork} 类注释）；</li>
 * <li><b>光照与流体不需要任何额外置位</b>（P17-B 留的 {@code [未实测]} 项，本片实测闭合，
 * 证据见 {@code plan/tmp/p17-sc2/SC2-RESULT.md} §4）：{@code World.setBlock} 在 flags 之外
 * <b>无条件</b>调 {@code func_147451_t(x,y,z)}（{@code World.java:527-529}，Sky+Block 两种光都走），
 * 且 {@code Chunk.func_150807_a} 自己按新旧不透明度做 {@code relightBlock}/
 * {@code generateSkylightMap}/{@code propagateSkylightOcclusion}（{@code Chunk.java:675-702}）。
 * 流体侧：{@code Blocks.water} 是静态源（{@code BlockStaticLiquid.java:15} 对水
 * {@code setTickRandomly(false)}），{@code Chunk} 组装期那条"裸数组 + generateSkylightMap"的路
 * 本片<b>不走</b>（水在 Chunk 之后才写），因此 provider 阶段无需、也不应再排一次 tick——
 * 排了反而会让 {@code BlockDynamicLiquid} 去够不着的邻格。</li>
 * </ul>
 *
 * <p>
 * ═══ 断面（每列，{@code H =} 该列天然地表） ═══
 * 过水列：{@code y=H} 清成空气、{@code y=H-1} 灌 {@code Blocks.water}（meta 0）、
 * {@code y=H-2} 铺 {@code gtsr:prosperityRiverGravel}（水床成层关系由判据 LAYER 组钉）。
 * 干谷列（档=不过水，或几何闸未过）：{@code y=H} 空气、{@code y=H-1} 铺同一份砂砾作谷底。
 * 水格四同层邻格与床格必为固体——这就是 {@link GTSRRiverNetwork#holdsWater} 的结论。
 *
 * <p>
 * ═══ 成本口径（实测见交付件 §5） ═══
 * 每 chunk：身份 6×6 格（coarse 面 1:4，含一列外的邻格环）+ 高度 18×18 格 + 骨架掩码 18×18 格，
 * 全部是<b>纯函数采样</b>；方块写入只发生在河道列（实测每 chunk 约 8-40 格）。
 * 本类不消费 {@code onPopulate} 传进来的 {@code Random}（全部掷骰走
 * {@link GTSRWorldgenHash} 的坐标哈希）⇒ <b>既有 rand 流一个数都不动</b>。
 */
public final class GTSRRiverPlacer {

    /** 河道统计日志窗口（沿用 {@code ChunkClampedSink} 的每 256 chunk 一行口径）。 */
    private static final int LOG_WINDOW_CHUNKS = 256;

    private static final AtomicLong CHUNKS_SERVED = new AtomicLong();
    private static final AtomicLong CHANNEL_COLUMNS = new AtomicLong();
    private static final AtomicLong WATER_COLUMNS = new AtomicLong();
    private static final AtomicLong WRITES = new AtomicLong();

    /** 河床料缺失锚点是否已打过（一次性；正常生产路径不可达，见 {@link #bedMaterial()}）。 */
    private static boolean bedMissingLogged;

    private GTSRRiverPlacer() {}

    /**
     * 本 chunk 的河流水系。由 {@code ChunkProviderProsperityRuins.onPopulate} 调用（唯一生产入口）。
     *
     * @param worldSeed {@code world.getSeed()}——必须与 {@code heightAt} 的其它调用点同一个值
     *                  （不含 def.seedSalt；身份面的盐在 {@link #tierGrid} 内按 S-A 同式掺入）
     */
    public static void place(World world, long worldSeed, int chunkX, int chunkZ, BlockSink sink) {
        final int baseX = chunkX << 4;
        final int baseZ = chunkZ << 4;
        // —— 1. 身份档：coarse 面 1:4 ⇒ 本 chunk 的 4×4 格 + 一列邻格所需的边圈 = 6×6 次解析
        final int[] tiers = tierGrid(worldSeed, baseX, baseZ);
        // —— 2. 天然地表（解析纯函数，与真实表层逐列同值；由 P17RiverNetworkCheck 的 C8 逐列对拍钉死）
        final int[] height = new int[18 * 18];
        for (int lz = 0; lz < 18; lz++) {
            for (int lx = 0; lx < 18; lx++) {
                final int x = baseX - 1 + lx;
                final int z = baseZ - 1 + lz;
                height[lz * 18 + lx] = ProsperityTerrainProfile
                    .heightAtWithReliefTier(worldSeed, x, z, tierAt(tiers, x, z, baseX, baseZ));
            }
        }
        // —— 3. 骨架掩码（成道位 + 过水位）
        final int[] mask = new int[18 * 18];
        for (int lz = 0; lz < 18; lz++) {
            for (int lx = 0; lx < 18; lx++) {
                final int x = baseX - 1 + lx;
                final int z = baseZ - 1 + lz;
                final int tier = tierAt(tiers, x, z, baseX, baseZ);
                mask[lz * 18 + lx] = GTSRRiverNetwork.riverMask(
                    worldSeed,
                    x,
                    z,
                    GTSRRiverNetwork.bandForRosterIndex(tier),
                    GTSRRiverNetwork.wetForRosterIndex(tier));
            }
        }
        final Block bed = bedMaterial();
        final int[] nbY = new int[4];
        final int[] nbMask = new int[4];
        int channelColumns = 0;
        int waterColumns = 0;
        int writes = 0;
        // —— 4. 逐列落块：只走本 chunk 的 16×16（邻格环只参与判定，一律不写）
        for (int lz = 1; lz < 17; lz++) {
            for (int lx = 1; lx < 17; lx++) {
                final int i = lz * 18 + lx;
                if (!GTSRRiverNetwork.isChannel(mask[i])) {
                    continue;
                }
                channelColumns++;
                final int surface = height[i];
                nbY[0] = height[i - 1];
                nbY[1] = height[i + 1];
                nbY[2] = height[i - 18];
                nbY[3] = height[i + 18];
                nbMask[0] = mask[i - 1];
                nbMask[1] = mask[i + 1];
                nbMask[2] = mask[i - 18];
                nbMask[3] = mask[i + 18];
                // 几何闸：水格四同层邻格与床格必须都是固体（证明见 GTSRRiverNetwork 类注释）
                final boolean wet = GTSRRiverNetwork.wantsWater(mask[i])
                    && GTSRRiverNetwork.holdsWater(surface, nbY, nbMask);
                final int x = baseX + lx - 1;
                final int z = baseZ + lz - 1;
                // 不穿基岩带（heightAt 下界 40、bedrockTop 上界 3 ⇒ 生产恒不触发，纯防御）
                if (GTSRRiverNetwork.bedY(surface) <= GTSRWorldgenHash.bedrockTopHash(worldSeed, x, z)) {
                    continue;
                }
                final int waterY = GTSRRiverNetwork.waterY(surface);
                // 先固体、后水（同一 flag，顺序只为"写序即断面序"可读）
                writes += accept(sink, x, GTSRRiverNetwork.airY(surface), z, Blocks.air, 0);
                writes += accept(
                    sink,
                    x,
                    wet ? GTSRRiverNetwork.bedY(surface) : GTSRRiverNetwork.dryBedY(surface),
                    z,
                    bed,
                    0);
                if (wet) {
                    waterColumns++;
                    writes += accept(sink, x, waterY, z, Blocks.water, 0);
                }
            }
        }
        // —— 5. 观测：每 256 chunk 一行（无河道也是读数，不静默）
        CHANNEL_COLUMNS.addAndGet(channelColumns);
        WATER_COLUMNS.addAndGet(waterColumns);
        WRITES.addAndGet(writes);
        final long served = CHUNKS_SERVED.incrementAndGet();
        if (served % LOG_WINDOW_CHUNKS == 0) {
            GTSteamReborn.LOG.info(
                "[GTSR] dim78 river over {} chunks: channelCols={} waterCols={} ({}pp of all cols)"
                    + " writes={} band={}/{}/{}/{} wet={}/{}/{}/{}",
                served,
                CHANNEL_COLUMNS.get(),
                WATER_COLUMNS.get(),
                formatPp(CHANNEL_COLUMNS.get(), served * 256L),
                WRITES.get(),
                GTSRRiverNetwork.RIVER_BAND_BY_ROSTER[0],
                GTSRRiverNetwork.RIVER_BAND_BY_ROSTER[1],
                GTSRRiverNetwork.RIVER_BAND_BY_ROSTER[2],
                GTSRRiverNetwork.RIVER_BAND_BY_ROSTER[3],
                GTSRRiverNetwork.RIVER_WET_BY_ROSTER[0],
                GTSRRiverNetwork.RIVER_WET_BY_ROSTER[1],
                GTSRRiverNetwork.RIVER_WET_BY_ROSTER[2],
                GTSRRiverNetwork.RIVER_WET_BY_ROSTER[3]);
        }
    }

    /** 一次写入；返回 1/0 只为统计，不参与任何生成判定（丢弃语义见 {@code BlockSink} 契约）。 */
    private static int accept(BlockSink sink, int x, int y, int z, Block block, int meta) {
        return sink.setBlock(x, y, z, block, meta, BlockSink.FLAG_POPULATE) ? 1 : 0;
    }

    /**
     * 河床料（S-B1/S-B2 在册的 {@code gtsr:prosperity_river_gravel}；本片不新增方块）。
     * <b>{@code BlockLoader} 未跑时该静态字段为 null</b>——生产路径由 preInit 顺序保证非 null，
     * 真为 null 时本方法回退 {@code Blocks.stone} 并打<b>一次性</b> WARN（plan §2.1 L8「禁止无日志的
     * 降级」；断面仍然闭合，只是河床料退化成骨架石）。
     */
    private static Block bedMaterial() {
        final Block bed = BlocksGTSR.prosperityRiverGravel;
        if (bed != null) {
            return bed;
        }
        if (!bedMissingLogged) {
            bedMissingLogged = true;
            GTSteamReborn.LOG.warn(
                "[GTSR] dim78 river bed material MISSING (BlocksGTSR.prosperityRiverGravel == null"
                    + " before BlockLoader) -> 河床退化为 stone，水断面不变（一次性告警）");
        }
        return Blocks.stone;
    }

    /** 千分位读数格式化（仅日志用）。 */
    private static String formatPp(long part, long total) {
        return total <= 0L ? "-" : String.format("%.3f", 100.0D * part / total);
    }

    // ————————————————————————— 身份格网 —————————————————————————

    /** coarse 面（1:4）的格网起点：覆盖 [baseX-4, baseX+20) ⇒ 含一列邻格环所需的边圈。 */
    private static int cellOrigin(int base) {
        return (base - 4) >> 2;
    }

    /**
     * 本 chunk 及其一列邻格环的 L1 名册下标（coarse 身份面，与 {@code heightAt} 内部的取数口
     * <b>同一个</b> {@link GTSRGenLayerRosterFace}，盐也沿用 {@link ProsperityTerrainProfile#CHAIN_SEED_SALT}
     * 的<b>常数引用</b>——不在本片再抄一份字面量，S-A 的 SALT 组钉的是"三处字面量"，第四处引用不新增真值）。
     * 缺失身份 = {@link GTSRGenLayerRosterFace#NO_IDENTITY}，由两张档表各自回退默认档（本片无任何
     * 身份等值判断）。
     */
    private static int[] tierGrid(long worldSeed, int baseX, int baseZ) {
        final long chainSeed = worldSeed ^ ProsperityTerrainProfile.CHAIN_SEED_SALT;
        final int cx0 = cellOrigin(baseX);
        final int cz0 = cellOrigin(baseZ);
        final int[] tiers = new int[7 * 7];
        for (int cz = 0; cz < 7; cz++) {
            for (int cx = 0; cx < 7; cx++) {
                tiers[cz * 7 + cx] = GTSRGenLayerRosterFace
                    .rosterIndexAt(chainSeed, GTSRBiomeAuthority.DIM_KEY_PROSPERITY, (cx0 + cx) << 2, (cz0 + cz) << 2);
            }
        }
        return tiers;
    }

    /** 方块坐标 → 档（列坐标可落在邻格环，故格网比 6×6 多留一圈冗余）。 */
    private static int tierAt(int[] tiers, int x, int z, int baseX, int baseZ) {
        final int cx = ((x >> 2) - cellOrigin(baseX));
        final int cz = ((z >> 2) - cellOrigin(baseZ));
        if (cx < 0 || cz < 0 || cx >= 7 || cz >= 7) {
            // 理论不可达（列域 [base-4, base+20)）；真到了这里按身份不可得处理，不伪造档
            return GTSRGenLayerRosterFace.NO_IDENTITY;
        }
        return tiers[cz * 7 + cx];
    }
}
