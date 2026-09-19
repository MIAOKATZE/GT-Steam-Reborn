#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""make_art32_samples.py — P10a 判据 5：32×32 代表样 + 16vs32 并排对比板（审阅产物，不入库）。

产物全部写到 plan/新维度计划/review/artgen32/（.gitignore 的 /plan/ 之下）：
  <key>_16.png        母版 16×16（从 src/main/resources **只读复制**，本片不写回任何在产路径）
  <key>_32.png        32×32 候选（走生成器生产断言全绿后才取图）
  compare_16v32.png   并排板：母版 16 / 最近邻放大到 32（反面教材）/ 32 重绘 / 32 三三拼接
  samples.md          逐张：母体路径、32 路径、这一档改了什么配方、实测指标

关键纪律：
  · 32 档样张一律经 `build_all()` 的**生产门槛**（色数 ≤16、alpha 档、接缝阈值 x2、双跑幂等、回读字节）
    后再取图，不在本脚本里自建一套"看起来等价"的检查；
  · manifest 之外的试点增量（如细颗粒倍频）只在本脚本的内存里生效，不改 manifest、不改在产资源；
  · 不写 src/main/resources/**。
"""

import hashlib
import importlib.util
import io
import json
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[3]
OUT_DIR = ROOT / "plan" / "新维度计划" / "review" / "artgen32"
ASSET_DIR = ROOT / "src" / "main" / "resources" / "assets" / "gtsr" / "textures" / "blocks"
BATCHES = {
    "dim7879": "tools/artgen/dim7879/gen_dim7879_blocks.py",
    "dim1": "tools/artgen/dim1/gen_dim1_blocks.py",
}
BG = (18, 20, 24, 255)
FG = (232, 228, 216, 255)
DIM = (150, 146, 134, 255)
HDR = (214, 208, 188, 255)
WARN = (214, 168, 96, 255)
CHK_A = (34, 37, 42, 255)
CHK_B = (24, 26, 30, 255)

# 选样覆盖：六类互不相同的像素语言（表层顶面 / 表层基面 / 壳沿侧面 / 十字装饰 / 硬石棱面 /
# 残骸构件 / 机制壳 / 植被冠层）——不是挑八张同类。
SAMPLES = (
    dict(batch="dim7879", key="prosperity_steppe_top", en='blade len x2 / blade density /4; speckle density kept; field wavelength kept', cover="dim78 表层 top",
         recipe="草叶长 ×2、布点密度 ÷4（R2 离散笔刷）；单像素斑点密度不变（R3）；场波长与色阶不动（R1）"),
    dict(batch="dim7879", key="prosperity_steppe_base", en='grit+flake kept 1px (coverage rule); trial: extra cells=32 octave w=0.09', cover="dim78 表层 base",
         delta=dict(fine_octave=0.10),
         recipe="基场地表：噪声波长保持（R1，特征世界尺寸不变、像素 ×4）；grit/flake 单像素点密度不变（R3）"
                "；【32 档试点】追加一档 cells=32 细颗粒倍频（权重归一后 0.09）——16 档下该档已退化成一格一像素的盐噪"),
    dict(batch="dim7879", key="prosperity_swamp_top_side", en='crust fringe 2-4px -> 4-8px (silhouette band xR); drip/shadow stay 1px hairline', cover="dim78 壳沿侧面",
         recipe="壳沿厚度 2-4px → 4-8px（R4 轮廓级条带 ×R）；沿下投影/垂落仍 1px 发丝线；土体与母版同画法"),
    dict(batch="dim7879", key="prosperity_tuft_copper", en='tuft height x2, blade width x2, bend pitch x3R; fg/bottom-row gates xR2/xR', cover="装饰（十字苔簇）",
         recipe="苔簇高度 ×2、叶宽 ×2、弯折节距 ×3R（R2/R4）；前景像素下限 ×R²、底行生根下限 ×R（R6）；"
                "覆盖率是比率 ⇒ 不动"),
    dict(batch="dim7879", key="shattered_corestone", en='facet cells kept (world size); joints+lit edges stay 1px; vein length x2', cover="dim79 硬方块",
         recipe="棱面格点数不动（R1 ⇒ 分面世界尺寸不变）；1px 深缝与受光棱仍 1px（R4 发丝线）；"
                "矿脉长度 ×2、条数不动；凹坑点密度不变"),
    dict(batch="dim1", key="ruin_debris_pipe", en='flange 2px->4px, rivets 2x2->3x3, shading profile expanded; mold line 1px', cover="废墟/残骸方块",
         recipe="管体明暗剖面按 R 最近邻展开；两端法兰 2px→4px、道钉/铆钉 2×2→3×3（R4 凸起件）；"
                "体内分模线仍 1px；锈斑半径 ×2、锈点密度不变"),
    dict(batch="dim1", key="ruined_casing_rusted", en='casing frame stays 1px (hairline); rivets 2x2->3x3 at x2; chip radius x2', cover="机器感 casing",
         recipe="机壳框 1px 不动（发丝线纪律，32 档读作更锐的边界）；铆钉 2×2→3×3 且位置 ×2（凸起件）；"
                "漆面剥落斑半径 ×2、布点范围 ×2；锈屑/碎片单像素点密度不变"),
    dict(batch="dim7879", key="prosperity_rust_leaves", en='canopy field kept; holes+needle tips stay 1px at same density (finer in world)', cover="草/苔一类植被",
         recipe="冠层场波长不动（R1）；暗孔/亮针仍 1px 且密度不变（R3 覆盖率守恒、颗粒变细）；"
                "乘色 tint 口径与母版一致（色数上限 16 与分辨率无关）"),
)


def load(batch):
    path = ROOT / BATCHES[batch]
    spec = importlib.util.spec_from_file_location("art_" + batch, str(path))
    mod = importlib.util.module_from_spec(spec)
    sys.modules["art_" + batch] = mod
    spec.loader.exec_module(mod)
    return mod


def manifest_of(batch):
    return json.loads((ROOT / "tools" / "artgen" / batch / "manifest.json").read_text(encoding="utf-8"))


def resolve_params(mod, man, entry):
    style = man["STYLE"]
    by_key = {e["key"]: e for e in man["OUTPUTS"]}
    if hasattr(mod, "_resolve_params"):
        return mod._resolve_params(entry, by_key, style)
    params = dict(entry["params"])
    for slot, ref_key in entry.get("params_from", {}).items():
        params[slot] = by_key[ref_key]["params"]
    return params


def apply_delta(mod, man, entry, params, delta):
    """样张试点增量：目前只有 fine_octave（在 32 档追加一档 cells=32 的细颗粒倍频）。"""
    if not delta:
        return params, "-"
    if "fine_octave" in delta:
        base = params.get("octaves") or mod.octaves_of(params)
        base = [[int(c), float(w)] for c, w in base]
        total = sum(w for _, w in base) + float(delta["fine_octave"])
        base = [[c, w / total] for c, w in base]
        base.append([32, float(delta["fine_octave"]) / total])
        params["octaves"] = base
        return params, "octaves += [32, %.3f]（权重归一）" % (float(delta["fine_octave"]) / total)
    return params, "?"


def needed_keys(man, keys):
    """闭包展开：样张条目 + 其 params_from 引用链（crust_side / grass_side 复用同族 base 的调色板）。"""
    by_key = {e["key"]: e for e in man["OUTPUTS"]}
    out, todo = set(), list(keys)
    while todo:
        key = todo.pop()
        if key in out or key not in by_key:
            continue
        out.add(key)
        todo.extend(by_key[key].get("params_from", {}).values())
    return out


def render(mod, man, key, size, delta):
    """返回 (RGBA Image, 指标 dict)。走 build_all 生产门槛（色数 / alpha / 接缝 x2 阈值 / 双跑幂等）后再取图。"""
    entry = [e for e in man["OUTPUTS"] if e["key"] == key][0]
    sub = dict(man)
    need = needed_keys(man, [key])
    sub["OUTPUTS"] = [e for e in man["OUTPUTS"] if e["key"] in need]
    original = mod.PAINTERS[entry["kind"]]
    captured = {}

    def spy(r, seed, p):
        params, note = apply_delta(mod, sub, entry, dict(p), delta)
        tex = original(r, seed, params)
        captured[key] = (tex.image(), note)
        return tex

    mod.set_size(size)
    mod.PAINTERS[entry["kind"]] = spy
    name = "blocks/" + entry["file"]
    try:
        first, meta = mod.build_all(sub)
        second, _ = mod.build_all(sub)                 # 样张自身也做一次双跑字节比对
        if first[name] != second[name]:
            raise AssertionError("样张双跑字节不一致: " + key)
    finally:
        mod.PAINTERS[entry["kind"]] = original
    item = [m for m in meta if m["key"] == key][0]
    im, note = captured[key]
    return im, dict(rgb=item["colors"],
                    gross=(("%.3f,%.3f" % (item["seam"]["col_gross"], item["seam"]["row_gross"]))
                           if item["seam"] else "-"),
                    tileable=item["tileable"], axes=item["axes"], delta=note,
                    sha=hashlib.sha256(first[name]).hexdigest()[:16])


def nearest(im, factor):
    return im.resize((im.width * factor, im.height * factor), Image.NEAREST)


def checker(size, a, b, block=8):
    im = Image.new("RGBA", (size, size), a)
    p = im.load()
    for y in range(size):
        for x in range(size):
            if ((x // block) + (y // block)) % 2 == 0:
                p[x, y] = b
    return im


def paste_cell(board, draw, x0, y0, im, caption, zoom_factor, warn=False):
    tile = checker(128, CHK_A, CHK_B) if (im.mode == "RGBA" and any(im.getpixel((x, y))[3] == 0 for x, y in ((0, 0), (1, 1)))) else None
    art = nearest(im, zoom_factor)
    if tile is not None:
        tile.alpha_composite(art)
        art = tile
    draw.text((x0, y0), caption, font=ImageFont.load_default(), fill=WARN if warn else DIM)
    board.paste(art, (x0, y0 + 14))


def build_board(rows, size32_note):
    cols = ("master 16 @8x", "16 -> 32 nearest (NOT shipped)", "32 redraw @4x", "32 tiling 3x3 @1x")
    label_w, cell_w, cell_h, top = 250, 146, 172, 60
    board = Image.new("RGBA", (label_w + len(cols) * cell_w + 24, top + len(rows) * cell_h + 30), BG)
    draw = ImageDraw.Draw(board)
    font = ImageFont.load_default()
    draw.text((12, 8), "P10a artgen 32x32 candidate samples vs master 16  --  %s" % size32_note, font=font, fill=HDR)
    draw.text((12, 22), "col2 = 16 master nearest-upscaled to 32 (BAD EXAMPLE, not shipped); col3 = 32 redraw by rule; "
                        "col4 = 3x3 tiling check. All 32 samples pass the production gates "
                        "(distinct RGB <=16 / seam_gross_max_x2 / double-run idempotent).",
              font=font, fill=DIM)
    y = top
    for x, name in enumerate(cols):
        draw.text((label_w + 12 + x * cell_w, y - 16), name, font=font, fill=HDR)
    for row in rows:
        draw.text((12, y + 2), "%s" % row["key"], font=font, fill=FG)
        draw.text((12, y + 16), "kind=%s rgb=%d gross=%s" % (row["kind"], row["rgb32"], row["gross32"]),
                  font=font, fill=DIM)
        draw.text((12, y + 28), "tile=%s axes=%s sha16=%s" % (row["tileable"], row["axes"], row["sha32"]),
                  font=font, fill=DIM)
        draw.text((12, y + 40), "32 recipe:", font=font, fill=DIM)
        recipe = row["en"]
        for i in range(0, len(recipe), 44):
            draw.text((12, y + 52 + (i // 44) * 11), recipe[i:i + 44], font=font, fill=FG)
        paste_cell(board, draw, label_w + 12, y, row["im16"], "16", 8)
        paste_cell(board, draw, label_w + 12 + cell_w, y, nearest(row["im16"], 2), "@32 nearest", 4, warn=True)
        paste_cell(board, draw, label_w + 12 + 2 * cell_w, y, row["im32"], "32", 4)
        tiled = Image.new("RGBA", (96, 96), (0, 0, 0, 0))
        for i in range(3):
            for j in range(3):
                tiled.paste(nearest(row["im32"], 1), (i * 32, j * 32))
        paste_cell(board, draw, label_w + 12 + 3 * cell_w, y, tiled, "tile 3x3", 1)
        y += cell_h
    return board


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    mods = {b: load(b) for b in set(s["batch"] for s in SAMPLES)}
    rows = []
    listing = []
    for spec in SAMPLES:
        batch, key = spec["batch"], spec["key"]
        mod, man = mods[batch], manifest_of(batch)
        entry = [e for e in man["OUTPUTS"] if e["key"] == key][0]
        im16_src = ASSET_DIR / entry["file"]
        im16_bytes = im16_src.read_bytes()
        im16 = Image.open(io.BytesIO(im16_bytes)).convert("RGBA")
        im32, info = render(mod, man, key, 32, spec.get("delta"))
        (OUT_DIR / ("%s_16.png" % key)).write_bytes(im16_bytes)
        buf = io.BytesIO()
        im32.save(buf, "PNG", optimize=True)
        (OUT_DIR / ("%s_32.png" % key)).write_bytes(buf.getvalue())
        rows.append(dict(key=key, cover=spec["cover"], en=spec["en"], kind=entry["kind"], recipe=spec["recipe"],
                         im16=im16, im32=im32, rgb32=info["rgb"], gross32=info["gross"],
                         tileable=info["tileable"], axes=info["axes"], sha32=info["sha"],
                         delta=info["delta"], file=entry["file"],
                         size16=len(im16_bytes), size32=len(buf.getvalue())))
        listing.append("  [OK] %-30s %s  rgb=%d gross=%s" % (key, entry["file"], info["rgb"], info["gross"]))
        print(listing[-1])
    board = build_board(rows, "SIZE=32 (R=2) 档")
    board_bytes = io.BytesIO()
    board.save(board_bytes, "PNG", optimize=True)
    (OUT_DIR / "compare_16v32.png").write_bytes(board_bytes.getvalue())
    write_index(rows)
    print("[BOARD] compare_16v32.png %d B 尺寸 %s" % (len(board_bytes.getvalue()), board.size))
    print("[OUT] %s 样张 %d 组（16/32 各一张）+ 并排板 + samples.md" % (OUT_DIR, len(rows)))
    return 0


def write_index(rows):
    lines = [
        "# P10a 32×32 代表样（审阅用，不入库）",
        "",
        "- 生成方式：`python tools/artgen/checks/make_art32_samples.py`（幂等；32 档走生成器生产断言）",
        "- 母体 16 档来源：`src/main/resources/assets/gtsr/textures/blocks/`（**本片只读复制，未写回**）",
        "- 32 档口径：见 `tools/artgen/dim7879/gen_dim7879_blocks.py` 文件头 R1..R6（不是最近邻放大）",
        "- 并排板：`compare_16v32.png`（第 2 列 = 把 16 档最近邻放大到 32 的反面教材，未纳入交付）",
        "",
        "| # | 覆盖 | 条目 | kind | 母体 16（在产路径） | 32 样张 | 实测 rgb / 接缝 gross | 双跑 SHA(前16) |",
        "|---|---|---|---|---|---|---|---|",
    ]
    for i, r in enumerate(rows, 1):
        lines.append("| %d | %s | `%s` | `%s` | `%s` | `%s_32.png` (%d B) | rgb=%d gross=%s | `%s` |"
                     % (i, r["cover"], r["key"], r["kind"], r["file"], r["key"], r["size32"],
                        r["rgb32"], r["gross32"], r["sha32"]))
    lines += ["", "## 逐张配方增量（32 档相对母版改了什么）", ""]
    for i, r in enumerate(rows, 1):
        lines.append("%d. `%s`（%s）" % (i, r["key"], r["cover"]))
        lines.append("   - 母体：`src/main/resources/assets/gtsr/textures/blocks/%s`（16×16，%d B，字节未动）"
                     % (r["file"], r["size16"]))
        lines.append("   - 32 样张：`plan/新维度计划/review/artgen32/%s_32.png`（32×32，%d B）"
                     % (r["key"], r["size32"]))
        lines.append("   - 配方：%s" % r["recipe"])
        lines.append("   - 内存试点参数增量（未写回 manifest）：`%s`" % r["delta"])
    lines += ["", "## 与『逐张手绘重画』的差别", ""]
    lines.append(
        "本片是**规则级重定**：同一 manifest、同一调色板、同一套画笔，只是把『随尺度变化的量』显式改成随 SIZE 走"
        "（波长不动、笔刷长宽 ×R、笔刷布点 ÷R²、单像素点密度不动、发丝线不加倍、轮廓条带 ×R、按像素计数的门槛 ×R/×R²）。"
        "所以 8 张样张仍是同一族地表的『同一构图』，不是重画构图；差别在于 32 档现在能把母版里被 1 像素强制糊掉的"
        "边界、发丝线、细颗粒分开画。逐张手绘重画是**构图级**改造（重新安排锈斑/棱面/管段的位置与叙事、"
        "为每个方块单独决定视觉重心），属 P10b 之后的可选美术投入，不在本管线改造的判据里。")

    (OUT_DIR / "samples.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


if __name__ == "__main__":
    raise SystemExit(main())
