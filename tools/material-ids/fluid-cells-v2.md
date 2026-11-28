# 旧流体 cell 的离线副本转换 v2

1. 主类仍为 test 源集 `MaterialIdOfflineCopy`，命令仍为 `dry-run|apply|resume SOURCE NEW_TARGET PLAN_JSON REVIEW_JSON`，Gradle 任务 `materialIdOfflineCopy`。生产 jar 不包含迁移器或主动探针。旧 `gtsr.material-copy/v1` 同物品 Damage 转换模式继续保留；跨物品必须选 `gtsr.material-copy/v2`，不得混用 `rules` 与 `fluidRules`。
2. v2 固定转换旧 `gregtech:gt.metaitem.01 / Damage=30000+oldMaterialId` 为 `IC2:itemFluidCell / Damage=0`，写入原生 `tag.Fluid={FluidName:<原注册名>,Amount:1000}`。每个旧单元对应 1000L，保留 Count 的原 NBT 类型、GT IntSlot、AE 长数量/请求/可制作状态和其余 NBT。没有额外空壳产出。已有 `tag.Fluid` 时停止，不覆盖；已转换单元不重复转换。
3. 工具只读所选停服副本，目标必须是独立新目录。保留既有哈希、dry-run 审阅、源变化复核、原子发布、区域原生编解码、INCOMPLETE 中断标记与从源重建的 resume。拒绝未知文件、符号链接、目录重叠、来源变化和不支持的数据。不加载原世界，不调用游戏注册表，不改原 cfg、源存档或目标数字注册表。

## 来源与 30705 歧义

1. 必须提供原 cfg、GT/GTSR 工件、目标 GT/GTSR 工件、实际 AE1104/IC2.828 工件、源/目标原生 `level.dat` 注册表证据和逐位置语义证据，每份由计划绑定 SHA-256。目标 GT 必须为已核 `.277`，AE 为 `rv3-beta-1104-GTNH`，IC2 为 `2.2.828-experimental`。源 GT 只接受 `.265` 或 `.277` 的显式工件证据；工具只解释其原生 NBT cell wire identity，不加载源 GT。
2. `sourceLevelData` 必须是 SOURCE 下的实际 `level.dat`，FML.ItemData 使用 Forge 1.7.10 原生 `K/V` compound 列表，K 前缀 `\u0001` 是 block、`\u0002` 是 item，V 是 int。源、目标完整 FML block/item 映射必须相等。目标单元数字 ID 必须同时存在于这两份表中；不能写字符串 id，也不能套当前实例的数字物品 ID。若需注册表重排，此工具直接拒绝。
3. `level.dat_old` 若存在也核对 FML 映射，不接受不同历史注册表。工件哈希证明输入字节被固定，不证明这些工件或 cfg 确实属于玩家原世界；其历史归属和语义证据仍必须由操作员审阅。
4. `.265` 中705无原生蒸馏水、`.277` 中705是蒸馏水，但仅凭版本、旧 cfg 的 `705=酸` 或最后保存版本，无法证明混用世界的每个30705都是酸。因此 v2 连 `.265` 来源也强制 `sourceHistory=reviewed-per-location`，每个命中旧规则的 cell 必须在 `semanticEvidence` 中逐完整位置列明材料 key，或列明 `native:ic2distilledwater`（仅允许705）。缺少、矛盾或未用到的条目均停止。不会由“没找到来源条目”推断为水。
5. 语义证据是 Java Properties，key 为工具报告中的完整路径，例如 `playerdata/uuid.dat.Inventory[0]`、`region/r.0.0.mca#359.Level.TileEntities[0].mOutputItem0`。Windows 文件分隔符须按实际 dry-run 路径使用并进行 Properties 转义。先用失败诊断定位候选，再由真实备份/逐位置记录证明含义；不得把诊断本身当作语义来源。路径列表没有证据时就拒绝，不能为通过检查临时猜写。
6. `oldMapping` 是原始数字材料映射；v2 的 `newMapping` 是经审阅的 `材料key=实际稳定FluidName` 对照表，而不是重新分配数字 ID 的配置。每条规则都要同时与这两份文件相符。`targetFingerprint` 是准备投入测试的目标 schema 配置指纹，必须来自同一目标配置审阅结果；启动入口还会与当前配置重新比对。

## 明确支持的 wire-layout 适配器

