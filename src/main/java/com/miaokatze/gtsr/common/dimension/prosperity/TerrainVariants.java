package com.miaokatze.gtsr.common.dimension.prosperity;

import java.util.HashMap;

import com.miaokatze.gtsr.common.dimension.framework.genlayer.GTSRGenLayerChain;
import com.miaokatze.gtsr.common.dimension.framework.structure.GTSRWorldgenHash;

/**
 * 群系内分支地形变体（<b>P19 §H 新增</b>，plan §H「实现放新类 TerrainVariants（profile 只留调用点）」）：
 * 在 {@link ProsperityTerrainProfile#heightCore} 的三频缓丘 {@code h0} 之上、河谷两段式压低<b>之前</b>，
 * 按 roster 注入群系性格形态项——齿轮森林丘陵/山地/<b>岭脊</b>（RTG TerrainBase hills 模板 + ATG
 * CoreNoise plateau 模板）+ <b>谷地负瓣</b>（v1.20.42 P22 A4，冲沟/谷地的下侧形态）、黄铜荒漠垄状沙丘 + <b>风蚀山体与风蚀柱座台</b>（RTG dunes +
 * TerrainHLDunes domain-warp + {@code terrainBryce} 倒数式模板）、起雾沼泽水位渐变夹持 +
 * <b>三档水体分支的下挖 delta</b> + 泥丘 + 炭屑滩（ATG swamp 模板）、锈蚀草原轻微丘陵与<b>低地</b>、
 * 遗忘之川微起伏（同场降档）。
 * <p>
 * <b>契约（v1.20.41 P20 §13 C7 改写）</b>：本类<b>只做地形——含沼泽深水池的下挖 delta 与夹持目标的
 * 抬升——一律不置水</b>；水体列的<b>回填门在 populate 侧</b>（{@code ChunkProviderProsperityRuins}
 * 的沼泽微池/巨湖回填，P20 归 S6），两侧共用本类的 {@link #swampTierAt} 作为<b>唯一分档真值</b>
 * （本类出"这一列属于哪一档、被挖到多深"，回填门按档位决定填到哪个水面）。
 * 旧契约原文「<i>只做地形夹持，不置水（沼泽微池水面归批 2 的沼泽小湖面）</i>」的"夹持"与"批 2"两处
 * 口径已不覆盖本片形态（三档水体分支 + 低地 + 风蚀山体），故随本轮改写；等价的机制说明见下方
 * "参数定值"各支与 {@link #swampTierAt} 的 javadoc。
 *
 * <h2>与 {@code ampAt} 振幅场的正交性（两场各管一件事）</h2>
 * {@link ProsperityTerrainProfile#ampAt} 是<b>振幅场</b>：控制三频缓丘的"起伏总量"（sd 量级，
 * P17 偏序 森&gt;原≥沙&gt;沼 由它承担）；本类是<b>形态场</b>：控制"形态分布"（丘陵鼓包/桌状山地/
 * 垄状沙丘/贴水微洼的形状与覆盖），变体项以<b>加性 delta</b> 叠在 h0 上，不乘不改振幅档表——
 * 振幅档与 zone 乘子照旧作用于三频基线，本类的门强/弱不改变 sd 偏序的来源，只改变群系内分布形状。
 *
 * <h2>全软门纪律（无硬地形阈值）</h2>
 * <ul>
 * <li>所有门限都是低频噪声 → smoothstep 带通（{@link #s01}），带外<b>精确等于 0.0</b>：
 * 丘陵/山地/岭脊/<b>谷地负瓣</b>/低地/风蚀山体/深水池/水沼地/泥丘/炭屑滩各门关死的列 delta 恒为 ±0.0，
 * {@code h0 + 0.0} 经 round 回 int 与改造前<b>逐位相同</b>
 * （"均匀区（门噪声极值外）h0 与改造前逐位同"的构造性保证）。荒漠沙丘与沼泽夹持是群系身份本身
 * （RTG/ATG 原型亦无关死态），全群系起作用、无恒等区，属设计内例外；身份缺席
 * （{@code rosterIndex} 越界/离线无账本）整列零变体，仍逐位同改造前。</li>
 * <li>半空间折叠 {@code min(d,0)} 用 {@code (d−|d|)/2} 表达，软削顶用 C1 光滑极小
 * {@link #softMin}——全程无 if 形状分支（代码里的 {@code > 0} 短路只影响求值次数，
 * 数学结果与"先求值再乘 0"逐位相同）。</li>
 * <li><b>本节纪律的一处设计内例外（v1.20.41 P20 §21-D）</b>：沼泽三档<b>水体项</b>在 delta 侧改为
 * <b>互斥取 {@link #swampTierAt} 判出的那一档</b>（单点分流 ⇒ 地形侧与回填侧同口径，杜绝"档位浅、
 * 水却深"的 4 格分水岭穿透）。互斥要求"本档之外的另两档在该列贡献精确 0"，而档位的定义本身就是
 * {@code 门 ≥ 0.5} 的硬判据 ⇒ 这一支的 delta 在<b>档位等值线</b>上不再 C0：列从 POOL 档跨进 DEEP 档
 * 时下挖从 ≤1.9 格跳到 ≥3.5 格（两侧都含夹持项后实测跳幅见 S4b 回执）。这是"回填门必须按档位决定
 * 水深"的必然后果——<b>软门连续性与档位↔delta 同口径在 0.5 这条线上不可兼得</b>，§21-D 选了后者。
 * 非水体两项（泥丘/炭屑滩）与其余所有支路仍按上两条纪律保持全软。</li>
 * <li>跨变体过渡：roster 身份是逐粗格（4 方块）台阶量，若按单点身份硬选变体，群系边界两侧
 * delta 硬跳（森 +14 ↔ 沙 0），正是 P18 T3 在振幅档上修掉的那类悬崖。本类照搬 T3 的药方：
 * 变体 delta 按 11×11 smoothstep 紧支核（半径 5 粗格 = 20 方块，核形与
 * {@code ProsperityTerrainProfile} 的 AMP_KERNEL 同式）对逐粗格身份做<b>加权混合</b>，
 * 边界两侧收敛到同一凸组合 ⇒ C0 连续；带内起伏缓入缓出再由各低频门自身连续性保证。</li>
 * </ul>
 *
 * <h2>参数定值（公式依据 G3 调查原文，可复核 RTG/ATG）</h2>
 * <ul>
 * <li><b>丘陵场（草原 0 / 森林 1 共场不同档）</b>：RTG TerrainBase:33-45 blendedHillHeight
 * S 曲线（r=s+1; r=r³+10; r=cbrt(r); r=r/0.46631−4.62021，s=0 映 ~0.15、s=−1 映 ~0 ⇒ m≥0 只加不减）+
 * :92-104 两波组合（150 波长大波 m、55 波长小波 sm，sm=sm²·m、m+=sm/3）。门噪声波长 320，
 * 森林带 [0.08,0.35]（<b>v1.20.41 幅度 ×14→×20 ⇒ 丘陵区 +8..+20</b>，需求 4「起伏再更大一些」的
 * 形态侧落点，档表 1.70 已封顶故不进档表）、草原带 [0.20,0.50]（幅度 ×10 ⇒ +2..+6 常态带；旧注释
 * 写"×8"与常量 10.0 滞后，本轮一并改齐）；门带按 valueNoise 实测边际（三角型集中在 0）标定，
 * 阈值必须落在 working 区而非薄上尾。</li>
 * <li><b>山地变体（仅森林，更稀有低频门）</b>：门噪声波长 512、带
 * <b>[0.24,0.60]（v1.20.41 由 [0.30,0.60] 放宽下檐 ⇒ 覆盖率 P≈10%→≥15%，需求 4）</b>；
 * ATG CoreNoise:154-194 plateau 双档 88/104（档选择 = 同门噪声的 [0.45,0.65] 二级带）线性混合
 * {@code base(1−lf)+ledge·lf} + 碎坡项 {@code |base−ledge|·lf·(rough+1)/2}（rough=波长 40 噪声），
 * lf=门值 ⇒ 山地列 +14..+26 稀有高值；plateau 作用在"已含丘陵"的底上 ⇒ 山顶整平。</li>
 * <li><b>岭脊变体（仅森林，v1.20.41 新增第三形态）</b>：RTG ridged 形状 {@code 1−|n|}（脊线在
 * n≈0 处最尖）载于波长 <b>188</b> 的门噪声（188%16=12 ⇒ 合 P20 §3 H-1；量级介于丘陵门 320 与
 * 山地门 512 之间，取"比值/形状"不取 RTG 绝对值，§13 C10），门带
 * {@code s01((1−|n|−0.72)/0.16)} ⇒ 满门域 |n|≤0.12、缓入域 |n|≤0.28；幅
 * {@code RIDGE_AMP=12.0} = 丘陵 20 与山地 +14..+26 之间的中间档。</li>
 * <li><b>谷地负瓣（仅森林，v1.20.42 P22 A4 新增）</b>：顶部抬升路径三面封死（幅度域上沿 22.0 实测
 * 零收益、15.28% 列被 {@code DELTA_CAP}=32 软顶吃掉、高度域上沿已是 108 哨兵）后，"山脉丘陵状"的
 * 起伏补强换到<b>下侧</b>——独立场波长 <b>139</b>（139%16=11 ✔ H-1）取负瓣门
 * {@code s01((−n−0.45)/0.25)}（满门 n≤−0.70、缓入到 −0.45；实测覆盖 any 10.10%/half 5.29%/满门 2.28%
 * ——计划按三角边际估 12-18%，实测 bilinear 边际更尖，差入披露），下挖
 * {@code −(4.0+6.0·门)·门}（外侧乘门 = 低地/深水池同款软门纪律）⇒ 满门最深 −10；负 delta 不经
 * {@code DELTA_CAP}（softMin 只封上侧）⇒ 只花下侧预算（触 y=40 地板现状 145 列 vs 预算带 943）。</li>
 * <li><b>荒漠沙丘（roster 2，无丘陵）</b>：TerrainHLDunes:11-27 domain-warp（波长 20、幅度 6 的
 * x 向方向性抖动先扭坐标）喂 RTG TerrainBase:226-247 dunes 参数化——强度场
 * {@code 0.38+0.30·n(/224)}、主波 {@code n(/60)·st·DUNE_MAIN_GAIN}（增益按符号引，别在这里重抄倍数——
 * 旧注释写死"·2"而常量真值是 2.6，P20 债④ 改齐）、半空间折叠 {@code (d−|d|)/2}（只取负瓣，
 * 垄形不对称）、二次整形 {@code p·(3p+1.8)}（p=−fold，线性+二次复合防三角边际压扁垄高）、
 * 碎浪细节 {@code n(扭曲坐标/17)·(0.6+st)}、盆底偏置 {@code −1.3·st} ⇒ 强度场高区垄峰 +4..+8、
 * 垄间盆 −1.5 左右（沙丘海/平沙地由强度场连续调制）。</li>
 * <li><b>风蚀山体 + 风蚀柱座台（roster 2，v1.20.41 新增）</b>：RTG {@code TerrainBase:158-164
 * terrainBryce} 的"四层高频绝对值相加 → 取倒数"式（本仓重写为
 * {@code sn = Σ 2^{-i}·|n(λ_i)|}，λ = 13/27/53/107，全部 %16 ≠ 0；倍频比 ≈2 同 RTG），
 * {@code ridge = BRYCE_GAIN/(BRYCE_FLOOR + BRYCE_SLOPE·sn)} ⇒ sn 越小柱越尖。RTG 的
 * {@code sn<6} 绝对阈值归零门<b>不可直译</b>（其 sn 量纲是"高度除数"，本仓 valueNoise ∈[−1,1)）
 * ⇒ 换成门带 {@code s01((n₄−0.60)/0.20)}（<b>范式映射非直译</b>；n₄ = 四层中最低频 λ107 层的
 * <b>有符号</b>值，与 sn 同源 ⇒ 零额外求值，荒漠每列仍 +4 次），限幅
 * {@code softMin(ridge, 22.0, 4)}（低于 {@link #DELTA_CAP}）。座台 = 同一四层域内 λ53 层有符号值的
 * 窄带 {@code 1.5·s01((n₃−0.82)/0.10)}，即"风蚀柱 footprint 内的 +1.5 座台"；柱身 feature 归
 * populate 侧（P20 S6），其落点谓词与本支共用同一场（{@link #windSpineSiteAt}）⇒ 无第二真值。</li>
 * <li><b>草原低地（roster 0，v1.20.41 新增）</b>：半空间折叠 {@code (d−|d|)/2}（沙丘同款，只取
 * 负瓣）载于波长 <b>93</b> 的场（93%16=13；意图 96 但 96 是 16 倍数 ⇒ 故意避开，H-1），覆盖门
 * {@code s01((−d−0.20)/0.30)} ⇒ 满门 d≤−0.5、缓入 d≤−0.2，形态深度
 * {@code −(3.0 + 3.0·min(1, −折叠×2))·门} ∈ −3..−6（需求 6"低地"）。深度出处 = EBXL
 * {@code BiomeMarsh.setHeight(new Height(−0.2F, 0.2F))} 折算 −0.2×14.5×RELIEF_MULT 1.2×amp 1.10
 * ≈ −3.8 ⇒ 下沿 −3、上沿 −6（1.5× 放大让"低地"可辨，P20 §8）。</li>
 * <li><b>沼泽（roster 3）</b>：ATG CoreNoise:129-152 swamp 渐变夹持
 * {@code h=(1−f)·h+f·target}，f=0.55+0.40·s01（波长 256 门）。
 * <b>v1.20.41 三档水体分支的地形侧</b>（需求 3；档位判定唯一出口 {@link #swampTierAt}）：
 * ① <b>表面水池</b> = 原微洼档，床纹波长 <b>48</b>（P20 §14.3 改判：<b>48 是活波长</b>，旧版把它
 * 写成 {@code :273} 的内联字面量，本轮提名为 {@link #SWAMP_POOL_BED_WAVE} 作单一真值；48 是 16
 * 倍数 = 既存偏离，登记 P20 §12 版 2 校准、本轮不回改，H-1 只管新增量），深度
 * {@code SWAMP_POOL_DEPTH} 2.6→<b>2.0</b>、门带 [0.15,0.50]→<b>[0.12,0.55]</b>（覆盖↑ ⇒ "多"）；
 * ② <b>深水池</b> = 新场波长 <b>37</b>（37%16=5 ✔）、门 {@code s01((n−0.62)/0.14)}、delta
 * {@code −(5.0 + 4.0·门)} ⇒ 与表面池（1–2 层）之间有 4 格分水岭（P20 §8 自定口径）；
 * ③ <b>水沼地</b> = 新场波长 <b>61</b>（61%16=13 ✔）、门 {@code s01((n−0.30)/0.35)}、delta
 * {@code −(0.5 + 1.0·门)}，且夹持目标 {@code target} 由 SEA_LEVEL−0.5 <b>抬到 SEA_LEVEL−0.0</b>
 * ——EBXL {@code BiomeMarsh} "root 0.0~0.1 ⇒ 半数列自然淹水"的机制解（本仓取"贴水面 + 浅扇形下挖"
 * 表达半淹，回填门仍在水面侧）；④ <b>泥丘</b> = 新场波长 <b>113</b>（113%16=1 ✔）、delta
 * {@code +(2.0 + 2.5·门)} ⇒ +2..+4.5；⑤ <b>炭屑滩</b> = 复用 ③ 场的<b>负瓣</b>
 * {@code s01((−n−0.55)/0.25)}（零额外求值、与 ③ 空间互斥）、delta {@code −(0.5 + 0.5·门)}
 * ⇒ −0.5..−1.0（<b>只改地形</b>，铺料归 populate 侧 S6）。①②③ 三项<b>水体项在 delta 侧互斥</b>
 * （<b>v1.20.41 P20 §21-D</b>）：一列只落 {@link #swampTierAt} 判出的那一档的那一项，其余两档在该列
 * 贡献精确 0 ⇒ 旧"三档并集相加"（§5 原文）造成的穿透归零——修复前被判为表面池的列有 11.4%
 * （7111/62169）被同列的深水池/水沼地项挖到 ≥4 格、NONE 档另有 1258 列 ≥4 格（S4 散文写 1257，其探针
 * 输出与本片复现都是 1258），S6 按"档位→水面"回填
 * 会出"档位浅、水却深"。④⑤ 两项是<b>非水体项</b>，保持相加但合计下挖限幅到
 * {@link #SWAMP_NONWATER_DIG_MAX} = 1.5 格。夹持本体幅度保守（≤±3.2）。
 * <b>v1.20.42 P22 A3 起三档水体加"避群系边缘"门</b>：非腹地列（coarse Chebyshev
 * {@link #SWAMP_EDGE_RADIUS_CELLS} 内 roster 非 3）的 {@link #SWG_TIER} 槽钳 NONE ⇒ 水体项
 * 精确 0（边缘截断水潭清零，判据落 P17TerrainReliefCheck A3 组）；波长与门带不动。</li>
 * <li><b>遗忘之川（roster 4）</b>：同沼泽场降档（f=0.25+0.25·s01、洼 ≤1.6）⇒ ±1..2 微起伏。
 * 生产路径身份面只产生 0..3（见 RELIEF_AMPLITUDE_BY_ROSTER 第 5 元同款纪律），本档为名册
 * 对称兜底，与 P17"生产取不到"口径一致；三档水体分支与泥丘/炭屑滩<b>不</b>进本档（roster 3 专属，
 * 由 {@link #swampTierAt} 的 roster 门同口径挡住）。</li>
 * <li><b>软削顶（A 模板）</b>：加权总 delta 过
 * {@code softMin(·,32,10)}（<b>v1.20.41 随森林 delta 上抬 1.43× 同步 26→32、k 8→10</b>；
 * {@link #softMin} 的定义保证 |a−b|≥k 时逐位等于 min ⇒ δ≤22 逐位不动、上界渐近 32，
 * "+14..+26 稀有高值"与新增的岭脊/风蚀山体不失真）；结果高度再过 {@code softMin(·,108,4)}
 * （≤104 逐位不动——P17 实测高度域上沿恰为 104）⇒ 变体列恒 ≤108，[40,110] 钳制对变体列
 * <b>永不截平</b>，sd 不被钳制削顶。</li>
 * </ul>
 *
 * <h2>每列新增成本（U8 性能批总账的申报基数）</h2>
 * 核混合 pass = 69 次 (seed,粗格) 缓存查表 + 5 路乘加（<b>零噪声求值</b>；缓存纪律与
 * {@code ProsperityTerrainProfile.ROSTER_CELL_CACHE} 同款：线程私有、上限 16384、超限整清）。
 * 噪声求值（{@code valueNoise}，每次 4 格点哈希）：草原 1（门开 +2）；森林 2 常态、
 * 丘陵列 4、山地列 3-5；荒漠 3（扭曲/强度/主波，碎浪复用扭曲种子换波长零加费）；沼泽 2。
 * <b>v1.20.42 P22 A4 复核（森林支路 +1）</b>：谷地负瓣的独立场（λ139）在 {@code w[1]>0} 的列上
 * 无条件求值一次（门开关须先求值才能判）⇒ 森林常态 2→<b>3</b>、丘陵列 4→<b>5</b>、山地列 3-5→<b>4-6</b>；
 * 其余群系求值数不变（谷地场在 {@code w[1]==0} 的列不进分支）。
 * 常态腹地 2-3 次，与任务包"每列 1-3 次"口径一致；门全开列最坏 5 次（幅度小、value noise 便宜，
 * 实测账交 U8 基准判据对 241µs 基线总账）。
 * <b>v1.20.41 P20 §21-D 复核（沼泽支路求值数）</b>：三档水体项改互斥<b>不</b>减少每列求值次数——
 * 深水池/水沼地/泥丘三个独立场都必须先求值才能定档并取本档值（半淹场同出炭屑负瓣 ⇒ 零加费），
 * 与互斥前一致：roster 3 每列 5 次（夹持 1 + 表面池 1 + §21-D 起仍为 +3），roster 4 保持 2 次
 * （{@code withTiers=false} 路径逐字未动）。
 * <b>v1.20.42 P22 A3 复核（边缘门求值数）</b>：{@code SWG_TIER} 槽的边缘门是粗格级缓存查表
 * （零噪声求值，见 {@link #swampInteriorAt}）⇒ 沼泽支路每列噪声求值数不变；非沼泽列不进
 * {@code swampGates} 的 withTiers 路径，逐位不动。
 * <b>P19 U8 实测账（GenBenchCheck 口径，基线 = 批2 终态）</b>：权重查表随
 * {@link #weightsAt} 改判为粗格终值缓存（69 次查表 → 1 次，同一粗格内列间恒等），
 * 身份取数并入 Profile 的 {@code chainRosterIndexAt} 单一 memo；其余见 plan/tmp/p19-u8-prered.md。
 * 纯函数、零 {@code net.minecraft} 依赖、零方块读取（身份取数只经
 * {@code GTSRGenLayerRosterFace.rosterIndexAt} 的 int 出口，经 Profile 共享 memo 转发），
 * 同 seed 同坐标恒同输出——四族共用 {@code heightAt} 的同源契约经调用点自动继承。
 */
