package com.miaokatze.gtsr.common.dimension.prosperity.ruins;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;
import com.miaokatze.gtsr.common.dimension.framework.structure.ChunkSliceSink;
import com.miaokatze.gtsr.common.dimension.framework.structure.GTSRWorldgenHash;
import com.miaokatze.gtsr.common.dimension.framework.structure.StructureBuilder;
import com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile;
import com.miaokatze.gtsr.common.dimension.prosperity.river.GTSRVoronoiRiverField;

/**
 * 旧栖晴晕<b>光点两趟</b>（v1.20.43 P22-B S4，p21 §5 链序「巨树趟（含岛心树）→ <b>光点趟</b> →
 * 灌木 → 普通树 → 花草 → 碎石 → 沙砾」；方块 = 已注册 {@code BlocksGTSR.prosperityRoostGlow}，
 * S2 零生成接线的补全片）：
 * <ol>
 * <li><b>冠下趟</b> {@link #placeCanopyPass}：经 {@link MegaTreeAnchors#enumerateAnchors}（S3 同一
 * 派生窗）枚举与本 chunk 相交的活湖锚点，逐锚点用 {@link #canopyLightsAt} 纯函数重放巨树冠层信息
 * （{@code IslandMegaTree.crownUnderside} 只读枚举口——形态单一真值源，本类<b>不含</b>任何冠形态
 * 公式），得到「冠 footprint 内、每列冠底格<b>下一格</b>」的候选光位集，再按
 * {@link #CANOPY_LIGHT_DENOM} 逐候选列抽样；</li>
 * <li><b>湖上趟</b> {@link #placeLakePass}：本 chunk 16×16 列上，{@code lakeAt < LAKE_SHORE}（湖水域，
 * 含死湖——湖上光点不要求岛在场）且 {@code heightAt < SEA_LEVEL}（真实水列，排除湖心岛与出水岸坡）
 * 的列，按 {@link #LAKE_LIGHT_DENOM} 抽样，落位 {@code y = SEA_LEVEL}（水面上空首格）。</li>
 * </ol>
 * <b>两档密度独立常量，湖上严格稀于冠下</b>（判据 {@code tools/dim1/LumenLightCheck} A 组按实测
 * 每 chunk 光源数钉 {@code lake < canopy}，不钉绝对值）。
 * <p>
 * <b>随机流纪律（SALT_WIND / SALT_ISLAND_TREE 同款）</b>：一个盐 {@link #SALT_LUMEN} 两种槽位——
 * <b>冠下趟按锚点槽</b> {@code chunkSeed(worldSeed, ax>>4, az>>4)}（冠光位集跨 ≤9 chunk ⇒ 与
 * S3 岛树同一条"任何被跨 chunk 重算得同一集"论证，任一 chunk 独立 decorate 时重放同一光位集，
 * {@link ChunkSliceSink} owns 过滤后只写本片）；<b>湖上趟按本 chunk 槽</b>
 * {@code chunkSeed(worldSeed, chunkX, chunkZ)}（湖上光点单格不跨界，chunk 级自洽即够）。两趟各自的
 * Random 全部由本盐派生 ⇒ 生产入口 {@link ProsperityDecorPlacer#decorate} 的共享 {@code rand}
 * 取数序<b>一位都不动</b>（链序挂在 placeTreePass 岛心树+巨树段之后、灌木段之前）。
 * <p>
 * <b>让行口径（比叶门更窄）</b>：光点<b>只写空气格</b>（{@code world.isAirBlock} 单门，无草/雪宽恕
 * ——干/叶/水/任何既有块一概不覆写）；且抽样决策全部先于世界读（{@link #canopyLightsAt} 纯函数、
 * 湖上趟先掷骰后验列），被让掉的格<b>不消耗</b>额外随机数 ⇒ 光<b>位集</b>与世界内容无关、逐 chunk
 * 重放逐位一致，世界只决定"这一格最终写不写得进"。
 * <p>
 * <b>public 面 = 离线判据重放口</b>（{@code LumenLightCheck}：密度比值臂分趟独立跑、9-chunk 并集
 * 对拍 {@link #canopyLightsAt} 参照臂；先例 {@code IslandMegaTree.placeInto} 的重放口纪律）；
 * 生产侧唯一调用者是 {@code ProsperityDecorPlacer.placeTreePass}（一行委托
 * {@link #placeLumenPass}）。不进 {@code PlacementGate}（p21 §5：光点无结构契约）。
 */
public final class ProsperityLumenPlacer {

