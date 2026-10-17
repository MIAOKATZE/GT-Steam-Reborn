"""Read closed native-ticket runtime evidence; never rewrite or upgrade raw receipts."""
from pathlib import Path
import argparse
import hashlib
import json

CRASH_MARKERS = ('Unreported exception thrown!', 'Minecraft ran into a problem!', 'Exception in server tick loop')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('run', type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    root = args.run.resolve(strict=True)
    if args.output.exists():
        raise FileExistsError('Earlier evidence summaries are immutable')
    receipt = json.loads((root / 'receipt.json').read_text(encoding='utf-8'))
    checks = json.loads((root / 'checks.json').read_text(encoding='utf-8'))
    if checks.get('mode') != 'ticket-restart':
        raise ValueError('Not a native-ticket restart receipt')
    assertions = checks['checks']
    findings = [dict(line=i, text=line) for i, line in enumerate((root / 'stdout.log').read_text(encoding='utf-8', errors='replace').splitlines(), 1)
                if any(marker in line for marker in CRASH_MARKERS)]
    samples = [json.loads(line) for line in (root / 'memory-samples.jsonl').read_text(encoding='utf-8').splitlines()]
    events = checks['ticketEvents']
    memory = {}
    for key, aggregate in [('workingSet', max), ('peakWorkingSet', max), ('privateBytes', max), ('availableBytes', min)]:
        values = [sample[key] for sample in samples if key in sample]
        if values:
            memory[('min' if aggregate is min else 'max') + key[0].upper() + key[1:]] = aggregate(values)
    heaps = checks.get('heapSamples', [])
    if heaps:
        memory['heapUsedMaxBytes'] = max(sample['usedBytes'] for sample in heaps)
        memory['heapMaxBytes'] = max(sample['maxBytes'] for sample in heaps)
    passed = receipt['status'] == 'PASS' and checks['status'] == 'PASS' and not findings and all(item['pass'] for item in assertions)
    result = dict(run=str(root), status='PASS' if passed else 'FAIL', rawRunnerStatus=receipt['status'],
                  rawChecksStatus=checks['status'], assertions=len(assertions), passed=sum(item['pass'] for item in assertions),
                  failed=sum(not item['pass'] for item in assertions), runtimeCrashFindings=findings,
                  candidateSHA256=receipt['candidateSHA256'], helperSHA256=receipt['helperSHA256'],
                  jvmIdentity=checks['jvmIdentity'], inputType=checks['inputType'], fixtureScope=checks['fixtureScope'],
                  exitCode=receipt['exitCode'], forcedStop=receipt['forcedStop'], elapsedSeconds=receipt['elapsedSeconds'],
                  memory=memory, memoryScope='Per-second OS and periodic Runtime samples, not post-GC live heap or comparative performance',
                  productionCodeSources={key: value for key, value in checks.items() if key.endswith('CodeSource')},
                  failedChecks=[item for item in assertions if not item['pass']],
                  lifecycleEvents=[event for event in events if event['event'] in
                                   ['pre-stop-active-ticket-proof', 'FMLServerStopping', 'FMLServerStopped', 'after-stop-native-persisted-ticket-file']],
                  worldUnloadOrder=[event for event in events if event['event'].startswith('WorldUnload') and event.get('dimension') in (0, 1048577)],
                  nativePersistedLoad=[event for event in events if event['event'] == 'WorldLoad-LOWEST-after-native-load' and event.get('dimension') in (0, 1048577)],
                  nativeQuitInHelperSource='quitToMainMenu' in (root / 'TicketRestartQa.java').read_text(encoding='utf-8'),
                  boundaries=['Synthetic in-process Minecraft integrated launch and world quit, not physical keyboard or mouse; inspect nativeQuitInHelperSource for complete menu return chain',
                              'Real registered minimal BaseMetaTile hosts, not a formed 4480-block Critical loom',
                              'Same-save native forcedchunks.dat path and callback registration observed; full formed loom owner restoration NOT_RUN',
                              'Production holders cleared; QA deliberately retains observed object identities until helper JVM exits',
                              'Legacy UI suite, remote dedicated TCP client, long stress and cross-heap performance comparison NOT_RUN'], rawFiles=[])
    if findings and receipt['status'] == 'PASS':
        result['rawStatusCaveat'] = 'Historical runner lacked crash-log gating: automatic lifecycle assertions passed, but recovered client crashes make overall runtime FAIL. Raw bytes unchanged.'
    for path in sorted(root.rglob('*')):
        if path.is_file():
            raw = path.read_bytes()
            result['rawFiles'].append(dict(path=str(path.relative_to(root)), bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest()))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({key: result[key] for key in ['status', 'rawRunnerStatus', 'assertions', 'passed', 'failed', 'runtimeCrashFindings', 'memory']}, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
