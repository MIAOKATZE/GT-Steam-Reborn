package com.miaokatze.gtsr.common.dimension.prosperity.ruins;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;
import com.miaokatze.gtsr.common.dimension.framework.structure.GTSRWorldgenHash;
import com.miaokatze.gtsr.common.dimension.framework.structure.StructureBuilder;
import com.miaokatze.gtsr.common.dimension.framework.structure.StructureRegistry;
import com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityBlockResolver;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityVariants;
import com.miaokatze.gtsr.config.Config;

/**
 * 城外中型废墟放置器（dim78 S-A5，plan §4 S-A5 / §12 修订 7/8："结构自然散布，城外废墟一等公民"）：
 * 6 个中型变体（footprint ≤16×16×12，钳单 chunk 同 {@link RuinedMachinePlacer} :89-96 范式），
 * 频率 = 1/{@link Config#prosperityOutpostChance}（默认 64）/chunk，per-chunk 确定性哈希掷骰。
 * <p>
 * <b>记号族与键复用 {@link CityVariants}</b>（含 S-A4 新增 {@code 'X'}=GT5U 镀铜砖块
 * sBlockCasings1 meta10、{@code 'Z'}=GT5U 固体钢机械外壳 sBlockCasings2 meta0——GT5U 福利
 * 直达城外，"找到后可获得更多"），char→(键,meta) 全部经 {@link CityVariants#blockKeyOf(char)}/
 * {@link CityVariants#metaOf(char)}；游戏侧解析经 {@link CityBlockResolver}（GT 字段 null 时不
 * put 的既有防御链）。损伤档/朝向复用 {@link CityVariants#damageTier(long)}/
 * {@link CityVariants#rotationOf(long)} 纯函数（outpostSeed 派生）。
 * <p>
 * 落地契约：接地 y = {@link ProsperityTerrainProfile#heightAt}（高度红线同源，每列独立）；
 * 落点门 = {@link #isNaturalProsperityTop(Block)}（<b>S-A1 连带放宽</b>：四自然 top 方块族 ∪
 * prosperitySurface——A1 主体换装后自然区 top 已是群系新方块，仅认 prosperitySurface 会使
 * 城外结构全灭，见 dim78-fix-slice-A1-report §8）。
 * <p>
 * 编排契约：由 {@code ProsperityWorldGenerator.generate} 在城 buffer 窗外（cities.length&gt;0
 * return 天然保证）<b>先掷 outpost、命中则本 chunk 跳过残缺机器</b>（同 chunk 互斥掷骰，防
 * footprint 撞格）；散布/装饰在 outpost 之后照常运行（其空气让行门不会覆盖本结构）。
 * 所有随机从 chunk 确定性哈希派生（盐 "OUtP" 0x4F557450），禁用 populate 裸 Random。
 */
public final class ProsperityOutpostPlacer {

    /** 盐 "OUtP"（outpost 助记；与机器 "mac"/散布 "ScAt" 盐隔离）。 */
    private static final long SALT_OUTPOST = 0x4F557450L;

    /** 放置细节种子盐（旋转/损伤/缺失掷骰与存在掷骰解耦）。 */
    private static final long SALT_PLACE = 0x5A4C4EL;

    private static volatile boolean registered;

    /** 单个 outpost 变体：注册名 + footprint (sizeX×sizeZ) + 层数 sizeY + 自上而下形状串。 */
    public static final class Outpost {

        public final String name;
        public final int sizeX;
        public final int sizeY;
        public final int sizeZ;
        /** layers[0] = 顶层；layers[layer] 为该层 sizeZ 行、每行 sizeX 字符。 */
        public final String[][] layers;

        public Outpost(String name, int sizeX, int sizeY, int sizeZ, String[][] layers) {
            this.name = name;
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
            this.layers = layers;
        }

