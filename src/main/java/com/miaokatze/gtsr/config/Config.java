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

    // 繁荣维度残缺机器生成频率分母（平均 1/N chunk × 群系机器权重；0 = 禁用；S4a 消费；
    // 沿革：S-A5 上调过一次，P16 按用户裁决把概率降到"当时实机生效值"的三分之一）。
    // <b>P5 单一真值声明（plan §2.4 判据 4 / §3.1 更正 2）</b>：机器概率分母<b>只有本字段一处</b>真值，
    // 消费方（RuinedMachinePlacer.placeAll 的 chance、编排器的注册日志 "machineChance=1/{}"）一律
    // 引用本字段，不得再在任何常量/注释里复写具体分母数字。
    // 实机若显示别的分母（如整合包 run/server/config/gtsr/gtsr.cfg 的覆盖值），那是外部覆盖，不改代码默认。
    public static int prosperityMachineChance = 72; // 不导出到 gtsr.cfg（P16 裁决：整合包不得自定义疏密）；字段仍可被离线测试注入

    // 繁荣维度城外中型废墟（outpost）生成频率分母（平均 1/N chunk 生成 1 座；0 = 禁用；
    // S-A5 新增消费 = plan §12 修订 7/8，与残缺机器同 chunk 互斥掷骰：先 outpost，命中跳过机器；
    // P16 同纪律把概率降到三分之一，数字只在本字段）。
    public static int prosperityOutpostChance = 192; // 不导出到 gtsr.cfg（P16 裁决：整合包不得自定义疏密）；字段仍可被离线测试注入

    // ═════════ P8 城外废墟族（plan §5 P8 / §2.1 L5 / §2.2 H-2·H-3）═════════
    //
    // 废墟族 = 由既有结构"破败化"派生出来的<b>破坏结构</b>（无仓室、无控制器、无战利品、无 TE），
    // 是互斥掷骰链上的<b>第三环</b>：outpost → 残缺机器 → 废墟，且只在<b>前两环都没真实落块</b>时
    // 才问门，过的是同一份 prosperityStructureBudgetPerChunk 预算 ⇒"在既有预算内挤位"，
    // 不是叠加密度（实测对照表见 tools/dim1/RuinFamilyCheck 的 DENSITY 行）。
    //
    // 【四键的分工】
    // ① prosperityRuinsEnabled 总开关：关掉后本族<b>一条 roster 都不注册</b>（不只是"不放置"），
    // 名册 / 展示页 / 机检三条链都读得到"没有 ruin 族"这种形态；
    // ② prosperityRuinChance 本族 1/N 分母（另有 0 = 禁用位，与机器/outpost 同口径）；
    // ③ prosperityRuinWindowRepeatCap 本族<b>自己的</b> H-2① 窗重复上限（默认 3，比既有两族的 8 紧：
    // 废墟 footprint 大，"同一种烂墙一窗里出 8 次"比机器更刺眼）；
    // ④ prosperityRuinMicroModulation micro 强度层调制开关（P6 悬空层的收口位，见该字段注释）。
    // 【单一真值】数字只在本类写一次：RuinPlacer/RuinShapes 一律读 Config，roster 的
    // placementDenominator/windowRepeatCap 传 0 = 跟随族键（与 machine/outpost 同纪律），
    // 类注释与注册日志只回显字段值、不写数字。
    public static boolean prosperityRuinsEnabled = true;

    public static int prosperityRuinChance = 144; // 不导出到 gtsr.cfg（P16 裁决：整合包不得自定义疏密）；字段仍可被离线测试注入

    public static int prosperityRuinWindowRepeatCap = 3;

    /**
     * micro 强度层是否参与本族的<b>密度与规模档</b>调制（plan §5 P8 判据 6：P6 交付的
     * {@code GTSRWorldChunkManager.microStrengthAt} 至此结构侧消费点一直是 0，是一片悬空层）。
     * <p>
     * 开（默认）：{@code P(废墟/chunk) = micro / prosperityRuinChance}，且规模档上界按 micro 放开
     * （弱 cell 只出小件、强 cell 才出大件）。关：micro 按中性 1.0F 处理 ⇒ 概率恰为 1/N、规模档
     * 放开到全族，即"链路活着但没有强度调制"。两档都仍然<b>读取</b> micro（出口不因此变成死码）；
     * 关掉它也不删出口——删出口是另一次收口，由 {@code RuinFamilyCheck} E 组申报当前形态。
     */
    public static boolean prosperityRuinMicroModulation = true;

    // ═════════ P14 tpdim 群系定位传送（plan §5 P14「环带步进 + 代价有上界」）═════════
    //
    // 只在 `/gtsr tpdim <A|B> 群系名` 填了群系名时生效（不填 = 0,0 现行为，两键不参与）。
    // 搜索是 L1 权威（GTSRBiomeAuthority.nearestBiomeChunk）上的由内向外环带步进，逐 chunk
    // 身份判定走账本同一份采样源（禁读 Chunk byte 平面、禁 getBiomeGenForCoords）；本两键是
    // 命令线程上扫描代价的唯一上界来源：半径上限决定搜多远，步数保险丝决定单跑最多评估多少
    // chunk（先到者生效；步数截断且已有命中时落点仍给，但回执申报"未证全局最近"）。

    // 群系定位环带搜索半径上限（chunk 数；默认 512 = 8192 格。超限返回可读错误
    // "该维度内未找到该群系（已搜半径 R chunk）"，玩家原地不动；1 = 几乎只查起点列）。
    public static int tpdimBiomeSearchMaxRadiusChunks = 512;

    // 群系定位单次命令步数保险丝（评估 chunk 数上限；默认 400000 ≈ π·357²，防整合包把半径
    // 调到 2048 后单跑逼近 1300 万次评估。0 与负值按 1 处理——保险丝不允许关掉）。
    public static int tpdimBiomeSearchMaxSteps = 400000;

    // 繁荣维度古代城存在概率（每个 24×24 chunk cell 的存在掷骰百分比；默认 45，0 = 无城，100 = 全 cell 有城；S4b 消费）。
    public static int prosperityCityChance = 45; // 不导出到 gtsr.cfg（P16 裁决：整合包不得自定义疏密）；字段仍可被离线测试注入

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

    // ═════════════ P5b 成簇散布（plan §7.2 U3 改判「K=1 + 必须成簇（高方差）」）═════════════
    //
    // 用户口径：「别出现每个区块都有，要有的区块多一点，有的区块少一点，方差拉大！」⇒ 散布层
    // 从"每 chunk 独立掷骰"换成两级过程：先按低概率在 C×C chunk 格上选"残骸场中心"（H-2/H-3
    // 中间尺度），再围绕场心按径向衰减撒件（H-3）。均值 ≈1.2 件/chunk，但多数 chunk 为 0、
    // 少数 chunk 成堆（判据带实测见 plan/investigation/p5b-clustered-scatter-20260919.md）。
    //
    // 【回退链两级】
    // ① prosperityScatterClusterMode = 0 ⇒ 走 P5 均匀档（scatterUniform 为 P5 代码原样），
    // 连同下方 P5 段四键（默认 K=8/落块24/竖向off/窗2）即与 P5 终态<b>逐位相同</b>
    // （tools/dim1/surface_checks.sh [14b] 摘要对拍钉住）；
    // ② 不关开关、只把参数设成退化档 cell=1 + chanceDenom=1 + piecesMin=piecesMax=8 +
    // radius=0 ⇒ 与 P5 K=8 均匀档<b>统计一致</b>（每 chunk 填到 round(8×群系权重)；
    // 实测对照见 ScatterClusterVarianceCheck DEGEN 行）；
    // ③ 回到改造前"现状柱阵"= ① + P5 段注释的四键旧值组合（竖向true/件数64/落块0/窗0）。
    // 竖向件在本片仍然<b>保持摘除</b>（任务包禁止复活；prosperityScatterVerticalPieces 默认 false 不动）。

    // 成簇模型开关：0 = P5 均匀档（回退位），1 = 两级成簇档（默认）。
    public static int prosperityScatterClusterMode = 1;

    // 场中心候选格的边长（chunk 数）。默认 4 ⇒ 4×4 chunk = 64×64 格一个候选场格；
    // 尺度必须介于 H-3(1 chunk) 与 H-2(16 chunk) 之间（plan §2.2），1 = 退化档（每 chunk 一场）。
    public static int prosperityScatterClusterCellChunks = 4;

    // 场中心命中分母：每个候选场格以 1/本值 的概率成为残骸场。默认取实测扫描选定档；
    // 1 = 每格皆场（退化档要件之一，此时密度由 piecesMin/Max 决定）。
    public static int prosperityScatterClusterFieldChanceDenom = 3; // 不导出到 gtsr.cfg（P16 裁决：整合包不得自定义疏密）；字段仍可被离线测试注入

    // 单场件数配额的均匀分布下界（该 chunk 对该场的落件上限，round(配额×群系权重) 口径同 P5 的 K）。
    public static int prosperityScatterClusterPiecesMin = 8;

    // 单场件数配额的均匀分布上界（< min 时按 min 处理）。默认档取值与 min 一起决定"富 chunk 堆多大"
    // （实测件数分布直方图见 p5b 证据文档判据 1）。
    public static int prosperityScatterClusterPiecesMax = 14;

    // 径向衰减半径（chunk 数；件位 r = 半径×16格 × u^falloffPower 采样）。0 = 件全部落在场心所在
    // chunk 内（退化档要件之一：配合 cell=1/denom=1/pieces=K 复现 P5 均匀形状）。
    public static int prosperityScatterClusterRadiusChunks = 2;

    // 径向衰减幂次（r = R·u^p，p=1 ⇒ 面密度 ∝1/r 中心聚簇最强；越大越向外均摊）。默认 1。
    public static int prosperityScatterClusterFalloffPower = 1;

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
    // 用户口径曾锁定「城外 outpost/机器的 1/N 概率一律不动」（当时"每 16×16 窗 16 座"≈15.4 座/窗即由
    // 两族 1/N 算术得出），故 P7 本段三键都不碰概率。
    // 【P16 改判（2026-09-21 用户裁决）】改为"疏密不允许整合包自定义"+ 概率整体降到当时实机生效值的
    // 三分之一 ⇒ 上述锁定作废；分母真值一律只看本类字段声明，注释与日志不得复写具体数字。
    //
    // 【回退位】prosperityStructureWindowRepeatCap = 0 ⇒ 窗重复上限关闭；
    // prosperityStructureFamilyGapChunks = 0 ⇒ 同族间距关闭 ⇒ 两键同 0 即回到"只受 1/N 独立掷骰 +
    // 每 chunk 预算约束"的状态（实测复现改造前 23.882% 贴脸率，见
    // plan/investigation/p7b-placement-contract-20260919.md 的 T4 与 p7c 证据文档的 cap 扫描表）。
    // prosperityStructureBudgetPerChunk = 0 ⇒ 关闭每 chunk 结构预算（同 chunk 可叠多座，
    // 改造前的跨 chunk 贴脸之上再叠同 chunk 贴脸，只作调参观察用）。

    // H-3 每 chunk 允许<b>真实落块</b>的结构座数上限（默认 1 = 与改造前"outpost 命中则跳过机器"的
    // 互斥掷骰等值，但改造前那条只在 outpost 谎报成功时才生效；0 = 不限）。
    public static int prosperityStructureBudgetPerChunk = 1; // 不导出到 gtsr.cfg（P16 裁决：整合包不得自定义疏密）；字段仍可被离线测试注入

    // H-2① 每 16×16 chunk 窗内"同一结构模板"允许<b>实际请求</b>的 chunk 数上限（P5b 起默认 8 =
    // 主代理拍板档「cap=8 + gap=1」：cap 退化为仅护栏、对当前命中分布几乎不咬合（P7c 扫描表
    // cap=8/gap=0 结构总数 = 回退档 984），密度损失全部由下方间距键承担 ⇒ 符合用户
    // "概率不动只治贴脸"口径；实测见 p5b 证据文档判据 3。旧默认 3 的 P7c 实测在
    // p7c-window-cap-semantics-20260919.md 判据 1/2；0 = 关闭。判定 = PlacementGate.windowRepeatAllows，
    // 与散布侧的排名配额是两套各自唯一的实现，见 PlacementGate 类注释第 4 点）。
    public static int prosperityStructureWindowRepeatCap = 8;

    // H-2② 同族结构的最小间距档（默认 1 = 8 邻 chunk 内已有同族命中则本座让行；0 = 关闭）。
    // 档位 g ⇒ 邻域 (2g+1)²−1 个 chunk（g=1→8、g=2→24），上界由 PlacementGate.SPACING_GAP_MAX 钳制。
    // 这是"贴脸率"的受控量（plan §2.2 H-2 的第二列），也是 23.882% 基线的唯一治疗手段。
    public static int prosperityStructureFamilyGapChunks = 1;

    // ═════ P9 生物层（L7）═════ 键名/默认值/注释与 GTSRCreatureRoster 的申报严格一致（§2.4 判据 4）。
    // 【单一真值分工】本段只放<b>三档的基权</b>与总开关/追踪距离；
    // "哪个群系带出现哪种生物、出现多少"的<b>带乘子表</b>只在 GTSRCreatureRoster 声明一处
    // （生效权 = 本段基权 × roster 带乘子，乘子 0 ⇒ 该带不出现该生物）。两处各持一半，不互相抄。
    // 【回退位】prosperityCreaturesEnabled = false ⇒ GTSRBiomeBase 的 4 张 spawn 列表回到
    // P8 的无条件清空态（逐位一致），且不调 EntityRegistry.registerModEntity。

    // L7 生物层总开关（默认开）。关掉后本维四群系的 spawn 列表恒空 ⇒ 与 P8 基线逐位一致。
    public static boolean prosperityCreaturesEnabled = true;

    // 三档实体的 FML 追踪距离（registerModEntity 的 trackingRange，默认 64 = GT++ 配方同档；
    // updateFrequency 固定 3，见 wiki mods/gto/machines/wave-creature-system.md:48-49，不做键）。
    public static int prosperityCreatureTrackingRange = 64;

    // 齿轮鸽基权（02 册 §1.4 设计值 12；生效权 = 12 × 本维带乘子）。
    public static int prosperityCreatureGearPigeonWeight = 12;

    // 汽雾萤基权（02 册 §1.4 设计值 8；生效权 = 8 × 本维带乘子）。
    public static int prosperityCreatureSteamFireflyWeight = 8;

    // 渣脊猎手基权（敌对档，用户 AUQ 已确认保留；02 册无此档 ⇒ 取低于两件和平档的 6）。
    public static int prosperityCreatureSlagRidgeHunterWeight = 6;

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

        // P16（用户裁决）：城外三类结构的疏密分母（残缺机器 / outpost / 古代城存在率）一律不再导出到
        // gtsr.cfg —— 整合包不得自定义稀疏程度，真值只在本类字段声明处。cfg 里若残留同名旧键会被
        // Forge 当孤儿项忽略，不影响加载。

        // ═════ P14 tpdim 群系定位（环带步进上界）——键名/默认值/注释与本类字段声明严格一致 ═════
        tpdimBiomeSearchMaxRadiusChunks = configuration.getInt(
            "tpdimBiomeSearchMaxRadiusChunks",
            Configuration.CATEGORY_GENERAL,
            tpdimBiomeSearchMaxRadiusChunks,
            1,
            2048,
            "tpdim 群系定位环带搜索半径上限（区块数，默认 512 = 8192 格；超限返回可读错误" + "「该维度内未找到该群系（已搜半径 R 区块）」且玩家原地不动；不填群系名的 0,0 传送不受本键影响）");

        tpdimBiomeSearchMaxSteps = configuration.getInt(
            "tpdimBiomeSearchMaxSteps",
            Configuration.CATEGORY_GENERAL,
            tpdimBiomeSearchMaxSteps,
            1,
            5000000,
            "tpdim 群系定位单次命令步数保险丝（评估区块数上限，默认 400000；与半径上限先到者生效。" + "步数截断且已有命中时仍传送但回执申报「未证全局最近」。不允许 0/负值 = 关闭保险丝）");

        // ═════ P8 城外废墟族（cfg 侧现两键与本类字段声明严格一致：ruinChance 已随 P16 移出 cfg）═════
        prosperityRuinsEnabled = configuration.getBoolean(
            "prosperityRuinsEnabled",
            Configuration.CATEGORY_GENERAL,
            prosperityRuinsEnabled,
            "城外废墟族（P8 破坏结构）总开关（默认开；关闭后本族一条 roster 都不注册、编排器也不问门，" + "城外结构面逐位回到 P5b 终态）");

        // prosperityRuinChance 不导出（P16：疏密不允许整合包自定义，见字段声明）。

        prosperityRuinWindowRepeatCap = configuration.getInt(
            "prosperityRuinWindowRepeatCap",
            Configuration.CATEGORY_GENERAL,
            prosperityRuinWindowRepeatCap,
            0,
            1000,
            "城外废墟族每 16×16 窗内同一废墟模板允许实际请求的区块数上限（默认 3；0 = 关闭；"
                + "判定 = PlacementGate.windowRepeatAllows，以命中集为条件、只削重复副本不削首次出现）");

        prosperityRuinMicroModulation = configuration.getBoolean(
            "prosperityRuinMicroModulation",
            Configuration.CATEGORY_GENERAL,
            prosperityRuinMicroModulation,
            "micro 强度层是否调制城外废墟族的密度与规模档（默认开：P = micro/ruinChance 且弱 cell 不放大件；"
                + "关 = 一律按中性 1.0F 处理，概率回到 1/N、规模档放开到全族。两档都仍经过 L1 的"
                + " microStrengthAt 只读出口，关掉的是调制不是接线）");

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

        // ═════ P5b 成簇散布（plan §7.2 U3 改判）——键名/默认值/注释与本类字段声明严格一致 ═════
        prosperityScatterClusterMode = configuration.getInt(
            "prosperityScatterClusterMode",
            Configuration.CATEGORY_GENERAL,
            prosperityScatterClusterMode,
            0,
            1,
            "成簇散布开关（默认 1 = 两级成簇档：低概率选残骸场中心 + 径向衰减撒件；" + "0 = P5 均匀档（每 chunk 独立掷骰，回退位，与 P5 终态逐位相同）。竖向件开关在两种模式下均保持原语义）");

        prosperityScatterClusterCellChunks = configuration.getInt(
            "prosperityScatterClusterCellChunks",
            Configuration.CATEGORY_GENERAL,
            prosperityScatterClusterCellChunks,
            1,
            16,
            "场中心候选格边长（chunk 数，默认 4 ⇒ 候选格 64×64 格；尺度介于 H-3 与 H-2 之间，" + "不得回到每 chunk 独立判定。1 = 退化档：每 chunk 一个候选场）");

        // prosperityScatterClusterFieldChanceDenom 不导出（P16：疏密不允许整合包自定义，见字段声明）。

        prosperityScatterClusterPiecesMin = configuration.getInt(
            "prosperityScatterClusterPiecesMin",
            Configuration.CATEGORY_GENERAL,
            prosperityScatterClusterPiecesMin,
            1,
            1024,
            "单场件数配额下界（该 chunk 对该场的落件上限，round(配额×群系权重) 口径同 P5 的 K）");

        prosperityScatterClusterPiecesMax = configuration.getInt(
            "prosperityScatterClusterPiecesMax",
            Configuration.CATEGORY_GENERAL,
            prosperityScatterClusterPiecesMax,
            1,
            1024,
            "单场件数配额上界（< 下界时按下界处理；min..max 均匀掷配额。min=max=K 且 cell=1/denom=1/radius=0" + " ⇒ 统计退化为 P5 的 K 档）");

        prosperityScatterClusterRadiusChunks = configuration.getInt(
            "prosperityScatterClusterRadiusChunks",
            Configuration.CATEGORY_GENERAL,
            prosperityScatterClusterRadiusChunks,
            0,
            16,
            "径向衰减半径（chunk 数，默认 2；件位 r = 半径×16格 × u^falloffPower，中心密外围疏。" + "0 = 件全部落在场心所在 chunk（退化档要件））");

        prosperityScatterClusterFalloffPower = configuration.getInt(
            "prosperityScatterClusterFalloffPower",
            Configuration.CATEGORY_GENERAL,
            prosperityScatterClusterFalloffPower,
            1,
            4,
            "径向衰减幂次（r = R·u^p；p=1 ⇒ 面密度 ∝1/r 聚簇最强，p 越大越向外均摊。默认 1）");

        // ═════ P7 结构放置契约（H-2/H-3 结构侧）——键名/默认值/注释与本类字段声明严格一致 ═════
        // prosperityStructureBudgetPerChunk 不导出（P16：疏密不允许整合包自定义，见字段声明）。

        prosperityStructureWindowRepeatCap = configuration.getInt(
            "prosperityStructureWindowRepeatCap",
            Configuration.CATEGORY_GENERAL,
            prosperityStructureWindowRepeatCap,
            0,
            256,
            "H-2① 每 16×16 区块窗内同一结构模板允许<b>实际请求</b>的区块数上限（默认 8 = P5b 拍板档"
                + "「cap=8 + gap=1」：cap 只作护栏、密度损失全部由间距承担；0 = 关闭）。"
                + "语义是命中集条件的重复上限：首次出现永不因本键被拒，只削第 cap+1 个副本"
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

        // ═════ P9 生物层（L7）——三档基权 / 总开关 / 追踪距离（带乘子表在 GTSRCreatureRoster） ═════
        prosperityCreaturesEnabled = configuration.getBoolean(
            "prosperityCreaturesEnabled",
            Configuration.CATEGORY_GENERAL,
            prosperityCreaturesEnabled,
            "L7 生物层总开关（默认开）。关闭后本维四群系的 4 张 spawn 列表回到无条件清空态" + "（与 P8 基线逐位一致），实体也不再走 registerModEntity。");

        prosperityCreatureTrackingRange = configuration.getInt(
            "prosperityCreatureTrackingRange",
            Configuration.CATEGORY_GENERAL,
            prosperityCreatureTrackingRange,
            8,
            128,
            "三档实体的 FML 追踪距离（registerModEntity 的 trackingRange，默认 64；"
                + "updateFrequency 固定 3，不做键 —— 见 wiki wave-creature-system.md:48-49）");

        prosperityCreatureGearPigeonWeight = configuration.getInt(
            "prosperityCreatureGearPigeonWeight",
            Configuration.CATEGORY_GENERAL,
            prosperityCreatureGearPigeonWeight,
            0,
            100,
            "齿轮鸽基权（02 册 §1.4 设计值 12；0 = 该档整档退出）。生效权 = 基权 × 带乘子，" + "带乘子表唯一声明在 GTSRCreatureRoster");

        prosperityCreatureSteamFireflyWeight = configuration.getInt(
            "prosperityCreatureSteamFireflyWeight",
            Configuration.CATEGORY_GENERAL,
            prosperityCreatureSteamFireflyWeight,
            0,
            100,
            "汽雾萤基权（02 册 §1.4 设计值 8；0 = 该档整档退出）。口径同齿轮鸽");

        prosperityCreatureSlagRidgeHunterWeight = configuration.getInt(
            "prosperityCreatureSlagRidgeHunterWeight",
            Configuration.CATEGORY_GENERAL,
            prosperityCreatureSlagRidgeHunterWeight,
            0,
            100,
            "渣脊猎手基权（敌对档，plan §7.1 生物裁决保留；0 = 该档整档退出）。口径同齿轮鸽");

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }
}
