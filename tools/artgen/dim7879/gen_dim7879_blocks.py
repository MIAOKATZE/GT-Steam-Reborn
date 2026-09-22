#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""gen_dim7879_blocks.py — dim78 自然区（17）+ dim79 破碎之地（18）方块贴图确定性生成器。

唯一权威 = tools/artgen/dim7879/manifest.json（PALETTE / DERIVED_RULES / SEEDS / STYLE / OUTPUTS）。
语义来源：plan/investigation/dim78-fix-slice-A1-report.md §1 + dim79-redo-slice-B-report.md §0；
注册名映射：src/main/java/com/miaokatze/gtsr/loader/BlockLoader.java（:70-187 行段）；
技能：gtnh-block-texture-artgen（manifest 单点、LCG 确定性、双跑 SHA256 对拍、预览板目检）。
风格锚：tools/artgen/dim1/gen_dim1_blocks.py 同款原语与像素纪律（本轮为独立域脚本，dim1 文件零改动）。

确定性纪律（本文件自检 + 断言落盘）：
- 零随机源：禁 random / time / datetime（_assert_source_clean() 用 ast 扫描本目录全部 .py 源码）；
  所有伪随机 = splitmix64 混合 + FNV-1a 标签（禁用内建 hash()，其受 PYTHONHASHSEED 影响）。
- 无缝纪律：地表类基场 = 周期 16 的环绕格点值噪（f(x+16,y)==f(x,y) 逐点断言），
  一切落笔经 put() 环绕，故整张图是"可拼接周期图案"；再用边缘/内部相邻差比 (seam ratio) 客观量化缝感。
  一切 % 取模周期（ripple/fiber/strata/course）必须整除 16（assert_period），否则环绕断裂。
- 双跑对拍：build_all() 独立构建两遍逐字节比对；落盘后回读字节核验；PNG IHDR 断言（16x16 / 8bpc / RGBA）。
- 像素纪律：不透明贴图每张 distinct RGB ≤ 16、alpha 恒 255、禁纯黑 #000000 与纯白 #FFFFFF；
  十字装饰（getRenderType=1）alpha 二值（前景 255 / 背景 0），背景像素恒 (0,0,0,0) 且不计入
  distinct RGB 与纯黑禁则，另断言覆盖率区间与"落地生根"（最下一行 ≥N 前景像素）。

产物：
- src/main/resources/assets/gtsr/textures/blocks/ 下 35 张同名覆写（注册名/Java/lang 零改动）；
- plan/新维度计划/review/dim7879/textures/preview.png（预览板：35 张 8x + 拼接 + 色数/缝感指标）；
- tools/artgen/dim7879/landed.sha256（落地 sha 侧车，供后续复核非目标不变）。

用法：python tools/artgen/dim7879/gen_dim7879_blocks.py（任意 cwd；路径相对本文件定位）
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
SIDECAR_PATH = HERE / "landed.sha256"
ASSET_DIR = ROOT / "src" / "main" / "resources" / "assets" / "gtsr" / "textures" / "blocks"
# P17-SB1 改口：旧值指向不存在的 plan/新维度计划/（跑一次即造第二真值源，P17-D §0 既存矛盾第 2 条）。
# 在产 32 档整板的权威产出器 = make_preview32.py（DEFAULT_OUT 同路径）；本生成器主入口若不带
# --no-preview/--preview 会用它自己构建的板覆写同一路径 ⇒ 默认仍不跑主入口，此路径只作真实化兜底。
PREVIEW_PATH = ROOT / "plan" / "维度计划" / "设计册与实施计划" / "review" / "dim7879" / "textures" / "preview.png"

SIZE = 16
BASE_SIZE = 16          # 母版尺度：manifest 与画笔里所有 px 量按 16 标定
W = SIZE
R = 1                   # 线性倍率 = SIZE // BASE_SIZE（16→1，32→2）
M64 = (1 << 64) - 1
TAU = math.pi * 2.0
FORBIDDEN_MODULES = ("random", "time", "datetime")
FORBIDDEN_RGB = ((0, 0, 0), (255, 255, 255))
DIRS = ((1, 0), (0, 1), (-1, 0), (0, -1))
TRANSPARENT = (0, 0, 0, 0)
DEFAULT_OCTAVES = ((4, 0.42), (8, 0.34), (16, 0.24))
CROSS_KINDS = ("cross_decor",)
PREVIEW_BG = (18, 20, 24, 255)
PREVIEW_CHK_A = (34, 37, 42, 255)
PREVIEW_CHK_B = (24, 26, 30, 255)
PREVIEW_FG = (232, 228, 216, 255)
PREVIEW_DIM = (150, 146, 134, 255)
PREVIEW_WARN = (214, 168, 96, 255)
PREVIEW_HDR = (214, 208, 188, 255)


# --------------------------------------------------------------------------
# 尺度参数化（P10a）：SIZE 可在 16 / 32 之间切换，默认 16（= 在产资源母版）
#
# 32 档配方口径（不是最近邻放大；见 plan/investigation/p10a-artgen-32-pipeline-20260920.md）：
#   R1 场波长**不随 R 缩放**：octaves / lattice / patch 的 cells 是"每张贴图的格点数"，
#      保持不变 ⇒ 32 档下每个特征的世界尺寸不变、像素数 ×4（同一构图重绘，而不是加倍特征密度）。
#      取模周期（ripple / fiber / strata / course / drift）同理不动，assert_period 已按 SIZE 判定整除。
#   R2 离散笔刷：长度与斑块半径 ×R（笔刷世界尺寸不变），其**布点密度** ÷R²（单位世界面积的笔刷
#      根数不变）——草叶/纤维属这一类；否则 32 档覆盖率会翻 4 倍，草皮糊成实心。
#   R3 单像素斑点（凹坑/亮片/尘点/锈屑）密度**不变**：覆盖率不变、颗粒在世界口径下变细，
#      这正是高分辨率的收益（crisp），不是糊。
#   R4 1px 发丝线（裂纹主线、描边、受光唇、晕、铆钉芯）**不加倍**；只有轮廓级条带（壳沿厚度、
#      机壳框/铁轨带/法兰/原木树皮环）按 ×R 增厚，否则 32 档读作"贴纸缩小"。
#   R5 按像素计数的门槛随面积走（前景像素下限 ×R²、底行前景下限 ×R）；按比率表达的门槛
#      （覆盖率、色数上限 ≤16、禁极值色）与分辨率无关 ⇒ 不动（色数纪律是分辨率无关的硬约束）。
#   R6 32 档**新增**的开销只花在两处：发丝线保持 1px 带来的锐度、以及代表样试点的附加细节倍频
#      （cells=16 的第四 octave，16 档下会退化成一堆盐粒 ⇒ 只有 32 档吃得下）。
#   倍率 R=1 时以上所有式子恒等于母版表达式 ⇒ 16 档产物与基线逐字节一致（可机检）。
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
    """线性 px 量（行/列号、母版坐标、条带厚度）→ 当前尺度；R=1 恒等。"""
    return int(v) * R if isinstance(v, int) else v * R


def sarea(d):
    """面密度（每像素命中率）→ 当前尺度：R=1 恒等，R=2 时 ÷4（保持单位世界面积的命中数）。"""
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


def _smooth(t):
    return t * t * (3.0 - 2.0 * t)


def tile_noise(seed, tag, cells):
    """周期 16 的格点值噪（格点环绕 → f(x+16,y) == f(x,y) 精确成立；cells 任意整数）。"""
    lat = [[h01(seed, tag + ":lat", i, j) for j in range(cells)] for i in range(cells)]

    def field(x, y):
        u = x * cells / float(SIZE)
        v = y * cells / float(SIZE)
        i0 = int(math.floor(u))
        j0 = int(math.floor(v))
        fx = _smooth(u - i0)
        fy = _smooth(v - j0)
        i0 %= cells
        j0 %= cells
        i1 = (i0 + 1) % cells
        j1 = (j0 + 1) % cells
        top = lat[i0][j0] * (1.0 - fx) + lat[i1][j0] * fx
        bot = lat[i0][j1] * (1.0 - fx) + lat[i1][j1] * fx
        return top * (1.0 - fy) + bot * fy

    return field


def tile_noise_aniso(seed, tag, cells_x, cells_y):
    """各向异性格点值噪（纵向纹理/树皮用；x、y 各自独立 cell 数，仍周期 16）。"""
    lat = [[h01(seed, tag + ":lat", i, j) for j in range(cells_y)] for i in range(cells_x)]

    def field(x, y):
        u = x * cells_x / float(SIZE)
        v = y * cells_y / float(SIZE)
        i0 = int(math.floor(u))
        j0 = int(math.floor(v))
        fx = _smooth(u - i0)
        fy = _smooth(v - j0)
        i0 %= cells_x
        j0 %= cells_y
        i1 = (i0 + 1) % cells_x
        j1 = (j0 + 1) % cells_y
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


