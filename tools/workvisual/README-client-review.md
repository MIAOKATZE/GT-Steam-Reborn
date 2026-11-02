# 客户端复审定点回归

对应 `LIFE-R03`、`PERF-001`、`RENDER-001`。本工具不启动 Gradle；原生客户端只由主执行者启动。

```powershell
python tools/workvisual/probe_client_review.py --out build/client-review-feedback-green
python tools/workvisual/probe_budget.py --out build/client-review-budget-policy
```

第一项直接编译并执行生产 `MachineVisualClient`、`MachineVisualClearance`、`MachineVisualRenderer`、`MachineVisualSpace`。MC、网络、材质匹配、Visibility ACK 和 GL 是明确的测试边界假件，定义成员/行程来自实际 `motions.json`；窄壳以 Java17 为编译目标、在 Java26 运行，不证明发布 JAR、Java8 全客户端或真实 GL。

初始 red 位于 `build/client-review-feedback-red/receipt.json`：登记512后第513个近宿主125 tick不入场；LOW512 Atmos调用World API 78,848次（含宿主身份核验1,024次）；同tick partial=.1/.9传给采样器的时刻均150。修正后21项反馈通过，对应数值为等待0个额外tick、2,088次、105/144。数值是受控调用计数，没有测量实际帧时、CPU或GC。

其余反馈覆盖已发布组下一tick障碍检查、恢复仍持额、同控制器坐标重入等待显式ACK、障碍候选的有限扫描轮次、固定几何重用、pause/OFF/stale/advanceUntil及单tick发电机原有coast边界。现有预算策略412项独立通过，不把两套测试相加当成原生检查数。

登记额度512，待登记观察池512，每tick最多准入4个；同优先级受120tick登记租期约束，最近渲染候选优先。保证范围为池内候选以及报告的512旧宿主加1个新近宿主场景，不声明任意无限宿主的绝对公平。退役通过既有Visibility静态恢复协议，CELLS继续持旧owner，RenderWorldLast静态代理负责补块，实际上传/原生退役ACK前held不释放。

原生扩展复用现有TASK03夹具，在独占lease空闲时准备：

```powershell
python tools/workvisual/prepare_native_review.py --compile
```

准备结果写 `build/task03-client-qa/client-review-prepare-receipt.json`，默认4GiB、隐藏桌面。它仅安装owned helper，不修改生产JAR；启动前核验helper及所有生成源SHA。由主执行者将当前GTSR和锁定Core候选装入后，使用生成入口：

```powershell
python build/task03-client-qa/run-client-review.py --jar <当前候选.jar> --run <未使用的run名> --hidden-desktop --heap-gib 4 --reserve-gib 4 --render-distance 3 --width 1280 --height 720 --deadline 900
```

有限范围包括：512个真实未成型控制器登记满额后加入首台真实工作机；20个明确注入客户端DTO的受控候选进行OFF/LOW/STANDARD实际World查询计数，随后恢复原DTO；再执行原五机自然生产、动作、OFF恢复以及Air/Fluid/Crust实际stone负例。受控20条不是20台自然生产多块，也不是CPU/GC性能验收。每机最多记录120帧实际partialTick与生产Renderer保存的sampledDecorativeMs，要求同一client tick中存在不同装饰时刻；截图就绪采样也改读这一实际帧时刻。

原生运行是否通过只看对应run的检查、生命周期和退出回执；`COMPILED_NOT_RUN`不代表游戏验收。旧失败目录保留，重跑必须使用新的run名。

`review-client-20261102-03` 已真实执行20受控候选计数（20tick：OFF=0、LOW=280、STANDARD=800），随后513入场检查失败。夹具再次调用同维度`enter`，将玩家临时传到入口，破坏冷宿主64格距离前提并触发正常近距淘汰；没有重建WorldClient。生成副本已改为仅跨维度时enter，并记录同一WorldClient、512冷宿主仍加载/对象未变/距内的前提，保留登记数必须为512的原断言。失败收尾按实际数量报告冷控制器清理未完成，并保留首个失败；修复后的完整原生结果需看新的run，不能沿用本轮已失败回执。
