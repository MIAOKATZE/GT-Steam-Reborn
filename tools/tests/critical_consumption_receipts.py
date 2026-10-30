"""Execute production consumption methods against deterministic, finite input boundaries.

The copied method bodies are not reimplemented. Surrounding MC/GT/AE inventories are explicit
fixtures; this does not simulate a live AE grid, native recipe lifecycle, or complete controller.
The full edited production sources must also be compiled separately against native dependencies.
Use --baseline-dir with source copies named <Class>.baseline.java to reproduce pre-fix failures.
"""
from pathlib import Path
import argparse
import hashlib
import json
import os
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'src/main/java/com/miaokatze/gtsr/common'
PATHS = {
    'CriticalHatchFunding': SOURCE / 'critical/CriticalHatchFunding.java',
    'CriticalNativeMEFunding': SOURCE / 'critical/CriticalNativeMEFunding.java',
    'MTEThermoChemicalDenseSteamGenerator': SOURCE / 'machine/tcds/MTEThermoChemicalDenseSteamGenerator.java',
}


def block(source, signature):
    start = source.index(signature)
    body = source.index('{', start)
    depth, end = 1, body + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]


AGGREGATE = r'''
import java.util.*;
import java.math.BigInteger;
public class AggregateReceiptRegression {
  static int checks, failures;
  static final Object ITEM = new Object();
  static ItemStack wanted(){ return new ItemStack(ITEM,1); }
  static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
  static void scenario(String name,Runnable test){try{test.run();System.out.println("PASS "+name);}catch(AssertionError e){failures++;System.out.println("FAIL "+name+": "+e.getMessage());}}
  static MTEHatchInputBus physical(int n){MTEHatchInputBus b=new MTEHatchInputBus();b.items[0]=new ItemStack(ITEM,n);return b;}
  static CriticalNativeMEFunding.ItemConsumption consume(MTEHatchInputBus... b){return CriticalHatchFunding.consumeAllItem(new MTEMultiBlockBase(),Arrays.asList(b),wanted());}
  static int left(MTEHatchInputBus b){return b.items[0]==null?0:b.items[0].stackSize;}
  static BigInteger paid(Pool... p){BigInteger total=BigInteger.ZERO;for(Pool x:p)total=total.add(x.paid);return total;}
  static MTEHatchCriticalInputBusME critical(Pool p){return new MTEHatchCriticalInputBusME(new Source(p));}
  public static void main(String[] args){
    scenario("network-max-plus-physical",()->{
      Pool p=new Pool(Long.MAX_VALUE);MTEHatchInputBus b=physical(1);
      var r=consume(critical(p),b);
      check(r.result==CriticalNativeMEFunding.Result.SUCCESS&&r.amount==Long.MAX_VALUE,"receipt must not wrap negative");
      check(left(b)==1&&p.stock==0,"unrepresentable physical excess stays in source");
    });
    scenario("partially-fitting-physical",()->{
      Pool p=new Pool(Long.MAX_VALUE-5);MTEHatchInputBus b=physical(9);
      var r=consume(critical(p),b);
      check(r.result==CriticalNativeMEFunding.Result.SUCCESS&&r.amount==Long.MAX_VALUE,"bounded mixed receipt");
      check(left(b)==4&&p.stock==0,"only five physical items fit after network debit");
    });
    scenario("two-max-networks",()->{
      Pool a=new Pool(Long.MAX_VALUE),b=new Pool(Long.MAX_VALUE);
      var r=consume(critical(a),critical(b));
      check(r.result==CriticalNativeMEFunding.Result.SUCCESS&&r.amount==Long.MAX_VALUE,"multiple valid networks must not lock after overflowing debit");
      check(paid(a,b).equals(BigInteger.valueOf(r.amount)),"receipt equals actual debit, not saturated already-consumed total");
      check(BigInteger.valueOf(a.stock).add(BigInteger.valueOf(b.stock)).equals(BigInteger.valueOf(Long.MAX_VALUE)),"excess network stock retained");
    });
    scenario("partially-fitting-second-network",()->{
      Pool a=new Pool(Long.MAX_VALUE-5),b=new Pool(9);
      var r=consume(critical(a),critical(b));
      check(r.result==CriticalNativeMEFunding.Result.SUCCESS&&r.amount==Long.MAX_VALUE,"network request capped before either iteration order overpays");
      check(paid(a,b).equals(BigInteger.valueOf(r.amount))&&a.stock+b.stock==4,"all paid stock exactly represented");
    });
    scenario("native-int-and-long-network",()->{
      Pool p=new Pool(Long.MAX_VALUE);MTEHatchInputBusME nativeBus=new MTEHatchInputBusME();nativeBus.items[0]=new ItemStack(ITEM,Integer.MAX_VALUE);
      var r=consume(critical(p),nativeBus);
      check(r.result==CriticalNativeMEFunding.Result.SUCCESS&&r.amount==Long.MAX_VALUE,"native int windows share long receipt budget");
      check(p.paid.add(BigInteger.valueOf(nativeBus.paid)).equals(BigInteger.valueOf(r.amount)),"native committed delta plus long debit equals receipt");
      check(nativeBus.opens==1&&nativeBus.closes==1,"native window still closed exactly once");
    });
    scenario("duplicate-network-views",()->{
      Pool p=new Pool(9);MTEHatchCriticalInputBusME a=critical(p),b=critical(p);b.source.limit=5;
      var r=consume(a,b,a);
      check(r.result==CriticalNativeMEFunding.Result.SUCCESS&&r.amount==9&&p.stock==0,"same network is charged once at greatest available view");
      check(p.paid.equals(BigInteger.valueOf(9)),"duplicate exposure does not duplicate receipt");
    });
    scenario("ordinary-and-circuit-slots",()->{
      MTEHatchInputBus p=physical(64);p.items[1]=new ItemStack(ITEM,99);
      var r=consume(p,p);
      check(r.result==CriticalNativeMEFunding.Result.SUCCESS&&r.amount==64&&left(p)==0,"physical bus identity deduplicated");
      check(p.items[1].stackSize==99,"circuit slot untouched");
    });
    scenario("debug-budget",()->{
      Pool p=new Pool(Long.MAX_VALUE-1);MTEHatchInputBusDebug debug=new MTEHatchInputBusDebug();debug.items[0]=new ItemStack(ITEM,3);
      var r=consume(critical(p),debug);
      check(r.result==CriticalNativeMEFunding.Result.SUCCESS&&r.amount==Long.MAX_VALUE,"debug phantom count cannot overflow receipt");
      check(debug.removals==0&&left(debug)==3,"debug input is still nonconsuming");
    });
    scenario("short-long-receipt",()->{
      Pool p=new Pool(9);p.maxCommit=4;MTEHatchInputBus b=physical(3);
      var r=consume(critical(p),b);
      check(r.result==CriticalNativeMEFunding.Result.COMMIT_FAILED&&r.amount==4&&p.stock==5,"short actual receipt stays uncertain");
      check(left(b)==3,"no physical debit after failed native payment");
    });
    scenario("throwing-long-source",()->{
      Pool p=new Pool(9);p.throwOnCommit=true;MTEHatchInputBus b=physical(3);
      var r=consume(critical(p),b);
      check(r.result==CriticalNativeMEFunding.Result.COMMIT_FAILED&&r.amount==0&&left(b)==3,"throwing debit quarantined, never counted as payment");
    });
    scenario("native-close-failure",()->{
      MTEHatchInputBusME p=new MTEHatchInputBusME();p.items[0]=new ItemStack(ITEM,9);p.closeSuccess=false;MTEHatchInputBus b=physical(3);
      var r=consume(p,b);
      check(r.result==CriticalNativeMEFunding.Result.COMMIT_FAILED&&left(b)==3&&p.closes==1,"failed native close does not authorize physical payment");
    });
    scenario("ordinary-small-mixed",()->{
      Pool p=new Pool(9);MTEHatchInputBus b=physical(3);var r=consume(critical(p),b);
      check(r.result==CriticalNativeMEFunding.Result.SUCCESS&&r.amount==12&&p.stock==0&&left(b)==0,"normal consume-all quantity is unchanged");
    });
    System.out.println("AggregateReceiptRegression checks="+checks+" failures="+failures);
    if(failures>0)throw new AssertionError("aggregate scenarios failed="+failures);
  }
}
class ItemStack {final Object item;int stackSize;ItemStack(Object i,int n){item=i;stackSize=n;}Object getItem(){return item;}int getItemDamage(){return 0;}static boolean areItemStackTagsEqual(ItemStack a,ItemStack b){return true;}}
class Tile {int dirty;void markDirty(){dirty++;}}
class MTEHatchInputBus {ItemStack[] items=new ItemStack[2];Tile tile=new Tile();int removals;boolean isValid(){return true;}int getSizeInventory(){return items.length;}int getCircuitSlot(){return 1;}ItemStack getStackInSlot(int slot){return slot<items.length?items[slot]:null;}ItemStack decrStackSize(int slot,int n){removals++;ItemStack ref=items[slot];int taken=Math.min(n,ref.stackSize);ref.stackSize-=taken;if(ref.stackSize==0)items[slot]=null;return new ItemStack(ref.item,taken);}Tile getBaseMetaTileEntity(){return tile;}}
class MTEHatchInputBusDebug extends MTEHatchInputBus {}
interface IRecipeProcessingAwareHatch {}
class MTEMultiBlockBase {void updateSlots(){}}
class GridAccessException extends Exception {}
class CheckRecipeResult {boolean success;CheckRecipeResult(boolean s){success=s;}boolean wasSuccessful(){return success;}}
class Storage {Object token=new Object();Object getItemInventory(){return token;}}
class Proxy {Storage storage=new Storage();Storage getStorage() throws GridAccessException {return storage;}}
class MTEHatchInputBusME extends MTEHatchInputBus implements IRecipeProcessingAwareHatch {
  static final int SLOT_COUNT=16;Proxy proxy=new Proxy();int opens,closes;long initial,paid;boolean closeSuccess=true;
  boolean isAllowedToWork(){return true;}Proxy getProxy(){return proxy;}
  void startRecipeProcessing(){opens++;initial=items[0]==null?0:items[0].stackSize;}
  CheckRecipeResult endRecipeProcessing(MTEMultiBlockBase host){closes++;paid+=initial-(items[0]==null?0:items[0].stackSize);return new CheckRecipeResult(closeSuccess);}
}
class Pool {long stock,maxCommit=Long.MAX_VALUE;boolean throwOnCommit;BigInteger paid=BigInteger.ZERO;Pool(long n){stock=n;}}
class Source implements CriticalEnergyPorts.LongItemSource {
  Pool pool;long limit=Long.MAX_VALUE;Source(Pool p){pool=p;}public Object networkIdentity(){return pool;}
  public ItemStack prototype(){return AggregateReceiptRegression.wanted();}public long available(){return Math.min(limit,pool.stock);}
  public long extract(long amount,boolean commit){if(pool.throwOnCommit)throw new IllegalStateException("uncertain source");long n=Math.min(amount,Math.min(pool.stock,pool.maxCommit));if(commit){pool.stock-=n;pool.paid=pool.paid.add(BigInteger.valueOf(n));}return n;}
}
class MTEHatchCriticalInputBusME extends MTEHatchInputBusME {Source source;MTEHatchCriticalInputBusME(Source s){source=s;}CriticalEnergyPorts.LongItemSource getLongInputSource(int slot){return slot==0?source:null;}}
class CriticalEnergyPorts {interface LongItemSource {Object networkIdentity();ItemStack prototype();long available();long extract(long wanted,boolean commit);}}
class CriticalNativeMEFunding { // NATIVE_METHODS
}
class CriticalHatchFunding { // PHYSICAL_METHODS
}
'''


