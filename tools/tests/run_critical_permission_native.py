#!/usr/bin/env python3
"""Run the bounded native vanilla permission test; does not boot Forge or construct FakePlayer."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home', type=Path, required=True)
    parser.add_argument('--classpath', required=True, help='Mapped real MC, Forge and runtime dependencies')
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    out = args.out.resolve()
    (out / 'empty').mkdir(parents=True, exist_ok=True)
    sources = [ROOT / 'src/main/java/com/miaokatze/gtsr/common/critical/CriticalConstructionAccess.java',
               ROOT / 'tools/tests/NativeConstructionPermissionCheck.java']
    receipt = {
        'boundary': 'Complete production access helper; actual vanilla WorldServer/DedicatedServer spawn protection '
                    'and UUID op lookup. Unsafe-preallocated actors/server objects are fixtures. No FakePlayer '
                    'constructor, Forge server lifecycle, third-party claims, or game integration is exercised.',
        'sourceSHA256': {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources},
    }
    commands = [
        ('compile', [args.java_home / 'bin/javac', '-encoding', 'UTF-8', '-proc:none', '-sourcepath', out / 'empty',
                     '-cp', args.classpath, '-d', out / 'classes', *sources]),
        ('test', [args.java_home / 'bin/java', '-cp', str(out / 'classes') + os.pathsep + args.classpath,
                  'com.miaokatze.gtsr.common.critical.NativeConstructionPermissionCheck']),
    ]
    for name, command in commands:
        result = subprocess.run(list(map(str, command)), capture_output=True, text=True)
        log = result.stdout + result.stderr
        (out / (name + '.log')).write_text(log)
        print(log, end='')
        receipt[name + 'ExitCode'] = result.returncode
        (out / 'receipt.json').write_text(json.dumps(receipt, indent=2))
        if result.returncode:
            return result.returncode
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
