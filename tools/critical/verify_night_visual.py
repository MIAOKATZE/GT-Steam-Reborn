"""Compile only the night module and run its local visual transition/palette contract."""
from pathlib import Path
import json
import os
import subprocess
import verify_v32 as fixture
from verify_moonless_night import NIGHT

ROOT = fixture.ROOT
OUT = ROOT / 'build/critical-night-visual-v2041'

def main():
    OUT.mkdir(parents=True, exist_ok=True)
    classes = OUT / 'classes'
    classes.mkdir(exist_ok=True)
    jars = list(fixture.CACHE.glob('*/*/*/*/*.jar'))
    baseline = ROOT / 'build/libs/gtsr-v2.0.40-dev.jar'
    sources = NIGHT + [ROOT / 'tools/critical/CriticalNightVisualContract.java']
    cp = os.pathsep.join(map(str, [classes, baseline, ROOT / 'build/classes/java/main',
        ROOT / 'build/classes/java/patchedMc', ROOT / 'src/main/resources', *jars]))
    config = OUT / 'log4j2.xml'
    config.write_text('<Configuration status="OFF"><Appenders/><Loggers><Root level="off"/></Loggers></Configuration>', encoding='utf-8')
    for exe, label, args in (
        ('javac', 'compile', ['-encoding', 'UTF-8', '-proc:none', '-cp', cp, '-d', classes, *sources]),
        ('java', 'run', ['-Djava.awt.headless=true', '-Dlog4j.configurationFile=' + str(config), '-cp', cp,
                        'com.miaokatze.gtsr.client.critical.CriticalNightVisualContract'])):
        argfile = OUT / (label + '.args')
        argfile.write_text('\n'.join('"' + str(v).replace('\\', '/') + '"' for v in args), encoding='utf-8')
        result = subprocess.run([str(fixture.JAVA / (exe + '.exe')), '@' + str(argfile)], capture_output=True,
                                creationflags=subprocess.CREATE_NO_WINDOW, timeout=90)
        text = (result.stdout + result.stderr).decode('utf-8', errors='replace')
        (OUT / (label + '.log')).write_text(text, encoding='utf-8')
        if result.returncode: raise RuntimeError(text[-8000:])
    assert 'PASS CriticalNightVisualContract checks=' in text
    (OUT / 'receipt.json').write_text(json.dumps(dict(status='PASS', result=text.strip(), sourceSHA256=fixture.binding(sources),
        scope='Deterministic bidirectional 10-second fade/world-connection isolation and normal-midnight minus one sky row palette; all night classes compile. Actual Mixin/GL/provider scene remains Native validation.'), indent=2), encoding='utf-8')
    print(text.strip())

if __name__ == '__main__': main()
