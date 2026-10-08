<p align="center"><img alt="GTSR" src="README/GTSR.png"></p>

<h1 align="center">GT-Steam-Reborn</h1>

<p align="center"><strong><em>GTNH 蒸汽时代扩展模组</em></strong><br><strong><em>GTNH Steam Age Expansion Mod</em></strong></p>

<p align="center">
  <a href="LICENSE"><img alt="License AGPL-3.0" src="https://img.shields.io/badge/License-AGPL--3.0-blue.svg"></a>
  <img alt="Minecraft 1.7.10" src="https://img.shields.io/badge/Minecraft-1.7.10-blue.svg">
  <img alt="Forge 10.13.4.1614" src="https://img.shields.io/badge/Forge-10.13.4.1614-blue.svg">
  <a href="https://github.com/GTNewHorizons/GT-New-Horizons-Modpack"><img alt="GTNH 2.9.0 beta1-3 & RC1-2" src="https://img.shields.io/badge/GTNH-2.9.0%20beta1--3%20%26%20RC1--2-orange.svg"></a>
  <a href="https://github.com/MIAOKATZE/GT-Steam-Reborn/releases"><img alt="Release 1.21.1" src="https://img.shields.io/badge/Release-1.21.1-green.svg"></a>
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

<p align="center"><img src="README/MTEWaterHubArray-T1.png" width="240" alt="蓄水枢纽阵列 / Water Hub Array"> <img src="README/MTEWaterHubArray-T2.png" width="240" alt="蓄水枢纽阵列 / Water Hub Array"><img src="README/MTEWaterHubArray-T3.png" width="240" alt="蓄水枢纽阵列 / Water Hub Array"><br><em>蓄水枢纽阵列 / Water Hub Array（青铜/钢/钨钢）</em></p>

**蓄水枢纽阵列 / Water Hub Array (WHA)**

3级（青铜/钢/钨钢）通用流体存储枢纽，不限流体种类（同一枢纽同时仅存一种流体）。接受通用流体缓存节点与奇点输入/输出仓（支持跨维度传输），双向接口。

***

枢纽奇点芯片解锁节点绑定，强化芯片进一步支持超压节点与高级蒸汽。蓄水枢纽仍一次储存一种流体；取下扩容芯片会损失超出缩减后容量的流体。

### Singularity Drilling Hub / 奇点钻井枢纽

<p align="center"><img src="README/MTESingularityDrillingHub.png" width="400" alt="奇点钻井枢纽 / Singularity Drilling Hub"><br><em>奇点钻井枢纽 / Singularity Drilling Hub</em></p>

**奇点钻井枢纽 / Singularity Drilling Hub (SDH)**

仅钢级，必须使用过热蒸汽（无加速效果），驱动钻井与采矿节点；蒸汽消耗随活跃节点数增长。蒸汽时代的奇迹造物：基于蒸汽纠缠奇点，遍及世界每一个角落，攫取一切所需的资源。

***

### Hub-Node Binding System / 枢纽-节点绑定系统

枢纽-节点系统是 GTSR 的核心创新，实现跨区块甚至跨维度的流体传输和远程作业。

#### Binding Mechanism / 绑定机制

节点绑定枢纽后可在输入与输出方向间切换；奇点仓方向固定。节点类型决定绑定所需材料，已绑定节点被破坏后掉落物保留绑定，原位重建枢纽也可恢复连接。

#### Transfer Mechanism / 传输机制

- **蒸汽枢纽阵列/蓄水枢纽阵列**：在枢纽与绑定节点间传输流体，支持溢流输出。缓存节点和奇点仓可调传输速率，接收类节点还可设置容量上限。
- **钻井枢纽**：消耗蒸汽驱动活跃节点。采矿节点产出→枢纽输出总线。钻井节点产出→枢纽输出仓。

#### Hub Terminal / 枢纽终端

枢纽终端是手持远程管理设备，打开枢纽控制器的状态终端，集中查看和管理远方节点。

**Cache Hub Status Terminal (Steam & Water hubs) / 缓存枢纽状态终端（蒸汽与蓄水枢纽阵列通用）**:

