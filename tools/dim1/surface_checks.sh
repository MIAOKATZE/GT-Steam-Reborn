#!/bin/bash
# P2「表层上收框架 + 降级态不铺 + 转置闭合」、P3「L2 高度/哈希单一真值」、
# P4「L4/L5 地表门统一 + dim78 装饰门缺陷修复」与
# P5「密度 γ：H-3 轮廓/落块双上限 + 竖向件摘出 + H-2 窗重复上限」与
# P6「H-1 群系带分层（macro 64 + micro 16）+ L6 城门（锈蚀草原带）」离线断言与回归的一键复跑入口。
#
# 为什么需要脚本：这些片的全部判据都跑在 MC-classpath 的离线 JVM（任务包禁止 gradlew 全量构建），
# 配方见 plan/investigation/v12030-hotfix-replaceruntime-report.md §3。两个已知坑已由
# SurfaceHarness / ReplaceSurfaceRuntimeCheck 在代码内注释：
#   ① Unsafe.putObject 之前必须先 Class.forName("net.minecraft.init.Blocks")；
#   ② new Block[65536] 未写入槽位是 null 而非 Blocks.air。
#
# 用法：
#   bash tools/dim1/surface_checks.sh              # 新断言 + 既有回归
#   bash tools/dim1/surface_checks.sh --parity     # 追加对拍与量化判据：
#                                                  #   [4] chunk 逐字节对拍（512 chunk）
#                                                  #   [5] 高度/列扫/哈希站点逐位对拍（≥1 万点）
#                                                  #   [6] 仅注释改动的文件字节码等值证明
#                                                  #   [7] 单一真值自检 RED→GREEN
#                                                  #   [8] P4 门 A/B 量化（BASE=缺陷态 vs AFTER）
#                                                  #   [9] P4 地表门 RED→GREEN（两条单变量破坏）
#                                                  #   [10] P5 密度 γ：回退摘要对拍 + 非散布零漂移
#                                                  #        + ContourBudget/RegionRepeatCap RED→GREEN
#                                                  #   [11] P6 群系带分层与城门：rollback 逐字节对拍
#                                                  #   [12] P7 结构放置契约：真地形 16384 chunk 跑
#                                                  #         PlacementContractCheck all（T2..T5 四张表）
#                                                  #         + 影子树单变量 RED→GREEN（p7red 两条：
#                                                  #           outpost/机器各把落块真值退回 return true）
#                                                  #   [15] P8 城外废墟族：非本片路径零漂移对拍
#                                                  #         （BASE=temp/p8-base）+ 散布档摘要对拍
#                                                  #         + 真地形密度对照 + 数据件跨进程双跑 SHA
#                                                  #         + 四条单变量影子 RED
#                                                  #   [16] P9 自定义生物层（L7）：非本片路径零漂移
#                                                  #         对拍（BASE=temp/p9-base = e3e14a6 快照，
#                                                  #         含"BASE 编译零 error"硬门槛）+ 散布档
#                                                  #         摘要对拍 + 六条单变量影子 RED
#                                                  #         （生物<b>行为</b>不在此测：无头冒烟对实体
#                                                  #          无效，见 CreatureSpawnAuthorityCheck 注释）
#                                                  #   [14] P5b 散布成簇（plan §7.2）：BASE 零漂移对拍（含
#                                                  #         BASE 编译零 error 硬门槛）+ P5 均匀档逐位退化
#                                                  #         摘要对拍 + 双跑 SHA + 两条成簇参数影子 RED
#                                                  #   [17] P14 tpdim 群系定位：512 chunk 逐字节对拍
#                                                  #         （BASE=temp/p14-base 开工前快照 9735cef，
#                                                  #          含"BASE 编译零 error"硬门槛 + Config 无
#                                                  #          tpdim 新键反假绿钉；快档断言在 [2h]）
#                                                  #   [18] P13 死代码清扫：512 chunk 逐字节对拍
#                                                  #         （BASE=temp/p13-base 开工前快照 04ede7c，
#                                                  #          含"BASE 编译零 error"硬门槛 + 两面反假绿：
#                                                  #          BASE 仍含被删成员声明 / AFTER 已净；
#                                                  #          快档侧 P13 另修 asset 行 echo 粘连并补
#                                                  #          RosterIntegrity/S8Registry 两条 run 挂点）
#                                                  #   [13] P7c 结构 H-2 语义改判：非本片路径零漂移对拍
#                                                  #         （BASE=temp/p7c-base 开工前快照）+ CHAIN 硬门槛
#                                                  #         RED→GREEN + 两条新规则的 4 个单变量 RED
#                                                  #         判据 1 的"改前实现返回 true"另由 temp 探针
#                                                  #         在 d5b7ca5 影子树上出证（见 p7b 证据文档 §1）
#                                                  #        + 四张数字表（T1..T4）+ 三条单变量 RED→GREEN
#
# ── P6 口径变更（读旧判据前必看）──
# P6 给 dim78 加了两个新自由度：macro 群系带尺度（默认 64）与城门条件档（默认 1=锚点带）。
# 两者都会改变 dim78 的群系面与"哪些 chunk 属城窗"。实测结论（本轮跑过才写进注释）：
#   · [4]/[5]/[8] 不需要包装——它们的 BASE 树是「cp -a 当前树 + 只还原 P4 的 4 个 placer」，
#     两侧都带 P6 代码，差值里只隔离 P4 那一件事（实测 diff_lines=0 / rd=0 / 非散布前序项相等）；
#   · [10] 与 [10b] 必须在 AFTER 侧把 P6 的三键设回改造前口径（`BiomeBandHierarchyCheck rollback
#     <目标工具 main> …`：反射写 Config，BASE 树无该键则登记并跳过），否则会把「P6 的城窗重分布」
#     误报成「P5 判据 3/5 不成立」（未包装时实测 3 条红 + 92 行漂移）；
#   · [10] 的 BASE 编译面还要同时还原 P6 的 3 个生产文件，否则 P5-BASE 编不过（实测 4 error）；
#   · rollback 包装必须在 Dim78ScatterDensityCheck.bootstrap() <b>之前</b>委托目标 main：bootstrap 会
#     二次创建方块实例，而 SurfaceByteParityDump 的 per-chunk sha 覆盖"标签+meta"，二次实例化会让
#     连 dim79 都跟着变（实测 774 行假漂移）。
# P6 自己带来的行为变化（带尺度=64 + 门=1）的对账在 [11]：暴露增量表 T4 与 P5 §10 的预算比对。

#
# BASE 快照：temp/p3-base/all/src/main/java/… 下<b>本片（P3）开工前</b>的生产文件副本
# ——任务包口径「BASE 取开工前快照（片内口径）」。P2/P3 期用的是 temp/p2-base、temp/p3-base，
# <b>都不适用于本片</b>：P3 的快照里装饰门已经是"改造前"的形态之外还带着 P3 之前的列扫实现，
# 拿它当 P4 的 BASE 会让 [5] 命中实现差异（rd≈8）而 [4] 又混入 P3 的改动，判据 1 直接失真
# （实测过一次：p3-base 当 P4-BASE 时 [4] 报 20 行差异、[5] 报 rd=8）。
# 缺该快照时 --parity 直接判失败（不许用"看起来一样"替代对拍）。
# P5 另建自己的 BASE：temp/p5-base/all/src/main/java/…（= P5 开工前快照，即 P4 终态）。
# [4]/[5]/[8]/[9] 继续用 temp/p4-base（P4 判据对象是"装饰门"，与 P5 无关），[10] 用 p5-base。
#
# 退出码：0 = 全绿；非 0 = 有工具变红（每行都打 EXIT= 便于机器读）。
set -u
cd "$(dirname "$0")/../.." || exit 1
OUT=temp/p4-surface
CP_FILE=temp/p1-cp.txt
G="$HOME/.gradle/caches/modules-2/files-2.1"

jar_of() { # jar_of <group-dir> <jar-file-name>
  local d
  d=$(find "$G/$1" -name "$2" 2>/dev/null | head -1)
  [ -n "$d" ] && cygpath -w "$d"
}

if [ -f "$CP_FILE" ]; then
  CP="$(cat "$CP_FILE")"
else
  JARS="$(jar_of com.google.guava 'guava-17.0.jar');"
  JARS="$JARS$(jar_of org.apache.commons 'commons-lang3-3.3.2.jar');"
  JARS="$JARS$(jar_of org.apache.logging.log4j 'log4j-api-2.0-beta9-fixed.jar');"
  JARS="$JARS$(jar_of org.apache.logging.log4j 'log4j-core-2.0-beta9-fixed.jar')"
  CP="$(cygpath -w build/classes/java/main);$(cygpath -w build/classes/java/patchedMc);$JARS"
fi
# P9：渲染器薄壳继承 RenderLiving ⇒ javac 与运行都需要 org.lwjgl（原版 client 渲染链引用 GL11）
# 与 com.google.code.gson（Render.<init> → RenderBlocks.<init> 里有 gson 类型字段）。
# 只加 jar，不加资源根；资源根（原版 assets）只在 P9 的 runres/[16] 按次加，避免给别的工具
# 改出新的 getResource 命中面。
extra_jar() { find "$G/$1" -name "$2" 2>/dev/null | sort | head -1; }
LWJ="$(extra_jar org.lwjgl.lwjgl 'lwjgl-2*.jar')"
GSON="$(extra_jar com.google.code.gson 'gson-2.2.4.jar')"
[ -z "$GSON" ] && GSON="$(extra_jar com.google.code.gson 'gson-2*.jar')"
[ -n "$LWJ" ] && CP="$CP;$(cygpath -w "$LWJ")"
[ -n "$GSON" ] && CP="$CP;$(cygpath -w "$GSON")"
MCRES=build/resources/patchedMc

D=src/main/java
# 本片 + P1/P2 涉及的生产文件（相对 src/main/java 的路径）。AFTER 与 BASE 编译**同一文件集合**，
# 只有本片的 BASE 快照文件内容不同——否则 BASE 侧会漏编 GTSRWorldChunkManager 之类而回落到
# build/classes 里的陈旧 class（实测会静默改变行为，对拍因此失真）。
# P3 追加：两个 *TerrainProfile、GTSRWorldgenHash、五个自带列扫的 placer/scatter、
# CityPlanner/CityVariants（手搓哈希站点）——它们都参与 heightAt/地表/城市掷骰。
REL="com/miaokatze/gtsr/common/dimension/framework/SurfaceGate.java
com/miaokatze/gtsr/common/dimension/framework/GTSRBiomeBase.java
com/miaokatze/gtsr/common/dimension/framework/GTSRBiomeAuthority.java
com/miaokatze/gtsr/common/dimension/framework/GTSRChunkProviderBase.java
com/miaokatze/gtsr/common/dimension/framework/GTSRWorldChunkManager.java
com/miaokatze/gtsr/common/dimension/framework/BiomeZoneSelector.java
com/miaokatze/gtsr/common/dimension/framework/structure/GTSRWorldgenHash.java
com/miaokatze/gtsr/common/dimension/framework/structure/PlacementGate.java
com/miaokatze/gtsr/common/dimension/prosperity/biome/ProsperityBiomes.java
com/miaokatze/gtsr/common/dimension/prosperity/ChunkProviderProsperityRuins.java
com/miaokatze/gtsr/common/dimension/prosperity/ProsperityTerrainProfile.java
com/miaokatze/gtsr/common/dimension/prosperity/ruins/ProsperityWorldGenerator.java
com/miaokatze/gtsr/common/dimension/prosperity/ruins/ProsperitySurfaceScatter.java
com/miaokatze/gtsr/common/dimension/prosperity/ruins/RuinedMachinePlacer.java
com/miaokatze/gtsr/common/dimension/prosperity/ruins/ProsperityDecorPlacer.java
com/miaokatze/gtsr/common/dimension/prosperity/ruins/ProsperityOutpostPlacer.java
com/miaokatze/gtsr/common/dimension/prosperity/ruins/city/CityPlanner.java
com/miaokatze/gtsr/common/dimension/prosperity/ruins/city/CityVariants.java
com/miaokatze/gtsr/common/dimension/shattered/biome/ShatteredBiomes.java
com/miaokatze/gtsr/common/dimension/shattered/ChunkProviderShatteredGrounds.java
com/miaokatze/gtsr/common/dimension/shattered/ShatteredTerrainProfile.java
com/miaokatze/gtsr/common/dimension/shattered/ShatteredDecorPlacer.java
com/miaokatze/gtsr/common/dimension/shattered/WorldProviderShatteredLands.java
com/miaokatze/gtsr/common/dimension/prosperity/entity/GTSRCreatureRoster.java
com/miaokatze/gtsr/common/dimension/prosperity/entity/GTSRCreatureRegistry.java
com/miaokatze/gtsr/common/dimension/prosperity/entity/GTSRCreatureRenderers.java
com/miaokatze/gtsr/common/dimension/prosperity/entity/EntityGearPigeon.java
com/miaokatze/gtsr/common/dimension/prosperity/entity/EntitySteamFirefly.java
com/miaokatze/gtsr/common/dimension/prosperity/entity/EntitySlagRidgeHunter.java
com/miaokatze/gtsr/config/Config.java"
# 本片（P4）BASE 快照覆盖的文件 = 本片改动的生产文件全集（4 个门定义/调用点；
# framework/SurfaceGate.java 是本片<b>新增</b>文件，BASE 树里没有它，靠"BASE 侧 placer 快照
# 不引用它"来保证 BASE 编译通过——不要把它加进本清单，否则 BASE 侧会缺一整个类）。
# P6（H-1 群系带分层 + L6 城门）开工前快照 = 本片第一个写入之前的工作树副本（b747dad）。
# 它同时服务 [11] 自己的对拍与 [10] 的 P5-BASE 编译面（见下方 [10] 的 P6_BASE_FILES 还原循环）。
SNAP6=temp/p6-base/all/src/main/java
P6_BASE_FILES="com/miaokatze/gtsr/common/dimension/framework/BiomeZoneSelector.java
com/miaokatze/gtsr/common/dimension/framework/GTSRWorldChunkManager.java
com/miaokatze/gtsr/common/dimension/prosperity/ruins/city/CityPlanner.java
com/miaokatze/gtsr/config/Config.java"
# P8：本片开工前快照（供各 era 的 BASE 树取"未被 P8 碰过"的框架件；[15] 自己用 SNAP8 同名别名）
SNAP8_LATE=temp/p8-base/src/main/java
PARITY_BASE_FILES="com/miaokatze/gtsr/common/dimension/prosperity/ruins/ProsperityDecorPlacer.java
com/miaokatze/gtsr/common/dimension/prosperity/ruins/ProsperityOutpostPlacer.java
com/miaokatze/gtsr/common/dimension/prosperity/ruins/ProsperitySurfaceScatter.java
com/miaokatze/gtsr/common/dimension/shattered/ShatteredDecorPlacer.java
com/miaokatze/gtsr/common/dimension/prosperity/ruins/ProsperityWorldGenerator.java
com/miaokatze/gtsr/common/dimension/prosperity/ruins/RuinedMachinePlacer.java
com/miaokatze/gtsr/common/dimension/framework/structure/StructureRegistry.java"
# P7c 修正（[4]/[10]/[11] 的 BASE 树自 P7 起编不过，实测 2/4/5 error）：BASE 树是
# 「cp -a 当前树 + 只还原快照文件」，而 P7 新增了 framework/structure/PlacementGate.java 并让
# StructureRegistry/编排器/两 placer 引用它 ⇒ 只还原 placer 时，当前 StructureRegistry 里的
# PlacementGate.FAMILY_UNSCOPED 找不到符号。修法是"还原该 era 的全部引用方 + 删掉该 era 不存在的
# 新文件"（快照本身从 9265f16 / b747dad 逐字取，见 temp/p*-base/all/src/main/java）。
# 不许退回"让 BASE 侧回落到 build/classes 的陈旧 class"——那会让对拍变成自己比自己（[11a] 曾以此出 0）。
PRE_P7_NEW="com/miaokatze/gtsr/common/dimension/framework/structure/PlacementGate.java"
prefix() { echo "$REL" | sed "s|^|$1/|"; }
# P7c：BASE 树里"该 era 还不存在的生产文件"必须同时从**编译输入**里去掉（只从树上删会得到
# javac "file not found"，实测 P4-BASE 1 error / P5-BASE 1 error ⇒ 整段对拍退回陈旧 class）。
no_entity() { grep -v "prosperity/entity/"; }
# P7c/P5b/P8/P9 的 BASE 树（PlacementGate 已在该 era 存在）：只排掉 P9 才新增的实体包。
# 不许退回"让它回落到 build/classes 的陈旧 class"（P7c 注释里的同一条纪律）。
src_era7p() { prefix "$1" | no_entity; }
src_list() { prefix "$1" | grep -v "framework/structure/PlacementGate.java" | no_entity; }
P12SRC="$(prefix src/main/java)"
BASEALL=temp/p4-base/all            # 编译面：cp -a 当前树后按快照覆盖 → $BASEALL/<包路径>.java
SNAP=$BASEALL/src/main/java        # 快照面：本片开工前的原始路径副本
BASE_D=$BASEALL/com/miaokatze/gtsr/common/dimension
BASE_SRC="$(src_list $BASEALL)"

LOG4J="-Dlog4j.configurationFile=temp/gtsr-check-log4j2.xml"
[ -f temp/gtsr-check-log4j2.xml ] || LOG4J=""
STD="-Dstdout.encoding=UTF-8"
FAILS=0

rm -rf "$OUT"; mkdir -p "$OUT/classes" "$OUT/tools" "$OUT/base-classes"

jc() { # jc <out-dir> <inputs...>
  local out="$1"; shift
  MSYS2_ARG_CONV_EXCL='*' javac -J-Duser.language=en -nowarn -encoding UTF-8 -cp "$CP" \
    -sourcepath src/main/java -d "$out" "$@"
}

echo "== [0] 编译本片 + P1 生产文件（离线 shadow，先于 build/classes） =="
jc "$OUT/classes" $P12SRC >"$OUT/javac-src.log" 2>&1
echo "COMPILE src EXIT=$? ($(grep -ac 'error:' "$OUT/javac-src.log") error)"

echo "== [1] 编译本片新断言 + 既有回归工具 =="
MSYS2_ARG_CONV_EXCL='*' javac -J-Duser.language=en -nowarn -encoding UTF-8 \
  -cp "$OUT/classes;$CP" -sourcepath "src/main/java;tools/dim1" -d "$OUT/tools" \
  tools/dim1/SurfaceHarness.java tools/dim1/SurfaceDegradationCheck.java \
  tools/dim1/SurfaceTranspositionCheck.java tools/dim1/SurfaceByteParityDump.java \
  tools/dim1/SurfaceYParityCheck.java tools/dim1/HeightHashSingleSourceCheck.java \
  tools/dim1/ReplaceSurfaceRuntimeCheck.java tools/dim1/BiomeAllocationCheck.java \
  tools/dim1/ShatteredTerrainCheck.java tools/dim1/SurfaceBiomeMatrixCheck.java \
  tools/dim1/BiomeZoneCheck.java tools/dim1/SurfaceGateUnifyCheck.java \
  tools/dim1/Dim78ScatterDensityCheck.java tools/dim1/ContourBudgetCheck.java \
  tools/dim1/RegionRepeatCapCheck.java tools/dim1/BiomeBandHierarchyCheck.java \
  tools/dim1/CityBiomeGateCheck.java tools/dim1/PlacementContractCheck.java \
  tools/dim1/ScatterClusterVarianceCheck.java tools/dim1/RuinFamilyCheck.java \
  tools/dim1/CreatureSpawnAuthorityCheck.java \
  tools/dim1/DiagLineCheck.java tools/dim1/StructureChannelCheck.java \
  tools/dim1/TpdimNearestBiomeCheck.java \
tools/dim1/StructureViewerExport.java tools/dim1/RosterIntegrityCheck.java \
tools/dim1/S8RegistryRosterCheck.java tools/dim1/OutpostTemplateCheck.java tools/dim1/CityDeterminismCheck.java \
  tools/dim1/gregtech/api/GregTechAPI.java \
  >"$OUT/javac-tools.log" 2>&1
echo "COMPILE tools EXIT=$? ($(grep -ac 'error:' "$OUT/javac-tools.log") error)"