        /** 取（世界向上 y ∈ 0..sizeY-1，dx ∈ 0..sizeX-1，dz ∈ 0..sizeZ-1）处字符。 */
        public char charAt(int y, int dx, int dz) {
            return this.layers[this.sizeY - 1 - y][dz].charAt(dx);
        }
    }

    // ═══ 6 中型变体（plan §4 S-A5 清单序；记号族 = CityVariants 全集含 'X'/'Z'）═══

    /** ① 瞭望台残躯 7×11×7：塔身残段 + 镀铜腰带环 + 钢护角，顶部塌失、南门洞。 */
    public static final Outpost WATCH_POST = new Outpost(
        "outpost_watch_post",
        7,
        11,
        7,
        new String[][] {
            // y=10 塌顶残段（SW 角残芯）
            { ".#.....", ".#.....", ".#.....", ".......", ".......", ".......", "......." },
            // y=9 残垛环
            { ".#####.", "#.....#", "#.....#", "#.....#", "#.....#", "#.....#", ".#####." },
            // y=8 镀铜腰带环（'X'，S-A4 福利键）
            { ".XXXXX.", "X.....X", "X.....X", "X.....X", "X.....X", "X.....X", ".XXXXX." },
            // y=7 瞭望窗带（铁栏杆残梗）
            { ".#####.", "#.B.B.#", "#.....#", "#.....#", "#.....#", "#.B.B.#", ".#####." },
            // y=6 梯井残板
            { ".#####.", "#.....#", "#..s..#", "#.....#", "#.....#", "#.....#", ".#####." },
            // y=5..3 塔身环
            { ".#####.", "#.....#", "#.....#", "#.....#", "#.....#", "#.....#", ".#####." },
            { ".#####.", "#.....#", "#.....#", "#.....#", "#.....#", "#.....#", ".#####." },
            { ".#####.", "#.....#", "#.....#", "#.....#", "#.....#", "#.....#", ".#####." },
            // y=2 钢护角（'Z'）
            { "Z#####Z", "#.....#", "#.....#", "#.....#", "#.....#", "#.....#", "Z#####Z" },
            // y=1 南墙门洞
            { ".#####.", "#.....#", "#.....#", "#.....#", "#.....#", "#.....#", ".##.##." },
            // y=0 铺面垫层
            { "sssssss", "sssssss", "sssssss", "sssssss", "sssssss", "sssssss", "sssssss" } });

    /** ② 断拱渠段 16×8×5：三墩双拱渠，中墩下段坍塌、右端渠体断失（露镀铜渠槽 'X' + 钢墩帽 'Z'）。 */
    public static final Outpost BROKEN_AQUEDUCT = new Outpost(
        "outpost_broken_aqueduct",
        16,
        8,
        5,
        new String[][] {
            // y=7 渠槽侧壁（镀铜砖 'X'；右端断失——第三墩成"无渠孤墩"）
            { "................", ".XXXXXXXXXX.....", "................", ".XXXXXXXXXX.....", "................" },
            // y=6 渠体板
            { "................", ".#############..", ".#############..", ".#############..", "................" },
            // y=5 起拱（钢墩帽 'Z' + 锈壳墩）
            { "................", ".ZZ....ZZ....ZZ.", ".##....##....##.", ".##....##....##.", "................" },
            // y=4 拱墩
            { "................", ".##....##....##.", ".##....##....##.", ".##....##....##.", "................" },
            // y=3 中墩下段已塌
            { "................", ".##..........##.", ".##..........##.", ".##..........##.", "................" },
            // y=2 塌墩渣堆
            { "................", ".##.....GG...##.", ".##.....GG...##.", ".##.....GG...##.", "................" },
            // y=1 加宽墩基
            { "................", ".###...###...###", ".###...###...###", ".###...###...###", "................" },
            // y=0 墩基铺面
            { "................", "..ss....ss....ss", "..ss....ss....ss", "..ss....ss....ss", "................" } });