public final class TerrainVariants {

    /** 变体名册槽数（0..3 = selector 群系，4 = 遗忘之川名册档；越界一律零变体）。 */
    private static final int ROSTER_SLOTS = 5;

    /**
     * 每线程每 seed 的<b>粗格权重向量</b>缓存（P19 U8 改判）：11×11 核对逐粗格身份的加权结果
     * {@code w[0..4]} 只依赖核中心粗格（核偏移固定、各粗格身份只依赖 (seed, 该粗格)）⇒ 同一粗格内
     * 所有列的权重向量<b>数学恒等</b>，缓存终值与旧"逐列 69 次查表累积"逐位相同（同一批加法同一
     * 次序）。每列成本从 69 次 HashMap 查表降为 1 次。纪律同 Profile 各表：线程私有、上限
     * {@link #VAR_CELL_CACHE_CAP}、超限整清重算值不变。
     */
    private static final int VAR_CELL_CACHE_CAP = 65536;
    private static final ThreadLocal<HashMap<Long, HashMap<Long, double[]>>> VAR_CELL_CACHE = ThreadLocal
        .withInitial(HashMap::new);

    /**
     * 每线程每 seed 的<b>沼泽腹地粗格布尔缓存</b>（v1.20.42 P22 A3）：{@link #swampInteriorAt} 的
     * 判定量只依赖 (seed, 粗格) ⇒ 同一粗格内所有列恒等，缓存终值零噪声求值。纪律同
     * {@link #VAR_CELL_CACHE}：线程私有、上限 {@link #SWAMP_INTERIOR_CACHE_CAP}、超限整清重算值不变。
     */
    private static final int SWAMP_INTERIOR_CACHE_CAP = 65536;
    private static final ThreadLocal<HashMap<Long, HashMap<Long, Boolean>>> SWAMP_INTERIOR_CACHE = ThreadLocal
        .withInitial(HashMap::new);