def _c(hexstr):
    """'8A7B4A' → RGBA。"""
    h = hexstr.lstrip("#")
    if len(h) == 6:
        h = h + "FF"
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), int(h[6:8], 16))


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
            if token not in self.rules:
                raise AssertionError("未定义派生 token: %r（spec=%r）" % (token, spec))
            toward, t = self.rules[token]
            color = shade(color, self.palette[toward], t)
        return color

    def levels(self, specs, base_key):
        return [self.resolve(spec, base_key) for spec in specs]


class Tex(object):
    """16x16 画布：一切落笔经环绕（mod 16），故任何笔触天然可拼接。"""

    def __init__(self, bg=None):
        self.bg = bg
        self.px = [[bg] * W for _ in range(W)]

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

    def is_bg(self, x, y):
        """仅十字装饰用：越界视为背景（不作环绕读，防跨边误判）。"""
        return self.get(x, y, wrap=False) == self.bg

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
def assert_period(period):
    """% 取模周期必须整除 16，否则环绕断裂（格点值噪不受此限）。"""
    period = int(period)
    if period < 1 or SIZE % period != 0:
        raise AssertionError("周期 %d 不整除 %d（环绕会断裂）" % (period, SIZE))
    return period


def octaves_of(params):
    raw = params.get("octaves", DEFAULT_OCTAVES)
    return [(int(cells), float(weight)) for cells, weight in raw]


def pick_level(levels, value):
    """归一化场值 → 阶色（各列表长度可不同：accent 3 阶与基色 4 阶共存时各按自身长度取阶）。"""
    n = len(levels)
    idx = int(value * n)
    return levels[0 if idx < 0 else (n - 1 if idx >= n else idx)]


def scatter(seed, tag, density):
    """确定性命中表：返回 [(x, y)]，命中率 ≈ density（逐像素独立 → 无方向偏好）。"""
    out = []
    for y in range(W):
        for x in range(W):
            if h01(seed, tag, x, y) < density:
                out.append((x, y))
    return out


def patch_field(seed, tag, cells, threshold):
    """低频斑块掩码（周期 16），用于泥斑/炭斑等成片结构。"""
    field = tile_noise(seed, tag, cells)
    return [[field(x, y) > threshold for x in range(W)] for y in range(W)]


def lattice_ids(seed, tag, cells):
    """周期 16 的 jittered 格点最近邻归属图（棱面 / 龟裂网用；cells 必须整除 16）。

    jitter 幅度 = ±0.5 格（点可越入邻格）→ 多边形不规则。**纪律**：jitter 若只给 ±0.4 格
    （点不越格界），实测退化为规则栅格，读作"网罩"而非碎裂岩石/龟裂（首轮目检实证），故保持越界抖动。
    """
    assert_period(cells)
    step = SIZE // cells
    pts = []
    for j in range(cells):
        for i in range(cells):
            jx = i * step + step * 0.5 + (h01(seed, tag + ":jx", i, j) - 0.5) * step
            jy = j * step + step * 0.5 + (h01(seed, tag + ":jy", i, j) - 0.5) * step
            pts.append((jx, jy))
    ids = [[0] * W for _ in range(W)]
    for y in range(W):
        for x in range(W):
            best = None
            best_id = 0
            for k, (px, py) in enumerate(pts):
                dx = abs(x + 0.5 - px)
                dx = min(dx, SIZE - dx)
                dy = abs(y + 0.5 - py)
                dy = min(dy, SIZE - dy)
                dist = dx * dx + dy * dy
                if best is None or dist < best:
                    best = dist
                    best_id = k
            ids[y][x] = best_id
    return ids


def cell_tones(seed, tag, cells):
    """每格一个色调偏移 ∈ {-1, 0, +1}（棱面/碎片逐面异调 → 硬边分面可读；规则同色会读作同质网格）。"""
    out = []
    for j in range(cells):
        for i in range(cells):
            roll = h01(seed, tag + ":tone", i, j)
            out.append(-1 if roll < 0.33 else (0 if roll < 0.66 else 1))
    return out


def face_boundary(ids, x, y):
    """(x,y) 的右/下邻居是否属于不同格 → 棱面/裂缝边界。"""
    return ids[y][x] != ids[y][(x + 1) % W] or ids[y][x] != ids[(y + 1) % W][x]


