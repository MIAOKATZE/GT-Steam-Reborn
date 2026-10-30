#!/usr/bin/env python3
"""Offline R3 native MUI routing / remote grant lifecycle regressions; no downloads or game launch."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[3]
BASELINE = '57d082e9e748c8918ae3bb2c33df386c9a1cc99e'
SOURCES = (
    'common/gui/MTECriticalMachineTerminalGui.java',
    'common/terminal/MachineTerminalHistory.java',
    'common/terminal/RemoteTerminalGuiData.java',
    'common/gui/mui2/Mui2ServerActions.java',
    'common/gui/mui2/inventory/GuardedModularContainer.java',
)
TESTS = ('PacketAuthorityRegression.java', 'GuiAuthorityRegression.java', 'RemoteGrantAuthorityRegression.java')
FAILURES = {
    'closed': 'closed critical manager still executes native decoded command',
    'deferred': 'critical command mutates before server END drain',
    'disconnected': 'disconnected critical manager still executes command',
    'connection': 'replaced connection retains critical command authority',
    'remote-disconnect': 'remote grant survived disconnect with same player/item/target',
    'remote-connection': 'remote grant survived replacement of its issuing connection',
}
CONTROLS = ('controls', 'lifecycle', 'commands', 'remote-controls')


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--workspace', type=Path, default=ROOT.parents[1])
    parser.add_argument('--out', type=Path, default=ROOT / 'build/review-gui-authority')
    parser.add_argument('--baseline-source', type=Path)
    args = parser.parse_args()
    workspace, out = args.workspace.resolve(), args.out.resolve()
    out.mkdir(parents=True, exist_ok=True)
    empty = out / 'empty'
    empty.mkdir(exist_ok=True)
    jdk = next((workspace / 'toolchains').glob('*jdk17*/bin'))
    cache = workspace / '.gradle-build/caches'
    jars = [next((workspace / '.gradle-build/wrapper').rglob('fastutil-*-min.jar')),
            workspace / 'review-gtsr-client-r1/forge-mcp.jar', workspace / 'review-monitoring/minecraft-mcp.jar',
            workspace / 'review-gtsr-hubs-r1/gt5u-mcp.jar', workspace / 'dependency-probe/gtsr-v2.0.43-dev.jar',
            *sorted((workspace / 'dependency-probe').glob('*.jar')),
            *sorted(cache.glob('modules-2/files-2.1/*/*/*/*/*.jar'))]
    jars = [p for p in dict.fromkeys(jars) if not p.name.endswith(('-sources.jar', '-javadoc.jar'))]
    assert all(p.exists() for p in jars)
    assert sha(workspace / 'dependency-probe/gtsr-v2.0.43-dev.jar') == 'e10aebdc28f331a5f15546472b68067aa4b4128dcfbab2b36684766046fad533'
    cp = ':'.join(map(str, jars))
    results, hashes, baseline_hashes = {}, {}, {}

    def run(name, tool, arguments, failure=None):
        result = subprocess.run([str(jdk / tool), *map(str, arguments)], cwd=out,
                                capture_output=True, text=True, timeout=120)
        log = result.stdout + result.stderr
        (out / (name + '.log')).write_text(log)
        if failure:
            assert result.returncode != 0 and 'java.lang.AssertionError: ' + failure in log, name + '\n' + log
        else:
            assert result.returncode == 0, name + '\n' + log
        print(name + (' reproduced' if failure else ' PASS'), flush=True)
        return result.returncode

    api_source = ROOT / 'tools/tests/network/ForgeHistoryApiFixture.java'
    run('compile-link-fixture', 'javac', ['-cp', cp, '-d', out, api_source])
    api_jar = out / 'history-forge-api-fixture.jar'
    run('generate-link-fixture', 'java', ['-cp', str(out) + ':' + cp, 'ForgeHistoryApiFixture',
                                        workspace / 'review-monitoring/minecraft-mcp.jar', api_jar])
    cp = str(api_jar) + ':' + cp
    (out / 'classpath.txt').write_text(cp)
    for mode in ('baseline', 'patched'):
        classes = out / (mode + '-classes')
        classes.mkdir(exist_ok=True)
        paths = []
        for name in (*SOURCES, 'common/network/ServerPacketAuthority.java'):
            relative = 'src/main/java/com/miaokatze/gtsr/' + name
            source = ROOT / relative
            if mode == 'baseline' and name in SOURCES:
                raw = (args.baseline_source / name).read_text() if args.baseline_source else subprocess.check_output(
                    ['git', 'show', BASELINE + ':' + relative], cwd=ROOT, text=True)
                baseline_hashes[relative] = hashlib.sha256(raw.encode()).hexdigest()
            else:
                raw = source.read_text()
                hashes[relative] = sha(source)
            raw = raw.replace('.func_152344_a(', '.addScheduledTask(').replace('.getWorldObj()', '.getWorld()').replace('.fontRenderer.', '.fontRendererObj.')
            target = out / (mode + '-source') / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(raw)
            paths.append(target)
        for name in TESTS:
            target = out / (mode + '-source') / name
            target.write_text((ROOT / 'tools/tests/network' / name).read_text())
            paths.append(target)
        paths.extend(sorted((ROOT / 'tools/tests/network/fixtures').glob('*.java')))
        run(mode + '-compile', 'javac', ['-encoding', 'UTF-8', '-proc:none', '-sourcepath', empty,
                                       '-cp', cp, '-d', classes, *paths])
        for scenario in (*FAILURES, *(CONTROLS if mode == 'patched' else ())):
            entry = 'RemoteGrantAuthorityRegression' if scenario.startswith('remote-') else 'GuiAuthorityRegression'
            results[mode + '-' + scenario] = run(mode + '-' + scenario, 'java', [
                '-cp', str(classes) + ':' + cp, 'com.miaokatze.gtsr.tests.' + entry, scenario],
                FAILURES[scenario] if mode == 'baseline' else None)
    assert all(sha(ROOT / p) == digest for p, digest in hashes.items()), 'Production source changed during test'
    # This records the authoritative branch choice, not a claim of a live native GT controller test.
    gui = (ROOT / 'src/main/java/com/miaokatze/gtsr/common/gui/MTECriticalMachineTerminalGui.java').read_text()
    power = gui[gui.index('header.child(action(manager, "power"'):gui.index('}).overlay(', gui.index('header.child(action(manager, "power"'))]
    assert 'multiblock.isKindSelector()' in power and 'loomController.getValue()' not in power
    report = {
        'baseline': str(args.baseline_source) if args.baseline_source else BASELINE,
        'results': results, 'sourceSha256': hashes, 'baselineSourceSha256': baseline_hashes,
        'testSha256': {name: sha(ROOT / 'tools/tests/network' / name) for name in TESTS},
        'runnerSha256': sha(Path(__file__)), 'apiFixtureSourceSha256': sha(api_source),
        'fixtureSha256': {p.name: sha(p) for p in sorted((ROOT / 'tools/tests/network/fixtures').glob('*.java'))},
        'dependencySha256': {p.name: sha(p) for p in jars[:5]}, 'powerBranchSourceGuard': True,
        'limitations': 'Whole production source overlays, native MC/Forge/Netty/MUI packet decode and routing. '
        'Unsafe isolates native player/world/container state. Remote grant uses explicit ASM-generated target '
        'and cache-node API fixtures implementing only the methods invoked by real Grant/status, not a native '
        'GT tile lifecycle. Missing Forge Entity#getEntityData and EntityPlayer.PERSISTED_NBT_TAG are link-only '
        'API additions; getEntityData throws if invoked and no persistence path is exercised. Existing three '
        'fastutil constructor fixtures and unused lwjgl3ify link types remain explicit. Power branch has only '
        'a source guard and whole-class compilation. No full Gradle, packaged candidate, side transforms, '
        'Mixins, real client/server game, rendered GUI, GL or TCP test.'}
    (out / 'verification.json').write_text(json.dumps(report, indent=2))
    print('PASS; receipt: ' + str(out / 'verification.json'))


if __name__ == '__main__':
    main()
