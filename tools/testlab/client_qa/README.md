# MUI2 客户端 QA 使用说明

此工具把生产候选 jar 装入独立的 owned 客户端沙箱，启动真实 Minecraft GL 客户端、集成服务端与玩家容器，并通过真实 MUI2 控件树执行脚本。它不修改生产 jar，也不复制来源实例的存档或账户。

脚本输入由进程内合成，经 ModularScreen hit-test/focus 和 lwjgl3ify `injectTextEvent` 送入；这不代表物理鼠标、键盘或 IME 验收。客户端与集成服务端使用 Forge `LocalChannel`，不能据此宣称 TCP 或远程专用服务器网络已验证。

以下命令均从仓库根目录运行。

原生区块票据同存档重开使用独立 `--mode ticket-restart`，不执行 UI 套件。其同 JVM 同 save、6 GiB heap + 4 GiB 余量、全局 lease、真实票据事件顺序及完整巨构未测边界见 [TICKET_RESTART.md](TICKET_RESTART.md)。

## 准备隔离环境

`prepare.py` 读取 Prism 实例根目录、生产混淆 jar 与本地 Prism cache，准备独立客户端沙箱，但不启动游戏。沙箱必须是仓库 `build/` 下全新的直接子目录，名称以 `client-testlab-` 开头。来源实例不会复制存档或账户；不要把当前运行的用户实例目录当作写入目标。

```powershell
$workspace = "build/mui2-client-qa-r8"
$clientSandbox = "build/client-testlab-mui2-r8"
$jar = "build/mui2-v12138/migration-candidate-r8.jar"

python -X utf8 tools/testlab/client_qa/prepare.py `
  --source "<Prism 实例根目录>" `
  --jar $jar `
  --workspace $workspace `
  --client-sandbox $clientSandbox

python -X utf8 tools/testlab/client_qa/compile.py `
  --workspace $workspace `
  --client-sandbox $clientSandbox
```

每一步都显式传同一组 `--workspace` 与 `--client-sandbox`：两者必须是仓库 `build/` 下的直接子目录。换候选 jar 时更新 `$jar`，并为本次准备选择新的沙箱名；`prepare.py` 不会覆盖已存在的沙箱。`compile.py` 只编译并安装 QA helper jar 到沙箱，不改生产 jar；存在共享 lease 时它会拒绝替换 helper。

## 组合并运行计划

`merge_plans.py` 接受一个输出路径和一个或多个计划片段，冻结合并 plan、片段 SHA、group 区间与 manifest。输出 plan 或 manifest 已存在时会拒绝覆盖。manifest 状态为 `PREPARED_NOT_RUN`，只表示计划已冻结，不代表已执行。

```powershell
python -X utf8 tools/testlab/client_qa/merge_plans.py `
  --output "$workspace/plan-r8.json" `
  tools/testlab/client_qa/plans/small-canvas.json `
  tools/testlab/client_qa/plans/inventory.json `
  tools/testlab/client_qa/plans/hubs-workers.json

python -X utf8 tools/testlab/client_qa/run.py `
  --workspace $workspace `
  --client-sandbox $clientSandbox `
  --jar $jar `
  --plan "$workspace/plan-r8.json" `
  --run r8-zh-small `
  --language zh_CN `
  --width 427 --height 240 --scale 2 `
  --render-distance 3 `
  --heap-gib 5 --reserve-gib 4 `
  --deadline 1800
```

实际验收时按目标窗口与计划调整语言、分辨率、scale 和 run 名。runner 默认预留 4 GiB；`--reserve-gib` 最低 1 GiB。推荐 `--heap-gib 5 --reserve-gib 4`，为 6 GiB Java heap 预留客户端/native 开销，启动前要求至少 10 GiB 可用，低于门槛时只写 preflight 并拒绝启动。R8 的一次 6 GiB 配置观测到 RSS 峰值 9.45 GiB、private bytes 10.67 GiB；heap 不代表进程总物理占用，也不能跨不同 heap 配置直接比较。

一次只运行一项 owned client QA。runner 建立共享 `.gtsr-native-server.lease`，超时处理只会终止它自己启动的客户端子进程；不要手动结束其它实例或 launcher 进程。

## 脚本输入与屏幕记录

MUI2 输入经真实 screen hit-test/focus；文本事件使用 `lwjgl3ify injectTextEvent`。legacy text lane 使用实际 `GuiScreen.keyTyped` 字符链：R8 的目标 TextField 输入检查未通过，R9 有一组旧屏组合输入检查未通过，R10 的旧 wire 24680 与新权威值对照通过；这些分项不等于物理键盘输入验收。物理 OS/IME 输入仍是 `NOT_RUN`。

