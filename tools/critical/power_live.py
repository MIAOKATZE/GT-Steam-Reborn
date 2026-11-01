"""Prepare or run the owned-only power audit mod; prepare never starts Gradle or Minecraft."""
from pathlib import Path
import argparse
import hashlib
import importlib.util
import json
import os
import shutil
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
PRODUCT_CLASS = 'com/miaokatze/gtsr/common/machine/MTECriticalMachine.class'
HELPER_NAME = 'gtsr-power-live-owned-only.jar'
PREFIXES = ('audit/PowerLiveProbeMod',
            'audit/RecipeNonCellLiveObserver',
            'com/miaokatze/gtsr/common/dimension/testlab/CriticalPowerLiveProbe')


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def owned_out(path):
    path = path.resolve()
    if not path.is_relative_to(ROOT / 'build'):
        raise ValueError('Power audit output must stay inside this repository build/')
    return path


def load_mapper():
    source = ROOT / 'tools/testlab/probe_srg_remap.py'
    spec = importlib.util.spec_from_file_location('gtsr_power_srg', source)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.Mapper(ROOT)


def compile_classpath(args):
    dev = args.dev_jar.resolve(strict=True)
    deps = [ROOT / 'build/classes/java/patchedMc', ROOT / 'build/rfg/recompiled_minecraft-1.7.10.jar',
            ROOT / 'build/rfg/mclauncher-1.7.10.jar', dev]
    for value in args.classpath.read_text(encoding='utf-8-sig').strip().split(os.pathsep):
        path = Path(value)
        if not path.exists() or path == dev:
            continue
        if path.is_dir() and path != deps[0]:
            continue  # never import dirty production classes or another probe's source overlay
        if path.suffix == '.jar':
            with zipfile.ZipFile(path) as jar:
                names = jar.namelist()
                if PRODUCT_CLASS in names or 'META-INF/services/com.sun.source.util.Plugin' in names:
                    continue
                if args.core_dev_jar and 'com/miaokatze/miaokatzecore/api/recipe/RecipeRegistration.class' in names:
                    continue
        deps.append(path)
    if args.core_dev_jar:
        deps.append(args.core_dev_jar.resolve(strict=True))
    cache = Path.home() / '.gradle/caches/modules-2/files-2.1'
    deps += sorted((cache / 'com.falsepattern/chunkapi-mc1.7.10/0.8.3').glob('*/*-dev.jar'))
    deps = list(dict.fromkeys(deps))
    gt = []
    for path in deps:
        if path.suffix == '.jar':
            with zipfile.ZipFile(path) as jar:
                if 'gregtech/api/GregTechAPI.class' in jar.namelist():
                    gt.append(path)
    if len(gt) != 1 or gt[0].name != 'GT5-Unofficial-5.09.54.265-dev.jar':
        raise ValueError('Compilation requires exactly one actual GT5U .265 dev jar')
    return deps, gt[0]


def prepare(args):
    out = owned_out(args.out)
    out.mkdir(parents=True, exist_ok=False)
    classes = out / 'classes'
    classes.mkdir()
    empty_source = out / 'empty-source'
    empty_source.mkdir()
    deps, gt = compile_classpath(args)
    sources = [HERE / 'PowerLiveProbeMod.java', HERE / 'CriticalPowerLiveProbe.java', HERE / 'RecipeNonCellLiveObserver.java']
    options = ['-encoding', 'UTF-8', '-proc:none', '--release', '8', '-implicit:none',
               '-sourcepath', str(empty_source), '-cp', os.pathsep.join(map(str, deps)),
               '-d', str(classes), *map(str, sources)]
    argfile = out / 'compile.args'
    argfile.write_text('\n'.join('"' + value.replace('\\', '/') + '"' for value in options), encoding='utf-8')
    compile_run = subprocess.run([str(args.javac), '-J-Duser.language=en', '-J-Duser.country=US',
                                  '@' + str(argfile)], capture_output=True)
    (out / 'compile.log').write_bytes(compile_run.stdout + compile_run.stderr)
    if compile_run.returncode:
        raise RuntimeError('Power helper compilation failed; see ' + str(out / 'compile.log'))
    artifacts = {p.relative_to(classes).as_posix(): p.read_bytes() for p in classes.rglob('*.class')}
    if not artifacts or any(not any(name == prefix + '.class' or name.startswith(prefix + '$')
                                    for prefix in PREFIXES) for name in artifacts):
        raise ValueError('Compiler emitted a class outside the owned helper classes')
    mapper = load_mapper()
    changes = {}
    for name, data in list(artifacts.items()):
        artifacts[name], changes[name] = mapper.remap(data)
    helper = out / HELPER_NAME
    with zipfile.ZipFile(helper, 'w', zipfile.ZIP_DEFLATED) as jar:
        for name, data in sorted(artifacts.items()):
            jar.writestr(zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0)), data)
        jar.writestr('mcmod.info', json.dumps([{'modid': 'gtsr_power_live_probe',
            'name': 'GTSR owned power live audit', 'version': '1'}]))
    scenario = [{'command': 'gtsrtest status', 'expect': r'GTSR-POWER-LIVE\] COMPLETE status=PASS',
                 'failOn': r'GTSR-POWER-LIVE\] COMPLETE status=FAIL', 'timeout': 90}]
    (out / 'scenario.json').write_text(json.dumps(scenario, indent=2) + '\n', encoding='utf-8')
    receipt = {'status': 'COMPILED_NOT_RUN', 'devJar': str(args.dev_jar.resolve()),
               'devSha256': sha(args.dev_jar), 'GT': str(gt), 'gtSha256': sha(gt),
               'helper': str(helper), 'helperSha256': sha(helper),
               'sources': {p.name: sha(p) for p in sources},
               'classMajors': sorted({int.from_bytes(data[6:8], 'big') for data in artifacts.values()}),
               'minecraftMemberRemapping': changes,
               'scope': 'Owned test mod only. Production jar is neither edited nor packaged.'}
    if args.core_dev_jar:
        receipt['coreDevJar'] = str(args.core_dev_jar.resolve())
        receipt['coreDevSha256'] = sha(args.core_dev_jar)
    (out / 'prepare-receipt.json').write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(receipt, indent=2))


