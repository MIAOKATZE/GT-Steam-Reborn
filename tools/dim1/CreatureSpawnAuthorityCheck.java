import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.block.Block;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority.BiomeId;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeBase;
import com.miaokatze.gtsr.common.dimension.framework.GTSRChunkProviderBase;
import com.miaokatze.gtsr.common.dimension.framework.GTSRDimensionDef;
import com.miaokatze.gtsr.common.dimension.framework.GTSRWorldChunkManager;
import com.miaokatze.gtsr.common.dimension.framework.SurfaceGate;
import com.miaokatze.gtsr.common.dimension.prosperity.ChunkProviderProsperityRuins;
import com.miaokatze.gtsr.common.dimension.prosperity.entity.EntityGearPigeon;
import com.miaokatze.gtsr.common.dimension.prosperity.entity.EntitySlagRidgeHunter;
import com.miaokatze.gtsr.common.dimension.prosperity.entity.EntitySteamFirefly;
import com.miaokatze.gtsr.common.dimension.prosperity.entity.GTSRCreatureRegistry;
import com.miaokatze.gtsr.common.dimension.prosperity.entity.GTSRCreatureRenderers;
import com.miaokatze.gtsr.common.dimension.prosperity.entity.GTSRCreatureRoster;
import com.miaokatze.gtsr.common.dimension.prosperity.entity.GTSRCreatureRoster.Species;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityPlan;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityPlanner;
import com.miaokatze.gtsr.config.Config;

/**
 * <b>P9 生物层（L7）的主断言工具</b>（plan §5 P9 / §2.1 L1+L7 / §7.1 U-B/U-D，任务包 8 条判据的
 * 离线可机检部分）。一次性自检 main，不进 jar，tools/ 惯例；一键入口
 * {@code tools/dim1/surface_checks.sh} 的 [16] 段。
 * <p>
 * ═══ 为什么"生物"必须靠结构断言而不是冒烟 ═══
 * wiki {@code architecture/dedicated-server-entity-tick-freeze-gate.md} §1 实证：无玩家的维度里
 * 实体被 1200t 冻结 ⇒ <b>无头冒烟看不见任何生物行为</b>。本片因此把验收钉在两条腿：
 * ① 本工具（注册/表内容/渲染器/纹理/幂等/收口的离线断言）；② 用户实机目检（回执里单列）。
 * <b>本工具的任何一条 PASS 都不构成"生物行为已验证"</b>。
 * <p>
 * ═══ 七组断言与判据的对应 ═══
 * <ul>
 * <li><b>A 主世界零污染</b>（判据 1）：注册/填充<b>前后</b>，遍历 {@code BiomeGenBase} 全表里
 * <b>所有非我方</b>群系的 4 张 spawnable 列表，逐位比 {@code 类名*权重(min-max)} 签名 ⇒ 差异必须 0；
 * 读面也避开自家入口（反射直读 {@code BiomeGenBase} 的 protected 字段），否则"用我方覆写读我方
 * 覆写"会结构性失明；另有<b>灵敏度自检</b>（往一个原版群系塞一条再摘掉，检测器必须恰好报 1 行差）
 * 与源级钉（全仓不出现 {@code EntityRegistry.addSpawn} / {@code registerGlobalEntityID}）；</li>
 * <li><b>B 我方群系刷怪表</b>（判据 2）：dim78 四群系 × 4 张表与 {@code roster.declaredEntries}
 * <b>逐位一致</b>（类、权重、min、max、顺序），并申报带乘子表；dim79 四群系恒空（零跨界）；
 * 模式 {@code off} 单进程跑总开关回退档（关 ⇒ 四张表全空 = P8 基线，且原版仍零差异）；</li>
 * <li><b>C L1 收口</b>（判据 3）：源级——{@code getPossibleCreatures} 体内无
 * {@code getBiomeGenForCoords}／无 {@code instanceof}／无 {@code biomeID} 减法，且出现
 * {@code ordinalAt} 与生效表出口；账本注释里"byte 平面读面归 P9"的旧边界条目已收口；
 * 行为级——离线 mock World <b>没有任何 Chunk byte 平面</b>，仍按坐标拿到与名册一致的表；</li>
 * <li><b>D 渲染器与纹理</b>（判据 4）：FML {@code RenderingRegistry} 在册条目里逐档查到自己的
 * {@code Render}，反射调其 {@code getEntityTexture} 取回纹理，再对该路径做
 * <b>实际可解析且非零字节</b>断言（{@link #resolveTexture}：先 classpath，回落
 * {@code src/main/resources/}，并打印走了哪条通道）。D1 起三档都是 {@code gtsr} 域自有皮肤，
 * 故本组同时钉 {@code domain == "gtsr"} 与"渲染器返回的整串 ResourceLocation == 名册申报"
 * （旧判据只比 {@code getResourcePath()}，换 domain 不红 ⇒ 借皮切自有时会静默漏钉）。
 * 同时钉渲染器必须是<b>我方</b>薄壳（原版具体渲染器会向下转型成原版实体）与模型确为申报的那一个
 * <b>类名</b>（D2 起申报值可以是我方复刻件 ⇒ "是不是原版"不再是安全条件，I1 才是）；</li>
 * <li><b>E DataWatcher 与 id</b>（判据 5）：三实体离线实例化后枚举 DataWatcher 全索引 ⇒
 * 自定义索引全部 ≥16 且不与 vanilla 链相撞；源级再扫一遍 {@code addObject(} 的字面量；
 * id 快照 = 自增、无重复、名字无重复，且离线不触发 FML 侧注册（无蛋 ⇒ 不占全局 id 段）；
 * 另钉 {@code EnumCreatureType.getCreatureClass()} 的基类契约与 {@code (World)} 反射构造器；</li>
 * <li><b>F 幂等与稳定</b>（判据 6）：填充链重跑 3 次条目不变、注册链重跑 3 次 id 快照不变；
 * 同 seed 下 {@code getPossibleCreatures} 对 16×16 chunk × 4 类别双跑逐位一致；</li>
 * <li><b>G 城窗 ×2 与结构联动数据</b>：城窗内 chunk 的生效权 == 声明表 ×2、窗外 == 该带声明表
 * （且声明表本体一字未改）；结构联动封顶恒 ≤2/座、未申报族为 0——
 * <b>本组只断言数据结构与纯函数，不声称行为已在游戏内生效</b>。</li>
 * <li><b>I 判据 4/5 的结构性盲区补钉（D2）</b>：v1.20.35 三条高危能一路离线全绿到实机，根因是本工具
 * 旧判据 4 只钉「渲染器类名前缀」与「模型 SimpleName 相符」，<b>从不看所选模型对实体做什么</b>，
 * 判据 6 只钉「串名唯一」，<b>从不看名牌键怎么拼、lang 有没有</b>。本组补六条：
 * I1 模型继承链上两个每帧热方法（{@code render}/{@code setLivingAnimations}）不得强转非自家实体类型
 * （C-01 {@code ModelBat.java:73} / C-02 {@code ModelSkeleton.java:44}，1.7.10 的
 * {@code RenderManager.java:300-304} 不吞异常 ⇒ 上屏即崩）；
 * I2 模型申报画布 == 绑定贴图的 IHDR 尺寸（旧判据只到"非零字节"，换尺寸导致的 UV 错位抓不到）；
 * I3 名册串名不得自带 modid（FML {@code EntityRegistry.java:164} 会自动前缀 ⇒ 双前缀）且两份 lang
 * 必须有 {@code entity.<modid>.<串名>.name}（C-04）；
 * I4 齿轮鸽的自家地表谓词必须放行<b>本维声明真值白名单全部成员</b>并拒绝原版草（C-03）；
 * I5 飞行实体的位移不得只喂 {@code getMoveHelper()}（该类零 motion 写入 ⇒ 原地不动，C-05）；
 * I6 名册必须以 {@code WorldServer.java:169} 与 {@code getMaxNumberOfCreature} 两条出处记载 creature 档的
 * 密度压制（<b>不钉 "400"/"10" 字面量</b>——那会让日后"只把 400 tick 改述成 20 秒"的一次注释订正
 * 把本判据弄成假红；钉引用出处即可承载"口径已写进名册"这条真值，清单 C-07）。
 * <b>D1 皮肤片（本片）已落地的口径</b>：三档皮肤改走 {@code gtsr} 域自有路径，判据 4 的
 * 「domain 恒 minecraft」随之翻成「恒 {@code gtsr}」，{@code Species.vanillaTexturePath()}
 * 改名 {@code skinTexturePath()}（名字不再宣称一件已不成立的事）。I2 的
 * 「名册申报画布 == 模型实测 == 贴图 IHDR」三方对钉<b>原样保留并被新皮重跑过</b>——
 * 它是 D1 画布尺寸的唯一机检出口，不许绕过。</li>
 * </ul>
 * <p>
 * <b>货币口径（诚实性申报）</b>：权重单位 = vanilla {@code SpawnListEntry.itemWeight}（同类别内的
 * 相对抽签权）；"逐位一致"= 同实例类、同顺序、四个数字全等；本工具<b>不</b>测量任何刷怪率、出没
 * 频率或渲染像素——那些只有实机目检能给。皮肤同理：本工具只钉 IHDR 与可解析性，
 * <b>不</b>判断像素观感（缩放/镜像/光照下的读感一律只能实机看）。
 * <p>
 * <b>运行</b>（MC classpath + 原版资源根 {@code build/resources/patchedMc}；D1 自有皮肤不在离线
 * classpath 上，由 {@link #resolveTexture} 回落仓库 {@code src/main/resources/} 读取，
 * 故 lwjgl 供 {@code Render} 实例化即可，无需额外挂资源根）：
 * <pre>
 * java -cp "temp/p4-surface/tools;temp/p4-surface/classes;build/resources/patchedMc;$CP" \
 *   CreatureSpawnAuthorityCheck [all|off|source]
 * </pre>
 */
