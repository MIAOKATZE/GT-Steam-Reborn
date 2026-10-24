"""Archive this bounded client's observed evidence without inheriting old PASS claims."""
from pathlib import Path
import argparse
import hashlib
import json
import shutil

ROOT = Path(__file__).resolve().parents[3]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--run", required=True)
    a = p.parse_args()
    source = ROOT / "build/v209-ui-qa/client-runs" / a.run
    assert source.parent == ROOT / "build/v209-ui-qa/client-runs"
    dest = ROOT / "plan/implementation/ui-miniature-v209/verification" / a.run
    dest.mkdir(parents=True, exist_ok=False)
    receipt = json.loads((source / "receipt.json").read_text(encoding="utf-8"))
    checks = json.loads((source / "checks.json").read_text(encoding="utf-8")) if (source / "checks.json").exists() else {}
    for name in ("receipt.json", "checks.json", "checks-live.json", "preflight.json", "plan.json", "plan-manifest.json",
                 "helper-compile-receipt.json", "helper.jar", "UiGalleryBatchV209.java", "UiGalleryBatch.java", "UiQaWireCounter.java",
                 "stdout.log", "memory-samples.jsonl", "batch-job-pilot-receipt.json"):
        path = source / name
        if path.is_file():
            shutil.copy2(path, dest / name)
    if (source / "screenshots").is_dir():
        shutil.copytree(source / "screenshots", dest / "screenshots")
    gl_contract = ROOT / "build/v209-ui-qa/gl-witness-api-contract.json"
    if gl_contract.is_file():
        shutil.copy2(gl_contract, dest / gl_contract.name)
    if checks.get("v209RawDriverCalibration"):
        (dest / "raw-driver-calibration.json").write_text(json.dumps(checks["v209RawDriverCalibration"], ensure_ascii=False, indent=2), encoding="utf-8")
    observations = checks.get("checks", [])
    memory = [json.loads(line) for line in (source / "memory-samples.jsonl").read_text(encoding="utf-8").splitlines()] if (source / "memory-samples.jsonl").exists() else []
    summary = dict(status=receipt.get("status"), candidateSHA256=receipt.get("candidateSHA256"),
                   helperSHA256=receipt.get("helperSHA256"), exitCode=receipt.get("exitCode"),
                   elapsedSeconds=receipt.get("elapsedSeconds"), forcedStop=receipt.get("forcedStop"),
                   candidateUnchanged=receipt.get("candidateUnchanged"), hiddenDesktop=receipt.get("hiddenDesktop"),
                   checkPass=sum(x.get("pass") is True for x in observations),
                   checkFail=sum(x.get("pass") is False for x in observations),
                   failedChecks=[x for x in observations if x.get("pass") is False],
                   notRunEvents=[event for event in checks.get("scriptEvents", []) if event.get("status") == "NOT_RUN"],
                   captures=[x.get("file") for x in receipt.get("screenshots", [])],
                   historyRounds=checks.get("v209HistoryRounds", []),
                   startupToTestLabReadyMillis=checks.get("startupToTestLabReadyMillis"),
                   heapGiB=receipt.get("heapGiB"),
                   peakSampledWorkingSetBytes=max((row.get("workingSet", 0) for row in memory), default=0),
                   peakWorkingSetCounterBytes=max((row.get("peakWorkingSet", 0) for row in memory), default=0),
                   peakSampledPrivateBytes=max((row.get("privateBytes", 0) for row in memory), default=0),
                   cleanupVerified=checks.get("batchCleanupVerified"),
                   runtimeCrashFindings=receipt.get("runtimeCrashFindings"),
                   constructionFixtureScope=checks.get("v209ConstructionFixtureScope"),
                   physicalInputAndIME="NOT_RUN", remoteTCP="NOT_RUN",
                   historyScope="Three real 30-second observed client/server tick rounds; not the full ten-round document proposal" if checks.get("v209HistoryRounds") else "NOT_RUN in this run; refer to separately bound history evidence",
                   sourceRunDirectory=str(source))
    (dest / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    manifest = [dict(file=str(path.relative_to(dest)), bytes=path.stat().st_size, sha256=sha(path))
                for path in sorted(dest.rglob("*")) if path.is_file()]
    (dest / "evidence-manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
