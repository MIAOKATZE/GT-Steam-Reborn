<h1 align="center">GT-Steam-Reborn</h1>
<p align="center"><strong><em>GTNH Steam Age Expansion Mod</em></strong></p>
<h1 align="center">GT-Steam-Reborn</h1>
<p align="center"><strong><em>GTNH 蒸汽时代扩展模组</em></strong></p>

A GregTech New Horizons expansion mod that **supplements the Steam Age and significantly expands steam usage**, providing 19 multiblock steam machines, 5 single-block nodes, 13 types of hatches, and a Hub-Node binding system. It fills the gameplay gap between the steam age and the electric age in GTNH, making steam a viable and deep progression path rather than a transient phase.

一个 GregTech New Horizons 扩展模组，**补充蒸汽时代并显著拓展蒸汽用途**，提供19台多方块蒸汽机器、5个单方块节点、13种仓室以及枢纽-节点绑定系统。它填补了 GTNH 蒸汽阶段到电力阶段之间的玩法空白，让蒸汽成为一条可行且有深度的进阶路线，而非过渡阶段。

> [!NOTE]
> This is an unofficial mod. Please avoid discussing this mod in official GTNH forums.
> 这是一个非官方模组，讨论此模组时请注意场合。

## Downloads & Requirements / 下载与版本需求

| GTNH | GTSR | Maintenance / 维护 |
|------|------|:---:|
| 2.9.0 beta-1 | 1.7.+ | 🔜 |
| 2.8.4 | 1.6.0 | ✔️ |

---

## Core Mechanic: Mixin Enhancements / 核心机制：Mixin 增强

GTSR injects 7 Mixin classes into GT5U and GT++ to fundamentally enhance the steam machine experience. These are critical to the mod's functionality:

GTSR 向 GT5U 和 GT++ 注入了 7 个 Mixin 类，从根本上增强了蒸汽机器体验。这些是模组功能的关键：

### MTESteamMultiBaseMixin — Steam Multiblock Core Enhancement / 蒸汽多方块核心增强

Injects into GT++'s `MTESteamMultiBase`, the base class for all steam multiblocks. This is the most important Mixin:

注入 GT++ 的 `MTESteamMultiBase`，所有蒸汽多方块的基类。这是最重要的 Mixin：

- **Superheated Steam 4x Speed**: When any input hatch contains superheated steam, consumption ×4 and processing time ÷4
- **Cooling Hatch Support**: New cooling product distribution — superheated steam → pressure cooling hatch (1:1), normal steam → cooling water (160:1 ratio)
- **Standard Output Bus Compatibility**: Fixes GT5U's `addOutput()` ignoring standard output buses; steam output buses are now dual-registered in both steam and standard lists
- **Dual Steam Type Consumption**: `depleteInput()` can consume from both normal and superheated steam hatches

- **过热蒸汽4倍速**：任意输入仓含过热蒸汽时，消耗×4、处理时间÷4
- **冷却仓支持**：新增冷却产物分配——过热蒸汽→耐压冷却仓(1:1)，普通蒸汽→冷却水(160:1比率)
- **标准输出总线兼容**：修复GT5U的`addOutput()`忽略标准输出总线的问题；蒸汽输出总线现在同时注册到蒸汽列表和标准列表
- **双蒸汽类型消耗**：`depleteInput()`可同时从普通蒸汽和过热蒸汽仓消耗

### Fluid Hatch Compatibility / 流体仓兼容

- **MTEHatchCustomFluidBaseMixin**: When a fluid hatch is locked to any steam type, it accepts ALL steam types (normal/superheated/dense/supercritical). Also adds screwdriver auto-input toggle (2000L/tick from front side).
- **MTEHatchInputMixin**: Adds 4-state orthogonal toggle (input filter × auto-input) to ALL input hatches via screwdriver right-click, plus auto-input at 2000L/tick.
- **MTEHatchInputBusMixin**: Same 4-state toggle for ALL input buses, plus auto-input (1 stack/5 seconds from front side). Shift+click preserves original sort/limit mode.

