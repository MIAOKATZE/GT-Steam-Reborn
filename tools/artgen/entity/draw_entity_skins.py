#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
D1 · dim78 三只自有生物皮肤生成器（v1.20.37 批）。

单一权威
-------
* 锚点与明暗阶：**不在此文件里**。全部从方块域两份既有清单读取
  ``tools/artgen/dim7879/manifest.json`` 与 ``tools/artgen/dim1/manifest.json``
  的 ``PALETTE`` + ``DERIVED_RULES``（同名锚点跨清单必须等值，不等即红）。
  本脚本因此**零硬编码十六进制**——要改色板只能改清单。
* 文件名 / 画布 / UV 部件归属 / 角色表 / 画法理由：``tools/artgen/entity/manifest.json``。
* 画布尺寸的最终裁判是 ``CreatureSpawnAuthorityCheck`` 的 I2 组（名册申报 ==
  模型 textureWidth/Height == 贴图 IHDR）；本脚本写盘后用正则回读名册做**同一件事的
  离线预检**，让 D1 在画皮阶段就能撞红，而不是等主代理跑断言链。

确定性
-------
全程无随机（无 RNG、无时间戳、无字典序依赖），同输入双跑**字节一致**；PNG 走 PIL 默认
编码器（不写文本块）。

UV 展开式
---------
``face_rect`` 是 1.7.10 ``ModelRenderer.addBox`` 的经典展开（与玩家 64x32 皮肤同式），
已用原版 chicken/skeleton/bat 三张皮的实际像素**逐面对验**（眼/齿/喙落在预测的 front 面，
见 plan/tmp/p3-d1/uv-verify.txt）——所以本文件里每张模板的 part/face 归属是实测不是推断。

用法
----
    python tools/artgen/entity/draw_entity_skins.py             # 画 + 落盘 + 自证
    python tools/artgen/entity/draw_entity_skins.py --preview   # 只出逐面 ASCII，不落盘
    python tools/artgen/entity/draw_entity_skins.py --audit     # 只重算已落盘文件的实测色数
