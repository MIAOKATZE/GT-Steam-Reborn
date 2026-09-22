package com.miaokatze.gtsr.common.dimension.prosperity.ruins;

import java.util.Random;

import net.minecraft.world.World;

import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.framework.SurfaceGate;
import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;
import com.miaokatze.gtsr.common.dimension.framework.structure.ChunkSliceSink;
import com.miaokatze.gtsr.common.dimension.framework.structure.ChunkSpans;
import com.miaokatze.gtsr.common.dimension.framework.structure.GTSRWorldgenHash;
import com.miaokatze.gtsr.common.dimension.framework.structure.PlacementGate;
import com.miaokatze.gtsr.common.dimension.framework.structure.StructureBuilder;
import com.miaokatze.gtsr.common.dimension.framework.structure.StructureRegistry;
import com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityBlockResolver;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityPlanner;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityVariants;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ruin.RuinPlacer;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.ruin.StructureBurialTiers;
import com.miaokatze.gtsr.config.Config;
import com.miaokatze.gtsr.main.GTSteamReborn;

/**
 * 残缺机器放置器（dim1 S4a，plan §1.2 S4a / 02 §6 裁剪版）：
 * 频率 = 1/{@link Config#prosperityMachineChance} chunk × 群系机器权重（02 §1.1 表），
 * 机型在 {@link RuinedMachineShapes#ALL} 5 机型中均匀掷选，损伤度 20-95、部件缺失概率
 * 10%/25%/45% 双重掷上限 40%（02 §6.3 表），'C' 核心位落积碳壳 meta1——<b>无 TileEntity、无控制器、
 * 无修复/战利品</b>（用户裁剪范围）。放置经 {@link StructureBuilder} → 注入 {@link BlockSink}
 * （世界生成期 ChunkClampedSink / S5 指令期 DirectWorldSink 同一放置路径）。
 * <p>
 * 跨界协议：origin 在 chunk 内做 footprint 收缩钳制，整机完整落在当前 populate chunk 内
 * ——ChunkClampedSink 零丢弃，plan S4a 验收"无跨界缺角/无越界告警"由构造保证。
 * 所有随机从 chunk 确定性哈希派生（02 §0.3），禁用 populate 裸 Random。
 * <p>
 * <b>P7（plan §5 P7 / §2.1 L5 禁止项 / §2.2 H-2·H-3 / 审计 A-4「两套接地并存」）</b>三件事：
 * <ol>
 * <li><b>接地同源</b>：改造前整机落在<b>中心列列扫</b>得到的同一个平面 {@code surfaceY+1} 上
 * （与城/outpost 的逐列 {@link ProsperityTerrainProfile#heightAt} 不同源 ⇒ 机器一头扎进坡里、
 * 另一头悬空；16 格跨度内的错位幅度实测见 plan/investigation/p7b-placement-contract-20260919.md 的 T5）。
 * 现 {@link #place} 与 outpost/城同构：每列落地 y 由 {@link CityVariants.GroundFn} 给出，
 * 世界生成侧一律取 {@code heightAt}；{@code findSurfaceY} 不再作为本类的放置依据（P3 并列扫
 * 实现体的登记见文件末注释，那句"统一归 P7"由本片兑现）。</li>
 * <li><b>放置真值</b>：改造前 {@code placeAll} 是 {@code void}，编排器与统计都无从知道这台机器
 * 到底有没有落块；现返回 {@code boolean}，且该值 = {@link PlacementGate.Permit#commit(int)} 的
 * 结果（被 sink 接受的非空气落块数 &gt; 0）。0 块 ⇒ 不计成功、不扣预算。</li>
 * <li><b>门的一入口</b>：每 chunk 结构预算、同族互斥、H-2① 窗重复上限、H-2② 同族间距、就绪门 y 带
 * 全部走 {@link PlacementGate}；本类不再自带可落地表 y 带的那对字面量，也不再自带第二份掷骰序列
 * （P7c：{@link #roll(long, int, int, float)} 是唯一实现体，命中重放口 {@link #MACHINE_INTENT} 与
 * {@link #placeAll} 共用它）。</li>
 * </ol>
 * P4 遗留的"横向直调 {@code ProsperityOutpostPlacer.isNaturalProsperityTop}"（审计 A-2 #3 之二）
 * 一并消掉：本类改成像散布层那样直调框架单一谓词 {@link SurfaceGate}（成员集合真值仍在
 * {@code SurfaceGate}，本类零方块集合知识；申报同步见 {@code tools/dim1/SurfaceGateUnifyCheck}
 * 的 {@code GATE_CALL_SITES}）。
 * <p>
 * ═══ <b>P16-B1（plan §0 U2 / §1 G6·G7 / 任务包 item 1·2·3·4·6）：城外跨 chunk 巨型机器残骸</b> ═══
 * 改造前本类把"一座机器"锁死在单 chunk 内（{@code freeX = 16 - sizeX} 的收缩钳制 +
 * {@code freeX < 0 ⇒ return null}），跨 chunk 只有城内 {@code CitySliceSink} 一条路。现在：
 * <ol>
 * <li><b>候选池扩到含跨片机型</b>：{@link #POOL} = {@link RuinedMachineShapes#ALL}（5 小机型）
 * + {@link RuinedColossusShapes#ALL}（2 条总 bbox 超出单 chunk 的巨构）。掷骰实现体仍是唯一的
 * {@link #roll}，盐仍是 {@code SALT_MACHINE} 一把，消费序仍是
 * {@code nextDouble → nextInt(候选池) → nextInt(freeX+1) → nextInt(freeZ+1)} ⇒
 * <b>本片零新增随机源</b>（任务包 item 6）。小机型的 {@code freeX/freeZ} 收缩钳制一字未改，
 * 巨构改取 {@link ChunkSpans#anchorFreeBlocks()} 的整 chunk 抖动窗（原点仍落在锚点 chunk 内）。</li>
 * <li><b>切片协议不复制第二套</b>：跨片写入复用 framework 的 {@link ChunkSliceSink}
 * （城内 {@code CitySliceSink} 已改为它的子类），几何半边复用 {@link ChunkSpans}；
 * 本类只多做一件事——把自己 chunk 之外的 {@code dx/dz} 范围裁掉再进循环（省 8/9 的逐列接地），
 * 裁剪与门过滤同源 {@link ChunkSpans#localMin(int, int, int)}/{@link ChunkSpans#localMax(int, int, int)}。</li>
 * <li><b>邻槽怎么补上它那一份</b>：{@link #renderForeignSpans} 以本 chunk 为视角回扫
 * {@link RuinedColossusShapes#BACK_REACH_CHUNKS} 格锚点，逐个重放同一份 {@link #roll} 与同一份纯判定
 * {@link #spanAllowsAt}，只画与自己相交的那一片。populate 顺序因此无关、逐 chunk 幂等（城链同一先例）。</li>
 * <li><b>互斥与密度（判据 G7 / plan §6 风险 3 的答案）</b>：巨构<b>不新增一环</b>，它坐在既有
 * "outpost → 残缺机器 → 废墟"的<b>第二环内部</b>，与 5 个小机型抢同一个
 * {@code Config.prosperityMachineChance} 分母、同一个每 chunk 预算位（默认 1 座）
 * ⇒ 一座 chunk 上"巨构"与"小机型"是二选一，<b>总座数一条不增</b>；三环的掷骰代码与概率值一行没动。
 * 又因为巨构会压到邻槽，{@link #spanAllowsAt} 额外要求"总 bbox 覆盖到的每个 chunk 都不被城窗抑制、
 * 且 outpost/机器/废墟三族在该槽都不命中"（只读复用三族各自的公开重放口，本类不自算第二份概率）。
 * 这条让行<b>没过时本 chunk 的机器格整格作废</b>（不回落成小机型 ⇒ 不是两次机会）⇒
 * 净效果是"偶发让位"，密度只减不增。既有 {@code familySpacingAllows}（同族优先级档，gap=1）
 * 继续负责"两个巨构锚点贴脸时恰活一个"。</li>
 * <li><b>落块材质（G6）</b>：{@link #resolveBlock} 那份只认 3 个 gtsr 键的私有表已<b>删除</b>，
 * 本类与城/outpost/废墟族一样把 String 键交给 {@link CityBlockResolver} 解析（唯一一张红线键表，
 * P16-B1 在里面加了燃烧室与防爆玻璃两键）。小机型的键集与改造前逐字相同 ⇒ 它们的落块一块没变；
 * 新材质只出现在 {@link RuinedColossusShapes} 自己的记号表里，ruin 族的禁字符清单
 * （{@code RuinDamageOps.FORBIDDEN_CHARS}）一个字没撕。</li>
 * </ol>
 * ═══ <b>P16-B2（残骸形态，plan §0 U2 / §1 G8·G9）</b>：本类的机制一字未动，只加了"形态侧分支" ═══
 * <ol>
 * <li><b>渲染真值换成派生残骸档</b>：{@link RuinedColossusShapes.Wreck}（母体经"侵蚀降级 → 削顶 →
 * 同列塌落 → 落渣"四步定盘），机械件从母体的 7 成被压到少数（G9）；母体字节一格未改 ⇒
 * 名册 SHA 与 B1 的成对断言读数都沿用（见 {@link RuinedColossusShapes}）。</li>
 * <li><b>半埋</b>：{@link RuinedColossusShapes#morphAt} 给出 0..maxBury 层的埋深，埋住的部分
 * 一格都不写（不挖洞、不切断地表），第一层可见格恰好坐在该列地表顶上（G8①③）。</li>
 * <li><b>B1 留的 {@code anchorFreeBlocks=15} 抖动位就此有了第二用途</b>：同一个世界原点既决定
 * "压到邻槽哪一侧"，又决定"烂到哪一档 / 埋几层"（G8④ 与侵蚀联动）。</li>
 * <li><b>零新随机源</b>：形态层只用 {@link RuinDamageOps#roll}（框架 cellSeed + splitmix64 那一份
 * 实现体）+ 本族形态盐；{@link #roll} 的消费序、{@code new Random} 的把数、Config 键数三项
 * 都由 {@code RuinFamilyCheck} 的 K5 组重新钉过。</li>
 * </ol>
 * 残留边界（诚实申报，逐条处置见 B2 回执）：跨片巨构的就绪门仍只过 {@link PlacementGate#readyAtSpan}
 * 那条<b>纯</b> y 带臂——世界顶块那一臂要读 {@code world.getBlock}，邻槽无法复现锚点结论，硬留着就会造出
 * "邻槽画半座、锚槽弃权"的鬼影剪影；城内跨 chunk 结构从一开始就是这条口径（{@code placeCities}
 * 只按 {@code heightAt} 接地）。<b>P16-B2 把这条纯臂从"只判中心列"收紧为"总 bbox 覆盖到的每一列
 * 都要在带内"</b>（{@link #spanAllowsAt}），于是"半埋 + 坡地"这一类真实失败形态被它挡下；
 * <b>P19 §F 把"水"的那一半也闭合了</b>：framework 现在有纯函数湿区谓词
 * {@link PlacementGate#dryFootprint}，本类小机型（{@link #placeAll}）与跨片巨构
 * （{@link #spanAllowsAt} 第⑤道）在落块前过同一道干区门，失败弃位不重试；
 * 仍开放的只剩"非自然顶不挡"那一半——顶块臂要读 {@code world.getBlock}，跨槽不可复现，
 * 维持"只在单 chunk 小机型上判"的既有口径不变。
 */
