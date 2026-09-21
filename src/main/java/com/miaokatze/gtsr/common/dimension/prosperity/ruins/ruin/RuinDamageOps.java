package com.miaokatze.gtsr.common.dimension.prosperity.ruins.ruin;

import java.util.Arrays;

import com.miaokatze.gtsr.common.dimension.framework.structure.GTSRWorldgenHash;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityVariants;

/**
 * 废墟族的<b>损毁算子</b>（<b>P8，plan §5 P8「新族骨架 + 按 variant opt-in 损毁算子」</b>）：
 * 把既有结构的字符层串"破败化"成破坏结构，并给出运行期的侵蚀档位。
 * <p>
 * ═══ 为什么写成"纯函数 + 字符层"而不是"放置时改世界" ═══
 * <ol>
 * <li>派生结果仍是 {@code String[][]} 字符盘 ⇒ 天然进得了 {@code RosterIntegrityCheck} 的逐名模板
 * SHA256 钉死面（P0 纪律），破坏结构因此和母体一样可逐位回归，不是"跑一次才知道长啥样"；</li>
 * <li>派生<b>只读</b>母体的 {@code layers}、从不回写 ⇒ "旧 39 名模板字节零改动"是构造性质，
 * 不是"我们小心没碰"（判据 1 的机检面）；</li>
 * <li>零 Minecraft 依赖（String 键 + 纯几何），JEP330 离线驱动（{@code tools/dim1/RuinFamilyCheck} /
 * {@code StructureViewerExport}）与游戏内放置共用同一份派生结果。</li>
 * </ol>
 * ═══ 材质红线：只换字符，不造字符（用户口径"保持材质、结构"）═══
 * 输出字符集 ⊆ 输入字符集 ∪ {@link CityVariants} 既有记号族。本类<b>不新增记号、不新增方块、
 * 不新增方块状态</b>。母体里的 GT5U 机器注册体两键（{@code 'X'}=sBlockCasings1 meta10、
 * {@code 'Z'}=sBlockCasings2 meta0）与机型核心位 {@code 'C'}，一律经
 * {@link #normalizeToRuinVocabulary} 折进本维三层壳族（锈壳/积碳壳/碎瓷壳）——这一步既是
 * "锈蚀替换"的物理含义（机器件被岁月还原成壳层材料），也是 {@code RuinFamilyCheck} 的
 * <b>TE 洁净机检</b>能通过的前提：废墟落块里不存在 GT 机器注册体，也不存在任何
 * {@code ITileEntityProvider}/容器方块。
 * <p>
 * ═══ 确定性 ═══
 * 全部掷骰走 {@link GTSRWorldgenHash}（plan §2.1 L2 禁止项：不得手搓哈希、不得内联已登记的位混合
 * 常数），盐由调用点给出 ⇒ "同母体 + 同盐 + 同尺寸"必得同一破坏模板，类加载即定盘。
 * <b>P16-B2</b>：这份掷骰实现体（{@link #roll}）同时是城外跨 chunk 巨构形态层的唯一掷骰出口——
 * 形态侧要"每座残骸各不相同"但不许新开随机源，正解就是复用本方法（盐与坐标组合由调用点给）。
 * 运行期的<b>逐块侵蚀</b>不在本类，走放置器里的 {@link CityVariants#MISSING_RATES} +
 * {@code Random(placeSeed)}，与既有三族同一纪律（y=0 垫层恒放＝现成半埋语义）。
 */
public final class RuinDamageOps {

    /**
     * 算子标签（申报进 {@link RuinTemplate#ops()}，供 {@code RuinFamilyCheck} 与展示页逐名核对；
     * 标签本身不是真值，真值是下面六个方法体）。
     */
    public static final String OP_BREAK_SPAN = "break_span";
    public static final String OP_TOPPLE = "topple";
    public static final String OP_HALF_BURY = "half_bury";
    public static final String OP_CHIP_CORNERS = "chip_corners";
    public static final String OP_RUST_SWAP = "rust_swap";
    public static final String OP_COLLAPSE_FAN = "collapse_fan";

