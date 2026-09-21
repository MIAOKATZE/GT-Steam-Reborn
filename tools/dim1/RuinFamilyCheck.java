import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.init.Blocks;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.framework.GTSRChunkProviderBase;
import com.miaokatze.gtsr.common.dimension.framework.GTSRWorldChunkManager;
import com.miaokatze.gtsr.common.dimension.framework.SurfaceGate;
import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;
import com.miaokatze.gtsr.common.dimension.framework.structure.ChunkSpans;
import com.miaokatze.gtsr.common.dimension.framework.structure.PlacementGate;
import com.miaokatze.gtsr.common.dimension.framework.structure.StructureBuilder;
import com.miaokatze.gtsr.common.dimension.framework.structure.StructureRegistry;
import com.miaokatze.gtsr.common.dimension.prosperity.ChunkProviderProsperityRuins;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperityOutpostPlacer;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperitySurfaceScatter;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperityWorldGenerator;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.RuinedColossusShapes;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.RuinedMachinePlacer;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.RuinedMachineShapes;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityBlockResolver;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityPlanner;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityVariants;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ruin.RuinDamageOps;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ruin.RuinPlacer;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ruin.RuinShapes;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ruin.RuinTemplate;
import com.miaokatze.gtsr.config.Config;

import gregtech.api.GregTechAPI;

/**
 * <b>P8 城外废墟族（破坏结构）的主断言工具</b>（plan §5 P8 / §2.1 L5 / §2.2 H-2·H-3 / §7.2，
 * 任务包判据 2·3·4·5·6）。一次性自检 main，不进 jar，tools/ 惯例。
 * <p>
 * ═══ 七组断言与各自的判据 ═══
 * <ul>
 * <li><b>A 谱系与契约</b>（判据 2）：≥6 条模板逐条申报 <i>名 / 尺寸 / 母体 / 算子序列 / 损毁档 /
 * 所用既有键 / solid 数</i>，并钉尺寸契约（footprint ≤16×16、高 2..12）、记号全在既有键表内、
 * 禁项记号不存在、roster 条目形状（族 / opt-in 位 / 数字全 0）、六个算子都被真正使用；</li>
 * <li><b>B 派生可复算</b>（判据 2/5）：用 A 组申报的 <i>母体 + 盐 + 算子序列</i> 重跑一次
 * {@link RuinDamageOps#derive} 并逐字节比对在册模板 ⇒ 谱系是可验算的事实而不是注释；
 * 换盐必须变形（反"盐没进派生"）；母体字符盘在本族加载后逐字节不变（判据 1 的构造性一半）；</li>
 * <li><b>C TE 洁净机检</b>（判据 3）：断言 {@code 落块 ∩ (ITileEntityProvider ∪ BlockContainer ∪
 * hasTileEntity ∪ 机器/容器类命名域 ∪ GT 机器注册体身份) == ∅}，并做<b>注入敏感度</b>——
 * 塞 chest / furnace / lit_furnace / hopper / GT casing 必须变红，塞 trapdoor（无 tile）必须放行；
 * 判定按<b>落块 Block 的类与对象身份</b>，不按字符键 {@code 'X'/'Z'}（SCOPE 行现场给出理由）；</li>
 * <li><b>D 确定性</b>（判据 5）：同 seed 双跑的落点+形态 SHA 逐位相同，三个 seed 两两互不相同；</li>
 * <li><b>E micro 层收口</b>（判据 6）：引用面（消费点 ≥1 且走 L1 出口）+ <b>行为面</b>
 * （不同强度档的件数/规模档确有差异 + 关掉调制开关单变量必变）；</li>
 * <li><b>F 不重演 S2</b>（判据 4）：真地形上"关新族 vs 开新族"同批 chunk 对照，
 * 打印总座数 / 每 16×16 窗分布 / 贴脸率 / 窗内同模板 max / 每 chunk 上限，
 * 并钉"前两环座数逐位不变"与"上升量恰好等于废墟座数"；</li>
 * <li><b>K 城外跨 chunk 巨构</b>（<b>P16-B1</b>，plan §1 判据 G6）：新机型落块 (键,meta) 全在申报的
 * 红线扩档表内（禁 {@code gt.blockmachines} 任意 meta、禁 casings2:14-15 / casings3:15 / casings4 /
 * casings5 / frames 与高阶壳），并按<b>落块 Block 的类身份</b>钉
 * {@code ∩ (ITileEntityProvider ∪ BlockContainer ∪ hasTileEntity(meta)) == ∅}；三条 RED 注入臂证明
 * 判据咬得住（GT 字段指向 TE 方块 / 机器块身份接进来 / 允许表被绕开）。另钉两条密度行为面：
 * 探测门 abort 不扣预算、一座巨构只吃 1 个预算位；以及"掷骰实现体仍只有一份、没新增 Config 键"；</li>
 * <li><b>G opt-in 边界</b>（判据 2）：{@code allowsDamagedVariant=false} ⇒ 生效缺失率恒 0
 * （⇒ 损毁算子对旧 39 名不可达），旧名册的 opt-in 位实测全 false。</li>
 * </ul>
 * <p>
 * <b>货币口径（诚实性申报）</b>：
 * <ul>
 * <li><b>座数</b> = 被 {@code Permit.commit(solid>0)} 兑现的真实落块结构（不是掷中次数）；</li>
 * <li><b>贴脸率</b> = 与 8 邻 chunk 有另一座结构的座数 / 总座数（与 P7c 的 T4 同一算法）；</li>
 * <li><b>窗内同模板 max</b> = 同一 16×16 窗内同一模板被门<b>放行</b>的 chunk 数上界（= "允许发射"，
 * 与 P7c 的 C3/C6b 同货币，不是落块数）；</li>
 * <li>F 组的 sink 不做 chunk 钳制（与 {@code PlacementContractCheck.RecordSink} 同口径），
 * 故落块数含跨 chunk 写出部分——本片只用它比较两档的<b>相对</b>差，不引绝对值。</li>
 * </ul>
 * <p>
 * <b>运行</b>（MC classpath；一键入口 {@code tools/dim1/surface_checks.sh} 的 [15] 段）：
 * <pre>
 * javac -cp "temp/p4-surface/classes;$CP" -sourcepath "src/main/java;tools/dim1" \
 *   -d temp/p4-surface/tools tools/dim1/RuinFamilyCheck.java
 * java -cp "temp/p4-surface/tools;temp/p4-surface/classes;$CP" RuinFamilyCheck [all|fast] [seeds=4] [regions=4]
 * </pre>
 * 退出码 0 = 全绿；1 = 有断言红（逐条打印）。
 */
public final class RuinFamilyCheck {

    /** 与 P4..P7c 同一 8 seed 集（值照抄 {@code Dim78ScatterDensityCheck.SEEDS}）。 */
    private static final long[] SEEDS = { 0x503441L, 0x503442L, 0x503443L, 0x503444L, 0x503445L, 0x503446L,
        0x503447L, 0x503448L };

    /** 一窗 = 16×16 chunk（与散布/结构侧同一 H-2 几何）。 */
    private static final int AXIS = ProsperitySurfaceScatter.WINDOW_CHUNKS;
    private static final int SIDE = AXIS * 16;

    /** 判据 2 的下界：任务包要求「≥6 个模板」。 */
    private static final int MIN_TEMPLATES = 6;

    /** 六个算子的登记表（A10 用它断言"六种破坏方式都真的被用上"）。 */
    private static final String[] ALL_OPS = { RuinDamageOps.OP_BREAK_SPAN, RuinDamageOps.OP_TOPPLE,
        RuinDamageOps.OP_HALF_BURY, RuinDamageOps.OP_CHIP_CORNERS, RuinDamageOps.OP_RUST_SWAP,
        RuinDamageOps.OP_COLLAPSE_FAN };

    private static final List<String> FAILURES = new ArrayList<>();
    private static int passed;

    /** 本工具启动时（废墟族已加载）先钉一次母体 SHA，供 B3 复比对。 */
    private static final Map<String, String> MOTHER_SHA = new LinkedHashMap<>();

    public static void main(String[] args) throws Exception {
        final String mode = args.length > 0 ? args[0] : "all";
        final int seeds = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        final int regions = args.length > 2 ? Integer.parseInt(args[2]) : 4;
        bootstrap();
        groupA_genealogy();
        groupB_reproducibleDerivation();
        groupC_teClean();
        groupK_colossus();
        groupM_colossusMorphology();
        groupG_optInBoundary();
        groupE_microLayer();
        groupD_determinism();
        if ("fast".equals(mode)) {
            finish("fast");
            return;
        }
        groupF_density(seeds, regions);
        finish("all");
    }

    // ══════════════════════════════════ A 组：谱系与契约 ══════════════════════════════════

    private static void groupA_genealogy() {
        check(RuinShapes.ALL.length >= MIN_TEMPLATES,
            "A1 ruin 模板数 >= " + MIN_TEMPLATES + "（实测 " + RuinShapes.ALL.length + "）");
        final Set<String> names = new TreeSet<>();
        for (final RuinTemplate t : RuinShapes.ALL) {
            check(names.add(t.name), "A2 模板名不重复: " + t.name);
            check(t.name.startsWith(RuinShapes.NAME_PREFIX), "A3 族前缀: " + t.name);
            check(t.sizeX >= 1 && t.sizeX <= 16 && t.sizeZ >= 1 && t.sizeZ <= 16,
                "A4 footprint 单 chunk 契约 <=16x16: " + t.name + " " + t.sizeX + "x" + t.sizeZ);
            check(t.sizeY >= 2 && t.sizeY <= 12, "A4 高度契约 2..12: " + t.name + " y=" + t.sizeY);
            check(motherExists(t.mother()), "A5 母体在既有名册里真实存在: " + t.name + " <- " + t.mother());
            check(t.damageOps() >= 1 && t.damageOps() <= ALL_OPS.length,
                "A5 损毁档（算子步数）在 1.." + ALL_OPS.length + ": " + t.name);
            for (final String op : t.ops()) {
                check(knownOp(op), "A6 算子标签已登记: " + t.name + " op=" + op);
            }
            for (final char c : t.usedChars().toCharArray()) {
                check(CityVariants.blockKeyOf(c) != null, "A7 记号在既有键表内: " + t.name + " char=" + c);
                check(!RuinShapes.isForbiddenChar(c), "A7 废墟族不含机器注册体/核心位记号: " + t.name + " char=" + c);
            }
            check(t.solidChars() >= 24, "A8 派生剪影仍是一座结构: " + t.name + " solid=" + t.solidChars());
            final StructureRegistry.Entry e = StructureRegistry.get(t.name);
            check(e != null, "A9 模板已进 StructureRegistry: " + t.name);
            if (e != null) {
                check(PlacementGate.FAMILY_RUIN.equals(e.family),
                    "A9 族标注 = " + PlacementGate.FAMILY_RUIN + ": " + t.name + " 实得 " + e.family);
                check(e.allowsDamagedVariant, "A9 opt-in 损毁位 = true: " + t.name);
                check(e.placementDenominator == 0 && e.windowRepeatCap == 0,
                    "A9 分母/上限一律 0 = 跟随 Config（数字单一出处）: " + t.name);
                check(e.footprintX == Math.max(t.sizeX, t.sizeZ) && e.footprintZ == Math.max(t.sizeX, t.sizeZ),
                    "A9 footprint = max(sizeX,sizeZ) 旋转安全口径: " + t.name);
            }
            System.out.println(
                "RUIN-TEMPLATE " + t.name + " size=" + t.sizeX + "x" + t.sizeY + "x" + t.sizeZ + " mother="
                    + t.mother() + " ops=" + Arrays.toString(t.ops()) + " damageTier=" + t.damageOps()
                    + " scaleTier=" + RuinShapes.scaleTier(t) + " solid=" + t.solidChars() + " chars=" + t.usedChars()
                    + " keys=" + t.usedBlockKeys());
        }
        final Set<String> used = new TreeSet<>();
        for (final RuinTemplate t : RuinShapes.ALL) {
            used.addAll(Arrays.asList(t.ops()));
        }
        for (final String op : ALL_OPS) {
            check(used.contains(op), "A10 算子 " + op + " 至少被一条模板使用（实测使用集 " + used + "）");
        }
        System.out.println("# A ruin 模板 " + RuinShapes.ALL.length + " 条；算子覆盖 " + used);
    }

    private static boolean knownOp(String op) {
        for (final String o : ALL_OPS) {
            if (o.equals(op)) {
                return true;
            }
        }
        return false;
    }

    private static boolean motherExists(String mother) {
        for (final RuinedMachineShapes.Shape s : RuinedMachineShapes.ALL) {
            if (s.name.equals(mother)) {
                return true;
            }
        }
        for (final ProsperityOutpostPlacer.Outpost o : ProsperityOutpostPlacer.ALL) {
            if (o.name.equals(mother)) {
                return true;
            }
        }
        return false;
    }

