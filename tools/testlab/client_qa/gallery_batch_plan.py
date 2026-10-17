"""Freeze independent visual capture events; never launch or control a client.

Each plan row requests exactly one real GL screenshot. These events deliberately
contain no functional script operations, assertions, business actions, or waits.
"""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[3]
COMMON = ROOT / "src/main/java/com/miaokatze/gtsr/common"
ENUMS = COMMON / "api/enums/MetaTileEntityID.java"
CATALOG = COMMON / "dimension/prosperity/lore/JournalCatalog.java"
LANGUAGE = ROOT / "src/main/resources/assets/gtsr/lang/zh_CN.lang"
NODES = (
    "STEAM_CACHE_NODE", "REINFORCED_STEAM_CACHE_NODE", "OVERPRESSURE_STEAM_CACHE_NODE",
    "WATER_CACHE_NODE", "REINFORCED_WATER_CACHE_NODE", "OVERPRESSURE_WATER_CACHE_NODE",
    "SINGULARITY_STEAM_COMPARTMENT", "SINGULARITY_STEAM_OUTPUT_COMPARTMENT",
    "SINGULARITY_FLUID_INPUT_COMPARTMENT", "SINGULARITY_FLUID_OUTPUT_COMPARTMENT",
)
STORAGE = {"HUB_STORAGE_UNIT", "REINFORCED_HUB_STORAGE_UNIT", "OVERPRESSURE_HUB_STORAGE_UNIT"}
HATCHES = {"STEAM_HUB_INPUT_HATCH", "STEAM_HUB_OUTPUT_HATCH", "WATER_HUB_INPUT_HATCH", "WATER_HUB_OUTPUT_HATCH"}
RICH_NOTE = "长状态、高位工作读数及填充背包的受控视觉夹具；保持 state=IDLE、结构未形成，不运行配方或其他业务。"
BOUND_NOTE = "已绑定状态的单节点视觉夹具：仅设置 owned 节点绑定状态，无付费绑定及枢纽结构；展示生产节点编辑窗口，不验收绑定业务。"
VISUAL_NOTE = "仅构造隔离的客户端视觉夹具并真实 GL 截图；不宣称通过物品入口、服务器鉴权、解锁、剧情推进或业务验收。"


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def journal_long_entries():
    """Select one actual current catalog entry per tab using current zh_CN text."""
    values = dict(line.split("=", 1) for line in LANGUAGE.read_text(encoding="utf-8").splitlines()
                  if "=" in line and not line.startswith("#"))
    rows = re.findall(r'new\s+JournalEntry\(\s*JournalTab\.([A-Z]+)\s*,\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*(\d+)',
                      CATALOG.read_text(encoding="utf-8"))
    longest = {}
    for tab, entry_id, key, count in rows:
        paragraphs = [values.get(f"{key}.p{index}", "") for index in range(int(count))]
        if not all(paragraphs):
            continue
        size = sum(map(len, paragraphs))
        if tab not in longest or size > longest[tab][0]:
            longest[tab] = (size, entry_id)
    if set(longest) != {"CHAPTERS", "BIOMES", "ITEMS", "STRUCTURES"}:
        raise ValueError("Current JournalCatalog must supply readable entries in all four tabs")
    return {tab: value[1] for tab, value in longest.items()}


