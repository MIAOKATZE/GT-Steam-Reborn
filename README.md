<h1 align="center">GT-Steam-Reborn</h1>
<p align="center"><strong><em>GTNH Steam Age Expansion Mod</em></strong><br><strong><em>GTNH 蒸汽时代扩展模组</em></strong></p>

A GregTech New Horizons expansion mod that **supplements the Steam Age and significantly expands steam usage**, providing 22 multiblock steam machines, 8 single-block nodes, 15 types of hatches plus 4 singularity compartments and 3 hub storage units, and a Hub-Node binding system. It fills the gameplay gap between the steam age and the electric age in GTNH, making steam a viable and deep progression path rather than a transient phase.

一个 GregTech New Horizons 扩展模组，**补充蒸汽时代并显著拓展蒸汽用途**，提供22台多方块蒸汽机器、8个单方块节点、15类仓室与4个奇点仓、3种存储单元以及枢纽-节点绑定系统。它填补了 GTNH 蒸汽阶段到电力阶段之间的玩法空白，让蒸汽成为一条可行且有深度的进阶路线，而非过渡阶段。

> [!NOTE]
> This is an unofficial mod. Please avoid discussing this mod in official GTNH forums.
> 这是一个非官方模组，讨论此模组时请注意场合。

> 📖 **完整文档请查阅 [Wiki](https://github.com/MIAOKATZE/GT-Steam-Reborn/wiki) / For full documentation, see the [Wiki](https://github.com/MIAOKATZE/GT-Steam-Reborn/wiki)**

## Downloads & Requirements / 下载与版本需求

| GTNH         | GTSR           | Maintenance / 维护 |
| ------------ | -------------- | :--------------: |
| 2.9.0 beta-2 | 1.7.16+        |        ✔️        |
| 2.9.0 beta-1 | 1.7.1\~1.7.15  |        ✔️        |
| 2.8.4        | 1.6.0          |        ❌️        |

***

## Multiblock Machines / 多方块机器 (22)

### Storage Hub Machines / 存储枢纽机器 (2)

> [!NOTE]
> 🎨 下方宣传材料均使用了 [Modernity-GTNH](https://github.com/ModernityGTNH/Modernity-GTNH) 材质包，特别感谢其作者带来的出色视觉体验！下载地址见仓库 [Releases](https://github.com/ModernityGTNH/Modernity-GTNH/releases)。
> 🎨 The promotional images below use the [Modernity-GTNH](https://github.com/ModernityGTNH/Modernity-GTNH) resource pack. Many thanks to its authors for the beautiful visuals! Download: see the repository's [Releases](https://github.com/ModernityGTNH/Modernity-GTNH/releases).

<p align="center"><img src="README/MTESteamHubArray-T1.png" width="240" alt="蒸汽枢纽阵列 / Steam Hub Array"> <img src="README/MTESteamHubArray-T2.png" width="240" alt="蒸汽枢纽阵列 / Steam Hub Array"> <img src="README/MTESteamHubArray-T3.png" width="240" alt="蒸汽枢纽阵列 / Steam Hub Array"><br><em>蒸汽枢纽阵列 / Steam Hub Array（青铜/钢/钨钢）</em></p>

**蒸汽枢纽阵列 / Steam Hub Array (SHA)**

3级（青铜/钢/钨钢）蒸汽存储枢纽。接受蒸汽缓存节点，实现双向、跨维度的流体存储与调度。
3-tier (Bronze/Steel/TungstenSteel) steam storage hub. Accepts steam cache nodes for bidirectional, cross-dimensional fluid storage and dispatch.

| 参数 Parameter | 数值 Value |
|----------|-------|
| 等级 Tier | 青铜 / 钢 / 钨钢 Bronze / Steel / TungstenSteel |
| 最大层数 Max Layers | 30 |
| 单元容量 Unit Capacity | 320M / 1.28B / 20.48B L |

**额外功能 / Additional Features**

| 功能 Feature | 说明 Description |
|---|---|
| 节点绑定 Node Binding | 枢纽奇点芯片解锁，总容量×5；绑定蒸汽缓存节点/奇点蒸汽仓与输出仓（奇点仓每仓消耗 1 奇点、模式锁定）Hub Singularity Chip enables node binding, ×5 total capacity; binds steam cache nodes and singularity steam (output) compartments (1 singularity each, mode-locked) |
| 强化芯片 Reinforced Chip | 3级（钨钢）解锁致密/超临界蒸汽，容量×20（优先于×5），并解锁超压缓存节点绑定 Tier 3 unlocks dense/supercritical steam, ×20 capacity (takes priority over ×5), and overpressure node binding |
| 传输 Transfer | 双向、跨维度 Bidirectional, cross-dimensional |
| 自动输出 Auto Output | 20,000,000 L/s（每 tick 1,000,000 L）20,000,000 L/s (1,000,000 L per tick) |

<p align="center"><img src="README/MTEWaterHubArray-T1.png" width="260" alt="蓄水枢纽阵列 / Water Hub Array"> <img src="README/MTEWaterHubArray-T2.png" width="260" alt="蓄水枢纽阵列 / Water Hub Array"><br><em>蓄水枢纽阵列 / Water Hub Array（青铜/钢）</em></p>

**蓄水枢纽阵列 / Water Hub Array (WHA)**

3级（青铜/钢/钨钢）通用流体存储枢纽，不限流体种类（同一枢纽同时仅存一种流体）。接受通用流体缓存节点与奇点输入/输出仓（支持跨维度传输），双向接口。
3-tier (Bronze/Steel/TungstenSteel) universal fluid storage hub — any fluid type allowed (one fluid per hub at a time). Accepts universal fluid cache nodes and singularity fluid input/output compartments (cross-dimensional) with a bidirectional interface.

| 参数 Parameter | 数值 Value |
|----------|-------|
| 材质 Material | 青铜 / 钢 / 钨钢 Bronze / Steel / TungstenSteel |
| 最大层数 Max Layers | 30 |
| 单元容量 Unit Capacity | 1.28M / 5.12M / 20.48M L |
| 维度 Dimension | 跨维度 Cross-dimensional |

**额外功能 / Additional Features**

| 功能 Feature | 说明 Description |
|---|---|
| 容量倍率 Capacity Multiplier | 枢纽奇点芯片使总容量×5（取下会吞掉超出部分的流体）；等级3强化芯片×20（优先于×5）Hub Singularity Chip ×5 total capacity (removing it swallows excess fluid); Reinforced Chip ×20 on tier 3 (takes priority over ×5) |
| 节点绑定 Node Binding | 枢纽奇点芯片解锁（耐压）通用流体缓存节点绑定（不消耗奇点）；强化芯片解锁超压节点绑定；奇点输入/输出仓亦可绑定（模式锁定）Hub Singularity Chip unlocks (reinforced) universal fluid node binding (no singularity cost); Reinforced Chip unlocks overpressure nodes; singularity fluid compartments bind too (mode-locked) |

***

### Singularity Drilling Hub / 奇点钻井枢纽 (1)

<p align="center"><img src="README/MTESingularityDrillingHub.png" width="400" alt="奇点钻井枢纽 / Singularity Drilling Hub"><br><em>奇点钻井枢纽 / Singularity Drilling Hub</em></p>

**奇点钻井枢纽 / Singularity Drilling Hub (SDH)**

仅钢级，必须使用过热蒸汽（无加速效果），驱动钻井与采矿节点；蒸汽消耗随活跃节点数增长。蒸汽时代的奇迹造物：基于蒸汽纠缠奇点，遍及世界每一个角落，攫取一切所需的资源。
Steel only, requires superheated steam (no speed bonus). Drives drilling and miner nodes; steam consumption scales with active node count. A marvel of the steam age: based on steam-entangled singularities, it reaches every corner of the world to extract all needed resources.

| 参数 Parameter | 数值 Value |
|----------|-------|
| 材质 Material | 钢 Steel |
| 蒸汽类型 Steam Type | 过热蒸汽（无加速）Superheated steam (no speed bonus) |
| 基础消耗 Base Consumption | 8,000 L/s + 节点消耗 node cost |

**额外功能 / Additional Features**

| 功能 Feature | 说明 Description |
|---|---|
| 节点消耗 Node Cost | 5,000\~240,000 L/s 每节点（仅工作中；采矿 5 级：5,000 / 10,000 / 20,000 / 80,000 / 240,000）5,000\~240,000 L/s per node (only when working; miner 5 tiers: 5,000 / 10,000 / 20,000 / 80,000 / 240,000) |
| 产出路由 Output Routing | 采矿节点→输出总线；钻井节点→输出仓 Miner node → Output Bus; Drilling node → Output Hatch |
| 绑定 Binding | 需枢纽奇点芯片；手持节点右击绑定/解绑 Requires Hub Singularity Chip; right-click with node to bind/unbind |

***

### Hub-Node Binding System / 枢纽-节点绑定系统

The Hub-Node system is GTSR's core innovation, enabling cross-chunk and cross-dimensional fluid transfer and remote operations.

枢纽-节点系统是 GTSR 的核心创新，实现跨区块甚至跨维度的流体传输和远程作业。

#### Binding Mechanism / 绑定机制

Hold a node item and right-click a hub controller to bind. Singularity cost varies by node type (steam nodes: 0/1/8 by tier; universal fluid nodes: all 0; singularity steam (output) compartments: 1 each; miner/driller: 1). Steam/Water hubs support 3-state cycle: output mode → input mode → unbind. Singularity compartments are mode-locked: when already bound, right-click only unbinds (no mode flip), and the terminal/GUI cannot switch their direction either. Bound nodes re-register with their hub idempotently every 600 ticks (30 s; retried 20 ticks after failure), so if a hub is demolished and rebuilt in place, bindings recover automatically within 30 seconds; hubs without a chip still prune dangling binding records (only fluid transfer requires a chip). Breaking a bound node drops it with the binding retained.

手持节点物品右键枢纽控制器绑定。奇点消耗因节点类型而异（蒸汽节点按等级 0/1/8；通用流体节点全家 0；奇点蒸汽仓/输出仓各 1；采矿/钻井 1）。蒸汽枢纽阵列/蓄水枢纽阵列支持3状态循环：输出模式→输入模式→解绑。奇点仓模式锁定：已绑定时右键仅解绑（无模式翻转），终端/界面也无法切换其方向。绑定成功后节点每 600 tick（30 秒）幂等重登记（失败 20 tick 重试），拆除枢纽后原位重建最迟 30 秒自动恢复绑定；未装芯片的枢纽也会清理悬空绑定记录（仅流体传输需要芯片）。破坏已绑定节点，掉落物保留绑定。

> 📷 图片待配：节点绑定枢纽的操作示意图或流程截图

#### Transfer Mechanism / 传输机制

- **Steam/Water Hub**: Every 20 ticks, transfers fluid between hub and bound nodes at each node's effective rate. Screwdriver on hub toggles overflow output mode. Rate tiers (all six cache nodes + all four singularity compartments; compartment effective rate = fixed base × tier — steam compartments base 8,000,000 L/s, fluid compartments 256,000 L/s): right-click (non-sneaking) with the Hub Terminal cycles 100%→80%→60%→40%→20%→10%→5%→2%→1%→0%. Capacity limit tiers (six cache nodes + the two receiving compartments): sneak+right-click with the Hub Terminal or the GUI button cycles 100%→80%→60%→40%→20%→10%→5%→2%→1%.
- **Drilling Hub**: Consumes steam to drive active nodes. Miner node outputs → hub Output Bus. Drilling node outputs → hub Output Hatch.
- **蒸汽枢纽阵列/蓄水枢纽阵列**：每20tick在枢纽与绑定节点间按有效速率传输流体。螺丝刀切换溢流输出模式。速率档适用于六缓存节点+四个奇点仓（奇点仓有效速率=固定基准×档位：蒸汽两仓基准 8,000,000 L/s、流体两仓 256,000 L/s；枢纽终端右击循环 100%→80%→60%→40%→20%→10%→5%→2%→1%→0%）；容量上限档（六缓存节点+两个接收类奇点仓）由终端潜行右击或 GUI 按钮循环 100%→80%→60%→40%→20%→10%→5%→2%→1%（见下方缓存节点一节）。
- **钻井枢纽**：消耗蒸汽驱动活跃节点。采矿节点产出→枢纽输出总线。钻井节点产出→枢纽输出仓。

#### Hub Terminal / 枢纽终端

The Hub Terminal is a handheld remote management device (crafted with 1 Steam Entangled Singularity surrounded by 8 steel plates). Right-click any hub controller with it to open that hub's status terminal — no more running back and forth between nodes.

枢纽终端是手持远程管理设备（1 蒸汽纠缠奇点 + 8 钢板环绕合成）。手持右击任意枢纽控制器即可打开对应的状态终端，告别在节点之间来回奔波。

**Cache Hub Status Terminal (Steam & Water hubs) / 缓存枢纽状态终端（蒸汽与蓄水枢纽阵列通用）**:

<p align="center"><img src="README/HubTerminalCacheStatus.png" width="400"><br><em>缓存枢纽状态终端 / Cache Hub Status Terminal</em></p>
使用枢纽终端右击控制器，打开状态GUI。 / Right-click the controller on the hub terminal and open the status GUI.

- Per-node display (icon, custom name, coords + dimension, fluid type, storage/capacity) with 16×16 hover-tooltip buttons: rate cycle (six cache nodes + four singularity compartments) / capacity cycle / mode toggle (node↔hub) / auto-output / teleport above the node (consumes 1 Steam Entangled Singularity from your main inventory only after a safe landing spot is confirmed); in-place renaming; handheld shortcuts: right-click (non-sneaking) a node/compartment with the terminal to cycle its rate tier, sneak+right-click to cycle the capacity limit tier (send-type compartments show a locked hint) — direction modes can only be switched via this UI's mode button or by right-clicking the hub while holding the node (singularity compartments are mode-locked and reject both; send-type compartments have no capacity tier) / 每节点显示（图标、自定义名、坐标+维度、流体类型、储量/容量），16×16 悬浮说明按钮：速率循环（六缓存节点+四奇点仓）/ 容量循环 / 模式切换（节点↔枢纽）/ 自动输出 / 传送至节点正上方（确认安全落点后才从主物品栏消耗 1 个蒸汽纠缠奇点）；内嵌重命名；手持快捷操作：终端右击（非潜行）节点/仓循环传输速率档、终端潜行右击循环容量上限档（发送类仓提示容量锁定）——方向模式仅能经本界面模式按钮或持节点右击枢纽切换（奇点仓方向锁定，两者均被拒绝；发送类仓无容量档）

**Drilling Hub Status Terminal / 钻井枢纽状态终端**:

<p align="center"><img src="README/HubTerminalDrillingStatus.png" width="400"><br><em>钻井枢纽状态终端 / Drilling Hub Status Terminal</em></p>
使用枢纽终端右击控制器，打开状态GUI。 / Right-click the controller on the hub terminal and open the status GUI.

- Per-node display (icon, name, tier Mk1-4, status, coords); remote start/stop, quick recycle (needs node stopped/idle, returns mining pipes), in-GUI upgrades, in-place renaming (syncs to WAILA and node GUI) / 每节点显示（图标、名字、等级 Mk1-4、状态、坐标）；远程启停、快捷回收（需节点停止/待机，返还钻管）、UI 内升级、内嵌重命名（同步至 WAILA 与节点 GUI）
- **Phase teleport / 阶段传送**: teleport directly above a bound node (y+1), cross-dimensional; consumes 1 Steam Entangled Singularity from your main inventory only after a safe landing spot is found / 传送到绑定节点正上方（y+1），支持跨维度；仅在找到安全落点后消耗主物品栏 1 个蒸汽纠缠奇点

***

### Steam Processing Machines / 蒸汽加工机器 (7)

All inherit from `MTESteamMultiBase` , supporting normal steam and superheated steam 4x speed.

均继承自 `MTESteamMultiBase`，支持普通蒸汽和过热蒸汽4倍速。

<p align="center"><img src="README/MTELargeSteamFurnace-T1.png" width="260" alt="大型蒸汽熔炉 / Large Steam Furnace"> <img src="README/MTELargeSteamFurnace-T2.png" width="260" alt="大型蒸汽熔炉 / Large Steam Furnace"><br><em>大型蒸汽熔炉 / Large Steam Furnace（青铜/钢）</em></p>

<p align="center"><img src="README/MTEAirCompressor-T1.png" width="260" alt="空气压缩机 / Air Compressor"> <img src="README/MTEAirCompressor-T2.png" width="260" alt="空气压缩机 / Air Compressor"><br><em>空气压缩机 / Air Compressor（青铜/钢）</em></p>

**大型蒸汽熔炉 / Large Steam Furnace (LSF)**

蒸汽驱动的工业化熔炼设备，具有更大的并行数。
Steam-driven industrial smelting equipment with greater parallel capacity.

| 参数 Parameter | 数值 Value |
|----------|-------|
| 材质 Material | 青铜 / 钢 Bronze / Steel |
| 并行 Parallel | 24 / 48 |
| 工作速度 Work Speed | 250%（青铜）/ 500%（钢）250% (Bronze) / 500% (Steel) |
| 蒸汽效率 Steam Efficiency | 60% / 40% |

**空气压缩机 / Air Compressor (AC)**

产出空气（下界维度产出下界空气），远优于普通压缩机的速度与便捷度。
Produces air (or nether air in the Nether dimension) with far greater speed and convenience than ordinary compressors.

| 参数 Parameter | 数值 Value |
|----------|-------|
| 材质 Material | 青铜 / 钢 Bronze / Steel |
| 并行 Parallel | 1 / 4 |

<p align="center"><img src="README/MTEAtmosphericCentrifuge-T1.png" width="260" alt="大气离心机 / Atmospheric Centrifuge"> <img src="README/MTEAtmosphericCentrifuge-T2.png" width="260" alt="大气离心机 / Atmospheric Centrifuge"><br><em>大气离心机 / Atmospheric Centrifuge（青铜/钢）</em></p>

**大气离心机 / Atmospheric Centrifuge (ATC)**

芯片系统：基础配方过滤≤3个输出，稀有气体芯片解锁最多9个输出；青铜级不能安装芯片。
Chip system: basic recipes filter ≤3 outputs, rare gas chip unlocks up to 9 outputs; Bronze tier cannot install chips.

| 参数 Parameter | 数值 Value |
|----------|-------|
| 材质 Material | 青铜 / 钢 Bronze / Steel |
| 并行 Parallel | 4 / 16 |
| 基础输出 Base Outputs | ≤3 |
| 芯片解锁输出 Chip Outputs | 最多 9（稀有气体芯片）Up to 9 (Rare Gas Chip) |
| 芯片安装 Chip Slot | 青铜级不可用 Not available on Bronze |

<p align="center"><img src="README/MTESteamFluidDrill-T1.png" width="260" alt="蒸汽流体钻井 / Steam Fluid Drill"> <img src="README/MTESteamFluidDrill-T2.png" width="260" alt="蒸汽流体钻井 / Steam Fluid Drill"><br><em>蒸汽流体钻井 / Steam Fluid Drill（青铜/钢）</em></p>

<p align="center"><img src="README/MTECrustSteamBorer.png" width="340" alt="地壳蒸汽掘进机 / Crust Steam Borer"><br><em>地壳蒸汽掘进机 / Crust Steam Borer</em></p>

**蒸汽流体钻井 / Steam Fluid Drill (SFD)**

产水/蒸馏水/盐水/岩浆；螺丝刀切换产出模式（仅钢）。
Produces water/distilled water/brine/lava; screwdriver switches output mode (Steel only).

| 参数 Parameter | 数值 Value |
|----------|-------|
| 材质 Material | 青铜 / 钢 Bronze / Steel |
| 蒸馏水效率 Distilled Water | 20% |
| 盐水效率 Brine | 10% |
| 岩浆效率 Lava | 0.5%（下界 5%）0.5% (5% in Nether) |
| 模式切换 Mode Switch | 螺丝刀（仅钢）Screwdriver (Steel only) |

**地壳蒸汽掘进机 / Crust Steam Borer (CSB)**

虚空采矿——按维度掉落表产出矿石。
Void mining — produces ores based on dimension drop tables.

| 参数 Parameter | 数值 Value |
|----------|-------|
| 材质 Material | 青铜 / 钢 Bronze / Steel |
| 适用维度 Dimensions | 仅主世界 / 下界 Overworld / Nether only |

<p align="center"><img src="README/MTECrustMatterAggregator.png" width="500" alt="地壳物质聚合器 / Crust Matter Aggregator"><br><em>地壳物质聚合器 / Crust Matter Aggregator</em></p>
<p align="center"><img src="README/MTECrustMatterAggregatorUI.png" width="500" alt="地壳物质聚合器终端配置界面 / Crust Matter Aggregator Terminal UI"><br><em>地壳物质聚合器终端配置界面 / Crust Matter Aggregator Terminal UI</em></p>

**地壳物质聚合器 / Crust Matter Aggregator (CMA)**

仅钢级，跨维度虚空采矿（经终端配置界面操作）。
Steel only, cross-dimension void mining (configured via the terminal UI).

| 参数 Parameter | 数值 Value |
|----------|-------|
| 材质 Material | 钢 Steel |
| 蒸汽档位 Steam Grades | 3 档，三档均 24,000 L/s（消耗不分档，系数仅作用于产出；致密流体 1/100）3 grades, all at 24,000 L/s (consumption not graded; coefficient only affects output; dense fluids 1/100) |
| 矿石模式 Ore Modes | 原矿 / 粗矿 / 粉碎矿 Raw / Crushed / Purified |
| 时运 Fortune | III\~XV（奇点/临界门控）III\~XV (singularity/critical gating) |

**额外功能 / Additional Features**

| 功能 Feature | 说明 Description |
|---|---|
| 跨维度采矿 Cross-Dimension Mining | GT NEI Ore Plugin 维度显示物品（可选，缺省当前维度）GT NEI Ore Plugin dimension items (optional, defaults to current dimension) |
| 筛选/定向模式 Filter / Directional Modes | 蒸汽 / UU 物质消耗倍率 Scales with steam / UU-Matter cost |
| 奇点模式 Singularity Mode | 持续 200 秒 200-second duration |

<p align="center"><img src="README/MTEVeinSteamPyrolyzer-T1.png" width="260" alt="地脉蒸汽热解机 / Vein Steam Pyrolyzer"> <img src="README/MTEVeinSteamPyrolyzer-T2.png" width="260" alt="地脉蒸汽热解机 / Vein Steam Pyrolyzer"><br><em>地脉蒸汽热解机 / Vein Steam Pyrolyzer（青铜/钢）</em></p>

**地脉蒸汽热解机 / Vein Steam Pyrolyzer (VSP)**

以蒸汽为能源逆向注入地下，增加地下流体储量，解决长期存档中流体枯竭问题。
Reverse-injects steam energy underground to increase fluid reserves, solving long-term save fluid depletion.

| 参数 Parameter | 数值 Value |
|----------|-------|
| 材质 Material | 青铜 / 钢 Bronze / Steel |
| 扫描范围 Scan Range (T1/T2/T3) | 2×2 / 4×4 / 8×8 区块 chunks |

***

### Enhanced Processing Machines / 强化加工机器 (9)

All inherit from `MTEEnhancedMultiBlockBase` (GT5U), with more advanced mechanics.

均继承自 `MTEEnhancedMultiBlockBase`（GT5U），具有更高级的机制。

<p align="center"><img src="README/MTELargeCokeOven-T1.png" width="240" alt="大型焦炉 / Large Coke Oven"> <img src="README/MTELargeCokeOven-T2.png" width="240" alt="大型焦炉 / Large Coke Oven"><br><em>大型焦炉 / Large Coke Oven（青铜/钢）</em></p>

<p align="center"><img src="README/MTESiemensMartinFurnace.png" width="400" alt="平炉 / Siemens-Martin Furnace"><br><em>平炉 / Siemens-Martin Furnace</em></p>

**大型焦炉 / Large Coke Oven (LCO)**

无需供能的自发焦炉，使用 GT5U 原版焦炉配方（煤炭/煤块/原木/甘蔗/仙人掌等）。
Self-powered coke oven using GT5U vanilla coke oven recipes (coal/lumps/logs/cactus/sugarcane etc.).

| 参数 Parameter | 数值 Value |
|----------|-------|
| 材质 Material | 青铜 / 钢 Bronze / Steel |
| 并行 Parallel | 24 / 64 |
| 基础速度 Base Speed | 青铜 120% / 钢 200% Bronze 120% / Steel 200% |

**额外功能 / Additional Features**

| 功能 Feature | 说明 Description |
|---|---|
| 炉温加速 Heat Acceleration | 每 1% 炉温叠加 1% 工作速度（叠加在基础速度上）Each 1% heat adds 1% work speed (stacked on base speed) |

**平炉 / Siemens-Martin Furnace (SMF)**

仅钢级，过热蒸汽驱动；过热机制可让炉温突破 100%。
Steel only, superheated-steam driven; overheat mechanism lets furnace temperature exceed 100%.

| 参数 Parameter | 数值 Value |
|----------|-------|
| 材质 Material | 钢 Steel |
| 蒸汽类型 Steam Type | 过热蒸汽 Superheated steam |
| 并行 Parallel | 64\~128（随炉温 100%\~200% 线性提升）64\~128 (scales with furnace temperature 100%\~200%) |
| 配方时间 Recipe Time | ×0.75（过热最高再 -50%）×0.75 (overheat up to additional -50%) |
| 空气消耗 Air Consumption | 运行 1,000 L/s（预热不消耗，不足停机）1,000 L/s during operation (preheat exempt; stops if insufficient) |

**额外功能 / Additional Features**

| 功能 Feature | 说明 Description |
|---|---|
| 过热机制 Overheat Mechanism | 炉温最高 200%，配方时间最多削减 50%（在 0.75 基础系数后应用）Temperature up to 200%, recipe time reduced up to 50% (applied after the 0.75 base factor) |

<p align="center"><img src="README/MTELargeGeothermalSteamBoiler-T1.png" width="260" alt="大型地热蒸汽锅炉 / Large Geothermal Steam Boiler"> <img src="README/MTELargeGeothermalSteamBoiler-T2.png" width="260" alt="大型地热蒸汽锅炉 / Large Geothermal Steam Boiler"><br><em>大型地热蒸汽锅炉 / Large Geothermal Steam Boiler（青铜/钢）</em></p>

**大型地热蒸汽锅炉 / Large Geothermal Steam Boiler (LGB)**

消耗岩浆产蒸汽；结垢与超压机制并存。
Consumes lava to produce steam; features calcification and overpressure mechanics.

| 参数 Parameter | 数值 Value |
|----------|-------|
| 材质 Material | 青铜 / 钢 Bronze / Steel |

**额外功能 / Additional Features**

| 功能 Feature | 说明 Description |
|---|---|
| 过热芯片 Overheat Chip | 仅钢，启用过热蒸汽输出与稀有副产物 Steel only; enables superheated output and rare byproducts |
| 结垢 Calcification | 普通水结垢（蒸馏水永不），满垢产出降至 1% Normal water calcifies (distilled water never does); output drops to 1% at full calcification |
| 超压模式 Overpressure Mode | 螺丝刀右击开启（需 100% 热量），热量上限提至 200%，产出线性增长；缺水自动停机（需手动重启）Screwdriver right-click (requires 100% heat), raises heat cap to 200% with linear output growth; auto-stops when water runs out (manual restart) |

<p align="center"><img src="README/MTEMegaSteamTurbineArray-T1.png" width="240" alt="巨型蒸汽轮机机组 / Mega Steam Turbine Array（等级 1/3/6）"> <img src="README/MTEMegaSteamTurbineArray-T3.png" width="240" alt="巨型蒸汽轮机机组 / Mega Steam Turbine Array（等级 1/3/6）"> <img src="README/MTEMegaSteamTurbineArray-T6.png" width="240" alt="巨型蒸汽轮机机组 / Mega Steam Turbine Array（等级 1/3/6）"><br><em>巨型蒸汽轮机机组 / Mega Steam Turbine Array（等级 1/3/6）</em></p>

**巨型蒸汽轮机机组 / Mega Steam Turbine Array (MSTA)**

12级蒸汽发电机组；堆叠层数越多效率上限越高，支持全蒸汽类型。
12-tier EU generator; stacking efficiency — more layers = higher efficiency cap; supports all steam types.

| 参数 Parameter | 数值 Value |
|----------|-------|
| 等级 Tier | 12 级 12-tier |
| 蒸汽类型 Steam Types | 全类型（等级 6+ 可处理致密/超临界）All types (tier 6+ processes dense/supercritical) |

**额外功能 / Additional Features**

| 功能 Feature | 说明 Description |
|---|---|
| 全局功率 Global Power | 螺丝刀轮切 100%→80%→60%→40%→20%，以输出换基础蒸汽节省 Screwdriver cycles 100%→80%→60%→40%→20%, trading output for base steam savings |
| 奇点模式 Singularity Modes | 纠缠 ×2 功率 / 临界 ×5 功率（含效率与节省加成），每颗 200s Entangled ×2 power / Critical ×5 power (plus efficiency & savings bonuses), 200s per singularity |
| 循环超限芯片 Cycle Overlimit Chip | 控制器槽，需 4 组额外叠加层；热蒸汽冷却直接产蒸馏水，效率因子按蒸汽家族叠加 Controller slot, requires all 4 extra stack groups; turns hot-steam cooling into distilled water, stacks steam efficiency within their family |

<p align="center"><img src="README/MTELargeSolarOverpressureArray-T1.png" width="240" alt="大型太阳能超压阵列 / Large Solar Overpressure Array"> <img src="README/MTELargeSolarOverpressureArray-T2.png" width="240" alt="大型太阳能超压阵列 / Large Solar Overpressure Array"> <img src="README/MTELargeSolarOverpressureArray-T3.png" width="240" alt="大型太阳能超压阵列 / Large Solar Overpressure Array"><br><em>大型太阳能超压阵列 / Large Solar Overpressure Array（青铜/钢/银）</em></p>

**大型太阳能超压阵列 / Large Solar Overpressure Array (LSOA)**

3级（青铜/钢/银）太阳能产蒸汽阵列；银级产出过热蒸汽。
3-tier (Bronze/Steel/Silver) solar-powered steam array; Silver tier outputs superheated steam.

| 参数 Parameter | 数值 Value |
|----------|-------|
| 等级 Tier | 青铜 / 钢 / 银 Bronze / Steel / Silver |
| 基础产出 Base Output | T1=120K / T2=180K / T3=240K L/s |
| 最高倍率 Max Multiplier | ×4.0（最大增幅产出 480K/720K/960K L/s）×4.0 (max boosted 480K/720K/960K L/s) |

**额外功能 / Additional Features**

| 功能 Feature | 说明 Description |
|---|---|
| 增幅来源 Boost Sources | 太阳能锅炉：高级每满组 64 台 +2.0×、简单 +1.0×，加超压额外增幅 Solar boiler: +2.0× per 64 Advanced, +1.0× per 64 Simple, plus overpressure extra boost |
| 结垢/超压/缺水停机 Calcification / Overpressure / Water-out | 规则同地热锅炉（见上）；缺水自动停机 Same rules as Geothermal Boiler (above); auto-stops when water runs out |

<p align="center"><img src="README/MTEKineticProcessingArray-T1.png" width="260" alt="动力加工阵列 / Kinetic Processing Array（等级 1/5）"> <img src="README/MTEKineticProcessingArray-T5.png" width="260" alt="动力加工阵列 / Kinetic Processing Array（等级 1/5）"><br><em>动力加工阵列 / Kinetic Processing Array（等级 1/5）</em></p>

**动力加工阵列 / Kinetic Processing Array (KPA)**

仅过热蒸汽，12级；处理放入的任意单方块机器配方。
Superheated steam only, 12-tier; runs recipes of any single-block machine placed inside.

| 参数 Parameter | 数值 Value |
|----------|-------|
| 等级 Tier | 12 级 12-tier |
| 蒸汽类型 Steam Type | 仅过热蒸汽 Superheated steam only |
| 并行 Parallel | (1 + 2 × 机器等级) + 机器数量 (1 + 2 × machineTier) + stackSize |
| 基础速度 Base Speed | 200%（能耗减免 40%）200% (40% energy discount) |

**额外功能 / Additional Features**

| 功能 Feature | 说明 Description |
|---|---|
| 升级 Upgrade | 管道/齿轮箱方块升级速度与能耗减免 Pipe/gearbox casings upgrade speed and energy discount |
| 临时升压 Temporary Overvolt | 手持蒸汽纠缠节点右击控制器，配方电压上限临时提高一级（持续 1200s）Right-click controller with Steam Entanglement Node to raise recipe voltage cap by one tier for 1200s |
| 内置能力 Built-in Capabilities | 洁净室 / ME 合成总线兼容 / 映射电解·离心·化学反应配方 Cleanroom / ME crafting bus support / Electrolyzer·Centrifuge·Chemical Reactor recipe mapping |

<p align="center"><img src="README/MTEGearSteamCompressor-T1.png" width="260" alt="自驱式机械蒸汽压缩机 / Gear Steam Compressor"> <img src="README/MTEGearSteamCompressor-T2.png" width="260" alt="自驱式机械蒸汽压缩机 / Gear Steam Compressor"><br><em>自驱式机械蒸汽压缩机 / Gear Steam Compressor（青铜/钢）</em></p>

**自驱式机械蒸汽压缩机 / Gear Steam Compressor (GSC)**

普通蒸汽→过热蒸汽+蒸馏水（固定 4:1 压缩比）；无需电力锅炉即可产出过热蒸汽的关键机器。
Converts normal steam → superheated steam + distilled water (fixed 4:1 compression ratio); a key machine for producing superheated steam without electric boilers.

| 参数 Parameter | 数值 Value |
|----------|-------|
| 材质 Material | 青铜 / 钢 Bronze / Steel |
| 压缩比 Compression Ratio | 4:1（固定）4:1 (fixed) |

<p align="center"><img src="README/MTEAmmoniaPlant.png" width="400" alt="制氨工厂 / Ammonia Plant"><br><em>制氨工厂 / Ammonia Plant</em></p>

**制氨工厂 / Ammonia Plant (AP)**

仅钢级，热量系统 + 7级催化剂（更高级催化剂=更多并行+更快反应），过热蒸汽为副产物。
Steel only; heat-based processing with a 7-tier catalyst system (higher catalysts = more parallel + faster reaction). Superheated steam as a byproduct.

| 参数 Parameter | 数值 Value |
|----------|-------|
| 材质 Material | 钢 Steel |
| 并行 Parallel | 64\~256 |
| 催化剂 Catalysts | 7 级：镍基→铂基→铀基→锇基→铁钴基→钌基→量子 7-tier: Nickel→Platinum→Uranium→Osmium→FeCo→Ruthenium→Quantum |

<p align="center"><img src="README/MTEReinforcedBrickBlastFurnace.png" width="260" alt="加固砖高炉 / Reinforced Brick Blast Furnace"><br><em>加固砖高炉 / Reinforced Brick Blast Furnace</em></p>

**加固砖高炉 / Reinforced Brick Blast Furnace (RBBF)**

单级、无需蒸汽，执行 GT5U 原始高炉配方；炉温越高并行越多、配方越快。
Single-tier, no steam required; runs GT5U primitive blast furnace recipes. Higher temperature grants more parallels and faster recipes.

| 参数 Parameter | 数值 Value |
|----------|-------|
| 等级 Tier | 单级 Single-tier |
| 蒸汽需求 Steam Required | 无 None |
| 炉温变化 Temperature | 运行 +0.01%/s，闲置 -1%/s +0.01%/s while working, -1%/s when idle |
| 并行 Parallel | 1\~4（每 25% 炉温 +1）1\~4 (each 25% = +1) |
| 速度 Speed | 最高 1.5× Up to 1.5× |

**额外功能 / Additional Features**

| 功能 Feature | 说明 Description |
|---|---|
| 结构 Structure | 钢加固砖结构，无需维护/空气/耐压蒸汽 Steel-reinforced brick; no maintenance/air/pressure steam required |

***

### Singularity Machines / 奇点机器 (3)

All inherit from `MTESingularityMachineBase` (Enhanced system). Without any EU cost, they devour high-grade steam to produce entangled singularities.

三者均继承自 `MTESingularityMachineBase`（Enhanced 体系）。无电力消耗，吞噬高等级蒸汽以产出纠缠奇点。

**蒸汽奇点纠缠装置 / Steam Singularity Entangler (SSE)**

吞噬输入仓中最高等级蒸汽（普通/过热/超临界，不含致密），按饱和函数累积热量；热量达 100% 时产出 1 个蒸汽纠缠奇点。
Devours the highest-grade steam in the input hatches (normal/superheated/supercritical, dense excluded), accumulating heat via a saturation function; at 100% heat it produces 1 Steam Entangled Singularity.

| 参数 Parameter | 数值 Value |
|----------|-------|
| 蒸汽输入 Steam Input | 普通 / 过热 / 超临界（不含致密）Normal / Superheated / Supercritical (dense excluded) |
| 产出 Output | 1 蒸汽纠缠奇点（热量 100%）1 Steam Entangled Singularity (at 100% heat) |
| 并行 Parallel | 无 None |

<p align="center"><img src="README/MTESteamSingularityEntangler.png" width="450" alt="蒸汽奇点纠缠装置 / Steam Singularity Entangler"><br><em>蒸汽奇点纠缠装置 / Steam Singularity Entangler</em></p>

**临界纠缠奇点稳定装置 / Critical Entangled Singularity Stabilizer (CSC)**

仅接收致密态变体（致密蒸汽 / 致密过热 / 致密超临界），按饱和函数累积热量；热量达 100% 时产出 1 个临界蒸汽纠缠奇点；会吞噬输入仓全部蒸汽并禁用蒸汽冷却。
Accepts only dense variants (dense steam / dense superheated / dense supercritical), accumulating heat via a saturation function; at 100% heat it produces 1 Critical Steam Entangled Singularity. Devours all steam from the input hatches and disables steam cooling.

| 参数 Parameter | 数值 Value |
|----------|-------|
| 蒸汽输入 Steam Input | 仅致密态：致密蒸汽 / 致密过热 / 致密超临界 Dense only: dense steam / dense superheated / dense supercritical |
| 产出 Output | 1 临界蒸汽纠缠奇点（热量 100%）1 Critical Steam Entangled Singularity (at 100% heat) |

**额外功能 / Additional Features**

| 功能 Feature | 说明 Description |
|---|---|
| 输入总线 Input Bus | 需要 Input bus required |
| 视觉效果 Visual | 运行期间结构核心生成灰色纠缠奇点动画（纯视觉）Gray entanglement animation at the structure core while running (visual only) |

<p align="center"><img src="README/MTECriticalSingularityCompressor.png" width="450" alt="临界纠缠奇点稳定装置 / Critical Entangled Singularity Stabilizer"><br><em>临界纠缠奇点稳定装置 / Critical Entangled Singularity Stabilizer</em></p>

**致密态蒸汽操控装置 / Dense State Manipulator (DSM)**

螺丝刀循环切换双模式：蒸汽压缩 / 蒸汽解压；输入总线中每颗奇点燃料续航 200s（普通奇点输出损失 20%，临界奇点无损）。
Dual mode cycled by screwdriver: Steam Compression / Steam Decompression; each singularity in the input buses fuels 200 seconds (Normal: 20% output loss; Critical: no loss).

| 参数 Parameter | 数值 Value |
|----------|-------|
| 模式 Modes | 蒸汽压缩（1000:1 蒸汽→致密）/ 蒸汽解压（1:1000 致密→蒸汽）Steam Compression (1000:1) / Steam Decompression (1:1000) |
| 燃料续航 Fuel | 每颗奇点 200s；普通（蒸汽纠缠奇点）输出损失 20%，临界（临界蒸汽纠缠奇点）无损 200s per singularity; Normal (Steam Entangled Singularity) 20% output loss, Critical (Critical Steam Entangled Singularity) no loss |
| 需求 Requires | 输入总线 + 输出仓；无热量机制 Input bus + output hatch; no heat mechanic |

<p align="center"><img src="README/MTEDenseStateManipulator.png" width="450" alt="致密态蒸汽操控装置 / Dense State Manipulator"><br><em>致密态蒸汽操控装置 / Dense State Manipulator</em></p>

***

## Single-Block Nodes / 单方块节点 (8)

### Cache Nodes / 缓存节点 (6)

<p align="center"><img src="README/MTECacheNodes.png" width="360" alt="缓存节点 / Cache Nodes"><br><em>缓存节点 / Cache Nodes</em></p>

Digital tank-based nodes that bind to hubs for cross-chunk/dimensional fluid transfer. Support fluid lock, auto-output, void excess, terminal-adjustable hub transfer rate (six cache nodes + four singularity compartments) and a 9-step capacity limit tier (see below).

基于数字储罐的节点，绑定枢纽实现跨区块/维度流体传输。支持流体锁定、自动输出、溢出虚空、枢纽终端调整交互速率（六缓存节点+四奇点仓）与容量上限档（见下）。

| 节点 Node | 接受流体 Accepted Fluid | 容量 Capacity | 输出速率 Output Rate | 枢纽交互速率 Hub Rate | 绑定奇点消耗 Binding Cost |
|---|---|---|---|---|---|
| 蒸汽缓存节点 Steam Cache Node | 普通蒸汽 Normal steam | 16M L | 2,000,000 L/s | 2,000,000 L/s | 0 |
| 强化蒸汽缓存节点 Reinforced Steam Cache Node | 普通 + 过热蒸汽 Normal + superheated | 64M L | 8,000,000 L/s | 8,000,000 L/s | 1 |
| 超压蒸汽缓存节点 Overpressure Steam Cache Node | 全部蒸汽类型 All steam types | 256M L | 64,000,000 L/s | 64,000,000 L/s | 8（需强化芯片）8 (Reinforced Chip) |
| 通用流体缓存节点 Universal Fluid Cache Node | 任意流体 Any fluid | 2M L | 64,000 L/s | 64,000 L/s | 0 |
| 耐压通用流体缓存节点 Reinforced Universal Fluid Cache Node | 任意流体 Any fluid | 8M L | 256,000 L/s | 256,000 L/s | 0 |
| 超压通用流体缓存节点 Overpressure Universal Fluid Cache Node | 任意流体 Any fluid | 32M L | 2,000,000 L/s | 2,000,000 L/s | 0（需强化芯片）0 (Reinforced Chip) |

**容量上限档 / Capacity Limit Tier**

缓存节点与两个接收类奇点仓（奇点通用蒸汽仓、奇点输入仓）支持容量上限档 {100, 80, 60, 40, 20, 10, 5, 2, 1}%：终端潜行右击本地循环，或在枢纽终端状态界面点击容量按钮远程循环；档位随 NBT 持久化，降档后超出部分温和保留在罐内（拒绝新入、不销毁）。发送类仓罐只出不进，无容量档。

Cache nodes and the two receiving compartments (Singularity Steam Compartment, Singularity Fluid Input Compartment) support a capacity limit tier of {100, 80, 60, 40, 20, 10, 5, 2, 1}%: cycle locally with a terminal sneak+right-click, or remotely via the capacity button in the terminal status UI; the tier persists in NBT, and fluid above a lowered limit is kept softly in the tank (new input rejected, nothing destroyed). Send-type compartments have no capacity tier (output-only tank).

**节点外观 / Node Appearance**

缓存节点顶面为三层渲染：基材 + 流体窗 + 状态框，未绑定时也显示家族默认流体窗（状态框为灰色框架）。基材随结构成型档位（青铜顶/钢顶/超压机壳），状态框颜色语义：红橙=从枢纽接受、紫蓝=向枢纽输送、灰=未绑定/控制器，边框为 11 帧中心透明动画材质。奇点仓正面为三层（底材 + 流体窗 + 语义固定框架：接收恒红橙/发送恒紫蓝）、顶面仅底材单层；底材跟随所在枢纽结构机壳（未成型时回退 LV 机壳）。两枢纽控制器正面为 [等级基材 + 内缩流体窗 + 专用框架] 三层（罐空回退默认流体：蒸汽阵列→蒸汽、蓄水阵列→水）。流体窗实时显示罐内流体（与 NEI 图标同源外观）；世界内外观在状态变化时由服务端发包即时切换；物品形态为 GT 原生 3D 渲染。

Cache nodes render a 3-layer top face: base texture + fluid window + status frame, and the family-default fluid window is shown even when unbound (status frame gray). The base texture follows the formed structure tier (bronze/steel top, overpressure casing); status frame colors: red-orange = receiving from hub, purple-blue = sending to hub, gray = unbound/controller — the frame is an 11-frame animated texture with a transparent center. Singularity compartments render a 3-layer front face (base + fluid window + semantically fixed frame: receive always red-orange / send always purple-blue) and a single-layer top face (base only); their base texture follows the casing of the hub structure (falling back to the LV casing when unformed). Both hub controllers render a 3-layer front face [tier base + inset fluid window + dedicated frame] (empty tank falls back to the default fluid: Steam Hub → steam, Water Hub → water). The fluid window shows the stored fluid in real time (same appearance source as the NEI icon); the in-world look switches immediately via a server packet on state change; item form uses GT's native 3D rendering.

**蒸汽缓存节点 / Steam Cache Node**

仅接受普通蒸汽，绑定蒸汽枢纽。
Accepts normal steam only. Binds to the Steam Hub.

| 参数 Parameter | 数值 Value |
|----------|-------|
| 接受流体 Accepted Fluid | 仅普通蒸汽 Normal steam only |
| 绑定枢纽 Binds To | 蒸汽枢纽 Steam Hub |

**强化蒸汽缓存节点 / Reinforced Steam Cache Node**

接受普通 + 过热蒸汽，绑定蒸汽枢纽。
Accepts normal + superheated steam. Binds to the Steam Hub.

| 参数 Parameter | 数值 Value |
|----------|-------|
| 接受流体 Accepted Fluid | 普通 + 过热蒸汽 Normal + superheated steam |
| 绑定枢纽 Binds To | 蒸汽枢纽 Steam Hub |

**超压蒸汽缓存节点 / Overpressure Steam Cache Node**

接受全部蒸汽类型，容量与输出速率最高；绑定蒸汽枢纽（需枢纽 3 级强化芯片）。
Accepts ALL steam types. Highest capacity and output rate. Binds to the Steam Hub (requires Reinforced Chip on tier 3 hub).

| 参数 Parameter | 数值 Value |
|----------|-------|
| 接受流体 Accepted Fluid | 全部蒸汽类型 All steam types |
| 绑定枢纽 Binds To | 蒸汽枢纽（需 3 级强化芯片）Steam Hub (requires Reinforced Chip on tier 3 hub) |

**通用流体缓存节点 / Universal Fluid Cache Node**

接受任意流体，绑定蓄水枢纽阵列，绑定不消耗奇点（该家族早期为水限定形态，随枢纽通用流体化更名）。
Accepts any fluid (the family was water-only in early versions and was renamed when the hub became fluid-agnostic). Binds to the Water Hub Array; binding costs no singularity.

| 参数 Parameter | 数值 Value |
|----------|-------|
| 接受流体 Accepted Fluid | 任意流体 Any fluid |
| 绑定枢纽 Binds To | 蓄水枢纽阵列 Water Hub Array |

**耐压通用流体缓存节点 / Reinforced Universal Fluid Cache Node**

接受任意流体，容量与速率提升，绑定蓄水枢纽阵列（不消耗奇点）。
Accepts any fluid with higher capacity and rate. Binds to the Water Hub Array (no singularity cost).

| 参数 Parameter | 数值 Value |
|----------|-------|
| 接受流体 Accepted Fluid | 任意流体 Any fluid |
| 绑定枢纽 Binds To | 蓄水枢纽阵列 Water Hub Array |

**超压通用流体缓存节点 / Overpressure Universal Fluid Cache Node**

接受任意流体，容量 32M L、速率 2,000,000 L/s；绑定蓄水枢纽阵列需枢纽 3 级 + 强化奇点枢纽升级芯片（不消耗奇点）。
Accepts any fluid, 32M L capacity at 2,000,000 L/s; binding requires a tier 3 Water Hub Array with the Reinforced Hub Singularity Chip (no singularity cost).

| 参数 Parameter | 数值 Value |
|----------|-------|
| 接受流体 Accepted Fluid | 任意流体 Any fluid |
| 绑定枢纽 Binds To | 蓄水枢纽阵列（需 3 级 + 强化芯片）Water Hub Array (tier 3 + Reinforced Chip) |

### Remote Worker Nodes / 远程工作节点 (2)

<p align="center"><img src="README/MTERemoteWorkerNodes.png" width="320" alt="远程工作节点 / Remote Worker Nodes"><br><em>远程工作节点 / Remote Worker Nodes</em></p>

Nodes that perform remote operations driven by the Singularity Drilling Hub. They consume mining pipes to drill downward, scanning and mining layer by layer as they descend.

由奇点钻井枢纽驱动执行远程作业的节点。消耗钻管向下钻探，下降过程中逐层扫描并开采。

**奇点采矿节点 / Singularity Miner Node**

5级升级体系（矿石钻机多方块控制器 + 奇点），提升采矿范围、时运与速度。
5-tier upgrade system (Ore Drill controllers + singularities) boosting range, fortune and speed.

| 参数 Parameter | 数值 Value |
|----------|-------|
| 产出 Output | 矿石 Ores |
| 升级体系 Upgrade System | 5 级（矿石钻机控制器 + 奇点）5-tier (Ore Drill controllers + singularities) |

**额外功能 / Additional Features**

| 功能 Feature | 说明 Description |
|---|---|
| 粉碎矿模式 Crushed-Ore Mode | 螺丝刀切换，按研磨配方实际数量 ×1.5 输出 Screwdriver; outputs at actual maceration count ×1.5 |
| 时运 Fortune | 6\~10 绕过 GT5U 时运>3 截断 {6,7,8,9,10} bypasses GT5U's fortune>3 truncation |
| 区块加载 Chunk Loading | 绑定枢纽后启用 Binding to a hub enables chunk loading |

**奇点钻井节点 / Singularity Drilling Node**

4级升级体系（石油钻机多方块控制器 + 奇点），等级越高抽取系数与作业范围越大。
4-tier upgrade system using Oil Drill multiblock controllers + singularities. Higher tiers increase extraction coefficient and work range.

| 参数 Parameter | 数值 Value |
|----------|-------|
| 产出 Output | 地下流体 Underground fluids |
| 升级体系 Upgrade System | 4 级（石油钻机控制器 + 奇点）4-tier (Oil Drill controllers + singularities) |
| 作业范围 Work Range | 1×1 → 8×8 区块 1×1 to 8×8 chunks |

**额外功能 / Additional Features**

| 功能 Feature | 说明 Description |
|---|---|
| 独立区块 Independent Chunks | 每个区块独立抽取与枯竭 Each chunk independently extracted and depleted |
| 区块加载 Chunk Loading | 绑定枢纽后启用远程自动加载区块 Binding to a hub enables automatic chunk loading for remote operation |

***

## Hatches / 仓室 (15 类仓室 + 4 奇点仓 + 3 种存储单元)

<p align="center"><img src="README/MTEAllHatches.png" width="380" alt="全部仓室 / All Hatches"><br><em>全部仓室 / All Hatches</em></p>

Specialized hatches for GTSR machines with varying capacities and fluid filters:

GTSR 机器专用仓室，具有不同容量和流体过滤：

**蒸汽输入/输出仓（通用）/ Steam Input/Output Hatches (Generic)**

GTSR 机器基础蒸汽输入/输出仓。
Basic steam input/output hatches for GTSR machines.

| 参数 Parameter | 数值 Value |
|----------|-------|
| 容量 Capacity | 16K\~128K |
| 过滤 Filter | 任意流体 Any fluid |

**蒸汽输出仓 / Steam Output Hatch**

GTSR 机器专用蒸汽输出仓。
Dedicated steam output hatch for GTSR machines.

| 参数 Parameter | 数值 Value |
|----------|-------|
| 用途 Purpose | 蒸汽输出 Steam output |

**蒸汽冷却仓 / Steam Cooling Hatch**

积累冷却水（160 蒸汽 : 1 水）。
Accumulates cooling water (160 steam : 1 water ratio).

| 参数 Parameter | 数值 Value |
|----------|-------|
| 容量 Capacity | 64K |
| 冷却比 Cooling Ratio | 160 蒸汽 : 1 水 160 steam : 1 water |

**耐压蒸汽输入/输出仓 / Pressure Steam Input/Output Hatches**

接受普通与过热蒸汽的耐压仓室。
Pressure-rated hatches accepting both normal and superheated steam.

| 参数 Parameter | 数值 Value |
|----------|-------|
| 容量 Capacity | 512K\~1M |
| 过滤 Filter | 普通 + 过热蒸汽 Normal + superheated steam |

**耐压蒸汽冷却仓 / Pressure Steam Cooling Hatch**

蒸汽冷却仓的耐压变体。
Pressure-rated variant of the steam cooling hatch.

| 参数 Parameter | 数值 Value |
|----------|-------|
| 变体 Variant | 耐压 Pressure-rated |

**蒸汽枢纽输入/输出仓 / Steam Hub Input/Output Hatches**

容量由枢纽控制器决定，填充/抽取委托给蒸汽枢纽。
Dynamic capacity (determined by hub controller); delegates fill/drain to the Steam Hub.

| 参数 Parameter | 数值 Value |
|----------|-------|
| 容量 Capacity | 动态（枢纽控制器决定）Dynamic (hub controller) |
| 委托 Delegates To | 蒸汽枢纽 Steam Hub |

**蓄水枢纽输入/输出仓 / Water Hub Input/Output Hatches**

容量由枢纽控制器决定，填充/抽取委托给蓄水枢纽阵列（不限流体种类，同一枢纽同时仅存一种）。
Dynamic capacity (determined by hub controller); delegates fill/drain to the Water Hub (any fluid; one fluid type per hub at a time).

| 参数 Parameter | 数值 Value |
|---|---|
| 容量 Capacity | 动态（枢纽控制器决定）Dynamic (hub controller) |
| 委托 Delegates To | 蓄水枢纽阵列 Water Hub Array |

**巨型超压蒸汽输入仓 / Mega Overpressure Steam Input Hatch**

专用于巨型蒸汽轮机机组，蒸汽奇点纠缠装置等（SSE/CSC/DSM 亦可安装）；接受全部蒸汽类型。
For Mega Steam Turbine Array; also installable on SSE/CSC/DSM (Steam Singularity Entangler / Critical Entangled Singularity Stabilizer / Dense State Manipulator); accepts all steam types.

| 参数 Parameter | 数值 Value |
|----------|-------|
| 适用机器 Used By | 巨型蒸汽轮机机组及 SSE/CSC/DSM Mega Steam Turbine Array, also SSE/CSC/DSM |
| 过滤 Filter | 全部蒸汽类型 All steam types |

**巨型空气输入仓 / Mega Air Input Hatch**

1亿L容量，仅接受空气与下界空气；用于平炉（空气消耗）与大气离心机（大量空气输入）。
100M L capacity; accepts air and nether air only. Used by Siemens-Martin Furnace (air consumption) and Atmospheric Centrifuge (large air input).

| 参数 Parameter | 数值 Value |
|----------|-------|
| 容量 Capacity | 100M L |
| 过滤 Filter | 仅空气 / 下界空气 Air / nether air only |
| 适用机器 Used By | 平炉、大气离心机 Siemens-Martin Furnace, Atmospheric Centrifuge |

**蒸馏水仓 / Distilled Water Hatch**

借助蒸汽纠缠奇点从虚空凝结最纯净的水源——放置即满，此后每 500 tick 补满一次。
Harnessing steam-entangled singularities, it condenses the purest water from the void — fills immediately on placement, then refills every 500 ticks.

| 参数 Parameter | 数值 Value |
|----------|-------|
| 容量 Capacity | 10M L |
| 补充 Refill | 放置即满，此后每 500 tick 一次 Fills on placement, then every 500 ticks |

**额外功能 / Additional Features**

| 功能 Feature | 说明 Description |
|---|---|
| 用途 Role | 蒸馏水永不结垢，是太阳能阵列与地热锅炉的理想介质 Distilled water never calcifies; ideal for Solar Array & Geothermal Boiler |

**红石仓 / Redstone Hatch**

可安装在任意多方块机器上，按所选机器词条值（效率/输出/消耗/工作状态等）输出红石信号；右键打开 GUI 设置阈值、反向与更新频率。
Installable on any multiblock machine; outputs a redstone signal based on a selected machine data entry (efficiency/output/consumption/working etc.). Right-click to open the GUI for threshold, invert and update interval.

| 参数 Parameter | 数值 Value |
|----------|-------|
| 适用机器 Used By | 任意多方块机器 Any multiblock machine |
| 输出 Output | 按词条阈值输出红石信号 Redstone signal by entry threshold |

**奇点仓四件套 / Singularity Compartments (4)**

模式锁定的枢纽缓存仓（仓室基类：分别继承耐压蒸汽仓/耐压蒸汽输出仓/枢纽输入仓/枢纽输出仓近亲，可加入对应枢纽多方块结构）：奇点通用蒸汽仓与奇点通用蒸汽输出仓绑蒸汽枢纽阵列（每仓消耗 1 蒸汽纠缠奇点、蒸汽全家族兼容）；奇点输入仓与奇点输出仓绑蓄水枢纽阵列（0 消耗、任意流体）。仓=从枢纽接受（接收），输出仓=向枢纽输送（发送），方向恒定锁定：终端/界面/右键均无法切换，已绑定时右键仅解绑。仓无 GUI；管道无法向仓注入流体（canTankBeFilled/acceptsFluid 阻断，仅枢纽链路交互）；枢纽交互有效速率=固定基准×传输速率档（终端右击循环，见传输机制一节）；两个接收仓支持容量上限档（终端潜行右击或终端 GUI 按钮，见缓存节点一节）。破坏掉落保留绑定。

Mode-locked hub cache compartments (hatch-based: each extends its pressure-steam/hub hatch counterpart and can join the corresponding hub multiblock structure): the Singularity Steam Compartment and Singularity Steam Output Compartment bind to the Steam Hub Array (1 Steam Entangled Singularity each; full steam family); the Singularity Fluid Input/Output Compartments bind to the Water Hub Array (0 cost, any fluid). Compartment = receive from hub, Output Compartment = send to hub — the direction is permanently locked: terminal/GUI/right-click cannot switch it, and right-clicking a bound compartment only unbinds. Compartments have no GUI; pipes cannot inject into them (canTankBeFilled/acceptsFluid blocked — hub link only); their effective hub interaction rate = fixed base × transfer rate tier (cycled by a terminal right-click, see Transfer Mechanism); the two receiving compartments support the capacity limit tier (terminal sneak+right-click or the terminal GUI button, see Cache Nodes). Breaking drops retain the binding.

| 仓 Compartment | 绑定枢纽 Bound Hub | 容量 Capacity | 基准交互速率 Base Hub Rate | 流体范围 Fluid Range | 方向（锁定）Direction (locked) | 消耗 Cost |
|---|---|---|---|---|---|---|
| 奇点通用蒸汽仓 Singularity Steam Compartment | 蒸汽枢纽 Steam Hub | 8M L | 8,000,000 L/s | 蒸汽全家族 Full steam family | 从枢纽接受 Receive | 1 奇点 1 singularity |
| 奇点通用蒸汽输出仓 Singularity Steam Output Compartment | 蒸汽枢纽 Steam Hub | 8M L | 8,000,000 L/s | 蒸汽全家族 Full steam family | 向枢纽输送 Send | 1 奇点 1 singularity |
| 奇点输入仓 Singularity Fluid Input Compartment | 蓄水枢纽阵列 Water Hub Array | 256K L | 256,000 L/s | 任意流体 Any fluid | 从枢纽接受 Receive | 0 |
| 奇点输出仓 Singularity Fluid Output Compartment | 蓄水枢纽阵列 Water Hub Array | 256K L | 256,000 L/s | 任意流体 Any fluid | 向枢纽输送 Send | 0 |

> 📷 图片待配：奇点仓四件套合图

正面为 [底材, 流体窗, 语义固定框架（接收/发送）] 三层，顶面仅底材单层；底材跟随所在枢纽结构机壳，未成型时回退 LV 机壳。正面流体窗实时显示罐内流体，罐空回退该仓默认流体（蒸汽仓→蒸汽、流体仓→水）。

The front face renders [base, fluid window, semantically fixed frame (receive/send)] in 3 layers, and the top face is a single base layer only; the base texture follows the casing of the hub structure (LV casing fallback when unformed). The front fluid window shows the stored fluid in real time, falling back to the compartment's default fluid when empty (steam pair → steam, fluid pair → water).

**枢纽存储单元（3种）/ Hub Storage Units (3)**

用于枢纽阵列层叠的存储单元，分枢纽 / 加固枢纽 / 超压枢纽三种。
Hub/Reinforced/Overpressure Hub Storage Units for stacking layers in hub arrays.

| 参数 Parameter | 数值 Value |
|----------|-------|
| 类型 Types | 枢纽 / 加固枢纽 / 超压枢纽 Hub / Reinforced / Overpressure |
| 单元容量 Unit Capacity | 320M / 1.28B / 20.48B L（蒸汽枢纽阵列）/ 1.28M / 5.12M / 20.48M L（蓄水枢纽阵列）320M / 1.28B / 20.48B L (Steam Hub Array) / 1.28M / 5.12M / 20.48M L (Water Hub Array) |

***

## Items / 物品

- **枢纽终端 / Hub Terminal**: Handheld remote management device. Right-click a hub controller to open its status terminal (cache hub / drilling hub); right-click (non-sneaking) a node/compartment to cycle its rate tier (six cache nodes + four singularity compartments), sneak+right-click to cycle the capacity limit tier (send-type compartments show a locked hint). Crafted with 1 Steam Entangled Singularity + 8 steel plates. / 手持远程管理设备。右击枢纽控制器打开对应状态终端（缓存枢纽/钻井枢纽）；终端右击（非潜行）节点/仓循环传输速率档（六缓存节点+四奇点仓）、终端潜行右击循环容量上限档（发送类仓提示容量锁定）。1 蒸汽纠缠奇点 + 8 钢板合成。
- **蒸汽纠缠奇点 / Steam Entangled Singularity**: Core binding material. Produced by the Steam Singularity Entangler (heat accumulation). Consumed when binding nodes to hubs and in various crafting recipes.
- **临界蒸汽纠缠奇点 / Critical Steam Entangled Singularity**: Produced by the Critical Entangled Singularity Stabilizer (CSC). Used for more advanced crafting and amplification; legend says it can tear apart the very limits of dimensions... DANGEROUS — it explodes when dropped, never discard it! The drop explosion guarantees that normal singularities will appear. / 由临界纠缠奇点稳定装置（CSC）产出。用于更高级的合成与增幅；传说其能够彻底撕开维度的限制……危险品——掉落物会爆炸，请勿丢弃！掉落爆炸保证会出现普通奇点。
- **枢纽奇点芯片 / Hub Singularity Chip**: Required for Steam/Water Hub node binding, multiplies hub total capacity ×5. Also enables hub debug mode when right-clicked. Removing it from a filled hub swallows the stored fluid exceeding the reduced capacity.
- **强化枢纽奇点芯片 / Reinforced Hub Singularity Chip**: For tier 3 (TungstenSteel) Steam/Water hubs — on the Steam Hub it enables dense/supercritical steam; on both hubs it grants ×20 capacity (takes priority over the ×5 Hub Chip bonus) and unlocks overpressure cache node binding; right-click a hub with it to list bound cache nodes. / 等级3（钨钢）蒸汽/蓄水枢纽阵列通用——蒸汽枢纽阵列解锁致密/超临界蒸汽；双枢纽容量×20（优先于普通芯片×5）并解锁超压缓存节点绑定；手持右击枢纽可列出已绑定缓存节点。
- **蒸汽轮机循环超限芯片 / Steam Turbine Cycle Overlimit Chip**: For Mega Steam Turbine Array controller slot — requires all 4 extra stack groups to activate: superheated/supercritical (incl. dense) steam cooling becomes distilled water, and steam efficiency factors stack within their steam family (e.g. supercritical = 超临界+过热+蒸汽 = 2.5×). / 装入巨型蒸汽轮机阵列控制器槽，需完成全部4组额外叠加层：过热/超临界（含致密）蒸汽冷却直接产蒸馏水，蒸汽效率因子按蒸汽家族内叠加（如超临界=超临界+过热+蒸汽=2.5倍）。
- **地热过热芯片 / Geothermal Overheat Chip**: For Large Geothermal Steam Boiler (steel tier) — enables superheated steam output and rare byproducts.
- **稀有气体分离芯片 / Rare Gas Separation Chip**: For Atmospheric Centrifuge — unlocks recipes with >3 fluid outputs (up to 9).
- **矿脉裂解器芯片（T1/T2/T3）/ Vein Pyrolyzer Chip (T1/T2/T3)**: For Vein Steam Pyrolyzer — expands underground fluid scan range.
- **制氨催化剂（7种变体）/ Ammonia Catalyst (7 variants)**: For Ammonia Plant — determines parallel count and reaction time. 7-tier progression from Nickel to Quantum.

> 📷 图片待配：物品栏合成图（10 类物品）

***

## Recipes / 配方系统

GTSR adds 11 custom RecipeMaps and extensive crafting recipes:

GTSR 添加了11个自定义 RecipeMap 和大量合成配方：

- **Workbench recipes**: Basic machines (Air Compressor, Atmospheric Centrifuge, etc.), cache nodes, hatches
- **Assembler recipes**: Advanced machines (Ammonia Plant, Singularity Entangler, etc.), chips, catalysts, nodes, overpressure components
- **Custom RecipeMaps**: Large Coke Oven, Siemens-Martin Furnace, Ammonia Plant, Air Compressor, Atmospheric Centrifuge, Steam Singularity Entangler (NEI display), Geothermal Boiler (NEI display), Steam Fluid Drill (NEI display), Critical Singularity Compressor (NEI display), Dense State Manipulator (NEI display), Gear Steam Compressor (NEI display)
- **工作台配方**：基础机器（空气压缩机、大气离心机等）、缓存节点、仓室
- **组装机配方**：高级机器（制氨工厂、蒸汽奇点纠缠装置等）、芯片、催化剂、节点、超压组件
- **自定义 RecipeMap**：大型焦炉、平炉、制氨工厂、空气压缩机、大气离心机、蒸汽奇点纠缠装置（NEI显示）、地热锅炉（NEI显示）、蒸汽流体钻井（NEI显示）、临界纠缠奇点稳定装置（NEI显示）、致密态蒸汽操控装置（NEI显示）、自驱式机械蒸汽压缩机（NEI显示）

***

## Core Mechanic: Mixin Enhancements / 核心机制：Mixin 增强

GTSR injects 11 Mixin classes into GT5U and GT++ to fundamentally enhance the steam machine experience. These are critical to the mod's functionality:

GTSR 向 GT5U 和 GT++ 注入了 11 个 Mixin 类，从根本上增强了蒸汽机器体验。这些是模组功能的关键：

### MTESteamMultiBaseMixin — Steam Multiblock Core Enhancement / 蒸汽多方块核心增强

- **Superheated Steam 4x Speed / 过热蒸汽4倍速**: When any input hatch contains superheated steam, consumption ×4 and processing time ÷4 / 任意输入仓含过热蒸汽时，消耗×4、处理时间÷4
- **Cooling Hatch Support / 冷却仓支持**: Superheated steam → pressure cooling hatch (1:1), normal steam → cooling water (160:1) / 过热蒸汽→耐压冷却仓(1:1)，普通蒸汽→冷却水(160:1)
- **Standard Output Bus Compatibility / 标准输出总线兼容**: Fixes GT5U's `addOutputPartial()` ignoring standard output buses / 修复GT5U的`addOutputPartial()`忽略标准输出总线的问题
- **Dual Steam Type Consumption / 双蒸汽类型消耗**: `depleteInput()` can consume from both normal and superheated steam hatches / 可同时从普通蒸汽和过热蒸汽仓消耗

### Fluid Hatch Compatibility / 流体仓兼容

- **MTEHatchCustomFluidBaseMixin**: Steam-locked fluid hatch matches 3 steam types only (normal/superheated/IC2 superheated — dense and supercritical are NOT included); screwdriver auto-input toggle (1000 mB per 100 ticks ≈ 200 L/s). / 蒸汽锁定仓仅匹配 3 种蒸汽（普通/过热/IC2过热，不含致密与超临界）；螺丝刀自动输入开关（1000 mB/100 tick ≈ 200 L/s）。
- **MTEHatchInputMixin / MTEHatchInputBusMixin**: 4-state orthogonal toggle (input filter × auto-input) for ALL input hatches/buses via screwdriver right-click. Hatch: 1000 mB/100 ticks (≈200 L/s); Bus: 1 stack/100 ticks (≈1 stack/5 seconds). Shift+click preserves original mode. / 螺丝刀4状态正交切换（输入过滤×自动输入）。仓：1000 mB/100 tick（≈200 L/s）；总线：1组/100 tick（≈1组/5秒）。Shift+右键保留原模式。

### Steam Bus Behavior / 蒸汽总线行为

- **MTEHatchSteamBusInputMixin / MTEHatchSteamBusOutputMixin**: Steam input buses allow pipe pull from the front container (allowPullStack); steam output buses auto-push to the front container (pushOutputInventory) — both previously blocked by GT++. / 蒸汽输入总线允许正面管道**抽取**（allowPullStack），蒸汽输出总线自动向正面容器**推出**（pushOutputInventory）——两者此前均被GT++阻止。

### Recipe Fix / 配方修正

- **MTERockBreakerRecipeBuilderMixin**: All glowstone dust inputs in Rock Breaker recipes are non-consumable (consumed = false) — no circuit-6 gate. / 岩石破碎机配方中所有荧石粉输入一律不可消耗（consumed=false），无电路6判断。

### Other Mixins / 其他 Mixin

- **SteamHatchElementOutputBusMixin / CommonMetaTileEntityMixin**: HatchElement extension and unified auto-input scheduling. / HatchElement 扩展与统一自动输入调度。
- **BaseMetaTileEntityMixin**: Empty-hand sneak right-click on GTSR steam machines triggers descaling (IShiftRightClickDecalcifiable), canceling the default behavior. / 对 GTSR 蒸汽机器空手潜行右击执行除垢（IShiftRightClickDecalcifiable）并取消默认行为。
- **gtnl/SteamMultiMachineBaseGTNLMixin**: Soft GTNL compatibility — `@Pseudo` + runtime class detection; applies GTSR's full enhancement to GTNL steam machines only when `Config.gtnlEnhancement` is enabled (silent by default). / GTNL 软兼容——`@Pseudo` + 运行时类探测；仅在 `Config.gtnlEnhancement` 开启时对 GTNL 蒸汽机应用 GTSR 完整增强（默认静默）。

***

## Tech Stack / 技术栈

- Java 8 (Jabel) / Minecraft 1.7.10 / Forge 10.13.4.1614
- SpongePowered Mixin (11 mixin classes)
- ModularUI / StructureLib
- Dependencies: GT5U (explicit API dependency), GT++ (visible at compile time via the GT5U fat dev jar, no explicit declaration), Bartworks, TecTech (same fat dev jar), AE2, ModularUI/ModularUI2, GTNHLib, StructureLib, Postea, NEI, IC2, GTNEIOrePlugin, Botania, Waila; EFR (etfuturum) and BuildCraft are soft references (recipes only)

## License / 许可证

See LICENSE file.
详见 LICENSE 文件。