    // ══════════════════════════════ B 组：派生可复算 ══════════════════════════════

    private static void groupB_reproducibleDerivation() {
        for (final RuinTemplate t : RuinShapes.ALL) {
            final String[][] again = RuinDamageOps.derive(motherLayers(t.mother()), t.salt(), t.ops());
            check(toCsv(again).equals(toCsv(t.layers)),
                "B1 派生可复算（母体+盐+算子重跑逐字节相同）: " + t.name);
            final String[][] other = RuinDamageOps.derive(motherLayers(t.mother()), t.salt() ^ 0x5A17L, t.ops());
            check(!toCsv(other).equals(toCsv(t.layers)), "B2 盐真的进了派生（换盐必须变形）: " + t.name);
            check(MOTHER_SHA.get(t.mother())
                .equals(sha256(toCsv(motherLayers(t.mother())))),
                "B3 母体字符盘在本族加载后仍逐字节不变: " + t.mother());
        }
        System.out.println("# B 派生复算 " + RuinShapes.ALL.length + " 条全等（盐敏感 + 母体未改写）");
    }

    /** 母体层串（与 {@code RuinTemplate} 的取层入口同一口径，工具侧独立读一遍防同源失明）。 */
    private static String[][] motherLayers(String mother) {
        for (final RuinedMachineShapes.Shape s : RuinedMachineShapes.ALL) {
            if (s.name.equals(mother)) {
                return s.layers;
            }
        }
        for (final ProsperityOutpostPlacer.Outpost o : ProsperityOutpostPlacer.ALL) {
            if (o.name.equals(mother)) {
                return o.layers;
            }
        }
        throw new IllegalStateException("mother missing: " + mother);
    }

    private static String toCsv(String[][] layers) {
        final StringBuilder b = new StringBuilder(1 << 12);
        for (final String[] layer : layers) {
            for (final String row : layer) {
                b.append(row).append('\n');
            }
        }
        return b.toString();
    }

    // ══════════════════════════ C 组：TE 洁净机检（含敏感度注入）══════════════════════════

    /** 申报的「机器/容器类命名域」（类名命中片段即算机器注册体）——GT5U 机器件即使不挂 tile 也算。 */
    private static final String[] MACHINE_CLASS_DOMAIN = { "gregtech.", "BlockMetaTileEntity", "BlockCasings",
        "BlockMachineBlock", "TileEntity" };

    private static final String[] GT_FORBIDDEN_KEYS = { CityVariants.K_GT_BRONZE, CityVariants.K_GT_STEEL };

    private static void groupC_teClean() {
        for (final RuinTemplate t : RuinShapes.ALL) {
            final Set<String> keys = t.usedBlockKeys();
            for (final String fk : GT_FORBIDDEN_KEYS) {
                check(!keys.contains(fk), "C1 落块键不含 GT 机器注册体 " + fk + ": " + t.name + " keys=" + keys);
            }
            final Set<String> offenders = new TreeSet<>();
            for (final char c : t.usedChars().toCharArray()) {
                final String key = CityVariants.blockKeyOf(c);
                final int meta = CityVariants.metaOf(c);
                final Block b = resolve(key);
                if (b == null) {
                    check(!CityVariants.K_GT_BRONZE.equals(key) && !CityVariants.K_GT_STEEL.equals(key),
                        "C1 GT 缺席时废墟模板也不该引到 GT 键: " + t.name + " char=" + c);
                    continue; // 生产防御链：解析落空 ⇒ 跳过该部件，不算落块
                }
                final String why = teOffense(b, meta);
                if (why != null) {
                    offenders.add("'" + c + "'=" + key + ":" + meta + " -> " + b.getClass()
                        .getName() + " (" + why + ")");
                }
            }
            check(offenders.isEmpty(), "C2 落块 ∩ (TE ∪ 机器注册体 ∪ 容器类) == ∅: " + t.name + " 命中 " + offenders);
        }
        System.out.println("TE-CHECK GREEN: ruins=" + RuinShapes.ALL.length + " offenders=0（判定=落块类/身份，非字符键）");

        // ── C3 敏感度注入：判据 3 的后半句「人为塞一个 casing/控制器方块必须变红」──
        // 离线 classpath 上 Blocks.chest / Blocks.furnace 这些原版容器<b>是 null</b>
        // （SurfaceHarness.initVanillaBlocks 只直填 6 个字段，Block.registerBlocks 走 FML Loader），
        // 所以探针全部用"当场 new 出来的方块实例"——三条臂各配一枚，另配四枚必须放行的对照件。
        final Object[][] probes = { { new TileProviderProbe(), "RED", "TE 接口臂（合成控制器）" },
            { new com.miaokatze.gtsr.common.blocks.BlockRunawaySingularity(), "RED", "BlockContainer 臂（本仓真实 TE 方块）" },
            { new BlockCasings(), "RED", "类命名域臂（GT casing 形态）" },
            { new BlockMetaTileEntity(), "RED", "类命名域臂（GT 机器体形态）" },
            { new PlainProbeBlock(), "CLEAN", "无 tile 的普通方块" },
            { Blocks.stone, "CLEAN", "原版石（废墟族在用）" },
            { Blocks.gravel, "CLEAN", "原版沙砾（废墟族在用）" },
            { Blocks.cobblestone, "CLEAN", "原版圆石" },
            { Blocks.iron_bars, "CLEAN", "铁栏杆（废墟族在用）" },
            { BlocksGTSRProbe.casing(), "CLEAN", "本维三层壳族（废墟族主力材质）" },
            { BlocksGTSRProbe.debris(), "CLEAN", "本维散布件族" },
            { BlocksGTSRProbe.surface(), "CLEAN", "本维锈石铺面" } };
        int red = 0;
        int clean = 0;
        final StringBuilder log = new StringBuilder();
        for (final Object[] pr : probes) {
            final Block b = (Block)pr[0];
            check(b != null, "C3 探针方块装配失败（null）: " + pr[2]);
            if (b == null) {
                continue;
            }
            final String why = teOffense(b, 0);
            final boolean expectRed = "RED".equals(pr[1]);
            if (why != null) {
                red++;
            } else {
                clean++;
            }
            log.append(b.getClass().getSimpleName()).append('=').append(why == null ? "clean" : why).append(' ');
            check(expectRed == (why != null),
                "C3 注入探针咬合性[" + pr[2] + "]: " + b.getClass().getSimpleName() + " 期望 "
                    + (expectRed ? "变红" : "放行") + " 实得 " + (why == null ? "clean" : why));
        }
        // GT 机器注册体的<b>身份</b>臂：离线没有真 GT5U，故把 stub 字段实赋值成一个"无 tile 的 casing 形态"
        // 方块（真游戏里 sBlockCasings1/2 正是这种无 tile 装饰外壳），验身份比对那一臂确实咬合。
        final Block fakeGtCasing1 = new PlainProbeBlock();
        final Block fakeGtCasing2 = new PlainProbeBlock();
        GregTechAPI.sBlockCasings1 = fakeGtCasing1;
        GregTechAPI.sBlockCasings2 = fakeGtCasing2;
        check("GT机器注册体(identity)".equals(teOffense(fakeGtCasing1, 10)),
            "C3 GT casing 身份臂：塞一个 sBlockCasings1 必须变红");
        check("GT机器注册体(identity)".equals(teOffense(fakeGtCasing2, 0)),
            "C3 GT casing 身份臂：塞一个 sBlockCasings2 必须变红");
        check(teOffense(new PlainProbeBlock(), 0) == null,
            "C3 身份臂只看那两个字段指向的对象（其它方块不受牵连）");
        GregTechAPI.sBlockCasings1 = null; // 还原：后续真地形采样按"GT 缺席"的既有防御链走
        GregTechAPI.sBlockCasings2 = null;
        System.out.println(
            "TE-CHECK RED PROBE: " + log + " flagged=" + red + " passed=" + clean + " gtIdentityArms=2/2 RED");

        // ── C4 作用域申报：为什么不能按字符键判 ──
        int outpostAccent = 0;
        for (final ProsperityOutpostPlacer.Outpost o : ProsperityOutpostPlacer.ALL) {
            for (final String[] layer : o.layers) {
                for (final String row : layer) {
                    for (final char ch : row.toCharArray()) {
                        if (ch == 'X' || ch == 'Z') {
                            outpostAccent++;
                        }
                    }
                }
            }
        }
        check(outpostAccent > 0,
            "C4 反假绿前提成立：既有 outpost 名册确实在用 GT 两键（实测 " + outpostAccent
                + " 格）⇒ 按字符键判就会误剥旧模板素材（任务包禁止项）");
        System.out.println("TE-CHECK SCOPE: 作用域=ruin 族 " + RuinShapes.ALL.length + " 条；outpost 名册 GT 两键用量="
            + outpostAccent + " 格（不在本检查作用域内，且它们本就无 TE）");
    }

    /**
     * 跨 chunk 巨构的安全谓词（K3 用）：只判<b>安全属性</b>，不判 ruin 族的谱系政策。
     * <p>
     * 与 {@link #teOffense(Block, int)} 的差别是刻意的，两条不能互换：
     * {@code teOffense} 多带的"类命名域"臂与"sBlockCasings1/2 身份"臂表达的是
     * <b>P8 废墟族的红线</b>（废墟不得引任何 GT 壳，因为它的字符盘必须由既有母体破败化派生），
     * 而巨构按 plan §1 G6 / 取证件 B8 是<b>允许</b>镀铜砖、固体钢壳（含齿轮箱/管道两档 meta）、
     * 青铜/钢燃烧室与防爆玻璃的——那些壳在 GT5U 侧就是
     * {@code BlockCasingsAbstract extends GTGenericBlock}，无 TE、无 tick 状态迁移。
     * 直接复用 {@code teOffense} 会把合法材质判红（本组首轮实测正是这个形态）。
     * <p>
     * 本谓词保留的四条臂，全部对应"放下去会不会生成 TileEntity / 会不会有机器逻辑"：
     * {@code ITileEntityProvider}、{@code BlockContainer}、{@code hasTileEntity(meta)}（<b>按落下的
     * 那一档 meta 问</b>，因为 GT 的 {@code BlockMachines} 是恒 true 而别的方块按 meta 分档），
     * 以及 {@code gt.blockmachines} 的对象身份。允许面（哪些 (键,meta) 能进模板）由
     * {@link #COLOSSUS_ALLOWED_KEY_METAS} 单独钉，两件事不混在一起。
     */
    private static String colossusOffense(Block b, int meta) {
        if (b == null) {
            return "解析落空（不该出现在 GT 在场档）";
        }
        if (b instanceof ITileEntityProvider) {
            return "ITileEntityProvider";
        }
        if (b instanceof BlockContainer) {
            return "BlockContainer";
        }
        if (b.hasTileEntity(meta)) {
            return "hasTileEntity:" + meta;
        }
        if (b == GregTechAPI.sBlockMachines) {
            return "gt.blockmachines(身份)";
        }
        return null;
    }

    /** 一条落块是否踩线；{@code null} = 干净。三条臂：tile 接口 / 类命名域 / GT 字段身份。 */
    private static String teOffense(Block b, int meta) {
        if (b == null) {
            return null;
        }
        if (b instanceof ITileEntityProvider) {
            return "ITileEntityProvider";
        }
        if (b instanceof BlockContainer) {
            return "BlockContainer";
        }
        if (b.hasTileEntity(meta)) {
            return "hasTileEntity";
        }
        final String cn = b.getClass().getName();
        for (final String d : MACHINE_CLASS_DOMAIN) {
            if (cn.contains(d)) {
                return "机器/容器类命名域 " + d;
            }
        }
        if (b == GregTechAPI.sBlockCasings1 || b == GregTechAPI.sBlockCasings2) {
            return "GT机器注册体(identity)";
        }
        return null;
    }

    /** 只用于 C3 身份臂的探针方块（<b>无</b> tile，模拟真实 GT casing 的形态）。 */
    static class PlainProbeBlock extends Block {

        PlainProbeBlock() {
            super(net.minecraft.block.material.Material.rock);
            setHardness(3.0F);
        }
    }

    /** 探针：只挂 {@code ITileEntityProvider}（合成"控制器"方块）。 */
    static final class TileProviderProbe extends PlainProbeBlock implements ITileEntityProvider {

        @Override
        public net.minecraft.tileentity.TileEntity createNewTileEntity(World world, int meta) {
            return null;
        }
    }

    /** 探针：类名落在申报的机器命名域里（{@code gregtech.blocks.BlockCasings} 的同名单方方块）。 */
    static final class BlockCasings extends PlainProbeBlock {}

