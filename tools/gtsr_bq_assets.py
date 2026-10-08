#!/usr/bin/env python3
"""GTSR adapter for the sibling BQ editor's parameterized asset generator.

The editor remains read-only. Typed item NBT is validated by its codec and
preserved verbatim; stale files are pruned only within this fixed quest line.
"""
import argparse
import copy
import importlib.util
import json
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
EDITOR_ROOT = REPO_ROOT.parent / "GT-MIAO-Web-BQ-Trade"
LINE_HIGH = 5139824686389002240
LINE_LOW = 1


def load_module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def within(path, root):
    resolved = Path(path).resolve()
    if not resolved.is_relative_to(Path(root).resolve()):
        raise ValueError("path outside owned root: %s" % path)
    return resolved


def owned_json(plan, shared):
    """Find line-owned JSON, including previous line/quest title slugs."""
    base = within(plan.base, plan.args.repo_root)
    suffix = "-" + shared.b64_id(LINE_HIGH, LINE_LOW)
    paths = []
    for kind in ("Quests", "QuestLines"):
        root = within(base / kind, base)
        if not root.exists():
            continue
        for directory in root.iterdir():
            if not directory.is_dir() or not directory.name.endswith(suffix):
                continue
            within(directory, root)
            for path in directory.glob("*.json"):
                within(path, directory)
                # A foreign UUID file accidentally placed here is never deleted.
                if path.name == "QuestLine.json" and kind == "QuestLines":
                    paths.append(path)
                else:
                    encoded = path.stem.rsplit("-", 1)[-1]
                    try:
                        raw = shared.base64.b64decode(encoded, validate=True)
                        high, _ = shared.struct.unpack(">QQ", raw)
                    except (ValueError, shared.struct.error):
                        continue
                    if high == LINE_HIGH:
                        paths.append(path)
    return sorted(paths)


