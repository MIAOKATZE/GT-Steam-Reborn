"""Offline launch gates: no Java, compilation, instance or desktop is started."""
from contextlib import ExitStack, redirect_stderr
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT.parent / 'tools/testlab'))
sys.path.insert(0, str(ROOT / 'tools/ore-center'))
import host


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


client = load('gtsr_owned_client_runner', ROOT / 'tools/testlab/client_qa/run.py')
ore = load('gtsr_ore_client_server', ROOT / 'tools/ore-center/client_server.py')
recovery = load('gtsr_recovery_driver', ROOT / 'tools/testlab/recovery_restart_driver.py')


class RuntimeBudgetTests(unittest.TestCase):
    def test_combined_budget_accepts_nine_gib_and_rejects_one_byte_less(self):
        with patch.object(host, 'available_memory', return_value=9 * 1024**3):
            budget = ore.memory_budget()
        self.assertEqual(budget['requiredBytes'], 9 * 1024**3)
        self.assertEqual(budget['availablePhysicalBytes'], budget['requiredBytes'])
        self.assertEqual(budget['reserveGiB'], 1)
        with patch.object(host, 'available_memory', return_value=9 * 1024**3 - 1):
            with self.assertRaises(RuntimeError):
                ore.memory_budget()

    def test_client_checks_five_gib_before_acquiring_native_lease(self):
        for available in (5 * 1024**3 - 1, 5 * 1024**3):
            with self.subTest(available=available), tempfile.TemporaryDirectory() as temp, ExitStack() as stack:
                root = Path(temp).resolve()
                workspace = root / 'build/client-qa'
                workspace.mkdir(parents=True)
                fixture = root / 'fixture'
                (fixture / '.testlab-host').mkdir(parents=True)
                (fixture / '.testlab-host/lease.json').write_text(json.dumps({'runId': 'host-run'}))
                helper = workspace / 'gtsr-mui2-qa-owned-only.jar'
                helper.write_bytes(b'owned helper')
                (workspace / 'client-launch.json').write_text(json.dumps({'command': ['java.exe']}))
                (workspace / 'qa-compile-receipt.json').write_text(json.dumps({
                    'jarSHA256': hashlib.sha256(helper.read_bytes()).hexdigest()}))
                jar = root / 'candidate.jar'
                jar.write_bytes(b'candidate')
                args = ['run.py', '--workspace', str(workspace), '--client-sandbox', str(fixture),
                        '--owner-host-run', 'host-run', '--jar', str(jar), '--run', 'budget']
                stack.enter_context(patch.object(client, 'ROOT', root))
                stack.enter_context(patch.object(client, 'LEASE', root / 'native.lease'))
                stack.enter_context(patch.object(client, 'OUT', workspace))
                stack.enter_context(patch.object(client, 'FIXTURE', fixture))
                stack.enter_context(patch.object(sys, 'argv', args))
                stack.enter_context(patch.object(host, 'find_registered', return_value={}))
                java = stack.enter_context(patch.object(host, 'require_java26'))
                stack.enter_context(patch.object(host, 'available_memory', return_value=available))
                acquire = stack.enter_context(patch.object(client.os, 'open', side_effect=RuntimeError('lease boundary')))
                with self.assertRaises(RuntimeError) as error:
                    client.main()
                self.assertEqual(str(error.exception) == 'lease boundary', available == 5 * 1024**3)
                java.assert_called_once_with('java.exe')
                self.assertEqual(acquire.call_count, int(available == 5 * 1024**3))

    def test_incompatible_heap_reserve_and_bypass_options_are_rejected(self):
        cases = [
            (client.main, ['run.py', '--jar', 'candidate.jar', '--run', 'options'],
             [('--heap-gib', '3'), ('--heap-gib', '5'), ('--reserve-gib', '2'), ('--allow-low-memory',)]),
            (ore.main, ['client_server.py', '--bundle', 'bundle.json', '--evidence', 'evidence'],
             [('--heap-gib', '5')]),
        ]
        for main, args, options in cases:
            for option in options:
                with self.subTest(main=main.__module__, option=option), patch.object(sys, 'argv', args + list(option)), redirect_stderr(io.StringIO()):
                    with self.assertRaises(SystemExit) as error:
                        main()
                    self.assertEqual(error.exception.code, 2)
        with redirect_stderr(io.StringIO()), self.assertRaises(SystemExit) as error:
            recovery.main(['--bundle', 'bundle.json', '--case', 'heavy', '--heap-mib', '3072'])
        self.assertEqual(error.exception.code, 2)


if __name__ == '__main__':
    unittest.main()