    /** 探针：类名落在申报的机器命名域里（{@code BlockMetaTileEntity} = GT 多方块控制器基座形态）。 */
    static final class BlockMetaTileEntity extends PlainProbeBlock {}

    /** {@code BlocksGTSR} 三字段的只读取用（避免在本工具里再写一次 new 实例）。 */
    private static final class BlocksGTSRProbe {

        private BlocksGTSRProbe() {}

        static Block casing() {
            return com.miaokatze.gtsr.common.blocks.BlocksGTSR.ruinedCasing;
        }

        static Block debris() {
            return com.miaokatze.gtsr.common.blocks.BlocksGTSR.ruinDebris;
        }

        static Block surface() {
            return com.miaokatze.gtsr.common.blocks.BlocksGTSR.prosperitySurface;
        }
    }

    /** 键 → Block（走<b>生产</b>解析链 {@link CityBlockResolver}，工具不自持第二张键表）。 */
    private static Block resolve(String key) {
        final Block[] held = new Block[1];
        final BlockSink capture = (x, y, z, block, meta, flags) -> {
            if (block instanceof Block) {
                held[0] = (Block)block;
            }
            return true;
        };
        new CityBlockResolver(capture).setBlock(0, 0, 0, key, 0, 0);
        return held[0];
    }

    // ═══════════════ K 组：城外跨 chunk 巨构（P16-B1，plan §1 判据 G6）═══════════════

    /**
     * 巨构的 (键,meta) 红线<b>扩档表</b>：键 → 允许 meta 集。这就是任务包 item 3 要的"允许面成文"，
     * 也是"真正的安全闸不是结构表里少个字符"的落点——判据是<b>使用集 ⊆ 允许表</b>，
     * 表外的一切（含同一注册块的另一档 meta）都红。
     * <p>
     * 表里的键名全部直引生产常量（写错键名 = 编译错），于是本表与 {@code CityBlockResolver} 的解析表、
     * 与 {@code StructureChannelCheck} 的 12 键镜像三者互相钉：新增记号忘了进本表 ⇒ K2a 红；
     * 进了本表没进解析表 ⇒ K3 的"GT 在场档"解析不出该键 ⇒ 申报行会显形。
     */
    private static final String[][] COLOSSUS_ALLOWED_KEY_METAS = {
        { CityVariants.K_CASING, "0,1,2" },
        { CityVariants.K_DEBRIS, "0,1,2,3" },
        { CityVariants.K_SURFACE, "5" },
        { CityVariants.K_STONE, "0" },
        { CityVariants.K_COBBLE, "0" },
        { CityVariants.K_GRAVEL, "0" },
        { CityVariants.K_BARS, "0" },
        { CityVariants.K_AIR, "0" },
        // GT 静态外壳（无 TE、无 tick 状态迁移；取证件 B6/B8"能安全用于残骸"那一栏的逐档 meta）
        { CityVariants.K_GT_BRONZE, "10" },
        { CityVariants.K_GT_STEEL, "0,2,3,12,13" },
        { CityBlockResolver.K_GT_FIREBOX, "13,14" },
        { CityBlockResolver.K_GT_GLASS, "10" } };

    /** 显式禁面（政策红线的机检形态；与 K2a 互为交叉验证，任一臂写错都会红）。 */
    private static final String[] COLOSSUS_FORBIDDEN_KEY_METAS = {
        "gt.blockmachines:0", "gt.blockmachines:50", "gt.blockmachines:51",
        CityVariants.K_GT_STEEL + ":14", CityVariants.K_GT_STEEL + ":15",
        CityBlockResolver.K_GT_FIREBOX + ":15", CityVariants.K_GT_BRONZE + ":0",
        CityBlockResolver.K_GT_GLASS + ":0" };

    private static void groupK_colossus() throws Exception {
        check(RuinedColossusShapes.ALL.length >= 1,
            "K1 城外跨 chunk 巨构至少 1 条（实测 " + RuinedColossusShapes.ALL.length + "）");
        final Map<String, Set<Integer>> allowed = new TreeMap<>();
        for (final String[] row : COLOSSUS_ALLOWED_KEY_METAS) {
            final Set<Integer> metas = new TreeSet<>();
            for (final String m : row[1].split(",")) {
                metas.add(Integer.parseInt(m.trim()));
            }
            allowed.put(row[0], metas);
        }
        final Set<String> usedAll = new TreeSet<>();
        for (final RuinedColossusShapes.Colossus c : RuinedColossusShapes.ALL) {
            final Set<String> used = c.usedKeyMetas();
            usedAll.addAll(used);
            final Set<String> offMenu = new TreeSet<>();
            for (final String km : used) {
                final int at = km.lastIndexOf(':');
                final Set<Integer> metas = allowed.get(km.substring(0, at));
                if (metas == null || !metas.contains(Integer.parseInt(km.substring(at + 1)))) {
                    offMenu.add(km);
                }
            }
            check(offMenu.isEmpty(), "K2a 落块 (键,meta) 全在申报红线内: " + c.name + " 越界 " + offMenu);
            final Set<String> forbidden = new TreeSet<>();
            for (final String fk : COLOSSUS_FORBIDDEN_KEY_METAS) {
                if (used.contains(fk)) {
                    forbidden.add(fk);
                }
            }
            check(forbidden.isEmpty(), "K2b 落块 ∩ 显式禁面 == ∅: " + c.name + " 命中 " + forbidden);
            int spanX = 0;
            int spanZ = 0;
            for (final ChunkSpans.Slice sl : c.slices) {
                spanX = Math.max(spanX, sl.chunkDx + 1);
                spanZ = Math.max(spanZ, sl.chunkDz + 1);
                check(sl.withinSliceLimits(),
                    "K2c 分片 ≤16×16×12: " + c.name + " 片 " + sl.chunkDx + "," + sl.chunkDz);
            }
            check(spanX * spanZ >= 2 && spanX == c.declaredChunksX && spanZ == c.declaredChunksZ,
                "K2d 跨 chunk 申报自洽: " + c.name + " 实得 " + spanX + "x" + spanZ + " 申报 " + c.declaredChunksX
                    + "x" + c.declaredChunksZ);
            System.out.println(
                "COLOSSUS-TEMPLATE " + c.name + " bbox=" + c.sizeX() + "x" + c.sizeY() + "x" + c.sizeZ()
                    + " slices=" + c.slices.length + " solid=" + c.solidChars() + " keyMetas=" + used);
        }
        // ── K3 类身份臂：真解析（走生产 CityBlockResolver，工具不自持第二张键表）+ 三条 RED 注入 ──
        final Block[] saved = new Block[] { GregTechAPI.sBlockCasings1, GregTechAPI.sBlockCasings2,
            GregTechAPI.sBlockCasings3, GregTechAPI.sBlockGlass1, GregTechAPI.sBlockMachines };
        try {
            final Block plainCasing = new PlainProbeBlock();
            final Block teCasing = new TileProviderProbe();
            GregTechAPI.sBlockCasings1 = plainCasing;
            GregTechAPI.sBlockCasings2 = plainCasing;
            GregTechAPI.sBlockCasings3 = plainCasing;
            GregTechAPI.sBlockGlass1 = plainCasing;
            GregTechAPI.sBlockMachines = teCasing;
            resetResolverCache();
            final Set<String> offenders = new TreeSet<>();
            int judged = 0;
            for (final String km : usedAll) {
                final int at = km.lastIndexOf(':');
                final String key = km.substring(0, at);
                final int meta = Integer.parseInt(km.substring(at + 1));
                final Block b = resolve(key);
                if (b == null) {
                    continue;
                }
                judged++;
                final String why = colossusOffense(b, meta);
                if (why != null) {
                    offenders.add(key + ":" + meta + " -> " + b.getClass().getName() + " (" + why + ")");
                }
            }
            check(judged >= 4,
                "K3 反空转：GT 在场档真的判到了 GT 键（判据对象 " + judged + " 个，usedAll=" + usedAll.size() + "）");
            for (final String ownKey : new String[] { CityVariants.K_CASING, CityVariants.K_DEBRIS,
                CityVariants.K_SURFACE, CityVariants.K_STONE, CityVariants.K_BARS, CityVariants.K_AIR }) {
                final Block b = resolve(ownKey);
                check(b != null, "K3b 本维/原版键在生产解析表里真实存在: " + ownKey);
                check(b == null || colossusOffense(b, 0) == null, "K3b 本维/原版键无 TE 身份: " + ownKey);
            }
            check(offenders.isEmpty(), "K3 落块 ∩ (ITileEntityProvider ∪ BlockContainer ∪ hasTileEntity) == ∅"
                + "（GT 在场档）命中 " + offenders);
            GregTechAPI.sBlockCasings3 = teCasing;
            resetResolverCache();
            final Block red = resolve(CityBlockResolver.K_GT_FIREBOX);
            check(red != null && colossusOffense(red, 13) != null,
                "K3c RED 臂：燃烧室字段一旦指向 TE 方块，同一判据必须咬住（实得 "
                    + (red == null ? "null" : String.valueOf(colossusOffense(red, 13))) + "）");
            // RED②：机器注册体的<b>身份</b>臂——故意给一个"看起来完全无害"的无 TE 方块，
            // 只要它就是 sBlockMachines（GTSR 全部 MTE 的寄居块）就必须判红。
            // 这一臂不能省：真游戏里 BlockMachines.hasTileEntity 恒 true，三条类身份臂当然也咬得住，
            // 但"靠恒 true 才咬得住"意味着判定寄在 GT5U 的实现细节上；身份臂是把这条外部事实
            // 换成本仓能自己钉住的形状（探针实测取的就是这个差别）。
            final Block machineIdentity = new PlainProbeBlock();
            GregTechAPI.sBlockMachines = machineIdentity;
            check("gt.blockmachines(身份)".equals(colossusOffense(machineIdentity, 50)),
                "K3d RED 臂：无 TE 外表但身份是 gt.blockmachines 的方块必须被判红（实得 "
                    + colossusOffense(machineIdentity, 50) + "）");
            check(colossusOffense(new PlainProbeBlock(), 0) == null,
                "K3e 身份臂只看那个对象，不牵连其它方块（反「整表判红」式假安全）");
        } finally {
            GregTechAPI.sBlockCasings1 = saved[0];
            GregTechAPI.sBlockCasings2 = saved[1];
            GregTechAPI.sBlockCasings3 = saved[2];
            GregTechAPI.sBlockGlass1 = saved[3];
            GregTechAPI.sBlockMachines = saved[4];
            resetResolverCache();
        }
        // ── K4 密度面：探测门不扣预算；一座巨构只吃一个预算位 ──
        // 把 H-2 两条规则拧到"0 = 关闭"回退档（组 F 同款存取），让门位判定只由预算/同族互斥决定；
        // 不这么做的话本组会在"恰好有邻居命中"的 seed 上随机红（判据本身没错，是样本选择错）。
        final int capSave = Config.prosperityStructureWindowRepeatCap;
        final int gapSave = Config.prosperityStructureFamilyGapChunks;
        Config.prosperityStructureWindowRepeatCap = 0;
        Config.prosperityStructureFamilyGapChunks = 0;
        try {
        final PlacementGate.ChunkGate probeGate = PlacementGate.beginChunk(SurfaceGate.DIM78, SEEDS[0], 3, 5);
        final PlacementGate.Permit p1 = probeGate
            .request(PlacementGate.FAMILY_MACHINE, RuinedColossusShapes.ALL[0].name,
                RuinedMachinePlacer.MACHINE_INTENT);
        check(p1 != null, "K4 门能受理巨构模板名（与 5 个小机型同一族、同一条 request0 序列）");
        if (p1 != null) {
            p1.abort();
        }
        check(probeGate.committed() == 0, "K4 探测门 abort 后预算未扣（实测 committed=" + probeGate.committed()
            + "）⇒ 邻槽复现锚点结论不额外吃预算位");
        final PlacementGate.ChunkGate landGate = PlacementGate.beginChunk(SurfaceGate.DIM78, SEEDS[0], 3, 5);
        final PlacementGate.Permit p2 = landGate
            .request(PlacementGate.FAMILY_MACHINE, RuinedColossusShapes.ALL[0].name,
                RuinedMachinePlacer.MACHINE_INTENT);
        check(p2 != null && p2.commit(1), "K4b 真实落块才计成功（commit(1)==true）");
        final PlacementGate.Permit p3 = landGate
            .request(PlacementGate.FAMILY_MACHINE, RuinedColossusShapes.ALL[1 % RuinedColossusShapes.ALL.length].name,
                RuinedMachinePlacer.MACHINE_INTENT);
        check(p3 == null,
            "K4c 同一 chunk 的第二座（含巨构）被同族互斥/预算挡掉 ⇒ 跨片机制没有把每 chunk 上界抬起来");
        } finally {
            Config.prosperityStructureWindowRepeatCap = capSave;
            Config.prosperityStructureFamilyGapChunks = gapSave;
        }
        // ── K5 单一真值面：一份掷骰实现体、一把盐、零新 Config 键 ──
        final String src = stripCodeComments(new String(
            Files.readAllBytes(
                Paths.get("src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/ruins/"
                    + "RuinedMachinePlacer.java")),
            StandardCharsets.UTF_8));
        // K5a 读数口径（首轮实测 2 处，两处都是<b>改造前就存在</b>的形态，不是本片新增）：
        //   ① roll() 里那把存在掷骰（chunkSeed ^ SALT_MACHINE）；
        //   ② registerVariants() 的 S5 /gtsr structure 回调里那把（seed 由指令侧 blockSeed 派生）。
        // 本条真正钉的是"巨构没有再加第三把"，以及"两把都仍由 GTSRWorldgenHash 派生、没有裸随机"。
        check(countOf(src, "new Random(") == 2,
            "K5a 机器族的随机源仍是 2 把（roll + S5 指令回调，实测 " + countOf(src, "new Random(")
                + " 把）⇒ 跨片巨构没新开随机源");
        check(countOf(src, "GTSRWorldgenHash.chunkSeed(") == 1,
            "K5a2 槽位种子仍只有 roll() 一处经框架唯一件派生（实测 " + countOf(src, "GTSRWorldgenHash.chunkSeed(")
                + " 处）⇒ 邻槽回扫复用同一份，不另起炉灶");
        check(!src.contains("ThreadLocalRandom") && !src.contains("Math.random")
            && !src.contains("new Random()"),
            "K5a3 机器族零裸随机（ThreadLocalRandom / Math.random / 无参 new Random() 全部为 0）");
        check(src.contains("POOL[r.nextInt(POOL.length)]"), "K5b 候选池是 5 小机型 + 巨构的<b>并集</b>，一次抽签");
        check(!src.contains("prosperityColossus") && !src.contains("ColossusChance"),
            "K5c 巨构没有自带分母/新 Config 键（密度真值仍只有 Config 一处）");
        System.out.println("# K 跨 chunk 巨构 verdict=green templates=" + RuinedColossusShapes.ALL.length
            + " usedKeyMetas=" + usedAll.size() + " allowedRows=" + COLOSSUS_ALLOWED_KEY_METAS.length);
    }