def install_adapter(shared, codec):
    original_parse = shared.parse_stack
    original_stack = shared.stack_nbt
    original_tasks = shared.build_tasks

    def parse_stack(item):
        result = original_parse(item)
        nbt = item.get("nbt") if isinstance(item, dict) else None
        if nbt is not None:
            codec.bq_json_to_tree(nbt)
        oredict = item.get("oredict", "") if isinstance(item, dict) else ""
        if not isinstance(oredict, str):
            raise ValueError("oredict must be a string")
        return (*result, copy.deepcopy(nbt), oredict)

    def stack_nbt(ident, meta, count, nbt=None, oredict=""):
        result = original_stack(ident, meta, count)
        result["OreDict:8"] = oredict
        if nbt is not None:
            codec.bq_json_to_tree(nbt)
            result["tag:10"] = copy.deepcopy(nbt)
        return result

    shared.parse_stack = parse_stack
    shared.stack_nbt = stack_nbt
    shared.icon_nbt = lambda icon: stack_nbt(*parse_stack(icon))

    def build_tasks(tasks):
        result = {}
        for index, task in enumerate(tasks or []):
            if task["type"] == "location":
                if task.get("dimension_key") != "prosperity":
                    raise ValueError("GTSR location must use dimension_key=prosperity")
                entry = {
                    "index:3": index, "taskID:8": "bq_standard:location",
                    "name:8": task.get("name", "Prosperity"),
                    "posX:3": 0, "posY:3": 0, "posZ:3": 0,
                    "dimension:3": int(task.get("dimension", 78)),
                    "biome:3": -1, "structure:8": "", "range:3": int(task.get("range", -1)),
                    "visible:1": 0, "hideInfo:1": 0, "invert:1": 0, "taxiCabDist:1": 0,
                    "gtsrDimension:8": "prosperity",
                }
                if entry["range:3"] != -1:
                    raise ValueError("GTSR prosperity location must cover the whole dimension")
            else:
                entry = original_tasks([task])["0:10"]
                entry["index:3"] = index
            result["%d:10" % index] = entry
        return result

    shared.build_tasks = build_tasks
    original_audit = shared.mode_audit
    original_rewrite_lang = shared.rewrite_lang_file

    def rewrite_lang_file(path, new_block):
        # Replace only the anchor span; suffix includes its existing line ending.
        with open(path, "r", encoding="utf-8", newline="") as stream:
            raw = stream.read()
        lines = raw.splitlines(keepends=True)
        begin, end = new_block[0], new_block[-1]
        bi = [i for i, text in enumerate(lines) if text.strip() == begin]
        if not bi:
            return original_rewrite_lang(path, new_block)
        ei = [i for i, text in enumerate(lines) if text.strip() == end]
        if len(bi) != 1 or len(ei) != 1 or ei[0] <= bi[0]:
            raise ValueError("invalid lang anchors: %s" % path)
        start = sum(map(len, lines[:bi[0]]))
        finish = sum(map(len, lines[:ei[0]])) + len(lines[ei[0]].rstrip("\r\n"))
        sep = "\r\n" if "\r\n" in raw else "\n"
        with open(path, "w", encoding="utf-8", newline="") as stream:
            stream.write(raw[:start] + sep.join(new_block) + raw[finish:])
        return "replaced"

    shared.rewrite_lang_file = rewrite_lang_file

    def mode_generate(plan, dry):
        files = plan.files()
        expected = {within(path, plan.base) for path, _ in files}
        stale = [path for path in owned_json(plan, shared) if path.resolve() not in expected]
        # Resolve all paths before any write/delete, including symlink targets.
        within(plan.lang_dir, plan.args.repo_root)
        for fname in ("en_US.lang", "zh_CN.lang"):
            within(Path(plan.lang_dir) / fname, plan.lang_dir)
        changes = 0
        for path, payload in files:
            target = Path(path)
            if target.exists() and target.read_text(encoding="utf-8") == payload:
                continue
            changes += 1
            if dry:
                print("would write  " + str(target.relative_to(plan.args.repo_root)))
            else:
                shared.write_if_changed(path, payload)
        for path in stale:
            print(("would prune  " if dry else "pruned       ") + str(path.relative_to(plan.args.repo_root)))
            if not dry:
                path.unlink()
                if not any(path.parent.iterdir()):
                    path.parent.rmdir()
        print("quests=%d JSON changes=%d stale=%d" % (len(plan.quests), changes, len(stale)))
        if dry:
            for fname, zh in (("en_US.lang", False), ("zh_CN.lang", True)):
                path = Path(plan.lang_dir) / fname
                block = shared.build_lang_block(plan.args.lang_pack_tag, plan.line,
                                                plan.quests, plan.high, plan.low, zh)
                raw = path.read_text(encoding="utf-8") if path.exists() else ""
                if "\n".join(block) not in raw:
                    print("would update lang block " + str(path.relative_to(plan.args.repo_root)))

    def mode_audit(args):
        quests, line = shared.load_ws(args.ws_root, args.ws_id)
        plan = shared.Plan(args, quests, line)
        files = plan.files()
        expected = {Path(path).resolve() for path, _ in files}
        problems = []
        for path, payload in files:
            path = within(path, plan.base)
            if not path.is_file():
                problems.append("missing: " + str(path))
            elif json.loads(path.read_text(encoding="utf-8")) != json.loads(payload):
                problems.append("JSON differs: " + str(path))
        # Check all JSON in owned directories, including unexpected/foreign files.
        suffix = "-" + shared.b64_id(LINE_HIGH, LINE_LOW)
        for kind in ("Quests", "QuestLines"):
            root = within(Path(plan.base) / kind, plan.base)
            if root.exists():
                for directory in root.iterdir():
                    if directory.is_dir() and directory.name.endswith(suffix):
                        within(directory, root)
                        for path in directory.glob("*.json"):
                            if within(path, directory) not in expected:
                                problems.append("extra JSON: " + str(path))
        if problems:
            for problem in problems:
                print(problem)
            return 1
        return original_audit(args)

    shared.mode_generate = mode_generate
    shared.mode_audit = mode_audit
    return shared


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--editor-root", type=Path, default=EDITOR_ROOT)
    parser.add_argument("--ws-root", type=Path)
    parser.add_argument("--repo-root", type=Path, default=REPO_ROOT)
    parser.add_argument("--modid", choices=("gtsr",), default="gtsr")
    parser.add_argument("--pack-root", choices=("bqquests",), default="bqquests")
    parser.add_argument("--ws-id", default="gtsr")
    parser.add_argument("--mode", choices=("dry-run", "generate", "audit"), default="dry-run")
    args = parser.parse_args(argv)
    shared = load_module("gtsr_shared_bq", args.editor_root / "tools/generate_bq_assets.py")
    codec = load_module("gtsr_editor_nbt", args.editor_root / "app/nbt.py")
    install_adapter(shared, codec)
    ws_root = args.ws_root or args.editor_root
    _, line = shared.load_ws(str(ws_root), args.ws_id)
    if int(line.get("id_low", 1)) != LINE_LOW or int(line.get("order_index", 900)) != 900:
        raise ValueError("GTSR quest line UUID/order must remain 1/900")
    return shared.main(["--repo-root", str(args.repo_root.resolve()),
                        "--ws-root", str(ws_root.resolve()), "--modid", args.modid,
                        "--pack-root", args.pack_root, "--ws-id", args.ws_id,
                        "--line-high", str(LINE_HIGH), "--order-index", "900",
                        "--mode", args.mode])


if __name__ == "__main__":
    raise SystemExit(main())
