<p align="center"><img alt="GTSR" src="README/GTSR.png"></p>

<h1 align="center">GT-Steam-Reborn</h1>

<p align="center"><strong><em>GTNH 蒸汽时代扩展模组</em></strong><br><strong><em>GTNH Steam Age Expansion Mod</em></strong></p>

<p align="center">
  <a href="LICENSE"><img alt="License AGPL-3.0" src="https://img.shields.io/badge/License-AGPL--3.0-blue.svg"></a>
  <img alt="Minecraft 1.7.10" src="https://img.shields.io/badge/Minecraft-1.7.10-blue.svg">
  <img alt="Forge 10.13.4.1614" src="https://img.shields.io/badge/Forge-10.13.4.1614-blue.svg">
  <a href="https://github.com/GTNewHorizons/GT-New-Horizons-Modpack"><img alt="GTNH 2.9.0 beta1-3 & RC1-2" src="https://img.shields.io/badge/GTNH-2.9.0%20beta1--3%20%26%20RC1--2-orange.svg"></a>
  <a href="https://github.com/MIAOKATZE/GT-Steam-Reborn/releases"><img alt="Release 1.20.98" src="https://img.shields.io/badge/Release-1.20.98-green.svg"></a>
</p>

GTSR 为 GTNH 增加蒸汽工业、远程物流和奇点科技。你可以扩建青铜加工基地，用枢纽连接跨维度的储罐与矿场，再推进过热、致密蒸汽与高级设备。

在繁荣维度探索遗址、解开机关并挑战首领，将六种原液带回基地加工。四种便携武器与停火后补弹的拟态弹药包，为远征提供从 LV 到 EV 的装备路线。

GTSR expands GTNH's steam progression with industrial machines, cross-dimensional logistics, singularity technology, the Prosperity dimension, six fluid-processing routes and portable weapons. Build a reliable steam base, link distant workshops through hubs and nodes, then bring resources and discoveries home from the ruins of Prosperity.

## 从基地到远征 / From Base to Expedition

| 你想做什么 | 可以从哪里开始 |
|---|---|
| 扩建青铜基地 | 大型焦炉与加固砖高炉建立材料基础，大型蒸汽熔炉承接批量熔炼。空气压缩机、大气离心机补足气体供应。 |
| 让加工持续运转 | 蒸汽流体钻井解决水源，地热锅炉和太阳能阵列提供蒸汽；机械压缩机将普通蒸汽转为过热蒸汽。先留足水和输出空间，再提高负荷。 |
| 把远方工坊接回主基地 | 建设蒸汽枢纽与蓄水枢纽，携带节点到矿场、加工间或其他维度；用枢纽终端检查方向、容量与传输速率。 |
| 整合矿物加工 | 奇点钻井枢纽收集远方资源，矿物处理集群编排加工链；物流单元接好输入与输出后，再添加增幅模块。 |
| 把蒸汽推进到更高阶段 | 热化学致密蒸汽发生系统拓展燃料供应，致密态操控与奇点设备支撑储运、发电和高级加工。 |
| 前往繁荣探索 | 准备回程锚点、武器和弹药，用时空校准工程进入维度；先阅读遗址线索，再完成机关与遭遇。 |

这些路线可以并行建设。锅炉、枢纽和加工机的总吞吐需要一起规划：只扩建机器并行而不补足供汽、冷却或输出空间，产线仍会受最弱的一环限制。熟悉当前机器的界面状态，再逐项提高负荷，比一次把所有档位拉满更容易找到瓶颈。

English quick start: use NEI for recipes and structure tooltips for building requirements. Hubs connect remote storage and workers; the Hub Terminal manages their flow and status. Prosperity adds ruins, encounters and processing resources. Portable weapons progress from LV to EV, with rechargeable mimic ammunition available at LuV. The detailed player guide below covers network controls, ore-chain supply, travel and weapon handling.




> [!NOTE]
> This is an unofficial mod. Please avoid discussing this mod in official GTNH forums.
> 这是一个非官方模组，讨论此模组时请注意场合。

