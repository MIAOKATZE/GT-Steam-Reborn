# 客户端复审定点回归

对应 `LIFE-R03`、`PERF-001`、`RENDER-001`。本工具不启动 Gradle；原生客户端只由主执行者启动。

```powershell
python tools/workvisual/probe_client_review.py --out build/client-review-feedback-green
python tools/workvisual/probe_budget.py --out build/client-review-budget-policy
```

第一项直接编译并执行生产 `MachineVisualClient`、`MachineVisualClearance`、`MachineVisualRenderer`、`MachineVisualSpace`。MC、网络、Visibility ACK、材质绑定和 GL 是明确的测试边界假件，定义成员/行程来自实际 `motions.json`；窄壳以 Java17 为编译目标，不证明发布 JAR、Java8 全客户端或真实 GL。

当前合同验证内部纯装饰按有效成型/工作快照和预算准入，不作持续或定时空间、扫掠、光照核验。2026-11-14 的离线回执为 `.artifacts/gtsr-turbine-proximity/decoration-client-review/receipt.json`：26项通过；登记等待0个额外tick，LOW512 Atmos仅有1,024次控制器身份查询，装饰体积查询0次，持额126成员；同tick partial=.1/.9样本为105/144。数值是受控调用计数，没有测量实际帧时、CPU或GC。

其余反馈覆盖无快照不准入、工作停止恢复静态、恢复期间仍持额、同控制器坐标重入等待显式ACK、恢复ACK后重新准入、仅成员定向几何重用，以及pause/OFF/stale/advanceUntil和单tick发电机coast边界。`MachineDecorationContracts`另对真实生产方法、全部资源定义和编译字节码验证授权、预算与无世界查询/无sweep边界。保留原预算策略合同，不把不同测试数相加当成原生检查数。

登记额度512，待登记观察池512，每tick最多准入4个；同优先级受120tick登记租期约束，最近渲染候选优先。保证范围为池内候选以及报告的512旧宿主加1个新近宿主场景，不声明任意无限宿主的绝对公平。退役通过既有Visibility静态恢复协议，CELLS继续持旧owner，RenderWorldLast静态代理负责补块，实际上传/原生退役ACK前held不释放。

原生扩展复用现有TASK03夹具，在独占lease空闲时准备：

```powershell
python tools/workvisual/prepare_native_review.py --compile
```

准备结果写 `build/task03-client-qa/client-review-prepare-receipt.json`，默认4GiB、隐藏桌面。它仅安装owned helper，不修改生产JAR；启动前核验helper及所有生成源SHA。由主执行者将当前GTSR和锁定Core候选装入后，使用生成入口：

```powershell
python build/task03-client-qa/run-client-review.py --jar <当前候选.jar> --run <未使用的run名> --hidden-desktop --heap-gib 4 --reserve-gib 4 --render-distance 3 --width 1280 --height 720 --deadline 900
```

有限范围包括：512个真实未成型控制器登记满额后加入首台真实工作机；20个明确注入客户端DTO的受控候选验证OFF/LOW/STANDARD装饰持额分别为0/126/360且空间查询计数均为0，随后恢复原DTO；再执行原五机自然生产、动作、OFF恢复，并验证Air/Fluid/Crust不受结构外或skip格stone影响。外部排气口的真实空气门控保持原合同。受控20条不是20台自然生产多块，也不是CPU/GC性能验收。每机最多记录120帧实际partialTick与生产Renderer保存的sampledDecorativeMs，要求同一client tick中存在不同装饰时刻；截图就绪采样也读这一实际帧时刻。

原生运行是否通过只看对应run的检查、生命周期和退出回执；`COMPILED_NOT_RUN`不代表游戏验收。旧失败目录保留，重跑必须使用新的run名。

历史记录：`review-client-20261102-03` 曾执行旧空间检查方案的受控候选计数，随后513入场检查失败；该计数和旧障碍回退结果不适用于当前纯装饰方案。夹具同维度`enter`导致的近距前提问题仍按既有修复处理；完整原生结果只看新的run，不能沿用历史回执。
