# MUI2 客户端 QA 使用说明

复用工作区 `Testlab/instances.json` 中的 GTSR 客户端。runner 直接启动 Java 26，固定最大堆 4 GiB，系统预留 1 GiB；使用私有桌面与目标实例租约。启动与恢复总入口见 [项目指南](../../../plan/测试维度指南.md)。

1. 客户端模式为 `ui/gallery/gallery-batch`。旧 `ticket-restart` 已退役，跨 JVM 恢复走 `tools/testlab/recovery_restart_driver.py`；[TICKET_RESTART.md](TICKET_RESTART.md)仅供历史记录查询。
2. 平台或缓存变化后重建元数据，复用登记实例，不复制整包或另建客户端沙箱。以下准备命令从 GTSR 仓根执行，不启动游戏：

```powershell
python -B tools/testlab/client_qa/prepare.py --jar <实际候选.jar>
python -B tools/testlab/client_qa/compile.py
```

3. 编译只生成项目 `build/mui2-client-qa` 下的 helper 与回执，执行时由 runner 临时安装并恢复。共享源码沿 `HostEnvironment/OwnedClientScope/ClientAccess` 取用；占用检查针对登记实例。
4. gallery 使用 `merge_plans.py --output <冻结计划.json> <片段...>` 准备计划。实际启动由共享宿主持有租约并调用本 runner；候选按当前配置或显式 bundle，沿项目 `profile.py` 选择。下列为获准测试时的命令形式：

```powershell
python -B ../tools/testlab/host.py doctor --mod gtsr --lane client --task gallery --client-plan <冻结计划.json> --prepare
python -B ../tools/testlab/host.py run --mod gtsr --lane client --task gallery --client-plan <冻结计划.json> --prepare --execute
```

5. 结束时先完成项目场景恢复和自有客户端退出，再恢复原包、配置及选项、回收本轮临时资源并释放实例租约；异常时保留未恢复记录。图库脚本、实际机器操作和跨 JVM 恢复协议仍由本项目维护。
6. 输入沿实际 MUI2 hit-test/focus、lwjgl3ify 文本事件及原生容器通道；物理 OS 输入和专服 TCP 分别按对应专项验收。

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

客户端视觉 builder 开窗前，若服务端仅持有默认 `inventoryContainer`，跳过冗余 `closeScreen`；实际打开的服务端容器仍关闭。v1.21.40 祭坛视觉项曾两次出现 `GUI=null` 超时，失败原件保留；加此 guard 后单项及同 JVM 的 Navigator→Altar 顺序对照均捕获成功。该对照支持探针关闭链修正，不代表已证实原失败的具体数据包来源，也不验证生产 wrapper 鉴权。

早期 `gallery_plan.py` / `--mode gallery` 混入功能回归的方案已停止，只为本轮历史失败证据复现而保留，不作为当前截图入口。正式库存及工具剧情原计划不覆盖、不混入新的事件队列。

这条路径能核对 GL 实际绘制、MUI2 hit-test/focus、文本事件、集成服务端业务与 container 生命周期。合成输入不是人工 OS/IME 测试，`LocalChannel` 不是 TCP；专用服务端、远程网络及物理输入须由对应独立证据覆盖。截图只有在实际 render pipeline 调用绘制并留存可见正文时才可作为该视觉项证据。任何 client-only 项未执行时保留 `SKIP/NOT_RUN`，不从 headless UI PASS 推断客户端画面通过。