public final class RuinedMachinePlacer {

    /** 盐 "mac"（02 §6.2 代码 15 同款）。 */
    private static final long SALT_MACHINE = 0x6D6163L;

    /** 本类所属维度键（P4 起门的显式维度入参；与 outpost/散布层同一词汇）。 */
    private static final String DIM_KEY = SurfaceGate.DIM78;

    /**
     * 群系机器权重表的<b>真实值仍由编排器折算传入</b>（{@link #placeAll} 的入参）；P7c 起本类只为
     * H-2 的命中重放另算一次同口径权重（{@link #machineWeightAt}，走 L1 唯一出口 + 编排器的权重表，
     * 不是第二份权重真值），两者逐槽同值由 {@code PlacementContractCheck} C7 钉住。
     */

    private static volatile boolean registered;

    /**
     * 机器族<b>唯一的候选池</b>（<b>P16-B1</b>：5 个小机型 + {@link RuinedColossusShapes#ALL} 跨片巨构）。
     * <p>
     * 它是"巨构不叠加密度"的结构性保证：池子只有一个 ⇒ {@link #roll} 里只有一次
     * {@code nextInt(POOL.length)} ⇒ 一个 chunk 上"出巨构"与"出小机型"是<b>同一次抽签的两个结果</b>，
     * 不是两条独立的 1/N。池子的长度只由两张形状表派生（本类不写第二个数字真值）。
     */
    static final RuinedMachineShapes.Shape[] POOL = buildPool();

    /**
     * 五个<b>小机型</b>各自的"验证过的最深埋深档"（<b>P17 S-D</b>；下标与
     * {@link RuinedMachineShapes#ALL} 同序）。
     * <p>
     * 值一律由 {@link StructureBurialTiers#ceilingFor} 从<b>该机型自己的字符盘</b>实算
     * （几何界 {@code (sizeY-1)/2} ∩ "露出比 ≥ 三分之一"那道闸），本类不写第二个数字真值，
     * 也不新增任何申报常数 ⇒ 五个小机型的形状字节一格未改，逐名模板 SHA 面不受影响。
     * 跨片巨构<b>不在</b>这张表里：它的档就是 {@code Colossus.maxBury}，由
     * {@link RuinedColossusShapes} 的静态契约逐档验过，走 {@link RuinedColossusShapes#morphAt}。
     */
    private static final int[] SMALL_BURY_CEILINGS = buildSmallBuryCeilings();

    private static int[] buildSmallBuryCeilings() {
        final RuinedMachineShapes.Shape[] small = RuinedMachineShapes.ALL;
        final int[] out = new int[small.length];
        for (int i = 0; i < small.length; i++) {
            out[i] = StructureBurialTiers.ceilingFor(small[i].sizeX, small[i].sizeY, small[i].sizeZ, small[i]::charAt);
        }
        return out;
    }

    /**
     * 本机型的验证档查表（引用相等，≤5 次；巨构形状走这里必得 0 —— 它的埋深由 {@code Morph} 带进来，
     * 两轨不互踩，见 {@link RuinedColossusShapes#morphAt}）。
     * <b>public 的唯一理由</b>：离线判据要按"档表 × 逐张盘"报实测窗口，不能再抄一份算式。
     */
    public static int buryCeilingOf(RuinedMachineShapes.Shape shape) {
        final RuinedMachineShapes.Shape[] small = RuinedMachineShapes.ALL;
        for (int i = 0; i < small.length; i++) {
            if (small[i] == shape) {
                return SMALL_BURY_CEILINGS[i];
            }
        }
        return 0;
    }

    /**
     * 一次小机型放置的埋深（<b>P17 S-D</b>）：身份按<b>锚点原点</b>反查（与巨构那支同一条坐标口径），
     * 档深走 {@link StructureBurialTiers#burialDepthFor} —— 小机型改造前<b>没有</b>埋深通道，
     * 所以取的是已夹紧的档表下限（零新增随机源、零新增族盐）。
     * 默认档（身份不可得）⇒ 0 ⇒ 与改造前逐位相同。
     */
    public static int buryingAt(RuinedMachineShapes.Shape shape, int originX, int originZ) {
        return buryingAt(shape, originX, originZ, StructureBurialTiers.rosterIndexAtOrigin(originX, originZ));
    }

    /**
     * {@link #buryingAt(RuinedMachineShapes.Shape, int, int)} 的<b>身份显式形态</b>（同一实现体）：
     * 离线判据据此在未装配 L1 账本的 JVM 里逐档复算生产同一条算式；装配了账本时两条出口逐点同值
     * （{@code P17StructureBiomeVarianceCheck} 的 SOURCE 组）。
     */
    public static int buryingAt(RuinedMachineShapes.Shape shape, int originX, int originZ, int rosterIndex) {
        return StructureBurialTiers.burialDepthFor(buryCeilingOf(shape), rosterIndex);
    }