- **MTEHatchCustomFluidBaseMixin**：流体仓锁定为任意蒸汽类型时，接受所有蒸汽类型（普通/过热/致密/超临界）。还添加螺丝刀自动输入开关（2000L/tick从正面抽取）。
- **MTEHatchInputMixin**：为所有输入仓添加螺丝刀4状态正交切换（输入过滤×自动输入），自动输入2000L/tick。
- **MTEHatchInputBusMixin**：为所有输入总线添加相同的4状态切换，自动输入1组/5秒。Shift+右键保留原版排序/限制模式。

### Steam Bus Behavior / 蒸汽总线行为

- **MTEHatchSteamBusInputMixin**: Enables auto-output for steam input buses (previously blocked by GT++), excluding circuit slot.
- **MTEHatchSteamBusOutputMixin**: Enables auto-output for steam output buses (previously returned `false`).

- **MTEHatchSteamBusInputMixin**：启用蒸汽输入总线的自动输出（之前被GT++阻止），排除电路槽位。
- **MTEHatchSteamBusOutputMixin**：启用蒸汽输出总线的自动输出（之前返回`false`）。

### Recipe Fix / 配方修正

- **MTERockBreakerRecipeBuilderMixin**: Makes glowstone dust non-consumable in the Rock Breaker's Netherrack recipe (circuit 6).

- **MTERockBreakerRecipeBuilderMixin**：使荧石粉在岩石破碎机的地狱岩配方（电路6）中不可消耗。

---

## Hub-Node Binding System / 枢纽-节点绑定系统

The Hub-Node system is GTSR's core innovation, enabling cross-chunk and cross-dimensional fluid transfer and remote operations.

枢纽-节点系统是 GTSR 的核心创新，实现跨区块甚至跨维度的流体传输和远程作业。

### Three Hubs / 三大枢纽

- **Steam Hub Array**: 3-tier (Bronze/Steel/TungstenSteel), accepts steam cache nodes, supports normal/dense/supercritical steam, capacity up to 12.8B L with overpressure storage units. Requires Hub Singularity Chip for node binding. Bidirectional transfer (input/output modes). Supports cross-dimensional transfer.
- **Water Hub Array**: Bronze/Steel tier, accepts water cache nodes, same-dimension only.
- **Singularity Drilling Hub**: Steel only, requires superheated steam (no speed bonus), drives drilling and miner nodes. Steam consumption scales with active node count.

- **蒸汽枢纽阵列**：3级（青铜/钢/钨钢），接受蒸汽缓存节点，支持普通/致密/超临界蒸汽，超压存储单元容量可达12.8B L。需要枢纽奇点芯片绑定节点。双向传输（输入/输出模式）。支持跨维度。
- **水枢纽阵列**：青铜/钢级，接受水缓存节点，仅同维度。
- **奇点钻井枢纽**：仅钢级，必须使用过热蒸汽（无加速效果），驱动钻井和采矿节点。蒸汽消耗随活跃节点数增长。

### Binding Mechanism / 绑定机制

Hold a node item and right-click a hub controller to bind. Singularity cost varies by node type (steam/water: 0, reinforced steam: 1, overpressure steam: 8, miner/driller: 1). Steam/Water hubs support 3-state cycle: output mode → input mode → unbind. Drilling hub supports 2-state: bind → unbind. Nodes auto-register with their hub on first tick.

手持节点物品右键枢纽控制器绑定。奇点消耗因节点类型而异（蒸汽/水：0，强化蒸汽：1，超压蒸汽：8，采矿/钻井：1）。蒸汽/水枢纽支持3状态循环：输出模式→输入模式→解绑。钻井枢纽支持2状态：绑定→解绑。节点在首次tick时自动向枢纽注册。

### Transfer Mechanism / 传输机制

- **Steam/Water Hub**: Every 20 ticks, transfers fluid between hub and bound nodes at configurable rates. Screwdriver on hub toggles overflow output mode. Transfer rate adjustable via chip right-click (100%→80%→60%→...→1%→0%).
- **Drilling Hub**: Consumes steam to drive active nodes. Miner node outputs → hub Output Bus. Drilling node outputs → hub Output Hatch.

