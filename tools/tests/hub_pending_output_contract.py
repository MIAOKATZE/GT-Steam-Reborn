"""Run exact hub save/load/retry source methods with isolated NBT and output boundaries.

This source-seam contract does not bootstrap GT, AE or a Minecraft world.
"""
from pathlib import Path
import hashlib
import json
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "build/machine-audit/hub-pending"
PRODUCT = ROOT / "src/main/java/com/miaokatze/gtsr/common/machine/MTESingularityDrillingHub.java"


def method(source, signature):
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 1
    end = opening + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


SOURCES = {
    "net/minecraft/nbt/NBTTagCompound.java": """
package net.minecraft.nbt;
import java.util.*;
public class NBTTagCompound {
 private final Map<String,Object> data=new HashMap<>();
 public void setInteger(String k,int v){data.put(k,v);} public int getInteger(String k){return ((Number)data.getOrDefault(k,0)).intValue();}
 public void setByte(String k,byte v){data.put(k,v);} public byte getByte(String k){return (byte)getInteger(k);}
 public void setBoolean(String k,boolean v){data.put(k,v);} public boolean getBoolean(String k){return (boolean)data.getOrDefault(k,false);}
 public void setString(String k,String v){data.put(k,v);} public String getString(String k){return (String)data.getOrDefault(k,"");}
 public void setTag(String k,Object v){data.put(k,v);} public boolean hasKey(String k){return data.containsKey(k);}
 public void removeTag(String k){data.remove(k);}
 public NBTTagCompound getCompoundTag(String k){return (NBTTagCompound)data.getOrDefault(k,new NBTTagCompound());}
 public NBTTagList getTagList(String k,int t){return (NBTTagList)data.getOrDefault(k,new NBTTagList());}
 public boolean equals(Object v){return v instanceof NBTTagCompound&&data.equals(((NBTTagCompound)v).data);}
 public int hashCode(){return data.hashCode();}
}
""",
    "net/minecraft/nbt/NBTTagList.java": """
package net.minecraft.nbt;
import java.util.*;
public class NBTTagList {private final List<NBTTagCompound> data=new ArrayList<>();
 public void appendTag(NBTTagCompound n){data.add(n);} public int tagCount(){return data.size();}
 public NBTTagCompound getCompoundTagAt(int i){return data.get(i);} }
""",
    "net/minecraft/item/ItemStack.java": """
package net.minecraft.item;
import net.minecraft.nbt.NBTTagCompound;
public class ItemStack {
 public int stackSize; public final int id,damage; public NBTTagCompound tag;
 public ItemStack(int id,int size,int damage){this.id=id;stackSize=size;this.damage=damage;}
 public NBTTagCompound writeToNBT(NBTTagCompound n){n.setInteger("id",id);n.setInteger("Damage",damage);n.setByte("Count",(byte)stackSize);
  if(tag!=null)n.setTag("tag",tag);return n;}
 public static ItemStack loadItemStackFromNBT(NBTTagCompound n){if(n.getInteger("id")<=0)return null;
  ItemStack item=new ItemStack(n.getInteger("id"),n.getByte("Count"),n.getInteger("Damage"));
  item.tag=n.hasKey("tag")?n.getCompoundTag("tag"):null;return item;}
}
""",
    "net/minecraftforge/fluids/FluidStack.java": """
package net.minecraftforge.fluids;
import net.minecraft.nbt.NBTTagCompound;
public class FluidStack {
 public int amount;public String name;public NBTTagCompound tag;
 public FluidStack(String name,int amount){this.name=name;this.amount=amount;}
 public FluidStack copy(){FluidStack stack=new FluidStack(name,amount);stack.tag=tag;return stack;}
 public boolean isFluidEqual(FluidStack stack){return stack!=null&&name.equals(stack.name)&&java.util.Objects.equals(tag,stack.tag);}
 public NBTTagCompound writeToNBT(NBTTagCompound n){n.setString("FluidName",name);n.setInteger("Amount",amount);
  if(tag!=null)n.setTag("Tag",tag);return n;}
 public static FluidStack loadFluidStackFromNBT(NBTTagCompound n){String name=n.getString("FluidName");if(name.isEmpty())return null;
  FluidStack stack=new FluidStack(name,n.getInteger("Amount"));stack.tag=n.hasKey("Tag")?n.getCompoundTag("Tag"):null;return stack;}
}
""",
    "HubPendingOutputContract.java": """
import java.util.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraftforge.fluids.FluidStack;
class FixtureParent {public void saveNBTData(NBTTagCompound n){} public void loadNBTData(NBTTagCompound n){}}
public class HubPendingOutputContract extends FixtureParent {
 private final ArrayList<BoundDrillNode> mBoundNodes=new ArrayList<>();
 private ArrayList<ItemStack> gtsr$pendingItemOutputs;
 private int capacity,deliveredItems;
 private static int checks;
 private static class BoundDrillNode {int x,y,z,dimensionId;boolean isMiner,isActive;
  BoundDrillNode(int x,int y,int z,int dim,boolean miner){this.x=x;this.y=y;this.z=z;dimensionId=dim;isMiner=miner;}}
 public int tryStoreNodeItemOutput(ItemStack item){int taken=Math.min(capacity,item.stackSize);capacity-=taken;deliveredItems+=taken;return item.stackSize-taken;}
 __PRODUCT_METHODS__
 private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
 public static void main(String[] args){
  HubPendingOutputContract original=new HubPendingOutputContract();original.gtsr$pendingItemOutputs=new ArrayList<>();
  ItemStack item=new ItemStack(17,4096,23);item.tag=new NBTTagCompound();item.tag.setString("Name","custom ore");
  original.gtsr$pendingItemOutputs.add(item);
  BoundDrillNode node=new BoundDrillNode(1,2,3,-4,true);node.isActive=true;original.mBoundNodes.add(node);
  NBTTagCompound saved=new NBTTagCompound();original.saveNBTData(saved);
  HubPendingOutputContract restored=new HubPendingOutputContract();restored.loadNBTData(saved);
  check(restored.gtsr$pendingItemOutputs!=null&&restored.gtsr$pendingItemOutputs.size()==1,"pending item survives normal save/load");
  ItemStack loaded=restored.gtsr$pendingItemOutputs.get(0);
  check(loaded.stackSize==4096,"item amount preserves full int beyond native byte Count");
  check(loaded.id==17&&loaded.damage==23&&item.tag.equals(loaded.tag),"item identity metadata and NBT preserved");
  restored.loadNBTData(saved);
  check(restored.gtsr$pendingItemOutputs.size()==1,"repeated reads replace balances instead of duplicating");
  check(restored.mBoundNodes.size()==1&&restored.mBoundNodes.get(0).dimensionId==-4,"existing binding codec preserved");
  restored.capacity=96;restored.gtsr$drainPendingOutputs();
  check(restored.gtsr$pendingItemOutputs.get(0).stackSize==4000&&restored.deliveredItems==96,"retry keeps only undelivered item balance");
  NBTTagCompound remainder=new NBTTagCompound();restored.saveNBTData(remainder);
  HubPendingOutputContract resumed=new HubPendingOutputContract();resumed.loadNBTData(remainder);resumed.capacity=200000;resumed.gtsr$drainPendingOutputs();
  check(resumed.deliveredItems==4000,"reload retries only remaining outputs");
  resumed.gtsr$drainPendingOutputs();check(resumed.deliveredItems==4000,"completed retry is not delivered twice");
  resumed.saveNBTData(saved);restored.loadNBTData(saved);
  check(restored.gtsr$pendingItemOutputs==null||restored.gtsr$pendingItemOutputs.isEmpty(),"empty save clears previously reused NBT balances");
  original.loadNBTData(new NBTTagCompound());
  check(original.gtsr$pendingItemOutputs==null||original.gtsr$pendingItemOutputs.isEmpty(),"legacy missing keys clear previous in-memory balances");
  NBTTagCompound invalid=new NBTTagCompound();NBTTagList invalidItems=new NBTTagList();NBTTagCompound bad=new NBTTagCompound();
  bad.setInteger("id",17);bad.setInteger("gtsr.amount",-2);invalidItems.appendTag(bad);invalidItems.appendTag(new NBTTagCompound());invalid.setTag("gtsr.pendingItems",invalidItems);
  original.loadNBTData(invalid);
  check(original.gtsr$pendingItemOutputs==null||original.gtsr$pendingItemOutputs.isEmpty(),"invalid or zero balance entries ignored");
  System.out.println("PASS HubPendingOutputContract checks="+checks);
 }
}
""",
}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    source = PRODUCT.read_text(encoding="utf-8")
    signatures = ["public void saveNBTData(NBTTagCompound aNBT)", "public void loadNBTData(NBTTagCompound aNBT)",
        "private void gtsr$drainPendingOutputs()"]
    methods = "\n".join(method(source, signature) for signature in signatures)
    files = []
    for name, text in SOURCES.items():
        path = OUT / "src" / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text.replace("__PRODUCT_METHODS__", methods), encoding="utf-8")
        files.append(path)
    jdk = Path.home() / ".gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin"
    javac = str(jdk / "javac.exe") if jdk.exists() else shutil.which("javac")
    java = str(jdk / "java.exe") if jdk.exists() else shutil.which("java")
    subprocess.run([javac, "-encoding", "UTF-8", "-d", str(OUT / "classes"), *map(str, files)], check=True)
    result = subprocess.run([java, "-cp", str(OUT / "classes"), "HubPendingOutputContract"], capture_output=True, text=True)
    (OUT / "result.txt").write_text(result.stdout + result.stderr, encoding="utf-8")
    (OUT / "source.json").write_text(json.dumps({"source": str(PRODUCT),
        "sha256": hashlib.sha256(PRODUCT.read_bytes()).hexdigest(), "signatures": signatures,
        "scope": "Exact production source methods, isolated parent/NBT/inventory boundaries; not GT integration"}, indent=2), encoding="utf-8")
    print(result.stdout + result.stderr, end="")
    result.check_returncode()


if __name__ == "__main__":
    main()
