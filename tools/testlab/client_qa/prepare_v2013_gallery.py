"""Freeze twelve native v2013 visual captures; never launches a client."""
from pathlib import Path
import hashlib
import json

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / "build/v2013-ui-qa"
SANDBOX = ROOT / "build/client-testlab-mui2-v12138"
PREFIX = "gtsr.critical.nei.simple."


def main():
    OUT.mkdir(exist_ok=True)
    spec = json.loads((ROOT / "build/v208-ui-qa/client-launch.json").read_text(encoding="utf-8"))
    cmd = spec["command"]
    assert Path(cmd[cmd.index("--gameDir") + 1]).resolve() == SANDBOX.resolve()
    assert cmd[cmd.index("--accessToken") + 1] == "0"
    spec["command"] = ["-Xmx3G" if arg.startswith("-Xmx") else arg for arg in cmd]
    spec["scope"] = "One owned offline client; private hidden desktop; heap3GiB/reserve2GiB; preparation never launches"
    (OUT / "client-launch.json").write_text(json.dumps(spec, indent=2), encoding="utf-8")
    rows = []

    def add(name, kind, operation, **extra):
        build = operation.startswith("build_")
        catalogue = "LOOM" if build else "STELLAR_LIFECYCLE" if operation.startswith("stellar_") else kind
        labels = [dict(x=8, y=6, key=PREFIX + ("build.title" if build else operation + ".title"), color="EAC879")]
        row = dict(id="v2013." + name, name="v2013." + name, v2011=True, v2013=True,
                   kind="nei-" + catalogue.lower(), variant="visual", visual="nei", neiKind=catalogue,
                   machineKind=kind, operation=operation, language="zh_CN", width=1280, height=720,
                   scale=2, minimumFrames=2, minimumDrawTicks=3, timeoutSeconds=45, labels=labels,
                   route="Actual registered GT recipe map/cached recipe/native NEI slots and renderer",
                   note="Read-only visual capture; no construction, payment, fuel consumption or physical Shift/Z")
        row.update(extra)
        rows.append(row)

    for name, operation in [("construction.foundation", "build_0"), ("construction.body", "build_1"),
                            ("construction.equipment", "build_2")]:
        add(name, "SUN", operation)
    add("turbine.initial", "TURBINE", "turbine", minimumDrawTicks=3, animationPair="turbine")
    add("turbine.later", "TURBINE", "turbine", minimumDrawTicks=160, animationPair="turbine")
    add("chemical.high-mixture", "BATTERY", "chemical_window_1")
    add("stellar.fusion", "SUN", "sun")
    add("stellar.ignition", "SUN", "stellar_ignition")
    add("stellar.sustain", "SUN", "stellar_sustain")
    for name, entry, page in [("tooltip.native-old", "LargeSteamFurnace", 0),
                              ("tooltip.native-sun.default", "CriticalSun", 0),
                              ("tooltip.native-sun.z-page", "CriticalSun", 1)]:
        add(name, "SOLAR", "solar_water", nativeMachineTooltipEntry=entry,
            nativeMachineTooltipPage=page, tooltipX=30, tooltipY=22)
    assert len(rows) == 12
    plan = OUT / "gallery-v2013-compact.json"
    plan.write_text(json.dumps(rows, ensure_ascii=False, indent=2), encoding="utf-8")
    sources = [ROOT / "src/main/java/com/miaokatze/gtsr" / p for p in
               ["client/nei/CriticalNEIShowcase.java", "api/recipe/CriticalNEILayout.java",
                "api/recipe/CriticalRecipeFrontend.java", "api/recipe/CriticalRecipeMaps.java",
                "client/tooltip/GTSRTooltipHandler.java", "common/critical/CriticalTooltips.java"]]
    manifest = dict(sha256=hashlib.sha256(plan.read_bytes()).hexdigest(), captures=len(rows),
                    status="PREPARED_NOT_LAUNCHED", hiddenDesktopRequired=True, heapGiB=3, reserveGiB=2,
                    physicalInput="NOT_RUN", businessTransactions="NOT_RUN",
                    nativeTooltipPaging="Registered ItemStack.getTooltip; installed Chromatic compatibility renderer and public TooltipHandler.nextTooltipPage; physical Z NOT_RUN",
                    sourceSHA256={str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources})
    plan.with_suffix(".manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    print(json.dumps(dict(plan=str(plan), **manifest), indent=2))


if __name__ == "__main__":
    main()
