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
import com.miaokatze.gtsr.common.dimension.framework.structure.PlacementGate;
import com.miaokatze.gtsr.common.dimension.framework.structure.StructureRegistry;
import com.miaokatze.gtsr.common.dimension.prosperity.ChunkProviderProsperityRuins;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperityOutpostPlacer;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperitySurfaceScatter;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperityWorldGenerator;
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
        // 这里不拿"实测均值"当断言——每档样本量是 512/(1/48)≈千级以下的随机量，均值排序会翻。
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
     * 真地形上跑「关掉新族」与「开新族」两条档，同一批 chunk、同一份<b>只读</b>网格，
     * 唯一变量是 {@link Config#prosperityRuinsEnabled}。编排顺序逐字照
     * {@code ProsperityWorldGenerator.generate}：城窗抑制 → outpost → 机器 → 废墟，
     * 同一 {@link PlacementGate.ChunkGate}（预算 / 同族互斥 / 窗上限 / 间距都在里面）。
     */
    private static void groupF_density(int seeds, int regions) throws Exception {
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
        System.out.println("DENSITY-RISE 总座数 OFF=" + off.structures + " ON=" + on.structures + " 上升=" + rise + " ("
            + fmt(100.0 * rise / Math.max(1, off.structures), 2) + "%) 废墟=" + on.ruinLanded + " 窗均="
            + off.winMean() + "->" + on.winMean() + " 贴脸率=" + off.adjPp() + "%->" + on.adjPp()
            + "% 窗内同模板max=" + off.maxWinEmit + "->" + on.maxWinEmit + " 每chunk max=" + off.maxPerChunk + "->"
            + on.maxPerChunk + "（约束：预算 " + budget0 + "/chunk、窗上限 " + cap0 + "(族)/" + ruinCap0
            + "(ruin)、同族间距 " + gap0 + " 档）");
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
                    for (final String t : gate.requestedTemplates()) {
                        perChunk++;
                        final long wk = windowKey(seed, cx, cz);
                        final Map<String, Integer> m = winEmit.computeIfAbsent(wk, k -> new HashMap<>());
                        final int n = m.merge(t, 1, Integer::sum);
                        if (n > maxWinEmit) {
                            maxWinEmit = n;
                        }
                    }
                    if (!landed) {
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
