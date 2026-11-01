# AirCompressor 接入

业务 owner 为 `MTEAirCompressor`；蒸汽付款、20/5 tick、产气与原业务状态仍由本机负责。`MachineVisualHost`/只读状态观察接 `MachineVisualDefinition.get("air_compressor")`，不通过预览调用 processing/checkMachine。观察成功推进不代表产物已送达。

定义是生产 `motions.json` 的两个 compression_pair 组：前后各 2 个原齿轮箱单位块，相向沿 Z 0..0.35，最低间隔 0.30。仅新分支精确替换 12 个侧玻璃；前后和 16 框架不变；旧厂继续识别，旧分支不套新运动。原 skip `(0,0/1,2)` 需实际世界净空，不能改成成型 required-air。

作者来源为 TASK03 `runtime/models.json` 的已选 new blocks/group，正式出口 `pipeline.py export-motions`；`profile.json` 固定材料、成员数与行程。原生 GT gearbox 为 Casings2:2/3，显示通过实际匹配方块的 GT icon/ISBRH，不做细模型或新世界方块。来源、定义和工具均进入独立 authoring manifest；JAR 中定义进入 runtime 闭包。

OFF、未知/过期快照、占用或未就绪显示组恢复静态；局部隐藏与代理可用性一起退役，不能留下洞。视觉状态为临时状态，不新建业务 NBT；拆机、转向、卸载、连接/世界切换按安装代际丢弃显示。具体 lifecycle/旧存档行为仍由实机门签收。

本机完整运动成本为 4 成员/2 组，纳入普通机全局 LOW 128/16、STANDARD 512/64、HIGH 1024/128 的成员/组额度；OFF 不录取新运动。预算轮转租期为 120 客户端 tick，有竞争才到期让额。r3 恢复组持续持额，直到同 maskEpoch 静态可见上传，或同 world/manager/section 的原生 GPU 退休确认后释放；精确 section 进入 important 重建队列本身不是 ACK。412 项预算源码方法与 14 项 foreground 坏哈希源码合同均未加载 FML/GL，不能证明这些 actor 回调；r3 客户端仍须单独验。

离线证据在 `build/workvisual-task03/*-audit/` 的 scope、连续扫掠、480 pose case 与 42 scalar case 中。`MotionOracleProbe` 另证生产 Core sampler。旧新结构、hatch/材料配额、原流程 ON/OFF 等价、真实客户端遮挡/镜像/端口与卸载恢复均为独立运行门；HTML 和本页不签通过。