    /**
     * 清掉 {@code CityBlockResolver} 的惰性解析缓存，让 K3 的字段注入真的生效。
     * 不清的话第一次 {@code resolve()} 就把 null 结果钉进了静态表，后面的 RED 注入臂会<b>静默失明</b>
     * （读到上一次的解析结果）——那是本组最容易假绿的一处，故用反射显式重置并在 finally 再清一次。
     */
    private static void resetResolverCache() throws Exception {
        final Field f = CityBlockResolver.class.getDeclaredField("blockResolver");
        f.setAccessible(true);
        f.set(null, null);
    }

    // ═══════ M 组：城外跨 chunk 巨构的【形态】判据（P16-B2，plan §0 U2 / §1 G8·G9）═══════
    //
    // 判据对象是【会落到世界里的那份东西】——RuinedColossusShapes.Wreck 残骸档 + Morph 埋深，
    // 不是名册里的母体蓝图；几何一律经【生产】入口 RuinedMachinePlacer.placeSlice，
    // 本组不自算第二套渲染，也不自算第二套允许表（K 组那一张仍是唯一真值）。
    //
    // 【机械件的口径 = 落块键，不是字符】：五族各自走自己的 char→键表
    // （机型 RuinedMachineShapes.blockKeyOf / outpost·城 CityVariants.blockKeyOf /
    // 巨构 RuinedColossusShapes.blockKeyOf），机械件 = 四把 GT 静态壳键
    // （镀铜砖 / 固体钢壳含齿轮箱·管道各两档 meta / 燃烧室 / 防爆玻璃）。
    // 用键而不用字符，是为了让「新巨构 vs 既有废墟族 vs outpost」三组数字在同一把尺上可比。

    /** 机械件键集（G9 的分子；四个键全在 K2a 允许表内，都是无 TE 的静态壳）。 */
    private static boolean isMachineKey(String key) {
        return CityVariants.K_GT_BRONZE.equals(key)
            || CityVariants.K_GT_STEEL.equals(key)
            || CityBlockResolver.K_GT_FIREBOX.equals(key)
            || CityBlockResolver.K_GT_GLASS.equals(key);
    }

    /** G9 机械件占比的实测带（P16-B2 实测钉值；改形态必须同步改这里并说明理由）。 */
    private static final double WRECK_SHARE_MIN = 6.0D;
    private static final double WRECK_SHARE_MAX = 32.0D;
    /**
     * G8④ 埋到最深一档时，允许的机芯露出比上界（百分数）。
     * <b>67 = 与生产静态契约 {@code RuinedColossusShapes} 里 {@code exposedMachine(maxBury) * 3
     * < machine * 2} 同一条阈值</b>（那边是防线、这边是验收，同一个数值只允许一处真值来源，
     * 改一处必须改两处并说明理由——这条配对由 M3④ 的失败信息直接给出实得读数兜底）。
     */
    private static final int DEEPEST_EXPOSED_PCT_MAX = 67;

    private static void groupM_colossusMorphology() {
        // ── M1 反空转探针：先证明【悬浮/接地】这套判据真的会咬——故意留一格下方是洞的方块 ──
        final VoxelSet red = new VoxelSet();
        red.put(0, 64, 0, "gtsr:RuinedCasing");
        red.put(0, 66, 0, "gtsr:RuinedCasing");
        check(red.floatingWithFlatGround(63) == 1,
            "M1 反空转：故意造一格【下方是洞】的落块，悬浮判据必须报 1（实得 " + red.floatingWithFlatGround(63)
                + "）⇒ 下面那条【悬浮=0】不是恒真式");
        red.put(0, 65, 0, "gtsr:RuinedCasing");
        check(red.floatingWithFlatGround(63) == 0, "M1 反空转配对臂：把那一格补上后必须归 0");

        // ── M2 G9：逐残骸档实测机械件占比 + 钉死带内 + 分片/覆盖不缩水 ──
        double lo = Double.MAX_VALUE;
        double hi = 0.0D;
        for (final RuinedColossusShapes.Colossus c : RuinedColossusShapes.ALL) {
            check(c.motherMachine * 100 / Math.max(1, c.motherSolid) > 50,
                "M2 对照行失准：母体蓝图里机械件本该是多数的【还立着的样子】: " + c.name);
            for (final RuinedColossusShapes.Wreck w : c.wrecks) {
                final double share = 100.0D * w.machine / Math.max(1, w.solid);
                lo = Math.min(lo, share);
                hi = Math.max(hi, share);
                check(w.machine * 2 < w.solid,
                    "M2 机械件必须是少数（残骸的本体）: " + w.label + ' ' + w.machine + '/' + w.solid);
                check(share >= WRECK_SHARE_MIN && share <= WRECK_SHARE_MAX,
                    "M2 实测占比必须落在钉死的带内 [" + WRECK_SHARE_MIN + ", " + WRECK_SHARE_MAX + "]: " + w.label
                        + " 实得 " + fmt(share, 1));
                check(w.slices.length == c.slices.length && w.sliceSolidSum() == w.solid,
                    "M2 形态改动不得让跨片结构缩水或重不漏: " + w.label + " slices=" + w.slices.length + " sum="
                        + w.sliceSolidSum() + " solid=" + w.solid);
            }
        }
        System.out.println("COLOSSUS-RATIO 残骸档机械件占比实测=[" + fmt(lo, 1) + ", " + fmt(hi, 1)
            + "] 档数=" + RuinedColossusShapes.ALL.length * RuinedColossusShapes.WRECK_COUNT + " 母体蓝图="
            + motherShareRow() + " 对照既有族: " + familyShareRow());

        // ── M3 G8 三条判据 + ④联动：逐残骸档 × 逐埋深，用真实地形（heightAt 逐列）经生产链渲染 ──
        final CityVariants.GroundFn ground = PlacementGate.groundFn(SEEDS[0]);
        int rendered = 0;
        int floating = 0;
        int carved = 0;
        long worldSolid = 0;
        long worldMachine = 0;
        double wlo = Double.MAX_VALUE;
        double whi = 0.0D;
        for (final RuinedColossusShapes.Colossus c : RuinedColossusShapes.ALL) {
            for (final RuinedColossusShapes.Wreck w : c.wrecks) {
                int prevExposed = -1;
                for (int d = 0; d <= c.maxBury; d++) {
                    final VoxelSet vox = render(c, new RuinedColossusShapes.Morph(c, w, d), ground, false);
                    rendered++;
                    // 世界读数（含运行期损伤链的逐格缺失）：机械件在世界里也必须还是少数
                    final int[] census = vox.machineCensus();
                    worldSolid += census[0];
                    worldMachine += census[1];
                    final double wshare = 100.0D * census[1] / Math.max(1, census[0]);
                    wlo = Math.min(wlo, wshare);
                    whi = Math.max(whi, wshare);
                    check(census[1] * 2 < census[0],
                        "M3 世界读数：机械件必须是少数（含运行期损伤链）: " + w.label + " d=" + d + ' ' + census[1]
                            + '/' + census[0]);
                    floating += vox.floatingAgainst(ground);
                    carved += vox.belowSurface(ground);
                    check(vox.solidCount() > 0, "M3 该档该埋深必须真落了块: " + w.label + " d=" + d);
                    if (d >= 1) {
                        check(coveredCells(w, d) > 0,
                            "M3① 埋深 " + d + " 必须确有被地形覆压的设计格: " + w.label + " 实得 0");
                    }
                    final int exposed = w.exposedMachine(d);
                    check(prevExposed < 0 || exposed <= prevExposed,
                        "M3④ 机芯露出数随埋深不升: " + w.label + " d=" + d + ' ' + exposed + " > " + prevExposed);
                    prevExposed = exposed;
                }
                final int deepest = w.exposedMachine(c.maxBury);
                check(deepest * 100 < Math.max(1, w.machine) * DEEPEST_EXPOSED_PCT_MAX,
                    "M3④ 埋到 maxBury=" + c.maxBury + " 时机芯露出比必须低于 " + DEEPEST_EXPOSED_PCT_MAX + "%: "
                        + w.label + " 实得 " + deepest + '/' + w.machine);
            }
        }
        check(rendered >= 8, "M3 反空转：渲染组数 = " + rendered);
        check(floating == 0, "M3② 悬浮块违反数必须为 0（实测合计 " + floating + "）");
        check(carved == 0, "M3③ 不得把任何方块写进地表高度场以内（挖空洞/切断地表的入口，实测 " + carved + "）");
        System.out.println("COLOSSUS-WORLD 世界落块=" + worldSolid + " 其中机械件=" + worldMachine + " 占比带=["
            + fmt(wlo, 1) + ", " + fmt(whi, 1) + "]（36 组：8 档 × 埋深 0..maxBury，含运行期逐格缺失掷骰）");
        check(worldMachine * 2 < worldSolid, "M3 世界合计：机械件仍是少数（" + worldMachine + '/' + worldSolid + "）");
        check(whi < 50.0D, "M3 世界合计上界必须 <50%（实测最高 " + fmt(whi, 1) + "）");
        System.out.println("COLOSSUS-BURY 渲染组=" + rendered + " 悬浮违反=" + floating + " 写进地形=" + carved
            + " 埋深带上界=" + maxBuryRow() + " 露出比行=" + exposureRow());

        // ── M4 B1 留下的 anchorFreeBlocks 抖动位：必须同时决定形态档与埋深（不再只是位置噪声） ──
        final RuinedColossusShapes.Colossus probe = RuinedColossusShapes.ALL[0];
        final Set<String> variantSeen = new TreeSet<>();
        final Set<String> burySeen = new TreeSet<>();
        int pure = 0;
        for (int jx = 0; jx < ChunkSpans.CHUNK_BLOCKS; jx++) {
            for (int jz = 0; jz < ChunkSpans.CHUNK_BLOCKS; jz++) {
                final RuinedColossusShapes.Morph m = RuinedColossusShapes.morphAt(SEEDS[1], jx, jz, probe);
                final RuinedColossusShapes.Morph again = RuinedColossusShapes.morphAt(SEEDS[1], jx, jz, probe);
                if (m.wreck.index == again.wreck.index && m.bury == again.bury) {
                    pure++;
                }
                variantSeen.add(String.valueOf(m.wreck.index));
                burySeen.add(String.valueOf(m.bury));
            }
        }
        check(pure == ChunkSpans.CHUNK_BLOCKS * ChunkSpans.CHUNK_BLOCKS,
            "M4 morphAt 必须是纯函数（同入参两次调用同结果，实得一致 " + pure + "/256）⇒ 邻槽能复现锚点形态");
        check(variantSeen.size() >= 2, "M4 抖动窗里必须见到 >=2 个形态档（实得 " + variantSeen.size() + "）");
        check(burySeen.size() >= 2, "M4 抖动窗里必须见到 >=2 个埋深（实得 " + burySeen + "）⇒ 半埋与锚点自由格打通");

        // ── M5 三条 B1 边界的处置面：成型中的巨构不得覆盖城 buffer、且 footprint 每列都在 y 带内 ──
        final CityVariants.GroundFn g78 = PlacementGate.groundFn(SEEDS[1]);
        final int chanceSave = Config.prosperityMachineChance;
        Config.prosperityMachineChance = 10; // 离线注入（字段本就是给离线档留的）；密度真值与 B4 无关
        try {
            int ready = 0;
            int bufferBad = 0;
            int bandBad = 0;
            for (int si = 0; si < 4; si++) {
                for (int cx = 0; cx < 16; cx++) {
                    for (int cz = 0; cz < 16; cz++) {
                        final PlacementGate.Intent it = RuinedMachinePlacer.MACHINE_INTENT
                            .intentAt(SEEDS[si], cx, cz);
                        if (it == null || RuinedColossusShapes.byName(it.templateName) == null) {
                            continue;
                        }
                        if (!RuinedMachinePlacer.spanReadyAt(SEEDS[si], cx, cz)) {
                            continue;
                        }
                        ready++;
                        final int x0 = ChunkSpans.chunkOf(it.originX);
                        final int x1 = ChunkSpans.chunkOf(it.originX + it.sizeX - 1);
                        for (int wx = x0; wx <= x1; wx++) {
                            for (int wz = ChunkSpans.chunkOf(it.originZ); wz <= ChunkSpans
                                .chunkOf(it.originZ + it.sizeZ - 1); wz++) {
                                if (CityPlanner.citiesNear(SEEDS[si], wx, wz).length > 0) {
                                    bufferBad++; // 那一格编排器会整段早退 ⇒ 邻槽不补片 ⇒ 鬼影剪影
                                }
                            }
                        }
                        for (int dx = 0; dx < it.sizeX && bandBad < 5; dx++) {
                            for (int dz = 0; dz < it.sizeZ; dz++) {
                                if (!PlacementGate.readyAtSpan(g78.groundY(it.originX + dx, it.originZ + dz))) {
                                    bandBad++;
                                }
                            }
                        }
                    }
                }
            }
            System.out.println(
                "COLOSSUS-SPAN 抽样成型巨构=" + ready + " 覆盖到城 buffer 的违例=" + bufferBad + " footprint 越 y 带列数="
                    + bandBad + "（边界②③的处置读数；配置注入 prosperityMachineChance=10 已在本臂的 finally 复位）");
            check(ready > 0, "M5 反空转：抽样里必须真有成型的跨片巨构（实得 " + ready + " 座）");
            check(bufferBad == 0,
                "M5 边界③ 闭合：成型中的巨构覆盖到的每个 chunk 都不在城 buffer 内（违例 " + bufferBad + "）");
            check(bandBad == 0, "M5 边界② 收紧臂咬住：footprint 每一列都在可落 y 带内（越带 " + bandBad + "）");
        } finally {
            Config.prosperityMachineChance = chanceSave;
        }

        // ── M6 边界⑤：GT 字段全 null（beta-1 无玻璃/无 GT）时，缺材质不得留下悬在洞上的件 ──
        final Block[] gtSave = new Block[] { GregTechAPI.sBlockCasings1, GregTechAPI.sBlockCasings2,
            GregTechAPI.sBlockCasings3, GregTechAPI.sBlockGlass1 };
        try {
            GregTechAPI.sBlockCasings1 = null;
            GregTechAPI.sBlockCasings2 = null;
            GregTechAPI.sBlockCasings3 = null;
            GregTechAPI.sBlockGlass1 = null;
            resetResolverCache();
            int landed = 0;
            int bad = 0;
            for (final RuinedColossusShapes.Colossus c : RuinedColossusShapes.ALL) {
                for (final RuinedColossusShapes.Wreck w : c.wrecks) {
                    // 走【生产解析链】（CityBlockResolver 在记录 sink 之前）⇒ 缺字段的拒写真的发生
                    final VoxelSet vox = render(c, new RuinedColossusShapes.Morph(c, w, 2), g78, true);
                    landed += vox.solidCount();
                    bad += vox.floatingAgainst(g78);
                }
            }
            check(landed > 0, "M6 边界⑤ 反空转：GT 全缺时本族仍能落块（实得 " + landed + "）⇒ 不是整族静默消失");
            check(bad == 0, "M6 边界⑤ 闭合：缺材质那一格不得把上层吊在半空（悬浮违反 " + bad + "）");
        } catch (Exception e) {
            check(false, "M6 边界⑤ 跑飞：" + e);
        } finally {
            GregTechAPI.sBlockCasings1 = gtSave[0];
            GregTechAPI.sBlockCasings2 = gtSave[1];
            GregTechAPI.sBlockCasings3 = gtSave[2];
            GregTechAPI.sBlockGlass1 = gtSave[3];
            try {
                resetResolverCache();
            } catch (Exception ignored) {
                // 收尾清缓存失败不影响判据
            }
        }
        System.out.println("# M 巨构形态 verdict=green 渲染组=" + rendered + " 悬浮=0 覆压/切表违例=0 边界②③⑤=已处置");
    }

