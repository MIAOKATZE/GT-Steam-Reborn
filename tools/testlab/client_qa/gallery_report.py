"""Build a self-contained, offline HTML gallery from owned client QA evidence.

Only gallery-inventory.json and client-runs evidence below --workspace are read.
No client, launcher, account, network, or third-party Python package is required.
"""
from __future__ import annotations

import argparse
from collections import Counter, defaultdict
from datetime import datetime, timezone
import hashlib
import html
import json
from pathlib import Path
import re
import shutil
import struct
from urllib.parse import quote


STATUSES = ("CAPTURED", "FAIL", "NOT_RUN", "UNREACHABLE")
STATUS_TEXT = {"CAPTURED": "已截图", "FAIL": "截图不完整 / 失败", "NOT_RUN": "未运行", "UNREACHABLE": "不可达"}


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def localized_titles(inventory):
    """Derive display labels from the current production registration and Chinese resource."""
    root = Path(__file__).resolve().parents[3]
    language = root / 'src/main/resources/assets/gtsr/lang/zh_CN.lang'
    registration = root / 'src/main/java/com/miaokatze/gtsr/loader/MachineLoader.java'
    values = dict(line.split('=', 1) for line in language.read_text(encoding='utf-8').splitlines()
                  if '=' in line and not line.startswith('#'))
    names = dict(re.findall(r'MetaTileEntityID\.([A-Z_]+)\.ID,\s*"([^"]+)"', registration.read_text(encoding='utf-8')))
    mappings = []
    for entry in inventory:
        kind = entry.get('kind', '')
        internal = names.get(kind, 'gtsr.' + kind.lower().replace('_', '.'))
        key = 'gt.blockmachines.' + internal + '.name'
        if key in values:
            entry['title'] = values[key]
            entry['titleResourceKey'] = key
            mappings.append({'id': entry['id'], 'key': key, 'title': values[key]})
    return {'sources': [{'path': str(p), 'sha256': sha256(p)} for p in (language, registration)], 'mappings': mappings}


def read_json(path, issues, fallback=None):
    if not path.is_file():
        issues.append(f"缺少证据：{path.name}")
        return fallback
    try:
        return json.loads(path.read_text(encoding="utf-8-sig"))
    except (OSError, ValueError) as error:
        issues.append(f"无法解析 {path.name}：{error}")
        return fallback


def dictionaries(value):
    return [item for item in value if isinstance(item, dict)] if isinstance(value, list) else []


def failed(item):
    return item.get("pass") is False or str(item.get("status", "")).upper() in {"FAIL", "FAILED", "ERROR"}


def png_dimensions(path):
    with path.open("rb") as stream:
        header = stream.read(24)
    if len(header) != 24 or header[:8] != b"\x89PNG\r\n\x1a\n" or header[12:16] != b"IHDR":
        raise ValueError("文件不是具有 IHDR 的 PNG")
    return list(struct.unpack(">II", header[16:24]))


def relative_url(path, output):
    return quote(path.relative_to(output).as_posix(), safe="/")


def plan_context(plan, checks):
    """Map both galleryCapture and historical screenshot steps to their group/page."""
    by_name, by_index, groups = {}, {}, defaultdict(list)
    current_group, expected = "pilot", {}
    actual_by_name = {}
    for check in dictionaries(checks.get("checks")):
        match = re.search(r"Actual (?:GuiScreen class|Minecraft currentScreen)=(\S+)", str(check.get("detail", "")))
        if match and check.get("pass") is True:
            actual_by_name[str(check.get("name", ""))] = match.group(1)
    for index, step in enumerate(dictionaries(plan)):
        op = step.get("op")
        flat_capture = not op and isinstance(step.get("name"), str) and isinstance(step.get("id"), str)
        if flat_capture:
            current_group, expected = step["id"], {}
        if op == "group":
            current_group, expected = str(step.get("name", "未命名组")), {}
        by_index[index] = {"group": current_group, "step": step}
        groups[current_group].append(index)
        if op in {"expectScreen", "expectGuiClass", "galleryExpect"}:
            expected = {
                "expectedGuiClass": step.get("class", step.get("contains")),
                "panel": step.get("panel"), "expectation": op,
                "guiClass": actual_by_name.get(str(step.get("name", "")))
                or actual_by_name.get("gallery.open." + str(step.get("id", ""))),
            }
        if op in {"screenshot", "galleryCapture"} or flat_capture:
            name = str(step.get("name", ""))
            by_name[name] = dict(expected, group=current_group, planIndex=index,
                                 page=step.get("page", name))
    return by_name, by_index, groups


def copy_evidence(run_dir, output, receipt, issues):
    raw = output / "raw-evidence" / run_dir.name
    raw.mkdir(parents=True, exist_ok=True)
    records = []
    # Explicit allowlist avoids importing unrelated launcher or account material.
    names = {"receipt.json", "launch.json", "checks.json", "checks-live.json", "plan.json", "plan-manifest.json",
             "helper-compile-receipt.json", "preflight.json"}
    names.update(path.name for path in run_dir.glob("*receipt*.json") if path.is_file())
    for name in sorted(names):
        source = run_dir / name
        if source.is_file():
            target = raw / name
            shutil.copy2(source, target)
            records.append({"file": relative_url(target, output), "sha256": sha256(source)})
    for source in sorted((run_dir / "batch-plans").rglob("*.json")):
        target = raw / "batch-plans" / source.relative_to(run_dir / "batch-plans")
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)
        records.append({"file": relative_url(target, output), "sha256": sha256(source)})
    for source in sorted((run_dir / "hotpatches").rglob("*")):
        if not source.is_file():
            continue
        target = raw / "hotpatches" / source.relative_to(run_dir / "hotpatches")
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)
        records.append({"file": relative_url(target, output), "sha256": sha256(source)})
    command = receipt.get("command")
    replay = {"command": command, "workingDirectory": receipt.get("workingDirectory", receipt.get("cwd")),
              "note": "原始进程命令；仅作为复现实行证据，图库生成器不执行命令。"}
    (raw / "replay-command.json").write_text(json.dumps(replay, ensure_ascii=False, indent=2), encoding="utf-8")
    if isinstance(command, list) and command and all(isinstance(arg, str) for arg in command):
        ps_quote = lambda arg: "'" + arg.replace("'", "''") + "'"
        lines = ["# 原始运行命令。该文件不会被图库自动执行。"]
        if replay["workingDirectory"]:
            lines.append("Set-Location -LiteralPath " + ps_quote(str(replay["workingDirectory"])))
        else:
            lines.append("# 收据未记录工作目录；重放前须切换到原 owned client sandbox，不能据此宣称已完整复现。")
            issues.append("原始 command 已保留；收据未记录工作目录")
        lines.append("& " + " ".join(ps_quote(arg) for arg in command))
        (raw / "replay-command.ps1").write_text("\n".join(lines) + "\n", encoding="utf-8-sig")
    elif isinstance(command, str) and command:
        (raw / "replay-command.txt").write_text(command, encoding="utf-8")
    else:
        issues.append("收据未记录 command，无法提供原始重放命令")
    return records


