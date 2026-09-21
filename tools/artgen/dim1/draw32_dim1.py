#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""draw32_dim1.py — 片 A2（原文件名 p3-a2-detail.py，P16-A4 由 plan/tmp 回写进仓）：dim1 14 张方块贴图 16x16 → 32x32 等比重绘 + 磨损细节补层（design-preserving redraw）。

链路与纪律
----------
1) 设计源 = 既有权威生成器 tools/artgen/dim1/gen_dim1_blocks.py（manifest 单点、零硬编码色值）。
   其 R1..R5 尺度参数化在 SIZE=32 下按"同等判断重画"：场波长不缩（构图/部件位置不变）、
   轮廓级条带（草沿/铁轨/法兰/砖皮/铆钉）×R、1px 发丝线（描边/裂纹主线/机壳框/分模线）不加倍、
   单像素斑点密度不变（世界口径颗粒变细）。本脚本**不修改生成器与 manifest**（本片写锁只含 14 个 .png），
   只做确定性的后处理细节层。
2) 细节层（本片新增，唯一权威 = 本文件 DETAIL 表，密度以千分比集中书写）：只在既有细节
   （锈斑/纤维/划痕/滴痕/麻点/灰缝/铆钉/反光点）的邻位生长，不新造部件；色值一律由该张
   manifest 角色名经 Rules.resolve 取得，禁 hex 字面量 ⇒ 色板轴零新增。
3) 硬断言：32x32 正方形 / RGBA8 IHDR；alpha 恒 255；distinct RGB ≤ 16；禁纯黑纯白；
   用色 ⊆（该张已 approve 的 16 档用色 ∪ 该条 manifest 声明的角色阶梯）；已 approve 的每一个用色档
   在 32 档仍在场（明暗层次不塌陷）；骨架（非基材档）像素只允许被显式申报的 on_top 磨损稀疏压过；
   地表类无缝 gross ≤ manifest STYLE.seam_gross_max_x2；细节层双跑逐字节一致；落盘回读一致。
4) 基线：改前 14 张字节已在 plan/tmp/p3-before-baseline/；本脚本核验 SIZE=16 生成 == 基线字节
   （证明"生成器 = 被 approve 资产的设计源"，故 32 档重绘不是换设计）。

逐张 why 的仓内档案 = 同目录 design32.json（文字逐字取自本文件 DETAIL 表的行内注释）。
注意：BASE_DIR 的 SIZE=16 基线核验依赖 plan/tmp/p3-before-baseline/（回滚快照，不入库）；该快照被清理后
本脚本的基线核验会报缺文件，属预期，不代表 gen_dim1_blocks.py 或 manifest.json 失效（两者本轮零改动）。

用法：python tools/artgen/dim1/draw32_dim1.py            # 生成 + 断言 + 预览板 + 落地
       python tools/artgen/dim1/draw32_dim1.py --draft   # 只到草稿目录，不写 src/main/resources
