#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""seam_recalib.py — P10a 判据 3 的实测台架（只读探针，不写任何资产）。

用途：
  1) `--probe`  : 对两批 artgen（dim7879 35 + dim1 14 = 49 张）分别在 SIZE=16 / SIZE=32 跑一次
                  "只量不断"的构建（把 seam_gross_max 临时替换成 +inf），打印声明轴 gross 的
                  min / p50 / p95 / max 分布与逐张清单——32 档的新阈值只能从这份分布推出。
  2) `--inject` : 人为注入一处真接缝缺陷（把某张贴图的左边缘整列换成"该图已存在的最暗色"——
                  不新增颜色，所以只会撞接缝断言、不会撞色数断言），断言必须变红。
                  这就是"32 档阈值仍然能抓错"的证据（反假绿）。

零随机源：本文件不 import random/time/datetime，全部度量来自生成器自身的确定性字段。
用法：
  python tools/artgen/checks/seam_recalib.py --probe
  python tools/artgen/checks/seam_recalib.py --inject
"""

import importlib.util
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
BATCHES = (
    ("dim7879", "tools/artgen/dim7879/gen_dim7879_blocks.py"),
    ("dim1", "tools/artgen/dim1/gen_dim1_blocks.py"),
)
OLD_THRESHOLD = 1.5     # 16 档标定值（母版；两批全部组都是这个数）


def load(name, rel):
    spec = importlib.util.spec_from_file_location(name, str(ROOT / rel))
    mod = importlib.util.module_from_spec(spec)
    sys.modules[name] = mod
    spec.loader.exec_module(mod)
    return mod


def manifest_for(name):
    return json.loads((ROOT / "tools" / "artgen" / name / "manifest.json").read_text(encoding="utf-8"))


def pct(sorted_vals, q):
    if not sorted_vals:
        return float("nan")
    idx = int(round(q * (len(sorted_vals) - 1)))
    return sorted_vals[idx]


def collect(mod, manifest, size):
    """在该尺度下逐张构建，返回 [(key, group, axes, gross_max, n_colors, err)]。

    绕开 build_all 的两道全批门槛（≤16 色 / 接缝阈值）逐张量，是为了**同时**拿到：
      · 49 张的 gross 分布（阈值重标要用）；
      · 32 档下撞色数纪律 / 撞画笔异常的条目清单（这些是 P10b 的逐张待办，不在本片自改）。
    量测口径与 build_all 一致：同 painter + 同 seed + 同参数展开 + seam_metrics 取声明轴。
    """
    mod.set_size(size)
    rules = mod.Rules(manifest)
    style = manifest["STYLE"]
    by_key = {e["key"]: e for e in manifest["OUTPUTS"]}
    rows = []
    for entry in manifest["OUTPUTS"]:
        key, group, kind = entry["key"], entry["group"], entry["kind"]
        try:
            if hasattr(mod, "_resolve_params"):
                params = mod._resolve_params(entry, by_key, style)
            else:
                params = dict(entry["params"])
                for slot, ref_key in entry.get("params_from", {}).items():
                    params[slot] = by_key[ref_key]["params"]
            seed = mod._parse_seed(manifest["SEEDS"][key])
            im = mod.PAINTERS[kind](rules, seed, params).image()
            colors = len(mod.distinct_rgb(im, True) if kind in getattr(mod, "CROSS_KINDS", ()) else mod.distinct_rgb(im))
            axes = str(entry.get("seam_axes", "xy"))
            gross = None
            if entry.get("tileable"):
                ratio = mod.seam_metrics(im)
                vals = ([ratio["col_gross"]] if "x" in axes else []) + \
                       ([ratio["row_gross"]] if "y" in axes else [])
                gross = max(vals)
            rows.append((key, group, axes if entry.get("tileable") else "-", gross, colors, None))
        except Exception as exc:                      # noqa: BLE001 - 台架要把每张的失败原因逐条列全
            rows.append((key, group, "-", None, None, "%s: %s" % (type(exc).__name__, exc)))
    return rows


def ceil_grid(v, grid=0.05):
    """把重标值向上取到 0.05 网格（保守取严，不向下凑整）。"""
    return int(v / grid + 0.999999) * grid


def report(name, size, rows, headroom):
    vals = sorted(v for _, _, _, v, _, _ in rows if v is not None)
    bad = [r for r in rows if r[5] is not None]
    over = [r for r in rows if r[4] is not None and r[4] > 16]
    print("[DIST] %s SIZE=%d 声明轴 gross 样本 n=%d（tileable 条目；其余 %d 张无拼接要求；画笔异常 %d 张）"
          % (name, size, len(vals), len(rows) - len(vals), len(bad)))
    print("       min=%.3f p50=%.3f p95=%.3f max=%.3f  | 16 档旧阈值 %.2f 下的越界数=%d"
          % (vals[0], pct(vals, 0.5), pct(vals, 0.95), vals[-1], OLD_THRESHOLD,
             sum(1 for v in vals if v > OLD_THRESHOLD)))
    if over:
        print("       [COLOR-OVER] 撞 ≤16 色纪律：%s（本片不自改，列 P10b 待办）"
              % ", ".join("%s=%d" % (r[0], r[4]) for r in over))
    if bad:
        print("       [PAINTER-ERR] %s" % "; ".join("%s -> %s" % (r[0], r[5])[:120] for r in bad))
    per_group = {}
    for key, group, axes, val, colors, err in rows:
        if val is not None:
            per_group.setdefault(group, []).append(val)
    for group in sorted(per_group):
        gvals = sorted(per_group[group])
        print("       [GROUP] %-20s n=%d p50=%.3f max=%.3f -> 按 16 档同余量(×%.3f)重标 = %.2f"
              % (group, len(gvals), pct(gvals, 0.5), gvals[-1], headroom,
                 ceil_grid(gvals[-1] * headroom)))
    for key, group, axes, val, colors, err in rows:
        print("       %-34s %-18s axes=%-3s gross=%-7s rgb=%-4s %s"
              % (key, group, axes,
                 ("%.3f" % val) if val is not None else "-",
                 colors if colors is not None else "-",
                 ("ERR " + err[:70]) if err else ""))
    return vals


def probe():
    rows_of = {}
    for name, rel in BATCHES:
        mod = load("ag_" + name, rel)
        for size in (16, 32):
            rows_of[(name, size)] = collect(mod, manifest_for(name), size)
    vals16 = sorted(v for key in (("dim7879", 16), ("dim1", 16))
                    for r in rows_of[key] for v in [r[3]] if v is not None)
    headroom = OLD_THRESHOLD / vals16[-1]
    print("[RULE] 16 档标定的余量规则：阈值 %.2f ÷ 实测 max %.3f = %.3f（重标沿用同一系数，不另拍数）"
          % (OLD_THRESHOLD, vals16[-1], headroom))
    for (name, size) in sorted(rows_of):
        report(name, size, rows_of[(name, size)], headroom)
    all32 = sorted(v for key in (("dim7879", 32), ("dim1", 32))
                   for r in rows_of[key] for v in [r[3]] if v is not None)
    print("[DIST] 合并 49 张（两批 tileable 子集）")
    print("       SIZE=16 n=%d min=%.3f p50=%.3f p95=%.3f max=%.3f"
          % (len(vals16), vals16[0], pct(vals16, 0.5), pct(vals16, 0.95), vals16[-1]))
    print("       SIZE=32 n=%d min=%.3f p50=%.3f p95=%.3f max=%.3f"
          % (len(all32), all32[0], pct(all32, 0.5), pct(all32, 0.95), all32[-1]))
    print("[SUGGEST] 32 档全局候选（同一余量系数、0.05 网格向上取整）：%.2f"
          % ceil_grid(all32[-1] * headroom))
    return 0


def paint_one(mod, manifest, key, size):
    mod.set_size(size)
    entry = [e for e in manifest["OUTPUTS"] if e["key"] == key][0]
    by_key = {e["key"]: e for e in manifest["OUTPUTS"]}
    style = manifest["STYLE"]
    if hasattr(mod, "_resolve_params"):
        params = mod._resolve_params(entry, by_key, style)
    else:
        params = dict(entry["params"])
        for slot, ref_key in entry.get("params_from", {}).items():
            params[slot] = by_key[ref_key]["params"]
    seed = mod._parse_seed(manifest["SEEDS"][key])
    tex = mod.PAINTERS[entry["kind"]](mod.Rules(manifest), seed, params)
    return entry, tex


def defect_nonperiodic_ramp(mod, tex, strength=1.0):
    """注入"真·不可拼接"缺陷：把该图整幅换成一条横贯的非周期亮度梯度。

    - 只使用该图**已有色**（按亮度排序后取前若干阶）⇒ 不新增 distinct RGB，绝不撞 ≤16 色纪律；
    - 形状含义：环边每一行的落差 = 整幅动态范围，而任何内部列差的均值只有 1 阶
      ⇒ gross 必然爆表。这是 gross 设计要拒的那一类（场不周期），不是人造的"局部描边"；
    - strength ∈ (0,1]：控制梯度吃掉多少阶，用来给"灵敏度下限"定标。
      （反例记录：先试的"左缘整列压暗"和"逐行压暗"实测让 gross **变小**——那类缺陷同时抬高了
       内部差的分母，gross 是相对量，抓不住"哪都不平"的图；故本台架用梯度型缺陷做灵敏度证据。）
    """
    w = mod.W
    colors = sorted({tuple(tex.px[y][x]) for y in range(w) for x in range(w)},
                    key=lambda c: c[0] + c[1] + c[2])
    used = colors[:max(2, int(round(len(colors) * strength)))]
    n = len(used)
    for y in range(w):
        for x in range(w):
            tex.px[y][x] = used[min(n - 1, int(x / float(w) * n))]
    return tex


def sweep(keys=("shattered_corestone", "prosperity_steppe_top")):
    """灵敏度对比：同一"世界尺度"的非周期梯度缺陷在 16 / 32 档各测出多大 gross。

    这是重标的依据——32 档分母 max(interior) 的样本从 15 列变 31 列，若同一缺陷在 32 档测出的
    gross 明显变小，就说明沿用 16 档阈值会**变钝**（假绿），必须按实测收紧。
    """
    mod = load("ag_dim7879", dict(BATCHES)["dim7879"])
    manifest = manifest_for("dim7879")
    for key in keys:
        print("[SWEEP] %s" % key)
        for size in (16, 32):
            for strength in (0.0, 0.25, 0.50, 1.00):
                entry, tex = paint_one(mod, manifest, key, size)
                if strength > 0.0:
                    tex = defect_nonperiodic_ramp(mod, tex, strength)
                im = tex.image()
                ratio = mod.seam_metrics(im)
                print("         SIZE=%-3d 非周期梯度占阶 t=%.2f  col_gross=%.3f row_gross=%.3f  (rgb=%d)"
                      % (size, strength, ratio["col_gross"], ratio["row_gross"],
                         len(mod.distinct_rgb(im))))
    return 0


def inject(size=32, key="shattered_corestone", strength=1.0):
    """走**生产断言**注入缺陷：断言必须红。返回 0 表示确实红了（= 阈值仍有效）。"""
    mod = load("ag_dim7879", dict(BATCHES)["dim7879"])
    mod.set_size(size)
    manifest = manifest_for("dim7879")
    target = [e for e in manifest["OUTPUTS"] if e["key"] == key]
    manifest["OUTPUTS"] = target
    kind = target[0]["kind"]
    original = mod.PAINTERS[kind]

    def defect(r, seed, p):
        return defect_nonperiodic_ramp(mod, original(r, seed, p), strength)

    mod.PAINTERS[kind] = defect
    try:
        mod.build_all(manifest)
    except AssertionError as exc:
        print("[RED] 注入接缝缺陷后生产断言变红（SIZE=%d，%s，t=%.2f）：%s" % (size, key, strength, exc))
        if "硬缝超限" not in str(exc):
            print("[FAIL] 红了但不是撞接缝断言（实为 %s）——该证据不成立" % str(exc)[:60])
            return 1
        print("[OK] SIZE=%d 阈值仍能抓真接缝缺陷（反假绿证据成立）" % size)
        return 0
    print("[FAIL] 注入硬缝后断言仍然放行 ⇒ SIZE=%d 阈值失效（假绿）" % size)
    return 2


if __name__ == "__main__":
    flags = [str(a) for a in sys.argv[1:]]
    size = 32
    for token in flags:
        if token.startswith("--size="):
            size = int(token.split("=", 1)[1])
    if "--inject" in flags:
        raise SystemExit(inject(size=size))
    if "--sweep" in flags:
        raise SystemExit(sweep())
    if "--probe" in flags or not flags:
        raise SystemExit(probe())
    print("未知参数：%s（可用 --probe / --sweep / --inject [--size=N]）" % flags, file=sys.stderr)
    raise SystemExit(3)