def run_metadata(run_dir, issues):
    """launch.json identifies an active JVM before the final runner receipt exists."""
    for name in ("receipt.json", "launch.json"):
        if not (run_dir / name).is_file():
            continue
        value = read_json(run_dir / name, issues, {})
        if isinstance(value, dict) and value.get("candidateSHA256"):
            return value, name
        issues.append(f"{name} 未提供有效候选 SHA")
    return {}, None


def validate_inheritance(path, primary_sha, inherited_sha):
    payload = path.read_bytes()
    proof = json.loads(payload.decode("utf-8-sig"))
    changed = {
        "com/miaokatze/gtsr/common/gui/MTECriticalMachineTerminalGui.class",
        "com/miaokatze/gtsr/common/gui/MTECriticalMachineTerminalGui$IndustrialModeDropdown.class",
    }
    scope = "Non-critical-terminal UI classes and all resources are byte-identical; critical terminal must be recaptured after content origin fix."
    if (not isinstance(proof, dict) or proof.get("status") != "PASS"
            or proof.get("otherEntriesByteIdentical") is not True
            or str(proof.get("r3SHA256", "")).lower() != inherited_sha
            or str(proof.get("r4SHA256", "")).lower() != primary_sha
            or proof.get("inheritanceScope") != scope
            or not isinstance(proof.get("entries"), int) or proof["entries"] <= len(changed)
            or not isinstance(proof.get("changedEntries"), list)
            or len(proof["changedEntries"]) != len(changed)
            or not all(isinstance(entry, str) for entry in proof["changedEntries"])
            or set(proof["changedEntries"]) != changed):
        raise ValueError("继承凭据 SHA、PASS、逐项字节一致声明或限定的 Critical terminal 两类变更范围不匹配")
    return proof, hashlib.sha256(payload).hexdigest(), payload


def select_inherited_captures(selected_runs, inventory, primary_sha, inherited_sha, normal_only):
    """Resolve names before copying: primary wins, then latest sorted run per SHA."""
    critical_names = {name for entry in inventory
                      if re.fullmatch(r"ui\.critical_[^.]+\.terminal", entry["id"])
                      for name in entry.get("shots", [])}
    winners = {}
    for target_sha in (inherited_sha, primary_sha):
        for run_dir, metadata, _ in selected_runs:
            if str(metadata[0].get("candidateSHA256", "")).lower() != target_sha:
                continue
            for path in sorted((run_dir / "screenshots").rglob("*.png")):
                name = path.stem
                if normal_only and ".normal." not in name:
                    continue
                if target_sha == inherited_sha:
                    if ".normal." not in name or name in critical_names or re.search(r"(?:^|\.)ui\.critical_[^.]+\.terminal\.", name):
                        continue
                winners[name] = (run_dir.name, path)
    allowed = defaultdict(set)
    for run_id, path in winners.values():
        allowed[run_id].add(path)
    return allowed


def collect_run(run_dir, output, assets, metadata=None, normal_only=False, allowed_paths=None,
                effective_sha=None, inherited=False, inheritance_proof=None):
    issues = []
    receipt, metadata_source = metadata if metadata is not None else run_metadata(run_dir, issues)
    checks_source = "checks.json" if (run_dir / "checks.json").is_file() else "checks-live.json"
    checks = read_json(run_dir / checks_source, issues, {})
    plan = read_json(run_dir / "plan.json", issues, []) if (run_dir / "plan.json").is_file() else []
    if not isinstance(checks, dict):
        issues.append("checks.json 顶层必须是对象")
        checks = {}
    if not isinstance(plan, list):
        issues.append("plan.json 顶层必须是数组")
        plan = []
    for path in sorted((run_dir / "batch-plans").rglob("*.json")):
        batch_plan = read_json(path, issues, [])
        if not isinstance(batch_plan, list):
            issues.append(f"batch-plans/{path.name} 顶层必须是数组")
            continue
        plan.extend(dictionaries(batch_plan))
    complete = (metadata_source == "receipt.json" and checks_source == "checks.json"
                and receipt.get("status") in {"PASS", "FAIL"} and checks.get("status") in {"PASS", "FAIL"})
    if not complete:
        issues.append("运行尚无完整最终收据；实时检查仅作进度证据，不视为 PASS")
    planned, indexed, groups = plan_context(plan, checks)
    events = dictionaries(checks.get("scriptEvents"))
    check_rows = dictionaries(checks.get("checks"))
    executed_groups, failed_groups, captures = set(), set(), {}
    event_rows = []
    for original in events:
        event = dict(original)
        index = event.get("index", event.get("step"))
        context = indexed.get(index, {}) if isinstance(index, int) else {}
        group = event.get("group", context.get("group"))
        if group:
            event["group"] = group
            executed_groups.add(group)
        if failed(event) and group:
            failed_groups.add(group)
        if event.get("galleryCapture"):
            captures[str(event["galleryCapture"])] = event
        event_rows.append(event)
    for check in check_rows:
        if failed(check) and str(check.get("name", "")).startswith("script.group."):
            failed_groups.add(str(check["name"])[len("script.group."):])
    executed_groups.update(failed_groups)
    raw_files = copy_evidence(run_dir, output, receipt, issues)
    run = {"id": run_dir.name, "status": receipt.get("status") if complete or
           (metadata_source == "receipt.json" and receipt.get("status") == "FAIL") else "IN_PROGRESS",
           "checksStatus": checks.get("status") if complete else "IN_PROGRESS",
           "reportedChecksStatus": checks.get("status"), "finalEvidence": complete,
           "metadataSource": metadata_source, "checksSource": checks_source,
           "candidateSHA256": receipt.get("candidateSHA256"), "resolution": receipt.get("resolution"),
           "guiScale": receipt.get("guiScale"), "language": receipt.get("language"), "heapGiB": receipt.get("heapGiB"), "reserveGiB": receipt.get("reserveGiB"),
           "failedChecks": [row for row in check_rows if failed(row)],
           "failedEvents": [row for row in event_rows if failed(row)],
           "failedGroups": sorted(failed_groups), "executedGroups": sorted(executed_groups),
           "plannedGroups": list(groups), "issues": issues, "rawEvidence": raw_files,
           "rawDirectory": relative_url(output / "raw-evidence" / run_dir.name, output),
           "plannedCaptures": [], "screenshots": [],
           "failure": checks.get("failure", receipt.get("failure")),
           "inputType": receipt.get("inputType", checks.get("physicalInputAndIME")),
           "batchJobs": checks.get("batchJobs", []),
           "startupToTestLabReadyMillis": checks.get("startupToTestLabReadyMillis"),
           "commandEvidence": relative_url(output / "raw-evidence" / run_dir.name / "replay-command.json", output)}
    image_dir = run_dir / "screenshots"
    images = sorted(image_dir.rglob("*.png")) if image_dir.is_dir() else []
    for source in images:
        name = source.stem
        if normal_only and ".normal." not in name:
            continue
        if allowed_paths is not None and source not in allowed_paths:
            continue
        context = dict(planned.get(name, {}))
        capture = captures.get(name, {})
        context.update({key: value for key, value in capture.items() if key not in {"galleryCapture"}})
        exceptions = []
        try:
            dimensions = png_dimensions(source)
        except (OSError, ValueError) as error:
            dimensions = None
            exceptions.append(str(error))
        digest = sha256(source)
        if capture.get("sha256") and str(capture["sha256"]).lower() != digest:
            exceptions.append("截图事件 SHA256 与实际 PNG 不一致")
        target = output / "assets" / run_dir.name / source.relative_to(image_dir)
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)
        if context.get("panelWithinCanvas") is False:
            exceptions.append("panelWithinCanvas=false：面板超出真实缩放画布")
        if context.get("group") in failed_groups:
            exceptions.append("所属脚本组存在失败；不影响已生成 PNG 的证据保留")
        image = {"id": str(len(assets)), "name": name, "asset": relative_url(target, output),
                 "source": str(source.resolve()), "sourceSHA256": digest, "copiedSHA256": sha256(target),
                 "bytes": source.stat().st_size, "run": run_dir.name,
                 "candidateSHA256": receipt.get("candidateSHA256"),
                 "effectiveCandidateSHA256": effective_sha or receipt.get("candidateSHA256"),
                 "inherited": inherited, "inheritanceProof": inheritance_proof if inherited else None,
                 "inheritanceProofPath": inheritance_proof["file"] if inherited else None,
                 "inheritanceProofSHA256": inheritance_proof["sha256"] if inherited else None,
                 "renderSourceLabel": "R3实拍，字节一致继承" if inherited else "R4实拍" if inheritance_proof else "本候选实拍",
                 "guiClass": context.get("guiClass"), "expectedGuiClass": context.get("expectedGuiClass"),
                 "guiClassSource": "galleryCapture" if capture.get("guiClass") else "check / plan inference",
                 "page": name, "pageIndex": context.get("page"),
                 "panel": context.get("panel"), "group": context.get("group"),
                 "resolution": dimensions, "launchResolution": receipt.get("resolution"),
                 "pngResolution": dimensions, "displayWidth": capture.get("displayWidth"),
                 "displayHeight": capture.get("displayHeight"), "guiScale": receipt.get("guiScale"),
                 "language": receipt.get("language"), "heapGiB": receipt.get("heapGiB"), "geometry": context.get("geometry"),
                 "panelWithinCanvas": context.get("panelWithinCanvas"), "captureEvent": bool(capture),
                 "captureStatus": "CAPTURED" if dimensions else "FAIL", "runStatus": run["status"],
                 "routes": [], "entryIds": [], "exceptions": exceptions}
        for key in ("job", "screenClass", "windowId", "syncStatus", "stableRenderFrames", "fixtureNote",
                    "caseWallMillis", "fixtureWaitMillis", "syncWaitMillis", "naturalRenderWaitMillis", "pngCaptureMillis"):
            if key in capture:
                image[key] = capture[key]
        image["eventSHA256"] = capture.get("sha256")
        assets.append(image)
        run["screenshots"].append(image["id"])
    captured_names = {asset["name"] for asset in assets if asset["run"] == run_dir.name}
    for name, context in planned.items():
        if normal_only and ".normal." not in name:
            continue
        if allowed_paths is not None and name not in {path.stem for path in allowed_paths}:
            continue
        run["plannedCaptures"].append({"name": name, "group": context.get("group"),
                                       "status": "CAPTURED" if name in captured_names else
                                       "FAIL" if name in captures or context.get("group") in executed_groups else "NOT_RUN"})
    return run, planned, indexed


