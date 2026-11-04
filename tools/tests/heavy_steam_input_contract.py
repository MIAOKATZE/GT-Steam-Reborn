"""Exercise Heavy's production input-selection methods at deterministic fluid API boundaries.

The methods are copied verbatim from the selected Java source and compiled with the
real production settlement arithmetic. This is NOT a Minecraft/AE integration test.
Use --source to run the same assertions against a saved pre-fix source.
"""

import argparse
from pathlib import Path
import shutil
import subprocess


ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "build/v2112-heavy-input-contract"
PRODUCTION = ROOT / "src/main/java/com/miaokatze/gtsr/common/machine/MTEHeavySteamTurbine.java"
CYCLE = ROOT / "src/main/java/com/miaokatze/gtsr/common/machine/turbine/HeavySteamTurbineCycle.java"

HARNESS = r"""
import java.util.*;
import com.miaokatze.gtsr.common.machine.turbine.HeavySteamTurbineCycle;

public class HeavySteamInputContract {
    enum SteamType { NONE, STEAM, SH_STEAM, UNSUPPORTED }
    static final class FluidStack {
        final SteamType type;
        final int tag;
        int amount;
        FluidStack(SteamType type, int amount, int tag) { this.type=type; this.amount=amount; this.tag=tag; }
        FluidStack(FluidStack other, int amount) { this(other.type,amount,other.tag); }
        boolean isFluidEqual(FluidStack other) { return type==other.type && tag==other.tag; }
    }
    static final class SteamTurbineSteamTypes {
        static SteamType classifyFluid(FluidStack f) { return f.amount>0 ? f.type : SteamType.NONE; }
    }
    // Deliberately an API boundary model, never an AE network or ME hatch implementation.
    static final class Source {
        final FluidStack fluid;
        int debitLimit=Integer.MAX_VALUE;
        Source(SteamType type,int amount,int tag) { fluid=new FluidStack(type,amount,tag); }
    }
    static final class GTSRHatchFluidAccess {
        static int probes, maxRequest;
        static long probeFluidAmountAcross(List<Source> sources,FluidStack want) {
            probes++; maxRequest=Math.max(maxRequest,want.amount);
            int amount=0;
            for(Source source:sources) if(source.fluid.isFluidEqual(want)) amount+=source.fluid.amount;
            return Math.min(amount,want.amount);
        }
        static int depleteFluidAcross(List<Source> sources,FluidStack want) {
            int remaining=want.amount;
            for(Source source:sources) if(source.fluid.isFluidEqual(want)) {
                int take=Math.min(remaining,Math.min(source.fluid.amount,source.debitLimit));
                source.fluid.amount-=take; remaining-=take;
            }
            return want.amount-remaining;
        }
    }
    int paidSteam, mMaterialTier=2;
    final List<Source> mInputHatches=new ArrayList<>();
    final List<FluidStack> configured=new ArrayList<>();
    List<FluidStack> getStoredFluids() { return configured; }
    // PRODUCTION_METHODS
    static void check(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
    static HeavySteamInputContract fixture(SteamType type,int amount) {
        HeavySteamInputContract machine=new HeavySteamInputContract();
        machine.configured.add(new FluidStack(type,1,0));
        machine.mInputHatches.add(new Source(type,amount,0));
        return machine;
    }
    static void rated(int tier,SteamType type,int expectedOutput) {
        int rated=HeavySteamTurbineCycle.ratedSteam(tier,type==SteamType.SH_STEAM);
        HeavySteamInputContract machine=fixture(type,1000000); machine.mMaterialTier=tier;
        int available=machine.availableSteam(type,rated);
        check(available==rated,"configuration sentinel must not cap actual supply: tier="+tier+" fuel="+type+" actual="+available);
        var plan=HeavySteamTurbineCycle.settle(tier,type==SteamType.SH_STEAM,100,0,available,4096,rated);
        int consumed=machine.consumeSteam(type,plan.steam());
        var actual=HeavySteamTurbineCycle.settle(tier,type==SteamType.SH_STEAM,100,0,consumed,4096,rated);
        check(consumed==rated && actual.output()==expectedOutput,"rated debit/output");
        check(machine.mInputHatches.get(0).fluid.amount==1000000-rated,"real boundary debit");
        check(machine.configured.get(0).amount==1,"configuration snapshots stay unchanged");
    }
    public static void main(String[] args) {
        rated(1,SteamType.STEAM,256); rated(2,SteamType.STEAM,1024); rated(2,SteamType.SH_STEAM,1024);
        var empty=fixture(SteamType.STEAM,0);
        check(empty.selectSteam()==SteamType.NONE,"empty configured source is not fuel");
        var duplicate=fixture(SteamType.STEAM,16);
        duplicate.configured.add(new FluidStack(SteamType.STEAM,1,0));
        GTSRHatchFluidAccess.probes=0;
        check(duplicate.availableSteam(SteamType.STEAM,512)==16,"duplicate config must not count supply twice");
        check(GTSRHatchFluidAccess.probes==1,"one query per fluid identity");
        duplicate.configured.add(new FluidStack(SteamType.STEAM,1,7));
        duplicate.mInputHatches.add(new Source(SteamType.STEAM,20,7));
        check(duplicate.availableSteam(SteamType.STEAM,512)==36,"distinct fluid NBT identities retained");
        var mixed=fixture(SteamType.STEAM,512);
        mixed.configured.add(new FluidStack(SteamType.SH_STEAM,1,0));
        mixed.mInputHatches.add(new Source(SteamType.SH_STEAM,1024,0));
        check(mixed.selectSteam()==SteamType.SH_STEAM,"steel prefers superheated");
        mixed.mMaterialTier=1;
        check(mixed.selectSteam()==SteamType.STEAM,"bronze rejects superheated");
        mixed.mMaterialTier=2; mixed.paidSteam=7;
        check(mixed.selectSteam()==SteamType.STEAM,"old prepaid steam settles first");
        check(mixed.availableSteam(SteamType.STEAM,16)==16,"prepaid quantity plus bounded remaining demand");
        check(mixed.consumeSteam(SteamType.STEAM,16)==16 && mixed.paidSteam==0,"prepaid consumed exactly once");
        var shortage=fixture(SteamType.STEAM,2048);
        shortage.mInputHatches.get(0).debitLimit=13;
        var plan=HeavySteamTurbineCycle.settle(2,false,150,0,shortage.availableSteam(SteamType.STEAM,2048),4096,2048);
        int debit=shortage.consumeSteam(SteamType.STEAM,plan.steam());
        var actual=HeavySteamTurbineCycle.settle(2,false,150,0,debit,4096,2048);
        check(debit==13 && actual.efficiency()==100 && actual.output()==6 && actual.credit()==100,"short debit recalculated at actual flow");
        check(GTSRHatchFluidAccess.maxRequest<=2048,"requests bounded by turbine rating");
        System.out.println("PASS production Heavy methods: 1L configuration identities, empty/duplicate/NBT sources, ordinary/SH selection, prepaid steam, rated/partial actual debit; AE_NETWORK=NOT_RUN");
    }
}
"""


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=PRODUCTION)
    args = parser.parse_args()
    source = args.source.read_text(encoding="utf-8")
    start_marker = "    private List<FluidStack> steamInputs(" if "private List<FluidStack> steamInputs(" in source else "    private int availableSteam("
    methods = source[source.index(start_marker):source.index("    private boolean flushCoolant()")]
    OUT.mkdir(parents=True, exist_ok=True)
    java_source = OUT / "HeavySteamInputContract.java"
    java_source.write_text(HARNESS.replace("    // PRODUCTION_METHODS", methods), encoding="utf-8")
    jdk = Path.home() / ".gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin"
    javac = str(jdk / "javac.exe") if jdk.exists() else shutil.which("javac")
    java = str(jdk / "java.exe") if jdk.exists() else shutil.which("java")
    classes = OUT / "classes"
    subprocess.run([javac, "-encoding", "UTF-8", "-d", str(classes), str(java_source), str(CYCLE)], check=True)
    result = subprocess.run([java, "-cp", str(classes), "HeavySteamInputContract"], capture_output=True, text=True)
    (OUT / "result.txt").write_text(result.stdout + result.stderr, encoding="utf-8")
    print(result.stdout + result.stderr, end="")
    raise SystemExit(result.returncode)


if __name__ == "__main__":
    main()
