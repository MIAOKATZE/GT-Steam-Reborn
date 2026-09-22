#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""draw32_dim78.py — 片 A1a（原文件名 gen_a1a.py，P16-A4 由 plan/tmp 回写进仓）：片 A1a：dim78 半边 17 张 16×16 → 32×32 重绘（逐张增量落盘）。

P17-SB1 增补（v1.20.38 轮）：dim78 半边扩到 35 张 = 原 17 + 木 3 档 12 张（copper/brass/marsh 的
log_side/log_top/leaves_side/leaves_top）+ 自有花 4 张（新 flower() 族形）+ 新草 2 张（复用 tuft() 两形态）。
新 18 张全部走本文件（32 档直画、无 DRY 档、直写 src/ 的既有纪律不变）；既 17 张的字节不受影响
（同一批 builder 与同一批 manifest 参数，双跑逐张 SHA 对拍为证）。

v1.20.39 T2 增补（plan §3.7 地底石化）：再 +1 张 prosperity_stone（半边 36 张）——地底 wholeBody 主体石，
新 builder stone_ruin()（grit_base 同骨架 + 锈染短缝/锈点层），母版参数与种子在 manifest.json
（PALETTE 锚 PROSPERITY_STONE / SEED 0x5EED8151）。既 35 张的 builder 与参数逐字未改。

风格锚底座 = 仓内既有管线 tools/artgen/dim7879/gen_dim7879_blocks.py 的原语与 R1-R6 口径
（Rules/Tex/h01/tile_noise/scatter/paint_blob/各 kind 画法，经 importlib 复用，不复制色值）。
本文件只加两样：① 32 档才吃得下的手绘细节层（R6）；② 配色收敛闸（越线项折近重复色）。

纪律：
- 零硬编码色值：一切色经 Rules.resolve('NAME@TOKEN') 从 manifest PALETTE + DERIVED_RULES 派生；
- 画完一张立刻写该 .png；
- 落盘前自检：32×32 / distinct RGB ≤16 / 十字 alpha 二值 + 覆盖率 + 落地生根 / 禁纯黑白 / 硬缝 gross 读数；
- 受保护色（闪点、锈屑、渗点等设计性稀疏色）永不参与收敛。

用法：python tools/artgen/dim7879/draw32_dim78.py <key ...> | --all