public final class CreatureSpawnAuthorityCheck {

    /** 四类刷怪列表（与 GTSRBiomeBase 清空的四张一一对应）。 */
    private static final EnumCreatureType[] TYPES = { EnumCreatureType.monster, EnumCreatureType.creature,
        EnumCreatureType.ambient, EnumCreatureType.waterCreature };

    /** dim78 名册（顺序 = rosterIndex）。 */
    private static final BiomeId[] PROSPERITY_KEYS = SurfaceHarness.PROSPERITY_KEYS;

    /** vanilla 在 Entity→EntityLivingBase→EntityLiving→EntityAgeable 链上用掉的索引（本轮实测枚举所得）。 */
    private static final Set<Integer> VANILLA_WATCHER_IDS = new LinkedHashSet<>(
        Arrays.asList(0, 1, 6, 7, 8, 9, 10, 11, 12));

    private static final List<String> FAILURES = new ArrayList<>();
    private static int passed;

    public static void main(String[] args) throws Exception {
        final String mode = args.length > 0 ? args[0] : "all";
        SurfaceHarness.initVanillaBlocks();
        SurfaceHarness.blockFamily();
        if ("off".equals(mode)) {
            scenarioOff();
        } else if ("source".equals(mode)) {
            groupCSourceLevel();
            groupESourceLevel();
        } else {
            runAll();
        }
        finish(mode);
    }

    // ————————————————————————— 场景 —————————————————————————

    private static Harness wired() throws Exception {
        final Harness h = new Harness();
        h.p = SurfaceHarness.prosperityBiomes();
        h.s = SurfaceHarness.shatteredBiomes();
        SurfaceHarness.recordAllAllocations(h.p, h.s);
        final GTSRDimensionDef def78 = SurfaceHarness.def(true, h.p, SurfaceHarness.prosperityWeights());
        h.mgr78 = SurfaceHarness.manager(true, def78);
        GTSRBiomeAuthority.bind(GTSRBiomeAuthority.DIM_KEY_PROSPERITY, 78, h.mgr78::biomeAt);
        final GTSRDimensionDef def79 = SurfaceHarness
            .def(false, h.s, SurfaceHarness.shatteredWeights());
        h.mgr79 = SurfaceHarness.manager(false, def79);
        GTSRBiomeAuthority.bind(GTSRBiomeAuthority.DIM_KEY_SHATTERED, 79, h.mgr79::biomeAt);
        check(def78.getBiomeTable()
            .size() == 4, "装配：dim78 def 群系表不是 4 项");
        check(GTSRBiomeAuthority.forDimKey(GTSRBiomeAuthority.DIM_KEY_PROSPERITY)
            .degraded() == GTSRBiomeAuthority.Degraded.NONE, "装配：dim78 不在 NONE 态，后续断言不可信");
        return h;
    }

    private static final class Harness {

        BiomeGenBase[] p;
        BiomeGenBase[] s;
        GTSRWorldChunkManager mgr78;
        GTSRWorldChunkManager mgr79;
    }

    private static void runAll() throws Exception {
        final Harness h = wired();
        // —— A 判据 1 的"前"快照：此刻策略未装、任何表都还没填 ——
        final Snapshot before = snapshotVanillaBiomes();
        check(before.biomeCount() > 20, "A0 原版群系覆盖面异常（只扫到 " + before.biomeCount() + " 个）");

        GTSRBiomeBase.setCreatureSpawnPolicy(GTSRCreatureRegistry.POLICY);
        GTSRCreatureRegistry.planRegistrations();

        groupA(before);
        groupB(h.p, h.s);
        groupC(h);
        groupD();
        groupE();
        groupF(h);
        groupG(h);
        groupH(h.p);
        groupI();
    }

    /** 判据 2 的回退档：总开关关闭 ⇒ 四群系 4 张表逐位为空（= P8 的无条件清空态）。 */
    private static void scenarioOff() throws Exception {
        Config.prosperityCreaturesEnabled = false;
        final Harness h = wired();
        final Snapshot before = snapshotVanillaBiomes();
        GTSRBiomeBase.setCreatureSpawnPolicy(GTSRCreatureRegistry.POLICY);
        GTSRCreatureRegistry.planRegistrations();
        for (int i = 0; i < h.p.length; i++) {
            for (final EnumCreatureType type : TYPES) {
                final List<BiomeGenBase.SpawnListEntry> list = h.p[i].getSpawnableList(type);
                check(list != null && list.isEmpty(),
                    "B2 总开关关闭后 " + PROSPERITY_KEYS[i] + '.' + type + " 仍有条目：" + signature(list));
            }
            check(((GTSRBiomeBase) h.p[i]).areCreatureSpawnsApplied(),
                "B2 幂等位未置 ⇒ 填充链根本没跑过（回退档也必须是「跑过链但被开关挡住」，而不是没接线）");
        }
        reportNoPollution("B2", before, snapshotVanillaBiomes());
        final String registry = read(
            "src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/entity/GTSRCreatureRegistry.java");
        check(registry.contains("prosperityCreaturesEnabled"), "B2 preInit/fill 未读总开关");
        check(count(registry, "prosperityCreaturesEnabled") >= 2,
            "B2 总开关只在 preInit 挡一道、POLICY.fill 未挡 ⇒ 关掉后仍可能被填充");
        check(!GTSRCreatureRegistry.isRegisteredWithFml(), "B2 回退档不该有 FML 侧注册");
        System.out.println("   B2 总开关关闭：四群系 4 张表全空（P8 基线）+ 原版群系零差异");
    }

    // ————————————————————————— A 主世界零污染（判据 1）—————————————————————————————————

    private static void groupA(Snapshot before) throws Exception {
        // 触发全部读取路径：群系表、L1 生效表、provider 入口
        for (final BiomeId key : PROSPERITY_KEYS) {
            final BiomeGenBase biome = biomeOf(key);
            for (final EnumCreatureType type : TYPES) {
                biome.getSpawnableList(type);
            }
        }
        reportNoPollution("A1", before, snapshotVanillaBiomes());

        // A2 灵敏度自检：往一个原版群系塞一条，检测器必须恰好报 1 行差（否则 A1 的 0 是结构性假绿）
        final BiomeGenBase victim = firstVanillaBiome();
        final List<BiomeGenBase.SpawnListEntry> live = liveList(victim, EnumCreatureType.creature);
        final BiomeGenBase.SpawnListEntry rogue = new BiomeGenBase.SpawnListEntry(EntityGearPigeon.class, 1, 1, 1);
        live.add(rogue);
        final int delta = before.diffLines(snapshotVanillaBiomes());
        check(delta == 1, "A2 污染检测器不敏感（塞 1 条报 " + delta + " 行差）⇒ A1 的 0 不可信");
        live.remove(rogue);
        reportNoPollution("A3（灵敏度自检后还原）", before, snapshotVanillaBiomes());
    }

    private static void reportNoPollution(String label, Snapshot before, Snapshot after) {
        final int lines = before.diffLines(after);
        System.out.println("   " + label + ' ' + before.scopeLine() + " 差异 " + lines);
        check(lines == 0, label + " 原版群系刷怪表被改动（" + lines + " 行差异）⇒ 主世界污染");
        check(after.biomeCount() == before.biomeCount(),
            label + " 原版群系数量前后不一致（" + before.biomeCount() + " -> " + after.biomeCount() + "）");
    }

    // ————————————————————————— B 我方群系刷怪表（判据 2）—————————————————————————————————

    /**
     * 判据 2 的"另一方"——<b>本工具自带的字面量表</b>（行 = 群系带，列 = 齿轮鸽/汽雾萤/渣脊猎手；
     * 0 = 该带不出现该档）。它是名册乘子矩阵 × {@code Config} 基权（12/8/6）的<b>手算结果</b>，
     * 故意不复用 {@code GTSRCreatureRoster} 的实现，否则"表与声明一致"就退化为自己比自己。
     */
    private static final int[][] EXPECTED_WEIGHTS = { { 36, 8, 12 }, { 60, 16, 6 }, { 12, 0, 18 }, { 0, 40, 12 } };

