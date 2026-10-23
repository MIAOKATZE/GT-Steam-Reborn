"""Run the production payment state machine against deterministic inventory/NBT boundaries.

This is an isolated contract test, not a Minecraft or AE integration test.
"""
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "build/rc2-old-machine-contract/payment"
SOURCES = {
    "net/minecraft/nbt/NBTTagCompound.java": r"""
package net.minecraft.nbt;
import java.util.*;
public class NBTTagCompound {
  private final Map<String,Object> data = new HashMap<>();
  public void setTag(String k,NBTTagCompound v){data.put(k,v);}
  public NBTTagCompound getCompoundTag(String k){return (NBTTagCompound)data.getOrDefault(k,new NBTTagCompound());}
  public void setInteger(String k,int v){data.put(k,v);}
  public int getInteger(String k){return (int)data.getOrDefault(k,0);}
  public void setBoolean(String k,boolean v){data.put(k,v);}
  public boolean getBoolean(String k){return (boolean)data.getOrDefault(k,false);}
}
""",
    "net/minecraftforge/fluids/FluidStack.java": r"""
package net.minecraftforge.fluids;
import net.minecraft.nbt.NBTTagCompound;
public class FluidStack {
  public int amount;
  private final int id, tag;
  public FluidStack(int id,int amount,int tag){this.id=id;this.amount=amount;this.tag=tag;}
  public FluidStack copy(){return new FluidStack(id,amount,tag);}
  public boolean isFluidEqual(FluidStack other){return other!=null&&id==other.id&&tag==other.tag;}
  public NBTTagCompound writeToNBT(NBTTagCompound n){n.setInteger("id",id);n.setInteger("amount",amount);n.setInteger("tag",tag);return n;}
  public static FluidStack loadFluidStackFromNBT(NBTTagCompound n){return n.getInteger("amount")>0?new FluidStack(n.getInteger("id"),n.getInteger("amount"),n.getInteger("tag")):null;}
}
""",
    "gregtech/api/metatileentity/implementations/MTEHatch.java":
        "package gregtech.api.metatileentity.implementations; public class MTEHatch {}",
    "gregtech/common/tileentities/machines/IDualInputHatch.java":
        "package gregtech.common.tileentities.machines; public interface IDualInputHatch {}",
    "com/miaokatze/gtsr/api/compat/GTSRHatchFluidAccess.java": r"""
package com.miaokatze.gtsr.api.compat;
import java.util.*;
import java.util.function.IntConsumer;
import net.minecraftforge.fluids.FluidStack;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.common.tileentities.machines.IDualInputHatch;
public class GTSRHatchFluidAccess {
  static int available=1000, next=0, calls=0, lastRequest=0;
  static boolean fail;
  public static int probeWorkingFluid(List<? extends MTEHatch> h,List<? extends IDualInputHatch> d,FluidStack f){return available;}
  public static void payWorkingFluid(List<? extends MTEHatch> h,List<? extends IDualInputHatch> d,FluidStack f,IntConsumer paid){
    calls++;lastRequest=f.amount;
    int amount=Math.min(next,f.amount);if(amount>0)paid.accept(amount);
    if(fail)throw new IllegalStateException("injected source failure");
  }
}
""",
    "com/miaokatze/gtsr/api/compat/WorkingFluidPaymentContract.java": r"""
package com.miaokatze.gtsr.api.compat;
import java.util.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;
public class WorkingFluidPaymentContract {
  static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
  public static void main(String[] args){
    int[] dirty={0};Runnable mark=()->dirty[0]++;
    FluidStack water=new FluidStack(1,100,7);
    GTSRWorkingFluidPayment first=new GTSRWorkingFluidPayment();
    GTSRHatchFluidAccess.next=40;
    check(!first.pay(List.of(),List.of(),water,mark),"partial payment must not authorize output");
    NBTTagCompound nbt=new NBTTagCompound();first.save(nbt,"payment");
    check(nbt.getCompoundTag("payment").getInteger("paid")==40,"actual partial payment persisted");
    GTSRWorkingFluidPayment loaded=new GTSRWorkingFluidPayment();loaded.load(nbt,"payment");
    int calls=GTSRHatchFluidAccess.calls;
    check(!loaded.pay(List.of(),List.of(),new FluidStack(1,100,8),mark),"request NBT is part of identity");
    check(GTSRHatchFluidAccess.calls==calls,"mismatched NBT cannot consume another source");
    GTSRHatchFluidAccess.available=59;
    check(!loaded.pay(List.of(),List.of(),water,mark),"remaining preflight insufficient");
    check(GTSRHatchFluidAccess.calls==calls,"failed preflight never drains");
    GTSRHatchFluidAccess.available=1000;GTSRHatchFluidAccess.next=60;
    check(loaded.pay(List.of(),List.of(),new FluidStack(1,999,7),mark),"saved target is frozen across retry");
    check(GTSRHatchFluidAccess.lastRequest==60,"retry charges only unpaid remainder");
    loaded.complete();loaded.save(nbt,"payment");
    check(loaded.getTarget()==null&&nbt.getCompoundTag("payment").getInteger("paid")==0,"completed credit consumed once");
    GTSRWorkingFluidPayment unknown=new GTSRWorkingFluidPayment();
    GTSRHatchFluidAccess.next=25;GTSRHatchFluidAccess.fail=true;
    check(!unknown.pay(List.of(),List.of(),water,mark)&&unknown.isUncertain(),"exception locks payment");
    unknown.save(nbt,"payment");
    check(nbt.getCompoundTag("payment").getInteger("paid")==25,"known prefix survives later exception");
    GTSRWorkingFluidPayment locked=new GTSRWorkingFluidPayment();locked.load(nbt,"payment");
    calls=GTSRHatchFluidAccess.calls;
    check(!locked.pay(List.of(),List.of(),water,mark)&&GTSRHatchFluidAccess.calls==calls,"uncertain reload never automatically replays");
    boolean rejected=false;try{locked.complete();}catch(IllegalStateException expected){rejected=true;}
    check(rejected,"uncertain receipt cannot establish output");
    check(dirty[0]>=6,"payment state mutations request persistence");
    System.out.println("PASS: partial receipt, frozen target, NBT identity, reload remainder, insufficient preflight, uncertain lock, no replay");
  }
}
""",
}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    files = []
    for name, source in SOURCES.items():
        target = OUT / "src" / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(source, encoding="utf-8")
        files.append(target)
    files.append(ROOT / "src/main/java/com/miaokatze/gtsr/api/compat/GTSRWorkingFluidPayment.java")
    jdk = Path.home() / ".gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin"
    javac = str(jdk / "javac.exe") if jdk.exists() else shutil.which("javac")
    java = str(jdk / "java.exe") if jdk.exists() else shutil.which("java")
    args = [javac, "-J-Duser.language=en", "-encoding", "UTF-8", "-d", str(OUT / "classes"), *map(str, files)]
    subprocess.run(args, check=True)
    result = subprocess.run([java, "-cp", str(OUT / "classes"),
        "com.miaokatze.gtsr.api.compat.WorkingFluidPaymentContract"], check=True, capture_output=True, text=True)
    (OUT / "result.txt").write_text(result.stdout, encoding="utf-8")
    print(result.stdout.strip())


if __name__ == "__main__":
    main()
