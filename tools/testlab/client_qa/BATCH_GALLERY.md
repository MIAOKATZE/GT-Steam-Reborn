# 专用 UI 事件图库

## v2.0.11 紧凑配方与对齐

`prepare_v2011_gallery.py` 准备 `build/v2011-ui-qa/gallery-v2011-compact.json`，不启动客户端。12 个定点包括中文锅炉水/盐、独立辐照、恒星点火/维持/聚变、化学代表混合、建设基础/主体/设备，以及真实恒星冷态终端和英文代表混合。新版 `UiGalleryBatchV2011` 只按真实 `kind/operation` 与匹配配方选择，不调用旧说明页 `page/pages/description`。

验收读取已初始化 GT `modularWindow` 的真实 `SlotWidget` 边框及 cached `PositionedStack`，核对 18×18 槽与 16×16 图标的 1px 内缩，并对每个原生物品/流体栈作坐标命中与对象身份检查。另记录 NEI `RecipeWidget` 的真实 ♥/+ 按钮矩形、真实字体宽度与译文，拒绝连续 ASCII 问号或未翻译键；文字绘制坐标与箭头中线属于有源码 SHA 的声明位置，必须继续用原始截图验证。`analyze_v2011_alignment.py --run <本轮run目录>` 生成独立原图墨迹边缘记录和派生辅助图，不把派生图算成额外实拍。

化学三条 `chemical_window_*` 仅在已加载真实 handler 中只读记录实际流体名、升数及 `baseEu`；恒星冷态仅展示同步状态，不支付点火费用。候选冻结后继续只启动一个 owned hidden desktop 客户端，heap 4 GiB，正常退出；不重跑 v2.0.10 的 28 张说明页。

## v2.0.10 原生伪 NEI

`prepare_nei_v2010_gallery.py` 准备 `build/nei-v2010-qa`，不启动游戏。`UiGalleryBatchV2010` 通过公开 NEI map 入口打开真实 GT handler，再按 `DisplayInfo.kind/operation/page/pages` 选择已注册配方。正常动画与低动效各保留两个自然 draw tick 时间点；同 JVM 通过真实语言管理器和资源重载切换英文。无世界微缩渲染器探针、业务交易或施工。

原生栈核验使用真实 recipe `PositionedStack` 坐标命中实际 NEI slot，并调用原生物品/流体 tooltip 生成链和 NEI `drawPagedTooltip`。这是合成坐标的真实槽与 tooltip 绘制证据，不宣称物理鼠标 hover。R/U 对应公开 recipe/usage 查询使用同一个实际 cached stack；实际 handler ID 保存在收据，不宣称物理按键通过。

生产候选冻结后，只启动一个 owned 客户端，必须附 `--hidden-desktop --heap-gib 4 --reserve-gib 2`；使用既有 `build/client-testlab-mui2-v12138`、全新 run ID 和正常 `finish` 收尾。`UiGalleryBatchV209` 现只保留 C08/history/remote；`prepare_v209_gallery.py` 生成两项 `gallery-v209-history.json`。旧运行归档保留，旧微缩计划不能对新候选执行。

```powershell
python -X utf8 tools/testlab/client_qa/prepare_nei_v2010_gallery.py
python -X utf8 tools/testlab/client_qa/compile.py --workspace build/nei-v2010-qa --client-sandbox build/client-testlab-mui2-v12138
# 收到冻结候选后才运行；本命令不属于准备步骤。
python -X utf8 tools/testlab/client_qa/run.py --workspace build/nei-v2010-qa --client-sandbox build/client-testlab-mui2-v12138 --jar <候选.jar> --mode gallery-batch --plan build/nei-v2010-qa/gallery-nei-v2010.json --run <唯一ID> --language zh_CN --hidden-desktop --heap-gib 4 --reserve-gib 2 --deadline 900
```

## v2.0.8 有限抽样

`prepare_v208_gallery.py` 仅准备 `build/v208-ui-qa/gallery-v208-13.json` 与既有 owned 离线启动描述，不启动游戏。清单只使用 Critical 当前三页（0/1/2）：织机首页、展开账、运行、库存、Sun 运行、narrow 滚动共 6 张；NEI 建设/Sun/化学目录 3 张；真实历史工厂 1 张及真实远程 Sun 1 张；默认/Shift 生产文案的真实字体 renderproof 2 张。旧全量四页清单不作为本轮输入。

