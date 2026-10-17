# P6 工具与剧情客户端计划

正式可复用套件是 `tools-lore.json`，787 步、6 组。文字诊断缩减版另存为 `tools-lore-text-focused.json`，371 步、4 组；它不能替代正式套件。组来源、源文件与组内容 SHA-256、变更及静态检查在 `tools-lore-provenance.json`。

| 正式顺序 | 内容 | 来源 |
|---|---|---|
| 1 `p6-wand-real-air-profile-save-load` | 正常协调棒空气入口、真实 sneak/held、profile C2S、NBT 保存加载及再操作 | R9 不可变 plan 的原组，145 步精确保留 |
| 2 `p6-journal-real-authorized-starter` | 正常物品/S2C 授权 starter、四 tab 可见性/真实矩形、正文视口及原生测高 | R10 原组，补最新文字测量断言，78 步 |
| 3 `p6-journal-local-full-permission-long-text-visual` | 独立本地全权限视觉 fixture、完整 keys、四 tab 真点击、长文、资源重载、小画布和末段可达 | R10 原组，补最新文字测量断言，306 步 |
| 4 `p6-navigator-real-compass-dimension78-gate` | TestLab 拒绝、真实 78 维资格/S2C、搜索/空结果、资源重载；不创建昂贵 LocateJob | R9 不可变 plan 的原组，60 步精确保留 |
| 5 `p6-altar-real-owner-board-reading-close-no-activation` | 真实主世界 core/index/board、站立平台/距离/live TE、正常阅读、资源重载/关闭不激活、服务器 Node 替换自动关窗、原 NBT 恢复 Board 正常重读 | R10 已通过的原组，177 步精确保留 |
| 6 `p6-altar-owned-fixture-cleanup` | 独立清理本次 board/core/session，恢复平台原块/meta、index、玩家数据并返回 TestLab | R10 已通过的原组，21 步精确保留 |

正式 SHA-256：`05380d74a12ed19b43eb32d3e362ac88ee366701a6c713cf9126accc48b9eb4a`。

文字 focused SHA-256：`b3e11a74c074de1e1c48de6f94a6fa215c65ff0833bf337b90befe5d640b778e`。它保留 Journal 两组的文字诊断及正常祭坛共享段落控件的一次阅读/清理，主动省去已通过的 Wand/Nav 与完整祭坛替换事务。R11 实际运行的是独立冻结副本；其路径及 SHA 另列于 provenance，不能把源 focused 的 hash 冒充运行副本 hash。

这些 JSON 由当前 `UiQaScript.java` / `Mui2ClientQa.java` 执行，要求正确 sneak SRG `field_78899_d`、动态完整清空搜索草稿，以及 `geometry` 的 `assertTextFit` / `assertLastVisible` 功能。正文同时核对实际矩形高度、最终宽度下 native `getDefaultHeight`、真实 FontRenderer 换行高度、段落不重叠及底部可达。原生 `scrollBy(+1)` 的返回值表示中间值是否被 clamp；处于内容末端时预期为 true，不能反解为“滚动失败”。

本次仅恢复/合成源计划并进行 JSON、组唯一、变量先定义后引用、来源组精确保留检查；没有重新执行整套最终候选。Wand/Nav 来源组在 R9 通过，祭坛生命周期/清理来源组在 R10 通过；这些 run 的整体状态仍可能因其他组失败而为 FAIL。Journal 的最新 font 修复结论应引用对应 focused 的不可变 receipt/PNG/原生测量证据。**不能把新合成的 787 步套件写成“最终候选整套新跑 PASS”。**

不要从早期 `build_plan.py.tools_and_lore()` 草稿覆盖此正式 JSON；它没有收齐后续原生 TE 生命周期与文字测量回归。若未来重生成，应逐组保留以上契约并更新 provenance。
