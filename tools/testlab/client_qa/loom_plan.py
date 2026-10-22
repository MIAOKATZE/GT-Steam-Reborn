"""Prepare six owned visual captures of the real loom construction UI; does not launch a client."""
from pathlib import Path
import argparse
import hashlib
import json


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--workspace", type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[3]
    workspace = args.workspace.resolve()
    if workspace.parent != root / "build":
        raise ValueError("Use an owned direct build child")
    steps = []
    def add(op, **kwargs):
        steps.append(dict(op=op, **kwargs))
    for route, state in [("native", "pouring"), ("terminal", "pouring"),
                         ("terminal", "curing"), ("terminal", "base"),
                         ("terminal", "body"), ("terminal", "paused")]:
        name = "loom-" + route + "-" + state
        add("group", name=name)
        add("galleryPrepare", side="server", kind="CRITICAL_LOOM", terminal=route == "terminal")
        add("wait", ticks=15)
        add("galleryLoomSeed", side="server", state=state)
        add("galleryOpen", side="server", id=name)
        add("galleryExpect", id=name, timeoutTicks=100)
        add("wait", ticks=20)
        add("click", widget="critical_native_construction" if route == "native" else "critical_tab_4")
        add("wait", ticks=20)
        add("galleryLoomFreezeCheck", side="server")
        add("galleryLoomCapture", name=name, route=route)
        if route == "native":
            add("galleryLoomCloseChild")
            add("wait", ticks=5)
        if state == "paused":
            add("wait", ticks=30)
            add("galleryLoomFreezeCheck", side="server")
            add("galleryLoomCapture", name=name + "-stable", route=route, screenshot=False)
        add("galleryClose")
        add("wait", ticks=5)
    add("group", name="loom-owned-cleanup")
    add("galleryCleanup", side="server")
    output = workspace / "loom-ui-six.json"
    raw = (json.dumps(steps, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
    output.write_bytes(raw)
    manifest = dict(status="PREPARED_NOT_RUN", sha256=hashlib.sha256(raw).hexdigest(),
                    steps=len(steps), screenshots=6,
                    scope="Owned passive server journal fixtures, real production GUI/S2C/GL; not actual construction/payment/curing-duration validation")
    output.with_suffix(".manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    print(json.dumps(manifest, indent=2))


if __name__ == "__main__":
    main()