def assemble_entries(inventory, runs, assets, contexts):
    by_name = defaultdict(list)
    for asset in assets:
        by_name[asset["name"]].append(asset)
    results = []
    for original in inventory:
        entry = dict(original)
        shot_names = entry.get("shots", [])
        related, matched = [], []
        inferred_groups = set(str(group) for group in entry.get("groups", []))
        for name in shot_names:
            matched.extend(by_name.get(name, []))
            for planned, _ in contexts.values():
                if name in planned and planned[name].get("group"):
                    inferred_groups.add(planned[name]["group"])
        # One PNG may intentionally belong to several inventory entries.
        for asset in matched:
            if entry["id"] not in asset["entryIds"]:
                asset["entryIds"].append(entry["id"])
            route = entry.get("route")
            if route is not None and route not in asset["routes"]:
                asset["routes"].append(route)
        entry["imageIds"] = list(dict.fromkeys(asset["id"] for asset in matched))
        entry["missingShots"] = [name for name in shot_names if not any(a["captureStatus"] == "CAPTURED" for a in by_name.get(name, []))]
        entry["groups"] = sorted(inferred_groups)
        for run in runs:
            shot_run = any(asset["run"] == run["id"] for asset in matched)
            relevant_groups = inferred_groups.intersection(run["plannedGroups"] + run["executedGroups"])
            if not shot_run and not relevant_groups:
                continue
            group_failures = [event for event in run["failedEvents"] if event.get("group") in inferred_groups]
            step_names = set()
            # Associate checks by their exact plan name/group; no fuzzy substring match.
            for info in contexts[run["id"]][1].values():
                if info["group"] in inferred_groups:
                    step = info["step"]
                    if step.get("name"):
                        step_names.add(step["name"])
                    if step.get("op") == "galleryExpect":
                        step_names.add("gallery.open." + str(step.get("id", "")))
            names = step_names | {"script.group." + group for group in inferred_groups}
            names.update("gallery.geometry." + name for name in shot_names)
            checks = [check for check in run["failedChecks"] if check.get("name") in names]
            related.append({"run": run["id"], "runStatus": run["status"],
                            "groups": sorted(relevant_groups), "failedGroups": sorted(inferred_groups.intersection(run["failedGroups"])),
                            "failedChecks": checks, "failedEvents": group_failures,
                            "attempted": bool(relevant_groups.intersection(run["executedGroups"]))})
        attempted = bool(matched) or any(row["attempted"] for row in related)
        if entry.get("reason") and not shot_names:
            status = "UNREACHABLE"
        elif shot_names and not entry["missingShots"]:
            status = "CAPTURED"
        elif attempted:
            status = "FAIL"
        else:
            status = "NOT_RUN"
        entry["status"] = status
        entry["relatedRuns"] = related
        entry["functionalStatus"] = "FAIL" if any(row["failedGroups"] or row["failedChecks"] or row["failedEvents"] for row in related) else "NOT_ASSESSED"
        entry["functionalNote"] = "存在相关失败，请展开诊断" if entry["functionalStatus"] == "FAIL" else "截图报告不作完整功能验收结论"
        results.append(entry)
    return results


