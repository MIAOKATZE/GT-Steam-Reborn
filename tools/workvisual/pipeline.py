"""GTSR source -> staged assets -> closed resources -> strict validation -> candidate identities.

Commands never launch Minecraft, edit another repository, or promote offline checks to game acceptance.
"""
import argparse
import collections
import csv
import json
import re
import subprocess
import sys
import zipfile
from pathlib import Path

from assets import CRITICAL, EFFECTS, MACHINE, Resources, authoring_manifest, export_motions, export_r5, runtime_manifest
from common import SCHEMA, ROOT, canonical, file_sha, finite, read_json, require_pass, rooted, sha, write_json
from motion import continuous_sweep, golden

HERE = Path(__file__).resolve().parent
DELETED_TOOLS = ["tools/critical/CriticalV18ClientProbe.java", "tools/critical/client_fixture_v18.py",
                 "tools/critical/native_fixture_v18.py", "tools/testlab/client_qa/bind_v209_history.py"]
GAME_GATES = ["real_client_state_tick_gl", "dedicated_server", "business_equivalence",
              "lifecycle_network_and_reload", "old_and_new_shape_compatibility", "performance_and_mod_combinations"]
AUDIT_REPORTS = ["runtime-manifest", "authoring-manifest", "scope-audit", "effects-contract", "motion-export", "continuous-sweep",
                 "pose-oracle", "scalar-oracle", "required-pose-cases", "required-scalar-cases"]


def csv_rows(path):
    with Path(path).open(encoding="utf-8-sig", newline="") as stream:
        return list(csv.DictReader(stream))


def scope_audit(repo, task, profile):
    rows = csv_rows(Path(task) / "最终逐机范围矩阵.csv")
    targets = csv_rows(Path(task) / "工作动画目标-16控制器.csv")
    registrations = csv_rows(Path(task) / "动画覆盖台账-82项.csv")
    expected = {row["controller"]: row["scope"] for row in rows}
    if len(rows) != profile["auditedControllers"] or len(expected) != len(rows):
        raise ValueError("Controller audit must contain 16 unique rows")
    if {row["item_key"]: row["final_scope"] for row in targets} != expected or len(targets) != len(rows):
        raise ValueError("16-controller treatment list and final matrix disagree")
    if {key for key, value in expected.items() if value == "STRUCTURE_MOTION"} != set(profile["machines"]):
        raise ValueError("Final matrix must select exactly the authorized five motions")
    if {key for key, value in expected.items() if value == "EXCLUDED_USER_REBUILD_NO_CHANGE"} != set(profile["excluded"]):
        raise ValueError("KPA/Mega exclusion differs")
    if collections.Counter(expected.values()) != {"STRUCTURE_MOTION": 5, "FX_ONLY": 9, "EXCLUDED_USER_REBUILD_NO_CHANGE": 2}:
        raise ValueError("Unknown final scope or incorrect disposition counts")
    loader_path = Path(repo) / "src/main/java/com/miaokatze/gtsr/loader/MachineLoader.java"
    source = loader_path.read_text(encoding="utf-8")
    matches = re.findall(r'GTSRItemList\.(\w+)\s*\.set\(\s*new\s+(\w+)\(\s*MetaTileEntityID\.(\w+)\.ID,\s*"([^"]+)"', source)
    actual = {row[0]: row[1:] for row in matches}
    keys = {row["item_key"] for row in registrations}
    if len(registrations) != profile["auditedRegistrations"] or len(keys) != len(registrations) or keys != set(actual):
        raise ValueError("82-registration audit must exactly cover the current MachineLoader")
    for row in registrations:
        if not row["final_scope"] or actual[row["item_key"]] != (row["class"], row["id_enum"], row["internal_name"]):
            raise ValueError("Registration identity/classification mismatch: " + row["item_key"])
        if row["item_key"] in expected and row["final_scope"] != expected[row["item_key"]]:
            raise ValueError("82-item treatment differs from final controller scope")
    enum_path = Path(repo) / "src/main/java/com/miaokatze/gtsr/common/api/enums/MetaTileEntityID.java"
    enum_source = enum_path.read_text(encoding="utf-8")
    relative_ids = dict((name, int(number)) for name, number in re.findall(r"^\s*(\w+)\((\d+)\)", enum_source, re.M))
    for row in registrations:
        row["relative_mte_id"] = relative_ids[row["id_enum"]]
    return {"schema": SCHEMA, "status": "PASS", "kind": "scope_and_registration_audit",
            "controllerCount": len(rows), "registrationCount": len(registrations), "scopes": dict(collections.Counter(expected.values())),
            "controllers": rows, "registrations": registrations, "loaderSha256": file_sha(loader_path), "enumSha256": file_sha(enum_path),
            "deletedToolState": [{"path": name, "exists": (Path(repo) / name).exists(), "replacement": "tools/workvisual/pipeline.py offline checks; game harness remains a separate gate"} for name in DELETED_TOOLS],
            "boundary": "Actual registration source identities and configured relative IDs; not a full loaded GT/GTNL registry collision check. Deleted tools are observed, never restored."}


