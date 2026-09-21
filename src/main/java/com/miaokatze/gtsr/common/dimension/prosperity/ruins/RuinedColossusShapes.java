package com.miaokatze.gtsr.common.dimension.prosperity.ruins;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.miaokatze.gtsr.common.dimension.framework.structure.ChunkSpans;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityBlockResolver;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityVariants;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ruin.RuinDamageOps;

/**
 * 城外<b>跨 chunk 巨型机器残骸</b>形状库（<b>P16-B1</b>，plan §0 U2 / §1 G6·G7 / 任务包 item 1·3·4）：
 * 两条总 bbox 超出单个 chunk 的机型（母体意象 = {@code MTESteamHubArray} 的 9×9 枢纽叠层与
 * {@code ClusterStructureDef} 的长轴大厅，<b>只借外形不借方块</b>），供
 * {@link RuinedMachinePlacer} 的跨片分支渲染。
 * <p>
 * ═══ 它是"新机型"，不是"新族名册"（互斥链与密度的答案）═══
 * 本表的条目<b>并入残缺机器族唯一的候选池</b>（{@code RuinedMachinePlacer.POOL} =
 * {@link RuinedMachineShapes#ALL} + {@link #ALL}），因此：
 * <ol>
 * <li><b>零新增随机</b>：沿用机器族那一把 {@code SALT_MACHINE}、那一条 {@code roll()} 实现体与那一条
 * 消费序（{@code nextDouble → nextInt(候选池) → nextInt(freeX+1) → nextInt(freeZ+1)}），
 * 没有第二个 {@code new Random}，也没有新的盐（任务包 item 6）；</li>
 * <li><b>零叠加密度</b>：分母仍是 {@code Config.prosperityMachineChance} 一处（{@code
 * tools/dim1/ContourBudgetCheck} A3 的"机器分母只被 3 个文件引用"不因此变多），
 * 每 chunk 结构预算仍是同一份 1 座 ⇒ 巨构只是<b>把机器族那一格从"小机型"换成"大机型"</b>；
 * 而跨片让行判据（{@link RuinedMachinePlacer} 的 coverage 那一关）没过时，<b>本 chunk 的机器族整格作废</b>
 * （不回落成小机型，否则就是拿两次掷骰换一次 ⇒ 密度上升方向搞反）⇒ 净效果是"偶发让位"，只减不增；</li>
 * <li><b>三环链语义一字未改</b>：编排器仍是 {@code outpost → 残缺机器（含本表）→ 废墟}，
 * 本表坐在<b>第二环内部</b>，不新增环；与另两环的互斥由"跨片让行 + 既有 {@code familySpacingAllows}
 * （同族优先级档）+ 每 chunk 预算"三件共同保证，申报见 {@link RuinedMachinePlacer} 类注释。</li>
 * </ol>
 * <p>
 * ═══ 为什么走新机型而不是把禁字符清单撕开（任务包 item 5）═══
 * {@code ruin} 族现禁 {@code X/Z/C} 三字符（{@code RuinDamageOps.FORBIDDEN_CHARS}）是<b>派生谱系纪律</b>
 * （废墟必须由既有母体破败化得到，不得凭空引入新材质），不是安全属性。"机器残骸含镀铜砖"与那条纪律
 * 正面冲突 ⇒ 本表另立机型并自带记号表，禁字符清单一个字没动。真正的安全闸是
 * "落块集合里没有 {@code ITileEntityProvider}/{@code BlockContainer}/{@code hasTileEntity} 为真的方块，
 * 且 (键,meta) 全在红线内"，由 {@code CityBlockResolver} 的键表（结构上就没有机器块）
 * 与 {@code tools/dim1/RuinFamilyCheck} 的 COLOSSUS 组两道钉。
 * <p>
 * ═══ 记号表（本族自己的键表 = {@link CityVariants} 既有记号的超集 + 七枚 GT 壳记号）═══
 * 共用记号（含义与 {@link CityVariants#blockKeyOf(char)} <b>逐字一致</b>，不另立第二套真值）：
 * {@code #}锈壳0 {@code @}积碳壳1 {@code %}碎瓷壳2 {@code d}轨枕 {@code p}锈管 {@code r}铆接板
 * {@code c}烟囱残段 {@code s}锈石铺面 {@code S}石 {@code C}圆石 {@code G}沙砾 {@code B}铁栏杆
 * {@code X}镀铜砖块 {@code Z}固体钢机械外壳 {@code .}清空气 {@code 空格}不触碰。
 * 本族新增七枚（一字符 = 一 (键,meta)，全部是<b>无 TE 的静态外壳</b>）：
 * <table border="1">
 * <tr>
 * <th>字符</th>
 * <th>注册标识 + meta</th>
 * <th>语义</th>
 * <th>存在性证据</th>
 * </tr>
 * <tr>
 * <td>{@code n}</td>
 * <td>{@code gt.blockcasings2} m12</td>
 * <td>青铜管道</td>
 * <td>GT5U {@code BlockCasings2.java:61 register(12, Casing_Pipe_Bronze)}（块对象 = 已白名单的
 * {@code K_GT_STEEL} 同一块，故本字符<b>不扩键</b>，只引新 meta）</td>
 * </tr>
 * <tr>
 * <td>{@code N}</td>
 * <td>{@code gt.blockcasings2} m13</td>
 * <td>钢管道</td>
 * <td>同 {@code :63 register(13, Casing_Pipe_Steel)}（m14 钛 / m15 钨钢 = 禁用，见下）</td>
 * </tr>
 * <tr>
 * <td>{@code e}</td>
 * <td>{@code gt.blockcasings2} m2</td>
 * <td>青铜齿轮箱</td>
 * <td>同 {@code :41 register(2, Casing_Gearbox_Bronze)}（m4 钛档不引）</td>
 * </tr>
 * <tr>
 * <td>{@code E}</td>
 * <td>{@code gt.blockcasings2} m3</td>
 * <td>钢齿轮箱</td>
 * <td>同 {@code :43 register(3, Casing_Gearbox_Steel)}（m5 钨钢档不引）</td>
 * </tr>
 * <tr>
 * <td>{@code f}</td>
 * <td>{@code gt.blockcasings3} m13</td>
 * <td>青铜燃烧室</td>
 * <td>{@code BlockCasings3.java:32 register(13, Casing_Firebox_Bronze)}；块对象 = 新键
 * {@link CityBlockResolver#K_GT_FIREBOX}（{@code BlockCasings3 extends BlockCasingsAbstract
 * extends GTGenericBlock}，无 {@code randomTick}/无 TE，取证件 B6 已证）</td>
 * </tr>
 * <tr>
 * <td>{@code F}</td>
 * <td>{@code gt.blockcasings3} m14</td>
 * <td>钢燃烧室</td>
 * <td>同 {@code :33 register(14, Casing_Firebox_Steel)}（<b>m15 钨钢禁用</b>）</td>
 * </tr>
 * <tr>
 * <td>{@code v}</td>
 * <td>{@code gt.blockglass1} m10</td>
 * <td>防爆玻璃</td>
 * <td>{@code BlockGlass1.java:43 register(10, ReinforcedGlass)}（beta-2/3 口径，与
 * {@code GTVersionCompat} 的 else 分支同源）；块对象 = 新键 {@link CityBlockResolver#K_GT_GLASS}</td>
 * </tr>
 * </table>
 * <b>禁面（本表结构上写不出来，因此不需要"记得别写"）</b>：{@code gt.blockmachines} 任意 meta
 * （GTSR 全部 MTE 寄居块，{@code BlockMachines.hasTileEntity} 恒 true）、一切
 * {@code ITileEntityProvider}/{@code BlockContainer}、{@code casings4} 系、{@code casings2:14-15}、
 * {@code casings3:15}、{@code casings5}、钛/钨钢 frames——这些键压根不在
 * {@code CityBlockResolver} 的解析表里（resolve 落空 ⇒ 跳过），meta 红线则由
 * {@code RuinFamilyCheck} 的 COLOSSUS 组逐格钉（含 RED 注入探针，证明判据咬得住）。
 * <p>
 * <b>用户点名而对不上的一项</b>（任务包 item 3"对不上的点名不存在"）：
 * <b>「太阳能破损结构金属块」在 GT5U/gtsr 的注册面上没有对应标识</b>——取证件 B8 的
 * 资源面盘点里最接近的是 {@code gt.blockmetal1..10}（金属块，meta=材质 id）与
 * {@code gt.blockframes}（框架，meta=材质 id），但两者都<b>给不出"太阳能"这一档的注册名与 meta 证据</b>
 * （金属块/框架的 meta 需要 {@code Materials.*.id} 才能定位，且帧块的高阶档在禁面上），
 * 故本表不引、也不臆造方块名。需要这一观感时只能另取证再扩档。
 * <p>
 * 零 Minecraft 依赖（char → String 键，与 {@link RuinedMachineShapes} 同范式），类加载即跑
 * {@link #CONTRACT} 成对契约（fail fast：分片越界、申报 bbox 与并集不符、单片空转都直接抛）。
 */
