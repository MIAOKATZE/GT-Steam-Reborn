# 材料 ID 预检与离线副本工具

1. 本轮没有读取、转换或覆盖 Issue #175 报告者的原始配置/存档。报告者的原映射、原注册环境与原始存档尚未提供；合成夹具通过不能关闭其迁移问题。工具输出始终 `worldReady=false`，没有 GT/AE 打开、保存、再打开证据时不可宣称可以开服。
2. 启动预检在工业材料与六气外观对象创建前，按实际账本核对 42 个活跃粉体和 63 个退休液气数字身份；64 种流体本体独立按名称注册，深渊执念本来就没有上述持久数字 ID。新环境只分配 42 个粉体 ID。完整旧映射的合法粉体原号保留，退休液气仅在当前目标槽位未占用时保留旧 cell 别名；例如 `.277` 的 705 归原生蒸馏水，旧酸 705 仅列为冲突证据，不注册歧义别名，也不因此阻断独立酸流体注册。粉体占用、重复键/ID、非数字、越界、缺键、名称冲突、来源/完整性指纹不符和空间不足仍拒绝。
3. 新旧配置均不存在时，自动生成独立 `industrial-material-ids-v2.cfg`，不需要 JVM 初始化参数；该映射持续标记为 `generated-unbound`，重复启动不会把它变成旧档来源证明。世界加载前另行只读检查所选目标目录及元数据：明确不存在或为空的新目标自动放行；已有世界需匹配配置侧绑定、已审核副本准入，或完整且无退休冲突的旧映射。来源不明或有旧 cell 冲突的世界会在加载前拒绝；禁止删除 cfg 猜号，旧 JVM 参数也不能跳过保护。首次成功启动后的绑定只写配置目录，不向原 `level.dat` 写迁移标记。
4. 原 `industrial-material-ids.cfg` 始终作为只读来源保留，不自动改写或删除。完整 105 项旧格式可导入为 42 活跃粉体和 63 退休液气记录；缺少历史 key 的旧格式文件无法证明缺的是新增材料还是丢失记录，因此拒绝猜号。已有 v2 文件的活跃/退休记录和账本必须完整匹配；后续旧 cfg 与其记录的源 SHA-256 不符时拒绝覆盖或静默重导入。
5. v2 在独立文件中用 `active:`、`retired:`、`alias:` 区分数字记录，并以 `# gtsr-provenance ` 注释保存 schema、origin、源 cfg SHA-256、GT 版本、账本和映射指纹及独立的完整来源记录指纹。`legacy-import` 只证明已读到完整旧映射，不证明任何玩家档来自当前 GT 版本。联机在 FML 初始 REGISTER 阶段核对 schema、账本、粉体和旧别名的完整 SHA-256；该映射指纹不含本机来源状态/源文件哈希。两端不一致或对端缺少 v2 校验即拒绝握手，须保留各自原配置、准备服务端匹配映射并重启后再连接，不热改已生成的材料表，也不自动覆写客户端配置。
6. v2 发布复用 Core `AtomicFilePublisher.publishAtomic`。兄弟文件 `.registration.lock` 是可复用协调锁，发布前再核对 v2 原字节，并在发布前后复核只读旧 cfg；成功持久化前不公开或应用数值身份。该锁协调遵守同一协议的注册器，不能对抗无视锁的外部进程恰在核对和原子替换之间写文件；停服、停止外部编辑后再注册。不得把单文件原子发布描述为整档事务。

## v1 支持范围

1. 下文旧模式为 `gtsr.material-copy/v1`；新增跨物品流体转换与 GT/AE 有限适配见 [v2 副本转换](fluid-cells-v2.md)，不能用 v1 的仅改 Damage 模式转换冲突酸 cell。

