"""Freeze independently owned UI QA fragments and their exact hashes into one plan."""
from pathlib import Path
import argparse
import hashlib
import json


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--include-group', action='append', default=[], help='Keep only these exact named groups; caller must verify variable dependencies')
    parser.add_argument('fragments', nargs='+', type=Path)
    args = parser.parse_args()
    if args.output.exists() or args.output.with_suffix('.manifest.json').exists():
        raise FileExistsError('Use a new immutable plan output name')
    steps, sources, groups = [], [], set()
    requested = set(args.include_group)
    for path in args.fragments:
        raw = path.read_bytes()
        fragment = json.loads(raw.decode('utf-8-sig'))
        if not isinstance(fragment, list) or not fragment or fragment[0].get('op') != 'group':
            raise ValueError(f'Fragment must start with a group: {path}')
        for step in fragment:
            if not isinstance(step, dict) or not isinstance(step.get('op'), str):
                raise ValueError(f'Invalid DSL step: {path}')
            if step['op'] == 'group':
                if step['name'] in groups:
                    raise ValueError(f'Duplicate group: {step["name"]}')
                groups.add(step['name'])
        selected, keep, names = [], False, []
        for step in fragment:
            if step['op'] == 'group':
                keep = not requested or step['name'] in requested
                if keep:
                    names.append(step['name'])
            if keep:
                selected.append(step)
        sources.append(dict(path=str(path.resolve()), sha256=hashlib.sha256(raw).hexdigest(),
                            start=len(steps), steps=len(selected), originalSteps=len(fragment), selectedGroups=names))
        steps.extend(selected)
    if requested - groups:
        raise ValueError(f'Unknown selected groups: {sorted(requested - groups)}')
    selected_groups = [s['name'] for s in steps if s['op'] == 'group']
    args.output.parent.mkdir(parents=True, exist_ok=True)
    payload = (json.dumps(steps, ensure_ascii=False, indent=2) + '\n').encode('utf-8')
    args.output.write_bytes(payload)
    manifest = dict(plan=str(args.output.resolve()), sha256=hashlib.sha256(payload).hexdigest(),
                    steps=len(steps), groups=len(selected_groups), selectedGroups=selected_groups, sources=sources,
                    status='PREPARED_NOT_RUN')
    args.output.with_suffix('.manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(manifest, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
