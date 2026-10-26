"""Prepare compact v2011 native recipe screenshots and measured geometry; never launches."""
from pathlib import Path
import hashlib
import json

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / "build/v2011-ui-qa"
SANDBOX = ROOT / "build/client-testlab-mui2-v12138"
PREFIX = "gtsr.critical.nei.simple."


def label(x, y, key=None, color="ADBDC6", **value):
    return dict(x=x, y=y, color=color, **({"key": PREFIX + key} if key else value))


def main():
    OUT.mkdir(exist_ok=True)
    spec = json.loads((ROOT / "build/v208-ui-qa/client-launch.json").read_text(encoding="utf-8"))
    cmd = spec["command"]
    assert Path(cmd[cmd.index("--gameDir") + 1]).resolve() == SANDBOX.resolve()
    assert cmd[cmd.index("--accessToken") + 1] == "0"
    spec["command"] = ["-Xmx4G" if arg.startswith("-Xmx") else arg for arg in cmd]
    spec["scope"] = "One existing owned offline client on a private hidden desktop; heap4GiB; no launch in preparation"
    (OUT / "client-launch.json").write_text(json.dumps(spec, indent=2), encoding="utf-8")
    rows = []

    def add(name, kind, operation, **options):
        construction = operation.startswith("build_")
        catalogue = "LOOM" if construction else "IRRADIATION" if operation == "solar_dust" else "STELLAR_LIFECYCLE" if operation.startswith("stellar_") else kind
        labels = [label(8, 7, "build.title" if construction else operation + ".title", "EAC879")]
        if construction:
            labels += [label(8, 27, "build.stage." + operation[6:], "E1E8EB"),
                       label(8, 150, "build.time")]
        else:
            labels += [label(8, 27, "input"), label(146, 27, "energy" if operation.startswith("chemical_") else "output")]
            if operation.startswith("chemical_"):
                labels += [label(146, 70, color="EAC879", text="EU"), label(8, 96, color="EAC879", baseEu=True),
                           label(8, 112, color="E1E8EB", recipeDuration=True), label(8, 128, "chemical.ratio." + operation[-1])]
            elif operation == "stellar_ignition":
                labels += [label(8, 96, color="E1E8EB", policyField="IGNITION_EU", suffix=" EU"), label(8, 112, "ignition.time")]
            elif operation == "stellar_sustain":
                labels += [label(8, 96, color="E1E8EB", policyField="SUSTAIN_EUT", suffix=" EU/t"),
                           label(8, 112, color="E1E8EB", recipeDuration=True), label(8, 128, "sustain.always")]
            elif operation == "sun":
                labels += [label(8, 96, color="E1E8EB", recipeDuration=True), label(8, 110, color="E1E8EB", fusionEu=True),
                           label(8, 124, "per_parallel"), label(8, 140, "fusion.sustain")]
            else:
                labels += [label(8, 96, color="E1E8EB", text="400 t / 0 EU"), label(8, 112, "per_parallel" if operation == "solar_dust" else "ratio")]
        row = dict(id="v2011." + name, name="v2011." + name, v2011=True, kind="nei-" + catalogue.lower(),
                   variant="visual", visual="nei", neiKind=catalogue, machineKind=kind, operation=operation,
                   language="zh_CN", width=1280, height=720, scale=2, minimumFrames=2, minimumDrawTicks=3,
                   timeoutSeconds=30, labels=labels, route="Actual registered recipe map and native GT cached recipe",
                   note="One compact real-recipe view; no explanatory pages, business payment or construction")
        if not construction:
            row["arrowCenterY"] = 49 if operation == "solar_dust" else 73
        row.update(options)
        rows.append(row)

    for name, kind, operation in [("boiler.water", "SOLAR", "solar_water"), ("boiler.salt", "SOLAR", "solar_salt"),
                                  ("irradiation", "SOLAR", "solar_dust"), ("stellar.ignition", "SUN", "stellar_ignition"),
                                  ("stellar.sustain", "SUN", "stellar_sustain"), ("stellar.fusion", "SUN", "sun"),
                                  ("chemical.mixture", "BATTERY", "chemical_window_1"),
                                  ("construction.foundation", "SUN", "build_0"),
                                  ("construction.body", "SUN", "build_4"), ("construction.equipment", "SUN", "build_5")]:
        add(name, kind, operation)
    rows.append(dict(id="v2011.stellar.cold-terminal", name="v2011.stellar.cold-terminal", kind="CRITICAL_SUN",
                     variant="terminal", page=1, width=1280, height=720, scale=2,
                     route="Actual owned HubTerminal -> production Critical factory -> real synchronized MUI2 runtime tab",
                     note="Cold unformed owned fixture; display/status only, no ignition payment or machine operation"))
    add("en.chemical.mixture", "BATTERY", "chemical_window_1", language="en_US")
    plan = OUT / "gallery-v2011-compact.json"
    plan.write_text(json.dumps(rows, ensure_ascii=False, indent=2), encoding="utf-8")
    manifest = dict(sha256=hashlib.sha256(plan.read_bytes()).hexdigest(), captures=len(rows), status="PREPARED_NOT_LAUNCHED",
                    hiddenDesktopRequired=True, heapGiB=4, physicalInput="NOT_RUN", businessTransactions="NOT_RUN",
                    alignmentEvidence="Actual native frame/stack/button geometry, actual font metrics, source-declared origins and subsequent raw PNG pixel/visual inspection")
    manifest["labelLayoutSources"] = {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in
                                     [ROOT / "src/main/java/com/miaokatze/gtsr/client/nei/CriticalNEIShowcase.java",
                                      ROOT / "src/main/java/com/miaokatze/gtsr/api/recipe/CriticalRecipeFrontend.java"]}
    plan.with_suffix(".manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    print(json.dumps(dict(plan=str(plan), **manifest), indent=2))


if __name__ == "__main__":
    main()
