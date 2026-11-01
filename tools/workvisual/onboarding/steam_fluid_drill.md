# SteamFluidDrill 接入

业务 owner 为 `MTESteamFluidDrill`，蒸汽周期付款、模式、效率及两次取整仍保持原实现。只读 `MachineVisualHost` 接 `MachineVisualDefinition.get("steam_fluid_drill")`，装饰节奏不当产量或付款进度。

原 30 单元结构不变。中央原 4 个 C 管和顶部 2 个 E 框架共 6 个单位块为一个轴组，沿 Y 向上 0..0.30 再归位；其余框架、底座、齿轮箱不动。全程扫掠包含顶部 `(0,6,2)`，原 skip 不自动成为净空保证。

源为 TASK03 的原 shape/批准中央组；`pipeline.py export-motions` 输出同一份生产定义。native pipe 为 Casings2:12/13，frame 为 Bronze/Steel meta。manifest 独立记作者输入与运行字节。无已证排汽口、无新粒子或火箱前景。

顶部/扫掠邻域有玩家占位或未知、成员不匹配、显示资源未就绪时关整组并恢复静态，不改生产与成型。OFF、实例退役、连接/世界/维度变化按瞬态代际清理，不增加业务存档字段。

本机 6 成员/1 组计入普通机 LOW 128/16、STANDARD 512/64、HIGH 1024/128 的全局成员/组保留额。预算轮转租期是 120 客户端 tick；竞争时到期退役，r3 恢复期持额至同 maskEpoch 的静态可见上传，或同 world/manager/section 原生 GPU 退休确认。OFF 不新录取，也不提前归还尚未确认的旧额。important 入队不算 ACK；412 项预算与 14 项 foreground guard 源码合同均不执行候选 FML/GL，不证明 actor 恢复回调，r3 客户端另验。

连续 swept AABB、480 pose case 和生产 sampler 数值分开留证；`worldOccupancyStatus` 仍是 NOT_RUN。实际世界阻挡、所有合法朝向、端口/cover、卸载重载与业务等价须在同一候选真实环境验证。
