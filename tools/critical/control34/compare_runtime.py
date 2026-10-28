"""Compare the real-production evidence candidate against the final UI candidate."""
from pathlib import Path
import hashlib,json,re,subprocess,zipfile

ROOT=Path(__file__).resolve().parents[3]
OLD=ROOT/'build/v2034-release/candidate-runtime.jar'
FINAL=ROOT/'build/v2034-release/final-candidate-runtime.jar'
OUT=ROOT/'plan/implementation/terminal-controls-v2034/runtime-evidence-inheritance.json'
JAVAP=Path.home()/'.gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin/javap.exe'

def sha(data):return hashlib.sha256(data).hexdigest()
def disassemble(jar,cls):
    p=subprocess.run([str(JAVAP),'-classpath',str(jar),'-p','-c',cls],capture_output=True,encoding='utf-8',errors='replace',check=True)
    return p.stdout
def method(text,signature):
    start=text.index('  '+signature+';')
    end=re.search(r'\n  (?:public |private |protected |static |[a-zA-Z_$][^\n]*\([^\n]*\);)',text[start+3:])
    result=text[start:start+3+end.start()] if end else text[start:]
    return re.sub(r'#\d+','#',result).strip()

def main():
    exact={}
    with zipfile.ZipFile(OLD) as old,zipfile.ZipFile(FINAL) as final:
        for name in ['CriticalEnergyRuntime','CriticalEnergyPorts','CriticalSolarPolicy','CriticalStellarRuntime','CriticalStellarPolicy']:
            key='com/miaokatze/gtsr/common/critical/'+name+'.class';a,b=old.read(key),final.read(key)
            exact[name]=dict(identical=a==b,r1SHA256=sha(a),finalSHA256=sha(b))
    methods={}
    cls='com.miaokatze.gtsr.common.critical.CriticalMachineState';old,final=disassemble(OLD,cls),disassemble(FINAL,cls)
    for signature in ['public void updateEntity()','private void runBatch()','private void emitEnergy()','private gregtech.api.interfaces.tileentity.IGregTechTileEntity productionController()','public boolean isEnabled()','public boolean isProductionEnabled()','public boolean isConstructionEnabled()','public boolean setEnabled(net.minecraft.entity.player.EntityPlayer, boolean)','public boolean setProductionEnabled(net.minecraft.entity.player.EntityPlayer, boolean)','public boolean isWorking()']:
        a,b=method(old,signature),method(final,signature)
        methods[signature]=dict(identicalIgnoringConstantPoolIndexes=a==b,r1NormalizedSHA256=sha(a.encode()),finalNormalizedSHA256=sha(b.encode()))
    result=dict(status='PASS' if all(x['identical'] for x in exact.values()) and all(x['identicalIgnoringConstantPoolIndexes'] for x in methods.values()) else 'FAIL',r1JarSHA256=sha(OLD.read_bytes()),finalJarSHA256=sha(FINAL.read_bytes()),exactClasses=exact,stateBusinessMethods=methods,scope='R1 real full construction and400tick IO evidence inherits to final candidate only for unchanged native runtime classes and listed production/power methods; final presentation getters and GUI are revalidated in R2')
    OUT.write_text(json.dumps(result,indent=2),encoding='utf-8');print(json.dumps(result,indent=2))

if __name__=='__main__':main()
