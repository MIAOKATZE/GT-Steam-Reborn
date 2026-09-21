#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""draw32_dim79.py — 片 A1b（dim79 半边）32x32 重绘器（原文件名 draw79.py，P16-A4 由 plan/tmp 回写进仓） —— 18 张破碎之地方块贴图。

色值唯一权威 = tools/artgen/dim7879/manifest.json（PALETTE + DERIVED_RULES）；
本文件零硬编码 hex，一切明暗阶经 shade(基色, 锚点, t) 派生（与 gen_dim7879_blocks.py 同式）。
种子亦复用 manifest.SEEDS（不另立种子表）。

尺度口径（承 R1-R6，见 gen_dim7879_blocks.py:70-86）：
  R1 格点数（facet cells / octaves cells）不随尺寸变 => 特征世界尺寸不变、像素数 x4；
     取模周期（course/drift/grain）按像素不动。
  R2 离散笔刷（团块/炭斑/碎屑/刺）半径与长度 x2、布点密度 /4 => 根数不变。
  R3 单像素斑点（凹坑/尘点/亮片）密度不变 => 覆盖率不变、颗粒变细。
  R4 1px 发丝线（裂纹/缝/受光唇/棱线）不加倍；轮廓级条带（壳沿、树皮环带、砌筑缝唇）x2。
  R5 色数 <=16、禁纯黑纯白、alpha 纪律与分辨率无关 => 不动。
  R6 32 档新增开销只花在发丝锐度与细倍频上。

