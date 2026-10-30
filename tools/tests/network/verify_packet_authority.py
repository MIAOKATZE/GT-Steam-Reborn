#!/usr/bin/env python3
"""Offline MC/Forge/MUI source-overlay regressions; no Gradle, downloads, game launch, GL, or TCP."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[3]
BASELINE = '57d082e9e748c8918ae3bb2c33df386c9a1cc99e'
SOURCES = (
    'common/network/BoundedRequestQueue.java', 'common/network/ServerPacketAuthority.java',
    'common/network/WandNet.java', 'common/network/GTSRFXNet.java', 'common/terminal/TerminalNet.java',
    'client/terminal/TerminalClientPacketSink.java', 'common/terminal/PacketTerminalRequest.java',
    'common/terminal/PacketTerminalAction.java', 'common/terminal/PacketTerminalData.java',
    'common/terminal/PacketOpenTerminalUi.java', 'common/gui/mui2/worker/WorkerUiSession.java',
    'common/gui/mui2/hub/HubTerminalSession.java', 'common/gui/mui2/cluster/ClusterSessionSyncHandler.java',
    'common/gui/mui2/inventory/AggregatorConfigSession.java',
    'common/gui/mui2/entanglement/EntanglementTerminalSession.java',
    'common/gui/mui2/TcdsTerminalPanel.java', 'common/terminal/MachineHistoryFactory.java',
)
REGRESSIONS = ('terminal-flood', 'bind-flood', 'wand-flood', 'disconnect', 'connection',
               'bind-disconnect', 'client', 'malformed-request', 'malformed-action',
               'malformed-data', 'malformed-open', 'history', 'budget', 'fx-dimension')
FIXED_ONLY = ('authority', 'cleanup')


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--workspace', type=Path, default=ROOT.parents[1])
    parser.add_argument('--out', type=Path, default=ROOT / 'build/review-packet-authority')
    parser.add_argument('--baseline', default=BASELINE)
    args = parser.parse_args()
    workspace, out = args.workspace.resolve(), args.out.resolve()
    out.mkdir(parents=True, exist_ok=True)
    jdk = next((workspace / 'toolchains').glob('*jdk17*/bin'))
    cache = workspace / '.gradle-build/caches'
    forge = cache / 'minecraft/net/minecraftforge/forge/1.7.10-10.13.4.1614-1.7.10/unpacked'
    mc = workspace / 'review-monitoring/minecraft-mcp.jar'
    release = workspace / 'dependency-probe/gtsr-v2.0.43-dev.jar'
    if sha(release) != 'e10aebdc28f331a5f15546472b68067aa4b4128dcfbab2b36684766046fad533':
        raise AssertionError('Unexpected official baseline dev artifact')
    empty = out / 'empty'
    empty.mkdir(exist_ok=True)

    def run(name, executable, arguments, expected_failure=False):
        command = [str(jdk / executable), *map(str, arguments)]
        result = subprocess.run(command, cwd=out, capture_output=True, text=True, timeout=90)
        log = result.stdout + result.stderr
        (out / (name + '.log')).write_text(log)
        if expected_failure:
            if result.returncode == 0 or 'java.lang.AssertionError:' not in log:
                raise AssertionError(name + ': expected behavioral assertion\n' + log)
        elif result.returncode:
            raise AssertionError(name + '\n' + log)
        return result.returncode

    asm = [next(cache.glob('modules-2/files-2.1/org.ow2.asm/' + name + '/9.8/*/*.jar'))
           for name in ('asm', 'asm-commons')]
    asmcp = ':'.join(map(str, asm))
    run('compile-remapper', 'javac', ['-cp', asmcp, '-d', out, ROOT / 'tools/tests/network/RemapSrg.java'])
    remapped_forge = out / 'forge-mcp.jar'
    run('remap-forge', 'java', ['-cp', str(out) + ':' + asmcp, 'RemapSrg', forge / 'binaries.jar',
                               remapped_forge, forge / 'srgs/notch-mcp.srg'])
    with zipfile.ZipFile(remapped_forge) as archive:
        assert archive.testzip() is None
        assert 'cpw/mods/fml/common/network/simpleimpl/MessageContext.class' in archive.namelist()
    jars = [remapped_forge, mc, release, *sorted((workspace / 'dependency-probe').glob('*.jar')),
            *sorted(cache.glob('modules-2/files-2.1/*/*/*/*/*.jar'))]
    jars = [p for p in dict.fromkeys(jars) if not p.name.endswith(('-sources.jar', '-javadoc.jar'))]
    cp = ':'.join(map(str, jars))
    (out / 'classpath.txt').write_text(cp)
    hashes, results = {}, {}
    test = ROOT / 'tools/tests/network/PacketAuthorityRegression.java'
    for mode in ('baseline', 'patched'):
        classes = out / (mode + '-classes')
        classes.mkdir(exist_ok=True)
        sources = []
        for name in SOURCES:
            relative = 'src/main/java/com/miaokatze/gtsr/' + name
            if mode == 'baseline':
                if name.endswith(('BoundedRequestQueue.java', 'ServerPacketAuthority.java')):
                    continue
                text = subprocess.check_output(['git', 'show', args.baseline + ':' + relative],
                                               cwd=ROOT, text=True)
            else:
                text = (ROOT / relative).read_text()
                hashes[relative] = sha(ROOT / relative)
            # These are MCP name differences in the cached native MC artifact, not behavioral fixtures.
            text = text.replace('.func_152344_a(', '.addScheduledTask(')
            text = text.replace('.getWorldObj()', '.getWorld()').replace('.fontRenderer.', '.fontRendererObj.')
            target = out / (mode + '-source') / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(text)
            sources.append(target)
        sources.append(test)
        if mode == 'patched':
            sources.append(ROOT / 'tools/tests/network/BoundedRequestQueueRegression.java')
        run(mode + '-compile', 'javac', ['-encoding', 'UTF-8', '-proc:none', '-sourcepath', empty,
                                         '-cp', cp, '-d', classes, *sources])
        runtime = str(classes) + ':' + cp
        for scenario in REGRESSIONS + (FIXED_ONLY if mode == 'patched' else ()):
            code = run(mode + '-' + scenario, 'java', ['-cp', runtime,
                       'com.miaokatze.gtsr.tests.PacketAuthorityRegression', scenario], mode == 'baseline')
            results[mode + '-' + scenario] = code
            print(mode + ': ' + scenario + (' reproduced' if mode == 'baseline' else ' PASS'))
        if mode == 'patched':
            run('patched-concurrent-queue', 'java', ['-cp', runtime,
                                                    'com.miaokatze.gtsr.tests.BoundedRequestQueueRegression'])
    assert all(sha(ROOT / path) == digest for path, digest in hashes.items()), 'Sources changed during test run'
    report = {'baseline': args.baseline, 'results': results, 'sourceSha256': hashes,
              'testSha256': sha(test), 'releaseSha256': sha(release), 'minecraftSha256': sha(mc),
              'forgeOriginalSha256': sha(forge / 'binaries.jar'), 'forgeRemappedSha256': sha(remapped_forge),
              'limitations': 'Detached source overlay with real MC/Forge/Netty/MUI/GTSR dependencies. Unsafe constructs '
              'isolated world/player/container state; MUI manager getters run on field-initialized native instances. '
              'Outbound FX transport is a recording wrapper. History active() covers the exact authority gate, not '
              'a rendered/opened UI. No full Gradle build, packaged candidate, Mixins/side stripping, gameplay, GL, or TCP.'}
    (out / 'verification.json').write_text(json.dumps(report, indent=2))
    print('PASS; receipt: ' + str(out / 'verification.json'))


if __name__ == '__main__':
    main()