    /** 既有 casing 三 meta 风化族（锈壳 meta0 / 积碳壳 meta1 / 碎瓷壳 meta2）。 */
    private static final char RUST_SHELL = '#';
    private static final char SOOT_SHELL = '@';
    private static final char PORCELAIN_SHELL = '%';

    /** 覆压/塌落散料用的既有记号（原版沙砾）。 */
    private static final char SPILL_GRAVEL = 'G';

    /** 百分比域（与 {@link CityVariants#MISSING_RATES} 同一"百分数"词汇，不另造单位）。 */
    private static final int PCT = 100;

    /** 废墟模板<b>禁止</b>出现的记号（GT5U 两键 + 机型核心位）；供 {@code RuinFamilyCheck} 复用。 */
    public static final char[] FORBIDDEN_CHARS = { 'X', 'Z', 'C' };

    private RuinDamageOps() {}

    // ═══════════════════════════ 入口：母体 → 破坏模板 ═══════════════════════════

    /**
     * 按顺序对母体字符盘应用一组算子，返回<b>新</b>的 {@code String[][]}（母体不被改动）。
     *
     * @param layers 母体层串（{@code layers[0]} = 顶层，与 {@code CityVariants}/{@code Outpost} 同一约定）
     * @param ops    算子标签，取值只能是本类六个 {@code OP_*}；未知标签 fail fast（不静默跳过）
     */
    public static String[][] derive(String[][] layers, long salt, String[] ops) {
        final int sizeY = layers.length;
        final int sizeZ = layers[0].length;
        final int sizeX = layers[0][0].length();
        final char[][][] g = toGrid(layers, sizeX, sizeY, sizeZ);
        normalizeToRuinVocabulary(g, sizeX, sizeY, sizeZ);
        for (final String op : ops) {
            switch (op) {
                case OP_BREAK_SPAN:
                    breakSpan(g, sizeX, sizeY, sizeZ, salt);
                    break;
                case OP_TOPPLE:
                    topple(g, sizeX, sizeY, sizeZ, salt);
                    break;
                case OP_HALF_BURY:
                    halfBury(g, sizeX, sizeY, sizeZ, salt);
                    break;
                case OP_CHIP_CORNERS:
                    chipCorners(g, sizeX, sizeY, sizeZ, salt);
                    break;
                case OP_RUST_SWAP:
                    rustSwap(g, sizeX, sizeY, sizeZ, salt);
                    break;
                case OP_COLLAPSE_FAN:
                    collapseFan(g, sizeX, sizeY, sizeZ, salt);
                    break;
                default:
                    throw new IllegalStateException("[GTSR] ruin damage op unknown: " + op);
            }
        }
        return toLayers(g, sizeX, sizeY, sizeZ);
    }

    // ══════════════════════════════ 六个算子 ══════════════════════════════