    // —— 丘陵场（RTG hills 模板）——
    /** 丘陵门噪声域盐（波长 320；森林/草原共场不同带）。 */
    private static final long S_HILL_GATE = 0x6811C21DL;
    /** 丘陵大波域盐（RTG 波长 150）。 */
    private static final long S_HILL_BIG = 0x6811C22DL;
    /** 丘陵小波域盐（RTG 波长 55）。 */
    private static final long S_HILL_SMALL = 0x6811C23DL;
    /**
     * 森林丘陵幅度（门全开 ×形状峰值）。
     * <p>
     * <b>v1.20.41 P20 S4：14.0 → 20.0</b>（需求 4「齿轮森林……起伏再更大一些」）。该量必须走<b>形态场</b>
     * 而不是 {@code RELIEF_AMPLITUDE_BY_ROSTER} 的档表：森林档 1.70 已因"1.90 档实测 1259 列触 y=40 下界
     * 超预算"被钉死（P20 §13 C2/§16，档表侧不可再抬），而 §6.1 R1-⑥ 要的森林绝对 sd ≥15.5 是档表量级
     * 取不到的（S2 实跑：三频臂 9.688、全链 13.145 ⇒ 形态净增 8.885，sd 目标要求净增 ≥12.1）。
     * 校准域 ∈ {18,20,22}（P20 §8）。<b>S4 续跑片实跑后仍取 20.0（不抬 22.0）——抬幅被 {@link
     * #DELTA_CAP} 封顶吃掉</b>：20.0 档实测聚合 sd 森 <b>14.044</b>（{@code plan/tmp/p20-s4/relief-run1.out}，
     * 16 seed × 3072² 步距 4、去河/湖列），把本值改到域上沿 22.0 后变体 delta 场 sd 只从
     * <b>10.682 → 10.697</b>（{@code probe-run1/probe-run2.out}，roster1 delta &gt;24 的列已占 15.3%
     * ⇒ 再抬只是把更多列压到 32 的软顶上，观感是更多平顶、sd 几乎不动）。⇒ §6.1 R1-⑥ 的 ≥15.5
     * 在本片可动常量域（本值 ≤22、{@code DELTA_CAP}=32、岭脊门 0.72/0.16、{@code RIDGE_AMP}=12
     * 均为 §5 字面值）内<b>不可达</b>；该片按实跑数上报，未自行放宽该带（证据与三选一处置见
     * {@code plan/tmp/p20-s4/RECONCILE-SUM.md} 第 3 条）。
     */
    private static final double HILL_AMP_FOREST = 20.0D;
    /** 草原轻微丘陵幅度（门全开 ×形状 ⇒ +2..+6 常态带）。 */
    private static final double HILL_AMP_STEPPE = 10.0D;

    // —— 山地变体（ATG plateau 模板，仅森林）——
    /** 山地门噪声域盐（波长 512，稀有低频门）。 */
    private static final long S_MTN_GATE = 0x6811C24DL;
    /** 山地碎坡噪声域盐（波长 40）。 */
    private static final long S_MTN_ROUGH = 0x6811C25DL;
    /** plateau 双档下檐（ATG ledgelevel 低档）。 */
    private static final double MTN_LEDGE_LOW = 88.0D;
    /** plateau 双档上檐（高档 = 低档 + 16 ⇒ 104，恰在软削顶哨兵之下）。 */
    private static final double MTN_LEDGE_HIGH = 104.0D;
    /**
     * 山地门下檐（<b>v1.20.41 由 0.30 放宽到 0.24</b>，带宽 0.30→0.36 ⇒ 覆盖率 P≈10% → ≥15%，
     * 需求 4「山地门覆盖率」子句；出处 = {@code :229-230} 注释自陈的 valueNoise 实测边际）。
     */
    private static final double MTN_GATE_LO = 0.24D;
    /** 山地门带宽（同上；上檐 = {@link #MTN_GATE_LO}+本值 = 0.60 未动）。 */
    private static final double MTN_GATE_SPAN = 0.36D;

    // —— 岭脊变体（v1.20.41 P20 S4 新增，仅森林；RTG ridged 形状范式）——
    /** 岭脊门噪声域盐（波长 {@link #RIDGE_SCALE}=188，介于丘陵门 320 与山地门 512 之间）。 */
    private static final long S_RIDGE_GATE = 0x6811C2B1L;
    /** 岭脊门波长（188 % 16 = 12 ⇒ 合 P20 §3 H-1「新增波长不得取 16 倍数」）。 */
    private static final double RIDGE_SCALE = 188.0D;
    /** 岭脊门下檐（作用于 ridged 形状 {@code 1−|n|}：脊线在 n≈0 处形状值→1 ⇒ 窄带成脊）。 */
    private static final double RIDGE_GATE_LO = 0.72D;
    /** 岭脊门带宽（满门 = |n|≤0.12、缓入到 |n|≤0.28 ⇒ 覆盖率约 12%，本轮取值实机校准）。 */
    private static final double RIDGE_GATE_SPAN = 0.16D;
    /**
     * 岭脊幅度（丘陵 20 与山地 +14..+26 之间的中间档；需求 4 的"第三形态"）。
     * <p>
     * <b>v1.20.42 P22 A4 校准 12.0 → 18.0</b>（任务包授权域 {14,16,18} 的上沿；次级杠杆按探针证据启用
     * 的<b>唯一一个</b>）。可达性曲线（16 seed × 3072² 步距 4 去河/湖列，{@code plan/tmp/p22-a4/}
     * relief-L*.out）：L1 负瓣单独 14.465 → amp14 14.552 → amp16 14.668 → <b>amp18 14.807</b>（每 +2 近线性
     * +0.09..+0.14），且岭脊正 delta 抵消部分谷地下挖 ⇒ 触 y=40 地板列反降（556→539，预算 943）。
     * <b>岭脊门带放宽（0.72→0.66）被同表证伪</b>：单独臂 14.337 &lt; 14.465、合臂 14.691 &lt; 14.807——
     * 门放宽把低尾脊列抬向均值 ⇒ spread 收缩（钳制列也降 556→435，同机制互证）；<b>DELTA_CAP 32→36
     * 不启用</b>：全部 rung 高度域上沿已 = 108 哨兵（顶部路径被哨兵吃死，与 P20 §21-B ② 同判）。
     */
    private static final double RIDGE_AMP = 18.0D;

    // —— 森林谷地负瓣（v1.20.42 P22 A4 新增，仅森林；"山脉丘陵状"的下侧形态）——
    /**
     * 森林谷地场域盐（波长 {@link #FOREST_VALLEY_SCALE}=139）。盐值在本类盐段<b>尾追加</b>：
     * 续 {@code 0x6811C2B1} 起 ×0x12 等差族的下一未用值（上一已用 = 灌木簇场② {@code 0x6811C365}，
     * +0x12 ⇒ 本值），不与既有任何域盐重合。
     */
    private static final long S_FOREST_VALLEY = 0x6811C377L;
    /**
     * 谷地场波长（139 % 16 = 11 ⇒ 合 P20 §3 H-1「新增波长不得取 16 倍数」）。量级取在岭脊门 188 与
     * 丘陵小波 55 之间——谷地要比岭脊更细（冲沟尺度）但比碎坡（40）粗，避免与山地碎坡噪声在视觉上混频。
     */
    private static final double FOREST_VALLEY_SCALE = 139.0D;
    /**
     * 谷地覆盖门下檐（作用于<b>负</b>瓣 {@code s01((−n−0.45)/0.25)}：满门 n ≤ −0.70、缓入到 n ≤ −0.45）。
     * <b>计划估计 vs 实测（A4 探针披露项）</b>：计划按三角边际估覆盖 ~12-18%；实测 bilinear valueNoise
     * 边际更尖，any(门&gt;0) = <b>10.10%</b>、half(门≥0.5) = 5.29%、满门 2.28%（16 seed × 1024² 步距 4，
     * {@code plan/tmp/p22-a4/}）。门式按任务包字面落地不漂移，覆盖差入交付披露。
     */
    private static final double FOREST_VALLEY_GATE_LO = 0.45D;
    /** 谷地覆盖门带宽。 */
    private static final double FOREST_VALLEY_GATE_SPAN = 0.25D;
    /**
     * 谷地基础下挖（满门深度档 {@code −(4.0+6.0·门)·门} 的下沿 = −4；外侧乘门 = 草原低地/深水池同款
     * 软门纪律——门关死列 delta 精确 0，缓入环无硬崖）。
     * <b>为什么走下侧不走顶部（v1.20.42 P22 A4 的路径论证）</b>：顶部三面被封死——{@code HILL_AMP_FOREST}
     * 20 已在授权域上沿（22.0 实测只 +0.001 sd）、15.28% 森林列被 {@link #DELTA_CAP}=32 软顶吃掉、顶部路径
     * 被 {@link #HEIGHT_SENTINEL}=108 吃死（高度域已 [0,108]）；而下侧预算余量充足——触 y=40 地板现状
     * 145 列 vs 预算带 943（P20 §21-A 证据），负 delta 不受 {@code softMin(·,32,10)} 影响（|a−b|≥k 时
     * 逐位等于 min ⇒ 负值直通）。
     */
    private static final double FOREST_VALLEY_BASE = 4.0D;
    /** 谷地按门加深档（满门最深 −10 ⇒ 与岭脊 +18 形成谷-脊相对高差 ≥28 格的"山脉丘陵状"读数）。 */
    private static final double FOREST_VALLEY_SPAN = 6.0D;

    // —— 荒漠沙丘（RTG dunes + TerrainHLDunes domain-warp 模板）——
    /** 沙丘扭曲噪声域盐（波长 20 扭曲 + 波长 17 碎浪共种子换波长）。 */
    private static final long S_DUNE_WARP = 0x6811C26DL;
    /** 沙丘强度场域盐（波长 224）。 */
    private static final long S_DUNE_STRENGTH = 0x6811C27DL;
    /** 沙丘主波域盐（波长 60）。 */
    private static final long S_DUNE_MAIN = 0x6811C28DL;
    /** 主波增益（d=main·st·2.6；对准三角边际定标使强度场高区垄峰进 +4..+8）。 */
    private static final double DUNE_MAIN_GAIN = 2.6D;
    /** 扭曲幅度（TerrainHLDunes 口径 4-8 取 6，x 向方向性）。 */
    private static final double DUNE_WARP_AMP = 6.0D;
    /** 垄脊二次项（p·(p·QUAD+LIN)，p=−fold ∈[0,|d|]；强度场高区峰上界 ~8，值噪声三角边际下的定标）。 */
    private static final double DUNE_RIDGE_QUAD = 3.0D;
    /** 垄脊线性项（保典型列垄高进入 +2..+4 可见带——纯二次会被三角边际压扁）。 */
    private static final double DUNE_RIDGE_LIN = 1.8D;

