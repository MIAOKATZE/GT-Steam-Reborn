# 专用 UI 事件图库

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