- **蒸汽/水枢纽**：每20tick在枢纽与绑定节点间传输流体，速率可配置。螺丝刀切换溢流输出模式。芯片右键调整传输速率百分比。
- **钻井枢纽**：消耗蒸汽驱动活跃节点。采矿节点产出→枢纽输出总线。钻井节点产出→枢纽输出仓。

---

## Multiblock Machines / 多方块机器 (19)

### Hub Machines / 枢纽机器 (3)

| Machine | Tier | Steam Type | Key Feature |
|---------|------|-----------|-------------|
| Steam Hub Array | Bronze/Steel/TungstenSteel | Normal+Dense+Supercritical | Central steam distribution, up to 25 storage units |
| Water Hub Array | Bronze/Steel | Water | Central water distribution, up to 9 storage units |
| Singularity Drilling Hub | Steel only | Superheated only (no speed bonus) | Drives remote drilling/miner nodes |

| 机器 | 等级 | 蒸汽类型 | 核心特性 |
|------|------|---------|---------|
| 蒸汽枢纽阵列 | 青铜/钢/钨钢 | 普通+致密+超临界 | 蒸汽中央分配，最多25个存储单元 |
| 水枢纽阵列 | 青铜/钢 | 水 | 水中央分配，最多9个存储单元 |
| 奇点钻井枢纽 | 仅钢 | 仅过热（无加速） | 驱动远程钻井/采矿节点 |

### Steam Processing Machines / 蒸汽加工机器 (8)

All inherit from `MTESteamMultiBase` (GT++), supporting normal steam and superheated steam 4x speed.

均继承自 `MTESteamMultiBase`（GT++），支持普通蒸汽和过热蒸汽4倍速。

- **Large Steam Furnace**: Bronze/Steel, 4/12 parallel. The most basic steam processing machine.
- **Air Compressor**: Bronze/Steel, 1/4 parallel. Produces air (or nether air in Nether dimension).
- **Atmospheric Centrifuge**: Bronze/Steel, 4/16 parallel. Chip system — basic recipe filters 2 outputs, rare gas chip unlocks up to 8 outputs. Bronze tier cannot install chips.
- **Steam Singularity Compressor**: Steel only, heat-based. Accumulates heat to 100% to produce Steam Entangled Singularities. No parallel.
- **Steam Fluid Drill**: Bronze/Steel. Produces water/distilled water/brine/lava. Screwdriver switches output mode (steel only). Brine/lava modes have 10%/0.5% efficiency.
- **Crust Steam Borer**: Bronze/Steel. Void mining — produces ores based on dimension drop tables.
- **Void Crust Steam Borer**: Steel only. Upgraded version of Crust Borer with higher steam cost.
- **Vein Steam Pyrolyzer**: Bronze/Steel. Extracts underground fluids from oil veins. Chip T1/T2/T3 expands scan range (2×2/4×4/8×8 chunks).

- **大型蒸汽熔炉**：青铜/钢，4/12并行。最基础的蒸汽加工机器。
- **空气压缩机**：青铜/钢，1/4并行。产出空气（下界维度产出下界空气）。
- **大气离心机**：青铜/钢，4/16并行。芯片系统——基础配方过滤2个输出，稀有气体芯片解锁最多8个输出。青铜级不能安装芯片。
- **蒸汽奇点压缩机**：仅钢，热量机制。累积热量至100%产出蒸汽纠缠奇点。无并行。
- **蒸汽流体钻机**：青铜/钢。产水/蒸馏水/盐水/岩浆。螺丝刀切换产出模式（仅钢）。盐水/岩浆模式效率10%/0.5%。
- **地壳蒸汽钻探机**：青铜/钢。虚空采矿——按维度掉落表产出矿石。
- **虚空地壳钻探机**：仅钢。地壳钻探机升级版，蒸汽消耗更高。
- **矿脉蒸汽裂解器**：青铜/钢。提取地下油脉流体。芯片T1/T2/T3扩展扫描范围（2×2/4×4/8×8区块）。

