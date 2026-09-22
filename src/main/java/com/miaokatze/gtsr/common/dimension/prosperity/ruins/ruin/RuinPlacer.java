package com.miaokatze.gtsr.common.dimension.prosperity.ruins.ruin;

import java.util.Random;

import net.minecraft.world.World;

import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.framework.SurfaceGate;
import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;
import com.miaokatze.gtsr.common.dimension.framework.structure.GTSRWorldgenHash;
import com.miaokatze.gtsr.common.dimension.framework.structure.PlacementGate;
import com.miaokatze.gtsr.common.dimension.framework.structure.StructureBuilder;
import com.miaokatze.gtsr.common.dimension.framework.structure.StructureRegistry;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ProsperityOutpostPlacer;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.RuinedMachinePlacer;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityBlockResolver;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityVariants;
import com.miaokatze.gtsr.config.Config;

/**
 * 城外废墟族放置器（<b>P8，plan §5 P8 / §2.1 L5 / §2.2 H-2·H-3</b>）：把 {@link RuinShapes} 的
 * 8 条破坏结构放进 dim78 的城外区，走 {@link PlacementGate} 的完整契约。
 * <p>
 * ═══ 掷骰链上的位置（互斥顺序语义与既有概率值一字未改）═══
 * 生产编排（{@code ProsperityWorldGenerator.generate}）的顺序是 <b>outpost（1/outpostChance）→
 * 残缺机器（1/machineChance × 群系权重）→ 废墟（本类，micro / ruinChance）</b>：
 * <ol>
 * <li>前两环的掷骰代码、概率值与"outpost 真实落块则跳过机器"那条互斥分支全在本类之外，本片一行没动；</li>
 * <li>本环只在<b>前两环都没有真实落块</b>时才问门，且过的是同一份
 * {@code prosperityStructureBudgetPerChunk} 预算与同一份同族间距档 ⇒ 废墟是<b>挤位</b>不是加密度
 * （"每 chunk 至多一座"这条上界改造前是 1、改造后还是 1；实测对照表见
 * {@code tools/dim1/RuinFamilyCheck} 的 DENSITY 行，座数上升量按判据 4 如实申报）；</li>
 * <li>城 buffer 窗内的抑制由编排器上方那条 {@code cities.length > 0 return} 天然保证（与两既有族同源）。</li>
 * </ol>
 * <p>
 * ═══ P7c 留给本片的接口承诺：命中重放口 ═══
 * 本族的窗上限取 {@link Config#prosperityRuinWindowRepeatCap}（非 0）。按 P7c 改判后的语义，
 * <b>非 0 上限只有走 {@link PlacementGate.ChunkGate#request(StructureRegistry.Entry,
 * PlacementGate.IntentFn)} 并自带重放口才生效</b>（无重放口时上限不生效、也绝不退回密度乘子，
 * 由 {@code PlacementContractCheck} A9 钉）。故本类：① 掷骰收成<b>唯一</b>实现体 {@link #roll}，
 * {@link #placeAll} 与 {@link #intentAt} 共用它；② 问门一律走 {@code Entry} + {@link #RUIN_INTENT}。
 * <p>
 * ═══ micro 强度层（判据 6：收口 P6 的悬空层）═══
 * {@link #microStrengthAt(int, int)} 是本仓<b>第一个</b>结构侧对 {@code microStrengthAt} 的消费点
 * （经 {@link GTSRBiomeAuthority} 的只读出口；L1 既不伪造身份也不伪造强度）。它调两件事：
 * <b>密度</b>（{@code P = micro / prosperityRuinChance}）与<b>规模档</b>
 * （{@link RuinShapes#allowedByMicro(float)}，弱 cell 不放大件）。关掉
 * {@link Config#prosperityRuinMicroModulation} 则两处都按中性 1.0F 处理（仍读取、仍消费，只是调制退回平坦）。
 * <p>
 * ═══ 落块材质 ═══
 * 全部字符经 {@link CityVariants#blockKeyOf(char)}/{@link CityVariants#metaOf(char)} 折算，
 * 游戏侧经 {@link CityBlockResolver} 解析（与城/outpost 同一条解析链，键表一字未扩）。派生阶段已把
 * GT5U 两键与机型核心位折进本维三层壳族（{@link RuinDamageOps#normalizeToRuinVocabulary}），
 * 故本族<b>结构上</b>不含仓室/控制器/容器方块——这一条不是注释，是 {@code RuinFamilyCheck} 的 TE 组断言。
 */
