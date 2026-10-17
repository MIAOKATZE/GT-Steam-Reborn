"""Copy a read-only Prism pack into a new owned offline QA client, without accounts or worlds."""
from pathlib import Path
import argparse
import hashlib
import json
import re
import shutil

ROOT = Path(__file__).resolve().parents[3]


def artifact(lib):
    group, name, version, *classifier = lib["name"].split(":")
    filename = name + "-" + version + ("-" + classifier[0] if classifier else "") + ".jar"
    return Path(group.replace(".", "/")) / name / version / filename


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True, help="Prism instance root containing patches and .minecraft")
    parser.add_argument("--prism-cache", type=Path, default=Path.home() / "AppData/Roaming/PrismLauncher")
    parser.add_argument("--jar", type=Path, required=True, help="Production obfuscated jar, never a dev/sources jar")
    parser.add_argument("--workspace", type=Path, default=ROOT / "build/mui2-client-qa")
    parser.add_argument("--client-sandbox", type=Path, default=ROOT / "build/client-testlab-mui2")
    parser.add_argument("--java", type=Path, default=Path.home() / ".gradle/jdks/azul_systems__inc_-21-amd64-windows.2/bin/java.exe")
    parser.add_argument("--exclude-mod", action="append", default=[], help="Additional exact filenames to exclude from the QA copy")
    args = parser.parse_args()
    source, cache, out, dest = args.source.resolve(strict=True), args.prism_cache.resolve(strict=True), args.workspace.resolve(), args.client_sandbox.absolute()
    if dest.resolve() != dest or dest.parent != ROOT / "build" or not dest.name.startswith("client-testlab-") or out.parent != ROOT / "build":
        raise ValueError("Use unredirected direct owned build children only")
    if dest.exists():
        raise ValueError("Sandbox already exists; prepare only once, then compile/run explicitly")
    if "-dev" in args.jar.name or "-sources" in args.jar.name:
        raise ValueError("A production obfuscated jar is required")
    libraries, jvm, main_class = {}, [], None
    for patch in sorted((json.loads(p.read_text(encoding="utf-8")) for p in (source / "patches").glob("*.json")), key=lambda p: p.get("order", 0)):
        jvm.extend(patch.get("+jvmArgs", []))
        main_class = patch.get("mainClass", main_class)
        for lib in patch.get("libraries", []) + ([patch["mainJar"]] if "mainJar" in patch else []):
            rules = lib.get("rules", [])
            allowed = not rules
            for rule in rules:
                if rule.get("os", {}).get("name", "windows") == "windows":
                    allowed = rule.get("action") == "allow"
            if not allowed or ("natives" in lib and "artifact" not in lib.get("downloads", {})):
                continue
            if "windows-arm64" in lib["name"] or "windows-x86" in lib["name"]:
                continue
            parts = lib["name"].split(":")
            libraries[tuple(parts[:2] + parts[3:])] = lib
    if not main_class or not libraries:
        raise ValueError("No usable patch-defined launch classpath")
    inputs = []
    for lib in libraries.values():
        path = source / "libraries" / artifact(lib).name if lib.get("MMC-hint") == "local" else cache / "libraries" / artifact(lib)
        if not path.is_file():
            raise FileNotFoundError(path)
        inputs.append((lib, path))
    index = cache / "assets/indexes/1.7.10.json"
    asset_data = json.loads(index.read_text())
    for entry in asset_data["objects"].values():
        path = cache / "assets/objects" / entry["hash"][:2] / entry["hash"]
        if not path.is_file() or path.stat().st_size != entry["size"]:
            raise FileNotFoundError(path)
    dest.mkdir()
    out.mkdir(parents=True, exist_ok=True)
    def copy(a, b):
        b.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(a, b)
    # No saves, backups, launcher files, account databases or original options are copied.
    for name in ("config", "scripts", "resourcepacks", "fonts"):
        if (source / ".minecraft" / name).exists():
            shutil.copytree(source / ".minecraft" / name, dest / name)
    for path in (source / ".minecraft/mods").rglob("*"):
        if not path.is_file() or path.name in args.exclude_mod or path.name.lower().startswith(("gtsr-v", "gtsr-outpost-", "gtsr-mui2-qa-")) or "criticalguilifecycle" in path.name.lower():
            continue
        copy(path, dest / "mods" / path.relative_to(source / ".minecraft/mods"))
    copy(args.jar.resolve(strict=True), dest / "mods/production-gtsr.jar")
    for path in (source / "natives").iterdir():
        if path.is_file():
            copy(path, dest / "natives" / path.name)
    cp, manifest = [], []
    for lib, path in inputs:
        target = dest / "libraries" / artifact(lib)
        copy(path, target)
        cp.append(str(target))
        manifest.append({"name": lib["name"], "sha256": hashlib.sha256(target.read_bytes()).hexdigest()})
    copy(index, dest / "assets/indexes/1.7.10.json")
    for entry in asset_data["objects"].values():
        rel = Path("objects") / entry["hash"][:2] / entry["hash"]
        if not (dest / "assets" / rel).exists():
            copy(cache / "assets" / rel, dest / "assets" / rel)
    # This contains mod sound resources, not player skins or authentication data.
    if (cache / "assets/asset_director").exists():
        shutil.copytree(cache / "assets/asset_director", dest / "assets/asset_director")
    cfg = dest / "config/gtsr/gtsr.cfg"
    text = cfg.read_text(encoding="utf-8-sig") if cfg.exists() else ""
    category = re.search(r"^\s*testlab\s*\{.*?^\s*\}", text, re.M | re.S)
    block = "testlab {\n    B:enabled=true\n    I:dimensionId=1048577\n    I:providerId=1048577\n}"
    text = text[:category.start()] + block + text[category.end():] if category else text + "\n" + block + "\n"
    cfg.parent.mkdir(parents=True, exist_ok=True)
    cfg.write_text(text, encoding="utf-8")
    command = [str(args.java.resolve(strict=True)), "-Xms512M", "-Xmx6G", *jvm, "-Dgtsr.uiqa.enabled=true", "-Djava.library.path=" + str(dest / "natives"), "-cp", ";".join(cp), main_class,
               "--username", "Mui2Audit", "--version", "1.7.10", "--gameDir", str(dest), "--assetsDir", str(dest / "assets"), "--assetIndex", "1.7.10", "--uuid", "00000000000000000000000000000138", "--accessToken", "0", "--userProperties", "{}", "--userType", "legacy", "--tweakClass", "cpw.mods.fml.common.launcher.FMLTweaker", "--width", "1280", "--height", "720"]
    (out / "client-launch.json").write_text(json.dumps({"command": command, "cwd": str(dest), "scope": "Owned offline profile; no accounts or worlds copied; NOT_LAUNCHED"}, indent=2), encoding="utf-8")
    (out / "client-preparation-receipt.json").write_text(json.dumps({"source": str(source), "destination": str(dest), "libraries": manifest, "assetObjectCount": len(asset_data["objects"]), "copiedWorlds": 0, "copiedAccounts": 0, "state": "PREPARED_NOT_LAUNCHED"}, indent=2), encoding="utf-8")
    print("Prepared owned offline client:", dest)


if __name__ == "__main__":
    main()
