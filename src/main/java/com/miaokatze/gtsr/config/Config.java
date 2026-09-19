package com.miaokatze.gtsr.config;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

import com.miaokatze.gtsr.common.dimension.framework.GTSRBiomeBase;

/**
 * 模组配置管理类
 * 负责读取和保存模组的配置文件 (config/gtsr/gtsr.cfg)
 */
public class Config {

    // GregTech 元机器实体 (MTE) ID 分配的偏移量。
    // 由 MetaTileEntityID.java 统一管理（新段 BASE=14700）；v1.11.34 起旧 ID 段已退役，偏移量仅作用于现行 ID 分配。
    public static int metaIdOffset = 0;

    // 是否为 GTNL 蒸汽机基类启用 GTSR 增强（过热蒸汽 4 倍加速 + 冷却舱室支持）。
    // 默认关闭。开启后 GTNL 蒸汽机将获得 GTSR 的过热蒸汽 4 倍消耗 4 倍速加速机制，
    // 并能使用 GTSR 冷却舱室。mixin 方法体运行时判断此值（远晚于配置读取），时序安全。
    public static boolean gtnlEnhancement = false;

    // 自然生成奇点频率（区块×区块，平均 N×N 区块生成 1 个，默认 48 = 每 chunk 1/2304）。
    public static int singularitySpawnFrequency = 48;

    // 自然生成的奇点是否具有破坏方块的能力（默认开；关闭后自然奇点不吸收/破坏方块）。
    public static boolean singularityDestroyBlocks = true;

    // 破碎维度奇点频率倍增分母（破碎分支频率 = singularitySpawnFrequency/N，默认 8 = 等效 8 倍密度；S6b 消费）。
    public static int shatteredSingularityMultiplier = 8;

    // 繁荣维度（prosperity-ruins）维度 ID（默认 78）。
    // 与其他 mod 冲突时该维度自动禁用（解析记 -1），不回退到空闲 ID。
    public static int prosperityDimId = 78;

    // 繁荣维度 ProviderType ID（默认 178）。被占用时维度自动禁用（解析记 -1）。
    public static int prosperityProviderId = 178;

    // 破碎维度（shattered-lands）维度 ID（默认 79）。
    // 与其他 mod 冲突时该维度自动禁用（解析记 -1），不回退到空闲 ID。
    public static int shatteredDimId = 79;

    // 破碎维度 ProviderType ID（默认 179）。被占用时维度自动禁用（解析记 -1）。
    public static int shatteredProviderId = 179;

    // 繁荣维度群系 ID 段起始（默认 180 = 四名群系的**首选** id 起点；P1 起逐群系独立配槽，
    // 首选槽被外部 mod 占用时向后顺延，允许非连续结果，硬上界见 GTSRBiomeBase.HARD_ID_MAX）。
    public static int prosperityBiomeIdStart = 180;

    // 破碎维度群系 ID 段起始（默认 190，口径同 prosperityBiomeIdStart：首选起点 + 逐群系顺延）。
    public static int shatteredBiomeIdStart = 190;

    // 群系配槽扫描上界（P1 新增；默认 254 = byte 平面硬上界，见 GTSRBiomeBase.HARD_ID_MAX）。
    // 只能**收紧**不能放宽：实际扫描上界 = min(本值, HARD_ID_MAX, biomeList 实际表长 - 1)。
    // 用途——整合包若想给自家群系预留高位段，把它调小即可让我方群系提前判"无槽"并显式降级。
    public static int biomeIdScanLimit = 254;

    // 繁荣维度残缺机器生成频率分母（平均 1/N chunk × 群系机器权重；默认 16，0 = 禁用；S4a 消费，
    // S-A5 密度上调 24→16 = plan §12 修订 7）。
    // <b>P5 单一真值声明（plan §2.4 判据 4 / §3.1 更正 2）</b>：机器概率分母<b>只有本字段一处</b>真值，
    // 消费方（RuinedMachinePlacer.placeAll 的 chance、编排器的注册日志 "machineChance=1/{}"）一律
    // 引用本字段，不得再在任何常量/注释里复写具体分母数字。设计基线 = 代码默认 16；
    // 实机若显示别的分母（如整合包 run/server/config/gtsr/gtsr.cfg 的覆盖值），那是外部覆盖，不改代码默认。
    public static int prosperityMachineChance = 16;

