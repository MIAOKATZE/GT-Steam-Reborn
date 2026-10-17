# 原生区块票据同存档重开回归

这是独立的开发测试模式，不执行 UI 套件，也不进入交付 jar。沿用 owned 离线客户端、显式 JVM 属性与测试维度配置双重门禁、全局 lease、内存预检。原实例、账户、世界与 EULA 均不修改。

先准备已对齐的 GT205 / MUI2.92 owned 客户端，编译 helper，再运行：

```powershell
python -X utf8 tools/testlab/client_qa/compile.py --workspace build/mui2-v12138 --client-sandbox build/client-testlab-mui2-v12138
python -X utf8 tools/testlab/client_qa/run.py --workspace build/mui2-v12138 --client-sandbox build/client-testlab-mui2-v12138 --jar build/mui2-v12138/migration-candidate-r9.jar --mode ticket-restart --run ticket-restart-unique --language zh_CN --heap-gib 5 --reserve-gib 4 --render-distance 3 --deadline 900
```

每次使用新 run 名，runner 拒绝覆盖原证据。该 run 自建一个 owned save，**同一 JVM 内两次进入同一个 save**，两次服务器对象与线程必须不同。真实注册 GT 方块产生 BaseMetaTileEntity，生产 `CriticalChunkLoading.ensureAllStructureChunks` 申请并强制一个真实 chunk；同时在主世界通过真实 manager API 建立一个无 loader 的原生票。正常进入 TestLab 维度后再申请生产票，不伪造 World 或 Ticket，不改配额与权限门。

每服票据建立后至少运行 120 个真实服务端 END tick。退出走实际 `GuiIngameMenu` 的返回菜单按钮逻辑：断开连接、`loadWorld(null)`、返回主菜单。观察真实 Forge WorldUnload 的 HIGHEST / LOWEST 顺序，确认 Forge 先清原生票表，生产 Supervisor 随后清本地引用；等待 FMLServerStopped 以及旧服务器线程结束后才允许重进。第二服还验证 public release 清本票、不影响另一张真实外票，然后重新 ensure，证明没有永久停服标志。

检查原生 forcedchunks.dat 在正常保存后确有 GTSR 票，重进经过原生加载路径且生产 callback 已登记。fixture 是最小合法 BaseMetaTile，**不代表完整形成的 Critical 巨构与 owner journal 恢复验收**。停服后的旧 Ticket.getChunkList 可以非空；撤销依据是 Forge 全局票表、生产 LOADERS / REGISTERED 与 loader 字段清空。QA 暂时保留观测对象身份，最终随 owned JVM 退出，不把这些观测引用计作生产泄漏。

runner 不只检查 exit0：BetterCrashes 可恢复异常，因此 ticket 模式额外检查客户端未报告异常、BetterCrashes 崩溃与服务端 tick 异常标记。结束后生成只读摘要：

```powershell
python -X utf8 tools/testlab/client_qa/summarize_ticket_restart.py build/mui2-v12138/client-runs/ticket-restart-unique --output build/mui2-v12138/ticket-crash/ticket-restart-unique-analysis.json
```

R12 的旧驱动只卸载世界而未返回菜单，真实票据与两服清理断言均通过，但触发 Blood Magic 空玩家渲染异常；原 raw 自动 PASS 不追改，独立分析记整体 FAIL。R13 使用完整原生退出链及日志门。最终状态以各自冻结 raw 与分析为准，不把历史失败抹成通过。

R9 候选 `a866ff75c3da37965f07a004594badebfac73ee237dbf9503b29cc6c2991f544` 的 R13 实测为 304/304 PASS，165.438 秒正常退出，无强停、无上述崩溃日志标记。两服分别为同一 owned save 中的不同真实服务器和线程，均完成至少 120 个建立票据后的 tick。此结论限于上述真实最小 host / 原生票生命周期，不扩展为完整巨构或长期压力通过。