`clicks` 只允许 Critical 本地折叠按钮，逐项至少隔两个自然 tick，并记录真实命中链；不接受业务按钮。`rememberHistory` 复用同一个 owned HubTerminal。历史项另放置 live owned Sun，经真实 local Critical 工厂打开并自然记录后，于下一服务器 tick 关闭，再从服务端 `MachineHistoryFactory.open` 打开历史列表。下一张 `actualRemote` 保留该真实历史面板，点击对应原始 index，经生产 Session C2S / RemoteTerminalFactory 创建 Grant。helper 不构造记录或 Grant。收据核对实际 S2C factory 名、双方 windowId/PSM、server grant，以及 client `remote=true` / `getTileEntity()==null`；仅证明同维近处实际远程路由/UI，物理跨维旅行未执行。NEI 通过 `GuiCraftingRecipe.openRecipeGui(RecipeMap.unlocalizedName)` 打开并记录实际 handler；编译依赖 NEI 2.8.145，现有 owned/目标实例为 2.8.155，使用共同公开 API，不替换模组。Tooltip 两张仅调用生产 builder 的默认/结构数组并用实际客户端字体绘制，标题明确标注 `FONT RENDER PROOF`，不声称键盘 Shift 或物品 hover 验收。

```powershell
python -X utf8 tools/testlab/client_qa/prepare_v208_gallery.py
python -X utf8 tools/testlab/client_qa/compile.py --workspace build/v208-ui-qa --client-sandbox build/client-testlab-mui2-v12138
# 生产候选 build 完成后，由主代理显式启动；一次 13 张，heap 上限 5 GiB：
& ./build/v208-ui-qa/run-v208.ps1 -CandidateJar <生产候选绝对路径.jar>
python -X utf8 tools/testlab/client_qa/gallery_report.py --workspace build/v208-ui-qa
```

`preparation.json` 保留准备状态、版本、helper 编译收据和边界；仅编译 helper 不代表已启动或通过截图验收。

停止控制先于启动和同步阶段的所有早退检查：有活动 owned server 时进入 `stage90/request9` 关闭容器并清理 fixture；无活动 server 时直接取消退出，不新建服务器或重开世界。若服务器在 `stage90` 清理完成前意外停止，先写 `FAILED_SERVER_STOPPED`，下一客户端 tick 记录失败并 shutdown；启动/断线故障不作为产品 GUI 或远程路由结论。纯调度合同 `tools/tests/UiGalleryBatchControlContract.java` 覆盖启动阶段、远程等待阶段与正常/异常关闭决策，游戏层仍须后续真实运行核验。

owned helper 在 `serverStarted` 回调中、首次登录前，为实际 `BQThreadedIO.INSTANCE` 连续提交并等待两个有界 barrier：第一个等待任务定义构建，第二个等待构建任务嵌套提交的 PacketSender 分片广播。helper 排在 GTSR/GTIT/GTSWN 注入器之后，并核对 barrier 前后在线人数均为 0、两个 barrier 使用同一真实 BQ worker；等待合计上限 30 秒，无固定 sleep。这样隔离“异步 ALL 广播进行中玩家加入，仅收到尾片”的测试启动窗口，不修改 BQ jar、分片、任务数据或产品；收据 `ownedBqStartupBarrier` 只证明该 QA 调度隔离，不等同 BQ 全包或远程验收。

`gallery-batch` 是显式开启的 owned helper 模式。它不执行 `UiQaScript`、库存交易、付费升级或剧情事务。生产环境不开启 helper 时没有此监听器。

客户端只启动一次，进入 TestLab 后按事件队列批量开窗。服务端开窗返回的 windowId 必须与客户端相等，主 PSM 必须初始化，GTSR 自定义 session 的初始 snapshot/revision 必须就绪；随后至少连续两个正常 RenderTick.END 的面板矩形稳定才调用 Minecraft framebuffer 截图。primitive 值只记录为观察，不把天然递增的 GT 时钟、运行时间等变成功能一致性门禁。MUI1 使用真实 wrapper、匹配 windowId 与 initialized 主窗口。页面允许本地 PagedWidget 选择并明确标注 NO_HITTEST；菜单操作仅白名单本地导航，不形成巨构。单项窗口/数据等待上限 12 秒，失败保留并继续，滚动项额外等待实际动画停止。