"""
import hashlib
import importlib.util
import json
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[3]   # P16-A4 回写仓内：plan/tmp 副本里这里是绝对路径
GEN = ROOT / "tools/artgen/dim1/gen_dim1_blocks.py"
MANIFEST_PATH = GEN.parent / "manifest.json"
ASSET_DIR = ROOT / "src/main/resources/assets/gtsr/textures/blocks"
BASE_DIR = ROOT / "plan/tmp/p3-before-baseline"
DRAFT_DIR = ROOT / "plan/tmp/p3-a2-land"
REVIEW_DIR = ROOT / "plan/tmp/p3-a2-review"

spec = importlib.util.spec_from_file_location("g1", str(GEN))
g1 = importlib.util.module_from_spec(spec)
sys.modules["g1"] = g1
spec.loader.exec_module(g1)

MANIFEST = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
ENTRIES = {e["key"]: e for e in MANIFEST["OUTPUTS"]}
RULES = g1.Rules(MANIFEST)
SALT = "a2wear"
R_SCALE = 2  # 本片的尺度倍率（16 -> 32）

# --------------------------------------------------------------------------
# 细节清单（单一权威；千分比 = 每 1000 个锚点像素命中数）
#   op = (动作, 锚点角色, 落笔角色, 千分比, 选项)
#   选项 onto=角色名  → 允许稀疏压在该骨架色上（=申报过的磨损），budget 千分比为其占比上限
# --------------------------------------------------------------------------
DETAIL = {
    "prosperity_surface_rust_grass": [
        ("neighbor", "speckle", "levels[0]", 480, {"dy": 1}),                      # 锈点下缘压暗
        ("neighbor", "blade_lit", "blade_lit", 380, {"dx": -1}),                   # 草尖受光加宽一格
        ("neighbor", "blade_dark", "blade_dark", 280, {"dx": 1, "dy": -1}),        # 草叶斜生
        ("neighbor", "blotch", "speckle", 360, {"dxdy": 1}),                       # 锈块边缘洒锈屑
    ],
    "prosperity_surface_rust_grass_side": [
        ("neighbor", "speckle", "blade_dark", 400, {"dy": 1}),                      # 沿内锈屑下缘压暗
        ("neighbor", "grit_lit", "grit_lit", 280, {"dx": 1}),                      # 土体亮砂短线
        ("neighbor", "flake", "flake", 360, {"dxdy": 1}),                          # 锈屑团块
        ("neighbor", "shadow", "blade_dark", 240, {"dy": 1}),                      # 垂落草茎加深
        ("neighbor", "blade_dark", "speckle", 260, {"dy": -1}),                    # 沿口背光处洒锈屑
    ],
    "prosperity_surface_rust_dirt": [
        ("neighbor", "grit_dark", "grit_dark", 360, {"dx": 1}),                    # 粗砂连成短线
        ("neighbor", "grit_lit", "grit_lit", 260, {"dx": -1}),
        ("neighbor", "flake", "flake", 400, {"dxdy": 1}),                          # 锈屑聚成小片
        ("neighbor", "clod", "levels[0]", 300, {"dy": 1}),                         # 土块下缘压暗
    ],
    "prosperity_surface_rust_sand": [
        ("neighbor", "grit_dark", "grit_dark", 280, {"dx": 1}),                    # 风向短纹
        ("neighbor", "grit_lit", "grit_lit", 220, {"dx": 1}),
        ("ladder_step", "levels", 300, {"min_step": 1}),                           # 波纹肩补过渡档
        ("grain_flat", "levels", 120, {}),                                         # 稀疏补粒（风向已有短纹）
    ],
    "prosperity_surface_rust_peat": [
        ("neighbor", "fiber_dark", "fiber_dark", 340, {"dx": 1}),                  # 纤维加长
        ("neighbor", "fiber_lit", "levels[3]", 360, {"dx": -1}),                   # 补回 L035 高光档
        ("neighbor", "muck", "levels[0]", 260, {"dy": 1}),                         # 泥斑下缘压暗
    ],
    "prosperity_surface_rust_clay": [
        ("neighbor", "sheen", "sheen", 360, {"dx": -1}),                           # 釉面反光加宽
        ("neighbor", "sheen", "levels[4]", 320, {"dx": 1}),                        # 反光亮侧补一档
        ("ladder_step", "levels", 420, {"min_step": 1}),                           # 明暗边界补过渡档（去带状平坦）
        ("grain_flat", "levels", 340, {}),                                         # 孤立平区补一粒（16 档画不出的细粒）
    ],
    "prosperity_surface_rust_stone": [
        ("neighbor", "pit", "pit", 360, {"dx": 1}),                                # 麻点连成小坑
        ("neighbor", "rust_levels[0]", "rust_levels[0]", 240, {"dy": 1}),          # 锈斑下缘垂流
        ("below_lip", "joint_lip", "brick_levels[0]", 140, {"dy": 2}),             # 砖面沿缝压暗（落砖面第一行）
        ("neighbor", "rust_levels[2]", "rust_levels[1]", 260, {"dy": 1}),          # 亮锈接暗锈
    ],
    "ruin_debris_sleeper": [
        ("neighbor", "grain", "grain", 300, {"dx": 1}),                            # 木纹加长
        ("neighbor", "plank_seam", "grain", 200, {"dx": -1}),                      # 板缝毛刺
        ("neighbor", "spike", "groove", 250, {"dy": 1}),                           # 道钉落影
        ("neighbor", "rail_lit", "rail_shadow", 60, {"onto": "rail_lit", "budget": 130}),  # 轨面磨痕
        ("ladder_step", "wood_levels", 200, {"min_step": 1}),                       # 木面明暗软肩
    ],
    "ruin_debris_pipe": [
        ("neighbor", "pit", "pit", 360, {"dx": 1}),                                # 凹坑连片
        ("neighbor", "rust_levels[1]", "pit", 200, {"dxdy": 1}),                   # 锈斑边缘起坑
        ("neighbor", "rust_levels[0]", "rust_levels[0]", 180, {"dy": 1}),          # 锈水下淌
        ("neighbor", "flange", "flange_edge", 50, {"onto": "flange", "budget": 70}),   # 法兰缺角
        ("neighbor", "rivet_lit", "rivet_dark", 400, {"dx": 1, "dy": 1}),          # 铆钉落影
        ("ladder_step", "pipe_levels", 260, {"min_step": 1}),                       # 管身受光带下缘软肩
    ],
    "ruin_debris_rivet_plate": [
        ("neighbor", "scratch", "scratch", 120, {"dy": 1}),                        # 划痕分叉
        ("neighbor", "rust_levels[0]", "rust_levels[0]", 260, {"dxdy": 1}),        # 锈斑边缘生长
        ("neighbor", "rust_levels[2]", "rust_levels[1]", 240, {"dy": 1}),
        ("neighbor", "bevel_lit", "plate_levels[0]", 60, {"onto": "bevel_lit", "budget": 80}),  # 受光棱磕伤
        ("rivet_shadow", "rivet_sites", "rivet_dark", {}),                         # 四角铆钉斜向落影
        ("ladder_step", "plate_levels", 240, {"min_step": 1}),                     # 钢板明暗场软肩
        ("grain_flat", "plate_levels", 120, {}),                                   # 板面平区补细粒
    ],
    "ruin_debris_chimney": [
        ("run", "soot", "soot", 200, {"dy": 1, "steps": 2}),                       # 烟炱拖尾
        ("neighbor", "joint", "joint_lip", 60, {"onto": "joint", "budget": 80}),   # 灰缝剥白
        ("below_lip", "joint_lip", "brick_levels[0]", 140, {"dy": 2}),             # 砖面沿缝压暗（落砖面第一行）
        ("neighbor", "rim", "cavity", 120, {"dy": -1, "onto": "rim", "budget": 160}),  # 顶缘崩口内扩
        ("grain_flat", "brick_levels", 140, {}),                                       # 砖面平区补细粒
    ],
    "ruined_casing_rusted": [
        ("neighbor", "chip_levels[0]", "chip_levels[1]", 280, {"dxdy": 1}),        # 漆面剥落扩展
        ("run", "bloom", "bloom", 220, {"dy": 1, "steps": 2}),                     # 锈汗下淌
        ("neighbor", "rust_levels[0]", "rust_levels[0]", 260, {"dx": 1}),
        ("neighbor", "frame", "levels[0]", 50, {"onto": "frame", "budget": 70}),   # 机壳框磨断
        ("rivet_shadow", "rivet_sites_casing", "rivet_dark", {}),
        ("ladder_step", "levels", 220, {"min_step": 1}),                            # 壳面明暗软肩
    ],
    "ruined_casing_sooted": [
        ("neighbor", "drip", "drip", 240, {"dx": 1}),                              # 滴痕拖副痕
        ("drip_pool", "drip", "drip", 550, {}),                                    # 滴尾挂珠
        ("neighbor", "drip_lit", "levels[3]", 280, {"dx": 1}),                     # 挂珠受光
        ("neighbor", "frame_lip", "frame", 60, {"onto": "frame_lip", "budget": 80}),
        ("rivet_shadow", "rivet_sites_casing", "rivet_dark", {}),
    ],
    "ruined_casing_porcelain": [
        ("neighbor", "crack", "crack_faint", 130, {"perp": 1}),                    # 裂纹分叉（淡档）
        ("neighbor", "crack_faint", "crack_faint", 180, {"dx": 1}),                # 细纹晕延展
        ("neighbor", "chip", "chip", 300, {"dxdy": 1}),                            # 崩边扩口
        ("neighbor", "chip_lit", "chip", 240, {"dy": 1}),                          # 崩口下缘压暗
        ("neighbor", "frame", "levels[0]", 50, {"onto": "frame", "budget": 70}),
        ("rivet_shadow", "rivet_sites_casing", "rivet_dark", {}),
        ("ladder_step", "levels", 200, {"min_step": 1}),                            # 釉面明暗软肩
    ],
}
CASING_RIVET_SITES = [(1, 1), (13, 1), (1, 13), (13, 13)]
VIOLATIONS = []
SKELETON_HIT = []


# --------------------------------------------------------------------------
# 角色色解析（零 hex 字面量）
# --------------------------------------------------------------------------
def _put_name(names, key, color):
    names.setdefault(key, color)


def resolve_roles(entry):
    """{角色名: RGB}：标量 params + levels 列表展开 + grass_side 的 body/fringe 透传。"""
    names = {}
    params = entry["params"]

    def walk(base, d, prefix=""):
        for name, val in d.items():
            if name == "base" or isinstance(val, (int, float, dict)) or name == "octaves":
                continue
            specs = [val] if isinstance(val, str) else (list(val) if isinstance(val, list) else [])
            for i, s in enumerate(specs):
                if not isinstance(s, str):
                    continue
                try:
                    col = RULES.resolve(s, base)[:3]
                except Exception:
                    continue
                tag = "%s%s[%d]" % (prefix, name, i) if len(specs) > 1 else "%s%s" % (prefix, name)
                _put_name(names, tag, col)
                if prefix:
                    _put_name(names, "%s%s[%d]" % (name, "", i) if len(specs) > 1 else name, col)

    top_base = params.get("base")
    if not top_base and entry.get("params_from"):
        # grass_side 自身无 base：与生成器 paint_grass_side 同口径，标量角色按 body['base'] 解析
        slot = "body" if "body" in entry["params_from"] else sorted(entry["params_from"])[0]
        top_base = ENTRIES[entry["params_from"][slot]]["params"]["base"]
    walk(top_base or "", params)
    for slot, ref in entry.get("params_from", {}).items():
        sub = ENTRIES[ref]["params"]
        walk(sub.get("base", ""), sub, prefix=slot + ".")
    return names


def writable_levels(key, roles):
    """基材档 = 可自由落笔的面；其余角色色视为骨架（默认受保护）。"""
    entry = ENTRIES[key]
    kind = entry["kind"]
    field = {
        "stone_brick": "brick_levels",
        "object_chimney": "brick_levels",
        "object_pipe": "pipe_levels",
        "object_plate": "plate_levels",
        "object_sleeper": "wood_levels",
    }.get(kind, "levels")

    def lvl(prefix):
        return [c for n, c in roles.items() if n.startswith(prefix + "[")]

    if kind == "grass_side":
        return sorted(set(lvl("body.levels")) | set(lvl("fringe.levels")))
    return sorted(set(lvl(field)))


def wrap_flags(key):
    entry = ENTRIES[key]
    if entry["group"] == "prosperity_ruin":
        return False, False
    axes = str(entry.get("seam_axes", "xy"))
    return ("x" in axes), ("y" in axes)


# --------------------------------------------------------------------------
# 细节落笔器（锚点全部取自改前基网格 ⇒ 与 op 顺序无关）
# --------------------------------------------------------------------------
class Pass(object):
    def __init__(self, im, roles, seed, wrap_x, wrap_y, writable):
        self.W = im.width
        src = im.load()
        self.base = [[tuple(src[x, y][:3]) for x in range(self.W)] for y in range(self.W)]
        self.dst = [[self.base[y][x] for x in range(self.W)] for y in range(self.W)]
        self.roles = roles
        self.seed = seed
        self.wx, self.wy = wrap_x, wrap_y
        self.writable = set(writable)
        self.writes = 0
        self.onto = {}

    def col(self, name):
        if name not in self.roles:
            raise AssertionError("DETAIL 引用未知角色 %r；该张可用：%s" % (name, sorted(self.roles)))
        return self.roles[name]

    def at(self, x, y):
        W = self.W
        if not (0 <= x < W) and self.wx:
            x %= W
        if not (0 <= y < W) and self.wy:
            y %= W
        if 0 <= x < W and 0 <= y < W:
            return self.base[y][x]
        return None

    def put(self, x, y, color, onto=None):
        W = self.W
        if self.wx and not (0 <= x < W):
            x %= W
        if self.wy and not (0 <= y < W):
            y %= W
        if not (0 <= x < W and 0 <= y < W):
            return False
        cur = self.dst[y][x]
        if cur in self.writable:
            self.dst[y][x] = color
            self.writes += 1
            return True
        if onto is not None and cur == onto:
            self.dst[y][x] = color
            self.onto[onto] = self.onto.get(onto, 0) + 1
            return True
        return False

    def h(self, tag, x, y):
        return g1.h01(self.seed, "%s.%s" % (SALT, tag), x, y)

    def image(self):
        out = Image.new("RGBA", (self.W, self.W), (0, 0, 0, 0))
        px = out.load()
        for y in range(self.W):
            for x in range(self.W):
                px[x, y] = self.dst[y][x] + (255,)
        return out


def ladder(roles, field):
    items = sorted((n, c) for n, c in roles.items() if n.startswith(field + "["))
    return [c for _n, c in items]


FIELD_OF = {
    "stone_brick": "brick_levels", "object_chimney": "brick_levels",
    "object_pipe": "pipe_levels", "object_plate": "plate_levels",
    "object_sleeper": "wood_levels",
}


def run_ops(p, ops, key):
    W = p.W
    entry = ENTRIES[key]
    base_field = FIELD_OF.get(entry["kind"], "levels")
    for idx, op in enumerate(ops):
        action = op[0]
        tag = "%s.%d" % (key, idx)
        if action == "neighbor":
            _, anchor, target, perm, opt = op
            a_color = p.col(anchor)
            t_color = p.col(target)
            onto = p.col(opt["onto"]) if "onto" in opt else None
            for y in range(W):
                for x in range(W):
                    if p.base[y][x] != a_color:
                        continue
                    if p.h(tag, x, y) >= perm * M:
                        continue
                    if opt.get("dxdy"):
                        dx = (-1, 1, 0, 0)[int(p.h(tag + ".d", x, y) * 4.0) % 4]
                        dy = (0, 0, -1, 1)[int(p.h(tag + ".e", x, y) * 4.0) % 4]
                    elif opt.get("perp"):
                        dx, dy = (1, 0) if (x + y) % 2 == 0 else (0, 1)
                    else:
                        dx, dy = opt.get("dx", 0), opt.get("dy", 0)
                    p.put(x + dx, y + dy, t_color, onto=onto)
        elif action == "run":
            _, anchor, target, perm, opt = op
            a_color = p.col(anchor)
            t_color = p.col(target)
            for step in range(int(opt.get("steps", 1))):
                snapshot = [[p.dst[y][x] for x in range(W)] for y in range(W)]
                for y in range(W):
                    for x in range(W):
                        if snapshot[y][x] != a_color:
                            continue
                        if p.h(tag + ".r%d" % step, x, y) >= perm * M:
                            continue
                        p.put(x, y + opt.get("dy", 1), t_color)
        elif action == "below_lip":
            _, lipname, target, perm, _opt = op
            lip = p.col(lipname)
            dark = p.col(target)
            for y in range(W):
                for x in range(W):
                    if p.at(x, y) == lip and p.h(tag, x, y) < perm * M:
                        p.put(x, y + int(opt.get("dy", 1)), dark)
        elif action == "ladder_step":
            _, _field, perm, opt = op
            lv = ladder(p.roles, base_field if key != "grass_side" else "body.levels")
            if not lv:
                raise AssertionError("%s 无基材阶梯" % key)
            mid = lv[len(lv) // 2]
            min_step = int(opt.get("min_step", 2))
            for y in range(W):
                for x in range(W):
                    cur = p.base[y][x]
                    if cur not in lv:
                        continue
                    i = lv.index(cur)
                    for dx, dy in ((-1, 0), (0, -1)):
                        nb = p.at(x + dx, y + dy)
                        if nb not in lv or nb == cur:
                            continue
                        step = abs(i - lv.index(nb))
                        if step < min_step or p.h(tag + "%d" % dy, x, y) >= perm * M:
                            continue
                        p.put(x, y, mid if step >= 2 else lv[min(i, lv.index(nb))])
        elif action == "grain_flat":
            _, _field, perm, _opt = op
            lv = ladder(p.roles, base_field)
            for y in range(W):
                for x in range(W):
                    cur = p.base[y][x]
                    if cur not in lv:
                        continue
                    i = lv.index(cur)
                    nb = [p.at(x - 1, y), p.at(x + 1, y), p.at(x, y - 1), p.at(x, y + 1)]
                    if any(v != cur for v in nb):
                        continue
                    hv = p.h(tag, x, y)
                    if hv >= perm * M:
                        continue
                    j = i - 1 if hv % 0.5 < 0.25 and i > 0 else (i + 1 if i + 1 < len(lv) else i - 1)
                    if 0 <= j < len(lv):
                        p.put(x, y, lv[j])
        elif action == "drip_pool":
            _, anchor, target, perm, _opt = op
            a_color = p.col(anchor)
            t_color = p.col(target)
            for y in range(W):
                for x in range(W):
                    if p.base[y][x] != a_color or p.at(x, y + 1) == a_color:
                        continue
                    if p.h(tag, x, y) >= perm * M:
                        continue
                    for dx in (-1, 0, 1):
                        p.put(x + dx, y + 1, t_color)
        elif action == "rivet_shadow":
            _, sitesname, target, _opt = op
            t_color = p.col(target)
            sites = entry["params"].get("rivet_sites") or CASING_RIVET_SITES
            for sx, sy in sites:
                x0, y0 = int(sx) * R_SCALE, int(sy) * R_SCALE
                for dx, dy in ((R_SCALE, R_SCALE + 1), (R_SCALE + 1, R_SCALE)):
                    p.put(x0 + dx, y0 + dy, t_color)
        else:
            raise AssertionError("未知细节动作 %r" % action)


M = 1.0 / 1000.0


# --------------------------------------------------------------------------
# 组装 / 断言 / 预览
# --------------------------------------------------------------------------
def build_passes():
    g1.set_size(32)
    first, meta = g1.build_all(MANIFEST)
    second, _ = g1.build_all(MANIFEST)
    passes = {}
    for m in meta:
        key = m["key"]
        assert first["blocks/" + m["file"]] == second["blocks/" + m["file"]], "生成器双跑不一致 " + key
        roles = resolve_roles(ENTRIES[key])
        wx, wy = wrap_flags(key)
        p = Pass(m["image"], roles, g1._parse_seed(MANIFEST["SEEDS"][key]), wx, wy,
                 writable_levels(key, roles))
        run_ops(p, DETAIL[key], key)
        passes[key] = p
        m["final"] = p.image()
        m["detail_writes"] = p.writes
        m["onto"] = dict(p.onto)
    return passes, meta


def budget_of(key, roles):
    """on_top 磨损预算：骨架色 RGB -> 允许被改写的千分比占比上限（跨 op 累加）。"""
    out = {}
    for op in DETAIL[key]:
        opt = op[4] if len(op) > 4 else {}
        if "onto" in opt:
            if opt["onto"] not in roles:
                raise AssertionError("%s 的 onto 角色未知：%s" % (key, opt["onto"]))
            col = roles[opt["onto"]]
            out[col] = out.get(col, 0) + int(opt.get("budget", 100))
    return out


def verify_and_report(meta):
    style = MANIFEST["STYLE"]
    maxc = int(style["pixel_discipline"]["max_distinct_rgb"])
    rows = []
    g1.set_size(16)
    b16, m16 = g1.build_all(MANIFEST)
    base16_px = {m["key"]: m["image"] for m in m16}
    for key, digest in {e["key"]: hashlib.sha256(b16["blocks/" + e["file"]]).hexdigest()
                        for e in MANIFEST["OUTPUTS"]}.items():
        want = (BASE_DIR / (key + ".png")).read_bytes()
        assert hashlib.sha256(want).hexdigest() == digest, "基线快照被改动或生成器漂移：" + key
    g1.set_size(32)   # 其后 seam_metrics / distinct 读数按 32 档口径
    for m in meta:
        key = m["key"]
        im = m["final"]
        assert im.size == (32, 32), "%s 非 32x32" % key
        used = g1.distinct_rgb(im)
        assert len(used) <= maxc, "%s 色数 %d > %d" % (key, len(used), maxc)
        assert not (used & set(g1.FORBIDDEN_RGB)), "%s 含纯黑/纯白" % key
        px = im.load()
        for y in range(32):
            for x in range(32):
                assert px[x, y][3] == 255, "%s alpha 非 255" % key
        roles = resolve_roles(ENTRIES[key])
        declared = set(roles.values())
        approved = g1.distinct_rgb(base16_px[key])
        off = used - approved - declared
        lost = approved - used
        assert not off, "%s 用了 manifest 阶梯外的色：%s" % (key, sorted(off))
        assert not lost, "%s 丢了已 approve 的用色档：%s" % (key, sorted(lost))
        # 骨架保护：非基材色只允许被申报的 on_top 压过
        writable = set(writable_levels(key, roles))
        bud = budget_of(key, roles)
        counts = {}
        for y in range(32):
            for x in range(32):
                cur, new = m["src_base"][y][x], px[x, y][:3]
                if cur == new:
                    continue
                if cur in writable:
                    counts["body"] = counts.get("body", 0) + 1
                else:
                    counts[cur] = counts.get(cur, 0) + 1
        for color, n in counts.items():
            if color == "body":
                continue
            limit = bud.get(color)
            total = sum(1 for y in range(32) for x in range(32) if m["src_base"][y][x] == color)
            if limit is None:
                VIOLATIONS.append("%s 未申报就压改骨架色 #%02X%02X%02X (%d/%d)" % (
                    key, color[0], color[1], color[2], n, total))
            elif n * 1000 > total * limit:
                VIOLATIONS.append("%s 骨架色 #%02X%02X%02X 改动 %d/%d=%.0f‰ > 申报 %d‰" % (
                    key, color[0], color[1], color[2], n, total, 1000.0 * n / total, limit))
            else:
                SKELETON_HIT.append("%s #%02X%02X%02X %d/%d=%.0f‰<=限 %d‰" % (
                    key, color[0], color[1], color[2], n, total, 1000.0 * n / total, limit))
        axes = str(ENTRIES[key].get("seam_axes", "xy"))
        gross = "-"
        if ENTRIES[key].get("tileable"):
            sm = g1.seam_metrics(im)
            lim = g1.seam_gross_max(style, ENTRIES[key]["group"])
            for ax, val in (("x", sm["col_gross"]), ("y", sm["row_gross"])):
                if ax in axes:
                    assert val <= lim, "%s 硬缝超限 %s_gross=%.3f > %.2f" % (key, ax, val, lim)
            gross = "%.2f,%.2f" % (sm["col_gross"], sm["row_gross"])
        # 反「分辨率通胀」读数：与 16 档 2x 最近邻拉伸不同的像素占比
        up = g1._nearest(base16_px[key], 2).load()
        diff = sum(1 for y in range(32) for x in range(32) if up[x, y][:3] != px[x, y][:3])
        role16 = sum(1 for y in range(16) for x in range(16)
                     if base16_px[key].load()[x, y][:3] not in writable)
        role32 = sum(1 for y in range(32) for x in range(32) if m["src_base"][y][x] not in writable)
        rows.append(dict(key=key, size="32x32", rgb=len(used), rgb16=len(approved),
                         grain="%d->%d" % (role16, role32),
                         new=sorted("#%02X%02X%02X" % c for c in (used - approved)),
                         body=counts.get("body", 0),
                         skeleton="+".join("%s:%d" % ("#%02X%02X%02X" % c, n) for c, n in counts.items() if c != "body") or "-",
                         vs_nn="%.1f%%" % (100.0 * diff / 1024.0), gross=gross))
    print("[SKELETON on_top 磨损实测（骨架只在申报处被稀疏压改）]")
    for line in sorted(SKELETON_HIT):
        print("  " + line)
    if VIOLATIONS:
        print("[骨架保护违规]")
        for line in VIOLATIONS:
            print("  " + line)
        raise AssertionError("骨架保护违规 %d 条" % len(VIOLATIONS))
    return rows


def board(meta, base16_px):
    cell_w, cell_h = 412, 200
    cols = 4
    rows = (len(meta) + cols - 1) // cols
    header = 46
    bd = Image.new("RGBA", (cols * cell_w + 24, header + rows * cell_h + 10), g1.PREVIEW_BG)
    d = ImageDraw.Draw(bd)
    font = ImageFont.load_default()
    d.text((12, 8), "p3 A2  dim1 14 sheets  16x16 (approved) -> 32x32 (redrawn + wear detail)", font=font, fill=g1.PREVIEW_HDR)
    d.text((12, 22), "per cell: [16 @8x] [32 base @4x] [32 FINAL @4x] | below = FINAL tiled 2x2 @2x (x-only when seam_axes=x)",
           font=font, fill=g1.PREVIEW_DIM)
    for i, m in enumerate(meta):
        key = m["key"]
        col, row = i % cols, i // cols
        x0, y0 = 12 + col * cell_w, header + row * cell_h
        used = len(g1.distinct_rgb(m["final"]))
        d.text((x0, y0 + 2), "%s rgb=%d+%dpx" % (key, used, m["detail_writes"]), font=font, fill=g1.PREVIEW_FG)
        bd.paste(g1._nearest(base16_px[key], 8), (x0, y0 + 14))
        bd.paste(g1._nearest(m["image"], 4), (x0 + 136, y0 + 14))
        bd.paste(g1._nearest(m["final"], 4), (x0 + 272, y0 + 14))
        d.text((x0, y0 + 136), "16@8x | 32 rescale@4x | 32 FINAL@4x", font=font, fill=g1.PREVIEW_DIM)
        t = g1._nearest(g1._tiled(m["final"], 2, 2) if str(ENTRIES[key].get("seam_axes", "xy")) == "xy"
                        else g1._tiled(m["final"], 2, 1), 2) if m["tileable"] else g1._nearest(m["final"], 2)
        bd.paste(t, (x0 + 272, y0 + 146))
    return bd


def main():
    draft = "--draft" in sys.argv
    passes, meta = build_passes()
    for m in meta:
        src = m["image"].load()
        m["src_base"] = [[tuple(src[x, y][:3]) for x in range(32)] for y in range(32)]
    rows = verify_and_report(meta)
    base16_px = {r["key"]: None for r in rows}
    g1.set_size(16)
    _b, m16 = g1.build_all(MANIFEST)
    base16_px = {m["key"]: m["image"] for m in m16}
    g1.set_size(32)
    print("[VERIFY] 32 档逐张读数")
    print("  %-34s %-6s %-7s %-6s %-8s %-9s %-9s %s" % (
        "key", "size", "rgb", "vs_nn", "seam", "new_px", "grain16>32", "skeleton_touched"))
    for r in rows:
        print("  %-34s %-6s %-7s %-6s %-8s %-9s %-9s %s" % (
            r["key"], r["size"], "%d(16:%d)" % (r["rgb"], r["rgb16"]), r["vs_nn"], r["gross"],
            r["body"], r["grain"], r["skeleton"]))
        if r["new"]:
            print("      PALETTE_DELTA_vs_approved16 = %s" % r["new"])

    out = {}
    for m in meta:
        data = g1._png_bytes(m["final"])
        g1.ihdr_check(data, (32, 32))
        out[m["key"]] = data
    passes2, meta2 = build_passes()
    same = all(out[m["key"]] == g1._png_bytes(m["final"]) for m in meta2)
    print("[IDEMPOTENT] 细节层双跑逐字节一致 = %s" % same)
    assert same, "细节层非幂等"

    DRAFT_DIR.mkdir(parents=True, exist_ok=True)
    REVIEW_DIR.mkdir(parents=True, exist_ok=True)
    for key in out:
        (DRAFT_DIR / (key + ".png")).write_bytes(out[key])
    board(meta, base16_px).save(str(REVIEW_DIR / "compare_16v32_a2.png"), "PNG")
    for key in out:
        m = next(mm for mm in meta if mm["key"] == key)
        g1._nearest(m["final"], 4).save(str(REVIEW_DIR / ("zoom32_%s.png" % key)), "PNG")
        g1._nearest(base16_px[key], 8).save(str(REVIEW_DIR / ("zoom16_%s.png" % key)), "PNG")
    (REVIEW_DIR / "_a2_after_readings.tsv").write_text(
        "key\tw\th\tdistinct_rgb\tbytes\tsha256\n" + "\n".join(
            "%s\t32\t32\t%d\t%d\t%s" % (k, len(g1.distinct_rgb(Image.open(str(DRAFT_DIR / (k + ".png"))).convert("RGBA"))),
                                        len(v), hashlib.sha256(v).hexdigest()) for k, v in sorted(out.items())) + "\n",
        encoding="utf-8")
    (REVIEW_DIR / "_a2_detail_spec.json").write_text(json.dumps({
        "generator": "tools/artgen/dim1/gen_dim1_blocks.py（未修改）",
        "manifest": "tools/artgen/dim1/manifest.json（未修改）",
        "scale_rules": "R1..R5（生成器文件头）",
        "salt_prefix": SALT,
        "r_unit": "千分比",
        "detail": {k: [list(map(str, o)) for o in v] for k, v in DETAIL.items()},
    }, ensure_ascii=False, indent=2), encoding="utf-8")

    if draft:
        print("[DRAFT] 只写草稿目录 %s" % DRAFT_DIR)
        return
    for key, data in out.items():
        path = ASSET_DIR / ENTRIES[key]["file"]
        path.write_bytes(data)
        assert path.read_bytes() == data, "回读字节不一致 " + key
    print("[LAND] %d 张落地 src/main/resources/assets/gtsr/textures/blocks/" % len(out))
    for key in sorted(out):
        print("  %-34s %5d B  %s" % (key, len(out[key]), hashlib.sha256(out[key]).hexdigest()))


if __name__ == "__main__":
    main()
