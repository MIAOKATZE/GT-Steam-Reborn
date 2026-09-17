package com.miaokatze.gtsr.common.dimension.shattered;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.gen.NoiseGeneratorOctaves;
import net.minecraft.world.gen.NoiseGeneratorSimplex;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.terraingen.ChunkProviderEvent;

import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.framework.GTSRChunkProviderBase;
import com.miaokatze.gtsr.common.dimension.shattered.biome.BiomeSingularityWaste;

import cpw.mods.fml.common.eventhandler.Event.Result;

/**
 * 破碎之地地形生成器（dim1 S6a，04 §1.2-§1.6 密度公式逐项落地）。
 * <p>
 * 密度公式（M40 去基座 + 高频化变体，04 §1.2）：
 * {@code density = ridged(x,z) * fragMask(x,z) - falloff(y) - cut(x,z)}，正值成石、负值虚空（无海平面流体）。
 * <ul>
 * <li>ridged：1-|simplex| 反向噪声，主波长 96，脊线即岛链；</li>
 * <li>fragMask：高频碎片掩码，波长 40，阈值 0.50（低于阈值线性压碎至 0.55 倍）；</li>
 * <li>falloff：三段式（y&gt;144 顶收敛 / 24&lt;y&lt;=144 近平坦 + 低幅细起伏 / y&lt;=24 岛底收薄）；</li>
 * <li>cut：低频切缝，波长 180，raw&gt;0.35 处按 (raw-0.35)*2.2 强制虚空（断裂峡谷）。</li>
 * </ul>
 * 采样：x/z 平面噪声与 y 无关，5×5 平面粗网格（步距 4）双线性插值 + falloff 逐 y 精确求值
 * （04 §1.4 自注"先算 5x5 平面噪声数组再对 y 循环"；falloff 精确求值保证 24/144 两个分段点
 * 不被粗网格步距抹平，单 chunk 噪声调用 ≤ 5×5×3 + 高度扰动 256×4 次）。
 * <p>
 * 裂隙层（04 §1.6）：y&lt;20 不走密度公式——y=0 基岩、y∈[1,19] 黑石 + 竖直裂隙缝
 * （cutNoise 23 波长段，raw&gt;0.62 且 y&gt;3 开缝）+ 每 chunk 1-3 个球腔（严格钳制 y&lt;20）。
 * <p>
 * 表面替换（04 §1.5）：自顶向下找首个裸露石面，top/filler 按 1-2 格深度替换（每列只处理最高面）；
 * 奇点荒原额外按 13 波长噪声把黑石斑咬入 filler 带之下 3 格。酸雾源点缀不做（用户裁剪，见 04 §2.1）。
 * <p>
 * 结构生成不经本类（IWorldGenerator = S6b 责任）。
 */
public class ChunkProviderShatteredLands extends GTSRChunkProviderBase {

    // —— 密度公式参数（04 §1.2 对照表 / §1.3 常量表，逐项一致）——
    /** ridged 主波长（M40=220 → 破碎 96，岛更小更密）。 */
    private static final double RIDGED_WAVELENGTH = 96.0D;
    /** 碎片掩码波长（新增高频掩码，把大岛切碎成大陆碎片群）。 */
    private static final double FRAG_WAVELENGTH = 40.0D;
    /** 断裂切缝波长（低频）。 */
    private static final double CUT_WAVELENGTH = 180.0D;
    /** 掩码阈值（M40=0.42 → 破碎 0.50，覆盖率降至 18-22%）。 */
    private static final double MASK_THRESHOLD = 0.50D;
    /** 切缝阈值：raw &gt; 此值按比例强制虚空。 */
    private static final double CUT_THRESHOLD = 0.35D;
    /** 岛体最低生成 y（裂隙层之上）。 */
    private static final int ISLAND_FLOOR = 24;
    /** 岛体最高生成 y（顶收敛开始）。 */
    private static final int ISLAND_CEIL = 144;

