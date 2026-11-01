"""Prepare/compile one owned TASK03 native client helper and a hidden-desktop runner; never launch Minecraft."""
from pathlib import Path
import argparse
import hashlib
import json
import re
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / "build/task03-client-qa"
FIXTURE = ROOT / "build/client-testlab-task03-gt265"
SOURCE = OUT / "qa-src"


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def replace_once(text, old, new):
    assert text.count(old) == 1, old
    return text.replace(old, new)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--compile", action="store_true")
    args = parser.parse_args()
    if (ROOT.parent / ".gtsr-native-server.lease").exists():
        raise RuntimeError("An owned native process holds the lease; do not replace its helper")
    spec = json.loads((OUT / "client-launch.json").read_text(encoding="utf8"))
    assert Path(spec["command"][spec["command"].index("--gameDir") + 1]).resolve() == FIXTURE
    SOURCE.mkdir(parents=True, exist_ok=True)
    for path in (ROOT / "tools/testlab/client_qa").glob("*.java"):
        shutil.copy2(path, SOURCE / path.name)
    shutil.copy2(Path(__file__).with_name("Task03ClientQa.java"), SOURCE / "Task03ClientQa.java")
    base = SOURCE / "Mui2ClientQa.java"
    text = base.read_text(encoding="utf8")
    text = replace_once(text, 'name="GTSR owned MUI2 client QA"', 'name="GTSR owned TASK03 native QA"')
    text = replace_once(text, "    UiGalleryBatch galleryBatch;", "    UiGalleryBatch galleryBatch;\n    Task03ClientQa task03;")
    text = replace_once(text, 'Object listener=this;if("ticket-restart"',
                        'Object listener=this;if("task03".equals(System.getProperty("gtsr.uiqa.mode"))){task03=new Task03ClientQa(this);listener=task03;}else if("ticket-restart"')
    for name in ("stopping", "stopped"):
        marker = f"public void {name}(FMLServer{'Stopping' if name == 'stopping' else 'Stopped'}Event event) {{"
        text = replace_once(text, marker, marker + f"\n        if(task03!=null){{task03.{name}();return;}}")
    text = replace_once(text, 'receipt.addProperty("pilotSlotConservation","NOT_RUN: initial TCDS pilot has no business slots; scripted production slot results are individual checks/scriptEvents")',
                        'receipt.addProperty("businessConservation","NOT_RUN: declared owned supply/sink fixture; full B0 differential and conservation matrix is separate")')
    text = replace_once(text, 'FML RenderTick START-to-END wall time by bounded phase; full client/background mods included; not exclusive widget CPU, not benchmark attribution',
                        'NOT_RUN in TASK03 helper; actual renderer counters/screenshots are recorded separately, no performance attribution')
    base.write_text(text, encoding="utf8")

    cfg = FIXTURE / "config/gtsr/gtsr.cfg"
    if not cfg.exists():
        cfg.parent.mkdir(parents=True, exist_ok=True)
        cfg.write_text("testlab {\n    B:enabled=true\n}\n", encoding="utf8")
    assert re.search(r"(?ms)testlab\s*\{[^}]*B:enabled\s*=\s*true", cfg.read_text(encoding="utf8")), "Owned testlab opt-in required"

    runner = (ROOT / "tools/testlab/client_qa/run.py").read_text(encoding="utf8")
    runner = replace_once(runner, 'Synthetic actual ModularScreen hit-test/key/drag dispatch, lwjgl3ify injectTextEvent, legacy GuiScreen.keyTyped and native PlayerController.windowClick; physical mouse/keyboard/IME NOT_RUN',
                          'Owned native TestLab fixture and supply/sink; actual world/chunk/TESR render, subscription packets and screenshots; physical input/IME NOT_RUN')
    runner = replace_once(runner, "ROOT=Path(__file__).resolve().parents[3]", "ROOT=Path(" + repr(str(ROOT)) + ")")
    runner = replace_once(runner, "OUT=ROOT/'build/mui2-client-qa'", "OUT=ROOT/'build/task03-client-qa'")
    runner = replace_once(runner, "FIXTURE=ROOT/'build/client-testlab-mui2'", "FIXTURE=ROOT/'build/client-testlab-task03-gt265'")
    runner = replace_once(runner, "    OUT=a.workspace.resolve();FIXTURE=a.client_sandbox.resolve()",
                          "    OUT=a.workspace.resolve();FIXTURE=a.client_sandbox.resolve()\n    if OUT!=ROOT/'build/task03-client-qa' or FIXTURE!=ROOT/'build/client-testlab-task03-gt265':raise ValueError('TASK03 runner accepts its exact owned workspace/sandbox only')")
    runner = replace_once(runner, "choices=['ui','gallery','gallery-batch','ticket-restart']", "choices=['ui','gallery','gallery-batch','ticket-restart','task03']")
    runner = runner.replace("default='ui'", "default='task03'")
    runner = runner.replace("p.add_argument('--keep-open'", "p.add_argument('--machine',default='air_compressor');p.add_argument('--offscreen-check',action='store_true');p.add_argument('--keep-open'", 1)
    runner = replace_once(runner, "    if a.heap_gib>5:", "    if not a.hidden_desktop:raise ValueError('TASK03 owned client requires --hidden-desktop')\n    if a.heap_gib>4:")
    runner = runner.replace("client Java heap must not exceed 5GiB", "client Java heap must not exceed 4GiB")
    runner = runner.replace("p.add_argument('--heap-gib',type=int,default=5)", "p.add_argument('--heap-gib',type=int,default=4)")
    runner = runner.replace("destination.chmod(0o666);shutil.copy2(a.jar,destination)", "\n        if destination.exists():destination.chmod(0o666)\n        shutil.copy2(a.jar,destination)")
    runner = runner.replace("f'fullscreen:false\\npauseOnLostFocus:false", "f'particles:0\\nfboEnable:true\\nfullscreen:false\\npauseOnLostFocus:false")
    runner = runner.replace("cmd[1:1]=['-Dgtsr.uiqa.mode='", "cmd[1:1]=['-Dgtsr.uiqa.enabled=true','-Dgtsr.task03.machine='+a.machine,'-Dgtsr.task03.offscreen='+str(a.offscreen_check).lower(),'-Dgtsr.uiqa.mode='")
    runner = replace_once(runner, "for source in Path(__file__).parent.glob('*.java'):", "for source in Path(" + repr(str(SOURCE)) + ").glob('*.java'):")
    runner = replace_once(runner, "        receipt['screenshots']=[", "        receipt['uncaughtExceptions']=[dict(file=p.name,details=json.loads(p.read_text(encoding='utf-8'))) for p in result.glob('uncaught-*.json')]\n        if receipt['uncaughtExceptions']:receipt['status']='FAIL'\n        receipt['screenshots']=[")
    (OUT / "run-task03.py").write_text(runner, encoding="utf8")
    compile(runner, str(OUT / "run-task03.py"), "exec")

    receipt = dict(status="PREPARED_NOT_RUN", sandbox=str(FIXTURE), sourceRoot=str(SOURCE), hiddenDesktopRequired=True,
                   machineDefault="air_compressor", heapGiB=4, productionJarModified=False,
                   sources={p.name: sha(p) for p in sorted(SOURCE.glob("*.java"))})
    if args.compile:
        classes = OUT / ("qa-classes-" + hashlib.sha256(json.dumps(receipt["sources"], sort_keys=True).encode()).hexdigest()[:12])
        classes.mkdir(exist_ok=True)
        classpath = spec["command"][spec["command"].index("-cp") + 1] + ";" + ";".join(str(p) for p in sorted((FIXTURE / "mods").glob("*.jar")))
        options = ["-encoding", "UTF-8", "-proc:none", "--release", "8", "-cp", classpath, "-d", str(classes), *map(str, sorted(SOURCE.glob("*.java")))]
        argfile = OUT / "qa-compile.args"
        argfile.write_text("\n".join('"' + s.replace("\\", "/") + '"' for s in options), encoding="utf8")
        javac = Path(spec["command"][0]).with_name("javac.exe")
        result = subprocess.run([str(javac), "-J-Duser.language=en", "@" + str(argfile)], capture_output=True, text=True, encoding="utf8", errors="replace")
        (OUT / "qa-compile.log").write_text(result.stdout + result.stderr, encoding="utf8")
        if result.returncode:
            raise RuntimeError(result.stderr)
        helper = FIXTURE / "mods/gtsr-mui2-qa-owned-only.jar"
        artifacts = sorted(classes.rglob("*.class"))
        assert all(p.relative_to(classes).parts[0] == "audit" for p in artifacts)
        with zipfile.ZipFile(helper, "w") as archive:
            for path in artifacts:
                archive.writestr(zipfile.ZipInfo(path.relative_to(classes).as_posix(), date_time=(1980, 1, 1, 0, 0, 0)), path.read_bytes())
        receipt.update(status="COMPILED_NOT_RUN", jarSHA256=sha(helper), sourceSHA256=sha(base), classCount=len(artifacts),
                       classMajors=sorted({int.from_bytes(p.read_bytes()[6:8], "big") for p in artifacts}),
                       installScope="Owned client helper only, excluded from production deliverable")
        (OUT / "qa-compile-receipt.json").write_text(json.dumps(receipt, indent=2), encoding="utf8")
    (OUT / "task03-prepare-receipt.json").write_text(json.dumps(receipt, indent=2), encoding="utf8")
    print(json.dumps({k: receipt[k] for k in ("status", "sandbox", "hiddenDesktopRequired", "heapGiB")}, indent=2))


if __name__ == "__main__":
    main()