    /**
     * 盐 "LUMN"（0x4C554D4E 截断；P22-B S4 光点两趟的<b>唯一</b>派生盐）：冠下趟锚点槽 /
     * 湖上趟 chunk 槽两种派生共用本盐（与 {@code ProsperityDecorPlacer.SALT_ISLAND_TREE} 锚点槽
     * 先例同族），既区别于岛树盐 0x49534C4E44 也区别于风蚀柱盐 0x77696E64 ⇒ 三条随机流互不扰动。
     */
    public static final long SALT_LUMEN = 0x4C554D4EL;

    /**
     * 冠下密度：每个候选列（冠 footprint 内有冠壳格的列）的 1/N 抽样分母。候选列 ≈ π·(r²+r) ≈ 754
     * ⇒ 单树期望 ≈ 15.7 枚光点，摊到 ≤2.9 个被跨 chunk。判据只钉「湖上 &lt; 冠下」比值，不钉本值
     * （实机校准旋钮 = 本常量，调它不动盐）。
     */
    static final int CANOPY_LIGHT_DENOM = 48;

    /**
     * 湖上密度：每个合格水列的 1/N 抽样分母。{@link #CANOPY_LIGHT_DENOM}=48 与冠下候选列 ~754/树
     * 相比，本档按「全水列 256/chunk ÷ 192 ≈ 1.3/chunk」对「冠下 ≈ 5.4/chunk（被跨窗均值）」取
     * 严格更稀（判据 A 组实测带 ≤0.8×）；<b>必须严格大于 {@link #CANOPY_LIGHT_DENOM}</b>
     * （分母大 ⇒ 密度小，判据 SOURCE 组钉偏序）。
     */
    static final int LAKE_LIGHT_DENOM = 192;

    /** 单锚点光位缓冲上限（footprint 31×31 = 961 列封顶，防御性；判据按同一常量分配）。 */
    public static final int CANOPY_LIGHT_CAP = IslandMegaTree.CROWN_QUERY_SIDE * IslandMegaTree.CROWN_QUERY_SIDE;

    private ProsperityLumenPlacer() {}

