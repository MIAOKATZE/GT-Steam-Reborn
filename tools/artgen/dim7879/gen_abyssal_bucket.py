#!/usr/bin/env python3
"""Vanilla 1.7.10 water bucket with its water palette darkened.

Source: assets/minecraft/textures/items/bucket_water.png from vanilla client.jar.
The embedded source keeps regeneration independent of local game installations.
Bucket metal, outline and alpha remain pixel-identical to the vanilla source.
"""
import base64
import hashlib
import io
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[3]
OUT_PATH = ROOT / "src/main/resources/assets/gtsr/textures/items/AbyssalObsessionBucket.png"
SIDECAR_PATH = Path(__file__).with_suffix(".sha256")
VANILLA_PNG = base64.b64decode("iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAtElEQVR42mNgGJTA1NT0PwwHBgbCMdEafVKW/w/KWoeCU2r24zcIpNE+fBYYG8Ve/28SfwsFW6U9ABsCwiC1GAaATAZJ6EZeBhuAbgjIABCGuRKrATdu3IArQDcAJl5UVITfABhGDkgQtkq9D8ZEGQAC7kVv/q9YsQKOQZqnTZuG2wBkQ0AARKMbgFczNlfADAHZTNB2QgYQ5QJsgYnsAoKaYQaAFMMMgGkm2gD0vICMaZLxAKBhOSA238OyAAAAAElFTkSuQmCC")
VANILLA_SHA256 = "c62115972bfe9efa49d6b1a744c51d465a5f619b6f5a2a30dfb2715c1969c497"


def source():
    assert hashlib.sha256(VANILLA_PNG).hexdigest() == VANILLA_SHA256
    return Image.open(io.BytesIO(VANILLA_PNG)).convert("RGBA")


def is_water(pixel):
    red, green, blue, alpha = pixel
    return alpha != 0 and blue > green > red


def build():
    original = source()
    result = original.copy()
    for y in range(original.height):
        for x in range(original.width):
            pixel = original.getpixel((x, y))
            if is_water(pixel):
                red, green, blue, alpha = pixel
                result.putpixel((x, y), (red * 45 // 100, green * 45 // 100, blue * 45 // 100, alpha))
    return result


def self_check(result):
    original = source()
    assert result.size == original.size == (16, 16) and result.mode == "RGBA"
    changed = 0
    for y in range(16):
        for x in range(16):
            before, after = original.getpixel((x, y)), result.getpixel((x, y))
            assert before[3] == after[3]
            if is_water(before):
                assert all(after[i] < before[i] for i in range(3))
                changed += 1
            else:
                assert before == after
    assert changed > 0
    return changed


def main():
    result = build()
    changed = self_check(result)
    assert result.tobytes() == build().tobytes()
    OUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    result.save(OUT_PATH, "PNG")
    assert Image.open(OUT_PATH).convert("RGBA").tobytes() == result.tobytes()
    sha = hashlib.sha256(OUT_PATH.read_bytes()).hexdigest()
    SIDECAR_PATH.write_text(f"{sha}  {OUT_PATH.name}\n", encoding="utf-8")
    print(f"OK {OUT_PATH.name}: {changed} water pixels darkened; metal/alpha unchanged; sha256={sha}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