逐张"为什么这样画"见各 t_* 的 docstring，同一批文字已收进同目录 design32.json.entries[*].why。
在产字节以同目录 landed.sha256 为准（重钉：bash tools/dim1/asset_baseline_check.sh --write-sidecars）。落盘为逐张增量写（画完立刻写）。
"""
import hashlib
import json
import math
import sys
from pathlib import Path

from PIL import Image

sys.stdout.reconfigure(encoding="utf-8")

ROOT = Path(__file__).resolve().parents[3]   # P16-A4 回写仓内：plan/tmp 副本里这里是绝对路径
BLK = ROOT / "src/main/resources/assets/gtsr/textures/blocks"
MANIFEST = ROOT / "tools/artgen/dim7879/manifest.json"
NOTE = ROOT / "plan" / "tmp" / "p3-a4" / "draw32_dim79-readings.tsv"   # 读数台账（scratch，不入库）

SIZE = 32
TAU = math.pi * 2.0
M64 = (1 << 64) - 1
TRANSPARENT = (0, 0, 0, 0)
FORBIDDEN = ((0, 0, 0), (255, 255, 255))
DEFAULT_OCTAVES = ((4, 0.42), (8, 0.34), (16, 0.24))

_man = json.loads(MANIFEST.read_text(encoding="utf-8"))


def _c(h):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16))


PAL = {k: _c(v) for k, v in _man["PALETTE"].items() if not k.startswith("_")}
RULES = {k: (v["toward"], float(v["t"])) for k, v in _man["DERIVED_RULES"].items()
         if not k.startswith("_")}
SEEDS = {k: int(v, 16) for k, v in _man["SEEDS"].items() if not k.startswith("_")}


def shade(color, toward, t):
    return tuple(int(round(color[i] + (toward[i] - color[i]) * t)) for i in range(3))


def P(spec, base=None):
    """'ASH_CRUST' / 'BASE@D180' / 'GLASS_GLINT' -> RGB（与 gen_dim7879.Rules.resolve 同式）。"""
    name, _, token = spec.partition("@")
    if name == "BASE":
        col = PAL[base]
    elif name in PAL:
        col = PAL[name]
    else:
        col = _c(name)
    if token:
        toward, t = RULES[token]
        col = shade(col, PAL[toward], t)
    return col


# ------------------------------------------------------------------ 确定性随机
def _mix(v):
    v = (v ^ (v >> 30)) * 0xBF58476D1CE4E5B9 & M64
    v = (v ^ (v >> 27)) * 0x94D049BB133111EB & M64
    return v ^ (v >> 31)


def _tag(tag):
    return int.from_bytes(hashlib.sha256(tag.encode("utf-8")).digest()[:8], "little")


def h01(seed, tag, *idx):
    v = _mix(seed + _mix(_tag(tag) + (idx[0] if idx else 0) * 0x9E3779B97F4A7C15))
    for i in idx[1:]:
        v = _mix(v + i * 0xD6E8FEB86659FD93)
    return (v & ((1 << 53) - 1)) / float(1 << 53)


def sp(v):
    return v * 2


def sarea(d):
    return d / 4.0


# ------------------------------------------------------------------ 画布
class C(object):
    def __init__(self, bg=None):
        self.px = [[bg] * SIZE for _ in range(SIZE)]

    def put(self, x, y, col, wrap=True):
        x = int(round(x)) % SIZE
        y = int(round(y)) % SIZE
        if not wrap and not (0 <= x < SIZE and 0 <= y < SIZE):
            return
        self.px[y][x] = col

    def get(self, x, y, wrap=True):
        x = int(round(x)) % SIZE
        y = int(round(y)) % SIZE
        return self.px[y][x]

    def each(self, fn):
        for y in range(SIZE):
            for x in range(SIZE):
                fn(x, y)

    def line(self, x0, y0, x1, y1, col, wrap=True):
        x0, y0, x1, y1 = int(round(x0)), int(round(y0)), int(round(x1)), int(round(y1))
        dx, dy = abs(x1 - x0), -abs(y1 - y0)
        sx = 1 if x0 < x1 else -1
        sy = 1 if y0 < y1 else -1
        err = dx + dy
        while True:
            self.put(x0, y0, col, wrap)
            if x0 == x1 and y0 == y1:
                break
            e2 = 2 * err
            if e2 >= dy:
                err += dy
                x0 += sx
            if e2 <= dx:
                err += dx
                y0 += sy

    def path(self, pts, col, wrap=True):
        for i in range(len(pts) - 1):
            self.line(pts[i][0], pts[i][1], pts[i + 1][0], pts[i + 1][1], col, wrap)

    def disc(self, cx, cy, rad, col, wrap=True):
        for y in range(int(cy - rad) - 1, int(cy + rad) + 2):
            for x in range(int(cx - rad) - 1, int(cx + rad) + 2):
                if (x - cx) ** 2 + (y - cy) ** 2 <= rad * rad + 0.35:
                    self.put(x, y, col, wrap)

    def image(self, alpha_bg=False):
        im = Image.new("RGBA", (SIZE, SIZE), TRANSPARENT if alpha_bg else (0, 0, 0, 255))
        d = im.load()
        for y in range(SIZE):
            for x in range(SIZE):
                col = self.px[y][x]
                if col is None:
                    col = TRANSPARENT if alpha_bg else (128, 128, 128)
                d[x, y] = (col[0], col[1], col[2], col[3] if len(col) == 4 else 255)
        return im


# ------------------------------------------------------------------ 场与笔刷
def lattice_noise(seed, cells, tag):
    """周期格点值噪（wrap 精确，cells 整除 SIZE）。"""
    step = SIZE // cells
    g = [[h01(seed, tag, i, j) for i in range(cells)] for j in range(cells)]

    def f(x, y):
        gx = int(x // step) % cells
        gy = int(y // step) % cells
        fx = (x - (int(x // step) * step)) / float(step)
        fy = (y - (int(y // step) * step)) / float(step)
        sx = fx * fx * (3 - 2 * fx)
        sy = fy * fy * (3 - 2 * fy)
        x0 = gx
        x1 = (gx + 1) % cells
        y0 = gy
        y1 = (gy + 1) % cells
        a = g[y0][x0] + (g[y0][x1] - g[y0][x0]) * sx
        b = g[y1][x0] + (g[y1][x1] - g[y1][x0]) * sx
        return a + (b - a) * sy
    return f


def field(seed, octaves, tag):
    """多倍频合成场，归一化到 0..1。"""
    ws = sum(w for _, w in octaves)
    acc = [[0.0] * SIZE for _ in range(SIZE)]
    for k, (cells, w) in enumerate(octaves):
        f = lattice_noise(seed, cells, "%s:%d" % (tag, k))
        for y in range(SIZE):
            for x in range(SIZE):
                acc[y][x] += f(x, y) * (w / ws)
    return acc


def qlevel(v, n, seed, tag, x, y, bias=0.0):
    """把 0..1 场量化到 n 档，用 1px 有序抖动避免色带（不引入新色值）。"""
    vv = v + bias
    if vv < 0:
        vv = 0.0
    if vv > 0.999999:
        vv = 0.999999
    raw = vv * n
    i = int(raw)
    frac = raw - i
    if h01(seed, tag, x, y) < frac * 0.85:
        i += 1
    return min(n - 1, i)


def facet_map(seed, cells, tag):
    """越界抖动的 Voronoi 棱面 id 图（wrap 距离 => 天然无缝）。"""
    step = SIZE // float(cells)
    pts = []
    for j in range(cells):
        for i in range(cells):
            jx = i * step + step * 0.5 + (h01(seed, tag + ":jx", i, j) - 0.5) * step
            jy = j * step + step * 0.5 + (h01(seed, tag + ":jy", i, j) - 0.5) * step
            pts.append((jx, jy))
    ids = [[0] * SIZE for _ in range(SIZE)]
    for y in range(SIZE):
        for x in range(SIZE):
            best = None
            bid = 0
            for i, (px_, py_) in enumerate(pts):
                dx = abs(x - px_)
                dy = abs(y - py_)
                dx = min(dx, SIZE - dx)
                dy = min(dy, SIZE - dy)
                d = dx * dx + dy * dy
                if best is None or d < best:
                    best = d
                    bid = i
            ids[y][x] = bid
    return ids, len(pts)


def speckle(cv, seed, tag, density, col, wrap=True):
    """单像素斑点：密度不随尺寸变（R3）。"""
    def at(x, y):
        if h01(seed, tag, x, y) < density:
            cv.put(x, y, col, wrap)
    cv.each(at)


def brush(cv, seed, tag, count, rad, col, elong=1.0, wrap=True):
    """离散笔刷：布点数按世界面积不变（R2），半径 x2。"""
    placed = 0
    tries = 0
    while placed < count and tries < count * 40:
        tries += 1
        u = h01(seed, tag + ":u", tries)
        v = h01(seed, tag + ":v", tries)
        cx, cy = u * SIZE, v * SIZE
        r = rad * (0.6 + 0.8 * h01(seed, tag + ":r", tries))
        if elong != 1.0:
            for y in range(int(cy - r * elong) - 2, int(cy + r * elong) + 3):
                for x in range(int(cx - r) - 2, int(cx + r) + 3):
                    if ((x - cx) / r) ** 2 + ((y - cy) / (r * elong)) ** 2 <= 1.0:
                        cv.put(x, y, col, wrap)
        else:
            cv.disc(cx, cy, r, col, wrap)
        placed += 1


def hair_crack(cv, seed, tag, x0, y0, steps, col, wrap=True, drift=1.0):
    """1px 发丝裂纹：每步 1-2px 的折线（R4 不加倍）。"""
    x, y = float(x0), float(y0)
    dx = 1.0 if h01(seed, tag + ":dir", 0) < 0.5 else -1.0
    dy = 1.0 if h01(seed, tag + ":dir", 1) < 0.5 else -1.0
    pts = [(x, y)]
    for i in range(steps):
        if h01(seed, tag + ":turn", i) < 0.34:
            dx, dy = dy * drift, dx * drift
        x += dx * (1.0 if h01(seed, tag + ":len", i) < 0.6 else 2.0)
        y += dy * (1.0 if h01(seed, tag + ":len", i + 99) < 0.5 else 2.0)
        pts.append((x, y))
    cv.path(pts, col, wrap)
    return pts


def wander(cv, seed, tag, x0, y0, steps, col, thick=2, wrap=True):
    """矿脉/裂隙带：中心线 1px 折线 + 可选并列（条带 x2 => 2px），返回中心线点。"""
    x, y = float(x0), float(y0)
    ang = h01(seed, tag + ":ang", 0) * TAU
    pts = [(x, y)]
    for i in range(steps):
        ang += (h01(seed, tag + ":bend", i) - 0.5) * 1.1
        n = 2 if h01(seed, tag + ":len", i) < 0.5 else 3
        for _ in range(n):
            x += math.cos(ang)
            y += math.sin(ang)
            pts.append((x, y))
    for (px_, py_) in pts:
        for k in range(thick):
            cv.put(px_, py_ + k, col, wrap)
    return pts


# ------------------------------------------------------------------ 主体画法
def paint_facet_stone(cv, seed, p, tag, pit_rim=False):
    """棱面硬石（corestone / slag_top 共用件）：4 格棱面 + 1px 缝 + 1px 受光唇。"""
    levels = [P(s, p["base"]) for s in p["levels"]]
    ids, n = facet_map(seed, p["facet_cells"], tag + ":f")
    lvl = [int(h01(seed, tag + ":lv", i) * len(levels)) % len(levels) for i in range(n)]
    fine = field(seed, [(16, 1.0)], tag + ":fine")       # R6 细倍频
    body = [[None] * SIZE for _ in range(SIZE)]
    for y in range(SIZE):
        for x in range(SIZE):
            i = ids[y][x]
            v = lvl[i] / float(len(levels)) + 0.16 * (fine[y][x] - 0.5)
            body[y][x] = qlevel(v, len(levels), seed, tag + ":q", x, y)
    joint = P(p["joint"], p["base"])
    lip = P(p["facet_lit"], p["base"])
    for y in range(SIZE):
        for x in range(SIZE):
            me = ids[y][x]
            bl = ids[(y + 1) % SIZE][x]
            rt = ids[y][(x + 1) % SIZE]
            if bl != me:
                # 缝为主（暗），只有当下方板块明显更亮时才画 1px 受光唇 => 避免读成"白水泥砖缝"
                col = lip if body[(y + 1) % SIZE][x] - body[y][x] >= 2 else joint
            elif rt != me:
                col = joint
            else:
                col = levels[body[y][x]]
            cv.put(x, y, col)
    speckle(cv, seed, tag + ":pit", p["pit_density"], P(p["pit"], p["base"]))
    if pit_rim:
        for y in range(SIZE):
            for x in range(SIZE):
                if cv.get(x, y) == P(p["pit"], p["base"]) and h01(seed, tag + ":rim", x, y) < 0.3:
                    cv.put(x, y + 1, lip)


def paint_grit(cv, seed, p, tag):
    """压实颗粒土（四族群 base 共用件）：4 档基场 + 明暗砂粒 + 板结团块 + 屑。"""
    levels = [P(s, p["base"]) for s in p["levels"]]
    f = field(seed, DEFAULT_OCTAVES, tag)
    for y in range(SIZE):
        for x in range(SIZE):
            cv.put(x, y, levels[qlevel(f[y][x], len(levels), seed, tag + ":q", x, y)])
    n = int(round(p["clod_density"] * SIZE * SIZE))
    brush(cv, seed, tag + ":clod", n, 2.0, P(p["clod"], p["base"]))
    speckle(cv, seed, tag + ":gd", p["grit_density"] * 0.55, P(p["grit_dark"], p["base"]))
    speckle(cv, seed, tag + ":gl", p["grit_density"] * 0.45, P(p["grit_lit"], p["base"]))
    speckle(cv, seed, tag + ":fl", p["flake_density"], P(p["flake"], p["base"]))


def paint_side(cv, seed, p, tag, fringe_fn, body_fn):
    """侧面共用件：上部壳沿（条带 x2 => 6-8px）+ 下部主体 + 1px 下缘阴影。"""
    body_fn(cv, seed, p["body"], tag + ":body")
    prof = []
    fp = p["fringe_period"]
    for x in range(SIZE):
        w = lattice_noise(seed, fp, tag + ":prof")
        base = p["fringe_min"] * 2
        d = int(round(base + (w(x, 0) - 0.5) * 2.6 * 2))
        prof.append(max(p["fringe_min"] * 2 - 2, min(p["fringe_max"] * 2 + 2, d)))
    tmp = C()
    fringe_fn(tmp, seed, p["fringe"], tag + ":fr")
    for y in range(SIZE):
        for x in range(SIZE):
            if y < prof[x]:
                cv.put(x, y, tmp.get(x, y))
            elif y == prof[x]:
                cv.put(x, y, P(p["shadow"], p["body"]["base"]))
    for x in range(SIZE):
        n = int(h01(seed, tag + ":hang", x) < p["hang_density"] * 4)
        for k in range(1, n + 1):
            cv.put(x, prof[x] + k, P(p["shadow"], p["body"]["base"]) if k > 1 else tmp.get(x, prof[x]))
    speckle(cv, seed, tag + ":es", p["edge_speckle_density"], P(p["edge_speckle"], p["fringe"]["base"]),
            wrap=True)


# ---- 各张 -----------------------------------------------------------------
def t_corestone(seed):
    """碎核岩：4 格大棱面在 32 档才真正读作"板状碎裂"而非盐粒；缝/唇保持 1px 是升档的全部收益；
    2 道浅矿脉按条带 x2 增厚成 2-3px，保证远处仍看得见"核岩有脉"。"""
    cv = C()
    p = {"base": "CORE_ROCK", "levels": ["BASE@D180", "BASE@D070", "BASE", "BASE@L045",
                                         "BASE@L110", "BASE@L200"],
         "facet_cells": 4, "joint": "BASE@D420", "facet_lit": "BASE@L280",
         "pit": "BASE@D280", "pit_density": 0.05}
    paint_facet_stone(cv, seed, p, "core", pit_rim=False)
    vein = P("CORE_VEIN")
    veinlit = P("CORE_VEIN@L180")
    for i in range(2):
        pts = wander(cv, seed, "core:v%d" % i, h01(seed, "vein:x", i) * SIZE,
                     h01(seed, "vein:y", i) * SIZE,
                     9 + int(h01(seed, "vein:n", i) * 4), vein, 2)
        for (x, y) in pts:
            if h01(seed, "vein:lit", i, int(x), int(y)) < 0.5:
                cv.put(x, y - 1, veinlit)
    return cv


def t_monolith(seed):
    """独石：全族最暗 + 周期 8px 的横通缝与错缝竖缝（砌筑）+ 1px 凿痕 + 2 道应力裂纹；
    与 corestone 的"天然碎裂"靠直线缝区分，32 档能容下 4 层皮通缝而 16 档只有 2 层。"""
    cv = C()
    p = {"base": "MONO_ROCK", "levels": ["BASE@D220", "BASE@D110", "BASE", "BASE@L090", "BASE@L200"]}
    levels = [P(s, p["base"]) for s in p["levels"]]
    f = field(seed, [(4, 0.55), (8, 0.3), (16, 0.15)], "mono")
    joint = P("BASE@D420", p["base"])
    lip = P("BASE@L140", p["base"])
    for y in range(SIZE):
        course = (y % 8) // 4
        for x in range(SIZE):
            v = f[y][x] * 0.75 + 0.25 * (0.5 + 0.5 * math.cos(TAU * (y % 8) / 8.0))
            cv.put(x, y, levels[qlevel(v, len(levels), seed, "mono:q", x, y, bias=course * 0.04)])
    for y in range(SIZE):
        if y % 8 == 0:
            for x in range(SIZE):
                cv.put(x, y, joint)
                cv.put(x, y + 1, lip)
    for c in range(SIZE // 8):
        xj = (8 + 16 * c + int(h01(seed, "mono:vj", c) * 6)) % SIZE
        for y in range(c * 8 + 1, c * 8 + 8):
            cv.put(xj, y, joint)
            cv.put((xj + 16) % SIZE, y, joint)
    for i in range(2):
        hair_crack(cv, seed, "mono:cr%d" % i, h01(seed, "mono:cx", i) * SIZE,
                   h01(seed, "mono:cy", i) * SIZE, 9, joint)
    speckle(cv, seed, "mono:ch", 0.02, lip)
    speckle(cv, seed, "mono:pit", 0.04, P("BASE@D280", p["base"]))
    return cv


def t_ash_top(seed):
    """灰烬表壳：细灰粒 + 周期 8px 风积纹 + 炭渣/炭斑 + 极稀疏余烬；
    灰烬的语义是"细"，所以 32 档只加波纹层次、不加对比度。"""
    cv = C()
    p = {"base": "ASH_CRUST", "levels": ["BASE@D220", "BASE@D110", "BASE", "BASE@L090", "BASE@L200"]}
    levels = [P(s, p["base"]) for s in p["levels"]]
    f = field(seed, [(2, 0.18), (4, 0.32), (8, 0.3), (16, 0.2)], "ash")
    for y in range(SIZE):
        wave = 0.5 + 0.5 * math.cos(TAU * (y % 8) / 8.0)
        for x in range(SIZE):
            v = f[y][x] + 0.25 * 0.4 * (wave - 0.5)
            cv.put(x, y, levels[qlevel(v, len(levels), seed, "ash:q", x, y)])
    brush(cv, seed, "ash:blob", 3, 1.5, P("ASH_CHAR"), elong=1.25)
    speckle(cv, seed, "ash:char", 0.03, P("ASH_CHAR"))
    speckle(cv, seed, "ash:glint", 0.006, P("GLINT@D220"))
    return cv


def t_ash_base(seed):
    """压实灰烬土：4 档中灰基场 + 明暗砂粒（覆盖率不变、颗粒变细）+ 板结团块 + 炭屑。"""
    cv = C()
    p = {"base": "ASH_SOIL", "levels": ["BASE@D140", "BASE@D070", "BASE", "BASE@L110"],
         "grit_dark": "BASE@D240", "grit_lit": "BASE@L180", "grit_density": 0.2,
         "clod": "BASE@D090", "clod_density": 0.055, "flake": "ASH_CHAR", "flake_density": 0.03}
    paint_grit(cv, seed, p, "ashb")
    return cv


def t_ash_side(seed):
    """灰烬侧面：6-8px 壳沿（条带 x2）压灰土主体，下缘 1px 阴影 + 零星挂垂；只保证水平无缝。"""
    cv = C()
    body = {"base": "ASH_SOIL", "levels": ["BASE@D140", "BASE@D070", "BASE", "BASE@L110"],
            "grit_dark": "BASE@D240", "grit_lit": "BASE@L180", "grit_density": 0.2,
            "clod": "BASE@D090", "clod_density": 0.055, "flake": "ASH_CHAR", "flake_density": 0.03}
    fringe = {"base": "ASH_CRUST", "levels": ["BASE@D220", "BASE@D110", "BASE", "BASE@L090",
                                             "BASE@L200"]}
    pr = {"body": body, "fringe": fringe, "fringe_min": 3, "fringe_max": 4, "fringe_period": 8,
          "hang_density": 0.14, "edge_speckle": "ASH_CHAR", "edge_speckle_density": 0.012,
          "shadow": "BASE@D140"}
    paint_side(cv, seed, pr, "ashs", _fringe_ash, _body_grit)
    return cv


def _fringe_ash(cv, seed, p, tag):
    levels = [P(s, p["base"]) for s in p["levels"]]
    f = field(seed, [(4, 0.4), (8, 0.35), (16, 0.25)], tag)
    for y in range(SIZE):
        for x in range(SIZE):
            cv.put(x, y, levels[qlevel(f[y][x], len(levels), seed, tag + ":q", x, y)])
    speckle(cv, seed, tag + ":char", 0.03, P("ASH_CHAR"))


def _body_grit(cv, seed, p, tag):
    paint_grit(cv, seed, p, tag)


def t_slag_top(seed):
    """熔渣表壳：4 格锐角渣块 + 1px 暗缝 + 受光棱 + 熔蚀坑带 1px 亮唇；
    渣 = "被熔过又冷下来的硬壳"，坑唇是它和灰烬（细粒）、琉璃（光滑）分家的关键。"""
    cv = C()
    p = {"base": "SLAG_CRUST", "levels": ["BASE@D220", "BASE@D110", "BASE", "BASE@L090",
                                          "BASE@L200"],
         "facet_cells": 4, "joint": "BASE@D420", "facet_lit": "BASE@L280",
         "pit": "BASE@D340", "pit_density": 0.06}
    paint_facet_stone(cv, seed, p, "slag", pit_rim=True)
    return cv


def t_slag_base(seed):
    """渣木林下层土：暗橄榄颗粒 + 炭屑（炭屑与灰烬族共用 ASH_CHAR，保持同 mod 语言）。"""
    cv = C()
    p = {"base": "SLAG_SOIL", "levels": ["BASE@D140", "BASE@D070", "BASE", "BASE@L110"],
         "grit_dark": "BASE@D240", "grit_lit": "BASE@L180", "grit_density": 0.18,
         "clod": "BASE@D090", "clod_density": 0.05, "flake": "ASH_CHAR", "flake_density": 0.02}
    paint_grit(cv, seed, p, "slgb")
    return cv


def t_slag_side(seed):
    """熔渣侧面：渣壳棱面沿压暗橄榄土，壳沿用同一 facet 配方（横 2 格）保持家族一致。"""
    cv = C()
    body = {"base": "SLAG_SOIL", "levels": ["BASE@D140", "BASE@D070", "BASE", "BASE@L110"],
            "grit_dark": "BASE@D240", "grit_lit": "BASE@L180", "grit_density": 0.18,
            "clod": "BASE@D090", "clod_density": 0.05, "flake": "ASH_CHAR", "flake_density": 0.02}
    fringe = {"base": "SLAG_CRUST", "levels": ["BASE@D220", "BASE@D110", "BASE", "BASE@L090",
                                              "BASE@L200"],
              "facet_cells": 4, "joint": "BASE@D420", "facet_lit": "BASE@L280",
              "pit": "BASE@D340", "pit_density": 0.06}
    pr = {"body": body, "fringe": fringe, "fringe_min": 3, "fringe_max": 4, "fringe_period": 8,
          "hang_density": 0.12, "edge_speckle": "SLAG_CRUST@D340", "edge_speckle_density": 0.012,
          "shadow": "BASE@D140"}
    paint_side(cv, seed, pr, "slgs", _fringe_facet, _body_grit)
    return cv


def _fringe_facet(cv, seed, p, tag):
    paint_facet_stone(cv, seed, p, tag, pit_rim=False)
    speckle(cv, seed, tag + ":ch", 0.02, P("ASH_CHAR"))


def t_glass_top(seed):
    """琉璃表壳：光滑冷蓝基场 + 3 道贝壳状断口（1px 暗线 + 1px 亮唇成对）+ 撞击放射纹 + 反光点带晕。
    方块是 Material.rock 不透明实体，"透明感"只能靠明度层级（亮面/中面/深缝）与 specular 画出来，
    所以这张的 16->32 收益全花在断口可读性上，而不是加 alpha。"""
    cv = C()
    p = {"base": "GLASS_CRUST", "levels": ["BASE@D220", "BASE@D110", "BASE", "BASE@L090",
                                           "BASE@L200"]}
    levels = [P(s, p["base"]) for s in p["levels"]]
    f = field(seed, [(4, 0.5), (8, 0.5)], "glass")
    for y in range(SIZE):
        for x in range(SIZE):
            cv.put(x, y, levels[qlevel(f[y][x], len(levels), seed, "glass:q", x, y)])
    dark = P("BASE@D420", p["base"])
    lit = P("BASE@L340", p["base"])
    glint = P("GLASS_GLINT")
    for i in range(3):
        pts = hair_crack(cv, seed, "glass:cr%d" % i, h01(seed, "glass:x", i) * SIZE,
                         h01(seed, "glass:y", i) * SIZE, 11, dark)
        for k in range(1, len(pts) - 1):
            cv.put(pts[k][0], pts[k][1] + 1, lit)
        for b in range(2):
            k = 2 + int(h01(seed, "glass:b", i, b) * (len(pts) - 4))
            bx, by = pts[k]
            hair_crack(cv, seed, "glass:br%d%d" % (i, b), bx, by, 4, dark)
    for i in range(2):
        cx = h01(seed, "glass:ix", i) * SIZE
        cy = h01(seed, "glass:iy", i) * SIZE
        for a in range(6):
            ang = a * TAU / 6.0 + h01(seed, "glass:ia", i, a) * 0.5
            r0, r1 = 2.0, 5.0 + h01(seed, "glass:ir", i, a) * 3.0
            cv.line(cx + math.cos(ang) * r0, cy + math.sin(ang) * r0,
                    cx + math.cos(ang) * r1, cy + math.sin(ang) * r1, dark)
        cv.disc(cx, cy, 1.0, glint)
    for y in range(SIZE):
        for x in range(SIZE):
            if h01(seed, "glass:g", x, y) < 0.008:
                cv.put(x, y, glint)
                cv.put(x - 1, y, lit)
                cv.put(x + 1, y, lit)
    return cv


def t_glass_base(seed):
    """琉璃下层：冷蓝颗粒 + 少量玻璃碎屑（2px 小片，根数不变、尺寸 x2）——
    下层比表壳暗一档，保证侧面壳沿压得住。"""
    cv = C()
    p = {"base": "GLASS_SOIL", "levels": ["BASE@D140", "BASE@D070", "BASE", "BASE@L110"],
         "grit_dark": "BASE@D240", "grit_lit": "BASE@L180", "grit_density": 0.18,
         "clod": "BASE@D090", "clod_density": 0.05, "flake": "GLASS_GLINT@D220",
         "flake_density": 0.012}
    paint_grit(cv, seed, p, "glassb")
    brush(cv, seed, "glassb:chip", 3, 1.0, P("GLASS_GLINT@D220"), elong=1.6)
    return cv


def t_glass_side(seed):
    """琉璃侧面：亮壳沿（含 1px 顶亮边）压冷蓝下层，1-2 道断口从壳沿向下延伸进主体。"""
    cv = C()
    body = {"base": "GLASS_SOIL", "levels": ["BASE@D140", "BASE@D070", "BASE", "BASE@L110"],
            "grit_dark": "BASE@D240", "grit_lit": "BASE@L180", "grit_density": 0.18,
            "clod": "BASE@D090", "clod_density": 0.05, "flake": "GLASS_GLINT@D220",
            "flake_density": 0.012}
    fringe = {"base": "GLASS_CRUST", "levels": ["BASE@D220", "BASE@D110", "BASE", "BASE@L090",
                                                "BASE@L200"]}
    pr = {"body": body, "fringe": fringe, "fringe_min": 3, "fringe_max": 4, "fringe_period": 8,
          "hang_density": 0.1, "edge_speckle": "GLASS_GLINT", "edge_speckle_density": 0.01,
          "shadow": "BASE@D140"}
    paint_side(cv, seed, pr, "glasss", _fringe_glass, _body_grit)
    dark = P("BASE@D420", fringe["base"])
    for i in range(2):
        x0 = h01(seed, "glasss:cx", i) * SIZE
        pts = [(x0 + (h01(seed, "glasss:j", i, k) - 0.5) * 2.0, 4 + k * 4) for k in range(6)]
        cv.path(pts, dark)
    return cv


def _fringe_glass(cv, seed, p, tag):
    levels = [P(s, p["base"]) for s in p["levels"]]
    f = field(seed, [(4, 0.5), (8, 0.5)], tag)
    for y in range(SIZE):
        for x in range(SIZE):
            cv.put(x, y, levels[qlevel(f[y][x], len(levels), seed, tag + ":q", x, y)])
    speckle(cv, seed, tag + ":g", 0.01, P("GLASS_GLINT"))


def t_tar_top(seed):
    """焦油壳：4 格龟裂网（1px 深缝）+ 板块内单侧受光 + 裂缝节点的湿亮芯。
    基色明度只有 32 档，D 侧步进会塌，所以层次全走 L 侧大 t（manifest 的 _dark_range_note）。"""
    cv = C()
    p = {"base": "TAR_CRUST", "levels": ["BASE@D180", "BASE@D070", "BASE", "BASE@L045"]}
    levels = [P(s, p["base"]) for s in p["levels"]]
    ids, n = facet_map(seed, 4, "tar:f")
    lvl = [int(h01(seed, "tar:lv", i) * len(levels)) % len(levels) for i in range(n)]
    for y in range(SIZE):
        for x in range(SIZE):
            i = ids[y][x]
            up = ids[(y - 1) % SIZE][x] != i
            cv.put(x, y, levels[min(len(levels) - 1,
                                    lvl[i] + (1 if up and h01(seed, "tar:d", x, y) < 0.25 else 0))])
    seam = P("ASH_CHAR")
    edge = [[False] * SIZE for _ in range(SIZE)]
    for y in range(SIZE):
        for x in range(SIZE):
            d = ids[y][x] != ids[(y + 1) % SIZE][x]
            r = ids[y][x] != ids[y][(x + 1) % SIZE]
            if d or r:
                cv.put(x, y, seam)
                edge[y][x] = d and r                      # 龟裂网交叉点
    for y in range(SIZE):
        for x in range(SIZE):
            if edge[y][x] and h01(seed, "tar:nd", x, y) < 0.55:
                cv.put(x, y, P("BASE@L340", p["base"]))
            elif h01(seed, "tar:gs", x, y) < 0.014:
                cv.put(x, y, P("BASE@L200", p["base"]))
    return cv


def t_tar_base(seed):
    """焦油基底：全库最暗 + 湿亮细点（L 侧大 t）；团块走 D 侧只作轮廓不作色阶。"""
    cv = C()
    p = {"base": "TAR_SOIL", "levels": ["BASE@D140", "BASE", "BASE@L070", "BASE@L180"],
         "grit_dark": "BASE@D240", "grit_lit": "BASE@L110", "grit_density": 0.14,
         "clod": "BASE@D090", "clod_density": 0.08, "flake": "BASE@L240", "flake_density": 0.01}
    paint_grit(cv, seed, p, "tarb")
    return cv


def t_tar_side(seed):
    """焦油侧面：壳沿下缘挂垂最长（黏稠），挂垂用壳色而非阴影 => 读作"在流"。"""
    cv = C()
    body = {"base": "TAR_SOIL", "levels": ["BASE@D140", "BASE", "BASE@L070", "BASE@L180"],
            "grit_dark": "BASE@D240", "grit_lit": "BASE@L110", "grit_density": 0.14,
            "clod": "BASE@D090", "clod_density": 0.08, "flake": "BASE@L240", "flake_density": 0.01}
    fringe = {"base": "TAR_CRUST", "levels": ["BASE@D180", "BASE@D070", "BASE", "BASE@L045",
                                              "BASE@L110"]}
    pr = {"body": body, "fringe": fringe, "fringe_min": 3, "fringe_max": 4, "fringe_period": 8,
          "hang_density": 0.18, "edge_speckle": "TAR_CRUST@L280", "edge_speckle_density": 0.01,
          "shadow": "BASE@D140"}
    paint_side(cv, seed, pr, "tars", _fringe_tar, _body_grit)
    return cv


def _fringe_tar(cv, seed, p, tag):
    levels = [P(s, p["base"]) for s in p["levels"]]
    f = field(seed, [(4, 0.5), (8, 0.5)], tag)
    seam = P("ASH_CHAR")
    for y in range(SIZE):
        for x in range(SIZE):
            cv.put(x, y, levels[qlevel(f[y][x], len(levels), seed, tag + ":q", x, y)])
    for y in range(0, SIZE, 5):
        for x in range(SIZE):
            if h01(seed, tag + ":net", x, y) < 0.5:
                cv.put(x, y, seam)
    speckle(cv, seed, tag + ":gl", 0.012, P("BASE@L340", p["base"]))


def t_spike(seed):
    """石刺簇：4 根锥刺（根宽 x2、收至 1px 尖）+ 1px 中棱线（左暗右亮）+ 尖顶反光 + 底屑；
    十字装饰不计色数但 alpha 必须二值，且最下一行要有根（否则游戏里飘空）。"""
    cv = C()
    p = {"base": "SPIKE_STONE"}
    dk = P("BASE@D340", p["base"])
    mid = P("BASE@D140", p["base"])
    lt = P("BASE@L200", p["base"])
    head = P("BASE@D090", p["base"])
    tip = P("GLINT@D090")
    roots = ((4, 15, -3.0), (12, 25, 1.5), (20, 20, -1.0), (27, 12, 3.0))
    for i, (cx, h, lean) in enumerate(roots):
        h += int((h01(seed, "spike:h", i) - 0.5) * 4)
        rw = 2 + int(h01(seed, "spike:w", i) * 1.9)
        ln = lean + (h01(seed, "spike:ln", i) - 0.5) * 3.0
        for k in range(h + 1):
            y = SIZE - 1 - k
            t = k / float(h)
            w = max(0, int(round(rw * (1.0 - t * 1.05))))
            cxi = int(round(cx + ln * t))
            if w <= 0:
                if 0 <= cxi < SIZE and h01(seed, "spike:tip", i) < 0.6:
                    cv.px[y][cxi] = tip
                break
            for dx in range(-w, w + 1):
                x = cxi + dx
                if not (0 <= x < SIZE):
                    continue
                cv.px[y][x] = dk if dx < 0 else (mid if dx == 0 else lt)
        if h01(seed, "spike:edge", i) < 0.7:
            for k in range(2, h - 2, 3):
                y = SIZE - 1 - k
                t = k / float(h)
                w = max(0, int(round(rw * (1.0 - t * 1.05))))
                x = int(round(cx + ln * t)) + w
                if 0 <= x < SIZE:
                    cv.px[y][x] = head
    for i in range(3):
        bx = int(h01(seed, "spike:bx", i) * SIZE)
        for dx in range(0, 2 + int(h01(seed, "spike:bw", i) * 2)):
            x = (bx + dx) % SIZE
            y = SIZE - 1 - int(h01(seed, "spike:by", i, dx) * 2)
            cv.px[y][x] = mid if dx % 2 else head
    return cv


def t_log_side(seed):
    """灰烬枯木侧面：4 块石化树皮（宽 8px，边界蛇行）+ 1px 纵缝与短纤维 + 2 道 x2 加厚暗环带
    （环带随树皮起伏，不是一条直尺线）+ 灰白尘点。与 dim78 锈木的分别画在颜色与缝向：
    这里无橙、纵缝为主、环带是炭灰而非锈橙，且读起来是"石化的树"不是"木板梯"。"""
    cv = C()
    p = {"base": "ASH_BARK"}
    levels = [P("BASE@D220", p["base"]), P("BASE@D110", p["base"]), P("BASE", p["base"]),
              P("BASE@L090", p["base"]), P("BASE@L180", p["base"])]
    seam = P("ASH_CHAR")
    f = field(seed, [(4, 0.5), (16, 0.5)], "log")
    wob = lattice_noise(seed, 8, "log:wob")
    plates = 3
    bnd = [6 + i * 10 + int(h01(seed, "log:bnd", i) * 5) for i in range(plates)]
    for y in range(SIZE):
        for x in range(SIZE):
            v = f[y][x] * 0.62 + 0.38 * (0.5 + 0.5 * math.cos(TAU * (y % 16) / 16.0))
            cv.put(x, y, levels[qlevel(v, len(levels), seed, "log:q", x, y)])
    for i, bx in enumerate(bnd):
        for y in range(SIZE):
            off = int(round((wob(bx, y) - 0.5) * 4.0))
            if h01(seed, "log:gap", i, y) < 0.14:
                continue                                  # 纵缝断断续续，别画成直尺
            cv.put(bx + off, y, seam)
            if h01(seed, "log:ridge", i, y // 6) < 0.45:
                cv.put(bx + off + 1, y, levels[4])
    for i in range(plates + 1):
        for d in range(int(5 * SIZE * sarea(0.05))):
            x0 = i * 10 + int(h01(seed, "log:fb", i, d) * 8)
            y0 = int(h01(seed, "log:fy", i, d) * SIZE)
            ln = 3 + int(h01(seed, "log:fl", i, d) * 3)
            col = levels[0] if h01(seed, "log:fc", i, d) < 0.6 else levels[4]
            for k in range(ln):
                cv.put(x0 + (k // 2) - (k // 4), y0 + k, col)
    for row in (5, 20):
        for x in range(SIZE):
            if h01(seed, "log:bgap", row, x) < 0.15:
                continue                                  # 环带被节瘤/裂口打断
            o = int(round((wob(x * 0.5 + 40, 0) - 0.5) * 3.0))
            cv.put(x, row + o, levels[0])
            cv.put(x, row + o + 1, seam)
    speckle(cv, seed, "log:dust", 0.02, P("BASE@L240", p["base"]))
    return cv


def t_log_top(seed):
    """枯树横截面：4px 树皮环 + 年轮（明暗交替 + 1px 亮轮线）+ 2 道放射炭化裂。
    32 档才容得下 4 条年轮，16 档只能画 2 条，读起来是"同心圆"而不是"年轮"。"""
    cv = C()
    p = {"base": "ASH_BARK"}
    bark1 = P("BASE@D140", p["base"])
    bark2 = P("BASE@D220", p["base"])
    core = P("ASH_RING@D140")
    ralt = P("ASH_RING@D220")
    rlit = P("ASH_RING@L180")
    seam = P("ASH_CHAR")
    cx = cy = (SIZE - 1) / 2.0
    for y in range(SIZE):
        for x in range(SIZE):
            dx = abs(x - cx)
            dy = abs(y - cy)
            m = max(dx, dy) + 0.55 * math.sqrt(dx * dx + dy * dy)
            if m > 27.0:
                cv.put(x, y, bark2)
            elif m > 24.0:
                cv.put(x, y, bark1)
            else:
                r = math.sqrt(dx * dx + dy * dy) + (h01(seed, "logt:n", x // 4, y // 4) - 0.5) * 1.2
                band = int(r // 4) % 2
                col = core if band == 0 else ralt
                if (r % 4) < 0.55:
                    col = rlit
                cv.put(x, y, col)
    for i in range(2):
        ang = h01(seed, "logt:a", i) * TAU
        pts = []
        for k in range(0, 13):
            rr = 2.0 + k * 1.9
            a = ang + (h01(seed, "logt:w", i, k) - 0.5) * 0.22
            pts.append((cx + math.cos(a) * rr, cy + math.sin(a) * rr))
        cv.path(pts, seam, wrap=False)
    speckle(cv, seed, "logt:pit", 0.05, seam)
    return cv


def t_thorn(seed):
    """灰烬棘丛：6 根 1px 细斜枝（长度 x2、粗细不变、连续不断线）+ 交错倒刺 + 节瘤炭点 + 灰白枯梢；
    和石刺簇是形态对立（细/乱/暗 vs 粗/整/亮），所以这里绝不出现块状填充，也不让节瘤把主线打断。"""
    cv = C()
    p = {"base": "THORN_ASH"}
    dk = P("BASE@D340", p["base"])
    mid = P("BASE@D140", p["base"])
    lt = P("BASE@L200", p["base"])
    node = P("ASH_CHAR")
    tip = P("GLINT@D220")
    for i in range(6):
        # 6 根枝从底部同一丛扇出（不是均匀一排草），才读作"棘丛"而不是"草皮"
        x = float(11 + i * 2 + int(h01(seed, "th:x", i) * 3) - int(h01(seed, "th:x2", i) * 2))
        y = float(SIZE - 1)
        h = 14 + int(h01(seed, "th:h", i) * 10)
        dr = -1 if x < 15.5 else 1
        for k in range(h):
            col = lt if k % 3 == 0 else mid
            cv.put(x, y, col)
            if k < 4:
                cv.put(x + 1, y, dk)
            if h01(seed, "th:barb", i, k) < 0.20 and 3 < k < h - 2:
                bx, by = x - dr, y - 1
                for s in range(2 + int(h01(seed, "th:bl", i, k) * 2)):
                    cv.put(bx, by, node if s == 0 else mid)
                    bx -= dr
                    by -= 1
            if h01(seed, "th:node", i, k) < 0.10:
                cv.put(x, y - 1, node)
            if h01(seed, "th:turn", i, k) < 0.22:
                dr = -dr
            x += dr * (1 if h01(seed, "th:step", i, k) < 0.42 else 0)
            y -= 1
            if not (0 <= x < SIZE and 0 <= y < SIZE):
                break
        if h01(seed, "th:tip", i) < 0.35:
            cv.put(x, y, tip)
    return cv


BUILDERS = [
    ("shattered_corestone", t_corestone, False),
    ("shattered_monolith", t_monolith, False),
    ("shattered_ash_top", t_ash_top, False),
    ("shattered_ash_top_side", t_ash_side, False),
    ("shattered_ash_base", t_ash_base, False),
    ("shattered_slag_top", t_slag_top, False),
    ("shattered_slag_top_side", t_slag_side, False),
    ("shattered_slag_base", t_slag_base, False),
    ("shattered_glass_top", t_glass_top, False),
    ("shattered_glass_top_side", t_glass_side, False),
    ("shattered_glass_base", t_glass_base, False),
    ("shattered_tar_top", t_tar_top, False),
    ("shattered_tar_top_side", t_tar_side, False),
    ("shattered_tar_base", t_tar_base, False),
    ("shattered_spike_cluster", t_spike, True),
    ("shattered_ash_log_side", t_log_side, False),
    ("shattered_ash_log_top", t_log_top, False),
    ("shattered_ash_thorn", t_thorn, True),
]


def gross_seam(im):
    """缝感粗测（分轴，对"内部最差相邻差"归一）：环绕接缝 x=31|0 / y=31|0 的平均相邻差
    除以内部各列（行）相邻差的最大值。<=1 表示接缝不比图内最突兀的一条线更跳 => 不出缝。
    （对内部中位数归一会把"一条棱面界正好跨缝"误报成出缝，故用 max 作分母。）"""
    px = im.load()

    def d1(x0, y0, x1, y1):
        a = px[x0 % SIZE, y0 % SIZE]
        b = px[x1 % SIZE, y1 % SIZE]
        return sum(abs(a[i] - b[i]) for i in range(3))

    col = [sum(d1(SIZE - 1 - x, y, SIZE - x, y) for y in range(SIZE)) / float(SIZE)
           for x in range(1, SIZE - 1)]
    row = [sum(d1(x, SIZE - 1 - y, x, SIZE - y) for x in range(SIZE)) / float(SIZE)
           for y in range(1, SIZE - 1)]
    ex = sum(d1(SIZE - 1, y, 0, y) for y in range(SIZE)) / float(SIZE)
    ey = sum(d1(x, SIZE - 1, x, 0) for x in range(SIZE)) / float(SIZE)
    return ex / max(col), ey / max(row)


# 每张的拼接口径：xy = 两向都要无缝（地表/石），x = 只横向（侧贴面/原木侧面），- = 不拼接
SEAM_KIND = {
    "shattered_corestone": "xy", "shattered_monolith": "xy",
    "shattered_ash_top": "xy", "shattered_ash_base": "xy",
    "shattered_slag_top": "xy", "shattered_slag_base": "xy",
    "shattered_glass_top": "xy", "shattered_glass_base": "xy",
    "shattered_tar_top": "xy", "shattered_tar_base": "xy",
    "shattered_ash_top_side": "x", "shattered_slag_top_side": "x",
    "shattered_glass_top_side": "x", "shattered_tar_top_side": "x",
    "shattered_ash_log_side": "x", "shattered_ash_log_top": "-",
    "shattered_spike_cluster": "-", "shattered_ash_thorn": "-",
}


def main(argv):
    only = set(a for a in argv[1:] if not a.startswith("-"))
    rows = []
    for key, fn, alpha in BUILDERS:
        if only and key not in only:
            continue
        cv = fn(SEEDS[key])
        im = cv.image(alpha_bg=alpha)
        out = BLK / (key + ".png")
        im.save(out)                                  # 逐张增量写盘
        cols = {p[:3] for p in im.getdata() if p[3] != 0}
        bad = cols & set(FORBIDDEN)
        tr = sum(1 for p in im.getdata() if p[3] == 0)
        al = sorted({p[3] for p in im.getdata()})
        cov = (SIZE * SIZE - tr) / float(SIZE * SIZE)
        sx, sy = gross_seam(im)
        kind = SEAM_KIND[key]
        worst = max(sx, sy) if kind == "xy" else (sx if kind == "x" else 0.0)
        rows.append((key, im.size, len(cols), "255" if al == [255] else "0/255",
                     tr, round(cov, 3), round(sx, 2), round(sy, 2), kind,
                     "OVER16" if len(cols) > 16 else "", "PURE" if bad else "",
                     "SEAM" if worst > 1.0 else ""))
        print("%-28s %s rgb=%2d a=%-5s tr=%4d cov=%.3f sx=%.2f sy=%.2f [%s] %s" % (
            rows[-1][0], rows[-1][1], rows[-1][2], rows[-1][3], rows[-1][4], rows[-1][5],
            rows[-1][6], rows[-1][7], rows[-1][8],
            " ".join(f for f in rows[-1][9:] if f) or "OK"))
    NOTE.parent.mkdir(parents=True, exist_ok=True)
    NOTE.write_text("key\tsize\trgb\talpha\ttransparent\tcoverage\tseam_x\tseam_y\ttileable\tflags\n"
                    + "\n".join("\t".join(str(v) for v in r) for r in rows) + "\n", encoding="utf-8")
    print("wrote", NOTE)


if __name__ == "__main__":
    main(sys.argv)
