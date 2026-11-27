"""Run explicit GTSR recovery representatives through two standard leased server JVMs.

No launch occurs without --execute. This delegates every launch, doctor check, temporary
configuration and exact original-JAR restoration to the registered shared TestLab host.
The preparation result must remain BLOCKED/PREPARED; only the second native report can PASS.
"""
from __future__ import annotations

import argparse
import gzip
import hashlib
import io
import json
from pathlib import Path
import struct
import subprocess
import sys
import time
import uuid

ROOT = Path(__file__).resolve().parents[2]
KEYS = ('center', 'heavy', 'mega', 'critical_turbine')
MAX_JOURNAL_BYTES = 64 * 1024 * 1024


def read(path):
    return json.loads(Path(path).read_text(encoding='utf-8-sig'))


def save(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    temp = path.with_suffix(path.suffix + '.tmp')
    temp.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    temp.replace(path)


def journal_header(path, full=False, projected=False):
    """Read bounded real raw/gzip NBT, projecting top-level scalars unless full is requested.

    The implementation deliberately needs no Minecraft/NBT package on the host. It rejects
    unknown tags, negative lengths, excessive nesting and truncated/corrupt journals.
    """
    with Path(path).open('rb') as source:
        magic = source.read(2)
    opener = gzip.open if magic == b'\x1f\x8b' else open if magic[:1] == b'\x0a' else None
    if opener is None:
        raise ValueError('Expected native raw or gzip NBT framing')
    with opener(path, 'rb') as stream:
        raw = stream.read(MAX_JOURNAL_BYTES + 1)
    if len(raw) > MAX_JOURNAL_BYTES:
        raise ValueError('Recovery journal exceeds 64 MiB')
    stream = io.BytesIO(raw)

    def take(length):
        if length < 0 or length > len(raw):
            raise ValueError('Invalid NBT length')
        value = stream.read(length)
        if len(value) != length:
            raise ValueError('Truncated recovery NBT')
        return value

    def number(fmt):
        return struct.unpack('>' + fmt, take(struct.calcsize(fmt)))[0]

    def string():
        return take(number('H')).decode('utf-8', errors='strict')

    def payload(tag, depth=0, keep=True, path=''):
        if depth > 64:
            raise ValueError('NBT nesting exceeds bound')
        if tag in (1, 2, 3, 4, 5, 6):
            return number({1: 'b', 2: 'h', 3: 'i', 4: 'q', 5: 'f', 6: 'd'}[tag])
        if tag == 8:
            return string()
        if tag in (7, 11, 12):
            length = number('i')
            if length < 0:
                raise ValueError('Negative NBT array length')
            size = length * {7: 1, 11: 4, 12: 8}[tag]
            if stream.tell() + size > len(raw):
                raise ValueError('Truncated recovery NBT array')
            stream.seek(size, 1)
            return None
        if tag == 9:
            child, length = number('B'), number('i')
            if length < 0 or length > len(raw) or child == 0 and length:
                raise ValueError('Invalid NBT list')
            children = []
            for _ in range(length):
                value = payload(child, depth + 1, keep, path)
                if full and keep:
                    children.append(value)
            return children if full and keep else None
        if tag == 10:
            scalars = {}
            while True:
                child = number('B')
                if child == 0:
                    return scalars if depth == 0 or full and keep else None
                name = string()
                child_keep = keep and (not projected or path != 'actualSavedTile' or name == 'mActive')
                child_path = (path + '.' + name if path else name) if child_keep else ''
                value = payload(child, depth + 1, child_keep, child_path)
                if full and keep and child_keep or depth == 0 and child in (1, 2, 3, 4, 5, 6, 8):
                    scalars[name] = value
        raise ValueError('Unknown NBT tag')

    if number('B') != 10:
        raise ValueError('Expected compound recovery journal')
    string()
    header = payload(10)
    if stream.read(1):
        raise ValueError('Trailing recovery journal data')
    return header


def legacy_tickets(path, header):
    if not path.is_file():
        return 0
    root = journal_header(path, full=True)
    def count(value):
        if isinstance(value, list):
            return sum(count(child) for child in value)
        if not isinstance(value, dict):
            return 0
        own = (value.get('OwnerType') == 'CriticalLoom'
               and all(value.get('Owner' + axis) == header.get('controller' + axis) for axis in 'XYZ'))
        return int(own) + sum(count(child) for child in value.values())
    return count(root)


def verify_saved_checkpoint(path, case):
    # The real construction schedule remains in the immutable raw journal/native world.
    # Admission needs the complete paid state, saved port stocks and actual native active flag only.
    journal = journal_header(path, full=True, projected=True)
    if journal.get('stage') != 'SAVED' or journal.get('case') != case or journal.get('allowed') != 1:
        raise ValueError('Final native save did not retain this enabled prepared machine')
    state = journal.get('state', {})
    proof = dict(case=case, savedWorldTick=journal.get('savedWorldTick'), savedState=state,
                 savedPortStocks=journal.get('ports', []),
                 preparedExternalSink=dict(fluid=journal.get('preparedSinkFluid', 0), eu=journal.get('preparedSinkEu', 0)))
    if case == 'center':
        duration, elapsed = state.get('CenterDuration', 0), state.get('CenterElapsed', 0)
        pending = state.get('CenterItemOutputs', [])
        expected = journal.get('expected', 0)
        product = journal.get('product', {})
        if product.get('id') is None:
            raise ValueError('Saved center checkpoint has no principal product identity')
        quantity = sum(item.get('CenterQuantity', item.get('Count', 0)) for item in pending
                       if item.get('id') == product['id'] and item.get('Damage', 0) == product.get('Damage', 0))
        if duration <= 0 or not 0 < elapsed <= duration or expected <= 0 or quantity != expected:
            raise ValueError('Final center save has no exact outstanding paid batch; completed/delivered state cannot PASS')
        if state.get('CenterSequence') != journal.get('sequence'):
            raise ValueError('Final center save selected a different paid sequence')
        proof.update(paidCheckpoint=True, progress=elapsed, duration=duration, outstandingPaidItems=quantity,
                     principalProduct=product, rolledPendingItemKinds=len(pending),
                     paidStage='AWAITING_DELIVERY' if elapsed == duration else 'PROCESSING')
    elif case == 'critical_turbine':
        paid = state.get('criticalPaidJournal', {}).get('criticalEnergyRuntime', {})
        if paid.get('funded') != 1 or not 0 < paid.get('progress', 0) <= 400:
            raise ValueError('Final critical save has no outstanding funded batch; completed state cannot PASS')
        if (journal.get('actualSavedTile', {}).get('mActive') != 1 or state.get('mMaxProgresstime') != 400
                or state.get('mProgresstime') != paid['progress']):
            raise ValueError('Final critical save did not retain the actual active paid-cycle mirror')
        proof.update(paidCheckpoint=True, progress=paid['progress'], duration=400)
    else:
        efficiency = state.get('turbineEfficiency' if case == 'heavy' else 'mEfficiency', 0)
        if efficiency <= 0 or journal.get('actualSavedTile', {}).get('mActive') != 1:
            raise ValueError('Final turbine save was not a genuinely working warm machine')
        proof.update(workingWarmCheckpoint=True, efficiency=efficiency)
    return proof


def verify_host_cleanup(receipt):
    cleanup = receipt.get('cleanup', {})
    if receipt.get('exitCode') != 0 or cleanup.get('forcedStop'):
        raise ValueError('JVM did not exit normally; preserve recovery evidence')
    for name in ('processExited', 'jarsRestored', 'configRestored'):
        if cleanup.get(name) is not True:
            raise ValueError('Shared host did not verify ' + name)
    if cleanup.get('world') != 'PRESERVED':
        raise ValueError('Shared host did not preserve the fixed world')


def verify_native(receipt, case, expected):
    report = receipt.get('nativeReport')
    if not report or receipt.get('case') != case:
        raise ValueError('Missing/mismatched correlated native report; hostStatus=' + str(receipt.get('status'))
                         + '; hostError=' + str(receipt.get('primaryError')))
    target = Path(report['path'])
    raw = target.read_bytes()
    if hashlib.sha256(raw).hexdigest() != report['sha256']:
        raise ValueError('Native report changed after host verification')
    native = json.loads(raw.decode('utf-8-sig'))
    if native.get('runId') != report['runId'] or native.get('requestId') != receipt.get('runId'):
        raise ValueError('Native request/run identity mismatch')
    if native.get('requested') != case or native.get('plannedTasks') != [case]:
        raise ValueError('Native report selected a different case')
    entries = native.get('results', [])
    if len(entries) != 1 or entries[0].get('id') != case or entries[0].get('status') != expected:
        detail = '; '.join(str(item.get('id')) + '=' + str(item.get('status')) + ': ' + item.get('detail', '') for item in entries)
        raise ValueError('Native case did not complete with expected status: ' + detail[:4000])
    if receipt.get('status') != expected or report.get('status') != expected:
        raise ValueError('Host/native completion disagreement')
    entry = entries[0]
    if expected == 'BLOCKED':
        if not entry.get('detail', '').startswith('PREPARED_NOT_PASS:'):
            raise ValueError('BLOCKED is not the explicit prepared checkpoint')
        if entry.get('details', {}).get('prepared') is not True:
            raise ValueError('Preparation checkpoint missing')
    else:
        checks = entry.get('details', {})
        for name in ('differentJvm', 'newTileReadFromWorld', 'nativeStructureRevalidated',
                     'realOutputAfterRecovery', 'actualBrokenWorldBlockRejected', 'ownedFixtureCleanup',
                     'actualSavedPortStocksReadBack'):
            if checks.get(name) is not True:
                raise ValueError('Native resume proof missing ' + name)
        if checks.get('recoveryProofMode') == 'NATIVE_LOAD_BOUNDARY':
            if case != 'recovery.resume.heavy' or checks.get('nativeLoadBoundaryVerified') is not True:
                raise ValueError('Native load boundary proof is restricted to the small heavy turbine')
            if checks.get('realStructureChunkWithheldTicks') != 0:
                raise ValueError('Load boundary proof cannot claim withheld-chunk ticks')
            if checks.get('nativeInitialStartup', -1) < 0 or checks.get('savedRotorEfficiency', 0) <= 0:
                raise ValueError('No unreviewed warm native load boundary')
            if checks.get('firstNativeRecheckRotorEfficiency', -1) < checks['savedRotorEfficiency']:
                raise ValueError('First natural native recheck lost saved rotor efficiency')
        else:
            if checks.get('recoveryProofMode') != 'DEFERRED_NATIVE_CHUNK' or checks.get('externalTicketDidNotPreloadDelayedChunk') is not True:
                raise ValueError('No actual deferred native structure chunk proof')
            if checks.get('recoveryFrozenTicks', 0) < 60:
                raise ValueError('No 60 naturally observed recovery ticks')
            if checks.get('realStructureChunkWithheldTicks', 0) < 60:
                raise ValueError('No real required structure chunk was withheld for 60 natural ticks')
        if case == 'recovery.resume.critical_turbine':
            if min(checks.get('criticalPaidJournalExactFrozenTicks', 0),
                   checks.get('criticalPortStocksExactFrozenTicks', 0)) < 60:
                raise ValueError('Critical authority and both port stocks were not frozen for 60 ticks')
            if (checks.get('criticalNativeMirrorBoundary') != 'SAVED_EQUALS_NEW_TILE; NATURAL_TICK_PAUSES_DISPLAY_ONLY'
                    or checks.get('criticalPausedActive') is not False
                    or not checks.get('criticalLoadedNativeMirror') or not checks.get('criticalPausedNativeMirror')
                    or checks.get('criticalPausedNativeTimer', -1) <= checks.get('criticalLoadedNativeTimer', 0)):
                raise ValueError('Critical natural paused-display boundary proof missing')
    verify_host_cleanup(receipt)
    return native, entry


def invoke(command, target, allowed=(0,)):
    process = subprocess.run(command, cwd=ROOT.parent, capture_output=True, text=True, encoding='utf-8', errors='replace')
    target.with_suffix('.stdout.txt').write_text(process.stdout, encoding='utf-8')
    target.with_suffix('.stderr.txt').write_text(process.stderr, encoding='utf-8')
    if process.returncode not in allowed:
        raise RuntimeError('Shared host rejected operation; inspect ' + str(target.with_suffix('.stderr.txt')))
    value = json.loads(process.stdout)
    save(target, value)
    return value


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--host', type=Path, default=ROOT.parent / 'tools/testlab/host.py')
    parser.add_argument('--registry', type=Path, default=ROOT.parent / 'plan/testlab-instances.json')
    parser.add_argument('--bundle', type=Path, required=True)
    parser.add_argument('--case', choices=KEYS, required=True)
    parser.add_argument('--phase', choices=('both', 'prepare', 'resume'), default='both')
    parser.add_argument('--heap-mib', type=int, default=4096)
    parser.add_argument('--timeout', type=int, default=1800)
    parser.add_argument('--evidence', type=Path)
    parser.add_argument('--execute', action='store_true')
    args = parser.parse_args(argv)
    evidence = (args.evidence or ROOT / '.artifacts/recovery-restart' / (time.strftime('%Y%m%d-%H%M%S') + '-' + uuid.uuid4().hex[:8])).absolute()
    common = [sys.executable, '-B', str(args.host.absolute()), '--mod', 'gtsr', '--lane', 'server',
              '--registry', str(args.registry.absolute()), '--bundle', str(args.bundle.absolute()),
              '--heap-mib', str(args.heap_mib), '--timeout', str(args.timeout), '--evidence', str(evidence / 'host')]

    def command(action, phase=None, execute=False):
        cmd = [*common[:3], action, *common[3:]]
        if phase:
            cmd += ['--task', 'recovery.' + phase + '.' + args.case, '--prepare']
        if execute:
            cmd += ['--execute']
        return cmd

    phases = ('prepare', 'resume') if args.phase == 'both' else (args.phase,)
    plan = {'status': 'PLANNED', 'case': args.case, 'evidence': str(evidence),
            'commands': [command('catalog')] + [cmd for phase in phases for cmd in (command('doctor', phase), command('run', phase, True))],
            'scope': 'Heavy: actual new-JVM native load boundary and first recheck; center/mega/critical: actual 60-tick deferred chunk. Singularity/TCDS and other machines NOT_RUN',
            'preparedIsPass': False, 'sameWorldRequired': True}
    if not args.execute:
        print(json.dumps(plan, ensure_ascii=False, indent=2))
        return 0
    evidence.mkdir(parents=True, exist_ok=False)
    save(evidence / 'plan.json', plan)
    result = dict(plan, status='RUNNING', phases=[])
    previous_world = None
    try:
        invoke(command('catalog'), evidence / 'catalog.json')
        for phase in phases:
            doctor = invoke(command('doctor', phase), evidence / (phase + '-doctor.json'))
            if doctor.get('status') != 'PLANNED' or doctor.get('blockers'):
                raise ValueError('Doctor did not authorize the exact registered instance')
            world = Path(doctor['world']).resolve()
            if previous_world is not None and world != previous_world:
                raise ValueError('Resume selected a different world')
            previous_world = world
            root = Path(doctor['instance'])
            journal = world / 'gtsr-testlab' / ('recovery-' + args.case + '.dat')
            if phase == 'prepare' and journal.exists():
                raise ValueError('Existing recovery journal retained; use --phase resume after reviewing it')
            if phase == 'resume':
                header = journal_header(journal)
                if header.get('stage') != 'SAVED' or header.get('case') != args.case:
                    raise ValueError('No actual final save observation for this case')
                save(evidence / 'saved-journal-header.json', header)
                save(evidence / 'saved-checkpoint-proof.json', verify_saved_checkpoint(journal, args.case))
                (evidence / 'saved-journal.dat').write_bytes(journal.read_bytes())
                if args.case == 'critical_turbine':
                    ticket_file = world / ('DIM' + str(header['dimension'])) / 'forcedchunks.dat'
                    if legacy_tickets(ticket_file, header) != 1:
                        raise ValueError('No exact obsolete CriticalLoom ticket in native saved forcedchunks.dat')
                    (evidence / 'saved-forcedchunks.dat').write_bytes(ticket_file.read_bytes())
            receipt = invoke(command('run', phase, True), evidence / (phase + '-host.json'), allowed=(0, 2))
            native, entry = verify_native(receipt, 'recovery.' + phase + '.' + args.case,
                                          'BLOCKED' if phase == 'prepare' else 'PASS')
            save(evidence / (phase + '-native.json'), native)
            if (root / '.testlab-host/lease.json').exists():
                raise ValueError('Host lease remains; a second JVM must not start')
            if phase == 'prepare':
                header = journal_header(journal)
                if header.get('stage') != 'SAVED' or header.get('case') != args.case:
                    log = root / 'logs/fml-server-latest.log'
                    if log.is_file():
                        lines = log.read_text(encoding='utf-8', errors='replace').splitlines()
                        save_errors = []
                        for index, line in enumerate(lines):
                            if 'Exception caught during firing event net.minecraftforge.event.world.WorldEvent$Save' in line:
                                save_errors.extend(lines[index:index + 30])
                        if save_errors:
                            (evidence / 'native-world-save-error.txt').write_text('\n'.join(save_errors), encoding='utf-8')
                            raise ValueError('Native WorldEvent.Save failed: ' + (save_errors[1] if len(save_errors) > 1 else save_errors[0]))
                    raise ValueError('Normal stop left journal stage=' + str(header.get('stage')) + '; final save observation missing')
                save(evidence / 'prepared-journal-header.json', header)
                save(evidence / 'prepared-checkpoint-proof.json', verify_saved_checkpoint(journal, args.case))
                if args.case == 'critical_turbine':
                    ticket_file = world / ('DIM' + str(native['dimension'])) / 'forcedchunks.dat'
                    if legacy_tickets(ticket_file, header) != 1:
                        raise ValueError('Obsolete ticket was not actually serialized by Forge')
            elif journal.exists():
                raise ValueError('Resume retained an active journal after claiming cleanup')
            result['phases'].append({'phase': phase, 'nativeRunId': native['runId'],
                                     'gtVersion': native.get('gtVersion'), 'status': 'PREPARED_NOT_PASS' if phase == 'prepare' else 'PASS'})
        result['status'] = 'PREPARED_NOT_PASS' if args.phase == 'prepare' else 'PASS'
    except (OSError, ValueError, RuntimeError, KeyError) as error:
        result.update(status='BLOCKED', error=str(error), recovery='PRESERVED; inspect native evidence before retry/cleanup')
    save(evidence / 'result.json', result)
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0 if result['status'] == 'PASS' else 2


if __name__ == '__main__':
    raise SystemExit(main())