原生滚动计划使用 `scroll` 操作的 `widget`、`notches`（1–100）及 `direction`（`UP`/`DOWN`）。MUI2 的 `amount` 幅度不能当作滚动距离；操作后需等 15 ticks 让动画完成。

拖拽操作 `drag` 用相对可见区域的分数坐标（0≤坐标<1），脚本会发送真实 screen press、5 次 `onMouseDrag` 和 release。例如：

```json
{"op":"drag","widget":"cluster_viewport","fromX":0.1,"fromY":0.991,"toX":0.97,"toY":0.991}
```

R8 已用该路径拖动 Cluster 横向 scrollbar 并验证 `scrollX>0`。几何记录使用 `geometry` 操作，例如：

```json
{"op":"geometry","widget":"journal.paragraphs","name":"p6.journal.paragraphs.area","depth":3}
```

它记录 widget/parent 的 `x/y/rx/ry/width/height`、指定深度的子树及 scroll 尺寸。R11 已在 Journal/祭坛文字 focused run 中执行 geometry/textMetrics 采证：native 字体测高与 MCFontRenderer 换行高度一致，starter/giant/small-bottom 各为18/27/36高；normal 正文区355×185、内容高170，small 正文区280×113、内容高179、scroll66，末段可见且不重叠。截图人工复核通过。该结果只关闭对应的文字与布局门，不代表完整工具/剧情计划的所有几何断言都运行。

## 查看结果

每个 run 独立写入 `$workspace/client-runs/<run>/`，包含 preflight、launch、pid、stdout、checks、receipt、内存采样、计划副本和截图。`receipt.status=PASS` 要求进程 exit 0、checks 为 PASS、候选 jar 未变化且没有强制停止；exit 0 单独不代表 QA 通过。`summarize_run.py` 保留失败状态及各 raw 文件 SHA。需要汇总时可运行：

```powershell
python -X utf8 tools/testlab/client_qa/summarize_run.py `
  "$workspace/client-runs/r8-zh-small" `
  --output "$workspace/r8-zh-small-analysis.json"
```

保留原始 checks/receipt/screenshots 与候选 SHA；修复 helper、计划或候选后使用新 run 名，避免覆盖失败证据。最终统一 manifest 应同时记录每项 raw evidence 的路径、大小与 SHA。

本轮 UI 阶段已归档关闭。最终客户端索引为 `plan/implementation/mui2-migration/verification/client/index.md`，manifest 为同目录 `evidence-manifest.json`；其中收录11轮、338文件、211张原始PNG，并保留历史 FAIL 与 NOT_RUN。R11 仅重跑371步/4组文字 focused 门，完整787步/6组 `tools-lore.json` 只是静态组合，未作为整套suite运行。专服直接回归仅覆盖R7候选；R7→R8逐entry字节一致证明支持有限范围继承，不代表R8专服实机运行。物理鼠标/键盘/IME、remote TCP及专服的5个client-only窗口仍为NOT_RUN/SKIP。

## 证据范围

### 离线全 UI 图库

当前采集入口为独立的 `run.py --mode gallery-batch --plan <冻结事件队列> --keep-open`，见 [专用事件图库](BATCH_GALLERY.md)。`gallery_batch_plan.py` 按现行目录生成独立截图事件；没有库存交易、付费升级、Wand 保存或祭坛替换等业务回归。一次启动后可通过 run 目录 `batch-control.json` 热触发后续队列，按实际 window/PSM/snapshot 就绪及连续真实渲染帧截图，分别记录启动、热批和 PNG IO 时间。

缓存节点编辑器绑定状态、Critical 长状态/高位读数及工具剧情本地 snapshot 均明确标注为视觉 fixture，不作为绑定、工作吞吐或物品/S2C路由验收。`gallery_report.py` 将完整清单、真实 PNG 和逐图 SHA 汇成可离线打开的 `index.html`；不可达、失败及未截图项目均保留。经审计确认字节一致的图可显式继承，实际 render 候选 SHA 与最终候选 SHA 分别记录。

早期 `gallery_plan.py` / `--mode gallery` 混入功能回归的方案已停止，只为本轮历史失败证据复现而保留，不作为当前截图入口。正式库存及工具剧情原计划不覆盖、不混入新的事件队列。

这条路径能核对 GL 实际绘制、MUI2 hit-test/focus、文本事件、集成服务端业务与 container 生命周期。合成输入不是人工 OS/IME 测试，`LocalChannel` 不是 TCP；专用服务端、远程网络及物理输入须由对应独立证据覆盖。截图只有在实际 render pipeline 调用绘制并留存可见正文时才可作为该视觉项证据。任何 client-only 项未执行时保留 `SKIP/NOT_RUN`，不从 headless UI PASS 推断客户端画面通过。