    /** 用【生产】放置链把一座残骸档（含埋深）按 chunk 分片渲进体素集。 */
    private static VoxelSet render(RuinedColossusShapes.Colossus c, RuinedColossusShapes.Morph morph,
        CityVariants.GroundFn ground, boolean viaResolver) {
        final VoxelSet vox = new VoxelSet();
        final BlockSink rec = (x, y, z, block, meta, flags) -> {
            vox.put(x, y, z, block);
            return true;
        };
        final BlockSink tail = viaResolver ? new CityBlockResolver(rec) : rec;
        final int cx0 = ChunkSpans.chunkOf(0);
        final int cz0 = ChunkSpans.chunkOf(0);
        for (int cx = cx0; cx <= ChunkSpans.chunkOf(c.sizeX() - 1); cx++) {
            for (int cz = cz0; cz <= ChunkSpans.chunkOf(c.sizeZ() - 1); cz++) {
                RuinedMachinePlacer.placeSlice(
                    new StructureBuilder(tail),
                    new java.util.Random(0xB2L + cx * 31L + cz),
                    c.shape,
                    0,
                    0,
                    cx,
                    cz,
                    ground,
                    BlockSink.FLAG_POPULATE,
                    morph);
            }
        }
        return vox;
    }

    /** 这座残骸档在埋深 d 下【被地形覆压掉】的设计实心格数（G8① 下部被覆盖的读数）。 */
    private static int coveredCells(RuinedColossusShapes.Wreck w, int d) {
        int n = 0;
        for (int y = 0; y < d && y < w.sizeY; y++) {
            n += w.layerSolid[y];
        }
        return n;
    }

    private static String motherShareRow() {
        final StringBuilder b = new StringBuilder();
        for (final RuinedColossusShapes.Colossus c : RuinedColossusShapes.ALL) {
            if (b.length() > 0) {
                b.append(',');
            }
            b.append(c.name)
                .append('=')
                .append(fmt(100.0D * c.motherMachine / Math.max(1, c.motherSolid), 1))
                .append('%');
        }
        return b.toString();
    }

    private static String maxBuryRow() {
        final StringBuilder b = new StringBuilder();
        for (final RuinedColossusShapes.Colossus c : RuinedColossusShapes.ALL) {
            if (b.length() > 0) {
                b.append('/');
            }
            b.append(c.name).append('=').append(c.maxBury);
        }
        return b.toString();
    }

    /** 逐档报【埋深 → 机芯露出比】（交付说明里那组实测露出比读数）。 */
    private static String exposureRow() {
        final StringBuilder b = new StringBuilder();
        for (final RuinedColossusShapes.Colossus c : RuinedColossusShapes.ALL) {
            for (final RuinedColossusShapes.Wreck w : c.wrecks) {
                b.append(' ').append(w.label).append('[');
                for (int d = 0; d <= c.maxBury; d++) {
                    if (d > 0) {
                        b.append(' ');
                    }
                    b.append(d)
                        .append(':')
                        .append(fmt(100.0D * w.exposedMachine(d) / Math.max(1, w.machine), 0))
                        .append('%');
                }
                b.append(']');
            }
        }
        return b.toString();
    }

    /** 对照申报：既有四族各自的机械件占比（同一把尺 = 落块键 ∈ 四把 GT 静态壳键）。 */
    private static String familyShareRow() {
        final int[] machine = new int[3];
        final int[] total = new int[3];
        for (final RuinedMachineShapes.Shape s : RuinedMachineShapes.ALL) {
            for (int y = 0; y < s.sizeY; y++) {
                for (int z = 0; z < s.sizeZ; z++) {
                    for (int x = 0; x < s.sizeX; x++) {
                        final char c = s.charAt(y, x, z);
                        if (!ChunkSpans.isSolid(c)) {
                            continue;
                        }
                        total[0]++;
                        if (isMachineKey(RuinedMachineShapes.blockKeyOf(c))) {
                            machine[0]++;
                        }
                    }
                }
            }
        }
        for (final ProsperityOutpostPlacer.Outpost o : ProsperityOutpostPlacer.ALL) {
            for (int y = 0; y < o.sizeY; y++) {
                for (int z = 0; z < o.sizeZ; z++) {
                    for (int x = 0; x < o.sizeX; x++) {
                        final char c = o.charAt(y, x, z);
                        if (!ChunkSpans.isSolid(c)) {
                            continue;
                        }
                        total[1]++;
                        if (isMachineKey(CityVariants.blockKeyOf(c))) {
                            machine[1]++;
                        }
                    }
                }
            }
        }
        for (final RuinTemplate t : RuinShapes.ALL) {
            for (int y = 0; y < t.sizeY; y++) {
                for (int z = 0; z < t.sizeZ; z++) {
                    for (int x = 0; x < t.sizeX; x++) {
                        final char c = t.charAt(y, x, z);
                        if (!ChunkSpans.isSolid(c)) {
                            continue;
                        }
                        total[2]++;
                        if (isMachineKey(CityVariants.blockKeyOf(c))) {
                            machine[2]++;
                        }
                    }
                }
            }
        }
        return "小机型=" + pctOf(machine[0], total[0]) + " outpost=" + pctOf(machine[1], total[1]) + " ruin="
            + pctOf(machine[2], total[2]);
    }

    private static String pctOf(int a, int b) {
        return b == 0 ? "n/a" : fmt(100.0D * a / b, 1) + '%';
    }

    /** 一次渲染落进世界的体素集（空气清空位也记，但只作为【不能承重】的那一类）。 */
    private static final class VoxelSet {

        private final Map<Long, Boolean> solidAt = new HashMap<>();
        private final Map<Long, String> blockAt = new HashMap<>();
        private final List<long[]> coords = new ArrayList<>();

        private static long key(int x, int y, int z) {
            // 无符号打包：各 21 位偏移量，够 ±100 万，且不与真实世界坐标混淆
            return (x + 1048576L) << 42 | (y + 1048576L) << 21 | (z + 1048576L);
        }

        void put(int x, int y, int z, Object block) {
            final boolean solid = !PlacementGate.isAirHandle(block);
            solidAt.put(key(x, y, z), Boolean.valueOf(solid));
            blockAt.put(key(x, y, z), solid ? String.valueOf(block) : null);
            coords.add(new long[] { x, y, z });
        }

        boolean isEmpty() {
            return solidAt.isEmpty();
        }

        int solidCount() {
            int n = 0;
            for (final Boolean v : solidAt.values()) {
                if (v.booleanValue()) {
                    n++;
                }
            }
            return n;
        }

        /** {@code {实心块数, 其中机械件块数}}；只在 block 是 String 逻辑键时有效（未走解析链的那一臂）。 */
        int[] machineCensus() {
            int solid = 0;
            int machine = 0;
            for (final Map.Entry<Long, String> e : blockAt.entrySet()) {
                final String k = e.getValue();
                if (k == null || PlacementGate.isAirHandle(k)) {
                    continue;
                }
                solid++;
                if (isMachineKey(k)) {
                    machine++;
                }
            }
            return new int[] { solid, machine };
        }

        private boolean solidAt(int x, int y, int z) {
            final Boolean v = solidAt.get(key(x, y, z));
            return v != null && v.booleanValue();
        }

        /** 悬浮数（单一平坦地表顶，给 M1 探针用）：下方既不是地形也不是本次落块即为悬浮。 */
        int floatingWithFlatGround(int groundY) {
            int n = 0;
            for (final long[] p : coords) {
                if (!solidAt((int) p[0], (int) p[1], (int) p[2])) {
                    continue;
                }
                if (p[1] - 1 > groundY && !solidAt((int) p[0], (int) p[1] - 1, (int) p[2])) {
                    n++;
                }
            }
            return n;
        }