    /** ③ 翻倒锅炉座 9×6×7：卧倒筒体（钢箍带 'Z' + 侧撕口），碎瓷垫楔、半埋基座。 */
    public static final Outpost TOPPLED_BOILER = new Outpost(
        "outpost_toppled_boiler",
        9,
        6,
        7,
        new String[][] {
            // y=5 筒顶弧 + 撕裂人孔
            { ".........", ".........", "..XXXXX..", "..XX.XX..", "..XXXXX..", ".........", "........." },
            // y=4 钢箍带环（'Z'）
            { ".........", "..ZZZZZ..", ".XXXXXXX.", ".XXXXXXX.", ".XXXXXXX.", "..ZZZZZ..", "........." },
            // y=3 筒身最宽 + 侧面撕裂口
            { "..ZZZZZ..", ".XXXXXXX.", "XXXXXXXXX", "XXXX..XXX", "XXXXXXXXX", ".XXXXXXX.", "..ZZZZZ.." },
            // y=2 筒身触地段
            { ".........", ".XXXXXXX.", "XXXXXXXXX", "XXXXXXXXX", "XXXXXXXXX", ".XXXXXXX.", "........." },
            // y=1 碎瓷壳垫楔（'%'）
            { ".........", "..%XXX%..", ".XXXXXXX.", ".XXXXXXX.", ".XXXXXXX.", "..%XXX%..", "........." },
            // y=0 半埋基座（沙砾+铺面）
            { ".........", ".........", "..GGSGG..", "..GsGsG..", "..GGSGG..", ".........", "........." } });

    /** ④ 砖窑残躯 8×9×8：蜂窝砖窑，顶冠塌失、镀铜窑圈 'X'、积碳火室、钢门框 'Z'。 */
    public static final Outpost BRICK_KILN = new Outpost(
        "outpost_brick_kiln",
        8,
        9,
        8,
        new String[][] {
            // y=8 塌失残冠（对角残段）
            { "..##....", ".##.....", "........", "........", "........", "........", ".....##.", "....##.." },
            // y=7 镀铜窑圈（'X'）
            { "..XXXX..", ".X....X.", "X......X", "X......X", "X......X", "X......X", ".X....X.", "..XXXX.." },
            // y=6..4 窑身环
            { "..####..", ".#....#.", "#......#", "#......#", "#......#", "#......#", ".#....#.", "..####.." },
            { "..####..", ".#....#.", "#......#", "#......#", "#......#", "#......#", ".#....#.", "..####.." },
            { "..####..", ".#....#.", "#......#", "#......#", "#......#", "#......#", ".#....#.", "..####.." },
            // y=3 火室积碳环
            { "..@@@@..", ".@....@.", "@......@", "@......@", "@......@", "@......@", ".@....@.", "..@@@@.." },
            // y=2 南向火口（钢门框 'Z'）
            { "..####..", ".#....#.", "#......#", "#......#", "#......#", "#......#", ".#....#.", ".Z#..#Z." },
            // y=1 炉膛底（沙砾+铁栏杆炉栅）
            { "..####..", ".#GGGG#.", "#GB..BG#", "#G....G#", "#G....G#", "#GB..BG#", ".#GGGG#.", "..####.." },
            // y=0 窑基
            { "ssssssss", "ssssssss", "ssssssss", "ssssssss", "ssssssss", "ssssssss", "ssssssss", "ssssssss" } });

    /** ⑤ 塌桁桥段 16×7×6：钢桁顶弦 'Z' + 中跨塌落渣堆，两端墩基、镀铜桥缘 'X'。 */
    public static final Outpost COLLAPSED_TRUSS = new Outpost(
        "outpost_collapsed_truss",
        16,
        7,
        6,
        new String[][] {
            // y=6 桥面（中跨断口；两缘镀铜砖 'X'）
            { "................", "................", "X#####....#####X", "X#####....#####X", "................",
                "................" },
            // y=5 钢桁顶弦（'Z'）
            { "ZZ............ZZ", "................", "................", "................", "................",
                "ZZ............ZZ" },
            // y=4 斜腹杆 + 中跨塌渣
            { "#Z............Z#", ".#.....GGG....#.", ".......GGG......", ".......GGG......", ".#.....GGG....#.",
                "#Z............Z#" },
            // y=3 墩柱 + 碎瓷散落
            { "##............##", ".#............#.", "..%.........%...", "..%.........%...", ".#............#.",
                "##............##" },
            // y=2 墩柱
            { "##............##", "##............##", "................", "................", "##............##",
                "##............##" },
            // y=1 桥台
            { "###..........###", "###..........###", "................", "................", "###..........###",
                "###..........###" },
            // y=0 墩基铺面
            { ".sss........sss.", ".sss........sss.", "................", "................", ".sss........sss.",
                ".sss........sss." } });

