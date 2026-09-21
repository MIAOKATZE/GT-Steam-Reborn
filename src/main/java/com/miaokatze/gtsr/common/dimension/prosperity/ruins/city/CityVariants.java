package com.miaokatze.gtsr.common.dimension.prosperity.ruins.city;

import java.util.Random;

import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;
import com.miaokatze.gtsr.common.dimension.framework.structure.GTSRWorldgenHash;
import com.miaokatze.gtsr.common.dimension.framework.structure.StructureBuilder;
import com.miaokatze.gtsr.common.dimension.framework.structure.StructureRegistry;

/**
 * 古代城 28 变体库（dim1 S4b，plan §3.3 清单逐项落地；"蒸汽朋克废都"意象。
 * <b>P16-B3 起含 2 个申报边长 &gt;16 的城内巨构</b>，形状表在 {@link CityMegaVariants}，
 * 经 {@code CityVariants.ALL} 尾部汇入同一条登记/选型链）。
 * <p>
 * <b>纯数据 + 纯放置</b>：全部形状以字符层串表达（{@link RuinedMachineShapes 记号族} 超集），
 * 经 {@link StructureBuilder} → 注入 {@link BlockSink} 写出，String 方块键参与（零 Minecraft
 * 依赖，离线 RasterSink 驱动与确定性自检可无游戏运行时实例化；游戏侧键解析 =
 * {@code CityBlockResolver}，与 RuinedMachinePlacer.resolveBlock 同范式）。
 * <p>
 * 记号（02 §6.1 记号族超集）：{@code #}=锈壳0 {@code @}=积碳壳1 {@code %}=碎瓷壳2
 * {@code d}=轨枕0 {@code p}=管道1 {@code r}=铆接板2 {@code c}=烟囱残段3
 * {@code s}=锈石铺面(Surface5) {@code S}=原版石 {@code C}=圆石 {@code G}=沙砾
 * {@code B}=铁栏杆 {@code X}=GT5U 镀铜砖块(sBlockCasings1 meta10) {@code Z}=GT5U 固体钢机械外壳
 * (sBlockCasings2 meta0) {@code .}=清空为空气(y&gt;0) {@code 空格}=不触碰。全部落在
 * plan §3.3 材料红线内（BlockRuinedCasing 0-2 / BlockRuinDebris 0-3 / BlockProsperitySurface
 * 0-5 + 原版 stone/cobble/gravel/iron_bars + GT5U casing 白名单两键（S-A4，dim78-fix-and-dim79-redo
 * plan §4 S-A4：sBlockCasings1 meta10 / sBlockCasings2 meta0，逻辑键游戏侧
 * {@code CityBlockResolver} 直引，GT 未加载防御=null 不 put）；<b>无 TE/无箱子/无控制器/无战利品</b>）。
 * <p>
 * 损伤档（plan §3.3：×3 档缺失率 5%/20%/40%，由 plotSeed 哈希选档）：y&gt;0 层逐块掷缺失，
 * y=0 垫层不缺失（残骸被岁月吞没观感，RuinedMachinePlacer 同款纪律）；朝向 0/90/180/270 由
 * plotSeed 哈希驱动、{@link StructureBuilder#rotateDelta} 原语层完成（plan §3.2）。
 * <p>
 * 注册：{@link #registerVariants()} 把 28 型（26 基础 + 2 巨构）登记进 {@link StructureRegistry}
 * （dimension=PROSPERITY，注册名=基础名，S5 /gtsr structure 补全同源；幂等）。
 * 形状尺寸静态校验 fail fast（RuinedMachineShapes 同款）。
 */
public final class CityVariants {

    /** 落地高度契约：城市一切落地 y 由该纯函数给出（游戏侧 = ProsperityTerrainProfile.heightAt）。 */
    public interface GroundFn {

        int groundY(int x, int z);
    }

    /** 单个变体：注册名 + 类别 + footprint (sizeX×sizeZ) + 层数 sizeY + 自上而下形状串。 */
    public static final class Variant {

        public final String name;
        public final String category;
        public final int sizeX;
        public final int sizeY;
        public final int sizeZ;
        /** layers[0] = 顶层；layers[layer] 为该层 sizeZ 行、每行 sizeX 字符。 */
        final String[][] layers;

        Variant(String name, String category, int sizeX, int sizeY, int sizeZ, String[][] layers) {
            this.name = name;
            this.category = category;
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
            this.layers = layers;
        }

        /** 原始（未旋转）本地坐标取字符（y 世界向上 0..sizeY-1）。 */
        char charAt(int y, int dx, int dz) {
            return this.layers[this.sizeY - 1 - y][dz].charAt(dx);
        }
    }

    // —— 方块键（String 契约键，游戏侧 CityBlockResolver 解析；编排器街道段直接引用）——
    public static final String K_CASING = "gtsr:RuinedCasing";
    public static final String K_DEBRIS = "gtsr:RuinDebris";
    public static final String K_SURFACE = "gtsr:ProsperitySurface";
    public static final String K_STONE = "minecraft:stone";
    public static final String K_COBBLE = "minecraft:cobblestone";
    public static final String K_GRAVEL = "minecraft:gravel";
    public static final String K_BARS = "minecraft:iron_bars";
    /** GT5U 镀铜砖块（逻辑键，非注册名；游戏侧 = GregTechAPI.sBlockCasings1 meta10，S-A4）。 */
    public static final String K_GT_BRONZE = "gt5u:CasingBronzePlated";
    /** GT5U 固体钢机械外壳（逻辑键，非注册名；游戏侧 = GregTechAPI.sBlockCasings2 meta0，S-A4）。 */
    public static final String K_GT_STEEL = "gt5u:CasingSolidSteel";

