# GTSR 模块化视觉资产管线

这里是 GTSR 侧的来源、确定性导出、资源闭包、纯合同和候选交接入口。需要 Python 3.10+；只有像素比较和相应测试使用已有的 Pillow。不另建编辑器或 Core 引擎，也不写 GTUDK/Outpost。GTUDK 当前用于效果审阅，PNG/CFR 出口不充当游戏资产或实机回执。

生产定义来自 `src/main/resources/assets/gtsr/machinevisual/motions.json`，Java 的 `MachineVisualDefinition` 与工具读取同一份数据。`motions.schema.json` 描述格式；`motion.py` 另验有限数、预算、组/成员唯一性、真实 token 材质、单位方块、所选五机和授权行程。只接受声明的字段、动作与轴，没有脚本/回调/类名执行入口。定义坐标为 X 右、Y 上、Z 后；raw shape 的映射为 `(x-h, v-layer, row-d)`，世界控制器中心的 `+0.5` 仅由后端应用一次。平移单位为 block；Core rotation degrees 在 Java sample 中显式转 radians；时间为整数毫秒，平移两段 smoothstep，旋转线性 wrap。

从任务包的批准源导出，再审计当前资源：

```powershell
$task03Sources = 'E:/CodeGT/TASK03-GTSR-20261101'
$r5Sources = 'E:/CodeGT/GT-Steam-Reborn/plan/临时计划/临界机器设计计划v2'
python tools/workvisual/pipeline.py export-motions --models "$task03Sources/visual-reference/runtime/models.json" --out build/workvisual-task03/staged/motions.json
python tools/workvisual/pipeline.py audit --task-package $task03Sources --r5-root $r5Sources --out build/workvisual-task03/source-audit
python tools/workvisual/pipeline.py export-r5 --source-root $r5Sources --out build/workvisual-task03/r5-export
python tools/workeffects/export_effects.py --check
python tools/workvisual/test_pipeline.py
```

导出写 staging，不改生产资产。新五机导出按当前实际源与批准 profile 补全 block/meta/tier；Air 的 12 个侧玻璃专门覆盖源里旧 rawLayers。`audit` 将 staged 定义与生产定义逐字段对比。R5 入口复用已批准源哈希、levitation JSON envelope、材质前缀与原导入器编码，14 个结果逐份复现记录 SHA；不执行源 JavaScript。它只重建 revision31，后续供给舱 revision32/33 保持已有修订入口，不能把 R5 基座冒充当前基座。

`audit` 的输出分开记账：

- `runtime-manifest.json`：从实际 schema2 palette、51 等**实际发现的** geometry、隐式 legacy sprite、新运动与效果资源递归闭合；PNG 与 mcmeta 分别记字节哈希。数量由输入发现，不把旧 33/1186/371 路径断言沿用到新树。已有 geometry/冻结件与施工顺序按原字节保留。`v2_v30_base_t3` 的旧 constructionOrder 与 blocks 不是完整排列，单列历史警告并保留字节，不改旧布局。
- `authoring-manifest.json`：任务包、实际 Java shape/材质源、工具、R5 原作者与 override 独立来源哈希；plan 仓与 Mod 仓身份分开。根 LICENSE 与 resources/LICENSE 的差异记录为待 owner 签收的元数据缺口，不推断素材法律归属。
- `scope-audit.json`：最终矩阵、16 控制器处置、82 分类与当前 MachineLoader 的 class/enum/internalName 精确集合比较；同时记录相对 ID。KPA/Mega 排除不是欠缺动画，四个已删测试工具只登记，不恢复。
- `continuous-sweep.json`：平移全程 swept AABB 与同相组相对路径；旋转为单位方块全圆径向充分证明。面接触、无正体积交叠、正机械公差分开；列出实际世界仍须检查的空位，Crust 按 0.60 重新算，未沿用 0.30。
- `pose-oracle.json` / `required-pose-cases.json`：五机 × 六相位 × 四向 × 合法水平镜像 × OFF/STANDARD，共 480 个必需 case，保存全尺寸八角点。`scalar-oracle.json` / `required-scalar-cases.json` 保存 42 个实际组时刻样本请求。
- `effects-contract.json`：原 38 加新 3 个排汽锚点、稳定 ID 归属、ABC 的 B 向下和上方空位转换、194 个真实火箱单元及 foreground 字节身份。源 token/孔像素证据不签工作状态、可见面或客户端 tick。
- `validation.json`：以上回执的精确哈希；失败会覆盖该状态文件为 FAIL，不能保留上次 PASS。当输入来自 JAR 时使用 `--jar <明确候选>`，读取 JAR 内资源而非 source overlay。

