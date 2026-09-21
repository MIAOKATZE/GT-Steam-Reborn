# -*- coding: utf-8 -*-
"""
make_preview32.py — 片 C1 第 5 项：把 review/dim7879/textures/preview.png 从 16 档历史板重出为 32 档整板。
仓内档案件（P16 主代理自 B5b 并行期收回 `plan/tmp/p3-c1/`）：本板是入库目检件，生成脚本必须与它同仓，
否则预览板一旦需要重出就没有依据（P16 已两次因"设计权威留在不入库的临时区"返工）。

为什么不用 `gen_dim7879_blocks.py --size 32` 直接出板：那条链的像素来自**生成器自己的 16 档母版配方**，
而 P16-A1a/A1b 在 32 档上加了手绘细节层（R6）与配色收敛闸，其权威是
tools/artgen/dim7879/draw32_dim78.py / draw32_dim79.py + design32.json（A4 回写件，且 A4 明确没跑过
--apply 落盘）。拿生成器像素出整板 = 展示一张"世界上不存在的贴图板"，那是目检件最坏的假绿。
⇒ 本脚本的像素一律读 **src/main/resources 磁盘实字节**（= 交付物本身，与 landed.sha256 同一批），
  版面与指标口径全部**复用仓内既有生成器的同一批函数**（build_board / seam_metrics / distinct_rgb /
  zoom / _nearest / _tiled / _checker），不复制第二套画法或阈值。

scope 与旧板一致 = dim7879 那一批 35 张（manifest.OUTPUTS）；S7b 基线的另 14 张属 tools/artgen/dim1
批，其改前/改后对照板在 A2 的 review 目录（由 plan/p16-overview.html 指路），本板不越界扩。

用法：python tools/artgen/dim7879/make_preview32.py [输出路径]
      双跑：连跑两次比对 SHA256（本脚本自带 --twice 直接打印两次读数）。
      ROOT 的 parents[3] 推导对 plan/tmp/p3-c1/ 与 tools/artgen/dim7879/ 两个位置同为仓根，迁入不改该行。
"""
import hashlib
import importlib.util
import io
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
GEN_PATH = ROOT / "tools" / "artgen" / "dim7879" / "gen_dim7879_blocks.py"
ASSET_DIR = ROOT / "src" / "main" / "resources" / "assets" / "gtsr" / "textures" / "blocks"
MAN_PATH = ROOT / "tools" / "artgen" / "dim7879" / "manifest.json"
SIDECAR = ROOT / "tools" / "artgen" / "dim7879" / "landed.sha256"
DEFAULT_OUT = ROOT / "plan" / "维度计划" / "设计册与实施计划" / "review" / "dim7879" / "textures" / "preview.png"

_spec = importlib.util.spec_from_file_location("gen7879_c1", str(GEN_PATH))
G = importlib.util.module_from_spec(_spec)
sys.modules["gen7879_c1"] = G
_spec.loader.exec_module(G)

SIZE = 32
G.set_size(SIZE)  # 母版 16 → R=2；build_board 的 zoom(8)/zoom(2)/zoom(4) 自动降倍 ⇒ 版面尺寸与旧板同量级


def sidecar_hashes():
    out = {}
    for line in io.open(str(SIDECAR), encoding="utf-8").read().splitlines():
        parts = line.split()
        if len(parts) == 2:
            out[parts[0]] = parts[1]
    return out


def build_meta():
    man = json.load(io.open(str(MAN_PATH), encoding="utf-8"))
    style = man["STYLE"]
    by_key = {e["key"]: e for e in man["OUTPUTS"]}
    max_colors = int(style["pixel_discipline"]["max_distinct_rgb"])
    forbid = tuple(G._c(x)[:3] for x in style["pixel_discipline"]["forbid_rgb"])
    cross_rules = style["cross_rules"]
    side = sidecar_hashes()
    meta = []
    for entry in man["OUTPUTS"]:
        key = entry["key"]
        fname = entry["file"]
        path = ASSET_DIR / fname
        raw = path.read_bytes()
        # 磁盘字节 == landed.sha256（asset_baseline_check 的三点核对同一条），不等就说明板子画错了东西
        assert side.get(fname) == hashlib.sha256(raw).hexdigest(), "%s 磁盘字节与 landed.sha256 不符" % key
        im = G.Image.open(io.BytesIO(raw))
        im.load()
        assert im.size == (SIZE, SIZE), "%s 尺寸 %s != %dx%d" % (key, im.size, SIZE, SIZE)
        kind = entry["kind"]
        is_cross = kind in G.CROSS_KINDS
        px = im.load()
        extra = {}
        if is_cross:
            fg = bg = 0
            for y in range(SIZE):
                for x in range(SIZE):
                    p = px[x, y]
                    if p[3] == 0:
                        assert p == G.TRANSPARENT, "%s 背景像素非 (0,0,0,0)" % key
                        bg += 1
                    elif p[3] == 255:
                        fg += 1
                    else:
                        raise AssertionError("%s alpha 非二值 @%d,%d=%d" % (key, x, y, p[3]))
            extra = {"coverage": fg / float(SIZE * SIZE), "bottom_fg": sum(1 for x in range(SIZE) if px[x, SIZE - 1][3] == 255)}
            assert bg > 0, "%s 无透明背景" % key
            assert float(cross_rules["coverage_min"]) <= extra["coverage"] <= float(cross_rules["coverage_max"]), \
                "%s 十字覆盖率 %.3f 越界" % (key, extra["coverage"])
            assert extra["bottom_fg"] >= int(cross_rules["bottom_row_fg_min"]) * G.R, "%s 未落地生根" % key
        else:
            for y in range(SIZE):
                for x in range(SIZE):
                    assert px[x, y][3] == 255, "%s 存在非不透明像素" % key
        colors = G.distinct_rgb(im, skip_transparent=is_cross)
        assert len(colors) <= max_colors, "%s 色数 %d > %d" % (key, len(colors), max_colors)
        for rgb in colors:
            assert rgb not in forbid, "%s 使用极值色 %s" % (key, rgb)
        axes = str(entry.get("seam_axes", "xy"))
        ratio = G.seam_metrics(im) if entry.get("tileable") else None
        meta.append({
            "key": key,
            "file": fname,
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
        })
    return meta, man


def board_bytes():
    meta, man = build_meta()
    return G._png_bytes(G.build_board(meta, man)), len(meta)


def main(argv):
    twice = "--twice" in argv
    outs = [Path(a) for a in argv if not a.startswith("--")]
    out = outs[0] if outs else DEFAULT_OUT
    data, n = board_bytes()
    sha = hashlib.sha256(data).hexdigest()
    print("[BOARD] 条目=%d 尺寸=%s 字节=%d SHA256=%s" % (n, G.Image.open(io.BytesIO(data)).size, len(data), sha))
    if twice:
        data2, _ = board_bytes()
        sha2 = hashlib.sha256(data2).hexdigest()
        print("[BOARD] 双跑（同进程第二次构建）SHA256=%s -> %s" % (sha2, "逐字节一致" if sha2 == sha else "不一致"))
        assert sha2 == sha and data2 == data, "双跑不一致 ⇒ 版面非确定"
    if not argv or "--write" in argv or outs:
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_bytes(data)
        assert out.read_bytes() == data, "回读字节不一致"
        print("[WROTE] %s" % out)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