public final class RuinedColossusShapes {

    /** 族名前缀（名册与断言据此分桶；与 {@code outpost_} / {@code ruin_} 同一命名纪律）。 */
    public static final String NAME_PREFIX = "colossus_";

    /** 一条跨片巨构：形状 + <b>显式申报</b>的跨 chunk 数 + 由申报派生的分片表。 */
    public static final class Colossus {

        public final String name;
        /**
         * 机器族候选池里的那份形状（sizeX/sizeY/sizeZ = 申报的总 bbox）。
         * <p>
         * <b>P16-B2 之后它的角色 = 谱系母体（蓝图），不再是世界渲染的那份东西</b>：世界里落的是
         * {@link #wrecks} 里的一条残骸档（{@link #morphAt} 选档）。母体字节<b>一格未改</b>，
         * 于是 ① {@code RosterIntegrityCheck} 的逐名模板 SHA 与 ② {@code OutpostTemplateCheck}
         * 的成对断言臂①②（分片 ≤16×16×12 / 申报总 bbox == 分片实心并集）都沿用 B1 的读数，
         * 既不需要重钉、也没有"把臂②改宽松"这回事（见 {@link Wreck} 类注释的对照申报）。
         */
        public final RuinedMachineShapes.Shape shape;
        /** 显式申报："本巨构横向跨 N 个 chunk / 纵向跨 M 个 chunk"（G7 第二条的被检对象）。 */
        public final int declaredChunksX;
        public final int declaredChunksZ;
        /** 由 {@link ChunkSpans#slice} 派生的实心分片（判据与生产共用同一份几何）。 */
        public final ChunkSpans.Slice[] slices;
        // ══════════════ 以下四组量都是 P16-B2（形态层）新增 ══════════════
        /** 本条在 {@link #ALL} 里的序号（形态层派生盐的位移量，不写第二份真值）。 */
        public final int index;
        /** 半埋深度上界（层）：{@link #morphAt} 在 {@code 0..maxBury} 上均匀取一档，0 = 全露。 */
        public final int maxBury;
        /** 派生残骸档（世界渲染的就是它们）；长度 = {@link #WRECK_COUNT}。 */
        public final Wreck[] wrecks;
        /** 母体的逐层实心/机械件计数（G9 对照与 G8④ 露出比的分母出处）。 */
        public final int motherSolid;
        public final int motherMachine;
        public final int[] motherLayerSolid;
        public final int[] motherLayerMachine;

        Colossus(RuinedMachineShapes.Shape shape, int declaredChunksX, int declaredChunksZ, int index, int maxBury) {
            this.shape = shape;
            this.name = shape.name;
            this.index = index;
            this.maxBury = maxBury;
            this.declaredChunksX = declaredChunksX;
            this.declaredChunksZ = declaredChunksZ;
            this.slices = ChunkSpans.slice(shape.layers, shape.sizeX, shape.sizeY, shape.sizeZ);
            final int[] census = layerCensus(cellsOf(shape));
            this.motherLayerSolid = new int[shape.sizeY];
            this.motherLayerMachine = new int[shape.sizeY];
            System.arraycopy(census, 0, this.motherLayerSolid, 0, shape.sizeY);
            System.arraycopy(census, shape.sizeY, this.motherLayerMachine, 0, shape.sizeY);
            this.motherSolid = sum(this.motherLayerSolid, 0);
            this.motherMachine = sum(this.motherLayerMachine, 0);
            this.wrecks = new Wreck[WRECK_COUNT];
            for (int i = 0; i < WRECK_COUNT; i++) {
                this.wrecks[i] = deriveWreck(this, i);
            }
        }

        /** 分片实际覆盖的 chunk 数（X 轴 / Z 轴）。 */
        public int coveredChunksX() {
            return ChunkSpans.distinctChunkOffsets(this.slices, true);
        }

        public int coveredChunksZ() {
            return ChunkSpans.distinctChunkOffsets(this.slices, false);
        }

        public int sizeX() {
            return this.shape.sizeX;
        }

        public int sizeY() {
            return this.shape.sizeY;
        }

        public int sizeZ() {
            return this.shape.sizeZ;
        }

        /**
         * 本条<b>可能落到世界里</b>的全部 (键,meta) 集合 = 母体 ∪ 每一条派生残骸档
         * （<b>P16-B2</b>：从"只看母体"扩成"母体 + wreck"，于是 {@code RuinFamilyCheck} 的 K2a
         * 允许表钉的是真正落出去的那批材质，而不是蓝图那一半。扩集合只会让判据更严，
         * 且 wreck 的降级目标字符全部取自既有记号族 ⇒ 没有新增任何方块）。
         */
        public Set<String> usedKeyMetas() {
            final Set<String> out = new LinkedHashSet<>();
            collectKeyMetas(out, cellsOf(this.shape));
            for (final Wreck w : this.wrecks) {
                collectKeyMetas(out, w.cells);
            }
            return out;
        }

        /** 实心格数（与 {@code RuinShapes#solidChars} 同一货币；申报行用它量"这是一座结构而不是一片渣"）。 */
        public int solidChars() {
            int n = 0;
            for (final ChunkSpans.Slice s : this.slices) {
                n += s.solidChars;
            }
            return n;
        }
    }

    /**
     * 一条<b>派生残骸档</b>（<b>P16-B2</b>，plan §0 U2「形态上微微能看出结构、本质上像残骸」+ §1 G8/G9）：
     * 母体字符盘经四步形态算子（{@link #blight} 侵蚀降级 → {@link #scour} 逐列削顶 → {@link #settle}
     * 承力塌落 → {@link #spill} 落渣成堆）定盘后的<b>那一座会落到世界里的东西</b>。
     * <p>
     * ═══ 与母体的关系（为什么世界的渲染对象不是名册里那份 SHA）═══
     * 母体 = 「还立着时候的蓝图」，它进 {@code StructureRegistry}、被 {@code RosterIntegrityCheck}
     * 逐名 SHA 钉死、被 {@code /gtsr structure} 指令原样摆放（玩家/验收用它看几何）；
     * 残骸档 = 蓝图 + 形态算子的纯函数结果，类加载期定盘、与 ruin 族同一纪律
     * （{@code RuinShapes} 的在册 layers 也是 {@link RuinDamageOps} 的纯函数结果）。
     * 二者都是<b>零新随机</b>：形态算子的每一次掷骰都走 {@link RuinDamageOps#roll}
     * （框架唯一件 {@code GTSRWorldgenHash.cellSeed} + {@code splitmix64}）+ 本族自己的形态盐。
     * <p>
     * ═══ 与 B1 成对断言的关系（G7 不破 + G8 的新判据面）═══
     * 形态算子里只有两类动作：<b>换字符</b>（blight，实心格数不变）与<b>将非承力格挪到该列堆顶</b>
     * （settle，位移不是删除：落点始终紧贴堆顶 ⇒ 逐列自下向上连续）与 <b>削掉顶上几层</b>
     * （scour，唯一的删除，且永不吃光一个分片）。于是：
     * <ol>
     * <li>{@code 每个分片 ≤16×16×12}（臂①）对残骸档同样成立——分片只切 X/Z，Y 不切；</li>
     * <li>{@code 申报总 bbox == 分片实心并集}（臂②）仍打在母体上、读数一字未动；残骸档这一层
     * 钉的是"不出框 + 母体覆盖到的每个 chunk 都仍有实心"（{@link #slices} 数与母体同）。
     * <b>这不是把臂②放宽</b>：申报值没改、被检对象（总 bbox）没改，只是额外为"实际渲染的那份"
     * 补一条"不得越过申报框"的下界——残骸只会比蓝图小，永远不可能比申报值大，
     * 所以跨片覆盖让行（按申报 bbox 判）永远偏保守，不会把结构插进别人的地盘；</li>
     * <li>G8②「不产生悬浮块」由 {@link #settle} 的构造性质给出：<b>任一实心格要么在 y=0，
     * 要么其下方格同为实心</b>（逐列连续），故半埋截断后（只从底部切掉若干层）该性质仍成立。</li>
     * </ol>
     */
    public static final class Wreck {

