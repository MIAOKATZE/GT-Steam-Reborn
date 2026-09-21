package com.miaokatze.gtsr.common.dimension.prosperity.ruins.city;

import java.util.Arrays;

import com.miaokatze.gtsr.common.dimension.framework.structure.ChunkSpans;

/**
 * 城内巨构（<b>P16-B3</b>，plan §0 U3 / §1 G11，用户裁决"城内也设 1-2 个巨构"）：
 * 两条<b>申报边长超出单 chunk（16×16）</b>的城变体，经既有选型链入城——
 * {@code CityVariants.ALL}（登记面）→ {@code CityPlan} district 池（分区面）→
 * {@code CityPlan.plotVariant} 的 plotSeed 纯哈希选型（选型面），<b>零新随机源</b>：
 * 损伤档/朝向沿用 {@code damageTier/rotationOf}（同一把 {@code CITY_PLOT_SALT_MUL} 路由），
 * 形状本身是类加载期定盘的字符层（与 26 基础变体的程序化件同范式）。
 * <p>
 * ═══ 跨 chunk 协议：复用，不复制 ═══
 * 巨构的跨片写入走<b>城内已有那一条</b>：{@code ProsperityWorldGenerator.placeCities} 的
 * {@code CitySliceSink}（P16-B1 起是 {@link com.miaokatze.gtsr.common.dimension.framework.structure.ChunkSliceSink}
 * 的城内薄壳）——每个 populate chunk 只画与自己相交的那一份，逐 chunk 幂等，全仓没有第二套切片器；
 * 本类因此<b>不写任何 clip/intersect 代码</b>，几何余量口径全部收在 {@code CityPlan} 的三处
 * （渲染 bbox 外扩 / contentReach / 缓冲窗），由 {@code CityVariants.MAX_SIDE} 单一输入派生。
 * <p>
 * ═══ 形态红线（静态契约 fail fast，本类 {@code static} 块钉死）═══
 * <ol>
 * <li><b>低空不顶穿街道网格</b>：y≤{@link #LOW_CLEAR_MAX_Y} 的实心格只允许落在<b>四朝向不变</b>的
 * plot 内接矩形（本地 [invLo..invHi]，invLo=max(off, side-1-off-12)、invHi=min(off+12, side-1-off)，
 * off=(side-13)/2——side 与 13 奇偶不同余时锚点整除不对称（24 宽的 plot 矩形是 [5..17]，rot180
 * 映射成 [6..18]），低空件必须收在交集里才四转安全）——街带（plot 外 3 格）与邻地块在
 * <b>可行高度</b>零占用；巨构外溢部分是高架体（同 {@code broken_bridge} 拱跨压街的先例形态）；</li>
 * <li><b>中央大道全高不占</b>：低空入 plot 矩形 + 高空悬挑 ≤ ovMax，配合 district 池
 * （只入 md≥2 的工业环/边缘环，永不入核心区池）⇒ 任何朝向、任何损伤档下 |u|≤2 ∧ |v|≤2
 * 的大道列都摸不到巨构一块（判据腿：{@code CityShapeCheck} E 组逐块实放核验）；</li>
 * <li><b>材料红线一字不放宽</b>：记号只用 {@link CityVariants} 既有符号表
 * （{@code #@%dprcsSCGBXZ.} + 空格），高阶壳 / {@code gt.blockmachines} / 任何 TE 类方块在
 * 结构上写不出来（契约逐格核 {@code blockKeyOf(c) != null}，未知字符直接抛）；</li>
 * <li><b>旋转安全方形</b>：sizeX==sizeZ（四朝向对街带的相对位置同规格）；</li>
 * <li><b>真实实心 bbox &gt;16</b>：申报框不算数，两头吃不满单 chunk 栅格的"假巨构"直接抛；</li>
 * <li><b>边长 ≤ 45</b>：= PLOT_DEPTH + 2×16，{@code CityPlan} 三处余量容量公式的上界，
 * 将来再扩巨构越界时会被这条契约逼住，不会静默漏角。</li>
 * </ol>
 * <p>
 * ═══ 两条巨构与分区归属（为什么进这两个池）═══
 * <ul>
 * <li>{@link #GREAT_FORGE} {@code great_forge} 24×18×24（category industry）→ <b>只进工业环池</b>
 * （{@code CityPlan.INDUSTRIAL_POOL}，md∈{2,3}）：意象 = 巨型锻焊大厅（母本借 {@code boiler_house}/
 * {@code forge_hall} 的厂房语汇放大一圈，四角烟囱束 + 中央炉座 + 穹顶塌 1/4）；核心环被排除是因为
 * md&lt;2 的地块贴中央大道（24 宽悬挑会在高架层面罩住 5 格大道界面），边缘环被排除是因为边缘
 * 意象是"城市消融进荒野"（45% 空地率），一座体量最大的完整大厅放在那里语义相反；</li>
 * <li>{@link #TITAN_GEARWORKS} {@code titan_gearworks} 20×19×20（category tower）→ <b>只进边缘环池</b>
 * （{@code CityPlan.EDGE_POOL}，md≥4）：意象 = 巨型齿轮机构塔（塔身 + 双片脱啮的悬空齿轮盘 +
 * 断轴指针），是 {@code gear_tower}/{@code clock_tower} 天际线语汇的巨构化；md≥4 距大道 ≥3 个
 * 街距，3 格悬挑只碰次街带的高架层面；不入工业环是防"巨构连片"（两巨构同池会把同城双巨构的
 * 概率显著抬升），单池一件让名册计数即频次计数。</li>
 * </ul>
 * 频次口径（选型仍是 {@code plotVariant} 那一行哈希，无额外闸门）：巨构 = 池长分之一，
 * 工业环 1/15 ≈ 6.7%、边缘环 1/11 ≈ 9.1% 的非空地块（每城期望件数实测读数见
 * {@code CityShapeCheck} E 组的 MEGA 行）。候选池变长会使<b>该池既有变体的选型全部漂移</b>——
 * 由"旧区块不追溯"铁律吸收，验收一律新区域（plan §6 风险 2 同一条纪律）。
 */