历史校验仅有一个明确限定：`v2_v31_base_t3`、`v2_v32_base_t3` 两份冻结 geometry 的旧清单保留源树 CRLF 哈希，而已发布 v2.1.1 和干净 v2.1.2 JAR 使用相同 LF 字节。工具只为这两条路径与已核实的四个完整 SHA 接受精确 CRLF↔LF 字节变体，逐 binding 输出 `NORMALIZATION_MATCH`、原 expected SHA 和实际 raw SHA；runtime manifest 始终记录真实 JAR 字节。它不重写资产/元数据、不重排施工索引，也不接受任意 JSON 语义相等、额外空白、混合换行、成员顺序变化或其他路径。负例验证这些变更仍 FAIL。已发布旧 runtime 与 tagged runtime 的 816 份 critical 文件逐字节相等是独立证据，ZIP 空目录项另计，不能把工作树 CRLF 当模型内容变化。

真实 Java 数值对照使用 `MotionOracleProbe.java`：以候选 dev JAR、Core 099 dev JAR 和目标依赖 classpath 编译运行，入参为 `scalar-oracle.json output.json candidate-dev.jar core-dev.jar`。它忽略请求里的 expected values，实际调用生产 `Group.sample`，重复采样不得推进；校验生产定义类与 Core Sampler 的 CodeSource 都来自显式 JAR，输出完整定义 SHA。随后运行：

可用 `probe_sampler.py --java <java.exe> --javac <javac.exe> --product <candidate-dev.jar> --core <core-dev.jar> --classpath-file <已导出compileClasspath.txt> --requests <scalar-oracle.json> --out <独立目录>` 完成编译/执行并保存精确命令、原始 stdout/stderr、输入哈希和 Java8 字节码回执。runner 将显式 product/Core 放在依赖前面，移除其他 product/Core 路径及纯构建 javac 插件；后者即使 `-proc:none` 也会被某些 javac 自动发现，不属于此纯 probe 的运行依赖。

```powershell
python tools/workvisual/pipeline.py compare-numbers --required build/workvisual-task03/source-audit/required-scalar-cases.json --oracle build/workvisual-task03/source-audit/scalar-oracle.json --actual build/workvisual-task03/java-scalars.json --out build/workvisual-task03/scalar-comparison.json
```

严格图片比较用 `compare-images --required <case文件> --baseline <旧清单> --candidate <新清单> --baseline-jar <旧jar> --candidate-jar <新jar> --out <回执>`。必需文件格式为 `{"schema":1,"cases":["精确caseId"]}`，不得为空/重复。两份图片清单必须为 `schema:1,status:PASS,artifactSha256,codeSourceSha256,cases:[{id,file,sha256}]`；两侧 case 全集与必需集合必须完全相等，文件在各自清单目录下。PNG 维度与像素样本非零，RGBA 每通道逐像素严格比，包含相同 alpha 下的 RGB 差异。报告读取 status，不只看子进程 exit 0。浏览器、FBO 与实机的采证性质由原 harness 说明，工具不替它升级结论。

候选闭环输入为明确 runtime/dev/sources JAR、上述 validation 与依赖锁。锁格式为 `{"schema":1,"dependencies":[{"id":"core_runtime","coordinate":"明确坐标","path":"实际文件","sha256":"完整哈希"}]}`；相对 path 以锁文件目录为根，不允许逃逸。`candidate` 回读 runtime/dev 中闭包的每个字节，记录三制品、依赖、源码 HEAD/dirty 和可选 `--receipt` 的哈希。附属回执按 kind 标为 `BOUND` 或 `UNBOUND`：数值探针/比较须匹配 motions 定义、GTSR dev CodeSource 与依赖锁 `core_dev`；图片比较须匹配 runtime、runtime CodeSource 与 `core_runtime`（图片清单提供 `coreCodeSourceSha256`）。缺少身份、身份不符或未知 kind 保留为 `UNBOUND` 参考，不计为候选验收证据。`BOUND` 仅表示声明身份相符，不证明回执执行过程。所有游戏门保持 NOT_RUN；候选身份 PASS 不表示完成游戏验收、发布或部署。资源闭包命令仍允许 resource-only fixture，报告明确 `artifactScope=resource_closure_only`，不把 ZIP 合法性当作可运行制品验证。

```powershell
python tools/workvisual/pipeline.py candidate --validation build/workvisual-task03/source-audit/validation.json --jar <runtime.jar> --dev <dev.jar> --sources <sources.jar> --dependency-lock <dependency-lock.json> --receipt build/workvisual-task03/scalar-comparison.json --out build/workvisual-task03/candidate.json
```