        public final int index;
        /** 形态档标签（申报行用；不是真值，真值是下面那四个算子）。 */
        public final String label;
        /** 本档的定盘盐（= {@code SALT_WRECK + index * 131}，派生可复算的输入）。 */
        public final long salt;
        public final int sizeX;
        public final int sizeY;
        public final int sizeZ;
        /** 工作栅格 {@code [y][z][x]}，y=0 = 底层（与 {@link RuinDamageOps} 的派生中间态同形）。 */
        final char[][][] cells;
        public final ChunkSpans.Slice[] slices;
        public final int solid;
        /** 机械件格数（{@link #MACHINE_CHARS} 那一组记号的格数；G9 的分子）。 */
        public final int machine;
        public final int[] layerSolid;
        public final int[] layerMachine;

        Wreck(int index, String label, long salt, char[][][] cells, RuinedMachineShapes.Shape mother,
            int motherSolidChars) {
            this.index = index;
            this.label = label;
            this.salt = salt;
            this.cells = cells;
            this.sizeY = cells.length;
            this.sizeZ = cells[0].length;
            this.sizeX = cells[0][0].length;
            final String[][] layers = freeze(cells);
            this.slices = ChunkSpans.slice(layers, this.sizeX, this.sizeY, this.sizeZ);
            final int[] census = layerCensus(cells);
            this.layerSolid = new int[this.sizeY];
            this.layerMachine = new int[this.sizeY];
            System.arraycopy(census, 0, this.layerSolid, 0, this.sizeY);
            System.arraycopy(census, this.sizeY, this.layerMachine, 0, this.sizeY);
            this.solid = sum(this.layerSolid, 0);
            this.machine = sum(this.layerMachine, 0);
            // 反"把整座磨成粉"：残骸档必须还是一座结构（母体实心格的 45% 下界，与 ruin 族 solid>=24 同族纪律）
            if (this.solid * 100 < motherSolidChars * 45) {
                throw new IllegalStateException(
                    "[GTSR] colossus wreck " + label
                        + " 塌得只剩 "
                        + this.solid
                        + " 格 < 母体 "
                        + motherSolidChars
                        + " 的 45%");
            }
            // 承力连续性的构造性质自检（坏数据不许静默进世界）：任一实心格要么 y=0、要么下方也是实心
            for (int y = 1; y < this.sizeY; y++) {
                for (int z = 0; z < this.sizeZ; z++) {
                    for (int x = 0; x < this.sizeX; x++) {
                        if (ChunkSpans.isSolid(cells[y][z][x]) && !ChunkSpans.isSolid(cells[y - 1][z][x])) {
                            throw new IllegalStateException(
                                "[GTSR] colossus wreck " + label
                                    + " 有悬浮格 @"
                                    + x
                                    + ","
                                    + y
                                    + ","
                                    + z
                                    + " ch="
                                    + cells[y][z][x]);
                        }
                    }
                }
            }
            for (final ChunkSpans.Slice s : this.slices) {
                if (!s.withinSliceLimits() || s.solidChars <= 0) {
                    throw new IllegalStateException(
                        "[GTSR] colossus wreck " + label + " 分片越界或空转 " + s.chunkDx + "," + s.chunkDz);
                }
            }
        }

        public char charAt(int y, int dx, int dz) {
            return this.cells[y][dz][dx];
        }

        /** 从 {@code fromY}（含）往上还有多少实心格 / 其中多少是机械件（G8④ 露出比的分子分母）。 */
        public int exposedSolid(int fromY) {
            return sum(this.layerSolid, fromY);
        }

        public int exposedMachine(int fromY) {
            return sum(this.layerMachine, fromY);
        }

        /** 本档的分片实心格总数（与 {@link #solid} 必等——settle 只做同列位移，不跨分片搬材质）。 */
        public int sliceSolidSum() {
            int n = 0;
            for (final ChunkSpans.Slice s : this.slices) {
                n += s.solidChars;
            }
            return n;
        }
    }

    /**
     * 某一次放置选定的形态：一条残骸档 + 一个半埋深度。
     * <p>
     * 它是 {@code (morphSeed, 世界原点)} 的<b>纯函数</b>产物（{@link #morphAt}），所以锚点槽与每一个
     * 邻槽各自重放都会得到<b>同一份</b>——这是"跨 chunk 分片不能各画一种形态"的机关；
     * 也是 B1 留给本片的 {@link ChunkSpans#anchorFreeBlocks()} 抖动位的落点：那 0..15 的自由格
     * 不再只是"结构摆在 chunk 哪一列"，它同时决定这一座埋多深、烂到哪一档。
     */
    public static final class Morph {

        public final Colossus colossus;
        public final Wreck wreck;
        /** 半埋深度（层）：模板的 {@code y < bury} 那几层由地形覆压，不落块。 */
        public final int bury;

        public Morph(Colossus colossus, Wreck wreck, int bury) {
            this.colossus = colossus;
            this.wreck = wreck;
            this.bury = bury;
        }
    }

    // ═════════════════════ P16-B2 形态层参数（必须在 ALL 之前定盘：构造期就要读）═════════════════════

    /**
     * 机械件记号集（G9 的分子口径，全仓唯一一份；断言与生产都读它）。
     * 这七枚都是 GT5U 静态外壳/玻璃；<b>其余</b>在册记号（三层壳族 {@code # @ %}、
     * 本维残件 {@code d p r c}、地表 {@code s}、原版 {@code S C G B}）一律计入"破碎与自然件"。
     */
    public static final String MACHINE_CHARS = "XZnNeEfFv";

    /** 形态档数（浅残/中残/深残/重残四档；{@link #ALL} 的每条巨构各派生这么多个 wreck）。 */
    public static final int WRECK_COUNT = 4;

    /** 四档形态参数（下标 = wreck 档号）。 */
    private static final String[] WRECK_LABEL = { "light", "medium", "deep", "heavy" };
    /** 侵蚀基准百分比（y=0 那一层把多少比例的机械件降级）。 */
    private static final int[] BLIGHT_BASE = { 52, 62, 70, 78 };
    /** 侵蚀随高度的递增幅度（顶部那一层再多吃这么多个百分点；越靠上越先锈穿）。 */
    private static final int[] BLIGHT_SPAN = { 26, 22, 18, 12 };
    /** 落渣百分比（空窝里有下方承托时补散料的概率；越靠底越密）。 */
    private static final int[] SPILL_PCT = { 10, 16, 22, 28 };
    /** 削顶档数（逐列最多再啃掉几层）。 */
    private static final int[] SCOUR_LEVELS = { 2, 3, 5, 7 };

    /** 形态档派生盐 "WREK"（族隔离；与机器族那把 {@code SALT_MACHINE} 不是同一用途）。 */
    private static final long SALT_WRECK = 0x5752454BL;
    /** 锚点形态选择盐 "MORP"（族隔离；只服务 {@link #morphAt} 那两次纯哈希掷骰）。 */
    private static final long SALT_MORPH = 0x4D4F5250L;

    /** 百分比域（与 {@link RuinDamageOps} 同一"百分数"词汇，不另造单位）。 */
    private static final int PCT = 100;

    /**
     * "塌过头"的下界：同一件事在 ruin 族已经有先例（{@code RuinShapes:149} 的
     * "derived silhouette too thin (solid&lt;24)"，"派生结果必须还是一座结构"），本族沿用<b>同一个量级</b>
     * 而不是另立一个看起来更严的数；再叠一条"至少还剩四分之一质量"，因为本族的分母是 600~1000 格。
     */
    private static final int RUIN_SILHOUETTE_MIN = 24;

    /**
     * ① 枢纽阵列残架 24×12×16（横向跨 2 chunk）：西立 9×9 枢纽塔（镀铜砖壳 + 固体钢角柱 +
     * 燃烧室排 + 两层齿轮箱环 + 积碳炉膛），一道管廊（青铜/钢管道）连到东段半塌的 9×16 副座，
     * 副座顶已削平、碎瓷与积碳混在渣堆里。
     */
    public static final Colossus HUB_ARRAY = buildHubArray();