    // —— 风蚀山体 + 风蚀柱座台（v1.20.41 P20 S4 新增，roster 2；RTG terrainBryce 倒数式范式）——
    /** 风蚀四层 λ13 域盐（最高频层，柱身"细而陡"的来源；13 % 16 = 13 ✔）。 */
    private static final long S_BRYCE_0 = 0x6811C2C3L;
    /** 风蚀四层 λ27 域盐（27 % 16 = 11 ✔）。 */
    private static final long S_BRYCE_1 = 0x6811C2D5L;
    /** 风蚀四层 λ53 域盐（53 % 16 = 5 ✔）；该层<b>有符号</b>值另作风蚀柱落点场（零额外求值）。 */
    private static final long S_BRYCE_2 = 0x6811C2E7L;
    /** 风蚀四层 λ107 域盐（107 % 16 = 11 ✔）；该层<b>有符号</b>值另作山体门（零额外求值）。 */
    private static final long S_BRYCE_3 = 0x6811C2F9L;
    /** 四层波长（RTG {@code /2,1,4,8} 的倍频比 ≈2 ⇒ 本仓从 13 起：13/27/53/107，全部 %16≠0）。 */
    private static final double BRYCE_SCALE_0 = 13.0D;
    private static final double BRYCE_SCALE_1 = 27.0D;
    private static final double BRYCE_SCALE_2 = 53.0D;
    private static final double BRYCE_SCALE_3 = 107.0D;
    /** 四层权重 1 : 1/2 : 1/4 : 1/8（RTG 各层幅度 0.5/0.2/4/2 的"高频主导"关系在本仓值域下的等比形）。 */
    private static final double BRYCE_W1 = 0.5D;
    private static final double BRYCE_W2 = 0.25D;
    private static final double BRYCE_W3 = 0.125D;
    /** 倒数式分母下限（RTG {@code n = height/sn} 的 sn→0 上凸行为的有界化）。 */
    private static final double BRYCE_FLOOR = 0.35D;
    /** 倒数式分母对 sn 的斜率（RTG 四层求和后的除数量级）。 */
    private static final double BRYCE_SLOPE = 4.0D;
    /**
     * 倒数式增益（计划 S4 表只给了形状 {@code 1/(0.35+4.0·sn)} 与限幅 22，未给增益；本值由
     * "门内典型列须达 ≥12 格"反解：gate=1 时 sn≈0.10 → 26/0.75 = 34.7 → 被 22 封顶，sn≈0.35 →
     * 26/1.75 = 14.9 ⇒ 12–22 的稀有条带）。<b>S4 续跑片实跑校准 18.0 → 26.0</b>：18.0 档实测
     * roster2 内 "变体 delta ≥ 12" 的列占比只有 <b>0.252%</b>（{@code plan/tmp/p20-s4/probe-run1.out}，
     * 16 seed × 1024² 步距 4），低于 §5 S4 判据 3 立的带下界 0.5%（"山体应稀有但不是零"）⇒ 按
     * §6.1 的处置纪律"回到 §5 的常量域重解"，在本常量自陈的校准域内抬增益，不动门带 0.60/0.20
     * 与限幅 22（两者是 §5 字面值）。抬幅后聚合 sd 沙 7.823 → 见 relief-run2.out；<b>仍须实机目检</b>。
     */
    private static final double BRYCE_GAIN = 26.0D;
    /** 风蚀山体限幅（与山地变体 +14..+26 同阶而低于 {@link #DELTA_CAP}=32；过 softMin 保 C1）。 */
    private static final double BRYCE_CAP = 22.0D;
    /** 风蚀山体限幅的 softMin 圆角（k=4，与 {@link #HEIGHT_SENTINEL} 同款 ⇒ 无硬折点）。 */
    private static final double BRYCE_CAP_K = 4.0D;
    /** 风蚀山体门下檐（作用于 λ107 层<b>有符号</b>值；RTG 的 {@code sn<6} 绝对门不可直译 ⇒ 换门带）。 */
    private static final double BRYCE_GATE_LO = 0.60D;
    /** 风蚀山体门带宽（满门 P(n₄≥0.80) ≈1–2%、缓入到 0.60；覆盖率本轮取值、实机校准）。 */
    private static final double BRYCE_GATE_SPAN = 0.20D;
    /** 风蚀柱座台幅度（需求 5 的地形侧项；柱身 feature 归 populate 侧 S6，其落点走 {@link #windSpineSiteAt}）。 */
    private static final double SPINE_PEDESTAL = 1.5D;
    /** 座台/落点场门下檐（λ53 层有符号值；0.82 给 ≈0.5–1% 列覆盖，与 1/12 chunk 的柱密度同量级）。 */
    private static final double SPINE_GATE_LO = 0.82D;
    /** 座台门带宽。 */
    private static final double SPINE_GATE_SPAN = 0.10D;

    // —— 灌木成簇场（v1.20.41 P20 S6 新增，需求 6「锈蚀草原再增加一些低地、灌木群」的 populate 侧）——
    /**
     * 灌木簇场①域盐（波长 {@link #SHRUB_CLUSTER_SCALE_A}=41；41 % 16 = 9 ✔ 合 P20 §3 H-1）。
     * 盐值续 {@code 0x6811C2B1} 起的 ×0x12 等差族（岭脊/风蚀四层/低地/沼泽各支同族），不与既有域重合。
     */
    private static final long S_SHRUB_CLUSTER_A = 0x6811C353L;
    /** 灌木簇场②域盐（波长 {@link #SHRUB_CLUSTER_SCALE_B}=83；83 % 16 = 3 ✔）。 */
    private static final long S_SHRUB_CLUSTER_B = 0x6811C365L;
    /** 簇场①波长（P20 §5 S6 的字面值 41；41 % 16 = 9 ✔）。 */
    private static final double SHRUB_CLUSTER_SCALE_A = 41.0D;
    /** 簇场②波长（P20 §5 S6 的字面值 83；83 % 16 = 3 ✔）。 */
    private static final double SHRUB_CLUSTER_SCALE_B = 83.0D;
    /** 簇门带下檐／带宽（P20 §5 S6 的字面值：簇门 {@code s01((n−0.55)/0.18)} ⇒ 满门 n ≥ 0.73）。 */
    private static final double SHRUB_CLUSTER_GATE_LO = 0.55D;
    private static final double SHRUB_CLUSTER_GATE_SPAN = 0.18D;

    // —— 草原低地（v1.20.41 P20 S4 新增，roster 0）——
    /** 低地场域盐（波长 {@link #STEPPE_LOW_SCALE}=93）。 */
    private static final long S_STEPPE_LOW = 0x6811C30BL;
    /** 低地场波长（93 % 16 = 13 ✔；意图 96 但 96 是 16 倍数 ⇒ 按 H-1 故意避开）。 */
    private static final double STEPPE_LOW_SCALE = 93.0D;
    /** 低地覆盖门下檐（作用于<b>负</b>瓣：满门 d≤−0.5、缓入到 d≤−0.2 ⇒ 覆盖率 ≈12%）。 */
    private static final double STEPPE_LOW_GATE_LO = 0.20D;
    /** 低地覆盖门带宽。 */
    private static final double STEPPE_LOW_GATE_SPAN = 0.30D;
    /** 低地基础下挖（需求 6"低地"−3..−6 的下沿，出处 = EBXL {@code Height(−0.2,0.2)} 折算）。 */
    private static final double STEPPE_LOW_BASE = 3.0D;
    /** 低地按形状加深的第二档（同一列最深 −6）。 */
    private static final double STEPPE_LOW_SPAN = 3.0D;

    // —— 沼泽/遗忘之川（ATG swamp 模板；三档水体分支的地形侧 = v1.20.41 P20 S4）——
    /** 夹持门噪声域盐（波长 256）。 */
    private static final long S_SWAMP_CLAMP = 0x6811C29DL;
    /** 表面池（微洼）噪声域盐（床纹波长 {@link #SWAMP_POOL_BED_WAVE}）。 */
    private static final long S_SWAMP_POOL = 0x6811C2ADL;
    /**
     * 沼泽<b>表面池床纹的活波长</b>（格；= 改造前 {@code variantAdjustment} 里的<b>内联字面量 48</b>，
     * P20 §14.3 对 §13 C9 的改判：实测该 48 <b>从未退役</b>，一直作为活波长在用，而
     * {@code GTSRVoronoiRiverField.SWAMP_POOL_BED_SCALE} 只是它的<b>零消费影子</b>。本轮把内联值
     * 提名为本常量 ⇒ 单一真值；影子由 P20 S7 删除，记录须原文点名"活波长 48 现以具名常量存在于
     * {@code TerrainVariants}"，<b>不得</b>写成"48 已退役"）。
     * <p>
     * <b>命名（P20 S4 续跑片的主代理裁定，覆盖 §14.3 的字面写法）</b>：本常量是<b>活波长</b>，
     * 与 river 侧那个<b>同名死常量无关</b>（后者<b>已由 P20 S7 删除</b> ⇒ 全仓现在只剩本页提到那个符号名，
     * 它不再指向任何在场声明；活波长只有本页这一个真值）。前身片按 §14.3 原文把它命名成
     * {@code SWAMP_POOL_BED_SCALE}，与 {@code GTSRVoronoiRiverField} 的死常量同符号名 ⇒ 同一轮内
     * 所有 grep 型判据/前置检查（含 S7-2 的 {@code grep -rn "SWAMP_POOL_BED_SCALE" tools}）歧义，
     * 故改名 {@code SWAMP_POOL_BED_WAVE}。§14.3 的三项实质要求（内联 48 提名、单一真值、
     * 不得写成"48 已退役"）不受影响。
     * <p>
     * 48 % 16 = 0 = <b>既存</b>的 chunk 对齐条纹偏离（P20 §3 H-1 只约束<b>新增</b>波长；回改等于
     * 全形态重排 + 全判据重钉，未获授权 ⇒ 登记在 §12 版 2 校准，本轮不动）。
     */
    private static final double SWAMP_POOL_BED_WAVE = 48.0D;
    /** 表面池床门下檐（v1.20.41 由 0.15 放宽 ⇒ 覆盖↑，需求 3"水池更多"）。 */
    private static final double SWAMP_POOL_GATE_LO = 0.12D;
    /** 表面池床带宽（v1.20.41 由 0.35 改 0.43 ⇒ 满门进 0.55；旧带 [0.15,0.50] 原文见类注释）。 */
    private static final double SWAMP_POOL_GATE_SPAN = 0.43D;
    /**
     * 表面池最大下挖（×f ⇒ ≤2.0）。<b>v1.20.41 由 2.6 降到 2.0</b>：三档可分辨性要求表面池稳定停在
     * 1–2 层水档，与深水池（≥5 层）之间留 §8 自定的 4 格分水岭。
     */
    private static final double SWAMP_POOL_DEPTH = 2.0D;
    /** 深水池床纹场域盐（波长 {@link #S_SWAMP_DEEP_BED}）。 */
    private static final long S_SWAMP_DEEP = 0x6811C31DL;
    /**
     * 深水池<b>床纹波长</b>（格；37 % 16 = 5 ✔ 合 H-1）。名字按 P20 §13 C9 / §14.3 的裁定原文保留
     * （{@code S_} 前缀在此承载的是<b>波长</b>而非域盐——域盐是同组的 {@link #S_SWAMP_DEEP}；
     * 与本类其它 {@code S_*} 域盐常量的形不一致是裁定原名的既成事实，不改名以免与裁定脱钩）。
     */
    private static final double S_SWAMP_DEEP_BED = 37.0D;
    /** 深水池门下檐（满门 P(n≥0.76) ≈1%、缓入 0.62 ⇒ 本轮覆盖 ≈5%，实机校准）。 */
    private static final double SWAMP_DEEP_GATE_LO = 0.62D;
    /** 深水池门带宽。 */
    private static final double SWAMP_DEEP_GATE_SPAN = 0.14D;
    /** 深水池基础下挖（需求 3"深水池"的水深下沿 5 层）。 */
    private static final double SWAMP_DEEP_BASE = 5.0D;
    /** 深水池按门加深档（满门下挖 −9 ⇒ 水深 5–9）。 */
    private static final double SWAMP_DEEP_SPAN = 4.0D;
    /** 水沼地（半淹档）场域盐（波长 {@link #SWAMP_MARSH_SCALE}）。 */
    private static final long S_SWAMP_MARSH = 0x6811C32FL;
    /** 水沼地波长（61 % 16 = 13 ✔；与 {@code SWAMP_POOL_INTERVAL}=220 的微池水网正交）。 */
    private static final double SWAMP_MARSH_SCALE = 61.0D;
    /** 水沼地门下檐（满门 P(n≥0.65) ≈6%、缓入 0.30 ⇒ 半淹带大面积，需求 3 的 r 20–45 量级）。 */
    private static final double SWAMP_MARSH_GATE_LO = 0.30D;
    /** 水沼地门带宽。 */
    private static final double SWAMP_MARSH_GATE_SPAN = 0.35D;
    /** 水沼地基础下挖（贴水线下 0.5）。 */
    private static final double SWAMP_MARSH_BASE = 0.5D;
    /** 水沼地按门加深档（满门 −1.5 ⇒ 水深 0–1 的半淹）。 */
    private static final double SWAMP_MARSH_SPAN = 1.0D;
    /** 泥丘场域盐（波长 {@link #SWAMP_HUMMOCK_SCALE}）。 */
    private static final long S_SWAMP_HUMMOCK = 0x6811C341L;
    /** 泥丘波长（113 % 16 = 1 ✔）。 */
    private static final double SWAMP_HUMMOCK_SCALE = 113.0D;
    /** 泥丘门下檐（满门 P(n≥0.75) ≈2%、缓入 0.45）。 */
    private static final double SWAMP_HUMMOCK_GATE_LO = 0.45D;
    /** 泥丘门带宽。 */
    private static final double SWAMP_HUMMOCK_GATE_SPAN = 0.30D;
    /** 泥丘抬升下/上沿（需求 3"另加两种分支形态"之一：+2.0..+4.5）。 */
    private static final double SWAMP_HUMMOCK_BASE = 2.0D;
    private static final double SWAMP_HUMMOCK_SPAN = 2.5D;
    /**
     * 炭屑滩门（复用<b>水沼地场同域的负瓣</b>，零额外求值、与半淹档空间互斥）：
     * {@code s01((−n−0.55)/0.25)} ⇒ 满门 P(n≤−0.80) ≈1%。
     */
    private static final double SWAMP_CHAR_GATE_LO = 0.55D;
    private static final double SWAMP_CHAR_GATE_SPAN = 0.25D;
    /** 炭屑滩下挖（−0.5..−1.0；<b>只改地形</b>，铺料归 populate 侧 P20 S6）。 */
    private static final double SWAMP_CHAR_BASE = 0.5D;
    private static final double SWAMP_CHAR_SPAN = 0.5D;
    /**
     * <b>非水体项（④ 泥丘 + ⑤ 炭屑滩）相加后允许的最大下挖</b>（格；P20 §21-D 裁定：两项<b>保持相加</b>
     * 但<b>限幅 ≤1.5 格</b>，使其无法把浅档列穿透到 ≥4 格的 4 格分水岭以下）。
     * <p>
     * <b>限幅只作用在下挖（负）侧</b>，抬升侧不设 1.5 的上限——这是本方法与 §21-D 字面（"限幅到 ≤1.5 格"）
     * 的唯一措辞偏离，理由是两条已定方案互斥：④ 泥丘的 {@code +2.0..+4.5} 是 P20 §5 S4 表里的<b>字面
     * 计划值</b>（需求 3"另加两种分支形态"之一，见 {@link #SWAMP_HUMMOCK_BASE}/{@link #SWAMP_HUMMOCK_SPAN}），
     * 双边夹到 ±1.5 会把它削成 +1.5 而改动已选方案；而泥丘项恒为<b>正</b>（抬升），在数学上不可能参与
     * "浅档列被挖到 ≥4"这条穿透（穿透只可能来自负项），故下挖侧限幅已充分实现该裁定的目的。
     * 实测炭屑滩自身最深 −1.0 ⇒ 本常量在现有参数下<b>不改变任何一列</b>，是把"非水体项不能穿透"从
     * 巧合升格为构造性保证的哨兵（改 SWAMP_CHAR_* 时它继续挡住）。
     */
    private static final double SWAMP_NONWATER_DIG_MAX = 1.5D;
    /**
     * 沼泽夹持目标相对海平面的<b>下檐</b>（<b>v1.20.41 由 0.5 抬到 0.0</b> = target 由 67.5 →
     * {@link ProsperityTerrainProfile#SEA_LEVEL}；出处 = EBXL {@code BiomeMarsh} "root 0.0~0.1 让
     * 半数列自然淹水"的机制解——贴水面 + 水沼地浅扇形下挖表达半淹，回填门仍在水面侧）。
     */
    private static final double SWAMP_CLAMP_UNDERSHOOT = 0.0D;
    /** 遗忘之川微起伏下挖上限（±1..2 档）。 */
    private static final double SANZU_POOL_DEPTH = 1.6D;

