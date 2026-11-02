"""Prepare a finite client-review extension to the existing owned TASK03 helper. Never launch a game."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "build/task03-client-qa"
FIXTURE = ROOT / "build/client-testlab-task03-gt265"


def replace_once(text, old, new):
    if text.count(old) != 1:
        raise ValueError("Owned TASK03 source seam changed: " + old[:120])
    return text.replace(old, new, 1)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--compile", action="store_true")
    args = parser.parse_args()
    if (ROOT.parent / ".gtsr-native-server.lease").exists():
        raise RuntimeError("Owned native process holds the lease; do not replace its helper")
    subprocess.run([sys.executable, str(ROOT / "tools/workeffects/native_qa/prepare.py")], cwd=ROOT, check=True)
    sources = OUT / "qa-src"
    helper = sources / "Task03ClientQa.java"
    text = helper.read_text(encoding="utf-8")
    text = replace_once(text, "    final Mui2ClientQa q;", "    final Mui2ClientQa q;\n    final ClientReviewNativeQa review;")
    text = replace_once(text, 'q=owner;out=Paths.get(System.getProperty("gtsr.uiqa.receipt")).getParent();',
                        'q=owner;out=Paths.get(System.getProperty("gtsr.uiqa.receipt")).getParent();review=new ClientReviewNativeQa(this);')
    text = replace_once(text, "Object player=players.get(0);serverTicks++;", "Object player=players.get(0);serverTicks++;\n            if(review.beforeServer(player))return;")
    text = replace_once(text, '                call(type(LAB+"TestLabCommand"),"enter",player);',
                        '                if(number(field(player,"dimension|field_71093_bK"))!=number(call(type(LAB+"TestLabDimension"),"id")))call(type(LAB+"TestLabCommand"),"enter",player);')
    text = replace_once(text, "            if(!ready||pendingShot!=null)return;", "            review.client();\n            if(!ready||pendingShot!=null)return;")
    text = replace_once(text, "    void completeCase(Object frame)throws Exception {", "    void completeCase(Object frame)throws Exception {\n        review.completeCase();")
    text = replace_once(text, "    @SubscribeEvent public void render(TickEvent.RenderTickEvent event) {", "    @SubscribeEvent public void render(TickEvent.RenderTickEvent event) {\n        try{review.render(event);}catch(Throwable error){finish(error);}")
    text = replace_once(text, 'long decorativeMs=number(field(entry,"decorativeMs"));', 'long decorativeMs=number(field(entry,"sampledDecorativeMs"));')
    text = replace_once(text, '"budgetDeferredHosts","budgetRotations"', '"budgetDeferredHosts","budgetRotations","registeredHosts","waitingHosts","retiringHosts","hostAdmissions","hostRetirements","clearanceMemberChecks","clearanceAirChecks","clearanceWorldQueries"')
    text = replace_once(text, 'state.addProperty("entries",((Map<?,?>)field(type(CLIENT),"ENTRIES")).size());',
                        'state.addProperty("entries",((Map<?,?>)field(type(CLIENT),"ENTRIES")).size());\n        for(String key:new String[]{"CANDIDATES","RETIRING"}){int size=((Map<?,?>)field(type(CLIENT),key)).size();state.addProperty(key,size);check("review.lifecycle-"+key,size==0,"World-null clears new registration holders");}')
    text = replace_once(text, "    void recordWorldCleanup()throws Exception {", "    void recordWorldCleanup()throws Exception {\n        review.finish();")
    text = replace_once(text, 'if(finishing){done=true;q.receipt.addProperty("ownedCleanupFailure",error.toString());q.finish(error);}',
                        'if(finishing){done=true;q.receipt.addProperty("ownedCleanupFailure",error.toString());if(failure==null)failure=error;else if(failure!=error)failure.addSuppressed(error);q.finish(failure);}')
    helper.write_text(text, encoding="utf-8")
    shutil.copy2(Path(__file__).with_name("ClientReviewNativeQa.java"), sources / "ClientReviewNativeQa.java")
    runner = (OUT / "run-task03.py").read_text(encoding="utf-8")
    runner = replace_once(runner, "default='air_compressor'", "default='air_compressor,atmospheric_centrifuge,gear_steam_compressor,steam_fluid_drill,crust_steam_borer'")
    if runner.count("qa-compile-receipt.json") != 2:
        raise ValueError("Owned TASK03 runner compile-receipt seams changed")
    runner = runner.replace("qa-compile-receipt.json", "client-review-prepare-receipt.json")
    runner = replace_once(runner, "    helper=FIXTURE/'mods/gtsr-mui2-qa-owned-only.jar';assert sha(helper)==compile_receipt['jarSHA256']",
                          "    helper=FIXTURE/'mods/gtsr-mui2-qa-owned-only.jar';assert sha(helper)==compile_receipt['jarSHA256']\n"
                          "    assert sha(OUT/'qa-src/Mui2ClientQa.java')==compile_receipt['sourceSHA256']\n"
                          "    for name,expected in compile_receipt['sources'].items():assert sha(OUT/'qa-src'/name)==expected")
    compile(runner, str(OUT / "run-client-review.py"), "exec")
    (OUT / "run-client-review.py").write_text(runner, encoding="utf-8")
    receipt = {"status": "PREPARED_NOT_RUN", "scope": __doc__, "heapGiB": 4, "hiddenDesktopRequired": True,
               "registryCap": 512, "candidateCap": 512,
               "sources": {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(sources.glob("*.java"))}}
    if args.compile:
        spec = json.loads((OUT / "client-launch.json").read_text(encoding="utf-8"))
        classes = OUT / ("qa-review-classes-" + hashlib.sha256(json.dumps(receipt["sources"], sort_keys=True).encode()).hexdigest()[:12])
        classes.mkdir(exist_ok=True)
        cp = spec["command"][spec["command"].index("-cp") + 1] + ";" + ";".join(str(p) for p in sorted((FIXTURE / "mods").glob("*.jar")))
        options = ["-encoding", "UTF-8", "-proc:none", "--release", "8", "-cp", cp, "-d", str(classes), *map(str, sorted(sources.glob("*.java")))]
        argfile = OUT / "qa-client-review-compile.args"
        argfile.write_text("\n".join('"' + s.replace("\\", "/") + '"' for s in options), encoding="utf-8")
        javac = Path(spec["command"][0]).with_name("javac.exe")
        result = subprocess.run([str(javac), "-J-Duser.language=en", "@" + str(argfile)], capture_output=True)
        (OUT / "qa-client-review-compile.stdout.log").write_bytes(result.stdout)
        (OUT / "qa-client-review-compile.stderr.log").write_bytes(result.stderr)
        if result.returncode:
            raise RuntimeError(result.stderr.decode("utf-8", errors="replace"))
        artifacts = sorted(classes.rglob("*.class"))
        if not all(p.relative_to(classes).parts[0] == "audit" for p in artifacts):
            raise ValueError("Unexpected output outside the owned audit package")
        jar = FIXTURE / "mods/gtsr-mui2-qa-owned-only.jar"
        with zipfile.ZipFile(jar, "w") as archive:
            for p in artifacts:
                archive.writestr(zipfile.ZipInfo(p.relative_to(classes).as_posix(), date_time=(1980, 1, 1, 0, 0, 0)), p.read_bytes())
        receipt.update(status="COMPILED_NOT_RUN", jarSHA256=hashlib.sha256(jar.read_bytes()).hexdigest(),
                       sourceSHA256=hashlib.sha256((sources / "Mui2ClientQa.java").read_bytes()).hexdigest(), classCount=len(artifacts),
                       classMajors=sorted({int.from_bytes(p.read_bytes()[6:8], "big") for p in artifacts}))
    (OUT / "client-review-prepare-receipt.json").write_text(json.dumps(receipt, indent=2) + "\n", encoding="utf-8")
    print(receipt["status"] + "; entry=" + str(OUT / "run-client-review.py") + "; game NOT_RUN")


if __name__ == "__main__":
    main()