    private static void groupB(BiomeGenBase[] p, BiomeGenBase[] s) {
        // B0 先钉数字的来源：Config 默认基权必须还是 12/8/6，否则下面的字面量表就成了无根断言
        check(Config.prosperityCreatureGearPigeonWeight == 12
            && Config.prosperityCreatureSteamFireflyWeight == 8
            && Config.prosperityCreatureSlagRidgeHunterWeight == 6,
            "B0 Config 三档基权已不是 12/8/6，字面量表 EXPECTED_WEIGHTS 需同步重算");
        int entries = 0;
        for (int i = 0; i < p.length; i++) {
            final BiomeId key = PROSPERITY_KEYS[i];
            final String actual = perTypeSignature(p[i]);
            final String expected = expectedPerTypeSignature(i);
            check(expected.equals(actual), "B1 " + key + " 刷怪表与字面量声明不一致 expect=" + expected + " actual=" + actual);
            // 与 roster 声明比（多重集口径，比"实现与实现"，与 B1 的独立钉互补）
            final List<BiomeGenBase.SpawnListEntry> declared = GTSRCreatureRoster.declaredEntries(key);
            entries += declared.size();
            check(declared.size() == counted(p[i]),
                "B1 " + key + " 条目数与 roster 声明数不同（声明 " + declared.size() + " / 在册 "
                    + counted(p[i]) + "）");
            for (final Species species : Species.values()) {
                final int weight = GTSRCreatureRoster.effectiveWeight(species, key);
                final List<BiomeGenBase.SpawnListEntry> typed = p[i].getSpawnableList(species.creatureType());
                final boolean present = containsClass(typed, species.entityClass());
                check(weight > 0 == present, "B1 " + key + '/' + species + " 缺席判定不一致（生效权=" + weight
                    + "，在册=" + present + "）");
                if (weight > 0) {
                    check(matchesEntry(typed, species, weight), "B1 " + key + '/' + species
                        + " 条目数字不符声明（期望 " + weight + '/' + species.minGroup() + '/' + species.maxGroup()
                        + "，实际 " + signature(typed) + "）");
                }
            }
        }
        System.out.println("   B1 dim78 四群系在册条目合计=" + entries + "；字面量表 EXPECTED_WEIGHTS 逐位通过；"
            + "带乘子(档/群系)=" + bandTable());
        int shatteredEntries = 0;
        for (final BiomeGenBase biome : s) {
            for (final EnumCreatureType type : TYPES) {
                shatteredEntries += biome.getSpawnableList(type).size();
            }
        }
        check(shatteredEntries == 0, "B3 dim79 群系被写入刷怪条目（" + shatteredEntries + " 条）⇒ 越界");
        System.out.println("   B3 dim79 四群系 4 张表条目合计=" + shatteredEntries + "（P8 基线=0）");
    }

    /** 按 {@link #TYPES} 顺序拼的分组签名（顺序稳定 ⇒ 可做幂等/前后比对）。 */
    private static String perTypeSignature(BiomeGenBase biome) {
        final StringBuilder sb = new StringBuilder();
        for (final EnumCreatureType type : TYPES) {
            sb.append(type)
                .append(signature(biome.getSpawnableList(type)))
                .append(' ');
        }
        return sb.toString();
    }

    private static int counted(BiomeGenBase biome) {
        int n = 0;
        for (final EnumCreatureType type : TYPES) {
            n += biome.getSpawnableList(type).size();
        }
        return n;
    }

    /** 字面量表 → 与 {@link #perTypeSignature} 同形状的期望串。 */
    private static String expectedPerTypeSignature(int bandRow) {
        final StringBuilder sb = new StringBuilder();
        for (final EnumCreatureType type : TYPES) {
            final StringBuilder part = new StringBuilder();
            for (int col = 0; col < Species.values().length; col++) {
                final Species species = Species.values()[col];
                if (species.creatureType() != type) {
                    continue;
                }
                final int weight = EXPECTED_WEIGHTS[bandRow][col];
                if (weight <= 0) {
                    continue;
                }
                part.append(species.entityClass()
                    .getSimpleName())
                    .append('*')
                    .append(weight)
                    .append('(')
                    .append(species.minGroup())
                    .append('-')
                    .append(species.maxGroup())
                    .append(") ");
            }
            sb.append(type)
                .append(part.length() == 0 ? "[]" : '[' + part.toString() + ']')
                .append(' ');
        }
        return sb.toString();
    }

    private static String bandTable() {
        final StringBuilder sb = new StringBuilder();
        for (final BiomeId key : PROSPERITY_KEYS) {
            sb.append(key.name())
                .append('=');
            for (final Species species : Species.values()) {
                sb.append(GTSRCreatureRoster.bandMultiplier(species, key))
                    .append('/');
            }
            sb.append("  ");
        }
        return sb.toString()
            .trim();
    }

    // ————————————————————————— C L1 收口（判据 3）—————————————————————————————————

    private static void groupC(Harness h) throws Exception {
        groupCSourceLevel();
        final World world = SurfaceHarness.mockWorld();
        world.provider.dimensionId = 78;
        final GTSRChunkProviderBase provider = new ChunkProviderProsperityRuins(world, SurfaceHarness.SEED);
        int sampled = 0;
        int cityHits = 0;
        final Set<String> hitBands = new LinkedHashSet<>();
        for (int cx = -72; cx <= 64; cx += 8) {
            for (int cz = -72; cz <= 64; cz += 8) {
                final int bx = cx * 16 + 8;
                final int bz = cz * 16 + 8;
                final BiomeId key = bandAt(bx, bz);
                if (key == null) {
                    continue;
                }
                hitBands.add(key.name());
                // 城窗是"同群系内随坐标变化"的合法调制（G3 专门测它），C2 比的是<b>身份</b>，
                // 所以把两侧都放进同一个调制口径，免得把城窗误报成"接错群系"
                final int mult = GTSRCreatureRoster.inCityWindow(SurfaceHarness.SEED, cx, cz)
                    ? GTSRCreatureRoster.CITY_WINDOW_WEIGHT_MULTIPLIER
                    : 1;
                cityHits += mult == 1 ? 0 : 1;
                for (final EnumCreatureType type : TYPES) {
                    final List<BiomeGenBase.SpawnListEntry> viaProvider =
                        provider.getPossibleCreatures(type, bx, 64, bz);
                    final List<BiomeGenBase.SpawnListEntry> declared =
                        GTSRCreatureRoster.scaleForLocation(biomeOf(key).getSpawnableList(type), mult);
                    check(signature(viaProvider).equals(signature(declared)), "C2 " + key + '/' + type
                        + " provider 入口与（该坐标调制后的）声明表不一致 @chunk(" + cx + ',' + cz + ')');
                    sampled++;
                }
            }
        }
        System.out.println("   C2 逐坐标核对=" + sampled + " 次（19×19 采样 chunk，跨 macro 带；"
            + "byte 平面在离线 JVM 根本不存在）覆盖带=" + hitBands
            + "，其中城窗采样点=" + cityHits);
        check(hitBands.size() >= 3, "C2 采样只命中 " + hitBands + " ⇒ 覆盖面不足，按带给权重的断言不成立");
    }

    private static void groupCSourceLevel() throws Exception {
        final String body = methodBody(
            read(
                "src/main/java/com/miaokatze/gtsr/common/dimension/framework/GTSRChunkProviderBase.java"),
            "public List<BiomeGenBase.SpawnListEntry> getPossibleCreatures(");
        check(body.contains("ordinalAt"), "C1 getPossibleCreatures 未走 GTSRBiomeAuthority.ordinalAt");
        check(body.contains("effectiveSpawnableList"), "C1 getPossibleCreatures 未走 L7 生效表出口");
        for (final String banned : new String[] { "getBiomeGenForCoords", "instanceof", "biomeID", "- Config." }) {
            check(!body.contains(banned), "C1 getPossibleCreatures 仍含禁止口径 " + banned);
        }
        final String authority = read(
            "src/main/java/com/miaokatze/gtsr/common/dimension/framework/GTSRBiomeAuthority.java");
        check(!authority.contains("（byte 平面读面，P9/L7 责任）"),
            "C1 GTSRBiomeAuthority 仍留着已修的『已知边界』条目（判据 3 要求同步收口）");
        check(!authority.contains("仍走旧口径的只剩四处"), "C1 边界条目的「只剩四处」计数未收口");
        check(authority.contains("P9"), "C1 边界注释未记录 P9 的收口事实");
        // P15 崩溃（进维刷怪 tick 抛 UnsupportedOperationException）的机制级钉：
        // 事件列表是 public final 字段，守卫无法改绑实例，可变性只能由 provider 出口保证
        check(body.contains("new ArrayList"), "C1 getPossibleCreatures 未交本次调用私有的可变副本");
        final String guard = read(
            "src/main/java/com/miaokatze/gtsr/common/dimension/framework/DimensionInterferenceGuard.java");
        check(guard.contains("retainDeclaredSpawns"), "C1 防线 3 白名单裁决入口丢失");
        check(guard.contains("setCanceled"), "C1 防线 3 降级态未走 cancel");
        check(!guard.contains("event.list.clear"), "C1 防线 3 重新出现对事件列表的原地清空写点");
        // 污染面与蛋注册面的机制级钉（判据 1 / U-B）
        final List<String> addSpawn = grepTree("EntityRegistry.addSpawn");
        check(addSpawn.isEmpty(), "C1 出现 EntityRegistry.addSpawn 调用（漏传 biomes 即污染主世界）：" + addSpawn);
        final List<String> eggs = grepTree("registerGlobalEntityID");
        check(eggs.isEmpty(), "C1 出现刷怪蛋/全局 id 注册（U-B 裁决 Phase 1 不注册蛋）：" + eggs);
    }

    // ————————————————————————— D 渲染器与纹理（判据 4）—————————————————————————————————

