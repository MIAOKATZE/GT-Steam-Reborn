"""Regression tests for false-green gates and unsafe definition inputs, not a game harness."""
import copy
import contextlib
import io
import sys
import tempfile
import unittest
from unittest import mock
import zipfile
from pathlib import Path

from assets import Resources
from common import ROOT, file_sha, parse_json, read_json, rooted
from motion import continuous_sweep, golden, validate_definitions
from pipeline import case_table, compare_images, compare_numbers, required_cases
import pipeline


class PipelineTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.profile = read_json(Path(__file__).with_name("profile.json"))
        cls.definitions = read_json(ROOT / "src/main/resources/assets/gtsr/machinevisual/motions.json")

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


if __name__ == "__main__":
    unittest.main()