    /** ⑥ 露齿轮渣山 12×6×12：渣堆半埋巨型齿轮（积碳齿环 + 镀铜齿 'X' + 钢毂 'Z'）。 */
    public static final Outpost GEAR_SLAG_MOUND = new Outpost(
        "outpost_gear_slag_mound",
        12,
        6,
        12,
        new String[][] {
            // y=5 齿尖露出
            { "............", "............", "............", "......X.....", "............", "............",
                "............", "............", ".....X......", "............", "............", "............" },
            // y=4 齿轮上半（钢毂 'Z'）
            { "............", "............", "....GGGG....", "...G@@@@G...", "...G@..@G...", "...G@ZZ@G...",
                "...G@..@G...", "...G@@@@G...", "....GGGG....", "............", "............", "............" },
            // y=3 齿轮全露 + 渣裙
            { "............", ".....GG.....", "...GG##GG...", "..G@@..@@G..", ".G@......@G.", ".G@.X..X.@G.",
                ".G@......@G.", "..G@@..@@G..", "...GG##GG...", ".....GG.....", "............", "............" },
            // y=2 最宽渣裙
            { "....GGGG....", "..GG@@@@GG..", ".G@@....@@G.", ".G@......@G.", "G@........@G", "G@...GG...@G",
                "G@........@G", ".G@......@G.", ".G@@....@@G.", "..GG@@@@GG..", "....GGGG....", "............" },
            // y=1 渣体（碎瓷壳混渣）
            { "..%%%%%%%%..", ".GG%%%%%%GG.", ".G%%GGGG%%G.", ".%%GG..GG%%.", "%%G......G%%", "%%G..GG..G%%",
                "%%G......G%%", ".%%GG..GG%%.", ".G%%GGGG%%G.", ".GG%%%%%%GG.", "..%%%%%%%%..", "............" },
            // y=0 渣山基底
            { "GGGGGGGGGGGG", "GGGGGGGGGGGG", "GGGGGGGGGGGG", "GGGGGGGGGGGG", "GGGGGGGGGGGG", "GGGGGGGGGGGG",
                "GGGGGGGGGGGG", "GGGGGGGGGGGG", "GGGGGGGGGGGG", "GGGGGGGGGGGG", "GGGGGGGGGGGG", "GGGGGGGGGGGG" } });

    /** 6 中型变体（放置时均匀掷选）。 */
    public static final Outpost[] ALL = { WATCH_POST, BROKEN_AQUEDUCT, TOPPLED_BOILER, BRICK_KILN, COLLAPSED_TRUSS,
        GEAR_SLAG_MOUND };

    static {
        for (Outpost outpost : ALL) {
            if (outpost.layers.length != outpost.sizeY) {
                throw new IllegalStateException(
                    "[GTSR] prosperity outpost " + outpost.name
                        + ": layer count "
                        + outpost.layers.length
                        + " != sizeY "
                        + outpost.sizeY);
            }
            for (int y = 0; y < outpost.sizeY; y++) {
                final String[] rows = outpost.layers[y];
                if (rows.length != outpost.sizeZ) {
                    throw new IllegalStateException(
                        "[GTSR] prosperity outpost " + outpost.name
                            + ": layer "
                            + y
                            + " row count "
                            + rows.length
                            + " != sizeZ "
                            + outpost.sizeZ);
                }
                for (int z = 0; z < rows.length; z++) {
                    if (rows[z].length() != outpost.sizeX) {
                        throw new IllegalStateException(
                            "[GTSR] prosperity outpost " + outpost.name
                                + ": layer "
                                + y
                                + " row "
                                + z
                                + " length "
                                + rows[z].length()
                                + " != sizeX "
                                + outpost.sizeX);
                    }
                }
            }
        }
    }