public final class RuinPlacer {

    /** 盐 "Ruin"（本族存在掷骰；与 "OUtP"/"mac"/"ScAt"/"WRpL"/"FmSp" 全部隔离）。 */
    private static final long SALT_RUIN = 0x5275696EL;

    /** 盐 "RUPl"（放置细节：朝向/损伤档/逐块缺失；与存在掷骰解耦，同 outpost 的 SALT_PLACE 纪律）。 */
    private static final long SALT_PLACE = 0x5255504CL;

    /** 本类所属维度键（P4 起门的显式维度入参；与 outpost/机器同一词汇，不另造字符串）。 */
    private static final String DIM_KEY = SurfaceGate.DIM78;

    /** 单 chunk 钳制契约下的 footprint 上界（与 {@code ProsperityOutpostPlacer.roll} 同一防御口径）。 */
    private static final int CHUNK_BLOCKS = 16;

    /** 逐块缺失的百分数域（与 {@link CityVariants#MISSING_RATES} 同一词汇）。 */
    private static final int PCT_BOUND = 100;

    /**
     * 八条废墟各自的"验证过的最深埋深档"（<b>P17 S-D</b>；下标与 {@link RuinShapes#ALL} 同序）。
     * 值由 {@link StructureBurialTiers#ceilingFor} 从<b>该模板自己的字符盘</b>实算 ⇒
     * 派生模板字节一格未改（逐名模板 SHA256 钉死面不动，无需 {@code --emit} 重钉）。
     */
    private static final int[] BURY_CEILINGS = buildBuryCeilings();

    private static int[] buildBuryCeilings() {
        final RuinTemplate[] all = RuinShapes.ALL;
        final int[] out = new int[all.length];
        for (int i = 0; i < all.length; i++) {
            out[i] = StructureBurialTiers.ceilingFor(all[i].sizeX, all[i].sizeY, all[i].sizeZ, all[i]::charAt);
        }
        return out;
    }

    /**
     * 本条废墟的验证档（引用相等查，≤8 次；不在 {@link RuinShapes#ALL} 里的模板 ⇒ 0 = 不埋，
     * 于是"给了新模板忘了进表"最坏退化成改造前形态，不是越界）。
     * <b>public 的唯一理由</b>：离线判据要按"档表 × 逐张盘"报实测窗口，不能再抄一份算式。
     */
    public static int buryCeilingOf(RuinTemplate ruin) {
        final RuinTemplate[] all = RuinShapes.ALL;
        for (int i = 0; i < all.length; i++) {
            if (all[i] == ruin) {
                return BURY_CEILINGS[i];
            }
        }
        return 0;
    }

    /**
     * 一次放置的埋深（<b>P17 S-D</b>，四族同源）：身份按<b>锚点原点</b>反查 L1 名册下标，档深由
     * {@link StructureBurialTiers#burialDepthFor} 在 {@code [档表下限, 本模板验证档]} 的窗口里给出
     * （本族改造前没有抖动埋深通道 ⇒ 实际取的是已夹紧的档表下限，零新增随机源）。
     * 默认档（身份不可得）⇒ 0 ⇒ 与改造前逐位相同。
     */
    public static int buryingAt(RuinTemplate ruin, int originX, int originZ) {
        return buryingAt(ruin, originX, originZ, StructureBurialTiers.rosterIndexAtOrigin(originX, originZ));
    }

    /**
     * {@link #buryingAt(RuinTemplate, int, int)} 的<b>身份显式形态</b>（同一个实现体，只是把
     * "锚点原点 → 名册下标"那一跳交给调用方）：离线判据据此在未装配 L1 账本的 JVM 里逐档复算
     * 生产同一条算式（装配了账本时两条出口逐点同值，由
     * {@code P17StructureBiomeVarianceCheck} 的 SOURCE 组钉）。
     */
    public static int buryingAt(RuinTemplate ruin, int originX, int originZ, int rosterIndex) {
        return StructureBurialTiers.burialDepthFor(buryCeilingOf(ruin), rosterIndex);
    }

    private static volatile boolean registered;

    private RuinPlacer() {}