def build():
    text = ENUMS.read_text(encoding="utf-8").split("public final int ID")[0]
    kinds = re.findall(r"^\s*([A-Z][A-Z_]+)\(\d+\)", text, re.M)
    critical = [kind for kind in kinds if kind.startswith("CRITICAL_") and kind != "CRITICAL_SINGULARITY_COMPRESSOR"]
    if len(kinds) != 78 or len(critical) != 10:
        raise ValueError(f"Inventory changed: expected 78 machine kinds / 10 Critical, got {len(kinds)} / {len(critical)}")
    inventory, events = [], []
    index = {}

    def entry(kind, variant="native", ui_id=None, category=None, title=None, route=None, fixture_note=None, reason=None):
        ui_id = ui_id or "ui." + kind.lower() + (".terminal" if variant == "terminal" else "")
        record = {"id": ui_id, "title": title or kind.replace("_", " "), "category": category or ("Critical" if kind in critical else "普通机器"),
                  "kind": kind, "variant": variant,
                  "route": route or ("生产机器普通右键" if variant == "native" else "手持枢纽终端生产入口"), "shots": []}
        if fixture_note:
            record["fixtureNote"] = fixture_note
        if reason:
            record["reason"] = reason
        inventory.append(record)
        index[ui_id] = record
        return record

    def capture(record, suffix, narrow=False, **extra):
        if record.get("reason"):
            raise ValueError("Cannot request a capture for an unreachable inventory entry")
        name = "batch." + record["id"] + "." + suffix
        if name in record["shots"]:
            raise ValueError("Duplicate capture: " + name)
        event = {"id": record["id"], "name": name, "kind": extra.get("visual", record["kind"]), "variant": record["variant"],
                 "width": 427 if narrow else 1280, "height": 240 if narrow else 720, "scale": 2, **extra}
        events.append(event)
        record["shots"].append(name)
        return event

    # Registered machine surfaces. No inherited builder is used to invent an entry.
    for kind in kinds:
        reason = None
        if kind in STORAGE:
            reason = "存储单元仅有继承的界面 builder；生产玩家右键路径没有开窗入口，不绕过入口调用 builder 伪造截图。"
        elif kind in HATCHES:
            reason = "生产 onRightclick 仅处理绑定/解绑等状态并返回 true，不为普通玩家打开 GUI；虽存在继承 builder，仍不构造伪入口。"
        record = entry(kind, reason=reason)
        if not reason:
            capture(record, "normal.overview")

    for kind in critical:
        record = entry(kind, "terminal")
        for page in range(4):
            capture(record, f"normal.page-{page}", page=page)
        if kind == "CRITICAL_PROCESSING":
            capture(record, "normal.processing-modes-dropdown", page=1, click="critical_processing_modes",
                    note="仅展开配置下拉菜单，不选择模式或执行配方。")

    extras = (
        ("TCDS_CONTROLLER", "tcds", "终端"),
        ("STEAM_HUB_ARRAY", "steam_hub", "Hub"),
        ("WATER_HUB_ARRAY", "water_hub", "Hub"),
        ("SINGULARITY_DRILLING_HUB", "drilling_hub", "Hub"),
        ("CLUSTER_CONTROLLER", "cluster", "Cluster"),
        ("SINGULARITY_CRUST_STEAM_BORER", "aggregator", "聚合器"),
    )
    for kind, short_id, category in extras:
        route = "生产机器潜行右键配置入口" if short_id == "aggregator" else "生产机器终端窗口入口"
        record = entry(kind, "terminal", "ui.terminal." + short_id, category, route=route)
        if short_id == "cluster":
            for page in range(5):
                capture(record, f"normal.page-{page}", page=page)
        else:
            capture(record, "normal.overview")
    for kind in NODES:
        record = entry(kind, "terminal", "ui.terminal.node_editor." + kind.lower(), "节点编辑器", fixture_note=BOUND_NOTE)
        capture(record, "normal.overview", note=BOUND_NOTE)
        capture(record, "normal.rate-editor", page=0, click="hub_rate", note="仅真实点击打开速率编辑页，不确认提交任何数值。")
        if "OUTPUT" not in kind:
            capture(record, "normal.capacity-editor", page=0, click="hub_capacity", note="先经取消返回主页面，再真实点击容量编辑；不确认、不执行付费升级。")
        else:
            record["fixtureNote"] += " 输出节点容量编辑按生产规则禁用，不伪造容量页或付费升级。"

    # Explicit visual-only surfaces replace all legacy functional script fragments.
    chest = entry("chest", "visual", "ui.terminal.unsealed_chest", "宝箱", "解封宝箱",
                  route="UnsealedChestFactory 客户端视觉夹具，不宣称玩家开箱入口验收", fixture_note=VISUAL_NOTE)
    capture(chest, "normal.visual", visual="chest", note=VISUAL_NOTE)
    tool_specs = (
        ("tuning_wand", "协调棒家族菜单", ("wand_families",)),
        ("machine_debug", "协调棒机器配置", ("wand_profile_construction", "wand_profile_singularity")),
        ("prosperity_journal", "繁荣手记", ("journal",)),
        ("prosperity_navigator", "繁荣导航器", ("navigator",)),
        ("spacetime_altar_story", "时空祭坛剧情", ("altar",)),
    )
    long_entries = journal_long_entries()
    for kind, title, visuals in tool_specs:
        record = entry(kind, "visual", "ui.client." + kind, "工具与剧情", title,
                       route="独立客户端视觉夹具，不宣称生产物品/S2C入口验收", fixture_note=VISUAL_NOTE)
        for visual in visuals:
            if visual == "journal":
                for tab in ("CHAPTERS", "BIOMES", "ITEMS", "STRUCTURES"):
                    capture(record, "normal.journal-" + tab.lower(), visual=visual, journalTab=tab, note=VISUAL_NOTE)
                    capture(record, "normal.journal-long-" + tab.lower(), visual=visual, journalTab=tab,
                            journalEntry=long_entries[tab], note="现行 JournalCatalog 中该页签中文正文最长的可读条目；全权限视觉夹具，无剧情推进。")
            else:
                capture(record, "normal." + visual, visual=visual, note=VISUAL_NOTE)

    for kind in ("CRITICAL_LOOM", "CRITICAL_PROCESSING"):
        for variant in ("native", "terminal"):
            ui_id = "ui." + kind.lower() + (".terminal" if variant == "terminal" else "")
            record = index[ui_id]
            record["fixtureNote"] = RICH_NOTE
            for page in ((0,) if variant == "native" else (0, 2, 3)):
                capture(record, f"normal.rich-page-{page}", page=page, rich=True, note=RICH_NOTE)
            if variant == "native":
                capture(record, "normal.rich-long-bottom", rich=True, scroll="@native", note=RICH_NOTE)

    # Narrow canvases remain the same catalog entries; only new capture names are added.
    for kind in critical:
        for variant in ("native", "terminal"):
            ui_id = "ui." + kind.lower() + (".terminal" if variant == "terminal" else "")
            record = index[ui_id]
            for page in ((None,) if variant == "native" else range(4)):
                suffix = "narrow.overview" if page is None else f"narrow.page-{page}"
                extra = {} if page is None else {"page": page}
                capture(record, suffix, narrow=True, **extra)

    # Pilot events are literal copies of full-plan events, including their names.
    pilot_names = []
    for size in ("normal", "narrow"):
        pilot_names.append("batch.ui.critical_loom." + size + ".overview")
        pilot_names.extend("batch.ui.critical_loom.terminal." + size + f".page-{page}" for page in range(4))
    pilot_names.extend(("batch.ui.critical_processing.terminal.normal.page-1",
                        "batch.ui.critical_processing.terminal.normal.processing-modes-dropdown"))
    by_name = {event["name"]: event for event in events}
    pilot = [dict(by_name[name]) for name in pilot_names]
    all_names = [event["name"] for event in events]
    if len(inventory) != 110 or sum(bool(record.get("reason")) for record in inventory) != 7:
        raise ValueError("Expected exactly 110 inventory entries including 7 unreachable entries")
    if len(all_names) != len(set(all_names)) or len(pilot) != 12:
        raise ValueError("Capture names must be unique and pilot must have exactly 12 events")
    if any(event["name"] not in all_names for event in pilot):
        raise ValueError("Pilot must be an exact subset of the full plan")
    return pilot, events, inventory, long_entries


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--workspace", type=Path, required=True)
    parser.add_argument("--revision", default="r1")
    args = parser.parse_args(argv)
    if not re.fullmatch(r"r[1-9][0-9]*", args.revision):
        parser.error("--revision 必须为 r1、r2 等不可变修订号")
    output = args.workspace.resolve()
    names = [f"gallery-batch-{part}-{args.revision}.json" for part in ("pilot", "all", "inventory")]
    targets = [output / name for name in names]
    for target in targets:
        if target.exists():
            raise FileExistsError("拒绝覆盖已冻结计划：" + str(target))
    pilot, events, inventory, long_entries = build()
    output.mkdir(parents=True, exist_ok=True)
    for target, payload in zip(targets, (pilot, events, inventory)):
        with target.open("x", encoding="utf-8", newline="\n") as stream:
            stream.write(json.dumps(payload, ensure_ascii=False, indent=2) + "\n")
    summary = {"status": "PREPARED_NOT_RUN", "inventoryCount": len(inventory),
               "reachable": sum(not record.get("reason") for record in inventory), "unreachable": 7,
               "pilotCaptures": len(pilot), "fullCaptures": len(events),
               "canvasCounts": dict(Counter(f'{event["width"]}x{event["height"]}@{event["scale"]}' for event in events)),
               "journalLongEntries": long_entries,
               "files": {target.name: sha(target) for target in targets},
               "sources": {str(path.relative_to(ROOT)): sha(path) for path in (ENUMS, CATALOG, LANGUAGE)},
               "scope": "独立快速 UI 视觉事件队列，仅静态生成；不混功能脚本，不运行客户端，不宣称完整功能验收。"}
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