1. MC：标准 short id/short Damage/byte Count 的玩家 Inventory、EnderItems；原生 Chest/Trap/Dropper/Furnace/Hopper 的 Items；Item、MinecartChest、MinecartHopper 实体的原生库存字段；所有所选维度和未加载 Anvil 区域。玩家与实体扩展中的未知 opaque 数据仍拒绝，不能宣称覆盖任意 Mod 实体。
2. GT：`.277 CommonBaseMetaTileEntity` 的 Inventory/IntSlot（显式保留 GTInventory 历史形态适配入口）、GTUtility 的 int Count、`MTEMultiBlockBase` 的已付款待输出 mOutputItemsLength/mOutputItemN。保持进度、EU、活动状态及已审阅常见字段。待输出的原生 FluidName/Amount 数据保持原样。机器私有字段、cover/delegate、配方锁检查器等未审核扩展拒绝；此适配器不是所有 GT 机器的完整存档恢复器。
3. AE：精确 `appliedenergistics2:item.ItemBasicStorageCell.1k/4k/16k/64k` 的 `it` short、`ic` long、`#N` AEItemStack 与 `@N` long；AEItemStack 的 Count=0 byte、Cnt/Req long、Craft byte，保留 ReqMade 等非目标字段。支持原生 Cnt fallback，但 `@N>0` 与 Cnt 不一致、缺失 slot、总数/类型数不符或越界 slot 时拒绝，需要另行审阅，不尝试自动修正。
4. AE：`appliedenergistics2:item.ItemEncodedPattern` 普通/终极样板的 in/out compound 列表；终极 StackType 只接受 item/fluid，空样板槽保留，FluidName 流体栈与 crafting/substitute/beSubstitute 等标志原样保存。物品项按真实长数量替换身份及 Fluid NBT，不经当前注册表重建、不改变数量/顺序/原 Flags。真正的原生配方匹配仍须副本游戏测试。
5. 未审核 AE tile/delegate、AE 扩展物品/磁盘、压缩外置存储、未知 StackType、任意 byte/int opaque 数组和未知机器扩展均 fail closed，首个异常列明位置，不生成成功 dry-run。未扫描的后续位置不可当作已检查。MC 区块结构数组与 FML.BlockedItemIds 是明确的原生结构例外。
6. 适配器是 test 源集中的显式旧格式层，有源码来源说明及删除条件。NBT 和 region 都用 MC 原生编解码；没有新增生产 Mixins、运行时扫描器或全世界 tick 迁移。输入量、深度、文件/节点预算沿 v1；汇总数量及1000L乘积溢出时拒绝。

## 输出、第一次副本测试与验收边界

1. dry-run 保存完整源文件哈希、计划、识别操作及支持边界，目标目录不创建。apply/resume 必须匹配这份审阅报告；所有输出均从不可变源重新生成，经原生 NBT/region 回读再原子发布。最终 marker `.gtsr-material-copy.json` 保存 sourceFiles/outputFiles、数量、逐路径操作、适配器记录、语义条目验证数、保留的原生蒸馏水记录数、targetFingerprint 和 `adaptersVerified=true`。
2. 最终 marker 仅标 `COPY_CONVERTED_REQUIRES_NATIVE_REOPEN`，`worldReady=false`，`nativeGameReopenVerified=false`。这些字段表示有限适配器的离线扫描/转换完成，不表示实机已通过。每个输出文件有哈希，但单文件原子发布不等于整个世界事务。
3. 只有 v2 完成 apply/resume 后才向外部 REVIEW_JSON 根追加 `admissionApproval`，包含 `schema:1`、`world:<规范绝对目标目录>`、`mapping:<targetFingerprint>`、`markerSha256`、`levelSha256`。dry-run 不生成该对象，也不自动安装任何配置。这是第一次打开转换副本的待审核候选；操作员另行审阅后，可将该对象复制至 `config/gtsr/material-copy-approvals/<SHA256(规范绝对世界路径UTF8)>.json`。此配置绑定具体 marker 与 level.dat，用于允许明确的副本测试，不伪造 worldReady。
4. 只有该副本实际注册、打开、GT/AE路径检查、保存、退出、再打开均通过后，才可考虑其游戏可用性。真实玩家原档恢复和替换仍需真实来源与单独指令。当前没有报告者原档；合成夹具不能关闭 Issue #175，也不能证明其世界已恢复。
5. 合同入口仍为 `verifyMaterialIdOfflineCopy`；原 v1 全部合同保留，新增 v2 wire-layout 合同检查背包/末影箱、原生水不转换、无来源/矛盾/Fluid冲突拒绝、GT int批量/已付输出状态守恒、AE超过int的数量/Req/Craft/类型计数、普通/终极样板、未知StackType/opaque拒绝、FML重复数字身份拒绝与内存二次转换幂等。CLI 的源不变、原子复制、跨维度区域和恢复重跑保障继续由 v1 合同检查；不冒称这些合成合同完成了真实 GT/AE 游戏重开。