def attach_observations(workspace, output, assets, issues, allowed_runs=None):
    """Keep manual visual findings independent from automated canvas diagnostics."""
    source = workspace / "gallery-observations.json"
    if not source.is_file():
        return None
    observations = read_json(source, issues, [])
    if not isinstance(observations, list):
        issues.append("gallery-observations.json 顶层必须是数组；原始文件已保留")
        observations = []
    if allowed_runs is None:
        shutil.copy2(source, output / "raw-evidence" / source.name)
    else:
        observations = [row for row in observations if isinstance(row, dict) and row.get("run") in allowed_runs]
        (output / "raw-evidence" / "gallery-observations-filtered.json").write_text(
            json.dumps(observations, ensure_ascii=False, indent=2), encoding="utf-8")
    records = []
    for index, observation in enumerate(observations):
        if not isinstance(observation, dict) or not all(isinstance(observation.get(key), str) for key in ("run", "name", "note")):
            issues.append(f"人工记录第 {index + 1} 项缺少字符串 run/name/note；原始文件已保留")
            continue
        record = dict(observation)
        matches = [asset for asset in assets if asset["run"] == record["run"] and asset["name"] == record["name"]]
        for asset in matches:
            asset.setdefault("observations", []).append(dict(record))
            asset["exceptions"].append("人工记录（" + str(record.get("severity", "未分级")) + "）：" + record["note"])
        record["matchedImages"] = [asset["id"] for asset in matches]
        records.append(record)
    return {"source": str(source), "sha256": sha256(source), "records": records,
            "note": "仅列出明确提供的人工记录；未列出的截图不表示已人工复核。几何诊断与人工视觉问题分别保留。"}


def esc(value):
    return html.escape(str(value if value is not None else "未记录"), quote=True)


def diagnostic(value):
    return "<pre>" + esc(json.dumps(value, ensure_ascii=False, indent=2)) + "</pre>"


def review_fields(kind, target=""):
    attributes = f'data-review-kind="{esc(kind)}" data-review-target="{esc(target)}"'
    return (f'<div class="review-fields"><label>人工审阅 <select {attributes} data-review-field="status" aria-label="人工审阅状态">'
            '<option value="PENDING">待审</option><option value="APPROVED">通过</option><option value="NEEDS_CHANGE">需调整</option>'
            f'</select></label><label class="review-note">备注 <textarea {attributes} data-review-field="note" rows="2" '
            'aria-label="人工审阅备注" placeholder="记录需调整的位置或审阅意见"></textarea></label></div>')


def image_card(asset, entry=None):
    route = entry.get("route", "未记录") if entry else " / ".join(map(str, asset["routes"])) or "未映射"
    route_text = str(route)
    terminal = "terminal" in route_text.lower() or "终端" in route_text or (entry and "terminal" in str(entry.get("variant", "")).lower())
    visual = (entry and entry.get("variant") == "visual") or "LOCAL_PRODUCTION_BUILDER_VISUAL_FIXTURE" in str(asset.get("syncStatus", ""))
    label = "视觉fixture / 生产builder" if visual else "terminal / 终端入口" if terminal else "native / 原生入口" if route != "未记录" and route != "未映射" else "入口未记录"
    gui = asset["guiClass"] or ("期望：" + str(asset["expectedGuiClass"]) if asset["expectedGuiClass"] else "GUI 类未记录")
    return (f'<figure><button class="zoom" data-image="{esc(asset["id"])}" aria-label="放大 {esc(asset["name"])}">'
            f'<img loading="lazy" src="{esc(asset["asset"])}" alt="{esc(asset["name"])}"></button>'
            f'<figcaption><strong>{esc(asset["page"])}</strong><span>{esc(label)}</span><code>{esc(gui)}</code>'
            f'<small>{esc(asset["run"])} · {esc(asset["pngResolution"])} · scale {esc(asset["guiScale"])}</small>'
            f'<small>{esc(asset.get("renderSourceLabel", "本候选实拍"))} · 源 SHA {esc(asset["candidateSHA256"])}</small>'
            f'<span class="image-review" data-image-review="{esc(asset["id"])}">人工审阅：待审</span>'
            f'<a href="{esc(asset["asset"])}" target="_blank" rel="noopener">打开原图 ↗</a>'
            + ''.join(f'<span class="bad">人工记录：{esc(row["note"])}</span>' for row in asset.get("observations", []))
            + ('<span class="bad">有异常：展开条目诊断</span>' if asset["exceptions"] else '') + '</figcaption></figure>')


STYLE = r"""
:root{color-scheme:light;--ink:#1d2937;--muted:#526174;--line:#dbe2e9;--accent:#125b87}*{box-sizing:border-box}body{margin:0;background:#f4f6f8;color:var(--ink);font:15px/1.6 system-ui,'Microsoft YaHei',sans-serif}a{color:var(--accent)}header,main{max-width:1480px;margin:auto;padding:24px}h1{margin:0;font-size:29px}h2{font-size:21px;margin:24px 0 12px}p{margin:8px 0}.notice{border-left:5px solid #dc9c26;background:#fff5dc;padding:12px 16px;margin:16px 0}.muted,small{color:var(--muted)}.summary{display:flex;gap:12px;flex-wrap:wrap;margin:18px 0}.summary span{background:white;border:1px solid var(--line);border-radius:7px;padding:8px 14px}.toolbar{position:sticky;top:0;z-index:5;background:#f4f6f8f5;border-block:1px solid var(--line);padding:12px 0;display:flex;gap:12px;flex-wrap:wrap;align-items:center}input,select,button{font:inherit}input,select{padding:9px;border:1px solid #b7c4cf;border-radius:5px;background:#fff}input{flex:1;min-width:240px}.entry{background:white;border:1px solid var(--line);border-radius:8px;padding:18px;margin:15px 0;scroll-margin-top:100px}.entry h3{margin:0 0 6px;font-size:18px}.badge{font-size:12px;font-weight:650;border-radius:4px;padding:3px 7px;background:#e8edf2;white-space:nowrap}.CAPTURED{background:#e3f2e8;color:#20603b}.FAIL,.bad{color:#a52c28}.badge.FAIL{background:#fbe6e5}.NOT_RUN{background:#edf0f3;color:#596579}.UNREACHABLE{background:#f7eddb;color:#775311}.meta{display:flex;gap:14px;flex-wrap:wrap;color:var(--muted);font-size:13px}.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(280px,1fr));gap:16px;margin-top:14px}figure{margin:0;border:1px solid var(--line);border-radius:6px;overflow:hidden}.zoom{border:0;padding:0;background:#1d2530;width:100%;cursor:zoom-in;display:block}.zoom img{display:block;width:100%;height:210px;object-fit:contain}figcaption{padding:10px;display:grid;gap:3px;font-size:13px}code{overflow-wrap:anywhere;font-size:12px}details{margin-top:12px;border-top:1px solid var(--line);padding-top:9px}summary{cursor:pointer;color:var(--accent)}pre{white-space:pre-wrap;overflow-wrap:anywhere;background:#f2f5f8;padding:12px;font:12px/1.6 Consolas,monospace;max-height:480px;overflow:auto}table{border-collapse:collapse;width:100%;font-size:13px}th,td{text-align:left;padding:9px;border-bottom:1px solid var(--line);vertical-align:top;overflow-wrap:anywhere}.table-wrap{overflow:auto}th{background:#e9eef3}dialog{width:96vw;max-width:none;height:94vh;border:1px solid #687b8d;border-radius:8px;padding:0;background:#f5f7f9}dialog::backdrop{background:#101b2de0}.viewer-bar{padding:10px;display:flex;align-items:center;gap:12px;flex-wrap:wrap;background:white;border-bottom:1px solid var(--line)}.viewer-bar button{padding:6px 10px;cursor:pointer}.viewer-meta{padding:6px 12px;font-size:12px;overflow-wrap:anywhere}.viewport{height:calc(100% - 130px);overflow:auto;background:#1d2530;padding:10px}.viewport img{display:block;max-width:100%;height:auto;margin:auto}.viewport.native img{max-width:none;margin:0}.hidden,[hidden]{display:none!important}.empty{padding:18px;border:1px dashed #b7c4cf;color:var(--muted)}footer{padding:24px;color:var(--muted);font-size:13px}
"""
STYLE += r"""
.review-fields{display:flex;gap:12px;align-items:flex-start;flex-wrap:wrap;padding:10px 0}.review-fields label{font-size:13px;color:var(--muted)}.review-fields select{padding:5px;display:block}.review-note{flex:1;min-width:220px}.review-note textarea{display:block;width:100%;font:inherit;padding:7px;border:1px solid #b7c4cf;border-radius:5px;resize:vertical;background:white;color:var(--ink)}#export-reviews{padding:8px 12px;border:1px solid #a9bac8;border-radius:5px;background:white;cursor:pointer}#review-storage{font-size:13px}#viewer{display:flex;flex-direction:column}#viewer:not([open]){display:none}#viewer .review-fields{padding:6px 12px;background:#fff;border-bottom:1px solid var(--line)}#viewer .viewport{height:auto;flex:1;min-height:100px}.image-review{color:var(--accent)}
"""