def paint_blob(t, seed, tag, cx, cy, rad, core, edge):
    """不规则斑块（锈斑/炭斑）：半径内按距离衰减 + 哈希腐蚀边缘 → 拒绝方块感。

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


def paint_crack_line(t, seed, tag, count, length, core, halo=None, halo_axis="v", faint=None):
    """细裂纹（1px 主线 + 可选单侧晕 + 可选断续浅纹）；先铺全部晕、再压主线（防晕吃线）。

    count 与 length 的母版口径：count = 每张图上的条数（世界密度随面积自然变稀），
    length 按 ×R 走以保持单条裂纹的世界长度；主线仍是 1px 发丝线（R4）。
    """
    length = (sp(length[0]), sp(length[1]))
    paths = []

    for k in range(count):
        x = int(h01(seed, tag + ":x", k, 0) * W)
        y = int(h01(seed, tag + ":y", k, 0) * W)
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


def drift_bias(period, strength):
    """周期%取模的风积/层理偏置（周期必须整除 16）。"""
    p = assert_period(period)

    def bias(x, y, value):
        wave = 0.5 + 0.5 * math.cos(TAU * (y % p) / float(p))
        return value + strength * 0.4 * (wave - 0.5)

    return bias


# --------------------------------------------------------------------------
# 各 kind 的画法
# --------------------------------------------------------------------------
def paint_grass_baked(r, seed, p):
    """烘焙草皮（dim78 steppe/forest top）：方块不 tint → 草色写进贴图。"""
    levels = r.levels(p["levels"], p["base"])
    field = octave_field(seed, "mot", octaves_of(p))
    t = Tex()
    t.fill_field(field, levels)
    n = len(levels)
    if p.get("accent_levels"):
        accent_levels = r.levels(p["accent_levels"], p["accent"])
        accent_blend = float(p.get("accent_blend", 0.0))
        blend = octave_field(seed, "acc", [(4, 0.6), (8, 0.4)])
        for y in range(W):
            for x in range(W):
                mix = blend(x, y) * 0.55 + accent_blend * 0.45
                if h01(seed, "acck", x, y) < mix:
                    t.put(x, y, pick_level(accent_levels, field(x, y)))
    blade_dark = r.resolve(p["blade_dark"], p["base"])
    blade_lit = r.resolve(p["blade_lit"], p["base"])
    for x, y in scatter(seed, "blade", sarea(p["blade_density"])):
        length = sp(1 + int(h01(seed, "bladel", x, y) * 3.0))
        for i in range(length):
            t.put(x, y - i, blade_dark)
        if h01(seed, "bladek", x, y) < 0.40:
            t.put(x, y - length + 1, blade_lit)

    speckle = r.resolve(p["speckle"], p["base"])
    for x, y in scatter(seed, "speck", p["speckle_density"]):
        t.put(x, y, speckle)
    if p.get("blotch"):
        blotch = r.resolve(p["blotch"], p["base"])
        for k in range(int(p.get("blotch_count", 0))):
            cx = int(h01(seed, "blotchx", k, 0) * W)
            cy = int(h01(seed, "blotchy", k, 0) * W)
            paint_blob(t, seed, "blotch%d" % k, cx, cy, 1.2 + h01(seed, "blotchr", k, 0), blotch, speckle)
    return t


def paint_terrain_grit(r, seed, p):
    """粗颗粒土/石（可选 strata 层理：周期整除 16 + 可选通缝线/受光唇）。"""
    levels = r.levels(p["levels"], p["base"])
    field = octave_field(seed, "mot", octaves_of(p))
    bias = None
    joint = None
    if p.get("strata_period"):
        bias = drift_bias(p["strata_period"], float(p.get("strata_strength", 0.0)))
        if p.get("strata_joint"):
            joint = (
                r.resolve(p["strata_joint"], p["base"]),
                r.resolve(p["strata_lip"], p["base"]) if p.get("strata_lip") else None,
            )
    t = Tex()
    t.fill_field(field, levels, bias=bias)
    if joint is not None:
        period = int(p["strata_period"])
        for y in range(W):
            if y % period == 0:
                for x in range(W):
                    t.put(x, y, joint[0])
            elif joint[1] is not None and y % period == 1:
                for x in range(W):
                    t.put(x, y, joint[1])
    grit_dark = r.resolve(p["grit_dark"], p["base"])
    grit_lit = r.resolve(p["grit_lit"], p["base"])
    for x, y in scatter(seed, "grit", p["grit_density"]):
        t.put(x, y, grit_dark if h01(seed, "gritk", x, y) < 0.62 else grit_lit)
    if p.get("clod"):
        clods = patch_field(seed, "clod", 4, 0.74)
        clod = r.resolve(p["clod"], p["base"])
        for y in range(W):
            for x in range(W):
                if clods[y][x] and h01(seed, "clodk", x, y) < 0.55:
                    t.put(x, y, clod)
    if p.get("flake"):
        flake = r.resolve(p["flake"], p["base"])
        for x, y in scatter(seed, "flake", p["flake_density"]):
            t.put(x, y, flake)
    return t


def paint_terrain_ripple(r, seed, p):
    levels = r.levels(p["levels"], p["base"])
    field = octave_field(seed, "mot", octaves_of(p))
    t = Tex()
    t.fill_field(field, levels, bias=drift_bias(p["ripple_period"], float(p["ripple_strength"])))
    grit_dark = r.resolve(p["grit_dark"], p["base"])
    grit_lit = r.resolve(p["grit_lit"], p["base"])
    for x, y in scatter(seed, "grit", p["grit_density"]):
        t.put(x, y, grit_dark if h01(seed, "gritk", x, y) < 0.55 else grit_lit)
    if p.get("glint"):
        glint = r.resolve(p["glint"], p["base"])
        for x, y in scatter(seed, "glint", p["glint_density"]):
            t.put(x, y, glint)
    return t


def paint_terrain_fiber(r, seed, p):
    levels = r.levels(p["levels"], p["base"])
    field = octave_field(seed, "mot", octaves_of(p))
    t = Tex()
    t.fill_field(field, levels, bias=drift_bias(p["fiber_row_period"], 0.25))
    if p.get("muck"):
        muck = r.resolve(p["muck"], p["base"])
        mucks = patch_field(seed, "muck", 4, 0.78)
        for y in range(W):
            for x in range(W):
                if mucks[y][x] and h01(seed, "muckk", x, y) < 0.7:
                    t.put(x, y, muck)
    fiber_dark = r.resolve(p["fiber_dark"], p["base"])
    fiber_lit = r.resolve(p["fiber_lit"], p["base"])
    for x, y in scatter(seed, "fib", sarea(p["fiber_density"])):
        dash = sp(1 + int(h01(seed, "fibl", x, y) * 3.0))
        color = fiber_dark if h01(seed, "fibk", x, y) < 0.6 else fiber_lit
        for i in range(dash):
            t.put(x + i, y, color)

    if p.get("seep"):
        seep = r.resolve(p["seep"], p["base"])
        for x, y in scatter(seed, "seep", p["seep_density"]):
            t.put(x, y, seep)
    return t


def paint_crust_side(r, seed, p):
    """表层侧面（kind=crust_side）：土体 + 顶部 2-4px 壳沿（色彩复用 manifest.params_from）。

    - 土体：复用 paint_terrain_grit(同族 base 的 params, 异种子) → 与同族 base 贴图同画法不同排列；
    - 壳沿：逐列高度 h ∈ [fringe_min, fringe_max]（周期 16 场取阶上色；带 accent_levels 的
      变体按 paint_grass_baked 同款混色规则叠 accent），沿口下缘压 blade_dark、顶缘偶受光
      blade_lit、沿内偶见 edge_speckle，沿下 1px 用土体深阶投影、偶有 1px 垂落；
    - 无缝：一切落笔经 put() 环绕 + 周期 16 场 → 水平（x）自拼接；垂直为设计不连续（壳沿在上），
      由 manifest entry.seam_axes 声明，断言只在 x 轴做。
    """
    body = p["body"]
    fringe = p["fringe"]
    t = paint_terrain_grit(r, seed, body)

    levels = r.levels(fringe["levels"], fringe["base"])
    n = len(levels)
    blade_dark = r.resolve(fringe["blade_dark"], fringe["base"]) if fringe.get("blade_dark") else None
    blade_lit = r.resolve(fringe["blade_lit"], fringe["base"]) if fringe.get("blade_lit") else None
    accent_levels = r.levels(fringe["accent_levels"], fringe["accent"]) if fringe.get("accent_levels") else None
    accent_blend = float(fringe.get("accent_blend", 0.0))
    fringe_min = sp(p["fringe_min"])          # 壳沿厚度：轮廓级条带 ⇒ ×R（R4）
    fringe_max = sp(p["fringe_max"])

    hang = float(p.get("hang_density", 0.0))
    speckle = r.resolve(p["edge_speckle"], fringe["base"]) if p.get("edge_speckle") else None
    shadow = r.resolve(p["shadow"], body["base"]) if p.get("shadow") else None

    field = octave_field(seed, "gras", [(4, 0.44), (8, 0.34), (16, 0.22)])
    blend = octave_field(seed, "gacc", [(4, 0.6), (8, 0.4)]) if accent_levels else None
    heights = []
    for x in range(W):
        h = fringe_min + int(h01(seed, "fh", x, 0) * (fringe_max - fringe_min + 1))
        h = max(fringe_min, min(fringe_max, h))
        heights.append(h)
        for y in range(h):
            color = pick_level(levels, field(x, y))
            if accent_levels is not None:
                mix = blend(x, y) * 0.55 + accent_blend * 0.45
                if h01(seed, "gacck", x, y) < mix:
                    color = pick_level(accent_levels, field(x, y))
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


def paint_stone_facet(r, seed, p):
    """棱面硬石（碎核岩 / 渣壳）：格点最近邻分面（每面异调）+ 1px 深缝 + 受光棱边 + 矿脉 + 凹坑。"""
    levels = r.levels(p["levels"], p["base"])
    n = len(levels)
    field = octave_field(seed, "mot", octaves_of(p))
    cells = int(p["facet_cells"])
    ids = lattice_ids(seed, "facet", cells)
    tones = cell_tones(seed, "facet", cells)
    t = Tex()
    for y in range(W):
        for x in range(W):
            idx = int(field(x, y) * n) + tones[ids[y][x]]
            idx = 0 if idx < 0 else (n - 1 if idx >= n else idx)
            t.put(x, y, levels[idx])
    joint = r.resolve(p["joint"], p["base"])
    facet_lit = r.resolve(p["facet_lit"], p["base"])
    for y in range(W):
        for x in range(W):
            if face_boundary(ids, x, y):
                t.put(x, y, joint)
            elif face_boundary(ids, x - 1, y) or face_boundary(ids, x, y - 1):
                t.put(x, y, facet_lit)
    if p.get("vein"):
        paint_crack_line(
            t,
            seed,
            "vein",
            int(p["vein_count"]),
            (4, 9),
            r.resolve(p["vein"], p["base"]),
            halo=r.resolve(p["vein_lit"], p["base"]),
            halo_axis="v",
        )
    if p.get("pit"):
        pit = r.resolve(p["pit"], p["base"])
        for x, y in scatter(seed, "pit", p["pit_density"]):
            t.put(x, y, pit)
    return t


def paint_terrain_slag(r, seed, p):
    """渣壳：与 stone_facet 同技法（角面 + 深缝 + 受光棱 + 熔蚀凹坑），仅参数与色系不同。"""
    return paint_stone_facet(r, seed, p)


def paint_stone_banded(r, seed, p):
    """砌筑石（monolith 平台块）：周期皮通缝 + 受光唇 + 凿痕 + 应力裂纹。"""
    levels = r.levels(p["levels"], p["base"])
    field = octave_field(seed, "mot", octaves_of(p))
    t = Tex()
    t.fill_field(field, levels)
    period = assert_period(p["course_period"])
    joint = r.resolve(p["course_joint"], p["base"])
    lit = r.resolve(p["course_lit"], p["base"])
    for y in range(W):
        if y % period == 0:
            for x in range(W):
                t.put(x, y, joint)
        elif y % period == 1:
            for x in range(W):
                t.put(x, y, lit)
    if p.get("chisel"):
        chisel = r.resolve(p["chisel"], p["base"])
        for x, y in scatter(seed, "chisel", p["chisel_density"]):
            t.put(x, y, chisel)
    if p.get("pit"):
        pit = r.resolve(p["pit"], p["base"])
        for x, y in scatter(seed, "pit", p["pit_density"]):
            t.put(x, y, pit)
    paint_crack_line(t, seed, "crack", int(p["crack_count"]), (3, 7), r.resolve(p["crack"], p["base"]))
    return t


def paint_terrain_ash(r, seed, p):
    """灰烬表壳：细灰粒 + 风积纹 + 炭渣点/炭斑 + 极稀疏余烬亮点。"""
    levels = r.levels(p["levels"], p["base"])
    field = octave_field(seed, "mot", octaves_of(p))
    t = Tex()
    t.fill_field(field, levels, bias=drift_bias(p["drift_period"], float(p["drift_strength"])))
    char = r.resolve(p["char"], p["base"])
    for k in range(int(p["char_blobs"])):
        cx = int(h01(seed, "chx", k, 0) * W)
        cy = int(h01(seed, "chy", k, 0) * W)
        paint_blob(t, seed, "char%d" % k, cx, cy, 1.0 + h01(seed, "chr", k, 0), char, char)
    for x, y in scatter(seed, "chark", p["char_density"]):
        t.put(x, y, char)
    if p.get("glint"):
        glint = r.resolve(p["glint"], p["base"])
        for x, y in scatter(seed, "glint", p["glint_density"]):
            t.put(x, y, glint)
    return t


def paint_terrain_vitreous(r, seed, p):
    """琉璃表壳：光滑基场 + 贝壳状裂纹（暗线 + 亮唇）+ 玻璃反光点与晕。"""
    levels = r.levels(p["levels"], p["base"])
    field = octave_field(seed, "mot", octaves_of(p))
    t = Tex()
    t.fill_field(field, levels)
    paint_crack_line(
        t,
        seed,
        "crack",
        int(p["crack_count"]),
        (5, 9),
        r.resolve(p["crack_dark"], p["base"]),
        halo=r.resolve(p["crack_lit"], p["base"]),
        halo_axis="h",
    )
    glint = r.resolve(p["glint"], p["base"])
    halo = r.resolve(p["glint_halo"], p["base"])
    for x, y in scatter(seed, "glint", p["glint_density"]):
        t.put(x, y, glint)
        t.put(x, y + 1, halo)
    return t


def paint_terrain_tar(r, seed, p):
    """焦油壳：龟裂网（暗缝）+ 缝节点沥青反光 = 近黑但有光泽层次。"""
    levels = r.levels(p["levels"], p["base"])
    field = octave_field(seed, "mot", octaves_of(p))
    t = Tex()
    t.fill_field(field, levels)
    ids = lattice_ids(seed, "tar", p["crack_net_cells"])
    crack = r.resolve(p["crack"], p["base"])
    gloss = r.resolve(p["gloss"], p["base"])
    node = r.resolve(p["gloss_node"], p["base"])
    for y in range(W):
        for x in range(W):
            if face_boundary(ids, x, y):
                t.put(x, y, crack)
            elif face_boundary(ids, x - 1, y):
                if h01(seed, "glossx", x, y) < p["gloss_density"]:
                    t.put(x, y, gloss)
            elif face_boundary(ids, x, y - 1):
                if h01(seed, "glossy", x, y) < p["gloss_density"]:
                    t.put(x, y, node)
    return t


def paint_log_side(r, seed, p):
    """树干侧面：各向异性纵向纹理 + 纵向棱脊 + 锈斑/炭缝 + 环带（周期整除 16）。"""
    levels = r.levels(["BASE@D070", "BASE", "BASE@L090"], p["base"])
    field = tile_noise_aniso(seed, "bark", int(p["grain_cells_x"]), int(p["grain_cells_y"]))
    t = Tex()
    t.fill_field(field, levels)
    bark_dark = r.resolve(p["bark_dark"], p["base"])
    bark_lit = r.resolve(p["bark_lit"], p["base"])
    for x in range(W):
        if h01(seed, "ridge", x, 0) < float(p["ridge_chance"]) / R:   # 逐列判定 ⇒ ÷R 保持世界棱脊根数

            for y in range(W):
                t.put(x, y, bark_dark)
                if h01(seed, "ridgelit", x, y) < 0.55:
                    t.put(x + 1, y, bark_lit)
    if p.get("seam"):
        seam = r.resolve(p["seam"], p["base"])
        for x, y in scatter(seed, "seam", p["seam_density"]):
            t.put(x, y, seam)
    if p.get("dust"):
        dust = r.resolve(p["dust"], p["base"])
        for x, y in scatter(seed, "dust", p["dust_density"]):
            t.put(x, y, dust)
    if p.get("band"):
        band = r.resolve(p["band"], p["base"])
        for row in p["band_rows"]:
            for x in range(W):
                t.put(x, int(row), band)
    if p.get("bloom"):
        bloom = r.resolve(p["bloom"], p["base"])
        for k in range(int(p["bloom_count"])):
            cx = int(h01(seed, "bloomx", k, 0) * W)
            cy = int(h01(seed, "bloomy", k, 0) * W)
            paint_blob(t, seed, "bloom%d" % k, cx, cy, 1.0 + h01(seed, "bloomr", k, 0), bloom, bloom)
        for x, y in scatter(seed, "bloomk", p["bloom_density"]):
            t.put(x, y, bloom)
    return t


def paint_log_top(r, seed, p):
    """树干横截面：同心年轮（ring_period px 取阶）+ 外圈树皮环 + 凹坑 + 放射裂纹。

    逐块各自成环 = 不做拼接断言（vanilla 原木顶面同款口径，manifest.tileable=false）。
    """
    base = p["base"]
    core = r.resolve(p["core"], base)
    alt = r.resolve(p["ring_alt"], base)
    lit = r.resolve(p["ring_lit"], base)
    rim1 = r.resolve(p["bark_rim_1"], base)
    rim2 = r.resolve(p["bark_rim_2"], base)
    t = Tex()
    period = float(p["ring_period"]) * R          # 年轮间距 ×R ⇒ 环数与世界口径一致（R2）
    mid = W / 2.0 - 0.5                          # 母版 7.5 = 16/2-0.5；32 档为 15.5

    def ridx(x, y):
        return int(math.hypot(x - mid, y - mid) / period)

    for y in range(W):
        for x in range(W):
            i = ridx(x, y)
            t.put(x, y, core if i % 2 == 0 else alt)
    for y in range(W):
        for x in range(W):
            i = ridx(x, y)
            # 环界受光画在**外圈一侧**（半径更大者）→ 每圈一道外缘亮线，读作年轮（只标左/上会读成断裂弧）
            if (
                (ridx((x + 1) % W, y) > i and ridx((x + 1) % W, y) != i)
                or (ridx(x, (y + 1) % W) > i and ridx(x, (y + 1) % W) != i)
                or (ridx((x - 1) % W, y) > i and ridx((x - 1) % W, y) != i)
                or (ridx(x, (y - 1) % W) > i and ridx(x, (y - 1) % W) != i)
            ):
                if i % 2 == 1:
                    t.put(x, y, lit)
    for k in range(R):                             # 树皮环带厚度 ×R（轮廓级条带 R4）
        for x in range(W):
            t.put(x, k, rim2)
            t.put(x, W - 1 - k, rim1)
        for y in range(W):
            t.put(k, y, rim2)
            t.put(W - 1 - k, y, rim1)
    pit = r.resolve(p["pit"], base)
    for x, y in scatter(seed, "pit", p["pit_density"]):
        t.put(x, y, pit)
    crack = r.resolve(p["crack"], base)
    for k in range(int(p["crack_count"])):
        angle = h01(seed, "ang", k, 0) * TAU
        length = sp(4 + int(h01(seed, "len", k, 0) * 4.0))
        for step in range(length):
            rad = (1.0 + step) * R
            x = int(round(mid + math.cos(angle) * rad))
            y = int(round(mid + math.sin(angle) * rad))
            t.put(x, y, crack)

    return t


def paint_leaves_clump(r, seed, p):
    """树叶冠层：细碎簇场 + 暗孔 + 亮针尖 + 稀疏锈斑（乘色底，见 manifest.tint）。"""
    levels = r.levels(p["levels"], p["base"])
    field = octave_field(seed, "mot", octaves_of(p))
    t = Tex()
    t.fill_field(field, levels)
    hole = r.resolve(p["hole"], p["base"])
    for x, y in scatter(seed, "hole", p["hole_density"]):
        t.put(x, y, hole)
    needle = r.resolve(p["needle_lit"], p["base"])
    for x, y in scatter(seed, "needle", p["needle_density"]):
        t.put(x, y, needle)
        if h01(seed, "needlel", x, y) < 0.4:
            t.put(x, y + 1, needle)
    if p.get("oxide"):
        oxide = r.resolve(p["oxide"], p["base"])
        for x, y in scatter(seed, "oxide", p["oxide_density"]):
            t.put(x, y, oxide)
    return t


def paint_cross_decor(r, seed, p):
    """十字装饰（getRenderType=1）：透明底 + 生根落地 + 形态由 form 分派。

    形态（manifest.params.form）：
    - dry_tuft   疏高干草叶（4-7px，有弯折）+ 穗头 + 锈屑——dim78 锈草丛
    - moss_tuft  密矮苔簇（3-5px，部分 2px 宽）+ 铜绿霜点——dim78 铜绿草丛
    - stone_spike 4 根锥形硬刺（根宽 2-5px 收至 1px）+ 尖顶反光 + 底部碎屑——dim79 石刺簇
    - thorn      5 根细斜枝（1px、斜率 0.5）+ 交错倒刺 + 节瘤 + 灰白枯梢——dim79 灰烬棘丛
    """
    form = p["form"]
    base = p["base"]
    dark = r.resolve(p["blade_dark"], base)
    mid = r.resolve(p["blade_mid"], base)
    lit = r.resolve(p["blade_lit"], base)
    t = Tex(bg=TRANSPARENT)
    fg = {form: 0}

    def put_fg(x, y, color):
        if 0 <= x < W and 0 <= y < W:
            t.put(x, y, color, wrap=False)
            fg[form] += 1

    if form in ("dry_tuft", "moss_tuft"):
        if form == "dry_tuft":
            head = r.resolve(p["head"], base)
        else:
            bloom = r.resolve(p["bloom"], base)
        count = int(p["blade_count"])
        for k in range(count):
            # 槽位散布：k*16/count + 抖动 → 叶片不扎堆（纯均匀随机实测会撞列，底部糊成实心块）
            x0 = int(k * W / float(count) + h01(seed, "bx", k, 0) * (W / float(count)))
            x0 = max(0, min(W - 1, x0))
            height = sp(int(p["height_min"])) + int(
                h01(seed, "bh", k, 0) * (sp(int(p["height_max"])) - sp(int(p["height_min"])) + 1)
            )
            if form == "dry_tuft":
                lean = (-1, 0, 0, 1)[int(h01(seed, "bl", k, 0) * 4.0) % 4]
                wide = False
            else:
                lean = (0, 0, -1, 1)[int(h01(seed, "bl", k, 0) * 4.0) % 4]
                wide = h01(seed, "bw", k, 0) < 0.4
            x = x0
            for i in range(height):
                y = W - 1 - i
                if i >= 2 * R and i % (3 * R) == 0:        # 弯折节距 ×R（保持世界弯折频率）
                    x += lean

                color = mid
                if i == 0:
                    color = dark
                elif i == height - 1 and h01(seed, "bt", k, 0) < 0.55:
                    color = lit
                for dx in range(R):          # 草叶是"自立笔刷"而非描边 ⇒ 宽度 ×R（R3：否则 32 档覆盖率掉一半，读作铁丝）
                    put_fg(x + dx, y, color)
                if wide and i < height - 1:
                    for dx in range(R):      # 密苔簇的加宽列紧贴主叶（母版 x+1 ⇒ 尺度 x+R..x+2R-1）
                        put_fg(x + R + dx, y, lit if i == height - 2 else mid)

            if form == "dry_tuft":
                if h01(seed, "bhead", k, 0) < p["head_chance"]:
                    put_fg(x, W - height - 1, head)
                    if h01(seed, "bhead2", k, 0) < 0.5:
                        put_fg(x + 1, W - height, head)
            else:
                if h01(seed, "bhead", k, 0) < p["head_chance"]:
                    put_fg(x, W - height - 1, lit)
        scatter_tag = "oxide" if form == "dry_tuft" else "bloom"
        scatter_density = p["oxide_density"] if form == "dry_tuft" else p["bloom_density"]
        speck = r.resolve(p["oxide"] if form == "dry_tuft" else p["bloom"], base)
        for x, y in scatter(seed, scatter_tag, scatter_density):
            if not t.is_bg(x, y):
                t.put(x, y, speck, wrap=False)
    elif form == "stone_spike":
        tip = r.resolve(p["tip"], base)
        for k in range(int(p["spike_count"])):
            cx = sp(2) + int(h01(seed, "sx", k, 0) * 12 * R)
            height = sp(int(p["height_min"])) + int(
                h01(seed, "sh", k, 0) * (sp(int(p["height_max"])) - sp(int(p["height_min"])) + 1)
            )
            half = sp(1 + int(h01(seed, "sw", k, 0) * 1.99))
            lean = (-1, 0, 0, 1)[int(h01(seed, "sl", k, 0) * 4.0) % 4]
            for i in range(height):
                y = W - 1 - i
                # 等腰三角收束：基宽 2*half → 顶 1px（线性收束 + 顶端强制 1px = 尖，非方柱）
                width = max(1, int(round(2.0 * half * (height - i) / float(height))))
                xoff = int(round(lean * (i / float(height)) * 2.0 * R))

                left = cx - (width - 1) // 2 + xoff
                for j in range(width):
                    x = left + j
                    if width == 1:
                        color = mid
                    elif j == 0:
                        color = dark
                    elif j == width - 1:
                        color = lit
                    else:
                        color = mid
                    put_fg(x, y, color)
            if h01(seed, "stip", k, 0) < p["tip_chance"]:
                put_fg(cx + int(round(lean * 2.0 * R)), W - height - 1, tip)
        for k in range(int(p["chip_count"])):
            cx = sp(1) + int(h01(seed, "cx", k, 0) * 14 * R)
            for dx in range(R):
                put_fg(cx + dx, W - 1, dark)
                if h01(seed, "cy", k, 0) < 0.5:
                    for dy in range(R):
                        put_fg(cx + dx, W - 1 - R + dy, mid)

    elif form == "thorn":
        node = r.resolve(p["node"], base)
        tip = r.resolve(p["tip"], base)
        count = int(p["twig_count"])
        for k in range(count):
            x0 = 1 + int(k * (W - 3) / float(max(1, count - 1)))
            x0 += int((h01(seed, "tx", k, 0) - 0.5) * 2.0 * R)
            x0 = max(0, min(W - 1, x0))
            height = sp(int(p["height_min"])) + int(
                h01(seed, "th", k, 0) * (sp(int(p["height_max"])) - sp(int(p["height_min"])) + 1)
            )
            direction = 1 if h01(seed, "td", k, 0) < 0.5 else -1
            x = x0
            for i in range(height):
                y = W - 1 - i
                if i > 0 and i % (2 * R) == 0:            # 斜率 0.5 的世界口径不变
                    x += direction
                color = mid
                if i == 0:
                    color = node
                elif i == height - 1 and h01(seed, "tt", k, 0) < p["tip_chance"]:
                    color = tip
                for dx in range(R):          # 棘枝同属自立笔刷 ⇒ 宽度 ×R（覆盖率随尺度守恒）
                    put_fg(x + dx, y, color)
                if i >= 2 * R and h01(seed, "tb", k, i) < p["barb_chance"]:

                    color_b = dark
                    if h01(seed, "tbk", k, i) < 0.3:
                        color_b = node
                    put_fg(x - direction, y, color_b)
                    if h01(seed, "tb2", k, i) < p["barb_chance"] * 0.5:
                        put_fg(x + direction, y + 1, dark)
            if h01(seed, "tbase", k, 0) < 0.5:
                put_fg(x0 + direction, W - 1, node)
    elif form == "flower":
        # P17-SB1 自有花族形（16 档母版腿：6 茎槽位三态——全花头 42% / 花蕾 30% / 纯茎 28%；
        # dark/mid/lit 复用族参数 blade_*（花瓣暗/中/亮），center=花心、stem=茎、head_count=茎数。
        # 32 档在产权威 = draw32_dim78.flower()（自绘 + 生根足 + 基叶一对，本腿只保证 manifest 全量可再生）。
        center = r.resolve(p["center"], base)
        stem_col = r.resolve(p["stem"], base)
        n_stems = int(p["head_count"])
        for k in range(n_stems):
            x = int((k + 0.30 + h01(seed, "fx", k, 0) * 0.40) * W / float(n_stems)) % W
            role = h01(seed, "fr", k, 0)
            h = (7 + int(h01(seed, "fh", k, 0) * 3.0)) if role < 0.42 else \
                (5 + int(h01(seed, "fh", k, 0) * 2.0))
            lean = (-1, 0, 0, 1)[int(h01(seed, "fl", k, 0) * 4.0) % 4]
            put_fg(x, W - 1, stem_col)
            cx = x
            for i in range(1, sp(h)):
                y = W - 1 - i
                if i % (2 * R) == 0:
                    cx += lean
                put_fg(cx, y, stem_col)
            hy = W - 1 - sp(h)
            if role < 0.42:                        # 全花头：心 + 4 正瓣（左上受光）+ 4 角半瓣
                put_fg(cx, hy, center)
                for dx, dy in ((0, -1), (0, 1), (-1, 0), (1, 0)):
                    put_fg(cx + dx * R, hy + dy * R, lit if dx + dy < 0 else mid)
                for dx, dy in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
                    if h01(seed, "fc", k, dx * 3 + dy) < 0.7:
                        put_fg(cx + dx * R, hy + dy * R, dark)
            elif role < 0.72:                      # 花蕾
                put_fg(cx, hy, mid)
                put_fg(cx, hy - R, dark)
    else:
        raise AssertionError("未知十字形态: %s" % form)
    if fg[form] < 8 * R * R:            # 前景像素下限随面积走（R6）；R=1 恒等于母版阈值 8
        raise AssertionError("十字形态 %s 前景像素过少: %d" % (form, fg[form]))
    return t


PAINTERS = {
    "grass_baked": paint_grass_baked,
    "terrain_grit": paint_terrain_grit,
    "terrain_ripple": paint_terrain_ripple,
    "terrain_fiber": paint_terrain_fiber,
    "terrain_ash": paint_terrain_ash,
    "terrain_vitreous": paint_terrain_vitreous,
    "terrain_tar": paint_terrain_tar,
    "terrain_slag": paint_terrain_slag,
    "stone_facet": paint_stone_facet,
    "stone_banded": paint_stone_banded,
    "crust_side": paint_crust_side,
    "cross_decor": paint_cross_decor,
    "log_side": paint_log_side,
    "log_top": paint_log_top,
    "leaves_clump": paint_leaves_clump,
}


# --------------------------------------------------------------------------
# 结构断言 / 度量
# --------------------------------------------------------------------------
def _parse_seed(text):
    return int(str(text), 16) if str(text).lower().startswith("0x") else int(text)


def distinct_rgb(im, skip_transparent=False):
    px = im.load()
    seen = set()
    for y in range(im.height):
        for x in range(im.width):
            p = px[x, y]
            if skip_transparent and p[3] == 0:
                continue
            seen.add(p[:3])
    return seen


def noise_period_selftest(seed, period=None, fields=None):
    """工具链自证：格点值噪 / 倍频和 / 各向异性变体满足 f(x+P,y)==f(x,y) 与 f(x,y+P)==f(x,y)。

    P10a 反假绿改造（三条，缺一条这条自检就只是装饰）：
      ① 周期 P 默认取 **当前 SIZE**，且显式传入 ≠ SIZE 的 P 直接报错——把历史版本的"硬编码 16"
         回退成 RED 而不是"跑通"（SIZE=32 时 f(x+16,y)==f(x,y) 会实测失败）；
      ② 非恒等性：字段必须是变动的（恒定场会让所有 == 断言空洞通过）；
      ③ 反证（negative control）：f(x+P/2,y) 必须**确实不等**，否则"周期 P"这个断言不提供任何
         信息（例如某处把环绕写成 f(x)==f(x) 的自反式，就属于这种假绿）。
    """
    if period is None:
        period = SIZE
    if int(period) != int(SIZE):
        raise AssertionError(
            "噪声自检周期 %d 与画布 SIZE %d 不符：断言未随尺度走（SIZE=%d 下这是假绿）"
            % (int(period), int(SIZE), int(SIZE))
        )
    if fields is None:
        fields = (
            octave_field(seed, "selftest", DEFAULT_OCTAVES),
            tile_noise_aniso(seed, "selftest_aniso", 4, 16),
        )
    for field in fields:
        values = set()
        for x in range(SIZE):
            for y in range(SIZE):
                values.add(field(x, y))
        if len(values) < 2:
            raise AssertionError("噪声自检字段恒定（周期断言空洞通过）")
        half = max(1, SIZE // 2)
        if all(field(x + half, y) == field(x, y) for x in range(SIZE) for y in range(SIZE)):
            raise AssertionError("半周期即相等（%d）⇒ 周期断言不提供信息" % half)
        for x in range(SIZE):
            for y in range(SIZE):
                if field(x + period, y) != field(x, y) or field(x, y + period) != field(x, y):
                    raise AssertionError("噪声场非周期 %d: (%d,%d)" % (period, x, y))
    return True


def seam_metrics(im):
    """缝感客观量化（地表类）：环边相邻差 vs 内部相邻差。

    - gross = wrap / max(interior)：杀"硬缝"（场断裂/边缘描边）的粗检，阈值见 STYLE.seam_gross_max；
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
    return {
        "col_wrap": col_wrap,
        "row_wrap": row_wrap,
        "col_idx": col_wrap / max(col_mean, 0.001),
        "row_idx": row_wrap / max(row_mean, 0.001),
        "col_gross": col_wrap / max(max(col_int), 0.001),
        "row_gross": row_wrap / max(max(row_int), 0.001),
    }