    // 繁荣维度城外中型废墟（outpost）生成频率分母（平均 1/N chunk 生成 1 座；默认 64，0 = 禁用；
    // S-A5 新增消费 = plan §12 修订 7/8，与残缺机器同 chunk 互斥掷骰：先 outpost，命中跳过机器）。
    public static int prosperityOutpostChance = 64;

    // 繁荣维度古代城存在概率（每个 24×24 chunk cell 的存在掷骰百分比；默认 45，0 = 无城，100 = 全 cell 有城；S4b 消费）。
    public static int prosperityCityChance = 45;

    // ═════════ P6 群系带分层与城门（plan §2.2 H-1/L6 / §5 P6 / §7.1 已锁定 U2）═════════
    //
    // H-1 身份层的尺度：dim78 macro 群系带 64 chunk（城盘 9-15 chunk 才可能整座落进同一带），
    // micro cell 恒 16（BiomeZoneSelector.MICRO_CELL_CHUNKS，只作用变体/装饰强度，不参与身份）。
    // 两键都必须是 16 的正整数倍；非整数倍由 BiomeZoneSelector.normalizeMacroCell 向下取整，
    // 非正值/小于 16 回退 16（= 改造前单层行为，plan §2.3 判据 5 的单值回退位）。
    // dim79 的键默认 16 ⇒ 其身份与地表逐位不变（本片对 dim79 内容零改动）。

    // dim78 macro 群系带尺度（chunk；默认 64 = U2 锁定档；16 = 回退到改造前单层分区）。
    public static int prosperityBiomeMacroBandChunks = 64;

    // dim79 macro 群系带尺度（chunk；默认 16 = 与改造前逐位相同，非本片刻意调整项）。
    public static int shatteredBiomeMacroBandChunks = 16;

    // L6 城门条件档（古代城只允许出现在锈蚀草原带；plan §2.1 L6 + §7.1 U2「门=锚点带」）：
    // 0 = 关（改造前行为：城与群系无关，四带均可出现）
    // 1 = 城盘锚点（中心 chunk）所在 macro 带为锈蚀草原 —— 默认档，U2 锁定
    // 2 = 城盘（边长 2r+1 的方形盘，r=4..7）≥50% chunk 落在锈蚀草原带
    // 3 = 城盘 100% 落在锈蚀草原带（最严，城市数会进一步塌缩，仅供调参）
    // 判定是纯函数（走 BiomeZoneSelector 带出口，禁止 world.getBiomeGenForCoords——城中心最远跨
    // 8 chunk 会触发邻 chunk 生成），且<b>只在 CityPlanner.citiesNear 内生效</b> ⇒ 渲染与
    // "cities.length>0 抑制散布/机器" 天然同一入口（plan §2.1 L6 禁止"鬼窗"）。
    public static int prosperityCityBiomeGate = 1;

    // ═════════════════ P5 密度 γ（plan §2.2 H-2/H-3 / §5 P5 / §7.1 已锁定 U3「中道 K + 摘竖向件」）═════════════════
    //
    // 受控量从"每 chunk 落多少<b>块</b>"改成"每 chunk 放多少<b>件（轮廓）</b>"：
    // 件数上限 K 是主口径（H-3），落块上限是配套的第二道天花板，尝试上限只作随机流的终止/兼容口径。
    // 改前 BUDGET_PER_CHUNK=64 与权重表 {30,25,30,15} 都是 ProsperitySurfaceScatter 的
    // private static final（不可回退）——P5 起全部上收到这里，plan §2.3 判据 5 才成立。
    //
    // 【回到改造前的"柱阵态"】以下四键一起设：
    // prosperityScatterVerticalPieces = true （把 15% 竖向件放回权重表）
    // prosperityScatterContoursPerChunk = 64 （件数天花板抬到不低于尝试上限 ⇒ 实际不约束）
    // prosperityScatterBlocksPerChunk = 0 （关闭落块天花板）
    // prosperityScatterWindowRepeatCap = 0 （关闭 H-2 窗重复上限）
    // 尝试上限与权重保持默认（64 / 30-25-30-15）即逐位复现改造前行为
    // （由 tools/dim1/Dim78ScatterDensityCheck 的 digest 对拍钉住，见 plan/investigation/p5-*）。

    // 散布每 chunk 尝试上限（改造前 ProsperitySurfaceScatter.BUDGET_PER_CHUNK=64 的 Config 化；
    // 仍是"掷点次数"口径，× 群系散布权重后为实际尝试数；不是受控量本身。0 = 整个散布层不再掷点）。
    public static int prosperityScatterAttemptsPerChunk = 64;