        /** 悬浮数（逐列读真实地形；接地定义与放置器同一口径：下方就是该列地表顶）。 */
        int floatingAgainst(CityVariants.GroundFn ground) {
            int n = 0;
            for (final long[] p : coords) {
                final int x = (int) p[0];
                final int y = (int) p[1];
                final int z = (int) p[2];
                if (!solidAt(x, y, z)) {
                    continue;
                }
                if (y - 1 > ground.groundY(x, z) && !solidAt(x, y - 1, z)) {
                    n++;
                }
            }
            return n;
        }

        /** 写进地表高度场以内的格数（含空气清空位）——非 0 即意味着挖洞或切断地表。 */
        int belowSurface(CityVariants.GroundFn ground) {
            int n = 0;
            for (final long[] p : coords) {
                if (p[1] <= ground.groundY((int) p[0], (int) p[2])) {
                    n++;
                }
            }
            return n;
        }
    }

    // ══════════════════════════════════ D 组：确定性 ══════════════════════════════════

    private static void groupD_determinism() {
        final String same1 = landingSignature(SEEDS[0], 0, 256);
        final String same2 = landingSignature(SEEDS[0], 0, 256);
        check(same1.equals(same2), "D1 同 seed 双跑的废墟落点+形态逐位相同");
        final String other = landingSignature(SEEDS[1], 0, 256);
        final String other2 = landingSignature(SEEDS[2], 0, 256);
        check(!same1.equals(other), "D2 跨 seed 必有差异（反「恒常数/恒空」假绿）");
        check(!other.equals(other2), "D2 跨 seed 差异不止一处（三 seed 两两不同）");
        System.out.println("DETERMINISM seed=" + SEEDS[0] + " 双跑 SHA=" + sha256(same1) + " equal="
            + same1.equals(same2) + " 跨seed SHA[0,16)=" + sha256(other).substring(0, 16) + "/"
            + sha256(other2).substring(0, 16));
    }

    /** 一段 chunk 带上的废墟意图序列（模板名 + 落点 + 旋转后尺寸 + 模板 SHA 前 12 位），作为形态指纹。 */
    private static String landingSignature(long seed, int czBase, int span) {
        final StringBuilder b = new StringBuilder(1 << 10);
        for (int cx = -span / 2; cx < span / 2; cx++) {
            final PlacementGate.Intent it = RuinPlacer.intentAt(seed, cx, czBase);
            if (it == null) {
                continue;
            }
            final RuinTemplate t = RuinShapes.byName(it.templateName);
            b.append(cx).append(',').append(it.originX).append(',').append(it.originZ).append(',')
                .append(it.templateName).append(',').append(it.sizeX).append('x').append(it.sizeZ).append(',')
                .append(t == null ? "?" : sha256(toCsv(t.layers)).substring(0, 12)).append(';');
        }
        return b.toString();
    }

    // ════════════════════════ E 组：micro 强度层收口（判据 6）════════════════════════════

    /**
     * micro 层收口要钉两面：<b>引用面</b>（结构侧消费点数 ≥ 1）与<b>行为面</b>（不同强度档的件数与
     * 规模档确实不同）。只钉引用面就是「接了线但没人用电」——那正是 P6 留到 P7c 的悬空层现场。
     */
    private static void groupE_microLayer() throws Exception {
        final String src = new String(
            Files.readAllBytes(Paths.get(
                "src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/ruins/ruin/RuinPlacer.java")),
            StandardCharsets.UTF_8);
        final int refs = countOf(stripCodeComments(src), "microStrengthAt");
        check(refs >= 1, "E1 ruin 侧 microStrengthAt 的代码内消费点 >= 1（实测 " + refs + "）⇒ 悬空层已收口");
        check(src.contains("GTSRBiomeAuthority") && src.contains(".microStrengthAt("),
            "E2 ruin 侧走 L1 的只读出口取强度，不自算强度算法");

        final GTSRWorldChunkManager mgr = SurfaceHarness.manager(true,
            SurfaceHarness.def(true, SurfaceHarness.prosperityBiomes(), SurfaceHarness.prosperityWeights()));
        final Map<Integer, int[]> byTier = new TreeMap<>();
        int tiersSeen = 0;
        final int span = 512;
        for (int cx = 0; cx < span; cx++) {
            final float s = mgr.microStrengthAt(cx, 7);
            final int tier = tierIndexOf(s);
            tiersSeen |= 1 << tier;
            final PlacementGate.Intent it = RuinPlacer.intentAt(SEEDS[3], cx, 7);
            final int[] acc = byTier.computeIfAbsent(tier, k -> new int[3]);
            if (it != null) {
                final RuinTemplate t = RuinShapes.byName(it.templateName);
                acc[0]++;
                acc[1] += t == null ? 0 : RuinShapes.scaleTier(t);
                if (t != null) {
                    acc[2] = Math.max(acc[2], RuinShapes.scaleTier(t));
                }
            }
        }
        check(Integer.bitCount(tiersSeen) >= 2,
            "E3 采样带里至少见到 2 个 micro 强度档（实测位图 " + Integer.toBinaryString(tiersSeen) + "）");
        check(byTier.size() >= 2, "E3 按 micro 档分桶后桶数 >= 2（实测 " + byTier.keySet() + "）");
        int hits = 0;
        for (final Map.Entry<Integer, int[]> e : byTier.entrySet()) {
            final int[] v = e.getValue();
            hits += v[0];
            System.out.println("MICRO-TIER tier=" + e.getKey() + " chunks=" + span + " ruinIntents=" + v[0]
                + " meanScaleTier=" + fmt(v[0] == 0 ? 0.0 : v[1] / (double)v[0], 2) + " maxScaleTier=" + v[2]
                + " candidatePool=" + RuinShapes.candidates(RuinShapes.allowedMaxScaleTier(e.getKey())).length);
        }
        check(hits > 0, "E4 采样带上废墟确有命中（实测 " + hits + " 次）⇒ 强度层不是接了根空线");
        // 规模档调制的<b>确定性</b>证据：三档候选集大小必须严格单调（弱 cell 拿不到大件）。
        // 这里不拿"实测均值"当断言——每档样本量是 512 格按 Config.prosperityRuinChance 分之一掷出的
        // 随机量（分母真值只在 Config 字段处，P16-B5b 起该键已改判、注释不再复写具体数字），
        // 千级以下的均值排序会翻。
        final int pool0 = RuinShapes.candidates(RuinShapes.allowedMaxScaleTier(0)).length;
        final int pool1 = RuinShapes.candidates(RuinShapes.allowedMaxScaleTier(1)).length;
        final int pool2 = RuinShapes.candidates(RuinShapes.allowedMaxScaleTier(2)).length;
        check(pool2 < pool1 && pool1 < pool0,
            "E4b 规模档调制严格单调：弱档候选 " + pool2 + " < 中档 " + pool1 + " < 强档 " + pool0
                + "（条）⇒ 强度确实在改「能出多大的废墟」");
        check(RuinShapes.candidates(RuinShapes.MAX_SCALE_TIER).length == RuinShapes.ALL.length,
            "E4c 强档候选集 == 全族（实测 " + pool0 + "/" + RuinShapes.ALL.length + "）");
        // 密度调制的<b>大样本</b>实测：多 seed 累计，强档件数必须高于弱档
        long strongHits = 0;
        long weakHits = 0;
        long strongChunks = 0;
        long weakChunks = 0;
        for (int si = 0; si < 8; si++) {
            for (int cx = 0; cx < span; cx++) {
                final int tier = tierIndexOf(mgr.microStrengthAt(cx, 7));
                final boolean want = RuinPlacer.intentAt(SEEDS[si], cx, 7) != null;
                if (tier == 0) {
                    strongChunks++;
                    if (want) {
                        strongHits++;
                    }
                } else if (tier == 2) {
                    weakChunks++;
                    if (want) {
                        weakHits++;
                    }
                }
            }
        }
        System.out.println("MICRO-DENSITY 强档 " + strongHits + "/" + strongChunks + " chunk = "
            + fmt(100.0 * strongHits / Math.max(1, strongChunks), 3) + "% vs 弱档 " + weakHits + "/" + weakChunks
            + " = " + fmt(100.0 * weakHits / Math.max(1, weakChunks), 3) + "%（同一 ruinChance 下的实测密度差）");
        check(strongHits > weakHits,
            "E4d 密度调制有行为差：8 seed × " + span + " chunk 上强档件数 " + strongHits + " 必须高于弱档 "
                + weakHits + "（否则 micro 只改规模不改密度，判据 6 的「件数确有差异」不成立）");
        // 反假绿：关掉调制开关（单变量）后，micro 分桶结果必须变 ⇒ 调制真的在起作用
        final boolean mod0 = Config.prosperityRuinMicroModulation;
        Config.prosperityRuinMicroModulation = false;
        final String flatSig = microBucketSignature(SEEDS[3], 7, span);
        Config.prosperityRuinMicroModulation = mod0;
        final String modSig = microBucketSignature(SEEDS[3], 7, span);
        check(!flatSig.equals(modSig),
            "E5 调制开关单变量：关掉 prosperityRuinMicroModulation 后 micro 分桶结果必须变（flat="
                + sha256(flatSig).substring(0, 12) + " mod=" + sha256(modSig).substring(0, 12) + "）");
        System.out.println("# E micro 强度层 verdict=closed refs=" + refs + " tiers=" + byTier.keySet()
            + " intents=" + hits + " 强档/弱档件数 " + strongHits + "/" + weakHits);
    }

    private static int tierIndexOf(float s) {
        if (s >= 1.15F) {
            return 0;
        }
        return s <= 0.85F ? 2 : 1;
    }

    private static String microBucketSignature(long seed, int cz, int span) {
        final StringBuilder b = new StringBuilder();
        for (int cx = 0; cx < span; cx++) {
            final PlacementGate.Intent it = RuinPlacer.intentAt(seed, cx, cz);
            final RuinTemplate t = it == null ? null : RuinShapes.byName(it.templateName);
            b.append(t == null ? '-' : RuinShapes.scaleTier(t)).append(';');
        }
        return b.toString();
    }

    // ══════════════════════════════ G 组：opt-in 边界 ══════════════════════════════

    private static void groupG_optInBoundary() {
        for (final long seed : new long[] { 0L, 1L, 7L, 4242L, -987654321L, Long.MAX_VALUE, Long.MIN_VALUE }) {
            check(RuinDamageOps.effectiveMissingRate(false, seed, 1.3F) == 0,
                "G1 allowsDamagedVariant=false ⇒ 生效缺失率恒 0（seed=" + seed + "）");
            check(RuinDamageOps.effectiveMissingRate(false, seed, 0.7F) == 0,
                "G1 opt-in 关掉时 micro 也不能把缺失率抬起来（seed=" + seed + "）");
            final int r = RuinDamageOps.effectiveMissingRate(true, seed, 1.0F);
            check(r == 5 || r == 20 || r == 40,
                "G2 opt-in 打开时缺失率必取 MISSING_RATES 的一档（实得 " + r + " seed=" + seed + "）");
        }
        int legacyTrue = 0;
        int ruinFalse = 0;
        for (final StructureRegistry.Entry e : StructureRegistry.all()) {
            if (PlacementGate.FAMILY_RUIN.equals(e.family)) {
                if (!e.allowsDamagedVariant) {
                    ruinFalse++;
                }
            } else if (e.allowsDamagedVariant) {
                legacyTrue++;
            }
        }
        check(legacyTrue == 0, "G3 旧 39 条目 allowsDamagedVariant 全为 false（实测 true " + legacyTrue + " 条）");
        check(ruinFalse == 0, "G4 ruin 条目 allowsDamagedVariant 全为 true（实测 false " + ruinFalse + " 条）");
        // G5：同一个 placeSeed 下，强/弱 micro 的缺失率不得总是相同（档位折算真的进了公式）
        int differ = 0;
        for (long s = 0; s < 300; s++) {
            if (RuinDamageOps.effectiveMissingRate(true, s, 1.3F) != RuinDamageOps
                .effectiveMissingRate(true, s, 0.7F)) {
                differ++;
            }
        }
        check(differ > 0, "G5 micro 参与缺失率折算（300 个 placeSeed 里有 " + differ + " 个给出不同档）");
        System.out.println("# G opt-in 边界 GREEN（旧名册不可达 + micro 参与档位折算 differ=" + differ + "）");
    }

    // ══════════════════════════ F 组：密度对照（判据 4）═════════════════════════