    /** 三档水体分支只在 roster 3（汽雾/喷气沼泽）出现——与 {@code swampLakeAt} 的 roster 门同口径。 */
    private static final int SWAMP_ROSTER = 3;
    // —— 沼泽边缘门（v1.20.42 P22 A3：三档水体 + 微池避群系边缘）——
    /**
     * 沼泽水体的<b>边缘净空</b>（方块）：水体只落在"距群系边缘 ≥ 本值"的腹地列——现状三档/微池
     * 水面会一路铺到 coarse 群系边界，被边界截断后水从断口外流到处都是（A3 探针改前基线：边缘截断
     * 水列 1069 / 8 seed×512²）。<b>v1.20.42 P22 A3 新增</b>；校准域 {@code N ∈ {12,16,24}}
     * （粗格半径 R ∈ {3,4,6}），本轮取 16。
     */
    private static final int SWAMP_EDGE_MARGIN_BLOCKS = 16;
    /**
     * 边缘门的粗格 Chebyshev 半径（派生式 = {@code ceil(N / COARSE_BLOCK_SCALE)}，N=16 ⇒ R=4）。
     * 边缘定义在 coarse 1:4 身份面：见 {@link #swampInteriorAt}。
     */
    private static final int SWAMP_EDGE_RADIUS_CELLS = (int) Math
        .ceil(SWAMP_EDGE_MARGIN_BLOCKS / (double) GTSRGenLayerChain.COARSE_BLOCK_SCALE);
    /** 分档判据：门值 ≥ 本值才算"该档成立"（0.5 = smoothstep 的中点，两侧对称、不随带宽漂移）。 */
    private static final double TIER_MIN = 0.5D;
    /** 档：无水体分支。 */
    public static final int SWAMP_TIER_NONE = 0;
    /** 档 1：表面水池（1–2 层）。 */
    public static final int SWAMP_TIER_POOL = 1;
    /** 档 2：深水池（5–9 层）。 */
    public static final int SWAMP_TIER_DEEP = 2;
    /** 档 3：水沼地（0–1 层半淹、大面积）。 */
    public static final int SWAMP_TIER_MARSH = 3;

    /** {@link #swampGates} 的槽位：表面池门。 */
    private static final int SWG_POOL = 0;
    /** 槽位：深水池门。 */
    private static final int SWG_DEEP = 1;
    /** 槽位：水沼地门。 */
    private static final int SWG_MARSH = 2;
    /** 槽位：泥丘门。 */
    private static final int SWG_HUMMOCK = 3;
    /** 槽位：炭屑滩门。 */
    private static final int SWG_CHAR = 4;
    /**
     * 槽位：<b>本列的水体档位</b>（{@link #SWAMP_TIER_NONE}…{@link #SWAMP_TIER_MARSH} 的 int 值以 double
     * 存放）。<b>P20 §21-D 的单点分流落点</b>：三档"取哪一档"的 {@code ≥ TIER_MIN} 门比较<b>只在本类的
     * {@link #swampGates} 里写这一遍</b>，{@link #swampTierAt}（回填侧真值）与 {@link #variantAdjustment}
     * 的 delta 侧（地形侧真值）都读这一个槽 ⇒ 两侧不可能各写一遍门比较而漂移。
     */
    private static final int SWG_TIER = 5;
    private static final int SWG_COUNT = 6;
    /**
     * 沼泽门值的线程私有 scratch（<b>零分配</b>：本类每列都会被调，delta 组合与分档判定共用一份，
     * 免得两处各算一遍形成"同式复算"的第二真值）。纪律同 {@link #VAR_CELL_CACHE}：线程私有、
     * 就地取用、不跨列持有。最后一槽 {@link #SWG_TIER} 承载单点分流出的档位（P20 §21-D）。
     */
    private static final ThreadLocal<double[]> SWAMP_GATE_SCRATCH = ThreadLocal
        .withInitial(() -> new double[SWG_COUNT]);

    // —— 软削顶（A 模板）——
    /**
     * 加权总 delta 的光滑上界（softMin k=10；≤22 逐位不动，渐近 32）。
     * <b>v1.20.41 随森林 delta 上抬 1.43×（{@link #HILL_AMP_FOREST}）同步 26→32、k 8→10</b>；
     * {@link #softMin} 的定义保证 |a−b| ≥ k 时逐位等于 min(a,b) ⇒ "δ≤22 逐位不动"的构造性保证
     * 与旧口径同形（旧值 δ≤18），只是上移 4。
     */
    private static final double DELTA_CAP = 32.0D;
    /** 削顶圆角 k（与 {@link #DELTA_CAP} 同批由 8 抬到 10）。 */
    private static final double DELTA_CAP_K = 10.0D;
    /** 结果高度的光滑哨兵（softMin k=4；≤104 逐位不动，恒 &lt;108 ⇒ 110 钳制零截平）。 */
    private static final double HEIGHT_SENTINEL = 108.0D;

    /** 核半径（粗格；与 ProsperityTerrainProfile.AMP_KERNEL_RADIUS 同值 5 ⇒ 直径 11 粗格）。 */
    private static final int VAR_KERNEL_RADIUS = 5;

    /** 核参与格点（i²+j²&lt;r²；与 Profile AMP_KERNEL 同式同 69 点，d=r 权重恰 0 不入表）。 */
    private static final int[] VAR_KERNEL_DX;
    private static final int[] VAR_KERNEL_DZ;
    /** 归一核权（Σw = 1）。 */
    private static final double[] VAR_KERNEL_W;

    static {
        final int r = VAR_KERNEL_RADIUS;
        final int rr = r * r;
        int n = 0;
        for (int i = -r; i <= r; i++) {
            for (int j = -r; j <= r; j++) {
                if (i * i + j * j < rr) {
                    n++;
                }
            }
        }
        VAR_KERNEL_DX = new int[n];
        VAR_KERNEL_DZ = new int[n];
        VAR_KERNEL_W = new double[n];
        double wSum = 0.0D;
        int k = 0;
        for (int i = -r; i <= r; i++) {
            for (int j = -r; j <= r; j++) {
                final int d2 = i * i + j * j;
                if (d2 >= rr) {
                    continue;
                }
                final double t = 1.0D - Math.sqrt(d2) / r;
                final double w = t * t * (3.0D - 2.0D * t);
                VAR_KERNEL_DX[k] = i;
                VAR_KERNEL_DZ[k] = j;
                VAR_KERNEL_W[k] = w;
                wSum += w;
                k++;
            }
        }
        for (k = 0; k < n; k++) {
            VAR_KERNEL_W[k] /= wSum;
        }
    }

    private TerrainVariants() {}

