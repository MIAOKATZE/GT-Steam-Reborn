"""Freeze the two registered native-chart hover targets; never launch or alter an instance."""
from pathlib import Path
import argparse
import hashlib
import json
import sys

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT.parent / "tools/testlab"))
import host


def cases():
    rows = []
    for target, chart, count, kind in [
        ("CriticalSolar", "boiler.dimension", 45, "HEAT"),
        ("MegaSteamTurbineArray", "gtsr.mega.upgrades", 6, "BARS"),
    ]:
        prefix = "boiler" if target == "CriticalSolar" else "turbine"
        base = dict(target=target, context="inventory", unicode=False, scale=2, shift=False,
                    chartId=chart, variants=count, chartKind=kind, chartRows=6,
                    valueColumns=3 if target == "CriticalSolar" else 6)
        sequence = [("common", "COMMON", None, 0)]
        if target == "CriticalSolar":
            sequence.append(("tab", "COMMON", "select", 0))
        sequence.extend([
            ("variant", "COMMON", "variant", 1),
            ("detail", "DETAIL", "tier", 1),
            ("quick", "QUICK", "tier", 1),
            ("structure", "QUICK", None, 1),
            ("common-restored", "COMMON", "tier", 1),
        ])
        for label, tier, action, active in sequence:
            row = dict(base, id=prefix + "-" + label, tier=tier, active=active)
            if action:
                row["action"] = action
            if label == "structure":
                row["shift"] = True
            if label == "tab":
                row["selectedChart"] = chart
            rows.append(row)
    return rows


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bundle", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    artifacts = host.bundle_artifacts(args.bundle.resolve(strict=True), "gtsr")
    expected = {row["modId"]: row["sha256"] for row in artifacts}
    if set(expected) != {"gtsr", "miaokatzecore"}:
        raise ValueError("Exactly the current GTSR/Core testlab pair is required")
    plan = dict(scriptOnly="gtsr-native-tooltip-charts-v2", language="zh_CN",
                expectedArtifacts=expected, cases=cases(),
                scope="Real registered inventory hover, Chromatic chart/guide components and synthetic native keyboard events; physical OS input NOT_RUN")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    raw = (json.dumps(plan, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
    args.output.write_bytes(raw)
    manifest = dict(status="PREPARED_NOT_RUN", sha256=hashlib.sha256(raw).hexdigest(),
                    bundle=str(args.bundle.resolve()), expectedArtifacts=expected,
                    sourceSHA256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                    caseCount=len(plan["cases"]),
                    launch="python -B tools/testlab/host.py run --mod gtsr --lane client --task ui --bundle <same bundle> --client-plan <this plan> --prepare --execute")
    args.output.with_suffix(".manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(manifest, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
