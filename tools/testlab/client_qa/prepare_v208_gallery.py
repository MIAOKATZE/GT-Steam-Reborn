"""Prepare thirteen explicit v2.0.8 captures and reuse an owned offline launch spec. Never launch a client."""
from pathlib import Path
import hashlib
import json

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / "build/v208-ui-qa"
SANDBOX = ROOT / "build/client-testlab-mui2-v12138"


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    OUT.mkdir(exist_ok=True)
    source = ROOT / "build/ui-calibration-v12139/client-launch.json"
    launch = json.loads(source.read_text(encoding="utf-8"))
    command = launch["command"]
    if Path(command[command.index("--gameDir") + 1]).resolve() != SANDBOX.resolve():
        raise ValueError("Existing launch spec must target the exact owned sandbox")
    if command[command.index("--accessToken") + 1] != "0":
        raise ValueError("Only the existing offline QA identity may be reused")
    launch["command"] = ["-Xmx5G" if value.startswith("-Xmx") else value for value in command]
    launch["scope"] = "Reused exact owned offline sandbox; v208 plan prepared, NOT_LAUNCHED, heap max 5GiB"
    (OUT / "client-launch.json").write_text(json.dumps(launch, indent=2), encoding="utf-8")
    rows = []

    def add(name, group, **options):
        rows.append(dict(id=group, name="v208." + name, width=1280, height=720, scale=2, **options))

    loom = dict(kind="CRITICAL_LOOM", variant="terminal", rememberHistory=True, rich=True,
                route="Owned real HubTerminal onItemUseFirst / CriticalMachineTerminalFactory",
                note="真实owned织机builder；长状态仅为视觉fixture，控制器IDLE、未形成，不施工、不付费。")
    add("loom.home", "v208.loom", page=0, **loom)
    add("loom.ledger", "v208.loom", page=0,
        clicks=["critical_details_13", "critical_economy_family_S"], **loom)
    add("loom.runtime", "v208.loom", page=1, clicks=["critical_runtime_disclosure"], **loom)
    add("loom.inventory", "v208.loom", page=2, **loom)
    add("sun.runtime", "v208.sun", kind="CRITICAL_SUN", variant="terminal", rememberHistory=True,
        rich=True, page=1, route="Owned real HubTerminal onItemUseFirst / CriticalMachineTerminalFactory",
        note="真实Sun运行页；固定长状态视觉fixture，不运行批次、不支付资源。")
    narrow = dict(loom)
    add("loom.narrow-scroll", "v208.loom", page=0, scroll="critical_terminal_viewport", **narrow)
    rows[-1].update(width=427, height=240)
    for kind, title in (("LOOM", "建设目录"), ("SUN", "恒星运行目录"), ("BATTERY", "化学运行目录")):
        add("nei." + kind.lower(), "v208.nei." + kind.lower(), kind="nei-" + kind.lower(),
            variant="visual", visual="nei", neiKind=kind,
            route="GuiCraftingRecipe.openRecipeGui(RecipeMap.unlocalizedName)",
            note="真实注册NEI handler与配方屏：" + title + "。无伪造NEI绘制。")
    add("terminal.history", "v208.history", kind="CRITICAL_LOOM", variant="terminal",
        rememberHistory=True, actualHistory=True,
        route="Real server MachineHistoryFactory.open with held original owned HubTerminal",
        note="织机和Sun真实开窗自然产生历史；匹配windowId/PSM；不点历史行、不创建远程grant。")
    add("terminal.remote-sun", "v208.remote", kind="CRITICAL_SUN", variant="terminal",
        rememberHistory=True, actualRemote=True, page=1,
        route="Real history-row hit / production Session C2S index / RemoteTerminalFactory",
        note="点击真实历史行访问已加载 Sun；核验服务器签发 grant、factory/window/PSM 以及客户端空 Tile。未进行玩家跨维度移动。")
    for shifted in (False, True):
        name = "shift" if shifted else "default"
        add("tooltip." + name, "v208.tooltip." + name, kind="tooltip-" + name, variant="visual",
            visual="critical_tooltip_fontproof", tooltipKind="SUN", tooltipShift=shifted,
            route="Actual registered item -> GTSRMachineTooltips.rewrite -> client font renderer",
            note="真实字体renderproof；程序选择文案分支，不宣称物品hover/Shift键盘/物理输入通过。")
    if len(rows) != 13 or any(row.get("page", 0) not in (0, 1, 2) for row in rows):
        raise ValueError("Exactly thirteen captures and only the current three Critical pages are allowed")
    plan = OUT / "gallery-v208-13.json"
    plan.write_text(json.dumps(rows, ensure_ascii=False, indent=2), encoding="utf-8")
    manifest = dict(sha256=sha(plan), captures=13, status="PREPARED_NOT_RUN", physicalInputAndIME="NOT_RUN",
                    sourceLaunchSHA256=sha(source), heapGiBLimit=5,
                    actualMods=sorted(path.name for path in (SANDBOX / "mods").glob("*.jar")
                                      if path.name.startswith(("GT5-", "ModularUI2-", "NotEnoughItems-", "miaokatzecore-"))))
    plan.with_suffix(".manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    inventory = {}
    for row in rows:
        entry = inventory.setdefault(row["id"], dict(id=row["id"], title=row["id"], category="v208 有限抽样",
                                                     kind=row["kind"], route=row["route"], shots=[]))
        entry["shots"].append(row["name"])
    (OUT / "gallery-inventory.json").write_text(json.dumps(list(inventory.values()), ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(dict(plan=str(plan), captures=len(rows), state="PREPARED_NOT_LAUNCHED", sha256=sha(plan)), indent=2))


if __name__ == "__main__":
    main()