public final class CityMegaVariants {

    private CityMegaVariants() {}

    /** 低空口径（结构层 y∈[0..本值] 的实心格必须落在 plot 矩形内；静态契约与 CityShapeCheck E 组共用）。 */
    public static final int LOW_CLEAR_MAX_Y = 2;

    /** 地块边长（与 {@link CityPlan#PLOT_DEPTH} 同值；契约用编译期常量避免类初始化环）。 */
    private static final int PLOT_LOCAL = 13;

    /** 巨构最大申报边长契约上界 = PLOT_LOCAL + 2×16（{@code CityPlan} 三处余量的外溢容量口径）。 */
    private static final int SIDE_CAP = PLOT_LOCAL + 2 * ChunkSpans.CHUNK_BLOCKS;

    /**
     * great_forge 24×18×24：巨型锻焊大厅。低空（y0 地坪 / y1-2 基座环）严格收在 plot 内；
     * y≥3 主体外扩到实心矩形 [2..21]（高架裙板压次街带，桥先例形态）；四角烟囱束错落，
     * 穹顶 y12..14 逐环收分并 NW 象限塌失（同 {@code dome_hall} 塌陷语汇），y15..17 残冠。
     */
    public static final CityVariants.Variant GREAT_FORGE = greatForge();

    /**
     * titan_gearworks 20×19×20：巨型齿轮机构塔。低空塔身严格收在 plot 内（y0 地坪、y1-2 井身环）；
     * y≥3 钟楼出挑、y9 承轮环、y10..12 两片巨型齿轮盘（齿缺按坐标模决定，NW 扇区脱啮塌口）、
     * y13..18 断轮残环、轮毂与断轴指针。齿轮盘是高架件——盘下（街带可行高度）不放任何实心格。
     */
    public static final CityVariants.Variant TITAN_GEARWORKS = titanGearworks();

    /** 本表全部巨构（CityVariants.ALL 末尾引用 + 静态契约遍历同一数组，一处真值）。 */
    static final CityVariants.Variant[] ALL = { GREAT_FORGE, TITAN_GEARWORKS };

    // ═══ 静态契约（类加载 fail fast，RuinedColossusShapes/CityVariants 同风格）═══