def seam_gross_max(style, group):
    """接缝粗检阈值按尺度取表（P10a 实测重标，不与 16 档共用一个数）。

    - SIZE=16：STYLE[group]["seam_gross_max"]（母版标定，逐字不改）；
    - SIZE=32：STYLE[group]["seam_gross_max_x2"]（32 档 gross 分布实测重标值，见判据 3）；
    缺键直接报错——绝不静默沿用 16 档阈值（那等于把断言换成另一套量纲的数还宣称全绿）。
    """
    block = style[group]
    key = "seam_gross_max" if R == 1 else "seam_gross_max_x%d" % R
    if key not in block:
        raise AssertionError(
            "STYLE[%s] 缺 %s：SIZE=%d 的接缝阈值未实测重标（禁止沿用 16 档标定）" % (group, key, SIZE)
        )
    return float(block[key])


def seam_warn_ceiling(style):
    """预览板色标上限（只影响板上的高亮/警示配色，不做断言）。

    某组在 32 档没有 tileable 样本时退回母版值并照样出图——断言用的阈值仍走 seam_gross_max()
    的硬门（缺键必报错），这里不让"展示口径"反过来逼我为空组编造阈值。
    """
    vals = []
    for name, block in style.items():
        if isinstance(block, dict) and "seam_gross_max" in block:
            key = "seam_gross_max" if R == 1 else "seam_gross_max_x%d" % R
            vals.append(float(block.get(key, block["seam_gross_max"])))
    return max(vals)


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
    """AST 扫描本目录全部 .py：禁 random / time / datetime 的 import 与调用 + 禁内建 hash()。"""
    for path in sorted(HERE.glob("*.py")):
        tree = ast.parse(path.read_text(encoding="utf-8"))
        for node in ast.walk(tree):
            if isinstance(node, ast.Import):
                for alias in node.names:
                    if alias.name.split(".")[0] in FORBIDDEN_MODULES:
                        raise AssertionError("%s 禁用模块被 import: %s" % (path.name, alias.name))
            elif isinstance(node, ast.ImportFrom):
                if (node.module or "").split(".")[0] in FORBIDDEN_MODULES:
                    raise AssertionError("%s 禁用模块被 from-import: %s" % (path.name, node.module))
            elif isinstance(node, ast.Attribute):
                if isinstance(node.value, ast.Name) and node.value.id in FORBIDDEN_MODULES:
                    raise AssertionError("%s 禁用模块属性访问: %s.%s" % (path.name, node.value.id, node.attr))
            elif isinstance(node, ast.Name) and node.id in ("hash",):
                raise AssertionError("%s 禁用内建 hash()（PYTHONHASHSEED 敏感）" % path.name)


