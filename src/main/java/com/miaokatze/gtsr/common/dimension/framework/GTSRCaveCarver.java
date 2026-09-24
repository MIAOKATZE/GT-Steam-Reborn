package com.miaokatze.gtsr.common.dimension.framework;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.BiomeGenBase;

import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.framework.cave.ProsperityCaveField;
import com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile;

/**
 * <b>dim78 洞穴数组写入器（P22 版 B · S1b，p21 §1.1/§1.3/§1.4 + 裁决 1 保护门）</b>。
 *
 * <p>
 * ═══ 输入契约 ═══ 只吃裸 {@code Block[65536]} + {@code byte[65536]} + {@code BiomeGenBase[256]}
 * （provideChunk 期 {@code replaceBlocksForBiome} 之后的现产数组），<b>零 World 读</b>：身份取数
 * 唯一出口 = {@link GTSRChunkProviderBase#biomeAtColumn}（P0 现号 :482-488，唯一下标出口
 * {@code x + z * 16}），top/filler 取自本列群系声明，主体取 {@link BlocksGTSR#prosperityStone}。
 * 全链路禁 {@code World.getBiomeGenForCoords} / {@code worldObj.}——EndlessIDs 在场时
 * {@code Chunk.getBiomeArray()/setBiomeArray} 被 mixin 取消，那是崩溃入口（p21 §1.2 /
 * {@code GTSRChunkProviderBase} 挂点区注释）；机检 = {@code CaveFieldCheck} SOURCE 组
 * （去注释后三项负形状 0 命中 + 成对正形状 {@code biomeAtColumn(} ≥1）。
 *
 * <p>
 * ═══ 下标口径（p21 §1.4 判据钉）═══ 本类内数组下标只允许「column | y」一种形态
 * （{@code x << 12 | z << 8 | y}，与 {@code ChunkProviderProsperityRuins.generateTerrain} 同形，
 * 即 vanilla 的 {@code (x*16+z)*256+y}）；出现 {@code <<4} 即红（防混入
 * {@code Chunk.getBlockIndex} 的 {@code y<<8|z<<4|x} 异形状）。坐标算术一律乘法/除法，不用移位。
 *
 * <p>
 * ═══ 可挖白名单（p21 §1.3，vanilla MapGenCaves:284 的本维补集）═══
 * {@code stone ∨ prosperityStone ∨ 本列 top ∨ 本列 filler} 才允许挖，白名单外一格不动
 * （基岩/重力沙砾/他料全免疫——§6 否决 vanilla 的原句「既非 stone 又非 top/filler 的
 * prosperityStone 挖不动 = 啃一层壳就停」由把主体纳入白名单即解）。
 *
 * <p>
 * ═══ 三条观感语义（p21 §1.4 复刻表）═══
 * <ol>
 * <li>lava 底：挖后 {@code y < }{@link #CAVE_LAVA_FLOOR} 置 {@code Blocks.lava}（vanilla :286-288
 * 原样），否则置 {@code null}（本仓空气 = null，框架 {@code isAirOrEmpty}）；</li>
 * <li>floor 铺 top：自顶向下扫，{@code foundTop}（本列已在挖穿路径上见过本列 top）且被挖格的
 * <b>下一格</b>（{@code idx - 1} = y−1，本下标口径的同形推论）== 本列 filler ⇒ 改写为本列 top
 * （vanilla :294-297 同式，洞口地板不破相）；</li>
 * <li>水体/岛柱保护（裁决 1 = P0-FILL R1/R3/R4）：消费 S1a 谓词——
 * {@link ProsperityCaveField#protectedColumn}（岛 ∪ 外扩 4 ∪ 柱）命中列<b>整列不挖</b>；
 * {@link ProsperityCaveField#carveFloorY} 把 {@link ProsperityCaveField#waterColumnProtected}
 * 命中列（湖/河/潭/池四源并集）的下界夹到 {@code max(CAVE_Y_MIN, heightAt + 3)}，壳底钉
 * heightAt（挖后地形面，R4 禁"挖前地面"旁路）⇒ 洞不穿潭池湖河；竖向下界另与基岩带
 * （{@code bedrockTop}）取大（p21 §1.5 表末行）。</li>
 * </ol>
 *
 * <p>
 * ═══ 消费序（S1a 类注释契约）═══ 列级：自顶数组扫出本列地表 y → {@code densityOpenAt} 早出 +
 * {@code canyonAt} 零值合门（两者皆关 ⇒ 82–86% 列不触碰任何保护腿）→ {@code protectedColumn} →
 * {@code carveFloorY}（含 {@code chainRosterIndexAt} 身份，与 populate 期 tierGrid 同源，
 * 零 World 读）→ 自顶向下逐格：{@code tubeAt}（管束）∪ 峡谷谷段 命中且白名单 ⇒ 挖。
 * 峡谷为明缝、天然破面（不受 {@code carveCeilingAt} 封顶，需求 7「地表可生成的峡谷」），
 * 下切深度仍受保护门下界夹制（谷不切到湖床之下）。
 *
 * <p>
 * ═══ CAVE_ENABLED ═══ 编译期常量（非 Config，p21 §7-S1b 回滚条）：置 {@code false} 后
 * {@link #carve} 首行直返、零写入，供二分定位（生成字节类改动不可二分对拍 ⇒ 二分只能靠本常量）。
 */
