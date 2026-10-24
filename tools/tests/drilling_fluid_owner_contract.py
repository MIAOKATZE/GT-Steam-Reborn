"""Run exact node retry/setItemNBT/load methods and hub methods with isolated boundaries."""
from pathlib import Path
import hashlib
import json
import shutil
import subprocess

from hub_pending_output_contract import ROOT, SOURCES, method

OUT = ROOT / "build/machine-audit/drilling-fluid-owner"
HUB = ROOT / "src/main/java/com/miaokatze/gtsr/common/machine/MTESingularityDrillingHub.java"
NODE = ROOT / "src/main/java/com/miaokatze/gtsr/common/machine/MTESingularityDrillingNode.java"


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    hub = HUB.read_text(encoding="utf-8")
    node = NODE.read_text(encoding="utf-8")
    hub_methods = [method(hub, "public int pushNodeFluidOutput(FluidStack fluid)"),
        method(hub, "private void gtsr$drainPendingOutputs()"),
        method(hub, "public void saveNBTData(NBTTagCompound aNBT)"),
        method(hub, "public void loadNBTData(NBTTagCompound aNBT)")]
    if "private void gtsr$addToPendingFluid(FluidStack fluid)" in hub:
        hub_methods.append(method(hub, "private void gtsr$addToPendingFluid(FluidStack fluid)"))
    node_methods = [method(node, "public void setItemNBT(net.minecraft.nbt.NBTTagCompound aNBT)"),
        method(node, "public void loadNBTData(net.minecraft.nbt.NBTTagCompound aNBT)")]
    if "private void savePendingFluid(NBTTagCompound aNBT)" in node:
        node_methods.append(method(node, "private void savePendingFluid(NBTTagCompound aNBT)"))
    # Compile the actual caller retry block without rewriting its balance handling.
    retry = method(node[node.index("public void doWork("):], "if (mPendingFluid != null)")
    fixture = """
import java.util.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraftforge.fluids.FluidStack;
class FixtureParent {public void setItemNBT(NBTTagCompound n){}public void loadNBTData(NBTTagCompound n){}public void saveNBTData(NBTTagCompound n){}}
class MTESingularityDrillingHub extends FixtureParent {
 ArrayList<ItemStack> gtsr$pendingItemOutputs;FluidStack gtsr$pendingFluidOutput;
 final ArrayList<BoundDrillNode> mBoundNodes=new ArrayList<>();
 static class BoundDrillNode {int x,y,z,dimensionId;boolean isMiner,isActive;
  BoundDrillNode(int x,int y,int z,int dim,boolean miner){this.x=x;this.y=y;this.z=z;dimensionId=dim;isMiner=miner;}}
 int capacity,delivered;
 boolean protectsExcessFluid(){return true;}
 private int tryStoreNodeItemOutput(ItemStack stack){return stack.stackSize;}
 private int gtsr$fillOutputHatches(FluidStack fluid){int moved=Math.min(fluid.amount,capacity);capacity-=moved;fluid.amount-=moved;delivered+=moved;return fluid.amount;}
 __HUB__
 void tick(){gtsr$drainPendingOutputs();}
}
class SourceNode extends FixtureParent {
 FluidStack mPendingFluid;int mDrillTier=2;
 int mTipDepth,mStatus,mCycleTimer,mExtractionCounter,mDisplayRate,mLastExtractedAmount;
 boolean mAtBedrock,mDisabled,mHasStarted,mSoftDisabled,mRetractDone,mForcedRetract;
 String mLastFluidName;Object mLockedFluid;
 __NODE__
 void retry(MTESingularityDrillingHub hub){__RETRY__}
}
public class DrillingFluidOwnerContract {
 static int checks;static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
 static SourceNode restoreItem(NBTTagCompound tag){SourceNode node=new SourceNode();node.loadNBTData(tag);return node;}
 public static void main(String[] args){
  MTESingularityDrillingHub hub=new MTESingularityDrillingHub();SourceNode node=new SourceNode();node.mPendingFluid=new FluidStack("oil",1000);
  node.mPendingFluid.tag=new NBTTagCompound();node.mPendingFluid.tag.setString("isotope","sample");
  for(int i=0;i<5;i++){node.retry(hub);hub.tick();}
  hub.capacity=10000;hub.tick();node.retry(hub);hub.tick();
  check(hub.delivered==1000,"blocked node retries must deliver extracted 1000 L exactly once across both endpoints");
  check(node.mPendingFluid==null,"fully accepted node remainder clears");
  MTESingularityDrillingHub recycledHub=new MTESingularityDrillingHub();SourceNode recycled=new SourceNode();recycled.mPendingFluid=new FluidStack("oil",2000);
  recycled.mPendingFluid.tag=new NBTTagCompound();recycled.mPendingFluid.tag.setString("isotope","custom");recycled.retry(recycledHub);
  NBTTagCompound itemTag=new NBTTagCompound();recycled.setItemNBT(itemTag);
  check(itemTag.getInteger("mDrillTier")==2,"existing node upgrade payload retained");
  ItemStack recoveredItem=new ItemStack(109,1,0);recoveredItem.tag=itemTag;
  recycledHub.gtsr$pendingItemOutputs=new ArrayList<>();recycledHub.gtsr$pendingItemOutputs.add(recoveredItem);
  NBTTagCompound hubSave=new NBTTagCompound();recycledHub.saveNBTData(hubSave);
  MTESingularityDrillingHub restoredHub=new MTESingularityDrillingHub();restoredHub.loadNBTData(hubSave);
  check(restoredHub.gtsr$pendingItemOutputs.size()==1,"recycled node body survives blocked hub output save/load");
  SourceNode placed=restoreItem(restoredHub.gtsr$pendingItemOutputs.get(0).tag);
  check(placed.mPendingFluid!=null&&placed.mPendingFluid.amount==2000,"real node setItemNBT/load preserves pending fluid");
  check(placed.mPendingFluid.name.equals("oil")&&placed.mPendingFluid.tag.equals(recycled.mPendingFluid.tag),"fluid identity and NBT survive item handoff");
  recycled.mPendingFluid=null; // World node removed after the single item has captured its state.
  recycledHub.capacity=750;recycledHub.tick();placed.retry(recycledHub);
  check(recycledHub.delivered==750&&placed.mPendingFluid.amount==1250,"replaced node only delivers available prefix");
  NBTTagCompound remainder=new NBTTagCompound();placed.setItemNBT(remainder);SourceNode resumed=restoreItem(remainder);
  recycledHub.capacity=5000;recycledHub.tick();resumed.retry(recycledHub);recycledHub.tick();
  check(recycledHub.delivered==2000&&resumed.mPendingFluid==null,"partial remainder handoff never replays delivered prefix");
  resumed.setItemNBT(itemTag);check(!itemTag.hasKey("mPendingFluid"),"empty item serialization clears prior balance");
  for(int i=0;i<3;i++){resumed.retry(recycledHub);recycledHub.tick();}
  check(recycledHub.delivered==2000,"completed retries remain empty");
  placed.loadNBTData(new NBTTagCompound());check(placed.mPendingFluid==null,"legacy missing pending key resets prior memory");
  System.out.println("PASS DrillingFluidOwnerContract checks="+checks);
 }
}
""".replace("__HUB__", "\n".join(hub_methods)).replace("__NODE__", "\n".join(node_methods)).replace("__RETRY__", retry)
    sources = {name: text for name, text in SOURCES.items() if name.endswith(".java") and "/" in name}
    sources["net/minecraftforge/fluids/FluidRegistry.java"] = "package net.minecraftforge.fluids;public class FluidRegistry {public static Object getFluid(String name){return name;}}"
    sources["DrillingFluidOwnerContract.java"] = fixture
    files = []
    for name, text in sources.items():
        path = OUT / "src" / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")
        files.append(path)
    jdk = Path.home() / ".gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin"
    javac = str(jdk / "javac.exe") if jdk.exists() else shutil.which("javac")
    java = str(jdk / "java.exe") if jdk.exists() else shutil.which("java")
    subprocess.run([javac, "-encoding", "UTF-8", "-d", str(OUT / "classes"), *map(str, files)], check=True)
    result = subprocess.run([java, "-cp", str(OUT / "classes"), "DrillingFluidOwnerContract"], capture_output=True, text=True)
    (OUT / "result.txt").write_text(result.stdout + result.stderr, encoding="utf-8")
    (OUT / "source.json").write_text(json.dumps({"sourceSha256": {str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in [HUB, NODE]},
        "scope": "Exact node retry block, setItemNBT/loadNBTData and hub methods with isolated parent/output/NBT boundaries; not GT integration"}, indent=2), encoding="utf-8")
    print(result.stdout + result.stderr, end="")
    result.check_returncode()


if __name__ == "__main__":
    main()