    /**
     * ② 锅炉大厅残壳 20×12×20（X/Z 各跨 2 chunk ⇒ 4 分片）：一周镀铜砖承重壳 + 固体钢角柱、
     * 西墙两排燃烧室、中跨防爆玻璃天窗（碎一半）、传动平台上青铜/钢管道与齿轮箱成对，
     * 东北角整体塌失（只留断口锯齿）。
     */
    public static final Colossus BOILER_HALL = buildBoilerHall();

    /** 本表全部机型（并进机器族候选池；顺序 = 候选池尾部追加，不参与任何概率计算）。 */
    public static final Colossus[] ALL = { HUB_ARRAY, BOILER_HALL };

    /** 全表最大跨度（块）——{@link RuinedMachinePlacer} 邻槽回扫窗的唯一派生出处。 */
    public static final int MAX_SPAN_BLOCKS = maxSpanBlocks();

    /** 邻槽回扫格数（单侧）：由 {@link #MAX_SPAN_BLOCKS} 与原点抖动窗派生，不写死。 */
    public static final int BACK_REACH_CHUNKS = ChunkSpans.reachBack(MAX_SPAN_BLOCKS);

    private static int maxSpanBlocks() {
        int m = 0;
        for (final Colossus c : ALL) {
            m = Math.max(m, Math.max(c.sizeX(), c.sizeZ()));
        }
        return m;
    }

    /**
     * 成对契约（fail fast，与 {@code RuinShapes:149-162} 同一"坏数据不许静默进世界"的纪律）：
     * 每条都要 ① 每个分片 {@code ≤16×16×12}、② 申报总 bbox == 各分片实心并集、
     * ③ 申报跨 chunk 数 == 实得分片数且至少跨 2 个 chunk、④ 每个分片都有实心格。
     * 同一条判据在 {@code tools/dim1/OutpostTemplateCheck} 以断言形态再跑一遍（那边是验收，这边是防线）。
     */
    static {
        for (final Colossus c : ALL) {
            if (!c.name.startsWith(NAME_PREFIX)) {
                throw new IllegalStateException("[GTSR] colossus name without family prefix: " + c.name);
            }
            if (c.shape.layers.length != c.sizeY()) {
                throw new IllegalStateException("[GTSR] colossus " + c.name + ": layer count != sizeY");
            }
            for (final ChunkSpans.Slice s : c.slices) {
                if (!s.withinSliceLimits() || !s.usedInsideSelf()) {
                    throw new IllegalStateException(
                        "[GTSR] colossus " + c.name
                            + ": slice "
                            + s.chunkDx
                            + ","
                            + s.chunkDz
                            + " breaches the per-slice limit "
                            + s.sizeX
                            + "x"
                            + s.sizeZ
                            + "x"
                            + s.sizeY
                            + " limits="
                            + s.withinSliceLimits()
                            + " inside="
                            + s.usedInsideSelf()
                            + " min="
                            + s.minX
                            + ","
                            + s.minZ
                            + " used="
                            + s.usedMinX
                            + ".."
                            + s.usedMaxX
                            + "/"
                            + s.usedMinY
                            + ".."
                            + s.usedMaxY
                            + "/"
                            + s.usedMinZ
                            + ".."
                            + s.usedMaxZ);
                }
                if (s.solidChars <= 0) {
                    throw new IllegalStateException("[GTSR] colossus " + c.name + ": empty slice declared");
                }
            }
            if (!ChunkSpans.unionEqualsBox(c.slices, c.sizeX(), c.sizeY(), c.sizeZ())) {
                throw new IllegalStateException(
                    "[GTSR] colossus " + c.name
                        + ": declared bbox "
                        + c.sizeX()
                        + "x"
                        + c.sizeY()
                        + "x"
                        + c.sizeZ()
                        + " != union of slices "
                        + java.util.Arrays.toString(ChunkSpans.unionBounds(c.slices)));
            }
            final int spanX = ChunkSpans.chunksAcross(c.sizeX());
            final int spanZ = ChunkSpans.chunksAcross(c.sizeZ());
            if (c.declaredChunksX != spanX || c.declaredChunksZ != spanZ
                || c.coveredChunksX() != spanX
                || c.coveredChunksZ() != spanZ) {
                throw new IllegalStateException(
                    "[GTSR] colossus " + c.name
                        + ": declared span "
                        + c.declaredChunksX
                        + "x"
                        + c.declaredChunksZ
                        + " vs grid span "
                        + spanX
                        + "x"
                        + spanZ
                        + " vs covered "
                        + c.coveredChunksX()
                        + "x"
                        + c.coveredChunksZ());
            }
            if (spanX * spanZ < 2) {
                throw new IllegalStateException("[GTSR] colossus " + c.name + " 不跨 chunk，不该待在这张表里");
            }
            for (int y = 0; y < c.sizeY(); y++) {
                for (int dz = 0; dz < c.sizeZ(); dz++) {
                    if (c.shape.layers[y][dz].length() != c.sizeX()) {
                        throw new IllegalStateException(
                            "[GTSR] colossus " + c.name + ": row length != sizeX @" + y + "," + dz);
                    }
                }
            }
            // ═══ P16-B2 形态层契约（G8/G9 的 fail-fast 面；同一批判据在 tools/dim1 里以断言形态再跑一遍）═══
            if (c.maxBury < 1 || c.maxBury >= c.sizeY() / 2) {
                throw new IllegalStateException(
                    "[GTSR] colossus " + c.name + ": maxBury " + c.maxBury + " 不在 1.." + (c.sizeY() / 2 - 1));
            }
            if (c.wrecks.length != WRECK_COUNT) {
                throw new IllegalStateException(
                    "[GTSR] colossus " + c.name + ": wreck 数 " + c.wrecks.length + " != " + WRECK_COUNT);
            }
            for (final Wreck w : c.wrecks) {
                // G9：机械件必须是少数（< 一半，且比母体显著下降）；这里是"进了世界就必须成立"的那道闸
                if (w.machine * 2 >= w.solid) {
                    throw new IllegalStateException(
                        "[GTSR] colossus " + c.name + '/' + w.label + ": 机械件不是少数 " + w.machine + "/" + w.solid);
                }
                if (w.machine >= c.motherMachine) {
                    throw new IllegalStateException(
                        "[GTSR] colossus " + c.name
                            + '/'
                            + w.label
                            + ": 侵蚀没减少机械件 "
                            + w.machine
                            + " >= 母体 "
                            + c.motherMachine);
                }
                // ═══ G8④「半埋深度与可读性联动」的机检形式 ═══
                // 判据用<b>机械件露出比</b> = 还看得见的机械件 / 这座残骸原有的机械件总数
                // （用户原话"埋得越深，机械件露出比例越低"最直接的那个读法），它在 d ∈ 0..maxBury
                // 上必须不升，且在<b>真的半埋</b>那几档（d ≥ 1）上必须严格下降，最deep档至少埋掉一半。
                // 另一个读法（露出部分里机械件的<b>占比</b>）由 tools/dim1 那侧整表报出，不在这里立判据，
                // 理由是几何事实而非可调参数：本族 y=0 是整层碎瓷壳垫层（机器族"垫层恒放"既有纪律），
                // 它是全表最大的一块自然质量，埋掉它的第一层会让"露出部分的机械占比"反而抬头。
                int prevExposed = -1;
                for (int d = 0; d <= c.maxBury; d++) {
                    final int total = w.exposedSolid(d);
                    final int mach = w.exposedMachine(d);
                    if (total < RUIN_SILHOUETTE_MIN || total * 6 < w.solid) {
                        throw new IllegalStateException(
                            "[GTSR] colossus " + c.name
                                + '/'
                                + w.label
                                + " 埋 "
                                + d
                                + " 层后只剩 "
                                + total
                                + "/"
                                + w.solid
                                + " 格（塌过头，G8①『还是一座结构』不成立：下界借 ruin 族既有"
                                + " silhouette 契约的 "
                                + RUIN_SILHOUETTE_MIN
                                + " 格，另加"
                                + "『至少还剩六分之一质量』一条）");
                    }
                    if (prevExposed >= 0 && mach > prevExposed) {
                        throw new IllegalStateException(
                            "[GTSR] colossus " + c.name
                                + '/'
                                + w.label
                                + ": 埋深 "
                                + d
                                + " 还有 "
                                + mach
                                + " 格机械件露着，比前一档 "
                                + prevExposed
                                + " 多（G8④ 联动被破坏）");
                    }
                    // 半埋档之间（d ≥ 2）必须每档都真吃掉机芯；d=0→1 只判不升——
                    // 因为本族 y=0 是整层垫层（机器族"垫层恒放"纪律），它一颗机芯都没有。
                    if (d >= 2 && mach >= prevExposed) {
                        throw new IllegalStateException(
                            "[GTSR] colossus " + c.name
                                + '/'
                                + w.label
                                + ": 埋深 "
                                + d
                                + " 的机芯露出数没比前一档（"
                                + prevExposed
                                + "）少 ⇒ 半埋没吃掉任何机芯（G8④ 空转）");
                    }
                    prevExposed = mach;
                }
                if (w.exposedMachine(c.maxBury) * 3 >= w.machine * 2) {
                    throw new IllegalStateException(
                        "[GTSR] colossus " + c.name
                            + '/'
                            + w.label
                            + ": 埋到 maxBury="
                            + c.maxBury
                            + " 还剩 "
                            + w.exposedMachine(c.maxBury)
                            + "/"
                            + w.machine
                            + " 机芯露在外面（埋深不够：要求至多露 2/3）");
                }
                // 分片拼合：每片都要有东西，否则"跨 chunk 成型"在最深那一档会退化成鬼影
                if (w.slices.length != c.slices.length) {
                    throw new IllegalStateException(
                        "[GTSR] colossus " + c.name
                            + '/'
                            + w.label
                            + ": wreck 分片数 "
                            + w.slices.length
                            + " != 母体 "
                            + c.slices.length);
                }
            }
        }
    }