    /**
     * {@code h0} → 群系变体修正（<b>P19 §H 新增的形态场主出口</b>；调用点仅
     * {@code ProsperityTerrainProfile.heightCore} 的 h0 计算后、两段式压低前一处）。
     * v1.20.41 起本类另有<b>三个只读公开出口</b>（{@link #swampTierAt}、{@link #windSpineSiteAt}、
     * {@link #shrubClusterAt}），供 populate 侧的回填门、柱 feature 与灌木成簇取同一真值——它们
     * <b>不</b>参与高度链（{@link #shrubClusterAt} 是本轮新增、{@code variantAdjustment} 一行不读它），
     * 也就不是第二个消费点。
     * <p>
     * 返回修正后的整型高度：均匀区/门关区逐位等于入参 {@code h0}（见类注释"全软门纪律"），
     * 变体列 = {@code round(h0 + softMin(加权 delta, 32, 10))} 再过 {@code softMin(·,108,4)}。
     * 河谷两段式/巨湖压低/低地防抬升/[40,110] 钳制全部在下游原样作用——本方法只改 h0 这一层的
     * 形态，不碰任何压低语义，也<b>不置水</b>（水体回填门见类注释契约段与 {@link #swampTierAt}）。
     *
     * @param worldSeed   世界种子（与 {@code heightAt} 同一口径，不含 def.seedSalt）
     * @param x           列 x
     * @param z           列 z
     * @param rosterIndex 调用方已解析的中心粗格名册下标（0..4）；<b>只作域纪律</b>
     *                    （越界/无身份 ⇒ 零变体，与振幅默认档同款的越界回退，非身份等值抑制）——
     *                    变体选择走核加权混合而非单点身份，理由见类注释"跨变体过渡"
     * @param h0          三频缓丘基线高度（BASE + round(relief)，改造前口径）
     * @return 变体修正后的 h0（均匀区逐位等于入参）
     */
    public static int variantAdjustment(long worldSeed, int x, int z, int rosterIndex, int h0) {
        if (rosterIndex < 0 || rosterIndex >= ROSTER_SLOTS) {
            return h0;
        }
        // P19 U8：权重向量按粗格缓存（同一粗格内所有列恒等，见 VAR_CELL_CACHE 注释）；只读不写。
        final double[] w = weightsAt(worldSeed, x, z);
        double delta = 0.0D;
        // —— 丘陵族（草原 w0 / 森林 w1 共场不同档；RTG hills 模板）——
        if (w[0] > 0.0D || w[1] > 0.0D) {
            final double gh = hillGateNoise(worldSeed, x, z);
            // 门带标定对准 valueNoise 实测边际（三角型、集中在 0：P(gh>0.30)≈10%、P(gh>0.5)≈4%），
            // 阈值放进气泡 working 区而不是薄上尾（初版 0.30/0.55 段实测覆盖率 <3%，探针归因后重标）。
            final double gateF = s01((gh - 0.08D) / 0.27D);
            final double gateS = s01((gh - 0.20D) / 0.30D);
            final boolean hillsOn = gateF > 0.0D || gateS > 0.0D;
            final double m = hillsOn ? hillsShape(worldSeed, x, z) : 0.0D;
            if (w[0] > 0.0D) {
                delta += w[0] * (HILL_AMP_STEPPE * m * gateS);
                // —— 低地支路（v1.20.41 需求 6：半空间折叠只取负瓣 + 覆盖门；与丘陵同域叠加）——
                // 折叠式与沙丘 (d−|d|)/2 同款：d≥0 ⇒ 精确 0 ⇒ 该支路在门带外逐位不改变 delta。
                final double dl = GTSRWorldgenHash
                    .valueNoise(worldSeed ^ S_STEPPE_LOW, x / STEPPE_LOW_SCALE, z / STEPPE_LOW_SCALE);
                final double lowFold = (dl - Math.abs(dl)) * 0.5D; // ∈ [−0.5, 0]
                final double lowShape = Math.min(1.0D, -2.0D * lowFold); // 形状 0..1（d≤−0.5 取满）
                final double lowGate = s01((-dl - STEPPE_LOW_GATE_LO) / STEPPE_LOW_GATE_SPAN);
                delta += w[0] * (-(STEPPE_LOW_BASE + STEPPE_LOW_SPAN * lowShape) * lowGate);
            }
            if (w[1] > 0.0D) {
                double forest = HILL_AMP_FOREST * m * gateF;
                // —— 岭脊支路（v1.20.41 需求 4 的第三形态；ridged 形状 1−|n| 的窄带门）——
                final double rn = GTSRWorldgenHash
                    .valueNoise(worldSeed ^ S_RIDGE_GATE, x / RIDGE_SCALE, z / RIDGE_SCALE);
                final double ridgeShape = 1.0D - Math.abs(rn);
                final double ridge = RIDGE_AMP * s01((ridgeShape - RIDGE_GATE_LO) / RIDGE_GATE_SPAN);
                // —— 山地变体（ATG plateau；作用在"已含丘陵"的底上 ⇒ 山顶整平）——
                final double gm = mtnGateNoise(worldSeed, x, z);
                final double mf = s01((gm - MTN_GATE_LO) / MTN_GATE_SPAN);
                if (mf > 0.0D) {
                    final double base = h0 + forest;
                    final double ledge = MTN_LEDGE_LOW + (MTN_LEDGE_HIGH - MTN_LEDGE_LOW) * s01((gm - 0.45D) / 0.20D);
                    final double rough = 0.5D
                        + 0.5D * GTSRWorldgenHash.valueNoise(worldSeed ^ S_MTN_ROUGH, x / 40.0D, z / 40.0D);
                    final double plateau = base * (1.0D - mf) + ledge * mf
                        + Math.abs(base - ledge) * mf * (rough + 1.0D) * 0.5D;
                    forest = plateau - h0;
                }
                delta += w[1] * (forest + ridge);
                // —— 森林谷地负瓣（v1.20.42 P22 A4：顶部路径被 DELTA_CAP 软顶/108 哨兵/振幅档域三面
                // 封死后，起伏补强换到下侧——负瓣只在下侧花预算（y=40 地板现状 145 列 vs 预算 943）。
                // 式 = 草原低地同款"外侧乘门"软门：门关死列贡献精确 0，缓入环从 0 连续过渡；
                // 负 delta 直通 DELTA_CAP（softMin 只封上侧），最深 −10 ⇒ 与岭脊 +18 拉开 ≥28 格谷-脊差。
                final double vn = GTSRWorldgenHash
                    .valueNoise(worldSeed ^ S_FOREST_VALLEY, x / FOREST_VALLEY_SCALE, z / FOREST_VALLEY_SCALE);
                final double valleyGate = s01((-vn - FOREST_VALLEY_GATE_LO) / FOREST_VALLEY_GATE_SPAN);
                delta += w[1] * (-(FOREST_VALLEY_BASE + FOREST_VALLEY_SPAN * valleyGate) * valleyGate);
            }
        }
        // —— 荒漠沙丘（RTG dunes 参数化 + domain-warp；无丘陵）——
        if (w[2] > 0.0D) {
            final long warpSeed = worldSeed ^ S_DUNE_WARP;
            final double wx = x + DUNE_WARP_AMP * GTSRWorldgenHash.valueNoise(warpSeed, x / 20.0D, z / 20.0D);
            final double st = 0.38D
                + 0.30D * GTSRWorldgenHash.valueNoise(worldSeed ^ S_DUNE_STRENGTH, x / 224.0D, z / 224.0D);
            final double main = GTSRWorldgenHash.valueNoise(worldSeed ^ S_DUNE_MAIN, wx / 60.0D, z / 60.0D);
            final double d = main * st * DUNE_MAIN_GAIN;
            final double fold = (d - Math.abs(d)) * 0.5D;
            final double p = -fold;
            final double bump = p * (p * DUNE_RIDGE_QUAD + DUNE_RIDGE_LIN);
            final double ripple = GTSRWorldgenHash.valueNoise(warpSeed, wx / 17.0D, z / 17.0D) * (0.6D + st);
            delta += w[2] * (bump - 1.3D * st + ripple);
            // —— 风蚀山体 + 风蚀柱座台（v1.20.41 需求 5；RTG terrainBryce 倒数式范式，四层求和）——
            // 四层取有符号值各一次：绝对值进 sn（柱身"细而陡"），最低频层 n₃ 另作山体门、
            // λ53 层 n₂ 另作风蚀柱落点场 ⇒ 与计划"荒漠每列 +4 次"口径一致（门/座台零额外求值）。
            final double n0 = GTSRWorldgenHash.valueNoise(worldSeed ^ S_BRYCE_0, x / BRYCE_SCALE_0, z / BRYCE_SCALE_0);
            final double n1 = GTSRWorldgenHash.valueNoise(worldSeed ^ S_BRYCE_1, x / BRYCE_SCALE_1, z / BRYCE_SCALE_1);
            final double n2 = GTSRWorldgenHash.valueNoise(worldSeed ^ S_BRYCE_2, x / BRYCE_SCALE_2, z / BRYCE_SCALE_2);
            final double n3 = GTSRWorldgenHash.valueNoise(worldSeed ^ S_BRYCE_3, x / BRYCE_SCALE_3, z / BRYCE_SCALE_3);
            final double sn = Math.abs(n0) + BRYCE_W1 * Math.abs(n1)
                + BRYCE_W2 * Math.abs(n2)
                + BRYCE_W3 * Math.abs(n3);
            final double bryceGate = s01((n3 - BRYCE_GATE_LO) / BRYCE_GATE_SPAN);
            final double bryce = bryceGate
                * softMin(BRYCE_GAIN / (BRYCE_FLOOR + BRYCE_SLOPE * sn), BRYCE_CAP, BRYCE_CAP_K);
            // 座台走公开谓词（与 populate 侧柱 feature 同一份式子，杜绝"柱落在无座台的平沙上"）
            delta += w[2] * (bryce + SPINE_PEDESTAL * windSpineSiteAt(worldSeed, x, z));
        }
        // —— 沼泽夹持 + 三档水体下挖 / 遗忘之川微起伏（ATG swamp 模板；只做地形，不置水）——
        if (w[3] > 0.0D || w[4] > 0.0D) {
            final double gs = GTSRWorldgenHash.valueNoise(worldSeed ^ S_SWAMP_CLAMP, x / 256.0D, z / 256.0D);
            final double gate = s01((gs + 1.0D) * 0.5D);
            // 三档门值与<b>档位</b>都只在这里算一次：delta 组合与 swampTierAt 共用 swampGates（单一真值，
            // P20 §13 C7；档位的单点分流 = g[SWG_TIER]，P20 §21-D）
            final double[] g = swampGates(worldSeed, x, z, w[3] > 0.0D);
            final double pool = g[SWG_POOL];
            final double target = ProsperityTerrainProfile.SEA_LEVEL - SWAMP_CLAMP_UNDERSHOOT;
            if (w[3] > 0.0D) {
                final double f = 0.55D + 0.40D * gate;
                // —— P20 §21-D：三档<b>水体项</b>在 delta 侧同样<b>互斥</b>，只落 g[SWG_TIER] 那一档 ——
                // g[SWG_TIER] 就是 swampTierAt 的返回值（同一次 swampGates、同一个槽），故"地形侧算到哪
                // 一档深"与"回填侧判哪一档"严格同口径；其余两档在本式里根本不参与求值 ⇒ 修复前"被判为
                // 表面池的列被同列深水池/水沼地项挖到 ≥4 格"（实测 7111/62169 = 11.4%，NONE 档 1258 列）
                // 的相加穿透在构造上归零。门比较只有 swampGates 里那一份，本处不写第二遍。
                final int tier = (int) g[SWG_TIER];
                final double water;
                if (tier == SWAMP_TIER_DEEP) {
                    // ② 深水池：−(5.0 + 4.0·门) ⇒ 水深 5–9（与表面池 1–2 留 4 格分水岭）
                    water = (SWAMP_DEEP_BASE + SWAMP_DEEP_SPAN * g[SWG_DEEP]) * g[SWG_DEEP];
                } else if (tier == SWAMP_TIER_MARSH) {
                    // ③ 水沼地：−(0.5 + 1.0·门) ⇒ 半淹（0–1 层）
                    water = (SWAMP_MARSH_BASE + SWAMP_MARSH_SPAN * g[SWG_MARSH]) * g[SWG_MARSH];
                } else if (tier == SWAMP_TIER_POOL) {
                    // ① 表面池：−2.0·池门·f ⇒ 1–2 层（档位优先序 深&gt;半淹&gt;表面 ⇒ 本档列不含更深档门）
                    water = SWAMP_POOL_DEPTH * pool * f;
                } else {
                    // ⓪ 三档门全 &lt; TIER_MIN ⇒ 本列无水体分支 ⇒ 水体项精确 0.0（旧的相加式在此仍会留
                    // 最深 −5.95 的穿透，正是 §21-D 要修掉的那一半）
                    water = 0.0D;
                }
                // ④ 泥丘 + ⑤ 炭屑滩是<b>非水体项</b>：按 §21-D 保持相加，但下挖侧限幅到
                // SWAMP_NONWATER_DIG_MAX（1.5 格）⇒ 两项相加也无法把浅档列穿透到 ≥4 格分水岭。
                final double dry = (SWAMP_HUMMOCK_BASE + SWAMP_HUMMOCK_SPAN * g[SWG_HUMMOCK]) * g[SWG_HUMMOCK]
                    - (SWAMP_CHAR_BASE + SWAMP_CHAR_SPAN * g[SWG_CHAR]) * g[SWG_CHAR];
                delta += w[3] * ((h0 * (1.0D - f) + target * f) - h0 - water + Math.max(-SWAMP_NONWATER_DIG_MAX, dry));
            }
            if (w[4] > 0.0D) {
                final double f4 = 0.25D + 0.25D * gate;
                delta += w[4] * ((h0 * (1.0D - f4) + target * f4) - h0 - SANZU_POOL_DEPTH * pool * f4);
            }
        }
        // 软削顶 A 模板（两道哨兵在观测域内逐位恒等，见类注释）：先封顶 delta，再封顶结果高度
        final double capped = softMin(delta, DELTA_CAP, DELTA_CAP_K);
        final double adjusted = softMin(h0 + capped, HEIGHT_SENTINEL, 4.0D);
        return (int) Math.round(adjusted);
    }