    private static RuinedMachineShapes.Shape[] buildPool() {
        final RuinedMachineShapes.Shape[] small = RuinedMachineShapes.ALL;
        final RuinedMachineShapes.Shape[] out = new RuinedMachineShapes.Shape[small.length
            + RuinedColossusShapes.ALL.length];
        System.arraycopy(small, 0, out, 0, small.length);
        for (int i = 0; i < RuinedColossusShapes.ALL.length; i++) {
            out[small.length + i] = RuinedColossusShapes.ALL[i].shape;
        }
        return out;
    }

    private RuinedMachinePlacer() {}

    /**
     * 注册数（真值仍是 {@link #POOL}；给编排器的注册证据日志与机检读，不持有第二份数字）。
     * 与 {@code RuinPlacer.registeredCount()} 同形。
     */
    public static int registeredCount() {
        return POOL.length;
    }

    /** 其中<b>跨 chunk 巨构</b>的条数（= {@link RuinedColossusShapes#ALL} 长度；注册证据行回显用）。 */
    public static int spanningCount() {
        return RuinedColossusShapes.ALL.length;
    }

    /**
     * 向 {@link StructureRegistry} 登记机器族全部机型（dimension=PROSPERITY，幂等）：
     * {@link RuinedMachineShapes#ALL} 5 小机型 + <b>P16-B1</b> {@link RuinedColossusShapes#ALL} 跨片巨构。
     * 由 ProsperityWorldGenerator 构造时调用（CommonProxy.init 注册线），S5 /gtsr structure
     * 补全同源消费。placer 回调走 DirectWorldSink 语义 flag，并<b>自包一层
     * {@link CityBlockResolver}</b>（String 键→Block；与 {@code ProsperityOutpostPlacer}
     * /{@code RuinPlacer} 的注册线同形，双层包裹幂等：已解析成 Block 的写入直通）。
     * <p>
     * P7 起的 roster 形状（{@link StructureRegistry.Entry} 注释里有选型理由）：族 =
     * {@link PlacementGate#FAMILY_MACHINE}；放置分母与窗上限一律传 0 = 跟随 Config 族键
     * （本类的分母真值仍只有 {@link Config#prosperityMachineChance} 一处）；
     * {@code allowsDamagedVariant=false}——机型本身就是残骸，P8 的损毁算子不再叠加。
     * <p>
     * <b>巨构条目的 footprint 口径与"单 chunk 收缩"不同</b>：登记的是<b>总 bbox</b> 的
     * {@code sizeX/sizeZ}（可以 &gt;16，这是申报而不是钳制；成对断言见
     * {@code tools/dim1/OutpostTemplateCheck} 的 G7 组），且不做旋转安全的外接放大——
     * 本族与 5 个小机型一样没有旋转算子。S5 指令下写的是<b>整座</b>（不切片：DirectWorldSink
     * 不受 chunk 钳制，让玩家能一次摆出完整巨构验收）。
     */
    public static void registerVariants() {
        if (registered) {
            return;
        }
        registered = true;
        for (final RuinedMachineShapes.Shape shape : POOL) {
            StructureRegistry.register(
                new StructureRegistry.Entry(
                    shape.name,
                    StructureRegistry.Dimension.PROSPERITY,
                    shape.sizeX,
                    shape.sizeZ,
                    (sink, x, y, z, seed) -> place(
                        new StructureBuilder(new CityBlockResolver(sink)),
                        new Random(seed),
                        shape,
                        x,
                        z,
                        CityVariants.flatGround(y),
                        BlockSink.FLAG_DIRECT),
                    PlacementGate.FAMILY_MACHINE,
                    0,
                    0,
                    false));
        }
    }

    /**
     * populate 入口（无显式门上下文的兼容形态：离线断言与旧调用点用）——自行按 chunk 建一个
     * {@link PlacementGate.ChunkGate}，语义与 {@link #placeAll(World, long, int, int, float, BlockSink,
     * PlacementGate.ChunkGate)} 在"本 chunk 只有机器这一族来问门"时逐位相同。
     */
    public static boolean placeAll(World world, long worldSeed, int cx, int cz, float biomeMachineWeight,
        BlockSink sink) {
        return placeAll(world, worldSeed, cx, cz, biomeMachineWeight, sink, null);
    }

    /**
     * populate 入口：掷频 → 掷机型 → <b>过 {@link PlacementGate}（预算/互斥/H-2① 窗重复上限/H-2② 同族
     * 间距）</b>→ 逐列接地 + 就绪门 → 放置 → <b>按真实落块数兑现许可</b>。
     * <p>
     * <b>P7c</b>：掷骰不再写在本方法里，而是收成唯一的 {@link #roll(long, int, int, float)}——
     * {@link #intentAt(long, int, int)}（H-2 的命中重放口）与 {@code placeAll} 共用同一段实现体，
     * 所以"别窗别槽会不会放这一族"与"本槽到底放没放"不可能各说一套（由
     * {@code tools/dim1/PlacementContractCheck} D7/A9 钉住）。随机流消耗序列与 P7b 逐位相同
     * （门判定不消费 {@code r}），故本片的落点/落块只在 H-2 两条规则咬合处变化。
     *
     * @param biomeMachineWeight 群系机器权重（02 §1.1：草原 0.7/森林 1.2/荒漠 0.9/沼泽 0.8）
     * @param gate               本 chunk 的结构门（编排器创建）；{@code null} = 自派生
     * @return true = 本 chunk 真实落了至少一块的机器（编排器据此扣预算）
     */
    public static boolean placeAll(World world, long worldSeed, int cx, int cz, float biomeMachineWeight,
        BlockSink sink, PlacementGate.ChunkGate gate) {
        if (sink == null) {
            return false;
        }
        // —— P16-B1 第一步：先把"别人家的巨构压在本 chunk 上的那一片"补画掉（无门、无预算、纯重放）。
        // 这一步与本槽掷不掷中都无关，所以它必须在 roll 之前，且对未命中槽同样要跑。
        renderForeignSpans(worldSeed, cx, cz, sink);
        // —— 本槽自己的机器族掷骰（候选池含跨片巨构，实现体唯一）——
        final Roll roll = roll(worldSeed, cx, cz, biomeMachineWeight);
        if (roll == null) {
            return false; // 禁用位 / 掷骰未中 / footprint 超出单 chunk（小机型收缩钳制）
        }
        // —— P16-B1 第二步：跨片巨构先过"覆盖让行"（纯函数），没过就整格作废（不回落成小机型）——
        if (roll.span && !spanAllowsAt(worldSeed, cx, cz, roll)) {
            return false;
        }
        // —— 结构侧唯一入口（P7/P7c）：预算 → 同族互斥 → H-2① 窗重复上限 → H-2② 同族间距。——
        final PlacementGate.ChunkGate chunkGate = gate != null ? gate
            : PlacementGate.beginChunk(DIM_KEY, worldSeed, cx, cz);
        final PlacementGate.Permit permit = chunkGate
            .request(PlacementGate.FAMILY_MACHINE, roll.intent.templateName, MACHINE_INTENT);
        if (permit == null) {
            return false;
        }
        final int x = roll.intent.originX;
        final int z = roll.intent.originZ;
        // 落点判定与接地同源（P7）：三族共用 PlacementGate.groundFn 这同一个供给器——中心列的
        // heightAt 值既是就绪门读的顶块所在行，也是整机逐列落地的基准（改造前这里走 findSurfaceY
        // 列扫出的整台平面，与落地用的另一套高度并存，审计 A-4）。
        final CityVariants.GroundFn ground = PlacementGate.groundFn(worldSeed);
        final int centerX = x + roll.shape.sizeX / 2;
        final int centerZ = z + roll.shape.sizeZ / 2;
        final int groundY = ground.groundY(centerX, centerZ);
        // P16-B1：跨片巨构只过<b>纯</b>的 y 带臂（世界顶块臂不可跨槽复现，留着就会造鬼影剪影；
        // 城内跨 chunk 结构从来就是这条口径）。小机型两臂全判，判定与改造前逐字相同。
        final boolean ready = roll.span ? PlacementGate.readyAtSpan(groundY)
            : PlacementGate.readyAt(groundY, landableTopAt(world, centerX, groundY, centerZ));
        if (!ready) {
            permit.abort(); // 未落块：显式归还，预算不扣
            return false;
        }
        // P19 湿区避让（plan §F）：掷骰命中后、落块前，footprint 过纯函数干区门。跨片巨构已在
        // {@link #spanAllowsAt} 的第⑤道按同一谓词对总 bbox 检查（邻槽补片同判），这里不再算第二遍；
        // 小机型（单 chunk）就地检查。失败 ⇒ 弃位且不重试，本 chunk 结构变稀属预期密度影响。
        if (!roll.span && !PlacementGate.dryFootprint(
            worldSeed,
            x,
            z,
            x + roll.shape.sizeX - 1,
            z + roll.shape.sizeZ - 1,
            PlacementGate.DRY_RATIO_STRUCTURAL)) {
            permit.abort(); // 未落块：显式归还，预算不扣
            return false;
        }
        final PlacementGate.CountingSink counter = PlacementGate.counting(sink);
        // 键解析一律走城/outpost/废墟族同一张红线表（P16-B1 起本类不再有私有 resolveBlock）。
        // 跨片：切片器包在计数外侧 ⇒ commit 的真值只数"真的落进本 chunk 的块"。
        final StructureBuilder builder = new StructureBuilder(
            roll.span ? new ChunkSliceSink(new CityBlockResolver(counter), cx, cz) : new CityBlockResolver(counter));
        if (roll.span) {
            placeSlice(
                builder,
                roll.rnd,
                roll.shape,
                x,
                z,
                cx,
                cz,
                ground,
                BlockSink.FLAG_POPULATE,
                // P16-B2：形态由"锚点自由格 + worldSeed"的纯函数给出，锚点槽与每个邻槽各自重放必同源
                morphAt(worldSeed, roll.shape, x, z));
        } else {
            // P17 S-D：小机型改前<b>没有</b>埋深通道（整座永远骑在地表上）；现在它与巨构读同一张
            // 群系埋深档表，只是窗口上界换成本机型字符盘实算的验证档（见 #SMALL_BURY_CEILINGS）。
            // morph 仍传 null ⇒ 渲染的还是母体字符盘（小机型没有 P16-B2 那四档派生残骸）。
            place(
                builder,
                roll.rnd,
                roll.shape,
                x,
                z,
                ground,
                BlockSink.FLAG_POPULATE,
                null,
                buryingAt(roll.shape, x, z));
        }
        return permit.commit(counter.solid());
    }