    private ProsperityOutpostPlacer() {}

    /**
     * 向 {@link StructureRegistry} 登记 6 中型变体（dimension=PROSPERITY；footprint = max(sizeX,sizeZ)
     * 旋转安全口径，幂等）。placer 回调自包 {@link CityBlockResolver}（String 键→Block，含 GT5U
     * 两键 null 防御），flat ground（/gtsr structure 原点 y 即基面，CityVariants.registerVariants
     * 同款口径）。
     */
    public static void registerVariants() {
        if (registered) {
            return;
        }
        registered = true;
        for (final Outpost outpost : ALL) {
            StructureRegistry.register(
                new StructureRegistry.Entry(
                    outpost.name,
                    StructureRegistry.Dimension.PROSPERITY,
                    Math.max(outpost.sizeX, outpost.sizeZ),
                    Math.max(outpost.sizeX, outpost.sizeZ),
                    (sink, x, y, z, seed) -> place(
                        new StructureBuilder(new CityBlockResolver(sink)),
                        outpost,
                        x,
                        z,
                        CityVariants.rotationOf(seed),
                        CityVariants.MISSING_RATES[CityVariants.damageTier(seed)],
                        new Random(seed),
                        CityVariants.flatGround(y),
                        BlockSink.FLAG_DIRECT)));
        }
    }

    /**
     * populate 入口：掷频 1/{@link Config#prosperityOutpostChance}（0 = 禁用）→ 掷变体/朝向/损伤 →
     * 选点（<b>旋转后</b> footprint 收缩钳制在 chunk 内）→ 落点判定 → 放置。
     *
     * @return true = 本 chunk 已生成 outpost（编排器据此跳过残缺机器，互斥掷骰）
     */
    public static boolean placeAll(World world, long worldSeed, int cx, int cz, BlockSink sink) {
        final int chance = Config.prosperityOutpostChance;
        if (chance <= 0 || sink == null) {
            return false; // 0 = 禁用（plan S-A5 失败回退开关）
        }
        final Random r = new Random(GTSRWorldgenHash.chunkSeed(worldSeed, cx, cz) ^ SALT_OUTPOST);
        if (r.nextInt(chance) != 0) {
            return false;
        }
        final Outpost outpost = ALL[r.nextInt(ALL.length)];
        final long placeSeed = GTSRWorldgenHash.chunkSeed(worldSeed, cx, cz) ^ SALT_PLACE;
        final int rot = CityVariants.rotationOf(placeSeed);
        // footprint 收缩钳制（旋转后口径）：origin 使整机（含垫层）完全落在当前 chunk
        final int[] rotated = StructureBuilder.rotateSize(outpost.sizeX, outpost.sizeZ, rot);
        final int freeX = 16 - rotated[0];
        final int freeZ = 16 - rotated[1];
        if (freeX < 0 || freeZ < 0) {
            return false; // >16 格防御性跳过（≤16×16×12 契约下不应发生）
        }
        final int x = (cx << 4) + r.nextInt(freeX + 1);
        final int z = (cz << 4) + r.nextInt(freeZ + 1);
        // 落点判定：中心列自上而下找地表 + 锈变地表门（S-A1 连带放宽：四自然 top ∪ prosperitySurface）
        final int centerX = x + rotated[0] / 2;
        final int centerZ = z + rotated[1] / 2;
        final int surfaceY = findSurfaceY(world, centerX, centerZ);
        if (surfaceY < 20 || surfaceY > 200) {
            return false;
        }
        if (!isNaturalProsperityTop(world.getBlock(centerX, surfaceY, centerZ))) {
            return false;
        }
        place(
            new StructureBuilder(new CityBlockResolver(sink)),
            outpost,
            x,
            z,
            rot,
            CityVariants.MISSING_RATES[CityVariants.damageTier(placeSeed)],
            new Random(placeSeed),
            groundFn(worldSeed),
            BlockSink.FLAG_POPULATE);
        return true;
    }

