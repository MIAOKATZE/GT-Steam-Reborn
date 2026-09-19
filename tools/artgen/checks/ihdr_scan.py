#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""ihdr_scan.py — 逐张读 PNG 的 IHDR（宽高 / bit depth / color type）并汇总，用于判据 1。

用法：python tools/artgen/checks/ihdr_scan.py <目录> [<目录> ...]
输出：每个目录一行 `IHDR dir=<目录> n=<张数> shapes=<"WxH:计数" 排序串> depth=<集合> colortype=<集合>`；
      末行 `IHDR-SCAN files=<总张数> distinct-shapes=<N>`。零依赖 PIL（直接读 IHDR 字段）。
"""

import sys
from pathlib import Path


def ihdr(path):
    data = path.read_bytes()[:33]
    if data[:8] != b"\x89PNG\r\n\x1a\n" or data[12:16] != b"IHDR":
        raise AssertionError("%s 不是带 IHDR 的 PNG" % path)
    w = int.from_bytes(data[16:20], "big")
    h = int.from_bytes(data[20:24], "big")
    return w, h, data[24], data[25]


def scan(target):
    root = Path(target)
    files = sorted(root.rglob("*.png")) if root.is_dir() else [root]
    shapes = {}
    depths = set()
    ctypes = set()
    for path in files:
        w, h, depth, ctype = ihdr(path)
        shapes["%dx%d" % (w, h)] = shapes.get("%dx%d" % (w, h), 0) + 1
        depths.add(depth)
        ctypes.add(ctype)
    print("IHDR dir=%s n=%d shapes=%s depth=%s colortype=%s"
          % (target, len(files), ",".join("%s:%d" % kv for kv in sorted(shapes.items())),
             sorted(depths), sorted(ctypes)))
    return len(files), len(shapes)


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("用法：ihdr_scan.py <目录|png> [...]", file=sys.stderr)
        raise SystemExit(2)
    total = 0
    distinct = set()
    for arg in sys.argv[1:]:
        n, k = scan(arg)
        total += n
        distinct.add(k)
    print("IHDR-SCAN files=%d distinct-shapes-per-dir=%s" % (total, sorted(distinct)))