def effects_contract(resources, scope):
    provenance = resources.json(EFFECTS + "provenance.json")
    if provenance.get("schema") != "gtsr.workeffects.v1":
        raise ValueError("Unknown effects provenance schema")
    allowed = {row["controller"] for row in scope["controllers"] if row["scope"] != "EXCLUDED_USER_REBUILD_NO_CHANGE"}
    machines = provenance["machines"]
    if not machines or len({machine["machine"] for machine in machines}) != len(machines):
        raise ValueError("Empty/duplicate effects machine list")
    existing = proposed = fireboxes = 0
    stable_ids = set()
    for machine in machines:
        if machine["machine"] not in allowed:
            raise ValueError("Effect attached outside authorized scope")
        fireboxes += len(machine["fireboxesABC"])
        for emitter in machine["emitters"]:
            if emitter["id"] in stable_ids or not emitter["id"].startswith(machine["machine"] + ":"):
                raise ValueError("Duplicate/non-owned stable emitter ID")
            stable_ids.add(emitter["id"])
            if emitter["origin"] == "existing":
                existing += 1
            elif emitter["origin"] == "selected-proposal":
                proposed += 1
                if emitter["aboveABC"] != [emitter["abc"][0], emitter["abc"][1] - 1, emitter["abc"][2]]:
                    raise ValueError("Particle ABC upward-air conversion differs")
            else:
                raise ValueError("Unknown emitter origin")
    tcds = provenance["tcds"]["existingAnchors"]
    if (existing, proposed, tcds) != (18, 3, 20):
        raise ValueError("Expected original 38 plus selected 3 particle anchors, merged exactly once")
    foreground = provenance["foreground"]
    if sha(resources.read(foreground["resource"])) != foreground["sha256"] or foreground["nativeActive"] is not False:
        raise ValueError("Foreground bytes/native-active classification differs")
    return {"schema": SCHEMA, "status": "PASS", "kind": "effect_resource_contract",
            "fireboxCells": fireboxes, "existingAnchors": existing + tcds, "selectedProposalAnchors": proposed,
            "stableEmitterIDs": sorted(stable_ids), "foregroundSha256": foreground["sha256"],
            "boundary": "Source token/provenance/resource identity only; visible-face masking, real working state/tick, multi-machine ownership and actual world obstruction are NOT_RUN."}


def audit(args):
    profile = read_json(args.profile)
    resources = Resources(directory=args.resource_root if not args.jar else None, jar=args.jar)
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=True)
    try:
        runtime = runtime_manifest(resources)
        authors = authoring_manifest(args.repo, args.task_package, args.r5_root, read_json(HERE / "authoring.json"))
        scope = scope_audit(args.repo, args.task_package, profile)
        effects = effects_contract(resources, scope)
        definitions = resources.json(MACHINE + "motions.json")
        definition_sha = sha(resources.read(MACHINE + "motions.json"))
        export = export_motions(args.task_package / "visual-reference/runtime/models.json", profile, out / "staged/motions.json")
        if canonical(read_json(out / "staged/motions.json")) != canonical(definitions):
            raise ValueError("Production motion definitions differ from the approved deterministic export")
        sweep = continuous_sweep(definitions, profile)
        require_pass(sweep, "Continuous geometry")
        poses = golden(definitions, profile)
        poses["definitionSha256"] = definition_sha
        scalars = {"schema": SCHEMA, "status": "PASS", "kind": "scalar_oracle", "definitionSha256": definition_sha,
                   "cases": poses.pop("scalarCases"), "boundary": "Expected Core-sampler numeric values only; compare to a separate production Java probe."}
        required = {"schema": SCHEMA, "cases": [value["id"] for value in poses["cases"]]}
        scalar_required = {"schema": SCHEMA, "cases": [value["id"] for value in scalars["cases"]]}
        for name, report in [("runtime-manifest", runtime), ("authoring-manifest", authors), ("scope-audit", scope),
                             ("effects-contract", effects), ("motion-export", export), ("continuous-sweep", sweep),
                             ("pose-oracle", poses), ("scalar-oracle", scalars), ("required-pose-cases", required), ("required-scalar-cases", scalar_required)]:
            write_json(out / (name + ".json"), report)
        validation = {"schema": SCHEMA, "status": "PASS", "kind": "offline_asset_validation", "runtimeContentSha256": runtime["contentSha256"],
                      "reports": [{"path": name + ".json", "sha256": file_sha(out / (name + ".json"))} for name in AUDIT_REPORTS],
                      "counts": runtime["counts"] | {"poseCases": len(required["cases"]), "scalarCases": len(scalar_required["cases"])},
                      "gameGates": {gate: "NOT_RUN" for gate in GAME_GATES},
                      "boundary": "Offline source/resource/math checks only. This report cannot sign real client/server/business/performance acceptance."}
        write_json(out / "validation.json", validation)
        return validation
    finally:
        resources.close()