def run(args):
    # Reuse the existing guarded server runner, including its JVM stop and evidence collection.
    out = owned_out(args.out)
    prepared = json.loads((out / 'prepare-receipt.json').read_text(encoding='utf-8'))
    helper = out / HELPER_NAME
    if sha(helper) != prepared['helperSha256']:
        raise ValueError('Helper differs from prepared artifact')
    spec = importlib.util.spec_from_file_location('gtsr_power_server', ROOT / 'tools/testlab/server_verify.py')
    server_runner = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(server_runner)
    sandbox = server_runner.guarded(args.sandbox)
    installed = sandbox / 'mods' / HELPER_NAME
    result = sandbox / 'gtsr-power-live/critical-power-live.json'
    if installed.exists() or result.exists() or (out / 'run-receipt.json').exists():
        raise ValueError('Existing helper/evidence: use a fresh owned run; do not overwrite an earlier audit')
    config = sandbox / 'config/gtsr/gtsr.cfg'
    config_before = config.read_bytes()
    # Enable only the copied server's existing testlab category; restore exact bytes afterwards.
    import re
    text = config_before.decode('utf-8-sig')
    category = re.search(r'(?ms)^\s*"?testlab"?\s*\{(.*?)^\s*\}', text)
    if not category:
        raise ValueError('Copied server has no testlab category')
    enabled, count = re.subn(r'(?m)(^\s*B:"?enabled"?\s*=\s*)(?:false|true)',
                             r'\g<1>true', category[1], count=1)
    if count != 1:
        raise ValueError('Copied server has no recognized testlab enabled setting')
    text = text[:category.start(1)] + enabled + text[category.end(1):]
    runtime = args.jar.resolve(strict=True)
    runtime_sha = sha(runtime)
    lease = ROOT.parent / '.gtsr-native-server.lease'
    fd = os.open(lease, os.O_CREAT | os.O_EXCL | os.O_WRONLY)
    os.write(fd, json.dumps({'pid': os.getpid(), 'task': 'critical-power-live', 'sandbox': str(sandbox)}).encode())
    os.close(fd)
    receipt = {'runtimeJar': str(runtime), 'runtimeSha256': runtime_sha,
               'helperSha256': sha(helper), 'status': 'FAIL'}
    try:
        shutil.copy2(helper, installed)
        config.write_text(text, encoding='utf-8')
        command = [sys.executable, '-X', 'utf8', str(ROOT / 'tools/testlab/server_verify.py'), 'run',
                   '--sandbox', str(sandbox), '--jar', str(runtime),
                   '--scenario', str(out / 'scenario.json'), '--java', str(args.java.resolve()),
                   '--xmx-gib', str(args.xmx_gib), '--wait-memory-seconds', '0', '--deadline', str(args.deadline)]
        receipt['command'] = command
        with (out / 'runner.log').open('wb') as log:
            completed = subprocess.run(command, stdout=log, stderr=subprocess.STDOUT)
        receipt['runnerExit'] = completed.returncode
        if result.exists():
            shutil.copy2(result, out / 'result.json')
            receipt['auditStatus'] = json.loads(result.read_text(encoding='utf-8'))['status']
        recipes = result.with_name('noncell-recipes-live.json')
        if recipes.exists():
            shutil.copy2(recipes, out / 'noncell-recipes-live.json')
            receipt['recipeAuditStatus'] = json.loads(recipes.read_text(encoding='utf-8'))['status']
        receipt['status'] = 'PASS' if completed.returncode == 0 and receipt.get('auditStatus') == 'PASS' and receipt.get('recipeAuditStatus') == 'PASS' else 'FAIL'
    finally:
        config.write_bytes(config_before)
        if installed.exists():
            installed.rename(out / 'installed-helper-after-run.jar')
        lease.unlink()
        receipt['configRestored'] = config.read_bytes() == config_before
        (out / 'run-receipt.json').write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(receipt, indent=2))
    if receipt['status'] != 'PASS':
        raise SystemExit(1)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='action', required=True)
    compile_parser = sub.add_parser('prepare')
    compile_parser.add_argument('--dev-jar', type=Path, required=True)
    compile_parser.add_argument('--core-dev-jar', type=Path)
    compile_parser.add_argument('--classpath', type=Path, default=ROOT / 'build/task03-compile-classpath.txt')
    compile_parser.add_argument('--javac', type=Path, default=shutil.which('javac'))
    compile_parser.add_argument('--out', type=Path, default=ROOT / 'build/critical-power-live-helper')
    run_parser = sub.add_parser('run')
    run_parser.add_argument('--out', type=Path, default=ROOT / 'build/critical-power-live-helper')
    run_parser.add_argument('--sandbox', type=Path, required=True)
    run_parser.add_argument('--jar', type=Path, required=True)
    run_parser.add_argument('--java', type=Path, required=True)
    run_parser.add_argument('--xmx-gib', type=int, choices=[3, 4], default=4)
    run_parser.add_argument('--deadline', type=int, default=900)
    args = parser.parse_args()
    prepare(args) if args.action == 'prepare' else run(args)


if __name__ == '__main__':
    main()
