# 真实 NEI 界面生命周期回归

此夹具使用本机 Prism RC-2 的只读依赖，复制到全新的独立目录。不会复制用户世界、账号或启动器设置，也不向原实例写文件。需要约数 GB 可用空间。`prepare` 不启动 Minecraft；`run` 会开启独立的 854×480 客户端窗口，结束后自动退出。

```powershell
python tools/critical/client_fixture.py prepare legacy-red-<唯一名称> --jar build/libs/gtsr-v1.21.15.jar --expect-legacy-failure
python tools/critical/client_fixture.py compile legacy-red-<唯一名称>
python tools/critical/client_fixture.py run legacy-red-<唯一名称>
```

启动完整 RFB/FML 客户端，使用复制的真实 NEI、GT、Forge 和客户端资源。helper 只添加 FML 客户端 tick listener；不替换、补丁或 mock 核心 Minecraft/NEI/GT 类。启动前检查已有 Minecraft Java 进程，并通过 `E:/CodeGT/.gtsr-native-server.lease` 与专服验证互斥，存在占用则拒绝启动。预检仅记录 PID，不记录用户启动命令或认证参数。

旧版测试通过真实 `Minecraft.displayGuiScreen` 打开候选 GUI，随后实际调用 `updateScreen`、`handleMouseInput`。`EXPECTED_FAILURE_REPRODUCED` 必须含打开阶段 manager NPE 且堆栈同时包含生产 GUI 的 initGui/updateScreen；退出码 0 或背景渲染成功均不足以证明复现。鼠标测试仅在真实 LWJGL 已初始化后调用输入处理入口，不伪造滚轮事件；这能覆盖 null-manager 分派路径，但不证明用户点击/拖拽或 NEI 配方交互。

新回归的 `checks.json` 和 `receipt.json` 位于 `build/critical-machines-v12116/client/<phase>`，已执行的旧版证据保留在 `build/critical-machines-v12115/client/legacy-red-player-20261011`。此旧版回归使用独立构造的真实控制器/容器和 synthetic 玩家背包；不加载世界，不证明机器正常放置、GT BaseMetaTileEntity 注册、NBT 重载、服务器窗口同步或完整游戏操作。迁移后的标准 GT UI 全链路验收需另用真实世界、玩家和注册机器实例，不能把本回归的 PASS 当作该项验收。

崩溃报告已包含实际旧版失败证据：`crash-2026-10-11_09.20.32-client.txt` 第 67395–67402 行的首次 OpenGuiHandler 异常从 GUI `initGui:57` 调用 `updateScreen:75`，先于 GuiContainer 的 NEI manager 初始化；后续鼠标异常属于连锁。

## 标准 GT 完整客户端模式

```powershell
python tools/critical/client_fixture.py prepare gt-<唯一名称> --jar <冻结候选jar>
python tools/critical/client_fixture.py compile gt-<唯一名称> --mode gt
python tools/critical/client_fixture.py run gt-<唯一名称>
```

`--mode gt` 编译 `CriticalGTClientProbe.java`，在真实 `TrackedDummyWorld` 中构造 9 种机器×3 档，逐坐标比对 canonical 材料、metadata、总量和世界边界；执行真实 `ObserverWorld` 与 `GTConstructableScan`，核对六类 GT 原生 hatch 目录。然后用 `Minecraft.launchIntegratedServer` 创建新世界，等待真实客户端/服务端玩家，通过注册的 `ItemMachines.placeBlockAt` 放置机器，由服务端 `MTE.onRightclick` 打开原生 MUI2。验收包含 72 slots、16 个流体同步器及实际水量、server/client windowId、三页鼠标分派、关闭包和重开、10 张真实 NEI recipe map 与 BlockRenderer 结构页。

完整模式以 1280×720 启动；`compile <新phase> --mode gt-small --preview-from <已核查phase>` 可独立以 854×480 原生启动，只补查实际小窗 UI、同步、三页点击和 TESR，不重复 NEI maps。两种 viewport 使用独立新世界，保留自动 GUI scale，记录 panel 与 scaled viewport 边界并保存真实 GL 截图。布局越界会记录失败，退出码 0 不等于 PASS。每轮保留候选 SHA、来源绑定、helper SHA 和逐项 receipt；重跑归档前次 receipt，避免读取旧 checks。新候选优先使用新 phase；`fork <phase> --from-phase <旧phase> --jar <新jar>` 可以复用只读依赖，跨版本输出目录可用 `--source-out <仓库build下目录>`。fork 不复制已有独立测试世界或备份。

夹具禁用失焦暂停，并读回实际 `GameSettings` 值。玩家必须完成登录、落在真实石平台上，双端同维度且在原生交互距离内稳定 20 ticks；客户端实际 GT 主机/MTE 网络同步后才请求服务端开窗。UI 截图在 `RenderTick.END` 捕获完整帧，NEI 结构页通过原生 `setRecipePage` 切换。TESR 样本绑定真实主 framebuffer 后立即读取像素与截图；它只证明纯 visual NBT 与 canonical proxy geometry 的实际渲染，不证明支付业务。

定向补查可用 `compile <新phase> --mode gt --preview-from <旧phase>` 引用已执行的完整预览证据。脚本必须验证同一候选 SHA、实际来源绑定、候选未变及所有 preview 检查通过；新回执明确记录引用路径，不声称本轮重新构造矩阵。`receipt.probeSHA` 是已执行 helper JAR 的 SHA；源 SHA 为 `launch.probeSourceSHA`，归档源文件位于该 phase 的 `CriticalGTClientProbe.java`。
