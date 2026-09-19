#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""gen_dim1_blocks.py — dim1（繁荣蒸汽时代遗迹 + 破碎之地）S7a 方块贴图确定性生成器。

唯一权威 = tools/artgen/dim1/manifest.json（PALETTE / DERIVED_RULES / SEEDS / STYLE / OUTPUTS）。
契约来源：plan/新维度计划/dim1-implementation-plan.md §4（:335-356）+ §S7a（:237-243）；
技能：gtnh-block-texture-artgen（manifest 单点、LCG 确定性、双跑 SHA256 对拍、预览板目检）。

确定性纪律（本文件自检 + 断言落盘）：
- 零随机源：禁 random / time / datetime（_assert_source_clean() 用 ast 扫描本文件源码）；
  所有伪随机 = splitmix64 混合 + FNV-1a 标签（禁用内建 hash()，其受 PYTHONHASHSEED 影响）。
- 无缝纪律：地表类基场 = 周期 16 的环绕格点值噪（f(x+16,y)==f(x,y) 逐点断言），
  一切落笔经 put() 环绕，故整张图是"可拼接周期图案"；再用边缘/内部相邻差比 (seam ratio) 客观量化缝感。
- 双跑对拍：build_all() 独立构建两遍逐字节比对；落盘后回读字节核验；PNG IHDR 断言（16x16 / 8bpc / RGBA）。
- 像素纪律：每张 distinct RGB ≤ 16、禁纯黑 #000000 与纯白 #FFFFFF（锚点亦非极值）、alpha 恒 255。

产物：
- src/main/resources/assets/gtsr/textures/blocks/<19 张>.png（同名，未改任何 Java 注册名）
- plan/新维度计划/review/dim1/textures/preview.png（预览板：19 张 8x + 拼接 + 色数/缝感指标）

草侧面类（kind=grass_side，19 张中的 2 张：prosperity_surface_rust_grass_side / shattered_grass_side）：
- 色彩按 manifest.params_from 引用既有条目（body ← 同族 dirt 的调色板、fringe ← 草顶面的草沿色彩），零新增色值；
- 草沿锚顶（顶部 3-4px）+ 土体在下 → 只断言水平（x）环边（entry.seam_axes="x"）；
  垂直向是设计不连续（草沿对其下方土体），row 指标仍打印供目检，不做自拼接断言。