    /**
     * 跨片巨构的<b>形态入口</b>（<b>P16-B2</b>）：把"这一座烂到哪一档、埋进地里几层"收敛成
     * <b>全仓唯一一处</b>调用（{@link #placeAll} 的锚点侧与 {@link #renderForeignSpans} 的邻槽侧
     * 都走这里），非巨构返回 {@code null} = 按母体渲染、埋深 0。
     * <p>
     * 为什么必须只有一处：分片协议要求"每个 chunk 画自己那一片"时看到的必须是<b>同一座</b>残骸，
     * 而两处各自算一次就是两个真值源（B1 的鬼影剪影正是这类问题的形状）。
     * <p>
     * <b>P17 S-D 补的一刀</b>：群系埋深档也<b>必须</b>在这唯一一处解析，且入参是
     * {@code originX/originZ}（锚点世界原点）而不是当前 chunk 号 —— 邻槽补片
     * （{@link #renderForeignSpans}）带的就是这两个原点值，按 chunk 号反查身份会让同一座巨构
     * 在锚点槽读到 A 档、邻槽读到 B 档，埋深差几层就是鬼影。
     */
    private static RuinedColossusShapes.Morph morphAt(long worldSeed, RuinedMachineShapes.Shape shape, int originX,
        int originZ) {
        return morphAt(worldSeed, shape, originX, originZ, StructureBurialTiers.rosterIndexAtOrigin(originX, originZ));
    }

    /**
     * {@link #morphAt(long, RuinedMachineShapes.Shape, int, int)} 的<b>身份显式形态</b>
     * （<b>P17 S-D</b>，同一个实现体，只是"锚点原点 → 名册下标"那一跳由调用方给出）：
     * 离线判据据此在未装配 L1 账本的 JVM 里逐档复算生产同一条形态/埋深链；装配了账本时两条出口
     * 逐点同值（{@code P17StructureBiomeVarianceCheck} 的 SOURCE 组）。
     *
     * @param rosterIndex 锚点原点的 L1 名册下标（{@code -1} ⇒ 默认档 ⇒ 与改造前逐位相同）
     */
    public static RuinedColossusShapes.Morph morphAt(long worldSeed, RuinedMachineShapes.Shape shape, int originX,
        int originZ, int rosterIndex) {
        final RuinedColossusShapes.Colossus c = RuinedColossusShapes.byName(shape.name);
        if (c == null) {
            return null;
        }
        warnUnresolvableColossusKeysOnce();
        return RuinedColossusShapes.morphAt(worldSeed, originX, originZ, c, rosterIndex);
    }

    /** GT 壳键是否已经解析过（只在第一次放巨构时探测一次，之后零开销）。 */
    private static volatile boolean gtKeysChecked;

    /**
     * <b>P16-B2 闭合 B1 边界⑤</b>（beta-1 包上玻璃字段为 null ⇒ 静默缺玻璃）：把"缺了哪一档材质"
     * 从<b>静默</b>变成<b>一次显式告警</b>。
     * <p>
     * 探测走生产解析链的公开面（{@link CityBlockResolver} 的 String 键 → Sink 转发判定），
     * 本类不自持第二张键表；<b>只告警不改变生成行为</b>（缺材质那一档仍按既有防御链跳过该格，
     * 与城/outpost 的 GT 缺席口径同形）。为什么不在注册期探测：{@code registerVariants} 跑在
     * {@code CommonProxy.init} 的注册线上，那时 GT 的字段可能还没填好，会造出假告警。
     */
    private static void warnUnresolvableColossusKeysOnce() {
        if (gtKeysChecked) {
            return;
        }
        gtKeysChecked = true;
        final StringBuilder missing = new StringBuilder();
        for (final String key : RuinedColossusShapes.gtDependentKeys()) {
            final boolean[] got = { false };
            final BlockSink probe = (x, y, z, block, meta, flags) -> {
                got[0] = true;
                return true;
            };
            new CityBlockResolver(probe).setBlock(0, 1, 0, key, 0, BlockSink.FLAG_DIRECT);
            if (!got[0]) {
                if (missing.length() > 0) {
                    missing.append(", ");
                }
                missing.append(key);
            }
        }
        if (missing.length() > 0) {
            GTSteamReborn.LOG.warn(
                "[GTSR] 城外跨 chunk 巨构的 GT 壳键解析落空 ⇒ 这些材质档整批不落块（不炸生成链；plan §1 G6 的" + " beta-1 玻璃字段口径）: {}",
                String.valueOf(missing));
        }
    }