    private static void groupD() throws Exception {
        GTSRCreatureRenderers.registerAll();
        final List<Object> infos = renderRegistryEntries();
        final Class<?> infoClass = infos.get(0)
            .getClass();
        final Field target = infoClass.getDeclaredField("target");
        final Field renderer = infoClass.getDeclaredField("renderer");
        target.setAccessible(true);
        renderer.setAccessible(true);
        final ClassLoader loader = CreatureSpawnAuthorityCheck.class.getClassLoader();
        for (final Species species : Species.values()) {
            Object found = null;
            for (final Object info : infos) {
                if (target.get(info) == species.entityClass()) {
                    found = renderer.get(info);
                    break;
                }
            }
            check(found instanceof Render,
                "D1 " + species + " 无已注册 Render 条目（在册 " + infos.size() + " 条）⇒ 隐形实体风险");
            if (!(found instanceof Render)) {
                continue;
            }
            final Render render = (Render) found;
            check(render.getClass()
                .getName().startsWith("com.miaokatze."),
                "D1 " + species + " 挂的是原版具体渲染器 " + render.getClass()
                    .getName() + "（会向下转型成原版实体 ⇒ 隐形实体）");
            final ResourceLocation loc = textureOf(render);
            check(loc != null && species.skinTexturePath()
                .equals(loc.toString()), "D1 " + species + " 渲染器返回的纹理不是名册申报的 "
                    + species.skinTexturePath() + "，实际 " + loc);
            if (loc == null) {
                continue;
            }
            check("gtsr".equals(loc.getResourceDomain()),
                "D1 " + species + " 纹理 domain=" + loc.getResourceDomain()
                    + "（D1 起三档必须全部走 gtsr 域自有皮肤；借原版皮属 v1.20.35~D2 的过渡态）");
            final String cpPath = "assets/" + loc.getResourceDomain() + '/' + loc.getResourcePath();
            final URL url = resolveTexture(loader, loc);
            check(url != null, "D2 纹理在 classpath 与仓库资源根上都不可解析（隐形实体的另一半成因）：" + cpPath);
            if (url == null) {
                continue;
            }
            final long size = Files.size(Paths.get(url.toURI()));
            check(size > 0, "D2 纹理存在但 0 字节：" + cpPath);
            final Class<?> model = modelOf(render);
            check(model != null && species.modelClassName()
                .equals(model.getSimpleName()),
                "D3 " + species + " 复用的模型不是申报的 " + species.modelClassName() + "，实际 " + model);
            System.out.println("   D " + species + " -> " + render.getClass()
                .getSimpleName() + " tex=" + loc + " bytes=" + size + " model=" + model.getSimpleName()
                + " 装载通道=" + LAST_TEXTURE_CHANNEL);
        }
    }

    /**
     * 皮肤资源的<b>实际</b>解析通道，最近一次 {@link #resolveTexture} 的结果（打印用，
     * 让"渲染器读到的就是我写的文件"这件事在回执里可追）。
     */
    static String LAST_TEXTURE_CHANNEL = "unset";

    /**
     * 解析一张 {@link ResourceLocation} 指向的材质字节。
     * <p>
     * 两条通道，按优先级：① <b>classpath</b>（生产与 gradle 构建后的真通道：jar 里的
     * {@code assets/<domain>/<path>}）；② 仓库资源根 {@code src/main/resources/<same path>}。
     * 第二条是 D1 才需要的：一键入口 {@code tools/dim1/surface_checks.sh} 的 {@code runres}
     * 只挂 {@code build/resources/patchedMc}（原版资源根）与 {@code build/classes}，
     * <b>不挂</b> {@code src/main/resources}，而 {@code surface_checks.sh} 不属本片的可写面
     * ⇒ 自有皮肤在离线 JVM 里只能走第二条。两条都试，且把走了哪条打出来，不静默。
     */
    private static URL resolveTexture(ClassLoader loader, ResourceLocation loc) throws java.io.IOException {
        final String cpPath = "assets/" + loc.getResourceDomain() + '/' + loc.getResourcePath();
        final URL url = loader.getResource(cpPath);
        if (url != null) {
            LAST_TEXTURE_CHANNEL = "classpath";
            return url;
        }
        final java.nio.file.Path file = Paths.get("src/main/resources", cpPath.split("/"));
        if (Files.isRegularFile(file)) {
            LAST_TEXTURE_CHANNEL = "src-tree";
            return file.toAbsolutePath()
                .toUri()
                .toURL();
        }
        LAST_TEXTURE_CHANNEL = "MISSING";
        return null;
    }

    private static ResourceLocation textureOf(Render render) throws Exception {
        final java.lang.reflect.Method m = Render.class.getDeclaredMethod("getEntityTexture", Entity.class);
        m.setAccessible(true);
        return (ResourceLocation) m.invoke(render, (Entity) null);
    }

    private static Class<?> modelOf(Render render) throws Exception {
        final Object model = modelInstance(render);
        return model == null ? null : model.getClass();
    }

    /** 渲染器实际持有的模型实例（{@code RenderLiving.mainModel}；判据 4 的 I1/I2 用它做反射级核对）。 */
    private static ModelBase modelInstance(Render render) {
        try {
            final Field f = findField(render.getClass(), "mainModel");
            f.setAccessible(true);
            final Object model = f.get(render);
            return model instanceof ModelBase ? (ModelBase) model : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static Field findField(Class<?> cls, String name) {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // 继续向上找
            }
        }
        throw new IllegalStateException("找不到字段 " + name + "（自 " + cls + "）");
    }

    @SuppressWarnings("unchecked")
    private static List<Object> renderRegistryEntries() throws Exception {
        final Object instance = Class.forName("cpw.mods.fml.client.registry.RenderingRegistry")
            .getMethod("instance")
            .invoke(null);
        final Field f = instance.getClass()
            .getDeclaredField("entityRenderers");
        f.setAccessible(true);
        return (List<Object>) f.get(instance);
    }

    // ————————————————————————— E DataWatcher 与 id（判据 5）—————————————————————————————————

    private static void groupE() throws Exception {
        groupESourceLevel();
        final World world = SurfaceHarness.mockWorld();
        final EntityLiving[] samples = { new EntityGearPigeon(world), new EntitySteamFirefly(world),
            new EntitySlagRidgeHunter(world) };
        final Species[] order = Species.values();
        for (int i = 0; i < samples.length; i++) {
            final EntityLiving entity = samples[i];
            final Set<Integer> used = usedWatcherIds(entity);
            final Set<Integer> custom = new LinkedHashSet<>(used);
            custom.removeAll(VANILLA_WATCHER_IDS);
            boolean allHigh = true;
            for (final Integer id : custom) {
                if (id < 16) {
                    allHigh = false;
                }
            }
            check(allHigh,
                "E1 " + order[i] + " 的自定义 DataWatcher 索引未全部 ≥16：" + new TreeSet<>(custom));
            check(!custom.isEmpty(),
                "E1 " + order[i] + " 没有任何 ≥16 的自定义槽（判据 5 要求有可核对的自定义数据）");
            System.out.println("   E1 " + order[i] + " 全部索引=" + new TreeSet<>(used) + " 自定义="
                + new TreeSet<>(custom) + "（vanilla 链占用 " + new TreeSet<>(VANILLA_WATCHER_IDS) + '）');
            check(order[i].entityClass() == entity.getClass(),
                "E2 " + order[i] + " 名册登记的类与实例类不同（刷怪表会指向错实体）");
        }
        checkContract(Species.GEAR_PIGEON, net.minecraft.entity.passive.EntityAnimal.class);
        checkContract(Species.STEAM_FIREFLY, net.minecraft.entity.passive.EntityAmbientCreature.class);
        checkContract(Species.SLAG_RIDGE_HUNTER, net.minecraft.entity.monster.EntityMob.class);
        for (final Species species : Species.values()) {
            check(species.creatureType()
                .getCreatureClass()
                .isAssignableFrom(species.entityClass()), "E3 " + species + " 不满足 "
                    + species.creatureType() + " 的基类契约（isAssignableFrom 恒 false ⇒ 注册了也永不落地）");
            try {
                check(species.entityClass()
                    .getConstructor(World.class) != null, "E3 " + species + " 缺 public (World) 构造器");
            } catch (NoSuchMethodException e) {
                check(false, "E3 " + species + " 缺 public (World) 构造器（vanilla 反射刷怪入口）：" + e);
            }
        }
        final Map<String, Integer> snapshot1 = GTSRCreatureRegistry.idSnapshot();
        GTSRCreatureRegistry.planRegistrations();
        GTSRCreatureRegistry.planRegistrations();
        final Map<String, Integer> snapshot2 = GTSRCreatureRegistry.idSnapshot();
        check(snapshot1.equals(snapshot2), "E4 id 快照在注册链重跑后变化：" + snapshot1 + " -> " + snapshot2);
        check(new HashSet<>(snapshot2.values()).size() == snapshot2.size(), "E4 id 有重复：" + snapshot2);
        check(snapshot2.size() == Species.values().length, "E4 在册档数 != 名册档数：" + snapshot2);
        int expected = GTSRCreatureRegistry.FIRST_ENTITY_ID;
        for (final Species species : Species.values()) {
            final Integer id = snapshot2.get(species.registryName());
            check(id != null && id == expected, "E4 " + species + " 的 id 不是自增第 " + expected + " 位，实际 " + id);
            expected++;
        }
        check(!GTSRCreatureRegistry.isRegisteredWithFml(),
            "E4 离线 JVM 不该发生 FML 侧注册（说明注册链走了 ModContainer 路径，快照不可信）");
        System.out.println("   E4 id 快照=" + snapshot2 + "（mod-local 自增；不占全局 id 段：U-B 不注册蛋）");
    }

    private static void checkContract(Species species, Class<?> required) {
        check(required.isAssignableFrom(species.entityClass()),
            "E2 " + species + " 必须继承 " + required.getSimpleName() + "（EnumCreatureType 契约）");
    }

