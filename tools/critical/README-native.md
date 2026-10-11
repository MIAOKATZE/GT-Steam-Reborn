# 临界机器原生服务器核验

在项目完整构建后，用候选正式 jar 建立独立 GTNH 205 专服夹具：

```powershell
python tools/critical/native_fixture.py prepare final-new --jar build/libs/<候选>.jar
python tools/critical/native_fixture.py compile final-new
python tools/critical/native_fixture.py run final-new
```

每次使用新的 phase 名，避免覆盖失败回执。以 `final` 开头才启用完整矩阵。脚本使用本机已有 `build/coke-rebuild-v12114/native/natural-final/fixture` 的运行依赖复制新实例；不下载整合包，不修改原测试实例。需要已缓存的 Java 17（helper 编译）、Java 21（专服）与 Gradle patched Minecraft 编译输出。

启动前检查现有游戏进程，并取得 `E:/CodeGT/.gtsr-native-server.lease`；有其他客户端或专服占用时拒绝启动。进程预检只记录 PID 与匹配原因，不记录用户客户端命令行。当前日志和原始回执保存在 `build/critical-machines-v12116/native/<phase>/`，包含候选/helper SHA、注册后生产类来源、夹具启动参数、PID、退出码与逐项 checks。旧 v12115 证据保留。`checks.status=PASS` 且实际进程正常退出后才算通过；退出码 0 单独不足以证明通过。

代表样本通过真实材料和能量完成跨区块施工；九机×三级矩阵通过正常区域/owner/job 绑定后，直接放真实几何并设置明确标注的 READY NBT 夹具，验证生产控制器的配方、输出和能量生命周期。矩阵不冒充 27 次付费建造。多数边界在服务器主线程调用真实 `updateEntity` 加速，另保留自然 world ticks 批次及维度时间推进样本。

NBT 快照替换、已登记残留和外来方块测试用于核查恢复接口；不证明强制终止进程、磁盘 fsync 或多 world chunk 原子保存。实际 helper 能量接收 TE 只证明 `IEnergyConnected` 方向、16A 限额和 EU 守恒，不代表完整 GT 电缆网络验证。最终覆盖与限制以对应 phase 的 checks/receipt 和 Plan 验证记录为准。

GT MetaTileEntity 新桥接验收使用独立 helper，不能以旧普通 TE 矩阵代替：

```powershell
python tools/critical/native_fixture.py prepare gt-candidate --jar build/critical-machines-v12116/candidate-d.jar --hatch-guards
python tools/critical/native_fixture.py compile gt-candidate --probe gt
python tools/critical/native_fixture.py run gt-candidate
python tools/critical/native_fixture.py restart gt-restart --from-phase gt-candidate
python tools/critical/native_fixture.py compile gt-restart --probe gt
python tools/critical/native_fixture.py run gt-restart
```

GT helper 从真实 GT 注册表取得控制器，经 ItemMachines 放置真实 BaseMetaTileEntity，检查原生 NBT 工厂与全部示例配方。业务样本通过生产付费施工到 READY，物理放置真实六类仓口，核查输入/输出、缺块暂停/修复、拆除保留仓口、solar 大流量分批输出及多流体输入。单独的电气原语测试把真实注册仓口加入原生列表，不声称验证电缆布线。旧控制器迁移样本带真实活动 job、库存和能量；自然 180 ticks 核查父级停机及软锤开关。restart 只复制已退出的独立夹具世界，并由新进程读取真实磁盘状态，不修改原样本。上述为测试范围，执行结果必须查看各 phase 实际回执；客户端 MUI2 和 27 个 NEI 结构预览由独立客户端 helper 验收。

restart 模式另外付费准备新的 Processing 到 READY，经物理仓口供料后等待 450 个自然世界 tick，核查前十 tick 原生输入/escrow 守恒与非零 Scanner/Waila 进度，最终只输出 16 件且配置电路保留。独立夹具每个 ServerTick START 加载自己的真实 80×80 区域，避免无人专服卸载区域；该阶段不手动推进生产 updateEntity。GT 配置槽 setter 会把电路强制转为零数量 ghost，配置保留核查该原生零数量、metadata 与 NBT；普通可消耗原料槽另使用一件实物电路，不把 ghost 当作物料。


## v1.21.17 专用织机核验