    /**
     * 沼泽三档水体的<b>分档真值</b>（v1.20.41 P20 §13 C7 新增；地形侧与回填门侧共用）。
     * <p>
     * 契约分工：本类<b>只出档位与下挖 delta</b>，不置水；{@code ChunkProviderProsperityRuins} 的
     * 沼泽回填（{@code fillSwampPools}，P20 归 S6）按本方法的档位决定"这一列填到哪个水面"。
     * 因此本方法是<b>只读谓词</b>（零状态、零方块读、同 seed 同坐标恒同输出），调用它不产生任何写。
     * <p>
     * 档位与地形侧的一致性（各档的 delta 就是 {@link #variantAdjustment} 里沼泽支路的<b>那一项</b>；
     * <b>P20 §21-D 起三档水体项在 delta 侧互斥</b>——只落本方法选出的那一档，其余两档在该列<b>不</b>
     * 贡献下挖，两侧读的是 {@link #swampGates} 的同一个 {@link #SWG_TIER} 槽）：
     * <table border="1">
     * <caption>沼泽三档</caption>
     * <tr>
     * <th>档</th>
     * <th>含义</th>
     * <th>判据（门 ≥ 0.5）</th>
     * <th>本类的 delta（互斥，只此一项）</th>
     * </tr>
     * <tr>
     * <td>{@link #SWAMP_TIER_NONE}</td>
     * <td>无水体分支（干沼地草面）</td>
     * <td>三档门全 &lt; 0.5</td>
     * <td>水体项精确 0.0，只有夹持 + 非水体两项（旧相加式在此还留最深 −5.95 的穿透 ⇒ §21-D 修掉）</td>
     * </tr>
     * <tr>
     * <td>{@link #SWAMP_TIER_POOL}</td>
     * <td>表面水池（1–2 层）</td>
     * <td>表面池门 ≥ 0.5 且非深/半淹</td>
     * <td>{@code −2.0·pool·f}</td>
     * </tr>
     * <tr>
     * <td>{@link #SWAMP_TIER_DEEP}</td>
     * <td><b>深水池</b>（5–9 层）</td>
     * <td>深水池门 ≥ 0.5（最高优先）</td>
     * <td>{@code −(5.0 + 4.0·门)·门}</td>
     * </tr>
     * <tr>
     * <td>{@link #SWAMP_TIER_MARSH}</td>
     * <td>水沼地（0–1 层半淹、大面积）</td>
     * <td>水沼地门 ≥ 0.5</td>
     * <td>{@code −(0.5 + 1.0·门)·门}</td>
     * </tr>
     * </table>
     * 优先级 <b>深水池 &gt; 水沼地 &gt; 表面池</b>（三档是三个正交噪声场，同列可叠门；取最深一档
     * 才能让回填门把该列按深水池填，而不是按半淹填浅）。<b>非水体项</b>（④ 泥丘 / ⑤ 炭屑滩）不参与
     * 分档、按 §21-D 保持相加，但其合计下挖被 {@link #SWAMP_NONWATER_DIG_MAX} 限幅到 1.5 格 ⇒ 任何档
     * 位的水深都只由本档的水体项决定（4 格分水岭不被穿透）。
     *
     * @param worldSeed   与 {@code heightAt}/{@link #variantAdjustment} 同口径的世界种子
     * @param x           列 x
     * @param z           列 z
     * @param rosterIndex 中心粗格名册下标；<b>只有 roster 3（汽雾沼泽）有档</b>，其余（含越界/无身份）
     *                    一律 {@link #SWAMP_TIER_NONE} —— 与 {@code GTSRVoronoiRiverField#swampLakeAt}
     *                    的 roster 门同口径，不引入第二身份面
     * @return {@link #SWAMP_TIER_NONE}/{@link #SWAMP_TIER_POOL}/{@link #SWAMP_TIER_DEEP}/
     *         {@link #SWAMP_TIER_MARSH}
     */
    public static int swampTierAt(long worldSeed, int x, int z, int rosterIndex) {
        if (rosterIndex != SWAMP_ROSTER) {
            return SWAMP_TIER_NONE;
        }
        // 单点分流（P20 §21-D）：档位只由 swampGates 的 SWG_TIER 槽产出，本方法不再自己比门
        // ⇒ 地形侧的 delta 与回填侧的档位取的是同一次比较的结果，不可能漂移。
        return (int) swampGates(worldSeed, x, z, true)[SWG_TIER];
    }

    /**
     * 沼泽<b>腹地谓词</b>（v1.20.42 P22 A3 新增；三档水体 + 微池的统一边缘门）：列所在粗格的
     * Chebyshev 半径 {@link #SWAMP_EDGE_RADIUS_CELLS}（N=16 ⇒ R=4 粗格 = 名义 16 方块）内
     * {@code rosterIndexAt} <b>全为 roster 3</b> 才算腹地。
     * <p>
     * <b>边缘定义在 coarse 1:4 身份面</b>（{@link GTSRGenLayerChain#COARSE_BLOCK_SHIFT}）：身份取数走
     * {@link ProsperityTerrainProfile#chainRosterIndexAt} 的共享 memo——与 {@link #weightsAt}/
     * {@code GTSRRiverPlacer.tierGrid} 同一身份面同一条盐，<b>不引入第二身份面</b>。粗格级常量 ⇒
     * 判定按 (seed, 粗格) 缓存（{@link #SWAMP_INTERIOR_CACHE}），每列一次查表、<b>零噪声求值</b>；
     * 未命中一次最坏 {@code (2R+1)² = 81} 次 memo 化身份查表（摊销后每新粗格 ~几个新格）。
     * <p>
     * <b>消费契约（单一真值）</b>：地形侧经 {@link #swampGates} 的 {@link #SWG_TIER} 槽乘本谓词
     * （非腹地 ⇒ tier=NONE ⇒ delta 侧水体项精确 0，"地形不挖"），回填侧
     * {@code ChunkProviderProsperityRuins.fillSwampPools} 三档腿读同一槽（自动 NONE）、微池腿显式乘
     * 本方法（"回填不灌"）——两侧不可能各写一遍而漂移。 {@code swampRiverPoolAt}（P22 A1b 残潭）
     * 的三档互斥腿读 {@link #swampTierAt}，边缘列 tier 归 NONE 后该腿对边缘河床列放行——残潭自身的
     * 不外流钳制归 A1b 片验收，本片不代管（全量复跑归 A5）。
     * <p>
     * <b>纯函数</b>、零 {@code net.minecraft} 依赖；N 的校准域 {12,16,24}（R∈{3,4,6}），域外取值禁。
     */
    public static boolean swampInteriorAt(long worldSeed, int x, int z) {
        final int cellX = x >> GTSRGenLayerChain.COARSE_BLOCK_SHIFT;
        final int cellZ = z >> GTSRGenLayerChain.COARSE_BLOCK_SHIFT;
        final HashMap<Long, HashMap<Long, Boolean>> bySeed = SWAMP_INTERIOR_CACHE.get();
        HashMap<Long, Boolean> cells = bySeed.get(worldSeed);
        if (cells == null) {
            cells = new HashMap<>();
            bySeed.put(worldSeed, cells);
        }
        final Long key = Long.valueOf(packCell(cellX, cellZ));
        final Boolean cached = cells.get(key);
        if (cached != null) {
            return cached.booleanValue();
        }
        boolean interior = true;
        for (int dz = -SWAMP_EDGE_RADIUS_CELLS; dz <= SWAMP_EDGE_RADIUS_CELLS && interior; dz++) {
            for (int dx = -SWAMP_EDGE_RADIUS_CELLS; dx <= SWAMP_EDGE_RADIUS_CELLS; dx++) {
                if (ProsperityTerrainProfile.chainRosterIndexAt(worldSeed, cellX + dx, cellZ + dz) != SWAMP_ROSTER) {
                    interior = false;
                    break;
                }
            }
        }
        if (cells.size() >= SWAMP_INTERIOR_CACHE_CAP) {
            cells.clear();
        }
        cells.put(key, Boolean.valueOf(interior));
        return interior;
    }

    /**
     * 风蚀柱<b>落点场</b>（v1.20.41 需求 5；给 populate 侧柱 feature 的单一真值谓词，P20 S6 消费）。
     * 式子与本类地形侧座台<b>同域同值</b>（{@code variantAdjustment} 的荒漠支路直接调用本方法加
     * {@link #SPINE_PEDESTAL} 的座台）⇒ 柱只会落在自己有座台抬升的列上，不存在"柱落在平沙上"或
     * 第二随机场。返回 ∈[0,1] 的门值（≈0.5–1% 列满门）；feature 侧按"门值 ≥ 0.5 且落块可行"取点。
     * <b>纯函数</b>、零 {@code net.minecraft} 依赖、不消费任何 {@code Random}。
     */
    public static double windSpineSiteAt(long worldSeed, int x, int z) {
        return s01(
            (GTSRWorldgenHash.valueNoise(worldSeed ^ S_BRYCE_2, x / BRYCE_SCALE_2, z / BRYCE_SCALE_2) - SPINE_GATE_LO)
                / SPINE_GATE_SPAN);
    }