1. 独立 CLI 在 test 源集，主类 `com.miaokatze.gtsr.common.dimension.prosperity.industrial.MaterialIdOfflineCopy`，不打入普通运行包，不在 tick 中执行，不加载 World、区块实体、玩家或实际物品注册表。
2. 只接受操作员明确声明 `reviewed-offline-copy` 的脱机副本；源只读打开，目标必须为新目录，拒绝目录重叠、源/目标别名和符号链接。`session.lock` 的 OS 共享锁只协调合作工具：Minecraft 1.7 的该文件是时间戳文件，不能据它证明游戏已经停服。操作员必须先停服并制作与运行实例分离的副本，工具不会尝试关闭游戏或猜测实例目录。
3. 受支持文件为 MC 原生 gzip/raw `.dat`、`.dat_old`、`.nbt` 和 Anvil `.mca`；区域输入仅用 `RandomAccessFile(...,"r")` 读取，gzip/zlib 解压后的 NBT 由 MC `CompressedStreamTools`/`NBTSizeTracker` 解析。`RegionFile` 的读写接口仅作用于新输出的临时区域副本，不接触源文件。
4. 当前只审计了 exact GT `5.09.54.277` 工件 `88a0ac96a01e11fa04f738784722ecf27d4c72488973ff41eeb1399a167801e7` 的源/目标编码，且只转换精确注册物品 `gregtech:gt.metaitem.01` 中经审核的 prefixIndex/prefix 对。索引 0/1/2 为 dustTiny/dustSmall/dust，30/31 为 cell/cellPlasma。只改 `Damage=prefixIndex*1000+oldId`；保留 `Count` 与全部其他 NBT。自定义区 `Damage>=32000` 不改，其他物品相同 Damage 不改。发现旧材料出现在未审核的 GT item/prefix 时拒绝。
5. 数字 `id` 必须由输入的完整源注册表证据解析到精确 registryName；不能用当前游戏注册表反推旧数字 ID。JSON 中的数字和名称规则只是经审核证据的使用接口，不能独立证明该证据确实属于某玩家原档。证据文件必须来自其原注册环境，并由操作员审核。
6. 目标占用证据必须包含 `.277` 的全部 780 个原生槽，以及目标环境其他提供者的实际占用。附带 `gt-277-native-slots.properties` 从上述 exact jar 的 `MaterialsIDMap` 构造器字节提取；value 是静态字段身份，部分与实际 `mName` 不同。例如 slot705 字段为 `DistilledWater`、实际名为 `ic2distilledwater`。附带表只证明原生占用，不能代替其他 Mod 的完整目标环境证据。工具拒绝目标完整映射中的重复/越界 ID、原生占用，以及输入证据中任何已占用 ID。
7. 无 Count/Damage 的 id compound 只接受已查 MC 编码的 Chest/Trap/Dropper/Furnace/Hopper 原生库存载体，并限制其字段为原生位置、Items、名称及工作计时；未知 id、原生载体附带的未知 primitive 字段、所有 GT 机器委托/实体、未审核的实体载体都会拒绝。GT 机器批次/配置恢复尚无适配器，不在当前转换支持范围。AE 物品存储、压缩/未知 byte/int 数组、嵌套未知列表、未审核物品编码、未知文件和未知区域压缩均 fail closed；不会把 AE 数据当作已经覆盖。只豁免 MC 原生区域 `Level.Sections[*]` 的 block/light 数组及 `Level` 的 Biomes/HeightMap。这通常会阻断含第三方压缩/自定义数据的真实整档，是当前明确限制。
8. 工具目前遇到首个不支持编码就中止，异常给出文件/路径；该文件之后的位置未被证明已检查。它不是任意 Mod 存储的完整格式探测器。失败不产生可供 apply 使用的成功 dry-run；输出若已创建则保留 `INCOMPLETE`。
9. 源文件单份上限 256MiB、NBT 计量上限 16MiB、NBT 深度上限64、整次节点数上限400万、文件数上限10万。超限拒绝，不跳过。区域只接受1(gzip)/2(zlib)，检查长度和扇区重叠。
10. 不支持其他 GT 原版本、其他 MetaItem 类、AE 和未知第三方编码。确认原环境后必须补齐并审阅对应原生适配器和回归，不能删除不支持文件来制造“完整迁移”结论。流体按原注册名称保留，本工具不转换流体身份。