# --------------------------------------------------------------------------
# 构建
# --------------------------------------------------------------------------
def _resolve_params(entry, by_key, style):
    """entry.params 展开：注入所属 group 的 octaves；params_from 引用既有条目（含其 group octaves）。"""
    params = dict(entry["params"])
    _inject_octaves(entry, params, style)
    for slot, ref_key in entry.get("params_from", {}).items():
        if ref_key not in by_key:
            raise AssertionError("%s.params_from.%s 指向未知条目 %s" % (entry["key"], slot, ref_key))
        ref = by_key[ref_key]
        ref_params = dict(ref["params"])
        _inject_octaves(ref, ref_params, style)
        params[slot] = ref_params
    return params


def _inject_octaves(entry, params, style):
    if "octaves" in params:
        return
    block = style.get(entry["group"])
    if isinstance(block, dict) and "octaves" in block:
        params["octaves"] = block["octaves"]


def _iter_color_specs(node):
    """递归收集 params 里所有带 '@' 的色值串（非色值串如 form/octaves 不含 '@' → 天然跳过）。"""
    if isinstance(node, str):
        if "@" in node:
            yield node
    elif isinstance(node, dict):
        for value in node.values():
            for spec in _iter_color_specs(value):
                yield spec
    elif isinstance(node, list):
        for value in node:
            for spec in _iter_color_specs(value):
                yield spec