"""
from __future__ import annotations

import hashlib
import json
import re
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[3]
ENTITY_MANIFEST = ROOT / "tools/artgen/entity/manifest.json"
BLOCK_MANIFESTS = (
    ROOT / "tools/artgen/dim7879/manifest.json",
    ROOT / "tools/artgen/dim1/manifest.json",
)
MEASURED = ROOT / "tools/artgen/entity/measured.json"
ROSTER_JAVA = (ROOT / "src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/entity"
               / "GTSRCreatureRoster.java")
FORBIDDEN = ((0, 0, 0), (255, 255, 255))
TRANSPARENT = (0, 0, 0, 0)
FACES = ("top", "bottom", "right", "front", "left", "back")
ORDER = ("STEAM_FIREFLY", "SLAG_RIDGE_HUNTER", "GEAR_PIGEON")

MANIFEST = json.loads(ENTITY_MANIFEST.read_text(encoding="utf-8"))


# --------------------------------------------------------------------------- 调色板
def _hex(h: str) -> tuple[int, int, int]:
    s = h.lstrip("#")
    return int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16)


def load_palette():
    """合并两份方块清单的 PALETTE/DERIVED_RULES，并钉住"同名锚点跨清单等值"。"""
    anchors, home, rules = {}, {}, {}
    for rel in BLOCK_MANIFESTS:
        man = json.loads(rel.read_text(encoding="utf-8"))
        for k, v in man["PALETTE"].items():
            if k.startswith("_"):
                continue
            if k in anchors and anchors[k].upper() != v.upper():
                raise SystemExit(f"锚点跨清单不同值：{k} {anchors[k]}({home[k]}) vs {v}({rel.name})")
            anchors.setdefault(k, v)
            home.setdefault(k, rel.name)
        for k, v in man["DERIVED_RULES"].items():
            if k.startswith("_"):
                continue
            got = (v["toward"], float(v["t"]))
            if k in rules and rules[k] != got:
                raise SystemExit(f"明暗阶跨清单不同式：{k} {rules[k]} vs {got}")
            rules.setdefault(k, got)
    return anchors, rules


ANCHORS, RULES = load_palette()


def shade(color, toward, t):
    return tuple(int(round(color[i] + (toward[i] - color[i]) * t)) for i in range(3))


class Roles:
    """一张皮的色板角色表（角色名 -> 锚点名）+ DERIVED_RULES 的明暗阶 token。

    ``P("PLATE@D280")`` = 该皮 PLATE 锚点向 DARK_ANCHOR 插值 t=0.28。角色名与锚点
    同名时也可直接 ``P("STEEL_PLATE")``。未申报的角色名/ token 一律撞红（禁魔数）。
    """

    def __init__(self, table):
        self.table = dict(table)
        self.used = []

    def anchor_of(self, name):
        if name in self.table:
            return self.table[name]
        if name in ANCHORS:
            return name
        raise SystemExit(f"未申报的色板角色：{name}（先加进 entity/manifest.json 的 palette_roles）")

    def P(self, spec):
        name, _, token = spec.partition("@")
        anchor = _hex(ANCHORS[self.anchor_of(name)])
        if not token:
            self.used.append(name)
            return anchor
        if token not in RULES:
            raise SystemExit(f"未申报的明暗阶 token：{token}（DERIVED_RULES 里没有，禁止另立数值）")
        toward, t = RULES[token]
        self.used.append(spec)
        return shade(anchor, _hex(ANCHORS[toward]), t)


# --------------------------------------------------------------------------- 画布
def face_rect(box, face):
    u, v, w, h, d = box
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + d + w + d, v + d, w, h),
    }[face]


class Skin:
    def __init__(self, key, spec, roles):
        self.key = key
        self.spec = spec
        self.roles = roles
        self.w, self.h = spec["canvas"]
        self.img = Image.new("RGBA", (self.w, self.h), TRANSPARENT)
        self.boxes = {p["name"]: tuple(p["box"]) for p in spec["parts"]}
        self.sampled = set()
        for box in self.boxes.values():
            for face in FACES:
                x, y, fw, fh = face_rect(box, face)
                self.sampled.update((x + i, y + j) for j in range(fh) for i in range(fw))

    def put(self, x, y, rgb):
        if not (0 <= x < self.w and 0 <= y < self.h):
            raise SystemExit(f"{self.key}: 越界像素 ({x},{y})，画布 {self.w}x{self.h}")
        if (x, y) not in self.sampled:
            raise SystemExit(f"{self.key}: ({x},{y}) 不在任何 UV 采样面内——死区不画东西")
        if rgb in FORBIDDEN:
            raise SystemExit(f"{self.key}: ({x},{y}) 触到纯黑/纯白禁则")
        self.img.putpixel((x, y), (rgb[0], rgb[1], rgb[2], 255))

    def rect(self, part, face, lx, ly, w, h, role):
        x, y, _, _ = face_rect(self.boxes[part], face)
        rgb = self.roles.P(role)
        for j in range(h):
            for i in range(w):
                self.put(x + lx + i, y + ly + j, rgb)

    def px(self, part, face, lx, ly, role):
        self.rect(part, face, lx, ly, 1, 1, role)

    def hline(self, part, face, ly, role, x0=0, w=None):
        _, _, fw, _ = face_rect(self.boxes[part], face)
        self.rect(part, face, x0, ly, fw if w is None else w, 1, role)

    def vline(self, part, face, lx, role, y0=0, h=None):
        _, _, _, fh = face_rect(self.boxes[part], face)
        self.rect(part, face, lx, y0, 1, fh if h is None else h, role)

    def fill(self, part, role):
        for face in FACES:
            _, _, fw, fh = face_rect(self.boxes[part], face)
            self.rect(part, face, 0, 0, fw, fh, role)

    def fill_faces(self, roles_by_face):
        for part, table in roles_by_face.items():
            for face in FACES:
                _, _, fw, fh = face_rect(self.boxes[part], face)
                role = table.get(face, table.get("*"))
                if role is None:
                    raise SystemExit(f"{self.key}.{part}.{face}: 未给基色（'*' 也没写）")
                self.rect(part, face, 0, 0, fw, fh, role)

    def stamp(self, part, face, rows, legend):
        """ASCII 模板盖到某个面（行=自上而下，列=自左而右，与展开面同向；空格=保留底色）。"""
        x, y, fw, fh = face_rect(self.boxes[part], face)
        if len(rows) != fh or any(len(r) != fw for r in rows):
            raise SystemExit(f"{self.key}.{part}.{face}: 模板 {len(rows)} 行/宽 {[len(r) for r in rows]} "
                             f"与面 {fw}x{fh} 不符")
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                if ch == " ":
                    continue
                if ch not in legend:
                    raise SystemExit(f"{self.key}.{part}.{face}: 模板字符 '{ch}' 无角色归属")
                self.put(x + i, y + j, self.roles.P(legend[ch]))

    # — 自证 —
    def audit(self):
        rgba = {self.img.getpixel((i, j)) for j in range(self.h) for i in range(self.w)
                if self.img.getpixel((i, j))[3] != 0}
        opaque = {p[:3] for p in rgba}
        painted = {(i, j) for j in range(self.h) for i in range(self.w) if self.img.getpixel((i, j))[3] != 0}
        stray = painted - self.sampled
        if stray:
            raise SystemExit(f"{self.key}: {len(stray)} 个像素落在死区")
        for c in opaque:
            if c in FORBIDDEN:
                raise SystemExit(f"{self.key}: 用了纯黑/纯白 {c}")
        cover = {}
        for part, box in self.boxes.items():
            for face in FACES:
                x, y, fw, fh = face_rect(box, face)
                op = sum(1 for j in range(fh) for i in range(fw) if self.img.getpixel((x + i, y + j))[3] != 0)
                cover[f"{part}.{face}"] = f"{op}/{fw * fh}"
        holes = sorted({k for k, v in cover.items() if int(v.split('/')[0]) != int(v.split('/')[1])})
        return {
            "file": self.spec["file"],
            "canvas": [self.w, self.h],
            "distinct_rgba": len(rgba) + 1,             # +1 = 透明背景 (0,0,0,0)，与方块域同口径
            "distinct_rgb_opaque": len(opaque),
            "palette_roles_used": sorted(set(self.roles.used)),
            "palette_hex": {},
            "sampled_face_coverage": cover,
            "faces_not_fully_painted": holes,
            "opaque_ratio": round(len(painted) / (self.w * self.h), 4),
        }

    def save(self, path: Path) -> bytes:
        self.img.save(path, format="PNG", optimize=False)
        return path.read_bytes()


# --------------------------------------------------------------------------- 汽雾萤 64x64
def paint_firefly(s: Skin):
    """蒸汽时代的蝙蝠形小生物：烟箱头 + 排烟管 + 铆接锅炉躯干 + 冲压骨架膜翼 + 翼尖灯。
    冷铁灰域走 L 侧拉开对比，黄铜/铜绿只做暖隔条，灯芯是唯一亮点。"""
    s.fill_faces({
        "head": {"*": "PLATE@D280", "top": "PLATE@L090", "bottom": "DEEP@D280", "front": "PLATE@D180"},
        "ear": {"*": "IRON@D180", "top": "SOOT@D280", "bottom": "DEEP@D280",
                "left": "DEEP@D180", "right": "DEEP@D180"},
        "body": {"*": "PLATE@D180", "top": "PLATE_HI@D090", "bottom": "SOOT"},
        "inner_membrane": {"*": "FRAME@D280", "top": "IRON@D180", "bottom": "DEEP@D280"},
        "wing": {"*": "FRAME@D180", "top": "BRASS_DARK", "bottom": "DEEP@D280"},
        "outer_wing": {"*": "FRAME@D280", "top": "IRON@D180", "bottom": "DEEP@D280"},
    })
    # 头 front = 烟箱门：黄铜圈 + GLINT 灯芯（6x6 里唯一能读的脸）
    s.stamp("head", "front", [
        "hhhhhh",
        "hbbbbh",
        "hbLLbh",
        "hbLLbh",
        "hbbbbh",
        "ffffff"],
        {"h": "PLATE_HI@L180", "b": "BRASS", "L": "LAMP", "f": "DEEP@D280"})
    # 头顶 = 冠板 + 检修口
    s.stamp("head", "top", [
        "rrrrrr",
        "rPPPPr",
        "PPbbPP",
        "PPbbPP",
        "rPPPPr",
        "rrrrrr"],
        {"r": "DEEP@D180", "P": "PLATE_HI", "b": "BRASS_DARK"})
    # 两颊 = 铁板 + 烟渍（左颊 mirror 采样，故两面同图）
    for face in ("left", "right"):
        s.stamp("head", face, [
            "ffffff",
            "fppppf",
            "fppppf",
            "fprrpf",
            "fprppf",
            "ffffff"],
            {"f": "DEEP@D280", "p": "PLATE", "r": "RUST@D180"})
    # 后脑 = 回火管座 + 铜绿
    s.stamp("head", "back", [
        "iiiiii",
        "iffffi",
        "ifGGfi",
        "ifGGfi",
        "iffffi",
        "iiiiii"],
        {"i": "IRON@D180", "f": "DEEP@D280", "G": "PATINA_DARK"})
    s.stamp("head", "bottom", [
        "ffffff",
        "fSSSSf",
        "fSooSf",
        "fSooSf",
        "fSSSSf",
        "ffffff"],
        {"f": "DEEP@D280", "S": "SOOT", "o": "RUST@D280"})

    # 排烟管（原版耳位改判）：帽环 + 管口 + 一道锈
    for face in ("front", "back"):
        s.stamp("ear", face, ["BBB", "bbb", "fSf", "fFf"],
                {"B": "BRASS@L180", "b": "BRASS", "f": "DEEP@D180", "S": "SOOT", "F": "DEEP@D280"})
        s.px("ear", face, 1, 2, "RUST@D090")
    for face in ("left", "right"):
        s.stamp("ear", face, ["B", "b", "S", "F"],
                {"B": "BRASS@L090", "b": "BRASS_DARK", "S": "SOOT@D180", "F": "DEEP@D280"})
    s.stamp("ear", "top", ["sfs"], {"s": "SOOT@L090", "f": "DEEP@D280"})

    # 躯干 = 铆接锅炉：两道黄铜箍带 + 两条抱箍（铆钉） + 正面水位镜 / 背面炉门
    HOOP = {"f": "DEEP@D280", "P": "PLATE@L090", "p": "PLATE", "b": "BRASS", "B": "BRASS@L180",
            "r": "DEEP@D180", "R": "RUST@D180"}
    s.stamp("body", "front", [
        "ffffff",
        "fPPPPf",
        "bbbbbb",
        "bBBBBb",
        "fgGGgf",
        "fgGGgf",
        "fggGGf",
        "fggGGf",
        "bbbbbb",
        "bBBBBb",
        "fRRRRf",
        "ffffff"],
        dict(HOOP, **{"g": "GLASS@D180", "G": "GLASS@L180"}))
    s.stamp("body", "back", [
        "ffffff",
        "fPPPPf",
        "bbbbbb",
        "bBBBBb",
        "fDDDDf",
        "fDllDf",
        "fDllDf",
        "fDDDDf",
        "bbbbbb",
        "bBBBBb",
        "fRRRRf",
        "ffffff"],
        dict(HOOP, **{"D": "SOOT", "l": "LAMP@D090"}))
    for face in ("left", "right"):
        s.stamp("body", face, [
            "ffffff",
            "fPPPPf",
            "bbbbbb",
            "bBBBBb",
            "rppppr",
            "Bppppr",
            "rppppB",
            "rppppr",
            "bbbbbb",
            "bBBBBb",
            "fRRRRf",
            "ffffff"], HOOP)
    s.stamp("body", "top", [
        "hhhhhh",
        "hPPPPb",
        "PPhhhb",
        "PPhhhb",
        "hPPPPb",
        "hhhhhh"],
        {"h": "DEEP@D180", "P": "PLATE_HI", "b": "BRASS_DARK"})
    s.stamp("body", "bottom", [
        "ffffff",
        "fSSSSf",
        "fSnnSf",
        "fSSSSf",
        "ffffff",
        "ffffff"],
        {"f": "DEEP@D280", "S": "SOOT", "n": "RUST@D280"})

    # 内翼膜：上梁 + 三肋 + 两个烧穿孔 + 铜绿后缘
    for face in ("front", "back"):
        s.hline("inner_membrane", face, 0, "BRASS_DARK")
        for col in (2, 5, 8):
            s.vline("inner_membrane", face, col, "VEIN@D090", 1, 4)
        s.hline("inner_membrane", face, 5, "PATINA_DARK")
        s.px("inner_membrane", face, 3, 3, "DEEP@D280")
        s.px("inner_membrane", face, 6, 2, "DEEP@D280")
    for face in ("left", "right"):
        s.hline("inner_membrane", face, 0, "BRASS_DARK")
        s.hline("inner_membrane", face, 5, "PATINA_DARK")

    # 主翼：前缘黄铜梁 + 三肋 + 排气缝 + 翼尖灯（"萤"的关键一处；左翼镜像同图）
    for face in ("front", "back"):
        s.hline("wing", face, 0, "BRASS@L090")
        s.hline("wing", face, 1, "IRON@D180")
        s.hline("wing", face, 4, "DEEP@D280")
        s.hline("wing", face, 9, "DEEP@D280")
        s.hline("wing", face, 15, "PATINA_DARK")
        for col in (2, 5, 8):
            s.vline("wing", face, col, "VEIN@D090", 2, 13)
        s.vline("wing", face, 0, "BRASS_DARK")
        for row in (2, 6, 11):
            s.px("wing", face, 0, row, "BRASS@L180")
        s.rect("wing", face, 6, 10, 4, 1, "BRASS_DARK")
        s.rect("wing", face, 6, 13, 4, 1, "BRASS_DARK")
        s.rect("wing", face, 6, 11, 1, 2, "BRASS_DARK")
        s.rect("wing", face, 9, 11, 1, 2, "BRASS_DARK")
        s.rect("wing", face, 7, 11, 2, 2, "LAMP")
        s.px("wing", face, 1, 12, "RUST@D180")
        s.px("wing", face, 3, 14, "DEEP@D280")
        s.px("wing", face, 4, 14, "DEEP@D280")
    for face in ("left", "right"):
        s.hline("wing", face, 0, "BRASS")
        s.hline("wing", face, 15, "PATINA_DARK")

    # 外翼段：更暗 + 一排排气孔 + 铜绿后缘
    for face in ("front", "back"):
        s.hline("outer_wing", face, 0, "BRASS@L090")
        s.hline("outer_wing", face, 1, "IRON@D180")
        s.hline("outer_wing", face, 11, "PATINA_DARK")
        for col in (2, 5):
            s.vline("outer_wing", face, col, "VEIN@D180", 2, 6)
        for col in (1, 3, 5, 7):
            s.px("outer_wing", face, col, 8, "DEEP@D280")
        s.px("outer_wing", face, 0, 4, "RUST@D180")
        s.px("outer_wing", face, 0, 5, "RUST@D090")
    for face in ("left", "right"):
        s.hline("outer_wing", face, 0, "BRASS_DARK")
        s.hline("outer_wing", face, 11, "PATINA_DARK")


# --------------------------------------------------------------------------- 渣脊猎手 64x32
FACET_LEGEND = {
    "#": "SLAG", "=": "SLAG@D180", "-": "SLAG_DARK@D090", "b": "SLAG_DARK@D180",
    "t": "SLAG@L200", ".": "TAR_DEEP", "S": "SHARD", "x": "SPIKE@D090",
    "n": "ASH_DARK", "G": "GLOW", "g": "GLASS_HI@L090",
}


def paint_hunter(s: Skin):
    """敌对的熔渣猎手：dim79 slag/glass/tar 色家族，语言是**棱面断口**——大块直边多边形
    之间走不同 L/D 阶（不用 1px 棋盘噪点，那读起来是迷彩不是断口），焦油只在断面缝里
    出现（1px 直缝，不成片），冷蓝 SHARD 是断口露背 + 眼 + 肘膝棱块，紫 GLOW 只留 2px 炉心。"""
    s.fill("head", "SLAG@D180")
    s.fill("body", "SLAG_DARK@D090")
    s.fill("arm", "SLAG@D180")
    s.fill("leg", "SLAG_DARK@D090")
    L = FACET_LEGEND
    # 头 front = 玩家主视面：亮顶脊 → 渣板 → 一道全宽焦油眉缝 → 两颗冷蓝琉璃眼 → 齿排
    s.stamp("head", "front", [
        "tttttttt",
        "########",
        "........",
        "SSttttSS",
        "SS....SS",
        "########",
        "x#xx#x#x",
        ".xx.xx.x"], L)
    s.stamp("head", "back", [
        "========",
        "===..===",
        "===..===",
        "========",
        "........",
        "========",
        "===..===",
        "........"], L)
    s.stamp("head", "top", [
        "########",
        "##....##",
        "#..##..#",
        "..#..#..",
        "#..##..#",
        "##....##",
        "========",
        "tttttttt"], L)
    s.stamp("head", "bottom", [
        "........",
        "..bbbb..",
        "..bnnb..",
        "...bb...",
        "...bb...",
        "........",
        "........",
        "........"], L)
    for face in ("left", "right"):
        s.stamp("head", face, [
            "========",
            "========",
            "........",
            "===..===",
            "===..===",
            "========",
            "........",
            "--------"], L)
    # 躯干 front = 叠置渣板胸（三块大板 + 两道全宽焦油横缝）+ 一颗冷蓝炉心
    s.stamp("body", "front", [
        "tttttttt",
        "########",
        "########",
        "........",
        "========",
        "========",
        "........",
        "##nnnn##",
        "#nSSSSn#",
        "#nSGGnS#",
        "##nnnn##",
        "........"], L)
    s.stamp("body", "back", [
        "========",
        "========",
        "........",
        "===..===",
        "===..===",
        "========",
        "........",
        "========",
        "========",
        "........",
        "===..===",
        "........"], L)
    for face in ("left", "right"):
        s.stamp("body", face, [
            "tttt",
            "####",
            "####",
            "....",
            "====",
            "====",
            "....",
            "nSSn",
            "nSSn",
            "....",
            "====",
            "...."], L)
    s.stamp("body", "top", [
        "tttttttt",
        "########",
        "#......#",
        "========"], L)
    s.stamp("body", "bottom", [
        "........",
        ".bbbbbb.",
        ".b....b.",
        "........"], L)
    # 细骨臂（2x12）：肩板 → 肘（冷蓝棱块）→ 腕缝 → 爪
    for face in ("front", "back"):
        s.stamp("arm", face, [
            "##",
            "##",
            "#=",
            "S=",
            "SS",
            "=S",
            "==",
            "==",
            "=.",
            "##",
            "##",
            "g#"], L)
    for face in ("left", "right"):
        s.stamp("arm", face, [
            "=-",
            "==",
            "==",
            "=S",
            "S=",
            "==",
            "==",
            "-=",
            "==",
            "=-",
            "==",
            "#g"], L)
    s.stamp("arm", "top", ["tt", "t#"], L)
    s.stamp("arm", "bottom", ["..", ".."], L)
    # 细骨腿（2x12）：股板 → 膝（冷蓝棱块）→ 踝 → 挑出的爪尖
    for face in ("front", "back"):
        s.stamp("leg", face, [
            "==",
            "==",
            "=.",
            "##",
            "##",
            "##",
            "=.",
            "S=",
            "SS",
            "==",
            "g=",
            ".g"], L)
    for face in ("left", "right"):
        s.stamp("leg", face, [
            "=-",
            "==",
            "==",
            "-=",
            "==",
            "==",
            "==",
            "-=",
            "S=",
            "=S",
            "=g",
            "g."], L)
    s.stamp("leg", "top", ["tt", "t="], L)
    s.stamp("leg", "bottom", ["..", ".."], L)




# --------------------------------------------------------------------------- 齿轮鸽 64x32
def paint_pigeon(s: Skin):
    """被驯化的机械鸽：亮（搪瓷 + 黄铜 + GLINT）、圆弧（齿轮 / 铆环 / 穹顶），
    与猎手的棱面、萤的暗铁正好反向。"""
    s.fill_faces({
        "head": {"*": "SHELL", "bottom": "IRON@D180"},
        "bill": {"*": "BRASS@D180", "bottom": "BRASS_DARK@D090"},
        "chin": {"*": "BRASS_DARK"},
        "body": {"*": "SHELL", "back": "STEEL@D180", "top": "STEEL@D180", "bottom": "DEEP@D180"},
        "leg": {"*": "BRASS_DARK", "top": "IRON", "bottom": "DEEP"},
        "wing": {"*": "STEEL@D180", "back": "STEEL@D280"},
    })
    # 头 front（4x6）：黄铜框琉璃眼，2x2 是这块的全部眼位预算
    s.stamp("head", "front", [
        "PPPP",
        "BBBB",
        "bHGb",
        "bGGb",
        "bppb",
        "dddd"],
        {"P": "SHELL@L280", "B": "BRASS@L180", "b": "BRASS", "H": "GLOW", "G": "GLASS",
         "p": "SHELL@D180", "d": "STEEL@D180"})
    s.stamp("head", "top", ["PPPP", "PbbP", "PbbP"], {"P": "SHELL", "b": "BRASS@D180"})
    s.stamp("head", "back", [
        "PPPP",
        "bppb",
        "bPPb",
        "bPPb",
        "bppb",
        "dddd"],
        {"P": "SHELL", "b": "BRASS@D180", "p": "PATINA", "d": "STEEL@D180"})
    for face in ("left", "right"):
        s.stamp("head", face, [
            "PPP",
            "PsP",
            "PrP",
            "PrP",
            "PsP",
            "ddd"],
            {"P": "SHELL", "s": "STEEL_HI", "r": "BRASS@D180", "d": "STEEL@D180"})
    s.stamp("head", "bottom", ["iiii", "ibbi", "iiii"], {"i": "IRON@D180", "b": "BRASS@D180"})
    # 喙 = 黄铜百叶
    s.stamp("bill", "front", ["BBBB", "bBBb"], {"B": "BRASS@L220", "b": "BRASS_DARK"})
    s.stamp("bill", "top", ["BBBB", "bbBb"], {"B": "BRASS@L180", "b": "BRASS"})
    s.stamp("bill", "back", ["bbbb", "bdbd"], {"b": "BRASS@D180", "d": "BRASS_DARK"})
    for face in ("left", "right"):
        s.stamp("bill", face, ["Bb", "bd"], {"B": "BRASS@L180", "b": "BRASS@D180", "d": "BRASS_DARK"})
    s.stamp("bill", "bottom", ["dddd", "bddb"], {"d": "BRASS_DARK@D090", "b": "BRASS_DARK"})
    # 下颏夹板
    s.stamp("chin", "front", ["bB", "dd"], {"b": "BRASS@D180", "B": "BRASS", "d": "BRASS_DARK"})
    s.stamp("chin", "back", ["dd", "dr"], {"d": "BRASS_DARK", "r": "IRON@D180"})
    s.stamp("chin", "top", ["bb", "bd"], {"b": "BRASS@D180", "d": "BRASS_DARK"})
    s.stamp("chin", "bottom", ["ri", "ii"], {"r": "BRASS@D180", "i": "IRON@D180"})
    for face in ("left", "right"):
        s.stamp("chin", face, ["bB", "dd"], {"b": "BRASS_DARK", "B": "BRASS@L090", "d": "BRASS_DARK@D090"})
    # 躯干 front（6x8）= 搪瓷胸甲里包一颗齿轮（齿 1px，不外露）
    s.stamp("body", "front", [
        "PPPPPP",
        "BBBBBB",
        "PbddbP",
        "PdbbdP",
        "bBllBb",
        "bBllBb",
        "PdbbdP",
        "PbddbP"],
        {"P": "SHELL", "B": "BRASS@L180", "b": "BRASS", "d": "STEEL", "l": "STEEL_HI"})
    for face in ("left", "right"):
        s.stamp("body", face, [
            "PPPPPP",
            "PrpprP",
            "PpLLpP",
            "bpLLpb",
            "bpLLpb",
            "PpPPpP",
            "PrrrrP",
            "dddddd"],
            {"P": "SHELL", "p": "SHELL@D180", "r": "BRASS@D180", "L": "STEEL_HI",
             "b": "BRASS", "d": "STEEL@D180"})
    s.stamp("body", "back", [
        "ssssss",
        "sPPPPs",
        "sPnnPs",
        "sPnnPs",
        "sPPPPs",
        "bbbbbb",
        "BBBBBB",
        "pppppp"],
        {"s": "STEEL@D280", "P": "STEEL_HI", "n": "IRON@D180", "b": "BRASS@D180",
         "B": "BRASS@L180", "p": "SHELL@D280"})
    s.stamp("body", "top", [
        "ssssss",
        "sLLLLs",
        "sLrrLs",
        "sLrrLs",
        "sLLLLs",
        "ssssss"],
        {"s": "STEEL@D280", "L": "STEEL_HI", "r": "BRASS@D180"})
    s.stamp("body", "bottom", [
        "dddddd",
        "dRRRRd",
        "dRoRod",
        "dRRRRd",
        "dddddd",
        "dddddd"],
        {"d": "DEEP@D280", "R": "DEEP", "o": "BRASS@D180"})
    # 腿 = 三段黄铜阻尼支柱（四面同图，反正左右腿共用 UV）
    for face in ("front", "back", "left", "right"):
        s.stamp("leg", face, ["rrr", "rBr", "rsr", "rBr", "iii"],
                {"r": "BRASS_DARK", "B": "BRASS@L180", "s": "STEEL_HI", "i": "IRON"})
        s.px("leg", face, 0, 1, "IRON@D180")
        s.px("leg", face, 2, 3, "IRON@D180")
    s.stamp("leg", "top", ["iii", "ibi", "iii"], {"i": "IRON", "b": "BRASS@D180"})
    s.stamp("leg", "bottom", ["ddd", "dbd", "ddd"], {"d": "DEEP", "b": "BRASS@D180"})
    # 翼：薄箱 (w=1,h=4,d=6) 的两张大面是 right(24,19) 与 left(31,19)（各 6x4，翼的内外侧），
    # front/back 只是 1x4 的棱边，top/bottom 是沿翼展方向的 1x6 前后缘。
    s.stamp("wing", "right", [
        "bbbbbb",
        "pdpdpb",
        "lDlDlb",
        "dddddm"],
        {"b": "BRASS", "p": "SHELL@L180", "d": "STEEL@D180", "l": "SHELL",
         "D": "STEEL_HI", "m": "PATINA"})
    s.stamp("wing", "left", [
        "rrrrrr",
        "rdrdrd",
        "rdrdrd",
        "rrrrrm"],
        {"r": "STEEL@D280", "d": "STEEL@D340", "m": "PATINA"})
    for face in ("front", "back"):
        s.stamp("wing", face, ["B", "d", "d", "r"],
                {"B": "BRASS@L180", "d": "STEEL@D180", "r": "STEEL@D280"})
    s.stamp("wing", "top", ["b", "b", "b", "b", "b", "r"], {"b": "BRASS@L090", "r": "STEEL@D280"})
    s.stamp("wing", "bottom", ["r", "r", "m", "r", "r", "r"], {"r": "STEEL@D280", "m": "PATINA"})


PAINTERS = {"STEAM_FIREFLY": paint_firefly, "SLAG_RIDGE_HUNTER": paint_hunter, "GEAR_PIGEON": paint_pigeon}


def build(key: str) -> Skin:
    spec = MANIFEST["skins"][key]
    skin = Skin(key, spec, Roles(spec["palette_roles"]))
    PAINTERS[key](skin)
    return skin


# --------------------------------------------------------------------------- 名册对钉预检
def roster_declarations():
    """回读名册里每档的 (贴图路径, 模型类名, 画布宽, 画布高)。"""
    src = ROSTER_JAVA.read_text(encoding="utf-8")
    pat = re.compile(r"(GEAR_PIGEON|STEAM_FIREFLY|SLAG_RIDGE_HUNTER)\(\s*\"[^\"]*\",[^;]*?"
                     r"\"(?P<tex>[^\"]*)\",\s*\"(?P<mdl>[^\"]*)\",\s*(?P<w>\d+),\s*(?P<h>\d+)\)", re.S)
    out = {}
    for m in pat.finditer(src):
        out[m.group(1)] = (m.group("tex"), m.group("mdl"), int(m.group("w")), int(m.group("h")))
    return out


def verify_against_roster(built):
    decl = roster_declarations()
    lines = []
    for key, skin in built.items():
        spec = skin.spec
        got = decl.get(spec["species"])
        if got is None:
            raise SystemExit(f"名册里找不到档位 {spec['species']} —— 预检失败")
        path, model, w, h = got
        if path != spec["roster_path"]:
            raise SystemExit(f"{key}: 名册申报 {path} != entity/manifest.json 的 roster_path {spec['roster_path']}")
        if (w, h) != tuple(spec["canvas"]) or (w, h) != (skin.w, skin.h):
            raise SystemExit(f"{key}: 画布三方不符 名册 {w}x{h} / 清单 {spec['canvas']} / 实测 {skin.w}x{skin.h}")
        if model not in spec["model"]:
            raise SystemExit(f"{key}: 名册模型 {model} != 清单 {spec['model']}")
        lines.append(f"   名册对钉 {spec['species']:20s} {path} 画布={w}x{h} model={model} OK")
    return lines


def preview(skin: Skin):
    print(f"\n===== {skin.key} {skin.w}x{skin.h} =====")
    ramp = " .:-=+*#%@"
    for part, box in skin.boxes.items():
        for face in FACES:
            x, y, fw, fh = face_rect(box, face)
            print(f"-- {part}.{face} @({x},{y}) {fw}x{fh}")
            for j in range(fh):
                row = ""
                for i in range(fw):
                    p = skin.img.getpixel((x + i, y + j))
                    if p[3] == 0:
                        row += " "
                        continue
                    lum = (p[0] * 30 + p[1] * 59 + p[2] * 11) // 100
                    row += ramp[min(9, lum * 10 // 256)]
            print(f"   |{row}|".rstrip())


def assemble(skin: Skin, zoom=6):
    """正交拼装预览（游戏内口径的人眼验收图）：把每个箱体的 front / right 面按模型空间
    的未旋转位置贴回剪影。几何出处见 manifest 的 ``_geom_note``。
    这不是渲染器——没有透视、没有旋转姿态、没有光照，只回答一个问题：
    **"我画在某个 UV 矩形上的东西，落在身体的哪一面、能不能被看见"**。"""
    front = Image.new("RGBA", (1, 1), TRANSPARENT)
    parts = []
    for p in skin.spec["parts"]:
        box, geom = tuple(p["box"]), p["geom"]
        u, v, w, h, d = box
        mx, my, mz = geom
        parts.append((p["name"], box, mx, my, mz, w, h, d))
    xs = [q[2] for q in parts] + [q[2] + q[5] for q in parts]
    ys = [q[3] for q in parts] + [q[3] + q[6] for q in parts]
    zs = [q[4] for q in parts] + [q[4] + q[7] for q in parts]
    # 剪影关于 x=0 对称（有 pair 的箱会补画另一半），故 x 轴取绝对值跨度
    span = max(abs(min(xs)), max(xs))
    x0, x1, y0, y1, z0, z1 = -span, span, min(ys), max(ys), min(zs), max(zs)

    def canvas(wv, hv):
        return Image.new("RGBA", (int((wv + 2) * zoom), int((hv + 2) * zoom)), (20, 15, 19, 255))

    def paste(img, part_face, origin, dims, flip=False):
        box, face = part_face
        x, y, cw, ch = face_rect(box, face)
        src = skin.img.crop((x, y, x + cw, y + ch))
        if flip:
            src = src.transpose(Image.FLIP_LEFT_RIGHT)
        ox, oy, ow, oh = dims
        img.paste(src, (int((ox - origin[0] + 1) * zoom), int((oy - origin[1] + 1) * zoom)), src)

    fv, sv = canvas(x1 - x0, y1 - y0), canvas(z1 - z0, y1 - y0)
    for name, box, mx, my, mz, w, h, d in parts:
        paste(fv, (box, "front"), (x0, y0), (mx, my, w, h))
        paste(sv, (box, "right"), (z0, y0), (mz, my, d, h))
        # 另一半：mirror=true 的箱是水平镜像采样，"same" 的箱取同一矩形不翻转
        pair = next((pp.get("pair") for pp in skin.spec["parts"] if pp["name"] == name), None)
        if pair:
            paste(fv, (box, "front"), (x0, y0), (-(mx + w), my, w, h), flip=(pair == "mirror"))
    return fv, sv


def write_assembly(board_dir: Path):
    """每档一张：正视图 | 侧视图 | ×2 原图（同一深色底，无文字无光照）。"""
    board_dir.mkdir(parents=True, exist_ok=True)
    for key in ORDER:
        skin = build(key)
        fv, sv = assemble(skin)
        flat = skin.img.resize((skin.w * 2, skin.h * 2), Image.NEAREST)
        gap = 8
        h = max(fv.height, sv.height, flat.height) + 2 * gap
        w = fv.width + sv.width + flat.width + 4 * gap
        board = Image.new("RGBA", (int(w), int(h)), (20, 15, 19, 255))
        x = gap
        for tile in (fv, sv, flat):
            board.paste(tile, (x, gap), tile)
            x += tile.width + gap
        path = board_dir / f"assembly-{key.lower()}.png"
        board.save(path)
        a = skin.audit()
        print(f"ASSEMBLE {key:20s} 正视 {fv.size[0]}x{fv.size[1]} 侧视 {sv.size[0]}x{sv.size[1]} "
              f"色数={a['distinct_rgb_opaque']} -> {path.relative_to(ROOT)}")


BORROWED = {  # D1 之前借的原版皮（只读对照，验收"不是换个色"用）
    "STEAM_FIREFLY": "build/resources/patchedMc/assets/minecraft/textures/entity/bat.png",
    "SLAG_RIDGE_HUNTER": "build/resources/patchedMc/assets/minecraft/textures/entity/skeleton/skeleton.png",
    "GEAR_PIGEON": "build/resources/patchedMc/assets/minecraft/textures/entity/chicken.png",
}


def write_compare(board_dir: Path, zoom=4):
    """每档一张：自有皮 | D1 前借的原版皮，同尺寸同倍率同底色（回答"是不是只换了个色"）。"""
    board_dir.mkdir(parents=True, exist_ok=True)
    for key in ORDER:
        skin = build(key)
        mine = skin.img.resize((skin.w * zoom, skin.h * zoom), Image.NEAREST)
        old = Image.open(ROOT / BORROWED[key]).convert("RGBA").resize((skin.w * zoom, skin.h * zoom),
                                                                      Image.NEAREST)
        gap = 10
        board = Image.new("RGBA", (mine.width + old.width + 3 * gap, mine.height + 2 * gap), (20, 15, 19, 255))
        board.paste(mine, (gap, gap))
        board.paste(old, (2 * gap + mine.width, gap))
        path = board_dir / f"compare-{key.lower()}.png"
        board.save(path)
        print(f"COMPARE {key:20s} 左=自有 {skin.spec['file']}  右=借用的原版 {BORROWED[key].split('/')[-1]}"
              f" -> {path.relative_to(ROOT)}")


def main() -> int:
    args = [a for a in sys.argv[1:] if not a.startswith("--only")]
    only = [a.split("=", 1)[1] for a in sys.argv[1:] if a.startswith("--only")]
    keys = [k.upper() for k in only] or list(ORDER)
    for k in keys:
        if k not in MANIFEST["skins"]:
            raise SystemExit(f"未知档位 {k}（可选：{'/'.join(ORDER)}）")
    out_dir = ROOT / MANIFEST["output_dir"]
    built = {}
    if "--assemble" in args:
        write_assembly(ROOT / "plan/tmp/p3-d1")
        return 0
    if "--compare" in args:
        write_compare(ROOT / "plan/tmp/p3-d1")
        return 0
    if "--audit" not in args:
        for key in keys:
            skin = build(key)
            built[key] = skin
            if "--preview" in args:
                preview(skin)
                continue
            out_dir.mkdir(parents=True, exist_ok=True)
            data = skin.save(out_dir / skin.spec["file"])
            ihdr = (int.from_bytes(data[16:20], "big"), int.from_bytes(data[20:24], "big"))
            if ihdr != tuple(skin.spec["canvas"]):
                raise SystemExit(f"{key}: 落盘 IHDR {ihdr} != 清单申报 {skin.spec['canvas']}")
            print(f"WROTE {MANIFEST['output_dir']}/{skin.spec['file']} IHDR={ihdr[0]}x{ihdr[1]} "
                  f"sha256={hashlib.sha256(data).hexdigest()}")
            a = skin.audit()
            a["palette_hex"] = {n: "#%02X%02X%02X" % skin.roles.P(n) for n in sorted(set(a["palette_roles_used"]))}
            merge_measured({key: a})
            write_pin(skin)
            print(f"AUDIT {a['file']:26s} 画布={a['canvas'][0]}x{a['canvas'][1]} "
                  f"distinct_rgba={a['distinct_rgba']} distinct_rgb_opaque={a['distinct_rgb_opaque']} "
                  f"不透明占画布={a['opaque_ratio'] * 100:.1f}% 未画满面={a['faces_not_fully_painted'] or '无'}")
            for line in verify_against_roster({key: skin}):
                print(line)
        return 0

    audit = json.loads(MEASURED.read_text(encoding="utf-8"))
    for key in keys:
        built[key] = build(key)
        a, landed = skin_audit(built[key]), dict(audit[key])
        landed.pop("palette_hex", None)
        if a != landed:
            diff = {k: (landed.get(k), a.get(k)) for k in set(a) | set(landed) if a.get(k) != landed.get(k)}
            raise SystemExit(f"{key}: 已落盘 measured.json 与重建实测不一致 -> {json.dumps(diff, ensure_ascii=False)}")
        print(f"AUDIT-OK {key:20s} measured.json 与重建逐位一致（palette_hex 除外）")
    for key in keys:
        a = audit[key]
        print(f"AUDIT {a['file']:26s} 画布={a['canvas'][0]}x{a['canvas'][1]} "
              f"distinct_rgba={a['distinct_rgba']} distinct_rgb_opaque={a['distinct_rgb_opaque']} "
              f"不透明占画布={a['opaque_ratio'] * 100:.1f}% 未画满面={a['faces_not_fully_painted'] or '无'}")
    for line in verify_against_roster(built):
        print(line)
    return 0


def skin_audit(skin: Skin) -> dict:
    a = skin.audit()
    a.pop("palette_hex", None)
    return a


def write_pin(skin: Skin):
    """逐张增量落盘的配套：只更新本档那一行 sha，别的行字面不动（与方块域 landed.sha256 同格式）。"""
    pin = ROOT / "tools/artgen/entity/landed.sha256"
    data = (ROOT / MANIFEST["output_dir"] / skin.spec["file"]).read_bytes()
    line = f"{skin.spec['file']} {hashlib.sha256(data).hexdigest()}"
    rows = {}
    if pin.exists():
        for raw in pin.read_text(encoding="utf-8").splitlines():
            if raw.strip():
                name, _, sha = raw.partition(" ")
                rows[name] = sha
    rows[skin.spec["file"]] = line.split(" ", 1)[1]
    pin.write_text("".join(f"{k} {rows[k]}\n" for k in sorted(rows)), encoding="utf-8")


def merge_measured(new: dict):
    """逐张增量落盘的配套：只并入/覆盖本档的实测，别的不碰。"""
    cur = json.loads(MEASURED.read_text(encoding="utf-8")) if MEASURED.exists() else {}
    for k, v in new.items():
        v.pop("sampled_face_coverage", None) if False else None
        cur[k] = v
    MEASURED.write_text(json.dumps({k: cur[k] for k in ORDER if k in cur}, ensure_ascii=False, indent=1) + "\n",
                        encoding="utf-8")


if __name__ == "__main__":
    sys.exit(main())
