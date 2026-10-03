#!/usr/bin/env python3
"""Copy an authorized GTNH instance into build/ and run one isolated server.

Preparation never accepts the Minecraft EULA. Boot requires a copied, already
accepted eula.txt and an explicit fresh GTSR jar. No source-instance writes,
symlinks, Gradle calls, Git calls, or unrelated-process termination are used.
"""

from __future__ import annotations

import argparse
import ctypes
import datetime as dt
import gzip
import hashlib
import io
import json
import os
from pathlib import Path
import queue
import re
import shutil
import subprocess
import struct
import threading
import time
import zipfile


REPO = Path(__file__).resolve().parents[2]
SANDBOX = REPO / "build" / "server-smoke-v67"
BINARIES = (
    "forge-1.7.10-10.13.4.1614-1.7.10-universal.jar",
    "lwjgl3ify-forgePatches.jar",
    "minecraft_server.1.7.10.jar",
    "java9args.txt",
)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def write_json(path: Path, value: dict) -> None:
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def guard() -> None:
    expected = (REPO / "build" / "server-smoke-v67").absolute()
    if SANDBOX.resolve() != expected or SANDBOX.resolve().parent != (REPO / "build").resolve():
        raise RuntimeError("Sandbox or build path is redirected; refusing filesystem writes")
    for current in [SANDBOX, *SANDBOX.rglob("*")] if SANDBOX.exists() else []:
        if current.is_symlink() or current.resolve() != current.absolute():
            raise RuntimeError(f"Redirected sandbox entry: {current}")


def accepted(path: Path) -> bool:
    return bool(re.search(r"^\s*eula\s*=\s*true\s*$", path.read_text(encoding="utf-8-sig"), re.M | re.I))


def jar_mods(path: Path) -> list[dict]:
    with zipfile.ZipFile(path) as jar:
        try:
            info = json.loads(jar.read("mcmod.info").decode("utf-8-sig"))
            return info if isinstance(info, list) else info.get("modList", [])
        except (KeyError, ValueError):
            return []


def copy_file(source: Path, destination: Path) -> dict:
    if source.is_symlink() or source.resolve() != source.absolute():
        raise RuntimeError(f"Refusing redirected source entry: {source}")
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, destination)
    return {"source": str(source), "relative": str(destination.relative_to(SANDBOX)),
            "bytes": source.stat().st_size, "sha256": sha256(source)}


def prepare(args: argparse.Namespace) -> None:
    guard()
    source = args.instance.resolve(strict=True)
    if source == SANDBOX or source.is_relative_to(SANDBOX):
        raise RuntimeError("Source instance must be outside the sandbox")
    if SANDBOX.exists() and any(SANDBOX.iterdir()):
        raise RuntimeError("Sandbox already populated; use its preparation receipt, do not overwrite it")
    for name in [*BINARIES, "eula.txt", "config", "mods", "libraries"]:
        if not (source / name).exists():
            raise RuntimeError(f"Required instance input missing: {name}")
    SANDBOX.mkdir(parents=True, exist_ok=True)
    copied = [copy_file(source / name, SANDBOX / name) for name in [*BINARIES, "eula.txt"]]
    for name in ["config", "mods", "libraries"]:
        for item in sorted((source / name).rglob("*")):
            if item.is_file():
                copied.append(copy_file(item, SANDBOX / item.relative_to(source)))
    # A new world, no source properties, operators, whitelist, or player data.
    (SANDBOX / "server.properties").write_text(
        "server-ip=127.0.0.1\nserver-port=0\nlevel-name=Smoke_v67\n"
        "online-mode=true\nenable-query=false\nenable-rcon=false\n"
        "max-players=1\nview-distance=3\nspawn-protection=0\n"
        "allow-flight=true\nmax-tick-time=-1\n", encoding="ascii")
    gt5 = []
    old_gtsr = []
    for jar in sorted((SANDBOX / "mods").rglob("*.jar")):
        metadata = jar_mods(jar)
        if jar.name.startswith("gregtech-"):
            gt5.append({"file": jar.name, "sha256": sha256(jar), "mods": metadata})
        if any(mod.get("modid", "").lower() == "gtsr" for mod in metadata):
            relative = jar.relative_to(SANDBOX / "mods")
            target = SANDBOX / "quarantined-mods" / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            jar.rename(target)
            old_gtsr.append(str(relative))
    if len(gt5) != 1 or gt5[0]["file"] != "gregtech-5.09.54.183.jar":
        raise RuntimeError(f"GT5 baseline differs from required 5.09.54.183: {gt5}")
    receipt = {"phase": "prepared", "utc": dt.datetime.now(dt.timezone.utc).isoformat(),
               "source_instance": str(source), "sandbox": str(SANDBOX), "copy_mode": "independent copies",
               "eula_accepted": accepted(SANDBOX / "eula.txt"), "gt5": gt5,
               "quarantined_gtsr": old_gtsr, "source_files": copied,
               "server_world": "Smoke_v67", "bind_address": "127.0.0.1", "server_port": 0}
    write_json(SANDBOX / "preparation-receipt.json", receipt)
    print(json.dumps({"phase": "prepared", "sandbox": str(SANDBOX),
                      "eula_accepted": receipt["eula_accepted"], "copied_files": len(copied),
                      "gt5": gt5[0]["file"], "quarantined_gtsr": old_gtsr}, ensure_ascii=False), flush=True)