def _assert_manifest_tokens(manifest):
    """manifest lint：每个 '@' 色值的 token 必须在 DERIVED_RULES 里、名字必须在 PALETTE 或为 6 位 hex。

    这道门是必须的：token 拼写错误在绘制期才炸，且会以 KeyError 而非断言形式出现（错误消息不指向
    manifest 行），故前置成结构化 lint。
    """
    rules = set(k for k in manifest["DERIVED_RULES"] if not k.startswith("_"))
    palette = set(k for k in manifest["PALETTE"] if not k.startswith("_"))
    bad_tokens = set()
    bad_names = set()
    for entry in manifest["OUTPUTS"]:
        for spec in _iter_color_specs(entry.get("params", {})):
            name, token = spec.split("@", 1)
            if token not in rules:
                bad_tokens.add(token)
            if name != "BASE" and name not in palette and not _is_hex6(name):
                bad_names.add(name)
    if bad_tokens:
        raise AssertionError("manifest 引用未定义派生 token: %s" % sorted(bad_tokens))
    if bad_names:
        raise AssertionError("manifest 引用未定义基色锚点: %s" % sorted(bad_names))
    return True


def _is_hex6(text):
    if len(text) != 6:
        return False
    return all(ch in "0123456789abcdefABCDEF" for ch in text)


