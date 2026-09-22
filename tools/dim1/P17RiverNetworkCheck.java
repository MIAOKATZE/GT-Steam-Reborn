import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.block.BlockDynamicLiquid;
import net.minecraft.block.BlockStaticLiquid;
import net.minecraft.block.BlockStone;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ObjectIntIdentityMap;
import net.minecraft.util.RegistryNamespaced;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.WorldInfo;

import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.framework.GTSRDimensionDef;
import com.miaokatze.gtsr.common.dimension.framework.GTSRWorldChunkManager;
import com.miaokatze.gtsr.common.dimension.framework.genlayer.GTSRGenLayerRosterFace;
import com.miaokatze.gtsr.common.dimension.prosperity.ChunkProviderProsperityRuins;
import com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile;
import com.miaokatze.gtsr.common.dimension.prosperity.entity.EntityGearPigeon;
import com.miaokatze.gtsr.common.dimension.prosperity.river.GTSRRiverNetwork;

/**
 * <b>P17 S-C / S-C2 机检：dim78 河流水系（河网密度按群系 / 断面成层 / 水不侧向外溢 / 照明与流体置位 /
 * 齿轮鸽 {@code isAnyLiquid} 连带面 / 确定性）</b>。需求原话：「增加河流」+
 * 「沼泽则是中等树，但<b>河网密集</b>，地势最平坦」。
 *
 * <p>
 * ═══ 八组断言（全部实跑，无一条是"引用注释"）═══
 * <ul>
 * <li><b>A 档表本体</b>：两张档表长 4、带宽与过水档的偏序（沼泽最大 / 荒漠过水档 = 0 且带宽最小 /
 * 森林 = 草原取中档）、默认档 = 0（身份不可得 ⇒ 与改造前同形）、断面常数自洽（下切 2 填 1、水在床正上）、
 * 同族平行水道不相交、几何闸的三例界形；</li>
 * <li><b>B 群系密度偏序（纯模型，跨成片尺度取窗）</b>：钉「沼泽水列 &gt; 森林 = 草原 &gt; 荒漠 = 0」
 * 与「荒漠仍成干谷」，并给「河网密集」的两种可验读数——<b>每 chunk 水系格数分布</b>与
 * <b>河间距分布</b>（到最近成道列 / 到最近水列的轴向距离）；</li>
 * <li><b>C 真实运行断面</b>：走<b>生产</b> {@code provideChunk}（真实地形 + 真实表层链 + 真实 Chunk
 * 组装 + 真实天光）与<b>生产</b> {@code populate → onPopulate → GTSRRiverPlacer}，逐格核
 * 水↔床↔空气成层、四同层可流入性，钉「populate 之前整窗零液体」这条源级红线的运行态形式，
 * 并钉「断面用的解析 H 与该列 populate 前的真实列顶逐列相等」（H 不是第二真值）；
 * 荒漠窗另钉「零水 / 有干谷」；</li>
 * <li><b>D 水不侧向外溢</b>：把世界里每一格水都强制叫醒一次（{@code onNeighborBlockChange} ⇒
 * 换成流水 + 排 tick），再按真实 {@code BlockDynamicLiquid.updateTick} 收敛；断言源水逐格复位 meta 0、
 * 河道外零新增液体；<b>活闸对照</b>另造一片"同层四向敞开"的断面，同一驱动必须真的平铺外溢并出现
 * meta&gt;0 的流水邻格 ⇒ 证明前面的"零外溢"不是恒真；</li>
 * <li><b>E 照明与流体置位</b>（闭合 P17-B 那条 {@code [未实测]}）：两条路各测一次——
 * ① P17-B 原设想那条（水写进裸 {@code Block[]} 后 {@code new Chunk(...)+generateSkylightMap()}）；
 * ② 本片实际走的 populate 路（{@code World.setBlock}）。两条路的水格天光都是 15、都不排任何流体 tick
 * ⇒ <b>provider 阶段无需也不应额外置位</b>；同时逐格打印 Sky/Block 光与 {@code canBlockSeeTheSky}
 * 的实测表，并钉「照明回调真的被走过」（0 就意味着桩吞掉了被测面）；</li>
 * <li><b>F 源级红线</b>：水只可能从 populate 通道出现（全仓 {@code Blocks.water} 写点 = 落块器一处；
 * {@code Blocks.flowing_water} 与 {@code extends BlockLiquid} = 0）；河道模型层零 {@code net.minecraft}
 * 依赖、零方块读取；分流不按地表方块比选；零身份等值判断；不新建随机源；不自建噪声核；
 * 身份只走 S-A 那一条 coarse 面；挂点唯一；<b>不新增方块</b>；沼泽四元组仍是固体；</li>
 * <li><b>G 齿轮鸽连带面</b>：{@code EntityGearPigeon.getCanSpawnHere} 的五段谓词（含
 * {@code isAnyLiquid}）在四个身份档各自的真实窗口上 before/after 复算，钉「放水后地面门已过却被液体
 * 拒绝的列 = 0」「每档刷出面绝对数 &gt; 0」「≥ 3 档保留率 ≥ 80%」；</li>
 * <li><b>H 确定性与幂等</b>：同 seed 两次独立运行摘要逐位一致（摘要含方块类 + meta + 天光 + 方块光），
 * 对同一批 chunk 连跑两次生产 populate 摘要不变，换 seed 必换河道，且换后的 seed 自身仍确定。</li>
 * </ul>
 *
 * <p>
 * <b>用法</b>：{@code java P17RiverNetworkCheck [srcRoot=src/main/java]}（F 组要读源码文本；args[0]
 * 缺省时自动向上找 {@code src/main/java}，找不到只跳过 F 组的文本断言并打 NOTE）。
 * 纯 JDK + 离线装配（复用 {@link SurfaceHarness} 的方块/名册装配口径 + 同款 id 注入）。退出码 0 = 全绿。
 *
 * <p>
 * ═══ S-C2 收口记录（本文件由 S-C 的中断现场收尾而来；<b>没有一条放宽容差或删断言</b>）═══
 * <ol>
 * <li><b>修编译</b>（S-C 那份 13 个 {@code javac} 错、从未跑起来）：{@code 0x…4C1A_L} 下划线紧邻 L
 * 后缀非法；两处中文串里嵌了 ASCII 双引号；{@code Dense.waterTotal()}、{@code RunWorld.digestSafe()/
 * digestSafe2()}、{@code hex(MessageDigest)} 四个被调用但从未定义的方法；{@code RunWorld} 里赋给不存在的
 * 字段 {@code world.providerForChunks}（真名 {@code chunkGen}）；</li>
 * <li><b>修装配</b>：① {@code RiverWorld} 原先调 {@code World} 五参构造器，而该构造器第一句就是
 * {@code saveHandler.loadWorldInfo()} ⇒ 离线必 NPE；改与 {@code temp/p17-sc/src/RiverProbe.java}
 * 同款的 {@code allocateInstance}。② {@code bootstrap()} 只给原版方块注 id，<b>没有给
 * {@code BlocksGTSR} 的自有方块注 id</b>——1.7.10 {@code ExtendedBlockStorage} 存 13-bit 方块 id，
 * 未注册方块 {@code getIdFromBlock = -1}（实测），于是真实 Chunk 里河床料与全部本维表层读回都是 air
 * ⇒ C/G 组的"真实断面"整段失真。现按 {@code BlocksGTSR} 的公开静态字段逐个补注 id（400 起）。
 * ③ 群系链种子原先来自 {@code SurfaceHarness.manager}（它钉的是 harness 自己的 SEED），
 * 与河道/高度用的世界 seed 不同源 ⇒ B/C/G 的"同一段算式"不成立；改为按本世界 seed 建
 * {@code GTSRWorldChunkManager}。</li>
 * <li><b>修恒真</b>：① E3 原先直调 {@code provider.populate(...)}，{@code inPopulate} 窗口从未打开
 * ⇒ 计数恒 0；现走 {@code world.populateNow(...)}。② F3–F9 的"符号不得出现"判定原先跑在<b>未剥注释</b>
 * 的源码文本上，会被自身 javadoc 打红（{@code GTSRRiverNetwork} 的 javadoc 含 {@code GTSRWorldgenHash}
 * ⇒ 命中禁词 "World"）；现统一先 {@code stripComments}。③ {@code F3} 的禁词 {@code "World"} 改为
 * {@code "net.minecraft"}（原判据把"零 World 依赖"表达成了一个会命中类名的子串；换成包名后判定面
 * <b>更严</b>：模型层连一个 MC 类型都不许 import）。④ H4 原先比较两个<b>从未生成</b>的世界
 * （两边都是空 Chunk ⇒ 恒等），现改为两个真 generate+populate 的世界。⑤ D3 原先数"液面格数变化"，
 * 但 1.7.10 源水被叫醒后换成的流水<b>仍是液体</b> ⇒ 该读数恒不变；改数"仍是静态源水的格数 + 排出的
 * tick 数"。</li>
 * <li><b>修事实错</b>：D5 原写作「对照断面的<b>源格</b> meta&gt;0」——1.7.10 的降档 meta 落在<b>被灌出的
 * 邻格</b>上，源格保持 meta 0（{@code BlockLiquid} 语义），原判据在任何实现下都不可能为真。现按真实
 * 语义钉「对照区出现 meta&gt;0 的流水邻格」，测的还是同一件事（唤醒会改状态），且围合断面 D1 仍要求
 * 逐格 meta 0。A10 是我在收口时新加的界形断言，第一版三例的期望值写反（已按
 * {@code holdsWater} 的真实充分条件重列，见该方法注释）。</li>
 * <li><b>修取窗</b>：S-A 之后群系<b>成片</b>（平均连通域 148.3 chunk），而 S-C 那份 B 组窗是
 * 6×6 <b>连续</b> chunk、C 组窗是 3×3 连续 chunk ⇒ 单窗内只会命中一个身份档，B1/B3/B4/B6 这类
 * "四档偏序"判据在正确的实现下也必然红（实测 {@code channel=[435,0,0,0]}）。B 组改为跨成片尺度的
 * <b>步进取窗</b>（每边 8 个、间隔 96 chunk），C/G 组改为<b>按身份档各挑一个真实窗</b>（沼泽窗做断面
 * 与流体面，荒漠窗单独钉"零水 + 有干谷"）。</li>
 * <li><b>补交付</b>：河间距分布、每 chunk 水系格数分布、C8 的 H 对拍、E 组逐格照明实测表、
 * G 组分身份档保留率、世界摘要扩到含 meta 与天光。S-C 写下的阈值数字（[8,120]、[1,140]、80% 等）
 * 原样保留。</li>
 * </ol>
 */
public final class P17RiverNetworkCheck {

    /** 本片世界种子（与兄弟片不同值，免得摘要互串）。 */
    static final long SEED = 0x5249_7665_4C1AL;

    /**
     * B 组分层取窗：每个身份档各挑 {@value #SAMPLES_PER_TIER} 个"整块同档"的 chunk。
     * <b>为什么不能像 S-C 那样拿一片连续/步进窗</b>：S-A 之后群系<b>成片</b>（平均连通域 148.3
     * chunk），一片 6×6 或 8×8 的窗只会落在少数几个档上（实测 {@code channel=[0,42,19,200]} ⇒
     * "四档偏序"类判据在任何正确实现下都必然红）。分层挑窗让四档的分母严格相等，偏序才是可比的。
     */
    static final int SAMPLES_PER_TIER = 40;
    /** 分层挑窗用的确定性格序扫描上限（每个档最多试这么多原点）。 */
    static final int SAMPLE_CANDIDATES = 40000;

    /** C/D/E/G 组每个身份档窗的边长（chunk）。 */
    static final int RUN_CHUNKS = 3;

    /** 挑档窗用的确定性候选原点个数与步长（chunk）。 */
    static final int WINDOW_CANDIDATES = 220;
    static final int WINDOW_STRIDE = 7;

    /**
     * 观察 y 域：覆盖到 {@code MIN_HEIGHT(40) - CUT_DEPTH(2)} 与 {@code MAX_HEIGHT(110) + 1} 之外，
     * 不留"水在窗外看不见"的死角。
     */
    static final int Y0 = 35;
    static final int Y1 = 112;

    /** 河间距扫描半径（格）；超出记 censored，不当 0 用。 */
    static final int SPACING_RADIUS = 120;
    static final int SPACING_SAMPLES = 150;

    /** 名册下标（0 锈蚀草原 / 1 齿轮森林 / 2 黄铜荒漠 / 3 起雾沼泽）。 */
    static final String[] ROSTER = { "草原", "森林", "荒漠", "沼泽" };

    static int assertions;
    static int failures;
    static final List<String> LINES = new ArrayList<String>();

