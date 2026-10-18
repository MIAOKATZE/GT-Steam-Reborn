# v1.21.46 枢纽流体仓室准入回归

只测试蒸汽枢纽阵列和蓄水枢纽阵列的结构流体 I/O 准入。奇点钻井枢纽的独立仓室兼容设计不在此限制范围内。

```powershell
python tools/tests/hub46/verify.py --jar build/hub-release-v12146/baseline-dev.jar --out build/hub-release-v12146/verification/before
python tools/tests/hub46/verify.py --jar <本次构建的dev.jar> --out build/hub-release-v12146/verification/after
```

默认只从指定产品 jar 加载产品类；不把 `build/classes/java/main` 放入 classpath。需要已解析的 GT205 依赖、patched Minecraft 类和本机 Gradle JDK17。`--source-overlay` 仅供诊断，不能作为发布候选的 jar-only 验收。

原生检查共 78 个断言：

- 两台机器的实际 C 结构元素拒绝四种奇点蒸汽/流体输入输出仓。
- 专用仓 adder 与原生 HatchElementBuilder 建造物品过滤器拒绝奇点仓；物品解析有独立正控。
- 四种专用 HubInput/HubOutput 仍可作为 C 位，且附上正确控制器。
- A 存储位拒绝奇点仓；加入奇点仓不会增加容量，专用存储单元仍可加入并增加容量。
- 奇点仓的远程节点类型解析仍保持有效。

修前基线预期退出码 1，`hub-gate.json` 应是 78 个断言中 10 个失败：四个 C 位误接纳、四个建造候选误接纳，以及蓄水枢纽两个通用 adder 的近亲子类误接纳。修后应退出码 0、78 个断言全通过。`verification.json` 记录产品/GT/fixture SHA256、完整 classpath 与是否存在 overlay。

测试复用 v33 的 LaunchClassLoader、Unsafe 分配和两处平台 gate；原生 GT/StructureLib、产品结构元素、adder、过滤器及容量方法真实运行。它不启动完整 Forge 服务端，不覆盖客户端 NEI/GL 展示、网络传输、性能或 98 台机器矩阵。建造候选验证覆盖原生物品谓词，不声称已运行 NEI 客户端。