    /**
     * <b>断跨</b>：沿 x 轴选一个断口，断口之上的结构整段消失（y=0 基座只留残根），断口边界本身再啃掉
     * 若干格——"桥/渠从跨中断掉"，不是均匀缺块。断口只允许落在<b>后半跨</b>，保证残段至少留一半，
     * 否则派生结果可能是空盘（放置器永远 {@code commit(0)}，是假绿温床）。
     */
    static void breakSpan(char[][][] g, int sizeX, int sizeY, int sizeZ, long salt) {
        if (sizeX < 4 || sizeY < 2) {
            return;
        }
        final int lo = Math.max(2, sizeX / 2);
        final int cut = lo + (int) roll(salt, sizeX, 7, Math.max(1, sizeX - lo));
        final boolean cutFromLeft = roll(salt, cut, 11, 2) == 0;
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    final boolean gone = cutFromLeft ? x >= cut : x <= sizeX - 1 - cut;
                    if (!gone) {
                        continue;
                    }
                    if (y > 0) {
                        g[y][z][x] = ' ';
                    } else if (roll(salt, x * 31 + z, 17, 2) == 0) {
                        g[y][z][x] = ' '; // 基座残根：一半留一半啃掉
                    }
                }
            }
        }
        final int edge = clamp(cutFromLeft ? cut - 1 : sizeX - cut, 0, sizeX - 1);
        for (int z = 0; z < sizeZ; z++) {
            for (int y = 1; y < sizeY; y++) {
                if (roll(salt, edge * 17 + z, 23, 3) == 0) {
                    g[y][z][edge] = ' '; // 残端不齐
                }
            }
        }
    }

    /**
     * <b>倾覆</b>：逐层水平错动（越高错得越远，像被推歪的塔），错出边界的部件直接丢；
     * 再把上部半壁砍掉——"歪着要倒"比"已经躺平"更有废墟感。
     */
    static void topple(char[][][] g, int sizeX, int sizeY, int sizeZ, long salt) {
        if (sizeY < 2 || sizeX < 3) {
            return;
        }
        final int dir = roll(salt, sizeZ, 3, 2) == 0 ? 1 : -1;
        final int shearEvery = 1 + (int) roll(salt, sizeX, 5, Math.max(1, Math.min(3, sizeY - 1)));
        final int maxShift = Math.max(1, sizeX / 3);
        final char[][][] out = blankGrid(sizeX, sizeY, sizeZ);
        for (int y = 0; y < sizeY; y++) {
            final int shift = Math.min(maxShift, y / shearEvery) * dir;
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    final int nx = x + shift;
                    if (nx >= 0 && nx < sizeX) {
                        out[y][z][nx] = g[y][z][x]; // 错出边界 = 掉了
                    }
                }
            }
        }
        for (int y = 0; y < sizeY; y++) {
            System.arraycopy(out[y], 0, g[y], 0, sizeZ);
        }
        final boolean keepLowSide = roll(salt, 1, 29, 2) == 0;
        final int cap = Math.max(1, sizeY / 2);
        for (int y = cap; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    final boolean keep = keepLowSide ? x < sizeX / 2 : x >= sizeX / 2;
                    if (!keep) {
                        g[y][z][x] = ' ';
                    }
                }
            }
        }
    }

    /**
     * <b>半埋</b>：整体下沉 1-2 层（丢掉被埋没的最底几层——母体的 y=0 垫层就是"被埋那层"，
     * 任务包点名的现成语义），然后从一侧沿 y 递减地铺一层散料覆压。
     */
    static void halfBury(char[][][] g, int sizeX, int sizeY, int sizeZ, long salt) {
        if (sizeY < 3) {
            return;
        }
        final int sink = 1 + (int) roll(salt, sizeX * 7L + sizeZ, 13, 2);
        for (int y = 0; y < sizeY; y++) {
            final int src = y + sink;
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    g[y][z][x] = src < sizeY ? g[src][z][x] : ' ';
                }
            }
        }
        final boolean fromLeft = roll(salt, sizeY, 37, 2) == 0;
        final int ridge = Math.max(2, sizeY / 2);
        for (int y = 0; y < ridge; y++) {
            final int reach = ridge - y;
            for (int z = 0; z < sizeZ; z++) {
                for (int i = 0; i < reach; i++) {
                    final int x = clamp(fromLeft ? i : sizeX - 1 - i, 0, sizeX - 1);
                    if (g[y][z][x] == ' ') {
                        g[y][z][x] = roll(salt, x * 13 + z * 7 + y, 41, 3) == 0 ? PORCELAIN_SHELL : SPILL_GRAVEL;
                    }
                }
            }
        }
    }

    /** <b>缺角</b>：上部四角按不同深度啃掉（四角各自独立，避免"四角旋转对称"看起来像程序）。 */
    static void chipCorners(char[][][] g, int sizeX, int sizeY, int sizeZ, long salt) {
        final int rx = 1 + (int) roll(salt, sizeX, 43, Math.max(1, sizeX / 3));
        final int rz = 1 + (int) roll(salt, sizeZ, 47, Math.max(1, sizeZ / 3));
        final int ry = Math.max(1, sizeY / 2);
        for (int y = sizeY - ry; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    if (Math.min(x, sizeX - 1 - x) >= rx || Math.min(z, sizeZ - 1 - z) >= rz) {
                        continue;
                    }
                    final int cornerIdx = (x < sizeX / 2 ? 0 : 1) * 2 + (z < sizeZ / 2 ? 0 : 1);
                    if ((sizeY - 1 - y) <= (cornerIdx * 2 + 1) % Math.max(2, ry + 1)) {
                        g[y][z][x] = ' ';
                    }
                }
            }
        }
    }

    /**
     * <b>锈蚀替换</b>：三层壳族按位置哈希互相折算（锈壳↔积碳壳↔碎瓷壳），并把硬质人工件降级：
     * 铺面 {@code 's'}→碎瓷壳/沙砾、原版石 {@code 'S'}→圆石。任务包点名的
     * "casing 三 meta 风化族"抓手就落在这里。
     */
    static void rustSwap(char[][][] g, int sizeX, int sizeY, int sizeZ, long salt) {
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    final char c = g[y][z][x];
                    if (c == ' ' || c == '.') {
                        continue;
                    }
                    final long h = roll(salt, x * 3 + y * 17 + z * 29, 53, PCT);
                    switch (c) {
                        case RUST_SHELL:
                            if (h < 12) {
                                g[y][z][x] = SOOT_SHELL;
                            } else if (h < 22) {
                                g[y][z][x] = PORCELAIN_SHELL;
                            }
                            break;
                        case SOOT_SHELL:
                            if (h < 10) {
                                g[y][z][x] = RUST_SHELL;
                            }
                            break;
                        case 's':
                            if (h < 45) {
                                g[y][z][x] = PORCELAIN_SHELL;
                            } else if (h < 60) {
                                g[y][z][x] = SPILL_GRAVEL;
                            }
                            break;
                        case 'S':
                            if (h < 40) {
                                g[y][z][x] = 'C'; // 下一轮归一化会把它折成积碳壳 meta1（同方块）
                            }
                            break;
                        default:
                            break;
                    }
                }
            }
        }
        normalizeToRuinVocabulary(g, sizeX, sizeY, sizeZ);
    }

    /**
     * <b>塌落扇形</b>：以上部 2/3 高度为"起塌面"，把其上的部件按离枢轴的远近向外甩成扇形散料，
     * 落点压进底部两层的边缘带，原位置腾空——"墙还立着、顶上已经摊开一圈"。
     */
    static void collapseFan(char[][][] g, int sizeX, int sizeY, int sizeZ, long salt) {
        if (sizeY < 4) {
            return;
        }
        final int start = Math.max(1, sizeY * 2 / 3);
        final int pivotX = (int) roll(salt, sizeX, 59, sizeX);
        final int pivotZ = (int) roll(salt, sizeZ, 61, Math.max(1, sizeZ));
        final int fanDepth = Math.max(1, Math.min(2, start));
        for (int y = start; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    final char c = g[y][z][x];
                    if (c == ' ' || c == '.') {
                        continue;
                    }
                    if (roll(salt, x * 5 + y * 11 + z * 19, 67, PCT) >= 60) {
                        continue; // 这一件没参与塌落
                    }
                    g[y][z][x] = ' ';
                    final int spread = y - start + 1;
                    final int fx = clamp(pivotX + Integer.compare(x - pivotX, 0) * spread, 0, sizeX - 1);
                    final int fz = clamp(pivotZ + Integer.compare(z - pivotZ, 0) * spread, 0, sizeZ - 1);
                    final int fy = (int) roll(salt, fx + fz * 3 + y, 71, fanDepth);
                    if (g[fy][fz][fx] == ' ') {
                        g[fy][fz][fx] = roll(salt, fx * 7 + fz, 73, 3) == 0 ? PORCELAIN_SHELL : SPILL_GRAVEL;
                    }
                }
            }
        }
    }

    // ═══════════════════════ 词汇归一化（TE 洁净的构造性保证）═══════════════════════════

    /**
     * 把母体里<b>不属于废墟词汇</b>的部件折进本维三层壳族：
     * <ul>
     * <li>{@code 'X'}（GT5U 镀铜砖块 sBlockCasings1 meta10）→ 碎瓷壳；</li>
     * <li>{@code 'Z'}（GT5U 固体钢机械外壳 sBlockCasings2 meta0）→ 锈壳；</li>
     * <li>{@code 'C'}（机型"核心位"）→ 积碳壳——核心位在废墟里<b>不再有任何"控制器/仓室"含义</b>
     * （本片判据 3 的机检目标），折成<b>同方块同 meta</b> 就是一次纯材料改写。</li>
     * </ul>
     * 公开是为了让 {@code tools/dim1/RuinFamilyCheck} 能独立复核"废墟模板不含这三个记号"，
     * 而不是只信派生结果。
     */
    public static void normalizeToRuinVocabulary(char[][][] g, int sizeX, int sizeY, int sizeZ) {
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    switch (g[y][z][x]) {
                        case 'X':
                            g[y][z][x] = PORCELAIN_SHELL;
                            break;
                        case 'Z':
                            g[y][z][x] = RUST_SHELL;
                            break;
                        case 'C':
                            g[y][z][x] = SOOT_SHELL;
                            break;
                        default:
                            break;
                    }
                }
            }
        }
    }

    // ═══════════════════════════ 运行期 opt-in 档位 ═══════════════════════════

    /**
     * micro 强度档 → 废墟"完整度"结构档（0 = 最完整 / 2 = 最烂）。
     * micro 未接线（中性 1.0F）⇒ 档 1，<b>不伪造强度</b>（与 {@code microStrengthAt} 的降级口径同源）。
     */
    public static int structuralTier(float microStrength) {
        if (microStrength >= 1.15F) {
            return 0;
        }
        if (microStrength <= 0.85F) {
            return 2;
        }
        return 1;
    }

    /**
     * 生效缺失率——<b>opt-in 损毁算子的唯一入口</b>：{@code allowsDamagedVariant}
     * （{@code StructureRegistry.Entry} 的 P7 扩字段）是唯一开关，false ⇒ 恒 0，
     * 既有三族的放置器因此与本算子完全无关（判据 2"算子只作用于新族"）；
     * true ⇒ 取 {@link CityVariants#MISSING_RATES} 的一档，并按 micro 结构档上下偏移一档
     * （钳在数组内）⇒ 强 micro cell 的废墟更完整、弱 micro cell 的更烂（判据 6 的行为差）。
     */
    public static int effectiveMissingRate(boolean allowsDamagedVariant, long placeSeed, float microStrength) {
        if (!allowsDamagedVariant) {
            return 0;
        }
        final int base = CityVariants.damageTier(placeSeed);
        final int biased = clamp(base + structuralTier(microStrength) - 1, 0, CityVariants.MISSING_RATES.length - 1);
        return CityVariants.MISSING_RATES[biased];
    }

    // ═══════════════════════════════ 工具件 ═══════════════════════════════

    /** 母体层串（{@code layers[0]}=顶层）→ 工作栅格 {@code g[y][z][x]}（y=0 = 底层）。 */
    private static char[][][] toGrid(String[][] layers, int sizeX, int sizeY, int sizeZ) {
        final char[][][] g = blankGrid(sizeX, sizeY, sizeZ);
        for (int y = 0; y < sizeY; y++) {
            final String[] rows = layers[sizeY - 1 - y];
            if (rows.length != sizeZ) {
                throw new IllegalStateException(
                    "[GTSR] ruin mother layer " + y + " row count " + rows.length + " != " + sizeZ);
            }
            for (int z = 0; z < sizeZ; z++) {
                if (rows[z].length() != sizeX) {
                    throw new IllegalStateException(
                        "[GTSR] ruin mother layer " + y
                            + " row "
                            + z
                            + " length "
                            + rows[z].length()
                            + " != sizeX "
                            + sizeX);
                }
                g[y][z] = rows[z].toCharArray();
            }
        }
        return g;
    }

    /** 工作栅格 → 层串（{@code layers[0]} = 顶层），与 {@link #toGrid} 严格互逆。 */
    private static String[][] toLayers(char[][][] g, int sizeX, int sizeY, int sizeZ) {
        final String[][] layers = new String[sizeY][];
        for (int y = 0; y < sizeY; y++) {
            final int ly = sizeY - 1 - y;
            final String[] rows = new String[sizeZ];
            for (int z = 0; z < sizeZ; z++) {
                rows[z] = new String(g[ly][z]);
            }
            layers[y] = rows;
        }
        return layers;
    }

    private static char[][][] blankGrid(int sizeX, int sizeY, int sizeZ) {
        final char[][][] g = new char[sizeY][][];
        for (int y = 0; y < sizeY; y++) {
            g[y] = new char[sizeZ][];
            for (int z = 0; z < sizeZ; z++) {
                g[y][z] = blankRow(sizeX);
            }
        }
        return g;
    }

    private static char[] blankRow(int sizeX) {
        final char[] row = new char[sizeX];
        Arrays.fill(row, ' ');
        return row;
    }

    /**
     * 域盐：让 {@link #roll} 的入参 salt 只作为 {@code cellSeed} 的 <b>worldSeed 位</b>出现一次。
     * <b>为什么必须有它</b>：早先写法把 {@code salt} 同时塞进 worldSeed 位和 salt 位，两者在
     * {@code cellSeed} 的 xor 里<b>自我抵消</b>（{@code salt ^ salt ^ x == x}），于是派生结果与模板盐
     * 完全无关——八条废墟模板全都退化成"同一档破坏"。这一条由 {@code RuinFamilyCheck} 的 B2
     * （"换盐必须变形"）抓到，不是靠读代码看出来。
     */
    private static final long ROLL_DOMAIN = 0x5241444CL; // "RADL"

    /**
     * 派生用掷骰的<b>全仓唯一实现体</b>：算法体走框架唯一件（{@link GTSRWorldgenHash#cellSeed} +
     * {@link GTSRWorldgenHash#splitmix64}），返回值用 {@link Math#floorMod(long, long)} 取模到
     * {@code [0, mod)}。plan §2.1 L2 禁止项：不得在本类手搓位混合常数（那些常数各自有唯一出处，
     * {@code HeightHashSingleSourceCheck} 逐字钉次数）。
     * <p>
     * <b>P16-B2 起本方法是 public</b>：城外跨 chunk 巨构的形态层（{@code RuinedColossusShapes} 的
     * 残骸派生与"锚点自由格 → 半埋深度/侵蚀档"那条打通链）<b>复用这一份</b>掷骰实现体，
     * 不再在自己文件里抄第二份 cellSeed+splitmix 组合（任务包 B2"不得新开随机源"的正解就是这一条：
     * 形态侧一律经本方法，盐由调用点给、域由 {@code a}/{@code b} 的坐标组合给）。
     * public 只放宽可见性，<b>不改变任何一次掷骰的取值</b> ⇒ ruin 族 8 条派生模板逐字节不变
     * （由 {@code RuinFamilyCheck} B2 的派生复算与 {@code RosterIntegrityCheck} 的逐名 SHA 共同钉住）。
     */
    public static long roll(long salt, long a, long b, int mod) {
        if (mod <= 0) {
            return 0L;
        }
        final long h = GTSRWorldgenHash.splitmix64(GTSRWorldgenHash.cellSeed(salt, (int) a, (int) b, ROLL_DOMAIN));
        return Math.floorMod(h, (long) mod);
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : Math.min(v, hi);
    }
}
