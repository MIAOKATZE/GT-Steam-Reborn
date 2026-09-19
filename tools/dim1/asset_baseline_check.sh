#!/usr/bin/env bash
# ============================================================================
# P0 资产基线单一真值机检（一条命令复跑）
#
#   bash tools/dim1/asset_baseline_check.sh                # 断言（默认；PASS 才退出 0）
#   bash tools/dim1/asset_baseline_check.sh --write-sidecars  # 从磁盘重建 landed.sha256 侧车（改动后重钉）
#
# 覆盖计划 §2.4 可机检判据 1/2/3 的资产侧：
#   ① 名册：S8RegistryRosterCheck（47 名 + 分组计数含 P8 ruin 桶 8 条 + 名集合相等）
#            + RosterIntegrityCheck（名集合三方相等 + 逐名 footprint + 逐名字符模板 SHA256 + 敏感度自检）
#   ② 贴图：两批 artgen（dim7879 / dim1）manifest OUTPUTS ↔ landed.sha256 ↔
#            src/main/resources/assets/gtsr/textures/blocks/ 磁盘实数 三点反向核对
#   ③ 结构数据件 data/structures-dim7879.json 的变体计数（当前 34，缺 5 机型；见下方 STRUCT-JSON 行）
#   ④ 【P13 挂点】展示页一致性 plan/新维度计划/review/dim1/_tools/check_feedback.py
#      ——含 P11 新增的横向断言「区2 表行群系名集合 == PAGE_ASSETS 群系键集合」。
#      为什么挂在这里而不是 surface_checks.sh（P11 上报"全仓无脚本调用本 py"）：本脚本已经跑
#      python 且已声明"资产/页面"职责；surface_checks.sh 跑在 MC-classpath 离线 JVM 域，
#      而本 py 依赖 PIL 与 review 目录，混挂会污染职责（P11 未闭合项 7 的原话）。
#      代价（如实申报）：本门因此需要 gitignore 的 plan/ 目录在场——与既有的
#      build/classes/java/main、~/.gradle 缓存前置同一性质（都是"开发树门"，非 clean clone 可跑）。
#
# 末行输出单行结论：roster=47/47 textures=49/49 SHA=OK（口径不变；页面门在其上方独立成行，红则整体 FAIL）
#
# 离线 Java 运行配方来源：plan/investigation/v12030-hotfix-replaceruntime-report.md §3（JEP330 单文件）。
# 实测本两只检只需 build/classes/java/main + forge universal jar（满足 WorldGenShatteredRuins 的
# IWorldGenerator 接口链接）；不需要 patchedMc/guava/log4j——零 MC 类初始化。
# ============================================================================
set -u

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
cd "$ROOT" || exit 2

MODE="check"
[ "${1:-}" = "--write-sidecars" ] && MODE="write"

CLASSES="build/classes/java/main"
ASSET_DIR="src/main/resources/assets/gtsr/textures/blocks"
BATCHES="tools/artgen/dim7879 tools/artgen/dim1"
# P8 名册增长：P0 基线 39（26 城 + 6 outpost + 5 机型 + 2 husk）+ 废墟族 8 条 = 47。
# 这是"增员"，与 P0 的"资产未丢"判据是两件事：贴图侧仍钉 49——废墟族只复用既有方块与记号族，
# 一张新贴图都不许有（TEXTURES 一旦变成 50/50 就说明本片越了材质红线）。
EXPECTED_ROSTER=47
EXPECTED_TEXTURES=49

fail() { echo "[FAIL] $*"; exit 1; }

# ── 1. classpath 装配（不下载任何件） ────────────────────────────────────────
[ -d "$CLASSES" ] || fail "缺少编译产物 $CLASSES（先按项目常规流程构建；本脚本不代跑 gradle）"
FORGE_JAR=$(ls -1 "$HOME"/.gradle/caches/modules-2/files-2.1/net.minecraftforge/forge/*/*/forge-*-universal.jar 2>/dev/null | head -1)
[ -n "$FORGE_JAR" ] || fail "gradle 缓存里找不到 forge universal jar（本脚本不下载依赖）"
which cygpath >/dev/null 2>&1 && { CLASSES_CP=$(cygpath -w "$CLASSES"); FORGE_CP=$(cygpath -w "$FORGE_JAR"); } || { CLASSES_CP="$CLASSES"; FORGE_CP="$FORGE_JAR"; }
CP="$CLASSES_CP;$FORGE_CP"

