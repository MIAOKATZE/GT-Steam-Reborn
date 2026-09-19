package com.miaokatze.gtsr.common.dimension.prosperity.ruins;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.blocks.BlocksGTSR;
import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeAuthority;
import com.miaokatze.gtsr.common.dimension.framework.SurfaceGate;
import com.miaokatze.gtsr.common.dimension.framework.structure.BlockSink;
import com.miaokatze.gtsr.common.dimension.framework.structure.GTSRWorldgenHash;
import com.miaokatze.gtsr.common.dimension.framework.structure.PlacementGate;
import com.miaokatze.gtsr.common.dimension.framework.structure.StructureBuilder;
import com.miaokatze.gtsr.common.dimension.framework.structure.StructureRegistry;
import com.miaokatze.gtsr.common.dimension.prosperity.ProsperityTerrainProfile;
import com.miaokatze.gtsr.common.dimension.prosperity.ruins.city.CityVariants;
import com.miaokatze.gtsr.config.Config;

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

    /** 方块键 → Block 解析表（游戏内放置路径专用；延迟初始化避免离线驱动触碰）。 */
    private static Map<String, Block> blockResolver;

    private RuinedMachinePlacer() {}

    /**
     * 向 {@link StructureRegistry} 登记 5 机型变体（dimension=PROSPERITY，幂等）。
     * 由 ProsperityWorldGenerator 构造时调用（CommonProxy.init 注册线），S5 /gtsr structure
     * 补全同源消费。placer 回调走 DirectWorldSink 语义 flag。
     * <p>
     * P7 起的 roster 形状（{@link StructureRegistry.Entry} 注释里有选型理由）：族 =
     * {@link PlacementGate#FAMILY_MACHINE}；放置分母与窗上限一律传 0 = 跟随 Config 族键
     * （本类的分母真值仍只有 {@link Config#prosperityMachineChance} 一处）；
     * {@code allowsDamagedVariant=false}——机型本身就是残骸，P8 的损毁算子不再叠加。
     */
    public static void registerVariants() {
        if (registered) {
            return;
        }
        registered = true;
        for (final RuinedMachineShapes.Shape shape : RuinedMachineShapes.ALL) {
            StructureRegistry.register(
                new StructureRegistry.Entry(
                    shape.name,
                    StructureRegistry.Dimension.PROSPERITY,
                    shape.sizeX,
                    shape.sizeZ,
                    (sink, x, y, z, seed) -> place(
                        new StructureBuilder(sink),
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
        final Roll roll = roll(worldSeed, cx, cz, biomeMachineWeight);
        if (roll == null) {
            return false; // 禁用位 / 掷骰未中 / footprint 超出单 chunk
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
        if (!PlacementGate.readyAt(groundY, landableTopAt(world, centerX, groundY, centerZ))) {
            permit.abort(); // 未落块：显式归还，预算不扣
            return false;
        }
        final PlacementGate.CountingSink counter = PlacementGate.counting(sink);
        place(new StructureBuilder(counter), roll.rnd, roll.shape, x, z, ground, BlockSink.FLAG_POPULATE);
        return permit.commit(counter.solid());
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

        Roll(PlacementGate.Intent intent, Random rnd, RuinedMachineShapes.Shape shape) {
            this.intent = intent;
            this.rnd = rnd;
            this.shape = shape;
        }
    }

    /**
     * 掷骰的<b>唯一</b>实现体（P7c）：频率 = 1/{@link Config#prosperityMachineChance} × 群系机器权重，
     * 机型在 {@link RuinedMachineShapes#ALL} 中均匀掷选，origin 由 footprint 收缩钳制后的余量掷出。
     * 顺序与 P7b 逐位一致（{@code nextDouble → nextInt(机型) → nextInt(freeX+1) → nextInt(freeZ+1)}），
     * 只是从 {@code placeAll} 的方法体里原样搬进来 ⇒ 落点零漂移。
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
        final RuinedMachineShapes.Shape shape = RuinedMachineShapes.ALL[r.nextInt(RuinedMachineShapes.ALL.length)];
        // footprint 收缩钳制：origin 使整机（含垫层）完全落在当前 chunk（>16 格形状防御性跳过）
        final int freeX = 16 - shape.sizeX;
        final int freeZ = 16 - shape.sizeZ;
        if (freeX < 0 || freeZ < 0) {
            return null;
        }
        final int x = (cx << 4) + r.nextInt(freeX + 1);
        final int z = (cz << 4) + r.nextInt(freeZ + 1);
        return new Roll(new PlacementGate.Intent(shape.name, x, z, shape.sizeX, shape.sizeZ), r, shape);
    }

    /**
     * 落点地表门（P7 改道：直调框架单一谓词，不再横向借 {@code ProsperityOutpostPlacer} 的别名；
     * 成员集合真值仍只在 {@link SurfaceGate#landableTops(String)} 一处）。
     */
    private static boolean landableTopAt(World world, int x, int y, int z) {
        return SurfaceGate.isNaturalTop(DIM_KEY, SurfaceGate.landableTops(DIM_KEY), world.getBlock(x, y, z));
    }

    /**
     * 形状放置核心（世界生成与 /gtsr structure 共用）。
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
        final int damage = 20 + r.nextInt(76); // 损伤度 20-95（02 §6.3）
        final int missingChance = damage < 40 ? 10 : damage < 70 ? 25 : 45;
        int writes = 0;
        for (int y = 0; y < shape.sizeY; y++) {
            for (int dx = 0; dx < shape.sizeX; dx++) {
                for (int dz = 0; dz < shape.sizeZ; dz++) {
                    final char c = shape.charAt(y, dx, dz);
                    final int wx = originX + dx;
                    final int wz = originZ + dz;
                    if (c == ' ') {
                        continue; // 不触碰：地形让行
                    }
                    final int wy = ground.groundY(wx, wz) + 1 + y;
                    if (wy < 1 || wy > 255) {
                        continue; // 逐列接地后的越界自卫（同 outpost/城口径）
                    }
                    if (c == '.') {
                        writes += builder.setBlock(wx, wy, wz, Blocks.air, 0, flags) ? 1 : 0; // 清空（炉膛/烟道/塌口）
                        continue;
                    }
                    if (y > 0 && r.nextInt(100) < missingChance && r.nextInt(100) < 40) {
                        continue; // 双重掷缺失，单部件缺失率上限 40%（02 §6.3）；垫层不缺失
                    }
                    final Block block = resolveBlock(RuinedMachineShapes.blockKeyOf(c));
                    if (block == null) {
                        continue;
                    }
                    writes += builder.setBlock(wx, wy, wz, block, RuinedMachineShapes.metaOf(c), flags) ? 1 : 0;
                }
            }
        }
        return writes;
    }

    // P3（plan §5 P3 / 审计 A-5 §1 #3）：本类原有一份私有 findSurfaceY(World,int,int)，与
    // ProsperitySurfaceScatter #2 / ProsperityDecorPlacer #4 / ProsperityOutpostPlacer #5 /
    // ShatteredDecorPlacer #6 共 5 份实现体经 diff 实测逐字符等价，已并到框架唯一件
    // GTSRChunkProviderBase.findSurfaceY。<b>P7 兑现了当时登记的下一半</b>：本类不再引用它
    // （落点判定与接地都改走 heightAt 逐列，见上方 P7 段）——"两套接地并存"（审计 A-4）就此闭合。
    // 列扫实现体本身保留：散布层/装饰层是逐件单块落点，其列扫即"逐列"，不属于 A-4 的错位面。

    /** 方块键解析（含未注册防御：总开关关闭等场景方块可能缺失，跳过该部件不炸生成链）。 */
    private static Block resolveBlock(String key) {
        if (blockResolver == null) {
            final Map<String, Block> resolver = new HashMap<>();
            resolver.put("gtsr:RuinedCasing", BlocksGTSR.ruinedCasing);
            resolver.put("gtsr:RuinDebris", BlocksGTSR.ruinDebris);
            resolver.put("gtsr:ProsperitySurface", BlocksGTSR.prosperitySurface);
            blockResolver = resolver;
        }
        return blockResolver.get(key);
    }
}
