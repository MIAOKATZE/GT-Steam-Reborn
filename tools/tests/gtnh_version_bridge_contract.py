"""Dispatch the actual modern Gradle-compiled production GUI bytecode on all three controlled API shapes.

These deliberately small fixtures verify JVM linkage and the slot's delegated callbacks, not Minecraft drag/drop.
Frozen upstream source: GT5-Unofficial tags 5.09.54.265/.275/.277, MTEDigitalTankBaseGui and
MTEMachineWithFluidScreenBaseGui. Real dependency compilation is a separate Gradle check.
"""
from pathlib import Path
import argparse
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]

STUBS = {
    "net.minecraftforge.fluids.FluidStack": "public final int id; public int amount; public FluidStack(int id,int amount){this.id=id;this.amount=amount;}",
    "net.minecraftforge.fluids.FluidTankInfo": "public FluidTankInfo(FluidStack stack,int capacity){}",
    "net.minecraftforge.fluids.IFluidTank": "FluidStack getFluid(); int getFluidAmount(); int getCapacity(); FluidTankInfo getInfo(); int fill(FluidStack stack,boolean execute); FluidStack drain(int amount,boolean execute);",
    "com.cleanroommc.modularui.screen.ModularPanel": "",
    "com.cleanroommc.modularui.value.sync.PanelSyncManager": "",
    "com.cleanroommc.modularui.value.sync.FluidSlotSyncHandler": """
        public final net.minecraftforge.fluids.IFluidTank tank;
        public java.util.function.Predicate<net.minecraftforge.fluids.FluidStack> predicate;
        public Runnable listener;
        public FluidSlotSyncHandler(net.minecraftforge.fluids.IFluidTank tank){this.tank=tank;}
        public FluidSlotSyncHandler filter(java.util.function.Predicate<net.minecraftforge.fluids.FluidStack> p){predicate=p;return this;}
        public void setChangeListener(Runnable r){listener=r;}
        public int insert(net.minecraftforge.fluids.FluidStack s){if(!predicate.test(s))return 0;int n=tank.fill(s,true);listener.run();return n;}
    """,
    "com.cleanroommc.modularui.api.widget.IPositioned": "",
    "com.cleanroommc.modularui.api.drawable.IDrawable": "",
    "com.cleanroommc.modularui.drawable.UITexture": "",
    "com.cleanroommc.modularui.widget.Widget": "",
    "gregtech.api.metatileentity.implementations.MTETieredMachineBlock": "",
    "gregtech.common.tileentities.storage.MTEDigitalTankBase": "public void setLockIfEmpty(){}",
    "com.cleanroommc.modularui.widgets.slot.FluidSlot": """
        public com.cleanroommc.modularui.value.sync.FluidSlotSyncHandler handler;
        public int bottom=-1,right=-1; public Object background;
        public FluidSlot syncHandler(com.cleanroommc.modularui.value.sync.FluidSlotSyncHandler h){handler=h;return this;}
        public com.cleanroommc.modularui.api.widget.IPositioned bottomRel(float x){bottom=(int)x;return this;}
        public com.cleanroommc.modularui.api.widget.IPositioned rightRel(float x){right=(int)x;return this;}
        public com.cleanroommc.modularui.widget.Widget background(com.cleanroommc.modularui.api.drawable.IDrawable... x){background=x[0];return this;}
    """,
    "gregtech.api.modularui2.GTGuiTextures": "public static final com.cleanroommc.modularui.drawable.UITexture SLOT_FLUID_TANK=new com.cleanroommc.modularui.drawable.UITexture();",
    "com.miaokatze.gtsr.common.machine.base.MTEFilteredCacheNode": """
        public int amount=0, locks=0, fills=0, drains=0;
        public net.minecraftforge.fluids.FluidStack getFillableStack(){return amount==0?null:new net.minecraftforge.fluids.FluidStack(1,amount);}
        public int getFluidAmount(){return amount;}
        public int getRealCapacity(){return 12345;}
        public boolean isFluidInputAllowed(net.minecraftforge.fluids.FluidStack s){return s!=null&&s.id==1;}
        public void setLockIfEmpty(){locks++;}
        public int fill(net.minecraftforge.fluids.FluidStack s,boolean execute){fills++;int n=Math.min(s.amount,getRealCapacity()-amount);if(execute)amount+=n;return n;}
        public net.minecraftforge.fluids.FluidStack drain(int n,boolean execute){drains++;int result=Math.min(n,amount);if(execute)amount-=result;return new net.minecraftforge.fluids.FluidStack(1,result);}
    """,
}

