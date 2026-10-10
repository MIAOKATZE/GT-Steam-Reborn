# 隐藏 OpenGL 验证

在主构建完成后运行（不会运行 Gradle，也不会启动服务器或显示客户端窗口）：

```powershell
python tools/critical/gl_fixture.py --jar build/libs/<候选>-dev.jar
```

使用本机 Gradle 已缓存的 Java 17、LWJGL 2 Windows natives 与真实 patched Minecraft `Tessellator` / `RenderBlocks`。真实 NVIDIA Pbuffer 上执行候选 dev jar 的 `CriticalControllerRenderer.renderModel`、`CriticalFX.render`、`GuiCriticalController.drawGuiContainerBackgroundLayer` 和 `CriticalMaterialRenderer.renderWorldBlock`，PNG 来自 `glReadPixels`，不是设计预览。候选 jar 的 SHA-256 在执行前后核对，生产类 CodeSource 必须匹配该 jar。

结果位于 `build/critical-machines-v12115/gl`：`binding.json` 记录候选/fixture替换范围，`checks.json` 记录22模型、九机idle/工作阶段、八帧纹理矩阵偏移、显示列表重载释放、GL错误与性能样本。每机30个暖缓存样本包含 `glFinish` 等待实际GPU完成，仅表示该视角/工作相位的模型+FX绘制耗时，不能换算完整Minecraft FPS。

MC世界启动边界由fixture代替，材料调色板读取及普通Voxel的block身份不依赖真实注册。静态检查另从候选jar加载真实 `CriticalMaterials$MaterialBlock`，使用单立方体 `IBlockAccess` 与真实 side/top/emission PNG 的三格atlas；它验证生产材质渲染与bounds复位，未模拟MC整张atlas拼接和真实加载区块。GUI只调用真实背景层，使用72槽与50%进度telemetry fixture；字体、按钮、物品图标、tooltip、鼠标、网络窗口未测。

另直接调用真实 `renderTileEntityAt` 21次，验证暂停批次仍使用build模式与冻结50%相位，环境秒数继续推进，并验证其GL blend/alpha/depthMask、矩阵栈与亮度恢复。Minecraft原版TESR dispatcher调用数为0，玩家坐标与世界来自fixture，因此仍不能宣称完整游戏内验证通过。

`CriticalClientStateProbe` 另外加载候选jar的真实控制器和 `CriticalGroupVisibility`，仅fixture化时间/方块世界：覆盖生产description packet字段、客户端入包锚点、暂停/缺电/禁用/结构失效/周期封顶，以及九机完整动态group扫描、缺块失效、每tick最多512次检查、拆除/维修期间保留完整group和结构身份切换。静态probe同时直接调用生产Block与ConstructionOnlyItemBlock，确认两条手动放置入口返回false。

`CriticalNightProbe` 对候选真实TimeMessage执行32组Netty ByteBuf往返，直接测receiveClient/time/isNight、生产TimeHandler真实MessageContext连接token、clientdrain与unload清理。覆盖当前连接最后包、跨维度拒收、同维度换World隔离、重连旧消息拒收，以及旧connection迟到回调不能删除新connection排队数据。WorldClient时间/规则和NetHandlerPlayClient连接对象采用fixture，FML Loader仅以Unsafe分配启动边界来初始化真实SimpleNetworkWrapper；未替换消息、时钟或drain实现。停止预测由服务端同步的advancing/timeRunning标志控制；真实WorldMixin织入与维度GameRules tick行为归原生服务器测试，本工具只用真实WorldProviderSurface计算预测天角。