    /** 身份面 memo（coarse 面 1:4，与 {@code GTSRGenLayerChain.biomeAtCoarse} 的 {@code >>2} 同格口径）。 */
    static final Map<Long, Integer> TIER_MEMO = new HashMap<Long, Integer>();

    /** 四个身份档各一个真实运行窗（索引 = 名册下标）。 */
    static RunWorld[] win;

    public static void main(String[] args) throws Exception {
        quietLogging();
        bootstrap();
        final String srcRoot = args.length > 0 ? args[0] : autoSrcRoot();
        groupA();
        groupB();
        win = buildWindows();
        for (int t = 0; t < 4; t++) {
            say("WIN 档" + t + "(" + ROSTER[t] + ") 窗原点 chunk=(" + win[t].baseCx + "," + win[t].baseCz
                + ") 方块域 x∈[" + (win[t].baseCx << 4) + "," + ((win[t].baseCx + RUN_CHUNKS) << 4)
                + ") 生成期液体=" + win[t].liquidPre + " 生成期河床料=" + win[t].gravelPre);
        }
        groupC(win[3]);
        groupD(win[3]);
        groupE(win[3]);
        groupF(srcRoot);
        groupG();
        groupH(win[3]);
        report();
    }

    // ══════════════════════════════════ A 档表本体 ══════════════════════════════════

    static void groupA() {
        final float[] band = GTSRRiverNetwork.RIVER_BAND_BY_ROSTER;
        final float[] wet = GTSRRiverNetwork.RIVER_WET_BY_ROSTER;
        check("A1 两张档表长度 = 名册成员数 4", band.length == 4 && wet.length == 4,
            "band=" + band.length + " wet=" + wet.length);
        check("A2 带宽档偏序：沼泽唯一最大且严格 > 森林=草原 > 荒漠 > 0",
            band[3] > band[1] && band[1] == band[0] && band[1] > band[2] && band[2] > 0.0F,
            "band=" + Arrays.toString(band));
        check("A3 过水档：沼泽=1.0（凡成道皆过水）、森林=草原=中间档、荒漠=0（只留干谷）",
            wet[3] >= 1.0F && wet[0] == wet[1] && wet[0] > 0.0F && wet[0] < 1.0F && wet[2] == 0.0F,
            "wet=" + Arrays.toString(wet));
        check("A4 默认档全为 0（身份不可得 ⇒ 一条河道都不成 = 改造前形态，与 S-A 振幅档 1.0/S-D 埋深档 0.0 同纪律）",
            GTSRRiverNetwork.DEFAULT_RIVER_BAND == 0.0D && GTSRRiverNetwork.DEFAULT_RIVER_WET == 0.0D
                && GTSRRiverNetwork.bandForRosterIndex(-1) == 0.0D
                && GTSRRiverNetwork.wetForRosterIndex(-1) == 0.0D,
            "band(-1)=" + GTSRRiverNetwork.bandForRosterIndex(-1) + " wet(-1)="
                + GTSRRiverNetwork.wetForRosterIndex(-1));
        check("A5 越界名册下标一律默认档（[-9,-1,4,99] 四组都 0）",
            GTSRRiverNetwork.bandForRosterIndex(-9) == 0.0D && GTSRRiverNetwork.wetForRosterIndex(-9) == 0.0D
                && GTSRRiverNetwork.bandForRosterIndex(4) == 0.0D
                && GTSRRiverNetwork.wetForRosterIndex(99) == 0.0D,
            "越界回退非默认档");
        check("A6 断面常数自洽：下切 2 = 空气 1 + 水 1，且水在床料正上方、床料在水正下方",
            GTSRRiverNetwork.CUT_DEPTH == 2 && GTSRRiverNetwork.WATER_DROP == 1
                && GTSRRiverNetwork.BED_DROP == 2 && GTSRRiverNetwork.waterY(70) == 69
                && GTSRRiverNetwork.bedY(70) == 68 && GTSRRiverNetwork.airY(70) == 70
                && GTSRRiverNetwork.bedY(70) == GTSRRiverNetwork.waterY(70) - 1,
            "断面常数被改动");
        check("A7 干谷断面：床料在 H-1（谷底仍是固体，不放水），且比过水床料高 1 格",
            GTSRRiverNetwork.dryBedY(70) == GTSRRiverNetwork.airY(70) - 1
                && GTSRRiverNetwork.DRY_BED_DROP == 1 && GTSRRiverNetwork.dryBedY(70) == 69
                && GTSRRiverNetwork.dryBedY(70) == GTSRRiverNetwork.bedY(70) + 1,
            "dryBed=" + GTSRRiverNetwork.dryBedY(70));
        // 摆动幅度界：平行线不交叉（2×摆动 + 2×最大半宽 必须小于最小间距）
        double maxBand = band[0];
        for (final float b : band) {
            maxBand = Math.max(maxBand, b);
        }
        final double worst = 2.0D * GTSRRiverNetwork.MEANDER_AMPLITUDE
            + 2.0D * GTSRRiverNetwork.BASE_HALF_WIDTH * maxBand;
        check("A8 同族相邻平行水道必不相交（2×摆动 + 2×最大半宽 < 两族最小间距）",
            worst < Math.min(GTSRRiverNetwork.SPACING_EW, GTSRRiverNetwork.SPACING_NS),
            "worst=" + worst + " minSpacing=" + Math.min(GTSRRiverNetwork.SPACING_EW,
                GTSRRiverNetwork.SPACING_NS));
        check("A9 两族间距不同值（避免交点成规则网格）",
            GTSRRiverNetwork.SPACING_EW != GTSRRiverNetwork.SPACING_NS,
            "EW=" + GTSRRiverNetwork.SPACING_EW + " NS=" + GTSRRiverNetwork.SPACING_NS);
        // 几何闸三例界形（充分条件本体，见 GTSRRiverNetwork#holdsWater）：
        //  · 邻列也是河道 ⇒ 它的地表那格被清空，要求 H(nb) >= H(self)，低 1 格就拒；
        //  · 邻列是岸 ⇒ 固体顶在 H(nb)，只要求 H(nb) >= H(self)-1，低 2 格才拒。
        final int ch = GTSRRiverNetwork.MASK_CHANNEL_EW;
        check("A10 几何闸界形三例：河道邻列低 1 格即拒 / 岸列低 1 格仍留 / 岸列低 2 格拒",
            !GTSRRiverNetwork.holdsWater(70, new int[] { 69, 70, 70, 70 }, new int[] { ch, 0, 0, 0 })
                && GTSRRiverNetwork.holdsWater(70, new int[] { 69, 70, 70, 70 }, new int[] { 0, 0, 0, 0 })
                && !GTSRRiverNetwork.holdsWater(70, new int[] { 68, 70, 70, 70 }, new int[] { 0, 0, 0, 0 })
                && GTSRRiverNetwork.holdsWater(70, new int[] { 71, 70, 70, 70 }, new int[] { ch, 0, 0, 0 }),
            "几何闸边界行为漂移");
    }

    // ══════════════════════ B 群系密度与河间距（纯模型，跨成片尺度取窗） ══════════════════════

    /** 每档：通道列 / 水列 / 含该档的 chunk 数 / 每 chunk 列数 min·max。 */
    static final class Dense {

        final long[] channel = new long[4];
        final long[] wet = new long[4];
        final long[] tierChunks = new long[4];
        final int[] chanMin = { -1, -1, -1, -1 };
        final int[] chanMax = new int[4];
        final int[] wetMin = { -1, -1, -1, -1 };
        final int[] wetMax = new int[4];
        /** 该档里「过水位被置上」的列数（荒漠档必须为 0 —— 抑制位真的只落在水上）。 */
        final long[] wetBit = new long[4];
        /** 该档的最大连续成道列数（沿 ±x 与 ±z 两向，含跨族交点）——真正的"河宽"。 */
        final int[] runMax = new int[4];
        long maskMismatch;
        long cols;
        long scannedChunks;

        long waterTotal() {
            return this.wet[0] + this.wet[1] + this.wet[2] + this.wet[3];
        }
    }