BASE = """package gregtech.common.gui.modularui.singleblock.base;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.slot.FluidSlot;
import net.minecraftforge.fluids.IFluidTank;
import com.miaokatze.gtsr.common.machine.base.MTEFilteredCacheNode;
public class MTEDigitalTankBaseGui<T extends gregtech.common.tileentities.storage.MTEDigitalTankBase> {
 protected final gregtech.api.metatileentity.implementations.MTETieredMachineBlock machine;
 public MTEDigitalTankBaseGui(T machine){this.machine=machine;}
 HOOK
 public FluidSlot dispatch(){return CALL;}
}
"""

HARNESS = """package fixture;
import com.miaokatze.gtsr.common.gui.MTEFilteredCacheNodeGui;
import com.miaokatze.gtsr.common.machine.base.MTEFilteredCacheNode;
import com.cleanroommc.modularui.widgets.slot.FluidSlot;
import net.minecraftforge.fluids.FluidStack;
public class BridgeContract {
 static int checks;
 static void check(boolean ok){checks++;if(!ok)throw new AssertionError("check "+checks);}
 public static void main(String[] args)throws Exception{
  MTEFilteredCacheNode m=new MTEFilteredCacheNode();
  MTEFilteredCacheNodeGui gui=new MTEFilteredCacheNodeGui(m);
  FluidSlot s=gui.dispatch();
  check(s.bottom==0&&s.right==0&&s.background==gregtech.api.modularui2.GTGuiTextures.SLOT_FLUID_TANK);
  check(s.handler.tank.getCapacity()==12345);
  check(s.handler.tank.getFluid()==null&&s.handler.tank.getFluidAmount()==0);
  check(s.handler.insert(new FluidStack(2,100))==0&&m.amount==0&&m.fills==0&&m.locks==0);
  check(s.handler.insert(new FluidStack(1,100))==100&&m.amount==100&&m.fills==1&&m.locks==1);
  check(s.handler.tank.getFluid().amount==100&&s.handler.tank.getFluidAmount()==100);
  check(s.handler.tank.fill(new FluidStack(1,20),false)==20&&m.amount==100);
  check(s.handler.tank.drain(10,false).amount==10&&m.amount==100);
  check(s.handler.tank.drain(10,true).amount==10&&m.amount==90);
  check(s.handler.tank.getInfo()!=null);
  check(MTEFilteredCacheNodeGui.class.getDeclaredMethod("createFluidSlot",com.cleanroommc.modularui.screen.ModularPanel.class,com.cleanroommc.modularui.value.sync.PanelSyncManager.class,net.minecraftforge.fluids.IFluidTank.class).getReturnType()==FluidSlot.class);
  check(MTEFilteredCacheNodeGui.class.getDeclaredMethod("createSlot",com.cleanroommc.modularui.screen.ModularPanel.class,com.cleanroommc.modularui.value.sync.PanelSyncManager.class).getReturnType()==FluidSlot.class);
  System.out.println(args[0]+": "+checks+" checks PASS");
 }
}
"""