### Enhanced Processing Machines / 强化加工机器 (8)

All inherit from `MTEEnhancedMultiBlockBase` (GT5U), with more advanced mechanics.

均继承自 `MTEEnhancedMultiBlockBase`（GT5U），具有更高级的机制。

- **Large Coke Oven**: Bronze/Steel, 8/16 parallel. Temperature acceleration system — recipe time decreases as heat increases (minimum 800s). Produces coke from coal.
- **Siemens-Martin Furnace**: Steel only, superheated steam, 32 parallel. Temperature-based recipe time reduction. Processes iron + fuel → steel. Carbon dust is fastest, coal is slowest.
- **Large Geothermal Steam Boiler**: Bronze/Steel. Consumes lava to produce steam. Overheat chip (steel only) enables superheated steam output and rare byproduct drops.
- **Mega Steam Turbine Array**: 12-tier. Generates EU from steam. Stacking efficiency — more layers = higher efficiency cap. Supports all steam types with progression.
- **Large Solar Overpressure Array**: 3-tier (Bronze/Steel/Nether). Produces steam from solar energy. Calcification mechanic — efficiency degrades over time, wash with screwdriver. Nether tier produces superheated steam.
- **Kinetic Processing Array**: 12-tier, superheated steam only. Dynamic recipes determined by inserted machines. Processes recipes from any single-block machine placed inside.
- **Gear Steam Compressor**: Bronze/Steel. Converts normal steam → superheated steam + cooling water. Essential for producing superheated steam without electric boilers.
- **Ammonia Plant**: Steel only, 64~256 parallel. Heat-based processing with 7-tier catalyst system (Nickel→Platinum→Uranium→Osmium→FeCo→Ruthenium→Quantum). Higher catalysts = more parallel + faster reaction.

- **大型焦炉**：青铜/钢，8/16并行。温度加速系统——配方时间随热量增加而缩短（最低800秒）。煤炭→焦炭。
- **平炉**：仅钢，过热蒸汽，32并行。温度削减配方时间。铁+燃料→钢。碳粉最快，煤炭最慢。
- **大型地热蒸汽锅炉**：青铜/钢。消耗岩浆产蒸汽。过热芯片（仅钢）启用过热蒸汽输出和稀有副产物。
- **巨型蒸汽轮机阵列**：12级。蒸汽发电。堆叠效率——层数越多效率上限越高。支持所有蒸汽类型进阶。
- **太阳能超压阵列**：3级（青铜/钢/下界）。太阳能产蒸汽。钙化机制——效率随时间降低，螺丝刀清洗。下界级产出过热蒸汽。
- **动能处理阵列**：12级，仅过热蒸汽。由放入的机器决定配方。处理放入的任意单方块机器的配方。
- **齿轮蒸汽压缩机**：青铜/钢。普通蒸汽→过热蒸汽+冷却水。无需电力锅炉即可产出过热蒸汽的关键机器。
- **制氨工厂**：仅钢，64~256并行。热量系统+7级催化剂（镍→铂→铀→锇→铁钴→钌→量子）。更高级催化剂=更多并行+更快反应。

---

## Single-Block Nodes / 单方块节点 (5)

### Cache Nodes / 缓存节点 (4)

Digital tank-based nodes that bind to hubs for cross-chunk/dimensional fluid transfer. Support fluid lock, auto-output, void excess, and chip-adjustable hub transfer rate.

基于数字储罐的节点，绑定枢纽实现跨区块/维度流体传输。支持流体锁定、自动输出、溢出虚空和芯片调整枢纽交互速率。

- **Steam Cache Node**: Accepts normal steam only. Binds to Steam Hub.
- **Reinforced Steam Cache Node**: Accepts normal + superheated steam. Binds to Steam Hub.
- **Overpressure Steam Cache Node**: Accepts ALL steam types. Highest capacity and output rate. Binds to Steam Hub (requires Reinforced Chip on tier 3 hub).
- **Water Cache Node**: Accepts water + distilled water. Binds to Water Hub.

