#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""selftest_scale_probe.py — P10a 判据 2 的反假绿台架（只读，不改任何资产）。

要证明的两件事：
  A) 参数化**之前**的 noise_period_selftest 在 SIZE=32 下"跑通"是假绿：
     它文档里声称的语义是"噪声场周期 16"（f(x+16,y)==f(x,y)），但代码里检查的偏移量写成了 SIZE，
     于是在 SIZE=32 下它验的是 32 周期性，仍然返回 True——**断言与它声称的语义脱钩**。
     这里直接在 32 档量一遍 f(x+16,y)==f(x,y) 是否成立：不成立 ⇒ 它声称的性质其实已失效（假绿坐实）。
  B) 参数化**之后**：显式把周期按回硬编码 16（等价于"把自检退回旧写法"）在 SIZE=32 下必须 RED；
     而默认（period=SIZE）在 16 与 32 两档都必须 GREEN。

用法：
  python tools/artgen/checks/selftest_scale_probe.py                       # 只跑 B（当前脚本）
  python tools/artgen/checks/selftest_scale_probe.py --legacy <旧版 .py>   # 再跑 A（对照旧版）
旧版取法（只读）：git show HEAD:tools/artgen/dim7879/gen_dim7879_blocks.py > temp/p10a-legacy-gen7879.py
"""

import importlib.util
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
CURRENT = ROOT / "tools" / "artgen" / "dim7879" / "gen_dim7879_blocks.py"
SEED = 0x5EED5EED


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, str(path))
    mod = importlib.util.module_from_spec(spec)
    sys.modules[name] = mod
    spec.loader.exec_module(mod)
    return mod


def fields_of(mod, size, legacy):
    """旧版没有 set_size()：直接改它的模块全局 SIZE/W 模拟"把 SIZE 常量改成 32"的朴素做法。"""
    if legacy:
        mod.SIZE = size
        mod.W = size
    else:
        mod.set_size(size)
    return (
        mod.octave_field(SEED, "selftest", mod.DEFAULT_OCTAVES),
        mod.tile_noise_aniso(SEED, "selftest_aniso", 4, 16),
    )


def hard16_claim_holds(mod, size, legacy):
    """逐点量"周期 16"这个声称在 SIZE 下到底成不成立。"""
    for field in fields_of(mod, size, legacy):
        for x in range(size):
            for y in range(size):
                if field(x + 16, y) != field(x, y) or field(x, y + 16) != field(x, y):
                    return False, (x, y)
    return True, None


def run_current():
    mod = load("probe_cur", CURRENT)
    print("[B1] 当前脚本 SIZE=32 + 自检硬编回 16（等价于退回旧写法）→ 期望 RED")
    mod.set_size(32)
    try:
        mod.noise_period_selftest(SEED, period=16)
    except AssertionError as exc:
        print("     [RED] %s" % exc)
    else:
        print("     [FAIL] 竟然绿了——参数化没生效")
        return 1
    print("[B2] 当前脚本 SIZE=32 + 默认 period=SIZE → 期望 GREEN")
    mod.noise_period_selftest(SEED)
    print("     [GREEN] 通过（含非常数性与半周期反证两道门）")
    print("[B3] 当前脚本 SIZE=16 + 默认 period=SIZE → 期望 GREEN（母版档不受影响）")
    mod.set_size(16)
    mod.noise_period_selftest(SEED)
    print("     [GREEN] 通过")
    return 0


def run_legacy(path):
    mod = load("probe_legacy", path)
    print("[A] 旧版（未参数化）在 SIZE=32 下：自检返回 True，但它声称的『周期 16』其实已不成立")
    if hasattr(mod, "set_size"):
        mod.set_size(32)
    else:
        mod.SIZE = 32
        mod.W = 32
    ok = mod.noise_period_selftest(SEED)
    holds, where = hard16_claim_holds(mod, 32, True)
    print("     旧版 noise_period_selftest(SIZE=32) -> %s（绿）" % ok)
    print("     实测 f(x+16,y)==f(x,y) 全图成立？ %s%s"
          % (holds, "" if holds else "（首个反例像素 %s）" % (where,)))
    if ok and not holds:
        print("     [FAKE-GREEN CONFIRMED] 断言绿、声称的性质失效 ⇒ 参数化前确为假绿")
        return 0
    print("     [UNEXPECTED] 与预期形状不符，需人工复核")
    return 1


if __name__ == "__main__":
    argv = [str(a) for a in sys.argv[1:]]
    rc = run_current()
    legacy = None
    for i, token in enumerate(argv):
        if token == "--legacy" and i + 1 < len(argv):
            legacy = Path(argv[i + 1])
    if legacy is not None:
        if not legacy.is_file():
            print("[MISSING] 旧版副本不存在：%s（先跑 git show HEAD:... > %s）" % (legacy, legacy), file=sys.stderr)
            raise SystemExit(3)
        rc = max(rc, run_legacy(legacy))
    raise SystemExit(rc)