def required_cases(path):
    document = read_json(path)
    cases = document.get("cases")
    if document.get("schema") != SCHEMA or not isinstance(cases, list) or not cases or any(not isinstance(value, str) or not value for value in cases):
        raise ValueError("Required case universe must be explicit and nonempty")
    if len(cases) != len(set(cases)):
        raise ValueError("Duplicate required case ID")
    return set(cases)


def case_table(document, required):
    require_pass(document, "Compared input")
    cases = document.get("cases")
    if not isinstance(cases, list) or not cases:
        raise ValueError("Compared case list is empty")
    table = {case["id"]: case for case in cases}
    if len(table) != len(cases) or set(table) != required:
        raise ValueError("Compared case universe differs; missing=" + str(sorted(required - set(table))) + "; extra=" + str(sorted(set(table) - required)))
    return table


def compare_numbers(required, oracle, actual, tolerance=1e-9):
    expected, observed = case_table(oracle, required), case_table(actual, required)
    digest = oracle.get("definitionSha256")
    if not isinstance(digest, str) or not re.fullmatch("[0-9a-f]{64}", digest) or actual.get("definitionSha256") != digest:
        raise ValueError("Numeric samples are not bound to the same exact definition bytes")
    finite(tolerance, "tolerance")
    if tolerance < 0 or tolerance > 1e-6:
        raise ValueError("Tolerance must be between zero and 1e-6 blocks/radians")
    maximum = 0.0
    count = 0
    failures = []
    for case in sorted(required):
        left, right = expected[case]["values"], observed[case]["values"]
        if not left or len(left) != len(right):
            raise ValueError("Empty/different numeric sample vector: " + case)
        for a, b in zip(left, right):
            error = abs(finite(a, case) - finite(b, case))
            maximum = max(maximum, error)
            count += 1
            if error > tolerance:
                failures.append({"id": case, "error": error})
                break
    return {"schema": SCHEMA, "status": "FAIL" if failures else "PASS", "kind": "strict_numeric_comparison",
            "definitionSha256": digest, "requiredCases": len(required), "comparedValues": count,
            "maximumError": maximum, "tolerance": tolerance, "failures": failures,
            "boundary": "Exact required case set; numeric production probe acceptance only, not a GL/world test."}


def compare_images(required, baseline, candidate, baseline_root, candidate_root, baseline_jar, candidate_jar):
    from PIL import Image, ImageChops
    left = case_table(baseline, required)
    right = case_table(candidate, required)
    for document, product in ((baseline, baseline_jar), (candidate, candidate_jar)):
        digest = file_sha(product)
        if document.get("artifactSha256") != digest or document.get("codeSourceSha256") != digest:
            raise ValueError("Image inventory is not bound to the explicit product/CodeSource identity")
    failures = []
    pixels = 0
    for identifier in sorted(required):
        images = []
        for record, root in ((left[identifier], baseline_root), (right[identifier], candidate_root)):
            path = rooted(root, record["file"])
            if file_sha(path) != record["sha256"]:
                raise ValueError("Image hash mismatch: " + identifier)
            with Image.open(path) as source:
                source.load()
                if not source.width or not source.height:
                    raise ValueError("Empty image: " + identifier)
                images.append(source.convert("RGBA"))
        a, b = images
        pixels += a.width * a.height
        # ImageChops.getbbox on RGBA alone can ignore RGB changes under equal alpha.
        same = a.size == b.size and all(channel.getbbox() is None for channel in ImageChops.difference(a, b).split())
        if not same:
            failures.append(identifier)
    return {"schema": SCHEMA, "status": "FAIL" if failures else "PASS", "kind": "strict_pixel_comparison",
            "requiredCases": len(required), "comparedPixels": pixels, "failures": failures,
            "baselineSha256": file_sha(baseline_jar), "candidateSha256": file_sha(candidate_jar),
            "boundary": "PNG bytes/pixels and reported CodeSource identities; capture harness determines whether evidence is browser, FBO or a real client."}


