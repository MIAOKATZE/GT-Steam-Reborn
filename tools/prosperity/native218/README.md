# v2.1.8 场景血条原生采证

`prepare.py --compile` 仅在没有共享 native lease 时，将专属 listener 注入既有 owned QA helper，安装到 `build/client-testlab-task03-gt265`，并在 `build/prosperity-v218-native-qa` 生成专属 runner；不会启动 Minecraft，不修改生产 jar。运行环境固定为已验证的 GT265、GTNH-Core 2.9.99、MUI2 2.3.94、MiaoCore 0.2.4、Java26，直接保留 v216 launch spec。

启动由主代理协调，运行命令：

```powershell
python -X utf8 tools/prosperity/native218/prepare.py --compile
python -X utf8 build/prosperity-v218-native-qa/run-qa.py --workspace build/prosperity-v218-native-qa --client-sandbox build/client-testlab-task03-gt265 --jar E:/CodeGT/.worktrees/task03-gtsr-clean/build/libs/gtsr-v2.1.7.jar --mode prosperity218 --run baseline217-hud-20261103a --language zh_CN --hidden-desktop --heap-gib 4 --reserve-gib 2 --deadline 900
```

runner 强制 private Windows desktop，沿用共享 lease、内存门槛和只回收 owned 子进程的生命周期。每次使用全新 run ID。不得触碰用户现有进程、原测试实例或切换用户桌面。

baseline 默认断言慢 tick 导致旧缓存失效；修复候选运行时追加 `--expect-stable-pause`，改为断言暂停超过 60 个真实 client tick 仍保留 owner。两种模式均断言真实离场清除，不能把 baseline 复现 PASS 写成产品无故障。

候选可追加 `--reuse-owned-world Mui2Audit-baseline217-hud-20261103a --probe-legacy-chest`，复用已放置的专属场景并检查一个真实 direct 封印箱的旧存档迁移。该项只把此实际 site/node 的未开奖 TE 通过旧 NBT 设为 tier V/contract 1，随后等自然服务器 tick 迁到 contract 2，再做保存读回及五 tick 稳定性检查；不直接调用更新/私有迁移，不证明完整开箱结算。不能对 baseline 217 启用此候选检查。

夹具使用真实繁荣 WorldServer，登记相隔 256 格的工厂、战场，然后逐 owner chunk 调用生产 `RemasterWorldgen.placeChunk` 放置真实几何、节点与 Boss。它证明人工登记生产场景夹具的运行，不证明自然选址生成。正常内部站位、超出成员高度、返回、内部边缘、离场重进分别记录。最后明确暂停 owned 集成服务端线程 4500 ms，客户端时钟不变，用于分离低 TPS 下的缓存租约失效与普通站位行为。

`native-events.jsonl` 记录生产服务器状态、真实 `gtsr_encounter` S2C 包与暂停边界；`checks.json` 记录客户端 SCENE 世界身份、剩余 ticks、加载的真实 Boss、HUD overlay 事件及阶段检查。截图来自完整 GL framebuffer。LocalChannel 只宣称原生集成服通信，不宣称 TCP 字节或真实物理输入。检查与截图只有实际完成运行后才可作为验收证据。
