"""Freeze seven bounded v2014 native captures; preparation never launches a client."""
from pathlib import Path
import hashlib
import json

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / "build/v2014-ui-qa"
SANDBOX = ROOT / "build/client-testlab-mui2-v12138"


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

    def nei(name, kind, operation, **extra):
        build = operation.startswith("build_")
        row = dict(id="v2014." + name, name="v2014." + name, v2011=True, v2014=True,
                   kind="nei-" + ("loom" if build else "battery"), variant="visual", visual="nei",
                   neiKind="LOOM" if build else "BATTERY", machineKind=kind, operation=operation,
                   language="zh_CN", width=1280, height=720, scale=2, minimumFrames=2,
                   minimumDrawTicks=3, timeoutSeconds=45,
                   labels=[dict(x=8, y=6, key="gtsr.critical.nei.simple." + ("build.title" if build else "chemical_compound.title"), color="EAC879")],
                   route="Actual registered GT recipe map/cached recipe/native NEI slots and production renderer",
                   note="Read-only visual capture; no construction, payment, fuel consumption, physical Shift or Z")
        row.update(extra)
        rows.append(row)

    for name, operation in [("construction.foundation", "build_0"),
                            ("construction.body", "build_1"), ("construction.equipment", "build_2")]:
        nei(name, "SUN", operation)
    for rank in ["lowest", "middle", "highest"]:
        nei("chemical." + rank, "BATTERY", "chemical_compound", peakRank=rank)
    rows.append(dict(id="v2014.chemical.terminal", name="v2014.chemical.terminal", v2014=True,
                     kind="CRITICAL_BATTERY", variant="terminal", language="zh_CN", page=1,
                     width=1280, height=720, scale=2, minimumFrames=2, timeoutSeconds=45,
                     route="Owned TestLab real GT controller -> current production MUI2 terminal factory",
                     note="Explicit owned visual fixture; unformed and disabled; no real construction or paid fuel receipt"))
    assert len(rows) == 7
    plan = OUT / "gallery-v2014-compact.json"
    plan.write_text(json.dumps(rows, ensure_ascii=False, indent=2), encoding="utf-8")
    sources = [ROOT / "src/main/java/com/miaokatze/gtsr" / p for p in [
        "client/nei/CriticalNEIShowcase.java", "api/recipe/CriticalNEILayout.java",
        "api/recipe/CriticalRecipeFrontend.java", "api/recipe/CriticalRecipeMaps.java",
        "common/critical/CriticalChemicalRecipes.java", "common/critical/CompoundRecipe.java",
        "common/gui/MTECriticalMachineTerminalGui.java", "common/gui/CriticalRuntimeDiagram.java"]]
    manifest = dict(sha256=hashlib.sha256(plan.read_bytes()).hexdigest(), captures=len(rows),
                    status="PREPARED_NOT_LAUNCHED", hiddenDesktopRequired=True, heapGiB=3, reserveGiB=2,
                    physicalInput="NOT_RUN", physicalZ="NOT_RUN", businessTransactions="NOT_RUN",
                    sourceSHA256={str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources})
    plan.with_suffix(".manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    print(json.dumps(dict(plan=str(plan), **manifest), indent=2))


if __name__ == "__main__":
    main()
