"""Compare the task's clean baseline with all three repositories before release."""
import hashlib
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BASELINE = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / 'plan/implementation/20261002192403/scope-baseline.json'
baseline = json.loads(BASELINE.read_text(encoding='utf-8'))
results = []
for index, repo in enumerate(baseline['repos']):
    root = Path(repo['path'])
    assert not repo['status'] and not repo['untracked'], 'Preexisting modifications require individual attribution'
    changes = []
    for name, digest in repo['hashes'].items():
        path = root / name
        if not path.exists() or hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            changes.append(name)
    untracked = subprocess.check_output(['git', 'ls-files', '--others', '--exclude-standard', '-z'], cwd=root).decode('utf-8').split('\0')
    changes += [name for name in untracked if name]
    if index == 1:
        ignored = subprocess.check_output(['git', 'ls-files', '--others', '-z', '--',
            'prosperity/', 'implementation/20261002192403/'], cwd=root).decode('utf-8').split('\0')
        changes = list(dict.fromkeys(changes + [name for name in ignored if name]))
    def allowed(name):
        if index == 0:
            return name == 'gradle.properties' or name.startswith(('src/', 'tools/'))
        if index == 1:
            return name.startswith(('prosperity/', 'implementation/20261002192403/')) or (
                name.startswith('workflow/v1.20.65_') and name.endswith('.md')) or (
                name.startswith('sum/101_') and name.endswith('.md'))
        return name in ['INDEX.md', '_meta/SOURCE_MAP.md', '_meta/CHANGELOG.md',
                       'mods/gtsr/dimension/prosperity-remaster-prefabs.md']
    unexpected = [name for name in changes if not allowed(name)]
    assert not unexpected, (root, unexpected)
    assert subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root).decode().strip() == repo['head'], 'Baseline HEAD changed before audit'
    results.append({'repository': str(root), 'baselineHead': repo['head'],
                    'trackedAndVisibleNewChanges': len(changes), 'unexpected': unexpected,
                    'paths': changes})
report = {'result': 'passed', 'preexistingWorkingTreeChanges': 0, 'repositories': results,
          'ignoredTaskEvidence': 'implementation/20261002192403',
          'limitation': 'Does not assert Minecraft gameplay or user acceptance'}
output = BASELINE.parent / 'scope-final.json'
output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps({'result': 'passed', 'repositoryChangeCounts': [r['trackedAndVisibleNewChanges'] for r in results],
                  'unexpected': 0, 'report': str(output)}, ensure_ascii=False))
