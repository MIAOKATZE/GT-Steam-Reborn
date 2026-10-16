"""Standalone full-client lifecycle audit; source Prism instance is strictly read-only."""
from pathlib import Path
import argparse, hashlib, json, os, re, shutil, subprocess, time, zipfile
from native_fixture import java_processes

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'build/critical-machines-v12117/client'
SOURCE = Path('E:/Prism Launcher/instances/GTNH-2.9.0-RC-2-SH')
PRISM = Path.home() / 'AppData/Roaming/PrismLauncher'
JAVA = Path.home() / '.gradle/jdks/azul_systems__inc_-21-amd64-windows.2/bin/java.exe'
JAVAC = Path.home() / '.gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin/javac.exe'
LEASE = Path('E:/CodeGT/.gtsr-native-server.lease')

def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def phase_name(value):
    if not re.fullmatch(r'[A-Za-z0-9_-]+',value):raise argparse.ArgumentTypeError('phase must contain only letters, numbers, underscore or hyphen')
    return value
def fork_fixture(phase,previous,source_out=None,jar=None):
    target=OUT/phase;old=((source_out or OUT)/previous).resolve()
    if not old.resolve().is_relative_to((ROOT/'build').resolve()):raise RuntimeError('Source fixture outside private build output')
    target.mkdir(parents=True,exist_ok=False)
    old_fixture=old/'fixture';fixture=target/'fixture';fixture.mkdir()
    for source in old_fixture.iterdir():
        if source.name in ['logs','crash-reports','.healer.log','saves','backups']:continue
        dest=fixture/source.name
        if source.is_dir():
            # Hard links only for immutable libraries/assets/natives; mutable game files are separate copies.
            shutil.copytree(source,dest,copy_function=os.link if source.name in ['assets','libraries','natives'] else shutil.copy2)
        else:shutil.copy2(source,dest)
    spec=json.loads((old/'launch.json').read_text());spec['command']=[s.replace(str(old),str(target)) for s in spec['command']];spec['cwd']=str(fixture);spec['forkedFrom']=str(old)
    options=fixture/'options.txt'
    options.write_text('\n'.join(line for line in options.read_text().splitlines() if not line.startswith('pauseOnLostFocus:'))+'\npauseOnLostFocus:false\n')
    spec.pop('probeSourceSHA',None);spec.pop('probeJarSHA',None)
    if jar:
        shutil.copy2(jar,fixture/'mods/production-gtsr.jar')
        spec.update(jar=str(jar.resolve()),sha256=sha(jar),expectedLegacyFailure=False)
        spec['command']=[s.replace('-Dcritical.gui.expectLegacyFailure=true','-Dcritical.gui.expectLegacyFailure=false') for s in spec['command']]
    (target/'launch.json').write_text(json.dumps(spec,indent=2))
    print(target)
def artifact(lib):
    group, name, version, *classifier = lib['name'].split(':')
    filename = name + '-' + version + ('-' + classifier[0] if classifier else '') + '.jar'
    return Path(group.replace('.', '/')) / name / version / filename

def prepare(phase, jar, expected):
    target = OUT / phase
    target.mkdir(parents=True, exist_ok=False)
    fixture = target / 'fixture'; fixture.mkdir()
    game = SOURCE / '.minecraft'
    # No saves, accounts, launcher configuration, or original instance options are copied.
    for name in ['config', 'scripts', 'resourcepacks']:
        if (game / name).exists(): shutil.copytree(game / name, fixture / name)
    shutil.copytree(PRISM / 'assets', fixture / 'assets')
    shutil.copytree(SOURCE / 'natives', fixture / 'natives')
    (fixture / 'mods').mkdir()
    for source in (game / 'mods').rglob('*'):
        if not source.is_file() or source.name.lower().startswith('gtsr-v') or 'criticalguilifecycle' in source.name.lower(): continue
        dest = fixture / 'mods' / source.relative_to(game / 'mods'); dest.parent.mkdir(parents=True, exist_ok=True); shutil.copy2(source, dest)
    shutil.copy2(jar, fixture / 'mods/production-gtsr.jar')
    # Disable fullscreen/sound and open only a separate small audit window.
    (fixture / 'options.txt').write_text('fullscreen:false\npauseOnLostFocus:false\nsoundCategory_master:0.0\n', encoding='utf-8')
    finish(phase,jar,expected)