    static Dense scan() {
        final Dense d = new Dense();
        final int[] nbY = new int[4];
        final int[] nbMask = new int[4];
        final int[][] origins = sampleOrigins();
        for (int[] o : origins) {
            {
                final int bcx = o[0];
                final int bcz = o[1];
                d.scannedChunks++;
                final int[] ch = new int[4];
                final int[] wc = new int[4];
                final boolean[] seen = new boolean[4];
                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        final int x = (bcx << 4) + lx;
                        final int z = ((bcz + 0) << 4) + lz;
                        final int t = tier(SEED, x, z);
                        if (t < 0 || t > 3) {
                            continue;
                        }
                        seen[t] = true;
                        // 同档值 ⇒ 同掩码：0 与 1 两档的档值逐字相同，则同一坐标上的骨架掩码必须
                        // 逐位相同（这条与"两档各自的窗统计"无关，直接钉"没有第二真值"）
                        if (t == 0 && mask(SEED, x, z, 0) != mask(SEED, x, z, 1)) {
                            d.maskMismatch++;
                        }
                        final int h = height(SEED, x, z, t);
                        final int m = mask(SEED, x, z, t);
                        if (!GTSRRiverNetwork.isChannel(m)) {
                            continue;
                        }
                        d.channel[t]++;
                        ch[t]++;
                        d.cols++;
                        // 河宽 = <b>横向</b>连段长度（沿走向数会把河长当河宽）：
                        // E-W 族沿 z 量、N-S 族沿 x 量，两向都数再扣掉重复计的本列
                        d.runMax[t] = Math.max(d.runMax[t],
                            crossWidth(SEED, x, z, GTSRRiverNetwork.MASK_CHANNEL_EW, 0, 1));
                        d.runMax[t] = Math.max(d.runMax[t],
                            crossWidth(SEED, x, z, GTSRRiverNetwork.MASK_CHANNEL_NS, 1, 0));
                        for (int i = 0; i < 4; i++) {
                            final int dx = i == 0 ? -1 : i == 1 ? 1 : 0;
                            final int dz = i == 2 ? -1 : i == 3 ? 1 : 0;
                            final int nt = tier(SEED, x + dx, z + dz);
                            nbY[i] = height(SEED, x + dx, z + dz, nt);
                            nbMask[i] = mask(SEED, x + dx, z + dz, nt);
                        }
                        if (GTSRRiverNetwork.wantsWater(m)) {
                            d.wetBit[t]++;
                        }
                        if (!GTSRRiverNetwork.wantsWater(m)
                            || !GTSRRiverNetwork.holdsWater(h, nbY, nbMask)) {
                            continue;
                        }
                        d.wet[t]++;
                        wc[t]++;
                    }
                }
                for (int t = 0; t < 4; t++) {
                    if (!seen[t]) {
                        continue;
                    }
                    d.tierChunks[t]++;
                    d.chanMin[t] = d.chanMin[t] < 0 ? ch[t] : Math.min(d.chanMin[t], ch[t]);
                    d.chanMax[t] = Math.max(d.chanMax[t], ch[t]);
                    d.wetMin[t] = d.wetMin[t] < 0 ? wc[t] : Math.min(d.wetMin[t], wc[t]);
                    d.wetMax[t] = Math.max(d.wetMax[t], wc[t]);
                }
            }
        }
        return d;
    }

    /**
     * 为四个身份档各挑 {@value #SAMPLES_PER_TIER} 个"整块同档"的 chunk 原点（确定性格序扫描，
     * 第 {@code k} 个候选的原点由质数乘法散列得到 ⇒ 双跑逐位一致；挑不满就直接抛，不静默降档）。
     */
    static int[][] sampleOrigins() {
        final int[][] out = new int[4 * SAMPLES_PER_TIER][2];
        for (int t = 0; t < 4; t++) {
            int picked = 0;
            for (int k = 0; k < SAMPLE_CANDIDATES && picked < SAMPLES_PER_TIER; k++) {
                final int cx = (int)(((long)k * 37L) % 3072L) - 1536;
                final int cz = (int)(((long)k * 149L) % 3072L) - 1536;
                if (!chunkIsTier(SEED, cx, cz, t)) {
                    continue;
                }
                out[t * SAMPLES_PER_TIER + picked][0] = cx;
                out[t * SAMPLES_PER_TIER + picked][1] = cz;
                picked++;
            }
            if (picked < SAMPLES_PER_TIER) {
                throw new IllegalStateException("档 " + t + "(" + ROSTER[t] + ") 在 " + SAMPLE_CANDIDATES
                    + " 个候选原点里只挑到 " + picked + " 个同档 chunk ⇒ 身份面分布或取窗逻辑变了，"
                    + "不降档自证");
            }
        }
        return out;
    }

    /** 本 chunk 的四分之一点采样全落在同一档上（成片尺度下等价于"整块同档"）。 */
    static boolean chunkIsTier(long seed, int cx, int cz, int tier) {
        for (int lz = 4; lz < 16; lz += 8) {
            for (int lx = 4; lx < 16; lx += 8) {
                if (tier(seed, (cx << 4) + lx, (cz << 4) + lz) != tier) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 某一<b>族</b>在 (x,z) 处沿其横向 (dx,dz) 的连续成道列数（两侧各数一次，本列只计一次）。
     * 本列不属该族时返回 0。
     */
    static int crossWidth(long seed, int x, int z, int familyBit, int dx, int dz) {
        if ((mask(seed, x, z, tier(seed, x, z)) & familyBit) == 0) {
            return 0;
        }
        int n = 1;
        for (int sign = -1; sign <= 1; sign += 2) {
            int px = x + sign * dx;
            int pz = z + sign * dz;
            int guard = 0;
            while (guard++ < 32
                && (mask(seed, px, pz, tier(seed, px, pz)) & familyBit) != 0) {
                n++;
                px += sign * dx;
                pz += sign * dz;
            }
        }
        return n;
    }

    static int mask(long seed, int x, int z, int tier) {
        return GTSRRiverNetwork.riverMask(seed, x, z, GTSRRiverNetwork.bandForRosterIndex(tier),
            GTSRRiverNetwork.wetForRosterIndex(tier));
    }

    static void groupB() {
        final long t0 = System.nanoTime();
        final Dense d = scan();
        final long ms = (System.nanoTime() - t0) / 1000000L;
        final int chunks = (int)d.scannedChunks;
        say("B-READ 分层窗=" + chunks + " chunk（四档各 " + SAMPLES_PER_TIER + " 个同档整块）通道列="
            + d.cols + " 每档分母=" + SAMPLES_PER_TIER + " 耗时=" + ms + "ms");
        say("B-PRE 改前对照：HEAD 470be0f 的 src/main/java 里 Blocks.water 引用面 = 0 处（git grep 实测，"
            + "见 SC2-RESULT §3）⇒ 改前三档水系格数一律 0，本组读数是改后");
        for (int t = 0; t < 4; t++) {
            say("B-DENSE 档" + t + "(" + ROSTER[t] + ") 含该档的chunk=" + d.tierChunks[t]
                + " 每chunk通道列 min/均/max=" + d.chanMin[t] + "/"
                + f3(d.channel[t] / (double)Math.max(1L, d.tierChunks[t])) + "/" + d.chanMax[t]
                + " 每chunk水列 min/均/max=" + d.wetMin[t] + "/"
                + f3(d.wet[t] / (double)Math.max(1L, d.tierChunks[t])) + "/" + d.wetMax[t]
                + " 过水位列=" + d.wetBit[t] + " 几何闸拒=" + (d.wetBit[t] - d.wet[t]));
        }
        final double denom = chunks * 256.0D;
        say("B-PP 水列占窗内列面比 沼泽=" + f3(100.0D * d.wet[3] / denom) + "pp 森林="
            + f3(100.0D * d.wet[1] / denom) + "pp 草原=" + f3(100.0D * d.wet[0] / denom)
            + "pp 荒漠=" + f3(100.0D * d.wet[2] / denom) + "pp");
        check("B1 河网密度群系偏序：沼泽 > 森林 且 沼泽 > 草原（水列绝对数，同窗同分母）",
            d.wet[3] > d.wet[1] && d.wet[3] > d.wet[0],
            "沼泽=" + d.wet[3] + " 森林=" + d.wet[1] + " 草原=" + d.wet[0]);
        check("B2 荒漠零水（档值 0 ⇒ 只留干谷；需求「荒漠最稀或仅干谷」）", d.wet[2] == 0L,
            "荒漠水列=" + d.wet[2]);
        check("B3 荒漠仍有干谷通道（成道不为 0 ⇒ 抑制只落在水上，不落在整个河道面）", d.channel[2] > 0L,
            "荒漠通道列=" + d.channel[2]);
        check("B4 同档值 ⇒ 同掩码（草原/森林档值逐字相同 ⇒ 同一坐标的骨架掩码逐位相等；"
            + "S-C 原式比的是两片不同窗的绝对数，那在正确的实现下也不成立）",
            d.maskMismatch == 0L, "逐位不等的列=" + d.maskMismatch);
        check("B5 沼泽每 chunk 通道列数落在申报带 [8,120]（既不是零也不是淹图）",
            chunkMean(d, 3) >= 8.0D && chunkMean(d, 3) <= 120.0D, "沼泽均值/chunk=" + f3(chunkMean(d, 3)));
        check("B6 四档通道列都 > 0（每档身份面都在窗内出现，偏序不是空集比空集）",
            d.channel[0] > 0 && d.channel[1] > 0 && d.channel[2] > 0 && d.channel[3] > 0,
            "channel=" + Arrays.toString(d.channel));
        check("B7 每 chunk 通道列占比 ≤ 40%（S-C 原式把「每 chunk 通道列数」拿去比 4×半宽+8 的"
            + "河宽界，量纲不同——两族相交时一个 chunk 的通道列本来就远大于河宽；这里按本句原意"
            + "钉占比，河宽另由 B7b 钉）",
            channelShareOk(d), "chanMax=" + Arrays.toString(d.chanMax) + " 占比上限="
                + CHANNEL_SHARE_PP + "pp");
        check("B7b 实测河宽（每族<b>横向</b>最大连续成道列数）不超过 2×该档半宽+3",
            runWidthOk(d), "maxRun=" + Arrays.toString(d.runMax) + " 半宽="
                + f3(GTSRRiverNetwork.BASE_HALF_WIDTH * GTSRRiverNetwork.bandForRosterIndex(3)) + "(满档)");
        // ———— 河间距（「河网密集」的第二种可验读数）————
        final Spacing sp = measureSpacing(SEED);
        for (int t = 0; t < 4; t++) {
            say("B-SPACING 档" + t + "(" + ROSTER[t] + ") 到最近成道列 均值=" + f3(sp.chan[t].mean)
                + " min/max=" + sp.chan[t].min + "/" + sp.chan[t].max + " 截断=" + sp.chan[t].censored
                + " 样本=" + sp.chan[t].n + " ｜ 到最近水列 均值=" + f3(sp.water[t].mean) + " min/max="
                + sp.water[t].min + "/" + sp.water[t].max + " 截断=" + sp.water[t].censored
                + " 样本=" + sp.water[t].n);
        }
        check("B8 河间距偏序与档表同向：沼泽到最近水列的距离 < min(森林,草原)",
            sp.water[3].mean > 0.0D && sp.water[3].mean < Math.min(sp.water[0].mean, sp.water[1].mean),
            "沼泽=" + f3(sp.water[3].mean) + " 森林=" + f3(sp.water[1].mean) + " 草原="
                + f3(sp.water[0].mean));
        check("B9 沼泽到最近水列的距离在可读带 (0,60] 格内（「河网」尺度：肉眼连得起来又没密到没有陆地）",
            sp.water[3].mean > 0.0D && sp.water[3].mean <= 60.0D, "沼泽=" + f3(sp.water[3].mean));
        check("B10 河间距采样无截断（四档在半径 " + SPACING_RADIUS + " 内都扫得到成道列 ⇒ 偏序不是没扫到）",
            sp.chan[0].censored == 0 && sp.chan[1].censored == 0 && sp.chan[2].censored == 0
                && sp.chan[3].censored == 0,
            "censored=" + Arrays.toString(new int[] { sp.chan[0].censored, sp.chan[1].censored,
                sp.chan[2].censored, sp.chan[3].censored }));
        check("B11 荒漠档的「过水位」出现次数 = 0（抑制位真的只落在水上，干谷照成；"
            + "最近的邻档水列仍会在半径内被扫到，故不能用截断数当判据）",
            d.wetBit[2] == 0L && d.channel[2] > 0L,
            "荒漠过水位列=" + d.wetBit[2] + " 通道列=" + d.channel[2] + " 水列间距截断="
                + sp.water[2].censored + "/" + sp.water[2].n);
        check("B12 三档以上都有足量河间距样本（每档 n ≥ 40 ⇒ 均值不是三五列凑出来的）",
            sp.water[0].n >= 40 && sp.water[1].n >= 40 && sp.water[3].n >= 40 && sp.chan[2].n >= 40,
            "n=" + Arrays.toString(new int[] { sp.water[0].n, sp.water[1].n, sp.chan[2].n, sp.water[3].n }));
    }

    static double chunkMean(Dense d, int tier) {
        return d.channel[tier] / (double)Math.max(1L, d.tierChunks[tier]);
    }

    /** 每 chunk 通道列占比上界（百分点）——「河道不会糊成一片」的原始意图。 */
    static final double CHANNEL_SHARE_PP = 40.0D;

    static boolean channelShareOk(Dense d) {
        for (int t = 0; t < 4; t++) {
            if (d.chanMin[t] < 0) {
                return false;
            }
            if (d.chanMax[t] > 256.0D * CHANNEL_SHARE_PP / 100.0D) {
                return false;
            }
        }
        return true;
    }

    static boolean runWidthOk(Dense d) {
        for (int t = 0; t < 4; t++) {
            final double maxHalf = GTSRRiverNetwork.BASE_HALF_WIDTH * GTSRRiverNetwork.bandForRosterIndex(t);
            if (d.runMax[t] <= 0 || d.runMax[t] > 2.0D * maxHalf + 3.0D) {
                return false;
            }
        }
        return true;
    }

    /** 一档的间距统计。 */
    static final class Gap {

        double mean;
        int min = -1;
        int max = -1;
        int n;
        int censored;
    }

    /** 四档 × 两种度量（成道列 / 水列）。 */
    static final class Spacing {

        final Gap[] chan = new Gap[] { new Gap(), new Gap(), new Gap(), new Gap() };
        final Gap[] water = new Gap[] { new Gap(), new Gap(), new Gap(), new Gap() };
    }

    /** 本列（按<b>它自己的</b>身份档）是否真的会被灌上水：过水位 + 几何闸，与落块器同一判定。 */
    static boolean wets(long seed, int x, int z) {
        final int t = tier(seed, x, z);
        final int m = mask(seed, x, z, t);
        if (!GTSRRiverNetwork.wantsWater(m)) {
            return false;
        }
        final int[] nbY = new int[4];
        final int[] nbMask = new int[4];
        for (int i = 0; i < 4; i++) {
            final int dx = i == 0 ? -1 : i == 1 ? 1 : 0;
            final int dz = i == 2 ? -1 : i == 3 ? 1 : 0;
            final int nt = tier(seed, x + dx, z + dz);
            nbY[i] = height(seed, x + dx, z + dz, nt);
            nbMask[i] = mask(seed, x + dx, z + dz, nt);
        }
        return GTSRRiverNetwork.holdsWater(height(seed, x, z, t), nbY, nbMask);
    }

    /**
     * 河间距：对每个身份档，从该档的<b>非</b>成道列出发，沿 ±x/±z 四条轴各扫到最近的目标列，
     * 取四轴最小值；同时记「最近成道列」与「最近真会灌水的列」两个距离（后者才是「河网密集」的观感量）。
     * 纯模型，不碰世界；取样点由质数步长决定 ⇒ 双跑逐位一致。
     */
    static Spacing measureSpacing(long seed) {
        final Spacing out = new Spacing();
        for (int tier = 0; tier < 4; tier++) {
            final Gap chan = out.chan[tier];
            final Gap water = out.water[tier];
            final int radius = SPACING_RADIUS;
            long chanSum = 0L;
            long waterSum = 0L;
            for (int step = 0; step < 60000 && (chan.n < SPACING_SAMPLES || water.n < SPACING_SAMPLES); step++) {
                final int x = (int)(((long)step * 613L) % 4096L) - 2048;
                final int z = (int)(((long)step * 971L) % 4096L) - 2048;
                if (tier(seed, x, z) != tier || GTSRRiverNetwork.isChannel(mask(seed, x, z, tier))) {
                    continue;
                }
                int bestChan = Integer.MAX_VALUE;
                int bestWater = Integer.MAX_VALUE;
                for (int r = 1; r <= radius && (bestChan > r || bestWater > r); r++) {
                    for (int dir = 0; dir < 4; dir++) {
                        final int dx = dir == 0 ? -r : dir == 1 ? r : 0;
                        final int dz = dir == 2 ? -r : dir == 3 ? r : 0;
                        final int px = x + dx;
                        final int pz = z + dz;
                        if (bestChan > r && GTSRRiverNetwork.isChannel(mask(seed, px, pz, tier(seed, px, pz)))) {
                            bestChan = r;
                        }
                        if (bestWater > r && wets(seed, px, pz)) {
                            bestWater = r;
                        }
                    }
                }
                if (bestChan == Integer.MAX_VALUE) {
                    chan.censored++;
                } else {
                    chan.n++;
                    chanSum += bestChan;
                    chan.min = chan.min < 0 ? bestChan : Math.min(chan.min, bestChan);
                    chan.max = Math.max(chan.max, bestChan);
                }
                if (bestWater == Integer.MAX_VALUE) {
                    water.censored++;
                } else {
                    water.n++;
                    waterSum += bestWater;
                    water.min = water.min < 0 ? bestWater : Math.min(water.min, bestWater);
                    water.max = Math.max(water.max, bestWater);
                }
            }
            chan.mean = chan.n == 0 ? -1.0D : chanSum / (double)chan.n;
            water.mean = water.n == 0 ? -1.0D : waterSum / (double)water.n;
        }
        return out;
    }

    // ══════════════════════════════════ 真实运行窗（按身份档挑窗） ══════════════════════════════════

    /** 一个身份档的 {@value #RUN_CHUNKS}×{@value #RUN_CHUNKS} chunk 真实窗与它的全部计数。 */
    static final class RunWorld {

        final RiverWorld world;
        final ChunkProviderProsperityRuins provider;
        final int chunks;
        final int baseCx;
        final int baseCz;
        final int tier;
        /** populate 前该列的真实列顶（-1 = 观察域内无固体）。 */
        final int[] preTop;

        long liquidPre;
        long gravelPre;
        long groundBefore;
        long waterCells;
        long dryValleys;
        long bedOk;
        long airOk;
        long sideSolid;
        long sideSource;
        long sideBad;
        long groundAfter;
        long liquidReject;
        long liquidRejectButGroundOk;
        long dryBad;
        long hMismatch;
        final StringBuilder worst = new StringBuilder();

        RunWorld(long seed, int chunks, int baseCx, int baseCz, int tier) {
            this.world = RiverWorld.allocate(seed);
            this.baseCx = baseCx;
            this.baseCz = baseCz;
            this.tier = tier;
            this.chunks = chunks;
            this.preTop = new int[chunks * chunks * 256];
            Arrays.fill(this.preTop, -1);
            this.provider = new ChunkProviderProsperityRuins(this.world, seed);
            this.world.chunkGen = this.provider;
        }

        int colIndex(int cx, int cz, int lx, int lz) {
            return (((cz - this.baseCz) * this.chunks) + (cx - this.baseCx)) * 256 + (lz * 16) + lx;
        }

        void generateAll() {
            for (int cz = 0; cz < this.chunks; cz++) {
                for (int cx = 0; cx < this.chunks; cx++) {
                    // 生产 provideChunk（真实地形填充 + 真实表层链 + 真实 Chunk 组装 + 真实天光）
                    this.provider.provideChunk(this.baseCx + cx, this.baseCz + cz);
                }
            }
            for (int cz = 0; cz < this.chunks; cz++) {
                for (int cx = 0; cx < this.chunks; cx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        for (int lx = 0; lx < 16; lx++) {
                            final int x = ((this.baseCx + cx) << 4) + lx;
                            final int z = ((this.baseCz + cz) << 4) + lz;
                            final int top = findTop(this, x, z);
                            this.preTop[colIndex(this.baseCx + cx, this.baseCz + cz, lx, lz)] = top;
                            if (top >= Y0 && EntityGearPigeon.canSpawnOnGround(block(x, z, top))) {
                                this.groundBefore++;
                            }
                            for (int y = Y0; y < Y1; y++) {
                                final Block b = block(x, z, y);
                                if (b.getMaterial().isLiquid()) {
                                    this.liquidPre++;
                                }
                                if (b == BlocksGTSR.prosperityRiverGravel) {
                                    this.gravelPre++;
                                }
                            }
                        }
                    }
                }
            }
        }

        /** 生产 populate → onPopulate（同时打开 tick 计数窗口，E3 才不是恒真）。 */
        void populateAll() {
            for (int cz = 0; cz < this.chunks; cz++) {
                for (int cx = 0; cx < this.chunks; cx++) {
                    this.world.populateNow(this.provider, this.baseCx + cx, this.baseCz + cz);
                }
            }
        }

        Block block(int x, int z, int y) {
            return this.world.getBlock(x, y, z);
        }

        void forEachColumn(ColumnBody body) {
            for (int cz = 0; cz < this.chunks; cz++) {
                for (int cx = 0; cx < this.chunks; cx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        for (int lx = 0; lx < 16; lx++) {
                            body.run(this, ((this.baseCx + cx) << 4) + lx, ((this.baseCz + cz) << 4) + lz,
                                this.baseCx + cx, this.baseCz + cz, lx, lz);
                        }
                    }
                }
            }
        }

        interface ColumnBody {

            void run(RunWorld w, int x, int z, int cx, int cz, int lx, int lz);
        }

        /** populate 之后的逐列复算：成层 / 四同层 / 干谷 / 刷怪面。 */
        void sweep() {
            forEachColumn(new ColumnBody() {

                @Override
                public void run(RunWorld w, int x, int z, int cx, int cz, int lx, int lz) {
                    for (int y = Y0; y < Y1; y++) {
                        if (w.block(x, z, y) != water()) {
                            continue;
                        }
                        w.waterCells++;
                        final int analyticH = height(SEED, x, z, w.tier);
                        final int pre = w.preTop[w.colIndex(cx, cz, lx, lz)];
                        if (pre != analyticH) {
                            w.hMismatch++;
                            w.note("H错@" + x + "," + z + " 真实=" + pre + " 解析=" + analyticH);
                        }
                        if (w.block(x, z, y - 1) == BlocksGTSR.prosperityRiverGravel) {
                            w.bedOk++;
                        } else {
                            w.note("床错@" + x + "," + y + "," + z + "=" + nm(w.block(x, z, y - 1)));
                        }
                        final Block up = w.block(x, z, y + 1);
                        if (up == air() || nm(up).equals("BlockAir")) {
                            w.airOk++;
                        }
                        for (int dir = 0; dir < 4; dir++) {
                            final int dx = dir == 0 ? -1 : dir == 1 ? 1 : 0;
                            final int dz = dir == 2 ? -1 : dir == 3 ? 1 : 0;
                            final Block nb = w.block(x + dx, z + dz, y);
                            if (nb.getMaterial().blocksMovement()) {
                                w.sideSolid++;
                            } else if (nb == water() && w.world.getBlockMetadata(x + dx, y, z + dz) == 0) {
                                w.sideSource++;
                            } else {
                                w.sideBad++;
                                w.note("侧漏@" + x + "," + y + "," + z + " 方向=" + dir + "=" + nm(nb)
                                    + "/" + nb.getMaterial().getClass().getSimpleName());
                            }
                        }
                    }
                    final int top = findTop(w, x, z);
                    if (top >= Y0 && w.block(x, z, top) == BlocksGTSR.prosperityRiverGravel) {
                        w.dryValleys++;
                        final Block above = w.block(x, z, top + 1);
                        if (above != air() && !above.getMaterial().isLiquid()) {
                            w.dryBad++;
                            w.note("干谷违例@" + x + "," + top + "," + z + "=" + nm(above));
                        }
                    }
                    if (top >= Y0) {
                        if (EntityGearPigeon.canSpawnOnGround(w.block(x, z, top))) {
                            w.groundAfter++;
                        }
                        if (w.world.isAnyLiquid(AxisAlignedBB.getBoundingBox(x, top + 1, z, x + 1, top + 2,
                            z + 1))) {
                            w.liquidReject++;
                            if (EntityGearPigeon.canSpawnOnGround(w.block(x, z, top))) {
                                w.liquidRejectButGroundOk++;
                            }
                        }
                    }
                }
            });
        }

        /** 前 260 字符的违例明细（只为把 FAIL 行变得可定位，不参与任何判定）。 */
        void note(String s) {
            if (this.worst.length() < 260) {
                this.worst.append(s).append(' ');
            }
        }

        /**
         * 世界摘要：逐格<b>方块注册 id + meta + 天光 + 方块光</b>四张面。
         * <p>
         * S-C2 两处改动：① 把 meta 与天光也纳进来，于是 H1 的"逐位一致"覆盖到 E 组关心的照明面，
         * 而不只是方块类型；② 方块身份改取<b>注册 id</b>（原版 {@code identityHashCode} 与
         * {@code Material.hashCode()} 都是跨进程不稳定的对象地址派生量，原式在 {@code --twice}
         * 双跑口径下必然两次摘要不同 —— 实测换一次 JVM 摘要从 {@code f26a212e…} 变成
         * {@code c1e5c83f…}，而 H1/H2 在同一进程内仍是"绿"，属静默假绿）。
         */
        String digest() throws Exception {
            final MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (int cz = 0; cz < this.chunks; cz++) {
                for (int cx = 0; cx < this.chunks; cx++) {
                    final Chunk c = this.world.getChunkFromChunkCoords(this.baseCx + cx, this.baseCz + cz);
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            for (int y = Y0; y < Y1; y++) {
                                final Block b = c.getBlock(x, y, z);
                                // 方块身份一律取 <b>注册 id</b>，不取 identityHashCode / Material.hashCode：
                                // 后两者是 JVM 运行期的对象地址派生量，<b>跨进程必不同</b>（S-C 那份用
                                // identityHashCode，于是 H1/H2 只能在同一进程内自证一致，摘要值换一次
                                // JVM 就整个改掉 —— 与 harness 的 --twice 双跑口径不兼容）。
                                final int id = b == null ? -1 : Block.getIdFromBlock(b);
                                md.update((byte)(id & 0xFF));
                                md.update((byte)(id >> 8 & 0xFF));
                                md.update((byte)c.getBlockMetadata(x, y, z));
                                md.update((byte)c.getSavedLightValue(EnumSkyBlock.Sky, x, y, z));
                                md.update((byte)c.getSavedLightValue(EnumSkyBlock.Block, x, y, z));
                            }
                        }
                    }
                }
            }
            return hex(md);
        }
    }

    /** 为四个身份档各挑一个"整窗同档"的真实窗（S-A 之后群系成片，3×3 窗内几乎必然同档）。 */
    static RunWorld[] buildWindows() {
        final RunWorld[] out = new RunWorld[4];
        for (int t = 0; t < 4; t++) {
            int foundX = -1;
            int foundZ = -1;
            for (int k = 0; k < WINDOW_CANDIDATES && foundX < 0; k++) {
                final int cx0 = ((k * 37) % 907) * WINDOW_STRIDE + 4;
                final int cz0 = ((k * 91) % 907) * WINDOW_STRIDE + 4;
                if (windowTierHomogeneous(SEED, cx0, cz0, t)) {
                    foundX = cx0;
                    foundZ = cz0;
                }
            }
            if (foundX < 0) {
                throw new IllegalStateException("档 " + t + "(" + ROSTER[t] + ") 在 " + WINDOW_CANDIDATES
                    + " 个候选原点里挑不到同档窗 ⇒ 身份面或成窗逻辑变了");
            }
            out[t] = new RunWorld(SEED, RUN_CHUNKS, foundX, foundZ, t);
            out[t].generateAll();
        }
        for (int t = 0; t < 4; t++) {
            out[t].populateAll();
            out[t].sweep();
        }
        return out;
    }

    static boolean windowTierHomogeneous(long seed, int cx0, int cz0, int tier) {
        for (int cz = 0; cz < RUN_CHUNKS; cz++) {
            for (int cx = 0; cx < RUN_CHUNKS; cx++) {
                for (int lz = 0; lz < 16; lz += 8) {
                    for (int lx = 0; lx < 16; lx += 8) {
                        if (tier(seed, ((cx0 + cx) << 4) + lx, ((cz0 + cz) << 4) + lz) != tier) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    static int findTop(RunWorld rw, int x, int z) {
        for (int y = Y1 - 1; y >= Y0; y--) {
            if (rw.block(x, z, y).getMaterial().blocksMovement()) {
                return y;
            }
        }
        return -1;
    }

    // ══════════════════════════════════ C 真实运行断面 ══════════════════════════════════

    static void groupC(RunWorld swamp) {
        say("C-READ 沼泽真实窗 " + swamp.chunks * swamp.chunks + " chunk：水格=" + swamp.waterCells
            + " 干谷底格=" + swamp.dryValleys + " 水↔床成层正确=" + swamp.bedOk + " 水上方为空气="
            + swamp.airOk + " 四同层固体=" + swamp.sideSolid + " 四同层相邻源水=" + swamp.sideSource
            + " 四同层越界=" + swamp.sideBad + " 生成期液体=" + swamp.liquidPre + " 生成期河床料="
            + swamp.gravelPre);
        check("C1 populate 之前整窗零液体（generateTerrain+表层链路径不产水）", swamp.liquidPre == 0L,
            "液体格=" + swamp.liquidPre);
        check("C2 每一格水都在河道断面上：水格数 > 0（本片真的放进了水）", swamp.waterCells > 0,
            "水格=" + swamp.waterCells);
        check("C3 成层关系：每格水正下方都是 gtsr:prosperityRiverGravel（河床料被真实消费）",
            swamp.waterCells > 0 && swamp.bedOk == swamp.waterCells,
            swamp.bedOk + "/" + swamp.waterCells + " " + swamp.worst);
        check("C4 成层关系：每格水正上方都是空气（水面敞露，不被表层埋掉）", swamp.airOk == swamp.waterCells,
            swamp.airOk + "/" + swamp.waterCells);
        check("C5 几何闸：每格水的四个同层邻格都是固体或 meta0 相邻源水（越界数 = 0）",
            swamp.sideBad == 0L && swamp.sideSolid + swamp.sideSource == 4 * swamp.waterCells,
            "越界=" + swamp.sideBad + " 合格=" + (swamp.sideSolid + swamp.sideSource) + " 期望="
                + (4 * swamp.waterCells) + " " + swamp.worst);
        check("C6 干谷（不放水的下切）只出现在没有水的地方：干谷底之上要么是空气要么是水",
            swamp.dryBad == 0L, "干谷违例=" + swamp.dryBad + " " + swamp.worst);
        check("C7 每 chunk 水系格数在申报带 [1,140]（真实窗，与 B 组模型读数的同阶性）",
            swamp.waterCells >= 1 && swamp.waterCells <= 140L * swamp.chunks * swamp.chunks,
            "水格=" + swamp.waterCells + " chunk=" + swamp.chunks * swamp.chunks);
        check("C8 断面高度不是第二真值：每格水所在列的 populate 前真实列顶 = heightAtWithReliefTier 复算值",
            swamp.hMismatch == 0L, "不一致列=" + swamp.hMismatch + " " + swamp.worst);
        check("C9 真实表层链在窗内正常成层（窗内确有本维表层固体，否则 C 组是在拿空世界自证）",
            findTop(swamp, swamp.baseCx << 4, swamp.baseCz << 4) > Y0 && swamp.groundBefore > 0,
            "首列顶=" + findTop(swamp, swamp.baseCx << 4, swamp.baseCz << 4) + " 生成期可刷出面="
                + swamp.groundBefore);
        final RunWorld desert = win[2];
        final long desertLiquid = countLiquidInWindow(desert);
        say("C-DESERT 荒漠窗（档 2，过水档 = 0）：水格=" + desert.waterCells + " 干谷底格="
            + desert.dryValleys + " 液体格=" + desertLiquid);
        check("C10 荒漠真实窗零水（需求「荒漠最稀或仅干谷」的运行态形式）", desert.waterCells == 0L,
            "荒漠水格=" + desert.waterCells);
        check("C11 荒漠真实窗确有干谷（下切发生了、床料铺了，只是不放水）", desert.dryValleys > 0,
            "荒漠干谷底格=" + desert.dryValleys);
    }

    static long countLiquidInWindow(RunWorld rw) {
        final long[] n = new long[1];
        rw.forEachColumn(new RunWorld.ColumnBody() {

            @Override
            public void run(RunWorld w, int x, int z, int cx, int cz, int lx, int lz) {
                for (int y = Y0; y < Y1; y++) {
                    if (w.block(x, z, y).getMaterial().isLiquid()) {
                        n[0]++;
                    }
                }
            }
        });
        return n[0];
    }

    // ══════════════════════════════════ D 水不侧向外溢（真实流体语义） ══════════════════════════════════

    static void groupD(RunWorld rw) {
        final List<int[]> cells = new ArrayList<int[]>();
        rw.forEachColumn(new RunWorld.ColumnBody() {

            @Override
            public void run(RunWorld w, int x, int z, int cx, int cz, int lx, int lz) {
                for (int y = Y0; y < Y1; y++) {
                    if (w.block(x, z, y) == water()) {
                        cells.add(new int[] { x, y, z });
                    }
                }
            }
        });
        check("D0 取样非空（有水可测）", !cells.isEmpty(), "水格=" + cells.size());
        final int queueBefore = rw.world.ticks.size();
        final int srcBefore = countStaticSource(rw, cells);
        // ① 逐格强制叫醒（模拟之后任何一次邻块变化：结构、装饰、玩家）
        for (final int[] c : cells) {
            water().onNeighborBlockChange(rw.world, c[0], c[1], c[2], stone());
        }
        final int scheduled = rw.world.ticks.size() - queueBefore;
        final int srcAfterWake = countStaticSource(rw, cells);
        final int liquidAfterWake = countLiquid(rw, cells);
        rw.world.drain(20000);
        // ② 收敛后逐位对比
        int stillSource = 0;
        int vanished = 0;
        for (final int[] c : cells) {
            final Block b = rw.block(c[0], c[2], c[1]);
            if (b == water() && rw.world.getBlockMetadata(c[0], c[1], c[2]) == 0) {
                stillSource++;
            } else if (!b.getMaterial().isLiquid()) {
                vanished++;
            }
        }
        // ③ 河道外不得多出一格液体：扫全窗
        final long outside = liquidOutsideChannel(rw);
        say("D-READ 水格=" + cells.size() + " 叫醒前源水=" + srcBefore + " 叫醒瞬间源水=" + srcAfterWake
            + "（差额即被换成流水的格数）液面格=" + liquidAfterWake + " 排出 tick=" + scheduled
            + " 收敛后仍为 meta0 源水=" + stillSource + " 消失=" + vanished + " 河道外新增液体格=" + outside);
        check("D1 强制叫醒后仍全部复位为 meta0 源水（无水流走、无格变空气）",
            !cells.isEmpty() && stillSource == cells.size() && vanished == 0,
            stillSource + "/" + cells.size() + " 消失=" + vanished);
        check("D2 收敛后全窗液体只在成道列上（一滴都没溢出河道）", outside == 0L, "河道外液体格=" + outside);
        check("D3 驱动真的动过：叫醒瞬间源水被换成流水且排了 tick（否则 D1 的「全部复位」是恒真）",
            srcBefore == cells.size() && srcAfterWake < cells.size() && scheduled > 0,
            "源水 " + srcBefore + "→" + srcAfterWake + " 排 tick=" + scheduled);
        // ④ 活闸对照：在同层敞开的形状上放水，同一套驱动必须真的外溢
        final int[] ctrl = controlSpill(rw);
        say("D-CONTROL 敞开断面：初始 1 格 → 收敛后液面格=" + ctrl[0] + " 其中 meta>0 的流水邻格=" + ctrl[2]
            + " 源格 meta=" + ctrl[1] + "（围合断面是 D1 的 " + cells.size() + " 格逐位不变）");
        check("D4 活闸非恒真：同层敞开的对照断面在同一 tick 驱动下确实平铺外溢（液面格 > 1）",
            ctrl[0] > 1, "对照液面格=" + ctrl[0]);
        check("D5 对照断面出现 meta>0 的流水格（1.7.10 的降档落在被灌出的邻格上 ⇒ 唤醒真的改状态；"
            + "围合断面 D1 要求逐格 meta 0）", ctrl[2] > 0, "meta>0 的流水格=" + ctrl[2]);
    }

    /**
     * 活闸对照：造一片<b>违反几何闸</b>的断面——在 1 格深的宽浅盘里放一格源水
     * （四同层都是「上方空气 + 下方固体」，正是 {@link GTSRRiverNetwork#holdsWater} 会拒绝的形状），
     * 走与 D1/D2 完全相同的唤醒 + {@code updateTick} 驱动。原版必须看到它平铺外溢，
     * 否则 D1/D2 的零外溢就只是恒真。
     *
     * @return {液面格数, 源格 meta, meta&gt;0 的流水格数}
     */
    static int[] controlSpill(RunWorld rw) {
        final int bx = 60000;
        final int bz = 60000;
        final int top = 70;
        for (int x = bx - 13; x <= bx + 13; x++) {
            for (int z = bz - 13; z <= bz + 13; z++) {
                for (int y = 60; y <= top; y++) {
                    solid(rw, x, y, z);
                }
                if (Math.abs(x - bx) <= 6 && Math.abs(z - bz) <= 6) {
                    raw(rw, x, top, z, air()); // 浅盘：整片把 y=top 清空
                }
            }
        }
        relight(rw, bx, bz);
        rw.world.setBlock(bx, top, bz, water(), 0, 2);
        water().onNeighborBlockChange(rw.world, bx, top, bz, stone());
        rw.world.drain(20000);
        int n = 0;
        int lowered = 0;
        for (int dx = -13; dx <= 13; dx++) {
            for (int dz = -13; dz <= 13; dz++) {
                final Block b = rw.world.getBlock(bx + dx, top, bz + dz);
                if (!b.getMaterial().isLiquid()) {
                    continue;
                }
                n++;
                if (rw.world.getBlockMetadata(bx + dx, top, bz + dz) > 0) {
                    lowered++;
                }
            }
        }
        return new int[] { n, rw.world.getBlockMetadata(bx, top, bz), lowered };
    }

    static void solid(RunWorld rw, int x, int y, int z) {
        rw.world.getChunkFromChunkCoords(x >> 4, z >> 4).func_150807_a(x & 15, y, z & 15, stone(), 0);
    }

    static void raw(RunWorld rw, int x, int y, int z, Block b) {
        rw.world.getChunkFromChunkCoords(x >> 4, z >> 4).func_150807_a(x & 15, y, z & 15, b, 0);
    }

    static void relight(RunWorld rw, int bx, int bz) {
        for (int cx = (bx >> 4) - 1; cx <= (bx >> 4) + 1; cx++) {
            for (int cz = (bz >> 4) - 1; cz <= (bz >> 4) + 1; cz++) {
                rw.world.getChunkFromChunkCoords(cx, cz).generateSkylightMap();
            }
        }
    }

    /** 取样格集合里仍是<b>静态源水</b>（{@code Blocks.water}）的格数——D3 用它证明驱动真的改过状态。 */
    static int countStaticSource(RunWorld rw, List<int[]> cells) {
        int n = 0;
        for (final int[] c : cells) {
            if (rw.block(c[0], c[2], c[1]) == water()) {
                n++;
            }
        }
        return n;
    }

    static int countLiquid(RunWorld rw, List<int[]> cells) {
        int n = 0;
        for (final int[] c : cells) {
            if (rw.block(c[0], c[2], c[1]).getMaterial().isLiquid()) {
                n++;
            }
        }
        return n;
    }

    /** 全窗液体格里不属于"模型认定的河道列"的格数（应为 0）。 */
    static long liquidOutsideChannel(RunWorld rw) {
        final long[] n = new long[1];
        rw.forEachColumn(new RunWorld.ColumnBody() {

            @Override
            public void run(RunWorld w, int x, int z, int cx, int cz, int lx, int lz) {
                int m = -1;
                for (int y = Y0; y < Y1; y++) {
                    if (!w.block(x, z, y).getMaterial().isLiquid()) {
                        continue;
                    }
                    if (m < 0) {
                        m = mask(SEED, x, z, tier(SEED, x, z));
                    }
                    if (!GTSRRiverNetwork.isChannel(m)) {
                        n[0]++;
                    }
                }
            }
        });
        return n[0];
    }

    // ══════════════════════════════════ E 照明与流体置位 ══════════════════════════════════

    static void groupE(RunWorld rw) throws Exception {
        // 路径①：P17-B 那条 —— 水写进裸 Block[] → new Chunk(...) → generateSkylightMap()
        final Block[] rawArr = new Block[65536];
        final byte[] metaArr = new byte[65536];
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y <= 68; y++) {
                    rawArr[x << 12 | z << 8 | y] = stone();
                }
                rawArr[x << 12 | z << 8 | 69] = water();
            }
        }
        final int queueBeforeRaw = rw.world.ticks.size();
        final Chunk rc = new Chunk(rw.world, rawArr, metaArr, 77, -31);
        rc.generateSkylightMap();
        final int queueAfterRaw = rw.world.ticks.size();
        final int skyWater = rc.getSavedLightValue(EnumSkyBlock.Sky, 3, 69, 4);
        final int skyAbove = rc.getSavedLightValue(EnumSkyBlock.Sky, 3, 70, 4);
        final int skyBed = rc.getSavedLightValue(EnumSkyBlock.Sky, 3, 68, 4);
        final int blkWater = rc.getSavedLightValue(EnumSkyBlock.Block, 3, 69, 4);
        final int heightRaw = rc.getHeightValue(3, 4);
        final boolean seeSkyRaw = rc.canBlockSeeTheSky(3, 69, 4);
        // 路径②：本片实际走的 populate 路（World.setBlock）之后
        final long[] popQueueBefore = new long[] { rw.world.ticksScheduledDuringPopulate };
        final int[] cnt = new int[6];
        final List<String> table = new ArrayList<String>();
        rw.forEachColumn(new RunWorld.ColumnBody() {

            @Override
            public void run(RunWorld w, int x, int z, int cx, int cz, int lx, int lz) {
                for (int y = Y0; y < Y1; y++) {
                    if (w.block(x, z, y) != water()) {
                        continue;
                    }
                    cnt[0]++;
                    final Chunk c = w.world.getChunkFromChunkCoords(x >> 4, z >> 4);
                    final int sk = c.getSavedLightValue(EnumSkyBlock.Sky, x & 15, y, z & 15);
                    final int bl = c.getSavedLightValue(EnumSkyBlock.Block, x & 15, y, z & 15);
                    final boolean see = c.canBlockSeeTheSky(x & 15, y, z & 15);
                    if (sk == 15) {
                        cnt[1]++;
                    }
                    if (bl > 0) {
                        cnt[2]++;
                    }
                    if (see) {
                        cnt[3]++;
                    }
                    if (y <= 4) {
                        cnt[4]++;
                    }
                    if (table.size() < 12) {
                        table.add("水格 x=" + x + " y=" + y + " z=" + z + " sky=" + sk + " block=" + bl
                            + " canSeeSky=" + see + " meta=" + w.world.getBlockMetadata(x, y, z) + " 上格="
                            + nm(w.block(x, z, y + 1)) + " 下格=" + nm(w.block(x, z, y - 1)));
                    }
                }
            }
        });
        final int total = cnt[0];
        final int lit = cnt[1];
        final int blkLitNonZero = cnt[2];
        final int seeSky = cnt[3];
        final int bedrockTouched = cnt[4];
        say("E-READ 裸数组路：天光(水格)=" + skyWater + " 天光(水上一格)=" + skyAbove + " 天光(床格)=" + skyBed
            + " 方块光(水格)=" + blkWater + " heightMap=" + heightRaw + " canSeeSky(水格)=" + seeSkyRaw
            + " 该路排出的 tick=" + (queueAfterRaw - queueBeforeRaw));
        say("E-READ populate 路：水格=" + total + " 天光=15 的格数=" + lit + " canSeeSky=" + seeSky
            + " 方块光>0 的格数=" + blkLitNonZero + " 触基岩带=" + bedrockTouched
            + "；provider 阶段排出的流体 tick 数=" + popQueueBefore[0] + "；setBlock 触发的照明回调="
            + rw.world.lightCalls + "（该桩每次补做 generateSkylightMap）");
        say("E-TABLE 水格照明实测表（前 " + table.size() + " 格）：");
        for (final String row : table) {
            say("E-TABLE " + row);
        }
        check("E1 裸数组+generateSkylightMap 路：水格自身与上一格天光都 = 15（无需任何额外置位）",
            skyWater == 15 && skyAbove == 15, "skyWater=" + skyWater + " skyAbove=" + skyAbove);
        check("E2 populate 路：每一格水都拿到天光 15（World.setBlock 无条件走 checkLight + Chunk 自算）",
            total > 0 && lit == total, lit + "/" + total);
        check("E3 provider 阶段不排任何流体 tick（水是静态源，setTickRandomly(false)；额外置位反而危险）",
            rw.world.ticksScheduledDuringPopulate == 0, "tick=" + rw.world.ticksScheduledDuringPopulate);
        check("E4 床格在固体内 ⇒ 天光 0（vanilla 同口径：地下不导天光，不是缺陷）", skyBed == 0,
            "skyBed=" + skyBed);
        check("E5 水不触基岩带（下切 2 穿不到 bedrockTop≤3）", bedrockTouched == 0,
            "触基岩水格=" + bedrockTouched);
        check("E6 每一格水都真露着天（canBlockSeeTheSky 全过 ⇒ 不是被埋在固体下的假断面）",
            total > 0 && seeSky == total, seeSky + "/" + total);
        check("E7 裸数组路也不排任何 tick（Chunk 组装期不做流体更新 ⇒ P17-B 担心的那一半不成立）",
            queueAfterRaw == queueBeforeRaw, "队列=" + queueBeforeRaw + "→" + queueAfterRaw);
        check("E8 照明回调确实被走过（populate 写水时每格都进 checkLight；=0 说明桩吞掉了被测面）",
            rw.world.lightCalls > 0, "lightCalls=" + rw.world.lightCalls);
        check("E9 水格不给自己添方块光（水的不透明度 0 ⇒ 方块光仍为 0，不是「亮起来的水」）",
            blkLitNonZero == 0, "方块光>0 的水格=" + blkLitNonZero);
    }

    // ══════════════════════════════════ F 源级红线 ══════════════════════════════════

    static void groupF(String srcRoot) throws Exception {
        final String riverDir = "com/miaokatze/gtsr/common/dimension/prosperity/river/";
        if (srcRoot == null) {
            say("NOTE F 组源级断言跳过：未找到 src 根（args[0] 传 src/main/java）");
            return;
        }
        // 禁词判定一律先剥注释（判的是代码，不是 javadoc 文案）；正向计数同口径。
        // ── P17-S-H（红 A）：判词再过一道<b>空白归一</b>，让 F 组与排版彻底解耦 ──
        // 批末 `spotlessApply` 只格式化 src/**（实测 15:17:46 那一批改了 13 个 src 文件、tools/** 0 个），
        // 它把 GTSRRiverPlacer 的调用点折成 `GTSRGenLayerRosterFace` 换行 `.rosterIndexAt(...)` ⇒
        // 任何含空白/跨行的字面量 needle 都会<b>静默失效</b>：正向 needle 失效＝假红（本次的 F8），
        // 负向 needle 失效＝<b>假绿</b>（F5 的 `rosterIndex ==`、F7 的 `>>> 33`、F9 的
        // `protected void onPopulate` 计数同属这一族，只是当前树还没被折到那一行）。
        // 被测语义是"身份走哪条面 / 盐怎么引用 / 有没有第二份字面量"，与换行缩进无关 ⇒ 判法必须排版无关。
        // 实测（temp/p17-sh/needle-sweep.py 扫 src/main/java 全部 376 个 .java）：归一前后只有 F8 的一条 needle 由
        // miss 变 HIT，其余 needle（含 scanTree 的三项计数）命中数逐条不变 ⇒ 本次改法不新增任何红。
        final String placer = flat(stripComments(read(new java.io.File(srcRoot, riverDir + "GTSRRiverPlacer.java"))));
        final String network = flat(stripComments(
            read(new java.io.File(srcRoot, riverDir + "GTSRRiverNetwork.java"))));
        final String provider = flat(stripComments(read(new java.io.File(srcRoot,
            "com/miaokatze/gtsr/common/dimension/prosperity/ChunkProviderProsperityRuins.java"))));
        // 盐值不写进本工具（否则本片自己就成了第四处字面量）：直接读生产常数，与 F8 正向臂同一个真值源。
        final long chainSalt = ProsperityTerrainProfile.CHAIN_SEED_SALT;
        final Map<String, Integer> waterRefs = new LinkedHashMap<String, Integer>();
        final Map<String, Integer> flowRefs = new LinkedHashMap<String, Integer>();
        final Map<String, Integer> liquidSub = new LinkedHashMap<String, Integer>();
        scanTree(new java.io.File(srcRoot), waterRefs, flowRefs, liquidSub);
        say("F-READ Blocks.water 引用=" + waterRefs + " flowing_water=" + flowRefs + " extends BlockLiquid="
            + liquidSub);
        check("F1 全仓只有一处写水（河床落块器）；flowing_water 与 BlockLiquid 子类都 = 0",
            waterRefs.size() == 1 && waterRefs.containsKey(riverDir + "GTSRRiverPlacer.java")
                && flowRefs.isEmpty() && liquidSub.isEmpty(),
            "water=" + waterRefs.keySet() + " flow=" + flowRefs.keySet() + " sub=" + liquidSub.keySet());
        check("F2 generateTerrain 路径无水：provider 的 generateTerrain 段与框架表层内核都不出现 water",
            !cutBetween(provider, "protectedvoidgenerateTerrain",
                "privatestaticGTSRChunkProviderBase.SurfaceSpec").contains("water")
                && !flat(read(new java.io.File(srcRoot,
                    "com/miaokatze/gtsr/common/dimension/framework/GTSRChunkProviderBase.java")))
                        .contains("Blocks.water"),
            "generateTerrain/框架里出现了 water");
        check("F3 模型层零 MC 依赖：GTSRRiverNetwork 不出现 net.minecraft，也不含 getBlock(/findSurfaceY(/"
            + "isAirBlock(/setBlock( 四个世界读写面",
            !network.contains("net.minecraft") && !network.contains("getBlock(")
                && !network.contains("findSurfaceY(") && !network.contains("isAirBlock(")
                && !network.contains("setBlock("),
            "GTSRRiverNetwork 里出现了 MC 类型或世界读面");
        check("F4 分流不按地表方块比选：河道两文件不 import/引用 SurfaceGate、isNaturalTop、landableTops",
            !placer.contains("SurfaceGate") && !network.contains("SurfaceGate")
                && !placer.contains("isNaturalTop") && !network.contains("landableTops"),
            "河道里出现了地表比选");
        check("F5 零身份等值判断：两文件不含 rosterIndex ==/BiomeId/FUMAROLE/BRASS_WASTES",
            !placer.contains("rosterIndex==") && !network.contains("rosterIndex==")
                && !placer.contains("BiomeId") && !network.contains("BiomeId")
                && !placer.contains("FUMAROLE") && !network.contains("FUMAROLE")
                && !placer.contains("BRASS_WASTES") && !network.contains("BRASS_WASTES"),
            "河道里出现了身份等值判断");
        check("F6 不消费 rand/不新建随机源：两文件无 java.util.Random、new Random、nextInt",
            !placer.contains("java.util.Random") && !network.contains("java.util.Random")
                && !placer.contains("newRandom") && !network.contains("newRandom")
                && !placer.contains("nextInt") && !network.contains("nextInt"),
            "河道里出现了随机源（会移动既有 rand 流）");
        check("F7 不自建噪声核/手搓哈希：两文件不含 splitmix64(、>>> 33、Math.random、valueNoise 定义",
            !placer.contains("splitmix64(") && !network.contains("splitmix64(")
                && !network.contains(">>>33") && !placer.contains("Math.random")
                && !network.contains("staticdoublevalueNoise"),
            "河道里出现了第二份哈希/噪声实现");
        // ── F8：本次红 A 的落点。判据写法改排版无关（见本组开头的说明），并同时<b>往严</b>收两处 ──
        // ① 由"至少有一处合格的 roster 面调用"收紧为"每一次 rosterIndexAt 调用都由该 coarse 面接收"
        //    ——原写法下再补一处「走别的面取身份」不会变红，而标题写的就是"只走那一条"。
        // ② 负向由"字面串 0x50524F53 不出现"收紧为"源码整数字面量里没有一个等于该盐值"
        //    ——十进制重抄、`0x5052_4F53` 下划线写法原写法全都抓不到（这才是真正的第二真值源）。
        final int identityCalls = count(placer, "rosterIndexAt(");
        final int identityViaRosterFace = count(placer, "GTSRGenLayerRosterFace.rosterIndexAt(");
        final boolean saltByConstant = placer.contains("ProsperityTerrainProfile.CHAIN_SEED_SALT");
        final boolean saltRetyped = hasIntLiteralEqualTo(placer, chainSalt);
        check("F8 身份取数只走 S-A 那一条 coarse 面（盐值引用常数，不重抄字面量）",
            identityCalls > 0 && identityCalls == identityViaRosterFace && saltByConstant && !saltRetyped,
            "身份取数调用=" + identityCalls + " 其中经 roster 面=" + identityViaRosterFace
                + " 盐走常数引用=" + (saltByConstant ? "是" : "否") + " 盐字面量重抄="
                + (saltRetyped ? "有" : "无") + "（盐值 " + chainSalt + " 取自生产常数）");
        check("F9 populate 是唯一挂点：provider 只覆写 onPopulate 一处并转调落块器",
            count(provider, "protectedvoidonPopulate") == 1 && provider.contains("GTSRRiverPlacer.place("),
            "挂点数异常");
        check("F9b 本片不新增方块：河道两文件不含 new Block、extends Block、registerBlock",
            !placer.contains("newBlock") && !network.contains("newBlock")
                && !placer.contains("extendsBlock") && !network.contains("extendsBlock")
                && !placer.contains("registerBlock") && !network.contains("registerBlock"),
            "河道自己造了方块");
        final Block[] quad = { BlocksGTSR.prosperitySwampTop, BlocksGTSR.prosperitySwampBase,
            BlocksGTSR.prosperitySteppeTop, BlocksGTSR.prosperityForestTop, BlocksGTSR.prosperityWastesTop,
            BlocksGTSR.prosperityWastesBase, BlocksGTSR.prosperityForestBase, BlocksGTSR.prosperitySteppeBase };
        boolean allSolid = true;
        for (final Block b : quad) {
            if (b == null || b.getMaterial().isLiquid() || b == water()) {
                allSolid = false;
            }
        }
        check("F10 沼泽/四群系 top+base 八元组仍是固体（未被改成液体；泥炭沼表土的既存表现语义本片不动）",
            allSolid, "有元组变成液体/缺失");
        final BiomeGenBase swamp = GTSRBiomeAuthority.forDimKey(GTSRBiomeAuthority.DIM_KEY_PROSPERITY)
            .biomeOf(GTSRBiomeAuthority.BiomeId.FUMAROLE_SWAMP);
        check("F11 起雾沼泽的 topBlock 仍是那块固体表土（isLiquid = false）",
            swamp != null && swamp.topBlock != null && !swamp.topBlock.getMaterial().isLiquid()
                && swamp.topBlock == BlocksGTSR.prosperitySwampTop,
            swamp == null ? "身份未装配" : nm(swamp.topBlock));
        check("F12 床料是在册方块且为固体挡移动（Material.ground）：gtsr:prosperity_river_gravel",
            BlocksGTSR.prosperityRiverGravel != null
                && BlocksGTSR.prosperityRiverGravel.getMaterial().blocksMovement()
                && !BlocksGTSR.prosperityRiverGravel.getMaterial().isLiquid()
                && Block.getIdFromBlock(BlocksGTSR.prosperityRiverGravel) > 0,
            nm(BlocksGTSR.prosperityRiverGravel) + " id="
                + Block.getIdFromBlock(BlocksGTSR.prosperityRiverGravel));
    }

    // ══════════════════════════════════ G 齿轮鸽 isAnyLiquid 连带面 ══════════════════════════════════

    static void groupG() {
        long before = 0;
        long after = 0;
        long reject = 0;
        long rejectButGroundOk = 0;
        final long[] beforeT = new long[4];
        final long[] afterT = new long[4];
        final long[] rejectT = new long[4];
        for (int t = 0; t < 4; t++) {
            beforeT[t] = win[t].groundBefore;
            afterT[t] = win[t].groundAfter;
            rejectT[t] = win[t].liquidReject;
            before += win[t].groundBefore;
            after += win[t].groundAfter;
            reject += win[t].liquidReject;
            rejectButGroundOk += win[t].liquidRejectButGroundOk;
        }
        say("G-READ 四个身份档真实窗前后对比（五段谓词里可离线复算的三段：地面门 / 液体门；"
            + "光照与实体碰撞两段在 before/after 同口径下不变）：刷出面 before=" + before + " after=" + after
            + " 窗内列数=" + (4L * RUN_CHUNKS * RUN_CHUNKS * 256L) + " 液体拒绝列=" + reject
            + " 其中「地面门已过又被液体拒」=" + rejectButGroundOk + " 保留率="
            + f3(100.0D * after / Math.max(1L, before)) + "%");
        for (int t = 0; t < 4; t++) {
            say("G-TIER 档" + t + "(" + ROSTER[t] + ") 刷出面 before=" + beforeT[t] + " after=" + afterT[t]
                + " 液体拒=" + rejectT[t] + " 保留率=" + f3(100.0D * afterT[t] / Math.max(1L, beforeT[t]))
                + "%");
        }
        check("G1 放水后齿轮鸽刷出面没被打成 0（合计保留率 ≥ 80%）",
            after > 0 && 100.0D * after / Math.max(1L, before) >= 80.0D, "after=" + after + " before=" + before);
        check("G2 isAnyLiquid 不额外吃掉任何「地面门已过」的列（液体都在河床里，其上方不是自然 top）",
            rejectButGroundOk == 0L, "地面门过而被液体拒=" + rejectButGroundOk);
        check("G3 被液体拒的列 > 0（河面确实在窗内出现，谓词真的咬合过）", reject > 0L, "液体拒绝列=" + reject);
        check("G4 四个身份档各自的刷出面都不被清零（每档 after > 0）",
            afterT[0] > 0 && afterT[1] > 0 && afterT[2] > 0 && afterT[3] > 0, "after=" + Arrays.toString(afterT));
        check("G5 至少三档的分档保留率 ≥ 80%（v1.20.36 修好的判据面不因放水回退）",
            tierRetentionOk(beforeT, afterT) >= 3, "before=" + Arrays.toString(beforeT) + " after="
                + Arrays.toString(afterT));
    }

    /** 保留率达标（before&gt;0 且 ≥80%）的身份档个数。 */
    static int tierRetentionOk(long[] before, long[] after) {
        int ok = 0;
        for (int t = 0; t < 4; t++) {
            if (before[t] > 0 && 100.0D * after[t] / before[t] >= 80.0D) {
                ok++;
            }
        }
        return ok;
    }

    // ══════════════════════════════════ H 确定性与幂等 ══════════════════════════════════

    static void groupH(RunWorld swamp) throws Exception {
        final String first = swamp.digest();
        // 双跑：同 seed 同档窗，另一个实例从零重算
        final RunWorld again = new RunWorld(SEED, RUN_CHUNKS, swamp.baseCx, swamp.baseCz, swamp.tier);
        again.generateAll();
        again.populateAll();
        final String second = again.digest();
        // 幂等：同一个世界对同一批 chunk 再跑一次生产 populate（生产路径不会重放，
        // 但"重放必须逐位不变"才证明落块不是增量叠加）
        swamp.populateAll();
        final String third = swamp.digest();
        // 纯模型双跑
        final Dense a = scan();
        final Dense b = scan();
        // 换 seed：同一起点、两个都真 generate+populate 的世界 ⇒ 摘要必须不同
        final RunWorld alt1 = new RunWorld(SEED ^ 0x5AL, RUN_CHUNKS, swamp.baseCx, swamp.baseCz, swamp.tier);
        alt1.generateAll();
        alt1.populateAll();
        final RunWorld alt2 = new RunWorld(SEED ^ 0x5AL, RUN_CHUNKS, swamp.baseCx, swamp.baseCz, swamp.tier);
        alt2.generateAll();
        alt2.populateAll();
        final RunWorld alt3 = new RunWorld(SEED ^ 0xA5L, RUN_CHUNKS, swamp.baseCx, swamp.baseCz, swamp.tier);
        alt3.generateAll();
        alt3.populateAll();
        check("H1 同 seed 两次独立运行的世界摘要逐位一致（摘要含方块类 + meta + 天光 + 方块光）",
            first.equals(second), first.substring(0, 16) + " vs " + second.substring(0, 16));
        check("H2 幂等：对同一批 chunk 连跑两次生产 populate，世界摘要一字不变", first.equals(third),
            first.substring(0, 16) + " vs " + third.substring(0, 16));
        check("H3 纯模型双跑逐位一致（骨架/几何闸不依赖任何可变状态）",
            a.cols == b.cols && a.waterTotal() == b.waterTotal(),
            "cols=" + a.cols + "/" + b.cols + " water=" + a.waterTotal() + "/" + b.waterTotal());
        check("H4 换 seed 必换河道（骨架不是常数场；两个真生成世界的摘要不同）",
            !alt1.digest().equals(alt3.digest()), "alt1=SEED^5A alt3=SEED^A5");
        check("H5 换后的 seed 自身也是确定的（同 seed 再跑一次逐位一致）",
            alt1.digest().equals(alt2.digest()), alt1.digest().substring(0, 16) + " vs "
                + alt2.digest().substring(0, 16));
        say("H-SHA 沼泽窗 worldA=" + first.substring(0, 16) + " worldB=" + second.substring(0, 16)
            + " 重放=" + third.substring(0, 16) + " 换seed=" + alt1.digest().substring(0, 16) + "/"
            + alt3.digest().substring(0, 16));
    }

    // ══════════════════════════════════ 世界与离线装配 ══════════════════════════════════

    /**
     * 真实 {@code World.setBlock} 通路可用、但照明引擎/渲染通知/存档全部桩化的离线世界。
     * 桩化的方法逐个申报（见 {@link #stubbed()}），其余（{@code setBlock/getBlock/getBlockMetadata/
     * isAnyLiquid/canBlockSeeTheSky/…}）都是原版实现体。
     * <p>
     * <b>为什么走 {@link #allocate} 而不是构造器</b>：{@code World} 构造器第一句就是
     * {@code saveHandler.loadWorldInfo()}，离线传 null 直接 NPE（S-C 那份就是这么写的，从未跑起来）。
     * 与 {@code temp/p17-sc/src/RiverProbe.java} 同法：{@code allocateInstance} 跳过构造器，
     * 再逐个补放本组断言真正会读到的字段（worldInfo / provider / worldChunkMgr / theProfiler）。
     */
    static final class RiverWorld extends World {

        long seedValue;
        Map<Long, Chunk> chunks = new HashMap<Long, Chunk>();
        IChunkProvider chunkGen;
        List<Object[]> ticks = new ArrayList<Object[]>();
        long lightCalls;
        int ticksScheduledDuringPopulate;
        boolean inPopulate;

        /** 只为让子类可编译（{@code World} 没有无参构造器）；本类一律经 {@link #allocate} 产生。 */
        private RiverWorld() {
            super((net.minecraft.world.storage.ISaveHandler)null, (String)null, (WorldProvider)null,
                (net.minecraft.world.WorldSettings)null, (Profiler)null);
        }

        static RiverWorld allocate(long seed) {
            try {
                final sun.misc.Unsafe u = unsafe();
                final RiverWorld w = (RiverWorld)u.allocateInstance(RiverWorld.class);
                w.seedValue = seed;
                w.chunks = new HashMap<Long, Chunk>();
                w.ticks = new ArrayList<Object[]>();
                final WorldInfo info = (WorldInfo)u.allocateInstance(WorldInfo.class);
                final Field sf = WorldInfo.class.getDeclaredField("randomSeed");
                sf.setAccessible(true);
                u.putLong(info, u.objectFieldOffset(sf), seed);
                u.putObject(w, u.objectFieldOffset(World.class.getDeclaredField("worldInfo")), info);
                final GTSRDimensionDef def = SurfaceHarness.def(true, SurfaceHarness.prosperityBiomes(),
                    SurfaceHarness.prosperityWeights());
                final RP p = (RP)u.allocateInstance(RP.class);
                u.putInt(p, u.objectFieldOffset(WorldProvider.class.getDeclaredField("dimensionId")), 78);
                // 群系链种子必须与本世界 seed 同源（S-C 用的是 SurfaceHarness.manager，
                // 它内部钉的是 harness 自己的 SEED ⇒ 表层身份与河道身份来自两条链）
                u.putObject(p, u.objectFieldOffset(WorldProvider.class.getDeclaredField("worldChunkMgr")),
                    new GTSRWorldChunkManager(seed, def));
                u.putObject(w, u.objectFieldOffset(World.class.getDeclaredField("provider")), p);
                u.putObject(w, u.objectFieldOffset(World.class.getDeclaredField("theProfiler")),
                    u.allocateInstance(Profiler.class));
                return w;
            } catch (ReflectiveOperationException | RuntimeException e) {
                throw new IllegalStateException("S-C2 离线世界装配失败: " + e, e);
            }
        }

        @Override
        public Chunk getChunkFromChunkCoords(int cx, int cz) {
            final Long key = Long.valueOf(((long)cx << 32) ^ (cz & 0xFFFFFFFFL));
            Chunk c = this.chunks.get(key);
            if (c == null) {
                c = this.chunkGen == null ? new Chunk(this, new Block[65536], new byte[65536], cx, cz)
                    : this.chunkGen.provideChunk(cx, cz);
                this.chunks.put(key, c);
            }
            return c;
        }

        /** 生产 populate 入口（同时打开 tick 计数窗口）。 */
        void populateNow(IChunkProvider provider, int cx, int cz) {
            this.inPopulate = true;
            provider.populate(provider, cx, cz);
            this.inPopulate = false;
        }

        @Override
        public void scheduleBlockUpdate(int x, int y, int z, Block block, int delay) {
            this.ticks.add(new Object[] { Integer.valueOf(x), Integer.valueOf(y), Integer.valueOf(z), block });
            if (this.inPopulate) {
                this.ticksScheduledDuringPopulate++;
            }
        }

        /** 将 tick 队列按原版 {@code updateTick} 逐批跑空（D 组的收敛驱动）。 */
        void drain(int cap) {
            for (int i = 0; i < cap && !this.ticks.isEmpty(); i++) {
                final List<Object[]> batch = new ArrayList<Object[]>(this.ticks);
                this.ticks.clear();
                for (final Object[] e : batch) {
                    final Block b = (Block)e[3];
                    b.updateTick(this, ((Integer)e[0]).intValue(), ((Integer)e[1]).intValue(),
                        ((Integer)e[2]).intValue(), new java.util.Random(7L));
                }
            }
        }

        // —— 以下是离线桩（真实照明引擎/渲染通知要 lightProviders 与 worldAccesses，二者由 World
        //    构造器建立，离线走 Unsafe 故为 null）。桩法一律"补做同类工作"：func_147451_t 的检查
        //    改为对该列所在 chunk 调 generateSkylightMap()（与框架 GTSRChunkProviderBase:693 同一个
        //    实现体），所以 E 组读到的是原版天光算式的输出。
        @Override
        public boolean func_147451_t(int x, int y, int z) {
            this.lightCalls++;
            getChunkFromChunkCoords(x >> 4, z >> 4).generateSkylightMap();
            return true;
        }

        @Override
        public boolean updateLightByType(EnumSkyBlock type, int x, int y, int z) {
            return true;
        }

        @Override
        public void markBlocksDirtyVertical(int x, int z, int y0, int y1) {}

        @Override
        public void markBlockRangeForRenderUpdate(int a, int b, int c, int d, int e, int f) {}

        @Override
        public void markBlockForUpdate(int x, int y, int z) {}

        @Override
        public void func_147479_m(int x, int y, int z) {}

        @Override
        public boolean doChunksNearChunkExist(int x, int y, int z, int r) {
            return true;
        }

        @Override
        public long getSeed() {
            return this.seedValue;
        }

        @Override
        protected IChunkProvider createChunkProvider() {
            return null;
        }

        @Override
        protected int func_152379_p() {
            return 0;
        }

        @Override
        public Entity getEntityByID(int id) {
            return null;
        }

        static String stubbed() {
            return "func_147451_t/updateLightByType/markBlocksDirtyVertical/markBlockRangeForRenderUpdate/"
                + "markBlockForUpdate/func_147479_m/doChunksNearChunkExist/scheduleBlockUpdate/getSeed";
        }
    }

    static final class RP extends WorldProvider {

        @Override
        public String getDimensionName() {
            return "p17-sc2-river-check";
        }

        @Override
        public long getSeed() {
            return SEED;
        }
    }

    // ══════════════════════════════════ 工具件 ══════════════════════════════════

    static int tier(long seed, int x, int z) {
        final long key = ((long)(x >> 2) << 32) ^ ((z >> 2) & 0xFFFFFFFFL);
        final Integer hit = TIER_MEMO.get(Long.valueOf(key));
        if (hit != null) {
            return hit.intValue();
        }
        final int v = GTSRGenLayerRosterFace.rosterIndexAt(seed ^ ProsperityTerrainProfile.CHAIN_SEED_SALT,
            GTSRBiomeAuthority.DIM_KEY_PROSPERITY, x, z);
        TIER_MEMO.put(Long.valueOf(key), Integer.valueOf(v));
        return v;
    }

    static int height(long seed, int x, int z, int tier) {
        return ProsperityTerrainProfile.heightAtWithReliefTier(seed, x, z, tier < 0 ? -1 : tier);
    }

    static Block water() {
        return Blocks.water;
    }

    static Block air() {
        return Blocks.air;
    }

    static Block stone() {
        return Blocks.stone;
    }

    static String nm(Block b) {
        return b == null ? "null" : b.getClass().getSimpleName();
    }

    static String f3(double v) {
        return String.format("%.3f", Double.valueOf(v));
    }

    static void check(String name, boolean ok, String detail) {
        assertions++;
        if (!ok) {
            failures++;
        }
        LINES.add((ok ? "ok   " : "FAIL ") + name + " :: " + detail);
    }

    static void say(String line) {
        LINES.add("# " + line);
    }

    static void report() {
        for (final String l : LINES) {
            System.out.println(l);
        }
        System.out.println(failures == 0
            ? "P17-RIVER PASS: assertions=" + assertions + " failures=0 stubbedWorldMethods="
                + RiverWorld.stubbed()
            : "P17-RIVER FAIL: assertions=" + assertions + " failures=" + failures);
        System.exit(failures == 0 ? 0 : 1);
    }

    static String hex(MessageDigest md) {
        final StringBuilder sb = new StringBuilder();
        for (final byte b : md.digest()) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    static void bootstrap() throws Exception {
        Class.forName("net.minecraft.init.Blocks");
        final sun.misc.Unsafe u = unsafe();
        putStatic(u, "air", ctor(BlockAir.class));
        putStatic(u, "stone", new BlockStone());
        putStatic(u, "cobblestone", new BlockStone());
        putStatic(u, "bedrock", new BlockStone());
        putStatic(u, "dirt", new BlockStone());
        putStatic(u, "gravel", new BlockStone());
        putStatic(u, "flowing_water", ctor(BlockDynamicLiquid.class, Material.water));
        putStatic(u, "water", ctor(BlockStaticLiquid.class, Material.water));
        final Field um = RegistryNamespaced.class.getDeclaredField("underlyingIntegerMap");
        um.setAccessible(true);
        final ObjectIntIdentityMap ids = (ObjectIntIdentityMap)um.get(Block.blockRegistry);
        ids.func_148746_a(Blocks.air, 0);
        ids.func_148746_a(Blocks.stone, 1);
        ids.func_148746_a(Blocks.dirt, 3);
        ids.func_148746_a(Blocks.gravel, 13);
        ids.func_148746_a(Blocks.bedrock, 12);
        ids.func_148746_a(Blocks.flowing_water, 8);
        ids.func_148746_a(Blocks.water, 9);
        // 方块名册 + L1 账本（与兄弟片同一装配口径：coarse 身份面要求已配槽）
        SurfaceHarness.blockFamily();
        SurfaceHarness.recordProsperityAll(SurfaceHarness.prosperityBiomes());
        // S-C2：本维自有方块也要注 id。1.7.10 ExtendedBlockStorage 存 13-bit 方块 id，
        // 未注册方块 getIdFromBlock = -1 ⇒ 经真实 Chunk 写进去会读回 air（实测），
        // 于是"真实断面"的河床料与表层全成空气。id 从 400 起（原版 1.7.10 用到 ~255，
        // ExtendedBlockStorage 上界 4095，与兄弟片的离线装配互不冲突）。
        int next = 400;
        for (final Field f : BlocksGTSR.class.getFields()) {
            if (f.getType() != Block.class) {
                continue;
            }
            final Object v = f.get(null);
            if (v != null && idOf(ids, v) < 0) {
                ids.func_148746_a(v, next++);
            }
        }
        System.out.println("BOOTSTRAP gtsr 方块注 id=" + (next - 400) + " 个；riverGravel id="
            + Block.getIdFromBlock(BlocksGTSR.prosperityRiverGravel));
    }

    static int idOf(ObjectIntIdentityMap ids, Object o) {
        try {
            final Field f = ObjectIntIdentityMap.class.getDeclaredField("field_148749_a");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            final java.util.IdentityHashMap<Object, Integer> m =
                (java.util.IdentityHashMap<Object, Integer>)f.get(ids);
            final Integer v = m.get(o);
            return v == null ? -1 : v.intValue();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    static Block ctor(Class<? extends Block> k, Object... args) throws ReflectiveOperationException {
        final Class<?>[] sig = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            sig[i] = args[i] instanceof Material ? Material.class : args[i].getClass();
        }
        final Constructor<? extends Block> c = k.getDeclaredConstructor(sig);
        c.setAccessible(true);
        return c.newInstance(args);
    }

    static void putStatic(sun.misc.Unsafe u, String field, Object value) throws Exception {
        final Field f = Blocks.class.getField(field);
        u.putObject(u.staticFieldBase(f), u.staticFieldOffset(f), value);
    }

    static sun.misc.Unsafe unsafe() throws ReflectiveOperationException {
        final Field uf = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        uf.setAccessible(true);
        return (sun.misc.Unsafe)uf.get(null);
    }

    static void quietLogging() {
        try {
            final java.nio.file.Path cfg = java.nio.file.Paths.get("temp", "p17-sc2", "check-log4j2.xml");
            java.nio.file.Files.createDirectories(cfg.getParent());
            java.nio.file.Files.write(cfg, ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<Configuration status=\"OFF\"><Loggers><Root level=\"OFF\"/></Loggers></Configuration>\n")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            System.setProperty("log4j.configurationFile", cfg.toAbsolutePath().toString());
        } catch (Exception e) {
            System.out.println("NOTE: log4j 静默装配失败（" + e.getClass().getSimpleName() + "），只影响噪声");
        }
    }

    static String autoSrcRoot() {
        final String[] guess = { "src/main/java", "../../src/main/java", "../../../src/main/java" };
        for (final String g : guess) {
            if (new java.io.File(g, "com/miaokatze/gtsr/common/dimension/prosperity/river").isDirectory()) {
                return g;
            }
        }
        return null;
    }

    static String read(java.io.File f) throws java.io.IOException {
        final StringBuilder sb = new StringBuilder();
        final java.io.BufferedReader r = new java.io.BufferedReader(
            new java.io.InputStreamReader(new java.io.FileInputStream(f), "UTF-8"));
        String line;
        while ((line = r.readLine()) != null) {
            sb.append(line).append('\n');
        }
        r.close();
        return sb.toString();
    }

    static int count(String s, String needle) {
        int n = 0;
        int i = s.indexOf(needle);
        while (i >= 0) {
            n++;
            i = s.indexOf(needle, i + needle.length());
        }
        return n;
    }

    static String cutBetween(String s, String from, String to) {
        final int a = s.indexOf(from);
        final int b = s.indexOf(to, a + 1);
        return a < 0 || b < 0 ? s : s.substring(a, b);
    }

    static void scanTree(java.io.File dir, Map<String, Integer> water, Map<String, Integer> flow,
        Map<String, Integer> sub) throws java.io.IOException {
        final java.io.File[] kids = dir.listFiles();
        if (kids == null) {
            return;
        }
        for (final java.io.File f : kids) {
            if (f.isDirectory()) {
                scanTree(f, water, flow, sub);
                continue;
            }
            if (!f.getName().endsWith(".java") || f.getName().equals("P17RiverNetworkCheck.java")) {
                continue; // 判据自身不算引用面
            }
            final String s = flat(stripComments(read(f)));
            final String rel = relativize(f);
            final int w = count(s, "Blocks.water");
            if (w > 0) {
                water.put(rel, Integer.valueOf(w));
            }
            final int g = count(s, "Blocks.flowing_water");
            if (g > 0) {
                flow.put(rel, Integer.valueOf(g));
            }
            final int s2 = count(s, "extendsBlockLiquid");
            if (s2 > 0) {
                sub.put(rel, Integer.valueOf(s2));
            }
        }
    }

    static String relativize(java.io.File f) {
        final String p = f.getPath().replace('\\', '/');
        final int i = p.indexOf("com/miaokatze");
        return i < 0 ? p : p.substring(i);
    }

    static String stripComments(String s) {
        return s.replaceAll("(?s)/\\*.*?\\*/", " ")
            .replaceAll("(?m)//.*$", " ");
    }

    /**
     * 空白归一：删掉<b>全部</b>空白，使源级 needle 与换行/缩进解耦（判语义不判排版）。
     * <p>
     * 存在的理由：批末 {@code spotlessApply} 只格式化 {@code src/**}，它会把
     * {@code Foo.bar(...)} 折成两行 ⇒ 含空白或点号的跨行字面量 needle 静默失效。对本组的<b>负向</b>
     * needle 而言"失效"表现为<b>假绿</b>（破坏不再被看见），对<b>正向</b> needle 表现为假红，
     * 两个方向都必须堵。归一后点号仍保留，故 needle 仍要求"接收者 . 成员"这种真实调用形状。
     */
    static String flat(String s) {
        return s.replaceAll("\\s+", "");
    }

    /**
     * 源码文本里是否出现<b>等于 {@code value}</b> 的整数字面量：十六进制/十进制、{@code l/L} 后缀、
     * Java 7 的下划线分隔（{@code 0x5052_4F53}）都算，比"某个字面串不出现"强一档。
     * 前面带 {@code [\w.]} 的排除是为了不把 {@code a.b1345727315} 这类成员名尾串误读成字面量。
     * 超出 long 范围的数字面量解析失败 ⇒ 不参与判定（本判据只关心一个 32 位量级的盐值）。
     */
    static boolean hasIntLiteralEqualTo(String src, long value) {
        final java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("(?<![\\w.])(0[xX][0-9a-fA-F_]+|[0-9][0-9_]*)(?:[lL])?").matcher(src);
        while (m.find()) {
            final String digits = m.group(1).replace("_", "");
            try {
                final long v = digits.length() > 2 && (digits.charAt(1) == 'x' || digits.charAt(1) == 'X')
                    ? Long.parseLong(digits.substring(2), 16)
                    : Long.parseLong(digits);
                if (v == value) {
                    return true;
                }
            } catch (final NumberFormatException ignored) {
                // 越界面量（如 2^64 级别的无符号常量）不参与本判定
            }
        }
        return false;
    }
}