<p align="center"><img src="README/HubTerminalCacheStatus.png" width="400" alt="缓存枢纽状态终端 / Cache Hub Status Terminal"><br><em>缓存枢纽状态终端 / Cache Hub Status Terminal</em></p>

- 每节点显示名字、坐标与维度、流体和储量。可调整速率、容量、方向、自动输出，重命名节点并传送到安全落点；奇点仓方向固定。操作细节见 [枢纽指南](README/FEATURES.md#枢纽与节点)。

**Drilling Hub Status Terminal / 钻井枢纽状态终端**:

<p align="center"><img src="README/HubTerminalDrillingStatus.png" width="400" alt="钻井枢纽状态终端 / Drilling Hub Status Terminal"><br><em>钻井枢纽状态终端 / Drilling Hub Status Terminal</em></p>

- 查看节点等级、状态与坐标，远程启停、升级、重命名；停止或待机后可快捷回收并返还钻管。
- **阶段传送 / Phase teleport**：支持跨维度前往绑定节点，找到安全落点后才消耗蒸汽纠缠奇点。

***

绑定、传输、终端与节点详情见 [枢纽指南](https://github.com/MIAOKATZE/GT-Steam-Reborn/wiki/Hub-System_CN)。

### Steam Processing Machines / 蒸汽加工机器

<p align="center"><img src="README/MTELargeSteamFurnace-T1.png" width="260" alt="大型蒸汽熔炉 / Large Steam Furnace"> <img src="README/MTELargeSteamFurnace-T2.png" width="260" alt="大型蒸汽熔炉 / Large Steam Furnace"><br><em>大型蒸汽熔炉 / Large Steam Furnace（青铜/钢）</em></p>

<p align="center"><img src="README/MTEAirCompressor-T1.png" width="260" alt="空气压缩机 / Air Compressor"> <img src="README/MTEAirCompressor-T2.png" width="260" alt="空气压缩机 / Air Compressor"><br><em>空气压缩机 / Air Compressor（青铜/钢）</em></p>

**大型蒸汽熔炉 / Large Steam Furnace (LSF)**

蒸汽驱动的工业化熔炼设备，具有更大的并行数。

**空气压缩机 / Air Compressor (AC)**

产出空气（下界维度产出下界空气），远优于普通压缩机的速度与便捷度。

<p align="center"><img src="README/MTEAtmosphericCentrifuge-T1.png" width="260" alt="大气离心机 / Atmospheric Centrifuge"> <img src="README/MTEAtmosphericCentrifuge-T2.png" width="260" alt="大气离心机 / Atmospheric Centrifuge"><br><em>大气离心机 / Atmospheric Centrifuge（青铜/钢）</em></p>

**大气离心机 / Atmospheric Centrifuge (ATC)**

分离空气中的气体；稀有气体芯片解锁多输出配方，青铜级不能安装芯片。

<p align="center"><img src="README/MTESteamFluidDrill-T1.png" width="260" alt="蒸汽流体钻井 / Steam Fluid Drill"> <img src="README/MTESteamFluidDrill-T2.png" width="260" alt="蒸汽流体钻井 / Steam Fluid Drill"><br><em>蒸汽流体钻井 / Steam Fluid Drill（青铜/钢）</em></p>

<p align="center"><img src="README/MTECrustSteamBorer.png" width="340" alt="地壳蒸汽掘进机 / Crust Steam Borer"><br><em>地壳蒸汽掘进机 / Crust Steam Borer</em></p>

**蒸汽流体钻井 / Steam Fluid Drill (SFD)**

产出水、蒸馏水、盐水或岩浆，钢级支持切换产出模式。

**地壳蒸汽掘进机 / Crust Steam Borer (CSB)**

虚空采矿——按维度掉落表产出矿石。

<p align="center"><img src="README/MTECrustMatterAggregator.png" width="500" alt="地壳物质聚合器 / Crust Matter Aggregator"><br><em>地壳物质聚合器 / Crust Matter Aggregator</em></p>
<p align="center"><img src="README/MTECrustMatterAggregatorUI.png" width="500" alt="地壳物质聚合器终端配置界面 / Crust Matter Aggregator Terminal UI"><br><em>地壳物质聚合器终端配置界面 / Crust Matter Aggregator Terminal UI</em></p>

**地壳物质聚合器 / Crust Matter Aggregator (CMA)**

仅钢级，跨维度虚空采矿（经终端配置界面操作）。

<p align="center"><img src="README/MTEVeinSteamPyrolyzer-T1.png" width="260" alt="地脉蒸汽热解机 / Vein Steam Pyrolyzer"> <img src="README/MTEVeinSteamPyrolyzer-T2.png" width="260" alt="地脉蒸汽热解机 / Vein Steam Pyrolyzer"><br><em>地脉蒸汽热解机 / Vein Steam Pyrolyzer（青铜/钢）</em></p>

**地脉蒸汽热解机 / Vein Steam Pyrolyzer (VSP)**

以蒸汽为能源逆向注入地下，增加地下流体储量，解决长期存档中流体枯竭问题。

***

地壳物质聚合器支持原矿、粗矿与粉碎矿模式，以及矿物筛选和定向采集；地脉热解机的芯片扩展地下流体扫描。结构与参数见 [蒸汽基础机器](https://github.com/MIAOKATZE/GT-Steam-Reborn/wiki/Steam-Base-Machines_CN)。

### Mineral Logistics Cluster / 蒸汽动力矿物处理物流工程集群

<p align="center"><img src="README/CRUSH-T1.png" width="500" alt="矿物处理集群主结构全景 / MLC Main Structure Overview"> <img src="README/CRUSH-UI1.png" width="800" alt="集群终端拓扑页 / Cluster Terminal Topology Page"><br><em>矿物处理集群主结构与终端拓扑页 / Mineral Logistics Cluster structure and terminal topology</em></p>

**蒸汽动力矿物处理物流工程集群/ Mineral Logistics Cluster (MLC) — in development, rolling out incrementally**

蒸汽驱动的矿石链式加工多方块工程集群：以物流模块为单元编排「粉碎→锻造→简易洗矿→洗矿→化洗→离心→热离→筛选→磁选→熔炼」加工链并自动吞吐矿石，以蒸汽与润滑剂为主驱动（热离与磁选链步需能源仓持续供电），是蒸汽时代的大型矿石处理中枢。

**模块清单 / Module List**：粉碎、锻造、简易洗矿、洗矿、化学浸浴、离心、热离心、筛分、磁选与熔炼由工作单元执行；物流单元负责链编排与取料，可选装并行、速度、主产物、副产物和节汽增幅模块。锻造与简易洗矿是链步骤而非独立机器，简易洗矿需要水，不依赖 GT++。

**结构 / Structure**：主段后方可串接延伸段，结构底材从青铜、钢推进到钛与钨钢。加工、增幅和物流模块挂载在主框架上，物流模块有朝向要求。总控承担蒸汽与润滑供给，原料、配方流体与产出走物流单元接口。

集群终端集中查看拓扑、链路、增幅、统计与性能。队列模式等待原料满足一批再处理；增幅液不足时该批对应增幅失效，加工仍继续。详见 [集群指南](https://github.com/MIAOKATZE/GT-Steam-Reborn/wiki/Mineral-Processing-Cluster_CN)。

### Enhanced Processing Machines / 强化加工机器

<p align="center"><img src="README/MTELargeCokeOven-T1.png" width="240" alt="大型焦炉 / Large Coke Oven"> <img src="README/MTELargeCokeOven-T2.png" width="240" alt="大型焦炉 / Large Coke Oven"><br><em>大型焦炉 / Large Coke Oven（青铜/钢）</em></p>

<p align="center"><img src="README/MTESiemensMartinFurnace.png" width="400" alt="平炉 / Siemens-Martin Furnace"><br><em>平炉 / Siemens-Martin Furnace</em></p>

**大型焦炉 / Large Coke Oven (LCO)**

无需供能的自发焦炉，使用 GT5U 原版焦炉配方（煤炭/煤块/原木/甘蔗/仙人掌等）。

**平炉 / Siemens-Martin Furnace (SMF)**

仅钢级，过热蒸汽驱动；过热机制提升炉温，支持炉温带来的加工增益。

<p align="center"><img src="README/MTELargeGeothermalSteamBoiler-T1.png" width="260" alt="大型地热蒸汽锅炉 / Large Geothermal Steam Boiler"> <img src="README/MTELargeGeothermalSteamBoiler-T2.png" width="260" alt="大型地热蒸汽锅炉 / Large Geothermal Steam Boiler"><br><em>大型地热蒸汽锅炉 / Large Geothermal Steam Boiler（青铜/钢）</em></p>

**大型地热蒸汽锅炉 / Large Geothermal Steam Boiler (LGB)**

消耗岩浆产蒸汽；结垢与超压机制并存。

<p align="center"><img src="README/MTEMegaSteamTurbineArray-T1.png" width="240" alt="巨型蒸汽轮机机组 / Mega Steam Turbine Array（等级 1/3/6）"> <img src="README/MTEMegaSteamTurbineArray-T3.png" width="240" alt="巨型蒸汽轮机机组 / Mega Steam Turbine Array（等级 1/3/6）"> <img src="README/MTEMegaSteamTurbineArray-T6.png" width="240" alt="巨型蒸汽轮机机组 / Mega Steam Turbine Array（等级 1/3/6）"><br><em>巨型蒸汽轮机机组 / Mega Steam Turbine Array（等级 1/3/6）</em></p>

**巨型蒸汽轮机机组 / Mega Steam Turbine Array (MSTA)**

12级蒸汽发电机组；堆叠层数越多效率上限越高，支持全蒸汽类型。

<p align="center"><img src="README/MTELargeSolarOverpressureArray-T1.png" width="240" alt="大型太阳能超压阵列 / Large Solar Overpressure Array"> <img src="README/MTELargeSolarOverpressureArray-T2.png" width="240" alt="大型太阳能超压阵列 / Large Solar Overpressure Array"> <img src="README/MTELargeSolarOverpressureArray-T3.png" width="240" alt="大型太阳能超压阵列 / Large Solar Overpressure Array"><br><em>大型太阳能超压阵列 / Large Solar Overpressure Array（青铜/钢/银）</em></p>

**大型太阳能超压阵列 / Large Solar Overpressure Array (LSOA)**

3级（青铜/钢/银）太阳能产蒸汽阵列；银级产出过热蒸汽。

<p align="center"><img src="README/MTEKineticProcessingArray-T1.png" width="260" alt="动力加工阵列 / Kinetic Processing Array（等级 1/5）"> <img src="README/MTEKineticProcessingArray-T5.png" width="260" alt="动力加工阵列 / Kinetic Processing Array（等级 1/5）"><br><em>动力加工阵列 / Kinetic Processing Array（等级 1/5）</em></p>

**动力加工阵列 / Kinetic Processing Array (KPA)**

仅过热蒸汽，12级；处理放入的任意单方块机器配方。

<p align="center"><img src="README/MTEGearSteamCompressor-T1.png" width="260" alt="自驱式机械蒸汽压缩机 / Gear Steam Compressor"> <img src="README/MTEGearSteamCompressor-T2.png" width="260" alt="自驱式机械蒸汽压缩机 / Gear Steam Compressor"><br><em>自驱式机械蒸汽压缩机 / Gear Steam Compressor（青铜/钢）</em></p>

**自驱式机械蒸汽压缩机 / Gear Steam Compressor (GSC)**

普通蒸汽转化为过热蒸汽与蒸馏水，是无需电力锅炉即可取得过热蒸汽的关键机器。

<p align="center"><img src="README/MTEAmmoniaPlant.png" width="400" alt="制氨工厂 / Ammonia Plant"><br><em>制氨工厂 / Ammonia Plant</em></p>

**制氨工厂 / Ammonia Plant (AP)**

仅钢级，热量系统 + 7级催化剂（更高级催化剂=更多并行+更快反应），过热蒸汽为副产物。

<p align="center"><img src="README/MTEReinforcedBrickBlastFurnace.png" width="260" alt="加固砖高炉 / Reinforced Brick Blast Furnace"><br><em>加固砖高炉 / Reinforced Brick Blast Furnace</em></p>

**加固砖高炉 / Reinforced Brick Blast Furnace (RBBF)**

单级、无需蒸汽，执行 GT5U 原始高炉配方；炉温越高并行越多、配方越快。

<p align="center"><img src="README/MTEThermochemicalDenseSteamGenerator.png" width="500" alt="热化学致密蒸汽发生系统"><br><em>热化学致密蒸汽发生系统 / Thermochemical Dense Steam Generator</em></p>

**热化学致密蒸汽发生系统 / Thermochemical Dense Steam Generator (TCDS)**

吞下一切可燃流体的蒸汽巨兽：燃气与燃油皆可为食，终端可调**设定流量**驱动燃烧——产出与最大热量随流量伸缩，部分供给自动降流量不停机。

燃气与燃油可同时供给，致密蒸汽芯片启用致密输出；空气、水与燃料供给共同影响运行。详情见 [热化学致密蒸汽发生系统](https://github.com/MIAOKATZE/GT-Steam-Reborn/wiki/Dense-Steam-Generator_CN)。

***

地热与太阳能设备使用普通水会结垢，蒸馏水可避免结垢；缺水会停机。地热过热芯片启用过热蒸汽与稀有副产物。动力加工阵列兼容洁净室与 ME 合成总线，管道和齿轮箱可升级加工能力。详细机制见 [强化加工机器](https://github.com/MIAOKATZE/GT-Steam-Reborn/wiki/Enhanced-Machines_CN)。

### Singularity Machines / 奇点机器

**蒸汽奇点纠缠装置 / Steam Singularity Entangler (SSE)**

消耗输入仓中最高等级的普通、过热或超临界蒸汽，累积热量产出蒸汽纠缠奇点；不接受致密蒸汽。

<p align="center"><img src="README/MTESteamSingularityEntangler.png" width="450" alt="蒸汽奇点纠缠装置 / Steam Singularity Entangler"><br><em>蒸汽奇点纠缠装置 / Steam Singularity Entangler</em></p>

**临界纠缠奇点稳定装置 / Critical Entangled Singularity Stabilizer (CSC)**

仅接收致密蒸汽、致密过热与致密超临界蒸汽，累积热量产出临界蒸汽纠缠奇点。运行时消耗输入仓蒸汽并禁用蒸汽冷却，需要输入总线；结构核心显示纠缠奇点动画。

<p align="center"><img src="README/MTECriticalSingularityCompressor.png" width="450" alt="临界纠缠奇点稳定装置 / Critical Entangled Singularity Stabilizer"><br><em>临界纠缠奇点稳定装置 / Critical Entangled Singularity Stabilizer</em></p>

**致密态蒸汽操控装置 / Dense State Manipulator (DSM)**

支持蒸汽压缩和蒸汽解压，以普通或临界奇点为燃料，实现常规蒸汽与致密态蒸汽转换；临界奇点可避免普通奇点的输出损失。

<p align="center"><img src="README/MTEDenseStateManipulator.png" width="450" alt="致密态蒸汽操控装置 / Dense State Manipulator"><br><em>致密态蒸汽操控装置 / Dense State Manipulator</em></p>

***

压缩、解压与纠缠设备的结构和运行条件见 [奇点体系](https://github.com/MIAOKATZE/GT-Steam-Reborn/wiki/Singularity-System_CN)。

## 繁荣时代：探索与资源 / Prosperity

<p align="center"><img src="README/Prosperity-Landscape.png" width="440" alt="繁荣维度地貌"> <img src="README/Prosperity-Ruins.png" width="440" alt="繁荣维度遗址"><br><em>繁荣时代的荒野与遗址 / Landscapes and ruins of Prosperity</em></p>

这里曾有一套把城市与工坊连在一起的蒸汽文明。如今从草原、森林到荒漠与沼泽，散落着历史书页、史料信物、机械遗址和各自独立的遭遇。探索不只是开箱：阅读现场线索，操作机关，完成遭遇后再领取奖励；历史书会随着你的经历补充纪年，测绘罗盘帮助寻找入口。

**时空奇点校准工程 / Spacetime Singularity Calibration Engineering**

<p align="center"><img src="README/MTESpacetimeSingularityCalibration.png" width="500" alt="时空奇点校准工程"><br><em>时空奇点校准工程 / Spacetime Singularity Calibration Engineering</em></p>

建立通向繁荣的稳定通道，需要 LuV 阶段能源与完整结构、维护及持续供电。核心牵引实体而不破坏方块，校准完成后可进入繁荣。时空锚定信标保存回程点并提供跨维度返程。

入口与返程条件见 [时空校准指南](https://github.com/MIAOKATZE/GT-Steam-Reborn/wiki/Spacetime-Calibration_CN)；遗址、故事与遭遇见 [繁荣维度](https://github.com/MIAOKATZE/GT-Steam-Reborn/wiki/Prosperity-Dimension_CN)。

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

各原液的接续加工见 [六流体工业线](https://github.com/MIAOKATZE/GT-Steam-Reborn/wiki/Industrial-Fluids_CN)。

## 便携武器 / Portable Weapons

<p align="center"><img src="README/Weapon-LM12.png" width="230" alt="LM12"> <img src="README/Weapon-T20.png" width="230" alt="T20"><br><img src="README/Weapon-QLZ04.png" width="230" alt="QLZ04"> <img src="README/Weapon-107.png" width="230" alt="107 工程奇点控制器"><br><em>从 LV 火力到 EV 奇点控制 / LV to EV portable firepower</em></p>

LM12 提供转管持续火力，标准与压制模式有不同的过热管理；T20 支持空爆；QLZ04 提供普通、霰榴弹和轮毂弹夹模式；107 工程奇点控制器可装填普通或临界奇点，普通模式牵引后爆发，失稳模式蓄力后直接引爆，均支持手动遥爆。制造阶段依次为 LV、MV、HV 与 EV。

普通弹药包耗尽会销毁；拟态弹药包停火后缓慢补弹，耗尽仍保留。四种武器支持附魔，107 使用纠缠奇点物品装填。模式、弹药与附魔详情见 [便携武器指南](https://github.com/MIAOKATZE/GT-Steam-Reborn/wiki/Portable-Weapons_CN)。

## Single-Block Nodes / 单方块节点

### Cache Nodes & Singularity Compartments / 缓存节点与奇点仓室

<p align="center"><img src="README/MTECacheNodesAndSingularityCompartments.png" width="360" alt="缓存节点与奇点仓室 / Cache Nodes & Singularity Compartments"><br><em>缓存节点与奇点仓室 / Cache Nodes & Singularity Compartments</em></p>

基于数字储罐的节点，绑定枢纽实现跨区块/维度流体传输。支持流体锁定、自动输出、溢出虚空、枢纽终端调整交互速率（六缓存节点+四奇点仓）与容量上限档（见下）。

**容量上限档 / Capacity Limit Tier**

缓存节点与接收类奇点仓支持保存容量上限设置，可在本地或状态终端远程调整。降低上限后，超出部分保留在罐内并拒绝新输入，不会直接销毁；发送类仓只出不进，不设容量档。

**节点外观 / Node Appearance**

流体窗显示当前流体，红橙边框表示从枢纽接收，紫蓝表示向枢纽输送，灰色表示未绑定；当前储量以界面为准。

蒸汽节点接入蒸汽枢纽：基础型接受普通蒸汽，强化型兼容过热蒸汽，超压型接受全部蒸汽类型。通用、耐压通用与超压通用流体节点接入蓄水枢纽，接受任意流体；超压型号需要钨钢枢纽与强化芯片。

**奇点仓四件套 / Singularity Compartments (4)**

奇点仓可装入对应多方块，直接对接枢纽网络。奇点通用蒸汽仓与蒸汽输出仓绑定蒸汽枢纽，奇点输入仓与输出仓绑定蓄水枢纽。接收仓从枢纽取流体，发送仓回送流体，方向固定；再次操作只会解绑。管道不能向奇点仓注入流体。

流体窗保留上次储存的流体外观，空罐时仍能识别用途；当前储量以界面为准。

### Remote Worker Nodes / 远程工作节点

<p align="center"><img src="README/MTERemoteWorkerNodes.png" width="320" alt="远程工作节点 / Remote Worker Nodes"><br><em>远程工作节点 / Remote Worker Nodes</em></p>

由奇点钻井枢纽驱动执行远程作业的节点。消耗钻管向下钻探，下降过程中逐层扫描并开采。

**奇点采矿节点 / Singularity Miner Node**

5级升级体系（矿石钻机多方块控制器 + 奇点），提升采矿范围、时运与速度，支持粉碎矿模式；绑定枢纽后启用区块加载。

**奇点钻井节点 / Singularity Drilling Node**

4级升级体系（石油钻机多方块控制器 + 奇点），等级越高抽取系数与作业范围越大，各区块独立抽取和枯竭，绑定后支持远程区块加载。

***

## Hatches / 仓室

<p align="center"><img src="README/MTEAllHatches.png" width="380" alt="全部仓室 / All Hatches"><br><em>全部仓室 / All Hatches</em></p>

GTSR 机器专用仓室，具有不同容量和流体过滤：

**蒸汽输入/输出仓（通用）/ Steam Input/Output Hatches (Generic)**

GTSR 机器基础蒸汽输入/输出仓。

**蒸汽输出仓 / Steam Output Hatch**

GTSR 机器专用蒸汽输出仓。

**蒸汽冷却仓 / Steam Cooling Hatch**

接收蒸汽冷却过程产生的水。

**耐压蒸汽输入/输出仓 / Pressure Steam Input/Output Hatches**

接受普通与过热蒸汽的耐压仓室。

**耐压蒸汽冷却仓 / Pressure Steam Cooling Hatch**

蒸汽冷却仓的耐压变体。

**蒸汽枢纽输入/输出仓 / Steam Hub Input/Output Hatches**

容量由枢纽控制器决定，填充/抽取委托给蒸汽枢纽。

**蓄水枢纽输入/输出仓 / Water Hub Input/Output Hatches**

容量由枢纽控制器决定，填充/抽取委托给蓄水枢纽阵列（不限流体种类，同一枢纽同时仅存一种）。

**巨型超压蒸汽输入仓 / Mega Overpressure Steam Input Hatch**

专用于巨型蒸汽轮机机组，蒸汽奇点纠缠装置等（SSE/CSC/DSM 亦可安装）；接受全部蒸汽类型。

**巨型空气输入仓 / Mega Air Input Hatch**

仅接受空气与下界空气，供平炉与大气离心机使用。

**蒸馏水仓 / Distilled Water Hatch**

借助蒸汽纠缠奇点从虚空凝结蒸馏水，放置后自动补充。蒸馏水不会造成结垢，适合太阳能阵列与地热锅炉。

**红石仓 / Redstone Hatch**

可安装在多方块机器上，根据效率、输出、消耗或工作状态等词条输出红石信号，可设置阈值、反向与更新频率。

> 奇点仓四件套已并入「缓存节点与奇点仓室」一节（见单方块节点章节）/ The Singularity Compartments (4) block has moved to "Cache Nodes & Singularity Compartments" (see Single-Block Nodes).

**枢纽存储单元（3种）/ Hub Storage Units (3)**

用于枢纽阵列层叠的存储单元，分枢纽 / 加固枢纽 / 超压枢纽三种。

***

## Items / 物品

- **枢纽终端 / Hub Terminal**：打开缓存或钻井枢纽状态终端，管理节点速率、容量、方向与运行状态。Handheld management of cache and drilling hub networks.
- **蒸汽纠缠奇点 / Steam Entangled Singularity**: Core binding material. Produced by the Steam Singularity Entangler (heat accumulation). Consumed when binding nodes to hubs and in various crafting recipes. / 核心绑定材料，由蒸汽奇点纠缠装置累积热量产出；节点绑定枢纽与多种合成均会消耗。
- **临界蒸汽纠缠奇点 / Critical Steam Entangled Singularity**：由临界纠缠奇点稳定装置产出，用于高级合成与增幅。掉落物会爆炸，请勿丢弃；爆炸会出现普通奇点。Advanced crafting and amplification material; dropped items explode.
- **枢纽奇点芯片 / Hub Singularity Chip**：解锁蒸汽、蓄水枢纽节点绑定并扩充储存，可进入枢纽调试。取下时会损失超过缩减后容量的流体。Unlocks binding and expands hub storage; removing it can discard excess fluid.
- **强化枢纽奇点芯片 / Reinforced Hub Singularity Chip**：供钨钢蒸汽、蓄水枢纽使用；蒸汽枢纽解锁致密与超临界蒸汽，双枢纽解锁超压节点绑定与强化储存。Unlocks advanced steam and overpressure nodes on TungstenSteel hubs.
- **蒸汽轮机循环超限芯片 / Steam Turbine Cycle Overlimit Chip**：供完成额外叠加结构的巨型蒸汽轮机阵列使用，使热蒸汽冷却产生蒸馏水，支持同族蒸汽效率叠加。Enables distilled-water cooling and steam-family efficiency stacking.
- **地热过热芯片 / Geothermal Overheat Chip**: For Large Geothermal Steam Boiler (steel tier) — enables superheated steam output and rare byproducts. / 用于大型地热蒸汽锅炉（钢级）——启用过热蒸汽输出与稀有副产物。
- **稀有气体分离芯片 / Rare Gas Separation Chip**：供大气离心机使用，解锁更多流体输出的气体分离配方。Unlocks additional gas-separation outputs.
- **矿脉裂解器芯片（T1/T2/T3）/ Vein Pyrolyzer Chip (T1/T2/T3)**: For Vein Steam Pyrolyzer — expands underground fluid scan range. / 用于地脉蒸汽热解机——扩大地下流体扫描范围。
- **制氨催化剂（7种变体）/ Ammonia Catalyst (7 variants)**: For Ammonia Plant — determines parallel count and reaction time. 7-tier progression from Nickel to Quantum. / 用于制氨工厂——决定并行数与反应时间，镍至量子共 7 级进阶。

***

## 配方与进阶 / Recipes & Progression

用 NEI 查询当前整合包中的配方与材料需求，按机器说明选择蒸汽、仓室与芯片。工作台负责部分基础设备，组装机承接高级控制器、奇点组件、催化剂与武器。需要操作细节时，请阅读 [玩家详细指南](README/FEATURES.md) 或 [Wiki](https://github.com/MIAOKATZE/GT-Steam-Reborn/wiki)。

***

## BetterQuesting Questline Integration / BetterQuesting 任务线整合

<p align="center"><img src="README/BQ.png" width="1000" alt="任务线「GT 蒸汽重生」任务总览 / Quest line GT Steam Reborn overview: guided quests"><br><em>任务线「GT 蒸汽重生」：84 个引导任务，连线为任务依赖 / Quest line "GT Steam Reborn": 84 guided quests; lines are quest dependencies</em></p>

- **内置引导任务线**：任务覆盖青铜基地建设、蒸汽经济学、枢纽网络到奇点账户的完整进度线，任务标题与描述随游戏语言自动切换（中/英）。/ **Built-in guided questline**: guided quests covering the full progression from bronze-base acceptance and steam economics to hub networks and the singularity account; quest titles and descriptions follow the game language (CN/EN).
- **自动更新**：进入世界自动加载引导任务，模组升级会刷新任务内容并保留完成与领取进度。/ **Automatic updates**: quest definitions refresh on world entry while keeping completion and claim progress.
- **可选依赖**：BetterQuesting 未安装时本整合静默停用，其余功能不受影响。/ **Optional dependency**: with BetterQuesting absent, this integration silently disables and nothing else is affected.

***

## License / 许可证

采用 AGPL-3.0 许可证，详见 LICENSE 文件。

文档更新：2026-10-08 · 当前版本：1.21.1