P16（v1.20.37）落档事实：本文件是 dim78 17 张的 32 档绘制器与逐张理由权威（RATIONALE 表）。
仓内逐张档案 = 同目录 design32.json；16 档母版参数仍由同目录 manifest.json + gen_dim7879_blocks.py
承载（本文件只加 32 档手绘层与收敛闸，不改母版）。在产字节以同目录 landed.sha256 为准，
重钉命令：bash tools/dim1/asset_baseline_check.sh --write-sidecars。
"""
import importlib.util
import json
import math
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]   # P16-A4 回写仓内：plan/tmp 副本里这里是绝对路径
GEN_PATH = ROOT / "tools/artgen/dim7879/gen_dim7879_blocks.py"
ASSET_DIR = ROOT / "src/main/resources/assets/gtsr/textures/blocks"

_spec = importlib.util.spec_from_file_location("gen7879_a1a", str(GEN_PATH))
G = importlib.util.module_from_spec(_spec)
sys.modules["gen7879_a1a"] = G
_spec.loader.exec_module(G)
G.set_size(32)

MAN = json.load(open(str(ROOT / "tools/artgen/dim7879/manifest.json"), encoding="utf-8"))
BY_KEY = {e["key"]: e for e in MAN["OUTPUTS"]}
RULES = G.Rules(MAN)
STYLE = MAN["STYLE"]
SIZE = 32
TAU = math.pi * 2.0
MAX_COLORS = 16
FORBID = ((0, 0, 0), (255, 255, 255))
TRANSPARENT = (0, 0, 0, 0)

KEYS = """prosperity_steppe_top prosperity_steppe_top_side prosperity_steppe_base
prosperity_forest_top prosperity_forest_top_side prosperity_forest_base
prosperity_wastes_top prosperity_wastes_top_side prosperity_wastes_base
prosperity_swamp_top prosperity_swamp_top_side prosperity_swamp_base
prosperity_tuft_rust prosperity_tuft_copper
prosperity_rust_log_side prosperity_rust_log_top prosperity_rust_leaves
prosperity_copper_log_side prosperity_copper_log_top
prosperity_copper_leaves_side prosperity_copper_leaves_top
prosperity_brass_log_side prosperity_brass_log_top
prosperity_brass_leaves_side prosperity_brass_leaves_top
prosperity_marsh_log_side prosperity_marsh_log_top
prosperity_marsh_leaves_side prosperity_marsh_leaves_top
prosperity_flower_rust prosperity_flower_patina prosperity_flower_brass prosperity_flower_marsh
prosperity_tuft_sedge prosperity_tuft_bristle
prosperity_silica_sand prosperity_coarse_sand prosperity_river_gravel
prosperity_stone""".split()

RATIONALE = {
 "prosperity_steppe_top": "16 档草叶是均匀撒点 ⇒ 32 档改成簇生（8×8 格位扎 2-4 根 1px 叶 + 1px 亮尖），另用 cells=8/4 低频斑块把基色整体推 ±1 阶：草皮成坨，而不是同一张噪点。",
 "prosperity_steppe_top_side": "草裙按 R4 增厚到 6-8px（世界厚度不变），沿顶 1px 受光唇 + 沿下 1px 接触投影仍是发丝线；新画 6 条 1px 根须从草沿扎进土体——16 档装不下这根信息。",
 "prosperity_steppe_base": "锈壤由盐粒噪点升级为有砾石的土：12 颗 2×2/3×2 小石各带上缘受光、下缘压暗，只用既有 4 阶 + 颗粒色；再补 5 条 1px 细根，与 top/side 的根须成一套。",
 "prosperity_forest_top": "与 steppe 同款簇生保持族内一致，但 accent 铜绿口袋用低频场成片铺开（16 档只有零星点），霜点代替锈屑 ⇒ 靠色相 + 成片形态与锈草甸双差可辨。",
 "prosperity_forest_top_side": "本张是 32 档唯一破 16 色的越线件（生成器实测 17）⇒ 把只占 1-6 像素的近重复色折进同族最近主色，保住草沿 4 阶 + 铜绿 accent 主色 + 霜点设计色；草裙/根须与 steppe 同款以锁住朝向关系。",
 "prosperity_forest_base": "砾石同 steppe base，另加 6 条 1px 横向根系纹；霜花碎屑保持稀疏设计色 ⇒ 与 forest top 的 accent 同色系，顶/侧/底读作同一土层。",
 "prosperity_wastes_top": "既有沙纹上补 1px 亮脊线 + 1px 背风暗线（沙纹受光面，R4 不加倍）；cells=16 附加倍频只用来打散大片同色沙面，金属闪点仍极稀疏。",
 "prosperity_wastes_top_side": "沙沿比草沿薄（4-6px）且几乎不挂丝（只 2 条根），土体沿用同族 base 的层理周期 ⇒ 侧面与 base 层带数一致，读作同一块砂岩。",
 "prosperity_wastes_base": "黄铜砂岩：层理带补 1px 受光唇与缝线（轮廓级条带 ×R、缝本身仍发丝），砾石改落层带两侧（层间砾）以免糊掉成层结构。",
 "prosperity_swamp_top": "纤维行纹在 32 档画成束芦苇丝（1px、长 3-6、逐行错位），泥斑用 paint_blob 的腐蚀边缘成 3-4px 软边团块（16 档只能一个点）；锈水渗点保持稀疏，全族最暗靠 L 侧大 t 不引新色。",
 "prosperity_swamp_top_side": "壳沿用 top 的橄榄纤维阶，沿内补 1px 芦苇横丝令侧顶同源；土体 5 条 1px 垂落根须 + 1px 接触投影（本族 shadow 走 D160 更压暗，保沼气浊感）。",
 "prosperity_swamp_base": "泥团在 32 档画成带 1px 上缘受光的软边块，锈水斑按 manifest 稀疏 ⇒ 与 top 同暗域、与 top_side 土体同阶，三面互锁。",
 "prosperity_tuft_rust": "锈草丛从空白透明底自绘（叠在生成器底座上会把叶密度翻倍、读作栅栏）：4 个草丛团 ×3 叶 + 3 根独叶，叶高 9-16px、基部 2px 宽向上收束到 1px、每 5px 一个弯折节，45% 带 2px 穗头；锈屑只落在已有草叶的邻位 ⇒ 读作落在草上的尘。中性乘色底与生根（底行 ≥6 列）口径不动。",
 "prosperity_tuft_copper": "铜绿草丛与锈草丛靠形态分家：5 团 ×4 叶 + 4 独叶，叶高仅 5-10px、部分带紧贴主叶的加宽列 ⇒ 更密更矮的苔垫；霜点同样只落在叶尖邻位。乘 Forest/Swamp 群系色后与锈草丛的疏高形态双差可辨。",
 "prosperity_rust_log_side": "纵向纹场 cells 4×16 不动（R1）+ 棱脊 ÷R 保持世界根数；两处手绘修正——锈芯环带行号按 ×R 落到 6/22（沿用 3/11 会把两道带挤在上半张），并补 1px 细纵裂与 3px 软边锈斑。",
 "prosperity_rust_log_top": "年轮方向不变（同心环 + 外圈 2px 树皮环带），但髓心由正中手移到 (17,14) ⇒ 环不完美同心（树是长的不是画的）；裂纹 2→3 条仍 1px，凹坑密度按 R3 不变。",
 "prosperity_rust_leaves": "冠层在 32 档加 5 段「缺口弧」暗孔（7-10px 弧、非整圈）沿叶簇边界 ⇒ 读作簇与簇之间的缝，而不是环或虫；亮针尖 1-2px、锈斑稀疏，仍是中性乘色底。",
 # —— P17-SB1：木 3 档 + 花 4 + 草 2（18 张；rust 档骨架的换谱姊妹张 + dim78 首批自有花）——
 "prosperity_copper_log_side": "与 rust 档同骨架（纵向纹场 4×16 + 棱脊 ÷R + 7 条细纵裂 + 2 团锈斑 + 环带 6/22），仅 Bark/Core 谱换铜绿系 ⇒ 族内一致、跨档靠色相与色斑谱双差可辨。",
 "prosperity_copper_log_top": "年轮骨架同 rust 档（髓心 (17,14) 手移、非完美同心），芯色换 COPPER_CORE 系；环带 2px ×R、裂纹 3 条 1px。",
 "prosperity_copper_leaves_side": "rust 叶同款 5 段缺口弧 + 亮针 + 稀疏霜点画法，底换 CANOPY_PATINA；乘色 tint 与草丛同口径 ⇒ 森林冠层读作铜绿。",
 "prosperity_copper_leaves_top": "顶视档：同谱同画法，暗孔 0.12/亮针 0.20 加密 ⇒ 俯视树冠的日光缝隙（与 side 的差异来自参数而非另一套笔刷）。",
 "prosperity_brass_log_side": "同骨架换黄铜系 Bark/Core + GLINT@D070 闪点斑 ⇒ 荒漠树干的金属砂感。",
 "prosperity_brass_log_top": "同骨架年轮，芯色 BRASS_CORE；顶面闪点在侧面谱系里不出现（双差）。",
 "prosperity_brass_leaves_side": "CANOPY_BRASS 秸秆色底 + BRASS_SAND@D140 稀疏点；乘群系黄铜草色后仍是废都可辨识的暗冠。",
 "prosperity_brass_leaves_top": "顶视加密档（孔 0.12/针 0.20），同谱。",
 "prosperity_marsh_log_side": "同骨架换 MARSH_BARK 深橄榄 + RUST_OXIDE@D140 锈斑 ⇒ 沼泽湿木的暗潮感。",
 "prosperity_marsh_log_top": "同骨架年轮，芯色 MARSH_CORE。",
 "prosperity_marsh_leaves_side": "CANOPY_MARSH 灰绿底 + 锈暗点；乘沼泽草色 4A5732 后与铜绿冠拉开色相。",
 "prosperity_marsh_leaves_top": "顶视加密档（孔 0.12/针 0.20），同谱。",
 "prosperity_flower_rust": "自有花族形（32 档自绘透明底）：6 茎 = 全花头/蕾/纯茎三态（42% / 30% / 28%），花头 = 1px 花心 + 4 正瓣（上左取亮阶、其余中阶）+ 4 角半瓣 70% 出现，顶瓣上一道 1px 亮唇；每茎 2px 生根足 ⇒ 底行 ≥6 列有底；色 = 花瓣 3 阶 + 心 + 茎 ≤6。",
 "prosperity_flower_patina": "同族形换霜花谱：花瓣 CANOPY 邻近亮铜绿（与叶冠靠形态+花心区分），茎 PATINA_SOIL ⇒ 森林林下花读作花而非叶簇。",
 "prosperity_flower_brass": "同族形换金盏谱：暖黄花瓣 + DARK_ANCHOR@L420 心（花心为族内最亮点，荒漠强日光口径）+ BRASS_ROCK@D090 茎。",
 "prosperity_flower_marsh": "同族形换沼兰谱：紫罗兰花瓣（全批唯一冷紫相，识别度最高）+ GLINT@D045 心 + PEAT_CRUST@L070 茎。",
 "prosperity_tuft_sedge": "苔薹草：与铜绿草丛同 moss_tuft 族形（密矮 5-10px 带加宽列），换 TUFT_SEDGE_MAT 黄绿谱 + PATINA_BLOOM@D180 暗霜点 + 独立种子 ⇒ 同形不同种，跨实例靠种子去重。",
 "prosperity_tuft_bristle": "刚毛草：与锈草丛同 dry_tuft 族形（疏高 9-16px + 45% 穗头），换 TUFT_BRISTLE_MAT 秸秆谱 + RUST_OXIDE@L140 亮尘 + 独立种子。",
 # —— P17-S-B2：沙/砂砾 3 张（全部非表层 top；荒漠铺沙 + S-C 河床料）——
 "prosperity_silica_sand": "细硅沙：沿用黄铜丘沙的 ripple_sand 族刷，但 ripple_period 8→4（同族笔刷不同读数）+ 闪点密度 0.006→0.004，底谱换 SILICA_SAND（全批最亮的冷沙相）⇒ 与在产的 prosperity_wastes_top 顶面双差可辨（色相 + 纹周期）。",
 "prosperity_coarse_sand": "风成粗粒沙：grit_base 骨架，颗粒密度 0.18→0.24、14 颗 2×2/3×2 砾粒带上缘受光与下缘压暗（同 base 族画法），底谱 COARSE_SAND 比细硅沙压暗两阶 ⇒ 读作「被风选过的粗砂」，不是另一张沙。",
 "prosperity_river_gravel": "河床砂砾：grit_base 骨架 + 砾密度 0.30（全批最高）+ 明暗阶拉到 D220/L220，底谱 RIVER_GRAVEL 是沙类里唯一不带暖相的灰褐 ⇒ 水磨砾石感；S-C 河床主料，本片先在荒漠砾石斑上真实消费一次。",
 # —— v1.20.39 T2：地底石化 1 张（wholeBody 主体石；冷灰带锈调）——
 "prosperity_stone": "繁荣废岩：grit_base 同骨架（母版 terrain_grit 带周期 8 沉积层理 ⇒ 成层废岩，不是土）+ 9 颗小砾 + 4 条 1px 锈染短缝与稀疏锈点，底谱 PROSPERITY_STONE 冷灰 ⇒ 贴近 prosperity_*_base 族但更石质，与 RIVER_GRAVEL（亮灰褐）和 SPIKE_STONE（冷蓝灰）三方拉开。",
}


# ---------------------------------------------------------------------------
# 底座与手绘层
# ---------------------------------------------------------------------------
def base_tex(key, overrides=None):
    entry = BY_KEY[key]
    params = G._resolve_params(entry, BY_KEY, STYLE)
    if overrides:
        for k, v in overrides.items():
            if isinstance(v, dict) and isinstance(params.get(k), dict):
                merged = dict(params[k])
                merged.update(v)
                params[k] = merged
            else:
                params[k] = v
    seed = G._parse_seed(MAN["SEEDS"][key])
    return G.PAINTERS[entry["kind"]](RULES, seed, params), seed, params


def rlevels(p):
    return RULES.levels(p["levels"], p["base"])


def level_shift(tex, seed, cells, amount, levels):
    """低频斑块 → 基色整体推 ±amount 阶（只用既有阶色 ⇒ 零新增色，成片干湿/明暗）。"""
    field = G.tile_noise(seed, "shift%d" % cells, cells)
    idx = {c: i for i, c in enumerate(levels)}
    for y in range(SIZE):
        for x in range(SIZE):
            cur = tex.px[y][x]
            if cur not in idx:
                continue
            v = field(x, y)
            delta = amount if v > 0.62 else (-amount if v < 0.38 else 0)
            tgt = min(len(levels) - 1, max(0, idx[cur] + delta))
            if tgt != idx[cur]:
                tex.put(x, y, levels[tgt])


def tuft_blades(tex, seed, tag, dark, lit, density=0.40, per=8):
    """簇生草叶：per×per 格位上一簇 2-4 根 1px 叶（16 档做不到簇生）。

    亮尖只给部分叶（62%）、叶侧受光点更少（22%）——全给会在 32 档读成"绿布撒盐"。
    """
    step = SIZE // per
    for j in range(per):
        for i in range(per):
            if G.h01(seed, tag + "cl", i, j) > density:
                continue
            x0 = i * step + int(G.h01(seed, tag + "cx", i, j) * step)
            y0 = j * step + int(G.h01(seed, tag + "cy", i, j) * step)
            nb = 2 + int(G.h01(seed, tag + "cn", i, j) * 2.4)
            for k in range(nb):
                # 逐叶再抖 0-2px：只按 k*2 等距会读出"梳齿"栅格
                x = x0 + k * 2 - nb // 2 + int(G.h01(seed, tag + "jx", i * per + j, k) * 2.4)
                ln = 3 + int(G.h01(seed, tag + "ll", i * per + j, k) * 3.0)
                for i2 in range(ln):
                    tex.put(x, y0 - i2, dark)
                if G.h01(seed, tag + "tip", i * per + j, k) < 0.62:
                    tex.put(x, y0 - ln + 1, lit)
                if G.h01(seed, tag + "f", i * per + j, k) < 0.22:
                    tex.put(x + 1, y0 - 1, lit)


def pebbles(tex, seed, dark, lit, tag, count=11, per=8):
    """砾石：2×2 / 3×2 小石，上缘受光下缘压暗（只两个传入色 ⇒ 不新增色）。"""
    step = SIZE // per
    placed = 0
    k = 0
    while placed < count and k < count * 4:
        i, j = k % per, k // per
        k += 1
        if G.h01(seed, tag + "q", i, j) > 0.6:
            continue
        cx = (i * step + int(G.h01(seed, tag + "x", i, j) * step)) % SIZE
        cy = (j * step + int(G.h01(seed, tag + "y", i, j) * step)) % SIZE
        w = 2 + int(G.h01(seed, tag + "w", i, j) * 2.0)
        for dx in range(w):
            tex.put(cx + dx, cy - 1, lit)
            tex.put(cx + dx, cy, dark if dx in (0, w - 1) else lit)
        tex.put(cx, cy + 1, dark)
        tex.put(cx + w - 1, cy + 1, dark)
        placed += 1


def strand_field(tex, seed, tag, count, color, y0s, length, sideways=True):
    """1px 发丝根须：从 y0（各列壳沿下缘）向下扎 length px，途中偶有 1px 侧移。"""
    for k in range(count):
        x = int(G.h01(seed, tag + "x", k, 0) * SIZE)
        y = (y0s[x] if y0s else int(G.h01(seed, tag + "y", k, 0) * SIZE)) + 2
        ln = 4 + int(G.h01(seed, tag + "l", k, 0) * (length - 3))
        lean = (-1, 0, 1)[int(G.h01(seed, tag + "d", k, 0) * 3.0) % 3] if sideways else 0
        for i in range(ln):
            tex.put(x + (lean if sideways and i % 3 == 2 else 0), y + i, color)


def specks(tex, seed, tag, color, density):
    for x, y in G.scatter(seed, tag, density):
        tex.put(x, y, color)


# --- kind 组合 -------------------------------------------------------------
def grass_top(key, tag):
    tex, seed, p = base_tex(key)
    levels = rlevels(p)
    dark = RULES.resolve(p["blade_dark"], p["base"])
    lit = RULES.resolve(p["blade_lit"], p["base"])
    level_shift(tex, seed, 8, 1, levels)
    level_shift(tex, seed ^ 0x51ED, 4, 1, levels)
    tuft_blades(tex, seed, tag, dark, lit, density=0.46)
    specks(tex, seed, tag + "spk", RULES.resolve(p["speckle"], p["base"]), 0.0035)
    if p.get("blotch"):
        bl = RULES.resolve(p["blotch"], p["base"])
        for k in range(2):
            G.paint_blob(tex, seed, tag + "bl%d" % k,
                         int(G.h01(seed, tag + "blx", k, 0) * SIZE),
                         int(G.h01(seed, tag + "bly", k, 0) * SIZE),
                         2.6 + G.h01(seed, tag + "blr", k, 0) * 1.3, bl, dark)
    return tex


def side(key, root_spec, roots, lit_lip=0.72, peb=4):
    tex, seed, p = base_tex(key)
    body, fringe = p["body"], p["fringe"]
    blevels = rlevels(body)
    fmin = int(p["fringe_min"]) * 2
    fmax = int(p["fringe_max"]) * 2
    heights = []
    for x in range(SIZE):
        h = fmin + int(G.h01(seed, "fh", x, 0) * (fmax - fmin + 1))
        heights.append(max(fmin, min(fmax, h)))
    flit = RULES.resolve(fringe["blade_lit"], fringe["base"]) if fringe.get("blade_lit") else blevels[-1]
    fdark = RULES.resolve(fringe["blade_dark"], fringe["base"]) if fringe.get("blade_dark") else blevels[0]
    for x in range(SIZE):
        h = heights[x]
        if G.h01(seed, "lip", x, 0) < lit_lip:
            tex.put(x, 0, flit)
        if G.h01(seed, "lipk", x, 0) < 0.52:
            tex.put(x, h - 1, fdark)
    rootc = RULES.resolve(root_spec, body["base"])
    strand_field(tex, seed, "rt", roots, rootc, heights, 9)
    clod = RULES.resolve(body["clod"], body["base"]) if body.get("clod") else blevels[0]
    pebbles(tex, seed ^ 0x2F3D, clod, blevels[-1], "sb", count=peb, per=8)
    return tex


def grit_base(key, peb=12, roots=0):
    tex, seed, p = base_tex(key)
    levels = rlevels(p)
    clod = RULES.resolve(p["clod"], p["base"]) if p.get("clod") else levels[0]
    lit = RULES.resolve(p["grit_lit"], p["base"])
    level_shift(tex, seed ^ 0x77, 8, 1, levels)
    pebbles(tex, seed, clod, lit, "peb", count=peb, per=8)
    if roots:
        strand_field(tex, seed, "gr", roots, levels[0], None, 6, sideways=True)
    return tex


def stone_ruin(key):
    """繁荣废岩（v1.20.39 T2）：grit_base 同骨架（层理由母版 terrain_grit 的 strata 参数进底座场：
    strata_period/strength 偏置 + strata_joint/strata_lip 的 1px 缝线/受光唇，颜色复用 grit_dark/
    grit_lit 两阶 ⇒ 零新增色）+ 9 颗小砾 + 4 条 1px 锈染短缝（读作石缝里的锈流；稀疏锈点由母版
    flake 参数直接出，不在此重复撒）。零硬编码色值，全部角色名走 manifest params。"""
    tex, seed, p = base_tex(key)
    levels = rlevels(p)
    clod = RULES.resolve(p["clod"], p["base"]) if p.get("clod") else levels[0]
    lit = RULES.resolve(p["grit_lit"], p["base"])
    level_shift(tex, seed ^ 0x77, 8, 1, levels)
    pebbles(tex, seed, clod, lit, "peb", count=9, per=8)
    rust = RULES.resolve(p["flake"], p["base"])
    for k in range(4):
        x = int(G.h01(seed, "rvx", k, 0) * SIZE)
        y = int(G.h01(seed, "rvy", k, 0) * SIZE)
        ln = 4 + int(G.h01(seed, "rvl", k, 0) * 6.0)
        for i in range(ln):
            tex.put(x + (1 if G.h01(seed, "rvd", k, i) < 0.28 else 0), y + i, rust)
    return tex


def ripple_sand(key):
    tex, seed, p = base_tex(key)
    levels = rlevels(p)
    dark = RULES.resolve(p["grit_dark"], p["base"])
    per = int(p["ripple_period"]) * 2
    for y in range(SIZE):
        m = y % per
        if m == 0:
            for x in range(SIZE):
                if G.h01(seed, "crest", x, y) < 0.82:
                    tex.put(x, y, levels[-1])
        elif m == 1:
            for x in range(SIZE):
                if G.h01(seed, "crest2", x, y) < 0.5:
                    tex.put(x, y, levels[-2])
        elif m == per // 2:
            for x in range(SIZE):
                tex.put(x, y, dark if G.h01(seed, "trough", x, y) < 0.8 else levels[0])
    level_shift(tex, seed ^ 0x9, 16, 1, levels)
    specks(tex, seed, "gl2", RULES.resolve(p["glint"], p["base"]), 0.0012)
    return tex


def fiber_swamp(key):
    tex, seed, p = base_tex(key)
    levels = rlevels(p)
    fdark = RULES.resolve(p["fiber_dark"], p["base"])
    flit = RULES.resolve(p["fiber_lit"], p["base"])
    muck = RULES.resolve(p["muck"], p["base"])
    for k in range(26):
        y = int(G.h01(seed, "reedy", k, 0) * SIZE)
        x = int(G.h01(seed, "reedx", k, 0) * SIZE)
        ln = 3 + int(G.h01(seed, "reedl", k, 0) * 4.0)
        col = fdark if G.h01(seed, "reedk", k, 0) < 0.6 else flit
        for i in range(ln):
            tex.put(x + i, y + i // 3, col)
    for k in range(3):
        G.paint_blob(tex, seed, "mk%d" % k,
                     int(G.h01(seed, "mkx", k, 0) * SIZE),
                     int(G.h01(seed, "mky", k, 0) * SIZE),
                     2.4 + G.h01(seed, "mkr", k, 0) * 1.4, muck, levels[0])
    specks(tex, seed, "sp2", RULES.resolve(p["seep"], p["base"]), 0.0012)
    return tex


def tuft(key):
    """十字装饰从**空白透明底**自绘（不叠在生成器 32 档底座上——叠加会让叶密度翻倍读作栅栏）。

    形态学：3-4 个草丛团（一团 3-5 根，团内 x 相差 1-3px）+ 若干独叶；
    叶身底部 2px 宽、向上收束到 1px（32 档的锐度收益），弯折节 5px；
    锈草丛=疏高（9-16px）+ 穗头，铜绿草丛=密矮（5-10px）+ 加宽列；
    屑点只落在已有前景像素的邻位 ⇒ 读作落在草上的尘，不读作空中噪点。
    """
    p = G._resolve_params(BY_KEY[key], BY_KEY, STYLE)
    seed = G._parse_seed(MAN["SEEDS"][key])
    base = p["base"]
    dark = RULES.resolve(p["blade_dark"], base)
    mid = RULES.resolve(p["blade_mid"], base)
    lit = RULES.resolve(p["blade_lit"], base)
    dry = p["form"] == "dry_tuft"
    head = RULES.resolve(p["head"], base) if dry else lit
    speck = RULES.resolve(p["oxide"] if dry else p["bloom"], base)
    tex = G.Tex(bg=TRANSPARENT)

    clumps = (4 if dry else 5)
    per_clump = (3 if dry else 4)
    hmin, hmax = (9, 16) if dry else (5, 10)
    xs = []
    for c in range(clumps):
        cx = int((c + 0.35 + G.h01(seed, "clx", c, 0) * 0.4) * SIZE / float(clumps)) % SIZE
        for j in range(per_clump):
            xs.append(cx + j * (2 if dry else 1) - per_clump + int(G.h01(seed, "clj", c, j) * 3.0))
    for k in range(3 if dry else 4):                       # 独叶
        xs.append(int(G.h01(seed, "solo", k, 0) * SIZE))
    for n, x0 in enumerate(xs):
        h = hmin + int(G.h01(seed, "hh", n, 0) * (hmax - hmin + 1))
        lean = (-1, 0, 0, 1)[int(G.h01(seed, "hl", n, 0) * 4.0) % 4]
        wide = (not dry) and G.h01(seed, "hw", n, 0) < 0.5
        x = x0
        for i in range(h):
            y = SIZE - 1 - i
            if i >= 5 and i % 5 == 0:
                x += lean
            col = dark if i < 2 else mid
            if i == h - 1 and G.h01(seed, "ht", n, 0) < 0.62:
                col = lit
            tex.put(x, y, col, wrap=False)
            if i < 2 or (i < 4 and not dry):               # 基部 2px 宽，向上收 1px
                tex.put(x + 1, y, dark if i < 2 else mid, wrap=False)
            if wide and i < h - 2:
                tex.put(x + 2, y, lit if i == h - 3 else mid, wrap=False)
        tipx, tipy = x, SIZE - 1 - (h - 1)
        if dry and G.h01(seed, "hhead", n, 0) < float(p["head_chance"]):
            tex.put(tipx, tipy - 1, head, wrap=False)      # 穗头 2-3px
            if G.h01(seed, "hhead2", n, 0) < 0.5:
                tex.put(tipx + 1, tipy, head, wrap=False)
        elif not dry and G.h01(seed, "ctip", n, 0) < 0.35:
            tex.put(tipx, tipy - 1, lit, wrap=False)
    # 屑点：仅落在既有前景的 4 邻位
    hits = [(x, y) for x, y in G.scatter(seed, "hspk", float(p["oxide_density"] if dry else p["bloom_density"]) * 3.0)]
    placed = 0
    for x, y in hits:
        if placed >= 14:
            break
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = x + dx, y + dy
            if 0 <= nx < SIZE and 0 <= ny < SIZE and tex.px[ny][nx] not in (None, TRANSPARENT):
                tex.put(nx + dx, ny + dy, speck, wrap=False)
                placed += 1
                break
    return tex


def flower(key):
    """P17-SB1 自有花族形（32 档自绘透明底，同 tuft 不叠生成器底座的理由——覆盖闸双钉）。

    形态学：6 茎槽位均布（防扎堆同 tuft 的槽位法），三态 = 全花头(42%)/花蕾(30%)/纯茎(28%)；
    花头 = 1px 花心 + 上下左右 4 正瓣（左上取亮阶、其余中阶，光源锚左上）+ 4 角半瓣（70% 出现）
    + 顶瓣上一道 1px 亮唇；蕾 = 中阶 1px + 暗尖 1px；每茎 2px 生根足 ⇒ 底行前景 ≥6 列（落地生根闸）。
    用色 = 花瓣暗/中/亮 3 阶（族参数 blade_*，与四形态十字同词汇）+ 花心 + 茎 = ≤6 色；
    一切色经 Rules.resolve('NAME@TOKEN') 派生。
    """
    p = G._resolve_params(BY_KEY[key], BY_KEY, STYLE)
    seed = G._parse_seed(MAN["SEEDS"][key])
    base = p["base"]
    pmid = RULES.resolve(p.get("blade_mid", "BASE"), base)
    pdark = RULES.resolve(p["blade_dark"], base)
    plit = RULES.resolve(p["blade_lit"], base)
    center = RULES.resolve(p["center"], base)
    stem = RULES.resolve(p["stem"], base)
    tex = G.Tex(bg=TRANSPARENT)
    n = int(p["head_count"])                       # 6
    for k in range(n):
        x = int((k + 0.30 + G.h01(seed, "fx", k, 0) * 0.40) * SIZE / float(n)) % SIZE
        role = G.h01(seed, "fr", k, 0)
        h = (13 + int(G.h01(seed, "fh", k, 0) * 6.0)) if role < 0.42 else \
            (9 + int(G.h01(seed, "fh", k, 0) * 4.0))
        lean = (-1, 0, 0, 1)[int(G.h01(seed, "fl", k, 0) * 4.0) % 4]
        tex.put(x, SIZE - 1, stem, wrap=False)     # 生根足 2px
        tex.put(x + 1, SIZE - 1, pdark if role >= 0.42 else stem, wrap=False)
        for b in range(2):                         # 基叶一对（茎色斜叶 4-6px，撑覆盖率与"草里开花"读感）
            side = -1 if b == 0 else 1
            bln = 4 + int(G.h01(seed, "bl", k, b) * 3.0)
            for i in range(1, bln):
                tex.put(x + side * (1 + i // 2), SIZE - 1 - i, stem, wrap=False)
        cx = x
        for i in range(1, h):
            y = SIZE - 1 - i
            if i % 4 == 0:
                cx += lean
            tex.put(cx, y, stem, wrap=False)
            if i == h - 2 and role < 0.42:         # 花头茎侧一片垂叶（同茎色，不新增色）
                tex.put(cx - lean if lean else cx + 1, y, stem, wrap=False)
        hy = SIZE - 1 - h
        if role < 0.42:                            # 全花头
            tex.put(cx, hy, center, wrap=False)
            for dx, dy in ((0, -1), (0, 1), (-1, 0), (1, 0)):
                tex.put(cx + dx, hy + dy, plit if dx + dy < 0 else pmid, wrap=False)
            for dx, dy in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
                if G.h01(seed, "fc", k, dx * 3 + dy) < 0.7:
                    tex.put(cx + dx, hy + dy, pmid, wrap=False)
            tex.put(cx, hy - 2, plit, wrap=False)  # 顶瓣亮唇
        elif role < 0.72:                          # 花蕾
            tex.put(cx, hy, pmid, wrap=False)
            tex.put(cx, hy - 1, pdark, wrap=False)
        # else 纯茎（茎已画）
    return tex


def log_side(key):
    tex, seed, p = base_tex(key, {"band_rows": [6, 22]})   # ×R：锈芯环带保持世界位置
    base = p["base"]
    dark = RULES.resolve(p["bark_dark"], base)
    lit = RULES.resolve(p["bark_lit"], base)
    bloom = RULES.resolve(p["bloom"], base)
    for k in range(7):
        x = int(G.h01(seed, "ckx", k, 0) * SIZE)
        y = int(G.h01(seed, "cky", k, 0) * SIZE)
        ln = 5 + int(G.h01(seed, "ckl", k, 0) * 9.0)
        for i in range(ln):
            tex.put(x + (1 if G.h01(seed, "ckd", k, i) < 0.25 else 0), y + i, dark)
    for k in range(2):
        G.paint_blob(tex, seed, "lb%d" % k,
                     int(G.h01(seed, "lbx", k, 0) * SIZE),
                     int(G.h01(seed, "lby", k, 0) * SIZE),
                     2.6 + G.h01(seed, "lbr", k, 0) * 1.2, bloom, dark)
    for x in range(SIZE):
        if G.h01(seed, "knob", x, 0) < 0.14:
            y = int(G.h01(seed, "knoby", x, 0) * SIZE)
            tex.put(x, y, lit)
            tex.put(x, y + 1, lit)
            tex.put(x, y - 1, dark)
    return tex


def log_top(key):
    tex, seed, p = base_tex(key)
    base = p["base"]
    core = RULES.resolve(p["core"], base)
    alt = RULES.resolve(p["ring_alt"], base)
    lit = RULES.resolve(p["ring_lit"], base)
    rim1 = RULES.resolve(p["bark_rim_1"], base)
    rim2 = RULES.resolve(p["bark_rim_2"], base)
    pit = RULES.resolve(p["pit"], base)
    cx, cy = 17.0, 14.0                     # 髓心手移：年轮不完美同心
    period = 6.0                            # 母版 3px × R
    rad = [[math.hypot(x - cx, y - cy) / period for x in range(SIZE)] for y in range(SIZE)]
    for y in range(SIZE):
        for x in range(SIZE):
            i = int(rad[y][x])
            tex.put(x, y, core if i % 2 == 0 else alt, wrap=False)
    for y in range(SIZE):
        for x in range(SIZE):
            i = int(rad[y][x])
            bright = False
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < SIZE and 0 <= ny < SIZE and int(rad[ny][nx]) > i:
                    bright = True
            if i % 2 == 1 and bright:
                tex.put(x, y, lit, wrap=False)
    for k in range(2):                      # 树皮环带厚度 ×R（R4 轮廓级条带）
        for v in range(SIZE):
            tex.put(v, k, rim2, wrap=False)
            tex.put(v, SIZE - 1 - k, rim1, wrap=False)
            tex.put(k, v, rim2, wrap=False)
            tex.put(SIZE - 1 - k, v, rim1, wrap=False)
    for x, y in G.scatter(seed, "pit", float(p["pit_density"])):
        if 2 <= x < SIZE - 2 and 2 <= y < SIZE - 2:
            tex.put(x, y, pit, wrap=False)
    for k in range(3):                      # 放射裂纹 2→3 条，仍 1px 发丝
        ang = G.h01(seed, "ang", k, 0) * TAU
        ln = 8 + int(G.h01(seed, "alen", k, 0) * 7.0)
        for i in range(ln):
            rr = 2.0 + i
            x = int(round(cx + math.cos(ang) * rr))
            y = int(round(cy + math.sin(ang) * rr))
            if 0 <= x < SIZE and 0 <= y < SIZE:
                tex.put(x, y, pit, wrap=False)
    return tex


def leaves(key):
    tex, seed, p = base_tex(key)
    levels = rlevels(p)
    hole = RULES.resolve(p["hole"], p["base"])
    needle = RULES.resolve(p["needle_lit"], p["base"])
    oxide = RULES.resolve(p["oxide"], p["base"])
    level_shift(tex, seed ^ 0x5B, 8, 1, levels)
    for k in range(5):
        cx = int(G.h01(seed, "lcx", k, 0) * SIZE)
        cy = int(G.h01(seed, "lcy", k, 0) * SIZE)
        rr = 4.0 + G.h01(seed, "lcr", k, 0) * 3.0
        a0 = G.h01(seed, "lca", k, 0) * TAU
        span = 7 + int(G.h01(seed, "lcs", k, 0) * 4.0)     # 缺口弧（非整圈）⇒ 读作簇间缝隙，不读作虫/环
        for i in range(span):
            a = a0 + i * 0.42
            tex.put(int(round(cx + math.cos(a) * rr)), int(round(cy + math.sin(a) * rr * 0.8)), hole)
    specks(tex, seed, "n2", needle, 0.0035)
    for x, y in G.scatter(seed, "n2b", 0.002):
        tex.put(x, y, needle)
        if G.h01(seed, "n2bl", x, y) < 0.5:
            tex.put(x, y + 1, needle)
    specks(tex, seed, "ox2", oxide, 0.0012)
    return tex


BUILDERS = {
    "prosperity_steppe_top": lambda: grass_top("prosperity_steppe_top", "st"),
    "prosperity_forest_top": lambda: grass_top("prosperity_forest_top", "ft"),
    "prosperity_steppe_base": lambda: grit_base("prosperity_steppe_base", 12, 5),
    "prosperity_forest_base": lambda: grit_base("prosperity_forest_base", 12, 6),
    "prosperity_wastes_base": lambda: grit_base("prosperity_wastes_base", 7, 0),
    "prosperity_swamp_base": lambda: grit_base("prosperity_swamp_base", 10, 0),
    "prosperity_steppe_top_side": lambda: side("prosperity_steppe_top_side", "BASE@D220", 6),
    "prosperity_forest_top_side": lambda: side("prosperity_forest_top_side", "BASE@D220", 6),
    "prosperity_wastes_top_side": lambda: side("prosperity_wastes_top_side", "BASE@D200", 2, lit_lip=0.5, peb=3),
    "prosperity_swamp_top_side": lambda: side("prosperity_swamp_top_side", "BASE@D220", 5),
    "prosperity_wastes_top": lambda: ripple_sand("prosperity_wastes_top"),
    "prosperity_swamp_top": lambda: fiber_swamp("prosperity_swamp_top"),
    "prosperity_tuft_rust": lambda: tuft("prosperity_tuft_rust"),
    "prosperity_tuft_copper": lambda: tuft("prosperity_tuft_copper"),
    "prosperity_rust_log_side": lambda: log_side("prosperity_rust_log_side"),
    "prosperity_rust_log_top": lambda: log_top("prosperity_rust_log_top"),
    "prosperity_rust_leaves": lambda: leaves("prosperity_rust_leaves"),
    # —— P17-SB1 18 张：木 3 档复用 rust 族 builder（换 manifest 谱与种子），新花族形 flower()，
    #    新草复用 tuft() 两形态（moss/dry）——与「每种新花/草 = 1 实例 + 1 贴图，零新画架」口径一致。
    "prosperity_copper_log_side": lambda: log_side("prosperity_copper_log_side"),
    "prosperity_copper_log_top": lambda: log_top("prosperity_copper_log_top"),
    "prosperity_copper_leaves_side": lambda: leaves("prosperity_copper_leaves_side"),
    "prosperity_copper_leaves_top": lambda: leaves("prosperity_copper_leaves_top"),
    "prosperity_brass_log_side": lambda: log_side("prosperity_brass_log_side"),
    "prosperity_brass_log_top": lambda: log_top("prosperity_brass_log_top"),
    "prosperity_brass_leaves_side": lambda: leaves("prosperity_brass_leaves_side"),
    "prosperity_brass_leaves_top": lambda: leaves("prosperity_brass_leaves_top"),
    "prosperity_marsh_log_side": lambda: log_side("prosperity_marsh_log_side"),
    "prosperity_marsh_log_top": lambda: log_top("prosperity_marsh_log_top"),
    "prosperity_marsh_leaves_side": lambda: leaves("prosperity_marsh_leaves_side"),
    "prosperity_marsh_leaves_top": lambda: leaves("prosperity_marsh_leaves_top"),
    "prosperity_flower_rust": lambda: flower("prosperity_flower_rust"),
    "prosperity_flower_patina": lambda: flower("prosperity_flower_patina"),
    "prosperity_flower_brass": lambda: flower("prosperity_flower_brass"),
    "prosperity_flower_marsh": lambda: flower("prosperity_flower_marsh"),
    "prosperity_tuft_sedge": lambda: tuft("prosperity_tuft_sedge"),
    "prosperity_tuft_bristle": lambda: tuft("prosperity_tuft_bristle"),
    # —— P17-S-B2 沙类 3 张：零新画架（ripple_sand / grit_base 两个既有族刷 + 换谱换种子换读数）——
    "prosperity_silica_sand": lambda: ripple_sand("prosperity_silica_sand"),
    "prosperity_coarse_sand": lambda: grit_base("prosperity_coarse_sand", 14, 0),
    "prosperity_river_gravel": lambda: grit_base("prosperity_river_gravel", 20, 0),
    # —— v1.20.39 T2 地底石化 1 张：grit_base 同骨架 + 锈染层（新 builder 仅因锈缝层，画架原语零新）——
    "prosperity_stone": lambda: stone_ruin("prosperity_stone"),
}

# 设计性稀疏色（闪点/锈屑/渗点/霜点）：配色收敛时永不合并
PROTECTED_SPEC = {
    "prosperity_steppe_top": [("RUST_OXIDE@D070", "STEPPE_SWARD")],
    "prosperity_steppe_base": [("RUST_OXIDE@D070", "RUST_DIRT")],
    "prosperity_forest_top": [("PATINA_BLOOM@D140", "PATINA_SWARD")],
    "prosperity_forest_base": [("PATINA_BLOOM", "PATINA_SOIL")],
    "prosperity_wastes_top": [("GLINT@D140", "BRASS_SAND")],
    "prosperity_swamp_top": [("RUST_OXIDE@D140", "PEAT_CRUST")],
    "prosperity_swamp_base": [("RUST_OXIDE@D180", "PEAT_MUD")],
    "prosperity_rust_leaves": [("RUST_OXIDE@D070", "LEAF_MAT")],
    "prosperity_tuft_rust": [("RUST_OXIDE@L140", "TUFT_RUST_MAT")],
    "prosperity_tuft_copper": [("PATINA_BLOOM@D140", "TUFT_PATINA_MAT")],
    "prosperity_rust_log_side": [("RUST_OXIDE@D070", "RUST_BARK")],
    # —— P17-SB1：稀疏设计色（锈斑/霜点/花心）永不参与收敛 ——
    "prosperity_copper_log_side": [("PATINA_BLOOM@D140", "COPPER_BARK")],
    "prosperity_copper_leaves_side": [("PATINA_BLOOM@D140", "CANOPY_PATINA")],
    "prosperity_copper_leaves_top": [("PATINA_BLOOM@D140", "CANOPY_PATINA")],
    "prosperity_brass_log_side": [("GLINT@D070", "BRASS_BARK")],
    "prosperity_brass_leaves_side": [("BRASS_SAND@D140", "CANOPY_BRASS")],
    "prosperity_brass_leaves_top": [("BRASS_SAND@D140", "CANOPY_BRASS")],
    "prosperity_marsh_log_side": [("RUST_OXIDE@D140", "MARSH_BARK")],
    "prosperity_marsh_leaves_side": [("RUST_OXIDE@D070", "CANOPY_MARSH")],
    "prosperity_marsh_leaves_top": [("RUST_OXIDE@D070", "CANOPY_MARSH")],
    "prosperity_tuft_sedge": [("PATINA_BLOOM@D180", "TUFT_SEDGE_MAT")],
    "prosperity_tuft_bristle": [("RUST_OXIDE@L140", "TUFT_BRISTLE_MAT")],
    # —— P17-S-B2：沙类的闪点/砾影是设计性稀疏色，永不参与收敛 ——
    "prosperity_silica_sand": [("GLINT@D140", "SILICA_SAND")],
    "prosperity_river_gravel": [("BASE@D220", "RIVER_GRAVEL")],
    # —— v1.20.39 T2：废岩的锈染短缝/锈点是设计性稀疏色，永不参与收敛 ——
    "prosperity_stone": [("RUST_OXIDE@D070", "PROSPERITY_STONE")],
}


# ---------------------------------------------------------------------------
# 配色收敛 + 自检 + 落盘
# ---------------------------------------------------------------------------
def rgb_of(color):
    return tuple(color[:3])


def palette_of(tex, skip_transparent):
    hist = {}
    for row in tex.px:
        for c in row:
            if c is None:
                continue
            if skip_transparent and len(c) > 3 and c[3] == 0:
                continue
            hist[rgb_of(c)] = hist.get(rgb_of(c), 0) + 1
    return hist


def collapse_palette(tex, protected, skip_transparent, target=MAX_COLORS):
    """最稀疏的近重复色折进最近主色（受保护色与透明底永不参与）。"""
    merged = []
    for _ in range(64):
        hist = palette_of(tex, skip_transparent)
        if len(hist) <= target:
            return merged
        movable = sorted((n, rgb) for rgb, n in hist.items()
                         if rgb not in protected and rgb != (0, 0, 0))
        if len(movable) < 2:
            return merged
        victim = movable[0][1]
        cands = [rgb for rgb in hist if rgb not in protected and rgb != victim]
        target_rgb = min(cands, key=lambda rgb: sum((victim[i] - rgb[i]) ** 2 for i in range(3)))
        for y in range(SIZE):
            for x in range(SIZE):
                c = tex.px[y][x]
                if c is None or (skip_transparent and len(c) > 3 and c[3] == 0):
                    continue
                if rgb_of(c) == victim:
                    tex.px[y][x] = target_rgb + (255,)
        merged.append((victim, target_rgb))
    raise AssertionError("配色收敛失败：仍有 %d 色" % len(palette_of(tex, skip_transparent)))


def write_texture(key, tex, merged):
    entry = BY_KEY[key]
    is_cross = entry["kind"] == "cross_decor"
    colors = palette_of(tex, is_cross)
    assert len(colors) <= MAX_COLORS, "%s 色数 %d > 16" % (key, len(colors))
    for rgb in colors:
        assert rgb not in FORBID, "%s 用了极值色 %s" % (key, rgb)
    im = G.Image.new("RGBA", (SIZE, SIZE), TRANSPARENT)
    px = im.load()
    fg = 0
    for y in range(SIZE):
        for x in range(SIZE):
            c = tex.px[y][x]
            assert c is not None, "%s 像素空洞 (%d,%d)" % (key, x, y)
            if is_cross:
                a = c[3] if len(c) > 3 else 255
                assert a in (0, 255), "%s alpha 非二值 (%d,%d)=%s" % (key, x, y, c)
                val = rgb_of(c) + (a,)
                if a == 0:
                    val = TRANSPARENT
                else:
                    fg += 1
            else:
                val = rgb_of(c) + (255,)
            px[x, y] = val
    if is_cross:
        bottom = sum(1 for x in range(SIZE) if px[x, SIZE - 1][3] == 255)
        cov = fg / float(SIZE * SIZE)
        assert 0.12 <= cov <= 0.62, "%s 覆盖率 %.3f 越界 [0.12,0.62]" % (key, cov)
        assert bottom >= 6, "%s 底行前景 %d < 6（未落地生根）" % (key, bottom)
    path = ASSET_DIR / (key + ".png")
    im.save(str(path))
    back = G.Image.open(str(path)).convert("RGBA")
    assert back.size == (32, 32) and back.size[0] == back.size[1]
    seen = palette_of_image(back, is_cross)
    line = "%-28s %-4dx%-4d colors=%2d %-6s" % (
        key, back.width, back.height, len(seen), "cross" if is_cross else "opaque")
    if entry.get("tileable") and not is_cross:
        m = G.seam_metrics(back)
        lim = {"prosperity_natural": 1.40, "prosperity_side": 1.30, "prosperity_decor": 0.40}.get(entry["group"], 1.5)
        ax = str(entry.get("seam_axes", "xy"))
        bad = []
        if "x" in ax and m["col_gross"] > lim:
            bad.append("x")
        if "y" in ax and m["row_gross"] > lim:
            bad.append("y")
        line += " seam x=%.2f y=%.2f lim=%.2f %s" % (
            m["col_gross"], m["row_gross"], lim, ("SEAM-OVER:" + ",".join(bad)) if bad else "ok")
    if merged:
        line += "  收敛: " + ", ".join("#%02X%02X%02X→#%02X%02X%02X" % (a + b) for a, b in merged)
    print(line)
    return path


def palette_of_image(im, skip_transparent):
    seen = set()
    for c in im.getdata():
        if skip_transparent and c[3] == 0:
            continue
        seen.add(c[:3])
    return seen


def main(argv):
    keys = KEYS if argv in ([], ["--all"]) else [a for a in argv if a in BY_KEY]
    prot = {}
    for key, specs in PROTECTED_SPEC.items():
        prot[key] = {RULES.resolve(spec, base)[:3] for spec, base in specs}
    for key in keys:
        tex = BUILDERS[key]()
        is_cross = BY_KEY[key]["kind"] == "cross_decor"
        merged = collapse_palette(tex, prot.get(key, set()), is_cross)
        write_texture(key, tex, merged)
    print("WRITTEN %d/%d" % (len(keys), len(KEYS)))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
