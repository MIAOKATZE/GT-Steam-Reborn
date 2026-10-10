# 临界机器原生服务器核验

在项目完整构建后，用候选正式 jar 建立独立 GTNH 205 专服夹具：

```powershell
python tools/critical/native_fixture.py prepare final-new --jar build/libs/<候选>.jar
python tools/critical/native_fixture.py compile final-new
python tools/critical/native_fixture.py run final-new
```

每次使用新的 phase 名，避免覆盖失败回执。以 `final` 开头才启用完整矩阵。脚本使用本机已有 `build/coke-rebuild-v12114/native/natural-final/fixture` 的运行依赖复制新实例；不下载整合包，不修改原测试实例。需要已缓存的 Java 17（helper 编译）、Java 21（专服）与 Gradle patched Minecraft 编译输出。

启动前检查现有专服进程，并取得 `E:/CodeGT/.gtsr-native-server.lease`；有其他专服占用时拒绝启动。日志和原始回执保存在 `build/critical-machines-v12115/native/<phase>/`，包含候选/helper SHA、注册后生产类来源、启动参数、PID、退出码与逐项 checks。`checks.status=PASS` 且实际进程正常退出后才算通过；退出码 0 单独不足以证明通过。

代表样本通过真实材料和能量完成跨区块施工；九机×三级矩阵通过正常区域/owner/job 绑定后，直接放真实几何并设置明确标注的 READY NBT 夹具，验证生产控制器的配方、输出和能量生命周期。矩阵不冒充 27 次付费建造。多数边界在服务器主线程调用真实 `updateEntity` 加速，另保留自然 world ticks 批次及维度时间推进样本。

NBT 快照替换、已登记残留和外来方块测试用于核查恢复接口；不证明强制终止进程、磁盘 fsync 或多 world chunk 原子保存。实际 helper 能量接收 TE 只证明 `IEnergyConnected` 方向、16A 限额和 EU 守恒，不代表完整 GT 电缆网络验证。最终覆盖与限制以对应 phase 的 checks/receipt 和 Plan 验证记录为准。