    /** 每列落地 y = heightAt（高度红线同源，纯函数不读方块）。 */
    private static CityVariants.GroundFn groundFn(final long worldSeed) {
        return (x, z) -> ProsperityTerrainProfile.heightAt(worldSeed, x, z);
    }

    /**
     * 变体放置核心（世界生成 / /gtsr structure / 离线 OutpostTemplateCheck 三方共用；
     * CityVariants.place 同款语义）：基座层（y=0）恒放置；其余层按损伤档掷缺失；
     * '.'（y&gt;0）清空气；每列落地 y 由 ground 给出。签名与函数体零 Minecraft 依赖
     * （String 键 + GroundFn），JEP330 离线驱动可直接调用。
     */
    public static void place(StructureBuilder builder, Outpost outpost, int originX, int originZ, int rot,
        int missingRate, Random r, CityVariants.GroundFn ground, int flags) {
        for (int y = 0; y < outpost.sizeY; y++) {
            for (int dz = 0; dz < outpost.sizeZ; dz++) {
                for (int dx = 0; dx < outpost.sizeX; dx++) {
                    final char c = outpost.charAt(y, dx, dz);
                    if (c == ' ') {
                        continue; // 不触碰：地形让行
                    }
                    // 旋转后本地坐标 → 世界坐标
                    final int[] rd = StructureBuilder.rotateDelta(dx, dz, rot, outpost.sizeX, outpost.sizeZ);
                    final int wx = originX + rd[0];
                    final int wz = originZ + rd[1];
                    final int wy = ground.groundY(wx, wz) + 1 + y;
                    if (wy < 1 || wy > 255) {
                        continue;
                    }
                    if (c == '.') {
                        if (y > 0) {
                            builder.setBlock(wx, wy, wz, CityVariants.K_AIR, 0, flags); // 内腔清空
                        }
                        continue; // y=0 的 '.' = 地坪留白（地形让行）
                    }
                    if (y > 0 && r.nextInt(100) < missingRate) {
                        continue; // 损伤档缺失（垫层不缺失）
                    }
                    builder.setBlock(wx, wy, wz, CityVariants.blockKeyOf(c), CityVariants.metaOf(c), flags);
                }
            }
        }
    }

    /**
     * 锈变地表门（S-A1 连带放宽，dim78-fix-slice-A1-report §8）：四自然 top 方块族
     * （ProsperitySteppe/Forest/Wastes/SwampTop，A1 主体换装后自然区地表）∪ prosperitySurface
     * （城内冻结语义 + 旧 chunk 兼容）。Scatter/MachinePlacer 共用本谓词；public 供
     * tools/dim1/ReplaceSurfaceRuntimeCheck 冒烟断言替换后地表命中本门。
     */
    public static boolean isNaturalProsperityTop(Block block) {
        return block == BlocksGTSR.prosperitySurface || block == BlocksGTSR.prosperitySteppeTop
            || block == BlocksGTSR.prosperityForestTop
            || block == BlocksGTSR.prosperityWastesTop
            || block == BlocksGTSR.prosperitySwampTop;
    }

    /** 自上而下找地表（WorldGenRunawaySingularity.java:82-91 范式）；找不到返回 -1。 */
    private static int findSurfaceY(World world, int x, int z) {
        for (int y = 255; y > 0; y--) {
            final Block block = world.getBlock(x, y, z);
            if (block != null && block.getMaterial() != Material.air) {
                return y;
            }
        }
        return -1;
    }
}
