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
    args = parser.parse_args()
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
    plan = OUT / "gallery-v209-history.json"
    plan.write_text(json.dumps(rows, ensure_ascii=False, indent=2), encoding="utf-8")
    plan.with_suffix(".manifest.json").write_text(json.dumps(dict(
        sha256=hashlib.sha256(plan.read_bytes()).hexdigest(), state="PREPARED_NOT_LAUNCHED",
        historyRounds=3, historyObservationSeconds=30,
        physicalInputAndIME="NOT_RUN", remoteTCP="NOT_RUN", hiddenDesktopRequired=True), indent=2), encoding="utf-8")
    print(json.dumps(dict(plan=str(plan), captureCount=len(rows), state="PREPARED_NOT_LAUNCHED")))


if __name__ == "__main__":
    main()
