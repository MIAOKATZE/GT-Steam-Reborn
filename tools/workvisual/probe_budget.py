"""Run exact production-source budget methods in a pure Java8 harness; never start Gradle or Minecraft."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'src/main/java/com/miaokatze/gtsr/client/machinevisual/MachineVisualClient.java'
HARNESS = Path(__file__).with_name('MotionBudgetContract.java')


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def method(text, name):
    import re
    match = re.search(r'    static [^\n]+\b' + name + r'\(', text)
    if not match:
        raise ValueError('Production method missing: ' + name)
    start = text.index('{', match.start())
    depth = 1
    end = start + 1
    while depth:
        depth += (text[end] == '{') - (text[end] == '}')
        end += 1
    return text[match.start():end]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java', default='java')
    parser.add_argument('--javac', default='javac')
    parser.add_argument('--out', type=Path, default=ROOT / 'build/task03-budget-source-r2')
    args = parser.parse_args()
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=True)
    source = SOURCE.read_text(encoding='utf-8')
    names = ['memberLimit', 'groupLimit', 'selectAdmissions', 'restorationAcknowledged']
    policy = out / 'MotionBudgetUnderTest.java'
    policy.write_text('final class MotionBudgetUnderTest {\n' + '\n'.join(method(source, name) for name in names) + '\n}\n', encoding='utf-8')
    receipt = {'status': 'FAIL', 'boundary': 'Exact production source methods only; candidate JAR/FML/GL/100-machine performance NOT_RUN',
               'sourceSha256': sha(SOURCE), 'methodWrapperSha256': sha(policy), 'harnessSha256': sha(HARNESS), 'methods': names,
               'limits': {'LOW': [128, 16], 'STANDARD': [2048, 64], 'HIGH': [4096, 128]}, 'commands': []}
    commands = [('compile', [args.javac, '--release', '8', '-encoding', 'UTF-8', '-d', str(out), str(policy), str(HARNESS)]),
                ('test', [args.java, '-cp', str(out), 'MotionBudgetContract'])]
    for name, command in commands:
        run = subprocess.run(command, capture_output=True)
        (out / (name + '.stdout.log')).write_bytes(run.stdout)
        (out / (name + '.stderr.log')).write_bytes(run.stderr)
        receipt['commands'].append({'name': name, 'command': command, 'exitCode': run.returncode})
        if run.returncode:
            break
    else:
        if sha(SOURCE) != receipt['sourceSha256']:
            raise ValueError('Production source changed during policy test')
        receipt['status'] = 'PASS'
    (out / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf-8')
    print(receipt['status'] + ' pure source budget contract; ' + str(out / 'receipt.json'))
    return 0 if receipt['status'] == 'PASS' else 1


if __name__ == '__main__':
    raise SystemExit(main())
