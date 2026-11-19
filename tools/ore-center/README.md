# 矿物加工集成中心维护

`compile.cjs` 从本地 `plan/矿物加工集成中心工程` 生成生产结构的整格稀疏坐标，输出到 `assets/gtsr/orecenter`。只有结构变化时才重新导出。运行时读取已提交的结构资源，并从 Forge 矿物词典和 GTNH 原生配方表取数据。

## 定向 TestLab

使用本轮源码构建的完整 GTSR 和兼容 Core TestLab 替换包，清单同时记录对应普通包 SHA-256。执行前在工作区根据 `plan/testlab-instances.json` 核对登记实例。入口统一登记在 GTSR 的 TestLab 目录，以下用例需显式选择。

| 用例 | 验收内容 |
| --- | --- |
| `ore_integration_center` | 原生配方注册、矿词命中、出口过滤、图谱优先级、NBT 和批次账单 |
| `ore_integration_center.machine` | 单扇实机扣料、断汽暂停、满输出和产物交付 |
| `ore_integration_center.graph_tcp` | 私有桌面原生 MUI2、非 OP2 机主的 TCP 写入、退出后重新打开及服务端 NBT |
| `perf.ore_integration_center` | 1/8/32 个规划会话，分别测量预编译和重建图谱，保留长尾调用及时间锚 |
| `perf.ore_integration_center.entities` | 0/1/2/3 台真实单扇控制器，记录蒸汽收支、实物产出、并发与各阶段 tick |

专服用例由共享 TestLab 宿主从控制台提交。双端用例从本仓运行以下命令，省略 `--execute` 时仅核环境及工件：

```powershell
python -B tools/ore-center/client_server.py --bundle <bundle.json> --evidence <新证据目录> --execute
```

手动审核用同一份工件清单并增加 --review-port 20742 与 --language zh_CN。工程 index 的「机器 GUI」直接连接此回环窗口。点击、拖动、滚轮和键盘由私有 JVM 的原生控件处理，浏览器读实际绘制结果。F4 用于查看控件名和坐标。结束审核后宿主停机并恢复实例；手动审核回执记录会话及清理，自动用例另行验证脚本断言。

排查压力峰值时在专服进程启用 JFR，使用报告中的 tick 与 UTC 毫秒锚连接对应观测区间。单次原生配方查询及 JVM 暂停可能跨越规划的软预算。压力判断同时看耗时分布、样本数量和实际业务产出。
