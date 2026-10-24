"""Bind the completed R1 history/grant evidence to a later candidate without claiming a rerun."""
from pathlib import Path
import argparse
import hashlib
import json
import zipfile

ROOT = Path(__file__).resolve().parents[3]
PREFIXES = tuple("com/miaokatze/gtsr/" + name for name in (
    "common/items/HubTerminal", "common/terminal/MachineHistoryFactory", "common/terminal/MachineTerminalHistory",
    "common/terminal/RemoteTerminalFactory", "common/terminal/RemoteTerminalGuiData"))


def sha(data):
    return hashlib.sha256(data).hexdigest()


def entries(path):
    with zipfile.ZipFile(path) as archive:
        return {name: sha(archive.read(name)) for name in archive.namelist()
                if name.endswith(".class") and name.startswith(PREFIXES)}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--final-jar", required=True, type=Path)
    args = parser.parse_args()
    source = ROOT / "build/v209-candidate/gtsr-v209-candidate.jar"
    target = args.final_jar.resolve(strict=True)
    first, final = entries(source), entries(target)
    assert first and final
    names = sorted(set(first) | set(final))
    rows = [dict(entry=name, sourceSHA256=first.get(name), finalSHA256=final.get(name),
                 identical=name in first and name in final and first[name] == final[name]) for name in names]
    result = dict(scope="Selected HubTerminal/history/session/remote Grant classes only; R1 full client 3x30s and remote evidence inherited by exact byte identity, not a final-candidate client rerun. Layout/render classes are outside this comparison.",
                  sourceCandidate=str(source), sourceCandidateSHA256=sha(source.read_bytes()),
                  finalCandidate=str(target), finalCandidateSHA256=sha(target.read_bytes()),
                  allSelectedClassesIdentical=all(row["identical"] for row in rows), entries=rows)
    output = ROOT / "plan/implementation/ui-miniature-v209/verification/history-candidate-binding.json"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, indent=2), encoding="utf-8")
    print(json.dumps({key: result[key] for key in ("sourceCandidateSHA256", "finalCandidateSHA256", "allSelectedClassesIdentical")}, indent=2))


if __name__ == "__main__":
    main()
