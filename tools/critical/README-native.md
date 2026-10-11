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
