"""Compile production hatch access methods against deterministic API boundary models.

These assertions cover bounded requests, filtering and source identity. They do not
claim to instantiate Minecraft tiles or an AE network; TestLab supplies that check.
"""

from pathlib import Path
import shutil
import subprocess


ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "build/v2112-hatch-fluid-probe-contract"
SOURCE = ROOT / "src/main/java/com/miaokatze/gtsr/api/compat/GTSRHatchFluidAccess.java"

HARNESS = r"""
import java.util.*;

public class HatchFluidProbeContract {
    enum ForgeDirection { UNKNOWN }
    enum Actionable { SIMULATE, MODULATE }
    interface IActionHost {}
    static class MachineSource { MachineSource(IActionHost host) {} }
    static class Fluid { final int id; Fluid(int id) {this.id=id;} }
    static class FluidStack {
        final int id, tag;
        int amount;
        FluidStack(int id,int amount,int tag) {this.id=id;this.amount=amount;this.tag=tag;}
        FluidStack(Fluid fluid,int amount) {this(fluid.id,amount,0);}
        FluidStack(FluidStack identity,int amount) {this(identity.id,amount,identity.tag);}
        FluidStack copy() {return new FluidStack(id,amount,tag);}
        boolean isFluidEqual(FluidStack other) {return other!=null && id==other.id && tag==other.tag;}
    }
    static class FluidTankInfo {
        final FluidStack fluid;
        FluidTankInfo(FluidStack fluid) {this.fluid=fluid;}
    }
    interface IAEFluidStack {FluidStack getFluidStack();long getStackSize();void setStackSize(long amount);}
    static class AEFluidStack implements IAEFluidStack {
        final FluidStack stack;
        long amount;
        AEFluidStack(FluidStack stack) {this.stack=stack.copy();this.amount=stack.amount;}
        static AEFluidStack create(FluidStack stack) {return new AEFluidStack(stack);}
        public FluidStack getFluidStack() {return new FluidStack(stack,(int)Math.min(Integer.MAX_VALUE,amount));}
        public long getStackSize() {return amount;}
        public void setStackSize(long amount) {this.amount=amount;}
    }
    static class GridAccessException extends Exception {}
    static class Inventory {
        final FluidStack stock;
        long amount,maxRequest;
        int requests;
        Inventory(long amount,int tag) {stock=new FluidStack(1,1,tag);this.amount=amount;}
        IAEFluidStack extractItems(IAEFluidStack request,Actionable mode,MachineSource source) {
            FluidStack want=request.getFluidStack();
            requests++;maxRequest=Math.max(maxRequest,request.getStackSize());
            if(!stock.isFluidEqual(want) || amount<=0) return null;
            long taken=Math.min(amount,request.getStackSize());
            if(mode==Actionable.MODULATE)amount-=taken;
            IAEFluidStack result=AEFluidStack.create(want);result.setStackSize(taken);return result;
        }
        Inventory getFluidInventory() {return this;}
    }
    static class AENetworkProxy {
        final Inventory inventory;
        boolean powered=true, reachable=true;
        AENetworkProxy(Inventory inventory) {this.inventory=inventory;}
        Inventory getStorage() throws GridAccessException {
            if(!reachable)throw new GridAccessException();return inventory;
        }
        AENetworkProxy getEnergy() throws GridAccessException {
            if(!reachable)throw new GridAccessException();return this;
        }
    }
    static class Platform {
        static IAEFluidStack poweredExtraction(AENetworkProxy energy,Inventory inventory,IAEFluidStack request,MachineSource source) {
            return energy.powered ? inventory.extractItems(request,Actionable.MODULATE,source) : null;
        }
    }
    static class MTEHatch implements IActionHost {
        final List<FluidStack> tanks=new ArrayList<>();
        FluidTankInfo[] getTankInfo(ForgeDirection side) {return tanks.stream().map(FluidTankInfo::new).toArray(FluidTankInfo[]::new);}
        FluidStack drain(ForgeDirection side,FluidStack request,boolean consume) {
            for(FluidStack tank:tanks)if(tank.isFluidEqual(request) && tank.amount>0) {
                int taken=Math.min(tank.amount,request.amount);if(consume)tank.amount-=taken;
                return new FluidStack(tank.id,taken,tank.tag);
            }
            return null;
        }
        IActionHost getBaseMetaTileEntity() {return this;}
    }
    static class MTEHatchInputME extends MTEHatch {
        final AENetworkProxy proxy;
        boolean enabled=true;
        final List<FluidStack> configured=new ArrayList<>();
        MTEHatchInputME(AENetworkProxy proxy) {this.proxy=proxy;}
        boolean isAllowedToWork() {return enabled && proxy.powered;}
        FluidStack[] getStoredFluids() {return isAllowedToWork() ? configured.toArray(new FluidStack[0]) : new FluidStack[0];}
        AENetworkProxy getProxy() {return proxy;}
        FluidTankInfo[] getTankInfo(ForgeDirection side) {throw new AssertionError("ME getTankInfo would request unbounded inventory");}
    }
    static class UnsupportedMe extends MTEHatchInputME { UnsupportedMe(AENetworkProxy p) {super(p);} }
    static class GTUtility {
        static <T> List<T> validMTEList(List<T> hatches) {return hatches;}
    }
    // PRODUCTION_METHODS
    static void check(boolean ok,String message) {if(!ok)throw new AssertionError(message);}
    static MTEHatchInputME me(AENetworkProxy proxy) {
        MTEHatchInputME me=new MTEHatchInputME(proxy);
        me.configured.add(new FluidStack(1,1,7));me.configured.add(new FluidStack(1,1,7));
        return me;
    }
    public static void main(String[] args) {
        FluidStack want=new FluidStack(1,512,7);
        MTEHatch ordinary=new MTEHatch();
        ordinary.tanks.add(new FluidStack(1,20,7));ordinary.tanks.add(new FluidStack(1,30,7));
        ordinary.tanks.add(new FluidStack(1,900,8));
        check(probeFluidAmount(ordinary,want).amount==50,"ordinary tanks sum with NBT filter");
        check(probeFluidAmount(ordinary,new FluidStack(1,16,7)).amount==16,"ordinary demand cap");
        Inventory stock=new Inventory(200,7);
        AENetworkProxy proxy=new AENetworkProxy(stock);
        MTEHatchInputME first=me(proxy), second=me(proxy);
        check(probeFluidAmount(first,want).amount==200,"1L config is identity, not stock");
        check(stock.amount==200 && stock.maxRequest==512,"ME simulation has bounded request and no debit");
        check(probeFluidAmountAcross(List.of(first,second),want)==200,"same monitor counted once across two hatches and duplicate filters");
        check(probeFluidAmount(first,new FluidStack(1,100,8))==null,"unconfigured NBT identity rejected");
        first.enabled=false;
        check(probeFluidAmount(first,want)==null,"disabled ME rejected");
        check(probeFluidAmountAcross(List.of(first,second),want)==200,"active second same-grid hatch remains usable");
        first.enabled=true;proxy.powered=false;
        check(probeFluidAmount(first,want)==null && depleteFluidAcross(List.of(first,second),want)==0,"no AE power cannot debit");
        check(stock.amount==200,"unpowered request preserves stock");
        proxy.powered=true;proxy.reachable=false;
        check(probeFluidAmount(first,want)==null && probeFluidAmountAcross(List.of(first,second),want)==0,"unreachable grid returns unavailable");
        proxy.reachable=true;
        UnsupportedMe unsupported=new UnsupportedMe(proxy);
        check(probeFluidAmount(unsupported,want)==null && drainFluidExact(unsupported,want)==0,"unsupported subclass boundary retained");
        check(depleteFluidAcross(List.of(first,second),want)==200,"actual bounded drain once per monitor");
        check(stock.amount==0 && probeFluidAmount(first,want)==null,"empty configured network reports zero");
        check(want.amount==512 && want.tag==7,"caller request is unchanged");
        check(probeFluidAmountAcross(List.of(ordinary,first),want)==50,"ordinary and empty ME mixed sources");
        check(probeFluidAmount(first,new FluidStack(1,0,7))==null,"zero request does not query AE");
        check(stock.maxRequest<=512,"all AE extraction requests are bounded");
        long large=5_000_000_000L;
        stock.amount=large;
        check(probeFluidAmountAcross(List.of(first,second),want,large+100)==large,"long ME probe exceeds int without duplicate source count");
        check(stock.amount==large,"long probe never debits");
        check(depleteFluidAcross(List.of(first,second),want,large+100)==large,"long ME actual debit uses IAE stack size");
        check(stock.amount==0,"long ME debit reaches actual empty network");
        MTEHatch multiTank=new MTEHatch();
        multiTank.tanks.add(new FluidStack(1,2_000_000_000,7));
        multiTank.tanks.add(new FluidStack(1,2_000_000_000,7));
        multiTank.tanks.add(new FluidStack(1,1_000_000_000,7));
        check(probeFluidAmountAcross(List.of(multiTank),want,large+100)==large,"ordinary long probe sums each real tank once");
        check(depleteFluidAcross(List.of(multiTank),want,large+100)==large,"ordinary long debit consumes real int blocks");
        check(probeFluidAmountAcross(List.of(multiTank),want,large)==0,"ordinary debit cannot repeat nonconsuming simulation");
        stock.amount=large;first.enabled=false;second.enabled=false;
        check(probeFluidAmountAcross(List.of(first,second),want,large)==0 && depleteFluidAcross(List.of(first,second),want,large)==0,"disabled long ME sources remain unavailable");
        first.enabled=true;second.enabled=true;proxy.powered=false;
        check(depleteFluidAcross(List.of(first,second),want,large)==0 && stock.amount==large,"unpowered long ME debit preserves network");
        System.out.println("PASS production hatch methods: bounded ME simulation/no debit, configured NBT filter, ordinary tank sum, same-network dedup, disabled/unpowered/unreachable/empty sources, actual drain, 5B long ME/ordinary probe and debit; AE_NETWORK=NOT_RUN");
    }
}
"""


def main():
    source = SOURCE.read_text(encoding="utf-8")
    methods = source[source.index("    public static FluidStack probeFluidAmount("):
                     source.index("    private static MTEHatchCraftingInputME nativeOwner(")]
    OUT.mkdir(parents=True, exist_ok=True)
    java_source = OUT / "HatchFluidProbeContract.java"
    java_source.write_text(HARNESS.replace("    // PRODUCTION_METHODS", methods), encoding="utf-8")
    jdk = Path.home() / ".gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin"
    javac = str(jdk / "javac.exe") if jdk.exists() else shutil.which("javac")
    java = str(jdk / "java.exe") if jdk.exists() else shutil.which("java")
    classes = OUT / "classes"
    subprocess.run([javac, "-encoding", "UTF-8", "-d", str(classes), str(java_source)], check=True)
    result = subprocess.run([java, "-cp", str(classes), "HatchFluidProbeContract"], capture_output=True, text=True)
    (OUT / "result.txt").write_text(result.stdout + result.stderr, encoding="utf-8")
    print(result.stdout + result.stderr, end="")
    raise SystemExit(result.returncode)


if __name__ == "__main__":
    main()
