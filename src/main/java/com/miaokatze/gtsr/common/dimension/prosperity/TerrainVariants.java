package com.miaokatze.gtsr.common.dimension.prosperity;

import com.miaokatze.gtsr.common.dimension.framework.genlayer.GTSRGenLayerChain;
import com.miaokatze.gtsr.common.dimension.framework.structure.GTSRWorldgenHash;
import com.miaokatze.gtsr.common.dimension.prosperity.river.GTSRVoronoiRiverField;

/**
 * 群系内分支地形变体（<b>P19 §H 新增</b>，plan §H「实现放新类 TerrainVariants（profile 只留调用点）」）：
 * 在 {@link ProsperityTerrainProfile#heightCore} 的三频缓丘 {@code h0} 之上、河谷两段式压低<b>之前</b>，
 * 按 roster 注入群系性格形态项——齿轮森林丘陵/山地/<b>岭脊</b>（RTG TerrainBase hills 模板 + ATG
 * CoreNoise plateau 模板）+ <b>谷地负瓣</b>（v1.20.42 P22 A4，冲沟/谷地的下侧形态）、黄铜荒漠垄状沙丘 + <b>风蚀山体与风蚀柱座台</b>（RTG dunes +
 * TerrainHLDunes domain-warp + {@code terrainBryce} 倒数式模板）、起雾沼泽水位渐变夹持 +
 * <b>三档水体分支的下挖 delta</b> + 泥丘 + 炭屑滩（ATG swamp 模板）、锈蚀草原轻微丘陵与<b>低地</b>、
 * 遗忘之川微起伏（同场降档）。
 * <p>
 * <b>v1.20.49 P26-B4（需求 5 四群系分支地形放大档）新增四门一脊</b>：森林 λ433 延绵脊
 * （{@link #FOREST_SPINE_AMP}，域门复用山地门 λ512）、荒漠 λ281 沙海域（主波/增益/QUAD/warp 域内
 * 增强 + 域内与风蚀山体互斥）、草原 λ167 小盆地（负瓣封闭碗形 + 域内钳低地）、λ193 灌木林域
 * （{@link #shrubWoodlandAt}，decorate 侧、不进高度链）；深水潭 λ71 升级、泥炭丘 λ173、巨柱窄带
 * 座台分档。全部新门面域外逐位 0（各增量项在门值 0 处 IEEE 精确退化为旧常量）；三重封顶解封
 * （DELTA_CAP 32→96 / HEIGHT_SENTINEL 108→172 / Profile 钳 110→180）只动顶部路径——
 * 均匀区（新门全关 ∧ |delta| ≤ 22 ∧ 高度 ≤ 100）经 digest 对拍证明与改造前逐位相同
 * （{@code plan/tmp/p26-b4-readings.md}）。
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
 * {@link #SWAMP_NONWATER_DIG_MAX} = 1.5 格（<b>v1.20.53 P30 II-AB 起水体档列另加正侧钳
 * {@code min(dry, +3.0)}</b>，见 {@link #SWAMP_NONWATER_RISE_MAX}）。夹持本体幅度保守（≤±3.2）。
 * <b>边缘门沿革</b>：v1.20.42 P22 A3 的 coarse Chebyshev 布尔阶梯于 <b>v1.20.53 P30 II-AB
 * 连续化</b>为 {@link #swampInteriorGateAt}（w3 群系混合等值线软门，三档门值比较前各乘、
 * 深腹地门=1 ⇒ IEEE 逐位不变）；边缘截断水潭清零的目的不变（判据落 P17TerrainReliefCheck
 * A3 组）。<b>沼泽瀑布潭全套（tyF 瀑域门、瀑域加密、潭存在性混合、唇缘环、入流扇形与 CPR
 * 侧潭心钳低/高水台/转换趟）随 v1.20.53 P30 II-AB 退役</b>（v1.20.50 P27 批次B-B1/P28 S ③B
 * 引入，P30 II-AB 全套摘除）；缓坡潭域 tyG、陡潭门与三档/微池置水通道语义不动。</li>
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
 * （零噪声求值）⇒ 沼泽支路每列噪声求值数不变；非沼泽列不进 {@code swampGates} 的 withTiers
 * 路径，逐位不动。<b>v1.20.53 P30 II-AB 边缘门连续化复核</b>：连续门
 * {@link #swampInteriorGateAt} 读 {@link #weightsAt} 的粗格缓存（variantAdjustment 首行已算
 * 同列 w ⇒ 缓存命中）⇒ 每列噪声求值数仍不变。
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
     * 次序）。每列成本从 69 次 HashMap 查表降为 1 次。
     * <p>
     * <b>P24-C2（v1.20.47）由两层 {@code HashMap<Long,…>} 改直接映射定长槽表</b>（同
     * {@link ProsperityTerrainProfile#cellSlotIndex} 散列口径）：key = (worldSeed, cellX, cellZ)
     * 全字段精确比较进槽、冲突即覆盖淘汰；省 {@code Long} 装箱与两层查表。纪律同 Profile 各表：
     * 线程私有、上限 {@link #VAR_CELL_CACHE_CAP}、淘汰映射确定性（值只依赖纯函数 ⇒ 逐位等价）。
     */
    private static final int VAR_CELL_CACHE_CAP = 65536;

    /** 权重向量槽（P24-C2）；{@code w} 只读返回、调用方不得改写（与旧缓存同一条纪律）。 */
    private static final class VarSlot {

        long seed;
        int cx;
        int cz;
        boolean valid;
        final double[] w = new double[ROSTER_SLOTS];
    }

    private static VarSlot[] varSlots() {
        final VarSlot[] a = new VarSlot[VAR_CELL_CACHE_CAP];
        for (int i = 0; i < a.length; i++) {
            a[i] = new VarSlot();
        }
        return a;
    }

    private static final ThreadLocal<VarSlot[]> VAR_CELL_CACHE = ThreadLocal.withInitial(TerrainVariants::varSlots);
    // [v1.20.53 P30 II-AB] 原 P22 A3 沼泽腹地布尔粗格缓存（腹地表与上限常量）随边缘门
    // 连续化退役——连续门 swampInteriorGateAt 直读上方 VAR_CELL_CACHE 的粗格权重缓存，零第二张表。

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
    /**
     * 草原轻微丘陵幅度（门全开 ×形状 ⇒ +2..+6 常态带）。
     * <p>
     * <b>v1.20.49 P26-B4 ⑩：10.0 → 14.0</b>（需求 5 平原"小小山丘"放大档 ⇒ +4..+8）。沙/原 sd 比探针
     * 复跑后仍须 ∈ [1.10,1.45]（R9：跌破 1.10 ⇒ 本值退 12）；批内读数与沙海域对冲结论见
     * {@code plan/tmp/p26-b4-readings.md}。
     */
    private static final double HILL_AMP_STEPPE = 14.0D;

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

    // —— 延绵山脉脊（v1.20.49 P26-B4 ② 新增，仅森林；D7' 破顶放大档的"延绵"主形态）——
    /**
     * 延绵脊场域盐（波长 {@link #FOREST_SPINE_SCALE}=433；433 % 16 = 1 ✔ H-1）。盐值续
     * {@code 0x6811C2B1} 起的 ×0x12 等差族尾追加（上一已用 = 谷地场 {@code 0x6811C377}，+0x12 ⇒ 本值），
     * 不与既有任何域盐重合。
     */
    private static final long S_FOREST_SPINE = 0x6811C389L;
    /**
     * 脊场波长（433 半波 ~216 格 = 脊线沿 |rn|≈0 等值线连续延伸 200-400 格的"延绵"量级来源）。
     * 域门复用山地门 {@link #mtnGateNoise}（λ512，森林列已求值 ⇒ 零加费），带 [0.24,0.60] 不变。
     */
    private static final double FOREST_SPINE_SCALE = 433.0D;
    /** 脊形门下檐（作用于 ridged 形状 {@code 1−|sn|}：满门 |sn| ≤ 0.20、缓入到 |sn| ≤ 0.42）。 */
    private static final double FOREST_SPINE_GATE_LO = 0.58D;
    /** 脊形门带宽。 */
    private static final double FOREST_SPINE_GATE_SPAN = 0.22D;
    /**
     * 脊峰幅度（D7' 放大档：脊峰目标 150~165 ⇒ 本值由缩样读数推导钉死——缩样实测脊峰落 150~165
     * 带内、森林 ≥150 占比见 {@code plan/tmp/p26-b4-readings.md}；R8：CLAMP 计数超带 ⇒ 降 delta）。
     * {@link #HEIGHT_SENTINEL}=172（k=4 ⇒ ≤168 逐位不动带）与 {@link #DELTA_CAP}=96
     * 联合给脊峰上界（h0 + 96 后再过哨兵渐近 172 ⇒ 恒 &lt; 180 钳制线）。
     */
    private static final double FOREST_SPINE_AMP = 52.0D;

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

    // —— 沙海域（v1.20.49 P26-B4 ⑤ 新增，roster 2"巨大沙丘"的域门族；域外 duneGate==0 ⇒ 各增量逐位 0）——
    /**
     * 沙海域域门场盐（波长 {@link #DUNE_SEA_SCALE}=281；281 % 16 = 9 ✔ H-1）。盐续 ×0x12 等差族
     * （延绵脊 {@code 0x6811C389} 之后 +0x12 ⇒ 本值）。
     */
    private static final long S_DUNE_SEA = 0x6811C39BL;
    /** 沙海域域门波长（域径 ~100-200 格）。 */
    private static final double DUNE_SEA_SCALE = 281.0D;
    /** 域门下檐/带宽（{@code s01((n−0.46)/0.22)} ⇒ 覆盖 ~15% 荒漠列）。 */
    private static final double DUNE_SEA_GATE_LO = 0.46D;
    private static final double DUNE_SEA_GATE_SPAN = 0.22D;
    /** 域内主波增益增量（2.6 + 4.4·gate ⇒ 域心 7.0，垄峰 +12..+26"丘高 15-30"）。 */
    private static final double DUNE_SEA_GAIN_BOOST = 4.4D;
    /** 域内垄脊二次项增量（3.0 + 3.0·gate ⇒ 迎缓背陡对比拉大；R10 触上界 ⇒ 减半）。 */
    private static final double DUNE_SEA_QUAD_BOOST = 3.0D;
    /** 域内 x 向 domain-warp 增量（6 + 4·gate ⇒ 丘链沿 z 延伸成链）。 */
    private static final double DUNE_SEA_WARP_BOOST = 4.0D;

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
    /**
     * 巨柱窄带门下檐/带宽（v1.20.49 P26-B4 ④：作用于 {@link #windSpineSiteAt} 同场门值——
     * site ≥ 0.88 ⇔ n₅₃ ≥ ~0.90 的更窄档，与 populate 侧巨柱落点<b>同一份场</b> ⇒ 零第二真值；
     * 满门 site=1.0 ⇒ 座台 1.5×(1+1.0) = 3.0）。
     */
    private static final double SPINE_PEDESTAL_GIANT_GATE_LO = 0.88D;
    private static final double SPINE_PEDESTAL_GIANT_GATE_SPAN = 0.12D;
    /** 巨柱窄带的座台抬升增量（+1.5·site → 域心 +3.0·site）。 */
    private static final double SPINE_PEDESTAL_GIANT_BOOST = 1.0D;
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

    // —— 灌木林域（v1.20.49 P26-B4 ⑫ 新增；decorate 侧加密的只读域门，不进高度链）——
    /**
     * 灌木林域场盐（波长 {@link #SHRUB_WOODLAND_SCALE}=193；193 % 16 = 1 ✔ H-1）。盐续 ×0x12 等差族
     * （草原小盆地 {@code 0x6811C3AD} 之后 +0x12 ⇒ 本值）。
     */
    private static final long S_SHRUB_WOODLAND = 0x6811C3BFL;
    /** 灌木林域波长（域直径 ~50-100 格）。 */
    private static final double SHRUB_WOODLAND_SCALE = 193.0D;
    /** 域门下檐/带宽（{@code s01((n−0.50)/0.18)} ⇒ 覆盖 ~8-12% 草原列）。 */
    private static final double SHRUB_WOODLAND_GATE_LO = 0.50D;
    private static final double SHRUB_WOODLAND_GATE_SPAN = 0.18D;

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

    // —— 草原小盆地（v1.20.49 P26-B4 ⑪ 新增；λ167 负瓣封闭碗形，第一版不集水）——
    /**
     * 盆地场域盐（波长 {@link #STEPPE_BASIN_SCALE}=167；167 % 16 = 7 ✔ H-1）。盐续 ×0x12 等差族
     * （沙海域 {@code 0x6811C39B} 之后 +0x12 ⇒ 本值）。
     */
    private static final long S_STEPPE_BASIN = 0x6811C3ADL;
    /**
     * 盆地场波长（负瓣等值线天然闭合 ⇒ λ167 blob 直径 ~40-70 格的封闭碗形）。量级取在低地场 93 与
     * 丘陵门 320 之间——盆地要比低地更宽（碗形）但比丘陵区更稀有。
     */
    private static final double STEPPE_BASIN_SCALE = 167.0D;
    /**
     * 盆地负瓣门下檐（{@code s01((−n−0.55)/0.25)}：满门 n ≤ −0.80、缓入到 n ≤ −0.55）。
     * <b>P26-B4 批内校准：0.55/0.25 → 0.60/0.22</b>（满门 n ≤ −0.82）——首版 0.55/0.25 在判据全域窗
     * 把 y=40 地板触边推到 947 > 预算 943（RELIEF CLAMP 带），收紧满门覆盖 ~25% 控吃地板预算；
     * 深度档 −5..−9 不动（需求口径）；触钳列预算带按本片实测重钉（见
     * {@code tools/dim1/P17TerrainReliefCheck} CLAMP_RATIO_MAX 与 {@code plan/tmp/p26-b4-readings.md}）。
     */
    private static final double STEPPE_BASIN_GATE_LO = 0.60D;
    /** 盆地负瓣门带宽。 */
    private static final double STEPPE_BASIN_GATE_SPAN = 0.22D;
    /** 盆地基础下挖（满门深度档 {@code −(5.0+4.0·门)·门} 的下沿 = −5）。 */
    private static final double STEPPE_BASIN_BASE = 5.0D;
    /** 盆地按门加深档（满门最深 −9）。 */
    private static final double STEPPE_BASIN_SPAN = 4.0D;

    // —— 草原深芯/缎带沟（v1.20.50 P27 批次A-A3 G 片新增：盆内深芯第二档 + ridged 负像沟；级联互斥）——
    /**
     * 深芯场域盐（波长 {@link #STEPPE_BASIN_CORE_SCALE}=89；89 % 16 = 9 ✔ H-1）。盐续 ×0x12 等差族
     * （盆地 {@code 0x6811C3AD} 之后 +0x12×2 ⇒ 本值；+0x12 的 {@code 0x6811C3BF} 归灌木林域，
     * +0x12×3 的 {@code 0x6811C3D1} 归沼泽分型域——P27 §0 D7 盐表钉死）。
     */
    private static final long S_STEPPE_BASIN_CORE = 0x6811C3E3L;
    /**
     * 深芯场波长（λ89 芯径 ~20-40 格 ≈ 碗径 40-70 的 1/3-1/2——"碗中碗"嵌套尺度）。
     * 深度层级对沼泽深潭 8-14 <b>水</b>：干碗要出同等戏剧感需再深一档 ⇒ 合成 −9..−16 <b>干</b>
     * （干盆维持：类契约"只做地形不置水" + populate 无草原回填路径，零改动自动成立）。
     */
    private static final double STEPPE_BASIN_CORE_SCALE = 89.0D;
    /**
     * 深芯负瓣门下檐/带宽（{@code s01((−n−0.62)/0.20)}：满门 n ≤ −0.82、缓入到 n ≤ −0.62）。
     * any 门估 4-7% ⇒ 芯∩盆 ≈ 0.3-0.5% 草原列 ≈ 每 3-8 个盆地一个深芯。
     */
    private static final double STEPPE_BASIN_CORE_GATE_LO = 0.62D;
    private static final double STEPPE_BASIN_CORE_GATE_SPAN = 0.20D;
    /** 深芯基础下挖（{@code −(4.0+3.0·门)·门×basinGate} 合成下沿：盆 −5..−9 叠芯 ⇒ −9..−16）。 */
    private static final double STEPPE_BASIN_CORE_BASE = 4.0D;
    /** 深芯按门加深档（满门最深 −16，唯一超 −9 的单臂；y=40 地板余量 14-16 格）。 */
    private static final double STEPPE_BASIN_CORE_SPAN = 3.0D;
    /**
     * 缎带沟场域盐（波长 {@link #STEPPE_GULLY_SCALE}=73；73 % 16 = 9 ✔ H-1）。盐续 ×0x12 等差族
     * （深芯 {@code 0x6811C3E3} 之后 +0x12 ⇒ 本值；+0x12×2 的 {@code 0x6811C407} 归沼泽缓坡床纹）。
     * <b>v1.20.50 P27 G 片 redirect：波长 λ57 → λ73，域盐不变</b>（主代理裁决，见下）。
     */
    private static final long S_STEPPE_GULLY = 0x6811C3F5L;
    /**
     * 沟场波长：<b>线状沟 = 场的等值线（n≈c），不是门阈 blob</b>——负瓣门阈是团块，"沟"必须
     * ridged 形 {@code 1−|n|} 沿 n≈0 等值线取窄带（岭脊 λ188/延绵脊 λ433 的负像）。<b>v1.20.50
     * P27 G 片 redirect（主代理裁决）：λ57 → λ73 收窄</b>——初版 λ57 实测沟带覆盖 17.99%
     * （{@code plan/tmp/p27-g-readings.md} §redirect），观感偏密；用户语义"小小的凹陷小峡谷"
     * = 点缀性 ⇒ 覆盖按线密度 ~(57/73)² ≈ 0.61 收窄至预期 ~11%。λ73 而非 GULLY_GATE_LO 加严：
     * 73 % 16 = 9 ✔ H-1，且门参数不动 = 沟形机制不变（满带 |n|≤0.10 半宽语义、深度 −4..−6.5、
     * 侧壁露石带全保持——门加严会同时压窄沟宽与露石，裁决选波长不选门）。λ73 满带空间宽
     * ~6-10 格（λ 线性放大 5-8 × 73/57）；等值线自然蛇行（曲率尺度 ~2λ≈146 格），被丘陵域
     * 截断成 30-80 格段——"小小"的长度控制不引入新噪声（复用已求值 gateS 作存在域）。
     */
    private static final double STEPPE_GULLY_SCALE = 73.0D;
    /**
     * 沟门下檐/带宽（{@code s01((1−|n|−0.78)/0.12)}：满带 |n| ≤ 0.10、缓入到 |n| ≤ 0.22）。
     * 深 6.5 落在半宽 2-3 格上 ⇒ 侧壁 Δh 3-6/2-3 列 ⇒ 石带 1-3 格、只在两壁窄带出露
     * （"只裸露一点石头"由 {@link #STEPPE_GULLY_SPAN} 单参数控制；沟底仍铺土——表层状态机涌现）。
     */
    private static final double STEPPE_GULLY_GATE_LO = 0.78D;
    private static final double STEPPE_GULLY_GATE_SPAN = 0.12D;
    /** 沟基础下挖（{@code −(4.0+2.5·门)·门} ⇒ 深 −4..−6.5）。 */
    private static final double STEPPE_GULLY_BASE = 4.0D;
    /** 沟按门加深档（满门最深 −6.5）。 */
    private static final double STEPPE_GULLY_SPAN = 2.5D;

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
     * 深水池<b>床纹波长</b>（格）。<b>v1.20.49 P26-B4 ⑥：37.0 → 71.0</b>（深水潭升级——潭径加大到
     * blob ~18-35 格；71 % 16 = 7 ✔ H-1）。名字按 P20 §13 C9 / §14.3 的裁定原文保留
     * （{@code S_} 前缀在此承载的是<b>波长</b>而非域盐——域盐是同组的 {@link #S_SWAMP_DEEP}；
     * 与本类其它 {@code S_*} 域盐常量的形不一致是裁定原名的既成事实，不改名以免与裁定脱钩）。
     */
    private static final double S_SWAMP_DEEP_BED = 71.0D;
    /**
     * 深水池门下檐。<b>v1.20.49 P26-B4 ⑥：0.62 → 0.68（带宽 0.14 → 0.16）</b>——覆盖 5% → 2-3%
     * （更稀更大）；判据侧 {@code SWAMP_TIER_SHARE_BAND} DEEP 行随缩样读数重钉。
     */
    private static final double SWAMP_DEEP_GATE_LO = 0.68D;
    /** 深水池门带宽。 */
    private static final double SWAMP_DEEP_GATE_SPAN = 0.16D;
    /**
     * 深水池基础下挖。<b>v1.20.49 P26-B4 ⑥：5.0 → 8.0（SPAN 4.0 → 6.0）</b>——潭深 8-14 层
     * （床 54-60 ≫ MIN_HEIGHT 40 大余量；回填侧 {@code fillSwampPools}/SwampFieldGrid 零改动自动跟随）。
     */
    private static final double SWAMP_DEEP_BASE = 8.0D;
    /** 深水池按门加深档（满门下挖 −14 ⇒ 水深 8–14）。 */
    private static final double SWAMP_DEEP_SPAN = 6.0D;
    // —— v1.20.50 P27 批次B-B1 S 片：潭型分型域（软域门负瓣）+ 缓坡床纹 ——
    // （v1.20.53 P30 II-AB 瀑布退役：原正瓣瀑域门及瀑域加密/潭存在性混合删除，负瓣 tyG 缓坡潭域保留）
    /**
     * 潭型<b>分型域</b>盐（波长 {@link #SWAMP_POOLTYPE_SCALE}=157；157 % 16 = 13 ✔ H-1）。盐续
     * ×0x12 等差族：深芯 {@code 0x6811C3E3} / 沟 {@code 0x6811C3F5}（均 G 片）之后，本值占
     * {@code 0x6811C3D1}、缓坡床纹占 {@code 0x6811C407}——主裁决 §0 D7 盐表钉死（沼泽分型域 /
     * 草原深芯 / 草原沟 / 沼泽缓坡床纹四值不推翻）。<b>软域门单场负瓣</b>（SWAMP_NET λ151 先例）：
     * {@code s01((−n−LO)/SPAN)} = 缓坡潭域 tyG（实测 2-32%/seed），带外门值精确 0.0 ⇒ 全部下游
     * 写成 tyG 乘子式（域外 IEEE 逐位不变，digest 对拍圈爆炸半径）。<b>v1.20.53 P30 II-AB</b>：
     * 原同场正瓣（瀑布潭域 tyF，v1.20.50 P27 引入）随沼泽瀑布全套退役删除，本盐只剩负瓣在用。
     */
    private static final long S_SWAMP_POOLTYPE = 0x6811C3D1L;
    /** 分型域波长（域径实测 blob 对角线 117-559 格——大片沼泽（单片 ≥4 万格）含数十个分型域， 混型是统计保证）。 */
    private static final double SWAMP_POOLTYPE_SCALE = 157.0D;
    /** 分型门下檐/带宽（双瓣同式：正瓣 {@code s01((n−LO)/SPAN)}、负瓣 {@code s01((−n−LO)/SPAN)}）。 */
    private static final double POOLTYPE_GATE_LO = 0.45D;
    private static final double POOLTYPE_GATE_SPAN = 0.20D;
    /**
     * 缓坡潭<b>床纹</b>盐（波长 {@link #SWAMP_DEEP_GENTLE_BED}=353；353 % 16 = 1 ✔ H-1）。
     * λ71 门带全宽空间尺度 ~5.3 格，物理不可能 1:3 缓坡（ue-swamp §1 C2）；λ353 带宽 ~26 格 ⇒
     * 上缘前半 ~13 格 <b>1:3.5 可走</b>、近潭心 1:1.5 陡碗心——"上缓下陡"碗 = "缓坡走到深潭旁"。
     * 域内门值级混合（{@code g_eff=(1−ty)·g_陡+ty·g_缓}），ty=0 短路跳过本床纹求值（域外零新噪声）。
     */
    private static final long S_SWAMP_DEEP_GENTLE = 0x6811C407L;
    private static final double SWAMP_DEEP_GENTLE_BED = 353.0D;
    /**
     * 缓坡潭门带宽（LO 沿用 {@link #SWAMP_DEEP_GATE_LO}=0.68 不动：档线切换深
     * {@code depth(0.5)=(B+S/2)/2}=3.75 vs 邻列 ≤1.9 ⇒ 台阶 ~1.85 格 = 可走跳档，ue-swamp §1 C1；
     * DEEP&gt;MARSH&gt;POOL 三档互斥与 §21-D 断言零触碰）。
     */
    private static final double GENTLE_GATE_SPAN = 0.30D;
    /** 缓坡潭基础下挖（{@code (3+9·g)·g} ⇒ 档线 3.75、满门 12——比陡潭浅 2 格的碗）。 */
    private static final double GENTLE_BASE = 3.0D;
    /** 缓坡潭按门加深档（满门最深 −12）。 */
    private static final double GENTLE_SPAN = 9.0D;
    // [v1.20.53 P30 II-AB 瀑布退役] 原 P27 S 片 b' 瀑域加密族常量（带宽除子 BOOST 与下檐
    // 下移 DROP 两乘子）、P28 S 片 ③B 族常量（潭心 blob 三层细分 CORE_GATE/DEEP_GATE、
    // 唇缘环 LIP/LIP_LO/LIP_SPAN、入流扇形 FAN_COS_LO/FAN_SPAN、梯度差分 GRAD_LAG、扇形求值域
    // DOMAIN_LO）随沼泽瀑布全套删除——深门式还原纯陡潭门式，delta 侧唇缘正项与谓词族同批退役。
    /**
     * <b>湖平面压力域缓入带宽</b>（0.02；v1.20.50 P28 引入时作瀑布潭湖岸避让门带宽，瀑布族随
     * v1.20.53 P30 II-AB 退役后改名归 {@link #lakePlaneTerrainAllowedAt} 专用——数值不动，
     * 消费面只剩该门的缓入带：湖平面域外沿 ≈ +45 格连续过渡，域内（lakeAt ≤
     * LAKE_SHORE+SANZU_BIOME_SHORE_JITTER）门 0）。
     */
    private static final double SWAMP_LAKE_SHORE_BAND = 0.02D;
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
    /**
     * 泥丘场域盐（波长 {@link #SWAMP_HUMMOCK_SCALE}）。
     * <b>v1.20.49 P26-B4 ⑧：波长 113 → 173</b>（173 % 16 = 13 ✔；丘径 20-40 格"泥炭丘"档）。
     */
    private static final long S_SWAMP_HUMMOCK = 0x6811C341L;
    /** 泥丘波长（113 % 16 = 1 ✔；P26-B4 ⑧ 起 = 173）。 */
    private static final double SWAMP_HUMMOCK_SCALE = 173.0D;
    /** 泥丘门下檐（满门 P(n≥0.75) ≈2%、缓入 0.45）。 */
    private static final double SWAMP_HUMMOCK_GATE_LO = 0.45D;
    /** 泥丘门带宽。 */
    private static final double SWAMP_HUMMOCK_GATE_SPAN = 0.30D;
    /**
     * 泥炭丘抬升下/上沿。<b>v1.20.49 P26-B4 ⑧：2.0/2.5 → 3.5/4.5</b>（+3.5..+8；
     * +8 上探后距夹持目标 68 抬到 ~76，仍在 {@code softMin(·,172,4)} 的 ≤168 逐位不动带 ⇒ 安全）。
     */
    private static final double SWAMP_HUMMOCK_BASE = 3.5D;
    private static final double SWAMP_HUMMOCK_SPAN = 4.5D;
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
     * <b>非水体项（④ 泥丘 + ⑤ 炭屑滩）在水体档列的正侧钳</b>（格；<b>v1.20.53 P30 II-AB 新增</b>）：
     * {@link #SWG_TIER} ≠ NONE 的列上 {@code dry} 合成正侧钳 {@code min(dry, +3.0)}——满门泥丘
     * +3.5..+8 削顶到 +3.0，防泥丘把水体档列的床抬过该档回填水面（"挖而不灌"的反向穿透）；
     * NONE 列<b>不限</b>（干沼草甸上的泥丘岛设计保留，P20 §5 S4 的字面计划值照旧）。与
     * {@link #SWAMP_NONWATER_DIG_MAX}（下挖侧，全档作用）互补成双边限幅；tier 读已有槽值，
     * 不重复求值。
     */
    private static final double SWAMP_NONWATER_RISE_MAX = 3.0D;
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
    // —— 沼泽边缘门（v1.20.42 P22 A3 引入；v1.20.53 P30 II-AB 连续化：三档水体 + 微池避群系边缘）——
    /**
     * <b>连续边缘门下檐</b>（w3 = {@link #weightsAt} 权重向量的 roster 3 分量）：w3 ≤ 本值门精确
     * 0.0（群系混合等值线外沿），w3 ≥ 本值 + {@link #SWAMP_INTERIOR_GATE_SPAN} = 0.55 满门 1.0。
     * 深腹地均匀区 w3 = 1.0 精确 ⇒ 门=1.0（乘子式在门=1 区 IEEE 逐位不变）。<b>v1.20.53 P30
     * II-AB</b>：替代 v1.20.42 P22 A3 的 coarse Chebyshev 4 格布尔阶梯（边缘截断水潭清零的同一
     * 目的——A3 探针改前基线：边缘截断水列 1069 / 8 seed×512²；潭缘改为群系混合等值线，非直角）。
     */
    private static final double SWAMP_INTERIOR_GATE_LO = 0.40D;
    /**
     * <b>连续边缘门带宽</b>（w3 0.40→0.55 软入 = 混合等值线两侧的连续过渡带）：w3 与地形混合
     * 同一连续面（{@link #VAR_CELL_CACHE} 粗格缓存）⇒ 零新身份面、零新噪声求值。
     */
    private static final double SWAMP_INTERIOR_GATE_SPAN = 0.15D;
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
    /**
     * 槽位：<b>缓坡潭域门</b>（v1.20.50 P27 批次B-B1 S 片；λ157 负瓣 tyG，深水池门值级混合
     * 与 depth 参数同型混合的域权——消费面经 {@link #swampGentleDomainAt} 单一出口。
     * 原同场正瓣瀑域门槽已随 v1.20.53 P30 II-AB 瀑布退役删除，本槽序号由 7 收为 6）。
     */
    private static final int SWG_TYG = 6;
    private static final int SWG_COUNT = 7;
    /**
     * 沼泽门值的线程私有 scratch（<b>零分配</b>：本类每列都会被调，delta 组合与分档判定共用一份，
     * 免得两处各算一遍形成"同式复算"的第二真值）。纪律同 {@link #VAR_CELL_CACHE}：线程私有、
     * 就地取用、不跨列持有。{@link #SWG_TIER} 槽承载单点分流出的档位（P20 §21-D；v1.20.53
     * P30 II-AB 起含连续边缘门衰减）；{@link #SWG_TYG} 承载缓坡潭域门值（P27 S 片）。
     */
    private static final ThreadLocal<double[]> SWAMP_GATE_SCRATCH = ThreadLocal
        .withInitial(() -> new double[SWG_COUNT]);

    // —— 软削顶（A 模板）——
    /**
     * 加权总 delta 的光滑上界（softMin k=10；≤86 逐位不动，渐近 96）。
     * <b>v1.20.41 随森林 delta 上抬 1.43×（{@link #HILL_AMP_FOREST}）同步 26→32、k 8→10</b>；
     * <b>v1.20.49 P26-B4 ①（D7' 破顶放大档）：32 → 96</b>——延绵脊（{@link #FOREST_SPINE_AMP}）与
     * 山地碎坡项解封后典型合成 delta 进入 60-95 带；k=10 不动 ⇒ "δ≤86 逐位不动"的构造性保证
     * 与旧口径同形（旧值 δ≤22 ⇒ 32/10 档）。顶部解封只放行 delta &gt; 22 的顶部路径列；|delta| ≤ 22
     * 的全部列逐位不变（均匀区 digest 对拍保证，见类注释）。
     */
    private static final double DELTA_CAP = 96.0D;
    /** 削顶圆角 k（与 {@link #DELTA_CAP} 同批由 8 抬到 10；P26-B4 不动）。 */
    private static final double DELTA_CAP_K = 10.0D;
    /**
     * 结果高度的光滑哨兵（softMin k=4；≤168 逐位不动，恒 &lt;172 ⇒ 180 钳制零截平）。
     * <b>v1.20.49 P26-B4 ①（D7'）：108 → 172</b>——沼泽构造性 ≤~76、草原 ≤~112、荒漠 ≤~140
     * 均在 ≤168 逐位不动带 ⇒ 三群系顶部以下列逐位不变（均匀区 digest 对拍证）；森林顶部路径解封
     * （峰目标 150~165，经 {@link #DELTA_CAP}=96 与本哨兵双重软顶 ⇒ 恒 &lt; 180 硬钳线，CLAMP 上沿零触）。
     */
    private static final double HEIGHT_SENTINEL = 172.0D;

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
     * <p>
     * v1.20.53 P30 I5：湖平面压力域内<b>不建异族负地形</b>（防深坑→深水潭）——w[0]/w[1] 两支
     * （草原盆地/沟壑/低地与森林谷地）的 delta 出口各乘 {@link #lakePlaneTerrainAllowedAt}
     * （局部量 w0g/w1g，与 P29 C2/C3 的 w2g/w3g <b>同一豁免面收口</b>），设计滩环不再被负项
     * 挖出深坑、再被主湖灌水门灌成深水潭；域外门=1 ⇒ 乘子 IEEE 逐位不变（era 闭合）。
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
                // ═══ v1.20.53 P30 I5 片：湖平面域豁免乘子 w0g = w[0] × lakePlaneTerrainAllowedAt ═══
                // 平面域（lake ≤ SHORE+JITTER，含抖动滩缘整环）门=0 ⇒ 下方五条 delta（丘陵+盆地/
                // 碗芯/缎带沟/低地）归零——设计滩环不再被草原盆地/ridged 沟壑/低地负项（单臂最坏
                // −16）挖出深坑、再被主湖灌水门灌成深水潭（湖平面压力域内不建异族负地形）；
                // 域外（门=1）w0g = w[0]×1.0 IEEE 逐位同（era 闭合）；带内各中间量（basinGate/
                // gullyGate/lowGate 等）照常求值、只在出口乘门——单点分流纪律（§21-D 同款）。
                // lakeAt 走 LAKE_MEMO 命中（heightCore 顶部已算同列 lake）；与 w2g/w3g（P29 C2/C3
                // 先例）同一豁免面收口。
                final double w0g = w[0] * lakePlaneTerrainAllowedAt(worldSeed, x, z);
                delta += w0g * (HILL_AMP_STEPPE * m * gateS);
                // —— 小盆地支路（v1.20.49 P26-B4 ⑪：λ167 负瓣封闭碗形，第一版不集水）——
                // 式 = 低地/谷地同款"外侧乘门"软门：门关死列贡献精确 0（−0.0），缓入环从 0 连续过渡；
                // 负瓣等值线天然闭合 ⇒ 碗形无需显式边界。负 delta 直通 DELTA_CAP（softMin 只封上侧）。
                final double bn = GTSRWorldgenHash
                    .valueNoise(worldSeed ^ S_STEPPE_BASIN, x / STEPPE_BASIN_SCALE, z / STEPPE_BASIN_SCALE);
                final double basinGate = s01((-bn - STEPPE_BASIN_GATE_LO) / STEPPE_BASIN_GATE_SPAN);
                delta += w0g * (-(STEPPE_BASIN_BASE + STEPPE_BASIN_SPAN * basinGate) * basinGate);
                // —— 盆内深芯第二档（v1.20.50 P27 G 片：碗中碗，只放大既有碗）——
                // 式 = 盆地同款"外侧乘门"软门，再乘 basinGate ⇒ 芯只在碗内下挖（域外 IEEE 精确 −0.0，
                // 逐位不改变 delta；与低地/谷地同款纪律）。芯缘 Δh 7 落在 λ89 缓入 ~8-15 格上 ⇒
                // 芯缘出 1-2 格窄石带——"深=露石"的语义自洽（侧壁露石是表层状态机涌现，非直写层）。
                final double cn = GTSRWorldgenHash.valueNoise(
                    worldSeed ^ S_STEPPE_BASIN_CORE,
                    x / STEPPE_BASIN_CORE_SCALE,
                    z / STEPPE_BASIN_CORE_SCALE);
                final double coreGate = s01((-cn - STEPPE_BASIN_CORE_GATE_LO) / STEPPE_BASIN_CORE_GATE_SPAN);
                delta += w0g * (-(STEPPE_BASIN_CORE_BASE + STEPPE_BASIN_CORE_SPAN * coreGate) * coreGate * basinGate);
                // —— 缎带沟（v1.20.50 P27 G 片：λ73 ridged 负像（redirect 由 λ57 收窄），切平地、避碗避丘）——
                // ridged 形 1−|n| 沿 n≈0 等值线取窄带（线状沟，非门阈 blob）；互斥全用<b>已求值</b>门
                // 相乘（零新求值）：×(1−basinGate) 防沟切碗（合成 −15+ 怪坑）、×(1−gateS) 复用丘陵门
                // （:781 已求值）作存在域 ⇒ 沟只切平坦草原、被丘陵域截断成短段。带外 s01=0 ⇒
                // gullyGate 精确 +0.0 ⇒ 本支路贡献 −0.0，域外逐位不改变 delta。
                final double gn = GTSRWorldgenHash
                    .valueNoise(worldSeed ^ S_STEPPE_GULLY, x / STEPPE_GULLY_SCALE, z / STEPPE_GULLY_SCALE);
                final double gullyGate = s01((1.0D - Math.abs(gn) - STEPPE_GULLY_GATE_LO) / STEPPE_GULLY_GATE_SPAN)
                    * (1.0D - basinGate)
                    * (1.0D - gateS);
                delta += w0g * (-(STEPPE_GULLY_BASE + STEPPE_GULLY_SPAN * gullyGate) * gullyGate);
                // —— 低地支路（v1.20.41 需求 6：半空间折叠只取负瓣 + 覆盖门；与丘陵同域叠加）——
                // 折叠式与沙丘 (d−|d|)/2 同款：d≥0 ⇒ 精确 0 ⇒ 该支路在门带外逐位不改变 delta。
                // P26-B4 ⑪：低地门乘 (1−盆地带)（域外 ×1.0 逐位不变）——盆地与低地不叠加（最坏
                // 合成下挖 ≤ −9 = 盆地满门单臂，防两项相加 −15 吃 y=40 地板预算；C0 连续，无硬环）。
                // P27 G 片：乘式加长 ×(1−gullyGate)（沟带外 gullyGate=+0.0 ⇒ ×1.0 IEEE 逐位不变）——
                // 沟与低地不相加 ⇒ 三负臂互斥、单臂最坏 = max(−16 深芯, −6.5 沟, −6 低地)。
                final double dl = GTSRWorldgenHash
                    .valueNoise(worldSeed ^ S_STEPPE_LOW, x / STEPPE_LOW_SCALE, z / STEPPE_LOW_SCALE);
                final double lowFold = (dl - Math.abs(dl)) * 0.5D; // ∈ [−0.5, 0]
                final double lowShape = Math.min(1.0D, -2.0D * lowFold); // 形状 0..1（d≤−0.5 取满）
                final double lowGate = s01((-dl - STEPPE_LOW_GATE_LO) / STEPPE_LOW_GATE_SPAN) * (1.0D - basinGate)
                    * (1.0D - gullyGate);
                delta += w0g * (-(STEPPE_LOW_BASE + STEPPE_LOW_SPAN * lowShape) * lowGate);
            }
            if (w[1] > 0.0D) {
                // ═══ v1.20.53 P30 I5 片：湖平面域豁免乘子 w1g = w[1] × lakePlaneTerrainAllowedAt ═══
                // 平面域（lake ≤ SHORE+JITTER，含抖动滩缘整环）门=0 ⇒ 下方两条 delta（丘陵/岭脊/
                // 山脉脊 + 谷地负瓣）归零——设计滩环不再被森林谷地负项（最深 −10）挖出深坑、再被
                // 主湖灌水门灌成深水潭（湖平面压力域内不建异族负地形）；域外（门=1）w1g = w[1]×1.0
                // IEEE 逐位同（era 闭合）；带内各中间量（ridge/mf/spine/valleyGate 等）照常求值、
                // 只在出口乘门——单点分流纪律（§21-D 同款）。lakeAt 走 LAKE_MEMO 命中（heightCore
                // 顶部已算同列 lake）；与 w0g/w2g/w3g 同一豁免面收口（P29 C2/C3 先例）。
                final double w1g = w[1] * lakePlaneTerrainAllowedAt(worldSeed, x, z);
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
                // —— 延绵山脉脊（v1.20.49 P26-B4 ②：λ433 ridged，域门复用 mf（山地门 λ512，
                // 森林列已求值 ⇒ 零加费）；脊线沿 |sn|≈0 等值线连续延伸 200-400 格（λ433 半波 ~216）。
                // 域外（脊形门关死）spine 精确 0 ⇒ 该支路逐位不改变 delta；脊峰经 DELTA_CAP=96 与
                // HEIGHT_SENTINEL=172 双重软顶 ⇒ 恒 < 180 钳制线（裸岩树线归 decorate 侧 TREE_LINE 门）。
                final double sn = GTSRWorldgenHash
                    .valueNoise(worldSeed ^ S_FOREST_SPINE, x / FOREST_SPINE_SCALE, z / FOREST_SPINE_SCALE);
                final double spine = FOREST_SPINE_AMP
                    * s01((1.0D - Math.abs(sn) - FOREST_SPINE_GATE_LO) / FOREST_SPINE_GATE_SPAN)
                    * mf;
                delta += w1g * (forest + ridge + spine);
                // —— 森林谷地负瓣（v1.20.42 P22 A4：顶部路径被 DELTA_CAP 软顶/108 哨兵/振幅档域三面
                // 封死后，起伏补强换到下侧——负瓣只在下侧花预算（y=40 地板现状 145 列 vs 预算 943）。
                // 式 = 草原低地同款"外侧乘门"软门：门关死列贡献精确 0，缓入环从 0 连续过渡；
                // 负 delta 直通 DELTA_CAP（softMin 只封上侧），最深 −10 ⇒ 与岭脊 +18 拉开 ≥28 格谷-脊差。
                final double vn = GTSRWorldgenHash
                    .valueNoise(worldSeed ^ S_FOREST_VALLEY, x / FOREST_VALLEY_SCALE, z / FOREST_VALLEY_SCALE);
                final double valleyGate = s01((-vn - FOREST_VALLEY_GATE_LO) / FOREST_VALLEY_GATE_SPAN);
                delta += w1g * (-(FOREST_VALLEY_BASE + FOREST_VALLEY_SPAN * valleyGate) * valleyGate);
            }
        }
        // —— 荒漠沙丘（RTG dunes 参数化 + domain-warp；无丘陵）——
        if (w[2] > 0.0D) {
            // ═══ v1.20.52 P29 C 片 C3：湖平面域衰减乘子 w2g = w[2] × lakePlaneTerrainAllowedAt ═══
            // 平面域（lake ≤ SHORE+JITTER，含抖动滩缘整环）门=0 ⇒ 下方两条 delta（沙海巨丘+风蚀
            // 巨柱/座台）归零——滩带 q→1 段不再爬回荒漠丘状地形（p29-r §2.2-3 根因收口）；
            // 域外（门=1）w2g = w[2]×1.0 IEEE 逐位同（era 闭合）；带内各中间量（duneGate/bryce/
            // site 等）照常求值、只在出口乘门——单点分流纪律（§21-D 同款）。lakeAt 走 LAKE_MEMO
            // 命中（heightCore 顶部已算同列 lake）。
            final double w2g = w[2] * lakePlaneTerrainAllowedAt(worldSeed, x, z);
            // —— 沙海域域门（v1.20.49 P26-B4 ⑤：λ281；域外 duneGate 精确 0 ⇒ 下方各增量逐位 0：
            // warpAmp/mainWave/gain/quad 的 "+增量×gate" 项在 gate=0 处分别等于旧常量（IEEE 精确），
            // 均匀区（沙海域域外）读数与改造前逐位相同——digest 对拍保证）——
            final double duneGate = s01(
                (GTSRWorldgenHash.valueNoise(worldSeed ^ S_DUNE_SEA, x / DUNE_SEA_SCALE, z / DUNE_SEA_SCALE)
                    - DUNE_SEA_GATE_LO) / DUNE_SEA_GATE_SPAN);
            final long warpSeed = worldSeed ^ S_DUNE_WARP;
            final double warpAmp = DUNE_WARP_AMP + DUNE_SEA_WARP_BOOST * duneGate;
            final double wx = x + warpAmp * GTSRWorldgenHash.valueNoise(warpSeed, x / 20.0D, z / 20.0D);
            final double st = 0.38D
                + 0.30D * GTSRWorldgenHash.valueNoise(worldSeed ^ S_DUNE_STRENGTH, x / 224.0D, z / 224.0D);
            // 主波波长 λ60 → 域内有效 λ120（/(60·(1+gate)) 连续过渡 ⇒ 丘距 120 格的丘链尺度）
            final double mainWave = 60.0D * (1.0D + duneGate);
            final double main = GTSRWorldgenHash.valueNoise(worldSeed ^ S_DUNE_MAIN, wx / mainWave, z / mainWave);
            final double gain = DUNE_MAIN_GAIN + DUNE_SEA_GAIN_BOOST * duneGate;
            final double d = main * st * gain;
            final double fold = (d - Math.abs(d)) * 0.5D;
            final double p = -fold;
            final double quad = DUNE_RIDGE_QUAD + DUNE_SEA_QUAD_BOOST * duneGate;
            final double bump = p * (p * quad + DUNE_RIDGE_LIN);
            final double ripple = GTSRWorldgenHash.valueNoise(warpSeed, wx / 17.0D, z / 17.0D) * (0.6D + st);
            delta += w2g * (bump - 1.3D * st + ripple);
            // —— 风蚀山体 + 风蚀柱座台（v1.20.41 需求 5；RTG terrainBryce 倒数式范式，四层求和）——
            // 四层取有符号值各一次：绝对值进 sn（柱身"细而陡"），最低频层 n₃ 另作山体门、
            // λ53 层 n₂ 另作风蚀柱落点场 ⇒ 与计划"荒漠每列 +4 次"口径一致（门/座台零额外求值）。
            final double n0 = GTSRWorldgenHash.valueNoise(worldSeed ^ S_BRYCE_0, x / BRYCE_SCALE_0, z / BRYCE_SCALE_0);
            final double n1 = GTSRWorldgenHash.valueNoise(worldSeed ^ S_BRYCE_1, x / BRYCE_SCALE_1, z / BRYCE_SCALE_1);
            final double n2 = GTSRWorldgenHash.valueNoise(worldSeed ^ S_BRYCE_2, x / BRYCE_SCALE_2, z / BRYCE_SCALE_2);
            final double n3 = GTSRWorldgenHash.valueNoise(worldSeed ^ S_BRYCE_3, x / BRYCE_SCALE_3, z / BRYCE_SCALE_3);
            final double snW = Math.abs(n0) + BRYCE_W1 * Math.abs(n1)
                + BRYCE_W2 * Math.abs(n2)
                + BRYCE_W3 * Math.abs(n3);
            // P26-B4 ⑤：沙海域域内与风蚀山体互斥（bryce 乘 (1−duneGate)，域心钳 0——防柱脚被巨丘
            // 埋/穿；域外 ×1.0 ⇒ 既有风蚀山体列逐位不变。单点分流的又一应用（§21-D 同款）。
            final double bryceGate = s01((n3 - BRYCE_GATE_LO) / BRYCE_GATE_SPAN) * (1.0D - duneGate);
            final double bryce = bryceGate
                * softMin(BRYCE_GAIN / (BRYCE_FLOOR + BRYCE_SLOPE * snW), BRYCE_CAP, BRYCE_CAP_K);
            // 座台走公开谓词（与 populate 侧柱 feature 同一份式子，杜绝"柱落在无座台的平沙上"）；
            // P26-B4 ④：巨柱窄带（site ≥ 0.88 ⇔ n₅₃ ≥ ~0.90）同场分档 +1.5 → 域心 +3.0
            // （窄带外 ×1.0 ⇒ 既有座台列逐位不变；落点场本身不动 = 零第二真值）。
            final double site = windSpineSiteAt(worldSeed, x, z);
            final double giantNarrow = s01((site - SPINE_PEDESTAL_GIANT_GATE_LO) / SPINE_PEDESTAL_GIANT_GATE_SPAN);
            delta += w2g * (bryce + SPINE_PEDESTAL * (1.0D + SPINE_PEDESTAL_GIANT_BOOST * giantNarrow) * site);
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
                // ═══ v1.20.52 P29 C 片 C2：湖平面域豁免乘子 w3g = w[3] × lakePlaneTerrainAllowedAt ═══
                // 平面域（lake ≤ SHORE+JITTER）门=0 ⇒ 夹持/三档下挖/泥丘炭屑全 0（h0 原样，
                // 湖滩不再被挖出沼泽潭/沼洼）；域外（门=1）w3g = w[3]×1.0 IEEE 逐位同（era 闭合）；
                // 45 格缓入带内按门缩放。lakeAt 走 LAKE_MEMO 命中（heightCore 顶部 :518-538 已算同列 lake）。
                final double w3g = w[3] * lakePlaneTerrainAllowedAt(worldSeed, x, z);
                final double f = 0.55D + 0.40D * gate;
                // —— P20 §21-D：三档<b>水体项</b>在 delta 侧同样<b>互斥</b>，只落 g[SWG_TIER] 那一档 ——
                // g[SWG_TIER] 就是 swampTierAt 的返回值（同一次 swampGates、同一个槽），故"地形侧算到哪
                // 一档深"与"回填侧判哪一档"严格同口径；其余两档在本式里根本不参与求值 ⇒ 修复前"被判为
                // 表面池的列被同列深水池/水沼地项挖到 ≥4 格"（实测 7111/62169 = 11.4%，NONE 档 1258 列）
                // 的相加穿透在构造上归零。门比较只有 swampGates 里那一份，本处不写第二遍。
                final int tier = (int) g[SWG_TIER];
                final double water;
                if (tier == SWAMP_TIER_DEEP) {
                    // ② 深水池：−(8.0 + 6.0·门) ⇒ 水深 8–14（与表面池 1–2 留 4 格分水岭）。
                    // P27 S 片：缓坡域 depth 参数<b>同型混合</b>（ty=0 ⇒ base/span 还原 8.0/6.0
                    // 逐位——8·1+3·0=8、6·1+9·0=6，IEEE 精确）；门 g[SWG_DEEP] 已是混合后的 g_eff。
                    final double tyG = g[SWG_TYG];
                    final double deepBase = SWAMP_DEEP_BASE * (1.0D - tyG) + GENTLE_BASE * tyG;
                    final double deepSpan = SWAMP_DEEP_SPAN * (1.0D - tyG) + GENTLE_SPAN * tyG;
                    water = (deepBase + deepSpan * g[SWG_DEEP]) * g[SWG_DEEP];
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
                // ═══ v1.20.53 P30 II-AB：非水体项双边限幅 ═══ 下挖侧（全档）SWAMP_NONWATER_DIG_MAX
                // 不动；tier≠NONE 列（含连续边缘门衰减后的档位，读已有 SWG_TIER 槽零重复求值）
                // 正侧另钳 min(dry, SWAMP_NONWATER_RISE_MAX=+3.0)——满门泥丘 +3.5..+8 削顶，防泥丘
                // 把水体档列的床抬过该档回填水面；NONE 列不限（草甸泥丘岛设计保留）。
                // 原 v1.20.50 P28 S 片 ③B 的唇缘环正项随沼泽瀑布全套退役删除（门关区与深腹地
                // 列 delta 逐位不变——被删项在其门=0/tyF 退 0 区本就精确 0）。
                double nonWater = Math.max(-SWAMP_NONWATER_DIG_MAX, dry);
                if (tier != SWAMP_TIER_NONE) {
                    nonWater = Math.min(nonWater, SWAMP_NONWATER_RISE_MAX);
                }
                delta += w3g * ((h0 * (1.0D - f) + target * f) - h0 - water + nonWater);
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
     * <b>三档沼泽列谓词</b>（v1.20.43 P22 版 B O1a，{@link #swampTierAt} 的布尔单一出口，
     * RVF {@code submergedAt} 先例同款）：{@code swampTierAt != SWAMP_TIER_NONE} 的逐字包装——
     * 本列判出三档水体（POOL/DEEP/MARSH 任一档）。消费面同一真值：
     * {@code GTSRRiverPlacer.neighborBarrier} 三档腿。需要档位<b>值</b>的消费面（SwampFieldGrid
     * 的 st[] 档位表 / 地形侧 delta）仍取
     * {@link #swampTierAt} 本值、不走本布尔出口（一处出口一次求值，避免同一列二次求档）。
     */
    public static boolean swampTieredAt(long worldSeed, int x, int z, int rosterIndex) {
        return swampTierAt(worldSeed, x, z, rosterIndex) != SWAMP_TIER_NONE;
    }

    /**
     * 沼泽<b>连续边缘门</b>（<b>v1.20.53 P30 II-AB 新增</b>，替代 v1.20.42 P22 A3 的 coarse
     * Chebyshev 4 格布尔阶梯）：{@code s01((w3 − 0.40)/0.15)}，w3 = {@link #weightsAt} 权重向量的
     * roster 3 分量——<b>潭缘 = 群系混合等值线</b>（11×11 核的连续混合面，非直角 Chebyshev 台阶），
     * 与地形混合同一连续面 ⇒ <b>零新身份面、零新噪声求值</b>（w3 走 {@link #VAR_CELL_CACHE}
     * 粗格缓存，同粗格内恒等；variantAdjustment 首行已算同列 w ⇒ 缓存命中）。深腹地 w3 = 1.0
     * ⇒ 门精确 1.0（乘子区 IEEE 逐位不变）。门带见 {@link #SWAMP_INTERIOR_GATE_LO}/
     * {@link #SWAMP_INTERIOR_GATE_SPAN}。
     * <p>
     * <b>消费契约（单一真值）</b>：地形侧 {@link #swampGates} 的三档门值比较前各乘本门
     * （{@link #SWG_TIER} 槽，边缘带内门值连续衰减——"地形不挖"）；回填侧
     * {@code ChunkProviderProsperityRuins.SwampFieldGrid} 微池腿以 {@code ≥ 0.5}
     * （{@link #TIER_MIN} 中点口径）阈值消费；PTP 微池地形腿经布尔包装 {@link #swampInteriorAt}
     * 同阈值同真值（"回填不灌"）。<b>纯函数</b>、零 {@code net.minecraft} 依赖。
     */
    public static double swampInteriorGateAt(long worldSeed, int x, int z) {
        return s01((weightsAt(worldSeed, x, z)[3] - SWAMP_INTERIOR_GATE_LO) / SWAMP_INTERIOR_GATE_SPAN);
    }

    /**
     * 沼泽<b>腹地谓词</b>（v1.20.42 P22 A3 新增；<b>v1.20.53 P30 II-AB 起改为
     * {@link #swampInteriorGateAt} ≥ {@link #TIER_MIN} 的薄包装</b>——原 coarse Chebyshev R=4
     * 布尔阶梯及其粗格缓存退役，边缘语义连续化，消费面读数 = 连续门中点阈）。保留本布尔出口
     * 是因为 PTP 微池地形腿（heightCore 的 P25 微池腹地门）仍在消费（本片写面无 PTP），两侧
     * 经同一门值同口径；后续片把该消费点换直调 gate 后本包装可删。<b>纯函数</b>。
     */
    public static boolean swampInteriorAt(long worldSeed, int x, int z) {
        return swampInteriorGateAt(worldSeed, x, z) >= TIER_MIN;
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
     * <b>灌木林域</b>谓词（v1.20.49 P26-B4 ⑫ 新增；需求 5 平原"灌木林"的 decorate 侧域门）。
     * <p>
     * 纪律同 {@link #shrubClusterAt}：<b>不进高度链</b>（{@link #variantAdjustment} 一行不读它 ⇒
     * 每列噪声求值数不变），只给 {@code ProsperityDecorPlacer} 的灌木事件补骰/簇加密/草趟加密取
     * 同一真值。式 = λ193 单场软门 {@code s01((n−0.50)/0.18)}（覆盖 ~8-12% 草原列、域径 50-100 格），
     * 带外精确 0.0。消费面按"门值 ≥ 0.5"取域（与簇场 {@code SHRUB_CLUSTER_GATE_MIN} 同中点口径）。
     * <b>纯函数</b>、零 {@code net.minecraft} 依赖、不消费任何 {@code Random}。
     */
    public static double shrubWoodlandAt(long worldSeed, int x, int z) {
        return s01(
            (GTSRWorldgenHash
                .valueNoise(worldSeed ^ S_SHRUB_WOODLAND, x / SHRUB_WOODLAND_SCALE, z / SHRUB_WOODLAND_SCALE)
                - SHRUB_WOODLAND_GATE_LO) / SHRUB_WOODLAND_GATE_SPAN);
    }

    /**
     * 沼泽<b>炭屑滩列</b>谓词（v1.20.49 P26-B4 ⑨ 新增；枯木滩装饰趟的落点腿之一）。
     * 读 {@link #swampGates} 的 {@link #SWG_CHAR} 槽（单一真值：地形侧的炭屑滩下挖与 decorate 侧的
     * 枯木落点同一次门比较），门值 ≥ {@link #TIER_MIN} 才算滩列；roster≠3 恒 false。
     * 与 {@link #swampTierAt} 的 MARSH 档（半淹）互补成枯木滩的完整落点集（炭屑滩是水沼地场负瓣，
     * 与半淹档空间互斥 ⇒ 两腿不重叠）。<b>纯函数</b>；返回的是线程私有 scratch 的即时读数（就地取用）。
     */
    public static boolean swampCharFlatAt(long worldSeed, int x, int z, int rosterIndex) {
        if (rosterIndex != SWAMP_ROSTER) {
            return false;
        }
        return swampGates(worldSeed, x, z, true)[SWG_CHAR] >= TIER_MIN;
    }

    /**
     * 沼泽<b>缓坡潭域</b>谓词（v1.20.50 P27 批次B-B1 S 片新增）：读 {@link #swampGates} 的
     * {@link #SWG_TYG} 槽（λ157 负瓣软门；原同场正瓣瀑域门随 v1.20.53 P30 II-AB 瀑布退役删除，
     * 软域门只剩本负瓣），门值 ≥ {@link #TIER_MIN} 才算缓坡域列；roster≠3 恒 false。地形侧消费在
     * {@link #variantAdjustment}/{@link #swampGates} 内部（门值级混合，不经本布尔出口）；
     * 本谓词留给 decorate 侧后续"潭边差异化"腿的单一真值（P27 未接线，先占位公开）。<b>纯函数</b>。
     */
    public static boolean swampGentleDomainAt(long worldSeed, int x, int z, int rosterIndex) {
        if (rosterIndex != SWAMP_ROSTER) {
            return false;
        }
        return swampGates(worldSeed, x, z, true)[SWG_TYG] >= TIER_MIN;
    }

    // [v1.20.53 P30 II-AB 瀑布退役] 原 P27/P28 的瀑布潭谓词族（瀑域门/潭心深水/高水台/唇缘-高水
    // 湖岸避让门/入流扇形门/g_eff 单点复算镜像 + λ157 单点取值件）随沼泽瀑布全套删除——
    // 生产与判据消费面（CPR SwampFieldGrid 的潭心钳低腿/高水台腿/转换趟）同批摘除，零残留引用。

    /**
     * <b>湖平面压力域地形门</b>（v1.20.52 P29 C 片 C1 新增）：乘子门
     * {@code s01((lakeAt − LAKE_SHORE − SANZU_BIOME_SHORE_JITTER)/SWAMP_LAKE_SHORE_BAND)}——
     * lakeAt ≤ LAKE_SHORE+SANZU_BIOME_SHORE_JITTER（sanzu 平面压力域<b>上确界</b>，含抖动滩缘
     * 整环：shoreAt ∈ [SHORE, SHORE+JITTER) 单边 ≥ 不变式 ⇒ 本门 0 域 ⊇ 平面域，且免逐列
     * sanzuBiomeShoreAt 噪声求值）⇒ <b>0</b>（沼泽/荒漠/草原/森林地形腿豁免：P29 C2/C3 收
     * w[2]/w[3]，P30 I5 收 w[0]/w[1]）；≥ SHORE+JITTER+0.02
     * （{@link #SWAMP_LAKE_SHORE_BAND} = 0.02，≈+45 格）⇒ 1；其间 45 格缓入带。
     * <b>递归安全</b>：lakeAt 是纯湖场、不触 {@code heightAt}（RVF 湖让位腿先例 :2371——本方法从
     * {@link #variantAdjustment}（heightCore 下游）调用不构成环）。<b>纯函数</b>；消费面
     * （P29 C2/C3、P30 I5）：本类 w[0]-w[3] 四支路的 w0g/w1g/w2g/w3g 乘子 + CPR
     * {@code SwampFieldGrid} 构建体的 tier==3 门（PTP 微池第四肢是本门的硬阈镜像，直用局部量
     * lake 零新求值）。
     */
    public static double lakePlaneTerrainAllowedAt(long worldSeed, int x, int z) {
        return s01(
            (GTSRVoronoiRiverField.lakeAt(worldSeed, x, z) - GTSRVoronoiRiverField.LAKE_SHORE
                - GTSRVoronoiRiverField.SANZU_BIOME_SHORE_JITTER) / SWAMP_LAKE_SHORE_BAND);
    }

    /**
     * 沼泽支路的<b>六项门值</b>（五项形态门 + 缓坡潭域门，v1.20.50 P27 批次B-B1 S 片起）
     * <b>+ 水体档位</b>（<b>单一真值</b>：{@link #variantAdjustment} 的下挖 delta 与
     * {@link #swampTierAt} 的分档判定同取本方法，P20 §13 C7 的地形/回填两侧契约）。
     * 槽位见 {@link #SWG_POOL}/{@link #SWG_DEEP}/{@link #SWG_MARSH}/{@link #SWG_HUMMOCK}/
     * {@link #SWG_CHAR}/{@link #SWG_TIER}/{@link #SWG_TYG}；门值全部 {@link #s01}
     * 带通 ⇒ 带外精确 0.0（全软门纪律）。{@link #SWG_DEEP} 槽存的是<b>缓坡域混合后</b>的
     * g_eff（tyG 门值级混合；tyG=0 列上逐位 = 陡门值。<b>v1.20.50 P27/P28 的瀑域加密与
     * λ48 潭存在性混合已随 v1.20.53 P30 II-AB 瀑布退役删除</b>——深门式还原纯陡潭门式）。
     * <p>
     * <b>单点分流（P20 §21-D）</b>：三档水体的 {@code ≥ TIER_MIN} 比较<b>只在本方法末尾写这一遍</b>，
     * 结果落在 {@link #SWG_TIER}；delta 侧与 {@link #swampTierAt} 都读该槽 ⇒ "本列被挖到哪一档深"
     * 与"本列被判为哪一档"是同一个量，三档水体项在 delta 侧因此天然互斥。
     * <b>v1.20.53 P30 II-AB 起比较前三档门值各乘连续边缘门 {@link #swampInteriorGateAt}</b>
     * （乘子式：深腹地门=1.0 ⇒ IEEE 逐位不变；边缘带内门值连续衰减 ⇒ 潭缘 = 群系混合等值线，
     * 替代 v1.20.42 P22 A3 的 Chebyshev 布尔钳 NONE）。
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
            g[SWG_TYG] = 0.0D;
            g[SWG_TIER] = SWAMP_TIER_NONE;
            return g;
        }
        // ═══ 潭型分型域（λ157 软域门负瓣 tyG = 缓坡潭域；v1.20.53 P30 II-AB：正瓣瀑域退役）═══
        // 带外精确 0.0 ⇒ 下游 tyG 乘子式在域外 IEEE 逐位不变（digest 对拍口径）。
        final double tyN = GTSRWorldgenHash
            .valueNoise(worldSeed ^ S_SWAMP_POOLTYPE, x / SWAMP_POOLTYPE_SCALE, z / SWAMP_POOLTYPE_SCALE);
        final double tyG = s01((-tyN - POOLTYPE_GATE_LO) / POOLTYPE_GATE_SPAN);
        g[SWG_TYG] = tyG;
        // 深水池门：纯陡潭门式（v1.20.53 P30 II-AB 瀑布退役：b' 瀑域加密两乘子与 λ48 潭存在性
        // 混合已删，式子与 v1.20.50 P27 前逐位一致）……
        double deepGate = s01(
            (GTSRWorldgenHash.valueNoise(worldSeed ^ S_SWAMP_DEEP, x / S_SWAMP_DEEP_BED, z / S_SWAMP_DEEP_BED)
                - SWAMP_DEEP_GATE_LO) / SWAMP_DEEP_GATE_SPAN);
        // ……叠缓坡域<b>门值级混合</b> g_eff=(1−ty)·g_陡+ty·g_缓（ue-swamp §4-H 关键写法：
        // ty=0 短路跳过 λ353 求值 ⇒ 域外零新噪声且 g_eff 逐位 = g_陡；SWG_TIER 仍只比
        // g_eff（×边缘门）≥ TIER_MIN 这一遍——单点分流结构不动，三档互斥与 §21-D 断言保持）。
        if (tyG != 0.0D) {
            final double gentleGate = s01(
                (GTSRWorldgenHash
                    .valueNoise(worldSeed ^ S_SWAMP_DEEP_GENTLE, x / SWAMP_DEEP_GENTLE_BED, z / SWAMP_DEEP_GENTLE_BED)
                    - SWAMP_DEEP_GATE_LO) / GENTLE_GATE_SPAN);
            deepGate = (1.0D - tyG) * deepGate + tyG * gentleGate;
        }
        g[SWG_DEEP] = deepGate;
        final double mn = GTSRWorldgenHash
            .valueNoise(worldSeed ^ S_SWAMP_MARSH, x / SWAMP_MARSH_SCALE, z / SWAMP_MARSH_SCALE);
        g[SWG_MARSH] = s01((mn - SWAMP_MARSH_GATE_LO) / SWAMP_MARSH_GATE_SPAN);
        // 炭屑滩复用同场的负瓣（"同一纯函数域"的字面兑现）⇒ 零额外求值，且与水沼地空间互斥
        g[SWG_CHAR] = s01((-mn - SWAMP_CHAR_GATE_LO) / SWAMP_CHAR_GATE_SPAN);
        g[SWG_HUMMOCK] = s01(
            (GTSRWorldgenHash.valueNoise(worldSeed ^ S_SWAMP_HUMMOCK, x / SWAMP_HUMMOCK_SCALE, z / SWAMP_HUMMOCK_SCALE)
                - SWAMP_HUMMOCK_GATE_LO) / SWAMP_HUMMOCK_GATE_SPAN);
        // 唯一的门比较（P20 §21-D 的单点分流）：两侧（delta / 回填档位）都只读本槽；
        // P30 II-AB 连续边缘门 = 比较前各乘 edgeGate（深腹地门=1.0 ⇒ IEEE 逐位不变；边缘带内
        // 连续衰减 ⇒ 非腹地列档位随门值缩退，替代 v1.20.42 A3 的布尔钳 NONE——"地形不挖、
        // 回填不灌"经同一 SWG_TIER 槽自动同口径）。
        final double edgeGate = swampInteriorGateAt(worldSeed, x, z);
        g[SWG_TIER] = g[SWG_DEEP] * edgeGate >= TIER_MIN ? SWAMP_TIER_DEEP
            : (g[SWG_MARSH] * edgeGate >= TIER_MIN ? SWAMP_TIER_MARSH
                : (g[SWG_POOL] * edgeGate >= TIER_MIN ? SWAMP_TIER_POOL : SWAMP_TIER_NONE));
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
        final VarSlot[] slots = VAR_CELL_CACHE.get();
        final VarSlot slot = slots[ProsperityTerrainProfile
            .cellSlotIndex(worldSeed, cellX, cellZ, VAR_CELL_CACHE_CAP - 1)];
        if (slot.valid && slot.seed == worldSeed && slot.cx == cellX && slot.cz == cellZ) {
            return slot.w;
        }
        // 未命中：整槽重算（w 逐项清 0 后累加，与旧路径同一批加法同一次序）。
        final double[] w = slot.w;
        for (int i = 0; i < w.length; i++) {
            w[i] = 0.0D;
        }
        for (int k = 0; k < VAR_KERNEL_DX.length; k++) {
            final int r = ProsperityTerrainProfile
                .chainRosterIndexAt(worldSeed, cellX + VAR_KERNEL_DX[k], cellZ + VAR_KERNEL_DZ[k]);
            if (r >= 0 && r < ROSTER_SLOTS) {
                w[r] += VAR_KERNEL_W[k];
            }
        }
        slot.seed = worldSeed;
        slot.cx = cellX;
        slot.cz = cellZ;
        slot.valid = true;
        return w;
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