    /**
     * 跨片巨构的<b>纯判定</b>（<b>P16-B1</b>，锚点侧 {@link #placeAll} 与邻槽侧
     * {@link #renderForeignSpans} 共用的唯一实现体）：给定"锚点槽 (ax,az) 掷出了巨构"这一事实，
     * 回答它到底该不该成型。四道都是 {@code (worldSeed, ax, az)} 的纯函数，<b>一次都不读世界</b>——
     * 这是跨 chunk 分片协议成立的前提（邻槽必须能独立复现同一个结论）。
     * <ol>
     * <li><b>让行给"任何别的城外结构"</b>：outpost 在本族之前问门，它只要命中就迟早占掉那个预算位；
     * 本类不改它的掷骰，所以由巨构这一侧躲（取证件 {@code RuinPlacer} P8 的"新族单向让行"同一方向，
     * 命中集也直接复用 P8 那份 {@code RuinPlacer.RUIN_NEIGHBORHOOD_INTENT}，不另拼一张）。
     * 这条同时是"邻槽能复现锚点结论"的机关：锚点上除我自己之外没有任何命中 ⇒ 真实门在被问到时
     * 一定是空的 ⇒ 下面的探测门与真实门逐臂同判。</li>
     * <li><b>覆盖让行</b>：总 bbox 覆盖到的<b>每一个</b> chunk（锚点自己除外）都不得被城窗抑制，
     * 且 outpost / 机器 / 废墟三族在该槽的命中重放口都必须是空 ⇒ 巨构不会与任何既有城外结构叠罗汉，
     * 也不会把半座结构插进城 buffer 里。</li>
     * <li><b>门</b>：为锚点槽临时建一把 {@link PlacementGate.ChunkGate} 走
     * {@code request(FAMILY_MACHINE, 模板名, MACHINE_INTENT)}（预算/互斥/H-2①/H-2② 的判定序列
     * 只有 {@code request0} 这一份实现，本类不重抄），拿到许可后立刻 {@code abort()}——
     * 探测不扣预算，真正的扣减只发生在锚点自己那次 {@link #placeAll} 的真实门上。</li>
     * <li><b>y 带</b>：{@link PlacementGate#readyAtSpan(int)}（纯臂，理由见 {@link #placeAll} 内注释）。</li>
     * </ol>
     *
     * @return true = 这座巨构成型（各槽各自画自己那一片）；false = 本槽这一格作废
     */
    private static boolean spanAllowsAt(long worldSeed, int ax, int az, Roll roll) {
        final int x0 = ChunkSpans.chunkOf(roll.intent.originX);
        final int x1 = ChunkSpans.chunkOf(roll.intent.originX + roll.shape.sizeX - 1);
        final int z0 = ChunkSpans.chunkOf(roll.intent.originZ);
        final int z1 = ChunkSpans.chunkOf(roll.intent.originZ + roll.shape.sizeZ - 1);
        for (int cx = x0; cx <= x1; cx++) {
            for (int cz = z0; cz <= z1; cz++) {
                if (CityPlanner.citiesNear(worldSeed, cx, cz).length > 0) {
                    return false; // 覆盖到城 buffer ⇒ 编排器在那一格会整段抑制，邻槽画不出对称的片
                }
                // 只读复用 P8 那份<b>合并命中集</b>（ruin → outpost → 机器三族，真值在 RuinPlacer 一处）：
                // 覆盖到的每一格里，任何"不是我自己这次命中"的意图都让本座让行。锚点自己也在循环里，
                // 靠 sameHit 把自己摘出去——于是 outpost 抢在机器之前落地的形态被同一条判据覆盖，
                // 本类不再横向引用 ProsperityOutpostPlacer（SurfaceGateUnifyCheck E 组那条
                // "机器层零横向 placer 引用"的申报因此一字未改）。
                final PlacementGate.Intent other = RuinPlacer.RUIN_NEIGHBORHOOD_INTENT.intentAt(worldSeed, cx, cz);
                if (other != null && !sameHit(other, roll.intent)) {
                    return false;
                }
            }
        }
        final PlacementGate.ChunkGate probe = PlacementGate.beginChunk(DIM_KEY, worldSeed, ax, az);
        final PlacementGate.Permit p = probe
            .request(PlacementGate.FAMILY_MACHINE, roll.intent.templateName, MACHINE_INTENT);
        if (p == null) {
            return false;
        }
        p.abort();
        // 第④道（P16-B2 收紧）：y 带从"只看中心列"改成"总 bbox 覆盖到的每一列都要在带内"。
        // 半埋把整座的 y 带整体下移 bury 层，而 24×20 的跨度里"中心列在带内、边角列在带外"
        // 是真实存在的（坡地/台地边缘），只判中心列会让巨构一头扎进坡里——那正是 P7 当年
        // 用"逐列 heightAt"消掉的那类错位，只是这里换成 y 带版本。
        // 仍然<b>全是纯函数</b>（heightAt 是 worldSeed 的纯函数，一次都不读 world.getBlock），
        // 所以邻槽复现锚点结论的前提一字未破。
        final CityVariants.GroundFn ground = PlacementGate.groundFn(worldSeed);
        final int ox = roll.intent.originX;
        final int oz = roll.intent.originZ;
        for (int dz = 0; dz < roll.shape.sizeZ; dz++) {
            for (int dx = 0; dx < roll.shape.sizeX; dx++) {
                if (!PlacementGate.readyAtSpan(ground.groundY(ox + dx, oz + dz))) {
                    return false;
                }
            }
        }
        // 第⑤道（P19 §F 湿区避让）：总 bbox 的干区门（PlacementGate.dryFootprint，四角+中心+
        // 周界步 8 采样）。三个读数（heightAt/lakeAt/strengthAt）全是 (worldSeed, x, z) 的纯函数
        // ⇒ 邻槽复现锚点结论的前提一字未破；失败 ⇒ 本座弃位且不重试（锚点与每个邻槽同判，
        // 不会出现"锚槽弃了、邻槽还画半座泡水剪影"的分叉）。
        if (!PlacementGate.dryFootprint(
            worldSeed,
            ox,
            oz,
            ox + roll.shape.sizeX - 1,
            oz + roll.shape.sizeZ - 1,
            PlacementGate.DRY_RATIO_STRUCTURAL)) {
            return false;
        }
        return true;
    }

    /**
     * 跨片巨构的<b>公开成型重放口</b>（<b>P16-B2</b>，只给 {@code tools/dim1} 判据用）：
     * 回答"锚点槽 (ax,az) 这一格到底会不会落出一座跨 chunk 巨构"，用的就是生产那一条
     * {@link #roll} + {@link #spanAllowsAt}（<b>工具不自算第二份判定</b>，否则判据与生产可以各说一套）。
     * <p>
     * 公开它唯一要买的东西是"邻槽是否也会画到自己身上"这条判据可以被离线枚举——
     * B1 的边界③（覆盖到城 buffer 时邻槽不再补片）就是靠它证伪/证真的。
     */
    public static boolean spanReadyAt(long worldSeed, int ax, int az) {
        final Roll r = roll(worldSeed, ax, az, machineWeightAt(ax, az));
        return r != null && r.span && spanAllowsAt(worldSeed, ax, az, r);
    }

    /** 两次命中是否同一次（模板名 + 原点）；用于把"我自己"从合并命中集里摘出去。 */
    private static boolean sameHit(PlacementGate.Intent a, PlacementGate.Intent b) {
        return a.templateName.equals(b.templateName) && a.originX == b.originX && a.originZ == b.originZ;
    }

    /**
     * 邻槽补片（<b>P16-B1</b>）：以本 chunk 为视角回扫可能压到自己身上的锚点巨构，逐个重放
     * {@link #roll} + {@link #spanAllowsAt}（两份实现体都与锚点侧同源），只把与自己相交的那一片写下去。
     * <p>
     * 不问门、不占预算、不 {@code commit}——预算位与"这座结构算不算落了地"都记在锚点那一格上
     * （{@link #placeAll}）；本方法只负责把已经成立的巨构补全，所以它对每 chunk 座数上界
     * （{@code tools/dim1/RuinFamilyCheck} F3 钉的那条）没有贡献。写入链与锚点侧同形：
     * 切片 → 键解析 → 计数 → 世界。
     */
    private static void renderForeignSpans(long worldSeed, int cx, int cz, BlockSink sink) {
        for (int ax = cx - RuinedColossusShapes.BACK_REACH_CHUNKS; ax <= cx; ax++) {
            for (int az = cz - RuinedColossusShapes.BACK_REACH_CHUNKS; az <= cz; az++) {
                if (ax == cx && az == cz) {
                    continue; // 本槽自己的锚点由 placeAll 主管（含真实门与预算）
                }
                final Roll r = roll(worldSeed, ax, az, machineWeightAt(ax, az));
                if (r == null || !r.span) {
                    continue;
                }
                if (!ChunkSpans.intersects(r.intent.originX, r.shape.sizeX, r.intent.originZ, r.shape.sizeZ, cx, cz)) {
                    continue;
                }
                if (!spanAllowsAt(worldSeed, ax, az, r)) {
                    continue;
                }
                final PlacementGate.CountingSink counter = PlacementGate.counting(sink);
                placeSlice(
                    new StructureBuilder(new ChunkSliceSink(new CityBlockResolver(counter), cx, cz)),
                    r.rnd,
                    r.shape,
                    r.intent.originX,
                    r.intent.originZ,
                    cx,
                    cz,
                    PlacementGate.groundFn(worldSeed),
                    BlockSink.FLAG_POPULATE,
                    // P16-B2：邻槽侧与锚点侧调的是同一个 morphAt(worldSeed, 同一个世界原点) ⇒ 同一座残骸
                    morphAt(worldSeed, r.shape, r.intent.originX, r.intent.originZ));
            }
        }
    }