    private static void groupESourceLevel() throws Exception {
        int scanned = 0;
        for (final Species species : Species.values()) {
            final String src = read("src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/entity/"
                + classNameOf(species) + ".java");
            for (final String literal : literals(src, "dataWatcher.addObject(")) {
                final String resolved = resolveInt(src, literal);
                check(resolved != null, "E0 " + species + " 的 addObject 首参既不是字面量也不是本文件常量：" + literal);
                if (resolved == null) {
                    continue;
                }
                final int id = Integer.parseInt(resolved);
                check(id >= 16, "E0 " + species + " 的 addObject 索引 " + literal + '(' + id + ") <16（与 vanilla 链相撞）");
                scanned++;
            }
            check(src.contains("isAIEnabled"),
                "E0 " + species + " 未覆写 isAIEnabled（1.7.10 的 EntityLiving 默认 false ⇒ 加了 AI 也不会跑）");
        }
        System.out.println("   E0 源级 addObject 索引扫描=" + scanned + " 处，全部 ≥16");
    }

    /** 字面量直接用；否则按 {@code int NAME = <digits>;} 在本文件里解析一次（常量声明是唯一来源）。 */
    private static String resolveInt(String src, String token) {
        if (token.chars()
            .allMatch(Character::isDigit)) {
            return token;
        }
        final java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("int\\s+" + java.util.regex.Pattern.quote(token) + "\\s*=\\s*(\\d+)\\s*;")
            .matcher(src);
        return m.find() ? m.group(1) : null;
    }

    /** {@code GEAR_PIGEON → EntityGearPigeon}（类名与枚举名同词根，注册表不留第二份映射表）。 */
    private static String classNameOf(Species species) {
        final StringBuilder sb = new StringBuilder("Entity");
        for (final String part : species.name()
            .split("_")) {
            sb.append(Character.toUpperCase(part.charAt(0)))
                .append(part.substring(1)
                    .toLowerCase(java.util.Locale.ROOT));
        }
        return sb.toString();
    }

    private static Set<Integer> usedWatcherIds(Entity entity) {
        final Set<Integer> out = new LinkedHashSet<>();
        for (final Object o : entity.getDataWatcher()
            .getAllWatched()) {
            try {
                final java.lang.reflect.Method m = o.getClass()
                    .getMethod("getDataValueId");
                out.add((Integer) m.invoke(o));
            } catch (ReflectiveOperationException e) {
                check(false, "E1 无法读取 DataWatcher 索引：" + e);
            }
        }
        return out;
    }

    // ————————————————————————— F 幂等与稳定（判据 6）—————————————————————————————————

    private static void groupF(Harness h) throws Exception {
        for (int round = 0; round < 3; round++) {
            for (final BiomeGenBase biome : h.p) {
                ((GTSRBiomeBase) biome).applyCreatureSpawnsOnce();
            }
        }
        final String[] beforeSig = new String[h.p.length];
        for (int i = 0; i < h.p.length; i++) {
            beforeSig[i] = perTypeSignature(h.p[i]);
        }
        for (int i = 0; i < h.p.length; i++) {
            check(beforeSig[i].equals(perTypeSignature(h.p[i])),
                "F1 填充链重跑 3 次后 " + PROSPERITY_KEYS[i] + " 的表变了：" + perTypeSignature(h.p[i]));
            check(expectedPerTypeSignature(i).equals(perTypeSignature(h.p[i])),
                "F1 重跑后 " + PROSPERITY_KEYS[i] + " 偏离字面量表：" + perTypeSignature(h.p[i]));
        }
        System.out.println("   F1 填充链重跑 3 次：四群系表逐位不变");

        final World world = SurfaceHarness.mockWorld();
        world.provider.dimensionId = 78;
        final GTSRChunkProviderBase provider = new ChunkProviderProsperityRuins(world, SurfaceHarness.SEED);
        final StringBuilder run1 = new StringBuilder();
        final StringBuilder run2 = new StringBuilder();
        int calls = 0;
        for (int cx = -8; cx < 8; cx++) {
            for (int cz = -8; cz < 8; cz++) {
                final int bx = cx * 16 + 8;
                final int bz = cz * 16 + 8;
                for (final EnumCreatureType type : TYPES) {
                    final String tag = bx + "," + bz + type;
                    run1.append(tag)
                        .append(signature(provider.getPossibleCreatures(type, bx, 64, bz)));
                    run2.append(tag)
                        .append(signature(provider.getPossibleCreatures(type, bx, 64, bz)));
                    calls++;
                }
            }
        }
        check(run1.toString()
            .contentEquals(run2), "F2 同 seed 下 getPossibleCreatures 双跑不一致");
        System.out.println("   F2 双跑逐位一致，坐标×类别=" + calls + " 次（16×16 chunk）");
    }

    // ————————————————————————— G 城窗 ×2 与结构联动数据 —————————————————————————

    private static void groupG(Harness h) throws Exception {
        CityPlan city = null;
        for (int cellX = -6; cellX <= 6 && city == null; cellX++) {
            for (int cellZ = -6; cellZ <= 6; cellZ++) {
                final CityPlan plan = CityPlanner.planFor(SurfaceHarness.SEED, cellX, cellZ);
                if (plan != null && CityPlanner.cityGateAllows(SurfaceHarness.SEED, plan)) {
                    city = plan;
                    break;
                }
            }
        }
        check(city != null,
            "G1 13×13 cell 内找不到城门放行的城窗（P6 城门链断了？先查装配再查本片）");
        if (city == null) {
            return;
        }
        final World world = SurfaceHarness.mockWorld();
        world.provider.dimensionId = 78;
        final GTSRChunkProviderBase provider = new ChunkProviderProsperityRuins(world, SurfaceHarness.SEED);
        final int inX = city.getCenterChunkX() * 16 + 8;
        final int inZ = city.getCenterChunkZ() * 16 + 8;
        final int outX = inX + 512 * 16;
        final int outZ = inZ + 512 * 16;
        check(GTSRCreatureRoster.inCityWindow(SurfaceHarness.SEED, inX >> 4, inZ >> 4),
            "G2 城中心 chunk 不被 inCityWindow 认领（与 CityPlanner.citiesNear 脱钩）");
        check(!GTSRCreatureRoster.inCityWindow(SurfaceHarness.SEED, outX >> 4, outZ >> 4),
            "G2 窗外 512 chunk 处仍被判在城窗内（城窗范围失控）");
        final BiomeGenBase inBiome = bandBiomeAt(inX, inZ);
        final BiomeGenBase outBiome = bandBiomeAt(outX, outZ);
        int compared = 0;
        for (final EnumCreatureType type : TYPES) {
            final List<BiomeGenBase.SpawnListEntry> declared = inBiome.getSpawnableList(type);
            final List<BiomeGenBase.SpawnListEntry> outside = provider.getPossibleCreatures(type, outX, 64, outZ);
            check(signature(outside).equals(signature(outBiome.getSpawnableList(type))),
                "G3 窗外生效表 != 该带声明表：" + signature(outside) + " vs "
                    + signature(outBiome.getSpawnableList(type)));
            final List<BiomeGenBase.SpawnListEntry> scaled = provider.getPossibleCreatures(type, inX, 64, inZ);
            if (declared.isEmpty()) {
                check(scaled == null || scaled.isEmpty(), "G3 该类别在城窗内被凭空造出条目：" + signature(scaled));
                continue;
            }
            for (int i = 0; i < declared.size(); i++) {
                final BiomeGenBase.SpawnListEntry a = declared.get(i);
                final BiomeGenBase.SpawnListEntry b = scaled.get(i);
                check(b.itemWeight == a.itemWeight * GTSRCreatureRoster.CITY_WINDOW_WEIGHT_MULTIPLIER,
                    "G3 城窗权重不是 ×" + GTSRCreatureRoster.CITY_WINDOW_WEIGHT_MULTIPLIER + "："
                        + a.entityClass.getSimpleName() + ' ' + a.itemWeight + " -> " + b.itemWeight);
                check(b.minGroupCount == a.minGroupCount && b.maxGroupCount == a.maxGroupCount,
                    "G3 城窗调制误改满群规模");
                compared++;
            }
            check(!signature(declared).equals(signature(scaled)), "G3 城窗内生效表与声明表逐位相同 ⇒ ×2 未生效");
        }
        final String declaredBefore = perTypeSignature(inBiome);
        provider.getPossibleCreatures(EnumCreatureType.creature, inX, 64, inZ);
        check(declaredBefore.equals(perTypeSignature(inBiome)), "G4 城窗调制把声明表本体改了 ⇒ 污染名册真值");
        System.out.println("   G3 城窗 ×" + GTSRCreatureRoster.CITY_WINDOW_WEIGHT_MULTIPLIER + " 核对条目=" + compared
            + " 条（城窗带=" + bandAt(inX, inZ) + "，窗外带=" + bandAt(outX, outZ) + "）");

        int linked = 0;
        for (final Species species : Species.values()) {
            check(GTSRCreatureRoster.STRUCTURE_LINK_CAP_PER_STRUCTURE <= 2, "G5 结构联动上限超过 plan 钉的 2/座");
            for (final String family : GTSRCreatureRoster.linkedFamilies(species)) {
                final int cap = GTSRCreatureRoster.structureLinkCap(species, family);
                check(cap > 0 && cap <= 2, "G5 " + species + '/' + family + " 封顶=" + cap + " 不在 (0,2]");
                linked++;
            }
            check(GTSRCreatureRoster.structureLinkCap(species, "not-a-family") == 0,
                "G5 " + species + " 对未申报族给了非 0 封顶");
        }
        System.out.println("   G5 结构联动申报=" + linked + " 条(档×族)，封顶恒 ≤2/座 ⇒ 数据结构与断言已落；"
            + "行为面本片未接入放置链 ⇒ 待实机目检");
    }

