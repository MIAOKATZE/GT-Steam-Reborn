# 稀疏资源 NBT 回归与恢复

`GTSR-NBT-001` 的回归调用真实 `CriticalMachineState.readFromNBT/writeToNBT`、`CriticalResourceNbt` 与施工账本退款方法，复用 `Issue37RegistrationRegression` 的隔离注册环境。仅使用原生 Minecraft NBT、GTNH 2.9.0 / GT5U `.265` 和 Java 26，不启动游戏或 Gradle。

动态日志每次读取最多 65,536 条记录、65,536 个数组槽，slot 范围为 `0..65535`。这是明确的读取分配安全预算，不是历史 writer 的数学上限，也不限制堆栈中的资源数量。固定库存/流体数组分别按原有 36/16 槽校验。负数、重复、缺失或错误类型的 slot、超限条目和槽位在资源数组分配前拒绝。writer 保留原有 slot/fullCount/Amount 格式；合法高稀疏槽、原生 Count 和大 fullCount 不压缩、不截断。

非法记录在控制器边界隔离；原始完整记录保存在 `resourceNbtQuarantine`，保存时也保留原始字段，外层写入 `nativeFundingUncertain=true` 和中文完整性错误状态。隔离后的控制器停止运行、结算和交互，拒绝继续接收物品/流体，不能移除；已经部分读取的库存不会暴露给自动化。现有 GT scanner 的 `getInfoData` 包含完整错误状态，不依赖能否打开 GUI；不新增每 tick 日志。重复加载/保存不会把隔离记录再次嵌套。

恢复应离线进行：

1. 保留原存档与隔离记录的副本，从 `resourceNbtQuarantine` 取出完整原始控制器记录。
2. 核对真实已支付资源后修复损坏的索引或日志结构；保持物品身份、NBT、fullCount/Amount 和退款顺序。超过读取预算的合法旧记录同样完整保留，需要人工核对后恢复为预算内的可读记录；程序不自动删项或截账。
3. 用修复后的完整原始记录替换该控制器记录，移除其中的 `resourceNbtQuarantine`。原始记录若已有 `nativeFundingUncertain` 或经济账本不确定标记，保留其原值并按原有资金核验流程处理；不要直接清空外层锁标记。
4. 重新加载后核对库存、托管、待交付余额及退款记录。仅修正 slot 的恢复由回归覆盖；工具不宣称具备跨存档事务或完整游戏验收。

源码回归示例（PowerShell；正式验证时填写实际候选路径）：

```powershell
python tools/tests/run_resource_nbt_regression.py `
  --product E:/path/to/gtsr-dev.jar `
  --core E:/path/to/miaokatzecore-dev.jar `
  --classpath build/recipe-energy-launch-v213/compile-classpath.txt `
  --java-home 'C:/Program Files/Java/jdk-26.0.2.1' `
  --out build/resource-nbt-green --mode green
```

每次使用独立输出目录。对最终候选验证增加 `--jar-only`，使两份生产类均从该候选 JAR 载入。默认源码模式只覆盖两份明确列出的生产源，回执记录源/候选/Core/GT 的 SHA-256，日志记录实际 CodeSource。

红阶段 `--mode red` 只将一条 `slot=Integer.MAX_VALUE` 写入真实控制器资金日志。旧实现的 `slot+1` 溢出会静默丢账，测试据此产生明确失败；不向旧实现提交会请求巨数组的十亿槽位。绿色阶段才运行十亿槽位和完整边界、重复保存、隔离锁、恢复、旧/新退款守恒回归。运行 JVM 的最大堆固定为 256 MiB，单个动态数组最多 65,536 个引用；实际引用字节数由 JVM 决定。
