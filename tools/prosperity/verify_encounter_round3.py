#!/usr/bin/env python3
"""Offline complete-production-controller/runtime checks against cached native Minecraft APIs."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
from verify_encounter_bounds import API, MAPPINGS

ROOT = Path(__file__).resolve().parents[2]
P = 'src/main/java/com/miaokatze/gtsr/common/'

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--workspace', type=Path, default=ROOT.parents[1])
    parser.add_argument('--out', type=Path)
    args = parser.parse_args()
    w = args.workspace.resolve()
    out = (args.out or w / 'review-gtsr-encounters-r3').resolve(); out.mkdir(parents=True, exist_ok=True)
    jdk = next((w / 'toolchains').glob('*jdk17*/bin'))
    empty = out / 'empty'; empty.mkdir(exist_ok=True)
    mc = w / 'review-monitoring/minecraft-mcp.jar'
    jars = [mc, w / 'review-gtsr-client-r1/forge-mcp.jar', w / 'review-gtsr-hubs-r1/gt5u-mcp.jar',
        *sorted((w / 'dependency-probe').glob('*.jar')),
        *sorted((w / '.gradle-build/caches/modules-2/files-2.1').glob('*/*/*/*/*.jar'))]
    cp = ':'.join(str(p) for p in dict.fromkeys(jars) if not p.name.endswith(('-sources.jar', '-javadoc.jar')))
    results = {}
    def run(label, executable, arguments, fail=False):
        r = subprocess.run([str(jdk / executable), *map(str, arguments)], cwd=out, capture_output=True, text=True, timeout=90)
        log = r.stdout + r.stderr; (out / (label + '.log')).write_text(log)
        if fail:
            if r.returncode == 0 or 'java.lang.AssertionError:' not in log: raise AssertionError(label + '\n' + log)
        elif r.returncode: raise AssertionError(label + '\n' + log)
        results[label] = {'exit': r.returncode, 'log': label + '.log'}
        print(label + ': ' + ('expected RED' if fail else 'PASS'))
    api = API.replace('else {\n     MethodVisitor', '''else {
     writer.visitField(ACC_PUBLIC, "captureDrops", "Z", null, null).visitEnd();
     writer.visitField(ACC_PUBLIC, "capturedDrops", "Ljava/util/ArrayList;", null, null).visitEnd();
     MethodVisitor''')
    api_path = out / 'EncounterApiFixture.java'; api_path.write_text(api)
    asm = next((w / '.gradle-build/caches/modules-2/files-2.1/org.ow2.asm/asm/9.8').glob('*/*.jar'))
    run('api-compile', 'javac', ['-cp', asm, '-d', out, api_path])
    fixture = out / 'forge-api-fixture.jar'
    run('api-build', 'java', ['-cp', str(out)+':'+str(asm), 'EncounterApiFixture', mc, fixture])
    cp = str(fixture) + ':' + cp
    (out / 'classpath.txt').write_text(cp)
    names = ['weapons/WeaponController.java', 'dimension/prosperity/remaster/RemasterRuntime.java']
    support = ['dimension/prosperity/remaster/' + s + '.java' for s in (
        'RemasterData', 'RemasterSite', 'RemasterRollout', 'RemasterCatalog', 'RemasterPrefab',
        'FutureStructurePlanner', 'RemasterSpawnerLedger')]
    mappings = {**MAPPINGS, '.setItemDamage(': '.setMetadata(', 'func_150303_d()': 'getTagType()'}
    hashes = {}
    for mode in ('baseline', 'patched'):
        sources = []
        for name in names + support:
            relative = P + name
            path = w / 'review-state/gtsr-r2-snapshot' / relative if mode == 'baseline' and name in names else ROOT / relative
            source = path.read_text() if path.exists() else subprocess.check_output(
                ['git', 'show', '57d082e9e748c8918ae3bb2c33df386c9a1cc99e:' + relative], cwd=ROOT, text=True)
            if mode == 'patched': hashes[relative] = hashlib.sha256(source.encode()).hexdigest()
            for a,b in mappings.items(): source=source.replace(a,b)
            target = out / (mode+'-sources') / name; target.parent.mkdir(parents=True, exist_ok=True); target.write_text(source); sources.append(target)
        tests = [ROOT / 'tools/prosperity' / (s+'.java') for s in ('RemasterSpawnerPlayerCheck','WeaponRefundConservationCheck')]
        classes = out / (mode+'-classes'); classes.mkdir(exist_ok=True)
        run(mode+'-compile', 'javac', ['-proc:none','-encoding','UTF-8','-sourcepath',empty,'-cp',cp,'-d',classes,*sources,*tests])
        runtime = str(classes)+':'+str(ROOT/'src/main/resources')+':'+cp
        for scenario in ('masked','normal'):
            run(mode+'-spawner-'+scenario, 'java',['-cp',runtime,'com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterSpawnerPlayerCheck',scenario],mode=='baseline' and scenario=='masked')
        for scenario in ('overflow','normal','missing-mimic','rejected-drop') + (('receipts',) if mode=='patched' else ()):
            run(mode+'-weapon-'+scenario, 'java',['-cp',runtime,'com.miaokatze.gtsr.common.weapons.WeaponRefundConservationCheck',scenario],mode=='baseline' and scenario in ('overflow','missing-mimic','rejected-drop'))
    seams = out / 'transaction-seams'; seams.mkdir(exist_ok=True)
    fixtures = sorted(p for p in (ROOT/'tools/prosperity/fixtures/weapon-round3').glob('*.java') if p.name != 'CommandApiFixture.java')
    run('transaction-seams-compile', 'javac', ['-proc:none','-sourcepath',empty,'-cp',str(out/'patched-classes')+':'+cp,'-d',seams,*fixtures])
    run('patched-weapon-transactions', 'java', ['-cp',str(seams)+':'+str(out/'patched-classes')+':'+cp,
        'com.miaokatze.gtsr.common.weapons.WeaponRefundConservationCheck','transactions'])
    run('patched-weapon-malformed', 'java', ['-cp',str(seams)+':'+str(out/'patched-classes')+':'+cp,
        'com.miaokatze.gtsr.common.weapons.WeaponRefundConservationCheck','malformed'])
    # Independent-review regression: replay the previous zero-cost early return without changing any other behavior.
    early_source = (out/'patched-sources/weapons/WeaponController.java').read_text().replace(
        '// Even an all-free top-up overwrites returnPack below; migrate legacy paid provenance first.\n        NBTTagList payments = magazinePayments(n);\n        if (paid <= 0) return;',
        'if (paid <= 0) return;\n        NBTTagList payments = magazinePayments(n);')
    early_path = out/'before-free-topup/WeaponController.java'; early_path.parent.mkdir(exist_ok=True); early_path.write_text(early_source)
    early_classes = out/'before-free-topup-classes'
    run('before-free-topup-compile', 'javac', ['-proc:none','-sourcepath',empty,'-cp',cp,'-d',early_classes,early_path])
    run('before-free-topup-transactions', 'java', ['-cp',str(seams)+':'+str(early_classes)+':'+str(out/'patched-classes')+':'+cp,
        'com.miaokatze.gtsr.common.weapons.WeaponRefundConservationCheck','transactions'], True)
    before_schema = (out/'patched-sources/weapons/WeaponController.java').read_text().replace(
        'if (!readablePayments(n)) return;', '').replace(
        'if ((k == WeaponKind.QLZ04 || k == WeaponKind.SINGULARITY) && !readablePayments(n)) return false;', '')
    schema_path = out/'before-schema/WeaponController.java'; schema_path.parent.mkdir(exist_ok=True); schema_path.write_text(before_schema)
    schema_classes = out/'before-schema-classes'
    run('before-schema-compile', 'javac', ['-proc:none','-sourcepath',empty,'-cp',cp,'-d',schema_classes,schema_path])
    run('before-schema-malformed', 'java', ['-cp',str(seams)+':'+str(schema_classes)+':'+str(out/'patched-classes')+':'+cp,
        'com.miaokatze.gtsr.common.weapons.WeaponRefundConservationCheck','malformed'], True)
    command_api_source = ROOT/'tools/prosperity/fixtures/weapon-round3/CommandApiFixture.java'
    run('command-api-compile', 'javac', ['-cp',asm,'-d',out,command_api_source])
    command_api = out/'command-api-fixture.jar'
    run('command-api-build', 'java', ['-cp',str(out)+':'+str(asm),'CommandApiFixture',mc,command_api])
    command_relative = P+'commands/WeaponAuditCommand.java'
    command = (ROOT/command_relative).read_text(); hashes[command_relative] = hashlib.sha256(command.encode()).hexdigest()
    for a,b in {**mappings,'.getMaxDamage()':'.getMaxDurability()', '.func_147447_a(':'.rayTraceBlocks('}.items(): command=command.replace(a,b)
    command_path = out/'command-mapped/WeaponAuditCommand.java'; command_path.parent.mkdir(exist_ok=True); command_path.write_text(command)
    run('command-compile', 'javac', ['-proc:none','-encoding','UTF-8','-sourcepath',empty,'-cp',str(command_api)+':'+str(out/'patched-classes')+':'+cp,'-d',out/'command-classes',command_path])
    (out/'verification.json').write_text(json.dumps({'results':results,'production_sha256':hashes,
        'baseline':'review-state/gtsr-r2-snapshot with unchanged-source git fallback','test_sha256':{str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in [*tests,Path(__file__),*fixtures,command_api_source]},'limitations':'Complete modified controller/runtime and current Data/Site compiled against native MC/Forge/GT/GTSR cached jars. Mechanical MCP name mappings, Forge World storage/Entity capture fields and throwing unexecuted getEntityData shim. Native ItemStack/NBT/InventoryPlayer with allocated players/world and explicit drop-admission fixture; Separate transactions execute actual finishReload/update with projectile/pose/network effect seams. Complete audit command compiles with two unexecuted Forge API methods and native bridge visibility adapter. No game loop, full Forge startup or Gradle build.'},indent=2))

if __name__=='__main__': main()
