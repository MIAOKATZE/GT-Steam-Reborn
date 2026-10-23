"""Bounded RC2 source-overlay chemical contract; no build, game launch or live ME network."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import subprocess

ROOT = Path(__file__).resolve().parents[2]
CACHE = Path.home() / '.gradle/caches/modules-2/files-2.1'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar',type=Path,help='Validate solely against a built dev jar instead of source overlay')
    parser.add_argument('--out',type=Path,default=ROOT / 'build/rc2-chemical-source-contract')
    args=parser.parse_args()
    out = args.out.resolve()
    out.relative_to(ROOT / 'build')
    classes = out / ('fixture-classes' if args.jar else 'classes')
    empty = out / 'empty'
    classes.mkdir(parents=True, exist_ok=True)
    if args.jar and any((classes / 'com/miaokatze/gtsr').rglob('*.class')):
        raise RuntimeError('Jar-only fixture directory contains production overlays; use a fresh --out directory')
    empty.mkdir(exist_ok=True)
    gt = next((CACHE / 'com.github.GTNewHorizons/GT5-Unofficial/5.09.54.205').glob('*/*-dev.jar'))
    ae = next((CACHE / 'com.github.GTNewHorizons/Applied-Energistics-2-Unofficial/rv3-beta-1080-GTNH').glob('*/*-dev.jar'))
    dependencies = sorted(p for p in CACHE.glob('*/*/*/*/*.jar')
        if '/GT5-Unofficial/' not in p.as_posix() and '/Applied-Energistics-2-Unofficial/' not in p.as_posix()
        and 'gtsr' not in p.name.lower())
    baseline = args.jar.resolve() if args.jar else ROOT / 'build/libs/gtsr-v2.0.6-dev.jar'
    production = [baseline] if args.jar else [ROOT / 'build/classes/java/main',baseline]
    cp = os.pathsep.join(map(str, [classes, *production, gt, ae,
        ROOT / 'build/classes/java/patchedMc', ROOT / 'build/rfg/recompiled_minecraft-1.7.10.jar',
        ROOT / 'build/rfg/mclauncher-1.7.10.jar', *dependencies]))
    sources = [ROOT / 'tools/tests' / n for n in ('Issue37RegistrationRegression.java','CriticalChemicalContract.java')]
    if not args.jar:
        sources += [ROOT / 'src/main/java/com/miaokatze/gtsr/common/critical' / (name + '.java') for name in
            ('CriticalChemicalMath','CriticalEnergyMath','CriticalEnergyPorts','CriticalEnergyRuntime','CriticalGTRecipes',
             'CriticalEnergyDebit','CriticalStellarPolicy','CriticalStellarRuntime','CriticalAdvancedRecipes',
             'CriticalProcessingModes')]
        sources += [ROOT / 'src/main/java/com/miaokatze/gtsr' / n for n in
            ('common/critical/recipe/RecipeMatch.java','common/machine/MTECriticalMachine.java','api/compat/GTSRHatchFluidAccess.java')]
    hashes = {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}
    java = Path.home() / '.gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin'

    def run(exe, name, args):
        argfile = out / (name + '.args')
        argfile.write_text('\n'.join('"' + str(a).replace('\\','/') + '"' for a in args), encoding='utf-8')
        result = subprocess.run([str(java / exe), '@' + str(argfile)],cwd=out,
            stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=60,
            creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
        output = result.stdout.decode('utf-8',errors='replace')
        (out / (name + '.log')).write_text(output,encoding='utf-8')
        if result.returncode:
            raise RuntimeError(name + ' failed: ' + output[-6500:])
        return output

    run('javac.exe','compile',['-encoding','UTF-8','-proc:none','-sourcepath',empty,'-cp',cp,'-d',classes,*sources])
    result = run('java.exe','contract',['-Xmx1G','-cp',cp,'CriticalChemicalContract'])
    lines = [line for line in result.splitlines() if line.startswith(('PASS ','SOURCE '))]
    receipt = {'status':'PASS','productOverlay':not bool(args.jar),'gtJar':str(gt),'aeJar':str(ae),
        'baselineJar':str(baseline),'baselineSHA256':hashlib.sha256(baseline.read_bytes()).hexdigest(),
        'sourceSHA256':hashes,'result':lines,
        'scope':('Jar-only production classes' if args.jar else 'Current source overlay') +
            ', real Minecraft NBT, GT wireless map and synthetic real-hatch drain receipts; no full build, live ME, FML server or crash-atomicity claim'}
    (out/'verification.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2),encoding='utf-8')
    print('\n'.join(lines))


if __name__ == '__main__':
    main()
