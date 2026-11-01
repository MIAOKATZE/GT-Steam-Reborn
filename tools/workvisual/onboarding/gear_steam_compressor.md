# GearSteamCompressor 接入

业务 owner 为 `MTEGearSteamCompressor`，压力蒸汽、冷却、配方与原付款/产物流程保持本机所有。只读 `MachineVisualHost` 接 `MachineVisualDefinition.get("gear_steam_compressor")`，不接入临界施工、升级或通用业务 executor。

原 170 单元 shape 不变，上下两组各 15 个真实 gearbox 单位块相向沿 Y 0..0.15，最低板间距 0.70。中间 15 个原 skip 单元须按实际世界占用门控，不能拆玩家方块或改成型条件；侧面面接触明确保留，不冒称四周正公差。

作者来源为批准的 TASK03 new motionGroups/blocks，正式出口 `pipeline.py export-motions`；材料元数据来自实际 Java token 与 GT265 block/meta/tier，对应 Casings2:2/3 gearbox。世界后端通过实际 native 方块原材质绘制，仅显示矩阵往复，不移动碰撞/交互/端口。

一组缺匹配方块、空间、快照或渲染就绪条件时只退役该组显示；OFF 恢复静态。transient 视觉状态按安装/连接代际管理，不新写业务 NBT。卸载、同坐标换机、转向、资源/context 重载仍各有运行门。

本机完整成本为 30 成员/2 组；普通机全局 LOW、STANDARD、HIGH 分别保留 128/16、512/64、1024/128 成员/组。120 客户端 tick 的预算租期仅在竞争时触发轮转，不能拆组凑数。r3 的旧静态恢复期间仍持额，须同 maskEpoch 可见上传，或同 world/manager/section 的原生 GPU 退休确认后释放；important 重建入队不算完成。OFF/降档不提前释放旧持额。412 项预算和 14 项 foreground guard 源码合同不等于 FML/GL、actor ACK 或 r3 实机预算通过。

离线 sweep 对两组同相相对轨迹连续求交；pose oracle 含 OFF、四向与镜像，scalar probe 读真实候选 Core sampler。实际 skip 空位、cover/hatch、客户端静态隐藏/恢复与生产等价尚须真实候选签收。
