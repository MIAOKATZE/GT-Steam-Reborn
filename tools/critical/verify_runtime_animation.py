"""Actual energy visual descriptor, anchor packet, renderer mode and pure animation samplers; no game startup."""
from pathlib import Path
import argparse
import json
import os
import subprocess
import verify_v32 as fixture

ROOT=fixture.ROOT


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar',type=Path)
    parser.add_argument('--solar-receipt',type=Path,required=True)
    parser.add_argument('--out',type=Path,default=ROOT/'build/critical-runtime-animation')
    args=parser.parse_args()
    out=args.out.resolve();out.relative_to(ROOT/'build')
    classes=out/'classes';gates=out/'gates'
    classes.mkdir(parents=True,exist_ok=True);gates.mkdir(exist_ok=True)
    log_config=out/'log4j2.xml'
    log_config.write_text('<Configuration status="OFF"><Appenders/><Loggers><Root level="off"/></Loggers></Configuration>',encoding='utf-8')
    jars=list(fixture.CACHE.glob('*/*/*/*/*.jar'))
    gt=[p for p in jars if 'GT5-Unofficial/5.09.54.205/' in p.as_posix()]
    lwjgl=[p for p in jars if '/org.lwjgl.lwjgl/lwjgl/' in p.as_posix() and p.name.endswith('.jar')]
    sources=[ROOT/'tools/critical'/name for name in ('CriticalRuntimeAnimationContract.java','CriticalRuntimeAnimationProbe.java','CriticalV32Contract.java','CriticalV32Fixture.java')]
    if not args.jar:
        sources+=fixture.PRODUCTION+[ROOT/'src/main/java/com/miaokatze/gtsr/client/critical'/name for name in (
            'CriticalControllerRenderer.java','CriticalAnimation.java','CriticalMechanicalPose.java','CriticalContinuousMotion.java','CriticalSolarFX.java')]
    baseline=max((ROOT/'build/libs').glob('*-dev.jar'),key=lambda p:p.stat().st_mtime)
    product=[args.jar.resolve()] if args.jar else [ROOT/'build/classes/java/main',baseline]
    cp=os.pathsep.join(map(str,[classes,*product,*gt,*lwjgl,ROOT/'build/classes/java/patchedMc',ROOT/'src/main/resources',*jars]))
    before=fixture.binding(sources)

    def run(exe,name,arguments):
        argfile=out/(name+'.args')
        argfile.write_text('\n'.join('"'+str(v).replace('\\','/')+'"' for v in arguments),encoding='utf-8')
        result=subprocess.run([str(fixture.JAVA/(exe+'.exe')),'@'+str(argfile)],capture_output=True,
            creationflags=subprocess.CREATE_NO_WINDOW,timeout=180)
        output=(result.stdout+result.stderr).decode('utf-8',errors='replace')
        (out/(name+'.log')).write_text(output,encoding='utf-8')
        if result.returncode:raise RuntimeError(name+' failed:\n'+output[-7000:])
        return output

    run('javac','compile',['-encoding','UTF-8','-proc:none','-cp',cp,'-d',classes,*sources])
    run('javac','gates',['-encoding','UTF-8','-proc:none','-cp',cp,'-d',gates,*fixture.GATES])
    output=run('java','run',['-Djava.awt.headless=true','-Dfile.encoding=UTF-8','-Dlog4j.configurationFile='+str(log_config),
        '-cp',str(gates)+os.pathsep+cp,'com.miaokatze.gtsr.common.critical.CriticalRuntimeAnimationContract',args.solar_receipt.resolve()])
    if 'PASS CriticalRuntimeAnimationContract checks=' not in output:raise RuntimeError('No explicit PASS receipt')
    assert before==fixture.binding(sources),'Sources changed during animation contract'
    print(output.strip())
    (out/'verification.json').write_text(json.dumps(dict(status='PASS',result=output.strip(),sourceSHA256=before,
        jar=str(args.jar) if args.jar else None,solarReceiptSHA256=fixture.sha(args.solar_receipt),
        scope='Real State/anchor S35/client interpolation, recovered-funded Turbine/Battery tick, renderer mode and pure approved pose/FX. '
              'Solar receipt comes from actual finite-hatch startSolar; its offline progress/delivery fields use setRuntimeBatch. '
              'No complete FML solar/night-clock execution or rendered GL claim.'),indent=2),encoding='utf-8')


if __name__=='__main__':main()
