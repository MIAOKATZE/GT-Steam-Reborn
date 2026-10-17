"""Summarize frozen raw evidence without changing or upgrading any run status."""
from pathlib import Path
import argparse
import hashlib
import json


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('run', type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    root = args.run.resolve(strict=True)
    if args.output.exists():
        raise FileExistsError('Do not overwrite an earlier evidence summary')
    receipt = json.loads((root / 'receipt.json').read_text(encoding='utf-8'))
    checks = json.loads((root / 'checks.json').read_text(encoding='utf-8'))
    assertions = checks['checks']
    samples = [json.loads(line) for line in (root / 'memory-samples.jsonl').read_text(encoding='utf-8').splitlines()] if (root / 'memory-samples.jsonl').exists() else []
    memory = {}
    for key, aggregate in [('workingSet', max), ('peakWorkingSet', max), ('privateBytes', max), ('availableBytes', min)]:
        values = [s[key] for s in samples if key in s]
        if values:
            memory[('min' if aggregate is min else 'max') + key[0].upper() + key[1:]] = aggregate(values)
    heaps = checks.get('heapSamples', [])
    if heaps:
        memory.update(heapUsedMinBytes=min(s['usedBytes'] for s in heaps), heapUsedMaxBytes=max(s['usedBytes'] for s in heaps),
                      heapCommittedMaxBytes=max(s['committedBytes'] for s in heaps), heapMaxBytes=max(s['maxBytes'] for s in heaps))
    summary = dict(run=str(root), status=receipt['status'], candidateSHA256=receipt['candidateSHA256'],
                   helperSHA256=receipt['helperSHA256'], exitCode=receipt.get('exitCode'), forcedStop=receipt['forcedStop'],
                   elapsedSeconds=receipt.get('elapsedSeconds'), assertions=len(assertions),
                   passed=sum(c['pass'] for c in assertions), failed=sum(not c['pass'] for c in assertions),
                   failedChecks=[c for c in assertions if not c['pass']],
                   failedGroups=[e for e in checks.get('scriptEvents', []) if e.get('status') == 'FAIL'],
                   geometry=[e for e in checks.get('scriptEvents', []) if 'geometry' in e],
                   memory=memory, memoryScope='OS per-second samples and Runtime heap samples; not post-GC live set; no cross-heap performance attribution',
                   actualLanguage=checks.get('actualLanguage'), actualClientRenderDistance=checks.get('actualClientRenderDistance'),
                   actualIntegratedViewDistance=checks.get('actualIntegratedViewDistance'),
                   lifecycleSeed=checks.get('lastTickLifecycleSeed'), lifecycleStopped=checks.get('afterServerStoppedLifecycle'),
                   tooltipActualPaint=checks.get('actualTooltipPostText'), inputType=checks.get('inputType'),
                   screenshots=receipt.get('screenshots', []), rawFiles=[])
    for path in sorted(root.rglob('*')):
        if path.is_file():
            raw = path.read_bytes()
            summary['rawFiles'].append(dict(path=str(path.relative_to(root)), bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest()))
    if 'slotConservation' in checks:
        summary['legacyMetadataCaveat'] = 'slotConservation is an inherited TCDS-pilot-only NOT_RUN label; inspect individual scripted inventory checks and complete snapshots for other production windows. Raw receipt remains unchanged.'
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(summary, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({k: summary[k] for k in ['status', 'assertions', 'passed', 'failed', 'memory']}, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