def finish(phase,jar,expected):
    target=OUT/phase;fixture=target/'fixture'
    if (target/'launch.json').exists():raise RuntimeError('Launch specification already exists')
    if sha(jar)!=sha(fixture/'mods/production-gtsr.jar'):raise RuntimeError('Candidate differs from prepared fixture')
    cp=[]; jvm=[]; main=None
    libraries={}
    patches=sorted((json.loads(p.read_text(encoding='utf-8')) for p in (SOURCE / 'patches').glob('*.json')), key=lambda p:p.get('order',0))
    for patch in patches:
        jvm.extend(patch.get('+jvmArgs', [])); main=patch.get('mainClass', main)
        for lib in patch.get('libraries', []) + ([patch['mainJar']] if 'mainJar' in patch else []):
            rules=lib.get('rules', [])
            allowed=not rules
            for rule in rules:
                if rule.get('os',{}).get('name','windows')=='windows':allowed=rule.get('action')=='allow'
            if not allowed:continue
            # Prism already extracted these native-only artifacts into the copied natives directory.
            if 'natives' in lib and 'artifact' not in lib.get('downloads',{}):continue
            if 'windows-arm64' in lib['name'] or 'windows-x86' in lib['name']: continue
            parts=lib['name'].split(':');libraries[tuple(parts[:2]+parts[3:])]=lib
    for lib in libraries.values():
        source = SOURCE / 'libraries' / artifact(lib).name if lib.get('MMC-hint')=='local' else PRISM / 'libraries' / artifact(lib)
        if not source.exists(): raise FileNotFoundError(source)
        dest=fixture / 'libraries' / artifact(lib);dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,dest);cp.append(str(dest))
    command=[str(JAVA),'-Xms512M','-Xmx6G']+jvm+['-Dcritical.gui.receipt='+str(target/'checks.json'),'-Dcritical.gui.expectLegacyFailure='+str(expected).lower(),'-Djava.library.path='+str(fixture/'natives'),'-cp',';'.join(cp),main,'--username','CriticalAudit','--version','1.7.10','--gameDir',str(fixture),'--assetsDir',str(fixture/'assets'),'--assetIndex','1.7.10','--uuid','00000000000000000000000000000001','--accessToken','0','--userProperties','{}','--userType','legacy','--tweakClass','cpw.mods.fml.common.launcher.FMLTweaker','--width','854','--height','480']
    spec=dict(command=command,cwd=str(fixture),jar=str(jar.resolve()),sha256=sha(jar),expectedLegacyFailure=expected,scope='Standalone real client boot; no original world copied')
    (target/'launch.json').write_text(json.dumps(spec,indent=2),encoding='utf-8')
    print(target)

def compile_probe(phase,mode,preview_from=None):
    target=OUT/phase;fixture=target/'fixture';classes=target/'classes';classes.mkdir(exist_ok=True)
    jars=list((fixture/'libraries').rglob('*.jar'))+list((fixture/'mods').glob('*.jar'))
    cp=';'.join(map(str,[ROOT/'build/classes/java/patchedMc']+jars))
    probe_name='CriticalLoomClientProbe' if mode=='loom' else 'CriticalGTClientProbe' if mode.startswith('gt') else 'CriticalGuiLifecycleProbe'
    source=target/(probe_name+'.java')
    shutil.copy2(Path(__file__).with_name(probe_name+'.java'),source)
    args=['-encoding','UTF-8','-proc:none','-source','8','-target','8','-cp',cp,'-d',str(classes),str(source)]
    argfile=target/'compile.args';argfile.write_text('\n'.join('"'+s.replace('\\','/')+'"' for s in args),encoding='utf-8')
    result=subprocess.run([str(JAVAC),'@'+str(argfile)],capture_output=True,text=True)
    (target/'compile.log').write_text(result.stdout+result.stderr,encoding='utf-8')
    if result.returncode: raise RuntimeError(result.stderr)
    helper=fixture/'mods/critical-gui-lifecycle.jar'
    with zipfile.ZipFile(helper,'w') as z:
        for file in classes.rglob('*.class'):z.write(file,file.relative_to(classes).as_posix())
    spec=json.loads((target/'launch.json').read_text());spec.update(probeSourceSHA=sha(source),probeJarSHA=sha(helper),probeMode=mode)
    spec['command']=[s for s in spec['command'] if not s.startswith(('-Dcritical.gui.small=','-Dcritical.gui.anchorOnly=','-Dcritical.gui.previewEvidence='))]
    spec.pop('previewEvidence',None)
    if mode in ['gt','loom']:
        spec['command'][spec['command'].index('--width')+1]='1280'
        spec['command'][spec['command'].index('--height')+1]='720'
    if mode in ['gt-small','gt-anchor']:
        spec['command'][spec['command'].index('--width')+1]='854'
        spec['command'][spec['command'].index('--height')+1]='480'
        spec['command'].insert(1,'-Dcritical.gui.small=true')
    if mode=='gt-anchor':spec['command'].insert(1,'-Dcritical.gui.anchorOnly=true')
    if preview_from:
        previous=OUT/preview_from
        evidence=json.loads((previous/'checks.json').read_text())
        old_receipt=json.loads((previous/'receipt.json').read_text())
        preview_checks=[c for c in evidence['checks'] if c['name'].startswith('preview.')]
        if old_receipt['candidateSHA']!=spec['sha256'] or not old_receipt['candidateBinding'] or not old_receipt['candidateUnchanged'] or not preview_checks or not all(c['pass'] for c in preview_checks) or not any(c['name']=='preview.matrix-27' for c in preview_checks):raise RuntimeError('Cannot reuse incomplete or different-candidate preview evidence')
        spec['command']=[s for s in spec['command'] if not s.startswith('-Dcritical.gui.previewEvidence=')]
        spec['command'].insert(1,'-Dcritical.gui.previewEvidence='+str(previous/'checks.json'))
        spec['previewEvidence']=str(previous/'checks.json')
    (target/'launch.json').write_text(json.dumps(spec,indent=2))

