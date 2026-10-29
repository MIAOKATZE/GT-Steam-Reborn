"""GT205 critical ME input contracts; production classes come exclusively from the supplied dev jar."""
import argparse
import json
import os
from pathlib import Path

import verify_v33 as common


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    root, cache = common.ROOT, common.CACHE
    jar, out = args.jar.resolve(), args.out.resolve()
    out.relative_to(root / 'build')
    classes, runtime, empty = [out / name for name in ('fixture-classes', 'runtime', 'empty')]
    for directory in (classes, runtime, empty):
        directory.mkdir(parents=True, exist_ok=True)
    if any((classes / 'com/miaokatze/gtsr').rglob('*.class')):
        raise RuntimeError('Production overlay detected; choose a fresh --out')
    (classes / 'mcpmod.info').write_text('[{"modid":"mcp","name":"MCP","version":"9.05"}]')
    gt = next((cache / 'com.github.GTNewHorizons/GT5-Unofficial/5.09.54.205').glob('*/*-dev.jar'))
    ae = next((cache / 'com.github.GTNewHorizons/Applied-Energistics-2-Unofficial/rv3-beta-1080-GTNH').glob('*/*-dev.jar'))
    dependencies = sorted(p for p in cache.glob('*/*/*/*/*.jar')
                          if '/GT5-Unofficial/' not in p.as_posix()
                          and '/Applied-Energistics-2-Unofficial/' not in p.as_posix()
                          and 'gtsr' not in p.name.lower())
    cp = os.pathsep.join(map(str, [classes, jar, gt, ae, root / 'build/classes/java/patchedMc',
        root / 'build/rfg/recompiled_minecraft-1.7.10.jar', root / 'build/rfg/mclauncher-1.7.10.jar', *dependencies]))
    sources = [root / 'tools/tests' / name for name in
               ('Issue37RegistrationRegression.java', 'CriticalMEInputContract.java')]
    java = Path.home() / '.gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin'
    receipt = dict(status='RUNNING', productionOverlay=False, jar=str(jar), jarSHA256=common.sha(jar),
        sourceSHA256={str(p.relative_to(root)): common.sha(p) for p in sources},
        gt=str(gt), ae=str(ae), classpath=cp.split(os.pathsep),
        scope='Real input hatches, GT native int lifecycle, long AE stacks, finite inventory/energy seams, '
              'native MUI widget construction and long sync packets; no live AE grid, full server, client input or GL.')
    report = out / 'verification.json'
    report.write_text(json.dumps(receipt, indent=2), encoding='utf-8')
    common.execute(java / 'javac.exe', 'compile', ['-encoding', 'UTF-8', '-proc:none', '-sourcepath', empty,
        '-cp', cp, '-d', classes, *sources], out, runtime)
    common.execute(java / 'java.exe', 'contract', ['-Dfile.encoding=UTF-8', '-Djava.awt.headless=true',
        '-cp', cp, 'CriticalMEInputContract'], out, runtime)
    output = (out / 'contract.log').read_text(encoding='utf-8', errors='replace')
    lines = [line for line in output.splitlines() if 'PASS CriticalMEInputContract checks=' in line]
    if not lines:
        raise RuntimeError('No explicit PASS receipt')
    receipt.update(status='PASS', result=lines,
        detachedBootstrapWarnings=[line.strip() for line in output.splitlines() if 'NoSuchMethodException:' in line])
    if common.sha(jar) != receipt['jarSHA256']:
        raise RuntimeError('Candidate jar changed during verification')
    report.write_text(json.dumps(receipt, ensure_ascii=False, indent=2), encoding='utf-8')
    print('\n'.join(lines))


if __name__ == '__main__':
    main()