    // ————————————————————————— H 读取路径只读性 —————————————————————————

    private static void groupH(BiomeGenBase[] p) {
        final String[] sigs = new String[p.length];
        for (int i = 0; i < p.length; i++) {
            sigs[i] = perTypeSignature(p[i]);
        }
        for (int i = 0; i < p.length; i++) {
            for (final EnumCreatureType type : TYPES) {
                p[i].getSpawnableList(type);
            }
            GTSRBiomeBase.effectiveSpawnableList(p[i], EnumCreatureType.monster, SurfaceHarness.SEED, 0, 0);
            check(sigs[i].equals(perTypeSignature(p[i])),
                "H1 " + PROSPERITY_KEYS[i] + " 的表被读取路径改动（只读性破了）");
        }
        check(GTSRBiomeBase.isCreatureSpawnPolicyInstalled(), "H1 策略未接线（前面的断言全部无意义）");
        System.out.println("   H1 读取路径只读性核对=" + p.length + " 群系 × " + TYPES.length + " 类别");
    }

    // ————————————————————————— I 判据 4/5 盲区补钉（D2 生物高危修复）—————————————————————————

    /**
     * RFG 反编译源根（= 编译期真实 classpath 的源码面；缺失时先跑一次 gradle build）。
     * 本组之所以能读上游源码做源级钉：C-01/C-02 的崩因（模型里的无条件向下转型）只存在于
     * <b>上游模型类的源码/字节</b>里，既有判据 4 只看渲染器类名前缀与模型 SimpleName ⇒ 结构性漏网。
     */
    private static final String MC_SRC = "build/rfg/minecraft-src/java/";

    /**
     * 会被"无条件向下转型"打崩的三个每帧热方法：{@code {反射名, 反编译源签名前缀}}
     * （调用频率见 {@code build/rfg/.../client/renderer/entity/RendererLivingEntity.java:164,165,247,266,309,319}；
     * {@code setRotationAngles} 由 {@code render} 第一句转调，故同属每帧入口）。
     */
    private static final String[][] HOT_MODEL_METHODS = { { "render", "public void render(Entity" },
        { "setLivingAnimations", "public void setLivingAnimations(EntityLivingBase" },
        { "setRotationAngles", "public void setRotationAngles(float" } };

    private static final String ENTITY_PKG = "src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/entity/";
    private static final String PIGEON_SRC = ENTITY_PKG + "EntityGearPigeon.java";
    private static final String FLY_SRC = ENTITY_PKG + "EntitySteamFirefly.java";

    /**
     * 上游 {@code EntityLiving.getCanSpawnHere()} 的三段落地安全谓词（实测
     * {@code build/rfg/.../entity/EntityLiving.java:742}）；齿轮鸽的覆写逐条复算它们
     * （Java 跳不过 {@code EntityAnimal} 那层），故本表与上游必须对钉。
     */
    private static final List<String> VANILLA_SPAWN_LEGS = Arrays
        .asList("checkNoEntityCollision", "getCollidingBoundingBoxes", "isAnyLiquid");

    /** 上游那三段谓词按源文本出现顺序取回（上游一改即与 {@link #VANILLA_SPAWN_LEGS} 不等 ⇒ 红）。 */
    private static List<String> upperSpawnLegs() throws IOException {
        final String src = dropCommentLines(read(MC_SRC + "net/minecraft/entity/EntityLiving.java"));
        final String body = src.contains("public boolean getCanSpawnHere(") ? methodBody(src,
            "public boolean getCanSpawnHere(") : "";
        final List<String> found = new ArrayList<>();
        for (final String leg : VANILLA_SPAWN_LEGS) {
            if (body.contains(leg)) {
                found.add(leg);
            }
        }
        return found;
    }

    private static void groupI() throws Exception {
        final String modid = modIdFromSource();
        final ClassLoader loader = CreatureSpawnAuthorityCheck.class.getClassLoader();
        final String langEn = read("src/main/resources/assets/gtsr/lang/en_US.lang");
        final String langZh = read("src/main/resources/assets/gtsr/lang/zh_CN.lang");

        // I1 渲染器所选模型（含其继承链上所有覆写）必须对自家实体零外类强转
        for (final Species species : Species.values()) {
            final Render render = GTSRCreatureRenderers.newCreatureRenderer(species);
            final ModelBase instance = modelInstance(render);
            final Class<?> model = instance == null ? null : instance.getClass();
            check(model != null, "I1 " + species + " 取不到模型实例（判据 4 的 D3 也无意义）");
            if (model == null) {
                continue;
            }
            final int scanned = checkNoForeignCast(model, species);
            // I2 模型申报画布 == 实际绑定贴图的 IHDR 尺寸（旧判据 4 只钉"可解析且非零字节"，
            // 换尺寸导致的 UV 错位抓不到 ⇒ D1 换自有皮肤的硬口径由此钉住）
            final ResourceLocation loc = textureOf(render);
            final URL url = loc == null ? null : resolveTexture(loader, loc);
            if (url == null) {
                check(false, "I2 " + species + " 贴图在 classpath 上不可解析 ⇒ 画布无从核对");
            } else {
                final int[] png = pngSize(url);
                final int mw = intField(instance, "textureWidth");
                final int mh = intField(instance, "textureHeight");
                check(png[0] == mw && png[1] == mh, "I2 " + species + " 画布不符：模型 " + mw + "x" + mh
                    + "（" + model.getName() + "，扫描继承链 " + scanned + " 层）vs 贴图 " + png[0] + "x" + png[1]
                    + " " + loc);
                // 名册申报画布 == 模型实测 == 贴图 IHDR（三方一致，否则 D1 按申报值画皮会 UV 错位）
                check(species.skinWidth() == mw && species.skinHeight() == mh, "I2 " + species
                    + " 名册申报画布 " + species.skinWidth() + "x" + species.skinHeight() + " 与模型实测 " + mw + "x"
                    + mh + "（贴图 " + png[0] + "x" + png[1] + "）不符 ⇒ D1 的皮尺寸口径出现第二真值");
                System.out.println("   I " + species + " model=" + model.getSimpleName() + " 画布=名册 "
                    + species.skinWidth() + "x" + species.skinHeight() + " / 模型 " + mw + "x" + mh + " / 贴图 "
                    + png[0] + "x" + png[1] + " 外类强转扫描=" + scanned + " 层 装载通道=" + LAST_TEXTURE_CHANNEL);
            }

            // I3 名牌键：FML 自动前缀 modid（cpw EntityRegistry.java:164）⇒ 名册串名不得自带 modid，
            // 且两份 lang 必须有该键（v1.20.35 两份 lang 的 ^entity. 键实测 0 个）
            check(!species.registryName().startsWith(modid + '.'), "I3 " + species + " 名册串名自带 modid 前缀（"
                + species.registryName() + "）⇒ FML 再前缀后成 " + modid + '.' + species.registryName()
                + "（双前缀，名牌显示原始 key）");
            final String key = "entity." + modid + '.' + species.registryName() + ".name";
            check(langValue(langEn, key) != null, "I3 en_US.lang 缺名牌键：" + key);
            check(langValue(langZh, key) != null, "I3 zh_CN.lang 缺名牌键：" + key);
        }

        // I4 C-03：齿轮鸽的本维地表可刷性（EntityAnimal.java:322-327 的 Blocks.grass 硬门 + 光照 >8）
        final String pigeon = read(PIGEON_SRC);
        final String marker = "public boolean getCanSpawnHere(";
        check(pigeon.contains(marker), "I4 EntityGearPigeon 未覆写 getCanSpawnHere ⇒ 走 EntityAnimal 的草方块"
            + "硬门（EntityAnimal.java:327），dim78 全自研地表 ⇒ 权重最高档永不自然刷出（C-03）");
        final String spawnBody = dropCommentLines(pigeon.contains(marker) ? methodBody(pigeon, marker) : "");
        check(spawnBody.contains("canSpawnOnGround("),
            "I4 getCanSpawnHere 未走本类的地表谓词入口 canSpawnOnGround ⇒ 地表真值无法离线复算");
        check(dropCommentLines(pigeon).contains("SurfaceGate.isNaturalTop(DIM_KEY, SurfaceGate.landableTops(DIM_KEY),"),
            "I4 EntityGearPigeon 未以 SurfaceGateUnifyCheck E 组申报的三参形式引地表门唯一真值");
        check(!spawnBody.contains("Blocks.grass"), "I4 getCanSpawnHere 仍按原版 Blocks.grass 判地表（dim78 恒 false）");
        check(spawnBody.contains("getFullBlockLightValue"), "I4 getCanSpawnHere 丢了原版光照门（>8）⇒ 密度口径与暗处刷出都失去约束");
        // 覆写把 super 换成了"逐条复算 EntityLiving 的三段落地安全谓词"（Java 跳不过 EntityAnimal 那层）
        // ⇒ 复算必须与上游逐条对齐，且上游一旦改动本钉要立刻红，不允许静默漂移
        for (final String leg : VANILLA_SPAWN_LEGS) {
            check(spawnBody.contains(leg), "I4 getCanSpawnHere 丢了 EntityLiving 的落地安全谓词一段：" + leg);
        }
        check(VANILLA_SPAWN_LEGS.equals(upperSpawnLegs()), "I4 上游 EntityLiving.getCanSpawnHere 的三段与本仓复算"
            + "不再同形（上游 " + upperSpawnLegs() + " vs 复算 " + VANILLA_SPAWN_LEGS
            + "）⇒ 同步复算层或改回 super");
        final Block[] tops = SurfaceGate.landableTops(SurfaceGate.DIM78);
        check(tops.length == SurfaceGate.DIM78_SIZE, "I4 dim78 地表白名单规模异常（" + tops.length + "）");
        int offlineUnbound = 0;
        for (int i = 0; i < tops.length; i++) {
            if (tops[i] == null) {
                // 离线装配（SurfaceHarness.blockFamily）不构造城内冻结地表块 prosperitySurface ⇒ 首槽
                // 允许为 null；别的槽出现 null 就是真值集被改动，必须红。生产侧首槽非空由
                // SurfaceGateUnifyCheck 的 F 组（a[0] == gtsr:prosperitySurface）钉，本工具不引
                // BlocksGTSR——它的编译闭包会拉进 gregtech/ae2，超出本工具的 classpath。
                check(i == 0, "I4 白名单第 " + i + " 槽在离线 JVM 里为 null（只有首槽 prosperitySurface 允许未装配）"
                    + " ⇒ 本维地表真值集被改坏");
                offlineUnbound++;
                continue;
            }
            check(tops[i] != Blocks.grass,
                "I4 白名单成员是原版草 ⇒ 本维地表真值不成立（dim78 四群系 topBlock 全是自研块）");
        }
        check(tops.length - offlineUnbound >= 4, "I4 白名单可判成员不足 4 个自然 top（实测可判 "
            + (tops.length - offlineUnbound) + "，离线未装配 " + offlineUnbound + "）");
        final Method groundGate = findStatic(EntityGearPigeon.class, "canSpawnOnGround", Block.class);
        check(groundGate != null, "I4 EntityGearPigeon 无可离线复算的地表谓词入口 canSpawnOnGround(Block) ⇒ \"本维地表确实可刷\"无从机检");
        if (groundGate != null) {
            groundGate.setAccessible(true);
            for (final Block top : tops) {
                if (top == null) {
                    continue;
                }
                check(Boolean.TRUE.equals(groundGate.invoke(null, top)),
                    "I4 本维地表声明真值成员 " + top + " 被自家门判不可刷 ⇒ 该带仍刷不出鸽（C-03 未修）");
            }
            check(Boolean.FALSE.equals(groundGate.invoke(null, Blocks.grass)),
                "I4 门放行了原版 Blocks.grass（本维不存在该地表；放行即等于把真值口径退回原版）");
        }

        // I5 C-05：位移来源。EntityMoveHelper.java:49-74 只写 yaw/jump、零 motion ⇒ 只喂 moveHelper 的
        // 飞行实体必然原地不动；位移要么由 navigator 驱动，要么由本类直投 motion（原版蝙蝠范式）
        final String fly = read(FLY_SRC);
        final String aiMarker = "protected void updateAITasks(";
        check(fly.contains(aiMarker),
            "I5 EntitySteamFirefly 无 updateAITasks 覆写 ⇒ 位移仍只喂 getMoveHelper（EntityMoveHelper 零 motion 写入 ⇒ 原地不动，C-05）");
        final String flyBody = dropCommentLines(fly.contains(aiMarker) ? methodBody(fly, aiMarker) : "");
        // 认"每轴一条 += 累加写入"而不是"出现过 motionY 字样"：夹位语句里的 this.motionY = 常量 也算
        // 出现过 motionY，按字样认会让"删掉悬停主写入、只留夹位"这种变异静默通过（实测过一轮）
        for (final String axis : new String[] { "X", "Y", "Z" }) {
            check(flyBody.contains("this.motion" + axis + " +="), "I5 updateAITasks 缺 this.motion" + axis
                + " += 累加写入 ⇒ 该轴不受控（原版蝙蝠的三轴同形写法是 EntityBat.java:175-177）");
        }
        check(!dropCommentLines(fly).contains("getMoveHelper()"),
            "I5 仍把 getMoveHelper() 当位移通道（该类不改 motion/pos；原版蝙蝠也不走这条路）");

        // I6 C-07 口径钉：creature 档受 WorldServer.java:169 的 400 tick 门与 getMaxNumberOfCreature=10
        // 双重压制，名册必须以这两处 file:line/符号为出处记载（不钉数字字面量，理由见组注释）
        final String roster = read(
            "src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/entity/GTSRCreatureRoster.java");
        check(roster.contains("WorldServer.java:169") && roster.contains("getMaxNumberOfCreature"),
            "I6 名册未以 WorldServer.java:169 / getMaxNumberOfCreature 两条出处记载 creature 档密度口径"
                + " ⇒ 权重排序会被读成观感承诺（C-07）");
        System.out.println("   I4/I6 本维地表可刷门可判成员=" + (tops.length - offlineUnbound) + '/' + tops.length
            + "（离线未装配 " + offlineUnbound + " 槽，见 I4 注释）；creature 档密度口径以 file:line 记载");
    }