    private RuinedColossusShapes() {}

    /** 名字是否属于本表（{@link RuinedMachinePlacer} 的记号分流谓词，全仓唯一一份）。 */
    public static boolean isColossus(String name) {
        for (final Colossus c : ALL) {
            if (c.name.equals(name)) {
                return true;
            }
        }
        return false;
    }

    public static Colossus byName(String name) {
        for (final Colossus c : ALL) {
            if (c.name.equals(name)) {
                return c;
            }
        }
        return null;
    }

    /** 本表全部 (键,meta) 的使用集（{@code RuinFamilyCheck} 的红线输入；工具不自持第二张表）。 */
    public static Set<String> allUsedKeyMetas() {
        final Set<String> out = new LinkedHashSet<>();
        for (final Colossus c : ALL) {
            out.addAll(c.usedKeyMetas());
        }
        return Collections.unmodifiableSet(out);
    }

    /** 本表实际用到的字符（升序，供断言逐字符检查记号可解析与 meta 域；含<b>派生残骸档</b>新增的记号）。 */
    public static String usedChars() {
        final Set<Character> seen = new LinkedHashSet<>();
        for (final Colossus c : ALL) {
            for (int y = 0; y < c.sizeY(); y++) {
                for (final String row : c.shape.layers[y]) {
                    for (final char ch : row.toCharArray()) {
                        seen.add(ch);
                    }
                }
            }
            for (final Wreck w : c.wrecks) {
                for (final char[][] layer : w.cells) {
                    for (final char[] row : layer) {
                        for (final char ch : row) {
                            seen.add(ch);
                        }
                    }
                }
            }
        }
        final StringBuilder b = new StringBuilder();
        for (final char ch : "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz_%@#.".toCharArray()) {
            if (seen.contains(ch)) {
                b.append(ch);
            }
        }
        return b.toString();
    }

    // ══════════════════════════════ char → (键, meta) ══════════════════════════════

    /**
     * 本族记号 → 方块键：七枚 GT 壳记号在本方法落定，其余<b>逐字委托</b>
     * {@link CityVariants#blockKeyOf(char)}（共用记号不抄第二份）。'.'/' ' 与未登记记号返回 null。
     */
    public static String blockKeyOf(char c) {
        switch (c) {
            case 'n':
            case 'N':
            case 'e':
            case 'E':
                // 管道 / 齿轮箱：与 'Z' 同一个注册块对象（sBlockCasings2），只有 meta 不同 ⇒ 不扩键
                return CityVariants.K_GT_STEEL;
            case 'f':
            case 'F':
                return CityBlockResolver.K_GT_FIREBOX;
            case 'v':
                return CityBlockResolver.K_GT_GLASS;
            default:
                return CityVariants.blockKeyOf(c);
        }
    }

    /** 与 {@link #blockKeyOf(char)} 配对的 meta；未登记记号同样委托 {@link CityVariants#metaOf(char)}。 */
    public static int metaOf(char c) {
        switch (c) {
            case 'n':
                return 12; // Casing_Pipe_Bronze
            case 'N':
                return 13; // Casing_Pipe_Steel
            case 'e':
                return 2; // Casing_Gearbox_Bronze
            case 'E':
                return 3; // Casing_Gearbox_Steel
            case 'f':
                return 13; // Casing_Firebox_Bronze
            case 'F':
                return 14; // Casing_Firebox_Steel
            case 'v':
                return 10; // ReinforcedGlass（beta-2/3 的 sBlockGlass1 meta10）
            default:
                return CityVariants.metaOf(c);
        }
    }

    // ══════════════════ P16-B2 形态层：母体 → 残骸档（plan §0 U2 / §1 G8·G9）══════════════════
    //
    // 四步算子，全部是 (母体字符盘, 本档定盘盐) 的纯函数，类加载期定盘、零 MC 依赖：
    // blight 侵蚀降级 → scour 削顶 → settle 承力塌落 → spill 落渣成堆。
    // 每一次掷骰都是 {@link RuinDamageOps#roll}（框架唯一件 cellSeed + splitmix64 的那一份实现体，
    // P16-B2 起为 public），盐 = 本族自己的 {@link #SALT_WRECK}；<b>没有 new Random、没有第二个随机源</b>
    // （任务包 B2 的硬约束；机器族那一条 {@code roll()} 消费序一字未动，见 RuinedMachinePlacer）。
    // 允许的字符只从既有记号族里取（母体七枚 GT 件 → 三层壳族 / 本维残件 / 原版石砾），
    // <b>不新增任何方块</b> ⇒ RuinFamilyCheck 的允许表（K2a）一格都不用放宽。

    /**
     * 母体 → 第 {@code index} 档残骸。四步算子的顺序是真判据的一部分（先降级再塌落，塌下来的件
     * 已经带着散料身份，堆在地上的才是渣而不是半台机器）。
     */
    private static Wreck deriveWreck(Colossus owner, int index) {
        final RuinedMachineShapes.Shape mother = owner.shape;
        final int sx = mother.sizeX;
        final int sy = mother.sizeY;
        final int sz = mother.sizeZ;
        final long salt = SALT_WRECK + owner.index * 131L + index;
        final char[][][] g = cellsOf(mother);
        blight(g, sx, sy, sz, salt, BLIGHT_BASE[index], BLIGHT_SPAN[index]);
        scour(g, sx, sy, sz, salt, SCOUR_LEVELS[index]);
        settle(g, sx, sy, sz);
        spill(g, sx, sy, sz, salt, SPILL_PCT[index]);
        return new Wreck(index, owner.name + '/' + WRECK_LABEL[index], salt, g, mother, owner.motherSolid);
    }

