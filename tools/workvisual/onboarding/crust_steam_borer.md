# CrustSteamBorer 接入

业务 owner 为 `MTECrustSteamBorer`，维度、蒸汽/采矿周期、原效率、产出与存盘语义仍由本机负责。只读 `MachineVisualHost` 接 `MachineVisualDefinition.get("crust_steam_borer")`；与 4 个真火箱的适用前景分层，不能把显示位移当采矿事实。

原 119 单元 shape 不变。中央原 7 个 C 管和顶部 4 个 F 框架共 11 个单位块同组，沿 Y 向上 0..0.60 再归位。目标是明显位移，周期仍受批准定义约束；全程重算而不继承旧 0.30 回执。固定支承与顶部 `(0,10,2)` 均纳入连续/真实占用门。

来源为 TASK03 最终范围和批准 new group/blocks；正式出口 `pipeline.py export-motions`。token 精确对应 pipe Casings2:12/13、frame Bronze/Steel、firebox Casings3:13/14；真实渲染使用匹配 native 方块。来源、工具、材料与当前资源分别进入 authoring/runtime manifest。

世界占用、成员身份或显示条件不能满足时恢复安全静态，报告该动作未运行通过，不缩块、不穿模或改外框。OFF 与实例卸载按代际释放显示，原业务 NBT 不为视觉新建账本。

本机完整成本为 11 成员/1 组，服从普通机全局 LOW 128/16、STANDARD 512/64、HIGH 1024/128 的成员/组保留额。预算租期为 120 客户端 tick，竞争时轮转，不能缩块或拆组换额度。r3 在退役、OFF、降档后持续为恢复持额，直到同 maskEpoch 可见上传，或同 world/manager/section 原生 GPU 退休确认才释放。精确 section 重要重建入队不等于 actor ACK。412 项预算和 14 项 foreground guard 的源码合同不等于 FML/GL、GPU 恢复、百机性能或 r3 客户端通过。

连续 geometry contract 按 0.60 已重新生成；四向/水平镜像的全角点 oracle 与 Core sampler probe 为独立纯合同证据。实机支承、顶部阻挡、火箱可见面、静态恢复、旧存档与真实业务 ON/OFF 等价仍须另签。