用法：python tools/artgen/dim1/gen_dim1_blocks.py（任意 cwd；路径相对本文件定位）
"""

import ast
import hashlib
import io
import json
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[3]
HERE = Path(__file__).resolve().parent
MANIFEST_PATH = HERE / "manifest.json"
ASSET_DIR = ROOT / "src" / "main" / "resources" / "assets" / "gtsr" / "textures" / "blocks"
PREVIEW_PATH = ROOT / "plan" / "新维度计划" / "review" / "dim1" / "textures" / "preview.png"

SIZE = 16
BASE_SIZE = 16          # 母版尺度：manifest 与画笔里所有 px 量按 16 标定
W = SIZE
R = 1                   # 线性倍率 = SIZE // BASE_SIZE（16→1，32→2）
M64 = (1 << 64) - 1
TAU = math.pi * 2.0
FORBIDDEN_MODULES = ("random", "time", "datetime")
FORBIDDEN_RGB = ((0, 0, 0), (255, 255, 255))
DIRS = ((1, 0), (0, 1), (-1, 0), (0, -1))
PREVIEW_BG = (18, 20, 24, 255)
PREVIEW_FG = (232, 228, 216, 255)
PREVIEW_DIM = (150, 146, 134, 255)
PREVIEW_WARN = (214, 168, 96, 255)
PREVIEW_HDR = (214, 208, 188, 255)


# --------------------------------------------------------------------------
# 尺度参数化（P10a，与 tools/artgen/dim7879/gen_dim7879_blocks.py 同口径）：
# SIZE 可在 16 / 32 之间切换，默认 16 = 在产资源母版；14 张在用 dim1 贴图因此在两档下都可再生。
#   R1 场波长与取模周期不随 R 缩放（cells / period 保持"每张贴图"口径 ⇒ 特征世界尺寸不变）；
#   R2 离散笔刷长度 ×R、其布点密度 ÷R²；R3 单像素斑点密度不变（覆盖率不变、颗粒变细）；
#   R4 1px 发丝线（描边、裂纹主线、机壳框）不加倍，轮廓级条带（草沿、铁轨带、法兰、砖皮、铆钉）×R；
#   R5 按像素计数的门槛 ×R / ×R²，按比率的门槛（色数 ≤16、覆盖率）不动。
# R=1 时全部表达式恒等于母版写法 ⇒ 16 档产物逐字节不变。
# --------------------------------------------------------------------------
def set_size(size):
    """切换出图尺度；size 必须是 BASE_SIZE 的整数倍（保证格点/周期整数倍率，R=1 时零漂移）。"""
    global SIZE, W, R
    size = int(size)
    if size < BASE_SIZE or size % BASE_SIZE:
        raise AssertionError("SIZE 必须是 %d 的正整数倍（实得 %d）" % (BASE_SIZE, size))
    SIZE = size
    W = size
    R = size // BASE_SIZE
    return SIZE


def sp(v):
    """线性 px 量（行/列号、母版坐标、条带厚度、笔刷长度）→ 当前尺度；R=1 恒等。"""
    return int(v) * R if isinstance(v, int) else v * R


def srows(base):
    """把母版的"一个 1px 行/列号"展开成当前尺度下的连续 R 行/列（R=1 时逐位相同）。"""
    base = int(base) * R
    return [base + k for k in range(R)]


def sarea(d):
    """面密度（每像素命中率）→ 当前尺度：R=1 恒等，R=2 时 ÷4。"""
    return float(d) / float(R * R)


def zoom(base):
    """预览板放大倍率：母版按 base 倍放大，32 档自动降倍以保持版面尺寸稳定。"""
    return max(1, int(base) // R)


# --------------------------------------------------------------------------
# 确定性原语（禁 random/time/datetime）
# --------------------------------------------------------------------------
def fnv1a(text):
    """FNV-1a 64 位（替代内建 hash()，不受 PYTHONHASHSEED 影响）。"""
    h = 0xCBF29CE484222325
    for byte in text.encode("utf-8"):
        h = (h ^ byte) & M64
        h = (h * 0x100000001B3) & M64
    return h


def h01(seed, tag, i, j):
    """(seed, tag, i, j) 纯函数 → [0,1) 浮点（splitmix64 混合，无全局状态）。"""
    h = seed & M64
    h = (h ^ (fnv1a(tag) * 0x9E3779B97F4A7C15)) & M64
    h = (h ^ ((i & M64) * 0xBF58476D1CE4E5B9)) & M64
    h = (h ^ ((j & M64) * 0x94D049BB133111EB)) & M64
    h ^= h >> 30
    h = (h * 0xBF58476D1CE4E5B9) & M64
    h ^= h >> 27
    h = (h * 0x94D049BB133111EB) & M64
    h ^= h >> 31
    return (h >> 11) / float(1 << 53)


def tile_noise(seed, tag, cells):
    """周期 16 的格点值噪（格点环绕 → f(x+16,y) == f(x,y) 精确成立）。"""
    lat = [[h01(seed, tag + ":lat", i, j) for j in range(cells)] for i in range(cells)]

    def smooth(t):
        return t * t * (3.0 - 2.0 * t)

    def field(x, y):
        u = x * cells / float(SIZE)
        v = y * cells / float(SIZE)
        i0 = int(math.floor(u))
        j0 = int(math.floor(v))
        fx = smooth(u - i0)
        fy = smooth(v - j0)
        i0 %= cells
        j0 %= cells
        i1 = (i0 + 1) % cells
        j1 = (j0 + 1) % cells
        top = lat[i0][j0] * (1.0 - fx) + lat[i1][j0] * fx
        bot = lat[i0][j1] * (1.0 - fx) + lat[i1][j1] * fx
        return top * (1.0 - fy) + bot * fy

    return field


def octave_field(seed, tag, octaves):
    """多倍频加权和（权重归一），仍严格周期 16。"""
    parts = [(tile_noise(seed, tag + ":o%d" % cells, cells), weight) for cells, weight in octaves]
    total = float(sum(weight for _, weight in parts))

    def field(x, y):
        return sum(f(x, y) * weight for f, weight in parts) / total

    return field


def shade(color, toward, t):
    """线性插值 shade(基色, 锚点, t)；t 值唯一来源 = manifest.DERIVED_RULES。"""
    return tuple(int(round(color[i] + (toward[i] - color[i]) * t)) for i in range(4))


class Rules(object):
    """manifest.PALETTE + DERIVED_RULES 的解析器（'NAME@TOKEN' → RGBA）。"""

    def __init__(self, manifest):
        self.palette = {}
        for key, value in manifest["PALETTE"].items():
            if key.startswith("_"):
                continue
            self.palette[key] = _c(value)
        self.rules = {}
        for key, spec in manifest["DERIVED_RULES"].items():
            if key.startswith("_"):
                continue
            self.rules[key] = (spec["toward"], float(spec["t"]))

    def base(self, spec, base_key):
        return self.resolve(spec, base_key)

    def resolve(self, spec, base_key):
        name = spec
        token = None
        if "@" in spec:
            name, token = spec.split("@", 1)
        if name == "BASE":
            color = self.palette[base_key]
        elif name in self.palette:
            color = self.palette[name]
        else:
            color = _c(name)
        if token is not None:
            toward, t = self.rules[token]
            color = shade(color, self.palette[toward], t)
        return color

    def levels(self, specs, base_key):
        return [self.resolve(spec, base_key) for spec in specs]


def _c(hexstr):
    """'8A7B4A' → RGBA。"""
    h = hexstr.lstrip("#")
    if len(h) == 6:
        h = h + "FF"
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), int(h[6:8], 16))


class Tex(object):
    """16x16 画布：一切落笔经环绕（mod 16），故任何笔触天然可拼接。"""

    def __init__(self):
        self.px = [[None] * W for _ in range(W)]

    def put(self, x, y, color, wrap=True):
        if wrap:
            x %= W
            y %= W
        elif not (0 <= x < W and 0 <= y < W):
            return
        self.px[y][x] = color

    def get(self, x, y, wrap=True):
        if wrap:
            x %= W
            y %= W
        elif not (0 <= x < W and 0 <= y < W):
            return None
        return self.px[y][x]

    def fill_field(self, field, levels, bias=None):
        """基场 → 分级上色（levels 由暗到亮；bias 为可选逐像素偏移函数）。"""
        n = len(levels)
        for y in range(W):
            for x in range(W):
                value = field(x, y)
                if bias is not None:
                    value = bias(x, y, value)
                idx = int(value * n)
                idx = 0 if idx < 0 else (n - 1 if idx >= n else idx)
                self.put(x, y, levels[idx])

    def image(self):
        im = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
        p = im.load()
        for y in range(W):
            for x in range(W):
                c = self.px[y][x]
                if c is None:
                    raise AssertionError("像素空洞 (%d,%d)" % (x, y))
                p[x, y] = c
        return im


# --------------------------------------------------------------------------
# 公共绘制件
# --------------------------------------------------------------------------
def scatter(seed, tag, density, wrapped=True):
    """确定性命中表：返回 [(x, y)]，命中率 ≈ density（逐像素独立 → 无方向偏好）。"""
    out = []
    for y in range(W):
        for x in range(W):
            if h01(seed, tag, x, y) < density:
                out.append((x, y))
    return out


def patch_field(seed, tag, cells, threshold):
    """低频斑块掩码（周期 16），用于泥斑/焦斑等成片结构。"""
    field = tile_noise(seed, tag, cells)
    return [[field(x, y) > threshold for x in range(W)] for y in range(W)]


def crack_paths(seed, tag, count, length, start_at=None):
    """确定性走线（环绕坐标）：对象裂纹/缝隙/矿脉通用件。

    length 以母版（16）像素标定，×R 后保持世界长度；count 是每张图上的条数（不动）。
    """
    length = (sp(length[0]), sp(length[1]))
    paths = []

    for k in range(count):
        x = int(h01(seed, tag + ":x", k, 0) * W)
        y = int(h01(seed, tag + ":y", k, 0) * W)
        if start_at is not None:
            x, y = start_at[k % len(start_at)]
        direction = int(h01(seed, tag + ":d", k, 0) * 4.0) % 4
        steps = int(length[0] + h01(seed, tag + ":l", k, 0) * (length[1] - length[0] + 1))
        path = []
        for step in range(steps):
            path.append((x % W, y % W))
            if h01(seed, tag + ":t", k, step) < 0.34:
                turn = 1 if h01(seed, tag + ":s", k, step) < 0.5 else 3
                direction = (direction + turn) % 4
            dx, dy = DIRS[direction]
            x += dx
            y += dy
        paths.append(path)
    return paths


def paint_blob(t, seed, tag, cx, cy, rad, core, edge):
    """不规则斑块（锈斑/漆面剥落/炭斑）：半径内按距离衰减 + 哈希腐蚀边缘 → 拒绝方块感。

    rad 以母版（16）像素标定，×R 后保持世界尺寸（R=1 恒等）。
    """
    rad = float(rad) * R
    span = int(math.ceil(rad)) + 1

    for iy in range(-span, span + 1):
        for ix in range(-span, span + 1):
            x = cx + ix
            y = cy + iy
            dxr = abs(ix)
            dxr = min(dxr, W - dxr)
            dyr = abs(iy)
            dyr = min(dyr, W - dyr)
            dist = math.sqrt(dxr * dxr + dyr * dyr)
            if dist > rad:
                continue
            keep = 0.98 - 0.62 * (dist / rad)
            if h01(seed, tag + ":e", x, y) > keep:
                continue
            t.put(x, y, core if dist <= rad * 0.5 else edge)


def paint_specks(t, seed, tag, count, colors, pair_chance=0.28):
    """单像素斑点（锈点/晶粒/闪点）：位置与取色全由 (seed, tag, k) 决定，无方块块面。"""
    n = len(colors)
    for k in range(count):
        x = int(h01(seed, tag + ":x", k, 0) * W)
        y = int(h01(seed, tag + ":y", k, 0) * W)
        color = colors[int(h01(seed, tag + ":c", k, 0) * n) % n]
        t.put(x, y, color)
        if h01(seed, tag + ":p", k, 0) < pair_chance:
            t.put(x + 1, y, color)


def paint_crack_line(t, seed, tag, count, length, core, halo=None, halo_axis="v", faint=None):
    """细裂纹（1px 主线 + 可选单侧晕 + 可选断续浅纹）；先铺全部晕、再压主线（防晕吃线）。"""
    paths = crack_paths(seed, tag, count, length)
    if halo is not None:
        for path in paths:
            for x, y in path:
                if halo_axis == "v":
                    t.put(x, y - 1, halo)
                    t.put(x, y + 1, halo)
                else:
                    t.put(x - 1, y, halo)
                    t.put(x + 1, y, halo)
    if faint is not None:
        for path in paths:
            for i, (x, y) in enumerate(path):
                if i % 3 == 0:
                    t.put(x + 1, y, faint)
    for path in paths:
        for x, y in path:
            t.put(x, y, core)
    return paths


def draw_rivets(t, sites, lit, mid, dark):
    """2x2 铆钉（左上受光 / 右下背光），sites = 母版（16）口径的左上角坐标。

    铆钉是轮廓级凸起（不是 1px 发丝线）⇒ 位置与边长都按 ×R 走；R=1 时逐位等价于母版 2x2。
    """
    n = 1 + R
    for rx, ry in sites:
        x0, y0 = int(rx) * R, int(ry) * R
        for dy in range(n):
            for dx in range(n):
                if dx == 0 and dy == 0:
                    color = lit
                elif dx == n - 1 and dy == n - 1:
                    color = dark
                elif dx + dy == n - 1:
                    color = mid
                else:
                    color = mid
                t.put(x0 + dx, y0 + dy, color)


def draw_frame(t, frame, lip):
    """1px 机壳框：上/左受光唇，下/右背光框（发丝线纪律 R4：32 档仍 1px，不做 ×R 增厚）。"""
    for x in range(W):
        t.put(x, 0, lip)
        t.put(x, W - 1, frame)
    for y in range(W):
        t.put(0, y, lip)
        t.put(W - 1, y, frame)


def fill_bricks(t, seed, params, levels, joint, joint_lip):
    """砖砌布局（母版口径：4 行皮数，8 宽砖，奇偶皮错 4，砖色按 (皮, 砖位) 哈希取阶）。

    皮高 / 砖宽 / 竖缝列号都是轮廓级条带 ⇒ ×R（R4）；哈希键随之走，保证 32 档砖位仍确定。
    """
    course_h = sp(params["course_h"])
    seams_even = set(sp(x) for x in params["joint_x_even"])
    seams_odd = set(sp(x) for x in params["joint_x_odd"])
    brick_w = sp(8)
    n = len(levels)
    for y in range(W):
        course = y // course_h
        in_course = y % course_h
        seams = seams_even if course % 2 == 0 else seams_odd
        for x in range(W):
            if in_course == course_h - 1 or x in seams:
                t.put(x, y, joint)
            elif in_course == course_h - 2:
                t.put(x, y, joint_lip)
            else:
                tone = levels[int(h01(seed, "brick", course, x // brick_w) * n) % n]
                t.put(x, y, tone)


# --------------------------------------------------------------------------
# 各 kind 的画法
# --------------------------------------------------------------------------
def paint_terrain_grit(r, seed, p):
    levels = r.levels(p["levels"], p["base"])
    field = octave_field(seed, "mot", OCTAVES)
    clods = patch_field(seed, "clod", 4, 0.74) if p.get("clod") else None
    t = Tex()
    grit_dark = r.resolve(p["grit_dark"], p["base"])
    grit_lit = r.resolve(p["grit_lit"], p["base"])
    clod = r.resolve(p["clod"], p["base"]) if p.get("clod") else None
    flake = r.resolve(p["flake"], p["base"]) if p.get("flake") else None
    n = len(levels)
    for y in range(W):
        for x in range(W):
            idx = int(field(x, y) * n)
            idx = max(0, min(n - 1, idx))
            if clods is not None and clods[y][x]:
                idx = max(0, idx - 1)
            color = levels[idx]
            if clod is not None and clods is not None and clods[y][x] and h01(seed, "clodk", x, y) < 0.55:
                color = clod
            t.put(x, y, color)
    for x, y in scatter(seed, "grit", p["grit_density"]):
        t.put(x, y, grit_dark if h01(seed, "gritk", x, y) < 0.62 else grit_lit)
    if flake is not None:
        for x, y in scatter(seed, "flake", p["flake_density"]):
            t.put(x, y, flake)
    return t


def paint_terrain_ripple(r, seed, p):
    levels = r.levels(p["levels"], p["base"])
    field = octave_field(seed, "mot", OCTAVES)
    period = p["ripple_period"]
    strength = p["ripple_strength"]

    def bias(x, y, value):
        wave = 0.5 + 0.5 * math.cos(TAU * (y % period) / float(period))
        return value + strength * 0.40 * (wave - 0.5)

    t = Tex()
    t.fill_field(field, levels, bias=bias)
    grit_dark = r.resolve(p["grit_dark"], p["base"])
    grit_lit = r.resolve(p["grit_lit"], p["base"])
    for x, y in scatter(seed, "grit", p["grit_density"]):
        t.put(x, y, grit_dark if h01(seed, "gritk", x, y) < 0.55 else grit_lit)
    return t


def paint_terrain_fiber(r, seed, p):
    levels = r.levels(p["levels"], p["base"])
    field = octave_field(seed, "mot", OCTAVES)
    period = p["fiber_row_period"]

    def bias(x, y, value):
        wave = 0.5 + 0.5 * math.cos(TAU * (y % period) / float(period))
        return value + 0.10 * (wave - 0.5)

    t = Tex()
    t.fill_field(field, levels, bias=bias)
    fiber_dark = r.resolve(p["fiber_dark"], p["base"])
    fiber_lit = r.resolve(p["fiber_lit"], p["base"])
    muck = r.resolve(p["muck"], p["base"])
    mucks = patch_field(seed, "muck", 4, 0.78)
    for y in range(W):
        for x in range(W):
            if mucks[y][x] and h01(seed, "muckk", x, y) < 0.7:
                t.put(x, y, muck)
    for x, y in scatter(seed, "fib", sarea(p["fiber_density"])):
        dash = sp(1 + int(h01(seed, "fibl", x, y) * 3.0))
        color = fiber_dark if h01(seed, "fibk", x, y) < 0.6 else fiber_lit
        for i in range(dash):
            t.put(x + i, y, color)

    return t


def paint_terrain_smooth(r, seed, p):
    levels = r.levels(p["levels"], p["base"])
    octs = [(int(c), float(w)) for c, w in p.get("octaves", OCTAVES)]
    field = octave_field(seed, "mot", octs)
    jitter = float(p.get("dither", 0.0))

    def bias(x, y, value):
        return value + jitter * (h01(seed, "dither", x, y) - 0.5) if jitter > 0.0 else value

    t = Tex()
    t.fill_field(field, levels, bias=bias if jitter > 0.0 else None)
    sheen = r.resolve(p["sheen"], p["base"])
    halo = r.resolve(p["sheen_halo"], p["base"])
    for x, y in scatter(seed, "sheen", p["sheen_density"]):
        t.put(x, y, sheen)
        t.put(x, y + 1, halo)
    return t


def paint_stone_brick(r, seed, p):
    levels = r.levels(p["brick_levels"], p["base"])
    joint = r.resolve(p["joint"], p["base"])
    joint_lip = r.resolve(p["joint_lip"], p["base"])
    t = Tex()
    fill_bricks(t, seed, p, levels, joint, joint_lip)
    pit = r.resolve(p["pit"], p["base"])
    for x, y in scatter(seed, "pit", p["pit_density"]):
        t.put(x, y, pit)
    rust_levels = r.levels(p["rust_levels"], p["base"])
    for k in range(int(p["blob_count"])):
        cx = int(h01(seed, "blobx", k, 0) * W)
        cy = int(h01(seed, "bloby", k, 0) * W)
        rad = 1.1 + h01(seed, "blobr", k, 0) * 1.5
        paint_blob(t, seed, "rust%d" % k, cx, cy, rad, rust_levels[1], rust_levels[0])
    return t


def paint_grass_tinted(r, seed, p):
    levels = r.levels(p["levels"], p["base"])
    field = octave_field(seed, "mot", [(2, 0.52), (4, 0.30), (8, 0.18)])
    t = Tex()
    t.fill_field(field, levels)
    blade_dark = r.resolve(p["blade_dark"], p["base"])
    blade_lit = r.resolve(p["blade_lit"], p["base"])
    speckle = r.resolve(p["speckle"], p["base"])
    for x, y in scatter(seed, "blade", sarea(p["blade_density"])):
        length = sp(1 + int(h01(seed, "bladel", x, y) * 3.0))
        for i in range(length):
            t.put(x, y - i, blade_dark)
        if h01(seed, "bladek", x, y) < 0.40:
            t.put(x, y - length + 1, blade_lit)

    for x, y in scatter(seed, "speck", p["speckle_density"]):
        t.put(x, y, speckle)
    blotch = p.get("blotch")
    if blotch is not None:
        blotch_color = r.resolve(blotch, p["base"])
        for k in range(int(p.get("blotch_count", 0))):
            cx = int(h01(seed, "blotchx", k, 0) * W)
            cy = int(h01(seed, "blotchy", k, 0) * W)
            paint_blob(t, seed, "blotch%d" % k, cx, cy, 1.2 + h01(seed, "blotchr", k, 0), blotch_color, speckle)
    return t


def paint_grass_shattered(r, seed, p):
    levels = r.levels(p["levels"], p["base"])
    accent_levels = r.levels(p["accent_levels"], p["accent"])
    field = octave_field(seed, "mot", OCTAVES)
    blend = octave_field(seed, "acc", [(4, 0.6), (8, 0.4)])
    t = Tex()
    n = len(levels)
    for y in range(W):
        for x in range(W):
            idx = max(0, min(n - 1, int(field(x, y) * n)))
            accent_mix = blend(x, y) * 0.55 + p["accent_blend"] * 0.45
            t.put(x, y, accent_levels[idx] if h01(seed, "acc", x, y) < accent_mix else levels[idx])
    blade_dark = r.resolve(p["blade_dark"], p["base"])
    for x, y in scatter(seed, "blade", sarea(0.34)):
        length = sp(1 + int(h01(seed, "bladel", x, y) * 2.0))
        for i in range(length):
            t.put(x, y - i, blade_dark)
    paint_crack_line(
        t, seed, "crack", int(p["crack_count"]), (4, 8), r.resolve(p["crack"], p["base"]),
        halo=None, faint=None)
    glint = r.resolve(p["glint"], p["base"])
    for x, y in scatter(seed, "glint", p["glint_density"]):
        t.put(x, y, glint)
    return t


def paint_grass_side(r, seed, p):
    """草方块侧面（kind=grass_side）：土体 + 顶部 3-4px 草沿（色彩复用，见 manifest.params_from）。

    - 土体：直接复用 paint_terrain_grit(同 params, 异种子) → 与同族 dirt 贴图同画法不同排列；
    - 草沿：逐列高度 h ∈ [fringe_min, fringe_max]（周期 16 场取阶上色；有 accent_levels 的
      变体按 paint_grass_shattered 同款混色规则叠 accent），沿口下缘压 blade_dark、顶缘偶受光
      blade_lit、沿内偶见 edge_speckle，沿下 1px 用土体深阶投影、偶有 1px 垂落草茎；
    - 无缝：一切落笔经 put() 环绕 + 周期 16 场 → 水平（x）自拼接；垂直为设计不连续（草沿在上），
      由 manifest entry.seam_axes 声明，断言只在 x 轴做。
    """
    body = p["body"]
    fringe = p["fringe"]
    t = paint_terrain_grit(r, seed, body)

    levels = r.levels(fringe["levels"], fringe["base"])
    n = len(levels)
    blade_dark = r.resolve(fringe["blade_dark"], fringe["base"]) if fringe.get("blade_dark") else None
    blade_lit = r.resolve(fringe["blade_lit"], fringe["base"]) if fringe.get("blade_lit") else None
    accent_levels = (
        r.levels(fringe["accent_levels"], fringe["accent"]) if fringe.get("accent_levels") else None)
    accent_blend = float(fringe.get("accent_blend", 0.0))
    fringe_min = sp(p["fringe_min"])          # 草沿厚度：轮廓级条带 ⇒ ×R（R4）
    fringe_max = sp(p["fringe_max"])

    hang = float(p.get("hang_density", 0.0))
    speckle = r.resolve(p["edge_speckle"], fringe["base"]) if p.get("edge_speckle") else None
    shadow = r.resolve(p["shadow"], body["base"]) if p.get("shadow") else None

    field = octave_field(seed, "gras", [(2, 0.52), (4, 0.30), (8, 0.18)])
    blend = octave_field(seed, "gacc", [(4, 0.6), (8, 0.4)]) if accent_levels else None
    heights = []
    for x in range(W):
        h = fringe_min + int(h01(seed, "fh", x, 0) * (fringe_max - fringe_min + 1))
        h = max(fringe_min, min(fringe_max, h))
        heights.append(h)
        for y in range(h):
            idx = max(0, min(n - 1, int(field(x, y) * n)))
            color = levels[idx]
            if accent_levels is not None:
                mix = blend(x, y) * 0.55 + accent_blend * 0.45
                if h01(seed, "gacck", x, y) < mix:
                    color = accent_levels[idx]
            if y == h - 1 and blade_dark is not None and h01(seed, "gedge", x, 0) < 0.55:
                color = blade_dark
            elif y == 0 and blade_lit is not None and h01(seed, "glit", x, 0) < 0.35:
                color = blade_lit
            t.put(x, y, color)
        if shadow is not None:
            t.put(x, h, shadow)
        if blade_dark is not None and h01(seed, "ghang", x, 0) < hang:
            t.put(x, h + 1, blade_dark)
    if speckle is not None:
        for x, y in scatter(seed, "gspk", float(p["edge_speckle_density"])):
            if y < heights[x]:
                t.put(x, y, speckle)
    return t


def paint_rift_stone(r, seed, p):
    levels = r.levels(p["levels"], p["base"])
    field = octave_field(seed, "mot", OCTAVES)
    t = Tex()
    t.fill_field(field, levels)
    pit = r.resolve(p["pit"], p["base"])
    for x, y in scatter(seed, "pit", p["pit_density"]):
        t.put(x, y, pit)
    crack = r.resolve(p["crack"], p["base"])
    core = r.resolve(p["crack_core"], p["base"])
    glow = r.resolve(p["glow"], p["base"])
    paths = paint_crack_line(
        t, seed, "rift", int(p["crack_count"]), (5, 9), crack, halo=glow, halo_axis="v")
    for path in paths:
        for i, (x, y) in enumerate(path):
            if i % 3 == 1:
                t.put(x, y, core)
    return t


def paint_blackstone(r, seed, p):
    levels = r.levels(p["levels"], p["base"])
    field = octave_field(seed, "mot", [(2, 0.52), (4, 0.30), (8, 0.18)])
    t = Tex()
    t.fill_field(field, levels)
    char = r.resolve(p["char"], p["base"])
    for k in range(int(p["char_density"] * W * W / 3)):
        cx = int(h01(seed, "charx", k, 0) * W)
        cy = int(h01(seed, "chary", k, 0) * W)
        paint_blob(t, seed, "char%d" % k, cx, cy, 1.3 + h01(seed, "charr", k, 0), char, char)
    for x, y in scatter(seed, "chark", p["char_density"]):
        t.put(x, y, char)
    paint_crack_line(t, seed, "vein", int(p["vein_count"]), (3, 7), r.resolve(p["vein"], p["base"]))
    ash = r.resolve(p["ash"], p["base"])
    for x, y in scatter(seed, "ash", p["ash_density"]):
        t.put(x, y, ash)
    return t


def paint_object_sleeper(r, seed, p):
    levels = r.levels(p["wood_levels"], p["base"])
    field = octave_field(seed, "wood", [(2, 0.55), (4, 0.45)])
    t = Tex()
    t.fill_field(field, levels)
    grain = r.resolve(p["grain"], p["base"])
    seam = r.resolve(p["plank_seam"], p["base"])
    groove = r.resolve(p["groove"], p["base"])
    rail = r.resolve(p["rail"], p["base"])
    rail_lit = r.resolve(p["rail_lit"], p["base"])
    rail_shadow = r.resolve(p["rail_shadow"], p["base"])
    spike = r.resolve(p["spike"], p["base"])
    rail_rows = set()
    for base_y in (2, 11):
        for off in range(4 * R):
            rail_rows.add(sp(base_y) + off)
    wood_rows = [y for y in range(W) if y not in rail_rows]
    for y in wood_rows:
        for x in range(W):
            if h01(seed, "grain", x, y) < 0.30:
                t.put(x, y, grain)
    seam_x = sp(9) + int(h01(seed, "seam", 0, 0) * 5.0 * R)
    for y in wood_rows:
        for dx in range(2 * R):
            t.put(seam_x + dx, y, seam)
    for base_y in (2, 11):
        for off, color in enumerate((groove, rail_lit, rail, rail_shadow)):   # 铁轨带：轮廓级 ⇒ 每子带 ×R
            for y in range(sp(base_y) + off * R, sp(base_y) + (off + 1) * R):
                for x in range(W):
                    t.put(x, y, color)
    for sx in (2, 7, 12):
        for dx in range(2 * R):                    # 道钉：母版 2px 宽 → ×R（轮廓级凸起）
            t.put(sp(sx) + dx, sp(5), spike)
            t.put(sp(sx) + dx, sp(14), spike)

    return t


def paint_object_pipe(r, seed, p):
    levels = r.levels(p["pipe_levels"], p["base"])
    profile = [4, 4, 4, 3, 4, 3, 2, 2, 2, 2, 1, 1, 1, 0, 0, 0]   # 母版 16 行的管道明暗剖面

    def prof(y):
        return profile[min(len(profile) - 1, y // R)]     # 母版剖面按 R 最近邻展开（R=1 恒等）

    t = Tex()
    for y in range(W):
        for x in range(W):
            value = 0.30 + 0.55 * (prof(y) / 4.0)
            value += 0.05 * (h01(seed, "pipe", x, y) - 0.5)
            idx = max(0, min(len(levels) - 1, int(value * len(levels))))
            t.put(x, y, levels[idx])
    flange = r.resolve(p["flange"], p["base"])
    flange_edge = r.resolve(p["flange_edge"], p["base"])
    for x in (sp(2), sp(13)):                       # 体内 1px 分模线：发丝线不加倍，位置按比例走
        for y in range(W):
            t.put(x, y, flange_edge)
    flange_cols = srows(0) + srows(1) + srows(14) + srows(15)   # 两端法兰：2px 母版凸缘 ⇒ ×R 增厚
    for x in flange_cols:
        for y in range(W):
            t.put(x, y, flange_edge if (y < R or y >= W - R) else flange)
    for x in flange_cols:
        for k in range(R):
            t.put(x, sp(1) + k, r.resolve(p["flange_lip"], p["base"]))
            t.put(x, W - 1 - R + k, flange_edge)
    rivet_lit = r.resolve(p["rivet_lit"], p["base"])
    rivet_dark = r.resolve(p["rivet_dark"], p["base"])
    for rx, step in ((sp(0), 1), (sp(15), -1)):
        for ry in (sp(4), sp(10)):
            t.put(rx, ry, rivet_lit)
            t.put(rx + step, ry, rivet_dark)
    rust_levels = r.levels(p["rust_levels"], p["base"])
    count = int(p["rust_density"] * W * W)
    for k in range(count):
        x = int(h01(seed, "rustx", k, 0) * W)
        y = int(h01(seed, "rusty", k, 0) * W)
        t.put(x, y, rust_levels[min(1, prof(y) // 3)])
    for k in range(max(1, count // 6)):
        cx = sp(4) + int(h01(seed, "rustbx", k, 0) * 8.0 * R)
        cy = sp(2) + int(h01(seed, "rustby", k, 0) * 12.0 * R)
        paint_blob(t, seed, "prust%d" % k, cx, cy, 0.9, rust_levels[1], rust_levels[0])
    pit = r.resolve(p["pit"], p["base"])
    for x, y in scatter(seed, "pit", p["pit_density"]):
        if sp(3) < x < sp(12):
            t.put(x, y, pit)
    return t


def paint_object_plate(r, seed, p):
    levels = r.levels(p["plate_levels"], p["base"])
    field = octave_field(seed, "plate", [(2, 0.5), (4, 0.32), (8, 0.18)])
    t = Tex()
    t.fill_field(field, levels)
    bevel_lit = r.resolve(p["bevel_lit"], p["base"])
    bevel_dark = r.resolve(p["bevel_dark"], p["base"])
    scratch = r.resolve(p["scratch"], p["base"])
    paint_crack_line(t, seed, "scratch", int(p["scratch_count"]), (4, 9), scratch)
    for y in range(W):
        t.put(sp(7) + int(h01(seed, "seamx", 0, y) * 2.0 * R), y, r.resolve(p["plate_levels"][0], p["base"]))
    rust_levels = r.levels(p["rust_levels"], p["base"])
    for k in range(7):
        cx = int(h01(seed, "rustx", k, 0) * W)
        cy = int(h01(seed, "rusty", k, 0) * W)
        paint_blob(t, seed, "prust%d" % k, cx, cy, 0.9 + h01(seed, "rustr", k, 0), rust_levels[1], rust_levels[0])
    for x in range(W):
        t.put(x, 0, bevel_lit)
        t.put(x, W - 1, bevel_dark)
    for y in range(W):
        t.put(0, y, bevel_lit)
        t.put(W - 1, y, bevel_dark)
    draw_rivets(
        t,
        [tuple(site) for site in p["rivet_sites"]],
        r.resolve(p["rivet_lit"], p["base"]),
        r.resolve(p["rivet_mid"], p["base"]),
        r.resolve(p["rivet_dark"], p["base"]),
    )
    for sx, sy in [tuple(site) for site in p["rivet_sites"]]:
        t.put(sp(sx) + R, sp(sy) + R, r.resolve(p["rivet_dark"], p["base"]))
        t.put(sp(sx) + R, sp(sy), r.resolve(p["rivet_lit"], p["base"]))

    return t


def paint_object_chimney(r, seed, p):
    t = Tex()
    levels = r.levels(p["brick_levels"], p["base"])
    joint = r.resolve(p["joint"], p["base"])
    joint_lip = r.resolve(p["joint_lip"], p["base"])
    fill_bricks(t, seed, p, levels, joint, joint_lip)
    cavity = r.resolve(p["cavity"], p["base"])
    cavity_hi = r.resolve(p["cavity_hi"], p["base"])
    rim = r.resolve(p["rim"], p["base"])
    soot = r.resolve(p["soot"], p["base"])
    break_min = sp(p["break_min"])
    break_span = int(p["break_span"]) * R
    for x in range(W):
        top = break_min + int(h01(seed, "brk", x, 0) * break_span)
        for y in range(top):
            t.put(x, y, cavity if y > 0 else cavity_hi)
        t.put(x, top, rim)
    for x, y in scatter(seed, "soot", p["soot_density"]):
        if y > sp(3):
            t.put(x, y, soot)

    return t


def paint_casing_rusted(r, seed, p):
    levels = r.levels(p["levels"], p["base"])
    field = octave_field(seed, "case", [(2, 0.5), (4, 0.32), (8, 0.18)])
    t = Tex()
    t.fill_field(field, levels)
    chip_levels = r.levels(p["chip_levels"], p["chip"])
    for k in range(int(p.get("chip_blobs", 4))):
        cx = sp(3) + int(h01(seed, "chipx", k, 0) * 10.0 * R)
        cy = sp(3) + int(h01(seed, "chipy", k, 0) * 10.0 * R)
        paint_blob(t, seed, "chip%d" % k, cx, cy, 0.9 + h01(seed, "chipr", k, 0) * 0.6, chip_levels[1], chip_levels[0])
    for x, y in scatter(seed, "chipk", 0.020):
        t.put(x, y, chip_levels[2])
    bloom = r.resolve(p["bloom"], p["base"])
    rust_levels = r.levels(p["rust_levels"], p["base"])
    for k in range(int(p["bloom_count"])):
        cx = int(h01(seed, "bloomx", k, 0) * W)
        cy = int(h01(seed, "bloomy", k, 0) * W)
        paint_blob(t, seed, "bloom%d" % k, cx, cy, 1.0 + h01(seed, "bloomr", k, 0), bloom, rust_levels[0])
    for x, y in scatter(seed, "rustspk", 0.030):
        t.put(x, y, rust_levels[0])
    draw_frame(t, r.resolve(p["frame"], p["base"]), r.resolve(p["frame_lip"], p["base"]))
    draw_rivets(
        t,
        [(1, 1), (13, 1), (1, 13), (13, 13)],
        r.resolve(p["rivet_lit"], p["base"]),
        r.resolve(p["rivet_mid"], p["base"]),
        r.resolve(p["rivet_dark"], p["base"]),
    )
    return t


def paint_casing_sooted(r, seed, p):
    levels = r.levels(p["levels"], p["base"])
    field = octave_field(seed, "case", [(2, 0.46), (4, 0.34), (8, 0.20)])
    t = Tex()
    t.fill_field(field, levels)
    drip = r.resolve(p["drip"], p["base"])
    drip_lit = r.resolve(p["drip_lit"], p["base"])
    for k in range(int(p["drip_count"])):
        cx = sp(2) + int(h01(seed, "dripx", k, 0) * 12.0 * R)
        start = sp(2) + int(h01(seed, "dripsy", k, 0) * 2.0 * R)
        length = sp(5) + int(h01(seed, "dripl", k, 0) * 8.0 * R)
        stop = min(W - 1 - R, start + length)
        for y in range(start, stop):
            t.put(cx, y, drip)
        t.put(cx, start, drip_lit)
        if h01(seed, "drips", k, 0) < 0.5:
            t.put(cx, stop, drip_lit)
    for k in range(4):
        cy = sp(3) + int(h01(seed, "oilx", k, 0) * 10.0 * R)
        cx = sp(3) + int(h01(seed, "oily", k, 0) * 10.0 * R)
        paint_blob(t, seed, "oil%d" % k, cx, cy, 1.1, levels[-1], levels[-2])
    draw_frame(t, r.resolve(p["frame"], p["base"]), r.resolve(p["frame_lip"], p["base"]))
    draw_rivets(
        t,
        [(1, 1), (13, 1), (1, 13), (13, 13)],
        r.resolve(p["rivet_lit"], p["base"]),
        r.resolve(p["rivet_mid"], p["base"]),
        r.resolve(p["rivet_dark"], p["base"]),
    )
    return t


def paint_casing_porcelain(r, seed, p):
    levels = r.levels(p["levels"], p["base"])
    field = octave_field(seed, "case", [(2, 0.55), (4, 0.45)])
    t = Tex()
    t.fill_field(field, levels)
    crack = r.resolve(p["crack"], p["base"])
    faint = r.resolve(p["crack_faint"], p["base"])
    paint_crack_line(t, seed, "porc", int(p["crack_count"]), (6, 11), crack, halo=None, faint=faint)
    chip = r.resolve(p["chip"], p["base"])
    chip_lit = r.resolve(p["chip_lit"], p["base"])
    paint_blob(t, seed, "chip", sp(3), sp(13), 1.6, chip, chip)
    t.put(sp(2), sp(11), chip_lit)
    t.put(sp(5), sp(12), chip_lit)
    draw_frame(t, r.resolve(p["frame"], p["base"]), r.resolve(p["frame_lip"], p["base"]))
    draw_rivets(
        t,
        [(1, 1), (13, 1), (1, 13), (13, 13)],
        r.resolve(p["rivet_lit"], p["base"]),
        r.resolve(p["rivet_mid"], p["base"]),
        r.resolve(p["rivet_dark"], p["base"]),
    )
    return t


PAINTERS = {
    "grass_tinted": paint_grass_tinted,
    "grass_shattered": paint_grass_shattered,
    "grass_side": paint_grass_side,
    "terrain_grit": paint_terrain_grit,
    "terrain_ripple": paint_terrain_ripple,
    "terrain_fiber": paint_terrain_fiber,
    "terrain_smooth": paint_terrain_smooth,
    "stone_brick": paint_stone_brick,
    "rift_stone": paint_rift_stone,
    "blackstone": paint_blackstone,
    "object_sleeper": paint_object_sleeper,
    "object_pipe": paint_object_pipe,
    "object_plate": paint_object_plate,
    "object_chimney": paint_object_chimney,
    "casing_rusted": paint_casing_rusted,
    "casing_sooted": paint_casing_sooted,
    "casing_porcelain": paint_casing_porcelain,
}

OCTAVES = [(2, 0.46), (4, 0.32), (8, 0.22)]


# --------------------------------------------------------------------------
# 结构断言 / 度量
# --------------------------------------------------------------------------
def _parse_seed(text):
    return int(str(text), 16) if str(text).lower().startswith("0x") else int(text)


def distinct_rgb(im):
    px = im.load()
    seen = set()
    for y in range(im.height):
        for x in range(im.width):
            seen.add(px[x, y][:3])
    return seen


def noise_period_selftest(seed, period=None, fields=None):
    """工具链自证：格点值噪 / 倍频和满足 f(x+P,y)==f(x,y) 与 f(x,y+P)==f(x,y)（精确相等）。

    P10a 反假绿（与 dim7879 同三条）：① P 默认取当前 SIZE 且拒绝 ≠ SIZE 的显式值（把"硬编码 16"
    的回退变成 RED）；② 字段必须变动；③ 半周期必须**不等**（negative control，防空断言）。
    """
    if period is None:
        period = SIZE
    if int(period) != int(SIZE):
        raise AssertionError(
            "噪声自检周期 %d 与画布 SIZE %d 不符：断言未随尺度走（SIZE=%d 下这是假绿）"
            % (int(period), int(SIZE), int(SIZE))
        )
    fields = fields or (octave_field(seed, "selftest", [(2, 0.46), (4, 0.32), (8, 0.22)]),)
    for field in fields:
        if len(set(field(x, y) for x in range(SIZE) for y in range(SIZE))) < 2:
            raise AssertionError("噪声自检字段恒定（周期断言空洞通过）")
        half = max(1, SIZE // 2)
        if all(field(x + half, y) == field(x, y) for x in range(SIZE) for y in range(SIZE)):
            raise AssertionError("半周期即相等（%d）⇒ 周期断言不提供信息" % half)
        for x in range(SIZE):
            for y in range(SIZE):
                if field(x + period, y) != field(x, y) or field(x, y + period) != field(x, y):
                    raise AssertionError("噪声场非周期 %d: (%d,%d)" % (period, x, y))
    return True


def seam_warn_ceiling(style):
    """预览板色标上限（只影响板上高亮配色，不做断言）；某组在 32 档无 tileable 样本时退回母版值。"""
    vals = []
    for name, block in style.items():
        if isinstance(block, dict) and "seam_gross_max" in block:
            key = "seam_gross_max" if R == 1 else "seam_gross_max_x%d" % R
            vals.append(float(block.get(key, block["seam_gross_max"])))
    return max(vals)


def seam_gross_max(style, group):
    """接缝粗检阈值按尺度取表；32 档必须用实测重标键 seam_gross_max_x2，缺键即报错（不沿用 16 档数）。"""
    block = style[group]
    key = "seam_gross_max" if R == 1 else "seam_gross_max_x%d" % R
    if key not in block:
        raise AssertionError(
            "STYLE[%s] 缺 %s：SIZE=%d 的接缝阈值未实测重标（禁止沿用 16 档标定）" % (group, key, SIZE)
        )
    return float(block[key])


def seam_metrics(im):
    """缝感客观量化（地表类）：环边相邻差 vs 内部相邻差。

    - gross = wrap / max(interior)：杀"硬缝"（场断裂/边缘描边）的粗检，阈值见 STYLE.seam_ratio_max；
    - idx  = wrap / mean(interior)：给人看的缝感指数（1.0 = 与内部平均无差别）。
    语义边界：gross 是必要非充分条件；无缝的充分保证来自 noise_period_selftest（场精确周期）
    + 一切笔触经 put() 环绕落笔。
    """
    px = im.load()

    def dist(a, b):
        return max(abs(a[i] - b[i]) for i in range(3))

    col_wrap = sum(dist(px[SIZE - 1, y], px[0, y]) for y in range(SIZE)) / float(SIZE)
    row_wrap = sum(dist(px[x, SIZE - 1], px[x, 0]) for x in range(SIZE)) / float(SIZE)
    col_int = [sum(dist(px[x, y], px[x + 1, y]) for y in range(SIZE)) / float(SIZE) for x in range(SIZE - 1)]
    row_int = [sum(dist(px[x, y], px[x, y + 1]) for x in range(SIZE)) / float(SIZE) for y in range(SIZE - 1)]
    col_mean = sum(col_int) / float(len(col_int))
    row_mean = sum(row_int) / float(len(row_int))
    col_max = max(col_int)
    row_max = max(row_int)
    return {
        "col_wrap": col_wrap,
        "row_wrap": row_wrap,
        "col_idx": col_wrap / max(col_mean, 0.001),
        "row_idx": row_wrap / max(row_mean, 0.001),
        "col_gross": col_wrap / max(col_max, 0.001),
        "row_gross": row_wrap / max(row_max, 0.001),
    }


def ihdr_check(data, expect_size):
    if data[12:16] != b"IHDR":
        raise AssertionError("PNG 缺 IHDR")
    width = int.from_bytes(data[16:20], "big")
    height = int.from_bytes(data[20:24], "big")
    depth = data[24]
    color_type = data[25]
    if (width, height) != expect_size:
        raise AssertionError("IHDR 尺寸 %dx%d != %s" % (width, height, expect_size))
    if depth != 8 or color_type != 6:
        raise AssertionError("IHDR 必须 8bpc RGBA(6)，实为 depth=%d colortype=%d" % (depth, color_type))
    if b"tIME" in data:
        raise AssertionError("PNG 含 tIME 时间戳（非确定性）")
    return width, height, depth, color_type


def _assert_source_clean():
    """AST 扫描本文件：禁 random / time / datetime 的 import 与调用。"""
    source = Path(__file__).read_text(encoding="utf-8")
    tree = ast.parse(source)
    for node in ast.walk(tree):
        if isinstance(node, ast.Import):
            for alias in node.names:
                if alias.name.split(".")[0] in FORBIDDEN_MODULES:
                    raise AssertionError("禁用模块被 import: %s" % alias.name)
        elif isinstance(node, ast.ImportFrom):
            if (node.module or "").split(".")[0] in FORBIDDEN_MODULES:
                raise AssertionError("禁用模块被 from-import: %s" % node.module)
        elif isinstance(node, ast.Attribute):
            if isinstance(node.value, ast.Name) and node.value.id in FORBIDDEN_MODULES:
                raise AssertionError("禁用模块属性访问: %s.%s" % (node.value.id, node.attr))
        elif isinstance(node, ast.Name) and node.id in ("hash",):
            raise AssertionError("禁用内建 hash()（PYTHONHASHSEED 敏感）")


# --------------------------------------------------------------------------
# 构建
# --------------------------------------------------------------------------
def build_all(manifest):
    _assert_source_clean()
    noise_period_selftest(0x5EED5EED)
    rules = Rules(manifest)
    by_key = {entry["key"]: entry for entry in manifest["OUTPUTS"]}
    out = {}
    meta = []
    style = manifest["STYLE"]
    max_colors = int(style["pixel_discipline"]["max_distinct_rgb"])
    seam_warn = seam_warn_ceiling(style)


    for entry in manifest["OUTPUTS"]:
        key = entry["key"]
        params = dict(entry["params"])
        for slot, ref_key in entry.get("params_from", {}).items():
            if ref_key not in by_key:
                raise AssertionError("%s.params_from.%s 指向未知条目 %s" % (key, slot, ref_key))
            params[slot] = by_key[ref_key]["params"]
        seed = _parse_seed(manifest["SEEDS"][key])
        painter = PAINTERS[entry["kind"]]
        tex = painter(rules, seed, params)
        im = tex.image()
        if (im.width, im.height) != (SIZE, SIZE):
            raise AssertionError("%s 尺寸 %s != %dx%d" % (key, im.size, SIZE, SIZE))
        colors = distinct_rgb(im)
        if len(colors) > max_colors:
            raise AssertionError("%s 色数 %d > %d：%s" % (key, len(colors), max_colors, sorted(colors)))
        for rgb in colors:
            if rgb in FORBIDDEN_RGB:
                raise AssertionError("%s 使用极值色 %s" % (key, rgb))
        for y in range(SIZE):
            for x in range(SIZE):
                if im.getpixel((x, y))[3] != 255:
                    raise AssertionError("%s 存在非不透明像素 (%d,%d)" % (key, x, y))
        axes = str(entry.get("seam_axes", "xy"))
        if not set(axes) <= set("xy"):
            raise AssertionError("%s seam_axes 非法: %r" % (key, axes))
        ratio = None
        if entry.get("tileable"):
            ratio = seam_metrics(im)
            seam_max = seam_gross_max(style, entry["group"])
            for axis, gross in (("x", ratio["col_gross"]), ("y", ratio["row_gross"])):
                if axis in axes and gross > seam_max:
                    raise AssertionError(
                        "%s 硬缝超限 %s_gross=%.3f > %.2f（SIZE=%d 阈值集 %s；col=%.3f row=%.3f）"
                        % (key, axis, gross, seam_max, SIZE, "母版" if R == 1 else "x%d" % R,
                           ratio["col_gross"], ratio["row_gross"])
                    )
        out["blocks/" + entry["file"]] = _png_bytes(im)
        meta.append(
            {
                "key": key,
                "file": entry["file"],
                "group": entry["group"],
                "kind": entry["kind"],
                "tileable": bool(entry.get("tileable")),
                "axes": axes,
                "tint": entry.get("tint"),
                "colors": len(colors),
                "seam": ratio,
                "image": im,
            }
        )
    out["preview"] = _png_bytes(build_board(meta, seam_warn))
    return out, meta


def _png_bytes(im):
    buf = io.BytesIO()
    im.save(buf, "PNG", optimize=False)
    return buf.getvalue()


def _nearest(im, factor):
    return im.resize((im.width * factor, im.height * factor), Image.NEAREST)


def _tiled(im, nx, ny):
    out = Image.new("RGBA", (im.width * nx, im.height * ny), (0, 0, 0, 0))
    for i in range(nx):
        for j in range(ny):
            out.paste(im, (i * im.width, j * im.height))
    return out


def build_board(meta, seam_warn=1.5):
    """预览板：17 张分组网格（8x 单张 + 3x3 拼接），带名称/色数/缝感指标。"""
    cell_w, cell_h = 252, 178
    cols = 4
    rows_total = 0
    groups = []
    for group in ("prosperity_terrain", "prosperity_ruin", "shattered"):
        items = [m for m in meta if m["group"] == group]
        rows = (len(items) + cols - 1) // cols
        groups.append((group, items, rows))
        rows_total += rows
    header = 44
    board_w = cols * cell_w + 24
    board_h = header + rows_total * cell_h + 40
    board = Image.new("RGBA", (board_w, board_h), PREVIEW_BG)
    draw = ImageDraw.Draw(board)
    font = ImageFont.load_default()
    draw.text((12, 8), "dim1 S7a block textures (%dx%d) - manifest: tools/artgen/dim1/manifest.json" % (SIZE, SIZE), font=font, fill=PREVIEW_HDR)
    draw.text((12, 22), "left: %dx single / right: tiling %dx (3x3, or 3x1 when seam_axes=x)  |  metrics: distinct RGB / seam ratio(col,row); grass_side = fringe-on-top, x-seam only" % (zoom(8), zoom(2)), font=font, fill=PREVIEW_DIM)
    y_cursor = header
    for group, items, rows in groups:
        draw.text((12, y_cursor + 4), "== %s (%d) ==" % (group, len(items)), font=font, fill=PREVIEW_HDR)
        y_cursor += 20
        for index, item in enumerate(items):
            col = index % cols
            row = index // cols
            x0 = 12 + col * cell_w
            y0 = y_cursor + row * cell_h
            draw.text((x0, y0 + 2), item["file"][:-4], font=font, fill=PREVIEW_FG)
            seam = item["seam"]
            axes = item.get("axes", "xy")
            if item["tileable"] and axes == "xy":
                metric = "rgb=%d  seam idx=%.2f,%.2f gross=%.2f,%.2f" % (
                    item["colors"], seam["col_idx"], seam["row_idx"], seam["col_gross"], seam["row_gross"])
                metric_fill = PREVIEW_FG if max(seam["col_gross"], seam["row_gross"]) <= seam_warn else PREVIEW_WARN
                tile_label = "tile 3x3@%dx" % zoom(2)
                tiled = _nearest(_tiled(item["image"], 3, 3), zoom(2))
            elif item["tileable"] and axes == "x":
                metric = "rgb=%d  seam x-only idx=%.2f gross=%.2f [y=%.2f n/a]" % (
                    item["colors"], seam["col_idx"], seam["col_gross"], seam["row_gross"])
                metric_fill = PREVIEW_FG if seam["col_gross"] <= seam_warn else PREVIEW_WARN
                tile_label = "tile 3x1@%dx (x)" % zoom(2)
                tiled = _nearest(_tiled(item["image"], 3, 1), zoom(2))
            elif item["tileable"]:
                metric = "rgb=%d  seam y-only idx=%.2f gross=%.2f [x=%.2f n/a]" % (
                    item["colors"], seam["row_idx"], seam["row_gross"], seam["col_gross"])
                metric_fill = PREVIEW_FG if seam["row_gross"] <= seam_warn else PREVIEW_WARN
                tile_label = "tile 1x3@%dx (y)" % zoom(2)
                tiled = _nearest(_tiled(item["image"], 1, 3), zoom(2))
            else:
                metric = "rgb=%d  object (no tiling req.)" % item["colors"]
                metric_fill = PREVIEW_DIM
                tile_label = "single @%dx" % zoom(2)
                tiled = _nearest(item["image"], zoom(2))
            draw.text((x0, y0 + 14), metric, font=font, fill=metric_fill)
            board.paste(_nearest(item["image"], zoom(8)), (x0, y0 + 28))
            board.paste(tiled, (x0 + 146, y0 + 44))
            draw.text((x0 + 146, y0 + 30), tile_label, font=font, fill=PREVIEW_DIM)
        y_cursor += rows * cell_h
    return board


# --------------------------------------------------------------------------
# main
# --------------------------------------------------------------------------
def _parse_args(argv):
    """极简 --k=v / --flag 解析（与 dim7879 同法，不引 argparse）。"""
    args = {}
    for token in argv:
        if not token.startswith("--"):
            raise AssertionError("未知参数: %r（用法见文件头）" % token)
        body = token[2:]
        if "=" in body:
            key, value = body.split("=", 1)
        else:
            key, value = body, True
        args[key.replace("-", "_")] = value
    return args


def main(argv=None):
    args = _parse_args(sys.argv[1:] if argv is None else list(argv))
    size = int(args.get("size", BASE_SIZE))
    set_size(size)
    only = args.get("only")
    only = sorted(str(x).strip() for x in str(only).split(",") if x.strip()) if only else None
    manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    if only:
        known = set(e["key"] for e in manifest["OUTPUTS"])
        unknown = sorted(set(only) - known)
        if unknown:
            raise AssertionError("--only 含未知条目 %s（可用名见 manifest.OUTPUTS）" % unknown)
        manifest["OUTPUTS"] = [e for e in manifest["OUTPUTS"] if e["key"] in set(only)]
    first, meta = build_all(manifest)
    second, _ = build_all(manifest)
    for name in first:
        if first[name] != second[name]:
            raise AssertionError("双次生成不一致（非幂等）: " + name)

    out_dir = Path(str(args["out"])) if args.get("out") else ASSET_DIR
    preview_path = Path(str(args["preview"])) if args.get("preview") else PREVIEW_PATH
    landing = not args.get("no_land")
    if size != BASE_SIZE and out_dir.resolve() == ASSET_DIR.resolve() and landing and not args.get("force_land"):
        raise AssertionError(
            "SIZE=%d 直接落地会覆写 src/main/resources 下在产母版贴图（未经用户目检的美术决策）："
            "请加 --out <草稿目录> 或（P10b 授权后）--force-land" % size
        )
    landed = []
    if landing:
        out_dir.mkdir(parents=True, exist_ok=True)
    for entry in manifest["OUTPUTS"]:
        rel = "blocks/" + entry["file"]
        data = first[rel]
        ihdr_check(data, (SIZE, SIZE))
        landed.append((entry["file"], len(data), hashlib.sha256(data).hexdigest()))
        if landing:
            path = out_dir / entry["file"]
            path.write_bytes(data)
            if path.read_bytes() != data:
                raise AssertionError("回读字节不一致: %s" % entry["file"])
    if not args.get("no_preview"):
        preview_path.parent.mkdir(parents=True, exist_ok=True)
        preview_path.write_bytes(first["preview"])
        if preview_path.read_bytes() != first["preview"]:
            raise AssertionError("预览板回读字节不一致")
        with Image.open(io.BytesIO(first["preview"])) as preview_im:
            preview_size = preview_im.size
        ihdr_check(first["preview"], preview_size)

    print("[SIZE] SIZE=%d（母版 %d，倍率 R=%d），条目 %d 张，落地=%s 目录=%s"
          % (SIZE, BASE_SIZE, R, len(manifest["OUTPUTS"]), "开" if landing else "关（草稿档）",
             out_dir if landing else "-"))
    print("[SOURCE] AST 自检：无 random/time/datetime/hash() 依赖")
    print("[PALETTE] 基色锚点（manifest.PALETTE 原值）:")
    for key in sorted(k for k in manifest["PALETTE"] if not k.startswith("_")):
        c = _c(manifest["PALETTE"][key])
        print("  %-20s #%02X%02X%02X" % (key, c[0], c[1], c[2]))

    print("[METRICS] 逐张（色数 / 缝感 idx=环边÷内部均值 与 gross=环边÷内部最大值 / 断言轴 / 拼接要求）:")
    for item in meta:
        if item["tileable"]:
            seam = "idx %.2f,%.2f gross %.2f,%.2f" % (
                item["seam"]["col_idx"], item["seam"]["row_idx"], item["seam"]["col_gross"], item["seam"]["row_gross"])
            axes = "axes=%s" % item.get("axes", "xy")
        else:
            seam = "-"
            axes = "-"
        print("  %-34s rgb=%2d seam=%-28s %-8s tileable=%s" % (
            item["key"], item["colors"], seam, axes, item["tileable"]))
    total = float(SIZE * SIZE)
    print("[TINT] 渲染期草 tint 预测（乘色，Java 常量同步抄录；草侧面为整面乘色，含土体带）:")
    for item in meta:
        if not item.get("tint"):
            continue
        px = item["image"].load()
        print("  %s:" % item["key"])
        for name, hexv in manifest["BIOME_TINT_REFERENCE"].items():
            if name.startswith("_"):
                continue
            tint = _c(hexv)
            acc = [0.0, 0.0, 0.0]
            for y in range(SIZE):
                for x in range(SIZE):
                    for i in range(3):
                        acc[i] += px[x, y][i] * tint[i] / 255.0
            print(
                "    %-16s tint #%s -> mean #%02X%02X%02X"
                % (name, hexv, int(acc[0] / total), int(acc[1] / total), int(acc[2] / total))
            )
    print("[SHA256] 落地资产（assets/gtsr/textures/blocks/）:")
    for name, size, digest in landed:
        print("  %-40s %5d B  %s" % (name, size, digest))
    print("[SHA256] 预览板:")
    print("  %-40s %5d B  %s" % (preview_path.name, len(first["preview"]), hashlib.sha256(first["preview"]).hexdigest()))
    print("[SEAM] 阈值集=%s：%s" % ("母版 16（seam_gross_max）" if R == 1 else "32 实测重标（seam_gross_max_x%d）" % R,
                                    ", ".join("%s=%s" % (g, manifest["STYLE"][g].get(
                                        "seam_gross_max" if R == 1 else "seam_gross_max_x%d" % R))
                                        for g in manifest["STYLE"]
                                        if isinstance(manifest["STYLE"][g], dict) and "seam_gross_max" in manifest["STYLE"][g])))
    asset_digest = _set_digest(["%s %s" % (n, d) for n, _, d in landed])
    preview_digest = hashlib.sha256(first["preview"]).hexdigest()
    print("[SET-SHA256] assets=%s" % asset_digest)
    print("[SET-SHA256] preview=%s" % preview_digest)
    print("[OK] %d 张落地 + 预览板；双跑与回读字节一致。" % len(landed))


def _set_digest(lines):
    payload = "\n".join(sorted(lines)).encode("utf-8")
    return hashlib.sha256(payload).hexdigest()


if __name__ == "__main__":
    try:
        main()
    except AssertionError as exc:
        print("[FAIL] %s" % exc, file=sys.stderr)
        raise SystemExit(1)