SCRIPT = r"""
const data=JSON.parse(document.getElementById('gallery-data').textContent);
const images=new Map(data.images.map(x=>[x.id,x]));
const reviewLabels={PENDING:'待审',APPROVED:'通过',NEEDS_CHANGE:'需调整'};
const storageKey='gtsr-ui-review-v1:'+data.inventorySHA256+':'+[...new Set(data.candidateSHA256s)].sort().join(',');
const storageMessage=document.getElementById('review-storage');
let reviews={};
function storageUnavailable(){storageMessage.textContent='浏览器本地存储不可用（file:// 可能受限）；本页仍可审阅，请在关闭前导出 JSON。';storageMessage.classList.add('bad');}
try{const saved=localStorage.getItem(storageKey);if(saved){const parsed=JSON.parse(saved);if(!parsed||typeof parsed!=='object'||Array.isArray(parsed))throw new Error('Invalid local review data');reviews=parsed;}storageMessage.textContent='审阅仅保存在当前浏览器本地；可导出 JSON，不修改原始证据。';}catch(error){storageUnavailable();}
function reviewKey(kind,target){if(kind==='image'){const item=images.get(target);return item?'image:'+JSON.stringify([item.candidateSHA256,item.run,item.name,item.sourceSHA256]):null;}return 'entry:'+target;}
function getReview(kind,target){const key=reviewKey(kind,target),saved=key&&reviews[key];return {status:saved&&Object.hasOwn(reviewLabels,saved.status)?saved.status:'PENDING',note:saved&&typeof saved.note==='string'?saved.note:'',updatedAt:saved&&typeof saved.updatedAt==='string'?saved.updatedAt:null};}
function paintReviews(){document.querySelectorAll('[data-review-field]').forEach(field=>{const target=field.dataset.reviewTarget;if(!target)return;field.value=getReview(field.dataset.reviewKind,target)[field.dataset.reviewField];});document.querySelectorAll('[data-image-review]').forEach(label=>{label.textContent='人工审阅：'+reviewLabels[getReview('image',label.dataset.imageReview).status];});}
function saveReview(event){const field=event.target.closest('[data-review-field]');if(!field||!field.dataset.reviewTarget)return;if(event.type==='input'&&field.dataset.reviewField==='status')return;if(event.type==='change'&&field.dataset.reviewField==='note')return;const kind=field.dataset.reviewKind,target=field.dataset.reviewTarget,key=reviewKey(kind,target);if(!key)return;const current=getReview(kind,target);current[field.dataset.reviewField]=field.value;current.updatedAt=new Date().toISOString();reviews[key]=current;try{localStorage.setItem(storageKey,JSON.stringify(reviews));}catch(error){storageUnavailable();}if(field.dataset.reviewField==='status')paintReviews();}
document.addEventListener('input',saveReview);document.addEventListener('change',saveReview);
document.getElementById('export-reviews').addEventListener('click',()=>{const payload={schemaVersion:1,exportedAt:new Date().toISOString(),inventorySHA256:data.inventorySHA256,candidateSHA256s:data.candidateSHA256s,note:'人工视觉审阅状态；独立于截图状态和功能验收。',entries:data.entries.map(entry=>({uiId:entry.id,title:entry.title,candidateSHA256s:[...new Set(entry.imageIds.map(id=>images.get(id).candidateSHA256).filter(Boolean))],screenshots:entry.imageIds.map(id=>{const item=images.get(id);return {name:item.name,run:item.run,candidateSHA256:item.candidateSHA256,sourceSHA256:item.sourceSHA256};}),expectedScreenshots:entry.shots||[],...getReview('entry',entry.id)})),images:data.images.map(item=>({uiIds:item.entryIds,screenshotName:item.name,run:item.run,candidateSHA256:item.candidateSHA256,sourceSHA256:item.sourceSHA256,...getReview('image',item.id)}))};const blob=new Blob([JSON.stringify(payload,null,2)],{type:'application/json;charset=utf-8'}),url=URL.createObjectURL(blob),link=document.createElement('a');link.href=url;link.download='ui-gallery-reviews-'+new Date().toISOString().replace(/[:.]/g,'-')+'.json';document.body.appendChild(link);link.click();link.remove();setTimeout(()=>URL.revokeObjectURL(url),1000);});
const search=document.getElementById('search'),category=document.getElementById('category'),status=document.getElementById('status');
function filter(){let count=0;const term=search.value.toLocaleLowerCase().trim();document.querySelectorAll('.entry').forEach(el=>{const show=(!term||el.dataset.search.includes(term))&&(!category.value||el.dataset.category===category.value)&&(!status.value||el.dataset.status===status.value);el.hidden=!show;if(show)count++;});document.getElementById('visible').textContent=`显示 ${count} / ${data.entries.length} 项`;document.getElementById('no-results').hidden=count!==0;}
[search,category,status].forEach(el=>el.addEventListener(el===search?'input':'change',filter));
const dialog=document.getElementById('viewer'),view=document.getElementById('viewport'),image=document.getElementById('full-image');
document.addEventListener('click',e=>{const button=e.target.closest('[data-image]');if(!button)return;const item=images.get(button.dataset.image);if(!item)return;image.src=item.asset;image.alt=item.name;document.getElementById('original').href=item.asset;document.getElementById('viewer-title').textContent=item.page+' · '+item.run;document.getElementById('viewer-meta').textContent=`${item.guiClass||'GUI 类未记录'} | PNG ${item.pngResolution||'未知'} | scale ${item.guiScale??'未记录'} | SHA256 ${item.sourceSHA256}`;dialog.querySelectorAll('[data-review-kind="image"]').forEach(field=>field.dataset.reviewTarget=item.id);paintReviews();view.classList.add('native');document.getElementById('size-mode').textContent='切换适应窗口';dialog.showModal();});
document.getElementById('close-viewer').addEventListener('click',()=>dialog.close());
document.getElementById('size-mode').addEventListener('click',e=>{const native=view.classList.toggle('native');e.target.textContent=native?'切换适应窗口':'切换 1:1 原比例';});
dialog.addEventListener('click',e=>{if(e.target===dialog)dialog.close();});
filter();
paintReviews();
"""