    // —— 裂隙层参数（04 §1.6）——
    /** 裂隙层上界（开区间）：裂隙层只覆盖 y&lt;20。 */
    private static final int RIFT_LAYER_TOP = 20;
    /** 竖缝噪声波长（复用 cutNoise 另一波段）。 */
    private static final double RIFT_SEAM_WAVELENGTH = 23.0D;
    /** 竖缝阈值：raw &gt; 0.62 且 y&gt;3 开竖井。 */
    private static final double RIFT_SEAM_THRESHOLD = 0.62D;

    // —— 荒原黑石斑（04 §1.5 scatterWasteDecay 的噪声口径）——
    private static final double WASTE_PATCH_WAVELENGTH = 13.0D;
    private static final double WASTE_PATCH_THRESHOLD = 0.72D;
    /** 黑石斑咬入深度（filler 带之下再替换的石格数）。 */
    private static final int WASTE_PATCH_DEPTH = 3;

    /** 平面粗网格边长（5×5，步距 4，覆盖 16 格 + 1 个右/下边界采样点）。 */
    private static final int PLANE_GRID = 5;
    private static final int PLANE_STEP = 4;

    private final NoiseGeneratorSimplex ridgedNoise;
    private final NoiseGeneratorSimplex fragNoise;
    private final NoiseGeneratorSimplex cutNoise;
    private final NoiseGeneratorOctaves heightNoise;

    public ChunkProviderShatteredLands(World world, long seed) {
        super(world, seed);
        // 噪声实例种子（04 §1.3 构造器：seed / seed^0x5EED1 / seed^0xC17 / seed^0xABC）
        this.ridgedNoise = new NoiseGeneratorSimplex(new Random(seed));
        this.fragNoise = new NoiseGeneratorSimplex(new Random(seed ^ 0x5EED1L));
        this.cutNoise = new NoiseGeneratorSimplex(new Random(seed ^ 0xC17L));
        this.heightNoise = new NoiseGeneratorOctaves(new Random(seed ^ 0xABCL), 4);
    }

