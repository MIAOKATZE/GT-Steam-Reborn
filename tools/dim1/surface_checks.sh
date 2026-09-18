#!/bin/bash
# P2「表层上收框架 + 降级态不铺 + 转置闭合」、P3「L2 高度/哈希单一真值」与
# P4「L4/L5 地表门统一 + dim78 装饰门缺陷修复」离线断言与回归的一键复跑入口。
#
# 为什么需要脚本：这两片全部判据都跑在 MC-classpath 的离线 JVM（任务包禁止 gradlew 全量构建），
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
#
# BASE 快照：temp/p3-base/all/src/main/java/… 下<b>本片（P3）开工前</b>的生产文件副本
# ——任务包口径「BASE 取开工前快照（片内口径）」。P2/P3 期用的是 temp/p2-base、temp/p3-base，
# <b>都不适用于本片</b>：P3 的快照里装饰门已经是"改造前"的形态之外还带着 P3 之前的列扫实现，
# 拿它当 P4 的 BASE 会让 [5] 命中实现差异（rd≈8）而 [4] 又混入 P3 的改动，判据 1 直接失真
# （实测过一次：p3-base 当 P4-BASE 时 [4] 报 20 行差异、[5] 报 rd=8）。
# 缺该快照时 --parity 直接判失败（不许用"看起来一样"替代对拍）。
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
com/miaokatze/gtsr/config/Config.java"
# 本片（P4）BASE 快照覆盖的文件 = 本片改动的生产文件全集（4 个门定义/调用点；
# framework/SurfaceGate.java 是本片<b>新增</b>文件，BASE 树里没有它，靠"BASE 侧 placer 快照
# 不引用它"来保证 BASE 编译通过——不要把它加进本清单，否则 BASE 侧会缺一整个类）。
PARITY_BASE_FILES="com/miaokatze/gtsr/common/dimension/prosperity/ruins/ProsperityDecorPlacer.java
com/miaokatze/gtsr/common/dimension/prosperity/ruins/ProsperityOutpostPlacer.java
com/miaokatze/gtsr/common/dimension/prosperity/ruins/ProsperitySurfaceScatter.java
com/miaokatze/gtsr/common/dimension/shattered/ShatteredDecorPlacer.java"
prefix() { echo "$REL" | sed "s|^|$1/|"; }
P12SRC="$(prefix src/main/java)"
BASEALL=temp/p4-base/all            # 编译面：cp -a 当前树后按快照覆盖 → $BASEALL/<包路径>.java
SNAP=$BASEALL/src/main/java        # 快照面：本片开工前的原始路径副本
BASE_D=$BASEALL/com/miaokatze/gtsr/common/dimension
BASE_SRC="$(prefix $BASEALL)"

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
  tools/dim1/gregtech/api/GregTechAPI.java >"$OUT/javac-tools.log" 2>&1
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
echo "-- asset_baseline_check.sh（P0 资产基线：roster/textures/SHA）"
bash tools/dim1/asset_baseline_check.sh >"$OUT/asset.out" 2>&1
code=$?; tail -2 "$OUT/asset.out" | cut -c1-170; echo "   EXIT=$code log=$OUT/asset.out"
[ $code -ne 0 ] && FAILS=$((FAILS + 1))

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
    [ "$miss" = "0" ] || FAILS=$((FAILS + 1))
    MSYS2_ARG_CONV_EXCL='*' javac -J-Duser.language=en -nowarn -encoding UTF-8 -cp "$CP" \
      -sourcepath "$BASEALL" -d "$OUT/base-classes" $BASE_SRC >"$OUT/javac-base.log" 2>&1
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
  chb=$(grep -a "kind=chain" "$OUT/gate-ab-base.txt"); cha=$(grep -a "kind=chain" "$OUT/gate-after.txt")
  echo "   BASE EXIT=$ea AFTER EXIT=$eb 样本列数 BASE=$colb AFTER=$cola"
  echo "   可落地列数(散布前)   ：BASE=$pab → AFTER=$paa（差 $((paa - pab))）"
  echo "   可落地列数(装饰前)   ：BASE=$pb → AFTER=$pa（差 $((pa - pb))，全部是 outpost/机器的 's' 板面）"
  echo "   装饰落块(生产口径)   ：BASE=$lb → AFTER=$la（差 $((la - lb)) 块）"
  echo "   装饰落块(pristine 面)：BASE=$lpb → AFTER=$lpa（差 $((lpa - lpb))，必须为 0）"
  echo "   前序阶段交叉校验     ：BASE[$chb]"
  echo "                          AFTER[$cha]"
  echo "   采样前提（无悬块/洞穴）：BASE[$prb]"
  echo "                          AFTER[$pra]"
  [ "$ea" = "0" ] || { echo "   FAIL：BASE measure 非 0"; FAILS=$((FAILS + 1)); }
  [ "$eb" = "0" ] || { echo "   FAIL：AFTER measure 非 0"; FAILS=$((FAILS + 1)); }
  [ "$colb" = "$cola" ] || { echo "   FAIL：BASE/AFTER 样本列数不同（$colb vs $cola）⇒ 不是同一输入"; FAILS=$((FAILS + 1)); }
  [ "$chb" = "$cha" ] || { echo "   FAIL：前序阶段（outpost/机器/散布）落块不同 ⇒ 差异不止来自装饰门"; FAILS=$((FAILS + 1)); }
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

fi

echo "== SUMMARY =="
if [ "$FAILS" = "0" ]; then
  echo "P2/P3/P4 SURFACE CHECKS: ALL GREEN"
else
  echo "P2/P3/P4 SURFACE CHECKS: $FAILS tool(s)/step(s) FAILED"
fi
[ "$FAILS" = "0" ]