```powershell
python -X utf8 tools/testlab/client_qa/gallery_batch_plan.py --workspace build/ui-calibration-v12139 --revision r1
python -X utf8 tools/testlab/client_qa/compile.py --workspace build/ui-calibration-v12139 --client-sandbox build/client-testlab-mui2-v12138
python -X utf8 tools/testlab/client_qa/run.py --workspace build/ui-calibration-v12139 --client-sandbox build/client-testlab-mui2-v12138 --jar build/ui-calibration-v12139/candidate-r3-production.jar --mode gallery-batch --plan build/ui-calibration-v12139/gallery-batch-pilot-r1.json --run gallery-batch-r3 --keep-open --language zh_CN --width 1280 --height 720 --scale 2 --heap-gib 5 --reserve-gib 4 --deadline 2400
```

用户规定后续客户端 Java heap 不得高于 5 GiB，runner 会在创建 run 或启动进程前拒绝超限。保留 4 GiB 余量时可用物理内存门槛为 9 GiB，共享 lease 仍生效。

pilot 完成后写 `batch-job-pilot-receipt.json` 与 `checks-live.json`，`--keep-open` 保持当前 JVM 等待。先人工确认皮肤/布局，再由唯一控制者原子写 run 目录下的 `batch-control.json`：

```json
{"action":"queue","job":"all-r1","plan":"E:/CodeGT/GT-Steam-Reborn/build/ui-calibration-v12139/gallery-batch-all-r1.json"}
```

同一 job ID 只能提交一次；同一 JVM 已成功捕获的同名 case 会跳过。计划必须位于本轮 owned workspace，原始字节与 SHA 会复制至 `batch-plans/<job>.json`。结束用 `{"action":"finish"}`；主动停止用 `{"action":"stop"}`，或创建空 `stop.request`。两者都会先关闭实际服务器容器并按 tile 身份清理本次 fixture，再正常退出，停止不会冒记全量通过。

测量分别记录 JVM 启动到 TestLab ready、job 热批耗时、fixture/sync 等待、自然渲染等待、PNG IO 及客户端事件处理耗时；这些是 CPU wall 观察，不是 GPU benchmark。缓存节点绑定、工具剧情本地 snapshot 均标为视觉 fixture，不能据此声称业务/权限通过。三存储单元与四枢纽仓共七项没有普通玩家开窗入口，清单保留理由，不伪造真实入口。

图库只应选择确认材质正确的候选 SHA。历史错误皮肤 run 与 USER_STOPPED 原始证据继续保留，但不混入最终候选图。审查 HTML 使用内置脚本和相对图片，支持原比例查看、浏览器本地备注与 JSON 反馈导出。

终端还原对照可在 `variant: "terminal"` 的事件上显式加 `legacy: true`。仅支持仍保留生产 GuiScreen 类的 TCDS、三类 Hub、附近缓存节点编辑器、Aggregator 与 Cluster：helper 在服务端对同一 owned tile 调用生产 `assembleSnapshot`，再于客户端调用生产缓存 `accept` 并本地显示原生产 GuiScreen。此路径标记为 `RETAINED_PRODUCTION_CLASS_VISUAL_FIXTURE`，不声明旧玩家入口、windowId 配对、鉴权、槽位交易或业务验收；不发送动作、不点击槽位。旧 Cluster 的 `page` 仅作本地页选择；当前 MUI2 页面优先真实命中本地页签并读回页号，缺少命名页签时才退回本地选择，事件明确区分。Critical 没有保留独立旧屏，不支持此标记。

对照事件记录实际 GuiScreen/类加载器/代码来源、owned 锚点及服务端 snapshot payload SHA。当前终端代表事件也记录该快照观察，便于检查配对是否一致；天然变化字段可能导致 SHA 不同，应按记录解释，不伪称一致。当前生产候选类已载入 JVM 后不能通过热批次更换；新 jar 必须另开一次正常启动，原 JVM 用 `finish` 正常关闭并释放 lease。

`populated: true` 是显式有数据视觉夹具：Hub 在 owned 世界放置真实节点并登记列表；聚合器用真实维度展示物品和 `forceRefreshPool` 生成矿池；Cluster 使用 `addClusterUnit` 登记三段九模块并填统计，保持总控未成型、关机。Cluster 模块的形成/tier 字段仅为图标与状态展示而显式设定，两个 GT 检查/运行计数器均延期；它们不代表物理结构形成，不运行配方。它不证明真实绑定成本、结构成型或生产运行。`tcdsFuelKind: 1/2/3` 仅设置 owned 机器公开显示字段以查看单/双燃料公式，不消耗燃料。每个受控快照同时保存 `.bin` 原始 payload；当前屏的 payload 是开窗时服务端观察，不能宣称与截图帧原子一致。没有显式 `hover` 的事件只把本轮 DrawScreen 事件坐标设为窗口外，避免系统指针偶然停在列表中央遮挡画面；不移动系统指针。