    /** 沿模型继承链扫描三个每帧热方法里的 {@code (EntityXxx)} 强转；返回实际扫描的类层数。 */
    private static int checkNoForeignCast(Class<?> model, Species species) throws IOException {
        int levels = 0;
        for (Class<?> c = model; c != null && ModelBase.class.isAssignableFrom(c); c = c.getSuperclass()) {
            final String src;
            try {
                src = dropCommentLines(readSourceOf(c));
            } catch (IOException e) {
                check(false, "I1 读不到 " + c.getName() + " 的反编译源（" + e.getMessage()
                    + "）⇒ 外类强转盲区无法闭合，先跑一次 gradle build 生成 " + MC_SRC + "…");
                return levels;
            }
            for (final String[] hot : HOT_MODEL_METHODS) {
                if (!declares(c, hot[0])) {
                    continue;
                }
                levels++;
                if (!src.contains(hot[1])) {
                    check(false, "I1 " + c.getName() + " 声明了 " + hot[0] + " 但源里找不到该签名（映射漂移？）");
                    continue;
                }
                final Matcher m = CAST.matcher(methodBody(src, hot[1]));
                while (m.find()) {
                    final String simple = m.group(1);
                    final Class<?> target = resolveCast(c, simple, src);
                    check(target != null && target.isAssignableFrom(species.entityClass()), "I1 " + species
                        + " 的模型 " + c.getSimpleName() + '.' + hot[0] + " 无条件强转 " + simple
                        + "，而自家实体是 " + species.entityClass().getSimpleName() + " ⇒ 上屏即 ClassCastException"
                            + "（RenderManager.java:300-304 不吞异常，直接 ReportedException 崩客户端）");
                }
            }
        }
        return levels;
    }

    private static final Pattern CAST = Pattern.compile("\\(\\s*(([a-zA-Z_$][\\w$]*\\.)*Entity[A-Za-z_$][\\w$]*)\\s*\\)");