    /**
     * <b>侵蚀降级</b>：把机械件记号按位置哈希折成本维壳族 / 残件 / 原版石砾——"机器被岁月还原成壳"。
     * 概率随高度递增（顶先锈穿，埋在下方的机芯反而留得住），于是<b>埋得越深、露出来的机械件越少</b>
     * 是这条斜坡的直接推论（G8④ 的成因，不是事后修饰）。
     * 只换字符、不动格位 ⇒ 实心格数与分片覆盖完全不变（B1 的成对断言读数是同一份）。
     */
    private static void blight(char[][][] g, int sx, int sy, int sz, long salt, int base, int span) {
        for (int y = 0; y < sy; y++) {
            final int pct = Math.min(92, base + span * y / Math.max(1, sy - 1));
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    final char c = g[y][z][x];
                    if (MACHINE_CHARS.indexOf(c) < 0) {
                        continue;
                    }
                    // 朝天的那一面再加一刀——但只加在<b>上半部</b>：露在外面的顶面先锈穿，
                    // 而贴地的机芯（炉膛/管排/齿轮箱，正是"微微能看出结构"的那批件）被自重与渣堆盖着，
                    // 保留率高。这一条同时把"逐层机械件占比"整成随高度递减的坡，
                    // 于是 G8④ 的另一个读法（露出部分里机械件的占比）也随埋深一起降。
                    final int q = y * 2 >= sy && y + 1 < sy && !ChunkSpans.isSolid(g[y + 1][z][x])
                        ? Math.min(95, pct + 22)
                        : pct;
                    if (RuinDamageOps.roll(salt, x * 3L + y * 17L + z * 29L, 71, PCT) >= q) {
                        continue;
                    }
                    g[y][z][x] = rustOf(c, (int) RuinDamageOps.roll(salt, x * 11L + z * 5L + y * 7L, 73, PCT));
                }
            }
        }
    }

    /**
     * 一枚机械件降级成什么散件（每档候选全部是既有记号，语义按材质就近：
     * 管道→锈管、齿轮箱→铆接板/积碳、燃烧室→积碳壳、玻璃→碎瓷、砖壳→圆石/碎瓷、钢壳→锈壳/轨枕）。
     */
    private static char rustOf(char machine, int pick) {
        final int q = pick / 25; // 0..3
        switch (machine) {
            case 'X':
                return new char[] { 'C', '%', 'C', '#' }[q];
            case 'Z':
                return new char[] { '#', 'd', 'C', 'p' }[q];
            case 'n':
            case 'N':
                return new char[] { 'p', 'p', 'r', '%' }[q];
            case 'e':
            case 'E':
                return new char[] { 'r', '@', 'd', '#' }[q];
            case 'f':
            case 'F':
                return new char[] { '@', '@', '%', '#' }[q];
            case 'v':
                return new char[] { '%', '%', 'G', '#' }[q];
            default:
                return machine;
        }
    }

    /**
     * <b>削顶</b>：逐列从自己的最高实心往下啃一个随机深度（最多 {@code levels} 层），啃掉的格清空。
     * 只有这一步会<b>减少</b>材质，且只在列顶——所以"申报总 bbox 的六个面由谁撑住"这件事不受影响
     * （底面 y=0 一层都不许啃：与机器族/{@code RuinDamageOps} 的"垫层恒放"同一纪律）。
     */
    private static void scour(char[][][] g, int sx, int sy, int sz, long salt, int levels) {
        for (int z = 0; z < sz; z++) {
            for (int x = 0; x < sx; x++) {
                int top = -1;
                for (int y = sy - 1; y >= 1; y--) {
                    if (ChunkSpans.isSolid(g[y][z][x])) {
                        top = y;
                        break;
                    }
                }
                if (top < 1) {
                    continue; // 没有可啃的上部（y=0 垫层恒留）
                }
                final int bite = 1 + (int) RuinDamageOps.roll(salt, x * 13L + z * 7L, 79, levels);
                for (int y = top; y > top - bite && y >= 1; y--) {
                    g[y][z][x] = ' ';
                }
            }
        }
    }

    /**
     * <b>承力塌落</b>（G8②「不产生悬浮块」的构造性来源）：任一列只保留"自 y=0 起连续的那一段"，
     * 悬在断口之上的部件按重力落到<b>本列</b>断口处，堆不下的部分（列已满）才真的消失。
     * <ul>
     * <li>同列位移 ⇒ 每个 chunk 分片的实心数不变 ⇒ 分片覆盖与 B1 的拼合对账同一份几何；</li>
     * <li>只做"上件下移"，且落点下方必为实心（或 y=0）⇒ 结果<b>逐列连续</b>，
     * 于是半埋截断（只从底部切掉若干层）之后仍然逐列连续 ⇒ 悬浮块恒 0；</li>
     * <li>不引入任何掷骰（纯重力），因此这一条与形态档无关、也不会让世界读数依赖迭代顺序。</li>
     * </ul>
     */
    private static void settle(char[][][] g, int sx, int sy, int sz) {
        for (int z = 0; z < sz; z++) {
            for (int x = 0; x < sx; x++) {
                int cursor = 0;
                while (cursor < sy && ChunkSpans.isSolid(g[cursor][z][x])) {
                    cursor++;
                }
                if (cursor >= sy) {
                    continue; // 整列本来就是连续的
                }
                for (int y = cursor + 1; y < sy; y++) {
                    final char c = g[y][z][x];
                    if (!ChunkSpans.isSolid(c)) {
                        continue;
                    }
                    g[y][z][x] = ' ';
                    g[cursor][z][x] = c;
                    cursor++;
                }
            }
        }
    }

    /**
     * <b>落渣成堆</b>：在"下方已实心 + 四周已有至少两件实心"的窝里按哈希补散料（只增格，不删不移），
     * 概率随高度递减（渣堆从底下堆起来），低层还会掺进本维地表 {@code s}——让巨构的底缘与地形接得上，
     * 半埋时看不出"被切了一刀"。加进去的每一格下方都是实心 ⇒ 逐列连续性质不变。
     */
    private static void spill(char[][][] g, int sx, int sy, int sz, long salt, int pct) {
        final char[] drift = { 'G', '%', 'C', 'd', 's' };
        for (int y = 0; y < sy; y++) {
            final int q = pct * (2 * sy - y) / (2 * sy);
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    if (ChunkSpans.isSolid(g[y][z][x]) || (y > 0 && !ChunkSpans.isSolid(g[y - 1][z][x]))) {
                        continue; // 只往"下方承得住"的空窝里落
                    }
                    if (neighbours(g, sx, sy, sz, x, y, z) < 2) {
                        continue;
                    }
                    final long h = RuinDamageOps.roll(salt, x * 29L + z * 11L + y * 3L, 83, PCT);
                    if (h >= q) {
                        continue;
                    }
                    final int pick = (int) RuinDamageOps.roll(salt, x * 5L + z * 23L + y, 89, drift.length);
                    // 地表记号只贴在最低两层上（再高就是"空中一片草皮"，那是装饰层的事）
                    g[y][z][x] = drift[pick] == 's' && y > 1 ? 'G' : drift[pick];
                }
            }
        }
    }

    /** 六个正邻方向里已有几格实心（落渣要求"落在东西堆里"，不是凭空长出来）。 */
    private static int neighbours(char[][][] g, int sx, int sy, int sz, int x, int y, int z) {
        int n = 0;
        if (x > 0 && ChunkSpans.isSolid(g[y][z][x - 1])) {
            n++;
        }
        if (x < sx - 1 && ChunkSpans.isSolid(g[y][z][x + 1])) {
            n++;
        }
        if (z > 0 && ChunkSpans.isSolid(g[y][z - 1][x])) {
            n++;
        }
        if (z < sz - 1 && ChunkSpans.isSolid(g[y][z + 1][x])) {
            n++;
        }
        if (y > 0 && ChunkSpans.isSolid(g[y - 1][z][x])) {
            n++;
        }
        if (y < sy - 1 && ChunkSpans.isSolid(g[y + 1][z][x])) {
            n++;
        }
        return n;
    }

    /**
     * 锚点 → 形态（<b>P16-B2 的"锚点自由格打通半埋/侵蚀"就落在这一个方法上</b>）。
     * <p>
     * 入参是巨构的<b>世界原点</b>，而那个原点的低 4 位正是 {@link ChunkSpans#anchorFreeBlocks()}
     * 那扇 0..15 抖动窗掷出来的结果（{@code RuinedMachinePlacer.roll} 的
     * {@code r.nextInt(freeX + 1)}）——本方法把它连同 chunk 号一起喂进 {@link RuinDamageOps#roll}
     * 的唯一实现体，于是同一扇抖动窗现在同时决定三件事：结构压在邻槽哪一侧、这一座烂到哪一档、
     * 埋进地里几层。抖动位不再只是"位置噪声"，B1 留的那一位空转就此闭合。
     * <p>
     * <b>跨 chunk 一致</b>：返回值只依赖 {@code (morphSeed, 世界原点)}，而这两者对锚点槽与每一个邻槽
     * 都是同一份纯重放（{@code renderForeignSpans} 与 {@code placeAll} 各自都从同一份
     * {@code roll} 出发），所以"同一座巨构在四个 chunk 里长成四种样子"在类型上就不可表示。
     * 两次掷骰换了 {@code (a,b)} 的先后与符号 ⇒ 形态档与埋深彼此解耦（否则同 hash 会造出
     * "埋得深的必然烂得狠"这种假相关）。
     *
     * @param morphSeed populate 侧 = worldSeed；{@code /gtsr structure} 侧不使用本方法（摆的是母体蓝图）
     */
    public static Morph morphAt(long morphSeed, int originX, int originZ, Colossus c) {
        final long salt = morphSeed ^ SALT_MORPH;
        final int variant = (int) RuinDamageOps.roll(salt, originX, originZ, WRECK_COUNT);
        final int bury = (int) RuinDamageOps.roll(salt, originZ, 1 - originX, c.maxBury + 1);
        return new Morph(c, c.wrecks[variant], bury);
    }

    /** {@link #morphAt} 的母体档（埋深 0 + 未派生的蓝图）——S5 指令与几何对账用。 */
    public static Morph blueprintMorph(Colossus c) {
        return new Morph(c, null, 0);
    }

    /** 逐列计数：返回 {@code int[2*sizeY]}，前半是每层实心格数、后半是每层机械件格数。 */
    private static int[] layerCensus(char[][][] g) {
        final int sy = g.length;
        final int sz = g[0].length;
        final int sx = g[0][0].length;
        final int[] out = new int[sy * 2];
        for (int y = 0; y < sy; y++) {
            int s = 0;
            int m = 0;
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    final char c = g[y][z][x];
                    if (!ChunkSpans.isSolid(c)) {
                        continue;
                    }
                    s++;
                    if (MACHINE_CHARS.indexOf(c) >= 0) {
                        m++;
                    }
                }
            }
            out[y] = s;
            out[sy + y] = m;
        }
        return out;
    }

    private static int sum(int[] a, int from) {
        int n = 0;
        for (int y = Math.max(0, from); y < a.length; y++) {
            n += a[y];
        }
        return n;
    }

    /** 母体形状 → 工作栅格 {@code [y][z][x]}（y=0 = 底层）。 */
    private static char[][][] cellsOf(RuinedMachineShapes.Shape s) {
        final char[][][] g = new char[s.sizeY][s.sizeZ][s.sizeX];
        for (int y = 0; y < s.sizeY; y++) {
            for (int z = 0; z < s.sizeZ; z++) {
                for (int x = 0; x < s.sizeX; x++) {
                    g[y][z][x] = s.charAt(y, x, z);
                }
            }
        }
        return g;
    }

    /** 一份字符盘用到的 (键,meta) 收进集合（{@code '.'}/{@code ' '} 与未登记记号跳过）。 */
    private static void collectKeyMetas(Set<String> out, char[][][] g) {
        for (final char[][] layer : g) {
            for (final char[] row : layer) {
                for (final char c : row) {
                    final String key = blockKeyOf(c);
                    if (key != null) {
                        out.add(key + ":" + metaOf(c));
                    }
                }
            }
        }
    }

    // ══════════════════════════════ 形状装配（纯数据，无 MC） ══════════════════════════════

    /** ① 枢纽阵列残架 24×12×16。 */
    private static Colossus buildHubArray() {
        final int sx = 24;
        final int sy = 12;
        final int sz = 16;
        final char[][][] g = grid(sx, sy, sz);
        // ── 地坪（y=0）：西塔基 9×9 + 东副座基 9×16 + 连系带；碎瓷壳，机器族 y=0 恒不缺失 ──
        rect(g, 0, 8, 0, 0, 3, 11, '%');
        rect(g, 15, 23, 0, 0, 0, 15, '%');
        rect(g, 9, 14, 0, 0, 6, 9, '%');
        // ── 西塔：x0..8 / z3..11 / y1..11，八层镀铜砖壳 + 四角固体钢 ──
        tower(g, 0, 8, 3, 11, 1, 7, 'X', 'Z', true);
        // 燃烧室排（南墙 z=3 内侧，y1..2）与积碳炉膛（y=1 中心 3×3）
        rect(g, 1, 7, 1, 2, 3, 3, 'f');
        rect(g, 3, 5, 1, 1, 6, 8, '@');
        // 两层齿轮箱环（青铜/钢各一层，环在内壁）
        ring(g, 1, 7, 4, 4, 10, 'e');
        ring(g, 1, 7, 7, 4, 10, 'E');
        // 防爆玻璃：东壁（x=8）带两条观察窗；南壁 y=6 一条
        col(g, 8, 5, 6, 5, 'v');
        col(g, 8, 5, 6, 9, 'v');
        rect(g, 3, 5, 6, 6, 3, 3, 'v');
        // 塔顶残段 y8..11：只有西半立着，最高残柱到 y=11（申报 bbox 的顶面由它撑住）
        tower(g, 0, 4, 3, 11, 8, 9, 'X', 'Z', false);
        tower(g, 0, 2, 3, 5, 10, 10, '@', 'Z', false);
        col(g, 0, 10, 11, 3, 'Z');
        col(g, 2, 11, 11, 6, 'X');
        // ── 连廊管排：x9..14 / z7..8 / y5..6（青铜管在上、钢管在下）+ 一支固体钢墩 ──
        rect(g, 9, 14, 5, 5, 7, 8, 'N');
        rect(g, 9, 14, 6, 6, 7, 8, 'n');
        col(g, 12, 1, 4, 7, 'Z');
        // ── 东副座：x15..23 / z0..15 / y1..5，顶已削平、东南塌口 ──
        tower(g, 15, 23, 0, 15, 1, 4, 'X', 'Z', true);
        rect(g, 16, 22, 1, 1, 1, 14, 'F');
        ring(g, 16, 22, 3, 1, 14, 'N');
        col(g, 23, 5, 5, 0, '@');
        // 塌口：东壁上半清空气（'.' = 内腔清空，与机型族同一记号），残梁斜到 y=5
        carve(g, 21, 23, 3, 5, 0, 3);
        rect(g, 19, 23, 5, 5, 12, 15, 'X');
        // 渣堆：副座内三处碎瓷 + 积碳（让"机械件是少数"有落点，比例调制的形态部分归 B2）
        rect(g, 17, 18, 2, 2, 5, 6, '%');
        rect(g, 20, 20, 2, 2, 9, 10, '@');
        return new Colossus(new RuinedMachineShapes.Shape("colossus_hub_array", sx, sy, sz, freeze(g)), 2, 1, 0, 3);
    }

    /** ② 锅炉大厅残壳 20×12×20。 */
    private static Colossus buildBoilerHall() {
        final int sx = 20;
        final int sy = 12;
        final int sz = 20;
        final char[][][] g = grid(sx, sy, sz);
        // ── 地坪（y=0）：一圈承重带（四边都吃满 ⇒ 申报 bbox 的 X/Z 面由它撑住；内腔让行地形）──
        ring(g, 0, 19, 0, 0, 19, '%');
        // ── 承重壳 y1..8：四壁镀铜砖 + 四角固体钢 + 内壁每 3 格一根钢肋 ──
        tower(g, 0, 19, 0, 19, 1, 8, 'X', 'Z', true);
        for (int y = 1; y <= 8; y++) {
            for (int z = 3; z <= 17; z += 4) {
                set(g, 0, y, z, 'Z');
                set(g, 19, y, z, 'Z');
            }
            for (int x = 3; x <= 17; x += 4) {
                set(g, x, y, 0, 'Z');
                set(g, x, y, 19, 'Z');
            }
        }
        // ── 西墙两排燃烧室（青铜 y1..3 / 钢 y4..5）──
        rect(g, 0, 0, 1, 3, 6, 13, 'f');
        rect(g, 0, 0, 4, 5, 6, 13, 'F');
        // ── 传动平台：y=6 一层台面板（铆接板记号 'r'）+ 中心齿轮箱环 + 积碳轴心 ──
        rect(g, 4, 15, 6, 6, 4, 15, 'r');
        ring(g, 7, 12, 7, 7, 12, 'e');
        ring(g, 8, 11, 7, 8, 11, 'E');
        rect(g, 9, 10, 7, 9, 9, 10, '@');
        // ── 管排：青铜管沿 z=2 走、钢管沿 z=17 走，各自架在固体钢支墩上 ──
        rect(g, 2, 17, 4, 4, 2, 2, 'n');
        rect(g, 2, 17, 4, 4, 17, 17, 'N');
        for (int x = 4; x <= 16; x += 6) {
            col(g, x, 1, 3, 2, 'Z');
            col(g, x, 1, 3, 17, 'Z');
        }
        // ── 防爆玻璃天窗：中跨 y=8 一整片（碎一半 ⇒ 隔格清空气）──
        for (int x = 6; x <= 13; x++) {
            for (int z = 6; z <= 13; z++) {
                set(g, x, 8, z, (x + z) % 3 == 0 ? '.' : 'v');
            }
        }
        // ── 残顶与断口：y9..11 只留西南一角，最高残柱到 y=11 ──
        rect(g, 0, 5, 9, 9, 14, 19, 'X');
        tower(g, 0, 3, 16, 19, 10, 10, '@', 'Z', false);
        col(g, 0, 11, 11, 19, 'Z');
        col(g, 3, 11, 11, 16, 'X');
        // ── 东北整体塌失：清到 y=7 以上（申报 bbox 的东面/北面仍由地坪与残梁撑住）──
        carve(g, 12, 19, 7, 11, 12, 19);
        rect(g, 18, 19, 5, 7, 0, 4, 'X');
        return new Colossus(new RuinedMachineShapes.Shape("colossus_boiler_hall", sx, sy, sz, freeze(g)), 2, 2, 1, 4);
    }

    // ══════════════════════════════ 字符盘工具件 ══════════════════════════════

    private static char[][][] grid(int sizeX, int sizeY, int sizeZ) {
        final char[][][] g = new char[sizeY][sizeZ][sizeX];
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                java.util.Arrays.fill(g[y][z], ' ');
            }
        }
        return g;
    }

    /** 顶层 = {@code layers[0]} 的层序换算（{@code charAt(y,dx,dz) = layers[sizeY-1-y][dz][dx]}）。 */
    private static String[][] freeze(char[][][] g) {
        final int sizeY = g.length;
        final int sizeZ = g[0].length;
        final String[][] layers = new String[sizeY][sizeZ];
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                layers[sizeY - 1 - y][z] = new String(g[y][z]);
            }
        }
        return layers;
    }

    private static void set(char[][][] g, int x, int y, int z, char c) {
        g[y][z][x] = c;
    }

    private static boolean inRange(char[][][] g, int x, int y, int z) {
        return y >= 0 && y < g.length && z >= 0 && z < g[0].length && x >= 0 && x < g[0][0].length;
    }

    /** 实心长方体（闭区间）。 */
    private static void rect(char[][][] g, int x0, int x1, int y0, int y1, int z0, int z1, char c) {
        for (int y = y0; y <= y1; y++) {
            for (int z = z0; z <= z1; z++) {
                for (int x = x0; x <= x1; x++) {
                    if (inRange(g, x, y, z)) {
                        set(g, x, y, z, c);
                    }
                }
            }
        }
    }

    /** 空心长方体壳（壁 = wall，内腔 = {@code '.'} 清空气；{@code inner=false} 时内腔留"不触碰"）。 */
    private static void tower(char[][][] g, int x0, int x1, int z0, int z1, int y0, int y1, char wall, char corner,
        boolean hollowInside) {
        for (int y = y0; y <= y1; y++) {
            for (int z = z0; z <= z1; z++) {
                for (int x = x0; x <= x1; x++) {
                    final boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                    if (!edge) {
                        if (hollowInside && y > y0) {
                            set(g, x, y, z, '.');
                        }
                        continue;
                    }
                    final boolean cornerCell = (x == x0 || x == x1) && (z == z0 || z == z1);
                    set(g, x, y, z, cornerCell ? corner : wall);
                }
            }
        }
    }

    /** 单层矩形环（壁厚 1 格）。 */
    private static void ring(char[][][] g, int x0, int x1, int y, int z0, int z1, char c) {
        rect(g, x0, x1, y, y, z0, z0, c);
        rect(g, x0, x1, y, y, z1, z1, c);
        rect(g, x0, x0, y, y, z0, z1, c);
        rect(g, x1, x1, y, y, z0, z1, c);
    }

    /** 竖向柱（同一 (x,z) 上 y0..y1）。 */
    private static void col(char[][][] g, int x, int y0, int y1, int z, char c) {
        rect(g, x, x, y0, y1, z, z, c);
    }

    /** 塌口：把一片范围清成"不触碰"（{@code ' '}，让行地形；与 {@code '.'} 的"清空气"区分开）。 */
    private static void carve(char[][][] g, int x0, int x1, int y0, int y1, int z0, int z1) {
        for (int y = y0; y <= y1; y++) {
            for (int z = z0; z <= z1; z++) {
                for (int x = x0; x <= x1; x++) {
                    if (inRange(g, x, y, z)) {
                        set(g, x, y, z, ' ');
                    }
                }
            }
        }
    }

    /**
     * 申报行（{@code tools/dim1} 的 COLOSSUS 组打印）。<b>P16-B2</b> 起每条巨构先报母体、
     * 再逐档报残骸（bbox/分片/实心/机械件占比），G9 的"实测百分比"就是这几行读数。
     */
    public static List<String> describe() {
        final List<String> out = new ArrayList<>();
        for (final Colossus c : ALL) {
            out.add(
                c.name + " 母体 bbox="
                    + c.sizeX()
                    + "x"
                    + c.sizeY()
                    + "x"
                    + c.sizeZ()
                    + " spanChunks="
                    + c.declaredChunksX
                    + "x"
                    + c.declaredChunksZ
                    + "(covered "
                    + c.coveredChunksX()
                    + "x"
                    + c.coveredChunksZ()
                    + ")"
                    + " slices="
                    + c.slices.length
                    + " solid="
                    + c.solidChars()
                    + " machine="
                    + c.motherMachine
                    + "("
                    + pct(c.motherMachine, c.motherSolid)
                    + "%)"
                    + " keys="
                    + c.usedKeyMetas());
            for (final Wreck w : c.wrecks) {
                out.add(
                    "  " + c.name
                        + '#'
                        + w.index
                        + " ["
                        + w.label
                        + "] salt="
                        + Long.toHexString(w.salt)
                        + " ops=blight("
                        + BLIGHT_BASE[w.index]
                        + '+'
                        + BLIGHT_SPAN[w.index]
                        + ")/scour("
                        + SCOUR_LEVELS[w.index]
                        + ")/settle/spill("
                        + SPILL_PCT[w.index]
                        + ")"
                        + " slices="
                        + w.slices.length
                        + " solid="
                        + w.solid
                        + "("
                        + pct(w.solid, c.motherSolid)
                        + "% of 母体)"
                        + " machine="
                        + w.machine
                        + "("
                        + pct(w.machine, w.solid)
                        + "%)"
                        + " 埋深→机芯露出="
                        + exposureRow(c, w));
            }
        }
        return out;
    }

    /**
     * "埋深 → 机械件露出比"那一行（G8④ 的读数面）。两件事一起报：
     * {@code 露出数/机芯总数}（判据立在这条上，越埋越小）与露出部分里机械件的<b>占比</b>
     * （受 y=0 垫层影响，见 {@link Colossus} 静态契约里的说明，只报不判）。
     */
    private static String exposureRow(Colossus c, Wreck w) {
        final StringBuilder b = new StringBuilder();
        for (int d = 0; d <= c.maxBury; d++) {
            if (d > 0) {
                b.append(" ");
            }
            b.append('d')
                .append(d)
                .append('=')
                .append(pct(w.exposedMachine(d), w.machine))
                .append("/")
                .append(pct(w.exposedMachine(d), w.exposedSolid(d)))
                .append('%');
        }
        return b.toString();
    }

    private static String pct(int part, int total) {
        return total <= 0 ? "0.0" : String.format(java.util.Locale.ROOT, "%.1f", 100.0d * part / total);
    }

    /** 本表<b>依赖 GT 字段</b>的键（形态侧唯一一份；给 {@code RuinedMachinePlacer} 的缺材质告警读）。 */
    public static String[] gtDependentKeys() {
        return new String[] { CityVariants.K_GT_BRONZE, CityVariants.K_GT_STEEL, CityBlockResolver.K_GT_FIREBOX,
            CityBlockResolver.K_GT_GLASS };
    }
}