- **蒸汽缓存节点**：仅接受普通蒸汽。绑定蒸汽枢纽。
- **强化蒸汽缓存节点**：接受普通+过热蒸汽。绑定蒸汽枢纽。
- **超压蒸汽缓存节点**：接受所有蒸汽类型。最高容量和输出速率。绑定蒸汽枢纽（需等级3枢纽安装强化芯片）。
- **水缓存节点**：接受水+蒸馏水。绑定水枢纽。

### Remote Worker Nodes / 远程工作节点 (2)

Nodes that perform remote operations driven by the Singularity Drilling Hub. They consume mining pipes to drill downward, then extract resources at bedrock level.

由奇点钻井枢纽驱动执行远程作业的节点。消耗钻管向下钻探，到达基岩后提取资源。

- **Singularity Drilling Node**: Extracts underground fluids. 4-tier upgrade system (Base/I/II/III) using Oil Drill multiblock controllers + singularities. Higher tiers increase extraction coefficient and work range (1×1 to 8×8 chunks). Each chunk is independently extracted and depleted.
- **Singularity Miner Node**: Mines ores. 4-tier upgrade system using Ore Drill multiblock controllers + singularities. Higher tiers increase mining range, fortune level, and speed. Fortune applies to both normal and small ores.

- **奇点钻井节点**：提取地下流体。4级升级体系（基础/I/II/III），使用油气钻井多方块控制器+奇点。更高级别增加提取系数和工作范围（1×1到8×8区块）。每个区块独立提取和衰减。
- **奇点采矿节点**：采矿。4级升级体系，使用矿石钻井多方块控制器+奇点。更高级别增加采矿范围、时运等级和速度。时运对普通矿和贫瘠矿均生效。

---

## Hatches / 仓室 (13)

Specialized hatches for GTSR machines with varying capacities and fluid filters:

GTSR 机器专用仓室，具有不同容量和流体过滤：

- **Steam Input/Output Hatches**: Basic 8K~128K capacity, steam only
- **Steam Cooling Hatch**: 64K, accumulates cooling water (160 steam : 1 water ratio)
- **Pressure Steam Input/Output/Cooling Hatches**: 512K~1M capacity, accepts both normal and superheated steam
- **Hub Input/Output Hatches**: Dynamic capacity (determined by hub controller), delegates fill/drain to hub
- **Overpressure Turbine Input Hatch**: For Mega Steam Turbine Array only, accepts all steam types

- **蒸汽输入/输出仓**：基础8K~128K容量，仅蒸汽
- **蒸汽冷却仓**：64K，累积冷却水（160蒸汽:1水比率）
- **耐压蒸汽输入/输出/冷却仓**：512K~1M容量，接受普通和过热蒸汽
- **枢纽输入/输出仓**：动态容量（由枢纽控制器决定），fill/drain委托给枢纽
- **超压轮机输入仓**：仅巨型蒸汽轮机阵列使用，接受所有蒸汽类型

---

## Items / 物品

- **Steam Entangled Singularity**: Core binding material. Produced by Steam Singularity Compressor (heat accumulation). Consumed when binding nodes to hubs and in various crafting recipes.
- **Hub Singularity Chip**: Required for Steam/Water Hub node binding. Also enables hub debug mode when right-clicked.
- **Reinforced Hub Singularity Chip**: For tier 3 Steam Hub only — enables dense/supercritical steam, ×10 capacity, and overpressure cache node binding.
- **Geothermal Overheat Chip**: For Large Geothermal Steam Boiler (steel tier) — enables superheated steam output and rare byproducts.
- **Rare Gas Separation Chip**: For Atmospheric Centrifuge — unlocks recipes with >2 fluid outputs (up to 8).
- **Vein Pyrolyzer Chip (T1/T2/T3)**: For Vein Steam Pyrolyzer — expands underground fluid scan range.
- **Ammonia Catalyst (7 variants)**: For Ammonia Plant — determines parallel count and reaction time. 7-tier progression from Nickel to Quantum.

