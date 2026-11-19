"""Prepare the owned-client center Tooltip and NEI structure probe."""

import argparse
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT.parent / 'tools/testlab'))
import host


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--bundle', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--render-structure', action='store_true', help='Also compare native and cached BlockRenderer frames')
    args = parser.parse_args()
    artifacts = {a['modId']: a['sha256'] for a in host.bundle_artifacts(args.bundle, 'gtsr')}
    if set(artifacts) != {'gtsr', 'miaokatzecore'}:
        raise ValueError('Expected the matching GTSR and Core testlab artifacts')
    rows = []
    for name, target, tier, shift, cycle in (
        ('center-common', 'OreIntegrationCenter', 'COMMON', False, False),
        ('center-quick', 'OreIntegrationCenter', 'QUICK', False, True),
        ('center-structure', 'OreIntegrationCenter', 'QUICK', True, False),
        ('center-common-restored', 'OreIntegrationCenter', 'COMMON', False, True),
        ('speed-ii', 'SpeedPackII', 'COMMON', False, False),
        ('shell-iii', 'ShellIII', 'COMMON', False, False),
    ):
        row = dict(id=name, target=target, tier=tier, shift=shift, context='inventory', unicode=False, scale=2)
        if cycle:
            row['action'] = 'tier'
        rows.append(row)
    plan = dict(scriptOnly='gtsr-center-tooltip-v1', language='zh_CN', expectedArtifacts=artifacts, cases=rows)
    if args.render_structure:
        plan['renderStructure'] = True
    args.output.write_text(json.dumps(plan, ensure_ascii=False, indent=2) + '\n', encoding='utf8')


if __name__ == '__main__':
    main()