    static {
        for (final CityVariants.Variant v : ALL) {
            final int side = Math.max(v.sizeX, v.sizeZ);
            if (v.sizeX != v.sizeZ) {
                throw new IllegalStateException(
                    "[GTSR] city mega " + v.name + ": 巨构必须方形（四朝向对街带同规格），got " + v.sizeX + "x" + v.sizeZ);
            }
            if (side <= ChunkSpans.CHUNK_BLOCKS) {
                throw new IllegalStateException("[GTSR] city mega " + v.name + ": 申报边长未超单 chunk，不配入巨构表");
            }
            if (side > SIDE_CAP) {
                throw new IllegalStateException(
                    "[GTSR] city mega " + v.name + ": 申报边长 " + side + " 超出 CityPlan 余量容量上界 " + SIDE_CAP);
            }
            final int off = (side - PLOT_LOCAL) / 2; // 锚点口径下 plot 矩形本地 [off..off+PLOT_LOCAL-1]
            // 四朝向不变的交集矩形（rot180 把 [off..off+12] 映到 [side-1-off-12..side-1-off]，取交集）
            final int invLo = Math.max(off, side - 1 - off - (PLOT_LOCAL - 1));
            final int invHi = Math.min(off + PLOT_LOCAL - 1, side - 1 - off);
            int minX = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxZ = Integer.MIN_VALUE;
            int solid = 0;
            for (int y = 0; y < v.sizeY; y++) {
                for (int dz = 0; dz < v.sizeZ; dz++) {
                    for (int dx = 0; dx < v.sizeX; dx++) {
                        final char c = v.charAt(y, dx, dz);
                        if (c == ' ') {
                            continue; // 不触碰
                        }
                        if (c != '.' && CityVariants.blockKeyOf(c) == null) {
                            throw new IllegalStateException(
                                "[GTSR] city mega " + v.name + ": 引入未知记号 '" + c + "'（材料红线不得放宽）");
                        }
                        if (c == '.') {
                            continue; // 清空格不算实心
                        }
                        if (y <= LOW_CLEAR_MAX_Y && (dx < invLo || dx > invHi || dz < invLo || dz > invHi)) {
                            throw new IllegalStateException(
                                "[GTSR] city mega " + v.name
                                    + ": 低空(y="
                                    + y
                                    + ")顶穿四朝向不变内接矩形 ["
                                    + invLo
                                    + ","
                                    + invHi
                                    + "] @ ("
                                    + dx
                                    + ","
                                    + dz
                                    + ")，会占可行高度的街带");
                        }
                        solid++;
                        minX = Math.min(minX, dx);
                        maxX = Math.max(maxX, dx);
                        minZ = Math.min(minZ, dz);
                        maxZ = Math.max(maxZ, dz);
                    }
                }
            }
            if (maxX - minX + 1 <= ChunkSpans.CHUNK_BLOCKS || maxZ - minZ + 1 <= ChunkSpans.CHUNK_BLOCKS) {
                throw new IllegalStateException(
                    "[GTSR] city mega " + v.name + ": 真实实心 bbox 仍是单 chunk（申报 " + side + " 名不副实）");
            }
            if (solid < 24) {
                throw new IllegalStateException("[GTSR] city mega " + v.name + ": 实心 " + solid + " < 24");
            }
        }
    }

    // ═══ 形状构建（与 CityVariants 程序化件同范式：grid[y][z][x] 世界向上，' ' 起盘）═══

