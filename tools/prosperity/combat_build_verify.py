"""Capture a real Gradle build and verify that its production inputs stay fixed."""
import argparse
import hashlib
import json
import subprocess
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def inputs():
    paths = [ROOT / "gradle.properties"]
    paths += sorted(p for p in (ROOT / "src/main").rglob("*") if p.is_file())
    return {p.relative_to(ROOT).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest() for p in paths}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--tagged", action="store_true")
    args = parser.parse_args()
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=True)
    command = ["cmd", "/c", "gradlew.bat", "build"]
    if args.tagged:
        command.append("--no-configuration-cache")
    before = inputs()
    started = time.monotonic()
    result = subprocess.run(command, cwd=ROOT, capture_output=True)
    output = result.stdout + result.stderr
    (out / "build.log").write_bytes(output)
    after = inputs()
    receipt = {"command": command, "exit": result.returncode,
               "seconds": round(time.monotonic() - started, 3),
               "inputStable": before == after, "inputs": after,
               "logSHA256": hashlib.sha256(output).hexdigest()}
    (out / "receipt.json").write_text(json.dumps(receipt, indent=2), encoding="utf-8")
    print(output.decode("utf-8", errors="replace")[-2500:])
    print(json.dumps({k: v for k, v in receipt.items() if k != "inputs"}))
    if result.returncode or before != after:
        raise SystemExit(result.returncode or 1)


if __name__ == "__main__":
    main()