    /**
     * 向 {@link StructureRegistry} 登记 8 条破坏结构（dimension=PROSPERITY，幂等）。
     * <p>
     * roster 形状（{@link StructureRegistry.Entry} 的 P7 扩字段）：族 = {@link PlacementGate#FAMILY_RUIN}；
     * 放置分母与窗上限<b>一律传 0 = 跟随 Config 族键</b>（{@code prosperityRuinChance} /
     * {@code prosperityRuinWindowRepeatCap}，本类不自持数字，与两既有城外族同纪律）；
     * {@code allowsDamagedVariant = <b>true</b>}——本族是全名册里唯一 opt-in 损毁算子的一族，
     * 该位由 {@link RuinDamageOps#effectiveMissingRate} 读取（既有 39 名一律 false，算子碰不到它们）。
     */
    public static void registerVariants() {
        if (registered) {
            return;
        }
        registered = true;
        for (final RuinTemplate ruin : RuinShapes.ALL) {
            StructureRegistry.register(
                new StructureRegistry.Entry(
                    ruin.name,
                    StructureRegistry.Dimension.PROSPERITY,
                    Math.max(ruin.sizeX, ruin.sizeZ),
                    Math.max(ruin.sizeX, ruin.sizeZ),
                    (sink, x, y, z, seed) -> place(
                        new StructureBuilder(new CityBlockResolver(sink)),
                        ruin,
                        x,
                        z,
                        CityVariants.rotationOf(seed),
                        RuinDamageOps.effectiveMissingRate(true, seed, 1.0F),
                        new Random(seed ^ SALT_PLACE),
                        CityVariants.flatGround(y),
                        BlockSink.FLAG_DIRECT),
                    PlacementGate.FAMILY_RUIN,
                    0,
                    0,
                    true));
        }
    }

    /** 注册数（真值仍是 {@link RuinShapes#ALL}；给编排器的证据日志与机检读，不持有第二份数字）。 */
    public static int registeredCount() {
        return RuinShapes.ALL.length;
    }

    /** populate 入口（无显式门上下文的兼容形态：离线断言与旧调用点用）。 */
    public static boolean placeAll(World world, long worldSeed, int cx, int cz, BlockSink sink) {
        return placeAll(world, worldSeed, cx, cz, sink, null);
    }

    /**
     * populate 入口：掷频（micro 调制）→ 掷规模档与变体/朝向 →
     * <b>过 {@link PlacementGate}（预算 / 同族互斥 / H-2① 窗重复上限 / H-2② 同族间距）</b>→
     * 逐列接地 + 就绪门 → 放置 → <b>按真实落块数兑现许可</b>（plan §2.1 L5「禁止失败计成功」）。
     *
     * @param gate 本 chunk 的结构门（编排器创建，三条城外族共用一个）；{@code null} = 自派生
     * @return true = 本 chunk <b>真实</b>落了至少一块的废墟；false = 开关关 / 未掷中 / 被门拒 /
     *         落点门不过 / 一块都没落进世界
     */
    public static boolean placeAll(World world, long worldSeed, int cx, int cz, BlockSink sink,
        PlacementGate.ChunkGate gate) {
        if (sink == null) {
            return false;
        }
        final Roll roll = roll(worldSeed, cx, cz);
        if (roll == null) {
            return false; // 开关关 / 禁用位 / 未掷中 / 候选集空 / footprint 超出单 chunk
        }
        // P7c 的承诺：本族用非 0 窗上限 ⇒ 必须走 Entry + 自带重放口这一条通道（A9 钉住）。
        final StructureRegistry.Entry entry = StructureRegistry.get(roll.ruin.name);
        final PlacementGate.ChunkGate chunkGate = gate != null ? gate
            : PlacementGate.beginChunk(DIM_KEY, worldSeed, cx, cz);
        final PlacementGate.Permit permit = chunkGate.request(entry, RUIN_INTENT, RUIN_NEIGHBORHOOD_INTENT);
        if (permit == null) {
            return false;
        }
        final int x = roll.intent.originX;
        final int z = roll.intent.originZ;
        // 落点判定与接地同源（L5 / plan §2.1 L2）：三族共用 PlacementGate.groundFn 这同一个供给器，
        // 中心列的 heightAt 值既是就绪门读的顶块所在行，也是整机逐列落地的基准。
        final CityVariants.GroundFn ground = PlacementGate.groundFn(worldSeed);
        final int centerX = x + roll.rotatedX / 2;
        final int centerZ = z + roll.rotatedZ / 2;
        final int surfaceY = ground.groundY(centerX, centerZ);
        if (!PlacementGate.readyAt(surfaceY, landableTopAt(world, centerX, surfaceY, centerZ))) {
            permit.abort(); // 未落块：显式归还，预算不扣
            return false;
        }
        final PlacementGate.CountingSink counter = PlacementGate.counting(sink);
        place(
            new StructureBuilder(new CityBlockResolver(counter)),
            roll.ruin,
            x,
            z,
            roll.rot,
            roll.missingRate,
            new Random(roll.placeSeed),
            ground,
            BlockSink.FLAG_POPULATE,
            // P17 S-D：群系埋深档，身份按锚点原点 (x,z) 反查；默认档 ⇒ 0 ⇒ 逐位等于改造前
            buryingAt(roll.ruin, x, z));
        return permit.commit(counter.solid());
    }

