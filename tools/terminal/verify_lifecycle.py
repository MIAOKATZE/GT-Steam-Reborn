"""Compile and exercise only the terminal lifecycle production sources against a dev jar.

This is a detached source-overlay contract, not a packaged-jar, TCP, FML startup or GL test.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile

from verify_remote import ROOT, CACHES, dependency

SOURCES = (
    'common/terminal/TerminalNet.java',
    'common/terminal/TerminalServerSessions.java',
    'main/GTSteamReborn.java',
    'client/terminal/TerminalClientPacketSink.java',
    'client/terminal/HubTerminalClientCache.java',
    'client/terminal/AggregatorClientCache.java',
    'client/terminal/ClusterTerminalClientCache.java',
    'client/gui/terminal/GuiTcdsTerminalScreen.java',
    'common/gui/mui2/cluster/ClusterSessionSyncHandler.java',
)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--core-jar', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--source-root', type=Path, default=ROOT / 'src/main/java/com/miaokatze/gtsr')
    parser.add_argument('--expect-regression', action='store_true')
    parser.add_argument('--java-bin', type=Path, default=Path.home() / '.gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin')
    args = parser.parse_args()
    out = args.out.resolve()
    out.relative_to((ROOT / 'build').resolve())
    if out == (ROOT / 'build').resolve():
        raise ValueError('Use a dedicated build child directory')
    out.mkdir(parents=True, exist_ok=True)
    classes = Path(tempfile.mkdtemp(prefix='classes-', dir=out))
    empty = out / 'empty'
    empty.mkdir(exist_ok=True)
    fixed = [dependency('com.github.GTNewHorizons', 'GT5-Unofficial', '5.09.54.205'),
             dependency('com.github.GTNewHorizons', 'ModularUI2', '2.3.92-1.7.10'),
             dependency('com.github.GTNewHorizons', 'ModularUI', '1.3.4')]
    deps = []
    for cache in CACHES:
        deps.extend(p.resolve() for p in sorted(cache.glob('*/*/*/*/*.jar'))
                    if not any(t in str(p).lower() for t in ('gtsr', 'miaokatzecore', 'miaokatze-core', 'jabel'))
                    and not p.name.endswith(('-sources.jar', '-javadoc.jar')))
    entries = list(dict.fromkeys([classes, args.jar.resolve(strict=True), args.core_jar.resolve(strict=True),
                                 *fixed, ROOT / 'build/rfg/recompiled_minecraft-1.7.10.jar',
                                 ROOT / 'build/rfg/mclauncher-1.7.10.jar', *deps]))
    cp = os.pathsep.join(str(p) for p in entries)
    (out / 'classpath.json').write_text(json.dumps([str(p) for p in entries], indent=2), encoding='utf-8')
    sources = [args.source_root.resolve() / p for p in SOURCES]
    sources.append(ROOT / 'tools/tests/TerminalLifecycleRegression.java')
    hashes = {str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}

    def run(exe, name, values):
        argfile = out / (name + '.args')
        argfile.write_text('\n'.join('"' + str(v).replace('\\', '/') + '"' for v in values), encoding='utf-8')
        flags = ['-J-Duser.language=en', '-J-Dfile.encoding=UTF-8'] if exe == 'javac' else ['-Dfile.encoding=UTF-8']
        result = subprocess.run([str(args.java_bin / (exe + '.exe')), *flags, '@' + str(argfile)],
                                cwd=out, capture_output=True, text=True, encoding='utf-8', errors='replace', timeout=60)
        log = result.stdout + result.stderr
        (out / (name + '.log')).write_text(log, encoding='utf-8')
        return result.returncode, log

    code, log = run('javac', 'compile', ['-encoding', 'UTF-8', '--release', '17', '-proc:none',
                                        '-sourcepath', empty, '-cp', cp, '-d', classes, *sources])
    if code:
        raise RuntimeError(log)
    results = {}
    for name in ('cluster', 'server', 'cooldown', 'client'):
        code, log = run('java', name, ['-cp', cp, 'com.miaokatze.gtsr.common.gui.mui2.cluster.TerminalLifecycleRegression', name])
        expected = code != 0 and 'java.lang.AssertionError:' in log if args.expect_regression else code == 0 and 'PASS TerminalLifecycleRegression' in log
        if not expected:
            raise AssertionError(name + '\n' + log)
        results[name] = {'exitCode': code, 'expectedRegression': args.expect_regression}
        print(name + ': ' + ('expected regression reproduced' if args.expect_regression else 'PASS'))
    if hashes != {str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}:
        raise RuntimeError('Source changed while verification ran')
    receipt = {'mode': 'detached-source-overlay', 'sourceSha256': hashes, 'results': results,
               'jarSha256': hashlib.sha256(args.jar.read_bytes()).hexdigest(),
               'coreSha256': hashlib.sha256(args.core_jar.read_bytes()).hexdigest(),
               'scope': 'Real request/action handlers with outbound network fixture; real serverStopped; real MUI2 readOnClient/clientTick; real Minecraft scheduler queue and connection listener. Unsafe initializes isolated world/player/client state. No game startup, TCP, rendering, or dedicated-server side-stripping verification.'}
    (out / 'verification.json').write_text(json.dumps(receipt, indent=2), encoding='utf-8')


if __name__ == '__main__':
    main()