def build_all(manifest):
    _assert_source_clean()
    _assert_manifest_tokens(manifest)
    noise_period_selftest(0x5EED5EED)
    rules = Rules(manifest)
    style = manifest["STYLE"]
    by_key = {entry["key"]: entry for entry in manifest["OUTPUTS"]}
    discipline = style["pixel_discipline"]
    max_colors = int(discipline["max_distinct_rgb"])
    forbid = tuple(_c(x)[:3] for x in discipline["forbid_rgb"])
    cross_rules = style["cross_rules"]
    out = {}
    meta = []
    for entry in manifest["OUTPUTS"]:
        key = entry["key"]
        kind = entry["kind"]
        seed = _parse_seed(manifest["SEEDS"][key])
        params = _resolve_params(entry, by_key, style)
        try:
            tex = PAINTERS[kind](rules, seed, params)
        except (AssertionError, KeyError, IndexError, TypeError, ValueError) as exc:
            raise AssertionError("绘制失败 %s (kind=%s, seed=%s): %r" % (key, kind, manifest["SEEDS"][key], exc))
        im = tex.image()
        if (im.width, im.height) != (SIZE, SIZE):
            raise AssertionError("%s 尺寸 %s != %dx%d" % (key, im.size, SIZE, SIZE))

        is_cross = kind in CROSS_KINDS
        px = im.load()
        if is_cross:
            if entry.get("alpha") != "binary":
                raise AssertionError("%s 十字装饰必须声明 alpha=binary" % key)
            fg_count = 0
            bg_count = 0
            for y in range(SIZE):
                for x in range(SIZE):
                    p = px[x, y]
                    if p[3] == 0:
                        if p != TRANSPARENT:
                            raise AssertionError("%s 背景像素非 (0,0,0,0): (%d,%d)=%s" % (key, x, y, p))
                        bg_count += 1
                    elif p[3] == 255:
                        fg_count += 1
                    else:
                        raise AssertionError("%s alpha 非二值: (%d,%d)=%d" % (key, x, y, p[3]))
            coverage = fg_count / float(SIZE * SIZE)
            bottom = sum(1 for x in range(SIZE) if px[x, SIZE - 1][3] == 255)
            if coverage < float(cross_rules["coverage_min"]) or coverage > float(cross_rules["coverage_max"]):
                raise AssertionError(
                    "%s 覆盖率 %.3f 越界 [%s,%s]" % (key, coverage, cross_rules["coverage_min"], cross_rules["coverage_max"])
                )
            if bottom < int(cross_rules["bottom_row_fg_min"]) * R:  # 底行前景下限是"逐列计数"⇒ ×R（R6）
                raise AssertionError(
                    "%s 未落地生根：最下一行前景 %d < %d"
                    % (key, bottom, int(cross_rules["bottom_row_fg_min"]) * R)
                )
            if bg_count == 0:
                raise AssertionError("%s 无透明背景" % key)
            extra = {"coverage": coverage, "bottom_fg": bottom}
        else:
            for y in range(SIZE):
                for x in range(SIZE):
                    if px[x, y][3] != 255:
                        raise AssertionError("%s 存在非不透明像素 (%d,%d)" % (key, x, y))
            extra = {}
        colors = distinct_rgb(im, skip_transparent=is_cross)
        if len(colors) > max_colors:
            raise AssertionError("%s 色数 %d > %d：%s" % (key, len(colors), max_colors, sorted(colors)))
        for rgb in colors:
            if rgb in forbid:
                raise AssertionError("%s 使用极值色 %s" % (key, rgb))
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
                "kind": kind,
                "tileable": bool(entry.get("tileable")),
                "axes": axes,
                "tint": entry.get("tint"),
                "colors": len(colors),
                "seam": ratio,
                "image": im,
                "extra": extra,
                "cross": is_cross,
            }
        )
    out["preview"] = _png_bytes(build_board(meta, manifest))
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


