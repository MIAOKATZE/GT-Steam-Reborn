#!/usr/bin/env python3
"""Run console-driven testlab scenarios in an isolated copy of an accepted GTNH server.

prepare copies binaries/config/mods/libraries only, never worlds or player data.
run replaces GTSR only in that copy and executes JSON command/expect steps.
The original instance and other Java processes are never modified.
"""

from __future__ import annotations

import argparse
import datetime as dt
import importlib.util
import json
import os
from pathlib import Path
import queue
import re
import shutil
import subprocess
import threading
import time


ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("gtsr_smoke", ROOT / "tools/prosperity/gtsr_server_smoke.py")
smoke = importlib.util.module_from_spec(spec)
spec.loader.exec_module(smoke)


def guarded(path: Path) -> Path:
    absolute = path.absolute()
    if (absolute.resolve() != absolute or absolute.parent != ROOT / "build"
            or not absolute.name.startswith("server-testlab-")):
        raise ValueError("Sandbox must be an unredirected build/server-testlab-* directory")
    # DirEntry reuses FindFirstFile metadata on Windows; Path.resolve on every
    # library/config file otherwise performs tens of thousands of redundant walks.
    pending = [absolute] if absolute.exists() else []
    while pending:
        with os.scandir(pending.pop()) as entries:
            for entry in entries:
                attributes = getattr(entry.stat(follow_symlinks=False), "st_file_attributes", 0)
                if entry.is_symlink() or attributes & 0x400:
                    raise ValueError(f"Redirected sandbox entry: {entry.path}")
                if entry.is_dir(follow_symlinks=False):
                    pending.append(Path(entry.path))
    return absolute


def prepare(args) -> None:
    sandbox = guarded(args.sandbox)
    source = args.instance.resolve(strict=True)
    if not smoke.accepted(source / "eula.txt"):
        raise ValueError("Source server must already have an accepted eula.txt")
    smoke.SANDBOX = sandbox
    smoke.guard = lambda: guarded(sandbox)
    smoke.prepare(args)
    properties = sandbox / "server.properties"
    generated = properties.read_text().splitlines()
    generated = [line for line in generated if not line.startswith(
        ("level-name=", "level-type=", "generate-structures="))]
    properties.write_text("\n".join(generated)
                          + "\nlevel-name=AgentTestLab\nlevel-type=FLAT\ngenerate-structures=false\n", encoding="ascii")
    preparation = sandbox / "preparation-receipt.json"
    receipt = json.loads(preparation.read_text(encoding="utf-8"))
    receipt["server_world"] = "AgentTestLab"
    smoke.write_json(preparation, receipt)