    private static Class<?> resolveCast(Class<?> owner, String name, String src) {
        if (name.contains(".")) {
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException e) {
                return null;
            }
        }
        final String imported = "import net.minecraft.";
        for (final String line : src.split("\n")) {
            final String t = line.trim();
            if (t.startsWith(imported) && t.endsWith('.' + name + ';')) {
                try {
                    return Class.forName(t.substring(7, t.length() - 1));
                } catch (ClassNotFoundException e) {
                    return null;
                }
            }
        }
        return null;
    }

    private static boolean declares(Class<?> c, String methodName) {
        for (final Method m : c.getDeclaredMethods()) {
            if (m.getName().equals(methodName)) {
                return true;
            }
        }
        return false;
    }

    /** 类 → 源码路径（上游走 RFG 反编译树，我方走 src/main/java）。 */
    private static String readSourceOf(Class<?> c) throws IOException {
        final String rel = c.getName().replace('.', '/') + ".java";
        if (c.getName().startsWith("net.minecraft.")) {
            return read(MC_SRC + rel);
        }
        if (c.getName().startsWith("com.miaokatze.")) {
            return read("src/main/java/" + rel);
        }
        throw new IOException("未知源码根的类 " + c.getName());
    }

    private static int intField(Object instance, String name) throws Exception {
        final Field f = ModelBase.class.getField(name);
        f.setAccessible(true);
        return f.getInt(instance);
    }

    private static Method findStatic(Class<?> cls, String name, Class<?>... params) {
        try {
            return cls.getDeclaredMethod(name, params);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /** 只保留非注释行（与 {@link #grepTree} 同一口径：注释里重申的符号不算违规）。 */
    private static String dropCommentLines(String src) {
        final StringBuilder sb = new StringBuilder();
        for (final String line : src.split("\n")) {
            final String t = line.trim();
            if (t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")) {
                continue;
            }
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    /** PNG IHDR 的宽高（字节 16..23，大端）。 */
    private static int[] pngSize(URL url) throws IOException {
        try (InputStream in = url.openStream()) {
            final byte[] head = new byte[24];
            int off = 0;
            while (off < 24) {
                final int n = in.read(head, off, 24 - off);
                if (n < 0) {
                    break;
                }
                off += n;
            }
            if (off < 24 || head[1] != 'P' || head[2] != 'N' || head[3] != 'G') {
                throw new IOException("不是合法 PNG 头：" + url);
            }
            return new int[] { be(head, 16), be(head, 20) };
        }
    }

    private static int be(byte[] b, int at) {
        return (b[at] & 0xFF) << 24 | (b[at + 1] & 0xFF) << 16 | (b[at + 2] & 0xFF) << 8 | b[at + 3] & 0xFF;
    }

    /** {@code GTSteamReborn.MODID}（读源取字面量，不加载 @Mod 类）。 */
    private static String modIdFromSource() throws IOException {
        final String src = read("src/main/java/com/miaokatze/gtsr/main/GTSteamReborn.java");
        final Matcher m = Pattern.compile("MODID\\s*=\\s*\"([^\"]+)\"")
            .matcher(src);
        check(m.find(), "I3 取不到 GTSteamReborn.MODID 字面量");
        return m.group(1);
    }

    /** lang 里该键的值（逐行比对键名，不依赖换行符形态；找不到返回 null）。 */
    private static String langValue(String langSource, String key) {
        for (final String line : langSource.split("\n")) {
            final String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) {
                continue;
            }
            final int eq = t.indexOf('=');
            if (eq > 0 && t.substring(0, eq)
                .trim()
                .equals(key)) {
                return t.substring(eq + 1);
            }
        }
        return null;
    }

    // ————————————————————————— 装配与工具 —————————————————————————

    private static BiomeId bandAt(int blockX, int blockZ) {
        final GTSRBiomeAuthority.Resolution resolved = GTSRBiomeAuthority.forDimension(78)
            .ordinalAt(blockX, blockZ);
        return resolved == null ? null : resolved.biomeId;
    }

    private static BiomeGenBase bandBiomeAt(int blockX, int blockZ) {
        final GTSRBiomeAuthority.Resolution resolved = GTSRBiomeAuthority.forDimension(78)
            .ordinalAt(blockX, blockZ);
        check(resolved != null && resolved.biome != null, "装配：L1 在 " + blockX + ',' + blockZ + " 解析不到群系");
        return resolved == null ? null : resolved.biome;
    }

    private static BiomeGenBase biomeOf(BiomeId key) {
        final BiomeGenBase biome = GTSRBiomeAuthority.forDimKey(key.dimKey())
            .biomeOf(key);
        check(biome != null, "装配：" + key + " 未入账（账本缺成员）");
        return biome;
    }

    private static boolean containsClass(List<BiomeGenBase.SpawnListEntry> list, Class<?> cls) {
        if (list == null) {
            return false;
        }
        for (final BiomeGenBase.SpawnListEntry e : list) {
            if (e.entityClass == cls) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesEntry(List<BiomeGenBase.SpawnListEntry> list, Species species, int weight) {
        if (list == null) {
            return false;
        }
        for (final BiomeGenBase.SpawnListEntry e : list) {
            if (e.entityClass == species.entityClass() && e.itemWeight == weight
                && e.minGroupCount == species.minGroup()
                && e.maxGroupCount == species.maxGroup()) {
                return true;
            }
        }
        return false;
    }

    private static String signature(List<BiomeGenBase.SpawnListEntry> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        final StringBuilder sb = new StringBuilder("[");
        for (final BiomeGenBase.SpawnListEntry e : list) {
            sb.append(e.entityClass.getSimpleName())
                .append('*')
                .append(e.itemWeight)
                .append('(')
                .append(e.minGroupCount)
                .append('-')
                .append(e.maxGroupCount)
                .append(") ");
        }
        return sb.append(']')
            .toString();
    }

    /** 原版（非我方）群系 4 张表的逐位快照。 */
    private static final class Snapshot {

        private final TreeMap<Integer, String> byId = new TreeMap<>();

        int biomeCount() {
            return this.byId.size();
        }

        String scopeLine() {
            return "检查 " + this.byId.size() + " 个原版群系 / " + this.byId.size() * TYPES.length + " 张列表";
        }

        int diffLines(Snapshot other) {
            final Set<Integer> ids = new LinkedHashSet<>(this.byId.keySet());
            ids.addAll(other.byId.keySet());
            int diff = 0;
            for (final Integer id : ids) {
                if (!Objects.equals(this.byId.get(id), other.byId.get(id))) {
                    diff++;
                }
            }
            return diff;
        }
    }

    private static Snapshot snapshotVanillaBiomes() {
        final Snapshot snap = new Snapshot();
        final BiomeGenBase[] table = BiomeGenBase.getBiomeGenArray();
        if (table == null) {
            check(false, "A0 BiomeGenBase.getBiomeGenArray() 返回 null，无法扫描原版群系");
            return snap;
        }
        for (final BiomeGenBase biome : table) {
            if (biome == null || biome instanceof GTSRBiomeBase) {
                continue;
            }
            final StringBuilder sb = new StringBuilder();
            for (final EnumCreatureType type : TYPES) {
                sb.append(signature(listOf(biome, type)))
                    .append('|');
            }
            snap.byId.put(biome.biomeID, sb.toString());
        }
        return snap;
    }

    /**
     * 读一张刷怪列表的<b>副本</b>——经 {@code BiomeGenBase} 的 protected 字段反射直读，
     * <b>不</b>走 {@code getSpawnableList}（那是我方覆写点；用自家覆写去验自家覆写会结构性失明）。
     */
    private static List<BiomeGenBase.SpawnListEntry> listOf(BiomeGenBase biome, EnumCreatureType type) {
        return new ArrayList<>(liveList(biome, type));
    }

    /** {@code EnumCreatureType} → {@code BiomeGenBase} 里的列表字段名（与 vanilla 的四张表一一对应）。 */
    private static final Map<EnumCreatureType, String> LIST_FIELDS = createListFields();

    private static Map<EnumCreatureType, String> createListFields() {
        final Map<EnumCreatureType, String> map = new java.util.EnumMap<>(EnumCreatureType.class);
        map.put(EnumCreatureType.monster, "spawnableMonsterList");
        map.put(EnumCreatureType.creature, "spawnableCreatureList");
        map.put(EnumCreatureType.waterCreature, "spawnableWaterCreatureList");
        map.put(EnumCreatureType.ambient, "spawnableCaveCreatureList");
        return map;
    }

    /**
     * 取<b>活</b>列表（不是副本）——灵敏度自检必须往真列表里塞/摘条目，才能证明检测面是通的。
     */
    @SuppressWarnings("unchecked")
    private static List<BiomeGenBase.SpawnListEntry> liveList(BiomeGenBase biome, EnumCreatureType type) {
        try {
            final Field f = BiomeGenBase.class.getDeclaredField(LIST_FIELDS.get(type));
            f.setAccessible(true);
            return (List<BiomeGenBase.SpawnListEntry>) f.get(biome);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("取不到 " + biome.biomeName + '.' + type + " 的列表", e);
        }
    }

    private static BiomeGenBase firstVanillaBiome() {
        final BiomeGenBase[] table = BiomeGenBase.getBiomeGenArray();
        for (final BiomeGenBase biome : table) {
            if (biome != null && !(biome instanceof GTSRBiomeBase) && biome.biomeID > 0) {
                return biome;
            }
        }
        throw new IllegalStateException("找不到可做灵敏度自检的原版群系");
    }

    // ————————————————————————— 源级工具 —————————————————————————

    private static String read(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    /** 从签名标记起按大括号配平取方法体（含签名行）。 */
    private static String methodBody(String source, String signatureMarker) {
        final int sig = source.indexOf(signatureMarker);
        check(sig >= 0, "源级扫描：找不到方法 " + signatureMarker);
        final int open = source.indexOf('{', sig);
        int depth = 0;
        int i = Math.max(open, 0);
        for (; i < source.length(); i++) {
            final char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    break;
                }
            }
        }
        return source.substring(Math.max(sig, 0), Math.min(i + 1, source.length()));
    }

    private static List<String> grepTree(String needle) throws IOException {
        final List<String> hits = new ArrayList<>();
        final java.util.stream.Stream<java.nio.file.Path> walk = Files.walk(Paths.get("src/main/java"));
        try {
            walk.filter(p -> p.toString()
                .endsWith(".java"))
                .forEach(p -> {
                    try {
                        final List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
                        for (int i = 0; i < lines.size(); i++) {
                            final String line = lines.get(i);
                            // 只认真调用：注释行（含本工具的申报）不算污染面
                            if (line.contains(needle) && !line.trim()
                                .startsWith("*") && !line.trim()
                                    .startsWith("//")) {
                                hits.add(p + ":" + (i + 1));
                            }
                        }
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                });
        } finally {
            walk.close();
        }
        return hits;
    }

    private static List<String> literals(String source, String marker) {
        final List<String> out = new ArrayList<>();
        int from = 0;
        while (true) {
            final int at = source.indexOf(marker, from);
            if (at < 0) {
                return out;
            }
            final int close = source.indexOf(',', at + marker.length());
            if (close > at) {
                out.add(source.substring(at + marker.length(), close)
                    .trim());
            }
            from = at + marker.length();
        }
    }

    private static int count(String haystack, String needle) {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
            n++;
        }
        return n;
    }

    // ————————————————————————— 断言收束 —————————————————————————

    private static void check(boolean ok, String msg) {
        if (ok) {
            passed++;
            return;
        }
        FAILURES.add(msg);
        System.out.println("  FAIL " + msg);
    }

    private static void finish(String mode) {
        System.out.println("CREATURESPAWN " + (FAILURES.isEmpty() ? " PASS" : " FAIL") + ": assertions=" + passed
            + " failures=" + FAILURES.size() + " mode=" + mode + " species=" + Species.values().length);
        if (!FAILURES.isEmpty()) {
            for (final String f : FAILURES) {
                System.out.println("  - " + f);
            }
            System.exit(1);
        }
        System.out.println("CREATURESPAWN DONE（离线结构断言；生物行为待实机目检）");
    }
}