run_java() { # $1=source $2..=args
  local src="$1"; shift
  MSYS2_ARG_CONV_EXCL='*' java -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 \
    -cp "$CP" "$src" "$@" 2>&1
}

# ── 2. 名册（结构侧） ───────────────────────────────────────────────────────
echo "[1/4] 结构名册"
S8_OUT=$(run_java tools/dim1/S8RegistryRosterCheck.java) || true
echo "$S8_OUT" | grep -E "ROSTER (PASS|FAIL|INTEGRITY)" | head -5
if ! echo "$S8_OUT" | grep -q "^ROSTER PASS: StructureRegistry names() = ${EXPECTED_ROSTER} "; then
  fail "S8RegistryRosterCheck 未 PASS（期望 $EXPECTED_ROSTER 名；名数改动要同时改本脚本与 S8 的名册源）"
fi

RI_OUT=$(run_java tools/dim1/RosterIntegrityCheck.java) || true
echo "$RI_OUT" | grep -E "ROSTER INTEGRITY (PASS|FAIL)" | head -3
echo "$RI_OUT" | grep -q "^ROSTER INTEGRITY PASS: roster=${EXPECTED_ROSTER}/${EXPECTED_ROSTER} footprint=OK templateSHA=OK double-build=OK canary=${EXPECTED_ROSTER}/${EXPECTED_ROSTER}$" \
  || fail "RosterIntegrityCheck 未 PASS（名集合/footprint/模板 SHA/敏感度自检 任一面红）"
ROSTER=$(echo "$RI_OUT" | sed -n 's/^ROSTER INTEGRITY PASS: roster=\([0-9]*\)\/\([0-9]*\) .*/\1\/\2/p')
[ -n "$ROSTER" ] || ROSTER="?"