    /**
     * P16-B5b 申报：疏密三键<b>改判前</b>的代码默认（留档给对照臂用；作废理由见
     * {@link #groupF_density} 里 F8 那条，不是这里）。生产真值出处仍只有 {@code Config} 字段一处
     * （plan §1 G10），本文件这三份是"独立申报"，同 {@code SurfaceGateUnifyCheck} 的 {@code EXPECTED78}
     * 与 {@code Dim78ScatterDensityCheck} 的 {@code DECL_*} 一族纪律。
     */
    private static final int LEGACY_MACHINE_DENOM = 16;
    /** 见 {@link #LEGACY_MACHINE_DENOM}。 */
    private static final int LEGACY_OUTPOST_DENOM = 64;
    /** 见 {@link #LEGACY_MACHINE_DENOM}。 */
    private static final int LEGACY_RUIN_DENOM = 48;
    /**
     * F8 的申报带：改后"每 chunk 城外结构座数"（%，ON 档，4 seed × 4 区 = 4096 chunk）。
     * 实测 <b>2.0508%</b>（84 座 / 4096 chunk）；同一次运行注回旧疏密复算 = 6.3721%（261 座）。
     * 带宽取实测 ±20%（不是把旧值圈进来的宽带来）⇒ 旧值 3.11 倍于新值、稳稳在带外。
     */
    private static final double PIN_NEW_PER_CHUNK_MIN = 1.64D, PIN_NEW_PER_CHUNK_MAX = 2.46D;
    /**
     * F9b 的巨构<b>成型率</b>带（落地的跨片巨构座数 / 机器族座数，4 seed × 4 区 = 4096 chunk）。
     * <p>
     * 实测：改后（72/192/144）<b>27.273%</b>（12/44）；注回改前疏密（16/64/48）同批 chunk 复算
     * <b>15.000%</b>（24/160）。两臂<b>不相等</b>，而且这不是缺陷：成型率是"落地之后"的量，巨构除了
     * 那一次抽签还要多过一道 {@code spanAllowsAt} 覆盖让行（B1 设计），让行的通过率取决于邻格有没有
     * 别的结构——疏密降到 1/3 后邻格空出来，巨构的让行通过率上升，于是它在<b>落地</b>机器族里的份额
     * 从 15.0% 抬到 27.3%。<b>抽签</b>那一侧才是"同一 POOL 同一次 nextInt"的口径，它的两臂份额
     * 由 {@code Dim78ScatterDensityCheck} 的 D-PIN ④ 钉（实测新 28.431% / 旧 28.279%，漂移 0.152pp ≤
     * 该处的 2pp 上界），本处只钉成型率自身的量级带。
     * <p>
     * 带取 [20, 40]：新值 27.273 居带中（±28%），下界 20 咬住"巨构被让行门整段挤掉"（实测把
     * {@code prosperityStructureFamilyGapChunks} 一抬就会掉到带下），上界 40 咬住"巨构变成机器族主体"
     * （>2/7 理论份额的合理上限）。这条带<b>不</b>声称两臂相等——那正是首版写错并被实测打回的地方
     * （当时的断言要求两臂漂移 ≤2pp，改前臂 15.0% 直接判红）。
     */
    private static final double PIN_SPAN_SHARE_MIN = 20.0D, PIN_SPAN_SHARE_MAX = 40.0D;

    /**
     * 真地形上跑「关掉新族」与「开新族」两条档，同一批 chunk、同一份<b>只读</b>网格，
     * 唯一变量是 {@link Config#prosperityRuinsEnabled}。编排顺序逐字照
     * {@code ProsperityWorldGenerator.generate}：城窗抑制 → outpost → 机器 → 废墟，
     * 同一 {@link PlacementGate.ChunkGate}（预算 / 同族互斥 / 窗上限 / 间距都在里面）。
     */
    private static void groupF_density(int seeds, int regions) throws Exception {
        final int[] savedChances = new int[] { Config.prosperityMachineChance, Config.prosperityOutpostChance,
            Config.prosperityRuinChance };
        final int budget0 = Config.prosperityStructureBudgetPerChunk;
        final int cap0 = Config.prosperityStructureWindowRepeatCap;
        final int ruinCap0 = Config.prosperityRuinWindowRepeatCap;
        final int gap0 = Config.prosperityStructureFamilyGapChunks;
        final Map<String, Dens> runs = new LinkedHashMap<>();
        try {
            for (int si = 0; si < seeds; si++) {
                final long seed = SEEDS[si % SEEDS.length];
                final GTSRWorldChunkManager mgr = new GTSRWorldChunkManager(seed, SurfaceHarness.def(true,
                    SurfaceHarness.prosperityBiomes(),
                    SurfaceHarness.prosperityWeights()));
                for (int rg = 0; rg < regions; rg++) {
                    final int cx0 = si * 4096 + rg * AXIS;
                    final int cz0 = si * 924816;
                    final Block[] grid = new Block[SIDE * SIDE * 256];
                    final World world = Dim78ScatterDensityCheck.world(grid, cx0, cz0, seed, SIDE);
                    materialize(world, seed, mgr, cx0, cz0, grid);
                    runs.computeIfAbsent("OFF-未开新族", Dens::new).run(seed, world, cx0, cz0, false);
                    runs.computeIfAbsent("ON-开新族", Dens::new).run(seed, world, cx0, cz0, true);
                }
            }
        } finally {
            Config.prosperityMachineChance = savedChances[0];
            Config.prosperityOutpostChance = savedChances[1];
            Config.prosperityRuinChance = savedChances[2];
            Config.prosperityStructureBudgetPerChunk = budget0;
            Config.prosperityStructureWindowRepeatCap = cap0;
            Config.prosperityRuinWindowRepeatCap = ruinCap0;
            Config.prosperityStructureFamilyGapChunks = gap0;
            Config.prosperityRuinsEnabled = true;
        }
        final Dens off = runs.get("OFF-未开新族");
        final Dens on = runs.get("ON-开新族");
        for (final Dens d : runs.values()) {
            d.report();
        }
        check(off.structures > 0, "F1 对照基线非空（OFF 档实测 " + off.structures + " 座）");
        check(on.structures >= off.structures,
            "F2 开新族不得让城外结构总座数下降（OFF=" + off.structures + " ON=" + on.structures + "）");
        check(on.maxPerChunk <= Math.max(1, budget0),
            "F3 每 chunk 座数上界仍受同一份预算约束（实测 maxPerChunk=" + on.maxPerChunk + " budget=" + budget0
                + "）⇒ 新族是挤位不是叠罗汉");
        check(on.outpostLanded == off.outpostLanded && on.machineLanded == off.machineLanded,
            "F4 outpost/机器的座数在两档逐位相同（OFF " + off.outpostLanded + "/" + off.machineLanded + " vs ON "
                + on.outpostLanded + "/" + on.machineLanded
                + "）⇒ 前两环的掷骰与概率一字未改，上升全部来自废墟填补空槽");
        check(on.ruinLanded > 0, "F5 废墟真的落地了（实测 " + on.ruinLanded + " 座）——否则对照表是两张空表比相等");
        final long rise = on.structures - off.structures;
        check(rise == on.ruinLanded,
            "F6 座数上升量恰好等于废墟座数（rise=" + rise + " ruins=" + on.ruinLanded + "）");
        check(on.maxWinEmit <= Math.max(cap0, ruinCap0),
            "F7 窗内同模板放行数不越过两档上限里较松的那档（实测 " + on.maxWinEmit + " <= max(" + cap0 + ","
                + ruinCap0 + ")）");
        // ═══════════════ F8/F9 P16-B5b：申报式密度带 + 巨构共存（两面反假绿） ═══════════════
        // 立论：城外疏密三键从 16/64/48 改到 72/192/144（plan §0 U2、§3）是<b>本轮有意的行为改变</b>，
        // 所以这里不按"byte-parity 不许变"办，而是按 P13 死代码清扫轮那种<b>申报式差异断言</b>办：
        // 新值钉带、旧值留档并写明作废理由、同一次运行再跑一条"注回旧疏密"的对照臂并断言它<b>落在带外</b>
        // ——带若连"把概率调回去"都测不出来，它就是恒真式，那才是本片禁止的事。
        // 对照臂的手法：进程内反射式直写 Config 三键（它们仍是 public static int，U6 的"不可配"只靠
        // 不注册进 gtsr.cfg 达成），跑完 finally 立即还原；<b>不改工作树、不碰影子树</b>。
        final Dens legacy = new Dens("OLD-改前疏密");
        try {
            for (int si = 0; si < seeds; si++) {
                final long seed = SEEDS[si % SEEDS.length];
                final GTSRWorldChunkManager mgr = new GTSRWorldChunkManager(seed, SurfaceHarness.def(true,
                    SurfaceHarness.prosperityBiomes(),
                    SurfaceHarness.prosperityWeights()));
                for (int rg = 0; rg < regions; rg++) {
                    final int cx0 = si * 4096 + rg * AXIS;
                    final int cz0 = si * 924816;
                    final Block[] grid = new Block[SIDE * SIDE * 256];
                    final World world = Dim78ScatterDensityCheck.world(grid, cx0, cz0, seed, SIDE);
                    materialize(world, seed, mgr, cx0, cz0, grid);
                    Config.prosperityMachineChance = LEGACY_MACHINE_DENOM;
                    Config.prosperityOutpostChance = LEGACY_OUTPOST_DENOM;
                    Config.prosperityRuinChance = LEGACY_RUIN_DENOM;
                    legacy.run(seed, world, cx0, cz0, true);
                }
            }
        } finally {
            Config.prosperityMachineChance = savedChances[0];
            Config.prosperityOutpostChance = savedChances[1];
            Config.prosperityRuinChance = savedChances[2];
            Config.prosperityRuinsEnabled = true;
        }
        legacy.report();
        final double newPp = 100.0 * on.structures / Math.max(1L, on.chunks);
        final double oldPp = 100.0 * legacy.structures / Math.max(1L, legacy.chunks);
        System.out.println("DENSITY-PIN 每 chunk 座数：改前 " + fmt(oldPp, 4) + "% → 改后 " + fmt(newPp, 4)
            + "%（旧/新 = " + fmt(oldPp / Math.max(1.0E-9D, newPp), 3) + "×；申报带 ["
            + fmt(PIN_NEW_PER_CHUNK_MIN, 4) + ", " + fmt(PIN_NEW_PER_CHUNK_MAX, 4) + "]%）");
        check(newPp >= PIN_NEW_PER_CHUNK_MIN && newPp <= PIN_NEW_PER_CHUNK_MAX,
            "F8 申报式密度带：改后每 chunk 座数 " + fmt(newPp, 4) + "% ∈ [" + PIN_NEW_PER_CHUNK_MIN + ", "
                + PIN_NEW_PER_CHUNK_MAX + "]（旧值留档 " + fmt(oldPp, 4) + "%；作废理由 = plan §0 U2 把城外"
                + "疏密降到实机现值的 1/3，且 B1 新入 2 条跨片巨构改变了同一次抽签的候选集）");
        check(oldPp < PIN_NEW_PER_CHUNK_MIN || oldPp > PIN_NEW_PER_CHUNK_MAX,
            "F8b 两面反假绿：改前疏密实测 " + fmt(oldPp, 4) + "% 必须落在新带之外（落在带内 ⇒ 本带对\"把概率"
                + "调回去\"不敏感＝恒真判据）");
        // ── F9 巨构与 5 个小机型共存（B1 申报"同一 POOL 同一次 nextInt、同一 prosperityMachineChance
        //    分母"的实测面）：成型率两臂同带、单 chunk 自有锚点上界仍是预算、三环互斥没有多出第四环 ──
        final double spanShare = 100.0 * on.colossusLanded / Math.max(1L, on.machineLanded);
        final double oldSpanShare = 100.0 * legacy.colossusLanded / Math.max(1L, legacy.machineLanded);
        System.out.println("COLOSSUS-PIN 机器族座数=" + on.machineLanded + " 其中跨片巨构=" + on.colossusLanded
            + " 成型率=" + fmt(spanShare, 3) + "%｜改前对照臂 机器=" + legacy.machineLanded + " 巨构="
            + legacy.colossusLanded + " 成型率=" + fmt(oldSpanShare, 3) + "%｜单 chunk 自有锚点上界实测="
            + on.maxPerChunk + "（预算 " + budget0 + "）｜只被邻槽补片压到、自己没出结构的 chunk="
            + on.foreignOnlyChunks + "（" + fmt(100.0 * on.foreignOnlyChunks / Math.max(1L, on.chunks), 3)
            + "%，落块 " + on.foreignOnlySolid + "）");
        check(on.maxPerChunk <= Math.max(1, budget0),
            "F9a 每 chunk 自有锚点（真问过门的那一座）座数上界仍是 " + Math.max(1, budget0) + "（实测 "
                + on.maxPerChunk + "）⇒ 巨构与 5 个小机型抢同一次抽签，没有各开一条概率线");
        check(spanShare >= PIN_SPAN_SHARE_MIN && spanShare <= PIN_SPAN_SHARE_MAX,
            "F9b 巨构成型率 ∈ [" + PIN_SPAN_SHARE_MIN + ", " + PIN_SPAN_SHARE_MAX + "]（实测 "
                + fmt(spanShare, 3) + "%，12/44 座）；改前疏密同批实测 " + fmt(oldSpanShare, 3)
                + "% 是<b>另一档</b>而不是同一量的两臂——成型率要过多邻格覆盖让行，稀疏后通过率上升，"
                + "两臂相等从来不是应有的形状（理由全文见 PIN_SPAN_SHARE_* 的注释）");
        check(oldSpanShare < spanShare,
            "F9c 方向自检：改前疏密的成型率 " + fmt(oldSpanShare, 3) + "% 必须<b>低于</b>改后 "
                + fmt(spanShare, 3) + "%（让行门在密档更常咬住巨构）——反了说明我把两臂的注键顺序写反，"
                + "上面的 F9b 就成了拿错读数钉的带");
        check(on.outpostLanded + on.machineLanded + on.ruinLanded == on.structures,
            "F9d 三环互斥未破：outpost+机器+废墟 == 总座数（实测 " + on.outpostLanded + "+" + on.machineLanded
                + "+" + on.ruinLanded + " vs " + on.structures + "）⇒ 巨构入池没有多出第四环");
    }