    // H-3 每 chunk 轮廓/件数 K（基准档 = 齿轮森林权重 1.0，其余群系按 02 §1.1 散布权重折算
    // round(K×w)；荒漠 1.5 故硬上界 = round(K×1.5)）。默认 8 = P5 参数扫描选定的中道档
    // （改造前那档：P4 权威样本 100.58 块/chunk、P5 复算 100.63 块/chunk、烟囱 9.40 柱/chunk）。
    // 0 = 关闭件数上限（此时密度只由上面的掷点上限决定 ⇒ 回到改造前口径）。
    public static int prosperityScatterContoursPerChunk = 8;

    // H-3 每 chunk 散布落块上限（件数之外的第二道天花板，防长管段叠字；round(N×w) 口径同上，
    // 单件可越界至多 4 块）。默认 24。0 = 关闭落块上限。
    public static int prosperityScatterBlocksPerChunk = 24;

    // H-3 竖向件（烟囱残段 2-5 格高柱）开关。默认 <b>false = 从散布源头摘出</b>——
    // S2 柱阵的主导项就是散布层的 15% 竖向件（实测 8.8-10.2 柱/chunk）。
    // 注意：摘出只针对<b>散布</b>，烟囱素材不作废——城内 chimney_stack / 残骸机器 chimney_base
    // 仍走 CityVariants / RuinedMachineShapes 的 'c' 键与形状盘，不经本开关。
    public static boolean prosperityScatterVerticalPieces = false;

    // H-2 每 16×16 chunk 窗内"同一模板"的发射 chunk 数上限（plan §2.2 H-2；plan §7.1 U3「柱 ≤2/窗」）。
    // 默认 2；0 = 关闭。机制是<b>纯函数</b>的窗内槽位哈希排序（ProsperitySurfaceScatter.windowAllows），
    // 跨 chunk 无需共享状态且结果一致。本片刻度只挂竖向件（平面件型的窗上限与结构模板重复上限
    // 同属 H-2 的主对象，归 P7 PlacementGate）。
    public static int prosperityScatterWindowRepeatCap = 2;

    // 散布件型权重（02 §3.1：轨枕 30 / 管道 25 / 铆接板 30 / 烟囱残段 15）。
    // 权重表按 prosperityScatterVerticalPieces 决定是否含烟囱残段项；三项平面权重保持 02 §3.1 的
    // 相对比例（摘竖向件后归一化到 85，不改相对设计）。全部为 0 ⇒ 散布层不放任何件。
    public static int prosperityScatterWeightSleeper = 30;

    public static int prosperityScatterWeightPipe = 25;

    public static int prosperityScatterWeightRivetPlate = 30;

    // 竖向件权重（仅 prosperityScatterVerticalPieces=true 时参与掷选）。
    public static int prosperityScatterWeightChimney = 15;

    // ═════════════ P7/P7c 结构放置契约（plan §5 P7 / §2.2 H-2·H-3 / §2.1 L5 / §7.2 改判）═════════════
    //
    // 结构族（残缺机器 / 城外中型废墟 / P8 起的废墟族）的预算、窗重复上限与同族间距，唯一入口是
    // framework/structure/PlacementGate——本段三键是它读取的<b>唯一数字出处</b>：placer 侧与
    // PlacementGate 侧都不再写任何预算/上限/间距字面量（plan §2.4 判据 4）。
    // StructureRegistry.Entry 的 windowRepeatCap / placementDenominator 允许"逐模板覆写"，
    // 但本片两条既有族一律传 0 = 跟随这里 ⇒ 树内仍只有一处数字。
    //
    // 【P7c 改判（plan §7.2）】P7 把"每窗同模板重复上限"实现成了散布侧那种<b>排名配额</b>，对 1/64、
    // 1/16 的稀疏结构事件等于密度乘子（实测 cap=2 ⇒ 城外结构 984 → 12 座，−98.8%），那不是"上限"。
    // 现语义 = <b>以实际命中集为条件</b>：某窗内某个模板已被请求 N 次，只有第 N+1 次请求在
    // N ≥ cap 时才被拒 ⇒ <b>首次出现永不因 cap 被拒</b>，cap 只削重复副本。贴脸（同族相邻 chunk）
    // 由下面的 prosperityStructureFamilyGapChunks 负责，不由配额负责。
    // 用户口径同时锁定：<b>城外 outpost/机器的 1/N 概率一律不动</b>（目标"每 16×16 窗 16 座"≈ 现状
    // 15.4 座/窗，由 machineChance=1/24 与 outpostChance=1/64 算术得出），故本段三键都不碰概率。
    //
    // 【回退位】prosperityStructureWindowRepeatCap = 0 ⇒ 窗重复上限关闭；
    // prosperityStructureFamilyGapChunks = 0 ⇒ 同族间距关闭 ⇒ 两键同 0 即回到"只受 1/N 独立掷骰 +
    // 每 chunk 预算约束"的状态（实测复现改造前 23.882% 贴脸率，见
    // plan/investigation/p7b-placement-contract-20260919.md 的 T4 与 p7c 证据文档的 cap 扫描表）。
    // prosperityStructureBudgetPerChunk = 0 ⇒ 关闭每 chunk 结构预算（同 chunk 可叠多座，
    // 改造前的跨 chunk 贴脸之上再叠同 chunk 贴脸，只作调参观察用）。