当前默认依赖与运行最低版本为 Core `0.2.3`，正式坐标由 `repositories.gradle` 的公共 Maven 提供。历史 `v0.2.0-git.8+099f028d1a` 只用于回放旧证据，不与普通 `0.2.0` 混用。离线构建仅显式覆盖 `-PmiaokatzeCoreRepository=<本地镜像>`，每轮依赖锁应记录实际 runtime/dev 文件和哈希；基线保持 GTNH 2.9.0 正式版 / GT5U .265。

普通机运动采用独立的客户端成员/组保留预算，不能拿旧临界 FX claim 数或这些额度当整帧/GPU/百机性能保证：

|质量档|成员额度|组额度|
|---|---:|---:|
|OFF|0|0|
|LOW|128|16|
|STANDARD|512|64|
|HIGH|1024|128|

成员按完整单位方块计，组按当前合格显示组计；不拆开组来凑额度。所选五机完整集合为 69 成员/7 组。每次新获准运动的宿主取得 **120 个客户端 tick 的预算轮转租期**；有竞争时到期退役，无竞争时可以继续保持，等待者按此前获额时间轮转。这个租期与快照新鲜度 lease 分开，不是业务/付款 tick 或固定墙钟时长。

活跃和恢复中的 `mask | restoringMask` 一起占额，包括已离开宿主表但仍待恢复的显示。退役、OFF 或降档不会提前释放额度。r3 有两个明确完成路径：同 `maskEpoch` 且 `hidden=false` 的真实静态上传确认；或同 world/manager/section 身份的原生区域删除、GPU buffer 退休确认。只从当前 section registry 找不到条目，不足以释放曾绑定 GPU section 的旧显示；未绑定过 GPU section 的真实缺席路径另行处理。恢复期间保留原位显示，待恢复额仍算在 held 中，所以降档过渡可能暂存高于新档上限的旧保留额，不能在它上面再扩录取。

r3 对待恢复的精确 section 绑定 world、manager、section 和 maskEpoch，每个前进的客户端 tick 最多将两个请求提升到原生 important 重建队列。入队、计时到期、修改本地 hidden 标志均不充当 actor ACK。该实现针对 r2 真实旧站恢复额度长驻路径修正；r3 的上传/删除回调是否实际闭合必须由 r3 客户端验证，不能由源码静态阅读签收。

`probe_budget.py`/`MotionBudgetContract.java` 的 `memberLimit`、`groupLimit`、`selectAdmissions`、`restorationAcknowledged` 测试使用**精确生产源码方法**；r2 与 r3 各自回执位于 `build/task03-budget-source-r2/receipt.json`、`build/task03-budget-source-r3/receipt.json`，均为 412 checks PASS，抽取方法 wrapper 相同。它们未加载候选 JAR、FML 或 GL，不能证明 actor 发出真实静态上传/GPU 退休 ACK，也不证明 100 台实机性能。候选绑定中将当前版本的 source-method 回执单列，旧回执不冒充新客户端恢复验收。

foreground 在 reload 时分别严格比对选定 PNG、GT 铜和钢底图 SHA，默认 `firebox_grate.png` 保持原字节。`probe_foreground_guard.py --jar <候选runtime.jar> --gt <GT265.jar> --java <java.exe> --javac <javac.exe> --out <独立目录>` 从生产源码抽取 reload/hash 方法及首条 render guard，使用候选/GT 原 PNG 和内存资源 API 验证 14 个必需 case。坏哈希、缺失/空 foreground 被拒绝且不改变原生底图标志；正确资源重新加载可恢复。首条 guard 只加哨兵以观察是否越过，未调用实际 GL renderer，没有装资源包、加载 FML 或验证 GPU ACK。r3 回执为 `build/workvisual-task03/r3-foreground-guard-source/receipt.json`，该源码合同与实机资源重载/外观结果分开。

纹理/atlas/context reload、普通机器安装代际、旧临界 geometry 缓存各有生产生命周期。这里的库存审计不声称旧临界 geometry 支持任意 F3+T 热改。完整客户端/专服、状态与包、业务 ON/OFF 等价、旧新 Air/Atmos shape/hatch/存档、遮挡、生命周期、GTNL 组合与帧时/内存预算按精确候选分别签收。r3 的普通业务 class 字节未变，motion JSON 和语言文件进行了 LF 规范化；JSON 解析相等不等于资源哈希相等，所以 manifest 和实际采样必须重新绑定 r3。r1/r2 客户端结果不转签 r3，当前 r3 客户端由主任务另验。Atmos 材料极小值理论 8 受实际原生仓口上限限制，可达值为 125，原生合同标为 UNSUPPORTED，不能把它改写为结构测试通过。没有把 HTML、源码方法或纯数学 PASS 当实机通过。

逐机接入页见 `onboarding/`；下一台应先更新受审 profile/源与明确 scope，再复用相同合同和工具，不复制 renderer 或 CriticalMachineState。