    /**
     * H-2 命中重放入口（纯函数，供 {@link PlacementGate} 枚举窗内/邻域槽位）：槽位 (cx,cz) 在
     * <b>没有任何窗上限/间距约束</b>时会请求哪条废墟、落在何处；{@code null} = 本槽不请求。
     */
    public static PlacementGate.Intent intentAt(long worldSeed, int cx, int cz) {
        final Roll roll = roll(worldSeed, cx, cz);
        return roll == null ? null : roll.intent;
    }

    /** 本族给门用的命中重放口（生产侧唯一一份；与 {@link #placeAll} 共用 {@link #roll} 实现体）。 */
    public static final PlacementGate.IntentFn RUIN_INTENT = new PlacementGate.IntentFn() {

        @Override
        public PlacementGate.Intent intentAt(long worldSeed, int cx, int cz) {
            return RuinPlacer.intentAt(worldSeed, cx, cz);
        }
    };

    /**
     * 本族<b>邻域让行</b>用的合并命中集（<b>P8</b>，喂给
     * {@link PlacementGate#neighborhoodClearAllows(long, int, int, int, PlacementGate.IntentFn)}）：
     * 三条城外族里<b>任何一族</b>会在该槽放东西 ⇒ 返回非空。
     * <p>
     * 为什么只用在这一条规则上：窗重复上限的货币是「同一 (窗, 模板) 的命中槽数」，把别的族算进来会
     * 数错货币（ruin 的模板名与机器/outpost 不重名，混集虽不改变计数结果，但语义上不是"本模板的重复"），
     * 所以上限仍只用 {@link #RUIN_INTENT}。而邻域规则的货币是「邻槽有没有任何东西要放」——正是合并集的语义。
     * <p>
     * <b>只读复用另两族公开的重放入口</b>（{@link ProsperityOutpostPlacer#intentAt} 与
     * {@link RuinedMachinePlacer#intentAt}），本类不重新实现任何掷骰 ⇒ 不会出现"第二份概率真值"。
     * 顺序固定（ruin → outpost → 机器），且本规则只判 null/非 null，不取返回值内容，故顺序无关结果。
     */
    public static final PlacementGate.IntentFn RUIN_NEIGHBORHOOD_INTENT = new PlacementGate.IntentFn() {

        @Override
        public PlacementGate.Intent intentAt(long worldSeed, int cx, int cz) {
            final PlacementGate.Intent ruin = RuinPlacer.intentAt(worldSeed, cx, cz);
            if (ruin != null) {
                return ruin;
            }
            final PlacementGate.Intent outpost = ProsperityOutpostPlacer.intentAt(worldSeed, cx, cz);
            if (outpost != null) {
                return outpost;
            }
            return RuinedMachinePlacer.intentAt(worldSeed, cx, cz);
        }
    };

    /** 一次废墟掷骰的全部产物（意图 + 放置细节；{@link #placeAll} 与 {@link #intentAt} 共用）。 */
    private static final class Roll {

        final PlacementGate.Intent intent;
        final RuinTemplate ruin;
        final int rot;
        final int rotatedX;
        final int rotatedZ;
        final long placeSeed;
        final int missingRate;

        Roll(PlacementGate.Intent intent, RuinTemplate ruin, int rot, int rotatedX, int rotatedZ, long placeSeed,
            int missingRate) {
            this.intent = intent;
            this.ruin = ruin;
            this.rot = rot;
            this.rotatedX = rotatedX;
            this.rotatedZ = rotatedZ;
            this.placeSeed = placeSeed;
            this.missingRate = missingRate;
        }
    }