    private static CityVariants.Variant greatForge() {
        final int w = 24, h = 18, d = 24;
        final int b0 = 2, b1 = w - 3; // 主体实心矩形 [2..21]（低空契约要求 y≤2 时此处必须为 ' '/'.'）
        // 低空件用四朝向不变内接矩形 [6..17]（24 与 13 奇偶差 ⇒ 交集比 plot 矩形少一侧一格，
        // 与静态契约 invLo/invHi 同式；这样 rot∈{0,90,180,270} 任一朝向都不会把垫层/环墙甩进街带）
        final int l0 = Math.max((w - PLOT_LOCAL) / 2, w - 1 - (w - PLOT_LOCAL) / 2 - (PLOT_LOCAL - 1)); // 6
        final int l1 = Math.min((w - PLOT_LOCAL) / 2 + PLOT_LOCAL - 1, w - 1 - (w - PLOT_LOCAL) / 2); // 17
        final char[][][] g = grid(w, h, d);
        // y=0：内接矩形锈石地坪（垫层恒放置，损伤豁免同基础件）
        floor(g, 0, l0, l1);
        // y=1..2：基座环墙（仅内接矩形——低空不顶穿口径；南墙拱门 + 四角镀铜墩）
        for (int y = 1; y <= 2; y++) {
            for (int dz = l0; dz <= l1; dz++) {
                for (int dx = l0; dx <= l1; dx++) {
                    final boolean edge = dx == l0 || dx == l1 || dz == l0 || dz == l1;
                    if (!edge) {
                        g[y][dz][dx] = '.'; // 内腔
                        continue;
                    }
                    final boolean corner = (dx == l0 || dx == l1) && (dz == l0 || dz == l1);
                    final boolean gate = y == 1 && dz == l0 && dx >= l0 + 5 && dx <= l0 + 7;
                    g[y][dz][dx] = gate ? '.' : (corner && y == 2 ? 'X' : '#');
                }
            }
        }
        // y=3..11：主体大厅（矩形 [2..21] 环墙 + 空腹）；y=3 起进入高架层，允许压次街带
        for (int y = 3; y <= 11; y++) {
            for (int dz = b0; dz <= b1; dz++) {
                for (int dx = b0; dx <= b1; dx++) {
                    final boolean edge = dx == b0 || dx == b1 || dz == b0 || dz == b1;
                    if (!edge) {
                        g[y][dz][dx] = '.';
                        continue;
                    }
                    final boolean corner = (dx == b0 || dx == b1) && (dz == b0 || dz == b1);
                    final char c;
                    if (corner) {
                        c = 'Z'; // 固体钢角柱
                    } else if (y == 7) {
                        c = 'X'; // 镀铜砖腰带
                    } else if (y >= 5 && y <= 9 && (dx * 7 + dz * 13 + y * 5) % 11 == 0) {
                        c = 'B'; // 高窗（铁栏杆棂条）
                    } else if ((dx * 5 + dz * 3 + y) % 17 == 0) {
                        c = '%'; // 碎瓷壳咬缺点缀
                    } else {
                        c = '#';
                    }
                    g[y][dz][dx] = c;
                }
            }
        }
        // y=4..5 中央炉座（内部 [10..13] 方环 '@'，心部镀铜台/烟气）
        for (int y = 4; y <= 5; y++) {
            for (int dz = 10; dz <= 13; dz++) {
                for (int dx = 10; dx <= 13; dx++) {
                    final boolean edge = dx == 10 || dx == 13 || dz == 10 || dz == 13;
                    g[y][dz][dx] = edge ? '@' : (y == 4 ? 'X' : '.');
                }
            }
        }
        // 四角烟囱残柱 2×2 束（y=3..13/14 错落；'c' 烟体 + 对角 '@' 烟道，顶部锯齿破口）
        final int[][] stacks = { { 3, 3, 13 }, { 19, 3, 14 }, { 3, 19, 14 }, { 19, 19, 13 } };
        for (int i = 0; i < stacks.length; i++) {
            final int sx = stacks[i][0], sz = stacks[i][1], top = stacks[i][2];
            for (int y = 3; y <= top; y++) {
                for (int dz = sz; dz <= sz + 1; dz++) {
                    for (int dx = sx; dx <= sx + 1; dx++) {
                        if (y >= top - 1 && (dx * 7 + dz * 13 + y * 5 + i * 11) % 3 == 0) {
                            continue; // 顶部锯齿破口（chimneyStack 同式）
                        }
                        g[y][dz][dx] = dx - sx == dz - sz ? '@' : 'c';
                    }
                }
            }
        }
        // y=12..14 逐环收分穹顶（NW 象限塌失，dome_hall 语汇）
        domeRing(g, 12, 4, 19, '#');
        domeRing(g, 13, 6, 17, 'r');
        domeRing(g, 14, 8, 15, 'Z');
        // y=15..16 残冠（铆接板/积碳壳稀疏半密环）
        crownRing(g, 15, 4, 19, 'r');
        crownRing(g, 16, 6, 17, '@');
        // y=17 残旗杆（四隅 'c' + 中央断轴 'd'）
        final int[][] fin = { { 8, 8 }, { 15, 15 }, { 8, 15 }, { 15, 8 }, { 11, 12 }, { 12, 11 } };
        for (final int[] f : fin) {
            g[17][f[1]][f[0]] = (f[0] & 1) == 0 ? 'c' : 'd';
        }
        return new CityVariants.Variant("great_forge", "industry", w, h, d, toLayers(g, w, h, d));
    }