## 调用与审核

1. CLI 的独立 JavaExec 任务为 `materialIdOfflineCopy`（不挂 `check`），沿项目定向合同使用 patchedMc + test.runtimeClasspath + main.compileClasspath + portableRecipeContractRuntime。参数固定为 `dry-run|apply|resume SOURCE NEW_TARGET PLAN_JSON REVIEW_JSON`。所有路径建议绝对路径；REVIEW_JSON 必须位于源/目标之外。
2. `dry-run` 不创建目标目录，只在所选 REVIEW_JSON 保存文件清单、哈希、计划、全部已识别操作位置、物品数量和支持边界。它读取所有所选副本文件。没有成功审核报告不得 apply。
3. `apply` 要求报告中的源/目标绝对路径、计划 SHA-256、全文件 SHA-256 清单完全匹配；目标必须不存在。每个输出用临时文件、回读校验和 Core 原子发布；每次记录保留数量与非 Damage NBT。全部转换并复核源与证据后，marker 状态仅改为 `COPY_CONVERTED_REQUIRES_NATIVE_REOPEN`。
4. `resume` 只接受同一源、计划、文件哈希和 `INCOMPLETE` marker。每份输出都重新从原输入生成，因此不会再次变换已经生成的输出。未知额外文件、符号链接、已转换源、成功 marker 都拒绝重跑；若中断留下无法归属的临时文件，应先人工审核该未完成输出再决定保留/重建，工具不自动删它们。
5. 新 cfg 输入单独保留在计划的 `newMapping` 路径，工具不会覆盖 cfg，也不会自动部署它。原 cfg、源存档及所有证据保持原位。只有另行取得本地 native 注册、打开/保存/再打开、机器付费批次恢复及实际存储覆盖证明后，才可评估副本；替换真实玩家原档仍需要明确目标和单独指令。
6. 完整计划示例见 `plan.example.json`。该示例的哈希均为占位值，工具会拒绝；不能拿它直接处理玩家存档。规则必须穷尽发生变化的材料及其实际物品形态。数字 registry 证据为 Java Properties 的 `旧数字物品ID=registryName`，冒号按 Properties 转义；占用证据为 `materialID=occupant`。

## 验证

1. `verifyMaterialIdSafety` 接主类 `MaterialIdPreflightContracts`：保留精确 `.277` 工件/原生 table 和历史 105 项格式的基础合同，另按真实账本验证 v2 的 42 活跃粉体/63 退休液气、新装自动分配且持续未绑定、完整旧映射粉体原号、原生蒸馏水 705 与旧酸别名隔离、退休冲突不重分配粉体、来源/映射指纹、损坏与缺键拒绝、不可变启动身份、只读原 cfg 和独立原子发布。还覆盖历史重复/越界/名称/空位耗尽、注入写失败和目标变化拒绝；夹具不读玩家原档。
2. `verifyMaterialIdOfflineCopy` 应接主类 `MaterialIdOfflineCopyContracts`：合成 raw/gzip NBT、多种 prefix、相同 Damage 非目标物品、GT 自定义区、Count/NBT守恒、两个维度的未加载区域、dry-run/apply源字节不变、中断恢复与重跑拒绝、AE/未知 primitive 容器/GT 机器委托/原生载体未知扩展/压缩/超深/格式错误拒绝。
3. `python tools/material-ids/verify_native_id_evidence.py --gt-jar <exact277-dev.jar>` 只读核对材料占用快照与 exact `.277` 构造器字节。不访问实例、cfg、玩家档或 Gradle输出。
4. 本轮未取得玩家原档或真正 AE 原始数据，也不启动实机。所有迁移证据止于合成夹具；尚未完成该玩家旧档迁移。