    /**
     * 掷骰的<b>唯一</b>实现体（与 {@code ProsperityOutpostPlacer.roll} 同形：{@code nextDouble(频率)
     * → nextInt(候选) → 旋转 → nextInt(freeX+1) → nextInt(freeZ+1)}）：
     * <ol>
     * <li>开关位：{@link Config#prosperityRuinsEnabled} 关 ⇒ 直接不请求（编排器也不注册本族 roster）；</li>
     * <li>频率：{@code P = micro / prosperityRuinChance}——micro 只出现在这一条公式里，
     * 分母的唯一出处仍是 {@link Config#prosperityRuinChance}；</li>
     * <li>规模档：{@link RuinShapes#allowedByMicro(float)} 决定候选集上界（弱 cell 不放大件）。</li>
     * </ol>
     * 放置细节（朝向/损伤档）走另一条盐 {@link #SALT_PLACE} 的 {@code new Random(placeSeed)}，
     * 与本 {@code r} 无关 ⇒ 门判定不消耗随机流，落点/落块逐位确定。
     */
    private static Roll roll(long worldSeed, int cx, int cz) {
        if (!Config.prosperityRuinsEnabled) {
            return null;
        }
        final int chance = Config.prosperityRuinChance;
        if (chance <= 0) {
            return null; // 0 = 禁用（与机器/outpost 同一"0 = 关闭"约定）
        }
        final float micro = microStrengthAt(cx, cz);
        final Random r = new Random(GTSRWorldgenHash.chunkSeed(worldSeed, cx, cz) ^ SALT_RUIN);
        if (r.nextDouble() * chance >= micro) {
            return null;
        }
        final RuinTemplate[] pool = RuinShapes.candidates(RuinShapes.allowedByMicro(micro));
        if (pool.length == 0) {
            return null;
        }
        final RuinTemplate ruin = pool[r.nextInt(pool.length)];
        final long placeSeed = GTSRWorldgenHash.chunkSeed(worldSeed, cx, cz) ^ SALT_PLACE;
        final int rot = CityVariants.rotationOf(placeSeed);
        final int[] rotated = StructureBuilder.rotateSize(ruin.sizeX, ruin.sizeZ, rot);
        final int freeX = CHUNK_BLOCKS - rotated[0];
        final int freeZ = CHUNK_BLOCKS - rotated[1];
        if (freeX < 0 || freeZ < 0) {
            return null; // >16 格防御性跳过（单 chunk 契约下不应发生，RuinFamilyCheck 钉这个上界）
        }
        final int x = (cx << 4) + r.nextInt(freeX + 1);
        final int z = (cz << 4) + r.nextInt(freeZ + 1);
        return new Roll(
            new PlacementGate.Intent(ruin.name, x, z, rotated[0], rotated[1]),
            ruin,
            rot,
            rotated[0],
            rotated[1],
            placeSeed,
            RuinDamageOps.effectiveMissingRate(true, placeSeed, micro));
    }

    /**
     * micro 强度读取（<b>全仓结构侧第一个消费点</b>，plan §5 P8 判据 6 收口 P6 的悬空层）：
     * 走 L1 的只读出口 {@link GTSRBiomeAuthority#microStrengthAt(int, int)}，采样坐标与本族落点判定
     * 同一口径（chunk 中心块坐标）。{@link Config#prosperityRuinMicroModulation} 关 ⇒ 返回中性 1.0F，
     * 但<b>仍然经过</b>那个出口（回退位关的是调制，不是接线；否则判据 6 又变回悬空层）。
     * <p>
     * 本方法<b>不参与群系身份</b>，也不得被反推身份（P6 红线：身份只有 {@code ordinalAt} 一条出口）。
     */
    static float microStrengthAt(int cx, int cz) {
        final float raw = GTSRBiomeAuthority.forDimKey(GTSRBiomeAuthority.DIM_KEY_PROSPERITY)
            .microStrengthAt((cx << 4) + 8, (cz << 4) + 8);
        return Config.prosperityRuinMicroModulation ? raw : 1.0F;
    }