def render_report(manifest):
    entries, assets, runs = manifest["entries"], manifest["images"], manifest["runs"]
    by_id = {asset["id"]: asset for asset in assets}
    counts = Counter(entry["status"] for entry in entries)
    categories = sorted({str(entry.get("category", "未分类")) for entry in entries})
    parts = ['<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">',
             '<title>UI 全览校准 · 离线证据图库</title><style>' + STYLE + '</style><body><header>',
             '<h1>UI 全览校准 · 离线证据图库</h1>',
             '<p class="muted">清单完整保留 · 真实客户端 PNG · 每张图可查看原比例与原始证据</p>',
             '<div class="notice"><strong>截图展示不等于完整功能验收。</strong> CAPTURED 只表示该条目的预期 PNG 已存在且具有有效 PNG 头；不证明交互、业务、物理输入或 IME 已通过。运行或脚本组失败时，已产生的截图仍然保留。入口不可达和未运行项不会从清单消失。</div>',
             f'<p>生成时间：{esc(manifest["generatedAt"])} · <a href="manifest.json">manifest.json（哈希与来源）</a> · <a href="raw-evidence/gallery-inventory.json">原始目录清单</a></p>',
             '<div class="summary">' + f'<span>目录 <b>{len(entries)}</b> 项</span><span>PNG <b>{len(assets)}</b> 张</span>' +
             ''.join(f'<span>{esc(STATUS_TEXT[s])} <b>{counts[s]}</b></span>' for s in STATUSES) + '</div>']
    if manifest["issues"]:
        parts.append('<div class="notice">' + '<br>'.join(esc(issue) for issue in manifest["issues"]) + '</div>')
    if manifest.get("candidateSHA256Filter"):
        parts.append('<p>本报告仅收录候选 SHA256：<code>' + esc(manifest["candidateSHA256Filter"]) + '</code></p>')
    if manifest.get("inheritance"):
        parts.append('<div class="notice">当前候选为 R4；标注“R3实拍，字节一致继承”的常规截图来自原 R3 实拍，依据限定范围的逐项字节一致凭据继承。Critical 终端截图全部要求 R4 重拍；继承图不会记为 R4 实机截图。<a href="' + esc(manifest["inheritance"]["file"]) + '">查看原始继承凭据</a></div>')
    if manifest.get("coverage", {}).get("normalOnly"):
        parts.append('<div class="notice"><strong>仅常规尺寸部分预览。</strong> 本报告仅纳入名称含 .normal. 的截图；窄屏截图未展示、未在此验收，等待 R4 生产布局修复后验证（PENDING_R4_PRODUCTION_LAYOUT_FIX）。原始清单与完整运行诊断保留，诊断可能包含本次展示范围之外的事件。</div>')
    if any(not run.get("finalEvidence", True) for run in runs):
        parts.append('<div class="notice">包含缺少完整最终证据的运行；实时截图和检查只表示当前进度，不构成最终验收或 PASS。</div>')
    parts.append('</header><main><div class="toolbar"><input id="search" type="search" aria-label="搜索" placeholder="搜索名称、ID、GUI、入口或异常"><select id="category" aria-label="类别"><option value="">全部类别</option>')
    parts.extend(f'<option value="{esc(category)}">{esc(category)}</option>' for category in categories)
    parts.append('</select><select id="status" aria-label="截图状态"><option value="">全部截图状态</option>')
    parts.extend(f'<option value="{s}">{s} · {esc(STATUS_TEXT[s])}</option>' for s in STATUSES)
    parts.append('</select><span id="visible"></span><button id="export-reviews" type="button">导出审阅 JSON</button></div><p id="review-storage" role="status">审阅在当前页面内存中保存；可导出 JSON。</p><p class="muted">人工审阅与自动检查独立；未列出人工记录的截图不表示已复核。“通过”仅表示人工视觉审阅。</p><p id="no-results" class="empty" hidden>没有符合筛选条件的目录项。</p>')
    for entry in entries:
        linked = [by_id[key] for key in entry["imageIds"]]
        search_text = json.dumps([entry] + linked, ensure_ascii=False).lower()
        parts.append(f'<article class="entry" data-category="{esc(entry.get("category", "未分类"))}" data-status="{entry["status"]}" data-search="{esc(search_text)}">')
        parts.append(f'<h3>{esc(entry.get("title", entry["id"]))} <span class="badge {entry["status"]}">{entry["status"]} · {esc(STATUS_TEXT[entry["status"]])}</span></h3>')
        parts.append('<div class="meta">' + ''.join(f'<span>{esc(key)}：{esc(entry.get(key))}</span>' for key in ("id", "category", "kind", "variant", "route")) + '</div>')
        parts.append(f'<p class="{"bad" if entry["functionalStatus"] == "FAIL" else "muted"}">功能验收：{esc(entry["functionalNote"])}</p>')
        parts.append(review_fields("entry", entry["id"]))
        for key, label in (("fixtureNote", "夹具说明"), ("reason", "不可达 / 范围说明")):
            if entry.get(key):
                parts.append(f'<p>{label}：{esc(entry[key])}</p>')
        if entry["missingShots"]:
            parts.append('<p class="bad">缺少预期截图：' + esc('、'.join(entry["missingShots"])) + '</p>')
        if entry.get("pendingShots"):
            parts.append('<details><summary>窄屏待 R4 验证（' + str(len(entry["pendingShots"])) + ' 张，不计入本次常规截图状态）</summary><p>' + esc(entry["scopeNote"]) + '</p>' + diagnostic(entry["pendingShots"]) + '</details>')
        if entry.get("notRunViews"):
            parts.append('<details><summary>明确未测视图边界</summary>' + diagnostic(entry["notRunViews"]) + '</details>')
        if linked:
            parts.append('<div class="grid">' + ''.join(image_card(asset, entry) for asset in linked) + '</div>')
        elif entry["status"] != "UNREACHABLE":
            parts.append('<p class="empty">没有匹配的实际截图，未用占位图替代。</p>')
        parts.append('<details><summary>诊断与证据来源（脚本组、失败检查、GUI、几何与哈希）</summary>' + diagnostic({"entry": entry, "images": linked}) + '</details></article>')
    unmapped = [asset for asset in assets if not asset["entryIds"]]
    if unmapped:
        parts.append('<details><summary>未映射到目录的实际截图（' + str(len(unmapped)) + ' 张）</summary><div class="grid">' + ''.join(image_card(asset) for asset in unmapped) + '</div></details>')
    parts.append('<h2>运行与功能验收证据</h2><p class="muted">以下为原始运行状态。相关检查之外的失败也完整保留；没有失败记录不自动等于功能 PASS。</p>')
    for run in runs:
        parts.append(f'<details><summary>{esc(run["id"])} · 运行 {esc(run["status"])} · checks {esc(run["checksStatus"])} · 失败检查 {len(run["failedChecks"])} · 失败组 {len(run["failedGroups"])}</summary>')
        parts.append(f'<p>候选 SHA256：<code>{esc(run["candidateSHA256"])}</code></p><p>启动分辨率 {esc(run["resolution"])} · GUI scale {esc(run["guiScale"])} · 语言 {esc(run["language"])} · Java heap {esc(run.get("heapGiB"))} GiB · 预留 {esc(run.get("reserveGiB"))} GiB；逐图尺寸以 PNG 实测为准。</p>')
        if run.get("startupToTestLabReadyMillis") is not None:
            parts.append(f'<p>启动至 TestLab 就绪：{esc(run["startupToTestLabReadyMillis"])} ms（单独记录，不计为热批次截图耗时）。</p>')
        jobs = dictionaries(run.get("batchJobs"))
        if jobs:
            columns = (("job", "批次"), ("status", "状态"), ("requested", "请求数"),
                       ("hotBatchElapsedMillis", "热批次 ms"), ("pngCaptureMillisTotal", "PNG 总 ms"),
                       ("clientTickHandlerMillisTotal", "客户端 tick handler 总 ms"))
            parts.append('<div class="table-wrap"><table><thead><tr>' + ''.join('<th>' + label + '</th>' for _, label in columns) + '</tr></thead><tbody>')
            for job in jobs:
                parts.append('<tr>' + ''.join('<td>' + esc(job.get(key)) + '</td>' for key, _ in columns) + '</tr>')
            parts.append('</tbody></table></div>')
        parts.append('<p>原始证据：' + ' · '.join(f'<a href="{esc(row["file"])}">{esc(Path(row["file"]).name)}</a>' for row in run["rawEvidence"]) + f' · <a href="{esc(run["commandEvidence"])}">原始重放命令</a></p>')
        parts.append(diagnostic(run) + '</details>')
    parts.append('<h2>完整目录索引</h2><div class="table-wrap"><table><thead><tr><th>ID</th><th>名称</th><th>类别 / 入口</th><th>截图状态</th><th>预期 / 缺少</th></tr></thead><tbody>')
    for entry in entries:
        parts.append(f'<tr><td>{esc(entry["id"])}</td><td>{esc(entry.get("title"))}</td><td>{esc(entry.get("category"))}<br>{esc(entry.get("route"))}</td><td>{entry["status"]}</td><td>{len(entry.get("shots", []))} / {len(entry["missingShots"])}</td></tr>')
    data = json.dumps({"images": assets, "entries": entries, "inventorySHA256": manifest["inventorySHA256"],
                       "candidateSHA256s": sorted({run["candidateSHA256"] for run in runs if run.get("candidateSHA256")})}, ensure_ascii=False).replace("<", "\\u003c").replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")
    parts.append('</tbody></table></div></main><footer>本报告使用相对 assets 引用、内置样式与脚本，无 CDN、无网络请求；可直接打开 index.html。诊断信息默认折叠。</footer>')
    parts.append('<dialog id="viewer"><div class="viewer-bar"><strong id="viewer-title"></strong><button id="size-mode">切换 1:1 原比例</button><a id="original" target="_blank" rel="noopener">打开原图 ↗</a><button id="close-viewer">关闭（Esc）</button></div><div class="viewer-meta" id="viewer-meta"></div>' + review_fields("image") + '<div class="viewport" id="viewport"><img id="full-image" alt=""></div></dialog>')
    parts.append('<script type="application/json" id="gallery-data">' + data + '</script><script>' + SCRIPT + '</script></body></html>')
    return ''.join(parts)


