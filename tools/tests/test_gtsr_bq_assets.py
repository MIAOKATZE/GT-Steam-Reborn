"""Real reviewed-pack regression tests; writes only temporary repositories."""
import contextlib
import copy
import io
import json
import shutil
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import gtsr_bq_assets as adapter


class ReviewedPackTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.repo = Path(self.temp.name)
        self.ws_root = self.repo / "source"
        self.ws_dir = self.ws_root / "workspace/bq/gtsr"
        shutil.copytree(adapter.EDITOR_ROOT / "workspace/bq/gtsr", self.ws_dir)
        self.quests = json.loads((adapter.EDITOR_ROOT / "workspace/bq/gtsr/quests.json").read_text(encoding="utf-8"))
        self.by_seq = {q["seq"]: q for q in self.quests}
        self.lang_dir = self.repo / "src/main/resources/assets/gtsr/lang"
        self.lang_dir.mkdir(parents=True)
        for lang in ("en_US", "zh_CN"):
            (self.lang_dir / (lang + ".lang")).write_text("outside.block=preserve\n", encoding="utf-8")
        self.assertEqual(0, self.run_mode("generate"))
        self.base = self.repo / "src/main/resources/assets/gtsr/bqquests"
        self.index = json.loads((self.base / "index.json").read_text(encoding="utf-8"))
        self.line = self.index["questLines"][0]
        self.paths = {}
        for entry in self.line["entries"]:
            path = self.repo / "src/main/resources" / entry["questFile"]
            data = json.loads(path.read_text(encoding="utf-8"))
            self.paths[data["questIDLow:4"]] = path

    def run_mode(self, mode):
        with contextlib.redirect_stdout(io.StringIO()):
            return adapter.main(["--repo-root", str(self.repo), "--ws-root", str(self.ws_root), "--mode", mode])

    def quest(self, seq):
        return json.loads(self.paths[seq].read_text(encoding="utf-8"))

    def test_all_84_roundtrip_and_reviewed_edges(self):
        self.assertEqual(set(range(1, 85)), set(self.paths))
        self.assertEqual(adapter.LINE_HIGH, self.line["idHigh"])
        self.assertEqual(1, self.line["idLow"])
        self.assertEqual(900, self.line["orderIndex"])
        self.assertEqual(0, self.run_mode("audit"))
        cell_ores = []
        for seq in range(71, 77):
            task = self.quest(seq)["tasks:9"]["0:10"]
            stack = task["requiredItems:9"]["0:10"]
            ws_stack = self.by_seq[seq]["tasks"][0]["items"][0]
            self.assertEqual(0, task["consume:1"])
            self.assertEqual(("gregtech:gt.metaitem.01", 0, 1),
                             (stack["id:8"], stack["Damage:2"], stack["Count:3"]))
            self.assertEqual(ws_stack["oredict"], stack["OreDict:8"])
            self.assertNotIn("tag:10", stack)
            cell_ores.append(stack["OreDict:8"])
        self.assertEqual({"cellwastesigh", "cellthickgrease", "cellmetalgrit", "cellumbralmire",
                          "cellsanzu_residual_steam", "cellwithered_breath"}, set(cell_ores))
        location = self.quest(70)["tasks:9"]["0:10"]
        self.assertEqual("bq_standard:location", location["taskID:8"])
        self.assertEqual(("prosperity", 78, -1),
                         (location["gtsrDimension:8"], location["dimension:3"], location["range:3"]))
        reward = self.quest(79)["rewards:9"]
        self.assertEqual(1, len(reward))
        self.assertEqual("bq_standard:choice", reward["0:10"]["rewardID:8"])
        choices = reward["0:10"]["choices:9"]
        self.assertEqual({"gtsr:MimicAmmo762Pack", "gtsr:MimicAmmo20Pack", "gtsr:MimicAmmo35Pack"},
                         {s["id:8"] for s in choices.values()})
        self.assertTrue(all(s["Count:3"] == 1 for s in choices.values()))
        self.assertEqual("bq_standard:checkbox", self.quest(83)["tasks:9"]["0:10"]["taskID:8"])
        abyssal = self.quest(84)["tasks:9"]["0:10"]
        self.assertEqual("bq_standard:retrieval", abyssal["taskID:8"])
        self.assertEqual((0, 1), (abyssal["consume:1"], abyssal["ignoreNBT:1"]))
        bucket = abyssal["requiredItems:9"]["0:10"]
        self.assertEqual(("gtsr:AbyssalObsessionBucket", 1), (bucket["id:8"], bucket["Count:3"]))
        self.assertEqual([70], [p["questIDLow:4"] for p in self.quest(84)["preRequisites:9"].values()])
        for seq, prereq in ((83, 40), (68, 83), (77, 83), (67, 33)):
            self.assertIn(prereq, [p["questIDLow:4"] for p in self.quest(seq)["preRequisites:9"].values()])
        for entry in self.line["entries"]:
            data = json.loads((self.repo / "src/main/resources" / entry["entryFile"]).read_text(encoding="utf-8"))
            ws = self.by_seq[data["questIDLow:4"]]
            self.assertEqual([ws["x"], ws["y"], ws["size"], ws["size"]],
                             [data["x:3"], data["y:3"], data["sizeX:3"], data["sizeY:3"]])
        for lang in ("en_US", "zh_CN"):
            self.assertTrue((self.lang_dir / (lang + ".lang")).read_text(encoding="utf-8").startswith("outside.block=preserve\n"))

    def test_audit_detects_missing_and_wrong_fluid_nbt(self):
        # Preserve typed-NBT tamper coverage with a dedicated temporary source fixture.
        self.by_seq[71]["tasks"][0]["items"][0]["nbt"] = {
            "Fluid:10": {"FluidName:8": "wastesigh", "Amount:3": 1000}}
        (self.ws_dir / "quests.json").write_text(json.dumps(self.quests), encoding="utf-8")
        self.assertEqual(0, self.run_mode("generate"))
        path = self.paths[71]
        original = self.quest(71)
        changed = copy.deepcopy(original)
        del changed["tasks:9"]["0:10"]["requiredItems:9"]["0:10"]["tag:10"]
        path.write_text(json.dumps(changed), encoding="utf-8")
        self.assertEqual(1, self.run_mode("audit"))
        changed = copy.deepcopy(original)
        changed["tasks:9"]["0:10"]["requiredItems:9"]["0:10"]["tag:10"]["Fluid:10"]["FluidName:8"] = "wrongfluid"
        path.write_text(json.dumps(changed), encoding="utf-8")
        self.assertEqual(1, self.run_mode("audit"))

    def test_location_schema_and_oredict_compatibility(self):
        shared = adapter.load_module("test_location_shared", adapter.EDITOR_ROOT / "tools/generate_bq_assets.py")
        codec = adapter.load_module("test_location_codec", adapter.EDITOR_ROOT / "app/nbt.py")
        adapter.install_adapter(shared, codec)
        stack = shared.stack_nbt(*shared.parse_stack({"item": "gregtech:gt.metaitem.01", "count": 24,
                                                     "oredict": "cellwastesigh"}))
        self.assertEqual("cellwastesigh", stack["OreDict:8"])
        self.assertEqual(24, stack["Count:3"])
        entry = shared.build_tasks([{"type": "checkbox"}, {"type": "location", "dimension_key": "prosperity"}])["1:10"]
        self.assertEqual({"index:3": 1, "taskID:8": "bq_standard:location", "name:8": "Prosperity",
                          "posX:3": 0, "posY:3": 0, "posZ:3": 0, "dimension:3": 78,
                          "biome:3": -1, "structure:8": "", "range:3": -1, "visible:1": 0,
                          "hideInfo:1": 0, "invert:1": 0, "taxiCabDist:1": 0,
                          "gtsrDimension:8": "prosperity"}, entry)
        with self.assertRaises(ValueError):
            shared.build_tasks([{"type": "location", "dimension_key": "other"}])
        with self.assertRaises(ValueError):
            shared.build_tasks([{"type": "location", "dimension_key": "prosperity", "range": 16}])

    def test_real_existing_lang_preserves_prefix_suffix_bytes(self):
        begin = b"# BQ quests BEGIN"
        end = b"# BQ quests END"
        snapshots = {}
        for lang in ("en_US", "zh_CN"):
            source = adapter.REPO_ROOT / "src/main/resources/assets/gtsr/lang" / (lang + ".lang")
            raw = source.read_bytes()
            prefix, _, remainder = raw.partition(begin)
            block, _, suffix = remainder.partition(end)
            self.assertTrue(block)
            # Force a block replacement while retaining the real surrounding text.
            target = self.lang_dir / (lang + ".lang")
            target.write_bytes(prefix + begin + b"\r\nold.block=value\r\n" + end + suffix)
            snapshots[target] = (prefix, suffix)
        self.assertEqual(0, self.run_mode("generate"))
        for target, (prefix, suffix) in snapshots.items():
            raw = target.read_bytes()
            self.assertEqual(prefix, raw.partition(begin)[0])
            self.assertEqual(suffix, raw.partition(end)[2])
        before = {path: path.read_bytes() for path in snapshots}
        self.assertEqual(0, self.run_mode("generate"))
        self.assertEqual(before, {path: path.read_bytes() for path in snapshots})

    def test_stale_prune_dry_run_and_foreign_line_protection(self):
        existing = self.paths[1]
        stale = existing.with_name("OldTitle-" + existing.name.split("-", 1)[1])
        stale.write_bytes(existing.read_bytes())
        old_line = existing.parent.with_name("OldLine-" + existing.parent.name.split("-", 1)[1])
        old_line.mkdir()
        old_quest = old_line / existing.name
        old_quest.write_bytes(existing.read_bytes())
        old_entries = self.base / "QuestLines" / old_line.name
        old_entries.mkdir()
        current_line_path = self.repo / "src/main/resources" / self.line["lineFile"]
        (old_entries / "QuestLine.json").write_bytes(current_line_path.read_bytes())
        foreign = self.base / "Quests/ForeignLine-AAAAAAAAAAAAAAAAAAAAAQ==/Other.json"
        foreign.parent.mkdir()
        foreign.write_text("{}", encoding="utf-8")
        foreign_same_dir = existing.parent / "Foreign-AAAAAAAAAAAAAAAAAAAAAg==.json"
        foreign_same_dir.write_text("{}", encoding="utf-8")
        before = {p: p.read_bytes() for p in self.repo.rglob("*") if p.is_file()}
        self.assertEqual(0, self.run_mode("dry-run"))
        self.assertEqual(before, {p: p.read_bytes() for p in self.repo.rglob("*") if p.is_file()})
        self.assertEqual(1, self.run_mode("audit"))
        self.assertEqual(0, self.run_mode("generate"))
        self.assertFalse(stale.exists())
        self.assertFalse(old_line.exists())
        self.assertFalse(old_entries.exists())
        self.assertTrue(foreign.exists())
        self.assertTrue(foreign_same_dir.exists())
        foreign_same_dir.unlink()
        self.assertEqual(0, self.run_mode("audit"))

    def test_audit_rejects_index_duplicate_missing_and_dangling(self):
        path = self.base / "index.json"
        for mutation in ("duplicate", "missing", "dangling"):
            index = copy.deepcopy(self.index)
            entries = index["questLines"][0]["entries"]
            if mutation == "duplicate":
                entries[1] = copy.deepcopy(entries[0])
            elif mutation == "missing":
                entries.pop()
            else:
                entries[0]["questFile"] = "assets/gtsr/bqquests/Quests/Absent.json"
            path.write_text(json.dumps(index), encoding="utf-8")
            self.assertEqual(1, self.run_mode("audit"), mutation)

    def test_without_nbt_generator_compatibility(self):
        shared = adapter.load_module("test_shared", adapter.EDITOR_ROOT / "tools/generate_bq_assets.py")
        expected = [shared.build_quest_json(q, adapter.LINE_HIGH) for q in self.quests[:66]]
        codec = adapter.load_module("test_codec", adapter.EDITOR_ROOT / "app/nbt.py")
        adapter.install_adapter(shared, codec)
        self.assertEqual(expected, [shared.build_quest_json(q, adapter.LINE_HIGH) for q in self.quests[:66]])
        self.assertEqual(4, len(shared.stack_nbt(*shared.parse_stack("minecraft:stone:2*3"))))
        with self.assertRaises(codec.NbtError):
            shared.parse_stack({"item": "minecraft:stone", "nbt": {"bad:99": 0}})

    def test_resolved_path_cannot_escape_owned_root(self):
        with self.assertRaises(ValueError):
            adapter.within(self.repo / "../outside.json", self.repo)
        target = self.repo.parent / (self.repo.name + "-external")
        target.mkdir()
        self.addCleanup(target.rmdir)
        link = self.repo / "outside-link"
        try:
            link.symlink_to(target, target_is_directory=True)
        except OSError:
            return  # Windows can require privileges for symlinks.
        with self.assertRaises(ValueError):
            adapter.within(link / "asset.json", self.repo)
        link.unlink()


if __name__ == "__main__":
    unittest.main()