def free_memory_bytes() -> int:
    if os.name != "nt":
        raise RuntimeError("This isolated smoke runner currently targets Windows")
    class MemoryStatus(ctypes.Structure):
        _fields_ = [("length", ctypes.c_ulong), ("load", ctypes.c_ulong),
                    ("total_phys", ctypes.c_ulonglong), ("avail_phys", ctypes.c_ulonglong),
                    ("total_page", ctypes.c_ulonglong), ("avail_page", ctypes.c_ulonglong),
                    ("total_virtual", ctypes.c_ulonglong), ("avail_virtual", ctypes.c_ulonglong),
                    ("avail_extended", ctypes.c_ulonglong)]
    status = MemoryStatus()
    status.length = ctypes.sizeof(status)
    if not ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(status)):
        raise RuntimeError("Unable to read physical memory availability")
    return status.avail_phys


def level_data_path() -> Path:
    properties = (SANDBOX / "server.properties").read_text(encoding="utf-8-sig")
    match = re.search(r"^\s*level-name\s*=([^\r\n]*)$", properties, re.M)
    name = match.group(1).strip() if match else "world"
    world = (SANDBOX / name).resolve()
    if not name or world == SANDBOX.resolve() or not world.is_relative_to(SANDBOX.resolve()):
        raise RuntimeError("Configured world is outside the isolated sandbox")
    return world / "level.dat"


def spawn_coordinates() -> tuple[int, int]:
    """Read the new world's spawn so probes use chunks the server already loaded."""
    stream = io.BytesIO(gzip.decompress(level_data_path().read_bytes()))
    def number(fmt: str):
        return struct.unpack(">" + fmt, stream.read(struct.calcsize(fmt)))[0]
    def string() -> str:
        return stream.read(number("H")).decode("utf-8")
    def payload(kind: int, depth: int = 0):
        if depth > 64:
            raise RuntimeError("Invalid NBT nesting in isolated world's level.dat")
        if kind in [1, 2, 3, 4, 5, 6]:
            return number({1: "b", 2: "h", 3: "i", 4: "q", 5: "f", 6: "d"}[kind])
        if kind == 8:
            return string()
        if kind == 9:
            subtype, count = number("B"), number("i")
            return [payload(subtype, depth + 1) for _ in range(count)]
        if kind == 10:
            result = {}
            while (subtype := number("B")) != 0:
                key = string()
                result[key] = payload(subtype, depth + 1)
            return result
        if kind in [7, 11, 12]:
            count = number("i")
            stream.read(count * {7: 1, 11: 4, 12: 8}[kind])
            return None
        raise RuntimeError(f"Unsupported NBT tag: {kind}")
    if number("B") != 10:
        raise RuntimeError("Isolated level.dat root is not an NBT compound")
    string()
    data = payload(10)["Data"]
    return int(data["SpawnX"]), int(data["SpawnZ"])


def probe_succeeded(responses: list[str]) -> bool:
    # English vanilla command responses; require placement and a block identity check.
    return (any(re.search(r"\bBlock placed\b", line) for line in responses)
            and any(re.search(r"\bSuccessfully found the block at\b", line) for line in responses))


