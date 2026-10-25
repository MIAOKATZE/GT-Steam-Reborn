"""Freeze a bounded v2012 visual plan and offline launch specification; never launches a client."""
from pathlib import Path
import hashlib
import json

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / "build/v2012-ui-qa"
SANDBOX = ROOT / "build/client-testlab-mui2-v12138"
PREFIX = "gtsr.critical.nei.simple."


def label(x, y, key, color="ADBDC6"):
    return dict(x=x, y=y, key=PREFIX+key, color=color)


def main():
    OUT.mkdir(exist_ok=True)
    spec=json.loads((ROOT/"build/v208-ui-qa/client-launch.json").read_text(encoding="utf-8"))
    cmd=spec["command"]
    assert Path(cmd[cmd.index("--gameDir")+1]).resolve()==SANDBOX.resolve()
    assert cmd[cmd.index("--accessToken")+1]=="0"
    spec["command"]=["-Xmx3G" if arg.startswith("-Xmx") else arg for arg in cmd]
    spec["scope"]="One owned offline client on a private hidden desktop; heap3GiB/reserve2GiB; preparation never launches"
    (OUT/"client-launch.json").write_text(json.dumps(spec,indent=2),encoding="utf-8")
    rows=[]

    def add(name,kind,operation,**extra):
        build=operation.startswith("build_")
        catalogue="LOOM" if build else "IRRADIATION" if operation=="solar_dust" else kind
        labels=[label(8,6,"build.title" if build else operation+".title","EAC879")]
        if build:
            labels += [label(8,20,"build.target."+kind.lower(),"E1E8EB"),label(8,150,"build.stage."+operation[6:],"E1E8EB"),label(8,162,"build.time")]
        else:
            labels += [label(8,21,"intro."+operation),label(8,32,"input"),label(144,32,"energy" if operation.startswith("chemical_") else "output")]
            keys=(['solar.cycle','solar.stock','solar.dimension','solar.salt' if operation=='solar_salt' else 'solar.water','solar.night'] if operation.startswith('solar_') and operation!='solar_dust'
                  else ['irradiation.cycle','irradiation.parallel','irradiation.shared','irradiation.no_energy','irradiation.output'] if operation=='solar_dust'
                  else [None,'turbine.efficiency','turbine.cycle','turbine.condensation','turbine.wireless'] if operation=='turbine'
                  else [None,'fusion.parallel','fusion.native','fusion.discount','fusion.sustain'] if operation=='sun'
                  else [None,'chemical.ratio.1','chemical.cap.1','chemical.heat','chemical.cycle'])
            labels += [label(8,97+i*13,key,'EAC879' if i==0 else 'ADBDC6' if i==4 else 'E1E8EB') for i,key in enumerate(keys) if key]
        row=dict(id="v2012."+name,name="v2012."+name,v2011=True,v2012=True,kind="nei-"+catalogue.lower(),variant="visual",visual="nei",neiKind=catalogue,machineKind=kind,operation=operation,
                 language="zh_CN",width=1280,height=720,scale=2,minimumFrames=2,minimumDrawTicks=3,timeoutSeconds=40,labels=labels,
                 route="Actual registered GT recipe map/cached recipe/native NEI slots and renderer",note="Read-only visual capture; no construction, payment, fuel consumption or business transaction")
        row.update(extra);rows.append(row)

    for name,kind,operation in [('boiler.water','SOLAR','solar_water'),('boiler.salt','SOLAR','solar_salt'),('irradiation','SOLAR','solar_dust'),
                                ('turbine','TURBINE','turbine'),('stellar.fusion','SUN','sun'),('chemical.mixture','BATTERY','chemical_window_1'),
                                ('construction.body','SUN','build_4'),('construction.equipment','SUN','build_5')]:
        add(name,kind,operation)
    add('turbine.later','TURBINE','turbine',minimumDrawTicks=17,animationPair='turbine')
    add('turbine.reduced.a','TURBINE','turbine',minimumDrawTicks=3,reducedMotion=True)
    add('turbine.reduced.b','TURBINE','turbine',minimumDrawTicks=17,reducedMotion=True)
    # All tooltip pages originate in the real registered ItemMachines stack; native operation text is used without replacement.
    for name,entry in [('early','LargeSteamFurnace'),('advanced','CriticalSun'),('node','SteamCacheNode'),('small-port','SteamHubInputHatch'),('cluster','ClusterController')]:
        add('tooltip.'+name,'SOLAR','solar_water',machineTooltipEntry=entry,width=1600,height=1000,tooltipX=30,tooltipY=22)
    add('tooltip.en.longest','SOLAR','solar_water',machineTooltipEntry='__LONGEST__',language='en_US',width=1920,height=1440,tooltipX=30,tooltipY=22)
    add('en.chemical.mixture','BATTERY','chemical_window_1',language='en_US',width=1280,height=720)
    plan=OUT/'gallery-v2012-compact.json'
    plan.write_text(json.dumps(rows,ensure_ascii=False,indent=2),encoding='utf-8')
    sources=[ROOT/'src/main/java/com/miaokatze/gtsr'/p for p in ['client/nei/CriticalNEIShowcase.java','api/recipe/CriticalNEILayout.java','api/recipe/CriticalRecipeFrontend.java','common/critical/CriticalTooltips.java','client/tooltip/GTSRTooltipHandler.java']]
    manifest=dict(sha256=hashlib.sha256(plan.read_bytes()).hexdigest(),captures=len(rows),status='PREPARED_NOT_LAUNCHED',hiddenDesktopRequired=True,heapGiB=3,reserveGiB=2,
                  physicalInput='NOT_RUN',businessTransactions='NOT_RUN',sourceSHA256={str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources})
    plan.with_suffix('.manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
    print(json.dumps(dict(plan=str(plan),**manifest),indent=2))


if __name__=='__main__':main()