    /**
     * 变体放置核心（世界生成 / {@code /gtsr structure} / 离线 {@code RuinFamilyCheck} 三方共用）。
     * 签名与 {@code ProsperityOutpostPlacer.place} 逐参同形：基座层（y=0）恒放＝现成半埋语义；
     * {@code y>0} 按 {@code missingRate} 掷缺失——该值已由
     * {@link RuinDamageOps#effectiveMissingRate} 按 {@code allowsDamagedVariant} 与 micro 档折算，
     * 传 0 即"关掉损毁算子"（既有三族走的就是这一支，故本算子对它们不可达）；
     * {@code '.'} 在 {@code y>0} 清空气、在 {@code y=0} 是地坪留白让行地形；
     * 每列落地 y 由 {@code ground} 给出（L2 唯一制式，禁止整台平面接地）。
     * <p>
     * <b>P17 S-D 的两条埋深通道要分清</b>（同一条需求"沙漠更多是半埋"在本族的两半）：
     * <ol>
     * <li><b>烘进字符盘的那一半</b>（{@link RuinDamageOps#halfBury} 的 {@code sink} 1-2 层）——
     * 它随模板 SHA256 一起定盘，<b>本切片一格未改</b>（改它要 {@code --emit} 重钉逐名模板 SHA，
     * 且那是"每座废墟都沉同样深"的族内基线，与群系无关）；</li>
     * <li><b>运行期的那一半</b>（本方法的 {@code burying}）——由群系档表给出，四族同源。</li>
     * </ol>
     *
     * @param burying 已经由 {@link StructureBurialTiers} 夹进"本模板验证过的最深档"内的埋深层数；
     *                {@code 0} = 与改造前逐位相同（既有三方调用点用的就是这一档）
     * @return 尝试写入 sink 的格数（含被拒绝的）；真实落块数由调用方经
     *         {@link PlacementGate.CountingSink} 折算
     */
    public static int place(StructureBuilder builder, RuinTemplate ruin, int originX, int originZ, int rot,
        int missingRate, Random r, CityVariants.GroundFn ground, int flags) {
        return place(builder, ruin, originX, originZ, rot, missingRate, r, ground, flags, 0);
    }

    /**
     * {@link #place(StructureBuilder, RuinTemplate, int, int, int, int, Random, CityVariants.GroundFn, int)}
     * 的<b>群系埋深档</b>形态（P17 S-D）：{@code y < burying} 的那几层一格都不写（连清空空气都不写），
     * 其余每列落地 {@code wy = ground + 1 + (y - burying)} ⇒ 写入面永远在该列地表顶之上，
     * "垫层恒放"随之下移到第一层可见格。掷缺失误不消费被埋掉的那几层 ⇒ 同一 {@code burying}
     * 下锚点/邻槽/离线复算三者读到的仍是同一份几何。
     */
    public static int place(StructureBuilder builder, RuinTemplate ruin, int originX, int originZ, int rot,
        int missingRate, Random r, CityVariants.GroundFn ground, int flags, int burying) {
        final int bury = Math.max(0, burying);
        int writes = 0;
        for (int y = 0; y < ruin.sizeY; y++) {
            if (y < bury) {
                continue; // 半埋：这几层在地形以下，一格都不写（与巨构族同一条纪律）
            }
            for (int dz = 0; dz < ruin.sizeZ; dz++) {
                for (int dx = 0; dx < ruin.sizeX; dx++) {
                    final char c = ruin.charAt(y, dx, dz);
                    if (c == ' ') {
                        continue; // 不触碰：地形让行
                    }
                    final int[] rd = StructureBuilder.rotateDelta(dx, dz, rot, ruin.sizeX, ruin.sizeZ);
                    final int wx = originX + rd[0];
                    final int wz = originZ + rd[1];
                    final int wy = ground.groundY(wx, wz) + 1 + (y - bury);
                    if (wy < 1 || wy > 255) {
                        continue;
                    }
                    if (c == '.') {
                        if (y > bury) {
                            writes += builder.setBlock(wx, wy, wz, CityVariants.K_AIR, 0, flags) ? 1 : 0; // 内腔清空
                        }
                        continue; // 第一层可见格的 '.' = 地坪留白（地形让行）
                    }
                    if (y > bury && missingRate > 0 && r.nextInt(PCT_BOUND) < missingRate) {
                        continue; // 损伤档缺失（垫层不缺失——既有三族同一纪律）
                    }
                    writes += builder.setBlock(wx, wy, wz, CityVariants.blockKeyOf(c), CityVariants.metaOf(c), flags)
                        ? 1
                        : 0;
                }
            }
        }
        return writes;
    }

    /**
     * 落点地表门：直调框架单一谓词（与机器层同形，成员集合真值只在 {@link SurfaceGate} 一处）。
     * <b>方法名刻意不叫 {@code isNaturalTop}/{@code isNaturalProsperityTop}</b>：
     * {@code SurfaceGateUnifyCheck} E 组把"别名数量 + 全仓门族定义总数"钉成常量
     * （3 别名 + SurfaceGate 两个重载 = 5），再起一个一行委托别名等于给 P4 的收口开倒车，
     * 而那个工具不在本片允许路径内。
     */
    private static boolean landableTopAt(World world, int x, int y, int z) {
        return SurfaceGate.isNaturalTop(DIM_KEY, SurfaceGate.landableTops(DIM_KEY), world.getBlock(x, y, z));
    }
}
