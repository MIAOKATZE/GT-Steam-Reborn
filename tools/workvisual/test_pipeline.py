"""Regression tests for false-green gates and unsafe definition inputs, not a game harness."""
import copy
import contextlib
import io
import json
import re
import sys
import tempfile
import unittest
from unittest import mock
import zipfile
from types import SimpleNamespace
from pathlib import Path

from assets import CRITICAL, FROZEN_NEWLINE_BINDINGS, Resources, historical_hash_match
from common import ROOT, file_sha, parse_json, read_json, rooted, sha
from motion import continuous_sweep, golden, validate_definitions
from pipeline import case_table, compare_images, compare_numbers, required_cases
import pipeline


class PipelineTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.profile = read_json(Path(__file__).with_name("profile.json"))
        cls.definitions = read_json(ROOT / "src/main/resources/assets/gtsr/machinevisual/motions.json")
        # This historical TASK03 contract covers exactly the five controllers in its approved profile.
        # Rebuilt KPA/turbines have separate packaged-fixture and native construction contracts.
        selected = {machine['id'] for machine in cls.profile['machines'].values()}
        cls.definitions['definitions'] = [definition for definition in cls.definitions['definitions']
                                          if definition['id'] in selected]

    def test_current_continuous_paths_keep_new_crust_travel_and_required_world_gates(self):
        result = continuous_sweep(self.definitions, self.profile)
        self.assertEqual("PASS", result["status"])
        machines = {machine["id"]: machine for machine in result["machines"]}
        self.assertEqual([4, 6, 11, 18, 30], sorted(machine["members"] for machine in machines.values()))
        self.assertIn([0, 10, 2], machines["crust_steam_borer"]["worldOccupancyRequired"])
        self.assertIn([0, 6, 2], machines["steam_fluid_drill"]["worldOccupancyRequired"])
        self.assertAlmostEqual(.3659661906262581, machines["atmospheric_centrifuge"]["radialMarginBlocks"])
        self.assertTrue(all(machine["worldOccupancyStatus"] == "NOT_RUN" for machine in machines.values()))

    def test_pose_universe_is_complete_and_nonempty(self):
        result = golden(self.definitions, self.profile)
        self.assertEqual(480, len(result["cases"]))
        self.assertEqual(480, len({case["id"] for case in result["cases"]}))
        self.assertTrue(all(case["values"] for case in result["cases"]))

    def test_bad_definition_inputs_are_rejected(self):
        mutations = [lambda doc: doc.update(schema=9),
                     lambda doc: doc["definitions"][0]["groups"][0].update(axis="w"),
                     lambda doc: doc["definitions"][0]["groups"][0].update(periodMs=0),
                     lambda doc: doc["definitions"][0]["groups"][0].update(travel=float("nan")),
                     lambda doc: doc["definitions"][0]["groups"].append(copy.deepcopy(doc["definitions"][0]["groups"][0])),
                     lambda doc: doc["definitions"][0]["groups"][0]["members"][0].update(x=999)]
        for mutation in mutations:
            with self.subTest(mutation=mutation):
                document = copy.deepcopy(self.definitions)
                mutation(document)
                with self.assertRaises(ValueError):
                    validate_definitions(document, self.profile)

    def test_duplicate_json_keys_nonfinite_and_path_escape_are_rejected(self):
        for value in ('{"schema":1,"schema":2}', '{"value":NaN}', '{"value":Infinity}'):
            with self.assertRaises(ValueError):
                parse_json(value)
        for name in ("../outside", "/absolute", "C:/absolute", "foo\\bar", "foo/../bar"):
            with self.assertRaises(ValueError):
                rooted(ROOT, name)

    def test_jar_directory_entries_are_not_runtime_files(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "candidate.jar"
            with zipfile.ZipFile(path, "w") as product:
                product.writestr("assets/gtsr/workeffects/", b"")
                product.writestr("assets/gtsr/workeffects/provenance.json", b'{}')
            resources = Resources(jar=path)
            try:
                self.assertEqual({"assets/gtsr/workeffects/provenance.json"}, resources.names)
            finally:
                resources.close()

    def test_filesystem_failure_replaces_a_previous_pass_report(self):
        with tempfile.TemporaryDirectory() as folder:
            out = Path(folder)
            (out / "validation.json").write_text('{"schema":1,"status":"PASS"}', encoding="utf-8")
            argv = ["pipeline.py", "audit", "--task-package", folder, "--r5-root", folder, "--out", folder]
            with mock.patch.object(sys, "argv", argv), mock.patch.object(pipeline, "audit", side_effect=OSError("invalid path")), contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(1, pipeline.main())
            self.assertEqual("FAIL", read_json(out / "validation.json")["status"])

    def test_only_pinned_frozen_newline_variants_match_and_report_raw_hashes(self):
        for name, allowed in FROZEN_NEWLINE_BINDINGS.items():
            lf = (ROOT / "src/main/resources" / name).read_bytes().replace(b"\r\n", b"\n")
            crlf = lf.replace(b"\n", b"\r\n")
            self.assertEqual({sha(lf), sha(crlf)}, allowed)
            for raw, expected, endings in ((lf, sha(crlf), "LF"), (crlf, sha(lf), "CRLF")):
                match = historical_hash_match(name, raw, expected, "test")
                self.assertEqual("NORMALIZATION_MATCH", match["classification"])
                self.assertEqual(sha(raw), match["rawSha256"])
                self.assertEqual(expected, match["expectedSha256"])
                self.assertEqual(endings, match["actualLineEndings"])
            self.assertIsNone(historical_hash_match(name, lf, sha(lf), "test"))

    def test_non_newline_edits_member_reordering_and_unpinned_bindings_fail(self):
        name = CRITICAL + "models/v2_v31_base_t3.geometry.json"
        lf = (ROOT / "src/main/resources" / name).read_bytes().replace(b"\r\n", b"\n")
        expected = sha(lf.replace(b"\n", b"\r\n"))
        # Preserve every byte except the order of the first two physical construction members.
        text = lf.decode("utf-8")
        marker = re.search(r'"blocks"\s*:\s*\[', text)
        first = marker.end()
        while text[first].isspace(): first += 1
        decoder = json.JSONDecoder()
        _, first_end = decoder.raw_decode(text, first)
        second = first_end
        while text[second].isspace() or text[second] == ',': second += 1
        _, second_end = decoder.raw_decode(text, second)
        reordered = (text[:first] + text[second:second_end] + text[first_end:second] + text[first:first_end] + text[second_end:]).encode("utf-8")
        self.assertNotEqual(parse_json(lf)["blocks"], parse_json(reordered)["blocks"])
        for path, raw, digest in ((name, lf + b" ", expected), (name, reordered, expected),
                                  (CRITICAL + "models/v2_base_t3.geometry.json", lf, expected),
                                  (name, b"other\nbytes\n", sha(b"other\r\nbytes\r\n"))):
            with self.subTest(path=path, digest=digest):
                with self.assertRaises(ValueError):
                    historical_hash_match(path, raw, digest, "test")

    def test_required_universe_must_be_explicit_nonempty_and_unique(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "cases.json"
            for value in ('{"schema":1,"cases":[]}', '{"schema":1,"cases":["a","a"]}'):
                path.write_text(value, encoding="utf-8")
                with self.assertRaises(ValueError):
                    required_cases(path)

    def test_no_intersection_only_or_empty_numeric_pass(self):
        required = {"a", "b"}
        for cases in ([], [{"id": "a", "values": [1]}], [{"id": "a", "values": [1]}, {"id": "a", "values": [1]}]):
            with self.assertRaises(ValueError):
                case_table({"schema": 1, "status": "PASS", "cases": cases}, required)
        with self.assertRaises(ValueError):
            case_table({"schema": 1, "status": "FAIL", "cases": [{"id": "a"}]}, {"a"})

    def test_numeric_mismatch_wrong_definition_and_zero_samples_fail(self):
        oracle = {"schema": 1, "status": "PASS", "definitionSha256": "a" * 64, "cases": [{"id": "a", "values": [.35]}]}
        actual = copy.deepcopy(oracle)
        actual["cases"][0]["values"] = [-.35]
        self.assertEqual("FAIL", compare_numbers({"a"}, oracle, actual)["status"])
        actual["definitionSha256"] = "b" * 64
        with self.assertRaises(ValueError):
            compare_numbers({"a"}, oracle, actual)
        oracle["cases"][0]["values"] = []
        with self.assertRaises(ValueError):
            compare_numbers({"a"}, oracle, oracle)

    def test_rgba_rgb_difference_with_equal_alpha_is_not_hidden(self):
        from PIL import Image
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            old_product, new_product = root / "old.jar", root / "new.jar"
            old_product.write_bytes(b"explicit-baseline-input")
            new_product.write_bytes(b"explicit-candidate-input")
            old_image, new_image = root / "old.png", root / "new.png"
            Image.new("RGBA", (1, 1), (0, 0, 0, 255)).save(old_image)
            Image.new("RGBA", (1, 1), (255, 0, 0, 255)).save(new_image)
            def inventory(product, image):
                return {"schema": 1, "status": "PASS", "artifactSha256": file_sha(product), "codeSourceSha256": file_sha(product),
                        "cases": [{"id": "actual_required_view", "file": image.name, "sha256": file_sha(image)}]}
            baseline, candidate = inventory(old_product, old_image), inventory(new_product, new_image)
            result = compare_images({"actual_required_view"}, baseline, candidate, root, root, old_product, new_product)
            self.assertEqual("FAIL", result["status"])
            self.assertEqual(1, result["comparedPixels"])
            candidate["codeSourceSha256"] = "0" * 64
            with self.assertRaises(ValueError):
                compare_images({"actual_required_view"}, baseline, candidate, root, root, old_product, new_product)

    def test_candidate_receipts_are_bound_to_explicit_product_definition_and_core(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            definition_path = "assets/gtsr/machinevisual/motions.json"
            definition = b'{"schema":1}'
            source = root / "author.json"
            source.write_bytes(b'{}')
            runtime_files = [{"path": definition_path, "bytes": len(definition), "sha256": sha(definition)}]
            authors = {"schema": 1, "status": "PASS", "files": [{"root": "repo", "path": source.name, "sha256": file_sha(source)}],
                       "licenseMetadata": []}
            authors['contentSha256'] = sha(pipeline.canonical(authors['files']))
            runtime = {"schema": 1, "status": "PASS", "files": runtime_files,
                       "contentSha256": sha(pipeline.canonical(runtime_files))}
            reports = []
            for name in pipeline.AUDIT_REPORTS:
                path = root / (name + ".json")
                value = runtime if name == "runtime-manifest" else authors if name == "authoring-manifest" else {}
                path.write_text(json.dumps(value), encoding="utf-8")
                reports.append({"path": path.name, "sha256": file_sha(path)})
            validation = root / "validation.json"
            validation.write_text(json.dumps({"schema": 1, "status": "PASS", "kind": "offline_asset_validation",
                "reports": reports, "runtimeContentSha256": runtime["contentSha256"]}), encoding="utf-8")
            jars = []
            for name in ["runtime.jar", "dev.jar", "sources.jar"]:
                path = root / name
                with zipfile.ZipFile(path, 'w') as archive:
                    archive.writestr(definition_path, definition)
                    archive.writestr("identity", name)
                jars.append(path)
            core = root / "core.jar"; core.write_bytes(b"explicit-Core-input")
            lock = root / "lock.json"
            lock.write_text(json.dumps({"schema": 1, "dependencies": [{"id": "core_dev", "coordinate": "core:dev:fixture",
                "path": str(core), "sha256": file_sha(core)}]}), encoding="utf-8")
            receipt = root / "receipt.json"
            bound = {"schema": 1, "status": "PASS", "kind": "strict_numeric_comparison", "definitionSha256": sha(definition),
                "codeSources": [{"class": "com.miaokatze.gtsr.common.machinevisual.MachineVisualDefinition", "sha256": file_sha(jars[1])},
                                {"class": "com.miaokatze.miaokatzecore.api.animation.Sampler", "sha256": file_sha(core)}]}
            args = SimpleNamespace(validation=validation, repo=root, jar=jars[0], dev=jars[1], sources=jars[2],
                                   dependency_lock=lock, receipt=[receipt], out=root/"candidate.json")
            wrong_definition = copy.deepcopy(bound); wrong_definition['definitionSha256'] = 'f'*64
            wrong_product = copy.deepcopy(bound); wrong_product['codeSources'][0]['sha256'] = 'e'*64
            wrong_core = copy.deepcopy(bound); wrong_core['codeSources'][1]['sha256'] = 'd'*64
            missing = copy.deepcopy(bound); missing.pop('codeSources')
            for value, expected in [(wrong_definition,"UNBOUND"), (wrong_product,"UNBOUND"), (wrong_core,"UNBOUND"),
                                    (missing,"UNBOUND"), ({"schema":1,"status":"PASS","kind":"unknown"},"UNBOUND"), (bound,"BOUND")]:
                receipt.write_text(json.dumps(value),encoding='utf-8')
                with mock.patch.object(pipeline.subprocess, "run", side_effect=[SimpleNamespace(stdout="fixture-head"), SimpleNamespace(stdout="")]):
                    result = pipeline.candidate(args)
                self.assertEqual(expected, result['additionalReceipts'][0].get('binding'))
                self.assertFalse(result['complete'])
                self.assertEqual({'NOT_RUN'},set(result['gameGates'].values()))

    def test_pixel_receipt_requires_runtime_and_core_identity(self):
        report = {'kind':'strict_pixel_comparison', 'candidateSha256':'a'*64,
                  'candidateCodeSourceSha256':'a'*64, 'candidateCoreSha256':'b'*64}
        artifacts = [{'kind':'runtime','sha256':'a'*64}]
        dependencies = [{'id':'core_runtime','sha256':'b'*64}]
        self.assertEqual('BOUND',pipeline.receipt_binding(report,None,artifacts,dependencies)['binding'])
        for key in ['candidateSha256','candidateCodeSourceSha256','candidateCoreSha256']:
            wrong = dict(report); wrong[key] = 'c'*64
            self.assertEqual('UNBOUND',pipeline.receipt_binding(wrong,None,artifacts,dependencies)['binding'])


if __name__ == "__main__":
    unittest.main()