def write_java(folder, name, body):
    path = folder / (name.replace(".", "/") + ".java")
    path.parent.mkdir(parents=True, exist_ok=True)
    package, cls = name.rsplit(".", 1)
    kind = "interface" if cls in ("IFluidTank", "IPositioned", "IDrawable") else "class"
    inheritance = {
        "MTEFilteredCacheNode": " extends gregtech.common.tileentities.storage.MTEDigitalTankBase",
        "MTEDigitalTankBase": " extends gregtech.api.metatileentity.implementations.MTETieredMachineBlock",
        "FluidSlot": " extends com.cleanroommc.modularui.widget.Widget implements com.cleanroommc.modularui.api.widget.IPositioned",
        "UITexture": " implements com.cleanroommc.modularui.api.drawable.IDrawable",
    }.get(cls, "")
    path.write_text(f"package {package}; public {kind} {cls}{inheritance} {{ {body} }}", encoding="utf-8")
    return path


def run():
    parser = argparse.ArgumentParser()
    parser.add_argument("--work-dir", type=Path)
    parser.add_argument("--production-classes", type=Path, default=ROOT / "build/classes/java/main")
    args = parser.parse_args()
    work = args.work_dir or Path(tempfile.mkdtemp(prefix="gtsr-version-bridge-"))
    work.mkdir(parents=True, exist_ok=True)
    javac = shutil.which("javac")
    java = shutil.which("java")
    sources = [write_java(work / "src", name, body) for name, body in STUBS.items()]
    base = work / "src/gregtech/common/gui/modularui/singleblock/base/MTEDigitalTankBaseGui.java"
    base.parent.mkdir(parents=True, exist_ok=True)
    modern = BASE.replace("HOOK", "protected FluidSlot createSlot(ModularPanel p,PanelSyncManager s){throw new AssertionError();}").replace("CALL", "createSlot(null,null)")
    base.write_text(modern, encoding="utf-8")
    harness = work / "src/fixture/BridgeContract.java"
    harness.parent.mkdir(parents=True, exist_ok=True)
    harness.write_text(HARNESS, encoding="utf-8")
    output = work / "classes"
    output.mkdir(exist_ok=True)
    subprocess.run([javac, "--release", "8", "-Xlint:-options", "-encoding", "UTF-8", "-d", str(output), "-cp", str(args.production_classes), *map(str, sources), str(base), str(harness)], check=True)
    package = Path("com/miaokatze/gtsr/common/gui")
    production = sorted((args.production_classes / package).glob("MTEFilteredCacheNodeGui*.class"))
    assert len(production) >= 2, "Run Gradle compileJava first; actual production GUI bytecode is required"
    for path in production:
        target = output / package / path.name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(path, target)
    print("Using actual Gradle production GUI classes:", ", ".join(p.name for p in production))
    for version in ("2.9.0", "2.9.1", "2.9.2"):
        if version == "2.9.0":
            base.write_text(BASE.replace("HOOK", "protected FluidSlot createFluidSlot(ModularPanel p,PanelSyncManager s,IFluidTank t){throw new AssertionError();}").replace("CALL", "createFluidSlot(null,null,null)"), encoding="utf-8")
        else:
            base.write_text(modern, encoding="utf-8")
        subprocess.run([javac, "--release", "8", "-Xlint:-options", "-cp", str(output), "-d", str(output), str(base)], check=True)
        subprocess.run([java, "-cp", str(output), "fixture.BridgeContract", version], check=True)
    # Guard the registration anchors; bootstrap must not relocate business registration.
    proxy = (ROOT / "src/main/java/com/miaokatze/gtsr/main/CommonProxy.java").read_text(encoding="utf-8")
    entry = (ROOT / "src/main/java/com/miaokatze/gtsr/main/GTSteamReborn.java").read_text(encoding="utf-8")
    assert "required-before:gregtech" in entry
    assert "GregTechAPI.sAfterGTPreload.add(registerRunnable)" in proxy
    assert 'RecipeRegistration.register("gtsr", () -> {' in proxy
    bootstrap = (ROOT / "src/main/java/com/miaokatze/gtsr/bootstrap/GTNHCompatibilityBootstrap.java").read_text(encoding="utf-8")
    assert "import gregtech." not in bootstrap and "Class.forName" not in bootstrap
    print("Lifecycle anchors and bootstrap isolation: 4 checks PASS")


if __name__ == "__main__":
    run()