def run(phase):
    target=OUT/phase;spec=json.loads((target/'launch.json').read_text());fixture=Path(spec['cwd']).resolve()
    if not fixture.is_relative_to(OUT.resolve()):raise RuntimeError('Fixture outside private output root')
    if sha(fixture/'mods/production-gtsr.jar')!=spec['sha256']:raise RuntimeError('Prepared candidate SHA changed')
    if sha(fixture/'mods/critical-gui-lifecycle.jar')!=spec.get('probeJarSHA'):raise RuntimeError('Prepared helper SHA changed')
    processes=java_processes()
    # Store only PIDs and occupancy; unrelated launcher command lines may contain authentication.
    occupied=[p['ProcessId'] for p in processes if any(s in (p.get('CommandLine') or '').lower() for s in ['lwjgl3ify','minecraft_server','launchwrapper.launch','retrofuturabootstrap','gradlestart'])]
    (target/'preflight.json').write_text(json.dumps(dict(time=time.time(),occupiedPIDs=occupied)))
    if occupied:raise RuntimeError('Minecraft test runtime already occupied: '+str(occupied))
    fd=os.open(LEASE,os.O_CREAT|os.O_EXCL|os.O_WRONLY);os.write(fd,json.dumps(dict(pid=os.getpid(),fixture=str(fixture),created=time.time())).encode());os.close(fd)
    proc=None;start=time.time()
    try:
        if (target/'receipt.json').exists():
            attempt=target/('attempt-'+str(time.time_ns()));attempt.mkdir()
            for name in ['receipt.json','checks.json','stdout.log','pid.json']:
                if (target/name).exists():shutil.copy2(target/name,attempt/name)
            if (target/'checks.json').exists():(target/'checks.json').unlink()
        with (target/'stdout.log').open('wb') as log:
            proc=subprocess.Popen(spec['command'],cwd=fixture,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
            (target/'pid.json').write_text(json.dumps(dict(pid=proc.pid)))
            try:code=proc.wait(timeout=900)
            except subprocess.TimeoutExpired:proc.terminate();code=proc.wait(timeout=60)
        checks=json.loads((target/'checks.json').read_text(encoding='utf-8')) if (target/'checks.json').exists() else {}
        status=checks.get('status','NO_RECEIPT')
        from urllib.parse import unquote
        source=unquote(checks.get('productionCodeSource','')).lower()
        binding=str(fixture/'mods/production-gtsr.jar').replace('\\','/').lower() in source
        unchanged=sha(fixture/'mods/production-gtsr.jar')==spec['sha256']
        receipt=dict(status=status,exitCode=code,seconds=time.time()-start,pid=proc.pid,candidateSHA=spec['sha256'],probeSHA=spec.get('probeJarSHA'),candidateBinding=binding,candidateUnchanged=unchanged)
        (target/'receipt.json').write_text(json.dumps(receipt,indent=2));print(json.dumps(receipt))
        if code!=0 or not binding or not unchanged or status not in ['PASS','EXPECTED_FAILURE_REPRODUCED']:raise RuntimeError('Client lifecycle check failed')
    finally:
        if proc is None or proc.poll() is not None:LEASE.unlink()

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('action',choices=['prepare','finish','fork','compile','run']);parser.add_argument('phase',type=phase_name);parser.add_argument('--jar',type=Path);parser.add_argument('--from-phase',type=phase_name);parser.add_argument('--source-out',type=Path);parser.add_argument('--mode',choices=['legacy','gt','gt-small','gt-anchor','loom'],default='legacy');parser.add_argument('--preview-from',type=phase_name);parser.add_argument('--expect-legacy-failure',action='store_true');args=parser.parse_args()
    if args.action in ['prepare','finish']:
        if not args.jar:parser.error('--jar required')
        (prepare if args.action=='prepare' else finish)(args.phase,args.jar,args.expect_legacy_failure)
    elif args.action=='fork':
        if not args.from_phase:parser.error('--from-phase required')
        fork_fixture(args.phase,args.from_phase,args.source_out,args.jar)
    elif args.action=='compile':compile_probe(args.phase,args.mode,args.preview_from)
    else:run(args.phase)
