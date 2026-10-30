"""Execute the exact production turbine selection/debit methods at finite inventory boundaries.

Only the surrounding GT/world/API surface is a fixture. This is not a game or live AE-grid test.
Use --source with an unmodified baseline controller to prove the regression fails before repair.
"""
from pathlib import Path
import argparse
import hashlib
import json
import os
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_SOURCE = ROOT / 'src/main/java/com/miaokatze/gtsr/common/machine/MTEMegaSteamTurbineArray.java'


def method(source, signature):
    start = source.index(signature)
    body = source.index('{', start)
    depth = 1
    end = body + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]


SCAFFOLD = r'''
import java.util.*;
public class TurbineReceiptRegression {
  static int checks;
  static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
  static TurbineFixture machine(){return new TurbineFixture();}
  public static void main(String[] args){
    TurbineFixture ghost=machine(); ghost.visible.add(new FluidStack(SteamType.STEAM,1000));
    ghost.mFullBaseEUt=900; ghost.mEUt=900; ghost.mMaxProgresstime=1;
    check(ghost.checkProcessing()==CheckRecipeResultRegistry.NO_FUEL_FOUND,"unpaid visible ME stock must not generate");
    check(ghost.cooling==0&&ghost.mFullBaseEUt==0&&ghost.mEUt==0&&ghost.mMaxProgresstime==0,"failed debit clears stale generation and emits no coolant");
    TurbineFixture partial=machine();partial.visible.add(new FluidStack(SteamType.STEAM,1000));
    partial.mInputHatches.add(new FluidStack(SteamType.STEAM,400));
    check(partial.checkProcessing()==CheckRecipeResultRegistry.NO_FUEL_FOUND&&partial.cooling==0&&partial.mEUt==0,"short real receipt cannot authorize full generation");
    check(partial.mInputHatches.get(0).amount==0,"partial fixture reports actual 400L removed (not a rollback claim)");
    TurbineFixture shortSource=machine();shortSource.visible.add(new FluidStack(SteamType.STEAM,999));
    shortSource.mInputHatches.add(new FluidStack(SteamType.STEAM,999));
    check(shortSource.checkProcessing()==CheckRecipeResultRegistry.NO_FUEL_FOUND&&shortSource.mInputHatches.get(0).amount==999
      &&shortSource.cooling==0,"insufficient visible stock remains untouched");
    TurbineFixture physical=machine();physical.visible.add(new FluidStack(SteamType.STEAM,1000));
    physical.mInputHatches.add(new FluidStack(SteamType.STEAM,1000));
    check(physical.checkProcessing()==CheckRecipeResultRegistry.GENERATING&&physical.cooling==1000&&physical.mFullBaseEUt>0,"ordinary physical paid generation remains intact");
    TurbineFixture large=machine();large.voltage=300_000_000L;
    large.visible.add(new FluidStack(SteamType.STEAM,2_000_000_000));large.visible.add(new FluidStack(SteamType.STEAM,1_000_000_000));
    large.mInputHatches.add(new FluidStack(SteamType.STEAM,2_000_000_000));large.mInputHatches.add(new FluidStack(SteamType.STEAM,1_000_000_000));
    check(large.checkProcessing()==CheckRecipeResultRegistry.GENERATING&&large.cooling==3_000_000_000L,"long demand over Integer.MAX_VALUE remains fully charged");
    check(large.mInputHatches.stream().allMatch(f->f.amount==0),"large receipt consumes each finite tank exactly");
    System.out.println("PASS TurbineReceiptRegression checks="+checks+" (production methods; deterministic API seams)");
  }
}
enum SteamType { NONE, STEAM, DENSE_STEAM, SH_STEAM, DENSE_SH_STEAM, SC_STEAM, DENSE_SC_STEAM }
class SteamTurbineSteamTypes { static final SteamType[] PRIORITY={SteamType.DENSE_SC_STEAM,SteamType.SC_STEAM,SteamType.DENSE_SH_STEAM,SteamType.DENSE_STEAM,SteamType.SH_STEAM,SteamType.STEAM}; }
class FluidStack { final SteamType type;int amount;FluidStack(SteamType t,int n){type=t;amount=n;}FluidStack(FluidStack f,int n){this(f.type,n);} }
class MTEHatchPressureSteamInput {FluidStack fluid;FluidStack getFluid(){return fluid;}FluidStack drain(int n,boolean commit){int actual=Math.min(n,fluid.amount);if(commit)fluid.amount-=actual;return new FluidStack(fluid,actual);} }
class MTEOverpressureTurbineInputHatch {FluidStack fluid;FluidStack getFluid(){return fluid;}void consumeSteam(int n){fluid.amount-=n;} }
class CheckRecipeResult {}
class CheckRecipeResultRegistry {static final CheckRecipeResult NO_FUEL_FOUND=new CheckRecipeResult(),GENERATING=new CheckRecipeResult();}
class GTSRHatchFluidAccess {
  static int depleteFluidAcross(List<FluidStack> inputs,FluidStack request){int remaining=request.amount;for(FluidStack f:inputs)if(f.type==request.type){int take=Math.min(remaining,f.amount);f.amount-=take;remaining-=take;}return request.amount-remaining;}
}
class TurbineFixture {
  ArrayList<FluidStack> visible=new ArrayList<>();List<FluidStack> mInputHatches=new ArrayList<>();
  List<MTEHatchPressureSteamInput> mPressureSteamInputs=new ArrayList<>();List<MTEOverpressureTurbineInputHatch> mOverpressureInputs=new ArrayList<>();
  int mEUt,mTheoreticalEUt,mSteamConsumption,mMaxProgresstime,mEfficiencyIncrease,mEfficiency=10000,mCasingTier=6,mPowerParameter=10;
  long mFullBaseEUt,cooling,voltage=100;SteamType mSteamType=SteamType.NONE;
  ArrayList<FluidStack> getStoredFluids(){return new ArrayList<>(visible);}int getGroupCount(){return 1;}long getVoltage(){return voltage;}
  float getCustomEfficiency(){return 1;}float getSteamSavings(){return 0;}int getSingularityPowerMult(){return 1;}
  SteamType classifyFluid(FluidStack f){return f==null||f.amount<=0?SteamType.NONE:f.type;}
  float getEffectiveSteamEffFactor(SteamType t){return 1;}float getEffectiveSteamEuPerL(SteamType t){return 1;}
  int getMaxEfficiencyLimit(SteamType t){return 10000;}void outputCoolingProduct(SteamType t,long amount){cooling+=amount;}
  long getTotalSteamAmount(SteamType t,List<FluidStack> fluids){long total=0;for(FluidStack f:fluids)if(classifyFluid(f)==t)total+=f.amount;return total;}
  // PRODUCTION_METHODS
}
'''


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source',type=Path,default=DEFAULT_SOURCE)
    parser.add_argument('--out',type=Path,default=ROOT/'build/turbine-receipt-regression')
    parser.add_argument('--java-home',type=Path,default=Path(os.environ['JAVA_HOME']) if 'JAVA_HOME' in os.environ else None)
    args=parser.parse_args()
    source=args.source.read_text(encoding='utf-8')
    methods='\n'.join(method(source,s) for s in ['public CheckRecipeResult checkProcessing()', 'private boolean depleteSteamByType(SteamType type, long amount)'])
    out=args.out.resolve();out.mkdir(parents=True,exist_ok=True)
    generated=out/'TurbineReceiptRegression.java';generated.write_text(SCAFFOLD.replace('// PRODUCTION_METHODS',methods),encoding='utf-8')
    def exe(name):
        result=str(args.java_home/'bin'/name) if args.java_home else shutil.which(name)
        if not result: raise RuntimeError('Set --java-home or JAVA_HOME for JDK17+')
        return result
    commands=[('compile',[exe('javac'),'-encoding','UTF-8','-d',str(out),str(generated)]),('test',[exe('java'),'-cp',str(out),'TurbineReceiptRegression'])]
    receipt={'source':str(args.source.resolve()),'sourceSHA256':hashlib.sha256(source.encode()).hexdigest(),
      'scope':'Exact production checkProcessing/depleteSteamByType methods; finite fake API inventories; no game, live AE, actual native hatch, or rollback claim.'}
    for name,cmd in commands:
        result=subprocess.run(cmd,capture_output=True,text=True)
        (out/(name+'.log')).write_text(result.stdout+result.stderr,encoding='utf-8')
        print(result.stdout+result.stderr,end='')
        receipt[name+'ExitCode']=result.returncode
        if result.returncode:
            (out/'receipt.json').write_text(json.dumps(receipt,indent=2));raise SystemExit(result.returncode)
    (out/'receipt.json').write_text(json.dumps(receipt,indent=2))


if __name__=='__main__': main()