# ── 3. 贴图三点核对（+ 可选侧重建） ──────────────────────────────────────────
echo "[2/4] 贴图 manifest <-> landed.sha256 <-> 磁盘"
TEX_OUT=$(PYTHONIOENCODING=utf-8 python -B - "$MODE" "$ASSET_DIR" <<'PY'
import hashlib
import io
import json
import sys

mode = sys.argv[1]
asset_dir = sys.argv[2]
batches = ["tools/artgen/dim7879", "tools/artgen/dim1"]
sidecar_name = "landed.sha256"
declared_total = 0
declared_files = set()
all_outs = []
disk_hits = 0
problems = []

for b in batches:
    manifest_path = "%s/manifest.json" % b
    side_path = "%s/%s" % (b, sidecar_name)
    man = json.loads(io.open(manifest_path, encoding="utf-8").read())
    outs = [e["file"] for e in man["OUTPUTS"]]
    dup = [f for f in set(outs) if outs.count(f) > 1]
    if dup:
        problems.append("%s: OUTPUTS 内重复文件名 %s" % (b, dup))
    # 侧车（"name sha256" 逐行；dim1 侧车由本脚本 --write-sidecars 维护，dim7879 由生成器写）
    try:
        side_raw = io.open(side_path, encoding="utf-8").read()
    except IOError:
        if mode == "write":
            side_raw = ""
        else:
            problems.append("%s: 缺 %s 侧车（跑 bash tools/dim1/asset_baseline_check.sh --write-sidecars）" % (b, sidecar_name))
            side_raw = ""
    side = {}
    for ln in side_raw.splitlines():
        ln = ln.strip()
        if not ln:
            continue
        parts = ln.split()
        if len(parts) != 2 or len(parts[1]) != 64:
            problems.append("%s/%s 行格式异常: %r" % (b, sidecar_name, ln))
            continue
        side[parts[0]] = parts[1]
    only_man = sorted(set(outs) - set(side))
    only_side = sorted(set(side) - set(outs))
    if mode != "write":
        if only_man:
            problems.append("%s: manifest 有而侧车无 %s" % (b, only_man))
        if only_side:
            problems.append("%s: 侧车有而 manifest 无 %s" % (b, only_side))
    # 磁盘实数 + 逐文件 SHA
    disk_missing = []
    sha_bad = []
    fresh = {}
    for f in outs:
        p = "%s/%s" % (asset_dir, f)
        try:
            data = io.open(p, "rb").read()
        except IOError:
            disk_missing.append(f)
            continue
        digest = hashlib.sha256(data).hexdigest()
        fresh[f] = digest
        if mode != "write" and side.get(f) != digest:
            sha_bad.append("%s: 期望 %s 实得 %s" % (f, side.get(f), digest))
        declared_files.add(f)
    if disk_missing:
        problems.append("%s: manifest 声明但磁盘缺失 %s" % (b, disk_missing))
    if sha_bad:
        problems.append("%s: 磁盘 SHA 与侧车不符 %s" % (b, sha_bad))
    declared_total += len(outs)
    all_outs.extend(outs)
    disk_hits += len(outs) - len(disk_missing)
    print("%-24s OUTPUTS=%-3d landed=%-3d 磁盘命中=%d"
          % (b, len(outs), len(side if mode != "write" else fresh), len(outs) - len(disk_missing)))
    if mode == "write":
        body = "".join(sorted("%s %s\n" % (n, d) for n, d in fresh.items()))
        io.open(side_path, "w", encoding="utf-8", newline="\r\n").write(body)
        print("  [WROTE] %s (%d 行)" % (side_path, len(fresh)))

overlap = sorted({f for f in all_outs if all_outs.count(f) > 1})
if overlap:
    problems.append("两批 manifest 声明了同名文件（重复计数风险）: %s" % overlap)

disk_all = sorted(f for f in __import__("os").listdir(asset_dir) if f.endswith(".png"))
unclaimed = sorted(set(disk_all) - declared_files)
print("磁盘 PNG 总数=%d 被两批声明=%d 未声明(其他资产域, 不计入维度材质)=%d"
      % (len(disk_all), len(declared_files), len(unclaimed)))
print("TEXTURES %d/%d" % (disk_hits, declared_total))
if problems:
    for p in problems:
        print("PROBLEM: %s" % p)
    sys.exit(1)
PY
)
TEX_RC=$?
echo "$TEX_OUT"
[ $TEX_RC -eq 0 ] || fail "贴图三点核对不通过（见上方 PROBLEM 行）"
TEXTURES=$(echo "$TEX_OUT" | sed -n 's/^TEXTURES \([0-9]*\)\/\([0-9]*\)$/\1\/\2/p')
[ -n "$TEXTURES" ] || TEXTURES="?"
[ "$TEXTURES" = "$EXPECTED_TEXTURES/$EXPECTED_TEXTURES" ] \
  || fail "在用维度材质数不是 $EXPECTED_TEXTURES（实得 $TEXTURES）——两批 OUTPUTS 之一漂移"

# ── 4. 结构数据件对账（P7 起升级为硬断言：生成器已有四条腿，缺谁就是真缺陷） ─────────
# P0 交付时这里"只报事实、不改生成器"（缺 5 机型是生成器只有三条腿，见 P0 报告 §3.3），
# 因此 STRUCT-JSON MISSING 是一行常驻告警。P7 把机型腿补进 tools/dim1/StructureViewerExport 后，
# 本行改为<b>只在真的有缺项/多项时才打印</b>并判 FAIL——告警行从此消失＝覆盖完整，不是被删掉。
echo "[3/4] 结构数据件对账"
SJ=$(ROSTER_NAMES=$(printf '%s\n' "$S8_OUT" | sed -n 's/^NAMES([0-9]*): \[\(.*\)\]$/\1/p') \
    PYTHONIOENCODING=utf-8 python -B - <<'PY'
import io, json, os
p = "plan/新维度计划/review/dim1/data/structures-dim7879.json"
roster = [x.strip() for x in os.environ.get("ROSTER_NAMES", "").split(",") if x.strip()]
d = json.loads(io.open(p, encoding="utf-8").read())
v = d["variants"]
groups = {}
for e in v:
    groups[e["group"]] = groups.get(e["group"], 0) + 1
names = sorted(e["id"] for e in v)
missing, extra = sorted(set(roster) - set(names)), sorted(set(names) - set(roster))
print("STRUCT-JSON variants=%d groups=%s" % (len(v), sorted(groups.items())))
print("STRUCT-JSON 覆盖名数=%d / 名册=%d" % (len(names), len(roster)))
if missing:
    print("STRUCT-JSON MISSING=%s" % missing)
if extra:
    print("STRUCT-JSON EXTRA=%s" % extra)
print("STRUCT-JSON COVER=OK" if not missing and not extra else "STRUCT-JSON COVER=FAIL")
PY
)
echo "$SJ" | sed 's/^/  /'
STRUCT_N=$(echo "$SJ" | sed -n 's/^STRUCT-JSON variants=\([0-9]*\) .*/\1/p')
[ -n "$STRUCT_N" ] || STRUCT_N="?"
STRUCT_FULL=no
echo "$SJ" | grep -q "^STRUCT-JSON COVER=OK$" && STRUCT_FULL=yes