    /** char → 方块键（'.'/' ' 返回 null）。 */
    public static String blockKeyOf(char c) {
        switch (c) {
            case '#':
            case '@':
            case '%':
                return K_CASING;
            case 'd':
            case 'p':
            case 'r':
            case 'c':
                return K_DEBRIS;
            case 's':
                return K_SURFACE;
            case 'S':
                return K_STONE;
            case 'C':
                return K_COBBLE;
            case 'G':
                return K_GRAVEL;
            case 'B':
                return K_BARS;
            case 'X':
                return K_GT_BRONZE;
            case 'Z':
                return K_GT_STEEL;
            default:
                return null;
        }
    }

    /** char → meta（与 {@link #blockKeyOf(char)} 配对）。 */
    public static int metaOf(char c) {
        switch (c) {
            case '@':
                return 1;
            case '%':
                return 2;
            case 'd':
                return 0;
            case 'p':
                return 1;
            case 'r':
                return 2;
            case 'c':
                return 3;
            case 's':
                return 5; // 锈石铺面（02 §1.1 meta 表）
            case 'X':
                return 10; // GT5U sBlockCasings1 meta10 = Bronze Plated Bricks（交叉证见切片报告）
            case 'Z':
                return 0; // GT5U sBlockCasings2 meta0 = Solid Steel Machine Casing
            default:
                return 0;
        }
    }

    // —— 共用行模板（须在 ALL 之前声明；同层共享同一 String[] 引用，省量且不可变使用）——
    private static final String[] SLAB5 = slab(5);
    private static final String[] RING5 = { "#####", "#...#", "#...#", "#...#", "#####" };
    private static final String[] RING7 = { "#######", "#.....#", "#.....#", "#.....#", "#.....#", "#.....#",
        "#######" };
    private static final String[] SLAB7 = slab(7);
    private static final String[] GEAR_RING = { "#@#@#@#", "@.....@", "#.....#", "@.....@", "#.....#", "@.....@",
        "#@#@#@#" };
    private static final String[] SOOT_RING = { "#######", "@#...#@", "#.....#", "#.....#", "#.....@", "@#...@#",
        "#######" };
    private static final String[] WALLS11_8 = { "###########", "#.........#", "#.........#", "#.........#",
        "#.........#", "#.........#", "#.........#", "###########" };
    private static final String[] SLAB11_8 = slab(11, 8);
    private static final String[] SLAB7_6 = slab(7, 6);
    private static final String[] SLAB13_7 = slab(13, 7);
    private static final String[] WALLS13_7 = { "#############", "#...........#", "#...........#", "#...........#",
        "#...........#", "#...........#", "#############" };
    private static final String[] SLAB6 = slab(6, 6);

