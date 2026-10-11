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

## v1.21.17 独立织机客户端模式

`compile <新phase> --mode loom` 编译 `CriticalLoomClientProbe.java`，输出位于 `build/critical-machines-v12117/client`。从旧版本 fixture 使用 `fork --source-out build/critical-machines-v12116/client --from-phase <phase> --jar <冻结候选jar>`，不复制旧世界。该模式以1280×720启动真实 RC-2 客户端和独立集成服务器，手工放置1941 canonical loom voxels、三种GT舱口，真实StructureLib成型；通过HubTerminal开四页原生MUI2并核验72slots、16fluids、windowId/S2C、350×212布局和点击释放/关闭。新候选执行1个独立loom与3个selected target完整预览，不引用旧27矩阵。

调试测试发布真实Forge PlayerInteractEvent，证明生产WandClient拦截和正常右击发送；OPEN使用明确的WandNet.sendTarget(action0)网络入口，不声称模拟Alt实体键。真实selector按钮鼠标命中产生C2Sprofile，只写棒NBT；随后目标APPLY/RESET包检查生产adapter。TESR使用独立纯视觉NBT样本，在真实客户端AngelicaGLStateManager/dispatcher/framebuffer中绘制loom idle与施工两态并保存PNG；该视觉样本不证明材料支付。完整自然施工支付、ME事务与survival由专服loom探针覆盖。

需求页另核对大阶段焊锡需求同步，并用原生ListWidget滚动保存大阶段成本与小回合缺料截图。第二个真实奇点控制器验证动态schema和旧超限应用/逆向。新loomproxy fallback使用真实TrackedDummyWorld观测子类（仅记录其空markRange方法调用）检查15×36×15范围、ownership缓存、clock不重画、invalidate/validate/descriptor切换，以及实际MaterialRenderer/RenderBlocks/Tessellator顶点。

客户端ME显示验收在已启动但未供料的真实织机上暂停工作，保留普通输入总线/流体仓/能源仓，并加入原生ME总线/流体仓、AE胸箱与有限1k物品/AE2FC流体存储单元。待网络自然启动后向真实monitor注入下一回合库存，检查生产缺料getter与客户端MUI2 item/fluid缺料列表S2C共同归零、内部槽不伪导入且elapsed不推进；这只证明ME显示/同步，扣款另用专服事务证据。

滚轮输入明确写入synthetic GuiContext wheel，再经真实screen/ListWidget分派；原生ScrollData有500ms动画，因此在后续20个自然client ticks读取实际滚动位置。ME展示快照有20个world ticks缓存，注入与getter验收分开，至少等待25个实际world ticks；不反射清生产缓存。回执保存即时/过期后的需求、快照、过滤器、网格工作状态及elapsed，用于区分正常缓存延迟与库存问题。Windows读checks.json显式使用UTF-8。

冻结候选D的实际完整结果位于`build/critical-machines-v12117/client/candidate-d-loom-r3`：156/156 PASS，exit0，候选绑定及未变检查均为true，1280×720，新世界/新JVM，运行164.36秒；15张PNG位于其`screenshots`。候选SHA256为`5eef79642722afa13d88899dab3504f2f3aef1ecd9f477f18e35b0e248b15a57`。前两次`candidate-d-loom`与`candidate-d-loom-r2`的FAIL证据保留：分别是helper在500ms滚动动画同回调立即读取起点、在ME20worldtick缓存过期前立即断言库存，不当作生产故障或PASS。

最终ME注入后第35个实际worldtick，需求1plate/99mB、有限库存快照8plate/122mB，物品与流体缺料均归零，host暂停且elapsed=0。主代理已逐张查看需求三位置、ME已供应、两families调试面板及idle/build TESR截图，确认文字与按钮可读。OPEN代表Alt右击所触发的包入口模拟，未模拟硬件Alt按键；真实Forge PlayerInteractEvent另覆盖正常右击客户端键状态捕获与APPLY。TESR纯视觉图显示实际动画组，不能视为完整1941壳体世界照片，不证明FPS或施工支付。