    /** 一档（OFF/ON）的累计量。 */
    private static final class Dens {
        final String label;
        long chunks;
        long citySkips;
        long structures;
        long outpostLanded;
        long machineLanded;
        long ruinLanded;
        /** P16-B5b：机器族里真落地的<b>跨片巨构</b>锚点数（按门登记的模板名判，不数补片）。 */
        long colossusLanded;
        /** P16-B5b：自己没出任何结构、但被邻槽巨构补片压到的 chunk 数。 */
        long foreignOnlyChunks;
        /** P16-B5b：上一行那些 chunk 里落进的块数（申报"补片面"的量级）。 */
        long foreignOnlySolid;
        long adjacent;
        long solidSum;
        int maxPerChunk;
        int maxWinEmit;
        long winCount;
        long winSum;
        final Map<Long, Map<String, Integer>> winEmit = new HashMap<>();
        final Set<String> landedKeys = new HashSet<>();

        Dens(String label) {
            this.label = label;
        }

        void run(long seed, World world, int cx0, int cz0, boolean ruinsOn) {
            Config.prosperityRuinsEnabled = ruinsOn;
            for (int dx = 0; dx < AXIS; dx++) {
                for (int dz = 0; dz < AXIS; dz++) {
                    final int cx = cx0 + dx;
                    final int cz = cz0 + dz;
                    chunks++;
                    if (CityPlanner.citiesNear(seed, cx, cz).length > 0) {
                        citySkips++;
                        continue; // 与编排器同源的城窗抑制判据
                    }
                    final PlacementGate.ChunkGate gate = PlacementGate
                        .beginChunk(SurfaceGate.DIM78, seed, cx, cz);
                    final RecordSink sink = new RecordSink();
                    final boolean op = ProsperityOutpostPlacer.placeAll(world, seed, cx, cz, sink, gate);
                    boolean landed = op;
                    boolean mc = false;
                    if (!op) {
                        mc = RuinedMachinePlacer.placeAll(world, seed, cx, cz, machineWeight(cx, cz), sink, gate);
                        landed = mc;
                    }
                    boolean ru = false;
                    if (!landed) {
                        ru = RuinPlacer.placeAll(world, seed, cx, cz, sink, gate);
                        landed = ru;
                    }
                    int perChunk = 0;
                    boolean spanAsked = false;
                    for (final String t : gate.requestedTemplates()) {
                        perChunk++;
                        if (RuinedColossusShapes.isColossus(t)) {
                            spanAsked = true;
                        }
                        final long wk = windowKey(seed, cx, cz);
                        final Map<String, Integer> m = winEmit.computeIfAbsent(wk, k -> new HashMap<>());
                        final int n = m.merge(t, 1, Integer::sum);
                        if (n > maxWinEmit) {
                            maxWinEmit = n;
                        }
                    }
                    if (!landed) {
                        if (sink.solid > 0) {
                            // 只有别人的巨构片压进来：一座都不算本 chunk 的，但块是真的落了
                            foreignOnlyChunks++;
                            foreignOnlySolid += sink.solid;
                        }
                        continue;
                    }
                    structures++;
                    solidSum += sink.solid;
                    if (perChunk > maxPerChunk) {
                        maxPerChunk = perChunk;
                    }
                    if (op) {
                        outpostLanded++;
                    } else if (mc) {
                        machineLanded++;
                        // 每 chunk 至多记一次（模板名循环里可能同时出现两条巨构请求），
                        // 所以这里按"本 chunk 落地的机器是不是跨片"计，不按名字计数
                        if (spanAsked) {
                            colossusLanded++;
                        }
                    } else {
                        ruinLanded++;
                    }
                    landedKeys.add(cx + "x" + cz + "x" + seed);
                }
            }
        }

        /** 8 邻贴脸：两档各自在 run 全部结束后数一次（跨区/跨 seed 都用完整 key，不会串门）。 */
        void countAdjacent() {
            adjacent = 0;
            for (final String k : landedKeys) {
                final int ix = k.indexOf('x');
                final int iz = k.indexOf('x', ix + 1);
                final int cx = Integer.parseInt(k.substring(0, ix));
                final int cz = Integer.parseInt(k.substring(ix + 1, iz));
                final String seed = k.substring(iz + 1);
                for (int ddx = -1; ddx <= 1; ddx++) {
                    for (int ddz = -1; ddz <= 1; ddz++) {
                        if (ddx == 0 && ddz == 0) {
                            continue;
                        }
                        if (landedKeys.contains((cx + ddx) + "x" + (cz + ddz) + "x" + seed)) {
                            adjacent++;
                        }
                    }
                }
            }
        }

        void report() {
            countAdjacent();
            winCount = winEmit.size();
            winSum = 0;
            for (final Map<String, Integer> m : winEmit.values()) {
                for (final int n : m.values()) {
                    winSum += n;
                }
            }
            System.out.println("DENSITY 档=" + label + " chunk=" + chunks + " 城窗跳过=" + citySkips + " 总座数="
                + structures + " (outpost=" + outpostLanded + " machine=" + machineLanded + " ruin=" + ruinLanded
                + ")" + " 窗均座数=" + winMean() + " 贴脸率=" + adjPp() + "% 窗内同模板max=" + maxWinEmit
                + " 每chunk max=" + maxPerChunk + " 落块/chunk=" + fmt(solidSum / (double)Math.max(1, chunks), 4));
        }

        String winMean() {
            return fmt(structures / (double)Math.max(1, winCount), 3);
        }

        String adjPp() {
            return fmt(100.0 * adjacent / (double)Math.max(1, structures), 3);
        }
    }

    /** 群系机器权重（走 L1 唯一出口 + <b>生产</b>权重表，与 {@code RuinedMachinePlacer} 重放口同一口径）。 */
    private static float machineWeight(int cx, int cz) {
        final GTSRBiomeAuthority.Resolution res = GTSRBiomeAuthority.forDimKey(GTSRBiomeAuthority.DIM_KEY_PROSPERITY)
            .ordinalAt((cx << 4) + 8, (cz << 4) + 8);
        return ProsperityWorldGenerator.weightForRosterIndex(res.ordinal, ProsperityWorldGenerator.MACHINE_WEIGHTS);
    }

    private static long windowKey(long seed, int cx, int cz) {
        return seed * 1_000_003L + ((long)Math.floorDiv(cx, AXIS) << 20) + Math.floorDiv(cz, AXIS);
    }

    /** 真实 generateTerrain + 真实表层缝 → 平坦网格（口径逐字同 {@code PlacementContractCheck.Sample}）。 */
    private static void materialize(World world, long seed, GTSRWorldChunkManager mgr, int cx0, int cz0, Block[] grid)
        throws Exception {
        final Method gen = SurfaceHarness.generateTerrain();
        final GTSRChunkProviderBase provider = new ChunkProviderProsperityRuins(world, seed);
        final Method seam = SurfaceHarness.surfaceSeam(provider.getClass());
        final Block[] blocks = new Block[65536];
        final byte[] metas = new byte[65536];
        for (int dx = 0; dx < AXIS; dx++) {
            for (int dz = 0; dz < AXIS; dz++) {
                final int cx = cx0 + dx;
                final int cz = cz0 + dz;
                Arrays.fill(blocks, null);
                Arrays.fill(metas, (byte)0);
                gen.invoke(provider, cx, cz, blocks, metas, null);
                final BiomeGenBase[] plane = mgr.loadBlockGeneratorData(null, cx * 16, cz * 16, 16, 16);
                seam.invoke(null, seed, cx * 16, cz * 16, blocks, metas, plane);
                final int bx = dx * 16;
                final int bz = dz * 16;
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        final int col = (lx << 12) | (lz << 8);
                        final int dstCol = (bx + lx) + (bz + lz) * SIDE;
                        for (int y = 0; y < 256; y++) {
                            grid[y * SIDE * SIDE + dstCol] = blocks[col | y];
                        }
                    }
                }
            }
        }
    }

    /** 落块记录 Sink（非空气计数）；与 {@code PlacementContractCheck.RecordSink} 同一口径。 */
    static final class RecordSink implements BlockSink {
        int accepted;
        int solid;
        int dropped;

        @Override
        public boolean setBlock(int x, int y, int z, Object block, int meta, int flags) {
            if (!(block instanceof Block)) {
                dropped++;
                return false;
            }
            accepted++;
            if (block != Blocks.air) {
                solid++;
            }
            return true;
        }
    }

    // ═════════════════════════════════ 装配与工具件 ═════════════════════════════════

    private static void bootstrap() {
        Dim78ScatterDensityCheck.bootstrap(); // SurfaceHarness.initVanillaBlocks + blockFamily + extraBlocks
        // 群系配槽必须登记：SurfaceHarness.prosperityBiomes() 造的四个群系要走 recordAllAllocations
        // 才拿到 L0 实际 id 与 L1 账本，loadBlockGeneratorData 才会给出<b>本维</b>的 top 方块。
        // 漏掉它的现场（本工具首轮实测）：城外结构三族座数全 0——表层缝上的是 vanilla 默认草皮，
        // SurfaceGate 的自然 top 集合不认 ⇒ 就绪门整段拒 ⇒ 密度对照表退化成两张空表比相等。
        SurfaceHarness.recordAllAllocations(
            SurfaceHarness.prosperityBiomes(),
            SurfaceHarness.shatteredBiomes());
        RuinedMachinePlacer.registerVariants();
        CityVariants.registerVariants();
        ProsperityOutpostPlacer.registerVariants();
        RuinPlacer.registerVariants();
        pinMotherSha();
    }

    private static void pinMotherSha() {
        for (final RuinedMachineShapes.Shape s : RuinedMachineShapes.ALL) {
            MOTHER_SHA.put(s.name, sha256(toCsv(s.layers)));
        }
        for (final ProsperityOutpostPlacer.Outpost o : ProsperityOutpostPlacer.ALL) {
            MOTHER_SHA.put(o.name, sha256(toCsv(o.layers)));
        }
    }

    /** 粗粒度去注释（只去 {@code //} 与块注释），用于「代码内消费点」计数而不是文档提及计数。 */
    private static String stripCodeComments(String s) {
        return s.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    private static int countOf(String hay, String needle) {
        int n = 0;
        for (int i = hay.indexOf(needle); i >= 0; i = hay.indexOf(needle, i + needle.length())) {
            n++;
        }
        return n;
    }

    private static String fmt(double v, int digits) {
        return String.format(Locale.ROOT, "%." + digits + "f", v);
    }

    private static String sha256(String s) {
        try {
            final byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            final StringBuilder sb = new StringBuilder(64);
            for (final byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void check(boolean ok, String msg) {
        if (ok) {
            passed++;
            return;
        }
        FAILURES.add(msg);
        System.out.println("  FAIL " + msg);
    }

    private static void finish(String mode) {
        System.out.println("RUINFAMILY " + ("all".equals(mode) ? "CHECK" : "FAST")
            + (FAILURES.isEmpty() ? " PASS" : " FAIL") + ": assertions=" + passed + " failures=" + FAILURES.size()
            + " mode=" + mode + " ruins=" + RuinShapes.ALL.length + " roster=" + StructureRegistry.names().size());
        if (!FAILURES.isEmpty()) {
            for (final String f : FAILURES) {
                System.out.println("  - " + f);
            }
            System.exit(1);
        }
        System.out.println("RUINFAMILY DONE");
    }
}