TCDS = r'''
import java.util.*;
public class TcdsReceiptRegression {
  static int checks,failures;
  static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
  static void scenario(String name,Runnable test){try{test.run();System.out.println("PASS "+name);}catch(AssertionError e){failures++;System.out.println("FAIL "+name+": "+e.getMessage());}}
  public static void main(String[] args){
    for(String failed:new String[]{"gas","liquid","air","water"}) scenario("short-"+failed,()->{
      TcdsFixture f=new TcdsFixture();f.dual=true;f.mSteamEquivalentBuffer=123;f.mMaxProgresstime=1;f.mCurrentOutputEquivalent=999;
      f.inputs.shortFluid=failed;
      CheckRecipeResult result=f.checkProcessing();
      check(result==CheckRecipeResultRegistry.NO_RECIPE,"short "+failed+" receipt must fail production");
      check(f.mSteamEquivalentBuffer==123&&f.mCurrentOutputEquivalent==0,"short receipt grants no new steam, preserves previously paid buffer");
      check(f.mMaxProgresstime==0,"failed debit does not retain running heat state");
      int position=Arrays.asList("gas","liquid","air","water").indexOf(failed);
      check(f.inputs.calls.size()==position+1,"no later ingredient charged after first short receipt");
      check(f.inputs.paid.get(failed)<f.inputs.requested.get(failed),"finite fixture records real short payment, not rollback");
    });
    scenario("visible-but-unpowered",()->{
      TcdsFixture f=new TcdsFixture();f.inputs.power=0;
      check(f.checkProcessing()==CheckRecipeResultRegistry.NO_RECIPE&&f.mSteamEquivalentBuffer==0,"stock preview without extraction energy cannot generate steam");
      check(f.inputs.calls.size()==1&&f.inputs.paid.get("gas")==0,"zero-paid prefix stops later extraction");
    });
    scenario("shared-power-runs-out",()->{
      TcdsFixture f=new TcdsFixture();f.dual=true;f.inputs.power=150;
      check(f.checkProcessing()==CheckRecipeResultRegistry.NO_RECIPE&&f.mSteamEquivalentBuffer==0,"independent stock previews do not share an infinite power budget");
      check(f.inputs.paid.get("gas")==100&&f.inputs.paid.get("liquid")==50,"known partial prefix remains consumed, no fictitious refund");
    });
    scenario("paid-gas",()->{
      TcdsFixture f=new TcdsFixture();
      check(f.checkProcessing()==CheckRecipeResultRegistry.SUCCESSFUL,"fully paid single fuel remains successful");
      check(f.mSteamEquivalentBuffer==70_000&&f.mCurrentOutputEquivalent==70_000&&f.mCurrentWaterConsumption==438,"existing output and water rounding preserved");
      check(f.inputs.paid.get("gas")==100&&f.inputs.paid.get("air")==10_000&&f.inputs.paid.get("water")==438,"all successful ingredients really paid");
    });
    scenario("paid-dual",()->{
      TcdsFixture f=new TcdsFixture();f.dual=true;
      check(f.checkProcessing()==CheckRecipeResultRegistry.SUCCESSFUL&&f.mSteamEquivalentBuffer==160_000,"dual output arithmetic unchanged");
      check(f.mSuperheatedTier&&f.mCurrentGasConsumption==100&&f.mCurrentLiquidConsumption==100&&f.mCurrentAirConsumption==20_000,"successful quality and display state retained");
    });
    scenario("preview-shortage",()->{
      TcdsFixture f=new TcdsFixture();f.inputs.visible.put("air",1);
      check(f.checkProcessing()==CheckRecipeResultRegistry.NO_RECIPE&&f.inputs.calls.isEmpty()&&f.mSteamEquivalentBuffer==0,"air preview shortage does not consume fuel");
    });
    scenario("buffer-full",()->{
      TcdsFixture f=new TcdsFixture();f.mSteamEquivalentBuffer=TcdsFixture.STEAM_BUFFER_CAPACITY;
      check(f.checkProcessing()==CheckRecipeResultRegistry.NO_RECIPE&&f.inputs.calls.isEmpty(),"full buffer still prevents payment");
    });
    System.out.println("TcdsReceiptRegression checks="+checks+" failures="+failures);
    if(failures>0)throw new AssertionError("TCDS scenarios failed="+failures);
  }
}
class Fluid {String name;Fluid(String n){name=n;}String getName(){return name;}}
class FluidStack {Fluid fluid;int amount;FluidStack(Fluid f,int n){fluid=f;amount=n;}}
class CheckRecipeResult {}
class CheckRecipeResultRegistry {static final CheckRecipeResult NO_RECIPE=new CheckRecipeResult(),SUCCESSFUL=new CheckRecipeResult();}
class Materials {static Materials Air=new Materials();FluidStack getGas(int n){return new FluidStack(new Fluid("air"),n);}}
class GTModHandler {static FluidStack getDistilledWater(int n){return new FluidStack(new Fluid("water"),n);}}
class InputInventory {
  Map<String,Integer> visible=new HashMap<>(),paid=new HashMap<>(),requested=new HashMap<>();List<String> calls=new ArrayList<>();
  long power=Long.MAX_VALUE;String shortFluid="";
  InputInventory(){for(String s:new String[]{"gas","liquid","air","water"})visible.put(s,Integer.MAX_VALUE);}
  int drain(FluidStack f){String key=f.fluid.name;calls.add(key);requested.put(key,f.amount);int n=(int)Math.min(f.amount,power);if(key.equals(shortFluid))n=Math.max(0,n-1);power-=n;paid.merge(key,n,Integer::sum);return n;}
}
class GTSRHatchFluidAccess {
  static long probeFluidAmountAcross(InputInventory i,FluidStack f){return Math.min(f.amount,i.visible.getOrDefault(f.fluid.name,0));}
  static boolean hasEnoughAcross(InputInventory i,FluidStack f){return probeFluidAmountAcross(i,f)>=f.amount;}
  static int depleteFluidAcross(InputInventory i,FluidStack f){return i.drain(f);}
}
class Tile {boolean isAllowedToWork(){return true;}}
class TcdsFixture {
  static final long STEAM_BUFFER_CAPACITY=840_000_000L;static final int AIR_PER_FUEL=100,STEAM_PER_WATER=160,GAS_SUPERHEAT_THRESHOLD=350,LIQUID_SUPERHEAT_THRESHOLD=450;
  boolean mMachine=true,mSuperheatedTier,dual;int mFlow=100,mOutputTier=100,mMaxProgresstime,mEfficiency,mEfficiencyIncrease;
  double mHeat=100,mLastHeatCap;long mSteamEquivalentBuffer;
  int mCurrentOutputEquivalent,mCurrentFuelKind,mCurrentFuelConsumption,mCurrentGasConsumption,mCurrentLiquidConsumption,mCurrentGasHeatValue,mCurrentLiquidHeatValue,mCurrentAirConsumption,mCurrentWaterConsumption;
  String mCurrentFuelFluidName="",mCurrentFuelLiquidName="";InputInventory inputs=new InputInventory(),mInputHatches=inputs;
  Tile getBaseMetaTileEntity(){return new Tile();}void explodeMultiblock(){throw new AssertionError("Unexpected explosion");}static int maxHeatForTier(int tier){return 200;}
  FuelScan scanFuels(){FuelScan s=new FuelScan();s.bestGas=new FuelCandidate(new Fluid("gas"),350,true);if(dual)s.bestLiquid=new FuelCandidate(new Fluid("liquid"),450,false);return s;}
  // PRODUCTION_METHODS
}
'''


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--baseline-dir', type=Path)
    parser.add_argument('--case', choices=['aggregate', 'tcds', 'all'], default='all')
    parser.add_argument('--out', type=Path, default=ROOT / 'build/critical-consumption-receipts')
    parser.add_argument('--java-home', type=Path,
                        default=Path(os.environ['JAVA_HOME']) if 'JAVA_HOME' in os.environ else None)
    args = parser.parse_args()
    paths = {name: args.baseline_dir / (name + '.baseline.java') if args.baseline_dir else path
             for name, path in PATHS.items()}
    sources = {name: path.read_text(encoding='utf-8') for name, path in paths.items()}
    native = sources['CriticalNativeMEFunding']
    physical = sources['CriticalHatchFunding']
    tcds = sources['MTEThermoChemicalDenseSteamGenerator']
    native_blocks = ['public enum Result', 'public static final class ItemConsumption',
                     'public static ItemConsumption consumeAllItem(', 'private static boolean sameItem(',
                     'private static final class ItemChoice']
    physical_blocks = ['private static boolean isCircuitSlot(',
                       'public static CriticalNativeMEFunding.ItemConsumption consumeAllItem(',
                       'private static boolean same(', 'private static final class ItemDebit']
    aggregate = AGGREGATE.replace('// NATIVE_METHODS', '\n'.join(block(native, s) for s in native_blocks))
    aggregate = aggregate.replace('// PHYSICAL_METHODS', '\n'.join(block(physical, s) for s in physical_blocks))
    # The sole type-name mapping binds the real fully-qualified debug-bus branch to its fixture type.
    aggregate = aggregate.replace('gregtech.api.metatileentity.implementations.MTEHatchInputBusDebug', 'MTEHatchInputBusDebug')
    tcds_blocks = ['public CheckRecipeResult checkProcessing()', 'private CheckRecipeResult processFuels(',
                   'public static double flowEfficiency(', 'public static double flowHeatFactor(',
                   'private static int saturateInt(', 'private static final class FuelCandidate',
                   'private static final class FuelScan']
    tcds_fixture = TCDS.replace('// PRODUCTION_METHODS', '\n'.join(block(tcds, s) for s in tcds_blocks))
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=True)
    receipt = {'scope': __doc__, 'sourceSHA256': {str(paths[n]): hashlib.sha256(s.encode()).hexdigest()
                                               for n, s in sources.items()}, 'checks': {}}

    def executable(name):
        result = str(args.java_home / 'bin' / name) if args.java_home else shutil.which(name)
        if not result:
            raise RuntimeError('Set --java-home or JAVA_HOME for JDK17+')
        return result

    failed = False
    for case, name, source in [('aggregate', 'AggregateReceiptRegression', aggregate),
                               ('tcds', 'TcdsReceiptRegression', tcds_fixture)]:
        if args.case not in ['all', case]:
            continue
        target = out / case
        target.mkdir(exist_ok=True)
        java = target / (name + '.java')
        java.write_text(source, encoding='utf-8')
        for stage, command in [('compile', [executable('javac'), '-encoding', 'UTF-8', '-d', str(target), str(java)]),
                               ('run', [executable('java'), '-cp', str(target), name])]:
            result = subprocess.run(command, capture_output=True, text=True)
            log = result.stdout + result.stderr
            (target / (stage + '.log')).write_text(log, encoding='utf-8')
            print(log, end='')
            receipt['checks'][case + '-' + stage] = result.returncode
            if result.returncode:
                failed = True
                break
    (out / 'receipt.json').write_text(json.dumps(receipt, indent=2), encoding='utf-8')
    raise SystemExit(1 if failed else 0)


if __name__ == '__main__':
    main()