def main(argv=None):
    parser = argparse.ArgumentParser(description="生成中文离线 UI 截图证据图库，不运行客户端。")
    parser.add_argument("--workspace", type=Path, default=Path("build/ui-calibration-v12139"))
    parser.add_argument("--output", type=Path, default=Path("plan/临时计划/20261016-UI全览校准-v1.21.39"))
    parser.add_argument("--inventory", type=Path, help="指定不可变修订版清单；默认读取 workspace/gallery-inventory.json（相对路径以当前目录为准）")
    parser.add_argument("--candidate-sha", help="主候选完整 SHA256；仅收录该候选和经凭据明确继承的截图")
    parser.add_argument("--inherit-candidate-sha", help="继承源 R3 完整 SHA256，仅允许非 Critical terminal 的 .normal. 截图")
    parser.add_argument("--inheritance-receipt", type=Path, help="R3→R4 逐项字节一致和变更范围的原始 JSON 凭据")
    parser.add_argument("--normal-only", action="store_true", help="只展示并复制名称含 .normal. 的截图；其它清单截图标为待 R4 验证")
    args = parser.parse_args(argv)
    if args.candidate_sha and not re.fullmatch(r"[a-fA-F0-9]{64}", args.candidate_sha):
        parser.error("--candidate-sha 必须为完整的 64 位十六进制 SHA256")
    candidate_filter = args.candidate_sha.lower() if args.candidate_sha else None
    inherited_sha = args.inherit_candidate_sha.lower() if args.inherit_candidate_sha else None
    inheritance, proof_payload = None, None
    if bool(inherited_sha) != bool(args.inheritance_receipt):
        parser.error("--inherit-candidate-sha 与 --inheritance-receipt 必须一起提供")
    if inherited_sha:
        if not candidate_filter or not re.fullmatch(r"[a-f0-9]{64}", inherited_sha) or inherited_sha == candidate_filter:
            parser.error("继承须指定不同的完整主候选与源候选 SHA256")
        try:
            proof, proof_sha, proof_payload = validate_inheritance(args.inheritance_receipt, candidate_filter, inherited_sha)
        except (OSError, ValueError) as error:
            parser.error(f"继承凭据无效：{error}")
        inheritance = {"file": "raw-evidence/inheritance/" + quote(args.inheritance_receipt.name),
                       "sha256": proof_sha, "sourceCandidateSHA256": inherited_sha,
                       "effectiveCandidateSHA256": candidate_filter, "scope": proof["inheritanceScope"],
                       "changedEntries": proof["changedEntries"]}
    workspace, output = args.workspace.resolve(), args.output.resolve()
    if output == workspace or workspace in output.parents:
        parser.error("--output 必须位于证据 workspace 之外，避免修改原始输入")
    if workspace == output or output in workspace.parents:
        parser.error("--output 不能包含证据 workspace")
    if output.exists() and (not output.is_dir() or any((output / name).exists() for name in ("index.html", "manifest.json", "assets", "raw-evidence"))):
        parser.error("拒绝覆盖已有 index.html、manifest.json、assets 或 raw-evidence；其它独立子预览目录可以保留")
    inventory_path = args.inventory.resolve() if args.inventory else workspace / "gallery-inventory.json"
    try:
        inventory = json.loads(inventory_path.read_text(encoding="utf-8-sig"))
    except (OSError, ValueError) as error:
        parser.error(f"无法读取目录清单：{error}")
    if not isinstance(inventory, list) or not all(isinstance(entry, dict) for entry in inventory):
        parser.error("gallery-inventory.json 必须是对象数组")
    ids = set()
    for index, entry in enumerate(inventory):
        if not isinstance(entry.get("id"), str) or not entry["id"] or entry["id"] in ids:
            parser.error(f"目录第 {index + 1} 项的 id 缺失或重复")
        ids.add(entry["id"])
        if not isinstance(entry.get("shots", []), list) or not all(isinstance(name, str) for name in entry.get("shots", [])):
            parser.error(f"{entry['id']} 的 shots 必须是字符串数组")
        if not isinstance(entry.get("groups", []), list) or not all(isinstance(name, str) for name in entry.get("groups", [])):
            parser.error(f"{entry['id']} 的 groups 必须是字符串数组")
    output.mkdir(parents=True, exist_ok=True)
    (output / "raw-evidence").mkdir(exist_ok=True)
    if inheritance:
        proof_target = output / "raw-evidence" / "inheritance" / args.inheritance_receipt.name
        proof_target.parent.mkdir(exist_ok=True)
        proof_target.write_bytes(proof_payload)
    shutil.copy2(inventory_path, output / "raw-evidence" / "gallery-inventory.json")
    if args.normal_only:
        for entry in inventory:
            entry["pendingShots"] = [name for name in entry.get("shots", []) if ".normal." not in name]
            entry["shots"] = [name for name in entry.get("shots", []) if ".normal." in name]
            entry["scopeStatus"] = "PENDING_R4_PRODUCTION_LAYOUT_FIX" if entry["pendingShots"] else "NORMAL_ONLY"
            entry["scopeNote"] = "本次仅展示常规尺寸；原窄屏截图移出展示与验收范围，待 R4 生产布局修复后重新验证。"
    titles = localized_titles(inventory)
    (output / 'raw-evidence/ui-title-map.json').write_text(json.dumps(titles, ensure_ascii=False, indent=2), encoding='utf-8')
    runs, assets, contexts, issues = [], [], {}, []
    runs_dir = workspace / "client-runs"
    excluded_runs = 0
    selected_runs = []
    for run_dir in sorted(runs_dir.iterdir()) if runs_dir.is_dir() else []:
        if not run_dir.is_dir():
            continue
        metadata_issues = []
        metadata = run_metadata(run_dir, metadata_issues)
        if candidate_filter and str(metadata[0].get("candidateSHA256", "")).lower() not in {candidate_filter, inherited_sha}:
            excluded_runs += 1
            continue
        selected_runs.append((run_dir, metadata, metadata_issues))
    allowed = select_inherited_captures(selected_runs, inventory, candidate_filter, inherited_sha, args.normal_only) if inherited_sha else None
    for run_dir, metadata, metadata_issues in selected_runs:
        inherited = str(metadata[0].get("candidateSHA256", "")).lower() == inherited_sha
        if inherited and allowed is not None and not allowed.get(run_dir.name):
            continue
        run, planned, indexed = collect_run(run_dir, output, assets, metadata, args.normal_only,
                                            allowed[run_dir.name] if allowed is not None else None,
                                            candidate_filter, inherited, inheritance)
        run["inheritedSource"] = inherited
        run["issues"].extend(metadata_issues)
        runs.append(run)
        contexts[run_dir.name] = (planned, indexed)
    observations = attach_observations(workspace, output, assets, issues,
                                       {run["id"] for run in runs} if candidate_filter else None)
    if len(inventory) != 110:
        issues.append(f"本轮目标为 110 个目录项，实际输入 {len(inventory)} 项；报告保留全部输入，不补造条目。")
    if not runs:
        issues.append("未发现 client-runs 运行证据；清单仍完整展示，截图状态按未运行或不可达记录。")
    entries = assemble_entries(inventory, runs, assets, contexts)
    manifest = {"schemaVersion": 1, "generatedAt": datetime.now(timezone.utc).isoformat(),
                "workspace": str(workspace), "inventorySource": str(inventory_path), "inventorySHA256": sha256(inventory_path),
                "candidateSHA256Filter": candidate_filter, "excludedRunCount": excluded_runs,
                "currentCandidateSHA256": candidate_filter, "inheritance": inheritance,
                "renderSourceCandidateSHA256s": sorted({asset["candidateSHA256"] for asset in assets if asset.get("candidateSHA256")}),
                "inheritedImageCount": sum(asset["inherited"] for asset in assets),
                "primaryImageCount": sum(not asset["inherited"] for asset in assets),
                "coverage": {"normalOnly": args.normal_only, "partial": args.normal_only,
                             "includedCaptureNamePattern": ".normal." if args.normal_only else "ALL",
                             "narrowAssessed": False,
                             "pendingScopeStatus": "PENDING_R4_PRODUCTION_LAYOUT_FIX" if args.normal_only else None,
                             "pendingShotCount": sum(len(entry.get("pendingShots", [])) for entry in entries),
                             "rawContainsFullRunDiagnostics": True},
                "notice": "截图展示不等于完整功能验收；CAPTURED 不表示功能 PASS。",
                "statusDefinitions": STATUS_TEXT, "counts": dict(Counter(entry["status"] for entry in entries)),
                "issues": issues, "titleMappings": titles, "manualObservations": observations,
                "entries": entries, "images": assets, "runs": runs}
    (output / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    (output / "index.html").write_text(render_report(manifest), encoding="utf-8")
    print(json.dumps({"index": str(output / "index.html"), "entries": len(entries),
                      "images": len(assets), "statuses": manifest["counts"]}, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