`native_fixture.py compile <独立phase> --probe loom` 编译 `CriticalLoomIntegrationProbe.java`。本轮输出目录为 `build/critical-machines-v12117/native`，不会覆盖 v1.21.16 证据。prepare 必须绑定冻结 candidate jar，compile 将源码副本及 SHA256 写入该 phase；完成 phase 不可重跑。

织机 probe 使用真实注册 GT ItemMachines、1941 canonical 壳体和实物 input bus/input fluid/energy hatch，核验裸控制器拒绝、完整 StructureLib 检查、回合先付款、速度与调试实付账本、织机破坏暂停。速度断言明确使用生产 state 更新；完整大阶段使用真实服务器自然 tick。完成大阶段后保留 funded round 和暂停状态，`restart <新phase> --from-phase <源phase>` 复制真实世界存档，通过独立 JVM 验证 funded round 不重复扣料。脚本保留已有 Java runtime 检查及 `E:/CodeGT/.gtsr-native-server.lease` 独占门禁，不操作用户游戏实例。

尚未运行前不能称为通过。织机 probe 另以真实 ME chest、1k cell、creative AE energy cell 和配置 GT ME bus 等待120自然tick组网，核验离网ghost拒绝、有限网络库存exact回合减少且内部input无物理导入；之后恢复普通实物bus完成自然大阶段。


独立autoHost仅显式加载controller与手搭loom所需chunk，通过自然tick读取真实Forge owner票、单owner单ticket及原生depth限制，观察未手工加载的远targetchunk被原生票加载并推进。超额测试直接请求原生预算+1，不修改Forge全局quota，要求拒绝、释放且spent不变。自动完工后400自然tick只保留controllerchunk供观察，要求READY不重领票；这段不通过disableWorking掩盖行为。


### 冻结 candidate D 实际结果

最终候选 `candidate-d.jar` SHA256 `5eef79642722afa13d88899dab3504f2f3aef1ecd9f477f18e35b0e248b15a57`。独立 phase `loom-d-01` 的40项原生断言及 `loom-d-restart-01` 的4项新JVM断言全部PASS，两个进程exit0，关机日志 `shutdown_clean=true`。两个phase都在停止前由专用bare controller取得真实GT/Forge票，因此卸载测试覆盖实际有票场景。早期失败phase保留，不能当作D成功证据。

D实际覆盖：1941手搭壳体与3必要hatch、裸controller拒绝、施工自动恢复GT工作开关、exact回合物品/焊锡先支付、缺料/流体/EU/破坏织机暂停、normal/2x/10x真实elapsed、instant自然施工完成、免物品仍扣流体/免流体仍扣物品、免费round不生成实付退款账本、reset恢复下一回合需求；离网ME配置ghost不支付、不推进；真实相连ME chest+item 1k cell与AE2FC fluid 1k cell的有限库存同时提交，items8→7（exact1）、焊锡81→23（exact58），内部input/tanks无物理导入。临时AE网络在交易后拆除。

独立autoHost仅预加载controller/loom的3个chunk；第7自然tick观察到未手工加载的远目标chunk被原生票加载且elapsed10。票始终单owner最多1张、depth≤原生25；超额预算请求拒绝且释放不付费；施工完成后票释放，继续400自然tick保持原生开关开启，仅pin controller供观察，READY在远区块卸载后暂停且不重取票。主host完整majorstage恰400自然tick，实付焊锡delta2304与阶段需求2304一致，fluid journal仅1段。

重启实际配置 `alwaysReloadChunkloaders=false`，按GT原生规则未主动恢复shutdown fixture票；未改配置或伪造adoption证明。新JVM从真实保存世界恢复funded round（stage4/round0/elapsed1/items79/fluid4620），实际resume推进。额外只读存档证明写在 `loom-d-restart-01/post-restart-funding-audit.json`，与4项JVM断言分开：停止后真实NBT elapsed2/round1/items79/fluid4620，物品与流体均未重复扣费、fluid journal仍1段；该JSON绑定region SHA256，不改写任何checks/receipt。

冻结probe源SHA256 `f5b052b4fc475a9e51a3248ec7d6f8ff16dddc6b1900536ee91fcea850930fec`；D helper jar SHA256 `276da84ef8b3e08cc9cce31382e72d439e43d1e39ea3fccfaac78003986dcc7b`。客户端、配方NEI/UI视觉及用户实例行为由独立核验负责，本文不将原生服务器结果作为其通过证明。