    private static CityVariants.Variant titanGearworks() {
        final int w = 20, h = 19, d = 20;
        final int off = (w - PLOT_LOCAL) / 2; // 3 → 锚点口径下 plot 矩形本地 [3..15]
        final int l0 = Math.max(off, w - 1 - off - (PLOT_LOCAL - 1)); // 四朝向不变内接矩形 [4..15]
        final int l1 = Math.min(off + PLOT_LOCAL - 1, w - 1 - off);
        final int s0 = 4, s1 = 14; // 井身塔 [4..14]（低空件，⊆ 内接矩形）
        final char[][][] g = grid(w, h, d);
        // y=0：plot 内地坪（内接矩形，四转安全，理由同 greatForge 低空件）
        floor(g, 0, l0, l1);
        // y=1..2：井身环墙（仅塔矩形——低空不顶穿口径）+ 内腔
        for (int y = 1; y <= 2; y++) {
            for (int dz = s0; dz <= s1; dz++) {
                for (int dx = s0; dx <= s1; dx++) {
                    final boolean edge = dx == s0 || dx == s1 || dz == s0 || dz == s1;
                    if (!edge) {
                        g[y][dz][dx] = '.';
                        continue;
                    }
                    final boolean corner = (dx == s0 || dx == s1) && (dz == s0 || dz == s1);
                    g[y][dz][dx] = corner && y == 2 ? 'X' : '#';
                }
            }
        }
        // y=3..6：井身延续（y=6 镀铜腰带 + 固体钢角柱）
        for (int y = 3; y <= 6; y++) {
            wallRing(g, y, s0, s1, y == 6);
        }
        // y=7..8：钟楼出挑（到 plot 矩形边，高架层）+ 钟窗
        for (int y = 7; y <= 8; y++) {
            for (int dz = off; dz <= off + 12; dz++) {
                for (int dx = off; dx <= off + 12; dx++) {
                    final boolean edge = dx == off || dx == off + 12 || dz == off || dz == off + 12;
                    g[y][dz][dx] = edge ? (y == 7 && (dx + dz) % 4 == 0 ? 'B' : '#') : '.';
                }
            }
        }
        // y=9：承轮环 [2..17] + 四向 'p' 管道拉杆（塔身 → 环，四边对称）
        solidRing(g, 9, 2, 17, 'r');
        g[9][9][3] = 'p';
        g[9][10][3] = 'p';
        g[9][3][9] = 'p';
        g[9][3][10] = 'p';
        g[9][9][16] = 'p';
        g[9][10][16] = 'p';
        g[9][16][9] = 'p';
        g[9][16][10] = 'p';
        // y=10..12：双片巨型齿轮盘（外两环为齿/轮体，NW 扇区脱啮；齿缺 = 坐标模，确定性）
        for (int y = 10; y <= 12; y++) {
            for (int dz = 0; dz < 20; dz++) {
                for (int dx = 0; dx < 20; dx++) {
                    final int dep = Math.min(Math.min(dx, 19 - dx), Math.min(dz, 19 - dz)); // 0=最外环
                    if (dep == 0) {
                        if ((dx + dz + y) % 4 == y % 3) {
                            continue; // 缺齿
                        }
                        if (dx < 9 && dz < 9 && (dx + dz) % 2 == 0) {
                            continue; // NW 扇区脱啮
                        }
                        g[y][dz][dx] = 'd'; // 齿（轨枕块=暗色残件）
                    } else if (dep == 1) {
                        if ((dx * 7 + dz * 13 + y * 5) % 8 == 0) {
                            continue; // 轮体破口
                        }
                        g[y][dz][dx] = y == 11 ? 'r' : '#';
                    } else if (dep == 2 && (dx == 9 || dx == 10 || dz == 9 || dz == 10)) {
                        g[y][dz][dx] = 'p'; // 轮辐（管道）
                    }
                }
            }
        }
        // y=13：断轮残环 [4..15]（隔一缺一）
        for (int dz = 4; dz <= 15; dz++) {
            for (int dx = 4; dx <= 15; dx++) {
                if ((dx == 4 || dx == 15 || dz == 4 || dz == 15) && (dx + dz) % 3 != 0) {
                    g[13][dz][dx] = 'c';
                }
            }
        }
        // y=14..16：轮毂（Z 环 → @ 积碳体 → 四隅铆接板）
        solidRing(g, 14, 7, 12, 'Z');
        solidRing(g, 15, 8, 11, '@');
        g[15][9][9] = '.';
        g[15][9][10] = '.';
        g[15][10][9] = '.';
        g[15][10][10] = '.';
        g[16][8][8] = 'r';
        g[16][11][11] = 'r';
        g[16][8][11] = '@';
        g[16][11][8] = '@';
        // y=16..17 断轴残段 + y=18 斜倚指针（'B' 铁栏杆 = 旧钟针）
        g[16][9][9] = 'c';
        g[16][10][10] = 'c';
        g[17][9][9] = '@';
        g[17][10][10] = 'c';
        g[18][9][9] = 'B';
        g[18][10][6] = 'B';
        g[18][6][10] = 'B';
        return new CityVariants.Variant("titan_gearworks", "tower", w, h, d, toLayers(g, w, h, d));
    }