def run(args: argparse.Namespace) -> None:
    guard()
    configured_world = level_data_path().parent
    if not (SANDBOX / "preparation-receipt.json").is_file():
        raise RuntimeError("Run prepare first")
    if not accepted(SANDBOX / "eula.txt"):
        raise RuntimeError("Copied Minecraft EULA is not accepted; explicit acceptance is required before boot")
    jar = args.jar.resolve(strict=True)
    metadata = jar_mods(jar)
    if not any(mod.get("modid", "").lower() == "gtsr" for mod in metadata):
        raise RuntimeError("Explicit fresh jar lacks gtsr mcmod.info metadata")
    if any(SANDBOX.glob("active-run.json")):
        raise RuntimeError("An active-run receipt exists; verify that recorded owned process ended before retrying")
    available = free_memory_bytes()
    required = (args.xmx_gib + 0.5) * 1024**3
    if available < required:
        raise RuntimeError(f"Insufficient free memory: {available / 1024**3:.2f} GiB, need {required / 1024**3:.2f} GiB")
    stamp = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    results = SANDBOX / "receipts" / stamp
    results.mkdir(parents=True, exist_ok=False)
    quarantined = []
    for previous in (SANDBOX / "mods").rglob("*.jar"):
        if any(mod.get("modid", "").lower() == "gtsr" for mod in jar_mods(previous)):
            relative = previous.relative_to(SANDBOX / "mods")
            moved = SANDBOX / "quarantined-mods" / stamp / relative
            moved.parent.mkdir(parents=True, exist_ok=True)
            previous.rename(moved)
            quarantined.append(str(relative))
    target = SANDBOX / "mods" / jar.name
    if target.exists():
        raise RuntimeError(f"Fresh jar filename conflicts with another sandbox mod: {target}")
    shutil.copy2(jar, target)
    java = str(args.java.resolve(strict=True)) if args.java else shutil.which("java")
    version = subprocess.run([java, "-version"], capture_output=True, text=True, check=True)
    command = [java, "-Xms1G", f"-Xmx{args.xmx_gib}G", "-Djava.awt.headless=true",
               "-Dfml.readTimeout=180", "-Duser.language=en", "@java9args.txt",
               "-jar", "lwjgl3ify-forgePatches.jar", "nogui"]
    receipt = {"phase": "launching", "utc": stamp, "sandbox": str(SANDBOX), "world": str(configured_world),
               "jar": str(jar), "jar_sha256": sha256(jar), "mod_metadata": metadata,
               "java_version": version.stdout + version.stderr, "command": command,
               "free_memory_bytes": available, "deadline_seconds": args.deadline,
               "done_seen": False, "stop_sent": False, "force_stopped": False,
               "registry_probes": args.probe_block,
               "registry_probe_results": [{"block": block, "success": False, "responses": []}
                                          for block in args.probe_block], "pid": None}
    receipt["quarantined_previous_gtsr"] = quarantined
    active = SANDBOX / "active-run.json"
    write_json(active, receipt)
    process = None
    console = results / "console.log"
    events: queue.Queue[bytes | None] = queue.Queue()
    started = time.monotonic()
    stop_time = None
    last_status = started
    active_probe = None
    done_time = None
    probes_dispatched = False
    drain_thread = None
    def drain() -> None:
        assert process is not None and process.stdout is not None
        for line in iter(process.stdout.readline, b""):
            events.put(line)
        events.put(None)
    try:
        process = subprocess.Popen(command, cwd=SANDBOX, stdin=subprocess.PIPE,
                                   stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        receipt.update(phase="running", pid=process.pid)
        write_json(active, receipt)
        drain_thread = threading.Thread(target=drain, daemon=True)
        drain_thread.start()
        print(f"OWNED JAVA PID {process.pid}; cwd {SANDBOX}; jar sha256 {receipt['jar_sha256']}", flush=True)
        with console.open("wb") as stream:
            while True:
                try:
                    line = events.get(timeout=0.5)
                    if line is None:
                        break
                    stream.write(line)
                    stream.flush()
                    decoded = line.decode("utf-8", errors="replace")
                    begin = re.search(r"GTSR_SMOKE_PROBE_BEGIN_(\d+)", decoded)
                    end = re.search(r"GTSR_SMOKE_PROBE_END_(\d+)", decoded)
                    if begin:
                        active_probe = int(begin.group(1))
                    elif end:
                        index = int(end.group(1))
                        if active_probe == index and index < len(receipt["registry_probe_results"]):
                            result = receipt["registry_probe_results"][index]
                            result["success"] = probe_succeeded(result["responses"])
                            print(f"PROBE {result['block']} success={result['success']}", flush=True)
                            if index == len(args.probe_block) - 1:
                                stop_time = time.monotonic() + 1
                        active_probe = None
                    elif active_probe is not None and active_probe < len(receipt["registry_probe_results"]):
                        receipt["registry_probe_results"][active_probe]["responses"].append(decoded.strip())
                    if re.search(r"Done \([\d.]+s\)!", decoded) and not receipt["done_seen"]:
                        receipt["done_seen"] = True
                        done_time = time.monotonic()
                        print("SERVER DONE reached; save-all before reading actual spawn for probes", flush=True)
                        process.stdin.write(b"save-all\n")
                        process.stdin.flush()
                except queue.Empty:
                    pass
                now = time.monotonic()
                if receipt["done_seen"] and not probes_dispatched:
                    spawn = None
                    if not args.probe_block:
                        spawn = (0, 0)
                    elif level_data_path().is_file():
                        try:
                            spawn = spawn_coordinates()
                        except (OSError, EOFError, struct.error):
                            pass  # save-all can still be writing the fresh NBT file.
                    if spawn is not None:
                        spawn_x, spawn_z = spawn
                        probes_dispatched = True
                        for index, block in enumerate(args.probe_block):
                            position = f"{spawn_x + index} 200 {spawn_z}"
                            receipt["registry_probe_results"][index]["position"] = position
                            # Clear only the owned test position before its BEGIN marker, so repeated
                            # registry probes still prove a fresh successful placement of the target.
                            commands = (f"setblock {position} minecraft:air 0 replace\n"
                                        f"say GTSR_SMOKE_PROBE_BEGIN_{index}\n"
                                        f"setblock {position} {block} 0 replace\n"
                                        f"testforblock {position} {block} 0\n"
                                        f"say GTSR_SMOKE_PROBE_END_{index}\n")
                            process.stdin.write(commands.encode("ascii"))
                        process.stdin.flush()
                        stop_time = time.monotonic() + (15 if args.probe_block else 1)
                    elif now - done_time >= 45:
                        receipt["probe_initialization_error"] = "No readable level.dat after Done + save-all + 45 seconds"
                        probes_dispatched = True
                        stop_time = now
                if now - last_status >= 60:
                    print(f"STATUS elapsed={now-started:.0f}s pid={process.pid} alive={process.poll() is None} done={receipt['done_seen']}", flush=True)
                    last_status = now
                if not receipt["stop_sent"] and (now - started >= args.deadline or (stop_time is not None and now >= stop_time)):
                    if process.poll() is None:
                        process.stdin.write(b"stop\n")
                        process.stdin.flush()
                    receipt["stop_sent"] = True
                    stop_time = now
                if receipt["stop_sent"] and now - stop_time >= 45 and process.poll() is None:
                    receipt["force_stopped"] = True
                    process.terminate()  # Only the Popen handle owned by this run.
                if now - started >= args.deadline + 90:
                    if process.poll() is None:
                        receipt["force_stopped"] = True
                        process.kill()  # Never search for or kill other Java PIDs.
                    break
        receipt["exit_code"] = process.wait(timeout=15)
    finally:
        if process is not None and process.poll() is None:
            try:
                process.stdin.write(b"stop\n")
                process.stdin.flush()
                receipt["stop_sent"] = True
                process.wait(timeout=45)
            except (OSError, subprocess.TimeoutExpired):
                if process.poll() is None:
                    receipt["force_stopped"] = True
                    process.kill()
                    process.wait(timeout=15)
        if drain_thread is not None:
            drain_thread.join(timeout=2)
            with console.open("ab") as stream:
                while not events.empty():
                    line = events.get_nowait()
                    if line is not None:
                        stream.write(line)
        receipt.update(phase="finished", elapsed_seconds=round(time.monotonic() - started, 2))
        if process is not None:
            receipt["exit_code"] = process.returncode
        if console.exists():
            receipt["console_sha256"] = sha256(console)
        for path in (SANDBOX / "logs").glob("*"):
            if path.is_file():
                shutil.copy2(path, results / path.name)
        write_json(results / "run-receipt.json", receipt)
        active.unlink(missing_ok=True)
    print(json.dumps(receipt, ensure_ascii=False), flush=True)
    if (not receipt["done_seen"] or receipt.get("exit_code") != 0 or receipt["force_stopped"]
            or not all(result["success"] for result in receipt["registry_probe_results"])):
        raise SystemExit(1)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="action", required=True)
    prep = commands.add_parser("prepare")
    prep.add_argument("--instance", type=Path, required=True)
    boot = commands.add_parser("run")
    boot.add_argument("--jar", type=Path, required=True)
    boot.add_argument("--java", type=Path)
    boot.add_argument("--deadline", type=int, default=600, choices=range(60, 601))
    boot.add_argument("--xmx-gib", type=int, default=3, choices=[3, 4, 6])
    boot.add_argument("--probe-block", action="append", default=[])
    args = parser.parse_args()
    for block in getattr(args, "probe_block", []):
        if not re.fullmatch(r"gtsr:[A-Za-z0-9_]+", block):
            parser.error("Block probes must be literal gtsr:<registry_name> identifiers")
    prepare(args) if args.action == "prepare" else run(args)


if __name__ == "__main__":
    main()