    /**
     * 单锚点冠下光位集（<b>纯函数重放口</b>，判据 9-chunk 并集对拍的参照臂）：formRand 以
     * {@code ProsperityDecorPlacer.SALT_ISLAND_TREE} 锚点槽派生（与 {@code placeIslandTreePass}
     * 同一派生式、盐单源——重放的是<b>同一棵</b>树的冠层），{@code IslandMegaTree.crownUnderside}
     * 给出每列冠底带，lumenRand 以 {@link #SALT_LUMEN} <b>同一锚点槽</b>派生做抽样。
     * 光位 = (ax+dx, 冠底−1, az+dz)——冠壳最低格正下方一格（贴冠底悬挂，非冠内）。
     * 零 World 读、零写入；扫描序 dx 外层 dz 内层固定 ⇒ 取数序确定。
     *
     * @param out 行数 ≥ {@link #CANOPY_LIGHT_CAP}、每行 ≥ 3；[0]=x、[1]=y、[2]=z
     * @return 写入行数（整树 255 早退 ⇒ 0）
     */
    public static int canopyLightsAt(long worldSeed, int ax, int az, int y0, int[][] out) {
        final Random formRand = new Random(
            GTSRWorldgenHash.chunkSeed(worldSeed, ax >> 4, az >> 4) ^ ProsperityDecorPlacer.SALT_ISLAND_TREE);
        final int[] underside = new int[IslandMegaTree.CROWN_QUERY_SIDE * IslandMegaTree.CROWN_QUERY_SIDE];
        final int trunkH = IslandMegaTree.crownUnderside(formRand, y0, underside);
        if (trunkH < 0) {
            return 0;
        }
        final int cy = y0 + trunkH - IslandMegaTree.CROWN_CENTER_INSET;
        final Random lumenRand = new Random(GTSRWorldgenHash.chunkSeed(worldSeed, ax >> 4, az >> 4) ^ SALT_LUMEN);
        final int r = MegaTreeAnchors.CANOPY_RADIUS;
        final int side = IslandMegaTree.CROWN_QUERY_SIDE;
        int n = 0;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                final int minDy = underside[(dx + r) * side + (dz + r)];
                if (minDy == Integer.MIN_VALUE) {
                    continue; // 该列无冠壳格（footprint 圆盘界外）——不消耗 rand
                }
                if (lumenRand.nextInt(CANOPY_LIGHT_DENOM) != 0) {
                    continue;
                }
                if (n >= out.length) {
                    return n; // 防御性封顶（候选列数 ≤ 961 = CANOPY_LIGHT_CAP，正常不可达）
                }
                out[n][0] = ax + dx;
                out[n][1] = cy + minDy - 1;
                out[n][2] = az + dz;
                n++;
            }
        }
        return n;
    }

    /**
     * 冠下趟（生产挂点 = {@code ProsperityDecorPlacer.placeTreePass}，经 {@link #placeLumenPass}）：
     * 枚举本 chunk 派生窗内相交的全部活湖锚点（S3 同一 {@code enumerateAnchors}），逐锚点重放
     * {@link #canopyLightsAt} 光位集，写入经 {@link ChunkSliceSink}——非本 chunk 的格静默吸收
     * （owns 单射），邻 chunk 在自己的趟里枚举到<b>同一锚点</b>重放<b>同一</b>光位集 ⇒ 跨 chunk
     * 无缝无缺角。空气门在写入处逐格判定（让行不进抽样）。
     */
    public static void placeCanopyPass(World world, long worldSeed, int chunkX, int chunkZ, BlockSink sink) {
        if (sink == null) {
            return;
        }
        final double[][] anchors = new double[MegaTreeAnchors.ENUM_CAP][MegaTreeAnchors.ANCHOR_OUT_LEN];
        final int n = MegaTreeAnchors
            .enumerateAnchors(worldSeed, chunkX, chunkZ, MegaTreeAnchors.CANOPY_RADIUS, anchors);
        if (n == 0) {
            return;
        }
        final StructureBuilder slice = new StructureBuilder(new ChunkSliceSink(sink, chunkX, chunkZ));
        final int[][] lights = new int[CANOPY_LIGHT_CAP][3];
        final Block glow = BlocksGTSR.prosperityRoostGlow;
        for (int i = 0; i < n; i++) {
            final int count = canopyLightsAt(
                worldSeed,
                (int) anchors[i][0],
                (int) anchors[i][1],
                (int) anchors[i][4],
                lights);
            for (int k = 0; k < count; k++) {
                if (!world.isAirBlock(lights[k][0], lights[k][1], lights[k][2])) {
                    continue; // 让行：干/枝/叶/任何既有块上不落光（含被邻趟已写的格）
                }
                slice.setBlock(lights[k][0], lights[k][1], lights[k][2], glow, 0, BlockSink.FLAG_POPULATE);
            }
        }
    }

    /**
     * 湖上趟：本 chunk 全列<b>先掷骰后验列</b>（1/{@link #LAKE_LIGHT_DENOM} 骰每列一枚、固定 256 枚
     * ⇒ 取数序与世界无关），命中列验两条纯函数腿——{@code lakeAt < LAKE_SHORE}（湖水域；死湖也有
     * 湖上光点，不要求岛/树在场）且 {@code heightAt < SEA_LEVEL}（真实水列）——再过空气门落位
     * {@code y = SEA_LEVEL}（置水最高格 y=67 之上首格 = 水面上空）。写入同样经
     * {@link ChunkSliceSink}（湖上光点本就不出 chunk，切片层只作统一通道）。
     */
    public static void placeLakePass(World world, long worldSeed, int chunkX, int chunkZ, BlockSink sink) {
        if (sink == null) {
            return;
        }
        final Random rand = new Random(GTSRWorldgenHash.chunkSeed(worldSeed, chunkX, chunkZ) ^ SALT_LUMEN);
        final StructureBuilder slice = new StructureBuilder(new ChunkSliceSink(sink, chunkX, chunkZ));
        final Block glow = BlocksGTSR.prosperityRoostGlow;
        final int y = ProsperityTerrainProfile.SEA_LEVEL;
        final int baseX = chunkX << 4;
        final int baseZ = chunkZ << 4;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                if (rand.nextInt(LAKE_LIGHT_DENOM) != 0) {
                    continue; // 先骰后验：骰序恒 256 枚/趟，列条件与空气门不反馈进随机流
                }
                final int wx = baseX + x;
                final int wz = baseZ + z;
                if (GTSRVoronoiRiverField.lakeAt(worldSeed, wx, wz) >= GTSRVoronoiRiverField.LAKE_SHORE) {
                    continue;
                }
                if (ProsperityTerrainProfile.heightAt(worldSeed, wx, wz) >= y) {
                    continue;
                }
                if (!world.isAirBlock(wx, y, wz)) {
                    continue; // 让行：水面上空被占（桥/枝/已落结构）即让
                }
                slice.setBlock(wx, y, wz, glow, 0, BlockSink.FLAG_POPULATE);
            }
        }
    }

    /**
     * 生产入口（p21 §5 链序「巨树趟（含岛心树）→ 光点趟 → …」的整趟委托）：冠下 → 湖上两趟串行，
     * 各自独立 Random；<b>不接收共享 {@code rand}</b>（调用方一行委托，签名无 Random ⇒ 共享流
     * 零取数由签名钉死）。
     */
    public static void placeLumenPass(World world, long worldSeed, int chunkX, int chunkZ, BlockSink sink) {
        placeCanopyPass(world, worldSeed, chunkX, chunkZ, sink);
        placeLakePass(world, worldSeed, chunkX, chunkZ, sink);
    }
}