def _checker(w, h, a, b, block=8):
    im = Image.new("RGBA", (w, h), a)
    p = im.load()
    for y in range(h):
        for x in range(w):
            if ((x // block) + (y // block)) % 2 == 0:
                p[x, y] = b
    return im


def group_order(style):
    return [name for name, block in style.items() if isinstance(block, dict) and "seam_gross_max" in block]


def _apply_tint(im, tint):
    """渲染期乘色模拟（草/叶 colorMultiplier：逐通道 tex*tint/255，alpha 保持）。"""
    out = Image.new("RGBA", im.size, (0, 0, 0, 0))
    src = im.load()
    dst = out.load()
    for y in range(im.height):
        for x in range(im.width):
            p = src[x, y]
            if p[3] == 0:
                continue
            dst[x, y] = (
                min(255, p[0] * tint[0] // 255),
                min(255, p[1] * tint[1] // 255),
                min(255, p[2] * tint[2] // 255),
                p[3],
            )
    return out


def build_board(meta, manifest, cols=4):
    """预览板：按 manifest.STYLE 分组顺序排网格（8x 单张 + 拼接），带色数/缝感/覆盖率指标；
    末尾追加 in-game 口径行（乘色模拟：贴图 × 群系草色，4x）——tint 消费件必须给游戏内口径预览。"""
    style = manifest["STYLE"]
    biome_tints = [(k, v) for k, v in manifest["BIOME_TINT_REFERENCE"].items() if not k.startswith("_")]
    seam_warn = seam_warn_ceiling(style)

    cell_w, cell_h = 252, 178
    groups = []
    rows_total = 0
    for name in group_order(style):
        items = [m for m in meta if m["group"] == name]
        if not items:
            continue
        rows = (len(items) + cols - 1) // cols
        groups.append((name, items, rows))
        rows_total += rows
    tinted_items = [m for m in meta if m.get("tint")]
    header = 46
    board_w = cols * cell_w + 24
    board_h = header + rows_total * cell_h + (20 + len(tinted_items) * cell_h if tinted_items else 0) + 60
    board = Image.new("RGBA", (board_w, board_h), PREVIEW_BG)
    draw = ImageDraw.Draw(board)
    font = ImageFont.load_default()
    draw.text(
        (12, 8),
        "dim78 natural + dim79 shattered block textures (%dx%d) - manifest: tools/artgen/dim7879/manifest.json"
        % (SIZE, SIZE),
        font=font,
        fill=PREVIEW_HDR,
    )
    draw.text(
        (12, 22),
        "left: %dx single (cross kinds over checker = transparent bg) / right: tiling %dx (3x3, or 3x1 when seam_axes=x)  |  metrics: distinct RGB / seam ratio(gross) / cross coverage"
        % (zoom(8), zoom(2)),
        font=font,
        fill=PREVIEW_DIM,
    )
    draw.text(
        (12, 34),
        "dim78 = rust / patina / brass / peat (4 biome reads)   dim79 = ash / slag / vitreous / tar over dark corestone (hard-dark family)",
        font=font,
        fill=PREVIEW_DIM,
    )
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
            axes = item["axes"]
            if item["tileable"] and axes == "xy":
                metric = "rgb=%d  seam idx=%.2f,%.2f gross=%.2f,%.2f" % (
                    item["colors"],
                    seam["col_idx"],
                    seam["row_idx"],
                    seam["col_gross"],
                    seam["row_gross"],
                )
                metric_fill = PREVIEW_FG if max(seam["col_gross"], seam["row_gross"]) <= seam_warn else PREVIEW_WARN
                tile_label = "tile 3x3@%dx" % zoom(2)
                tiled = _nearest(_tiled(item["image"], 3, 3), zoom(2))
                single = _nearest(item["image"], zoom(8))
            elif item["tileable"] and axes == "x":
                metric = "rgb=%d  seam x-only gross=%.2f [y=%.2f n/a]" % (
                    item["colors"],
                    seam["col_gross"],
                    seam["row_gross"],
                )
                metric_fill = PREVIEW_FG if seam["col_gross"] <= seam_warn else PREVIEW_WARN
                tile_label = "tile 3x1@%dx (x)" % zoom(2)
                tiled = _nearest(_tiled(item["image"], 3, 1), zoom(2))
                single = _nearest(item["image"], zoom(8))
            else:
                extra = item["extra"]
                if item["cross"]:
                    metric = "rgb=%d  cross alpha=binary cov=%.2f root=%d" % (
                        item["colors"],
                        extra["coverage"],
                        extra["bottom_fg"],
                    )
                    single = _checker(W * zoom(8), W * zoom(8), PREVIEW_CHK_A, PREVIEW_CHK_B)
                    single.alpha_composite(_nearest(item["image"], zoom(8)))
                    tile_label = "single @%dx (alpha)" % zoom(2)
                    tiled = _checker(W * zoom(2), W * zoom(2), PREVIEW_CHK_A, PREVIEW_CHK_B)
                    tiled.alpha_composite(_nearest(item["image"], zoom(2)))
                else:
                    metric = "rgb=%d  object (no tiling req.)" % item["colors"]
                    single = _nearest(item["image"], zoom(8))
                    tile_label = "single @%dx" % zoom(2)
                    tiled = _nearest(item["image"], zoom(2))
                metric_fill = PREVIEW_DIM
            draw.text((x0, y0 + 14), metric, font=font, fill=metric_fill)
            board.paste(single, (x0, y0 + 28))
            board.paste(tiled, (x0 + 146, y0 + 44))
            draw.text((x0 + 146, y0 + 30), tile_label, font=font, fill=PREVIEW_DIM)
        y_cursor += rows * cell_h
    tinted = tinted_items
    if tinted:
        draw.text(
            (12, y_cursor + 4),
            "== in-game caliber: texture x biome grass color (colorMultiplier simulate, 4x) ==",
            font=font,
            fill=PREVIEW_HDR,
        )
        y_cursor += 20
        for index, item in enumerate(tinted):
            for j, (name, hexv) in enumerate(biome_tints):
                x0 = 12 + j * cell_w
                y0 = y_cursor + index * cell_h
                tint = _c(hexv)
                draw.text(
                    (x0, y0 + 2),
                    "%s x %s (#%s)" % (item["key"], name, hexv),
                    font=font,
                    fill=PREVIEW_FG,
                )
                tinted_im = _apply_tint(item["image"], tint)
                if item["cross"]:
                    strip = _checker(W * zoom(4), W * zoom(4), PREVIEW_CHK_A, PREVIEW_CHK_B)
                    strip.alpha_composite(_nearest(tinted_im, zoom(4)))
                else:
                    strip = _nearest(tinted_im, zoom(4))
                board.paste(strip, (x0, y0 + 16))
                if item["cross"]:
                    draw.text((x0, y0 + 82), "alpha bg = checker", font=font, fill=PREVIEW_DIM)
        y_cursor += len(tinted) * cell_h
    return board


# --------------------------------------------------------------------------
# main
# --------------------------------------------------------------------------
def _parse_args(argv):
    """极简 --k=v / --flag 解析（不引 argparse，保持本文件零额外依赖面）。"""
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
    if landing and not args.get("no_sidecar") and out_dir.resolve() == ASSET_DIR.resolve() and not only:
        SIDECAR_PATH.write_text(
            "\n".join(sorted("%s %s" % (n, d) for n, _, d in landed)) + "\n", encoding="utf-8"
        )

    print("[SIZE] SIZE=%d（母版 %d，倍率 R=%d），条目 %d 张，落地=%s 目录=%s"
          % (SIZE, BASE_SIZE, R, len(manifest["OUTPUTS"]), "开" if landing else "关（草稿档）",
             out_dir if landing else "-"))
    print("[SOURCE] AST 自检：%d 个 .py 无 random/time/datetime/hash() 依赖" % len(list(HERE.glob("*.py"))))
    print("[PALETTE] 基色锚点（manifest.PALETTE 原值）:")
    for key in sorted(k for k in manifest["PALETTE"] if not k.startswith("_")):
        c = _c(manifest["PALETTE"][key])
        print("  %-18s #%02X%02X%02X" % (key, c[0], c[1], c[2]))
    print("[METRICS] 逐张（色数 / 缝感 gross=环边÷内部最大值 / 断言轴 / alpha 档 / 拼接要求）:")
    max_colors = int(manifest["STYLE"]["pixel_discipline"]["max_distinct_rgb"])
    for item in meta:
        if item["tileable"]:
            seam = "gross %.2f,%.2f" % (item["seam"]["col_gross"], item["seam"]["row_gross"])
            axes = "axes=%s" % item["axes"]
        else:
            seam = "-"
            axes = "-"
        if item["cross"]:
            alpha = "alpha=binary cov=%.2f root=%d" % (item["extra"]["coverage"], item["extra"]["bottom_fg"])
        else:
            alpha = "alpha=255"
        near = "  <== 色数贴近上限" if item["colors"] >= max_colors - 1 else ""
        print(
            "  %-34s rgb=%2d seam=%-16s %-8s %-32s tileable=%s%s"
            % (item["key"], item["colors"], seam, axes, alpha, item["tileable"], near)
        )
    total = float(SIZE * SIZE)
    tinted = [item for item in meta if item.get("tint")]
    if tinted:
        print("[TINT] 渲染期草 tint 预测（乘色，Java 常量同步抄录；仅 tuft/leaves 有 colorMultiplier 消费端）:")
        for item in tinted:
            px = item["image"].load()
            fg = [(x, y) for y in range(SIZE) for x in range(SIZE) if px[x, y][3] != 0]
            print("  %s:  前景像素 %d/%d（乘色均值只按前景统计——含透明底会把均值拖到近黑，首轮曾误报）" % (item["key"], len(fg), SIZE * SIZE))
            for name, hexv in manifest["BIOME_TINT_REFERENCE"].items():
                if name.startswith("_"):
                    continue
                tint = _c(hexv)
                acc = [0.0, 0.0, 0.0]
                for x, y in fg:
                    for i in range(3):
                        acc[i] += px[x, y][i] * tint[i] / 255.0
                print(
                    "    %-16s tint #%s -> mean #%02X%02X%02X"
                    % (name, hexv, int(acc[0] / len(fg)), int(acc[1] / len(fg)), int(acc[2] / len(fg)))
                )
    print("[SHA256] 落地资产（assets/gtsr/textures/blocks/）:")
    for name, size, digest in landed:
        print("  %-40s %5d B  %s" % (name, size, digest))
    print("[SHA256] 预览板:")
    print(
        "  %-40s %5d B  %s"
        % (preview_path.name, len(first["preview"]), hashlib.sha256(first["preview"]).hexdigest())
    )
    print("[SEAM] 阈值集=%s：%s" % ("母版 16（seam_gross_max）" if R == 1 else "32 实测重标（seam_gross_max_x%d）" % R,
                                    ", ".join("%s=%s" % (g, manifest["STYLE"][g].get(
                                        "seam_gross_max" if R == 1 else "seam_gross_max_x%d" % R))
                                        for g in group_order(manifest["STYLE"]))))
    print("[SET-SHA256] assets=%s" % _set_digest(["%s %s" % (n, d) for n, _, d in landed]))
    print("[SET-SHA256] preview=%s" % hashlib.sha256(first["preview"]).hexdigest())
    print("[OK] %d 张（%s）+ 预览板；双跑字节一致。"
          % (len(landed), "落地 %s" % out_dir if landing else "未落地（--no-land 草稿档）"))


def _set_digest(lines):
    payload = "\n".join(sorted(lines)).encode("utf-8")
    return hashlib.sha256(payload).hexdigest()


if __name__ == "__main__":
    try:
        main()
    except AssertionError as exc:
        print("[FAIL] %s" % exc, file=sys.stderr)
        raise SystemExit(1)