def candidate(args):
    validation_path = args.validation.resolve()
    validation = read_json(validation_path)
    require_pass(validation, "Offline validation")
    required_reports = {name + ".json" for name in AUDIT_REPORTS}
    if validation.get("kind") != "offline_asset_validation" or {entry["path"] for entry in validation["reports"]} != required_reports or len(validation["reports"]) != len(required_reports):
        raise ValueError("Offline validation must contain the complete nonempty report universe")
    for report in validation["reports"]:
        if file_sha(rooted(validation_path.parent, report["path"])) != report["sha256"]:
            raise ValueError("Validation report changed after audit: " + report["path"])
    manifest = read_json(validation_path.parent / "runtime-manifest.json")
    authors = read_json(validation_path.parent / "authoring-manifest.json")
    require_pass(manifest, "Runtime closure")
    require_pass(authors, "Authoring sources")
    if not manifest.get("files") or not authors.get("files"):
        raise ValueError("Runtime and authoring manifests must both be nonempty")
    if sha(canonical(authors["files"])) != authors["contentSha256"]:
        raise ValueError("Authoring source identity mismatch")
    for source in authors["files"]:
        if source["root"] == "repo" and file_sha(rooted(args.repo, source["path"])) != source["sha256"]:
            raise ValueError("Repository authoring input changed after validation: " + source["path"])
    if sha(canonical(manifest["files"])) != manifest["contentSha256"] or validation["runtimeContentSha256"] != manifest["contentSha256"]:
        raise ValueError("Runtime closure identity mismatch")
    artifacts = []
    for kind, path in (("runtime", args.jar), ("dev", args.dev), ("sources", args.sources)):
        if not zipfile.is_zipfile(path):
            raise ValueError("Artifact is not a valid JAR: " + str(path))
        if kind != "sources":
            with zipfile.ZipFile(path) as product:
                names = product.namelist()
                if len(names) != len(set(names)):
                    raise ValueError("Duplicate candidate ZIP entry")
                for record in manifest["files"]:
                    raw = product.read(record["path"])
                    if sha(raw) != record["sha256"] or len(raw) != record["bytes"]:
                        raise ValueError("Candidate resource differs from validated closure: " + record["path"])
        artifacts.append({"kind": kind, "file": path.name, "bytes": path.stat().st_size, "sha256": file_sha(path)})
    dependencies = read_json(args.dependency_lock)
    if dependencies.get("schema") != SCHEMA or not dependencies.get("dependencies"):
        raise ValueError("Explicit nonempty dependency lock required")
    locked = []
    for entry in dependencies["dependencies"]:
        path = Path(entry["path"])
        if not path.is_absolute():
            path = rooted(args.dependency_lock.parent, entry["path"])
        if file_sha(path) != entry["sha256"] or not entry.get("coordinate") or not entry.get("id"):
            raise ValueError("Dependency bytes/coordinate differ: " + str(entry.get("id")))
        locked.append({key: entry[key] for key in ("id", "coordinate", "sha256")} | {"file": path.name, "bytes": path.stat().st_size})
    if len({entry["id"] for entry in locked}) != len(locked):
        raise ValueError("Duplicate dependency identity")
    receipts = []
    for path in args.receipt:
        report = read_json(path)
        require_pass(report, "Candidate additional receipt")
        receipts.append({"file": path.name, "sha256": file_sha(path), "kind": report.get("kind"), "boundary": report.get("boundary")})
    head = subprocess.run(["git", "rev-parse", "HEAD"], cwd=args.repo, check=True, capture_output=True, text=True).stdout.strip()
    dirty = subprocess.run(["git", "status", "--porcelain", "--untracked-files=all"], cwd=args.repo, check=True, capture_output=True, text=True).stdout.splitlines()
    result = {"schema": SCHEMA, "status": "PASS", "kind": "candidate_identity_binding", "complete": False,
              "source": {"head": head, "dirty": dirty}, "artifacts": artifacts, "dependencies": locked,
              "runtimeContentSha256": manifest["contentSha256"], "authoringContentSha256": authors["contentSha256"],
              "validationSha256": file_sha(validation_path), "additionalReceipts": receipts,
              "gameGates": {gate: "NOT_RUN" for gate in GAME_GATES}, "licenseMetadata": authors["licenseMetadata"],
              "publication": "NOT_PERFORMED", "deployment": "NOT_PERFORMED",
              "boundary": "Same-candidate artifact/resource/dependency binding only. This is a reviewable candidate, not completed game acceptance or permission to publish/deploy."}
    write_json(args.out, result)
    return result