    /**
     * 灌木<b>成簇场</b>（v1.20.41 P20 S6 需求 6「锈蚀草原……灌木群」的第二档；populate 侧单一真值谓词）。
     * <p>
     * <b>为什么在 TerrainVariants 而不改 {@code DensityField}</b>：P20 §5 S6 原文把"成簇"的第二档写在
     * {@code DensityField.LAYER_SHRUB} 的 {@code fold} 里，但本轮任务包把本片写锁收死为
     * {@code ProsperityDecorPlacer} + {@code TerrainVariants}（{@code DensityField} 只读），且明确要求
     * 「低地形态侧已由 S4 落地，本片只接 populate」。密度场的职责是<b>群系身份的连续过渡</b>
     * （11×11 核 + 档表行值凸组合 + 「均匀区期望 == 档表原值逐位」的构造性保证，见其类注释），把
     * 一个独立噪声簇门折进 {@code fold} 会破坏该保证（均匀区不再是档表逐字值）并连带
     * {@code DensityField#DICE_GATE} 的 LCM 精确重合论证。故本支取"<b>密度场不动、簇场另立</b>"：
     * 密度门决定<b>这个 chunk 有没有灌木事件</b>（逐位不变），本场只决定<b>该事件摊成几株</b>——
     * 两者相乘仍是单一真值，且 ANTI 换档臂（它换的是档表行值）对两条通道都仍然可见。
     * <p>
     * <b>式子</b>：两波长（41 / 83，均 %16 ≠ 0 ✔ H-1）的独立 valueNoise 各过一次
     * {@code s01((n − 0.55)/0.18)}（P20 §5 S6 的字面门带），取 <b>max = 并集</b>（"双场并集"的字面兑现：
     * 41 给簇的细密粒、83 给簇区的整体趋向）。返回 ∈[0,1] 的<b>软</b>门值 ⇒ 株数由门值连续派生
     * （见 {@code ProsperityDecorPlacer#placeShrubClusterPass}），不在 0.5 上留硬台阶。
     * <p>
     * <b>零地形成本</b>：本方法<b>不</b>被 {@link #variantAdjustment} 消费（不进高度链 ⇒ §21-G 的每列
     * 求值数逐档不变），只在 populate 的灌木事件上按列求值一次（每次 2 个 valueNoise）。
     * 纯函数、零 {@code net.minecraft} 依赖、不消费任何 {@code Random}。
     */
    public static double shrubClusterAt(long worldSeed, int x, int z) {
        final double a = s01(
            (GTSRWorldgenHash
                .valueNoise(worldSeed ^ S_SHRUB_CLUSTER_A, x / SHRUB_CLUSTER_SCALE_A, z / SHRUB_CLUSTER_SCALE_A)
                - SHRUB_CLUSTER_GATE_LO) / SHRUB_CLUSTER_GATE_SPAN);
        final double b = s01(
            (GTSRWorldgenHash
                .valueNoise(worldSeed ^ S_SHRUB_CLUSTER_B, x / SHRUB_CLUSTER_SCALE_B, z / SHRUB_CLUSTER_SCALE_B)
                - SHRUB_CLUSTER_GATE_LO) / SHRUB_CLUSTER_GATE_SPAN);
        return Math.max(a, b);
    }

    /**
     * 沼泽支路的五项门值 <b>+ 水体档位</b>（<b>单一真值</b>：{@link #variantAdjustment} 的下挖 delta 与
     * {@link #swampTierAt} 的分档判定同取本方法，P20 §13 C7 的地形/回填两侧契约）。
     * 槽位见 {@link #SWG_POOL}/{@link #SWG_DEEP}/{@link #SWG_MARSH}/{@link #SWG_HUMMOCK}/
     * {@link #SWG_CHAR}/{@link #SWG_TIER}；门值全部 {@link #s01} 带通 ⇒ 带外精确 0.0（全软门纪律）。
     * <p>
     * <b>单点分流（P20 §21-D）</b>：三档水体的 {@code ≥ TIER_MIN} 比较<b>只在本方法末尾写这一遍</b>，
     * 结果落在 {@link #SWG_TIER}；delta 侧与 {@link #swampTierAt} 都读该槽 ⇒ "本列被挖到哪一档深"
     * 与"本列被判为哪一档"是同一个量，三档水体项在 delta 侧因此天然互斥。
     * 优先序 <b>深水池 &gt; 水沼地 &gt; 表面池</b>（原 {@link #swampTierAt} 的判序，未改语义：三档是三个
     * 正交噪声场，同列可叠门，取最深一档才能让回填门把该列按深水池填）。
     * <p>
     * <b>返回的是线程私有 scratch</b>：调用方须<b>就地取用、不得跨列持有</b>（同粗格缓存的纪律）。
     * {@code withTiers == false}（遗忘之川列）时三项分支档与 {@link #SWG_TIER} 被显式写 0，只算表面池
     * ⇒ 名册兜底档的每列成本不因本轮改动上升（深水池/水沼地/泥丘/炭屑滩是 roster 3 专属）。
     */
    private static double[] swampGates(long worldSeed, int x, int z, boolean withTiers) {
        final double[] g = SWAMP_GATE_SCRATCH.get();
        g[SWG_POOL] = s01(
            (GTSRWorldgenHash.valueNoise(worldSeed ^ S_SWAMP_POOL, x / SWAMP_POOL_BED_WAVE, z / SWAMP_POOL_BED_WAVE)
                - SWAMP_POOL_GATE_LO) / SWAMP_POOL_GATE_SPAN);
        if (!withTiers) {
            g[SWG_DEEP] = 0.0D;
            g[SWG_MARSH] = 0.0D;
            g[SWG_HUMMOCK] = 0.0D;
            g[SWG_CHAR] = 0.0D;
            g[SWG_TIER] = SWAMP_TIER_NONE;
            return g;
        }
        g[SWG_DEEP] = s01(
            (GTSRWorldgenHash.valueNoise(worldSeed ^ S_SWAMP_DEEP, x / S_SWAMP_DEEP_BED, z / S_SWAMP_DEEP_BED)
                - SWAMP_DEEP_GATE_LO) / SWAMP_DEEP_GATE_SPAN);
        final double mn = GTSRWorldgenHash
            .valueNoise(worldSeed ^ S_SWAMP_MARSH, x / SWAMP_MARSH_SCALE, z / SWAMP_MARSH_SCALE);
        g[SWG_MARSH] = s01((mn - SWAMP_MARSH_GATE_LO) / SWAMP_MARSH_GATE_SPAN);
        // 炭屑滩复用同场的负瓣（"同一纯函数域"的字面兑现）⇒ 零额外求值，且与水沼地空间互斥
        g[SWG_CHAR] = s01((-mn - SWAMP_CHAR_GATE_LO) / SWAMP_CHAR_GATE_SPAN);
        g[SWG_HUMMOCK] = s01(
            (GTSRWorldgenHash.valueNoise(worldSeed ^ S_SWAMP_HUMMOCK, x / SWAMP_HUMMOCK_SCALE, z / SWAMP_HUMMOCK_SCALE)
                - SWAMP_HUMMOCK_GATE_LO) / SWAMP_HUMMOCK_GATE_SPAN);
        // 唯一的门比较（P20 §21-D 的单点分流）：两侧（delta / 回填档位）都只读本槽
        g[SWG_TIER] = g[SWG_DEEP] >= TIER_MIN ? SWAMP_TIER_DEEP
            : (g[SWG_MARSH] >= TIER_MIN ? SWAMP_TIER_MARSH
                : (g[SWG_POOL] >= TIER_MIN ? SWAMP_TIER_POOL : SWAMP_TIER_NONE));
        // ═══ v1.20.42 P22 A3 边缘门：非腹地列（Chebyshev R 内 roster 非 3）一律 NONE ═══
        // 只钳 SWG_TIER 槽、不碰门值槽 ⇒ 遗忘之川（withTiers=false，上方已 return）与本档
        // 非水体项（泥丘/炭屑滩/夹持）的读数逐位不变；delta 侧（variantAdjustment 只在本档
        // tier 槽取水体项）与回填侧（swampTierAt）经同一槽自动同口径，"地形不挖、回填不灌"。
        if (g[SWG_TIER] != SWAMP_TIER_NONE && !swampInteriorAt(worldSeed, x, z)) {
            g[SWG_TIER] = SWAMP_TIER_NONE;
        }
        return g;
    }

    /**
     * 11×11 核对逐粗格名册的加权累积（P18 T3 同款防悬崖；中心粗格即调用方
     * {@code rosterIndex} 的来源格 ⇒ 均匀区 {@code w[rosterIndex] == 1.0}、其余精确 0.0）。
     * <b>P19 U8 改判</b>：加权结果按核中心粗格缓存（{@link #weightsAt}）——本方法保留为
     * 未命中路径的一次性求值体，算式与加法次序一字未动；粗格身份取数改走
     * {@link ProsperityTerrainProfile#chainRosterIndexAt} 的单一份共享 memo
     * （与 ampAt/heightCore 同一份，取数式与本类旧内联版逐字同源，消同格短命链重复求值）。
     */
    private static double[] weightsAt(long worldSeed, int x, int z) {
        final int cellX = x >> GTSRGenLayerChain.COARSE_BLOCK_SHIFT;
        final int cellZ = z >> GTSRGenLayerChain.COARSE_BLOCK_SHIFT;
        final HashMap<Long, HashMap<Long, double[]>> bySeed = VAR_CELL_CACHE.get();
        HashMap<Long, double[]> cells = bySeed.get(worldSeed);
        if (cells == null) {
            cells = new HashMap<>();
            bySeed.put(worldSeed, cells);
        }
        final Long key = Long.valueOf(packCell(cellX, cellZ));
        final double[] cached = cells.get(key);
        if (cached != null) {
            return cached;
        }
        final double[] w = new double[ROSTER_SLOTS];
        for (int k = 0; k < VAR_KERNEL_DX.length; k++) {
            final int r = ProsperityTerrainProfile
                .chainRosterIndexAt(worldSeed, cellX + VAR_KERNEL_DX[k], cellZ + VAR_KERNEL_DZ[k]);
            if (r >= 0 && r < ROSTER_SLOTS) {
                w[r] += VAR_KERNEL_W[k];
            }
        }
        if (cells.size() >= VAR_CELL_CACHE_CAP) {
            cells.clear();
        }
        cells.put(key, w);
        return w;
    }

    /** (cellX, cellZ) → long 打包（与 Profile.packCell 同式：低 32 位 cellZ，负坐标两侧一致）。 */
    private static long packCell(int cellX, int cellZ) {
        return ((long) cellX << 32) | (cellZ & 0xFFFFFFFFL);
    }

    /** 丘陵门噪声（波长 320；包内可见仅供离线探针/判据取单一真值门值，生产路径勿直调）。 */
    static double hillGateNoise(long worldSeed, int x, int z) {
        return GTSRWorldgenHash.valueNoise(worldSeed ^ S_HILL_GATE, x / 320.0D, z / 320.0D);
    }

    /** 山地门噪声（波长 512；包内可见同上）。 */
    static double mtnGateNoise(long worldSeed, int x, int z) {
        return GTSRWorldgenHash.valueNoise(worldSeed ^ S_MTN_GATE, x / 512.0D, z / 512.0D);
    }

    /** RTG TerrainBase:33-45 blendedHillHeight（s=−1 映 ~0、s=0 映 ~0.15、s=1 映 ~1.0 ⇒ 恒非负）。 */
    private static double blendedHillHeight(double s) {
        double r = s + 1.0D;
        r = r * r * r + 10.0D;
        r = Math.cbrt(r);
        return r / 0.46631D - 4.62021D;
    }

    /** RTG TerrainBase:92-104 丘陵两波组合（150 大波 m 与 55 小波 sm²·m，m += sm/3）。 */
    private static double hillsShape(long worldSeed, int x, int z) {
        final double mb = blendedHillHeight(
            GTSRWorldgenHash.valueNoise(worldSeed ^ S_HILL_BIG, x / 150.0D, z / 150.0D));
        double ms = blendedHillHeight(GTSRWorldgenHash.valueNoise(worldSeed ^ S_HILL_SMALL, x / 55.0D, z / 55.0D));
        ms = ms * ms * mb;
        return mb + ms / 3.0D;
    }

    /** smoothstep 带通（clamp 后 3t²−2t³；带外精确 0.0/1.0——软门的构造性基础）。 */
    private static double s01(double t) {
        final double c = Math.max(0.0D, Math.min(1.0D, t));
        return c * c * (3.0D - 2.0D * c);
    }

    /**
     * C1 光滑极小（多项式补偿式；|a−b| ≥ k 时逐位等于 min(a,b)——两道削顶哨兵在
     * 观测域内恒等、越界域内渐近收口的构造性基础）。
     */
    private static double softMin(double a, double b, double k) {
        final double h = Math.max(0.0D, k - Math.abs(a - b));
        return Math.min(a, b) - h * h / (4.0D * k);
    }
}