    // H-3 每 chunk 允许<b>真实落块</b>的结构座数上限（默认 1 = 与改造前"outpost 命中则跳过机器"的
    // 互斥掷骰等值，但改造前那条只在 outpost 谎报成功时才生效；0 = 不限）。
    public static int prosperityStructureBudgetPerChunk = 1;

    // H-2① 每 16×16 chunk 窗内"同一结构模板"允许<b>实际请求</b>的 chunk 数上限（默认 3 = 命中集条件
    // 下几乎不咬合的松档：城外每窗每模板的命中数中位 1、实测最大 8 ⇒ 3 只削最密的重复，
    // 结构总数仍回到 ~984 量级；0 = 关闭。判定 = PlacementGate.windowRepeatAllows，
    // 与散布侧的排名配额是两套各自唯一的实现，见 PlacementGate 类注释第 4 点）。
    public static int prosperityStructureWindowRepeatCap = 3;

    // H-2② 同族结构的最小间距档（默认 1 = 8 邻 chunk 内已有同族命中则本座让行；0 = 关闭）。
    // 档位 g ⇒ 邻域 (2g+1)²−1 个 chunk（g=1→8、g=2→24），上界由 PlacementGate.SPACING_GAP_MAX 钳制。
    // 这是"贴脸率"的受控量（plan §2.2 H-2 的第二列），也是 23.882% 基线的唯一治疗手段。
    public static int prosperityStructureFamilyGapChunks = 1;

    /**
     * planDimension 总开关持有者（plan 维度组键 planDimension.*，见 synchronizeConfiguration）。
     * 关闭后对应维度完全不注册（DimensionRegistrar 冲突检测第一道闸）。
     */
    public static final PlanDimensionHolder planDimension = new PlanDimensionHolder();

    public static final class PlanDimensionHolder {

        // 繁荣蒸汽时代遗迹维度总开关。默认开。关闭后维度不注册，相关生成与指令一并禁用。
        public boolean prosperityDimension = true;

        // 维度破碎之地维度总开关。默认开。关闭后维度不注册，相关生成与指令一并禁用。
        public boolean shatteredDimension = true;
    }

