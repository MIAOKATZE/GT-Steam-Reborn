"""Prepare bounded native NEI captures; never starts a client."""
from pathlib import Path
import hashlib
import json

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / "build/nei-v2010-qa"
SANDBOX = ROOT / "build/client-testlab-mui2-v12138"


def main():
    OUT.mkdir(exist_ok=True)
    source = ROOT / "build/v208-ui-qa/client-launch.json"
    spec = json.loads(source.read_text(encoding="utf-8"))
    cmd = spec["command"]
    assert Path(cmd[cmd.index("--gameDir") + 1]).resolve() == SANDBOX.resolve()
    assert cmd[cmd.index("--accessToken") + 1] == "0"
    spec["command"] = ["-Xmx4G" if value.startswith("-Xmx") else value for value in cmd]
    spec["scope"] = "Existing owned offline identity, one private hidden desktop, heap4GiB"
    (OUT / "client-launch.json").write_text(json.dumps(spec, indent=2), encoding="utf-8")
    rows = []

    def add(name, machine, operation, page=0, **options):
        nei = "LOOM" if operation.startswith("build_") else "IRRADIATION" if operation == "solar_dust" else machine
        row = dict(id="v2010.nei." + name, name="v2010.nei." + name, v2010=True,
                   kind="nei-" + nei.lower(), variant="visual", visual="nei", neiKind=nei,
                   machineKind=machine, operation=operation, infoPage=page,
                   language="zh_CN", width=1280, height=720, scale=2,
                   minimumFrames=2, minimumDrawTicks=2, timeoutSeconds=30,
                   route="Registered public NEI map -> real GT handler -> native recipe page",
                   note="Natural renderer/framebuffer; synthetic local recipe selection; no business action")
        row.update(options)
        rows.append(row)

    # Final page counts are tied to the production catalogue contract.
    for machine, operation, pages in [("SOLAR", "solar_water", 2), ("SOLAR", "solar_salt", 2),
                                      ("SOLAR", "solar_dust", 2), ("TURBINE", "turbine", 2),
                                      ("SUN", "sun", 3), ("BATTERY", "battery", 3)]:
        for page in range(pages):
            add(operation + ".p" + str(page), machine, operation, page)
    add("build.foundation", "SUN", "build_0")
    for stage in (1, 2, 3):
        add("build.stage%d.p0" % stage, "SUN", "build_" + str(stage))
    for page in (0, 1):
        add("build.stage4.p%d" % page, "SUN", "build_4", page, multiPage=True)
    add("sun.animation.a", "SUN", "sun", minimumDrawTicks=3)
    add("sun.animation.b", "SUN", "sun", minimumDrawTicks=23)
    add("sun.reduced.a", "SUN", "sun", minimumDrawTicks=3, reducedMotion=True)
    add("sun.reduced.b", "SUN", "sun", minimumDrawTicks=23, reducedMotion=True)
    add("tooltip.item", "SOLAR", "solar_dust", tooltipType="item", queryRU=True)
    add("tooltip.fluid", "SOLAR", "solar_water", tooltipType="fluid", queryRU=True)
    add("en.sun.longest", "SUN", "sun", -1, language="en_US", longest=True)
    add("en.battery.longest", "BATTERY", "battery", -1, language="en_US", longest=True)
    plan = OUT / "gallery-nei-v2010.json"
    plan.write_text(json.dumps(rows, ensure_ascii=False, indent=2), encoding="utf-8")
    manifest = dict(sha256=hashlib.sha256(plan.read_bytes()).hexdigest(), captures=len(rows),
                    status="PREPARED_NOT_LAUNCHED", hiddenDesktopRequired=True, heapGiB=4,
                    nativeNEI=True, physicalInput="NOT_RUN", businessTransactions="NOT_RUN")
    plan.with_suffix(".manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    print(json.dumps(dict(plan=str(plan), **manifest), indent=2))


if __name__ == "__main__":
    main()