def parser():
    cli = argparse.ArgumentParser(description=__doc__)
    commands = cli.add_subparsers(dest="command", required=True)
    audit_cli = commands.add_parser("audit", help="Regenerate separate runtime/authoring manifests and offline contracts")
    audit_cli.add_argument("--repo", type=Path, default=ROOT)
    audit_cli.add_argument("--resource-root", type=Path, default=ROOT / "src/main/resources")
    audit_cli.add_argument("--jar", type=Path, help="Read resources only from this explicit candidate JAR")
    audit_cli.add_argument("--task-package", required=True, type=Path)
    audit_cli.add_argument("--r5-root", required=True, type=Path)
    audit_cli.add_argument("--profile", type=Path, default=HERE / "profile.json")
    audit_cli.add_argument("--out", required=True, type=Path)
    r5 = commands.add_parser("export-r5", help="Reproduce recorded R5 revision31 into a staging directory")
    r5.add_argument("--source-root", required=True, type=Path)
    r5.add_argument("--binding", type=Path, default=ROOT / "src/main/resources" / CRITICAL / "source-sha256.json")
    r5.add_argument("--out", required=True, type=Path)
    motions = commands.add_parser("export-motions", help="Export approved source through the GTSR motion profile")
    motions.add_argument("--models", required=True, type=Path)
    motions.add_argument("--profile", type=Path, default=HERE / "profile.json")
    motions.add_argument("--out", required=True, type=Path)
    numbers = commands.add_parser("compare-numbers", help="Compare the entire explicit golden numeric universe")
    numbers.add_argument("--required", required=True, type=Path)
    numbers.add_argument("--oracle", required=True, type=Path)
    numbers.add_argument("--actual", required=True, type=Path)
    numbers.add_argument("--tolerance", type=float, default=1e-9)
    numbers.add_argument("--out", required=True, type=Path)
    images = commands.add_parser("compare-images", help="Compare exact nonempty case inventories and strict RGBA pixels")
    images.add_argument("--required", required=True, type=Path)
    images.add_argument("--baseline", required=True, type=Path)
    images.add_argument("--candidate", required=True, type=Path)
    images.add_argument("--baseline-jar", required=True, type=Path)
    images.add_argument("--candidate-jar", required=True, type=Path)
    images.add_argument("--out", required=True, type=Path)
    bind = commands.add_parser("candidate", help="Bind validated resources to explicit runtime/dev/sources and dependencies")
    bind.add_argument("--repo", type=Path, default=ROOT)
    bind.add_argument("--validation", required=True, type=Path)
    bind.add_argument("--jar", required=True, type=Path)
    bind.add_argument("--dev", required=True, type=Path)
    bind.add_argument("--sources", required=True, type=Path)
    bind.add_argument("--dependency-lock", required=True, type=Path)
    bind.add_argument("--receipt", action="append", type=Path, default=[])
    bind.add_argument("--out", required=True, type=Path)
    return cli


def main():
    args = parser().parse_args()
    try:
        if args.command == "audit":
            result = audit(args)
        elif args.command == "export-r5":
            result = export_r5(args.source_root, args.binding, args.out)
        elif args.command == "export-motions":
            result = export_motions(args.models, read_json(args.profile), args.out)
        elif args.command == "compare-numbers":
            result = compare_numbers(required_cases(args.required), read_json(args.oracle), read_json(args.actual), args.tolerance)
            actual = read_json(args.actual)
            result["inputs"] = {"requiredSha256": file_sha(args.required), "oracleSha256": file_sha(args.oracle), "actualSha256": file_sha(args.actual)}
            result["actualEvidenceKind"] = actual.get("kind")
            result["actualCodeSources"] = actual.get("codeSources", [])
            write_json(args.out, result)
        elif args.command == "compare-images":
            result = compare_images(required_cases(args.required), read_json(args.baseline), read_json(args.candidate),
                                    args.baseline.parent, args.candidate.parent, args.baseline_jar, args.candidate_jar)
            write_json(args.out, result)
        else:
            result = candidate(args)
    except (ValueError, KeyError, OSError, zipfile.BadZipFile) as error:
        result = {"schema": SCHEMA, "status": "FAIL", "kind": args.command, "error": str(error)}
        target = args.out / ("validation.json" if args.command == "audit" else "export-manifest.json") if args.command in ("audit", "export-r5") else args.out
        write_json(target, result)
    print(json.dumps({key: result[key] for key in ("status", "kind", "counts", "error") if key in result}, ensure_ascii=True))
    return 0 if result["status"] == "PASS" else 1


if __name__ == "__main__":
    sys.exit(main())
