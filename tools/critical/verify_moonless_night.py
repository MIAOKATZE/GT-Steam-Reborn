"""Night ownership and shared lightmap contract, no FML/client startup; compile all new mixins as well."""
from pathlib import Path
import argparse
import json
import os
import subprocess
import verify_v32 as fixture

ROOT = fixture.ROOT
NIGHT = [ROOT / 'src/main/java/com/miaokatze/gtsr' / path for path in (
    'common/critical/CriticalWorldData.java', 'common/critical/CriticalNightClock.java',
    'client/critical/CriticalNightClient.java', 'mixin/MixinWorldCriticalTime.java',
    'mixin/MixinWorldProviderCriticalNight.java', 'mixin/PersonalWorldProviderCriticalNightMixin.java',
    'mixin/WorldCriticalNightClientMixin.java', 'mixin/WorldProviderCriticalNightClientMixin.java',
    'mixin/RenderGlobalCriticalNightMixin.java', 'mixin/EntityRendererCriticalNightMixin.java')]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path)
    parser.add_argument('--out', type=Path, default=ROOT / 'build/critical-moonless-night-v2037')
    args = parser.parse_args()
    out = args.out.resolve()
    out.relative_to(ROOT / 'build')
    classes, gates = out / 'classes', out / 'gates'
    classes.mkdir(parents=True, exist_ok=True)
    gates.mkdir(exist_ok=True)
    log_config = out / 'log4j2.xml'
    log_config.write_text('<Configuration status="OFF"><Appenders/><Loggers><Root level="off"/></Loggers></Configuration>', encoding='utf-8')
    jars = list(fixture.CACHE.glob('*/*/*/*/*.jar'))
    gt = [p for p in jars if 'GT5-Unofficial/5.09.54.205/' in p.as_posix()]
    baseline = max((ROOT / 'build/libs').glob('*-dev.jar'), key=lambda p: p.stat().st_mtime)
    sources = [ROOT / 'tools/critical/CriticalMoonlessNightContract.java', ROOT / 'tools/critical/CriticalV32Fixture.java']
    if not args.jar:
        sources += list(dict.fromkeys(fixture.PRODUCTION + NIGHT))
    cp = os.pathsep.join(map(str, [classes, *([args.jar.resolve()] if args.jar else []), *gt,
        *([] if args.jar else [ROOT / 'build/classes/java/main', baseline]),
        ROOT / 'build/classes/java/patchedMc', ROOT / 'src/main/resources', *jars]))

    def run(exe, name, values):
        argfile = out / (name + '.args')
        argfile.write_text('\n'.join('"' + str(v).replace('\\', '/') + '"' for v in values), encoding='utf-8')
        result = subprocess.run([str(fixture.JAVA / (exe + '.exe')), '@' + str(argfile)],
            capture_output=True, creationflags=subprocess.CREATE_NO_WINDOW, timeout=180)
        output = (result.stdout + result.stderr).decode('utf-8', errors='replace')
        (out / (name + '.log')).write_text(output, encoding='utf-8')
        if result.returncode:
            raise RuntimeError(name + ' failed:\n' + output[-9000:])
        return output

    run('javac', 'compile', ['-encoding', 'UTF-8', '-proc:none', '-cp', cp, '-d', classes, *sources])
    run('javac', 'gates', ['-encoding', 'UTF-8', '-proc:none', '-cp', cp, '-d', gates, *fixture.GATES])
    output = run('java', 'run', ['-Djava.awt.headless=true', '-Dfile.encoding=UTF-8', '-Dlog4j.configurationFile=' + str(log_config), '-cp',
        str(gates) + os.pathsep + cp, 'com.miaokatze.gtsr.common.critical.CriticalMoonlessNightContract'])
    if 'PASS CriticalMoonlessNightContract checks=' not in output:
        raise RuntimeError('Missing explicit PASS: ' + output[-2000:])
    (out / 'verification.json').write_text(json.dumps(dict(status='PASS', result=output.strip(),
        sourceSHA256=fixture.binding(sources), jar=str(args.jar) if args.jar else None,
        scope='Real WorldData ownership/NBT + public controller observations + lightmap sky-zero palette; '
              'all night classes compile; actual Mixin weaving/GL upload/custom sky handler still require native validation.'),
        ensure_ascii=False, indent=2), encoding='utf-8')
    print(output.strip())


if __name__ == '__main__':
    main()