> 📖 **完整文档请查阅 [Wiki](https://github.com/MIAOKATZE/GT-Steam-Reborn/wiki) / For full documentation, see the [Wiki](https://github.com/MIAOKATZE/GT-Steam-Reborn/wiki)**

## 下载与版本需求 / Downloads & Requirements

从 [最新发布页](https://github.com/MIAOKATZE/GT-Steam-Reborn/releases/latest) 下载普通 `gtsr-*.jar`，放入客户端和服务器的 `mods` 文件夹；`dev` 与 `sources` 文件不是游玩版本。支持 **GTNH 2.9.0 beta1–3 与 RC1–2**，当前版本以 **RC-2** 为基准。升级时请保留现有配置与存档备份。


| GTNH         | GTSR           | Maintenance / 维护 |
| ------------ | -------------- | :--------------: |
| 2.9.0 beta1-3 & RC1-2 | **1.20.0 +**（当前 / current） |        ✔️        |
| 2.9.0 beta-1&2 | 1.7.31~1.11.37  |        ✔️        |
| 2.9.0 beta-2 | 1.7.16~1.7.30  |        ✔️        |
| 2.9.0 beta-1 | 1.7.1\~1.7.15  |        ✔️        |
| 2.8.4        | 1.6.0          |        ❌️        |

***

## Multiblock Machines / 多方块机器

### Storage Hub Machines / 存储枢纽机器

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

<p align="center"><img src="README/MTEWaterHubArray-T1.png" width="240" alt="蓄水枢纽阵列 / Water Hub Array"> <img src="README/MTEWaterHubArray-T2.png" width="240" alt="蓄水枢纽阵列 / Water Hub Array"><img src="README/MTEWaterHubArray-T3.png" width="240" alt="蓄水枢纽阵列 / Water Hub Array"><br><em>蓄水枢纽阵列 / Water Hub Array（青铜/钢）</em></p>

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

### Singularity Drilling Hub / 奇点钻井枢纽

<p align="center"><img src="README/MTESingularityDrillingHub.png" width="400" alt="奇点钻井枢纽 / Singularity Drilling Hub"><br><em>奇点钻井枢纽 / Singularity Drilling Hub</em></p>

**奇点钻井枢纽 / Singularity Drilling Hub (SDH)**

仅钢级，必须使用过热蒸汽（无加速效果），驱动钻井与采矿节点；蒸汽消耗随活跃节点数增长。蒸汽时代的奇迹造物：基于蒸汽纠缠奇点，遍及世界每一个角落，攫取一切所需的资源。

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
| 绑定 Binding | 需枢纽奇点芯片；手持节点右击绑定/解绑，Alt+右击绑定手持整组 Requires Hub Singularity Chip; right-click with node to bind/unbind, Alt+right-click binds the entire held stack |

***

### Hub-Node Binding System / 枢纽-节点绑定系统

The Hub-Node system is GTSR's core innovation, enabling cross-chunk and cross-dimensional fluid transfer and remote operations.

枢纽-节点系统是 GTSR 的核心创新，实现跨区块甚至跨维度的流体传输和远程作业。

#### Binding Mechanism / 绑定机制


手持节点物品右键枢纽控制器绑定，Alt+右击绑定手持整组（奇点消耗=单节点成本×堆叠数量）。奇点消耗因节点类型而异（蒸汽节点按等级 0/1/8；通用流体节点全家 0；奇点蒸汽仓/输出仓各 1；采矿/钻井 1）。蒸汽枢纽阵列/蓄水枢纽阵列支持3状态循环：输出模式→输入模式→解绑。奇点仓模式锁定：已绑定时右键仅解绑（无模式翻转），终端/界面也无法切换其方向。原位重建枢纽后，节点最迟约 30 秒恢复绑定；破坏已绑定节点，掉落物保留绑定。


#### Transfer Mechanism / 传输机制

- **蒸汽枢纽阵列/蓄水枢纽阵列**：每20tick在枢纽与绑定节点间按有效速率传输流体。螺丝刀切换溢流输出模式。速率档适用于六缓存节点+四个奇点仓（奇点仓有效速率=固定基准×档位：蒸汽两仓基准 8,000,000 L/s、流体两仓 256,000 L/s；枢纽终端右击循环 100%→80%→60%→40%→20%→10%→5%→2%→1%→0%）；容量上限档（六缓存节点+两个接收类奇点仓）由终端潜行右击或 GUI 按钮循环 100%→80%→60%→40%→20%→10%→5%→2%→1%（见下方缓存节点一节）。
- **钻井枢纽**：消耗蒸汽驱动活跃节点。采矿节点产出→枢纽输出总线。钻井节点产出→枢纽输出仓。

#### Hub Terminal / 枢纽终端


枢纽终端是手持远程管理设备（1 蒸汽纠缠奇点 + 8 钢板环绕合成）。手持右击任意枢纽控制器即可打开对应的状态终端，告别在节点之间来回奔波。

**Cache Hub Status Terminal (Steam & Water hubs) / 缓存枢纽状态终端（蒸汽与蓄水枢纽阵列通用）**:

<p align="center"><img src="README/HubTerminalCacheStatus.png" width="400" alt="缓存枢纽状态终端 / Cache Hub Status Terminal"><br><em>缓存枢纽状态终端 / Cache Hub Status Terminal</em></p>
使用枢纽终端右击控制器，打开状态GUI。 / Right-click the controller on the hub terminal and open the status GUI.

- 每节点显示名字、坐标与维度、流体和储量。可调整速率、容量、方向、自动输出，重命名节点并传送到安全落点；奇点仓方向固定。操作细节见 [枢纽指南](README/FEATURES.md#枢纽与节点)。

**Drilling Hub Status Terminal / 钻井枢纽状态终端**:

<p align="center"><img src="README/HubTerminalDrillingStatus.png" width="400" alt="钻井枢纽状态终端 / Drilling Hub Status Terminal"><br><em>钻井枢纽状态终端 / Drilling Hub Status Terminal</em></p>
使用枢纽终端右击控制器，打开状态GUI。 / Right-click the controller on the hub terminal and open the status GUI.

- 查看节点等级、状态与坐标，远程启停、升级、重命名；停止或待机后可快捷回收并返还钻管。
- **Phase teleport / 阶段传送**: teleport directly above a bound node (y+1), cross-dimensional; consumes 1 Steam Entangled Singularity from your main inventory only after a safe landing spot is found / 传送到绑定节点正上方（y+1），支持跨维度；仅在找到安全落点后消耗主物品栏 1 个蒸汽纠缠奇点

***

### Steam Processing Machines / 蒸汽加工机器


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

### Mineral Logistics Cluster / 蒸汽动力矿物处理物流工程集群

<p align="center"><img src="README/CRUSH-T1.png" width="500" alt="矿物处理集群主结构全景 / MLC Main Structure Overview"> <img src="README/CRUSH-UI1.png" width="800" alt="集群终端拓扑页 / Cluster Terminal Topology Page"><br><em>矿物处理集群主结构（主段 15×20×29 + 延伸段串接）与集群终端拓扑页；模块合影、链路/增幅页等更多配图见项目 wiki / Mineral Logistics Cluster main structure (main segment 15×20×29 + daisy-chained extension segments) and cluster terminal topology page; more shots (module group, chain/amplifier pages) in the project wiki</em></p>

**蒸汽动力矿物处理物流工程集群/ Mineral Logistics Cluster (MLC) — in development, rolling out incrementally**

蒸汽驱动的矿石链式加工多方块工程集群：以物流模块为单元编排「粉碎→锻造→简易洗矿→洗矿→化洗→离心→热离→筛选→磁选→熔炼」加工链并自动吞吐矿石，以蒸汽与润滑剂为主驱动（热离与磁选链步需能源仓持续供电），是蒸汽时代的大型矿石处理中枢。


**模块清单 / Module List**：十种链步——粉碎/锻造/简易洗矿/洗矿/化学浸浴/离心/热离心/筛分/磁选/熔炼——由七类工作单元执行：粉碎机兼锻造、洗矿机兼简易洗矿/化学浸浴；另配物流单元（链编排与并行取料），可选装并行/速度/主产物/副产物/节汽五类增幅模块。集群共 14 个多方块机器：总控 1 + 工作单元 7 + 增幅 5 + 物流 1。锻造与简易洗矿是链步骤而非独立机器（各基础耗时 4 tick；简易洗矿需水，不依赖 GT++——GT++ 缺席时配方透传，该链步仍可用）。

**Module list**: the ten chain steps — crush / hammer / simple wash / ore wash / chemical bath / centrifuge / thermal centrifuge / sifting / magnetic separation / smelting — are executed by seven working-unit classes: the crusher also handles hammering, the ore washer also simple washing and chemical bathing; plus a logistics unit (chain orchestration and parallel fetching) and five optional amplifier modules (parallel / speed / primary / secondary / steam saver). The cluster totals 14 multiblock machines: 1 controller + 7 working units + 5 amplifiers + 1 logistics unit. Hammer and simple wash are chain steps, not standalone machines (4 base ticks each; simple wash needs water and does not depend on GT++ — recipes pass through when GT++ is absent, so the link stays available).

**结构 / Structure**：主段 15×20×29，背面最多串接 19 个 15×8×29 延伸段（总段数 ≤20，基础段+19延伸层）；四族结构方块 tier0-3 = 青铜/钢/钛/钨钢；每段可以挂载 加工（左）+ 增幅（中）+ 1 物流（右）模块各一个，其中加工模块和增幅模块不限制方向，物流模块需要朝向主结构的右边。

**Structure**: a 15×20×29 main segment with up to 19 15×8×29 extension segments chained behind it (≤20 segments in total, base plus 19 extensions); four structure block families tier 0-3 = bronze / steel / titanium / tungsten steel; each segment can mount one processing (left) + one amplifier (middle) + one logistics (right) module, where processing and amplifier modules have no facing restriction, while the logistics module must face the right side of the main structure.

**运行要点 / Operating Tips**

| 要点 | 玩家需要知道的内容 |
|---|---|
| 蒸汽与润滑剂 | 总控承担持续供给；高档结构与更多模块需要更强的供汽能力。热离心与磁选链步还需要持续供电。 |
| 物流接口 | 原料、配方流体与产出走物流单元自身的总线和仓室；总控输入仓用于蒸汽与润滑剂。 |
| 链路保存 | 编辑后点击保存才应用整条链。队列模式会等待单种物品攒满并行数再开批。 |
| 增幅模块 | 并行、速度、主产物、副产物与节汽模块可选；增幅液不足一整批时该模块本批失效，加工仍继续。 |
| 终端 | 拓扑、链路、增幅、统计、性能五页集中查看布局与成本。详细供给规则见 [集群指南](README/FEATURES.md#矿物处理集群)。 |

### Enhanced Processing Machines / 强化加工机器


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

<p align="center"><img src="README/MTEThermochemicalDenseSteamGenerator.png" width="500" alt="热化学致密蒸汽发生系统"><br><em>热化学致密蒸汽发生系统 / Thermochemical Dense Steam Generator</em></p>

**热化学致密蒸汽发生系统 / Thermochemical Dense Steam Generator (TCDS)**

吞下一切可燃流体的蒸汽巨兽：燃气与燃油皆可为食，终端可调**设定流量**驱动燃烧——产出与最大热量随流量伸缩，部分供给自动降流量不停机。

| 参数 Parameter | 数值 Value |
|----------|-------|
| 产出公式 Output Formula | 流量×热值×效率×2×热量%（默认 100 L/t：单燃料 = 200×热值，双燃料 = 200×(燃气+燃油热值)）Flow x heat value x efficiency x 2 x heat% (at default 100 L/t: single fuel = 200 x heat value, dual fuel = 200 x (gas + liquid heat values)) |
| 流量设定 Flow | ≥1（默认 100）L/t；<100 L 热量上限等比至 50% 地板，100~500 L 线性升至 150% 上限 ≥1 (default 100) L/t; below 100 L the heat cap scales down to a 50% floor, 100-500 L ramps linearly to a 150% cap |
| 燃烧效率 Efficiency | ≤500 L/t 100%，1000 L/t 约 50%，5000 L/t 约 25%，更高流量趋近 0 永不为 0 100% at ≤500 L/t, ~50% at 1000 L/t, ~25% at 5000 L/t, approaching but never reaching 0 |
| 热量 Heat | 0% 起步，上限 = 档位最大热量×流量因子（基准 200%）；供给不足即停机降温 starts at 0%, capped at tier max heat x flow factor (200% base); any supply shortage stops & cools |
| 燃料消耗 Fuel Consumption | 各族实际流量（供给不足自动降流量不停机）Each family's actual flow (partial supply derates flow without stopping) |
| 空气消耗 Air | 实际流量 ×100 L（不足即停机，不降载）Actual flow x 100 L (any shortage stops the machine, no derating) |
| 水耗 Water | 蒸汽 ÷160；热量 >100% 时缺水会爆炸 Steam ÷ 160; explodes on water shortage above 100% heat |

**额外功能 / Additional Features**

| 功能 Feature | 说明 Description |
|---|---|
| 输出档位 Output Tier | Shift+螺丝刀右键轮换 100→80→60→40→20→10→5→2→1%，最大热量 200/160/120/80/40/10/5/2/1%（10/5/2/1 档热量上限即 10/5/2/1%）Shift+screwdriver cycles 100→80→60→40→20→10→5→2→1%, max heat 200/160/120/80/40/10/5/2/1% (the 10/5/2/1 tiers cap heat at 10/5/2/1%) |
| 双燃料叠加 Dual-Fuel Stacking | 燃气与燃油同时供给时两种同烧，产出为两族流量×热值项直加（各自独立降流量）；双过热档需燃气热值 ≥350 且燃油 ≥450 With gas and liquid fuel supplied together both burn at once — output is the direct sum of both families' flow x heat-value terms (each derates its flow independently); dual superheat needs gas ≥350 and oil ≥450 |
| 致密档 Dense Tier | 致密蒸汽芯片（组装机配方）切换致密输出；过热致密档需燃气热值 ≥350 且燃油 ≥450 Dense Steam Chip (assembler recipe) for dense output; superheated dense tier needs gas heat value ≥350 and oil ≥450 |


***

### Singularity Machines / 奇点机器


**蒸汽奇点纠缠装置 / Steam Singularity Entangler (SSE)**

吞噬输入仓中最高等级蒸汽（普通/过热/超临界，不含致密），按饱和函数累积热量；热量达 100% 时产出 1 个蒸汽纠缠奇点。

| 参数 Parameter | 数值 Value |
|----------|-------|
| 蒸汽输入 Steam Input | 普通 / 过热 / 超临界（不含致密）Normal / Superheated / Supercritical (dense excluded) |
| 产出 Output | 1 蒸汽纠缠奇点（热量 100%）1 Steam Entangled Singularity (at 100% heat) |
| 并行 Parallel | 无 None |

<p align="center"><img src="README/MTESteamSingularityEntangler.png" width="450" alt="蒸汽奇点纠缠装置 / Steam Singularity Entangler"><br><em>蒸汽奇点纠缠装置 / Steam Singularity Entangler</em></p>

**临界纠缠奇点稳定装置 / Critical Entangled Singularity Stabilizer (CSC)**

仅接收致密态变体（致密蒸汽 / 致密过热 / 致密超临界），按饱和函数累积热量；热量达 100% 时产出 1 个临界蒸汽纠缠奇点；会吞噬输入仓全部蒸汽并禁用蒸汽冷却。

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

## 繁荣时代：探索与资源 / Prosperity

<p align="center"><img src="README/Prosperity-Landscape.png" width="440" alt="繁荣维度地貌"> <img src="README/Prosperity-Ruins.png" width="440" alt="繁荣维度遗址"><br><em>繁荣时代的荒野与遗址 / Landscapes and ruins of Prosperity</em></p>

这里曾有一套把城市与工坊连在一起的蒸汽文明。如今从草原、森林到荒漠与沼泽，散落着历史书页、史料信物、机械遗址和各自独立的遭遇。探索不只是开箱：阅读现场线索，操作机关，完成遭遇后再领取奖励；历史书会随着你的经历补充纪年，测绘罗盘帮助寻找入口。

**时空奇点校准工程 / Spacetime Singularity Calibration Engineering**

<p align="center"><img src="README/MTESpacetimeSingularityCalibration.png" width="500" alt="时空奇点校准工程"><br><em>时空奇点校准工程 / Spacetime Singularity Calibration Engineering</em></p>

建立通向繁荣的稳定通道：需要普通 LuV 及以上能源仓，输入电压至少 32,768 EU。预热 30 秒消耗 30,720 EU/t，校准后维持消耗 15,360 EU/t。核心只牵引实体、不破坏方块；潜行可退出牵引。保持结构、维护与供电，等待界面显示已校准后进入核心。出发前可用时空锚定信标 Alt+右击标记回程点，之后 Shift+持续右击 3 秒召回，每次成功消耗 20,480,000 EU。

**六条流体工业线 / Six Resource Fluids**

把探索所得原液送回基地，按照 NEI 中的配方分离、回收与深加工。它们不是同一种资源换皮：每条路线都有自己的中间液、粉体与副产物，需要蒸馏、化学反应、离心、筛分等设备衔接。

| 原液 | 产线定位 |
|---|---|
| 荒原叹息 | 酸气冷凝与酸类、伴生矿物回收。 |
| 浓稠油污 | 油污热处理与有机化工，衔接燃料和化工馏分。 |
| 金属风沙 | 湿捕金属与矿物，继续回收难熔金属、铂族、稀土及微量元素。 |
| 枯竭气息 | 气体处理与微量金属回收，保留各中间物流继续加工。 |
| 三途余汽 | 凝液蒸馏、含矿水相与真菌有机化工，产出含氟气体、氢氟酸、萤石、矿物及有机酸等。 |
| 至暗泥泞 | 泥泞均质、植物净液分离与有机物回收，衔接肥料、纤维和生物质相关产物。 |

植物净液是至暗泥泞加工所得中间液；含氟主线从三途余汽的凝液蒸馏开始。各支线的流体体积与粉体用途以当前配方为准；请在 [详细指南](README/FEATURES.md#六流体产线) 中查看选线与接续要点。

## 便携武器 / Portable Weapons

<p align="center"><img src="README/Weapon-LM12.png" width="230" alt="LM12"> <img src="README/Weapon-T20.png" width="230" alt="T20"><br><img src="README/Weapon-QLZ04.png" width="230" alt="QLZ04"> <img src="README/Weapon-107.png" width="230" alt="107 工程奇点控制器"><br><em>从 LV 火力到 EV 奇点控制 / LV to EV portable firepower</em></p>

| 武器 | 制造阶段 | 用途与模式 |
|---|---|---|
| LM12 | LV | 转管持续火力；标准与压制模式在射速、预转和过热管理之间取舍。 |
| T20 | MV | 20 mm 火力；切换空爆模式，适合接近目标时提前爆发。 |
| QLZ04 | HV | 35 mm 榴弹；普通、霰榴弹和 8 发轮毂弹夹模式，适合不同距离与射击节奏。 |
| 107 工程奇点控制器 | EV | 单发普通/临界奇点；普通模式牵引后爆发，失稳模式蓄力后直接爆发，两种模式支持手动遥爆。 |

默认 **左键开火，右键聚焦，C 切模式，V 切弹药，R 装填**；按键可在控制设置中调整。107 失稳模式按住左键蓄满 5 秒，松手发射；提前松手取消。弹体飞行中再次左键触发遥爆。

LM12、T20、QLZ04 的普通弹药包分别为 **500 / 160 / 60 发**，耗尽后移除。LuV 阶段可制造拟态弹药包：停火 **5 秒**后，每 **2 秒**分别恢复 **10 / 3 / 1 发**，耗尽仍保留。107 使用纠缠奇点物品装填。四种武器支持附魔；弹道、换弹退款和奇点范围详见 [武器指南](README/FEATURES.md#便携武器操作)。

## Single-Block Nodes / 单方块节点

### Cache Nodes & Singularity Compartments / 缓存节点与奇点仓室

<p align="center"><img src="README/MTECacheNodesAndSingularityCompartments.png" width="360" alt="缓存节点与奇点仓室 / Cache Nodes & Singularity Compartments"><br><em>缓存节点与奇点仓室 / Cache Nodes & Singularity Compartments</em></p>


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

缓存节点与两个接收类奇点仓（奇点通用蒸汽仓、奇点输入仓）支持容量上限档 {100, 80, 60, 40, 20, 10, 5, 2, 1}%：终端潜行右击本地循环，或在枢纽终端状态界面点击容量按钮远程循环；档位会保存，降档后超出部分温和保留在罐内（拒绝新入、不销毁）。发送类仓罐只出不进，无容量档。


**节点外观 / Node Appearance**

节点以流体窗显示当前流体，红橙边框表示从枢纽接收，紫蓝表示向枢纽输送，灰色表示未绑定。奇点仓直接右击可查看流体，手持终端时优先调整传输速率。


六种缓存节点的用途、容量与绑定成本见上表。蒸汽节点接入蒸汽枢纽，通用流体节点接入蓄水枢纽；超压型号需要钨钢枢纽与强化芯片。

**奇点仓四件套 / Singularity Compartments (4)**

奇点仓可装入对应多方块，直接对接枢纽网络。奇点通用蒸汽仓与蒸汽输出仓绑蒸汽枢纽，每仓消耗 1 个奇点；奇点输入仓与输出仓绑蓄水枢纽，无绑定消耗。接收仓从枢纽取流体，发送仓向枢纽回送，方向固定；已绑定时再次右击只会解绑。管道不能向奇点仓注入流体。


| 仓 Compartment | 绑定枢纽 Bound Hub | 容量 Capacity | 基准交互速率 Base Hub Rate | 流体范围 Fluid Range | 方向（锁定）Direction (locked) | 消耗 Cost |
|---|---|---|---|---|---|---|
| 奇点通用蒸汽仓 Singularity Steam Compartment | 蒸汽枢纽 Steam Hub | 8M L | 8,000,000 L/s | 蒸汽全家族 Full steam family | 从枢纽接受 Receive | 1 奇点 1 singularity |
| 奇点通用蒸汽输出仓 Singularity Steam Output Compartment | 蒸汽枢纽 Steam Hub | 8M L | 8,000,000 L/s | 蒸汽全家族 Full steam family | 向枢纽输送 Send | 1 奇点 1 singularity |
| 奇点输入仓 Singularity Fluid Input Compartment | 蓄水枢纽阵列 Water Hub Array | 256K L | 256,000 L/s | 任意流体 Any fluid | 从枢纽接受 Receive | 0 |
| 奇点输出仓 Singularity Fluid Output Compartment | 蓄水枢纽阵列 Water Hub Array | 256K L | 256,000 L/s | 任意流体 Any fluid | 向枢纽输送 Send | 0 |

流体窗保留上次储存的流体外观，空罐时仍能识别用途；当前储量以界面为准。


### Remote Worker Nodes / 远程工作节点

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

## Hatches / 仓室

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

| 参数 Parameter | 数值 Value |
|----------|-------|
| 适用机器 Used By | 任意多方块机器 Any multiblock machine |
| 输出 Output | 按词条阈值输出红石信号 Redstone signal by entry threshold |

> 奇点仓四件套已并入「缓存节点与奇点仓室」一节（见单方块节点章节）/ The Singularity Compartments (4) block has moved to "Cache Nodes & Singularity Compartments" (see Single-Block Nodes).

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
- **蒸汽纠缠奇点 / Steam Entangled Singularity**: Core binding material. Produced by the Steam Singularity Entangler (heat accumulation). Consumed when binding nodes to hubs and in various crafting recipes. / 核心绑定材料，由蒸汽奇点纠缠装置累积热量产出；节点绑定枢纽与多种合成均会消耗。
- **临界蒸汽纠缠奇点 / Critical Steam Entangled Singularity**: Produced by the Critical Entangled Singularity Stabilizer (CSC). Used for more advanced crafting and amplification; legend says it can tear apart the very limits of dimensions... DANGEROUS — it explodes when dropped, never discard it! The drop explosion guarantees that normal singularities will appear. / 由临界纠缠奇点稳定装置（CSC）产出。用于更高级的合成与增幅；传说其能够彻底撕开维度的限制……危险品——掉落物会爆炸，请勿丢弃！掉落爆炸保证会出现普通奇点。
- **枢纽奇点芯片 / Hub Singularity Chip**: Required for Steam/Water Hub node binding, multiplies hub total capacity ×5. Also enables hub debug mode when right-clicked. Removing it from a filled hub swallows the stored fluid exceeding the reduced capacity. / 蒸汽/蓄水枢纽节点绑定所需，枢纽总容量×5；右击可进入枢纽调试模式；从已填充枢纽取下会吞掉超出缩减后容量的流体。
- **强化枢纽奇点芯片 / Reinforced Hub Singularity Chip**: For tier 3 (TungstenSteel) Steam/Water hubs — on the Steam Hub it enables dense/supercritical steam; on both hubs it grants ×20 capacity (takes priority over the ×5 Hub Chip bonus) and unlocks overpressure cache node binding; right-click a hub with it to list bound cache nodes. / 等级3（钨钢）蒸汽/蓄水枢纽阵列通用——蒸汽枢纽阵列解锁致密/超临界蒸汽；双枢纽容量×20（优先于普通芯片×5）并解锁超压缓存节点绑定；手持右击枢纽可列出已绑定缓存节点。
- **蒸汽轮机循环超限芯片 / Steam Turbine Cycle Overlimit Chip**: For Mega Steam Turbine Array controller slot — requires all 4 extra stack groups to activate: superheated/supercritical (incl. dense) steam cooling becomes distilled water, and steam efficiency factors stack within their steam family (e.g. supercritical = 超临界+过热+蒸汽 = 2.5×). / 装入巨型蒸汽轮机阵列控制器槽，需完成全部4组额外叠加层：过热/超临界（含致密）蒸汽冷却直接产蒸馏水，蒸汽效率因子按蒸汽家族内叠加（如超临界=超临界+过热+蒸汽=2.5倍）。
- **地热过热芯片 / Geothermal Overheat Chip**: For Large Geothermal Steam Boiler (steel tier) — enables superheated steam output and rare byproducts. / 用于大型地热蒸汽锅炉（钢级）——启用过热蒸汽输出与稀有副产物。
- **稀有气体分离芯片 / Rare Gas Separation Chip**: For Atmospheric Centrifuge — unlocks recipes with >3 fluid outputs (up to 9). / 用于大气离心机——解锁超过 3 个流体输出的配方（最多 9 个）。
- **矿脉裂解器芯片（T1/T2/T3）/ Vein Pyrolyzer Chip (T1/T2/T3)**: For Vein Steam Pyrolyzer — expands underground fluid scan range. / 用于地脉蒸汽热解机——扩大地下流体扫描范围。
- **制氨催化剂（7种变体）/ Ammonia Catalyst (7 variants)**: For Ammonia Plant — determines parallel count and reaction time. 7-tier progression from Nickel to Quantum. / 用于制氨工厂——决定并行数与反应时间，镍至量子共 7 级进阶。


***

## 配方与进阶 / Recipes & Progression

用 NEI 查询当前整合包中的配方与材料需求，按机器说明选择蒸汽、仓室与芯片。工作台负责部分基础设备，组装机承接高级控制器、奇点组件、催化剂与武器。需要操作细节时，请阅读 [玩家详细指南](README/FEATURES.md) 或 [Wiki](https://github.com/MIAOKATZE/GT-Steam-Reborn/wiki)。

***

## BetterQuesting Questline Integration / BetterQuesting 任务线整合

<p align="center"><img src="README/BQ.png" width="450" alt="任务线「GT 蒸汽重生」任务总览 / Quest line GT Steam Reborn overview: guided quests"><br><em>任务线「GT 蒸汽重生」，连线为任务依赖 / Quest line "GT Steam Reborn"; lines are quest dependencies</em></p>

- **内置引导任务线**：任务覆盖青铜基地建设、蒸汽经济学、枢纽网络到奇点账户的完整进度线，任务标题与描述随游戏语言自动切换（中/英）。/ **Built-in guided questline**: guided quests covering the full progression from bronze-base acceptance and steam economics to hub networks and the singularity account; quest titles and descriptions follow the game language (CN/EN).
- **自动更新**：进入世界自动加载引导任务，模组升级会刷新任务内容并保留完成与领取进度。/ **Automatic updates**: quest definitions refresh on world entry while keeping completion and claim progress.
- **可选依赖**：BetterQuesting 未安装时本整合静默停用，其余功能不受影响。/ **Optional dependency**: with BetterQuesting absent, this integration silently disables and nothing else is affected.

***

## License / 许可证

AGPL-3.0 — see the LICENSE file.
采用 AGPL-3.0 许可证，详见 LICENSE 文件。


文档更新：2026-10-07 · 当前版本：1.20.98