    /**
     * 同步配置文件
     * 从磁盘读取配置并更新静态变量，如果配置有变动则自动保存
     * 
     * @param configFile 配置文件对象
     */
    public static void synchronizeConfiguration(File configFile) {
        Configuration configuration = new Configuration(configFile);

        metaIdOffset = configuration.getInt(
            "metaIdOffset",
            Configuration.CATEGORY_GENERAL,
            metaIdOffset,
            -5000,
            5000,
            "应用于 MTE ID 基准值的偏移量 (用于预留 ID 区间)");

        gtnlEnhancement = configuration.getBoolean(
            "gtnlEnhancement",
            Configuration.CATEGORY_GENERAL,
            false,
            "是否为GTNL蒸汽机基类启用GTSR增强（过热蒸汽4倍加速+冷却舱室支持）。默认关闭。开启后GTNL蒸汽机将获得GTSR的过热蒸汽4倍消耗4倍速加速机制，并能使用GTSR冷却舱室。");

        singularitySpawnFrequency = configuration.getInt(
            "singularitySpawnFrequency",
            Configuration.CATEGORY_GENERAL,
            singularitySpawnFrequency,
            1,
            10000,
            "自然生成奇点频率（区块×区块，平均 N×N 区块生成 1 个，默认 48 = 每 chunk 1/2304）");

        singularityDestroyBlocks = configuration.getBoolean(
            "singularityDestroyBlocks",
            Configuration.CATEGORY_GENERAL,
            true,
            "自然生成的奇点是否具有破坏方块的能力（默认开；关闭后自然奇点不吸收/破坏方块）");

        shatteredSingularityMultiplier = configuration.getInt(
            "shatteredSingularityMultiplier",
            Configuration.CATEGORY_GENERAL,
            shatteredSingularityMultiplier,
            1,
            64,
            "破碎维度奇点频率倍增分母（破碎分支频率 = singularitySpawnFrequency/N，默认 8 = 等效 8 倍密度；范围 1-64）");

        planDimension.prosperityDimension = configuration.getBoolean(
            "planDimension.prosperityDimension",
            Configuration.CATEGORY_GENERAL,
            true,
            "繁荣蒸汽时代遗迹维度（prosperity-ruins）总开关。默认开。关闭后维度不注册，相关生成与指令一并禁用。");

        planDimension.shatteredDimension = configuration.getBoolean(
            "planDimension.shatteredDimension",
            Configuration.CATEGORY_GENERAL,
            true,
            "维度破碎之地（shattered-lands）总开关。默认开。关闭后维度不注册，相关生成与指令一并禁用。");

        prosperityDimId = configuration.getInt(
            "prosperityDimId",
            Configuration.CATEGORY_GENERAL,
            prosperityDimId,
            0,
            1000,
            "繁荣维度 ID（默认 78）。与其他 mod 冲突时维度自动禁用（记 -1），不回退到空闲 ID");

        prosperityProviderId = configuration.getInt(
            "prosperityProviderId",
            Configuration.CATEGORY_GENERAL,
            prosperityProviderId,
            0,
            1000,
            "繁荣维度 ProviderType ID（默认 178）。被占用时维度自动禁用（记 -1）");

        shatteredDimId = configuration.getInt(
            "shatteredDimId",
            Configuration.CATEGORY_GENERAL,
            shatteredDimId,
            0,
            1000,
            "破碎维度 ID（默认 79）。与其他 mod 冲突时维度自动禁用（记 -1），不回退到空闲 ID");

        shatteredProviderId = configuration.getInt(
            "shatteredProviderId",
            Configuration.CATEGORY_GENERAL,
            shatteredProviderId,
            0,
            1000,
            "破碎维度 ProviderType ID（默认 179）。被占用时维度自动禁用（记 -1）");

        prosperityBiomeIdStart = configuration.getInt(
            "prosperityBiomeIdStart",
            Configuration.CATEGORY_GENERAL,
            prosperityBiomeIdStart,
            0,
            GTSRBiomeBase.HARD_ID_MAX,
            "繁荣维度群系 ID 首选起点（默认 180）。P1 起逐群系独立配槽：首选槽被占则向后顺延，允许非连续；" + "可用上界 "
                + GTSRBiomeBase.HARD_ID_MAX
                + "（byte 平面 + 255 懒回填哨兵，见 GTSRBiomeBase.HARD_ID_MAX）");

        shatteredBiomeIdStart = configuration.getInt(
            "shatteredBiomeIdStart",
            Configuration.CATEGORY_GENERAL,
            shatteredBiomeIdStart,
            0,
            GTSRBiomeBase.HARD_ID_MAX,
            "破碎维度群系 ID 首选起点（默认 190）。配槽口径同 prosperityBiomeIdStart（首选 + 顺延，允许非连续）；" + "可用上界 "
                + GTSRBiomeBase.HARD_ID_MAX);

        biomeIdScanLimit = configuration.getInt(
            "biomeIdScanLimit",
            Configuration.CATEGORY_GENERAL,
            biomeIdScanLimit,
            1,
            GTSRBiomeBase.HARD_ID_MAX,
            "群系配槽扫描上界（默认 " + GTSRBiomeBase.HARD_ID_MAX
                + " = byte 平面硬上界，只能收紧不能放宽）。整合包要预留高位段时调小它，"
                + "我方群系会提前判无槽并在日志显式降级 degraded=SHORT/EMPTY");

        prosperityMachineChance = configuration.getInt(
            "prosperityMachineChance",
            Configuration.CATEGORY_GENERAL,
            prosperityMachineChance,
            0,
            1000,
            "繁荣维度残缺机器生成频率分母（平均 1/N 区块 × 群系机器权重，默认 16 = 约 4-8% 每区块；0 = 禁用）");

        prosperityOutpostChance = configuration.getInt(
            "prosperityOutpostChance",
            Configuration.CATEGORY_GENERAL,
            prosperityOutpostChance,
            0,
            1000,
            "繁荣维度城外中型废墟（outpost）生成频率分母（平均 1/N 区块生成 1 座，默认 64；0 = 禁用；与残缺机器同区块互斥：outpost 命中则跳过机器）");

        prosperityCityChance = configuration.getInt(
            "prosperityCityChance",
            Configuration.CATEGORY_GENERAL,
            prosperityCityChance,
            0,
            100,
            "繁荣维度古代城存在概率（每个 24×24 区块 cell 的存在掷骰百分比，默认 45 = 平均每 500-600 格一座；0 = 无城）");

        // ═════ P6 群系带分层与城门（H-1/L6）——键名/默认值/注释与本类字段声明严格一致 ═════
        prosperityBiomeMacroBandChunks = configuration.getInt(
            "prosperityBiomeMacroBandChunks",
            Configuration.CATEGORY_GENERAL,
            prosperityBiomeMacroBandChunks,
            16,
            1024,
            "dim78 macro 群系带尺度（区块，默认 64 = U2 锁定档；须为 micro cell 16 的正整数倍，" + "非整数倍向下取整。16 = 回退改造前单层分区，群系身份逐位不变）");

        shatteredBiomeMacroBandChunks = configuration.getInt(
            "shatteredBiomeMacroBandChunks",
            Configuration.CATEGORY_GENERAL,
            shatteredBiomeMacroBandChunks,
            16,
            1024,
            "dim79 macro 群系带尺度（区块，默认 16 = 与改造前逐位相同；口径同上，仅整合包可调）");

        prosperityCityBiomeGate = configuration.getInt(
            "prosperityCityBiomeGate",
            Configuration.CATEGORY_GENERAL,
            prosperityCityBiomeGate,
            0,
            3,
            "古代城群系门条件档（默认 1 = 城盘锚点所在 macro 带须为锈蚀草原；0 = 关城门（改造前行为）"
                + "；2 = 城盘≥50% 落草原带；3 = 城盘全落草原带。判定与城渲染同走 CityPlanner.citiesNear 单一入口）");

        // ═════════ P5 密度 γ（H-2/H-3）——键名/默认值/注释与本类字段声明严格一致（plan §2.1 横切 roster
        // 「禁止同一数值在 Config/常量/注释三处漂移」）═════════
        prosperityScatterAttemptsPerChunk = configuration.getInt(
            "prosperityScatterAttemptsPerChunk",
            Configuration.CATEGORY_GENERAL,
            prosperityScatterAttemptsPerChunk,
            0,
            1024,
            "繁荣维度地表散布每 chunk 尝试上限（默认 64 = 改造前 BUDGET_PER_CHUNK 的原值；× 群系散布权重后为实际掷点次数。"
                + "受控量是件数 prosperityScatterContoursPerChunk，本键只作掷点终止/兼容口径）");

        prosperityScatterContoursPerChunk = configuration.getInt(
            "prosperityScatterContoursPerChunk",
            Configuration.CATEGORY_GENERAL,
            prosperityScatterContoursPerChunk,
            0,
            1024,
            "H-3 每 chunk 散布轮廓/件数上限 K（默认 8 = P5 参数扫描选定的中道档；基准档为齿轮森林权重 1.0，"
                + "其余群系按 02 §1.1 散布权重折算 round(K×w)，荒漠 1.5 ⇒ 实际硬上界 round(K×1.5)。0 = 关闭件数上限。"
                + "回到改造前柱阵态需连同竖向件/落块/窗上限三键一起设，见本类字段注释）");

        prosperityScatterBlocksPerChunk = configuration.getInt(
            "prosperityScatterBlocksPerChunk",
            Configuration.CATEGORY_GENERAL,
            prosperityScatterBlocksPerChunk,
            0,
            4096,
            "H-3 每 chunk 散布落块上限（默认 24 = 件数之外的第二道天花板，防长管段叠字；折算口径同件数，" + "单件最多越界 4 块。0 = 关闭落块上限）");

        prosperityScatterVerticalPieces = configuration.getBoolean(
            "prosperityScatterVerticalPieces",
            Configuration.CATEGORY_GENERAL,
            prosperityScatterVerticalPieces,
            "H-3 竖向件（烟囱残段 2-5 格高柱）开关。默认 false = 从散布源头摘出（S2 柱阵主导项，实测 8.8-10.2 柱/chunk）。"
                + "摘出只作用于散布层：城内 chimney_stack、残骸机器 chimney_base 仍走 'c' 键与形状盘，素材不作废。");

        prosperityScatterWindowRepeatCap = configuration.getInt(
            "prosperityScatterWindowRepeatCap",
            Configuration.CATEGORY_GENERAL,
            prosperityScatterWindowRepeatCap,
            0,
            256,
            "H-2 每 16×16 chunk 窗内同一模板允许发射的 chunk 数上限（默认 2 = plan §7.1 U3「柱 ≤2/窗」；0 = 关闭）。"
                + "纯函数窗内槽位哈希排序，跨 chunk 一致无需共享状态；本片刻度只挂竖向件，结构模板窗上限归 P7");

        prosperityScatterWeightSleeper = configuration.getInt(
            "prosperityScatterWeightSleeper",
            Configuration.CATEGORY_GENERAL,
            prosperityScatterWeightSleeper,
            0,
            1000,
            "散布件型权重·轨枕（02 §3.1 原值 30；与管道/铆接板保持相对比例，竖向件摘出后归一化到剩余三项之和）");

        prosperityScatterWeightPipe = configuration.getInt(
            "prosperityScatterWeightPipe",
            Configuration.CATEGORY_GENERAL,
            prosperityScatterWeightPipe,
            0,
            1000,
            "散布件型权重·废弃管道段（02 §3.1 原值 25）");

        prosperityScatterWeightRivetPlate = configuration.getInt(
            "prosperityScatterWeightRivetPlate",
            Configuration.CATEGORY_GENERAL,
            prosperityScatterWeightRivetPlate,
            0,
            1000,
            "散布件型权重·铆接板（02 §3.1 原值 30）");

        prosperityScatterWeightChimney = configuration.getInt(
            "prosperityScatterWeightChimney",
            Configuration.CATEGORY_GENERAL,
            prosperityScatterWeightChimney,
            0,
            1000,
            "散布件型权重·烟囱残段（02 §3.1 原值 15；仅 prosperityScatterVerticalPieces=true 时参与掷选）");

        // ═════ P7 结构放置契约（H-2/H-3 结构侧）——键名/默认值/注释与本类字段声明严格一致 ═════
        prosperityStructureBudgetPerChunk = configuration.getInt(
            "prosperityStructureBudgetPerChunk",
            Configuration.CATEGORY_GENERAL,
            prosperityStructureBudgetPerChunk,
            0,
            16,
            "H-3 每区块允许真实落块的结构座数上限（默认 1 = 城外废墟与残缺机器互斥的唯一真值口径；"
                + "0 = 不限，仅供调参观察。判定入口 PlacementGate.ChunkGate.request，预算只在真实落块后扣减）");

        prosperityStructureWindowRepeatCap = configuration.getInt(
            "prosperityStructureWindowRepeatCap",
            Configuration.CATEGORY_GENERAL,
            prosperityStructureWindowRepeatCap,
            0,
            256,
            "H-2① 每 16×16 区块窗内同一结构模板允许<b>实际请求</b>的区块数上限（默认 3；0 = 关闭）。" + "语义是命中集条件的重复上限：首次出现永不因本键被拒，只削第 cap+1 个副本"
                + "（P7c 改判，plan §7.2；旧实现是排名配额 ⇒ 密度乘子，已作废）。"
                + "纯函数重放，跨区块一致、无需共享状态；与 P5 竖向件的排名配额是两套各自唯一的实现");

        prosperityStructureFamilyGapChunks = configuration.getInt(
            "prosperityStructureFamilyGapChunks",
            Configuration.CATEGORY_GENERAL,
            prosperityStructureFamilyGapChunks,
            0,
            4,
            "H-2② 同族结构最小间距档（默认 1 = 8 邻区块内已有同族命中则本座让行；0 = 关闭）。" + "档位 g 的邻域为 (2g+1)²−1 个区块（1→8、2→24、3→48、4→80）；贴脸率由本键负责，"
                + "不由窗重复上限负责（P7c，plan §7.2「只治贴脸、概率不动」）");

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }
}