public final class GTSRCaveCarver {

    /** 编译期总门（非 Config；二分定位用；回滚 = 翻本值或 revert 单行挂点）。 */
    public static final boolean CAVE_ENABLED = true;

    /** lava 底界（vanilla {@code MapGenCaves:286-288} 原样：y 小于该值的挖后格置 lava）。 */
    public static final int CAVE_LAVA_FLOOR = 10;

    private GTSRCaveCarver() {}

    /**
     * 单 chunk 挖洞（provideChunk 期表层之后、Chunk 组装之前，由
     * {@link GTSRChunkProviderBase#carveCaves} 钩子转调；dim78 覆写是唯一生产入口）。
     *
     * @param worldSeed 世界种子（钩子由基类以现产 getSeed() 传入，本类零 World 读）
     * @param baseX     chunk 原点世界 x 坐标（调用方以乘法换算；本类内不做坐标移位）
     * @param baseZ     chunk 原点世界 z 坐标
     * @return 实际写入的挖格数（lava ∪ 空气；floor 铺 top 的改写格不计入），供判据/探针取数
     */
    public static int carve(long worldSeed, int baseX, int baseZ, Block[] blocks, byte[] metadata,
        BiomeGenBase[] biomes) {
        if (!CAVE_ENABLED) {
            return 0;
        }
        int dug = 0;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                dug += carveColumn(worldSeed, baseX + x, baseZ + z, x, z, x << 12 | z << 8, blocks, biomes);
            }
        }
        return dug;
    }

    /**
     * 单列挖洞（下标 = 预组合的 {@code column | y}；{@code wx/wz} 世界坐标、{@code lx/lz} chunk 内
     * 局部坐标——身份经 {@link GTSRChunkProviderBase#biomeAtColumn(BiomeGenBase[], int, int)} 走
     * 唯一下标出口）。返回本列挖格数。
     */
    private static int carveColumn(long worldSeed, int wx, int wz, int lx, int lz, int column, Block[] blocks,
        BiomeGenBase[] biomes) {
        // ① 自顶数组扫本列地表 y（判空门与表层链同一 isAirOrEmpty 谓词源；起点 255）
        int surfaceY = -1;
        for (int y = 255; y >= ProsperityCaveField.CAVE_Y_MIN; y--) {
            if (!GTSRChunkProviderBase.isAirOrEmpty(blocks[column | y])) {
                surfaceY = y;
                break;
            }
        }
        if (surfaceY < ProsperityCaveField.CAVE_Y_MIN) {
            return 0; // 竖向域内无固体（或全基岩）⇒ 无洞可挖
        }
        // ② 成本主控早出（S1a 消费序契约）：密度门关 ∧ 峡谷零 ⇒ 本列不触碰任何保护腿
        final boolean densityOpen = ProsperityCaveField.densityOpenAt(worldSeed, wx, wz);
        final double canyonDepth = ProsperityCaveField.canyonAt(worldSeed, wx, wz);
        if (!densityOpen && canyonDepth <= 0.0D) {
            return 0;
        }
        // ③ 保护门一：岛 ∪ 外扩 ∪ 柱（R3：整列不挖，防"岛面塌陷"）
        if (ProsperityCaveField.protectedColumn(worldSeed, wx, wz)) {
            return 0;
        }
        // ④ 保护门二：水体列下界夹壳（R1 四源并集 / R4 壳底钉 heightAt），另与基岩带取大
        final int rosterIndex = ProsperityTerrainProfile.chainRosterIndexAt(worldSeed, wx >> 2, wz >> 2);
        int floor = ProsperityCaveField.carveFloorY(worldSeed, wx, wz, rosterIndex);
        final int bedrockTop = GTSRChunkProviderBase.bedrockTop(worldSeed, wx, wz);
        if (floor < bedrockTop + 1) {
            floor = bedrockTop + 1;
        }
        if (floor > surfaceY) {
            return 0; // 壳底已在地下水面之上 ⇒ 该列一格不挖（洞不穿潭池湖河的构造保证）
        }
        // ⑤ 封顶与谷段：管束受破面许可封顶；峡谷 = 明缝自地表下切（floor 夹制不变）
        final int tubeTop = densityOpen
            ? Math.min(ProsperityCaveField.carveCeilingAt(worldSeed, wx, wz, surfaceY), ProsperityCaveField.CAVE_Y_MAX)
            : -1;
        final int canyonBottom = canyonDepth > 0.0D ? Math.max(floor, surfaceY - (int) canyonDepth) : surfaceY + 1;
        final int loopTop = Math.min(surfaceY, ProsperityCaveField.CAVE_Y_MAX);
        if (tubeTop < floor && canyonBottom > loopTop) {
            return 0;
        }
        final BiomeGenBase biome = GTSRChunkProviderBase.biomeAtColumn(biomes, lx, lz);
        final Block top = biome == null ? null : biome.topBlock;
        final Block filler = biome == null ? null : biome.fillerBlock;
        final Block body = BlocksGTSR.prosperityStone;
        boolean foundTop = false;
        int dug = 0;
        for (int y = loopTop; y >= floor; y--) {
            final int idx = column | y;
            final boolean hitCanyon = canyonDepth > 0.0D && y >= canyonBottom;
            final boolean hitTube = y <= tubeTop && ProsperityCaveField.tubeAt(worldSeed, wx, y, wz);
            if (!hitCanyon && !hitTube) {
                continue;
            }
            final Block block = blocks[idx];
            // 白名单（p21 §1.3）：stone ∨ 主体 ∨ 本列 top ∨ 本列 filler；白名单外一格不动。
            // null/空气槽显式排除（防 BlocksGTSR 未装配时 null==null 的伪命中）。
            if (block == null || block == Blocks.air) {
                continue;
            }
            if (!(block == Blocks.stone || block == body || block == top || block == filler)) {
                continue;
            }
            if (block == top) {
                foundTop = true; // vanilla :179-181：挖前见 top ⇒ 本列已破面
            }
            if (y < CAVE_LAVA_FLOOR) {
                blocks[idx] = Blocks.lava; // vanilla :286-288 原样；metadata 不动（同 vanilla）
                dug++;
            } else {
                blocks[idx] = null; // 本仓空气 = null
                dug++;
                if (foundTop && top != null && filler != null && blocks[idx - 1] == filler) {
                    blocks[idx - 1] = top; // vanilla :294-297：洞口地板铺 top（idx-1 = y−1 同形）
                }
            }
        }
        return dug;
    }
}