    /**
     * 密度公式地形填充（04 §1.4）：先 5×5 平面噪声粗网格，再逐列双线性插值 + 逐 y 精确 falloff。
     * density &gt; 0 → stone（表面替换后续做），&lt;= 0 → 保持 air（虚空）。
     */
    @Override
    protected void generateTerrain(int chunkX, int chunkZ, Block[] blocks, byte[] metadata,
        BiomeGenBase[] biomesForGeneration) {
        final double[] planeLand = new double[PLANE_GRID * PLANE_GRID];
        final double[] planeCut = new double[PLANE_GRID * PLANE_GRID];
        for (int gx = 0; gx < PLANE_GRID; gx++) {
            for (int gz = 0; gz < PLANE_GRID; gz++) {
                final double[] plane = samplePlane(chunkX * 16 + gx * PLANE_STEP, chunkZ * 16 + gz * PLANE_STEP);
                planeLand[gx * PLANE_GRID + gz] = plane[0];
                planeCut[gx * PLANE_GRID + gz] = plane[1];
            }
        }
        final int baseX = chunkX * 16;
        final int baseZ = chunkZ * 16;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                final int column = x << 12 | z << 8;
                final int gx = x >> 2;
                final int gz = z >> 2;
                final double tx = (x & 3) / (double) PLANE_STEP;
                final double tz = (z & 3) / (double) PLANE_STEP;
                final double land = bilerp(planeLand, gx, gz, tx, tz);
                final double cut = bilerp(planeCut, gx, gz, tx, tz);
                final double heightMod = heightModulation(baseX + x, baseZ + z);
                for (int y = RIFT_LAYER_TOP; y < 256; y++) {
                    if (land - falloff(y, heightMod) - cut > 0.0D) {
                        blocks[column | y] = Blocks.stone;
                    }
                }
            }
        }
        // 裂隙层（y<20）在密度地形之后覆写（同 chunk 构造阶段，无跨 chunk 依赖）
        generateRiftLayer(blocks, chunkX, chunkZ);
    }

    /**
     * 平面噪声打包（04 §1.4 samplePlane 逐项一致）：
     * ridged = 1-|simplex(x/96, z/96)|；fragMask 波长 40 阈值 0.50；
     * cut 波长 180 阈值 0.35；返回 {ridged*mask*1.35, cut}。
     */
    private double[] samplePlane(double wx, double wz) {
        final double ridged = 1.0D
            - Math.abs(this.ridgedNoise.func_151605_a(wx / RIDGED_WAVELENGTH, wz / RIDGED_WAVELENGTH));
        final double frag = this.fragNoise.func_151605_a(wx / FRAG_WAVELENGTH, wz / FRAG_WAVELENGTH) * 0.5D + 0.5D;
        final double mask = frag > MASK_THRESHOLD ? 1.0D : (frag / MASK_THRESHOLD) * 0.55D;
        final double cutRaw = this.cutNoise.func_151605_a(wx / CUT_WAVELENGTH, wz / CUT_WAVELENGTH) * 0.5D + 0.5D;
        final double cut = cutRaw > CUT_THRESHOLD ? (cutRaw - CUT_THRESHOLD) * 2.2D : 0.0D;
        return new double[] { ridged * mask * 1.35D, cut };
    }

    /**
     * 三段式高度收敛（04 §1.4 sampleDensity falloff 分支逐项一致）：
     * y&gt;144 顶收敛（越近天越薄）；24&lt;y&lt;=144 近平坦 + 低幅细起伏；y&lt;=24 岛底收薄不穿透裂隙层。
     */
    private double falloff(int y, double heightMod) {
        if (y > ISLAND_CEIL) {
            return (y - ISLAND_CEIL) / (256.0D - ISLAND_CEIL) * 1.6D;
        }
        if (y > ISLAND_FLOOR) {
            return 0.05D + heightMod * 0.05D;
        }
        return 1.0D - (y / (double) ISLAND_FLOOR) * 0.5D;
    }

    /** 岛面低幅细起伏（04 §1.3 heightNoise：4 倍频程，64 格尺度，钳 [-1,1]）。 */
    private double heightModulation(int wx, int wz) {
        final double[] buf = new double[1];
        this.heightNoise.generateNoiseOctaves(buf, wx, 0, wz, 1, 1, 1, 1.0D / 64.0D, 1.0D / 64.0D, 1.0D / 64.0D);
        return Math.max(-1.0D, Math.min(1.0D, buf[0]));
    }

    /** 平面粗网格双线性插值（4 格单元；gx/gz ∈ [0,3]，边界采样点 gx+1/gz+1 ≤ 4）。 */
    private double bilerp(double[] grid, int gx, int gz, double tx, double tz) {
        final double c00 = grid[gx * PLANE_GRID + gz];
        final double c10 = grid[(gx + 1) * PLANE_GRID + gz];
        final double c01 = grid[gx * PLANE_GRID + gz + 1];
        final double c11 = grid[(gx + 1) * PLANE_GRID + gz + 1];
        final double a = c00 + (c10 - c00) * tx;
        final double b = c01 + (c11 - c01) * tx;
        return a + (b - a) * tz;
    }

    /**
     * 裂隙层（04 §1.6 generateRiftLayer 逐项一致）：y=0 基岩；y∈[1,19] 黑石基底 +
     * 竖直裂隙缝（23 波长噪声 raw&gt;0.62 且 y&gt;3 开缝直通虚空底部）；每 chunk 掷骰 1-3 个
     * r∈[2,5] 球腔回填 air。球腔严格钳制 y∈[1,19]（裂隙层不越 y&lt;20 边界）。
     */
    private void generateRiftLayer(Block[] blocks, int chunkX, int chunkZ) {
        final int baseX = chunkX * 16;
        final int baseZ = chunkZ * 16;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                final int column = x << 12 | z << 8;
                blocks[column] = Blocks.bedrock;
                final double seam = this.cutNoise
                    .func_151605_a((baseX + x) / RIFT_SEAM_WAVELENGTH, (baseZ + z) / RIFT_SEAM_WAVELENGTH) * 0.5D
                    + 0.5D;
                for (int y = 1; y < RIFT_LAYER_TOP; y++) {
                    blocks[column | y] = seam > RIFT_SEAM_THRESHOLD && y > 3 ? Blocks.air
                        : BlocksGTSR.shatteredBlackstone;
                }
            }
        }
        final int caves = 1 + this.rand.nextInt(3);
        for (int i = 0; i < caves; i++) {
            final int cx = this.rand.nextInt(16);
            final int cy = 4 + this.rand.nextInt(12);
            final int cz = this.rand.nextInt(16);
            final int r = 2 + this.rand.nextInt(4);
            carveRiftSphere(blocks, cx, cy, cz, r);
        }
    }

    /** 球腔回填 air（y 严格钳制 [1,19]；y=0 基岩不破坏；不跨 chunk 写入）。 */
    private void carveRiftSphere(Block[] blocks, int cx, int cy, int cz, int r) {
        for (int dx = -r; dx <= r; dx++) {
            final int x = cx + dx;
            if (x < 0 || x > 15) {
                continue;
            }
            for (int dz = -r; dz <= r; dz++) {
                final int z = cz + dz;
                if (z < 0 || z > 15) {
                    continue;
                }
                for (int dy = -r; dy <= r; dy++) {
                    final int y = cy + dy;
                    if (y < 1 || y >= RIFT_LAYER_TOP) {
                        continue;
                    }
                    if (dx * dx + dy * dy + dz * dz <= r * r) {
                        blocks[x << 12 | z << 8 | y] = Blocks.air;
                    }
                }
            }
        }
    }

    /**
     * 表面替换（04 §1.5）：每列自顶向下找首个"石面 + 上方空气"，替换 top 并按 1-2 格深度
     * 替换 filler；奇点荒原列按 13 波长噪声把黑石斑咬入 filler 带之下 3 格。
     * 循环自 y=254 起（04 样板自 255 起会在最高列 idx+1 越界 65536）；每列只处理最高裸露面
     * （浮岛悬空底面保持石体，04 §1.5 break 语义）。
     * 酸雾源点缀不做（用户裁剪，04 §2.1 不入本切片）。
     */
    @Override
    protected void replaceBlocksForBiome(int chunkX, int chunkZ, Block[] blocks, byte[] metadata,
        BiomeGenBase[] biomes) {
        // 保留 S1 框架的 ReplaceBiomeBlocks 事件契约（兼容第三方 terraingen 钩子）
        final ChunkProviderEvent.ReplaceBiomeBlocks event = new ChunkProviderEvent.ReplaceBiomeBlocks(
            this,
            chunkX,
            chunkZ,
            blocks,
            metadata,
            biomes,
            null);
        MinecraftForge.EVENT_BUS.post(event);
        if (event.getResult() == Result.DENY) {
            return;
        }
        final int baseX = chunkX * 16;
        final int baseZ = chunkZ * 16;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                final BiomeGenBase biome = biomes[z + x * 16];
                final int depth = 1 + this.rand.nextInt(2); // filler 深度 1-2 格（04 §1.5）
                final boolean wastePatch = biome instanceof BiomeSingularityWaste
                    && wastePatchNoise(baseX + x, baseZ + z);
                for (int y = 254; y > ISLAND_FLOOR; y--) {
                    final int idx = x << 12 | z << 8 | y;
                    if (blocks[idx] == Blocks.stone && blocks[idx + 1] == Blocks.air) {
                        blocks[idx] = biome.topBlock;
                        for (int d = 1; d <= depth; d++) {
                            final int fidx = idx - d;
                            if (blocks[fidx] == Blocks.stone) {
                                blocks[fidx] = biome.fillerBlock;
                            }
                        }
                        if (wastePatch) {
                            for (int d = depth + 1; d <= depth + WASTE_PATCH_DEPTH; d++) {
                                final int pidx = idx - d;
                                if (blocks[pidx] == Blocks.stone) {
                                    blocks[pidx] = BlocksGTSR.shatteredBlackstone;
                                }
                            }
                        }
                        break; // 每列只处理最高裸露面（04 §1.5）
                    }
                }
            }
        }
    }

    /** 荒原黑石斑噪声（复用 fragNoise 高频段，确定性、与 chunk 随机数顺序解耦）。 */
    private boolean wastePatchNoise(int wx, int wz) {
        return this.fragNoise.func_151605_a(wx / WASTE_PATCH_WAVELENGTH, wz / WASTE_PATCH_WAVELENGTH) * 0.5D + 0.5D
            > WASTE_PATCH_THRESHOLD;
    }
}
