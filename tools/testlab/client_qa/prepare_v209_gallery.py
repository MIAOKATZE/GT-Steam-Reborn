"""Freeze bounded v209 real-client QA; this command never starts a game."""
from pathlib import Path
import argparse
import hashlib
import json

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / "build/v209-ui-qa"
SANDBOX = ROOT / "build/client-testlab-mui2-v12138"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--previews-only", action="store_true")
    parser.add_argument("--compact", action="store_true", help="New compact model revision; implies previews-only and uses twenty stable frames")
    args = parser.parse_args()
    if args.compact:
        args.previews_only = True
    OUT.mkdir(exist_ok=True)
    source = ROOT / "build/v208-ui-qa/client-launch.json"
    spec = json.loads(source.read_text(encoding="utf-8"))
    command = spec["command"]
    assert Path(command[command.index("--gameDir") + 1]).resolve() == SANDBOX.resolve()
    assert command[command.index("--accessToken") + 1] == "0"
    spec["scope"] = "Existing exact owned offline QA sandbox, private hidden desktop required, heap <=5GiB"
    (OUT / "client-launch.json").write_text(json.dumps(spec, indent=2), encoding="utf-8")
    rows = []

    def add(name, **kwargs):
        rows.append(dict(id="v209." + name, name="v209." + name, v209=True,
                         width=1280, height=720, scale=2, **kwargs))

    add("terminal.history", kind="CRITICAL_SUN", variant="terminal", rememberHistory=True,
        actualHistory=True, c08History=True,
        route="PlayerControllerMP.sendUseItem -> real native C08 -> production HubTerminal -> queued history factory",
        note="Three real 30-second tick observations, actual local page hit-test and normal close; seven naturally recorded owned targets; physical input/TCP NOT_RUN")
    add("terminal.remote-sun", kind="CRITICAL_SUN", variant="terminal", rememberHistory=True,
        actualRemote=True, page=1,
        route="Real history-row hit -> production C2S Session -> server Grant -> remote factory/window/PSM",
        note="Same dimension loaded owned target; nullable client Tile/Grant; no cross-dimension travel or physical input/TCP claim")
    for kind in ("SOLAR", "TURBINE", "SUN", "BATTERY"):
        add("nei." + kind.lower(), kind="nei-showcase-" + kind.lower(), variant="visual", visual="nei",
            neiKind="SHOWCASE", showcaseKind=kind, previewProbe=True, minimumFrames=300,
            timeoutSeconds=60, reducedMotion=False,
            route="Actual registered construction recipe map / operation=showcase / production NEI renderer",
            note="Real NEI recipe screen, programmatic native recipe selection, automatic bounded miniature")
    add("loom.construction-paused", kind="CRITICAL_LOOM", variant="terminal", rememberHistory=True,
        page=0, constructionPreviewFixture=True, previewProbe=True, minimumFrames=300, timeoutSeconds=60,
        route="Real production loom terminal builder, explicitly injected read-only construction snapshot visual fixture",
        note="Paused visual fixture only; no actual construction, resource payment or S2C business assertion")
    for label, reduced, reload in (("reduced", True, False), ("reload", False, True)):
        add("nei.sun-" + label, kind="nei-showcase-sun", variant="visual", visual="nei", neiKind="SHOWCASE",
            showcaseKind="SUN", previewProbe=True, minimumFrames=300, timeoutSeconds=60,
            reducedMotion=reduced, reloadResources=reload,
            route="Actual NEI showcase; owned process-local low motion or actual Minecraft resource reload",
            note="Representative lifecycle rendering; no world or construction advancement")
    if args.previews_only:
        rows = [row for row in rows if row.get("previewProbe")]
        add("nei.sun-fallback", kind="nei-showcase-sun-fallback", variant="visual", visual="nei", neiKind="SHOWCASE",
            showcaseKind="SUN", fboDisabled=True, minimumFrames=30, timeoutSeconds=30, reducedMotion=False,
            route="Actual NEI showcase with owned process-local framebuffer option disabled",
            note="Real FBO-unavailable branch must draw the production 2D schematic; no private failure flag injection")
    if args.compact:
        for row in rows:
            row["minimumFrames"] = 20
            if row.get("previewProbe"):
                row["compactBudgetProbe"] = True
            if row["name"] == "v209.nei.sun":
                row["nativeOutputQuery"] = True
            if row.get("fboDisabled"):
                row["fboDisabled"] = False
                row["coldFallback"] = True
                row["minimumFrames"] = 1
                row["route"] = "Actual NEI first visible frame after owned native-scene cold clear"
                row["note"] = "Capture only while ready0/native lists0 before second upload call; preparing fallback, unsupported hardware NOT_RUN; skip if first draw already ready"
    plan = OUT / ("gallery-v209-compact.json" if args.compact else "gallery-v209-previews.json" if args.previews_only else "gallery-v209-bounded.json")
    plan.write_text(json.dumps(rows, ensure_ascii=False, indent=2), encoding="utf-8")
    plan.with_suffix(".manifest.json").write_text(json.dumps(dict(
        sha256=hashlib.sha256(plan.read_bytes()).hexdigest(), state="PREPARED_NOT_LAUNCHED",
        historyRounds=0 if args.previews_only else 3, historyObservationSeconds=0 if args.previews_only else 30,
        previewCaptures=8 if args.previews_only else 7,
        physicalInputAndIME="NOT_RUN", remoteTCP="NOT_RUN", hiddenDesktopRequired=True), indent=2), encoding="utf-8")
    print(json.dumps(dict(plan=str(plan), captureCount=len(rows), state="PREPARED_NOT_LAUNCHED")))


if __name__ == "__main__":
    main()
