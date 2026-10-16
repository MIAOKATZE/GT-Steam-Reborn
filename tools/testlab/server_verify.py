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


def lab_dimension_id(config: Path) -> int:
    text = config.read_text(encoding="utf-8-sig") if config.exists() else ""
    category = re.search(r'^\s*"?testlab"?\s*\{(.*?)^\s*\}', text, re.M | re.S)
    value = re.search(r'^\s*I:"?dimensionId"?\s*=\s*(-?\d+)\s*$', category[1], re.M) if category else None
    return int(value[1]) if value else 1048577


def lab_enabled(config: Path) -> bool:
    text = config.read_text(encoding="utf-8-sig") if config.exists() else ""
    category = re.search(r'^\s*"?testlab"?\s*\{(.*?)^\s*\}', text, re.M | re.S)
    value = re.search(r'^\s*B:"?enabled"?\s*=\s*(true|false)\s*$', category[1], re.M | re.I) if category else None
    return bool(value and value[1].lower() == "true")


def disabled_fingerprint(world: Path, dimension_id: int) -> dict:
    """Inspect only this sandbox world's test reports and lab dimension, without writing."""
    result = {}
    for root in (world / "gtsr-testlab", world / f"DIM{dimension_id}"):
        entries = [root, *sorted(root.rglob("*"))] if root.exists() else []
        result[root.name] = {"exists": root.exists(), "entries": {}}
        for path in entries:
            stat = path.stat()
            result[root.name]["entries"][str(path.relative_to(world))] = {
                "kind": "directory" if path.is_dir() else "file",
                "mtime_ns": stat.st_mtime_ns,
                "sha256": smoke.sha256(path) if path.is_file() else None,
            }
    return result


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
    dimension_id = lab_dimension_id(lab_config)
    receipt["expect_disabled"] = args.expect_disabled
    receipt["disabled_mode_violations"] = []
    receipt["class_loading"] = {"enabled": args.trace_class_loading}
    world = None
    if args.expect_disabled:
        level_names = re.findall(r"^level-name=(.*)$", properties, re.M)
        world = (sandbox / (level_names[-1].strip() if level_names else "world")).resolve()
        if world == sandbox or not world.is_relative_to(sandbox):
            raise ValueError("Disabled-mode world must be inside this sandbox")
        receipt["disabled_fingerprint"] = {
            "world": str(world), "dimension_id": dimension_id,
            "before": disabled_fingerprint(world, dimension_id),
        }
    receipt["runtime_configuration"] = {
        "server_properties_sha256": smoke.sha256(sandbox / "server.properties"),
        "level_name": re.findall(r"^level-name=(.*)$", properties, re.M),
        "level_type": re.findall(r"^level-type=(.*)$", properties, re.M),
        "gtsr_config_sha256": smoke.sha256(lab_config) if lab_config.exists() else None,
        "testlab_enabled": lab_enabled(lab_config),
    }
    process = None
    reader = None
    events = queue.Queue()
    started = time.monotonic()

    def observe_disabled(decoded):
        if args.expect_disabled and (
                "[GTSR-TESTLAB]" in decoded
                or re.search(r"\bdimension\s+testlab\b", decoded, re.I)
                or re.search(rf"\bLoading\s+dimension\s*{dimension_id}(?:\s|$)", decoded, re.I)):
            receipt["disabled_mode_violations"].append(decoded.strip())
            receipt["status"] = "FAIL"
            print("DISABLED MODE FAILED " + decoded.strip(), flush=True)
            return True
        return False

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
        receipt["java_version"] = subprocess.run(
            [java, "-version"], capture_output=True, text=True, timeout=30).stderr
        if args.trace_class_loading:
            version = re.search(r'version "(?:1\.)?(\d+)', receipt["java_version"])
            if not version or int(version[1]) < 9:
                raise ValueError("--trace-class-loading requires Java 9+ unified JVM logging")
            trace_relative = (result_dir / "class-loading.log").relative_to(sandbox).as_posix()
            command.insert(1, f"-Xlog:class+load=info:file={trace_relative}")
            receipt["class_loading"]["file"] = str(result_dir / "class-loading.log")
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
                    if observe_disabled(decoded):
                        stop_at = stop_at or now
                    if "GTSR-TESTLAB" in decoded or "[GTSR_TESTLAB]" in decoded:
                        print(decoded.rstrip(), flush=True)
                    if re.search(r"Done \([\d.]+s\)!", decoded):
                        receipt["vanilla_done_seen"] = True
                        if args.expect_disabled:
                            receipt["done_seen"] = True
                            receipt["native_ready_line"] = decoded.strip()
                            print("VANILLA READY; checking disabled testlab", flush=True)
                        else:
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
                        observe_disabled(line.decode("utf-8", "replace"))
        receipt.update(exit_code=process.returncode if process else None,
                       elapsed_seconds=round(time.monotonic() - started, 2))
        receipt["runtime_configuration"].update(
            gtsr_config_sha256_after=smoke.sha256(lab_config) if lab_config.exists() else None,
            testlab_enabled_after=lab_enabled(lab_config))
        receipt["reports"] = []
        for report in ([] if args.expect_disabled else sandbox.glob("*/gtsr-testlab/*.json")):
            target = result_dir / "reports" / report.name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(report, target)
            receipt["reports"].append({"file": str(target), "sha256": smoke.sha256(target)})
        if args.expect_disabled:
            fingerprint = receipt["disabled_fingerprint"]
            fingerprint["after"] = disabled_fingerprint(world, dimension_id)
            fingerprint["unchanged"] = fingerprint["before"] == fingerprint["after"]
            if not fingerprint["unchanged"] or receipt["disabled_mode_violations"]:
                receipt["status"] = "FAIL"
        if args.trace_class_loading:
            trace = result_dir / "class-loading.log"
            loaded = set()
            total_loaded = 0
            if trace.is_file():
                with trace.open(encoding="utf-8", errors="replace") as lines:
                    for line in lines:
                        if re.search(r"\[class,load\s*\]\s+", line):
                            total_loaded += 1
                        match = re.search(
                            r"\[class,load\s*\]\s+(com\.miaokatze\.gtsr\.common\.dimension\.testlab\.\S+)", line)
                        if match:
                            loaded.add(match[1])
                receipt["class_loading"].update(log_sha256=smoke.sha256(trace), log_bytes=trace.stat().st_size)
                if not total_loaded:
                    receipt["class_loading"]["error"] = "JVM trace contained no class-loading records"
                    receipt["status"] = "FAIL"
            else:
                receipt["class_loading"]["error"] = "JVM class-loading log was not produced"
                receipt["status"] = "FAIL"
            receipt["class_loading"].update(loaded_testlab_classes=sorted(loaded), loaded_testlab_count=len(loaded),
                                            total_loaded_class_lines=total_loaded)
            if args.expect_disabled and loaded:
                receipt["status"] = "FAIL"
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
    parser.add_argument("--expect-disabled", action="store_true",
                        help="Start scenarios at vanilla Done; require silent, inactive testlab and unchanged lab data")
    parser.add_argument("--trace-class-loading", action="store_true",
                        help="Java 9+ only: trace class loading in the owned JVM; disabled mode requires zero testlab classes")
    args = parser.parse_args()
    if args.action == "prepare" and args.instance is None:
        parser.error("prepare requires --instance")
    if args.action == "run" and (args.jar is None or args.scenario is None):
        parser.error("run requires --jar and --scenario")
    prepare(args) if args.action == "prepare" else run(args)


if __name__ == "__main__":
    main()
