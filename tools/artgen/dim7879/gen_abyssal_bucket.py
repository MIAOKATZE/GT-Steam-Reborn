#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""gen_abyssal_bucket.py — 深渊执念桶物品贴图一次性生成器（v1.20.40 P19-U1，plan §I）。

定位：一次性生成、留档备查，<b>不进</b> manifest.json 幂等链（dim7879 主链=方块 32px 域，
本件是 16px 物品域孤件；任务包拍板：手写 PNG 不合仓内纪律 ⇒ 脚本生成一次并留档）。
风格：深蓝液面 + 灰金属桶身 + 提梁弧，全部手工像素表（零伪随机、零 IO 依赖单文件）。

确定性纪律（对齐 gen_dim7879_blocks.py 同域要求）：
- 零随机源/零时间源（纯查表绘制，无 random/time/datetime 导入）；
- 双跑对拍：build() 独立构建两遍逐字节比对，PNG 字节不一致即 AssertionError；
- 落盘后回读字节核验 + PNG IHDR 断言（16x16 / 8bpc / RGBA）；
- 像素纪律：distinct RGB ≤ 16、无纯黑 #000000/纯白 #FFFFFF、背景 alpha 恒 0（物品域惯例）；
- 脚本自身幂等：同输入重复运行产物字节恒等（sha256 侧车留档，供复核）。

产物：
- src/main/resources/assets/gtsr/textures/items/AbyssalObsessionBucket.png（16x16 RGBA）
- tools/artgen/dim7879/gen_abyssal_bucket.sha256（sha256 侧车）

用法：python tools/artgen/dim7879/gen_abyssal_bucket.py（任意 cwd；路径相对本文件定位）
"""

import hashlib
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[3]
OUT_PATH = ROOT / "src" / "main" / "resources" / "assets" / "gtsr" / "textures" / "items" / "AbyssalObsessionBucket.png"
SIDECAR_PATH = Path(__file__).resolve().parent / "gen_abyssal_bucket.sha256"

SIZE = 16
TRANSPARENT = (0, 0, 0, 0)

# 调色板（7 色，≤16 纪律；液体取材料色族：0x2E5F8A 普通档 / 0x234A6B 材料档的邻近档）
OUTLINE = (40, 44, 48, 255)        # 桶身轮廓/提梁
METAL_LIGHT = (176, 182, 188, 255)  # 金属亮面（左壁高光）
METAL_MID = (134, 140, 148, 255)    # 金属主面
METAL_DARK = (94, 100, 108, 255)    # 金属暗面（右壁阴影）
LIQUID_BRIGHT = (74, 132, 178, 255)  # 液面高光
LIQUID_MAIN = (46, 95, 138, 255)    # 液面主色 = 0x2E5F8A
LIQUID_DEEP = (35, 74, 107, 255)    # 液面深色 = 0x234A6B


def build() -> Image.Image:
    """手工像素表绘制 16x16 桶图标：提梁弧 + 外扩梯形桶身 + 顶部两行液面。"""
    px = [[TRANSPARENT for _ in range(SIZE)] for _ in range(SIZE)]

    # 提梁弧（y=1 顶弧 + y=2/3 两臂，落点接桶沿两角）
    for x in range(5, 11):
        px[1][x] = OUTLINE
    px[2][4] = OUTLINE
    px[2][11] = OUTLINE
    px[3][3] = OUTLINE
    px[3][12] = OUTLINE

    # 桶身：y=4..14 线性收腰，left 3→5 / right 12→10；y=4 整行桶沿横杆
    for y in range(4, 15):
        t = (y - 4) * 2 // 10
        left, right = 3 + t, 12 - t
        for x in range(left, right + 1):
            if x == left or x == right or y == 14 or y == 4:
                px[y][x] = OUTLINE
            elif y <= 6:
                # 液面两行（5-6，贴沿下）：亮带在左 1/3（受光），余主色，右角点深色
                if y == 5 and x <= left + 2:
                    px[y][x] = LIQUID_BRIGHT
                elif x >= right - 1:
                    px[y][x] = LIQUID_DEEP
                else:
                    px[y][x] = LIQUID_MAIN
            else:
                # 金属桶身：左壁亮带 + 右壁暗带，其余主面
                if x <= left + 1 and y <= 11:
                    px[y][x] = METAL_LIGHT
                elif x >= right - 1:
                    px[y][x] = METAL_DARK
                else:
                    px[y][x] = METAL_MID

    img = Image.new("RGBA", (SIZE, SIZE))
    img.putdata(tuple(px[r][c] for r in range(SIZE) for c in range(SIZE)))
    return img


def self_check(img: Image.Image) -> None:
    """断言：尺寸/模式/不透明像素色数/禁色/背景透明。"""
    assert img.size == (SIZE, SIZE), f"size={img.size}"
    assert img.mode == "RGBA", f"mode={img.mode}"
    data = list(img.getdata())
    opaque = [p[:3] for p in data if p[3] == 255]
    distinct = set(opaque)
    assert len(distinct) <= 16, f"distinct RGB={len(distinct)}"
    assert (0, 0, 0) not in distinct and (255, 255, 255) not in distinct, "forbidden pure black/white"
    for r in range(SIZE):
        assert px_alpha(img, 0, r) in (0, 255) and px_alpha(img, 15, r) in (0, 255)
    assert all(p[3] == 0 for p in data[0:16]), "top row must stay background"


def px_alpha(img: Image.Image, x: int, y: int) -> int:
    return img.getpixel((x, y))[3]


def main() -> int:
    a, b = build(), build()
    assert a.tobytes() == b.tobytes(), "double-run byte mismatch"
    self_check(a)

    OUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    a.save(OUT_PATH, "PNG")

    raw = OUT_PATH.read_bytes()
    assert raw == OUT_PATH.read_bytes(), "re-read mismatch"
    # IHDR 断言：宽16 高16 位深8 色型6(RGBA)，偏移：8(sig) +4(len) +4(IHDR) → 16..29
    assert raw[16:20] == (16).to_bytes(4, "big") and raw[20:24] == (16).to_bytes(4, "big"), "IHDR size"
    assert raw[24] == 8 and raw[25] == 6, "IHDR bitdepth/colortype"
    # 回读核验与内存位图逐字节一致
    assert Image.open(OUT_PATH).convert("RGBA").tobytes() == a.tobytes(), "roundtrip mismatch"

    sha = hashlib.sha256(raw).hexdigest()
    SIDECAR_PATH.write_text(f"{sha}  {OUT_PATH.name}\n", encoding="utf-8")
    print(f"OK {OUT_PATH.name} sha256={sha} bytes={len(raw)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