# ── 5. 展示页一致性（P13 挂点：把 P11 的横向断言变成常驻门） ────────────────────────────
# 目标断言（check_feedback.py 第②段）：区2 表行群系名集合 == PAGE_ASSETS 的 kind=="biome" 键集合。
# 这条之所以必须进门：①段只比"页面声明 vs feedback 键数"，从不横向比页面内部两个域，所以
# 旧 3 群系死键能在 P11 之前长期漏检（P11 证据文档 §2 的开工 RED 三条之一）。
# 挂整个脚本而非只摘一条：P11 上报的"全仓无脚本调用 check_feedback.py"一次修净，
# 顺带把 49 张贴图色数标注、俯视域零残留、路径 404 三组也变成常驻门。
echo "[4/4] 展示页一致性（区2 群系行 ↔ PAGE_ASSETS 群系键 + 色数实测 + 零残留）"
PAGE_PY="plan/新维度计划/review/dim1/_tools/check_feedback.py"
if [ ! -f "$PAGE_PY" ]; then
  fail "缺 $PAGE_PY —— 本门要求展示页核对件在场（plan/ 被 gitignore，本脚本是开发树门，不降级为跳过）"
fi
PAGE_OUT=$(PYTHONIOENCODING=utf-8 python -B "$PAGE_PY" 2>&1)
PAGE_RC=$?
echo "$PAGE_OUT" | grep -aE "^(PASS|FAIL|CHECK|INFO)" | head -20
echo "$PAGE_OUT" | grep -a "区2 表行群系名集合" | head -2 | cut -c1-170
[ $PAGE_RC -eq 0 ] || fail "展示页核对不通过（见上方 FAIL 行；CHECK FAILED 计数在末行）"
PAGE_BIOME_LINE=$(echo "$PAGE_OUT" | grep -a "区2 表行群系名集合" | head -1)
case "$PAGE_BIOME_LINE" in
  PASS*) echo "  BIOME-KEY CROSSCHECK=OK";;
  *) fail "未取到「区2↔PAGE_ASSETS 群系键」断言行 ⇒ 挂点本身失效（脚本输出格式变了？）实得: $PAGE_BIOME_LINE";;
esac

# ── 6. 结论 ─────────────────────────────────────────────────────────────────
echo "[SUMMARY] 结论"
if [ "$ROSTER" = "$EXPECTED_ROSTER/$EXPECTED_ROSTER" ] && [ "$TEXTURES" = "$EXPECTED_TEXTURES/$EXPECTED_TEXTURES" ]; then
  if [ "$STRUCT_FULL" = "yes" ]; then
    echo "roster=$ROSTER textures=$TEXTURES SHA=OK"
    exit 0
  fi
  echo "roster=$ROSTER textures=$TEXTURES SHA=OK  (结构数据件 $STRUCT_N/$EXPECTED_ROSTER：见上方 STRUCT-JSON MISSING/EXTRA)"
  fail "结构数据件未覆盖全名册（$STRUCT_N/$EXPECTED_ROSTER）"
fi
echo "roster=$ROSTER textures=$TEXTURES SHA=FAIL"
exit 1
