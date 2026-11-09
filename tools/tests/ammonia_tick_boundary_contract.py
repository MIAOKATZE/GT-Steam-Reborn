"""Verbatim production ammonia tick boundaries with a controlled native-super completion endpoint; no game."""
from pathlib import Path
import argparse,hashlib,json,re,subprocess
ROOT=Path(__file__).resolve().parents[2]
SOURCE=ROOT/'src/main/java/com/miaokatze/gtsr/common/machine/MTEAmmoniaPlant.java'

def method(source,name):
    match=re.search(r'(?m)^    public [^\n]*\b'+name+r'\([^;]*?\)\s*\{',source)
    start=source.index('{',match.start());depth=1;end=start+1
    while depth:
        depth+=(source[end]=='{')-(source[end]=='}');end+=1
    return source[match.start():end]

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();a.out.mkdir(parents=True,exist_ok=True)
    source=SOURCE.read_text(encoding='utf-8');methods='\n'.join(method(source,name) for name in ['onRunningTick','onPostTick'])
    fixture=r'''
import java.util.*;
class Parent {
 protected int mMaxProgresstime,progress;boolean startRecipe;
 public boolean onRunningTick(AmmoniaTickBoundaryContract.ItemStack stack){return true;}
 public void onPostTick(AmmoniaTickBoundaryContract.IGregTechTileEntity tile,long tick){
  if(mMaxProgresstime>0){if(onRunningTick(null)&&++progress>=mMaxProgresstime){mMaxProgresstime=0;progress=0;}}
  else if(startRecipe){mMaxProgresstime=1280;progress=0;startRecipe=false;}
 }
}
public final class AmmoniaTickBoundaryContract extends Parent {
 static class ItemStack{}
 static class IGregTechTileEntity {boolean isServerSide(){return true;}boolean isAllowedToWork(){return true;}}
 static final int HEAT_MAX=10000,HEAT_INCREASE_PER_SEC=10,HEAT_DECREASE_PER_SEC=50,
 PREHEAT_GAS_PER_TICK=10,PREHEAT_STEAM_PER_TICK=400,PREHEAT_STEAM_PER_SEC=8000,
 MAINTAIN_GAS_PER_TICK=10,MAINTAIN_STEAM_PER_TICK=600,MAINTAIN_STEAM_PER_SEC=12000,EXTRA_STEAM_FORMULA_CONSTANT=16000;
 int mHeatLevel=10000,mParallelCount=64,mReactionTimeSec=64,mStartUpCheck=100,attempts,stops;
 long mBatchExtraSteamPerSec=16000,mRealtimeSteamCost,mRealtimeSteamOutput,pending;
 boolean mMachine=true,sufficient=true;List<String> paid=new ArrayList<>();
 IGregTechTileEntity tile=new IGregTechTileEntity();
 IGregTechTileEntity getBaseMetaTileEntity(){return tile;}
 boolean consumeFuelAndSteam(int gas,int steam){attempts++;if(!sufficient)return false;paid.add(gas+"/"+steam);pending+=steam;return true;}
 void updateCatalyst(){} void pushSuperheatedSteam(int amount){} void adjustHeat(int amount){mHeatLevel+=amount;}
 void stopMachine(){stops++;mMaxProgresstime=0;}
 __METHODS__
 static int checks;static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
 public static void main(String[] args){
  AmmoniaTickBoundaryContract m=new AmmoniaTickBoundaryContract();m.startRecipe=true;m.onPostTick(m.tile,1);
  check(m.mMaxProgresstime==1280&&m.progress==0&&m.paid.isEmpty(),"recipe start does not charge an unadvanced tick");
  m.onPostTick(m.tile,2);check(m.progress==1&&m.paid.equals(List.of("10/1400")),"running tick has one paired charge");
  m.progress=1279;m.paid.clear();m.pending=0;m.onPostTick(m.tile,3);
  check(m.mMaxProgresstime==0&&m.progress==0&&m.paid.equals(List.of("10/1400")),"completion tick cannot repeat idle charge");
  check(m.pending==1400,"completion backpressure receives one thermal output obligation");
  m.paid.clear();m.onPostTick(m.tile,4);
  check(m.paid.equals(List.of("10/600"))&&m.pending==2000,"next idle tick still pays one normal maintenance pair");
  m.mMaxProgresstime=1280;m.progress=7;m.sufficient=false;m.paid.clear();m.onPostTick(m.tile,5);
  check(m.mMaxProgresstime==1280&&m.progress==7&&m.paid.isEmpty()&&m.stops==0,"unpaid active tick pauses without abort or idle fallback charge");
  System.out.println("PASS ammonia production tick boundary contracts checks="+checks);
 }
}
'''.replace('__METHODS__',methods)
    java=a.out/'AmmoniaTickBoundaryContract.java';java.write_text(fixture,encoding='utf-8');receipt={'boundary':'Verbatim production onPostTick/onRunningTick; controlled parent marks real-super recipe-start/completion boundaries and controlled paired-payment/side-output endpoints. No native world/ticks or actual IO claim.','sourceSha256':hashlib.sha256(SOURCE.read_bytes()).hexdigest(),'commands':[]}
    for name,cmd in [('compile',['javac','-encoding','UTF-8','-d',str(a.out),str(java)]),('run',['java','-cp',str(a.out),'AmmoniaTickBoundaryContract'])]:
        r=subprocess.run(cmd,capture_output=True,timeout=20);text=(r.stdout+r.stderr).decode('utf-8',errors='replace');(a.out/(name+'.log')).write_text(text,encoding='utf-8');receipt['commands'].append({'name':name,'exitCode':r.returncode,'output':text});print(text,end='')
        if r.returncode:break
    receipt['status']='PASS' if all(x['exitCode']==0 for x in receipt['commands']) and len(receipt['commands'])==2 else 'FAIL';(a.out/'receipt.json').write_text(json.dumps(receipt,indent=2),encoding='utf-8');assert receipt['status']=='PASS'
if __name__=='__main__':main()