    /**
     * H-2 命中重放的公开入口（纯函数）：槽位 (cx,cz) 在<b>没有任何窗上限/间距约束</b>时会请求哪台
     * 机型、落在何处；{@code null} = 本槽不请求。群系权重按 L1 唯一出口自算（与编排器给
     * {@link #placeAll} 的那一份同值，由 {@code PlacementContractCheck} C7 在真地形上逐槽对账钉住）。
     */
    public static PlacementGate.Intent intentAt(long worldSeed, int cx, int cz) {
        return intentAt(worldSeed, cx, cz, machineWeightAt(cx, cz));
    }

    /** {@link #intentAt(long, int, int)} 的权重显式形态（{@code placeAll} 一侧用编排器算出的权重）。 */
    public static PlacementGate.Intent intentAt(long worldSeed, int cx, int cz, float biomeMachineWeight) {
        final Roll roll = roll(worldSeed, cx, cz, biomeMachineWeight);
        return roll == null ? null : roll.intent;
    }

    /** 供 {@link PlacementGate} 枚举邻域/窗内槽位用本族命中集的重放口（生产侧唯一一份）。 */
    public static final PlacementGate.IntentFn MACHINE_INTENT = new PlacementGate.IntentFn() {

        @Override
        public PlacementGate.Intent intentAt(long worldSeed, int cx, int cz) {
            return RuinedMachinePlacer.intentAt(worldSeed, cx, cz);
        }
    };

    /** 机器族的群系权重（L1 唯一出口 + 编排器的权重表；与 {@code ProsperityWorldGenerator#biomeWeight} 同口径）。 */
    private static float machineWeightAt(int cx, int cz) {
        final GTSRBiomeAuthority.Resolution res = GTSRBiomeAuthority.forDimKey(GTSRBiomeAuthority.DIM_KEY_PROSPERITY)
            .ordinalAt((cx << 4) + 8, (cz << 4) + 8);
        return ProsperityWorldGenerator.weightForRosterIndex(res.ordinal, ProsperityWorldGenerator.MACHINE_WEIGHTS);
    }

    /** 一次掷骰的结果：命中意图 + 掷完意图所需的随机流后的 {@code Random}（供损伤/缺失继续消耗）。 */
    private static final class Roll {

        final PlacementGate.Intent intent;
        final Random rnd;
        final RuinedMachineShapes.Shape shape;
        /** <b>P16-B1</b>：这一次抽到的是不是<b>跨 chunk 巨构</b>（总 bbox 有一边超出单 chunk）。 */
        final boolean span;

        Roll(PlacementGate.Intent intent, Random rnd, RuinedMachineShapes.Shape shape, boolean span) {
            this.intent = intent;
            this.rnd = rnd;
            this.shape = shape;
            this.span = span;
        }
    }

    /**
     * 掷骰的<b>唯一</b>实现体（P7c）：频率 = 1/{@link Config#prosperityMachineChance} × 群系机器权重，
     * 机型在 {@link #POOL}（5 小机型 + P16-B1 的跨片巨构）中均匀掷选，origin 由余量掷出。
     * 顺序与 P7b 逐位一致（{@code nextDouble → nextInt(机型) → nextInt(freeX+1) → nextInt(freeZ+1)}），
     * 只是从 {@code placeAll} 的方法体里原样搬进来 ⇒ 池子不变时落点零漂移。
     * <p>
     * <b>P16-B1 的两条 origin 窗口</b>（同一份代码里的单点分流，不是两套掷骰）：
     * <ul>
     * <li>小机型（{@code sizeX/sizeZ ≤ 16}）：仍是原来的 footprint 收缩钳制，整机完全落在当前 chunk，
     * {@link ChunkSpans} 与 {@link CitySliceSink} 都不参与 ⇒ 既有 5 机型的落点与落块<b>逐位不变</b>
     * （候选池变长引起的漂移是另一回事，见 {@link #POOL}）；</li>
     * <li>跨片巨构：收缩钳制在数学上不可用（{@code 16 - sizeX < 0}），改成"原点落在锚点 chunk 的任意一列"
     * （{@link ChunkSpans#anchorFreeBlocks()}）⇒ 总 bbox 天然压到 1~4 个邻槽，由分片协议补全。
     * {@code Intent} 里申报的 {@code sizeX/sizeZ} 是<b>总 bbox</b>（成对断言 G7 第二条的申报值）。</li>
     * </ul>
     */
    private static Roll roll(long worldSeed, int cx, int cz, float biomeMachineWeight) {
        final int chance = Config.prosperityMachineChance;
        if (chance <= 0 || biomeMachineWeight <= 0F) {
            return null; // 0 = 禁用（plan S4a 失败回退开关）
        }
        final Random r = new Random(GTSRWorldgenHash.chunkSeed(worldSeed, cx, cz) ^ SALT_MACHINE);
        // P(生成/chunk) = 群系机器权重 / chance。分母唯一出处 = Config.prosperityMachineChance
        // （P5 plan §2.4 判据 4 / §3.1 更正 2：改造前这句注释写死过一个具体分母，与代码默认值漂移，
        // 导致机器密度口径三处不一致；此处起只写公式与出处，不写数字。）
        if (r.nextDouble() * chance >= biomeMachineWeight) {
            return null;
        }
        final RuinedMachineShapes.Shape shape = POOL[r.nextInt(POOL.length)];
        final boolean span = shape.sizeX > ChunkSpans.CHUNK_BLOCKS || shape.sizeZ > ChunkSpans.CHUNK_BLOCKS;
        // footprint 收缩钳制：origin 使整机（含垫层）完全落在当前 chunk（>16 格形状防御性跳过）
        final int freeX = span ? ChunkSpans.anchorFreeBlocks() : ChunkSpans.CHUNK_BLOCKS - shape.sizeX;
        final int freeZ = span ? ChunkSpans.anchorFreeBlocks() : ChunkSpans.CHUNK_BLOCKS - shape.sizeZ;
        if (freeX < 0 || freeZ < 0) {
            return null;
        }
        final int x = (cx << 4) + r.nextInt(freeX + 1);
        final int z = (cz << 4) + r.nextInt(freeZ + 1);
        return new Roll(new PlacementGate.Intent(shape.name, x, z, shape.sizeX, shape.sizeZ), r, shape, span);
    }

    /**
     * 落点地表门（P7 改道：直调框架单一谓词，不再横向借 {@code ProsperityOutpostPlacer} 的别名；
     * 成员集合真值仍只在 {@link SurfaceGate#landableTops(String)} 一处）。
     * <b>P16-B1 的调用面不变</b>：只有单 chunk 小机型过这条世界读臂（跨片巨构走
     * {@link PlacementGate#readyAtSpan(int)} 的纯臂），所以
     * {@code tools/dim1/SurfaceGateUnifyCheck} 的 {@code GATE_CALL_SITES}（本文件那一条 MACHINE 直调）
     * 与"全仓门族定义总数 == 5"两条读数都不需要改数——本类既没有再起一个一行委托别名，
     * 也没有自持任何方块集合。
     */
    private static boolean landableTopAt(World world, int x, int y, int z) {
        return SurfaceGate.isNaturalTop(DIM_KEY, SurfaceGate.landableTops(DIM_KEY), world.getBlock(x, y, z));
    }

