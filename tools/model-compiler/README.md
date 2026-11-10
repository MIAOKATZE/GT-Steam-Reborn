# 遭遇模型编译

`compileRigMeshes` 使用纯Java8工具，把 `src/main/resources/assets/gtsr/liminal/*/models/*.obj` 编译到 `build/generated/rigmeshes`。`processResources` 将发行资源中的OBJ替换为 `.rigmesh`，武器OBJ不在此范围。源OBJ和源码包仍保留原文件，禁止用生成文件覆盖作者来源。

格式为 `GSRM`、版本1、源OBJ的SHA256、原始double位值字典、坐标及UV表、按首次实际面出现排序的分组和多边形索引。UV只执行原读取器的 `1-v`；重复UV按两个double的原始位去重，不合并正负零。读取后按原扇顺序生成三角形索引，法线继续由原坐标和运算顺序计算，不降精度、不减面。

构建工具先验证整套模型的往返及确定性，再发布生成文件，清理自身输出目录中的失效网格。模型数量从实际源文件推导；当前40份独立黄金由验证合同限定，未来有意调整名册时须同步审核黄金。

运行模型由`liminal/active-models.txt`确定；退休作者源和旧黄金仍保留，但不进运行包。`sounds/inactive-sounds.txt`是构建期精确过滤清单，旧事件别名仍保留。编译任务还检查所有方块、lore图标及信标/指针/深渊桶的PNG每帧16像素，防止旧作者工具重新生成32像素后无意回退；不改变武器、怪物、GUI或机器物品图标。

`check` 自动执行 `verifyRigMeshContracts`：手写夹具、非法输入边界及独立旧OBJ黄金对照。黄金位于 `src/test/resources/encounter/rigmesh-golden.tsv`，可用 `generate_golden.py` 重新生成；该Python工具仅用于独立复核，不是日常构建前置。原生GL对照工具在 `native/`，运行环境与证据独立于产品包。

运行时OBJ是资源包覆盖权威：资源管理器将OBJ路径报告为不存在时使用编译网格，选中OBJ流后的格式或读取错误不静默回退。rig和PNG维持原路径。编译网格只减少包内表示和CPU常驻网格数据，不承诺显存、帧率或任意资源包兼容；真实覆盖及GL范围以实际验证回执为准。