def run(args) -> None:
    runner_sha256 = smoke.sha256(Path(__file__))
    print("PRECHECK isolated server directory", flush=True)
    sandbox = guarded(args.sandbox)
    print("SANDBOX validated", flush=True)
    if not (sandbox / "preparation-receipt.json").is_file() or not smoke.accepted(sandbox / "eula.txt"):
        raise ValueError("Prepare the isolated accepted server first")
    scenario = json.loads(args.scenario.read_text(encoding="utf-8-sig"))
    if not scenario or any(not isinstance(s.get("command"), str) or "\n" in s["command"]
                           or "\r" in s["command"] for s in scenario):
        raise ValueError("Scenario must be a nonempty list of single-line command/expect/timeout objects")
    for step in scenario:
        re.compile(step["expect"])
    jar = args.jar.resolve(strict=True)
    if not any(m.get("modid", "").lower() == "gtsr" for m in smoke.jar_mods(jar)):
        raise ValueError("Explicit jar must contain GTSR mcmod.info")
    memory_deadline = time.monotonic() + args.wait_memory_seconds
    while smoke.free_memory_bytes() < (args.xmx_gib + 0.5) * 1024**3:
        if time.monotonic() >= memory_deadline:
            raise RuntimeError("Insufficient free memory for the requested server heap")
        print("WAITING for free memory; no server started and no other process modified", flush=True)
        time.sleep(10)
    print("MEMORY ready", flush=True)
    active = sandbox / "active-testlab-run.json"
    # Exclusive creation also prevents two agents from replacing the owned server jar.
    lock_fd = os.open(active, os.O_WRONLY | os.O_CREAT | os.O_EXCL)
    os.close(lock_fd)
    stamp = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
    result_dir = sandbox / "testlab-receipts" / stamp
    result_dir.mkdir(parents=True)
    receipt = {"source_jar": str(jar), "sha256": smoke.sha256(jar), "scenario": scenario,
               "runner_sha256": runner_sha256, "scenario_sha256": smoke.sha256(args.scenario),
               "sandbox": str(sandbox), "utc": stamp, "steps": [], "done_seen": False, "vanilla_done_seen": False,
               "forced_stop": False, "status": "RUNNING"}
    properties = (sandbox / "server.properties").read_text(encoding="utf-8-sig")
    lab_config = sandbox / "config/gtsr/gtsr.cfg"
    receipt["runtime_configuration"] = {
        "server_properties_sha256": smoke.sha256(sandbox / "server.properties"),
        "level_name": re.findall(r"^level-name=(.*)$", properties, re.M),
        "level_type": re.findall(r"^level-type=(.*)$", properties, re.M),
        "gtsr_config_sha256": smoke.sha256(lab_config) if lab_config.exists() else None,
    }
    process = None
    reader = None
    events = queue.Queue()
    started = time.monotonic()
    try:
        print("CHECKING copied mod inventory", flush=True)
        for index, previous in enumerate((sandbox / "mods").rglob("*.jar"), 1):
            if index % 100 == 0:
                print(f"CHECKED {index} copied mod jars", flush=True)
            if any(m.get("modid", "").lower() == "gtsr" for m in smoke.jar_mods(previous)):
                target = sandbox / "quarantined-mods" / stamp / previous.relative_to(sandbox / "mods")
                target.parent.mkdir(parents=True, exist_ok=True)
                previous.rename(target)
        destination = sandbox / "mods" / jar.name
        if destination.exists():
            raise RuntimeError(f"Candidate filename conflicts with an unrelated sandbox mod: {destination}")
        shutil.copy2(jar, destination)
        java = str(args.java.resolve(strict=True)) if args.java else shutil.which("java")
        command = [java, "-Xms1G", f"-Xmx{args.xmx_gib}G", "-Djava.awt.headless=true",
                   "-Dfml.readTimeout=180", "-Duser.language=en", "@java9args.txt",
                   "-jar", "lwjgl3ify-forgePatches.jar", "nogui"]
        receipt["java_version"] = subprocess.run([java, "-version"], capture_output=True, text=True).stderr
        process = subprocess.Popen(command, cwd=sandbox, stdin=subprocess.PIPE,
                                   stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        receipt.update(pid=process.pid, command=command)
        smoke.write_json(active, receipt)

        def drain():
            for line in iter(process.stdout.readline, b""):
                events.put(line)
            events.put(None)

        reader = threading.Thread(target=drain, daemon=True)
        reader.start()
        print(f"OWNED SERVER pid={process.pid} sha256={receipt['sha256']}", flush=True)
        current = None
        armed = False
        next_index = 0
        last_status = started
        stop_at = None
        with (result_dir / "console.log").open("wb") as log:
            while True:
                now = time.monotonic()
                try:
                    line = events.get(timeout=0.25)
                    if line is None:
                        break
                    log.write(line)
                    log.flush()
                    decoded = line.decode("utf-8", "replace")
                    if "GTSR-TESTLAB" in decoded or "[GTSR_TESTLAB]" in decoded:
                        print(decoded.rstrip(), flush=True)
                    if re.search(r"Done \([\d.]+s\)!", decoded):
                        receipt["vanilla_done_seen"] = True
                        print("VANILLA INITIALIZED; awaiting first real GTSR server tick", flush=True)
                    if re.search(r"GTSR-TESTLAB\] state=READY(?:\s|$)", decoded):
                        receipt["done_seen"] = True
                        receipt["native_ready_line"] = decoded.strip()
                        print("SERVER READY", flush=True)
                    if "GTSR-TESTLAB] state=UNAVAILABLE" in decoded:
                        receipt["startup_error"] = decoded.strip()
                        receipt["status"] = "FAIL"
                        stop_at = stop_at or now
                    if current is not None and receipt["steps"][-1]["marker"] in decoded:
                        armed = True
                    elif current is not None and armed and stop_at is None and re.search(current["expect"], decoded):
                        record = receipt["steps"][-1]
                        record.update(status="PASS", response=decoded.strip(), seconds=round(now - record["started"], 2))
                        print(f"STEP PASS {record['command']}", flush=True)
                        current = None
                    elif current is not None and armed and stop_at is None and (
                            re.search(r"GTSR-TESTLAB\].*(?:ERROR |state=ERROR|state=RESET_FAILED|报告写入失败|基础设施失败|最终报告写入失败)", decoded)
                            or (current.get("failOn") and re.search(current["failOn"], decoded))):
                        receipt["steps"][-1].update(status="FAIL", response=decoded.strip())
                        receipt["status"] = "FAIL"
                        print("COMMAND FAILED " + decoded.strip(), flush=True)
                        stop_at = now
                except queue.Empty:
                    pass
                if stop_at is None and receipt["done_seen"] and current is None:
                    if next_index < len(scenario):
                        current = scenario[next_index]
                        marker = f"GTSR_TESTLAB_STEP_{next_index}_{stamp}"
                        record = {"command": current["command"], "started": now, "status": "RUNNING", "marker": marker}
                        receipt["steps"].append(record)
                        next_index += 1
                        print(f"COMMAND {current['command']}", flush=True)
                        armed = False
                        process.stdin.write(("say " + marker + "\n" + current["command"] + "\n").encode("utf-8"))
                        process.stdin.flush()
                    else:
                        receipt["status"] = "PASS"
                        stop_at = now
                if current is not None and now - receipt["steps"][-1]["started"] > current.get("timeout", 120):
                    receipt["steps"][-1]["status"] = "TIMEOUT"
                    receipt["status"] = "FAIL"
                    stop_at = stop_at or now
                if now - started > args.deadline:
                    receipt["status"] = "FAIL"
                    stop_at = stop_at or now
                if now - last_status > 30:
                    print(f"STATUS elapsed={now-started:.0f}s ready={receipt['done_seen']} step={next_index}/{len(scenario)}", flush=True)
                    last_status = now
                if stop_at is not None and not receipt.get("stop_sent"):
                    process.stdin.write(b"stop\n")
                    process.stdin.flush()
                    receipt["stop_sent"] = True
                if stop_at is not None and now - stop_at > 60:
                    process.kill()
                    receipt["forced_stop"] = True
                    break
    finally:
        if process is not None and process.poll() is None:
            try:
                process.stdin.write(b"stop\n")
                process.stdin.flush()
                process.wait(timeout=45)
            except (OSError, subprocess.TimeoutExpired):
                # A crash can close stdin before the JVM has finished its normal shutdown.
                # Report a forced stop only if this runner actually has to terminate it.
                try:
                    process.wait(timeout=45)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=15)
                    receipt["forced_stop"] = True
        if reader is not None:
            reader.join(timeout=2)
            with (result_dir / "console.log").open("ab") as log:
                while not events.empty():
                    line = events.get_nowait()
                    if line is not None:
                        log.write(line)
        receipt.update(exit_code=process.returncode if process else None,
                       elapsed_seconds=round(time.monotonic() - started, 2))
        receipt["reports"] = []
        for report in sandbox.glob("*/gtsr-testlab/*.json"):
            target = result_dir / "reports" / report.name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(report, target)
            receipt["reports"].append({"file": str(target), "sha256": smoke.sha256(target)})
        if receipt["exit_code"] != 0 or receipt["forced_stop"] or receipt["status"] != "PASS":
            receipt["status"] = "FAIL"
        smoke.write_json(result_dir / "receipt.json", receipt)
        active.unlink(missing_ok=True)
    print(json.dumps({"status": receipt["status"], "receipt": str(result_dir / "receipt.json")}), flush=True)
    if receipt["status"] != "PASS":
        raise SystemExit(1)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["prepare", "run"])
    parser.add_argument("--sandbox", type=Path, default=ROOT / "build/server-testlab-v12134")
    parser.add_argument("--instance", type=Path)
    parser.add_argument("--jar", type=Path)
    parser.add_argument("--scenario", type=Path)
    parser.add_argument("--java", type=Path)
    parser.add_argument("--xmx-gib", type=int, choices=[3, 4, 6, 8], default=3)
    parser.add_argument("--deadline", type=int, default=1800)
    parser.add_argument("--wait-memory-seconds", type=int, default=600)
    args = parser.parse_args()
    if args.action == "prepare" and args.instance is None:
        parser.error("prepare requires --instance")
    if args.action == "run" and (args.jar is None or args.scenario is None):
        parser.error("run requires --jar and --scenario")
    prepare(args) if args.action == "prepare" else run(args)


if __name__ == "__main__":
    main()