    private static String[] slab(int w) {
        final String[] rows = new String[w];
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < w; i++) {
            sb.append('s');
        }
        for (int i = 0; i < w; i++) {
            rows[i] = sb.toString();
        }
        return rows;
    }

    /**
     * 28 变体（plan §3.3 清单序：塔 6 / 厂房 6 / 基础设施 5 / 民用 5 / 小件 4 +
     * <b>P16-B3 巨构 2（表尾，形状与静态契约见 {@link CityMegaVariants}）</b>）。
     * 巨构<b>只</b>经 {@code CityPlan} 的工业环/边缘环 district 池进入 {@code plotVariant}
     * 哈希选型（低空不顶穿街带、大道全高不占、材料零放宽——判据腿在 CityShapeCheck E 组）。
     */
    public static final Variant[] ALL = {
        // ══ 塔类（6）——垂直地标，天际线骨架 ══
        new Variant(
            "watch_tower",
            "tower",
            5,
            12,
            5,
            new String[][] {
                // y=11 环形胸墙断口（垛口交替缺失）
                { ".#.#.", "#...#", "#...#", "#...#", ".#.#." }, RING5, RING5, RING5, RING5,
                // y=6 南墙门洞
                { "#####", "#...#", "#...#", "#...#", "##.##" }, RING5, RING5, RING5,
                // y=2 内壁铆接板残带
                { "#####", "#.r.#", "#...#", "#.r.#", "#####" },
                // y=1 井基层（'.'=梯井中空）
                { "sssss", "s...s", "s...s", "s...s", "sssss" }, SLAB5 }),
        new Variant(
            "gear_tower",
            "tower",
            7,
            16,
            7,
            new String[][] {
                // y=15 顶部齿轮半脱落（残环）
                { ".#####.", "#....##", "#.....#", "##....#", "#.....#", "##...##", "#####.." }, RING7, RING7,
                // y=12..11 齿轮浮雕带（积碳壳拼齿环）
                GEAR_RING, RING7, RING7, RING7, RING7,
                // y=7 积碳咬斑环
                SOOT_RING, RING7, RING7, RING7,
                // y=3 内齿轮毂浮雕
                { "#######", "#..@..#", "#.@.@.#", "#..@..#", "#.....#", "#.....#", "#######" }, RING7, RING7, SLAB7 }),
        chimneyStack(), waterTower(), coolingTower(), clockTower(),
        // ══ 厂房类（6）——工业环主体 ══
        boilerHouse(), pumpHouse(), forgeHall(), engineRoom(), gasHolder(),
        new Variant(
            "pressure_tank_row",
            "industry",
            12,
            4,
            4,
            new String[][] {
                // y=3 卧罐顶弧（3-5 罐串联取 2 罐段）
                { "............", ".rrrr.rrrrr.", ".rrrr.rrrr..", "............" },
                // y=2 罐身
                { "............", ".rrrr.rrrrr.", ".rrrr.rrrrr.", "............" },
                // y=1 罐底 + 管架
                { "............", ".rrrr.rrrrr.", ".rrrr.rrrrr.", "............" },
                // y=0 管架腿 + 平台
                { "p..s..p..s..", "ssssssssssss", "ssssssssssss", "p..s..p..s.." } }),
        // ══ 基础设施类（5）——跨地块/跨街区连接件 ══
        brokenBridge(), viaduct(), railPlatform(), craneRuin(), canalGate(),
        // ══ 民用类（5）——核心区氛围 ══
        domeHall(), marketColonnade(), manorRuin(), new Variant(
            "fountain_basin",
            "civic",
            5,
            2,
            5,
            new String[][] {
                // y=1 残池沿 + 中央残柱（干涸）
                { ".##.#", "#...#", "#.@.#", "#..##", ".##.#" },
                // y=0 池底（沙砾淤积）
                { "sssss", "sGGGs", "sGGGs", "sGGGs", "sssss" } }),
        tramDepot(),
        // ══ 废墟小件（4）——空地填充/边缘环密度件 ══
        new Variant(
            "fallen_arch",
            "rubble",
            7,
            4,
            4,
            new String[][] {
                // y=3 塌落拱环（斜倚段）
                { "..##...", "..##...", ".......", "......." },
                // y=2 断拱斜面
                { ".##....", ".#.....", "..##...", "......." },
                // y=1 拱脚残段
                { "##.....", "#......", ".##....", "...##.." },
                // y=0 基座 + 散块
                { "#s.....", "#s.....", "##s....", "....##." } }),
        new Variant(
            "broken_pillars",
            "rubble",
            6,
            3,
            6,
            new String[][] {
                // y=2 高柱残梗（3 根）
                { "..#...", "......", ".#....", "......", "....#.", "......" },
                // y=1 中柱（4 根）
                { "..#...", "......", ".#....", "......", "....#.", "..#..." },
                // y=0 柱础 + 铺面
                { "ss#sss", "s#ssss", "ssss#s", "ssssss", "#sss#s", "ss#sss" } }),
        new Variant(
            "machine_plinth",
            "rubble",
            4,
            3,
            3,
            new String[][] {
                // y=2 地脚螺栓孔（'.'=螺栓孔留白："机器已被拆走"）
                { "#..#", ".#.#", "##.#" },
                // y=1 基座箱
                { "####", "#..#", "####" },
                // y=0 垫层
                { "ssss", "ssss", "ssss" } }),
        new Variant(
            "slag_heap",
            "rubble",
            5,
            3,
            5,
            new String[][] {
                // y=2 锥顶散渣
                { "..G..", ".....", ".....", ".....", "..G.." },
                // y=1 锥腰
                { ".GGG.", "GGGGG", "GGGGG", "GGGGG", ".GGG." },
                // y=0 锥底 + 铆接板瓦砾
                { "GGGGG", "GGrGG", "GGGGG", "GrGGG", "GGGrG" } }),
        // ══ P16-B3 城内巨构（2）——申报边长 >16，跨 chunk 走 CitySliceSink 既有切片协议 ══
        CityMegaVariants.GREAT_FORGE, CityMegaVariants.TITAN_GEARWORKS, };

    static {
        for (Variant v : ALL) {
            if (v.layers.length != v.sizeY) {
                throw new IllegalStateException(
                    "[GTSR] city variant " + v.name + ": layer count " + v.layers.length + " != sizeY " + v.sizeY);
            }
            for (int y = 0; y < v.sizeY; y++) {
                final String[] rows = v.layers[y];
                if (rows.length != v.sizeZ) {
                    throw new IllegalStateException(
                        "[GTSR] city variant " + v.name
                            + ": layer "
                            + y
                            + " row count "
                            + rows.length
                            + " != sizeZ "
                            + v.sizeZ);
                }
                for (int z = 0; z < rows.length; z++) {
                    if (rows[z].length() != v.sizeX) {
                        throw new IllegalStateException(
                            "[GTSR] city variant " + v.name
                                + ": layer "
                                + y
                                + " row "
                                + z
                                + " length "
                                + rows[z].length()
                                + " != sizeX "
                                + v.sizeX);
                    }
                }
            }
        }
    }

    // ═══ 程序化形状（径向轮廓字符串手工展开低效，静态构造）═══

    /**
     * 全体变体<b>申报边长上界</b>（max(sizeX,sizeZ)；= {@link CityMegaVariants} 的巨构边长）。
     * <p>
     * 它是 {@code CityPlan} 三处几何余量（渲染 bbox 外扩 / {@code contentReachBlocks} 内容余量 /
     * 缓冲窗 chunk 裕量）的<b>唯一输入</b>——余量口径从"城内最大 footprint 16"的写死假设
     * 改为随名册派生（P16-B3，plan §1 G11；三处各自的公式见 {@code CityPlan} 常量区）。
     * 由 {@link #ALL} 现算，名册是唯一真值，不在别处第二抄。
     */
    public static final int MAX_SIDE = maxDeclaredSide();

    private static int maxDeclaredSide() {
        int m = 0;
        for (final Variant v : ALL) {
            m = Math.max(m, Math.max(v.sizeX, v.sizeZ));
        }
        return m;
    }

    /** chimney_stack 8×14×8：2-4 根方形烟囱束总成（4 束 3×3，高 14/12/10/8 错落，顶部破口）。 */
    private static Variant chimneyStack() {
        final int w = 8, h = 14, d = 8;
        final int[][] pos = { { 0, 0 }, { 5, 0 }, { 0, 5 }, { 5, 5 } };
        final int[] hs = { 14, 12, 10, 8 };
        final char[][][] g = grid(w, h, d);
        for (int i = 0; i < 4; i++) {
            final int px = pos[i][0], pz = pos[i][1], ch = hs[i];
            for (int y = 0; y < ch; y++) {
                for (int dz = 0; dz < 3; dz++) {
                    for (int dx = 0; dx < 3; dx++) {
                        final int gx = px + dx, gz = pz + dz;
                        char c;
                        if (dx == 1 && dz == 1) {
                            c = y < ch - 1 ? '@' : ' '; // 烟道积烟，顶部破口
                        } else if (y >= ch - 2 && (dx * 7 + dz * 13 + y * 5 + i * 11) % 3 == 0) {
                            c = ' '; // 顶部锯齿破口
                        } else {
                            c = '#';
                        }
                        g[y][gz][gx] = c;
                    }
                }
            }
        }
        for (int z = 0; z < d; z++) {
            for (int x = 0; x < w; x++) {
                if (g[0][z][x] == ' ') {
                    g[0][z][x] = '%'; // 碎瓷壳联箱底座
                }
            }
        }
        return new Variant("chimney_stack", "tower", w, h, d, toLayers(g, w, h, d));
    }

    /** water_tower 5×10×5：四腿钢架 + 顶置积碳壳罐体，罐顶破口。 */
    private static Variant waterTower() {
        return new Variant(
            "water_tower",
            "tower",
            5,
            10,
            5,
            new String[][] {
                // y=9 罐顶破口
                { ".@@@.", "@@.@@", ".@@@.", "@.@@@", ".@@@." },
                // y=8..7 罐体（积碳壳围合）
                { "@@@@@", "@...@", "@...@", "@...@", "@@@@@" }, { "@@@@@", "@...@", "@...@", "@...@", "@@@@@" },
                // y=6..4 钢架腿 + 横撑
                { "#...#", ".....", ".....", ".....", "#...#" }, { "#...#", ".....", "..r..", ".....", "#...#" },
                { "#...#", ".....", ".....", ".....", "#...#" },
                // y=3..1 腿
                { "#...#", ".....", ".....", ".....", "#...#" }, { "#...#", ".....", ".....", ".....", "#...#" },
                { "#...#", ".....", ".....", ".....", "#...#" }, SLAB5 });
    }

    /** cooling_tower 9×12×9：双曲线收分轮廓（逐层收缩），腰部大洞。 */
    private static Variant coolingTower() {
        final int w = 9, h = 12, d = 9, cx = 4, cz = 4;
        final char[][][] g = grid(w, h, d);
        for (int y = 0; y < h; y++) {
            final double t = y / (double) (h - 1);
            final double r = 3.6D - 1.7D * Math.sin(Math.PI * t); // 双曲线：底 3.6 → 腰 1.9 → 顶 3.6
            for (int dz = -4; dz <= 4; dz++) {
                for (int dx = -4; dx <= 4; dx++) {
                    final double dist = Math.sqrt(dx * dx + dz * dz);
                    if (dist <= r && dist > r - 1.0D) {
                        // 腰部大洞（+X 侧扇区 y4..7 咬穿）
                        if (y >= 4 && y <= 7 && dx >= 1 && Math.abs(dz) <= 1) {
                            continue;
                        }
                        // 顶部半塌
                        if (y == h - 1 && (dx + dz & 1) == 0) {
                            continue;
                        }
                        g[y][cz + dz][cx + dx] = '#';
                    }
                }
            }
        }
        return new Variant("cooling_tower", "tower", w, h, d, toLayers(g, w, h, d));
    }

    /** clock_tower 5×14×5：四立面拱窗，顶钟位缺失（"指针"=铁栏杆残梗）。 */
    private static Variant clockTower() {
        final String[] windowRing = { "#####", "#.B.#", "#...#", "#.B.#", "#####" };
        final String[] archRing = { "#####", "#.#.#", "#...#", "#.#.#", "#####" };
        return new Variant(
            "clock_tower",
            "tower",
            5,
            14,
            5,
            new String[][] {
                // y=13 顶钟位缺失：铁栏杆"指针"残梗
                { ".B.B.", "B...B", ".....", "B...B", ".B.B." }, windowRing, windowRing, windowRing, RING5, RING5,
                RING5, RING5, archRing, RING5, RING5, RING5, RING5, SLAB5 });
    }

    /** boiler_house 11×7×8：山墙厂房 + 屋面一排烟囱穿孔。 */
    private static Variant boilerHouse() {
        return new Variant(
            "boiler_house",
            "industry",
            11,
            7,
            8,
            new String[][] {
                // y=6 屋脊 + 烟囱穿孔（烟囱束=钢外壳 'Z'，S-A4 金属质感层；承重 '#' 不动）
                { "...........", "...........", "...........", "###Z####Z##", "###########", "...........",
                    "...........", "..........." },
                // y=5 屋坡 + 天窗破洞（檐口镀铜砖带 'X'）
                { "...........", "...........", "#XXXXXXXXX#", "####.######", "######.####", "###########",
                    "...........", "..........." },
                // y=4 檐口环
                WALLS11_8,
                // y=3 高窗带
                { "###.###.###", "#..B...B..#", "#.........#", "#..B...B..#", "#.........#", "#..B...B..#",
                    "###.###.###", "###########" },
                // y=2 大门南墙
                { "###########", "#.........#", "#.........#", "#.........#", "#.........#", "#.........#",
                    "###########", "####..#####" },
                // y=1 炉座积碳带（炉座护壁墩=镀铜砖 'X'）
                { "###########", "#@@#...#@@#", "#@@#...#@@#", "#@@#...#@@#", "#@@#...#@@#", "#@@#...#@@#",
                    "#XX#####XX#", "###########" },
                SLAB11_8 });
    }

    /** pump_house 7×5×6：矮胖小屋，墙侧突出断管道。 */
    private static Variant pumpHouse() {
        return new Variant(
            "pump_house",
            "industry",
            7,
            5,
            6,
            new String[][] {
                // y=4 屋面板 + 塌洞
                { "CCCCCCC", "CCCCCCC", "CCC.CCC", "CCCCCCC", "CCCCCCC", "CCCCCCC" },
                // y=3 墙 + 断管道残梗
                { "#######", "#....p#", "#.....#", "#....p#", "#.....#", "#######" },
                // y=2 墙
                { "#######", "#.....#", "#.....#", "#.....#", "#.....#", "#######" },
                // y=1 泵心
                { "#######", "#..@..#", "#.@.@.#", "#..@..#", "#.....#", "#######" }, SLAB7_6 });
    }

    /** forge_hall 13×7×7：大门山墙 + 室内炉座残基。 */
    private static Variant forgeHall() {
        return new Variant(
            "forge_hall",
            "industry",
            13,
            7,
            7,
            new String[][] {
                // y=6 屋脊（脊段=钢外壳 'Z'，S-A4 金属质感层）
                { ".............", ".............", ".............", "###ZZ#####ZZ#", ".............", ".............",
                    "............." },
                // y=5 屋坡 + 塌洞（檐口镀铜砖带 'X'）
                { ".............", ".............", "#XXXXXXXXXXX#", "##...####...#", "#############", ".............",
                    "............." },
                WALLS13_7,
                // y=3 大门山墙（北墙门洞）+ 高窗
                { "#####.#######", "#....B......#", "#...........#", "#...........#", "#......B....#", "#...........#",
                    "#############" },
                // y=2 炉座残基（炉座垫层=镀铜砖 'X'）
                { "#############", "#@@.......@@#", "#@@..@@@..@@#", "#@@..@@@..@@#", "#@@.......@@#", "#XX.......XX#",
                    "#############" },
                WALLS13_7, SLAB13_7 });
    }

    /** engine_room 9×6×7：半地下室感（满铺锈石地坪）+ 天窗带。 */
    private static Variant engineRoom() {
        final String[] ring9x7 = { "#########", "#.......#", "#.......#", "#.......#", "#.......#", "#.......#",
            "#########" };
        // 腰带环（镀铜砖 'X' 整环，S-A4 金属质感层；承重 '#' 环保持原样）
        final String[] bronzeRing9x7 = { "XXXXXXXXX", "X.......X", "X.......X", "X.......X", "X.......X", "X.......X",
            "XXXXXXXXX" };
        return new Variant(
            "engine_room",
            "industry",
            9,
            6,
            7,
            new String[][] {
                // y=5 屋面 + 天窗带（天窗框=钢外壳 'Z'）
                { ".........", "sssssssss", "sssZZZsss", "sssssssss", "sssZZZsss", "sssssssss", "........." }, ring9x7,
                bronzeRing9x7,
                // y=3 高窗
                { "##B###B##", "#.......#", "#.......#", "#.......#", "#.......#", "#.......#", "##B###B##" },
                // y=1 汽机基座
                { "#########", "#..@@@..#", "#..@.@..#", "#..@@@..#", "#.......#", "#...p...#", "#########" },
                // y=0 下沉地坪
                slab(9, 7) });
    }

    /** gas_holder 7×9×7：铁栏杆导架 + 积碳壳外皮立罐，顶钟下陷。 */
    private static Variant gasHolder() {
        final String[] shellRing = { "B.....B", "B@@@@@B", "B@...@B", "B@...@B", "B@...@B", "B@...@B", "B@@@@@B" };
        // 镀铜环带（罐身一圈镀铜砖 'X'，S-A4 金属质感层）
        final String[] bronzeShellRing = { "B.....B", "BXXXXXB", "BX...XB", "BX...XB", "BX...XB", "BX...XB",
            "BXXXXXB" };
        return new Variant(
            "gas_holder",
            "industry",
            7,
            9,
            7,
            new String[][] {
                // y=8 顶钟下陷破口
                { ".@@.@@.", ".@...@.", ".@.....", ".@...@.", ".....@.", ".@...@.", ".@@.@@." },
                // y=7 钟顶（钟沿=镀铜砖 'X'）
                { "B.....B", "B@XXX@B", "B@...@B", "B@...@B", "B@...@B", "B@XXX@B", "B@@@@@B" },
                // y=6 导架裸段（钟体下陷）
                { "B.....B", "B.....B", "B.....B", "B.....B", "B.....B", "B.....B", "B.....B" }, shellRing, shellRing,
                bronzeShellRing, shellRing,
                // y=2 罐底阀管（阀管=钢外壳 'Z'）
                { "B.....B", "B@ZZZ@B", "B@...@B", "B@.@.@B", "B@...@B", "B@ZZZ@B", "B@@@@@B" }, SLAB7 });
    }

    /** broken_bridge 16×5×5：跨街拱桥，中段 2 孔坍塌。 */
    private static Variant brokenBridge() {
        return new Variant(
            "broken_bridge",
            "infra",
            16,
            5,
            5,
            new String[][] {
                // y=4 桥面（中段坍塌断口）
                { "................", "................", "######....######", "................", "................" },
                // y=3 桥侧栏
                { "................", "#######..#######", "################", "#######..#######", "................" },
                // y=2 拱圈（中墩已塌）
                { "................", "................", "..##........##..", "................", "................" },
                // y=1 拱墩
                { "................", "..###......###..", "..###......###..", "..###......###..", "................" },
                // y=0 桥基
                { "................", "..ss........ss..", "..ss........ss..", "..ss........ss..",
                    "................" } });
    }

    /** viaduct 16×3×5：墩柱 + 管槽直线段，端头断口。 */
    private static Variant viaduct() {
        return new Variant(
            "viaduct",
            "infra",
            16,
            3,
            5,
            new String[][] {
                // y=2 管槽面（端头断口）
                { "................", "................", "###############.", "................", "................" },
                // y=1 墩柱 + 槽体
                { "................", ".##....##....##.", "################", ".##....##....##.", "................" },
                // y=0 墩基
                { "................", ".ss....ss....ss.", ".ss....ss....ss.", ".ss....ss....ss.",
                    "................" } });
    }

    /** rail_platform 12×4×6：矮站台 + 雨棚柱列 + 断轨枕排。 */
    private static Variant railPlatform() {
        return new Variant(
            "rail_platform",
            "infra",
            12,
            4,
            6,
            new String[][] {
                // y=3 雨棚残板
                { ".ssss.......", ".ssss.......", ".ssss.......", "............", "............", "............" },
                // y=2 棚柱列
                { "....#.......", "....#.......", "....#.......", "....#.......", "............", "............" },
                // y=1 站台
                { "............", "............", "ssssssssssss", "ssssssssssss", "ssssssssssss", "............" },
                // y=0 断轨枕排 + 基面
                { "d..d..d..d..", ".d..d..d..d.", "ssssssssssss", "ssssssssssss", "ssssssssssss", "............" } });
    }

    /** crane_ruin 6×9×6：A 字臂塔 + 吊缆垂落（铁栏杆线）。 */
    private static Variant craneRuin() {
        final String[] mast = { "......", "......", "..##..", "..##..", "......", "......" };
        final String[] legs = { "#....#", "#....#", "..##..", "..##..", "#....#", "#....#" };
        return new Variant(
            "crane_ruin",
            "infra",
            6,
            9,
            6,
            new String[][] {
                // y=8 臂尖 + 吊钩缆
                { "......", "......", "..##B.", "......", "......", "......" },
                // y=7 臂杆
                { "......", "......", "..####", "......", "......", "......" }, mast, mast,
                // y=5..4 塔身收腿
                legs, legs,
                // y=3..2 支腿张开
                legs, legs,
                // y=1 支腿（y=0 垫层在下方）
                SLAB6 });
    }

    /** canal_gate 12×4×5：两道闸墙 + 淤平闸室。 */
    private static Variant canalGate() {
        return new Variant(
            "canal_gate",
            "infra",
            12,
            4,
            5,
            new String[][] {
                // y=3 闸墙顶（残口）
                { "..##....##..", "..##....##..", "..##....##..", "..##....##..", "..#.....##.." },
                // y=2 闸槽（积碳）
                { "..##....##..", "..#@....#@..", "..##....##..", "..##....##..", "..##....##.." },
                // y=1 淤平闸室
                { "..##GGGG##..", "..##GGGG##..", "..##GGGG##..", "..##GGGG##..", "..##GGGG##.." },
                // y=0 闸底
                slab(12, 5) });
    }

    /** dome_hall 11×9×11：鼓座 + 穹顶塌陷 1/3（NW 象限），内部大厅空旷。 */
    private static Variant domeHall() {
        final int w = 11, h = 9, d = 11, c = 5;
        final char[][][] g = grid(w, h, d);
        for (int y = 1; y <= 3; y++) { // 鼓座环 + 空旷大厅
            for (int z = 0; z < d; z++) {
                for (int x = 0; x < w; x++) {
                    if (x == 0 || x == w - 1 || z == 0 || z == d - 1) {
                        if (y <= 2 && (x == c || z == c)) {
                            g[y][z][x] = '.'; // 门洞
                        } else if (y == 2 && (x == 2 || x == 8 || z == 2 || z == 8)) {
                            g[y][z][x] = '.'; // 高窗
                        } else {
                            g[y][z][x] = '#';
                        }
                    } else {
                        g[y][z][x] = '.';
                    }
                }
            }
        }
        for (int y = 4; y < h; y++) { // 方step穹顶
            final int ring = h - 1 - y; // y4→4 .. y8→0
            for (int dz = -ring; dz <= ring; dz++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    if (dx < 0 && dz < 0 && (y >= 6 || (dx + dz) % 2 == 0)) {
                        continue; // NW 象限塌陷 1/3
                    }
                    g[y][c + dz][c + dx] = '#';
                }
            }
        }
        for (int z = 0; z < d; z++) {
            for (int x = 0; x < w; x++) {
                if (g[0][z][x] == ' ') {
                    g[0][z][x] = 's';
                }
            }
        }
        return new Variant("dome_hall", "civic", w, h, d, toLayers(g, w, h, d));
    }

    /** market_colonnade 13×4×5：双排柱列，顶棚残存一半。 */
    private static Variant marketColonnade() {
        final String[] colonnade = { ".#..#..#..#..", ".............", ".............", ".............",
            ".#..#..#..#.." };
        return new Variant(
            "market_colonnade",
            "civic",
            13,
            4,
            5,
            new String[][] {
                // y=3 顶棚残半（z0-2 侧）
                { "##.##########", "#############", "####.########", ".............", "............." }, colonnade,
                colonnade,
                // y=0 柱础 + 铺面
                { ".#..#..#..#..", "sssssssssssss", "sssssssssssss", "sssssssssssss", ".#..#..#..#.." } });
    }

    /** manor_ruin 9×5×5：L 形残墙 + 窗洞阵。 */
    private static Variant manorRuin() {
        return new Variant(
            "manor_ruin",
            "civic",
            9,
            5,
            5,
            new String[][] {
                // y=4 残墙顶
                { "##.###.##", ".#.......", ".........", ".........", ".#......." },
                // y=3 窗洞阵
                { "#.#.#.#.#", ".#.......", ".........", ".........", ".#..#...." },
                { "##.###.##", ".#.......", ".........", ".........", ".#......." },
                // y=1 墙裙
                { "#########", ".#.......", ".........", ".........", ".#......." },
                // y=0 L 形地坪
                { "sssssssss", "ss.......", "ss.......", "ss.......", "ssss....." } });
    }

    /** tram_depot 9×5×5：三开间拱棚，中开间全塌。 */
    private static Variant tramDepot() {
        return new Variant(
            "tram_depot",
            "civic",
            9,
            5,
            5,
            new String[][] {
                // y=4 棚顶（中开间塌失）
                { "sss...sss", "sss...sss", "sss...sss", "sss...sss", "sss...sss" },
                // y=3 拱带
                { "###...###", "#.#...#.#", "###...###", "#.#...#.#", "###...###" },
                // y=2 柱列
                { ".#.....#.", ".#.....#.", ".#.....#.", ".#.....#.", ".#.....#." },
                // y=1 轨枕残线
                { ".#.....#.", "....d....", ".#.....#.", "...d.....", ".#.....#." },
                // y=0 地坪
                { "#########", "sssssssss", "sssssssss", "sssssssss", "#########" } });
    }

    // —— 程序化/模板工具（行模板集中在 ALL 之前声明）——

    private static String[] slab(int w, int d) {
        final String[] rows = new String[d];
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < w; i++) {
            sb.append('s');
        }
        final String row = sb.toString();
        for (int i = 0; i < d; i++) {
            rows[i] = row;
        }
        return rows;
    }

    private static char[][][] grid(int w, int h, int d) {
        final char[][][] g = new char[h][d][w];
        for (int y = 0; y < h; y++) {
            for (int z = 0; z < d; z++) {
                java.util.Arrays.fill(g[y][z], ' ');
            }
        }
        return g;
    }

    private static String[][] toLayers(char[][][] g, int w, int h, int d) {
        // g[y][z][x]（世界向上 y）→ layers[0]=顶层（与字符串形状同约定）
        final String[][] layers = new String[h][];
        for (int y = 0; y < h; y++) {
            final int ly = h - 1 - y;
            final String[] rows = new String[d];
            for (int z = 0; z < d; z++) {
                rows[z] = new String(g[ly][z]);
            }
            layers[y] = rows;
        }
        return layers;
    }

    // ═══ 损伤/朝向/放置 ═══

    /** 损伤档缺失率（plan §3.3：轻 5% / 中 20% / 重 40%）。 */
    public static final int[] MISSING_RATES = { 5, 20, 40 };

    private static final long SALT_TIER = 0xDA66ACL; // "damage" 助记
    private static final long SALT_ROT = 0x07E7L; // rotation 助记

    /** 损伤档（0..2，plotSeed 哈希选档——世界生成与 /gtsr structure 同函数同结果）。 */
    public static int damageTier(long plotSeed) {
        // mix 已规范非负（0x7FFF... 截断，与 CityPlanner.mix 同口径）；% 3 得 0..2。
        // 历史：mix 未规范时负 plotSeed 使 %3 返回 -2/-1，MISSING_RATES 负索引崩世界（2026-09-18 实测）。
        return (int) (mix(plotSeed, SALT_TIER) % 3);
    }

    /** 朝向（0..3 = 0/90/180/270°，plan §3.2）。 */
    public static int rotationOf(long plotSeed) {
        return (int) (mix(plotSeed, SALT_ROT) & 3);
    }

    /**
     * plot 级 splitmix64 混哈希（非负长整）——<b>P3 起算法体在 {@link GTSRWorldgenHash}</b>，
     * 本方法只保留本类的<b>专属盐乘子</b>实参（{@link GTSRWorldgenHash#CITY_PLOT_SALT_MUL}）。
     * <p>
     * 与 {@code CityPlanner.mix} <b>不同值</b>（那边乘子是 0xD1B5…），二者对同一入参输出不同，
     * 故不合并；详见 {@code CityPlanner.mix} 的差异登记与
     * {@code tools/dim1/SurfaceYParityCheck} 的 {@code city.variants_mix} 站点逐位对拍。
     * 本方法被 {@link #damageTier(long)}/{@link #rotationOf(long)} 消费，其取值口径逐位不变，
     * 因而既有 26 个城变体的损伤档/朝向选择与改造前完全一致；P16-B3 的 2 个巨构同走这两个函数、
     * 同一口径（无新随机源，plan §1 G11）。
     */
    private static long mix(long seed, long salt) {
        return GTSRWorldgenHash.saltRoutedHash(seed, salt, GTSRWorldgenHash.CITY_PLOT_SALT_MUL);
    }

    /**
     * 变体放置核心（世界生成 plot 渲染与 /gtsr structure 直写共用）。
     * 基座层（y=0）恒放置；其余层按损伤档掷缺失；'.'（y&gt;0）清空气；每列落地 y 由
     * {@code ground.groundY(wx,wz)} 纯函数给出（高度红线，禁止读方块）。
     */
    public static void place(Variant v, BlockSink sink, int originX, int originZ, GroundFn ground, long plotSeed,
        int flags) {
        final int rot = rotationOf(plotSeed);
        final int missingRate = MISSING_RATES[damageTier(plotSeed)];
        final int[] rs = StructureBuilder.rotateSize(v.sizeX, v.sizeZ, rot);
        final Random r = new Random(plotSeed ^ SALT_TIER);
        for (int y = 0; y < v.sizeY; y++) {
            for (int dz = 0; dz < v.sizeZ; dz++) {
                for (int dx = 0; dx < v.sizeX; dx++) {
                    final char c = v.charAt(y, dx, dz);
                    if (c == ' ') {
                        continue; // 不触碰
                    }
                    // 旋转后本地坐标 → 世界坐标
                    final int[] rd = StructureBuilder.rotateDelta(dx, dz, rot, v.sizeX, v.sizeZ);
                    final int wx = originX + rd[0];
                    final int wz = originZ + rd[1];
                    final int wy = ground.groundY(wx, wz) + 1 + y;
                    if (wy < 1 || wy > 255) {
                        continue;
                    }
                    if (c == '.') {
                        if (y > 0) {
                            sink.setBlock(wx, wy, wz, K_AIR, 0, flags); // 内腔清空
                        }
                        continue; // y=0 的 '.' = 地坪留白（地形让行）
                    }
                    if (y > 0 && r.nextInt(100) < missingRate) {
                        continue; // 损伤档缺失（垫层不缺失）
                    }
                    sink.setBlock(wx, wy, wz, blockKeyOf(c), metaOf(c), flags);
                }
            }
        }
    }

    /** 空气句柄键（游戏侧解析为 Blocks.air；离线 RasterSink 调色板按名透明）。 */
    public static final String K_AIR = "minecraft:air";

    /** 按注册名放置（S5 /gtsr structure 与编排器共用；未知名静默返回）。 */
    public static void placeByName(String name, BlockSink sink, int originX, int originZ, GroundFn ground,
        long plotSeed, int flags) {
        final Variant v = byName(name);
        if (v != null) {
            place(v, sink, originX, originZ, ground, plotSeed, flags);
        }
    }

    public static Variant byName(String name) {
        for (Variant v : ALL) {
            if (v.name.equals(name)) {
                return v;
            }
        }
        return null;
    }

    /** Registry placer 用 flat ground（/gtsr structure 原点 y 即基面，plan S5 口径）。 */
    public static CityVariants.GroundFn flatGround(final int groundY) {
        return (x, z) -> groundY;
    }

    // ═══ 注册 ═══

    private static volatile boolean registered;

    /**
     * 向 {@link StructureRegistry} 登记全部城变体（26 基础 + 2 巨构 = 28；dimension=PROSPERITY，
     * 注册名=基础名；footprint = max(sizeX,sizeZ)（旋转安全口径，巨构因此登记为<b>申报总 bbox</b>
     * 24/20——"申报不是钳制"与 P16-B1 城外跨片同语义），幂等；由 ProsperityWorldGenerator 构造时调用）。
     */
    public static void registerVariants() {
        if (registered) {
            return;
        }
        registered = true;
        for (final Variant v : ALL) {
            StructureRegistry.register(
                new StructureRegistry.Entry(
                    v.name,
                    StructureRegistry.Dimension.PROSPERITY,
                    Math.max(v.sizeX, v.sizeZ),
                    Math.max(v.sizeX, v.sizeZ),
                    (sink, x, y, z, seed) -> place(v, sink, x, z, flatGround(y), seed, BlockSink.FLAG_DIRECT)));
        }
    }

    /** GTSRWorldgenHash 无直接依赖（plotSeed 派生链在 CityPlanner）。 */
}
