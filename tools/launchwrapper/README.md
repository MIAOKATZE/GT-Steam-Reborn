# Classic LaunchWrapper 负缓存回归探针

本工具针对 `PersonalWorldProvider` 的早期 Mixin 探测，使用真实 LaunchWrapper 1.12、UniMixins 0.3.1、Forge 1614 和 PersonalSpace 1.0.41 的字节码。每次启动独立 Java 8 小进程，堆上限 256 MiB，不启动 Minecraft，不改变游戏实例或 Java26 默认配置。`tools/` 下的 helper 不进入生产 jar。

本机默认依赖为 Gradle 的 Zulu8 JDK、Gradle 依赖缓存、`build/client-testlab-task03-gt265/mods/` 与 `E:/CodeGT/plan/stage07-20261101/testlab-launcher/libraries/`；可用 `--jdk` 指定其他现成 Java8。没有安装/下载步骤。

```powershell
python -X utf8 tools/launchwrapper/run.py
python -X utf8 tools/launchwrapper/run.py --pipeline early
python -X utf8 tools/launchwrapper/run.py --pipeline late
python -X utf8 tools/launchwrapper/run.py --pipeline absent
python -X utf8 tools/launchwrapper/run.py --pipeline late --side SERVER
python -X utf8 tools/launchwrapper/run.py --pipeline absent --side SERVER
```

- 默认模式复现原异常。真实 MixinService 查询尚未入 classpath 的目标，随后执行 Forge 原生 `ModCandidate.addClassEntry`、`ModClassLoader.addFile`、`clearNegativeCacheFor`。检查斜杠候选名无法清除点分负缓存，目标资源后来存在而 `getClassBytes` 仍为空，真实 `findClass` 抛出 `ClassNotFoundException → NullPointerException`。进程通过表示这个旧缺陷已稳定复现。
- `early` 经正常 `Launch.main → MixinTweaker`，仅抽取原早期清单中的这一个 Mixin，复现准备阶段和随后实际目标解析的失败；避免把整个 GTSR 配置和全部模组混入最小用例。
- `late` 经相同真实启动链，消费生产 `GTSRLateMixinLoader`、生产 late JSON 和原 Mixin。按 GTNHMixins `CONSTRUCTING` hook 的同一 config/list/register/prepare 接缝注册；真实 PersonalSpace class 产生 3 个 handler、4 个实际调用点，并能被 LaunchWrapper 解析。
- `absent` 不将 PersonalSpace jar 加入运行时，生产 loader 返回空清单；准备完成后目标既无负缓存也无 invalid 标记。

完整日志、逐用例 JSON、输入 SHA 和变换后的目标 class 落在 `build/launchwrapper-diagnose/`。`--out` 可指定独立证据目录。正式构建后可对生产字节码再运行，例如：

```powershell
python -X utf8 tools/launchwrapper/run.py --pipeline late --gtsr-jar build/libs/<候选生产jar> --out build/launchwrapper-candidate
```

`--gtsr-jar` 从该 jar 提取两枚生产 class、早晚配置及 refmap，不把整 jar 的 manifest 自动启动其他 Mixin。默认 source 模式只用 javac 编译本问题的两枚生产 class。

隔离替身仅为 Minecraft 的 `World`、`WorldProvider` 和 `CriticalNightClock`，用于在不运行游戏的情况下支持实际 PersonalSpace 字节码变换和类解析。未创建替代 PersonalWorldProvider，也未重写 PersonalSpace jar。此探针不覆盖完整 FML ASM 扫描/`CONSTRUCTING` 事件分发、生产夜间时钟逻辑或实际客户端画面；完整游戏启动还需从日志确认 `Loading mixins from ILateMixinLoader`、`Preparing mixins.gtsr.late.json (1)` 与实际 `Mixing`。

这里的 `SERVER`/`CLIENT` 是真实 MixinEnvironment side，不等于启动了专服/客户端。当前本机是 Java8u482；用户报告为 Java8u51，不声称已在该旧补丁版 JVM 上运行。
