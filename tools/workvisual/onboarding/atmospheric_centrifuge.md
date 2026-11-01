# AtmosphericCentrifuge 接入

业务 owner 为 `MTEAtmosphericCentrifuge`；芯片、模式、原 4/16 并行和多输出分支保持本机负责。只读 `MachineVisualHost` 接 `MachineVisualDefinition.get("atmospheric_centrifuge")`；NO_RECIPE 不统一解释成缺料。

新结构为批准的近圆 9×5×9、253 单元候选，双层各 9 个单位块扇叶共 18 个成员，同一组同相绕 `(0,0,4)` 连续旋转，周期 9666 ms。原 7×4×7 结构另保留静态识别。不得把底层抬高、缩小单位块或在原 7 格包络保留全尺寸连续旋转。

来源为 TASK03 批准的 new rawLayers/new blocks/group；`pipeline.py export-motions` 输出确定性定义并与生产 JSON 逐字段比较。原 gearbox、glass、frame 按 `tokenMaterials` 的 GT block/meta/tier 固定。几何充分界为 moving √6.5、fixed √8.5，径向余量 0.3659661906262581；Y 方向的面接触不等于正机械公差。

全圆扫掠的实际世界空位未清时，关有关显示组并恢复原静态方块；OFF、快照过期、卸载/重载同样走代际退役。atlas/context 与静态 mesh 可见性为客户端生命周期，不写业务 NBT，不移动世界块。

本机完整运动成本为 18 成员/1 组，纳入普通机 LOW 128/16、STANDARD 512/64、HIGH 1024/128 的全局成员/组额度。120 客户端 tick 是有竞争时的预算轮转租期，与快照新鲜度分开。r3 恢复中的显示继续持额，须同 maskEpoch 的静态可见上传，或同 world/manager/section 的原生 GPU 退休确认后释放；important 入队和超时不是 ACK。412 项预算与 14 项 foreground guard 的 source-method 测试不是 FML/GL 或 actor ACK 证明，旧客户端结果不转签 r3。材料极小值理论 8 受实际原生仓口上限限制，可达值为 125；该原生合同保持 UNSUPPORTED，不能记成通过。

source/JAR 审计、480 case 全角点与 `MotionOracleProbe` 分层留证。新旧 shape、材料/hatch 配额、透明排序、镜像绕序/法线、端口、旧存档迁移与实际世界障碍仍须真实候选验证；纯径向 PASS 不签这些门。