    /**
     * 放置循环的唯一实现体。基座层（y=0 垫层）恒放置；其余层按损伤度掷缺失（缺失=不触碰地形，
     * 保留"残骸被岁月吞没"观感）。
     * <p>
     * <b>P7</b>：签名从"整台同一平面 {@code originY}"改为"逐列 {@code ground}"（与
     * {@link ProsperityOutpostPlacer#place} / {@code CityVariants#place} 同构），每列落地
     * {@code wy = ground.groundY(wx,wz) + 1 + y}；返回值从 {@code void} 改成<b>写入次数</b>
     * （调用方经 {@link PlacementGate.CountingSink} 折算真实落块数，供 {@code commit} 用）。
     * 随机消耗序列（损伤度 → 逐格双重掷缺失）一字未改。
     * <p>
     * <b>P16-B1（键解析改道，任务包 item 3）</b>：本方法不再自带 {@code resolveBlock} 那份
     * "只认 3 个 gtsr 键"的私有表——它把 {@link RuinedMachineShapes#blockKeyOf(char)} 或
     * （跨片机型）{@link RuinedColossusShapes#blockKeyOf(char)} 给出的 <b>String 逻辑键</b>直接写给
     * sink，由链上的 {@link CityBlockResolver} 解析（城/outpost/废墟族一直在用的那一张红线表）。
     * 记号表的<b>分流只在这一处</b>（{@link #keyOf}/{@link #metaOf}，谓词是
     * {@link RuinedColossusShapes#isColossus(String)}）：小机型的键集与改造前逐字相同 ⇒ 落块不变；
     * GT 壳只可能出现在跨片巨构的字符盘里。GT 缺席时解析落空 ⇒ {@code CityBlockResolver} 返回
     * false ⇒ 该格不落地（既有防御链，与 {@code 'X'/'Z'} 在城里的行为同形）。
     * <p>
     * ═══ <b>P16-B2（残骸形态：读哪份字符盘 + 埋多深 + 断了就不许悬着）</b> ═══
     * {@code morph == null}（5 个小机型与 S5 指令的母体蓝图）⇒ 本方法的逐格判定与改造前
     * <b>逐位相同</b>（同一损伤档、同一双重缺失掷、同一消费序）。非空时多三件事，全部只作用于
     * 跨片巨构这一支：
     * <ol>
     * <li><b>字符盘换成派生残骸档</b>（{@link RuinedColossusShapes.Wreck}）：机械件已被侵蚀成少数、
     * 逐列已按重力落定 ⇒ G9 的"本质像残骸"是渲染真值，不是注释。</li>
     * <li><b>半埋</b>：{@code y < bury} 的那几层由地形覆压，<b>一格都不写</b>（连清空空气都不写，
     * 所以绝不会在地表以下挖洞 ⇒ G8③"不穿出空洞"）；{@code wy = ground + 1 + (y - bury)}，
     * 全部落在该列地表顶 {@code ground + 1} 上 ⇒ 逐列接地同 P7 口径。
     * 垫层恒放的既有纪律随之下移到"第一层可见格"（{@code baseY}），于是"门过了却一块没落"
     * 依旧不可能。</li>
     * <li><b>同列截断</b>（G8②"悬浮块违反数 = 0"的世界读数面）：某列一旦被缺失掷骰啃掉一格
     * （或被解析链拒写，见 B1 边界⑤的玻璃），其上不再落任何块。之所以这条<b>能</b>跨 chunk 成立：
     * 承力方向是竖直的，而"同一 (x,z) 列"永远整个落在同一个 chunk 里，本 chunk 看得见该列的
     * 全部格子——横向悬挑则做不到这一点（邻槽各算一套就会造出两种读数），故横向承力在形态层
     * 已由 {@code RuinedColossusShapes} 的 {@code settle} 算子逐列落定，运行期不再依赖它。</li>
     * </ol>
     */
    private static int placeRange(StructureBuilder builder, Random r, RuinedMachineShapes.Shape shape, int originX,
        int originZ, int dxLo, int dxHi, int dzLo, int dzHi, CityVariants.GroundFn ground, int flags,
        RuinedColossusShapes.Morph morph, int burying) {
        final int damage = 20 + r.nextInt(76); // 损伤度 20-95（02 §6.3）
        final int missingChance = damage < 40 ? 10 : damage < 70 ? 25 : 45;
        final boolean colossus = RuinedColossusShapes.isColossus(shape.name);
        // P17 S-D：morph == null 时埋深来自群系档表（小机型；默认档恒 0 ⇒ 逐位退化回改造前）。
        // 两者都已在各自那条链上夹进"本形状验证过的最深档"之内，这里只做一次非负自卫。
        final int bury = Math.max(0, morph == null ? burying : morph.bury);
        final int baseY = bury; // "垫层恒放"下移到第一层可见格（morph == null && bury == 0 时即改造前的 0）
        // 同列截断状态位（只在巨构支分配；列宽 ≤16 ⇒ 一格一 bit，且该列必属本 chunk）
        final boolean[] broken = morph == null ? null : new boolean[(dxHi - dxLo + 1) * (dzHi - dzLo + 1)];
        int writes = 0;
        for (int y = 0; y < shape.sizeY; y++) {
            if (y < bury) {
                continue; // 半埋：这几层在地形以下，一格都不写（挖洞与切断地表的形态学入口就此封死）
            }
            for (int dx = dxLo; dx <= dxHi; dx++) {
                for (int dz = dzLo; dz <= dzHi; dz++) {
                    final char c = morph == null ? shape.charAt(y, dx, dz) : morph.wreck.charAt(y, dx, dz);
                    final int wx = originX + dx;
                    final int wz = originZ + dz;
                    if (c == ' ') {
                        continue; // 不触碰：地形让行
                    }
                    final int wy = ground.groundY(wx, wz) + 1 + (y - bury);
                    final int col = broken == null ? -1 : (dx - dxLo) * (dzHi - dzLo + 1) + (dz - dzLo);
                    if (broken != null && broken[col]) {
                        continue; // 该列已在下面断过：其上不再落块（否则就是悬浮件）
                    }
                    if (wy < 1 || wy > 255) {
                        if (broken != null) {
                            broken[col] = true;
                        }
                        continue; // 逐列接地后的越界自卫（同 outpost/城口径）
                    }
                    if (c == '.') {
                        // 清空（炉膛/烟道/塌口）：改写 String 空气键，与城/outpost/废墟族同一记号
                        writes += builder.setBlock(wx, wy, wz, CityVariants.K_AIR, 0, flags) ? 1 : 0;
                        continue;
                    }
                    if (y > baseY && r.nextInt(100) < missingChance && r.nextInt(100) < 40) {
                        if (broken != null) {
                            broken[col] = true;
                        }
                        continue; // 双重掷缺失，单部件缺失率上限 40%（02 §6.3）；垫层不缺失
                    }
                    final boolean ok = builder.setBlock(wx, wy, wz, keyOf(colossus, c), metaOf(colossus, c), flags);
                    if (broken != null && !ok) {
                        broken[col] = true; // 解析落空（GT 缺字段）⇒ 同样截断，不留悬在洞上的件
                    }
                    writes += ok ? 1 : 0;
                }
            }
        }
        return writes;
    }

    /**
     * 形状放置核心（世界生成的单 chunk 小机型与 {@code /gtsr structure} 共用）。
     * 基座层（y=0 垫层）恒放置；其余层按损伤度掷缺失（缺失=不触碰地形，保留"残骸被岁月吞没"观感）。
     * <p>
     * <b>P7</b>：签名从"整台同一平面 {@code originY}"改为"逐列 {@code ground}"（与
     * {@link ProsperityOutpostPlacer#place} / {@code CityVariants#place} 同构），每列落地
     * {@code wy = ground.groundY(wx,wz) + 1 + y}；返回值从 {@code void} 改成<b>写入次数</b>
     * （调用方经 {@link PlacementGate.CountingSink} 折算真实落块数，供 {@code commit} 用）。
     * 随机消耗序列（损伤度 → 逐格双重掷缺失）一字未改。
     */
    static int place(StructureBuilder builder, Random r, RuinedMachineShapes.Shape shape, int originX, int originZ,
        CityVariants.GroundFn ground, int flags) {
        return place(
            builder,
            r,
            shape,
            originX,
            originZ,
            ground,
            flags,
            // S5 指令与 5 个小机型：morph=null ⇒ 母体蓝图、埋深 0（B1 的读数一字不动）
            null);
    }

    /**
     * {@link #place} 的形态档入口（<b>P16-B2</b>，{@code tools/dim1} 的形态断言用）：
     * {@code morph == null} ⇒ 按<b>母体蓝图</b>整座渲染、埋深 0（S5 指令与 B1 的几何对账口径）。
     */
    static int place(StructureBuilder builder, Random r, RuinedMachineShapes.Shape shape, int originX, int originZ,
        CityVariants.GroundFn ground, int flags, RuinedColossusShapes.Morph morph) {
        return place(builder, r, shape, originX, originZ, ground, flags, morph, 0);
    }

