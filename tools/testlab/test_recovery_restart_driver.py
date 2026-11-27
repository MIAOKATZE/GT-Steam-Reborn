"""Offline gates for the two-JVM driver; these never start Minecraft."""
import gzip
import hashlib
import json
from pathlib import Path
import struct
import tempfile
import unittest
from unittest.mock import patch

import recovery_restart_driver as driver


def nbt_string(value):
    raw = value.encode('utf-8')
    return struct.pack('>H', len(raw)) + raw


class RestartDriverTests(unittest.TestCase):
    def test_journal_header_reads_real_nbt_and_skips_nested_paid_payload(self):
        raw = b'\x0a' + nbt_string('')
        for name, value in [('case', 'heavy'), ('jvm', 'first-jvm'), ('stage', 'SAVED')]:
            raw += b'\x08' + nbt_string(name) + nbt_string(value)
        raw += b'\x0a' + nbt_string('actualSavedTile')
        raw += b'\x09' + nbt_string('pendingItems') + b'\x0a' + struct.pack('>i', 1)
        raw += b'\x03' + nbt_string('Count') + struct.pack('>i', 8) + b'\x00\x00\x00'
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / 'journal.dat'
            path.write_bytes(gzip.compress(raw))
            self.assertEqual(driver.journal_header(path), {'case': 'heavy', 'jvm': 'first-jvm', 'stage': 'SAVED'})
            path.write_bytes(gzip.compress(raw[:-1]))
            with self.assertRaisesRegex(ValueError, 'Truncated'):
                driver.journal_header(path)

    def test_read_budget_accepts_exactly_64_mib_and_rejects_over_budget_or_truncation(self):
        prefix = b'\x0a' + nbt_string('') + b'\x07' + nbt_string('blob')
        payload_size = driver.MAX_JOURNAL_BYTES - len(prefix) - 4 - 1
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / 'bounded.dat'
            raw = prefix + struct.pack('>i', payload_size) + bytes(payload_size) + b'\x00'
            self.assertEqual(len(raw), 64 * 1024 * 1024)
            path.write_bytes(gzip.compress(raw, compresslevel=1))
            self.assertEqual(driver.journal_header(path), {})
            path.write_bytes(gzip.compress(raw + b'\x00', compresslevel=1))
            with self.assertRaisesRegex(ValueError, 'exceeds 64 MiB'):
                driver.journal_header(path)
            path.write_bytes(gzip.compress(prefix + struct.pack('>i', 1024) + bytes(1023)))
            with self.assertRaisesRegex(ValueError, 'Truncated'):
                driver.journal_header(path)

    def test_projection_retains_paid_state_and_native_active_without_construction_tree(self):
        raw = b'\x0a' + nbt_string('')
        raw += b'\x0a' + nbt_string('state') + b'\x03' + nbt_string('progress') + struct.pack('>i', 20) + b'\x00'
        raw += b'\x0a' + nbt_string('actualSavedTile') + b'\x01' + nbt_string('mActive') + b'\x01'
        raw += b'\x0a' + nbt_string('criticalState') + b'\x08' + nbt_string('constructionPhysicalJob') + nbt_string('large-tree')
        raw += b'\x00\x00\x00'
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / 'projected.dat'
            path.write_bytes(gzip.compress(raw))
            projected = driver.journal_header(path, full=True, projected=True)
            self.assertEqual(projected['state'], {'progress': 20})
            self.assertEqual(projected['actualSavedTile'], {'mActive': 1})
            self.assertIn('criticalState', driver.journal_header(path, full=True)['actualSavedTile'])

    def receipt(self, temp, expected='BLOCKED'):
        case = 'recovery.prepare.heavy' if expected == 'BLOCKED' else 'recovery.resume.heavy'
        details = {'prepared': True} if expected == 'BLOCKED' else {
            name: True for name in ('differentJvm', 'newTileReadFromWorld', 'nativeStructureRevalidated',
                                   'realOutputAfterRecovery', 'actualBrokenWorldBlockRejected', 'ownedFixtureCleanup',
                                   'actualSavedPortStocksReadBack')}
        if expected == 'PASS':
            details.update(recoveryFrozenTicks=0, realStructureChunkWithheldTicks=0,
                           recoveryProofMode='NATIVE_LOAD_BOUNDARY', nativeLoadBoundaryVerified=True,
                           nativeInitialStartup=600, savedRotorEfficiency=10, firstNativeRecheckRotorEfficiency=10)
        native = dict(runId='business-run', requestId='host-request', requested=case, plannedTasks=[case],
                      results=[dict(id=case, status=expected, details=details,
                                    detail='PREPARED_NOT_PASS: new JVM required' if expected == 'BLOCKED' else 'done')])
        path = Path(temp) / 'native.json'
        raw = json.dumps(native).encode()
        path.write_bytes(raw)
        receipt = dict(case=case, runId='host-request', status=expected, exitCode=0,
                       nativeReport=dict(path=str(path), sha256=hashlib.sha256(raw).hexdigest(), runId='business-run', status=expected),
                       cleanup=dict(processExited=True, jarsRestored=True, configRestored=True, world='PRESERVED', forcedStop=False))
        return case, receipt

    def test_native_ticket_disk_check_matches_all_owner_coordinates(self):
        raw = b'\x0a' + nbt_string('') + b'\x09' + nbt_string('Tickets') + b'\x0a' + struct.pack('>i', 1)
        raw += b'\x08' + nbt_string('OwnerType') + nbt_string('CriticalLoom')
        for axis, value in zip('XYZ', (120, 65, 80)):
            raw += b'\x03' + nbt_string('Owner' + axis) + struct.pack('>i', value)
        raw += b'\x00\x00'
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / 'forcedchunks.dat'
            path.write_bytes(gzip.compress(raw))
            header = dict(controllerX=120, controllerY=65, controllerZ=80)
            self.assertEqual(driver.legacy_tickets(path, header), 1)
            path.write_bytes(raw)  # Forge 1614 forcedchunks.dat is native uncompressed NBT.
            self.assertEqual(driver.legacy_tickets(path, header), 1)
            header['controllerX'] = 121
            self.assertEqual(driver.legacy_tickets(path, header), 0)

    def test_center_paid_save_gate_rejects_already_delivered_batch(self):
        journal = dict(stage='SAVED', case='center', allowed=1, expected=16, sequence=7, product=dict(id=44, Damage=2),
                       state=dict(CenterDuration=256, CenterElapsed=40, CenterSequence=7,
                                  CenterItemOutputs=[dict(id=44, Damage=2, CenterQuantity=16),
                                                     dict(id=44, Damage=3, CenterQuantity=3)]))
        with patch.object(driver, 'journal_header', return_value=journal):
            self.assertTrue(driver.verify_saved_checkpoint('unused', 'center')['paidCheckpoint'])
            journal['state']['CenterItemOutputs'] = []
            with self.assertRaisesRegex(ValueError, 'outstanding paid batch'):
                driver.verify_saved_checkpoint('unused', 'center')

    def test_critical_and_turbine_final_save_require_funded_or_real_active_state(self):
        journal = dict(stage='SAVED', case='critical_turbine', allowed=1, actualSavedTile=dict(mActive=1),
                       state=dict(mMaxProgresstime=400, mProgresstime=30,
                                  criticalPaidJournal=dict(criticalEnergyRuntime=dict(funded=1, progress=30))))
        with patch.object(driver, 'journal_header', return_value=journal):
            self.assertTrue(driver.verify_saved_checkpoint('unused', 'critical_turbine')['paidCheckpoint'])
            journal['state']['criticalPaidJournal']['criticalEnergyRuntime']['funded'] = 0
            with self.assertRaisesRegex(ValueError, 'funded batch'):
                driver.verify_saved_checkpoint('unused', 'critical_turbine')
            journal.update(case='heavy', state=dict(turbineEfficiency=10), actualSavedTile=dict(mActive=1))
            self.assertTrue(driver.verify_saved_checkpoint('unused', 'heavy')['workingWarmCheckpoint'])
            journal['actualSavedTile']['mActive'] = 0
            with self.assertRaisesRegex(ValueError, 'working warm'):
                driver.verify_saved_checkpoint('unused', 'heavy')

    def test_prepared_is_accepted_only_as_explicit_blocked_checkpoint(self):
        with tempfile.TemporaryDirectory() as temp:
            case, receipt = self.receipt(temp)
            driver.verify_native(receipt, case, 'BLOCKED')
            receipt['status'] = 'PASS'
            with self.assertRaisesRegex(ValueError, 'disagreement'):
                driver.verify_native(receipt, case, 'BLOCKED')

    def test_resume_requires_frozen_ticks_and_normal_full_process_exit(self):
        with tempfile.TemporaryDirectory() as temp:
            case, receipt = self.receipt(temp, 'PASS')
            driver.verify_native(receipt, case, 'PASS')
            receipt['cleanup']['forcedStop'] = True
            with self.assertRaisesRegex(ValueError, 'normally'):
                driver.verify_native(receipt, case, 'PASS')

    def test_native_report_identity_and_bytes_cannot_be_substituted(self):
        with tempfile.TemporaryDirectory() as temp:
            case, receipt = self.receipt(temp)
            receipt['runId'] = 'different-request'
            with self.assertRaisesRegex(ValueError, 'identity'):
                driver.verify_native(receipt, case, 'BLOCKED')
            receipt['runId'] = 'host-request'
            Path(receipt['nativeReport']['path']).write_text('{}', encoding='utf-8')
            with self.assertRaisesRegex(ValueError, 'changed'):
                driver.verify_native(receipt, case, 'BLOCKED')

    def test_small_machine_boundary_cannot_claim_delayed_ticks_or_lose_heat(self):
        with tempfile.TemporaryDirectory() as temp:
            case, receipt = self.receipt(temp, 'PASS')
            path = Path(receipt['nativeReport']['path'])
            native = json.loads(path.read_text())
            checks = native['results'][0]['details']
            for field, value, error in [('realStructureChunkWithheldTicks', 60, 'cannot claim'),
                                         ('firstNativeRecheckRotorEfficiency', 9, 'lost saved')]:
                original = checks[field]
                checks[field] = value
                raw = json.dumps(native).encode()
                path.write_bytes(raw)
                receipt['nativeReport']['sha256'] = hashlib.sha256(raw).hexdigest()
                with self.assertRaisesRegex(ValueError, error):
                    driver.verify_native(receipt, case, 'PASS')
                checks[field] = original

    def test_large_machine_cannot_use_small_machine_boundary_proof(self):
        with tempfile.TemporaryDirectory() as temp:
            _, receipt = self.receipt(temp, 'PASS')
            case = 'recovery.resume.mega'
            path = Path(receipt['nativeReport']['path'])
            native = json.loads(path.read_text())
            native.update(requested=case, plannedTasks=[case])
            native['results'][0]['id'] = case
            raw = json.dumps(native).encode()
            path.write_bytes(raw)
            receipt['case'] = case
            receipt['nativeReport']['sha256'] = hashlib.sha256(raw).hexdigest()
            with self.assertRaisesRegex(ValueError, 'restricted'):
                driver.verify_native(receipt, case, 'PASS')

    def test_critical_requires_authority_ports_and_natural_inactive_mirror(self):
        with tempfile.TemporaryDirectory() as temp:
            _, receipt = self.receipt(temp, 'PASS')
            case = 'recovery.resume.critical_turbine'
            path = Path(receipt['nativeReport']['path'])
            native = json.loads(path.read_text())
            native.update(requested=case, plannedTasks=[case])
            native['results'][0]['id'] = case
            checks = native['results'][0]['details']
            checks.update(recoveryProofMode='DEFERRED_NATIVE_CHUNK', recoveryFrozenTicks=80,
                          realStructureChunkWithheldTicks=60, externalTicketDidNotPreloadDelayedChunk=True,
                          criticalPaidJournalExactFrozenTicks=80, criticalPortStocksExactFrozenTicks=80,
                          criticalNativeMirrorBoundary='SAVED_EQUALS_NEW_TILE; NATURAL_TICK_PAUSES_DISPLAY_ONLY',
                          criticalLoadedNativeMirror='20/400/10000', criticalPausedNativeMirror='0/0/0',
                          criticalPausedActive=False, criticalLoadedNativeTimer=0, criticalPausedNativeTimer=80)
            receipt['case'] = case

            def verify():
                raw = json.dumps(native).encode()
                path.write_bytes(raw)
                receipt['nativeReport']['sha256'] = hashlib.sha256(raw).hexdigest()
                return driver.verify_native(receipt, case, 'PASS')

            verify()
            checks['criticalPortStocksExactFrozenTicks'] = 0
            with self.assertRaisesRegex(ValueError, 'both port stocks'):
                verify()
            checks['criticalPortStocksExactFrozenTicks'] = 80
            checks['criticalPausedActive'] = True
            with self.assertRaisesRegex(ValueError, 'paused-display'):
                verify()


if __name__ == '__main__':
    unittest.main()
