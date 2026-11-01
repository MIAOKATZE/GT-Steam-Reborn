"""Shared, bounded file formats for the GTSR visual asset tools (no game execution)."""
import hashlib
import json
import math
from pathlib import Path, PurePosixPath


SCHEMA = 1
ROOT = Path(__file__).resolve().parents[2]
SPRITE_KEYS = {"albedo", "emission", "frozenAlbedo", "frozenEmission"}


def sha(data):
    return hashlib.sha256(data).hexdigest()


def file_sha(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"), allow_nan=False).encode("utf-8")


def write_json(path, value):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2, allow_nan=False) + "\n", encoding="utf-8", newline="\n")


def _unique_pairs(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("Duplicate JSON key: " + key)
        result[key] = value
    return result


def parse_json(raw):
    def reject_constant(value):
        raise ValueError("Non-finite JSON value: " + value)
    return json.loads(raw, object_pairs_hook=_unique_pairs, parse_constant=reject_constant)


def read_json(path):
    return parse_json(Path(path).read_text(encoding="utf-8-sig"))


def relative_name(value):
    """Only portable, relative POSIX paths are admitted to manifests and archives."""
    if not isinstance(value, str) or not value or "\\" in value or ":" in value:
        raise ValueError("Invalid relative path: " + str(value))
    path = PurePosixPath(value)
    if path.is_absolute() or any(part in ("", ".", "..") for part in value.split("/")):
        raise ValueError("Path escapes its root: " + value)
    return path.as_posix()


def rooted(root, name):
    path = (Path(root).resolve() / relative_name(name)).resolve()
    path.relative_to(Path(root).resolve())
    return path


def finite(value, name):
    if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value):
        raise ValueError("Expected finite number: " + name)
    return value


def walk_sprites(value):
    if isinstance(value, dict):
        for key, child in value.items():
            if key in SPRITE_KEYS and child:
                yield child
            else:
                yield from walk_sprites(child)
    elif isinstance(value, list):
        for child in value:
            yield from walk_sprites(child)


def check_finite_tree(value, depth=0):
    if depth > 48:
        raise ValueError("JSON nesting budget exceeded")
    if isinstance(value, float) and not math.isfinite(value):
        raise ValueError("Non-finite value")
    if isinstance(value, dict):
        for child in value.values():
            check_finite_tree(child, depth + 1)
    elif isinstance(value, list):
        for child in value:
            check_finite_tree(child, depth + 1)


def require_pass(report, label):
    if report.get("schema") != SCHEMA or report.get("status") != "PASS":
        raise ValueError(label + " must be a current-schema PASS report")