- **蒸汽纠缠奇点**：核心绑定材料。蒸汽奇点压缩机产出（热量累积）。绑定节点到枢纽时消耗，也用于多种合成配方。
- **枢纽奇点芯片**：蒸汽/水枢纽绑定节点必需。右键枢纽可启用调试模式。
- **强化枢纽奇点枢纽升级芯片**：仅等级3蒸汽枢纽——启用致密/超临界蒸汽、×10容量和超压缓存节点绑定。
- **地热过热芯片**：大型地热蒸汽锅炉（钢级）——启用过热蒸汽输出和稀有副产物。
- **稀有气体分离芯片**：大气离心机——解锁>2个流体输出的配方（最多8个）。
- **矿脉裂解器芯片（T1/T2/T3）**：矿脉蒸汽裂解器——扩展地下流体扫描范围。
- **制氨催化剂（7种变体）**：制氨工厂——决定并行数和反应时间。7级进阶从镍到量子。

---

## Steam Tier System / 蒸汽等级体系

GTSR introduces a steam progression system that makes steam type matter:

GTSR 引入了蒸汽进阶体系，让蒸汽类型具有实际意义：

| Steam Type | Availability | Effect |
|-----------|-------------|--------|
| Normal Steam | Always available | Base fuel for SteamMultiBase machines |
| Superheated Steam | From Gear Steam Compressor / Geothermal Boiler | 4x speed for SteamMultiBase machines; required for Drilling Hub, Siemens-Martin, Kinetic Array |
| Dense Steam | Steam Hub tier 2+ | Higher energy density in hub system |
| Supercritical Steam | Steam Hub tier 3 + Reinforced Chip | Highest energy density, ×10 hub capacity |

| 蒸汽类型 | 获取方式 | 效果 |
|---------|---------|------|
| 普通蒸汽 | 始终可用 | SteamMultiBase机器的基础燃料 |
| 过热蒸汽 | 齿轮蒸汽压缩机/地热锅炉 | SteamMultiBase机器4倍速；钻井枢纽、平炉、动能阵列必须使用 |
| 致密蒸汽 | 蒸汽枢纽等级2+ | 枢纽系统中更高的能量密度 |
| 超临界蒸汽 | 蒸汽枢纽等级3+强化芯片 | 最高能量密度，枢纽容量×10 |

---

## Recipes / 配方系统

GTSR adds 8 custom RecipeMaps and extensive crafting recipes:

GTSR 添加了8个自定义 RecipeMap 和大量合成配方：

- **Workbench recipes**: Basic machines (Air Compressor, Atmospheric Centrifuge, etc.), cache nodes, hatches
- **Assembler recipes**: Advanced machines (Ammonia Plant, Singularity Compressor, etc.), chips, catalysts, nodes, overpressure components
- **Custom RecipeMaps**: Large Coke Oven, Siemens-Martin Furnace, Ammonia Plant, Air Compressor, Atmospheric Centrifuge, Steam Singularity Compressor, Geothermal Boiler (NEI display), Steam Fluid Drill (NEI display)

- **工作台配方**：基础机器（空气压缩机、大气离心机等）、缓存节点、仓室
- **组装机配方**：高级机器（制氨工厂、奇点压缩机等）、芯片、催化剂、节点、超压组件
- **自定义 RecipeMap**：大型焦炉、平炉、制氨工厂、空气压缩机、大气离心机、蒸汽奇点压缩机、地热锅炉（NEI显示）、蒸汽流体钻井（NEI显示）

---

## Tech Stack / 技术栈

- Java 8 (Jabel) / Minecraft 1.7.10 / Forge 10.13.4.1614
- SpongePowered Mixin (7 mixin classes)
- ModularUI / StructureLib
- Dependencies: GT5U, GT++, Bartworks, EFR, Railcraft, BuildCraft

## License / 许可证

See LICENSE file.
详见 LICENSE 文件。