    /**
     * {@link #place(StructureBuilder, Random, RuinedMachineShapes.Shape, int, int, CityVariants.GroundFn, int,
     * RuinedColossusShapes.Morph)} 的<b>群系埋深档</b>形态（<b>P17 S-D</b>）：多收一个已经由
     * {@link StructureBurialTiers#depthAt} 夹进"本形状验证过的最深档"之内的 {@code burying}。
     * <p>
     * 两个入口的分工：{@code morph != null}（跨片巨构）时埋深真值在 {@code morph.bury} 里，
     * 本参数被忽略（同一座残骸在两槽必须读到同一个埋深，不能让调用点再投一票）；
     * {@code morph == null}（5 个小机型与 S5 指令）时本参数才是埋深，且默认 {@code 0} ⇒
     * 既有工具与指令路径<b>逐位</b>退化回改造前。
     */
    static int place(StructureBuilder builder, Random r, RuinedMachineShapes.Shape shape, int originX, int originZ,
        CityVariants.GroundFn ground, int flags, RuinedColossusShapes.Morph morph, int burying) {
        return placeRange(
            builder,
            r,
            shape,
            originX,
            originZ,
            0,
            shape.sizeX - 1,
            0,
            shape.sizeZ - 1,
            ground,
            flags,
            morph,
            burying);
    }

    /**
     * 跨片巨构的"只画本 chunk 那一片"形态（<b>P16-B1</b>）：把循环范围按 chunk 栅格裁到
     * {@code [dxLo..dxHi]×[dzLo..dzHi]}，其余逐格逻辑与 {@link #place} <b>同一个实现体</b>
     * （{@link #placeRange}）。裁剪用 {@link ChunkSpans#localMin(int, int, int)}
     * /{@link ChunkSpans#localMax(int, int, int)}——与 {@link ChunkSliceSink} 的门过滤同源，
     * 于是"范围裁剪"和"切片器丢弃"永远不会给出两套互相打脸的几何。
     * <p>
     * 为什么要裁而不只是靠切片器吸收：不裁的话每个邻槽都要为整座（最多数千格）算一次
     * {@code heightAt} 再扔掉 8/9；裁之后本 chunk 只算自己那 1/4。裁剪不影响落块结果，
     * 只影响随机流的<b>消费位置</b>——而每个 chunk 只画自己那一片、且各片都由自己独立重放，
     * 所以"同 seed 同一 chunk 逐位可复现"仍然成立（跨片对账由
     * {@code tools/dim1/OutpostTemplateCheck} 的分片 raster 双跑 SHA 钉）。
     * <p>
     * 对外公开的唯一原因：{@code tools/dim1/OutpostTemplateCheck} 的成对断言与分片拼合对账要在默认包
     * 里按 chunk 复算同一份几何（{@link RuinedMachinePlacer} 自己不留第二套切分逻辑）。
     * 生产侧调用点只有 {@link #placeAll} 与 {@link #renderForeignSpans}。
     */
    public static int placeSlice(StructureBuilder builder, Random r, RuinedMachineShapes.Shape shape, int originX,
        int originZ, int chunkX, int chunkZ, CityVariants.GroundFn ground, int flags) {
        return placeSlice(builder, r, shape, originX, originZ, chunkX, chunkZ, ground, flags, null);
    }

    /**
     * {@link #placeSlice(StructureBuilder, Random, RuinedMachineShapes.Shape, int, int, int, int,
     * CityVariants.GroundFn, int)} 的<b>形态档形态</b>（<b>P16-B2</b>）：多收一个
     * {@link RuinedColossusShapes.Morph}，于是"这一座烂到哪一档、埋几层"与"本 chunk 画哪一片"
     * 在同一个实现体里落定；{@code morph == null} 时逐位退化成 B1 的行为（母体蓝图、埋深 0），
     * {@code tools/dim1/OutpostTemplateCheck} 的成对断言与分片拼合对账因此仍按 B1 的读数复算。
     */
    public static int placeSlice(StructureBuilder builder, Random r, RuinedMachineShapes.Shape shape, int originX,
        int originZ, int chunkX, int chunkZ, CityVariants.GroundFn ground, int flags,
        RuinedColossusShapes.Morph morph) {
        final int dxLo = ChunkSpans.localMin(originX, shape.sizeX, chunkX);
        final int dxHi = ChunkSpans.localMax(originX, shape.sizeX, chunkX);
        final int dzLo = ChunkSpans.localMin(originZ, shape.sizeZ, chunkZ);
        final int dzHi = ChunkSpans.localMax(originZ, shape.sizeZ, chunkZ);
        if (dxLo > dxHi || dzLo > dzHi) {
            return 0; // 本 chunk 与总 bbox 不相交（调用方的 intersects 已筛过，这里是自卫）
        }
        return placeRange(builder, r, shape, originX, originZ, dxLo, dxHi, dzLo, dzHi, ground, flags, morph, 0);
    }

    /**
     * {@link #placeSlice(StructureBuilder, Random, RuinedMachineShapes.Shape, int, int, int, int,
     * CityVariants.GroundFn, int, RuinedColossusShapes.Morph)} 的<b>群系埋深档</b>形态
     * （<b>P17 S-D</b>，{@code tools/dim1/P17StructureBiomeVarianceCheck} 按生产实现体复算四族埋深用）：
     * {@code morph != null} 时埋深真值仍在 {@code morph.bury}（同一座残骸在两槽必须同源），本参数被忽略；
     * {@code morph == null}（五个小机型）时本参数就是埋深，传 {@code 0} ⇒ 逐位等于改造前。
     */
    public static int placeSlice(StructureBuilder builder, Random r, RuinedMachineShapes.Shape shape, int originX,
        int originZ, int chunkX, int chunkZ, CityVariants.GroundFn ground, int flags, RuinedColossusShapes.Morph morph,
        int burying) {
        final int dxLo = ChunkSpans.localMin(originX, shape.sizeX, chunkX);
        final int dxHi = ChunkSpans.localMax(originX, shape.sizeX, chunkX);
        final int dzLo = ChunkSpans.localMin(originZ, shape.sizeZ, chunkZ);
        final int dzHi = ChunkSpans.localMax(originZ, shape.sizeZ, chunkZ);
        if (dxLo > dxHi || dzLo > dzHi) {
            return 0; // 本 chunk 与总 bbox 不相交（调用方的 intersects 已筛过，这里是自卫）
        }
        return placeRange(
            builder,
            r,
            shape,
            originX,
            originZ,
            dxLo,
            dxHi,
            dzLo,
            dzHi,
            ground,
            flags,
            morph,
            morph == null ? burying : 0);
    }

    /** 记号→键的单点分流（跨片机型走本族记号表，其余逐字委托既有表）。 */
    private static String keyOf(boolean colossus, char c) {
        return colossus ? RuinedColossusShapes.blockKeyOf(c) : RuinedMachineShapes.blockKeyOf(c);
    }

    /** 记号→meta 的单点分流（与 {@link #keyOf(boolean, char)} 成对，不允许两处各判一次）。 */
    private static int metaOf(boolean colossus, char c) {
        return colossus ? RuinedColossusShapes.metaOf(c) : RuinedMachineShapes.metaOf(c);
    }

    // P3（plan §5 P3 / 审计 A-5 §1 #3）：本类原有一份私有 findSurfaceY(World,int,int)，与
    // ProsperitySurfaceScatter #2 / ProsperityDecorPlacer #4 / ProsperityOutpostPlacer #5 /
    // ShatteredDecorPlacer #6 共 5 份实现体经 diff 实测逐字符等价，已并到框架唯一件
    // GTSRChunkProviderBase.findSurfaceY。<b>P7 兑现了当时登记的下一半</b>：本类不再引用它
    // （落点判定与接地都改走 heightAt 逐列，见上方 P7 段）——"两套接地并存"（审计 A-4）就此闭合。
    // 列扫实现体本身保留：散布层/装饰层是逐件单块落点，其列扫即"逐列"，不属于 A-4 的错位面。
}