    // —— 构建小工具（本类专用；6 行级几何辅助非切片协议，协议仍只在 ChunkSliceSink）——

    private static void floor(char[][][] g, int y, int lo, int hi) {
        for (int dz = lo; dz <= hi; dz++) {
            Arrays.fill(g[y][dz], lo, hi + 1, 's');
        }
    }

    /** 井身环墙（可选镀铜腰带 + 固体钢角柱）。 */
    private static void wallRing(char[][][] g, int y, int lo, int hi, boolean bronze) {
        for (int dz = lo; dz <= hi; dz++) {
            for (int dx = lo; dx <= hi; dx++) {
                final boolean edge = dx == lo || dx == hi || dz == lo || dz == hi;
                if (!edge) {
                    continue;
                }
                final boolean corner = (dx == lo || dx == hi) && (dz == lo || dz == hi);
                final char c;
                if (corner && bronze) {
                    c = 'Z';
                } else if (bronze) {
                    c = 'X';
                } else {
                    c = (dx * 5 + dz * 3 + y * 7) % 19 == 0 ? '%' : '#';
                }
                g[y][dz][dx] = c;
            }
        }
    }

    private static void solidRing(char[][][] g, int y, int lo, int hi, char c) {
        for (int dz = lo; dz <= hi; dz++) {
            for (int dx = lo; dx <= hi; dx++) {
                if (dx == lo || dx == hi || dz == lo || dz == hi) {
                    g[y][dz][dx] = c;
                }
            }
        }
    }

    /** 穹顶单环：NW 象限半密塌失 + 其余稀疏破口（dome_hall 塌陷语汇）。 */
    private static void domeRing(char[][][] g, int y, int lo, int hi, char fill) {
        for (int dz = lo; dz <= hi; dz++) {
            for (int dx = lo; dx <= hi; dx++) {
                if (dx != lo && dx != hi && dz != lo && dz != hi) {
                    continue;
                }
                if (dx < 12 && dz < 12 && (dx + dz + y) % 2 == 0) {
                    continue; // NW 象限塌失（半密）
                }
                if ((dx * 7 + dz * 5 + y * 3) % 9 == 0) {
                    continue; // 其余稀疏破口
                }
                g[y][dz][dx] = fill;
            }
        }
    }

    /** 残冠单环：模 5 半密。 */
    private static void crownRing(char[][][] g, int y, int lo, int hi, char fill) {
        for (int dz = lo; dz <= hi; dz++) {
            for (int dx = lo; dx <= hi; dx++) {
                if (dx != lo && dx != hi && dz != lo && dz != hi) {
                    continue;
                }
                if ((dx + dz + y) % 5 < 2) {
                    g[y][dz][dx] = fill;
                }
            }
        }
    }

    private static char[][][] grid(int w, int h, int d) {
        final char[][][] g = new char[h][d][w];
        for (int y = 0; y < h; y++) {
            for (int z = 0; z < d; z++) {
                Arrays.fill(g[y][z], ' ');
            }
        }
        return g;
    }

    private static String[][] toLayers(char[][][] g, int w, int h, int d) {
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
}