run() { # run <label> <classname> [args...] —— 带参时日志分文件（<cls>-<arg>.out），避免互相覆盖
  local label="$1" cls="$2"; shift 2
  local log="$OUT/$cls.out"
  [ $# -gt 0 ] && log="$OUT/$cls-$1.out"
  echo "-- $label"
  MSYS2_ARG_CONV_EXCL='*' java $STD $LOG4J -cp "$OUT/tools;$OUT/classes;$CP" "$cls" "$@" >"$log" 2>&1
  local code=$?
  tail -1 "$log" | cut -c1-170
  echo "   EXIT=$code log=$log"
  [ $code -ne 0 ] && FAILS=$((FAILS + 1))
}

runres() { # runres <label> <classname> [args...] —— 额外挂<b>原版资源根</b>（P9 判据 4 的纹理
  # getResource 断言需要 assets/minecraft/...；只给这一类工具挂，见文件头 CP 段的说明）
  local label="$1" cls="$2"; shift 2
  local log="$OUT/$cls.out"
  [ $# -gt 0 ] && log="$OUT/$cls-$1.out"
  echo "-- $label"
  MSYS2_ARG_CONV_EXCL='*' java $STD $LOG4J -cp "$OUT/tools;$OUT/classes;$MCRES;$CP" "$cls" "$@" >"$log" 2>&1
  local code=$?
  tail -1 "$log" | cut -c1-170
  echo "   EXIT=$code log=$log"
  [ $code -ne 0 ] && FAILS=$((FAILS + 1))
}

total_assertions() { # total_assertions <cls-prefix> —— 汇总该工具各场景的 assertions
  local sum=0 v f
  for f in "$OUT/$1"-*.out; do
    [ -f "$f" ] || continue
    v=$(grep -ao "assertions=[0-9]*" "$f" | tail -1 | cut -d= -f2)
    [ -n "$v" ] && sum=$((sum + v))
  done
  echo "$sum"
}

echo "== [2] P2 新断言 =="
run "SurfaceDegradationCheck（判据 B：EMPTY/SHORT 不铺表层 + NPE 守卫 + 无 plains/grass/dirt）" \
  SurfaceDegradationCheck
run "SurfaceTranspositionCheck（判据 C：逐列异群系下标对号 + 灵敏度自检）" SurfaceTranspositionCheck
run "SurfaceGateUnifyCheck（P4 判据 2/4：门集合单一真值 + 别名等价 + 源级自造门 0 + 通过率带）"   SurfaceGateUnifyCheck assert src/main/java

echo "== [2b] P5 密度 γ 断言（快档：纯函数硬钉 + 16 窗行为钉；P5 全档与对拍在 [10]） =="
run "RegionRepeatCapCheck（P5 判据 2：H-2 每 16×16 窗同模板发射上限，cap=1..4 穷举硬钉）" RegionRepeatCapCheck 8 2

echo "== [2c] P6 断言（H-1 群系带分层 + L6 城门；四张表的实测出口在 --parity 的 [11]） =="
run "BiomeBandHierarchyCheck（P6 判据 4/5：分层职责/macro-micro 恒等式与回退位/单一真值/带尺度守恒/确定性）" \
  BiomeBandHierarchyCheck assert src/main/java 8 128
run "CityBiomeGateCheck（P6 判据 2/3：无鬼窗逐点一致 + 城市 100% 落草原带 + 门非恒真 + 两种数法一致）" \
  CityBiomeGateCheck assert 8 8
echo "   P6 合计 assertions=$(total_assertions BiomeBandHierarchyCheck)（带分层）+ $(total_assertions CityBiomeGateCheck)（城门）"

echo "== [2d] P7 结构放置契约（判据 1/2/3/4/8 的契约单元 + 源级 + 纯函数档；真地形档在 --parity 的 [12]） =="
run "PlacementContractCheck source（A0-A6 契约单元 + D 单一真值 + E micro 层结论）" PlacementContractCheck source
run "PlacementContractCheck pure 8 8（B1 接地逐点 >=16384 chunk，纯函数不需装配世界）" PlacementContractCheck pure 8 8
run "PlacementContractCheck census 8 8（P7c 判据 1：真实命中集上的 min(cap,h) 精确等式 + 首次命中 100%）" \
  PlacementContractCheck census 8 8

echo "== [2e] P8 城外废墟族（破坏结构）：谱系/派生复算/TE 洁净/micro 收口/opt-in 边界 =="
# 快档 = fast（不跑真地形密度对照；密度对照表在 --parity 的 [15b]，因为它要 generateTerrain 全量）
run "RuinFamilyCheck fast（A 谱系契约 + B 派生可复算 + C TE 洁净与注入 RED + E micro 收口 + G opt-in 边界）"   RuinFamilyCheck fast

echo "== [2f] P9 生物层（L7）：注册链/表内容/渲染器与纹理/DataWatcher/幂等/城窗的离线结构断言 =="
# 三档分进程跑：判据 2 的"总开关关闭"档必须在冷装配里看到关态（同一 JVM 的账本与每实例幂等位会互污）
runres "CreatureSpawnAuthorityCheck all（判据 1/2/3/4/5/6 + 城窗 ×2 与结构联动数据）" CreatureSpawnAuthorityCheck all
runres "CreatureSpawnAuthorityCheck off（判据 2 回退档：关 ⇒ 四群系 4 张表全空 = P8 基线 + 原版零差异）" \
  CreatureSpawnAuthorityCheck off
runres "CreatureSpawnAuthorityCheck source（判据 3/5 源级档：L1 收口钉 + addObject 索引 ≥16 扫描）" \
  CreatureSpawnAuthorityCheck source
echo "   P9 合计 assertions=$(total_assertions CreatureSpawnAuthorityCheck)"

echo "== [2g] P12 观测与降级（L8）：诊断行列名申报 + 一次性门 + StructureChannel 真实判据（三档） =="
rundiag() { # rundiag <label> <classname> [args...] —— 额外挂 <b>gtsr 资源根</b>（P12 DiagLineCheck 的
  # textures 列要真实解析 assets/gtsr/textures/blocks/<png>；其余工具不给，见文件头 CP 段纪律）
  local label="$1" cls="$2"; shift 2
  local log="$OUT/$cls.out"
  [ $# -gt 0 ] && log="$OUT/$cls-$1.out"
  echo "-- $label"
  MSYS2_ARG_CONV_EXCL='*' java $STD $LOG4J -cp "$OUT/tools;$OUT/classes;src/main/resources;$CP" "$cls" "$@" >"$log" 2>&1
  local code=$?
  tail -1 "$log" | cut -c1-170
  echo "   EXIT=$code log=$log"
  [ $code -ne 0 ] && FAILS=$((FAILS + 1))
}
run "DiagLineCheck（P12 判据 1/2/5：dim78 正常与 dim79 强制 EMPTY 各一条 diag 行 + 16 列申报 + 512 一次性门 + boot 行）" \
  DiagLineCheck
run "StructureChannelCheck（P12 判据 4：chunk 窗真实落块，GREEN 档 clipped 必 0）" StructureChannelCheck
# 判据 4 的 RED 档：sink 窗投到远端 chunk(60,60) 让写入全裁 ⇒ 必须变红（旧恒真 sink 在此必绿，
# 由 --legacy-tautology-demo 同场对照；两档都进 [2g] 日志）
MSYS2_ARG_CONV_EXCL='*' java $STD $LOG4J -cp "$OUT/tools;$OUT/classes;$CP" \
  StructureChannelCheck --red-clipped >"$OUT/red-structurechannel.txt" 2>&1
er=$?
grep -a "STRUCTURECHANNEL" "$OUT/red-structurechannel.txt" | head -2 | cut -c1-170
echo "   RED EXIT=$er（必须非 0）log=$OUT/red-structurechannel.txt"
[ "$er" != "0" ] || FAILS=$((FAILS + 1))
run "StructureChannelCheck --legacy-tautology-demo（反假绿对照：旧恒真判据对同一全裁流必绿）" \
  StructureChannelCheck --legacy-tautology-demo

echo "== [2h] P14 tpdim 群系定位（环带步进最近性/代价上界/降级态错误通道/基线路径源级钉；512 chunk 零漂移对拍在 --parity 的 [17]） =="
# 四档分进程跑（L1 账本/bind 同 JVM 互污，与 BiomeAllocationCheck 同纪律）
run "TpdimNearestBiomeCheck d78（判据 2/3/4：四群系定位+穷举最近对拍+步数/耗时/两条上界档）" \
  TpdimNearestBiomeCheck d78
run "TpdimNearestBiomeCheck d79（同上，权重 40-30-20-10 与 macro=16 口径）" TpdimNearestBiomeCheck d79
run "TpdimNearestBiomeCheck empty79（dim79 降级态：名单空/解析 null/81 步收口，不乱指）" \
  TpdimNearestBiomeCheck empty79
run "TpdimNearestBiomeCheck source（判据 1/5：基线路径逐字钉+补全单一真值+diag 反复制钉）" \
  TpdimNearestBiomeCheck source
echo "   P14 合计 assertions=$(total_assertions TpdimNearestBiomeCheck)"

echo "== [3] 既有回归（必须保持绿） =="
run "ReplaceSurfaceRuntimeCheck（46 项，含 null/plains 回退与逐列下标断言；P2b 起 256 格假绿已除）" ReplaceSurfaceRuntimeCheck
# BiomeAllocationCheck 按场景分进程跑（同一 JVM 里账本会互相污染）；A+B+C+D 合计 = P1 的 121 项
for sc in A B C D; do
  run "BiomeAllocationCheck scenario $sc（P1：逐群系配槽 + 三态降级 + 别名防线）" BiomeAllocationCheck "$sc"
done
echo "   BiomeAllocationCheck 合计 assertions=$(total_assertions BiomeAllocationCheck)（P1 交付口径 121）"
run "ShatteredTerrainCheck（纯函数 + 源码接线）" ShatteredTerrainCheck
run "SurfaceBiomeMatrixCheck（表层四元组矩阵行为钉：真实注册链+真实表层链，P2b 起替代文本钉）" SurfaceBiomeMatrixCheck
run "BiomeZoneCheck（空间连贯分区）" BiomeZoneCheck
# ── P13 修复 1：asset 行 echo 粘连（基线遗留，P14 证据文档 §线索 已登记）──────────────────
# 原行为 `echo "-- …）"bash tools/dim1/asset_baseline_check.sh >out` ——shell 把 `"字符串"词`
# 拼成<b>同一个参数</b>，于是 asset_baseline_check.sh <b>从未被执行</b>，下一行 `code=$?` 取到的
# 是 echo 的 0 ⇒ P0 资产基线门在 surface_checks 里长期假绿（各片的 roster=47 全靠手工直跑补）。
# 现拆成两行，并把 --parity 的 [3] 计数纳入 FAILS。
echo "-- asset_baseline_check.sh（P0 资产基线：roster/textures/SHA + 结构数据件 + 展示页群系键横向钉）"
bash tools/dim1/asset_baseline_check.sh >"$OUT/asset.out" 2>&1
code=$?; tail -2 "$OUT/asset.out" | cut -c1-170; echo "   EXIT=$code log=$OUT/asset.out"
[ $code -ne 0 ] && FAILS=$((FAILS + 1))
# ── P13 修复 2：RosterIntegrityCheck / S8RegistryRosterCheck 补 run 挂点 ──────────────────
# 两者原本只进 [1] 编译清单、没有任何 run 挂点（P14 证据文档 §线索 2），在本脚本里只被
# asset 行（而 asset 行又因上面的粘连从未跑）间接覆盖 ⇒ 名集合三方相等 / 逐名 footprint /
# 模板 SHA256 / 敏感度自检这四组断言在本一键复跑入口内<b>零执行</b>。现各自直接挂点，
# 与 asset 门互为冗余（asset 门钉末行口径，这里钉断言数与 EXIT）。
run "RosterIntegrityCheck（P0 判据 1：名集合三方相等 + 逐名 footprint + 模板 SHA256 + 双跑 + 敏感度）" \
  RosterIntegrityCheck
run "S8RegistryRosterCheck（P0 判据 1 名数面：名册名数 + 分组计数 + 名集合相等）" \
  S8RegistryRosterCheck

if [ "${1:-}" = "--parity" ]; then
  echo "== [4] 正常态逐字节对拍（BASE=本片开工前快照 / AFTER=当前树，各 256 chunk × 两维） =="
  if [ ! -f "$SNAP/com/miaokatze/gtsr/common/dimension/prosperity/ruins/ProsperityDecorPlacer.java" ]; then
    echo "   FAIL：缺 P4 BASE 快照 $SNAP/com/miaokatze/gtsr/common/dimension/prosperity/ruins/ProsperityDecorPlacer.java"
    echo "        （必须先自建 temp/p4-base/all/src/main/java/… = 本片开工前工作树副本，见文件头注释）"
    FAILS=$((FAILS + 1))
  else
    # cp -a 保留时间戳：javac 按「源比 class 新才重编」的默认规则解析依赖，
    # 与 [0] 步编译当前树时的行为一致（否则会把 CommonProxy 之类拉进来重编，缺第三方依赖）。
    rm -rf "$BASEALL/com" "$OUT/base-classes"
    mkdir -p "$BASEALL/com" "$OUT/base-classes"
    cp -a src/main/java/. "$BASEALL/"
    # 按申报清单把 BASE 侧还原成本片开工前（片内口径的"改造前"）
    miss=0
    for rel in $PARITY_BASE_FILES; do
      if [ -f "$SNAP/$rel" ]; then
        cp "$SNAP/$rel" "$BASEALL/$rel"
      else
        echo "   FAIL：BASE 快照缺 $rel"; miss=$((miss + 1))
      fi
    done
    rm -f "$BASEALL/$PRE_P7_NEW"   # P4 era 还没有 PlacementGate（P7c：不删则 BASE 侧编不过）
    [ "$miss" = "0" ] || FAILS=$((FAILS + 1))
    MSYS2_ARG_CONV_EXCL='*' javac -J-Duser.language=en -nowarn -encoding UTF-8 -cp "$CP" \
      -sourcepath "$BASEALL" -d "$OUT/base-classes" $BASE_SRC >"$OUT/javac-base.log" 2>&1
    [ "$(grep -ac 'error:' "$OUT/javac-base.log")" = "0" ] \
      || { echo "   FAIL：P4-BASE 树编译失败（还原清单不完整，对拍会退化成自比）"; FAILS=$((FAILS + 1)); }
    echo "COMPILE BASE EXIT=$? ($(grep -ac 'error:' "$OUT/javac-base.log") error)"
    MSYS2_ARG_CONV_EXCL='*' java $STD $LOG4J -cp "$OUT/tools;$OUT/classes;$CP" \
      SurfaceByteParityDump 16 >"$OUT/parity-after.txt" 2>"$OUT/parity-after.err"
    MSYS2_ARG_CONV_EXCL='*' java $STD $LOG4J -cp "$OUT/tools;$OUT/base-classes;$CP" \
      SurfaceByteParityDump 16 >"$OUT/parity-base.txt" 2>"$OUT/parity-base.err"
    n=$(diff "$OUT/parity-base.txt" "$OUT/parity-after.txt" | grep -ac "^[<>]")
    c=$(grep -ac "^CHUNK" "$OUT/parity-after.txt")
    d78=$(grep -ac "^CHUNK dim=78" "$OUT/parity-after.txt")
    d79=$(grep -ac "^CHUNK dim=79" "$OUT/parity-after.txt")
    u=$(grep -a "^# unmapped" "$OUT/parity-after.txt")
    hd=$(grep -ac "^DIMHDR.*degraded=NONE bound=true" "$OUT/parity-after.txt")
    echo "   chunks=$c (dim78=$d78 dim79=$d79) NONE前提行=$hd diff_lines=$n $u"
    echo "   明细：$OUT/parity-base.txt vs $OUT/parity-after.txt"
    [ "$n" = "0" ] || FAILS=$((FAILS + 1))

    # ── [5] P3 判据 2+3：高度场 / 列扫 / 哈希站点逐位对拍 ──
    echo "== [5] 站点级逐位对拍（BASE 快照树 vs 当前树，同一工具源码，diff ^SITE 必须为空） =="
    # BASE 侧工具单独编一份（工具源码同一份，只是 classpath 指向 base-classes）
    MSYS2_ARG_CONV_EXCL='*' javac -J-Duser.language=en -nowarn -encoding UTF-8 \
      -cp "$OUT/base-classes;$CP" -sourcepath "$BASEALL;tools/dim1" -d "$OUT/base-tools" \
      tools/dim1/SurfaceHarness.java tools/dim1/SurfaceYParityCheck.java \
      tools/dim1/SurfaceGateUnifyCheck.java tools/dim1/gregtech/api/GregTechAPI.java \
      >"$OUT/javac-base-tools.log" 2>&1
    echo "COMPILE base-tools EXIT=$? ($(grep -ac 'error:' "$OUT/javac-base-tools.log") error)"
    MSYS2_ARG_CONV_EXCL='*' java $STD $LOG4J -cp "$OUT/tools;$OUT/classes;$CP" \
      SurfaceYParityCheck >"$OUT/site-after.txt" 2>"$OUT/site-after.err"
    ea=$?
    MSYS2_ARG_CONV_EXCL='*' java $STD $LOG4J -cp "$OUT/base-tools;$OUT/base-classes;$CP" \
      SurfaceYParityCheck >"$OUT/site-base.txt" 2>"$OUT/site-base.err"
    eb=$?
    sites=$(grep -ac "^SITE " "$OUT/site-after.txt")
    hp=$(grep -a "^HEIGHT_POINTS_PER_PROFILE" "$OUT/site-after.txt")
    sd=$(diff <(grep "^SITE " "$OUT/site-base.txt") <(grep "^SITE " "$OUT/site-after.txt") | grep -ac "^[<>]")
    # 防伪对拍：BASE 与 AFTER 必须命中<b>不同</b>的实现（否则等于拿同一份代码和自己比）
    rd=$(diff <(grep "^# resolved" "$OUT/site-base.txt") <(grep "^# resolved" "$OUT/site-after.txt") | grep -ac "^<")
    echo "   AFTER EXIT=$ea BASE EXIT=$eb 站点数=$sites $hp site_digest_diff_lines=$sd 实现改道行数=$rd"
    echo "   命中实况：$OUT/site-base.txt / $OUT/site-after.txt 的 '# resolved' 头行"
    [ "$ea" = "0" ] || { FAILS=$((FAILS + 1)); tail -5 "$OUT/site-after.err"; }
    [ "$eb" = "0" ] || { FAILS=$((FAILS + 1)); tail -5 "$OUT/site-base.err"; }
    [ "$sd" = "0" ] || FAILS=$((FAILS + 1))
    [ "$sites" -ge 20 ] || FAILS=$((FAILS + 1))
    # P4 口径变更（plan §5 P4 禁止越界「改 heightAt/噪声参数/播种」）：本片<b>不动</b> L2/列扫/哈希
    # 站点，故 rd 必须为 0——rd>0 即"本片越界改了高度或列扫实现"，直接判红。
    # P3 期这里是 rd>=10（当时那 8 处改道就是被验对象）；两片的判据对象不同，不是放宽。
    # 本片"BASE/AFTER 确有行为差异"的反假绿证据在 [8]（门 A/B 计数必须不同）与 [9]（RED 必须变红）。
    [ "$rd" = "0" ] || { echo "   FAIL：L2/列扫实现被本片改道（rd=$rd）——P4 禁止越界，判红"; FAILS=$((FAILS + 1)); }
  fi

  # ── [6] 仅注释改动的文件：编译产物等值证明（比采样更强的"同一输入同一输出"） ──
  # 判据形式：把 BASE 快照与当前树各编一次，逐 class 文件比 SHA256。
  # 注释/文档<b>不进入 class 文件</b>（class 里没有源文本、没有时间戳），所以"仅加注释"
  # 的硬证据就是 class 字节逐位相同——它蕴含"所有方法体字节码相同"。
  # 曾试 javap -c 逐行比对，但 PATH 上的 javap 与 javac 不是同一个 JDK
  # （实测 javac=26 / javap=25 → 只吐 "Unsupported class file version: 70"，
  # 两份错误输出相同会被误判成"等值"），故改为 class 摘要比对 + 行数/符号双门槛。
  echo "== [6] 未合并列扫 #1/#8 的编译产物等值（BASE vs AFTER class SHA256） =="
  for rel in com/miaokatze/gtsr/common/dimension/framework/GTSRDimTeleporter.java \
             com/miaokatze/gtsr/common/world/WorldGenRunawaySingularity.java; do
    cls=$(echo "$rel" | sed 's|/|.|g; s|\.java$||')
    simple=$(basename "$rel" .java)
    pkgdir=$(dirname "$rel")
    mkdir -p "$OUT/javap-after/$pkgdir" "$OUT/javap-base/$pkgdir"
    rm -f "$OUT/javap-after/$pkgdir/$simple"*.class "$OUT/javap-base/$pkgdir/$simple"*.class
    MSYS2_ARG_CONV_EXCL='*' javac -J-Duser.language=en -nowarn -encoding UTF-8 -cp "$CP" \
      -sourcepath src/main/java -d "$OUT/javap-after" "src/main/java/$rel" \
      >"$OUT/javap-after.log" 2>&1
    ea=$?
    MSYS2_ARG_CONV_EXCL='*' javac -J-Duser.language=en -nowarn -encoding UTF-8 -cp "$CP" \
      -sourcepath "$BASEALL" -d "$OUT/javap-base" "$BASEALL/$rel" \
      >"$OUT/javap-base.log" 2>&1
    eb=$?
    if [ $ea -ne 0 ] || [ $eb -ne 0 ]; then
      echo "   COMPILE FAIL after=$ea base=$eb（$cls）"; FAILS=$((FAILS + 1)); continue
    fi
    ACLS="$OUT/javap-after/$pkgdir/$simple"
    BCLS="$OUT/javap-base/$pkgdir/$simple"
    na=$(ls "$ACLS"*.class 2>/dev/null | wc -l)
    nb=$(ls "$BCLS"*.class 2>/dev/null | wc -l)
    ha=$(cat "$ACLS"*.class | sha256sum | cut -c1-24)
    hb=$(cat "$BCLS"*.class | sha256sum | cut -c1-24)
    size=$(wc -c < "$ACLS.class")
    if [ "$na" = "0" ] || [ "$na" != "$nb" ] || [ "$size" -lt 500 ]; then
      echo "   $cls class 数/大小异常（after=$na base=$nb main=${size}B）——判红，不拿空产物当等值"
      FAILS=$((FAILS + 1)); continue
    fi
    same=$([ "$ha" = "$hb" ] && echo 逐位相同 || echo 不同)
    echo "   $cls classes=$na/${nb} size=${size}B AFTER_SHA=$ha BASE_SHA=$hb → $same"
    [ "$ha" = "$hb" ] || FAILS=$((FAILS + 1))
    # 额外语义门槛：确认该 class 里确实还有 findSurfaceY（防"被误删"当成"没变化"）
    if ! grep -ac findSurfaceY "$ACLS.class" >/dev/null 2>&1; then
      echo "   $cls 的 class 常量池里已无 findSurfaceY —— 该列扫被误并/误删，判红"
      FAILS=$((FAILS + 1))
    fi
  done

  # ── [7] P3 判据 4：单一真值自检 RED → GREEN ──
  echo "== [7] 单一真值自检（先 GREEN，再在影子树上人为破坏看是否变红） =="
  MSYS2_ARG_CONV_EXCL='*' java $STD -cp "$OUT/tools" HeightHashSingleSourceCheck src/main/java \
    >"$OUT/single-source-green.txt" 2>&1
  eg=$?
  tail -1 "$OUT/single-source-green.txt" | cut -c1-160
  echo "   GREEN EXIT=$eg log=$OUT/single-source-green.txt"
  [ "$eg" = "0" ] || FAILS=$((FAILS + 1))
  # 影子树：新增一处手搓哈希 + 复制一份列扫（临时目录，不碰工作树）
  rm -rf temp/p3-red-shadow && cp -a src/main/java temp/p3-red-shadow
  python - <<'PY' >"$OUT/red-inject.log" 2>&1
import io, os
root = 'temp/p3-red-shadow/com/miaokatze/gtsr/common/dimension'
p = os.path.join(root, 'prosperity/ruins/ProsperitySurfaceScatter.java')
t = io.open(p, encoding='utf-8').read()
anchor = '    /** 权重选取（02 §3.2 pickWeighted 原样）。 */'
inject = '''    private static long sneakyHash(long seed, int x, int z) {
        long h = seed ^ (x * 0x27D4EB2F165667C5L) ^ (z * 0x165667B19E3779F9L);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return h;
    }

''' + anchor
assert anchor in t, 'anchor missing (ProsperitySurfaceScatter)'
io.open(p, 'w', encoding='utf-8', newline='').write(t.replace(anchor, inject, 1))
q = os.path.join(root, 'prosperity/ruins/RuinedMachinePlacer.java')
u = io.open(q, encoding='utf-8').read()
a2 = '    /** 方块键解析（含未注册防御：总开关关闭等场景方块可能缺失，跳过该部件不炸生成链）。 */'
i2 = '''    private static int findSurfaceY(World world, int x, int z) {
        for (int y = 255; y > 0; y--) {
            if (world.getBlock(x, y, z) != null) {
                return y;
            }
        }
        return -1;
    }

''' + a2
assert a2 in u, 'anchor missing (RuinedMachinePlacer)'
io.open(q, 'w', encoding='utf-8', newline='').write(u.replace(a2, i2, 1))
print('injected 2 violations')
PY
  MSYS2_ARG_CONV_EXCL='*' java $STD -cp "$OUT/tools" HeightHashSingleSourceCheck temp/p3-red-shadow \
    >"$OUT/single-source-red.txt" 2>&1
  er=$?
  grep -a "^  FAIL" "$OUT/single-source-red.txt" | head -6 | cut -c1-160
  tail -1 "$OUT/single-source-red.txt" | cut -c1-160
  echo "   RED EXIT=$er（必须非 0）log=$OUT/single-source-red.txt"
  [ "$er" != "0" ] || FAILS=$((FAILS + 1))
  rm -rf temp/p3-red-shadow

  # ── [8] P4 判据 3：修门影响 A/B 量化（BASE=缺陷态 4 员装饰门 / AFTER=单一谓词 5 员） ──
  # 两跑吃<b>同一组 seed、同一份工具源码</b>，只有 classpath 指向不同的树；measure 模式的门判定
  # 一律走各树自己的生产谓词（不引用 SurfaceGate），所以差值就是"修门"这一件事的净效应。
  # 默认 2 seed × 2 区（≈1 分钟）；归档权威表用 8 seed × 8 区，复现：
  #   GATE_AB_SEEDS=8 GATE_AB_REGIONS=8 bash tools/dim1/surface_checks.sh --parity
  echo "== [8] P4 修门影响 A/B（BASE 缺陷态 vs AFTER 单一谓词） =="
  GA=${GATE_AB_SEEDS:-2}; GB=${GATE_AB_REGIONS:-2}; GC=${GATE_AB_AXIS:-16}
  MSYS2_ARG_CONV_EXCL='*' java $STD -cp "$OUT/base-tools;$OUT/base-classes;$CP" \
    SurfaceGateUnifyCheck measure $GA $GB $GC >"$OUT/gate-ab-base.txt" 2>&1
  ea=$?
  MSYS2_ARG_CONV_EXCL='*' java $STD -cp "$OUT/tools;$OUT/classes;$CP" \
    SurfaceGateUnifyCheck measure $GA $GB $GC >"$OUT/gate-after.txt" 2>&1
  eb=$?
  g() { # g <file> <行匹配> <字段>
    grep -a "$2" "$1" | grep -ao "$3=[0-9]*" | head -1 | cut -d= -f2
  }
  colb=$(g "$OUT/gate-ab-base.txt" "surface=post-chain" cols); cola=$(g "$OUT/gate-after.txt" "surface=post-chain" cols)
  pb=$(g "$OUT/gate-ab-base.txt" "surface=post-chain" pass); pa=$(g "$OUT/gate-after.txt" "surface=post-chain" pass)
  pab=$(g "$OUT/gate-ab-base.txt" "surface=pre-scatter" pass); paa=$(g "$OUT/gate-after.txt" "surface=pre-scatter" pass)
  lb=$(g "$OUT/gate-ab-base.txt" "kind=decor surface=post-chain" landedTotal)
  la=$(g "$OUT/gate-after.txt" "kind=decor surface=post-chain" landedTotal)
  lpb=$(g "$OUT/gate-ab-base.txt" "kind=decor surface=pristine" landedTotal)
  lpa=$(g "$OUT/gate-after.txt" "kind=decor surface=pristine" landedTotal)
  prb=$(grep -a "kind=premise" "$OUT/gate-ab-base.txt"); pra=$(grep -a "kind=premise" "$OUT/gate-after.txt")
  # P5 口径变更（保留强度、只拆一项）：kind=chain 行里的 scatterLanded* 两项<b>就是 P5 的被验对象</b>
  # （密度 γ 只改散布层），若仍要求"整行逐字相同"，本片的预期效果会被误判成"P4 判据被破坏"。
  # 故拆成两条门槛：① 非散布前序项（outpost 命中数 / 机器落块）必须逐字相同——强度与原断言一致；
  # ② 散部落块必须<b>严格下降</b>（方向钉）——比原来的"相等"更强：同时钉住"P5 确实生效"与
  #   "P5 没把散布改密"。两条合起来仍然保证"P4 的 A/B 差只来自装饰门 + P5 的散布档"。
  chainl() { grep -a "kind=chain" "$1"; }
  chb=$(chainl "$OUT/gate-ab-base.txt" | sed 's/ scatterLanded=.*//')
  cha=$(chainl "$OUT/gate-after.txt" | sed 's/ scatterLanded=.*//')
  scb=$(chainl "$OUT/gate-ab-base.txt" | grep -ao "scatterLanded=[0-9]*" | head -1 | cut -d= -f2)
  sca=$(chainl "$OUT/gate-after.txt" | grep -ao "scatterLanded=[0-9]*" | head -1 | cut -d= -f2)
  echo "   BASE EXIT=$ea AFTER EXIT=$eb 样本列数 BASE=$colb AFTER=$cola"
  echo "   可落地列数(散布前)   ：BASE=$pab → AFTER=$paa（差 $((paa - pab))）"
  echo "   可落地列数(装饰前)   ：BASE=$pb → AFTER=$pa（差 $((pa - pb))；P4 期这一项全是 outpost/机器的 's' 板面）"
  echo "   装饰落块(生产口径)   ：BASE=$lb → AFTER=$la（差 $((la - lb)) 块）"
  echo "   装饰落块(pristine 面)：BASE=$lpb → AFTER=$lpa（差 $((lpa - lpb))，必须为 0）"
  echo "   前序阶段交叉校验     ：BASE[$chb]"
  echo "                          AFTER[$cha]"
  echo "   散部落块(P5 密度γ项) ：BASE=${scb:-?} → AFTER=${sca:-?}（P5 必须严格下降）"
  echo "   注(P5)：口径C/装饰落块两行的差值自 P5 起同时含'修门'与'散布变疏'两项，不再是纯门净效应；"
  echo "        要保持 P4 §3.2 的纯门 A/B，需给 SurfaceGateUnifyCheck 的 measure 模式加'散布回退'开关"
  echo "        （不在 P5 允许路径内，已上报主代理）。P4 期的 +1360 列/+54 块只在 P4 期样本成立。"
  echo "   采样前提（无悬块/洞穴）：BASE[$prb]"
  echo "                          AFTER[$pra]"
  [ "$ea" = "0" ] || { echo "   FAIL：BASE measure 非 0"; FAILS=$((FAILS + 1)); }
  [ "$eb" = "0" ] || { echo "   FAIL：AFTER measure 非 0"; FAILS=$((FAILS + 1)); }
  [ "$colb" = "$cola" ] || { echo "   FAIL：BASE/AFTER 样本列数不同（$colb vs $cola）⇒ 不是同一输入"; FAILS=$((FAILS + 1)); }
  # P7c 口径变更（被验对象换了，强度不降）：kind=chain 行的 outpostHitChunks/machineLanded 自
  # P7/P7c 起本身就是被改判的量（接地改道 831→984 命中 + 窗上限/间距收紧），再要求它与 p4-BASE
  # "逐字相同"会把计划批准的行为变化误报成越界。换成三条不弱于等式的门槛：① 两侧都必须非 0
  # （旧等式在两侧同为 0 时是假绿，P7b 的现场正是如此）；② 两行原文照打（幅度可核）；
  # ③ 散布项的方向钉（下面 sca<scb）一字未动。
  cnz() { # cnz <行> <字段>
    echo "$1" | grep -ao "$2=[0-9]*" | head -1 | cut -d= -f2
  }
  chb_op=$(cnz "$chb" outpostHitChunks); cha_op=$(cnz "$cha" outpostHitChunks)
  chb_mc=$(cnz "$chb" machineLanded); cha_mc=$(cnz "$cha" machineLanded)
  # 注(诚实性)：AFTER 侧的 measure harness 仍把 provider 挂在 SurfaceHarness.mockWorld()（该文件的
  # 允许改动被任务包限定为"仅修因格式化失效的计数方式"，本片不动它）⇒ P7 起结构接地走 heightAt 后
  # 这一档的 outpost 命中会退化到 0（机器侧仍落块）。因此本处只要求"AFTER 侧结构前序不得整段为 0"，
  # 逐族的 CHAIN 硬门槛放在 harness 正确的 [13b]（Dim78ScatterDensityCheck digest）里判。
  for pair in "BASE-outpost ${chb_op:-0}" "BASE-machine ${chb_mc:-0}"; do
    [ "$(echo "$pair" | cut -d' ' -f2)" -gt 0 ] \
      || { echo "   FAIL：结构前序有一侧整段为 0（$pair）⇒ 静默失效不得当成一致；两侧差异明细见上面的前序阶段交叉校验两行"; FAILS=$((FAILS + 1)); }
  done
  [ "$(( ${cha_op:-0} + ${cha_mc:-0} ))" -gt 0 ]     || { echo "   FAIL：AFTER 侧结构前序整段为 0（outpost=${cha_op:-?} machine=${cha_mc:-?}）⇒ 静默失效不得当成一致"; FAILS=$((FAILS + 1)); }
  [ -n "$scb" ] && [ -n "$sca" ] && [ "$sca" -lt "$scb" ] \
    || { echo "   FAIL：P5 散部落块未严格低于 p4-BASE（BASE=${scb:-?} AFTER=${sca:-?}）⇒ 密度γ 未生效或反被改密"; FAILS=$((FAILS + 1)); }
  [ "$prb" = "$pra" ] || { echo "   FAIL：采样前提在两跑间不一致"; FAILS=$((FAILS + 1)); }
  [ "$pa" -ge "$pb" ] || { echo "   FAIL：修门后可落地列数反而变少（放宽必须单向）"; FAILS=$((FAILS + 1)); }
  [ "$la" -ge "$lb" ] || { echo "   FAIL：修门后装饰落块反而变少（放宽必须单向）"; FAILS=$((FAILS + 1)); }
  [ "$lpa" = "$lpb" ] || { echo "   FAIL：pristine 面落量出现差异（$lpb → $lpa）⇒ 门修复之外的漂移"; FAILS=$((FAILS + 1)); }
  [ "$pa" -gt "$pb" ] || { echo "   FAIL：AFTER 可落地列数未超过 BASE（$pb → $pa）⇒ 修门没生效，A/B 是恒等自证"; FAILS=$((FAILS + 1)); }

  # ── [9] P4 判据 6：地表门 RED→GREEN（两条单变量破坏，影子树，不碰工作树） ──
  # R1 = 改集合成员（bind() 里把 dim78 首成员换成 steppeTop ⇒ 集合丢掉 prosperitySurface）
  # R2 = 把装饰层的门调用指回改造前的自持 4 员谓词
  echo "== [9] P4 地表门 RED→GREEN（影子树单变量破坏） =="
  REDROOT=temp/p4-red-shadow; REDCLS=$OUT/red-classes
  RED_SRC=$(echo "$REL" | sed 's|^|temp/p4-red-shadow/|' | tr '\n' ' ')
  red() { # red <标签> <sed 表达式> <目标文件>
    rm -rf "$REDROOT" "$REDCLS"; mkdir -p "$REDCLS"
    cp -a src/main/java "$REDROOT"
    sed -i "$2" "$REDROOT/$3"
    cmp -s "src/main/java/$3" "$REDROOT/$3" && { echo "   $1 注入未生效（影子文件与工作树相同）"; FAILS=$((FAILS + 1)); }
    MSYS2_ARG_CONV_EXCL='*' javac -J-Duser.language=en -nowarn -encoding UTF-8 -cp "$CP" \
      -sourcepath "$REDROOT" -d "$REDCLS" $RED_SRC >"$OUT/red-$1-javac.log" 2>&1
    if [ $? -ne 0 ]; then echo "   $1 影子树编译失败（不是 RED，是脚本坏了）"; FAILS=$((FAILS + 1)); fi
    MSYS2_ARG_CONV_EXCL='*' java $STD -cp "$OUT/tools;$REDCLS;$CP" \
      SurfaceGateUnifyCheck assert "$REDROOT" >"$OUT/red-$1.txt" 2>&1
    er=$?
    grep -a "^  FAIL" "$OUT/red-$1.txt" | head -3 | cut -c1-150 | sed "s/^/     /"
    tail -1 "$OUT/red-$1.txt" | cut -c1-150 | sed "s/^/     /"
    echo "     $1 EXIT=$er（RED 必须非 0）log=$OUT/red-$1.txt"
    [ "$er" != "0" ] || { echo "   FAIL：$1 未变红 ⇒ 本判据对这类破坏不敏感"; FAILS=$((FAILS + 1)); }
  }
  red R1 's|DIM78_TOPS\[0\] = BlocksGTSR.prosperitySurface;|DIM78_TOPS[0] = BlocksGTSR.prosperitySteppeTop;|' \
    com/miaokatze/gtsr/common/dimension/framework/SurfaceGate.java
  red R2 's|return SurfaceGate.isNaturalTop(DIM_KEY, SurfaceGate.landableTops(DIM_KEY), ground);|return ground == BlocksGTSR.prosperitySteppeTop;|' \
    com/miaokatze/gtsr/common/dimension/prosperity/ruins/ProsperityDecorPlacer.java
  rm -rf "$REDROOT" "$REDCLS"
  run "SurfaceGateUnifyCheck（RED 后工作树复位 GREEN）" SurfaceGateUnifyCheck assert src/main/java

  # ── [10] P5 密度 γ：可回退摘要对拍 + 非散布路径零漂移 + 两条新上限 RED→GREEN ──
  # BASE 必须是<b>本片开工前</b>的快照 temp/p5-base/all/src/main/java/…（= P4 终态）。
  # 沿用 p4-base 会把 P4 的门缺陷一起拉进来 ⇒ "回退逐位相同"会因两个原因同时成立而失真（假绿）。
  echo "== [10] P5 密度 γ（H-2/H-3）：摘要对拍 + 非散布零漂移 + 新上限 RED→GREEN =="
  BASE5=temp/p5-base/all
  SNAP5=$BASE5/src/main/java
  P5_BASE_FILES="com/miaokatze/gtsr/common/dimension/prosperity/ruins/ProsperitySurfaceScatter.java
com/miaokatze/gtsr/common/dimension/prosperity/ruins/ProsperityWorldGenerator.java
com/miaokatze/gtsr/common/dimension/prosperity/ruins/RuinedMachinePlacer.java
com/miaokatze/gtsr/common/dimension/prosperity/ruins/ProsperityOutpostPlacer.java
com/miaokatze/gtsr/common/dimension/framework/structure/StructureRegistry.java
com/miaokatze/gtsr/config/Config.java"
  if [ ! -f "$SNAP5/com/miaokatze/gtsr/common/dimension/prosperity/ruins/ProsperitySurfaceScatter.java" ]; then
    echo "   FAIL：缺 P5 BASE 快照 $SNAP5/…/ProsperitySurfaceScatter.java"
    echo "        （必须先自建 temp/p5-base/all/src/main/java/… = 本片开工前工作树副本）"
    FAILS=$((FAILS + 1))
  else
    rm -rf "$BASE5/com" "$OUT/p5-base-classes" "$OUT/p5-base-tools"
    mkdir -p "$BASE5/com" "$OUT/p5-base-classes" "$OUT/p5-base-tools"
    cp -a src/main/java/. "$BASE5/"
    miss=0
    # P6 起：先还原 P6 改过的 4 个生产文件（= b747dad 内容），再还原 P5 自己的 4 个快照文件——
    # 顺序不能反：两片都改过 Config.java，后写者赢 ⇒ 交集文件必须落在**更早**（P5 期）的形态。
    # 之所以要还原 P6 的文件：否则 BASE 侧会带上引用新 Config 键的 P6 代码而 P5-era Config 无那些键
    # ⇒ COMPILE P5-BASE 直接 4 error（实测踩过）。P5 本身没动那 3 个文件，故其 P6 前快照 == P5 期内容。
    for rel in $P6_BASE_FILES; do
      if [ -f "$SNAP6/$rel" ]; then
        cp "$SNAP6/$rel" "$BASE5/$rel"
      else
        echo "   FAIL：P6 快照缺 $rel（P5-BASE 树回不到纯 P5 期）"; miss=$((miss + 1))
      fi
    done
    for rel in $P5_BASE_FILES; do
      if [ -f "$SNAP5/$rel" ]; then
        cp "$SNAP5/$rel" "$BASE5/$rel"
        cmp -s "$SNAP5/$rel" "$BASE5/$rel" || { echo "   FAIL：BASE 还原后与工作树相同（快照已失效）"; miss=$((miss + 1)); }
      else
        echo "   FAIL：BASE 快照缺 $rel"; miss=$((miss + 1))
      fi
    done
    rm -f "$BASE5/$PRE_P7_NEW"   # P5 era 还没有 PlacementGate（P7c 修正）
    [ "$miss" = "0" ] || FAILS=$((FAILS + 1))
    if grep -aq "prosperityCityBiomeGate" "$BASE5/com/miaokatze/gtsr/config/Config.java"; then
      echo "   FAIL：P5-BASE 树仍含 P6 新键 ⇒ P6 还原失败"; FAILS=$((FAILS + 1))
    else
      echo "   P5-BASE 树确认同时无 P5/P6 新键（快照有效）"
    fi
    # BASE 树不得含 P5 新键（防"快照其实是改造后"的假对拍）
    if grep -aq "prosperityScatterContoursPerChunk" "$BASE5/com/miaokatze/gtsr/config/Config.java"; then
      echo "   FAIL：BASE 侧 Config 已含 P5 新键 ⇒ 快照不是开工前形态"; FAILS=$((FAILS + 1))
    else
      echo "   BASE 侧确认无 P5 新键（快照有效）"
    fi
    P5_SRC="$(src_list $BASE5)"
    MSYS2_ARG_CONV_EXCL='*' javac -J-Duser.language=en -nowarn -encoding UTF-8 -cp "$CP" \
      -sourcepath "$BASE5" -d "$OUT/p5-base-classes" $P5_SRC >"$OUT/p5-javac-base.log" 2>&1
    echo "COMPILE P5-BASE EXIT=$? ($(grep -ac 'error:' "$OUT/p5-javac-base.log") error)"
    [ "$(grep -ac 'error:' "$OUT/p5-javac-base.log")" = "0" ] \
      || { echo "   FAIL：P5-BASE 树编译失败（还原清单不完整，摘要对拍会退化成自比）"; FAILS=$((FAILS + 1)); } 
    # 工具源码同一份，只在 BASE classpath 上重编（证明本工具确实是双树可跑的）
    MSYS2_ARG_CONV_EXCL='*' javac -J-Duser.language=en -nowarn -encoding UTF-8 \
      -cp "$OUT/p5-base-classes;$CP" -sourcepath "$BASE5;tools/dim1" -d "$OUT/p5-base-tools" \
      tools/dim1/SurfaceHarness.java tools/dim1/Dim78ScatterDensityCheck.java \
      tools/dim1/gregtech/api/GregTechAPI.java >"$OUT/p5-javac-base-tools.log" 2>&1
    echo "COMPILE P5-BASE-TOOLS EXIT=$? ($(grep -ac 'error:' "$OUT/p5-javac-base-tools.log") error)"

    PS=${P5_AB_SEEDS:-4}; PR=${P5_AB_REGIONS:-4}
    # AFTER 侧经 BiomeBandHierarchyCheck 的 rollback 包装：把 P6 的三个键设回改造前口径，否则本判据
    # 会把"P6 的城窗重分布"误报成"P5 判据 3「可回退」不成立"（P6 自己的账在 [11c] 的表 4）。
    # P7c：两侧都在"跳过结构前序"的档上跑（-Dgtsr.skipStructure=1），让 SCAN/CHAIN 的逐位相同
    # 真正只隔离散布那一件事（P7/P7c 之后结构前序本身是被改判的量）；结构侧判据在 [12]/[13]。
    MSYS2_ARG_CONV_EXCL='*' java $STD $LOG4J -Dgtsr.skipStructure=1 -cp "$OUT/p5-base-tools;$OUT/p5-base-classes;$CP" \
      Dim78ScatterDensityCheck digest $PS $PR 16 >"$OUT/p5-digest-base.txt" 2>&1
    eb=$?
    MSYS2_ARG_CONV_EXCL='*' java $STD $LOG4J -Dgtsr.skipStructure=1 -cp "$OUT/tools;$OUT/classes;$CP" \
      BiomeBandHierarchyCheck rollback Dim78ScatterDensityCheck digest $PS $PR 16 >"$OUT/p5-digest-after.txt" 2>&1
    ea=$?
    # digest 模式在 AFTER 树上跑的就是"四键设回旧值"那一行（ProsperitySurfaceScatter 的 B0 档），
    # 因此 SCAN 行与 CHAIN 行应当逐字相同：前者 = 回退位级复现，后者 = 判据 5 非散布路径零漂移。
    n=$(diff <(grep -a "^SCAN\|^CHAIN " "$OUT/p5-digest-base.txt") \
             <(grep -a "^SCAN\|^CHAIN " "$OUT/p5-digest-after.txt") | grep -ac "^[<>]")
    db=$(grep -ao "digest=[0-9a-f]*" "$OUT/p5-digest-base.txt" | head -1 | cut -d= -f2)
    da=$(grep -ao "digest=[0-9a-f]*" "$OUT/p5-digest-after.txt" | head -1 | cut -d= -f2)
    cb=$(grep -ao "chimColPerChunk=[0-9.]*" "$OUT/p5-digest-base.txt" | head -1 | cut -d= -f2)
    ca=$(grep -ao "chimColPerChunk=[0-9.]*" "$OUT/p5-digest-after.txt" | head -1 | cut -d= -f2)
    bm=$(grep -ao "blockMean=[0-9.]*" "$OUT/p5-digest-after.txt" | head -1 | cut -d= -f2)
    chb2=$(grep -a "^CHAIN" "$OUT/p5-digest-base.txt"); cha2=$(grep -a "^CHAIN" "$OUT/p5-digest-after.txt")
    echo "   BASE EXIT=$eb AFTER EXIT=$ea 样本=${PS}seed×${PR}区  回退档/改造前摘要：$db vs $da → $([ "$db" = "$da" ] && echo 逐位相同 || echo 不同)"
    echo "   柱/chunk：BASE=$cb → ROLLBACK=$ca（判据3：柱阵必须复现）"
    echo "   非散布路径 CHAIN 行：BASE[$chb2]"
    echo "                      AFTER[$cha2]"
    echo "   明细：$OUT/p5-digest-base.txt vs $OUT/p5-digest-after.txt（SCAN+CHAIN 差异行=$n）"
    [ "$ea" = "0" ] && [ "$eb" = "0" ] || { echo "   FAIL：摘要对拍两跑有一跑非 0"; FAILS=$((FAILS + 1)); }
    [ "$db" = "$da" ] || { echo "   FAIL：回退档摘要与改造前不同（$db vs $da）⇒ 判据3「可回退」不成立"; FAILS=$((FAILS + 1)); }
    [ "$n" = "0" ] || { echo "   FAIL：SCAN/CHAIN 行有差异（$n 行）⇒ 非散布路径出现漂移"; FAILS=$((FAILS + 1)); }

    # [10b] 表层/高度/群系面逐字节对拍（BASE = p5-base 快照）
    MSYS2_ARG_CONV_EXCL='*' java $STD $LOG4J -cp "$OUT/tools;$OUT/classes;$CP" \
      BiomeBandHierarchyCheck rollback SurfaceByteParityDump 16 >"$OUT/p5-parity-after.txt" 2>"$OUT/p5-parity-after.err"
    # 工具类走 $OUT/tools（与 [4] 同口径：同一份 dump 代码），只有生产 class 指向 P5-BASE。
    # 用 $OUT/p5-base-tools 会 ClassNotFoundException（首版实测踩过，524 行差异全是 JVM 报错文本）
    MSYS2_ARG_CONV_EXCL='*' java $STD $LOG4J -cp "$OUT/tools;$OUT/p5-base-classes;$CP" \
      SurfaceByteParityDump 16 >"$OUT/p5-parity-base.txt" 2>"$OUT/p5-parity-base.err"
    # 两侧 stderr 都单独收（P6 起 AFTER 侧经 rollback 包装，包装的标记与 JVM 告警都不能混进正文，
    # 否则"整文件逐字节 diff"会因为日志而非生产行为变红——实测 1 行 / 4 行两种假漂移都踩过）
    n2=$(diff "$OUT/p5-parity-base.txt" "$OUT/p5-parity-after.txt" | grep -ac "^[<>]")
    c2=$(grep -ac "^CHUNK" "$OUT/p5-parity-after.txt")
    echo "   [10b] chunks=$c2 diff_lines=$n2（$(grep -a '^# unmapped' "$OUT/p5-parity-after.txt")）"
    [ "$n2" = "0" ] || { echo "   FAIL：表层/高度/群系面出现漂移（$n2 行）⇒ P5/P6 越界（AFTER 侧已回退 P6 三键）"; FAILS=$((FAILS + 1)); }

    # [10c] 两条新上限先 GREEN（8×4 = 8192 chunk 采样）
    KS=${P5_PIN_SEEDS:-8}; KR=${P5_PIN_REGIONS:-4}
    run "ContourBudgetCheck（P5 判据 2/3：H-3 件数+落块双上限钉 + 回退摘要 + 灵敏度）" ContourBudgetCheck $KS $KR
    run "RegionRepeatCapCheck（P5 判据 2：H-2 窗上限钉 + pinned/relaxed 灵敏度）" RegionRepeatCapCheck $KS $KR

    # [10d] 判据 2 的"人为放宽上限必须变红"：影子树单变量改 Config 默认值（不碰工作树）
    P5RED=temp/p5-red-shadow; P5REDCLS=$OUT/p5-red-classes
    P5_RED_SRC=$(echo "$REL" | sed 's|^|temp/p5-red-shadow/|' | tr '\n' ' ')
    p5red() { # p5red <标签> <sed> <工具类> <seeds> <regions>
      rm -rf "$P5RED" "$P5REDCLS"; mkdir -p "$P5REDCLS"
      cp -a src/main/java "$P5RED"
      sed -i "$2" "$P5RED/com/miaokatze/gtsr/config/Config.java"
      cmp -s src/main/java/com/miaokatze/gtsr/config/Config.java \
        "$P5RED/com/miaokatze/gtsr/config/Config.java" \
        && { echo "   $1 注入未生效（影子 Config 与工作树相同）"; FAILS=$((FAILS + 1)); }
      MSYS2_ARG_CONV_EXCL='*' javac -J-Duser.language=en -nowarn -encoding UTF-8 -cp "$CP" \
        -sourcepath "$P5RED" -d "$P5REDCLS" $P5_RED_SRC >"$OUT/p5-red-$1-javac.log" 2>&1
      if [ $? -ne 0 ]; then echo "   $1 影子树编译失败（不是 RED，是脚本坏了）"; FAILS=$((FAILS + 1)); fi
      MSYS2_ARG_CONV_EXCL='*' javac -J-Duser.language=en -nowarn -encoding UTF-8 \
        -cp "$P5REDCLS;$CP" -sourcepath "$P5RED;tools/dim1" -d "$OUT/p5-red-tools" \
        tools/dim1/SurfaceHarness.java tools/dim1/Dim78ScatterDensityCheck.java \
        tools/dim1/ContourBudgetCheck.java tools/dim1/RegionRepeatCapCheck.java \
        tools/dim1/gregtech/api/GregTechAPI.java >"$OUT/p5-red-$1-tooljavac.log" 2>&1
      if [ $? -ne 0 ]; then echo "   $1 工具影子编译失败（不是 RED，是脚本坏了）"; FAILS=$((FAILS + 1)); fi
      MSYS2_ARG_CONV_EXCL='*' java $STD -cp "$OUT/p5-red-tools;$P5REDCLS;$CP" "$3" "$4" "$5" \
        >"$OUT/p5-red-$1.txt" 2>&1
      er=$?
      grep -a "^  FAIL" "$OUT/p5-red-$1.txt" | head -3 | cut -c1-150 | sed "s/^/     /"
      tail -1 "$OUT/p5-red-$1.txt" | cut -c1-150 | sed "s/^/     /"
      echo "     $1 EXIT=$er（RED 必须非 0）log=$OUT/p5-red-$1.txt"
      [ "$er" != "0" ] || { echo "   FAIL：$1 未变红 ⇒ 本判据对这类放宽不敏感"; FAILS=$((FAILS + 1)); }
    }
    # 只演示任务包要求的两条"人为放宽上限"：H-3 件数 K 与 H-2 窗重复上限（各钉各的工具）。
    p5red K64 's|public static int prosperityScatterContoursPerChunk = 8;|public static int prosperityScatterContoursPerChunk = 64;|' ContourBudgetCheck $KS $KR
    p5red CAP999 's|public static int prosperityScatterWindowRepeatCap = 2;|public static int prosperityScatterWindowRepeatCap = 999;|' RegionRepeatCapCheck $KS $KR
    rm -rf "$P5RED" "$P5REDCLS" "$OUT/p5-red-tools"
    # 复位确认（影子树从不碰工作树，这一步只证明工作树仍是 GREEN 档）
    run "ContourBudgetCheck（RED 后工作树复位 GREEN）" ContourBudgetCheck $KS $KR
    run "RegionRepeatCapCheck（RED 后工作树复位 GREEN）" RegionRepeatCapCheck $KS $KR
  fi


  # ── [11] P6 群系带分层与城门（H-1/L6）：rollback 逐字节对拍 + 四张数字表 + 单变量 RED→GREEN ──
  # BASE 必须是<b>本片开工前</b>的快照 temp/p6-base/all/src/main/java/…（= P5 终态 b747dad）。
  # 关键口径：P6 的两个新自由度（macro 带尺度、城门条件档）本身就是被验对象，所以
  # "非本片路径零漂移"的对拍必须在 AFTER 侧把这三个键<b>设回改造前口径</b>（走 BiomeBandHierarchyCheck
  # 的 rollback 包装：反射写 Config，BASE 树无该键则登记并跳过）；另外再跑一次<b>默认档</b> dump，
  # 要求 dim79 差异为 0（dim79 零改动）而 dim78 差异 > 0（反假绿：P6 确实只在 dim78 生效）。
  echo "== [11] P6 群系带分层与城门（H-1/L6）=="
  BASE6=temp/p6-base/all
  SNAP6=$BASE6/src/main/java
  P6_BASE_FILES="com/miaokatze/gtsr/common/dimension/framework/BiomeZoneSelector.java
com/miaokatze/gtsr/common/dimension/framework/GTSRWorldChunkManager.java
com/miaokatze/gtsr/common/dimension/prosperity/ruins/city/CityPlanner.java
com/miaokatze/gtsr/common/dimension/prosperity/ruins/ProsperityWorldGenerator.java
com/miaokatze/gtsr/common/dimension/prosperity/ruins/RuinedMachinePlacer.java
com/miaokatze/gtsr/common/dimension/prosperity/ruins/ProsperityOutpostPlacer.java
com/miaokatze/gtsr/common/dimension/prosperity/ruins/ProsperitySurfaceScatter.java
com/miaokatze/gtsr/common/dimension/framework/structure/StructureRegistry.java
com/miaokatze/gtsr/config/Config.java"
  if [ ! -f "$SNAP6/com/miaokatze/gtsr/common/dimension/prosperity/ruins/city/CityPlanner.java" ]; then
    echo "   FAIL：缺 P6 BASE 快照 $SNAP6/…/CityPlanner.java（必须先自建 = 本片开工前工作树副本）"
    FAILS=$((FAILS + 1))
  else
    rm -rf "$BASE6/com" "$OUT/p6-base-classes"
    mkdir -p "$BASE6/com" "$OUT/p6-base-classes"
    cp -a src/main/java/. "$BASE6/"
    miss=0
    for rel in $P6_BASE_FILES; do
      if [ -f "$SNAP6/$rel" ]; then
        cp "$SNAP6/$rel" "$BASE6/$rel"
        cmp -s "$SNAP6/$rel" "$BASE6/$rel" || { echo "   FAIL：BASE 还原后与工作树相同（快照失效）"; miss=$((miss + 1)); }
      else
        echo "   FAIL：BASE 快照缺 $rel"; miss=$((miss + 1))
      fi
    done
    rm -f "$BASE6/$PRE_P7_NEW"   # P5 era（P6 开工前）还没有 PlacementGate（P7c 修正）
    [ "$miss" = "0" ] || FAILS=$((FAILS + 1))
    if grep -aq "prosperityCityBiomeGate" "$BASE6/com/miaokatze/gtsr/config/Config.java"; then
      echo "   FAIL：BASE 侧 Config 已含 P6 新键 ⇒ 快照不是开工前形态"; FAILS=$((FAILS + 1))
    else
      echo "   BASE 侧确认无 P6 新键（快照有效）"
    fi
    P6_SRC="$(src_list $BASE6)"
    MSYS2_ARG_CONV_EXCL='*' javac -J-Duser.language=en -nowarn -encoding UTF-8 -cp "$CP" \
      -sourcepath "$BASE6" -d "$OUT/p6-base-classes" $P6_SRC >"$OUT/p6-javac-base.log" 2>&1
    echo "COMPILE P6-BASE EXIT=$? ($(grep -ac 'error:' "$OUT/p6-javac-base.log") error)"
    [ "$(grep -ac 'error:' "$OUT/p6-javac-base.log")" = "0" ] \
      || { echo "   FAIL：P6-BASE 树编译失败（还原清单不完整，[11a] 的 0 差异会是假绿）"; FAILS=$((FAILS + 1)); } 

    # [11a] 表层/高度/群系面 512 chunk 逐字节对拍（AFTER 侧三个 P6 键回退到改造前口径）
    MSYS2_ARG_CONV_EXCL='*' java $STD $LOG4J -cp "$OUT/tools;$OUT/p6-base-classes;$CP" \
      SurfaceByteParityDump 16 >"$OUT/p6-parity-base.txt" 2>"$OUT/p6-parity-base.err"
    MSYS2_ARG_CONV_EXCL='*' java $STD $LOG4J -cp "$OUT/tools;$OUT/classes;$CP" \
      BiomeBandHierarchyCheck rollback SurfaceByteParityDump 16 >"$OUT/p6-parity-rollback.txt" 2>"$OUT/p6-parity-rb.err"
    n6=$(diff <(grep -a "^CHUNK\|^AGG\|^DIMHDR" "$OUT/p6-parity-base.txt") \
             <(grep -a "^CHUNK\|^AGG\|^DIMHDR" "$OUT/p6-parity-rollback.txt") | grep -ac "^[<>]")
    c6=$(grep -ac "^CHUNK" "$OUT/p6-parity-rollback.txt")
    rb=$(grep -a "^# P6-ROLLBACK" "$OUT/p6-parity-rb.err" | head -1)
    echo "   [11a] 回退位对拍 chunks=$c6 diff_lines=$n6｜$rb"
    [ "$n6" = "0" ] || { echo "   FAIL：P6 键回退后仍有漂移（$n6 行）⇒ 本片越界改了表层/高度/群系面的其它路径"; FAILS=$((FAILS + 1)); }

    # [11b] 默认档 dump：dim79 必须 0 差异（零改动），dim78 必须 >0（P6 真的生效，不是两边恒等）
    MSYS2_ARG_CONV_EXCL='*' java $STD $LOG4J -cp "$OUT/tools;$OUT/classes;$CP" \
      SurfaceByteParityDump 16 >"$OUT/p6-parity-default.txt" 2>"$OUT/p6-parity-def.err"
    d79=$(diff <(grep -a "^CHUNK dim=79\|^AGG dim=79" "$OUT/p6-parity-base.txt") \
              <(grep -a "^CHUNK dim=79\|^AGG dim=79" "$OUT/p6-parity-default.txt") | grep -ac "^[<>]")
    d78=$(diff <(grep -a "^CHUNK dim=78\|^AGG dim=78" "$OUT/p6-parity-base.txt") \
              <(grep -a "^CHUNK dim=78\|^AGG dim=78" "$OUT/p6-parity-default.txt") | grep -ac "^[<>]")
    echo "   [11b] 默认档 vs 开工前：dim79 差异行=$d79（必须 0）dim78 差异行=$d78（必须 >0 = 本片目标行为）"
    [ "$d79" = "0" ] || { echo "   FAIL：dim79 出现漂移（$d79 行）——本片禁止动 dim79"; FAILS=$((FAILS + 1)); }
    [ "$d78" -gt "0" ] || { echo "   FAIL：dim78 默认档与开工前逐字节相同 ⇒ macro 带根本没生效（假绿）"; FAILS=$((FAILS + 1)); }

    # [11c] 四张数字表（判据 1）：T1/T2 = macro × 门档，T3 = 份额守恒，T4 = 暴露增量对账
    echo "   [11c] 表 1/表 2（8 seed × 8 区 = 16384 chunk；选定档行尾标 SELECTED）"
    MSYS2_ARG_CONV_EXCL='*' java $STD -cp "$OUT/tools;$OUT/classes;$CP" \
      CityBiomeGateCheck table 8 8 >"$OUT/p6-table-t1t2.txt" 2>&1
    et12=$?
    echo "     EXIT=$et12 明细=$OUT/p6-table-t1t2.txt"
    [ "$et12" = "0" ] || { echo "   FAIL：表 1/表 2 出口非 0"; FAILS=$((FAILS + 1)); }
    grep -a "SELECTED" "$OUT/p6-table-t1t2.txt" | cut -c1-210 | sed 's/^/     /'
    echo "   [11c] 表 3（带尺度/chunk 尺度份额 vs 45-30-15-10 + 连贯性）"
    MSYS2_ARG_CONV_EXCL='*' java $STD -cp "$OUT/tools;$OUT/classes;$CP" \
      BiomeBandHierarchyCheck table 8 256 >"$OUT/p6-table-t3.txt" 2>&1
    et3=$?
    echo "     EXIT=$et3 明细=$OUT/p6-table-t3.txt"
    [ "$et3" = "0" ] || { echo "   FAIL：表 3 出口非 0"; FAILS=$((FAILS + 1)); }
    grep -aq "^T3" "$OUT/p6-table-t3.txt" || { echo "   FAIL：表 3 没有 T3 行（mode 派发被改坏过，实测踩过）"; FAILS=$((FAILS + 1)); }
    grep -a "^T3" "$OUT/p6-table-t3.txt" | cut -c1-210 | sed 's/^/     /'
    echo "   [11c] 表 4（真实链两跑：净暴露增量 vs P5 §10 预算；report 模式只申报不判红）"
    MSYS2_ARG_CONV_EXCL='*' java $STD $LOG4J -cp "$OUT/tools;$OUT/classes;$CP" \
      CityBiomeGateCheck load ${P6_LOAD_SEEDS:-8} ${P6_LOAD_REGIONS:-8} >"$OUT/p6-table-t4.txt" 2>&1
    e6=$?
    grep -a "^T4\|BUDGET" "$OUT/p6-table-t4.txt" | cut -c1-270 | sed 's/^/     /'
    echo "     EXIT=$e6 明细=$OUT/p6-table-t4.txt（要把预算当硬门禁：CityBiomeGateCheck load <s> <r> strict）"
    [ "$e6" = "0" ] || { echo "   FAIL：表 4 跑非 0（真缺陷，不是预算申报）"; FAILS=$((FAILS + 1)); }
    if grep -aq "BUDGET-EXCEEDED" "$OUT/p6-table-t4.txt"; then
      echo "     注意：城门净暴露增量超 P5 让出的 8pp 预算 ⇒ 按任务包口径上报主代理裁决（本片不改 K）"
    fi

    # [11d] 判据 9：单变量 RED→GREEN（影子树，绝不碰工作树）
    P6RED=temp/p6-red-shadow; P6REDCLS=$OUT/p6-red-classes
    P6_RED_SRC=$(echo "$REL" | sed 's|^|temp/p6-red-shadow/|' | tr '\n' ' ')
    p6red() { # p6red <标签> <sed 表达式> <目标文件> <工具类> <工具参数...>
      local label="$1" expr="$2" file="$3" tool="$4"; shift 4
      rm -rf "$P6RED" "$P6REDCLS"; mkdir -p "$P6REDCLS"
      cp -a src/main/java "$P6RED"
      sed -i "$expr" "$P6RED/$file"
      cmp -s "$P6RED/$file" "src/main/java/$file" \
        && { echo "   $label 注入未生效（影子文件与工作树相同）"; FAILS=$((FAILS + 1)); }
      MSYS2_ARG_CONV_EXCL='*' javac -J-Duser.language=en -nowarn -encoding UTF-8 -cp "$CP" \
        -sourcepath "$P6RED" -d "$P6REDCLS" $P6_RED_SRC >"$OUT/p6-red-$label-javac.log" 2>&1
      if [ $? -ne 0 ]; then echo "   $label 影子树编译失败（脚本坏了，不是 RED）"; FAILS=$((FAILS + 1)); fi
      MSYS2_ARG_CONV_EXCL='*' javac -J-Duser.language=en -nowarn -encoding UTF-8 \
        -cp "$P6REDCLS;$CP" -sourcepath "$P6RED;tools/dim1" -d "$OUT/p6-red-tools" \
        tools/dim1/SurfaceHarness.java tools/dim1/Dim78ScatterDensityCheck.java \
        tools/dim1/BiomeBandHierarchyCheck.java tools/dim1/CityBiomeGateCheck.java \
        tools/dim1/gregtech/api/GregTechAPI.java >"$OUT/p6-red-$label-tooljavac.log" 2>&1
      if [ $? -ne 0 ]; then echo "   $label 工具影子编译失败（脚本坏了，不是 RED）"; FAILS=$((FAILS + 1)); fi
      MSYS2_ARG_CONV_EXCL='*' java $STD -cp "$OUT/p6-red-tools;$P6REDCLS;$CP" "$tool" "$@" \
        >"$OUT/p6-red-$label.txt" 2>&1
      local er=$?
      grep -a "^  FAIL" "$OUT/p6-red-$label.txt" | head -3 | cut -c1-150 | sed "s/^/     /"
      tail -1 "$OUT/p6-red-$label.txt" | cut -c1-150 | sed "s/^/     /"
      echo "     $label EXIT=$er（RED 必须非 0）log=$OUT/p6-red-$label.txt"
      [ "$er" != "0" ] || { echo "   FAIL：$label 未变红 ⇒ 本判据对这类破坏不敏感（假绿）"; FAILS=$((FAILS + 1)); }
    }
    P6F_CFG=com/miaokatze/gtsr/config/Config.java
    P6F_PLAN=com/miaokatze/gtsr/common/dimension/prosperity/ruins/city/CityPlanner.java
    # R1：关掉城门 ⇒ "城市 100% 落锈蚀草原带" 必须变红
    p6red R1_GATE_OFF 's|public static int prosperityCityBiomeGate = 1;|public static int prosperityCityBiomeGate = 0;|' \
      "$P6F_CFG" CityBiomeGateCheck assert 8 8
    # R2a：把 macro 带尺度改回 16（改造前单层）⇒ 分层申报档断言必须变红
    p6red R2_MACRO16 's|public static int prosperityBiomeMacroBandChunks = 64;|public static int prosperityBiomeMacroBandChunks = 16;|' \
      "$P6F_CFG" BiomeBandHierarchyCheck assert src/main/java 8 128
    # R2b：同一破坏在城门侧的表现——"整座城能落进同一带"（64 带 57.7%）必须塌回 16 口径而变红
    p6red R2b_MACRO16 's|public static int prosperityBiomeMacroBandChunks = 64;|public static int prosperityBiomeMacroBandChunks = 16;|' \
      "$P6F_CFG" CityBiomeGateCheck assert 8 8
    # R3：门条件从"锚点所在 macro 带"退化成"中心 chunk 的 16 格分区"⇒ 锚点带 100% 必须变红
    p6red R3_CENTER_CHUNK 's|return steppeBandAt(worldSeed, plan.getCenterChunkX(), plan.getCenterChunkZ());|return BiomeZoneSelector.select(worldSeed, plan.getCenterChunkX(), plan.getCenterChunkZ(), PROSPERITY_BAND_WEIGHTS.length, PROSPERITY_BAND_WEIGHTS, BiomeZoneSelector.MICRO_CELL_CHUNKS, BiomeZoneSelector.ZONE_SALT_PROSPERITY) == GTSRBiomeAuthority.BiomeId.RUSTED_STEPPE.rosterIndex();|' \
      "$P6F_PLAN" CityBiomeGateCheck assert 8 8
    rm -rf "$P6RED" "$P6REDCLS" "$OUT/p6-red-tools"
    run "BiomeBandHierarchyCheck（RED 后工作树复位 GREEN）" BiomeBandHierarchyCheck assert src/main/java 8 128
    run "CityBiomeGateCheck（RED 后工作树复位 GREEN）" CityBiomeGateCheck assert 8 8
  fi

  # ── [12] P7 结构放置契约（任务包判据 1/2/3/6/8 的真地形档 + 单变量 RED→GREEN） ──
  echo "== [12] P7/P7c 结构放置契约：真地形 16384 chunk 六张表 + 影子树单变量 RED =="
  run "PlacementContractCheck all 8 8（T2 接地逐点 / T3 列扫对账 / T4 贴脸率三档 / T5 落点位移）" \
    PlacementContractCheck all 8 8
  echo "   明细：$OUT/PlacementContractCheck-all.out（T2..T7 六张表；P7c 后 C6 与 C5/C5b/C6b/C6c/C7/C8/C9/C10/G1 全绿）"
  P7RED=temp/p7-red-shadow; P7REDCLS=$OUT/p7-red-classes
  P7_RED_SRC=$(echo "$REL" | sed 's|^|temp/p7-red-shadow/|' | tr '\n' ' ')
  p7red() { # p7red <标签> <sed 表达式> <目标文件> <工具参数...>——同 p6red 口径，只改影子树
    local label="$1" expr="$2" file="$3"; shift 3
    rm -rf "$P7RED" "$P7REDCLS" "$OUT/p7-red-tools"; mkdir -p "$P7REDCLS" "$OUT/p7-red-tools"
    cp -a src/main/java "$P7RED"
    sed -i "$expr" "$P7RED/$file"
    cmp -s "$P7RED/$file" "src/main/java/$file" \
      && { echo "   $label 注入未生效（影子文件与工作树相同）"; FAILS=$((FAILS + 1)); }
    MSYS2_ARG_CONV_EXCL='*' javac -J-Duser.language=en -nowarn -encoding UTF-8 -cp "$CP" \
      -sourcepath "$P7RED" -d "$P7REDCLS" $P7_RED_SRC >"$OUT/p7-red-$label-javac.log" 2>&1
    if [ $? -ne 0 ]; then echo "   $label 影子树编译失败（脚本坏了，不是 RED）"; FAILS=$((FAILS + 1)); fi
    MSYS2_ARG_CONV_EXCL='*' javac -J-Duser.language=en -nowarn -encoding UTF-8 \
      -cp "$P7REDCLS;$CP" -sourcepath "$P7RED;tools/dim1" -d "$OUT/p7-red-tools" \
      tools/dim1/SurfaceHarness.java tools/dim1/Dim78ScatterDensityCheck.java \
      tools/dim1/PlacementContractCheck.java tools/dim1/gregtech/api/GregTechAPI.java \
      >"$OUT/p7-red-$label-tooljavac.log" 2>&1
    if [ $? -ne 0 ]; then echo "   $label 工具影子编译失败（脚本坏了，不是 RED）"; FAILS=$((FAILS + 1)); fi
    MSYS2_ARG_CONV_EXCL='*' java $STD -Xmx2g -cp "$OUT/p7-red-tools;$P7REDCLS;$CP" \
      PlacementContractCheck "$@" >"$OUT/p7-red-$label.txt" 2>&1
    local er=$?
    grep -a "^  FAIL" "$OUT/p7-red-$label.txt" | head -4 | cut -c1-150 | sed "s/^/     /"
    tail -1 "$OUT/p7-red-$label.txt" | cut -c1-150 | sed "s/^/     /"
    echo "     $label EXIT=$er（RED 必须非 0）log=$OUT/p7-red-$label.txt"
    [ "$er" != "0" ] || { echo "   FAIL：$label 未变红 ⇒ 判据 1 对这类破坏不敏感（假绿）"; FAILS=$((FAILS + 1)); }
  }
  F_OP=com/miaokatze/gtsr/common/dimension/prosperity/ruins/ProsperityOutpostPlacer.java
  F_MC=com/miaokatze/gtsr/common/dimension/prosperity/ruins/RuinedMachinePlacer.java
  # R1：outpost 的落块真值退回"无条件报成功"（改造前 :342-352 的原样）⇒ A7 假成功计数必须变红
  p7red R1_OUTPOST_LIE 's|return permit.commit(counter.solid());|return true;|' "$F_OP" all 1 8
  # R2：机器那一侧同样退回报成功 ⇒ A7/A8（预算位与互斥位被假成功吞掉）必须变红
  p7red R2_MACHINE_LIE 's|return permit.commit(counter.solid());|return true;|' "$F_MC" all 1 8
  rm -rf "$P7RED" "$P7REDCLS" "$OUT/p7-red-tools"
  echo "   P7 合计 assertions=$(total_assertions PlacementContractCheck)"

  # ── [13] P7c：结构 H-2 语义改判（窗上限 = 命中集条件、贴脸 = 同族间距） ──
  # 三件事：① 非本片路径零漂移（表层/高度/群系/散布档 512 chunk 逐字节，BASE = 本片开工前快照）；
  #        ② CHAIN 硬门槛 GREEN（Dim78ScatterDensityCheck 结构命中为 0 必须判红）；
  #        ③ 四个单变量 RED：窗上限退回排名配额 / 上限退化成"只数首次" / 间距档默认改 0 /
  #           两 placer 的 roll 整段静默失效（CHAIN 归零）。
  echo "== [13] P7c 结构 H-2 语义改判：零漂移对拍 + CHAIN 硬门槛 + 四条单变量 RED =="
  BASE7C=temp/p7c-base/all
  SNAP7C=temp/p7c-base/src/main/java
  P7C_BASE_FILES="com/miaokatze/gtsr/common/dimension/framework/structure/PlacementGate.java
com/miaokatze/gtsr/common/dimension/framework/structure/StructureRegistry.java
com/miaokatze/gtsr/common/dimension/prosperity/ruins/ProsperityWorldGenerator.java
com/miaokatze/gtsr/common/dimension/prosperity/ruins/ProsperitySurfaceScatter.java
com/miaokatze/gtsr/common/dimension/prosperity/ruins/RuinedMachinePlacer.java
com/miaokatze/gtsr/common/dimension/prosperity/ruins/ProsperityOutpostPlacer.java
com/miaokatze/gtsr/config/Config.java"
  if [ ! -f "$SNAP7C/com/miaokatze/gtsr/common/dimension/framework/structure/PlacementGate.java" ]; then
    echo "   FAIL：缺 P7c BASE 快照 $SNAP7C/…/PlacementGate.java（必须是本片第一个写入之前的工作树副本）"
    FAILS=$((FAILS + 1))
  else
    rm -rf "$BASE7C" ; mkdir -p "$BASE7C/com" "$OUT/p7c-base-classes"
    cp -a src/main/java/. "$BASE7C/"
    miss7c=0
    for rel in $P7C_BASE_FILES; do
      if [ -f "$SNAP7C/$rel" ]; then
        cp "$SNAP7C/$rel" "$BASE7C/$rel"
      else
        echo "   FAIL：P7c BASE 快照缺 $rel"; miss7c=$((miss7c + 1))
      fi
    done
    [ "$miss7c" = "0" ] || FAILS=$((FAILS + 1))
    # 反假绿：BASE 侧必须"还没有 P7c 的新键"，否则快照其实是改造后
    if grep -aq "prosperityStructureFamilyGapChunks" "$BASE7C/com/miaokatze/gtsr/config/Config.java"; then
      echo "   FAIL：P7c-BASE 侧 Config 已含 prosperityStructureFamilyGapChunks ⇒ 快照不是开工前形态"
      FAILS=$((FAILS + 1))
    else
      echo "   P7c-BASE 侧确认无间距新键（快照有效）"
    fi
    P7C_SRC="$(src_era7p $BASE7C)"
    MSYS2_ARG_CONV_EXCL='*' javac -J-Duser.language=en -nowarn -encoding UTF-8 -cp "$CP" \
      -sourcepath "$BASE7C" -d "$OUT/p7c-base-classes" $P7C_SRC >"$OUT/p7c-javac-base.log" 2>&1
    echo "COMPILE P7C-BASE EXIT=$? ($(grep -ac 'error:' "$OUT/p7c-javac-base.log") error)"
    [ "$(grep -ac 'error:' "$OUT/p7c-javac-base.log")" = "0" ] \
      || { echo "   FAIL：P7c-BASE 树编译失败"; FAILS=$((FAILS + 1)); }
    MSYS2_ARG_CONV_EXCL='*' java $STD $LOG4J -cp "$OUT/tools;$OUT/classes;$CP" \
      SurfaceByteParityDump 16 >"$OUT/p7c-parity-after.txt" 2>"$OUT/p7c-parity-after.err"
    MSYS2_ARG_CONV_EXCL='*' java $STD $LOG4J -cp "$OUT/tools;$OUT/p7c-base-classes;$CP" \
      SurfaceByteParityDump 16 >"$OUT/p7c-parity-base.txt" 2>"$OUT/p7c-parity-base.err"
    n7c=$(diff "$OUT/p7c-parity-base.txt" "$OUT/p7c-parity-after.txt" | grep -ac "^[<>]")
    c7c=$(grep -ac "^CHUNK" "$OUT/p7c-parity-after.txt")
    d78_7c=$(grep -ac "^CHUNK dim=78" "$OUT/p7c-parity-after.txt")
    d79_7c=$(grep -ac "^CHUNK dim=79" "$OUT/p7c-parity-after.txt")
    echo "   [13a] 非本片路径逐字节对拍 chunks=$c7c (dim78=$d78_7c dim79=$d79_7c) diff_lines=$n7c"
    [ "$n7c" = "0" ] || { echo "   FAIL：表层/高度/群系/散布档出现漂移（$n7c 行）⇒ P7c 越界"; FAILS=$((FAILS + 1)); }

    # [13b] CHAIN 硬门槛 GREEN：默认档下结构命中必须非零（P7b 现场是 0/0/0 且静默通过）
    run "Dim78ScatterDensityCheck digest 8 2（P5 摘要 + P7c CHAIN 硬门槛）" \
      Dim78ScatterDensityCheck digest 8 2
    grep -a "^CHAIN-GATE" "$OUT/Dim78ScatterDensityCheck-digest.out" | cut -c1-170 | sed 's/^/     /'
    grep -aq "CHAIN-GATE .* verdict=OK" "$OUT/Dim78ScatterDensityCheck-digest.out" \
      || { echo "   FAIL：CHAIN-GATE 未出 OK（结构命中被判红或门槛行缺失）"; FAILS=$((FAILS + 1)); }

    # [13c] 四条单变量 RED（影子树，绝不碰工作树）
    P7CRED=temp/p7c-red-shadow; P7CREDCLS=$OUT/p7c-red-classes
    P7C_RED_SRC=$(echo "$REL" | sed 's|^|temp/p7c-red-shadow/|' | tr '\n' ' ')
    p7cred() { # p7cred <标签> <sed 表达式> <目标文件> <工具类> <工具参数...>
      local label="$1" expr="$2" file="$3" tool="$4"; shift 4
      rm -rf "$P7CRED" "$P7CREDCLS" "$OUT/p7c-red-tools"; mkdir -p "$P7CREDCLS" "$OUT/p7c-red-tools"
      cp -a src/main/java "$P7CRED"
      sed -i "$expr" "$P7CRED/$file"
      cmp -s "$P7CRED/$file" "src/main/java/$file" \
        && { echo "   $label 注入未生效（影子文件与工作树相同）"; FAILS=$((FAILS + 1)); }
      MSYS2_ARG_CONV_EXCL='*' javac -J-Duser.language=en -nowarn -encoding UTF-8 -cp "$CP" \
        -sourcepath "$P7CRED" -d "$P7CREDCLS" $P7C_RED_SRC >"$OUT/p7c-red-$label-javac.log" 2>&1
      if [ $? -ne 0 ]; then echo "   $label 影子树编译失败（脚本坏了，不是 RED）"; FAILS=$((FAILS + 1)); fi
      MSYS2_ARG_CONV_EXCL='*' javac -J-Duser.language=en -nowarn -encoding UTF-8 \
        -cp "$P7CREDCLS;$CP" -sourcepath "$P7CRED;tools/dim1" -d "$OUT/p7c-red-tools" \
        tools/dim1/SurfaceHarness.java tools/dim1/Dim78ScatterDensityCheck.java \
        tools/dim1/PlacementContractCheck.java tools/dim1/gregtech/api/GregTechAPI.java \
        >"$OUT/p7c-red-$label-tooljavac.log" 2>&1
      if [ $? -ne 0 ]; then echo "   $label 工具影子编译失败（脚本坏了，不是 RED）"; FAILS=$((FAILS + 1)); fi
      MSYS2_ARG_CONV_EXCL='*' java $STD -Xmx2g -cp "$OUT/p7c-red-tools;$P7CREDCLS;$CP" "$tool" "$@" \
        >"$OUT/p7c-red-$label.txt" 2>&1
      local er=$?
      grep -a "^  FAIL" "$OUT/p7c-red-$label.txt" | head -4 | cut -c1-150 | sed "s/^/     /"
      tail -1 "$OUT/p7c-red-$label.txt" | cut -c1-150 | sed "s/^/     /"
      echo "     $label EXIT=$er（RED 必须非 0）log=$OUT/p7c-red-$label.txt"
      [ "$er" != "0" ] || { echo "   FAIL：$label 未变红 ⇒ 判据对这类破坏不敏感（假绿）"; FAILS=$((FAILS + 1)); }
      return $er
    }
    F_GATE=com/miaokatze/gtsr/common/dimension/framework/structure/PlacementGate.java
    F_CFG=com/miaokatze/gtsr/config/Config.java
    F_MC=com/miaokatze/gtsr/common/dimension/prosperity/ruins/RuinedMachinePlacer.java
    F_OP=com/miaokatze/gtsr/common/dimension/prosperity/ruins/ProsperityOutpostPlacer.java
    # R1：窗上限退回 P7/P7b 的"排名配额"（与掷骰独立 ⇒ 密度乘子）⇒ C5/C6/C8 必须变红
    p7cred R1_RANKING_QUOTA 's|final long mine = windowRepeatHash(worldSeed, cx, cz, templateName);|if (cap >= 0) { return ProsperitySurfaceScatter.windowAllows(worldSeed, cx, cz, templateIdOf(templateName), cap); }\n        final long mine = windowRepeatHash(worldSeed, cx, cz, templateName);|' \
      "$F_GATE" PlacementContractCheck census 8 8
    # R2：语义退化成"只数首次"（cap 档位被忽略，等价 cap≡1）⇒ C6b/C8/A5b 必须变红
    p7cred R2_ONLY_FIRST 's|if (++ahead >= cap) {|if (++ahead >= 1) {|' "$F_GATE" PlacementContractCheck census 8 8
    # R3：默认间距档改回 0（治疗被关掉）⇒ A0 申报与 C10 都必须变红
    p7cred R3_GAP_OFF 's|public static int prosperityStructureFamilyGapChunks = 1;|public static int prosperityStructureFamilyGapChunks = 0;|' \
      "$F_CFG" PlacementContractCheck source
    # R4：两 placer 的 roll 整段静默失效（= P7b 的 CHAIN 0/0/0 现场）⇒ CHAIN 硬门槛必须判红
    p7cred R4_CHAIN_DEAD 's|final Random r = new Random(GTSRWorldgenHash.chunkSeed(worldSeed, cx, cz) . SALT_MACHINE);|if (chance >= 0) { return null; }\n        final Random r = new Random(GTSRWorldgenHash.chunkSeed(worldSeed, cx, cz) ^ SALT_MACHINE);|' \
      "$F_MC" Dim78ScatterDensityCheck digest 4 4
    p7cred R4b_CHAIN_DEAD 's|final Random r = new Random(GTSRWorldgenHash.chunkSeed(worldSeed, cx, cz) . SALT_OUTPOST);|if (chance >= 0) { return null; }\n        final Random r = new Random(GTSRWorldgenHash.chunkSeed(worldSeed, cx, cz) ^ SALT_OUTPOST);|' \
      "$F_OP" Dim78ScatterDensityCheck digest 4 4
    rm -rf "$P7CRED" "$P7CREDCLS" "$OUT/p7c-red-tools"
    run "PlacementContractCheck census 8 8（RED 后工作树复位 GREEN）" PlacementContractCheck census 8 8
    run "Dim78ScatterDensityCheck digest 8 2（RED 后工作树复位 GREEN）" Dim78ScatterDensityCheck digest 8 2
  fi

  # ── [14] P5b 散布成簇（plan §7.2 U3 改判「K=1 + 必须成簇（高方差）」） ──
  # 四件事：① 非本片路径零漂移（表层/高度/群系 512 chunk 逐字节，BASE=temp/p5b-base 开工前快照，
  #           带 P7c 的"BASE 编译零 error"硬门槛）；
  #        ② 判据 5 回退链①：ClusterMode=0 的 K8 行与 P5-BASE 树同名行 SCAN 逐字节一致（逐位退化）；
  #        ③ 判据 5 回退链②的确定性面：成簇 variance 小档双跑 digest 相等（同 seed 同结果）；
  #        ④ 判据 4 灵敏度：两条成簇参数的影子树单变量 RED（denom=1 → ContourBudgetCheck 红；
  #           ClusterMode=0 → SurfaceGateUnifyCheck 口径 C 新带红）——带若对破坏不敏感就是假绿。
  # 大样本实测（判据 1/2/3/5 的数字与断言）在 ScatterClusterVarianceCheck 的 variance/structure 档，
  # 本脚本只挂快档；全量数字见 plan/investigation/p5b-clustered-scatter-20260919.md。
  echo "== [14] P5b 散布成簇：零漂移对拍 + 逐位退化摘要 + 双跑 SHA + 两条影子 RED =="
  BASE5B=temp/p5b-base/all
  SNAP5B=temp/p5b-base/src/main/java
  P5B_BASE_FILES="com/miaokatze/gtsr/common/dimension/prosperity/ruins/ProsperitySurfaceScatter.java
com/miaokatze/gtsr/common/dimension/prosperity/ruins/ProsperityWorldGenerator.java
com/miaokatze/gtsr/config/Config.java"
  if [ ! -f "$SNAP5B/com/miaokatze/gtsr/common/dimension/prosperity/ruins/ProsperitySurfaceScatter.java" ]; then
    echo "   FAIL：缺 P5b BASE 快照 $SNAP5B/…/ProsperitySurfaceScatter.java（必须=本片第一个写入前的工作树副本）"
    FAILS=$((FAILS + 1))
  else
    rm -rf "$BASE5B/com" "$OUT/p5b-base-classes" "$OUT/p5b-base-tools"
    mkdir -p "$BASE5B/com" "$OUT/p5b-base-classes" "$OUT/p5b-base-tools"
    cp -a src/main/java/. "$BASE5B/"
    miss5b=0
    for rel in $P5B_BASE_FILES; do
      if [ -f "$SNAP5B/$rel" ]; then
        cp "$SNAP5B/$rel" "$BASE5B/$rel"
        cmp -s "$SNAP5B/$rel" "$BASE5B/$rel" || true
        cmp -s "$BASE5B/$rel" "src/main/java/$rel" \
          && { echo "   FAIL：BASE 还原后与工作树相同（快照失效）"; miss5b=$((miss5b + 1)); }
      else
        echo "   FAIL：BASE 快照缺 $rel"; miss5b=$((miss5b + 1))
      fi
    done
    [ "$miss5b" = "0" ] || FAILS=$((FAILS + 1))
    # 反假绿：BASE 侧不得含 P5b 新键（否则快照其实是改造后，对拍/退化都会自比）
    if grep -aq "prosperityScatterClusterMode" "$BASE5B/com/miaokatze/gtsr/config/Config.java"; then
      echo "   FAIL：P5b-BASE 侧 Config 已含成簇键 ⇒ 快照不是开工前形态"; FAILS=$((FAILS + 1))
    else
      echo "   P5b-BASE 侧确认无成簇键（快照有效）"
    fi
    # P8 同步（本清单之外的 era 修正）：P8 把 PlacementGate 的窗上限读取点改成
    # familyWindowRepeatCap()，它会引用废墟族的 Config 键 ⇒「当前 PlacementGate + P5b 的 Config」编不过
    # （实测 1 error：cannot find symbol prosperityRuinWindowRepeatCap）。P5b 没碰 PlacementGate，
    # 所以从"本片开工前快照"取它 = P5b 终态那一份；同时删掉本 era 还不存在的 ruin 新包。
    cp "$SNAP8_LATE/com/miaokatze/gtsr/common/dimension/framework/structure/PlacementGate.java"       "$BASE5B/com/miaokatze/gtsr/common/dimension/framework/structure/PlacementGate.java"
    rm -rf "$BASE5B/com/miaokatze/gtsr/common/dimension/prosperity/ruins/ruin"
    P5B_SRC="$(src_era7p $BASE5B)"
    MSYS2_ARG_CONV_EXCL='*' javac -J-Duser.language=en -nowarn -encoding UTF-8 -cp "$CP" \
      -sourcepath "$BASE5B" -d "$OUT/p5b-base-classes" $P5B_SRC >"$OUT/p5b-javac-base.log" 2>&1
    echo "COMPILE P5B-BASE EXIT=$? ($(grep -ac 'error:' "$OUT/p5b-javac-base.log") error)"
    # 判据 6 的 P7c 硬门槛：BASE 编译零 error，否则对拍退化成自比，直接判红
    [ "$(grep -ac 'error:' "$OUT/p5b-javac-base.log")" = "0" ] \
      || { echo "   FAIL：P5b-BASE 树编译失败（还原清单不完整，[14a] 的 0 差异会是假绿）"; FAILS=$((FAILS + 1)); }
    MSYS2_ARG_CONV_EXCL='*' javac -J-Duser.language=en -nowarn -encoding UTF-8 \
      -cp "$OUT/p5b-base-classes;$CP" -sourcepath "$BASE5B;tools/dim1" -d "$OUT/p5b-base-tools" \
      tools/dim1/SurfaceHarness.java tools/dim1/Dim78ScatterDensityCheck.java \
      tools/dim1/gregtech/api/GregTechAPI.java >"$OUT/p5b-javac-base-tools.log" 2>&1
    echo "COMPILE P5B-BASE-TOOLS EXIT=$? ($(grep -ac 'error:' "$OUT/p5b-javac-base-tools.log") error)"

    # [14a] 判据 6：非本片路径零漂移（表层/高度/群系 512 chunk 逐字节）
    MSYS2_ARG_CONV_EXCL='*' java $STD $LOG4J -cp "$OUT/tools;$OUT/classes;$CP" \
      SurfaceByteParityDump 16 >"$OUT/p5b-parity-after.txt" 2>"$OUT/p5b-parity-after.err"
    MSYS2_ARG_CONV_EXCL='*' java $STD $LOG4J -cp "$OUT/tools;$OUT/p5b-base-classes;$CP" \
      SurfaceByteParityDump 16 >"$OUT/p5b-parity-base.txt" 2>"$OUT/p5b-parity-base.err"
    n5b=$(diff "$OUT/p5b-parity-base.txt" "$OUT/p5b-parity-after.txt" | grep -ac "^[<>]")
    c5b=$(grep -ac "^CHUNK" "$OUT/p5b-parity-after.txt")
    echo "   [14a] 零漂移对拍 chunks=$c5b diff_lines=$n5b（BASE=temp/p5b-base，编译零 error 门槛已过）"
    [ "$n5b" = "0" ] || { echo "   FAIL：表层/高度/群系面出现漂移（$n5b 行）⇒ P5b 越界"; FAILS=$((FAILS + 1)); }

    # [14b] 判据 5 回退链①：BASE（P5 终态树）与 AFTER 的 "K8-摘竖向件" 行（六参行自带 ClusterMode=0
    #       = P5 均匀档）SCAN 行逐字节一致 ⇒ 成簇改造没碰均匀档一行行为
    # 两侧都带 -Dgtsr.skipStructure=1（P7c 判据 4 口径）：结构侧默认档本片被改判（cap 3→8），
    # 含前序的表会把"结构差"混进"均匀档逐位退化"这一件事（首版实测不带开关时 K8 行差 3 块）。
    MSYS2_ARG_CONV_EXCL='*' java $STD $LOG4J -Dgtsr.skipStructure=1 -cp "$OUT/p5b-base-tools;$OUT/p5b-base-classes;$CP" \
      Dim78ScatterDensityCheck grid 2 2 16 >"$OUT/p5b-uniform-base.txt" 2>&1
    eb1=$?
    MSYS2_ARG_CONV_EXCL='*' java $STD $LOG4J -Dgtsr.skipStructure=1 -cp "$OUT/tools;$OUT/classes;$CP" \
      Dim78ScatterDensityCheck grid 2 2 16 >"$OUT/p5b-uniform-after.txt" 2>&1
    ea1=$?
    ub=$(grep -a "^SCAN label=K8-摘竖向件" "$OUT/p5b-uniform-base.txt")
    ua=$(grep -a "^SCAN label=K8-摘竖向件" "$OUT/p5b-uniform-after.txt")
    echo "   [14b] BASE[$ub]"
    echo "         AFTER[$ua]"
    [ "$eb1" = "0" ] && [ "$ea1" = "0" ] || { echo "   FAIL：均匀档退化对拍有一跑非 0"; FAILS=$((FAILS + 1)); }
    [ -n "$ub" ] && [ "$ub" = "$ua" ] || { echo "   FAIL：ClusterMode=0 的 K8 行与 P5 树不同 ⇒ 回退链①不成立"; FAILS=$((FAILS + 1)); }

    # [14c] 判据 5/1 的确定性面：成簇 variance 小档双跑（跨进程）digest 相等
    MSYS2_ARG_CONV_EXCL='*' java $STD $LOG4J -cp "$OUT/tools;$OUT/classes;$CP" \
      ScatterClusterVarianceCheck variance 2 2 >"$OUT/p5b-variance-run1.txt" 2>&1
    e1=$?
    MSYS2_ARG_CONV_EXCL='*' java $STD $LOG4J -cp "$OUT/tools;$OUT/classes;$CP" \
      ScatterClusterVarianceCheck variance 2 2 >"$OUT/p5b-variance-run2.txt" 2>&1
    e2=$?
    d1=$(grep -a "^HIST label=CLU-DEFAULT" "$OUT/p5b-variance-run1.txt" | grep -ao "digest=[0-9a-f]*")
    d2=$(grep -a "^HIST label=CLU-DEFAULT" "$OUT/p5b-variance-run2.txt" | grep -ao "digest=[0-9a-f]*")
    echo "   [14c] variance(2x2) 双跑 EXIT=$e1/$e2 digest $d1 vs $d2 → $([ -n "$d1" ] && [ "$d1" = "$d2" ] && echo 同seed同结果 || echo 不同)"
    [ "$e1" = "0" ] && [ "$e2" = "0" ] || { echo "   FAIL：variance 小档跑非 0（判据 1 带或判据 5 断言被破坏）"; FAILS=$((FAILS + 1)); }
    [ -n "$d1" ] && [ "$d1" = "$d2" ] || { echo "   FAIL：双跑 digest 不同 ⇒ 确定性被破坏"; FAILS=$((FAILS + 1)); }

    # [14d] 判据 4 灵敏度：两条成簇参数的影子树单变量 RED（不碰工作树）
    P5BRED=temp/p5b-red-shadow; P5BREDCLS=$OUT/p5b-red-classes
    P5B_RED_SRC=$(echo "$REL" | sed 's|^|temp/p5b-red-shadow/|' | tr '\n' ' ')
    p5bred() { # p5bred <标签> <sed> <工具类> <工具参数...>
      local label="$1" expr="$2" tool="$3"; shift 3
      rm -rf "$P5BRED" "$P5BREDCLS" "$OUT/p5b-red-tools"; mkdir -p "$P5BREDCLS" "$OUT/p5b-red-tools"
      cp -a src/main/java "$P5BRED"
      sed -i "$expr" "$P5BRED/com/miaokatze/gtsr/config/Config.java"
      cmp -s "$P5BRED/com/miaokatze/gtsr/config/Config.java" src/main/java/com/miaokatze/gtsr/config/Config.java \
        && { echo "   $label 注入未生效（影子 Config 与工作树相同）"; FAILS=$((FAILS + 1)); }
      MSYS2_ARG_CONV_EXCL='*' javac -J-Duser.language=en -nowarn -encoding UTF-8 -cp "$CP" \
        -sourcepath "$P5BRED" -d "$P5BREDCLS" $P5B_RED_SRC >"$OUT/p5b-red-$label-javac.log" 2>&1
      if [ $? -ne 0 ]; then echo "   $label 影子树编译失败（脚本坏了，不是 RED）"; FAILS=$((FAILS + 1)); fi
      MSYS2_ARG_CONV_EXCL='*' javac -J-Duser.language=en -nowarn -encoding UTF-8 \
        -cp "$P5BREDCLS;$CP" -sourcepath "$P5BRED;tools/dim1" -d "$OUT/p5b-red-tools" \
        tools/dim1/SurfaceHarness.java tools/dim1/Dim78ScatterDensityCheck.java \
        tools/dim1/ContourBudgetCheck.java tools/dim1/SurfaceGateUnifyCheck.java \
        tools/dim1/ScatterClusterVarianceCheck.java tools/dim1/gregtech/api/GregTechAPI.java \
        >"$OUT/p5b-red-$label-tooljavac.log" 2>&1
      if [ $? -ne 0 ]; then echo "   $label 工具影子编译失败（脚本坏了，不是 RED）"; FAILS=$((FAILS + 1)); fi
      MSYS2_ARG_CONV_EXCL='*' java $STD $LOG4J -cp "$OUT/p5b-red-tools;$P5BREDCLS;$CP" "$tool" "$@" \
        >"$OUT/p5b-red-$label.txt" 2>&1
      local er=$?
      grep -a "^  FAIL" "$OUT/p5b-red-$label.txt" | head -3 | cut -c1-150 | sed "s/^/     /"
      tail -1 "$OUT/p5b-red-$label.txt" | cut -c1-150 | sed "s/^/     /"
      echo "     $label EXIT=$er（RED 必须非 0）log=$OUT/p5b-red-$label.txt"
      [ "$er" != "0" ] || { echo "   FAIL：$label 未变红 ⇒ 申报带对这类破坏不敏感（假绿）"; FAILS=$((FAILS + 1)); }
    }
    # R1：场中心概率放宽成每格必中（denom 3→1）⇒ ContourBudgetCheck 的 A1b 声明 + B 行为带必红
    p5bred R1_DENOM1 's|public static int prosperityScatterClusterFieldChanceDenom = 3;|public static int prosperityScatterClusterFieldChanceDenom = 1;|' ContourBudgetCheck 8 4
    # R2：成簇开关默认改 0（退回 P5 均匀档）⇒ SurfaceGateUnifyCheck 口径 C 新带 [98.6,99.6] 必红（实测均匀档 95.641）
    #     （这是判据 4 点名的"散布吞列趋势变了，带必须随实测走"的反证：不随就红）
    p5bred R2_CLUSTER_OFF 's|public static int prosperityScatterClusterMode = 1;|public static int prosperityScatterClusterMode = 0;|' SurfaceGateUnifyCheck assert src/main/java
    rm -rf "$P5BRED" "$P5BREDCLS" "$OUT/p5b-red-tools"
    # 复位确认（影子树从不碰工作树，本步只证明工作树仍是 GREEN 档）
    run "ContourBudgetCheck（P5b RED 后工作树复位 GREEN）" ContourBudgetCheck 8 4
    run "SurfaceGateUnifyCheck（P5b RED 后口径 C 带复位 GREEN）" SurfaceGateUnifyCheck assert src/main/java
  fi
  # ── [15] P8 城外废墟族（破坏结构）──
  # 四件事：① 非本片路径零漂移（表层/高度/群系 512 chunk 逐字节 + 散布档 SCAN 逐字节，
  #           BASE=temp/p8-base 开工前快照，带"BASE 编译零 error"硬门槛）；
  #        ② 密度对照表（判据 4）：真地形上"关新族 vs 开新族"同批 chunk 的座数/窗均/贴脸率/窗内 max；
  #        ③ 确定性双跑（判据 2/5）：展示页数据件跨进程双跑逐字节一致；
  #        ④ 三条单变量影子 RED：派生算子被削 / 盐失效 / micro 调制失效。
  echo "== [15] P8 城外废墟族：零漂移对拍 + 密度对照 + 双跑 SHA + 三条影子 RED =="
  BASE8=temp/p8-base/all
  SNAP8=temp/p8-base/src/main/java
  P8_BASE_FILES="com/miaokatze/gtsr/common/dimension/framework/structure/PlacementGate.java
com/miaokatze/gtsr/common/dimension/framework/GTSRBiomeAuthority.java
com/miaokatze/gtsr/common/dimension/framework/GTSRBiomeBase.java
com/miaokatze/gtsr/common/dimension/framework/GTSRChunkProviderBase.java
com/miaokatze/gtsr/common/dimension/framework/GTSRWorldChunkManager.java
com/miaokatze/gtsr/common/dimension/prosperity/ruins/ProsperityWorldGenerator.java
com/miaokatze/gtsr/config/Config.java"
  if [ ! -f "$SNAP8/com/miaokatze/gtsr/common/dimension/framework/structure/PlacementGate.java" ]; then
    echo "   FAIL：缺 P8 BASE 快照 $SNAP8（必须是本片第一个写入之前的工作树副本）"
    FAILS=$((FAILS + 1))
  else
    rm -rf "$BASE8"; mkdir -p "$BASE8/com" "$OUT/p8-base-classes" "$OUT/p8-base-tools"
    cp -a src/main/java/. "$BASE8/"
    miss8=0
    for rel in $P8_BASE_FILES; do
      if [ -f "$SNAP8/$rel" ]; then
        cp "$SNAP8/$rel" "$BASE8/$rel"
        cmp -s "$BASE8/$rel" "src/main/java/$rel" \
          && { echo "   FAIL：BASE 还原后与工作树相同（快照失效）"; miss8=$((miss8 + 1)); }
      else
        echo "   FAIL：P8 BASE 快照缺 $rel"; miss8=$((miss8 + 1))
      fi
    done
    # 废墟族是本片<b>新增</b>的包 ⇒ BASE 树里必须没有它（留着会让 BASE 侧编排器引用到本片才有的
    # PlacementGate.FAMILY_RUIN，而 PlacementGate 已还原 ⇒ BASE 编不过，对拍退化成自比）
    rm -rf "$BASE8/com/miaokatze/gtsr/common/dimension/prosperity/ruins/ruin"
    [ "$miss8" = "0" ] || FAILS=$((FAILS + 1))
    if grep -aq "prosperityRuinChance" "$BASE8/com/miaokatze/gtsr/config/Config.java"; then
      echo "   FAIL：P8-BASE 侧 Config 已含废墟族新键 ⇒ 快照不是开工前形态"; FAILS=$((FAILS + 1))
    else
      echo "   P8-BASE 侧确认无废墟族新键（快照有效）"
    fi
    P8_SRC="$(src_era7p $BASE8)"
    MSYS2_ARG_CONV_EXCL='*' javac -J-Duser.language=en -nowarn -encoding UTF-8 -cp "$CP" \
      -sourcepath "$BASE8" -d "$OUT/p8-base-classes" $P8_SRC >"$OUT/p8-javac-base.log" 2>&1
    echo "COMPILE P8-BASE EXIT=$? ($(grep -ac 'error:' "$OUT/p8-javac-base.log") error)"
    # 判据 7 的硬门槛（沿用 P7c/P5b 口径）：BASE 编译零 error，否则下面的 0 差异是假绿
    [ "$(grep -ac 'error:' "$OUT/p8-javac-base.log")" = "0" ] \
      || { echo "   FAIL：P8-BASE 树编译失败（还原清单不完整，[15a] 的 0 差异会是假绿）"; FAILS=$((FAILS + 1)); }
    grep -a "error:" "$OUT/p8-javac-base.log" | head -5 | sed 's/^/     /'

    # [15a] 表层/高度/群系逐字节对拍（512 chunk = 256 × 两维）
    MSYS2_ARG_CONV_EXCL='*' java $STD $LOG4J -cp "$OUT/tools;$OUT/classes;$CP" \
      SurfaceByteParityDump 16 >"$OUT/p8-parity-after.txt" 2>"$OUT/p8-parity-after.err"
    MSYS2_ARG_CONV_EXCL='*' java $STD $LOG4J -cp "$OUT/tools;$OUT/p8-base-classes;$CP" \
      SurfaceByteParityDump 16 >"$OUT/p8-parity-base.txt" 2>"$OUT/p8-parity-base.err"
    n8=$(diff "$OUT/p8-parity-base.txt" "$OUT/p8-parity-after.txt" | grep -ac "^[<>]")
    c8=$(grep -ac "^CHUNK" "$OUT/p8-parity-after.txt")
    echo "   [15a] 表层/高度/群系逐字节对拍 chunks=$c8 diff_lines=$n8（BASE=temp/p8-base，零 error 门槛已过）"
    [ "$n8" = "0" ] || { echo "   FAIL：表层/高度/群系面出现漂移（$n8 行）⇒ P8 越界"; FAILS=$((FAILS + 1)); }

    # [15a2] 散布档逐字节：两侧都在净地形上跑（-Dgtsr.skipStructure=1，P7c 判据 4 口径）
    MSYS2_ARG_CONV_EXCL='*' javac -J-Duser.language=en -nowarn -encoding UTF-8 \
      -cp "$OUT/p8-base-classes;$CP" -sourcepath "$BASE8;tools/dim1" -d "$OUT/p8-base-tools" \
      tools/dim1/SurfaceHarness.java tools/dim1/Dim78ScatterDensityCheck.java \
      tools/dim1/gregtech/api/GregTechAPI.java >"$OUT/p8-javac-base-tools.log" 2>&1
    echo "   COMPILE P8-BASE-TOOLS EXIT=$? ($(grep -ac 'error:' "$OUT/p8-javac-base-tools.log") error)"
    MSYS2_ARG_CONV_EXCL='*' java $STD $LOG4J -Dgtsr.skipStructure=1 -Xmx2g \
      -cp "$OUT/p8-base-tools;$OUT/p8-base-classes;$CP" Dim78ScatterDensityCheck digest 2 2 \
      >"$OUT/p8-scatter-base.txt" 2>&1
    esb=$?
    MSYS2_ARG_CONV_EXCL='*' java $STD $LOG4J -Dgtsr.skipStructure=1 -Xmx2g \
      -cp "$OUT/tools;$OUT/classes;$CP" Dim78ScatterDensityCheck digest 2 2 \
      >"$OUT/p8-scatter-after.txt" 2>&1
    esa=$?
    sb=$(grep -a "^SCAN" "$OUT/p8-scatter-base.txt" | sha256sum | cut -c1-16)
    sa=$(grep -a "^SCAN" "$OUT/p8-scatter-after.txt" | sha256sum | cut -c1-16)
    # 只比"数据行"：两跑的 stderr 头里带各自的 classpath 目录名（Unsafe WARNING），全文 diff 会恒差 2 行
    nd=$(diff <(grep -av "^WARNING\|^Picked up" "$OUT/p8-scatter-base.txt")                <(grep -av "^WARNING\|^Picked up" "$OUT/p8-scatter-after.txt") | grep -ac "^[<>]")
    echo "   [15a2] 散布档 SCAN 摘要 BASE=$sb AFTER=$sa 行差=$nd（EXIT $esb/$esa）"
    { [ "$esb" = "0" ] && [ "$esa" = "0" ]; } || { echo "   FAIL：散布档两跑有一跑非 0"; FAILS=$((FAILS + 1)); }
    [ "$sb" = "$sa" ] || { echo "   FAIL：散布档摘要漂移 ⇒ P8 越界碰了 P5/P5b"; FAILS=$((FAILS + 1)); }

    # [15b] 判据 4：真地形"关新族 vs 开新族"对照（DENSITY-RISE 行如实打印上升量）
    run "RuinFamilyCheck all 4 4（P8 判据 2/3/4/5/6 全档：真地形 4096 chunk 密度对照）" \
      RuinFamilyCheck all 4 4
    grep -a "^DENSITY-RISE" "$OUT/RuinFamilyCheck-all.out" | cut -c1-260 | sed 's/^/     /'
    grep -a "^TE-CHECK" "$OUT/RuinFamilyCheck-all.out" | cut -c1-200 | sed 's/^/     /'
    grep -a "TE-CHECK RED PROBE" "$OUT/RuinFamilyCheck-all.out" | cut -c1-200 | sed 's/^/     /'

    # [15c] 判据 2/5：展示页数据件跨进程双跑逐字节（导出器自带进程内双跑，这里补跨进程那一面）
    MSYS2_ARG_CONV_EXCL='*' java $STD $LOG4J -cp "$OUT/tools;$OUT/classes;$CP" StructureViewerExport \
      >"$OUT/p8-export-run1.log" 2>&1
    e1=$?
    MSYS2_ARG_CONV_EXCL='*' java $STD $LOG4J -cp "$OUT/tools;$OUT/classes;$CP" StructureViewerExport \
      >"$OUT/p8-export-run2.log" 2>&1
    e2=$?
    x1=$(grep -a "^SHA256 json=" "$OUT/p8-export-run1.log" | sed -n "s/^SHA256 json=//p")
    x2=$(grep -a "^SHA256 json=" "$OUT/p8-export-run2.log" | sed -n "s/^SHA256 json=//p")
    echo "   [15c] 数据件双跑 EXIT=$e1/$e2 json SHA $x1 vs $x2 -> $([ -n "$x1" ] && [ "$x1" = "$x2" ] && echo 逐字节一致 || echo 不一致)"
    { [ "$e1" = "0" ] && [ "$e2" = "0" ]; } || { echo "   FAIL：导出器跑非 0"; FAILS=$((FAILS + 1)); }
    [ -n "$x1" ] && [ "$x1" = "$x2" ] || { echo "   FAIL：数据件双跑不一致 ⇒ 确定性被破坏"; FAILS=$((FAILS + 1)); }
    grep -a "^EXPORT variants=" "$OUT/p8-export-run2.log" | cut -c1-170 | sed 's/^/     /'
  fi

  # [15d] 三条单变量影子 RED（影子树，绝不碰工作树）
  P8RED=temp/p8-red-shadow; P8REDCLS=$OUT/p8-red-classes
  P8_RED_SRC=$(echo "$REL" | sed 's|^|temp/p8-red-shadow/|' | tr '\n' ' ')
  p8red() { # p8red <标签> <sed 表达式> <目标文件> <工具类> <工具参数...>
    local label="$1" expr="$2" file="$3" tool="$4"; shift 4
    rm -rf "$P8RED" "$P8REDCLS" "$OUT/p8-red-tools"; mkdir -p "$P8REDCLS" "$OUT/p8-red-tools"
    cp -a src/main/java "$P8RED"
    sed -i "$expr" "$P8RED/$file"
    cmp -s "$P8RED/$file" "src/main/java/$file" \
      && { echo "   $label 注入未生效（影子文件与工作树相同）"; FAILS=$((FAILS + 1)); }
    MSYS2_ARG_CONV_EXCL='*' javac -J-Duser.language=en -nowarn -encoding UTF-8 -cp "$CP" \
      -sourcepath "$P8RED" -d "$P8REDCLS" $P8_RED_SRC \
      "$P8RED/com/miaokatze/gtsr/common/dimension/prosperity/ruins/ruin/RuinPlacer.java" \
      "$P8RED/com/miaokatze/gtsr/common/dimension/prosperity/ruins/ruin/RuinShapes.java" \
      "$P8RED/com/miaokatze/gtsr/common/dimension/prosperity/ruins/ruin/RuinTemplate.java" \
      "$P8RED/com/miaokatze/gtsr/common/dimension/prosperity/ruins/ruin/RuinDamageOps.java" \
      >"$OUT/p8-red-$label-javac.log" 2>&1
    if [ $? -ne 0 ]; then echo "   $label 影子树编译失败（脚本坏了，不是 RED）"; FAILS=$((FAILS + 1)); fi
    MSYS2_ARG_CONV_EXCL='*' javac -J-Duser.language=en -nowarn -encoding UTF-8 \
      -cp "$P8REDCLS;$CP" -sourcepath "$P8RED;tools/dim1" -d "$OUT/p8-red-tools" \
      tools/dim1/SurfaceHarness.java tools/dim1/Dim78ScatterDensityCheck.java \
      tools/dim1/RuinFamilyCheck.java tools/dim1/PlacementContractCheck.java \
      tools/dim1/gregtech/api/GregTechAPI.java >"$OUT/p8-red-$label-tooljavac.log" 2>&1
    if [ $? -ne 0 ]; then echo "   $label 工具影子编译失败（脚本坏了，不是 RED）"; FAILS=$((FAILS + 1)); fi
    MSYS2_ARG_CONV_EXCL='*' java $STD $LOG4J -Xmx2g -cp "$OUT/p8-red-tools;$P8REDCLS;$CP" "$tool" "$@" \
      >"$OUT/p8-red-$label.txt" 2>&1
    local er=$?
    grep -a "^  FAIL\|^  - " "$OUT/p8-red-$label.txt" | head -3 | cut -c1-150 | sed "s/^/     /"
    tail -1 "$OUT/p8-red-$label.txt" | cut -c1-150 | sed "s/^/     /"
    echo "     $label EXIT=$er（RED 必须非 0）log=$OUT/p8-red-$label.txt"
    [ "$er" != "0" ] || { echo "   FAIL：$label 未变红 ⇒ 判据对这类破坏不敏感（假绿）"; FAILS=$((FAILS + 1)); }
    return $er
  }
  F_RSH=com/miaokatze/gtsr/common/dimension/prosperity/ruins/ruin/RuinShapes.java
  F_RPL=com/miaokatze/gtsr/common/dimension/prosperity/ruins/ruin/RuinPlacer.java
  F_RDO=com/miaokatze/gtsr/common/dimension/prosperity/ruins/ruin/RuinDamageOps.java
  # R1：把本族的 opt-in 损毁位改回 false ⇒ A9「ruin 条目 allowsDamagedVariant = true」必须变红
  p8red R1_OPTIN_OFF 's|^                    true));|                    false));|' \
    "$F_RPL" RuinFamilyCheck fast
  # R2：派生盐失效（换盐不变形）⇒ B2 盐敏感必须变红
  p8red R2_SALT_DEAD 's|cellSeed(salt,|cellSeed(0L,|' \
    "$F_RDO" RuinFamilyCheck fast
  # R3：micro 调制整条链失效（强度恒中性）⇒ E4d/E5 的行为差必须变红
  p8red R3_MICRO_DEAD 's|return Config.prosperityRuinMicroModulation ? raw : 1.0F;|return 1.0F;|' \
    "$F_RPL" RuinFamilyCheck fast
  # R4：opt-in 守卫被摘（false 也照样算缺失率）⇒ G1「恒 0」必须变红
  p8red R4_GUARD_GONE 's|^            return 0;$|            return 20;|' \
    "$F_RDO" RuinFamilyCheck fast
  rm -rf "$P8RED" "$P8REDCLS" "$OUT/p8-red-tools"
  run "RuinFamilyCheck fast（P8 RED 后工作树复位 GREEN）" RuinFamilyCheck fast

  # ── [16] P9 自定义生物层（L7）──
  # ① 非本片路径零漂移（表层/高度/群系 512 chunk 逐字节 + 散布档 SCAN 逐字节，
  #    BASE=temp/p9-base 的 e3e14a6=P8 终态快照，带"BASE 编译零 error"硬门槛）；
  # ② 五条单变量影子 RED（判据 2 的缺席门/幂等、判据 3 的 L1 身份、判据 2 回退档的总开关、
  #    判据 4 的纹理可解析）——每条都必须变红，否则对应判据是假绿；
  # ③ 本段<b>不</b>测任何生物行为（无头冒烟对实体无效，见 CreatureSpawnAuthorityCheck 类注释）。
  echo "== [16] P9 生物层（L7）：非本片路径零漂移对拍 + 五条单变量影子 RED =="
  BASE9=temp/p9-base/all
  SNAP9=temp/p9-base/src/main/java
  P9_BASE_FILES="com/miaokatze/gtsr/common/dimension/framework/GTSRBiomeBase.java
com/miaokatze/gtsr/common/dimension/framework/GTSRBiomeAuthority.java
com/miaokatze/gtsr/common/dimension/framework/GTSRWorldChunkManager.java
com/miaokatze/gtsr/common/dimension/framework/GTSRChunkProviderBase.java
com/miaokatze/gtsr/config/Config.java"
  if [ ! -f "$SNAP9/com/miaokatze/gtsr/common/dimension/framework/GTSRBiomeBase.java" ]; then
    echo "   FAIL：缺 P9 BASE 快照 $SNAP9（必须是本片第一个写入之前的工作树副本 = e3e14a6）"
    FAILS=$((FAILS + 1))
  else
    rm -rf "$BASE9"; mkdir -p "$BASE9/com" "$OUT/p9-base-classes" "$OUT/p9-base-tools"
    cp -a src/main/java/. "$BASE9/"
    miss9=0
    for rel in $P9_BASE_FILES; do
      if [ -f "$SNAP9/$rel" ]; then
        cp "$SNAP9/$rel" "$BASE9/$rel"
        cmp -s "$BASE9/$rel" "src/main/java/$rel" \
          && { echo "   FAIL：BASE 还原后与工作树相同（快照失效）$rel"; miss9=$((miss9 + 1)); }
      else
        echo "   FAIL：P9 BASE 快照缺 $rel"; miss9=$((miss9 + 1))
      fi
    done
    # 生物层是本片<b>新增</b>的包 ⇒ BASE 树里必须没有它（否则 BASE 侧的 GTSRBiomeBase 快照
    # 会被引用到本片才有的 setCreatureSpawnPolicy，对拍退化成自比）
    rm -rf "$BASE9/com/miaokatze/gtsr/common/dimension/prosperity/entity"
    [ "$miss9" = "0" ] || FAILS=$((FAILS + 1))
    if grep -aq "prosperityCreaturesEnabled" "$BASE9/com/miaokatze/gtsr/config/Config.java"; then
      echo "   FAIL：P9-BASE 侧 Config 已含生物层新键 ⇒ 快照不是开工前形态"; FAILS=$((FAILS + 1))
    else
      echo "   P9-BASE 确认无生物层新键、无 L7 填充口（快照有效 = e3e14a6 / P8 终态）"
    fi
    grep -aq "setCreatureSpawnPolicy" "$BASE9/com/miaokatze/gtsr/common/dimension/framework/GTSRBiomeBase.java" \
      && { echo "   FAIL：P9-BASE 侧 GTSRBiomeBase 已有 L7 填充口 ⇒ 快照不是开工前形态"; FAILS=$((FAILS + 1)); }
    P9_SRC="$(src_era7p $BASE9)"
    MSYS2_ARG_CONV_EXCL='*' javac -J-Duser.language=en -nowarn -encoding UTF-8 -cp "$CP" \
      -sourcepath "$BASE9" -d "$OUT/p9-base-classes" $P9_SRC >"$OUT/p9-javac-base.log" 2>&1
    echo "COMPILE P9-BASE EXIT=$? ($(grep -ac 'error:' "$OUT/p9-javac-base.log") error)"
    # 判据 7 的硬门槛（沿用 P7c/P5b/P8 口径）：BASE 编译零 error，否则 [16a] 的 0 差异是假绿
    [ "$(grep -ac 'error:' "$OUT/p9-javac-base.log")" = "0" ] \
      || { echo "   FAIL：P9-BASE 树编译失败（还原清单不完整，[16a] 的 0 差异会是假绿）"; FAILS=$((FAILS + 1)); }
    grep -a "error:" "$OUT/p9-javac-base.log" | head -5 | sed 's/^/     /'

    # [16a] 表层/高度/群系逐字节对拍（512 chunk = 256 × 两维）
    MSYS2_ARG_CONV_EXCL='*' java $STD $LOG4J -cp "$OUT/tools;$OUT/classes;$CP" \
      SurfaceByteParityDump 16 >"$OUT/p9-parity-after.txt" 2>"$OUT/p9-parity-after.err"
    MSYS2_ARG_CONV_EXCL='*' java $STD $LOG4J -cp "$OUT/tools;$OUT/p9-base-classes;$CP" \
      SurfaceByteParityDump 16 >"$OUT/p9-parity-base.txt" 2>"$OUT/p9-parity-base.err"
    n9=$(diff "$OUT/p9-parity-base.txt" "$OUT/p9-parity-after.txt" | grep -ac "^[<>]")
    c9=$(grep -ac "^CHUNK" "$OUT/p9-parity-after.txt")
    d78_9=$(grep -ac "^CHUNK dim=78" "$OUT/p9-parity-after.txt")
    d79_9=$(grep -ac "^CHUNK dim=79" "$OUT/p9-parity-after.txt")
    echo "   [16a] chunks=$c9 (dim78=$d78_9 dim79=$d79_9) diff_lines=$n9  BASE=temp/p9-base（零 error 门槛已过）"
    [ "$n9" = "0" ] || { echo "   FAIL：表层/高度/群系面出现漂移（$n9 行）⇒ P9 越界"; FAILS=$((FAILS + 1)); }

    # [16a2] 散布档逐字节：两侧都在净地形上跑（-Dgtsr.skipStructure=1，P7c 判据 4 口径）
    MSYS2_ARG_CONV_EXCL='*' javac -J-Duser.language=en -nowarn -encoding UTF-8 \
      -cp "$OUT/p9-base-classes;$CP" -sourcepath "$BASE9;tools/dim1" -d "$OUT/p9-base-tools" \
      tools/dim1/SurfaceHarness.java tools/dim1/Dim78ScatterDensityCheck.java \
      tools/dim1/gregtech/api/GregTechAPI.java >"$OUT/p9-javac-base-tools.log" 2>&1
    echo "   COMPILE P9-BASE-TOOLS EXIT=$? ($(grep -ac 'error:' "$OUT/p9-javac-base-tools.log") error)"
    MSYS2_ARG_CONV_EXCL='*' java $STD $LOG4J -Dgtsr.skipStructure=1 -Xmx2g \
      -cp "$OUT/p9-base-tools;$OUT/p9-base-classes;$CP" Dim78ScatterDensityCheck digest 2 2 \
      >"$OUT/p9-scatter-base.txt" 2>&1
    esb9=$?
    MSYS2_ARG_CONV_EXCL='*' java $STD $LOG4J -Dgtsr.skipStructure=1 -Xmx2g \
      -cp "$OUT/tools;$OUT/classes;$CP" Dim78ScatterDensityCheck digest 2 2 \
      >"$OUT/p9-scatter-after.txt" 2>&1
    esa9=$?
    sb9=$(grep -a "^SCAN" "$OUT/p9-scatter-base.txt" | sha256sum | cut -c1-16)
    sa9=$(grep -a "^SCAN" "$OUT/p9-scatter-after.txt" | sha256sum | cut -c1-16)
    nd9=$(diff <(grep -av "^WARNING\|^Picked up" "$OUT/p9-scatter-base.txt")                <(grep -av "^WARNING\|^Picked up" "$OUT/p9-scatter-after.txt") | grep -ac "^[<>]")
    echo "   [16a2] 散布档 SCAN 摘要 BASE=$sb9 AFTER=$sa9 行差=$nd9（EXIT $esb9/$esa9）"
    { [ "$esb9" = "0" ] && [ "$esa9" = "0" ]; } || { echo "   FAIL：散布档两跑有一跑非 0"; FAILS=$((FAILS + 1)); }
    [ "$sb9" = "$sa9" ] || { echo "   FAIL：散布档摘要漂移 ⇒ P9 越界碰了 P5/P5b"; FAILS=$((FAILS + 1)); }

    # [16b] 五条单变量影子 RED（影子树，绝不碰工作树）
    P9RED=temp/p9-red-shadow; P9REDCLS=$OUT/p9-red-classes
    P9_RED_SRC=$(echo "$REL" | sed 's|^|temp/p9-red-shadow/|' | tr '\n' ' ')
    p9red() { # p9red <标签> <sed 表达式> <目标文件> <工具模式...>
      local label="$1" expr="$2" file="$3"; shift 3
      rm -rf "$P9RED" "$P9REDCLS" "$OUT/p9-red-tools"; mkdir -p "$P9REDCLS" "$OUT/p9-red-tools"
      cp -a src/main/java "$P9RED"
      sed -i "$expr" "$P9RED/$file"
      cmp -s "$P9RED/$file" "src/main/java/$file" \
        && { echo "   $label 注入未生效（影子文件与工作树相同）"; FAILS=$((FAILS + 1)); }
      MSYS2_ARG_CONV_EXCL='*' javac -J-Duser.language=en -nowarn -encoding UTF-8 -cp "$CP" \
        -sourcepath "$P9RED" -d "$P9REDCLS" $P9_RED_SRC >"$OUT/p9-red-$label-javac.log" 2>&1
      if [ $? -ne 0 ]; then echo "   $label 影子树编译失败（脚本坏了，不是 RED）"; FAILS=$((FAILS + 1)); fi
      MSYS2_ARG_CONV_EXCL='*' javac -J-Duser.language=en -nowarn -encoding UTF-8 \
        -cp "$P9REDCLS;$CP" -sourcepath "$P9RED;tools/dim1" -d "$OUT/p9-red-tools" \
        tools/dim1/SurfaceHarness.java tools/dim1/CreatureSpawnAuthorityCheck.java \
        >"$OUT/p9-red-$label-tooljavac.log" 2>&1
      if [ $? -ne 0 ]; then echo "   $label 工具影子编译失败（脚本坏了，不是 RED）"; FAILS=$((FAILS + 1)); fi
      MSYS2_ARG_CONV_EXCL='*' java $STD $LOG4J -Xmx2g \
        -cp "$OUT/p9-red-tools;$P9REDCLS;$MCRES;$CP" CreatureSpawnAuthorityCheck "$@" \
        >"$OUT/p9-red-$label.txt" 2>&1
      local er=$?
      grep -a "^  FAIL\|^  - " "$OUT/p9-red-$label.txt" | head -3 | cut -c1-150 | sed "s/^/     /"
      tail -1 "$OUT/p9-red-$label.txt" | cut -c1-150 | sed "s/^/     /"
      echo "     $label EXIT=$er（RED 必须非 0）log=$OUT/p9-red-$label.txt"
      [ "$er" != "0" ] || { echo "   FAIL：$label 未变红 ⇒ 对应判据对这类破坏不敏感（假绿）"; FAILS=$((FAILS + 1)); }
      return $er
    }
    F_BBASE=com/miaokatze/gtsr/common/dimension/framework/GTSRBiomeBase.java
    F_PROVIDER=com/miaokatze/gtsr/common/dimension/framework/GTSRChunkProviderBase.java
    F_REG=com/miaokatze/gtsr/common/dimension/prosperity/entity/GTSRCreatureRegistry.java
    F_ROSTER=com/miaokatze/gtsr/common/dimension/prosperity/entity/GTSRCreatureRoster.java
    # R1：带乘子矩阵的"缺席位"被改成 1（喷气口沼泽的齿轮鸽 0→1）⇒ B1 的缺席判定与字面量表必红
    #     （判据 2 的"该带不出现该档"语义被钉住；addCreatureSpawn 里的 weight<=0 门只是防御性双保险）
    p9red R1_ABSENT_BAND_DEAD 's|{ 3, 5, 1, 0 }, { 1, 2, 0, 5 }|{ 3, 5, 1, 1 }, { 1, 2, 0, 5 }|' \
      "$F_ROSTER" all
    # R2：幂等位失效（每次读列表都再填一遍）⇒ F1「重跑 3 次条目不变」必红（判据 6）
    p9red R2_IDEMPOTENT_DEAD 's|if (this.creatureSpawnsApplied) {|if (false) {|' "$F_BBASE" all
    # R3：L1 身份出口接错维度 ⇒ C2 的逐坐标核对必红，而 C1 的源级钉仍绿
    #     （证明"行为级"那条腿真的在独立咬合，不是源级断言的复读）
    p9red R3_L1_WRONG_DIM 's|forDimension(world\.provider\.dimensionId)|forDimension(0)|' \
      "$F_PROVIDER" all
    # R4：总开关的 fill 侧闸门被摘 ⇒ 判据 2 的回退档（off 模式）必红
    p9red R4_SWITCH_GATE_DEAD 's|^            if (!Config.prosperityCreaturesEnabled) {|            if (false) {|' \
      "$F_REG" off
    # R5：城窗乘子失效 ⇒ G3「城窗 ×2」必红
    p9red R5_CITY_MULT_DEAD 's|^    public static final int CITY_WINDOW_WEIGHT_MULTIPLIER = 2;|    public static final int CITY_WINDOW_WEIGHT_MULTIPLIER = 1;|' \
      "$F_ROSTER" all
    # R6：纹理路径改成指不存在的 ⇒ D2「资源实际可解析」必红（防隐形实体的正向断言被钉住）
    p9red R6_TEX_DEAD 's|"textures/entity/chicken.png"|"textures/entity/gtsr_not_there_pigeon.png"|' \
      "$F_ROSTER" all
    rm -rf "$P9RED" "$P9REDCLS" "$OUT/p9-red-tools"
    runres "CreatureSpawnAuthorityCheck all（P9 RED 后工作树复位 GREEN）" CreatureSpawnAuthorityCheck all
  fi

  # ── [17] P14 tpdim 群系定位：非本片路径零漂移（512 chunk 逐字节，BASE=temp/p14-base=开工前快照 9735cef）──
  # P14 生产面只动三件：GTSRCommand（不在 BASE 编译清单，指令层不进生成链）、Config（仅追加两键，
  # 不改任何既有键名/默认值/范围）、GTSRBiomeAuthority（仅追加只读定位出口，配槽/降级/身份零触碰）。
  # 硬门槛照 P7c 口径：BASE 编译零 error，否则对拍退化成自比直接判红。
  echo "== [17] P14：512 chunk 逐字节对拍（BASE=p14-base 开工前快照，含 BASE 编译零 error 硬门槛） =="
  BASE14=temp/p14-base/all
  SNAP14=temp/p14-base/src/main/java
  P14_BASE_FILES="com/miaokatze/gtsr/config/Config.java
com/miaokatze/gtsr/common/dimension/framework/GTSRBiomeAuthority.java
com/miaokatze/gtsr/common/commands/GTSRCommand.java"
  if [ ! -f "$SNAP14/com/miaokatze/gtsr/common/commands/GTSRCommand.java" ]; then
    echo "   FAIL：缺 P14 BASE 快照 $SNAP14（必须先自建 = 本片第一个写入之前的工作树副本/git show 9735cef）"
    FAILS=$((FAILS + 1))
  else
    rm -rf "$BASE14" "$OUT/p14-base-classes"
    mkdir -p "$BASE14/com" "$OUT/p14-base-classes"
    cp -a src/main/java/. "$BASE14/"
    miss14=0
    for rel in $P14_BASE_FILES; do
      if [ -f "$SNAP14/$rel" ]; then
        cp "$SNAP14/$rel" "$BASE14/$rel"
      else
        echo "   FAIL：P14 BASE 快照缺 $rel"; miss14=$((miss14 + 1))
      fi
    done
    [ "$miss14" = "0" ] || FAILS=$((FAILS + 1))
    # 反假绿：BASE 侧不得含 P14 新键（否则快照其实是改造后，0 差异是自我比对口径）
    if grep -aq "tpdimBiomeSearchMaxRadiusChunks" "$BASE14/com/miaokatze/gtsr/config/Config.java"; then
      echo "   FAIL：P14-BASE 侧 Config 已含 tpdim 新键 ⇒ 快照不是开工前形态"; FAILS=$((FAILS + 1))
    else
      echo "   P14-BASE 侧确认无 tpdim 新键（快照有效）"
    fi
    P14_SRC="$(prefix $BASE14)"
    MSYS2_ARG_CONV_EXCL='*' javac -J-Duser.language=en -nowarn -encoding UTF-8 -cp "$CP" \
      -sourcepath "$BASE14" -d "$OUT/p14-base-classes" $P14_SRC >"$OUT/p14-javac-base.log" 2>&1
    echo "COMPILE P14-BASE EXIT=$? ($(grep -ac 'error:' "$OUT/p14-javac-base.log") error)"
    [ "$(grep -ac 'error:' "$OUT/p14-javac-base.log")" = "0" ] \
      || { echo "   FAIL：P14-BASE 树编译失败（还原清单不完整，[17a] 的 0 差异会是假绿）"; FAILS=$((FAILS + 1)); }
    MSYS2_ARG_CONV_EXCL='*' java $STD $LOG4J -cp "$OUT/tools;$OUT/classes;$CP" \
      SurfaceByteParityDump 16 >"$OUT/p14-parity-after.txt" 2>"$OUT/p14-parity-after.err"
    MSYS2_ARG_CONV_EXCL='*' java $STD $LOG4J -cp "$OUT/tools;$OUT/p14-base-classes;$CP" \
      SurfaceByteParityDump 16 >"$OUT/p14-parity-base.txt" 2>"$OUT/p14-parity-base.err"
    n14=$(diff "$OUT/p14-parity-base.txt" "$OUT/p14-parity-after.txt" | grep -ac "^[<>]")
    c14=$(grep -ac "^CHUNK" "$OUT/p14-parity-after.txt")
    d78_14=$(grep -ac "^CHUNK dim=78" "$OUT/p14-parity-after.txt")
    d79_14=$(grep -ac "^CHUNK dim=79" "$OUT/p14-parity-after.txt")
    echo "   [17a] 逐字节对拍 chunks=$c14 (dim78=$d78_14 dim79=$d79_14) diff_lines=$n14"
    [ "$n14" = "0" ] || { echo "   FAIL：表层/高度/群系面出现漂移（$n14 行）⇒ P14 越界碰了生成链"; FAILS=$((FAILS + 1)); }
  fi

  # ── [18] P13 死代码清扫：非本片路径零漂移（512 chunk 逐字节，BASE=temp/p13-base=开工前快照 04ede7c）──
  # P13 生产面只删「全仓零消费方」的成员（StructureBuilder 7 个成员 + RuinShapes.isRuinName +
  # GTSRCreatureRoster.cityWindowMultiplier + ShatteredBiomes 的 skyFogColorFor(BiomeGenBase) 重载），
  # 判定逻辑与数值一字未改 ⇒ 本档的期望是**严格** diff_lines=0。
  # 反假绿两面：① BASE 快照必须**仍含**这些被删成员（否则快照其实是改造后，0 差异是自比）；
  # ② AFTER 工作树必须**已无**它们（否则删除没落地，[18a] 的 0 差异只是两边都还在）。
  echo "== [18] P13：512 chunk 逐字节对拍（BASE=p13-base=04ede7c 快照，含 BASE 编译零 error 硬门槛） =="
  BASE13=temp/p13-base/all
  SNAP13=temp/p13-base/src/main/java
  P13_BASE_FILES="com/miaokatze/gtsr/common/dimension/framework/structure/StructureBuilder.java
com/miaokatze/gtsr/common/dimension/prosperity/ruins/ruin/RuinShapes.java
com/miaokatze/gtsr/common/dimension/prosperity/entity/GTSRCreatureRoster.java
com/miaokatze/gtsr/common/dimension/shattered/biome/ShatteredBiomes.java"
  if [ ! -f "$SNAP13/com/miaokatze/gtsr/common/dimension/framework/structure/StructureBuilder.java" ]; then
    echo "   FAIL：缺 P13 BASE 快照 $SNAP13（必须先自建 = 本片第一个写入之前的工作树副本 / git show 04ede7c）"
    FAILS=$((FAILS + 1))
  else
    rm -rf "$BASE13" "$OUT/p13-base-classes"
    mkdir -p "$BASE13/com" "$OUT/p13-base-classes"
    cp -a src/main/java/. "$BASE13/"
    miss13=0
    for rel in $P13_BASE_FILES; do
      if [ -f "$SNAP13/$rel" ]; then
        cp "$SNAP13/$rel" "$BASE13/$rel"
      else
        echo "   FAIL：P13 BASE 快照缺 $rel"; miss13=$((miss13 + 1))
      fi
    done
    [ "$miss13" = "0" ] || FAILS=$((FAILS + 1))
    # 反假绿①：BASE 侧必须还能看到被删成员的<b>声明签名</b>（用声明而非裸名，避免 AFTER 的
    # 收口说明注释里提到这些名字造成误判）
    dead13=0
    for probe in "public void hollowBox(:framework/structure/StructureBuilder" \
                 "public static boolean isRuinName(:prosperity/ruins/ruin/RuinShapes" \
                 "public static int cityWindowMultiplier(:prosperity/entity/GTSRCreatureRoster" \
                 "public static int skyFogColorFor(BiomeGenBase:shattered/biome/ShatteredBiomes"; do
      tok="${probe%%:*}"; rel="com/miaokatze/gtsr/common/dimension/${probe#*:}.java"
      if grep -aqF "$tok" "$BASE13/$rel"; then :; else
        echo "   FAIL：P13-BASE 侧已无声明 $tok ⇒ 快照不是开工前形态（对拍会退化成自比）"; dead13=$((dead13 + 1))
      fi
    done
    [ "$dead13" = "0" ] && echo "   P13-BASE 侧确认 4 个被删成员的声明仍在场（快照有效）"
    [ "$dead13" = "0" ] || FAILS=$((FAILS + 1))
    # 反假绿②：AFTER 工作树必须已无这些<b>声明</b>（否则删除没落地，0 差异只是两边都还在）
    left13=0
    for probe in "public void hollowBox(:framework/structure/StructureBuilder" \
                 "public static boolean isRuinName(:prosperity/ruins/ruin/RuinShapes" \
                 "public static int cityWindowMultiplier(:prosperity/entity/GTSRCreatureRoster" \
                 "public static int skyFogColorFor(BiomeGenBase:shattered/biome/ShatteredBiomes"; do
      tok="${probe%%:*}"; rel="com/miaokatze/gtsr/common/dimension/${probe#*:}.java"
      if grep -aqF "$tok" "src/main/java/$rel"; then
        echo "   FAIL：AFTER 侧仍有声明 $tok ⇒ 删除未落地"; left13=$((left13 + 1))
      fi
    done
    [ "$left13" = "0" ] && echo "   P13-AFTER 侧确认 4 个被删成员已净（工作树 = 删除后形态）"
    [ "$left13" = "0" ] || FAILS=$((FAILS + 1))
    P13_SRC="$(prefix $BASE13)"
    MSYS2_ARG_CONV_EXCL='*' javac -J-Duser.language=en -nowarn -encoding UTF-8 -cp "$CP" \
      -sourcepath "$BASE13" -d "$OUT/p13-base-classes" $P13_SRC >"$OUT/p13-javac-base.log" 2>&1
    echo "COMPILE P13-BASE EXIT=$? ($(grep -ac 'error:' "$OUT/p13-javac-base.log") error)"
    [ "$(grep -ac 'error:' "$OUT/p13-javac-base.log")" = "0" ] \
      || { echo "   FAIL：P13-BASE 树编译失败（还原清单不完整，[18a] 的 0 差异会是假绿）"; FAILS=$((FAILS + 1)); }
    MSYS2_ARG_CONV_EXCL='*' java $STD $LOG4J -cp "$OUT/tools;$OUT/classes;$CP" \
      SurfaceByteParityDump 16 >"$OUT/p13-parity-after.txt" 2>"$OUT/p13-parity-after.err"
    MSYS2_ARG_CONV_EXCL='*' java $STD $LOG4J -cp "$OUT/tools;$OUT/p13-base-classes;$CP" \
      SurfaceByteParityDump 16 >"$OUT/p13-parity-base.txt" 2>"$OUT/p13-parity-base.err"
    n13=$(diff "$OUT/p13-parity-base.txt" "$OUT/p13-parity-after.txt" | grep -ac "^[<>]")
    c13=$(grep -ac "^CHUNK" "$OUT/p13-parity-after.txt")
    d78_13=$(grep -ac "^CHUNK dim=78" "$OUT/p13-parity-after.txt")
    d79_13=$(grep -ac "^CHUNK dim=79" "$OUT/p13-parity-after.txt")
    echo "   [18a] 逐字节对拍 chunks=$c13 (dim78=$d78_13 dim79=$d79_13) diff_lines=$n13"
    [ "$n13" = "0" ] || { echo "   FAIL：表层/高度/群系面出现漂移（$n13 行）⇒ P13 的「纯删除」主张不成立"; FAILS=$((FAILS + 1)); }
  fi

fi

echo "== SUMMARY =="
if [ "$FAILS" = "0" ]; then
  echo "P2/P3/P4/P5/P5b/P6/P7/P7c/P8/P9/P12/P13/P14 SURFACE CHECKS: ALL GREEN"
else
  echo "P2/P3/P4/P5/P5b/P6/P7/P7c/P8/P9/P12/P13/P14 SURFACE CHECKS: $FAILS tool(s)/step(s) FAILED"
fi
[ "$FAILS" = "0" ]
